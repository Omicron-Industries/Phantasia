package net.phoenixvine.phantasia.client.event;

import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

@OnlyIn(Dist.CLIENT)
public final class PhantasiaPatternLoader {

    private static final ExecutorService LOADER_POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Phantasia-PatternLoader");
        t.setDaemon(true);
        return t;
    });

    public final int total;

    public final AtomicInteger progress = new AtomicInteger(0);

    public static final class Result {

        public final Map<BlockPos, PhantasiaBlockInfo> blockMap;

        public final Map<BlockPos, BlockPos> localToWorld;

        public final Set<BlockPos> baseplatePositions;

        public final Set<BlockPos> bePositions;

        @Nullable
        public final BlockPos controllerWorldPos;

        public final BlockPos origin;

        public final int minY, maxY;

        public final int blockCount;

        Result(Map<BlockPos, PhantasiaBlockInfo> blockMap,
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

    private final Future<?> future;
    private volatile Result result;
    private volatile boolean done = false;
    private volatile boolean cancelled = false;

    public PhantasiaPatternLoader(IPhantasiaMultiblockShape shape, int total) {
        this.total = total;
        this.future = LOADER_POOL.submit(() -> build(shape));
    }

    public float fraction() {
        if (total <= 0) return 1f;
        return Math.min(1f, progress.get() / (float) total);
    }

    public boolean isDone() {
        return done;
    }

    @Nullable
    public Result getResult() {
        return result;
    }

    public void cancel() {
        cancelled = true;
        future.cancel(true);
    }

    private void build(IPhantasiaMultiblockShape shape) {
        try {
            BlockPos origin = new BlockPos(0, 50, 0);

            PhantasiaBlockInfo[][][] raw = shape.getBlocks();
            int sxLen = raw.length;
            int syLen = sxLen > 0 ? raw[0].length : 0;
            int szLen = sxLen > 0 && syLen > 0 ? raw[0][0].length : 0;
            int padX = Math.max(2, sxLen / 2 + 1);
            int padZ = Math.max(2, szLen / 2 + 1);

            var _baseplateState = net.phoenixvine.phantasia.utils.PhantasiaBaseplateConfig.currentBaseplateBlockState();
            PhantasiaBlockInfo floor = _baseplateState != null ? PhantasiaBlockInfo.fromBlockState(_baseplateState) :
                    null;

            Map<BlockPos, PhantasiaBlockInfo> blockMap = new HashMap<>();
            Map<BlockPos, BlockPos> localToWorld = new HashMap<>();
            Set<BlockPos> baseplatePos = new HashSet<>();
            Set<BlockPos> bePos = new HashSet<>();
            BlockPos controllerWP = null;

            if (floor != null) for (int bx = -padX; bx < sxLen + padX; bx++)
                for (int bz = -padZ; bz < szLen + padZ; bz++) {
                    if (Thread.interrupted() || cancelled) return;
                    BlockPos wp = origin.offset(bx, -1, bz);
                    blockMap.put(wp, floor);
                    baseplatePos.add(wp);
                }

            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            for (int x = 0; x < raw.length; x++) {
                for (int y = 0; y < raw[x].length; y++) {
                    for (int z = 0; z < raw[x][y].length; z++) {
                        if (Thread.interrupted() || cancelled) return;

                        PhantasiaBlockInfo info = raw[x][y][z];
                        if (info == null) {
                            progress.incrementAndGet();
                            continue;
                        }

                        BlockPos lp = new BlockPos(x, y, z);
                        BlockPos wp = origin.offset(x, y, z);

                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);

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
