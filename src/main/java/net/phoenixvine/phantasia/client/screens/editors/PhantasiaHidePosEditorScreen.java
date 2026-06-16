package net.phoenixvine.phantasia.client.screens.editors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.client.camera.CameraView;
import net.phoenixvine.phantasia.client.camera.PhantasiaCamera;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;

import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

/**
 * Subscreen for editing the "hide positions" list of a script step.
 *
 * Left half: the parent's 3-D renderer showing the multiblock. Hovering a row
 * in the list highlights that position in world-space with a coloured overlay;
 * clicking on a visible block in the viewport adds it to the hide list.
 * Right half: scrollable list of hidden positions with ✕ remove buttons, plus
 * a manual x,y,z add field at the bottom.
 */
@OnlyIn(Dist.CLIENT)
public class PhantasiaHidePosEditorScreen extends Screen {

    private static final int TOP_BAR_H = 22;
    private static final int BOTTOM_H = 36;
    private static final int ROW_H = 20;
    private static final int PANEL_W = 260; // right panel width

    // ── References ────────────────────────────────────────────────────────────
    private final PhantasiaScriptEditorScreen parent;
    private final PhantasiaScriptData data;
    private final int stepIndex;

    // ── Camera (own, so we don't disturb parent camera) ───────────────────────
    private PhantasiaCamera camera;
    private boolean isPanning = false;
    private static final float CAM_ORBIT = 0.4f;
    private static final float CAM_PAN = 0.02f;

    // ── Hover state ───────────────────────────────────────────────────────────
    /** Local XYZ of the row currently hovered in the list, or null. */
    private int[] hoveredListPos = null;
    /** World-space pos hovered in the viewport (for click-to-add), or null. */
    private BlockPos hoveredViewportPos = null;

    // ── Scroll ────────────────────────────────────────────────────────────────
    private int scrollOffset = 0;

    // ── Add-entry input ───────────────────────────────────────────────────────
    private EditBox addBox;
    private String addError = null;

    // ── Button registry ───────────────────────────────────────────────────────
    private record Btn(int x, int y, int w, int h, Runnable action) {

        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Btn> btns = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────────────────
    public PhantasiaHidePosEditorScreen(PhantasiaScriptEditorScreen parent,
                                        PhantasiaScriptData data,
                                        int stepIndex) {
        super(Component.translatable("screen.phantasia.hide_pos_editor.title"));
        this.parent = parent;
        this.data = data;
        this.stepIndex = stepIndex;
    }

