package net.phoenixvine.phantasia.client.render;

import com.lowdragmc.lowdraglib.Platform;
import com.lowdragmc.lowdraglib.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib.client.utils.glu.Project;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.phoenixvine.phantasia.client.camera.CameraView;
import net.phoenixvine.phantasia.common.PhantasiaVariantState;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRender;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

/**
 * PhantasiaWorldRenderer
 *
 * ── Rendering model ───────────────────────────────────────────────────────────
 *
 * VISIBILITY → GPU bitmask (UBO or SSBO). setVisible() = bit rebuild + partial
 * glBufferSubData. ~1–5 µs regardless of pattern size.
 *
 * FULL BAKE → scheduleBake(). Bakes ALL pattern blocks once on initial load.
 * No temporary AIR, no per-step rebakes. Double-buffered.
 *
 * PARTIAL BAKE → requestPartialBake(changedPositions). Bakes only the specified
 * positions (e.g. coil blocks) and splices their geometry into the
 * existing front buffers using a per-layer overlay VBO. Avoids
 * rebuilding the entire structure for localised state changes.
 *
 * ACTIVE/WORKING → requestPartialBake(controllerPos ∪ overlayAnimatedPos).
 * Only controller + coil overlays change; structure geometry intact.
 *
 * COIL SWAP → requestPartialBake(allCoilPositions). Rebakes only the coil
 * positions (could be 200–2000 blocks) instead of all 3M.
 *
 * TILE ENTITIES → still rendered via immediate BER each frame; no bake involved.
 *
 * ── Partial bake detail ───────────────────────────────────────────────────────
 * A partial bake produces one overlay VertexBuffer per layer, plus an updated
 * block-ID VBO slice. On swap, the overlay VBOs are stored and drawn AFTER the
 * main front VBOs each frame, overpainting the stale geometry for the changed
 * blocks. The next full bake absorbs the overlay and clears it.
 *
 * Block IDs are stable so the visibility UBO continues to work correctly through
 * partial bakes — hidden coil blocks stay hidden even while being redrawn.
 */
public final class PhantasiaWorldRenderer {

    // ── GL scratch buffers (static — one per class, not per instance) ─────────

    private static final FloatBuffer SCRATCH_MV = direct(64).asFloatBuffer();
    private static final FloatBuffer SCRATCH_PROJ = direct(64).asFloatBuffer();
    private static final IntBuffer SCRATCH_VP = direct(16 * 4).asIntBuffer();
    private static final FloatBuffer PIXEL_DEPTH = direct(4).asFloatBuffer();
    private static final FloatBuffer UNPROJECT_OUT = direct(12).asFloatBuffer();


    // ── Production-Pack Modpack Profiler ──────────────────────────────────────
    private static final long PROFILE_WINDOW_NS = 5_000_000_000L; // Log every 5 seconds
    private long profileWindowStart = -1;
    private int profiledFramesCount = 0;
    private long totalRenderTimeNs = 0;
    private long maxRenderTimeNs = 0;

    // Baseline Phase Metrics
    private long totalSetupTimeNs = 0;
    private long totalVboTimeNs = 0;
    private long totalDynamicTimeNs = 0;
    private long totalParticleTickTimeNs = 0;
    private long totalRayTraceTimeNs = 0;

    // Micro-Optimizations Deep Metrics
    private long totalBerRenderTimeNs = 0; // Pure overhead inside individual machine render calls
    private long totalClipContextTimeNs = 0; // Cost of voxel clip steps inside your custom raytracer
    private int maxBerCountTracked = 0;
    private int maxRayIterationsTracked = 0;


    private long totalBeTimeNs = 0;
    private long totalParticleTimeNs = 0;

    private final float[] snapMV = new float[16];
    private final float[] snapProj = new float[16];
    private final int[] snapVP = new int[4];

