package net.phoenixvine.phantasia.compat.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

public class PhantasiaCustomMultiblockDefinition implements IPhantasiaMultiblockDefinition {

    private final PhantasiaCustomLayout layout;
    private final PhantasiaCustomShape shape;

    public PhantasiaCustomMultiblockDefinition(PhantasiaCustomLayout layout) {
        this.layout = layout;
        this.shape = new PhantasiaCustomShape(layout);
    }

    public PhantasiaCustomLayout getLayout() {
        return layout;
    }

    public PhantasiaCustomShape getShape() {
        return shape;
    }

    @Override
    public ResourceLocation getId() {
        String id = layout.id;
        if (id == null || !id.contains(":")) id = "phantasia:" + (id != null ? id : "unnamed");
        return new ResourceLocation(id);
    }

    @Override
    public String getDisplayName() {
        return layout.displayName != null ? layout.displayName : "Custom Scene";
    }

    @Override
    public ItemStack getIcon() {
        if (layout.iconBlock != null && !layout.iconBlock.isBlank()) {
            ResourceLocation rl = new ResourceLocation(layout.iconBlock);
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item != null && item != Items.AIR) return new ItemStack(item);
            Block block = ForgeRegistries.BLOCKS.getValue(rl);
            if (block != null && block != Blocks.AIR) return new ItemStack(block);
        }

        for (String blockStr : layout.blocks.values()) {
            Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(
                    blockStr.contains("[") ? blockStr.substring(0, blockStr.indexOf('[')) : blockStr));
            if (b != null && b != Blocks.AIR) return new ItemStack(b);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public List<IPhantasiaMultiblockShape> getMatchingShapes() {
        return List.of(shape);
    }

    @Override
    public List<IPhantasiaMultiblockShape> getAllShapes() {
        return List.of(shape);
    }

    @Override
    @Nullable
    public PhantasiaScriptData getDefaultScriptData() {
        return null;
    }

    @Override
    public void applyWorkingState(PhantasiaTrackedDummyWorld level, Set<BlockPos> positions,
                                  Map<BlockPos, PhantasiaBlockInfo> blockMap, boolean working) {}
}
