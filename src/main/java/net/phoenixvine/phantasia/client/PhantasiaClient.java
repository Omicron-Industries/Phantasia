package net.phoenixvine.phantasia.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.client.event.PhantasiaClientEvents;
import net.phoenixvine.phantasia.client.screens.*;
import net.phoenixvine.phantasia.common.PhantasiaKeybind;
import net.phoenixvine.phantasia.common.data.guides.PhantasiaGuideLoader;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneLoader;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptLoader;

@Mod.EventBusSubscriber(modid = Phantasia.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class PhantasiaClient {

    private PhantasiaClient() {}

    public static void init(IEventBus modBus) {
        MinecraftForge.EVENT_BUS.register(PhantasiaKeybind.class);
        MinecraftForge.EVENT_BUS.register(PhantasiaClientEvents.class);
    }

    public static class VocalVibrancyClientTick {

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
        }
    }

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            PhantasiaScriptLoader.discoverAndLoad();
            PhantasiaSceneLoader.load();
            PhantasiaGuideLoader.load(); // Added right after scene loader

            // Fix: Initialize shader manually using the active stable resource manager
            // This safely bypasses vanilla's early asset loop event that Oculus blocks.
            var resourceManager = Minecraft.getInstance().getResourceManager();

        });
    }

    private static void addPhantasiaMachine(com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition def) {
        if (!PhantasiaSceneSelectionScreen.PHANTASIA_SCENES.contains(def))
            PhantasiaSceneSelectionScreen.PHANTASIA_SCENES.add(def);
    }
}
