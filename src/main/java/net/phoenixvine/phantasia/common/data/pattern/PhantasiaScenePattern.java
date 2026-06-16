package net.phoenixvine.phantasia.common.data.pattern;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData;

import java.util.*;

/**
 * PhantasiaScenePattern
 *
 * The merged world representation of a {@link PhantasiaSceneData}.
 * Built when the scene editor opens, and rebuilt whenever placements change.
 *
 * Each placement uses {@link MultiblockMachineDefinition#getMatchingShapes()} to get
 * its first available shape (index 0), then stamps it into a temporary isolated
 * {@link PhantasiaTrackedDummyWorld} — mirroring exactly what
 * does — before merging the resulting block map into the shared scene world at the
 * declared offset.
 */
public class PhantasiaScenePattern {

    // ── Per-placement data ────────────────────────────────────────────────────

    public static class PlacementEntry {

        public final int index;
        public final String machineId;
        public final BlockPos offset;
        /** World positions belonging to this placement (non-baseplate). */
        public final Set<BlockPos> worldPositions;
        /** World positions of this placement's baseplate. */
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
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    public final List<PlacementEntry> placements;
    public final Map<BlockPos, BlockInfo> mergedBlockMap;
    public final Set<BlockPos> allBaseplatePositions;
    public final int minY;
    public final int maxY;

