package net.phoenixvine.phantasia.compat.vanilla;

import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

public final class VanillaMultiblockShape implements IPhantasiaMultiblockShape {

    private final PhantasiaBlockInfo[][][] blocks;

    VanillaMultiblockShape(PhantasiaBlockInfo[][][] blocks) {
        this.blocks = blocks;
    }

    @Override
    public PhantasiaBlockInfo[][][] getBlocks() {
        return blocks;
    }
}
