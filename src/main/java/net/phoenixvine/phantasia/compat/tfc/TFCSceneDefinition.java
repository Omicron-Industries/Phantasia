package net.phoenixvine.phantasia.compat.tfc;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
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

public class TFCSceneDefinition implements IPhantasiaMultiblockDefinition {

    private final ResourceLocation id;
    private final String displayName;

    private final String iconId;
    private final TFCSceneShape shape;
    private final PhantasiaScriptData defaultScript;
    @Nullable
    private ItemStack cachedIcon;

    public TFCSceneDefinition(ResourceLocation id, String displayName, String iconId,
                              TFCSceneShape shape, PhantasiaScriptData defaultScript) {
        this.id = id;
        this.displayName = displayName;
        this.iconId = iconId;
        this.shape = shape;
        this.defaultScript = defaultScript;
    }

    @Override
    public ResourceLocation getId() {
        return id;
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
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public ItemStack getIcon() {
        if (cachedIcon == null) {
            ResourceLocation rl = iconId.contains(":") ? new ResourceLocation(iconId) :
                    new ResourceLocation("tfc", iconId);
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item != null && item != Items.AIR) {
                cachedIcon = new ItemStack(item);
            } else {
                Block b = ForgeRegistries.BLOCKS.getValue(rl);
                if (b != null && b != Blocks.AIR) {
                    ItemStack s = new ItemStack(b);
                    cachedIcon = s.isEmpty() ? ItemStack.EMPTY : s;
                } else {
                    cachedIcon = ItemStack.EMPTY;
                }
            }
        }
        return cachedIcon;
    }

    @Override
    @Nullable
    public PhantasiaScriptData getDefaultScriptData() {
        return defaultScript;
    }

    @Override
    public void applyWorkingState(PhantasiaTrackedDummyWorld level,
                                  Set<BlockPos> positions,
                                  Map<BlockPos, PhantasiaBlockInfo> blockMap,
                                  boolean working) {
        for (BlockPos pos : positions) {
            if (working) {
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) continue;
                BlockState active = applyActiveState(state);
                if (active == state) continue;
                level.setBlock(pos, active, 0);
            } else {
                PhantasiaBlockInfo original = blockMap.get(pos);
                if (original == null || original.getBlockState().isAir()) continue;
                BlockState current = level.getBlockState(pos);
                if (current == original.getBlockState()) continue;
                level.setBlock(pos, original.getBlockState(), 0);
            }
        }
    }

    private static BlockState applyActiveState(BlockState state) {
        for (Property<?> prop : List.copyOf(state.getProperties())) {
            String name = prop.getName();
            if (prop instanceof BooleanProperty bp) {
                if (name.equals("lit") || name.equals("burning") || name.equals("active")) {
                    state = state.setValue(bp, true);
                }

            } else if (prop instanceof IntegerProperty ip) {
                if (name.equals("heat_level")) {
                    state = state.setValue(ip, 7);
                } else if (name.equals("stage")) {
                    state = state.setValue(ip, 8);
                }
            }
        }
        return state;
    }

    static PhantasiaBlockInfo tfc(String id) {
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("tfc", id));
        if (b == null || b == Blocks.AIR) {
            net.phoenixvine.phantasia.Phantasia.LOGGER.warn("[Phantasia/TFC] Block not found: tfc:{}", id);
            return PhantasiaBlockInfo.EMPTY;
        }
        return PhantasiaBlockInfo.fromBlockState(b.defaultBlockState());
    }

    static PhantasiaScriptData script(String machineId) {
        return new PhantasiaScriptData(machineId);
    }

    static PhantasiaScriptData.StepData step(int tick, String caption, String show) {
        PhantasiaScriptData.StepData s = new PhantasiaScriptData.StepData();
        s.tick = tick;
        s.caption = caption;
        if (show.startsWith("layer:")) {
            s.show = "layer";
            s.layer = Integer.parseInt(show.substring(6));
        } else if (show.startsWith("layers:")) {
            s.show = "layers";
            String[] parts = show.substring(7).split("-");
            s.layerMin = Integer.parseInt(parts[0]);
            s.layerMax = Integer.parseInt(parts[1]);
        } else {
            s.show = show;
        }
        return s;
    }

    static PhantasiaScriptData.StepData workingStep(int tick, String caption, String show) {
        PhantasiaScriptData.StepData s = step(tick, caption, show);
        s.working = true;
        return s;
    }
}
