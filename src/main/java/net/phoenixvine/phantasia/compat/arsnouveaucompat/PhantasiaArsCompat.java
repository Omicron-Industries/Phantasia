package net.phoenixvine.phantasia.compat.arsnouveaucompat;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.common.multiblock.PhantasiaMultiblockRegistry;
import net.phoenixvine.phantasia.common.multisetup.PhantasiaMultiSetupRegistry;

/**
 * All Ars Nouveau-specific initialization for Phantasia.
 * Loaded only when ars_nouveau is present, via a ModList guard in {@link Phantasia}.
 */
public final class PhantasiaArsCompat {

    private static ArsNouveauSetupProvider provider;

    private PhantasiaArsCompat() {}

    public static void init() {
        provider = new ArsNouveauSetupProvider();
        PhantasiaMultiSetupRegistry.register(provider);
        PhantasiaMultiblockRegistry.register(new ArsNouveauMultiblockProvider());

        // Flush recipe caches whenever datapacks reload so recipes stay in sync
        MinecraftForge.EVENT_BUS.addListener(PhantasiaArsCompat::onReload);

        Phantasia.LOGGER.info("[Phantasia] Ars Nouveau compat initialized.");
    }

    private static void onReload(AddReloadListenerEvent event) {
        if (provider != null) provider.invalidateAll();
    }
}
