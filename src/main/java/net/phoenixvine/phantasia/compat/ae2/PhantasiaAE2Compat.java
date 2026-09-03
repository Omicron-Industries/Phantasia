package net.phoenixvine.phantasia.compat.ae2;

import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.common.multiblock.PhantasiaMultiblockRegistry;

public class PhantasiaAE2Compat {

    public static void init() {
        PhantasiaMultiblockRegistry.register(new AE2MultiblockProvider());
        Phantasia.LOGGER.info("[Phantasia] Applied Energistics 2 compat initialized.");
    }
}
