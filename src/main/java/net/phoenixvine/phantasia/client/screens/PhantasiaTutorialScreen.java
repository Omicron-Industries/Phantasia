package net.phoenixvine.phantasia.client.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.client.tutorial.TutorialSequence;
import net.phoenixvine.phantasia.client.tutorial.TutorialSlide;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import java.util.List;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

@OnlyIn(Dist.CLIENT)
public class PhantasiaTutorialScreen extends PhantasiaScreen {

    private static final int HEADER_H = 28;
    private static final int TEXT_H = 160;
    private static final int MOCK_PAD = 10;
    private static final int VW = 480;
    private static final int VH = 300;

    private final Screen parent;
    private final TutorialSequence sequence;
    private int slideIndex = 0;
    private int animTick = 0;
    private int typeChars = 0;
    private boolean textDone = false;

    private float cursorX = 0.5f, cursorY = 0.5f;
    private float startX = 0.5f, startY = 0.5f;
    private int waypointIdx = 0;
    private int waypointTicksIn = 0;

    private int clickFlashTick = -1;

    public PhantasiaTutorialScreen(Screen parent, TutorialSequence sequence) {
        super(sequence.title);
        this.parent = parent;
        this.sequence = sequence;
    }

    public PhantasiaTutorialScreen(Screen parent, TutorialSequence sequence, int startSlide) {
        this(parent, sequence);
        this.slideIndex = Math.max(0, Math.min(startSlide, sequence.slides.size() - 1));
    }

