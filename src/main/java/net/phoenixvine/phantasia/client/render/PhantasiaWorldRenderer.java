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
 */
public final class PhantasiaWorldRenderer {

    // ── GL scratch buffers (static — one per class, not per instance) ─────────

    private static final FloatBuffer SCRATCH_MV = direct(64).asFloatBuffer();
    private static final FloatBuffer SCRATCH_PROJ = direct(64).asFloatBuffer();
    private static final IntBuffer SCRATCH_VP = direct(16 * 4).asIntBuffer();
    private static final FloatBuffer PIXEL_DEPTH = direct(4).asFloatBuffer();
    private static final FloatBuffer UNPROJECT_OUT = direct(12).asFloatBuffer();

    /** Only sample depth when the mouse actually moves. */
    private int lastPickMouseX = Integer.MIN_VALUE, lastPickMouseY = Integer.MIN_VALUE;

    /** Cached result from the last depth sample. */
    @Nullable
    private BlockHitResult cachedPickResult = null;

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

    // ── Layers ────────────────────────────────────────────────────────────────

    private final List<RenderType> LAYERS = RenderType.chunkBufferLayers();
    private final int LAYER_COUNT = LAYERS.size();

    // ── Double-buffered main geometry VBOs ────────────────────────────────────

    private final VertexBuffer[] front;
    private final VertexBuffer[] back;
    private volatile boolean backReady = false;

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
        this.lastPickMouseX = Integer.MIN_VALUE;
        this.lastPickMouseY = Integer.MIN_VALUE;

        Set<BlockPos> old = targetVisible;
        targetVisible = Set.copyOf(newVisible);

        Set<BlockPos> eligible = new HashSet<>();
        for (BlockPos pos : targetVisible) {
            BlockState state = world.getBlockState(pos);
            if (state.isRandomlyTicking() || state.getBlock().isRandomlyTicking(state))
                eligible.add(pos);
        }
        animateTickEligible = eligible.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(eligible);

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

        long t4 = System.nanoTime();
        if (guiMouseX != lastPickMouseX || guiMouseY != lastPickMouseY) {
            lastPickMouseX = guiMouseX;
            lastPickMouseY = guiMouseY;
            cachedPickResult = doDepthSampleRead(view, glX, glY, glW, glH, scale, windowH);
        }
        lastHitResult = cachedPickResult;
        totalRayTraceTimeNs += (System.nanoTime() - t4);

        resetCamera();

        long frameDurationNs = System.nanoTime() - renderStart;
        totalRenderTimeNs += frameDurationNs;
        maxRenderTimeNs = Math.max(maxRenderTimeNs, frameDurationNs);
        profiledFramesCount++;

