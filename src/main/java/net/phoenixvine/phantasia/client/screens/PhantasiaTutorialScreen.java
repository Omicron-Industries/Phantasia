package net.phoenixvine.phantasia.client.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.client.tutorial.TutorialSequence;
import net.phoenixvine.phantasia.client.tutorial.TutorialSlide;
import net.phoenixvine.phantasia.utils.PhantasiaTheme;

import java.util.List;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

@OnlyIn(Dist.CLIENT)
public class PhantasiaTutorialScreen extends PhantasiaScreen {

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int HEADER_H = 28;
    private static final int TEXT_H = 160;  // bottom panel
    private static final int MOCK_PAD = 10;
    private static final int VW = 480;      // Virtual Width matching mock screen space
    private static final int VH = 300;      // Virtual Height matching mock screen space

    // ── State ─────────────────────────────────────────────────────────────────
    private final Screen parent;
    private final TutorialSequence sequence;
    private int slideIndex = 0;
    private int animTick = 0;   // increments every render frame
    private int typeChars = 0;   // characters revealed by typewriter
    private boolean textDone = false;

    // Cursor animation
    private float cursorX = 0.5f, cursorY = 0.5f; // relative 0-1 within mock area
    private float startX = 0.5f, startY = 0.5f;   // stable leg-start coordinates
    private int waypointIdx = 0;
    private int waypointTicksIn = 0; // ticks spent in current waypoint phase

