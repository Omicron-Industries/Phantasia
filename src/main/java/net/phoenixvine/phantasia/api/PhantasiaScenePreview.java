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
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.common.multiblock.PhantasiaMultiblockRegistry;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

@OnlyIn(Dist.CLIENT)
public final class PhantasiaScenePreview {

    private final PhantasiaSceneData scene;
    private final PhantasiaTrackedDummyWorld world;
    private PhantasiaWorldRenderer renderer;
    private boolean closed = false;

    private final PhantasiaCamera camera;
    private float autoSpinDegreesPerSecond = 20f;
    private float spinAccum = 0f;

    private boolean ready = false;
    private boolean loadFailed = false;

    PhantasiaScenePreview(PhantasiaSceneData scene) {
        this.scene = scene;
        this.world = new PhantasiaTrackedDummyWorld();
        this.camera = new PhantasiaCamera(-135f, -25f, 60f, 0f, 5f, 0f);
        load();
    }

    public boolean isReady() {
        return ready && renderer != null && renderer.isSceneReady();
    }

    public boolean isLoadFailed() {
        return loadFailed;
    }

    public void setAutoSpin(float degreesPerSecond) {
        this.autoSpinDegreesPerSecond = degreesPerSecond;
    }

    public PhantasiaCamera getCamera() {
        return camera;
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

    public boolean mouseClicked(double mx, double my, int x, int y, int w, int h, Screen parentScreen) {
        if (closed || !isOver(mx, my, x, y, w, h)) return false;
        PhantasiaAPI.openScene(scene.id, parentScreen);
        return true;
    }

    public void close() {
        if (closed) return;
        closed = true;
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
    }

    private void load() {
        if (scene.placements.isEmpty()) {
            loadFailed = true;
            return;
        }

        Map<BlockPos, PhantasiaBlockInfo> blockMap = new HashMap<>();
        Set<BlockPos> blockEntityPositions = new HashSet<>();

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (PhantasiaSceneData.PlacementData placement : scene.placements) {
            var defOpt = PhantasiaMultiblockRegistry.resolve(placement.machine);
            if (defOpt.isEmpty()) continue;
            IPhantasiaMultiblockDefinition def = defOpt.get();

            List<IPhantasiaMultiblockShape> shapes = def.getMatchingShapes();
            if (shapes == null || shapes.isEmpty()) shapes = def.getAllShapes();
            if (shapes == null || shapes.isEmpty()) continue;
            IPhantasiaMultiblockShape shape = shapes.get(0);

            BlockPos placementOrigin = new BlockPos(8 + placement.x, 50 + placement.y, 8 + placement.z);
            PhantasiaBlockInfo[][][] raw = shape.getBlocks();

            for (int lx = 0; lx < raw.length; lx++) {
                for (int ly = 0; ly < raw[lx].length; ly++) {
                    for (int lz = 0; lz < raw[lx][ly].length; lz++) {
                        PhantasiaBlockInfo info = raw[lx][ly][lz];
                        if (info == null) continue;

                        BlockState state = info.getBlockState();
                        if (state == null || state.isAir() ||
                                state.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE)
                            continue;

                        BlockPos wp = placementOrigin.offset(lx, ly, lz);
                        blockMap.put(wp, info);
                        world.setBlock(wp, state, 3);

                        if (state.getBlock() instanceof EntityBlock entityBlock) {
                            var be = entityBlock.newBlockEntity(wp, state);
                            if (be != null) {
                                be.setLevel(world);
                                world.setInnerBlockEntity(be);
                                blockEntityPositions.add(wp);
                            }
                        }

                        minX = Math.min(minX, wp.getX());
                        maxX = Math.max(maxX, wp.getX());
                        minY = Math.min(minY, wp.getY());
                        maxY = Math.max(maxY, wp.getY());
                        minZ = Math.min(minZ, wp.getZ());
                        maxZ = Math.max(maxZ, wp.getZ());
                    }
                }
            }
        }

        if (blockMap.isEmpty()) {
            loadFailed = true;
            return;
        }

        renderer = new PhantasiaWorldRenderer(world);
        renderer.setPatternBlocks(blockMap.keySet());
        renderer.setBaseplatePositions(Set.of());
        renderer.setVisible(blockMap.keySet());
        renderer.requestBake();

        float spanX = maxX - minX, spanZ = maxZ - minZ;
        float maxDim = Math.max(spanX, spanZ);
        float zoom = Math.max(30f, maxDim * 2.2f);
        float centerX = (minX + maxX) / 2f + 0.5f;
        float centerY = (minY + maxY) / 2f + 1f;
        float centerZ = (minZ + maxZ) / 2f + 0.5f;
        camera.setPosition(camera.getYaw(), camera.getPitch(), zoom);
        camera.setTarget(centerX, centerY, centerZ);

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
