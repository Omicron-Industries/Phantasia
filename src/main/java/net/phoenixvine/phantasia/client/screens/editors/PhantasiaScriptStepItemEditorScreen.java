package net.phoenixvine.phantasia.client.screens.editors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.client.screens.PhantasiaScreen;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;

import org.lwjgl.glfw.GLFW;

import java.util.List;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

@OnlyIn(Dist.CLIENT)
public class PhantasiaScriptStepItemEditorScreen extends PhantasiaScreen {

    private static final int PANEL_W = 440;

    private static final String[] TYPES = { "input", "output", "catalyst" };
    private static final String[] TYPE_LABELS = { "Input", "Output", "Catalyst" };
    private static final String[] TRACKS = { "none", "left", "right", "up", "down", "pulse" };
    private static final String[] TRACK_LABELS = { "Fixed", "→", "←", "↑", "↓", "Pulse" };

    private final PhantasiaScriptEditorScreen parent;
    private final PhantasiaScriptData scriptData;
    private final int stepIndex;

    private int editingItem = -1;
    private String newItemType = "input";
    private String newItemTrack = "none";

    private EditBox itemIdBox;
    private EditBox itemCountBox;
    private EditBox itemLabelBox;
    private EditBox itemDurationBox;
    private EditBox itemDescBox;
    private EditBox itemMicrosceneBox;
    private EditBox itemGuideIdBox;

    public PhantasiaScriptStepItemEditorScreen(PhantasiaScriptEditorScreen parent,
                                               PhantasiaScriptData scriptData,
                                               int stepIndex) {
        super(Component.translatable("screen.phantasia.script_step_item.title", stepIndex + 1));
        this.parent = parent;
        this.scriptData = scriptData;
        this.stepIndex = stepIndex;
    }

    @Override
    public void hideAllInputs() {}

    private PhantasiaScriptData.StepData step() {
        return scriptData.getSteps().get(stepIndex);
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();

        itemIdBox = addW(new EditBox(font, 0, 0, 200, 12, Component.empty()));
        itemIdBox.setMaxLength(128);
        itemIdBox.setHint(Component.translatable("screen.phantasia.script_step_item_editor.hint_item_id"));

        itemCountBox = addW(new EditBox(font, 0, 0, 34, 12, Component.empty()));
        itemCountBox.setMaxLength(4);
        itemCountBox.setFilter(s -> s.matches("\\d*"));
        itemCountBox.setHint(Component.translatable("ui.phantasia.raw_value", 1));

        itemLabelBox = addW(new EditBox(font, 0, 0, 100, 12, Component.empty()));
        itemLabelBox.setMaxLength(48);
        itemLabelBox.setHint(Component.translatable("screen.phantasia.script_step_item_editor.hint_label_auto"));

        itemDurationBox = addW(new EditBox(font, 0, 0, 34, 12, Component.empty()));
        itemDurationBox.setMaxLength(4);
        itemDurationBox.setFilter(s -> s.matches("\\d*"));
        itemDurationBox.setHint(Component.translatable("screen.phantasia.script_step_item_editor.hint_ticks"));

        itemDescBox = addW(new EditBox(font, 0, 0, 300, 12, Component.empty()));
        itemDescBox.setMaxLength(256);
        itemDescBox.setHint(Component.translatable("screen.phantasia.script_step_item_editor.hint_desc"));

        itemMicrosceneBox = addW(new EditBox(font, 0, 0, 200, 12, Component.empty()));
        itemMicrosceneBox.setMaxLength(128);
        itemMicrosceneBox.setHint(Component.translatable("screen.phantasia.script_step_item_editor.hint_scene"));

        itemGuideIdBox = addW(new EditBox(font, 0, 0, 200, 12, Component.empty()));
        itemGuideIdBox.setMaxLength(128);
        itemGuideIdBox.setHint(Component.translatable("screen.phantasia.script_step_item_editor.hint_guide_id"));

        hideAll();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        btns.clear();
        hideAll();

        g.fill(0, 0, width, height, C_BG());
        renderTopBar(g, mx, my);
        renderPanel(g, mx, my);
        super.render(g, mx, my, partial);
    }