    // Click flash
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

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);

        int[] nav = navButtonBounds();
        // Next
        if (nav[2] > 0 && isOver((int) mx, (int) my, nav[2], nav[3], 70, 18)) {
            advance();
            return true;
        }
        // Prev
        if (nav[0] > 0 && isOver((int) mx, (int) my, nav[0], nav[1], 70, 18)) {
            retreat();
            return true;
        }
        // Close
        int cx = this.width - 20, cy = 6;
        if (isOver((int) mx, (int) my, cx, cy, 14, 14)) {
            onClose();
            return true;
        }
        // Click anywhere to rush typewriter
        if (!textDone) {
            typeChars = currentSlide().text.getString().length();
            textDone = true;
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 257 /* Enter */ || key == 32 /* Space */) {
            if (!textDone) {
                typeChars = currentSlide().text.getString().length();
                textDone = true;
                return true;
            }
            advance();
            return true;
        }
        if (key == 263 /* Left */) {
            retreat();
            return true;
        }
        if (key == 256 /* Escape */) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    // ── Slide control ─────────────────────────────────────────────────────────

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

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        tick();

        PhantasiaTheme theme = PhantasiaTheme.current();
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

        // Render the typewriter skip hint above the nav button row.
        if (!textDone) {
            g.drawString(font, "Click or [Space] to skip...", this.width - 130, this.height - 36, C_DIM(), false);
        }

        super.render(g, mx, my, partial);
    }

    public void tick() {
        animTick++;

        // Typewriter
        TutorialSlide slide = currentSlide();
        int textLen = slide.text.getString().length();
        if (!textDone) {
            typeChars = Math.min(typeChars + 2, textLen);
            if (typeChars >= textLen) textDone = true;
        }

        // Cursor waypoints
        List<TutorialSlide.CursorWaypoint> path = slide.cursor;
        if (!path.isEmpty()) {
            // Loop back around infinitely when the path finishes
            if (waypointIdx >= path.size()) {
                waypointIdx = 0;
                waypointTicksIn = 0;
                startX = cursorX;
                startY = cursorY;
            }

            TutorialSlide.CursorWaypoint wp = path.get(waypointIdx);
            waypointTicksIn++;

            if (waypointTicksIn <= wp.travelTicks()) {
                // Lerp towards waypoint using unmutated start coordinates
                float t = (float) waypointTicksIn / Math.max(1, wp.travelTicks());
                t = t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2; // ease in-out

                cursorX = startX + (wp.relX() - startX) * t;
                cursorY = startY + (wp.relY() - startY) * t;
            } else {
                // Dwelling
                cursorX = wp.relX();
                cursorY = wp.relY();
                int dwellTick = waypointTicksIn - wp.travelTicks();
                if (wp.click() && dwellTick == 2) clickFlashTick = animTick;
                if (dwellTick >= wp.dwellTicks()) {
                    waypointIdx++;
                    waypointTicksIn = 0;
                    startX = cursorX; // Establish current position as next leg's starting baseline
                    startY = cursorY;
                }
            }
        }
    }

    private void renderHeader(GuiGraphics g, PhantasiaTheme theme) {
        g.fill(0, 0, this.width, HEADER_H, 0xCC0A0A14);
        g.fill(0, HEADER_H - 1, this.width, HEADER_H, C_ACCENT());

        String title = sequence.title.getString() + "  ·  Slide " + (slideIndex + 1) + " of " + sequence.slides.size();
        g.drawCenteredString(font, title, this.width / 2, (HEADER_H - 8) / 2, C_ACCENT());

        // Close button [×]
        int cx = this.width - 20, cy = 6;
        boolean hov = isOver(
                (int) (minecraft.mouseHandler.xpos() * this.width / minecraft.getWindow().getScreenWidth()),
                (int) (minecraft.mouseHandler.ypos() * this.height / minecraft.getWindow().getScreenHeight()), cx, cy,
                14, 14);
        g.fill(cx, cy, cx + 14, cy + 14, hov ? C_BTN_HOV() : C_BTN());
        g.drawCenteredString(font, "×", cx + 7, cy + 3, hov ? C_ACCENT() : C_DIM());
    }

    private void renderMock(GuiGraphics g, int mx, int my, int mw, int mh) {
        // Background
        g.fill(mx, my, mx + mw, my + mh, 0xFF05050D);
        g.fill(mx, my, mx + mw, my + 1, 0x33FFFFFF);
        g.fill(mx, my, mx + 1, my + mh, 0x22FFFFFF);

        TutorialSlide slide = currentSlide();
        if (slide.mock != null) {
            // Scissor to mock area
            g.enableScissor(mx, my, mx + mw, my + mh);
            slide.mock.render(g, mx, my, mw, mh, animTick);
            g.disableScissor();
        }
    }

    private void renderHighlights(GuiGraphics g, int mx, int my, int mw, int mh) {
        TutorialSlide slide = currentSlide();
        if (slide.highlights.isEmpty()) return;

        // Dim overlay over entire mock area
        g.fill(mx, my, mx + mw, my + mh, 0x88000000);

        // Compute aspect ratio letterbox scale dimensions to precisely track mock internal offsets
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

            // Re-render mock clipped to highlighted rect (removes dim)
            if (slide.mock != null) {
                g.enableScissor(hx, hy, hx + hw, hy + hh);
                slide.mock.render(g, mx, my, mw, mh, animTick);
                g.disableScissor();
            }

            // Animated border
            g.fill(hx - 1, hy - 1, hx + hw + 1, hy, borderColor); // top
            g.fill(hx - 1, hy + hh, hx + hw + 1, hy + hh + 1, borderColor); // bottom
            g.fill(hx - 1, hy, hx, hy + hh, borderColor); // left
            g.fill(hx + hw, hy, hx + hw + 1, hy + hh, borderColor); // right

            // Label above highlight
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

        // Compute aspect ratio letterbox scale dimensions to precisely track mock internal offsets
        float s = Math.min(mw / (float) VW, mh / (float) VH);
        int actualW = (int) (VW * s);
        int actualH = (int) (VH * s);
        int ox = mx + (mw - actualW) / 2;
        int oy = my + (mh - actualH) / 2;

        int cx = ox + (int) (cursorX * actualW);
        int cy = oy + (int) (cursorY * actualH);

        // Click flash ring
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

        // Outer glow ring
        float pulse = (float) (Math.sin(animTick * 0.15) * 0.3 + 0.7);
        int ga = (int) (0xBB * pulse);
        int gCol = (ga << 24) | (C_ACCENT() & 0xFFFFFF);
        int r = 6;
        g.fill(cx - r, cy - 1, cx + r + 1, cy, gCol); // top arm
        g.fill(cx - r, cy + 1, cx + r + 1, cy + 2, gCol); // bottom arm
        g.fill(cx - 1, cy - r, cx, cy + r + 1, gCol); // left arm
        g.fill(cx + 1, cy - r, cx + 2, cy + r + 1, gCol); // right arm

        // Core dot (white)
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
        g.fill(cx, cy - 2, cx + 1, cy + 3, 0xFFFFFFFF);
        g.fill(cx - 2, cy, cx + 3, cy + 1, 0xFFFFFFFF);
    }

    private void renderTextPanel(GuiGraphics g, PhantasiaTheme theme) {
        int panelY = this.height - TEXT_H;
        g.fill(0, panelY, this.width, this.height, 0xCC0A0A14);
        g.fill(0, panelY, this.width, panelY + 1, 0x44FFFFFF);

        TutorialSlide slide = currentSlide();

        // Slide dots — fixed position top-right of text panel
        int dotY = panelY + 8;
        int dotStartX = this.width / 2 - sequence.slides.size() * 6;
        for (int i = 0; i < sequence.slides.size(); i++) {
            boolean active = i == slideIndex;
            g.fill(dotStartX + i * 12, dotY + 1, dotStartX + i * 12 + 6, dotY + 7,
                    active ? C_ACCENT() : C_DIM());
        }

        // Slide title — wraps within left column so it never overlaps the dots
        int maxTitleW = dotStartX - 28;
        var titleLines = font.split(slide.title, maxTitleW);
        int titleY = panelY + 6;
        for (var line : titleLines) {
            g.drawString(font, line, 16, titleY, C_ACCENT(), false);
            titleY += font.lineHeight + 1;
        }

        // Separator adapts to title height
        int sepY = Math.max(panelY + 20, titleY + 3);
        g.fill(16, sepY, this.width - 16, sepY + 1, 0x33FFFFFF);

        // Typewriter text — word-wrapped to screen width
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

    private void renderNavButtons(GuiGraphics g, PhantasiaTheme theme) {
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

    /** Returns [prevX, prevY, nextX, nextY] — value is -1 if button shouldn't show. */
    private int[] navButtonBounds() {
        int by = this.height - 24;
        int prevX = slideIndex > 0 ? 16 : -1;
        int nextX = this.width - 86;
        return new int[] { prevX, by, nextX, by };
    }
}
