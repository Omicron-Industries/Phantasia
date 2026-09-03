package net.phoenixvine.phantasia.compat.ie;

import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.common.multiblock.PhantasiaMultiblockRegistry;

public final class PhantasiaIECompat {

    private PhantasiaIECompat() {}

    public static void init() {
        PhantasiaMultiblockRegistry.register(new IEMultiblockProvider());
        Phantasia.LOGGER.info("[Phantasia] Immersive Engineering compat initialized.");
    }
}
