package net.phoenixvine.phantasia.client.render;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRender;

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
import net.minecraft.world.phys.Vec3;
import net.phoenixvine.phantasia.client.camera.CameraView;
import net.phoenixvine.phantasia.common.data.pattern.PhantasiaLoadedPattern;
import net.phoenixvine.phantasia.common.data.variant.PhantasiaVariantState;

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
 */
public final class PhantasiaWorldRenderer {

    // ── GL scratch buffers (static — one per class, not per instance) ─────────

    private static final FloatBuffer SCRATCH_MV = direct(64).asFloatBuffer();
    private static final FloatBuffer SCRATCH_PROJ = direct(64).asFloatBuffer();
    private static final IntBuffer SCRATCH_VP = direct(16 * 4).asIntBuffer();
    private static final FloatBuffer UNPROJECT_OUT = direct(12).asFloatBuffer();

    /** Cached result from the last depth sample. */
    @Nullable
    private BlockHitResult cachedPickResult = null;

    private double guiMouseX;
    private double guiMouseY;

    /** Instance field — not static — so multiple renderer instances don't share a timer. */
    private long lastTraceTime = 0;

    @Nullable
    public BlockHitResult getLastHitResult() {
        return this.lastHitResult;
    }

    // ── Profiler ──────────────────────────────────────────────────────────────
    // Window: emit a report every 5 s of wall-clock render time.
    private static final long PROFILE_WINDOW_NS = 5_000_000_000L;
    private static final int SPIKE_HISTORY_SIZE = 64; // ring-buffer of recent frame times

    private long profileWindowStart = -1;
    private int profiledFramesCount = 0;

    // ── Per-window accumulators ───────────────────────────────────────────────
    private long totalRenderTimeNs = 0;
    private long maxRenderTimeNs = 0;
    private long totalSetupTimeNs = 0;
    private long totalVboTimeNs = 0;
    private long totalDynamicTimeNs = 0;
    private long totalParticleTickTimeNs = 0;
    private long totalRayTraceTimeNs = 0;
    private long totalPrePhaseTimeNs = 0; // tickAlpha, swaps, bake scheduling, sprite marker

    // Sub-phase: inside Phase 3 (Dynamic & BERs)
    private long totalBerTimeNs = 0; // cumulative BER render time
    private long totalEntityTimeNs = 0; // cumulative entity render time
    private long totalDynRendererTimeNs = 0; // cumulative dynamic-renderer time
    private long totalBufFlushTimeNs = 0; // buffers.endBatch() cost

    // Sub-phase: inside Phase 4 (Particles & Ticks)
    private long totalParticleRenderNs = 0; // particle draw only
    private long totalWorldTickNs = 0; // world.tickWorld() + animateTick

    // Sub-phase: ray trace internals
    private long totalClipContextTimeNs = 0; // cumulative world.clip() cost
    private int totalRayAttempts = 0; // sum of retry counts over window
    private int maxRayIterations = 0; // worst-case retry depth this window
    private int rayTraceCallsThisWindow = 0; // how many frames actually ran a trace

    // BER detail
    private long totalBerRenderTimeNs = 0; // alias kept for call sites
    private int maxBerCountTracked = 0;
    private int maxRayIterationsTracked = 0; // alias kept for dumpProfilingData

    // Bake start timestamps (written on bake thread, read on render thread at swap)
    private volatile long fullBakeStartNs = 0;
    private volatile long partialBakeStartNs = 0;
    private volatile int lastBakeBlockCount = 0;

    // Bake timing (accumulated across async completions — written from render thread on swap)
    private long lastFullBakeDurationNs = 0;
    private long lastPartialBakeDurationNs = 0;
    private int bakeCompletionsThisWindow = 0;
    private long totalBakeTimeNs = 0;

    // Spike ring-buffer: store last SPIKE_HISTORY_SIZE frame durations (ms * 1000 = µs)
    private final long[] spikeHistory = new long[SPIKE_HISTORY_SIZE];
    private int spikeHead = 0; // next write index

    // Frame budget warnings — log once per window if thresholds exceeded
    private int framesOver8ms = 0;
    private int framesOver16ms = 0;
    private int framesOver33ms = 0;

    // Per-frame temporaries (set at render start, read by sub-methods)
    private int frameParticleCount = 0;
    private int frameBerCount = 0;
    private int frameRayDepth = 0;
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
    private static final int TRANSITION_THRESHOLD = 0;

    // ── Streaming bake ────────────────────────────────────────────────────────
    // For patterns exceeding STREAMING_THRESHOLD blocks, the bake is split into
    // STREAMING_CHUNK_SIZE-block chunks. Each completed chunk is uploaded into its
    // own VertexBuffer[] set and drawn immediately, so the scene populates in real
    // time rather than blocking until the entire multi is baked.

    /** Block count above which streaming bake is used instead of a monolithic bake. */
    public static final int STREAMING_THRESHOLD = 4_000;

    /** Blocks processed per streaming chunk. */
    private static final int STREAMING_CHUNK_SIZE = 4_000;

    /** True while a streaming bake is actively in progress. */
    public volatile boolean isStreaming = false;

    /** Number of blocks that have been baked so far in the current streaming bake. */
    public volatile int streamingProgress = 0;

    /** Total blocks to bake in the current streaming bake. */
    public volatile int streamingTotal = 0;

    /**
     * Completed chunk VBO sets. Each entry is one STREAMING_CHUNK_SIZE-block bake.
     * The render thread draws front[] first, then all entries in this list.
     * Only appended to from recordRenderCall; reads happen on the render thread.
     */
    private final List<VertexBuffer[]> streamChunks = new ArrayList<>();

    /**
     * Number of chunk entries in streamChunks that are fully uploaded and ready.
     * Incremented on the render thread inside recordRenderCall after each chunk upload.
     */
    public volatile int streamChunksReady = 0;

    /** Pending chunk VBO arrays waiting to be added to streamChunks. */
    private final java.util.concurrent.ConcurrentLinkedQueue<VertexBuffer[]> pendingChunkUploads = new java.util.concurrent.ConcurrentLinkedQueue<>();

    // ── Layers ────────────────────────────────────────────────────────────────

    private final List<RenderType> LAYERS = RenderType.chunkBufferLayers();
    private final int LAYER_COUNT = LAYERS.size();

    // ── Double-buffered main geometry VBOs ────────────────────────────────────

    private final VertexBuffer[] front;
    private final VertexBuffer[] back;
    private volatile boolean backReady = false;
    /**
     * True once front[] has been populated at least once; prevents blank-flash on step transitions during streaming.
     */
    private boolean frontHasContent = false;

    // 1. Add this field at the top of PhantasiaWorldRenderer if it's missing
    private final Set<BlockPos> baseplatePositions = new java.util.HashSet<>();

    // 2. Add a setter method so your pattern loader can hand them over
    public void setBaseplatePositions(Set<BlockPos> positions) {
        this.baseplatePositions.clear();
        if (positions != null) {
            this.baseplatePositions.addAll(positions);
        }
    }