    private PhantasiaScriptData.StepData step() {
        List<PhantasiaScriptData.StepData> steps = data.getSteps();
        if (stepIndex >= 0 && stepIndex < steps.size()) return steps.get(stepIndex);
        return new PhantasiaScriptData.StepData();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Init
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();

        // Clone the parent camera so we start at the same view angle
        if (camera == null && parent.camera != null) {
            PhantasiaCamera pc = parent.camera;
            camera = new PhantasiaCamera(pc.getYaw(), pc.getPitch(), pc.getZoom(),
                    pc.getTargetX(), pc.getTargetY(), pc.getTargetZ());
        } else if (camera == null) {
            camera = new PhantasiaCamera(-135f, -30f, 30f, 0f, 5f, 0f);
        }

        // Add-box sits in the bottom strip of the right panel
        int addY = this.height - BOTTOM_H + 10;
        int listX = this.width - PANEL_W + 4;
        addBox = addRenderableWidget(new EditBox(font,
                listX, addY, PANEL_W - 60, 14, Component.empty()));
        addBox.setMaxLength(64);
        addBox.setHint(Component.translatable("screen.phantasia.hide_pos_editor.hint_xyz"));
        addBox.setFilter(s -> s.matches("[\\d\\s,\\-]*"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tick
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (camera != null) camera.tick();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Render
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        btns.clear();
        hoveredListPos = null;
        hoveredViewportPos = null;

        g.fill(0, 0, this.width, this.height, C_BG());

        int viewW = this.width - PANEL_W;
        int viewH = this.height - TOP_BAR_H;

        // ── 3-D Viewport ──────────────────────────────────────────────────────
        if (parent.renderer != null && camera != null) {
            // Pass mouse only when cursor is in the viewport area
            int vmx = (mx < viewW) ? mx : -1;
            int vmy = (my > TOP_BAR_H) ? my : -1;
            parent.renderer.setMousePos(vmx, vmy);
            CameraView view = camera.getView(partial);
            parent.renderer.render(view, 0, TOP_BAR_H, viewW, viewH);

            // Pick hovered viewport block (only if cursor is in viewport)
            if (mx < viewW && my > TOP_BAR_H) {
                BlockHitResult hit = parent.renderer.getLastHitResult();
                if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                    BlockPos wp = hit.getBlockPos();
                    if (parent.renderer.isVisible(wp)) {
                        hoveredViewportPos = wp;
                    }
                }
            }
        }

        renderTopBar(g, mx, my);
        renderRightPanel(g, mx, my);
        renderViewportOverlays(g, mx, my);

        super.render(g, mx, my, partial);
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private void renderTopBar(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, this.width, TOP_BAR_H, C_BAR());
        g.fill(0, TOP_BAR_H - 1, this.width, TOP_BAR_H, C_ACCENT());

        g.drawCenteredString(font,
                "Hide Positions — Step " + (stepIndex + 1) + "  (" + step().hidePositions.size() + " hidden)",
                (this.width - PANEL_W) / 2, (TOP_BAR_H - 8) / 2, C_ACCENT());

        // Done button
        int doneW = font.width(Component.translatable("screen.phantasia.hide_pos_editor.btn_done").getString()) + 12;
        int doneX = this.width - 4 - doneW;
        boolean doneHov = isOver(mx, my, doneX, 3, doneW, TOP_BAR_H - 6);
        g.fill(doneX, 3, doneX + doneW, TOP_BAR_H - 3, doneHov ? C_BTN_HOV() : C_BTN());
        if (doneHov) {
            g.fill(doneX, 3, doneX + doneW, 4, C_ACCENT());
            g.fill(doneX, TOP_BAR_H - 4, doneX + doneW, TOP_BAR_H - 3, C_ACCENT());
        }
        g.drawString(font, Component.translatable("screen.phantasia.hide_pos_editor.btn_done").getString(), doneX + 6,
                (TOP_BAR_H - 8) / 2, doneHov ? C_GREEN() : C_TEXT(), false);
        btns.add(new Btn(doneX, 3, doneW, TOP_BAR_H - 6, this::close));

        // Clear all button
        int clrW = font.width(Component.translatable("screen.phantasia.hide_pos_editor.btn_clear").getString()) + 12;
        int clrX = doneX - 4 - clrW;
        boolean clrHov = isOver(mx, my, clrX, 3, clrW, TOP_BAR_H - 6);
        g.fill(clrX, 3, clrX + clrW, TOP_BAR_H - 3, clrHov ? C_BTN_HOV() : C_BTN());
        g.drawString(font, Component.translatable("screen.phantasia.hide_pos_editor.btn_clear").getString(), clrX + 6,
                (TOP_BAR_H - 8) / 2, clrHov ? C_RED() : C_TEXT(), false);
        btns.add(new Btn(clrX, 3, clrW, TOP_BAR_H - 6, () -> {
            parent.checkpoint();
            step().hidePositions.clear();
            parent.dirty = true;
            parent.rebuildVisibility();
        }));

        // Hint: click block in viewport to hide it
        if (hoveredViewportPos != null) {
            g.drawString(font, Component.translatable("screen.phantasia.hide_pos_editor.hint_click").getString(), 8,
                    (TOP_BAR_H - 8) / 2, C_DIM(), false);
        } else {
            g.drawString(font,
                    Component.translatable("screen.phantasia.hide_pos_editor.hint_click_verbose").getString(), 8,
                    (TOP_BAR_H - 8) / 2, C_DIM(), false);
        }
    }

    // ── Right panel ───────────────────────────────────────────────────────────

    private void renderRightPanel(GuiGraphics g, int mx, int my) {
        int px = this.width - PANEL_W;
        g.fill(px, TOP_BAR_H, this.width, this.height, C_PANEL());
        g.fill(px, TOP_BAR_H, px + 1, this.height, C_ACCENT());

        // Panel header
        g.fill(px, TOP_BAR_H, this.width, TOP_BAR_H + 14, C_BAR());
        g.drawString(font, Component.translatable("screen.phantasia.hide_pos_editor.section_positions").getString(),
                px + 6, TOP_BAR_H + 3, C_ACCENT(), false);

        renderList(g, mx, my, px);
        renderBottomStrip(g, mx, my, px);
    }

    private void renderList(GuiGraphics g, int mx, int my, int px) {
        List<int[]> positions = step().hidePositions;

        int listTop = TOP_BAR_H + 16;
        int listBottom = this.height - BOTTOM_H - 2;
        int listH = listBottom - listTop;
        int visRows = listH / ROW_H;

        int maxScroll = Math.max(0, positions.size() - visRows);
        scrollOffset = Math.min(scrollOffset, maxScroll);
        if (scrollOffset < 0) scrollOffset = 0;

        if (positions.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.phantasia.hide_pos_editor.empty").getString(),
                    px + PANEL_W / 2, listTop + listH / 2 - 4, C_DIM());
            g.drawCenteredString(font,
                    Component.translatable("screen.phantasia.hide_pos_editor.empty_hint").getString(),
                    px + PANEL_W / 2, listTop + listH / 2 + 6, C_DIM());
            return;
        }

        // Scrollbar
        if (positions.size() > visRows) {
            int sbX = this.width - 6;
            g.fill(sbX, listTop, sbX + 4, listBottom, 0x33FFFFFF);
            int thumbH = Math.max(12, listH * visRows / positions.size());
            int thumbY = listTop + (listH - thumbH) * scrollOffset / Math.max(1, maxScroll);
            g.fill(sbX, thumbY, sbX + 4, thumbY + thumbH, C_ACCENT());
        }

        for (int i = scrollOffset; i < Math.min(positions.size(), scrollOffset + visRows + 1); i++) {
            int[] pos = positions.get(i);
            int ry = listTop + (i - scrollOffset) * ROW_H;
            if (ry + ROW_H < listTop || ry > listBottom) continue;

            boolean hov = isOver(mx, my, px + 1, ry, PANEL_W - 22, ROW_H);
            if (hov) hoveredListPos = pos;

            // Row background
            g.fill(px + 1, ry, this.width - 6, ry + ROW_H - 1,
                    hov ? 0xBB0D1A2D : (i % 2 == 0 ? 0x11FFFFFF : 0x00000000));
            if (hov) g.fill(px + 1, ry, px + 2, ry + ROW_H - 1, C_ACCENT());

            // Coord label
            String coord = pos[0] + ", " + pos[1] + ", " + pos[2];
            g.drawString(font, coord, px + 7, ry + (ROW_H - 8) / 2, C_ACCENT(), false);

            // Block name (dimmed, right-aligned to avoid coord overlap)
            String blockName = lookupBlockName(pos);
            if (blockName != null) {
                int maxNameW = PANEL_W - 30 - font.width(coord) - 8;
                if (font.width(blockName) > maxNameW)
                    blockName = font.plainSubstrByWidth(blockName, maxNameW - font.width("…")) + "…";
                g.drawString(font, blockName,
                        px + 7 + font.width(coord) + 6, ry + (ROW_H - 8) / 2, C_DIM(), false);
            }

            // ✕ remove button
            int rbx = this.width - 22;
            boolean rbHov = isOver(mx, my, rbx, ry + 3, 16, ROW_H - 6);
            g.fill(rbx, ry + 3, rbx + 16, ry + ROW_H - 3,
                    rbHov ? 0xBB3A0A0A : C_BTN());
            g.drawCenteredString(font, "✕", rbx + 8, ry + (ROW_H - 8) / 2,
                    rbHov ? C_RED() : C_DIM());
            final int fi = i;
            btns.add(new Btn(rbx, ry + 3, 16, ROW_H - 6, () -> {
                parent.checkpoint();
                if (fi >= 0 && fi < step().hidePositions.size()) {
                    step().hidePositions.remove(fi);
                    parent.dirty = true;
                    parent.rebuildVisibility();
                    if (scrollOffset > 0 && scrollOffset >= step().hidePositions.size())
                        scrollOffset--;
                }
            }));
        }
    }

