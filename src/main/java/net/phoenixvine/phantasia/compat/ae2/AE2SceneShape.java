package net.phoenixvine.phantasia.compat.ae2;

import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import java.util.function.Supplier;

public class AE2SceneShape implements IPhantasiaMultiblockShape {

    private final Supplier<PhantasiaBlockInfo[][][]> factory;
    private volatile PhantasiaBlockInfo[][][] cached;

    public AE2SceneShape(Supplier<PhantasiaBlockInfo[][][]> factory) {
        this.factory = factory;
    }

    @Override
    public PhantasiaBlockInfo[][][] getBlocks() {
        if (cached == null) {
            cached = factory.get();
        }
        return cached;
    }
}
