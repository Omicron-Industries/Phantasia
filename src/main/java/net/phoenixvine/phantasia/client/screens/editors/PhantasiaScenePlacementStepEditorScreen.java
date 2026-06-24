package net.phoenixvine.phantasia.client.screens.editors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.client.camera.CameraView;
import net.phoenixvine.phantasia.client.camera.PhantasiaCamera;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.client.render.PhantasiaWorldRenderer;
import net.phoenixvine.phantasia.client.screens.PhantasiaScreen;
import net.phoenixvine.phantasia.common.data.pattern.PhantasiaScenePattern;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData;

import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

/**
 * Full-screen per-placement step override editor.
 *
 * Opens from the scene editor's placements panel ("Per-Step Config →") and
 * shows every scene step's override for one specific placement. The user can
 * switch between steps using the dot timeline at the bottom, and configure
 * show mode, layer/range, working state, fake recipe, and particles. When
 * the "pos" show mode is active a button opens {@link PhantasiaHidePosEditorScreen}
 * (which uses this screen as its {@link PhantasiaHidePosContext}).
 */
@OnlyIn(Dist.CLIENT)
public class PhantasiaScenePlacementStepEditorScreen extends PhantasiaScreen
                                                     implements PhantasiaHidePosContext {

    private static final int PANEL_W = 240;
    private static final int STEP_H = 28;  // bottom timeline height
    private static final int ITEM_H = 14;  // height of a single control row

    private static final float CAM_ORBIT = 0.4f;
    private static final float CAM_PAN = 0.02f;

    // ── References ───────────────────────────────────────────────────────────
    final PhantasiaSceneEditorScreen parent;
    private final PhantasiaSceneData data;
    private final int placementIndex;

    // ── Camera (own, does not mutate parent camera) ───────────────────────────
    private PhantasiaCamera camera;
    private boolean isPanning = false;

    // ── Selection ────────────────────────────────────────────────────────────
    private int selectedStep = 0;

    // ── Layer slider ─────────────────────────────────────────────────────────
    private boolean draggingLayer = false;
    private boolean draggingLayerMax = false;

    // ── Widgets ──────────────────────────────────────────────────────────────
    private EditBox layerBox;
    private EditBox layerMinBox;
    private EditBox layerMaxBox;
    private EditBox fakeRecipeBox;
    private EditBox particlesBox;

    // ─────────────────────────────────────────────────────────────────────────

    public PhantasiaScenePlacementStepEditorScreen(PhantasiaSceneEditorScreen parent,
                                                   PhantasiaSceneData data,
                                                   int placementIndex) {
        super(Component.literal("Per-Step Config"));
        this.parent = parent;
        this.data = data;
        this.placementIndex = placementIndex;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Init
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();

        if (camera == null && parent.camera != null) {
            PhantasiaCamera pc = parent.camera;
            camera = new PhantasiaCamera(pc.getYaw(), pc.getPitch(), pc.getZoom(),
                    pc.getTargetX(), pc.getTargetY(), pc.getTargetZ());
        } else if (camera == null) {
            camera = new PhantasiaCamera(-135f, -30f, 30f, 0f, 5f, 0f);
        }

        buildBoxes();
        populateBoxes();
        rebuildVisibility();
    }

    private void buildBoxes() {
        clearWidgets();

        layerBox = addW(new EditBox(font, 0, 0, 36, 12, Component.empty()));
        layerBox.setMaxLength(5);
        layerBox.setFilter(s -> s.matches("-?\\d*"));
        layerBox.setResponder(v -> {
            PhantasiaSceneData.MachineOverride ov = ov();
            if (ov != null) try {
                ov.layer = Integer.parseInt(v);
                dirty();
            } catch (NumberFormatException ignored) {}
        });

        layerMinBox = addW(new EditBox(font, 0, 0, 30, 12, Component.empty()));
        layerMinBox.setMaxLength(5);
        layerMinBox.setFilter(s -> s.matches("-?\\d*"));
        layerMinBox.setResponder(v -> {
            PhantasiaSceneData.MachineOverride ov = ov();
            if (ov != null) try {
                ov.layerMin = Integer.parseInt(v);
                dirty();
            } catch (NumberFormatException ignored) {}
        });

        layerMaxBox = addW(new EditBox(font, 0, 0, 30, 12, Component.empty()));
        layerMaxBox.setMaxLength(5);
        layerMaxBox.setFilter(s -> s.matches("-?\\d*"));
        layerMaxBox.setResponder(v -> {
            PhantasiaSceneData.MachineOverride ov = ov();
            if (ov != null) try {
                ov.layerMax = Integer.parseInt(v);
                dirty();
            } catch (NumberFormatException ignored) {}
        });

        fakeRecipeBox = addW(new EditBox(font, 0, 0, PANEL_W - 16, 12, Component.empty()));
        fakeRecipeBox.setMaxLength(128);
        fakeRecipeBox.setHint(Component.literal("gtceu:ebf_iron_ingot"));
        fakeRecipeBox.setResponder(v -> {
            PhantasiaSceneData.MachineOverride ov = ov();
            if (ov != null) {
                ov.fakeRecipeId = v.isBlank() ? null : v.trim();
                dirty();
            }
        });

        particlesBox = addW(new EditBox(font, 0, 0, PANEL_W - 16, 12, Component.empty()));
        particlesBox.setMaxLength(512);
        particlesBox.setHint(Component.literal("particle1;particle2"));
        particlesBox.setResponder(v -> {
            PhantasiaSceneData.MachineOverride ov = ov();
            if (ov != null) {
                ov.particleEffects = new ArrayList<>();
                for (String p : v.split(";")) {
                    String t = p.trim();
                    if (!t.isEmpty()) ov.particleEffects.add(t);
                }
                dirty();
            }
        });

        hideAllBoxes();
    }

    @Override
    public void hideAllInputs() {
        hideAllBoxes();
    }

    private void hideAllBoxes() {
        for (var box : List.of(layerBox, layerMinBox, layerMaxBox, fakeRecipeBox, particlesBox)) {
            if (box != null) {
                box.visible = false;
                box.active = false;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data helpers
    // ─────────────────────────────────────────────────────────────────────────

    private PhantasiaSceneData.StepData step() {
        return data.steps.get(Mth.clamp(selectedStep, 0, data.steps.size() - 1));
    }

    /** Returns the current step's override for this placement, or null if none. */
    private PhantasiaSceneData.MachineOverride ov() {
        return step().getOverride(placementIndex);
    }

    /** Returns or creates the current step's override for this placement. */
    private PhantasiaSceneData.MachineOverride ensureOv() {
        PhantasiaSceneData.MachineOverride ov = step().getOverride(placementIndex);
        if (ov == null) {
            ov = new PhantasiaSceneData.MachineOverride();
            step().setOverride(placementIndex, ov);
        }
        return ov;
    }

    private void dirty() {
        parent.dirty = true;
        parent.applyActiveStateToScene(step().working);
    }

    /** Returns [minY, maxY] for this placement, or null if not found. */
    private int[] placementYBounds() {
        if (parent.scenePattern == null) return null;
        for (PhantasiaScenePattern.PlacementEntry pe : parent.scenePattern.placements) {
            if (pe.index == placementIndex) return new int[] { pe.minY, pe.maxY };
        }
        return null;
    }

    private void populateBoxes() {
        PhantasiaSceneData.MachineOverride ov = ov();
        if (ov != null) {
            layerBox.setValue(String.valueOf(ov.layer));
            layerMinBox.setValue(String.valueOf(ov.layerMin));
            layerMaxBox.setValue(String.valueOf(ov.layerMax));
            fakeRecipeBox.setValue(ov.fakeRecipeId != null ? ov.fakeRecipeId : "");
            particlesBox.setValue(ov.particleEffects != null ? String.join(";", ov.particleEffects) : "");
        } else {
            layerBox.setValue("");
            layerMinBox.setValue("");
            layerMaxBox.setValue("");
            fakeRecipeBox.setValue("");
            particlesBox.setValue("");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tick
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (camera != null) camera.tick();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Render
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        btns.clear();
        hideAllBoxes();
        pendingTooltip = null;

        // Background
        g.fill(0, 0, width, height, C_BG());

        // 3D viewport (left side minus panel)
        int viewW = width - PANEL_W;
        int viewY = TOP_BAR_H;
        int viewH = height - TOP_BAR_H - STEP_H;
        if (parent.renderer != null && camera != null) {
            parent.renderer.setMousePos(mx < viewW ? mx : -1, my > viewY ? my : -1);
            CameraView view = camera.getView(partial);
            parent.renderer.render(view, 0, viewY, viewW, viewH);
        }

        renderTopBar(g, mx, my);
        renderLayerSlider(g, mx, my);
        renderPanel(g, mx, my);
        renderStepTimeline(g, mx, my);

        super.render(g, mx, my, partial);
        renderPendingTooltip(g, mx, my);
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private void renderTopBar(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, width, TOP_BAR_H, C_BAR());
        g.fill(0, TOP_BAR_H - 1, width, TOP_BAR_H, C_ACCENT());

        String pd = placementIndex < data.placements.size() ? data.placements.get(placementIndex).machine :
                "#" + placementIndex;
        g.drawCenteredString(font,
                "Per-Step Config  —  Placement #" + placementIndex + "  " + pd,
                width / 2, (TOP_BAR_H - 8) / 2, C_DIM());

        topBtn(g, mx, my, width - 4, "← Back", C_BTN(),
                "Return to scene editor",
                () -> { restoreAllBaseplates(); Minecraft.getInstance().setScreen(parent); });
    }

    // ── Right panel ───────────────────────────────────────────────────────────

    private static final String[] SHOW_MODES = { "all", "layer", "layers", "pos" };
    private static final String[] SHOW_LABELS = { "All", "Layer", "Range", "Pos" };

    private void renderPanel(GuiGraphics g, int mx, int my) {
        int px = width - PANEL_W;
        int py = TOP_BAR_H;
        int ph = height - TOP_BAR_H - STEP_H;

        g.fill(px, py, width, py + ph, C_PANEL());
        g.fill(px, py, px + 1, py + ph, C_ACCENT());

        PhantasiaSceneData.MachineOverride ov = ov();
        String curShow = ov != null && ov.show != null ? ov.show : null; // null = global

        int cy = py + 8;

        g.drawString(font, "Step " + (selectedStep + 1) + " / " + data.steps.size() + " override",
                px + 6, cy, C_ACCENT(), false);
        cy += 14;

        // ── Show mode ─────────────────────────────────────────────────────────
        g.drawString(font, Component.translatable("screen.phantasia.placement_step_editor.label_show").getString(),
                px + 6, cy + 2, C_DIM(), false);
        // "Global" button (inherit from step)
        int bx = px + 6 +
                font.width(Component.translatable("screen.phantasia.placement_step_editor.label_show").getString()) + 4;
        boolean gSel = (curShow == null);
        boolean gHov = isOver(mx, my, bx, cy, 40, 13);
        g.fill(bx, cy, bx + 40, cy + 13, gSel ? C_BTN_ACT() : (gHov ? C_BTN_HOV() : C_BTN()));
        if (gSel) g.fill(bx, cy, bx + 40, cy + 1, C_DIM());
        g.drawString(font, Component.translatable("screen.phantasia.placement_step_editor.btn_global").getString(),
                bx + 3, cy + 3, gSel ? C_DIM() : C_TEXT(), false);
        if (gHov) pendingTooltip = "Inherit the step's global show mode for this placement";
        btns.add(new Btn(bx, cy, 40, 13, () -> {
            parent.checkpoint();
            PhantasiaSceneData.MachineOverride o = ov();
            if (o != null) {
                o.show = null;
                rebuildVisibility();
                dirty();
            }
        }));
        cy += 17;

        bx = px + 6;
        for (int si = 0; si < SHOW_MODES.length; si++) {
            String sm = SHOW_MODES[si], sml = SHOW_LABELS[si];
            int smW = font.width(sml) + 10;
            boolean smSel = sm.equals(curShow);
            boolean smHov = isOver(mx, my, bx, cy, smW, 13);
            g.fill(bx, cy, bx + smW, cy + 13, smSel ? C_BTN_ACT() : (smHov ? C_BTN_HOV() : C_BTN()));
            if (smSel) g.fill(bx, cy, bx + smW, cy + 1, C_ACCENT());
            g.drawString(font, sml, bx + 5, cy + 3, smSel ? C_ACCENT() : C_TEXT(), false);
            final String fsm = sm;
            btns.add(new Btn(bx, cy, smW, 13, () -> {
                parent.checkpoint();
                PhantasiaSceneData.MachineOverride o = ensureOv();
                o.show = fsm;
                // Clamp/initialize layer values to valid bounds when switching into
                // layer or layers mode — mirrors the script editor's behaviour so you
                // never land on an out-of-bounds Y that shows nothing.
                if ("layer".equals(fsm) || "layers".equals(fsm)) {
                    int[] bounds = placementYBounds();
                    if (bounds != null) {
                        int minY = bounds[0], maxY = bounds[1];
                        if ("layer".equals(fsm)) {
                            if (o.layer < minY || o.layer > maxY)
                                o.layer = (minY + maxY) / 2;
                        } else {
                            boolean uninit = o.layerMin == 0 && o.layerMax == 0 && maxY > 0;
                            if (uninit || o.layerMin < minY || o.layerMin > maxY) o.layerMin = minY;
                            if (uninit || o.layerMax < minY || o.layerMax > maxY) o.layerMax = maxY;
                            if (o.layerMin >= o.layerMax) {
                                o.layerMin = minY;
                                o.layerMax = maxY;
                            }
                        }
                    }
                }
                rebuildVisibility();
                dirty();
                populateBoxes();
                if ("pos".equals(fsm)) openPosEditor();
            }));
            bx += smW + 3;
        }
        cy += 17;

        // ── Layer / Range inputs ──────────────────────────────────────────────
        if ("layer".equals(curShow) && ov != null) {
            g.drawString(font, "Y:", px + 6, cy + 2, C_DIM(), false);
            placeBox(layerBox, px + 6 + font.width("Y:") + 4, cy, 36, 12);
            cy += ITEM_H + 2;
        }
        if ("layers".equals(curShow) && ov != null) {
            g.drawString(font, Component.translatable("screen.phantasia.placement_step_editor.label_min").getString(),
                    px + 6, cy + 2, C_DIM(), false);
            int mxLeft = px + 6 +
                    font.width(Component.translatable("screen.phantasia.placement_step_editor.label_min").getString()) +
                    4;
            placeBox(layerMinBox, mxLeft, cy, 30, 12);
            int mxRight = mxLeft + 34;
            g.drawString(font, Component.translatable("screen.phantasia.placement_step_editor.label_max").getString(),
                    mxRight, cy + 2, C_DIM(), false);
            placeBox(layerMaxBox,
                    mxRight + font.width(
                            Component.translatable("screen.phantasia.placement_step_editor.label_max").getString()) + 4,
                    cy, 30, 12);
            cy += ITEM_H + 2;
        }
        if ("pos".equals(curShow)) {
            int posCount = ov != null ? ov.positions.size() : 0;
            int pbW = PANEL_W - 14;
            boolean pbHov = isOver(mx, my, px + 6, cy, pbW, ITEM_H);
            g.fill(px + 6, cy, px + 6 + pbW, cy + ITEM_H,
                    pbHov ? C_BTN_HOV() : C_BTN());
            if (pbHov) g.fill(px + 6, cy, px + 6 + pbW, cy + 1, C_ACCENT());
            g.drawCenteredString(font,
                    String.format(Component.translatable("screen.phantasia.placement_step_editor.btn_edit_positions")
                            .getString(), posCount),
                    px + 6 + pbW / 2, cy + 3,
                    pbHov ? C_ACCENT() : C_DIM());
            if (pbHov) pendingTooltip = "Open the block-position picker for this placement on this step";
            btns.add(new Btn(px + 6, cy, pbW, ITEM_H, this::openPosEditor));
            cy += ITEM_H + 4;
        }

        cy += 4;
        g.fill(px + 6, cy, px + PANEL_W - 6, cy + 1, 0x22FFFFFF);
        cy += 8;

        // ── Working state ─────────────────────────────────────────────────────
        g.drawString(font, Component.translatable("screen.phantasia.placement_step_editor.label_working").getString(),
                px + 6, cy + 2, C_DIM(), false);
        Boolean curW = ov != null ? ov.machineWorking : null;
        String[] wLabels = { "Global", "Idle", "Active" };
        Boolean[] wVals = { null, false, true };
        int[] wCols = { 0xFF334455, 0xFF5577AA, 0xFF1A7040 };
        int wbx = px + 6;
        for (int wi = 0; wi < wLabels.length; wi++) {
            String wl = wLabels[wi];
            Boolean wv = wVals[wi];
            int wc = wCols[wi];
            int wW = font.width(wl) + 8;
            boolean wSel = (curW == null && wv == null) || (curW != null && curW.equals(wv));
            boolean wHov = isOver(mx, my, wbx, cy, wW, 12);
            g.fill(wbx, cy, wbx + wW, cy + 12,
                    wSel ? (wi == 2 ? 0xFF0D3A20 : C_BTN_ACT()) : (wHov ? C_BTN_HOV() : C_BTN()));
            if (wSel) g.fill(wbx, cy, wbx + wW, cy + 1, wc);
            g.drawString(font, wl, wbx + 4, cy + 2, wSel ? wc : C_TEXT(), false);
            final Boolean fwv = wv;
            btns.add(new Btn(wbx, cy, wW, 12, () -> {
                parent.checkpoint();
                ensureOv().machineWorking = fwv;
                dirty();
            }));
            wbx += wW + 3;
        }
        cy += 16;

        cy += 4;
        g.fill(px + 6, cy, px + PANEL_W - 6, cy + 1, 0x22FFFFFF);
        cy += 8;

        // ── Fake recipe ───────────────────────────────────────────────────────
        g.drawString(font, Component.translatable("screen.phantasia.placement_step_editor.label_recipe").getString(),
                px + 6, cy + 2, C_DIM(), false);
        cy += 12;
        placeBox(fakeRecipeBox, px + 6, cy, PANEL_W - 14, 12);
        cy += 16;

        // ── Particles ─────────────────────────────────────────────────────────
        g.drawString(font, Component.translatable("screen.phantasia.placement_step_editor.label_particles").getString(),
                px + 6, cy + 2, C_DIM(), false);
        cy += 12;
        placeBox(particlesBox, px + 6, cy, PANEL_W - 14, 12);
        cy += 16;

        // ── Edit placement / items ────────────────────────────────────────────
        cy += 4;
        g.fill(px + 6, cy, px + PANEL_W - 6, cy + 1, 0x22FFFFFF);
        cy += 8;
        int epW = PANEL_W - 14;
        boolean epHov = isOver(mx, my, px + 6, cy, epW, ITEM_H);
        g.fill(px + 6, cy, px + 6 + epW, cy + ITEM_H, epHov ? C_BTN_HOV() : C_BTN());
        if (epHov) g.fill(px + 6, cy, px + 6 + epW, cy + 1, C_ACCENT());
        g.drawCenteredString(font,
                Component.translatable("screen.phantasia.placement_step_editor.btn_edit_placement").getString(),
                px + 6 + epW / 2, cy + 3, epHov ? C_ACCENT() : C_DIM());
        if (epHov) pendingTooltip = "Open the placement editor (machine ID, offset, item conditions)";
        btns.add(new Btn(px + 6, cy, epW, ITEM_H, () -> Minecraft.getInstance().setScreen(
                new PhantasiaPlacementEditorScreen(parent, data, placementIndex))));
        cy += ITEM_H + 4;

        // ── Clear override ────────────────────────────────────────────────────
        if (ov != null) {
            cy += 6;
            int cbW = PANEL_W - 14;
            boolean cbHov = isOver(mx, my, px + 6, cy, cbW, 12);
            g.fill(px + 6, cy, px + 6 + cbW, cy + 12, cbHov ? 0xFF3A1010 : C_BTN());
            g.drawCenteredString(font,
                    Component.translatable("screen.phantasia.placement_step_editor.btn_clear_override").getString(),
                    px + 6 + cbW / 2, cy + 2, cbHov ? C_RED() : C_DIM());
            if (cbHov) pendingTooltip = "Remove all overrides for this placement on this step";
            btns.add(new Btn(px + 6, cy, cbW, 12, () -> {
                parent.checkpoint();
                step().removeOverride(placementIndex);
                populateBoxes();
                rebuildVisibility();
                dirty();
            }));
        }
    }

    // ── Layer slider ─────────────────────────────────────────────────────────

    private void renderLayerSlider(GuiGraphics g, int mx, int my) {
        PhantasiaSceneData.MachineOverride ov = ov();
        if (ov == null) return;
        String show = ov.show != null ? ov.show : "";
        if (!"layer".equals(show) && !"layers".equals(show)) return;
        if (parent.scenePattern == null) return;

        // Find Y bounds for this placement
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (PhantasiaScenePattern.PlacementEntry pe : parent.scenePattern.placements) {
            if (pe.index == placementIndex) {
                minY = pe.minY;
                maxY = pe.maxY;
                break;
            }
        }
        if (minY == Integer.MAX_VALUE) return;

        int sliderX = 10, sceneTop = TOP_BAR_H, sceneBottom = height - STEP_H;
        int sliderH = sceneBottom - sceneTop - 24, sliderY = sceneTop + 12;
        int range = Math.max(1, maxY - minY);

        g.drawString(font, Component.translatable("screen.phantasia.placement_step_editor.label_layer").getString(),
                sliderX - 2, sliderY - 20, C_DIM(), false);
        g.drawString(font, Component.translatable("screen.phantasia.placement_step_editor.label_filter").getString(),
                sliderX - 2, sliderY - 11, C_DIM(), false);
        g.fill(sliderX + 3, sliderY, sliderX + 7, sliderY + sliderH, 0x44FFFFFF);
        g.drawString(font, "Y=" + maxY, sliderX + 16, sliderY - 1, C_DIM(), false);
        g.drawString(font, "Y=" + minY, sliderX + 16, sliderY + sliderH - 1, C_DIM(), false);

        if ("layer".equals(show)) {
            int layer = ov.layer;
            float t = 1f - (float) (net.minecraft.util.Mth.clamp(layer, minY, maxY) - minY) / range;
            int thumbY = sliderY + (int) (t * sliderH);
            boolean hov = isOver(mx, my, sliderX - 2, thumbY - 7, 18, 14) || draggingLayer;
            g.fill(sliderX - 2, thumbY - 6, sliderX + 16, thumbY + 6, hov ? C_ACCENT() : C_BTN_ACT());
            g.drawString(font, String.valueOf(layer), sliderX + 18, thumbY - 4, C_ACCENT(), false);
            if (hov) pendingTooltip = "Drag to change visible layer (Y=" + layer + ")";
        } else {
            int curMin = net.minecraft.util.Mth.clamp(ov.layerMin, minY, maxY);
            int curMax = net.minecraft.util.Mth.clamp(ov.layerMax, minY, maxY);
            float tMin = 1f - (float) (curMin - minY) / range;
            float tMax = 1f - (float) (curMax - minY) / range;
            int tyMin = sliderY + (int) (tMin * sliderH);
            int tyMax = sliderY + (int) (tMax * sliderH);
            g.fill(sliderX + 2, tyMax, sliderX + 8, tyMin, 0x664FC3F7);
            boolean hovMin = isOver(mx, my, sliderX - 2, tyMin - 5, 18, 12) || (draggingLayer && !draggingLayerMax);
            boolean hovMax = isOver(mx, my, sliderX - 2, tyMax - 5, 18, 12) || (draggingLayer && draggingLayerMax);
            g.fill(sliderX - 2, tyMin - 5, sliderX + 16, tyMin + 5, hovMin ? C_ACCENT() : C_BTN_ACT());
            g.fill(sliderX - 2, tyMax - 5, sliderX + 16, tyMax + 5, hovMax ? C_ACCENT() : C_BTN_ACT());
            g.drawString(font, curMin + "→" + curMax, sliderX + 18, (tyMin + tyMax) / 2 - 4, C_ACCENT(), false);
            if (hovMin) pendingTooltip = "Drag to set min layer (Y=" + curMin + ")";
            if (hovMax) pendingTooltip = "Drag to set max layer (Y=" + curMax + ")";
        }
    }

    private boolean startLayerDrag(double mx, double my) {
        PhantasiaSceneData.MachineOverride ov = ov();
        if (ov == null) return false;
        String show = ov.show != null ? ov.show : "";
        if (!"layer".equals(show) && !"layers".equals(show)) return false;
        if (parent.scenePattern == null) return false;

        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (PhantasiaScenePattern.PlacementEntry pe : parent.scenePattern.placements) {
            if (pe.index == placementIndex) {
                minY = pe.minY;
                maxY = pe.maxY;
                break;
            }
        }
        if (minY == Integer.MAX_VALUE) return false;

        int sliderX = 10, sceneTop = TOP_BAR_H, sceneBottom = height - STEP_H;
        int sliderH = sceneBottom - sceneTop - 24, sliderY = sceneTop + 12;
        int range = Math.max(1, maxY - minY);

        if ("layer".equals(show)) {
            float t = 1f - (float) (net.minecraft.util.Mth.clamp(ov.layer, minY, maxY) - minY) / range;
            int thumbY = sliderY + (int) (t * sliderH);
            if (isOver(mx, my, sliderX - 2, thumbY - 7, 18, 14)) {
                draggingLayer = true;
                draggingLayerMax = false;
                return true;
            }
        } else {
            int curMin = net.minecraft.util.Mth.clamp(ov.layerMin, minY, maxY);
            int curMax = net.minecraft.util.Mth.clamp(ov.layerMax, minY, maxY);
            float tMin = 1f - (float) (curMin - minY) / range;
            float tMax = 1f - (float) (curMax - minY) / range;
            int tyMin = sliderY + (int) (tMin * sliderH);
            int tyMax = sliderY + (int) (tMax * sliderH);
            if (isOver(mx, my, sliderX - 2, tyMin - 7, 18, 14)) {
                draggingLayer = true;
                draggingLayerMax = false;
                return true;
            }
            if (isOver(mx, my, sliderX - 2, tyMax - 7, 18, 14)) {
                draggingLayer = true;
                draggingLayerMax = true;
                return true;
            }
        }
        return false;
    }

    private void applyLayerDrag(double mx, double my) {
        PhantasiaSceneData.MachineOverride ov = ov();
        if (ov == null) return;
        String show = ov.show != null ? ov.show : "";
        if (parent.scenePattern == null) return;

        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (PhantasiaScenePattern.PlacementEntry pe : parent.scenePattern.placements) {
            if (pe.index == placementIndex) {
                minY = pe.minY;
                maxY = pe.maxY;
                break;
            }
        }
        if (minY == Integer.MAX_VALUE) return;

        int sliderY = TOP_BAR_H + 12;
        int sliderH = (height - STEP_H) - TOP_BAR_H - 24;
        int range = Math.max(1, maxY - minY);
        float t = net.minecraft.util.Mth.clamp(1f - (float) (my - sliderY) / sliderH, 0f, 1f);
        int newY = minY + (int) (t * range);

        if ("layer".equals(show)) {
            if (ensureOv().layer != newY) {
                ensureOv().layer = newY;
                rebuildVisibility();
                dirty();
            }
        } else {
            if (draggingLayerMax) {
                if (ensureOv().layerMax != newY) {
                    ensureOv().layerMax = newY;
                    rebuildVisibility();
                    dirty();
                }
            } else {
                if (ensureOv().layerMin != newY) {
                    ensureOv().layerMin = newY;
                    rebuildVisibility();
                    dirty();
                }
            }
        }
    }

    // ── Step timeline ─────────────────────────────────────────────────────────

    private void renderStepTimeline(GuiGraphics g, int mx, int my) {
        int rowY = height - STEP_H;
        int padL = 30, padR = PANEL_W + 20;
        int trackW = width - padL - padR;
        int total = data.steps.isEmpty() ? 60 : data.steps.get(data.steps.size() - 1).tick + 60;
        int midY = rowY + STEP_H / 2;

        g.fill(0, rowY, width, height, C_PANEL());
        g.fill(0, rowY, width, rowY + 1, 0x33FFFFFF);
        g.fill(padL, midY - 1, padL + trackW, midY + 1, 0xFF1A2C3C);

        for (int i = 0; i < data.steps.size(); i++) {
            PhantasiaSceneData.StepData s = data.steps.get(i);
            float t = total > 0 ? (float) s.tick / total : (float) i / Math.max(1, data.steps.size() - 1);
            int dotX = padL + (int) (t * trackW);
            boolean sel = (i == selectedStep);
            boolean hov = isOver(mx, my, dotX - 8, midY - 8, 16, 16);
            boolean hasOv = s.getOverride(placementIndex) != null;

            g.fill(dotX - 6, midY - 6, dotX + 6, midY + 6,
                    sel ? C_ACCENT() : (hov ? 0xFFAADDFF : (hasOv ? 0xFF2244AA : 0xFF3A506A)));
            g.fill(dotX - 4, midY - 4, dotX + 4, midY + 4,
                    sel ? 0xFF1A3C5C : 0xFF0A1520);

            if (hasOv && !sel)
                g.fill(dotX - 3, midY - 6, dotX + 3, midY - 4, C_ACCENT());

            g.drawCenteredString(font, String.valueOf(i + 1), dotX, midY - 3,
                    sel ? C_ACCENT() : C_DIM());

            if (s.caption != null && !s.caption.isEmpty())
                g.drawCenteredString(font, trunc(s.caption, 60), dotX, midY + 7,
                        sel ? C_DIM() : 0x33FFFFFF);

            if (hov) pendingTooltip = "Step " + (i + 1) + "  t=" + s.tick +
                    (s.caption != null ? ": " + s.caption : "") + (hasOv ? "  [has override]" : "");

            final int fi = i;
            btns.add(new Btn(dotX - 8, midY - 8, 16, 16, () -> {
                selectedStep = fi;
                parent.selectedStep = fi;
                populateBoxes();
                rebuildVisibility();
                parent.applyActiveStateToScene(step().working);
            }));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pos editor
    // ─────────────────────────────────────────────────────────────────────────

    private void openPosEditor() {
        ensureOv(); // guarantee the override + hidePositions list exists
        Minecraft.getInstance().setScreen(new PhantasiaHidePosEditorScreen(this));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PhantasiaHidePosContext impl
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public String getHidePosLabel() {
        return "Placement #" + placementIndex + ", Step " + (selectedStep + 1);
    }

    @Override
    public Screen returnScreen() {
        rebuildVisibility();
        return this;
    }

    @Override
    public PhantasiaWorldRenderer getRenderer() {
        return parent.renderer;
    }

    @Override
    public PhantasiaCamera getParentCamera() {
        return camera;
    }

    @Override
    public PhantasiaTrackedDummyWorld getEditorLevel() {
        return parent.editorLevel;
    }

    @Override
    public BlockPos localToWorld(int[] localXYZ) {
        if (parent.scenePattern == null) return null;
        for (PhantasiaScenePattern.PlacementEntry pe : parent.scenePattern.placements) {
            if (pe.index == placementIndex)
                return pe.offset.offset(localXYZ[0], localXYZ[1], localXYZ[2]);
        }
        return null;
    }

    @Override
    public int[] worldToLocal(BlockPos world) {
        if (parent.scenePattern == null) return null;
        for (PhantasiaScenePattern.PlacementEntry pe : parent.scenePattern.placements) {
            if (pe.index == placementIndex) {
                BlockPos local = world.subtract(pe.offset);
                return new int[] { local.getX(), local.getY(), local.getZ() };
            }
        }
        return null;
    }

    @Override
    public List<int[]> getHidePositions() {
        // "pos" mode in scene context is a SHOW list (positions), not a hide list
        return ensureOv().positions;
    }

    @Override
    public void checkpoint() {
        parent.checkpoint();
    }

    @Override
    public void markDirty() {
        dirty();
    }

    @Override
    public void rebuildVisibility() {
        if (parent.renderer == null || parent.scenePattern == null) return;
        Set<BlockPos> visible = parent.scenePattern.computeVisible(step(), data);

        // Hide blocks and baseplates belonging to other placements.
        Set<BlockPos> otherPositions = new java.util.HashSet<>();
        Set<BlockPos> selectedBaseplates = new java.util.HashSet<>();
        for (PhantasiaScenePattern.PlacementEntry pe : parent.scenePattern.placements) {
            if (pe.index != placementIndex) {
                otherPositions.addAll(pe.worldPositions);
                otherPositions.addAll(pe.baseplatePositions);
            } else {
                selectedBaseplates.addAll(pe.baseplatePositions);
            }
        }

        Set<BlockPos> filtered = visible != null ? new java.util.HashSet<>(visible) : new java.util.HashSet<>();
        filtered.removeAll(otherPositions);

        // Restrict the renderer's baseplatePositions to only the selected placement so that
        // scheduleFullBake does not unconditionally include other placements' baseplates.
        parent.renderer.setBaseplatePositions(selectedBaseplates);

        parent.renderer.setVisible(filtered);
        parent.renderer.requestBake();
    }

    /** Restores all baseplates when leaving this screen. */
    private void restoreAllBaseplates() {
        if (parent.renderer != null && parent.scenePattern != null) {
            parent.renderer.setBaseplatePositions(parent.scenePattern.allBaseplatePositions);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Input
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        for (Btn b : btns) if (b.hit(mx, my)) {
            b.action().run();
            return true;
        }
        if (super.mouseClicked(mx, my, btn)) return true;
        if (btn == 0 && mx < width - PANEL_W && my > TOP_BAR_H && startLayerDrag(mx, my)) return true;
        if (btn == 2 && mx < width - PANEL_W && my > TOP_BAR_H) {
            isPanning = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (draggingLayer && btn == 0) {
            applyLayerDrag(mx, my);
            return true;
        }
        if (camera == null) return super.mouseDragged(mx, my, btn, dx, dy);
        if (mx >= width - PANEL_W) return super.mouseDragged(mx, my, btn, dx, dy);
        if (btn == 0) {
            camera.orbit((float) dx * CAM_ORBIT, (float) dy * CAM_ORBIT);
            return true;
        }
        if (btn == 2 && isPanning) {
            Vector3f right = new Vector3f(), up = new Vector3f();
            camera.getRightAndUp(right, up);
            float s = CAM_PAN;
            camera.pan(
                    (right.x * (float) -dx + up.x * (float) dy) * s,
                    (right.y * (float) -dx + up.y * (float) dy) * s,
                    (right.z * (float) -dx + up.z * (float) dy) * s);
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 0) draggingLayer = false;
        if (btn == 2) isPanning = false;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < width - PANEL_W && my > TOP_BAR_H && camera != null) {
            camera.zoom(delta > 0 ? 0.9f : 1.1f, 2f, 300f);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        boolean ctrl = (mod & 2) != 0;
        if (ctrl && kc == GLFW.GLFW_KEY_Z) {
            parent.checkpoint();
            return true;
        }
        if (kc == GLFW.GLFW_KEY_ESCAPE) {
            restoreAllBaseplates();
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        if (getFocused() != null && getFocused().keyPressed(kc, sc, mod)) return true;
        return super.keyPressed(kc, sc, mod);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
