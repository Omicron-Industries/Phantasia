package net.phoenixvine.phantasia.common.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.common.data.pattern.PhantasiaLoadedPattern;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;
import net.phoenixvine.phantasia.common.data.variant.PhantasiaVariantGroup;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

public interface IPhantasiaMultiblockDefinition {

    ResourceLocation getId();

    List<IPhantasiaMultiblockShape> getMatchingShapes();

    List<IPhantasiaMultiblockShape> getAllShapes();

    String getDisplayName();

    ItemStack getIcon();

    default void onShapeLoaded(PhantasiaTrackedDummyWorld level, BlockPos origin,
                               Map<BlockPos, PhantasiaBlockInfo> blockMap,
                               Map<BlockPos, BlockPos> localToWorld,
                               @javax.annotation.Nullable net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData script) {
        onShapeLoaded(level, origin, blockMap, localToWorld);
    }

    default void onShapeLoaded(PhantasiaTrackedDummyWorld level, BlockPos origin,
                               Map<BlockPos, PhantasiaBlockInfo> blockMap,
                               Map<BlockPos, BlockPos> localToWorld) {}

    default void setMachineWorking(PhantasiaTrackedDummyWorld level, boolean working) {}

    default void applyWorkingState(PhantasiaTrackedDummyWorld level,
                                   Set<BlockPos> positions,
                                   Map<BlockPos, PhantasiaBlockInfo> blockMap,
                                   boolean working) {}

    default void onSceneTick(PhantasiaTrackedDummyWorld level,
                             Map<BlockPos, BlockPos> localToWorld,
                             int sceneTick) {}

    default boolean shouldHoldStep(PhantasiaTrackedDummyWorld level,
                                   Map<BlockPos, BlockPos> localToWorld,
                                   String holdId,
                                   int sceneTick) {
        return false;
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    default void renderWorkingOverlay(net.minecraft.client.gui.GuiGraphics g,
                                      int vpX, int vpY, int vpW, int vpH, float partial) {}

    @Nullable
    default PhantasiaScriptData getDefaultScriptData() {
        return null;
    }

    default int getShapeIndexForLayerCount(int layerCount) {
        int idx = layerCount - 1;
        int max = Math.max(0, getMatchingShapes().size() - 1);
        return Math.max(0, Math.min(idx, max));
    }

    default List<PhantasiaVariantGroup> detectProviderVariants(PhantasiaScriptData data,
                                                               PhantasiaLoadedPattern pattern,
                                                               String machinePrefix,
                                                               Set<String> explicitIds) {
        return Collections.emptyList();
    }

    default boolean shouldAutoDetectVariants() {
        return true;
    }
}
