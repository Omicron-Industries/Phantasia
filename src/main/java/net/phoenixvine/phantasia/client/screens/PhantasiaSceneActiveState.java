package net.phoenixvine.phantasia.client.screens;

import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.client.render.PhantasiaWorldRenderer;
import net.phoenixvine.phantasia.common.data.pattern.PhantasiaScenePattern;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData;
import net.phoenixvine.phantasia.common.multiblock.PhantasiaMultiblockRegistry;

import javax.annotation.Nullable;

/**
 * Single source of truth for applying working/idle state to a scene dummy world.
 * Used by PhantasiaSceneViewerScreen and PhantasiaSceneEditorScreen.
 *
 * All mod-specific logic (GTCEu RecipeLogic, ActiveBlock, etc.) lives behind
 * {@link net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition#applyWorkingState}.
 *
 * PhantasiaSceneScreen (single-machine) calls definition.applyWorkingState directly.
 */
public final class PhantasiaSceneActiveState {

    private PhantasiaSceneActiveState() {}

    /**
     * Apply working state per placement, delegating all mod-specific logic to each
     * placement's {@link net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition}.
     */
    public static void apply(PhantasiaTrackedDummyWorld level,
                             PhantasiaScenePattern pattern,
                             @Nullable PhantasiaSceneData.StepData step,
                             boolean globalWorking,
                             @Nullable PhantasiaWorldRenderer renderer) {
        if (level == null || pattern == null) return;

        for (PhantasiaScenePattern.PlacementEntry pe : pattern.placements) {
            PhantasiaSceneData.MachineOverride ov = step != null ? step.getOverride(pe.index) : null;
            boolean effective = ov != null ? ov.resolveWorking(globalWorking) : globalWorking;
            PhantasiaMultiblockRegistry.resolve(pe.machineId)
                    .ifPresent(def -> def.applyWorkingState(
                            level, pe.worldPositions, pattern.mergedBlockMap, effective));
        }

        if (renderer != null) renderer.requestBake();
    }
}
