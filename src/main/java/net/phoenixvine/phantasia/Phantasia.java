package net.phoenixvine.phantasia;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.phoenixvine.phantasia.client.PhantasiaClient;
import net.phoenixvine.phantasia.client.keybind.PhoenixKeybinds;
import net.phoenixvine.phantasia.compat.arsnouveaucompat.PhantasiaArsCompat;
import net.phoenixvine.phantasia.configs.PhantasiaConfigs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(Phantasia.MOD_ID)
@SuppressWarnings("removal")
public class Phantasia {

    public static final String MOD_ID = "phantasia";
    public static final Logger LOGGER = LogManager.getLogger();

    public static net.phoenixvine.phantasia.compat.custom.PhantasiaCustomProvider CUSTOM_PROVIDER;

    public Phantasia() {
        PhantasiaConfigs.init();

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        net.phoenixvine.phantasia.common.PhantasiaItems.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            modEventBus.addListener(PhoenixKeybinds::register);
            PhantasiaClient.init(modEventBus);
        }
        modEventBus.addListener(this::clientSetup);

        // Vanilla multiblock structures (Beacon, Nether Portal, End Portal, etc.)
        net.phoenixvine.phantasia.common.multiblock.PhantasiaMultiblockRegistry.register(
                new net.phoenixvine.phantasia.compat.vanilla.VanillaMultiblockProvider());

        // Custom user-authored scenes — always registered; loads from phantasia/custom/ on world join
        CUSTOM_PROVIDER = new net.phoenixvine.phantasia.compat.custom.PhantasiaCustomProvider();
        net.phoenixvine.phantasia.common.multiblock.PhantasiaMultiblockRegistry.register(CUSTOM_PROVIDER);

        // GTCEu integration — only loaded when the mod is present
        if (ModList.get().isLoaded("gtceu")) {
            net.phoenixvine.phantasia.compat.gtceu.PhantasiaGTCompat.init(modEventBus);
        }

        // PhoenixCore integration — only loaded when the mod is present
        if (ModList.get().isLoaded("phoenixcore")) {
            net.phoenixvine.phantasia.compat.phoenixcore.PhoenixCoreBlockInspectHelper.register();
        }

        // Ars Nouveau integration — only loaded when the mod is present
        if (ModList.get().isLoaded("ars_nouveau")) {
            PhantasiaArsCompat.init();
        }

        // Immersive Engineering integration — only loaded when the mod is present
        if (ModList.get().isLoaded("immersiveengineering")) {
            net.phoenixvine.phantasia.compat.ie.PhantasiaIECompat.init();
        }

        // Applied Energistics 2 integration — only loaded when the mod is present
        if (ModList.get().isLoaded("ae2")) {
            net.phoenixvine.phantasia.compat.ae2.PhantasiaAE2Compat.init();
        }

        // TerraFirmaCraft integration — only loaded when the mod is present
        if (ModList.get().isLoaded("tfc")) {
            net.phoenixvine.phantasia.compat.tfc.PhantasiaTFCCompat.init();
        }

        // Extended AE integration — EAE's jar may be on the classpath without appearing in ModList
        // (some CurseForge builds lack a valid mods.toml). Use class-presence as the authoritative check.
        boolean eaeLoaded = false;
        try {
            Class.forName("com.glodblock.github.extendedae.ExtendedAE");
            eaeLoaded = true;
        } catch (ClassNotFoundException ignored) {}
        if (ModList.get().isLoaded("ae2") && eaeLoaded) {
            net.phoenixvine.phantasia.compat.eae.PhantasiaEAECompat.init();
        }

        // Create integration — only loaded when the mod is present
        if (ModList.get().isLoaded("create")) {
            net.phoenixvine.phantasia.compat.create.PhantasiaCreateCompat.init();
        }

        // Advanced AE integration — only loaded when the mod is present
        if (ModList.get().isLoaded("advanced_ae")) {
            net.phoenixvine.phantasia.compat.aae.PhantasiaAAECompat.init();
        }

        // Immersive Petroleum — registers via IE's MultiblockHandler, so requires IE too
        boolean ipLoaded = false;
        try {
            Class.forName("flaxbeard.immersivepetroleum.ImmersivePetroleum");
            ipLoaded = true;
        } catch (ClassNotFoundException ignored) {}
        if (ModList.get().isLoaded("immersiveengineering") && ipLoaded) {
            net.phoenixvine.phantasia.compat.ip.PhantasiaIPCompat.init();
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
