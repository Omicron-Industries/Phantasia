package net.phoenixvine.phantasia.client.screens.editors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.client.camera.CameraView;
import net.phoenixvine.phantasia.client.camera.PhantasiaCamera;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.client.render.PhantasiaWorldRenderer;
import net.phoenixvine.phantasia.client.screens.PhantasiaSceneScreen;
import net.phoenixvine.phantasia.compat.custom.PhantasiaCustomLayout;
import net.phoenixvine.phantasia.compat.custom.PhantasiaCustomMultiblockDefinition;
import net.phoenixvine.phantasia.compat.custom.PhantasiaCustomProvider;

import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

@OnlyIn(Dist.CLIENT)
public class PhantasiaCustomEditorScreen extends Screen {

    private static final int PANEL_W = 160;
    private static final int CELL_SIZE = 16;
    private static final int GRID_MAX = 9;

    private final PhantasiaCustomProvider provider;
    private PhantasiaCustomLayout layout;
    private PhantasiaCustomMultiblockDefinition definition;

    private int editLayer = 0;
    private int gridSizeX = 3;
    private int gridSizeZ = 3;

    private PhantasiaTrackedDummyWorld dummyWorld;
    private PhantasiaWorldRenderer renderer;
    private PhantasiaCamera camera;
    private boolean isPanning = false;

    private EditBox nameField;
    private EditBox iconField;

    private final Map<BlockPos, ItemStack> gridItemCache = new HashMap<>();

    private boolean dirty = false;

    public PhantasiaCustomEditorScreen(PhantasiaCustomProvider provider, PhantasiaCustomLayout existing) {
        super(Component.literal("Multiblock Editor"));
        this.provider = provider;
        this.layout = existing != null ? existing : defaultLayout();
        rebuildDefinition();
    }

    private PhantasiaCustomLayout defaultLayout() {
        PhantasiaCustomLayout l = new PhantasiaCustomLayout();
        l.id = "phantasia:new_scene";
        l.displayName = "New Scene";
        return l;
    }

    @Override
    protected void init() {
        int panelX = this.width - PANEL_W - 4;

        nameField = new EditBox(font, panelX, 24, PANEL_W - 4, 16,
                Component.literal("Name"));
        nameField.setMaxLength(64);
        nameField.setValue(layout.displayName != null ? layout.displayName : "");
        nameField.setResponder(val -> {
            layout.displayName = val;
            dirty = true;
        });
        addRenderableWidget(nameField);

        iconField = new EditBox(font, panelX, 52, PANEL_W - 4, 16,
                Component.literal("Icon block"));
        iconField.setMaxLength(128);
        iconField.setValue(layout.iconBlock != null ? layout.iconBlock : "");
        iconField.setResponder(val -> {
            layout.iconBlock = val.isBlank() ? null : val;
            dirty = true;
        });
        addRenderableWidget(iconField);

        initPreview();
    }

    private void initPreview() {
        if (dummyWorld == null) dummyWorld = new PhantasiaTrackedDummyWorld();
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        renderer = new PhantasiaWorldRenderer(dummyWorld);
        camera = new PhantasiaCamera(20f, 5f, 40f, 0f, 0f, 0f);
        rebuildPreview();
    }

    private void rebuildDefinition() {
        definition = new PhantasiaCustomMultiblockDefinition(layout);
    }

    private void rebuildPreview() {
        if (dummyWorld == null || renderer == null) return;
        dummyWorld.clear();
        gridItemCache.clear();

        Set<BlockPos> visible = new HashSet<>();
        for (var e : layout.blocks.entrySet()) {
            BlockPos local = e.getKey();
            BlockState state = PhantasiaCustomLayout.parseBlockState(e.getValue());
            if (state.isAir()) continue;
            dummyWorld.setBlock(local, state, 0);
            visible.add(local);

            ItemStack icon = new ItemStack(state.getBlock());
            if (!icon.isEmpty()) gridItemCache.put(local, icon);
        }

        renderer.setPatternBlocks(visible);
        renderer.setVisible(visible);
        renderer.requestBake();

        if (camera != null && !visible.isEmpty()) {
            int maxDim = Math.max(layout.maxX() + 1, Math.max(layout.maxY() + 1, layout.maxZ() + 1));
            float zoom = Math.max(20f, maxDim * 3.5f);
            camera.setPosition(camera.getYaw(), camera.getPitch(), zoom);

            camera.setTarget((layout.maxX() + 1) / 2f,
                    (layout.maxY() + 1) / 2f,
                    (layout.maxZ() + 1) / 2f);
        }

        definition.getShape().invalidate();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g);

        int panelX = this.width - PANEL_W - 4;
        int previewW = panelX - 4;

        g.fill(0, 0, previewW, this.height, 0xFF0A0A12);
        renderPreview(g, previewW, this.height, partialTick);

        g.fill(panelX - 2, 0, this.width, this.height, 0xCC0D0D18);
        g.fill(panelX - 2, 0, panelX - 1, this.height, C_ACCENT());

        g.drawString(font, "§bMultiblock Editor", panelX, 8, C_HILIGHT(), false);

