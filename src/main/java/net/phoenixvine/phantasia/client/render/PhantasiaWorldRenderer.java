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

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.phoenixvine.phantasia.common.PhantasiaVariantState;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PhantasiaWorldRenderer
 *
 * ── Rendering model ───────────────────────────────────────────────────────────
 *
 * VISIBILITY      → GPU bitmask (UBO or SSBO). setVisible() = bit rebuild + partial
 *                   glBufferSubData. ~1–5 µs regardless of pattern size.
 *
 * FULL BAKE       → scheduleBake(). Bakes ALL pattern blocks once on initial load.
 *                   No temporary AIR, no per-step rebakes. Double-buffered.
 *
 * PARTIAL BAKE    → requestPartialBake(changedPositions). Bakes only the specified
 *                   positions (e.g. coil blocks) and splices their geometry into the
 *                   existing front buffers using a per-layer overlay VBO. Avoids
 *                   rebuilding the entire structure for localised state changes.
 *
 * ACTIVE/WORKING  → requestPartialBake(controllerPos ∪ overlayAnimatedPos).
 *                   Only controller + coil overlays change; structure geometry intact.
 *
 * COIL SWAP       → requestPartialBake(allCoilPositions). Rebakes only the coil
 *                   positions (could be 200–2000 blocks) instead of all 3M.
 *
 * TILE ENTITIES   → still rendered via immediate BER each frame; no bake involved.
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

    private static final FloatBuffer SCRATCH_MV    = direct(64).asFloatBuffer();
    private static final FloatBuffer SCRATCH_PROJ  = direct(64).asFloatBuffer();
    private static final IntBuffer   SCRATCH_VP    = direct(16 * 4).asIntBuffer();
    private static final FloatBuffer PIXEL_DEPTH   = direct(4).asFloatBuffer();
    private static final FloatBuffer UNPROJECT_OUT = direct(12).asFloatBuffer();

    private final float[] snapMV   = new float[16];
    private final float[] snapProj = new float[16];
    private final int[]   snapVP   = new int[4];

    private static ByteBuffer direct(int bytes) {
        return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final float FOV  = 60f;
    private static final float NEAR = 0.1f;
    private static final float FAR  = 10_000f;
    private static final float ALPHA_STEP = 0.2f;
    private static final int TRANSITION_THRESHOLD = 32;

    // ── Layers ────────────────────────────────────────────────────────────────

    private final List<RenderType> LAYERS      = RenderType.chunkBufferLayers();
    private final int              LAYER_COUNT = LAYERS.size();

    // ── Double-buffered main geometry VBOs ────────────────────────────────────

    private final VertexBuffer[] front;
    private final VertexBuffer[] back;
    private volatile boolean backReady = false;

    /** Parallel block-ID VBOs (one int per vertex = compact block ID). */
    private final int[] frontIdVbo;
    private final int[] backIdVbo;

    // ── Partial-bake overlay VBOs ─────────────────────────────────────────────
    //
    // After a partial bake, overlay[i] holds the updated geometry for the changed
    // blocks in layer i, and overlayIdVbo[i] holds their block IDs. Both are drawn
    // AFTER front[i] each frame to overpaint the stale geometry. A subsequent full
    // bake clears the overlay.

    private final VertexBuffer[] overlay;
    private final int[]          overlayIdVbo;
    private volatile boolean     overlayReady = false;

    // ── Bake coordination ─────────────────────────────────────────────────────

    private volatile boolean fullBakeNeeded    = false;
    private volatile boolean partialBakePending = false;

    /** Positions queued for the next partial bake. Set from the main thread. */
    private volatile Set<BlockPos> partialBakePositions = Collections.emptySet();

    private final AtomicInteger pendingUploads = new AtomicInteger(0);

    @Nullable private Future<?> bakeFuture = null;

    // ── Visibility ────────────────────────────────────────────────────────────

    private Set<BlockPos> targetVisible      = Collections.emptySet();
    private Set<BlockPos> bakedAll           = Collections.emptySet();
    private Set<BlockPos> baseplatePositions = Collections.emptySet();
    private Map<BlockPos, Integer> posToId   = Collections.emptyMap();
    private int totalBakedBlocks             = 0;
    private Set<BlockPos> animateTickEligible = Collections.emptySet();

    @Nullable private PhantasiaVisibilityBuffer visibilityBuffer = null;

    // ── Fade-in state (small transitions only) ────────────────────────────────

    private final Map<BlockPos, Float>               blockAlpha  = new HashMap<>();
    private final Map<BlockPos, List<RenderType>>    blockLayers = new HashMap<>();
    private boolean hasTransitions = false;

    // ── Animated / tile-entity tracking ──────────────────────────────────────

    private Set<BlockPos> animatedPositions       = Collections.emptySet();
    private volatile Set<BlockPos> backAnimatedPositions = null;
    private final Map<BlockPos, List<RenderType>> animatedLayers = new HashMap<>();

    private volatile Set<BlockPos> backTileEntities  = null;
    private Set<BlockPos> frontTileEntities          = Collections.emptySet();

    // ── Scene state ───────────────────────────────────────────────────────────

    private final TrackedDummyWorld world;
    @Nullable private BlockPos controllerWorldPos = null;

    /**
     * The slot origin this pattern was placed at (from PhantasiaSlotAllocator).
     * Set once after pattern load via {@link #setSlotOrigin}.
     *
     * All block geometry is baked at world-space slot coordinates (potentially
     * tens of thousands of blocks from 0,0). The modelview matrix is shifted by
     * -slotOrigin each frame so the GPU sees coordinates near zero, preserving
     * the full float32 precision that GT's 0.001-block overlay offset requires.
     *
     * The raytrace unproject result is shifted back by +slotOrigin so returned
     * BlockPos values remain in dummy-world coordinate space.
     */
    private BlockPos slotOrigin = BlockPos.ZERO;

    private int guiMouseX, guiMouseY;
    private long lastParticleTick = -1;
    private boolean tickedThisFrame = false;
    @Nullable private BlockHitResult lastHitResult;

    private final PhantasiaCameraEntity cameraEntity;
    private final Camera camera;

    // ── Bake pool ─────────────────────────────────────────────────────────────

    private static final ExecutorService BAKE_POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Phantasia-BakeThread");
        t.setDaemon(true);
        return t;
    });

    private static final org.slf4j.Logger LOGGER =
            com.mojang.logging.LogUtils.getLogger();
    private static final boolean DEBUG_RENDER = false;
    private int debugFrameCounter = 0;

    // ── Constructor ───────────────────────────────────────────────────────────

    public PhantasiaWorldRenderer(TrackedDummyWorld world) {
        this.world        = world;
        this.cameraEntity = new PhantasiaCameraEntity(world);
        this.camera       = new Camera();

        this.front        = new VertexBuffer[LAYER_COUNT];
        this.back         = new VertexBuffer[LAYER_COUNT];
        this.overlay      = new VertexBuffer[LAYER_COUNT];
        this.frontIdVbo   = new int[LAYER_COUNT];
        this.backIdVbo    = new int[LAYER_COUNT];
        this.overlayIdVbo = new int[LAYER_COUNT];

        for (int i = 0; i < LAYER_COUNT; i++) {
            front[i]   = new VertexBuffer(VertexBuffer.Usage.STATIC);
            back[i]    = new VertexBuffer(VertexBuffer.Usage.STATIC);
            overlay[i] = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        }
        GL15.glGenBuffers(frontIdVbo);
        GL15.glGenBuffers(backIdVbo);
        GL15.glGenBuffers(overlayIdVbo);
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
    public void setSlotOrigin(BlockPos origin) {
        this.slotOrigin = origin != null ? origin : BlockPos.ZERO;
    }

    public void setPatternBlocks(Set<BlockPos> all) {
        this.bakedAll = Set.copyOf(all);
    }

    /**
     * Assigns stable compact IDs to every block and allocates the GPU visibility
     * buffer. Must be called once after pattern load, before the first bake.
     */
    public void assignBlockIds(Set<BlockPos> allPatternPositions) {
        Map<BlockPos, Integer> ids = new HashMap<>(allPatternPositions.size() + baseplatePositions.size());
        int id = 0;
        for (BlockPos pos : allPatternPositions) ids.put(pos, id++);
        for (BlockPos pos : baseplatePositions)  if (!ids.containsKey(pos)) ids.put(pos, id++);
        this.posToId          = Collections.unmodifiableMap(ids);
        this.totalBakedBlocks = id;
        rebuildVisibilityBuffer();
    }

    public void rebuildVisibilityBuffer() {
        if (visibilityBuffer != null) visibilityBuffer.close();
        visibilityBuffer = totalBakedBlocks > 0
                ? PhantasiaVisibilityBuffer.create(totalBakedBlocks)
                : null;
    }

    /**
     * Returns the block count and SSBO flag so the caller can trigger a shader
     * recompile after assignBlockIds().
     */
    public int getTotalBakedBlocks() { return totalBakedBlocks; }

    public boolean needsSSBO() {
        if (visibilityBuffer == null) return false;
        return visibilityBuffer.isSSBO();
    }

    /**
     * Updates the GPU visibility bitmask. O(visible.size()) + partial GPU upload.
     * Does not schedule any CPU bake.
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
        animateTickEligible = eligible.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(eligible);

        // GPU bitmask update.
        if (visibilityBuffer != null && !posToId.isEmpty()) {
            Set<BlockPos> gpuVisible = new HashSet<>(newVisible);
            gpuVisible.addAll(baseplatePositions);
            visibilityBuffer.setVisible(gpuVisible, posToId);
        }

        // Small fade-in for newly appearing blocks.
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
     *  - Coil type swap:  pass all coil positions (typically 200–2000 blocks).
     *  - ACTIVE toggle:   pass controller pos + any animated overlay positions.
     *  - Variant switch:  pass the positions in the toggled OptionalGroup.
     *
     * The partial bake produces overlay VBOs that overpaint the stale geometry
     * for the changed blocks without touching the rest of the baked scene. It runs
     * on the bake thread concurrently with a pending full bake — if both are queued,
     * the partial bake runs first, then the full bake absorbs its result.
     *
     * Block IDs are stable so the visibility UBO remains valid throughout.
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
        animateTickEligible      = Collections.emptySet();
        hasTransitions           = false;
        overlayReady             = false;
        partialBakePending       = false;
        partialBakePositions     = Collections.emptySet();
        fullBakeNeeded           = true;
    }

    public void setMousePos(int mx, int my) { this.guiMouseX = mx; this.guiMouseY = my; }
    @Nullable public BlockHitResult getLastHitResult() { return lastHitResult; }
    public boolean isVisible(BlockPos pos) {
        return targetVisible.contains(pos) || baseplatePositions.contains(pos);
    }

    // ── Main render entry ─────────────────────────────────────────────────────

    public void render(CameraView view, int guiX, int guiY, int guiW, int guiH) {
        if (guiW <= 0 || guiH <= 0) return;

        PhantasiaShaders.flushPending();
        tickAlpha();

        if (backReady)    swapFullBuffers();
        if (overlayReady) swapOverlayBuffers();

        PhantasiaSpriteMarker.markAll(Set.of());

        boolean bakeIdle = bakeFuture == null || bakeFuture.isDone();
        if (bakeIdle) {
            if (partialBakePending && !hasTransitions) {
                partialBakePending    = false;
                Set<BlockPos> targets = partialBakePositions;
                partialBakePositions  = Collections.emptySet();
                schedulePartialBake(targets);
            } else if (fullBakeNeeded && !hasTransitions) {
                fullBakeNeeded = false;
                scheduleFullBake();
            }
        }

        Minecraft mc = Minecraft.getInstance();
        double scale  = mc.getWindow().getGuiScale();
        int windowH   = mc.getWindow().getHeight();
        int glX = (int)(guiX * scale);
        int glY = (int)(windowH - (guiY + guiH) * scale);
        int glW = (int)(guiW * scale);
        int glH = (int)(guiH * scale);

        setupCamera(view, glX, glY, glW, glH);

        long totalTicks = mc.level != null ? mc.level.getGameTime() : 0;
        RenderSystem.setShaderGameTime(totalTicks, (totalTicks + mc.getFrameTime()) / 20f);
        snapshotMatrices();

        drawVBOs();

        if (hasTransitions) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            MultiBufferSource.BufferSource dynBuffers = mc.renderBuffers().bufferSource();
            drawFadingIn(dynBuffers);
            dynBuffers.endBatch();
        }

        float partial = mc.getFrameTime();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        turnOnLight(partial);
        float camX = view.eyeX(), camY = view.eyeY(), camZ = view.eyeZ();

        // ── MATRIX TRACKING SYNC FIX ──────────────────────────────────────────
        // 1. Grab the active global ModelView stack (which matches setupCamera's state).
        PoseStack currentPoseStack = RenderSystem.getModelViewStack();
        currentPoseStack.pushPose();

        // 2. Pass this exact stack down into the renderers.
        drawTileEntities(currentPoseStack, buffers, partial, camX, camY, camZ);
        drawEntities(currentPoseStack, buffers, partial, camX, camY, camZ);

        // 3. Make sure the system registers our matrix mutations before flushing the buffer
        RenderSystem.applyModelViewMatrix();

        // 4. Flush the quads out. Now the multi-buffer layer builders will use
        // the correct matched matrices rather than floating point defaults.
        buffers.endBatch();

        // 5. Cleanup the stack safely.
        currentPoseStack.popPose();
        RenderSystem.applyModelViewMatrix();
        // ──────────────────────────────────────────────────────────────────────

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

        turnOffLight();
        lastHitResult = doRayTrace(view, scale, windowH);
        resetCamera();
    }

    // ── Alpha tick ────────────────────────────────────────────────────────────

    private void tickAlpha() {
        if (blockAlpha.isEmpty()) return;
        Iterator<Map.Entry<BlockPos, Float>> it = blockAlpha.entrySet().iterator();
        boolean any = false;
        while (it.hasNext()) {
            Map.Entry<BlockPos, Float> e = it.next();
            float next = Math.min(1f, e.getValue() + ALPHA_STEP);
            if (next >= 1f) { it.remove(); blockLayers.remove(e.getKey()); }
            else            { e.setValue(next); any = true; }
        }
        hasTransitions = any;
    }

    // ── Buffer swaps ──────────────────────────────────────────────────────────

    private void swapFullBuffers() {
        for (int i = 0; i < LAYER_COUNT; i++) {
            VertexBuffer tmp = front[i]; front[i] = back[i]; back[i] = tmp;
            int tmpId = frontIdVbo[i]; frontIdVbo[i] = backIdVbo[i]; backIdVbo[i] = tmpId;
        }
        frontTileEntities   = backTileEntities      != null ? backTileEntities      : Collections.emptySet();
        animatedPositions   = backAnimatedPositions != null ? backAnimatedPositions : Collections.emptySet();
        backReady           = false;
        backTileEntities    = null;
        backAnimatedPositions = null;
        // Full bake absorbs any overlay that was previously applied.
        overlayReady = false;
        if (visibilityBuffer != null)
            visibilityBuffer.setVisible(union(targetVisible, baseplatePositions), posToId);
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
        Set<BlockPos> r = new HashSet<>(a); r.addAll(b); return r;
    }

    // ── Full bake ─────────────────────────────────────────────────────────────

    private void scheduleFullBake() {
        Set<BlockPos> snapshot = Set.copyOf(bakedAll);
        if (snapshot.isEmpty()) { uploadEmptyBuffers(false); return; }

        pendingUploads.set(LAYER_COUNT);
        bakeFuture = BAKE_POOL.submit(() -> {
            Minecraft mc = Minecraft.getInstance();
            BlockRenderDispatcher brd = mc.getBlockRenderer();
            RandomSource random = RandomSource.createNewThreadLocalInstance();
            ModelBlockRenderer.enableCaching();

            PhantasiaVariantState vs = PhantasiaVariantState.get();
            Map<BlockPos, BlockInfo> variantSaved = applyVariants(vs, snapshot);

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
                            uploadToVBO(back[fi], backIdVbo[fi], bl);
                            if (pendingUploads.decrementAndGet() == 0) backReady = true;
                        } finally { slot.release(); }
                    });
                    try { slot.acquire(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                }
            } finally {
                ModelBlockRenderer.clearCache();
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

        pendingUploads.set(LAYER_COUNT);
        bakeFuture = BAKE_POOL.submit(() -> {
            Minecraft mc = Minecraft.getInstance();
            BlockRenderDispatcher brd = mc.getBlockRenderer();
            RandomSource random = RandomSource.createNewThreadLocalInstance();
            ModelBlockRenderer.enableCaching();

            PhantasiaVariantState vs = PhantasiaVariantState.get();
            Map<BlockPos, BlockInfo> variantSaved = applyVariants(vs, valid);

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
                            uploadToVBO(overlay[fi], overlayIdVbo[fi], bl);
                            if (pendingUploads.decrementAndGet() == 0) overlayReady = true;
                        } finally { slot.release(); }
                    });
                    try { slot.acquire(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                }
            } finally {
                ModelBlockRenderer.clearCache();
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

    /** Bakes one layer into a BakedLayer (RenderedBuffer + block ID IntBuffer). */
    private BakedLayer bakeLayerToBuffer(BlockRenderDispatcher brd, RandomSource random,
                                         RenderType layer,
                                         List<BlockPos> solid, List<BlockPos> fluid,
                                         Set<BlockPos> animatedOut) {
        int count    = solid.size() + fluid.size();
        int sizeHint = Math.max(layer.bufferSize(), Math.min(count * 512, 64 * 1024 * 1024));
        BufferBuilder bb = new BufferBuilder(sizeHint);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        PoseStack ps = new PoseStack();
        TintedVertexConsumer tinted = new TintedVertexConsumer(bb);
        PhantasiaVertexConsumer pvc = new PhantasiaVertexConsumer(tinted);

        for (BlockPos pos : solid) {
            pvc.setCurrentBlockId(posToId.getOrDefault(pos, 0));
            BlockState state = world.getBlockState(pos);
            ps.pushPose(); ps.translate(pos.getX(), pos.getY(), pos.getZ());

            if (Platform.isForge()) {
                WorldSceneRenderer.renderBlocksForge(brd, state, pos, world, ps, pvc, random, layer);
            } else {
                brd.renderBatched(state, pos, world, ps, pvc, true, random);
            }

            ps.popPose();
            tinted.resetTint();

            // FIX: If you still need tracking for block positions that have an active animation tick
            // (e.g., for custom tickers), use the state directly. Otherwise, if your atlas-wide scan
            // completely handles Embeddium compatibility, you can safely delete this block-level check.
            if (state.isRandomlyTicking() || state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock) {
                animatedOut.add(pos);
            }
        }

        for (BlockPos pos : fluid) {
            pvc.setCurrentBlockId(posToId.getOrDefault(pos, 0));
            BlockState state = world.getBlockState(pos);
            FluidState fs = state.getFluidState();
            if (fs.isEmpty()) continue;
            tinted.addOffset(pos.getX() - (pos.getX() & 15),
                    pos.getY() - (pos.getY() & 15),
                    pos.getZ() - (pos.getZ() & 15));
            brd.renderLiquid(pos, world, pvc, state, fs);
            tinted.clearOffset();
            tinted.resetTint();
        }

        return new BakedLayer(bb.end(), pvc.blockIdBuffer());
    }

    private void uploadToVBO(VertexBuffer vbo, int idVboHandle, BakedLayer bl) {
        if (!vbo.isInvalid()) {
            vbo.bind();
            vbo.upload(bl.renderedBuffer());
            VertexBuffer.unbind();
        }
        if (bl.blockIds().limit() > 0) {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, idVboHandle);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, bl.blockIds(), GL15.GL_DYNAMIC_DRAW);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
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
                if (!target.isInvalid()) { target.bind(); target.upload(rb); VertexBuffer.unbind(); }
                if (pendingUploads.decrementAndGet() == 0) {
                    if (intoOverlay) overlayReady = true; else backReady = true;
                }
            });
        }
        if (!intoOverlay) { backTileEntities = Collections.emptySet(); backAnimatedPositions = Collections.emptySet(); }
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

    private record BakedLayer(BufferBuilder.RenderedBuffer renderedBuffer, IntBuffer blockIds) {}

    // ── VBO draw ──────────────────────────────────────────────────────────────

    private void drawVBOs() {
        ShaderInstance phantasiaShader = PhantasiaShaders.PHANTASIA_BLOCK;

        for (int i = 0; i < LAYER_COUNT; i++) {
            VertexBuffer vbo = front[i];
            RenderType layer = LAYERS.get(i);
            if (vbo.isInvalid() || vbo.getFormat() == null) continue;

            layer.setupRenderState();
            applyLayerBlend(layer);

            ShaderInstance shader = phantasiaShader != null ? phantasiaShader : RenderSystem.getShader();
            if (shader == null) { layer.clearRenderState(); continue; }

            bindShaderSamplers(shader);
            setShaderUniforms(shader);
            RenderSystem.setupShaderLights(shader);

            if (visibilityBuffer != null && phantasiaShader != null) visibilityBuffer.bind();

            shader.apply();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            // Draw main VBO.
            drawVBOWithIds(vbo, frontIdVbo[i], phantasiaShader != null);

            // Draw overlay on top if one is live.
            // The overlay VBO contains only the changed blocks — it overpaints the
            // stale geometry for those positions with the up-to-date block states.
            // The visibility shader still discards hidden overlay fragments correctly
            // because block IDs are stable and the visibility UBO hasn't changed.
            // Guard per-layer: a partial bake may upload geometry for only some layers;
            // drawing an overlay[i] that was never uploaded (mode == null) causes an NPE.
            if ((overlayReady || hasLiveOverlay()) && isOverlayLayerReady(i)) {
                drawVBOWithIds(overlay[i], overlayIdVbo[i], phantasiaShader != null);
            }

            if (phantasiaShader != null) {
                GL20.glDisableVertexAttribArray(PhantasiaVertexConsumer.ATTRIB_LOC);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
                if (visibilityBuffer != null) visibilityBuffer.unbind();
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

    /** True when overlay[i] has been successfully uploaded (format non-null = mode non-null). */
    private boolean isOverlayLayerReady(int i) {
        VertexBuffer ov = overlay[i];
        return ov != null && !ov.isInvalid() && ov.getFormat() != null;
    }

    private void drawVBOWithIds(VertexBuffer vbo, int idVbo, boolean useCustomShader) {
        vbo.bind();
        if (useCustomShader && idVbo != 0) {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, idVbo);
            GL20.glEnableVertexAttribArray(PhantasiaVertexConsumer.ATTRIB_LOC);

            // FIX: Use GL30 for raw integer vertex attributes
            org.lwjgl.opengl.GL30.glVertexAttribIPointer(PhantasiaVertexConsumer.ATTRIB_LOC, 1, GL11.GL_INT, 0, 0);
        }
        vbo.draw();
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
                ps.pushPose(); ps.translate(pos.getX(), pos.getY(), pos.getZ());
                if (Platform.isForge()) WorldSceneRenderer.renderBlocksForge(brd, state, pos, world, ps, tinted, random, layer);
                else                    brd.renderBatched(state, pos, world, ps, tinted, true, random);
                ps.popPose(); tinted.resetTint();
            }
        }
    }



    // ── clientTick via MethodHandle ───────────────────────────────────────────

    private static final java.lang.invoke.MethodHandle NO_OP;
    static {
        try { NO_OP = java.lang.invoke.MethodHandles.lookup().findStatic(PhantasiaWorldRenderer.class, "noOp",
                java.lang.invoke.MethodType.methodType(void.class)); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    private static void noOp() {}

    private static final ClassValue<java.lang.invoke.MethodHandle> GET_MACHINE = new ClassValue<>() {
        @Override protected java.lang.invoke.MethodHandle computeValue(Class<?> t) {
            var lookup = java.lang.invoke.MethodHandles.lookup();
            for (String n : new String[]{"getMetaMachine","getMachine","getOwner"}) {
                try { return lookup.unreflect(t.getMethod(n)); } catch (Exception ignored) {}
            }
            return NO_OP;
        }
    };
    private static final ClassValue<java.lang.invoke.MethodHandle> CLIENT_TICK = new ClassValue<>() {
        @Override protected java.lang.invoke.MethodHandle computeValue(Class<?> c) {
            var lookup = java.lang.invoke.MethodHandles.lookup();
            for (Class<?> cur = c; cur != null; cur = cur.getSuperclass()) {
                try { var m = cur.getDeclaredMethod("clientTick"); m.setAccessible(true); return lookup.unreflect(m); }
                catch (Exception ignored) {}
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
        } catch (Throwable t) { LOGGER.warn("[Phantasia] driveClientTick: {}", t.getMessage()); }
    }

// ── Tile-entity draw ──────────────────────────────────────────────────────

    private void drawTileEntities(PoseStack poseStack, MultiBufferSource.BufferSource buffers, float partial,
                                  float camX, float camY, float camZ) {
        Minecraft mc = Minecraft.getInstance();
        long currentTick = mc.level != null ? mc.level.getGameTime() : -1;
        boolean tick = currentTick >= 0 && currentTick != lastParticleTick;
        if (tick) lastParticleTick = currentTick;
        tickedThisFrame = tick;

        for (BlockPos pos : frontTileEntities) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be == null || !be.hasLevel() || !be.getType().isValid(be.getBlockState())) continue;
            @SuppressWarnings("unchecked")
            BlockEntityRenderer<BlockEntity> ber =
                    (BlockEntityRenderer<BlockEntity>) mc.getBlockEntityRenderDispatcher().getRenderer(be);
            if (ber == null) continue;

            // FIX: Push onto the global matrix stack
            poseStack.pushPose();
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());

            try {
                ber.render(be, partial, poseStack, buffers, 15728880, OverlayTexture.NO_OVERLAY);
                if (tick) driveClientTick(be);
            } catch (Exception e) {
                LOGGER.warn("[Phantasia] BE render error at {}: {}", pos, e.getMessage());
            }

            // FIX: Pop off the global matrix stack
            poseStack.popPose();
        }
    }

    // ... (keep the driveClientTick / MethodHandle code identical) ...

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
                    entity.xOld = entity.getX(); entity.yOld = entity.getY(); entity.zOld = entity.getZ();
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

    private void turnOnLight(float p)  { try { Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();  } catch (Exception ignored) {} }
    private void turnOffLight()        { try { Minecraft.getInstance().gameRenderer.lightTexture().turnOffLightLayer(); } catch (Exception ignored) {} }

    // ── Camera ────────────────────────────────────────────────────────────────

    private void setupCamera(CameraView view, int glX, int glY, int glW, int glH) {
        RenderSystem.enableDepthTest(); RenderSystem.enableBlend();
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
        mv.pushPose(); mv.setIdentity();
        Project.gluLookAt(mv, view.eyeX(), view.eyeY(), view.eyeZ(),
                view.lookAtX(), view.lookAtY(), view.lookAtZ(), 0f, 1f, 0f);

        // ── Precision fix ────────────────────────────────────────────────────
        // Block geometry is baked at slot-space coordinates (origin may be tens of
        // thousands of blocks from 0,0). Translating the modelview by -slotOrigin
        // re-centres everything to near-zero before the vertex shader sees it,
        // preserving the float32 sub-block precision that GT's 0.001-block overlay
        // offset requires to render without z-fighting or disappearing overlays.
        mv.translate(-slotOrigin.getX(), -slotOrigin.getY(), -slotOrigin.getZ());
        // ─────────────────────────────────────────────────────────────────────

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
        mv.popPose(); RenderSystem.applyModelViewMatrix();
        RenderSystem.depthMask(false); RenderSystem.disableDepthTest(); RenderSystem.enableBlend();
    }

    private void syncCameraEntity(CameraView view) {
        Vector3f dir = view.direction();
        float yaw   = (float) Math.toDegrees(Math.atan2(-dir.x(), dir.z()));
        float hd    = (float) Math.sqrt(dir.x() * dir.x() + dir.z() * dir.z());
        float pitch = (float) Math.toDegrees(Math.atan2(-dir.y(), hd));
        cameraEntity.setPos(view.eyeX(), view.eyeY(), view.eyeZ());
        cameraEntity.setYRot(yaw); cameraEntity.setXRot(pitch);
        cameraEntity.xo = cameraEntity.getX(); cameraEntity.yo = cameraEntity.getY(); cameraEntity.zo = cameraEntity.getZ();
        cameraEntity.yRotO = yaw; cameraEntity.xRotO = pitch;
    }

    // ── Matrix snapshot ───────────────────────────────────────────────────────

    private void snapshotMatrices() {
        RenderSystem.getModelViewMatrix().get(SCRATCH_MV);   SCRATCH_MV.rewind();
        RenderSystem.getProjectionMatrix().get(SCRATCH_PROJ); SCRATCH_PROJ.rewind();
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, SCRATCH_VP);    SCRATCH_VP.rewind();
        for (int i = 0; i < 16; i++) snapMV[i]   = SCRATCH_MV.get(i);
        for (int i = 0; i < 16; i++) snapProj[i] = SCRATCH_PROJ.get(i);
        for (int i = 0; i < 4;  i++) snapVP[i]   = SCRATCH_VP.get(i);
        SCRATCH_MV.rewind(); SCRATCH_PROJ.rewind(); SCRATCH_VP.rewind();
    }

    // ── Ray-trace ─────────────────────────────────────────────────────────────

    @Nullable
    private BlockHitResult doRayTrace(CameraView view, double guiScale, int windowH) {
        int glX = (int)(guiMouseX * guiScale);
        int glY = (int)(windowH - guiMouseY * guiScale);
        if (glX < snapVP[0] || glX > snapVP[0] + snapVP[2] || glY < snapVP[1] || glY > snapVP[1] + snapVP[3])
            return null;
        GL11.glReadPixels(glX, glY, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, PIXEL_DEPTH);
        PIXEL_DEPTH.rewind(); float depth = PIXEL_DEPTH.get(); PIXEL_DEPTH.rewind();
        for (int i = 0; i < 16; i++) SCRATCH_MV.put(i, snapMV[i]);
        for (int i = 0; i < 16; i++) SCRATCH_PROJ.put(i, snapProj[i]);
        for (int i = 0; i < 4;  i++) SCRATCH_VP.put(i, snapVP[i]);
        SCRATCH_MV.rewind(); SCRATCH_PROJ.rewind(); SCRATCH_VP.rewind();
        Project.gluUnProject(glX, glY, depth, SCRATCH_MV, SCRATCH_PROJ, SCRATCH_VP, UNPROJECT_OUT);
        SCRATCH_MV.rewind(); SCRATCH_PROJ.rewind(); SCRATCH_VP.rewind(); UNPROJECT_OUT.rewind();
        float hx = UNPROJECT_OUT.get(), hy = UNPROJECT_OUT.get(), hz = UNPROJECT_OUT.get();
        UNPROJECT_OUT.rewind();
        // The modelview was shifted by -slotOrigin in setupCamera, so the unprojected
        // point is in shifted space. Add slotOrigin back to recover dummy-world coords.
        hx += slotOrigin.getX();
        hy += slotOrigin.getY();
        hz += slotOrigin.getZ();
        try {
            Vec3 eye = new Vec3(view.eyeX(), view.eyeY(), view.eyeZ());
            Vec3 dir = new Vec3(hx, hy, hz).subtract(eye).normalize();
            Vec3 end = eye.add(dir.scale(200.0));
            for (int attempt = 0; attempt < 16; attempt++) {
                var ctx = new net.minecraft.world.level.ClipContext(eye, end,
                        net.minecraft.world.level.ClipContext.Block.OUTLINE,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, cameraEntity);
                BlockHitResult res = world.clip(ctx);
                if (res == null || res.getType() == HitResult.Type.MISS) return null;
                BlockPos pos = res.getBlockPos();
                if (targetVisible.contains(pos) || baseplatePositions.contains(pos)) return res;
                eye = res.getLocation().add(dir.scale(0.02));
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ── Shader / blend helpers ────────────────────────────────────────────────

    private static void bindShaderSamplers(ShaderInstance s) {
        for (int j = 0; j < 12; j++) s.setSampler("Sampler" + j, RenderSystem.getShaderTexture(j));
    }

    private static void setShaderUniforms(ShaderInstance s) {
        if (s.MODEL_VIEW_MATRIX != null) s.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
        if (s.PROJECTION_MATRIX != null) s.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        if (s.COLOR_MODULATOR   != null) s.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        if (s.FOG_START         != null) s.FOG_START.set(RenderSystem.getShaderFogStart());
        if (s.FOG_END           != null) s.FOG_END.set(RenderSystem.getShaderFogEnd());
        if (s.FOG_COLOR         != null) s.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        if (s.FOG_SHAPE         != null) s.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        if (s.TEXTURE_MATRIX    != null) s.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        if (s.GAME_TIME         != null) s.GAME_TIME.set(RenderSystem.getShaderGameTime());
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
        if (bakeFuture != null) { bakeFuture.cancel(true); bakeFuture = null; }
        for (int i = 0; i < LAYER_COUNT; i++) {
            if (front[i]   != null && !front[i].isInvalid())   front[i].close();
            if (back[i]    != null && !back[i].isInvalid())    back[i].close();
            if (overlay[i] != null && !overlay[i].isInvalid()) overlay[i].close();
        }
        GL15.glDeleteBuffers(frontIdVbo);
        GL15.glDeleteBuffers(backIdVbo);
        GL15.glDeleteBuffers(overlayIdVbo);
        if (visibilityBuffer != null) { visibilityBuffer.close(); visibilityBuffer = null; }
        PhantasiaShaders.invalidate();
    }
}