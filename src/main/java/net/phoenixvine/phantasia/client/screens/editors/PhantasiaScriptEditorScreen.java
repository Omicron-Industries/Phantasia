package net.phoenixvine.phantasia.client.screens.editors;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.client.camera.CameraView;
import net.phoenixvine.phantasia.client.camera.LerpType;
import net.phoenixvine.phantasia.client.camera.PhantasiaCamera;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.client.render.PhantasiaWorldRenderer;
import net.phoenixvine.phantasia.client.screens.*;
import net.phoenixvine.phantasia.client.screens.subscreen.PhantasiaTextInputScreen;
import net.phoenixvine.phantasia.common.data.pattern.PhantasiaLoadedPattern;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaScenes;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptLoader;
import net.phoenixvine.phantasia.common.data.variant.PhantasiaVariantState;

import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.*;

import static net.phoenixvine.phantasia.client.screens.PhantasiaSceneScreen.CAM_PAN_ZOOM_SCALE;
import static net.phoenixvine.phantasia.client.screens.PhantasiaSceneScreen.SHARED_LEVEL;
import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

@OnlyIn(Dist.CLIENT)
public class PhantasiaScriptEditorScreen extends PhantasiaScreen implements PhantasiaHidePosContext {

    // ── Theme ─────────────────────────────────────────────────────────────────

    // Mistake colour palette
    private static final int[] MISTAKE_COLORS = {
            0xFFFFB74D, 0xFFFF5252, 0xFF66BB6A, 0xFF4FC3F7, 0xFFCE93D8, 0xFFFFFFFF
    };
    private static final String[] MISTAKE_COLOR_NAMES = {
            "Amber", "Red", "Green", "Cyan", "Purple", "White"
    };

    /** Two sub-rows of ~19px each, 4px padding = 42 total */
    private static final int STEP_ROW_H = 42;
    private static final int TIMELINE_H = 22;
    private static final int BOTTOM_H = STEP_ROW_H + TIMELINE_H;
    /** Height of the camera floating panel that overlaps the 3-D viewport */
    private static final int CAM_PANEL_H = 52;

    // --- ROBUST FILTER STATE SEPARATION ---
    public enum FilterMode {
        ALL,
        LAYER,
        RANGE,
        PARTS
    }

    private FilterMode currentFilterMode = FilterMode.ALL;

    // Private caches so modes never stomp on or erase each other's parameters
    private String cacheRangeMin = "";
    private String cacheRangeMax = "";
    private String cachePartsExpr = ""; // Replaces the old single persistentExpr
    // ──────────────────────────────────────

    private static final float CAM_ORBIT_SENSITIVITY = 0.35f;
    private static final float CAM_PAN_SENSITIVITY = 0.02f;

    // Show-mode tabs in row 2 (excludes parts group which opens a modal)
    private static final String[] SHOW_MODES = { "all", "layer", "layers" };
    private static final String[] SHOW_LABELS = { "All",
            Component.translatable("screen.phantasia.script_editor.label_layer_filter").getString(), "Range" };

    private String persistentExpr = "";

    // Parts picker — quick-select preset buttons { show value, label, tooltip }
    private static final String[][] PARTS_PRESETS = {
            { "parts:@type(controller)", "Controller", "Multiblock controller block only" },
            { "parts:@type(functional)", "Functional", "All MetaMachine blocks + frame/gearbox blocks" },
            { "parts:@type(parts)", "All I/O Parts", "All system hatches, buses, and ports" },
            { "parts:@block(...)", "Block Name...", "Inserts a namespaced block filter: @block()" }, // NEW!
            { "parts:@ability(hatch)", "Hatches", "Filters specifically by hatch capabilities" },
            { "parts:@ability(bus)", "Buses", "Filters specifically by bus capabilities" },
            { "parts:@ability(muffler)", "Mufflers", "Filters specifically by muffler capabilities" },
            { "parts:@ability(maintenance)", "Maintenance", "Filters specifically by maintenance capabilities" },
            { "parts:@ability(input)", "Input Caps", "Filters strictly by technical input handlers" },
            { "parts:@ability(output)", "Output Caps", "Filters strictly by technical output handlers" },
            { "parts:@ability(port)", "Ports", "Filters specifically by port capabilities" },
            { "parts:@coil", "GT Coils", "Uses GregTech API to find all registered heating elements" }
    };

    // ── Mode ──────────────────────────────────────────────────────────────────
    private enum Mode {
        SELECT,
        ANNOTATE,
        WORLD
    }

    private Mode mode = null;

    // ── WORLD mode inline state ───────────────────────────────────────────────
    private BlockPos wiBlock = null;   // local-space selected block in WORLD mode
    private EditBox wiItemBox;
    private EditBox wiSourceBox;

    // ── Data ──────────────────────────────────────────────────────────────────
    protected final PhantasiaSceneScreen parentScene;
    private final String machineId;
    protected PhantasiaScriptData data;
    public boolean dirty = false;
    private int selectedStep = 0;

    // ── Own 3D world ──────────────────────────────────────────────────────────
    PhantasiaWorldRenderer renderer;
    PhantasiaTrackedDummyWorld editorLevel;
    PhantasiaLoadedPattern pattern;

    /** Pre-computed block registry identifiers — avoids repeated registry lookups during filter eval. */
    private Map<BlockPos, String> blockIdentifierCache = new HashMap<>();
    /** Cached Y-bounds of the pattern so localMinY/localMaxY don't iterate every frame. */
    private int cachedLocalMinY = 0, cachedLocalMaxY = 0;
    /** Ticks remaining until a deferred rebuildVisibility() fires; 0 = none pending. */
    private int rebuildCooldown = 0;
    /** True when any call this tick has requested a visibility rebuild. Fires once in tick(). */
    private boolean rebuildPending = false;
    /** True while the parts-filter editbox had focus last tick; used to detect focus-lost. */
    private boolean partsBoxWasFocused = false;

    // ── Camera ────────────────────────────────────────────────────────────────
    PhantasiaCamera camera;

    // ── SELECT mode ───────────────────────────────────────────────────────────
    private static final int SELECT_PANEL_W = 220;
    private static final int SELECT_ROW_H = 20;

    private final Set<BlockPos> selectedWorldPos = new LinkedHashSet<>();
    private BlockPos hoveredWorldPos = null;
    /** Shared pulse used by WORLD-mode world-item markers (and previously SELECT's dot overlay). */
    private float selectPulse = 0f;
    private boolean pulseUp = true;
    /** When true, viewport shows the real filtered ("pos") result instead of all blocks for picking. */
    private boolean selectPreviewMode = false;
    /** Local XYZ of the row currently hovered in the SELECT side panel, or null. */
    private int[] selectHoveredListPos = null;
    private int selectScrollOffset = 0;
    private EditBox selectAddBox;
    private String selectAddError = null;

    // ── ANNOTATE mode ─────────────────────────────────────────────────────────
    private BlockPos pendingAnnotationLocalPos = null;
    private String pendingAnnotationLabel = "";
    private int selectedMistakeColor = 0;
    private int hoveredMistakeIndex = -1;

    // ── Layer slider ──────────────────────────────────────────────────────────
    private boolean draggingLayer = false;
    private boolean draggingLayerMax = false;

    // ── Timeline dragging ─────────────────────────────────────────────────────
    private int draggingTimelineDot = -1;
    private boolean dotDragMoved = false;
    private double dotDragStartMX = 0;
    private int timelineGhostX = -1;
    private int timelineGhostTick = -1;

    // ── Step reordering ───────────────────────────────────────────────────────
    private int reorderingStep = -1;
    private int reorderInsertAt = -1;

    // ── SELECT deferred click ─────────────────────────────────────────────────
    private boolean selectClickPending = false;
    private int selectClickBtn = 0;
    private double selectClickMX = 0;
    private double selectClickMY = 0;

    // ── Undo ──────────────────────────────────────────────────────────────────
    private static final int MAX_UNDO = 20;
    private final ArrayDeque<PhantasiaScriptData> undoStack = new ArrayDeque<>();
    private final ArrayDeque<Integer> undoStepStack = new ArrayDeque<>();
    /** Set true while populateInputsFromStep() runs to suppress EditBox responders from writing back to data. */
    private boolean isPopulating = false;

    // ── Preview ───────────────────────────────────────────────────────────────
    private boolean previewing = false;
    private int previewTick = 0;
    private int itemAnimTick = 0;
    private float previewAccum = 0f;

    // ── Dialogs / panels ──────────────────────────────────────────────────────
    private boolean showingCloseConfirm = false;
    /** Camera floating panel toggle (top-bar tab) */
    private boolean showCameraPanel = false;
    /** Parts picker modal */
    private boolean showPartsModal = false;
    /** Start-camera panel toggle */
    private boolean showStartCamPanel = false;

    // ── Inputs ────────────────────────────────────────────────────────────────
    private EditBox tickBox;
    private EditBox layerCountBox; // expandable size-variant layer count display (read-only label)
    private EditBox hideLayerBox;
    private EditBox hidePosBox;
    private EditBox lerpTicksBox;
    private EditBox partsExprBox;

    private EditBox rangeMinBox; // ADD THIS LINE
    private EditBox rangeMaxBox; // ADD THIS LINE
    private EditBox camZoomBox;
    private EditBox scriptDurationBox;
    private EditBox scYawBox;
    private EditBox scPitchBox;
    private EditBox scZoomBox;
    private EditBox scOffsetXBox;
    private EditBox scOffsetYBox;
    private EditBox scOffsetZBox;

    // ── Button registry ───────────────────────────────────────────────────────

    // ── Misc ──────────────────────────────────────────────────────────────────
    private int lastMouseX = 0;
    private int lastMouseY = 0;
    private Mode hoveredModeBtnThisFrame = null;
    /** Pending tooltip text; set during render, drawn last as a floating overlay */

    // ── Step clipboard (Ctrl+C / Ctrl+V) ──────────────────────────────────────
    private PhantasiaScriptData.StepData stepClipboard = null;

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────────

    public PhantasiaScriptEditorScreen(PhantasiaSceneScreen parent,
                                       String machineId,
                                       PhantasiaScriptData original) {
        super(Component.translatable("screen.phantasia.script_editor.title"));
        this.parentScene = parent;
        this.machineId = machineId;
        this.data = original.copy();
        ensureOneStep();
    }

    protected Screen createItemEditorScreen(int stepIndex) {
        return new PhantasiaScriptStepItemEditorScreen(this, data, stepIndex);
    }