        g.drawString(font, "Name", panelX, 15, C_DIM(), false);
        g.drawString(font, "Icon block", panelX, 43, C_DIM(), false);

        int gridTop = 80;
        g.drawString(font, "§7Layer Y=" + editLayer, panelX, gridTop - 12, C_DIM(), false);
        renderGrid(g, panelX, gridTop, mx, my);

        int ctrlY = gridTop + CELL_SIZE * Math.min(gridSizeZ, GRID_MAX) + 8;
        g.drawString(font, "X:" + gridSizeX + "  Z:" + gridSizeZ, panelX, ctrlY, C_DIM(), false);
        drawSmallButton(g, panelX + 60, ctrlY - 1, "-X", mx, my);
        drawSmallButton(g, panelX + 76, ctrlY - 1, "+X", mx, my);
        drawSmallButton(g, panelX + 96, ctrlY - 1, "-Z", mx, my);
        drawSmallButton(g, panelX + 112, ctrlY - 1, "+Z", mx, my);

        int layerY = ctrlY + 14;
        g.drawString(font, "Layer", panelX, layerY, C_DIM(), false);
        drawSmallButton(g, panelX + 40, layerY - 1, "▼", mx, my);
        drawSmallButton(g, panelX + 56, layerY - 1, "▲", mx, my);

        ItemStack held = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getMainHandItem() :
                ItemStack.EMPTY;
        int heldY = layerY + 18;
        g.drawString(font, "Held:", panelX, heldY, C_DIM(), false);
        if (!held.isEmpty()) {
            g.renderItem(held, panelX + 32, heldY - 4);
            g.drawString(font, held.getHoverName().getString(), panelX + 52, heldY, C_TEXT(), false);
        } else {
            g.drawString(font, "§8(empty)", panelX + 32, heldY, C_DIM(), false);
        }
        g.drawString(font, "§8L-click: place  R-click: erase", panelX, heldY + 12, C_DIM(), false);

        int btnY = this.height - 28;
        drawButton(g, panelX, btnY, PANEL_W - 4, 12, "§aSave", mx, my);
        drawButton(g, panelX, btnY + 15, PANEL_W - 4, 12, "§cClose", mx, my);

