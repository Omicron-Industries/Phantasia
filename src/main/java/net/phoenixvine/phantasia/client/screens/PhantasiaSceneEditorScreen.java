package net.phoenixvine.phantasia.client.screens;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.client.camera.CameraView;
import net.phoenixvine.phantasia.client.camera.LerpType;
import net.phoenixvine.phantasia.client.camera.PhantasiaCamera;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.client.render.PhantasiaWorldRenderer;
import net.phoenixvine.phantasia.common.PhantasiaSceneData;
import net.phoenixvine.phantasia.common.PhantasiaSceneLoader;
import net.phoenixvine.phantasia.common.PhantasiaScenePattern;
import net.phoenixvine.phantasia.common.PhantasiaScenes;
import net.phoenixvine.phantasia.common.PhantasiaScriptData;

import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * PhantasiaSceneEditorScreen
 *
 * Manual scene editor. Mirrors the style of {@link PhantasiaScriptEditorScreen} but
 * operates on {@link PhantasiaSceneData} instead of per-machine script data.
 *
 * Key differences from the machine editor:
 * - A "Placements" side panel (toggle with ⊞ button in top bar) for adding,
 * removing, and repositioning machine placements.
 * - Per-placement visibility overrides in the step row, shown when a placement
 * is selected in the placements panel.
 * - No SELECT mode block-position picking (positions are global across the merged
 * world; use the pos show mode with manual entry or future click-to-add support).
 * - World is rebuilt when placements change.
 */
@OnlyIn(Dist.CLIENT)
public class PhantasiaSceneEditorScreen extends Screen {

    // ── Theme (matches PhantasiaScriptEditorScreen) ───────────────────────────
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

    private static final int TOP_BAR_H = 22;
    private static final int STEP_ROW_H = 50;
    private static final int TIMELINE_H = 22;
    private static final int BOTTOM_H = STEP_ROW_H + TIMELINE_H;

    private static final float CAM_ORBIT_SENSITIVITY = 0.35f;
    private static final float CAM_PAN_SENSITIVITY = 0.02f;

    private static final String[] SHOW_MODES = { "all", "layer", "layers", "pos" };
    private static final String[] SHOW_LABELS = { "All", "Layer", "Range", "Pos" };

    // ── Data ──────────────────────────────────────────────────────────────────
    private final Screen parent;
    PhantasiaSceneData data;
    private EditBox sceneIconBox;
    boolean dirty = false;
    private int selectedStep = 0;

    // ── World ─────────────────────────────────────────────────────────────────
    private PhantasiaWorldRenderer renderer;
    private PhantasiaTrackedDummyWorld editorLevel;
    private PhantasiaScenePattern scenePattern;

    // ── Camera ────────────────────────────────────────────────────────────────
    private PhantasiaCamera camera;

    // ── Placements panel ──────────────────────────────────────────────────────
    private boolean showPlacementsPanel = true;
    private int selectedPlacement = -1; // -1 = no selection / global step view
    private static final int PLACEMENTS_PANEL_W = 220;

    // ── Timeline ─────────────────────────────────────────────────────────────
    private int draggingTimelineDot = -1;
    private boolean dotDragMoved = false;
    private double dotDragStartMX = 0;
    private int timelineGhostTick = -1;

    // ── Preview ───────────────────────────────────────────────────────────────
    private boolean previewing = false;
    private int previewTick = 0;
    private float previewAccum = 0f;

    // ── Unsaved confirm ───────────────────────────────────────────────────────
    private boolean showingCloseConfirm = false;

    // ── Undo ──────────────────────────────────────────────────────────────────
    private static final int MAX_UNDO = 20;
    private final ArrayDeque<PhantasiaSceneData> undoStack = new ArrayDeque<>();

    // ── Inputs ────────────────────────────────────────────────────────────────
    private EditBox captionBox;
    private EditBox tickBox;
    private EditBox lerpTicksBox;
    private EditBox camZoomBox;
    private EditBox scriptDurationBox;
    // Per-placement override boxes (reused for whichever placement is selected)
    private EditBox ovLayerBox;
    private EditBox ovHidePosBox;
    // Add-placement inputs
    private EditBox newMachineIdBox;
    private EditBox newOffsetXBox;
    private EditBox newOffsetYBox;
    private EditBox newOffsetZBox;
    // Scene metadata
    private EditBox sceneNameBox;

    // ── Button registry ───────────────────────────────────────────────────────
    private record Btn(int x, int y, int w, int h, Runnable action) {

        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Btn> btns = new ArrayList<>(64);
    private int lastMX, lastMY;

    // ── Item panel hover state ─────────────────────────────────────────────────
    /** Placement index of the currently-hovered item slot, or -1. */
    private int hoveredItemPlacement = -1;
    /** Item index within that placement, or -1. */
    private int hoveredItemIndex     = -1;

    // ─────────────────────────────────────────────────────────────────────────

    public PhantasiaSceneEditorScreen(Screen parent, PhantasiaSceneData original) {
        super(Component.literal("Scene Editor"));
        this.parent = parent;
        this.data = original.copy();
        ensureOneStep();
    }

    private void ensureOneStep() {
        if (data.steps.isEmpty()) {
            PhantasiaSceneData.StepData s = new PhantasiaSceneData.StepData(0, null);
            s.show = "all";
            data.steps.add(s);
        }
        selectedStep = Mth.clamp(selectedStep, 0, data.steps.size() - 1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Init
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();

        if (renderer == null) {
            rebuildWorld();
        }
        buildInputWidgets();
        populateInputsFromStep();
    }

    void rebuildWorld() {
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }

        editorLevel = new PhantasiaTrackedDummyWorld();
        scenePattern = PhantasiaScenePattern.build(data, editorLevel);

        renderer = new PhantasiaWorldRenderer(editorLevel);
        if (scenePattern != null)
            renderer.setBaseplatePositions(scenePattern.allBaseplatePositions);

        initCamera();
        rebuildVisibility();
    }

    private void initCamera() {
        if (scenePattern == null || scenePattern.placements.isEmpty()) {
            camera = new PhantasiaCamera(-135f, -30f, 30f, 0f, 5f, 0f);
            return;
        }

        // Centre on the midpoint of all placements
        float sumX = 0, sumZ = 0;
        for (PhantasiaScenePattern.PlacementEntry pe : scenePattern.placements) {
            sumX += pe.offset.getX();
            sumZ += pe.offset.getZ();
        }
        float midX = sumX / scenePattern.placements.size();
        float midZ = sumZ / scenePattern.placements.size();
        float midY = (scenePattern.minY + scenePattern.maxY) * 0.5f + 0.5f;

        // Zoom proportional to scene spread
        float spanX = 0, spanZ = 0;
        for (PhantasiaScenePattern.PlacementEntry pe : scenePattern.placements) {
            spanX = Math.max(spanX, Math.abs(pe.offset.getX() - midX));
            spanZ = Math.max(spanZ, Math.abs(pe.offset.getZ() - midZ));
        }
        float dist = 20f + Math.max(spanX, spanZ) * 1.5f;

        camera = new PhantasiaCamera(-135f, -30f, dist, midX, midY, midZ);
        camera.setFloorY(scenePattern.minY + 0.5f);
    }