    private void renderTopBar(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, width, TOP_BAR_H, 0xEE0A0A14);
        g.fill(0, TOP_BAR_H - 1, width, TOP_BAR_H, C_ACCENT());
        g.drawCenteredString(font,
                "Items — Step " + (stepIndex + 1) + " of " + scriptData.getSteps().size(),
                width / 2, (TOP_BAR_H - 8) / 2, C_DIM());

        int rx = width - 4;
        rx = topBtn(g, mx, my, rx, "\u2190 Back", C_BTN(), this::goBack);

        boolean si = step().showItems;
        int siW = font.width(si ? "Items: Visible" : "Items: Hidden") + 12;
        int siX = rx - siW;
        boolean siHov = isOver(mx, my, siX, 3, siW, TOP_BAR_H - 6);
        g.fill(siX, 3, siX + siW, TOP_BAR_H - 3, si ? C_BTN_ACT() : (siHov ? C_BTN_HOV() : C_BTN()));
        if (si) g.fill(siX, 3, siX + siW, 4, C_ACCENT());
        g.drawString(font, si ? "Items: Visible" : "Items: Hidden",
                siX + 6, (TOP_BAR_H - 8) / 2, si ? C_ACCENT() : C_DIM(), false);
        btns.add(new Btn(siX, 3, siW, TOP_BAR_H - 6, () -> {
            parent.checkpoint();
            step().showItems = !step().showItems;
            parent.dirty = true;
        }));
    }

    private int topBtn(GuiGraphics g, int mx, int my, int rx, String label, int base, Runnable action) {
        int w = font.width(label) + 10;
        int x = rx - w, y = 3, h = TOP_BAR_H - 6;
        boolean hov = isOver(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, hov ? C_BTN_HOV() : base);
        if (hov) g.fill(x, y, x + w, y + 1, C_ACCENT());
        g.drawString(font, label, x + 5, (TOP_BAR_H - 8) / 2, hov ? C_ACCENT() : C_TEXT(), false);
        btns.add(new Btn(x, y, w, h, action));
        return x - 4;
    }

    private void renderPanel(GuiGraphics g, int mx, int my) {
        int pw = Math.min(PANEL_W, width - 20);
        int px = (width - pw) / 2;
        int py = TOP_BAR_H + 8;
        int cy = py;

        g.fill(px, py, px + pw, height - 8, C_PANEL());
        g.fill(px, py, px + pw, py + 1, C_ACCENT());
        cy += 8;

        PhantasiaScriptData.StepData s = step();

        if (s.items.isEmpty()) {
            g.drawString(font, Component.translatable("screen.phantasia.script_step_item_editor.empty").getString(),
                    px + 10, cy + 2, C_DIM(), false);
            cy += 14;
        }

        for (int ii = 0; ii < s.items.size(); ii++) {
            cy = renderItemRow(g, mx, my, px, cy, pw, s, ii);
        }

        cy += 4;
        g.fill(px + 8, cy, px + pw - 8, cy + 1, 0x33FFFFFF);
        cy += 8;

        g.drawString(font,
                Component.translatable("screen.phantasia.script_step_item_editor.btn_add_section").getString(), px + 8,
                cy, C_ACCENT(), false);
        cy += 12;

        int bx = px + 8;
        for (int ti = 0; ti < TYPES.length; ti++) {
            String t = TYPES[ti];
            String tl = TYPE_LABELS[ti];
            int tw = font.width(tl) + 10;
            boolean sel = t.equals(newItemType);
            int ac = PhantasiaSceneData.ItemConditionData.staticAccentFor(t);
            boolean hov = isOver(mx, my, bx, cy, tw, 14);
            g.fill(bx, cy, bx + tw, cy + 14, sel ? C_BTN_ACT() : (hov ? C_BTN_HOV() : C_BTN()));
            if (sel) g.fill(bx, cy, bx + tw, cy + 1, ac);
            g.drawString(font, tl, bx + 5, cy + 3, sel ? ac : C_TEXT(), false);
            final String ft = t;
            btns.add(new Btn(bx, cy, tw, 14, () -> newItemType = ft));
            bx += tw + 3;
        }
        cy += 18;

        cy = labeledBox(g, Component.translatable("screen.phantasia.script_step_item_editor.label_item").getString(),
                px, cy, pw, itemIdBox);

        g.drawString(font, Component.translatable("screen.phantasia.script_step_item_editor.label_count").getString(),
                px + 8, cy + 2, C_DIM(), false);
        int cntX = px + 8 +
                font.width(Component.translatable("screen.phantasia.script_step_item_editor.label_count").getString()) +
                4;
        place(itemCountBox, cntX, cy, 34, 12);
        int lblX = cntX + 38;
        g.drawString(font, Component.translatable("screen.phantasia.script_step_item_editor.label_label").getString(),
                lblX, cy + 2, C_DIM(), false);
        place(itemLabelBox,
                lblX + font.width(
                        Component.translatable("screen.phantasia.script_step_item_editor.label_label").getString()) + 4,
                cy,
                pw - (lblX - px) - font.width(
                        Component.translatable("screen.phantasia.script_step_item_editor.label_label").getString()) - 8,
                12);
        cy += 16;

        g.drawString(font, Component.translatable("screen.phantasia.script_step_item_editor.label_track").getString(),
                px + 8, cy + 2, C_DIM(), false);
        int tbx = px + 8 +
                font.width(Component.translatable("screen.phantasia.script_step_item_editor.label_track").getString()) +
                4;
        for (int ti = 0; ti < TRACKS.length; ti++) {
            String t = TRACKS[ti];
            String tl = TRACK_LABELS[ti];
            int tw = font.width(tl) + 8;
            boolean sel = t.equals(newItemTrack);
            boolean hov = isOver(mx, my, tbx, cy, tw, 12);
            g.fill(tbx, cy, tbx + tw, cy + 12, sel ? C_BTN_ACT() : (hov ? C_BTN_HOV() : C_BTN()));
            if (sel) g.fill(tbx, cy, tbx + tw, cy + 1, C_ACCENT());
            g.drawString(font, tl, tbx + 4, cy + 2, sel ? C_ACCENT() : C_TEXT(), false);
            final String ft = t;
            btns.add(new Btn(tbx, cy, tw, 12, () -> newItemTrack = ft));
            tbx += tw + 2;
        }
        if (!"none".equals(newItemTrack)) {
            g.drawString(font,
                    Component.translatable("screen.phantasia.script_step_item_editor.label_ticks").getString(), tbx + 2,
                    cy + 2, C_DIM(), false);
            place(itemDurationBox, tbx + font.width(
                    Component.translatable("screen.phantasia.script_step_item_editor.label_ticks").getString()) + 6, cy,
                    34, 12);
        } else {
            itemDurationBox.visible = false;
        }
        cy += 16;

        cy = labeledBox(g, "Desc:", px, cy, pw, itemDescBox);

        cy = labeledBox(g, "Scene:", px, cy, pw, itemMicrosceneBox);
        cy = labeledBox(g, "Guide ID:", px, cy, pw, itemGuideIdBox);

        int addBtnW = pw - 16;
        boolean addHov = isOver(mx, my, px + 8, cy, addBtnW, 14);
        g.fill(px + 8, cy, px + 8 + addBtnW, cy + 14, addHov ? C_BTN_HOV() : C_BTN());
        if (addHov) g.fill(px + 8, cy, px + 8 + addBtnW, cy + 1, C_GREEN());
        g.drawCenteredString(font, "\u2713 Add Item", px + 8 + addBtnW / 2, cy + 3,
                addHov ? C_GREEN() : C_TEXT());
        btns.add(new Btn(px + 8, cy, addBtnW, 14, this::commitAdd));
    }

    private int renderItemRow(GuiGraphics g, int mx, int my,
                              int px, int cy, int pw,
                              PhantasiaScriptData.StepData s, int ii) {
        PhantasiaSceneData.ItemConditionData it = s.items.get(ii);
        boolean editing = (editingItem == ii);
        int ac = it.accentColor();

        boolean rowHov = isOver(mx, my, px + 4, cy, pw - 30, 14);
        g.fill(px + 4, cy, px + pw - 4, cy + 14,
                editing ? C_BTN_ACT() : (rowHov ? C_BTN_HOV() : C_BTN()));
        g.fill(px + 4, cy, px + 5, cy + 14, ac);

        String badge = switch (it.type == null ? "input" : it.type.toLowerCase(java.util.Locale.ROOT)) {
            case "output" -> "out";
            case "catalyst" -> "cat";
            default -> "in";
        };
        int badgeW = font.width(badge) + 6;
        g.fill(px + 7, cy + 2, px + 7 + badgeW, cy + 12, ac & 0x44FFFFFF | 0x44000000);
        g.drawString(font, badge, px + 10, cy + 3, ac, false);

        String nm = it.item.contains(":") ? it.item.split(":")[1].replace('_', ' ') : it.item;
        String disp = (it.label != null && !it.label.isBlank()) ? it.label + "  (" + nm + ")" : nm;
        g.drawString(font, trunc(disp, pw - badgeW - 60), px + 10 + badgeW, cy + 3, C_TEXT(), false);

        if (it.track != null && !"none".equals(it.track)) {
            String ti = switch (it.track) {
                case "left" -> "→";
                case "right" -> "←";
                case "up" -> "↑";
                case "down" -> "↓";
                case "pulse" -> "~";
                default -> "?";
            };
            g.drawString(font, ti, px + pw - 54, cy + 3, C_ACCENT(), false);
        }

        if (it.count > 1)
            g.drawString(font, "x" + it.count, px + pw - 42, cy + 3, C_DIM(), false);

        int rmX = px + pw - 24;
        boolean rmH = isOver(mx, my, rmX, cy + 1, 18, 12);
        g.fill(rmX, cy + 1, rmX + 18, cy + 13, rmH ? C_BTN_HOV() : C_BTN());
        g.drawString(font, "\u2715", rmX + 5, cy + 3, rmH ? C_RED() : C_DIM(), false);
        final int fii = ii;
        btns.add(new Btn(rmX, cy + 1, 18, 12, () -> {
            parent.checkpoint();
            s.items.remove(fii);
            if (editingItem == fii) editingItem = -1;
            else if (editingItem > fii) editingItem--;
            parent.dirty = true;
        }));

        btns.add(new Btn(px + 4, cy, pw - 30, 14, () -> {
            if (editingItem == fii) {
                editingItem = -1;
            } else {
                editingItem = fii;
                populateBoxes(it);
            }
        }));
        cy += 15;

        if (editing) {

            int bx = px + 8;
            for (int ti = 0; ti < TYPES.length; ti++) {
                String t = TYPES[ti];
                String tl = TYPE_LABELS[ti];
                int tw = font.width(tl) + 10;
                boolean sel = t.equals(it.type);
                int iac = PhantasiaSceneData.ItemConditionData.staticAccentFor(t);
                boolean hov = isOver(mx, my, bx, cy, tw, 12);
                g.fill(bx, cy, bx + tw, cy + 12, sel ? C_BTN_ACT() : (hov ? C_BTN_HOV() : C_BTN()));
                if (sel) g.fill(bx, cy, bx + tw, cy + 1, iac);
                g.drawString(font, tl, bx + 5, cy + 2, sel ? iac : C_TEXT(), false);
                final String ft = t;
                btns.add(new Btn(bx, cy, tw, 12, () -> {
                    parent.checkpoint();
                    it.type = ft;
                    parent.dirty = true;
                }));
                bx += tw + 3;
            }
            cy += 16;

            cy = labeledBox(g,
                    Component.translatable("screen.phantasia.script_step_item_editor.label_item").getString(), px, cy,
                    pw, itemIdBox);

            g.drawString(font,
                    Component.translatable("screen.phantasia.script_step_item_editor.label_count").getString(), px + 8,
                    cy + 2, C_DIM(), false);
            int cntX = px + 8 + font.width(
                    Component.translatable("screen.phantasia.script_step_item_editor.label_count").getString()) + 4;
            place(itemCountBox, cntX, cy, 34, 12);
            int lbx = cntX + 38;
            g.drawString(font,
                    Component.translatable("screen.phantasia.script_step_item_editor.label_label").getString(), lbx,
                    cy + 2, C_DIM(), false);
            place(itemLabelBox,
                    lbx + font.width(Component.translatable("screen.phantasia.script_step_item_editor.label_label")
                            .getString()) + 4,
                    cy,
                    pw - (lbx - px) - font.width(Component
                            .translatable("screen.phantasia.script_step_item_editor.label_label").getString()) - 8,
                    12);
            cy += 16;

            g.drawString(font,
                    Component.translatable("screen.phantasia.script_step_item_editor.label_track").getString(), px + 8,
                    cy + 2, C_DIM(), false);
            int tbx = px + 8 + font.width(
                    Component.translatable("screen.phantasia.script_step_item_editor.label_track").getString()) + 4;
            for (int ti = 0; ti < TRACKS.length; ti++) {
                String t = TRACKS[ti];
                String tl = TRACK_LABELS[ti];
                int tw = font.width(tl) + 8;
                boolean sel = t.equals(it.track == null ? "none" : it.track);
                boolean hov = isOver(mx, my, tbx, cy, tw, 12);
                g.fill(tbx, cy, tbx + tw, cy + 12, sel ? C_BTN_ACT() : (hov ? C_BTN_HOV() : C_BTN()));
                if (sel) g.fill(tbx, cy, tbx + tw, cy + 1, C_ACCENT());
                g.drawString(font, tl, tbx + 4, cy + 2, sel ? C_ACCENT() : C_TEXT(), false);
                final String ft = t;
                btns.add(new Btn(tbx, cy, tw, 12, () -> {
                    parent.checkpoint();
                    it.track = ft;
                    parent.dirty = true;
                }));
                tbx += tw + 2;
            }
            boolean hasTrack = it.track != null && !"none".equals(it.track);
            if (hasTrack) {
                g.drawString(font,
                        Component.translatable("screen.phantasia.script_step_item_editor.label_ticks").getString(),
                        tbx + 2, cy + 2, C_DIM(), false);
                place(itemDurationBox, tbx + font.width(
                        Component.translatable("screen.phantasia.script_step_item_editor.label_ticks").getString()) + 6,
                        cy, 34, 12);
            } else {
                itemDurationBox.visible = false;
            }
            cy += 16;

            cy = labeledBox(g, "Desc:", px, cy, pw, itemDescBox);
            cy = labeledBox(g, "Scene:", px, cy, pw, itemMicrosceneBox);
            cy = labeledBox(g, "Guide ID:", px, cy, pw, itemGuideIdBox);

            int applyW = pw - 16;
            boolean applyHov = isOver(mx, my, px + 8, cy, applyW, 12);
            g.fill(px + 8, cy, px + 8 + applyW, cy + 12, applyHov ? C_BTN_HOV() : C_BTN());
            if (applyHov) g.fill(px + 8, cy, px + 8 + applyW, cy + 1, C_ACCENT());
            g.drawCenteredString(font, "\u2713 Apply & close", px + 8 + applyW / 2, cy + 2,
                    applyHov ? C_ACCENT() : C_TEXT());
            btns.add(new Btn(px + 8, cy, applyW, 12, () -> {
                parent.checkpoint();
                String id = itemIdBox.getValue().trim();
                if (!id.isEmpty()) it.item = id;
                try {
                    it.count = Math.max(1, Integer.parseInt(itemCountBox.getValue().trim()));
                } catch (NumberFormatException ignored) {}
                String lbl = itemLabelBox.getValue().trim();
                it.label = lbl.isEmpty() ? null : lbl;
                if (it.track != null && !"none".equals(it.track)) {
                    try {
                        it.trackDurationTicks = Math.max(1, Integer.parseInt(itemDurationBox.getValue().trim()));
                    } catch (NumberFormatException ignored) {}
                }
                String desc = itemDescBox.getValue().trim();
                it.description = desc.isEmpty() ? null : desc;
                String sc = itemMicrosceneBox.getValue().trim();
                it.microsceneId = sc.isEmpty() ? null : sc;
                String gi = itemGuideIdBox.getValue().trim();
                it.guideId = gi.isEmpty() ? null : gi;
                parent.dirty = true;
                editingItem = -1;
                goBack();
            }));
            cy += 16;
        }
        return cy;
    }

    private void commitAdd() {
        String id = itemIdBox.getValue().trim();
        if (id.isEmpty()) return;
        int cnt = 1;
        try {
            cnt = Math.max(1, Integer.parseInt(itemCountBox.getValue().trim()));
        } catch (NumberFormatException ignored) {}
        String lbl = itemLabelBox.getValue().trim();
        String desc = itemDescBox.getValue().trim();
        String sc = itemMicrosceneBox.getValue().trim();
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
        item.microsceneId = sc.isEmpty() ? null : sc;
        String gi = itemGuideIdBox.getValue().trim();
        item.guideId = gi.isEmpty() ? null : gi;
        step().items.add(item);

        itemIdBox.setValue("");
        itemCountBox.setValue("");
        itemLabelBox.setValue("");
        itemDurationBox.setValue("");
        itemDescBox.setValue("");
        itemMicrosceneBox.setValue("");
        parent.dirty = true;
    }

    private void populateBoxes(PhantasiaSceneData.ItemConditionData it) {
        itemIdBox.setValue(it.item != null ? it.item : "");
        itemCountBox.setValue(it.count > 1 ? String.valueOf(it.count) : "");
        itemLabelBox.setValue(it.label != null ? it.label : "");
        itemDurationBox.setValue(it.trackDurationTicks != 20 ? String.valueOf(it.trackDurationTicks) : "");
        itemDescBox.setValue(it.description != null ? it.description : "");
        itemMicrosceneBox.setValue(it.microsceneId != null ? it.microsceneId : "");
        itemGuideIdBox.setValue(it.guideId != null ? it.guideId : "");
    }

    private void goBack() {
        Minecraft.getInstance().setScreen(parent);
    }

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

    private void hideAll() {
        for (var box : List.of(itemIdBox, itemCountBox, itemLabelBox, itemDurationBox,
                itemDescBox, itemMicrosceneBox, itemGuideIdBox)) {
            if (box != null) {
                box.visible = false;
                box.active = false;
            }
        }
    }

    private int labeledBox(GuiGraphics g, String lbl, int px, int cy, int pw, EditBox box) {
        g.drawString(font, lbl, px + 8, cy + 2, C_DIM(), false);
        place(box, px + 8 + font.width(lbl) + 4, cy, pw - 20 - font.width(lbl) - 4, 12);
        return cy + 16;
    }

    private void place(EditBox box, int x, int y, int w, int h) {
        box.setX(x);
        box.setY(y);
        box.setWidth(w);
        box.setHeight(h);
        box.visible = true;
        box.active = true;
    }
}
