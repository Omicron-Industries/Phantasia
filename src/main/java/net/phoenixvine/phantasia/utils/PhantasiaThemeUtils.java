package net.phoenixvine.phantasia.utils;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class PhantasiaThemeUtils {

    public static int C_BG() { return PhantasiaTheme.current().bg(); }
    public static int C_PANEL() { return PhantasiaTheme.current().panel(); }
    public static int C_BTN() { return PhantasiaTheme.current().btn(); }
    public static int C_BTN_HOV() { return PhantasiaTheme.current().btnHov(); }
    public static int C_BTN_ACT() { return PhantasiaTheme.current().btnAct(); }
    public static int C_TEXT() { return PhantasiaTheme.current().text(); }
    public static int C_DIM() { return PhantasiaTheme.current().dim(); }
    public static int C_TL_BG() { return PhantasiaTheme.current().tlBg(); }
    public static int C_WARN() { return PhantasiaTheme.current().warn(); }

    public static int C_ACCENT() { return PhantasiaTheme.current().accent(); }
    public static int C_PROG() { return PhantasiaTheme.current().prog(); }
    public static int C_HILIGHT() { return PhantasiaTheme.current().hilight(); }
    public static int C_BORDER() { return PhantasiaTheme.current().dim(); }

    public static void drawThemedBtn(GuiGraphics g, Font font, int x, int y, int w, int h, String label, boolean hov, int baseColor) {
        PhantasiaTheme t = PhantasiaTheme.current();
        if ("MINECRAFT".equalsIgnoreCase(PhantasiaTheme.getActiveName())) {
            drawMinecraftBtn(g, font, x, y, w, h, label, hov, baseColor);
        } else {
            drawModernBtn(g, font, x, y, w, h, label, hov, baseColor, t);
        }
    }

    public static void drawIconBtn(GuiGraphics g, Font font, int x, int y, int w, int h, String icon, String label, boolean hov, int baseColor) {
        drawThemedBtn(g, font, x, y, w, h, "", hov, baseColor);
        int midY = y + (h - 8) / 2;
        g.drawString(font, icon, x + 6, midY, C_ACCENT(), false);
        g.drawString(font, label, x + 20, midY, hov ? C_ACCENT() : C_TEXT(), false);
    }

    private static void drawModernBtn(GuiGraphics g, Font font, int x, int y, int w, int h, String label, boolean hov, int baseColor, PhantasiaTheme t) {
        g.fill(x, y, x + w, y + h, hov ? t.btnHov() : baseColor);
        if (hov) {
            int accent = t.accent();
            g.fill(x, y, x + w, y + 1, accent);
            g.fill(x, y + h - 1, x + w, y + h, accent);
        }
        int textColor = hov ? t.accent() : t.text();
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, textColor, false);
    }

    private static final int MC_BORDER = 0xFF000000;
    private static final int MC_SHADOW = 0xFF373737;
    private static final int MC_HIGHLIGHT = 0xFFAAAAAA;
    private static final int MC_FILL = 0xFF8B8B8B;

    private static void drawMinecraftBtn(GuiGraphics g, Font font, int x, int y, int w, int h, String label, boolean hov, int baseColor) {
        g.fill(x, y, x + w, y + h, MC_BORDER);
        int fill = hov ? 0xFF9A9A9A : baseColor == PhantasiaTheme.current().btnAct() ? baseColor : MC_FILL;
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, MC_HIGHLIGHT);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, MC_HIGHLIGHT);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, MC_SHADOW);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, MC_SHADOW);
        int lx = x + (w - font.width(label)) / 2;
        int ly = y + (h - 8) / 2;
        if (!label.isEmpty()) {
            g.drawString(font, label, lx + 1, ly + 1, 0xFF383838, false);
            g.drawString(font, label, lx, ly, hov ? 0xFFFFFFA0 : 0xFFFFFFFF, false);
        }
    }

    public static void drawBorderRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }
}
