package net.phoenixvine.phantasia.common.data.pattern;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import java.util.*;

public class PhantasiaScenePattern {

    public static class PlacementEntry {

        public final int index;
        public final String machineId;
        public final BlockPos offset;

        public final float centerX;
        public final float centerZ;

        public final Set<BlockPos> worldPositions;

        public final Set<BlockPos> baseplatePositions;
        public final int minY;
        public final int maxY;

        PlacementEntry(int index, String machineId, BlockPos offset,
                       Set<BlockPos> worldPositions, Set<BlockPos> baseplatePositions,
                       int minY, int maxY) {
            this.index = index;
            this.machineId = machineId;
            this.offset = offset;
            this.worldPositions = Collections.unmodifiableSet(worldPositions);
            this.baseplatePositions = Collections.unmodifiableSet(baseplatePositions);
            this.minY = minY;
            this.maxY = maxY;
            if (worldPositions.isEmpty()) {
                this.centerX = offset.getX();
                this.centerZ = offset.getZ();
            } else {
                float sx = 0, sz = 0;
                for (BlockPos p : worldPositions) {
                    sx += p.getX() + 0.5f;
                    sz += p.getZ() + 0.5f;
                }
                this.centerX = sx / worldPositions.size();
                this.centerZ = sz / worldPositions.size();
            }
        }

        public java.util.Map<BlockPos, BlockPos> computeLocalToWorld() {
            java.util.Map<BlockPos, BlockPos> map = new java.util.HashMap<>();
            for (BlockPos wp : worldPositions) map.put(wp.subtract(offset), wp);
            return map;
        }
    }

    public final List<PlacementEntry> placements;
    public final Map<BlockPos, PhantasiaBlockInfo> mergedBlockMap;
    public final Set<BlockPos> allBaseplatePositions;
    public final int minY;
    public final int maxY;

    private PhantasiaScenePattern(List<PlacementEntry> placements,
                                  Map<BlockPos, PhantasiaBlockInfo> mergedBlockMap,
                                  Set<BlockPos> allBaseplatePositions,
                                  int minY, int maxY) {
        this.placements = Collections.unmodifiableList(placements);
        this.mergedBlockMap = Collections.unmodifiableMap(mergedBlockMap);
        this.allBaseplatePositions = Collections.unmodifiableSet(allBaseplatePositions);
        this.minY = minY;
        this.maxY = maxY;
    }

    public static PhantasiaScenePattern build(PhantasiaSceneData sceneData,
                                              PhantasiaTrackedDummyWorld world) {
        List<PlacementEntry> placements = new ArrayList<>();
        Map<BlockPos, PhantasiaBlockInfo> mergedMap = new HashMap<>();
        Set<BlockPos> allBaseplates = new HashSet<>();
        int globalMinY = Integer.MAX_VALUE;
        int globalMaxY = Integer.MIN_VALUE;

        for (int i = 0; i < sceneData.placements.size(); i++) {
            PhantasiaSceneData.PlacementData pd = sceneData.placements.get(i);
            PlacementEntry entry = buildPlacement(i, pd, world, mergedMap, allBaseplates);
            if (entry == null) {
                net.phoenixvine.phantasia.Phantasia.LOGGER
                        .warn("[Phantasia/Scene] Could not build placement {} ({}) — skipping.", i, pd.machine);
                continue;
            }
            placements.add(entry);
            if (entry.minY < globalMinY) globalMinY = entry.minY;
            if (entry.maxY > globalMaxY) globalMaxY = entry.maxY;
        }

        if (placements.isEmpty()) return null;
        if (globalMinY == Integer.MAX_VALUE) globalMinY = 0;
        if (globalMaxY == Integer.MIN_VALUE) globalMaxY = 0;

        return new PhantasiaScenePattern(placements, mergedMap, allBaseplates,
                globalMinY, globalMaxY);
    }

