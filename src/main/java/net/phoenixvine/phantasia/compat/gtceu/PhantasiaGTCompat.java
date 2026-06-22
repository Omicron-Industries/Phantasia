package net.phoenixvine.phantasia.compat.gtceu;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.DimensionMarker;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialRegistryEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.event.PostMaterialEvent;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.api.sound.SoundEntry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.IEventBus;
import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.common.PhantasiaTestMultiblocks;
import net.phoenixvine.phantasia.common.multiblock.PhantasiaMultiblockRegistry;

/**
 * All GTCEu-specific initialization for Phantasia.
 * Loaded only when GTCEu is present, via a {@code ModList} guard in {@link Phantasia}.
 */
public final class PhantasiaGTCompat {

    public static GTRegistrate PHANTASIA_REGISTRATE = GTRegistrate.create(Phantasia.MOD_ID);

    private PhantasiaGTCompat() {}

    public static void init(IEventBus modEventBus) {
        GTCEuBlockInspectHelper.register();
        PHANTASIA_REGISTRATE.registerRegistrate();
        net.phoenixvine.phantasia.datagen.PhantasiaDatagen.init();

        // Register our GTCEu provider so Phantasia can resolve GT machines
        PhantasiaMultiblockRegistry.register(new GTCEuMultiblockProvider());

        modEventBus.addListener(PhantasiaGTCompat::addMaterialRegistries);
        modEventBus.addListener(PhantasiaGTCompat::addMaterials);
        modEventBus.addListener(PhantasiaGTCompat::modifyMaterials);
        modEventBus.addGenericListener(GTRecipeType.class, PhantasiaGTCompat::registerRecipeTypes);
        modEventBus.addGenericListener(MachineDefinition.class, PhantasiaGTCompat::registerMachines);
        modEventBus.addGenericListener(SoundEntry.class, PhantasiaGTCompat::registerSounds);
        modEventBus.addGenericListener(DimensionMarker.class, PhantasiaGTCompat::registerDimensionMarkers);
    }

    private static void addMaterialRegistries(MaterialRegistryEvent event) {
        GTCEuAPI.materialManager.createRegistry(Phantasia.MOD_ID);
    }

    private static void addMaterials(MaterialEvent event) {}

    private static void modifyMaterials(PostMaterialEvent event) {}

    private static void registerRecipeTypes(GTCEuAPI.RegisterEvent<ResourceLocation, GTRecipeType> event) {}

    private static void registerMachines(GTCEuAPI.RegisterEvent<ResourceLocation, MachineDefinition> event) {
        PhantasiaTestMultiblocks.init();
    }

    private static void registerSounds(GTCEuAPI.RegisterEvent<ResourceLocation, SoundEntry> event) {}

    private static void registerDimensionMarkers(GTCEuAPI.RegisterEvent<ResourceLocation, DimensionMarker> event) {
        ResourceLocation sceneDimKey = new ResourceLocation(Phantasia.MOD_ID, "scene");
        DimensionMarker sceneMarker = new DimensionMarker(3, () -> Items.DIAMOND_BLOCK,
                "phantasia.dimension.scene.name");
        event.register(sceneDimKey, sceneMarker);

        ResourceLocation netherKey = new ResourceLocation("minecraft", "the_nether");
        DimensionMarker upgradedNetherMarker = new DimensionMarker(5, () -> Items.NETHERITE_BLOCK,
                "text.mymod.super_nether");
        GTRegistries.DIMENSION_MARKERS.registerOrOverride(netherKey, upgradedNetherMarker);
    }
}
