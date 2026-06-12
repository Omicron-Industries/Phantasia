package net.phoenixvine.phantasia.client.event;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.client.screens.PhantasiaSceneScreen;
import net.phoenixvine.phantasia.common.PhantasiaKeybind;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptLoader;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScripts;
import net.phoenixvine.phantasia.common.world.PhantasiaDimension;
import net.phoenixvine.phantasia.common.world.PhantasiaSlotAllocator;
import net.phoenixvine.phantasia.common.world.PhantasiaSlotVersions;

import java.util.HashMap;
import java.util.Map;

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

    /**
     * On world join, pre-populate the Phantasia scene dimension with block data
     * for any machine whose slot is stale or missing. This runs in a daemon thread
     * so world load is not blocked; each cold write is submitted to the server
     * thread via {@link PhantasiaSceneScreen#coldPopulateDimensionSlot}.
     *
     * <p>
     * After this runs, the first screen open for each machine will find its
     * chunk already saved and take the fast warm-start path instead of
     * re-iterating shape.getBlocks().
     * </p>
     */
    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        Thread t = new Thread(() -> {
            // Small delay so the server level is fully available before we query it.
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                return;
            }

            for (var def : GTRegistries.MACHINES) {
                if (Thread.interrupted()) return;
                if (!(def instanceof MultiblockMachineDefinition multiDef)) continue;

                ResourceLocation id = multiDef.getId();

                var shapes = multiDef.getMatchingShapes();
                if (shapes == null || shapes.isEmpty()) continue;

                var shape = shapes.get(0);
                if (shape == null) continue;

                BlockPos origin = PhantasiaSlotAllocator.originFor(id);
                var script = PhantasiaScripts.get(multiDef);
                int shapeHash = PhantasiaSlotVersions.hashShape(shape.getBlocks());
                int scriptHash = PhantasiaSlotVersions.hashScript(script.getSourceData());

                // Already valid \u2014 chunk saved and hashes match. Nothing to do.
                if (PhantasiaSlotVersions.isValid(id, shapeHash, scriptHash, origin)) continue;

                Phantasia.LOGGER.info("[Phantasia] Pre-populating dimension slot for {}", id);

                // Build the block map off-thread (safe: read-only access to shape data).
                BlockInfo[][][] raw = shape.getBlocks();
                Map<BlockPos, BlockInfo> blockMap = new HashMap<>();
                for (int x = 0; x < raw.length; x++) {
                    for (int y = 0; y < raw[x].length; y++) {
                        for (int z = 0; z < raw[x][y].length; z++) {
                            BlockInfo info = raw[x][y][z];
                            if (info == null) continue;
                            var state = info.getBlockState();
                            if (state == null || state.isAir()) continue;
                            blockMap.put(origin.offset(x, y, z), info);
                        }
                    }
                }

                if (blockMap.isEmpty()) continue;

                // Submit the dimension write and version stamp to the server thread.
                final Map<BlockPos, BlockInfo> snapshot = Map.copyOf(blockMap);
                final int sh = shapeHash, sc = scriptHash;
                final ResourceLocation fid = id;
                final BlockPos fOrigin = origin;
                net.minecraft.client.Minecraft.getInstance().tell(() -> {
                    try {
                        PhantasiaSceneScreen.coldPopulateDimensionSlot(fid, snapshot);
                        PhantasiaSlotVersions.put(fid, sh, sc);
                        PhantasiaDimension.forceChunkLoad(fOrigin);
                    } catch (Exception e) {
                        Phantasia.LOGGER.warn("[Phantasia] Pre-population failed for {}: {}",
                                fid, e.getMessage());
                    }
                });
            }

            Phantasia.LOGGER.info("[Phantasia] Dimension pre-population scan complete");
        }, "phantasia-prepopulate");
        t.setDaemon(true);
        t.start();
    }
}
