package net.phoenixvine.phantasia.api;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.client.screens.PhantasiaGuideScreen;
import net.phoenixvine.phantasia.client.screens.PhantasiaSceneScreen;
import net.phoenixvine.phantasia.client.screens.PhantasiaSceneViewerScreen;
import net.phoenixvine.phantasia.common.data.guides.PhantasiaGuideData;
import net.phoenixvine.phantasia.common.data.guides.PhantasiaGuideRegistry;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaScenes;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScripts;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.PhantasiaMultiblockRegistry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Public entry point for external mods integrating with Phantasia.
 *
 * <p>
 * All client-side. Guard calls with {@code @OnlyIn(Dist.CLIENT)} or a
 * dist-checked proxy in your own mod.
 *
 * <p>
 * Typical usage from a quest mod:
 * 
 * <pre>{@code
 * // Create once, keep the instance:
 * PhantasiaMachinePreview preview = PhantasiaAPI.createPreview("gtceu:electric_blast_furnace");
 *
 * // In your screen's render():
 * preview.render(guiGraphics, x, y, 120, 90, partialTick);
 *
 * // In your screen's mouseClicked():
 * if (preview.mouseClicked(mx, my, x, y, 120, 90, this)) return true;
 *
 * // In your screen's onClose() / cleanup:
 * preview.close();
 * }</pre>
 */
@OnlyIn(Dist.CLIENT)
public final class PhantasiaAPI {

    private static final Logger LOGGER = LogManager.getLogger("PhantasiaAPI");

    private PhantasiaAPI() {}

    // ── Presence checks ───────────────────────────────────────────────────────

    /**
     * Returns {@code true} if a multiblock with this id is registered and Phantasia
     * can resolve it. Safe to call at any time after mod init.
     */
    public static boolean isAvailable(String machineId) {
        return PhantasiaMultiblockRegistry.resolve(machineId).isPresent();
    }

    /** Returns {@code true} if a script exists for the given machine id. */
    public static boolean hasScript(String machineId) {
        return PhantasiaMultiblockRegistry.resolve(machineId)
                .map(PhantasiaScripts::has).orElse(false);
    }

    /** Returns {@code true} if a scene with this id has been registered or loaded from a data pack. */
    public static boolean hasScene(String sceneId) {
        return PhantasiaScenes.has(sceneId);
    }

    /** Returns {@code true} if a guide with this id has been registered. */
    public static boolean hasGuide(String guideId) {
        return PhantasiaGuideRegistry.get(guideId) != null;
    }

    // ── Content enumeration ───────────────────────────────────────────────────

    /**
     * Returns the namespaced ids of every registered multiblock machine, across all providers.
     * Useful for populating a picker in a quest editor UI.
     */
    public static List<String> getAllMachineIds() {
        return PhantasiaMultiblockRegistry.getAllDefinitions().stream()
                .map(d -> d.getId().toString())
                .collect(Collectors.toList());
    }

    /**
     * Returns the ids of every registered scene.
     */
    public static List<String> getAllSceneIds() {
        return PhantasiaScenes.all().stream()
                .map(s -> s.id)
                .collect(Collectors.toList());
    }

    /**
     * Returns the ids of every registered guide.
     */
    public static List<String> getAllGuideIds() {
        return PhantasiaGuideRegistry.all().stream()
                .map(g -> g.id)
                .collect(Collectors.toList());
    }

    /**
     * Returns all registered multiblock definitions.
     */
    public static List<IPhantasiaMultiblockDefinition> getAllDefinitions() {
        return PhantasiaMultiblockRegistry.getAllDefinitions();
    }

    // ── Direct screen opening ─────────────────────────────────────────────────

    /**
     * Opens the Phantasia single-machine viewer for {@code machineId}.
     * Does nothing if the machine id is unknown.
     *
     * @param machineId namespaced machine id, e.g. {@code "gtceu:electric_blast_furnace"}
     * @param parent    screen to return to when the player closes Phantasia
     */
    public static void openForMachine(String machineId, Screen parent) {
        Optional<IPhantasiaMultiblockDefinition> def = PhantasiaMultiblockRegistry.resolve(machineId);
        if (def.isEmpty()) {
            LOGGER.warn("[PhantasiaAPI] openForMachine: unknown machine id '{}' — screen not opened.", machineId);
            return;
        }
        Minecraft.getInstance().setScreen(new PhantasiaSceneScreen(def.get(), parent));
    }

