package net.phoenixvine.phantasia.common;

import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.phantasia.Phantasia;

import java.util.function.Consumer;

import static com.gregtechceu.gtceu.common.data.GTMaterials.Air;

public class PhantasiaTestingRecipes {

    public static void init(Consumer<FinishedRecipe> provider) {
        GTRecipeTypes.GAS_COLLECTOR_RECIPES.recipeBuilder("air_from_scene")
                .circuitMeta(7)
                .outputFluids(Air.getFluid(10000))
                .dimension(new ResourceLocation(Phantasia.MOD_ID, "scene"))
                .duration(200)
                .EUt(16)
                .save(provider);
    }
}
