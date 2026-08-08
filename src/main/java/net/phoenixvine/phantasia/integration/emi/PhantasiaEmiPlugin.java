package net.phoenixvine.phantasia.integration.emi;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;

import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;

@dev.emi.emi.api.EmiEntrypoint
@OnlyIn(Dist.CLIENT)
public class PhantasiaEmiPlugin implements EmiPlugin {

    public static final boolean EMI_PRESENT = ModList.get().isLoaded("emi");

    @Override
    public void register(EmiRegistry registry) {}
}
