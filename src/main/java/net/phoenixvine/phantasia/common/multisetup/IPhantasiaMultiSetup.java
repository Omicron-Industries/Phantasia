package net.phoenixvine.phantasia.common.multisetup;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A multi-component setup that performs recipes — e.g. Enchanting Apparatus, Imbuement Chamber.
 * Unlike {@link net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition},
 * these are recipe-centric: the structure exists only to perform one recipe at a time,
 * not as a persistent machine.
 */
public interface IPhantasiaMultiSetup {

    ResourceLocation getId();

    String getDisplayName();

    ItemStack getIcon();

    /**
     * Base block layout for this setup with no items placed — just the structural blocks.
     * Used as a fallback when no recipe is selected.
     */
    BlockInfo[][][] getBaseLayout();

    /** All recipes this setup can perform, lazily loaded from the recipe manager. */
    List<IPhantasiaSetupRecipe> getRecipes();
}