    private void ensureOneStep() {
        if (data.getSteps().isEmpty()) {
            PhantasiaScriptData.StepData s = new PhantasiaScriptData.StepData(0, null);
            s.show = "all";
            data.getSteps().add(s);
        }
        selectedStep = Mth.clamp(selectedStep, 0, data.getSteps().size() - 1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Init
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();

        if (pattern == null) {
            setupEditorWorld();
        }

        // ── Renderer — borrow the parent scene's already-baked renderer ──────────
        // The editor only changes visibility (GPU state), never geometry, so there
        // is no reason to re-bake. We adopt parentScene.renderer for the duration of
        // this screen and restore full visibility on close.
        if (renderer == null) {
            renderer = parentScene != null ? parentScene.renderer : null;
            initCamera();
        }

        buildInputWidgets();
        populateInputsFromStep();
        rebuildVisibility();
    }

    private void setupEditorWorld() {
        pattern = parentScene != null ? parentScene.getLoadedPattern() : null;
        if (pattern == null) {
            onClose();
            return;
        }

        // Pre-compute block registry identifiers once so evalBlockStateFilter()
        // never does a registry lookup or string allocation during filtering.
        // editorLevel is intentionally not created — SHARED_LEVEL already holds the same data.
        blockIdentifierCache = new HashMap<>(pattern.localToWorld.size());
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (Map.Entry<BlockPos, BlockPos> e : pattern.localToWorld.entrySet()) {
            BlockPos lp = e.getKey(), wp = e.getValue();
            BlockState state = getResolvedState(wp);
            if (state != null && !state.isAir()) {
                blockIdentifierCache.put(wp,
                        net.minecraft.core.registries.BuiltInRegistries.BLOCK
                                .getKey(state.getBlock()).toString()
                                .toLowerCase(java.util.Locale.ROOT));
            }
            int y = lp.getY();
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }
        cachedLocalMinY = minY == Integer.MAX_VALUE ? 0 : minY;
        cachedLocalMaxY = maxY == Integer.MIN_VALUE ? 0 : maxY;
    }

    private void initCamera() {
        if (pattern == null) return;
        float midY = pattern.origin.getY() + (pattern.minY + pattern.maxY) * 0.5f + 0.5f;
        BlockPos cp = pattern.controllerWorldPos != null ? pattern.controllerWorldPos : pattern.origin;
        float tgtX = cp.getX() + 0.5f;
        float tgtZ = cp.getZ() + 0.5f;
        int machineH = pattern.maxY - pattern.minY + 1;
        float dist = 15f + Math.max(0, machineH - 8) * 1.5f;

        float yaw = -135f, pitch = -30f;
        if (!data.getSteps().isEmpty() && data.getSteps().get(0).camera != null) {
            yaw = data.getSteps().get(0).camera.yaw;
            pitch = data.getSteps().get(0).camera.pitch;
        }
        camera = new PhantasiaCamera(yaw, pitch, dist, tgtX, midY, tgtZ);
        camera.setFloorY(pattern.origin.getY() + 0.5f);
    }

    private void buildInputWidgets() {
        clearWidgets();

        tickBox = addW(new EditBox(font, 0, 0, 40, 12, Component.empty()));
        tickBox.setMaxLength(5);
        tickBox.setFilter(s -> s.matches("\\d*"));
        tickBox.setResponder(v -> {
            if (isPopulating) return;
            try {
                step().tick = Integer.parseInt(v);
                dirty = true;
            } catch (NumberFormatException ignored) {}
        });

        // Hidden backing editbox for layerCount — keeps the value in sync but is never rendered.
        // The actual UI is the teal +/- stepper drawn in renderStepRow().
        layerCountBox = addW(new EditBox(font, 0, 0, 30, 12, Component.empty()));
        layerCountBox.setMaxLength(3);
        layerCountBox.setFilter(s -> s.matches("\\d*"));
        layerCountBox.setHint(Component.literal("—"));
        layerCountBox.setResponder(v -> {
            if (isPopulating) return;
            try {
                step().layerCount = v.isBlank() ? -1 : Integer.parseInt(v);
                dirty = true;
            } catch (NumberFormatException ignored) {}
        });
        layerCountBox.visible = false;
        layerCountBox.active = false;

        // --- EXPANDED HIDE LAYER BOX (Supports "1,2,3") ───
        hideLayerBox = addW(new EditBox(font, 0, 0, 45, 12, Component.empty())); // Width synced with layout bounds
        // (45px)
        hideLayerBox.setMaxLength(32);
        hideLayerBox.setFilter(s -> s.matches("[\\d,\\s-]*"));
        hideLayerBox.setHint(Component.translatable("screen.phantasia.script_editor.hint_hide_layer"));
        hideLayerBox.setResponder(v -> {
            if (isPopulating) return;
            PhantasiaScriptData.StepData stepData = step();

            // Clean out any previous multi-Y layer tokens stored inside the hidePositions collection
            stepData.hidePositions.removeIf(xyz -> xyz.length >= 3 && xyz[0] == Integer.MIN_VALUE);

            // Parse the comma-separated layers and bundle them into the collection as unique out-of-bounds tokens
            if (!v.trim().isEmpty()) {
                for (String layerStr : v.split(",")) {
                    try {
                        int targetY = Integer.parseInt(layerStr.trim());
                        stepData.hidePositions.add(new int[] { Integer.MIN_VALUE, targetY, 0 });
                    } catch (NumberFormatException ignored) {}
                }
            }

            // Synchronize legacy step primitive for backwards compatibility where possible
            try {
                stepData.hideLayer = Integer.parseInt(v.trim());
            } catch (NumberFormatException e) {
                stepData.hideLayer = -1;
            }

            dirty = true;
            rebuildVisibility();
        });

        // REMOVED: hidePosBox initialization code was here

        lerpTicksBox = addW(new EditBox(font, 0, 0, 34, 12, Component.empty()));
        lerpTicksBox.setMaxLength(4);
        lerpTicksBox.setFilter(s -> s.matches("\\d*"));
        lerpTicksBox.setHint(Component.translatable("screen.phantasia.script_editor.hint_lerp_ticks"));
        lerpTicksBox.setResponder(v -> {
            if (isPopulating) return;
            PhantasiaScriptData.StepData s = step();
            if (s.camera == null) return;
            try {
                s.camera.lerpTicks = Integer.parseInt(v);
            } catch (NumberFormatException ignored) {
                s.camera.lerpTicks = 20;
            }
            dirty = true;
        });
        lerpTicksBox.visible = false;
        lerpTicksBox.active = false;

        camZoomBox = addW(new EditBox(font, 0, 0, 40, 12, Component.empty()));
        camZoomBox.setMaxLength(7);
        camZoomBox.setFilter(s -> s.matches("-?\\d*\\.?\\d*"));
        camZoomBox.setHint(Component.translatable("screen.phantasia.script_editor.hint_zoom"));
        camZoomBox.setResponder(v -> {
            if (isPopulating) return;
            PhantasiaScriptData.StepData s = step();
            if (s.camera == null) return;
            try {
                s.camera.zoom = Float.parseFloat(v);
            } catch (NumberFormatException ignored) {
                s.camera.zoom = -1f;
            }
            dirty = true;
        });
        camZoomBox.visible = false;
        camZoomBox.active = false;

        wiItemBox = addW(new EditBox(font, 0, 0, 200, 12, Component.empty()));
        wiItemBox.setMaxLength(120);
        wiItemBox.setHint(Component.literal("namespace:item  (blank = clear slot)"));
        wiItemBox.visible = false;
        wiItemBox.active = false;

        wiSourceBox = addW(new EditBox(font, 0, 0, 54, 12, Component.empty()));
        wiSourceBox.setMaxLength(6);
        wiSourceBox.setHint(Component.literal("source (0-10000)"));
        wiSourceBox.setFilter(v -> v.matches("\\d*"));
        wiSourceBox.visible = false;
        wiSourceBox.active = false;

        // --- DYNAMIC VISIBILITY FLAGS ---
        boolean isRangeMode = (this.currentFilterMode == FilterMode.RANGE);
        boolean isPartsMode = (this.currentFilterMode == FilterMode.PARTS);

        // --- RANGE MIN BOX (Isolated Cache Configuration) ---
        rangeMinBox = addW(new EditBox(font, 0, 0, 30, 12, Component.empty()));
        rangeMinBox.setMaxLength(4);
        rangeMinBox.setFilter(s -> s.matches("\\d*"));
        rangeMinBox.setHint(Component.translatable("screen.phantasia.script_editor.hint_range_min"));
        rangeMinBox.setValue(this.cacheRangeMin);
        rangeMinBox.setResponder(v -> {
            if (isPopulating) return;
            this.cacheRangeMin = v;
            checkpoint();
            saveFilterStateToStep();
            scheduleRebuild();
        });
        rangeMinBox.visible = isRangeMode;
        rangeMinBox.active = isRangeMode;

        // --- RANGE MAX BOX (Isolated Cache Configuration) ---
        rangeMaxBox = addW(new EditBox(font, 0, 0, 30, 12, Component.empty()));
        rangeMaxBox.setMaxLength(4);
        rangeMaxBox.setFilter(s -> s.matches("\\d*"));
        rangeMaxBox.setHint(Component.translatable("screen.phantasia.script_editor.hint_range_max"));
        rangeMaxBox.setValue(this.cacheRangeMax);
        rangeMaxBox.setResponder(v -> {
            if (isPopulating) return;
            this.cacheRangeMax = v;
            checkpoint();
            saveFilterStateToStep();
            scheduleRebuild();
        });
        rangeMaxBox.visible = isRangeMode;
        rangeMaxBox.active = isRangeMode;

        // --- PARTS EXPRESSION BOX (Isolated Cache Configuration) ---
        partsExprBox = addW(new EditBox(font, 0, 0, 200, 12, Component.empty()));
        partsExprBox.setMaxLength(128);
        partsExprBox.setHint(Component.translatable("screen.phantasia.script_editor.hint_parts_expr"));
        partsExprBox.setValue(this.cachePartsExpr);
        partsExprBox.setResponder(v -> {
            if (isPopulating) return;
            // Only cache; commit + rebuild on focus-lost so large machines don't bake per keystroke.
            this.cachePartsExpr = v;
        });
        partsExprBox.visible = isPartsMode;
        partsExprBox.active = isPartsMode;

        scriptDurationBox = addW(new EditBox(font, 0, 0, 46, 12, Component.empty()));
        scriptDurationBox.setMaxLength(6);
        scriptDurationBox.setFilter(s -> s.matches("\\d*"));
        scriptDurationBox.setHint(Component.translatable("screen.phantasia.script_editor.hint_zoom"));
        scriptDurationBox.setResponder(v -> {
            if (isPopulating) return;
            try {
                data.setScriptDuration(v.isBlank() ? -1 : Integer.parseInt(v));
            } catch (NumberFormatException ignored) {
                data.setScriptDuration(-1);
            }
            dirty = true;
        });
        scriptDurationBox.visible = false;
        scriptDurationBox.active = false;

        scYawBox = makeFloatBox(v -> {
            ensureStartCam().yaw = v;
            dirty = true;
        }, "\u2212135");
        scPitchBox = makeFloatBox(v -> {
            ensureStartCam().pitch = v;
            dirty = true;
        }, "\u221235");
        scZoomBox = makeFloatBox(v -> {
            ensureStartCam().zoom = v;
            dirty = true;
        }, "auto");
        scOffsetXBox = makeFloatBox(v -> {
            ensureStartCam().targetOffsetX = v;
            dirty = true;
        }, "0");
        scOffsetYBox = makeFloatBox(v -> {
            ensureStartCam().targetOffsetY = v;
            dirty = true;
        }, "0");
        scOffsetZBox = makeFloatBox(v -> {
            ensureStartCam().targetOffsetZ = v;
            dirty = true;
        }, "0");
    }

    private EditBox makeFloatBox(java.util.function.Consumer<Float> setter, String hint) {
        EditBox box = addW(new EditBox(font, 0, 0, 44, 12, Component.empty()));
        box.setMaxLength(8);
        box.setFilter(s -> s.matches("-?\\d*\\.?\\d*"));
        box.setHint(Component.literal(hint));
        box.setResponder(v -> {
            if (isPopulating) return;
            try {
                setter.accept(Float.parseFloat(v));
            } catch (NumberFormatException ignored) {}
        });
        box.visible = false;
        box.active = false;
        return box;
    }

    private PhantasiaScriptData.StartCameraData ensureStartCam() {
        if (data.getStartCamera() == null)
            data.setStartCamera(new PhantasiaScriptData.StartCameraData());
        return data.getStartCamera();
    }

    private void clearStartCam() {
        data.setStartCamera(null);
        populateStartCamBoxes();
        dirty = true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tick
    // ─────────────────────────────────────────────────────────────────────────

    /** Schedule a rebuildVisibility() after a short idle delay — safe to call on every keystroke. */
    private void scheduleRebuild() {
        rebuildCooldown = 3; // ~150 ms at 20 tps
    }

    @Override
    public void tick() {
        super.tick();
        itemAnimTick++;
        if (camera != null) camera.tick();
        if (rebuildCooldown > 0 && --rebuildCooldown == 0) rebuildVisibility();
        if (rebuildPending) flushVisibility();

        // Commit parts-filter and rebuild when the editbox loses focus.
        boolean partsBoxFocused = partsExprBox != null && partsExprBox.isFocused();
        if (partsBoxWasFocused && !partsBoxFocused && partsExprBox != null) {
            checkpoint();
            saveFilterStateToStep();
            rebuildVisibility();
        }
        partsBoxWasFocused = partsBoxFocused;

        if (mode == Mode.SELECT || mode == Mode.WORLD) {
            selectPulse += pulseUp ? 0.07f : -0.07f;
            if (selectPulse >= 1f) {
                selectPulse = 1f;
                pulseUp = false;
            }
            if (selectPulse <= 0f) {
                selectPulse = 0f;
                pulseUp = true;
            }
        }

        if (previewing) {
            previewAccum += 1f;
            while (previewAccum >= 1f) {
                previewAccum -= 1f;
                previewTick++;
            }
            int total = computeTotalTicks();
            if (previewTick >= total) previewTick = 0;
            for (int i = data.getSteps().size() - 1; i >= 0; i--) {
                if (data.getSteps().get(i).tick <= previewTick) {
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

    /** Computes and applies the visible set immediately, then clears any pending flag. */
    public void rebuildVisibility() {
        flushVisibility();
    }

    /** Actually computes and pushes the visible set. Called at most once per tick from tick(). */
    private void flushVisibility() {
        rebuildPending = false;
        if (renderer == null || pattern == null) return;
        PhantasiaScriptData.StepData s = step();
        Set<BlockPos> visible = new HashSet<>(pattern.baseplatePositions);

        if (mode == Mode.SELECT && !selectPreviewMode) {
            for (BlockPos wp : pattern.localToWorld.values()) visible.add(wp);
        } else {
            for (Map.Entry<BlockPos, BlockPos> e : pattern.localToWorld.entrySet()) {
                BlockPos local = e.getKey(), world = e.getValue();
                if (evalShowFilter(s, local, world)) visible.add(world);
            }
        }
        renderer.setVisible(visible);
    }

    // ── PhantasiaHidePosContext impl ──────────────────────────────────────────

    @Override
    public String getHidePosLabel() {
        return "Step " + (selectedStep + 1);
    }

    @Override
    public Screen returnScreen() {
        return this;
    }

    @Override
    public PhantasiaWorldRenderer getRenderer() {
        return renderer;
    }

    @Override
    public PhantasiaCamera getParentCamera() {
        return camera;
    }

    @Override
    public PhantasiaTrackedDummyWorld getEditorLevel() {
        return SHARED_LEVEL;
    }

    @Override
    public BlockPos localToWorld(int[] xyz) {
        if (pattern == null) return null;
        try {
            return pattern.toWorld(new BlockPos(xyz[0], xyz[1], xyz[2]));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public int[] worldToLocal(BlockPos world) {
        if (pattern == null) return null;
        try {
            BlockPos local = pattern.toLocal(world);
            return local != null ? new int[] { local.getX(), local.getY(), local.getZ() } : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public java.util.List<int[]> getHidePositions() {
        return step().hidePositions;
    }

    /** Mirrors {@link PhantasiaHidePosContext#showAllForPickingMode()} — all blocks clickable while picking. */
    @Override
    public void showAllForPickingMode() {
        selectPreviewMode = false;
        rebuildVisibility();
    }

    /** Mirrors {@link PhantasiaHidePosContext#previewVisibility()} — shows the real "pos" filtered result. */
    @Override
    public void previewVisibility() {
        selectPreviewMode = true;
        rebuildVisibility();
    }

    public void markDirty() {
        dirty = true;
    }

    private boolean evalShowFilter(PhantasiaScriptData.StepData s, BlockPos local, BlockPos world) {
        // Hide layer predicates evaluate against everything first
        if (s.hideLayer >= 0 && local.getY() == s.hideLayer) return false;

        for (int[] xyz : s.hidePositions) {
            if (xyz.length >= 3) {
                // Check for our multi-Y array token indicator
                if (xyz[0] == Integer.MIN_VALUE) {
                    if (local.getY() == xyz[1]) return false;
                }
                // Otherwise, check for normal localized block position hide rule
                else if (xyz[0] == local.getX() && xyz[1] == local.getY() && xyz[2] == local.getZ()) {
                    return false;
                }
            }
        }

        String show = s.show == null ? "all" : s.show.toLowerCase(java.util.Locale.ROOT);
        return switch (show) {
            case "all" -> true;
            case "layer" -> local.getY() == s.layer;
            case "layers" -> local.getY() >= s.layerMin && local.getY() <= s.layerMax;
            case "pos" -> isInPositionList(local);
            default -> evalBlockStateFilter(show, world);
        };
    }

    private void handlePartsPresetClick(String presetValue) {
        PhantasiaScriptData.StepData s = step();
        checkpoint();

        if (presetValue.startsWith("parts:")) {
            String subExpr = presetValue.substring(6);
            String currentText = this.cachePartsExpr.trim();

            if (currentText.isEmpty() || currentText.contains(subExpr)) {
                this.cachePartsExpr = subExpr;
            } else {
                this.cachePartsExpr = currentText + " | " + subExpr;
            }

            partsExprBox.setValue(this.cachePartsExpr);
            this.currentFilterMode = FilterMode.PARTS; // Lock into parts mode
            saveFilterStateToStep();
        } else {
            // Fallback parameters if clicking raw categories
            s.show = presetValue;
            this.cachePartsExpr = "";
            partsExprBox.setValue("");
        }

        dirty = true;
        rebuildVisibility();
    }

    private boolean evalBlockStateFilter(String show, BlockPos world) {
        if (SHARED_LEVEL == null) return false;

        // 1. CRITICAL: Always check and reject air/null states first.
        // This prevents empty filter strings from returning 'true' for thousands of air coordinates,
        // which floods the renderer pass and causes the multiblock to go "poof" and disappear.
        BlockState state = getResolvedState(world);
        if (state == null || state.isAir()) return false;

        // 2. Now check if the selection filter string is empty or clear
        if (show == null || show.isEmpty() || show.equals("all")) return true;
        String expression = show.startsWith("parts:") ? show.substring(6) : show;
        if (expression.trim().isEmpty()) return true;

        // 3. Evaluate the pre-computed identifier for active selection filtering
        String fullIdentifier = blockIdentifierCache.getOrDefault(world, "");
        return evalPartsExpr(expression, fullIdentifier, state);
    }

    private static boolean evalPartsExpr(String expr, String fullIdentifier, BlockState state) {
        if (state == null) return false;

        // CLEAN FALLBACK: If the filter bar is blank, bypass evaluation and show the block!
        if (expr == null || expr.trim().isEmpty()) {
            return true;
        }

        // Ensure the entire registry path is lowercased for safe matching
        String cleanPath = fullIdentifier.toLowerCase(java.util.Locale.ROOT);
        return evalSubExpression(expr.trim(), cleanPath, state);
    }

    private static boolean evalSubExpression(String expr, String path, BlockState state) {
        if (expr.isEmpty()) return true;

        // 1. Process top-level OR (|) groups, skipping tokens isolated inside parentheses
        List<String> orGroups = new ArrayList<>();
        int bracketDepth = 0;
        int lastSplitIndex = 0;

        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '(') bracketDepth++;
            else if (c == ')') bracketDepth--;
            else if (c == '|' && bracketDepth == 0) {
                orGroups.add(expr.substring(lastSplitIndex, i));
                lastSplitIndex = i + 1;
            }
        }
        orGroups.add(expr.substring(lastSplitIndex));

        // Evaluate OR branches independently
        if (orGroups.size() > 1) {
            for (String group : orGroups) {
                if (evalSubExpression(group, path, state)) return true;
            }
            return false;
        }

        // 2. Process top-level AND (&) chains, skipping tokens isolated inside parentheses
        List<String> andTerms = new ArrayList<>();
        bracketDepth = 0;
        lastSplitIndex = 0;

        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '(') bracketDepth++;
            else if (c == ')') bracketDepth--;
            else if (c == '&' && bracketDepth == 0) {
                andTerms.add(expr.substring(lastSplitIndex, i));
                lastSplitIndex = i + 1;
            }
        }
        andTerms.add(expr.substring(lastSplitIndex));

        // Every chained segment must evaluate to true
        if (andTerms.size() > 1) {
            for (String term : andTerms) {
                if (!evalSubExpression(term, path, state)) return false;
            }
            return true;
        }

        // 3. Evaluate atomic terms, peeling back brackets or negative indicators
        String term = expr.trim();
        if (term.startsWith("!")) {
            return !evalSubExpression(term.substring(1).trim(), path, state);
        }

        if (term.startsWith("(") && term.endsWith(")")) {
            return evalSubExpression(term.substring(1, term.length() - 1).trim(), path, state);
        }

        // Base verification function for an individual token
        return matchToken(term, path, state);
    }

    private static boolean matchToken(String token, String path, BlockState state) {
        String cleanToken = token.trim().toLowerCase(java.util.Locale.ROOT);
        if (cleanToken.isEmpty()) return true;

        // 1. Structural Scopes -> @type(controller), @type(functional), @type(parts)
        if (cleanToken.startsWith("@type(") && cleanToken.endsWith(")")) {
            String typeValue = cleanToken.substring(6, cleanToken.length() - 1).trim();

            return switch (typeValue) {
                case "controller" -> state.getBlock() instanceof MetaMachineBlock mmb &&
                        mmb.getDefinition() instanceof MultiblockMachineDefinition;

                case "functional" -> state.getBlock() instanceof MetaMachineBlock ||
                        state.getBlock().getDescriptionId().contains("frame") ||
                        state.getBlock().getDescriptionId().contains("gearbox");

                case "parts" -> {
                    if (!(state.getBlock() instanceof MetaMachineBlock mmb)) yield false;
                    if (mmb.getDefinition() instanceof MultiblockMachineDefinition) yield false;
                    String p = mmb.getDefinition().getId().getPath();
                    yield p.contains("hatch") || p.contains("bus") || p.contains("port") || p.contains("storage") ||
                            p.contains("input") || p.contains("output") || p.contains("muffler") ||
                            p.contains("maintenance");
                }

                default -> false;
            };
        }

        // Add this right alongside your @type, @ability, and @block hooks
        if (cleanToken.equals("@coil")) {
            Block currentBlock = state.getBlock();

            // Scan GregTech's internal API registry map for a direct reference match
            for (java.util.function.Supplier<com.gregtechceu.gtceu.common.block.CoilBlock> coilSupplier : com.gregtechceu.gtceu.api.GTCEuAPI.HEATING_COILS
                    .values()) {
                if (coilSupplier != null && coilSupplier.get() == currentBlock) {
                    return true;
                }
            }
            return false;
        }

        // 2. Ability Scopes -> @ability(input), @ability(muffler), etc.
        if (cleanToken.startsWith("@ability(") && cleanToken.endsWith(")")) {
            String abilityValue = cleanToken.substring(9, cleanToken.length() - 1).trim();
            // Checks the registry path string specifically for technical keywords
            return path.contains(abilityValue);
        }

        // 3. Strict Block Scopes -> @block(gtceu:input_bus)
        if (cleanToken.startsWith("@block(") && cleanToken.endsWith(")")) {
            String blockTarget = cleanToken.substring(7, cleanToken.length() - 1).trim();
            return path.contains(blockTarget);
        }

        // Fallback: If they type a raw word without a prefix, treat it as a loose block search
        return path.contains(cleanToken);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Render
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void renderAsBackground(GuiGraphics g, float partial) {
        g.fill(0, 0, this.width, this.height, C_BG());
        if (renderer != null && camera != null) {
            int sceneH = this.height - TOP_BAR_H - BOTTOM_H;
            CameraView view = camera.getView(partial);
            renderer.render(view, 0, TOP_BAR_H, this.width, sceneH);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        btns.clear();
        hideAllInputs();
        pendingTooltip = null;
        lastMouseX = mx;
        lastMouseY = my;

        g.fill(0, 0, this.width, this.height, C_BG());

        // 3D scene
        if (renderer != null && camera != null) {
            int sceneH = this.height - TOP_BAR_H - BOTTOM_H;
            boolean overCamPanel = showCameraPanel && isCamPanelHit(mx, my);
            renderer.setMousePos(overCamPanel ? -1 : mx, overCamPanel ? -1 : my);
            CameraView view = camera.getView(partial);
            renderer.render(view, 0, TOP_BAR_H, this.width, sceneH);
            BlockHitResult hit = renderer.getLastHitResult();
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos hp = hit.getBlockPos();
                // In SELECT mode allow hovering invisible blocks so the user can click to add them
                hoveredWorldPos = (mode == Mode.SELECT || renderer.isVisible(hp)) ? hp : null;
            } else {
                hoveredWorldPos = null;
            }
        }

        renderInSceneOverlays(g, mx, my);
        renderTopBar(g, mx, my);
        renderModeTooltipBanner(g);
        renderLayerSlider(g, mx, my);
        renderStepRow(g, mx, my);
        renderTimeline(g, mx, my);
        renderEditorItemPanel(g, mx, my);
        if (showStartCamPanel) {
            renderStartCamPanel(g, mx, my);
        } else {
            for (var box : List.of(scYawBox, scPitchBox, scZoomBox, scOffsetXBox, scOffsetYBox, scOffsetZBox)) {
                if (box != null) {
                    box.visible = false;
                    box.active = false;
                }
            }
        }
        // Camera panel floats above the step row
        if (showCameraPanel) renderCameraPanel(g, mx, my);
        // Parts modal dims everything
        if (showPartsModal) renderPartsModal(g, mx, my);

        super.render(g, mx, my, partial);

        // Block name + local XYZ tooltip (all modes, not just SELECT)
        if (hoveredWorldPos != null && SHARED_LEVEL != null && pattern != null) {
            try {
                BlockState bs = getResolvedState(hoveredWorldPos);

                if (bs != null && !bs.isAir()) {
                    BlockPos local = pattern.toLocal(hoveredWorldPos);
                    String coords = local != null ? local.getX() + ", " + local.getY() + ", " + local.getZ() :
                            hoveredWorldPos.toShortString();
                    java.util.List<net.minecraft.network.chat.Component> lines = java.util.List.of(
                            bs.getBlock().getName(),
                            net.minecraft.network.chat.Component.literal(coords)
                                    .withStyle(style -> style.withColor(0x667788)));
                    g.renderComponentTooltip(font, lines, mx, my);
                }
            } catch (Exception ignored) {}
        }

        if (showingCloseConfirm)
            renderCloseConfirmDialog(g, mx, my, this::forceClose, () -> showingCloseConfirm = false);

        // Floating tooltip drawn absolutely last
        renderPendingTooltip(g, mx, my);
    }

    @Override
    public void hideAllInputs() {
        if (tickBox != null) tickBox.visible = false;
        if (layerCountBox != null) layerCountBox.visible = false;
        if (hideLayerBox != null) hideLayerBox.visible = false;
        if (lerpTicksBox != null) lerpTicksBox.visible = false;
        if (camZoomBox != null) camZoomBox.visible = false;
        if (wiItemBox != null) {
            wiItemBox.visible = false;
            wiItemBox.active = false;
        }
        if (wiSourceBox != null) {
            wiSourceBox.visible = false;
            wiSourceBox.active = false;
        }

        // Explicitly handles dynamic mode boxes safely when they are null
        if (rangeMinBox != null) rangeMinBox.visible = false;
        if (rangeMaxBox != null) rangeMaxBox.visible = false;
        if (partsExprBox != null) partsExprBox.visible = false;
        if (scriptDurationBox != null) scriptDurationBox.visible = false;
    }
    // ─────────────────────────────────────────────────────────────────────────
    // In-scene overlays
    // ─────────────────────────────────────────────────────────────────────────

    private void renderModeTooltipBanner(GuiGraphics g) {
        if (hoveredModeBtnThisFrame == null || hoveredModeBtnThisFrame == mode) return;
        String tip = switch (hoveredModeBtnThisFrame) {
            case SELECT -> "SELECT — Click blocks to add/remove from this step's position list";
            case ANNOTATE -> "ANNOTATE — Click any block to attach a floating mistake label";
            case WORLD -> "WORLD — Click any block entity to set the item or source amount for this step";
        };
        drawBanner(g, tip, TOP_BAR_H + 4, C_DIM());
    }

    private void renderInSceneOverlays(GuiGraphics g, int mx, int my) {
        renderMistakeMarkers(g);
        if (mode == Mode.SELECT) renderSelectOverlay(g, mx, my);
        if (mode == Mode.ANNOTATE) renderAnnotateOverlay(g, mx, my);
        if (mode == Mode.WORLD) renderWorldItemOverlay(g, mx, my);
    }

    private void renderMistakeMarkers(GuiGraphics g) {
        if (data.getMistakes().isEmpty() || pattern == null || camera == null) return;
        hoveredMistakeIndex = -1;
        CameraView view = camera.getView(0f);
        Vector3f eye = view.eyePos(), lookat = view.lookAt();
        Vector3f fwd = new Vector3f(lookat).sub(eye).normalize();
        Vector3f rgt = new Vector3f(fwd).cross(new Vector3f(0, 1, 0)).normalize();
        Vector3f upv = new Vector3f(rgt).cross(fwd).normalize();
        float fov = this.height / (2f * (float) Math.tan(Math.toRadians(PhantasiaCamera.FOV)));

        for (int i = 0; i < data.getMistakes().size(); i++) {
            PhantasiaScriptData.MistakeData m = data.getMistakes().get(i);
            BlockPos local = new BlockPos(m.x, m.y, m.z);
            BlockPos world = pattern.localToWorld.get(local);
            if (world == null) continue;
            float wx = world.getX() + 0.5f, wy = world.getY() + 1.4f, wz = world.getZ() + 0.5f;
            Vector3f toP = new Vector3f(wx - eye.x(), wy - eye.y(), wz - eye.z());
            float depth = toP.dot(fwd);
            if (depth < 0.5f) continue;
            float sx = this.width / 2f + (toP.dot(rgt) / depth) * fov;
            float sy = this.height / 2f - (toP.dot(upv) / depth) * fov;
            int isx = (int) sx, isy = (int) sy;
            if (isy < TOP_BAR_H || isy > this.height - BOTTOM_H) continue;
            int col = m.colorArgb(), lw = font.width(m.label) + 8;
            g.fill(isx - lw / 2 - 1, isy - 6, isx + lw / 2 + 1, isy + 8, 0xCC000000);
            g.fill(isx - lw / 2 - 1, isy - 6, isx + lw / 2 + 1, isy - 5, col);
            g.drawCenteredString(font, m.label, isx, isy - 3, col);
            g.fill(isx - 1, isy + 8, isx + 1, isy + 14, col & 0x88FFFFFF);
            if (mode == Mode.ANNOTATE && isOver(lastMouseX, lastMouseY, isx - lw / 2 - 1, isy - 6, lw + 2, 14))
                hoveredMistakeIndex = i;
        }
    }

    private void renderSelectOverlay(GuiGraphics g, int mx, int my) {
        int hy = TOP_BAR_H + 4;
        String hint;
        if (selectPreviewMode) {
            hint = "Previewing filtered result \u2014 click \u25CB Filtered to resume picking";
        } else {
            hint = selectedWorldPos.isEmpty() ?
                    "Left-click blocks to add to step  |  Ctrl+A: select all  |  Ctrl+D: clear" :
                    selectedWorldPos.size() + " block" + (selectedWorldPos.size() == 1 ? "" : "s") +
                            " selected  \u2014  Left-click to toggle  |  Right-click to remove";
        }
        int selViewW = this.width - SELECT_PANEL_W;
        drawBanner(g, hint, hy, C_ACCENT());

        // Cursor-following tooltip on a hoverable block — mirrors HidePos's hoveredViewportPos tooltip,
        // no 3D crosshairs or dots; the renderer's own block model is the only thing drawn in-world.
        if (!selectPreviewMode && hoveredWorldPos != null && pattern != null && mx < selViewW) {
            BlockPos local = pattern.toLocal(hoveredWorldPos);
            if (local != null && !pattern.baseplatePositions.contains(hoveredWorldPos)) {
                boolean isSel = isInPositionList(local);
                String blockName = "";
                try {
                    BlockState bs = getResolvedState(hoveredWorldPos);
                    if (bs != null && !bs.isAir()) blockName = bs.getBlock().getName().getString();
                } catch (Exception ignored) {}

                String coordStr = local.getX() + ", " + local.getY() + ", " + local.getZ();
                String line1 = blockName.isEmpty() ? coordStr : blockName;
                String line2 = (blockName.isEmpty() ? "" : "  " + coordStr) +
                        (blockName.isEmpty() ? "" : "  \u2014  ") + (isSel ? "Remove" : "Add");
                int tipX = mx + 10, tipY = my - 22;
                int tipW = Math.max(font.width(line1), font.width(line2)) + 8;
                int tipH = 24;
                tipX = Math.min(tipX, selViewW - tipW - 2);
                tipY = Math.max(tipY, TOP_BAR_H + 2);

                g.fill(tipX - 1, tipY - 1, tipX + tipW + 1, tipY + tipH + 1, 0xBB000000);
                g.fill(tipX - 1, tipY - 1, tipX, tipY + tipH + 1, isSel ? C_WARN() : C_GREEN());
                g.drawString(font, line1, tipX + 3, tipY + 3, C_TEXT(), false);
                g.drawString(font, line2, tipX + 3, tipY + 13, isSel ? C_WARN() : C_GREEN(), false);
            }
        }

        // Dim the viewport when a row is hovered in the side panel — same idea as HidePos's
        // "can't easily draw a 3D overlay, so flash a subtle tint" approach.
        if (selectHoveredListPos != null && mx >= selViewW) {
            g.fill(0, TOP_BAR_H, selViewW, this.height - BOTTOM_H, 0x2200FF66);
            String coordLabel = "  Selected: " + selectHoveredListPos[0] + ", " + selectHoveredListPos[1] + ", " +
                    selectHoveredListPos[2];
            g.fill(2, TOP_BAR_H + 2, font.width(coordLabel) + 6, TOP_BAR_H + 14, 0xAA000000);
            g.drawString(font, coordLabel, 4, TOP_BAR_H + 4, C_GREEN(), false);
        }
    }

    private void renderAnnotateOverlay(GuiGraphics g, int mx, int my) {
        if (pendingAnnotationLocalPos != null) {
            int px = this.width / 2 - 160, py = this.height - BOTTOM_H - 52, pw = 320;
            g.fill(px, py, px + pw, py + 50, C_BAR());
            g.fill(px, py, px + pw, py + 1, C_WARN());
            String labelPreview = pendingAnnotationLabel.isBlank() ? "\u270E  Type label..." :
                    "\u270E  " + pendingAnnotationLabel;
            int lpW = pw - 14;
            boolean lpHov = isOver(mx, my, px + 6, py + 4, lpW, 14);
            g.fill(px + 6, py + 4, px + 6 + lpW, py + 18, lpHov ? C_BTN_HOV() : C_BTN());
            g.fill(px + 6, py + 4, px + 6 + lpW, py + 5, C_WARN());
            g.drawString(font, labelPreview, px + 10, py + 7,
                    pendingAnnotationLabel.isBlank() ? C_DIM() : C_TEXT(), false);
            btns.add(new Btn(px + 6, py + 4, lpW, 14, this::openAnnotationLabelInput));
            int sx = px + 6, sy = py + 22;
            g.drawString(font, Component.translatable("screen.phantasia.script_editor.label_colour").getString(), sx,
                    sy + 1, C_DIM(), false);
            sx += 44;
            for (int i = 0; i < MISTAKE_COLORS.length; i++) {
                boolean sel = (i == selectedMistakeColor);
                boolean hov = isOver(mx, my, sx, sy, 16, 12);
                g.fill(sx, sy, sx + 16, sy + 12, MISTAKE_COLORS[i]);
                if (sel) {
                    g.fill(sx - 1, sy - 1, sx + 17, sy, 0xFFFFFFFF);
                    g.fill(sx - 1, sy + 12, sx + 17, sy + 13, 0xFFFFFFFF);
                }
                int fi = i;
                btns.add(new Btn(sx, sy, 16, 12, () -> selectedMistakeColor = fi));
                if (hov) pendingTooltip = MISTAKE_COLOR_NAMES[i];
                sx += 20;
            }
            int btnY = py + 36;
            btn(g, mx, my, px + pw - 120, btnY, 54, 12, "\u2713 Add", C_GREEN(), this::confirmAnnotation);
            btn(g, mx, my, px + pw - 62, btnY, 54, 12, "\u2715 Cancel", C_BTN(), this::cancelAnnotation);
            g.drawString(font, "Marking: " + pendingAnnotationLocalPos.toShortString(), px + 6, btnY + 1, C_DIM(),
                    false);
            return;
        }
        String hint = hoveredMistakeIndex >= 0 ? "Right-click marker to remove  |  Left-click block to add" :
                "Left-click any block to add a mistake marker";
        drawBanner(g, hint, TOP_BAR_H + 4, C_WARN());
        if (mode == Mode.ANNOTATE) {
            java.util.List<String> gm = data.getGlobalMistakes();
            int ROW = 13;
            int panelW = 260;
            int panelH = 18 + gm.size() * ROW + ROW; // header + rows + add row
            int panelX = this.width - panelW - 6;
            int panelY = this.height - BOTTOM_H - panelH - 4;
            g.fill(panelX, panelY, panelX + panelW, panelY + panelH, C_PANEL());
            g.fill(panelX, panelY, panelX + panelW, panelY + 1, C_WARN());
            g.drawString(font,
                    Component.translatable("screen.phantasia.script_editor.section_global_mistakes").getString(),
                    panelX + 5, panelY + 4, C_WARN(), false);
            int ry = panelY + 16;
            for (int i = 0; i < gm.size(); i++) {
                String w = gm.get(i);
                g.fill(panelX + 2, ry, panelX + panelW - 2, ry + ROW, 0x22FFFFFF);
                g.drawString(font, trunc(w, panelW - 26), panelX + 5, ry + 2, C_TEXT(), false);
                int rbx = panelX + panelW - 14;
                boolean rbHov = isOver(mx, my, rbx, ry + 1, 11, ROW - 2);
                g.fill(rbx, ry + 1, rbx + 11, ry + ROW - 1, rbHov ? 0xAA440000 : 0x55220000);
                g.drawCenteredString(font, "\u2715", rbx + 5, ry + 2, rbHov ? 0xFFFF5555 : C_DIM());
                final int fi = i;
                btns.add(new Btn(rbx, ry + 1, 11, ROW - 2, () -> {
                    checkpoint();
                    data.getGlobalMistakes().remove(fi);
                    dirty = true;
                }));
                ry += ROW;
            }
            boolean addHov = isOver(mx, my, panelX + 2, ry + 1, panelW - 4, ROW - 2);
            g.fill(panelX + 2, ry + 1, panelX + panelW - 2, ry + ROW - 1, addHov ? C_BTN_HOV() : C_BTN());
            g.fill(panelX + 2, ry + 1, panelX + panelW - 2, ry + 2, C_WARN());
            g.drawCenteredString(font,
                    Component.translatable("screen.phantasia.script_editor.btn_add_global_mistake").getString(),
                    panelX + panelW / 2, ry + 3,
                    addHov ? C_ACCENT() : C_DIM());
            btns.add(new Btn(panelX + 2, ry + 1, panelW - 4, ROW - 2, this::openGlobalMistakeInput));
        }
    }

    private void openAnnotationLabelInput() {
        Minecraft.getInstance().setScreen(new PhantasiaTextInputScreen(
                this, "Annotation Label", "e.g. Wrong block here", pendingAnnotationLabel, 128,
                v -> pendingAnnotationLabel = v));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WORLD mode overlay + inline panel
    // ─────────────────────────────────────────────────────────────────────────

    private void renderWorldItemOverlay(GuiGraphics g, int mx, int my) {
        if (pattern == null || camera == null || SHARED_LEVEL == null) return;
        CameraView view = camera.getView(0f);
        Vector3f eye = view.eyePos(), lookat = view.lookAt();
        Vector3f fwd = new Vector3f(lookat).sub(eye).normalize();
        Vector3f rgt = new Vector3f(fwd).cross(new Vector3f(0, 1, 0)).normalize();
        Vector3f upv = new Vector3f(rgt).cross(fwd).normalize();
        float fov = this.height / (2f * (float) Math.tan(Math.toRadians(PhantasiaCamera.FOV)));

        java.util.List<PhantasiaScriptData.WorldItemEntry> entries = step().worldItems;
        int sceneBottom = this.height - BOTTOM_H;

        for (java.util.Map.Entry<BlockPos, BlockPos> e : pattern.localToWorld.entrySet()) {
            BlockPos local = e.getKey();
            BlockPos world = e.getValue();
            if (SHARED_LEVEL.getBlockEntity(world) == null) continue;
            BlockState bs = getResolvedState(world);
            if (bs != null && bs.getBlock() instanceof MetaMachineBlock) continue;

            boolean hasEntry = entries.stream()
                    .anyMatch(wi -> wi.x == local.getX() && wi.y == local.getY() && wi.z == local.getZ());
            boolean isSelected = local.equals(wiBlock);

            float[] sc = projectToScreen(world.getX() + 0.5f, world.getY() + 0.5f, world.getZ() + 0.5f,
                    eye, fwd, rgt, upv, fov);
            if (sc == null || sc[2] < 0.3f) continue;
            int isx = (int) sc[0], isy = (int) sc[1];
            if (isy < TOP_BAR_H || isy > sceneBottom) continue;

            if (isSelected) {
                g.fill(isx - 5, isy - 5, isx + 5, isy + 5, 0xCCFFFFFF);
                g.fill(isx - 3, isy - 3, isx + 3, isy + 3, C_ACCENT() | 0xFF000000);
            } else if (hasEntry) {
                int alpha = (int) (0x55 + selectPulse * 0x77);
                g.fill(isx - 4, isy - 4, isx + 4, isy + 4, (alpha << 24) | (C_GREEN() & 0xFFFFFF));
            } else {
                g.fill(isx - 3, isy - 3, isx + 3, isy + 3, 0x44AAAAAA);
            }
        }

        // Banner hint
        if (wiBlock == null) {
            String beCount = entries.isEmpty() ? "" : " (" + entries.size() + " set)";
            drawBanner(g, "Click a block entity to edit its item for this step — right-click to remove entry" + beCount,
                    TOP_BAR_H + 4, C_DIM());
        }

        // Inline panel when a block is selected
        if (wiBlock != null) renderWorldItemPanel(g, mx, my);
    }

    private static final int WI_PANEL_H = 62;

    private void renderWorldItemPanel(GuiGraphics g, int mx, int my) {
        int panelW = Math.min(440, this.width - 16);
        int panelX = 6;
        int panelY = this.height - BOTTOM_H - WI_PANEL_H - 4;

        g.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + WI_PANEL_H + 2, 0xDD070712);
        g.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY - 1, C_ACCENT());

        // Block name header
        String label = "World Items — " + wiBlock.toShortString();
        if (SHARED_LEVEL != null) {
            BlockPos wp = pattern != null ? pattern.localToWorld.get(wiBlock) : null;
            if (wp != null) {
                BlockState bs = getResolvedState(wp);
                if (!bs.isAir()) label += "  ·  " + bs.getBlock().getName().getString();
            }
        }
        g.drawString(font, label, panelX + 4, panelY + 3, C_ACCENT(), false);

        int row1Y = panelY + 14;
        int x = panelX + 4;

        // Item field
        g.drawString(font, Component.translatable("screen.phantasia.script_editor.label_item").getString(), x,
                row1Y + 2, C_DIM(), false);
        x += font.width(Component.translatable("screen.phantasia.script_editor.label_item").getString()) + 4;
        placeBox(wiItemBox, x, row1Y, panelW - 200, 12);
        x += panelW - 200 + 8;

        // Source field
        g.drawString(font, Component.translatable("screen.phantasia.script_editor.label_source").getString(), x,
                row1Y + 2, C_DIM(), false);
        x += font.width(Component.translatable("screen.phantasia.script_editor.label_source").getString()) + 4;
        placeBox(wiSourceBox, x, row1Y, 54, 12);
        x += 62;

        // Confirm button
        int confirmW = font.width(Component.translatable("ui.phantasia.btn_confirm").getString()) + 12;
        boolean confHov = isOver(mx, my, x, row1Y, confirmW, 13);
        g.fill(x, row1Y, x + confirmW, row1Y + 13, confHov ? C_BTN_HOV() : C_GREEN());
        if (confHov) g.fill(x, row1Y, x + confirmW, row1Y + 1, C_ACCENT());
        g.drawString(font, Component.translatable("ui.phantasia.btn_confirm").getString(), x + 6, row1Y + 2,
                confHov ? C_ACCENT() : C_TEXT(), false);
        btns.add(new Btn(x, row1Y, confirmW, 13, this::confirmWorldItemEntry));
        x += confirmW + 4;

        // Remove button
        java.util.List<PhantasiaScriptData.WorldItemEntry> entries = step().worldItems;
        boolean hasEntry = wiBlock != null && entries.stream().anyMatch(
                wi -> wi.x == wiBlock.getX() && wi.y == wiBlock.getY() && wi.z == wiBlock.getZ());
        if (hasEntry) {
            int remW = font.width(Component.translatable("ui.phantasia.btn_remove").getString()) + 12;
            boolean remHov = isOver(mx, my, x, row1Y, remW, 13);
            g.fill(x, row1Y, x + remW, row1Y + 13, remHov ? C_BTN_HOV() : C_BTN());
            g.drawString(font, Component.translatable("ui.phantasia.btn_remove").getString(), x + 6, row1Y + 2,
                    remHov ? C_RED() : C_DIM(), false);
            btns.add(new Btn(x, row1Y, remW, 13, this::removeWorldItemEntry));
        }

        // Row 2: hint
        int row2Y = panelY + 30;
        g.drawString(font, Component.translatable("screen.phantasia.script_editor.hint_item_blank").getString(),
                panelX + 4, row2Y + 2, C_DIM(), false);

        // recipeId conflict note
        if (data.getRecipeId() != null && !data.getRecipeId().isBlank() && hasEntry) {
            g.drawString(font,
                    Component.translatable("screen.phantasia.script_editor.warn_recipe_override").getString(),
                    panelX + 4, row2Y + 14, 0xFFFFAA44, false);
        }
    }

    private void confirmWorldItemEntry() {
        if (wiBlock == null) return;
        String item = wiItemBox.getValue().trim();
        int sourceAmt = wiSourceBox.getValue().isBlank() ? -1 : parseIntSafe(wiSourceBox.getValue(), -1);
        if (item.isBlank() && sourceAmt < 0) {
            wiBlock = null;
            return;
        }

        checkpoint();
        java.util.List<PhantasiaScriptData.WorldItemEntry> entries = step().worldItems;
        entries.removeIf(wi -> wi.x == wiBlock.getX() && wi.y == wiBlock.getY() && wi.z == wiBlock.getZ());
        PhantasiaScriptData.WorldItemEntry entry = new PhantasiaScriptData.WorldItemEntry(wiBlock.getX(),
                wiBlock.getY(), wiBlock.getZ(), item);
        entry.sourceAmount = sourceAmt;
        entries.add(entry);
        dirty = true;
        parentScene.refireShapeLoaded();
        wiBlock = null;
        wiItemBox.setValue("");
        wiSourceBox.setValue("");
    }

    private void removeWorldItemEntry() {
        if (wiBlock == null) return;
        checkpoint();
        step().worldItems.removeIf(wi -> wi.x == wiBlock.getX() && wi.y == wiBlock.getY() && wi.z == wiBlock.getZ());
        dirty = true;
        parentScene.refireShapeLoaded();
        wiBlock = null;
        wiItemBox.setValue("");
        wiSourceBox.setValue("");
    }

    private boolean handleWorldItemClick(double mx, double my, int btn) {
        if (hoveredWorldPos == null || pattern == null || SHARED_LEVEL == null) return false;
        BlockPos local = pattern.toLocal(hoveredWorldPos);
        if (local == null || pattern.baseplatePositions.contains(hoveredWorldPos)) return false;
        if (SHARED_LEVEL.getBlockEntity(hoveredWorldPos) == null) return false;
        BlockState bs = getResolvedState(hoveredWorldPos);
        if (bs != null && bs.getBlock() instanceof MetaMachineBlock) return false;

        if (btn == 1) {
            // Right-click: remove entry
            if (step().worldItems
                    .removeIf(wi -> wi.x == local.getX() && wi.y == local.getY() && wi.z == local.getZ())) {
                checkpoint();
                dirty = true;
                parentScene.refireShapeLoaded();
                if (local.equals(wiBlock)) wiBlock = null;
            }
            return true;
        }

        // Left-click: select block and populate fields
        wiBlock = local;
        PhantasiaScriptData.WorldItemEntry existing = step().worldItems.stream()
                .filter(wi -> wi.x == local.getX() && wi.y == local.getY() && wi.z == local.getZ())
                .findFirst().orElse(null);
        wiItemBox.setValue(existing != null && existing.item != null ? existing.item : "");
        wiSourceBox
                .setValue(existing != null && existing.sourceAmount >= 0 ? String.valueOf(existing.sourceAmount) : "");
        setFocused(wiItemBox);
        return true;
    }

    private static int parseIntSafe(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void openGlobalMistakeInput() {
        Minecraft.getInstance().setScreen(new PhantasiaTextInputScreen(
                this, "Global Mistake Note", "e.g. Controller must face south", "", 256, v -> {
                    if (!v.isBlank()) {
                        checkpoint();
                        data.getGlobalMistakes().add(v.trim());
                        dirty = true;
                    }
                }));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Top bar
    // ─────────────────────────────────────────────────────────────────────────

    private void renderTopBar(GuiGraphics g, int mx, int my) {
        hoveredModeBtnThisFrame = null;
        g.fill(0, 0, this.width, TOP_BAR_H, C_PANEL());
        g.fill(0, TOP_BAR_H - 1, this.width, TOP_BAR_H, C_ACCENT());

        int x = 6;
        x = modeBtn(g, mx, my, x, Mode.SELECT, "\u25C8 Select");

        // Select pick/preview toggle — mirrors HidePos's previewMode button.
        // Only shown while actively in SELECT mode.
        if (mode == Mode.SELECT) {
            String selPrevLabel = selectPreviewMode ? "\u25C9 Filtered" : "\u25CB Filtered";
            int selPrevW = font.width(selPrevLabel) + 10;
            boolean selPrevHov = isOver(mx, my, x, 3, selPrevW, TOP_BAR_H - 6);
            g.fill(x, 3, x + selPrevW, TOP_BAR_H - 3,
                    selectPreviewMode ? C_BTN_ACT() : (selPrevHov ? C_BTN_HOV() : C_BTN()));
            if (selectPreviewMode) g.fill(x, 3, x + selPrevW, 4, C_ACCENT());
            g.drawString(font, selPrevLabel, x + 5, (TOP_BAR_H - 8) / 2,
                    selectPreviewMode ? C_ACCENT() : C_TEXT(), false);
            if (selPrevHov) pendingTooltip = selectPreviewMode ?
                    "Showing only selected blocks \u2014 click to switch back to pick mode" :
                    "Showing all blocks for picking \u2014 click to preview the selected-only result";
            btns.add(new Btn(x, 3, selPrevW, TOP_BAR_H - 6, () -> {
                if (selectPreviewMode) showAllForPickingMode();
                else previewVisibility();
            }));
            x += selPrevW + 4;
        }

        x = modeBtn(g, mx, my, x, Mode.ANNOTATE, "\u26A0 Annotate");
        // GT machines use IItemHandler capability \u2014 their BEs don't expose a visual inventory,
        // so the World item placer has no effect and should not be shown.
        if (!machineId.startsWith("gtceu:")) {
            x = modeBtn(g, mx, my, x, Mode.WORLD, "\u25A6 World");
        } else if (mode == Mode.WORLD) {
            mode = Mode.ANNOTATE;
        }
        x += 6;

        // Preview
        boolean ph = isOver(mx, my, x, 3, 70, TOP_BAR_H - 6);
        g.fill(x, 3, x + 70, TOP_BAR_H - 3, previewing ? C_BTN_ACT() : (ph ? C_BTN_HOV() : C_BTN()));
        if (previewing) g.fill(x, TOP_BAR_H - 3, x + 70, TOP_BAR_H - 2, C_GREEN());
        g.drawString(font, previewing ? "\u23F9 Stop" : "\u25BA Preview",
                x + 5, (TOP_BAR_H - 8) / 2, previewing ? C_GREEN() : C_DIM(), false);
        if (ph) pendingTooltip = previewing ? "Stop preview playback" :
                "Play a live preview stepping through all steps in order";
        btns.add(new Btn(x, 3, 70, TOP_BAR_H - 6, this::togglePreview));
        x += 74;

        // Camera tab
        int camTabW = 76;
        boolean camHov = isOver(mx, my, x, 3, camTabW, TOP_BAR_H - 6);
        g.fill(x, 3, x + camTabW, TOP_BAR_H - 3,
                showCameraPanel ? C_BTN_ACT() : (camHov ? C_BTN_HOV() : C_BTN()));
        if (showCameraPanel) g.fill(x, TOP_BAR_H - 3, x + camTabW, TOP_BAR_H - 2, C_ACCENT());
        g.drawString(font, "\uD83C\uDFA5 Camera", x + 5, (TOP_BAR_H - 8) / 2,
                showCameraPanel ? C_ACCENT() : C_DIM(), false);
        if (camHov) pendingTooltip = "Set per-step camera overrides (yaw, pitch, zoom, easing)";
        btns.add(new Btn(x, 3, camTabW, TOP_BAR_H - 6, () -> showCameraPanel = !showCameraPanel));
        x += camTabW + 4;

        // Start Cam tab
        boolean hasStartCam = data.getStartCamera() != null;
        int scTabW = 86;
        boolean scHov = isOver(mx, my, x, 3, scTabW, TOP_BAR_H - 6);
        g.fill(x, 3, x + scTabW, TOP_BAR_H - 3,
                showStartCamPanel ? C_BTN_ACT() : (scHov ? C_BTN_HOV() : C_BTN()));
        if (hasStartCam) g.fill(x, TOP_BAR_H - 3, x + scTabW, TOP_BAR_H - 2, C_ACCENT());
        g.drawString(font, "\u2299 Start Cam", x + 5, (TOP_BAR_H - 8) / 2,
                hasStartCam ? C_ACCENT() : C_DIM(), false);
        if (scHov)
            pendingTooltip = "Set the initial camera position when this machine is first opened (yaw, pitch, zoom, target offset)";
        btns.add(new Btn(x, 3, scTabW, TOP_BAR_H - 6, () -> showStartCamPanel = !showStartCamPanel));
        x += scTabW + 4;

        // Right side
        int rx = this.width - 4;
        rx = topBtn(g, mx, my, rx, "\u2715 Back", C_BTN(), "Close the editor (warns if unsaved)", this::onClose);
        rx = topBtn(g, mx, my, rx, "\uD83D\uDCBE Save", C_GREEN(), "Save script to disk and hot-reload in-game",
                this::save);
        if (dirty) {
            String dot = "\u25CF unsaved";
            rx -= font.width(dot) + 10;
            g.drawString(font, dot, rx, (TOP_BAR_H - 8) / 2, C_WARN(), false);
        }
        renderTopBarBadge(g, mx, my, rx);
    }

    /** Override in subclasses to draw a mod badge on the right side of the top bar. */
    protected void renderTopBarBadge(GuiGraphics g, int mx, int my, int rightEdge) {}

    private int modeBtn(GuiGraphics g, int mx, int my, int x, Mode m, String label) {
        int w = font.width(label) + 12;
        boolean act = (mode == m);
        boolean hov = isOver(mx, my, x, 3, w, TOP_BAR_H - 6);
        if (hov) hoveredModeBtnThisFrame = m;
        g.fill(x, 3, x + w, TOP_BAR_H - 3, act ? C_BTN_ACT() : (hov ? C_BTN_HOV() : C_BTN()));
        if (act) g.fill(x, TOP_BAR_H - 3, x + w, TOP_BAR_H - 2, C_ACCENT());
        g.drawString(font, label, x + 6, (TOP_BAR_H - 8) / 2, act ? C_ACCENT() : C_TEXT(), false);
        btns.add(new Btn(x, 3, w, TOP_BAR_H - 6, () -> setMode(act ? null : m)));
        return x + w + 4;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Layer slider
    // ─────────────────────────────────────────────────────────────────────────

    private int localMinY() {
        return cachedLocalMinY;
    }

    private int localMaxY() {
        return cachedLocalMaxY;
    }

    /** Clamps s.layer / s.layerMin / s.layerMax to local Y bounds in-place. */
    private void clampLayerValues(PhantasiaScriptData.StepData s) {
        if (pattern == null) return;
        int minY = localMinY(), maxY = localMaxY();
        if ("layer".equals(s.show)) {
            if (s.layer < minY || s.layer > maxY)
                s.layer = (minY + maxY) / 2;
        } else if ("layers".equals(s.show)) {
            // layerMax==0 with minY==0 is ambiguous — treat as uninitialised when both are 0
            boolean uninitialised = s.layerMin == 0 && s.layerMax == 0 && maxY > 0;
            if (uninitialised || s.layerMin < minY || s.layerMin > maxY) s.layerMin = minY;
            if (uninitialised || s.layerMax < minY || s.layerMax > maxY) s.layerMax = maxY;
            if (s.layerMin >= s.layerMax) {
                s.layerMin = minY;
                s.layerMax = maxY;
            }
        }
    }

    private void renderLayerSlider(GuiGraphics g, int mx, int my) {
        PhantasiaScriptData.StepData s = step();
        if (this.currentFilterMode != FilterMode.LAYER && this.currentFilterMode != FilterMode.RANGE) return;
        if (pattern == null) return;

        int sliderX = 10, sceneTop = TOP_BAR_H, sceneBottom = this.height - BOTTOM_H;
        int sliderH = sceneBottom - sceneTop - 24, sliderY = sceneTop + 12;

        clampLayerValues(s);

        int minY = localMinY(), maxY = localMaxY();
        int range = Math.max(1, maxY - minY);

        g.drawString(font, Component.translatable("screen.phantasia.script_editor.label_layer_filter").getString(),
                sliderX - 2, sliderY - 20, C_DIM(), false);
        g.drawString(font, Component.translatable("screen.phantasia.script_editor.label_layer_filter2").getString(),
                sliderX - 2, sliderY - 11, C_DIM(), false);
        g.fill(sliderX + 3, sliderY, sliderX + 7, sliderY + sliderH, 0x44FFFFFF);
        g.drawString(font, "Y=" + maxY, sliderX + 16, sliderY - 1, C_DIM(), false);
        g.drawString(font, "Y=" + minY, sliderX + 16, sliderY + sliderH - 1, C_DIM(), false);

        if (this.currentFilterMode == FilterMode.LAYER) {
            float t = 1f - (float) (Mth.clamp(s.layer, minY, maxY) - minY) / range;
            int thumbY = sliderY + (int) (t * sliderH);
            boolean hov = isOver(mx, my, sliderX - 2, thumbY - 7, 18, 14) || draggingLayer;
            g.fill(sliderX - 2, thumbY - 6, sliderX + 16, thumbY + 6, hov ? C_ACCENT() : C_BTN_ACT());
            g.drawString(font, String.valueOf(s.layer), sliderX + 18, thumbY - 4, C_ACCENT(), false);
            if (hov) pendingTooltip = "Drag to change visible layer (Y=" + s.layer + ")";
            for (int y = minY; y <= maxY; y++) {
                float ft = 1f - (float) (y - minY) / range;
                int fy = sliderY + (int) (ft * sliderH);
                g.fill(sliderX + 4, fy, sliderX + 6, fy + 1, 0x33FFFFFF);
            }
        } else { // FilterMode.RANGE
            // Secure values within absolute physical boundaries before calculation
            int currentMin = Mth.clamp(s.layerMin, minY, maxY);
            int currentMax = Mth.clamp(s.layerMax, minY, maxY);

            float tMin = 1f - (float) (currentMin - minY) / range;
            float tMax = 1f - (float) (currentMax - minY) / range;
            int tyMin = sliderY + (int) (tMin * sliderH);
            int tyMax = sliderY + (int) (tMax * sliderH);

            g.fill(sliderX + 2, tyMax, sliderX + 8, tyMin, 0x664FC3F7);
            boolean hovMin = isOver(mx, my, sliderX - 2, tyMin - 5, 18, 12) || (draggingLayer && !draggingLayerMax);
            boolean hovMax = isOver(mx, my, sliderX - 2, tyMax - 5, 18, 12) || (draggingLayer && draggingLayerMax);
            g.fill(sliderX - 2, tyMin - 5, sliderX + 16, tyMin + 5, hovMin ? C_ACCENT() : C_BTN_ACT());
            g.fill(sliderX - 2, tyMax - 5, sliderX + 16, tyMax + 5, hovMax ? C_ACCENT() : C_BTN_ACT());
            g.drawString(font, currentMin + "\u2192" + currentMax, sliderX + 18, (tyMin + tyMax) / 2 - 4, C_ACCENT(),
                    false);
            if (hovMin) pendingTooltip = "Drag to set minimum layer (Y=" + currentMin + ")";
            if (hovMax) pendingTooltip = "Drag to set maximum layer (Y=" + currentMax + ")";

            // Sync local tracking strings safely
            this.cacheRangeMin = String.valueOf(currentMin);
            this.cacheRangeMax = String.valueOf(currentMax);

            // FIXED TYPO: Guard correctly against the input widget reference directly
            if (this.rangeMinBox != null && !this.rangeMinBox.isFocused()) {
                this.rangeMinBox.setValue(this.cacheRangeMin);
            }
            if (this.rangeMaxBox != null && !this.rangeMaxBox.isFocused()) {
                this.rangeMaxBox.setValue(this.cacheRangeMax);
            }
        }
    }

    private boolean startLayerSliderDrag(double mx, double my) {
        PhantasiaScriptData.StepData s = step();
        if (this.currentFilterMode != FilterMode.LAYER && this.currentFilterMode != FilterMode.RANGE) return false;
        if (pattern == null) return false;

        clampLayerValues(s);
        int sliderX = 10, sceneTop = TOP_BAR_H, sceneBottom = this.height - BOTTOM_H;
        int sliderH = sceneBottom - sceneTop - 24, sliderY = sceneTop + 12;
        int minY = localMinY(), maxY = localMaxY();
        int range = Math.max(1, maxY - minY);

        if (this.currentFilterMode == FilterMode.LAYER) {
            float t = 1f - (float) (Mth.clamp(s.layer, minY, maxY) - minY) / range;
            int thumbY = sliderY + (int) (t * sliderH);
            if (isOver(mx, my, sliderX - 2, thumbY - 7, 18, 14)) {
                draggingLayer = true;
                draggingLayerMax = false;
                return true;
            }
        } else { // FilterMode.RANGE
            int currentMin = Mth.clamp(s.layerMin, minY, maxY);
            int currentMax = Mth.clamp(s.layerMax, minY, maxY);

            float tMin = 1f - (float) (currentMin - minY) / range;
            float tMax = 1f - (float) (currentMax - minY) / range;
            int tyMin = sliderY + (int) (tMin * sliderH), tyMax = sliderY + (int) (tMax * sliderH);

            int minHitY = Mth.clamp(tyMin - 7, sliderY, sliderY + sliderH - 1);
            int maxHitY = Mth.clamp(tyMax - 7, sliderY, sliderY + sliderH - 1);

            if (isOver(mx, my, sliderX - 4, minHitY, 22, 14)) {
                draggingLayer = true;
                draggingLayerMax = false;
                return true;
            }
            if (isOver(mx, my, sliderX - 4, maxHitY, 22, 14)) {
                draggingLayer = true;
                draggingLayerMax = true;
                return true;
            }
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step row — two clean rows, camera controls live in the camera panel
    // ─────────────────────────────────────────────────────────────────────────
    private void renderStepRow(GuiGraphics g, int mx, int my) {
        int rowY = this.height - BOTTOM_H;
        g.fill(0, rowY, this.width, rowY + STEP_ROW_H, C_BAR());
        g.fill(0, rowY, this.width, rowY + 1, C_ACCENT());

        PhantasiaScriptData.StepData s = step();

        // ── ROW 1: step nav · tick · caption · running · fake recipe ──────────
        int y1 = rowY + 4;
        int x = 8;

        // Step counter + nav
        g.drawString(font, Component.translatable("screen.phantasia.script_editor.label_step").getString(), x, y1 - 2,
                0xFF334455, false);
        String stepLbl = (selectedStep + 1) + "/" + data.getSteps().size();
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

        // Tick field
        g.drawString(font, Component.translatable("screen.phantasia.script_editor.label_tick").getString(), x, y1 + 3,
                C_DIM(), false);
        x += font.width(Component.translatable("screen.phantasia.script_editor.label_tick").getString()) + 3;
        placeBox(tickBox, x, y1, 38, 13);
        if (isOver(mx, my, x, y1, 38, 13)) pendingTooltip = "Tick this step activates at (20 ticks = 1 second)";
        x += 44;

        // Caption — click to open sub-screen
        String capVal = s.caption != null ? s.caption : "";
        int capW = Math.min(220, this.width / 2 - x - 10);
        boolean capHov = isOver(mx, my, x, y1, capW, 13);
        g.fill(x, y1, x + capW, y1 + 13, capHov ? C_BTN_HOV() : C_BTN());
        g.fill(x, y1, x + capW, y1 + 1, 0x33FFFFFF);
        String capDisplay = capVal.isEmpty() ? "\u270E  Caption\u2026" : trunc(capVal, capW - 16);
        g.drawString(font, capDisplay, x + 4, y1 + 3, capVal.isEmpty() ? C_DIM() : C_TEXT(), false);
        if (!capVal.isEmpty() && font.width(capVal) > capW - 16)
            g.drawString(font, "\u2026", x + capW - 8, y1 + 3, C_DIM(), false);
        if (capHov) pendingTooltip = "Click to edit caption text shown to the viewer during this step";
        btns.add(new Btn(x, y1, capW, 13, () -> Minecraft.getInstance().setScreen(
                new PhantasiaTextInputScreen(this, "Step Caption", "What the viewer sees\u2026",
                        s.caption != null ? s.caption : "", 256, v -> {
                            checkpoint();
                            s.caption = v.isBlank() ? null : v;
                            dirty = true;
                        }))));
        x += capW + 8;

        g.fill(x, y1, x + 1, y1 + 14, 0x33FFFFFF);
        x += 8;

        // Running toggle
        boolean wh = isOver(mx, my, x, y1, 82, 14);
        g.fill(x, y1, x + 82, y1 + 14, s.working ? C_BTN_ACT() : (wh ? C_BTN_HOV() : C_BTN()));
        if (s.working) g.fill(x, y1, x + 82, y1 + 1, C_GREEN());
        g.drawString(font, (s.working ? "\u2713" : "\u25CB") + " Running", x + 5, y1 + 3,
                s.working ? C_GREEN() : C_DIM(), false);
        if (wh) pendingTooltip = "Toggle: show this machine as running/active in this step";
        btns.add(new Btn(x, y1, 82, 14, () -> {
            checkpoint();
            s.working = !s.working;
            dirty = true;
            if (parentScene != null) parentScene.applyActiveStateToWorld(s.working);
            if (renderer != null) renderer.requestBake();
        }));
        x += 88;

        if (s.working) {
            g.drawString(font, Component.translatable("screen.phantasia.script_editor.label_recipe").getString(), x,
                    y1 + 3, C_DIM(), false);
            x += font.width(Component.translatable("screen.phantasia.script_editor.label_recipe").getString()) + 4;
            int recBtnW = 180;
            boolean recHov = isOver(mx, my, x, y1, recBtnW, 13);
            g.fill(x, y1, x + recBtnW, y1 + 13, recHov ? C_BTN_HOV() : C_BTN());
            g.fill(x, y1, x + recBtnW, y1 + 1, 0x22FFFFFF);
            String recLbl = s.fakeRecipeId != null ? trunc(s.fakeRecipeId, recBtnW - 16) : "✎  Choose recipe…";
            g.drawString(font, recLbl, x + 4, y1 + 3, s.fakeRecipeId != null ? C_TEXT() : C_DIM(), false);
            if (recHov) pendingTooltip = "Click to search and pick a GregTech recipe ID";
            final PhantasiaScriptData.StepData fStep = s;
            btns.add(new Btn(x, y1, recBtnW, 13, () -> Minecraft.getInstance().setScreen(
                    new net.phoenixvine.phantasia.compat.gtceu.GTRecipePickerScreen(this, fStep.fakeRecipeId,
                            picked -> {
                                checkpoint();
                                fStep.fakeRecipeId = picked;
                                dirty = true;
                            }))));
            x += recBtnW + 4;
        }

        // ── ROW 2: show mode · Parts… · hide controls ──────────────────────────
        int y2 = rowY + STEP_ROW_H / 2 + 5;
        x = 8;
        g.drawString(font, Component.translatable("screen.phantasia.script_editor.label_show").getString(), x, y2 + 2,
                C_DIM(), false);
        x += font.width(Component.translatable("screen.phantasia.script_editor.label_show").getString()) + 4;

        for (int i = 0; i < SHOW_MODES.length; i++) {
            String sm = SHOW_MODES[i];
            String sml = SHOW_LABELS[i];
            int mw = font.width(sml) + 10;

            boolean act = false;
            if (sm.equals("all") && this.currentFilterMode == FilterMode.ALL &&
                    (s.show == null || !s.show.startsWith("pos")))
                act = true;
            else if (sm.equals("layer") && this.currentFilterMode == FilterMode.LAYER) act = true;
            else if (sm.equals("layers") && this.currentFilterMode == FilterMode.RANGE) act = true;

            boolean hov = isOver(mx, my, x, y2, mw, 14);
            g.fill(x, y2, x + mw, y2 + 14, act ? C_BTN_ACT() : (hov ? C_BTN_HOV() : C_BTN()));
            if (act) g.fill(x, y2, x + mw, y2 + 1, C_ACCENT());
            g.drawString(font, sml, x + 5, y2 + 3, act ? C_ACCENT() : C_TEXT(), false);
            if (hov) pendingTooltip = showModeTooltip(sm);

            final String fsm = sm;
            btns.add(new Btn(x, y2, mw, 14, () -> {
                checkpoint();

                if (fsm.equals("all")) {
                    this.currentFilterMode = FilterMode.ALL;
                    s.show = "all";
                } else if (fsm.equals("layer")) {
                    this.currentFilterMode = FilterMode.LAYER;
                    if (pattern != null) {
                        int minY = localMinY(), maxY = localMaxY();
                        if (s.layer < minY || s.layer > maxY) s.layer = (minY + maxY) / 2;
                    }
                } else if (fsm.equals("layers")) {
                    this.currentFilterMode = FilterMode.RANGE;
                    if (pattern != null) {
                        int minY = localMinY(), maxY = localMaxY();
                        if (s.layerMin == 0 && s.layerMax == 0 && maxY > 0) {
                            s.layerMin = minY;
                            s.layerMax = maxY;
                        }
                        if (s.layerMin < minY || s.layerMin > maxY) s.layerMin = minY;
                        if (s.layerMax < minY || s.layerMax > maxY) s.layerMax = maxY;
                        if (s.layerMin >= s.layerMax) {
                            s.layerMin = minY;
                            s.layerMax = maxY;
                        }
                        this.cacheRangeMin = String.valueOf(s.layerMin);
                        this.cacheRangeMax = String.valueOf(s.layerMax);
                    }
                }

                if ("pos".equals(fsm)) {
                    syncSelectedFromStep();
                    setMode(Mode.SELECT);
                } else if (mode == Mode.SELECT && !"pos".equals(fsm)) setMode(null);

                saveFilterStateToStep();
                buildInputWidgets();
                rebuildVisibility();
                dirty = true;
            }));
            x += mw + 3;

            if (act) {
                if ("layer".equals(sm))
                    g.drawString(font, " Y=" + s.layer, x, y2 + 2, C_ACCENT(), false);
                else if ("layers".equals(sm))
                    g.drawString(font, " " + this.cacheRangeMin + "\u2192" + this.cacheRangeMax, x, y2 + 2, C_ACCENT(),
                            false);
            }
        }

        boolean partsSel = (this.currentFilterMode == FilterMode.PARTS);
        int partsW = font.width("Parts\u2026") + 10;
        boolean partsHov = isOver(mx, my, x, y2, partsW, 14);
        g.fill(x, y2, x + partsW, y2 + 14, partsSel ? C_BTN_ACT() : (partsHov ? C_BTN_HOV() : C_BTN()));
        if (partsSel) {
            g.fill(x, y2, x + partsW, y2 + 1, C_ACCENT());
            String suffix = this.cachePartsExpr.isEmpty() ? " (All)" : " (" + trunc(this.cachePartsExpr, 15) + ")";
            g.drawString(font, suffix, x + partsW + 2, y2 + 3, C_DIM(), false);
        }
        g.drawString(font, "Parts\u2026", x + 5, y2 + 3, partsSel ? C_ACCENT() : C_TEXT(), false);
        if (partsHov)
            pendingTooltip = "Filter by machine part type \u2014 hatch, bus, controller, functional, and more";

        btns.add(new Btn(x, y2, partsW, 14, () -> {
            checkpoint();
            this.currentFilterMode = FilterMode.PARTS;
            saveFilterStateToStep();
            buildInputWidgets();
            rebuildVisibility();
            showPartsModal = true;
        }));
        x += partsW + 8;

        // Items… button
        int itemsBtnW = font.width("Items\u2026") + 10;
        boolean itemsHov = isOver(mx, my, x, y2, itemsBtnW, 14);
        boolean itemsActive = !s.items.isEmpty();
        g.fill(x, y2, x + itemsBtnW, y2 + 14, itemsActive ? C_BTN_ACT() : (itemsHov ? C_BTN_HOV() : C_BTN()));
        if (itemsActive) g.fill(x, y2, x + itemsBtnW, y2 + 1, C_ACCENT());
        g.drawString(font, "Items\u2026", x + 5, y2 + 3, itemsActive ? C_ACCENT() : C_TEXT(), false);
        if (itemsActive) {
            String badge = String.valueOf(s.items.size());
            int bx2 = x + itemsBtnW - font.width(badge) - 4;
            g.drawString(font, badge, bx2, y2 + 3, C_ACCENT(), false);
        }
        if (itemsHov) pendingTooltip = "Edit item conditions shown alongside the 3D scene during this step";
        btns.add(new Btn(x, y2, itemsBtnW, 14, () -> Minecraft.getInstance().setScreen(
                createItemEditorScreen(selectedStep))));
        x += itemsBtnW + 3;

        // Expandable shape stepper — for expandable multiblocks (data flag or auto-detected from shape variants)
        boolean isExpandable = data.isExpandable() || (parentScene != null && parentScene.computeHasRealSizeVariants());
        if (isExpandable) {
            int maxShapes = (parentScene != null && parentScene.availableShapes != null) ?
                    parentScene.availableShapes.size() : 0;
            // lc == -1 means "no override"; treat as shape 0 for display
            int curShape = s.layerCount > 0 ? s.layerCount : 1;

            int arrowW = 12;
            String stepLabel = "Size " + curShape + (maxShapes > 0 ? "/" + maxShapes : "");
            int labelW = font.width(stepLabel);
            int totalW = arrowW + 4 + labelW + 4 + arrowW;

            boolean prevHov = isOver(mx, my, x, y2, arrowW, 14);
            boolean nextHov = isOver(mx, my, x + arrowW + 4 + labelW + 4, y2, arrowW, 14);
            boolean anyActive = s.layerCount > 0;

            g.fill(x, y2, x + totalW, y2 + 14, anyActive ? C_BTN_ACT() : C_BTN());
            if (anyActive) g.fill(x, y2, x + totalW, y2 + 1, C_ACCENT());

            g.fill(x, y2, x + arrowW, y2 + 14, prevHov ? C_BTN_HOV() : 0x22FFFFFF);
            g.drawString(font, "◄", x + 2, y2 + 3, prevHov ? C_TEXT() : C_DIM(), false);

            g.drawString(font, stepLabel, x + arrowW + 4, y2 + 3, anyActive ? C_ACCENT() : C_DIM(), false);

            g.fill(x + arrowW + 4 + labelW + 4, y2, x + totalW, y2 + 14, nextHov ? C_BTN_HOV() : 0x22FFFFFF);
            g.drawString(font, "►", x + arrowW + 4 + labelW + 4 + 2, y2 + 3, nextHov ? C_TEXT() : C_DIM(), false);

            if (prevHov || nextHov)
                pendingTooltip = "Set the expandable shape variant for this step (Size 1 = smallest)";

            final int fMaxShapes = maxShapes;
            btns.add(new Btn(x, y2, arrowW, 14, () -> {
                checkpoint();
                int next = curShape - 1;
                s.layerCount = (next <= 0) ? -1 : next;
                if (layerCountBox != null) layerCountBox.setValue(s.layerCount > 0 ? String.valueOf(s.layerCount) : "");
                dirty = true;
                rebuildVisibility();
            }));
            btns.add(new Btn(x + arrowW + 4 + labelW + 4, y2, arrowW, 14, () -> {
                checkpoint();
                int next = curShape + 1;
                s.layerCount = (fMaxShapes > 0 && next > fMaxShapes) ? fMaxShapes : next;
                if (layerCountBox != null) layerCountBox.setValue(s.layerCount > 0 ? String.valueOf(s.layerCount) : "");
                dirty = true;
                rebuildVisibility();
            }));
            x += totalW + 3;
        }

        // ── HIDE CONTROLS (Right-aligned layout anchors) ───────────────────
        int rx2 = this.width - 8;

        // 1. HidePos Button (Furthest right)
        int hideCount = s.hidePositions.size();
        String hideBtnLabel = hideCount == 0 ? "HidePos\u2026" : "HidePos (" + hideCount + ")";
        int hideBtnW = font.width(hideBtnLabel) + 10;
        rx2 -= hideBtnW;
        boolean hideHov = isOver(mx, my, rx2, y2, hideBtnW, 14);
        g.fill(rx2, y2, rx2 + hideBtnW, y2 + 14, hideCount > 0 ? C_BTN_ACT() : (hideHov ? C_BTN_HOV() : C_BTN()));
        if (hideCount > 0) g.fill(rx2, y2, rx2 + hideBtnW, y2 + 1, C_ACCENT());
        g.drawString(font, hideBtnLabel, rx2 + 5, y2 + 3, hideCount > 0 ? C_ACCENT() : C_TEXT(), false);
        if (hideHov) pendingTooltip = "Edit local positions excluded from this step (" + hideCount + " hidden)";
        final int fStep = selectedStep;
        btns.add(new Btn(rx2, y2, hideBtnW, 14, () -> Minecraft.getInstance().setScreen(
                new PhantasiaHidePosEditorScreen(this))));

        // 2. HideY Edit Box and Label (Shifted left to provide 45px box space)
        rx2 -= 8; // Spacer between HidePos button and HideY setup
        int boxW = 45; // Wider box layout to accommodate lists like "1,2" cleanly
        int labelW = font.width(Component.translatable("screen.phantasia.script_editor.label_hideY").getString());
        int combinedW = labelW + 3 + boxW;

        int hideYLabelX = rx2 - combinedW;
        g.drawString(font, Component.translatable("screen.phantasia.script_editor.label_hideY").getString(),
                hideYLabelX, y2 + 2, C_DIM(), false);

        int hideYBoxX = hideYLabelX + labelW + 3;
        placeBox(hideLayerBox, hideYBoxX, y2, boxW, 13);

        if (isOver(mx, my, hideYLabelX, y2, combinedW, 13)) {
            pendingTooltip = "Hide layers by entering comma-separated Y values. (e.g. 1,2). Blank = none.";
        }
    }

    private static String showModeTooltip(String mode) {
        return switch (mode) {
            case "all" -> "Show every block in the structure";
            case "layer" -> "Show only blocks on a single Y layer (drag the side slider to change it)";
            case "layers" -> "Show only blocks within a Y range (drag the side slider handles)";
            default -> mode;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Camera panel (floating overlay above step row)
    // ─────────────────────────────────────────────────────────────────────────

    // ── Editor item panel ─────────────────────────────────────────────────────

    private static final int EIP_W = 186;
    private static final int EIP_ROW = 38;
    private static final int EIP_ICON = 28;
    private static final int EIP_HOV = 36;

    /**
     * Shows the GuideME-style item panel while authoring, so devs can see how
     * the current step's items will look to viewers. Tracks animate using the
     * editor's own {@code previewTick}. Items are clickable to preview the
     * microscene popup.
     */
    private void renderEditorItemPanel(GuiGraphics g, int mx, int my) {
        PhantasiaScriptData.StepData s = step();
        if (!s.showItems || s.items.isEmpty()) return;

        int panelX = this.width - EIP_W - 4;
        int panelY = TOP_BAR_H + 4;
        int panelH = s.items.size() * EIP_ROW + 8;

        g.fill(panelX, panelY, panelX + EIP_W, panelY + panelH, 0xDD070712);
        g.fill(panelX, panelY, panelX + EIP_W, panelY + 1, C_ACCENT());
        g.fill(panelX, panelY, panelX + 1, panelY + panelH, 0x554FC3F7);

        int ry = panelY + 4;
        for (PhantasiaSceneData.ItemConditionData it : s.items) {
            boolean hov = isOver(mx, my, panelX + 2, ry, EIP_W - 4, EIP_ROW - 1);
            int ac = it.accentColor();

            g.fill(panelX + 2, ry, panelX + EIP_W - 2, ry + EIP_ROW - 1,
                    hov ? 0xBB0D1A2D : 0x22000000);
            g.fill(panelX + 2, ry, panelX + 3, ry + EIP_ROW - 1, ac);

            float[] anim = editorItemTrackOffset(it);
            int alpha = Math.max(0, Math.min(255, (int) (anim[2] * 255)));
            int iconSz = hov ? EIP_HOV : EIP_ICON;
            int iconX = panelX + 5 + Math.round(anim[0]);
            int iconCY = ry + (EIP_ROW - 1) / 2;
            int iconY = iconCY - iconSz / 2 + Math.round(anim[1]);

            net.minecraft.world.item.Item mcItem = eipResolveItem(it.item);
            if (mcItem != null) {
                net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(mcItem, it.count);
                int renderY = iconCY - 8 + Math.round(anim[1]);
                if (alpha < 255) com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, alpha / 255f);
                g.renderItem(stack, iconX, renderY);
                g.renderItemDecorations(font, stack, iconX, renderY);
                if (alpha < 255) com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            } else {
                g.fill(iconX + 2, iconY + 2, iconX + EIP_HOV - 2, iconY + EIP_HOV - 2, 0x44FF0000);
                g.drawCenteredString(font, "?", iconX + EIP_HOV / 2, iconCY - 4, 0xFFFF5252);
            }

            int tx = panelX + 5 + EIP_HOV + 4;
            int tw = EIP_W - (tx - panelX) - 5;
            int ty = ry + 3;

            g.drawString(font, eipTrunc(it.displayLabel(), tw), tx, ty,
                    hov ? 0xFFFFFFFF : C_TEXT(), false);
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

            if (hov) {
                boolean hasScene = it.microsceneId != null && !it.microsceneId.isBlank();
                String hint = hasScene ? "\u25BA Scene" : "\u25BA More";
                g.drawString(font, hint, panelX + EIP_W - font.width(hint) - 5,
                        ry + EIP_ROW - 11, 0xFF80DEEA, false);
            }

            final PhantasiaSceneData.ItemConditionData fIt = it;
            btns.add(new Btn(panelX + 2, ry, EIP_W - 4, EIP_ROW - 1, () -> {
                PhantasiaSceneData microscene = fIt.microsceneId != null ? PhantasiaScenes.get(fIt.microsceneId) : null;
                Minecraft.getInstance().setScreen(
                        new PhantasiaItemMicrosceneScreen(
                                PhantasiaScriptEditorScreen.this, fIt, microscene));
            }));

            ry += EIP_ROW;
        }
    }

    private float[] editorItemTrackOffset(PhantasiaSceneData.ItemConditionData it) {
        String track = it.track == null ? "none" : it.track.toLowerCase(java.util.Locale.ROOT);
        if ("none".equals(track)) return new float[] { 0, 0, 1f };
        int dur = Math.max(1, it.trackDurationTicks);
        float t = (itemAnimTick % dur) / (float) dur;
        return switch (track) {
            case "left" -> {
                float fade = 1f - Math.abs(t - 0.5f) * 2f;
                yield new float[] { t * 40f - 20f, 0, Math.max(0, fade) };
            }
            case "right" -> {
                float fade = 1f - Math.abs(t - 0.5f) * 2f;
                yield new float[] { 20f - t * 40f, 0, Math.max(0, fade) };
            }
            case "up" -> new float[] { 0, -(t * 24f), Math.max(0, 1f - t) };
            case "down" -> new float[] { 0, (t * 24f), Math.max(0, 1f - t) };
            case "pulse" -> new float[] { 0, (float) Math.sin(t * 2 * Math.PI) * 3f, 1f };
            default -> new float[] { 0, 0, 1f };
        };
    }

    private static net.minecraft.world.item.Item eipResolveItem(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            var rl = id.contains(":") ? new net.minecraft.resources.ResourceLocation(id) :
                    new net.minecraft.resources.ResourceLocation("minecraft", id);
            var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
            return (item == null || item == net.minecraft.world.item.Items.AIR) ? null : item;
        } catch (Exception e) {
            return null;
        }
    }

    private String eipTrunc(String s, int maxPx) {
        if (s == null) return "";
        while (font.width(s) > maxPx && s.length() > 2) s = s.substring(0, s.length() - 2) + "\u2026";
        return s;
    }

    private boolean isCamPanelHit(int mx, int my) {
        int panelW = Math.min(500, this.width - 16);
        int panelH = CAM_PANEL_H;
        int panelX = 6;
        int panelY = this.height - BOTTOM_H - panelH - 4;
        return isOver(mx, my, panelX - 2, panelY - 2, panelW + 4, panelH + 4);
    }

    private void renderCameraPanel(GuiGraphics g, int mx, int my) {
        int panelW = Math.min(500, this.width - 16);
        int panelH = CAM_PANEL_H;
        int panelX = 6;
        int panelY = this.height - BOTTOM_H - panelH - 4;

        g.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xDD070712);
        g.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY - 1, C_ACCENT());
        g.drawString(font, "\uD83C\uDFA5  Camera \u2014 step " + (selectedStep + 1),
                panelX + 4, panelY + 3, C_ACCENT(), false);

        PhantasiaScriptData.StepData s = step();
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
            g.drawString(font, Component.translatable("screen.phantasia.script_editor.no_cam_override").getString(),
                    x, r1Y + 3, C_DIM(), false);
        }

        // Row 2: Zoom + lerp type + lerp ticks
        int r2Y = panelY + 32;
        x = panelX + 4;

        if (hasCam) {
            g.drawString(font, Component.translatable("screen.phantasia.script_editor.label_zoom").getString(), x,
                    r2Y + 2, C_DIM(), false);
            x += font.width(Component.translatable("screen.phantasia.script_editor.label_zoom").getString()) + 3;
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
            if (lth)
                pendingTooltip = "Easing type \u2014 click to cycle: " + lerpTypeList();
            btns.add(new Btn(x, r2Y, ltW, 13, () -> {
                checkpoint();
                LerpType[] vals = LerpType.values();
                s.camera.lerpType = vals[(lt.ordinal() + 1) % vals.length].name();
                dirty = true;
            }));
            x += ltW + 6;

            if (lt != LerpType.SNAP) {
                g.drawString(font, Component.translatable("screen.phantasia.script_editor.label_over").getString(), x,
                        r2Y + 2, C_DIM(), false);
                x += font.width(Component.translatable("screen.phantasia.script_editor.label_over").getString()) + 3;
                placeBox(lerpTicksBox, x, r2Y, 34, 12);
                if (isOver(mx, my, x, r2Y, 34, 12))
                    pendingTooltip = "Camera transition duration in ticks (20 ticks = 1 second)";
                x += 38;
                g.drawString(font, Component.translatable("screen.phantasia.script_editor.label_ticks").getString(), x,
                        r2Y + 2, C_DIM(), false);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parts modal
    // ─────────────────────────────────────────────────────────────────────────

    /** Computes the height the parts modal needs based on chip wrapping. */
    private int partsModalHeight() {
        int mw = 380;
        int mdx = (this.width - mw) / 2;
        int rowMax = mdx + mw - 10;
        int cx = mdx + 10, cy = 0; // relative y
        for (String[] preset : PARTS_PRESETS) {
            int cw = font.width(preset[1]) + 10;
            if (cx + cw > rowMax) {
                cx = mdx + 10;
                cy += 18;
            }
            cx += cw + 4;
        }
        // header(22) + label(10) + chips(cy+18) + gap(4) + expr section(28) + hint(12) + clear(20) + padding(8)
        return 22 + 10 + (cy + 18) + 4 + 28 + 12 + 20 + 8;
    }

    private void renderPartsModal(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, this.width, this.height, 0x88000000);

        int mw = 380, mh = partsModalHeight();
        int mdx = (this.width - mw) / 2;
        int mdy = Math.max(TOP_BAR_H + 4, (this.height - mh) / 2);

        g.fill(mdx, mdy, mdx + mw, mdy + mh, C_PANEL());
        g.fill(mdx, mdy, mdx + mw, mdy + 1, C_ACCENT());
        g.fill(mdx, mdy, mdx + 1, mdy + mh, 0x44FFFFFF);
        g.fill(mdx + mw - 1, mdy, mdx + mw, mdy + mh, 0x44FFFFFF);
        g.fill(mdx, mdy + mh - 1, mdx + mw, mdy + mh, 0x44FFFFFF);

        g.drawString(font, Component.translatable("screen.phantasia.script_editor.label_parts_filter").getString(),
                mdx + 10, mdy + 7, C_ACCENT(), false);

        // Close button
        boolean xHov = isOver(mx, my, mdx + mw - 18, mdy + 4, 14, 14);
        g.fill(mdx + mw - 18, mdy + 4, mdx + mw - 4, mdy + 18, xHov ? C_BTN_HOV() : C_BTN());
        g.drawString(font, "\u2715", mdx + mw - 14, mdy + 7, xHov ? C_RED() : C_DIM(), false);
        if (xHov) pendingTooltip = "Close";
        btns.add(new Btn(mdx + mw - 18, mdy + 4, 14, 14, () -> showPartsModal = false));

        PhantasiaScriptData.StepData s = step();

        // ── Quick-select chips ────────────────────────────────────────────────
        g.drawString(font, Component.translatable("screen.phantasia.script_editor.label_quick_select").getString(),
                mdx + 10, mdy + 22, C_DIM(), false);
        int cx = mdx + 10, cy = mdy + 32;
        int rowMax = mdx + mw - 10;

        for (String[] preset : PARTS_PRESETS) {
            String showValue = preset[0];
            String label = preset[1];
            String tooltip = preset[2];

            int cw = font.width(label) + 10;
            if (cx + cw > rowMax) {
                cx = mdx + 10;
                cy += 18;
            }

            boolean btnHov = isOver(mx, my, cx, cy, cw, 14);
            g.fill(cx, cy, cx + cw, cy + 14, btnHov ? C_BTN_HOV() : C_BTN());
            g.drawCenteredString(font, label, cx + cw / 2, cy + 3, btnHov ? C_TEXT() : C_DIM());

            if (btnHov) {
                pendingTooltip = tooltip;
            }

            // Add the updated button click handler inside your PARTS_PRESETS loop
            btns.add(new Btn(cx, cy, cw, 14, () -> {
                checkpoint();

                if (showValue.startsWith("parts:")) {
                    String token = showValue.substring(6); // Extracts the token string
                    String currentText = partsExprBox.getValue().trim();

                    if (token.equals("@block(...)")) {
                        // Smart Insertion for the raw block tool
                        if (currentText.isEmpty()) {
                            partsExprBox.setValue("@block()");
                        } else {
                            partsExprBox.setValue(currentText + " & @block()");
                        }
                        // Move the cursor back by 1 character so it sits perfectly inside the ()
                        partsExprBox.setCursorPosition(partsExprBox.getValue().length() - 1);
                    } else {
                        // Standard logic chaining for static capability/type macros
                        if (currentText.isEmpty()) {
                            partsExprBox.setValue(token);
                        } else if (!currentText.contains(token)) {
                            String combined = currentText + " & " + token;
                            partsExprBox.setValue(combined);
                        }
                    }

                    // CRITICAL: Catch the updated value and lock it into your screen's persistent cache!
                    this.persistentExpr = partsExprBox.getValue();
                    s.show = "parts:" + this.persistentExpr;
                }

                dirty = true;
                rebuildVisibility();
            }));

            cx += cw + 4;
        }
        cy += 22;

        // ── Custom expression input ───────────────────────────────────────────
        g.fill(mdx + 8, cy - 1, mdx + mw - 8, cy + 27, 0x22FFFFFF);
        g.drawString(font, Component.translatable("screen.phantasia.script_editor.label_custom_expr").getString(),
                mdx + 12, cy + 3, C_DIM(), false);
        int exprX = mdx + 12 +
                font.width(Component.translatable("screen.phantasia.script_editor.label_custom_expr").getString()) + 6;
        int exprW = mdx + mw - 8 - exprX - 6;
        placeBox(partsExprBox, exprX, cy, exprW, 13);
        // inside renderPartsModal()
        if (isOver(mx, my, exprX, cy, exprW, 13))
            pendingTooltip = "Filter syntax: () brackets, | (OR), & (AND), ! (NOT), @ (Ability) — e.g. (@hatch | high_temperature) & !muffler";
        cy += 17;
        g.drawString(font,
                "| for OR   •   & for AND   •   ! to negate   •   @ for PartAbility",
                mdx + 12, cy, C_DIM(), false);
        cy += 12;

        // ── Clear / show all ─────────────────────────────────────────────────
        boolean clrHov = isOver(mx, my, mdx + 8, cy + 2, mw - 16, 14);
        g.fill(mdx + 8, cy + 2, mdx + mw - 8, cy + 16, clrHov ? C_BTN_HOV() : C_BTN());
        g.drawString(font, "\u2715  Clear \u2014 show all blocks", mdx + 14, cy + 5, clrHov ? C_ACCENT() : C_DIM(),
                false);
        if (clrHov) pendingTooltip = "Reset to showing every block in this step";
        btns.add(new Btn(mdx + 8, cy + 2, mw - 16, 14, () -> {
            checkpoint();
            s.show = "all";
            if (partsExprBox != null) partsExprBox.setValue("");
            dirty = true;
            rebuildVisibility();
            showPartsModal = false;
        }));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Timeline
    // ─────────────────────────────────────────────────────────────────────────

    private void renderTimeline(GuiGraphics g, int mx, int my) {
        int tlY = this.height - TIMELINE_H;
        int margin = 30, trackW = this.width - margin * 2;
        int total = computeTotalTicks();
        int midY = tlY + TIMELINE_H / 2;

        g.fill(0, tlY, this.width, this.height, C_PANEL());
        g.fill(0, tlY, this.width, tlY + 1, 0x33FFFFFF);
        g.fill(margin, midY - 1, margin + trackW, midY + 1, 0xFF1A2C3C);
        g.fill(margin - 1, midY - 3, margin, midY + 3, 0xFF3A506A);
        g.fill(margin + trackW, midY - 3, margin + trackW + 1, midY + 3, 0xFF3A506A);

        if (data.getSteps().isEmpty()) return;

        // Ghost + click-to-add
        timelineGhostX = -1;
        timelineGhostTick = -1;
        boolean mouseOnTrack = isOver(mx, my, margin, tlY, trackW, TIMELINE_H);
        if (mouseOnTrack && draggingTimelineDot < 0) {
            boolean nearDot = false;
            for (PhantasiaScriptData.StepData s : data.getSteps()) {
                float t = total > 0 ? (float) s.tick / total : 0f;
                if (Math.abs(mx - (margin + (int) (t * trackW))) < 14) {
                    nearDot = true;
                    break;
                }
            }
            if (!nearDot) {
                timelineGhostX = mx;
                timelineGhostTick = total > 0 ? Math.round((float) (mx - margin) / trackW * total) : 0;
                g.fill(mx, tlY + 2, mx + 1, tlY + TIMELINE_H - 2, 0x554FC3F7);
                int ghW = font.width("+") + 6;
                g.fill(mx - ghW / 2, midY - 7, mx + ghW / 2, midY + 7, 0x884FC3F7);
                g.drawCenteredString(font, "+", mx, midY - 3, 0xFFFFFFFF);
                g.drawCenteredString(font, "t=" + timelineGhostTick, mx, midY + 8, 0x664FC3F7);
                pendingTooltip = "Click to add a step at tick " + timelineGhostTick;
            }
        }

        // Preview playhead
        if (previewing && total > 0) {
            int px = margin + (int) ((float) previewTick / total * trackW);
            g.fill(px - 1, tlY + 2, px + 1, tlY + TIMELINE_H - 2, 0xAAFFFFFF);
        }

        // Step dots
        for (int i = 0; i < data.getSteps().size(); i++) {
            PhantasiaScriptData.StepData s = data.getSteps().get(i);
            float t = total > 0 ? (float) s.tick / total : 0f;
            int dotX = margin + (int) (t * trackW);
            boolean sel = (i == selectedStep), hov = isOver(mx, my, dotX - 9, midY - 9, 18, 18);
            boolean drag = (draggingTimelineDot == i);
            int ringCol = sel || drag ? C_ACCENT() : (hov ? 0xFFAADDFF : 0xFF3A506A);
            g.fill(dotX - 7, midY - 7, dotX + 7, midY + 7, ringCol);
            g.fill(dotX - 5, midY - 5, dotX + 5, midY + 5, drag ? 0xFFFFFFFF : (sel ? 0xFF1A3C5C : 0xFF0A1520));
            if (s.camera != null) g.fill(dotX - 3, midY - 7, dotX + 3, midY - 5, C_ACCENT());
            g.drawCenteredString(font, String.valueOf(i + 1), dotX, midY - 3,
                    sel || drag ? C_ACCENT() : (hov ? 0xFFCCEEFF : C_DIM()));
            if (i + 1 < data.getSteps().size()) {
                PhantasiaScriptData.StepData next = data.getSteps().get(i + 1);
                float nt = total > 0 ? (float) next.tick / total : 0f;
                int nextX = margin + (int) (nt * trackW);
                int lineCol = (next.camera != null && LerpType.fromString(next.camera.lerpType) != LerpType.SNAP) ?
                        0xFF1A4060 : 0xFF1E3A52;
                g.fill(dotX + 7, midY, nextX - 7, midY + 1, lineCol);
            }
            String lbl = (sel || drag) ? "#" + (i + 1) + "  t=" + s.tick : "#" + (i + 1);
            int lx = Mth.clamp(dotX - font.width(lbl) / 2, margin, margin + trackW - font.width(lbl));
            g.drawString(font, lbl, lx, midY + 9, sel || drag ? C_ACCENT() : C_DIM(), false);
            if (hov && !sel && !drag)
                g.drawCenteredString(font, "\u2715", dotX, midY - 16, 0x88FF5252);
            if (hov && !drag) pendingTooltip = "Step " + (i + 1) + " \u2014 tick " + s.tick +
                    (s.camera != null ? " \uD83C\uDFA5" : "") + (s.caption != null ? ": " + s.caption : "");
        }

        // Tick ruler
        if (total > 0) {
            for (int tick = 0; tick <= total; tick += 60) {
                int rx = margin + (int) ((float) tick / total * trackW);
                g.fill(rx, midY + 1, rx + 1, midY + 4, 0x33FFFFFF);
            }
        }

        g.drawString(font, "\u25C4 \u25BA", margin - 24, midY - 4, C_DIM(), false);

        // Duration box
        int durLabelW = font.width(Component.translatable("screen.phantasia.script_editor.label_total").getString()) +
                4;
        int durX = this.width - 4 - 46 - durLabelW;
        g.drawString(font, Component.translatable("screen.phantasia.script_editor.label_total").getString(), durX,
                midY - 4, C_DIM(), false);
        if (isOver(mx, my, durX, tlY, durLabelW + 50, TIMELINE_H))
            pendingTooltip = "Override total script length in ticks. Blank = auto (last step tick + 60).";
        placeBox(scriptDurationBox, durX + durLabelW, tlY + 5, 46, 12);
        scriptDurationBox.visible = true;
        scriptDurationBox.active = true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Start-camera panel
    // ─────────────────────────────────────────────────────────────────────────

    private void renderStartCamPanel(GuiGraphics g, int mx, int my) {
        int pw = 370, ph = 60, px = 6, py = TOP_BAR_H + 2;
        g.fill(px, py, px + pw, py + ph, C_PANEL());
        g.fill(px, py, px + pw, py + 1, C_ACCENT());
        g.fill(px, py, px + 1, py + ph, 0x33FFFFFF);
        g.fill(px + pw - 1, py, px + pw, py + ph, 0x33FFFFFF);

        PhantasiaScriptData.StartCameraData sc = data.getStartCamera();
        boolean hasSC = sc != null;
        int row1 = py + 6;
        int x = px + 6;
        g.drawString(font, Component.translatable("screen.phantasia.script_editor.label_start_cam").getString(), x,
                row1 - 1, C_ACCENT(), false);
        x += font.width(Component.translatable("screen.phantasia.script_editor.label_start_cam").getString()) + 10;
        x = scField(g, mx, my, x, row1, "Yaw", scYawBox, "Yaw of initial camera (degrees). Blank = face machine.",
                hasSC);
        x = scField(g, mx, my, x, row1, "Pitch", scPitchBox,
                "Pitch of initial camera (degrees). Blank = default (\u221235\u00B0).", hasSC);
        x = scField(g, mx, my, x, row1, "Zoom", scZoomBox, "Initial zoom distance in world units. Blank = auto-fit.",
                hasSC);
        int row2 = py + 26;
        x = px + 6;
        g.drawString(font, Component.translatable("screen.phantasia.script_editor.label_target_offset").getString(), x,
                row2 + 2, C_DIM(), false);
        x += font.width(Component.translatable("screen.phantasia.script_editor.label_target_offset").getString()) + 6;
        x = scField(g, mx, my, x, row2, "X", scOffsetXBox, "Look-at offset X axis (world units).", hasSC);
        x = scField(g, mx, my, x, row2, "Y", scOffsetYBox, "Look-at offset Y axis (positive = up).", hasSC);
        x = scField(g, mx, my, x, row2, "Z", scOffsetZBox, "Look-at offset Z axis.", hasSC);
        x += 8;
        btn(g, mx, my, x, row2, 90, 13, "\u2299 Capture View", C_BTN(), this::captureStartCam);
        x += 96;
        if (hasSC) btn(g, mx, my, x, row2, 60, 13, "\u2715 Clear", C_BTN(), this::clearStartCam);
        for (var box : List.of(scYawBox, scPitchBox, scZoomBox, scOffsetXBox, scOffsetYBox, scOffsetZBox)) {
            box.visible = true;
            box.active = true;
        }
    }

    private int scField(GuiGraphics g, int mx, int my, int x, int y,
                        String label, EditBox box, String tooltip, boolean hasSC) {
        int labelW = font.width(label) + 3;
        g.drawString(font, label, x, y + 2, C_DIM(), false);
        placeBox(box, x + labelW, y, 44, 13);
        if (!hasSC) g.fill(x + labelW, y, x + labelW + 44, y + 13, 0x33000000);
        if (isOver(mx, my, x, y, labelW + 44, 13)) pendingTooltip = tooltip;
        return x + labelW + 48;
    }

    private void captureStartCam() {
        checkpoint();
        PhantasiaScriptData.StartCameraData sc = ensureStartCam();
        if (camera != null) {
            sc.yaw = camera.getYaw();
            sc.pitch = camera.getPitch();
            sc.zoom = camera.getZoom();
        }
        populateStartCamBoxes();
        dirty = true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mouse / keyboard
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
        if (showPartsModal) {
            // Check Btn registry first
            for (Btn b : btns) if (b.hit(mx, my)) {
                b.action().run();
                return true;
            }
            // Route clicks to the partsExprBox if it's inside the modal
            if (partsExprBox != null && partsExprBox.visible && mx >= partsExprBox.getX() &&
                    mx < partsExprBox.getX() + partsExprBox.getWidth() && my >= partsExprBox.getY() &&
                    my < partsExprBox.getY() + partsExprBox.getHeight()) {
                setFocused(partsExprBox);
                partsExprBox.mouseClicked(mx, my, btn);
                return true;
            }
            // Click inside modal bounds — don't close
            int mw = 380, mh = partsModalHeight();
            int mdx = (this.width - mw) / 2;
            int mdy = Math.max(TOP_BAR_H + 4, (this.height - mh) / 2);
            if (isOver(mx, my, mdx, mdy, mw, mh)) return true;

            // --- FIXED FOR CLEAN STATE RESYNC ---
            // Click outside — lock filter mode to PARTS, save, close, and refresh the screen scene
            this.currentFilterMode = FilterMode.PARTS;
            saveFilterStateToStep();
            showPartsModal = false;
            rebuildVisibility();
            return true;
            // ------------------------------------
        }
        for (Btn b : btns) if (b.hit(mx, my)) {
            b.action().run();
            return true;
        }
        for (var child : children()) {
            if (child instanceof EditBox eb && eb.visible && eb.active && mx >= eb.getX() &&
                    mx < eb.getX() + eb.getWidth() && my >= eb.getY() && my < eb.getY() + eb.getHeight()) {
                setFocused(eb);
                eb.mouseClicked(mx, my, btn);
                return true;
            }
        }
        if (startLayerSliderDrag(mx, my)) return true;

        int tlY = this.height - TIMELINE_H, midY = tlY + TIMELINE_H / 2;
        int margin = 30, trackW = this.width - margin * 2, total = computeTotalTicks();
        boolean onTimeline = isOver(mx, my, 0, tlY, this.width, TIMELINE_H);
        if (onTimeline) {
            if (btn == 1) {
                for (int i = 0; i < data.getSteps().size(); i++) {
                    PhantasiaScriptData.StepData s = data.getSteps().get(i);
                    float t = total > 0 ? (float) s.tick / total : 0f;
                    int dotX = margin + (int) (t * trackW);
                    if (isOver(mx, my, dotX - 9, midY - 9, 18, 18) && data.getSteps().size() > 1) {
                        checkpoint();
                        data.getSteps().remove(i);
                        selectStep(Math.min(selectedStep, data.getSteps().size() - 1));
                        dirty = true;
                        return true;
                    }
                }
            }
            if (btn == 0) {
                if (startTimelineDotDrag(mx, my)) return true;
                boolean nearDot = false;
                for (PhantasiaScriptData.StepData s : data.getSteps()) {
                    float t = total > 0 ? (float) s.tick / total : 0f;
                    if (Math.abs(mx - (margin + (int) (t * trackW))) < 14) {
                        nearDot = true;
                        break;
                    }
                }
                if (!nearDot) {
                    int newTick = total > 0 ? Math.round((float) (mx - margin) / trackW * total) : 0;
                    addStepAtTick(newTick);
                    return true;
                }
            }
            return true;
        }

        int sceneBottom = this.height - BOTTOM_H;
        if (my < TOP_BAR_H || my >= sceneBottom) return false;
        if (mode == Mode.WORLD) return handleWorldItemClick(mx, my, btn);
        if (mode == Mode.SELECT) {
            selectClickPending = true;
            selectClickBtn = btn;
            selectClickMX = mx;
            selectClickMY = my;
            return true;
        }
        if (mode == Mode.ANNOTATE) return handleAnnotateClick(mx, my, btn);
        return false;
    }

    private boolean startTimelineDotDrag(double mx, double my) {
        int tlY = this.height - TIMELINE_H, midY = tlY + TIMELINE_H / 2;
        int margin = 30, trackW = this.width - margin * 2, total = computeTotalTicks();
        for (int i = 0; i < data.getSteps().size(); i++) {
            PhantasiaScriptData.StepData s = data.getSteps().get(i);
            float t = total > 0 ? (float) s.tick / total : 0f;
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

    private boolean handleSelectClick(double mx, double my, int btn) {
        if (hoveredWorldPos == null || pattern == null) return false;
        if (pattern.baseplatePositions.contains(hoveredWorldPos)) return false;
        BlockPos local = pattern.toLocal(hoveredWorldPos);
        if (local == null) return false;
        PhantasiaScriptData.StepData s = step();
        s.show = "pos";
        checkpoint();
        if (btn == 0) {
            if (isInPositionList(local)) {
                s.positions.removeIf(
                        p -> p.length >= 3 && p[0] == local.getX() && p[1] == local.getY() && p[2] == local.getZ());
                selectedWorldPos.remove(hoveredWorldPos);
            } else {
                s.positions.add(new int[] { local.getX(), local.getY(), local.getZ() });
                selectedWorldPos.add(hoveredWorldPos);
            }
        } else if (btn == 1) {
            s.positions.removeIf(
                    p -> p.length >= 3 && p[0] == local.getX() && p[1] == local.getY() && p[2] == local.getZ());
            selectedWorldPos.remove(hoveredWorldPos);
        }
        dirty = true;
        rebuildVisibility();
        return true;
    }

    private boolean handleAnnotateClick(double mx, double my, int btn) {
        if (btn == 1 && hoveredMistakeIndex >= 0) {
            checkpoint();
            data.getMistakes().remove(hoveredMistakeIndex);
            hoveredMistakeIndex = -1;
            dirty = true;
            return true;
        }
        if (btn != 0) return false;
        if (pendingAnnotationLocalPos != null) {
            confirmAnnotation();
            return true;
        }
        if (hoveredWorldPos == null || pattern == null) return false;
        BlockPos local = pattern.toLocal(hoveredWorldPos);
        if (local == null) return false;
        pendingAnnotationLocalPos = local;
        pendingAnnotationLabel = "";
        openAnnotationLabelInput();
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        // Only capture layer adjustments if we actually initiated a thumb drag via mouseClicked
        if (draggingLayer && pattern != null) {
            int sceneTop = TOP_BAR_H, sceneBottom = this.height - BOTTOM_H;
            int sliderH = sceneBottom - sceneTop - 24, sliderY = sceneTop + 12;
            float t = 1f - Mth.clamp((float) (my - sliderY) / sliderH, 0f, 1f);

            int minY = localMinY(), maxY = localMaxY();
            int layer = minY + Math.round(t * (maxY - minY));
            PhantasiaScriptData.StepData s = step();

            int prevMin = s.layerMin;
            int prevMax = s.layerMax;
            int prevLayer = s.layer;

            if (draggingLayerMax) {
                s.layerMax = Mth.clamp(layer, s.layerMin, maxY);
            } else if (this.currentFilterMode == FilterMode.RANGE) {
                s.layerMin = Mth.clamp(layer, minY, s.layerMax);
            } else {
                s.layer = Mth.clamp(layer, minY, maxY);
            }

            dirty = true;
            // Defer rebuildVisibility() to mouseReleased — rebuilding per-pixel during drag
            // is a full VBO bake on every event, which tanks framerate on large machines.
            return true;
        }

        // Default: Let camera pan/orbit catch the movement so moving the multiblock doesn't freeze or crash!
        if (my >= TOP_BAR_H && my < this.height - BOTTOM_H && camera != null) {
            // Button 1 (Right click) or Button 2 (Middle click) panners
            if (btn == 1 || btn == 2) {
                // 1. Get orientation direction arrays from the camera system
                org.joml.Vector3f right = new org.joml.Vector3f();
                org.joml.Vector3f up = new org.joml.Vector3f();
                camera.getRightAndUp(right, up);

                float s = camera.getZoom() * CAM_PAN_ZOOM_SCALE;

                // 3. Project 2D screen adjustments cleanly onto 3D world space axes
                camera.pan(
                        (right.x * (float) -dx + up.x * (float) dy) * s,
                        (right.y * (float) -dx + up.y * (float) dy) * s,
                        (right.z * (float) -dx + up.z * (float) dy) * s);
            }
            // Button 0 (Left click) orbit
            else if (btn == 0) {
                camera.orbit((float) dx * CAM_ORBIT_SENSITIVITY, (float) dy * CAM_ORBIT_SENSITIVITY);
            }
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (my >= TOP_BAR_H && my < this.height - BOTTOM_H && camera != null) {
            camera.zoom(delta > 0 ? 0.9f : 1.1f, 2f, 150f);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (selectClickPending && btn == selectClickBtn) {
            selectClickPending = false;
            if (Math.abs(mx - selectClickMX) + Math.abs(my - selectClickMY) < 4)
                handleSelectClick(selectClickMX, selectClickMY, selectClickBtn);
        }
        if (draggingTimelineDot >= 0 && dotDragMoved) {
            PhantasiaScriptData.StepData dragged = data.getSteps().get(draggingTimelineDot);
            data.getSteps().sort(Comparator.comparingInt(s -> s.tick));
            selectedStep = data.getSteps().indexOf(dragged);
            populateInputsFromStep();
            rebuildVisibility();
        }
        draggingTimelineDot = -1;
        dotDragMoved = false;
        if (draggingLayer) rebuildVisibility();
        draggingLayer = false;
        draggingLayerMax = false;
        reorderingStep = -1;
        reorderInsertAt = -1;
        selectClickPending = false;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean charTyped(char c, int mod) {
        return super.charTyped(c, mod);
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (kc == GLFW.GLFW_KEY_ESCAPE && mode == Mode.WORLD && wiBlock != null) {
            wiBlock = null;
            return true;
        }
        boolean ctrl = (mod & 2) != 0;
        if (ctrl && kc == GLFW.GLFW_KEY_Z) {
            undo();
            return true;
        }
        if (ctrl && kc == 83) {
            save();
            return true;
        }
        if (ctrl && kc == 67) {
            copyStep();
            return true;
        }
        if (ctrl && kc == 86) {
            pasteStep();
            return true;
        }
        if (ctrl && kc == 68 && mode != Mode.SELECT) {
            duplicateStep();
            return true;
        }
        if (partsExprBox != null && partsExprBox.isFocused() &&
                (kc == GLFW.GLFW_KEY_ENTER || kc == GLFW.GLFW_KEY_KP_ENTER)) {
            partsExprBox.setFocused(false);
            return true;
        }
        if (getFocused() != null && getFocused().keyPressed(kc, sc, mod)) return true;
        if (pendingAnnotationLocalPos != null) {
            if (kc == GLFW.GLFW_KEY_ENTER || kc == GLFW.GLFW_KEY_KP_ENTER) {
                confirmAnnotation();
                return true;
            }
            if (kc == GLFW.GLFW_KEY_ESCAPE) {
                cancelAnnotation();
                return true;
            }
        }
        if (kc == GLFW.GLFW_KEY_ESCAPE) {
            if (showPartsModal) {
                showPartsModal = false;
                return true;
            }
            if (showingCloseConfirm) {
                showingCloseConfirm = false;
                return true;
            }
            if (mode != null) {
                setMode(null);
                return true;
            }
            onClose();
            return true;
        }
        if (kc == GLFW.GLFW_KEY_DELETE || kc == GLFW.GLFW_KEY_BACKSPACE) {
            deleteStep();
            return true;
        }
        if (kc == GLFW.GLFW_KEY_ENTER || kc == GLFW.GLFW_KEY_KP_ENTER) {
            addStep();
            return true;
        }
        if (kc == GLFW.GLFW_KEY_RIGHT) {
            if (ctrl) moveStep(selectedStep, +1);
            else selectStep(Math.min(selectedStep + 1, data.getSteps().size() - 1));
            return true;
        }
        if (kc == GLFW.GLFW_KEY_LEFT) {
            if (ctrl) moveStep(selectedStep, -1);
            else selectStep(Math.max(selectedStep - 1, 0));
            return true;
        }
        if (mode == Mode.SELECT && ctrl) {
            if (kc == 65) {
                selectAllBlocks();
                return true;
            }
            if (kc == 68) {
                deselectAll();
                return true;
            }
        }
        return super.keyPressed(kc, sc, mod);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Undo
    // ─────────────────────────────────────────────────────────────────────────

    public void checkpoint() {
        if (undoStack.size() >= MAX_UNDO) {
            undoStack.pollFirst();
            undoStepStack.pollFirst();
        }
        undoStack.addLast(data.copy());
        undoStepStack.addLast(selectedStep);
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        data = undoStack.pollLast();
        int restoredStep = undoStepStack.isEmpty() ? selectedStep : undoStepStack.pollLast();
        selectedStep = Mth.clamp(restoredStep, 0, data.getSteps().size() - 1);
        dirty = !undoStack.isEmpty();
        populateInputsFromStep();
        syncSelectedFromStep();
        rebuildVisibility();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Actions
    // ─────────────────────────────────────────────────────────────────────────

    private void setMode(Mode m) {
        mode = m;
        wiBlock = null;
        if (m == Mode.SELECT) {
            selectPreviewMode = true; // start showing filtered result, like HidePos's "always preview"
            syncSelectedFromStep();
            if (!"pos".equals(step().show)) {
                step().show = "pos";
                dirty = true;
            }
        } else {
            // Restore show from the current filter mode so that exiting SELECT
            // doesn't leave show="pos" with an empty positions list (which hides everything).
            saveFilterStateToStep();
        }
        pendingAnnotationLocalPos = null;
        rebuildVisibility();
    }

    private void togglePreview() {
        previewing = !previewing;
        previewTick = 0;
        previewAccum = 0f;
        if (!previewing) rebuildVisibility();
    }

    private void selectStep(int i) {
        selectedStep = Mth.clamp(i, 0, data.getSteps().size() - 1);
        wiBlock = null;

        // 1. Reconstruct the active FilterMode enum and fill caches from the new step
        populateInputsFromStep();

        // 2. Lock in and compile the filter state to ensure s.show matches exactly
        saveFilterStateToStep();

        if (mode == Mode.SELECT) syncSelectedFromStep();

        // 3. Apply the new step's working state to the scene world, then rebake so active
        // block textures (glows, active coils) immediately reflect the new state.
        if (parentScene != null) parentScene.applyActiveStateToWorld(step().working);
        if (renderer != null) renderer.requestBake();

        // 4. Force-repaint the main world view instantly
        rebuildVisibility();
    }

    private void addStepAtTick(int tick) {
        // Determine what show string we are inheriting from the timeline position
        String inheritedShow = "all";
        for (PhantasiaScriptData.StepData step : data.getSteps()) {
            if (step.tick <= tick) inheritedShow = step.show;
            else break;
        }

        PhantasiaScriptData.StepData s = new PhantasiaScriptData.StepData(tick, null);
        s.show = inheritedShow;

        int insertAt = data.getSteps().size();
        for (int i = 0; i < data.getSteps().size(); i++) {
            if (data.getSteps().get(i).tick > tick) {
                insertAt = i;
                break;
            }
        }

        checkpoint();
        data.getSteps().add(insertAt, s);

        // Select the newly spawned step index
        selectStep(insertAt);

        // 4. Force our isolated caches to immediately realign with the newly inherited settings!
        populateInputsFromStep();
        saveFilterStateToStep();
        rebuildVisibility();

        dirty = true;
    }

    private void addStep() {
        int lastTick = data.getSteps().isEmpty() ? 0 : data.getSteps().get(data.getSteps().size() - 1).tick + 60;
        PhantasiaScriptData.StepData s = new PhantasiaScriptData.StepData(lastTick, null);
        s.show = "all";
        checkpoint();
        data.getSteps().add(s);
        selectStep(data.getSteps().size() - 1);
        dirty = true;
    }

    private void deleteStep() {
        if (data.getSteps().size() <= 1) return;
        checkpoint();
        data.getSteps().remove(selectedStep);
        selectStep(Math.min(selectedStep, data.getSteps().size() - 1));
        dirty = true;
    }

    private void duplicateStep() {
        if (selectedStep < 0 || selectedStep >= data.getSteps().size()) return;
        PhantasiaScriptData.StepData copy = data.getSteps().get(selectedStep).copy();
        copy.tick += 60;
        checkpoint();
        data.getSteps().add(selectedStep + 1, copy);
        selectStep(selectedStep + 1);
        dirty = true;
    }

    private void copyStep() {
        if (selectedStep >= 0 && selectedStep < data.getSteps().size())
            stepClipboard = data.getSteps().get(selectedStep).copy();
    }

    private void pasteStep() {
        if (stepClipboard == null) return;
        PhantasiaScriptData.StepData copy = stepClipboard.copy();
        copy.tick = step().tick + 60;
        checkpoint();
        data.getSteps().add(selectedStep + 1, copy);
        selectStep(selectedStep + 1);
        dirty = true;
    }

    private void moveStep(int from, int delta) {
        int to = from + delta;
        if (to < 0 || to >= data.getSteps().size()) return;
        checkpoint();
        Collections.swap(data.getSteps(), from, to);
        selectedStep = to;
        rebuildVisibility();
        dirty = true;
    }

    private void captureCamera() {
        PhantasiaScriptData.StepData s = step();
        if (s.camera == null) s.camera = new PhantasiaScriptData.CameraData();
        checkpoint();
        if (camera != null) {
            s.camera.yaw = camera.getYaw();
            s.camera.pitch = camera.getPitch();
            s.camera.zoom = camera.getZoom();
        }
        if (camZoomBox != null) camZoomBox.setValue(s.camera.zoom > 0 ? String.valueOf(s.camera.zoom) : "");
        dirty = true;
    }

    private void confirmAnnotation() {
        if (pendingAnnotationLocalPos == null) return;
        String label = pendingAnnotationLabel.trim();
        if (!label.isEmpty()) {
            checkpoint();
            BlockPos lp = pendingAnnotationLocalPos;
            String colorHex = String.format("%06X", MISTAKE_COLORS[selectedMistakeColor] & 0xFFFFFF);
            data.getMistakes().removeIf(m -> m.x == lp.getX() && m.y == lp.getY() && m.z == lp.getZ());
            data.getMistakes()
                    .add(new PhantasiaScriptData.MistakeData(lp.getX(), lp.getY(), lp.getZ(), label, colorHex));
            dirty = true;
        }
        pendingAnnotationLocalPos = null;
        pendingAnnotationLabel = "";
    }

    private void cancelAnnotation() {
        pendingAnnotationLocalPos = null;
        pendingAnnotationLabel = "";
    }

    private void selectAllBlocks() {
        if (pattern == null) return;
        checkpoint();
        step().positions.clear();
        selectedWorldPos.clear();
        for (Map.Entry<BlockPos, BlockPos> e : pattern.localToWorld.entrySet()) {
            if (pattern.baseplatePositions.contains(e.getValue())) continue;
            step().positions.add(new int[] { e.getKey().getX(), e.getKey().getY(), e.getKey().getZ() });
            selectedWorldPos.add(e.getValue());
        }
        dirty = true;
        rebuildVisibility();
    }

    private void deselectAll() {
        checkpoint();
        step().positions.clear();
        selectedWorldPos.clear();
        dirty = true;
        showAllForPickingMode(); // mirrors HidePos's "Clear" button dropping back into pick mode
    }

    private void save() {
        PhantasiaScriptLoader.save(machineId, data);
        dirty = false;
        if (parentScene != null) parentScene.reloadScript();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private int computeTotalTicks() {
        int dur = data.getScriptDuration();
        if (dur > 0) return dur;
        return data.getSteps().isEmpty() ? 60 : data.getSteps().get(data.getSteps().size() - 1).tick + 60;
    }

    private void syncSelectedFromStep() {
        selectedWorldPos.clear();
        if (pattern == null) return;
        for (int[] xyz : step().positions) {
            if (xyz.length < 3) continue;
            BlockPos world = pattern.toWorld(new BlockPos(xyz[0], xyz[1], xyz[2]));
            if (world != null) selectedWorldPos.add(world);
        }
    }

    private boolean isInPositionList(BlockPos local) {
        for (int[] p : step().positions)
            if (p.length >= 3 && p[0] == local.getX() && p[1] == local.getY() && p[2] == local.getZ()) return true;
        return false;
    }

    private void populateInputsFromStep() {
        if (tickBox == null) return;
        isPopulating = true;
        try {
            populateInputsFromStepImpl();
        } finally {
            isPopulating = false;
        }
    }

    private void populateInputsFromStepImpl() {
        if (tickBox == null) return;
        PhantasiaScriptData.StepData s = step();
        tickBox.setValue(String.valueOf(s.tick));

        // --- READ AND POPULATE MULTI-Y LAYER BOX ---
        String layersString = "";
        String rawShow = s.show != null ? s.show.trim() : "all";

        if (rawShow.contains("|hidey:")) {
            String[] parts = rawShow.split("\\|hidey:", 2);
            layersString = parts[1]; // Extract "1,2"
            rawShow = parts[0];      // Clean up base mode (e.g., "all", "layers", "parts:...")
        } else if (s.hideLayer >= 0) {
            layersString = String.valueOf(s.hideLayer);
        }
        hideLayerBox.setValue(layersString);

        // REMOVED: hidePosBox.setValue(...) to fix compilation crash

        if (layerCountBox != null)
            layerCountBox.setValue(s.layerCount > 0 ? String.valueOf(s.layerCount) : "");
        if (lerpTicksBox != null && s.camera != null)
            lerpTicksBox.setValue(String.valueOf(s.camera.lerpTicks > 0 ? s.camera.lerpTicks : 20));
        if (camZoomBox != null && s.camera != null)
            camZoomBox.setValue(s.camera.zoom > 0 ? String.valueOf(s.camera.zoom) : "");
        if (scriptDurationBox != null)
            scriptDurationBox.setValue(data.getScriptDuration() > 0 ? String.valueOf(data.getScriptDuration()) : "");
        populateStartCamBoxes();

        // 1. Determine active mode from our stripped rawShow base string
        if (s != null && s.show != null) {
            if (rawShow.equals("layers")) {
                this.currentFilterMode = FilterMode.RANGE;
                this.cacheRangeMin = String.valueOf(s.layerMin);
                this.cacheRangeMax = String.valueOf(s.layerMax);
            } else if (rawShow.startsWith("range:")) {
                this.currentFilterMode = FilterMode.RANGE;
                String body = rawShow.substring(6);
                if (body.contains("..")) {
                    String[] split = body.split("\\.\\.", 2);
                    this.cacheRangeMin = split[0].trim();
                    this.cacheRangeMax = split[1].trim();
                    try {
                        s.layerMin = Integer.parseInt(this.cacheRangeMin);
                    } catch (NumberFormatException ignored) {}
                    try {
                        s.layerMax = Integer.parseInt(this.cacheRangeMax);
                    } catch (NumberFormatException ignored) {}
                    s.show = "layers" + (layersString.isEmpty() ? "" : "|hidey:" + layersString);
                }
            } else if (rawShow.startsWith("parts:")) {
                this.currentFilterMode = FilterMode.PARTS;
                this.cachePartsExpr = rawShow.substring(6);
            } else if (rawShow.equalsIgnoreCase("layer")) {
                this.currentFilterMode = FilterMode.LAYER;
            } else {
                this.currentFilterMode = FilterMode.ALL;
            }
        }

        // 2. Safely reflect caches back into the text boxes
        if (this.partsExprBox != null && !this.partsExprBox.isFocused()) {
            this.partsExprBox.setValue(this.cachePartsExpr);
        }
        if (this.rangeMinBox != null && !this.rangeMinBox.isFocused()) {
            this.rangeMinBox.setValue(this.cacheRangeMin);
        }
        if (this.rangeMaxBox != null && !this.rangeMaxBox.isFocused()) {
            this.rangeMaxBox.setValue(this.cacheRangeMax);
        }
    }

    private void saveFilterStateToStep() {
        PhantasiaScriptData.StepData s = step();
        if (s == null) return;

        switch (this.currentFilterMode) {
            case ALL -> s.show = "all";
            case LAYER -> s.show = "layer";
            case RANGE -> {
                // Write canonical "layers" so evalShowFilter matches it,
                // and store the values in the struct fields it reads from.
                s.show = "layers";
                try {
                    s.layerMin = Integer.parseInt(this.cacheRangeMin.trim());
                } catch (NumberFormatException ignored) {}
                try {
                    s.layerMax = Integer.parseInt(this.cacheRangeMax.trim());
                } catch (NumberFormatException ignored) {}
            }
            case PARTS -> {
                String expr = this.cachePartsExpr.trim();
                // If parts mode is active but expression is empty, show all blocks safely
                s.show = expr.isEmpty() ? "all" : "parts:" + expr;
            }
        }
        this.dirty = true;
    }

    private void populateStartCamBoxes() {
        if (scYawBox == null) return;
        PhantasiaScriptData.StartCameraData sc = data.getStartCamera();
        if (sc == null) {
            scYawBox.setValue("");
            scPitchBox.setValue("");
            scZoomBox.setValue("");
            scOffsetXBox.setValue("");
            scOffsetYBox.setValue("");
            scOffsetZBox.setValue("");
        } else {
            scYawBox.setValue(sc.hasYaw() ? String.valueOf(sc.yaw) : "");
            scPitchBox.setValue(sc.hasPitch() ? String.valueOf(sc.pitch) : "");
            scZoomBox.setValue(sc.hasZoom() ? String.valueOf(sc.zoom) : "");
            scOffsetXBox.setValue(sc.targetOffsetX != 0f ? String.valueOf(sc.targetOffsetX) : "");
            scOffsetYBox.setValue(sc.targetOffsetY != 0f ? String.valueOf(sc.targetOffsetY) : "");
            scOffsetZBox.setValue(sc.targetOffsetZ != 0f ? String.valueOf(sc.targetOffsetZ) : "");
        }
    }

    private PhantasiaScriptData.StepData step() {
        if (selectedStep >= 0 && selectedStep < data.getSteps().size())
            return data.getSteps().get(selectedStep);
        return new PhantasiaScriptData.StepData();
    }

    private float[] projectToScreen(float wx, float wy, float wz,
                                    Vector3f eye, Vector3f fwd, Vector3f rgt, Vector3f upv, float fov) {
        Vector3f toP = new Vector3f(wx - eye.x(), wy - eye.y(), wz - eye.z());
        float depth = toP.dot(fwd);
        if (depth < 0.3f) return null;
        return new float[] { this.width / 2f + (toP.dot(rgt) / depth) * fov,
                this.height / 2f - (toP.dot(upv) / depth) * fov, depth };
    }

    /** Small button with a tooltip that sets {@link #pendingTooltip} on hover. */

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
            if (!sb.isEmpty()) sb.append("; ");
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
        // Do NOT close the renderer — it belongs to parentScene and is still needed there.
        // Restore full visibility so the main scene looks correct when we return.
        if (renderer != null && parentScene != null) {
            parentScene.applyVisibility();
        }
        renderer = null;
        blockIdentifierCache.clear();
        editorLevel = null;
        pattern = null;
        if (parentScene != null) parentScene.reloadScript();
        Minecraft.getInstance().setScreen(parentScene);
    }

    /**
     * Resolves the block state at a world position using the central variant engine.
     * This ensures the editor logic (filtering, picking, naming) perfectly matches
     * the actual visual models shown by the 3D renderer.
     */
    private BlockState getResolvedState(BlockPos pos) {
        if (SHARED_LEVEL == null || pos == null) return null;
        BlockState baseState = SHARED_LEVEL.getBlockState(pos);
        if (baseState == null || baseState.isAir()) return baseState;

        try {
            BlockState resolved = PhantasiaVariantState.get().resolveState(pos, baseState);
            return resolved != null ? resolved : baseState;
        } catch (Exception e) {
            return baseState;
        }
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
