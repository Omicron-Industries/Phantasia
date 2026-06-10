package net.phoenixvine.phantasia;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.data.DimensionMarker;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.phoenixvine.phantasia.common.PhantasiaTestingRecipes;

import java.util.function.Consumer;

@SuppressWarnings("unused")
@GTAddon
public class PhantasiaGTAddon implements IGTAddon {

    @Override
    public GTRegistrate getRegistrate() {
        return Phantasia.PHANTASIA_REGISTRATE;
    }

    @Override
    public void initializeAddon() {}

    @Override
    public String addonModId() {
        return Phantasia.MOD_ID;
    }

    @Override
    public void registerTagPrefixes() {
        // CustomTagPrefixes.init();
    }

    @Override
    public void addRecipes(Consumer<FinishedRecipe> provider) {
        PhantasiaTestingRecipes.init(provider);
    }

    @Override
    public void registerElements() {
        // CustomElements.init();
    }

    // --- Helper Methods for Dimension Markers ---

    private void registerCustomMarkers() {
        // Updated to use your dimension "phantasia:scene"
        ResourceLocation myDimKey = new ResourceLocation(Phantasia.MOD_ID, "scene");

        DimensionMarker customMarker = new DimensionMarker(
                3, // Tier
                () -> Items.DIAMOND_BLOCK,
                "phantasia.dimension.scene.name");

        // Registering the marker to GregTech's registry
        GTRegistries.DIMENSION_MARKERS.register(myDimKey, customMarker);
    }

    private void modifyExistingMarker() {
        ResourceLocation netherKey = new ResourceLocation("minecraft", "the_nether");

        DimensionMarker upgradedNetherMarker = new DimensionMarker(
                5, // Tier (Fixed syntax error here)
                () -> Items.NETHERITE_BLOCK,
                "text.mymod.super_nether");

        GTRegistries.DIMENSION_MARKERS.register(netherKey, upgradedNetherMarker);
    }

    // If you have custom ingredient types, uncomment this & change to match your capability.
    // KubeJS WILL REMOVE YOUR RECIPES IF THESE ARE NOT REGISTERED.
    /*
     * public static final ContentJS<Double> PRESSURE_IN = new ContentJS<>(NumberComponent.ANY_DOUBLE,
     * CustomRecipeCapabilities.PRESSURE, false);
     * public static final ContentJS<Double> PRESSURE_OUT = new ContentJS<>(NumberComponent.ANY_DOUBLE,
     * CustomRecipeCapabilities.PRESSURE, true);
     * * @Override
     * public void registerRecipeKeys(KJSRecipeKeyEvent event) {
     * event.registerKey(CustomRecipeCapabilities.PRESSURE, Pair.of(PRESSURE_IN, PRESSURE_OUT));
     * }
     */
}
