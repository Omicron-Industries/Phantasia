package net.phoenixvine.phantasia.common.data.pattern;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScript;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.common.world.PhantasiaDimension;
import net.phoenixvine.phantasia.common.world.PhantasiaSlotAllocator;
import net.phoenixvine.phantasia.common.world.PhantasiaSlotVersions;

import com.mojang.blaze3d.systems.RenderSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
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
 * <li>{@code SHARED_LEVEL.setBlock()} / {@code setInnerBlockEntity()} are called
 * on the loader thread. This is safe because the renderer has not yet been
 * given a bake request — nothing reads {@code SHARED_LEVEL} until
 * {@code onPatternLoaded} fires on the render thread and calls
 * {@code renderer.requestBake()}.
 * <li>Structure-forming logic (e.g. GTCEu's {@code onStructureFormed}) is always
 * dispatched back to the render thread via {@code recordRenderCall} inside
 * {@link IPhantasiaMultiblockDefinition#onShapeLoaded}.
 * </ul>
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
                                               IPhantasiaMultiblockDefinition definition,
                                               int shapeIndex,
                                               List<IPhantasiaMultiblockShape> shapes,
                                               PhantasiaScript script,
                                               PhantasiaTrackedDummyWorld sharedLevel,
                                               Consumer<PhantasiaLoadedPattern> onLoaded) {
        PhantasiaPatternLoader loader = new PhantasiaPatternLoader();
        loader.future = LOADER_POOL.submit(() -> loader.run(
                definition, shapeIndex, shapes, script, sharedLevel, onLoaded));
        return loader;
    }

    public boolean isDone() {
        return done;
    }

    public boolean isFailed() {
        return failed;
    }

    /** Cancel the in-progress load. Safe to call from any thread. */
    public void cancel() {
        if (future != null) future.cancel(true);
    }

    // ── Core load logic ───────────────────────────────────────────────────────

    private void run(
                     IPhantasiaMultiblockDefinition definition,
                     int shapeIndex,
                     List<IPhantasiaMultiblockShape> shapes,
                     PhantasiaScript script,
                     PhantasiaTrackedDummyWorld sharedLevel,
                     Consumer<PhantasiaLoadedPattern> onLoaded) {
        try {
            PhantasiaLoadedPattern result = doLoad(definition, shapeIndex, shapes, script, sharedLevel);

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
                                          IPhantasiaMultiblockDefinition definition,
                                          int shapeIndex,
                                          List<IPhantasiaMultiblockShape> shapes,
                                          PhantasiaScript script,
                                          PhantasiaTrackedDummyWorld sharedLevel) throws InterruptedException {
        ResourceLocation machineId = definition.getId();
        BlockPos slotOrigin = PhantasiaSlotAllocator.originFor(machineId);
        BlockPos renderOrigin = PhantasiaSlotAllocator.RENDER_ORIGIN;

        IPhantasiaMultiblockShape shape = shapes.get(shapeIndex);
        int shapeHash = PhantasiaSlotVersions.hashShape(shape.getBlocks());
        int scriptHash = PhantasiaSlotVersions.hashScript(script.getSourceData());
        boolean warm = PhantasiaSlotVersions.isValid(machineId, shapeHash, scriptHash, slotOrigin);

        LOGGER.info("[Phantasia] {} load for {} at slot={} render={}",
                warm ? "Warm" : "Cold", machineId, slotOrigin, renderOrigin);

        PhantasiaLoadedPattern result = warm ? loadWarm(shape, renderOrigin, sharedLevel, script) :
                loadCold(definition, shape, renderOrigin, slotOrigin, sharedLevel, script);

        if (!warm) {
            PhantasiaSlotVersions.put(machineId, shapeHash, scriptHash);
            PhantasiaDimension.forceChunkLoad(slotOrigin);
        }
        return result;
    }

    // ── Cold load ─────────────────────────────────────────────────────────────

    private PhantasiaLoadedPattern loadCold(
                                            IPhantasiaMultiblockDefinition definition,
                                            IPhantasiaMultiblockShape shape,
                                            BlockPos renderOrigin,
                                            BlockPos slotOrigin,
                                            PhantasiaTrackedDummyWorld sharedLevel,
                                            PhantasiaScript script) throws InterruptedException {
        phase = "Reading shape…";

        BlockInfo[][][] raw = shape.getBlocks();
        int sxLen = raw.length;
        int syLen = sxLen > 0 ? raw[0].length : 0;
        int szLen = sxLen > 0 && syLen > 0 ? raw[0][0].length : 0;
        int padX = Math.max(2, sxLen / 2 + 1);
        int padZ = Math.max(2, szLen / 2 + 1);

        int baseplateCount = (sxLen + 2 * padX + 1) * (szLen + 2 * padZ + 1);
        int machineCount = countNonNull(raw);
        blocksTotal = baseplateCount + machineCount;

        Map<BlockPos, BlockInfo> blockMap = new HashMap<>(blocksTotal);
        Map<BlockPos, BlockPos> localToWorld = new HashMap<>(machineCount);
        Set<BlockPos> baseplatePos = new HashSet<>(baseplateCount);
        Set<BlockPos> bePos = new HashSet<>();

        // ── Baseplate ─────────────────────────────────────────────────────────
        phase = "Reading baseplate…";
        var _baseplateState0 = net.phoenixvine.phantasia.utils.PhantasiaTheme.currentBaseplateBlockState();
        BlockInfo floor = _baseplateState0 != null ? BlockInfo.fromBlockState(_baseplateState0) : null;

        if (floor != null) for (int bx = -padX; bx <= sxLen + padX; bx++) {
            for (int bz = -padZ; bz <= szLen + padZ; bz++) {
                if (Thread.interrupted()) throw new InterruptedException();
                BlockPos wp = renderOrigin.offset(bx, -1, bz);
                blockMap.put(wp, floor);
                baseplatePos.add(wp);
                blocksPlaced++;
            }
        }

        // ── Machine blocks ────────────────────────────────────────────────────
        // NOTE: world writes happen on the render thread (in onAsyncPatternLoaded)
        // to avoid ConcurrentModificationException in TrackedDummyWorld.tickWorld().
        phase = "Reading blocks…";

        for (int x = 0; x < raw.length; x++) {
            for (int y = 0; y < raw[x].length; y++) {
                for (int z = 0; z < raw[x][y].length; z++) {
                    if (Thread.interrupted()) throw new InterruptedException();

                    BlockInfo info = raw[x][y][z];
                    if (info == null) continue;

                    BlockState state = info.getBlockState();
                    // Skip air and invisible predicates (e.g. GTM "any" placeholder blocks).
                    if (state == null || state.isAir() || state.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE) continue;

                    BlockPos lp = new BlockPos(x, y, z);
                    BlockPos wp = renderOrigin.offset(x, y, z);

                    blockMap.put(wp, info);
                    localToWorld.put(lp, wp);

                    if (state.getBlock() instanceof EntityBlock) {
                        bePos.add(wp);
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
        net.phoenixvine.phantasia.client.screens.PhantasiaSceneScreen
                .coldPopulateDimensionSlot(definition.getId(), slotSpaceMap);

        // ── Prepare post-write task (fires after world is populated on render thread) ──
        // onShapeLoaded needs the world to be populated; we defer it until after the
        // render thread writes all blocks in onAsyncPatternLoaded.
        phase = "Forming structure…";
        final Map<BlockPos, BlockInfo> blockMapSnapshot = Map.copyOf(blockMap);
        final Map<BlockPos, BlockPos> localToWorldSnapshot = Map.copyOf(localToWorld);
        Runnable postWriteTask =
                () -> definition.onShapeLoaded(sharedLevel, renderOrigin, blockMapSnapshot, localToWorldSnapshot);

        return buildResult(raw, blockMap, localToWorld, baseplatePos, bePos, renderOrigin, script, postWriteTask);
    }

    // ── Warm load ─────────────────────────────────────────────────────────────

    private PhantasiaLoadedPattern loadWarm(
                                            IPhantasiaMultiblockShape shape,
                                            BlockPos renderOrigin,
                                            PhantasiaTrackedDummyWorld sharedLevel,
                                            PhantasiaScript script) throws InterruptedException {
        phase = "Reading shape…";

        BlockInfo[][][] raw = shape.getBlocks();
        int sxLen = raw.length;
        int syLen = sxLen > 0 ? raw[0].length : 0;
        int szLen = sxLen > 0 && syLen > 0 ? raw[0][0].length : 0;
        int padX = Math.max(2, sxLen / 2 + 1);
        int padZ = Math.max(2, szLen / 2 + 1);

        int baseplateCount = (sxLen + 2 * padX + 1) * (szLen + 2 * padZ + 1);
        int machineCount = countNonNull(raw);
        blocksTotal = baseplateCount + machineCount;

        sharedLevel.blockEntities.clear();

        Map<BlockPos, BlockInfo> blockMap = new HashMap<>(blocksTotal);
        Map<BlockPos, BlockPos> localToWorld = new HashMap<>(machineCount);
        Set<BlockPos> baseplatePos = new HashSet<>(baseplateCount);
        Set<BlockPos> bePos = new HashSet<>();

        phase = "Indexing baseplate…";
        var _baseplateState1 = net.phoenixvine.phantasia.utils.PhantasiaTheme.currentBaseplateBlockState();
        BlockInfo floor = _baseplateState1 != null ? BlockInfo.fromBlockState(_baseplateState1) : null;
        if (floor != null) for (int bx = -padX; bx <= sxLen + padX; bx++) {
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
                    if (info.getBlockState().isAir()) continue; // skip "any"/air placeholder positions

                    BlockPos lp = new BlockPos(x, y, z);
                    BlockPos wp = renderOrigin.offset(x, y, z);

                    blockMap.put(wp, info);
                    localToWorld.put(lp, wp);

                    BlockState state = sharedLevel.getBlockState(wp);
                    if (state.getBlock() instanceof EntityBlock entityBlock) {
                        BlockEntity newBE = entityBlock.newBlockEntity(wp, state);
                        if (newBE != null) {
                            sharedLevel.setInnerBlockEntity(newBE);
                            bePos.add(wp);
                        }
                    }
                    blocksPlaced++;
                }
            }
        }

        phase = "Forming structure…";
        return buildResult(raw, blockMap, localToWorld, baseplatePos, bePos, renderOrigin, script);
    }

    // ── Build result ──────────────────────────────────────────────────────────

    private static PhantasiaLoadedPattern buildResult(
                                                      BlockInfo[][][] raw,
                                                      Map<BlockPos, BlockInfo> blockMap,
                                                      Map<BlockPos, BlockPos> localToWorld,
                                                      Set<BlockPos> baseplatePos,
                                                      Set<BlockPos> bePos,
                                                      BlockPos origin,
                                                      PhantasiaScript script) {
        return buildResult(raw, blockMap, localToWorld, baseplatePos, bePos, origin, script, null);
    }

    private static PhantasiaLoadedPattern buildResult(
                                                      BlockInfo[][][] raw,
                                                      Map<BlockPos, BlockInfo> blockMap,
                                                      Map<BlockPos, BlockPos> localToWorld,
                                                      Set<BlockPos> baseplatePos,
                                                      Set<BlockPos> bePos,
                                                      BlockPos origin,
                                                      PhantasiaScript script,
                                                      Runnable postWriteTask) {
        LOGGER.info("[Phantasia] PatternLoader: registered {} BEs", bePos.size());

        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (BlockPos lp : localToWorld.keySet()) {
            minY = Math.min(minY, lp.getY());
            maxY = Math.max(maxY, lp.getY());
        }
        if (minY > maxY) {
            minY = 0;
            maxY = 0;
        }

        return new PhantasiaLoadedPattern(blockMap, localToWorld, baseplatePos,
                null, bePos, origin, minY, maxY, script, postWriteTask);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int countNonNull(BlockInfo[][][] raw) {
        int n = 0;
        for (BlockInfo[][] layer : raw)
            for (BlockInfo[] row : layer)
                for (BlockInfo b : row) {
                    if (b == null) continue;
                    net.minecraft.world.level.block.state.BlockState s = b.getBlockState();
                    if (s == null || s.isAir() || s.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE) continue;
                    n++;
                }
        return n;
    }
}
