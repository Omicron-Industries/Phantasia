package net.phoenixvine.phantasia.common.multisetup;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import java.util.List;
import java.util.Map;

/**
 * A single recipe performable in a multi-component setup (e.g. one Enchanting Apparatus recipe).
 * The layout describes what blocks to place; itemPlacements describes what items to put on them.
 */
public interface IPhantasiaSetupRecipe {

    ResourceLocation getId();

    ItemStack getOutput();

    /** All input ingredients in display order (not positional). */
    List<ItemStack> getInputItems();

    /**
     * Block-position → item for each slot that holds an item during the recipe.
     * Positions are in layout-local space (same coordinate system as {@link #getLayout()}).
     */
    Map<BlockPos, ItemStack> getItemPlacements();

    /**
     * 3D block layout for this recipe — same format as {@link IPhantasiaMultiblockShape}
     * so the existing renderer can display it directly.
     * Indexed [x][y][z]; null cells are air.
     */
    PhantasiaBlockInfo[][][] getLayout();

    /** Source/mana cost, 0 for mods that don't use it. */
    int getSourceCost();
}
