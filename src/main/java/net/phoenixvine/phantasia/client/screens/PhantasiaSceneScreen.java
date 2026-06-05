package net.phoenixvine.phantasia.client.screens;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phantasia.client.camera.CameraView;
import net.phoenixvine.phantasia.client.camera.LerpType;
import net.phoenixvine.phantasia.client.camera.PhantasiaCamera;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.client.render.PhantasiaWorldRenderer;
import net.phoenixvine.phantasia.common.PhantasiaLoadedPattern;
import net.phoenixvine.phantasia.common.PhantasiaScript;
import net.phoenixvine.phantasia.common.PhantasiaScriptData;
import net.phoenixvine.phantasia.common.PhantasiaScripts;
import net.phoenixvine.phantasia.utils.PhantasiaThemeUtils;
import net.phoenixvine.phantasia.utils.PhantasiaUIUtils;

import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.*;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

@OnlyIn(Dist.CLIENT)
public class PhantasiaSceneScreen extends Screen {

    // ─────────────────────────────────────────────────────────────────────────
    // Shared dummy world
    // ─────────────────────────────────────────────────────────────────────────

    public static PhantasiaTrackedDummyWorld SHARED_LEVEL;
    /**
     * Persists the machine working state across screen instances (e.g. opening
     * the script editor subscreen creates a new PhantasiaSceneScreen instance on
     * return). Stored statically alongside SHARED_LEVEL so the VBO is always
     * restored to the correct active state without recalculating from playbackTick,
     * which would be 0 on a fresh instance and produce the wrong result.
     * Only reset when the scene screen itself is fully closed via onClose().
     */
    private static boolean machineWorking = false;
    private static final int REGION_SIZE = 512; // retained for any external references

    public static void invalidateSharedLevel() {
        SHARED_LEVEL = null;
    }

    public static BlockPos getOriginForCurrentPattern() {
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof PhantasiaSceneScreen pss && pss.pattern != null)
            return pss.pattern.origin;
        if (mc.screen instanceof PhantasiaFootprintScreen pfs && pfs.getPattern() != null)
            return pfs.getPattern().origin;
        return BlockPos.ZERO;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Layout constants
    // ─────────────────────────────────────────────────────────────────────────

    private static final int FULL_PANEL_W = 168;
    private static final int COLLAPSED_PANEL_W = 18;
    private static final int TIMELINE_H = 26;
    private static final int CAPTION_STRIP_H = 22;

    // ─────────────────────────────────────────────────────────────────────────
    // Camera defaults
    // ─────────────────────────────────────────────────────────────────────────

    private static final float CAM_TARGET_Y_BIAS = 0.0f;
    private static final float CAM_DEFAULT_PITCH = 5.0f;
    private static final float CAM_DEFAULT_ZOOM = 40.0f;
    private static final float CAM_ZOOM_IN_FACTOR = 0.9f;
    private static final float CAM_ZOOM_OUT_FACTOR = 1.1f;
    private static final float CAM_ZOOM_MIN = 2.0f;
    private static final float CAM_ZOOM_MAX = 300.0f;
    private static final float CAM_ORBIT_SENSITIVITY = 0.5f;
    private static final float CAM_PAN_SPEED = 0.02f;

    // ─────────────────────────────────────────────────────────────────────────
    // Core state
    // ─────────────────────────────────────────────────────────────────────────

    private final Screen parent;
    public final MultiblockMachineDefinition definition;
    private PhantasiaScript script;

    private PhantasiaLoadedPattern pattern;

    /**
     * Our custom renderer. Created once on first init(), survives re-inits
     * (window resize, sub-screen returns) so VBOs are preserved.
     * Explicitly closed in onClose() and on shape changes.
     */
    private PhantasiaWorldRenderer renderer;

    private int shapeIndex = 0;
    private List<MultiblockShapeInfo> availableShapes = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────────────────
    // Camera
    // ─────────────────────────────────────────────────────────────────────────

    private PhantasiaCamera camera;
    private boolean isPanning = false;

    // ─────────────────────────────────────────────────────────────────────────
    // Playback
    // ─────────────────────────────────────────────────────────────────────────

    private boolean playing = true;
    private int playbackTick = 0;
    private float tickAccum = 0f;
    private float playbackSpeed = 1.0f;
    private boolean scrubbing = false;
    private PhantasiaScript.Step lastAppliedStep = null;

    // ─────────────────────────────────────────────────────────────────────────
    // View / filter
    // ─────────────────────────────────────────────────────────────────────────

    public enum ViewFilter {
        ALL,
        HATCHES_BUSES,
        ENERGY_IO,
        BLOCK_ENTITIES,
        CONTROLLER
    }

    private ViewFilter viewFilter = ViewFilter.ALL;
    private boolean wasPlayingBeforeFilter = false;
    private int manualLayer = -1;

    private Set<BlockPos> filteredHatchBus = null;
    private Set<BlockPos> filteredEnergyIO = null;
    private Set<BlockPos> filteredHasBE = null;
    private Set<BlockPos> filteredController = null;

    // ─────────────────────────────────────────────────────────────────────────
    // Build-order mode
    // ─────────────────────────────────────────────────────────────────────────

    private boolean buildOrderMode = false;
    private int buildOrderGroup = 0;
    private float buildPulse = 0f;
    private int savedManualLayer = -1;
    private boolean buildPulseUp = true;

    private int coilIndex = 1;

    // Dynamically retrieve all registered coil blocks sorted by their temperature/tier
    private static final List<BlockInfo> COIL_TIERS = java.util.stream.Stream.of(
                    com.gregtechceu.gtceu.api.block.ICoilType.ALL_COILS_TEMPERATURE_SORTED.get())
            .map(coil -> {
                // Fetch the block associated with the coil material from the registry
                var block = com.gregtechceu.gtceu.api.GTCEuAPI.HEATING_COILS.get(coil);
                return new BlockInfo(block.get().defaultBlockState());
            })
            .toList();

    // ─────────────────────────────────────────────────────────────────────────
    // Caption
    // ─────────────────────────────────────────────────────────────────────────

    private float captionAlpha = 0f;
    private String captionCurrent = null;
    private String captionOutgoing = null;
    private float captionOutAlpha = 0f;

    // ─────────────────────────────────────────────────────────────────────────
    // UI
    // ─────────────────────────────────────────────────────────────────────────

    private final List<PhantasiaUIUtils.ButtonAction> activeButtons = new ArrayList<>();
    private boolean sidePanelCollapsed = false;
    private BlockPos hoveredPos = null;

    public boolean showMistakes = false;
    public int selectedTierIndex = -1;

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────────

    public PhantasiaSceneScreen(MultiblockMachineDefinition definition, Screen parent) {
        super(Component.literal(definition.getLangValue()));
        this.parent = parent;
        this.definition = definition;
        this.script = PhantasiaScripts.get(definition);
    }

