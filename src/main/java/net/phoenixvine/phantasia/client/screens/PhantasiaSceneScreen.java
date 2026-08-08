package net.phoenixvine.phantasia.client.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.client.camera.CameraView;
import net.phoenixvine.phantasia.client.camera.LerpType;
import net.phoenixvine.phantasia.client.camera.PhantasiaCamera;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.client.render.PhantasiaWorldRenderer;
import net.phoenixvine.phantasia.client.screens.editors.PhantasiaScriptEditorScreen;
import net.phoenixvine.phantasia.client.screens.subscreen.*;
import net.phoenixvine.phantasia.common.data.guides.PhantasiaGuideData;
import net.phoenixvine.phantasia.common.data.guides.PhantasiaGuideRegistry;
import net.phoenixvine.phantasia.common.data.pattern.PhantasiaLoadedPattern;
import net.phoenixvine.phantasia.common.data.pattern.PhantasiaPatternLoader;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaScenes;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScript;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScripts;
import net.phoenixvine.phantasia.common.data.variant.PhantasiaVariantGroup;
import net.phoenixvine.phantasia.common.data.variant.PhantasiaVariantState;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.common.multiblock.PhantasiaMultiblockRegistry;
import net.phoenixvine.phantasia.compat.arsnouveaucompat.ArsNouveauScriptEditorScreen;
import net.phoenixvine.phantasia.configs.PhantasiaConfigs;
import net.phoenixvine.phantasia.integration.emi.PhantasiaEmiPlugin;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;
import net.phoenixvine.phantasia.utils.PhantasiaThemeUtils;
import net.phoenixvine.phantasia.utils.PhantasiaUIUtils;

import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.*;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

@OnlyIn(Dist.CLIENT)
public class PhantasiaSceneScreen extends Screen {

    public static PhantasiaTrackedDummyWorld SHARED_LEVEL;

    private static boolean machineWorking = false;
    private static final int REGION_SIZE = 512;