    // Stores the complete layout map for accurate 100% raytrace intersection
    private Set<BlockPos> patternBlocks = Collections.emptySet();

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
    private Set<BlockPos> animateTickEligible = Collections.emptySet();
    /** Pre-cached (pos, state) pairs for all non-air visible blocks — avoids HashMap lookup in the hot tick loop. */
    private record AnimTickEntry(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {}
    private List<AnimTickEntry> animateTickList = Collections.emptyList();
    private int animateTickIdx = 0;
    /** Blocks ticked per game tick. Large enough for visible particle density, small enough to be ~free. */
    private static final int ANIMATE_TICK_BUDGET = 2048;

    // ── Fade-in state (small transitions only) ────────────────────────────────

    private final Map<BlockPos, Float> blockAlpha = new HashMap<>();
    private final Map<BlockPos, List<RenderType>> blockLayers = new HashMap<>();
    private boolean hasTransitions = false;

    /**
     * Positions in the current front[] VBO that are no longer in targetVisible.
     * Rendered at alpha=0 each frame to immediately hide them without waiting for
     * the rebake to complete. Cleared on swapFullBuffers().
     */
    private final Set<BlockPos> suppressedPositions = new HashSet<>();
    private final Map<BlockPos, List<RenderType>> suppressedLayers = new HashMap<>();

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
    private PhantasiaLoadedPattern patternContext = null;

    public void setPatternContext(PhantasiaLoadedPattern pattern) {
        this.patternContext = pattern;
    }

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

    public void setControllerWorldPos(@Nullable BlockPos pos) {
        this.controllerWorldPos = pos;
    }

    public void setSlotOrigin(BlockPos slotOrigin) {
        this.slotOrigin = slotOrigin;
    }

    public void setPatternBlocks(Set<BlockPos> all) {
        this.bakedAll = Set.copyOf(all);
    }

    public void setVisible(Set<BlockPos> newVisible) {
        this.cachedPickResult = null;
        this.lastHitResult = null;
        this.lastTraceTime = 0;

        Set<BlockPos> old = targetVisible;
        Set<BlockPos> next = Set.copyOf(newVisible);
        // Skip the expensive bake if the visible set is unchanged.
        if (next.equals(old)) return;
        targetVisible = next;

        // Build animate-tick list from ALL non-air visible blocks — no isRandomlyTicking filter
        // because many blocks (GTCEu machines etc.) have interesting animateTick without that flag.
        // States are pre-cached here so the hot tick loop is pure virtual dispatch, no HashMap lookup.
        List<AnimTickEntry> tickEntries = new ArrayList<>(targetVisible.size());
        Set<BlockPos> eligible = new HashSet<>();
        for (BlockPos pos : targetVisible) {
            BlockState state = world.getBlockState(pos);
            if (state == null || state.isAir()) continue;
            tickEntries.add(new AnimTickEntry(pos, state));
            if (state.isRandomlyTicking() || state.getBlock().isRandomlyTicking(state))
                eligible.add(pos);
        }
        animateTickEligible = eligible.isEmpty() ? Collections.emptySet()
                : Collections.unmodifiableSet(eligible);
        animateTickList = tickEntries.isEmpty() ? Collections.emptyList()
                : Collections.unmodifiableList(tickEntries);
        animateTickIdx = 0;

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

        partialBakePending = false;
        partialBakePositions = Collections.emptySet();
        overlayReady = false;
        fullBakeNeeded = true;
    }

    public void requestBake() {
        fullBakeNeeded = true;
    }

    public void requestPartialBake(Set<BlockPos> changedPositions) {
        if (changedPositions == null || changedPositions.isEmpty()) return;
        if (fullBakeNeeded) return;
        Set<BlockPos> existing = partialBakePositions;
        Set<BlockPos> merged = new HashSet<>(existing);
        merged.addAll(changedPositions);
        partialBakePositions = Set.copyOf(merged);
        partialBakePending = true;
    }

    public void invalidate() {
        if (bakeFuture != null && !bakeFuture.isDone()) bakeFuture.cancel(true);
        blockAlpha.clear();
        blockLayers.clear();
        animateTickEligible = Collections.emptySet();
        animateTickList = Collections.<AnimTickEntry>emptyList();
        animateTickIdx = 0;
        hasTransitions = false;
        overlayReady = false;
        partialBakePending = false;
        partialBakePositions = Collections.emptySet();
        fullBakeNeeded = true;
        isStreaming = false;
        streamingProgress = 0;
        streamingTotal = 0;
        // Chunk VBOs must be closed on the render thread.
        RenderSystem.recordRenderCall(this::closeAndClearStreamChunks);
    }

    public void setMousePos(int mx, int my) {
        this.guiMouseX = mx;
        this.guiMouseY = my;
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

        long tPre = System.nanoTime();
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
        totalPrePhaseTimeNs += (System.nanoTime() - tPre);

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

        RenderSystem.setShaderGameTime(currentTick >= 0 ? currentTick : 0,
                ((currentTick >= 0 ? currentTick : 0) + mc.getFrameTime()) / 20f);
        snapshotMatrices();
        totalSetupTimeNs += (System.nanoTime() - t0);

        // ── FIX: SWAP MAIN CAMERA TO PREVENT PLAYER ZOOM LEAKAGE ──
        // Many dynamic and billboarding renderers look at mc.gameRenderer.getMainCamera().
        // We locate it purely by type (Camera.class) to stay 100% independent of obfuscation/mappings.
        Camera originalCamera = null;
        java.lang.reflect.Field cameraField = null;
        try {
            for (java.lang.reflect.Field f : mc.gameRenderer.getClass().getDeclaredFields()) {
                if (f.getType() == Camera.class) {
                    f.setAccessible(true);
                    cameraField = f;
                    originalCamera = (Camera) f.get(mc.gameRenderer);
                    f.set(mc.gameRenderer, this.camera);
                    break;
                }
            }
        } catch (Throwable ignored) {}

        try {
            // ── PHASE 2: STATIC VBO GEOMETRY DRAW ──
            long t1 = System.nanoTime();
            drawVBOs();

            // Draw fading blocks while transitioning AND while blocks are still at alpha=1
            // waiting for the bake to complete. swapFullBuffers() clears the 1.0 entries,
            // so this naturally stops once the new geometry is in front[].
            if (hasTransitions || !blockAlpha.isEmpty()) {
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

            // FIX: Use a fresh local PoseStack for BER/entity/dynamic draws.
            // The global MV stack already contains the full camera-view transform; passing it
            // to GT's machine BER causes the slot-relative translation to double-stack with
            // the camera matrix, making the controller "run away" on zoom.
            // A fresh PoseStack starts at identity -- the slot-relative translate inside each
            // draw method then puts it exactly where the block lives in slot-relative space,
            // which is what GT's BER expects.
            PoseStack localPoseStack = new PoseStack();

            frameBerCount = 0; // reset per-frame profiler counters
            frameRayDepth = 0;
            frameParticleCount = 0;

            long _p3ber = System.nanoTime();
            drawTileEntities(localPoseStack, buffers, partial, camX, camY, camZ);
            totalBerTimeNs += (System.nanoTime() - _p3ber);

            long _p3ent = System.nanoTime();
            drawEntities(localPoseStack, buffers, partial, camX, camY, camZ);
            totalEntityTimeNs += (System.nanoTime() - _p3ent);

            long _p3dyn = System.nanoTime();
            drawDynamicRenderers(localPoseStack, buffers, partial, camX, camY, camZ);
            totalDynRendererTimeNs += (System.nanoTime() - _p3dyn);

            long _p3flush = System.nanoTime();
            buffers.endBatch();
            totalBufFlushTimeNs += (System.nanoTime() - _p3flush);

            maxBerCountTracked = Math.max(maxBerCountTracked, frameBerCount);
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
                    long _partRender = System.nanoTime();
                    PhantasiaParticleEngine.renderDirect(buffers, mc.gameRenderer.lightTexture(), this.camera, partial);
                    buffers.endBatch();
                    totalParticleRenderNs += (System.nanoTime() - _partRender);
                    mv.popPose();
                    RenderSystem.applyModelViewMatrix();
                } catch (Exception e) {
                    LOGGER.error("[Phantasia] particle render failed", e);
                }
            }

            if (tickedThisFrame) {
                long _tickStart = System.nanoTime();
                PhantasiaParticleEngine.tick();
                world.tickWorld();
                // Checking package-local implementation of TrackedDummyWorld if applicable
                if (!animateTickList.isEmpty()) {
                    RandomSource ar = RandomSource.createNewThreadLocalInstance();
                    int size = animateTickList.size();
                    int limit = Math.min(ANIMATE_TICK_BUDGET, size);
                    for (int _i = 0; _i < limit; _i++) {
                        if (animateTickIdx >= size) animateTickIdx = 0;
                        AnimTickEntry entry = animateTickList.get(animateTickIdx++);
                        try {
                            entry.state().getBlock().animateTick(entry.state(), world, entry.pos(), ar);
                        } catch (Exception ignored) {}
                    }
                }
                totalWorldTickNs += (System.nanoTime() - _tickStart);
            }
            totalParticleTickTimeNs += (System.nanoTime() - t3);

            turnOffLight();

            // ── PHASE 5: RAY TRACE PICK PASS ──
            long t4 = System.nanoTime();
            long currentSystemTime = System.currentTimeMillis();
            final long SAMPLE_INTERVAL_MS = 16;

            if (currentSystemTime - lastTraceTime > SAMPLE_INTERVAL_MS || cachedPickResult == null) {
                frameRayDepth = 0; // reset; doDepthSampleRead increments per retry
                cachedPickResult = doDepthSampleRead(view, glX, glY, glW, glH, scale, windowH);
                lastHitResult = cachedPickResult;
                lastTraceTime = currentSystemTime;
                rayTraceCallsThisWindow++;
                totalRayAttempts += frameRayDepth;
                maxRayIterations = Math.max(maxRayIterations, frameRayDepth);
                maxRayIterationsTracked = maxRayIterations;
            } else {
                cachedPickResult = lastHitResult;
            }
            totalRayTraceTimeNs += (System.nanoTime() - t4);

            resetCamera();

        } finally {
            // ── RESTORE ORIGINAL CAMERA CONTEXT ──
            // Wrapped in a finally block to ensure the player's view never breaks if rendering crashes
            if (cameraField != null && originalCamera != null) {
                try {
                    cameraField.set(mc.gameRenderer, originalCamera);
                } catch (Throwable ignored) {}
            }
        }

        // ── RECORD SUMMARY ──
        long frameDurationNs = System.nanoTime() - renderStart;
        totalRenderTimeNs += frameDurationNs;
        maxRenderTimeNs = Math.max(maxRenderTimeNs, frameDurationNs);
        profiledFramesCount++;

        // Spike ring-buffer
        spikeHistory[spikeHead] = frameDurationNs;
        spikeHead = (spikeHead + 1) % SPIKE_HISTORY_SIZE;

        // Budget violation counters
        long frameMs8 = 8_000_000L;
        long frameMs16 = 16_000_000L;
        long frameMs33 = 33_000_000L;
        if (frameDurationNs > frameMs8) framesOver8ms++;
        if (frameDurationNs > frameMs16) framesOver16ms++;
        if (frameDurationNs > frameMs33) framesOver33ms++;

        if (System.nanoTime() - profileWindowStart >= PROFILE_WINDOW_NS) {
            dumpProfilingData();
        }
    }

