package net.phoenixvine.phantasia.compat.ip;

import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.common.multiblock.PhantasiaMultiblockRegistry;

public final class PhantasiaIPCompat {

    private PhantasiaIPCompat() {}

    public static void init() {
        PhantasiaMultiblockRegistry.register(new IPMultiblockProvider());
        Phantasia.LOGGER.info("[Phantasia] Immersive Petroleum compat initialized.");
    }
}
