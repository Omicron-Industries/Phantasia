package net.phoenixvine.phantasia.client.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.client.camera.CameraView;
import net.phoenixvine.phantasia.client.camera.LerpType;
import net.phoenixvine.phantasia.client.camera.PhantasiaCamera;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.client.render.PhantasiaWorldRenderer;
import net.phoenixvine.phantasia.client.screens.editors.PhantasiaSceneEditorScreen;
import net.phoenixvine.phantasia.common.data.pattern.PhantasiaScenePattern;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector3f;

import java.util.*;

/**
 * PhantasiaSceneViewerScreen
 *
 * Read-only viewer for {@link PhantasiaSceneData} multi-machine scenes.
 * Analogous to {@link PhantasiaSceneScreen} for GT machines — it loads the
 * merged pattern, plays through the scripted steps, and lets the user orbit,
 * zoom, and inspect blocks. An "Edit" button in the top-right opens
 * {@link PhantasiaSceneEditorScreen} for the full editing workflow.
 *
 * Layout:
 * - Full viewport (no side panel) with a minimal top bar.
 * - A thin timeline strip at the bottom for step playback.
 * - Block name tooltip on hover (right-click opens block inspector).
 */
@OnlyIn(Dist.CLIENT)
public class PhantasiaSceneViewerScreen extends Screen {

    // ── Theme (mirrors SceneScreen) ───────────────────────────────────────────
    private static final int C_BG = 0xFF080810;
    private static final int C_BAR = 0xEE0A0A14;
    private static final int C_ACCENT = 0xFF4FC3F7;
    private static final int C_BTN = 0xBB151528;
    private static final int C_BTN_HOV = 0xBB1A2840;
    private static final int C_TEXT = 0xFFDDDDDD;
    private static final int C_DIM = 0xFF667788;
    private static final int C_TL_BG = 0xDD0A0A14;
    private static final int C_PROG = 0xFF4FC3F7;
    private static final int C_GREEN = 0xFF66BB6A;

    private static final int TOP_BAR_H = 22;
    private static final int TIMELINE_H = 24;

    private static final float CAM_ORBIT = 0.45f;
    private static final float CAM_PAN = 0.02f;
    private static final float ZOOM_IN = 0.9f;
    private static final float ZOOM_OUT = 1.1f;

    // ── Core state ────────────────────────────────────────────────────────────
    private final Screen parent;
    private PhantasiaSceneData data;

    private PhantasiaTrackedDummyWorld level;
    private PhantasiaWorldRenderer renderer;
    private PhantasiaScenePattern pattern;

    private PhantasiaCamera camera;
    private boolean isPanning = false;

    // ── Playback ──────────────────────────────────────────────────────────────
    private boolean playing = true;
    private int playbackTick = 0;
    private float tickAccum = 0f;
    private float speed = 1f;
    private boolean scrubbing = false;

    private int lastStepIndex = -1;

    // ── Hover ─────────────────────────────────────────────────────────────────
    private BlockPos hoveredPos = null;

    // ── Button registry ───────────────────────────────────────────────────────
    private record Btn(int x, int y, int w, int h, Runnable action) {

        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Btn> btns = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────────────────
    public PhantasiaSceneViewerScreen(Screen parent, PhantasiaSceneData data) {
        super(Component.literal(data.name != null ? data.name : data.id));
        this.parent = parent;
        this.data = data;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Init
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();

        if (renderer == null) {
            level = new PhantasiaTrackedDummyWorld();
            pattern = PhantasiaScenePattern.build(data, level);

            renderer = new PhantasiaWorldRenderer(level);
            if (pattern != null) {
                renderer.setBaseplatePositions(pattern.allBaseplatePositions);

                Set<BlockPos> fullBakeSet = new HashSet<>(level.renderedBlocks.keySet());
                fullBakeSet.addAll(pattern.allBaseplatePositions);
                // Belt-and-suspenders: also pull positions from computeVisible(all)
                PhantasiaSceneData.StepData allStep = new PhantasiaSceneData.StepData();
                allStep.show = "all";
                Set<BlockPos> allVis = pattern.computeVisible(allStep, data);
                if (allVis != null) fullBakeSet.addAll(allVis);

                renderer.setPatternBlocks(fullBakeSet);
                renderer.requestBake();
            }

            initCamera();
            applyVisibility();
        }
    }

