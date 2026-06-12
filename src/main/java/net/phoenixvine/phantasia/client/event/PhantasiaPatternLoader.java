package net.phoenixvine.phantasia.client.event;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

/**
 * PhantasiaPatternLoader
 *
 * ── Purpose ──────────────────────────────────────────────────────────────────
 * Builds the pure-data structures required by loadPattern() off the render
 * thread, then surfaces a {@link Result} for the screen to apply synchronously
 * when it is ready.
 *
 * ── Thread model ─────────────────────────────────────────────────────────────
 * Phase 1 (LOADER_POOL thread): iterates raw[][][], constructs blockMap,
 * localToWorld, baseplatePos, bePos, and instantiates each BlockEntity via
 * BlockInfo.getBlockEntity(). Increments {@link #progress} as it goes.
 * No SHARED_LEVEL mutations, no GL calls.
 *
 * Phase 2 (render thread, called by PhantasiaSceneScreen once isDone()):
 * Writes to SHARED_LEVEL (addBlocks, setInnerBlockEntity, setLevel) and calls
 * onStructureFormed(). This is fast — a single synchronous pass over already-
 * built data — so it does not visibly stall the frame.
 *
 * ── When to use ──────────────────────────────────────────────────────────────
 * Only when block count > LARGE_LOAD_THRESHOLD (see PhantasiaSceneScreen).
 * Small machines load synchronously in loadPattern() as before.
 */
@OnlyIn(Dist.CLIENT)
public final class PhantasiaPatternLoader {

    // ── Thread pool ───────────────────────────────────────────────────────────

