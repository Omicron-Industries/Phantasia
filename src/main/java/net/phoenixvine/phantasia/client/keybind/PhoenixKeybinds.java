package net.phoenixvine.phantasia.client.keybind;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public class PhoenixKeybinds {

    public static final KeyMapping OPEN_PHANTASIA_MENU = new KeyMapping(
            "key.phantasia.phantasia_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            "key.categories.phantasia");

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_PHANTASIA_MENU);
    }

    /** Returns the current display name of the Phantasia keybind wrapped in brackets, e.g. {@code [P]}. */
    public static String keyDisplay() {
        return "[" + OPEN_PHANTASIA_MENU.getTranslatedKeyMessage().getString() + "]";
    }
}
