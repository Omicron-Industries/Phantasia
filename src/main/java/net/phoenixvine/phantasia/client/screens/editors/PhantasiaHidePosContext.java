package net.phoenixvine.phantasia.client.screens.editors;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.phoenixvine.phantasia.client.camera.PhantasiaCamera;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.client.render.PhantasiaWorldRenderer;

import java.util.List;

import javax.annotation.Nullable;

public interface PhantasiaHidePosContext {

    String getHidePosLabel();

    Screen returnScreen();

    PhantasiaWorldRenderer getRenderer();

    PhantasiaCamera getParentCamera();

    PhantasiaTrackedDummyWorld getEditorLevel();

    @Nullable
    BlockPos localToWorld(int[] localXYZ);

    @Nullable
    int[] worldToLocal(BlockPos world);

    List<int[]> getHidePositions();

    void checkpoint();

    void markDirty();

    void rebuildVisibility();

    default void showAllForPickingMode() {
        rebuildVisibility();
    }

    default void previewVisibility() {
        rebuildVisibility();
    }
}
