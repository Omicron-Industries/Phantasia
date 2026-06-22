package net.phoenixvine.phantasia.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.client.screens.PhantasiaGuideScreen;
import net.phoenixvine.phantasia.common.data.guides.PhantasiaGuideData;
import net.phoenixvine.phantasia.common.data.guides.PhantasiaGuideRegistry;
import net.phoenixvine.phantasia.utils.PhantasiaTheme;

import java.io.File;

@Mod.EventBusSubscriber(modid = Phantasia.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PhantasiaWelcomeOverlay {

    private PhantasiaWelcomeOverlay() {}

    private static final File SEEN_FILE = new File("phantasia/seen_welcome.txt");
    private static final int SHOW_TICKS = 280;
    private static final int FADE_TICKS = 40;

    private static boolean active = false;
    private static int timer = 0;
    private static boolean wasClicking = false;

    // Toast bounds updated each render frame for click-hit detection.
    private static int toastX, toastY;
    private static final int TOAST_W = 248;
    private static final int TOAST_H = 46;

    /** Call on world join. Shows the toast exactly once, ever. */
    public static void checkFirstRun() {
        if (!SEEN_FILE.exists()) {
            active = true;
            timer = SHOW_TICKS;
            try {
                SEEN_FILE.getParentFile().mkdirs();
                SEEN_FILE.createNewFile();
            } catch (Exception e) {
                Phantasia.LOGGER.warn("[Phantasia] Could not write seen_welcome.txt: {}", e.getMessage());
            }
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) return;

        if (timer > 0) {
            timer--;
        } else {
            active = false;
            wasClicking = false;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            wasClicking = false;
            return;
        }

        // Click detection via GLFW — no Forge input event needed.
        long window = mc.getWindow().getWindow();
        boolean clicking = org.lwjgl.glfw.GLFW.glfwGetMouseButton(
                window, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

        if (clicking && !wasClicking) {
            double[] rawX = new double[1], rawY = new double[1];
            org.lwjgl.glfw.GLFW.glfwGetCursorPos(window, rawX, rawY);
            double sx = rawX[0] * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
            double sy = rawY[0] * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();

            if (sx >= toastX && sx <= toastX + TOAST_W && sy >= toastY && sy <= toastY + TOAST_H) {
                active = false;
                PhantasiaGuideData guide = PhantasiaGuideRegistry.get("phantasia:getting_started");
                if (guide != null) {
                    mc.setScreen(new PhantasiaGuideScreen(null, guide));
                }
            }
        }
        wasClicking = clicking;
    }

    @SubscribeEvent
    public static void onRender(RenderGuiOverlayEvent.Pre event) {
        if (!active || timer <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        renderToast(event.getGuiGraphics(), mc);
    }

    private static void renderToast(GuiGraphics g, Minecraft mc) {
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        float alpha = timer < FADE_TICKS ? (float) timer / FADE_TICKS : 1.0f;

        int tx = (sw - TOAST_W) / 2;
        int ty = sh - 84;
        toastX = tx;
        toastY = ty;

        PhantasiaTheme theme = PhantasiaTheme.current();
        int bgColor = withAlpha(theme.bg(), (int) (0xDD * alpha));
        int bdrColor = withAlpha(theme.accent(), (int) (0xFF * alpha));
        int txtColor = withAlpha(theme.text(), (int) (0xFF * alpha));
        int dimColor = withAlpha(theme.dim(), (int) (0xAA * alpha));

        // Background panel
        g.fill(tx, ty, tx + TOAST_W, ty + TOAST_H, bgColor);
        // Top accent stripe
        g.fill(tx, ty, tx + TOAST_W, ty + 1, bdrColor);
        // Subtle bottom border
        g.fill(tx, ty + TOAST_H - 1, tx + TOAST_W, ty + TOAST_H,
                withAlpha(theme.accent(), (int) (0x44 * alpha)));

        var font = mc.font;
        int lx = tx + 8;
        g.drawString(font,
                net.minecraft.network.chat.Component.translatable("ui.phantasia.welcome.installed").getString(), lx,
                ty + 6, bdrColor, false);
        g.drawString(font,
                net.minecraft.network.chat.Component.translatable("ui.phantasia.welcome.instruction").getString(), lx,
                ty + 17, txtColor, false);
        g.drawString(font,
                net.minecraft.network.chat.Component.translatable("ui.phantasia.welcome.open_guide").getString(), lx,
                ty + 29, dimColor, false);
    }

    private static int withAlpha(int argb, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (argb & 0xFFFFFF);
    }
}