    private void renderBottomStrip(GuiGraphics g, int mx, int my, int px) {
        int barY = this.height - BOTTOM_H;
        g.fill(px, barY, this.width, this.height, C_BAR());
        g.fill(px, barY, px + 1, this.height, C_ACCENT());
        g.fill(px + 1, barY, this.width, barY + 1, 0x33FFFFFF);

        g.drawString(font, Component.translatable("screen.phantasia.hide_pos_editor.label_add").getString(), px + 6,
                barY + 11, C_DIM(), false);
        // addBox is positioned in init; just draw the Add button beside it
        int addBtnX = this.width - PANEL_W + 4 + (PANEL_W - 60) + 4;
        boolean addHov = isOver(mx, my, addBtnX, barY + 8, 44, 16);
        g.fill(addBtnX, barY + 8, addBtnX + 44, barY + 24,
                addHov ? C_BTN_HOV() : C_BTN());
        if (addHov) g.fill(addBtnX, barY + 8, addBtnX + 44, barY + 9, C_ACCENT());
        g.drawCenteredString(font, Component.translatable("screen.phantasia.hide_pos_editor.btn_add").getString(),
                addBtnX + 22, barY + 11,
                addHov ? C_ACCENT() : C_TEXT());
        btns.add(new Btn(addBtnX, barY + 8, 44, 16, this::tryAdd));

        if (addError != null)
            g.drawString(font, addError, px + 6, barY + 24, C_RED(), false);
    }

