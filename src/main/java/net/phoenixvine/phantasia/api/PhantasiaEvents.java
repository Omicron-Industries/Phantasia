package net.phoenixvine.phantasia.api;

import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.Event;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;

import lombok.Getter;

import javax.annotation.Nullable;

@OnlyIn(Dist.CLIENT)
public final class PhantasiaEvents {

    private PhantasiaEvents() {}

    public static abstract class ViewerEvent extends Event {

        private final IPhantasiaMultiblockDefinition definition;

        @Getter
        private final Screen screen;

        protected ViewerEvent(IPhantasiaMultiblockDefinition definition, Screen screen) {
            this.definition = definition;
            this.screen = screen;
        }

        @Nullable
        public IPhantasiaMultiblockDefinition getDefinition() {
            return definition;
        }

        @Nullable
        public String getMachineId() {
            return definition != null ? definition.getId().toString() : null;
        }
    }

    public static final class ViewerOpen extends ViewerEvent {

        public ViewerOpen(IPhantasiaMultiblockDefinition definition, Screen screen) {
            super(definition, screen);
        }
    }

    public static final class ViewerClose extends ViewerEvent {

        private final float secondsViewed;

        public ViewerClose(IPhantasiaMultiblockDefinition definition, Screen screen, float secondsViewed) {
            super(definition, screen);
            this.secondsViewed = secondsViewed;
        }

        public float getSecondsViewed() {
            return secondsViewed;
        }
    }

    public static final class SceneViewerOpen extends ViewerEvent {

        private final String sceneId;

        public SceneViewerOpen(String sceneId, Screen screen) {
            super(null, screen);
            this.sceneId = sceneId;
        }

        public String getSceneId() {
            return sceneId;
        }
    }

    public static final class SceneViewerClose extends ViewerEvent {

        private final String sceneId;
        private final float secondsViewed;

        public SceneViewerClose(String sceneId, Screen screen, float secondsViewed) {
            super(null, screen);
            this.sceneId = sceneId;
            this.secondsViewed = secondsViewed;
        }

        public String getSceneId() {
            return sceneId;
        }

        public float getSecondsViewed() {
            return secondsViewed;
        }
    }
}
