package net.phoenixvine.phantasia.compat.tfc;

import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.common.multiblock.PhantasiaMultiblockRegistry;

public class PhantasiaTFCCompat {

    public static void init() {
        PhantasiaMultiblockRegistry.register(new TFCMultiblockProvider());
        Phantasia.LOGGER.info("[Phantasia] TerraFirmaCraft compat initialized.");
    }
}