    private static PlacementEntry buildPlacement(int index,
                                                 PhantasiaSceneData.PlacementData pd,
                                                 PhantasiaTrackedDummyWorld sharedWorld,
                                                 Map<BlockPos, PhantasiaBlockInfo> mergedMap,
                                                 Set<BlockPos> allBaseplates) {
        MultiblockMachineDefinition def = resolveMultiblockDefinition(pd.machine);
        if (def != null) {
            return buildMultiblockPlacement(index, pd, def, sharedWorld, mergedMap, allBaseplates);
        }

        return buildSingleblockPlacement(index, pd, sharedWorld, mergedMap, allBaseplates);
    }

    private static PlacementEntry buildMultiblockPlacement(int index,
                                                           PhantasiaSceneData.PlacementData pd,
                                                           MultiblockMachineDefinition def,
                                                           PhantasiaTrackedDummyWorld sharedWorld,
                                                           Map<BlockPos, PhantasiaBlockInfo> mergedMap,
                                                           Set<BlockPos> allBaseplates) {
        if (def == null) return null;

        List<MultiblockShapeInfo> shapes = def.getMatchingShapes();
        if (shapes == null || shapes.isEmpty()) return null;

        MultiblockShapeInfo shape = shapes.get(0);
        com.lowdragmc.lowdraglib.utils.BlockInfo[][][] rawLdlib = shape.getBlocks();
        if (rawLdlib == null || rawLdlib.length == 0) return null;
        PhantasiaBlockInfo[][][] raw = new PhantasiaBlockInfo[rawLdlib.length][][];
        for (int xi = 0; xi < rawLdlib.length; xi++) {
            raw[xi] = new PhantasiaBlockInfo[rawLdlib[xi].length][];
            for (int yi = 0; yi < rawLdlib[xi].length; yi++) {
                raw[xi][yi] = new PhantasiaBlockInfo[rawLdlib[xi][yi].length];
                for (int zi = 0; zi < rawLdlib[xi][yi].length; zi++) {
                    com.lowdragmc.lowdraglib.utils.BlockInfo bi = rawLdlib[xi][yi][zi];
                    raw[xi][yi][zi] = bi != null ? PhantasiaBlockInfo.fromBlockState(bi.getBlockState()) :
                            PhantasiaBlockInfo.EMPTY;
                }
            }
        }

        BlockPos origin = new BlockPos(pd.x, pd.y, pd.z);

        int sxLen = raw.length;
        int syLen = raw[0].length;
        int szLen = syLen > 0 ? raw[0][0].length : 0;
        int padX = Math.max(2, sxLen / 2 + 1);
        int padZ = Math.max(2, szLen / 2 + 1);

        Map<BlockPos, PhantasiaBlockInfo> placementMap = new HashMap<>();
        Map<BlockPos, BlockPos> localToWorld = new HashMap<>();
        Map<BlockPos, BlockEntity> cachedBEs = new HashMap<>();
        Set<BlockPos> baseplatePos = new HashSet<>();
        Set<BlockPos> bePos = new HashSet<>();
        BlockPos controllerWP = null;
        MultiblockControllerMachine controller = null;

        var _baseplateState0 = net.phoenixvine.phantasia.utils.PhantasiaBaseplateConfig.currentBaseplateBlockState();
        PhantasiaBlockInfo floor = _baseplateState0 != null ? PhantasiaBlockInfo.fromBlockState(_baseplateState0) :
                null;
        if (floor != null) for (int bx = -padX; bx < sxLen + padX; bx++)
            for (int bz = -padZ; bz < szLen + padZ; bz++) {
                BlockPos wp = origin.offset(bx, -1, bz);
                placementMap.put(wp, floor);
                baseplatePos.add(wp);
            }

        PhantasiaTrackedDummyWorld tempWorld = new PhantasiaTrackedDummyWorld();
        tempWorld.addBlocks(placementMap);

        for (int x = 0; x < raw.length; x++)
            for (int y = 0; y < raw[x].length; y++)
                for (int z = 0; z < raw[x][y].length; z++) {
                    PhantasiaBlockInfo info = raw[x][y][z];
                    if (info == null) continue;
                    BlockPos lp = new BlockPos(x, y, z);
                    BlockPos wp = origin.offset(x, y, z);
                    try {
                        var be = info.getBlockEntity(wp);
                        if (be instanceof MetaMachineBlockEntity mbe) {
                            mbe.setLevel(sharedWorld);
                            var machine = mbe.getMetaMachine();
                            if (machine instanceof MultiblockControllerMachine ctrl && controllerWP == null) {
                                controller = ctrl;
                                controllerWP = wp;
                            }
                            bePos.add(wp);
                            cachedBEs.put(wp, be);
                        }
                    } catch (Exception ignored) {}
                    placementMap.put(wp, info);
                    localToWorld.put(lp, wp);
                }

        sharedWorld.addBlocks(placementMap);
        for (BlockPos bp : bePos) {
            try {
                BlockEntity be = cachedBEs.get(bp);
                if (be != null) sharedWorld.setInnerBlockEntity(be);
            } catch (Exception ignored) {}
        }

        net.phoenixvine.phantasia.common.multiblock.PhantasiaMultiblockRegistry.resolve(pd.machine)
                .ifPresent(iDef -> iDef.onShapeLoaded(sharedWorld, origin, placementMap, localToWorld));

        mergedMap.putAll(placementMap);
        allBaseplates.addAll(baseplatePos);

        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (BlockPos lp : localToWorld.keySet()) {
            int wy = origin.getY() + lp.getY();
            if (wy < minY) minY = wy;
            if (wy > maxY) maxY = wy;
        }
        if (minY == Integer.MAX_VALUE) {
            minY = pd.y;
            maxY = pd.y;
        }

        Set<BlockPos> worldPositions = new HashSet<>(localToWorld.values());

        return new PlacementEntry(index, pd.machine, origin,
                worldPositions, baseplatePos, minY, maxY);
    }

