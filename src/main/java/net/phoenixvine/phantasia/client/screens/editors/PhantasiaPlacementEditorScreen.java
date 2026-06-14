package net.phoenixvine.phantasia.client.screens.editors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData;

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
public class PhantasiaPlacementEditorScreen extends PhantasiaEditorScreen {

    // ── Theme ─────────────────────────────────────────────────────────────────

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
    /** Index of item being edited in the existing-item form, -1 = none. */
    private int editingItem = -1;
    /**
     * Pending type/track for the add-new form. These are separate from the
     * editing-form state so the two forms never bleed into each other.
     */
    private String newItemType = "input";
    private String newItemTrack = "none";

    // ── Pending tooltip ───────────────────────────────────────────────────────

    // ── Widgets: machine / offset (always visible in the panel) ──────────────
    private EditBox machineIdBox;
    private EditBox offsetXBox, offsetYBox, offsetZBox;

    // ── Widgets: add-new form (separate set, never shared with edit form) ─────
    private EditBox addItemIdBox;
    private EditBox addItemCountBox;
    private EditBox addItemLabelBox;
    private EditBox addItemDurationBox;
    private EditBox addItemDescBox;
    private EditBox addItemMicrosceneBox;

    // ── Widgets: inline edit form (populated from the selected item) ──────────
    private EditBox editItemIdBox;
    private EditBox editItemCountBox;
    private EditBox editItemLabelBox;
    private EditBox editItemDurationBox;
    private EditBox editItemDescBox;
    private EditBox editItemMicrosceneBox;

    // ── Button list ───────────────────────────────────────────────────────────

    private int lastMX, lastMY;

    // ── Scroll ────────────────────────────────────────────────────────────────
    /** Current scroll offset in pixels (positive = scrolled down). */
    private int scrollY = 0;
    /** Total content height as measured during the last render pass. */
    private int contentHeight = 0;

    // ─────────────────────────────────────────────────────────────────────────

    public PhantasiaPlacementEditorScreen(PhantasiaSceneEditorScreen parent,
                                          PhantasiaSceneData sceneData,
                                          int placementIndex) {
        super(Component.literal("Edit Placement"));
        this.parent = parent;
        this.sceneData = sceneData;
        this.placementIndex = placementIndex;
    }

    @Override
    protected void hideAllInputs() {}

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

        // Machine / offset widgets
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

        // Add-new form widgets
        addItemIdBox = addW(new EditBox(font, 0, 0, 200, 12, Component.empty()));
        addItemIdBox.setMaxLength(128);
        addItemIdBox.setHint(Component.literal("namespace:item_id"));

        addItemCountBox = makeIntBox();
        addItemCountBox.setHint(Component.literal("1"));

        addItemLabelBox = addW(new EditBox(font, 0, 0, 100, 12, Component.empty()));
        addItemLabelBox.setMaxLength(32);
        addItemLabelBox.setHint(Component.literal("auto"));

        addItemDurationBox = makeDurationBox();
        addItemDescBox = makeDescBox();
        addItemMicrosceneBox = makeMicrosceneBox();

        // Edit form widgets (identical layout, independent instances)
        editItemIdBox = addW(new EditBox(font, 0, 0, 200, 12, Component.empty()));
        editItemIdBox.setMaxLength(128);
        editItemIdBox.setHint(Component.literal("namespace:item_id"));

        editItemCountBox = makeIntBox();
        editItemCountBox.setHint(Component.literal("1"));

        editItemLabelBox = addW(new EditBox(font, 0, 0, 100, 12, Component.empty()));
        editItemLabelBox.setMaxLength(32);
        editItemLabelBox.setHint(Component.literal("auto"));

        editItemDurationBox = makeDurationBox();
        editItemDescBox = makeDescBox();
        editItemMicrosceneBox = makeMicrosceneBox();