    /**
     * Casts a ray from the camera through the mouse cursor using world.clip(),
     * iteratively skipping hidden blocks until a visible one is hit.
     * Uses Minecraft's actual voxel traversal so non-full-cube shapes (slabs,
     * stairs, etc.) are handled correctly — unlike unit-cube AABB testing.
     */
    @Nullable
    private BlockHitResult doDepthSampleRead(CameraView view, int glX, int glY, int glW, int glH, double guiScale,
                                             int windowH) {
        // 1. Bounds-check: cursor must be inside the scene viewport.
        int mouseGlX = (int) (guiMouseX * guiScale);
        int mouseGlY = (int) (windowH - guiMouseY * guiScale);
        if (mouseGlX < glX || mouseGlX >= glX + glW || mouseGlY < glY || mouseGlY >= glY + glH) {
            return null;
        }

        // 2. Unproject near (depth=0) and far (depth=1) plane points to build the ray.
        for (int i = 0; i < 16; i++) SCRATCH_MV.put(i, snapMV[i]);
        for (int i = 0; i < 16; i++) SCRATCH_PROJ.put(i, snapProj[i]);
        for (int i = 0; i < 4; i++) SCRATCH_VP.put(i, snapVP[i]);
        SCRATCH_MV.rewind();
        SCRATCH_PROJ.rewind();
        SCRATCH_VP.rewind();
        UNPROJECT_OUT.rewind();

        Project.gluUnProject(mouseGlX, mouseGlY, 0f, SCRATCH_MV, SCRATCH_PROJ, SCRATCH_VP, UNPROJECT_OUT);
        double nearX = UNPROJECT_OUT.get(0), nearY = UNPROJECT_OUT.get(1), nearZ = UNPROJECT_OUT.get(2);

        for (int i = 0; i < 16; i++) SCRATCH_MV.put(i, snapMV[i]);
        for (int i = 0; i < 16; i++) SCRATCH_PROJ.put(i, snapProj[i]);
        for (int i = 0; i < 4; i++) SCRATCH_VP.put(i, snapVP[i]);
        SCRATCH_MV.rewind();
        SCRATCH_PROJ.rewind();
        SCRATCH_VP.rewind();
        UNPROJECT_OUT.rewind();

        Project.gluUnProject(mouseGlX, mouseGlY, 1f, SCRATCH_MV, SCRATCH_PROJ, SCRATCH_VP, UNPROJECT_OUT);
        double farX = UNPROJECT_OUT.get(0), farY = UNPROJECT_OUT.get(1), farZ = UNPROJECT_OUT.get(2);

        // Unproject yields slot-relative coords; shift to world space.
        double ox = slotOrigin != null ? slotOrigin.getX() : 0.0;
        double oy = slotOrigin != null ? slotOrigin.getY() : 0.0;
        double oz = slotOrigin != null ? slotOrigin.getZ() : 0.0;
        Vec3 rayStart = new Vec3(nearX + ox, nearY + oy, nearZ + oz);
        Vec3 farPt = new Vec3(farX + ox, farY + oy, farZ + oz);

        Vec3 lookDir = farPt.subtract(rayStart).normalize();
        if (lookDir.lengthSqr() < 1e-10) return null;

        // Ray end 200 blocks out - far enough for any GT multiblock.
        Vec3 rayEnd = rayStart.add(lookDir.scale(200.0));

        // 3. Iterative world.clip(): use Minecraft's voxel traversal which respects
        // actual block shape (slabs, stairs, hatches, etc.). Hidden blocks are
        // transparent to the ray - nudge past them and retry up to 32 times.
        for (int attempt = 0; attempt < 32; attempt++) {
            net.minecraft.world.level.ClipContext ctx = new net.minecraft.world.level.ClipContext(
                    rayStart, rayEnd,
                    net.minecraft.world.level.ClipContext.Block.OUTLINE,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    cameraEntity);

            BlockHitResult result = world.clip(ctx);
            if (result == null || result.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                return null;
            }

            BlockPos pos = result.getBlockPos();
            if (targetVisible.contains(pos) || baseplatePositions.contains(pos)) {
                return result;
            }

            // Hidden block - nudge ray start just past the hit face and retry.
            rayStart = result.getLocation().add(lookDir.scale(0.02));
            frameRayDepth++;
        }
        return null;
    }

