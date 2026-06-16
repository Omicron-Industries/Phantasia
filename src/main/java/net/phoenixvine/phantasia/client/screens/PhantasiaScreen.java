package net.phoenixvine.phantasia.client.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.client.camera.LerpType;
import net.phoenixvine.phantasia.utils.PhantasiaThemeUtils;

import java.util.ArrayList;
import java.util.List;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

/**
 * PhantasiaScreen
 *
 * Base class for all Phantasia editor screens. Centralises:
 * - Theme constants (dynamically supplied from PhantasiaThemeUtils)
 * - The {@link Btn} record and button list
 * - {@code pendingTooltip} and its render pass
 * - Common rendering helpers: {@code btn}, {@code tipBtn}, {@code topBtn},
 * {@code renderCloseConfirmDialog}, {@code drawBanner}, {@code placeBox}
 * - Utility helpers: {@code isOver}, {@code trunc}, {@code parseIntOrZero},
 * {@code lerpTypeList}, {@code addW}
 *
 * Subclasses are responsible for their own undo stacks (typed per data model)
 * and their own {@code hideAllInputs()} implementations.
 */
@OnlyIn(Dist.CLIENT)
public abstract class PhantasiaScreen extends Screen {

    /** Top bar height — consistent across all editors. */
    public static final int TOP_BAR_H = 22;

    // ── Button list ───────────────────────────────────────────────────────────

    /**
     * A registered click target. Built fresh each frame in {@code render()} so
     * hit-testing always reflects the current layout.
     */
    public record Btn(int x, int y, int w, int h, Runnable action) {

        public boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public final List<Btn> btns = new ArrayList<>(64);

    // ── Pending tooltip ───────────────────────────────────────────────────────

    /**
     * Set during the render pass by any button/widget that wants a tooltip.
     * Drawn as a floating overlay at the very end of {@code render()} by
     * {@link #renderPendingTooltip}.
     */
    public String pendingTooltip = null;

    // ─────────────────────────────────────────────────────────────────────────

