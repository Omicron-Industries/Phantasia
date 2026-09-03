package net.phoenixvine.phantasia.compat.tfc;

import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import java.util.function.Supplier;

public class TFCSceneShape implements IPhantasiaMultiblockShape {

    private final Supplier<PhantasiaBlockInfo[][][]> factory;
    private volatile PhantasiaBlockInfo[][][] cached;

    public TFCSceneShape(Supplier<PhantasiaBlockInfo[][][]> factory) {
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