    @Override
    public void hideAllInputs() {}

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);

        int[] nav = navButtonBounds();

        if (nav[2] > 0 && isOver((int) mx, (int) my, nav[2], nav[3], 70, 18)) {
            advance();
            return true;
        }

        if (nav[0] > 0 && isOver((int) mx, (int) my, nav[0], nav[1], 70, 18)) {
            retreat();
            return true;
        }

        int cx = this.width - 20, cy = 6;
        if (isOver((int) mx, (int) my, cx, cy, 14, 14)) {
            onClose();
            return true;
        }

        if (!textDone) {
            typeChars = currentSlide().text.getString().length();
            textDone = true;
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 257 || key == 32) {
            if (!textDone) {
                typeChars = currentSlide().text.getString().length();
                textDone = true;
                return true;
            }
            advance();
            return true;
        }
        if (key == 263) {
            retreat();
            return true;
        }
        if (key == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private TutorialSlide currentSlide() {
        return sequence.slides.get(slideIndex);
    }

    private void goToSlide(int idx) {
        slideIndex = idx;
        animTick = 0;
        typeChars = 0;
        textDone = false;
        cursorX = 0.5f;
        cursorY = 0.5f;
        startX = 0.5f;
        startY = 0.5f;
        waypointIdx = 0;
        waypointTicksIn = 0;
        clickFlashTick = -1;
    }

    private void advance() {
        if (slideIndex < sequence.slides.size() - 1) goToSlide(slideIndex + 1);
        else onClose();
    }

    private void retreat() {
        if (slideIndex > 0) goToSlide(slideIndex - 1);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        tick();

        PhoenixTheme theme = PhoenixTheme.current();
        g.fillGradient(0, 0, this.width, this.height, C_BG(), 0xFF0B0B18);

        renderHeader(g, theme);

        int mockX = MOCK_PAD;
        int mockY = HEADER_H + MOCK_PAD;
        int mockW = this.width - MOCK_PAD * 2;
        int mockH = this.height - HEADER_H - TEXT_H - MOCK_PAD * 2;

        renderMock(g, mockX, mockY, mockW, mockH);
        renderHighlights(g, mockX, mockY, mockW, mockH);
        renderCursor(g, mockX, mockY, mockW, mockH);

        renderTextPanel(g, theme);
        renderNavButtons(g, theme);

        if (!textDone) {
            g.drawString(font, "Click or [Space] to skip...", this.width - 130, this.height - 36, C_DIM(), false);
        }

        super.render(g, mx, my, partial);
    }

    public void tick() {
        animTick++;

        TutorialSlide slide = currentSlide();
        int textLen = slide.text.getString().length();
        if (!textDone) {
            typeChars = Math.min(typeChars + 2, textLen);
            if (typeChars >= textLen) textDone = true;
        }

        List<TutorialSlide.CursorWaypoint> path = slide.cursor;
        if (!path.isEmpty()) {

            if (waypointIdx >= path.size()) {
                waypointIdx = 0;
                waypointTicksIn = 0;
                startX = cursorX;
                startY = cursorY;
            }

            TutorialSlide.CursorWaypoint wp = path.get(waypointIdx);
            waypointTicksIn++;

            if (waypointTicksIn <= wp.travelTicks()) {

                float t = (float) waypointTicksIn / Math.max(1, wp.travelTicks());
                t = t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;

                cursorX = startX + (wp.relX() - startX) * t;
                cursorY = startY + (wp.relY() - startY) * t;
            } else {

                cursorX = wp.relX();
                cursorY = wp.relY();
                int dwellTick = waypointTicksIn - wp.travelTicks();
                if (wp.click() && dwellTick == 2) clickFlashTick = animTick;
                if (dwellTick >= wp.dwellTicks()) {
                    waypointIdx++;
                    waypointTicksIn = 0;
                    startX = cursorX;
                    startY = cursorY;
                }
            }
        }
    }

    private void renderHeader(GuiGraphics g, PhoenixTheme theme) {
        g.fill(0, 0, this.width, HEADER_H, 0xCC0A0A14);
        g.fill(0, HEADER_H - 1, this.width, HEADER_H, C_ACCENT());

        String title = sequence.title.getString() + "  ·  Slide " + (slideIndex + 1) + " of " + sequence.slides.size();
        g.drawCenteredString(font, title, this.width / 2, (HEADER_H - 8) / 2, C_ACCENT());

        int cx = this.width - 20, cy = 6;
        boolean hov = isOver(
                (int) (minecraft.mouseHandler.xpos() * this.width / minecraft.getWindow().getScreenWidth()),
                (int) (minecraft.mouseHandler.ypos() * this.height / minecraft.getWindow().getScreenHeight()), cx, cy,
                14, 14);
        g.fill(cx, cy, cx + 14, cy + 14, hov ? C_BTN_HOV() : C_BTN());
        g.drawCenteredString(font, "×", cx + 7, cy + 3, hov ? C_ACCENT() : C_DIM());
    }

    private void renderMock(GuiGraphics g, int mx, int my, int mw, int mh) {
        g.fill(mx, my, mx + mw, my + mh, 0xFF05050D);
        g.fill(mx, my, mx + mw, my + 1, 0x33FFFFFF);
        g.fill(mx, my, mx + 1, my + mh, 0x22FFFFFF);

        TutorialSlide slide = currentSlide();
        if (slide.mock != null) {

            g.enableScissor(mx, my, mx + mw, my + mh);
            slide.mock.render(g, mx, my, mw, mh, animTick);
            g.disableScissor();
        }
    }

    private void renderHighlights(GuiGraphics g, int mx, int my, int mw, int mh) {
        TutorialSlide slide = currentSlide();
        if (slide.highlights.isEmpty()) return;

        g.fill(mx, my, mx + mw, my + mh, 0x88000000);

        float s = Math.min(mw / (float) VW, mh / (float) VH);
        int actualW = (int) (VW * s);
        int actualH = (int) (VH * s);
        int ox = mx + (mw - actualW) / 2;
        int oy = my + (mh - actualH) / 2;

        float pulse = (float) (Math.sin(animTick * 0.10) * 0.3 + 0.7);
        int borderA = (int) (0xFF * pulse);
        int borderColor = (borderA << 24) | (C_ACCENT() & 0xFFFFFF);

        for (TutorialSlide.Highlight h : slide.highlights) {
            int hx = ox + (int) (h.relX() * actualW);
            int hy = oy + (int) (h.relY() * actualH);
            int hw = (int) (h.relW() * actualW);
            int hh = (int) (h.relH() * actualH);

            if (slide.mock != null) {
                g.enableScissor(hx, hy, hx + hw, hy + hh);
                slide.mock.render(g, mx, my, mw, mh, animTick);
                g.disableScissor();
            }

            g.fill(hx - 1, hy - 1, hx + hw + 1, hy, borderColor);
            g.fill(hx - 1, hy + hh, hx + hw + 1, hy + hh + 1, borderColor);
            g.fill(hx - 1, hy, hx, hy + hh, borderColor);
            g.fill(hx + hw, hy, hx + hw + 1, hy + hh, borderColor);

            if (h.label() != null) {
                int lw = font.width(h.label()) + 8;
                int lx = hx, ly = hy - 14;
                g.fill(lx, ly, lx + lw, ly + 12, (borderA << 24) | (C_ACCENT() & 0xFFFFFF & 0x33FFFFFF));
                g.fill(lx, ly, lx + lw, ly + 12, 0x88000000);
                g.drawString(font, h.label(), lx + 4, ly + 2, borderColor, false);
            }
        }
    }

    private void renderCursor(GuiGraphics g, int mx, int my, int mw, int mh) {
        TutorialSlide slide = currentSlide();
        if (slide.cursor.isEmpty()) return;

        float s = Math.min(mw / (float) VW, mh / (float) VH);
        int actualW = (int) (VW * s);
        int actualH = (int) (VH * s);
        int ox = mx + (mw - actualW) / 2;
        int oy = my + (mh - actualH) / 2;

        int cx = ox + (int) (cursorX * actualW);
        int cy = oy + (int) (cursorY * actualH);

        if (clickFlashTick >= 0) {
            int age = animTick - clickFlashTick;
            if (age < 15) {
                int r = age * 2;
                int fa = Math.max(0, (int) (0xAA * (1f - age / 15f)));
                int fc = (fa << 24) | 0xFFFFFF;
                g.fill(cx - r, cy - 1, cx + r + 1, cy, fc);
                g.fill(cx - 1, cy - r, cx, cy + r + 1, fc);
                g.fill(cx + r, cy - 1, cx + r + 1, cy + 1, fc);
                g.fill(cx - 1, cy + r, cx + 1, cy + r + 1, fc);
            } else {
                clickFlashTick = -1;
            }
        }

        float pulse = (float) (Math.sin(animTick * 0.15) * 0.3 + 0.7);
        int ga = (int) (0xBB * pulse);
        int gCol = (ga << 24) | (C_ACCENT() & 0xFFFFFF);
        int r = 6;
        g.fill(cx - r, cy - 1, cx + r + 1, cy, gCol);
        g.fill(cx - r, cy + 1, cx + r + 1, cy + 2, gCol);
        g.fill(cx - 1, cy - r, cx, cy + r + 1, gCol);
        g.fill(cx + 1, cy - r, cx + 2, cy + r + 1, gCol);

        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
        g.fill(cx, cy - 2, cx + 1, cy + 3, 0xFFFFFFFF);
        g.fill(cx - 2, cy, cx + 3, cy + 1, 0xFFFFFFFF);
    }

    private void renderTextPanel(GuiGraphics g, PhoenixTheme theme) {
        int panelY = this.height - TEXT_H;
        g.fill(0, panelY, this.width, this.height, 0xCC0A0A14);
        g.fill(0, panelY, this.width, panelY + 1, 0x44FFFFFF);

        TutorialSlide slide = currentSlide();

        int dotY = panelY + 8;
        int dotStartX = this.width / 2 - sequence.slides.size() * 6;
        for (int i = 0; i < sequence.slides.size(); i++) {
            boolean active = i == slideIndex;
            g.fill(dotStartX + i * 12, dotY + 1, dotStartX + i * 12 + 6, dotY + 7,
                    active ? C_ACCENT() : C_DIM());
        }

        int maxTitleW = dotStartX - 28;
        var titleLines = font.split(slide.title, maxTitleW);
        int titleY = panelY + 6;
        for (var line : titleLines) {
            g.drawString(font, line, 16, titleY, C_ACCENT(), false);
            titleY += font.lineHeight + 1;
        }

        int sepY = Math.max(panelY + 20, titleY + 3);
        g.fill(16, sepY, this.width - 16, sepY + 1, 0x33FFFFFF);

        String fullText = slide.text.getString();
        String shown = fullText.substring(0, Math.min(typeChars, fullText.length()));
        int ty = sepY + 6;
        int maxW = Math.max(60, this.width - 32);
        outer:
        for (String para : shown.split("\n", -1)) {
            for (var wrapped : font.split(net.minecraft.network.chat.Component.literal(para), maxW)) {
                if (ty + 10 > this.height - 28) break outer;
                g.drawString(font, wrapped, 16, ty, C_TEXT(), false);
                ty += 11;
            }
        }
    }

    private void renderNavButtons(GuiGraphics g, PhoenixTheme theme) {
        int[] nav = navButtonBounds();
        int imx = (int) (minecraft.mouseHandler.xpos() * this.width / minecraft.getWindow().getScreenWidth());
        int imy = (int) (minecraft.mouseHandler.ypos() * this.height / minecraft.getWindow().getScreenHeight());

        if (nav[0] > 0) {
            boolean hov = isOver(imx, imy, nav[0], nav[1], 70, 18);
            g.fill(nav[0], nav[1], nav[0] + 70, nav[1] + 18, hov ? C_BTN_HOV() : C_BTN());
            if (hov) g.fill(nav[0], nav[1], nav[0] + 70, nav[1] + 1, C_ACCENT());
            g.drawCenteredString(font, "← Previous", nav[0] + 35, nav[1] + 5, hov ? C_ACCENT() : C_TEXT());
        }

        if (nav[2] > 0) {
            boolean hov = isOver(imx, imy, nav[2], nav[3], 70, 18);
            boolean isLast = slideIndex == sequence.slides.size() - 1;
            String label = isLast ? "Finish ✓" : "Next →";
            g.fill(nav[2], nav[3], nav[2] + 70, nav[3] + 18, hov ? C_BTN_HOV() : C_BTN());
            g.fill(nav[2], nav[3], nav[2] + 70, nav[3] + 1, C_ACCENT());
            g.drawCenteredString(font, label, nav[2] + 35, nav[3] + 5, hov ? C_ACCENT() : C_TEXT());
        }
    }

    private int[] navButtonBounds() {
        int by = this.height - 24;
        int prevX = slideIndex > 0 ? 16 : -1;
        int nextX = this.width - 86;
        return new int[] { prevX, by, nextX, by };
    }
}