    private PhantasiaScenePattern(List<PlacementEntry> placements,
                                  Map<BlockPos, BlockInfo> mergedBlockMap,
                                  Set<BlockPos> allBaseplatePositions,
                                  int minY, int maxY) {
        this.placements = Collections.unmodifiableList(placements);
        this.mergedBlockMap = Collections.unmodifiableMap(mergedBlockMap);
        this.allBaseplatePositions = Collections.unmodifiableSet(allBaseplatePositions);
        this.minY = minY;
        this.maxY = maxY;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Builds the pattern and populates the given world with all blocks.
     *
     * @param sceneData the scene definition
     * @param world     an empty PhantasiaTrackedDummyWorld to populate
     * @return the built pattern, or null if no placements resolved
     */
    public static PhantasiaScenePattern build(PhantasiaSceneData sceneData,
                                              PhantasiaTrackedDummyWorld world) {
        List<PlacementEntry> placements = new ArrayList<>();
        Map<BlockPos, BlockInfo> mergedMap = new HashMap<>();
        Set<BlockPos> allBaseplates = new HashSet<>();
        int globalMinY = Integer.MAX_VALUE;
        int globalMaxY = Integer.MIN_VALUE;

        for (int i = 0; i < sceneData.placements.size(); i++) {
            PhantasiaSceneData.PlacementData pd = sceneData.placements.get(i);
            PlacementEntry entry = buildPlacement(i, pd, world, mergedMap, allBaseplates);
            if (entry == null) {
                System.err.println(
                        "[Phantasia/Scene] Could not build placement " + i + " (" + pd.machine + ") — skipping.");
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
                                                 Map<BlockPos, BlockInfo> mergedMap,
                                                 Set<BlockPos> allBaseplates) {
        // ── Try multiblock first ──────────────────────────────────────────────
        MultiblockMachineDefinition def = resolveMultiblockDefinition(pd.machine);
        if (def != null) {
            return buildMultiblockPlacement(index, pd, def, sharedWorld, mergedMap, allBaseplates);
        }

        // ── Fall back to singleblock (any MachineDefinition or plain block) ───
        return buildSingleblockPlacement(index, pd, sharedWorld, mergedMap, allBaseplates);
    }

    private static PlacementEntry buildMultiblockPlacement(int index,
                                                           PhantasiaSceneData.PlacementData pd,
                                                           MultiblockMachineDefinition def,
                                                           PhantasiaTrackedDummyWorld sharedWorld,
                                                           Map<BlockPos, BlockInfo> mergedMap,
                                                           Set<BlockPos> allBaseplates) {
        if (def == null) return null;

        List<MultiblockShapeInfo> shapes = def.getMatchingShapes();
        if (shapes == null || shapes.isEmpty()) return null;

        // Use shape 0 — the canonical/default shape for this machine.
        MultiblockShapeInfo shape = shapes.get(0);
        BlockInfo[][][] raw = shape.getBlocks();
        if (raw == null || raw.length == 0) return null;

        // Declared scene-space origin for this placement
        BlockPos origin = new BlockPos(pd.x, pd.y, pd.z);

        int sxLen = raw.length;
        int syLen = raw[0].length;
        int szLen = syLen > 0 ? raw[0][0].length : 0;
        int padX = Math.max(2, sxLen / 2 + 1);
        int padZ = Math.max(2, szLen / 2 + 1);

        Map<BlockPos, BlockInfo> placementMap = new HashMap<>();
        Map<BlockPos, BlockPos> localToWorld = new HashMap<>();
        Set<BlockPos> baseplatePos = new HashSet<>();
        Set<BlockPos> bePos = new HashSet<>();
        BlockPos controllerWP = null;
        MultiblockControllerMachine controller = null;

        // Baseplate
        var _baseplateState0 = net.phoenixvine.phantasia.utils.PhantasiaTheme.currentBaseplateBlockState();
        BlockInfo floor = _baseplateState0 != null ? BlockInfo.fromBlockState(_baseplateState0) : null;
        if (floor != null) for (int bx = -padX; bx <= sxLen + padX; bx++)
            for (int bz = -padZ; bz <= szLen + padZ; bz++) {
                BlockPos wp = origin.offset(bx, -1, bz);
                placementMap.put(wp, floor);
                baseplatePos.add(wp);
            }

        // Machine blocks — use a temporary isolated world for BE initialisation
        // so block entities get the right level reference before we merge.
        // FIX: Pass active client level into the constructor instance
        PhantasiaTrackedDummyWorld tempWorld = new PhantasiaTrackedDummyWorld();
        tempWorld.addBlocks(placementMap); // add baseplate first

        for (int x = 0; x < raw.length; x++)
            for (int y = 0; y < raw[x].length; y++)
                for (int z = 0; z < raw[x][y].length; z++) {
                    BlockInfo info = raw[x][y][z];
                    if (info == null) continue;
                    BlockPos lp = new BlockPos(x, y, z);
                    BlockPos wp = origin.offset(x, y, z);
                    try {
                        var be = info.getBlockEntity(wp);
                        if (be instanceof MetaMachineBlockEntity mbe) {
                            mbe.setLevel(sharedWorld); // use shared world for rendering
                            var machine = mbe.getMetaMachine();
                            if (machine instanceof MultiblockControllerMachine ctrl && controllerWP == null) {
                                controller = ctrl;
                                controllerWP = wp;
                            }
                            bePos.add(wp);
                        }
                    } catch (Exception ignored) {}
                    placementMap.put(wp, info);
                    localToWorld.put(lp, wp);
                }

        // Stamp into shared world and register BEs
        sharedWorld.addBlocks(placementMap);
        for (BlockPos bp : bePos) {
            try {
                BlockInfo info = placementMap.get(bp);
                if (info == null) continue;
                var be = info.getBlockEntity(bp);
                if (be != null) sharedWorld.setInnerBlockEntity(be);
            } catch (Exception ignored) {}
        }

        // Fire onStructureFormed so machine state is correct for rendering
        if (controller != null) {
            try {
                BlockPattern pat = controller.getPattern();
                if (pat != null && pat.checkPatternAt(controller.getMultiblockState(), true))
                    controller.onStructureFormed();
            } catch (Exception ignored) {}
        }

        // Merge into scene map
        mergedMap.putAll(placementMap);
        allBaseplates.addAll(baseplatePos);

        // Compute Y bounds from machine blocks (not baseplate)
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

        // World positions = all machine blocks (non-baseplate)
        Set<BlockPos> worldPositions = new HashSet<>(localToWorld.values());

        return new PlacementEntry(index, pd.machine, origin,
                worldPositions, baseplatePos, minY, maxY);
    }

    /**
     * Builds a placement for a singleblock machine or plain block.
     * Places one block at the declared origin, with a small baseplate around it.
     * Works for any GTCEu MachineDefinition (including singleblock machines) as
     * well as any block registered in the Forge block registry.
     */
    private static PlacementEntry buildSingleblockPlacement(int index,
                                                            PhantasiaSceneData.PlacementData pd,
                                                            PhantasiaTrackedDummyWorld sharedWorld,
                                                            Map<BlockPos, BlockInfo> mergedMap,
                                                            Set<BlockPos> allBaseplates) {
        BlockPos origin = new BlockPos(pd.x, pd.y, pd.z);

        // Resolve block: try GTCEu machine registry first, then Forge block registry
        BlockInfo blockInfo = resolveBlockInfo(pd.machine);
        if (blockInfo == null) {
            System.err.println("[Phantasia/Scene] Could not resolve block for singleblock placement " + index + " (" +
                    pd.machine + ") — skipping.");
            return null;
        }

        Map<BlockPos, BlockInfo> placementMap = new HashMap<>();
        Set<BlockPos> baseplatePos = new HashSet<>();

        // Small 5×5 baseplate centered on origin
        var _baseplateState1 = net.phoenixvine.phantasia.utils.PhantasiaTheme.currentBaseplateBlockState();
        BlockInfo floor = _baseplateState1 != null ? BlockInfo.fromBlockState(_baseplateState1) : null;
        if (floor != null) for (int bx = -2; bx <= 2; bx++)
            for (int bz = -2; bz <= 2; bz++) {
                BlockPos wp = origin.offset(bx, -1, bz);
                placementMap.put(wp, floor);
                baseplatePos.add(wp);
            }

        // The single machine block at origin
        placementMap.put(origin, blockInfo);

        // Register block entity if present
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

    /**
     * Resolves a machine/block ID to a {@link BlockInfo}.
     * Tries the GTCEu machine registry first (for singleblock MetaMachines),
     * then falls back to the Forge block registry.
     */
    private static BlockInfo resolveBlockInfo(String id) {
        try {
            net.minecraft.resources.ResourceLocation rl = id.contains(":") ?
                    new net.minecraft.resources.ResourceLocation(id) :
                    new net.minecraft.resources.ResourceLocation("gtceu", id);

            // GTCEu machine registry
            var machineDef = GTRegistries.MACHINES.get(rl);
            if (machineDef != null) {
                var block = machineDef.getBlock();
                if (block != null)
                    return BlockInfo.fromBlockState(block.defaultBlockState());
            }

            // Forge block registry fallback
            var block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(rl);
            if (block != null && block != net.minecraft.world.level.block.Blocks.AIR)
                return BlockInfo.fromBlockState(block.defaultBlockState());

        } catch (Exception ignored) {}
        return null;
    }

    // ── Visibility ────────────────────────────────────────────────────────────

    /**
     * Computes the set of world positions that should be visible for a given step,
     * applying the global show mode then per-placement overrides.
     */
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
                // Placement-relative coords for layer/pos filtering
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
            case "pos" -> posListContains(positions, x, y, z);
            default -> true; // "all" and anything unrecognised
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

    // ── Lookup ────────────────────────────────────────────────────────────────

    /** Returns which placement owns the given world position, or null. */
    public PlacementEntry placementFor(BlockPos worldPos) {
        for (PlacementEntry pe : placements)
            if (pe.worldPositions.contains(worldPos) || pe.baseplatePositions.contains(worldPos))
                return pe;
        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
