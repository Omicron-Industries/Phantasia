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

@OnlyIn(Dist.CLIENT)
public abstract class PhantasiaScreen extends Screen {

    public static final int TOP_BAR_H = 22;

    public record Btn(int x, int y, int w, int h, Runnable action) {

        public boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public final List<Btn> btns = new ArrayList<>(64);

    public void renderAsBackground(net.minecraft.client.gui.GuiGraphics g, float partial) {
        g.fill(0, 0, this.width, this.height, net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.C_BG());
    }

    public String pendingTooltip = null;

    public PhantasiaScreen(Component title) {
        super(title);
    }

    public void renderPendingTooltip(GuiGraphics g, int mx, int my) {
        if (pendingTooltip == null) return;
        int tw = font.width(pendingTooltip) + 8;
        int tx = Math.min(mx + 12, this.width - tw - 2);
        int ty = Math.max(my - 18, TOP_BAR_H + 2);
        g.fill(tx - 2, ty - 2, tx + tw + 2, ty + 12, 0xDD070712);
        g.fill(tx - 2, ty - 2, tx + tw + 2, ty - 1, C_ACCENT());
        g.drawString(font, pendingTooltip, tx + 4, ty + 2, C_TEXT(), false);
    }

    public void btn(GuiGraphics g, int mx, int my,
                    int x, int y, int w, int h,
                    String label, int base, Runnable action) {
        boolean hov = isOver(mx, my, x, y, w, h);
        PhantasiaThemeUtils.drawThemedBtn(g, font, x, y, w, h, label, hov, base);
        btns.add(new Btn(x, y, w, h, action));
    }

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

    public void drawBanner(GuiGraphics g, String text, int y, int accentColor) {
        int tw = font.width(text) + 20;
        int tx = (this.width - tw) / 2;
        g.fill(tx, y, tx + tw, y + 16, 0xBB0C0C1A);
        g.fill(tx, y, tx + tw, y + 1, accentColor);
        g.drawString(font, text, tx + 10, y + 4, C_DIM(), false);
    }

    public void drawBannerWrapped(GuiGraphics g, String text, int y, int accentColor) {
        int maxLineW = Math.min(this.width - 40, 420);
        var lines = font.split(net.minecraft.network.chat.Component.literal(text), maxLineW);
        if (lines.isEmpty()) return;
        int lineH = 11;
        int innerW = 0;
        for (var line : lines) innerW = Math.max(innerW, font.width(line));
        int tw = innerW + 20;
        int tx = (this.width - tw) / 2;
        int totalH = lines.size() * lineH + 8;
        g.fill(tx, y, tx + tw, y + totalH, 0xBB0C0C1A);
        g.fill(tx, y, tx + tw, y + 1, accentColor);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), tx + 10, y + 4 + i * lineH, C_DIM(), false);
        }
    }

    public void placeBox(EditBox box, int x, int y, int w, int h) {
        box.setX(x);
        box.setY(y);
        box.setWidth(w);
        box.setHeight(h);
        box.visible = true;
        box.active = true;
    }

    public <T extends net.minecraft.client.gui.components.AbstractWidget> T addW(T w) {
        w.visible = false;
        w.active = false;
        return addRenderableWidget(w);
    }

    public abstract void hideAllInputs();

    public boolean isOver(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public boolean isOver(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public String trunc(String s, int maxPx) {
        if (s == null) return "";
        while (font.width(s) > maxPx && s.length() > 2)
            s = s.substring(0, s.length() - 2) + "\u2026";
        return s;
    }

    public static int parseIntOrZero(String v) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String lerpTypeList() {
        StringBuilder sb = new StringBuilder();
        for (LerpType lt : LerpType.values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(lt.name());
        }
        return sb.toString();
    }
}
