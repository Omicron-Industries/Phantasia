package net.phoenixvine.phantasia.utils;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.phoenixvine.wiki.theme.PhoenixTheme;

public class PhantasiaThemeUtils {

    public static int C_BG() {
        return PhoenixTheme.current().bg.getColor();
    }

    public static int C_PANEL() {
        return PhoenixTheme.current().panel.getColor();
    }

    public static int C_BTN() {
        return PhoenixTheme.current().btn.getColor();
    }

    public static int C_BTN_HOV() {
        return PhoenixTheme.current().btnHover.getColor();
    }

    public static int C_BTN_ACT() {
        return PhoenixTheme.current().btnActive.getColor();
    }

    public static int C_TEXT() {
        return PhoenixTheme.current().text.getColor();
    }

    public static int C_DIM() {
        return PhoenixTheme.current().textDim.getColor();
    }

    public static int C_TL_BG() {
        return PhoenixTheme.current().timelineBg.getColor();
    }

    public static int C_WARN() {
        return PhoenixTheme.current().warn.getColor();
    }

    public static int C_ACCENT() {
        return PhoenixTheme.current().accent.getColor();
    }

    public static int C_PROG() {
        return PhoenixTheme.current().progress.getColor();
    }

    public static int C_HILIGHT() {
        return PhoenixTheme.current().highlight.getColor();
    }

    public static int C_BORDER() {
        return PhoenixTheme.current().textDim.getColor();
    }

    public static int C_BAR() {
        return PhoenixTheme.current().panel.getColor();
    }

    public static int C_RED() {
        return PhoenixTheme.current().warn.getColor();
    }

    public static int C_SEL() {
        return PhoenixTheme.current().highlight.getColor();
    }

    public static int C_BTN_H() {
        return PhoenixTheme.current().btnHover.getColor();
    }

    public static int C_CYCLE() {
        return PhoenixTheme.current().highlight.getColor();
    }

    public static int C_ORANGE() {
        return PhoenixTheme.current().warn.getColor();
    }

    public static int C_GREEN() {
        return PhoenixTheme.current().progress.getColor();
    }

    public static void drawThemedBtn(GuiGraphics g, Font font, int x, int y, int w, int h, String label, boolean hov,
                                     int baseColor) {
        if (!label.isEmpty()) {

            int minRequiredW = font.width(label) + 2;
            if (minRequiredW > w) {
                w = minRequiredW;
            }
        }

        PhoenixTheme t = PhoenixTheme.current();
        if ("MINECRAFT".equalsIgnoreCase(PhoenixTheme.getActiveName())) {
            drawMinecraftBtn(g, font, x, y, w, h, label, hov, baseColor);
        } else {
            drawModernBtn(g, font, x, y, w, h, label, hov, baseColor, t);
        }
    }

    public static void drawIconBtn(GuiGraphics g, Font font, int x, int y, int w, int h, String icon, String label,
                                   boolean hov, int baseColor) {
        drawThemedBtn(g, font, x, y, w, h, "", hov, baseColor);
        int midY = y + (h - 8) / 2;
        g.drawString(font, icon, x + 6, midY, C_ACCENT(), false);
        g.drawString(font, label, x + 20, midY, hov ? C_ACCENT() : C_TEXT(), false);
    }

    private static void drawModernBtn(GuiGraphics g, Font font, int x, int y, int w, int h, String label, boolean hov,
                                      int baseColor, PhoenixTheme t) {
        g.fill(x, y, x + w, y + h, hov ? t.btnHover.getColor() : baseColor);
        if (hov) {
            int accent = t.accent.getColor();
            g.fill(x, y, x + w, y + 1, accent);
            g.fill(x, y + h - 1, x + w, y + h, accent);
        }
        int textColor = hov ? t.accent.getColor() : t.text.getColor();
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, textColor, false);
    }

    private static final int MC_BORDER = 0xFF000000;
    private static final int MC_SHADOW = 0xFF373737;
    private static final int MC_HIGHLIGHT = 0xFFAAAAAA;
    private static final int MC_FILL = 0xFF8B8B8B;

    private static void drawMinecraftBtn(GuiGraphics g, Font font, int x, int y, int w, int h, String label,
                                         boolean hov, int baseColor) {
        g.fill(x, y, x + w, y + h, 0xFF000000);

        int fill = hov ? 0xFF8B8B8B : 0xFF707070;

        if (baseColor == PhoenixTheme.current().btnActive.getColor()) {
            fill = 0xFF4A4A4A;
        }
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);

        int highlightColor = hov ? 0xFFB0B0B0 : 0xFFAEAEAE;
        g.fill(x + 1, y + 1, x + w - 1, y + 2, highlightColor);
        g.fill(x + 1, y + 2, x + 2, y + h - 1, highlightColor);

        int shadowColor = hov ? 0xFF5A5A5A : 0xFF373737;
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, shadowColor);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, shadowColor);

        if (!label.isEmpty()) {
            int lx = x + (w - font.width(label)) / 2;
            int ly = y + (h - 8) / 2;

            int textColor = hov ? 0xFFFFFF55 : 0xFFE0E0E0;

            int textShadowColor = hov ? 0xFF3F3F15 : 0xFF383838;
            g.drawString(font, label, lx + 1, ly + 1, textShadowColor, false);

            g.drawString(font, label, lx, ly, textColor, false);
        }
    }

    public static void drawBorderRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }
}