    private static PlacementEntry buildSingleblockPlacement(int index,
                                                            PhantasiaSceneData.PlacementData pd,
                                                            PhantasiaTrackedDummyWorld sharedWorld,
                                                            Map<BlockPos, PhantasiaBlockInfo> mergedMap,
                                                            Set<BlockPos> allBaseplates) {
        BlockPos origin = new BlockPos(pd.x, pd.y, pd.z);

        PhantasiaBlockInfo blockInfo = resolveBlockInfo(pd.machine);
        if (blockInfo == null) {
            net.phoenixvine.phantasia.Phantasia.LOGGER.warn(
                    "[Phantasia/Scene] Could not resolve block for singleblock placement {} ({}) — skipping.", index,
                    pd.machine);
            return null;
        }

        Map<BlockPos, PhantasiaBlockInfo> placementMap = new HashMap<>();
        Set<BlockPos> baseplatePos = new HashSet<>();

        var _baseplateState1 = net.phoenixvine.phantasia.utils.PhantasiaBaseplateConfig.currentBaseplateBlockState();
        PhantasiaBlockInfo floor = _baseplateState1 != null ? PhantasiaBlockInfo.fromBlockState(_baseplateState1) :
                null;
        if (floor != null) for (int bx = -2; bx <= 2; bx++)
            for (int bz = -2; bz <= 2; bz++) {
                BlockPos wp = origin.offset(bx, -1, bz);
                placementMap.put(wp, floor);
                baseplatePos.add(wp);
            }

        placementMap.put(origin, blockInfo);

        try {
            var be = blockInfo.getBlockEntity(origin);
            if (be instanceof MetaMachineBlockEntity mbe) {
                mbe.setLevel(sharedWorld);
            }
            if (be != null) sharedWorld.setInnerBlockEntity(be);
        } catch (Exception ignored) {}

        sharedWorld.addBlocks(placementMap);
        mergedMap.putAll(placementMap);
        allBaseplates.addAll(baseplatePos);

        Set<BlockPos> worldPositions = Set.of(origin);
        return new PlacementEntry(index, pd.machine, origin,
                worldPositions, baseplatePos, origin.getY(), origin.getY());
    }

