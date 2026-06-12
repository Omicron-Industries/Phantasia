package net.phoenixvine.phantasia.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.phoenixvine.phantasia.client.screens.*;
import net.phoenixvine.phantasia.client.screens.editors.PhantasiaGuideEditorScreen;
import net.phoenixvine.phantasia.client.screens.editors.PhantasiaScriptEditorScreen;
import net.phoenixvine.phantasia.client.screens.subscreen.PhantasiaBlockFilterScreen;
import net.phoenixvine.phantasia.client.screens.subscreen.PhantasiaBlockInspectScreen;
import net.phoenixvine.phantasia.client.screens.subscreen.PhantasiaFootprintScreen;
import net.phoenixvine.phantasia.client.screens.subscreen.PhantasiaTextInputScreen;

public class PhantasiaRenderBypass {

    public static boolean isInPhantasiaInstance() {
        Screen currentScreen = Minecraft.getInstance().screen;
        if (currentScreen == null) return false;

        return currentScreen instanceof PhantasiaSceneScreen ||
                currentScreen instanceof PhantasiaScriptEditorScreen ||
                currentScreen instanceof PhantasiaSceneSelectionScreen ||
                currentScreen instanceof PhantasiaFootprintScreen ||
                currentScreen instanceof PhantasiaBlockFilterScreen ||
                currentScreen instanceof PhantasiaBlockInspectScreen ||
                currentScreen instanceof PhantasiaSceneCreateScreen ||
                currentScreen instanceof PhantasiaTextInputScreen ||
                currentScreen instanceof PhantasiaGuideScreen ||       // Added
                currentScreen instanceof PhantasiaGuideEditorScreen;   // Added
    }
}
