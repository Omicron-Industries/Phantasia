package net.phoenixvine.phantasia.common.data.pattern;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScript;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import com.mojang.blaze3d.systems.RenderSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class PhantasiaPatternLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("PhantasiaPatternLoader");

    private static final ExecutorService LOADER_POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Phantasia-PatternLoader");
        t.setDaemon(true);
        return t;
    });

    public volatile int blocksTotal = 0;

    public volatile int blocksPlaced = 0;

    public volatile String phase = "Preparing…";

    private volatile boolean done = false;

    private volatile boolean failed = false;

    private volatile Future<?> future = null;

    private PhantasiaPatternLoader() {}

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

    public void cancel() {
        if (future != null) future.cancel(true);
    }

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

                sharedLevel.blockEntities.clear();
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
        BlockPos renderOrigin = new BlockPos(8, 50, 8);
        IPhantasiaMultiblockShape shape = shapes.get(shapeIndex);
        LOGGER.info("[Phantasia] Async load for {}", definition.getId());
        return loadCold(definition, shape, renderOrigin, sharedLevel, script);
    }

    private PhantasiaLoadedPattern loadCold(
                                            IPhantasiaMultiblockDefinition definition,
                                            IPhantasiaMultiblockShape shape,
                                            BlockPos renderOrigin,
                                            PhantasiaTrackedDummyWorld sharedLevel,
                                            PhantasiaScript script) throws InterruptedException {
        phase = "Reading shape…";

        PhantasiaBlockInfo[][][] raw = shape.getBlocks();
        int sxLen = raw.length;
        int syLen = sxLen > 0 ? raw[0].length : 0;
        int szLen = sxLen > 0 && syLen > 0 ? raw[0][0].length : 0;
        int padX = Math.max(2, sxLen / 2 + 1);
        int padZ = Math.max(2, szLen / 2 + 1);

        int baseplateCount = (sxLen + 2 * padX) * (szLen + 2 * padZ);
        int machineCount = countNonNull(raw);
        blocksTotal = baseplateCount + machineCount;

        Map<BlockPos, PhantasiaBlockInfo> blockMap = new HashMap<>(blocksTotal);
        Map<BlockPos, BlockPos> localToWorld = new HashMap<>(machineCount);
        Set<BlockPos> baseplatePos = new HashSet<>(baseplateCount);
        Set<BlockPos> bePos = new HashSet<>();

        phase = "Reading baseplate…";
        var _baseplateState0 = net.phoenixvine.phantasia.utils.PhantasiaBaseplateConfig.currentBaseplateBlockState();
        PhantasiaBlockInfo floor = _baseplateState0 != null ? PhantasiaBlockInfo.fromBlockState(_baseplateState0) :
                null;

        if (floor != null) for (int bx = -padX; bx < sxLen + padX; bx++) {
            for (int bz = -padZ; bz < szLen + padZ; bz++) {
                if (Thread.interrupted()) throw new InterruptedException();
                BlockPos wp = renderOrigin.offset(bx, -1, bz);
                blockMap.put(wp, floor);
                baseplatePos.add(wp);
                blocksPlaced++;
            }
        }

        phase = "Reading blocks…";

        for (int x = 0; x < raw.length; x++) {
            for (int y = 0; y < raw[x].length; y++) {
                for (int z = 0; z < raw[x][y].length; z++) {
                    if (Thread.interrupted()) throw new InterruptedException();

                    PhantasiaBlockInfo info = raw[x][y][z];
                    if (info == null) continue;

                    BlockState state = info.getBlockState();

                    if (state == null || state.isAir() ||
                            state.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE)
                        continue;

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

        phase = "Forming structure…";
        final Map<BlockPos, PhantasiaBlockInfo> blockMapSnapshot = Map.copyOf(blockMap);
        final Map<BlockPos, BlockPos> localToWorldSnapshot = Map.copyOf(localToWorld);
        Runnable postWriteTask = () -> definition.onShapeLoaded(sharedLevel, renderOrigin, blockMapSnapshot,
                localToWorldSnapshot);

        return buildResult(raw, blockMap, localToWorld, baseplatePos, bePos, renderOrigin, script, postWriteTask);
    }

    private static PhantasiaLoadedPattern buildResult(
                                                      PhantasiaBlockInfo[][][] raw,
                                                      Map<BlockPos, PhantasiaBlockInfo> blockMap,
                                                      Map<BlockPos, BlockPos> localToWorld,
                                                      Set<BlockPos> baseplatePos,
                                                      Set<BlockPos> bePos,
                                                      BlockPos origin,
                                                      PhantasiaScript script) {
        return buildResult(raw, blockMap, localToWorld, baseplatePos, bePos, origin, script, null);
    }

    private static PhantasiaLoadedPattern buildResult(
                                                      PhantasiaBlockInfo[][][] raw,
                                                      Map<BlockPos, PhantasiaBlockInfo> blockMap,
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

    private static int countNonNull(PhantasiaBlockInfo[][][] raw) {
        int n = 0;
        for (PhantasiaBlockInfo[][] layer : raw)
            for (PhantasiaBlockInfo[] row : layer)
                for (PhantasiaBlockInfo b : row) {
                    if (b == null) continue;
                    net.minecraft.world.level.block.state.BlockState s = b.getBlockState();
                    if (s == null || s.isAir() ||
                            s.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE)
                        continue;
                    n++;
                }
        return n;
    }
}