    public static void invalidateSharedLevel() {
        if (SHARED_LEVEL != null) {
            SHARED_LEVEL.clear();
        }
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

    private static final int FULL_PANEL_W = 168;
    private static final int COLLAPSED_PANEL_W = 18;
    private static final int TIMELINE_H = 26;
    private static final int CAPTION_STRIP_H = 38;

    private final java.util.Map<net.minecraft.core.BlockPos, PhantasiaVariantGroup> positionToVariantGroupCache = new java.util.HashMap<>();
    private boolean cacheInitialized = false;

    private static final boolean ENABLE_PROFILER = true;

    private static final long SCREEN_PROFILE_WINDOW_NS = 5_000_000_000L;
    private long screenProfileWindowStart = -1;
    private int screenProfiledFramesCount = 0;
    private long totalScreenRenderTimeNs = 0;
    private long maxScreenSpikeNs = 0;
    private long totalVariantLookupTimeNs = 0;

    private static final float CAM_TARGET_Y_BIAS = 0.0f;
    private static final float CAM_DEFAULT_PITCH = 5.0f;
    private static final float CAM_DEFAULT_ZOOM = 40.0f;
    private static final float CAM_ZOOM_IN_FACTOR = 0.9f;
    private static final float CAM_ZOOM_OUT_FACTOR = 1.1f;
    private static final float CAM_ZOOM_MIN = 2.0f;
    private static final float CAM_ZOOM_MAX = 300.0f;
    private static final float CAM_ORBIT_SENSITIVITY = 0.5f;

    public static final float CAM_PAN_ZOOM_SCALE = 0.0005f;

    private final Screen parent;
    public final IPhantasiaMultiblockDefinition definition;
    public PhantasiaScript script;

    private long openedAtMs = -1;

    private PhantasiaLoadedPattern pattern;

    private float patternMaxDim = 0f;

    private PhantasiaPatternLoader asyncLoader;

    public PhantasiaWorldRenderer renderer;

    private int shapeIndex = 0;
    public List<IPhantasiaMultiblockShape> availableShapes = new ArrayList<>();

    private PhantasiaCamera camera;
    private boolean isPanning = false;

    private boolean playing = PhantasiaConfigs.INSTANCE == null ||
            PhantasiaConfigs.INSTANCE.phantasiaUI.autoPlayScripts;
    private int playbackTick = 0;
    private float tickAccum = 0f;
    private float playbackSpeed = 1.0f;
    private boolean scrubbing = false;
    private PhantasiaScript.Step lastAppliedStep = null;
    private int sceneTick = 0;
    private int itemAnimTick = 0;
    private int sipScrollOffset = 0;

    public int getPlaybackTick() {
        return this.playbackTick;
    }

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

    private boolean buildOrderMode = false;
    private int buildOrderGroup = 0;
    private float buildPulse = 0f;
    private int savedManualLayer = -1;
    private boolean buildPulseUp = true;

    private float captionAlpha = 0f;
    private String captionCurrent = null;
    private String captionOutgoing = null;
    private float captionOutAlpha = 0f;

    private final List<PhantasiaUIUtils.ButtonAction> activeButtons = new ArrayList<>();
    private boolean sidePanelCollapsed = false;
    private BlockPos hoveredPos = null;

    public boolean showMistakes = false;
    public int selectedTierIndex = -1;

    public PhantasiaSceneScreen(IPhantasiaMultiblockDefinition definition, Screen parent) {
        super(Component.literal(definition.getDisplayName()));
        this.parent = parent;
        this.definition = definition;
        this.script = PhantasiaScripts.get(definition);
    }

    public void reloadScript() {
        this.script = PhantasiaScripts.get(definition);
        if (pattern != null) {
            this.script = this.script.withVariants(definition, pattern);
            PhantasiaVariantState vs = PhantasiaVariantState.get();
            vs.loadGroups(this.script.getVariantGroups());
        }
        this.lastAppliedStep = null;
        this.playbackTick = 0;

        machineWorking = false;
        applyActiveStateToWorld(false);
        applyVisibility();
    }

    private List<PhantasiaBlockInfo> coilTiers = new ArrayList<>();

    private PhantasiaCamera buildFreshCamera() {
        float[] yp = resolveStartingYawPitch();
        float zoom = resolveStartingZoom();
        float[] target = resolveTarget();
        PhantasiaCamera cam = new PhantasiaCamera(yp[0], yp[1], zoom,
                target[0], target[1], target[2]);
        if (pattern != null) cam.setFloorY(pattern.origin.getY() + 0.5f);
        if (PhantasiaConfigs.INSTANCE != null) {
            boolean follow = PhantasiaConfigs.INSTANCE.phantasiaUI.scriptLockCamera;
            cam.setLocked(follow);
            if (!follow) cam.setPlayerOwned(true);
        }
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

    private void onAsyncPatternLoaded(PhantasiaLoadedPattern p) {
        asyncLoader = null;
        this.pattern = p;

        SHARED_LEVEL.renderedBlocks.clear();
        SHARED_LEVEL.blockEntities.clear();
        SHARED_LEVEL.addBlocks(p.blockMap);
        for (BlockPos beWorldPos : p.blockEntityWorldPos) {
            PhantasiaBlockInfo info = p.blockMap.get(beWorldPos);
            if (info != null && info.getBlockState().getBlock() instanceof EntityBlock eb) {
                BlockEntity be = eb.newBlockEntity(beWorldPos, info.getBlockState());
                if (be != null) {
                    be.setLevel(SHARED_LEVEL);
                    SHARED_LEVEL.setInnerBlockEntity(be);
                }
            }
        }

        if (p.postWriteTask != null) {
            try {
                p.postWriteTask.run();
            } catch (Exception e) {
                net.phoenixvine.phantasia.Phantasia.LOGGER
                        .error("[Phantasia] postWriteTask failed — continuing with finishPatternSetup", e);
            }
        }

        finishPatternSetup(p);
    }

    private float camZoomMax() {
        return Math.max(CAM_ZOOM_MAX, patternMaxDim * 9.0f);
    }

    private void finishPatternSetup(PhantasiaLoadedPattern p) {
        int sx = 0, sy = 0, sz = 0;
        for (BlockPos lp : p.localToWorld.keySet()) {
            sx = Math.max(sx, lp.getX() + 1);
            sy = Math.max(sy, lp.getY() + 1);
            sz = Math.max(sz, lp.getZ() + 1);
        }
        patternMaxDim = Math.max(sx, Math.max(sy, sz));

        invalidateFilterSets();
        applyActiveStateToWorld(machineWorking);
        script = script.withVariants(definition, p);
        PhantasiaVariantState vs = PhantasiaVariantState.get();
        vs.loadGroups(script.getVariantGroups());
        vs.setOnChangeCallback(() -> {
            if (renderer == null || pattern == null) return;
            this.cacheInitialized = false;

            applyActiveStateToWorld(machineWorking);
            Set<BlockPos> variantPositions = buildVariantWorldPositions(vs);
            if (variantPositions.isEmpty()) renderer.requestBake();
            else renderer.requestPartialBake(variantPositions);
        });

        if (renderer != null) {
            boolean showBP = PhantasiaConfigs.INSTANCE == null || PhantasiaConfigs.INSTANCE.phantasiaUI.showBaseplate;
            renderer.setBaseplatePositions(showBP ? p.baseplatePositions : Set.of());
            renderer.setControllerWorldPos(p.controllerWorldPos);
            Set<BlockPos> fullBakeSet = new HashSet<>(p.blockMap.keySet());
            if (showBP) fullBakeSet.addAll(p.baseplatePositions);
            renderer.setPatternBlocks(fullBakeSet);
            renderer.requestBake();
        }

        applyVisibility();
        ensureVariantCachePopulated();
    }

    private PhantasiaLoadedPattern loadPattern(IPhantasiaMultiblockShape shape) {
        BlockPos renderOrigin = new BlockPos(8, 50, 8);
        int blockCount = countBlocks(shape.getBlocks());
        if (blockCount <= PhantasiaWorldRenderer.STREAMING_THRESHOLD) {
            net.phoenixvine.phantasia.Phantasia.LOGGER.info(
                    "[Phantasia] Sync load ({} blocks) for {}", blockCount, definition.getId());
            return loadPatternCold(shape, renderOrigin);
        } else {
            net.phoenixvine.phantasia.Phantasia.LOGGER.info(
                    "[Phantasia] Async load ({} blocks) for {}", blockCount, definition.getId());
            if (asyncLoader != null) {
                asyncLoader.cancel();
                asyncLoader = null;
            }
            asyncLoader = PhantasiaPatternLoader.start(
                    definition, shapeIndex, availableShapes, script, SHARED_LEVEL,
                    this::onAsyncPatternLoaded);
            return null;
        }
    }

    private static int countBlocks(PhantasiaBlockInfo[][][] raw) {
        int n = 0;
        for (PhantasiaBlockInfo[][] layer : raw)
            for (PhantasiaBlockInfo[] row : layer)
                for (PhantasiaBlockInfo b : row) {
                    if (b == null) continue;
                    net.minecraft.world.level.block.state.BlockState s = b.getBlockState();
                    if (s == null || s.isAir() ||
                            s.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE)
                        continue;
                    n++;
                }
        return n;
    }

    private PhantasiaLoadedPattern loadPatternCold(IPhantasiaMultiblockShape shape, BlockPos renderOrigin) {
        SHARED_LEVEL.renderedBlocks.clear();
        SHARED_LEVEL.blockEntities.clear();

        PhantasiaBlockInfo[][][] raw = shape.getBlocks();
        Map<BlockPos, PhantasiaBlockInfo> blockMap = new HashMap<>();
        Map<BlockPos, BlockPos> localToWorld = new HashMap<>();
        Set<BlockPos> baseplatePos = new HashSet<>();
        Set<BlockPos> bePos = new HashSet<>();
        BlockPos controllerWP = null;

        var _baseplateState0 = net.phoenixvine.phantasia.utils.PhantasiaBaseplateConfig.currentBaseplateBlockState();
        PhantasiaBlockInfo floor = _baseplateState0 != null ? PhantasiaBlockInfo.fromBlockState(_baseplateState0) :
                null;
        int sxLen = raw.length;
        int szLen = sxLen > 0 && raw[0].length > 0 ? raw[0][0].length : 0;
        int padX = Math.max(2, sxLen / 2 + 1);
        int padZ = Math.max(2, szLen / 2 + 1);

        if (floor != null) for (int bx = -padX; bx < sxLen + padX; bx++)
            for (int bz = -padZ; bz < szLen + padZ; bz++) {
                BlockPos wp = renderOrigin.offset(bx, -1, bz);
                blockMap.put(wp, floor);
                baseplatePos.add(wp);
                SHARED_LEVEL.setBlock(wp, floor.getBlockState(), 3);
            }

        for (int x = 0; x < raw.length; x++)
            for (int y = 0; y < raw[x].length; y++)
                for (int z = 0; z < raw[x][y].length; z++) {
                    PhantasiaBlockInfo info = raw[x][y][z];
                    if (info == null) continue;

                    BlockState state = info.getBlockState();
                    if (state.isAir()) continue;

                    BlockPos lp = new BlockPos(x, y, z);
                    BlockPos wp = renderOrigin.offset(x, y, z);

                    blockMap.put(wp, info);
                    localToWorld.put(lp, wp);

                    SHARED_LEVEL.setBlock(wp, state, 3);

                    if (controllerWP == null && PhantasiaMultiblockRegistry.isControllerBlock(state))
                        controllerWP = wp;

                    if (state.getBlock() instanceof EntityBlock entityBlock) {
                        BlockEntity newEntity = entityBlock.newBlockEntity(wp, state);
                        if (newEntity != null) {
                            newEntity.setLevel(SHARED_LEVEL);
                            SHARED_LEVEL.setInnerBlockEntity(newEntity);
                            bePos.add(wp);
                        }
                    }
                }

        PhantasiaLoadedPattern result = finalisePattern(raw, blockMap, localToWorld, baseplatePos, bePos,
                controllerWP, renderOrigin);
        PhantasiaScriptData scriptData = script != null ? script.getSourceData() : null;
        RenderSystem.recordRenderCall(() -> definition.onShapeLoaded(SHARED_LEVEL, renderOrigin,
                new HashMap<>(blockMap), new HashMap<>(localToWorld), scriptData));
        return result;
    }

    public void refireShapeLoaded() {
        if (pattern == null || SHARED_LEVEL == null) return;
        PhantasiaScriptData scriptData = script != null ? script.getSourceData() : null;
        BlockPos origin = pattern.origin;
        Map<BlockPos, net.phoenixvine.phantasia.utils.PhantasiaBlockInfo> blockMap = new java.util.HashMap<>(
                pattern.blockMap);
        Map<BlockPos, BlockPos> localToWorld = new java.util.HashMap<>(pattern.localToWorld);
        com.mojang.blaze3d.systems.RenderSystem.recordRenderCall(
                () -> definition.onShapeLoaded(SHARED_LEVEL, origin, blockMap, localToWorld, scriptData));
    }

    private PhantasiaLoadedPattern finalisePattern(
                                                   PhantasiaBlockInfo[][][] raw,
                                                   Map<BlockPos, PhantasiaBlockInfo> blockMap,
                                                   Map<BlockPos, BlockPos> localToWorld,
                                                   Set<BlockPos> baseplatePos,
                                                   Set<BlockPos> bePos,
                                                   BlockPos controllerWP,
                                                   BlockPos origin) {
        net.phoenixvine.phantasia.Phantasia.LOGGER.info(
                "[Phantasia] Registered {} block entities with SHARED_LEVEL", bePos.size());

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
                controllerWP, bePos, origin, minY, maxY, script);
    }

    public void applyVisibility() {
        if (renderer == null || pattern == null || SHARED_LEVEL == null) return;
        PhantasiaScript.Step step = script.getActiveStep(playbackTick);

        Set<BlockPos> next = new HashSet<>();
        for (Map.Entry<BlockPos, BlockPos> e : pattern.localToWorld.entrySet()) {
            if (isBlockVisible(e.getKey(), e.getValue(), step))
                next.add(e.getValue());
        }

        renderer.setVisible(next);
        renderer.requestBake();
    }

    public boolean isBlockVisible(BlockPos local, BlockPos world, PhantasiaScript.Step step) {
        if (viewFilter != ViewFilter.ALL) {
            Set<BlockPos> fs = getFilterSet(viewFilter);
            return fs == null || fs.contains(world);
        }

        if (buildOrderMode) {
            int g = pattern.getGroupIndex(local);
            return g != -1 && g <= buildOrderGroup;
        }

        if (manualLayer >= 0) return local.getY() == manualLayer;
        if (step != null) return step.filter().test(local);
        return true;
    }

    public void applyViewFilter(ViewFilter vf) {
        if (viewFilter == vf) return;
        viewFilter = vf;
        applyVisibility();
    }

    public boolean computeHasRealSizeVariants() {
        if (availableShapes == null || availableShapes.size() <= 1) return false;
        int firstCount = countBlocks(availableShapes.get(0));
        for (int i = 1; i < availableShapes.size(); i++) {
            if (countBlocks(availableShapes.get(i)) != firstCount) return true;
        }
        return false;
    }

    private static int countBlocks(IPhantasiaMultiblockShape shape) {
        int count = 0;
        for (PhantasiaBlockInfo[][] layer : shape.getBlocks())
            for (PhantasiaBlockInfo[] row : layer)
                for (PhantasiaBlockInfo b : row)
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
            if (!PhantasiaMultiblockRegistry.isPartBlock(state)) continue;
            ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(state.getBlock());
            if (rl == null) continue;
            String p = rl.getPath();
            if (p.contains("hatch") || p.contains("bus") || p.contains("muffler") || p.contains("maintenance"))
                filteredHatchBus.add(wp);
            if (p.contains("energy") || p.contains("dynamo") || p.contains("laser") || p.contains("power"))
                filteredEnergyIO.add(wp);
        }
    }

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

