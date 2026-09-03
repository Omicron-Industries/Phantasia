package net.phoenixvine.phantasia.compat.aae;

import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.common.multiblock.PhantasiaMultiblockRegistry;

public class PhantasiaAAECompat {

    public static void init() {
        PhantasiaMultiblockRegistry.register(new AAEMultiblockProvider());
        Phantasia.LOGGER.info("[Phantasia] Advanced AE compat initialized.");
    }
}