    // ── Viewport highlight overlays ───────────────────────────────────────────

    /**
     * Draws a coloured halo in the viewport when a list row is hovered,
     * and a "click to hide" label when the cursor is over a visible block.
     */
    private void renderViewportOverlays(GuiGraphics g, int mx, int my) {
        int viewW = this.width - PANEL_W;

        // Viewport click hint
        if (hoveredViewportPos != null && mx < viewW && my > TOP_BAR_H) {
            // Translate world pos → local for display
            int[] local = worldToLocal(hoveredViewportPos);
            String blockName = "";
            try {
                BlockState bs = parent.editorLevel != null ? parent.editorLevel.getBlockState(hoveredViewportPos) :
                        null;
                if (bs != null && !bs.isAir()) blockName = bs.getBlock().getName().getString();
            } catch (Exception ignored) {}

            String coordStr = local != null ? local[0] + ", " + local[1] + ", " + local[2] :
                    hoveredViewportPos.getX() + ", " + hoveredViewportPos.getY() + ", " + hoveredViewportPos.getZ();

            // Draw tooltip near cursor
            int tipX = mx + 10, tipY = my - 22;
            String line1 = blockName.isEmpty() ? coordStr : blockName;
            String line2 = blockName.isEmpty() ? "" : "  " + coordStr;
            int tipW = Math.max(font.width(line1), font.width(line2)) + 8;
            int tipH = blockName.isEmpty() ? 14 : 24;
            tipX = Math.min(tipX, viewW - tipW - 2);
            tipY = Math.max(tipY, TOP_BAR_H + 2);

            g.fill(tipX - 1, tipY - 1, tipX + tipW + 1, tipY + tipH + 1, 0xBB000000);
            g.fill(tipX - 1, tipY - 1, tipX, tipY + tipH + 1, C_ACCENT());
            g.drawString(font, line1, tipX + 3, tipY + 3, C_TEXT(), false);
            if (!line2.isEmpty())
                g.drawString(font, line2, tipX + 3, tipY + 13, C_DIM(), false);
        }

        // Dim the viewport when a list row is hovered to draw attention to it
        if (hoveredListPos != null && mx >= this.width - PANEL_W) {
            // We can't easily draw a 3D overlay here without custom OpenGL,
            // so instead flash a subtle tint over the viewport edge
            g.fill(0, TOP_BAR_H, this.width - PANEL_W, this.height, 0x22FF4444);

            // Draw the local coords in the corner of the viewport
            String coordLabel = "  Hiding: " + hoveredListPos[0] + ", " + hoveredListPos[1] + ", " + hoveredListPos[2];
            g.fill(2, TOP_BAR_H + 2, font.width(coordLabel) + 6, TOP_BAR_H + 14, 0xAA000000);
            g.drawString(font, coordLabel, 4, TOP_BAR_H + 4, C_RED(), false);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String lookupBlockName(int[] localXYZ) {
        if (parent.editorLevel == null || parent.pattern == null) return null;
        try {
            BlockPos world = parent.pattern.toWorld(new BlockPos(localXYZ[0], localXYZ[1], localXYZ[2]));
            if (world == null) return null;
            BlockState state = parent.editorLevel.getBlockState(world);
            if (state == null || state.isAir()) return null;
            return state.getBlock().getName().getString();
        } catch (Exception e) {
            return null;
        }
    }

    private int[] worldToLocal(BlockPos world) {
        if (parent.pattern == null) return null;
        try {
            BlockPos local = parent.pattern.toLocal(world);
            return local != null ? new int[] { local.getX(), local.getY(), local.getZ() } : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void tryAdd() {
        addError = null;
        String raw = addBox.getValue().trim();
        if (raw.isEmpty()) {
            addError = Component.translatable("screen.phantasia.hide_pos_editor.err_enter_xyz").getString();
            return;
        }
        String[] parts = raw.split("[,\\s]+");
        if (parts.length < 3) {
            addError = Component.translatable("screen.phantasia.hide_pos_editor.err_need_xyz").getString();
            return;
        }
        try {
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            int z = Integer.parseInt(parts[2].trim());

            for (int[] p : step().hidePositions)
                if (p.length >= 3 && p[0] == x && p[1] == y && p[2] == z) {
                    addError = Component.translatable("screen.phantasia.hide_pos_editor.err_already_hidden")
                            .getString();
                    return;
                }

            parent.checkpoint();
            step().hidePositions.add(new int[] { x, y, z });
            parent.dirty = true;
            parent.rebuildVisibility();
            addBox.setValue("");

            int listH = this.height - BOTTOM_H - 2 - (TOP_BAR_H + 16);
            int visRows = listH / ROW_H;
            scrollOffset = Math.max(0, step().hidePositions.size() - visRows);
        } catch (NumberFormatException e) {
            addError = Component.translatable("screen.phantasia.hide_pos_editor.err_invalid_number").getString();
        }
    }

    private void tryAddWorldPos(BlockPos world) {
        if (parent.pattern == null) return;
        int[] local = worldToLocal(world);
        if (local == null) return;

        for (int[] p : step().hidePositions)
            if (p.length >= 3 && p[0] == local[0] && p[1] == local[1] && p[2] == local[2])
                return; // already hidden

        parent.checkpoint();
        step().hidePositions.add(local);
        parent.dirty = true;
        parent.rebuildVisibility();

        int listH = this.height - BOTTOM_H - 2 - (TOP_BAR_H + 16);
        int visRows = listH / ROW_H;
        scrollOffset = Math.max(0, step().hidePositions.size() - visRows);
    }

    private void close() {
        Minecraft.getInstance().setScreen(parent);
    }

    private boolean isOver(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean isOver(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Input
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // Button registry first
        for (Btn b : btns) if (b.hit(mx, my)) {
            b.action().run();
            return true;
        }
        if (super.mouseClicked(mx, my, btn)) return true;

        int viewW = this.width - PANEL_W;

        // Left-click in viewport → add hovered block to hide list
        if (btn == 0 && mx < viewW && my > TOP_BAR_H && hoveredViewportPos != null) {
            tryAddWorldPos(hoveredViewportPos);
            return true;
        }

        // Middle-click in viewport → panning
        if (btn == 2 && mx < viewW && my > TOP_BAR_H) {
            isPanning = true;
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (camera == null) return super.mouseDragged(mx, my, btn, dx, dy);
        int viewW = this.width - PANEL_W;
        if (mx >= viewW) return super.mouseDragged(mx, my, btn, dx, dy);

        if (btn == 0) {
            camera.orbit((float) dx * CAM_ORBIT, (float) dy * CAM_ORBIT);
            return true;
        }
        if (btn == 2 && isPanning) {
            Vector3f right = new Vector3f(), up = new Vector3f();
            camera.getRightAndUp(right, up);
            float s = CAM_PAN;
            camera.pan(
                    (right.x * (float) -dx + up.x * (float) dy) * s,
                    (right.y * (float) -dx + up.y * (float) dy) * s,
                    (right.z * (float) -dx + up.z * (float) dy) * s);
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 2) isPanning = false;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int viewW = this.width - PANEL_W;
        if (mx < viewW && my > TOP_BAR_H) {
            // Zoom
            if (camera != null) camera.zoom(delta > 0 ? 0.9f : 1.1f, 2f, 300f);
        } else {
            // List scroll
            scrollOffset = Math.max(0, scrollOffset - (int) Math.signum(delta));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (kc == GLFW.GLFW_KEY_ENTER || kc == GLFW.GLFW_KEY_KP_ENTER) {
            if (getFocused() == addBox) {
                tryAdd();
                return true;
            }
        }
        if (kc == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(kc, sc, mod);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
