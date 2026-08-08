package net.phoenixvine.phantasia.compat.arsnouveaucompat;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.common.multiblock.PhantasiaMultiblockRegistry;
import net.phoenixvine.phantasia.common.multisetup.PhantasiaMultiSetupRegistry;

public final class PhantasiaArsCompat {

    private static ArsNouveauSetupProvider provider;

    private PhantasiaArsCompat() {}

    public static void init() {
        provider = new ArsNouveauSetupProvider();
        PhantasiaMultiSetupRegistry.register(provider);
        PhantasiaMultiblockRegistry.register(new ArsNouveauMultiblockProvider());

        MinecraftForge.EVENT_BUS.addListener(PhantasiaArsCompat::onReload);

        Phantasia.LOGGER.info("[Phantasia] Ars Nouveau compat initialized.");
    }

    private static void onReload(AddReloadListenerEvent event) {
        if (provider != null) provider.invalidateAll();
    }
}
