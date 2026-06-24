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

/**
 * Mod-agnostic representation of a multiblock machine definition.
 * GTCEu wraps {@code MultiblockMachineDefinition}; other mods provide their own.
 */
public interface IPhantasiaMultiblockDefinition {

    ResourceLocation getId();

    /** Shapes used for rendering (shape index 0 is the default). */
    List<IPhantasiaMultiblockShape> getMatchingShapes();

    /** All available shapes, used for variant auto-detection. */
    List<IPhantasiaMultiblockShape> getAllShapes();

    String getDisplayName();

    ItemStack getIcon();

    /**
     * Called on the render thread after all blocks have been placed in the dummy world.
     * Implementations should fire their structure-forming logic here (e.g. GTCEu's
     * {@code onStructureFormed}). Default: delegates to the 4-arg overload.
     *
     * @param script the currently loaded script; may be null. Definitions that
     *               support per-recipe scenes should read
     *               {@link net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData#getRecipeId()}
     *               to load the correct item placements.
     */
    default void onShapeLoaded(PhantasiaTrackedDummyWorld level, BlockPos origin,
                               Map<BlockPos, PhantasiaBlockInfo> blockMap,
                               Map<BlockPos, BlockPos> localToWorld,
                               @javax.annotation.Nullable net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData script) {
        onShapeLoaded(level, origin, blockMap, localToWorld);
    }

    default void onShapeLoaded(PhantasiaTrackedDummyWorld level, BlockPos origin,
                               Map<BlockPos, PhantasiaBlockInfo> blockMap,
                               Map<BlockPos, BlockPos> localToWorld) {}

    /**
     * Set the working/idle animation state of this machine in the dummy world.
     * Default: no-op.
     */
    default void setMachineWorking(PhantasiaTrackedDummyWorld level, boolean working) {}

    /**
     * Apply working/idle state to all machines and animated blocks that belong to
     * this placement. Called once per placement entry by {@code PhantasiaSceneActiveState}.
     *
     * @param level     the dummy world
     * @param positions world positions that belong to this placement
     * @param blockMap  full merged block map (implementations should filter to {@code positions})
     * @param working   the effective working state for this placement
     */
    default void applyWorkingState(PhantasiaTrackedDummyWorld level,
                                   Set<BlockPos> positions,
                                   Map<BlockPos, PhantasiaBlockInfo> blockMap,
                                   boolean working) {}

    /**
     * Called every client tick while the scene screen is open and not scrubbing.
     * Use for driving continuous visual effects: draining/filling tanks, keeping
     * crafting flags alive, emitting sounds, etc.
     *
     * @param level        the shared dummy world
     * @param localToWorld local-coordinate → world-coordinate map for this pattern
     * @param sceneTick    monotonically-increasing tick counter (resets when scene reloads)
     */
    default void onSceneTick(PhantasiaTrackedDummyWorld level,
                             Map<BlockPos, BlockPos> localToWorld,
                             int sceneTick) {}

    /**
     * Called each tick when the active script step has a {@code "hold"} id.
     * Return {@code true} to keep the playback tick pinned to that step;
     * return {@code false} to release the hold and let playback resume.
     *
     * <p>
     * The first call for a given hold is the trigger — use it to start
     * animations or record the start time.
     *
     * @param holdId    the value of the step's {@code "hold"} field
     * @param sceneTick monotonic scene tick (same value passed to {@link #onSceneTick})
     */
    default boolean shouldHoldStep(PhantasiaTrackedDummyWorld level,
                                   Map<BlockPos, BlockPos> localToWorld,
                                   String holdId,
                                   int sceneTick) {
        return false;
    }

    /**
     * Called on the render thread when the working state is active.
     * Use to render a mob or other overlay on top of the viewport.
     * {@code vpX/Y/W/H} are the viewport bounds in GUI-scaled coordinates.
     * Default: no-op.
     */
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    default void renderWorkingOverlay(net.minecraft.client.gui.GuiGraphics g,
                                      int vpX, int vpY, int vpW, int vpH, float partial) {}

    /**
     * Override to supply a richer default script written to the game dir on first launch.
     * Return null to use the bare single-step default.
     */
    @Nullable
    default PhantasiaScriptData getDefaultScriptData() {
        return null;
    }

    /**
     * Translates a layer count (1-based, as written in the script) to a shape index.
     * Default: shape index = layerCount - 1, clamped to the valid range.
     * Override for definitions where the mapping is non-trivial.
     */
    default int getShapeIndexForLayerCount(int layerCount) {
        int idx = layerCount - 1;
        int max = Math.max(0, getMatchingShapes().size() - 1);
        return Math.max(0, Math.min(idx, max));
    }

    /**
     * Provider-specific variant group detection (e.g. PartAbility tier groups for GTCEu).
     * Called by {@link net.phoenixvine.phantasia.common.data.variant.PhantasiaVariantGroup#compile}
     * after manual groups are compiled. Default: no additional groups.
     */
    default List<PhantasiaVariantGroup> detectProviderVariants(PhantasiaScriptData data,
                                                               PhantasiaLoadedPattern pattern,
                                                               String machinePrefix,
                                                               Set<String> explicitIds) {
        return Collections.emptyList();
    }

    /**
     * Whether to run generic optional-block detection across all shapes.
     * Return false for definitions whose shape differences are structural (e.g. beacon tiers)
     * rather than optional substitutions.
     */
    default boolean shouldAutoDetectVariants() {
        return true;
    }
}
