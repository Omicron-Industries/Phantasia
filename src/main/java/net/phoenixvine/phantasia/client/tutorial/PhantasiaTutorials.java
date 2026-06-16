package net.phoenixvine.phantasia.client.tutorial;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
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
            devScripts()
    );

    // ── Scale helper ──────────────────────────────────────────────────────────

    @FunctionalInterface private interface DrawTask { void run(GuiGraphics g, Font f, PhantasiaTheme t, int tick); }

    private static TutorialSlide.MockRenderer mock(DrawTask task) {
        return (g, mx, my, mw, mh, tick) -> {
            Font f = Minecraft.getInstance().font;
            PhantasiaTheme t = PhantasiaTheme.current();
            float s = Math.min(mw / (float) VW, mh / (float) VH);
            int ox = mx + (mw - (int)(VW * s)) / 2;
            int oy = my + (mh - (int)(VH * s)) / 2;
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
    private static final int SEL_CARD_W   = 104;
    private static final int SEL_CARD_H   = 86;
    private static final int SEL_CARD_PAD = 8;
    private static final int SEL_GRID_W   = 3 * SEL_CARD_W + 2 * SEL_CARD_PAD; // 328

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
        String[] tabLabels = {"Multiblocks", "Scenes", "Guides", "Tutorials"};
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
        g.fill(gridX, searchY,     gridX + SEL_GRID_W, searchY + 1,  0xFF333355);
        g.fill(gridX, searchY + 15, gridX + SEL_GRID_W, searchY + 16, 0xFF333355);
        g.fill(gridX, searchY,     gridX + 1,           searchY + 16, 0xFF333355);
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
        String[] fallbackNames  = {"Electric Blast Furnace", "Chemical Reactor", "Macerator",
                                   "Large Boiler", "Pyrolyse Oven", "Large Turbine"};
        boolean[] fallbackSteps = {true, true, false, true, false, false};

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
                name = (i < machines.size())
                        ? machines.get(i).getId().getPath().replace('_', ' ')
                        : fallbackNames[i];
            }
            if (f.width(name) > SEL_CARD_W - 8)
                name = f.plainSubstrByWidth(name, SEL_CARD_W - 8 - f.width("...")) + "...";
            g.drawString(f, name, cx + 4, cy + SEL_CARD_H - 22, C_TEXT(), false);

            // Green dot + step count
            boolean hasScript = (i < machines.size())
                    ? PhantasiaScripts.has(machines.get(i))
                    : fallbackSteps[i];
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
        String[] fallbackTitles = {"Getting Started", "Ore Processing", "Power Setup",
                                   "EBF Basics", "Recipe Tips", "Material Guide"};
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
        String[][] playerTuts = {{"Getting Started", "Overview"}, {"Understanding Guides", "Guides"}, {"Understanding Scripts", "Scripts"}};
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
            y += (int)(f.lineHeight * 1.5f) + 6;
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
        String[] pages = {"Page 1", "Page 2", "Page 3"};
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
    // Viewport + STEP_ROW_H(42)+TIMELINE_H(22)=64 bottom + right panel (168 or 18)

    private static void drawScriptEditor(GuiGraphics g, Font f, PhantasiaTheme t,
                                         String machineName, int selectedStep,
                                         List<String> stepCaptions, int tick) {
        int TOP_H = 22;
        int BOTTOM_H = 64; // STEP_ROW_H(42) + TIMELINE_H(22)
        int RIGHT_W = 150;
        int vpW = VW - RIGHT_W;
        int vpH = VH - TOP_H - BOTTOM_H;

        // Top bar
        g.fill(0, 0, VW, TOP_H, C_BAR());
        g.fill(0, TOP_H - 1, VW, TOP_H, C_ACCENT());
        // Back button
        int backW = f.width("← Back") + 10;
        g.fill(4, 3, 4 + backW, TOP_H - 3, C_BTN());
        g.drawString(f, "← Back", 9, (TOP_H - 8) / 2, C_TEXT(), false);
        g.drawString(f, machineName, 4 + backW + 10, (TOP_H - 8) / 2, C_ACCENT(), false);

        // 3D viewport
        g.fill(0, TOP_H, vpW, TOP_H + vpH, 0xFF06060F);

        // Fake isometric machine blocks (EBF cross-section) — 3 layers visible
        int bx = vpW / 2 - 30, by = TOP_H + vpH / 2 - 20;
        for (int brow = 0; brow < 3; brow++) {
            for (int bcol = 0; bcol < 3; bcol++) {
                int blx = bx + bcol * 16 - brow * 8;
                int bly = by + brow * 9 - bcol * 5;
                // Highlight the current layer faintly
                int blockColor = (brow == selectedStep % 3) ? 0xFF3A5080 : 0xFF1E2C44;
                g.fill(blx, bly, blx + 15, bly + 15, blockColor);
                g.fill(blx, bly, blx + 15, bly + 1, 0x55FFFFFF);
                g.fill(blx, bly, blx + 1, bly + 15, 0x33FFFFFF);
            }
        }
        // Highlight pulsing selection on active blocks
        float pulse = 0.5f + 0.5f * (float) Math.sin(tick * 0.12f);
        int highlightAlpha = (int)(80 * pulse);
        int selBx = bx - (selectedStep % 3) * 8 + 8;
        int selBy = by + (selectedStep % 3) * 9;
        for (int bcol = 0; bcol < 3; bcol++) {
            int blx = selBx + bcol * 16;
            int bly = selBy - bcol * 5;
            g.fill(blx, bly, blx + 15, bly + 15, (highlightAlpha << 24) | (C_ACCENT() & 0xFFFFFF));
        }

        // Camera panel overlay (CAM_PANEL_H=52, floating in viewport)
        int camPanX = vpW - 110, camPanY = TOP_H + 4;
        g.fill(camPanX, camPanY, vpW - 4, camPanY + 52, 0xCC0B0B18);
        g.fill(camPanX, camPanY, vpW - 4, camPanY + 1, C_ACCENT());
        g.drawString(f, "Camera", camPanX + 4, camPanY + 4, C_DIM(), false);
        String[] camFields = {"Yaw: -135°", "Pitch: -30°", "Zoom: 40.0"};
        for (int ci = 0; ci < 3; ci++) {
            g.drawString(f, camFields[ci], camPanX + 4, camPanY + 14 + ci * 11, C_TEXT(), false);
        }

        // Right panel — step detail
        g.fill(vpW, TOP_H, VW, VH - BOTTOM_H, 0xFF0C0C1A);
        g.fill(vpW, TOP_H, vpW + 1, VH - BOTTOM_H, C_ACCENT());

        g.drawString(f, "Steps", vpW + 6, TOP_H + 6, C_ACCENT(), false);
        for (int si = 0; si < stepCaptions.size() && si < 7; si++) {
            int sY = TOP_H + 18 + si * 18;
            boolean sel = si == selectedStep;
            g.fill(vpW + 2, sY, VW - 2, sY + 15, sel ? C_BTN_HOV() : C_PANEL());
            if (sel) g.fill(vpW + 2, sY, vpW + 3, sY + 15, C_ACCENT());
            String cap = stepCaptions.get(si);
            if (f.width(cap) > RIGHT_W - 14) cap = f.plainSubstrByWidth(cap, RIGHT_W - 14 - f.width("...")) + "...";
            g.drawString(f, cap, vpW + 6, sY + 3, sel ? C_ACCENT() : C_TEXT(), false);
        }

        // + Add Step button at bottom of right panel
        int addBtnY = VH - BOTTOM_H - 18;
        g.fill(vpW + 2, addBtnY, VW - 2, addBtnY + 14, C_BTN());
        g.drawCenteredString(f, "+ Add Step", (vpW + 2 + VW - 2) / 2, addBtnY + 3, C_ACCENT());

        // Bottom panel background
        int botY = VH - BOTTOM_H;
        g.fill(0, botY, VW, VH, 0xFF0A0A14);
        g.fill(0, botY, VW, botY + 1, 0x44FFFFFF);

        // Step row (caption + info row)
        if (!stepCaptions.isEmpty() && selectedStep < stepCaptions.size()) {
            g.drawString(f, "Step " + (selectedStep + 1) + ": " + stepCaptions.get(selectedStep),
                    8, botY + 6, C_ACCENT(), false);
            g.drawString(f, "Show: ALL  |  Lerp: SPRING (25t)", 8, botY + 18, C_DIM(), false);
        }
        // Save/filter row
        int saveBtnW = f.width("Save Script") + 10;
        g.fill(VW - 4 - saveBtnW, botY + 4, VW - 4, botY + 18, C_BTN());
        g.drawString(f, "Save Script", VW - 4 - saveBtnW + 5, botY + 8, C_TEXT(), false);

        // Timeline (TIMELINE_H=22 at very bottom)
        int tlY = botY + 42;
        g.fill(0, tlY, VW, VH, C_TL_BG());
        g.fill(0, tlY, VW, tlY + 1, 0x44FFFFFF);

        // Timeline dots
        int dotSpacing = (VW - 20) / Math.max(stepCaptions.size(), 1);
        for (int di = 0; di < stepCaptions.size(); di++) {
            int dx = 10 + di * dotSpacing;
            int dy = tlY + 11;
            boolean selDot = di == selectedStep;
            g.fill(dx - 3, dy - 3, dx + 3, dy + 3, selDot ? C_ACCENT() : C_BTN());
            if (selDot) g.fill(dx - 1, dy - 1, dx + 1, dy + 1, 0xFFFFFFFF);
        }
        // Timeline connecting line
        g.fill(10, tlY + 10, 10 + (stepCaptions.size() - 1) * dotSpacing, tlY + 12, 0x44FFFFFF);
    }

    // ── Scene viewer replica (PhantasiaSceneScreen) ───────────────────────────
    // Full-screen 3D, right panel collapsed (18px wide), bottom thin timeline

    private static void drawSceneViewer(GuiGraphics g, Font f, PhantasiaTheme t,
                                        String machineName, int tick) {
        // Full viewport
        g.fill(0, 0, VW, VH, 0xFF080815);

        // Fake isometric EBF — 5×5×5 blocks rotating slowly
        int cx = VW / 2 - 30, cy = VH / 2 - 20;
        float angle = (tick % 240) / 240f * 2f * (float) Math.PI;
        int cosA = (int)(Math.cos(angle) * 10);
        for (int layer = 0; layer < 4; layer++) {
            for (int br = 0; br < 4; br++) {
                for (int bc = 0; bc < 4; bc++) {
                    // Skip inner air for EBF shape
                    if (layer > 0 && layer < 3 && br > 0 && br < 3 && bc > 0 && bc < 3) continue;
                    int blx = cx + bc * 14 - br * 7 + cosA;
                    int bly = cy - layer * 8 + br * 8 - bc * 4;
                    int depth = layer * 20 + br * 5 + bc;
                    int col = 0xFF1A2C44 + (depth & 0x0F) * 0x010101;
                    g.fill(blx, bly, blx + 13, bly + 13, col);
                    g.fill(blx, bly, blx + 13, bly + 1, 0x44FFFFFF);
                    g.fill(blx, bly, blx + 1, bly + 13, 0x22FFFFFF);
                }
            }
        }

        // Collapsed right panel (COLLAPSED_PANEL_W=18)
        g.fill(VW - 18, 0, VW, VH, 0xCC0A0A14);
        g.fill(VW - 18, 0, VW - 17, VH, C_ACCENT());
        // Expand arrow
        g.drawString(f, "◄", VW - 13, VH / 2 - 4, C_DIM(), false);

        // Caption strip at bottom (CAPTION_STRIP_H=38)
        int capY = VH - 38;
        g.fill(0, capY, VW - 18, VH, 0xCC080810);
        g.fill(0, capY, VW - 18, capY + 1, 0x33FFFFFF);
        g.drawString(f, machineName, 10, capY + 6, C_ACCENT(), false);
        g.drawString(f, "Heating Coils — Place GregTech coil blocks in the center ring.", 10, capY + 18, C_TEXT(), false);

        // Bottom timeline (thin, inside caption strip)
        int tlY = VH - 10;
        g.fill(0, tlY, VW - 18, VH, C_TL_BG());
        g.fill(0, tlY, VW - 18, tlY + 1, 0x44FFFFFF);
        int nDots = 8;
        int dotSp = (VW - 30) / nDots;
        int activeDot = (tick / 60) % nDots;
        g.fill(10, tlY + 4, 10 + (nDots - 1) * dotSp, tlY + 6, 0x44FFFFFF);
        for (int di = 0; di < nDots; di++) {
            int dx = 10 + di * dotSp, dy = tlY + 5;
            g.fill(dx - 2, dy - 2, dx + 2, dy + 2, di == activeDot ? C_ACCENT() : C_BTN());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Tutorial sequences
    // ═════════════════════════════════════════════════════════════════════════

    private static final List<String> EBF_STEPS = List.of(
            "Introduction", "Foundation Layer", "Casing Walls",
            "Heating Coils", "Muffler & Maintenance", "Energy Hatch", "Item I/O", "Complete"
    );

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
                                .mock(mock((g, f, t, tick) ->
                                        drawSelectionScreen(g, f, t, tick, 0)))
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
                                    g.fill(barX + 8, barY + 18, barX + 8 + (int)((barW - 16) * pct), barY + 24, C_PROG());
                                }))
                                .cursor(0.5f, 0.4f, 20, 80, false)
                                .highlight(0.14f, 0.76f, 0.72f, 0.18f, "Hold progress bar")
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
                                .cursor(0.22f, 0.14f, 20, 40, true)
                                .cursor(0.39f, 0.14f, 15, 40, true)
                                .cursor(0.56f, 0.14f, 15, 40, true)
                                .cursor(0.73f, 0.14f, 15, 40, true)
                                .highlight(0.16f, 0.10f, 0.70f, 0.09f, "Tab bar")
                                .build(),

                        TutorialSlide.of("You're Ready!",
                                "That's all you need to know as a player.\n\n" +
                                "Open the other tabs in Tutorials for deeper dives into\n" +
                                "guides, scripts, and scenes.\n" +
                                "Pack authors can check the dev tutorials for more.")
                                .mock(mock((g, f, t, tick) -> {
                                    drawSelectionScreen(g, f, t, tick, 3);
                                }))
                                .build()
                )
        );
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
                                .mock(mock((g, f, t, tick) ->
                                        drawSelectionScreen(g, f, t, tick, 2)))
                                .highlight(0.56f, 0.10f, 0.20f, 0.09f, "Guides tab")
                                .build(),

                        TutorialSlide.of("Reading a Guide",
                                "Each page has an optional headline, body text, and an item grid.\n\n" +
                                "The item grid shows ingredients, outputs, or catalysts.\n" +
                                "Use the navigation buttons at the bottom to flip pages.")
                                .mock(mock((g, f, t, tick) ->
                                        drawGuideScreen(g, f, t,
                                                "Ore Processing Guide",
                                                "What Goes In?",
                                                "Raw iron ore enters the Electric Blast Furnace\nalong with a limestone flux to produce iron ingots.\n\nThe EBF requires a heating coil tier of at least\nCupronickel for basic iron smelting.",
                                                0, 4)))
                                .cursor(0.77f, 0.93f, 25, 40, true)
                                .highlight(0.0f, 0.86f, 1.0f, 0.14f, "Navigation bar")
                                .build(),

                        TutorialSlide.of("Pages and Navigation",
                                "Use the Next/Prev buttons at the bottom of the guide or press\n" +
                                "the arrow keys to navigate between pages.\n\n" +
                                "Press ESC to close and return to the selection screen.")
                                .mock(mock((g, f, t, tick) -> {
                                    int page = (tick / 80) % 4;
                                    String[] headlines = {"What Goes In?", "Energy Requirements", "Output Products", "Advanced Tips"};
                                    String[] bodies = {
                                        "Raw iron ore + limestone flux in the EBF.",
                                        "Requires at least 128 EU/t at LV tier.",
                                        "2x Iron Ingots per ore. Scale up with coil tier.",
                                        "Use magnetic coils for Steel and higher metals."
                                    };
                                    drawGuideScreen(g, f, t, "Ore Processing Guide",
                                            headlines[page], bodies[page], page, 4);
                                }))
                                .cursor(0.77f, 0.93f, 20, 50, true)
                                .cursor(0.77f, 0.93f, 0, 60, false)
                                .cursor(0.77f, 0.93f, 10, 50, true)
                                .cursor(0.77f, 0.93f, 0, 60, false)
                                .build(),

                        TutorialSlide.of("Cross-Links",
                                "Guide pages can link to other guides, to scene layouts, or to\n" +
                                "machine scripts. Clicking a link opens that content directly.\n\n" +
                                "This lets pack authors build a flowing documentation flow\n" +
                                "from overview → details → assembly.")
                                .mock(mock((g, f, t, tick) ->
                                        drawGuideScreen(g, f, t,
                                                "EBF Basics",
                                                "Next Steps",
                                                "Now that you understand the inputs and outputs,\nyou're ready to build the machine.\n\n" +
                                                "Continue Reading →\n► View Automated Script →",
                                                3, 4)))
                                .cursor(0.5f, 0.65f, 20, 30, true)
                                .cursor(0.5f, 0.73f, 15, 30, true)
                                .highlight(0.12f, 0.62f, 0.76f, 0.16f, "Cross-link buttons")
                                .build()
                )
        );
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
                                        drawScriptEditor(g, f, t, "Electric Blast Furnace", 0, EBF_STEPS, tick)))
                                .highlight(0.0f, 0.0f, 0.69f, 0.79f, "3D viewport")
                                .highlight(0.69f, 0.0f, 0.31f, 0.79f, "Step list")
                                .build(),

                        TutorialSlide.of("Following the Steps",
                                "Each step focuses the camera on a specific part of the machine.\n" +
                                "The current step is highlighted on the left panel.\n\n" +
                                "Press Space or click Next to advance through the walkthrough.\n" +
                                "The machine in the preview updates to match each step.")
                                .mock(mock((g, f, t, tick) -> {
                                    int step = (tick / 60) % EBF_STEPS.size();
                                    drawScriptEditor(g, f, t, "Electric Blast Furnace", step, EBF_STEPS, tick);
                                }))
                                .cursor(0.85f, 0.30f, 20, 40, true)
                                .cursor(0.85f, 0.48f, 15, 40, true)
                                .cursor(0.85f, 0.66f, 15, 40, true)
                                .highlight(0.69f, 0.0f, 0.31f, 0.79f, "Active step")
                                .build(),

                        TutorialSlide.of("Variants",
                                "Scripts detect optional blocks in the machine and expose them\n" +
                                "as variant toggles in the panel on the right side.\n\n" +
                                "For the EBF these include coil tier, hatch tier, and\n" +
                                "optional fusion glass. Your selections persist across sessions.")
                                .mock(mock((g, f, t, tick) -> {
                                    // Show the scene viewer (right panel open with variants)
                                    drawSceneViewer(g, f, t, "Electric Blast Furnace", tick);
                                }))
                                .highlight(0.87f, 0.0f, 0.13f, 1.0f, "Right panel")
                                .build()
                )
        );
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
                                .mock(mock((g, f, t, tick) ->
                                        drawSceneViewer(g, f, t, "Ore Processing Line", tick)))
                                .build(),

                        TutorialSlide.of("Scene Steps",
                                "Scene steps walk you through the full multi-machine layout.\n\n" +
                                "Each step can show specific machines and hide others so you\n" +
                                "focus on one part of the production chain at a time.")
                                .mock(mock((g, f, t, tick) -> {
                                    int step = (tick / 70) % 6;
                                    drawScriptEditor(g, f, t, "Ore Processing Line", step,
                                            List.of("Overview", "EBF Setup", "Chem Reactor", "Macerators", "Power I/O", "Finished"),
                                            tick);
                                }))
                                .cursor(0.85f, 0.25f, 20, 50, true)
                                .cursor(0.85f, 0.43f, 15, 50, true)
                                .highlight(0.69f, 0.0f, 0.31f, 0.79f, "Scene steps")
                                .build(),

                        TutorialSlide.of("Mistakes",
                                "Scenes can include layout validation hints called Mistakes.\n\n" +
                                "Each mistake has a severity (Info / Warning / Error) and appears\n" +
                                "in the guide view for the scene — helping players avoid common\n" +
                                "assembly errors before they make them.")
                                .mock(mock((g, f, t, tick) ->
                                        drawGuideScreen(g, f, t,
                                                "Ore Processing Line",
                                                "Layout Notes",
                                                "ℹ Optimal spacing between EBF and Chem Reactor: 4 blocks\n" +
                                                "⚠ Coolant pipe required on Chemical Reactor south face\n" +
                                                "✖ Power distribution overflow if EBF and Turbine share bus",
                                                2, 5)))
                                .highlight(0.0f, 0.45f, 1.0f, 0.35f, "Mistake banners")
                                .build()
                )
        );
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
                                .mock(mock((g, f, t, tick) ->
                                        drawSelectionScreen(g, f, t, tick, 2)))
                                .cursor(0.17f, 0.58f, 30, 50, true)
                                .highlight(0.04f, 0.52f, 0.34f, 0.40f, "+ New Guide card")
                                .build(),

                        TutorialSlide.of("The Guide Editor",
                                "The left panel is your writing area.\n\n" +
                                "Type a Headline (shown large at the top of the page) and\n" +
                                "Body Text below it. Pages are listed in the right panel.\n" +
                                "Click '+ Add Page' to add more pages.")
                                .mock(mock((g, f, t, tick) ->
                                        drawGuideEditor(g, f, t,
                                                "Ore Processing Guide",
                                                "What Goes In?",
                                                "Raw iron ore enters the Electric Blast Furnace\nalong with a limestone flux.\n\nCoil tier must be at least Cupronickel.",
                                                tick)))
                                .cursor(0.35f, 0.37f, 20, 40, true)
                                .cursor(0.35f, 0.65f, 15, 40, true)
                                .highlight(0.0f, 0.25f, 0.54f, 0.14f, "Headline editor")
                                .highlight(0.0f, 0.42f, 0.54f, 0.42f, "Body text editor")
                                .build(),

                        TutorialSlide.of("Managing Pages",
                                "Click a page in the right panel list to select and edit it.\n" +
                                "Click '+ Add Page' to create a new blank page.\n\n" +
                                "Use Ctrl+S or the Save button to write the guide to disk.\n" +
                                "It immediately appears in /phantasia for all players.")
                                .mock(mock((g, f, t, tick) ->
                                        drawGuideEditor(g, f, t,
                                                "Ore Processing Guide",
                                                "Energy Requirements",
                                                "The EBF requires at least 128 EU/t at LV tier.\nHigher coil tiers unlock hotter temperatures\nfor producing Steel, Aluminium, and beyond.",
                                                tick)))
                                .cursor(0.65f, 0.38f, 20, 30, true)
                                .cursor(0.65f, 0.56f, 15, 30, true)
                                .cursor(0.72f, 0.88f, 20, 40, true)
                                .highlight(0.54f, 0.28f, 0.46f, 0.62f, "Page list + Add Page")
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
                                                "Raw iron ore + limestone flux in the EBF.\nCoil tier: at least Cupronickel.", tick);
                                    }
                                }))
                                .cursor(0.83f, 0.06f, 20, 50, true)
                                .cursor(0.5f, 0.5f, 20, 60, false)
                                .cursor(0.91f, 0.06f, 20, 50, true)
                                .highlight(0.76f, 0.02f, 0.24f, 0.10f, "Preview & Save")
                                .build()
                )
        );
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
                                .mock(mock((g, f, t, tick) ->
                                        drawSelectionScreen(g, f, t, tick, 0)))
                                .cursor(0.17f, 0.58f, 25, 50, true)
                                .highlight(0.04f, 0.52f, 0.34f, 0.40f, "Open EBF card")
                                .build(),

                        TutorialSlide.of("Adding Steps",
                                "Click '+ Add Step' at the bottom of the step list.\n\n" +
                                "Each step has a caption (the text shown to the player),\n" +
                                "a show mode (All / Layer / Range / Parts), and an optional\n" +
                                "camera animation. The EBF script uses one step per layer.")
                                .mock(mock((g, f, t, tick) ->
                                        drawScriptEditor(g, f, t, "Electric Blast Furnace",
                                                (tick / 50) % EBF_STEPS.size(), EBF_STEPS, tick)))
                                .cursor(0.83f, 0.90f, 25, 40, true)
                                .cursor(0.83f, 0.25f, 20, 40, true)
                                .highlight(0.69f, 0.84f, 0.31f, 0.12f, "+ Add Step")
                                .build(),

                        TutorialSlide.of("Setting the Camera",
                                "For each step, set the camera's yaw, pitch, and zoom in the\n" +
                                "floating Camera panel (top-right of the 3D viewport).\n\n" +
                                "Set Lerp Type to SPRING for natural camera movement between\n" +
                                "steps, and Lerp Ticks to 20–30 for smooth transitions.")
                                .mock(mock((g, f, t, tick) ->
                                        drawScriptEditor(g, f, t, "Electric Blast Furnace", 2, EBF_STEPS, tick)))
                                .cursor(0.61f, 0.13f, 20, 30, false)
                                .cursor(0.61f, 0.20f, 10, 25, false)
                                .cursor(0.61f, 0.27f, 10, 25, false)
                                .highlight(0.52f, 0.04f, 0.27f, 0.24f, "Camera panel")
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
                                .cursor(0.87f, 0.90f, 20, 40, true)
                                .highlight(0.72f, 0.86f, 0.28f, 0.10f, "Save Script")
                                .build()
                )
        );
    }
}
