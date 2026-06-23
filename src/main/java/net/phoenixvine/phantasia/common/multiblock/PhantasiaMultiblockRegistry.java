package net.phoenixvine.phantasia.common.multiblock;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Central registry for all multiblock providers.
 * Each mod integration calls {@link #register} during mod init to plug in.
 */
public final class PhantasiaMultiblockRegistry {

    private static final List<IPhantasiaMultiblockProvider> PROVIDERS = new ArrayList<>();

    private PhantasiaMultiblockRegistry() {}

    public static void register(IPhantasiaMultiblockProvider provider) {
        PROVIDERS.add(provider);
    }

    public static List<IPhantasiaMultiblockProvider> getProviders() {
        return Collections.unmodifiableList(PROVIDERS);
    }

    /** Try each provider in registration order until one resolves the id. */
    public static Optional<IPhantasiaMultiblockDefinition> resolve(String machineId) {
        for (IPhantasiaMultiblockProvider p : PROVIDERS) {
            if (!p.isAvailable()) continue;
            Optional<IPhantasiaMultiblockDefinition> r = p.resolve(machineId);
            if (r.isPresent()) return r;
        }
        return Optional.empty();
    }

    /**
     * Resolve to a {@link PhantasiaBlockInfo} for single-block placements.
     * Checks each provider first, then falls back to the Forge block registry.
     */
    public static Optional<PhantasiaBlockInfo> resolveBlock(String id) {
        for (IPhantasiaMultiblockProvider p : PROVIDERS) {
            if (!p.isAvailable()) continue;
            Optional<PhantasiaBlockInfo> r = p.resolveBlock(id);
            if (r.isPresent()) return r;
        }
        // Forge block registry fallback
        try {
            ResourceLocation rl = id.contains(":") ? new ResourceLocation(id) : new ResourceLocation("minecraft", id);
            var block = ForgeRegistries.BLOCKS.getValue(rl);
            if (block != null && block != Blocks.AIR)
                return Optional.of(PhantasiaBlockInfo.fromBlockState(block.defaultBlockState()));
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    /** All definitions from all available providers. */
    public static List<IPhantasiaMultiblockDefinition> getAllDefinitions() {
        List<IPhantasiaMultiblockDefinition> all = new ArrayList<>();
        for (IPhantasiaMultiblockProvider p : PROVIDERS) {
            if (p.isAvailable()) all.addAll(p.getAllDefinitions());
        }
        return all;
    }

    public static boolean isControllerBlock(BlockState state) {
        for (IPhantasiaMultiblockProvider p : PROVIDERS) {
            if (p.isAvailable() && p.isControllerBlock(state)) return true;
        }
        return false;
    }

    public static boolean isPartBlock(BlockState state) {
        for (IPhantasiaMultiblockProvider p : PROVIDERS) {
            if (p.isAvailable() && p.isPartBlock(state)) return true;
        }
        return false;
    }

    public static boolean isFunctionalBlock(BlockState state) {
        for (IPhantasiaMultiblockProvider p : PROVIDERS) {
            if (p.isAvailable() && p.isFunctionalBlock(state)) return true;
        }
        return false;
    }

    /**
     * Resolve a looked-at {@link BlockState} to its controller definition.
     * Checks each provider for a controller block match.
     */
    public static Optional<IPhantasiaMultiblockDefinition> resolveFromBlock(BlockState state) {
        for (IPhantasiaMultiblockProvider p : PROVIDERS) {
            if (!p.isAvailable() || !p.isControllerBlock(state)) continue;
            // Try resolving by block registry key
            ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock());
            if (key != null) {
                Optional<IPhantasiaMultiblockDefinition> r = p.resolve(key.toString());
                if (r.isPresent()) return r;
            }
        }
        return Optional.empty();
    }

    /** Resolve a held item to a multiblock definition (delegates to each provider). */
    public static Optional<IPhantasiaMultiblockDefinition> resolveFromItem(ItemStack stack) {
        for (IPhantasiaMultiblockProvider p : PROVIDERS) {
            if (!p.isAvailable()) continue;
            Optional<IPhantasiaMultiblockDefinition> r = p.resolveFromItem(stack);
            if (r.isPresent()) return r;
        }
        return Optional.empty();
    }

    public static Optional<String> getExtraBlockInfo(BlockState state) {
        for (IPhantasiaMultiblockProvider p : PROVIDERS) {
            if (!p.isAvailable()) continue;
            Optional<String> info = p.getExtraBlockInfo(state);
            if (info.isPresent()) return info;
        }
        return Optional.empty();
    }
}
