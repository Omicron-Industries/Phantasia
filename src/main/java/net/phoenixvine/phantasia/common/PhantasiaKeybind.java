package net.phoenixvine.phantasia.common;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phantasia.client.keybind.PhoenixKeybinds;
import net.phoenixvine.phantasia.client.screens.PhantasiaContextualSelectionScreen;
import net.phoenixvine.phantasia.client.screens.PhantasiaSceneScreen;
import net.phoenixvine.phantasia.client.screens.PhantasiaSceneSelectionScreen;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.PhantasiaMultiblockRegistry;
import net.phoenixvine.phantasia.configs.PhantasiaConfigs;

import com.mojang.blaze3d.platform.InputConstants;

@OnlyIn(Dist.CLIENT)
public class PhantasiaKeybind {

    private static int holdTimer = 0;
    private static int fadeTimer = 0;
    private static final int MAX_FADE_TICKS = 120;
    private static final int FADE_OUT_TICKS = 40;
    private static IPhantasiaMultiblockDefinition lastDef = null;
    private static boolean wasLookingAtValid = false;

    private static IPhantasiaMultiblockDefinition tooltipTarget = null;
    private static String tooltipItemTarget = null;

    // -------------------------------------------------------------------------
    // KEY CHECK
    // -------------------------------------------------------------------------
    private static boolean isPhantasiaKeyDown() {
        Minecraft mc = Minecraft.getInstance();
        InputConstants.Key key = PhoenixKeybinds.OPEN_PHANTASIA_MENU.getKey();
        return switch (key.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
            case MOUSE -> org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), key.getValue()) ==
                    org.lwjgl.glfw.GLFW.GLFW_PRESS;
            default -> false;
        };
    }

    // -------------------------------------------------------------------------
    // TOOLTIP
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        var mode = PhantasiaConfigs.INSTANCE.phantasiaUI.displayMode;
        if (mode == PhantasiaConfigs.PhantasiaUIConfig.DisplayMode.JADE_ONLY ||
                mode == PhantasiaConfigs.PhantasiaUIConfig.DisplayMode.HOTBAR_ONLY)
            return;

        ItemStack stack = event.getItemStack();
        var defOpt = PhantasiaMultiblockRegistry.resolveFromItem(stack);
        if (defOpt.isPresent()) {
            IPhantasiaMultiblockDefinition def = defOpt.get();
            if (PhantasiaSceneSelectionScreen.PHANTASIA_SCENES.contains(def)) {
                tooltipTarget = def;
                String keyName = PhoenixKeybinds.OPEN_PHANTASIA_MENU.getTranslatedKeyMessage().getString();
                event.getToolTip().add(Component.translatable("tooltip.phantasia.hold_to_phantasize", keyName));
                if (holdTimer > 0) {
                    float progress = (float) holdTimer / PhantasiaConfigs.INSTANCE.phantasiaUI.activationTicks;
                    int barLen = 12;
                    int filled = (int) (barLen * progress);
                    event.getToolTip().add(Component.literal(
                            "  §b" + "▬".repeat(filled) + "§8" + "▬".repeat(barLen - filled)));
                }
                return;
            }
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId != null) {
            String itemKey = itemId.toString();
            boolean matched = hasTooltipItemMatch(itemKey);
            if (matched) {
                tooltipItemTarget = itemKey;
                String keyName = PhoenixKeybinds.OPEN_PHANTASIA_MENU.getTranslatedKeyMessage().getString();
                event.getToolTip().add(Component.translatable("tooltip.phantasia.hold_to_phantasize", keyName));
                if (holdTimer > 0) {
                    float progress = (float) holdTimer / PhantasiaConfigs.INSTANCE.phantasiaUI.activationTicks;
                    int barLen = 12;
                    int filled = (int) (barLen * progress);
                    event.getToolTip().add(Component.literal(
                            "  §b" + "▬".repeat(filled) + "§8" + "▬".repeat(barLen - filled)));
                }
            }
        }
    }

    private static boolean hasTooltipItemMatch(String itemKey) {
        for (var scene : net.phoenixvine.phantasia.common.data.scene.PhantasiaScenes.all()) {
            if (scene.tooltipItems != null && scene.tooltipItems.contains(itemKey)) return true;
        }
        for (var guide : net.phoenixvine.phantasia.common.data.guides.PhantasiaGuideRegistry.all()) {
            if (guide.tooltipItems != null && guide.tooltipItems.contains(itemKey)) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // CLIENT TICK
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        String currentItemTarget = getTargetItemId(mc);
        if (currentItemTarget == null) currentItemTarget = tooltipItemTarget;
        if (currentItemTarget == null) tooltipItemTarget = null;

        IPhantasiaMultiblockDefinition currentTarget = null;
        if (currentItemTarget == null) {
            currentTarget = getTargetDefinition(mc);
            if (currentTarget == null) currentTarget = tooltipTarget;
            if (currentTarget == null) tooltipTarget = null;
        } else {
            tooltipTarget = null;
        }

        boolean hasAnyTarget = (currentTarget != null) || (currentItemTarget != null);

        if (hasAnyTarget && isPhantasiaKeyDown()) {
            holdTimer++;
            if (holdTimer >= PhantasiaConfigs.INSTANCE.phantasiaUI.activationTicks) {
                if (currentTarget != null) {
                    final IPhantasiaMultiblockDefinition defToOpen = currentTarget;
                    mc.tell(() -> openForMultiblock(mc, defToOpen));
                } else {
                    final String itemKey = currentItemTarget;
                    mc.tell(() -> openForItemKey(mc, itemKey));
                }
                holdTimer = 0;
                fadeTimer = 0;
                tooltipTarget = null;
                tooltipItemTarget = null;
            }
        } else {
            holdTimer = Math.max(0, holdTimer - 2);
        }
    }

    private static void openForMultiblock(Minecraft mc, IPhantasiaMultiblockDefinition defToOpen) {
        String targetId = defToOpen.getId().getPath().toLowerCase(java.util.Locale.ROOT);
        java.util.List<net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData> matchingScenes = new java.util.ArrayList<>();
        for (var scene : net.phoenixvine.phantasia.common.data.scene.PhantasiaScenes.all()) {
            if (scene.id != null && scene.id.toLowerCase(java.util.Locale.ROOT).contains(targetId)) {
                matchingScenes.add(scene);
            }
        }
        java.util.List<net.phoenixvine.phantasia.common.data.guides.PhantasiaGuideData> matchingGuides = new java.util.ArrayList<>();
        for (var guide : net.phoenixvine.phantasia.common.data.guides.PhantasiaGuideRegistry.all()) {
            if (guide.id != null && guide.id.toLowerCase(java.util.Locale.ROOT).contains(targetId)) {
                matchingGuides.add(guide);
            }
        }
        boolean hasScript = PhantasiaSceneSelectionScreen.PHANTASIA_SCENES.contains(defToOpen);
        int totalMatches = (hasScript ? 1 : 0) + matchingScenes.size() + matchingGuides.size();
        if (totalMatches == 1) {
            if (hasScript) {
                mc.setScreen(new PhantasiaSceneScreen(defToOpen, null));
            } else if (!matchingScenes.isEmpty()) {
                mc.setScreen(new net.phoenixvine.phantasia.client.screens.PhantasiaSceneViewerScreen(null,
                        matchingScenes.get(0)));
            } else {
                mc.setScreen(
                        new net.phoenixvine.phantasia.client.screens.PhantasiaGuideScreen(null, matchingGuides.get(0)));
            }
        } else if (totalMatches > 1) {
            mc.setScreen(new PhantasiaContextualSelectionScreen(defToOpen, matchingScenes, matchingGuides, hasScript));
        }
    }

    private static void openForItemKey(Minecraft mc, String itemKey) {
        java.util.List<net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData> matchingScenes = new java.util.ArrayList<>();
        for (var scene : net.phoenixvine.phantasia.common.data.scene.PhantasiaScenes.all()) {
            if (scene.tooltipItems != null && scene.tooltipItems.contains(itemKey)) {
                matchingScenes.add(scene);
            }
        }
        java.util.List<net.phoenixvine.phantasia.common.data.guides.PhantasiaGuideData> matchingGuides = new java.util.ArrayList<>();
        for (var guide : net.phoenixvine.phantasia.common.data.guides.PhantasiaGuideRegistry.all()) {
            if (guide.tooltipItems != null && guide.tooltipItems.contains(itemKey)) {
                matchingGuides.add(guide);
            }
        }
        int totalMatches = matchingScenes.size() + matchingGuides.size();
        if (totalMatches == 1) {
            if (!matchingScenes.isEmpty()) {
                mc.setScreen(new net.phoenixvine.phantasia.client.screens.PhantasiaSceneViewerScreen(null,
                        matchingScenes.get(0)));
            } else {
                mc.setScreen(
                        new net.phoenixvine.phantasia.client.screens.PhantasiaGuideScreen(null, matchingGuides.get(0)));
            }
        } else if (totalMatches > 1) {
            mc.setScreen(new PhantasiaContextualSelectionScreen(null, matchingScenes, matchingGuides, false));
        }
    }

    // -------------------------------------------------------------------------
    // OVERLAY
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() != VanillaGuiOverlay.PLAYER_LIST.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        IPhantasiaMultiblockDefinition lookedAt = getLookedAtDefinition(mc);

        if (lookedAt != null) {
            if (lookedAt != lastDef || !wasLookingAtValid) fadeTimer = MAX_FADE_TICKS;
            lastDef = lookedAt;
            wasLookingAtValid = true;
        } else {
            wasLookingAtValid = false;
        }
        if (fadeTimer > 0) fadeTimer--;

        var mode = PhantasiaConfigs.INSTANCE.phantasiaUI.displayMode;
        boolean canShowHotbar = (mode == PhantasiaConfigs.PhantasiaUIConfig.DisplayMode.HOTBAR_ONLY ||
                mode == PhantasiaConfigs.PhantasiaUIConfig.DisplayMode.TOOLTIP_HOTBAR);

        if (canShowHotbar && fadeTimer > 0 && lastDef != null && mc.screen == null) {
            renderPhantasiaToast(event.getGuiGraphics(), mc, lastDef, holdTimer, fadeTimer);
        }
    }

    // -------------------------------------------------------------------------
    // TOAST RENDERING
    // -------------------------------------------------------------------------
    private static void renderPhantasiaToast(GuiGraphics g, Minecraft mc,
                                             IPhantasiaMultiblockDefinition def,
                                             int timer, int fade) {
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        float alphaMult = (fade <= FADE_OUT_TICKS) ? (float) fade / FADE_OUT_TICKS : 1.0f;

        int alphaBG = (int) (0x88 * alphaMult) << 24;
        int alphaText = (int) (0xFF * alphaMult) << 24;
        int accent = (int) (0xFF * alphaMult) << 24 | 0x4FC3F7;

        int barH = 28;
        int barW = 160;
        int barX = (screenW - barW) / 2;
        int barY = screenH - 68;

        g.pose().pushPose();
        g.pose().translate(0, 0, 500);

        g.fill(barX, barY, barX + barW, barY + barH, alphaBG | 0x08080F);
        g.fill(barX, barY + barH - 1, barX + barW, barY + barH, accent);

        float progress = (float) timer / Math.max(1, PhantasiaConfigs.INSTANCE.phantasiaUI.activationTicks);
        if (progress > 0) {
            g.fill(barX, barY + barH - 2,
                    barX + (int) (barW * Math.min(progress, 1.0f)), barY + barH,
                    (int) (0xAA * alphaMult) << 24 | 0x4FC3F7);
        }

        if (fade > FADE_OUT_TICKS) {
            g.renderItem(def.getIcon(), barX + 6, barY + 4);
        }

        String name = truncate(def.getDisplayName(), 120, mc);
        String keyName = PhoenixKeybinds.OPEN_PHANTASIA_MENU.getTranslatedKeyMessage().getString();

        g.drawString(mc.font, name, barX + 28, barY + 4, alphaText | 0xFFFFFF, false);
        g.drawString(mc.font, "§b[" + keyName + "] §7to Phantasize",
                barX + 28, barY + 15, alphaText | 0xBBBBBB, false);

        g.pose().popPose();
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------
    private static String truncate(String s, int maxPx, Minecraft mc) {
        if (mc.font.width(s) <= maxPx) return s;
        String e = "...";
        while (mc.font.width(s + e) > maxPx && s.length() > 1) {
            s = s.substring(0, s.length() - 1);
        }
        return s + e;
    }

    private static IPhantasiaMultiblockDefinition getTargetDefinition(Minecraft mc) {
        ItemStack stack = mc.player.getMainHandItem();
        var fromItem = PhantasiaMultiblockRegistry.resolveFromItem(stack);
        if (fromItem.isPresent() && PhantasiaSceneSelectionScreen.PHANTASIA_SCENES.contains(fromItem.get())) {
            return fromItem.get();
        }
        return getLookedAtDefinition(mc);
    }

    private static String getTargetItemId(Minecraft mc) {
        ItemStack stack = mc.player.getMainHandItem();
        if (!stack.isEmpty() && PhantasiaMultiblockRegistry.resolveFromItem(stack).isEmpty()) {
            ResourceLocation heldId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (heldId != null && hasTooltipItemMatch(heldId.toString())) {
                return heldId.toString();
            }
        }
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
            BlockState state = mc.level.getBlockState(pos);
            if (!PhantasiaMultiblockRegistry.isControllerBlock(state)) {
                ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
                if (blockId != null && hasTooltipItemMatch(blockId.toString())) {
                    return blockId.toString();
                }
            }
        }
        return null;
    }

    public static IPhantasiaMultiblockDefinition getLookedAtDefinition(Minecraft mc) {
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (!PhantasiaMultiblockRegistry.isControllerBlock(state)) return null;
        return PhantasiaMultiblockRegistry.resolveFromBlock(state)
                .filter(PhantasiaSceneSelectionScreen.PHANTASIA_SCENES::contains)
                .orElse(null);
    }
}
