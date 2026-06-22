package net.phoenixvine.phantasia.client.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phantasia.client.camera.CameraView;
import net.phoenixvine.phantasia.client.camera.PhantasiaCamera;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.client.render.PhantasiaWorldRenderer;
import net.phoenixvine.phantasia.common.data.pattern.PhantasiaScenePattern;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData;

import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

/**
 * PhantasiaItemMicrosceneScreen
 *
 * Modal popup opened when the user clicks an item in the scene editor's item
 * panel. Shows:
 *
 * <ul>
 * <li>Left half: the item icon large, type badge, label, and full description
 * text (word-wrapped).
 * <li>Right half: an embedded live 3D scene render if a {@code microsceneId}
 * was configured, otherwise a tinted placeholder.
 * </ul>
 *
 * The embedded scene auto-rotates slowly and plays through its steps so the
 * user can see the machine in context without interacting with the camera.
 */
@OnlyIn(Dist.CLIENT)
public class PhantasiaItemMicrosceneScreen extends PhantasiaScreen {

    // ── Theme ─────────────────────────────────────────────────────────────────
    private static final int C_CARD = 0xEE0A0A18;

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int CARD_PAD = 24;
    private static final int ICON_SIZE = 64;   // large item icon

    // ── Data ──────────────────────────────────────────────────────────────────
    private final Screen parent;
    private final PhantasiaSceneData.ItemConditionData item;
    @Nullable
    private final PhantasiaSceneData microsceneData;

    // ── 3D scene ──────────────────────────────────────────────────────────────
    @Nullable
    private PhantasiaTrackedDummyWorld sceneWorld;
    @Nullable
    private PhantasiaScenePattern scenePattern;
    @Nullable
    private PhantasiaWorldRenderer sceneRenderer;
    @Nullable
    private PhantasiaCamera sceneCamera;

    // Playback
    private int tick = 0;
    private float autoYaw = 25f;   // slowly rotates
    private int stepIndex = 0;

    // ── Btn list ──────────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────────────────

    public PhantasiaItemMicrosceneScreen(Screen parent,
                                         PhantasiaSceneData.ItemConditionData item,
                                         @Nullable PhantasiaSceneData microsceneData) {
        super(Component.empty());
        this.parent = parent;
        this.item = item;
        this.microsceneData = microsceneData;
    }

