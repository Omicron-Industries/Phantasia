package net.phoenixvine.phantasia.compat.ie;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import blusunrize.immersiveengineering.api.IEProperties;
import blusunrize.immersiveengineering.api.multiblocks.MultiblockHandler;
import blusunrize.immersiveengineering.api.multiblocks.TemplateMultiblock;
import blusunrize.immersiveengineering.api.multiblocks.blocks.registry.MultiblockPartBlock;
import blusunrize.immersiveengineering.common.blocks.multiblocks.IETemplateMultiblock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

public class IEMultiblockDefinition implements IPhantasiaMultiblockDefinition {

    private static final Logger LOGGER = LoggerFactory.getLogger(IEMultiblockDefinition.class);

    private final MultiblockHandler.IMultiblock multiblock;

    @Nullable
    private List<IPhantasiaMultiblockShape> cachedShapes;
    @Nullable
    private Block cachedControllerBlock;
    private boolean controllerResolved = false;

    @Nullable
    private BlockPos lastMasterWorldPos;

    @Nullable
    private BlockPos masterLocalPos;

    private final Map<BlockPos, BlockPos> worldToLocal = new HashMap<>();

    private final Map<BlockPos, BlockState> savedStates = new HashMap<>();

    @SuppressWarnings({ "deprecation", "DataFlowIssue" })
    private static final net.minecraft.world.level.block.entity.BlockEntityType<?> DUMMY_BE_TYPE = net.minecraft.world.level.block.entity.BlockEntityType.Builder
            .of((pos, state) -> null)
            .build(null);

    @SuppressWarnings("deprecation")
    private static final class ModelOffsetBE extends BlockEntity {

        private final ModelData modelData;

        ModelOffsetBE(BlockPos pos, BlockState state, BlockPos submodelOffset) {
            super(null, pos, state);
            this.modelData = ModelData.builder()
                    .with(IEProperties.Model.SUBMODEL_OFFSET, submodelOffset)
                    .build();
        }

        @Override
        public net.minecraft.world.level.block.entity.BlockEntityType<?> getType() {
            return DUMMY_BE_TYPE;
        }

        @Override
        public ModelData getModelData() {
            return modelData;
        }
    }

    public IEMultiblockDefinition(MultiblockHandler.IMultiblock multiblock) {
        this.multiblock = multiblock;
    }

    public MultiblockHandler.IMultiblock getMultiblock() {
        return multiblock;
    }

