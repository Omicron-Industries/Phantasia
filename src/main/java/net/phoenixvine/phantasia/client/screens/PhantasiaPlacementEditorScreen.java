package net.phoenixvine.phantasia.client.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.common.PhantasiaSceneData;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * PhantasiaPlacementEditorScreen
 *
 * Subscreen opened from {@link PhantasiaSceneEditorScreen} when the user clicks
 * "Edit placement / items →" for a selected placement.
 *
 * Lets the user:
 * - Change the machine ID (multiblock OR singleblock) and XYZ offset.
 * - Add, edit, and remove {@link PhantasiaSceneData.ItemConditionData} entries
 * that show recipe conditions alongside this machine in the scene viewer.
 *
 * Changes are written directly into the parent editor's {@code data} object
 * (same reference) so they're immediately reflected. The parent editor's
 * {@link PhantasiaSceneEditorScreen#checkpoint()} is called before mutating
 * so the undo stack stays consistent.
 */
@OnlyIn(Dist.CLIENT)
public class PhantasiaPlacementEditorScreen extends Screen {

    // ── Theme ─────────────────────────────────────────────────────────────────
    private static final int C_BG = 0xFF080810;
    private static final int C_BAR = 0xEE0A0A14;
    private static final int C_PANEL = 0xDD0C0C1A;
    private static final int C_ACCENT = 0xFF4FC3F7;
    private static final int C_BTN = 0xBB151528;
    private static final int C_BTN_HOV = 0xBB1A2840;
    private static final int C_BTN_ACT = 0xFF0D3050;
    private static final int C_TEXT = 0xFFDDDDDD;
    private static final int C_DIM = 0xFF667788;
    private static final int C_WARN = 0xFFFFB74D;
    private static final int C_GREEN = 0xFF66BB6A;
    private static final int C_RED = 0xFFFF5252;

    private static final int TOP_H = 22;
    private static final int PANEL_W = 420;

    private static final String[] TYPES = { "input", "output", "catalyst" };
    private static final String[] TYPE_LABELS = { "Input", "Output", "Catalyst" };

    private static final String[] TRACKS = { "none", "left", "right", "up", "down", "pulse" };
    private static final String[] TRACK_LABELS = { "Fixed", "→", "←", "↑", "↓", "Pulse" };

    // ── Parent ────────────────────────────────────────────────────────────────
    private final PhantasiaSceneEditorScreen parent;
    private final PhantasiaSceneData sceneData;
    private final int placementIndex;

    // ── State ─────────────────────────────────────────────────────────────────
    /** Index of item being edited, -1 = none (add-new form is active). */
    private int editingItem = -1;
    /** Pending type for the add-new form. */
    private String newItemType = "input";
    /** Pending track for the add-new form. */
    private String newItemTrack = "none";

    // ── Widgets ───────────────────────────────────────────────────────────────
    private EditBox machineIdBox;
    private EditBox offsetXBox, offsetYBox, offsetZBox;

    // Item add/edit fields
    private EditBox itemIdBox;
    private EditBox itemCountBox;
    private EditBox itemLabelBox;
    private EditBox itemDurationBox;
    private EditBox itemDescBox;
    private EditBox itemMicrosceneBox;

    // ── Button list ───────────────────────────────────────────────────────────
    private record Btn(int x, int y, int w, int h, Runnable action) {

        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Btn> btns = new ArrayList<>();
    private int lastMX, lastMY;

    // ─────────────────────────────────────────────────────────────────────────

    public PhantasiaPlacementEditorScreen(PhantasiaSceneEditorScreen parent,
                                          PhantasiaSceneData sceneData,
                                          int placementIndex) {
        super(Component.literal("Edit Placement"));
        this.parent = parent;
        this.sceneData = sceneData;
        this.placementIndex = placementIndex;
    }

    private PhantasiaSceneData.PlacementData pd() {
        return sceneData.placements.get(placementIndex);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Init
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        clearWidgets();

        PhantasiaSceneData.PlacementData p = pd();

        machineIdBox = addW(new EditBox(font, 0, 0, 260, 12, Component.empty()));
        machineIdBox.setMaxLength(128);
        machineIdBox.setHint(Component.literal("gtceu:electric_blast_furnace"));
        machineIdBox.setValue(p.machine != null ? p.machine : "");

        offsetXBox = makeIntBox();
        offsetXBox.setValue(String.valueOf(p.x));
        offsetYBox = makeIntBox();
        offsetYBox.setValue(String.valueOf(p.y));
        offsetZBox = makeIntBox();
        offsetZBox.setValue(String.valueOf(p.z));

        itemIdBox = addW(new EditBox(font, 0, 0, 200, 12, Component.empty()));
        itemIdBox.setMaxLength(128);
        itemIdBox.setHint(Component.literal("namespace:item_id"));

        itemCountBox = makeIntBox();
        itemCountBox.setHint(Component.literal("1"));

        itemLabelBox = addW(new EditBox(font, 0, 0, 100, 12, Component.empty()));
        itemLabelBox.setMaxLength(32);
        itemLabelBox.setHint(Component.literal("auto"));

        itemDurationBox = addW(new EditBox(font, 0, 0, 34, 12, Component.empty()));
        itemDurationBox.setMaxLength(4);
        itemDurationBox.setFilter(s -> s.matches("\\d*"));
        itemDurationBox.setHint(Component.literal("20"));

        itemDescBox = addW(new EditBox(font, 0, 0, 200, 12, Component.empty()));
        itemDescBox.setMaxLength(256);
        itemDescBox.setHint(Component.literal("Optional description..."));

        itemMicrosceneBox = addW(new EditBox(font, 0, 0, 200, 12, Component.empty()));
        itemMicrosceneBox.setMaxLength(128);
        itemMicrosceneBox.setHint(Component.literal("phantasia:scene_id (optional)"));

        hideAll();
    }

    private EditBox makeIntBox() {
        EditBox b = addW(new EditBox(font, 0, 0, 34, 12, Component.empty()));
        b.setMaxLength(5);
        b.setFilter(s -> s.matches("-?\\d*"));
        return b;
    }

    private <T extends net.minecraft.client.gui.components.AbstractWidget> T addW(T w) {
        return addRenderableWidget(w);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Render
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        btns.clear();
        hideAll();
        lastMX = mx;
        lastMY = my;

        g.fill(0, 0, width, height, C_BG);
        renderTopBar(g, mx, my);
        renderPanel(g, mx, my);
        super.render(g, mx, my, partial);
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private void renderTopBar(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, width, TOP_H, C_BAR);
        g.fill(0, TOP_H - 1, width, TOP_H, C_ACCENT);

        String title = "Edit Placement #" + placementIndex + "  —  " +
                (pd().machine.isEmpty() ? "unnamed" : pd().machine);
        g.drawCenteredString(font, title, width / 2, (TOP_H - 8) / 2, C_DIM);

        int rx = width - 4;
        rx = topBtn(g, mx, my, rx, "\u2190 Back", C_BTN, this::goBack);
    }

    private int topBtn(GuiGraphics g, int mx, int my, int rx, String label, int base, Runnable action) {
        int w = font.width(label) + 10;
        int x = rx - w, y = 3, h = TOP_H - 6;
        boolean hov = over(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, hov ? C_BTN_HOV : base);
        if (hov) {
            g.fill(x, y, x + w, y + 1, C_ACCENT);
            g.fill(x, y + h - 1, x + w, y + h, C_ACCENT);
        }
        g.drawString(font, label, x + 5, (TOP_H - 8) / 2, hov ? C_ACCENT : C_TEXT, false);
        btns.add(new Btn(x, y, w, h, action));
        return x - 4;
    }

    // ── Main panel ────────────────────────────────────────────────────────────

    private void renderPanel(GuiGraphics g, int mx, int my) {
        int pw = Math.min(PANEL_W, width - 20);
        int px = (width - pw) / 2;
        int py = TOP_H + 8;
        int cy = py;

        g.fill(px, py, px + pw, height - 8, C_PANEL);
        g.fill(px, py, px + pw, py + 1, C_ACCENT);
        cy += 8;

        // ── Machine ID ───────────────────────────────────────────────────────
        g.drawString(font, "Machine ID:", px + 8, cy + 2, C_DIM, false);
        cy += 12;
        place(machineIdBox, px + 8, cy, pw - 60, 12);

        // Apply button
        boolean applyHov = over(mx, my, px + pw - 48, cy, 40, 12);
        g.fill(px + pw - 48, cy, px + pw - 8, cy + 12, applyHov ? C_BTN_HOV : C_BTN);
        if (applyHov) g.fill(px + pw - 48, cy, px + pw - 8, cy + 1, C_GREEN);
        g.drawString(font, "\u2713 Apply", px + pw - 44, cy + 2, applyHov ? C_GREEN : C_DIM, false);
        btns.add(new Btn(px + pw - 48, cy, 40, 12, this::applyMachineId));
        cy += 16;

        // Offset
        g.drawString(font, "Offset:", px + 8, cy + 2, C_DIM, false);
        int ox = px + 8 + font.width("Offset:") + 4;
        g.drawString(font, "X", ox, cy + 2, C_DIM, false);
        place(offsetXBox, ox + 8, cy, 34, 12);
        ox += 46;
        g.drawString(font, "Y", ox, cy + 2, C_DIM, false);
        place(offsetYBox, ox + 8, cy, 34, 12);
        ox += 46;
        g.drawString(font, "Z", ox, cy + 2, C_DIM, false);
        place(offsetZBox, ox + 8, cy, 34, 12);
        ox += 46;

        boolean offHov = over(mx, my, ox, cy, 50, 12);
        g.fill(ox, cy, ox + 50, cy + 12, offHov ? C_BTN_HOV : C_BTN);
        g.drawString(font, "\u2713 Move", ox + 4, cy + 2, offHov ? C_ACCENT : C_DIM, false);
        btns.add(new Btn(ox, cy, 50, 12, this::applyOffset));
        cy += 20;

        // ── Divider ──────────────────────────────────────────────────────────
        g.fill(px + 8, cy, px + pw - 8, cy + 1, 0x33FFFFFF);
        cy += 8;

        // ── Recipe item conditions ────────────────────────────────────────────
        g.drawString(font, "Recipe Item Conditions", px + 8, cy + 2, C_ACCENT, false);
        g.drawString(font, "(displayed in scene viewer alongside this machine)", px + 8, cy + 12, C_DIM, false);
        cy += 24;

        PhantasiaSceneData.PlacementData p = pd();

        // Existing items
        for (int ii = 0; ii < p.items.size(); ii++) {
            cy = renderItemRow(g, mx, my, px, cy, pw, p, ii);
        }

        if (p.items.isEmpty()) {
            g.drawString(font, "No items yet.", px + 12, cy + 2, C_DIM, false);
            cy += 14;
        }

        // ── Add item form ─────────────────────────────────────────────────────
        cy += 4;
        g.fill(px + 4, cy - 2, px + pw - 4, cy + 1, 0x22FFFFFF);
        cy += 6;
        g.drawString(font, "+ Add Item", px + 8, cy, C_ACCENT, false);
        cy += 12;

        // Type selector for new item
        int bx = px + 8;
        for (int ti = 0; ti < TYPES.length; ti++) {
            String t = TYPES[ti];
            String tl = TYPE_LABELS[ti];
            int tw = font.width(tl) + 10;
            boolean sel = t.equals(newItemType);
            int ac = PhantasiaSceneData.ItemConditionData.staticAccentFor(t);
            boolean hov = over(mx, my, bx, cy, tw, 14);
            g.fill(bx, cy, bx + tw, cy + 14, sel ? C_BTN_ACT : (hov ? C_BTN_HOV : C_BTN));
            if (sel) g.fill(bx, cy, bx + tw, cy + 1, ac);
            g.drawString(font, tl, bx + 5, cy + 3, sel ? ac : C_TEXT, false);
            final String ft = t;
            btns.add(new Btn(bx, cy, tw, 14, () -> newItemType = ft));
            bx += tw + 3;
        }
        cy += 18;

        // Item ID row
        g.drawString(font, "Item:", px + 8, cy + 2, C_DIM, false);
        place(itemIdBox, px + 8 + font.width("Item:") + 4, cy, pw - 20 - font.width("Item:") - 4, 12);
        cy += 16;

        // Count + Label row
        g.drawString(font, "Count:", px + 8, cy + 2, C_DIM, false);
        int cntX = px + 8 + font.width("Count:") + 4;
        place(itemCountBox, cntX, cy, 34, 12);
        int lblX = cntX + 38;
        g.drawString(font, "Label:", lblX, cy + 2, C_DIM, false);
        place(itemLabelBox, lblX + font.width("Label:") + 4, cy, pw - (lblX - px) - font.width("Label:") - 8, 12);
        cy += 16;

        // Track row
        g.drawString(font, "Track:", px + 8, cy + 2, C_DIM, false);
        int tbx = px + 8 + font.width("Track:") + 4;
        for (int ti = 0; ti < TRACKS.length; ti++) {
            String t = TRACKS[ti];
            String tl = TRACK_LABELS[ti];
            int tw = font.width(tl) + 8;
            boolean sel = t.equals(newItemTrack);
            boolean hov = over(mx, my, tbx, cy, tw, 12);
            g.fill(tbx, cy, tbx + tw, cy + 12, sel ? C_BTN_ACT : (hov ? C_BTN_HOV : C_BTN));
            if (sel) g.fill(tbx, cy, tbx + tw, cy + 1, C_ACCENT);
            g.drawString(font, tl, tbx + 4, cy + 2, sel ? C_ACCENT : C_TEXT, false);
            final String ft = t;
            btns.add(new Btn(tbx, cy, tw, 12, () -> newItemTrack = ft));
            tbx += tw + 2;
        }
        // Duration box (only relevant when track != none)
        if (!"none".equals(newItemTrack)) {
            g.drawString(font, "ticks:", tbx + 2, cy + 2, C_DIM, false);
            place(itemDurationBox, tbx + font.width("ticks:") + 6, cy, 34, 12);
        } else {
            itemDurationBox.visible = false;
        }
        cy += 16;

        // Description
        g.drawString(font, "Desc:", px + 8, cy + 2, C_DIM, false);
        place(itemDescBox, px + 8 + font.width("Desc:") + 4, cy, pw - 20 - font.width("Desc:") - 4, 12);
        cy += 16;

        // Microscene ID
        g.drawString(font, "Scene:", px + 8, cy + 2, C_DIM, false);
        place(itemMicrosceneBox, px + 8 + font.width("Scene:") + 4, cy, pw - 20 - font.width("Scene:") - 4, 12);
        cy += 16;

        // Add button
        int addBtnW = pw - 16;
        boolean addHov = over(mx, my, px + 8, cy, addBtnW, 14);
        g.fill(px + 8, cy, px + 8 + addBtnW, cy + 14, addHov ? C_BTN_HOV : C_BTN);
        if (addHov) g.fill(px + 8, cy, px + 8 + addBtnW, cy + 1, C_GREEN);
        g.drawCenteredString(font, "\u2713 Add Item", px + 8 + addBtnW / 2, cy + 3, addHov ? C_GREEN : C_TEXT);
        btns.add(new Btn(px + 8, cy, addBtnW, 14, this::commitAddItem));
    }

    // ── Single item row ───────────────────────────────────────────────────────

    private int renderItemRow(GuiGraphics g, int mx, int my,
                              int px, int cy, int pw,
                              PhantasiaSceneData.PlacementData p, int ii) {
        PhantasiaSceneData.ItemConditionData item = p.items.get(ii);
        boolean editing = (editingItem == ii);
        int ac = item.accentColor();

        // Row background
        boolean rowHov = over(mx, my, px + 4, cy, pw - 30, 14);
        g.fill(px + 4, cy, px + pw - 4, cy + 14,
                editing ? C_BTN_ACT : (rowHov ? C_BTN_HOV : C_BTN));
        g.fill(px + 4, cy, px + 5, cy + 14, ac);

        // Type badge
        String badge = item.type == null ? "in" : switch (item.type.toLowerCase(java.util.Locale.ROOT)) {
            case "output" -> "out";
            case "catalyst" -> "cat";
            default -> "in";
        };
        int badgeW = font.width(badge) + 6;
        g.fill(px + 7, cy + 2, px + 7 + badgeW, cy + 12, ac & 0x44FFFFFF | 0x44000000);
        g.drawString(font, badge, px + 10, cy + 3, ac, false);

        // Name / label
        String nm = item.item.contains(":") ? item.item.split(":")[1].replace('_', ' ') : item.item;
        String disp = (item.label != null && !item.label.isBlank()) ? item.label + "  (" + nm + ")" : nm;
        g.drawString(font, trunc(disp, pw - badgeW - 60), px + 10 + badgeW, cy + 3, C_TEXT, false);

        // Count
        if (item.count > 1)
            g.drawString(font, "x" + item.count, px + pw - 46, cy + 3, C_DIM, false);

        // Track indicator
        if (item.track != null && !"none".equals(item.track)) {
            String trackLabel = switch (item.track) {
                case "left" -> "→";
                case "right" -> "←";
                case "up" -> "↑";
                case "down" -> "↓";
                case "pulse" -> "~";
                default -> "?";
            };
            int tix = px + pw - 46 - font.width(trackLabel) - 4;
            g.drawString(font, trackLabel, tix, cy + 3, C_ACCENT, false);
        }

        // Remove button
        int rmX = px + pw - 26, rmY = cy + 1;
        boolean rmH = over(mx, my, rmX, rmY, 18, 12);
        g.fill(rmX, rmY, rmX + 18, rmY + 12, rmH ? C_BTN_HOV : C_BTN);
        g.drawString(font, "\u2715", rmX + 5, rmY + 2, rmH ? C_RED : C_DIM, false);
        final int fii = ii;
        btns.add(new Btn(rmX, rmY, 18, 12, () -> {
            parent.checkpoint();
            p.items.remove(fii);
            if (editingItem == fii) editingItem = -1;
            else if (editingItem > fii) editingItem--;
            parent.dirty = true;
        }));

        // Click to expand/collapse edit
        btns.add(new Btn(px + 4, cy, pw - 30, 14, () -> {
            if (editingItem == fii) {
                editingItem = -1;
            } else {
                editingItem = fii;
                populateEditBoxes(p);
            }
        }));
        cy += 15;

        // Inline edit form
        if (editing) {
            cy = renderItemEditForm(g, mx, my, px, cy, pw, p, ii);
        }
        return cy;
    }

    /** Renders the inline edit form for an existing item. */
    private int renderItemEditForm(GuiGraphics g, int mx, int my,
                                   int px, int cy, int pw,
                                   PhantasiaSceneData.PlacementData p, int ii) {
        PhantasiaSceneData.ItemConditionData item = p.items.get(ii);
        g.fill(px + 4, cy, px + pw - 4, cy + 1, 0x22FFFFFF);
        cy += 4;

        // Type selector
        int bx = px + 8;
        for (int ti = 0; ti < TYPES.length; ti++) {
            String t = TYPES[ti];
            String tl = TYPE_LABELS[ti];
            int tw = font.width(tl) + 10;
            boolean sel = t.equals(item.type);
            int ac = PhantasiaSceneData.ItemConditionData.staticAccentFor(t);
            boolean hov = over(mx, my, bx, cy, tw, 12);
            g.fill(bx, cy, bx + tw, cy + 12, sel ? C_BTN_ACT : (hov ? C_BTN_HOV : C_BTN));
            if (sel) g.fill(bx, cy, bx + tw, cy + 1, ac);
            g.drawString(font, tl, bx + 5, cy + 2, sel ? ac : C_TEXT, false);
            final String ft = t;
            btns.add(new Btn(bx, cy, tw, 12, () -> {
                parent.checkpoint();
                item.type = ft;
                parent.dirty = true;
            }));
            bx += tw + 3;
        }
        cy += 16;

        // Item ID
        g.drawString(font, "Item:", px + 8, cy + 2, C_DIM, false);
        place(itemIdBox, px + 8 + font.width("Item:") + 4, cy, pw - 20 - font.width("Item:") - 4, 12);
        cy += 16;

        // Count + Label
        g.drawString(font, "Count:", px + 8, cy + 2, C_DIM, false);
        int cntX = px + 8 + font.width("Count:") + 4;
        place(itemCountBox, cntX, cy, 34, 12);
        int lblX = cntX + 38;
        g.drawString(font, "Label:", lblX, cy + 2, C_DIM, false);
        place(itemLabelBox, lblX + font.width("Label:") + 4, cy, pw - (lblX - px) - font.width("Label:") - 8, 12);
        cy += 16;

        // Track selector
        g.drawString(font, "Track:", px + 8, cy + 2, C_DIM, false);
        int tbx = px + 8 + font.width("Track:") + 4;
        for (int ti = 0; ti < TRACKS.length; ti++) {
            String t = TRACKS[ti];
            String tl = TRACK_LABELS[ti];
            int tw = font.width(tl) + 8;
            boolean sel = t.equals(item.track == null ? "none" : item.track);
            boolean hov = over(mx, my, tbx, cy, tw, 12);
            g.fill(tbx, cy, tbx + tw, cy + 12, sel ? C_BTN_ACT : (hov ? C_BTN_HOV : C_BTN));
            if (sel) g.fill(tbx, cy, tbx + tw, cy + 1, C_ACCENT);
            g.drawString(font, tl, tbx + 4, cy + 2, sel ? C_ACCENT : C_TEXT, false);
            final String ft = t;
            btns.add(new Btn(tbx, cy, tw, 12, () -> {
                parent.checkpoint();
                item.track = ft;
                parent.dirty = true;
            }));
            tbx += tw + 2;
        }
        // Duration box — only shown when a track is selected
        boolean hasTrack = item.track != null && !"none".equals(item.track);
        if (hasTrack) {
            g.drawString(font, "ticks:", tbx + 2, cy + 2, C_DIM, false);
            place(itemDurationBox, tbx + font.width("ticks:") + 6, cy, 34, 12);
        } else {
            itemDurationBox.visible = false;
        }
        cy += 16;

        // Description
        g.drawString(font, "Desc:", px + 8, cy + 2, C_DIM, false);
        place(itemDescBox, px + 8 + font.width("Desc:") + 4, cy, pw - 20 - font.width("Desc:") - 4, 12);
        cy += 16;

        // Microscene ID
        g.drawString(font, "Scene:", px + 8, cy + 2, C_DIM, false);
        place(itemMicrosceneBox, px + 8 + font.width("Scene:") + 4, cy, pw - 20 - font.width("Scene:") - 4, 12);
        cy += 16;

        // Apply button
        int applyW = pw - 16;
        boolean applyHov = over(mx, my, px + 8, cy, applyW, 12);
        g.fill(px + 8, cy, px + 8 + applyW, cy + 12, applyHov ? C_BTN_HOV : C_BTN);
        if (applyHov) g.fill(px + 8, cy, px + 8 + applyW, cy + 1, C_ACCENT);
        g.drawCenteredString(font, "\u2713 Apply & close", px + 8 + applyW / 2, cy + 2,
                applyHov ? C_ACCENT : C_TEXT);
        btns.add(new Btn(px + 8, cy, applyW, 12, () -> {
            parent.checkpoint();
            String newId = itemIdBox.getValue().trim();
            if (!newId.isEmpty()) item.item = newId;
            try {
                item.count = Math.max(1, Integer.parseInt(itemCountBox.getValue().trim()));
            } catch (NumberFormatException ignored) {}
            String lbl = itemLabelBox.getValue().trim();
            item.label = lbl.isEmpty() ? null : lbl;
            if (item.track != null && !"none".equals(item.track)) {
                try {
                    item.trackDurationTicks = Math.max(1, Integer.parseInt(itemDurationBox.getValue().trim()));
                } catch (NumberFormatException ignored) {}
            }
            String desc = itemDescBox.getValue().trim();
            item.description = desc.isEmpty() ? null : desc;
            String sceneId = itemMicrosceneBox.getValue().trim();
            item.microsceneId = sceneId.isEmpty() ? null : sceneId;
            parent.dirty = true;
            editingItem = -1;
            goBack();
        }));
        cy += 16;
        return cy;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Actions
    // ─────────────────────────────────────────────────────────────────────────

    private void applyMachineId() {
        String id = machineIdBox.getValue().trim();
        if (id.isEmpty()) return;
        parent.checkpoint();
        pd().machine = id;
        parent.dirty = true;
        parent.rebuildWorld();
    }

    private void applyOffset() {
        parent.checkpoint();
        PhantasiaSceneData.PlacementData p = pd();
        p.x = parseIntOrZero(offsetXBox.getValue());
        p.y = parseIntOrZero(offsetYBox.getValue());
        p.z = parseIntOrZero(offsetZBox.getValue());
        parent.dirty = true;
        parent.rebuildWorld();
    }

    private void commitAddItem() {
        String id = itemIdBox.getValue().trim();
        if (id.isEmpty()) return;
        int cnt = 1;
        try {
            cnt = Math.max(1, Integer.parseInt(itemCountBox.getValue().trim()));
        } catch (NumberFormatException ignored) {}
        String lbl = itemLabelBox.getValue().trim();
        String desc = itemDescBox.getValue().trim();
        String scId = itemMicrosceneBox.getValue().trim();
        int dur = 20;
        try {
            dur = Math.max(1, Integer.parseInt(itemDurationBox.getValue().trim()));
        } catch (NumberFormatException ignored) {}

        parent.checkpoint();
        PhantasiaSceneData.ItemConditionData item = new PhantasiaSceneData.ItemConditionData(id, cnt,
                lbl.isEmpty() ? null : lbl, newItemType);
        item.track = newItemTrack;
        item.trackDurationTicks = dur;
        item.description = desc.isEmpty() ? null : desc;
        item.microsceneId = scId.isEmpty() ? null : scId;
        pd().items.add(item);

        // Reset add form
        itemIdBox.setValue("");
        itemCountBox.setValue("");
        itemLabelBox.setValue("");
        itemDurationBox.setValue("");
        itemDescBox.setValue("");
        itemMicrosceneBox.setValue("");
        parent.dirty = true;
    }

    private void populateEditBoxes(PhantasiaSceneData.PlacementData p) {
        if (editingItem < 0 || editingItem >= p.items.size()) return;
        PhantasiaSceneData.ItemConditionData it = p.items.get(editingItem);
        itemIdBox.setValue(it.item != null ? it.item : "");
        itemCountBox.setValue(it.count > 1 ? String.valueOf(it.count) : "");
        itemLabelBox.setValue(it.label != null ? it.label : "");
        itemDurationBox.setValue(it.trackDurationTicks != 20 ? String.valueOf(it.trackDurationTicks) : "");
        itemDescBox.setValue(it.description != null ? it.description : "");
        itemMicrosceneBox.setValue(it.microsceneId != null ? it.microsceneId : "");
    }

    private void goBack() {
        Minecraft.getInstance().setScreen(parent);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Input
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Btn b : btns) if (b.hit(mx, my)) {
            b.action().run();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (getFocused() != null && getFocused().keyPressed(kc, sc, mod)) return true;
        if (kc == GLFW.GLFW_KEY_ESCAPE) {
            goBack();
            return true;
        }
        return super.keyPressed(kc, sc, mod);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void hideAll() {
        for (var box : List.of(machineIdBox, offsetXBox, offsetYBox, offsetZBox,
                itemIdBox, itemCountBox, itemLabelBox, itemDurationBox,
                itemDescBox, itemMicrosceneBox)) {
            if (box != null) {
                box.visible = false;
                box.active = false;
            }
        }
    }

    private void place(EditBox box, int x, int y, int w, int h) {
        box.setX(x);
        box.setY(y);
        box.setWidth(w);
        box.setHeight(h);
        box.visible = true;
        box.active = true;
    }

    private boolean over(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private String trunc(String s, int maxPx) {
        if (s == null) return "";
        while (font.width(s) > maxPx && s.length() > 2) s = s.substring(0, s.length() - 2) + "\u2026";
        return s;
    }

    private static int parseIntOrZero(String v) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