    private void dumpProfilingData() {
        if (profiledFramesCount == 0) return;

        final double MS = 1_000_000.0;
        final double n = profiledFramesCount;

        // ── Frame totals ──────────────────────────────────────────────────────
        double avgTotal = (totalRenderTimeNs / n) / MS;
        double maxTotal = maxRenderTimeNs / MS;

        // Percentile spike from ring-buffer: sort the last SPIKE_HISTORY_SIZE entries
        int filled = Math.min(profiledFramesCount, SPIKE_HISTORY_SIZE);
        long[] sorted = new long[filled];
        for (int i = 0; i < filled; i++)
            sorted[i] = spikeHistory[(spikeHead - 1 - i + SPIKE_HISTORY_SIZE) % SPIKE_HISTORY_SIZE];
        java.util.Arrays.sort(sorted);
        double p95 = filled > 0 ? sorted[(int) (filled * 0.95)] / MS : 0;
        double p99 = filled > 0 ? sorted[Math.min(filled - 1, (int) (filled * 0.99))] / MS : 0;

        // ── Phase breakdown ───────────────────────────────────────────────────
        double avgPre = (totalPrePhaseTimeNs / n) / MS;
        double avgSetup = (totalSetupTimeNs / n) / MS;
        double avgVbo = (totalVboTimeNs / n) / MS;
        double avgDynamic = (totalDynamicTimeNs / n) / MS;
        double avgParticle = (totalParticleTickTimeNs / n) / MS;
        double avgRay = (totalRayTraceTimeNs / n) / MS;
        double unaccounted = avgTotal - avgPre - avgSetup - avgVbo - avgDynamic - avgParticle - avgRay;

        // ── Phase 3 sub-breakdown ─────────────────────────────────────────────
        double avgBer = (totalBerTimeNs / n) / MS;
        double avgEntity = (totalEntityTimeNs / n) / MS;
        double avgDynRend = (totalDynRendererTimeNs / n) / MS;
        double avgBufFlush = (totalBufFlushTimeNs / n) / MS;

        // ── Phase 4 sub-breakdown ─────────────────────────────────────────────
        double avgPartRender = (totalParticleRenderNs / n) / MS;
        double avgWorldTick = (totalWorldTickNs / n) / MS;

        // ── Ray trace internals ───────────────────────────────────────────────
        double avgClip = rayTraceCallsThisWindow > 0 ?
                (totalClipContextTimeNs / (double) rayTraceCallsThisWindow) / MS : 0;
        double avgRayDepth = rayTraceCallsThisWindow > 0 ? totalRayAttempts / (double) rayTraceCallsThisWindow : 0;

        // ── Bake stats ────────────────────────────────────────────────────────
        double avgBake = bakeCompletionsThisWindow > 0 ? (totalBakeTimeNs / (double) bakeCompletionsThisWindow) / MS :
                0;

        // ── Budget summary ────────────────────────────────────────────────────
        double pctOver8 = (framesOver8ms / n) * 100.0;
        double pctOver16 = (framesOver16ms / n) * 100.0;
        double pctOver33 = (framesOver33ms / n) * 100.0;

        LOGGER.info(String.format(
                "\n" +
                        "======= [PHANTASIA DEEP PROFILER] =======\n" +
                        " Frames: %d   Window: %.1f s\n" +
                        " Avg Frame:  %.3f ms   Max: %.3f ms   P95: %.3f ms   P99: %.3f ms\n" +
                        " Budget violations: >8ms=%.1f%%   >16ms=%.1f%%   >33ms=%.1f%%\n" +
                        "\n" +
                        " ── Phase Breakdown (avg/frame) ──────────────────────────────────\n" +
                        "   Phase 0 Pre (swap/sched) : %7.3f ms\n" +
                        "   Phase 1 Setup & Matrices : %7.3f ms\n" +
                        "   Phase 2 VBO Draw         : %7.3f ms\n" +
                        "   Phase 3 BERs & Dynamic   : %7.3f ms\n" +
                        "   Phase 4 Particles & Tick : %7.3f ms\n" +
                        "   Phase 5 Ray Trace        : %7.3f ms\n" +
                        "   Unaccounted overhead     : %7.3f ms\n" +
                        "\n" +
                        " ── Phase 3 Sub-Breakdown ────────────────────────────────────────\n" +
                        "   Block Entity Renderers   : %7.3f ms  (max BERs/frame: %d)\n" +
                        "   Entity Renderers         : %7.3f ms\n" +
                        "   Dynamic Renderers        : %7.3f ms\n" +
                        "   BufferSource.endBatch()  : %7.3f ms\n" +
                        "\n" +
                        " ── Phase 4 Sub-Breakdown ────────────────────────────────────────\n" +
                        "   Particle Render          : %7.3f ms\n" +
                        "   World Tick + AnimTick    : %7.3f ms\n" +
                        "\n" +
                        " ── Ray Trace Internals ──────────────────────────────────────────\n" +
                        "   Avg clip cost (per call) : %7.3f ms  Max depth: %d  Avg depth: %.2f\n" +
                        "\n" +
                        " ── Bake Thread ──────────────────────────────────────────────────\n" +
                        "   Completions this window  : %d\n" +
                        "   Avg bake duration        : %7.3f ms\n" +
                        "   Last full bake           : %7.3f ms  (%d blocks)\n" +
                        "   Last partial bake        : %7.3f ms\n" +
                        "=========================================",
                profiledFramesCount,
                PROFILE_WINDOW_NS / 1_000_000_000.0,
                avgTotal, maxTotal, p95, p99,
                pctOver8, pctOver16, pctOver33,
                avgPre, avgSetup, avgVbo, avgDynamic, avgParticle, avgRay, unaccounted,
                avgBer, maxBerCountTracked,
                avgEntity, avgDynRend, avgBufFlush,
                avgPartRender, avgWorldTick,
                avgClip, maxRayIterations, avgRayDepth,
                bakeCompletionsThisWindow, avgBake,
                lastFullBakeDurationNs / MS, lastBakeBlockCount,
                lastPartialBakeDurationNs / MS));

        // ── Reset window ──────────────────────────────────────────────────────
        profileWindowStart = System.nanoTime();
        profiledFramesCount = 0;
        totalRenderTimeNs = 0;
        maxRenderTimeNs = 0;
        totalSetupTimeNs = 0;
        totalVboTimeNs = 0;
        totalDynamicTimeNs = 0;
        totalParticleTickTimeNs = 0;
        totalRayTraceTimeNs = 0;
        totalPrePhaseTimeNs = 0;
        totalBerTimeNs = 0;
        totalEntityTimeNs = 0;
        totalDynRendererTimeNs = 0;
        totalBufFlushTimeNs = 0;
        totalParticleRenderNs = 0;
        totalWorldTickNs = 0;
        totalClipContextTimeNs = 0;
        totalRayAttempts = 0;
        maxRayIterations = 0;
        rayTraceCallsThisWindow = 0;
        totalBerRenderTimeNs = 0;
        maxBerCountTracked = 0;
        maxRayIterationsTracked = 0;
        bakeCompletionsThisWindow = 0;
        totalBakeTimeNs = 0;
        framesOver8ms = 0;
        framesOver16ms = 0;
        framesOver33ms = 0;
    }
    // ── Alpha tick ────────────────────────────────────────────────────────────

