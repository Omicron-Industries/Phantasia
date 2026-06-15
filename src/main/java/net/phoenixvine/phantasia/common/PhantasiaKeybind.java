package net.phoenixvine.phantasia.common;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.item.MetaMachineItem;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;

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
import net.phoenixvine.phantasia.configs.PhantasiaConfigs;

import com.mojang.blaze3d.platform.InputConstants;

@OnlyIn(Dist.CLIENT)
public class PhantasiaKeybind {

    private static int holdTimer = 0;
    private static int fadeTimer = 0;
    private static final int MAX_FADE_TICKS = 120; // 3 seconds
    private static final int FADE_OUT_TICKS = 40;  // 1 second actual fade
    private static MultiblockMachineDefinition lastDef = null;
    private static boolean wasLookingAtValid = false;

    // Refreshed every render frame by onItemTooltip while a tooltip is visible.
    private static MultiblockMachineDefinition tooltipTarget = null;

    // Generic item/block target set by onItemTooltip or block look-up (non-multiblock).
    private static String tooltipItemTarget = null;

    // -------------------------------------------------------------------------
    // KEY CHECK — raw GLFW, works even when a GUI is open
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
        if (stack.getItem() instanceof MetaMachineItem machineItem) {
            if (machineItem.getDefinition() instanceof MultiblockMachineDefinition multiDef &&
                    PhantasiaSceneSelectionScreen.PHANTASIA_SCENES.contains(multiDef)) {

                tooltipTarget = multiDef;

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

        // Generic item tooltip — check if any guide or scene lists this item in tooltipItems
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
    // CLIENT TICK — hold detection, runs regardless of open GUI
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        MultiblockMachineDefinition currentTarget = getTargetDefinition(mc);
        if (currentTarget == null) currentTarget = tooltipTarget;
        if (currentTarget == null) tooltipTarget = null;

        // Check for generic item/block target (non-multiblock)
        String currentItemTarget = getTargetItemId(mc);
        if (currentItemTarget == null) currentItemTarget = tooltipItemTarget;
        if (currentItemTarget == null) tooltipItemTarget = null;

        boolean hasAnyTarget = (currentTarget != null) || (currentItemTarget != null);

        if (hasAnyTarget && isPhantasiaKeyDown()) {
            holdTimer++;
            if (holdTimer >= PhantasiaConfigs.INSTANCE.phantasiaUI.activationTicks) {
                if (currentTarget != null) {
                    final MultiblockMachineDefinition defToOpen = currentTarget;
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

    private static void openForMultiblock(Minecraft mc, MultiblockMachineDefinition defToOpen) {
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
                mc.setScreen(new net.phoenixvine.phantasia.client.screens.PhantasiaSceneViewerScreen(null, matchingScenes.get(0)));
            } else {
                mc.setScreen(new net.phoenixvine.phantasia.client.screens.PhantasiaGuideScreen(null, matchingGuides.get(0)));
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
                mc.setScreen(new net.phoenixvine.phantasia.client.screens.PhantasiaSceneViewerScreen(null, matchingScenes.get(0)));
            } else {
                mc.setScreen(new net.phoenixvine.phantasia.client.screens.PhantasiaGuideScreen(null, matchingGuides.get(0)));
            }
        } else if (totalMatches > 1) {
            mc.setScreen(new PhantasiaContextualSelectionScreen(null, matchingScenes, matchingGuides, false));
        }
    }
    // -------------------------------------------------------------------------
    // OVERLAY — fade logic (original) + hotbar toast render
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() != VanillaGuiOverlay.PLAYER_LIST.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        MultiblockMachineDefinition lookedAt = getLookedAtDefinition(mc);

        // Sticky fade: reset to full whenever we look at a (possibly new) machine
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
                                             MultiblockMachineDefinition def,
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

        // Background
        g.fill(barX, barY, barX + barW, barY + barH, alphaBG | 0x08080F);
        // Bottom accent line
        g.fill(barX, barY + barH - 1, barX + barW, barY + barH, accent);

        // Progress fill
        float progress = (float) timer / Math.max(1, PhantasiaConfigs.INSTANCE.phantasiaUI.activationTicks);
        if (progress > 0) {
            g.fill(barX, barY + barH - 2,
                    barX + (int) (barW * Math.min(progress, 1.0f)), barY + barH,
                    (int) (0xAA * alphaMult) << 24 | 0x4FC3F7);
        }

        // Machine icon — only rendered while fully opaque (not fading).
        // renderItem() ignores GL alpha entirely, so we hide it the moment
        // the fade starts rather than letting it linger at full brightness
        // while everything else fades out.
        if (fade > FADE_OUT_TICKS) {
            g.renderItem(def.asStack(), barX + 6, barY + 4);
        }

        // Name
        String name = def.getLangValue();
        if (name == null || name.isEmpty() || name.contains(".")) {
            name = Component.translatable(def.getDescriptionId()).getString();
        }
        name = truncate(name, 120, mc);

        String keyName = PhoenixKeybinds.OPEN_PHANTASIA_MENU.getTranslatedKeyMessage().getString();

        g.drawString(mc.font, name,
                barX + 28, barY + 4, alphaText | 0xFFFFFF, false);
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

    private static MultiblockMachineDefinition getTargetDefinition(Minecraft mc) {
        ItemStack stack = mc.player.getMainHandItem();
        if (stack.getItem() instanceof MetaMachineItem machineItem) {
            if (machineItem.getDefinition() instanceof MultiblockMachineDefinition multiDef &&
                    PhantasiaSceneSelectionScreen.PHANTASIA_SCENES.contains(multiDef)) {
                return multiDef;
            }
        }
        return getLookedAtDefinition(mc);
    }

    /** Returns the registry ID of the held or looked-at item/block if it matches a tooltipItems entry. */
    private static String getTargetItemId(Minecraft mc) {
        // Check held item first
        ItemStack stack = mc.player.getMainHandItem();
        if (!stack.isEmpty() && !(stack.getItem() instanceof MetaMachineItem)) {
            ResourceLocation heldId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (heldId != null && hasTooltipItemMatch(heldId.toString())) {
                return heldId.toString();
            }
        }
        // Check looked-at block
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
            BlockState state = mc.level.getBlockState(pos);
            if (!(state.getBlock() instanceof MetaMachineBlock)) {
                ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
                if (blockId != null && hasTooltipItemMatch(blockId.toString())) {
                    return blockId.toString();
                }
            }
        }
        return null;
    }

    public static MultiblockMachineDefinition getLookedAtDefinition(Minecraft mc) {
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (state.getBlock() instanceof MetaMachineBlock machineBlock) {
            if (machineBlock.getDefinition() instanceof MultiblockMachineDefinition multiDef &&
                    PhantasiaSceneSelectionScreen.PHANTASIA_SCENES.contains(multiDef)) {
                return multiDef;
            }
        }
        return null;
    }
}
