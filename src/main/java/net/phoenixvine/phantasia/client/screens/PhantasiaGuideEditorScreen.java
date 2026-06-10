package net.phoenixvine.phantasia.client.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phantasia.common.PhantasiaGuideData;
import net.phoenixvine.phantasia.common.PhantasiaGuideData.PageData;
import net.phoenixvine.phantasia.common.PhantasiaGuideRegistry;
import net.phoenixvine.phantasia.common.PhantasiaSceneData;

import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * PhantasiaGuideEditorScreen
 *
 * Two-panel editor for {@link PhantasiaGuideData} — standalone text + item guides.
 *
 * Left panel  : live preview of the current page (same rendering as PhantasiaGuideScreen).
 * Right panel : page list + text fields (headline, body text, items).
 *
 *  ┌───────────────────────────────────────────────────────────┐
 *  │  [← Back]   Guide Editor — "Title"   [▶ Preview] [Save]  │
 *  ├───────────────────────────┬───────────────────────────────┤
 *  │                           │  Pages                        │
 *  │  ── HEADLINE ──────────  │  ┌────────────────────────┐   │
 *  │  Body text preview…       │  │ 1. Page headline       │   │
 *  │                           │  │ 2. Another page    [✕] │   │
 *  │  [item] [item]            │  └────────────────────────┘   │
 *  │                           │  [+ Add Page]                 │
 *  │                           ├───────────────────────────────┤
 *  │                           │  Headline:  [______________]  │
 *  │                           │  Body text:                   │
 *  │                           │  ┌────────────────────────┐   │
 *  │                           │  │ Multi-line text area   │   │
 *  │                           │  └────────────────────────┘   │
 *  │                           │  Items: [Add item...]  [✕]    │
 *  │                           │  Link guide: [__________]     │
 *  │                           │  Link scene: [__________]     │
 *  └───────────────────────────┴───────────────────────────────┘
 */
@OnlyIn(Dist.CLIENT)
public class PhantasiaGuideEditorScreen extends Screen {

    // ── Theme ─────────────────────────────────────────────────────────────────
    private static final int C_BG       = 0xFF080810;
    private static final int C_BAR      = 0xEE0A0A14;
    private static final int C_PANEL    = 0xDD0C0C1A;
    private static final int C_ACCENT   = 0xFF4FC3F7;
    private static final int C_BTN      = 0xBB151528;
    private static final int C_BTN_HOV  = 0xBB1A2840;
    private static final int C_BTN_ACT  = 0xBB0D2235;
    private static final int C_TEXT     = 0xFFDDDDDD;
    private static final int C_DIM      = 0xFF667788;
    private static final int C_RED      = 0xFFFF5252;
    private static final int C_GREEN    = 0xFF66BB6A;
    private static final int C_SEL      = 0xBB0D2235;

    private static final int TOP_H      = 22;
    private static final int RIGHT_W    = 280;
    private static final int ROW_H      = 18;

    // ── State ─────────────────────────────────────────────────────────────────
    private final Screen parent;
    private PhantasiaGuideData data;
    private boolean dirty = false;

    // Undo
    private static final int MAX_UNDO = 20;
    private final ArrayDeque<PhantasiaGuideData> undoStack = new ArrayDeque<>();

    // Selection
    private int selectedPage = 0;
    private int pageScrollOffset = 0;

    // Inputs for current page
    private EditBox headlineBox;
    private EditBox bodyBox;        // single EditBox for body (multiline via \n)
    private EditBox guideLinkBox;
    private EditBox sceneLinkBox;
    private EditBox titleBox;       // guide-level title
    private EditBox iconItemBox;    // guide-level icon

    // Item editing
    private int selectedItem = -1;
    private EditBox itemIdBox;
    private EditBox itemLabelBox;
    private EditBox itemCountBox;
    private String itemTypeSelected = "input";

