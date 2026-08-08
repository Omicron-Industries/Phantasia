package net.phoenixvine.phantasia.common.multiblock;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import java.util.List;
import java.util.Optional;

public interface IPhantasiaMultiblockProvider {

    String getModId();

    boolean isAvailable();

    Optional<IPhantasiaMultiblockDefinition> resolve(String machineId);

    Optional<PhantasiaBlockInfo> resolveBlock(String id);

    List<IPhantasiaMultiblockDefinition> getAllDefinitions();

    boolean isControllerBlock(BlockState state);

    boolean isPartBlock(BlockState state);

    default boolean isFunctionalBlock(BlockState state) {
        return isControllerBlock(state) || isPartBlock(state);
    }

    default Optional<IPhantasiaMultiblockDefinition> resolveFromItem(ItemStack stack) {
        return Optional.empty();
    }

    default Optional<String> getExtraBlockInfo(BlockState state) {
        return Optional.empty();
    }
}
