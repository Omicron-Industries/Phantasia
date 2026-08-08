package net.phoenixvine.phantasia.common.multisetup;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import java.util.List;

public interface IPhantasiaMultiSetup {

    ResourceLocation getId();

    String getDisplayName();

    ItemStack getIcon();

    PhantasiaBlockInfo[][][] getBaseLayout();

    List<IPhantasiaSetupRecipe> getRecipes();
}
