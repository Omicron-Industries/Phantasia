package net.phoenixvine.phantasia.client;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.client.event.PhantasiaClientEvents;
import net.phoenixvine.phantasia.client.screens.*;
import net.phoenixvine.phantasia.client.web.PhantasiaWebExport;
import net.phoenixvine.phantasia.common.PhantasiaKeybind;
import net.phoenixvine.phantasia.common.data.guides.PhantasiaGuideLoader;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneLoader;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptLoader;
import net.phoenixvine.wiki.theme.PhoenixTheme;

@Mod.EventBusSubscriber(modid = Phantasia.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class PhantasiaClient {

    private PhantasiaClient() {}

    public static void init(IEventBus modBus) {
        MinecraftForge.EVENT_BUS.register(PhantasiaKeybind.class);
        MinecraftForge.EVENT_BUS.register(PhantasiaClientEvents.class);
        MinecraftForge.EVENT_BUS.register(PhantasiaClientCommands.class);
    }

    public static class VocalVibrancyClientTick {

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
        }
    }

    public static class PhantasiaClientCommands {

        @SubscribeEvent
        public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(
                    Commands.literal("phantasia")
                            .then(Commands.literal("editor")
                                    .executes(context -> {
                                        Minecraft.getInstance().tell(() -> {
                                            if (net.phoenixvine.phantasia.Phantasia.CUSTOM_PROVIDER == null) return;
                                            Minecraft.getInstance().setScreen(
                                                    new net.phoenixvine.phantasia.client.screens.editors.PhantasiaCustomEditorScreen(
                                                            net.phoenixvine.phantasia.Phantasia.CUSTOM_PROVIDER, null));
                                        });
                                        return 1;
                                    }))
                            .then(Commands.literal("theme")
                                    .executes(context -> {
                                        Minecraft.getInstance().tell(() -> {
                                            Minecraft.getInstance().setScreen(
                                                    new net.phoenixvine.wiki.theme.PhoenixThemeEditorScreen(null,
                                                            "Phantasia"));
                                        });
                                        return 1;
                                    }))
                            .then(Commands.literal("webexport")
                                    .executes(context -> {
                                        Minecraft.getInstance().tell(() -> {
                                            var player = Minecraft.getInstance().player;
                                            if (player == null) return;
                                            player.sendSystemMessage(
                                                    Component.translatable("ui.phantasia.web_export_starting"));
                                            try {
                                                var docsDir = FMLPaths.GAMEDIR.get().getParent().resolve("docs");
                                                var result = PhantasiaWebExport.export(docsDir);
                                                player.sendSystemMessage(Component.literal(
                                                        "[Phantasia] Export complete: " + result.scenes() +
                                                                " scenes, " +
                                                                result.scripts() + " scripts, " + result.guides() +
                                                                " guides, " +
                                                                result.blocks() + " blocks → " + docsDir));
                                                if (result.hasErrors())
                                                    player.sendSystemMessage(Component
                                                            .literal("[Phantasia] Warnings: " + result.errors()));
                                            } catch (Exception e) {
                                                player.sendSystemMessage(Component
                                                        .literal("[Phantasia] Export failed: " + e.getMessage()));
                                                Phantasia.LOGGER.error("[Phantasia] webexport failed", e);
                                            }
                                        });
                                        return 1;
                                    })));
        }
    }

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (net.phoenixvine.phantasia.Phantasia.CUSTOM_PROVIDER != null)
                net.phoenixvine.phantasia.Phantasia.CUSTOM_PROVIDER.reload();
            PhantasiaScriptLoader.discoverAndLoad();
            PhantasiaSceneLoader.load();
            PhantasiaGuideLoader.load();

            PhoenixTheme.loadThemes();

            var resourceManager = Minecraft.getInstance().getResourceManager();
        });
    }

    private static void addPhantasiaMachine(net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition def) {
        if (!PhantasiaSceneSelectionScreen.PHANTASIA_SCENES.contains(def))
            PhantasiaSceneSelectionScreen.PHANTASIA_SCENES.add(def);
    }
}