    private static ByteBuffer direct(int bytes) {
        return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final float FOV = 60f;
    private static final float NEAR = 0.1f;
    private static final float FAR = 10_000f;
    private static final float ALPHA_STEP = 0.2f;
    private static final int TRANSITION_THRESHOLD = 32;

    // Reusable structures to completely eliminate heap allocation in the render loop
    private net.minecraft.world.phys.Vec3 mutableRayEye = net.minecraft.world.phys.Vec3.ZERO;

    // ── Layers ────────────────────────────────────────────────────────────────

    private final List<RenderType> LAYERS = RenderType.chunkBufferLayers();
    private final int LAYER_COUNT = LAYERS.size();

    // ── Double-buffered main geometry VBOs ────────────────────────────────────

    private final VertexBuffer[] front;
    private final VertexBuffer[] back;
    private volatile boolean backReady = false;

    // patternBlocks removed — raytrace uses bakedAll directly (same data, no duplication)

    // ── Partial-bake overlay VBOs ─────────────────────────────────────────────
    // After a partial bake, overlay[i] holds updated geometry for changed blocks.
    // Drawn AFTER front[i] each frame to overpaint stale geometry.

    private final VertexBuffer[] overlay;
    private volatile boolean overlayReady = false;

    // ── Bake coordination ─────────────────────────────────────────────────────

    private volatile boolean fullBakeNeeded = false;
    private volatile boolean partialBakePending = false;

    /** Positions queued for the next partial bake. Set from the main thread. */
    private volatile Set<BlockPos> partialBakePositions = Collections.emptySet();

    private final AtomicInteger pendingUploads = new AtomicInteger(0);

    @Nullable
    private Future<?> bakeFuture = null;

    // ── Visibility ────────────────────────────────────────────────────────────

    private Set<BlockPos> targetVisible = Collections.emptySet();
    private Set<BlockPos> bakedAll = Collections.emptySet();
    private Set<BlockPos> baseplatePositions = Collections.emptySet();
    private Set<BlockPos> animateTickEligible = Collections.emptySet();

    // ── Fade-in state (small transitions only) ────────────────────────────────

    private final Map<BlockPos, Float> blockAlpha = new HashMap<>();
    private final Map<BlockPos, List<RenderType>> blockLayers = new HashMap<>();
    private boolean hasTransitions = false;

    // ── Animated / tile-entity tracking ──────────────────────────────────────

    private Set<BlockPos> animatedPositions = Collections.emptySet();
    private volatile Set<BlockPos> backAnimatedPositions = null;
    private final Map<BlockPos, List<RenderType>> animatedLayers = new HashMap<>();

    private volatile Set<BlockPos> backTileEntities = null;
    private Set<BlockPos> frontTileEntities = Collections.emptySet();

    // ── Scene state ───────────────────────────────────────────────────────────

    private final TrackedDummyWorld world;
    @Nullable
    private BlockPos controllerWorldPos = null;

    /**
     * The slot origin this pattern was placed at (from PhantasiaSlotAllocator).
     *
     * All block geometry is baked at world-space slot coordinates (potentially
     * tens of thousands of blocks from 0,0). The modelview matrix is shifted by
     * the full float32 precision that GT's 0.001-block overlay offset requires.
     *
     * BlockPos values remain in dummy-world coordinate space.
     */
    private BlockPos slotOrigin = BlockPos.ZERO;

    // Keep a reference to the active layout pattern for exact boundary collision tests
    private net.phoenixvine.phantasia.common.PhantasiaLoadedPattern patternContext = null;

    public void setPatternContext(net.phoenixvine.phantasia.common.PhantasiaLoadedPattern pattern) {
        this.patternContext = pattern;
    }


    private int guiMouseX, guiMouseY;
    private long lastParticleTick = -1;
    private boolean tickedThisFrame = false;
    @Nullable
    private BlockHitResult lastHitResult;

    private final PhantasiaCameraEntity cameraEntity;
    private final Camera camera;

    // ── Bake pool ─────────────────────────────────────────────────────────────

    private static final ExecutorService BAKE_POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Phantasia-BakeThread");
        t.setDaemon(true);
        return t;
    });

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final boolean DEBUG_RENDER = false;
    private int debugFrameCounter = 0;

    // ── Constructor ───────────────────────────────────────────────────────────

    public PhantasiaWorldRenderer(TrackedDummyWorld world) {
        this.world = world;
        this.cameraEntity = new PhantasiaCameraEntity(world);
        this.camera = new Camera();

        this.front = new VertexBuffer[LAYER_COUNT];
        this.back = new VertexBuffer[LAYER_COUNT];
        this.overlay = new VertexBuffer[LAYER_COUNT];

        for (int i = 0; i < LAYER_COUNT; i++) {
            front[i] = new VertexBuffer(VertexBuffer.Usage.STATIC);
            back[i] = new VertexBuffer(VertexBuffer.Usage.STATIC);
            overlay[i] = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void setBaseplatePositions(Set<BlockPos> bp) {
        this.baseplatePositions = Set.copyOf(bp);
    }

    public void setControllerWorldPos(@Nullable BlockPos pos) {
        this.controllerWorldPos = pos;
    }

    /**
     * Sets the slot origin used to re-center block geometry on the GPU.
     * Must be called before the first {@link #render} call.
     * See field Javadoc for why this is needed.
     */
    public void setSlotOrigin(BlockPos slotOrigin) {
        this.slotOrigin = slotOrigin;
    }

    public void setPatternBlocks(Set<BlockPos> all) {
        this.bakedAll = Set.copyOf(all);
    }

    /**
     * Updates which blocks are visible. Schedules a full rebake of the visible set —
     * only visible blocks are baked into the VBOs, so hidden blocks simply have no
     * geometry submitted. No shader magic required.
     *
     * For small appearing diffs (≤ TRANSITION_THRESHOLD) a fade-in is used instead
     * of a rebake to avoid the 1-frame pop.
     */
    public void setVisible(Set<BlockPos> newVisible) {
        Set<BlockPos> old = targetVisible;
        targetVisible = Set.copyOf(newVisible);

        // Rebuild animateTick cache.
        Set<BlockPos> eligible = new HashSet<>();
        for (BlockPos pos : targetVisible) {
            BlockState state = world.getBlockState(pos);
            if (state.isRandomlyTicking() || state.getBlock().isRandomlyTicking(state))
                eligible.add(pos);
        }
        animateTickEligible = eligible.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(eligible);

        // Small fade-in for newly appearing blocks (skip rebake for tiny diffs).
        int appearing = 0;
        for (BlockPos pos : newVisible) if (!old.contains(pos)) appearing++;
        if (appearing > 0 && appearing <= TRANSITION_THRESHOLD) {
            BlockRenderDispatcher brd = Minecraft.getInstance().getBlockRenderer();
            RandomSource random = RandomSource.createNewThreadLocalInstance();
            for (BlockPos pos : newVisible) {
                if (!old.contains(pos) && blockAlpha.putIfAbsent(pos, 0f) == null) {
                    BlockState state = world.getBlockState(pos);
                    List<RenderType> layers = new ArrayList<>(2);
                    for (RenderType layer : LAYERS) {
                        if (WorldSceneRenderer.canRenderInLayer(brd, state, pos, world, layer, random)) {
                            layers.add(layer);
                            break;
                        }
                    }
                    blockLayers.put(pos, layers);
                }
            }
        }
        blockAlpha.keySet().removeIf(pos -> !newVisible.contains(pos));
        blockLayers.keySet().removeIf(pos -> !newVisible.contains(pos));
        hasTransitions = !blockAlpha.isEmpty();

        // Schedule a rebake so geometry reflects the new visible set.
        // Hidden blocks produce no vertices — visibility is purely geometric.
        // Also cancel any pending partial bake and clear any live overlay: a partial
        // bake's overlay VBOs may contain geometry for now-hidden blocks, which would
        // reappear until the full bake completes. Clearing them eagerly prevents that.
        partialBakePending = false;
        partialBakePositions = Collections.emptySet();
        overlayReady = false;
        fullBakeNeeded = true;
    }

    /**
     * Schedules a full geometry rebake (initial load, pattern structural change).
     * Use {@link #requestPartialBake} for localised changes like ACTIVE state or
     * coil swaps where only a subset of blocks changed.
     */
    public void requestBake() {
        fullBakeNeeded = true;
    }

    /**
     * Schedules a targeted partial rebake for a specific set of changed positions.
     *
     * Use this instead of requestBake() when only a subset of the pattern's block
     * states changed. Examples:
     * - Coil type swap: pass all coil positions (typically 200–2000 blocks).
     * - ACTIVE toggle: pass controller pos + any animated overlay positions.
     * - Variant switch: pass the positions in the toggled OptionalGroup.
     *
     * The partial bake produces overlay VBOs that overpaint the stale geometry
     * for the changed blocks without touching the rest of the baked scene. It runs
     * on the bake thread concurrently with other work.
     *
     * Note: if {@link #setVisible} is called after this, the pending partial bake
     * is discarded and a full visibility rebake runs instead.
     *
     * @param changedPositions world-space positions whose block states changed
     */
    public void requestPartialBake(Set<BlockPos> changedPositions) {
        if (changedPositions == null || changedPositions.isEmpty()) return;
        // If a full bake is already scheduled, partial is redundant — the full bake
        // will pick up the new states.
        if (fullBakeNeeded) return;
        // Merge with any already-queued partial positions (multiple rapid state
        // changes before the bake thread starts should all be captured).
        Set<BlockPos> existing = partialBakePositions;
        Set<BlockPos> merged = new HashSet<>(existing);
        merged.addAll(changedPositions);
        partialBakePositions = Set.copyOf(merged);
        partialBakePending = true;
    }

    /**
     * Full invalidation: cancels any in-flight bake, clears overlay and transition
     * state, and schedules a fresh full bake. Use when block states change in a way
     * that affects the whole scene (e.g. full pattern reload, shape switch).
     */
    public void invalidate() {
        if (bakeFuture != null && !bakeFuture.isDone()) bakeFuture.cancel(true);
        blockAlpha.clear();
        blockLayers.clear();
        animateTickEligible = Collections.emptySet();
        hasTransitions = false;
        overlayReady = false;
        partialBakePending = false;
        partialBakePositions = Collections.emptySet();
        fullBakeNeeded = true;
    }

    public void setMousePos(int mx, int my) {
        this.guiMouseX = mx;
        this.guiMouseY = my;
    }

    @Nullable
    public BlockHitResult getLastHitResult() {
        return lastHitResult;
    }

    public boolean isVisible(BlockPos pos) {
        return targetVisible.contains(pos) || baseplatePositions.contains(pos);
    }
    public void render(CameraView view, int guiX, int guiY, int guiW, int guiH) {
        if (guiW <= 0 || guiH <= 0) return;

        long renderStart = System.nanoTime();
        if (profileWindowStart == -1) {
            profileWindowStart = renderStart;
        }

        tickAlpha();

        if (backReady) swapFullBuffers();
        if (overlayReady) swapOverlayBuffers();

        PhantasiaSpriteMarker.markAll(Set.of());

        boolean bakeIdle = bakeFuture == null || bakeFuture.isDone();
        if (bakeIdle) {
            if (partialBakePending && !hasTransitions) {
                partialBakePending = false;
                Set<BlockPos> targets = partialBakePositions;
                partialBakePositions = Collections.emptySet();
                schedulePartialBake(targets);
            } else if (fullBakeNeeded && !hasTransitions) {
                fullBakeNeeded = false;
                scheduleFullBake();
            }
        }

        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScale();
        int windowH = mc.getWindow().getHeight();
        int glX = (int) (guiX * scale);
        int glY = (int) (windowH - (guiY + guiH) * scale);
        int glW = (int) (guiW * scale);
        int glH = (int) (guiH * scale);

        // ── PHASE 1: CAMERA & MATRICES SETUP ──
        long t0 = System.nanoTime();
        setupCamera(view, glX, glY, glW, glH);

        long totalTicks = mc.level != null ? mc.level.getGameTime() : 0;
        long currentTick = mc.level != null ? mc.level.getGameTime() : -1;
        boolean tick = currentTick >= 0 && currentTick != lastParticleTick;
        if (tick) lastParticleTick = currentTick;
        tickedThisFrame = tick;

        RenderSystem.setShaderGameTime(currentTick >= 0 ? currentTick : 0, ((currentTick >= 0 ? currentTick : 0) + mc.getFrameTime()) / 20f);
        snapshotMatrices();
        totalSetupTimeNs += (System.nanoTime() - t0);

        // ── PHASE 2: STATIC VBO GEOMETRY DRAW ──
        long t1 = System.nanoTime();
        drawVBOs();

        if (hasTransitions) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            MultiBufferSource.BufferSource dynBuffers = mc.renderBuffers().bufferSource();
            drawFadingIn(dynBuffers);
            dynBuffers.endBatch();
        }
        totalVboTimeNs += (System.nanoTime() - t1);

        // ── PHASE 3: BLOCK ENTITIES / MULTI-BUFFERS ──
        long t2 = System.nanoTime();
        float partial = mc.getFrameTime();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        turnOnLight(partial);
        float camX = view.eyeX(), camY = view.eyeY(), camZ = view.eyeZ();

        PoseStack currentPoseStack = RenderSystem.getModelViewStack();
        currentPoseStack.pushPose();

        drawTileEntities(currentPoseStack, buffers, partial, camX, camY, camZ);
        drawEntities(currentPoseStack, buffers, partial, camX, camY, camZ);
        drawDynamicRenderers(currentPoseStack, buffers, partial, camX, camY, camZ);

        RenderSystem.applyModelViewMatrix();
        buffers.endBatch();
        currentPoseStack.popPose();
        RenderSystem.applyModelViewMatrix();
        totalDynamicTimeNs += (System.nanoTime() - t2);

        // ── PHASE 4: PARTICLES & WORLD TICKING ──
        long t3 = System.nanoTime();
        if (!PhantasiaParticleEngine.isOculusBlockingParticles()) {
            try {
                Vec3 camPos = this.camera.getPosition();
                PoseStack mv = RenderSystem.getModelViewStack();
                mv.pushPose();
                mv.translate(camPos.x, camPos.y, camPos.z);
                RenderSystem.applyModelViewMatrix();
                PhantasiaParticleEngine.renderDirect(buffers, mc.gameRenderer.lightTexture(), this.camera, partial);
                buffers.endBatch();
                mv.popPose();
                RenderSystem.applyModelViewMatrix();
            } catch (Exception e) {
                LOGGER.error("[Phantasia] particle render failed", e);
            }
        }

        if (tickedThisFrame) {
            PhantasiaParticleEngine.tick();
            world.tickWorld();
            if (world instanceof PhantasiaTrackedDummyWorld ptdw && !animateTickEligible.isEmpty()) {
                RandomSource ar = RandomSource.createNewThreadLocalInstance();
                for (BlockPos pos : animateTickEligible) ptdw.tickAnimateForPos(pos, ar);
            }
        }
        totalParticleTickTimeNs += (System.nanoTime() - t3);

        turnOffLight();

        // ── PHASE 5: ADVANCED CPU RAY TRACING ──
        long t4 = System.nanoTime();
        lastHitResult = doRayTrace(view, scale, windowH);
        totalRayTraceTimeNs += (System.nanoTime() - t4);

        resetCamera();

        // ── RECORD SUMMARY ──
        long frameDurationNs = System.nanoTime() - renderStart;
        totalRenderTimeNs += frameDurationNs;
        maxRenderTimeNs = Math.max(maxRenderTimeNs, frameDurationNs);
        profiledFramesCount++;

        if (System.nanoTime() - profileWindowStart >= PROFILE_WINDOW_NS) {
            dumpProfilingData();
        }
    }


    private void dumpProfilingData() {
        if (profiledFramesCount == 0) return;

        double toMs = 1_000_000.0;
        double avgTotal = (totalRenderTimeNs / (double) profiledFramesCount) / toMs;
        double maxTotal = maxRenderTimeNs / toMs;

        double avgSetup = (totalSetupTimeNs / (double) profiledFramesCount) / toMs;
        double avgVbo = (totalVboTimeNs / (double) profiledFramesCount) / toMs;
        double avgDynamic = (totalDynamicTimeNs / (double) profiledFramesCount) / toMs;
        double avgParticles = (totalParticleTickTimeNs / (double) profiledFramesCount) / toMs;
        double avgRayTrace = (totalRayTraceTimeNs / (double) profiledFramesCount) / toMs;

        double microBerAvg = (totalBerRenderTimeNs / (double) profiledFramesCount) / toMs;
        double microClipAvg = (totalClipContextTimeNs / (double) profiledFramesCount) / toMs;

        LOGGER.info(String.format(
                "\n======= [PHANTASIA PRODUCTION PACK PROFILER] =======\n" +
                        " * Frames Tracked:       %d\n" +
                        " * Avg Frame Render:     %.3f ms  (Max Spike: %.3f ms)\n" +
                        " ─── Engine Phase Breakdown ───\n" +
                        "   -> Setup & Matrices:  %.3f ms\n" +
                        "   -> VBO Drawing:       %.3f ms\n" +
                        "   -> Dynamic & BERs:    %.3f ms\n" +
                        "   -> Particles & Ticks: %.3f ms\n" +
                        "   -> Ray Trace Lookup:  %.3f ms\n" +
                        " ─── Modpack Stress Diagnostics ───\n" +
                        "   -> Active Machine BERs Tracked: %d  (Pure Render Cost: %.3f ms)\n" +
                        "   -> Max Ray Intersection Depth:  %d  (Pure Clip Cost:   %.3f ms)\n" +
                        "===================================================",
                profiledFramesCount, avgTotal, maxTotal, avgSetup, avgVbo, avgDynamic, avgParticles, avgRayTrace,
                maxBerCountTracked, microBerAvg, maxRayIterationsTracked, microClipAvg
        ));

        // Reset trackers
        profileWindowStart = System.nanoTime();
        profiledFramesCount = 0;
        totalRenderTimeNs = 0;
        maxRenderTimeNs = 0;
        totalSetupTimeNs = 0;
        totalVboTimeNs = 0;
        totalDynamicTimeNs = 0;
        totalParticleTickTimeNs = 0;
        totalRayTraceTimeNs = 0;
        totalBerRenderTimeNs = 0;
        totalClipContextTimeNs = 0;
        maxBerCountTracked = 0;
        maxRayIterationsTracked = 0;
    }
    // ── Alpha tick ────────────────────────────────────────────────────────────

    private void tickAlpha() {
        if (blockAlpha.isEmpty()) return;
        Iterator<Map.Entry<BlockPos, Float>> it = blockAlpha.entrySet().iterator();
        boolean any = false;
        while (it.hasNext()) {
            Map.Entry<BlockPos, Float> e = it.next();
            float next = Math.min(1f, e.getValue() + ALPHA_STEP);
            if (next >= 1f) {
                it.remove();
                blockLayers.remove(e.getKey());
            } else {
                e.setValue(next);
                any = true;
            }
        }
        hasTransitions = any;
    }

    // ── Buffer swaps ──────────────────────────────────────────────────────────

    private void swapFullBuffers() {
        for (int i = 0; i < LAYER_COUNT; i++) {
            VertexBuffer tmp = front[i];
            front[i] = back[i];
            back[i] = tmp;
        }
        frontTileEntities = backTileEntities != null ? backTileEntities : Collections.emptySet();
        animatedPositions = backAnimatedPositions != null ? backAnimatedPositions : Collections.emptySet();
        backReady = false;
        backTileEntities = null;
        backAnimatedPositions = null;
        // Full bake absorbs any overlay that was previously applied.
        overlayReady = false;
    }

    private void swapOverlayBuffers() {
        // overlay[] is already the "back" of the overlay pair — just mark ready.
        // We don't swap VBOs here because overlay[] is double-used (back and front
        // are the same objects; we upload to overlay[i] directly on the render thread
        // via recordRenderCall, so no second copy needed).
        overlayReady = false; // consumed; drawVBOs() will draw it until next full bake
    }

    private static Set<BlockPos> union(Set<BlockPos> a, Set<BlockPos> b) {
        if (b.isEmpty()) return a;
        Set<BlockPos> r = new HashSet<>(a);
        r.addAll(b);
        return r;
    }

    // ── Full bake ─────────────────────────────────────────────────────────────

    private void scheduleFullBake() {
        // Only bake visible blocks + baseplate. Hidden blocks produce no geometry —
        // this is how visibility is enforced without any shader machinery.
        Set<BlockPos> visible = new HashSet<>(targetVisible);
        visible.addAll(baseplatePositions);
        visible.retainAll(bakedAll);
        Set<BlockPos> snapshot = Set.copyOf(visible);
        if (snapshot.isEmpty()) {
            uploadEmptyBuffers(false);
            return;
        }

        pendingUploads.set(LAYER_COUNT);
        bakeFuture = BAKE_POOL.submit(() -> {
            Minecraft mc = Minecraft.getInstance();
            BlockRenderDispatcher brd = mc.getBlockRenderer();
            RandomSource random = RandomSource.createNewThreadLocalInstance();
            ModelBlockRenderer.enableCaching();

            PhantasiaVariantState vs = PhantasiaVariantState.get();
            Map<BlockPos, BlockInfo> variantSaved = applyVariants(vs, snapshot);
            // Mask hidden blocks as AIR so AO/face-culling queries during baking
            // don't see them as solid neighbours and cull exposed faces incorrectly.
            Map<BlockPos, BlockInfo> hiddenSaved = maskHiddenBlocks(snapshot);

            Map<RenderType, List<BlockPos>> solidBuckets = new HashMap<>(LAYER_COUNT);
            Map<RenderType, List<BlockPos>> fluidBuckets = new HashMap<>(LAYER_COUNT);
            bucket(brd, random, snapshot, solidBuckets, fluidBuckets);

            Set<BlockPos> animatedBack = new HashSet<>();
            try {
                Semaphore slot = new Semaphore(0);
                for (int i = 0; i < LAYER_COUNT; i++) {
                    if (Thread.interrupted()) return;
                    BakedLayer bl = bakeLayerToBuffer(brd, random, LAYERS.get(i),
                            solidBuckets.getOrDefault(LAYERS.get(i), List.of()),
                            fluidBuckets.getOrDefault(LAYERS.get(i), List.of()),
                            animatedBack);
                    final int fi = i;
                    RenderSystem.recordRenderCall(() -> {
                        try {
                            uploadToVBO(back[fi], bl);
                            if (pendingUploads.decrementAndGet() == 0) backReady = true;
                        } finally {
                            slot.release();
                        }
                    });
                    try {
                        slot.acquire();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            } finally {
                ModelBlockRenderer.clearCache();
                restoreVariants(hiddenSaved);
                restoreVariants(variantSaved);
            }
            backAnimatedPositions = animatedBack;
            Set<BlockPos> tes = new HashSet<>();
            for (BlockPos pos : snapshot) {
                if (Thread.interrupted()) return;
                BlockEntity be = world.getBlockEntity(pos);
                if (be != null && mc.getBlockEntityRenderDispatcher().getRenderer(be) != null)
                    tes.add(pos);
            }
            backTileEntities = tes;
        });
    }

    // ── Partial bake ─────────────────────────────────────────────────────────

    /**
     * Bakes only {@code targets} and uploads the results into the overlay VBOs.
     * The overlay is drawn on top of the existing front VBOs each frame, overpainting
     * the stale geometry for the changed blocks without touching the rest of the scene.
     *
     * Partial bakes are safe concurrently with normal rendering: the overlay VBOs are
     * separate from front[] and are only swapped in via overlayReady after the upload
     * is complete, so there is never a half-uploaded frame.
     */
    private void schedulePartialBake(Set<BlockPos> targets) {
        // Intersect targets with bakedAll so we never bake positions that aren't in
        // the pattern (e.g. stale positions from a previous shape variant).
        Set<BlockPos> valid = new HashSet<>(targets);
        valid.retainAll(bakedAll);
        if (valid.isEmpty()) return;

        // Build the full visibility snapshot (same logic as scheduleFullBake) so the
        // masking is consistent — partial-baked blocks must see the same neighbour
        // states they would see in a full bake.
        Set<BlockPos> fullVisible = new HashSet<>(targetVisible);
        fullVisible.addAll(baseplatePositions);
        fullVisible.retainAll(bakedAll);
        Set<BlockPos> fullVisibleSnapshot = Set.copyOf(fullVisible);

        pendingUploads.set(LAYER_COUNT);
        bakeFuture = BAKE_POOL.submit(() -> {
            Minecraft mc = Minecraft.getInstance();
            BlockRenderDispatcher brd = mc.getBlockRenderer();
            RandomSource random = RandomSource.createNewThreadLocalInstance();
            ModelBlockRenderer.enableCaching();

            PhantasiaVariantState vs = PhantasiaVariantState.get();
            Map<BlockPos, BlockInfo> variantSaved = applyVariants(vs, valid);
            // Mask hidden blocks as AIR so AO/face-culling queries during baking
            // don't see them as solid neighbours and cull exposed faces incorrectly.
            Map<BlockPos, BlockInfo> hiddenSaved = maskHiddenBlocks(fullVisibleSnapshot);

            Map<RenderType, List<BlockPos>> solidBuckets = new HashMap<>(LAYER_COUNT);
            Map<RenderType, List<BlockPos>> fluidBuckets = new HashMap<>(LAYER_COUNT);
            bucket(brd, random, valid, solidBuckets, fluidBuckets);

            Set<BlockPos> animatedBack = new HashSet<>();
            try {
                Semaphore slot = new Semaphore(0);
                for (int i = 0; i < LAYER_COUNT; i++) {
                    if (Thread.interrupted()) return;
                    BakedLayer bl = bakeLayerToBuffer(brd, random, LAYERS.get(i),
                            solidBuckets.getOrDefault(LAYERS.get(i), List.of()),
                            fluidBuckets.getOrDefault(LAYERS.get(i), List.of()),
                            animatedBack);
                    final int fi = i;
                    RenderSystem.recordRenderCall(() -> {
                        try {
                            // Upload into the overlay VBOs (not back[]).
                            uploadToVBO(overlay[fi], bl);
                            if (pendingUploads.decrementAndGet() == 0) overlayReady = true;
                        } finally {
                            slot.release();
                        }
                    });
                    try {
                        slot.acquire();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            } finally {
                ModelBlockRenderer.clearCache();
                restoreVariants(hiddenSaved);
                restoreVariants(variantSaved);
            }
            // Partial bake doesn't update frontTileEntities — BER renders are
            // immediate-mode and pick up the new block state automatically.
        });
    }

    // ── Bake helpers ──────────────────────────────────────────────────────────

    /** Groups positions into per-layer solid and fluid buckets. */
    private void bucket(BlockRenderDispatcher brd, RandomSource random,
                        Set<BlockPos> positions,
                        Map<RenderType, List<BlockPos>> solidOut,
                        Map<RenderType, List<BlockPos>> fluidOut) {
        for (BlockPos pos : positions) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() == Blocks.AIR) continue;
            if (state.getRenderShape() != RenderShape.INVISIBLE) {
                for (RenderType layer : LAYERS) {
                    if (WorldSceneRenderer.canRenderInLayer(brd, state, pos, world, layer, random)) {
                        solidOut.computeIfAbsent(layer, k -> new ArrayList<>()).add(pos);
                        break;
                    }
                }
            }
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty()) {
                RenderType fl = net.minecraft.client.renderer.ItemBlockRenderTypes.getRenderLayer(fluid);
                fluidOut.computeIfAbsent(fl, k -> new ArrayList<>()).add(pos);
            }
        }
    }

    /** Bakes one render layer for the given solid and fluid positions. */
    private BakedLayer bakeLayerToBuffer(BlockRenderDispatcher brd, RandomSource random,
                                         RenderType layer,
                                         List<BlockPos> solid, List<BlockPos> fluid,
                                         Set<BlockPos> animatedOut) {
        int count = solid.size() + fluid.size();
        int sizeHint = Math.max(layer.bufferSize(), Math.min(count * 512, 64 * 1024 * 1024));
        BufferBuilder bb = new BufferBuilder(sizeHint);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        PoseStack ps = new PoseStack();
        TintedVertexConsumer tinted = new TintedVertexConsumer(bb);

        for (BlockPos pos : solid) {
            BlockState state = world.getBlockState(pos);
            ps.pushPose();
            ps.translate(pos.getX(), pos.getY(), pos.getZ());
            if (Platform.isForge()) {
                WorldSceneRenderer.renderBlocksForge(brd, state, pos, world, ps, tinted, random, layer);
            } else {
                brd.renderBatched(state, pos, world, ps, tinted, true, random);
            }
            ps.popPose();
            tinted.resetTint();
            if (state.isRandomlyTicking() || state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock) {
                animatedOut.add(pos);
            }
        }

        for (BlockPos pos : fluid) {
            BlockState state = world.getBlockState(pos);
            FluidState fs = state.getFluidState();
            if (fs.isEmpty()) continue;
            tinted.addOffset(pos.getX() - (pos.getX() & 15),
                    pos.getY() - (pos.getY() & 15),
                    pos.getZ() - (pos.getZ() & 15));
            brd.renderLiquid(pos, world, tinted, state, fs);
            tinted.clearOffset();
            tinted.resetTint();
        }

        return new BakedLayer(bb.end());
    }

    private void uploadToVBO(VertexBuffer vbo, BakedLayer bl) {
        if (!vbo.isInvalid()) {
            vbo.bind();
            vbo.upload(bl.renderedBuffer());
            VertexBuffer.unbind();
        }
    }

    private void uploadEmptyBuffers(boolean intoOverlay) {
        pendingUploads.set(LAYER_COUNT);
        for (int i = 0; i < LAYER_COUNT; i++) {
            RenderType layer = LAYERS.get(i);
            BufferBuilder bb = new BufferBuilder(layer.bufferSize());
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            BufferBuilder.RenderedBuffer rb = bb.end();
            final int fi = i;
            RenderSystem.recordRenderCall(() -> {
                VertexBuffer target = intoOverlay ? overlay[fi] : back[fi];
                if (!target.isInvalid()) {
                    target.bind();
                    target.upload(rb);
                    VertexBuffer.unbind();
                }
                if (pendingUploads.decrementAndGet() == 0) {
                    if (intoOverlay) overlayReady = true;
                    else backReady = true;
                }
            });
        }
        if (!intoOverlay) {
            backTileEntities = Collections.emptySet();
            backAnimatedPositions = Collections.emptySet();
        }
    }

    /** Temporarily writes variant-resolved states into renderedBlocks. Returns a save map to restore later. */
    private Map<BlockPos, BlockInfo> applyVariants(PhantasiaVariantState vs, Set<BlockPos> positions) {
        Map<BlockPos, BlockInfo> saved = new HashMap<>();
        for (BlockPos vp : positions) {
            BlockInfo baseInfo = world.renderedBlocks.get(vp);
            if (baseInfo == null) continue;
            BlockState base = baseInfo.getBlockState();
            if (base == null || base.isAir()) continue;
            BlockState resolved = vs.resolveState(vp, base);
            if (resolved != base) {
                saved.put(vp, baseInfo);
                world.renderedBlocks.put(vp, BlockInfo.fromBlockState(resolved));
            }
        }
        return saved;
    }

    private void restoreVariants(Map<BlockPos, BlockInfo> saved) {
        for (Map.Entry<BlockPos, BlockInfo> e : saved.entrySet())
            world.renderedBlocks.put(e.getKey(), e.getValue());
    }

    /**
     * Temporarily replaces all blocks in {@code bakedAll} that are NOT in
     * {@code visibleSnapshot} with AIR in {@code world.renderedBlocks}.
     *
     * This prevents Minecraft's AO / face-culling logic from seeing hidden
     * blocks as solid neighbours during baking, which would otherwise cause
     * faces on the boundary of visible/hidden blocks to be incorrectly culled,
     * producing the black-void gap seen when blocks are hidden.
     *
     * Restore the returned map via {@link #restoreVariants} in a finally block.
     */
    private Map<BlockPos, BlockInfo> maskHiddenBlocks(Set<BlockPos> visibleSnapshot) {
        Map<BlockPos, BlockInfo> saved = new HashMap<>();
        BlockInfo air = BlockInfo.fromBlockState(Blocks.AIR.defaultBlockState());
        for (BlockPos pos : bakedAll) {
            if (!visibleSnapshot.contains(pos)) {
                BlockInfo existing = world.renderedBlocks.get(pos);
                if (existing != null && !existing.getBlockState().isAir()) {
                    saved.put(pos, existing);
                    world.renderedBlocks.put(pos, air);
                }
            }
        }
        return saved;
    }

    private record BakedLayer(BufferBuilder.RenderedBuffer renderedBuffer) {}

    // ── VBO draw ──────────────────────────────────────────────────────────────

    private void drawVBOs() {
        for (int i = 0; i < LAYER_COUNT; i++) {
            VertexBuffer vbo = front[i];
            RenderType layer = LAYERS.get(i);
            if (vbo.isInvalid() || vbo.getFormat() == null) continue;

            layer.setupRenderState();
            applyLayerBlend(layer);

            ShaderInstance shader = RenderSystem.getShader();
            if (shader == null) {
                layer.clearRenderState();
                continue;
            }

            bindShaderSamplers(shader);
            setShaderUniforms(shader);
            RenderSystem.setupShaderLights(shader);
            shader.apply();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            vbo.bind();
            vbo.draw();

            // Draw overlay on top if one is live.
            if ((overlayReady || hasLiveOverlay()) && isOverlayLayerReady(i)) {
                overlay[i].bind();
                overlay[i].draw();
            }

            VertexBuffer.unbind();
            shader.clear();
            layer.clearRenderState();
        }
    }

    /** True when any overlay layer has been uploaded and not yet replaced by a full bake. */
    private boolean hasLiveOverlay() {
        for (int i = 0; i < LAYER_COUNT; i++) {
            if (isOverlayLayerReady(i)) return true;
        }
        return false;
    }

    /** True when overlay[i] has been successfully uploaded (format non-null). */
    private boolean isOverlayLayerReady(int i) {
        VertexBuffer ov = overlay[i];
        return ov != null && !ov.isInvalid() && ov.getFormat() != null;
    }

    // ── Fade-in draw ──────────────────────────────────────────────────────────

    private void drawFadingIn(MultiBufferSource.BufferSource buffers) {
        if (blockAlpha.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher brd = mc.getBlockRenderer();
        RandomSource random = RandomSource.createNewThreadLocalInstance();
        PoseStack ps = new PoseStack();
        Map<RenderType, TintedVertexConsumer> consumers = new IdentityHashMap<>(LAYER_COUNT);
        for (Map.Entry<BlockPos, Float> e : blockAlpha.entrySet()) {
            BlockPos pos = e.getKey();
            float alpha = e.getValue();
            if (alpha <= 0.005f) continue;
            BlockState state = world.getBlockState(pos);
            if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) continue;
            List<RenderType> layers = blockLayers.get(pos);
            if (layers == null || layers.isEmpty()) continue;
            for (RenderType layer : layers) {
                TintedVertexConsumer tinted = consumers.computeIfAbsent(layer,
                        l -> new TintedVertexConsumer(buffers.getBuffer(l)));
                tinted.setAlpha(alpha);
                ps.pushPose();
                ps.translate(pos.getX(), pos.getY(), pos.getZ());
                if (Platform.isForge())
                    WorldSceneRenderer.renderBlocksForge(brd, state, pos, world, ps, tinted, random, layer);
                else brd.renderBatched(state, pos, world, ps, tinted, true, random);
                ps.popPose();
                tinted.resetTint();
            }
        }
    }

    // ── clientTick via MethodHandle ───────────────────────────────────────────

    private static final java.lang.invoke.MethodHandle NO_OP;
    static {
        try {
            NO_OP = java.lang.invoke.MethodHandles.lookup().findStatic(PhantasiaWorldRenderer.class, "noOp",
                    java.lang.invoke.MethodType.methodType(void.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void noOp() {}

    private static final ClassValue<java.lang.invoke.MethodHandle> GET_MACHINE = new ClassValue<>() {

        @Override
        protected java.lang.invoke.MethodHandle computeValue(Class<?> t) {
            var lookup = java.lang.invoke.MethodHandles.lookup();
            for (String n : new String[] { "getMetaMachine", "getMachine", "getOwner" }) {
                try {
                    return lookup.unreflect(t.getMethod(n));
                } catch (Exception ignored) {}
            }
            return NO_OP;
        }
    };
    private static final ClassValue<java.lang.invoke.MethodHandle> CLIENT_TICK = new ClassValue<>() {

        @Override
        protected java.lang.invoke.MethodHandle computeValue(Class<?> c) {
            var lookup = java.lang.invoke.MethodHandles.lookup();
            for (Class<?> cur = c; cur != null; cur = cur.getSuperclass()) {
                try {
                    var m = cur.getDeclaredMethod("clientTick");
                    m.setAccessible(true);
                    return lookup.unreflect(m);
                } catch (Exception ignored) {}
            }
            return NO_OP;
        }
    };

    private static void driveClientTick(BlockEntity be) {
        try {
            var gm = GET_MACHINE.get(be.getClass());
            if (gm == NO_OP) return;
            Object machine = gm.invoke(be);
            if (machine == null) return;
            var tick = CLIENT_TICK.get(machine.getClass());
            if (tick == NO_OP) return;
            tick.invoke(machine);
        } catch (Throwable t) {
            LOGGER.warn("[Phantasia] driveClientTick: {}", t.getMessage());
        }
    }

    private void drawTileEntities(PoseStack poseStack, MultiBufferSource.BufferSource buffers, float partial,
                                  float camX, float camY, float camZ) {
        Minecraft mc = Minecraft.getInstance();
        var dispatcher = mc.getBlockEntityRenderDispatcher();
        int count = 0;

        for (BlockPos pos : frontTileEntities) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be == null || be.isRemoved()) continue;

            // Direct pointer fetch from dispatcher cache
            BlockEntityRenderer<BlockEntity> ber = dispatcher.getRenderer(be);
            if (ber == null) continue;

            poseStack.pushPose();
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            count++;

            long berStart = System.nanoTime();
            try {
                ber.render(be, partial, poseStack, buffers, 15728880, OverlayTexture.NO_OVERLAY);
                if (tickedThisFrame) driveClientTick(be);
            } catch (Exception e) {
                LOGGER.warn("[Phantasia] BE render error at {}: {}", pos, e.getMessage());
            }
            totalBerRenderTimeNs += (System.nanoTime() - berStart);

            poseStack.popPose();
        }
        maxBerCountTracked = Math.max(maxBerCountTracked, count);
    }

    // ── DynamicRender draw (GTCEu ring/overlay renderers) ────────────────────
    // GTCEu's DynamicRender (e.g. FusionRingRender) is not a standard BER — it
    // is registered on the machine definition's IRenderer and invoked separately
    // by GT's own level renderer. We replicate that call here so effects like the
    // fusion plasma ring show up in the preview renderer.
    //
    // We use reflection because DynamicRender is generic-typed to the concrete
    // machine subclass (DynamicRender<FusionReactorMachine, ...>) and there is no
    // single non-generic accessor on MetaMachine to retrieve it. The actual render
    // method signature is always render(MachineSelf, float, PoseStack,
    // MultiBufferSource, int, int) — safe to invoke reflectively.

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void drawDynamicRenderers(PoseStack poseStack, MultiBufferSource.BufferSource buffers, float partial,
                                      float camX, float camY, float camZ) {
        Vec3 cameraPos = new Vec3(camX, camY, camZ);
        for (BlockPos pos : frontTileEntities) {
            BlockEntity be = world.getBlockEntity(pos);
            if (!(be instanceof MetaMachineBlockEntity mmbe)) continue;
            MetaMachine machine = mmbe.getMetaMachine();
            if (machine == null) continue;

            // DynamicRender instances are stored as fields on the IRenderer implementation
            // (e.g. WorkableCasingMachineRenderer). There is no public accessor on MetaMachine
            // or IRenderer — scan declared fields of the renderer for any DynamicRender
            // instances or List<DynamicRender> collections.
            // IRenderer is retrieved via the MachineDefinition using reflection,
            // since MachineDefinition holds a renderer field set by the builder but
            // not exposed via a public getRenderer() method in this GT version.
            com.lowdragmc.lowdraglib.client.renderer.IRenderer iRenderer = null;
            try {
                com.gregtechceu.gtceu.api.machine.MachineDefinition def = machine.getDefinition();
                for (Class<?> defCls = def.getClass(); defCls != null && defCls != Object.class; defCls = defCls.getSuperclass()) {
                    for (java.lang.reflect.Field f : defCls.getDeclaredFields()) {
                        if (com.lowdragmc.lowdraglib.client.renderer.IRenderer.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            iRenderer = (com.lowdragmc.lowdraglib.client.renderer.IRenderer) f.get(def);
                            break;
                        }
                    }
                    if (iRenderer != null) break;
                }
            } catch (Exception ignored) {}
            if (iRenderer == null) continue;

            // Walk the full class hierarchy of the renderer to find DynamicRender fields.
            for (Class<?> cls = iRenderer.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
                for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(iRenderer);
                        if (val == null) continue;
                        if (val instanceof DynamicRender<?,?> dr) {
                            renderOneDynamic(dr, machine, pos, cameraPos, partial, poseStack, buffers);
                        } else if (val instanceof java.util.List<?> list) {
                            for (Object item : list) {
                                if (item instanceof DynamicRender<?,?> dr) {
                                    renderOneDynamic(dr, machine, pos, cameraPos, partial, poseStack, buffers);
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void renderOneDynamic(DynamicRender dr, MetaMachine machine, BlockPos pos,
                                  Vec3 cameraPos, float partial,
                                  PoseStack poseStack, MultiBufferSource.BufferSource buffers) {
        try {
            if (!dr.shouldRender(machine, cameraPos)) return;
        } catch (Throwable ignored) {}

        poseStack.pushPose();
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        try {
            dr.render(machine, partial, poseStack, buffers,
                    15728880, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        } catch (Throwable e) {
            LOGGER.warn("[Phantasia] DynamicRender error at {}: {}", pos, e.getMessage());
        }
        poseStack.popPose();
    }

    // ── Entity draw ───────────────────────────────────────────────────────────

    private void drawEntities(PoseStack poseStack, MultiBufferSource.BufferSource buffers, float partial,
                              float camX, float camY, float camZ) {
        var erd = Minecraft.getInstance().getEntityRenderDispatcher();
        for (Entity entity : world.getAllEntities()) {
            try {
                if (controllerWorldPos != null && Math.abs(entity.getX()) < 1.0 &&
                        Math.abs(entity.getY()) < 1.0 && Math.abs(entity.getZ()) < 1.0) {
                    entity.setPos(controllerWorldPos.getX() + 0.5,
                            controllerWorldPos.getY() + 0.5,
                            controllerWorldPos.getZ() + 0.5);
                    entity.xOld = entity.getX();
                    entity.yOld = entity.getY();
                    entity.zOld = entity.getZ();
                }
                double d0 = net.minecraft.util.Mth.lerp(partial, entity.xOld, entity.getX());
                double d1 = net.minecraft.util.Mth.lerp(partial, entity.yOld, entity.getY());
                double d2 = net.minecraft.util.Mth.lerp(partial, entity.zOld, entity.getZ());
                float yRot = net.minecraft.util.Mth.lerp(partial, entity.yRotO, entity.getYRot());

                // FIX: Push onto the global matrix stack
                poseStack.pushPose();

                erd.render(entity, d0, d1, d2, yRot, partial, poseStack, buffers,
                        erd.getRenderer(entity).getPackedLightCoords(entity, partial));

                // FIX: Pop off the global matrix stack
                poseStack.popPose();
            } catch (Exception ignored) {}
        }
    }

    // ── Light texture ─────────────────────────────────────────────────────────

    private void turnOnLight(float p) {
        try {
            Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
        } catch (Exception ignored) {}
    }

    private void turnOffLight() {
        try {
            Minecraft.getInstance().gameRenderer.lightTexture().turnOffLightLayer();
        } catch (Exception ignored) {}
    }

    // ── Camera ────────────────────────────────────────────────────────────────

    private void setupCamera(CameraView view, int glX, int glY, int glW, int glH) {
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.viewport(glX, glY, glW, glH);
        RenderSystem.depthMask(true);
        RenderSystem.clearColor(0f, 0f, 0f, 0f);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.backupProjectionMatrix();
        float aspect = (float) glW / glH;
        RenderSystem.setProjectionMatrix(
                new Matrix4f().setPerspective((float) Math.toRadians(FOV), aspect, NEAR, FAR),
                VertexSorting.byDistance(new Vector3f(view.eyeX(), view.eyeY(), view.eyeZ())));
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        Project.gluLookAt(mv, view.eyeX(), view.eyeY(), view.eyeZ(),
                view.lookAtX(), view.lookAtY(), view.lookAtZ(), 0f, 1f, 0f);

        // ── Precision fix ────────────────────────────────────────────────────
        // Block geometry is baked at slot-space coordinates (origin may be tens of
        // thousands of blocks away). Translating the modelview matrix by the negative origin
        // re-centres everything to near-zero before the vertex shader sees it,
        // preserving the float32 sub-block precision that GT's 0.001-block overlay
        // offset requires to render without z-fighting or disappearing overlays.
        // ─────────────────────────────────────────────────────────────────────
        if (this.slotOrigin != null) {
            mv.translate(-this.slotOrigin.getX(), -this.slotOrigin.getY(), -this.slotOrigin.getZ());
        }

        RenderSystem.applyModelViewMatrix();
        RenderSystem.activeTexture(33984);
        syncCameraEntity(view);
        camera.setup(world, cameraEntity, false, false, Minecraft.getInstance().getFrameTime());
    }

    private void resetCamera() {
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        Minecraft mc = Minecraft.getInstance();
        RenderSystem.viewport(0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight());
        RenderSystem.restoreProjectionMatrix();
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
    }

    private void syncCameraEntity(CameraView view) {
        Vector3f dir = view.direction();
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x(), dir.z()));
        float hd = (float) Math.sqrt(dir.x() * dir.x() + dir.z() * dir.z());
        float pitch = (float) Math.toDegrees(Math.atan2(-dir.y(), hd));
        cameraEntity.setPos(view.eyeX(), view.eyeY(), view.eyeZ());
        cameraEntity.setYRot(yaw);
        cameraEntity.setXRot(pitch);
        cameraEntity.xo = cameraEntity.getX();
        cameraEntity.yo = cameraEntity.getY();
        cameraEntity.zo = cameraEntity.getZ();
        cameraEntity.yRotO = yaw;
        cameraEntity.xRotO = pitch;
    }

    // ── Matrix snapshot ───────────────────────────────────────────────────────

    private void snapshotMatrices() {
        RenderSystem.getModelViewMatrix().get(SCRATCH_MV);
        SCRATCH_MV.rewind();
        RenderSystem.getProjectionMatrix().get(SCRATCH_PROJ);
        SCRATCH_PROJ.rewind();
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, SCRATCH_VP);
        SCRATCH_VP.rewind();
        for (int i = 0; i < 16; i++) snapMV[i] = SCRATCH_MV.get(i);
        for (int i = 0; i < 16; i++) snapProj[i] = SCRATCH_PROJ.get(i);
        for (int i = 0; i < 4; i++) snapVP[i] = SCRATCH_VP.get(i);
        SCRATCH_MV.rewind();
        SCRATCH_PROJ.rewind();
        SCRATCH_VP.rewind();
    }

    private BlockHitResult doRayTrace(CameraView view, double guiScale, int windowH) {
        int glX = (int) (guiMouseX * guiScale);
        int glY = (int) (windowH - guiMouseY * guiScale);
        if (glX < snapVP[0] || glX > snapVP[0] + snapVP[2] || glY < snapVP[1] || glY > snapVP[1] + snapVP[3])
            return null;

        float depth = 1.0f;

        // The snapped modelview includes translate(-slotOrigin) after the lookAt.
        // To get hx/hy/hz in the same space as view.eyeX/Y/Z() (dummy-world coords),
        // we must unproject using a matrix that does NOT include the slot translation.
        // Build that matrix: apply lookAt from scratch using the eye/lookat positions.
        Matrix4f lookAtOnly = new Matrix4f();
        {
            // Reproduce the gluLookAt that setupCamera applied, minus the slot translate.
            float ex = view.eyeX(), ey = view.eyeY(), ez = view.eyeZ();
            float lx = view.lookAtX(), ly = view.lookAtY(), lz = view.lookAtZ();
            float fx = lx - ex, fy = ly - ey, fz = lz - ez;
            float flen = (float) Math.sqrt(fx*fx + fy*fy + fz*fz);
            if (flen > 0) { fx /= flen; fy /= flen; fz /= flen; }
            // up = (0,1,0), right = forward x up
            float rx = fy*0 - fz*1, ry = fz*0 - fx*0, rz = fx*1 - fy*0;
            float rlen = (float) Math.sqrt(rx*rx + ry*ry + rz*rz);
            if (rlen > 0) { rx /= rlen; ry /= rlen; rz /= rlen; }
            float ux = ry*fz - rz*fy, uy = rz*fx - rx*fz, uz = rx*fy - ry*fx;
            lookAtOnly.set(
                    rx,  ux, -fx, 0,
                    ry,  uy, -fy, 0,
                    rz,  uz, -fz, 0,
                    -(rx*ex + ry*ey + rz*ez), -(ux*ex + uy*ey + uz*ez), (fx*ex + fy*ey + fz*ez), 1
            );
        }
        FloatBuffer mvNoSlot = java.nio.ByteBuffer.allocateDirect(64).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
        lookAtOnly.get(mvNoSlot);
        mvNoSlot.rewind();

        for (int i = 0; i < 16; i++) SCRATCH_PROJ.put(i, snapProj[i]);
        for (int i = 0; i < 4; i++) SCRATCH_VP.put(i, snapVP[i]);
        SCRATCH_PROJ.rewind();
        SCRATCH_VP.rewind();
        Project.gluUnProject(glX, glY, depth, mvNoSlot, SCRATCH_PROJ, SCRATCH_VP, UNPROJECT_OUT);
        SCRATCH_PROJ.rewind();
        SCRATCH_VP.rewind();
        UNPROJECT_OUT.rewind();
        float hx = UNPROJECT_OUT.get(), hy = UNPROJECT_OUT.get(), hz = UNPROJECT_OUT.get();
        UNPROJECT_OUT.rewind();

        long clipStart = System.nanoTime();
        try {
            double startX = view.eyeX();
            double startY = view.eyeY();
            double startZ = view.eyeZ();

            double dirX = hx - startX;
            double dirY = hy - startY;
            double dirZ = hz - startZ;

            double len = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
            if (len == 0) return null;
            dirX /= len; dirY /= len; dirZ /= len;

            // Current integer voxel coordinates
            int x = net.minecraft.util.Mth.floor(startX);
            int y = net.minecraft.util.Mth.floor(startY);
            int z = net.minecraft.util.Mth.floor(startZ);

            // Direction signs (+1 or -1 block movement steps)
            int stepX = (dirX > 0) ? 1 : -1;
            int stepY = (dirY > 0) ? 1 : -1;
            int stepZ = (dirZ > 0) ? 1 : -1;

            // How far along the ray vector we must travel to cross a full block width along each axis
            double deltaX = (dirX == 0) ? Double.MAX_VALUE : Math.abs(1.0 / dirX);
            double deltaY = (dirY == 0) ? Double.MAX_VALUE : Math.abs(1.0 / dirY);
            double deltaZ = (dirZ == 0) ? Double.MAX_VALUE : Math.abs(1.0 / dirZ);

            // Compute starting ray length distance to the closest imminent voxel boundaries
            double tMaxX = (dirX > 0) ? (x + 1.0 - startX) * deltaX : (startX - x) * deltaX;
            double tMaxY = (dirY > 0) ? (y + 1.0 - startY) * deltaY : (startY - y) * deltaY;
            double tMaxZ = (dirZ > 0) ? (z + 1.0 - startZ) * deltaZ : (startZ - z) * deltaZ;

            int iterations = 0;
            int maxVoxelSteps = 300; // Limit depth traversal range (equivalent to ~200 blocks)
            // tEntry = ray parameter at which we entered the current voxel.
            // For the starting voxel this is 0 (the eye is inside it).
            // After each DDA step it becomes the tMax of whichever axis just crossed.
            double tEntry = 0.0;
            net.minecraft.core.Direction hitFace = net.minecraft.core.Direction.UP;
            BlockPos.MutableBlockPos mutPos = new BlockPos.MutableBlockPos();

            for (int i = 0; i < maxVoxelSteps; i++) {
                iterations++;
                mutPos.set(x, y, z);

                // Hit if the block is currently visible (step-controlled) or is a baseplate block.
                // bakedAll is intentionally NOT checked here — hidden blocks must not be raytraceable.
                if (targetVisible.contains(mutPos) || baseplatePositions.contains(mutPos)) {
                    // tEntry is exactly the ray distance at which we crossed into this voxel,
                    // so hitVec lands precisely on the entry face regardless of ray direction.
                    net.minecraft.world.phys.Vec3 hitVec = new net.minecraft.world.phys.Vec3(
                            startX + dirX * tEntry,
                            startY + dirY * tEntry,
                            startZ + dirZ * tEntry
                    );

                    totalClipContextTimeNs += (System.nanoTime() - clipStart);
                    maxRayIterationsTracked = Math.max(maxRayIterationsTracked, iterations);

                    return new BlockHitResult(hitVec, hitFace, mutPos.immutable(), false);
                }

                // Hop to the closest voxel interface edge via regular DDA grid stepping.
                // Record tEntry = the crossing distance before we advance.
                if (tMaxX < tMaxY) {
                    if (tMaxX < tMaxZ) {
                        tEntry = tMaxX;
                        x += stepX;
                        tMaxX += deltaX;
                        hitFace = (stepX > 0) ? net.minecraft.core.Direction.WEST : net.minecraft.core.Direction.EAST;
                    } else {
                        tEntry = tMaxZ;
                        z += stepZ;
                        tMaxZ += deltaZ;
                        hitFace = (stepZ > 0) ? net.minecraft.core.Direction.NORTH : net.minecraft.core.Direction.SOUTH;
                    }
                } else {
                    if (tMaxY < tMaxZ) {
                        tEntry = tMaxY;
                        y += stepY;
                        tMaxY += deltaY;
                        hitFace = (stepY > 0) ? net.minecraft.core.Direction.DOWN : net.minecraft.core.Direction.UP;
                    } else {
                        tEntry = tMaxZ;
                        z += stepZ;
                        tMaxZ += deltaZ;
                        hitFace = (stepZ > 0) ? net.minecraft.core.Direction.NORTH : net.minecraft.core.Direction.SOUTH;
                    }
                }
            }

            totalClipContextTimeNs += (System.nanoTime() - clipStart);
            maxRayIterationsTracked = Math.max(maxRayIterationsTracked, iterations);
        } catch (Exception ignored) {}

        return null;
    }

    private static void bindShaderSamplers(ShaderInstance s) {
        for (int j = 0; j < 12; j++) s.setSampler("Sampler" + j, RenderSystem.getShaderTexture(j));
    }

    private static void setShaderUniforms(ShaderInstance s) {
        if (s.MODEL_VIEW_MATRIX != null) s.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
        if (s.PROJECTION_MATRIX != null) s.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        if (s.COLOR_MODULATOR != null) s.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        if (s.FOG_START != null) s.FOG_START.set(RenderSystem.getShaderFogStart());
        if (s.FOG_END != null) s.FOG_END.set(RenderSystem.getShaderFogEnd());
        if (s.FOG_COLOR != null) s.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        if (s.FOG_SHAPE != null) s.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        if (s.TEXTURE_MATRIX != null) s.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        if (s.GAME_TIME != null) s.GAME_TIME.set(RenderSystem.getShaderGameTime());
    }

    private static void applyLayerBlend(RenderType layer) {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        if (layer == RenderType.translucent()) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(770, 771);
            RenderSystem.depthMask(false);
        } else {
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    public void close() {
        if (bakeFuture != null) {
            bakeFuture.cancel(true);
            bakeFuture = null;
        }
        for (int i = 0; i < LAYER_COUNT; i++) {
            if (front[i] != null && !front[i].isInvalid()) front[i].close();
            if (back[i] != null && !back[i].isInvalid()) back[i].close();
            if (overlay[i] != null && !overlay[i].isInvalid()) overlay[i].close();
        }
    }
}