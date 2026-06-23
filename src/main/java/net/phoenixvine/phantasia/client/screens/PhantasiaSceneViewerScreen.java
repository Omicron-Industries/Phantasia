package net.phoenixvine.phantasia.client.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
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
import com.mojang.datafixers.util.Pair;
import org.joml.Vector3f;

import java.util.*;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

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
public class PhantasiaSceneViewerScreen extends PhantasiaScreen {

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

    // ── Mistakes overlay ──────────────────────────────────────────────────────
    private boolean showMistakes = false;

    // ── Button registry ───────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────────────────
    public PhantasiaSceneViewerScreen(Screen parent, PhantasiaSceneData data) {
        super(Component.literal(data.name != null ? data.name : data.id));
        this.parent = parent;
        this.data = data;
    }

    @Override
    public void hideAllInputs() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Init
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();

        net.phoenixvine.phantasia.client.render.PhantasiaParticleEngine.init();

        if (renderer == null) {
            level = new PhantasiaTrackedDummyWorld();
            level.clearSceneEntities();
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
            if (net.phoenixvine.phantasia.configs.PhantasiaConfigs.INSTANCE != null)
                camera.setLocked(net.phoenixvine.phantasia.configs.PhantasiaConfigs.INSTANCE.phantasiaUI.scriptLockCamera);
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
        if (net.phoenixvine.phantasia.configs.PhantasiaConfigs.INSTANCE != null) {
            boolean locked = net.phoenixvine.phantasia.configs.PhantasiaConfigs.INSTANCE.phantasiaUI.scriptLockCamera;
            camera.setLocked(locked);
        }
        if (data.startCamera != null) {
            float zoom = data.startCamera.zoom > 0 ? data.startCamera.zoom : dist;
            camera.scriptDrive(data.startCamera.yaw, data.startCamera.pitch, zoom, LerpType.SNAP, 1);
        }
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

    private void applyWorkingState(PhantasiaSceneData.StepData step) {
        if (level == null || pattern == null) return;
        boolean globalWorking = step.working;

        // Build per-world-position effective working state
        java.util.Map<BlockPos, Boolean> posWorking = new java.util.HashMap<>();
        for (net.phoenixvine.phantasia.common.data.pattern.PhantasiaScenePattern.PlacementEntry pe : pattern.placements) {
            PhantasiaSceneData.MachineOverride ov = step.getOverride(pe.index);
            boolean effective = ov != null ? ov.resolveWorking(globalWorking) : globalWorking;
            for (BlockPos wp : pe.worldPositions) posWorking.put(wp, effective);
        }

        // Set RecipeLogic on GTCEu workable machines (multiblock and single-block)
        try {
            for (net.minecraft.world.level.block.entity.BlockEntity be : level.blockEntities.values()) {
                if (!(be instanceof com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity mmbe)) continue;
                var machine = mmbe.getMetaMachine();
                Boolean effective = posWorking.get(be.getBlockPos());
                if (effective == null) effective = globalWorking;
                com.gregtechceu.gtceu.api.machine.trait.RecipeLogic logic = null;
                if (machine instanceof com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine workable) {
                    logic = workable.getRecipeLogic();
                } else if (machine instanceof com.gregtechceu.gtceu.api.machine.WorkableTieredMachine workable) {
                    logic = workable.getRecipeLogic();
                }
                if (logic != null)
                    logic.setStatus(effective ? com.gregtechceu.gtceu.api.machine.trait.RecipeLogic.Status.WORKING :
                            com.gregtechceu.gtceu.api.machine.trait.RecipeLogic.Status.IDLE);
            }
        } catch (Throwable ignored) {}

        // Toggle ActiveBlock state / ACTIVE property for coils, fireboxes, controllers, etc.
        // Update renderedBlocks directly (not setBlock) to preserve block entities for TESR/DynamicRender.
        for (java.util.Map.Entry<BlockPos, net.phoenixvine.phantasia.utils.PhantasiaBlockInfo> e : pattern.mergedBlockMap.entrySet()) {
            net.minecraft.world.level.block.state.BlockState original = e.getValue().getBlockState();
            if (original == null || original.isAir()) continue;
            try {
                BlockPos worldPos = e.getKey();
                boolean effectiveWorking = posWorking.getOrDefault(worldPos, globalWorking);
                net.minecraft.world.level.block.state.BlockState current = level.getBlockState(worldPos);
                if (current == null || current.isAir()) continue;
                net.minecraft.world.level.block.state.BlockState next = null;
                var currentBlock = current.getBlock();
                if (currentBlock instanceof com.gregtechceu.gtceu.api.block.ActiveBlock currentAb) {
                    next = currentAb.changeActive(current, effectiveWorking);
                } else {
                    var origBlock = original.getBlock();
                    if (origBlock instanceof com.gregtechceu.gtceu.api.block.ActiveBlock origAb) {
                        next = origAb.changeActive(current, effectiveWorking);
                    }
                }
                if (next != null && next != current) {
                    level.renderedBlocks.put(worldPos, net.phoenixvine.phantasia.utils.PhantasiaBlockInfo.fromBlockState(next));
                }
            } catch (Throwable ignored) {}
        }
        if (renderer != null) {
            renderer.clearOverlay();
            renderer.requestBake();
        }

        // For vanilla beacon blocks: vanilla tick resets beamSections every 80 game ticks.
        // Register a post-tick hook on the dummy world to re-force the beam state after each tick.
        level.clearPostTickHooks();
        for (java.util.Map.Entry<BlockPos, net.phoenixvine.phantasia.utils.PhantasiaBlockInfo> e : pattern.mergedBlockMap.entrySet()) {
            if (!e.getValue().getBlockState().is(Blocks.BEACON)) continue;
            final BlockPos bPos = e.getKey();
            final boolean beaconWorking = posWorking.getOrDefault(bPos, globalWorking);
            level.addPostTickHook(() -> {
                var be = level.blockEntities.get(bPos);
                if (!(be instanceof BeaconBlockEntity beacon)) return;
                forceBeaconSections(beacon, beaconWorking);
            });
            // Also apply immediately so the beam shows before the first tick.
            var be = level.getBlockEntity(bPos);
            if (be instanceof BeaconBlockEntity beacon) forceBeaconSections(beacon, beaconWorking);
        }
    }

    private static void forceBeaconSections(BeaconBlockEntity beacon, boolean active) {
        try {
            java.lang.reflect.Field f = BeaconBlockEntity.class.getDeclaredField("beamSections");
            f.setAccessible(true);
            Object current = f.get(beacon);
            java.util.ArrayList<BeaconBlockEntity.BeaconBeamSection> list;
            if (current instanceof java.util.ArrayList<?> al) {
                @SuppressWarnings("unchecked")
                java.util.ArrayList<BeaconBlockEntity.BeaconBeamSection> cast =
                        (java.util.ArrayList<BeaconBlockEntity.BeaconBeamSection>) al;
                list = cast;
            } else {
                list = new java.util.ArrayList<>();
                f.set(beacon, list);
            }
            list.clear();
            if (active) list.add(new BeaconBlockEntity.BeaconBeamSection(new float[]{1f, 1f, 1f}));
        } catch (Exception ignored) {}
    }

    private void applyWorldItemsToLevel(PhantasiaSceneData.StepData step) {
        if (step == null || level == null || pattern == null) return;
        java.util.Set<BlockPos> rebakePos = new java.util.HashSet<>();
        for (net.phoenixvine.phantasia.common.data.pattern.PhantasiaScenePattern.PlacementEntry pe : pattern.placements) {
            PhantasiaSceneData.MachineOverride ov = step.getOverride(pe.index);
            if (ov == null || ov.worldItems.isEmpty()) continue;
            for (net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData.WorldItemEntry wi : ov.worldItems) {
                BlockPos worldPos = new BlockPos(wi.x, wi.y, wi.z).offset(pe.offset);
                net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(worldPos);
                if (be == null) continue;
                if (be.getLevel() == null) be.setLevel(level);
                if (wi.sourceAmount >= 0) {
                    try {
                        if (be instanceof com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile jar) {
                            jar.setSource(wi.sourceAmount);
                            jar.setChanged();
                            rebakePos.add(worldPos);
                        }
                    } catch (Exception ignored) {}
                }
                if (wi.item != null && !wi.item.isBlank()) {
                    try {
                        net.minecraft.resources.ResourceLocation rl = new net.minecraft.resources.ResourceLocation(wi.item);
                        net.minecraft.world.item.Item itm = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
                        net.minecraft.world.item.ItemStack stack = (itm == null || itm == net.minecraft.world.item.Items.AIR)
                                ? net.minecraft.world.item.ItemStack.EMPTY : new net.minecraft.world.item.ItemStack(itm);
                        if (be instanceof net.minecraft.world.Container container) {
                            container.setItem(0, stack);
                            be.setChanged();
                            rebakePos.add(worldPos);
                        } else {
                            net.minecraft.world.item.ItemStack finalStack = stack;
                            be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER)
                                    .ifPresent(handler -> {
                                        if (handler.getSlots() > 0 && handler.isItemValid(0, finalStack)) {
                                            handler.insertItem(0, finalStack, false);
                                            be.setChanged();
                                            rebakePos.add(worldPos);
                                        }
                                    });
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        if (!rebakePos.isEmpty() && renderer != null) renderer.requestPartialBake(rebakePos);
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

        // Apply camera, visibility, and working states on step change
        int si = activeStepIndex();
        if (si != lastStepIndex) {
            lastStepIndex = si;
            applyVisibility();
            PhantasiaSceneData.StepData step = activeStep();
            if (step != null) {
                applyWorkingState(step);
                applyWorldItemsToLevel(step);
                if (step.camera != null && camera != null) {
                    float zoom = step.camera.zoom > 0 ? step.camera.zoom : camera.getZoom();
                    LerpType lt = LerpType.fromString(step.camera.lerpType);
                    camera.scriptDrive(step.camera.yaw, step.camera.pitch, zoom, lt,
                            step.camera.lerpTicks > 0 ? step.camera.lerpTicks : 20);
                }
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

        g.fill(0, 0, this.width, this.height, C_BG());

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
        if (showMistakes) renderMistakesOverlay(g);

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
                    g.fill(tx - 1, ty - 1, tx, ty + 11, C_ACCENT());
                    g.drawString(font, name, tx + 3, ty + 1, C_TEXT(), false);
                }
            } catch (Exception ignored) {}
        }
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private void renderTopBar(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, this.width, TOP_BAR_H, C_BAR());
        g.fill(0, TOP_BAR_H - 1, this.width, TOP_BAR_H, C_ACCENT());

        // Title centred
        String title = data.name != null && !data.name.isBlank() ? data.name : data.id;
        g.drawCenteredString(font, title, this.width / 2, (TOP_BAR_H - 8) / 2, C_ACCENT());

        // Left: Back
        topBtn(g, mx, my, 4, Component.translatable("screen.phantasia.scene_viewer.btn_back").getString(),
                this::onClose);

        // Right: Edit (admin only), then camera reset
        int rx = this.width - 4;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getAbilities().instabuild) {
            rx = topBtnRight(g, mx, my, rx,
                    Component.translatable("screen.phantasia.scene_viewer.btn_edit").getString(), this::openEditor);
        }
        // Show Guide button whenever there is guide content
        if (hasGuideContent()) {
            rx = topBtnRight(g, mx, my, rx,
                    Component.translatable("screen.phantasia.scene_viewer.btn_guide").getString(), this::openGuide);
        }
        // Show Mistakes button when the scene has mistakes defined
        if (data.mistakes != null && !data.mistakes.isEmpty()) {
            rx = topBtnRight(g, mx, my, rx, showMistakes ? "⚠ Hide Mistakes" : "⚠ Mistakes",
                    () -> showMistakes = !showMistakes);
        }
        topBtnRight(g, mx, my, rx, Component.translatable("screen.phantasia.scene_viewer.btn_center").getString(),
                this::centerCamera);
    }

    private void topBtn(GuiGraphics g, int mx, int my, int x, String label, Runnable action) {
        int w = font.width(label) + 10, h = TOP_BAR_H - 6;
        boolean hov = isOver(mx, my, x, 3, w, h);
        g.fill(x, 3, x + w, 3 + h, hov ? C_BTN_HOV() : C_BTN());
        if (hov) g.fill(x, 3, x + w, 4, C_ACCENT());
        g.drawString(font, label, x + 5, (TOP_BAR_H - 8) / 2, hov ? C_ACCENT() : C_TEXT(), false);
        btns.add(new Btn(x, 3, w, h, action));
    }

    private int topBtnRight(GuiGraphics g, int mx, int my, int rx, String label, Runnable action) {
        int w = font.width(label) + 10, h = TOP_BAR_H - 6;
        int x = rx - w;
        boolean hov = isOver(mx, my, x, 3, w, h);
        g.fill(x, 3, x + w, 3 + h, hov ? C_BTN_HOV() : C_BTN());
        if (hov) g.fill(x, 3, x + w, 4, C_ACCENT());
        g.drawString(font, label, x + 5, (TOP_BAR_H - 8) / 2, hov ? C_ACCENT() : C_TEXT(), false);
        btns.add(new Btn(x, 3, w, h, action));
        return x - 4;
    }

    // ── Timeline strip ────────────────────────────────────────────────────────

    private void renderTimeline(GuiGraphics g, int mx, int my) {
        int tlY = this.height - TIMELINE_H;
        int tx = 80, tw = this.width - tx - 70;
        int midY = tlY + TIMELINE_H / 2;

        g.fill(0, tlY, this.width, this.height, C_TL_BG());
        g.fill(0, tlY, this.width, tlY + 1, 0x33FFFFFF);

        // ▶/⏸ button
        int pbW = font.width(playing ? "⏸" : "▶") + 10;
        boolean pbHov = isOver(mx, my, 6, tlY + 4, pbW, TIMELINE_H - 8);
        g.fill(6, tlY + 4, 6 + pbW, tlY + TIMELINE_H - 4, pbHov ? C_BTN_HOV() : C_BTN());
        g.drawString(font, playing ? "⏸" : "▶", 6 + 5, tlY + (TIMELINE_H - 8) / 2 + 2,
                pbHov ? C_ACCENT() : C_TEXT(), false);
        btns.add(new Btn(6, tlY + 4, pbW, TIMELINE_H - 8, () -> {
            if (!playing && playbackTick >= totalTicks()) {
                playbackTick = 0;
                tickAccum = 0;
                lastStepIndex = -1;
            }
            playing = !playing;
        }));

        // Camera lock button (placed between play and speed, matching PhantasiaSceneScreen layout)
        int lkX = 6 + pbW + 4;
        String lockLabel = camera != null && camera.isLocked() ? "🔒" : "🔓";
        boolean lkHov = isOver(mx, my, lkX, tlY + 4, 18, TIMELINE_H - 8);
        net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.drawThemedBtn(g, font, lkX, tlY + 4, 18, TIMELINE_H - 8, lockLabel, lkHov, C_BTN());
        btns.add(new Btn(lkX, tlY + 4, 18, TIMELINE_H - 8, () -> { if (camera != null) camera.toggleLocked(); }));

        // Speed toggle
        String spdLabel = speed == 0.5f ? "½×" : speed == 2f ? "2×" : "1×";
        int spdX = lkX + 18 + 4;
        int spdW = font.width(spdLabel) + 8;
        boolean spdHov = isOver(mx, my, spdX, tlY + 4, spdW, TIMELINE_H - 8);
        g.fill(spdX, tlY + 4, spdX + spdW, tlY + TIMELINE_H - 4, spdHov ? C_BTN_HOV() : C_BTN());
        g.drawString(font, spdLabel, spdX + 4, tlY + (TIMELINE_H - 8) / 2 + 2,
                spdHov ? C_ACCENT() : C_DIM(), false);
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
        g.fill(tx, midY - 1, tx + (int) (tw * prog), midY + 1, C_PROG());
        int headX = tx + (int) (tw * prog);
        g.fill(headX - 3, midY - 5, headX + 3, midY + 5, C_ACCENT());

        // Time label
        g.drawString(font, formatTicks(playbackTick), tx + tw + 6, tlY + (TIMELINE_H - 8) / 2 + 2,
                C_DIM(), false);

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
            if (!camera.isLocked()) camera.orbit((float) dx * CAM_ORBIT, (float) dy * CAM_ORBIT);
            return true;
        }
        if (btn == 2 && isPanning) {
            if (!camera.isLocked()) {
                Vector3f right = new Vector3f(), up = new Vector3f();
                camera.getRightAndUp(right, up);
                float s = CAM_PAN;
                camera.pan(
                        (right.x * (float) -dx + up.x * (float) dy) * s,
                        (right.y * (float) -dx + up.y * (float) dy) * s,
                        (right.z * (float) -dx + up.z * (float) dy) * s);
            }
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
            if (!camera.isLocked()) camera.zoom(delta > 0 ? ZOOM_IN : ZOOM_OUT, 2f, 300f);
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

    private void renderMistakesOverlay(GuiGraphics g) {
        if (data.mistakes == null || data.mistakes.isEmpty()) return;
        int overlayX = 8, maxW = 260, lineH = font.lineHeight + 2;

        // Pre-compute wrapped lines for each mistake to size the background
        var allLines = new java.util.ArrayList<Pair<List<FormattedCharSequence>, Integer>>();
        for (var m : data.mistakes) {
            String prefix = "ERROR".equalsIgnoreCase(m.severity) ? "✖ " :
                    "INFO".equalsIgnoreCase(m.severity) ? "ℹ " : "⚠ ";
            String text = prefix + (m.description != null ? m.description : m.id);
            var lines = font.split(net.minecraft.network.chat.Component.literal(text), maxW - 4);
            int col = PhantasiaSceneData.SceneMistakeData.severityColor(m.severity);
            allLines.add(new Pair<>(lines, col));
        }

        int totalLines = allLines.stream().mapToInt(p -> p.getFirst().size()).sum();
        int ph = totalLines * lineH + (data.mistakes.size() - 1) * 4 + 10;

        int bgY = TOP_BAR_H + 4;
        g.fill(overlayX - 2, bgY - 2, overlayX + maxW, bgY + ph, 0xCC06060E);
        g.fill(overlayX - 2, bgY - 2, overlayX + maxW, bgY - 1, 0xFFFF5252);

        int y = bgY + 2;
        for (var entry : allLines) {
            var lines = entry.getFirst();
            int col = entry.getSecond();
            for (var line : lines) {
                g.drawString(font, line, overlayX + 2, y, col, false);
                y += lineH;
            }
            y += 4; // gap between mistakes
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
        net.phoenixvine.phantasia.client.render.PhantasiaParticleEngine.destroy();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String formatTicks(int t) {
        return String.format("%d.%02ds", t / 20, (t % 20) * 5);
    }
}
