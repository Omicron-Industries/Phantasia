package net.phoenixvine.phantasia.common;


import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;

/**
 * PhantasiaEmiPlugin
 *
 * Registers Phantasia as an EMI plugin. Currently used only to:
 *  1. Mark EMI as present via {@link #EMI_PRESENT} so other code can gate on it safely.
 *  2. Provide the entry point required by EMI's {@code @EmiEntrypoint} annotation.
 *
 * Recipe resolution for the Material Cost screen happens lazily inside
 * {@link net.phoenixvine.phantasia.client.screens.PhantasiaMaterialCostScreen}
 * via {@code EmiApi.getRecipeManager()} — no recipe categories or extra UI are
 * registered here.
 *
 * Registration: EMI discovers this class via the {@code @EmiEntrypoint} annotation.
 * No service file or mods.toml entry is needed for Forge 1.20.1 with EMI 1.1+.
 */
@dev.emi.emi.api.EmiEntrypoint
@OnlyIn(Dist.CLIENT)
public class PhantasiaEmiPlugin implements EmiPlugin {

    /**
     * True when EMI is present in the mod list.
     * Use this to guard any code that imports EMI classes so those classes are never
     * loaded on instances without EMI.
     *
     * Example usage in PhantasiaSceneScreen:
     * <pre>
     *   if (PhantasiaEmiPlugin.EMI_PRESENT) {
     *       Minecraft.getInstance().setScreen(new PhantasiaMaterialCostScreen(pattern, this));
     *   }
     * </pre>
     */
    public static final boolean EMI_PRESENT = ModList.get().isLoaded("emi");

    @Override
    public void register(EmiRegistry registry) {
        // No recipe categories or widgets registered — Phantasia uses EMI only as a
        // recipe-graph data source inside PhantasiaMaterialCostScreen.
    }
}