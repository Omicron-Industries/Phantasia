package net.phoenixvine.phantasia.compat.eae;

import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.common.multiblock.PhantasiaMultiblockRegistry;

public class PhantasiaEAECompat {

    public static void init() {
        PhantasiaMultiblockRegistry.register(new EAEMultiblockProvider());
        Phantasia.LOGGER.info("[Phantasia] Extended AE compat initialized.");
    }
}
