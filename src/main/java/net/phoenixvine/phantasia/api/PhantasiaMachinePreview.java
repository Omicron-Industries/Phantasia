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

@OnlyIn(Dist.CLIENT)
public final class PhantasiaMachinePreview {

    @Getter
    private final IPhantasiaMultiblockDefinition definition;

    private final PhantasiaTrackedDummyWorld world;
    private PhantasiaWorldRenderer renderer;
    private PhantasiaLoadedPattern pattern;
    private PhantasiaPatternLoader loader;
    private boolean closed = false;

    @Getter
    private final PhantasiaCamera camera;

    private float autoSpinDegreesPerSecond = 20f;
    private float spinAccum = 0f;

    private boolean ready = false;

    @Getter
    private boolean loadFailed = false;

    PhantasiaMachinePreview(IPhantasiaMultiblockDefinition definition) {
        this.definition = definition;
        this.world = new PhantasiaTrackedDummyWorld();
        this.camera = new PhantasiaCamera(-135f, -25f, 40f, 0f, 5f, 0f);
        startLoad();
    }

    public boolean isReady() {
        return ready && renderer != null && renderer.isSceneReady();
    }

    public void setAutoSpin(float degreesPerSecond) {
        this.autoSpinDegreesPerSecond = degreesPerSecond;
    }

    public void tick() {
        camera.tick();
        if (autoSpinDegreesPerSecond != 0) {
            camera.orbit(autoSpinDegreesPerSecond / 20f, 0);
        }
    }

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

    public void render(GuiGraphics g, int x, int y, int w, int h, float partialTick) {
        if (closed) return;

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

            renderLoadingSpinner(g, x, y, w, h);
            return;
        }

        CameraView view = camera.getView(partialTick);
        renderer.render(view, x, y, w, h);

        if (!isReady()) {
            renderLoadingSpinner(g, x, y, w, h);
            return;
        }

        var font = Minecraft.getInstance().font;
        String hint = "Click to view in Phantasia";
        int hintW = font.width(hint);
        if (hintW + 8 <= w) {
            g.fill(x + 1, y + h - 13, x + w - 1, y + h - 1, 0x88000000);
            g.drawCenteredString(font, hint, x + w / 2, y + h - 11, C_DIM());
        }
    }

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

    public boolean mouseClicked(double mx, double my, int x, int y, int w, int h, Screen parentScreen) {
        if (closed || !isOver(mx, my, x, y, w, h)) return false;
        PhantasiaAPI.openForDefinition(definition, parentScreen);
        return true;
    }

    public void close() {
        if (closed) return;
        closed = true;
        if (loader != null) loader.cancel();
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
    }

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

    private PhantasiaLoadedPattern loadPatternSync(IPhantasiaMultiblockShape shape, PhantasiaScript script) {
        BlockPos renderOrigin = new BlockPos(8, 50, 8);
        PhantasiaBlockInfo[][][] raw = shape.getBlocks();

        Map<BlockPos, PhantasiaBlockInfo> blockMap = new HashMap<>();
        Map<BlockPos, BlockPos> localToWorld = new HashMap<>();
        Set<BlockPos> baseplatePos = new HashSet<>();
        Set<BlockPos> bePos = new HashSet<>();

        var baseplateState = net.phoenixvine.phantasia.utils.PhantasiaBaseplateConfig.currentBaseplateBlockState();
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

        renderer = new PhantasiaWorldRenderer(world);

        float maxDim = 0;
        for (BlockPos lp : pat.localToWorld.keySet()) {
            maxDim = Math.max(maxDim, Math.max(Math.abs(lp.getX()), Math.abs(lp.getZ())));
        }
        float zoom = Math.max(20f, maxDim * 3.5f);
        float centerY = (pat.minY + pat.maxY) / 2f + 1f;
        camera.setPosition(camera.getYaw(), camera.getPitch(), zoom);
        camera.setTarget(pat.origin.getX() + 4f, pat.origin.getY() + centerY, pat.origin.getZ() + 4f);

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

        int frame = (int) ((ms / 200) % 4);
        String dots = ".".repeat(frame + 1) + "   ".substring(frame);
        g.drawCenteredString(font, "Loading" + dots, x + w / 2, y + h / 2 - 4, C_DIM());
    }

    private static boolean isOver(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
