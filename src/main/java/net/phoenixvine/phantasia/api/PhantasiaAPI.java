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

@OnlyIn(Dist.CLIENT)
public final class PhantasiaAPI {

    private static final Logger LOGGER = LogManager.getLogger("PhantasiaAPI");

    private PhantasiaAPI() {}

    public static boolean isAvailable(String machineId) {
        return PhantasiaMultiblockRegistry.resolve(machineId).isPresent();
    }

    public static boolean hasScript(String machineId) {
        return PhantasiaMultiblockRegistry.resolve(machineId)
                .map(PhantasiaScripts::has).orElse(false);
    }

    public static boolean hasScene(String sceneId) {
        return PhantasiaScenes.has(sceneId);
    }

    public static boolean hasGuide(String guideId) {
        return PhantasiaGuideRegistry.get(guideId) != null;
    }

    public static List<String> getAllMachineIds() {
        return PhantasiaMultiblockRegistry.getAllDefinitions().stream()
                .map(d -> d.getId().toString())
                .collect(Collectors.toList());
    }

    public static List<String> getAllSceneIds() {
        return PhantasiaScenes.all().stream()
                .map(s -> s.id)
                .collect(Collectors.toList());
    }

    public static List<String> getAllGuideIds() {
        return PhantasiaGuideRegistry.all().stream()
                .map(g -> g.id)
                .collect(Collectors.toList());
    }

    public static List<IPhantasiaMultiblockDefinition> getAllDefinitions() {
        return PhantasiaMultiblockRegistry.getAllDefinitions();
    }

    public static void openForMachine(String machineId, Screen parent) {
        Optional<IPhantasiaMultiblockDefinition> def = PhantasiaMultiblockRegistry.resolve(machineId);
        if (def.isEmpty()) {
            LOGGER.warn("[PhantasiaAPI] openForMachine: unknown machine id '{}' — screen not opened.", machineId);
            return;
        }
        Minecraft.getInstance().setScreen(new PhantasiaSceneScreen(def.get(), parent));
    }

    public static void openForDefinition(IPhantasiaMultiblockDefinition definition, Screen parent) {
        Minecraft.getInstance().setScreen(new PhantasiaSceneScreen(definition, parent));
    }

    public static void openScene(String sceneId, Screen parent) {
        PhantasiaSceneData scene = PhantasiaScenes.get(sceneId);
        if (scene == null) {
            LOGGER.warn("[PhantasiaAPI] openScene: unknown scene id '{}' — screen not opened.", sceneId);
            return;
        }
        Minecraft.getInstance().setScreen(new PhantasiaSceneViewerScreen(parent, scene));
    }

    public static void openGuide(String guideId, Screen parent) {
        PhantasiaGuideData guide = PhantasiaGuideRegistry.get(guideId);
        if (guide == null) {
            LOGGER.warn("[PhantasiaAPI] openGuide: unknown guide id '{}' — screen not opened.", guideId);
            return;
        }
        Minecraft.getInstance().setScreen(new PhantasiaGuideScreen(parent, guide));
    }

    public static PhantasiaMachinePreview createPreview(String machineId) {
        Optional<IPhantasiaMultiblockDefinition> def = PhantasiaMultiblockRegistry.resolve(machineId);
        if (def.isEmpty()) {
            LOGGER.warn("[PhantasiaAPI] createPreview: unknown machine id '{}' — returning null.", machineId);
            return null;
        }
        return new PhantasiaMachinePreview(def.get());
    }

    public static PhantasiaMachinePreview createPreview(IPhantasiaMultiblockDefinition definition) {
        return new PhantasiaMachinePreview(definition);
    }

    public static PhantasiaScenePreview createScenePreview(String sceneId) {
        PhantasiaSceneData scene = PhantasiaScenes.get(sceneId);
        if (scene == null) {
            LOGGER.warn("[PhantasiaAPI] createScenePreview: unknown scene id '{}' — returning null.", sceneId);
            return null;
        }
        return new PhantasiaScenePreview(scene);
    }
}
