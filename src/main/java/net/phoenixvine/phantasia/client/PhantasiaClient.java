package net.phoenixvine.phantasia.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.client.screens.*;
import net.phoenixvine.phantasia.common.PhantasiaKeybind;
import net.phoenixvine.phantasia.common.PhantasiaSceneLoader;
import net.phoenixvine.phantasia.common.PhantasiaScriptLoader;

@Mod.EventBusSubscriber(modid = Phantasia.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class PhantasiaClient {

    private PhantasiaClient() {}

    public static void init(IEventBus modBus) {
        MinecraftForge.EVENT_BUS.register(PhantasiaKeybind.class);
        MinecraftForge.EVENT_BUS.register(PhantasiaClientEvents.class);
    }

    // Inner static class keeps the tick handler co-located with the rest of
    // PhantasiaClient rather than scattering it into a separate file.
    // Registered on the FORGE event bus (not MOD bus) so it fires every game tick.
    public static class VocalVibrancyClientTick {

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            // Only tick at END to avoid running twice per tick (START + END both fire)
            if (event.phase != TickEvent.Phase.END) return;
        }
    }

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            PhantasiaScriptLoader.discoverAndLoad();
            PhantasiaSceneLoader.load();
        });
    }

    private static void addPhantasiaMachine(com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition def) {
        if (!PhantasiaSceneSelectionScreen.PHANTASIA_SCENES.contains(def))
            PhantasiaSceneSelectionScreen.PHANTASIA_SCENES.add(def);
    }
}
