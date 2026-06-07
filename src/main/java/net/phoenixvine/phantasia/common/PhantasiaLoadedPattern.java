package net.phoenixvine.phantasia.common;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;

import lombok.Getter;

import java.util.*;

public class PhantasiaLoadedPattern {

    public final Map<BlockPos, BlockInfo> blockMap;
    public final Map<BlockPos, BlockPos> localToWorld;
    public final Map<BlockPos, BlockPos> worldToLocal;
    public final Set<BlockPos> baseplatePositions;
    public final BlockPos controllerWorldPos;
    public final Set<BlockPos> blockEntityWorldPos;
    public final LinkedHashMap<String, Integer> shoppingList;

    public final List<List<BlockPos>> buildOrder;

    private final Map<BlockPos, Integer> posToGroupIndex;

    public final BlockPos origin;
    public final int minY;
    public final int maxY;
    public final MultiblockControllerMachine controller;

    @Getter
    public final PhantasiaScript script;

    public PhantasiaLoadedPattern(
            Map<BlockPos, BlockInfo> blockMap,
            Map<BlockPos, BlockPos> localToWorld,
            Set<BlockPos> baseplatePositions,
            BlockPos controllerWorldPos,
            Set<BlockPos> blockEntityWorldPos,
            BlockPos origin,
            int minY, int maxY,
            MultiblockControllerMachine controller,
            PhantasiaScript script) {
        this.blockMap = new HashMap<>(blockMap);
        this.localToWorld = Map.copyOf(localToWorld);
        this.baseplatePositions = Set.copyOf(baseplatePositions);
        this.controllerWorldPos = controllerWorldPos;
        this.blockEntityWorldPos = Set.copyOf(blockEntityWorldPos);
        this.origin = origin;
        this.minY = minY;
        this.maxY = maxY;
        this.controller = controller;
        this.script = script;

        Map<BlockPos, BlockPos> rev = new HashMap<>();
        for (Map.Entry<BlockPos, BlockPos> e : localToWorld.entrySet()) rev.put(e.getValue(), e.getKey());
        this.worldToLocal = Collections.unmodifiableMap(rev);

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockPos> e : localToWorld.entrySet()) {
            BlockInfo info = blockMap.get(e.getValue()); // world-pos key
            if (info == null) continue;
            BlockState state = info.getBlockState();
            if (state == null || state.isAir()) continue;
            counts.merge(state.getBlock().getName().getString(), 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        LinkedHashMap<String, Integer> sl = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : sorted) sl.put(e.getKey(), e.getValue());
        this.shoppingList = sl;

        List<List<BlockPos>> order = new ArrayList<>();
        Map<BlockPos, Integer> groupLookup = new HashMap<>();

        int currentGroupIndex = 0;
        for (int y = minY; y <= maxY; y++) {
            List<BlockPos> layer = new ArrayList<>();
            for (BlockPos lp : localToWorld.keySet()) {
                if (lp.getY() == y) {
                    layer.add(lp);
                    groupLookup.put(lp, currentGroupIndex);
                }
            }
            if (!layer.isEmpty()) {
                layer.sort(Comparator.comparingInt((BlockPos p) -> p.getX()).thenComparingInt(Vec3i::getZ));
                order.add(Collections.unmodifiableList(layer));
                currentGroupIndex++;
            }
        }
        this.buildOrder = Collections.unmodifiableList(order);
        this.posToGroupIndex = Collections.unmodifiableMap(groupLookup);
    }

    public Set<BlockPos> getRelativePositions() {
        return localToWorld.keySet();
    }

    public int getGroupIndex(BlockPos localPos) {
        return posToGroupIndex.getOrDefault(localPos, -1);
    }

    public BlockPos toWorld(BlockPos localPos) {
        return localToWorld.get(localPos);
    }

    public BlockPos toLocal(BlockPos worldPos) {
        return worldToLocal.get(worldPos);
    }

    public boolean isMachineBlock(BlockPos worldPos) {
        return worldToLocal.containsKey(worldPos);
    }

    public boolean hasBlockEntity(BlockPos worldPos) {
        return blockEntityWorldPos.contains(worldPos);
    }
}