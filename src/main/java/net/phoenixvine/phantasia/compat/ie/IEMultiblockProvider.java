package net.phoenixvine.phantasia.compat.ie;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockProvider;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import blusunrize.immersiveengineering.api.multiblocks.MultiblockHandler;

import java.util.*;

import javax.annotation.Nullable;

public class IEMultiblockProvider implements IPhantasiaMultiblockProvider {

    private final Map<ResourceLocation, IEMultiblockDefinition> cache = new HashMap<>();
    @Nullable
    private Set<Block> controllerBlockCache;

    @Override
    public String getModId() {
        return "immersiveengineering";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Optional<IPhantasiaMultiblockDefinition> resolve(String machineId) {
        try {
            ResourceLocation rl = machineId.contains(":") ? new ResourceLocation(machineId) :
                    new ResourceLocation("immersiveengineering", machineId);
            for (MultiblockHandler.IMultiblock mb : MultiblockHandler.getMultiblocks()) {
                if (mb.getUniqueName().equals(rl))
                    return Optional.of(cache.computeIfAbsent(rl, k -> new IEMultiblockDefinition(mb)));
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    @Override
    public Optional<PhantasiaBlockInfo> resolveBlock(String id) {
        return Optional.empty();
    }

    @Override
    public List<IPhantasiaMultiblockDefinition> getAllDefinitions() {
        List<IPhantasiaMultiblockDefinition> result = new ArrayList<>();
        try {
            for (MultiblockHandler.IMultiblock mb : MultiblockHandler.getMultiblocks())
                result.add(cache.computeIfAbsent(mb.getUniqueName(), k -> new IEMultiblockDefinition(mb)));
        } catch (Exception ignored) {}
        return result;
    }

    @Override
    public boolean isControllerBlock(BlockState state) {
        try {
            for (MultiblockHandler.IMultiblock mb : MultiblockHandler.getMultiblocks()) {
                if (isTrigger(mb, state)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static boolean isTrigger(MultiblockHandler.IMultiblock mb, BlockState state) {
        for (Direction dir : HORIZONTALS) {
            try {
                if (mb.isBlockTrigger(state, dir, null)) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private static final Direction[] HORIZONTALS = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    @Override
    public boolean isPartBlock(BlockState state) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (key == null || !"immersiveengineering".equals(key.getNamespace())) return false;
        return !isControllerBlock(state);
    }

    @Override
    public Optional<IPhantasiaMultiblockDefinition> resolveFromItem(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)) return Optional.empty();
        BlockState defaultState = bi.getBlock().defaultBlockState();
        try {
            for (MultiblockHandler.IMultiblock mb : MultiblockHandler.getMultiblocks()) {
                if (isTrigger(mb, defaultState)) {
                    ResourceLocation rl = mb.getUniqueName();
                    return Optional.of(cache.computeIfAbsent(rl, k -> new IEMultiblockDefinition(mb)));
                }
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }
}
