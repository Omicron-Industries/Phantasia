package net.phoenixvine.phantasia.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.common.PhantasiaItems;
import net.phoenixvine.phantasia.common.PhantasiaKeybind;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptLoader;
import net.phoenixvine.phantasia.common.item.PhantasiaWandItem;

import com.mojang.blaze3d.vertex.PoseStack;

@Mod.EventBusSubscriber(modid = Phantasia.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PhantasiaClientEvents {

    private PhantasiaClientEvents() {}

    /**
     * Updated to .Pre to match PhantasiaKeybind's logic.
     * This allows the UI to render on the highest layer (PLAYER_LIST)
     * and ensures it stays above maps/other HUD elements.
     */
    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        PhantasiaKeybind.onRenderOverlay(event);
    }

    /**
     * Reset the lazy-reload flag when the player disconnects so that the next
     * world join re-runs the deferred script load. This matters if the player
     * connects to a server where different addons are loaded, or rejoins after
     * a /reload that re-registered machines.
     */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        PhantasiaScriptLoader.resetLazyReload();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        // Check both hands for the capture wand
        ItemStack wand = ItemStack.EMPTY;
        for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() == PhantasiaItems.CAPTURE_WAND.get()) {
                wand = held;
                break;
            }
        }
        if (wand.isEmpty()) return;

        BlockPos pos1 = PhantasiaWandItem.getPos1(wand);
        BlockPos pos2 = PhantasiaWandItem.getPos2(wand);
        if (pos1 == null && pos2 == null) return;

        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();

        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);

        MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();

        // Corner 1 marker — green
        if (pos1 != null) {
            renderBox(ps, source,
                    new AABB(pos1.getX(), pos1.getY(), pos1.getZ(),
                            pos1.getX() + 1, pos1.getY() + 1, pos1.getZ() + 1).inflate(0.003),
                    0f, 1f, 0f, 1f);
        }
        // Corner 2 marker — red
        if (pos2 != null) {
            renderBox(ps, source,
                    new AABB(pos2.getX(), pos2.getY(), pos2.getZ(),
                            pos2.getX() + 1, pos2.getY() + 1, pos2.getZ() + 1).inflate(0.003),
                    1f, 0f, 0f, 1f);
        }
        // Full selection — yellow, slightly expanded
        if (pos1 != null && pos2 != null) {
            int x0 = Math.min(pos1.getX(), pos2.getX()), x1 = Math.max(pos1.getX(), pos2.getX()) + 1;
            int y0 = Math.min(pos1.getY(), pos2.getY()), y1 = Math.max(pos1.getY(), pos2.getY()) + 1;
            int z0 = Math.min(pos1.getZ(), pos2.getZ()), z1 = Math.max(pos1.getZ(), pos2.getZ()) + 1;
            renderBox(ps, source, new AABB(x0, y0, z0, x1, y1, z1).inflate(0.006), 1f, 1f, 0f, 0.85f);
        }

        source.endBatch(RenderType.lines());

        ps.popPose();
    }

    private static void renderBox(PoseStack ps, MultiBufferSource.BufferSource src,
                                  AABB box, float r, float g, float b, float a) {
        net.minecraft.client.renderer.LevelRenderer.renderLineBox(
                ps, src.getBuffer(RenderType.lines()), box, r, g, b, a);
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.tell(() -> {
            String worldId;
            if (mc.isLocalServer() && mc.getSingleplayerServer() != null) {
                var server = mc.getSingleplayerServer();
                String name = server.getWorldData().getLevelName();
                long seed = server.overworld().getSeed();
                worldId = name + "_" + seed;
            } else if (mc.getCurrentServer() != null) {
                worldId = mc.getCurrentServer().ip;
            } else {
                worldId = "unknown";
            }
            PhantasiaWelcomeOverlay.checkFirstRun(worldId);
        });
    }
}