        if (System.nanoTime() - profileWindowStart >= PROFILE_WINDOW_NS) {
            dumpProfilingData();
        }
    }

    /**
     * Samples the main pass OpenGL depth buffer at the cursor and unprojects the coordinate
     * back into world space. Uses Minecraft's exact voxel clip trace for pixel-perfect interactions.
     */
    @Nullable
    private BlockHitResult doDepthSampleRead(CameraView view, int glX, int glY, int glW, int glH, double guiScale, int windowH) {
        int mouseGlX = (int)(guiMouseX * guiScale);
        int mouseGlY = (int)(windowH - guiMouseY * guiScale);

        if (mouseGlX < glX || mouseGlX >= glX + glW || mouseGlY < glY || mouseGlY >= glY + glH) {
            return null;
        }

        PIXEL_DEPTH.clear();
        org.lwjgl.opengl.GL11.glReadPixels(
                mouseGlX, mouseGlY, 1, 1,
                org.lwjgl.opengl.GL11.GL_DEPTH_COMPONENT,
                org.lwjgl.opengl.GL11.GL_FLOAT,
                PIXEL_DEPTH);
        float depth = PIXEL_DEPTH.get(0);

        if (depth >= 1.0f) return null;

        for (int i = 0; i < 16; i++) SCRATCH_MV.put(i, snapMV[i]);
        for (int i = 0; i < 16; i++) SCRATCH_PROJ.put(i, snapProj[i]);
        for (int i = 0; i < 4; i++) SCRATCH_VP.put(i, snapVP[i]);
        SCRATCH_MV.rewind(); SCRATCH_PROJ.rewind(); SCRATCH_VP.rewind();
        UNPROJECT_OUT.rewind();

        Project.gluUnProject(mouseGlX, mouseGlY, depth, SCRATCH_MV, SCRATCH_PROJ, SCRATCH_VP, UNPROJECT_OUT);
        float hx = UNPROJECT_OUT.get(0);
        float hy = UNPROJECT_OUT.get(1);
        float hz = UNPROJECT_OUT.get(2);

        // Calculate actual world coordinates based on the slot-relative offset
        double ox = this.slotOrigin != null ? this.slotOrigin.getX() : 0.0;
        double oy = this.slotOrigin != null ? this.slotOrigin.getY() : 0.0;
        double oz = this.slotOrigin != null ? this.slotOrigin.getZ() : 0.0;

        double worldX = hx + ox;
        double worldY = hy + oy;
        double worldZ = hz + oz;

        // Origin at camera eye
        Vec3 startVec = new Vec3(view.eyeX(), view.eyeY(), view.eyeZ());
        // Destination at unprojected depth buffer location
        Vec3 endVec = new Vec3(worldX, worldY, worldZ);

        // Normalize dir and extend slightly to ensure we clip into the block volume bounding box
        Vec3 dir = endVec.subtract(startVec).normalize();
        Vec3 traceEnd = endVec.add(dir.scale(0.005D));

        long startTrace = System.nanoTime();

        // Let Minecraft calculate the exact geometric face/block using its native voxel shape clip system
        net.minecraft.world.level.ClipContext context = new net.minecraft.world.level.ClipContext(
                startVec,
                traceEnd,
                net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                this.cameraEntity
        );

        BlockHitResult result = this.world.clip(context);

        totalClipContextTimeNs += (System.nanoTime() - startTrace);
        maxRayIterationsTracked++;

        return result.getType() == HitResult.Type.MISS ? null : result;
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
        overlayReady = false;
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

    private void schedulePartialBake(Set<BlockPos> targets) {
        Set<BlockPos> valid = new HashSet<>(targets);
        valid.removeIf(pos -> !bakedAll.contains(pos) && !baseplatePositions.contains(pos));
        if (valid.isEmpty()) return;

        Set<BlockPos> patternVisible = new HashSet<>(targetVisible);
        patternVisible.retainAll(bakedAll);

        Set<BlockPos> fullVisibleSnapshot = new HashSet<>(patternVisible);
        fullVisibleSnapshot.addAll(baseplatePositions);

        pendingUploads.set(LAYER_COUNT);
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

            double renderX = pos.getX() - this.slotOrigin.getX();
            double renderY = pos.getY() - this.slotOrigin.getY();
            double renderZ = pos.getZ() - this.slotOrigin.getZ();
            poseStack.translate(renderX, renderY, renderZ);

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

    // ── Fixed Dynamic Renderers Draw (e.g. Fusion Plasma Rings) ──────────────
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void drawDynamicRenderers(PoseStack poseStack, MultiBufferSource.BufferSource buffers, float partial,
                                      float camX, float camY, float camZ) {
        Vec3 cameraPos = new Vec3(camX, camY, camZ);
        for (BlockPos pos : frontTileEntities) {
            BlockEntity be = world.getBlockEntity(pos);
            if (!(be instanceof MetaMachineBlockEntity mmbe)) continue;
            MetaMachine machine = mmbe.getMetaMachine();
            if (machine == null) continue;

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

        double renderX = pos.getX() - this.slotOrigin.getX();
        double renderY = pos.getY() - this.slotOrigin.getY();
        double renderZ = pos.getZ() - this.slotOrigin.getZ();
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
        float aspect = (float) glW / glH;
        RenderSystem.setProjectionMatrix(
                new Matrix4f().setPerspective((float) Math.toRadians(FOV), aspect, NEAR, FAR),
                VertexSorting.byDistance(new Vector3f(view.eyeX(), view.eyeY(), view.eyeZ())));
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();

        double ox = this.slotOrigin != null ? this.slotOrigin.getX() : 0.0;
        double oy = this.slotOrigin != null ? this.slotOrigin.getY() : 0.0;
        double oz = this.slotOrigin != null ? this.slotOrigin.getZ() : 0.0;
        Project.gluLookAt(mv,
                (float)(view.eyeX()    - ox), (float)(view.eyeY()    - oy), (float)(view.eyeZ()    - oz),
                (float)(view.lookAtX() - ox), (float)(view.lookAtY() - oy), (float)(view.lookAtZ() - oz),
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
        float yaw   = (float) Math.toDegrees(Math.atan2(-dir.x(), dir.z()));
        float hd    = (float) Math.sqrt(dir.x() * dir.x() + dir.z() * dir.z());
        float pitch = (float) Math.toDegrees(Math.atan2(-dir.y(), hd));

        double relX = view.eyeX() - ox;
        double relY = view.eyeY() - oy;
        double relZ = view.eyeZ() - oz;
        cameraEntity.setPos(relX, relY, relZ);
        cameraEntity.setYRot(yaw);
        cameraEntity.setXRot(pitch);
        cameraEntity.xo     = relX;
        cameraEntity.yo     = relY;
        cameraEntity.zo     = relZ;
        cameraEntity.yRotO  = yaw;
        cameraEntity.xRotO  = pitch;
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
    }
}