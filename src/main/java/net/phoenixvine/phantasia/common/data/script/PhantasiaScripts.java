package net.phoenixvine.phantasia.common.data.script;

import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.phantasia.client.screens.PhantasiaSceneScreen;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PhantasiaScripts {

    private static final Map<ResourceLocation, PhantasiaScript> REGISTRY = new HashMap<>();

    public static void register(IPhantasiaMultiblockDefinition def, PhantasiaScript script) {
        REGISTRY.put(def.getId(), script);
    }

    public static void clearScript(IPhantasiaMultiblockDefinition def) {
        REGISTRY.remove(def.getId());
    }

    public static void clearAllJson() {
        REGISTRY.clear();
    }

    public static PhantasiaScript get(IPhantasiaMultiblockDefinition def) {
        return REGISTRY.getOrDefault(def.getId(), PhantasiaScript.showAll());
    }

    public static PhantasiaScript getById(ResourceLocation id) {
        return REGISTRY.getOrDefault(id, PhantasiaScript.showAll());
    }

    public static boolean has(IPhantasiaMultiblockDefinition def) {
        return REGISTRY.containsKey(def.getId());
    }

    public static Collection<Map.Entry<ResourceLocation, PhantasiaScript>> allEntries() {
        return Collections.unmodifiableCollection(REGISTRY.entrySet());
    }

    public static void invalidateWorldCache() {
        PhantasiaSceneScreen.invalidateSharedLevel();
    }
}
