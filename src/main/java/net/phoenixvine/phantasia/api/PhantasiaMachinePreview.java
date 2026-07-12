package net.phoenixvine.phantasia.api;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.client.camera.CameraView;
import net.phoenixvine.phantasia.client.camera.PhantasiaCamera;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.client.render.PhantasiaWorldRenderer;
import net.phoenixvine.phantasia.common.data.pattern.PhantasiaLoadedPattern;
import net.phoenixvine.phantasia.common.data.pattern.PhantasiaPatternLoader;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScript;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScripts;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

/**
 * Self-contained 3-D multiblock preview widget for embedding in any screen.
 *
 * <p>
 * Owns its own {@link PhantasiaTrackedDummyWorld} and
 * {@link PhantasiaWorldRenderer} so it never conflicts with the main
 * {@code SHARED_LEVEL} used by {@link net.phoenixvine.phantasia.client.screens.PhantasiaSceneScreen}.
 *
 * <p>
 * Lifecycle:
 * <ol>
 * <li>Obtain via {@link PhantasiaAPI#createPreview(String)}.
 * <li>Call {@link #tick()} each game tick (or {@link #tickAutoSpin(float)} each render frame)
 * to drive the camera spin animation.
 * <li>Call {@link #render(GuiGraphics, int, int, int, int, float)} in your render method.
 * <li>Call {@link #mouseClicked(double, double, int, int, int, int, Screen)} in mouseClicked
 * to open the full Phantasia screen when the widget is clicked.
 * <li>Call {@link #close()} when your screen closes to release GL resources.
 * </ol>
 */
@OnlyIn(Dist.CLIENT)
public final class PhantasiaMachinePreview {

    /**
     * -- GETTER --
     * The resolved machine definition backing this preview.
     */
    @Getter
    private final IPhantasiaMultiblockDefinition definition;

    // ── Per-widget rendering resources ────────────────────────────────────────

    private final PhantasiaTrackedDummyWorld world;
    private PhantasiaWorldRenderer renderer;
    private PhantasiaLoadedPattern pattern;
    private PhantasiaPatternLoader loader;
    private boolean closed = false;

    // ── Camera ────────────────────────────────────────────────────────────────

    /**
     * -- GETTER --
     * Directly access the camera if you want to set a custom angle or zoom.
     * The auto-spin still applies unless you call
     * .
     */
    @Getter
    private final PhantasiaCamera camera;
    /** Degrees per second auto-spin. Set to 0 to disable. */
    private float autoSpinDegreesPerSecond = 20f;
    private float spinAccum = 0f;

    // ── State ─────────────────────────────────────────────────────────────────

    private boolean ready = false;
    /**
     * -- GETTER --
     * if the pattern failed to load (machine had no shapes, etc.).
     */
    @Getter
    private boolean loadFailed = false;

    // ─────────────────────────────────────────────────────────────────────────
    // Construction
    // ─────────────────────────────────────────────────────────────────────────