    @Override
    public void hideAllInputs() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        if (microsceneData != null) {
            sceneWorld = new PhantasiaTrackedDummyWorld();
            scenePattern = PhantasiaScenePattern.build(microsceneData, sceneWorld);
            sceneRenderer = new PhantasiaWorldRenderer(sceneWorld);
            if (scenePattern != null)
                sceneRenderer.setBaseplatePositions(scenePattern.allBaseplatePositions);
            initSceneCamera();
            rebuildSceneVisibility();
        }
    }

    private void initSceneCamera() {
        if (scenePattern == null || scenePattern.placements.isEmpty()) {
            sceneCamera = new PhantasiaCamera(autoYaw, -25f, 6f, 0f, 2f, 0f);
            return;
        }
        float sumX = 0, sumZ = 0;
        for (var pe : scenePattern.placements) {
            sumX += pe.offset.getX();
            sumZ += pe.offset.getZ();
        }
        float midX = sumX / scenePattern.placements.size();
        float midZ = sumZ / scenePattern.placements.size();
        float midY = (scenePattern.minY + scenePattern.maxY) * 0.5f + 0.5f;
        float span = 0;
        for (var pe : scenePattern.placements) {
            span = Math.max(span, Math.abs(pe.offset.getX() - midX));
            span = Math.max(span, Math.abs(pe.offset.getZ() - midZ));
        }
        float zoom = Math.max(4f, span * 2.2f + 3f);
        sceneCamera = new PhantasiaCamera(autoYaw, -25f, zoom, midX, midY, midZ);
    }

    private void rebuildSceneVisibility() {
        if (sceneRenderer == null || scenePattern == null || microsceneData == null) return;
        PhantasiaSceneData.StepData step = currentStep();
        if (step == null) return;
        sceneRenderer.setVisible(scenePattern.computeVisible(step, microsceneData));
        sceneRenderer.requestBake();
    }

    @Nullable
    private PhantasiaSceneData.StepData currentStep() {
        if (microsceneData == null || microsceneData.steps.isEmpty()) return null;
        stepIndex = Math.min(stepIndex, microsceneData.steps.size() - 1);
        return microsceneData.steps.get(stepIndex);
    }

    @Override
    public void tick() {
        super.tick();
        tick++;
        // Slow auto-rotation
        autoYaw += 0.4f;
        if (sceneCamera != null) sceneCamera.setYaw(autoYaw);

        // Step auto-advance: move to next step when current step's duration expires
        if (microsceneData != null && microsceneData.steps.size() > 1) {
            int nextIdx = stepIndex + 1;
            if (nextIdx < microsceneData.steps.size()) {
                int nextTick = microsceneData.steps.get(nextIdx).tick;
                int loopTick = tick % (microsceneData.steps.get(microsceneData.steps.size() - 1).tick + 80);
                if (loopTick >= nextTick) {
                    stepIndex = nextIdx % microsceneData.steps.size();
                    rebuildSceneVisibility();
                }
            } else {
                // Loop back
                int totalTicks = microsceneData.steps.get(microsceneData.steps.size() - 1).tick + 80;
                if (tick % totalTicks == 0) {
                    stepIndex = 0;
                    rebuildSceneVisibility();
                }
            }
        }
    }

    @Override
    public void removed() {
        if (sceneRenderer != null) {
            sceneRenderer.close();
            sceneRenderer = null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Render
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        btns.clear();

        // Dim entire background
        g.fill(0, 0, width, height, C_BG());

        // Card dimensions
        int cardW = Math.min(700, width - 40);
        int cardH = Math.min(420, height - 60);
        int cardX = (width - cardW) / 2;
        int cardY = (height - cardH) / 2;

        // Card background + border
        g.fill(cardX, cardY, cardX + cardW, cardY + cardH, C_CARD);
        g.fill(cardX, cardY, cardX + cardW, cardY + 1, C_ACCENT());
        g.fill(cardX, cardY + cardH - 1, cardX + cardW, cardY + cardH, C_ACCENT() & 0x55FFFFFF | 0x55000000);
        g.fill(cardX, cardY, cardX + 1, cardY + cardH, C_ACCENT() & 0x44FFFFFF | 0x44000000);
        g.fill(cardX + cardW - 1, cardY, cardX + cardW, cardY + cardH, C_ACCENT() & 0x44FFFFFF | 0x44000000);

        // Layout: if microscene present split 50/50, else full width for item info
        boolean has3d = sceneRenderer != null && sceneCamera != null;
        int infoW = has3d ? cardW / 2 : cardW;
        int scW = cardW - infoW;

        // ── 3D scene (right half) ─────────────────────────────────────────────
        if (has3d) {
            int sx = cardX + infoW;
            int sy = cardY + 1;
            int sw = scW;
            int sh = cardH - 2;

            // Render scene into that region
            CameraView view = sceneCamera.getView(partial);
            sceneRenderer.render(view, sx, sy, sw, sh);

            // Step caption overlay
            PhantasiaSceneData.StepData step = currentStep();
            if (step != null && step.caption != null && !step.caption.isBlank()) {
                int capY = cardY + cardH - 22;
                g.fill(sx, capY, sx + sw, cardY + cardH - 1, 0xBB050510);
                var lines = font.split(Component.literal(step.caption), sw - 16);
                int ly = capY + (22 - lines.size() * 9) / 2;
                for (var line : lines) {
                    g.drawCenteredString(font, line, sx + sw / 2, ly, C_TEXT());
                    ly += 9;
                }
            }

            // Divider
            g.fill(sx - 1, cardY + 1, sx, cardY + cardH - 1, 0x33FFFFFF);
        }

        // ── Item info (left half / full) ──────────────────────────────────────
        int ix = cardX + CARD_PAD;
        int iy = cardY + CARD_PAD;
        int iw = infoW - CARD_PAD * 2;
        int bottomLimit = cardY + cardH - 28; // space for close button

        // Large item icon — top left of the info area
        net.minecraft.world.item.Item mcItem = resolveItem(item.item);
        if (mcItem != null) {
            ItemStack stack = new ItemStack(mcItem, item.count);
            float scale = ICON_SIZE / 16f;
            g.pose().pushPose();
            g.pose().translate(ix, iy, 200f);
            g.pose().scale(scale, scale, 1f);
            g.renderItem(stack, 0, 0);
            g.renderItemDecorations(font, stack, 0, 0);
            g.pose().popPose();
        } else {
            g.fill(ix, iy, ix + ICON_SIZE, iy + ICON_SIZE, 0x44FF0000);
            g.drawCenteredString(font, "?", ix + ICON_SIZE / 2, iy + ICON_SIZE / 2 - 4, 0xFFFF5252);
        }

        // Item registry ID under the icon
        String shortId = item.item != null && item.item.contains(":") ? item.item.split(":")[1].replace('_', ' ') :
                (item.item != null ? item.item : "?");
        g.drawString(font, shortId, ix, iy + ICON_SIZE + 3, C_DIM(), false);

        // ── Header block — to the right of the icon ───────────────────────────
        int tx = ix + ICON_SIZE + 12;
        int ty = iy;
        int tw = iw - ICON_SIZE - 12;

        // Label
        g.drawString(font, item.displayLabel(), tx, ty, 0xFFFFFFFF, false);
        ty += 12;

        // Type badge
        int ac = item.accentColor();
        String typeTxt = switch (item.type == null ? "input" : item.type.toLowerCase(java.util.Locale.ROOT)) {
            case "output" -> "Output";
            case "catalyst" -> "Catalyst";
            default -> "Input";
        };
        int badgeW = font.width(typeTxt) + 8;
        g.fill(tx, ty, tx + badgeW, ty + 11, ac & 0x44FFFFFF | 0x44000000);
        g.fill(tx, ty, tx + badgeW, ty + 1, ac);
        g.drawString(font, typeTxt, tx + 4, ty + 2, ac, false);
        ty += 14;

        // Track info if animated
        if (item.track != null && !"none".equals(item.track)) {
            String trackDesc = "Animated: " + item.track + " (" + item.trackDurationTicks + " ticks/cycle)";
            g.drawString(font, trackDesc, tx, ty, C_DIM(), false);
            ty += 10;
        }

        // First portion of description — fills remaining space right of the icon
        // then continues full-width below the icon area
        int descStartY = iy + ICON_SIZE + 14;
        if (item.description != null && !item.description.isBlank()) {
            // Thin divider above description block
            g.fill(ix, descStartY - 4, ix + iw, descStartY - 3, 0x22FFFFFF);

            var lines = font.split(Component.literal(item.description), iw);
            int fy = descStartY;
            for (var line : lines) {
                if (fy + 9 > bottomLimit) break;
                g.drawString(font, line, ix, fy, C_TEXT(), false);
                fy += 10;
            }
        } else {
            g.fill(ix, descStartY - 4, ix + iw, descStartY - 3, 0x22FFFFFF);
            g.drawString(font, Component.translatable("screen.phantasia.item_microscene.no_description").getString(),
                    ix, descStartY, C_DIM(), false);
        }

        // ── Close button ──────────────────────────────────────────────────────
        int closeX = cardX + cardW - 52;
        int closeY = cardY + cardH - 20;
        boolean closeHov = isOver(mx, my, closeX, closeY, 44, 14);
        g.fill(closeX, closeY, closeX + 44, closeY + 14, closeHov ? C_BTN_HOV() : C_BTN());
        if (closeHov) g.fill(closeX, closeY, closeX + 44, closeY + 1, C_ACCENT());
        g.drawCenteredString(font, Component.translatable("screen.phantasia.item_microscene.btn_back").getString(),
                closeX + 22, closeY + 3, closeHov ? C_ACCENT() : C_TEXT());
        btns.add(new Btn(closeX, closeY, 44, 14, this::goBack));

        // Click anywhere outside card to close
        btns.add(new Btn(0, 0, cardX, height, this::goBack));
        btns.add(new Btn(cardX + cardW, 0, width - cardX - cardW, height, this::goBack));

        super.render(g, mx, my, partial);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Input
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Btn b : btns) if (b.hit(mx, my)) {
            b.action().run();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (kc == GLFW.GLFW_KEY_ESCAPE) {
            goBack();
            return true;
        }
        return super.keyPressed(kc, sc, mod);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void goBack() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Nullable
    private static net.minecraft.world.item.Item resolveItem(String id) {
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
}