    private record Btn(int x, int y, int w, int h, Runnable action) {
        boolean hit(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private final List<Btn> btns = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────────────────

    public PhantasiaGuideEditorScreen(Screen parent, PhantasiaGuideData data) {
        super(Component.literal("Guide Editor"));
        this.parent = parent;
        this.data   = data.copy();
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        buildWidgets();
        populateFromPage();
    }

    private void buildWidgets() {
        clearWidgets();

        // Guide-level
        titleBox = addW(new EditBox(font, 0, 0, 160, 12, Component.empty()));
        titleBox.setMaxLength(128);
        titleBox.setValue(data.title != null ? data.title : "");
        titleBox.setResponder(v -> { data.title = v.isBlank() ? "Untitled" : v; dirty = true; });

        iconItemBox = addW(new EditBox(font, 0, 0, 120, 12, Component.empty()));
        iconItemBox.setMaxLength(128);
        iconItemBox.setValue(data.iconItem != null ? data.iconItem : "minecraft:book");
        iconItemBox.setHint(Component.literal("icon item id"));
        iconItemBox.setResponder(v -> { data.iconItem = v.isBlank() ? "minecraft:book" : v; dirty = true; });

        // Page-level
        headlineBox = addW(new EditBox(font, 0, 0, RIGHT_W - 12, 12, Component.empty()));
        headlineBox.setMaxLength(256);
        headlineBox.setHint(Component.literal("Headline (optional)"));
        headlineBox.setResponder(v -> { page().headline = v.isBlank() ? null : v; dirty = true; });

        // Body text — single EditBox, users write \n for line breaks
        bodyBox = addW(new EditBox(font, 0, 0, RIGHT_W - 12, 12, Component.empty()));
        bodyBox.setMaxLength(4096);
        bodyBox.setHint(Component.literal("Body text (use \\n for line breaks)"));
        bodyBox.setResponder(v -> {
            page().text = v.isBlank() ? null : v.replace("\\n", "\n");
            dirty = true;
        });

        guideLinkBox = addW(new EditBox(font, 0, 0, RIGHT_W - 12, 12, Component.empty()));
        guideLinkBox.setMaxLength(128);
        guideLinkBox.setHint(Component.literal("Linked guide ID (optional)"));
        guideLinkBox.setResponder(v -> { page().guideId = v.isBlank() ? null : v; dirty = true; });

        sceneLinkBox = addW(new EditBox(font, 0, 0, RIGHT_W - 12, 12, Component.empty()));
        sceneLinkBox.setMaxLength(128);
        sceneLinkBox.setHint(Component.literal("Linked scene ID (optional)"));
        sceneLinkBox.setResponder(v -> { page().sceneId = v.isBlank() ? null : v; dirty = true; });

        // Item editing
        itemIdBox = addW(new EditBox(font, 0, 0, RIGHT_W - 12, 12, Component.empty()));
        itemIdBox.setMaxLength(128);
        itemIdBox.setHint(Component.literal("item id (e.g. gtceu:iron_ingot)"));

        itemLabelBox = addW(new EditBox(font, 0, 0, 100, 12, Component.empty()));
        itemLabelBox.setMaxLength(64);
        itemLabelBox.setHint(Component.literal("label"));

        itemCountBox = addW(new EditBox(font, 0, 0, 36, 12, Component.empty()));
        itemCountBox.setMaxLength(4);
        itemCountBox.setFilter(s -> s.matches("\\d*"));
        itemCountBox.setValue("1");
    }

    private void populateFromPage() {
        selectedPage = Math.min(selectedPage, Math.max(0, data.pages.size() - 1));
        if (data.pages.isEmpty()) {
            hidePageInputs();
            return;
        }
        PageData p = page();

        headlineBox.setValue(p.headline != null ? p.headline : "");
        // Reverse \n → \\n so user can see and edit them
        bodyBox.setValue(p.text != null ? p.text.replace("\n", "\\n") : "");
        guideLinkBox.setValue(p.guideId != null ? p.guideId : "");
        sceneLinkBox.setValue(p.sceneId != null ? p.sceneId : "");
        selectedItem = -1;
        clearItemInputs();
    }

    private void hidePageInputs() {
        for (var b : List.of(headlineBox, bodyBox, guideLinkBox, sceneLinkBox,
                itemIdBox, itemLabelBox, itemCountBox)) {
            if (b != null) { b.visible = false; b.active = false; }
        }
    }

    private void clearItemInputs() {
        itemIdBox.setValue("");
        itemLabelBox.setValue("");
        itemCountBox.setValue("1");
        itemTypeSelected = "input";
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        btns.clear();
        hideAllInputs();

        g.fill(0, 0, width, height, C_BG);

        renderTopBar(g, mx, my);
        renderLeftPreview(g, mx, my);
        renderRightPanel(g, mx, my);

        super.render(g, mx, my, partial);
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private void renderTopBar(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, width, TOP_H, C_BAR);
        g.fill(0, TOP_H - 1, width, TOP_H, C_ACCENT);

        // Back
        topBtn(g, mx, my, 4, "← Back", this::onClose);

        // Title field
        g.drawString(font, "Title:", 4 + font.width("← Back") + 18, (TOP_H - 8) / 2, C_DIM, false);
        int titleX = 4 + font.width("← Back") + 18 + font.width("Title:") + 4;
        placeBox(titleBox, titleX, 4, 160, 13);

        // Icon item field
        int iconX = titleX + 164;
        g.drawString(font, "Icon:", iconX, (TOP_H - 8) / 2, C_DIM, false);
        placeBox(iconItemBox, iconX + font.width("Icon:") + 4, 4, 120, 13);

        // Right buttons
        int rx = width - 4;
        rx = topBtnR(g, mx, my, rx, dirty ? "💾 Save*" : "💾 Save", this::save);
        rx = topBtnR(g, mx, my, rx - 4, "▶ Preview", this::openPreview);
        if (!undoStack.isEmpty())
            topBtnR(g, mx, my, rx - 4, "↩ Undo", this::undo);
    }

    // ── Left preview ──────────────────────────────────────────────────────────

    private void renderLeftPreview(GuiGraphics g, int mx, int my) {
        int previewW = width - RIGHT_W;
        int previewH = height - TOP_H;
        int colW = Math.min(360, previewW - 48);
        int colX = previewW / 2 - colW / 2;

        g.fill(0, TOP_H, previewW, height, 0xFF070710);
        g.fill(previewW - 1, TOP_H, previewW, height, C_ACCENT);

        if (data.pages.isEmpty()) {
            g.drawCenteredString(font, "No pages yet — add one →",
                previewW / 2, height / 2, C_DIM);
            return;
        }

        PageData p = page();
        int y = TOP_H + 16;

        // Headline
        if (p.headline != null && !p.headline.isBlank()) {
            g.fill(colX, y, colX + colW, y + 1, C_ACCENT);
            y += 7;
            float scale = 1.5f;
            int sw = (int)(colW / scale);
            for (var line : font.split(Component.literal(p.headline), sw)) {
                g.pose().pushPose();
                g.pose().translate(colX, y, 0);
                g.pose().scale(scale, scale, 1f);
                g.drawString(font, line, 0, 0, 0xFFEEEEFF, false);
                g.pose().popPose();
                y += (int)(font.lineHeight * scale) + 2;
            }
            y += 4;
        } else {
            g.fill(colX, y, colX + colW, y + 1, 0x334FC3F7);
            y += 8;
        }

        // Body
        if (p.text != null && !p.text.isBlank()) {
            for (String para : p.text.split("\n", -1)) {
                if (para.isBlank()) {
                    y += font.lineHeight / 2;
                } else {
                    for (var line : font.split(Component.literal(para), colW)) {
                        g.drawString(font, line, colX, y, 0xFFDDDDDD, false);
                        y += font.lineHeight + 2;
                    }
                }
            }
            y += 8;
        } else {
            g.drawString(font, "(no body text)", colX, y, C_DIM, false);
            y += font.lineHeight + 8;
        }

        // Items
        if (!p.items.isEmpty()) {
            g.fill(colX, y, colX + colW, y + 1, 0x334FC3F7);
            y += 6;
            int perRow = Math.max(1, (colW + 6) / (94 + 6));
            int col = 0;
            int rowY = y;
            for (PhantasiaSceneData.ItemConditionData it : p.items) {
                int cx = colX + col * (94 + 6);
                renderMiniCard(g, it, cx, rowY);
                col++;
                if (col >= perRow) { col = 0; rowY += 90 + 6; }
            }
        }

        // Links
        if (p.guideId != null && !p.guideId.isBlank()) {
            g.drawString(font, "→ Guide: " + p.guideId, colX, y + 4, 0xFF4FC3F7, false);
        }
        if (p.sceneId != null && !p.sceneId.isBlank()) {
            g.drawString(font, "▶ Scene: " + p.sceneId, colX, y + 14, 0xFF66BB6A, false);
        }
    }

    private void renderMiniCard(GuiGraphics g, PhantasiaSceneData.ItemConditionData it, int cx, int cy) {
        g.fill(cx, cy, cx + 94, cy + 90, 0xCC101022);
        g.fill(cx, cy, cx + 94, cy + 2, it.accentColor());
        // Icon
        ItemStack stack = resolveStack(it);
        if (!stack.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(cx + (94 - 32) / 2, cy + 8, 100);
            g.pose().scale(2f, 2f, 1f);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
        }
        String label = it.displayLabel();
        if (font.width(label) > 88) label = font.plainSubstrByWidth(label, 85) + "…";
        g.drawCenteredString(font, label, cx + 47, cy + 75, 0xFFDDDDDD);
    }

    // ── Right panel ───────────────────────────────────────────────────────────

    private void renderRightPanel(GuiGraphics g, int mx, int my) {
        int px = width - RIGHT_W;
        g.fill(px, TOP_H, width, height, C_PANEL);

        int y = TOP_H + 4;

        // Page list header
        g.fill(px, y, width, y + 14, C_BAR);
        g.drawString(font, "Pages", px + 6, y + 3, C_ACCENT, false);
        int addW = font.width("+ Add") + 8;
        boolean addHov = over(mx, my, width - addW - 4, y + 2, addW, 12);
        g.fill(width - addW - 4, y + 2, width - 4, y + 14,
            addHov ? C_BTN_HOV : C_BTN);
        g.drawString(font, "+ Add", width - addW - 1, y + 4, addHov ? C_ACCENT : C_DIM, false);
        btns.add(new Btn(width - addW - 4, y + 2, addW, 12, this::addPage));
        y += 16;

        // Page rows
        int listTop = y;
        int listH = Math.min(data.pages.size() * ROW_H, 6 * ROW_H);
        for (int i = pageScrollOffset; i < data.pages.size()
                && y < listTop + listH + ROW_H; i++) {
            PageData p = data.pages.get(i);
            boolean sel = i == selectedPage;
            boolean hov = over(mx, my, px, y, RIGHT_W - 20, ROW_H);

            g.fill(px, y, width, y + ROW_H - 1,
                sel ? C_SEL : (hov ? C_BTN_HOV : (i % 2 == 0 ? 0x11FFFFFF : 0)));
            if (sel) g.fill(px, y, px + 2, y + ROW_H - 1, C_ACCENT);

            String lbl = (i + 1) + ". ";
            lbl += (p.headline != null && !p.headline.isBlank())
                ? p.headline : (p.text != null ? p.text.split("\n")[0] : "(empty)");
            if (font.width(lbl) > RIGHT_W - 30)
                lbl = font.plainSubstrByWidth(lbl, RIGHT_W - 30 - font.width("…")) + "…";
            g.drawString(font, lbl, px + 6, y + (ROW_H - 8) / 2, sel ? C_ACCENT : C_TEXT, false);

            // Delete button
            boolean delHov = over(mx, my, width - 18, y + 2, 14, ROW_H - 4);
            g.fill(width - 18, y + 2, width - 4, y + ROW_H - 2,
                delHov ? 0xBB3A0A0A : C_BTN);
            g.drawCenteredString(font, "✕", width - 11, y + (ROW_H - 8) / 2,
                delHov ? C_RED : C_DIM);
            final int fi = i;
            btns.add(new Btn(px, y, RIGHT_W - 20, ROW_H, () -> selectPage(fi)));
            btns.add(new Btn(width - 18, y + 2, 14, ROW_H - 4, () -> deletePage(fi)));

            y += ROW_H;
        }

        y += 4;
        g.fill(px, y, width, y + 1, 0x33FFFFFF);
        y += 6;

        if (data.pages.isEmpty()) return;

        // ── Page field editors ─────────────────────────────────────────────
        g.drawString(font, "Headline:", px + 4, y, C_DIM, false);
        y += font.lineHeight + 2;
        placeBox(headlineBox, px + 4, y, RIGHT_W - 8, 13);
        y += 16;

        g.drawString(font, "Body text (\\n = newline):", px + 4, y, C_DIM, false);
        y += font.lineHeight + 2;
        placeBox(bodyBox, px + 4, y, RIGHT_W - 8, 13);
        y += 16;

        g.fill(px + 4, y, width - 4, y + 1, 0x22FFFFFF);
        y += 6;

        // Items section
        g.drawString(font, "Items (" + page().items.size() + "):", px + 4, y, C_DIM, false);
        int addItemW = font.width("+ Item") + 8;
        boolean aiHov = over(mx, my, width - addItemW - 4, y - 1, addItemW, 12);
        g.fill(width - addItemW - 4, y - 1, width - 4, y + 11,
            aiHov ? C_BTN_HOV : C_BTN);
        g.drawString(font, "+ Item", width - addItemW - 1, y + 1,
            aiHov ? C_ACCENT : C_DIM, false);
        btns.add(new Btn(width - addItemW - 4, y - 1, addItemW, 12, this::addItem));
        y += font.lineHeight + 3;

        // Item list (up to 3 rows)
        for (int i = 0; i < page().items.size() && i < 4; i++) {
            PhantasiaSceneData.ItemConditionData it = page().items.get(i);
            boolean isel = i == selectedItem;
            boolean ihov = over(mx, my, px + 4, y, RIGHT_W - 24, 14);
            g.fill(px + 4, y, width - 20, y + 14, isel ? C_SEL : (ihov ? C_BTN_HOV : C_BTN));
            if (isel) g.fill(px + 4, y, px + 6, y + 14, it.accentColor());

            String itLbl = it.displayLabel() + " — " + it.item;
            if (font.width(itLbl) > RIGHT_W - 34)
                itLbl = font.plainSubstrByWidth(itLbl, RIGHT_W - 34) + "…";
            g.drawString(font, itLbl, px + 8, y + 3, isel ? C_ACCENT : C_TEXT, false);

            boolean diHov = over(mx, my, width - 18, y, 14, 14);
            g.fill(width - 18, y, width - 4, y + 14, diHov ? 0xBB3A0A0A : C_BTN);
            g.drawCenteredString(font, "✕", width - 11, y + 3, diHov ? C_RED : C_DIM);
            final int fi = i;
            btns.add(new Btn(px + 4, y, RIGHT_W - 24, 14, () -> selectItem(fi)));
            btns.add(new Btn(width - 18, y, 14, 14, () -> removeItem(fi)));
            y += 16;
        }

        // Item editor (shown when an item is selected or being added)
        if (selectedItem >= 0 || true) { // always show add row
            y += 2;
            g.drawString(font, "ID:", px + 4, y, C_DIM, false);
            placeBox(itemIdBox, px + 4 + font.width("ID:") + 3, y - 1, RIGHT_W - 8 - font.width("ID:") - 3, 12);
            y += 14;

            g.drawString(font, "Label:", px + 4, y, C_DIM, false);
            placeBox(itemLabelBox, px + 4 + font.width("Label:") + 3, y - 1,
                100, 12);
            y += 14;

            g.drawString(font, "Count:", px + 4, y, C_DIM, false);
            placeBox(itemCountBox, px + 4 + font.width("Count:") + 3, y - 1, 36, 12);
            y += 14;

            // Type selector
            g.drawString(font, "Type:", px + 4, y, C_DIM, false);
            int tx = px + 4 + font.width("Type:") + 4;
            for (String t : new String[]{"input", "output", "catalyst"}) {
                int tw = font.width(t) + 8;
                boolean tsel = t.equals(itemTypeSelected);
                boolean thov = over(mx, my, tx, y - 1, tw, 12);
                g.fill(tx, y - 1, tx + tw, y + 11, tsel ? C_BTN_ACT : (thov ? C_BTN_HOV : C_BTN));
                if (tsel) g.fill(tx, y - 1, tx + tw, y, PhantasiaSceneData.ItemConditionData.staticAccentFor(t));
                g.drawString(font, t, tx + 4, y + 1, tsel ? C_ACCENT : C_DIM, false);
                final String ft = t;
                btns.add(new Btn(tx, y - 1, tw, 12, () -> { itemTypeSelected = ft; }));
                tx += tw + 4;
            }
            y += 16;

            // Apply / add button
            int apW = font.width("✓ Apply") + 10;
            boolean apHov = over(mx, my, px + 4, y, apW, 13);
            g.fill(px + 4, y, px + 4 + apW, y + 13, apHov ? C_BTN_HOV : C_BTN);
            if (apHov) g.fill(px + 4, y, px + 4 + apW, y + 1, C_ACCENT);
            g.drawString(font, "✓ Apply", px + 8, y + 3, apHov ? C_ACCENT : C_TEXT, false);
            btns.add(new Btn(px + 4, y, apW, 13, this::applyItemEdit));
            y += 16;
        }

        y += 4;
        g.fill(px + 4, y, width - 4, y + 1, 0x22FFFFFF);
        y += 6;

        // Links
        g.drawString(font, "Guide link:", px + 4, y, C_DIM, false);
        y += font.lineHeight + 2;
        placeBox(guideLinkBox, px + 4, y, RIGHT_W - 8, 13);
        y += 16;

        g.drawString(font, "Scene link:", px + 4, y, C_DIM, false);
        y += font.lineHeight + 2;
        placeBox(sceneLinkBox, px + 4, y, RIGHT_W - 8, 13);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void addPage() {
        checkpoint();
        PageData p = new PageData();
        data.pages.add(p);
        selectedPage = data.pages.size() - 1;
        dirty = true;
        populateFromPage();
    }

    private void deletePage(int idx) {
        if (idx < 0 || idx >= data.pages.size()) return;
        checkpoint();
        data.pages.remove(idx);
        selectedPage = Math.min(selectedPage, Math.max(0, data.pages.size() - 1));
        dirty = true;
        populateFromPage();
    }

    private void selectPage(int idx) {
        if (idx == selectedPage) return;
        selectedPage = idx;
        populateFromPage();
    }

    private void addItem() {
        selectedItem = -1;
        clearItemInputs();
    }

    private void selectItem(int idx) {
        selectedItem = idx;
        if (idx >= 0 && idx < page().items.size()) {
            PhantasiaSceneData.ItemConditionData it = page().items.get(idx);
            itemIdBox.setValue(it.item != null ? it.item : "");
            itemLabelBox.setValue(it.label != null ? it.label : "");
            itemCountBox.setValue(String.valueOf(it.count));
            itemTypeSelected = it.type != null ? it.type : "input";
        }
    }

    private void removeItem(int idx) {
        if (idx < 0 || idx >= page().items.size()) return;
        checkpoint();
        page().items.remove(idx);
        selectedItem = -1;
        dirty = true;
    }

    private void applyItemEdit() {
        String id = itemIdBox.getValue().trim();
        if (id.isBlank()) return;
        checkpoint();
        int cnt = 1;
        try { cnt = Math.max(1, Integer.parseInt(itemCountBox.getValue().trim())); }
        catch (NumberFormatException ignored) {}

        PhantasiaSceneData.ItemConditionData it =
            new PhantasiaSceneData.ItemConditionData(
                id, cnt,
                itemLabelBox.getValue().isBlank() ? null : itemLabelBox.getValue(),
                itemTypeSelected);

        if (selectedItem >= 0 && selectedItem < page().items.size()) {
            page().items.set(selectedItem, it);
        } else {
            page().items.add(it);
            selectedItem = page().items.size() - 1;
        }
        dirty = true;
    }

    private void save() {
        PhantasiaGuideRegistry.save(data);
        dirty = false;
    }

    private void openPreview() {
        Minecraft.getInstance().setScreen(new PhantasiaGuideScreen(this, data));
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        data = undoStack.pop();
        dirty = true;
        selectedPage = Math.min(selectedPage, Math.max(0, data.pages.size() - 1));
        buildWidgets();
        populateFromPage();
    }

    private void checkpoint() {
        undoStack.push(data.copy());
        if (undoStack.size() > MAX_UNDO) undoStack.removeLast();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        for (Btn b : btns) if (b.hit(mx, my)) { b.action().run(); return true; }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (kc == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (kc == GLFW.GLFW_KEY_S && (mod & GLFW.GLFW_MOD_CONTROL) != 0) { save(); return true; }
        if (kc == GLFW.GLFW_KEY_Z && (mod & GLFW.GLFW_MOD_CONTROL) != 0) { undo(); return true; }
        return super.keyPressed(kc, sc, mod);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PageData page() {
        if (data.pages.isEmpty()) data.pages.add(new PageData());
        return data.pages.get(Math.min(selectedPage, data.pages.size() - 1));
    }

    private void hideAllInputs() {
        for (var b : List.of(headlineBox, bodyBox, guideLinkBox, sceneLinkBox,
                titleBox, iconItemBox, itemIdBox, itemLabelBox, itemCountBox)) {
            if (b != null) { b.visible = false; b.active = false; }
        }
    }

    private void placeBox(EditBox box, int x, int y, int w, int h) {
        box.setX(x); box.setY(y); box.setWidth(w); box.setHeight(h);
        box.visible = true; box.active = true;
    }

    private void topBtn(GuiGraphics g, int mx, int my, int x, String lbl, Runnable act) {
        int w = font.width(lbl) + 10, h = TOP_H - 6;
        boolean hov = over(mx, my, x, 3, w, h);
        g.fill(x, 3, x + w, 3 + h, hov ? C_BTN_HOV : C_BTN);
        if (hov) g.fill(x, 3, x + w, 4, C_ACCENT);
        g.drawString(font, lbl, x + 5, (TOP_H - 8) / 2, hov ? C_ACCENT : C_TEXT, false);
        btns.add(new Btn(x, 3, w, h, act));
    }

    private int topBtnR(GuiGraphics g, int mx, int my, int rx, String lbl, Runnable act) {
        int w = font.width(lbl) + 10, h = TOP_H - 6, x = rx - w;
        boolean hov = over(mx, my, x, 3, w, h);
        g.fill(x, 3, x + w, 3 + h, hov ? C_BTN_HOV : C_BTN);
        if (hov) g.fill(x, 3, x + w, 4, C_ACCENT);
        g.drawString(font, lbl, x + 5, (TOP_H - 8) / 2, hov ? C_ACCENT : C_TEXT, false);
        btns.add(new Btn(x, 3, w, h, act));
        return x - 4;
    }

    private static ItemStack resolveStack(PhantasiaSceneData.ItemConditionData it) {
        if (it.item == null || it.item.isBlank()) return ItemStack.EMPTY;
        try {
            ResourceLocation rl = it.item.contains(":")
                ? new ResourceLocation(it.item)
                : new ResourceLocation("minecraft", it.item);
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            return (item == null || item == Items.AIR)
                ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, it.count));
        } catch (Exception e) { return ItemStack.EMPTY; }
    }

    private boolean over(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
    private boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private <T extends net.minecraft.client.gui.components.AbstractWidget> T addW(T w) {
        w.visible = false; w.active = false;
        return addRenderableWidget(w);
    }
}
