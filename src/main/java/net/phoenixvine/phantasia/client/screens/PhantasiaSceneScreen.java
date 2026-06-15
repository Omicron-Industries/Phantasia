package net.phoenixvine.phantasia.client.screens;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
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
import net.phoenixvine.phantasia.client.screens.editors.PhantasiaScriptEditorScreen;
import net.phoenixvine.phantasia.client.screens.subscreen.*;
import net.phoenixvine.phantasia.common.data.pattern.PhantasiaLoadedPattern;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaScenes;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScript;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScripts;
import net.phoenixvine.phantasia.common.data.variant.PhantasiaVariantGroup;
import net.phoenixvine.phantasia.common.data.variant.PhantasiaVariantState;
import net.phoenixvine.phantasia.common.world.PhantasiaDimension;
import net.phoenixvine.phantasia.common.world.PhantasiaSlotAllocator;
import net.phoenixvine.phantasia.common.world.PhantasiaSlotVersions;
import net.phoenixvine.phantasia.integration.emi.PhantasiaEmiPlugin;
import net.phoenixvine.phantasia.utils.PhantasiaThemeUtils;
import net.phoenixvine.phantasia.utils.PhantasiaUIUtils;

import com.mojang.blaze3d.systems.RenderSystem;
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
    private static final int CAPTION_STRIP_H = 38;

    private final java.util.Map<net.minecraft.core.BlockPos, PhantasiaVariantGroup> positionToVariantGroupCache = new java.util.HashMap<>();
    private boolean cacheInitialized = false;

    private static final long SCREEN_PROFILE_WINDOW_NS = 5_000_000_000L; // Profile every 5 seconds
    private long screenProfileWindowStart = -1;
    private int screenProfiledFramesCount = 0;
    private long totalScreenRenderTimeNs = 0;
    private long maxScreenSpikeNs = 0;
    private long totalVariantLookupTimeNs = 0;

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
    public static final float CAM_PAN_SPEED = 0.02f;

    // ─────────────────────────────────────────────────────────────────────────
    // Core state
    // ─────────────────────────────────────────────────────────────────────────

    private final Screen parent;
    public final MultiblockMachineDefinition definition;
    public PhantasiaScript script;

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

    private List<BlockInfo> coilTiers = new ArrayList<>();

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
    // Pattern loading (Fully Fake World Schema approach)
    // ─────────────────────────────────────────────────────────────────────────

    private PhantasiaLoadedPattern loadPattern(MultiblockShapeInfo shape) {
        ResourceLocation machineId = definition.getId();
        // Stable per-machine slot in the Phantasia scene dimension (used for persistence only).
        BlockPos slotOrigin = PhantasiaSlotAllocator.originFor(machineId);
        // Fixed local origin used for SHARED_LEVEL block placement. Always near (0,0,0)
        // so baked VBO vertices are small floats — preserving the float32 precision that
        // GT's 0.001-block overlay offset requires to avoid z-fighting.
        BlockPos renderOrigin = PhantasiaSlotAllocator.RENDER_ORIGIN;

        int shapeHash = PhantasiaSlotVersions.hashShape(shape.getBlocks());
        int scriptHash = PhantasiaSlotVersions.hashScript(script.getSourceData());

        // isValid() checks hash stamps AND that the dimension chunk actually exists
        // on disk, so we never warm-start against a missing or freshly wiped region.
        boolean warm = PhantasiaSlotVersions.isValid(machineId, shapeHash, scriptHash, slotOrigin);

        if (warm) {
            net.phoenixvine.phantasia.Phantasia.LOGGER.info(
                    "[Phantasia] Warm load for {} at slot={} render={}", machineId, slotOrigin, renderOrigin);
            return loadPatternWarm(shape, renderOrigin);
        } else {
            net.phoenixvine.phantasia.Phantasia.LOGGER.info(
                    "[Phantasia] Cold load for {} at slot={} render={}", machineId, slotOrigin, renderOrigin);
            PhantasiaLoadedPattern result = loadPatternCold(shape, renderOrigin, slotOrigin);
            PhantasiaSlotVersions.put(machineId, shapeHash, scriptHash);
            PhantasiaDimension.forceChunkLoad(slotOrigin);
            return result;
        }
    }

    /**
     * Cold load: places all blocks into SHARED_LEVEL at {@code renderOrigin} (near 0,0,0),
     * dynamically spins up Schema entities, then writes them to the Phantasia dimension at
     * {@code slotOrigin} for persistence.
     */
    private PhantasiaLoadedPattern loadPatternCold(MultiblockShapeInfo shape, BlockPos renderOrigin,
                                                   BlockPos slotOrigin) {
        SHARED_LEVEL.renderedBlocks.clear();
        SHARED_LEVEL.blockEntities.clear();

        BlockInfo[][][] raw = shape.getBlocks();
        Map<BlockPos, BlockInfo> blockMap = new HashMap<>();
        Map<BlockPos, BlockPos> localToWorld = new HashMap<>();
        Set<BlockPos> baseplatePos = new HashSet<>();
        Set<BlockPos> bePos = new HashSet<>();
        BlockPos controllerWP = null;
        MultiblockControllerMachine controller = null;
        List<IMultiPart> parts = new ArrayList<>();

        BlockInfo floor = BlockInfo.fromBlockState(Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        int sxLen = raw.length;
        int szLen = sxLen > 0 && raw[0].length > 0 ? raw[0][0].length : 0;
        int padX = Math.max(2, sxLen / 2 + 1);
        int padZ = Math.max(2, szLen / 2 + 1);

        for (int bx = -padX; bx <= sxLen + padX; bx++)
            for (int bz = -padZ; bz <= szLen + padZ; bz++) {
                BlockPos wp = renderOrigin.offset(bx, -1, bz);
                blockMap.put(wp, floor);
                baseplatePos.add(wp);

                // FIX NADA ELSE: Map the baseplate into the dummy world so the renderer registers it!
                SHARED_LEVEL.setBlock(wp, floor.getBlockState(), 3);
            }

        for (int x = 0; x < raw.length; x++)
            for (int y = 0; y < raw[x].length; y++)
                for (int z = 0; z < raw[x][y].length; z++) {
                    BlockInfo info = raw[x][y][z];
                    if (info == null) continue;
                    BlockPos lp = new BlockPos(x, y, z);
                    BlockPos wp = renderOrigin.offset(x, y, z);

                    blockMap.put(wp, info);
                    localToWorld.put(lp, wp);

                    BlockState state = info.getBlockState();
                    SHARED_LEVEL.setBlock(wp, state, 3);

                    if (state.getBlock() instanceof EntityBlock entityBlock) {
                        BlockEntity newEntity = entityBlock.newBlockEntity(wp, state);
                        if (newEntity != null) {
                            SHARED_LEVEL.setInnerBlockEntity(newEntity);
                            if (newEntity instanceof MetaMachineBlockEntity mmbe) {
                                mmbe.setLevel(SHARED_LEVEL);
                                var machine = mmbe.getMetaMachine();
                                if (machine instanceof MultiblockControllerMachine ctrl && controllerWP == null) {
                                    controller = ctrl;
                                    controllerWP = wp;
                                } else if (machine instanceof IMultiPart part) {
                                    parts.add(part);
                                }
                            }
                            bePos.add(wp);
                        }
                    }
                }

        // Persist to the Phantasia dimension at slot-space coords for warm-start.
        // Build a slot-space copy by re-offsetting each render-local position.
        Map<BlockPos, BlockInfo> slotSpaceMap = new HashMap<>(blockMap.size());
        for (Map.Entry<BlockPos, BlockInfo> e : blockMap.entrySet()) {
            // Convert render-local wp back to local offset, then apply slotOrigin.
            BlockPos localOffset = e.getKey().subtract(renderOrigin);
            slotSpaceMap.put(slotOrigin.offset(localOffset), e.getValue());
        }
        coldPopulateDimensionSlot(definition.getId(), slotSpaceMap);

        return finalisePattern(raw, blockMap, localToWorld, baseplatePos, bePos,
                controllerWP, controller, renderOrigin, parts);
    }

    /**
     * Warm load: block states are already in SHARED_LEVEL from a previous session.
     * Skips the shape.getBlocks() iteration and SHARED_LEVEL.addBlocks(); only
     * rebuilds the index maps and re-registers BEs dynamically utilizing the Schema logic.
     */
    private PhantasiaLoadedPattern loadPatternWarm(MultiblockShapeInfo shape, BlockPos renderOrigin) {
        SHARED_LEVEL.blockEntities.clear();

        BlockInfo[][][] raw = shape.getBlocks();
        Map<BlockPos, BlockInfo> blockMap = new HashMap<>();
        Map<BlockPos, BlockPos> localToWorld = new HashMap<>();
        Set<BlockPos> baseplatePos = new HashSet<>();
        Set<BlockPos> bePos = new HashSet<>();
        BlockPos controllerWP = null;
        MultiblockControllerMachine controller = null;
        List<IMultiPart> parts = new ArrayList<>();

        BlockInfo floor = BlockInfo.fromBlockState(Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        int sxLen = raw.length;
        int szLen = sxLen > 0 && raw[0].length > 0 ? raw[0][0].length : 0;
        int padX = Math.max(2, sxLen / 2 + 1);
        int padZ = Math.max(2, szLen / 2 + 1);

        for (int bx = -padX; bx <= sxLen + padX; bx++)
            for (int bz = -padZ; bz <= szLen + padZ; bz++) {
                BlockPos wp = renderOrigin.offset(bx, -1, bz);
                blockMap.put(wp, floor);
                baseplatePos.add(wp);
            }

        for (int x = 0; x < raw.length; x++)
            for (int y = 0; y < raw[x].length; y++)
                for (int z = 0; z < raw[x][y].length; z++) {
                    BlockInfo info = raw[x][y][z];
                    if (info == null) continue;
                    BlockPos lp = new BlockPos(x, y, z);
                    BlockPos wp = renderOrigin.offset(x, y, z);

                    blockMap.put(wp, info);
                    localToWorld.put(lp, wp);

                    BlockState state = SHARED_LEVEL.getBlockState(wp);
                    if (state.getBlock() instanceof EntityBlock entityBlock) {
                        BlockEntity newEntity = entityBlock.newBlockEntity(wp, state);
                        if (newEntity != null) {
                            SHARED_LEVEL.setInnerBlockEntity(newEntity);
                            if (newEntity instanceof MetaMachineBlockEntity mmbe) {
                                mmbe.setLevel(SHARED_LEVEL);
                                var machine = mmbe.getMetaMachine();
                                if (machine instanceof MultiblockControllerMachine ctrl && controllerWP == null) {
                                    controller = ctrl;
                                    controllerWP = wp;
                                } else if (machine instanceof IMultiPart part) {
                                    parts.add(part);
                                }
                            }
                            bePos.add(wp);
                        }
                    }
                }

        return finalisePattern(raw, blockMap, localToWorld, baseplatePos, bePos,
                controllerWP, controller, renderOrigin, parts);
    }

    /**
     * Writes the blocks in {@code blockMap} to the Phantasia scene dimension so
     * that subsequent screen opens can skip the shape.getBlocks() iteration
     * (warm-start). Also called during world-load pre-population.
     *
     * <p>
     * The write is submitted to the server thread asynchronously so the
     * calling render-thread open is not blocked on disk I/O.
     * </p>
     */
    public static void coldPopulateDimensionSlot(ResourceLocation machineId,
                                                 Map<BlockPos, BlockInfo> blockMap) {
        var sl = PhantasiaDimension.getServerLevel();
        if (sl == null) return;
        try {
            net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks
                    .getCurrentServer();
            if (server == null) return;
            server.submit(() -> {
                for (Map.Entry<BlockPos, BlockInfo> e : blockMap.entrySet()) {
                    try {
                        BlockState state = e.getValue().getBlockState();
                        if (state != null && !state.isAir()) {
                            sl.setBlock(e.getKey(), state,
                                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS |
                                            net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE);
                        }
                    } catch (Exception ignored) {}
                }
                net.phoenixvine.phantasia.Phantasia.LOGGER.debug(
                        "[Phantasia] Wrote {} blocks to scene dimension for {}",
                        blockMap.size(), machineId);
            });
        } catch (Exception e) {
            net.phoenixvine.phantasia.Phantasia.LOGGER.warn(
                    "[Phantasia] Could not submit dimension write task: {}", e.getMessage());
        }
    }

    /**
     * Common tail of both load paths: applies GTCEu exact multiblock structural logic natively,
     * computes Y extents, and constructs the PhantasiaLoadedPattern.
     */
    private PhantasiaLoadedPattern finalisePattern(
                                                   BlockInfo[][][] raw,
                                                   Map<BlockPos, BlockInfo> blockMap,
                                                   Map<BlockPos, BlockPos> localToWorld,
                                                   Set<BlockPos> baseplatePos,
                                                   Set<BlockPos> bePos,
                                                   BlockPos controllerWP,
                                                   MultiblockControllerMachine controller,
                                                   BlockPos origin,
                                                   List<IMultiPart> parts) {
        net.phoenixvine.phantasia.Phantasia.LOGGER.info(
                "[Phantasia] Registered {} block entities with SHARED_LEVEL", bePos.size());

        if (controller != null) {
            try {
                var mState = controller.getMultiblockState();
                if (mState != null) {
                    mState.setError(null);

                    // FIXED: Changed .put() to .set() to match your PatternMatchContext API
                    mState.getMatchContext().set("parts", new java.util.HashSet<>(parts));
                }

                controller.getPatternLock().lock();
                try {
                    controller.onStructureFormed();
                } finally {
                    controller.getPatternLock().unlock();
                }

                net.phoenixvine.phantasia.Phantasia.LOGGER
                        .info("[Phantasia] onStructureFormed fully simulated via fake world!");
            } catch (Exception e) {
                net.phoenixvine.phantasia.Phantasia.LOGGER.error(
                        "[Phantasia] formStructure failed: {}", e.getMessage(), e);
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

    @Override
    protected void init() {
        super.init();

        // Invalidate our high-performance zero-allocation cache on screen re-initialization
        this.cacheInitialized = false;
        org.slf4j.LoggerFactory.getLogger("PhantasiaScreenCache")
                .info("[Phantasia] Screen init triggered: Invalidating variant lookup cache maps.");

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

            // Restore the persisted working state to SHARED_LEVEL immediately after
            // pattern load. Pattern load writes fresh default states (ACTIVE=false).
            applyActiveStateToWorld(machineWorking);

            // Compile variant groups now that the pattern is loaded.
            script = script.withVariants(definition, pattern, availableShapes);
            PhantasiaVariantState vs = PhantasiaVariantState.get();
            vs.loadGroups(script.getVariantGroups());

            vs.setOnChangeCallback(() -> {
                if (renderer == null || pattern == null) return;

                // Hotfix/Optimization Hook: A variant selection changed! We must invalidate
                // the cache map so it rebuilds the updated positional variant mappings.
                this.cacheInitialized = false;

                Set<BlockPos> variantPositions = buildVariantWorldPositions(vs);
                if (variantPositions.isEmpty()) {
                    renderer.requestBake();
                } else {
                    renderer.requestPartialBake(variantPositions);
                }
            });
        }

        // ── Renderer ──────────────────────────────────────────────────────────
        if (renderer == null) {
            renderer = new PhantasiaWorldRenderer(SHARED_LEVEL);
        }
        if (pattern != null) {
            renderer.setBaseplatePositions(pattern.baseplatePositions);
            renderer.setControllerWorldPos(pattern.controllerWorldPos);

            // Tell the renderer the full set of pattern blocks (validity for bake targeting).
            Set<BlockPos> fullBakeSet = new HashSet<>();
            fullBakeSet.addAll(pattern.blockMap.keySet());
            fullBakeSet.addAll(pattern.baseplatePositions);
            renderer.setPatternBlocks(fullBakeSet);

            renderer.requestBake();
        }

        // Initialise the isolated particle engine for this scene.
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

        // ── Synchronize Layer Visibility ──────────────────────────────────────
        if (pattern != null) {
            applyVisibility();

            // ── PERFORMANCE OPTIMIZATION: Populate Ambient Ticking Cache ──
            this.ambientTickingPositions.clear();
            for (net.minecraft.core.BlockPos wp : pattern.blockMap.keySet()) {
                net.minecraft.world.level.block.state.BlockState state = SHARED_LEVEL.getBlockState(wp);
                if (!state.isAir()) {
                    this.ambientTickingPositions.add(wp);
                }
            }
        }

        // Trigger an immediate proactive populate step on startup to verify cache alignment
        ensureVariantCachePopulated();
        org.slf4j.LoggerFactory.getLogger("PhantasiaScreenCache").info(String.format(
                "[Phantasia] Cache initialization finalized successfully. Pre-cached %d variant blocks for O(1) rendering lookups.",
                this.positionToVariantGroupCache.size()));
    }

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
                long gameTick = net.minecraft.client.Minecraft.getInstance().level != null ?
                        net.minecraft.client.Minecraft.getInstance().level.getGameTime() : 0;
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

        boolean stateChanged = false;
        if (pattern.controller instanceof WorkableMultiblockMachine w) {
            RecipeLogic logic = w.getRecipeLogic();
            boolean wasWorking = (logic.getStatus() == RecipeLogic.Status.WORKING);
            if (wasWorking != working) {
                logic.setStatus(working ? RecipeLogic.Status.WORKING : RecipeLogic.Status.IDLE);
                stateChanged = true;
            }

            String rid = (step != null && working) ? step.fakeRecipeId() : null;
            if (rid != null && !rid.isBlank()) {
                injectFakeRecipe(logic, rid.trim());
            } else if (!working && logic.getLastRecipe() != null) {
                logic.resetRecipeLogic();
            }
        }

        // Collect positions whose ACTIVE state will change. Only these need a
        // partial rebake — the rest of the structure geometry is unchanged.
        var activeProp = com.gregtechceu.gtceu.api.block.property.GTBlockStateProperties.ACTIVE;
        Set<BlockPos> activeChangedPositions = new HashSet<>();
        if (stateChanged && SHARED_LEVEL != null && pattern.blockMap != null) {
            if (pattern.controllerWorldPos != null)
                activeChangedPositions.add(pattern.controllerWorldPos);
            for (BlockPos wp : pattern.blockMap.keySet()) {
                try {
                    if (SHARED_LEVEL.getBlockState(wp).hasProperty(activeProp))
                        activeChangedPositions.add(wp);
                } catch (Exception ignored) {}
            }
        }

        // Persist working state statically so reinit (subscreen return) can restore
        // it without recalculating from playbackTick=0 on the new instance.
        machineWorking = working;
        applyActiveStateToWorld(working);

        // Trigger a targeted rebake for only the blocks whose geometry changed.
        if (renderer != null && stateChanged) {
            if (!activeChangedPositions.isEmpty()) {
                renderer.requestPartialBake(activeChangedPositions);
            } else {
                renderer.requestBake();
            }
        }

        var rs = pattern.controller.getRenderState();
        var ap = com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties.IS_ACTIVE;
        if (rs.hasProperty(ap) && rs.getValue(ap) != working) {
            pattern.controller.setRenderState(rs.setValue(ap, working));
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

    // ─────────────────────────────────────────────────────────────────────────
    // render()
    // ─────────────────────────────────────────────────────────────────────────

    private void ensureVariantCachePopulated() {
        if (cacheInitialized || script == null) return;
        positionToVariantGroupCache.clear();

        for (var group : script.getVariantGroups()) {
            if (group == null || group.getPositionBaseIndex() == null) continue;
            for (net.minecraft.core.BlockPos pos : group.getPositionBaseIndex().keySet()) {
                if (pos != null) {
                    positionToVariantGroupCache.put(pos, group);
                }
            }
        }
        cacheInitialized = true;
    }

    private void dumpScreenProfilingMetrics() {
        if (screenProfiledFramesCount == 0) return;

        double toMs = 1_000_000.0;
        double avgScreenTotal = (totalScreenRenderTimeNs / (double) screenProfiledFramesCount) / toMs;
        double maxScreenSpike = maxScreenSpikeNs / toMs;
        double avgLookupTime = (totalVariantLookupTimeNs / (double) screenProfiledFramesCount) / toMs;

        // Using standard logger matching your renderer structure
        net.minecraft.client.Minecraft.getInstance().getReportingContext();
        org.slf4j.LoggerFactory.getLogger("PhantasiaScreenProfiler").info(String.format(
                "\n======= [PHANTASIA UI SCREEN PROFILER] =======\n" +
                        " * Screen Frames Tracked:   %d\n" +
                        " * Avg Screen Loop Render: %.3f ms  (Max Frame Spike: %.3f ms)\n" +
                        " ─── Dynamic Layout Diagnostics ───\n" +
                        "   -> Flattened Variant Lookup: %.4f ms (Zero-Allocation O(1))\n" +
                        "   -> Registered Screen Elements Cache Size: %d entries\n" +
                        "===================================================",
                screenProfiledFramesCount, avgScreenTotal, maxScreenSpike, avgLookupTime,
                positionToVariantGroupCache.size()));

        // Reset trackers cleanly
        screenProfileWindowStart = System.nanoTime();
        screenProfiledFramesCount = 0;
        totalScreenRenderTimeNs = 0;
        maxScreenSpikeNs = 0;
        totalVariantLookupTimeNs = 0;
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        long screenRenderStart = System.nanoTime();
        if (screenProfileWindowStart == -1) {
            screenProfileWindowStart = screenRenderStart;
        }

        activeButtons.clear();
        ensureVariantCachePopulated();

        // ── FIX EMBEDDIUM CONTROLLER SHADER ANIMATION FREEZE ──
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

            // ── FIX: Apply viewport offset context to mouse coords handed down to the FBO color-pick pass ──
            renderer.setMousePos(mx, my);
            renderer.render(view, 0, CAPTION_STRIP_H, sw, sh);

            BlockHitResult hit = renderer.getLastHitResult();
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos hp = hit.getBlockPos();

                // ── FIX: Ensure baseplate blocks are recognized as valid elements to hover over ──
                boolean isBaseplate = pattern != null && pattern.baseplatePositions.contains(hp);
                hoveredPos = (renderer.isVisible(hp) || isBaseplate) ? hp : null;
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
        renderScriptItemPanel(g, mx, my);
        regBtn(g, mx, my, 10, 10, 50, 18, "Back", this::onClose);

        super.render(g, mx, my, partial);

        int px = this.width - pw;
        if (hoveredPos != null && SHARED_LEVEL != null) {
            long lookupStart = System.nanoTime();
            try {
                BlockState st = null;
                if (script != null) {
                    // Fully flattened O(1) cache lookup—completely bypassing nested iterator loop allocation!
                    var group = positionToVariantGroupCache.get(hoveredPos);
                    if (group != null) {
                        int activeSel = PhantasiaVariantState.get()
                                .getSelection(group.getId());
                        if (activeSel >= 0 && activeSel < group.getOptions().size()) {
                            st = group.getOptions().get(activeSel);
                        }
                    }
                }

                // Fallback to the base level state if it's not a variant block
                if (st == null) {
                    st = SHARED_LEVEL.getBlockState(hoveredPos);
                }

                if (!st.isAir()) {
                    g.drawString(font, trunc(st.getBlock().getName().getString(), pw - 20),
                            px + 10, this.height - 20, C_DIM(), false);
                    if (mx < px) g.renderTooltip(font, st.getBlock().getName(), mx, my);
                }
            } catch (Exception ignored) {} finally {
                totalVariantLookupTimeNs += (System.nanoTime() - lookupStart);
            }
        }

        // Record screen metrics tracking metrics
        long frameDurationNs = System.nanoTime() - screenRenderStart;
        totalScreenRenderTimeNs += frameDurationNs;
        maxScreenSpikeNs = Math.max(maxScreenSpikeNs, frameDurationNs);
        screenProfiledFramesCount++;

        if (System.nanoTime() - screenProfileWindowStart >= SCREEN_PROFILE_WINDOW_NS) {
            dumpScreenProfilingMetrics();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Timeline
    // ─────────────────────────────────────────────────────────────────────────

    // ── Script item panel ─────────────────────────────────────────────────────

    private static final int SIP_W = 186;
    private static final int SIP_ROW = 38;
    private static final int SIP_ICON = 28;
    private static final int SIP_HOV = 36;

    /**
     * Renders the GuideME-style item panel for the current script step.
     * Docked to the right, above the timeline. Only shown when the active
     * step has items and {@code showItems == true}.
     */
    private void renderScriptItemPanel(GuiGraphics g, int mx, int my) {
        if (script == null) return;
        PhantasiaScriptData sd = script.getSourceData();
        if (sd == null) return;

        int stepIdx = script.getActiveStepIndex(playbackTick);
        if (stepIdx < 0 || stepIdx >= sd.getSteps().size()) return;
        PhantasiaScriptData.StepData step = sd.getSteps().get(stepIdx);
        if (!step.showItems || step.items.isEmpty()) return;

        int pw = getCurrentPanelWidth();
        int panelX = this.width - pw - SIP_W - 4;
        int panelY = CAPTION_STRIP_H + 4;
        int panelH = step.items.size() * SIP_ROW + 8;

        // Clamp so it doesn't go off-screen on narrow displays
        if (panelX < 4) panelX = 4;

        g.fill(panelX, panelY, panelX + SIP_W, panelY + panelH, 0xDD070712);
        g.fill(panelX, panelY, panelX + SIP_W, panelY + 1, C_ACCENT());
        g.fill(panelX, panelY, panelX + 1, panelY + panelH, 0x554FC3F7);

        int ry = panelY + 4;
        for (PhantasiaSceneData.ItemConditionData it : step.items) {
            boolean hov = isOver(mx, my, panelX + 2, ry, SIP_W - 4, SIP_ROW - 1);
            int ac = it.accentColor();

            // Row bg
            g.fill(panelX + 2, ry, panelX + SIP_W - 2, ry + SIP_ROW - 1,
                    hov ? 0xBB0D1A2D : 0x22000000);
            g.fill(panelX + 2, ry, panelX + 3, ry + SIP_ROW - 1, ac);

            // Track animation
            float[] anim = computeSipTrackOffset(it, playbackTick);
            int alpha = Math.max(0, Math.min(255, (int) (anim[2] * 255)));
            int iconSize = hov ? SIP_HOV : SIP_ICON;
            int iconX = panelX + 5;
            int iconCY = ry + (SIP_ROW - 1) / 2;
            int iconY = iconCY - iconSize / 2 + Math.round(anim[1]);

            net.minecraft.world.item.Item mcItem = resolveItemById(it.item);
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
                g.fill(iconX + 2, iconY + 2, iconX + SIP_HOV - 2, iconY + SIP_HOV - 2, 0x44FF0000);
                g.drawCenteredString(font, "?", iconX + SIP_HOV / 2, iconCY - 4, 0xFFFF5252);
            }

            // Text
            int tx = panelX + 5 + SIP_HOV + 4;
            int tw = SIP_W - (tx - panelX) - 5;
            int ty = ry + 3;

            g.drawString(font, sipTrunc(it.displayLabel(), tw), tx, ty,
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
                var lines = font.split(Component.literal(it.description), tw);
                int rem = 2;
                for (var line : lines) {
                    if (rem-- <= 0) break;
                    g.drawString(font, line, tx, ty, C_DIM(), false);
                    ty += 8;
                }
            }

            // Click hint + button
            boolean hasContent = (it.microsceneId != null && !it.microsceneId.isBlank()) ||
                    (it.description != null && !it.description.isBlank());
            if (hasContent && hov) {
                boolean hasScene = it.microsceneId != null && !it.microsceneId.isBlank();
                String hint = hasScene ? "\u25BA Scene" : "\u25BA More";
                g.drawString(font, hint, panelX + SIP_W - font.width(hint) - 5,
                        ry + SIP_ROW - 11, 0xFF80DEEA, false);
            }

            final PhantasiaSceneData.ItemConditionData fIt = it;
            activeButtons.add(new PhantasiaUIUtils.ButtonAction(panelX + 2, ry, SIP_W - 4, SIP_ROW - 1, () -> {
                PhantasiaSceneData microscene = fIt.microsceneId != null ? PhantasiaScenes.get(fIt.microsceneId) : null;
                Minecraft.getInstance().setScreen(
                        new PhantasiaItemMicrosceneScreen(
                                PhantasiaSceneScreen.this, fIt, microscene));
            }));

            ry += SIP_ROW;
        }
    }

    private static float[] computeSipTrackOffset(PhantasiaSceneData.ItemConditionData it, int tick) {
        String track = it.track == null ? "none" : it.track.toLowerCase(java.util.Locale.ROOT);
        if ("none".equals(track)) return new float[] { 0, 0, 1f };
        int dur = Math.max(1, it.trackDurationTicks);
        float t = (tick % dur) / (float) dur;
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

    private static net.minecraft.world.item.Item resolveItemById(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            var rl = id.contains(":") ? new net.minecraft.resources.ResourceLocation(id) :
                    new net.minecraft.resources.ResourceLocation("minecraft", id);
            var item = ForgeRegistries.ITEMS.getValue(rl);
            return (item == null || item == net.minecraft.world.item.Items.AIR) ? null : item;
        } catch (Exception e) {
            return null;
        }
    }

    private String sipTrunc(String s, int maxPx) {
        if (s == null) return "";
        while (font.width(s) > maxPx && s.length() > 2) s = s.substring(0, s.length() - 2) + "\u2026";
        return s;
    }

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
        int maxW = sw - 24;                                      // horizontal margin each side
        int stripY = this.height - TIMELINE_H - CAPTION_STRIP_H;

        g.fill(0, stripY, sw, stripY + CAPTION_STRIP_H, 0xDD08080F);
        g.fill(0, stripY, sw, stripY + 1, C_ACCENT());

        if (captionOutgoing != null && captionOutAlpha > 0.05f) {
            int col = ((int) (captionOutAlpha * 160) << 24) | 0xBBBBBB;
            drawWrappedCenteredCaption(g, captionOutgoing, sw, stripY, maxW, col);
        }
        if (captionCurrent != null && captionAlpha > 0.05f) {
            int col = ((int) (captionAlpha * 255) << 24) | 0xDDDDDD;
            drawWrappedCenteredCaption(g, captionCurrent, sw, stripY, maxW, col);
        }

        g.pose().popPose();
    }

    /**
     * Splits {@code text} into at most 3 lines at word boundaries, appending
     * "…" to the last line if more text was cut. Lines are drawn centred
     * horizontally and vertically within the caption strip.
     */
    private void drawWrappedCenteredCaption(GuiGraphics g, String text,
                                            int sw, int stripY, int maxW, int col) {
        // font.split respects maxW and splits on whitespace
        var allLines = font.split(net.minecraft.network.chat.Component.literal(text), maxW);

        boolean truncated = allLines.size() > 3;
        var lines = truncated ? allLines.subList(0, 3) : allLines;

        int lineH = font.lineHeight + 1;   // 9px per line
        int totalH = lines.size() * lineH - 1;
        int startY = stripY + (CAPTION_STRIP_H - totalH) / 2;

        for (int i = 0; i < lines.size(); i++) {
            net.minecraft.util.FormattedCharSequence line = lines.get(i);

            // On the last displayed line, append "…" if we had to cut
            if (truncated && i == lines.size() - 1) {
                String truncStr = net.minecraft.network.chat.FormattedText
                        .of(line.toString()).getString();
                // Trim + append ellipsis so the line still fits maxW
                String withEllipsis = trunc(
                        net.minecraft.network.chat.Component.literal(truncStr)
                                .getVisualOrderText().toString(),
                        maxW - font.width("\u2026")) + "\u2026";
                g.drawCenteredString(font, withEllipsis, sw / 2, startY + i * lineH, col);
            } else {
                g.drawString(font, line, sw / 2 - font.width(line) / 2,
                        startY + i * lineH, col, false);
            }
        }
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
        if (PhantasiaEmiPlugin.EMI_PRESENT) {
            regIconBtn(g, mx, my, px + 10, y, pw - 20, 16, "\uD83E\uDDEE", "Materials",
                    false, this::openMaterialCostScreen);
            y += 20;
        }

        boolean hasVariants = script != null &&
                script.getVariantGroups().stream().anyMatch(PhantasiaVariantGroup::hasChoice);
        if (hasVariants) {
            regIconBtn(g, mx, my, px + 10, y, pw - 20, 16, "⚙", "Variants",
                    false, this::openVariantsScreen);
            y += 20;
        }

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
            // FIX: Solved the dangling 'if' syntax block and hooked up the inspector subscreen
            if (btn == 1 && hoveredPos != null && SHARED_LEVEL != null) {
                if (!SHARED_LEVEL.getBlockState(hoveredPos).isAir()) {
                    Minecraft.getInstance().setScreen(new PhantasiaBlockInspectScreen(hoveredPos, pattern, this));
                    return true;
                }
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

    private void openMaterialCostScreen() {
        if (pattern == null) return;
        if (camera != null) camera.save();
        Minecraft.getInstance().setScreen(
                new PhantasiaMaterialCostScreen(pattern, this));
    }

    private void openVariantsScreen() {
        if (camera != null) camera.save();
        Minecraft.getInstance().setScreen(
                new PhantasiaVariantsScreen(this, PhantasiaVariantState.get()));
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

    /**
     * Returns the world-space positions of all blocks belonging to any variant group.
     * Used by the variant onChange callback to scope the partial bake to only the
     * positions that could have changed, rather than the whole pattern.
     */
    private Set<BlockPos> buildVariantWorldPositions(PhantasiaVariantState vs) {
        if (pattern == null || script == null) return Set.of();
        Set<BlockPos> result = new HashSet<>();

        for (PhantasiaVariantGroup group : script.getVariantGroups()) {
            // FIX: Grab the world positions directly from the compiled group map keys
            result.addAll(group.getPositionBaseIndex().keySet());
        }

        return result;
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
        PhantasiaVariantState.get().clear();
        machineWorking = false;
        invalidateSharedLevel();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
