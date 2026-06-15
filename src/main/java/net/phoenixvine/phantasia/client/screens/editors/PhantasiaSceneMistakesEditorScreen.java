package net.phoenixvine.phantasia.client.screens.editors;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.client.screens.PhantasiaScreen;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

/**
 * PhantasiaSceneMistakesEditorScreen
 *
 * Subscreen for defining layout mistake rules on a {@link PhantasiaSceneData}.
 *
 * A "layout mistake" is a named rule that flags incorrect placement of machines
 * in a multi-machine scene — for example two machines placed too close together,
 * a machine missing from the layout, or a machine rotated the wrong way.
 *
 * Each {@link PhantasiaSceneData.SceneMistakeData} entry holds:
 * - id : unique string key (e.g. "too_close")
 * - description : human-readable explanation shown to the player
 * - severity : INFO | WARNING | ERROR (controls highlight colour)
 * - placements : list of placement indices this rule applies to
 *
 * The screen shows a scrollable list of existing mistakes, an inline expand-to-
 * edit form for each, and an Add-new form at the bottom.
 *
 * NOTE: Requires PhantasiaSceneData to expose:
 * List<SceneMistakeData> mistakes (add this field if absent)
 *
 * And PhantasiaSceneData.SceneMistakeData:
 * String id
 * String description
 * String severity ("INFO" | "WARNING" | "ERROR")
 * List<Integer> placements (placement indices this mistake targets)
 */
@OnlyIn(Dist.CLIENT)
public class PhantasiaSceneMistakesEditorScreen extends PhantasiaScreen {

    // ── Theme ─────────────────────────────────────────────────────────────────

    private static final int ROW_H = 20;
    private static final int BOTTOM_H = 28;
    private static final int PANEL_W = 560;

    private static final String[] SEVERITIES = { "INFO", "WARNING", "ERROR" };
    private static final String[] SEVERITY_LABELS = { "Info", "Warning", "Error" };
    private static final int[] SEVERITY_COLORS = { 0xFF4FC3F7, 0xFFFFB74D, 0xFFFF5252 };

    // ── Parent / data ─────────────────────────────────────────────────────────
    private final PhantasiaSceneEditorScreen parent;
    private final PhantasiaSceneData data;

    // ── State ─────────────────────────────────────────────────────────────────
    private int expandedRow = -1;   // index of the mistake row that is expanded for editing
    private int scrollOffset = 0;
    private String addError = null;

    // ── Widgets ───────────────────────────────────────────────────────────────
    // Shared add/edit fields (reused per expanded row)
    private EditBox editIdBox;
    private EditBox editDescBox;
    private EditBox editPlacementsBox;   // comma-separated placement indices: "0, 2"

    // Pending severity for add-new form
    private String newSeverity = "WARNING";

    // ── Button list ───────────────────────────────────────────────────────────


    // ─────────────────────────────────────────────────────────────────────────

    public PhantasiaSceneMistakesEditorScreen(PhantasiaSceneEditorScreen parent,
                                              PhantasiaSceneData data) {
        super(Component.translatable("screen.phantasia.scene_mistakes_editor.title"));
        this.parent = parent;
        this.data = data;
        // Ensure the list exists on the data object
        if (data.mistakes == null) data.mistakes = new ArrayList<>();
    }

    @Override
    public void hideAllInputs() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Init
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();

        editIdBox = addW(new EditBox(font, 0, 0, 160, 12, Component.empty()));
        editIdBox.setMaxLength(64);
        editIdBox.setHint(Component.translatable("screen.phantasia.scene_mistakes_editor.hint_id"));
        editIdBox.setFilter(s -> s.matches("[\\w_]*"));

        editDescBox = addW(new EditBox(font, 0, 0, 260, 12, Component.empty()));
        editDescBox.setMaxLength(256);
        editDescBox.setHint(Component.translatable("screen.phantasia.scene_mistakes_editor.hint_desc"));

        editPlacementsBox = addW(new EditBox(font, 0, 0, 120, 12, Component.empty()));
        editPlacementsBox.setMaxLength(64);
        editPlacementsBox.setHint(Component.translatable("screen.phantasia.scene_mistakes_editor.hint_placements"));
        editPlacementsBox.setFilter(s -> s.matches("[\\d,\\s]*"));