    @Override
    public ResourceLocation getId() {
        return multiblock.getUniqueName();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public List<IPhantasiaMultiblockShape> getMatchingShapes() {
        if (cachedShapes != null) return cachedShapes;
        if (!(multiblock instanceof TemplateMultiblock tmb)) return List.of();

        var level = Minecraft.getInstance().level;
        if (level == null) return List.of();

        try {
            List<StructureTemplate.StructureBlockInfo> blocks = tmb.getStructure(level);
            if (blocks == null || blocks.isEmpty()) return List.of();
            cachedShapes = List.of(new IEMultiblockShape(blocks));
        } catch (Exception e) {
            LOGGER.warn("[Phantasia/IE] Failed to load structure for {}: {}", getId(), e.getMessage());
        }
        return cachedShapes != null ? cachedShapes : List.of();
    }

    @Override
    public List<IPhantasiaMultiblockShape> getAllShapes() {
        return getMatchingShapes();
    }

    @Override
    public String getDisplayName() {
        String path = multiblock.getUniqueName().getPath();
        return Arrays.stream(path.split("_"))
                .filter(s -> !s.isEmpty())
                .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
                .collect(Collectors.joining(" "));
    }

    @Override
    public ItemStack getIcon() {
        Block ctrl = getControllerBlock();
        return ctrl != null ? new ItemStack(ctrl) : ItemStack.EMPTY;
    }

    @Nullable
    public Block getControllerBlock() {
        if (controllerResolved) return cachedControllerBlock;
        if (!(multiblock instanceof TemplateMultiblock tmb)) {
            controllerResolved = true;
            return null;
        }

        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        controllerResolved = true;
        try {
            List<StructureTemplate.StructureBlockInfo> blocks = tmb.getStructure(level);
            if (blocks == null || blocks.isEmpty()) return null;
            BlockPos masterOffset = tmb.getMasterFromOriginOffset();
            for (StructureTemplate.StructureBlockInfo info : blocks) {
                if (info.pos().equals(masterOffset) && info.state() != null) {
                    cachedControllerBlock = info.state().getBlock();
                    break;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[Phantasia/IE] Could not resolve controller block for {}: {}", getId(), e.getMessage());
        }
        return cachedControllerBlock;
    }

    @Override
    public void onShapeLoaded(PhantasiaTrackedDummyWorld level, BlockPos origin,
                              Map<BlockPos, PhantasiaBlockInfo> blockMap,
                              Map<BlockPos, BlockPos> localToWorld) {
        if (!(multiblock instanceof TemplateMultiblock tmb)) return;
        try {
            masterLocalPos = tmb.getMasterFromOriginOffset();
            lastMasterWorldPos = localToWorld.getOrDefault(masterLocalPos, origin);
        } catch (Exception e) {
            lastMasterWorldPos = origin;
            masterLocalPos = BlockPos.ZERO;
        }
        worldToLocal.clear();
        for (Map.Entry<BlockPos, BlockPos> e : localToWorld.entrySet()) {
            worldToLocal.put(e.getValue(), e.getKey());
        }
        savedStates.clear();
    }

    @Override
    public void applyWorkingState(PhantasiaTrackedDummyWorld level,
                                  Set<BlockPos> positions,
                                  Map<BlockPos, PhantasiaBlockInfo> blockMap,
                                  boolean working) {
        if (!(multiblock instanceof IETemplateMultiblock ietmb)) return;

        Block block = ietmb.getBlock();
        if (!(block instanceof MultiblockPartBlock)) {
            LOGGER.warn("[Phantasia/IE] applyWorkingState: expected MultiblockPartBlock for {}, got {}",
                    getId(), block.getClass().getSimpleName());
            return;
        }

        if (working) {

            BlockState formedBase = block.defaultBlockState();
            for (var prop : formedBase.getProperties()) {
                if (prop instanceof net.minecraft.world.level.block.state.properties.BooleanProperty bp &&
                        bp.getName().equals("active")) {
                    formedBase = formedBase.setValue(bp, true);
                    break;
                }
            }
            final BlockState formedState = formedBase;
            final BlockPos masterRef = masterLocalPos != null ? masterLocalPos : BlockPos.ZERO;

            boolean firstApply = savedStates.isEmpty();

            for (BlockPos wp : positions) {

                BlockPos localPos = worldToLocal.get(wp);
                if (localPos == null) continue;

                if (firstApply) {
                    BlockState orig = level.getBlockState(wp);
                    if (!orig.isAir()) savedStates.put(wp, orig);
                }

                boolean isMaster = wp.equals(lastMasterWorldPos);
                BlockState toPlace = isMaster ? formedState.setValue(IEProperties.MULTIBLOCKSLAVE, false) : formedState;
                level.setBlock(wp, toPlace, 0);

                BlockPos submodelOffset = localPos.subtract(masterRef);
                level.setBlockEntity(new ModelOffsetBE(wp, toPlace, submodelOffset));
            }
        } else {
            for (Map.Entry<BlockPos, BlockState> e : savedStates.entrySet()) {
                level.setBlock(e.getKey(), e.getValue(), 0);
            }
            savedStates.clear();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IEMultiblockDefinition other)) return false;
        return multiblock.getUniqueName().equals(other.multiblock.getUniqueName());
    }

    @Override
    public int hashCode() {
        return multiblock.getUniqueName().hashCode();
    }

    @Override
    public String toString() {
        return "IEMultiblockDefinition[" + getId() + "]";
    }
}