    private void buildInputWidgets() {
        clearWidgets();

        captionBox = addW(new EditBox(font, 0, 0, 200, 12, Component.empty()));
        captionBox.setMaxLength(256);
        captionBox.setHint(Component.literal("Caption for this step..."));
        captionBox.setResponder(v -> {
            step().caption = v.isBlank() ? null : v;
            dirty = true;
        });

        tickBox = addW(new EditBox(font, 0, 0, 40, 12, Component.empty()));
        tickBox.setMaxLength(5);
        tickBox.setFilter(s -> s.matches("\\d*"));
        tickBox.setResponder(v -> {
            try {
                step().tick = Integer.parseInt(v);
                dirty = true;
            } catch (NumberFormatException ignored) {}
        });

        lerpTicksBox = addW(new EditBox(font, 0, 0, 34, 12, Component.empty()));
        lerpTicksBox.setMaxLength(4);
        lerpTicksBox.setFilter(s -> s.matches("\\d*"));
        lerpTicksBox.setHint(Component.literal("20"));
        lerpTicksBox.setResponder(v -> {
            PhantasiaSceneData.StepData s = step();
            if (s.camera == null) return;
            try {
                s.camera.lerpTicks = Integer.parseInt(v);
            } catch (NumberFormatException ignored) {
                s.camera.lerpTicks = 20;
            }
            dirty = true;
        });

        camZoomBox = addW(new EditBox(font, 0, 0, 40, 12, Component.empty()));
        camZoomBox.setMaxLength(7);
        camZoomBox.setFilter(s -> s.matches("-?\\d*\\.?\\d*"));
        camZoomBox.setHint(Component.literal("auto"));
        camZoomBox.setResponder(v -> {
            PhantasiaSceneData.StepData s = step();
            if (s.camera == null) return;
            try {
                s.camera.zoom = Float.parseFloat(v);
            } catch (NumberFormatException ignored) {
                s.camera.zoom = -1f;
            }
            dirty = true;
        });

        scriptDurationBox = addW(new EditBox(font, 0, 0, 46, 12, Component.empty()));
        scriptDurationBox.setMaxLength(6);
        scriptDurationBox.setFilter(s -> s.matches("\\d*"));
        scriptDurationBox.setHint(Component.literal("auto"));
        scriptDurationBox.setResponder(v -> {
            // PhantasiaSceneData has no scriptDuration yet — store in a local field
            dirty = true;
        });

        // Per-placement override boxes
        ovLayerBox = addW(new EditBox(font, 0, 0, 28, 12, Component.empty()));
        ovLayerBox.setMaxLength(4);
        ovLayerBox.setFilter(s -> s.matches("-?\\d*"));
        ovLayerBox.setHint(Component.literal("0"));
        ovLayerBox.setResponder(v -> {
            PhantasiaSceneData.MachineOverride ov = ensureOverride();
            if (ov == null) return;
            try {
                ov.layer = Integer.parseInt(v);
                dirty = true;
            } catch (NumberFormatException ignored) {}
        });

        ovHidePosBox = addW(new EditBox(font, 0, 0, 120, 12, Component.empty()));
        ovHidePosBox.setMaxLength(512);
        ovHidePosBox.setHint(Component.literal("x,y,z; ..."));
        ovHidePosBox.setResponder(v -> {
            PhantasiaSceneData.MachineOverride ov = ensureOverride();
            if (ov == null) return;
            ov.hidePositions = parsePosList(v);
            dirty = true;
        });

        // Add-placement inputs
        newMachineIdBox = addW(new EditBox(font, 0, 0, 160, 12, Component.empty()));
        newMachineIdBox.setMaxLength(128);
        newMachineIdBox.setHint(Component.literal("gtceu:electric_blast_furnace"));

        newOffsetXBox = makeSmallIntBox("0");
        newOffsetYBox = makeSmallIntBox("0");
        newOffsetZBox = makeSmallIntBox("0");

        sceneNameBox = addW(new EditBox(font, 0, 0, 160, 12, Component.empty()));
        sceneNameBox.setMaxLength(64);
        sceneNameBox.setValue(data.name != null ? data.name : "");
        sceneNameBox.setResponder(v -> {
            data.name = v.isBlank() ? data.id : v;
            dirty = true;
        });

        // Add this configuration entry logic block right here:
        sceneIconBox = addW(new EditBox(font, 0, 0, 160, 12, Component.empty()));
        sceneIconBox.setMaxLength(128);
        sceneIconBox.setValue(data.iconItem != null ? data.iconItem : "minecraft:chest");
        sceneIconBox.setResponder(v -> {
            data.iconItem = v.isBlank() ? "minecraft:chest" : v;
            dirty = true;
        });

        hideAllInputs();
    }

    private EditBox makeSmallIntBox(String hint) {
        EditBox b = addW(new EditBox(font, 0, 0, 34, 12, Component.empty()));
        b.setMaxLength(5);
        b.setFilter(s -> s.matches("-?\\d*"));
        b.setHint(Component.literal(hint));
        return b;
    }

