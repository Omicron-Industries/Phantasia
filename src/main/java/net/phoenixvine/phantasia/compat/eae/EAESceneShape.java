package net.phoenixvine.phantasia.compat.eae;

import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import java.util.function.Supplier;

public class EAESceneShape implements IPhantasiaMultiblockShape {

    private final Supplier<PhantasiaBlockInfo[][][]> supplier;
    private volatile PhantasiaBlockInfo[][][] cached;

    public EAESceneShape(Supplier<PhantasiaBlockInfo[][][]> supplier) {
        this.supplier = supplier;
    }

    @Override
    public PhantasiaBlockInfo[][][] getBlocks() {
        if (cached == null) cached = supplier.get();
        return cached;
    }
}
