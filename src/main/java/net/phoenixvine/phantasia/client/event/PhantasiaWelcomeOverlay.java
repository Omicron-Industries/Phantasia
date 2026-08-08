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
import net.phoenixvine.wiki.theme.PhoenixTheme;

import java.io.File;

@Mod.EventBusSubscriber(modid = Phantasia.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PhantasiaWelcomeOverlay {

    private PhantasiaWelcomeOverlay() {}

    private static final java.io.File SEEN_DIR = new java.io.File("phantasia/seen_worlds");

    private static final int FADE_TICKS = 40;

    private static boolean active = false;
    private static boolean playerHasMoved = false;
    private static int timer = 0;
    private static boolean wasClicking = false;

    private static int toastX, toastY;
    private static final int TOAST_W = 248;
    private static int TOAST_H = 46;

    public static void checkFirstRun(String worldId) {
        if (!net.phoenixvine.phantasia.configs.PhantasiaConfigs.INSTANCE.phantasiaUI.showWelcomeToast) return;
        String safe = worldId.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        java.io.File seenFile = new java.io.File(SEEN_DIR, safe + ".txt");
        if (!seenFile.exists()) {
            int duration = net.phoenixvine.phantasia.configs.PhantasiaConfigs.INSTANCE.phantasiaUI.welcomeToastDuration;
            if (duration <= 0) return;
            active = true;
            playerHasMoved = false;
            timer = duration;
            try {
                SEEN_DIR.mkdirs();
                seenFile.createNewFile();
            } catch (Exception e) {
                Phantasia.LOGGER.warn("[Phantasia] Could not write seen_worlds/{}.txt: {}", safe, e.getMessage());
            }
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            wasClicking = false;
            return;
        }

        long window = mc.getWindow().getWindow();

        if (!playerHasMoved) {
            var opt = mc.options;
            if (opt.keyUp.isDown() || opt.keyDown.isDown() || opt.keyLeft.isDown() || opt.keyRight.isDown() ||
                    opt.keyJump.isDown() || opt.keyShift.isDown()) {
                playerHasMoved = true;
            }
        }

        if (playerHasMoved) {
            if (timer > 0) {
                timer--;
            } else {
                active = false;
                wasClicking = false;
                return;
            }
        }

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
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        renderToast(event.getGuiGraphics(), mc);
    }

    private static void renderToast(GuiGraphics g, Minecraft mc) {
        int sw = mc.getWindow().getGuiScaledWidth();

        float alpha = (!playerHasMoved || timer >= FADE_TICKS) ? 1.0f : (float) timer / FADE_TICKS;

        var font = mc.font;
        int innerW = TOAST_W - 16;
        int lineH = font.lineHeight + 2;

        String title = net.minecraft.network.chat.Component.translatable("ui.phantasia.welcome.installed").getString();
        String instruction = net.minecraft.network.chat.Component.translatable("ui.phantasia.welcome.instruction")
                .getString();
        String openGuide = net.minecraft.network.chat.Component.translatable("ui.phantasia.welcome.open_guide")
                .getString();

        var instrLines = font.split(net.minecraft.network.chat.Component.literal(instruction), innerW);
        var guideLines = font.split(net.minecraft.network.chat.Component.literal(openGuide), innerW);

        int contentH = lineH + 3 + instrLines.size() * lineH + 3 + guideLines.size() * lineH;
        TOAST_H = 6 + contentH + 6;

        int tx = sw - TOAST_W - 8;
        int ty = 8;
        toastX = tx;
        toastY = ty;

        PhoenixTheme theme = PhoenixTheme.current();
        int bgColor = withAlpha(theme.bg.getColor(), (int) (0xDD * alpha));
        int bdrColor = withAlpha(theme.accent.getColor(), (int) (0xFF * alpha));
        int txtColor = withAlpha(theme.text.getColor(), (int) (0xFF * alpha));
        int dimColor = withAlpha(theme.textDim.getColor(), (int) (0xAA * alpha));

        g.fill(tx, ty, tx + TOAST_W, ty + TOAST_H, bgColor);
        g.fill(tx, ty, tx + TOAST_W, ty + 1, bdrColor);
        g.fill(tx, ty + TOAST_H - 1, tx + TOAST_W, ty + TOAST_H,
                withAlpha(theme.accent.getColor(), (int) (0x44 * alpha)));

        int lx = tx + 8;
        int y = ty + 6;
        g.drawString(font, title, lx, y, bdrColor, false);
        y += lineH + 3;
        for (var line : instrLines) {
            g.drawString(font, line, lx, y, txtColor, false);
            y += lineH;
        }
        y += 3;
        for (var line : guideLines) {
            g.drawString(font, line, lx, y, dimColor, false);
            y += lineH;
        }
    }

    private static int withAlpha(int argb, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (argb & 0xFFFFFF);
    }
}
