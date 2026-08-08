package net.phoenixvine.phantasia.compat.gtceu;

import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

public class GTCEuMultiblockShape implements IPhantasiaMultiblockShape {

    private final MultiblockShapeInfo shapeInfo;

    public GTCEuMultiblockShape(MultiblockShapeInfo shapeInfo) {
        this.shapeInfo = shapeInfo;
    }

    @Override
    public PhantasiaBlockInfo[][][] getBlocks() {
        BlockInfo[][][] src = shapeInfo.getBlocks();
        PhantasiaBlockInfo[][][] dst = new PhantasiaBlockInfo[src.length][][];
        for (int x = 0; x < src.length; x++) {
            dst[x] = new PhantasiaBlockInfo[src[x].length][];
            for (int y = 0; y < src[x].length; y++) {
                dst[x][y] = new PhantasiaBlockInfo[src[x][y].length];
                for (int z = 0; z < src[x][y].length; z++) {
                    BlockInfo bi = src[x][y][z];
                    dst[x][y][z] = bi != null ? PhantasiaBlockInfo.fromBlockState(bi.getBlockState()) :
                            PhantasiaBlockInfo.EMPTY;
                }
            }
        }
        return dst;
    }

    public MultiblockShapeInfo getShapeInfo() {
        return shapeInfo;
    }
}
