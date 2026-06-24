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
import net.phoenixvine.phantasia.client.camera.LerpType;
import net.phoenixvine.phantasia.client.camera.PhantasiaCamera;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.client.render.PhantasiaWorldRenderer;
import net.phoenixvine.phantasia.client.screens.PhantasiaScreen;
import net.phoenixvine.phantasia.common.data.pattern.PhantasiaScenePattern;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneLoader;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.*;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

/**
 * PhantasiaSceneEditorScreen
 *
 * Manual scene editor. Mirrors the style of {@link PhantasiaScriptEditorScreen} but
 * operates on {@link PhantasiaSceneData} instead of per-machine script data.
 *
 * Key differences from the machine editor:
 * - A Component.translatable("screen.phantasia.scene_editor.section_placements").getString() side panel (toggle with ⊞
 * button in top bar) for adding,
 * removing, and repositioning machine placements.
 * - Per-placement visibility overrides in the step row, shown when a placement
 * is selected in the placements panel.
 * - No SELECT mode block-position picking (positions are global across the merged
 * world; use the pos show mode with manual entry or future click-to-add support).
 * - World is rebuilt when placements change.
 */
@OnlyIn(Dist.CLIENT)
public class PhantasiaSceneEditorScreen extends PhantasiaScreen {

    private static final int STEP_ROW_H = 50;
    private static final int TIMELINE_H = 22;
    private static final int BOTTOM_H = STEP_ROW_H + TIMELINE_H;

    /** Height of the camera floating panel that overlaps the 3-D viewport */
    private static final int CAM_PANEL_H = 52;

    private static final float CAM_ORBIT_SENSITIVITY = 0.35f;
    private static final float CAM_PAN_SENSITIVITY = 0.02f;

    private static final String[] SHOW_MODES = { "all", "layer", "layers", "pos" };
    private static final String[] SHOW_LABELS = { "All", "Layer", "Range", "Pos" };

    // ── Data ──────────────────────────────────────────────────────────────────
    private final Screen parent;
    PhantasiaSceneData data;
    private EditBox sceneIconBox;
    boolean dirty = false;
    int selectedStep = 0;

    // ── World ─────────────────────────────────────────────────────────────────
    PhantasiaWorldRenderer renderer;
    PhantasiaTrackedDummyWorld editorLevel;
    PhantasiaScenePattern scenePattern;

    // ── Camera ────────────────────────────────────────────────────────────────
    PhantasiaCamera camera;
    /** Camera floating panel toggle (top-bar tab) */
    private boolean showCameraPanel = false;

    // ── WORLD mode ────────────────────────────────────────────────────────────
    /** World-item floating panel toggle (top-bar tab) */
    private boolean showWorldItemPanel = false;
    /** Local-space selected block in WORLD mode (relative to placement offset). */
    private BlockPos wiBlock = null;
    /** Which placement wiBlock belongs to. */
    private int wiPlacementIdx = -1;
    private EditBox wiItemBox;
    private EditBox wiSourceBox;
    private BlockPos hoveredWorldPos = null;
    private float sceneSelectPulse = 0f;
    private boolean sceneSelectPulseUp = true;
    private static final int WI_PANEL_H = 62;

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
    private int sceneTick = 0;

    // ── Start-cam panel ───────────────────────────────────────────────────────
    private boolean showStartCamPanel = false;
    private EditBox scYawBox;
    private EditBox scPitchBox;
    private EditBox scZoomBox;

    // ── Unsaved confirm ───────────────────────────────────────────────────────
    private boolean showingCloseConfirm = false;

    // ── Undo ──────────────────────────────────────────────────────────────────
    private static final int MAX_UNDO = 20;
    private final ArrayDeque<PhantasiaSceneData> undoStack = new ArrayDeque<>();

    // ── Clipboard ─────────────────────────────────────────────────────────────
    /** Deep-copied step held by Ctrl+C; null until the user copies something. */
    private PhantasiaSceneData.StepData clipboard = null;

    // ── Inputs ────────────────────────────────────────────────────────────────
    private EditBox captionBox;
    private EditBox descriptionBox;
    private EditBox tickBox;
    private EditBox lerpTicksBox;
    private EditBox camZoomBox;

    // Per-placement override boxes (reused for whichever placement is selected)
    private EditBox ovLayerBox;
    private EditBox ovHidePosBox;
    // Per-placement recipe / particle override boxes
    private EditBox ovFakeRecipeBox;
    private EditBox ovParticleBox;
    // Add-placement inputs
    private EditBox newMachineIdBox;
    private EditBox newOffsetXBox;
    private EditBox newOffsetYBox;
    private EditBox newOffsetZBox;
    // Scene metadata
    private EditBox sceneNameBox;
    private EditBox tooltipItemBox;

    private int lastMX, lastMY;

    // ── Item panel hover state ─────────────────────────────────────────────────
    private int hoveredItemPlacement = -1;
    private int hoveredItemIndex = -1;
    /** Screen bounds of the currently-rendered item strip panel, or null if not showing. Used for click-outside-to-close. */
    private int[] itemStripBounds = null;

    // ─────────────────────────────────────────────────────────────────────────

    public PhantasiaSceneEditorScreen(Screen parent, PhantasiaSceneData original) {
        super(Component.translatable("screen.phantasia.scene_editor.title"));
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
        net.phoenixvine.phantasia.client.render.PhantasiaParticleEngine.init();
        if (renderer == null) rebuildWorld();
        buildInputWidgets();
        populateInputsFromStep();
    }

    void rebuildWorld() {
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }

        editorLevel = new PhantasiaTrackedDummyWorld();
        editorLevel.clearSceneEntities();
        scenePattern = PhantasiaScenePattern.build(data, editorLevel);

        renderer = new PhantasiaWorldRenderer(editorLevel);
        if (scenePattern != null) {
            renderer.setBaseplatePositions(scenePattern.allBaseplatePositions);

            Set<BlockPos> fullBakeSet = new HashSet<>(editorLevel.renderedBlocks.keySet());
            fullBakeSet.addAll(scenePattern.allBaseplatePositions);
            PhantasiaSceneData.StepData allStep = new PhantasiaSceneData.StepData();
            allStep.show = "all";
            Set<BlockPos> allVisible = scenePattern.computeVisible(allStep, data);
            if (allVisible != null) fullBakeSet.addAll(allVisible);

            renderer.setPatternBlocks(fullBakeSet);
            renderer.requestBake();
        }

        initCamera();
        applyActiveStateToScene(step().working);
        applyWorldItemsToEditorLevel();
        rebuildVisibility();