    private void initCamera() {
        if (pattern == null || pattern.placements.isEmpty()) {
            camera = new PhantasiaCamera(-135f, -30f, 30f, 0f, 5f, 0f);
            return;
        }
        float sumX = 0, sumZ = 0;
        for (var pe : pattern.placements) {
            sumX += pe.offset.getX();
            sumZ += pe.offset.getZ();
        }
        float midX = sumX / pattern.placements.size();
        float midZ = sumZ / pattern.placements.size();
        float midY = (pattern.minY + pattern.maxY) * 0.5f + 0.5f;
        float spanH = pattern.maxY - pattern.minY + 1;
        float dist = 20f + Math.max(0, spanH - 6) * 2f;
        camera = new PhantasiaCamera(-135f, -30f, dist, midX, midY, midZ);
        camera.setFloorY(pattern.minY + 0.5f);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Visibility
    // ─────────────────────────────────────────────────────────────────────────

    private void applyVisibility() {
        if (renderer == null || pattern == null) return;
        PhantasiaSceneData.StepData step = activeStep();
        Set<BlockPos> visible = pattern.computeVisible(step != null ? step : allStep(), data);
        renderer.setVisible(visible != null ? visible : Set.of());
    }

    private PhantasiaSceneData.StepData activeStep() {
        List<PhantasiaSceneData.StepData> steps = data.steps;
        if (steps == null || steps.isEmpty()) return null;
        PhantasiaSceneData.StepData active = steps.get(0);
        for (PhantasiaSceneData.StepData s : steps) {
            if (s.tick <= playbackTick) active = s;
            else break;
        }
        return active;
    }

    private int activeStepIndex() {
        List<PhantasiaSceneData.StepData> steps = data.steps;
        if (steps == null || steps.isEmpty()) return 0;
        int idx = 0;
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).tick <= playbackTick) idx = i;
            else break;
        }
        return idx;
    }

    private int totalTicks() {
        List<PhantasiaSceneData.StepData> steps = data.steps;
        if (steps == null || steps.isEmpty()) return 60;
        return steps.get(steps.size() - 1).tick + 60;
    }

    private static PhantasiaSceneData.StepData allStep() {
        PhantasiaSceneData.StepData s = new PhantasiaSceneData.StepData();
        s.show = "all";
        return s;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tick
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (camera != null) camera.tick();

        if (!playing || scrubbing) return;
        tickAccum += speed;
        while (tickAccum >= 1f) {
            tickAccum -= 1f;
            playbackTick++;
        }
        if (playbackTick >= totalTicks()) {
            playbackTick = totalTicks();
            playing = false;
        }

        // Apply camera and visibility on step change
        int si = activeStepIndex();
        if (si != lastStepIndex) {
            lastStepIndex = si;
            applyVisibility();
            PhantasiaSceneData.StepData step = activeStep();
            if (step != null && step.camera != null && camera != null) {
                float zoom = step.camera.zoom > 0 ? step.camera.zoom : camera.getZoom();
                LerpType lt = LerpType.fromString(step.camera.lerpType);
                camera.scriptDrive(step.camera.yaw, step.camera.pitch, zoom, lt,
                        step.camera.lerpTicks > 0 ? step.camera.lerpTicks : 20);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Render
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        btns.clear();

        if (Minecraft.getInstance().level != null) {
            long gameTime = Minecraft.getInstance().level.getGameTime();
            RenderSystem.setShaderGameTime(gameTime, partial);
        }

        g.fill(0, 0, this.width, this.height, C_BG);

        int viewH = this.height - TOP_BAR_H - TIMELINE_H;

        // ── Viewport ──────────────────────────────────────────────────────────
        if (renderer != null && camera != null) {
            CameraView view = camera.getView(partial);
            renderer.setMousePos(mx, my);
            renderer.render(view, 0, TOP_BAR_H, this.width, viewH);

            BlockHitResult hit = renderer.getLastHitResult();
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos hp = hit.getBlockPos();
                hoveredPos = renderer.isVisible(hp) ? hp : null;
            } else {
                hoveredPos = null;
            }
        }

        renderTopBar(g, mx, my);
        renderTimeline(g, mx, my);

        super.render(g, mx, my, partial);

        // Block hover tooltip (drawn after super so it's on top)
        if (hoveredPos != null && level != null) {
            try {
                BlockState bs = level.getBlockState(hoveredPos);
                if (!bs.isAir()) {
                    String name = bs.getBlock().getName().getString();
                    int tw = font.width(name) + 8;
                    int tx = Math.min(mx + 10, this.width - tw - 2);
                    int ty = Math.max(my - 18, TOP_BAR_H + 2);
                    g.fill(tx - 1, ty - 1, tx + tw + 1, ty + 11, 0xBB000000);
                    g.fill(tx - 1, ty - 1, tx, ty + 11, C_ACCENT);
                    g.drawString(font, name, tx + 3, ty + 1, C_TEXT, false);
                }
            } catch (Exception ignored) {}
        }
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private void renderTopBar(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, this.width, TOP_BAR_H, C_BAR);
        g.fill(0, TOP_BAR_H - 1, this.width, TOP_BAR_H, C_ACCENT);

        // Title centred
        String title = data.name != null && !data.name.isBlank() ? data.name : data.id;
        g.drawCenteredString(font, title, this.width / 2, (TOP_BAR_H - 8) / 2, C_ACCENT);

        // Left: Back
        topBtn(g, mx, my, 4, "← Back", this::onClose);

        // Right: Edit (admin only), then camera reset
        int rx = this.width - 4;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getAbilities().instabuild) {
            rx = topBtnRight(g, mx, my, rx, "✏ Edit", this::openEditor);
        }
        // Show Guide button whenever there is guide content
        if (hasGuideContent()) {
            rx = topBtnRight(g, mx, my, rx, "📖 Guide", this::openGuide);
        }
        topBtnRight(g, mx, my, rx, "⊕ Center", this::centerCamera);
    }

    private void topBtn(GuiGraphics g, int mx, int my, int x, String label, Runnable action) {
        int w = font.width(label) + 10, h = TOP_BAR_H - 6;
        boolean hov = isOver(mx, my, x, 3, w, h);
        g.fill(x, 3, x + w, 3 + h, hov ? C_BTN_HOV : C_BTN);
        if (hov) g.fill(x, 3, x + w, 4, C_ACCENT);
        g.drawString(font, label, x + 5, (TOP_BAR_H - 8) / 2, hov ? C_ACCENT : C_TEXT, false);
        btns.add(new Btn(x, 3, w, h, action));
    }

    private int topBtnRight(GuiGraphics g, int mx, int my, int rx, String label, Runnable action) {
        int w = font.width(label) + 10, h = TOP_BAR_H - 6;
        int x = rx - w;
        boolean hov = isOver(mx, my, x, 3, w, h);
        g.fill(x, 3, x + w, 3 + h, hov ? C_BTN_HOV : C_BTN);
        if (hov) g.fill(x, 3, x + w, 4, C_ACCENT);
        g.drawString(font, label, x + 5, (TOP_BAR_H - 8) / 2, hov ? C_ACCENT : C_TEXT, false);
        btns.add(new Btn(x, 3, w, h, action));
        return x - 4;
    }

    // ── Timeline strip ────────────────────────────────────────────────────────

    private void renderTimeline(GuiGraphics g, int mx, int my) {
        int tlY = this.height - TIMELINE_H;
        int tx = 80, tw = this.width - tx - 70;
        int midY = tlY + TIMELINE_H / 2;

        g.fill(0, tlY, this.width, this.height, C_TL_BG);
        g.fill(0, tlY, this.width, tlY + 1, 0x33FFFFFF);

        // ▶/⏸ button
        int pbW = font.width(playing ? "⏸" : "▶") + 10;
        boolean pbHov = isOver(mx, my, 6, tlY + 4, pbW, TIMELINE_H - 8);
        g.fill(6, tlY + 4, 6 + pbW, tlY + TIMELINE_H - 4, pbHov ? C_BTN_HOV : C_BTN);
        g.drawString(font, playing ? "⏸" : "▶", 6 + 5, tlY + (TIMELINE_H - 8) / 2 + 2,
                pbHov ? C_ACCENT : C_TEXT, false);
        btns.add(new Btn(6, tlY + 4, pbW, TIMELINE_H - 8, () -> {
            if (!playing && playbackTick >= totalTicks()) {
                playbackTick = 0;
                tickAccum = 0;
                lastStepIndex = -1;
            }
            playing = !playing;
        }));

        // Speed toggle
        String spdLabel = speed == 0.5f ? "½×" : speed == 2f ? "2×" : "1×";
        int spdX = 6 + pbW + 4;
        int spdW = font.width(spdLabel) + 8;
        boolean spdHov = isOver(mx, my, spdX, tlY + 4, spdW, TIMELINE_H - 8);
        g.fill(spdX, tlY + 4, spdX + spdW, tlY + TIMELINE_H - 4, spdHov ? C_BTN_HOV : C_BTN);
        g.drawString(font, spdLabel, spdX + 4, tlY + (TIMELINE_H - 8) / 2 + 2,
                spdHov ? C_ACCENT : C_DIM, false);
        btns.add(new Btn(spdX, tlY + 4, spdW, TIMELINE_H - 8,
                () -> speed = speed == 1f ? 2f : speed == 2f ? 0.5f : 1f));

        // Track
        g.fill(tx, midY - 1, tx + tw, midY + 1, 0xFF1A2C3C);

        // Step dots
        int total = totalTicks();
        List<PhantasiaSceneData.StepData> steps = data.steps;
        if (steps != null) {
            for (PhantasiaSceneData.StepData s : steps) {
                int dotX = tx + (total > 0 ? tw * s.tick / total : 0);
                g.fill(dotX - 1, midY - 4, dotX + 1, midY + 4, 0xAAFFFFFF);
            }
        }

        // Progress bar + scrub head
        float prog = total > 0 ? (float) playbackTick / total : 0f;
        g.fill(tx, midY - 1, tx + (int) (tw * prog), midY + 1, C_PROG);
        int headX = tx + (int) (tw * prog);
        g.fill(headX - 3, midY - 5, headX + 3, midY + 5, C_ACCENT);

        // Time label
        g.drawString(font, formatTicks(playbackTick), tx + tw + 6, tlY + (TIMELINE_H - 8) / 2 + 2,
                C_DIM, false);

        // Scrub zone button
        btns.add(new Btn(tx, tlY, tw, TIMELINE_H, () -> {})); // handled in mouseClicked
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Input
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        for (Btn b : btns) if (b.hit(mx, my)) {
            b.action().run();
            return true;
        }

        int tlY = this.height - TIMELINE_H;
        int tx = 80, tw = this.width - tx - 70;

        // Timeline scrub
        if (btn == 0 && my >= tlY && mx >= tx && mx <= tx + tw) {
            playing = false;
            scrubbing = true;
            scrubTo((float) (mx - tx) / tw);
            return true;
        }

        int viewBottom = this.height - TIMELINE_H;
        if (mx < this.width && my > TOP_BAR_H && my < viewBottom) {
            if (btn == 2) {
                isPanning = true;
                return true;
            }
            if (btn == 0) return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        int tlY = this.height - TIMELINE_H;
        int tx = 80, tw = this.width - tx - 70;

        if (scrubbing && btn == 0) {
            scrubTo(Mth.clamp((float) (mx - tx) / tw, 0f, 1f));
            return true;
        }
        if (camera == null) return super.mouseDragged(mx, my, btn, dx, dy);

        if (btn == 0 && my > TOP_BAR_H && my < tlY) {
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
        if (scrubbing) {
            int tx = 80, tw = this.width - tx - 70;
            scrubTo(Mth.clamp((float) (mx - tx) / tw, 0f, 1f));
            scrubbing = false;
            applyVisibility();
        }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (my > TOP_BAR_H && my < this.height - TIMELINE_H && camera != null) {
            camera.zoom(delta > 0 ? ZOOM_IN : ZOOM_OUT, 2f, 300f);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (kc == 256) {
            onClose();
            return true;
        } // ESC
        return super.keyPressed(kc, sc, mod);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Actions
    // ─────────────────────────────────────────────────────────────────────────

    private void scrubTo(float t) {
        playbackTick = (int) (Mth.clamp(t, 0f, 1f) * totalTicks());
        int si = activeStepIndex();
        if (si != lastStepIndex) {
            lastStepIndex = si;
            applyVisibility();
        }
    }

    private void centerCamera() {
        if (camera == null || pattern == null) return;
        float sumX = 0, sumZ = 0;
        for (var pe : pattern.placements) {
            sumX += pe.offset.getX();
            sumZ += pe.offset.getZ();
        }
        float midX = sumX / pattern.placements.size();
        float midZ = sumZ / pattern.placements.size();
        float midY = (pattern.minY + pattern.maxY) * 0.5f + 0.5f;
        camera.hardReset(-135f, -30f, camera.getZoom(), midX, midY, midZ,
                LerpType.EASE_OUT, 16);
    }

    private void openEditor() {
        if (camera != null) camera.save();
        Minecraft.getInstance().setScreen(new PhantasiaSceneEditorScreen(parent, data));
    }

    private boolean hasGuideContent() {
        if (data.steps == null) return false;
        return data.steps.stream()
                .anyMatch(s -> (s.caption != null && !s.caption.isBlank()) ||
                        (s.description != null && !s.description.isBlank()) ||
                        (s.showItems && data.placements.stream().anyMatch(p -> !p.items.isEmpty())));
    }

    private void openGuide() {
        Minecraft.getInstance().setScreen(new PhantasiaGuideScreen(this, data));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onClose() {
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isOver(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static String formatTicks(int t) {
        return String.format("%d.%02ds", t / 20, (t % 20) * 5);
    }
}