    private static final ExecutorService LOADER_POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Phantasia-PatternLoader");
        t.setDaemon(true);
        return t;
    });

    // ── Progress ──────────────────────────────────────────────────────────────

    /** Total blocks to process (machine blocks only, excludes baseplate). */
    public final int total;

    /** How many blocks have been processed so far. Read from the render thread. */
    public final AtomicInteger progress = new AtomicInteger(0);

    // ── Result ────────────────────────────────────────────────────────────────

    /**
     * Plain data bag produced by the off-thread phase.
     * All fields are populated before {@link PhantasiaPatternLoader#isDone()}
     * returns true. The render thread applies them to SHARED_LEVEL.
     */
    public static final class Result {

        /** Full block map (world-space pos → BlockInfo), including baseplate. */
        public final Map<BlockPos, BlockInfo> blockMap;
        /** Maps pattern-local pos → world pos for machine blocks only. */
        public final Map<BlockPos, BlockPos> localToWorld;
        /** Baseplate world positions (floor tiles). */
        public final Set<BlockPos> baseplatePositions;
        /** World positions of machine blocks that have a BlockEntity. */
        public final Set<BlockPos> bePositions;
        /** World position of the controller block, or null if none found. */
        @Nullable
        public final BlockPos controllerWorldPos;
        /** Origin used when placing blocks (fixed at 0,50,0). */
        public final BlockPos origin;
        /** Min/max Y in local (pattern) space. */
        public final int minY, maxY;
        /** Number of blocks in the machine (excluding baseplate). */
        public final int blockCount;

        Result(Map<BlockPos, BlockInfo> blockMap,
               Map<BlockPos, BlockPos> localToWorld,
               Set<BlockPos> baseplatePositions,
               Set<BlockPos> bePositions,
               @Nullable BlockPos controllerWorldPos,
               BlockPos origin,
               int minY, int maxY,
               int blockCount) {
            this.blockMap = blockMap;
            this.localToWorld = localToWorld;
            this.baseplatePositions = baseplatePositions;
            this.bePositions = bePositions;
            this.controllerWorldPos = controllerWorldPos;
            this.origin = origin;
            this.minY = minY;
            this.maxY = maxY;
            this.blockCount = blockCount;
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private final Future<?> future;
    private volatile Result result;
    private volatile boolean done = false;
    private volatile boolean cancelled = false;

    // ── Constructor / factory ─────────────────────────────────────────────────

    /**
     * Starts loading {@code shape} asynchronously.
     *
     * @param shape the MultiblockShapeInfo to load
     * @param total pre-counted non-null block count (caller already counted to decide
     *              whether to use the loader, so we reuse that value)
     */
    public PhantasiaPatternLoader(MultiblockShapeInfo shape, int total) {
        this.total = total;
        this.future = LOADER_POOL.submit(() -> build(shape));
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** 0.0–1.0 progress fraction. Safe to read from any thread. */
    public float fraction() {
        if (total <= 0) return 1f;
        return Math.min(1f, progress.get() / (float) total);
    }

    /**
     * True once the off-thread phase is complete (successfully or via exception).
     * Check this from the render thread each frame; when true, call {@link #getResult()}.
     */
    public boolean isDone() {
        return done;
    }

    /**
     * Returns the built result, or null if the load was cancelled or failed.
     * Only valid after {@link #isDone()} returns true.
     */
    @Nullable
    public Result getResult() {
        return result;
    }

    /**
     * Cancels the background load. Safe to call from any thread.
     * After cancellation, {@link #isDone()} will still eventually return true
     * (when the thread notices the interrupt), but {@link #getResult()} will be null.
     */
    public void cancel() {
        cancelled = true;
        future.cancel(true);
    }

    // ── Off-thread build ──────────────────────────────────────────────────────

    private void build(MultiblockShapeInfo shape) {
        try {
            BlockPos origin = new BlockPos(0, 50, 0);

            BlockInfo[][][] raw = shape.getBlocks();
            int sxLen = raw.length;
            int syLen = sxLen > 0 ? raw[0].length : 0;
            int szLen = sxLen > 0 && syLen > 0 ? raw[0][0].length : 0;
            int padX = Math.max(2, sxLen / 2 + 1);
            int padZ = Math.max(2, szLen / 2 + 1);

            BlockInfo floor = BlockInfo.fromBlockState(Blocks.DEEPSLATE_BRICKS.defaultBlockState());

            Map<BlockPos, BlockInfo> blockMap = new HashMap<>();
            Map<BlockPos, BlockPos> localToWorld = new HashMap<>();
            Set<BlockPos> baseplatePos = new HashSet<>();
            Set<BlockPos> bePos = new HashSet<>();
            BlockPos controllerWP = null;

            // ── Baseplate ─────────────────────────────────────────────────────
            for (int bx = -padX; bx <= sxLen + padX; bx++)
                for (int bz = -padZ; bz <= szLen + padZ; bz++) {
                    if (Thread.interrupted() || cancelled) return;
                    BlockPos wp = origin.offset(bx, -1, bz);
                    blockMap.put(wp, floor);
                    baseplatePos.add(wp);
                }

            // ── Machine blocks ────────────────────────────────────────────────
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            for (int x = 0; x < raw.length; x++) {
                for (int y = 0; y < raw[x].length; y++) {
                    for (int z = 0; z < raw[x][y].length; z++) {
                        if (Thread.interrupted() || cancelled) return;

                        BlockInfo info = raw[x][y][z];
                        if (info == null) {
                            progress.incrementAndGet();
                            continue;
                        }

                        BlockPos lp = new BlockPos(x, y, z);
                        BlockPos wp = origin.offset(x, y, z);

                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);

                        // Detect controller position by instantiating the BE here.
                        // BlockInfo.getBlockEntity() is safe off-thread (no GL).
                        // We only need the class, not registration — the render thread
                        // will reinstantiate fresh BEs with setLevel() before use.
                        try {
                            var be = info.getBlockEntity(wp);
                            if (be instanceof MetaMachineBlockEntity mbe) {
                                var machine = mbe.getMetaMachine();
                                if (machine instanceof MultiblockControllerMachine && controllerWP == null) {
                                    controllerWP = wp;
                                }
                                bePos.add(wp);
                            }
                        } catch (Exception ignored) {}

                        blockMap.put(wp, info);
                        localToWorld.put(lp, wp);
                        progress.incrementAndGet();
                    }
                }
            }

            if (minY > maxY) {
                minY = 0;
                maxY = 0;
            }

            if (!cancelled) {
                result = new Result(
                        blockMap, localToWorld, baseplatePos, bePos,
                        controllerWP, origin, minY, maxY, localToWorld.size());
            }
        } catch (Exception e) {
            net.phoenixvine.phantasia.Phantasia.LOGGER.error(
                    "[Phantasia] PhantasiaPatternLoader failed: {}", e.getMessage(), e);
        } finally {
            done = true;
        }
    }
}
