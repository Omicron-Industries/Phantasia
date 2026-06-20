package net.phoenixvine.phantasia.client.tutorial;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.client.screens.PhantasiaSceneSelectionScreen;
import net.phoenixvine.phantasia.common.data.guides.PhantasiaGuideRegistry;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScripts;
import net.phoenixvine.phantasia.utils.PhantasiaTheme;

import java.util.List;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

/**
 * All hardcoded tutorial sequences.
 * Each slide's MockRenderer scales a 480×300 virtual screen into the mock area
 * and faithfully replicates the look of the real Phantasia screens, using
 * real machine/guide/script data where available.
 */
@OnlyIn(Dist.CLIENT)
public final class PhantasiaTutorials {

    private PhantasiaTutorials() {}

    // ── Virtual screen size ───────────────────────────────────────────────────
    // All mock renderers paint into a 480×300 virtual coordinate space.
    // withScale() maps that into the actual mock rect.
    private static final int VW = 480;
    private static final int VH = 300;

    public static final List<TutorialSequence> ALL = List.of(
            gettingStarted(),
            guides(),
            scripts(),
            scenes(),
            devGuides(),
            devScripts());

    // ── Scale helper ──────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface DrawTask {

        void run(GuiGraphics g, Font f, PhantasiaTheme t, int tick);
    }

    private static TutorialSlide.MockRenderer mock(DrawTask task) {
        return (g, mx, my, mw, mh, tick) -> {
            Font f = Minecraft.getInstance().font;
            PhantasiaTheme t = PhantasiaTheme.current();
            float s = Math.min(mw / (float) VW, mh / (float) VH);
            int ox = mx + (mw - (int) (VW * s)) / 2;
            int oy = my + (mh - (int) (VH * s)) / 2;
            g.pose().pushPose();
            g.pose().translate(ox, oy, 0);
            g.pose().scale(s, s, 1f);
            task.run(g, f, t, tick);
            g.pose().popPose();
        };
    }

    // ── Selection screen replica (PhantasiaSceneSelectionScreen) ─────────────
    // HEADER_H=52, CARD_W=104, CARD_H=86, CARD_PAD=8, COLS=3, TAB_H=16,
    // SEARCH_H=24, FOOTER_H=30

    private static final int SEL_HEADER_H = 52;
    private static final int SEL_CARD_W = 104;
    private static final int SEL_CARD_H = 86;
    private static final int SEL_CARD_PAD = 8;
    private static final int SEL_GRID_W = 3 * SEL_CARD_W + 2 * SEL_CARD_PAD; // 328

    private static void drawSelectionScreen(GuiGraphics g, Font f, PhantasiaTheme t, int tick, int activeTab) {
        int gridX = (VW - SEL_GRID_W) / 2;

        // Background
        g.fillGradient(0, 0, VW, VH, C_BG(), 0xFF0B0B18);

        // Header
        g.fill(0, 0, VW, SEL_HEADER_H, 0xCC0A0A14);
        g.fill(0, SEL_HEADER_H - 2, VW, SEL_HEADER_H, C_ACCENT());
        g.drawCenteredString(f, "✶ Phantasia", VW / 2, 8, C_ACCENT());
        g.drawCenteredString(f, "Multiblock machines, scenes, and guides", VW / 2, 20, C_DIM());

        // Tabs — mirror real tab layout (tabY=32)
        String[] tabLabels = { "Multiblocks", "Scenes", "Guides", "Tutorials" };
        int tx = gridX;
        for (int i = 0; i < tabLabels.length; i++) {
            int tw = f.width(tabLabels[i]) + 16;
            boolean act = (i == activeTab);
            g.fill(tx, 32, tx + tw, 48, act ? C_BTN_HOV() : C_BTN());
            if (act) g.fill(tx, 46, tx + tw, 48, C_ACCENT());
            g.drawString(f, tabLabels[i], tx + 8, 36, act ? C_ACCENT() : C_DIM(), false);
            tx += tw + (i == 0 ? 0 : 4); // real layout: tab 1 is at tabStartX+104 hardcoded
        }

        // Search box (at HEADER_H + 4)
        int searchY = SEL_HEADER_H + 4;
        g.fill(gridX, searchY, gridX + SEL_GRID_W, searchY + 16, 0xFF0A0A14);
        g.fill(gridX, searchY, gridX + SEL_GRID_W, searchY + 1, 0xFF333355);
        g.fill(gridX, searchY + 15, gridX + SEL_GRID_W, searchY + 16, 0xFF333355);
        g.fill(gridX, searchY, gridX + 1, searchY + 16, 0xFF333355);
        g.fill(gridX + SEL_GRID_W - 1, searchY, gridX + SEL_GRID_W, searchY + 16, 0xFF333355);
        g.drawString(f, "Search...", gridX + 4, searchY + 4, 0xFF888888, false);

        // Cards
        int cardsY = searchY + 20;
        if (activeTab == 0) drawMachineCards(g, f, gridX, cardsY);
        else if (activeTab == 2) drawGuideCardsSelection(g, f, t, gridX, cardsY);
        else if (activeTab == 3) drawTutorialCardsSelection(g, f, t, gridX, cardsY);

        // Footer
        int footerY = VH - 26;
        g.fill(0, footerY, VW, VH, 0xCC0A0A14);
        g.fill(0, footerY, VW, footerY + 1, 0x33FFFFFF);
        g.drawCenteredString(f, "ESC to close  •  [P] near a machine to open directly", VW / 2, footerY + 9, C_DIM());
    }

    private static void drawMachineCards(GuiGraphics g, Font f, int gridX, int startY) {
        var machines = PhantasiaSceneSelectionScreen.PHANTASIA_SCENES;

        // Fallback names if machines haven't loaded yet
        String[] fallbackNames = { "Electric Blast Furnace", "Chemical Reactor", "Macerator",
                "Large Boiler", "Pyrolyse Oven", "Large Turbine" };
        boolean[] fallbackSteps = { true, true, false, true, false, false };

        for (int i = 0; i < 6; i++) {
            int col = i % 3, row = i / 3;
            int cx = gridX + col * (SEL_CARD_W + SEL_CARD_PAD);
            int cy = startY + row * (SEL_CARD_H + SEL_CARD_PAD);

            // card background — mirrors renderCard() in selection screen
            int cardBg = (0xBB << 24) | (C_PANEL() & 0x00FFFFFF);
            g.fill(cx, cy, cx + SEL_CARD_W, cy + SEL_CARD_H, cardBg);
            g.fill(cx, cy, cx + SEL_CARD_W, cy + 2, C_BORDER());

            // Item icon
            if (i < machines.size()) {
                var icon = machines.get(i).getIcon();
                if (!icon.isEmpty()) {
                    g.pose().pushPose();
                    g.pose().translate(cx + (SEL_CARD_W - 32) / 2f, cy + 6, 0);
                    g.pose().scale(2f, 2f, 1f);
                    g.renderItem(icon, 0, 0);
                    g.pose().popPose();
                }
            } else {
                g.fill(cx + 36, cy + 6, cx + 68, cy + 38, 0xFF334466);
                g.fill(cx + 36, cy + 6, cx + 68, cy + 7, 0x44FFFFFF);
            }

            // Machine name
            String name = (i < machines.size()) ? machines.get(i).getDisplayName() : fallbackNames[i];
            if (name == null || name.isEmpty()) {
                name = (i < machines.size()) ? machines.get(i).getId().getPath().replace('_', ' ') : fallbackNames[i];
            }
            if (f.width(name) > SEL_CARD_W - 8)
                name = f.plainSubstrByWidth(name, SEL_CARD_W - 8 - f.width("...")) + "...";
            g.drawString(f, name, cx + 4, cy + SEL_CARD_H - 22, C_TEXT(), false);

            // Green dot + step count
            boolean hasScript = (i < machines.size()) ? PhantasiaScripts.has(machines.get(i)) : fallbackSteps[i];
            if (hasScript) {
                g.fill(cx + SEL_CARD_W - 8, cy + 4, cx + SEL_CARD_W - 4, cy + 8, C_GREEN());
                String steps;
                if (i < machines.size() && PhantasiaScripts.has(machines.get(i))) {
                    steps = PhantasiaScripts.get(machines.get(i)).getSteps().size() + " steps";
                } else {
                    steps = (i == 0) ? "8 steps" : (i == 1) ? "5 steps" : "6 steps";
                }
                g.drawString(f, steps, cx + 4, cy + SEL_CARD_H - 10, C_DIM(), false);
            }
        }
    }

    private static void drawGuideCardsSelection(GuiGraphics g, Font f, PhantasiaTheme t, int gridX, int startY) {
        var guides = PhantasiaGuideRegistry.all().stream().limit(6).toList();
        String[] fallbackTitles = { "Getting Started", "Ore Processing", "Power Setup",
                "EBF Basics", "Recipe Tips", "Material Guide" };
        for (int i = 0; i < 6; i++) {
            int col = i % 3, row = i / 3;
            int cx = gridX + col * (SEL_CARD_W + SEL_CARD_PAD);
            int cy = startY + row * (SEL_CARD_H + SEL_CARD_PAD);
            g.fill(cx, cy, cx + SEL_CARD_W, cy + SEL_CARD_H, (0xBB << 24) | (C_PANEL() & 0x00FFFFFF));
            g.fill(cx, cy, cx + SEL_CARD_W, cy + 2, C_BORDER());
            // book icon placeholder
            g.fill(cx + 36, cy + 8, cx + 68, cy + 36, C_BTN());
            g.fill(cx + 36, cy + 8, cx + 68, cy + 9, C_ACCENT());
            String title = (i < guides.size()) ? guides.get(i).title : fallbackTitles[i];
            if (title == null) title = fallbackTitles[i];
            if (f.width(title) > SEL_CARD_W - 8)
                title = f.plainSubstrByWidth(title, SEL_CARD_W - 8 - f.width("...")) + "...";
            g.drawString(f, title, cx + 4, cy + SEL_CARD_H - 22, C_TEXT(), false);
        }
    }

    private static void drawTutorialCardsSelection(GuiGraphics g, Font f, PhantasiaTheme t, int gridX, int startY) {
        // Section label — mirrors renderTutorialCards() section headers
        g.drawString(f, "For Players", gridX, startY, C_ACCENT(), false);
        startY += 12;
        String[][] playerTuts = { { "Getting Started", "Overview" }, { "Understanding Guides", "Guides" },
                { "Understanding Scripts", "Scripts" } };
        for (int i = 0; i < 3; i++) {
            int cx = gridX + i * (SEL_CARD_W + SEL_CARD_PAD);
            g.fill(cx, startY, cx + SEL_CARD_W, startY + SEL_CARD_H, (0xBB << 24) | (C_PANEL() & 0x00FFFFFF));
            g.fill(cx, startY, cx + SEL_CARD_W, startY + 2, C_ACCENT());
            g.drawString(f, playerTuts[i][0], cx + 4, startY + SEL_CARD_H - 22, C_TEXT(), false);
            g.drawString(f, playerTuts[i][1], cx + 4, startY + SEL_CARD_H - 10, C_DIM(), false);
        }
        startY += SEL_CARD_H + 12;
        g.drawString(f, "For Pack Authors", gridX, startY, C_WARN(), false);
    }

    // ── Guide screen replica (PhantasiaGuideScreen) ───────────────────────────
    // TOP_BAR_H=22, NAV_H=30, COL_W=360 (we use 380 for our 480 virtual width)

    private static void drawGuideScreen(GuiGraphics g, Font f, PhantasiaTheme t,
                                        String title, String headline, String body,
                                        int pageIdx, int pageCount) {
        int TOP_BAR_H = 22, NAV_H = 30;
        int colW = 380, colX = (VW - colW) / 2;

        // Background
        g.fillGradient(0, 0, VW, VH, 0xFF07070E, 0xFF0D0D1E);

        // Top bar
        g.fill(0, 0, VW, TOP_BAR_H, C_BAR());
        g.fill(0, TOP_BAR_H - 1, VW, TOP_BAR_H, C_ACCENT());
        g.drawCenteredString(f, title, VW / 2, (TOP_BAR_H - 8) / 2, C_ACCENT());

        // Back button
        int bw = f.width("← Back") + 12;
        g.fill(4, 3, 4 + bw, TOP_BAR_H - 3, C_BTN());
        g.drawString(f, "← Back", 10, (TOP_BAR_H - 8) / 2, C_TEXT(), false);

        // Edit button (creative only, shown for demo)
        int ew = f.width("✏ Edit") + 12;
        g.fill(VW - 4 - ew, 3, VW - 4, TOP_BAR_H - 3, C_BTN());
        g.drawString(f, "✏ Edit", VW - 4 - ew + 6, (TOP_BAR_H - 8) / 2, C_TEXT(), false);

        // Content area
        int y = TOP_BAR_H + 14;

        // Headline
        if (headline != null && !headline.isEmpty()) {
            g.fill(colX, y, colX + colW, y + 1, C_ACCENT());
            y += 7;
            g.pose().pushPose();
            g.pose().translate(colX, y, 0);
            g.pose().scale(1.5f, 1.5f, 1f);
            g.drawString(f, headline, 0, 0, 0xFFEEEEFF, false);
            g.pose().popPose();
            y += (int) (f.lineHeight * 1.5f) + 6;
        } else {
            g.fill(colX, y, colX + colW, y + 1, 0x334FC3F7);
            y += 8;
        }

        // Page counter
        if (pageCount > 1) {
            g.drawString(f, "Page " + (pageIdx + 1) + " of " + pageCount, colX, y, C_DIM(), false);
            y += f.lineHeight + 5;
        }
        y += 4;

        // Body text
        if (body != null) {
            for (var line : f.split(net.minecraft.network.chat.Component.literal(body), colW)) {
                g.drawString(f, line, colX, y, C_TEXT(), false);
                y += f.lineHeight + 2;
            }
        }

        // Nav bar
        int navY = VH - NAV_H;
        g.fill(0, navY, VW, VH, 0xDD0A0A14);
        g.fill(0, navY, VW, navY + 1, 0x33FFFFFF);
        int midX = VW / 2;
        int bY = navY + 6, bH = NAV_H - 12;

        // Prev
        boolean hasPrev = pageIdx > 0;
        int prevW = f.width("◄  Prev") + 14;
        g.fill(midX - prevW - 26, bY, midX - 26, bY + bH, hasPrev ? C_BTN() : 0x33111128);
        g.drawCenteredString(f, "◄  Prev", midX - 26 - prevW / 2, bY + (bH - 8) / 2, hasPrev ? C_TEXT() : C_DIM());

        // Page indicator
        g.drawCenteredString(f, (pageIdx + 1) + " / " + pageCount, midX, bY + (bH - 8) / 2, C_DIM());

        // Next
        boolean hasNext = pageIdx < pageCount - 1;
        int nextW = f.width("Next  ►") + 14;
        g.fill(midX + 26, bY, midX + 26 + nextW, bY + bH, hasNext ? C_BTN() : 0x33111128);
        if (hasNext) g.fill(midX + 26, bY, midX + 26 + nextW, bY + 1, C_ACCENT());
        g.drawCenteredString(f, "Next  ►", midX + 26 + nextW / 2, bY + (bH - 8) / 2,
                hasNext ? C_ACCENT() : C_DIM());
    }

    // ── Guide editor replica (PhantasiaGuideEditorScreen) ────────────────────
    // TOP_H=22, rightWidth≈280

    private static void drawGuideEditor(GuiGraphics g, Font f, PhantasiaTheme t,
                                        String guideTitle, String headline, String bodyText, int tick) {
        int TOP_H = 22, rightW = 220;
        int previewW = VW - rightW;
        int colW = Math.min(320, previewW - 48);
        int colX = previewW / 2 - colW / 2;

        // Background
        g.fill(0, 0, VW, VH, C_BG());

        // Top bar
        g.fill(0, 0, VW, TOP_H, C_BAR());
        g.fill(0, TOP_H - 1, VW, TOP_H, C_ACCENT());

        // Back button
        int backW = f.width("← Back") + 10;
        g.fill(4, 3, 4 + backW, TOP_H - 3, C_BTN());
        g.drawString(f, "← Back", 9, (TOP_H - 8) / 2, C_TEXT(), false);

        // Title label + title box
        int titleLabelX = 4 + backW + 8;
        g.drawString(f, "Title:", titleLabelX, (TOP_H - 8) / 2, C_DIM(), false);
        int titleBoxX = titleLabelX + f.width("Title:") + 4;
        g.fill(titleBoxX, 3, titleBoxX + 140, TOP_H - 3, 0xFF0A0A14);
        g.fill(titleBoxX, 3, titleBoxX + 140, 4, 0xFF333355);
        g.drawString(f, guideTitle, titleBoxX + 3, (TOP_H - 8) / 2, C_TEXT(), false);

        // Save button (right side)
        int saveW = f.width("💾 Save") + 10;
        g.fill(VW - 4 - saveW, 3, VW - 4, TOP_H - 3, C_BTN());
        g.drawString(f, "💾 Save", VW - 4 - saveW + 5, (TOP_H - 8) / 2, C_TEXT(), false);

        // Preview button
        int prevBtnW = f.width("► Preview") + 10;
        g.fill(VW - 4 - saveW - 4 - prevBtnW, 3, VW - 4 - saveW - 4, TOP_H - 3, C_BTN());
        g.drawString(f, "► Preview", VW - 4 - saveW - 4 - prevBtnW + 5, (TOP_H - 8) / 2, C_TEXT(), false);

        // Left panel: dark editing area
        g.fill(0, TOP_H, previewW, VH, 0xFF070710);
        // Right panel separator line
        g.fill(previewW - 1, TOP_H, previewW, VH, C_ACCENT());

        // Headline editor box
        int y = TOP_H + 12;
        int hlH = 32;
        boolean hlFocused = (tick / 60) % 2 == 0;
        g.fill(colX - 4, y, colX + colW + 4, y + hlH, hlFocused ? 0xFF0D1C2A : 0xBB0D131A);
        // border
        g.fill(colX - 4, y, colX + colW + 4, y + 1, hlFocused ? C_ACCENT() : 0xFF223544);
        g.fill(colX - 4, y + hlH - 1, colX + colW + 4, y + hlH, hlFocused ? C_ACCENT() : 0xFF223544);
        g.fill(colX - 4, y, colX - 3, y + hlH, hlFocused ? C_ACCENT() : 0xFF223544);
        g.fill(colX + colW + 3, y, colX + colW + 4, y + hlH, hlFocused ? C_ACCENT() : 0xFF223544);
        g.drawString(f, "Headline", colX, y + 2, C_DIM(), false);
        g.drawString(f, headline != null ? headline : "", colX + 4, y + 14, 0xFFFFFFFF, false);

        y += hlH + 8;

        // Body text editor box
        int bodyH = VH - y - 30;
        boolean bodyFocused = !hlFocused;
        g.fill(colX - 4, y, colX + colW + 4, y + bodyH, bodyFocused ? 0xFF091612 : 0xBB0A0F0D);
        g.fill(colX - 4, y, colX + colW + 4, y + 1, bodyFocused ? C_GREEN() : 0xFF1B2B24);
        g.fill(colX - 4, y + bodyH - 1, colX + colW + 4, y + bodyH, bodyFocused ? C_GREEN() : 0xFF1B2B24);
        g.fill(colX - 4, y, colX - 3, y + bodyH, bodyFocused ? C_GREEN() : 0xFF1B2B24);
        g.fill(colX + colW + 3, y, colX + colW + 4, y + bodyH, bodyFocused ? C_GREEN() : 0xFF1B2B24);
        g.drawString(f, "Text", colX, y + 3, C_DIM(), false);
        if (bodyText != null) {
            int ty = y + 15;
            for (var line : f.split(net.minecraft.network.chat.Component.literal(bodyText), colW - 8)) {
                if (ty + f.lineHeight > y + bodyH - 4) break;
                g.drawString(f, line, colX + 4, ty, 0xFFFFFFFF, false);
                ty += f.lineHeight + 2;
            }
        }

        // Right panel — page list + items
        int rpx = previewW + 4;
        g.fill(previewW, TOP_H, VW, VH, C_BG());

        g.drawString(f, "Pages", rpx, TOP_H + 6, C_ACCENT(), false);
        String[] pages = { "Page 1", "Page 2", "Page 3" };
        for (int i = 0; i < pages.length; i++) {
            int py = TOP_H + 18 + i * 18;
            boolean sel = i == (tick / 80) % 3;
            g.fill(rpx - 2, py - 2, VW - 4, py + 12, sel ? C_BTN_HOV() : C_PANEL());
            if (sel) g.fill(rpx - 2, py - 2, rpx - 1, py + 12, C_ACCENT());
            g.drawString(f, pages[i], rpx + 2, py, sel ? C_ACCENT() : C_TEXT(), false);
        }

        // + Add Page button
        int addY = TOP_H + 18 + 3 * 18 + 4;
        g.fill(rpx - 2, addY, VW - 4, addY + 12, C_BTN());
        g.drawCenteredString(f, "+ Add Page", (rpx - 2 + VW - 4) / 2, addY + 2, C_ACCENT());

        // Divider
        g.fill(rpx - 2, addY + 18, VW - 4, addY + 19, 0x33FFFFFF);

        // Items label
        g.drawString(f, "Items", rpx, addY + 24, C_DIM(), false);
    }

    // ── Script editor replica (PhantasiaScriptEditorScreen) ──────────────────
    // Layout: top bar (22px) · 3D viewport · step row (42px) · timeline (22px)
    // No right panel — all editing controls are in the bottom strip.

    private static void drawScriptEditor(GuiGraphics g, Font f, PhantasiaTheme t,
                                         String machineName, int selectedStep,
                                         List<String> stepCaptions, int tick) {
        drawScriptEditor(g, f, t, machineName, selectedStep, stepCaptions, tick, false);
    }

    private static void drawScriptEditor(GuiGraphics g, Font f, PhantasiaTheme t,
                                         String machineName, int selectedStep,
                                         List<String> stepCaptions, int tick,
                                         boolean showCamPanel) {
        final int TOP_H   = 22;
        final int STEP_H  = 42;  // two sub-rows
        final int TL_H    = 22;
        final int BOT_H   = STEP_H + TL_H;
        int vpH = VH - TOP_H - BOT_H;

        // ── Top bar ───────────────────────────────────────────────────────────
        g.fill(0, 0, VW, TOP_H, C_BAR());
        g.fill(0, TOP_H - 1, VW, TOP_H, C_ACCENT());

        // Mode tabs
        int x = 6;
        String[] modeTabs = { "◈ Select", "⚠ Annotate", "◦ World" };
        for (String tab : modeTabs) {
            int tw = f.width(tab) + 12;
            g.fill(x, 3, x + tw, TOP_H - 3, C_BTN());
            g.drawString(f, tab, x + 6, (TOP_H - 8) / 2, C_DIM(), false);
            x += tw + 4;
        }
        x += 4;

        // Preview button
        int pvW = f.width("► Preview") + 10;
        g.fill(x, 3, x + pvW, TOP_H - 3, C_BTN());
        g.drawString(f, "► Preview", x + 5, (TOP_H - 8) / 2, C_DIM(), false);
        x += pvW + 4;

        // Camera tab (highlighted if open)
        int camTabW = f.width("🎥 Camera") + 10;
        g.fill(x, 3, x + camTabW, TOP_H - 3, showCamPanel ? C_BTN_ACT() : C_BTN());
        if (showCamPanel) g.fill(x, TOP_H - 3, x + camTabW, TOP_H - 2, C_ACCENT());
        g.drawString(f, "🎥 Camera", x + 5, (TOP_H - 8) / 2, showCamPanel ? C_ACCENT() : C_DIM(), false);
        x += camTabW + 4;

        // Start Cam tab
        int scW = f.width("⊙ Start Cam") + 10;
        g.fill(x, 3, x + scW, TOP_H - 3, C_BTN());
        g.drawString(f, "⊙ Start Cam", x + 5, (TOP_H - 8) / 2, C_DIM(), false);

        // Right side: back + save
        int rx = VW - 4;
        int saveW = f.width("💾 Save") + 10;
        rx -= saveW;
        g.fill(rx, 3, rx + saveW, TOP_H - 3, C_BTN());
        g.fill(rx, TOP_H - 3, rx + saveW, TOP_H - 2, C_GREEN());
        g.drawString(f, "💾 Save", rx + 5, (TOP_H - 8) / 2, C_GREEN(), false);
        rx -= 4;
        int backW = f.width("✕ Back") + 10;
        rx -= backW;
        g.fill(rx, 3, rx + backW, TOP_H - 3, C_BTN());
        g.drawString(f, "✕ Back", rx + 5, (TOP_H - 8) / 2, C_TEXT(), false);

        // ── 3D viewport ───────────────────────────────────────────────────────
        g.fill(0, TOP_H, VW, TOP_H + vpH, 0xFF07070F);

        // Isometric machine blocks
        int cx2 = VW / 2 - 28, cy2 = TOP_H + vpH / 2 - 18;
        float angle = (tick % 360) / 360f * 2f * (float) Math.PI;
        int rot = (int) (Math.cos(angle) * 6);
        for (int layer = 0; layer < 4; layer++) {
            for (int r = 0; r < 4; r++) {
                for (int c = 0; c < 4; c++) {
                    boolean shell = layer == 0 || layer == 3 || r == 0 || r == 3 || c == 0 || c == 3;
                    if (!shell) continue;
                    int bx2 = cx2 + c * 13 - r * 6 + rot;
                    int by2 = cy2 - layer * 7 + r * 7 - c * 3;
                    boolean hi = (layer == selectedStep % 4);
                    int col = hi ? 0xFF2A4870 : (0xFF1A2A3E + (layer * 6 + r + c) % 10 * 0x010101);
                    g.fill(bx2, by2, bx2 + 12, by2 + 12, col);
                    g.fill(bx2, by2, bx2 + 12, by2 + 1, hi ? 0x55FFFFFF : 0x22FFFFFF);
                    g.fill(bx2, by2, bx2 + 1, by2 + 12, hi ? 0x33FFFFFF : 0x11FFFFFF);
                }
            }
        }
        // Pulse highlight on active layer
        float pulse = 0.5f + 0.5f * (float) Math.sin(tick * 0.15f);
        int pa = (int) (35 * pulse);
        g.fill(cx2 - 4, cy2 - 4 - (selectedStep % 4) * 7,
               cx2 + 48 + rot, cy2 + 14 - (selectedStep % 4) * 7,
               (pa << 24) | (C_ACCENT() & 0xFFFFFF));

        // Camera panel floating overlay (top-right of viewport, 52px tall)
        if (showCamPanel) {
            int cpx = VW - 114, cpy = TOP_H + 4;
            g.fill(cpx, cpy, VW - 4, cpy + 52, 0xEE0B0B18);
            g.fill(cpx, cpy, VW - 4, cpy + 1, C_ACCENT());
            g.drawString(f, "🎥 Camera", cpx + 4, cpy + 4, C_ACCENT(), false);
            String[] camFields = { "Yaw   -135°", "Pitch  -30°", "Zoom   40.0", "Lerp   SPRING 25t" };
            for (int ci = 0; ci < camFields.length; ci++)
                g.drawString(f, camFields[ci], cpx + 4, cpy + 14 + ci * 9, C_TEXT(), false);
        }

        // ── Step row (STEP_H = 42, two sub-rows) ─────────────────────────────
        int botY = VH - BOT_H;
        g.fill(0, botY, VW, botY + STEP_H, C_BAR());
        g.fill(0, botY, VW, botY + 1, C_ACCENT());

        // Row 1: step counter + nav + tick + caption + Running
        int y1 = botY + 4;
        int bx3 = 8;

        // Step counter
        String stepLbl = (selectedStep + 1) + "/" + stepCaptions.size();
        g.drawString(f, "Step", bx3, y1 - 2, 0xFF334455, false);
        g.drawString(f, stepLbl, bx3, y1 + 6, C_ACCENT(), false);
        bx3 += f.width(stepLbl) + 10;

        // Nav buttons: + − Dup ◄ ►
        for (String nav : new String[]{ "+", "−", "Dup", "◄", "►" }) {
            int nw = f.width(nav) + 8;
            g.fill(bx3, y1, bx3 + nw, y1 + 14, C_BTN());
            g.drawCenteredString(f, nav, bx3 + nw / 2, y1 + 3, C_TEXT());
            bx3 += nw + 4;
        }
        bx3 += 4;

        // Tick field
        g.drawString(f, "Tick", bx3, y1 + 3, C_DIM(), false);
        bx3 += f.width("Tick") + 3;
        g.fill(bx3, y1, bx3 + 38, y1 + 13, 0xFF0A0A14);
        g.fill(bx3, y1, bx3 + 38, y1 + 1, 0xFF333355);
        int stepTick = selectedStep * 40;
        g.drawString(f, String.valueOf(stepTick), bx3 + 3, y1 + 3, C_TEXT(), false);
        bx3 += 44;

        // Caption button
        String cap = selectedStep < stepCaptions.size() ? stepCaptions.get(selectedStep) : "";
        int capW = Math.min(180, VW / 2 - bx3 - 10);
        g.fill(bx3, y1, bx3 + capW, y1 + 13, C_BTN());
        g.fill(bx3, y1, bx3 + capW, y1 + 1, 0x33FFFFFF);
        String capDisp = cap.isEmpty() ? "✎  Caption…" : f.plainSubstrByWidth(cap, capW - 16);
        g.drawString(f, capDisp, bx3 + 4, y1 + 3, cap.isEmpty() ? C_DIM() : C_TEXT(), false);
        bx3 += capW + 8;

        // Running toggle
        g.fill(bx3, y1, bx3 + 82, y1 + 14, C_BTN());
        g.drawString(f, "○ Running", bx3 + 5, y1 + 3, C_DIM(), false);

        // Row 2: Show mode tabs
        int y2 = botY + STEP_H / 2 + 5;
        int bx4 = 8;
        g.drawString(f, "Show:", bx4, y2 + 2, C_DIM(), false);
        bx4 += f.width("Show:") + 4;
        String[] showTabs = { "All", "Layer", "Range", "Parts…" };
        for (int si = 0; si < showTabs.length; si++) {
            int sw = f.width(showTabs[si]) + 10;
            boolean active = si == 0;
            g.fill(bx4, y2, bx4 + sw, y2 + 14, active ? C_BTN_ACT() : C_BTN());
            if (active) g.fill(bx4, y2 + 13, bx4 + sw, y2 + 14, C_ACCENT());
            g.drawString(f, showTabs[si], bx4 + 5, y2 + 3, active ? C_ACCENT() : C_TEXT(), false);
            bx4 += sw + 4;
        }

        // ── Timeline (TL_H = 22, very bottom) ────────────────────────────────
        int tlY = VH - TL_H;
        g.fill(0, tlY, VW, VH, C_PANEL());
        g.fill(0, tlY, VW, tlY + 1, 0x33FFFFFF);

        int margin = 30, trackW = VW - margin * 2;
        int midY = tlY + TL_H / 2;
        g.fill(margin, midY - 1, margin + trackW, midY + 1, 0xFF1A2C3C);
        // End caps
        g.fill(margin - 1, midY - 3, margin, midY + 3, 0xFF3A506A);
        g.fill(margin + trackW, midY - 3, margin + trackW + 1, midY + 3, 0xFF3A506A);

        // Step dots with numbers
        int nSteps = stepCaptions.size();
        for (int di = 0; di < nSteps; di++) {
            float frac = nSteps > 1 ? (float) di / (nSteps - 1) : 0f;
            int dotX = margin + (int) (frac * trackW);
            boolean sel = di == selectedStep;
            int ring = sel ? C_ACCENT() : 0xFF3A506A;
            g.fill(dotX - 7, midY - 7, dotX + 7, midY + 7, ring);
            g.fill(dotX - 5, midY - 5, dotX + 5, midY + 5, sel ? 0xFF1A3C5C : 0xFF0A1520);
            g.drawCenteredString(f, String.valueOf(di + 1), dotX, midY - 3,
                    sel ? C_ACCENT() : C_DIM());
        }
        // Preview playhead
        if (nSteps > 1) {
            float prog = (float) selectedStep / (nSteps - 1);
            int phX = margin + (int) (prog * trackW);
            g.fill(phX - 1, tlY + 2, phX + 1, tlY + TL_H - 2, 0xAAFFFFFF);
        }
    }

    // ── Scene viewer replica (PhantasiaSceneScreen) ───────────────────────────
    // Layout: viewport fills screen; right panel (168px full / 18px collapsed);
    // timeline bar at very bottom (26px); caption strip above it (38px overlay).

    private static void drawSceneViewer(GuiGraphics g, Font f, PhantasiaTheme t,
                                        String machineName, int tick) {
        drawSceneViewer(g, f, t, machineName, tick, false, 0, null);
    }

    private static void drawSceneViewer(GuiGraphics g, Font f, PhantasiaTheme t,
                                        String machineName, int tick,
                                        boolean panelExpanded, int activeStep,
                                        List<String> steps) {
        final int PANEL_W = panelExpanded ? 168 : 18;
        final int TL_H    = 26;   // timeline height
        final int CAP_H   = 38;   // caption strip height
        int vpW = VW - PANEL_W;

        // 3D viewport background
        g.fill(0, 0, vpW, VH, 0xFF07070F);

        // Fake machine blocks — isometric grid
        int cx = vpW / 2 - 28, cy = VH / 2 - 18;
        float angle = (tick % 360) / 360f * 2f * (float) Math.PI;
        int rot = (int) (Math.cos(angle) * 8);
        for (int layer = 0; layer < 4; layer++) {
            for (int r = 0; r < 4; r++) {
                for (int c = 0; c < 4; c++) {
                    boolean isShell = layer == 0 || layer == 3 || r == 0 || r == 3 || c == 0 || c == 3;
                    if (!isShell) continue;
                    int bx = cx + c * 13 - r * 6 + rot;
                    int by = cy - layer * 7 + r * 7 - c * 3;
                    boolean highlighted = (steps != null && layer == activeStep % 4);
                    int col = highlighted ? 0xFF2A4870 : (0xFF1A2A40 + (layer * 8 + r * 2 + c) % 12 * 0x010101);
                    g.fill(bx, by, bx + 12, by + 12, col);
                    g.fill(bx, by, bx + 12, by + 1, highlighted ? 0x66FFFFFF : 0x33FFFFFF);
                    g.fill(bx, by, bx + 1, by + 12, highlighted ? 0x44FFFFFF : 0x1AFFFFFF);
                }
            }
        }
        if (steps != null && activeStep < steps.size()) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(tick * 0.15f);
            int alpha = (int) (40 * pulse);
            g.fill(cx - 4, cy - 4, cx + 52 + rot, cy + 28, (alpha << 24) | (C_ACCENT() & 0xFFFFFF));
        }

        // ── Right side panel ──────────────────────────────────────────────────
        g.fill(vpW, 0, VW, VH, C_PANEL());
        g.fill(vpW, 0, vpW + 1, VH, C_ACCENT());

        // Collapse/expand button
        String arrow = panelExpanded ? "▶" : "◀";
        int cbx = VW - 18;
        g.fill(cbx, 0, VW, 18, C_BTN());
        g.drawString(f, arrow, cbx + 5, 5, C_DIM(), false);

        if (panelExpanded) {
            int py = 22;
            int bW = 20, lW = PANEL_W - 60, lX = vpW + 30;
            g.drawString(f, f.plainSubstrByWidth(machineName, PANEL_W - 20),
                    vpW + 8, py, C_ACCENT(), false);
            py += 18;
            g.fill(vpW + 4, py, VW - 4, py + 1, 0x33FFFFFF);
            py += 6;

            // Show: view filter tabs (2-column grid, mirrors real screen)
            g.drawString(f, "Show:", vpW + 8, py, C_DIM(), false);
            py += 12;
            int fw = (PANEL_W - 25) / 2;
            String[] vfs = { "All", "Layer", "Range", "Parts" };
            for (int i = 0; i < vfs.length; i++) {
                int bx = (i % 2 == 0) ? vpW + 10 : vpW + 15 + fw;
                boolean active = (i == 0);
                g.fill(bx, py, bx + fw, py + 14, active ? C_BTN_ACT() : C_BTN());
                if (active) g.fill(bx, py + 13, bx + fw, py + 14, C_ACCENT());
                g.drawString(f, vfs[i], bx + 5, py + 3, active ? C_ACCENT() : C_TEXT(), false);
                if (i % 2 != 0 || i == vfs.length - 1) py += 17;
            }
            py += 8;

            // Layer navigation
            g.drawString(f, "Layer:", vpW + 8, py + 4, C_DIM(), false);
            g.fill(vpW + 10, py + 14, vpW + 10 + bW, py + 28, C_BTN());
            g.drawCenteredString(f, "◀", vpW + 10 + bW / 2, py + 17, C_TEXT());
            g.drawCenteredString(f, "All", lX + lW / 2, py + 17, C_ACCENT());
            g.fill(lX + lW + 2, py + 14, lX + lW + 2 + bW, py + 28, C_BTN());
            g.drawCenteredString(f, "▶", lX + lW + 2 + bW / 2, py + 17, C_TEXT());
            py += 32;

            // Feature buttons (mirrors real panel order)
            String[] btns = { "🧱 Build Mode", "🗺 Footprint", "⊕ Center Camera", "🔍 Block List", "🧮 Materials" };
            for (String b : btns) {
                g.fill(vpW + 4, py, VW - 4, py + 13, C_BTN());
                g.drawString(f, b, vpW + 8, py + 3, C_TEXT(), false);
                py += 16;
            }
        }

        // ── Caption strip (overlay, above timeline) ───────────────────────────
        int capY = VH - TL_H - CAP_H;
        g.fill(0, capY, vpW, capY + CAP_H, 0xDD08080F);
        g.fill(0, capY, vpW, capY + 1, C_ACCENT());
        String caption = (steps != null && activeStep < steps.size())
                ? steps.get(activeStep)
                : "Heating Coils — Place GregTech coil blocks in the center ring.";
        g.drawCenteredString(f, caption, vpW / 2, capY + (CAP_H - f.lineHeight) / 2, 0xFFDDDDDD);

        // ── Timeline bar (very bottom) ────────────────────────────────────────
        int tlY = VH - TL_H;
        g.fill(0, tlY, vpW, VH, C_TL_BG());
        g.fill(0, tlY, vpW, tlY + 1, C_ACCENT());

        // Playback buttons
        g.fill(4, tlY + 4, 22, tlY + TL_H - 4, C_BTN());
        g.drawCenteredString(f, "⏸", 13, tlY + 8, C_TEXT());
        g.fill(26, tlY + 4, 44, tlY + TL_H - 4, C_BTN());
        g.drawCenteredString(f, "🔒", 35, tlY + 8, C_TEXT());
        g.fill(48, tlY + 4, 62, tlY + TL_H - 4, C_BTN());
        g.drawString(f, "1x", 51, tlY + 9, C_TEXT(), false);

        // Scrubber track
        int nSteps = steps != null ? steps.size() : 8;
        int tx = 68, tw = vpW - tx - 50, midY = tlY + TL_H / 2;
        g.fill(tx, midY - 1, tx + tw, midY + 1, 0xFF1A2C3C);
        int dotSp = nSteps > 1 ? tw / (nSteps - 1) : tw;
        float prog = steps != null ? (float) activeStep / Math.max(1, nSteps - 1) : (tick % 200) / 200f;
        g.fill(tx, midY - 1, tx + (int) (tw * prog), midY + 1, C_PROG());
        for (int di = 0; di < nSteps; di++) {
            int dx = tx + di * dotSp, dy = midY;
            boolean sel = steps != null && di == activeStep;
            g.fill(dx - 2, dy - 3, dx + 2, dy + 3, sel ? C_ACCENT() : C_BTN());
        }
        // Playhead
        int phX = tx + (int) (tw * prog);
        g.fill(phX - 2, midY - 4, phX + 2, midY + 4, C_ACCENT());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Tutorial sequences
    // ═════════════════════════════════════════════════════════════════════════

    private static final List<String> EBF_STEPS = List.of(
            "Introduction", "Foundation Layer", "Casing Walls",
            "Heating Coils", "Muffler & Maintenance", "Energy Hatch", "Item I/O", "Complete");

    // ── Getting Started ───────────────────────────────────────────────────────

    static TutorialSequence gettingStarted() {
        return new TutorialSequence(
                "getting_started", "Getting Started",
                "A quick overview of what Phantasia is and how to use it.",
                "minecraft:knowledge_book", TutorialSequence.PLAYER,
                List.of(
                        TutorialSlide.of("Welcome to Phantasia",
                                "Phantasia is an interactive guide system built into this modpack.\n\n" +
                                        "It provides 3D machine previews, step-by-step assembly walkthroughs,\n" +
                                        "and rich reference pages — all without leaving the game.")
                                .mock(mock((g, f, t, tick) -> drawSelectionScreen(g, f, t, tick, 0)))
                                .build(),

                        TutorialSlide.of("Hold [P] Near a Machine",
                                "Point at any multiblock machine in the world and hold the P key.\n" +
                                        "A progress bar appears at the bottom of your screen.\n\n" +
                                        "Keep holding until it fills — the viewer opens automatically.")
                                .mock(mock((g, f, t, tick) -> {
                                    // World scene with hold-bar overlay at bottom
                                    g.fill(0, 0, VW, VH, 0xFF060C14);

                                    // World sky gradient
                                    g.fillGradient(0, 0, VW, VH / 2, 0xFF1A2840, 0xFF0A1420);
                                    g.fill(0, VH / 2, VW, VH, 0xFF0A0A08);

                                    // EBF machine block in the world
                                    int bx = VW / 2 - 24, by = VH / 2 - 40;
                                    g.fill(bx, by, bx + 48, by + 48, 0xFF1E2C44);
                                    g.fill(bx, by, bx + 48, by + 1, 0x44FFFFFF);
                                    g.fill(bx, by, bx + 1, by + 48, 0x22FFFFFF);
                                    g.fill(bx + 4, by + 4, bx + 44, by + 44, 0xFF223355);
                                    String ebfLabel = "EBF";
                                    g.drawCenteredString(f, ebfLabel, bx + 24, by + 20, C_ACCENT());

                                    // Crosshair
                                    g.fill(VW / 2 - 4, VH / 2 - 1, VW / 2 + 4, VH / 2 + 1, 0x88FFFFFF);
                                    g.fill(VW / 2 - 1, VH / 2 - 4, VW / 2 + 1, VH / 2 + 4, 0x88FFFFFF);

                                    // Tooltip: machine name
                                    int ttW = f.width("Electric Blast Furnace") + 8;
                                    g.fill(VW / 2 - ttW / 2, VH / 2 + 30, VW / 2 + ttW / 2, VH / 2 + 42, 0xCC07070E);
                                    g.drawCenteredString(f, "Electric Blast Furnace", VW / 2, VH / 2 + 33, C_ACCENT());

                                    // Hold-to-phantasize bar (bottom of screen, like real keybind overlay)
                                    float pct = ((tick % 100) / 100f);
                                    int barW = 200, barH = 28;
                                    int barX = (VW - barW) / 2, barY = VH - 44;
                                    g.fill(barX, barY, barX + barW, barY + barH, 0xCC0A0A14);
                                    g.fill(barX, barY, barX + barW, barY + 1, C_ACCENT());
                                    g.drawString(f, "[P] Hold to Phantasize", barX + 8, barY + 5, C_TEXT(), false);
                                    // Progress bar
                                    g.fill(barX + 8, barY + 18, barX + barW - 8, barY + 24, 0x33FFFFFF);
                                    g.fill(barX + 8, barY + 18, barX + 8 + (int) ((barW - 16) * pct), barY + 24,
                                            C_PROG());
                                }))
                                .cursor(0.5f, 0.47f, 20, 80, false)
                                .highlight(0.292f, 0.853f, 0.417f, 0.093f, "Hold progress bar")
                                .build(),

                        TutorialSlide.of("Browse Everything with /phantasia",
                                "Type /phantasia to open a searchable list of every multiblock,\n" +
                                        "scene layout, and guide in this pack.\n\n" +
                                        "Use it any time — you don't need to be near a machine.\n" +
                                        "The Tutorials tab is also right here.")
                                .mock(mock((g, f, t, tick) -> {
                                    int tab = (tick / 80) % 4;
                                    drawSelectionScreen(g, f, t, tick, tab);
                                }))
                                .cursor(0.238f, 0.133f, 20, 40, true)
                                .cursor(0.371f, 0.133f, 15, 40, true)
                                .cursor(0.485f, 0.133f, 15, 40, true)
                                .cursor(0.615f, 0.133f, 15, 40, true)
                                .highlight(0.158f, 0.107f, 0.525f, 0.053f, "Tab bar")
                                .build(),

                        TutorialSlide.of("You're Ready!",
                                "That's all you need to know as a player.\n\n" +
                                        "Open the other tabs in Tutorials for deeper dives into\n" +
                                        "guides, scripts, and scenes.\n" +
                                        "Pack authors can check the dev tutorials for more.")
                                .mock(mock((g, f, t, tick) -> {
                                    drawSelectionScreen(g, f, t, tick, 3);
                                }))
                                .build()));
    }

    // ── Understanding Guides ──────────────────────────────────────────────────

    static TutorialSequence guides() {
        return new TutorialSequence(
                "guides", "Understanding Guides",
                "Learn what guides are and how to read them.",
                "minecraft:book", TutorialSequence.PLAYER,
                List.of(
                        TutorialSlide.of("What is a Guide?",
                                "A guide is a text-and-item reference document — like a recipe card\n" +
                                        "or lore page. It can have multiple pages you scroll through.\n\n" +
                                        "Find guides in /phantasia under the Guides tab.")
                                .mock(mock((g, f, t, tick) -> drawSelectionScreen(g, f, t, tick, 2)))
                                .cursor(0.485f, 0.130f, 20, 40, true)
                                .highlight(0.433f, 0.107f, 0.104f, 0.053f, "Guides tab")
                                .build(),

                        TutorialSlide.of("Reading a Guide",
                                "Each page has an optional headline, body text, and an item grid.\n\n" +
                                        "The item grid shows ingredients, outputs, or catalysts.\n" +
                                        "Use the navigation buttons at the bottom to flip pages.")
                                .mock(mock((g, f, t, tick) -> drawGuideScreen(g, f, t,
                                        "Ore Processing Guide",
                                        "What Goes In?",
                                        "Raw iron ore enters the Electric Blast Furnace\nalong with a limestone flux to produce iron ingots.\n\nThe EBF requires a heating coil tier of at least\nCupronickel for basic iron smelting.",
                                        0, 4)))
                                .cursor(0.62f, 0.95f, 25, 40, true)
                                .highlight(0.0f, 0.90f, 1.0f, 0.10f, "Navigation bar")
                                .build(),

                        TutorialSlide.of("Pages and Navigation",
                                "Use the Next/Prev buttons at the bottom of the guide or press\n" +
                                        "the arrow keys to navigate between pages.\n\n" +
                                        "Press ESC to close and return to the selection screen.")
                                .mock(mock((g, f, t, tick) -> {
                                    int page = (tick / 80) % 4;
                                    String[] headlines = { "What Goes In?", "Energy Requirements", "Output Products",
                                            "Advanced Tips" };
                                    String[] bodies = {
                                            "Raw iron ore + limestone flux in the EBF.",
                                            "Requires at least 128 EU/t at LV tier.",
                                            "2x Iron Ingots per ore. Scale up with coil tier.",
                                            "Use magnetic coils for Steel and higher metals."
                                    };
                                    drawGuideScreen(g, f, t, "Ore Processing Guide",
                                            headlines[page], bodies[page], page, 4);
                                }))
                                .cursor(0.62f, 0.95f, 20, 50, true)
                                .cursor(0.62f, 0.95f, 0, 60, false)
                                .cursor(0.62f, 0.95f, 10, 50, true)
                                .cursor(0.62f, 0.95f, 0, 60, false)
                                .build(),

                        TutorialSlide.of("Cross-Links",
                                "Guide pages can link to other guides, to scene layouts, or to\n" +
                                        "machine scripts. Clicking a link opens that content directly.\n\n" +
                                        "This lets pack authors build a flowing documentation flow\n" +
                                        "from overview → details → assembly.")
                                .mock(mock((g, f, t, tick) -> drawGuideScreen(g, f, t,
                                        "EBF Basics",
                                        "Next Steps",
                                        "Now that you understand the inputs and outputs,\nyou're ready to build the machine.\n\n" +
                                                "Continue Reading →\n► View Automated Script →",
                                        3, 4)))
                                .cursor(0.46f, 0.65f, 20, 30, true)
                                .cursor(0.46f, 0.73f, 15, 30, true)
                                .highlight(0.12f, 0.62f, 0.76f, 0.16f, "Cross-link buttons")
                                .build()));
    }

    // ── Understanding Scripts ─────────────────────────────────────────────────

    static TutorialSequence scripts() {
        return new TutorialSequence(
                "scripts", "Understanding Scripts",
                "Learn how step-by-step machine walkthroughs work.",
                "minecraft:writable_book", TutorialSequence.PLAYER,
                List.of(
                        TutorialSlide.of("What is a Script?",
                                "A script is a step-by-step 3D walkthrough for a single multiblock.\n\n" +
                                        "It loads the machine into a virtual world preview, then walks you\n" +
                                        "through each layer with camera movements and instructions.\n\n" +
                                        "Open one from /phantasia → Multiblocks.")
                                .mock(mock((g, f, t, tick) ->
                                        drawSceneViewer(g, f, t, "Electric Blast Furnace", tick, false, 0, EBF_STEPS)))
                                .highlight(0.0f, 0.0f, 0.963f, 0.787f, "3D viewport")
                                .highlight(0.0f, 0.787f, 0.963f, 0.213f, "Caption + timeline")
                                .build(),

                        TutorialSlide.of("Following the Steps",
                                "Each step focuses the camera on a specific part of the machine.\n\n" +
                                        "The caption strip above the timeline shows the current instruction.\n" +
                                        "Use the scrubber or play/pause to move through the walkthrough.\n" +
                                        "The 3D preview updates to show only the relevant blocks.")
                                .mock(mock((g, f, t, tick) -> {
                                    int step = (tick / 60) % EBF_STEPS.size();
                                    drawSceneViewer(g, f, t, "Electric Blast Furnace", tick, false, step, EBF_STEPS);
                                }))
                                .highlight(0.0f, 0.787f, 0.963f, 0.127f, "Caption strip")
                                .highlight(0.0f, 0.913f, 0.963f, 0.087f, "Timeline scrubber")
                                .build(),

                        TutorialSlide.of("Variants",
                                "The right panel holds machine-specific options.\n\n" +
                                        "Click ◀ to expand it — you'll find Show/Layer/Build controls\n" +
                                        "and extra tools. Your layer and build-order state persists.")
                                .mock(mock((g, f, t, tick) ->
                                        drawSceneViewer(g, f, t, "Electric Blast Furnace", tick, true, 0, EBF_STEPS)))
                                .highlight(0.65f, 0.0f, 0.35f, 1.0f, "Right panel")
                                .build()));
    }

    // ── Understanding Scenes ──────────────────────────────────────────────────

    static TutorialSequence scenes() {
        return new TutorialSequence(
                "scenes", "Understanding Scenes",
                "Learn how multi-machine scene layouts work.",
                "minecraft:filled_map", TutorialSequence.PLAYER,
                List.of(
                        TutorialSlide.of("What is a Scene?",
                                "A scene places multiple machines together at offsets from a\n" +
                                        "shared origin — like a factory floor layout.\n\n" +
                                        "Each machine is controlled independently but shown together\n" +
                                        "in the same 3D preview world.")
                                .mock(mock((g, f, t, tick) -> drawSceneViewer(g, f, t, "Ore Processing Line", tick)))
                                .build(),

                        TutorialSlide.of("Scene Steps",
                                "Scene steps walk you through the full multi-machine layout.\n\n" +
                                        "Each step can show specific machines and hide others so you\n" +
                                        "focus on one part of the production chain at a time.")
                                .mock(mock((g, f, t, tick) -> {
                                    int step = (tick / 70) % 6;
                                    List<String> sceneSteps = List.of("Overview", "EBF Setup", "Chem Reactor",
                                            "Macerators", "Power I/O", "Finished");
                                    drawSceneViewer(g, f, t, "Ore Processing Line", tick, false, step, sceneSteps);
                                }))
                                .highlight(0.0f, 0.787f, 0.963f, 0.127f, "Caption — current step")
                                .highlight(0.0f, 0.913f, 0.963f, 0.087f, "Timeline scrubber")
                                .build(),

                        TutorialSlide.of("Mistakes",
                                "Scenes can include layout validation hints called Mistakes.\n\n" +
                                        "Each mistake has a severity (Info / Warning / Error) and appears\n" +
                                        "in the guide view for the scene — helping players avoid common\n" +
                                        "assembly errors before they make them.")
                                .mock(mock((g, f, t, tick) -> drawGuideScreen(g, f, t,
                                        "Ore Processing Line",
                                        "Layout Notes",
                                        "ℹ Optimal spacing between EBF and Chem Reactor: 4 blocks\n" +
                                                "⚠ Coolant pipe required on Chemical Reactor south face\n" +
                                                "✖ Power distribution overflow if EBF and Turbine share bus",
                                        2, 5)))
                                .highlight(0.0f, 0.45f, 1.0f, 0.35f, "Mistake banners")
                                .build()));
    }

    // ── Dev: Creating Guides ──────────────────────────────────────────────────

    static TutorialSequence devGuides() {
        return new TutorialSequence(
                "dev_guides", "Creating Guides",
                "How to write and publish your own guides.",
                "minecraft:knowledge_book", TutorialSequence.DEV,
                List.of(
                        TutorialSlide.of("Opening the Guide Editor",
                                "Go to /phantasia → Guides tab → click the '+ New Guide' card.\n\n" +
                                        "The editor opens with a blank guide ready to fill in.\n" +
                                        "Give it a unique ID (e.g. yourmod:my_guide), a title, and an icon.")
                                .mock(mock((g, f, t, tick) -> drawSelectionScreen(g, f, t, tick, 2)))
                                .cursor(0.485f, 0.130f, 20, 50, true)
                                .cursor(0.267f, 0.397f, 20, 40, true)
                                .highlight(0.433f, 0.107f, 0.104f, 0.055f, "Guides tab")
                                .highlight(0.158f, 0.253f, 0.68f, 0.287f, "Guide cards area")
                                .build(),

                        TutorialSlide.of("The Guide Editor",
                                "The left panel is your writing area.\n\n" +
                                        "Type a Headline (shown large at the top of the page) and\n" +
                                        "Body Text below it. Pages are listed in the right panel.\n" +
                                        "Click '+ Add Page' to add more pages.")
                                .mock(mock((g, f, t, tick) -> drawGuideEditor(g, f, t,
                                        "Ore Processing Guide",
                                        "What Goes In?",
                                        "Raw iron ore enters the Electric Blast Furnace\nalong with a limestone flux.\n\nCoil tier must be at least Cupronickel.",
                                        tick)))
                                .cursor(0.27f, 0.167f, 20, 40, true)
                                .cursor(0.27f, 0.57f, 15, 40, true)
                                .highlight(0.042f, 0.113f, 0.46f, 0.107f, "Headline editor")
                                .highlight(0.042f, 0.247f, 0.46f, 0.65f, "Body text editor")
                                .build(),

                        TutorialSlide.of("Managing Pages",
                                "Click a page in the right panel list to select and edit it.\n" +
                                        "Click '+ Add Page' to create a new blank page.\n\n" +
                                        "Use Ctrl+S or the Save button to write the guide to disk.\n" +
                                        "It immediately appears in /phantasia for all players.")
                                .mock(mock((g, f, t, tick) -> drawGuideEditor(g, f, t,
                                        "Ore Processing Guide",
                                        "Energy Requirements",
                                        "The EBF requires at least 128 EU/t at LV tier.\nHigher coil tiers unlock hotter temperatures\nfor producing Steel, Aluminium, and beyond.",
                                        tick)))
                                .cursor(0.62f, 0.147f, 20, 30, true)
                                .cursor(0.62f, 0.213f, 15, 30, true)
                                .cursor(0.92f, 0.037f, 20, 40, true)
                                .highlight(0.54f, 0.090f, 0.46f, 0.24f, "Page list + Add Page")
                                .build(),

                        TutorialSlide.of("Preview and Save",
                                "Click '▶ Preview' to open the guide reader and see exactly\n" +
                                        "what players will see — including cross-links and item cards.\n\n" +
                                        "Click '💾 Save' to write to disk immediately.\n" +
                                        "The guide reloads for all players without a restart.")
                                .mock(mock((g, f, t, tick) -> {
                                    boolean inPreview = (tick / 80) % 2 == 1;
                                    if (inPreview) {
                                        drawGuideScreen(g, f, t, "Ore Processing Guide",
                                                "What Goes In?",
                                                "Raw iron ore + limestone flux in the EBF.\nCoil tier: at least Cupronickel.",
                                                0, 3);
                                    } else {
                                        drawGuideEditor(g, f, t, "Ore Processing Guide",
                                                "What Goes In?",
                                                "Raw iron ore + limestone flux in the EBF.\nCoil tier: at least Cupronickel.",
                                                tick);
                                    }
                                }))
                                .cursor(0.78f, 0.037f, 20, 50, true)
                                .cursor(0.46f, 0.5f, 20, 60, false)
                                .cursor(0.93f, 0.037f, 20, 50, true)
                                .highlight(0.69f, 0.01f, 0.30f, 0.067f, "Preview & Save")
                                .build()));
    }

    // ── Dev: Writing Scripts ──────────────────────────────────────────────────

    static TutorialSequence devScripts() {
        return new TutorialSequence(
                "dev_scripts", "Writing Scripts",
                "How to write step-by-step machine walkthroughs.",
                "minecraft:writable_book", TutorialSequence.DEV,
                List.of(
                        TutorialSlide.of("Opening the Script Editor",
                                "Go to /phantasia → Multiblocks tab, find your machine (e.g.\n" +
                                        "Electric Blast Furnace), and click to open the viewer.\n\n" +
                                        "The script editor icon is in the right panel. A blank script\n" +
                                        "is created for you if none exists yet.")
                                .mock(mock((g, f, t, tick) -> drawSelectionScreen(g, f, t, tick, 0)))
                                .cursor(0.267f, 0.397f, 25, 50, true)
                                .highlight(0.158f, 0.253f, 0.217f, 0.287f, "Open EBF card")
                                .build(),

                        TutorialSlide.of("Adding Steps",
                                "Click '+ Add Step' at the bottom of the step list.\n\n" +
                                        "Each step has a caption (the text shown to the player),\n" +
                                        "a show mode (All / Layer / Range / Parts), and an optional\n" +
                                        "camera animation. The EBF script uses one step per layer.")
                                .mock(mock((g, f, t, tick) -> drawScriptEditor(g, f, t, "Electric Blast Furnace",
                                        (tick / 50) % EBF_STEPS.size(), EBF_STEPS, tick)))
                                .cursor(0.094f, 0.820f, 25, 40, true)
                                .cursor(0.50f, 0.43f, 20, 40, false)
                                .highlight(0.0f, 0.787f, 0.55f, 0.070f, "+ step controls row")
                                .build(),

                        TutorialSlide.of("Setting the Camera",
                                "Click 🎥 Camera in the top bar to open the camera panel.\n\n" +
                                        "Set per-step yaw, pitch, zoom, and lerp type here.\n" +
                                        "SPRING easing gives natural movement between steps;\n" +
                                        "20–30 lerp ticks is a good default.")
                                .mock(mock((g, f, t, tick) -> drawScriptEditor(g, f, t, "Electric Blast Furnace", 2,
                                        EBF_STEPS, tick, true)))
                                .cursor(0.62f, 0.037f, 20, 40, true)
                                .highlight(0.762f, 0.087f, 0.229f, 0.173f, "Camera panel")
                                .build(),

                        TutorialSlide.of("Save and Test",
                                "Click 'Save Script' (bottom-right of editor) to write to disk.\n\n" +
                                        "Then press [P] while looking at the real machine in the world\n" +
                                        "to run your script live — no restart needed.\n\n" +
                                        "Iterate fast: save → test in world → come back and tweak.")
                                .mock(mock((g, f, t, tick) -> {
                                    int step = (tick / 40) % EBF_STEPS.size();
                                    drawScriptEditor(g, f, t, "Electric Blast Furnace", step, EBF_STEPS, tick);
                                }))
                                .cursor(0.88f, 0.037f, 20, 40, true)
                                .highlight(0.84f, 0.01f, 0.16f, 0.063f, "💾 Save")
                                .build()));
    }
}
