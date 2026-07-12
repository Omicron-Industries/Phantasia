package net.phoenixvine.phantasia.api;

import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.Event;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;

import lombok.Getter;

import javax.annotation.Nullable;

/**
 * Forge events fired by Phantasia on the client event bus
 * ({@link net.minecraftforge.common.MinecraftForge#EVENT_BUS}).
 *
 * <p>
 * Subscribe from your mod's client-side event handler:
 * 
 * <pre>{@code
 * 
 * @SubscribeEvent
 * public static void onPhantasiaOpen(PhantasiaEvents.ViewerOpen event) {
 *     if ("gtceu:electric_blast_furnace".equals(event.getMachineId())) {
 *         // player just opened the EBF guide — mark the quest step complete
 *     }
 * }
 * }</pre>
 */
@OnlyIn(Dist.CLIENT)
public final class PhantasiaEvents {

    private PhantasiaEvents() {}

    // ── Base ──────────────────────────────────────────────────────────────────

    /** Base class for all Phantasia viewer events. Not posted directly. */
    public static abstract class ViewerEvent extends Event {

        private final IPhantasiaMultiblockDefinition definition;
        /**
         * -- GETTER --
         * The Phantasia screen that was opened/closed.
         */
        @Getter
        private final Screen screen;

        protected ViewerEvent(IPhantasiaMultiblockDefinition definition, Screen screen) {
            this.definition = definition;
            this.screen = screen;
        }

        /**
         * The machine definition whose guide/script is being viewed.
         * May be {@code null} for non-machine screens (scene viewer, guide reader).
         */
        @Nullable
        public IPhantasiaMultiblockDefinition getDefinition() {
            return definition;
        }

        /**
         * Namespaced machine id, e.g. {@code "gtceu:electric_blast_furnace"}.
         * Returns {@code null} if this event was fired for a non-machine screen.
         */
        @Nullable
        public String getMachineId() {
            return definition != null ? definition.getId().toString() : null;
        }
    }

    // ── Single-machine viewer ─────────────────────────────────────────────────

    /**
     * Fired when the player opens the Phantasia single-machine viewer
     * ({@link net.phoenixvine.phantasia.client.screens.PhantasiaSceneScreen}).
     *
     * <p>
     * Use this to detect when a player starts viewing a machine guide.
     */
    public static final class ViewerOpen extends ViewerEvent {

        public ViewerOpen(IPhantasiaMultiblockDefinition definition, Screen screen) {
            super(definition, screen);
        }
    }

    /**
     * Fired when the player closes the Phantasia single-machine viewer.
     * {@link #getSecondsViewed()} tells you how long the screen was open.
     *
     * <p>
     * Use this to mark quest objectives complete after the player has
     * actually spent time reading (e.g. require ≥ 3 seconds).
     */
    public static final class ViewerClose extends ViewerEvent {

        private final float secondsViewed;

        public ViewerClose(IPhantasiaMultiblockDefinition definition, Screen screen, float secondsViewed) {
            super(definition, screen);
            this.secondsViewed = secondsViewed;
        }

        /**
         * Wall-clock seconds the viewer was open before being closed.
         * This includes time spent scrubbing the timeline, orbiting the camera, etc.
         */
        public float getSecondsViewed() {
            return secondsViewed;
        }
    }

    // ── Scene viewer ──────────────────────────────────────────────────────────

    /**
     * Fired when the player opens the Phantasia multi-machine scene viewer
     * ({@link net.phoenixvine.phantasia.client.screens.PhantasiaSceneViewerScreen}).
     * {@link #getMachineId()} returns {@code null}; use {@link #getSceneId()} instead.
     */
    public static final class SceneViewerOpen extends ViewerEvent {

        private final String sceneId;

        public SceneViewerOpen(String sceneId, Screen screen) {
            super(null, screen);
            this.sceneId = sceneId;
        }

        /** The scene identifier, e.g. {@code "phoenixvine:ore_processing_line"}. */
        public String getSceneId() {
            return sceneId;
        }
    }

    /**
     * Fired when the player closes the multi-machine scene viewer.
     */
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