    public PhantasiaScreen(Component title) {
        super(title);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tooltip overlay
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Draw the floating tooltip bubble. Call at the very end of
     * {@code render()}, after {@code super.render()}, so it always renders on
     * top of widgets.
     */
    public void renderPendingTooltip(GuiGraphics g, int mx, int my) {
        if (pendingTooltip == null) return;
        int tw = font.width(pendingTooltip) + 8;
        int tx = Math.min(mx + 12, this.width - tw - 2);
        int ty = Math.max(my - 18, TOP_BAR_H + 2);
        g.fill(tx - 2, ty - 2, tx + tw + 2, ty + 12, 0xDD070712);
        g.fill(tx - 2, ty - 2, tx + tw + 2, ty - 1, C_ACCENT());
        g.drawString(font, pendingTooltip, tx + 4, ty + 2, C_TEXT(), false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Button helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Plain button. Registers the action in {@link #btns} and draws the button
     * completely via the theme layout utility.
     */
    public void btn(GuiGraphics g, int mx, int my,
                    int x, int y, int w, int h,
                    String label, int base, Runnable action) {
        boolean hov = isOver(mx, my, x, y, w, h);
        PhantasiaThemeUtils.drawThemedBtn(g, font, x, y, w, h, label, hov, base);
        btns.add(new Btn(x, y, w, h, action));
    }

    /**
     * Button with a tooltip. Sets {@link #pendingTooltip} on hover and renders
     * through the theme manager layout.
     */
    public void tipBtn(GuiGraphics g, int mx, int my,
                       int x, int y, int w, int h,
                       String label, int base, String tooltip, Runnable action) {
        boolean hov = isOver(mx, my, x, y, w, h);
        PhantasiaThemeUtils.drawThemedBtn(g, font, x, y, w, h, label, hov, base);
        if (hov) {
            pendingTooltip = tooltip;
        }
        btns.add(new Btn(x, y, w, h, action));
    }

    /**
     * Right-aligned top-bar button. Returns the x coordinate of the left edge
     * minus a 4 px gap for sequential horizontal packing.
     */
    public int topBtn(GuiGraphics g, int mx, int my,
                      int rx, String label, int color, String tooltip,
                      Runnable action) {
        int w = font.width(label) + 10;
        int x = rx - w, y = 3, h = TOP_BAR_H - 6;
        boolean hov = isOver(mx, my, x, y, w, h);
        PhantasiaThemeUtils.drawThemedBtn(g, font, x, y, w, h, label, hov, color);
        if (hov) {
            pendingTooltip = tooltip;
        }
        btns.add(new Btn(x, y, w, h, action));
        return x - 4;
    }

    /**
     * Left-aligned top-bar button. Draws at {@code x} and returns the right
     * edge plus a 4 px gap for chaining left-to-right.
     */
    public int topBtnL(GuiGraphics g, int mx, int my,
                       int x, String label, int color, String tooltip,
                       Runnable action) {
        int w = font.width(label) + 10, h = TOP_BAR_H - 6, y = 3;
        boolean hov = isOver(mx, my, x, y, w, h);
        PhantasiaThemeUtils.drawThemedBtn(g, font, x, y, w, h, label, hov, color);
        if (hov) {
            pendingTooltip = tooltip;
        }
        btns.add(new Btn(x, y, w, h, action));
        return x + w + 4;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Common dialog
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Renders a centered discard confirm dialog modal using dynamic theme elements.
     */
    public void renderCloseConfirmDialog(GuiGraphics g, int mx, int my,
                                         Runnable onDiscard, Runnable onKeep) {
        g.fill(0, 0, this.width, this.height, 0xBB000000);
        int dw = 280, dh = 70;
        int dx = (this.width - dw) / 2, dy = (this.height - dh) / 2;
        g.fill(dx, dy, dx + dw, dy + dh, C_PANEL());
        g.fill(dx, dy, dx + dw, dy + 1, C_WARN());
        g.drawCenteredString(font, "Unsaved changes \u2014 discard and close?", dx + dw / 2, dy + 10, C_WARN());
        g.drawCenteredString(font, Component.translatable("screen.phantasia.editor.close_confirm_body").getString(),
                dx + dw / 2, dy + 22, C_DIM());
        int btnY = dy + dh - 20;
        btn(g, mx, my, dx + dw / 2 - 118, btnY, 110, 14, "\u2715 Discard & Close", C_RED(), onDiscard);
        btn(g, mx, my, dx + dw / 2 + 8, btnY, 110, 14, "\u21A9 Keep Editing", C_BTN(), onKeep);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Banner
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Centered one-line banner pill — used for mode hints and preview overlays.
     */
    public void drawBanner(GuiGraphics g, String text, int y, int accentColor) {
        int tw = font.width(text) + 20;
        int tx = (this.width - tw) / 2;
        g.fill(tx, y, tx + tw, y + 16, 0xBB0C0C1A);
        g.fill(tx, y, tx + tw, y + 1, accentColor);
        g.drawString(font, text, tx + 10, y + 4, C_DIM(), false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Widget helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Position and show an {@link EditBox}. Call each frame from the render
     * method after {@link #hideAllInputs()} to lay out only the boxes that
     * are currently relevant.
     */
    public void placeBox(EditBox box, int x, int y, int w, int h) {
        box.setX(x);
        box.setY(y);
        box.setWidth(w);
        box.setHeight(h);
        box.visible = true;
        box.active = true;
    }

    /**
     * Register a widget as a renderable+interactive child and return it.
     * Widgets start hidden; use {@link #placeBox} to show them each frame.
     */
    public <T extends net.minecraft.client.gui.components.AbstractWidget> T addW(T w) {
        w.visible = false;
        w.active = false;
        return addRenderableWidget(w);
    }

    /**
     * Hide all edit boxes for this editor. Subclasses implement this to reset
     * every box to invisible at the start of each frame.
     */
    public abstract void hideAllInputs();

    // ─────────────────────────────────────────────────────────────────────────
    // Hit-testing
    // ─────────────────────────────────────────────────────────────────────────

    public boolean isOver(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public boolean isOver(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // String utilities
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Truncate {@code s} to fit within {@code maxPx} pixels of font width,
     * appending an ellipsis if it was cut.
     */
    public String trunc(String s, int maxPx) {
        if (s == null) return "";
        while (font.width(s) > maxPx && s.length() > 2)
            s = s.substring(0, s.length() - 2) + "\u2026";
        return s;
    }

    /** Parse an integer from a string, returning 0 on failure. */
    public static int parseIntOrZero(String v) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Comma-separated list of all {@link LerpType} names, derived directly
     * from the enum so it never drifts out of sync.
     */
    public static String lerpTypeList() {
        StringBuilder sb = new StringBuilder();
        for (LerpType lt : LerpType.values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(lt.name());
        }
        return sb.toString();
    }
}