    private <T extends net.minecraft.client.gui.components.AbstractWidget> T addW(T w) {
        return addRenderableWidget(w);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tick
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (camera != null) camera.tick();

        if (previewing) {
            previewAccum += 1f;
            while (previewAccum >= 1f) {
                previewAccum -= 1f;
                previewTick++;
            }
            int total = computeTotalTicks();
            if (previewTick >= total) previewTick = 0;
            for (int i = data.steps.size() - 1; i >= 0; i--) {
                if (data.steps.get(i).tick <= previewTick) {
                    if (i != selectedStep) {
                        selectedStep = i;
                        rebuildVisibility();
                    }
                    break;
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Visibility
    // ─────────────────────────────────────────────────────────────────────────

    private void rebuildVisibility() {
        if (renderer == null || scenePattern == null) return;
        Set<BlockPos> visible = scenePattern.computeVisible(step(), data);
        renderer.setVisible(visible);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Render
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        btns.clear();
        hideAllInputs();
        lastMX = mx;
        lastMY = my;

        g.fill(0, 0, this.width, this.height, C_BG);

        // 3D scene
        if (renderer != null && camera != null) {
            int sceneX = showPlacementsPanel ? PLACEMENTS_PANEL_W : 0;
            int sceneW = this.width - sceneX;
            int sceneH = this.height - TOP_BAR_H - BOTTOM_H;
            renderer.setMousePos(mx, my); // raw screen coords — renderer handles viewport offset
            CameraView view = camera.getView(partial);
            renderer.render(view, sceneX, TOP_BAR_H, sceneW, sceneH);
        }

        renderTopBar(g, mx, my);
        if (showPlacementsPanel) renderPlacementsPanel(g, mx, my);
        renderStepRow(g, mx, my);
        renderTimeline(g, mx, my);
        renderItemStrip(g);

        super.render(g, mx, my, partial);

        if (showingCloseConfirm) renderCloseConfirmDialog(g, mx, my);
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private void renderTopBar(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, this.width, TOP_BAR_H, C_BAR);
        g.fill(0, TOP_BAR_H - 1, this.width, TOP_BAR_H, C_ACCENT);

        int x = 6;

        // Placements panel toggle
        boolean ppHov = isOver(mx, my, x, 3, 86, TOP_BAR_H - 6);
        g.fill(x, 3, x + 86, TOP_BAR_H - 3, showPlacementsPanel ? C_BTN_ACT : (ppHov ? C_BTN_HOV : C_BTN));
        if (showPlacementsPanel) g.fill(x, TOP_BAR_H - 3, x + 86, TOP_BAR_H - 2, C_ACCENT);
        g.drawString(font, "\u229E Placements", x + 5, (TOP_BAR_H - 8) / 2, showPlacementsPanel ? C_ACCENT : C_DIM,
                false);
        btns.add(new Btn(x, 3, 86, TOP_BAR_H - 6, () -> showPlacementsPanel = !showPlacementsPanel));
        x += 92;

        // Preview
        boolean ph = isOver(mx, my, x, 3, 70, TOP_BAR_H - 6);
        g.fill(x, 3, x + 70, TOP_BAR_H - 3, previewing ? C_BTN_ACT : (ph ? C_BTN_HOV : C_BTN));
        if (previewing) g.fill(x, TOP_BAR_H - 3, x + 70, TOP_BAR_H - 2, C_GREEN);
        g.drawString(font, previewing ? "\u23F9 Stop" : "\u25BA Preview",
                x + 5, (TOP_BAR_H - 8) / 2, previewing ? C_GREEN : C_DIM, false);
        btns.add(new Btn(x, 3, 70, TOP_BAR_H - 6, this::togglePreview));

        // Scene name (centre)
        String title = data.name != null && !data.name.isBlank() ? data.name : data.id;
        g.drawCenteredString(font, title, this.width / 2, (TOP_BAR_H - 8) / 2, C_DIM);

        // Right buttons
        int rx = this.width - 4;
        rx = topBtn(g, mx, my, rx, "\u2715 Back", C_BTN, this::onClose);
        rx = topBtn(g, mx, my, rx, "\uD83D\uDCBE Save", C_GREEN, this::save);
        if (dirty) {
            String dot = "\u25CF unsaved";
            rx -= font.width(dot) + 10;
            g.drawString(font, dot, rx, (TOP_BAR_H - 8) / 2, C_WARN, false);
        }
    }

    private int topBtn(GuiGraphics g, int mx, int my, int rx, String label, int color, Runnable action) {
        int w = font.width(label) + 10;
        int x = rx - w, y = 3, h = TOP_BAR_H - 6;
        boolean hov = isOver(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, hov ? C_BTN_HOV : color);
        if (hov) {
            g.fill(x, y, x + w, y + 1, C_ACCENT);
            g.fill(x, y + h - 1, x + w, y + h, C_ACCENT);
        }
        g.drawString(font, label, x + 5, (TOP_BAR_H - 8) / 2, hov ? C_ACCENT : C_TEXT, false);
        btns.add(new Btn(x, y, w, h, action));
        return x - 4;
    }

    // ── Placements panel ──────────────────────────────────────────────────────

    private void renderPlacementsPanel(GuiGraphics g, int mx, int my) {
        int pw = PLACEMENTS_PANEL_W;
        int ph = this.height - TOP_BAR_H - BOTTOM_H;
        int px = 0, py = TOP_BAR_H;

        g.fill(px, py, px + pw, py + ph, C_PANEL);
        g.fill(px + pw - 1, py, px + pw, py + ph, 0x44FFFFFF);

        // Header
        g.fill(px, py, px + pw, py + 14, C_BAR);
        g.drawString(font, "Placements", px + 6, py + 3, C_ACCENT, false);

        // Scene name field
        g.drawString(font, "Name:", px + 6, py + 18, C_DIM, false);
        placeBox(sceneNameBox, px + 6 + font.width("Name:") + 3, py + 16, pw - 20 - font.width("Name:"), 12);
        sceneNameBox.visible = true;
        sceneNameBox.active = true;

        // Render Icon field configuration right underneath Name
        g.drawString(font, "Icon:", px + 6, py + 34, C_DIM, false);
        placeBox(sceneIconBox, px + 6 + font.width("Icon:") + 3, py + 32, pw - 20 - font.width("Icon:"), 12);
        sceneIconBox.visible = true;
        sceneIconBox.active = true;

        // Placement list - pushed down by 16px to make visual room
        int listY = py + 48; // <-- Changed from py + 32 to py + 48
        int rowH = 38;
        for (int i = 0; i < data.placements.size(); i++) {
            PhantasiaSceneData.PlacementData pd = data.placements.get(i);
            boolean sel = (i == selectedPlacement);
            boolean hov = isOver(mx, my, px + 2, listY, pw - 4, rowH - 2);

            g.fill(px + 2, listY, px + pw - 2, listY + rowH - 2,
                    sel ? C_BTN_ACT : (hov ? C_BTN_HOV : C_BTN));
            if (sel) g.fill(px + 2, listY, px + 3, listY + rowH - 2, C_ACCENT);

            // Machine name
            String mid = pd.machine.contains(":") ? pd.machine.split(":")[1].replace('_', ' ') : pd.machine;
            String midTrunc = trunc(mid, pw - 30);
            g.drawString(font, "#" + i + " " + midTrunc, px + 6, listY + 3, sel ? C_ACCENT : C_TEXT, false);
            g.drawString(font, "x" + pd.x + " y" + pd.y + " z" + pd.z,
                    px + 6, listY + 13, C_DIM, false);

            // Item condition count badge
            if (!pd.items.isEmpty()) {
                String badge = pd.items.size() + " item" + (pd.items.size() == 1 ? "" : "s");
                int badgeW = font.width(badge) + 6;
                int badgeX = px + pw - 24 - badgeW - 2;
                g.fill(badgeX, listY + 3, badgeX + badgeW, listY + 13, 0x55000000);
                g.fill(badgeX, listY + 3, badgeX + 1, listY + 13, 0xFF4FC3F7);
                g.drawString(font, badge, badgeX + 3, listY + 4, 0xFF4FC3F7, false);
            }

            // Has override in current step?
            if (step().getOverride(i) != null) {
                g.drawString(font, "\u25C6 override", px + 6, listY + 23, C_ACCENT, false);
            }

            // Remove button
            int rbx = px + pw - 22, rby = listY + 2;
            boolean rbHov = isOver(mx, my, rbx, rby, 18, 14);
            g.fill(rbx, rby, rbx + 18, rby + 14, rbHov ? C_BTN_HOV : C_BTN);
            g.drawString(font, "\u2715", rbx + 5, rby + 3, rbHov ? C_RED : C_DIM, false);
            final int fi = i;
            btns.add(new Btn(rbx, rby, 18, 14, () -> removePlacement(fi)));
            btns.add(new Btn(px + 2, listY, pw - 24, rowH - 2, () -> {
                selectedPlacement = (selectedPlacement == fi) ? -1 : fi;
                populateOverrideBoxes();
            }));

            listY += rowH;
        }

        // ── Add-placement form ────────────────────────────────────────────────
        int formY = listY + 4;
        g.fill(px + 2, formY - 2, px + pw - 2, formY + 54, 0x220A0A20);
        g.drawString(font, "+ Add machine:", px + 6, formY, C_DIM, false);
        formY += 10;

        placeBox(newMachineIdBox, px + 6, formY, pw - 12, 12);
        newMachineIdBox.visible = true;
        newMachineIdBox.active = true;
        formY += 16;

        g.drawString(font, "X:", px + 6, formY + 2, C_DIM, false);
        placeBox(newOffsetXBox, px + 18, formY, 34, 12);
        newOffsetXBox.visible = true;
        newOffsetXBox.active = true;

        g.drawString(font, "Y:", px + 58, formY + 2, C_DIM, false);
        placeBox(newOffsetYBox, px + 68, formY, 34, 12);
        newOffsetYBox.visible = true;
        newOffsetYBox.active = true;

        g.drawString(font, "Z:", px + 108, formY + 2, C_DIM, false);
        placeBox(newOffsetZBox, px + 118, formY, 34, 12);
        newOffsetZBox.visible = true;
        newOffsetZBox.active = true;

        formY += 16;
        btn(g, mx, my, px + 6, formY, pw - 12, 14, "\u2713 Add Placement", C_BTN, this::tryAddPlacement);

        // ── Per-placement override section (when a placement is selected) ─────
        if (selectedPlacement >= 0 && selectedPlacement < data.placements.size()) {
            int ovY = formY + 22;
            PhantasiaSceneData.MachineOverride ov = step().getOverride(selectedPlacement);
            PhantasiaSceneData.PlacementData pd = data.placements.get(selectedPlacement);

            g.fill(px + 2, ovY - 2, px + pw - 2, ovY + 80, 0x220A0A20);
            String ovLabel = "#" + selectedPlacement + " override";
            g.drawString(font, ovLabel, px + 6, ovY, C_ACCENT, false);
            ovY += 10;

            // Show mode buttons
            String curShow = ov != null && ov.show != null ? ov.show : "(global)";
            g.drawString(font, "Show:", px + 6, ovY + 2, C_DIM, false);
            int bx = px + 6 + font.width("Show:") + 3;

            // Global (clear override show)
            boolean gHov = isOver(mx, my, bx, ovY, 40, 12);
            boolean gSel = (ov == null || ov.show == null);
            g.fill(bx, ovY, bx + 40, ovY + 12, gSel ? C_BTN_ACT : (gHov ? C_BTN_HOV : C_BTN));
            if (gSel) g.fill(bx, ovY, bx + 40, ovY + 1, C_DIM);
            g.drawString(font, "Global", bx + 4, ovY + 2, gSel ? C_DIM : C_TEXT, false);
            btns.add(new Btn(bx, ovY, 40, 12, () -> {
                checkpoint();
                ensureOverride().show = null;
                rebuildVisibility();
                dirty = true;
            }));
            bx += 44;

            for (int si = 0; si < SHOW_MODES.length; si++) {
                String sm = SHOW_MODES[si];
                String sml = SHOW_LABELS[si];
                int smW = font.width(sml) + 8;
                boolean smSel = sm.equals(curShow);
                boolean smHov = isOver(mx, my, bx, ovY, smW, 12);
                g.fill(bx, ovY, bx + smW, ovY + 12, smSel ? C_BTN_ACT : (smHov ? C_BTN_HOV : C_BTN));
                if (smSel) g.fill(bx, ovY, bx + smW, ovY + 1, C_ACCENT);
                g.drawString(font, sml, bx + 4, ovY + 2, smSel ? C_ACCENT : C_TEXT, false);
                final String fsm = sm;
                btns.add(new Btn(bx, ovY, smW, 12, () -> {
                    checkpoint();
                    ensureOverride().show = fsm;
                    rebuildVisibility();
                    dirty = true;
                }));
                bx += smW + 2;
            }
            ovY += 16;

            // Layer field (shown for layer/layers modes)
            if (ov != null && ("layer".equals(ov.show) || "layers".equals(ov.show))) {
                g.drawString(font, "Layer:", px + 6, ovY + 2, C_DIM, false);
                placeBox(ovLayerBox, px + 6 + font.width("Layer:") + 3, ovY, 28, 12);
                ovLayerBox.visible = true;
                ovLayerBox.active = true;
                ovY += 16;
            }

            // HidePos
            g.drawString(font, "HidePos:", px + 6, ovY + 2, C_DIM, false);
            placeBox(ovHidePosBox, px + 6 + font.width("HidePos:") + 3, ovY, pw - 14 - font.width("HidePos:"), 12);
            ovHidePosBox.visible = true;
            ovHidePosBox.active = true;
            ovY += 16;

            // Clear override button
            if (ov != null) {
                btn(g, mx, my, px + 6, ovY, pw - 12, 12,
                        "\u2715 Clear override", C_BTN, () -> {
                            checkpoint();
                            step().removeOverride(selectedPlacement);
                            populateOverrideBoxes();
                            rebuildVisibility();
                            dirty = true;
                        });
                ovY += 16;
            }

            // ── Edit placement subscreen button ───────────────────────────────
            ovY += 2;
            // Item condition summary line
            if (!pd.items.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (PhantasiaSceneData.ItemConditionData it : pd.items) {
                    if (sb.length() > 0) sb.append("  ");
                    String pill = it.type == null ? "in" : switch (it.type.toLowerCase(java.util.Locale.ROOT)) {
                        case "output" -> "out"; case "catalyst" -> "cat"; default -> "in";
                    };
                    sb.append("[").append(pill).append("] ");
                    String nm = it.item.contains(":") ? it.item.split(":")[1] : it.item;
                    sb.append(nm.replace('_', ' '));
                }
                g.drawString(font, trunc(sb.toString(), pw - 12), px + 6, ovY, 0xFF9ECDEE, false);
                ovY += 11;
            }
            int editBtnW = pw - 12;
            btn(g, mx, my, px + 6, ovY, editBtnW, 14,
                    "\u270E Edit placement / items \u2192", C_BTN, () -> {
                        Minecraft.getInstance().setScreen(
                                new PhantasiaPlacementEditorScreen(this, data, selectedPlacement));
                    });
        }
    }

    // ── Step row ──────────────────────────────────────────────────────────────

    private void renderStepRow(GuiGraphics g, int mx, int my) {
        int rowY = this.height - BOTTOM_H;
        g.fill(0, rowY, this.width, rowY + STEP_ROW_H, C_BAR);
        g.fill(0, rowY, this.width, rowY + 1, C_ACCENT);

        PhantasiaSceneData.StepData s = step();
        int y1 = rowY + 5;
        int x = 8;

        // Step nav
        g.drawString(font, "STEP", x, y1 - 2, 0xFF334455, false);
        String stepLbl = (selectedStep + 1) + "/" + data.steps.size();
        g.drawString(font, stepLbl, x, y1 + 6, C_ACCENT, false);
        x += font.width(stepLbl) + 8;

        btn(g, mx, my, x, y1, 14, 14, "+", C_BTN, this::addStep);
        x += 18;
        btn(g, mx, my, x, y1, 14, 14, "\u2212", C_BTN, this::deleteStep);
        x += 18;
        btn(g, mx, my, x, y1, 24, 14, "Dup", C_BTN, this::duplicateStep);
        x += 28;
        btn(g, mx, my, x, y1, 14, 14, "\u25C4", C_BTN, () -> moveStep(selectedStep, -1));
        x += 18;
        btn(g, mx, my, x, y1, 14, 14, "\u25BA", C_BTN, () -> moveStep(selectedStep, +1));
        x += 18;

        g.fill(x, y1, x + 1, y1 + 14, 0x33FFFFFF);
        x += 8;

        // Tick
        g.drawString(font, "Tick:", x, y1 + 3, C_DIM, false);
        x += font.width("Tick:") + 3;
        placeBox(tickBox, x, y1, 38, 13);
        x += 44;

        // Caption
        g.drawString(font, "Caption:", x, y1 + 3, C_DIM, false);
        x += font.width("Caption:") + 4;
        int capW = Math.min(200, this.width / 2 - x - 10);
        placeBox(captionBox, x, y1, capW, 13);
        x += capW + 8;

        g.fill(x, y1, x + 1, y1 + 14, 0x33FFFFFF);
        x += 8;

        // Camera group
        boolean hasCam = s.camera != null;
        boolean cch = isOver(mx, my, x, y1, 110, 14);
        g.fill(x, y1, x + 110, y1 + 14, hasCam ? C_BTN_ACT : (cch ? C_BTN_HOV : C_BTN));
        if (hasCam) g.fill(x, y1, x + 110, y1 + 1, C_ACCENT);
        g.drawString(font, hasCam ? "\uD83D\uDCF7 Update Cam" : "\uD83D\uDCF7 Capture Cam",
                x + 5, y1 + 3, hasCam ? C_ACCENT : C_DIM, false);
        btns.add(new Btn(x, y1, 110, 14, this::captureCamera));
        x += 116;

        if (hasCam) {
            // Zoom
            g.drawString(font, "Zoom:", x, y1 + 3, C_DIM, false);
            x += font.width("Zoom:") + 3;
            placeBox(camZoomBox, x, y1, 40, 13);
            x += 46;

            // Clear cam
            boolean rch = isOver(mx, my, x, y1, 48, 14);
            g.fill(x, y1, x + 48, y1 + 14, rch ? C_BTN_HOV : C_BTN);
            g.drawString(font, "\u2715 Cam", x + 5, y1 + 3, rch ? C_RED : C_DIM, false);
            btns.add(new Btn(x, y1, 48, 14, () -> {
                checkpoint();
                s.camera = null;
                dirty = true;
            }));
            x += 54;

            // Lerp type
            LerpType lt = LerpType.fromString(s.camera.lerpType);
            String ltLabel = lt.name().replace("_", " ");
            int ltW = font.width(ltLabel) + 16;
            boolean lth = isOver(mx, my, x, y1, ltW, 14);
            g.fill(x, y1, x + ltW, y1 + 14, lth ? C_BTN_HOV : C_BTN);
            g.fill(x, y1, x + ltW, y1 + 1, C_ACCENT);
            g.drawString(font, ltLabel, x + 8, y1 + 3, C_ACCENT, false);
            btns.add(new Btn(x, y1, ltW, 14, () -> {
                checkpoint();
                LerpType[] vals = LerpType.values();
                s.camera.lerpType = vals[(lt.ordinal() + 1) % vals.length].name();
                dirty = true;
            }));
            x += ltW + 4;

            if (lt != LerpType.SNAP) {
                g.drawString(font, "in", x, y1 + 3, C_DIM, false);
                x += font.width("in") + 3;
                placeBox(lerpTicksBox, x, y1, 34, 13);
                x += 38;
                g.drawString(font, "t", x, y1 + 3, C_DIM, false);
            }
        }

        // ── Row 2: global show mode ───────────────────────────────────────────
        int y2 = rowY + STEP_ROW_H / 2 + 3;
        x = 8;
        g.drawString(font, "Global:", x, y2 + 2, C_DIM, false);
        x += font.width("Global:") + 4;

        for (int i = 0; i < SHOW_MODES.length; i++) {
            String sm = SHOW_MODES[i];
            String sml = SHOW_LABELS[i];
            int mw = font.width(sml) + 10;
            boolean act = sm.equals(s.show);
            boolean hov = isOver(mx, my, x, y2, mw, 14);
            g.fill(x, y2, x + mw, y2 + 14, act ? C_BTN_ACT : (hov ? C_BTN_HOV : C_BTN));
            if (act) g.fill(x, y2, x + mw, y2 + 1, C_ACCENT);
            g.drawString(font, sml, x + 5, y2 + 3, act ? C_ACCENT : C_TEXT, false);
            final String fsm = sm;
            btns.add(new Btn(x, y2, mw, 14, () -> {
                checkpoint();
                s.show = fsm;
                dirty = true;
                rebuildVisibility();
            }));
            x += mw + 3;
        }

        // Layer value hint
        if ("layer".equals(s.show))
            g.drawString(font, " Y=" + s.layer, x, y2 + 2, C_ACCENT, false);
        else if ("layers".equals(s.show))
            g.drawString(font, " " + s.layerMin + "\u2192" + s.layerMax, x, y2 + 2, C_ACCENT, false);
    }

    // ── Timeline ─────────────────────────────────────────────────────────────

    private void renderTimeline(GuiGraphics g, int mx, int my) {
        int tlY = this.height - TIMELINE_H;
        int margin = 30;
        int trackW = this.width - margin * 2;
        int total = computeTotalTicks();
        int midY = tlY + TIMELINE_H / 2;

        g.fill(0, tlY, this.width, this.height, C_PANEL);
        g.fill(0, tlY, this.width, tlY + 1, 0x33FFFFFF);
        g.fill(margin, midY - 1, margin + trackW, midY + 1, 0xFF1A2C3C);

        if (previewing && total > 0) {
            int px = margin + (int) ((float) previewTick / total * trackW);
            g.fill(px - 1, tlY + 2, px + 1, tlY + TIMELINE_H - 2, 0xAAFFFFFF);
        }

        // Ghost
        timelineGhostTick = -1;
        boolean onTrack = isOver(mx, my, margin, tlY, trackW, TIMELINE_H);
        if (onTrack && draggingTimelineDot < 0) {
            boolean nearDot = false;
            for (PhantasiaSceneData.StepData s : data.steps) {
                float t = total > 0 ? (float) s.tick / total : 0f;
                if (Math.abs(mx - (margin + (int) (t * trackW))) < 14) {
                    nearDot = true;
                    break;
                }
            }
            if (!nearDot) {
                timelineGhostTick = total > 0 ? Math.round((float) (mx - margin) / trackW * total) : 0;
                g.fill(mx, tlY + 2, mx + 1, tlY + TIMELINE_H - 2, 0x554FC3F7);
            }
        }

        // Dots
        for (int i = 0; i < data.steps.size(); i++) {
            PhantasiaSceneData.StepData s = data.steps.get(i);
            float t = total > 0 ? (float) s.tick / total : 0f;
            int dotX = margin + (int) (t * trackW);
            boolean sel = (i == selectedStep);
            boolean hov = isOver(mx, my, dotX - 9, midY - 9, 18, 18);
            boolean drag = (draggingTimelineDot == i);

            g.fill(dotX - 7, midY - 7, dotX + 7, midY + 7,
                    sel || drag ? C_ACCENT : (hov ? 0xFFAADDFF : 0xFF3A506A));
            g.fill(dotX - 5, midY - 5, dotX + 5, midY + 5,
                    drag ? 0xFFFFFFFF : (sel ? 0xFF1A3C5C : 0xFF0A1520));
            if (s.camera != null) g.fill(dotX - 3, midY - 7, dotX + 3, midY - 5, C_ACCENT);

            g.drawCenteredString(font, String.valueOf(i + 1), dotX, midY - 3,
                    sel || drag ? C_ACCENT : C_DIM);

            final int fi = i;
            btns.add(new Btn(dotX - 9, midY - 9, 18, 18, () -> selectStep(fi)));
        }

        // Duration box
        int durLabelW = font.width("Total:") + 4;
        int durX = this.width - 4 - 46 - durLabelW;
        g.drawString(font, "Total:", durX, midY - 4, C_DIM, false);
        placeBox(scriptDurationBox, durX + durLabelW, tlY + 5, 46, 12);
        scriptDurationBox.visible = true;
        scriptDurationBox.active = true;
    }

    // ── Item panel (GuideME-style) ────────────────────────────────────────────

    private static final int ITEM_PANEL_W   = 186;
    private static final int ITEM_ROW_H     = 38;
    private static final int ITEM_ICON_BASE = 28;   // base icon draw size
    private static final int ITEM_ICON_HOV  = 36;   // enlarged on hover

    /**
     * Renders a GuideME-style item panel docked to the right edge of the 3D
     * viewport. Each row is one item — large hoverable icon, label, type pill,
     * optional description lines, and a "▶ Open scene" hint when a microscene
     * is configured. Hovering scales the icon up and highlights the row.
     * Clicking an item with a {@code microsceneId} opens
     * {@link PhantasiaItemMicrosceneScreen}.
     */
    private void renderItemStrip(GuiGraphics g) {
        if (scenePattern == null || data == null) return;
        PhantasiaSceneData.StepData step = step();
        if (!step.showItems) return;

        // Collect all items across placements
        record ItemSlot(int placementIdx, int itemIdx, PhantasiaSceneData.ItemConditionData it) {}
        List<ItemSlot> slots = new ArrayList<>();
        for (PhantasiaScenePattern.PlacementEntry pe : scenePattern.placements) {
            if (pe.index < 0 || pe.index >= data.placements.size()) continue;
            PhantasiaSceneData.PlacementData pd = data.placements.get(pe.index);
            for (int ii = 0; ii < pd.items.size(); ii++)
                slots.add(new ItemSlot(pe.index, ii, pd.items.get(ii)));
        }
        if (slots.isEmpty()) return;

        int panelH = slots.size() * ITEM_ROW_H + 8;
        int panelX = this.width - ITEM_PANEL_W - 4;
        int panelY = TOP_BAR_H + 4;

        // Panel background
        g.fill(panelX, panelY, panelX + ITEM_PANEL_W, panelY + panelH, 0xDD070712);
        g.fill(panelX, panelY, panelX + ITEM_PANEL_W, panelY + 1,      0xFF4FC3F7);
        g.fill(panelX, panelY, panelX + 1, panelY + panelH,            0x554FC3F7);

        hoveredItemPlacement = -1;
        hoveredItemIndex     = -1;

        int ry = panelY + 4;
        for (ItemSlot slot : slots) {
            PhantasiaSceneData.ItemConditionData it = slot.it();
            boolean hov = isOver(lastMX, lastMY, panelX + 2, ry, ITEM_PANEL_W - 4, ITEM_ROW_H - 1);
            if (hov) { hoveredItemPlacement = slot.placementIdx(); hoveredItemIndex = slot.itemIdx(); }

            int ac = it.accentColor();
            // Row bg
            g.fill(panelX + 2, ry, panelX + ITEM_PANEL_W - 2, ry + ITEM_ROW_H - 1,
                    hov ? 0xBB0D1A2D : 0x22000000);
            g.fill(panelX + 2, ry, panelX + 3, ry + ITEM_ROW_H - 1, ac); // accent bar

            // Track-animated icon position
            float[] anim  = computeTrackOffset(it, previewTick);
            float animDy  = anim[1];
            int   alpha   = Math.max(0, Math.min(255, (int)(anim[2] * 255)));

            int iconSize  = hov ? ITEM_ICON_HOV : ITEM_ICON_BASE;
            int iconX     = panelX + 5;
            int iconCentY = ry + (ITEM_ROW_H - 1) / 2;
            int iconY     = iconCentY - iconSize / 2 + Math.round(animDy);

            net.minecraft.world.item.Item mcItem = resolveItem(it.item);
            if (mcItem != null) {
                net.minecraft.world.item.ItemStack stack =
                        new net.minecraft.world.item.ItemStack(mcItem, it.count);
                float scale = iconSize / 16f;
                if (alpha < 255) RenderSystem.setShaderColor(1f, 1f, 1f, alpha / 255f);
                g.pose().pushPose();
                g.pose().translate(iconX, iconY, 200f); // z=200 so it draws over row bg
                g.pose().scale(scale, scale, 1f);
                g.renderItem(stack, 0, 0);
                g.renderItemDecorations(font, stack, 0, 0);
                g.pose().popPose();
                if (alpha < 255) RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            } else {
                g.fill(iconX + 2, iconY + 2, iconX + iconSize - 2, iconY + iconSize - 2, 0x44FF0000);
                g.drawCenteredString(font, "?", iconX + iconSize / 2, iconCentY - 4, 0xFFFF5252);
            }

            // Text — right of icon (always leave ITEM_ICON_HOV + margin for icon column)
            int tx = panelX + 5 + ITEM_ICON_HOV + 4;
            int tw = ITEM_PANEL_W - (tx - panelX) - 5;
            int ty = ry + 3;

            // Label
            String lbl = it.displayLabel();
            g.drawString(font, trunc(lbl, tw), tx, ty, hov ? 0xFFFFFFFF : C_TEXT, false);
            ty += 10;

            // Type pill
            String pillTxt = switch (it.type == null ? "input" : it.type.toLowerCase(java.util.Locale.ROOT)) {
                case "output" -> "Out"; case "catalyst" -> "Cat"; default -> "In";
            };
            int pillW = font.width(pillTxt) + 5;
            g.fill(tx, ty, tx + pillW, ty + 8, ac & 0x44FFFFFF | 0x44000000);
            g.drawString(font, pillTxt, tx + 2, ty, ac, false);
            ty += 10;

            // Description (1–2 lines, truncated)
            if (it.description != null && !it.description.isBlank()) {
                var lines = font.split(net.minecraft.network.chat.Component.literal(it.description), tw);
                int rem = 2;
                for (var line : lines) {
                    if (rem-- <= 0) break;
                    g.drawString(font, line, tx, ty, C_DIM, false);
                    ty += 8;
                }
            }

            // Microscene hint (bottom-right of row)
            boolean hasScene = it.microsceneId != null && !it.microsceneId.isBlank();
            boolean hasContent = hasScene || (it.description != null && !it.description.isBlank());
            if (hasContent) {
                String hint = hov ? (hasScene ? "\u25BA Scene" : "\u25BA More") : "\u25B7";
                int hintX = panelX + ITEM_PANEL_W - font.width(hint) - 5;
                int hintY = ry + ITEM_ROW_H - 11;
                g.drawString(font, hint, hintX, hintY, hov ? 0xFF80DEEA : 0x44FFFFFF, false);
            }

            // Every item is clickable — opens PhantasiaItemMicrosceneScreen
            final PhantasiaSceneData.ItemConditionData fIt = it;
            btns.add(new Btn(panelX + 2, ry, ITEM_PANEL_W - 4, ITEM_ROW_H - 1, () -> {
                PhantasiaSceneData microscene = fIt.microsceneId != null
                        ? PhantasiaScenes.get(fIt.microsceneId) : null;
                Minecraft.getInstance().setScreen(
                        new PhantasiaItemMicrosceneScreen(
                                PhantasiaSceneEditorScreen.this, fIt, microscene));
            }));

            ry += ITEM_ROW_H;
        }
    }

    /**
     * Returns {@code [dx, dy, alpha]} for an item given its track setting and
     * the current {@code tick}.
     *
     * <ul>
     *   <li>{@code none}  — [0, 0, 1.0]
     *   <li>{@code left}  — item travels left→right, fades at ends
     *   <li>{@code right} — item travels right→left, fades at ends
     *   <li>{@code up}    — item floats upward and fades
     *   <li>{@code down}  — item falls and fades
     *   <li>{@code pulse} — gentle vertical bob, always opaque
     * </ul>
     */
    private static float[] computeTrackOffset(PhantasiaSceneData.ItemConditionData it, int tick) {
        String track = it.track == null ? "none" : it.track.toLowerCase(java.util.Locale.ROOT);
        if ("none".equals(track)) return new float[]{ 0, 0, 1f };

        int dur = Math.max(1, it.trackDurationTicks);
        float t = (tick % dur) / (float) dur; // 0..1 looping

        return switch (track) {
            case "left" -> {
                // Moves left-to-right across a 40px window, fades near edges
                float dx   = (t * 40f) - 20f;
                float fade = 1f - Math.abs(t - 0.5f) * 2f; // 0 at edges, 1 at centre
                yield new float[]{ dx, 0, Math.max(0f, fade) };
            }
            case "right" -> {
                float dx   = 20f - (t * 40f);
                float fade = 1f - Math.abs(t - 0.5f) * 2f;
                yield new float[]{ dx, 0, Math.max(0f, fade) };
            }
            case "up" -> {
                float dy   = -(t * 24f);       // moves 24px upward
                float fade = 1f - t;           // fades as it rises
                yield new float[]{ 0, dy, Math.max(0f, fade) };
            }
            case "down" -> {
                float dy   = t * 24f;
                float fade = 1f - t;
                yield new float[]{ 0, dy, Math.max(0f, fade) };
            }
            case "pulse" -> {
                // Gentle sine bob ±3px
                float dy = (float) Math.sin(t * 2 * Math.PI) * 3f;
                yield new float[]{ 0, dy, 1f };
            }
            default -> new float[]{ 0, 0, 1f };
        };
    }

    private static String shortMachineId(String machine) {
        if (machine == null || machine.isEmpty()) return "?";
        return machine.contains(":") ? machine.split(":")[1].replace('_', ' ') : machine;
    }

    private static net.minecraft.world.item.Item resolveItem(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            net.minecraft.resources.ResourceLocation rl = id.contains(":")
                    ? new net.minecraft.resources.ResourceLocation(id)
                    : new net.minecraft.resources.ResourceLocation("minecraft", id);
            net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
            return (item == null || item == net.minecraft.world.item.Items.AIR) ? null : item;
        } catch (Exception e) { return null; }
    }

    // ── Confirm dialog ────────────────────────────────────────────────────────

    private void renderCloseConfirmDialog(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, this.width, this.height, 0xBB000000);
        int dw = 280, dh = 70;
        int dx = (this.width - dw) / 2, dy = (this.height - dh) / 2;
        g.fill(dx, dy, dx + dw, dy + dh, C_PANEL);
        g.fill(dx, dy, dx + dw, dy + 1, C_WARN);
        g.drawCenteredString(font, "Unsaved changes — discard and close?", dx + dw / 2, dy + 10, C_WARN);
        g.drawCenteredString(font, "All edits since your last save will be lost.", dx + dw / 2, dy + 22, C_DIM);
        int btnY = dy + dh - 20;
        btn(g, mx, my, dx + dw / 2 - 118, btnY, 110, 14, "\u2715 Discard & Close", C_RED, this::forceClose);
        btn(g, mx, my, dx + dw / 2 + 8, btnY, 110, 14, "\u21A9 Keep Editing", C_BTN, () -> showingCloseConfirm = false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Input
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (showingCloseConfirm) {
            for (Btn b : btns) if (b.hit(mx, my)) {
                b.action().run();
                return true;
            }
            return true;
        }
        for (Btn b : btns) if (b.hit(mx, my)) {
            b.action().run();
            return true;
        }
        if (super.mouseClicked(mx, my, btn)) return true;

        // Timeline
        int tlY = this.height - TIMELINE_H;
        int margin = 30, trackW = this.width - margin * 2;
        int total = computeTotalTicks();
        int midY = tlY + TIMELINE_H / 2;
        boolean onTimeline = isOver(mx, my, 0, tlY, this.width, TIMELINE_H);

        if (onTimeline) {
            if (btn == 1) {
                for (int i = 0; i < data.steps.size(); i++) {
                    PhantasiaSceneData.StepData s = data.steps.get(i);
                    float t = total > 0 ? (float) s.tick / total : 0f;
                    int dotX = margin + (int) (t * trackW);
                    if (isOver(mx, my, dotX - 9, midY - 9, 18, 18) && data.steps.size() > 1) {
                        checkpoint();
                        data.steps.remove(i);
                        selectStep(Math.min(selectedStep, data.steps.size() - 1));
                        dirty = true;
                        return true;
                    }
                }
            }
            if (btn == 0) {
                if (startTimelineDotDrag(mx, my)) return true;
                if (timelineGhostTick >= 0) {
                    addStepAtTick(timelineGhostTick);
                    return true;
                }
            }
        }

        return super.mouseClicked(mx, my, btn);
    }

    private boolean startTimelineDotDrag(double mx, double my) {
        int tlY = this.height - TIMELINE_H;
        int margin = 30, trackW = this.width - margin * 2;
        int total = computeTotalTicks();
        int midY = tlY + TIMELINE_H / 2;
        for (int i = 0; i < data.steps.size(); i++) {
            float t = total > 0 ? (float) data.steps.get(i).tick / total : 0f;
            int dotX = margin + (int) (t * trackW);
            if (isOver(mx, my, dotX - 9, midY - 9, 18, 18)) {
                checkpoint();
                draggingTimelineDot = i;
                dotDragMoved = false;
                dotDragStartMX = mx;
                selectStep(i);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (draggingTimelineDot >= 0 && draggingTimelineDot < data.steps.size()) {
            int margin = 30, trackW = this.width - margin * 2;
            int total = computeTotalTicks();
            float t = Mth.clamp((float) (mx - margin) / trackW, 0f, 1f);
            data.steps.get(draggingTimelineDot).tick = Math.max(0, Math.round(t * total));
            if (Math.abs(mx - dotDragStartMX) > 3) dotDragMoved = true;
            populateInputsFromStep();
            dirty = true;
            return true;
        }

        int sceneBottom = this.height - BOTTOM_H;
        if (my < TOP_BAR_H || my >= sceneBottom) return super.mouseDragged(mx, my, btn, dx, dy);

        if (camera != null && (btn == 2 || btn == 0)) {
            if (btn == 2) {
                Vector3f rgt = new Vector3f(), up = new Vector3f();
                camera.getRightAndUp(rgt, up);
                float pd = CAM_PAN_SENSITIVITY;
                camera.pan(rgt.x * -(float) dx * pd + up.x * (float) dy * pd,
                        rgt.y * -(float) dx * pd + up.y * (float) dy * pd,
                        rgt.z * -(float) dx * pd + up.z * (float) dy * pd);
            } else {
                camera.orbit((float) dx * CAM_ORBIT_SENSITIVITY,
                        (float) dy * CAM_ORBIT_SENSITIVITY);
            }
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int sceneBottom = this.height - BOTTOM_H;
        if (my >= TOP_BAR_H && my < sceneBottom && camera != null) {
            camera.zoom(delta > 0 ? 0.9f : 1.1f, 2f, 200f);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (draggingTimelineDot >= 0 && dotDragMoved) {
            PhantasiaSceneData.StepData dragged = data.steps.get(draggingTimelineDot);
            data.steps.sort(Comparator.comparingInt(s -> s.tick));
            selectedStep = data.steps.indexOf(dragged);
            populateInputsFromStep();
            rebuildVisibility();
        }
        draggingTimelineDot = -1;
        dotDragMoved = false;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean charTyped(char c, int mod) {
        if (super.charTyped(c, mod)) return true;
        return false;
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (getFocused() != null && getFocused().keyPressed(kc, sc, mod)) return true;

        if (kc == GLFW.GLFW_KEY_ESCAPE) {
            if (showingCloseConfirm) {
                showingCloseConfirm = false;
                return true;
            }
            onClose();
            return true;
        }
        boolean ctrl = (mod & 2) != 0;
        if (ctrl && kc == 90) {
            undo();
            return true;
        }

        if (kc == GLFW.GLFW_KEY_RIGHT) {
            if (ctrl) moveStep(selectedStep, +1);
            else selectStep(Math.min(selectedStep + 1, data.steps.size() - 1));
            return true;
        }
        if (kc == GLFW.GLFW_KEY_LEFT) {
            if (ctrl) moveStep(selectedStep, -1);
            else selectStep(Math.max(selectedStep - 1, 0));
            return true;
        }
        return super.keyPressed(kc, sc, mod);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Undo
    // ─────────────────────────────────────────────────────────────────────────

    void checkpoint() {
        if (undoStack.size() >= MAX_UNDO) undoStack.pollFirst();
        undoStack.addLast(data.copy());
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        data = undoStack.pollLast();
        selectedStep = Mth.clamp(selectedStep, 0, data.steps.size() - 1);
        dirty = !undoStack.isEmpty();
        populateInputsFromStep();
        rebuildVisibility();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Actions
    // ─────────────────────────────────────────────────────────────────────────

    private void tryAddPlacement() {
        String machine = newMachineIdBox.getValue().trim();
        if (machine.isEmpty()) return;
        int x = parseIntOrZero(newOffsetXBox.getValue());
        int y = parseIntOrZero(newOffsetYBox.getValue());
        int z = parseIntOrZero(newOffsetZBox.getValue());
        checkpoint();
        data.placements.add(new PhantasiaSceneData.PlacementData(machine, x, y, z));
        newMachineIdBox.setValue("");
        newOffsetXBox.setValue("");
        newOffsetYBox.setValue("");
        newOffsetZBox.setValue("");
        dirty = true;
        rebuildWorld();
    }

    private void removePlacement(int index) {
        if (index < 0 || index >= data.placements.size()) return;
        checkpoint();
        data.placements.remove(index);
        if (selectedPlacement >= data.placements.size())
            selectedPlacement = data.placements.size() - 1;
        // Clean up overrides referencing this or higher indices
        for (PhantasiaSceneData.StepData s : data.steps) {
            s.removeOverride(index);
            // Shift overrides for indices above removed
            for (int i = index + 1; i < data.placements.size() + 1; i++) {
                PhantasiaSceneData.MachineOverride ov = s.machineOverrides.remove(String.valueOf(i));
                if (ov != null) s.machineOverrides.put(String.valueOf(i - 1), ov);
            }
        }
        dirty = true;
        rebuildWorld();
    }

    private void addStep() {
        int lastTick = data.steps.isEmpty() ? 0 : data.steps.get(data.steps.size() - 1).tick + 60;
        PhantasiaSceneData.StepData s = new PhantasiaSceneData.StepData(lastTick, null);
        s.show = "all";
        checkpoint();
        data.steps.add(s);
        selectStep(data.steps.size() - 1);
        dirty = true;
    }

    private void addStepAtTick(int tick) {
        PhantasiaSceneData.StepData s = new PhantasiaSceneData.StepData(tick, null);
        s.show = "all";
        int insertAt = data.steps.size();
        for (int i = 0; i < data.steps.size(); i++)
            if (data.steps.get(i).tick > tick) {
                insertAt = i;
                break;
            }
        checkpoint();
        data.steps.add(insertAt, s);
        selectStep(insertAt);
        dirty = true;
    }

    private void deleteStep() {
        if (data.steps.size() <= 1) return;
        checkpoint();
        data.steps.remove(selectedStep);
        selectStep(Math.min(selectedStep, data.steps.size() - 1));
        dirty = true;
    }

    private void duplicateStep() {
        if (selectedStep < 0 || selectedStep >= data.steps.size()) return;
        PhantasiaSceneData.StepData copy = data.steps.get(selectedStep).copy();
        copy.tick += 60;
        checkpoint();
        data.steps.add(selectedStep + 1, copy);
        selectStep(selectedStep + 1);
        dirty = true;
    }

    private void moveStep(int from, int delta) {
        int to = from + delta;
        if (to < 0 || to >= data.steps.size()) return;
        checkpoint();
        Collections.swap(data.steps, from, to);
        selectedStep = to;
        rebuildVisibility();
        dirty = true;
    }

    private void captureCamera() {
        PhantasiaSceneData.StepData s = step();
        if (s.camera == null) s.camera = new PhantasiaScriptData.CameraData();
        checkpoint();
        if (camera != null) {
            s.camera.yaw = camera.getYaw();
            s.camera.pitch = camera.getPitch();
            s.camera.zoom = camera.getZoom();
        }
        if (camZoomBox != null)
            camZoomBox.setValue(s.camera.zoom > 0 ? String.valueOf(s.camera.zoom) : "");
        dirty = true;
    }

    private void selectStep(int i) {
        selectedStep = Mth.clamp(i, 0, data.steps.size() - 1);
        populateInputsFromStep();
        populateOverrideBoxes();
        rebuildVisibility();
    }

    private void save() {
        PhantasiaSceneLoader.save(data);
        dirty = false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Override helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns (or creates) the override for the currently selected placement. */
    private PhantasiaSceneData.MachineOverride ensureOverride() {
        if (selectedPlacement < 0) return null;
        PhantasiaSceneData.MachineOverride ov = step().getOverride(selectedPlacement);
        if (ov == null) {
            ov = new PhantasiaSceneData.MachineOverride();
            step().setOverride(selectedPlacement, ov);
        }
        return ov;
    }

    private void populateOverrideBoxes() {
        if (ovLayerBox == null) return;
        if (selectedPlacement < 0 || selectedPlacement >= data.placements.size()) return;
        PhantasiaSceneData.MachineOverride ov = step().getOverride(selectedPlacement);
        ovLayerBox.setValue(ov != null ? String.valueOf(ov.layer) : "");
        ovHidePosBox.setValue(ov != null ? serializePosList(ov.hidePositions) : "");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private int computeTotalTicks() {
        return data.steps.isEmpty() ? 60 : data.steps.get(data.steps.size() - 1).tick + 60;
    }

    private PhantasiaSceneData.StepData step() {
        if (selectedStep >= 0 && selectedStep < data.steps.size())
            return data.steps.get(selectedStep);
        return new PhantasiaSceneData.StepData();
    }

    private void populateInputsFromStep() {
        if (captionBox == null) return;
        PhantasiaSceneData.StepData s = step();
        captionBox.setValue(s.caption != null ? s.caption : "");
        tickBox.setValue(String.valueOf(s.tick));
        if (lerpTicksBox != null && s.camera != null)
            lerpTicksBox.setValue(String.valueOf(s.camera.lerpTicks > 0 ? s.camera.lerpTicks : 20));
        if (camZoomBox != null && s.camera != null)
            camZoomBox.setValue(s.camera.zoom > 0 ? String.valueOf(s.camera.zoom) : "");
    }

    private void hideAllInputs() {
        for (var box : List.of(captionBox, tickBox, lerpTicksBox, camZoomBox, scriptDurationBox,
                ovLayerBox, ovHidePosBox, newMachineIdBox,
                newOffsetXBox, newOffsetYBox, newOffsetZBox, sceneNameBox, sceneIconBox)) {
            if (box != null) {
                box.visible = false;
                box.active = false;
            }
        }
    }

    private void placeBox(EditBox box, int x, int y, int w, int h) {
        box.setX(x);
        box.setY(y);
        box.setWidth(w);
        box.setHeight(h);
        box.visible = true;
        box.active = true;
    }

    private void btn(GuiGraphics g, int mx, int my, int x, int y, int w, int h,
                     String label, int base, Runnable action) {
        boolean hov = isOver(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, hov ? C_BTN_HOV : base);
        if (hov) {
            g.fill(x, y, x + w, y + 1, C_ACCENT);
            g.fill(x, y + h - 1, x + w, y + h, C_ACCENT);
        }
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, hov ? C_ACCENT : C_TEXT, false);
        btns.add(new Btn(x, y, w, h, action));
    }

    private boolean isOver(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean isOver(double mx, double my, int x, int y, int w, int h) {
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

    private static List<int[]> parsePosList(String raw) {
        List<int[]> r = new ArrayList<>();
        if (raw == null || raw.isBlank()) return r;
        for (String e : raw.split(";")) {
            String[] p = e.trim().split(",");
            if (p.length >= 3) try {
                r.add(new int[] { Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()),
                        Integer.parseInt(p[2].trim()) });
            } catch (NumberFormatException ignored) {}
        }
        return r;
    }

    private static String serializePosList(List<int[]> list) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int[] p : list) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(p[0]).append(',').append(p[1]).append(',').append(p[2]);
        }
        return sb.toString();
    }

    private void togglePreview() {
        previewing = !previewing;
        previewTick = 0;
        previewAccum = 0f;
        if (!previewing) rebuildVisibility();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onClose() {
        if (dirty && !showingCloseConfirm) {
            showingCloseConfirm = true;
            return;
        }
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        Minecraft.getInstance().setScreen(parent);
    }

    private void forceClose() {
        dirty = false;
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