    private void tickAlpha() {
        if (blockAlpha.isEmpty()) return;
        boolean anyStillFading = false;
        for (Map.Entry<BlockPos, Float> e : blockAlpha.entrySet()) {
            float next = Math.min(1f, e.getValue() + ALPHA_STEP);
            e.setValue(next);
            if (next < 1f) anyStillFading = true;
        }
        // Keep hasTransitions true until ALL blocks reach 1.0 — completed blocks stay
        // in blockAlpha at 1.0 so drawFadingIn() keeps them visible until the bake fires
        // and swapFullBuffers() moves them into front[].
        hasTransitions = anyStillFading;
    }

    // ── Buffer swaps ──────────────────────────────────────────────────────────

    private void swapFullBuffers() {
        if (fullBakeStartNs > 0) {
            lastFullBakeDurationNs = System.nanoTime() - fullBakeStartNs;
            totalBakeTimeNs += lastFullBakeDurationNs;
            bakeCompletionsThisWindow++;
            fullBakeStartNs = 0;
        }
        for (int i = 0; i < LAYER_COUNT; i++) {
            VertexBuffer tmp = front[i];
            front[i] = back[i];
            back[i] = tmp;
        }
        frontTileEntities = backTileEntities != null ? backTileEntities : Collections.emptySet();
        animatedPositions = backAnimatedPositions != null ? backAnimatedPositions : Collections.emptySet();
        PhantasiaSpriteMarker.invalidateCache(); // new bake may have new animated sprites
        frontHasContent = true;
        backReady = false;
        backTileEntities = null;
        backAnimatedPositions = null;
        overlayReady = false;
        // Remove fade-in entries that reached alpha 1.0 — they're now in front[] at full
        // opacity, so drawFadingIn() no longer needs to render them.
        blockAlpha.entrySet().removeIf(e -> e.getValue() >= 1f);
        blockLayers.keySet().removeIf(pos -> !blockAlpha.containsKey(pos));
        hasTransitions = !blockAlpha.isEmpty();

        // When a full bake completes after streaming, the consolidated geometry is
        // now in front[]. Release all streaming chunk VBOs — they're redundant.
        closeAndClearStreamChunks();
        isStreaming = false;
    }

    /** Closes all stream chunk VBOs and resets streaming state. Called on render thread. */
    private void closeAndClearStreamChunks() {
        for (VertexBuffer[] chunk : streamChunks) {
            for (VertexBuffer vb : chunk) {
                if (vb != null && !vb.isInvalid()) vb.close();
            }
        }
        streamChunks.clear();
        streamChunksReady = 0;
        pendingChunkUploads.clear();
    }

    private void swapOverlayBuffers() {
        overlayReady = false;
    }

    private static Set<BlockPos> union(Set<BlockPos> a, Set<BlockPos> b) {
        if (b.isEmpty()) return a;
        Set<BlockPos> r = new HashSet<>(a);
        r.addAll(b);
        return r;
    }

    // ── Full bake ─────────────────────────────────────────────────────────────