        hideAll();
    }


    // ─────────────────────────────────────────────────────────────────────────
    // Render
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        btns.clear();
        hideAll();

        g.fill(0, 0, width, height, C_BG());
        renderTopBar(g, mx, my);
        renderContent(g, mx, my);
        renderBottomHint(g);

        super.render(g, mx, my, partial);
    }

    private void renderTopBar(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, width, TOP_BAR_H, C_BAR());
        g.fill(0, TOP_BAR_H - 1, width, TOP_BAR_H, C_ACCENT());

        g.drawCenteredString(font,
                "\u26A0 Layout Mistakes  \u2014  " + data.mistakes.size() + " defined",
                width / 2, (TOP_BAR_H - 8) / 2, C_ACCENT());

        // Done
        int doneW = font.width("\u2713 Done") + 12;
        int doneX = width - 4 - doneW;
        boolean doneHov = isOver(mx, my, doneX, 3, doneW, TOP_BAR_H - 6);
        g.fill(doneX, 3, doneX + doneW, TOP_BAR_H - 3, doneHov ? C_BTN_HOV() : C_BTN());
        if (doneHov) {
            g.fill(doneX, 3, doneX + doneW, 4, C_ACCENT());
        }
        g.drawString(font, "\u2713 Done", doneX + 6, (TOP_BAR_H - 8) / 2, doneHov ? C_GREEN() : C_TEXT(), false);
        btns.add(new Btn(doneX, 3, doneW, TOP_BAR_H - 6, this::goBack));
    }

    private void renderContent(GuiGraphics g, int mx, int my) {
        int pw = Math.min(PANEL_W, width - 20);
        int px = (width - pw) / 2;
        int cy = TOP_BAR_H + 6;

        // ── Existing mistakes ─────────────────────────────────────────────────
        for (int i = 0; i < data.mistakes.size(); i++) {
            PhantasiaSceneData.SceneMistakeData m = data.mistakes.get(i);
            boolean expanded = (expandedRow == i);
            cy = renderMistakeRow(g, mx, my, px, cy, pw, i, m, expanded);
        }

        if (data.mistakes.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.phantasia.scene_mistakes_editor.empty").getString(), width / 2, cy + 8, C_DIM());
            cy += 22;
        }

        // ── Divider ───────────────────────────────────────────────────────────
        cy += 4;
        g.fill(px, cy, px + pw, cy + 1, 0x33FFFFFF);
        cy += 8;

        // ── Add-new form ──────────────────────────────────────────────────────
        g.drawString(font, Component.translatable("screen.phantasia.scene_mistakes_editor.btn_new").getString(), px + 4, cy, C_ACCENT(), false);
        cy += 12;

        // Severity selector
        g.drawString(font, Component.translatable("screen.phantasia.scene_mistakes_editor.label_severity").getString(), px + 4, cy + 2, C_DIM(), false);
        int sbx = px + 4 + font.width(Component.translatable("screen.phantasia.scene_mistakes_editor.label_severity").getString()) + 4;
        for (int si = 0; si < SEVERITIES.length; si++) {
            String sv = SEVERITIES[si];
            String svl = SEVERITY_LABELS[si];
            int svW = font.width(svl) + 10;
            boolean svSel = sv.equals(newSeverity);
            boolean svHov = isOver(mx, my, sbx, cy, svW, 12);
            int svCol = SEVERITY_COLORS[si];
            g.fill(sbx, cy, sbx + svW, cy + 12, svSel ? C_BTN_ACT() : (svHov ? C_BTN_HOV() : C_BTN()));
            if (svSel) g.fill(sbx, cy, sbx + svW, cy + 1, svCol);
            g.drawString(font, svl, sbx + 5, cy + 2, svSel ? svCol : C_TEXT(), false);
            final String fsv = sv;
            btns.add(new Btn(sbx, cy, svW, 12, () -> newSeverity = fsv));
            sbx += svW + 3;
        }
        cy += 16;

        // ID field
        g.drawString(font, Component.translatable("screen.phantasia.scene_mistakes_editor.label_id").getString(), px + 4, cy + 2, C_DIM(), false);
        place(editIdBox, px + 4 + font.width(Component.translatable("screen.phantasia.scene_mistakes_editor.label_id").getString()) + 4, cy, 160, 12);
        cy += 16;

        // Description field
        g.drawString(font, Component.translatable("screen.phantasia.scene_mistakes_editor.label_desc").getString(), px + 4, cy + 2, C_DIM(), false);
        place(editDescBox, px + 4 + font.width(Component.translatable("screen.phantasia.scene_mistakes_editor.label_desc").getString()) + 4, cy, pw - 12 - font.width(Component.translatable("screen.phantasia.scene_mistakes_editor.label_desc").getString()), 12);
        cy += 16;

        // Placements field
        g.drawString(font, Component.translatable("screen.phantasia.scene_mistakes_editor.label_placements").getString(), px + 4, cy + 2, C_DIM(), false);
        int plLblW = font.width(Component.translatable("screen.phantasia.scene_mistakes_editor.label_placements").getString()) + 4;
        place(editPlacementsBox, px + 4 + plLblW, cy, 120, 12);

        // Placement index reference
        int refX = px + 4 + plLblW + 124;
        for (int pi = 0; pi < data.placements.size(); pi++) {
            String ref = "#" + pi + " " + shortMachineId(data.placements.get(pi).machine);
            g.drawString(font, ref, refX, cy + 2, C_DIM(), false);
            refX += font.width(ref) + 8;
            if (refX > px + pw - 20) break; // don't overflow
        }
        cy += 16;

        // Error
        if (addError != null) {
            g.drawString(font, addError, px + 4, cy, C_RED(), false);
            cy += 12;
        }

        // Add button
        int addBtnW = pw - 8;
        boolean addHov = isOver(mx, my, px + 4, cy, addBtnW, 14);
        g.fill(px + 4, cy, px + 4 + addBtnW, cy + 14, addHov ? C_BTN_HOV() : C_BTN());
        if (addHov) g.fill(px + 4, cy, px + 4 + addBtnW, cy + 1, C_GREEN());
        g.drawCenteredString(font, "\u2713 Add Mistake", px + 4 + addBtnW / 2, cy + 3, addHov ? C_GREEN() : C_TEXT());
        btns.add(new Btn(px + 4, cy, addBtnW, 14, this::commitAdd));
    }

    /**
     * Renders one row for an existing mistake. If {@code expanded} is true, also
     * renders the inline edit form immediately below the row.
     */
    private int renderMistakeRow(GuiGraphics g, int mx, int my,
                                 int px, int cy, int pw,
                                 int idx, PhantasiaSceneData.SceneMistakeData m,
                                 boolean expanded) {
        int sevCol = severityColor(m.severity);

        // Row background
        boolean rowHov = isOver(mx, my, px + 2, cy, pw - 28, ROW_H);
        g.fill(px + 2, cy, px + pw - 2, cy + ROW_H,
                expanded ? C_BTN_ACT() : (rowHov ? C_BTN_HOV() : C_BTN()));
        g.fill(px + 2, cy, px + 3, cy + ROW_H, sevCol);

        // Severity badge
        String badge = m.severity != null ? m.severity.substring(0, 1) : "?";
        int badgeW = font.width(badge) + 6;
        g.fill(px + 5, cy + 4, px + 5 + badgeW, cy + ROW_H - 4, sevCol & 0x55FFFFFF | 0x55000000);
        g.drawString(font, badge, px + 8, cy + 6, sevCol, false);

        // ID + description
        String idStr = m.id != null ? m.id : "(no id)";
        g.drawString(font, idStr, px + 5 + badgeW + 4, cy + 4, expanded ? C_ACCENT() : C_TEXT(), false);
        if (m.description != null && !m.description.isBlank()) {
            String desc = trunc(m.description, pw - badgeW - 60);
            g.drawString(font, desc, px + 5 + badgeW + 4, cy + 12, C_DIM(), false);
        }

        // Placement indices badge
        if (m.placements != null && !m.placements.isEmpty()) {
            String pls = "P:" + m.placements.toString().replace(" ", "");
            int plX = px + pw - 48 - font.width(pls);
            g.drawString(font, pls, plX, cy + 6, 0xFF889AAA, false);
        }

        // Remove button
        int rmX = px + pw - 24, rmY = cy + 4;
        boolean rmHov = isOver(mx, my, rmX, rmY, 18, 12);
        g.fill(rmX, rmY, rmX + 18, rmY + 12, rmHov ? C_BTN_HOV() : C_BTN());
        g.drawString(font, "\u2715", rmX + 5, rmY + 2, rmHov ? C_RED() : C_DIM(), false);
        final int fi = idx;
        btns.add(new Btn(rmX, rmY, 18, 12, () -> {
            parent.checkpoint();
            data.mistakes.remove(fi);
            if (expandedRow == fi) expandedRow = -1;
            else if (expandedRow > fi) expandedRow--;
            parent.dirty = true;
        }));

        // Click row to expand/collapse edit
        btns.add(new Btn(px + 2, cy, pw - 28, ROW_H, () -> {
            if (expandedRow == fi) {
                expandedRow = -1;
            } else {
                expandedRow = fi;
                populateEditBoxes(m);
            }
        }));
        cy += ROW_H + 1;

        // Inline edit form
        if (expanded) {
            cy = renderInlineEditForm(g, mx, my, px, cy, pw, idx, m);
        }

        return cy;
    }

    /** Inline edit form for an existing mistake (shown when row is expanded). */
    private int renderInlineEditForm(GuiGraphics g, int mx, int my,
                                     int px, int cy, int pw,
                                     int idx, PhantasiaSceneData.SceneMistakeData m) {
        g.fill(px + 2, cy, px + pw - 2, cy + 1, 0x22FFFFFF);
        cy += 4;

        // Severity selector
        int sbx = px + 8;
        for (int si = 0; si < SEVERITIES.length; si++) {
            String sv = SEVERITIES[si];
            String svl = SEVERITY_LABELS[si];
            int svW = font.width(svl) + 10;
            boolean svSel = sv.equals(m.severity);
            boolean svHov = isOver(mx, my, sbx, cy, svW, 12);
            int svCol = SEVERITY_COLORS[si];
            g.fill(sbx, cy, sbx + svW, cy + 12, svSel ? C_BTN_ACT() : (svHov ? C_BTN_HOV() : C_BTN()));
            if (svSel) g.fill(sbx, cy, sbx + svW, cy + 1, svCol);
            g.drawString(font, svl, sbx + 5, cy + 2, svSel ? svCol : C_TEXT(), false);
            final String fsv = sv;
            btns.add(new Btn(sbx, cy, svW, 12, () -> {
                parent.checkpoint();
                m.severity = fsv;
                parent.dirty = true;
            }));
            sbx += svW + 3;
        }
        cy += 16;

        // ID field
        g.drawString(font, Component.translatable("screen.phantasia.scene_mistakes_editor.label_id").getString(), px + 8, cy + 2, C_DIM(), false);
        place(editIdBox, px + 8 + font.width(Component.translatable("screen.phantasia.scene_mistakes_editor.label_id").getString()) + 4, cy, 160, 12);
        cy += 16;

        // Description field
        g.drawString(font, Component.translatable("screen.phantasia.scene_mistakes_editor.label_desc").getString(), px + 8, cy + 2, C_DIM(), false);
        place(editDescBox, px + 8 + font.width(Component.translatable("screen.phantasia.scene_mistakes_editor.label_desc").getString()) + 4, cy, pw - 20 - font.width(Component.translatable("screen.phantasia.scene_mistakes_editor.label_desc").getString()), 12);
        cy += 16;

        // Placements
        g.drawString(font, Component.translatable("screen.phantasia.scene_mistakes_editor.label_placements").getString(), px + 8, cy + 2, C_DIM(), false);
        place(editPlacementsBox, px + 8 + font.width(Component.translatable("screen.phantasia.scene_mistakes_editor.label_placements").getString()) + 4, cy, 120, 12);
        cy += 16;

        // Apply button
        int applyW = pw - 16;
        boolean applyHov = isOver(mx, my, px + 8, cy, applyW, 12);
        g.fill(px + 8, cy, px + 8 + applyW, cy + 12, applyHov ? C_BTN_HOV() : C_BTN());
        if (applyHov) g.fill(px + 8, cy, px + 8 + applyW, cy + 1, C_ACCENT());
        g.drawCenteredString(font, "\u2713 Apply", px + 8 + applyW / 2, cy + 2, applyHov ? C_ACCENT() : C_TEXT());
        btns.add(new Btn(px + 8, cy, applyW, 12, () -> commitEdit(idx, m)));
        cy += 16;

        return cy;
    }

    private void renderBottomHint(GuiGraphics g) {
        int by = height - BOTTOM_H;
        g.fill(0, by, width, height, C_BAR());
        g.fill(0, by, width, by + 1, 0x22FFFFFF);
        g.drawCenteredString(font,
                "Mistakes flag incorrect machine placements in the scene viewer  \u2014  " + data.placements.size() +
                        " placement(s) available",
                width / 2, by + (BOTTOM_H - 8) / 2, C_DIM());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Actions
    // ─────────────────────────────────────────────────────────────────────────

    private void commitAdd() {
        addError = null;
        String id = editIdBox.getValue().trim();
        String desc = editDescBox.getValue().trim();
        String pls = editPlacementsBox.getValue().trim();

        if (id.isEmpty()) {
            addError = Component.translatable("screen.phantasia.scene_mistakes_editor.err_blank_id").getString();
            return;
        }
        for (PhantasiaSceneData.SceneMistakeData m : data.mistakes) {
            if (id.equals(m.id)) {
                addError = Component.translatable("screen.phantasia.scene_mistakes_editor.err_duplicate_id").getString() + ": " + id;
                return;
            }
        }

        parent.checkpoint();
        PhantasiaSceneData.SceneMistakeData m = new PhantasiaSceneData.SceneMistakeData();
        m.id = id;
        m.description = desc.isEmpty() ? null : desc;
        m.severity = newSeverity;
        m.placements = parsePlacementIndices(pls);
        data.mistakes.add(m);
        parent.dirty = true;

        editIdBox.setValue("");
        editDescBox.setValue("");
        editPlacementsBox.setValue("");
    }

    private void commitEdit(int idx, PhantasiaSceneData.SceneMistakeData m) {
        parent.checkpoint();
        String newId = editIdBox.getValue().trim();
        if (!newId.isEmpty()) m.id = newId;
        String desc = editDescBox.getValue().trim();
        m.description = desc.isEmpty() ? null : desc;
        m.placements = parsePlacementIndices(editPlacementsBox.getValue().trim());
        parent.dirty = true;
        expandedRow = -1;
    }

    private void populateEditBoxes(PhantasiaSceneData.SceneMistakeData m) {
        editIdBox.setValue(m.id != null ? m.id : "");
        editDescBox.setValue(m.description != null ? m.description : "");
        if (m.placements != null) {
            StringBuilder sb = new StringBuilder();
            for (int p : m.placements) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(p);
            }
            editPlacementsBox.setValue(sb.toString());
        } else {
            editPlacementsBox.setValue("");
        }
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
        scrollOffset = Math.max(0, scrollOffset - (int) Math.signum(delta));
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
        for (var box : java.util.List.of(editIdBox, editDescBox, editPlacementsBox)) {
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




    private static int severityColor(String severity) {
        if (severity == null) return SEVERITY_COLORS[1];
        return switch (severity.toUpperCase(java.util.Locale.ROOT)) {
            case "INFO" -> SEVERITY_COLORS[0];
            case "ERROR" -> SEVERITY_COLORS[2];
            default -> SEVERITY_COLORS[1]; // WARNING
        };
    }

    private static String shortMachineId(String machine) {
        if (machine == null || machine.isEmpty()) return "?";
        return machine.contains(":") ? machine.split(":")[1].replace('_', ' ') : machine;
    }

    private static List<Integer> parsePlacementIndices(String raw) {
        List<Integer> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) return result;
        for (String part : raw.split("[,\\s]+")) {
            try {
                result.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }
}
