package net.phoenixvine.phantasia.utils;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class PhantasiaThemeUtils {

    // ── Core Semantic Properties ─────────────────────────────────────────────

    public static int C_BG() {
        return PhantasiaTheme.current().bg();
    }

    public static int C_PANEL() {
        return PhantasiaTheme.current().panel();
    }

    public static int C_BTN() {
        return PhantasiaTheme.current().btn();
    }

    public static int C_BTN_HOV() {
        return PhantasiaTheme.current().btnHov();
    }

    public static int C_BTN_ACT() {
        return PhantasiaTheme.current().btnAct();
    }

    public static int C_TEXT() {
        return PhantasiaTheme.current().text();
    }

    public static int C_DIM() {
        return PhantasiaTheme.current().dim();
    }

    public static int C_TL_BG() {
        return PhantasiaTheme.current().tlBg();
    }

    public static int C_WARN() {
        return PhantasiaTheme.current().warn();
    }

    public static int C_ACCENT() {
        return PhantasiaTheme.current().accent();
    }

    public static int C_PROG() {
        return PhantasiaTheme.current().prog();
    }

    public static int C_HILIGHT() {
        return PhantasiaTheme.current().hilight();
    }

    public static int C_BORDER() {
        return PhantasiaTheme.current().dim();
    }

    // ── Legacy aliases ────────────────────────────────────────────────────────
    // These are plain passthroughs to the semantic getters above, kept around
    // so older screen code written before the semantic color system existed
    // doesn't need to be touched just to keep compiling. Each one is a direct
    // 1:1 alias — no extra logic, no separate color computation.
    // New code should call the semantic getter directly instead of these.

    /** Legacy alias for {@link #C_PANEL()}. Old name for the top-bar background. */
    public static int C_BAR() {
        return PhantasiaTheme.current().panel();
    }

    /**
     * Legacy alias for {@link #C_WARN()}, using its own animation offset (6000)
     * one slot past the last semantic offset (hilight=5500) so an animated
     * "warn" color and an animated "red" color don't land on the same hue
     * at the same moment.
     */
    public static int C_RED() {
        return PhantasiaTheme.current().warn.getColor(6000);
    }

    /** Legacy alias for {@link #C_HILIGHT()}. Old name used for list-item selection. */
    public static int C_SEL() {
        return PhantasiaTheme.current().hilight();
    }

    /** Legacy alias for {@link #C_BTN_HOV()}. */
    public static int C_BTN_H() {
        return PhantasiaTheme.current().btnHov();
    }

    /** Legacy alias for {@link #C_HILIGHT()}. Old name used for cycling/step selection. */
    public static int C_CYCLE() {
        return PhantasiaTheme.current().hilight();
    }

    /** Legacy alias for {@link #C_WARN()}. */
    public static int C_ORANGE() {
        return PhantasiaTheme.current().warn();
    }

    /** Legacy alias for {@link #C_PROG()}. Old name used for positive/active feedback. */
    public static int C_GREEN() {
        return PhantasiaTheme.current().prog();
    }

    // ── Rendering Implementations ────────────────────────────────────────────

    public static void drawThemedBtn(GuiGraphics g, Font font, int x, int y, int w, int h, String label, boolean hov,
                                     int baseColor) {
        if (!label.isEmpty()) {
            // Tightened down to +2 total padding to respect tight grid systems
            int minRequiredW = font.width(label) + 2;
            if (minRequiredW > w) {
                w = minRequiredW;
            }
        }

        PhantasiaTheme t = PhantasiaTheme.current();
        if ("MINECRAFT".equalsIgnoreCase(PhantasiaTheme.getActiveName())) {
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
                                      int baseColor, PhantasiaTheme t) {
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

    private static void drawMinecraftBtn(GuiGraphics g, Font font, int x, int y, int w, int h, String label,
                                         boolean hov, int baseColor) {
        // 1. Core Outer Black Frame
        g.fill(x, y, x + w, y + h, 0xFF000000);

        // 2. Determine Inner Body Fill (Vanilla Uses 0xFF707070 Base, 0xFF8B8B8B Hovered)
        int fill = hov ? 0xFF8B8B8B : 0xFF707070;

        // If the button is explicitly flagged active/pressed, toggle to the darker inset tone
        if (baseColor == PhantasiaTheme.current().btnAct()) {
            fill = 0xFF4A4A4A;
        }
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);

        // 3. Draw Top and Left Inner Highlight Bevels
        int highlightColor = hov ? 0xFFB0B0B0 : 0xFFAEAEAE;
        g.fill(x + 1, y + 1, x + w - 1, y + 2, highlightColor); // Top Inner Edge
        g.fill(x + 1, y + 2, x + 2, y + h - 1, highlightColor); // Left Inner Edge

        // 4. Draw Bottom and Right Inner Shadow Bevels
        int shadowColor = hov ? 0xFF5A5A5A : 0xFF373737;
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, shadowColor); // Bottom Inner Edge
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, shadowColor); // Right Inner Edge

        // 5. Pixel-Perfect Vanilla Text Positioning
        if (!label.isEmpty()) {
            int lx = x + (w - font.width(label)) / 2;
            int ly = y + (h - 8) / 2;

            // Determine Text Color (Vanilla is Bright Yellow 0xFFFF55 when hovered, White 0xFFE0E0E0 normally)
            int textColor = hov ? 0xFFFFFF55 : 0xFFE0E0E0;

            // Render native dropshadow text layout manually
            // Shadow offset: +1 X, +1 Y using a deep muddy variant of the color
            int textShadowColor = hov ? 0xFF3F3F15 : 0xFF383838;
            g.drawString(font, label, lx + 1, ly + 1, textShadowColor, false);

            // Core Text Front Layer
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