    private void scheduleFullBake() {
        Set<BlockPos> patternVisible = new HashSet<>(targetVisible);
        patternVisible.retainAll(bakedAll);

        Set<BlockPos> snapshot = new HashSet<>(patternVisible);
        snapshot.addAll(baseplatePositions);
        // Strip positions whose block state is air or has no visible geometry (e.g. GTM "any"
        // predicate placeholders). Doing this before the bake avoids iterating dead positions
        // in bucket(), which matters a lot for machines with thousands of predicate slots.
        snapshot.removeIf(pos -> {
            BlockState s = world.getBlockState(pos);
            return s == null || s.isAir() || s.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE;
        });

        if (snapshot.isEmpty()) {
            uploadEmptyBuffers(false);
            return;
        }

        // Large patterns stream block-by-block in 4k chunks so the scene is
        // immediately visible and populates in real time.
        if (snapshot.size() > STREAMING_THRESHOLD) {
            scheduleStreamingBake(snapshot);
            return;
        }

        bakeFuture = BAKE_POOL.submit(() -> {
            fullBakeStartNs = System.nanoTime();
            lastBakeBlockCount = snapshot.size();
            Minecraft mc = Minecraft.getInstance();
            BlockRenderDispatcher brd = mc.getBlockRenderer();
            RandomSource random = RandomSource.createNewThreadLocalInstance();
            ModelBlockRenderer.enableCaching();

            PhantasiaVariantState vs = PhantasiaVariantState.get();
            Map<BlockPos, BlockInfo> variantSaved = applyVariants(vs, snapshot);

            Map<BlockPos, BlockInfo> hiddenSaved = maskHiddenBlocks(snapshot);

            Map<RenderType, List<BlockPos>> solidBuckets = new HashMap<>(LAYER_COUNT);
            Map<RenderType, List<BlockPos>> fluidBuckets = new HashMap<>(LAYER_COUNT);
            bucket(brd, random, snapshot, solidBuckets, fluidBuckets);

            // Bake ALL layers to CPU buffers first (no render-thread sync needed),
            // then enqueue all GPU uploads in one batch.
            Set<BlockPos> animatedBack = new HashSet<>();
            BakedLayer[] baked = new BakedLayer[LAYER_COUNT];
            try {
                for (int i = 0; i < LAYER_COUNT; i++) {
                    if (Thread.interrupted()) return;
                    RenderType layer = LAYERS.get(i);
                    List<BlockPos> solid = solidBuckets.getOrDefault(layer, List.of());
                    List<BlockPos> fluid = fluidBuckets.getOrDefault(layer, List.of());
                    if (solid.isEmpty() && fluid.isEmpty()) {
                        baked[i] = null;
                        continue;
                    }
                    baked[i] = bakeLayerToBuffer(brd, random, layer, solid, fluid, animatedBack);
                    if (baked[i] == null) return; // interrupted inside bakeLayerToBuffer
                }
            } finally {
                ModelBlockRenderer.clearCache();
                restoreVariants(hiddenSaved);
                restoreVariants(variantSaved);
            }
            // Only upload non-empty layers

            pendingUploads.set(LAYER_COUNT);
            for (int i = 0; i < LAYER_COUNT; i++) {
                final int fi = i;
                final BakedLayer bl = baked[fi]; // null = empty layer
                RenderSystem.recordRenderCall(() -> {
                    if (bl != null) uploadToVBO(back[fi], bl);
                    else uploadEmptyVBO(back[fi], LAYERS.get(fi));
                    if (pendingUploads.decrementAndGet() == 0) backReady = true;
                });
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

    /**
     * Streaming bake for patterns larger than {@link #STREAMING_THRESHOLD} blocks.
     *
     * <p>
     * Splits {@code snapshot} into {@link #STREAMING_CHUNK_SIZE}-block batches.
     * Each completed batch is uploaded into a fresh {@code VertexBuffer[]} set and
     * added to {@link #streamChunks}, which {@link #drawVBOs()} draws every frame.
     * The scene is immediately visible and the multiblock materializes in real time.
     *
     * <p>
     * After all chunks finish, a normal consolidating full bake is dispatched so
     * that the final state lives in {@code front[]} and all chunk VBOs are released.
     */
    private void scheduleStreamingBake(Set<BlockPos> snapshot) {
        // On the initial open (front is empty), wipe to blank immediately so the
        // viewport appears quickly. On step transitions (front already has content),
        // keep the old frame alive — the new geometry streams in via streamChunks,
        // and front[] is replaced atomically when consolidation completes.
        if (!frontHasContent) uploadEmptyBuffers(false);

        // Reset streaming counters (render thread — safe to write here).
        isStreaming = true;
        streamingProgress = 0;
        streamingTotal = snapshot.size();

        // Drain any leftover chunks from a previous streaming bake.
        // closeAndClearStreamChunks() touches VBO objects, so defer to render thread.
        RenderSystem.recordRenderCall(this::closeAndClearStreamChunks);

        List<BlockPos> ordered = new ArrayList<>(snapshot);

        bakeFuture = BAKE_POOL.submit(() -> {
            fullBakeStartNs = System.nanoTime();
            lastBakeBlockCount = snapshot.size();

            Minecraft mc = Minecraft.getInstance();
            BlockRenderDispatcher brd = mc.getBlockRenderer();
            RandomSource random = RandomSource.createNewThreadLocalInstance();

            PhantasiaVariantState vs = PhantasiaVariantState.get();
            Map<BlockPos, BlockInfo> variantSaved = applyVariants(vs, snapshot);
            Map<BlockPos, BlockInfo> hiddenSaved = maskHiddenBlocks(snapshot);

            Set<BlockPos> animatedBack = new HashSet<>();
            Set<BlockPos> tesBack = new HashSet<>();

            try {
                int total = ordered.size();
                int chunkStart = 0;

                while (chunkStart < total) {
                    if (Thread.interrupted()) return;

                    int chunkEnd = Math.min(chunkStart + STREAMING_CHUNK_SIZE, total);
                    List<BlockPos> chunk = ordered.subList(chunkStart, chunkEnd);

                    ModelBlockRenderer.enableCaching();
                    Map<RenderType, List<BlockPos>> solidBuckets = new HashMap<>(LAYER_COUNT);
                    Map<RenderType, List<BlockPos>> fluidBuckets = new HashMap<>(LAYER_COUNT);
                    bucket(brd, random, new HashSet<>(chunk), solidBuckets, fluidBuckets);

                    BakedLayer[] baked = new BakedLayer[LAYER_COUNT];
                    try {
                        for (int i = 0; i < LAYER_COUNT; i++) {
                            if (Thread.interrupted()) return;
                            RenderType layer = LAYERS.get(i);
                            List<BlockPos> solid = solidBuckets.getOrDefault(layer, List.of());
                            List<BlockPos> fluid = fluidBuckets.getOrDefault(layer, List.of());
                            if (solid.isEmpty() && fluid.isEmpty()) {
                                baked[i] = null;
                                continue;
                            }
                            baked[i] = bakeLayerToBuffer(brd, random, layer, solid, fluid, animatedBack);
                            if (baked[i] == null) return; // interrupted inside bakeLayerToBuffer
                        }
                    } finally {
                        ModelBlockRenderer.clearCache();
                    }

                    // Collect BEs for this chunk.
                    for (BlockPos pos : chunk) {
                        BlockEntity be = world.getBlockEntity(pos);
                        if (be != null && mc.getBlockEntityRenderDispatcher().getRenderer(be) != null)
                            tesBack.add(pos);
                    }

                    final BakedLayer[] bakedFinal = baked;
                    final int chunkEndFinal = chunkEnd;

                    // Upload this chunk's geometry to a fresh VBO set on the render thread.
                    RenderSystem.recordRenderCall(() -> {
                        VertexBuffer[] chunkVBOs = new VertexBuffer[LAYER_COUNT];
                        for (int i = 0; i < LAYER_COUNT; i++) {
                            chunkVBOs[i] = new VertexBuffer(VertexBuffer.Usage.STATIC);
                            if (bakedFinal[i] != null) {
                                uploadToVBO(chunkVBOs[i], bakedFinal[i]);
                            } else {
                                uploadEmptyVBO(chunkVBOs[i], LAYERS.get(i));
                            }
                        }
                        streamChunks.add(chunkVBOs);
                        streamChunksReady = streamChunks.size();
                        streamingProgress = chunkEndFinal;
                    });

                    chunkStart = chunkEnd;
                }

                // All chunks uploaded. Kick off a consolidating full bake that will
                // fold everything into back[] → front[] so chunk VBOs can be freed.
                backAnimatedPositions = animatedBack;
                backTileEntities = tesBack;

            } finally {
                restoreVariants(hiddenSaved);
                restoreVariants(variantSaved);
            }

            // Consolidating bake: re-bake the entire snapshot into back[] normally.
            // When swapFullBuffers() fires, it closes all stream chunks automatically.
            ModelBlockRenderer.enableCaching();
            PhantasiaVariantState vs2 = PhantasiaVariantState.get();
            Map<BlockPos, BlockInfo> vs2saved = applyVariants(vs2, snapshot);
            Map<BlockPos, BlockInfo> hidSaved2 = maskHiddenBlocks(snapshot);

            Map<RenderType, List<BlockPos>> solidB = new HashMap<>(LAYER_COUNT);
            Map<RenderType, List<BlockPos>> fluidB = new HashMap<>(LAYER_COUNT);
            bucket(brd, random, snapshot, solidB, fluidB);

            Set<BlockPos> animatedBack2 = new HashSet<>();
            BakedLayer[] finalBaked = new BakedLayer[LAYER_COUNT];
            try {
                for (int i = 0; i < LAYER_COUNT; i++) {
                    if (Thread.interrupted()) return;
                    RenderType layer = LAYERS.get(i);
                    List<BlockPos> solid = solidB.getOrDefault(layer, List.of());
                    List<BlockPos> fluid = fluidB.getOrDefault(layer, List.of());
                    if (solid.isEmpty() && fluid.isEmpty()) {
                        finalBaked[i] = null;
                        continue;
                    }
                    finalBaked[i] = bakeLayerToBuffer(brd, random, layer, solid, fluid, animatedBack2);
                }
            } finally {
                ModelBlockRenderer.clearCache();
                restoreVariants(hidSaved2);
                restoreVariants(vs2saved);
            }

            pendingUploads.set(LAYER_COUNT);
            for (int i = 0; i < LAYER_COUNT; i++) {
                final int fi = i;
                final BakedLayer bl = finalBaked[fi];
                RenderSystem.recordRenderCall(() -> {
                    if (bl != null) uploadToVBO(back[fi], bl);
                    else uploadEmptyVBO(back[fi], LAYERS.get(fi));
                    if (pendingUploads.decrementAndGet() == 0) {
                        streamingProgress = streamingTotal;
                        backReady = true;
                    }
                });
            }
            backAnimatedPositions = animatedBack2;
        });
    }

    // ── Partial bake ─────────────────────────────────────────────────────────

    private void schedulePartialBake(Set<BlockPos> targets) {
        Set<BlockPos> valid = new HashSet<>(targets);
        valid.removeIf(pos -> !bakedAll.contains(pos) && !baseplatePositions.contains(pos));
        if (valid.isEmpty()) return;

        Set<BlockPos> patternVisible = new HashSet<>(targetVisible);
        patternVisible.retainAll(bakedAll);

        Set<BlockPos> fullVisibleSnapshot = new HashSet<>(patternVisible);
        fullVisibleSnapshot.addAll(baseplatePositions);

        bakeFuture = BAKE_POOL.submit(() -> {
            Minecraft mc = Minecraft.getInstance();
            BlockRenderDispatcher brd = mc.getBlockRenderer();
            RandomSource random = RandomSource.createNewThreadLocalInstance();
            ModelBlockRenderer.enableCaching();

            PhantasiaVariantState vs = PhantasiaVariantState.get();
            Map<BlockPos, BlockInfo> variantSaved = applyVariants(vs, valid);

            Map<BlockPos, BlockInfo> hiddenSaved = maskHiddenBlocks(fullVisibleSnapshot);

            Map<RenderType, List<BlockPos>> solidBuckets = new HashMap<>(LAYER_COUNT);
            Map<RenderType, List<BlockPos>> fluidBuckets = new HashMap<>(LAYER_COUNT);
            bucket(brd, random, valid, solidBuckets, fluidBuckets);

            Set<BlockPos> animatedBack = new HashSet<>();
            BakedLayer[] baked = new BakedLayer[LAYER_COUNT];
            try {
                for (int i = 0; i < LAYER_COUNT; i++) {
                    if (Thread.interrupted()) return;
                    RenderType layer = LAYERS.get(i);
                    List<BlockPos> solid = solidBuckets.getOrDefault(layer, List.of());
                    List<BlockPos> fluid = fluidBuckets.getOrDefault(layer, List.of());
                    if (solid.isEmpty() && fluid.isEmpty()) {
                        baked[i] = null;
                        continue;
                    }
                    baked[i] = bakeLayerToBuffer(brd, random, layer, solid, fluid, animatedBack);
                }
            } finally {
                ModelBlockRenderer.clearCache();
                restoreVariants(hiddenSaved);
                restoreVariants(variantSaved);
            }
            pendingUploads.set(LAYER_COUNT);
            for (int i = 0; i < LAYER_COUNT; i++) {
                final int fi = i;
                final BakedLayer bl = baked[fi];
                RenderSystem.recordRenderCall(() -> {
                    if (bl != null) uploadToVBO(overlay[fi], bl);
                    else uploadEmptyVBO(overlay[fi], LAYERS.get(fi));
                    if (pendingUploads.decrementAndGet() == 0) overlayReady = true;
                });
            }
        });
    }

    private void bucket(BlockRenderDispatcher brd, RandomSource random,
                        Set<BlockPos> positions,
                        Map<RenderType, List<BlockPos>> solidOut,
                        Map<RenderType, List<BlockPos>> fluidOut) {
        for (BlockPos pos : positions) {
            BlockState state = world.getBlockState(pos);

            if ((state == null || state.isAir()) && baseplatePositions.contains(pos)) {
                state = net.minecraft.world.level.block.Blocks.COBBLED_DEEPSLATE.defaultBlockState();
            }

            if (state == null || state.isAir()) continue;

            if (state.getRenderShape() != RenderShape.INVISIBLE) {
                for (RenderType layer : LAYERS) {
                    if (WorldSceneRenderer.canRenderInLayer(brd, state, pos, world, layer, random)) {
                        solidOut.computeIfAbsent(layer, k -> new ArrayList<>()).add(pos);
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

        int checkInterval = 0;
        for (BlockPos pos : solid) {
            if (++checkInterval >= 512) {
                checkInterval = 0;
                if (Thread.interrupted()) return null;
            }
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

    private void uploadEmptyVBO(VertexBuffer vbo, RenderType layer) {
        if (!vbo.isInvalid()) {
            BufferBuilder bb = new BufferBuilder(layer.bufferSize());
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            vbo.bind();
            vbo.upload(bb.end());
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

    private Map<BlockPos, BlockInfo> applyVariants(PhantasiaVariantState vs, Set<BlockPos> positions) {
        Map<BlockPos, BlockInfo> saved = new HashMap<>();
        for (BlockPos vp : positions) {
            if (world.getBlockEntity(vp) != null) continue; // BER blocks go invisible when state-overridden
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

    private Map<BlockPos, BlockInfo> maskHiddenBlocks(Set<BlockPos> visibleSnapshot) {
        Map<BlockPos, BlockInfo> saved = new HashMap<>();
        BlockInfo air = BlockInfo.fromBlockState(Blocks.AIR.defaultBlockState());

        Set<BlockPos> totalTracked = new HashSet<>(bakedAll);
        totalTracked.addAll(baseplatePositions);

        for (BlockPos pos : totalTracked) {
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

            // Draw any completed streaming chunks on top of front[].
            // streamChunksReady is written on the render thread inside recordRenderCall
            // so reading it here (also render thread) is safe without volatile guards.
            int readyChunks = streamChunksReady;
            for (int c = 0; c < readyChunks && c < streamChunks.size(); c++) {
                VertexBuffer[] chunkVBOs = streamChunks.get(c);
                if (chunkVBOs != null && chunkVBOs[i] != null && !chunkVBOs[i].isInvalid() &&
                        chunkVBOs[i].getFormat() != null) {
                    chunkVBOs[i].bind();
                    chunkVBOs[i].draw();
                }
            }

            if ((overlayReady || hasLiveOverlay()) && isOverlayLayerReady(i)) {
                overlay[i].bind();
                overlay[i].draw();
            }

            VertexBuffer.unbind();
            shader.clear();
            layer.clearRenderState();
        }
    }

    private boolean hasLiveOverlay() {
        for (int i = 0; i < LAYER_COUNT; i++) {
            if (isOverlayLayerReady(i)) return true;
        }
        return false;
    }

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
        // Skip BEs whose level reference is null — their clientTick may assume a real world
        // (e.g. GTCEu MufflerPartMachine calls Level.getBlockState via capability helpers).
        if (be.getLevel() == null) return;
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

    // ── Fixed Tile Entities Draw ─────────────────────────────────────────────
    private void drawTileEntities(PoseStack poseStack, MultiBufferSource.BufferSource buffers, float partial,
                                  float camX, float camY, float camZ) {
        Minecraft mc = Minecraft.getInstance();
        var dispatcher = mc.getBlockEntityRenderDispatcher();
        int count = 0;

        for (BlockPos pos : frontTileEntities) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be == null || be.isRemoved()) continue;

            BlockEntityRenderer<BlockEntity> ber = dispatcher.getRenderer(be);
            if (ber == null) continue;

            poseStack.pushPose();

            // FIX: Use the BlockEntity's internal position
            BlockPos bePos = be.getBlockPos();

            double renderX = bePos.getX() - this.slotOrigin.getX();
            double renderY = bePos.getY() - this.slotOrigin.getY();
            double renderZ = bePos.getZ() - this.slotOrigin.getZ();
            poseStack.translate(renderX, renderY, renderZ);

            count++;

            long berStart = System.nanoTime();
            try {
                ber.render(be, partial, poseStack, buffers, 15728880, OverlayTexture.NO_OVERLAY);
                frameBerCount++;
                if (tickedThisFrame) driveClientTick(be);
            } catch (Exception e) {
                LOGGER.warn("[Phantasia] BE render error at {}: {}", pos, e.getMessage());
            }
            totalBerRenderTimeNs += (System.nanoTime() - berStart);

            poseStack.popPose();

        }
        maxBerCountTracked = Math.max(maxBerCountTracked, count);
    }

    // ── Fixed Dynamic Renderers Draw (e.g. Fusion Plasma Rings) ──────────────
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void drawDynamicRenderers(PoseStack poseStack, MultiBufferSource.BufferSource buffers, float partial,
                                      float camX, float camY, float camZ) {
        Vec3 cameraPos = new Vec3(camX, camY, camZ);
        for (BlockPos pos : frontTileEntities) {
            BlockEntity be = world.getBlockEntity(pos);
            if (!(be instanceof MetaMachineBlockEntity mmbe)) continue;
            MetaMachine machine = mmbe.getMetaMachine();
            if (machine == null) continue;

            // FIX: Grab the coordinate state directly from the metamachine itself
            // to stay anchored correctly inside fake/dummy world layouts.
            BlockPos machinePos = machine.getPos();

            com.lowdragmc.lowdraglib.client.renderer.IRenderer iRenderer = null;
            try {
                com.gregtechceu.gtceu.api.machine.MachineDefinition def = machine.getDefinition();
                for (Class<?> defCls = def.getClass(); defCls != null &&
                        defCls != Object.class; defCls = defCls.getSuperclass()) {
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

            for (Class<?> cls = iRenderer.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
                for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(iRenderer);
                        if (val == null) continue;
                        if (val instanceof DynamicRender<?, ?> dr) {
                            // FIX: Pass machinePos instead of layout pos
                            renderOneDynamic(dr, machine, machinePos, cameraPos, partial, poseStack, buffers);
                        } else if (val instanceof java.util.List<?> list) {
                            for (Object item : list) {
                                if (item instanceof DynamicRender<?, ?> dr) {
                                    // FIX: Pass machinePos instead of layout pos
                                    renderOneDynamic(dr, machine, machinePos, cameraPos, partial, poseStack, buffers);
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void renderOneDynamic(DynamicRender dr, MetaMachine machine, BlockPos pos,
                                  Vec3 cameraPos, float partial,
                                  PoseStack poseStack, MultiBufferSource.BufferSource buffers) {
        try {
            if (!dr.shouldRender(machine, cameraPos)) return;
        } catch (Throwable ignored) {}

        poseStack.pushPose();

        // FIX: Use the MetaMachine's internal position rather than the spatial loop position
        BlockPos machinePos = machine.getPos();

        double renderX = machinePos.getX() - this.slotOrigin.getX();
        double renderY = machinePos.getY() - this.slotOrigin.getY();
        double renderZ = machinePos.getZ() - this.slotOrigin.getZ();
        poseStack.translate(renderX, renderY, renderZ);

        try {
            dr.render(machine, partial, poseStack, buffers,
                    15728880, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        } catch (Throwable e) {
            LOGGER.warn("[Phantasia] DynamicRender error at {}: {}", pos, e.getMessage());
        }
        poseStack.popPose();
    }

    // ── Fixed Entity Draw ────────────────────────────────────────────────────
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

                poseStack.pushPose();

                double renderX = d0 - this.slotOrigin.getX();
                double renderY = d1 - this.slotOrigin.getY();
                double renderZ = d2 - this.slotOrigin.getZ();
                poseStack.translate(renderX, renderY, renderZ);

                erd.render(entity, 0.0, 0.0, 0.0, yRot, partial, poseStack, buffers,
                        erd.getRenderer(entity).getPackedLightCoords(entity, partial));

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

        double ox = this.slotOrigin != null ? this.slotOrigin.getX() : 0.0;
        double oy = this.slotOrigin != null ? this.slotOrigin.getY() : 0.0;
        double oz = this.slotOrigin != null ? this.slotOrigin.getZ() : 0.0;

        float aspect = (float) glW / glH;
        RenderSystem.setProjectionMatrix(
                new Matrix4f().setPerspective((float) Math.toRadians(FOV), aspect, NEAR, FAR),
                // FIX: Sort translucent vertices using slot-relative eye coordinates
                VertexSorting.byDistance(new Vector3f((float) (view.eyeX() - ox), (float) (view.eyeY() - oy),
                        (float) (view.eyeZ() - oz))));

        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();

        // FIX: Changed view.lookAtY() - oz to view.lookAtY() - oy
        Project.gluLookAt(mv,
                (float) (view.eyeX() - ox), (float) (view.eyeY() - oy), (float) (view.eyeZ() - oz),
                (float) (view.lookAtX() - ox), (float) (view.lookAtY() - oy), (float) (view.lookAtZ() - oz),
                0f, 1f, 0f);

        RenderSystem.applyModelViewMatrix();
        RenderSystem.activeTexture(33984);
        syncCameraEntity(view, ox, oy, oz);
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

    private void syncCameraEntity(CameraView view, double ox, double oy, double oz) {
        Vector3f dir = view.direction();
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x(), dir.z()));
        float hd = (float) Math.sqrt(dir.x() * dir.x() + dir.z() * dir.z());
        float pitch = (float) Math.toDegrees(Math.atan2(-dir.y(), hd));

        double relX = view.eyeX() - ox;
        double relY = view.eyeY() - oy;
        double relZ = view.eyeZ() - oz;
        cameraEntity.setPos(relX, relY, relZ);
        cameraEntity.setYRot(yaw);
        cameraEntity.setXRot(pitch);
        cameraEntity.xo = relX;
        cameraEntity.yo = relY;
        cameraEntity.zo = relZ;
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
        // Close any streaming chunk VBOs that are still alive.
        for (VertexBuffer[] chunk : streamChunks) {
            for (VertexBuffer vb : chunk) {
                if (vb != null && !vb.isInvalid()) vb.close();
            }
        }
        streamChunks.clear();
        streamChunksReady = 0;
        // Release large collections so the bake thread can't keep them alive after close.
        animateTickList = Collections.emptyList();
        animateTickIdx = 0;
        targetVisible = Collections.emptySet();
        bakedAll = Collections.emptySet();
        animateTickEligible = Collections.emptySet();
        blockAlpha.clear();
        blockLayers.clear();
    }
}
