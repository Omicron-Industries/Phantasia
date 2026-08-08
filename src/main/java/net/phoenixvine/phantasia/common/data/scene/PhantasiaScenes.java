package net.phoenixvine.phantasia.common.data.scene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PhantasiaScenes {

    private static final Map<String, PhantasiaSceneData> REGISTRY = new LinkedHashMap<>();

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

    public static PhantasiaSceneData get(String id) {
        return REGISTRY.get(id);
    }

    public static boolean has(String id) {
        return REGISTRY.containsKey(id);
    }

    public static List<PhantasiaSceneData> all() {
        return Collections.unmodifiableList(new ArrayList<>(REGISTRY.values()));
    }
}