        // onShapeLoaded defers onStructureFormed via recordRenderCall — queue a second
        // applyActiveStateToScene so it fires AFTER onStructureFormed, ensuring
        // RecipeLogic stays WORKING instead of being reset by onStructureFormed.
        final boolean deferredWorking = step().working;
        com.mojang.blaze3d.systems.RenderSystem.recordRenderCall(
                () -> applyActiveStateToScene(deferredWorking));
    }

    private void initCamera() {
        if (scenePattern == null || scenePattern.placements.isEmpty()) {
            camera = new PhantasiaCamera(-135f, -30f, 30f, 0f, 5f, 0f);
            return;
        }

        float sumX = 0, sumZ = 0;
        for (PhantasiaScenePattern.PlacementEntry pe : scenePattern.placements) {
            sumX += pe.centerX;
            sumZ += pe.centerZ;
        }
        float midX = sumX / scenePattern.placements.size();
        float midZ = sumZ / scenePattern.placements.size();
        float midY = (scenePattern.minY + scenePattern.maxY) * 0.5f + 0.5f;

        float spanX = 0, spanZ = 0;
        for (PhantasiaScenePattern.PlacementEntry pe : scenePattern.placements) {
            spanX = Math.max(spanX, Math.abs(pe.centerX - midX));
            spanZ = Math.max(spanZ, Math.abs(pe.centerZ - midZ));
        }
        float dist = 20f + Math.max(spanX, spanZ) * 1.5f;

        camera = new PhantasiaCamera(-135f, -30f, dist, midX, midY, midZ);
        camera.setFloorY(scenePattern.minY + 0.5f);
    }

    private void buildInputWidgets() {
        clearWidgets();

        captionBox = addW(new EditBox(font, 0, 0, 200, 12, Component.empty()));
        captionBox.setMaxLength(256);
        captionBox.setHint(Component.translatable("screen.phantasia.scene_editor.hint_caption"));
        captionBox.setResponder(v -> {
            step().caption = v.isBlank() ? null : v;
            dirty = true;
        });

        descriptionBox = addW(new EditBox(font, 0, 0, 200, 12, Component.empty()));
        descriptionBox.setMaxLength(2048);
        descriptionBox.setHint(Component.translatable("screen.phantasia.scene_editor.hint_desc"));
        descriptionBox.setResponder(v -> {
            step().description = v.isBlank() ? null : v;
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
        lerpTicksBox.setHint(Component.translatable("screen.phantasia.scene_editor.hint_lerp_ticks"));
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
        camZoomBox.setHint(Component.translatable("screen.phantasia.scene_editor.hint_zoom"));
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

        // WORLD mode boxes
        wiItemBox = addW(new EditBox(font, 0, 0, 200, 12, Component.empty()));
        wiItemBox.setMaxLength(120);
        wiItemBox.setHint(Component.literal("namespace:item  (blank = clear slot)"));
        wiItemBox.visible = false;
        wiItemBox.active = false;

        wiSourceBox = addW(new EditBox(font, 0, 0, 54, 12, Component.empty()));
        wiSourceBox.setMaxLength(6);
        wiSourceBox.setHint(Component.literal("1-10k"));
        wiSourceBox.setFilter(v -> v.matches("\\d*"));
        wiSourceBox.visible = false;
        wiSourceBox.active = false;

        // Per-placement override boxes
        ovLayerBox = addW(new EditBox(font, 0, 0, 28, 12, Component.empty()));
        ovLayerBox.setMaxLength(4);
        ovLayerBox.setFilter(s -> s.matches("-?\\d*"));
        ovLayerBox.setHint(Component.translatable("ui.phantasia.raw_value", 0));
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
        ovHidePosBox.setHint(Component.translatable("screen.phantasia.scene_editor.hint_hidepos"));
        ovHidePosBox.setResponder(v -> {
            PhantasiaSceneData.MachineOverride ov = ensureOverride();
            if (ov == null) return;
            ov.hidePositions = parsePosList(v);
            dirty = true;
        });

        ovFakeRecipeBox = addW(new EditBox(font, 0, 0, 140, 12, Component.empty()));
        ovFakeRecipeBox.setMaxLength(128);
        ovFakeRecipeBox.setHint(Component.translatable("screen.phantasia.scene_editor.hint_recipe"));
        ovFakeRecipeBox.setResponder(v -> {
            PhantasiaSceneData.MachineOverride ov = ensureOverride();
            if (ov == null) return;
            ov.fakeRecipeId = v.isBlank() ? null : v.trim();
            dirty = true;
        });

        ovParticleBox = addW(new EditBox(font, 0, 0, 140, 12, Component.empty()));
        ovParticleBox.setMaxLength(512);
        ovParticleBox.setHint(Component.translatable("screen.phantasia.scene_editor.hint_particles"));
        ovParticleBox.setResponder(v -> {
            PhantasiaSceneData.MachineOverride ov = ensureOverride();
            if (ov == null) return;
            ov.particleEffects = new java.util.ArrayList<>();
            for (String part : v.split(";")) {
                String trim = part.trim();
                if (!trim.isEmpty()) ov.particleEffects.add(trim);
            }
            dirty = true;
        });

        // Add-placement inputs
        newMachineIdBox = addW(new EditBox(font, 0, 0, 160, 12, Component.empty()));
        newMachineIdBox.setMaxLength(128);
        newMachineIdBox.setHint(Component.translatable("screen.phantasia.scene_editor.hint_machine_id"));

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

        sceneIconBox = addW(new EditBox(font, 0, 0, 160, 12, Component.empty()));
        sceneIconBox.setMaxLength(128);
        sceneIconBox.setValue(data.iconItem != null ? data.iconItem : "minecraft:chest");
        sceneIconBox.setResponder(v -> {
            data.iconItem = v.isBlank() ? "minecraft:chest" : v;
            dirty = true;
        });

        tooltipItemBox = addW(new EditBox(font, 0, 0, 120, 12, Component.empty()));
        tooltipItemBox.setMaxLength(128);
        tooltipItemBox.setHint(Component.literal("e.g. minecraft:iron_ore"));

        // Start-cam panel boxes
        scYawBox = addW(new EditBox(font, 0, 0, 44, 12, Component.empty()));
        scYawBox.setMaxLength(10);
        scYawBox.setResponder(v -> {
            if (data.startCamera == null) return;
            try { data.startCamera.yaw = Float.parseFloat(v.trim()); dirty = true; } catch (NumberFormatException ignored) {}
        });
        scPitchBox = addW(new EditBox(font, 0, 0, 44, 12, Component.empty()));
        scPitchBox.setMaxLength(10);
        scPitchBox.setResponder(v -> {
            if (data.startCamera == null) return;
            try { data.startCamera.pitch = Float.parseFloat(v.trim()); dirty = true; } catch (NumberFormatException ignored) {}
        });
        scZoomBox = addW(new EditBox(font, 0, 0, 44, 12, Component.empty()));
        scZoomBox.setMaxLength(10);
        scZoomBox.setResponder(v -> {
            if (data.startCamera == null) return;
            try { data.startCamera.zoom = Float.parseFloat(v.trim()); dirty = true; } catch (NumberFormatException ignored) {}
        });

        populateStartCamBoxes();
        hideAllInputs();
    }

    private EditBox makeSmallIntBox(String hint) {
        EditBox b = addW(new EditBox(font, 0, 0, 34, 12, Component.empty()));
        b.setMaxLength(5);
        b.setFilter(s -> s.matches("-?\\d*"));
        b.setHint(Component.literal(hint));
        return b;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tick
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (camera != null) camera.tick();
        sceneSelectPulse += sceneSelectPulseUp ? 0.07f : -0.07f;
        if (sceneSelectPulse >= 1f) {
            sceneSelectPulse = 1f;
            sceneSelectPulseUp = false;
        }
        if (sceneSelectPulse <= 0f) {
            sceneSelectPulse = 0f;
            sceneSelectPulseUp = true;
        }

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
                        applyActiveStateToScene(data.steps.get(i).working);
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

    @Override
    public void renderAsBackground(net.minecraft.client.gui.GuiGraphics g, float partial) {
        g.fill(0, 0, this.width, this.height, net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.C_BG());
        if (renderer != null && camera != null) {
            int sceneX = showPlacementsPanel ? PLACEMENTS_PANEL_W : 0;
            int sceneW = this.width - sceneX;
            int sceneH = this.height - TOP_BAR_H - BOTTOM_H;
            net.phoenixvine.phantasia.client.camera.CameraView view = camera.getView(partial);
            renderer.render(view, sceneX, TOP_BAR_H, sceneW, sceneH);
        }
    }

    void rebuildVisibility() {
        if (renderer == null || scenePattern == null) return;
        Set<BlockPos> visible = scenePattern.computeVisible(step(), data);
        renderer.setVisible(visible);
        renderer.requestBake();
    }

    void applyActiveStateToScene(boolean globalWorking) {
        if (editorLevel == null || scenePattern == null) return;
        net.phoenixvine.phantasia.client.screens.PhantasiaSceneActiveState.apply(
                editorLevel, scenePattern, step(), globalWorking, renderer);
    }

    private void applyWorldItemsToEditorLevel() {
        if (editorLevel == null || scenePattern == null) return;
        PhantasiaSceneData.StepData s = step();
        java.util.Set<net.minecraft.core.BlockPos> rebakePos = new java.util.HashSet<>();
        for (PhantasiaScenePattern.PlacementEntry pe : scenePattern.placements) {
            PhantasiaSceneData.MachineOverride ov = s.getOverride(pe.index);
            if (ov == null || ov.worldItems.isEmpty()) continue;
            for (net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData.WorldItemEntry wi : ov.worldItems) {
                net.minecraft.core.BlockPos worldPos = new net.minecraft.core.BlockPos(wi.x, wi.y, wi.z).offset(pe.offset);
                net.minecraft.world.level.block.entity.BlockEntity be = editorLevel.getBlockEntity(worldPos);
                if (be == null) continue;
                if (be.getLevel() == null) be.setLevel(editorLevel);
                if (wi.sourceAmount >= 0) {
                    try {
                        if (be instanceof com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile jar) {
                            jar.setSource(wi.sourceAmount);
                            net.minecraft.nbt.CompoundTag nbt = be.saveWithoutMetadata();
                            net.minecraft.world.level.block.state.BlockState currentState = editorLevel.getBlockState(worldPos);
                            editorLevel.renderedBlocks.put(worldPos, net.phoenixvine.phantasia.utils.PhantasiaBlockInfo.fromBlockStateAndNbt(currentState, nbt));
                            editorLevel.blockEntities.put(worldPos, be);
                            rebakePos.add(worldPos);
                        }
                    } catch (Exception ignored) {}
                }
                if (wi.item != null && !wi.item.isBlank()) {
                    try {
                        net.minecraft.resources.ResourceLocation rl = new net.minecraft.resources.ResourceLocation(wi.item);
                        net.minecraft.world.item.Item itm = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
                        net.minecraft.world.item.ItemStack stack = (itm == null || itm == net.minecraft.world.item.Items.AIR)
                                ? net.minecraft.world.item.ItemStack.EMPTY : new net.minecraft.world.item.ItemStack(itm);
                        if (be instanceof net.minecraft.world.Container container) {
                            container.setItem(0, stack);
                            be.setChanged();
                            rebakePos.add(worldPos);
                        } else {
                            net.minecraft.world.item.ItemStack finalStack = stack;
                            be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER)
                                    .ifPresent(handler -> {
                                        if (handler.getSlots() > 0 && handler.isItemValid(0, finalStack)) {
                                            handler.insertItem(0, finalStack, false);
                                            be.setChanged();
                                            rebakePos.add(worldPos);
                                        }
                                    });
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        if (!rebakePos.isEmpty() && renderer != null) renderer.requestPartialBake(rebakePos);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Render
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        btns.clear();
        hideAllInputs();
        pendingTooltip = null;
        lastMX = mx;
        lastMY = my;

        g.fill(0, 0, this.width, this.height, C_BG());

        // 3D scene
        if (renderer != null && camera != null) {
            int sceneX = showPlacementsPanel ? PLACEMENTS_PANEL_W : 0;
            int sceneW = this.width - sceneX;
            int sceneH = this.height - TOP_BAR_H - BOTTOM_H;
            boolean overCamPanel = showCameraPanel && isCamPanelHit(mx, my);
            boolean overWiPanel = showWorldItemPanel && isWiPanelHit(mx, my);
            boolean overScPanel = showStartCamPanel && isStartCamPanelHit(mx, my);
            renderer.setMousePos((overCamPanel || overWiPanel || overScPanel) ? -1 : mx, (overCamPanel || overWiPanel || overScPanel) ? -1 : my);
            CameraView view = camera.getView(partial);
            renderer.render(view, sceneX, TOP_BAR_H, sceneW, sceneH);
            // Track hovered block for WORLD mode
            net.minecraft.world.phys.BlockHitResult hit = renderer.getLastHitResult();
            if (hit != null && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                BlockPos hp = hit.getBlockPos();
                hoveredWorldPos = renderer.isVisible(hp) ? hp : null;
            } else {
                hoveredWorldPos = null;
            }
        }

        renderTopBar(g, mx, my);
        if (showPlacementsPanel) renderPlacementsPanel(g, mx, my);
        renderStepRow(g, mx, my);
        renderTimeline(g, mx, my);
        renderItemStrip(g);
        if (showCameraPanel) renderCameraPanel(g, mx, my);
        if (showStartCamPanel) renderStartCamPanel(g, mx, my);
        if (showWorldItemPanel) renderWorldItemOverlay(g, mx, my);

        super.render(g, mx, my, partial);

        if (showingCloseConfirm)
            renderCloseConfirmDialog(g, mx, my, this::forceClose, () -> showingCloseConfirm = false);

        // Floating tooltip — drawn absolutely last so it is always on top
        renderPendingTooltip(g, mx, my);
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private void renderTopBar(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, this.width, TOP_BAR_H, C_BAR());
        g.fill(0, TOP_BAR_H - 1, this.width, TOP_BAR_H, C_ACCENT());

        int x = 6;

        // Placements panel toggle
        boolean ppHov = isOver(mx, my, x, 3, 86, TOP_BAR_H - 6);
        g.fill(x, 3, x + 86, TOP_BAR_H - 3, showPlacementsPanel ? C_BTN_ACT() : (ppHov ? C_BTN_HOV() : C_BTN()));
        if (showPlacementsPanel) g.fill(x, TOP_BAR_H - 3, x + 86, TOP_BAR_H - 2, C_ACCENT());
        g.drawString(font, "\u229E Placements", x + 5, (TOP_BAR_H - 8) / 2, showPlacementsPanel ? C_ACCENT() : C_DIM(),
                false);
        if (ppHov) pendingTooltip = "Toggle placements side panel";
        btns.add(new Btn(x, 3, 86, TOP_BAR_H - 6, () -> showPlacementsPanel = !showPlacementsPanel));
        x += 92;

        // Preview
        boolean ph = isOver(mx, my, x, 3, 70, TOP_BAR_H - 6);
        g.fill(x, 3, x + 70, TOP_BAR_H - 3, previewing ? C_BTN_ACT() : (ph ? C_BTN_HOV() : C_BTN()));
        if (previewing) g.fill(x, TOP_BAR_H - 3, x + 70, TOP_BAR_H - 2, C_GREEN());
        g.drawString(font, previewing ? "\u23F9 Stop" : "\u25BA Preview",
                x + 5, (TOP_BAR_H - 8) / 2, previewing ? C_GREEN() : C_DIM(), false);
        if (ph)
            pendingTooltip = previewing ? "Stop preview playback" : "Play a live preview stepping through all steps";
        btns.add(new Btn(x, 3, 70, TOP_BAR_H - 6, this::togglePreview));
        x += 76;

        // Camera panel tab
        int camTabW = 76;
        boolean camHov = isOver(mx, my, x, 3, camTabW, TOP_BAR_H - 6);
        g.fill(x, 3, x + camTabW, TOP_BAR_H - 3, showCameraPanel ? C_BTN_ACT() : (camHov ? C_BTN_HOV() : C_BTN()));
        if (showCameraPanel) g.fill(x, TOP_BAR_H - 3, x + camTabW, TOP_BAR_H - 2, C_ACCENT());
        g.drawString(font, "\uD83C\uDFA5 Camera", x + 5, (TOP_BAR_H - 8) / 2, showCameraPanel ? C_ACCENT() : C_DIM(),
                false);
        if (camHov) pendingTooltip = "Set per-step camera overrides (yaw, pitch, zoom, easing)";
        btns.add(new Btn(x, 3, camTabW, TOP_BAR_H - 6, () -> showCameraPanel = !showCameraPanel));
        x += camTabW + 6;

        // World items panel tab
        int wiTabW = 70;
        boolean wiHov = isOver(mx, my, x, 3, wiTabW, TOP_BAR_H - 6);
        g.fill(x, 3, x + wiTabW, TOP_BAR_H - 3, showWorldItemPanel ? C_BTN_ACT() : (wiHov ? C_BTN_HOV() : C_BTN()));
        if (showWorldItemPanel) g.fill(x, TOP_BAR_H - 3, x + wiTabW, TOP_BAR_H - 2, C_ACCENT());
        g.drawString(font, "▦ World", x + 5, (TOP_BAR_H - 8) / 2, showWorldItemPanel ? C_ACCENT() : C_DIM(), false);
        if (wiHov) pendingTooltip = "Place items in block entities per placement per step";
        btns.add(new Btn(x, 3, wiTabW, TOP_BAR_H - 6, () -> {
            showWorldItemPanel = !showWorldItemPanel;
            wiBlock = null;
        }));
        x += wiTabW + 6;
        // Start camera tab (left-side, after World Items)
        String startCamLabel = data.startCamera != null ? "\uD83C\uDFA5 StartCam \u2713" : "\uD83C\uDFA5 StartCam";
        int scTabW = font.width(startCamLabel) + 10;
        boolean scHov = isOver(mx, my, x, 3, scTabW, TOP_BAR_H - 6);
        g.fill(x, 3, x + scTabW, TOP_BAR_H - 3, showStartCamPanel ? C_BTN_ACT() : (scHov ? C_BTN_HOV() : C_BTN()));
        if (showStartCamPanel || data.startCamera != null)
            g.fill(x, TOP_BAR_H - 3, x + scTabW, TOP_BAR_H - 2, C_ACCENT());
        g.drawString(font, startCamLabel, x + 5, (TOP_BAR_H - 8) / 2,
                (showStartCamPanel || data.startCamera != null) ? C_ACCENT() : C_DIM(), false);
        if (scHov) pendingTooltip = "Toggle start camera panel \u2014 capture or clear the opening view";
        btns.add(new Btn(x, 3, scTabW, TOP_BAR_H - 6, () -> showStartCamPanel = !showStartCamPanel));
        int leftEdge = x + scTabW + 6;

        // Right buttons
        int rx = this.width - 4;
        rx = topBtn(g, mx, my, rx, "\u2715 Back", C_BTN(), "Close the editor (warns if unsaved)", this::onClose);
        rx = topBtn(g, mx, my, rx, "\uD83D\uDCBE Save", C_GREEN(), "Save scene to disk", this::save);
        if (dirty) {
            String dot = "\u25CF unsaved";
            rx -= font.width(dot) + 10;
            g.drawString(font, dot, rx, (TOP_BAR_H - 8) / 2, C_WARN(), false);
        }
        int rightEdge = rx - 4;

    }

    // ── Camera panel (floating overlay above step row) ────────────────────────

    private boolean isCamPanelHit(int mx, int my) {
        int panelW = Math.min(500, this.width - 16);
        int panelH = CAM_PANEL_H;
        int panelX = showPlacementsPanel ? PLACEMENTS_PANEL_W + 6 : 6;
        int panelY = this.height - BOTTOM_H - panelH - 4;
        return isOver(mx, my, panelX - 2, panelY - 2, panelW + 4, panelH + 4);
    }

    private void renderCameraPanel(GuiGraphics g, int mx, int my) {
        int panelW = Math.min(500, this.width - 16);
        int panelH = CAM_PANEL_H;
        int panelX = showPlacementsPanel ? PLACEMENTS_PANEL_W + 6 : 6;
        int panelY = this.height - BOTTOM_H - panelH - 4;

        g.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xDD070712);
        g.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY - 1, C_ACCENT());
        g.drawString(font, "\uD83C\uDFA5  Camera \u2014 step " + (selectedStep + 1),
                panelX + 4, panelY + 3, C_ACCENT(), false);

        PhantasiaSceneData.StepData s = step();
        boolean hasCam = s.camera != null;

        // Row 1: Capture / Clear / live info
        int r1Y = panelY + 14;
        int x = panelX + 4;

        String capLabel = hasCam ? "\uD83D\uDCF7 Update Cam" : "\uD83D\uDCF7 Capture Cam";
        int capW = font.width(capLabel) + 12;
        boolean capHov = isOver(mx, my, x, r1Y, capW, 14);
        g.fill(x, r1Y, x + capW, r1Y + 14, hasCam ? C_BTN_ACT() : (capHov ? C_BTN_HOV() : C_BTN()));
        if (hasCam) g.fill(x, r1Y, x + capW, r1Y + 1, C_ACCENT());
        g.drawString(font, capLabel, x + 6, r1Y + 3, hasCam ? C_ACCENT() : C_DIM(), false);
        if (capHov) pendingTooltip = hasCam ? "Overwrite this step's camera with the current viewport position" :
                "Snapshot the current viewport as this step's camera target";
        btns.add(new Btn(x, r1Y, capW, 14, this::captureCamera));
        x += capW + 6;

        if (hasCam) {
            boolean clrHov = isOver(mx, my, x, r1Y, 52, 14);
            g.fill(x, r1Y, x + 52, r1Y + 14, clrHov ? C_BTN_HOV() : C_BTN());
            g.drawString(font, "\u2715 Clear", x + 5, r1Y + 3, clrHov ? C_RED() : C_DIM(), false);
            if (clrHov) pendingTooltip = "Remove camera override from this step";
            btns.add(new Btn(x, r1Y, 52, 14, () -> {
                checkpoint();
                s.camera = null;
                dirty = true;
            }));
            x += 58;

            if (camera != null) {
                String info = String.format("Yaw %.1f\u00B0  Pitch %.1f\u00B0  Zoom %.1f",
                        s.camera.yaw, s.camera.pitch,
                        s.camera.zoom > 0 ? s.camera.zoom : camera.getZoom());
                g.drawString(font, info, x, r1Y + 3, C_DIM(), false);
            }
        } else {
            g.drawString(font, Component.translatable("screen.phantasia.scene_editor.no_cam_override").getString(),
                    x, r1Y + 3, C_DIM(), false);
        }

        // Row 2: Zoom + lerp type + lerp ticks
        int r2Y = panelY + 32;
        x = panelX + 4;

        if (hasCam) {
            g.drawString(font, Component.translatable("screen.phantasia.scene_editor.label_zoom").getString(), x,
                    r2Y + 2, C_DIM(), false);
            x += font.width(Component.translatable("screen.phantasia.scene_editor.label_zoom").getString()) + 3;
            placeBox(camZoomBox, x, r2Y, 40, 12);
            if (isOver(mx, my, x, r2Y, 40, 12))
                pendingTooltip = "Camera distance from the look-at target in world units (\u22121 = auto)";
            x += 46;

            LerpType lt = LerpType.fromString(s.camera.lerpType);
            String ltLabel = lt.name().replace("_", " ");
            int ltW = font.width(ltLabel) + 16;
            boolean lth = isOver(mx, my, x, r2Y, ltW, 13);
            g.fill(x, r2Y, x + ltW, r2Y + 13, lth ? C_BTN_HOV() : C_BTN());
            g.fill(x, r2Y, x + ltW, r2Y + 1, C_ACCENT());
            g.drawString(font, ltLabel, x + 8, r2Y + 2, C_ACCENT(), false);
            if (lth) pendingTooltip = "Easing type \u2014 click to cycle: " + lerpTypeList();
            btns.add(new Btn(x, r2Y, ltW, 13, () -> {
                checkpoint();
                LerpType[] vals = LerpType.values();
                s.camera.lerpType = vals[(lt.ordinal() + 1) % vals.length].name();
                dirty = true;
            }));
            x += ltW + 6;

            if (lt != LerpType.SNAP) {
                g.drawString(font, Component.translatable("screen.phantasia.scene_editor.val_over").getString(), x,
                        r2Y + 2, C_DIM(), false);
                x += font.width(Component.translatable("screen.phantasia.scene_editor.val_over").getString()) + 3;
                placeBox(lerpTicksBox, x, r2Y, 34, 12);
                if (isOver(mx, my, x, r2Y, 34, 12))
                    pendingTooltip = "Camera transition duration in ticks (20 ticks = 1 second)";
                x += 38;
                g.drawString(font, Component.translatable("screen.phantasia.scene_editor.val_ticks").getString(), x,
                        r2Y + 2, C_DIM(), false);
            }
        }
    }

    // ── Start-cam panel ───────────────────────────────────────────────────────

    private boolean isStartCamPanelHit(int mx, int my) {
        int panelW = 320, panelH = 42;
        int panelX = showPlacementsPanel ? PLACEMENTS_PANEL_W + 6 : 6;
        int panelY = this.height - BOTTOM_H - panelH - 4;
        return isOver(mx, my, panelX - 2, panelY - 2, panelW + 4, panelH + 4);
    }

    private void renderStartCamPanel(GuiGraphics g, int mx, int my) {
        int pw = 320, ph = 42;
        int px = showPlacementsPanel ? PLACEMENTS_PANEL_W + 6 : 6;
        int py = this.height - BOTTOM_H - ph - 4;
        g.fill(px, py, px + pw, py + ph, C_PANEL());
        g.fill(px, py, px + pw, py + 1, C_ACCENT());
        g.fill(px, py, px + 1, py + ph, 0x33FFFFFF);
        g.fill(px + pw - 1, py, px + pw, py + ph, 0x33FFFFFF);

        boolean hasSC = data.startCamera != null;
        int row1 = py + 6;
        int x = px + 6;
        g.drawString(font, "Start Camera", x, row1 + 1, C_ACCENT(), false);
        x += font.width("Start Camera") + 10;

        // Yaw / Pitch / Zoom fields
        x = scField(g, mx, my, x, row1, "Yaw", scYawBox, "Yaw angle (degrees)", hasSC);
        x = scField(g, mx, my, x, row1, "Pitch", scPitchBox, "Pitch angle (degrees)", hasSC);
        x = scField(g, mx, my, x, row1, "Zoom", scZoomBox, "Camera distance from target", hasSC);

        int row2 = py + 22;
        x = px + 6;
        // Capture button
        int capW = font.width("⊙ Capture View") + 12;
        boolean capHov = isOver(mx, my, x, row2, capW, 14);
        g.fill(x, row2, x + capW, row2 + 14, capHov ? C_BTN_HOV() : C_BTN());
        g.drawString(font, "⊙ Capture View", x + 6, row2 + 3, capHov ? C_ACCENT() : C_DIM(), false);
        if (capHov) pendingTooltip = "Snapshot the current viewport as the scene opening camera";
        btns.add(new Btn(x, row2, capW, 14, this::captureStartCam));
        x += capW + 6;

        if (hasSC) {
            // Clear button
            int clrW = font.width("✕ Clear") + 10;
            boolean clrHov = isOver(mx, my, x, row2, clrW, 14);
            g.fill(x, row2, x + clrW, row2 + 14, clrHov ? C_BTN_HOV() : C_BTN());
            g.drawString(font, "✕ Clear", x + 5, row2 + 3, clrHov ? C_RED() : C_DIM(), false);
            if (clrHov) pendingTooltip = "Remove start camera override";
            btns.add(new Btn(x, row2, clrW, 14, this::clearStartCam));
            x += clrW + 8;
            // Live info
            String info = String.format("Yaw %.1f°  Pitch %.1f°  Zoom %.1f",
                    data.startCamera.yaw, data.startCamera.pitch,
                    data.startCamera.zoom > 0 ? data.startCamera.zoom : 0f);
            g.drawString(font, info, x, row2 + 3, C_DIM(), false);
        }

        for (var box : java.util.List.of(scYawBox, scPitchBox, scZoomBox)) {
            box.visible = hasSC;
            box.active = hasSC;
        }
    }

    private int scField(GuiGraphics g, int mx, int my, int x, int y,
                        String label, EditBox box, String tooltip, boolean hasSC) {
        int labelW = font.width(label) + 3;
        g.drawString(font, label, x, y + 2, C_DIM(), false);
        placeBox(box, x + labelW, y, 44, 12);
        if (!hasSC) g.fill(x + labelW, y, x + labelW + 44, y + 12, 0x44000000);
        if (isOver(mx, my, x, y, labelW + 44, 12)) pendingTooltip = tooltip;
        return x + labelW + 48;
    }

    private void captureStartCam() {
        checkpoint();
        if (data.startCamera == null) {
            data.startCamera = new PhantasiaScriptData.CameraData();
        }
        if (camera != null) {
            data.startCamera.yaw = camera.getYaw();
            data.startCamera.pitch = camera.getPitch();
            data.startCamera.zoom = camera.getZoom();
        }
        populateStartCamBoxes();
        dirty = true;
    }

    private void clearStartCam() {
        checkpoint();
        data.startCamera = null;
        populateStartCamBoxes();
        dirty = true;
    }

    private void populateStartCamBoxes() {
        if (scYawBox == null || scPitchBox == null || scZoomBox == null) return;
        if (data.startCamera != null) {
            scYawBox.setValue(String.format("%.1f", data.startCamera.yaw));
            scPitchBox.setValue(String.format("%.1f", data.startCamera.pitch));
            scZoomBox.setValue(data.startCamera.zoom > 0 ? String.format("%.1f", data.startCamera.zoom) : "");
        } else {
            scYawBox.setValue("");
            scPitchBox.setValue("");
            scZoomBox.setValue("");
        }
    }

    // ── World items overlay ───────────────────────────────────────────────────

    private boolean isWiPanelHit(int mx, int my) {
        int panelX = showPlacementsPanel ? PLACEMENTS_PANEL_W + 6 : 6;
        int panelY = Math.max(TOP_BAR_H + 4, this.height - BOTTOM_H - WI_PANEL_H - 4);
        int panelW = this.width - panelX - 10;
        return isOver(mx, my, panelX - 2, panelY - 2, panelW + 4, WI_PANEL_H + 4);
    }

    private void renderWorldItemOverlay(GuiGraphics g, int mx, int my) {
        if (scenePattern == null || camera == null || editorLevel == null) return;
        CameraView view = camera.getView(0f);
        Vector3f eye = view.eyePos(), lookat = view.lookAt();
        Vector3f fwd = new Vector3f(lookat).sub(eye).normalize();
        Vector3f rgt = new Vector3f(fwd).cross(new Vector3f(0, 1, 0)).normalize();
        Vector3f upv = new Vector3f(rgt).cross(fwd).normalize();
        float fov = this.height /
                (2f * (float) Math.tan(Math.toRadians(net.phoenixvine.phantasia.client.camera.PhantasiaCamera.FOV)));
        int sceneBottom = this.height - BOTTOM_H;

        if (selectedPlacement < 0 || selectedPlacement >= scenePattern.placements.size()) {
            drawBannerWrapped(g, "Select a placement in the panel, then click a block entity to edit its item", TOP_BAR_H + 4,
                    C_DIM());
            return;
        }

        PhantasiaScenePattern.PlacementEntry pe = scenePattern.placements.get(selectedPlacement);
        PhantasiaSceneData.MachineOverride ov = step().getOverride(selectedPlacement);
        java.util.List<PhantasiaScriptData.WorldItemEntry> entries = ov != null ? ov.worldItems :
                java.util.Collections.emptyList();

        for (BlockPos worldPos : pe.worldPositions) {
            if (editorLevel.getBlockEntity(worldPos) == null) continue;
            if (editorLevel.getBlockState(worldPos)
                    .getBlock() instanceof com.gregtechceu.gtceu.api.block.MetaMachineBlock)
                continue;

            BlockPos local = worldPos.subtract(pe.offset);
            boolean hasEntry = entries.stream()
                    .anyMatch(wi -> wi.x == local.getX() && wi.y == local.getY() && wi.z == local.getZ());
            boolean isSelected = local.equals(wiBlock) && wiPlacementIdx == selectedPlacement;

            float[] sc = projectToScreen(worldPos.getX() + 0.5f, worldPos.getY() + 0.5f, worldPos.getZ() + 0.5f,
                    eye, fwd, rgt, upv, fov);
            if (sc == null || sc[2] < 0.3f) continue;
            int isx = (int) sc[0], isy = (int) sc[1];
            if (isy < TOP_BAR_H || isy > sceneBottom) continue;

            if (!isSelected) {
                g.fill(isx - 3, isy - 3, isx + 3, isy + 3, 0x44AAAAAA);
            }
        }

        if (wiBlock == null || wiPlacementIdx != selectedPlacement) {
            String beCount = entries.isEmpty() ? "" : " (" + entries.size() + " set)";
            drawBannerWrapped(g,
                    "Click a block entity to edit its item for this placement/step — right-click to remove" + beCount,
                    TOP_BAR_H + 4, C_DIM());
        }

        if (wiBlock != null && wiPlacementIdx == selectedPlacement) renderWorldItemPanel(g, mx, my);
    }

    private void renderWorldItemPanel(GuiGraphics g, int mx, int my) {
        int panelX = showPlacementsPanel ? PLACEMENTS_PANEL_W + 6 : 6;
        int panelY = Math.max(TOP_BAR_H + 4, this.height - BOTTOM_H - WI_PANEL_H - 4);

        // Compute the actual content width first (labels + boxes + buttons), then
        // clamp the whole row to fit on-screen by shrinking the item box rather than
        // letting later widgets (source box / confirm / remove) run off the edge.
        String itemLabel = Component.translatable("screen.phantasia.scene_editor.label_item").getString();
        String sourceLabel = Component.translatable("screen.phantasia.scene_editor.label_source").getString();
        String confirmLabel = Component.translatable("ui.phantasia.btn_confirm").getString();
        String removeLabel = Component.translatable("ui.phantasia.btn_remove").getString();

        PhantasiaSceneData.MachineOverride ovForSize = step().getOverride(wiPlacementIdx);
        boolean hasEntry = ovForSize != null && wiBlock != null && ovForSize.worldItems.stream().anyMatch(
                wi -> wi.x == wiBlock.getX() && wi.y == wiBlock.getY() && wi.z == wiBlock.getZ());

        int itemBoxW = 200;
        int sourceBoxW = 54;
        int confirmW = font.width(confirmLabel) + 12;
        int removeW = hasEntry ? font.width(removeLabel) + 12 : 0;

        int contentW = 4 + font.width(itemLabel) + 4 + itemBoxW + 8
                + font.width(sourceLabel) + 4 + sourceBoxW + 8
                + confirmW + (hasEntry ? 4 + removeW : 0) + 4;

        int maxPanelW = this.width - panelX - 10;
        int panelW = Math.min(contentW, maxPanelW);

        // If everything doesn't fit, shrink the item box first (it has the most slack)
        // before anything else gets clipped or pushed off-screen.
        if (contentW > maxPanelW) {
            int overflow = contentW - maxPanelW;
            itemBoxW = Math.max(60, itemBoxW - overflow);
            panelW = maxPanelW;
        }

        g.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + WI_PANEL_H + 2, 0xDD070712);
        g.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY - 1, C_ACCENT());

        String placement = wiPlacementIdx >= 0 && wiPlacementIdx < data.placements.size() ?
                data.placements.get(wiPlacementIdx).machine : "?";
        String label = "World Items — " + placement + " @ " + wiBlock.toShortString();
        if (editorLevel != null && scenePattern != null && wiPlacementIdx < scenePattern.placements.size()) {
            BlockPos worldPos = wiBlock.offset(scenePattern.placements.get(wiPlacementIdx).offset);
            net.minecraft.world.level.block.state.BlockState bs = editorLevel.getBlockState(worldPos);
            if (!bs.isAir()) label += "  ·  " + bs.getBlock().getName().getString();
        }
        g.drawString(font, trunc(label, panelW - 8), panelX + 4, panelY + 3, C_ACCENT(), false);

        int row1Y = panelY + 14;
        int x = panelX + 4;

        g.drawString(font, itemLabel, x, row1Y + 2, C_DIM(), false);
        x += font.width(itemLabel) + 4;
        placeBox(wiItemBox, x, row1Y, itemBoxW, 12);
        x += itemBoxW + 8;

        g.drawString(font, sourceLabel, x, row1Y + 2, C_DIM(), false);
        x += font.width(sourceLabel) + 4;
        placeBox(wiSourceBox, x, row1Y, sourceBoxW, 12);
        x += sourceBoxW + 8;

        boolean confHov = isOver(mx, my, x, row1Y, confirmW, 13);
        g.fill(x, row1Y, x + confirmW, row1Y + 13, confHov ? C_BTN_HOV() : C_GREEN());
        if (confHov) g.fill(x, row1Y, x + confirmW, row1Y + 1, C_ACCENT());
        g.drawString(font, confirmLabel, x + 6, row1Y + 2, confHov ? C_ACCENT() : C_TEXT(), false);
        btns.add(new Btn(x, row1Y, confirmW, 13, this::confirmWorldItemEntry));
        x += confirmW + 4;

        if (hasEntry) {
            boolean remHov = isOver(mx, my, x, row1Y, removeW, 13);
            g.fill(x, row1Y, x + removeW, row1Y + 13, remHov ? C_BTN_HOV() : C_BTN());
            g.drawString(font, removeLabel, x + 6, row1Y + 2, remHov ? C_RED() : C_DIM(), false);
            btns.add(new Btn(x, row1Y, removeW, 13, this::removeWorldItemEntry));
        }

        int row2Y = panelY + 30;
        String hint = Component.translatable("screen.phantasia.scene_editor.hint_item_blank").getString();
        var hintLines = font.split(net.minecraft.network.chat.Component.literal(hint), panelW - 8);
        for (int li = 0; li < hintLines.size(); li++) {
            g.drawString(font, hintLines.get(li), panelX + 4, row2Y + 2 + li * 10, C_DIM(), false);
        }
    }

    private void confirmWorldItemEntry() {
        if (wiBlock == null) return;
        String item = wiItemBox.getValue().trim();
        int sourceAmt = wiSourceBox.getValue().isBlank() ? -1 : parseSceneIntSafe(wiSourceBox.getValue(), -1);
        if (item.isBlank() && sourceAmt < 0) {
            wiBlock = null;
            return;
        }

        checkpoint();
        PhantasiaSceneData.MachineOverride ov = ensureOverrideForPlacement(wiPlacementIdx);
        if (ov == null) return;
        ov.worldItems.removeIf(wi -> wi.x == wiBlock.getX() && wi.y == wiBlock.getY() && wi.z == wiBlock.getZ());
        PhantasiaScriptData.WorldItemEntry entry = new PhantasiaScriptData.WorldItemEntry(wiBlock.getX(),
                wiBlock.getY(), wiBlock.getZ(), item);
        entry.sourceAmount = sourceAmt;
        ov.worldItems.add(entry);
        dirty = true;

        // Apply change immediately to the editor level for visual feedback
        if (editorLevel != null && scenePattern != null && wiPlacementIdx < scenePattern.placements.size()) {
            BlockPos worldPos = wiBlock.offset(scenePattern.placements.get(wiPlacementIdx).offset);
            net.minecraft.world.level.block.entity.BlockEntity be = editorLevel.getBlockEntity(worldPos);
            if (be != null) {
                if (be.getLevel() == null) be.setLevel(editorLevel);
                if (sourceAmt >= 0) {
                    try {
                        if (be instanceof com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile jar) {
                            jar.setSource(sourceAmt);
                            jar.setChanged();
                        }
                    } catch (Exception ignored) {}
                }
                if (!item.isBlank()) {
                    try {
                        net.minecraft.resources.ResourceLocation rl = new net.minecraft.resources.ResourceLocation(item);
                        net.minecraft.world.item.Item itm = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
                        net.minecraft.world.item.ItemStack stack = (itm == null || itm == net.minecraft.world.item.Items.AIR)
                                ? net.minecraft.world.item.ItemStack.EMPTY : new net.minecraft.world.item.ItemStack(itm);
                        if (be instanceof net.minecraft.world.Container container) {
                            container.setItem(0, stack);
                            be.setChanged();
                        } else {
                            net.minecraft.world.item.ItemStack finalStack = stack;
                            be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER)
                                    .ifPresent(handler -> {
                                        if (handler.getSlots() > 0 && handler.isItemValid(0, finalStack)) {
                                            handler.insertItem(0, finalStack, false);
                                            be.setChanged();
                                        }
                                    });
                        }
                    } catch (Exception ignored) {}
                }
                if (renderer != null) renderer.requestPartialBake(java.util.Set.of(worldPos));
            }
        }

        wiBlock = null;
        wiItemBox.setValue("");
        wiSourceBox.setValue("");
    }

    private void removeWorldItemEntry() {
        if (wiBlock == null) return;
        checkpoint();
        PhantasiaSceneData.MachineOverride ov = step().getOverride(wiPlacementIdx);
        if (ov != null) {
            BlockPos b = wiBlock;
            ov.worldItems.removeIf(wi -> wi.x == b.getX() && wi.y == b.getY() && wi.z == b.getZ());
        }
        dirty = true;
        wiBlock = null;
        wiItemBox.setValue("");
        wiSourceBox.setValue("");
    }

    private boolean handleWorldItemClick(double mx, double my, int btn) {
        if (hoveredWorldPos == null || scenePattern == null || editorLevel == null) return false;
        if (editorLevel.getBlockEntity(hoveredWorldPos) == null) return false;
        if (scenePattern.allBaseplatePositions.contains(hoveredWorldPos)) return false;
        if (editorLevel.getBlockState(hoveredWorldPos)
                .getBlock() instanceof com.gregtechceu.gtceu.api.block.MetaMachineBlock)
            return false;

        // Find which placement this world position belongs to
        int foundPlacement = -1;
        for (PhantasiaScenePattern.PlacementEntry pe : scenePattern.placements) {
            if (pe.worldPositions.contains(hoveredWorldPos)) {
                foundPlacement = pe.index;
                break;
            }
        }
        if (foundPlacement < 0) return false;

        PhantasiaScenePattern.PlacementEntry pe = scenePattern.placements.get(foundPlacement);
        BlockPos local = hoveredWorldPos.subtract(pe.offset);

        if (btn == 1) {
            PhantasiaSceneData.MachineOverride ov = step().getOverride(foundPlacement);
            if (ov != null && ov.worldItems
                    .removeIf(wi -> wi.x == local.getX() && wi.y == local.getY() && wi.z == local.getZ())) {
                checkpoint();
                dirty = true;
                if (local.equals(wiBlock) && wiPlacementIdx == foundPlacement) wiBlock = null;
            }
            return true;
        }

        // Left-click: select
        wiPlacementIdx = foundPlacement;
        wiBlock = local;
        // Select this placement in the panel too
        if (selectedPlacement != foundPlacement) {
            selectedPlacement = foundPlacement;
            populateOverrideBoxes();
        }
        PhantasiaSceneData.MachineOverride ov = step().getOverride(foundPlacement);
        PhantasiaScriptData.WorldItemEntry existing = ov == null ? null : ov.worldItems.stream()
                .filter(wi -> wi.x == local.getX() && wi.y == local.getY() && wi.z == local.getZ())
                .findFirst().orElse(null);
        wiItemBox.setValue(existing != null && existing.item != null ? existing.item : "");
        wiSourceBox
                .setValue(existing != null && existing.sourceAmount >= 0 ? String.valueOf(existing.sourceAmount) : "");
        setFocused(wiItemBox);
        return true;
    }

    private PhantasiaSceneData.MachineOverride ensureOverrideForPlacement(int placementIdx) {
        if (placementIdx < 0 || placementIdx >= data.placements.size()) return null;
        PhantasiaSceneData.MachineOverride ov = step().getOverride(placementIdx);
        if (ov == null) {
            ov = new PhantasiaSceneData.MachineOverride();
            step().setOverride(placementIdx, ov);
        }
        return ov;
    }

    private static int parseSceneIntSafe(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private float[] projectToScreen(float wx, float wy, float wz,
                                    Vector3f eye, Vector3f fwd, Vector3f rgt, Vector3f upv, float fov) {
        Vector3f toP = new Vector3f(wx - eye.x(), wy - eye.y(), wz - eye.z());
        float depth = toP.dot(fwd);
        if (depth <= 0.1f) return null;
        float rightProj = toP.dot(rgt);
        float upProj = toP.dot(upv);
        float screenX = this.width * 0.5f + (rightProj / depth) * fov;
        float screenY = this.height * 0.5f - (upProj / depth) * fov;
        return new float[] { screenX, screenY, depth };
    }

    // ── Placements panel ──────────────────────────────────────────────────────

    private void renderPlacementsPanel(GuiGraphics g, int mx, int my) {
        int pw = PLACEMENTS_PANEL_W;
        int ph = this.height - TOP_BAR_H - BOTTOM_H;
        int px = 0, py = TOP_BAR_H;

        g.fill(px, py, px + pw, py + ph, C_PANEL());
        g.fill(px + pw - 1, py, px + pw, py + ph, 0x44FFFFFF);

        // Header
        g.fill(px, py, px + pw, py + 14, C_BAR());
        g.drawString(font, Component.translatable("screen.phantasia.scene_editor.section_placements").getString(),
                px + 6, py + 3, C_ACCENT(), false);

        // Scene name field
        g.drawString(font, Component.translatable("screen.phantasia.scene_editor.label_name").getString(), px + 6,
                py + 18, C_DIM(), false);
        placeBox(sceneNameBox,
                px + 6 + font.width(Component.translatable("screen.phantasia.scene_editor.label_name").getString()) + 3,
                py + 16,
                pw - 20 - font.width(Component.translatable("screen.phantasia.scene_editor.label_name").getString()),
                12);
        sceneNameBox.visible = true;
        sceneNameBox.active = true;

        // Icon field
        g.drawString(font, Component.translatable("screen.phantasia.scene_editor.label_icon").getString(), px + 6,
                py + 34, C_DIM(), false);
        placeBox(sceneIconBox,
                px + 6 + font.width(Component.translatable("screen.phantasia.scene_editor.label_icon").getString()) + 3,
                py + 32,
                pw - 20 - font.width(Component.translatable("screen.phantasia.scene_editor.label_icon").getString()),
                12);
        sceneIconBox.visible = true;
        sceneIconBox.active = true;

        // ── Tooltip Items ─────────────────────────────────────────────────────
        int tiY = py + 48;
        g.fill(px + 4, tiY, px + pw - 4, tiY + 1, 0x22FFFFFF);
        tiY += 4;
        g.drawString(font,
                String.format(Component.translatable("screen.phantasia.scene_editor.section_tooltip_items").getString(),
                        data.tooltipItems != null ? data.tooltipItems.size() : 0),
                px + 6, tiY, C_DIM(), false);
        tiY += font.lineHeight + 2;

        if (data.tooltipItems != null) {
            for (int i = 0; i < data.tooltipItems.size(); i++) {
                String tid = data.tooltipItems.get(i);
                boolean tiHov = isOver(mx, my, px + 4, tiY, pw - 26, 12);
                g.fill(px + 4, tiY, px + pw - 22, tiY + 12, tiHov ? C_BTN_HOV() : C_BTN());
                String tidTrunc = font.width(tid) > pw - 34 ? font.plainSubstrByWidth(tid, pw - 34) + "…" : tid;
                g.drawString(font, tidTrunc, px + 7, tiY + 2, C_TEXT(), false);
                boolean tiDelHov = isOver(mx, my, px + pw - 20, tiY, 16, 12);
                g.fill(px + pw - 20, tiY, px + pw - 4, tiY + 12, tiDelHov ? 0xBB3A0A0A : C_BTN());
                g.drawCenteredString(font, "✕", px + pw - 12, tiY + 2, tiDelHov ? C_RED() : C_DIM());
                final int fi = i;
                btns.add(new Btn(px + pw - 20, tiY, 16, 12, () -> {
                    checkpoint();
                    data.tooltipItems.remove(fi);
                    dirty = true;
                }));
                tiY += 14;
            }
        }

        // Add tooltip item input row
        int addTiW = font.width(Component.translatable("ui.phantasia.btn_add").getString()) + 8;
        placeBox(tooltipItemBox, px + 4, tiY - 1, pw - addTiW - 14, 12);
        tooltipItemBox.visible = true;
        tooltipItemBox.active = true;
        boolean addTiHov = isOver(mx, my, px + pw - addTiW - 6, tiY - 1, addTiW, 12);
        g.fill(px + pw - addTiW - 6, tiY - 1, px + pw - 6, tiY + 11, addTiHov ? C_BTN_HOV() : C_BTN());
        g.drawString(font, Component.translatable("ui.phantasia.btn_add").getString(), px + pw - addTiW - 3, tiY + 1,
                addTiHov ? C_ACCENT() : C_DIM(), false);
        btns.add(new Btn(px + pw - addTiW - 6, tiY - 1, addTiW, 12, () -> {
            String v = tooltipItemBox.getValue().trim();
            if (!v.isBlank()) {
                if (data.tooltipItems == null) data.tooltipItems = new java.util.ArrayList<>();
                if (!data.tooltipItems.contains(v)) {
                    checkpoint();
                    data.tooltipItems.add(v);
                    dirty = true;
                    tooltipItemBox.setValue("");
                }
            }
        }));
        tiY += 16;

        // ── Placement list ────────────────────────────────────────────────────
        // Clip the list+form to the panel height so they can't overflow the screen.
        int listY = tiY;
        int rowH = 38;
        int clipMaxY = py + ph; // nothing should render below this line

        for (int i = 0; i < data.placements.size(); i++) {
            if (listY + rowH > clipMaxY) break; // stop drawing if we'd overflow

            PhantasiaSceneData.PlacementData pd = data.placements.get(i);
            boolean sel = (i == selectedPlacement);
            boolean hov = isOver(mx, my, px + 2, listY, pw - 4, rowH - 2);

            g.fill(px + 2, listY, px + pw - 2, listY + rowH - 2,
                    sel ? C_BTN_ACT() : (hov ? C_BTN_HOV() : C_BTN()));
            if (sel) g.fill(px + 2, listY, px + 3, listY + rowH - 2, C_ACCENT());

            String mid = pd.machine.contains(":") ? pd.machine.split(":")[1].replace('_', ' ') : pd.machine;
            String midTrunc = trunc(mid, pw - 30);
            g.drawString(font, "#" + i + " " + midTrunc, px + 6, listY + 3, sel ? C_ACCENT() : C_TEXT(), false);
            g.drawString(font, "x" + pd.x + " y" + pd.y + " z" + pd.z, px + 6, listY + 13, C_DIM(), false);

            if (!pd.items.isEmpty()) {
                String badge = pd.items.size() + " item" + (pd.items.size() == 1 ? "" : "s");
                int badgeW = font.width(badge) + 6;
                int badgeX = px + pw - 24 - badgeW - 2;
                g.fill(badgeX, listY + 3, badgeX + badgeW, listY + 13, 0x55000000);
                g.fill(badgeX, listY + 3, badgeX + 1, listY + 13, 0xFF4FC3F7);
                g.drawString(font, badge, badgeX + 3, listY + 4, 0xFF4FC3F7, false);
            }

            if (step().getOverride(i) != null)
                g.drawString(font, "\u25C6 override", px + 6, listY + 23, C_ACCENT(), false);

            int rbx = px + pw - 22, rby = listY + 2;
            boolean rbHov = isOver(mx, my, rbx, rby, 18, 14);
            g.fill(rbx, rby, rbx + 18, rby + 14, rbHov ? C_BTN_HOV() : C_BTN());
            g.drawString(font, "\u2715", rbx + 5, rby + 3, rbHov ? C_RED() : C_DIM(), false);
            if (rbHov) pendingTooltip = "Remove placement #" + i;
            final int fi = i;
            btns.add(new Btn(rbx, rby, 18, 14, () -> removePlacement(fi)));
            btns.add(new Btn(px + 2, listY, pw - 24, rowH - 2, () -> Minecraft.getInstance().setScreen(
                    new PhantasiaScenePlacementStepEditorScreen(this, data, fi))));

            listY += rowH;
        }

        // ── Add-placement form ────────────────────────────────────────────────
        int formY = listY + 4;
        if (formY + 54 <= clipMaxY) {
            g.fill(px + 2, formY - 2, px + pw - 2, Math.min(formY + 54, clipMaxY), 0x220A0A20);
            g.drawString(font, Component.translatable("screen.phantasia.scene_editor.label_add_machine").getString(),
                    px + 6, formY, C_DIM(), false);
            formY += 10;

            if (formY + 12 <= clipMaxY) {
                placeBox(newMachineIdBox, px + 6, formY, pw - 12, 12);
                newMachineIdBox.visible = true;
                newMachineIdBox.active = true;
                formY += 16;
            }

            if (formY + 12 <= clipMaxY) {
                g.drawString(font, "X:", px + 6, formY + 2, C_DIM(), false);
                placeBox(newOffsetXBox, px + 18, formY, 34, 12);
                newOffsetXBox.visible = true;
                newOffsetXBox.active = true;
                g.drawString(font, "Y:", px + 58, formY + 2, C_DIM(), false);
                placeBox(newOffsetYBox, px + 68, formY, 34, 12);
                newOffsetYBox.visible = true;
                newOffsetYBox.active = true;
                g.drawString(font, "Z:", px + 108, formY + 2, C_DIM(), false);
                placeBox(newOffsetZBox, px + 118, formY, 34, 12);
                newOffsetZBox.visible = true;
                newOffsetZBox.active = true;
                formY += 16;
            }

            if (formY + 14 <= clipMaxY)
                tipBtn(g, mx, my, px + 6, formY, pw - 12, 14, "\u2713 Add Placement", C_BTN(),
                        "Add a new machine placement to this scene", this::tryAddPlacement);
        }

        // ── Per-placement override section (when a placement is selected) ─────
        if (selectedPlacement >= 0 && selectedPlacement < data.placements.size()) {
            int ovY = formY + 22;
            PhantasiaSceneData.MachineOverride ov = step().getOverride(selectedPlacement);
            PhantasiaSceneData.PlacementData pd = data.placements.get(selectedPlacement);

            if (ovY + 10 > clipMaxY) return; // no room left

            g.fill(px + 2, ovY - 2, px + pw - 2, Math.min(ovY + 160, clipMaxY), 0x220A0A20);
            g.drawString(font, "#" + selectedPlacement + " override", px + 6, ovY, C_ACCENT(), false);
            ovY += 10;

            // Show mode buttons
            String curShow = ov != null && ov.show != null ? ov.show : "(global)";
            if (ovY + 12 <= clipMaxY) {
                g.drawString(font, Component.translatable("screen.phantasia.scene_editor.label_show").getString(),
                        px + 6, ovY + 2, C_DIM(), false);
                int bx = px + 6 +
                        font.width(Component.translatable("screen.phantasia.scene_editor.label_show").getString()) + 3;

                boolean gHov = isOver(mx, my, bx, ovY, 40, 12);
                boolean gSel = (ov == null || ov.show == null);
                g.fill(bx, ovY, bx + 40, ovY + 12, gSel ? C_BTN_ACT() : (gHov ? C_BTN_HOV() : C_BTN()));
                if (gSel) g.fill(bx, ovY, bx + 40, ovY + 1, C_DIM());
                g.drawString(font, Component.translatable("screen.phantasia.scene_editor.val_global").getString(),
                        bx + 4, ovY + 2, gSel ? C_DIM() : C_TEXT(), false);
                if (gHov) pendingTooltip = "Use the global step show mode for this placement";
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
                    g.fill(bx, ovY, bx + smW, ovY + 12, smSel ? C_BTN_ACT() : (smHov ? C_BTN_HOV() : C_BTN()));
                    if (smSel) g.fill(bx, ovY, bx + smW, ovY + 1, C_ACCENT());
                    g.drawString(font, sml, bx + 4, ovY + 2, smSel ? C_ACCENT() : C_TEXT(), false);
                    if (smHov) pendingTooltip = showModeTooltip(sm);
                    final String fsm = sm;
                    btns.add(new Btn(bx, ovY, smW, 12, () -> {
                        checkpoint();
                        PhantasiaSceneData.MachineOverride nov = ensureOverride();
                        nov.show = fsm;
                        if ("layer".equals(fsm) || "layers".equals(fsm)) {
                            int[] bounds = selectedPlacementYBounds();
                            if (bounds != null) {
                                int minY = bounds[0], maxY = bounds[1];
                                if ("layer".equals(fsm)) {
                                    if (nov.layer < minY || nov.layer > maxY)
                                        nov.layer = (minY + maxY) / 2;
                                } else {
                                    boolean uninit = nov.layerMin == 0 && nov.layerMax == 0 && maxY > 0;
                                    if (uninit || nov.layerMin < minY || nov.layerMin > maxY) nov.layerMin = minY;
                                    if (uninit || nov.layerMax < minY || nov.layerMax > maxY) nov.layerMax = maxY;
                                    if (nov.layerMin >= nov.layerMax) {
                                        nov.layerMin = minY;
                                        nov.layerMax = maxY;
                                    }
                                }
                            }
                        }
                        rebuildVisibility();
                        dirty = true;
                    }));
                    bx += smW + 2;
                }
                ovY += 16;
            }

            // Layer field
            if (ov != null && ("layer".equals(ov.show) || "layers".equals(ov.show)) && ovY + 12 <= clipMaxY) {
                g.drawString(font, Component.translatable("screen.phantasia.scene_editor.label_layer").getString(),
                        px + 6, ovY + 2, C_DIM(), false);
                placeBox(ovLayerBox,
                        px + 6 + font.width(
                                Component.translatable("screen.phantasia.scene_editor.label_layer").getString()) + 3,
                        ovY, 28, 12);
                ovLayerBox.visible = true;
                ovLayerBox.active = true;
                ovY += 16;
            }

            // HidePos
            if (ovY + 12 <= clipMaxY) {
                g.drawString(font, Component.translatable("screen.phantasia.scene_editor.label_hidepos").getString(),
                        px + 6, ovY + 2, C_DIM(), false);
                placeBox(ovHidePosBox,
                        px + 6 + font.width(
                                Component.translatable("screen.phantasia.scene_editor.label_hidepos").getString()) + 3,
                        ovY, pw - 14 - font.width(
                                Component.translatable("screen.phantasia.scene_editor.label_hidepos").getString()),
                        12);
                ovHidePosBox.visible = true;
                ovHidePosBox.active = true;
                if (isOver(mx, my, px + 6, ovY, pw - 12, 12))
                    pendingTooltip = "Local positions to hide for this placement, semicolon-separated (x,y,z; ...)";
                ovY += 16;
            }

            // Working state
            if (ovY + 12 <= clipMaxY) {
                g.drawString(font, Component.translatable("screen.phantasia.scene_editor.label_working").getString(),
                        px + 6, ovY + 2, C_DIM(), false);
                int wbx = px + 6 +
                        font.width(Component.translatable("screen.phantasia.scene_editor.label_working").getString()) +
                        4;
                Boolean curWorking = ov != null ? ov.machineWorking : null;
                String[] wLabels = { Component.translatable("screen.phantasia.scene_editor.val_global").getString(),
                        "Idle", "Working" };
                Boolean[] wVals = { null, false, true };
                int[] wCols = { 0xFF334455, 0xFF445566, 0xFF1A7040 };
                for (int wi = 0; wi < wLabels.length; wi++) {
                    String wlbl = wLabels[wi];
                    Boolean wval = wVals[wi];
                    int wW = font.width(wlbl) + 10;
                    boolean wSel = (curWorking == null && wval == null) ||
                            (curWorking != null && curWorking.equals(wval));
                    boolean wHov = isOver(mx, my, wbx, ovY, wW, 12);
                    int wBg = wSel ? (wi == 2 ? 0xFF0D3A20 : C_BTN_ACT()) : (wHov ? C_BTN_HOV() : C_BTN());
                    g.fill(wbx, ovY, wbx + wW, ovY + 12, wBg);
                    if (wSel) g.fill(wbx, ovY, wbx + wW, ovY + 1, wCols[wi]);
                    g.drawString(font, wlbl, wbx + 5, ovY + 2, wSel ? wCols[wi] : C_TEXT(), false);
                    if (wHov) pendingTooltip = wi == 0 ? "Use global working state" :
                            wi == 1 ? "Force machine idle" : "Force machine working";
                    final Boolean fwval = wval;
                    btns.add(new Btn(wbx, ovY, wW, 12, () -> {
                        checkpoint();
                        PhantasiaSceneData.MachineOverride nov = ensureOverride();
                        if (nov != null) {
                            nov.machineWorking = fwval;
                            dirty = true;
                            applyActiveStateToScene(step().working);
                        }
                    }));
                    wbx += wW + 3;
                }
                ovY += 16;
            }

            // Fake recipe ID
            if (ovY + 12 <= clipMaxY) {
                g.drawString(font, Component.translatable("screen.phantasia.scene_editor.label_recipe").getString(),
                        px + 6, ovY + 2, C_DIM(), false);
                placeBox(ovFakeRecipeBox,
                        px + 6 + font.width(
                                Component.translatable("screen.phantasia.scene_editor.label_recipe").getString()) + 3,
                        ovY, pw - 14 - font.width(
                                Component.translatable("screen.phantasia.scene_editor.label_recipe").getString()),
                        12);
                ovFakeRecipeBox.visible = true;
                ovFakeRecipeBox.active = true;
                if (isOver(mx, my, px + 6, ovY, pw - 12, 12))
                    pendingTooltip = "Optional fake recipe ID for this machine in this step";
                ovY += 16;
            }

            // Particle effects
            if (ovY + 12 <= clipMaxY) {
                g.drawString(font, Component.translatable("screen.phantasia.scene_editor.label_particles").getString(),
                        px + 6, ovY + 2, C_DIM(), false);
                placeBox(
                        ovParticleBox, px + 6 +
                                font.width(Component
                                        .translatable("screen.phantasia.scene_editor.label_particles").getString()) +
                                3,
                        ovY,
                        pw - 14 -
                                font.width(Component.translatable("screen.phantasia.scene_editor.label_particles")
                                        .getString()),
                        12);
                ovParticleBox.visible = true;
                ovParticleBox.active = true;
                if (isOver(mx, my, px + 6, ovY, pw - 12, 12))
                    pendingTooltip = "Semicolon-separated particle effect IDs to show on this machine";
                ovY += 16;
            }

            // Clear override button
            if (ov != null && ovY + 12 <= clipMaxY) {
                tipBtn(g, mx, my, px + 6, ovY, pw - 12, 12, "\u2715 Clear override", C_BTN(),
                        "Remove all overrides for this placement on this step", () -> {
                            checkpoint();
                            step().removeOverride(selectedPlacement);
                            populateOverrideBoxes();
                            rebuildVisibility();
                            dirty = true;
                        });
                ovY += 16;
            }

            // Item summary + edit button
            ovY += 2;
            if (!pd.items.isEmpty() && ovY + 11 <= clipMaxY) {
                StringBuilder sb = new StringBuilder();
                for (PhantasiaSceneData.ItemConditionData it : pd.items) {
                    if (sb.length() > 0) sb.append("  ");
                    String pill = it.type == null ? "in" : switch (it.type.toLowerCase(java.util.Locale.ROOT)) {
                        case "output" -> "out";
                        case "catalyst" -> "cat";
                        default -> "in";
                    };
                    sb.append("[").append(pill).append("] ");
                    String nm = it.item.contains(":") ? it.item.split(":")[1] : it.item;
                    sb.append(nm.replace('_', ' '));
                }
                g.drawString(font, trunc(sb.toString(), pw - 12), px + 6, ovY, 0xFF9ECDEE, false);
                ovY += 11;
            }

            if (ovY + 14 <= clipMaxY) {
                int editBtnW = pw - 12;
                tipBtn(g, mx, my, px + 6, ovY, editBtnW, 14, "\u270E Edit placement / items \u2192", C_BTN(),
                        "Open the placement editor to change machine ID, offset, and item conditions",
                        () -> Minecraft.getInstance().setScreen(
                                new PhantasiaPlacementEditorScreen(this, data, selectedPlacement)));
                ovY += 18;
            }

            if (ovY + 14 <= clipMaxY) {
                int editBtnW = pw - 12;
                tipBtn(g, mx, my, px + 6, ovY, editBtnW, 14, "\u26A0 Edit Layout Mistakes \u2192", C_BTN(),
                        "Open the mistakes editor for this scene",
                        () -> Minecraft.getInstance().setScreen(
                                new PhantasiaSceneMistakesEditorScreen(this, data)));
            }
        }
    }

    private static String showModeTooltip(String mode) {
        return switch (mode) {
            case "all" -> "Show every block in the structure";
            case "layer" -> "Show only blocks on a single Y layer";
            case "layers" -> "Show only blocks within a Y range";
            case "pos" -> "Show only specific block positions";
            default -> mode;
        };
    }

    /** Returns [minY, maxY] for the currently selected placement, or null. */
    private int[] selectedPlacementYBounds() {
        if (scenePattern == null || selectedPlacement < 0) return null;
        for (PhantasiaScenePattern.PlacementEntry pe : scenePattern.placements) {
            if (pe.index == selectedPlacement) return new int[] { pe.minY, pe.maxY };
        }
        return null;
    }

    // ── Step row ──────────────────────────────────────────────────────────────

    private void renderStepRow(GuiGraphics g, int mx, int my) {
        int rowY = this.height - BOTTOM_H;
        g.fill(0, rowY, this.width, rowY + STEP_ROW_H, C_BAR());
        g.fill(0, rowY, this.width, rowY + 1, C_ACCENT());

        PhantasiaSceneData.StepData s = step();
        int y1 = rowY + 5;
        int x = 8;

        // Step nav
        g.drawString(font, Component.translatable("screen.phantasia.scene_editor.label_step").getString(), x, y1 - 2,
                0xFF334455, false);
        String stepLbl = (selectedStep + 1) + "/" + data.steps.size();
        g.drawString(font, stepLbl, x, y1 + 6, C_ACCENT(), false);
        x += font.width(stepLbl) + 8;

        tipBtn(g, mx, my, x, y1, 14, 14, "+", C_BTN(), "Add a new step at the end of the timeline", this::addStep);
        x += 18;
        tipBtn(g, mx, my, x, y1, 14, 14, "\u2212", C_BTN(), "Delete this step (must have more than one step)",
                this::deleteStep);
        x += 18;
        tipBtn(g, mx, my, x, y1, 24, 14, "Dup", C_BTN(), "Duplicate this step and insert it after the current one",
                this::duplicateStep);
        x += 28;
        tipBtn(g, mx, my, x, y1, 14, 14, "\u25C4", C_BTN(), "Move this step one position earlier (Ctrl+\u2190)",
                () -> moveStep(selectedStep, -1));
        x += 18;
        tipBtn(g, mx, my, x, y1, 14, 14, "\u25BA", C_BTN(), "Move this step one position later (Ctrl+\u2192)",
                () -> moveStep(selectedStep, +1));
        x += 18;

        g.fill(x, y1, x + 1, y1 + 14, 0x33FFFFFF);
        x += 8;

        // Tick
        g.drawString(font, Component.translatable("screen.phantasia.scene_editor.label_tick").getString(), x, y1 + 3,
                C_DIM(), false);
        x += font.width(Component.translatable("screen.phantasia.scene_editor.label_tick").getString()) + 3;
        placeBox(tickBox, x, y1, 38, 13);
        if (isOver(mx, my, x, y1, 38, 13)) pendingTooltip = "Tick this step activates at (20 ticks = 1 second)";
        x += 44;

        // Caption — click to open sub-screen, matching the script editor pattern
        String capVal = s.caption != null ? s.caption : "";
        int capW = Math.min(200, this.width / 2 - x - 10);
        boolean capHov = isOver(mx, my, x, y1, capW, 13);
        g.fill(x, y1, x + capW, y1 + 13, capHov ? C_BTN_HOV() : C_BTN());
        g.fill(x, y1, x + capW, y1 + 1, 0x33FFFFFF);
        String capDisplay = capVal.isEmpty() ? "\u270E  Caption\u2026" : trunc(capVal, capW - 16);
        g.drawString(font, capDisplay, x + 4, y1 + 3, capVal.isEmpty() ? C_DIM() : C_TEXT(), false);
        if (!capVal.isEmpty() && font.width(capVal) > capW - 16)
            g.drawString(font, "\u2026", x + capW - 8, y1 + 3, C_DIM(), false);
        if (capHov) pendingTooltip = "Click to edit the caption shown to viewers during this step";
        btns.add(new Btn(x, y1, capW, 13, () -> Minecraft.getInstance().setScreen(
                new net.phoenixvine.phantasia.client.screens.subscreen.PhantasiaTextInputScreen(
                        this, "Step Caption", "What the viewer sees\u2026",
                        s.caption != null ? s.caption : "", 256, v -> {
                    checkpoint();
                    s.caption = v.isBlank() ? null : v;
                    dirty = true;
                }))));
        x += capW + 8;

        g.fill(x, y1, x + 1, y1 + 14, 0x33FFFFFF);
        x += 8;

        // Camera capture button (now just the capture shortcut — full controls live in the camera panel)
        boolean hasCam = s.camera != null;
        boolean cch = isOver(mx, my, x, y1, 110, 14);
        g.fill(x, y1, x + 110, y1 + 14, hasCam ? C_BTN_ACT() : (cch ? C_BTN_HOV() : C_BTN()));
        if (hasCam) g.fill(x, y1, x + 110, y1 + 1, C_ACCENT());
        g.drawString(font, hasCam ? "\uD83D\uDCF7 Update Cam" : "\uD83D\uDCF7 Capture Cam",
                x + 5, y1 + 3, hasCam ? C_ACCENT() : C_DIM(), false);
        if (cch) pendingTooltip = hasCam ?
                "Overwrite this step\u2019s camera with the current viewport (open Camera panel for full controls)" :
                "Snapshot the current viewport as this step\u2019s camera (open Camera panel for full controls)";
        btns.add(new Btn(x, y1, 110, 14, this::captureCamera));
        x += 114;

        // Working state toggle
        boolean isWorking = s.working;
        int wW = font.width(isWorking ? "Working" : "Idle") + 12;
        boolean wHov = isOver(mx, my, x, y1, wW, 14);
        g.fill(x, y1, x + wW, y1 + 14, isWorking ? 0xFF0D3A20 : (wHov ? C_BTN_HOV() : C_BTN()));
        if (isWorking) g.fill(x, y1, x + wW, y1 + 1, C_GREEN());
        g.drawString(font, isWorking ? "● Working" : "○ Idle",
                x + 5, y1 + 3, isWorking ? C_GREEN() : C_DIM(), false);
        if (wHov)
            pendingTooltip = "Toggle global working/idle state for all machines in this step (per-placement overrides in the placements panel)";
        btns.add(new Btn(x, y1, wW, 14, () -> {
            checkpoint();
            s.working = !s.working;
            dirty = true;
            applyActiveStateToScene(s.working);
        }));
        x += wW + 6;

        // Show items toggle
        boolean showIt = s.showItems;
        int siW = font.width("Items") + 12;
        boolean siHov = isOver(mx, my, x, y1, siW, 14);
        g.fill(x, y1, x + siW, y1 + 14, showIt ? C_BTN_ACT() : (siHov ? C_BTN_HOV() : C_BTN()));
        if (showIt) g.fill(x, y1, x + siW, y1 + 1, C_ACCENT());
        g.drawString(font, "Items", x + 5, y1 + 3, showIt ? C_ACCENT() : C_DIM(), false);
        if (siHov) pendingTooltip = showIt ? "Items panel is visible — click to hide" :
                "Items panel is hidden — click to show";
        btns.add(new Btn(x, y1, siW, 14, () -> {
            checkpoint();
            s.showItems = !s.showItems;
            dirty = true;
        }));

        // ── Row 2: global show-mode buttons + description box ─────────────────
        int y2 = rowY + 27;
        x = 8;

        g.drawString(font, Component.translatable("screen.phantasia.scene_editor.label_global").getString(), x, y2 + 2,
                C_DIM(), false);
        x += font.width(Component.translatable("screen.phantasia.scene_editor.label_global").getString()) + 4;

        for (int i = 0; i < SHOW_MODES.length; i++) {
            String sm = SHOW_MODES[i];
            String sml = SHOW_LABELS[i];
            int mw = font.width(sml) + 10;
            boolean act = sm.equals(s.show);
            boolean hov = isOver(mx, my, x, y2, mw, 14);
            g.fill(x, y2, x + mw, y2 + 14, act ? C_BTN_ACT() : (hov ? C_BTN_HOV() : C_BTN()));
            if (act) g.fill(x, y2, x + mw, y2 + 1, C_ACCENT());
            g.drawString(font, sml, x + 5, y2 + 3, act ? C_ACCENT() : C_TEXT(), false);
            if (hov) pendingTooltip = showModeTooltip(sm);
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
            g.drawString(font, " Y=" + s.layer, x, y2 + 2, C_ACCENT(), false);
        else if ("layers".equals(s.show))
            g.drawString(font, " " + s.layerMin + "\u2192" + s.layerMax, x, y2 + 2, C_ACCENT(), false);

        // Description button — right portion of row 2, opens terminal editor like caption
        int descX = this.width / 2;
        int descW = this.width - descX - 12;
        String descLabel = Component.translatable("screen.phantasia.scene_editor.label_desc").getString();
        g.drawString(font, descLabel, descX, y2 + 2, C_DIM(), false);
        int dboxX = descX + font.width(descLabel) + 4;
        int dboxW = descW - font.width(descLabel) - 4;
        String descVal = s.description != null ? s.description : "";
        boolean descHov = isOver(mx, my, dboxX, y2, dboxW, 13);
        g.fill(dboxX, y2, dboxX + dboxW, y2 + 13, descHov ? C_BTN_HOV() : C_BTN());
        g.fill(dboxX, y2, dboxX + dboxW, y2 + 1, 0x33FFFFFF);
        String descDisplay = descVal.isEmpty() ? "✎  Description…" : trunc(descVal, dboxW - 16);
        g.drawString(font, descDisplay, dboxX + 4, y2 + 3, descVal.isEmpty() ? C_DIM() : C_TEXT(), false);
        if (!descVal.isEmpty() && font.width(descVal) > dboxW - 16)
            g.drawString(font, "…", dboxX + dboxW - 8, y2 + 3, C_DIM(), false);
        if (descHov) pendingTooltip = "Click to edit the description shown to viewers during this step";
        btns.add(new Btn(dboxX, y2, dboxW, 13, () -> Minecraft.getInstance().setScreen(
                new net.phoenixvine.phantasia.client.screens.subscreen.PhantasiaTextInputScreen(
                        this, "Step Description", "Detailed guide text…",
                        s.description != null ? s.description : "", 2048, v -> {
                    checkpoint();
                    s.description = v.isBlank() ? null : v;
                    dirty = true;
                }))));
    }

    // ── Timeline ─────────────────────────────────────────────────────────────

    private static final int TL_MARGIN = 30;

    private int tlY() {
        return this.height - TIMELINE_H;
    }

    private int tlTrackW() {
        return this.width - TL_MARGIN * 2;
    }

    private void renderTimeline(GuiGraphics g, int mx, int my) {
        int tlY = tlY();
        int margin = TL_MARGIN;
        int trackW = tlTrackW();
        int total = computeTotalTicks();
        int midY = tlY + TIMELINE_H / 2;

        g.fill(0, tlY, this.width, this.height, C_PANEL());
        g.fill(0, tlY, this.width, tlY + 1, 0x33FFFFFF);
        g.fill(margin, midY - 1, margin + trackW, midY + 1, 0xFF1A2C3C);
        g.fill(margin - 1, midY - 3, margin, midY + 3, 0xFF3A506A);
        g.fill(margin + trackW, midY - 3, margin + trackW + 1, midY + 3, 0xFF3A506A);

        // Preview playhead
        if (previewing && total > 0) {
            int px = margin + (int) ((float) previewTick / total * trackW);
            g.fill(px - 1, tlY + 2, px + 1, tlY + TIMELINE_H - 2, 0xAAFFFFFF);
        }

        // Ghost + click-to-add
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
                int ghW = font.width("+") + 6;
                g.fill(mx - ghW / 2, midY - 7, mx + ghW / 2, midY + 7, 0x884FC3F7);
                g.drawCenteredString(font, "+", mx, midY - 3, 0xFFFFFFFF);
                g.drawCenteredString(font, "t=" + timelineGhostTick, mx, midY + 8, 0x664FC3F7);
                pendingTooltip = "Click to add a step at tick " + timelineGhostTick;
            }
        }

        // Step dots
        for (int i = 0; i < data.steps.size(); i++) {
            PhantasiaSceneData.StepData s = data.steps.get(i);
            float t = total > 0 ? (float) s.tick / total : 0f;
            int dotX = margin + (int) (t * trackW);
            boolean sel = (i == selectedStep);
            boolean hov = isOver(mx, my, dotX - 9, midY - 9, 18, 18);
            boolean drag = (draggingTimelineDot == i);

            int ringCol = sel || drag ? C_ACCENT() : (hov ? 0xFFAADDFF : 0xFF3A506A);
            g.fill(dotX - 7, midY - 7, dotX + 7, midY + 7, ringCol);
            g.fill(dotX - 5, midY - 5, dotX + 5, midY + 5, drag ? 0xFFFFFFFF : (sel ? 0xFF1A3C5C : 0xFF0A1520));
            // Camera pip
            if (s.camera != null) g.fill(dotX - 3, midY - 7, dotX + 3, midY - 5, C_ACCENT());

            g.drawCenteredString(font, String.valueOf(i + 1), dotX, midY - 3,
                    sel || drag ? C_ACCENT() : (hov ? 0xFFCCEEFF : C_DIM()));

            // Line to next dot
            if (i + 1 < data.steps.size()) {
                PhantasiaSceneData.StepData next = data.steps.get(i + 1);
                float nt = total > 0 ? (float) next.tick / total : 0f;
                int nextX = margin + (int) (nt * trackW);
                g.fill(dotX + 7, midY, nextX - 7, midY + 1, 0xFF1E3A52);
            }

            // Step label below dot
            String lbl = (sel || drag) ? "#" + (i + 1) + "  t=" + s.tick : "#" + (i + 1);
            int lx = Mth.clamp(dotX - font.width(lbl) / 2, margin, margin + trackW - font.width(lbl));
            g.drawString(font, lbl, lx, midY + 9, sel || drag ? C_ACCENT() : C_DIM(), false);

            // Hover: faint X hint (right-click to delete)
            if (hov && !sel && !drag)
                g.drawCenteredString(font, "\u2715", dotX, midY - 16, 0x88FF5252);

            if (hov && !drag)
                pendingTooltip = "Step " + (i + 1) + " \u2014 tick " + s.tick +
                        (s.camera != null ? " \uD83C\uDFA5" : "") + (s.caption != null ? ": " + s.caption : "") +
                        "  (right-click to delete)";

            final int fi = i;
            btns.add(new Btn(dotX - 9, midY - 9, 18, 18, () -> selectStep(fi)));
        }

        // Tick ruler every 60 ticks
        if (total > 0) {
            for (int tick = 0; tick <= total; tick += 60) {
                int rx = margin + (int) ((float) tick / total * trackW);
                g.fill(rx, midY + 1, rx + 1, midY + 4, 0x33FFFFFF);
            }
        }

        // Clipboard hint
        if (clipboard != null) {
            String hint = "Ctrl+V to paste step";
            g.drawString(font, hint, margin, tlY + 3, 0x554FC3F7, false);
        }

        // Duration readout — plain label, computed automatically from last step
        int total2 = computeTotalTicks();
        int durLabelW = font.width(Component.translatable("screen.phantasia.scene_editor.label_total").getString()) + 4;
        int durX = this.width - 4 - font.width(String.valueOf(total2)) - 10 - durLabelW;
        g.drawString(font, Component.translatable("screen.phantasia.scene_editor.label_total").getString(), durX,
                midY - 4, C_DIM(), false);
        g.drawString(font, String.valueOf(total2) + "t", durX + durLabelW, midY - 4, C_ACCENT(), false);
        if (isOver(mx, my, durX, tlY, durLabelW + 50, TIMELINE_H))
            pendingTooltip = "Total scene length = last step tick + 60 ticks";
    }

    // ── Item panel (GuideME-style) ────────────────────────────────────────────

    private static final int ITEM_PANEL_W = 186;
    private static final int ITEM_ROW_H = 38;
    private static final int ITEM_ICON_BASE = 28;
    private static final int ITEM_ICON_HOV = 36;

    private void renderItemStrip(GuiGraphics g) {
        itemStripBounds = null;
        if (scenePattern == null || data == null) return;
        PhantasiaSceneData.StepData step = step();
        if (!step.showItems) return;

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
        itemStripBounds = new int[] { panelX, panelY, ITEM_PANEL_W, panelH };

        g.fill(panelX, panelY, panelX + ITEM_PANEL_W, panelY + panelH, 0xDD070712);
        g.fill(panelX, panelY, panelX + ITEM_PANEL_W, panelY + 1, 0xFF4FC3F7);
        g.fill(panelX, panelY, panelX + 1, panelY + panelH, 0x554FC3F7);

        hoveredItemPlacement = -1;
        hoveredItemIndex = -1;

        int ry = panelY + 4;
        for (ItemSlot slot : slots) {
            PhantasiaSceneData.ItemConditionData it = slot.it();
            boolean hov = isOver(lastMX, lastMY, panelX + 2, ry, ITEM_PANEL_W - 4, ITEM_ROW_H - 1);
            if (hov) {
                hoveredItemPlacement = slot.placementIdx();
                hoveredItemIndex = slot.itemIdx();
            }

            int ac = it.accentColor();
            g.fill(panelX + 2, ry, panelX + ITEM_PANEL_W - 2, ry + ITEM_ROW_H - 1,
                    hov ? 0xBB0D1A2D : 0x22000000);
            g.fill(panelX + 2, ry, panelX + 3, ry + ITEM_ROW_H - 1, ac);

            float[] anim = computeTrackOffset(it, previewTick);
            float animDy = anim[1];
            int alpha = Math.max(0, Math.min(255, (int) (anim[2] * 255)));
            int iconSize = hov ? ITEM_ICON_HOV : ITEM_ICON_BASE;
            int iconX = panelX + 5;
            int iconCentY = ry + (ITEM_ROW_H - 1) / 2;
            int iconY = iconCentY - iconSize / 2 + Math.round(animDy);

            net.minecraft.world.item.Item mcItem = resolveItem(it.item);
            if (mcItem != null) {
                net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(mcItem, it.count);
                float scale = iconSize / 16f;
                if (alpha < 255) RenderSystem.setShaderColor(1f, 1f, 1f, alpha / 255f);
                g.pose().pushPose();
                g.pose().translate(iconX, iconY, 200f);
                g.pose().scale(scale, scale, 1f);
                g.renderItem(stack, 0, 0);
                g.renderItemDecorations(font, stack, 0, 0);
                g.pose().popPose();
                if (alpha < 255) RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            } else {
                g.fill(iconX + 2, iconY + 2, iconX + iconSize - 2, iconY + iconSize - 2, 0x44FF0000);
                g.drawCenteredString(font, "?", iconX + iconSize / 2, iconCentY - 4, 0xFFFF5252);
            }

            int tx = panelX + 5 + ITEM_ICON_HOV + 4;
            int tw = ITEM_PANEL_W - (tx - panelX) - 5;
            int ty = ry + 3;

            g.drawString(font, trunc(it.displayLabel(), tw), tx, ty, hov ? 0xFFFFFFFF : C_TEXT(), false);
            ty += 10;

            String pillTxt = switch (it.type == null ? "input" : it.type.toLowerCase(java.util.Locale.ROOT)) {
                case "output" -> "Out";
                case "catalyst" -> "Cat";
                default -> "In";
            };
            int pillW = font.width(pillTxt) + 5;
            g.fill(tx, ty, tx + pillW, ty + 8, ac & 0x44FFFFFF | 0x44000000);
            g.drawString(font, pillTxt, tx + 2, ty, ac, false);
            ty += 10;

            if (it.description != null && !it.description.isBlank()) {
                var lines = font.split(net.minecraft.network.chat.Component.literal(it.description), tw);
                int rem = 2;
                for (var line : lines) {
                    if (rem-- <= 0) break;
                    g.drawString(font, line, tx, ty, C_DIM(), false);
                    ty += 8;
                }
            }

            String editHint = hov ? "\u270E Edit" : "\u25B7";
            g.drawString(font, editHint, panelX + ITEM_PANEL_W - font.width(editHint) - 5,
                    ry + ITEM_ROW_H - 11, hov ? C_ACCENT() : 0x44FFFFFF, false);

            final int fPlacementIdx = slot.placementIdx();
            btns.add(new Btn(panelX + 2, ry, ITEM_PANEL_W - 4, ITEM_ROW_H - 1, () -> Minecraft.getInstance().setScreen(
                    new PhantasiaPlacementEditorScreen(PhantasiaSceneEditorScreen.this, data, fPlacementIdx))));

            ry += ITEM_ROW_H;
        }
    }

    private static float[] computeTrackOffset(PhantasiaSceneData.ItemConditionData it, int tick) {
        String track = it.track == null ? "none" : it.track.toLowerCase(java.util.Locale.ROOT);
        if ("none".equals(track)) return new float[] { 0, 0, 1f };
        int dur = Math.max(1, it.trackDurationTicks);
        float t = (tick % dur) / (float) dur;
        return switch (track) {
            case "left" -> {
                float fade = 1f - Math.abs(t - 0.5f) * 2f;
                yield new float[] { t * 40f - 20f, 0, Math.max(0f, fade) };
            }
            case "right" -> {
                float fade = 1f - Math.abs(t - 0.5f) * 2f;
                yield new float[] { 20f - t * 40f, 0, Math.max(0f, fade) };
            }
            case "up" -> new float[] { 0, -(t * 24f), Math.max(0f, 1f - t) };
            case "down" -> new float[] { 0, t * 24f, Math.max(0f, 1f - t) };
            case "pulse" -> new float[] { 0, (float) Math.sin(t * 2 * Math.PI) * 3f, 1f };
            default -> new float[] { 0, 0, 1f };
        };
    }

    private static net.minecraft.world.item.Item resolveItem(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            net.minecraft.resources.ResourceLocation rl = id.contains(":") ?
                    new net.minecraft.resources.ResourceLocation(id) :
                    new net.minecraft.resources.ResourceLocation("minecraft", id);
            net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
            return (item == null || item == net.minecraft.world.item.Items.AIR) ? null : item;
        } catch (Exception e) {
            return null;
        }
    }

    // ── Confirm dialog ────────────────────────────────────────────────────────

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

        // Click outside the Items popup closes it (the toggle button itself is a
        // Btn above, so this only fires for genuine clicks elsewhere on screen).
        if (itemStripBounds != null
                && !isOver(mx, my, itemStripBounds[0], itemStripBounds[1], itemStripBounds[2], itemStripBounds[3])) {
            step().showItems = false;
            itemStripBounds = null;
            return true;
        }

        if (super.mouseClicked(mx, my, btn)) return true;

        // Timeline
        int tlY = tlY();
        int margin = TL_MARGIN, trackW = tlTrackW();
        int total = computeTotalTicks();
        int midY = tlY + TIMELINE_H / 2;
        boolean onTimeline = isOver(mx, my, 0, tlY, this.width, TIMELINE_H);

        if (onTimeline) {
            // Right-click on a dot → delete step
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
            return true;
        }

        // Scene area – WORLD mode click
        int sceneBottom = this.height - BOTTOM_H;
        if (my >= TOP_BAR_H && my < sceneBottom && showWorldItemPanel) {
            return handleWorldItemClick(mx, my, btn);
        }

        return false;
    }

    private boolean startTimelineDotDrag(double mx, double my) {
        int tlY = tlY();
        int margin = TL_MARGIN, trackW = tlTrackW();
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
            int margin = TL_MARGIN, trackW = tlTrackW();
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

        if (camera != null) {
            // Right-click OR middle-click → pan (matches script editor)
            if (btn == 1 || btn == 2) {
                Vector3f rgt = new Vector3f(), up = new Vector3f();
                camera.getRightAndUp(rgt, up);
                float pd = CAM_PAN_SENSITIVITY;
                camera.pan(rgt.x * -(float) dx * pd + up.x * (float) dy * pd,
                        rgt.y * -(float) dx * pd + up.y * (float) dy * pd,
                        rgt.z * -(float) dx * pd + up.z * (float) dy * pd);
            } else if (btn == 0) {
                camera.orbit((float) dx * CAM_ORBIT_SENSITIVITY, (float) dy * CAM_ORBIT_SENSITIVITY);
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
    public boolean keyPressed(int kc, int sc, int mod) {
        if (getFocused() != null && getFocused().keyPressed(kc, sc, mod)) return true;

        if (kc == GLFW.GLFW_KEY_ESCAPE) {
            if (showWorldItemPanel && wiBlock != null) {
                wiBlock = null;
                return true;
            }
            if (showingCloseConfirm) {
                showingCloseConfirm = false;
                return true;
            }
            onClose();
            return true;
        }
        if (kc == GLFW.GLFW_KEY_DELETE) {
            deleteStep();
            return true;
        }
        boolean ctrl = (mod & 2) != 0;
        if (ctrl && kc == 90) {
            undo();
            return true;
        }
        if (ctrl && kc == 67) {
            clipboard = step().copy();
            return true;
        }
        if (ctrl && kc == 86) {
            if (clipboard == null) return true;
            PhantasiaSceneData.StepData pasted = clipboard.copy();
            pasted.tick = step().tick + 60;
            checkpoint();
            data.steps.add(selectedStep + 1, pasted);
            selectStep(selectedStep + 1);
            dirty = true;
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
        for (PhantasiaSceneData.StepData s : data.steps) {
            s.removeOverride(index);
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

    // toggleStartCamera is no longer used — panel toggle is inlined in renderTopBar

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
        wiBlock = null;
        populateInputsFromStep();
        populateOverrideBoxes();
        applyActiveStateToScene(step().working);
        applyWorldItemsToEditorLevel();
        rebuildVisibility();
    }

    void save() {
        PhantasiaSceneLoader.save(data);
        dirty = false;
    }

    private void togglePreview() {
        previewing = !previewing;
        previewTick = 0;
        previewAccum = 0f;
        if (!previewing) rebuildVisibility();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Override helpers
    // ─────────────────────────────────────────────────────────────────────────

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
        if (ovFakeRecipeBox != null)
            ovFakeRecipeBox.setValue(ov != null && ov.fakeRecipeId != null ? ov.fakeRecipeId : "");
        if (ovParticleBox != null)
            ovParticleBox
                    .setValue(ov != null && ov.particleEffects != null ? String.join("; ", ov.particleEffects) : "");
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
        if (descriptionBox != null)
            descriptionBox.setValue(s.description != null ? s.description : "");
        tickBox.setValue(String.valueOf(s.tick));
        if (lerpTicksBox != null && s.camera != null)
            lerpTicksBox.setValue(String.valueOf(s.camera.lerpTicks > 0 ? s.camera.lerpTicks : 20));
        if (camZoomBox != null && s.camera != null)
            camZoomBox.setValue(s.camera.zoom > 0 ? String.valueOf(s.camera.zoom) : "");
    }

    @Override
    public void hideAllInputs() {
        for (var box : new EditBox[] { captionBox, descriptionBox, tickBox, lerpTicksBox, camZoomBox,
                wiItemBox, wiSourceBox,
                ovLayerBox, ovHidePosBox, ovFakeRecipeBox, ovParticleBox, newMachineIdBox,
                newOffsetXBox, newOffsetYBox, newOffsetZBox, sceneNameBox, sceneIconBox, tooltipItemBox,
                scYawBox, scPitchBox, scZoomBox }) {
            if (box != null) {
                box.visible = false;
                box.active = false;
            }
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
        net.phoenixvine.phantasia.client.render.PhantasiaParticleEngine.destroy();
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