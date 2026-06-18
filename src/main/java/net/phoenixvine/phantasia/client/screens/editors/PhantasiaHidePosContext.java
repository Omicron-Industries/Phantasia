package net.phoenixvine.phantasia.client.screens.editors;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.phoenixvine.phantasia.client.camera.PhantasiaCamera;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.client.render.PhantasiaWorldRenderer;

import java.util.List;

import javax.annotation.Nullable;

/**
 * Abstraction used by {@link PhantasiaHidePosEditorScreen} so the same hide-positions
 * editor can be opened from both {@link PhantasiaScriptEditorScreen} and
 * {@link PhantasiaScenePlacementStepEditorScreen}.
 */
public interface PhantasiaHidePosContext {

    /** Short label shown in the editor's title bar, e.g. "Step 3" or "Placement #0, Step 2". */
    String getHidePosLabel();

    /** The screen to return to when the user clicks Done. */
    Screen returnScreen();

    /** The world renderer for the 3D view (shared with the parent, not cloned). */
    PhantasiaWorldRenderer getRenderer();

    /** Camera to clone as the initial view angle (parent camera is NOT mutated). */
    PhantasiaCamera getParentCamera();

    /** Level used for block-name lookups. */
    PhantasiaTrackedDummyWorld getEditorLevel();

    /** Convert local XYZ (relative to the structure) to a world {@link BlockPos}. Returns null if unavailable. */
    @Nullable
    BlockPos localToWorld(int[] localXYZ);

    /** Convert world {@link BlockPos} to local XYZ. Returns null if this pos is not part of the structure. */
    @Nullable
    int[] worldToLocal(BlockPos world);

    /** The mutable hide-positions list that the editor reads and writes. */
    List<int[]> getHidePositions();

    /** Push the current data state onto the undo stack. */
    void checkpoint();

    /** Mark the owning data as modified. */
    void markDirty();

    /** Ask the parent to recompute and apply visible-block masks after positions change. */
    void rebuildVisibility();

    /**
     * Called when the pos editor opens so all blocks are visible for picking.
     * Script editor: no-op (SELECT mode already shows everything).
     * Scene placement editor: overrides to show all blocks so the user can click
     * any block to add it to the pos list, regardless of the current pos filter.
     */
    default void showAllForPickingMode() {
        rebuildVisibility();
    }

    /**
     * Preview the actual filtered result without exiting pick mode.
     * Default: same as rebuildVisibility (script editor already shows filtered result).
     * Scene placement editor: overrides to apply pos filter without inPickingMode.
     */
    default void previewVisibility() {
        rebuildVisibility();
    }
}
