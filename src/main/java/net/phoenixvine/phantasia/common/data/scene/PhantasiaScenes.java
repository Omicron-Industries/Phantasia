package net.phoenixvine.phantasia.common.data.scene;

import net.phoenixvine.phantasia.common.data.script.PhantasiaScripts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PhantasiaScenes — central runtime registry of {@link PhantasiaSceneData} objects.
 *
 * Unlike {@link PhantasiaScripts} (which keys by MultiblockMachineDefinition),
 * scenes are keyed by their string ID (e.g. "phoenixvine:ore_processing_line").
 * Scene data is not compiled into a predicate form — the editor and scene screen
 * work directly against the data, building the merged world on demand.
 */
public class PhantasiaScenes {

    /** Ordered map so the selection screen lists scenes in load order. */
    private static final Map<String, PhantasiaSceneData> REGISTRY = new LinkedHashMap<>();

    // ── Called by PhantasiaSceneLoader ────────────────────────────────────────

    public static void register(PhantasiaSceneData scene) {
        if (scene.id != null && !scene.id.isBlank())
            REGISTRY.put(scene.id, scene);
    }

    public static void remove(String id) {
        REGISTRY.remove(id);
    }

    public static void clearAll() {
        REGISTRY.clear();
    }

    // ── Lookup ────────────────────────────────────────────────────────────────

    public static PhantasiaSceneData get(String id) {
        return REGISTRY.get(id);
    }

    public static boolean has(String id) {
        return REGISTRY.containsKey(id);
    }

    /** Returns an unmodifiable snapshot of all registered scenes, in load order. */
    public static List<PhantasiaSceneData> all() {
        return Collections.unmodifiableList(new ArrayList<>(REGISTRY.values()));
    }
}