        this.cacheInitialized = false;
        org.slf4j.LoggerFactory.getLogger("PhantasiaScreenCache")
                .info("[Phantasia] Screen init triggered: Invalidating variant lookup cache maps.");

        if (openedAtMs < 0) {
            openedAtMs = System.currentTimeMillis();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new net.phoenixvine.phantasia.api.PhantasiaEvents.ViewerOpen(definition, this));
        }

        if (SHARED_LEVEL == null) {
            if (Minecraft.getInstance().level == null) {
                onClose();
                return;
            }
            SHARED_LEVEL = new PhantasiaTrackedDummyWorld();

        }

        availableShapes = definition.getMatchingShapes();
        if (pattern == null && !availableShapes.isEmpty()) {
            if (shapeIndex == 0 && definition != null) {
                int saved = PhantasiaVariantState.get().getShapeIndex(definition.getId().toString());
                if (saved > 0 && saved < availableShapes.size()) shapeIndex = saved;
            }
            if (shapeIndex >= availableShapes.size()) shapeIndex = 0;
            pattern = loadPattern(availableShapes.get(shapeIndex));
            if (pattern != null) {

                finishPatternSetup(pattern);
            }

        }

        if (renderer == null) {
            renderer = new PhantasiaWorldRenderer(SHARED_LEVEL);
        }
        if (pattern != null) {
            boolean showBP2 = PhantasiaConfigs.INSTANCE == null || PhantasiaConfigs.INSTANCE.phantasiaUI.showBaseplate;
            renderer.setBaseplatePositions(showBP2 ? pattern.baseplatePositions : Set.of());
            renderer.setControllerWorldPos(pattern.controllerWorldPos);

            Set<BlockPos> fullBakeSet = new HashSet<>();
            fullBakeSet.addAll(pattern.blockMap.keySet());
            if (showBP2) fullBakeSet.addAll(pattern.baseplatePositions);
            renderer.setPatternBlocks(fullBakeSet);

            renderer.requestBake();
        }

        net.phoenixvine.phantasia.client.render.PhantasiaParticleEngine.init();
        SHARED_LEVEL.clearSceneEntities();

        if (camera == null) {
            camera = buildFreshCamera();
        } else if (camera.hasSavedSnapshot()) {
            camera.restore();
        } else if (!camera.isPlayerOwned()) {
            resetCameraToDefault(LerpType.SNAP, 0);
        }

        if (pattern != null) {
            applyVisibility();

        }

        ensureVariantCachePopulated();
        org.slf4j.LoggerFactory.getLogger("PhantasiaScreenCache").info(String.format(
                "[Phantasia] Cache initialization finalized successfully. Pre-cached %d variant blocks for O(1) rendering lookups.",
                this.positionToVariantGroupCache.size()));
    }

    @Override
    public void tick() {
        super.tick();
        itemAnimTick++;

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

        if (SHARED_LEVEL != null && renderer != null && !scrubbing && pattern != null) {
            definition.onSceneTick(SHARED_LEVEL, pattern.localToWorld, sceneTick++);
        }

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

        if (renderer != null && !renderer.isSceneReady()) {
            tickAccum = 0f;
            return;
        }

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

        if (step != null && step.hold() != null && SHARED_LEVEL != null && pattern != null) {
            if (definition.shouldHoldStep(SHARED_LEVEL, pattern.localToWorld, step.hold(), sceneTick)) {
                playbackTick = step.tickOffset();
                tickAccum = 0f;
            }
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
                sceneTick = 0;
                invalidateFilterSets();
                init();
                return;
            }

            if (step != null && step.layerCount() != -1 && definition != null && script != null &&
                    script.getSourceData().isExpandable() && availableShapes != null) {
                int targetIdx = definition.getShapeIndexForLayerCount(step.layerCount());
                if (targetIdx != shapeIndex && targetIdx < availableShapes.size()) {
                    shapeIndex = targetIdx;
                    camera.hardReset(
                            camera.getYaw(), camera.getPitch(), camera.getZoom(),
                            camera.getTargetX(), camera.getTargetY(), camera.getTargetZ(),
                            LerpType.SNAP, 0);
                    if (renderer != null) {
                        renderer.close();
                        renderer = null;
                    }
                    pattern = null;
                    sceneTick = 0;
                    invalidateFilterSets();
                    init();
                    return;
                }
            }

            applyVisibility();
            updateMachineState(step);
            updateCaptionForStep(step);
            applyWorldItems(step);
            applyHighlights(step);
            applyBlockTransitions(step);
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

    private void applyWorldItems(PhantasiaScript.Step step) {
        if (step == null || SHARED_LEVEL == null || pattern == null) return;
        var entries = step.worldItems();
        if (entries.isEmpty()) return;

        Set<BlockPos> rebakePos = new java.util.HashSet<>();
        for (var e : entries) {
            net.minecraft.core.BlockPos local = new net.minecraft.core.BlockPos(e.x, e.y, e.z);
            net.minecraft.core.BlockPos world = pattern.localToWorld.get(local);
            if (world == null) continue;

            net.minecraft.world.level.block.entity.BlockEntity be = SHARED_LEVEL.getBlockEntity(world);
            if (be == null) continue;
            if (be.getLevel() == null) be.setLevel(SHARED_LEVEL);

            if (e.sourceAmount >= 0) {
                try {
                    if (be instanceof com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile jar) {
                        jar.setSource(e.sourceAmount);
                        jar.setChanged();
                        rebakePos.add(world);
                    }
                } catch (Exception ignored) {}
            }

            if (e.item != null && !e.item.isBlank()) {
                try {
                    net.minecraft.resources.ResourceLocation rl = new net.minecraft.resources.ResourceLocation(e.item);
                    net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                            .getValue(rl);
                    net.minecraft.world.item.ItemStack stack = (item == null ||
                            item == net.minecraft.world.item.Items.AIR) ? net.minecraft.world.item.ItemStack.EMPTY :
                                    new net.minecraft.world.item.ItemStack(item);
                    if (be instanceof net.minecraft.world.Container container) {
                        container.setItem(0, stack);
                        be.setChanged();
                        rebakePos.add(world);
                    } else {
                        net.minecraft.world.item.ItemStack finalStack = stack;
                        be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER)
                                .ifPresent(handler -> {
                                    if (handler.getSlots() > 0 && handler.isItemValid(0, finalStack)) {
                                        handler.insertItem(0, finalStack, false);
                                        be.setChanged();
                                        rebakePos.add(world);
                                    }
                                });
                    }
                } catch (Exception ignored) {}
            }
        }

        if (!rebakePos.isEmpty() && renderer != null) {
            renderer.requestPartialBake(rebakePos);
        }
    }

    private void applyHighlights(PhantasiaScript.Step step) {
        if (renderer == null) return;
        if (step == null || step.highlights().isEmpty()) {
            renderer.setHighlights(Collections.emptyMap());
            return;
        }
        Map<BlockPos, Integer> map = new HashMap<>();
        BlockPos origin = pattern != null ? pattern.origin : BlockPos.ZERO;
        for (PhantasiaScript.HighlightEntry h : step.highlights()) {
            BlockPos worldPos = h.localPos().offset(origin);
            map.put(worldPos, h.argb());
        }
        renderer.setHighlights(map);
    }

    private void applyBlockTransitions(PhantasiaScript.Step step) {
        if (renderer == null || SHARED_LEVEL == null || pattern == null) return;
        if (step == null || step.blockTransitions().isEmpty()) return;
        BlockPos origin = pattern.origin;
        for (PhantasiaScript.BlockTransitionEntry t : step.blockTransitions()) {
            BlockPos worldPos = t.localPos().offset(origin);
            try {
                net.minecraft.world.level.block.state.BlockState newState = net.phoenixvine.phantasia.compat.custom.PhantasiaCustomLayout
                        .parseBlockState(t.stateString());
                if (newState != null) renderer.applyBlockTransition(worldPos, newState);
                else Phantasia.LOGGER.warn("[Phantasia] blockTransition unknown state '{}'", t.stateString());
            } catch (Exception e) {
                Phantasia.LOGGER.warn("[Phantasia] blockTransition bad state '{}': {}", t.stateString(),
                        e.getMessage());
            }
        }
    }

    public void applyActiveStateToWorld(boolean working) {
        if (SHARED_LEVEL == null || pattern == null) return;
        definition.applyWorkingState(SHARED_LEVEL, pattern.blockMap.keySet(), pattern.blockMap, working);
        if (renderer != null) {
            renderer.flushTransitions();
            renderer.requestBake();
        }
    }

    private void updateMachineState(PhantasiaScript.Step step) {
        if (pattern == null) return;
        boolean working = step != null && step.working() && playbackTick < script.getTotalTicks();
        if (machineWorking == working) return;
        machineWorking = working;
        applyActiveStateToWorld(working);
        if (renderer != null) renderer.requestBake();
    }

    private int detectCoilIndex(PhantasiaLoadedPattern pat) {
        if (pat == null || coilTiers.isEmpty()) return 0;
        for (PhantasiaBlockInfo info : pat.blockMap.values()) {
            var block = info.getBlockState().getBlock();
            for (int i = 0; i < coilTiers.size(); i++) {
                if (coilTiers.get(i).getBlockState().getBlock() == block) return i;
            }
        }
        return 0;
    }

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

        screenProfileWindowStart = System.nanoTime();
        screenProfiledFramesCount = 0;
        totalScreenRenderTimeNs = 0;
        maxScreenSpikeNs = 0;
        totalVariantLookupTimeNs = 0;
    }

    public void renderAsBackground(net.minecraft.client.gui.GuiGraphics g, float partial) {
        int pw = getCurrentPanelWidth();
        int sw = this.width - pw;
        int sh = this.height - TIMELINE_H - CAPTION_STRIP_H;
        g.fill(0, 0, this.width, this.height, C_BG());
        if (renderer != null && camera != null) {
            CameraView view = camera.getView(partial);
            renderer.setMousePos(-1, -1);
            renderer.render(view, 0, CAPTION_STRIP_H, sw, sh);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        long screenRenderStart = ENABLE_PROFILER ? System.nanoTime() : 0;
        if (ENABLE_PROFILER && screenProfileWindowStart == -1) {
            screenProfileWindowStart = screenRenderStart;
        }

        activeButtons.clear();
        ensureVariantCachePopulated();

        if (Minecraft.getInstance().level != null) {
            long gameTime = Minecraft.getInstance().level.getGameTime();
            RenderSystem.setShaderGameTime(gameTime, partial);
        }

        int pw = getCurrentPanelWidth();
        int sw = this.width - pw;
        int sh = this.height - TIMELINE_H - CAPTION_STRIP_H;

        g.fill(0, 0, this.width, this.height, C_BG());

        if (renderer != null && camera != null) {
            CameraView view = camera.getView(partial);

            renderer.setMousePos(mx, my);
            renderer.render(view, 0, CAPTION_STRIP_H, sw, sh);

            BlockHitResult hit = renderer.getLastHitResult();
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos hp = hit.getBlockPos();

                boolean isBaseplate = pattern != null && pattern.baseplatePositions.contains(hp);
                hoveredPos = (renderer.isVisible(hp) || isBaseplate) ? hp : null;
            } else {
                hoveredPos = null;
            }
        }

        renderCaption(g);
        if (buildOrderMode && pattern != null) renderBuildPulseBanner(g);
        if (script != null && script.hasCommonMistakes(playbackTick))
            renderLocalMistakesOverlay(g);
        if (showMistakes && script != null && !script.getGlobalMistakes().isEmpty())
            renderGlobalMistakesOverlay(g);
        if (machineWorking && definition != null)
            definition.renderWorkingOverlay(g, 0, CAPTION_STRIP_H, sw, sh, partial);

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

                    var group = positionToVariantGroupCache.get(hoveredPos);
                    if (group != null) {
                        int activeSel = PhantasiaVariantState.get()
                                .getSelection(group.getId());
                        if (activeSel >= 0 && activeSel < group.getOptions().size()) {
                            st = group.getOptions().get(activeSel);
                        }
                    }
                }

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

        if (asyncLoader != null && !asyncLoader.isDone()) {
            g.fill(0, 0, this.width, this.height, 0xCC000000);
            int cx = this.width / 2, cy = this.height / 2;
            String phase = asyncLoader.phase;
            int total = asyncLoader.blocksTotal;
            int placed = asyncLoader.blocksPlaced;
            g.drawCenteredString(font, phase, cx, cy - 20, 0xFFCCCCCC);
            if (total > 0) {
                int barW = 200, barH = 6;
                int barX = cx - barW / 2, barY = cy - 3;
                float pct = Math.min(1f, (float) placed / total);
                g.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFF222222);
                g.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);
                g.fill(barX, barY, barX + (int) (barW * pct), barY + barH, C_ACCENT());
                g.drawCenteredString(font, placed + " / " + total + " blocks", cx, cy + 12, C_DIM());
            }
        }

        if (ENABLE_PROFILER) {
            long frameDurationNs = System.nanoTime() - screenRenderStart;
            totalScreenRenderTimeNs += frameDurationNs;
            maxScreenSpikeNs = Math.max(maxScreenSpikeNs, frameDurationNs);
            screenProfiledFramesCount++;
            if (System.nanoTime() - screenProfileWindowStart >= SCREEN_PROFILE_WINDOW_NS) {
                dumpScreenProfilingMetrics();
            }
        }
    }

    private static final int SIP_W = 186;
    private static final int SIP_ROW = 38;
    private static final int SIP_ICON = 28;
    private static final int SIP_HOV = 36;

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
        int totalH = step.items.size() * SIP_ROW + 8;
        int maxPanelH = this.height - panelY - 24;
        int panelH = Math.min(totalH, maxPanelH);
        int maxScroll = Math.max(0, totalH - panelH);
        sipScrollOffset = Mth.clamp(sipScrollOffset, 0, maxScroll);

        if (panelX < 4) panelX = 4;

        g.fill(panelX, panelY, panelX + SIP_W, panelY + panelH, 0xDD070712);
        g.fill(panelX, panelY, panelX + SIP_W, panelY + 1, C_ACCENT());
        g.fill(panelX, panelY, panelX + 1, panelY + panelH, 0x554FC3F7);
        if (maxScroll > 0) {

            int sbH = Math.max(12, panelH * panelH / totalH);
            int sbY = panelY + (int) ((panelH - sbH) * (sipScrollOffset / (float) maxScroll));
            g.fill(panelX + SIP_W - 3, panelY, panelX + SIP_W - 1, panelY + panelH, 0x33FFFFFF);
            g.fill(panelX + SIP_W - 3, sbY, panelX + SIP_W - 1, sbY + sbH, 0xAAFFFFFF);
        }

        g.enableScissor(panelX, panelY + 1, panelX + SIP_W, panelY + panelH);
        int ry = panelY + 4 - sipScrollOffset;
        for (PhantasiaSceneData.ItemConditionData it : step.items) {
            boolean hov = ry + SIP_ROW > panelY && ry < panelY + panelH &&
                    isOver(mx, my, panelX + 2, ry, SIP_W - 4, SIP_ROW - 1);
            int ac = it.accentColor();

            g.fill(panelX + 2, ry, panelX + SIP_W - 2, ry + SIP_ROW - 1,
                    hov ? 0xBB0D1A2D : 0x22000000);
            g.fill(panelX + 2, ry, panelX + 3, ry + SIP_ROW - 1, ac);

            float[] anim = computeSipTrackOffset(it, itemAnimTick);
            int alpha = Math.max(0, Math.min(255, (int) (anim[2] * 255)));
            int iconSize = hov ? SIP_HOV : SIP_ICON;
            int iconX = panelX + 5 + Math.round(anim[0]);
            int iconCY = ry + (SIP_ROW - 1) / 2;
            int iconY = iconCY - iconSize / 2 + Math.round(anim[1]);

            net.minecraft.world.item.Item mcItem = resolveItemById(it.item);
            if (mcItem != null) {
                net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(mcItem, it.count);
                int renderY = iconCY - 8 + Math.round(anim[1]);
                if (alpha < 255) RenderSystem.setShaderColor(1f, 1f, 1f, alpha / 255f);
                g.renderItem(stack, iconX, renderY);
                g.renderItemDecorations(font, stack, iconX, renderY);
                if (alpha < 255) RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            } else {
                g.fill(iconX + 2, iconY + 2, iconX + SIP_HOV - 2, iconY + SIP_HOV - 2, 0x44FF0000);
                g.drawCenteredString(font, "?", iconX + SIP_HOV / 2, iconCY - 4, 0xFFFF5252);
            }

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

            boolean hasGuide = it.guideId != null && !it.guideId.isBlank();
            boolean hasScene = it.microsceneId != null && !it.microsceneId.isBlank();
            boolean hasContent = hasGuide || hasScene || (it.description != null && !it.description.isBlank());
            if (hasContent && hov) {
                String hint = hasGuide ? "\u25BA Guide" : hasScene ? "\u25BA Scene" : "\u25BA More";
                g.drawString(font, hint, panelX + SIP_W - font.width(hint) - 5,
                        ry + SIP_ROW - 11, 0xFF80DEEA, false);
            }

            final PhantasiaSceneData.ItemConditionData fIt = it;
            activeButtons.add(new PhantasiaUIUtils.ButtonAction(panelX + 2, ry, SIP_W - 4, SIP_ROW - 1, () -> {
                if (fIt.guideId != null && !fIt.guideId.isBlank()) {
                    PhantasiaGuideData guide = PhantasiaGuideRegistry.get(fIt.guideId);
                    if (guide != null) {
                        Minecraft.getInstance().setScreen(new PhantasiaGuideScreen(PhantasiaSceneScreen.this, guide));
                        return;
                    }
                }
                PhantasiaSceneData microscene = fIt.microsceneId != null ? PhantasiaScenes.get(fIt.microsceneId) : null;
                Minecraft.getInstance().setScreen(
                        new PhantasiaItemMicrosceneScreen(
                                PhantasiaSceneScreen.this, fIt, microscene));
            }));

            ry += SIP_ROW;
        }
        g.disableScissor();
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

    private void renderCaption(GuiGraphics g) {
        if (captionCurrent == null && captionOutgoing == null) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);

        int sw = this.width - getCurrentPanelWidth();
        int maxW = sw - 24;
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

    private void drawWrappedCenteredCaption(GuiGraphics g, String text,
                                            int sw, int stripY, int maxW, int col) {
        var allLines = font.split(net.minecraft.network.chat.Component.literal(text), maxW);

        boolean truncated = allLines.size() > 3;
        var lines = truncated ? allLines.subList(0, 3) : allLines;

        int lineH = font.lineHeight + 1;
        int totalH = lines.size() * lineH - 1;
        int startY = stripY + (CAPTION_STRIP_H - totalH) / 2;

        for (int i = 0; i < lines.size(); i++) {
            net.minecraft.util.FormattedCharSequence line = lines.get(i);

            if (truncated && i == lines.size() - 1) {
                String truncStr = net.minecraft.network.chat.FormattedText
                        .of(line.toString()).getString();

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

    private void renderLocalMistakesOverlay(GuiGraphics g) {
        List<PhantasiaScript.LocalWarning> local = script.getCommonMistakes(playbackTick);
        int x = 8, y = TIMELINE_H + 26;
        int ph = local.size() * 12 + 10;
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
    }

    private void renderGlobalMistakesOverlay(GuiGraphics g) {
        List<String> global = script.getGlobalMistakes();
        int x = 8;

        int y = TIMELINE_H + 26;
        if (script.hasCommonMistakes(playbackTick)) y += script.getCommonMistakes(playbackTick).size() * 12 + 10 + 6;
        int ph = global.size() * 12 + 10;
        g.fill(x - 2, y - 2, x + 240, y + ph, 0xCC06060E);
        g.fill(x - 2, y - 2, x + 240, y - 1, 0xFFFF5252);
        for (String m : global) {
            g.drawString(font, "• " + m, x, y, 0xFFFFFFFF, false);
            y += 12;
        }
    }

    private void renderSidePanel(GuiGraphics g, int mx, int my) {
        int pw = getCurrentPanelWidth();
        int px = this.width - pw;
        activeButtons.removeIf(b -> b.x() >= px);

        g.fill(px, 0, this.width, this.height, C_PANEL());
        g.fill(px, 0, px + 1, this.height, C_ACCENT());

        int collapseBtnX = this.width - COLLAPSED_PANEL_W;
        String collapseBtnLabel = sidePanelCollapsed ? "▶" : "◀";
        regBtn(g, mx, my, collapseBtnX, 0, COLLAPSED_PANEL_W, 18, collapseBtnLabel, () -> {
            sidePanelCollapsed = !sidePanelCollapsed;
        });

        int y = 10;
        if (sidePanelCollapsed) return;

        g.drawString(font, trunc(definition.getDisplayName(), pw - 20),
                px + 10, y, C_ACCENT(), false);
        y += 20;

        boolean hasCoilBlocks = !coilTiers.isEmpty();
        boolean hasRealSizeVariants = computeHasRealSizeVariants();
        boolean isCoilTierMachine = hasCoilBlocks && !hasRealSizeVariants;

        if (hasRealSizeVariants) {
            regBtn(g, mx, my, px + 10, y, pw - 20, 16,
                    "Structure Size: " + (shapeIndex + 1), () -> {

                        shapeIndex = (shapeIndex + 1) % availableShapes.size();
                        if (definition != null)
                            PhantasiaVariantState.get().setShapeIndex(definition.getId().toString(), shapeIndex);
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

        g.drawString(font, Component.translatable("screen.phantasia.scene.label_show").getString(), px + 10, y, C_DIM(),
                false);
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

        if (!buildOrderMode && pattern != null) {
            g.drawString(font, Component.translatable("screen.phantasia.scene.label_layer").getString(), px + 10, y + 4,
                    C_DIM(), false);
            String layerLabel = manualLayer >= 0 ? "Y=" + manualLayer : "All";

            regBtn(g, mx, my, px + 10, y + 14, bW, 14, "◀",
                    () -> nudgeLayer(-1));

            g.drawCenteredString(font, layerLabel, lX + lW / 2, y + 17, C_ACCENT());

            regBtn(g, mx, my, lX + lW + 2, y + 14, bW, 14, "▶",
                    () -> nudgeLayer(1));

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

        if (buildOrderMode && pattern != null) {
            int totalGroups = pattern.buildOrder.size();
            g.drawString(font, Component.translatable("screen.phantasia.scene.label_build_step").getString(), px + 10,
                    y + 4, C_DIM(), false);
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

                        savedManualLayer = manualLayer;
                        manualLayer = -1;
                        buildOrderGroup = 0;
                    } else {

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

        if (script != null && script.getSourceData().isExpandable() && availableShapes != null &&
                availableShapes.size() > 1) {
            int maxL = availableShapes.size();
            int curL = shapeIndex + 1;
            int teal = 0xFF4DB8B8;
            int tealDim = 0xFF1A3A3A;

            g.drawString(font, Component.translatable("screen.phantasia.scene.label_size").getString(), px + 10, y + 4,
                    teal, false);
            int lblW = font.width(Component.translatable("screen.phantasia.scene.label_size").getString()) + 4;

            int minX = px + 10 + lblW;
            int btnH = 14;
            boolean minHov = mx >= minX && mx < minX + btnH && my >= y && my < y + btnH;
            g.fill(minX, y, minX + btnH, y + btnH, minHov ? 0xFF2A5A5A : tealDim);
            g.fill(minX, y + btnH - 1, minX + btnH, y + btnH, teal);
            g.drawCenteredString(font, "−", minX + btnH / 2, y + (btnH - 8) / 2, teal);
            regBtn(g, mx, my, minX, y, btnH, btnH, "", () -> {
                if (shapeIndex > 0) {
                    shapeIndex--;
                    if (definition != null)
                        PhantasiaVariantState.get().setShapeIndex(definition.getId().toString(), shapeIndex);
                    if (renderer != null) {
                        renderer.close();
                        renderer = null;
                    }
                    pattern = null;
                    invalidateFilterSets();
                    init();
                }
            });

            int valX = minX + btnH + 2;
            int valW = font.width("00/00") + 6;
            g.fill(valX, y, valX + valW, y + btnH, 0xFF1A2A2A);
            g.fill(valX, y + btnH - 1, valX + valW, y + btnH, teal);
            g.drawCenteredString(font, curL + "/" + maxL, valX + valW / 2, y + (btnH - 8) / 2, teal);

            int plusX = valX + valW + 2;
            boolean plusHov = mx >= plusX && mx < plusX + btnH && my >= y && my < y + btnH;
            g.fill(plusX, y, plusX + btnH, y + btnH, plusHov ? 0xFF2A5A5A : tealDim);
            g.fill(plusX, y + btnH - 1, plusX + btnH, y + btnH, teal);
            g.drawCenteredString(font, "+", plusX + btnH / 2, y + (btnH - 8) / 2, teal);
            regBtn(g, mx, my, plusX, y, btnH, btnH, "", () -> {
                if (shapeIndex < maxL - 1) {
                    shapeIndex++;
                    if (definition != null)
                        PhantasiaVariantState.get().setShapeIndex(definition.getId().toString(), shapeIndex);
                    if (renderer != null) {
                        renderer.close();
                        renderer = null;
                    }
                    pattern = null;
                    invalidateFilterSets();
                    init();
                }
            });
            y += 20;
        }

        boolean hasVariants = script != null &&
                script.getVariantGroups().stream().anyMatch(PhantasiaVariantGroup::hasChoice);
        if (hasVariants) {
            regIconBtn(g, mx, my, px + 10, y, pw - 20, 16, "⚙", "Variants",
                    false, this::openVariantsScreen);
            y += 20;
        }

        if (script != null && !script.getGlobalMistakes().isEmpty()) {
            regIconBtn(g, mx, my, px + 10, y, pw - 20, 16, "⚠", "Common Mistakes",
                    showMistakes, () -> showMistakes = !showMistakes);
            y += 20;
        }

        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getAbilities().instabuild) {
            regIconBtn(g, mx, my, px + 10, y, pw - 20, 16, "✏", "Edit Script",
                    false, this::openScriptEditor);
        }
    }

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
            float s = camera.getZoom() * CAM_PAN_ZOOM_SCALE;
            camera.pan(
                    (right.x * (float) -dx + up.x * (float) dy) * s,
                    (right.y * (float) -dx + up.y * (float) dy) * s,
                    (right.z * (float) -dx + up.z * (float) dy) * s);
            return true;
        }

        if (btn == 0) {

            float originalZoom = camera.getZoom();
            float targetX = camera.getTargetX();
            float targetY = camera.getTargetY();
            float targetZ = camera.getTargetZ();

            float orbitMult = PhantasiaConfigs.INSTANCE != null ?
                    PhantasiaConfigs.INSTANCE.phantasiaUI.cameraSensitivity : 1f;
            camera.orbit((float) dx * CAM_ORBIT_SENSITIVITY * orbitMult,
                    (float) dy * CAM_ORBIT_SENSITIVITY * orbitMult);

            camera.setTarget(targetX, targetY, targetZ);
            camera.setPosition(camera.getYaw(), camera.getPitch(), originalZoom);
            return true;
        }

        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= this.width - getCurrentPanelWidth()) return false;

        if (script != null) {
            PhantasiaScriptData sd = script.getSourceData();
            int stepIdx = sd != null ? script.getActiveStepIndex(playbackTick) : -1;
            if (stepIdx >= 0 && stepIdx < sd.getSteps().size()) {
                PhantasiaScriptData.StepData step = sd.getSteps().get(stepIdx);
                if (step.showItems && !step.items.isEmpty()) {
                    int pw = getCurrentPanelWidth();
                    int sipPanelX = this.width - pw - SIP_W - 4;
                    if (sipPanelX < 4) sipPanelX = 4;
                    int panelY = CAPTION_STRIP_H + 4;
                    int totalH = step.items.size() * SIP_ROW + 8;
                    int panelH = Math.min(totalH, this.height - panelY - 24);
                    if (mx >= sipPanelX && mx < sipPanelX + SIP_W && my >= panelY && my < panelY + panelH) {
                        sipScrollOffset = Mth.clamp(sipScrollOffset - (int) (delta * SIP_ROW), 0,
                                Math.max(0, totalH - panelH));
                        return true;
                    }
                }
            }
        }
        if (camera == null || camera.isLocked()) return false;
        float zoomMult = PhantasiaConfigs.INSTANCE != null ? PhantasiaConfigs.INSTANCE.phantasiaUI.scrollZoomSpeed : 1f;
        float zoomIn = 1f - (1f - CAM_ZOOM_IN_FACTOR) * zoomMult;
        float zoomOut = 1f + (CAM_ZOOM_OUT_FACTOR - 1f) * zoomMult;
        camera.zoom(delta > 0 ? Math.max(0.5f, zoomIn) : Math.min(2f, zoomOut),
                CAM_ZOOM_MIN, camZoomMax());
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

        float[] defaultTarget = resolveTarget();

        float dx = camera.getTargetX() - defaultTarget[0];
        float dy = camera.getTargetY() - defaultTarget[1];
        float dz = camera.getTargetZ() - defaultTarget[2];
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        int dynamicTicks = (int) Mth.clamp(12 + (distance * 0.4), 12, 26);

        resetCameraToDefault(LerpType.EASE_OUT, dynamicTicks);
    }

    private void nudgeLayer(int delta) {
        if (pattern == null) return;
        if (manualLayer < 0) {

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
            applyWorldItems(step);
            applyHighlights(step);

            boolean working = step != null && step.working() && playbackTick < script.getTotalTicks();
            machineWorking = working;
            applyActiveStateToWorld(working);
            if (renderer != null) renderer.requestBake();
        }
    }

    private void openScriptEditor() {
        if (camera != null) camera.save();
        String machineId = definition.getId().toString();
        PhantasiaScriptData current = script.getSourceData();
        if (current == null) current = PhantasiaScriptData.defaultFor(machineId);
        PhantasiaScriptEditorScreen editor = machineId.startsWith("ars_nouveau:") ?
                new ArsNouveauScriptEditorScreen(this, machineId, current) :
                new PhantasiaScriptEditorScreen(this, machineId, current);
        Minecraft.getInstance().setScreen(editor);
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

    public float getRotationYaw() {
        return camera != null ? camera.getYaw() : 0f;
    }

    public float getRotationPitch() {
        return camera != null ? camera.getPitch() : 0f;
    }

    public PhantasiaLoadedPattern getLoadedPattern() {
        return pattern;
    }

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

    private Set<BlockPos> buildVariantWorldPositions(PhantasiaVariantState vs) {
        if (pattern == null || script == null) return Set.of();
        Set<BlockPos> result = new HashSet<>();

        for (PhantasiaVariantGroup group : script.getVariantGroups()) {

            result.addAll(group.getPositionBaseIndex().keySet());
        }

        return result;
    }

    @Override
    public void onClose() {
        float secondsViewed = openedAtMs >= 0 ? (System.currentTimeMillis() - openedAtMs) / 1000f : 0f;
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new net.phoenixvine.phantasia.api.PhantasiaEvents.ViewerClose(definition, this, secondsViewed));
        openedAtMs = -1;

        if (asyncLoader != null) {
            asyncLoader.cancel();
            asyncLoader = null;
        }
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        net.phoenixvine.phantasia.client.render.PhantasiaParticleEngine.destroy();
        PhantasiaVariantState.get().clear();
        positionToVariantGroupCache.clear();
        cacheInitialized = false;
        machineWorking = false;
        invalidateSharedLevel();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
