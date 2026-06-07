package net.phoenixvine.phantasia;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialRegistryEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.event.PostMaterialEvent;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.api.sound.SoundEntry;
import com.lowdragmc.lowdraglib.Platform;
import com.mojang.serialization.Codec;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
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
import net.phoenixvine.phantasia.common.PhantasiaTestMultiblocks;
// Make sure to import your generator class

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(Phantasia.MOD_ID)
@SuppressWarnings("removal")
public class Phantasia {

    public static final String MOD_ID = "phantasia";
    public static final Logger LOGGER = LogManager.getLogger();
    public static GTRegistrate PHANTASIA_REGISTRATE = GTRegistrate.create(Phantasia.MOD_ID);

    // --- Chunk Generator Registration ---
    private static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GEN_CODECS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, Phantasia.MOD_ID);

    public static final RegistryObject<Codec<PhantasiaVoidChunkGenerator>> VOID_GENERATOR =
            CHUNK_GEN_CODECS.register("void", () -> PhantasiaVoidChunkGenerator.CODEC);
    // -------------------------------------

    public Phantasia() {
        PhantasiaConfigs.init();
        PhantasiaDatagen.init();
        PHANTASIA_REGISTRATE.registerRegistrate();

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the chunk generator DeferredRegister to the mod event bus
        CHUNK_GEN_CODECS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        if (Platform.isClient()) {
            modEventBus.addListener(PhoenixKeybinds::register);
            PhantasiaClient.init(modEventBus);
        }
        modEventBus.addListener(this::clientSetup);

        modEventBus.addListener(this::addMaterialRegistries);
        modEventBus.addListener(this::addMaterials);
        modEventBus.addListener(this::modifyMaterials);

        modEventBus.addGenericListener(GTRecipeType.class, this::registerRecipeTypes);
        modEventBus.addGenericListener(MachineDefinition.class, this::registerMachines);
        modEventBus.addGenericListener(SoundEntry.class, this::registerSounds);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("Hello from common setup! This is *after* registries are done, so we can do this:");
            LOGGER.info("Look, I found a {}!", Items.DIAMOND);
        });
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Hey, we're on Minecraft version {}!", Minecraft.getInstance().getLaunchedVersion());
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    private void addMaterialRegistries(MaterialRegistryEvent event) {
        GTCEuAPI.materialManager.createRegistry(Phantasia.MOD_ID);
    }

    private void addMaterials(MaterialEvent event) {
        // CustomMaterials.init();
    }

    private void modifyMaterials(PostMaterialEvent event) {
        // CustomMaterials.modify();
    }

    private void registerRecipeTypes(GTCEuAPI.RegisterEvent<ResourceLocation, GTRecipeType> event) {
        // CustomRecipeTypes.init();
    }

    private void registerMachines(GTCEuAPI.RegisterEvent<ResourceLocation, MachineDefinition> event) {
        PhantasiaTestMultiblocks.init();
    }

    public void registerSounds(GTCEuAPI.RegisterEvent<ResourceLocation, SoundEntry> event) {
        // CustomSounds.init();
    }
}