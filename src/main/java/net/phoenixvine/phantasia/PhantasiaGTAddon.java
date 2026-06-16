package net.phoenixvine.phantasia;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.data.recipes.FinishedRecipe;
import net.phoenixvine.phantasia.common.PhantasiaTestingRecipes;
import net.phoenixvine.phantasia.compat.gtceu.PhantasiaGTCompat;

import java.util.function.Consumer;

@SuppressWarnings("unused")
@GTAddon
public class PhantasiaGTAddon implements IGTAddon {

    @Override
    public GTRegistrate getRegistrate() {
        return PhantasiaGTCompat.PHANTASIA_REGISTRATE;
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
}
