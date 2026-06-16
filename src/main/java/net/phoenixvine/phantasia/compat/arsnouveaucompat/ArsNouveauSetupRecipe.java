package net.phoenixvine.phantasia.compat.arsnouveaucompat;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.phantasia.common.multisetup.IPhantasiaSetupRecipe;

import java.util.List;
import java.util.Map;

/** Wraps a single Ars Nouveau recipe as a Phantasia setup recipe. */
public class ArsNouveauSetupRecipe implements IPhantasiaSetupRecipe {

    private final ResourceLocation id;
    private final ItemStack output;
    private final List<ItemStack> inputs;
    private final int sourceCost;
    private final BlockInfo[][][] layout;
    private final Map<BlockPos, ItemStack> itemPlacements;

    public ArsNouveauSetupRecipe(
            ResourceLocation id,
            ItemStack output,
            List<ItemStack> inputs,
            int sourceCost,
            BlockInfo[][][] layout,
            Map<BlockPos, ItemStack> itemPlacements) {
        this.id = id;
        this.output = output;
        this.inputs = inputs;
        this.sourceCost = sourceCost;
        this.layout = layout;
        this.itemPlacements = itemPlacements;
    }

    @Override public ResourceLocation getId() { return id; }
    @Override public ItemStack getOutput() { return output; }
    @Override public List<ItemStack> getInputItems() { return inputs; }
    @Override public Map<BlockPos, ItemStack> getItemPlacements() { return itemPlacements; }
    @Override public BlockInfo[][][] getLayout() { return layout; }
    @Override public int getSourceCost() { return sourceCost; }
}
