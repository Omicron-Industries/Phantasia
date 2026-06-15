package net.phoenixvine.phantasia.common.data.script;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;

import net.phoenixvine.phantasia.client.screens.PhantasiaSceneScreen;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PhantasiaScripts {

    private static final Map<MultiblockMachineDefinition, PhantasiaScript> REGISTRY = new HashMap<>();

    public static void registerJson(MultiblockMachineDefinition def, PhantasiaScript script) {
        REGISTRY.put(def, script);
    }

    public static void clearJson(MultiblockMachineDefinition def) {
        REGISTRY.remove(def);
    }

    public static void clearAllJson() {
        REGISTRY.clear();
    }

    public static PhantasiaScript get(MultiblockMachineDefinition def) {
        return REGISTRY.getOrDefault(def, PhantasiaScript.showAll());
    }

    public static boolean has(MultiblockMachineDefinition def) {
        return REGISTRY.containsKey(def);
    }

    public static Collection<Map.Entry<MultiblockMachineDefinition, PhantasiaScript>> allEntries() {
        return Collections.unmodifiableCollection(REGISTRY.entrySet());
    }

    public static void invalidateWorldCache() {
        PhantasiaSceneScreen.invalidateSharedLevel();
    }
}
