package net.phoenixvine.phantasia.common.multisetup;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import java.util.List;
import java.util.Map;

public interface IPhantasiaSetupRecipe {

    ResourceLocation getId();

    ItemStack getOutput();

    List<ItemStack> getInputItems();

    Map<BlockPos, ItemStack> getItemPlacements();

    PhantasiaBlockInfo[][][] getLayout();

    int getSourceCost();
}
