package net.phoenixvine.phantasia.compat.aae;

import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import java.util.function.Supplier;

public class AAESceneShape implements IPhantasiaMultiblockShape {

    private final Supplier<PhantasiaBlockInfo[][][]> supplier;
    private volatile PhantasiaBlockInfo[][][] cached;

    public AAESceneShape(Supplier<PhantasiaBlockInfo[][][]> supplier) {
        this.supplier = supplier;
    }

    @Override
    public PhantasiaBlockInfo[][][] getBlocks() {
        if (cached == null) cached = supplier.get();
        return cached;
    }
}
