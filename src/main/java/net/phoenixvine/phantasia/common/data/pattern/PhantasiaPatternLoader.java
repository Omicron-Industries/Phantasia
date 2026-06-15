package net.phoenixvine.phantasia.common.data.pattern;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScript;
import net.phoenixvine.phantasia.common.world.PhantasiaDimension;
import net.phoenixvine.phantasia.common.world.PhantasiaSlotAllocator;
import net.phoenixvine.phantasia.common.world.PhantasiaSlotVersions;

import com.mojang.blaze3d.systems.RenderSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * PhantasiaPatternLoader
 *
 * Moves the render-thread-blocking work of {@code loadPattern()} off the render
 * thread entirely. The caller gets immediate control back; the loader reports
 * progress through atomics and delivers the finished {@link PhantasiaLoadedPattern}
 * via a {@link Consumer} posted to the render thread with
 * {@link RenderSystem#recordRenderCall}.
 *
 * <h3>Thread safety</h3>
 * <ul>
 *   <li>{@code SHARED_LEVEL.setBlock()} / {@code setInnerBlockEntity()} are called
 *       on the loader thread. This is safe because the renderer has not yet been
 *       given a bake request — nothing reads {@code SHARED_LEVEL} until
 *       {@code onPatternLoaded} fires on the render thread and calls
 *       {@code renderer.requestBake()}.
 *   <li>{@code onStructureFormed()} is always dispatched back to the render thread
 *       via {@code recordRenderCall} so GT machine state is never mutated off-thread.
 * </ul>
 *
 * <h3>Progress</h3>
 * {@link #blocksPlaced} and {@link #blocksTotal} are {@code volatile int}s safe to
 * read from the render thread for a progress bar.  {@link #isDone()} becomes true
 * when the callback has been posted (though the render thread may not have processed
 * it yet). {@link #isFailed()} is set if an unrecoverable exception occurs.
 */
public final class PhantasiaPatternLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("PhantasiaPatternLoader");

    private static final ExecutorService LOADER_POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Phantasia-PatternLoader");
        t.setDaemon(true);
        return t;
    });

    // ── Progress (read from render thread) ────────────────────────────────────

    /** Total blocks to place, including baseplate. Set before work begins. */
    public volatile int blocksTotal = 0;

    /** Blocks placed so far. Incremented on the loader thread. */
    public volatile int blocksPlaced = 0;

    /** Human-readable phase label for the progress bar. */
    public volatile String phase = "Preparing…";

    /** True once the callback has been posted to the render thread. */
    private volatile boolean done = false;

    /** True if loading failed with an exception. */
    private volatile boolean failed = false;

    private volatile Future<?> future = null;

    // ── Constructor / launch ──────────────────────────────────────────────────

    private PhantasiaPatternLoader() {}

    /**
     * Starts an asynchronous pattern load and returns the loader immediately.
     *
     * @param definition  the multiblock definition to load
     * @param shapeIndex  which shape variant to use
     * @param shapes      the full list of available shapes
     * @param script      the compiled script for this machine
     * @param sharedLevel the shared dummy world to populate
     * @param onLoaded    callback invoked on the render thread with the finished pattern
     */
    public static PhantasiaPatternLoader start(
            MultiblockMachineDefinition definition,
            int shapeIndex,
            List<MultiblockShapeInfo> shapes,
            PhantasiaScript script,
            PhantasiaTrackedDummyWorld sharedLevel,
            Consumer<PhantasiaLoadedPattern> onLoaded) {

        PhantasiaPatternLoader loader = new PhantasiaPatternLoader();
        loader.future = LOADER_POOL.submit(() -> loader.run(
                definition, shapeIndex, shapes, script, sharedLevel, onLoaded));
        return loader;
    }

    public boolean isDone()   { return done; }
    public boolean isFailed() { return failed; }

    /** Cancel the in-progress load. Safe to call from any thread. */
    public void cancel() {
        if (future != null) future.cancel(true);
    }

    // ── Core load logic ───────────────────────────────────────────────────────

    private void run(
            MultiblockMachineDefinition definition,
            int shapeIndex,
            List<MultiblockShapeInfo> shapes,
            PhantasiaScript script,
            PhantasiaTrackedDummyWorld sharedLevel,
            Consumer<PhantasiaLoadedPattern> onLoaded) {

        try {
            PhantasiaLoadedPattern result = doLoad(
                    definition, shapeIndex, shapes, script, sharedLevel);

            // Post the callback to the render thread so onStructureFormed and
            // renderer.requestBake() happen in the right thread context.
            RenderSystem.recordRenderCall(() -> {
                done = true;
                onLoaded.accept(result);
            });
        } catch (InterruptedException ignored) {
            LOGGER.debug("[Phantasia] PatternLoader cancelled");
            done = true;
        } catch (Exception e) {
            LOGGER.error("[Phantasia] PatternLoader failed", e);
            failed = true;
            done = true;
        }
    }

    private PhantasiaLoadedPattern doLoad(
            MultiblockMachineDefinition definition,
            int shapeIndex,
            List<MultiblockShapeInfo> shapes,
            PhantasiaScript script,
            PhantasiaTrackedDummyWorld sharedLevel) throws InterruptedException {

        ResourceLocation machineId = definition.getId();
        BlockPos slotOrigin   = PhantasiaSlotAllocator.originFor(machineId);
        BlockPos renderOrigin = PhantasiaSlotAllocator.RENDER_ORIGIN;

        MultiblockShapeInfo shape = shapes.get(shapeIndex);
        int shapeHash  = PhantasiaSlotVersions.hashShape(shape.getBlocks());
        int scriptHash = PhantasiaSlotVersions.hashScript(script.getSourceData());
        boolean warm   = PhantasiaSlotVersions.isValid(machineId, shapeHash, scriptHash, slotOrigin);

        LOGGER.info("[Phantasia] {} load for {} at slot={} render={}",
                warm ? "Warm" : "Cold", machineId, slotOrigin, renderOrigin);

        PhantasiaLoadedPattern result = warm
                ? loadWarm(shape, renderOrigin, sharedLevel, script)
                : loadCold(definition, shape, renderOrigin, slotOrigin, sharedLevel, script);

        if (!warm) {
            PhantasiaSlotVersions.put(machineId, shapeHash, scriptHash);
            PhantasiaDimension.forceChunkLoad(slotOrigin);
        }
        return result;
    }

    // ── Cold load ─────────────────────────────────────────────────────────────

    private PhantasiaLoadedPattern loadCold(
            MultiblockMachineDefinition definition,
            MultiblockShapeInfo shape,
            BlockPos renderOrigin,
            BlockPos slotOrigin,
            PhantasiaTrackedDummyWorld sharedLevel,
            PhantasiaScript script) throws InterruptedException {

        phase = "Reading shape…";

        BlockInfo[][][] raw = shape.getBlocks();
        int sxLen = raw.length;
        int syLen = sxLen > 0 ? raw[0].length : 0;
        int szLen = sxLen > 0 && syLen > 0 ? raw[0][0].length : 0;
        int padX  = Math.max(2, sxLen / 2 + 1);
        int padZ  = Math.max(2, szLen / 2 + 1);

        // Count total work units upfront so the progress bar is accurate.
        int baseplateCount = (sxLen + 2 * padX + 1) * (szLen + 2 * padZ + 1);
        int machineCount   = countNonNull(raw);
        blocksTotal = baseplateCount + machineCount;

        Map<BlockPos, BlockInfo>  blockMap     = new HashMap<>(blocksTotal);
        Map<BlockPos, BlockPos>   localToWorld = new HashMap<>(machineCount);
        Set<BlockPos>             baseplatePos = new HashSet<>(baseplateCount);
        Set<BlockPos>             bePos        = new HashSet<>();
        List<IMultiPart>          parts        = new ArrayList<>();
        BlockPos                  controllerWP = null;
        MultiblockControllerMachine controller  = null;

        // ── Baseplate ─────────────────────────────────────────────────────────
        phase = "Placing baseplate…";
        BlockInfo floor = BlockInfo.fromBlockState(Blocks.DEEPSLATE_BRICKS.defaultBlockState());

        for (int bx = -padX; bx <= sxLen + padX; bx++) {
            for (int bz = -padZ; bz <= szLen + padZ; bz++) {
                if (Thread.interrupted()) throw new InterruptedException();
                BlockPos wp = renderOrigin.offset(bx, -1, bz);
                blockMap.put(wp, floor);
                baseplatePos.add(wp);
                sharedLevel.setBlock(wp, floor.getBlockState(), 3);
                blocksPlaced++;
            }
        }

        // ── Machine blocks ────────────────────────────────────────────────────
        phase = "Placing blocks…";

        for (int x = 0; x < raw.length; x++) {
            for (int y = 0; y < raw[x].length; y++) {
                for (int z = 0; z < raw[x][y].length; z++) {
                    if (Thread.interrupted()) throw new InterruptedException();

                    BlockInfo info = raw[x][y][z];
                    if (info == null) continue;

                    BlockPos lp = new BlockPos(x, y, z);
                    BlockPos wp = renderOrigin.offset(x, y, z);

                    blockMap.put(wp, info);
                    localToWorld.put(lp, wp);

                    BlockState state = info.getBlockState();
                    sharedLevel.setBlock(wp, state, 3);

                    if (state.getBlock() instanceof EntityBlock entityBlock) {
                        BlockEntity newBE = entityBlock.newBlockEntity(wp, state);
                        if (newBE != null) {
                            sharedLevel.setInnerBlockEntity(newBE);
                            if (newBE instanceof MetaMachineBlockEntity mmbe) {
                                mmbe.setLevel(sharedLevel);
                                var machine = mmbe.getMetaMachine();
                                if (machine instanceof MultiblockControllerMachine ctrl && controllerWP == null) {
                                    controller = ctrl;
                                    controllerWP = wp;
                                } else if (machine instanceof IMultiPart part) {
                                    parts.add(part);
                                }
                            }
                            bePos.add(wp);
                        }
                    }
                    blocksPlaced++;
                }
            }
        }

        // ── Persist to scene dimension ────────────────────────────────────────
        phase = "Saving to dimension…";
        Map<BlockPos, BlockInfo> slotSpaceMap = new HashMap<>(blockMap.size());
        for (Map.Entry<BlockPos, BlockInfo> e : blockMap.entrySet()) {
            BlockPos localOffset = e.getKey().subtract(renderOrigin);
            slotSpaceMap.put(slotOrigin.offset(localOffset), e.getValue());
        }
        // coldPopulateDimensionSlot submits its own async server task — safe to call here.
        net.phoenixvine.phantasia.client.screens.PhantasiaSceneScreen
                .coldPopulateDimensionSlot(definition.getId(), slotSpaceMap);

        // ── onStructureFormed — must run on render thread ─────────────────────
        // Captured for the recordRenderCall closure below.
        phase = "Forming structure…";
        return finalise(raw, blockMap, localToWorld, baseplatePos, bePos,
                controllerWP, controller, renderOrigin, parts, script, sharedLevel, true);
    }

    // ── Warm load ─────────────────────────────────────────────────────────────

    private PhantasiaLoadedPattern loadWarm(
            MultiblockShapeInfo shape,
            BlockPos renderOrigin,
            PhantasiaTrackedDummyWorld sharedLevel,
            PhantasiaScript script) throws InterruptedException {

        phase = "Reading shape…";

        BlockInfo[][][] raw = shape.getBlocks();
        int sxLen = raw.length;
        int syLen = sxLen > 0 ? raw[0].length : 0;
        int szLen = sxLen > 0 && syLen > 0 ? raw[0][0].length : 0;
        int padX  = Math.max(2, sxLen / 2 + 1);
        int padZ  = Math.max(2, szLen / 2 + 1);

        int baseplateCount = (sxLen + 2 * padX + 1) * (szLen + 2 * padZ + 1);
        int machineCount   = countNonNull(raw);
        blocksTotal = baseplateCount + machineCount;

        // Warm load: block states already in SHARED_LEVEL from a previous session.
        // Clear BEs so we re-register fresh instances (BEs are session-local).
        sharedLevel.blockEntities.clear();

        Map<BlockPos, BlockInfo>  blockMap     = new HashMap<>(blocksTotal);
        Map<BlockPos, BlockPos>   localToWorld = new HashMap<>(machineCount);
        Set<BlockPos>             baseplatePos = new HashSet<>(baseplateCount);
        Set<BlockPos>             bePos        = new HashSet<>();
        List<IMultiPart>          parts        = new ArrayList<>();
        BlockPos                  controllerWP = null;
        MultiblockControllerMachine controller  = null;

        phase = "Indexing baseplate…";
        BlockInfo floor = BlockInfo.fromBlockState(Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        for (int bx = -padX; bx <= sxLen + padX; bx++) {
            for (int bz = -padZ; bz <= szLen + padZ; bz++) {
                if (Thread.interrupted()) throw new InterruptedException();
                BlockPos wp = renderOrigin.offset(bx, -1, bz);
                blockMap.put(wp, floor);
                baseplatePos.add(wp);
                blocksPlaced++;
            }
        }

        phase = "Indexing blocks…";
        for (int x = 0; x < raw.length; x++) {
            for (int y = 0; y < raw[x].length; y++) {
                for (int z = 0; z < raw[x][y].length; z++) {
                    if (Thread.interrupted()) throw new InterruptedException();

                    BlockInfo info = raw[x][y][z];
                    if (info == null) continue;

                    BlockPos lp = new BlockPos(x, y, z);
                    BlockPos wp = renderOrigin.offset(x, y, z);

                    blockMap.put(wp, info);
                    localToWorld.put(lp, wp);

                    // Re-register BEs from the world state (blocks are already placed).
                    BlockState state = sharedLevel.getBlockState(wp);
                    if (state.getBlock() instanceof EntityBlock entityBlock) {
                        BlockEntity newBE = entityBlock.newBlockEntity(wp, state);
                        if (newBE != null) {
                            sharedLevel.setInnerBlockEntity(newBE);
                            if (newBE instanceof MetaMachineBlockEntity mmbe) {
                                mmbe.setLevel(sharedLevel);
                                var machine = mmbe.getMetaMachine();
                                if (machine instanceof MultiblockControllerMachine ctrl && controllerWP == null) {
                                    controller = ctrl;
                                    controllerWP = wp;
                                } else if (machine instanceof IMultiPart part) {
                                    parts.add(part);
                                }
                            }
                            bePos.add(wp);
                        }
                    }
                    blocksPlaced++;
                }
            }
        }

        phase = "Forming structure…";
        return finalise(raw, blockMap, localToWorld, baseplatePos, bePos,
                controllerWP, controller, renderOrigin, parts, script, sharedLevel, true);
    }

    // ── Shared finalise ───────────────────────────────────────────────────────

    /**
     * Builds the {@link PhantasiaLoadedPattern} from the collected data.
     * {@code onStructureFormed} is deferred to the render thread when
     * {@code fireStructureFormed} is true.
     */
    private PhantasiaLoadedPattern finalise(
            BlockInfo[][][] raw,
            Map<BlockPos, BlockInfo> blockMap,
            Map<BlockPos, BlockPos> localToWorld,
            Set<BlockPos> baseplatePos,
            Set<BlockPos> bePos,
            BlockPos controllerWP,
            MultiblockControllerMachine controller,
            BlockPos origin,
            List<IMultiPart> parts,
            PhantasiaScript script,
            PhantasiaTrackedDummyWorld sharedLevel,
            boolean fireStructureFormed) {

        LOGGER.info("[Phantasia] PatternLoader: registered {} BEs", bePos.size());

        // onStructureFormed touches GT machine state — defer to render thread.
        if (fireStructureFormed && controller != null) {
            final MultiblockControllerMachine ctrl = controller;
            final List<IMultiPart> partsCopy = List.copyOf(parts);
            RenderSystem.recordRenderCall(() -> {
                try {
                    var mState = ctrl.getMultiblockState();
                    if (mState != null) {
                        mState.setError(null);
                        mState.getMatchContext().set("parts", new HashSet<>(partsCopy));
                    }
                    ctrl.getPatternLock().lock();
                    try {
                        ctrl.onStructureFormed();
                    } finally {
                        ctrl.getPatternLock().unlock();
                    }
                    LOGGER.info("[Phantasia] onStructureFormed simulated successfully.");
                } catch (Exception e) {
                    LOGGER.error("[Phantasia] onStructureFormed failed: {}", e.getMessage(), e);
                }
            });
        }

        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (BlockPos lp : localToWorld.keySet()) {
            minY = Math.min(minY, lp.getY());
            maxY = Math.max(maxY, lp.getY());
        }
        if (minY > maxY) { minY = 0; maxY = 0; }

        return new PhantasiaLoadedPattern(blockMap, localToWorld, baseplatePos,
                controllerWP, bePos, origin, minY, maxY, controller, script);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int countNonNull(BlockInfo[][][] raw) {
        int n = 0;
        for (BlockInfo[][] layer : raw)
            for (BlockInfo[] row : layer)
                for (BlockInfo b : row)
                    if (b != null) n++;
        return n;
    }
}