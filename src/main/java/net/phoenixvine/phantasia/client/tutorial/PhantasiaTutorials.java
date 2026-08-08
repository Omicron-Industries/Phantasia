package net.phoenixvine.phantasia.client.tutorial;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.client.screens.PhantasiaSceneSelectionScreen;
import net.phoenixvine.phantasia.common.data.guides.PhantasiaGuideRegistry;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScripts;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import java.util.List;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

@OnlyIn(Dist.CLIENT)
public final class PhantasiaTutorials {

    private PhantasiaTutorials() {}

    private static final int VW = 480;
    private static final int VH = 300;

    public static List<TutorialSequence> all() {
        return List.of(
                gettingStarted(),
                guides(),
                scripts(),
                scenes(),
                devGuides(),
                devScripts(),
                devScenes());
    }

    @FunctionalInterface
    private interface DrawTask {

        void run(GuiGraphics g, Font f, PhoenixTheme t, int tick);
    }

    private static TutorialSlide.MockRenderer mock(DrawTask task) {
        return (g, mx, my, mw, mh, tick) -> {
            Font f = Minecraft.getInstance().font;
            PhoenixTheme t = PhoenixTheme.current();
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

    private static String firstMachineName() {
        var machines = PhantasiaSceneSelectionScreen.PHANTASIA_SCENES;
        if (!machines.isEmpty()) {
            String name = machines.get(0).getDisplayName();
            if (name == null || name.isEmpty())
                name = machines.get(0).getId().getPath().replace('_', ' ');
            return name;
        }
        return "Multiblock Machine";
    }

    private static String firstMachineShortLabel() {
        String name = firstMachineName();
        String[] words = name.split("\\s+");
        if (words.length > 1) {
            StringBuilder sb = new StringBuilder();
            for (String w : words) if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0)));
            return sb.toString();
        }
        return name.length() > 6 ? name.substring(0, 6) : name;
    }

    private static final int SEL_HEADER_H = 52;
    private static final int SEL_CARD_W = 104;
    private static final int SEL_CARD_H = 86;
    private static final int SEL_CARD_PAD = 8;
    private static final int SEL_GRID_W = 3 * SEL_CARD_W + 2 * SEL_CARD_PAD;

    private static void drawSelectionScreen(GuiGraphics g, Font f, PhoenixTheme t, int tick, int activeTab) {
        int gridX = (VW - SEL_GRID_W) / 2;

        g.fillGradient(0, 0, VW, VH, C_BG(), 0xFF0B0B18);

        g.fill(0, 0, VW, SEL_HEADER_H, 0xCC0A0A14);
        g.fill(0, SEL_HEADER_H - 2, VW, SEL_HEADER_H, C_ACCENT());
        g.drawCenteredString(f, "✶ Phantasia", VW / 2, 8, C_ACCENT());
        g.drawCenteredString(f, "Multiblock machines, scenes, and guides", VW / 2, 20, C_DIM());

        int tx = gridX;
        renderMockTab(g, f, tx, 32, "Multiblocks", activeTab == 0);
        tx = gridX + 104;
        renderMockTab(g, f, tx, 32, "Scenes", activeTab == 1);
        tx += f.width("Scenes") + 20;
        renderMockTab(g, f, tx, 32, "Guides", activeTab == 2);
        tx += f.width("Guides") + 20;
        renderMockTab(g, f, tx, 32, "Tutorials", activeTab == 3);

        int searchY = SEL_HEADER_H;
        int searchH = 24;
        g.fill(gridX, searchY, gridX + SEL_GRID_W, searchY + searchH, 0xFF0A0A14);
        g.fill(gridX, searchY, gridX + SEL_GRID_W, searchY + 1, 0xFF333355);
        g.fill(gridX, searchY + searchH - 1, gridX + SEL_GRID_W, searchY + searchH, 0xFF333355);
        g.fill(gridX, searchY, gridX + 1, searchY + searchH, 0xFF333355);
        g.fill(gridX + SEL_GRID_W - 1, searchY, gridX + SEL_GRID_W, searchY + searchH, 0xFF333355);
        g.drawString(f, "Search...", gridX + 4, searchY + 8, 0xFF888888, false);

        int cardsY = searchY + searchH + 6;
        if (activeTab == 0) drawMachineCards(g, f, gridX, cardsY);
        else if (activeTab == 1) drawSceneCardsSelection(g, f, t, gridX, cardsY);
        else if (activeTab == 2) drawGuideCardsSelection(g, f, t, gridX, cardsY);
        else if (activeTab == 3) drawTutorialCardsSelection(g, f, t, gridX, cardsY);

        int footerY = VH - 26;
        g.fill(0, footerY, VW, VH, 0xCC0A0A14);
        g.fill(0, footerY, VW, footerY + 1, 0x33FFFFFF);
        g.drawCenteredString(f,
                "ESC to close  •  " + net.phoenixvine.phantasia.client.keybind.PhoenixKeybinds.keyDisplay() +
                        " near a machine to open directly",
                VW / 2, footerY + 9, C_DIM());
    }

    private static void renderMockTab(GuiGraphics g, Font f, int x, int y, String label, boolean active) {
        int w = f.width(label) + 16;
        g.fill(x, y, x + w, y + 16, active ? C_BTN_HOV() : C_BTN());
        if (active) g.fill(x, y + 14, x + w, y + 16, C_ACCENT());
        g.drawString(f, label, x + 8, y + 4, active ? C_ACCENT() : C_DIM(), false);
    }

    private static void drawMachineCards(GuiGraphics g, Font f, int gridX, int startY) {
        var machines = PhantasiaSceneSelectionScreen.PHANTASIA_SCENES;

        String[] fallbackNames = { "Multiblock A", "Multiblock B", "Multiblock C",
                "Multiblock D", "Multiblock E", "Multiblock F" };
        boolean[] fallbackSteps = { true, true, false, true, false, false };

        for (int i = 0; i < 6; i++) {
            int col = i % 3, row = i / 3;
            int cx = gridX + col * (SEL_CARD_W + SEL_CARD_PAD);
            int cy = startY + row * (SEL_CARD_H + SEL_CARD_PAD);

            int cardBg = (0xBB << 24) | (C_PANEL() & 0x00FFFFFF);
            g.fill(cx, cy, cx + SEL_CARD_W, cy + SEL_CARD_H, cardBg);
            g.fill(cx, cy, cx + SEL_CARD_W, cy + 2, C_BORDER());

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

            String name = (i < machines.size()) ? machines.get(i).getDisplayName() : fallbackNames[i];
            if (name == null || name.isEmpty()) {
                name = (i < machines.size()) ? machines.get(i).getId().getPath().replace('_', ' ') : fallbackNames[i];
            }
            if (f.width(name) > SEL_CARD_W - 8)
                name = f.plainSubstrByWidth(name, SEL_CARD_W - 8 - f.width("...")) + "...";
            g.drawString(f, name, cx + 4, cy + SEL_CARD_H - 22, C_TEXT(), false);

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

    private static void drawGuideCardsSelection(GuiGraphics g, Font f, PhoenixTheme t, int gridX, int startY) {
        {
            int cx = gridX;
            int cy = startY;
            g.fill(cx, cy, cx + SEL_CARD_W, cy + SEL_CARD_H, (0xBB << 24) | (C_PANEL() & 0x00FFFFFF));
            g.fill(cx, cy, cx + SEL_CARD_W, cy + 2, C_BORDER());
            g.drawCenteredString(f, "+", cx + SEL_CARD_W / 2, cy + 24, C_DIM());
            g.drawCenteredString(f, "+ New Guide", cx + SEL_CARD_W / 2, cy + SEL_CARD_H - 22, C_DIM());
        }

        var guides = PhantasiaGuideRegistry.all().stream().limit(5).toList();
        String[] fallbackTitles = { "Getting Started", "Ore Processing", "Power Setup",
                "EBF Basics", "Recipe Tips" };
        String[] fallbackIcons = { "minecraft:knowledge_book", "minecraft:iron_ore",
                "minecraft:redstone", "minecraft:furnace", "minecraft:crafting_table" };
        for (int i = 0; i < 5; i++) {
            int gridPos = i + 1;
            int col = gridPos % 3, row = gridPos / 3;
            int cx = gridX + col * (SEL_CARD_W + SEL_CARD_PAD);
            int cy = startY + row * (SEL_CARD_H + SEL_CARD_PAD);
            g.fill(cx, cy, cx + SEL_CARD_W, cy + SEL_CARD_H, (0xBB << 24) | (C_PANEL() & 0x00FFFFFF));
            g.fill(cx, cy, cx + SEL_CARD_W, cy + 2, C_BORDER());

            String iconRes = (i < guides.size() && guides.get(i).iconItem != null) ? guides.get(i).iconItem :
                    fallbackIcons[i % fallbackIcons.length];
            try {
                var rl = iconRes.contains(":") ? new net.minecraft.resources.ResourceLocation(iconRes) :
                        new net.minecraft.resources.ResourceLocation("minecraft", iconRes);
                var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                    g.pose().pushPose();
                    g.pose().translate(cx + (SEL_CARD_W - 32) / 2f, cy + 8, 0);
                    g.pose().scale(2f, 2f, 1f);
                    g.renderItem(new net.minecraft.world.item.ItemStack(item), 0, 0);
                    g.pose().popPose();
                }
            } catch (Exception ignored) {}
            String title = (i < guides.size()) ? guides.get(i).title : fallbackTitles[i];
            if (title == null) title = fallbackTitles[i % fallbackTitles.length];
            if (f.width(title) > SEL_CARD_W - 8)
                title = f.plainSubstrByWidth(title, SEL_CARD_W - 8 - f.width("...")) + "...";
            g.drawString(f, title, cx + 4, cy + SEL_CARD_H - 33, C_TEXT(), false);
            int pages = (i < guides.size() && guides.get(i).pages != null) ? guides.get(i).pages.size() : (i + 2);
            g.drawString(f, pages + " page" + (pages == 1 ? "" : "s"), cx + 4, cy + SEL_CARD_H - 22, C_DIM(), false);
        }
    }

    private static void drawSceneCardsSelection(GuiGraphics g, Font f, PhoenixTheme t, int gridX, int startY) {
        String[] fallbackTitles = { "Ore Processing Line", "Steel Production", "Power Grid",
                "Blast Array", "Chemical Plant" };
        String[] fallbackIcons = { "minecraft:chest", "minecraft:blast_furnace", "minecraft:redstone_block",
                "minecraft:iron_block", "minecraft:cauldron" };
        int[] fallbackMachines = { 3, 2, 4, 5, 2 };

        {
            int cx = gridX;
            int cy = startY;
            g.fill(cx, cy, cx + SEL_CARD_W, cy + SEL_CARD_H, (0xBB << 24) | (C_PANEL() & 0x00FFFFFF));
            g.fill(cx, cy, cx + SEL_CARD_W, cy + 2, C_BORDER());
            g.drawCenteredString(f, "+", cx + SEL_CARD_W / 2, cy + SEL_CARD_H / 2 - 10, C_DIM());
            g.drawCenteredString(f, "New Scene", cx + SEL_CARD_W / 2, cy + SEL_CARD_H - 22, C_DIM());
        }

        for (int i = 0; i < 5; i++) {
            int slot = i + 1;
            int col = slot % 3, row = slot / 3;
            int cx = gridX + col * (SEL_CARD_W + SEL_CARD_PAD);
            int cy = startY + row * (SEL_CARD_H + SEL_CARD_PAD);
            g.fill(cx, cy, cx + SEL_CARD_W, cy + SEL_CARD_H, (0xBB << 24) | (C_PANEL() & 0x00FFFFFF));
            g.fill(cx, cy, cx + SEL_CARD_W, cy + 2, C_BORDER());
            try {
                var rl = new net.minecraft.resources.ResourceLocation(fallbackIcons[i]);
                var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                    g.pose().pushPose();
                    g.pose().translate(cx + (SEL_CARD_W - 32) / 2f, cy + 6, 0);
                    g.pose().scale(2f, 2f, 1f);
                    g.renderItem(new net.minecraft.world.item.ItemStack(item), 0, 0);
                    g.pose().popPose();
                }
            } catch (Exception ignored) {}
            String title = fallbackTitles[i];
            if (f.width(title) > SEL_CARD_W - 8)
                title = f.plainSubstrByWidth(title, SEL_CARD_W - 8 - f.width("...")) + "...";
            g.drawString(f, title, cx + 4, cy + SEL_CARD_H - 34, C_TEXT(), false);
            g.drawString(f, fallbackMachines[i] + " machines", cx + 4, cy + SEL_CARD_H - 23, C_DIM(), false);
            g.fill(cx + SEL_CARD_W - 8, cy + 4, cx + SEL_CARD_W - 4, cy + 8, C_GREEN());
        }
    }

    private static void drawTutorialCardsSelection(GuiGraphics g, Font f, PhoenixTheme t, int gridX, int startY) {
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
        startY += SEL_CARD_H + SEL_CARD_PAD;
        g.drawString(f, "For Pack Authors", gridX, startY, C_WARN(), false);
        startY += 12;
        String[][] devTuts = { { "Creating Guides", "Dev" }, { "Writing Scripts", "Dev" },
                { "Writing Scenes", "Dev" } };
        for (int i = 0; i < 3; i++) {
            int cx = gridX + i * (SEL_CARD_W + SEL_CARD_PAD);
            g.fill(cx, startY, cx + SEL_CARD_W, startY + SEL_CARD_H, (0xBB << 24) | (C_PANEL() & 0x00FFFFFF));
            g.fill(cx, startY, cx + SEL_CARD_W, startY + 2, C_WARN());
            g.drawString(f, devTuts[i][0], cx + 4, startY + SEL_CARD_H - 22, C_TEXT(), false);
            g.drawString(f, devTuts[i][1], cx + 4, startY + SEL_CARD_H - 10, C_DIM(), false);
        }
    }

    private static void drawGuideScreen(GuiGraphics g, Font f, PhoenixTheme t,
                                        String title, String headline, String body,
                                        int pageIdx, int pageCount) {
        int TOP_BAR_H = 22, NAV_H = 30;
        int colW = 380, colX = (VW - colW) / 2;

        g.fillGradient(0, 0, VW, VH, 0xFF07070E, 0xFF0D0D1E);

        g.fill(0, 0, VW, TOP_BAR_H, C_BAR());
        g.fill(0, TOP_BAR_H - 1, VW, TOP_BAR_H, C_ACCENT());
        g.drawCenteredString(f, title, VW / 2, (TOP_BAR_H - 8) / 2, C_ACCENT());

        int bw = f.width("← Back") + 12;
        g.fill(4, 3, 4 + bw, TOP_BAR_H - 3, C_BTN());
        g.drawString(f, "← Back", 10, (TOP_BAR_H - 8) / 2, C_TEXT(), false);

        int ew = f.width("✏ Edit") + 12;
        g.fill(VW - 4 - ew, 3, VW - 4, TOP_BAR_H - 3, C_BTN());
        g.drawString(f, "✏ Edit", VW - 4 - ew + 6, (TOP_BAR_H - 8) / 2, C_TEXT(), false);

        int y = TOP_BAR_H + 14;

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

        if (pageCount > 1) {
            g.drawString(f, "Page " + (pageIdx + 1) + " of " + pageCount, colX, y, C_DIM(), false);
            y += f.lineHeight + 5;
        }
        y += 4;

        if (body != null) {
            for (String rawLine : body.split("\n", -1)) {
                if (y + f.lineHeight > VH - NAV_H - 4) break;
                boolean isLink = rawLine.endsWith("→");
                if (isLink) {
                    int bW = f.width(rawLine) + 12;
                    g.fill(colX, y - 1, colX + bW, y + f.lineHeight + 2, 0x22FFFFFF);
                    g.fill(colX, y - 1, colX + 2, y + f.lineHeight + 2, C_ACCENT());
                    g.drawString(f, rawLine, colX + 4, y, C_ACCENT(), false);
                } else if (!rawLine.isEmpty()) {
                    g.drawString(f, rawLine, colX, y, C_TEXT(), false);
                }
                y += f.lineHeight + 2;
            }
        }

        int navY = VH - NAV_H;
        g.fill(0, navY, VW, VH, 0xDD0A0A14);
        g.fill(0, navY, VW, navY + 1, 0x33FFFFFF);
        int midX = VW / 2;
        int bY = navY + 6, bH = NAV_H - 12;

        boolean hasPrev = pageIdx > 0;
        int prevW = f.width("◄  Prev") + 14;
        g.fill(midX - prevW - 26, bY, midX - 26, bY + bH, hasPrev ? C_BTN() : 0x33111128);
        g.drawCenteredString(f, "◄  Prev", midX - 26 - prevW / 2, bY + (bH - 8) / 2, hasPrev ? C_TEXT() : C_DIM());

        g.drawCenteredString(f, (pageIdx + 1) + " / " + pageCount, midX, bY + (bH - 8) / 2, C_DIM());

        boolean hasNext = pageIdx < pageCount - 1;
        int nextW = f.width("Next  ►") + 14;
        g.fill(midX + 26, bY, midX + 26 + nextW, bY + bH, hasNext ? C_BTN() : 0x33111128);
        if (hasNext) g.fill(midX + 26, bY, midX + 26 + nextW, bY + 1, C_ACCENT());
        g.drawCenteredString(f, "Next  ►", midX + 26 + nextW / 2, bY + (bH - 8) / 2,
                hasNext ? C_ACCENT() : C_DIM());
    }

    private static void drawGuideEditor(GuiGraphics g, Font f, PhoenixTheme t,
                                        String guideTitle, String headline, String bodyText, int tick) {
        int TOP_H = 22, rightW = 220;
        int previewW = VW - rightW;
        int colW = Math.min(320, previewW - 48);
        int colX = previewW / 2 - colW / 2;

        g.fill(0, 0, VW, VH, C_BG());

        g.fill(0, 0, VW, TOP_H, C_BAR());
        g.fill(0, TOP_H - 1, VW, TOP_H, C_ACCENT());

        int backW = f.width("← Back") + 10;
        g.fill(4, 3, 4 + backW, TOP_H - 3, C_BTN());
        g.drawString(f, "← Back", 9, (TOP_H - 8) / 2, C_TEXT(), false);

        int titleLabelX = 4 + backW + 8;
        g.drawString(f, "Title:", titleLabelX, (TOP_H - 8) / 2, C_DIM(), false);
        int titleBoxX = titleLabelX + f.width("Title:") + 4;
        g.fill(titleBoxX, 3, titleBoxX + 140, TOP_H - 3, 0xFF0A0A14);
        g.fill(titleBoxX, 3, titleBoxX + 140, 4, 0xFF333355);
        g.drawString(f, guideTitle, titleBoxX + 3, (TOP_H - 8) / 2, C_TEXT(), false);

        int saveW = f.width("💾 Save") + 10;
        g.fill(VW - 4 - saveW, 3, VW - 4, TOP_H - 3, C_BTN());
        g.drawString(f, "💾 Save", VW - 4 - saveW + 5, (TOP_H - 8) / 2, C_TEXT(), false);

        int prevBtnW = f.width("► Preview") + 10;
        g.fill(VW - 4 - saveW - 4 - prevBtnW, 3, VW - 4 - saveW - 4, TOP_H - 3, C_BTN());
        g.drawString(f, "► Preview", VW - 4 - saveW - 4 - prevBtnW + 5, (TOP_H - 8) / 2, C_TEXT(), false);

        g.fill(0, TOP_H, previewW, VH, 0xFF070710);

        g.fill(previewW - 1, TOP_H, previewW, VH, C_ACCENT());

        int y = TOP_H + 12;
        int hlH = 32;
        boolean hlFocused = (tick / 60) % 2 == 0;
        g.fill(colX - 4, y, colX + colW + 4, y + hlH, hlFocused ? 0xFF0D1C2A : 0xBB0D131A);

        g.fill(colX - 4, y, colX + colW + 4, y + 1, hlFocused ? C_ACCENT() : 0xFF223544);
        g.fill(colX - 4, y + hlH - 1, colX + colW + 4, y + hlH, hlFocused ? C_ACCENT() : 0xFF223544);
        g.fill(colX - 4, y, colX - 3, y + hlH, hlFocused ? C_ACCENT() : 0xFF223544);
        g.fill(colX + colW + 3, y, colX + colW + 4, y + hlH, hlFocused ? C_ACCENT() : 0xFF223544);
        g.drawString(f, "Headline", colX, y + 2, C_DIM(), false);
        g.drawString(f, headline != null ? headline : "", colX + 4, y + 14, 0xFFFFFFFF, false);

        y += hlH + 8;

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

        int addY = TOP_H + 18 + 3 * 18 + 4;
        g.fill(rpx - 2, addY, VW - 4, addY + 12, C_BTN());
        g.drawCenteredString(f, "+ Add Page", (rpx - 2 + VW - 4) / 2, addY + 2, C_ACCENT());

        g.fill(rpx - 2, addY + 18, VW - 4, addY + 19, 0x33FFFFFF);

        g.drawString(f, "Items", rpx, addY + 24, C_DIM(), false);
    }

    private static void drawScriptEditor(GuiGraphics g, Font f, PhoenixTheme t,
                                         String machineName, int selectedStep,
                                         List<String> stepCaptions, int tick) {
        drawScriptEditor(g, f, t, machineName, selectedStep, stepCaptions, tick, false);
    }

    private static void drawScriptEditor(GuiGraphics g, Font f, PhoenixTheme t,
                                         String machineName, int selectedStep,
                                         List<String> stepCaptions, int tick,
                                         boolean showCamPanel) {
        final int TOP_H = 22;
        final int STEP_H = 42;
        final int TL_H = 22;
        final int BOT_H = STEP_H + TL_H;
        int vpH = VH - TOP_H - BOT_H;

        g.fill(0, 0, VW, TOP_H, C_BAR());
        g.fill(0, TOP_H - 1, VW, TOP_H, C_ACCENT());

        int x = 6;
        String[] modeTabs = { "◈ Select", "⚠ Annotate", "◦ World" };
        for (String tab : modeTabs) {
            int tw = f.width(tab) + 12;
            g.fill(x, 3, x + tw, TOP_H - 3, C_BTN());
            g.drawString(f, tab, x + 6, (TOP_H - 8) / 2, C_DIM(), false);
            x += tw + 4;
        }
        x += 4;

        int pvW = f.width("► Preview") + 10;
        g.fill(x, 3, x + pvW, TOP_H - 3, C_BTN());
        g.drawString(f, "► Preview", x + 5, (TOP_H - 8) / 2, C_DIM(), false);
        x += pvW + 4;

        int camTabW = f.width("🎥 Camera") + 10;
        g.fill(x, 3, x + camTabW, TOP_H - 3, showCamPanel ? C_BTN_ACT() : C_BTN());
        if (showCamPanel) g.fill(x, TOP_H - 3, x + camTabW, TOP_H - 2, C_ACCENT());
        g.drawString(f, "🎥 Camera", x + 5, (TOP_H - 8) / 2, showCamPanel ? C_ACCENT() : C_DIM(), false);
        x += camTabW + 4;

        int scW = f.width("⊙ Start Cam") + 10;
        g.fill(x, 3, x + scW, TOP_H - 3, C_BTN());
        g.drawString(f, "⊙ Start Cam", x + 5, (TOP_H - 8) / 2, C_DIM(), false);

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

        g.fill(0, TOP_H, VW, TOP_H + vpH, 0xFF07070F);

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

        float pulse = 0.5f + 0.5f * (float) Math.sin(tick * 0.15f);
        int pa = (int) (35 * pulse);
        g.fill(cx2 - 4, cy2 - 4 - (selectedStep % 4) * 7,
                cx2 + 48 + rot, cy2 + 14 - (selectedStep % 4) * 7,
                (pa << 24) | (C_ACCENT() & 0xFFFFFF));

        if (showCamPanel) {
            int cpH = 54, cpX = 6, cpW = VW - 12;
            int cpY = VH - BOT_H - cpH - 4;
            g.fill(cpX - 2, cpY - 2, cpX + cpW + 2, cpY + cpH + 2, 0xDD070712);
            g.fill(cpX - 2, cpY - 2, cpX + cpW + 2, cpY - 1, C_ACCENT());
            g.drawString(f, "🎥  Camera — step " + (selectedStep + 1), cpX + 4, cpY + 3, C_ACCENT(), false);

            int r1Y = cpY + 14;
            int bxc = cpX + 4;
            int capBtnW = f.width("📷 Capture Cam") + 12;
            g.fill(bxc, r1Y, bxc + capBtnW, r1Y + 14, C_BTN_ACT());
            g.fill(bxc, r1Y, bxc + capBtnW, r1Y + 1, C_ACCENT());
            g.drawString(f, "📷 Capture Cam", bxc + 6, r1Y + 3, C_ACCENT(), false);
            bxc += capBtnW + 6;
            int clrW = f.width("✕ Clear") + 10;
            g.fill(bxc, r1Y, bxc + clrW, r1Y + 14, C_BTN());
            g.drawString(f, "✕ Clear", bxc + 5, r1Y + 3, C_DIM(), false);
            bxc += clrW + 10;
            g.drawString(f, "Yaw -135.0°  Pitch -30.0°  Zoom 40.0", bxc, r1Y + 3, C_DIM(), false);

            int r2Y = cpY + 32;
            bxc = cpX + 4;
            g.drawString(f, "Zoom:", bxc, r2Y + 2, C_DIM(), false);
            bxc += f.width("Zoom:") + 3;
            g.fill(bxc, r2Y, bxc + 40, r2Y + 12, 0xFF0A0A14);
            g.fill(bxc, r2Y, bxc + 40, r2Y + 1, 0xFF333355);
            g.drawString(f, "40.0", bxc + 3, r2Y + 2, C_TEXT(), false);
            bxc += 46;
            int ltW = f.width("SPRING") + 16;
            g.fill(bxc, r2Y, bxc + ltW, r2Y + 13, C_BTN());
            g.fill(bxc, r2Y, bxc + ltW, r2Y + 1, C_ACCENT());
            g.drawString(f, "SPRING", bxc + 8, r2Y + 2, C_ACCENT(), false);
            bxc += ltW + 6;
            g.drawString(f, "over", bxc, r2Y + 2, C_DIM(), false);
            bxc += f.width("over") + 3;
            g.fill(bxc, r2Y, bxc + 34, r2Y + 12, 0xFF0A0A14);
            g.fill(bxc, r2Y, bxc + 34, r2Y + 1, 0xFF333355);
            g.drawString(f, "25", bxc + 3, r2Y + 2, C_TEXT(), false);
            bxc += 38;
            g.drawString(f, "ticks", bxc, r2Y + 2, C_DIM(), false);
        }

        int botY = VH - BOT_H;
        g.fill(0, botY, VW, botY + STEP_H, C_BAR());
        g.fill(0, botY, VW, botY + 1, C_ACCENT());

        int y1 = botY + 4;
        int bx3 = 8;

        String stepLbl = (selectedStep + 1) + "/" + stepCaptions.size();
        g.drawString(f, "Step", bx3, y1 - 2, 0xFF334455, false);
        g.drawString(f, stepLbl, bx3, y1 + 6, C_ACCENT(), false);
        bx3 += f.width(stepLbl) + 10;

        for (String nav : new String[] { "+", "−", "Dup", "◄", "►" }) {
            int nw = f.width(nav) + 8;
            g.fill(bx3, y1, bx3 + nw, y1 + 14, C_BTN());
            g.drawCenteredString(f, nav, bx3 + nw / 2, y1 + 3, C_TEXT());
            bx3 += nw + 4;
        }
        bx3 += 4;

        g.drawString(f, "Tick", bx3, y1 + 3, C_DIM(), false);
        bx3 += f.width("Tick") + 3;
        g.fill(bx3, y1, bx3 + 38, y1 + 13, 0xFF0A0A14);
        g.fill(bx3, y1, bx3 + 38, y1 + 1, 0xFF333355);
        int stepTick = selectedStep * 40;
        g.drawString(f, String.valueOf(stepTick), bx3 + 3, y1 + 3, C_TEXT(), false);
        bx3 += 44;

        String cap = selectedStep < stepCaptions.size() ? stepCaptions.get(selectedStep) : "";
        int capW = Math.max(100, VW - bx3 - 130);
        g.fill(bx3, y1, bx3 + capW, y1 + 13, C_BTN());
        g.fill(bx3, y1, bx3 + capW, y1 + 1, 0x33FFFFFF);
        String capDisp = cap.isEmpty() ? "✎  Caption…" : f.plainSubstrByWidth(cap, capW - 16);
        g.drawString(f, capDisp, bx3 + 4, y1 + 3, cap.isEmpty() ? C_DIM() : C_TEXT(), false);
        bx3 += capW + 8;

        g.fill(bx3, y1, bx3 + 82, y1 + 14, C_BTN());
        g.drawString(f, "○ Running", bx3 + 5, y1 + 3, C_DIM(), false);

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

        int tlY = VH - TL_H;
        g.fill(0, tlY, VW, VH, C_PANEL());
        g.fill(0, tlY, VW, tlY + 1, 0x33FFFFFF);

        int margin = 30, trackW = VW - margin * 2;
        int midY = tlY + TL_H / 2;
        g.fill(margin, midY - 1, margin + trackW, midY + 1, 0xFF1A2C3C);

        g.fill(margin - 1, midY - 3, margin, midY + 3, 0xFF3A506A);
        g.fill(margin + trackW, midY - 3, margin + trackW + 1, midY + 3, 0xFF3A506A);

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

        if (nSteps > 1) {
            float prog = (float) selectedStep / (nSteps - 1);
            int phX = margin + (int) (prog * trackW);
            g.fill(phX - 1, tlY + 2, phX + 1, tlY + TL_H - 2, 0xAAFFFFFF);
        }
    }

    private static void drawSceneViewer(GuiGraphics g, Font f, PhoenixTheme t,
                                        String machineName, int tick) {
        drawSceneViewer(g, f, t, machineName, tick, false, 0, null);
    }

    private static void drawSceneViewer(GuiGraphics g, Font f, PhoenixTheme t,
                                        String machineName, int tick,
                                        boolean panelExpanded, int activeStep,
                                        List<String> steps) {
        final int PANEL_W = panelExpanded ? 168 : 18;
        final int TL_H = 26;
        final int CAP_H = 38;
        int vpW = VW - PANEL_W;

        g.fill(0, 0, vpW, VH, 0xFF07070F);

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

        g.fill(vpW, 0, VW, VH, C_PANEL());
        g.fill(vpW, 0, vpW + 1, VH, C_ACCENT());

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

            g.drawString(f, "Layer:", vpW + 8, py + 4, C_DIM(), false);
            g.fill(vpW + 10, py + 14, vpW + 10 + bW, py + 28, C_BTN());
            g.drawCenteredString(f, "◀", vpW + 10 + bW / 2, py + 17, C_TEXT());
            g.drawCenteredString(f, "All", lX + lW / 2, py + 17, C_ACCENT());
            g.fill(lX + lW + 2, py + 14, lX + lW + 2 + bW, py + 28, C_BTN());
            g.drawCenteredString(f, "▶", lX + lW + 2 + bW / 2, py + 17, C_TEXT());
            py += 32;

            String[] btns = { "🧱 Build Mode", "🗺 Footprint", "⊕ Center Camera", "🔍 Block List", "🧮 Materials" };
            for (String b : btns) {
                g.fill(vpW + 4, py, VW - 4, py + 13, C_BTN());
                g.drawString(f, b, vpW + 8, py + 3, C_TEXT(), false);
                py += 16;
            }
        }

        int capY = VH - TL_H - CAP_H;
        g.fill(0, capY, vpW, capY + CAP_H, 0xDD08080F);
        g.fill(0, capY, vpW, capY + 1, C_ACCENT());
        String caption = (steps != null && activeStep < steps.size()) ? steps.get(activeStep) :
                "Heating Coils — Place GregTech coil blocks in the center ring.";
        g.drawCenteredString(f, caption, vpW / 2, capY + (CAP_H - f.lineHeight) / 2, 0xFFDDDDDD);

        int tlY = VH - TL_H;
        g.fill(0, tlY, vpW, VH, C_TL_BG());
        g.fill(0, tlY, vpW, tlY + 1, C_ACCENT());

        g.fill(4, tlY + 4, 22, tlY + TL_H - 4, C_BTN());
        g.drawCenteredString(f, "⏸", 13, tlY + 8, C_TEXT());
        g.fill(26, tlY + 4, 44, tlY + TL_H - 4, C_BTN());
        g.drawCenteredString(f, "🔒", 35, tlY + 8, C_TEXT());
        g.fill(48, tlY + 4, 62, tlY + TL_H - 4, C_BTN());
        g.drawString(f, "1x", 51, tlY + 9, C_TEXT(), false);

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

        int phX = tx + (int) (tw * prog);
        g.fill(phX - 2, midY - 4, phX + 2, midY + 4, C_ACCENT());
    }

    private static void drawSceneEditor(GuiGraphics g, Font f, PhoenixTheme t,
                                        String sceneName, boolean showPlacements,
                                        boolean showCamPanel, int activeStep,
                                        List<String> steps, int tick) {
        final int TOP_H = 22;
        final int STEP_H = 50;
        final int TL_H = 22;
        final int BOT_H = STEP_H + TL_H;
        final int PNL_W = showPlacements ? 220 : 0;
        int vpW = VW - PNL_W;
        int vpH = VH - TOP_H - BOT_H;

        g.fill(0, 0, VW, TOP_H, C_BAR());
        g.fill(0, TOP_H - 1, VW, TOP_H, C_ACCENT());
        int x = 6;
        int ppW = f.width("⊞ Placements") + 12;
        g.fill(x, 3, x + ppW, TOP_H - 3, showPlacements ? C_BTN_ACT() : C_BTN());
        if (showPlacements) g.fill(x, TOP_H - 3, x + ppW, TOP_H - 2, C_ACCENT());
        g.drawString(f, "⊞ Placements", x + 5, (TOP_H - 8) / 2, showPlacements ? C_ACCENT() : C_DIM(), false);
        x += ppW + 4;
        int pvW2 = f.width("► Preview") + 10;
        g.fill(x, 3, x + pvW2, TOP_H - 3, C_BTN());
        g.drawString(f, "► Preview", x + 5, (TOP_H - 8) / 2, C_DIM(), false);
        x += pvW2 + 4;
        int camTabW2 = 76;
        g.fill(x, 3, x + camTabW2, TOP_H - 3, showCamPanel ? C_BTN_ACT() : C_BTN());
        if (showCamPanel) g.fill(x, TOP_H - 3, x + camTabW2, TOP_H - 2, C_ACCENT());
        g.drawString(f, "🎥 Camera", x + 5, (TOP_H - 8) / 2, showCamPanel ? C_ACCENT() : C_DIM(), false);
        x += camTabW2 + 4;
        int wiW = f.width("▦ World") + 10;
        g.fill(x, 3, x + wiW, TOP_H - 3, C_BTN());
        g.drawString(f, "▦ World", x + 5, (TOP_H - 8) / 2, C_DIM(), false);
        int rx = VW - 4;
        int bkW2 = f.width("✕ Back") + 10;
        rx -= bkW2;
        g.fill(rx, 3, rx + bkW2, TOP_H - 3, C_BTN());
        g.drawString(f, "✕ Back", rx + 5, (TOP_H - 8) / 2, C_TEXT(), false);
        rx -= 4;
        int svW2 = f.width("💾 Save") + 10;
        rx -= svW2;
        g.fill(rx, 3, rx + svW2, TOP_H - 3, C_BTN());
        g.fill(rx, TOP_H - 3, rx + svW2, TOP_H - 2, C_GREEN());
        g.drawString(f, "💾 Save", rx + 5, (TOP_H - 8) / 2, C_GREEN(), false);
        g.drawCenteredString(f, sceneName, VW / 2, (TOP_H - 8) / 2, C_DIM());

        if (showPlacements) {
            g.fill(0, TOP_H, PNL_W, VH - BOT_H, C_PANEL());
            g.fill(PNL_W - 1, TOP_H, PNL_W, VH - BOT_H, 0x44FFFFFF);
            g.fill(0, TOP_H, PNL_W, TOP_H + 14, C_BAR());
            g.drawString(f, "Placements", 6, TOP_H + 3, C_ACCENT(), false);
            g.drawString(f, "Name:", 6, TOP_H + 18, C_DIM(), false);
            int nfW2 = PNL_W - 44;
            g.fill(40, TOP_H + 16, 40 + nfW2, TOP_H + 28, 0xFF0A0A14);
            g.fill(40, TOP_H + 16, 40 + nfW2, TOP_H + 17, 0xFF333355);
            String sn = sceneName.length() > 18 ? sceneName.substring(0, 15) + "..." : sceneName;
            g.drawString(f, sn, 43, TOP_H + 18, C_TEXT(), false);
            g.fill(4, TOP_H + 32, PNL_W - 4, TOP_H + 33, 0x22FFFFFF);
            g.drawString(f, "Machines (" + 3 + "):", 6, TOP_H + 37, C_DIM(), false);
            String[] mNames2 = { firstMachineName(), "Chemical Reactor", "Macerator" };
            String[] offsets = { "0, 0, 0", "14, 0, 0", "28, 0, 0" };
            for (int i = 0; i < 3; i++) {
                int my = TOP_H + 50 + i * 28;
                boolean sel = (i == activeStep % 3);
                g.fill(4, my, PNL_W - 4, my + 22, sel ? C_BTN_HOV() : C_BTN());
                if (sel) g.fill(4, my, 5, my + 22, C_ACCENT());
                String mn = mNames2[i].length() > 18 ? mNames2[i].substring(0, 15) + "..." : mNames2[i];
                g.drawString(f, mn, 8, my + 3, sel ? C_ACCENT() : C_TEXT(), false);
                g.drawString(f, "offset: " + offsets[i], 8, my + 13, C_DIM(), false);
            }
            int addBtnY = TOP_H + 50 + 3 * 28 + 4;
            g.fill(4, addBtnY, PNL_W - 4, addBtnY + 14, C_BTN());
            g.drawCenteredString(f, "+ Add Machine", PNL_W / 2, addBtnY + 3, C_ACCENT());
        }

        int vpX = PNL_W;
        g.fill(vpX, TOP_H, VW, TOP_H + vpH, 0xFF07070F);
        int cx = vpX + vpW / 2 - 44;
        int cy = TOP_H + vpH / 2 - 20;
        float angle = (tick % 360) / 360f * 2f * (float) Math.PI;
        int rot = (int) (Math.cos(angle) * 5);
        for (int m = 0; m < 3; m++) {
            int ox = m * 16 + rot;
            for (int layer = 0; layer < 3; layer++) {
                for (int r = 0; r < 3; r++) {
                    for (int c = 0; c < 3; c++) {
                        boolean shell = layer == 0 || layer == 2 || r == 0 || r == 2 || c == 0 || c == 2;
                        if (!shell) continue;
                        int bx2 = cx + ox + c * 9 - r * 4;
                        int by2 = cy - layer * 5 + r * 5 - c * 2 - m * 3;
                        boolean hi = (m == activeStep % 3);
                        int col = hi ? 0xFF2A4870 : (0xFF1A2A3E + (m * 8 + layer * 4 + r + c) % 8 * 0x010101);
                        g.fill(bx2, by2, bx2 + 8, by2 + 8, col);
                        g.fill(bx2, by2, bx2 + 8, by2 + 1, hi ? 0x66FFFFFF : 0x22FFFFFF);
                    }
                }
            }
        }

        if (showCamPanel) {
            int cpH2 = 54, cpX2 = vpX + 6, cpW2 = VW - vpX - 12;
            int cpY2 = VH - BOT_H - cpH2 - 4;
            g.fill(cpX2 - 2, cpY2 - 2, cpX2 + cpW2 + 2, cpY2 + cpH2 + 2, 0xDD070712);
            g.fill(cpX2 - 2, cpY2 - 2, cpX2 + cpW2 + 2, cpY2 - 1, C_ACCENT());
            g.drawString(f, "🎥  Camera — step " + (activeStep + 1), cpX2 + 4, cpY2 + 3, C_ACCENT(), false);
            int r1Y2 = cpY2 + 14, bxc2 = cpX2 + 4;
            int capBtnW2 = f.width("📷 Capture Cam") + 12;
            g.fill(bxc2, r1Y2, bxc2 + capBtnW2, r1Y2 + 14, C_BTN_ACT());
            g.fill(bxc2, r1Y2, bxc2 + capBtnW2, r1Y2 + 1, C_ACCENT());
            g.drawString(f, "📷 Capture Cam", bxc2 + 6, r1Y2 + 3, C_ACCENT(), false);
            bxc2 += capBtnW2 + 10;
            g.drawString(f, "Yaw -90.0°  Pitch -20.0°  Zoom 60.0", bxc2, r1Y2 + 3, C_DIM(), false);
            int r2Y2 = cpY2 + 32;
            bxc2 = cpX2 + 4;
            g.drawString(f, "Zoom:", bxc2, r2Y2 + 2, C_DIM(), false);
            bxc2 += f.width("Zoom:") + 3;
            g.fill(bxc2, r2Y2, bxc2 + 40, r2Y2 + 12, 0xFF0A0A14);
            g.fill(bxc2, r2Y2, bxc2 + 40, r2Y2 + 1, 0xFF333355);
            g.drawString(f, "60.0", bxc2 + 3, r2Y2 + 2, C_TEXT(), false);
            bxc2 += 46;
            int ltW2 = f.width("SPRING") + 16;
            g.fill(bxc2, r2Y2, bxc2 + ltW2, r2Y2 + 13, C_BTN());
            g.fill(bxc2, r2Y2, bxc2 + ltW2, r2Y2 + 1, C_ACCENT());
            g.drawString(f, "SPRING", bxc2 + 8, r2Y2 + 2, C_ACCENT(), false);
            bxc2 += ltW2 + 6;
            g.drawString(f, "over 30 ticks", bxc2, r2Y2 + 2, C_DIM(), false);
        }

        int rowY = VH - BOT_H;
        g.fill(0, rowY, VW, rowY + STEP_H, C_BAR());
        g.fill(0, rowY, VW, rowY + 1, C_ACCENT());
        int y1 = rowY + 4, bx3 = 8;
        String sLabel = (activeStep + 1) + "/" + (steps != null ? steps.size() : 3);
        g.drawString(f, "Step", bx3, y1 - 2, 0xFF334455, false);
        g.drawString(f, sLabel, bx3, y1 + 6, C_ACCENT(), false);
        bx3 += f.width(sLabel) + 10;
        for (String nav : new String[] { "+", "−", "Dup", "◄", "►" }) {
            int nw = f.width(nav) + 8;
            g.fill(bx3, y1, bx3 + nw, y1 + 14, C_BTN());
            g.drawCenteredString(f, nav, bx3 + nw / 2, y1 + 3, C_TEXT());
            bx3 += nw + 4;
        }
        bx3 += 4;
        String capText = (steps != null && activeStep < steps.size()) ? steps.get(activeStep) : "Overview";
        int capW2 = Math.max(100, VW - bx3 - 130);
        g.fill(bx3, y1, bx3 + capW2, y1 + 13, C_BTN());
        g.fill(bx3, y1, bx3 + capW2, y1 + 1, 0x33FFFFFF);
        g.drawString(f, f.plainSubstrByWidth(capText, capW2 - 12), bx3 + 4, y1 + 3, C_TEXT(), false);

        int y2 = rowY + STEP_H / 2 + 5;
        bx3 = 8;
        g.drawString(f, "Visible:", bx3, y2 + 2, C_DIM(), false);
        bx3 += f.width("Visible:") + 4;
        String[] vis = { firstMachineShortLabel(), "CR", "MAC" };
        for (int m = 0; m < 3; m++) {
            boolean act = (m <= activeStep % 3);
            int mw2 = f.width(vis[m]) + 10;
            g.fill(bx3, y2, bx3 + mw2, y2 + 14, act ? C_BTN_ACT() : C_BTN());
            if (act) g.fill(bx3, y2, bx3 + mw2, y2 + 1, C_GREEN());
            g.drawString(f, vis[m], bx3 + 5, y2 + 3, act ? C_GREEN() : C_DIM(), false);
            bx3 += mw2 + 4;
        }
        bx3 += 8;
        g.drawString(f, "Running: all", bx3, y2 + 2, C_DIM(), false);

        int tlY2 = VH - TL_H;
        g.fill(0, tlY2, VW, VH, C_PANEL());
        g.fill(0, tlY2, VW, tlY2 + 1, 0x33FFFFFF);
        int margin2 = 30, trackW2 = VW - margin2 * 2, midY2 = tlY2 + TL_H / 2;
        g.fill(margin2, midY2 - 1, margin2 + trackW2, midY2 + 1, 0xFF1A2C3C);
        int nSteps2 = steps != null ? steps.size() : 3;
        for (int di = 0; di < nSteps2; di++) {
            float frac = nSteps2 > 1 ? (float) di / (nSteps2 - 1) : 0f;
            int dotX2 = margin2 + (int) (frac * trackW2);
            boolean sel2 = di == activeStep;
            g.fill(dotX2 - 5, midY2 - 5, dotX2 + 5, midY2 + 5, sel2 ? C_ACCENT() : 0xFF3A506A);
            g.fill(dotX2 - 3, midY2 - 3, dotX2 + 3, midY2 + 3, sel2 ? 0xFF1A3C5C : 0xFF0A1520);
            g.drawCenteredString(f, String.valueOf(di + 1), dotX2, midY2 - 3, sel2 ? C_ACCENT() : C_DIM());
        }
    }

    private static final List<String> EBF_STEPS = List.of(
            "Introduction", "Foundation Layer", "Casing Walls",
            "Heating Coils", "Muffler & Maintenance", "Energy Hatch", "Item I/O", "Complete");

    static TutorialSequence gettingStarted() {
        return new TutorialSequence(
                "getting_started",
                Component.translatable("tutorial.phantasia.getting_started.title"),
                Component.translatable("tutorial.phantasia.getting_started.desc"),
                "minecraft:knowledge_book", TutorialSequence.PLAYER,
                List.of(
                        TutorialSlide.of("tutorial.phantasia.getting_started.s0.title",
                                "tutorial.phantasia.getting_started.s0.body")
                                .mock(mock((g, f, t, tick) -> drawSelectionScreen(g, f, t, tick, 0)))
                                .build(),

                        TutorialSlide
                                .of(Component.translatable("tutorial.phantasia.getting_started.s1.title",
                                        net.phoenixvine.phantasia.client.keybind.PhoenixKeybinds.keyDisplay()),
                                        Component.translatable("tutorial.phantasia.getting_started.s1.body",
                                                net.phoenixvine.phantasia.client.keybind.PhoenixKeybinds.keyDisplay()))
                                .mock(mock((g, f, t, tick) -> {

                                    g.fill(0, 0, VW, VH, 0xFF060C14);

                                    g.fillGradient(0, 0, VW, VH / 2, 0xFF1A2840, 0xFF0A1420);
                                    g.fill(0, VH / 2, VW, VH, 0xFF0A0A08);

                                    String mName = firstMachineName();
                                    String mShort = firstMachineShortLabel();
                                    int bx = VW / 2 - 24, by = VH / 2 - 40;
                                    g.fill(bx, by, bx + 48, by + 48, 0xFF1E2C44);
                                    g.fill(bx, by, bx + 48, by + 1, 0x44FFFFFF);
                                    g.fill(bx, by, bx + 1, by + 48, 0x22FFFFFF);
                                    g.fill(bx + 4, by + 4, bx + 44, by + 44, 0xFF223355);
                                    g.drawCenteredString(f, mShort, bx + 24, by + 20, C_ACCENT());

                                    g.fill(VW / 2 - 4, VH / 2 - 1, VW / 2 + 4, VH / 2 + 1, 0x88FFFFFF);
                                    g.fill(VW / 2 - 1, VH / 2 - 4, VW / 2 + 1, VH / 2 + 4, 0x88FFFFFF);

                                    int ttW = Math.max(140, f.width(mName) + 24);
                                    g.fill(VW / 2 - ttW / 2, VH / 2 + 30, VW / 2 + ttW / 2, VH / 2 + 42, 0xCC07070E);
                                    g.drawCenteredString(f, mName, VW / 2, VH / 2 + 33, C_ACCENT());

                                    float pct = ((tick % 100) / 100f);
                                    int barW = 200, barH = 28;
                                    int barX = (VW - barW) / 2, barY = VH - 44;
                                    g.fill(barX, barY, barX + barW, barY + barH, 0xCC0A0A14);
                                    g.fill(barX, barY, barX + barW, barY + 1, C_ACCENT());
                                    g.drawString(f,
                                            net.phoenixvine.phantasia.client.keybind.PhoenixKeybinds.keyDisplay() +
                                                    " Hold to Phantasize",
                                            barX + 8, barY + 5, C_TEXT(), false);

                                    g.fill(barX + 8, barY + 18, barX + barW - 8, barY + 24, 0x33FFFFFF);
                                    g.fill(barX + 8, barY + 18, barX + 8 + (int) ((barW - 16) * pct), barY + 24,
                                            C_PROG());
                                }))
                                .cursor(0.5f, 0.47f, 20, 80, false)
                                .highlight(0.292f, 0.853f, 0.417f, 0.093f, "Hold progress bar")
                                .build(),

                        TutorialSlide.of(
                                Component.translatable("tutorial.phantasia.getting_started.s2.title"),
                                Component.translatable("tutorial.phantasia.getting_started.s2.body",
                                        net.phoenixvine.phantasia.client.keybind.PhoenixKeybinds.keyDisplay()))
                                .mock(mock((g, f, t, tick) -> {
                                    g.fill(0, 0, VW, VH, 0xFF060C14);
                                    g.fillGradient(0, 0, VW, VH / 2, 0xFF1A2840, 0xFF0A1420);
                                    g.fill(0, VH / 2, VW, VH, 0xFF0A0A08);

                                    String dmName = firstMachineName();
                                    String dmShort = firstMachineShortLabel();
                                    int bx = VW / 2 - 20, by = VH / 2 - 36;
                                    g.fill(bx, by, bx + 40, by + 40, 0xFF1E2C44);
                                    g.fill(bx, by, bx + 40, by + 1, 0x44FFFFFF);
                                    g.fill(bx, by, bx + 1, by + 40, 0x22FFFFFF);
                                    g.drawCenteredString(f, dmShort, bx + 20, by + 16, C_ACCENT());

                                    g.fill(VW / 2 - 4, VH / 2 - 14 - 1, VW / 2 + 4, VH / 2 - 14 + 1, 0x88FFFFFF);
                                    g.fill(VW / 2 - 1, VH / 2 - 18, VW / 2 + 1, VH / 2 - 10, 0x88FFFFFF);

                                    int mode = (tick / 100) % 3;
                                    String modeLabel;

                                    if (mode == 0) {

                                        modeLabel = "JADE";
                                        int jw = Math.max(130, f.width(dmName) + 14);
                                        int jh = 18;
                                        int jx = 8, jy = 8;
                                        g.fill(jx, jy, jx + jw, jy + jh, 0xCC07070E);
                                        g.fill(jx, jy, jx + jw, jy + 1, C_ACCENT());
                                        g.fill(jx, jy, jx + 1, jy + jh, C_ACCENT());
                                        g.drawString(f, dmName, jx + 5, jy + 5, C_ACCENT(), false);
                                    } else if (mode == 1) {

                                        modeLabel = "TOOLTIP";
                                        int ttW = Math.max(160, f.width(dmName) + 32);
                                        int ttH = 22;
                                        int ttX = VW / 2 - ttW / 2, ttY = VH / 2 - 6;
                                        g.fill(ttX, ttY, ttX + ttW, ttY + ttH, 0xCC07070E);
                                        g.fill(ttX, ttY, ttX + ttW, ttY + 1, C_ACCENT());
                                        g.drawCenteredString(f, dmName, VW / 2, ttY + (ttH - 8) / 2, C_ACCENT());
                                    } else {

                                        modeLabel = "HOTBAR";
                                        int hbW = Math.max(160, f.width(dmName) + 24);
                                        int hbH = 16;
                                        int hbX = (VW - hbW) / 2, hbY = VH - 44;
                                        g.fill(hbX, hbY, hbX + hbW, hbY + hbH, 0xCC07070E);
                                        g.fill(hbX, hbY, hbX + hbW, hbY + 1, C_ACCENT());
                                        g.drawCenteredString(f, dmName, VW / 2, hbY + 4, C_ACCENT());

                                        int htW = 182, htH = 22;
                                        int htX = (VW - htW) / 2, htY = VH - 24;
                                        g.fill(htX, htY, htX + htW, htY + htH, 0xBB1A1A2A);
                                        g.fill(htX, htY, htX + htW, htY + 1, 0x55FFFFFF);
                                    }

                                    int bW = f.width("Mode: " + modeLabel) + 8;
                                    g.fill(VW - bW - 6, VH - 28, VW - 6, VH - 14, 0xCC0A0A14);
                                    g.fill(VW - bW - 6, VH - 28, VW - 6, VH - 27, C_ACCENT());
                                    g.drawString(f, "Mode: " + modeLabel, VW - bW - 2, VH - 24, C_DIM(), false);
                                }))
                                .highlight(0.0f, 0.0f, 0.33f, 0.12f, "JADE position")
                                .highlight(0.29f, 0.40f, 0.42f, 0.10f, "TOOLTIP position")
                                .highlight(0.21f, 0.80f, 0.58f, 0.10f, "HOTBAR position")
                                .build(),

                        TutorialSlide.of("tutorial.phantasia.getting_started.s3.title",
                                "tutorial.phantasia.getting_started.s3.body")
                                .mock(mock((g, f, t, tick) -> {

                                    int c = tick % 340;
                                    int tab = c < 120 ? 0 : c < 200 ? 1 : c < 280 ? 2 : 3;
                                    drawSelectionScreen(g, f, t, tick, tab);
                                }))
                                .cursor(0.231f, 0.133f, 40, 60, true)
                                .cursor(0.425f, 0.133f, 20, 60, true)
                                .cursor(0.533f, 0.133f, 20, 60, true)
                                .cursor(0.654f, 0.133f, 20, 60, true)
                                .highlight(0.158f, 0.107f, 0.560f, 0.053f, "Tab bar")
                                .build(),

                        TutorialSlide.of("tutorial.phantasia.getting_started.s4.title",
                                "tutorial.phantasia.getting_started.s4.body")
                                .mock(mock((g, f, t, tick) -> {
                                    drawSelectionScreen(g, f, t, tick, 3);
                                }))
                                .build()));
    }

    static TutorialSequence guides() {
        return new TutorialSequence(
                "guides",
                Component.translatable("tutorial.phantasia.guides.title"),
                Component.translatable("tutorial.phantasia.guides.desc"),
                "minecraft:book", TutorialSequence.PLAYER,
                List.of(
                        TutorialSlide.of("tutorial.phantasia.guides.s0.title",
                                "tutorial.phantasia.guides.s0.body")
                                .mock(mock((g, f, t, tick) -> drawSelectionScreen(g, f, t, tick, 2)))
                                .cursor(0.533f, 0.130f, 20, 40, true)
                                .highlight(0.506f, 0.107f, 0.090f, 0.053f, "Guides tab")
                                .build(),

                        TutorialSlide.of("tutorial.phantasia.guides.s1.title",
                                "tutorial.phantasia.guides.s1.body")
                                .mock(mock((g, f, t, tick) -> drawGuideScreen(g, f, t,
                                        "Ore Processing Guide",
                                        "What Goes In?",
                                        "Raw iron ore enters the Electric Blast Furnace\nalong with a limestone flux to produce iron ingots.\n\nThe EBF requires a heating coil tier of at least\nCupronickel for basic iron smelting.",
                                        0, 4)))
                                .cursor(0.62f, 0.95f, 25, 40, true)
                                .highlight(0.0f, 0.90f, 1.0f, 0.10f, "Navigation bar")
                                .build(),

                        TutorialSlide.of("tutorial.phantasia.guides.s2.title",
                                "tutorial.phantasia.guides.s2.body")
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

                        TutorialSlide.of("tutorial.phantasia.guides.s3.title",
                                "tutorial.phantasia.guides.s3.body")
                                .mock(mock((g, f, t, tick) -> drawGuideScreen(g, f, t,
                                        "EBF Basics",
                                        "Next Steps",
                                        "Now that you understand the inputs and outputs,\nyou're ready to build the machine.\n\n" +
                                                "Continue Reading →\n► View Automated Script →",
                                        3, 4)))
                                .cursor(0.25f, 0.390f, 20, 30, true)
                                .cursor(0.25f, 0.427f, 15, 30, true)
                                .highlight(0.104f, 0.370f, 0.60f, 0.080f, "Cross-link buttons")
                                .build()));
    }

    static TutorialSequence scripts() {
        return new TutorialSequence(
                "scripts",
                Component.translatable("tutorial.phantasia.scripts.title"),
                Component.translatable("tutorial.phantasia.scripts.desc"),
                "minecraft:writable_book", TutorialSequence.PLAYER,
                List.of(
                        TutorialSlide.of("tutorial.phantasia.scripts.s0.title",
                                "tutorial.phantasia.scripts.s0.body")
                                .mock(mock((g, f, t, tick) -> drawSceneViewer(g, f, t, "Electric Blast Furnace", tick,
                                        false, 0, EBF_STEPS)))
                                .highlight(0.0f, 0.0f, 0.963f, 0.787f, "3D viewport")
                                .highlight(0.0f, 0.787f, 0.963f, 0.213f, "Caption + timeline")
                                .build(),

                        TutorialSlide.of("tutorial.phantasia.scripts.s1.title",
                                "tutorial.phantasia.scripts.s1.body")
                                .mock(mock((g, f, t, tick) -> {
                                    int step = (tick / 60) % EBF_STEPS.size();
                                    drawSceneViewer(g, f, t, "Electric Blast Furnace", tick, false, step, EBF_STEPS);
                                }))
                                .highlight(0.0f, 0.787f, 0.963f, 0.127f, "Caption strip")
                                .highlight(0.0f, 0.913f, 0.963f, 0.087f, "Timeline scrubber")
                                .build(),

                        TutorialSlide.of("tutorial.phantasia.scripts.s2.title",
                                "tutorial.phantasia.scripts.s2.body")
                                .mock(mock((g, f, t, tick) -> drawSceneViewer(g, f, t, "Electric Blast Furnace", tick,
                                        true, 0, EBF_STEPS)))
                                .highlight(0.65f, 0.0f, 0.35f, 1.0f, "Right panel")
                                .build()));
    }

    static TutorialSequence scenes() {
        return new TutorialSequence(
                "scenes",
                Component.translatable("tutorial.phantasia.scenes.title"),
                Component.translatable("tutorial.phantasia.scenes.desc"),
                "minecraft:filled_map", TutorialSequence.PLAYER,
                List.of(
                        TutorialSlide.of("tutorial.phantasia.scenes.s0.title",
                                "tutorial.phantasia.scenes.s0.body")
                                .mock(mock((g, f, t, tick) -> drawSceneViewer(g, f, t, "Ore Processing Line", tick)))
                                .build(),

                        TutorialSlide.of("tutorial.phantasia.scenes.s1.title",
                                "tutorial.phantasia.scenes.s1.body")
                                .mock(mock((g, f, t, tick) -> {
                                    int step = (tick / 70) % 6;
                                    List<String> sceneSteps = List.of("Overview", "EBF Setup", "Chem Reactor",
                                            "Macerators", "Power I/O", "Finished");
                                    drawSceneViewer(g, f, t, "Ore Processing Line", tick, false, step, sceneSteps);
                                }))
                                .highlight(0.0f, 0.787f, 0.963f, 0.127f, "Caption — current step")
                                .highlight(0.0f, 0.913f, 0.963f, 0.087f, "Timeline scrubber")
                                .build(),

                        TutorialSlide.of("tutorial.phantasia.scenes.s2.title",
                                "tutorial.phantasia.scenes.s2.body")
                                .mock(mock((g, f, t, tick) -> drawGuideScreen(g, f, t,
                                        "Ore Processing Line",
                                        "Layout Notes",
                                        "ℹ Optimal spacing between EBF and Chem Reactor: 4 blocks\n" +
                                                "⚠ Coolant pipe required on Chemical Reactor south face\n" +
                                                "✖ Power distribution overflow if EBF and Turbine share bus",
                                        2, 5)))
                                .highlight(0.0f, 0.260f, 1.0f, 0.120f, "Mistake banners")
                                .build()));
    }

    static TutorialSequence devGuides() {
        return new TutorialSequence(
                "dev_guides",
                Component.translatable("tutorial.phantasia.dev_guides.title"),
                Component.translatable("tutorial.phantasia.dev_guides.desc"),
                "minecraft:knowledge_book", TutorialSequence.DEV,
                List.of(
                        TutorialSlide.of("tutorial.phantasia.dev_guides.s0.title",
                                "tutorial.phantasia.dev_guides.s0.body")
                                .mock(mock((g, f, t, tick) -> drawSelectionScreen(g, f, t, tick, 2)))
                                .cursor(0.533f, 0.130f, 20, 50, true)
                                .cursor(0.267f, 0.417f, 20, 40, true)
                                .highlight(0.506f, 0.107f, 0.090f, 0.055f, "Guides tab")
                                .highlight(0.158f, 0.273f, 0.68f, 0.287f, "Guide cards area")
                                .build(),

                        TutorialSlide.of("tutorial.phantasia.dev_guides.s1.title",
                                "tutorial.phantasia.dev_guides.s1.body")
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

                        TutorialSlide.of("tutorial.phantasia.dev_guides.s2.title",
                                "tutorial.phantasia.dev_guides.s2.body")
                                .mock(mock((g, f, t, tick) -> drawGuideEditor(g, f, t,
                                        "Ore Processing Guide",
                                        "Energy Requirements",
                                        "The EBF requires at least 128 EU/t at LV tier.\nHigher coil tiers unlock hotter temperatures\nfor producing Steel, Aluminium, and beyond.",
                                        tick)))
                                .cursor(0.62f, 0.147f, 20, 30, true)
                                .cursor(0.62f, 0.213f, 15, 30, true)
                                .cursor(0.92f, 0.037f, 20, 40, true)
                                .highlight(0.550f, 0.090f, 0.44f, 0.290f, "Page list + Add Page")
                                .build(),

                        TutorialSlide.of("tutorial.phantasia.dev_guides.s3.title",
                                "tutorial.phantasia.dev_guides.s3.body")
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

    static TutorialSequence devScripts() {
        return new TutorialSequence(
                "dev_scripts",
                Component.translatable("tutorial.phantasia.dev_scripts.title"),
                Component.translatable("tutorial.phantasia.dev_scripts.desc"),
                "minecraft:writable_book", TutorialSequence.DEV,
                List.of(
                        TutorialSlide.of("tutorial.phantasia.dev_scripts.s0.title",
                                "tutorial.phantasia.dev_scripts.s0.body")
                                .mock(mock((g, f, t, tick) -> drawSelectionScreen(g, f, t, tick, 0)))
                                .cursor(0.260f, 0.417f, 25, 60, true)
                                .highlight(0.158f, 0.273f, 0.217f, 0.287f, "Machine card")
                                .build(),

                        TutorialSlide.of("tutorial.phantasia.dev_scripts.s1.title",
                                "tutorial.phantasia.dev_scripts.s1.body")
                                .mock(mock((g, f, t, tick) -> drawScriptEditor(g, f, t, firstMachineName(),
                                        (tick / 60) % EBF_STEPS.size(), EBF_STEPS, tick)))

                                .cursor(0.071f, 0.037f, 20, 60, true)
                                .cursor(0.500f, 0.430f, 20, 50, true)
                                .highlight(0.013f, 0.000f, 0.117f, 0.073f, "◈ Select")
                                .build(),

                        TutorialSlide.of("tutorial.phantasia.dev_scripts.s2.title",
                                "tutorial.phantasia.dev_scripts.s2.body")
                                .mock(mock((g, f, t, tick) -> drawScriptEditor(g, f, t, firstMachineName(),
                                        (tick / 60) % EBF_STEPS.size(), EBF_STEPS, tick)))

                                .cursor(0.213f, 0.037f, 20, 60, true)
                                .cursor(0.500f, 0.350f, 20, 50, true)
                                .highlight(0.138f, 0.000f, 0.150f, 0.073f, "⚠ Annotate")
                                .build(),

                        TutorialSlide.of("tutorial.phantasia.dev_scripts.s3.title",
                                "tutorial.phantasia.dev_scripts.s3.body")
                                .mock(mock((g, f, t, tick) -> drawScriptEditor(g, f, t, firstMachineName(),
                                        (tick / 60) % EBF_STEPS.size(), EBF_STEPS, tick)))

                                .cursor(0.477f, 0.037f, 20, 60, true)
                                .highlight(0.417f, 0.000f, 0.123f, 0.073f, "► Preview")
                                .build(),

                        TutorialSlide.of("tutorial.phantasia.dev_scripts.s4.title",
                                "tutorial.phantasia.dev_scripts.s4.body")
                                .mock(mock((g, f, t, tick) -> drawScriptEditor(g, f, t, firstMachineName(),
                                        (tick / 60) % EBF_STEPS.size(), EBF_STEPS, tick)))

                                .cursor(0.094f, 0.823f, 25, 50, true)

                                .cursor(0.50f, 0.823f, 20, 50, false)
                                .highlight(0.0f, 0.787f, 1.0f, 0.140f, "Step row")
                                .build(),

                        TutorialSlide.of("tutorial.phantasia.dev_scripts.s5.title",
                                "tutorial.phantasia.dev_scripts.s5.body")
                                .mock(mock((g, f, t, tick) -> {
                                    int s = (tick / 80) % EBF_STEPS.size();
                                    drawScriptEditor(g, f, t, firstMachineName(), s, EBF_STEPS, tick);
                                }))

                                .cursor(0.102f, 0.897f, 20, 50, true)
                                .cursor(0.173f, 0.897f, 15, 40, true)
                                .cursor(0.265f, 0.897f, 15, 40, true)
                                .highlight(0.0f, 0.870f, 0.42f, 0.050f, "Show mode tabs")
                                .build(),

                        TutorialSlide.of("tutorial.phantasia.dev_scripts.s6.title",
                                "tutorial.phantasia.dev_scripts.s6.body")
                                .mock(mock((g, f, t, tick) -> drawScriptEditor(g, f, t, firstMachineName(),
                                        (tick / 70) % EBF_STEPS.size(), EBF_STEPS, tick, true)))

                                .cursor(0.610f, 0.037f, 20, 60, true)

                                .cursor(0.110f, 0.663f, 20, 50, true)
                                .highlight(0.0f, 0.587f, 1.0f, 0.193f, "Camera panel")
                                .build(),

                        TutorialSlide.of("tutorial.phantasia.dev_scripts.s7.title",
                                "tutorial.phantasia.dev_scripts.s7.body")
                                .mock(mock((g, f, t, tick) -> {
                                    int s = (tick / 70) % EBF_STEPS.size();
                                    drawScriptEditor(g, f, t, firstMachineName(), s, EBF_STEPS, tick);
                                }))

                                .cursor(0.348f, 0.037f, 20, 60, true)

                                .cursor(0.50f, 0.43f, 20, 50, true)
                                .highlight(0.296f, 0.000f, 0.104f, 0.073f, "◦ World")
                                .build(),

                        TutorialSlide.of(
                                Component.translatable("tutorial.phantasia.dev_scripts.s8.title"),
                                Component.translatable("tutorial.phantasia.dev_scripts.s8.body",
                                        net.phoenixvine.phantasia.client.keybind.PhoenixKeybinds.keyDisplay()))
                                .mock(mock((g, f, t, tick) -> {
                                    int step = (tick / 40) % EBF_STEPS.size();
                                    drawScriptEditor(g, f, t, firstMachineName(), step, EBF_STEPS, tick);
                                }))

                                .cursor(0.944f, 0.037f, 20, 50, true)
                                .highlight(0.896f, 0.010f, 0.096f, 0.053f, "💾 Save")
                                .build()));
    }

    static TutorialSequence devScenes() {
        List<String> sceneSteps = List.of(
                "Place outer casing",
                "Add heating coils",
                "Install output hatches",
                "Place energy hatch",
                "Add maintenance hatch");
        return new TutorialSequence(
                "dev_scenes",
                Component.translatable("tutorial.phantasia.dev_scenes.title"),
                Component.translatable("tutorial.phantasia.dev_scenes.desc"),
                "minecraft:filled_map", TutorialSequence.DEV,
                List.of(
                        TutorialSlide.of("tutorial.phantasia.dev_scenes.s0.title",
                                "tutorial.phantasia.dev_scenes.s0.body")
                                .mock(mock((g, f, t, tick) -> drawSelectionScreen(g, f, t, tick, 1)))
                                .cursor(0.425f, 0.130f, 20, 60, true)
                                .highlight(0.375f, 0.107f, 0.110f, 0.053f, "Scenes tab")
                                .build(),

                        TutorialSlide.of("tutorial.phantasia.dev_scenes.s1.title",
                                "tutorial.phantasia.dev_scenes.s1.body")
                                .mock(mock((g, f, t, tick) -> {
                                    boolean inEditor = (tick / 80) % 2 == 1;
                                    if (inEditor) {
                                        drawSceneEditor(g, f, t, "New Scene", true, false, 0, sceneSteps, tick);
                                    } else {
                                        drawSelectionScreen(g, f, t, tick, 1);
                                    }
                                }))

                                .cursor(0.267f, 0.417f, 20, 60, true)

                                .cursor(0.096f, 0.037f, 20, 60, true)

                                .cursor(0.229f, 0.557f, 20, 50, true)
                                .highlight(0.0f, 0.073f, 0.458f, 0.687f, "Placements panel")
                                .build(),

                        TutorialSlide.of("tutorial.phantasia.dev_scenes.s2.title",
                                "tutorial.phantasia.dev_scenes.s2.body")
                                .mock(mock((g, f, t, tick) -> {
                                    int s = (tick / 80) % sceneSteps.size();
                                    drawSceneEditor(g, f, t, "Processing Line", true, false, s, sceneSteps, tick);
                                }))

                                .cursor(0.094f, 0.797f, 25, 50, true)

                                .cursor(0.200f, 0.883f, 15, 50, true)
                                .highlight(0.0f, 0.760f, 1.0f, 0.167f, "Step row + visibility toggles")
                                .build(),

                        TutorialSlide.of("tutorial.phantasia.dev_scenes.s3.title",
                                "tutorial.phantasia.dev_scenes.s3.body")
                                .mock(mock((g, f, t, tick) -> drawSceneEditor(g, f, t,
                                        "Processing Line", false, true, (tick / 70) % sceneSteps.size(),
                                        sceneSteps, tick)))

                                .cursor(0.394f, 0.037f, 20, 60, true)

                                .cursor(0.840f, 0.037f, 20, 50, true)
                                .highlight(0.0f, 0.560f, 1.0f, 0.193f, "Camera panel")
                                .highlight(0.792f, 0.010f, 0.096f, 0.053f, "💾 Save")
                                .build()));
    }
}