    PhantasiaMachinePreview(IPhantasiaMultiblockDefinition definition) {
        this.definition = definition;
        this.world = new PhantasiaTrackedDummyWorld();
        this.camera = new PhantasiaCamera(-135f, -25f, 40f, 0f, 5f, 0f);
        startLoad();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /** {@code true} once the pattern has loaded and the first bake is ready. */
    public boolean isReady() {
        return ready && renderer != null && renderer.isSceneReady();
    }

    /**
     * Degrees per second the camera auto-spins. Default 20. Set to 0 to disable.
     */
    public void setAutoSpin(float degreesPerSecond) {
        this.autoSpinDegreesPerSecond = degreesPerSecond;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tick / animation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Call once per game tick (20 Hz) to drive the camera smoothing.
     * If your screen has a {@code tick()} method, put this there.
     */
    public void tick() {
        camera.tick();
        if (autoSpinDegreesPerSecond != 0) {
            camera.orbit(autoSpinDegreesPerSecond / 20f, 0);
        }
    }

    /**
     * Alternatively, call once per render frame with the render partial tick
     * to drive the spin at a framerate-independent rate.
     * Use this if you don't have access to a screen tick.
     */
    public void tickAutoSpin(float partialTick) {
        spinAccum += partialTick;
        if (spinAccum >= 1f) {
            int ticks = (int) spinAccum;
            spinAccum -= ticks;
            for (int i = 0; i < ticks; i++) {
                camera.tick();
                if (autoSpinDegreesPerSecond != 0) {
                    camera.orbit(autoSpinDegreesPerSecond / 20f, 0);
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rendering
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Renders the 3-D preview into the given screen-space rectangle.
     *
     * <p>
     * Shows a loading shimmer while the pattern is still baking, and a dimmed
     * placeholder if loading failed.
     *
     * @param g           GuiGraphics from the current render call
     * @param x           left edge of the widget in screen pixels
     * @param y           top edge of the widget in screen pixels
     * @param w           widget width in pixels
     * @param h           widget height in pixels
     * @param partialTick render partial tick for smooth camera interpolation
     */
    public void render(GuiGraphics g, int x, int y, int w, int h, float partialTick) {
        if (closed) return;

        // Background
        g.fill(x, y, x + w, y + h, 0xBB0A0F14);
        g.fill(x, y, x + w, y + 1, C_ACCENT());
        g.fill(x, y + h - 1, x + w, y + h, C_ACCENT());
        g.fill(x, y, x + 1, y + h, C_ACCENT());
        g.fill(x + w - 1, y, x + w, y + h, C_ACCENT());

        if (loadFailed) {
            var font = Minecraft.getInstance().font;
            g.drawCenteredString(font, "Preview unavailable", x + w / 2, y + h / 2 - 4, C_DIM());
            return;
        }

        if (renderer == null) {
            // Pattern hasn't loaded at all yet (still async, or sync load hasn't run this
            // constructor call yet) - nothing to drive.
            renderLoadingSpinner(g, x, y, w, h);
            return;
        }

        // Always drive the renderer once it exists, even before isReady() - isSceneReady()
        // reports whether the *previous* bake finished, but the bake itself only actually runs
        // inside render() (see requestBake()'s fullBakeNeeded flag, processed at the top of
        // PhantasiaWorldRenderer.render()). Gating this call behind isReady() meant the bake this
        // widget is waiting on would never run in the first place - a permanent "Loading..."
        // regardless of how fast the pattern itself loaded.
        CameraView view = camera.getView(partialTick);
        renderer.render(view, x, y, w, h);

        if (!isReady()) {
            renderLoadingSpinner(g, x, y, w, h);
            return;
        }

        // Subtle "click to view" hint at the bottom
        var font = Minecraft.getInstance().font;
        String hint = "Click to view in Phantasia";
        int hintW = font.width(hint);
        if (hintW + 8 <= w) {
            g.fill(x + 1, y + h - 13, x + w - 1, y + h - 1, 0x88000000);
            g.drawCenteredString(font, hint, x + w / 2, y + h - 11, C_DIM());
        }
    }

    /**
     * Renders the preview without the "click to view" hint overlay.
     * Useful if you want to draw your own bottom label.
     */
    public void renderRaw(GuiGraphics g, int x, int y, int w, int h, float partialTick) {
        if (closed) return;
        g.fill(x, y, x + w, y + h, 0xBB0A0F14);
        g.fill(x, y, x + w, y + 1, C_ACCENT());
        g.fill(x, y + h - 1, x + w, y + h, C_ACCENT());
        g.fill(x, y, x + 1, y + h, C_ACCENT());
        g.fill(x + w - 1, y, x + w, y + h, C_ACCENT());

        if (loadFailed) return;
        if (renderer == null) {
            renderLoadingSpinner(g, x, y, w, h);
            return;
        }
        CameraView view = camera.getView(partialTick);
        renderer.render(view, x, y, w, h);
        if (!isReady()) {
            renderLoadingSpinner(g, x, y, w, h);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mouse input
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Call from your screen's {@code mouseClicked}. Returns {@code true} and opens
     * the full Phantasia viewer if the cursor is inside the widget area.
     *
     * @param mx           cursor X
     * @param my           cursor Y
     * @param x            widget left edge
     * @param y            widget top edge
     * @param w            widget width
     * @param h            widget height
     * @param parentScreen screen to return to when Phantasia is closed
     */
    public boolean mouseClicked(double mx, double my, int x, int y, int w, int h, Screen parentScreen) {
        if (closed || !isOver(mx, my, x, y, w, h)) return false;
        PhantasiaAPI.openForDefinition(definition, parentScreen);
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Releases the GL resources used by this widget's renderer.
     * Call this when the owning screen closes.
     */
    public void close() {
        if (closed) return;
        closed = true;
        if (loader != null) loader.cancel();
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal
    // ─────────────────────────────────────────────────────────────────────────

    private void startLoad() {
        List<IPhantasiaMultiblockShape> shapes = definition.getMatchingShapes();
        if (shapes == null || shapes.isEmpty()) {
            shapes = definition.getAllShapes();
        }
        if (shapes == null || shapes.isEmpty()) {
            loadFailed = true;
            return;
        }

        PhantasiaScript script = null;
        try {
            script = PhantasiaScripts.get(definition);
        } catch (Exception ignored) {}

        IPhantasiaMultiblockShape shape = shapes.get(0);

        // PhantasiaSceneScreen picks sync-vs-async by block count (see its own loadPattern());
        // this widget used to *always* go through the async PhantasiaPatternLoader regardless of
        // size, which meant every embedded preview - no matter how small the structure - resolved
        // block states off the render thread. That's fine for most blocks, but at least some
        // integrations (observed with GTCEu multiblocks) apparently aren't safe to resolve there
        // and the load simply never completed, even for a trivial ~30-block structure that loads
        // instantly through PhantasiaSceneScreen's own sync path. Mirror that same threshold here
        // so small structures - the common case for a quest-icon-sized preview - load
        // synchronously and immediately, and only genuinely large ones pay for the async path.
        int blockCount = countBlocks(shape.getBlocks());
        if (blockCount <= PhantasiaWorldRenderer.STREAMING_THRESHOLD) {
            try {
                PhantasiaLoadedPattern pat = loadPatternSync(shape, script);
                onPatternLoaded(pat);
            } catch (Exception e) {
                loadFailed = true;
            }
            return;
        }

        final PhantasiaScript finalScript = script;
        final List<IPhantasiaMultiblockShape> finalShapes = shapes;

        loader = PhantasiaPatternLoader.start(definition, 0, finalShapes, finalScript, world, pat -> {
            if (closed) return;
            onPatternLoaded(pat);
        });
    }

    private static int countBlocks(PhantasiaBlockInfo[][][] raw) {
        int n = 0;
        for (PhantasiaBlockInfo[][] layer : raw)
            for (PhantasiaBlockInfo[] row : layer)
                for (PhantasiaBlockInfo b : row) {
                    if (b == null) continue;
                    BlockState s = b.getBlockState();
                    if (s == null || s.isAir() ||
                            s.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE)
                        continue;
                    n++;
                }
        return n;
    }

    /**
     * Synchronous load path for small structures, mirroring
     * {@code PhantasiaSceneScreen.loadPatternCold()} - writes blocks directly to this widget's
     * own {@link #world} on the calling thread instead of handing block-state resolution off to
     * the background pattern-loader thread.
     */
    private PhantasiaLoadedPattern loadPatternSync(IPhantasiaMultiblockShape shape, PhantasiaScript script) {
        BlockPos renderOrigin = new BlockPos(8, 50, 8);
        PhantasiaBlockInfo[][][] raw = shape.getBlocks();

        Map<BlockPos, PhantasiaBlockInfo> blockMap = new HashMap<>();
        Map<BlockPos, BlockPos> localToWorld = new HashMap<>();
        Set<BlockPos> baseplatePos = new HashSet<>();
        Set<BlockPos> bePos = new HashSet<>();

        var baseplateState = net.phoenixvine.phantasia.utils.PhantasiaTheme.currentBaseplateBlockState();
        PhantasiaBlockInfo floor = baseplateState != null ? PhantasiaBlockInfo.fromBlockState(baseplateState) : null;
        int sxLen = raw.length;
        int szLen = sxLen > 0 && raw[0].length > 0 ? raw[0][0].length : 0;
        int padX = Math.max(2, sxLen / 2 + 1);
        int padZ = Math.max(2, szLen / 2 + 1);

        if (floor != null) {
            for (int bx = -padX; bx < sxLen + padX; bx++) {
                for (int bz = -padZ; bz < szLen + padZ; bz++) {
                    BlockPos wp = renderOrigin.offset(bx, -1, bz);
                    blockMap.put(wp, floor);
                    baseplatePos.add(wp);
                    world.setBlock(wp, floor.getBlockState(), 3);
                }
            }
        }

        for (int x = 0; x < raw.length; x++) {
            for (int y = 0; y < raw[x].length; y++) {
                for (int z = 0; z < raw[x][y].length; z++) {
                    PhantasiaBlockInfo info = raw[x][y][z];
                    if (info == null) continue;

                    BlockState state = info.getBlockState();
                    if (state == null || state.isAir() ||
                            state.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE)
                        continue;

                    BlockPos lp = new BlockPos(x, y, z);
                    BlockPos wp = renderOrigin.offset(x, y, z);

                    blockMap.put(wp, info);
                    localToWorld.put(lp, wp);

                    world.setBlock(wp, state, 3);

                    if (state.getBlock() instanceof EntityBlock entityBlock) {
                        var be = entityBlock.newBlockEntity(wp, state);
                        if (be != null) {
                            be.setLevel(world);
                            world.setInnerBlockEntity(be);
                            bePos.add(wp);
                        }
                    }
                }
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

        PhantasiaLoadedPattern result = new PhantasiaLoadedPattern(blockMap, localToWorld, baseplatePos, null, bePos,
                renderOrigin, minY, maxY, script);

        Map<BlockPos, PhantasiaBlockInfo> blockMapSnapshot = Map.copyOf(blockMap);
        Map<BlockPos, BlockPos> localToWorldSnapshot = Map.copyOf(localToWorld);
        RenderSystem.recordRenderCall(
                () -> definition.onShapeLoaded(world, renderOrigin, blockMapSnapshot, localToWorldSnapshot));

        return result;
    }

    private void onPatternLoaded(PhantasiaLoadedPattern pat) {
        this.pattern = pat;

        // Write blocks to our private world (mirrors what SceneScreen does)
        for (var entry : pat.blockMap.entrySet()) {
            BlockPos wp = entry.getKey();
            PhantasiaBlockInfo info = entry.getValue();
            if (info == null) continue;
            var state = info.getBlockState();
            if (state != null && !state.isAir()) {
                world.setBlock(wp, state, 2);
                if (state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock eb) {
                    var be = eb.newBlockEntity(wp, state);
                    if (be != null) world.setInnerBlockEntity(be);
                }
            }
        }
        if (pat.postWriteTask != null) {
            RenderSystem.recordRenderCall(pat.postWriteTask::run);
        }

        // Create renderer on the render thread
        renderer = new PhantasiaWorldRenderer(world);

        // Configure camera zoom to fit the structure
        float maxDim = 0;
        for (BlockPos lp : pat.localToWorld.keySet()) {
            maxDim = Math.max(maxDim, Math.max(Math.abs(lp.getX()), Math.abs(lp.getZ())));
        }
        float zoom = Math.max(20f, maxDim * 3.5f);
        float centerY = (pat.minY + pat.maxY) / 2f + 1f;
        camera.setPosition(camera.getYaw(), camera.getPitch(), zoom);
        camera.setTarget(pat.origin.getX() + 4f, pat.origin.getY() + centerY, pat.origin.getZ() + 4f);

        // Show all blocks
        Set<BlockPos> visible = new HashSet<>(pat.baseplatePositions);
        visible.addAll(pat.localToWorld.values());

        renderer.setPatternBlocks(new HashSet<>(pat.blockMap.keySet()));
        renderer.setBaseplatePositions(pat.baseplatePositions);
        renderer.setVisible(visible);
        renderer.requestBake();

        ready = true;
    }

    private void renderLoadingSpinner(GuiGraphics g, int x, int y, int w, int h) {
        var font = Minecraft.getInstance().font;
        long ms = System.currentTimeMillis();
        // Simple rotating dots indicator
        int frame = (int) ((ms / 200) % 4);
        String dots = ".".repeat(frame + 1) + "   ".substring(frame);
        g.drawCenteredString(font, "Loading" + dots, x + w / 2, y + h / 2 - 4, C_DIM());
    }

    private static boolean isOver(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