    private static PhantasiaBlockInfo resolveBlockInfo(String id) {
        try {
            net.minecraft.resources.ResourceLocation rl = id.contains(":") ?
                    new net.minecraft.resources.ResourceLocation(id) :
                    new net.minecraft.resources.ResourceLocation("gtceu", id);

            var machineDef = GTRegistries.MACHINES.get(rl);
            if (machineDef != null) {
                var block = machineDef.getBlock();
                if (block != null)
                    return PhantasiaBlockInfo.fromBlockState(block.defaultBlockState());
            }

            var block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(rl);
            if (block != null && block != net.minecraft.world.level.block.Blocks.AIR)
                return PhantasiaBlockInfo.fromBlockState(block.defaultBlockState());

        } catch (Exception ignored) {}
        return null;
    }

    public Set<BlockPos> computeVisible(PhantasiaSceneData.StepData step,
                                        PhantasiaSceneData sceneData) {
        Set<BlockPos> visible = new HashSet<>(allBaseplatePositions);

        for (PlacementEntry pe : placements) {
            PhantasiaSceneData.MachineOverride ov = step.getOverride(pe.index);

            String show = ov != null && ov.show != null ? ov.show : step.show;
            int layer = ov != null ? ov.layer : step.layer;
            int layerMin = ov != null ? ov.layerMin : step.layerMin;
            int layerMax = ov != null ? ov.layerMax : step.layerMax;
            int hideLayer = ov != null ? ov.hideLayer : step.hideLayer;
            List<int[]> positions = (ov != null && !ov.positions.isEmpty()) ? ov.positions : step.positions;
            List<int[]> hidePositions = (ov != null && !ov.hidePositions.isEmpty()) ? ov.hidePositions :
                    step.hidePositions;

            for (BlockPos wp : pe.worldPositions) {

                int relY = wp.getY() - pe.offset.getY();
                int relX = wp.getX() - pe.offset.getX();
                int relZ = wp.getZ() - pe.offset.getZ();

                if (!matchesShow(show, relX, relY, relZ, layer, layerMin, layerMax, positions))
                    continue;
                if (matchesHide(relY, relX, relZ, hideLayer, hidePositions))
                    continue;

                visible.add(wp);
            }
        }
        return visible;
    }

    private static boolean matchesShow(String show, int x, int y, int z,
                                       int layer, int layerMin, int layerMax,
                                       List<int[]> positions) {
        return switch (show == null ? "all" : show.toLowerCase(java.util.Locale.ROOT)) {
            case "layer" -> y == layer;
            case "layers" -> y >= layerMin && y <= layerMax;
            case "pos" -> !posListContains(positions, x, y, z);
            default -> true;
        };
    }

    private static boolean matchesHide(int y, int x, int z,
                                       int hideLayer, List<int[]> hidePositions) {
        if (hideLayer >= 0 && y == hideLayer) return true;
        return posListContains(hidePositions, x, y, z);
    }

    private static boolean posListContains(List<int[]> list, int x, int y, int z) {
        for (int[] p : list)
            if (p.length >= 3 && p[0] == x && p[1] == y && p[2] == z) return true;
        return false;
    }

    public PlacementEntry placementFor(BlockPos worldPos) {
        for (PlacementEntry pe : placements)
            if (pe.worldPositions.contains(worldPos) || pe.baseplatePositions.contains(worldPos))
                return pe;
        return null;
    }

    private static MultiblockMachineDefinition resolveMultiblockDefinition(String machineId) {
        try {
            ResourceLocation rl = machineId.contains(":") ? new ResourceLocation(machineId) :
                    new ResourceLocation("gtceu", machineId);
            var def = GTRegistries.MACHINES.get(rl);
            return def instanceof MultiblockMachineDefinition m ? m : null;
        } catch (Exception e) {
            return null;
        }
    }
}
