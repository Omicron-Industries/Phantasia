package net.phoenixvine.phantasia.common.multiblock;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * Registered by each mod integration (GTCEu, etc.) to supply multiblock definitions
 * and block-type queries to the rest of Phantasia.
 */
public interface IPhantasiaMultiblockProvider {

    /** Mod ID this provider serves (e.g. "gtceu"). */
    String getModId();

    /** True if the backing mod is currently loaded. */
    boolean isAvailable();

    /**
     * Resolve a machine/block ID string to a multiblock definition.
     * The id may be a full "namespace:path" ResourceLocation or a bare path
     * (the provider decides the default namespace).
     */
    Optional<IPhantasiaMultiblockDefinition> resolve(String machineId);

    /**
     * Resolve an id string to a {@link BlockInfo} for single-block placements.
     * Falls through to the Forge block registry automatically in
     * {@link PhantasiaMultiblockRegistry}.
     */
    Optional<BlockInfo> resolveBlock(String id);

    /** All multiblock definitions this provider exposes (used to populate PHANTASIA_SCENES). */
    List<IPhantasiaMultiblockDefinition> getAllDefinitions();

    boolean isControllerBlock(BlockState state);

    boolean isPartBlock(BlockState state);

    default boolean isFunctionalBlock(BlockState state) {
        return isControllerBlock(state) || isPartBlock(state);
    }

    /**
     * Try to resolve a held {@link ItemStack} to a multiblock definition
     * (e.g. for GTCEu: check if the item is a {@code MetaMachineItem}).
     */
    default Optional<IPhantasiaMultiblockDefinition> resolveFromItem(ItemStack stack) {
        return Optional.empty();
    }

    /** Optional: extra human-readable info for the block inspect screen. */
    default Optional<String> getExtraBlockInfo(BlockState state) {
        return Optional.empty();
    }
}