    /**
     * Opens the Phantasia single-machine viewer directly from a resolved definition.
     */
    public static void openForDefinition(IPhantasiaMultiblockDefinition definition, Screen parent) {
        Minecraft.getInstance().setScreen(new PhantasiaSceneScreen(definition, parent));
    }

    /**
     * Opens the Phantasia multi-machine scene viewer for {@code sceneId}.
     * Does nothing if the scene is not found.
     *
     * @param sceneId scene identifier as declared in the scene JSON, e.g. {@code "phoenixvine:ore_line"}
     * @param parent  screen to return to when the player closes Phantasia
     */
    public static void openScene(String sceneId, Screen parent) {
        PhantasiaSceneData scene = PhantasiaScenes.get(sceneId);
        if (scene == null) {
            LOGGER.warn("[PhantasiaAPI] openScene: unknown scene id '{}' — screen not opened.", sceneId);
            return;
        }
        Minecraft.getInstance().setScreen(new PhantasiaSceneViewerScreen(parent, scene));
    }

    /**
     * Opens the Phantasia guide reader for {@code guideId}.
     * Does nothing if the guide is not found.
     *
     * @param guideId guide identifier, e.g. {@code "phantasia:getting_started"}
     * @param parent  screen to return to when the player closes the guide
     */
    public static void openGuide(String guideId, Screen parent) {
        PhantasiaGuideData guide = PhantasiaGuideRegistry.get(guideId);
        if (guide == null) {
            LOGGER.warn("[PhantasiaAPI] openGuide: unknown guide id '{}' — screen not opened.", guideId);
            return;
        }
        Minecraft.getInstance().setScreen(new PhantasiaGuideScreen(parent, guide));
    }

    // ── Embeddable preview widget ─────────────────────────────────────────────

    /**
     * Creates a self-contained 3-D preview widget for the given machine.
     *
     * <p>
     * The widget begins loading the pattern asynchronously; call
     * {@link PhantasiaMachinePreview#isReady()} before rendering if you want to
     * show a loading placeholder yourself. Rendering before ready is safe — the
     * widget draws a dimmed placeholder instead.
     *
     * <p>
     * <b>Important:</b> call {@link PhantasiaMachinePreview#close()} when your
     * screen is closed to release GL resources.
     *
     * @param machineId namespaced machine id, e.g. {@code "gtceu:electric_blast_furnace"}
     * @return a new preview widget, or {@code null} if the machine id is unknown
     */
    public static PhantasiaMachinePreview createPreview(String machineId) {
        Optional<IPhantasiaMultiblockDefinition> def = PhantasiaMultiblockRegistry.resolve(machineId);
        if (def.isEmpty()) {
            LOGGER.warn("[PhantasiaAPI] createPreview: unknown machine id '{}' — returning null.", machineId);
            return null;
        }
        return new PhantasiaMachinePreview(def.get());
    }

    /**
     * Creates a preview widget from an already-resolved definition.
     */
    public static PhantasiaMachinePreview createPreview(IPhantasiaMultiblockDefinition definition) {
        return new PhantasiaMachinePreview(definition);
    }

    /**
     * Creates a self-contained 3-D preview widget for an entire multi-machine scene - the scene
     * analog of {@link #createPreview(String)}. Renders a single static composite of every
     * placement's default shape (no steps/camera keyframes/item overlays - see
     * {@link PhantasiaScenePreview}'s class doc for what's intentionally out of scope).
     *
     * @param sceneId scene identifier as declared in the scene JSON, e.g. {@code "phoenixvine:ore_line"}
     * @return a new preview widget, or {@code null} if the scene id is unknown
     */
    public static PhantasiaScenePreview createScenePreview(String sceneId) {
        PhantasiaSceneData scene = PhantasiaScenes.get(sceneId);
        if (scene == null) {
            LOGGER.warn("[PhantasiaAPI] createScenePreview: unknown scene id '{}' — returning null.", sceneId);
            return null;
        }
        return new PhantasiaScenePreview(scene);
    }
}
