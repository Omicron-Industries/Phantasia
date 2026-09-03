package net.phoenixvine.phantasia.compat.eae;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

public class EAESceneDefinition implements IPhantasiaMultiblockDefinition {

    private final ResourceLocation id;
    private final String displayName;

    private final String iconId;
    private final EAESceneShape shape;
    private final PhantasiaScriptData defaultScript;

    public EAESceneDefinition(ResourceLocation id, String displayName, String iconId,
                              EAESceneShape shape, PhantasiaScriptData defaultScript) {
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
        ResourceLocation rl = iconId.contains(":") ? new ResourceLocation(iconId) :
                new ResourceLocation("expatternprovider", iconId);
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item != null && item != Items.AIR) return new ItemStack(item);
        Block b = ForgeRegistries.BLOCKS.getValue(rl);
        if (b != null && b != Blocks.AIR) {
            ItemStack s = new ItemStack(b);
            if (!s.isEmpty()) return s;
        }
        return ItemStack.EMPTY;
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
                if (active.getBlock() instanceof EntityBlock eb) {
                    BlockEntity be = eb.newBlockEntity(pos, active);
                    if (be != null) {
                        be.setLevel(level);
                        level.setBlockEntity(be);
                    }
                }
            } else {
                PhantasiaBlockInfo original = blockMap.get(pos);
                if (original == null || original.getBlockState().isAir()) continue;
                BlockState current = level.getBlockState(pos);
                if (current == original.getBlockState()) continue;
                level.setBlock(pos, original.getBlockState(), 0);
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static BlockState applyActiveState(BlockState state) {
        for (Property<?> prop : List.copyOf(state.getProperties())) {
            String name = prop.getName();
            if (prop instanceof BooleanProperty bp) {
                if (name.equals("formed") || name.equals("powered") || name.equals("active") ||
                        name.equals("working")) {
                    state = state.setValue(bp, true);
                }
            } else if (prop instanceof EnumProperty ep) {
                if (name.equals("state")) {
                    for (Comparable val : (Collection<Comparable>) ep.getPossibleValues()) {
                        if ("online".equals(val.toString()) || "active".equals(val.toString())) {
                            state = state.setValue(ep, val);
                            break;
                        }
                    }
                }
            }
        }
        return state;
    }

    static PhantasiaBlockInfo eae(String blockId) {
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("expatternprovider", blockId));
        if (b == null || b == Blocks.AIR) {
            net.phoenixvine.phantasia.Phantasia.LOGGER.warn("[Phantasia/EAE] Block not found: expatternprovider:{}",
                    blockId);
            return PhantasiaBlockInfo.EMPTY;
        }
        return PhantasiaBlockInfo.fromBlockState(b.defaultBlockState());
    }

    static PhantasiaBlockInfo ae2(String blockId) {
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("ae2", blockId));
        if (b == null || b == Blocks.AIR) {
            net.phoenixvine.phantasia.Phantasia.LOGGER.warn("[Phantasia/EAE] Block not found: ae2:{}", blockId);
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