        super.render(g, mx, my, partialTick);
    }

    private void renderPreview(GuiGraphics g, int w, int h, float pt) {
        if (renderer == null || camera == null) return;
        try {
            CameraView view = camera.getView(pt);
            renderer.render(view, 0, 0, w, h);
        } catch (Exception ignored) {}
    }

    private void renderGrid(GuiGraphics g, int ox, int oy, int mx, int my) {
        for (int z = 0; z < Math.min(gridSizeZ, GRID_MAX); z++) {
            for (int x = 0; x < Math.min(gridSizeX, GRID_MAX); x++) {
                int cx = ox + x * CELL_SIZE;
                int cy = oy + z * CELL_SIZE;
                BlockPos pos = new BlockPos(x, editLayer, z);
                String blockStr = layout.blocks.get(pos);
                boolean filled = blockStr != null;
                boolean hovered = mx >= cx && mx < cx + CELL_SIZE && my >= cy && my < cy + CELL_SIZE;

                g.fill(cx, cy, cx + CELL_SIZE, cy + CELL_SIZE, hovered ? 0xFF1A2A3A : 0xFF0D141C);

                int borderCol = hovered ? C_ACCENT() : 0xFF203040;
                g.fill(cx, cy, cx + CELL_SIZE, cy + 1, borderCol);
                g.fill(cx, cy + CELL_SIZE - 1, cx + CELL_SIZE, cy + CELL_SIZE, borderCol);
                g.fill(cx, cy, cx + 1, cy + CELL_SIZE, borderCol);
                g.fill(cx + CELL_SIZE - 1, cy, cx + CELL_SIZE, cy + CELL_SIZE, borderCol);

                if (filled) {
                    ItemStack stack = gridItemCache.get(pos);
                    if (stack != null && !stack.isEmpty()) g.renderItem(stack, cx, cy);
                }
            }
        }
    }

    private void drawSmallButton(GuiGraphics g, int x, int y, String label, int mx, int my) {
        boolean hov = mx >= x && mx < x + 14 && my >= y && my < y + 10;
        g.fill(x, y, x + 14, y + 10, hov ? 0xFF1E3A5F : 0xFF0F1F2F);
        g.drawString(font, label, x + 2, y + 1, hov ? C_ACCENT() : C_DIM(), false);
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, String label, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, hov ? 0xFF1E3A5F : 0xFF0F1F2F);
        g.fill(x, y + h - 1, x + w, y + h, hov ? C_ACCENT() : 0xFF203040);
        g.drawCenteredString(font, label, x + w / 2, y + 2, C_TEXT());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;

        int panelX = this.width - PANEL_W - 4;

        int gridTop = 80;
        int gridW = Math.min(gridSizeX, GRID_MAX) * CELL_SIZE;
        int gridH = Math.min(gridSizeZ, GRID_MAX) * CELL_SIZE;
        if (mx >= panelX && mx < panelX + gridW && my >= gridTop && my < gridTop + gridH) {
            int gx = (int) (mx - panelX) / CELL_SIZE;
            int gz = (int) (my - gridTop) / CELL_SIZE;
            BlockPos pos = new BlockPos(gx, editLayer, gz);
            if (button == 0) {

                ItemStack held = Minecraft.getInstance().player != null ?
                        Minecraft.getInstance().player.getMainHandItem() : ItemStack.EMPTY;
                if (!held.isEmpty() && held.getItem() instanceof BlockItem bi) {
                    Block block = bi.getBlock();
                    if (block != Blocks.AIR) {
                        layout.blocks.put(pos, PhantasiaCustomLayout.serializeBlockState(block.defaultBlockState()));
                        updateGridSize(gx, gz);
                        dirty = true;
                        rebuildPreview();
                    }
                }
            } else if (button == 1) {

                layout.blocks.remove(pos);
                dirty = true;
                rebuildPreview();
            }
            return true;
        }

        int ctrlY = gridTop + CELL_SIZE * Math.min(gridSizeZ, GRID_MAX) + 8;
        int layerY = ctrlY + 14;
        int btnY = this.height - 28;

        if (hitSmall(mx, my, panelX + 60, ctrlY - 1)) {
            gridSizeX = Math.max(1, gridSizeX - 1);
            return true;
        }
        if (hitSmall(mx, my, panelX + 76, ctrlY - 1)) {
            gridSizeX = Math.min(GRID_MAX, gridSizeX + 1);
            return true;
        }
        if (hitSmall(mx, my, panelX + 96, ctrlY - 1)) {
            gridSizeZ = Math.max(1, gridSizeZ - 1);
            return true;
        }
        if (hitSmall(mx, my, panelX + 112, ctrlY - 1)) {
            gridSizeZ = Math.min(GRID_MAX, gridSizeZ + 1);
            return true;
        }

        if (hitSmall(mx, my, panelX + 40, layerY - 1)) {
            editLayer = Math.max(0, editLayer - 1);
            return true;
        }
        if (hitSmall(mx, my, panelX + 56, layerY - 1)) {
            editLayer++;
            return true;
        }

        if (mx >= panelX && mx < panelX + PANEL_W - 4 && my >= btnY && my < btnY + 12) {
            save();
            return true;
        }

        if (mx >= panelX && mx < panelX + PANEL_W - 4 && my >= btnY + 15 && my < btnY + 27) {
            onClose();
            return true;
        }

        if (mx < panelX - 4) {
            isPanning = button == 2 || (button == 0 && hasShiftDown());
            return true;
        }

        return false;
    }

    private boolean hitSmall(double mx, double my, int x, int y) {
        return mx >= x && mx < x + 14 && my >= y && my < y + 10;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (camera != null) {
            if (isPanning) {

                Vector3f right = new Vector3f();
                Vector3f up = new Vector3f();
                camera.getRightAndUp(right, up);
                float scale = camera.getZoom() * PhantasiaSceneScreen.CAM_PAN_ZOOM_SCALE;
                right.mul((float) (-dx) * scale);
                up.mul((float) (dy) * scale);
                camera.pan(right.x + up.x, right.y + up.y, right.z + up.z);
            } else if (button == 0 || button == 1) {
                camera.orbit((float) (dx * 0.5f), (float) (dy * 0.5f));
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int panelX = this.width - PANEL_W - 4;
        if (mx < panelX && camera != null) {
            camera.zoom(delta > 0 ? 0.9f : 1.1f, 2f, 300f);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        isPanning = false;
        return super.mouseReleased(mx, my, button);
    }

    private void updateGridSize(int gx, int gz) {
        if (gx >= gridSizeX) gridSizeX = Math.min(gx + 1, GRID_MAX);
        if (gz >= gridSizeZ) gridSizeZ = Math.min(gz + 1, GRID_MAX);
    }

    private void save() {
        if (nameField != null && !nameField.getValue().isBlank()) {
            layout.displayName = nameField.getValue();
        }
        String safePath = layout.displayName.toLowerCase()
                .replaceAll("[^a-z0-9_]", "_").replaceAll("_+", "_");
        layout.id = "phantasia:" + safePath;
        if (iconField != null && !iconField.getValue().isBlank()) {
            layout.iconBlock = iconField.getValue();
        }
        provider.save(layout);

        rebuildDefinition();
        net.phoenixvine.phantasia.client.screens.PhantasiaSceneSelectionScreen.PHANTASIA_SCENES.removeIf(
                d -> d.getId().equals(definition.getId()));
        net.phoenixvine.phantasia.client.screens.PhantasiaSceneSelectionScreen.PHANTASIA_SCENES.add(definition);

        dirty = false;
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(
                    Component.literal("§a[Phantasia] Saved '" + layout.displayName +
                            "' — it will appear in the scene list as §bphantasia:" + safePath +
                            "§a and can be scripted like any other multiblock."));
        }
    }

    @Override
    public void onClose() {
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        if (dummyWorld != null) {
            dummyWorld.clear();
            dummyWorld = null;
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
