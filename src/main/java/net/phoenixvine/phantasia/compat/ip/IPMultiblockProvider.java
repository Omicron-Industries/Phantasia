package net.phoenixvine.phantasia.compat.ip;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockProvider;
import net.phoenixvine.phantasia.compat.ie.IEMultiblockDefinition;
import net.phoenixvine.phantasia.compat.ie.IEMultiblockProvider;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import blusunrize.immersiveengineering.api.multiblocks.MultiblockHandler;

import java.util.*;

public class IPMultiblockProvider implements IPhantasiaMultiblockProvider {

    private static final String NS = "immersivepetroleum";
    private final Map<ResourceLocation, IEMultiblockDefinition> cache = new HashMap<>();

    @Override
    public String getModId() {
        return NS;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public List<IPhantasiaMultiblockDefinition> getAllDefinitions() {
        List<IPhantasiaMultiblockDefinition> result = new ArrayList<>();
        try {
            for (MultiblockHandler.IMultiblock mb : MultiblockHandler.getMultiblocks()) {
                if (NS.equals(mb.getUniqueName().getNamespace()))
                    result.add(cache.computeIfAbsent(mb.getUniqueName(), k -> new IEMultiblockDefinition(mb)));
            }
        } catch (Exception ignored) {}
        return result;
    }

    @Override
    public Optional<IPhantasiaMultiblockDefinition> resolve(String machineId) {
        try {
            ResourceLocation rl = machineId.contains(":") ? new ResourceLocation(machineId) :
                    new ResourceLocation(NS, machineId);
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
    public boolean isControllerBlock(BlockState state) {
        try {
            for (MultiblockHandler.IMultiblock mb : MultiblockHandler.getMultiblocks()) {
                if (NS.equals(mb.getUniqueName().getNamespace()) && IEMultiblockProvider.isTrigger(mb, state))
                    return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    @Override
    public boolean isPartBlock(BlockState state) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return key != null && NS.equals(key.getNamespace());
    }

    @Override
    public Optional<IPhantasiaMultiblockDefinition> resolveFromItem(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)) return Optional.empty();
        BlockState def = bi.getBlock().defaultBlockState();
        try {
            for (MultiblockHandler.IMultiblock mb : MultiblockHandler.getMultiblocks()) {
                if (NS.equals(mb.getUniqueName().getNamespace()) && IEMultiblockProvider.isTrigger(mb, def)) {
                    ResourceLocation rl = mb.getUniqueName();
                    return Optional.of(cache.computeIfAbsent(rl, k -> new IEMultiblockDefinition(mb)));
                }
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }
}
