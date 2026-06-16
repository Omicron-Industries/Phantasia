package net.phoenixvine.phantasia;

import com.lowdragmc.lowdraglib.Platform;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.phoenixvine.phantasia.client.PhantasiaClient;
import net.phoenixvine.phantasia.client.keybind.PhoenixKeybinds;
import net.phoenixvine.phantasia.common.world.PhantasiaVoidChunkGenerator;
import net.phoenixvine.phantasia.configs.PhantasiaConfigs;
import net.phoenixvine.phantasia.datagen.PhantasiaDatagen;

import com.mojang.serialization.Codec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(Phantasia.MOD_ID)
@SuppressWarnings("removal")
public class Phantasia {

    public static final String MOD_ID = "phantasia";
    public static final Logger LOGGER = LogManager.getLogger();

    // ── Chunk Generator Registration ──────────────────────────────────────────

    private static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GEN_CODECS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, Phantasia.MOD_ID);

    public static final RegistryObject<Codec<PhantasiaVoidChunkGenerator>> VOID_GENERATOR =
            CHUNK_GEN_CODECS.register("void", () -> PhantasiaVoidChunkGenerator.CODEC);

    // ─────────────────────────────────────────────────────────────────────────

    public Phantasia() {
        PhantasiaConfigs.init();
        PhantasiaDatagen.init();

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        CHUNK_GEN_CODECS.register(modEventBus);
        net.phoenixvine.phantasia.common.PhantasiaItems.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        if (Platform.isClient()) {
            modEventBus.addListener(PhoenixKeybinds::register);
            PhantasiaClient.init(modEventBus);
        }
        modEventBus.addListener(this::clientSetup);

        // GTCEu integration — only loaded when the mod is present
        if (ModList.get().isLoaded("gtceu")) {
            net.phoenixvine.phantasia.compat.gtceu.PhantasiaGTCompat.init(modEventBus);
        }

        // Ars Nouveau integration — only loaded when the mod is present
        if (ModList.get().isLoaded("ars_nouveau")) {
            net.phoenixvine.phantasia.compat.arsnouveaucompat.PhantasiaArsCompat.init();
        }

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[Phantasia] Common setup complete.");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("[Phantasia] Client setup on Minecraft {}", Minecraft.getInstance().getLaunchedVersion());
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