    public void reloadScript() {
        this.script = PhantasiaScripts.get(definition);
        this.lastAppliedStep = null;
        applyVisibility();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // init()
    // ─────────────────────────────────────────────────────────────────────────

    private List<BlockInfo> coilTiers = new ArrayList<>();

    @Override
    protected void init() {
        super.init();

        this.coilTiers = java.util.stream.Stream.of(
                        com.gregtechceu.gtceu.api.block.ICoilType.ALL_COILS_TEMPERATURE_SORTED.get())
                .map(coil -> {
                    var block = com.gregtechceu.gtceu.api.GTCEuAPI.HEATING_COILS.get(coil);
                    return new BlockInfo(block.get().defaultBlockState());
                })
                .toList();

        if (SHARED_LEVEL == null) {
            if (Minecraft.getInstance().level == null) {
                onClose();
                return;
            }
            SHARED_LEVEL = new PhantasiaTrackedDummyWorld();

            // Fix: Instantiate with no arguments
            SHARED_LEVEL.setParticleManager(new com.lowdragmc.lowdraglib.client.scene.ParticleManager());
        }

        availableShapes = definition.getMatchingShapes();
        if (pattern == null && !availableShapes.isEmpty()) {
            if (shapeIndex >= availableShapes.size()) shapeIndex = 0;
            pattern = loadPattern(availableShapes.get(shapeIndex));
            invalidateFilterSets();
            // Sync coilIndex to whatever coil type is actually in the loaded pattern
            // so the button label matches the displayed blocks from the first frame.
            // coilIndex defaults to 1 (kanthal) but patterns typically load with
            // cupronickel (index 0) from their MultiblockShapeInfo.
            coilIndex = detectCoilIndex(pattern);
            // Restore the persisted working state to SHARED_LEVEL immediately after
            // pattern load. Pattern load writes fresh default states (ACTIVE=false).
            // We restore from the static machineWorking field rather than recalculating
            // from script+playbackTick because on a fresh screen instance playbackTick=0
            // which would incorrectly evaluate to non-working for most scripts.
            applyActiveStateToWorld(machineWorking);
        }

        // ── Renderer ──────────────────────────────────────────────────────────
        if (renderer == null) {
            renderer = new PhantasiaWorldRenderer(SHARED_LEVEL);
            if (pattern != null) {
                renderer.setBaseplatePositions(pattern.baseplatePositions);
                renderer.setControllerWorldPos(pattern.controllerWorldPos);
            }
        }
        // Initialise the isolated particle engine for this scene.
        // Must be called each time the scene opens (not just when renderer is new)
        // so the engine is fresh and providers are current.
        net.phoenixvine.phantasia.client.render.PhantasiaParticleEngine.init();
        SHARED_LEVEL.entities.clear();

        // ── Camera ────────────────────────────────────────────────────────────
        if (camera == null) {
            camera = buildFreshCamera();
        } else if (camera.hasSavedSnapshot()) {
            camera.restore();
        } else if (!camera.isPlayerOwned()) {
            resetCameraToDefault(LerpType.SNAP, 0);
        }

        // ── Synchronize Structure and Text ────────────────────────────────────
        if (pattern != null) {
            // Force the structure block states to match coilIndex (0) immediately on startup
            updateCoilType();
            applyVisibility();

            // ── PERFORMANCE OPTIMIZATION: Populate Ambient Ticking Cache ──
            // Instead of querying pattern maps every tick, cache the ticking positions once here.
            this.ambientTickingPositions.clear();
            for (net.minecraft.core.BlockPos wp : pattern.blockMap.keySet()) {
                net.minecraft.world.level.block.state.BlockState state = SHARED_LEVEL.getBlockState(wp);
                // Ignore air entirely, map only real positions
                if (!state.isAir()) {
                    this.ambientTickingPositions.add(wp);
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Camera helpers
    // ─────────────────────────────────────────────────────────────────────────

    private PhantasiaCamera buildFreshCamera() {
        float[] yp = resolveStartingYawPitch();
        float zoom = resolveStartingZoom();
        float[] target = resolveTarget();
        PhantasiaCamera cam = new PhantasiaCamera(yp[0], yp[1], zoom,
                target[0], target[1], target[2]);
        if (pattern != null) cam.setFloorY(pattern.origin.getY() + 0.5f);
        return cam;
    }

    private void resetCameraToDefault(LerpType type, int ticks) {
        float[] yp = resolveStartingYawPitch();
        float zoom = resolveStartingZoom();
        float[] target = resolveTarget();
        camera.setTarget(target[0], target[1], target[2]);
        camera.hardReset(yp[0], yp[1], zoom, target[0], target[1], target[2], type, ticks);
        if (pattern != null) camera.setFloorY(pattern.origin.getY() + 0.5f);
    }

    private float[] resolveStartingYawPitch() {
        if (script != null) {
            var sc = script.getStartCamera();
            if (sc != null) {
                float yaw = sc.hasYaw() ? sc.getYaw() : getFacingYaw();
                float pitch = sc.hasPitch() ? sc.getPitch() : CAM_DEFAULT_PITCH;
                return new float[] { yaw, pitch };
            }
        }
        if (script != null && !script.getSteps().isEmpty()) {
            PhantasiaScript.Step s0 = script.getSteps().get(0);
            if (s0.hasCamera()) return new float[] { s0.yaw(), s0.pitch() };
        }
        return new float[] { getFacingYaw(), CAM_DEFAULT_PITCH };
    }

    private float resolveStartingZoom() {
        if (script != null) {
            var sc = script.getStartCamera();
            if (sc != null && sc.hasZoom()) return sc.getZoom();
        }
        if (script != null && !script.getSteps().isEmpty()) {
            PhantasiaScript.Step s0 = script.getSteps().get(0);
            if (s0.hasCamera() && s0.zoom() > 0) return s0.zoom();
        }
        if (pattern == null) return CAM_DEFAULT_ZOOM;
        // Compute the bounding box of THIS pattern only — do NOT use
        // SHARED_LEVEL.getSize(), which spans every region ever added to the
        // shared world and grows without bound as the player switches shapes.
        int sxLen = 0, syLen = 0, szLen = 0;
        for (BlockPos lp : pattern.localToWorld.keySet()) {
            sxLen = Math.max(sxLen, lp.getX() + 1);
            syLen = Math.max(syLen, lp.getY() + 1);
            szLen = Math.max(szLen, lp.getZ() + 1);
        }
        float maxDim = Math.max(sxLen, Math.max(syLen, szLen));
        return Math.max(CAM_DEFAULT_ZOOM, maxDim * 3.0f);
    }

    private float[] resolveTarget() {
        if (pattern == null) return new float[] { 0, 0, 0 };
        float cx, cy, cz;
        // Compute the centroid from this pattern's own world positions only.
        // SHARED_LEVEL.getMinPos()/getMaxPos() spans ALL ever-added regions
        // and drifts far from the current pattern after structure-size switches.
        int wxMin = Integer.MAX_VALUE, wxMax = Integer.MIN_VALUE;
        int wzMin = Integer.MAX_VALUE, wzMax = Integer.MIN_VALUE;
        for (BlockPos wp : pattern.blockMap.keySet()) {
            wxMin = Math.min(wxMin, wp.getX());
            wxMax = Math.max(wxMax, wp.getX());
            wzMin = Math.min(wzMin, wp.getZ());
            wzMax = Math.max(wzMax, wp.getZ());
        }
        if (wxMin > wxMax) {
            cx = pattern.origin.getX() + 0.5f;
            cz = pattern.origin.getZ() + 0.5f;
        } else {
            cx = (wxMin + wxMax) * 0.5f + 0.5f;
            cz = (wzMin + wzMax) * 0.5f + 0.5f;
        }
        cy = pattern.origin.getY() + (pattern.minY + pattern.maxY) * 0.5f + CAM_TARGET_Y_BIAS;
        if (script != null) {
            var sc = script.getStartCamera();
            if (sc != null && sc.hasTargetOffset()) {
                cx += sc.getTargetOffsetX();
                cy += sc.getTargetOffsetY();
                cz += sc.getTargetOffsetZ();
            }
        }
        return new float[] { cx, cy, cz };
    }

    private float getFacingYaw() {
        if (pattern == null || pattern.controllerWorldPos == null || SHARED_LEVEL == null)
            return -135f;
        try {
            BlockState ctrl = SHARED_LEVEL.getBlockState(pattern.controllerWorldPos);
            if (ctrl.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                return switch (ctrl.getValue(BlockStateProperties.HORIZONTAL_FACING)) {
                    case NORTH -> 180f;
                    case SOUTH -> 0f;
                    case WEST -> 270f;
                    case EAST -> 90f;
                    default -> -135f;
                };
            }
        } catch (Exception ignored) {}
        return -135f;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pattern loading
    // ─────────────────────────────────────────────────────────────────────────

    private PhantasiaLoadedPattern loadPattern(MultiblockShapeInfo shape) {
        // Fixed origin — Embeddium cannot see these blocks because getChunkSource()
        // in PhantasiaTrackedDummyWorld returns DummyWorld's own isolated chunk
        // source rather than the real ClientLevel's, so player position is irrelevant.
        BlockPos origin = new BlockPos(0, 50, 0);

        SHARED_LEVEL.renderedBlocks.clear();
        SHARED_LEVEL.blockEntities.clear();

        Map<BlockPos, BlockInfo> blockMap = new HashMap<>();
        Map<BlockPos, BlockPos> localToWorld = new HashMap<>();
        Set<BlockPos> baseplatePos = new HashSet<>();
        Set<BlockPos> bePos = new HashSet<>();
        BlockPos controllerWP = null;
        MultiblockControllerMachine controller = null;

        BlockInfo floor = BlockInfo.fromBlockState(Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        BlockInfo[][][] raw = shape.getBlocks();
        int sxLen = raw.length;
        int szLen = sxLen > 0 && raw[0].length > 0 ? raw[0][0].length : 0;
        int padX = Math.max(2, sxLen / 2 + 1);
        int padZ = Math.max(2, szLen / 2 + 1);

        for (int bx = -padX; bx <= sxLen + padX; bx++)
            for (int bz = -padZ; bz <= szLen + padZ; bz++) {
                BlockPos wp = origin.offset(bx, -1, bz);
                blockMap.put(wp, floor);
                baseplatePos.add(wp);
            }

        Map<BlockPos, BlockEntity> builtBEs = new HashMap<>();

        for (int x = 0; x < raw.length; x++)
            for (int y = 0; y < raw[x].length; y++)
                for (int z = 0; z < raw[x][y].length; z++) {
                    BlockInfo info = raw[x][y][z];
                    if (info == null) continue;
                    BlockPos lp = new BlockPos(x, y, z);
                    BlockPos wp = origin.offset(x, y, z);
                    try {
                        var be = info.getBlockEntity(wp);
                        if (be != null) {
                            builtBEs.put(wp, be);
                            if (be instanceof MetaMachineBlockEntity mbe) {
                                mbe.setLevel(SHARED_LEVEL);
                                var machine = mbe.getMetaMachine();
                                if (machine instanceof MultiblockControllerMachine ctrl && controllerWP == null) {
                                    controller = ctrl;
                                    controllerWP = wp;
                                }
                                bePos.add(wp);
                            }
                        }
                    } catch (Exception ignored) {}
                    blockMap.put(wp, info);
                    localToWorld.put(lp, wp);
                }

        SHARED_LEVEL.addBlocks(blockMap);

        // Register BEs using the already-constructed instances — no second getBlockEntity call.
        for (Map.Entry<BlockPos, BlockEntity> entry : builtBEs.entrySet()) {
            try {
                BlockEntity be = entry.getValue();
                be.setLevel(SHARED_LEVEL);
                SHARED_LEVEL.setInnerBlockEntity(be);
            } catch (Exception ignored) {}
        }
        net.phoenixvine.phantasia.Phantasia.LOGGER.info(
                "[Phantasia] Registered {} block entities with SHARED_LEVEL", bePos.size());
        if (controller != null) {
            try {
                BlockPattern pat = controller.getPattern();
                net.phoenixvine.phantasia.Phantasia.LOGGER.info(
                        "[Phantasia] loadPattern: controller={}, pattern={}, multiblockState={}",
                        controller.getClass().getSimpleName(),
                        pat != null ? "present" : "null",
                        controller.getMultiblockState());
                if (pat != null) {
                    boolean matched = pat.checkPatternAt(controller.getMultiblockState(), true);
                    net.phoenixvine.phantasia.Phantasia.LOGGER.info(
                            "[Phantasia] checkPatternAt result: {}", matched);
                    if (matched) {
                        controller.onStructureFormed();
                        net.phoenixvine.phantasia.Phantasia.LOGGER.info(
                                "[Phantasia] onStructureFormed completed, entities in world: {}",
                                SHARED_LEVEL.getAllEntities().spliterator().estimateSize());
                    }
                }
            } catch (Exception e) {
                net.phoenixvine.phantasia.Phantasia.LOGGER.error(
                        "[Phantasia] onStructureFormed failed: {}", e.getMessage(), e);
            }
        }

        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (BlockPos lp : localToWorld.keySet()) {
            minY = Math.min(minY, lp.getY());
            maxY = Math.max(maxY, lp.getY());
        }
        if (minY > maxY) {
            minY = 0;
            maxY = 0;
        }

        return new PhantasiaLoadedPattern(blockMap, localToWorld, baseplatePos,
                controllerWP, bePos, origin, minY, maxY, controller, script);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Visibility
    // ─────────────────────────────────────────────────────────────────────────

    public void applyVisibility() {
        if (renderer == null || pattern == null || SHARED_LEVEL == null) return;
        PhantasiaScript.Step step = script.getActiveStep(playbackTick);

        Set<BlockPos> next = new HashSet<>();
        for (Map.Entry<BlockPos, BlockPos> e : pattern.localToWorld.entrySet()) {
            if (isBlockVisible(e.getKey(), e.getValue(), step))
                next.add(e.getValue());
        }
        renderer.setVisible(next);
    }

    public boolean isBlockVisible(BlockPos local, BlockPos world, PhantasiaScript.Step step) {
        // ViewFilter always takes priority — it is an explicit "show only X" selection.
        if (viewFilter != ViewFilter.ALL) {
            Set<BlockPos> fs = getFilterSet(viewFilter);
            return fs == null || fs.contains(world);
        }
        // Build-order mode is checked BEFORE manualLayer: build mode drives its own
        // layer visibility and must never be overridden by whatever layer was set in
        // the normal view. When build mode is active, manualLayer is ignored entirely.
        if (buildOrderMode) {
            int g = pattern.getGroupIndex(local);
            return g != -1 && g <= buildOrderGroup;
        }
        // Manual layer filter (◀/▶ buttons in normal view).
        // manualLayer is in local (pattern-space) Y — same coordinate space as local.getY().
        if (manualLayer >= 0) return local.getY() == manualLayer;
        if (step != null) return step.filter().test(local);
        return true;
    }

    public void applyViewFilter(ViewFilter vf) {
        if (viewFilter == vf) return;
        viewFilter = vf;
        applyVisibility();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Filter sets (lazy build)
    // ─────────────────────────────────────────────────────────────────────────

    private boolean computeHasRealSizeVariants() {
        if (availableShapes == null || availableShapes.size() <= 1) return false;
        int firstCount = countBlocks(availableShapes.get(0));
        for (int i = 1; i < availableShapes.size(); i++) {
            if (countBlocks(availableShapes.get(i)) != firstCount) return true;
        }
        return false;
    }

    private static int countBlocks(MultiblockShapeInfo shape) {
        int count = 0;
        for (BlockInfo[][] layer : shape.getBlocks())
            for (BlockInfo[] row : layer)
                for (BlockInfo b : row)
                    if (b != null && b.getBlockState() != null && !b.getBlockState().isAir())
                        count++;
        return count;
    }

    private void buildFilterSets() {
        if (pattern == null || SHARED_LEVEL == null) return;
        filteredHatchBus = new HashSet<>();
        filteredEnergyIO = new HashSet<>();
        filteredHasBE = pattern.blockEntityWorldPos;
        filteredController = pattern.controllerWorldPos != null ? Set.of(pattern.controllerWorldPos) : Set.of();

        for (Map.Entry<BlockPos, BlockPos> e : pattern.localToWorld.entrySet()) {
            BlockPos wp = e.getValue();
            if (wp.equals(pattern.controllerWorldPos)) continue;
            BlockState state = SHARED_LEVEL.getBlockState(wp);
            if (!(state.getBlock() instanceof MetaMachineBlock)) continue;
            ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(state.getBlock());
            if (rl == null) continue;
            String p = rl.getPath();
            if (p.contains("hatch") || p.contains("bus") || p.contains("muffler") || p.contains("maintenance"))
                filteredHatchBus.add(wp);
            if (p.contains("energy") || p.contains("dynamo") || p.contains("laser") || p.contains("power"))
                filteredEnergyIO.add(wp);
        }
    }

    /**
     * Nulls all filter-set caches so the next getFilterSet() call rebuilds them
     * against the current pattern. Must be called whenever pattern changes.
     */
    private void invalidateFilterSets() {
        filteredHatchBus = null;
        filteredEnergyIO = null;
        filteredHasBE = null;
        filteredController = null;
    }

    private Set<BlockPos> getFilterSet(ViewFilter vf) {
        if (filteredHatchBus == null) buildFilterSets();
        return switch (vf) {
            case HATCHES_BUSES -> filteredHatchBus;
            case ENERGY_IO -> filteredEnergyIO;
            case BLOCK_ENTITIES -> filteredHasBE;
            case CONTROLLER -> filteredController;
            default -> null;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // tick()
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();

        if (camera != null) camera.tick();

        if (captionCurrent != null && captionAlpha < 1f)
            captionAlpha = Math.min(1f, captionAlpha + 0.1f);
        if (captionOutgoing != null) {
            captionOutAlpha -= 0.1f;
            if (captionOutAlpha <= 0f) {
                captionOutgoing = null;
                captionOutAlpha = 0f;
            }
        }

        if (buildOrderMode) {
            buildPulse += buildPulseUp ? 0.05f : -0.05f;
            if (buildPulse >= 1f) {
                buildPulse = 1f;
                buildPulseUp = false;
            }
            if (buildPulse <= 0f) {
                buildPulse = 0f;
                buildPulseUp = true;
            }
        }

        // Tick ambient particle effects whenever not actively scrubbing.
        // Scrubbing excluded because: (a) you're jumping through time so particle
        // continuity doesn't matter, and (b) calling tickAnimateForPos on every
        // block in the scene 20x/s while the mouse is moving caused noticeable lag.
        if (SHARED_LEVEL != null && renderer != null && !scrubbing) {
            tickAmbientEffects(SHARED_LEVEL.getRandom());
        }

        // Emit script-defined particle effects for the active step.
        if (SHARED_LEVEL != null && !scrubbing && playing && script != null) {
            PhantasiaScript.Step step = script.getActiveStep(playbackTick);
            if (step != null && !step.particleEffects().isEmpty()) {
                long gameTick = net.minecraft.client.Minecraft.getInstance().level != null
                        ? net.minecraft.client.Minecraft.getInstance().level.getGameTime() : 0;
                BlockPos origin = getOriginForCurrentPattern();
                for (net.phoenixvine.phantasia.common.PhantasiaParticleEffect fx : step.particleEffects()) {
                    BlockPos worldPos = fx.localPos().offset(origin);
                    fx.emit(SHARED_LEVEL, worldPos, gameTick, SHARED_LEVEL.getRandom());
                }
            }
        }

        if (!playing || scrubbing || buildOrderMode || script == null || viewFilter != ViewFilter.ALL) return;

        int prevTick = playbackTick;
        tickAccum += playbackSpeed;
        while (tickAccum >= 1f) {
            tickAccum -= 1f;
            playbackTick++;
        }
        if (playbackTick >= script.getTotalTicks()) {
            playbackTick = (int) script.getTotalTicks();
            playing = false;
        }

        PhantasiaScript.Step step = script.getActiveStep(playbackTick);

        if (step != null && step.forceCoil() != -1 && step.forceCoil() != coilIndex) {
            coilIndex = step.forceCoil();
            updateCoilType();
        }

        if (playbackTick != prevTick && step != lastAppliedStep) {
            lastAppliedStep = step;

            if (step != null && step.hasCamera() && camera != null) {
                float zoom = step.zoom() > 0 ? step.zoom() : camera.getZoom();
                camera.scriptDrive(step.yaw(), step.pitch(), zoom,
                        step.lerpType(), step.lerpTicks());
            }

            if (step != null && step.forceShape() != -1 && step.forceShape() != shapeIndex && availableShapes != null &&
                    step.forceShape() < availableShapes.size()) {

                shapeIndex = step.forceShape();

                camera.hardReset(
                        camera.getYaw(), camera.getPitch(), camera.getZoom(),
                        camera.getTargetX(), camera.getTargetY(), camera.getTargetZ(),
                        LerpType.SNAP, 0);

                if (renderer != null) {
                    renderer.close();
                    renderer = null;
                }
                pattern = null;
                invalidateFilterSets();
                init();
                return; // init() already calls applyVisibility
            }

            applyVisibility();
            updateMachineState(step);
            updateCaptionForStep(step);
        }
    }

    // Add this as a field in PhantasiaSceneScreen
    private final java.util.List<BlockPos> ambientTickingPositions = new java.util.ArrayList<>();

    private void tickAmbientEffects(net.minecraft.util.RandomSource random) {
        for (BlockPos wp : ambientTickingPositions) {
            if (!renderer.isVisible(wp)) continue;
            try {
                SHARED_LEVEL.tickAnimateForPos(wp, random);
            } catch (Exception e) {
                // Log once per position so spam doesn't fill the log.
                // Common cause: a custom particle type whose lazy Supplier throws
                // (e.g. the type isn't a SimpleParticleType, or KJS registry lookup
                // fails). The block simply won't emit particles this tick.
                if (net.phoenixvine.phantasia.Phantasia.LOGGER.isDebugEnabled()) {
                    net.phoenixvine.phantasia.Phantasia.LOGGER.debug(
                            "[Phantasia] animateTick failed at {}: {}", wp, e.getMessage());
                }
            }
        }
    }

    private void updateCaptionForStep(PhantasiaScript.Step step) {
        String next = step != null ? step.caption() : null;
        if (!Objects.equals(next, captionCurrent)) {
            captionOutgoing = captionCurrent;
            captionOutAlpha = captionAlpha;
            captionCurrent = next;
            captionAlpha = 0f;
        }
    }

    /**
     * Writes the ACTIVE block state property to the controller AND all coil blocks
     * in SHARED_LEVEL. Called whenever the working state changes or when coil types
     * are swapped, so the VBO always rebakes with the correct active states.
     *
     * GT's internal notifyBlockEntityChanged → sendBlockUpdated chain is a no-op in
     * TrackedDummyWorld, so we must write to SHARED_LEVEL explicitly.
     */
    private void applyActiveStateToWorld(boolean working) {
        if (SHARED_LEVEL == null || pattern == null || pattern.blockMap == null) return;
        var activeProp = com.gregtechceu.gtceu.api.block.property.GTBlockStateProperties.ACTIVE;

        // Controller
        if (pattern.controllerWorldPos != null) {
            try {
                var state = SHARED_LEVEL.getBlockState(pattern.controllerWorldPos);
                if (state.hasProperty(activeProp) && state.getValue(activeProp) != working)
                    SHARED_LEVEL.setBlock(pattern.controllerWorldPos,
                            state.setValue(activeProp, working), 3);
            } catch (Exception ignored) {}
        }

        // All coil blocks and any other animated blocks that carry ACTIVE
        for (Map.Entry<BlockPos, BlockInfo> e : pattern.blockMap.entrySet()) {
            try {
                var state = SHARED_LEVEL.getBlockState(e.getKey());
                if (state.hasProperty(activeProp) && state.getValue(activeProp) != working)
                    SHARED_LEVEL.setBlock(e.getKey(), state.setValue(activeProp, working), 3);
            } catch (Exception ignored) {}
        }
    }

    private void updateMachineState(PhantasiaScript.Step step) {
        if (pattern == null || pattern.controller == null) return;
        boolean working = step != null && step.working() && playbackTick < script.getTotalTicks();
        if (pattern.controller instanceof WorkableMultiblockMachine w) {
            RecipeLogic logic = w.getRecipeLogic();
            boolean wasWorking = (logic.getStatus() == RecipeLogic.Status.WORKING);
            if (wasWorking != working) {
                logic.setStatus(working ? RecipeLogic.Status.WORKING : RecipeLogic.Status.IDLE);
                if (renderer != null) renderer.invalidate();
            }

            String rid = (step != null && working) ? step.fakeRecipeId() : null;
            if (rid != null && !rid.isBlank()) {
                injectFakeRecipe(logic, rid.trim());
            } else if (!working && logic.getLastRecipe() != null) {
                logic.resetRecipeLogic();
            }
        }

        // Persist working state statically so reinit (subscreen return) can
        // restore it without recalculating from playbackTick=0 on the new instance.
        machineWorking = working;
        applyActiveStateToWorld(working);

        // Trigger a rebake so the new ACTIVE block states (coils, controller overlay)
        // are reflected in the VBO. Without this the rendered geometry stays frozen
        // with the pre-working states even though renderedBlocks has been updated.
        if (renderer != null) renderer.requestBake();

        var rs = pattern.controller.getRenderState();
        var ap = com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties.IS_ACTIVE;
        if (rs.hasProperty(ap) && rs.getValue(ap) != working) {
            pattern.controller.setRenderState(rs.setValue(ap, working));
            // Notify the BER that render state changed — in a real game this is done
            // via LDLib's @RequireRerender syncdata sync, which is a no-op in the dummy
            // world. Sending a block update forces the BER to re-read the render state.
            if (SHARED_LEVEL != null && pattern.controllerWorldPos != null) {
                SHARED_LEVEL.sendBlockUpdated(pattern.controllerWorldPos,
                        SHARED_LEVEL.getBlockState(pattern.controllerWorldPos),
                        SHARED_LEVEL.getBlockState(pattern.controllerWorldPos), 3);
            }
        }
    }

    private void injectFakeRecipe(RecipeLogic logic, String rid) {
        try {
            var rl = new ResourceLocation(rid);

            // Safely access the client-side global Minecraft recipe manager
            if (Minecraft.getInstance().getConnection() == null) return;

            var optionalRecipe = Minecraft.getInstance()
                    .getConnection()
                    .getRecipeManager()
                    .byKey(rl);

            // Check if the recipe exists and belongs to GregTech (GTRecipe)
            if (optionalRecipe.isPresent() &&
                    optionalRecipe.get() instanceof com.gregtechceu.gtceu.api.recipe.GTRecipe gtRecipe) {
                if (logic.getLastRecipe() != gtRecipe) {
                    // Initialize the recipe inside the machine logic loop safely
                    logic.setupRecipe(gtRecipe);

                    // Set progress to mid-recipe so duration-dependent renders show a stable state.
                    logic.setProgress(gtRecipe.duration / 2);
                }
            }
        } catch (Exception ignored) {
            // Bad resource location or missing recipe — leave last-recipe unchanged.
        }
    }

    /**
     * Scans the pattern blockMap for the first CoilBlock and returns its index
     * in coilTiers, so coilIndex stays in sync with what's actually displayed.
     * Returns 0 (lowest tier) if no match found.
     */
    private int detectCoilIndex(PhantasiaLoadedPattern pat) {
        if (pat == null || coilTiers.isEmpty()) return 0;
        for (BlockInfo info : pat.blockMap.values()) {
            var block = info.getBlockState().getBlock();
            if (block instanceof com.gregtechceu.gtceu.common.block.CoilBlock) {
                for (int i = 0; i < coilTiers.size(); i++) {
                    if (coilTiers.get(i).getBlockState().getBlock() == block) return i;
                }
            }
        }
        return 0;
    }

    private void updateCoilType() {
        if (pattern == null || pattern.blockMap == null || coilTiers.isEmpty()) return;

        if (coilIndex >= coilTiers.size()) coilIndex = 0;
        BlockInfo newCoil = coilTiers.get(coilIndex);

        // Invalidate FIRST — before any world state changes — so the bake thread
        // is cancelled before it reads the old coil states. If we invalidate after
        // setBlock(), there are frames where the VBO still has the old coil geometry
        // while the BER already reads the new coil block state and renders its active
        // overlay on top, causing the "cupronickel active bleeding into kanthal" bleed.
        if (renderer != null) renderer.invalidate();

        // Determine working state so coil blocks get the right ACTIVE value immediately.
        boolean currentlyWorking = script != null && script.getActiveStep(playbackTick) != null &&
                script.getActiveStep(playbackTick).working() && playbackTick < script.getTotalTicks();

        for (Map.Entry<BlockPos, BlockInfo> e : pattern.blockMap.entrySet()) {
            if (e.getValue().getBlockState().getBlock() instanceof com.gregtechceu.gtceu.common.block.CoilBlock) {
                e.setValue(newCoil);
                if (SHARED_LEVEL != null)
                    SHARED_LEVEL.setBlock(e.getKey(), newCoil.getBlockState(), 3);
            }
        }

        applyVisibility();
        // Re-apply active state to all blocks (including freshly-swapped coils)
        // so the new coil immediately shows as active if the step is working.
        applyActiveStateToWorld(currentlyWorking);
        if (script != null)
            updateMachineState(script.getActiveStep(playbackTick));
    }
    // ─────────────────────────────────────────────────────────────────────────
    // render()
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        activeButtons.clear();


        // ── FIX EMBEDDIUM CONTROLLER SHADER ANIMATION FREEZE ──
        // Embeddium relies on RenderSystem's Shader Game Time uniform to animate
        // LDLib/GregTech controller overlays. Since standard Screen rendering freezes
        // this timer uniform, we manually calculate and push the active engine time here.
        if (Minecraft.getInstance().level != null) {
            long gameTime = Minecraft.getInstance().level.getGameTime();
            RenderSystem.setShaderGameTime(gameTime, partial);
        }
        // ──────────────────────────────────────────────────────

        int pw = getCurrentPanelWidth();
        int sw = this.width - pw;
        int sh = this.height - TIMELINE_H - CAPTION_STRIP_H;

        g.fill(0, 0, this.width, this.height, C_BG());

        if (renderer != null && camera != null) {
            CameraView view = camera.getView(partial);
            renderer.setMousePos(mx, my);
            renderer.render(view, 0, CAPTION_STRIP_H, sw, sh);
            BlockHitResult hit = renderer.getLastHitResult();
            // Only accept hits on blocks that are actually visible (in targetVisible set).
            // Hidden blocks must not show hover info — their faces are transparent.
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos hp = hit.getBlockPos();
                hoveredPos = renderer.isVisible(hp) ? hp : null;
            } else {
                hoveredPos = null;
            }
        }

        renderCaption(g);
        if (buildOrderMode && pattern != null) renderBuildPulseBanner(g);
        if (showMistakes && script != null && script.hasCommonMistakes())
            renderMistakesOverlay(g);

        renderTimeline(g, mx, my);
        renderSidePanel(g, mx, my);
        regBtn(g, mx, my, 10, 10, 50, 18, "Back", this::onClose);

        super.render(g, mx, my, partial);

        int px = this.width - pw;
        if (hoveredPos != null && SHARED_LEVEL != null) {
            try {
                BlockState st = SHARED_LEVEL.getBlockState(hoveredPos);
                if (!st.isAir()) {
                    g.drawString(font, trunc(st.getBlock().getName().getString(), pw - 20),
                            px + 10, this.height - 20, C_DIM(), false);
                    if (mx < px) g.renderTooltip(font, st.getBlock().getName(), mx, my);
                }
            } catch (Exception ignored) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Timeline
    // ─────────────────────────────────────────────────────────────────────────

    private void renderTimeline(GuiGraphics g, int mx, int my) {
        int px = this.width - getCurrentPanelWidth();
        int barY = this.height - TIMELINE_H;

        g.fill(0, barY, px, this.height, C_TL_BG());
        g.fill(0, barY, px, barY + 1, C_ACCENT());

        int x = 6;
        regBtn(g, mx, my, x, barY + 4, 18, 17, playing ? "⏸" : "▶", () -> {
            if (!playing && playbackTick >= script.getTotalTicks()) {
                playbackTick = 0;
                tickAccum = 0f;
                lastAppliedStep = null;
                applyVisibility();
            }
            playing = !playing;
        });
        x += 22;
        regBtn(g, mx, my, x, barY + 4, 18, 17,
                camera != null && camera.isLocked() ? "🔒" : "🔓",
                () -> {
                    if (camera != null) camera.toggleLocked();
                });
        x += 22;
        String spd = playbackSpeed == 0.5f ? "½x" : playbackSpeed == 2f ? "2x" : "1x";
        regBtn(g, mx, my, x, barY + 4, 24, 17, spd,
                () -> playbackSpeed = playbackSpeed == 1f ? 2f : playbackSpeed == 2f ? 0.5f : 1f);

        int tx = 80, tw = px - tx - 65, midY = barY + TIMELINE_H / 2;
        g.fill(tx, midY - 1, tx + tw, midY + 1, 0xFF1A2C3C);

        float total = script.getTotalTicks();
        for (PhantasiaScript.Step s : script.getSteps()) {
            int mx2 = tx + (int) (tw * s.tickOffset() / total);
            g.fill(mx2 - 1, midY - 4, mx2 + 1, midY + 4, 0xAAFFFFFF);
        }
        float prog = total > 0 ? playbackTick / total : 0f;
        g.fill(tx, midY - 1, tx + (int) (tw * prog), midY + 1, C_PROG());
        g.fill(tx + (int) (tw * prog) - 2, midY - 4,
                tx + (int) (tw * prog) + 2, midY + 4, C_ACCENT());
        g.drawString(font, formatTicks(playbackTick), tx + tw + 8, barY + 9, C_DIM(), false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Caption strip
    // ─────────────────────────────────────────────────────────────────────────

    private void renderCaption(GuiGraphics g) {
        if (captionCurrent == null && captionOutgoing == null) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);

        int sw = this.width - getCurrentPanelWidth();
        int stripY = this.height - TIMELINE_H - CAPTION_STRIP_H;
        g.fill(0, stripY, sw, stripY + CAPTION_STRIP_H, 0xDD08080F);
        g.fill(0, stripY, sw, stripY + 1, 0xFF4FC3F7);
        int ty = stripY + (CAPTION_STRIP_H - 8) / 2;

        if (captionOutgoing != null && captionOutAlpha > 0.05f) {
            int col = ((int) (captionOutAlpha * 160) << 24) | 0xBBBBBB;
            g.drawCenteredString(font, trunc(captionOutgoing, sw - 20), sw / 2, ty, col);
        }
        if (captionCurrent != null && captionAlpha > 0.05f) {
            int col = ((int) (captionAlpha * 255) << 24) | 0xDDDDDD;
            g.drawCenteredString(font, trunc(captionCurrent, sw - 20), sw / 2, ty, col);
        }
        g.pose().popPose();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Build-order banner
    // ─────────────────────────────────────────────────────────────────────────

    private void renderBuildPulseBanner(GuiGraphics g) {
        if (buildOrderGroup >= pattern.buildOrder.size()) return;
        int sceneW = this.width - getCurrentPanelWidth();
        int alpha = (int) (buildPulse * 0xBB);
        int col = (alpha << 24) | (C_TL_BG() & 0x00FFFFFF);
        int by = TIMELINE_H;
        g.fill(0, by, sceneW, by + 18, ((alpha / 3) << 24) | 0x1A1400);
        g.fill(0, by + 17, sceneW, by + 18, col);
        List<BlockPos> grp = pattern.buildOrder.get(buildOrderGroup);
        g.drawCenteredString(font,
                "Next: Layer Y=" + grp.get(0).getY() + " — " + grp.size() + " block(s)",
                sceneW / 2, by + 5, col);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mistakes overlay
    // ─────────────────────────────────────────────────────────────────────────

    private void renderMistakesOverlay(GuiGraphics g) {
        List<PhantasiaScript.LocalWarning> local = script.getCommonMistakes();
        List<String> global = script.getGlobalMistakes();
        int x = 8, y = TIMELINE_H + 26;
        int ph = (local.size() + global.size()) * 12 + 10;
        g.fill(x - 2, y - 2, x + 240, y + ph, 0xCC06060E);
        g.fill(x - 2, y - 2, x + 240, y - 1, 0xFFFF5252);
        for (var w : local) {
            g.drawString(font, "⚠ " + w.label(), x, y, w.color(), false);
            BlockPos lp = w.localPos();
            g.drawString(font,
                    " [" + lp.getX() + "," + lp.getY() + "," + lp.getZ() + "]",
                    x + font.width("⚠ " + w.label()), y, C_DIM(), false);
            y += 12;
        }
        for (String m : global) {
            g.drawString(font, "• " + m, x, y, 0xFFFFFFFF, false);
            y += 12;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Side panel
    // ─────────────────────────────────────────────────────────────────────────

    private void renderSidePanel(GuiGraphics g, int mx, int my) {
        int pw = getCurrentPanelWidth();
        int px = this.width - pw;
        activeButtons.removeIf(b -> b.x() >= px);

        g.fill(px, 0, this.width, this.height, C_PANEL());
        g.fill(px, 0, px + 1, this.height, C_ACCENT());

        // Collapse/Expand button
        int collapseBtnX = this.width - COLLAPSED_PANEL_W;
        String collapseBtnLabel = sidePanelCollapsed ? "▶" : "◀";
        regBtn(g, mx, my, collapseBtnX, 0, COLLAPSED_PANEL_W, 18, collapseBtnLabel, () -> {
            sidePanelCollapsed = !sidePanelCollapsed;
        });

        int y = 10;
        if (sidePanelCollapsed) return; // Only show title if not collapsed

        g.drawString(font, trunc(definition.getLangValue(), pw - 20),
                px + 10, y, C_ACCENT(), false);
        y += 20;

        boolean hasCoilBlocks = pattern != null && pattern.blockMap.values().stream()
                .anyMatch(i -> i.getBlockState().getBlock() instanceof com.gregtechceu.gtceu.common.block.CoilBlock);
        boolean hasRealSizeVariants = computeHasRealSizeVariants();
        boolean isCoilTierMachine = hasCoilBlocks && !hasRealSizeVariants;

        if (isCoilTierMachine) {
            String cn = COIL_TIERS.get(coilIndex).getBlockState().getBlock()
                    .getName().getString();
            regBtn(g, mx, my, px + 10, y, pw - 20, 16, "Coil: " + cn, () -> {
                coilIndex = (coilIndex + 1) % COIL_TIERS.size();
                updateCoilType();
            });
            y += 20;
        }

        if (hasRealSizeVariants) {
            regBtn(g, mx, my, px + 10, y, pw - 20, 16,
                    "Structure Size: " + (shapeIndex + 1), () -> {
                        // Advance to the next shape and rebuild the renderer + pattern.
                        shapeIndex = (shapeIndex + 1) % availableShapes.size();
                        if (renderer != null) {
                            renderer.close();
                            renderer = null;
                        }
                        pattern = null;
                        invalidateFilterSets();
                        if (camera != null) camera.clearSnapshot();
                        if (camera != null) camera.clearPlayerOwned();
                        init();
                    });
            y += 20;
        }
        y += 5;

        int bW = 20, lW = pw - 60, lX = px + 30;

        g.drawString(font, "Show:", px + 10, y, C_DIM(), false);
        y += 12;
        ViewFilter[] vfs = ViewFilter.values();
        int fw = (pw - 25) / 2;
        for (int i = 0; i < vfs.length; i++) {
            final ViewFilter vf = vfs[i];
            int bx = (i % 2 == 0) ? px + 10 : px + 15 + fw;
            regBtn(g, mx, my, bx, y, fw, 14, vf.name(), viewFilter == vf,
                    () -> toggleViewFilter(vf));
            if (i % 2 != 0 || i == vfs.length - 1) y += 17;
        }

        y += 8;
        if (script != null && script.hasCommonMistakes()) {
            regIconBtn(g, mx, my, px + 10, y, pw - 20, 16, "⚠", "Common Mistakes",
                    showMistakes, () -> showMistakes = !showMistakes);
            y += 20;
        }

        // ── Layer navigation (only shown when NOT in build mode) ──────────────
        if (!buildOrderMode && pattern != null) {
            g.drawString(font, "Layer:", px + 10, y + 4, C_DIM(), false);
            String layerLabel = manualLayer >= 0 ? "Y=" + manualLayer : "All";
            // ◀ button
            regBtn(g, mx, my, px + 10, y + 14, bW, 14, "◀",
                    () -> nudgeLayer(-1));
            // Layer display (centered)
            g.drawCenteredString(font, layerLabel, lX + lW / 2, y + 17, C_ACCENT());
            // ▶ button
            regBtn(g, mx, my, lX + lW + 2, y + 14, bW, 14, "▶",
                    () -> nudgeLayer(1));
            // Reset to "All" button
            if (manualLayer >= 0) {
                regBtn(g, mx, my, px + 10, y + 31, pw - 20, 12, "Show All Layers",
                        () -> {
                            manualLayer = -1;
                            applyVisibility();
                        });
                y += 46;
            } else {
                y += 32;
            }
        }

        // ── Build-order step buttons (only shown when IN build mode) ──────────
        if (buildOrderMode && pattern != null) {
            int totalGroups = pattern.buildOrder.size();
            g.drawString(font, "Build Step:", px + 10, y + 4, C_DIM(), false);
            String stepLabel = (buildOrderGroup + 1) + " / " + totalGroups;
            regBtn(g, mx, my, px + 10, y + 14, bW, 14, "◀",
                    () -> buildOrderStep(-1));
            g.drawCenteredString(font, stepLabel, lX + lW / 2, y + 17, C_ACCENT());
            regBtn(g, mx, my, lX + lW + 2, y + 14, bW, 14, "▶",
                    () -> buildOrderStep(1));
            y += 32;
        }
        regIconBtn(g, mx, my, px + 10, y, pw - 20, 16, "🧱", "Build Mode",
                buildOrderMode, () -> {
                    buildOrderMode = !buildOrderMode;
                    if (buildOrderMode) {
                        // Entering build mode: save and clear any active layer filter
                        // so build mode always starts from group 0 with a clean slate.
                        savedManualLayer = manualLayer;
                        manualLayer = -1;
                        buildOrderGroup = 0;
                    } else {
                        // Leaving build mode: restore the layer selection (or -1 = all).
                        manualLayer = savedManualLayer;
                    }
                    applyVisibility();
                });
        y += 20;
        regIconBtn(g, mx, my, px + 10, y, pw - 20, 16, "🗺", "Footprint",
                false, this::openFootprintScreen);
        y += 20;
        regIconBtn(g, mx, my, px + 10, y, pw - 20, 16, "⊕", "Center Camera",
                false, this::centerCamera);
        y += 20;
        regIconBtn(g, mx, my, px + 10, y, pw - 20, 16, "🔍", "Block List",
                false, this::openBlockFilterScreen);
        y += 20;

        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getAbilities().instabuild) {
            regIconBtn(g, mx, my, px + 10, y, pw - 20, 16, "✏", "Edit Script",
                    false, this::openScriptEditor);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Input
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        for (PhantasiaUIUtils.ButtonAction b : activeButtons) {
            if (b.hit(mx, my)) {
                b.action().run();
                return true;
            }
        }

        int px = this.width - getCurrentPanelWidth();
        int tlY = this.height - TIMELINE_H;

        if (btn == 0 && my >= tlY && mx < px && !buildOrderMode) {
            int tx = 80, tw = px - tx - 65;
            if (mx >= tx && mx <= tx + tw) {
                playing = false;
                scrubbing = true;
                scrubTo((float) (mx - tx) / tw);
                return true;
            }
        }

        if (mx < px && my > CAPTION_STRIP_H && my < tlY) {
            if (btn == 1 && hoveredPos != null && SHARED_LEVEL != null) {
                try {
                    if (!SHARED_LEVEL.getBlockState(hoveredPos).isAir()) {
                        if (camera != null) camera.save();
                        Minecraft.getInstance().setScreen(
                                new PhantasiaBlockInspectScreen(hoveredPos, pattern, this));
                        return true;
                    }
                } catch (Exception ignored) {}
            }
            if (btn == 2 && camera != null && !camera.isLocked()) {
                isPanning = true;
                return true;
            }
            if (btn == 0) return true;
        }

        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        int px = this.width - getCurrentPanelWidth();
        int tlY = this.height - TIMELINE_H;
        if (mx >= px || my >= tlY || camera == null || camera.isLocked())
            return super.mouseDragged(mx, my, btn, dx, dy);

        if (scrubbing && btn == 0) {
            scrubTo(Mth.clamp((float) (mx - 80) / (px - 80 - 65), 0f, 1f));
            return true;
        }

        if (btn == 2 && isPanning) {
            Vector3f right = new Vector3f(), up = new Vector3f();
            camera.getRightAndUp(right, up);
            float s = CAM_PAN_SPEED;
            camera.pan(
                    (right.x * (float) -dx + up.x * (float) dy) * s,
                    (right.y * (float) -dx + up.y * (float) dy) * s,
                    (right.z * (float) -dx + up.z * (float) dy) * s);
            return true;
        }

        if (btn == 0) {
            // Cache current focal values to freeze them during rotation
            float originalZoom = camera.getZoom();
            float targetX = camera.getTargetX();
            float targetY = camera.getTargetY();
            float targetZ = camera.getTargetZ();

            // Perform pure rotation
            camera.orbit((float) dx * CAM_ORBIT_SENSITIVITY,
                    (float) dy * CAM_ORBIT_SENSITIVITY);

            // Re-bind to the fixed pivot point and distance
            camera.setTarget(targetX, targetY, targetZ);
            camera.setPosition(camera.getYaw(), camera.getPitch(), originalZoom);
            return true;
        }

        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= this.width - getCurrentPanelWidth()) return false;
        if (camera == null || camera.isLocked()) return false;
        camera.zoom(delta > 0 ? CAM_ZOOM_IN_FACTOR : CAM_ZOOM_OUT_FACTOR,
                CAM_ZOOM_MIN, CAM_ZOOM_MAX);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 2 || btn == 0) isPanning = false;
        if (scrubbing) {
            int px = this.width - getCurrentPanelWidth();
            scrubTo(Mth.clamp((float) (mx - 80) / (px - 80 - 65), 0f, 1f));
            scrubbing = false;
            applyVisibility();
        }
        return super.mouseReleased(mx, my, btn);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Actions
    // ─────────────────────────────────────────────────────────────────────────

    private void toggleViewFilter(ViewFilter vf) {
        if (viewFilter == vf) {
            viewFilter = ViewFilter.ALL;
            if (wasPlayingBeforeFilter) playing = true;
            wasPlayingBeforeFilter = false;
        } else {
            if (viewFilter == ViewFilter.ALL) {
                wasPlayingBeforeFilter = playing;
                playing = false;
            }
            viewFilter = vf;
        }
        applyVisibility();
    }

    private void centerCamera() {
        if (camera == null || pattern == null) return;

        // 1. Resolve where the camera is *going* to go
        float[] defaultTarget = resolveTarget();

        // 2. Calculate the Euclidean distance from where the camera is *currently* looking
        float dx = camera.getTargetX() - defaultTarget[0];
        float dy = camera.getTargetY() - defaultTarget[1];
        float dz = camera.getTargetZ() - defaultTarget[2];
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // 3. Scale the ticks dynamically using Minecraft's Mth utility.
        // Small jump (e.g., distance ~0) = 12 ticks. Max jump caps out at 26 ticks.
        int dynamicTicks = (int) Mth.clamp(12 + (distance * 0.4), 12, 26);

        resetCameraToDefault(LerpType.EASE_OUT, dynamicTicks);
    }

    private void nudgeLayer(int delta) {
        if (pattern == null) return;
        if (manualLayer < 0) {
            // FIX (bug 3): initialise to the boundary layer on first nudge,
            // then always clamp to [minY, maxY] so the set is never empty.
            manualLayer = (delta < 0) ? pattern.maxY : pattern.minY;
        } else {
            manualLayer = Mth.clamp(manualLayer + delta, pattern.minY, pattern.maxY);
        }
        applyVisibility();
    }

    private void buildOrderStep(int delta) {
        if (pattern == null) return;
        buildOrderGroup = Mth.clamp(buildOrderGroup + delta, 0,
                pattern.buildOrder.size() - 1);
        applyVisibility();
    }

    private void scrubTo(float t) {
        scrubbing = true;
        playing = false;
        playbackTick = (int) (Mth.clamp(t, 0f, 1f) * script.getTotalTicks());
        PhantasiaScript.Step step = script.getActiveStep(playbackTick);
        if (step != lastAppliedStep) {
            lastAppliedStep = step;
            updateCaptionForStep(step);
            applyVisibility();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sub-screen navigation
    // ─────────────────────────────────────────────────────────────────────────

    private void openScriptEditor() {
        if (camera != null) camera.save();
        String machineId = definition.getId().toString();
        PhantasiaScriptData current = script.getSourceData();
        if (current == null) current = PhantasiaScriptData.defaultFor(machineId);
        Minecraft.getInstance().setScreen(
                new PhantasiaScriptEditorScreen(this, machineId, current));
    }

    private void openFootprintScreen() {
        if (pattern == null) return;
        if (camera != null) camera.save();
        Minecraft.getInstance().setScreen(
                new PhantasiaFootprintScreen(pattern, this, script));
    }

    private void openBlockFilterScreen() {
        if (pattern == null) return;
        if (camera != null) camera.save();
        Minecraft.getInstance().setScreen(
                new PhantasiaBlockFilterScreen(pattern, script, viewFilter, this));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Accessors
    // ─────────────────────────────────────────────────────────────────────────

    public float getRotationYaw() {
        return camera != null ? camera.getYaw() : 0f;
    }

    public float getRotationPitch() {
        return camera != null ? camera.getPitch() : 0f;
    }

    public PhantasiaLoadedPattern getLoadedPattern() {
        return pattern;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Button helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void regBtn(GuiGraphics g, int mx, int my,
                        int x, int y, int w, int h, String label, Runnable action) {
        boolean hov = isOver(mx, my, x, y, w, h);
        PhantasiaThemeUtils.drawThemedBtn(g, font, x, y, w, h, label, hov, C_BTN());
        activeButtons.add(new PhantasiaUIUtils.ButtonAction(x, y, w, h, action));
    }

    private void regBtn(GuiGraphics g, int mx, int my,
                        int x, int y, int w, int h,
                        String label, boolean active, Runnable action) {
        boolean hov = isOver(mx, my, x, y, w, h);
        PhantasiaThemeUtils.drawThemedBtn(g, font, x, y, w, h, label, hov,
                active ? C_BTN_ACT() : C_BTN());
        activeButtons.add(new PhantasiaUIUtils.ButtonAction(x, y, w, h, action));
    }

    private void regIconBtn(GuiGraphics g, int mx, int my,
                            int x, int y, int w, int h,
                            String icon, String label, boolean active, Runnable action) {
        boolean hov = isOver(mx, my, x, y, w, h);
        PhantasiaThemeUtils.drawIconBtn(g, font, x, y, w, h, icon, label, hov,
                active ? C_BTN_ACT() : C_BTN());
        activeButtons.add(new PhantasiaUIUtils.ButtonAction(x, y, w, h, action));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Misc
    // ─────────────────────────────────────────────────────────────────────────

    private int getCurrentPanelWidth() {
        return sidePanelCollapsed ? COLLAPSED_PANEL_W : FULL_PANEL_W;
    }

    private boolean isOver(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private String trunc(String s, int maxPx) {
        if (s == null) return "";
        while (font.width(s) > maxPx && s.length() > 2)
            s = s.substring(0, s.length() - 2) + "\u2026";
        return s;
    }

    private static String formatTicks(int t) {
        return String.format("%d.%02ds", t / 20, (t % 20) * 5);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onClose() {
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        net.phoenixvine.phantasia.client.render.PhantasiaParticleEngine.destroy();
        machineWorking = false;
        invalidateSharedLevel();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