        hideAll();
    }

    private EditBox makeIntBox() {
        EditBox b = addW(new EditBox(font, 0, 0, 34, 12, Component.empty()));
        b.setMaxLength(5);
        b.setFilter(s -> s.matches("-?\\d*"));
        return b;
    }

    private EditBox makeDurationBox() {
        EditBox b = addW(new EditBox(font, 0, 0, 34, 12, Component.empty()));
        b.setMaxLength(4);
        b.setFilter(s -> s.matches("\\d*"));
        b.setHint(Component.literal("20"));
        return b;
    }

    private EditBox makeDescBox() {
        EditBox b = addW(new EditBox(font, 0, 0, 200, 12, Component.empty()));
        b.setMaxLength(256);
        b.setHint(Component.literal("Optional description..."));
        return b;
    }

    private EditBox makeMicrosceneBox() {
        EditBox b = addW(new EditBox(font, 0, 0, 200, 12, Component.empty()));
        b.setMaxLength(128);
        b.setHint(Component.literal("phantasia:scene_id (optional)"));
        return b;
    }


    // ─────────────────────────────────────────────────────────────────────────
    // Render
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        btns.clear();
        hideAll();
        pendingTooltip = null;
        lastMX = mx;
        lastMY = my;

        g.fill(0, 0, width, height, C_BG);
        renderTopBar(g, mx, my);
        renderPanel(g, mx, my);
        super.render(g, mx, my, partial);

        // Floating tooltip — drawn last so it is always on top
        if (pendingTooltip != null) {
            int tw = font.width(pendingTooltip) + 8;
            int tx = Math.min(mx + 12, width - tw - 2);
            int ty = Math.max(my - 18, TOP_BAR_H + 2);
            g.fill(tx - 2, ty - 2, tx + tw + 2, ty + 12, 0xDD070712);
            g.fill(tx - 2, ty - 2, tx + tw + 2, ty - 1, C_ACCENT);
            g.drawString(font, pendingTooltip, tx + 4, ty + 2, 0xFFDDDDDD, false);
        }
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private void renderTopBar(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, width, TOP_BAR_H, C_BAR);
        g.fill(0, TOP_BAR_H - 1, width, TOP_BAR_H, C_ACCENT);

        String title = "Edit Placement #" + placementIndex + "  \u2014  " +
                (pd().machine.isEmpty() ? "unnamed" : pd().machine);
        g.drawCenteredString(font, title, width / 2, (TOP_BAR_H - 8) / 2, C_DIM);

        int rx = width - 4;
        rx = topBtn(g, mx, my, rx, "\u2190 Back", C_BTN, "Return to the scene editor", this::goBack);
    }


    // ── Main panel ────────────────────────────────────────────────────────────

    private void renderPanel(GuiGraphics g, int mx, int my) {
        int pw = Math.min(PANEL_W, width - 20);
        int px = (width - pw) / 2;
        int py = TOP_BAR_H + 8;

        // Visible area for clipping (content scrolls within this)
        int clipTop = py;
        int clipBottom = height - 8;
        int clipH = clipBottom - clipTop;

        // Constrain scroll
        int maxScroll = Math.max(0, contentHeight - clipH);
        scrollY = Math.max(0, Math.min(scrollY, maxScroll));

        // We render at a virtual y that is offset by scroll
        int cy = clipTop - scrollY;

        // Background — covers the full visible clip area regardless of content
        g.fill(px, clipTop, px + pw, clipBottom, C_PANEL);
        g.fill(px, clipTop, px + pw, clipTop + 1, C_ACCENT);

        // ── Machine ID ───────────────────────────────────────────────────────
        cy += 8;
        drawIfVisible(g, "Machine ID:", px + 8, cy + 2, C_DIM, clipTop, clipBottom);
        cy += 12;

        if (inClip(cy, clipTop, clipBottom)) {
            place(machineIdBox, px + 8, cy, pw - 60, 12);
            boolean applyHov = isOver(mx, my, px + pw - 48, cy, 40, 12);
            g.fill(px + pw - 48, cy, px + pw - 8, cy + 12, applyHov ? C_BTN_HOV : C_BTN);
            if (applyHov) {
                g.fill(px + pw - 48, cy, px + pw - 8, cy + 1, C_GREEN);
                pendingTooltip = "Apply new machine ID and rebuild the scene";
            }
            g.drawString(font, "\u2713 Apply", px + pw - 44, cy + 2, applyHov ? C_GREEN : C_DIM, false);
            btns.add(new Btn(px + pw - 48, cy, 40, 12, this::applyMachineId));
        }
        cy += 16;

        // Offset
        if (inClip(cy, clipTop, clipBottom)) {
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
            boolean offHov = isOver(mx, my, ox, cy, 50, 12);
            g.fill(ox, cy, ox + 50, cy + 12, offHov ? C_BTN_HOV : C_BTN);
            g.drawString(font, "\u2713 Move", ox + 4, cy + 2, offHov ? C_ACCENT : C_DIM, false);
            if (offHov) pendingTooltip = "Apply new XYZ offset and rebuild the scene";
            btns.add(new Btn(ox, cy, 50, 12, this::applyOffset));
        }
        cy += 20;

        // ── Divider ──────────────────────────────────────────────────────────
        if (inClip(cy, clipTop, clipBottom))
            g.fill(px + 8, cy, px + pw - 8, cy + 1, 0x33FFFFFF);
        cy += 8;

        // ── Recipe item conditions ────────────────────────────────────────────
        drawIfVisible(g, "Recipe Item Conditions", px + 8, cy + 2, C_ACCENT, clipTop, clipBottom);
        cy += 12;
        drawIfVisible(g, "(displayed in scene viewer alongside this machine)", px + 8, cy, C_DIM, clipTop, clipBottom);
        cy += 14;

        PhantasiaSceneData.PlacementData p = pd();

        // Existing items
        for (int ii = 0; ii < p.items.size(); ii++) {
            cy = renderItemRow(g, mx, my, px, cy, pw, p, ii, clipTop, clipBottom);
        }

        if (p.items.isEmpty()) {
            drawIfVisible(g, "No items yet.", px + 12, cy + 2, C_DIM, clipTop, clipBottom);
            cy += 14;
        }

        // ── Add item form ─────────────────────────────────────────────────────
        cy += 4;
        if (inClip(cy, clipTop, clipBottom))
            g.fill(px + 4, cy - 2, px + pw - 4, cy + 1, 0x22FFFFFF);
        cy += 6;
        drawIfVisible(g, "+ Add Item", px + 8, cy, C_ACCENT, clipTop, clipBottom);
        cy += 12;

        // Type selector for new item
        if (inClip(cy, clipTop, clipBottom)) {
            int bx = px + 8;
            for (int ti = 0; ti < TYPES.length; ti++) {
                String t = TYPES[ti], tl = TYPE_LABELS[ti];
                int tw = font.width(tl) + 10;
                boolean sel = t.equals(newItemType);
                int ac = PhantasiaSceneData.ItemConditionData.staticAccentFor(t);
                boolean hov = isOver(mx, my, bx, cy, tw, 14);
                g.fill(bx, cy, bx + tw, cy + 14, sel ? C_BTN_ACT : (hov ? C_BTN_HOV : C_BTN));
                if (sel) g.fill(bx, cy, bx + tw, cy + 1, ac);
                g.drawString(font, tl, bx + 5, cy + 3, sel ? ac : C_TEXT, false);
                final String ft = t;
                btns.add(new Btn(bx, cy, tw, 14, () -> newItemType = ft));
                bx += tw + 3;
            }
        }
        cy += 18;

        // Item ID row
        if (inClip(cy, clipTop, clipBottom)) {
            g.drawString(font, "Item:", px + 8, cy + 2, C_DIM, false);
            place(addItemIdBox, px + 8 + font.width("Item:") + 4, cy, pw - 20 - font.width("Item:") - 4, 12);
            if (isOver(mx, my, px + 8, cy, pw - 16, 12))
                pendingTooltip = "Namespaced item ID (e.g. minecraft:iron_ingot)";
        }
        cy += 16;

        // Count + Label row
        if (inClip(cy, clipTop, clipBottom)) {
            g.drawString(font, "Count:", px + 8, cy + 2, C_DIM, false);
            int cntX = px + 8 + font.width("Count:") + 4;
            place(addItemCountBox, cntX, cy, 34, 12);
            int lblX = cntX + 38;
            g.drawString(font, "Label:", lblX, cy + 2, C_DIM, false);
            place(addItemLabelBox, lblX + font.width("Label:") + 4, cy, pw - (lblX - px) - font.width("Label:") - 8,
                    12);
            if (isOver(mx, my, px + 8, cy, pw - 16, 12))
                pendingTooltip = "Optional display label (leave blank to auto-generate from item name)";
        }
        cy += 16;

        // Track selector
        if (inClip(cy, clipTop, clipBottom)) {
            g.drawString(font, "Track:", px + 8, cy + 2, C_DIM, false);
            int tbx = px + 8 + font.width("Track:") + 4;
            for (int ti = 0; ti < TRACKS.length; ti++) {
                String t = TRACKS[ti], tl = TRACK_LABELS[ti];
                int tw = font.width(tl) + 8;
                boolean sel = t.equals(newItemTrack);
                boolean hov = isOver(mx, my, tbx, cy, tw, 12);
                g.fill(tbx, cy, tbx + tw, cy + 12, sel ? C_BTN_ACT : (hov ? C_BTN_HOV : C_BTN));
                if (sel) g.fill(tbx, cy, tbx + tw, cy + 1, C_ACCENT);
                g.drawString(font, tl, tbx + 4, cy + 2, sel ? C_ACCENT : C_TEXT, false);
                final String ft = t;
                btns.add(new Btn(tbx, cy, tw, 12, () -> newItemTrack = ft));
                tbx += tw + 2;
            }
            if (!"none".equals(newItemTrack)) {
                g.drawString(font, "ticks:", tbx + 2, cy + 2, C_DIM, false);
                place(addItemDurationBox, tbx + font.width("ticks:") + 6, cy, 34, 12);
            } else {
                addItemDurationBox.visible = false;
            }
        }
        cy += 16;

        // Description
        if (inClip(cy, clipTop, clipBottom)) {
            g.drawString(font, "Desc:", px + 8, cy + 2, C_DIM, false);
            place(addItemDescBox, px + 8 + font.width("Desc:") + 4, cy, pw - 20 - font.width("Desc:") - 4, 12);
        }
        cy += 16;

        // Microscene ID
        if (inClip(cy, clipTop, clipBottom)) {
            g.drawString(font, "Scene:", px + 8, cy + 2, C_DIM, false);
            place(addItemMicrosceneBox, px + 8 + font.width("Scene:") + 4, cy, pw - 20 - font.width("Scene:") - 4, 12);
            if (isOver(mx, my, px + 8, cy, pw - 16, 12))
                pendingTooltip = "Optional microscene ID to open when the player clicks this item";
        }
        cy += 16;

        // Add button
        if (inClip(cy, clipTop, clipBottom)) {
            int addBtnW = pw - 16;
            boolean addHov = isOver(mx, my, px + 8, cy, addBtnW, 14);
            g.fill(px + 8, cy, px + 8 + addBtnW, cy + 14, addHov ? C_BTN_HOV : C_BTN);
            if (addHov) {
                g.fill(px + 8, cy, px + 8 + addBtnW, cy + 1, C_GREEN);
                pendingTooltip = "Add this item to the placement";
            }
            g.drawCenteredString(font, "\u2713 Add Item", px + 8 + addBtnW / 2, cy + 3, addHov ? C_GREEN : C_TEXT);
            btns.add(new Btn(px + 8, cy, addBtnW, 14, this::commitAddItem));
        }
        cy += 18;

        // Record total content height for scroll clamping next frame
        contentHeight = (cy + scrollY) - clipTop;

        // Scrollbar
        if (contentHeight > clipH) {
            int sbX = px + pw - 4;
            int sbH = clipH;
            int thumbH = Math.max(20, sbH * clipH / contentHeight);
            int thumbY = clipTop + (int) ((long) scrollY * (sbH - thumbH) / Math.max(1, contentHeight - clipH));
            g.fill(sbX, clipTop, sbX + 4, clipBottom, 0x22FFFFFF);
            g.fill(sbX, thumbY, sbX + 4, thumbY + thumbH, 0x88FFFFFF);
        }
    }

    // ── Single item row ───────────────────────────────────────────────────────

    private int renderItemRow(GuiGraphics g, int mx, int my,
                              int px, int cy, int pw,
                              PhantasiaSceneData.PlacementData p, int ii,
                              int clipTop, int clipBottom) {
        PhantasiaSceneData.ItemConditionData item = p.items.get(ii);
        boolean editing = (editingItem == ii);
        int ac = item.accentColor();

        if (inClip(cy, clipTop, clipBottom)) {
            boolean rowHov = isOver(mx, my, px + 4, cy, pw - 30, 14);
            g.fill(px + 4, cy, px + pw - 4, cy + 14,
                    editing ? C_BTN_ACT : (rowHov ? C_BTN_HOV : C_BTN));
            g.fill(px + 4, cy, px + 5, cy + 14, ac);

            String badge = item.type == null ? "in" : switch (item.type.toLowerCase(java.util.Locale.ROOT)) {
                case "output" -> "out";
                case "catalyst" -> "cat";
                default -> "in";
            };
            int badgeW = font.width(badge) + 6;
            g.fill(px + 7, cy + 2, px + 7 + badgeW, cy + 12, ac & 0x44FFFFFF | 0x44000000);
            g.drawString(font, badge, px + 10, cy + 3, ac, false);

            String nm = item.item.contains(":") ? item.item.split(":")[1].replace('_', ' ') : item.item;
            String disp = (item.label != null && !item.label.isBlank()) ? item.label + "  (" + nm + ")" : nm;
            g.drawString(font, trunc(disp, pw - badgeW - 60), px + 10 + badgeW, cy + 3, C_TEXT, false);

            if (item.count > 1)
                g.drawString(font, "x" + item.count, px + pw - 46, cy + 3, C_DIM, false);

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

            int rmX = px + pw - 26, rmY = cy + 1;
            boolean rmH = isOver(mx, my, rmX, rmY, 18, 12);
            g.fill(rmX, rmY, rmX + 18, rmY + 12, rmH ? C_BTN_HOV : C_BTN);
            g.drawString(font, "\u2715", rmX + 5, rmY + 2, rmH ? C_RED : C_DIM, false);
            if (rmH) pendingTooltip = "Remove this item";
            final int fii = ii;
            btns.add(new Btn(rmX, rmY, 18, 12, () -> {
                parent.checkpoint();
                p.items.remove(fii);
                if (editingItem == fii) editingItem = -1;
                else if (editingItem > fii) editingItem--;
                parent.dirty = true;
            }));

            if (rowHov && !rmH) pendingTooltip = editing ? "Collapse edit form" : "Expand to edit this item";
            btns.add(new Btn(px + 4, cy, pw - 30, 14, () -> {
                if (editingItem == fii) {
                    editingItem = -1;
                } else {
                    editingItem = fii;
                    populateEditBoxes(p);
                }
            }));
        }
        cy += 15;

        if (editing) {
            cy = renderItemEditForm(g, mx, my, px, cy, pw, p, ii, clipTop, clipBottom);
        }
        return cy;
    }

    /**
     * Renders the inline edit form for an existing item. Uses the dedicated
     * {@code editItem*} boxes so it can never clobber the add-new form state.
     */
    private int renderItemEditForm(GuiGraphics g, int mx, int my,
                                   int px, int cy, int pw,
                                   PhantasiaSceneData.PlacementData p, int ii,
                                   int clipTop, int clipBottom) {
        PhantasiaSceneData.ItemConditionData item = p.items.get(ii);

        if (inClip(cy, clipTop, clipBottom))
            g.fill(px + 4, cy, px + pw - 4, cy + 1, 0x22FFFFFF);
        cy += 4;

        // Type selector
        if (inClip(cy, clipTop, clipBottom)) {
            int bx = px + 8;
            for (int ti = 0; ti < TYPES.length; ti++) {
                String t = TYPES[ti], tl = TYPE_LABELS[ti];
                int tw = font.width(tl) + 10;
                boolean sel = t.equals(item.type);
                int ac = PhantasiaSceneData.ItemConditionData.staticAccentFor(t);
                boolean hov = isOver(mx, my, bx, cy, tw, 12);
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
        }
        cy += 16;

        // Item ID
        if (inClip(cy, clipTop, clipBottom)) {
            g.drawString(font, "Item:", px + 8, cy + 2, C_DIM, false);
            place(editItemIdBox, px + 8 + font.width("Item:") + 4, cy, pw - 20 - font.width("Item:") - 4, 12);
        }
        cy += 16;

        // Count + Label
        if (inClip(cy, clipTop, clipBottom)) {
            g.drawString(font, "Count:", px + 8, cy + 2, C_DIM, false);
            int cntX = px + 8 + font.width("Count:") + 4;
            place(editItemCountBox, cntX, cy, 34, 12);
            int lblX = cntX + 38;
            g.drawString(font, "Label:", lblX, cy + 2, C_DIM, false);
            place(editItemLabelBox, lblX + font.width("Label:") + 4, cy, pw - (lblX - px) - font.width("Label:") - 8,
                    12);
        }
        cy += 16;

        // Track selector
        if (inClip(cy, clipTop, clipBottom)) {
            g.drawString(font, "Track:", px + 8, cy + 2, C_DIM, false);
            int tbx = px + 8 + font.width("Track:") + 4;
            for (int ti = 0; ti < TRACKS.length; ti++) {
                String t = TRACKS[ti], tl = TRACK_LABELS[ti];
                int tw = font.width(tl) + 8;
                boolean sel = t.equals(item.track == null ? "none" : item.track);
                boolean hov = isOver(mx, my, tbx, cy, tw, 12);
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
            boolean hasTrack = item.track != null && !"none".equals(item.track);
            if (hasTrack) {
                g.drawString(font, "ticks:", tbx + 2, cy + 2, C_DIM, false);
                place(editItemDurationBox, tbx + font.width("ticks:") + 6, cy, 34, 12);
            } else {
                editItemDurationBox.visible = false;
            }
        }
        cy += 16;

        // Description
        if (inClip(cy, clipTop, clipBottom)) {
            g.drawString(font, "Desc:", px + 8, cy + 2, C_DIM, false);
            place(editItemDescBox, px + 8 + font.width("Desc:") + 4, cy, pw - 20 - font.width("Desc:") - 4, 12);
        }
        cy += 16;

        // Microscene ID
        if (inClip(cy, clipTop, clipBottom)) {
            g.drawString(font, "Scene:", px + 8, cy + 2, C_DIM, false);
            place(editItemMicrosceneBox, px + 8 + font.width("Scene:") + 4, cy, pw - 20 - font.width("Scene:") - 4, 12);
        }
        cy += 16;

        // Apply button — checkpoint BEFORE mutating, then close
        if (inClip(cy, clipTop, clipBottom)) {
            int applyW = pw - 16;
            boolean applyHov = isOver(mx, my, px + 8, cy, applyW, 12);
            g.fill(px + 8, cy, px + 8 + applyW, cy + 12, applyHov ? C_BTN_HOV : C_BTN);
            if (applyHov) {
                g.fill(px + 8, cy, px + 8 + applyW, cy + 1, C_ACCENT);
                pendingTooltip = "Save changes to this item";
            }
            g.drawCenteredString(font, "\u2713 Apply & close", px + 8 + applyW / 2, cy + 2,
                    applyHov ? C_ACCENT : C_TEXT);
            btns.add(new Btn(px + 8, cy, applyW, 12, () -> {
                // Checkpoint before any mutation so undo captures the pre-edit state
                parent.checkpoint();
                String newId = editItemIdBox.getValue().trim();
                if (!newId.isEmpty()) item.item = newId;
                try {
                    item.count = Math.max(1, Integer.parseInt(editItemCountBox.getValue().trim()));
                } catch (NumberFormatException ignored) {}
                String lbl = editItemLabelBox.getValue().trim();
                item.label = lbl.isEmpty() ? null : lbl;
                if (item.track != null && !"none".equals(item.track)) {
                    try {
                        item.trackDurationTicks = Math.max(1, Integer.parseInt(editItemDurationBox.getValue().trim()));
                    } catch (NumberFormatException ignored) {}
                }
                String desc = editItemDescBox.getValue().trim();
                item.description = desc.isEmpty() ? null : desc;
                String sceneId = editItemMicrosceneBox.getValue().trim();
                item.microsceneId = sceneId.isEmpty() ? null : sceneId;
                parent.dirty = true;
                editingItem = -1;
            }));
        }
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
        String id = addItemIdBox.getValue().trim();
        if (id.isEmpty()) return;
        int cnt = 1;
        try {
            cnt = Math.max(1, Integer.parseInt(addItemCountBox.getValue().trim()));
        } catch (NumberFormatException ignored) {}
        String lbl = addItemLabelBox.getValue().trim();
        String desc = addItemDescBox.getValue().trim();
        String scId = addItemMicrosceneBox.getValue().trim();
        int dur = 20;
        try {
            dur = Math.max(1, Integer.parseInt(addItemDurationBox.getValue().trim()));
        } catch (NumberFormatException ignored) {}

        parent.checkpoint();
        PhantasiaSceneData.ItemConditionData item = new PhantasiaSceneData.ItemConditionData(id, cnt,
                lbl.isEmpty() ? null : lbl, newItemType);
        item.track = newItemTrack;
        item.trackDurationTicks = dur;
        item.description = desc.isEmpty() ? null : desc;
        item.microsceneId = scId.isEmpty() ? null : scId;
        pd().items.add(item);

        // Reset add form only — the edit form is untouched
        addItemIdBox.setValue("");
        addItemCountBox.setValue("");
        addItemLabelBox.setValue("");
        addItemDurationBox.setValue("");
        addItemDescBox.setValue("");
        addItemMicrosceneBox.setValue("");
        parent.dirty = true;
    }

    private void populateEditBoxes(PhantasiaSceneData.PlacementData p) {
        if (editingItem < 0 || editingItem >= p.items.size()) return;
        PhantasiaSceneData.ItemConditionData it = p.items.get(editingItem);
        editItemIdBox.setValue(it.item != null ? it.item : "");
        editItemCountBox.setValue(it.count > 1 ? String.valueOf(it.count) : "");
        editItemLabelBox.setValue(it.label != null ? it.label : "");
        editItemDurationBox.setValue(it.trackDurationTicks != 20 ? String.valueOf(it.trackDurationTicks) : "");
        editItemDescBox.setValue(it.description != null ? it.description : "");
        editItemMicrosceneBox.setValue(it.microsceneId != null ? it.microsceneId : "");
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
    public boolean mouseScrolled(double mx, double my, double delta) {
        scrollY = Math.max(0, scrollY - (int) (delta * 12));
        return true;
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
                addItemIdBox, addItemCountBox, addItemLabelBox, addItemDurationBox, addItemDescBox,
                addItemMicrosceneBox,
                editItemIdBox, editItemCountBox, editItemLabelBox, editItemDurationBox, editItemDescBox,
                editItemMicrosceneBox)) {
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

    /** Returns true when the given content y-coordinate is within the visible clip range. */
    private static boolean inClip(int cy, int clipTop, int clipBottom) {
        return cy + 14 >= clipTop && cy < clipBottom;
    }

    private void drawIfVisible(GuiGraphics g, String text, int x, int y, int color, int clipTop, int clipBottom) {
        if (inClip(y, clipTop, clipBottom))
            g.drawString(font, text, x, y, color, false);
    }




}
