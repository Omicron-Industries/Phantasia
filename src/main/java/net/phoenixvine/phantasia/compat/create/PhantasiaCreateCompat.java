package net.phoenixvine.phantasia.compat.create;

import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.common.multiblock.PhantasiaMultiblockRegistry;

public class PhantasiaCreateCompat {

    public static void init() {
        PhantasiaMultiblockRegistry.register(new CreateMultiblockProvider());
        Phantasia.LOGGER.info("[Phantasia] Create compat initialized.");
    }
}
