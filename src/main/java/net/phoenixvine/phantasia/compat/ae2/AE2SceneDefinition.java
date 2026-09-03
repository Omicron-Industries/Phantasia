package net.phoenixvine.phantasia.compat.ae2;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
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

public class AE2SceneDefinition implements IPhantasiaMultiblockDefinition {

    private final ResourceLocation id;
    private final String displayName;
    private final String iconBlockId;
    private final AE2SceneShape shape;
    private final PhantasiaScriptData defaultScript;
    @Nullable
    private ItemStack cachedIcon;

    public AE2SceneDefinition(ResourceLocation id, String displayName, String iconBlockId,
                              AE2SceneShape shape, PhantasiaScriptData defaultScript) {
        this.id = id;
        this.displayName = displayName;
        this.iconBlockId = iconBlockId;
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
            Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("ae2", iconBlockId));
            cachedIcon = (b != null && b != Blocks.AIR) ? new ItemStack(b) : ItemStack.EMPTY;
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
                if (name.equals("formed") || name.equals("powered") || name.equals("active")) {
                    state = state.setValue(bp, true);
                }
            } else if (prop instanceof EnumProperty ep) {
                if (name.equals("state")) {

                    for (Comparable val : (Collection<Comparable>) ep.getPossibleValues()) {
                        if ("online".equals(val.toString())) {
                            state = state.setValue(ep, val);
                            break;
                        }
                    }
                }
            }
        }
        return state;
    }

    static PhantasiaBlockInfo ae2(String aeBlockId) {
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("ae2", aeBlockId));
        if (b == null || b == Blocks.AIR) {
            net.phoenixvine.phantasia.Phantasia.LOGGER.warn("[Phantasia/AE2] Block not found: ae2:{}", aeBlockId);
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
