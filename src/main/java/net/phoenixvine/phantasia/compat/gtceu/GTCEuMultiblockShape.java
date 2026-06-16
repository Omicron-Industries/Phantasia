package net.phoenixvine.phantasia.compat.gtceu;

import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;

/** Wraps GTCEu's {@link MultiblockShapeInfo} as an {@link IPhantasiaMultiblockShape}. */
public class GTCEuMultiblockShape implements IPhantasiaMultiblockShape {

    private final MultiblockShapeInfo shapeInfo;

    public GTCEuMultiblockShape(MultiblockShapeInfo shapeInfo) {
        this.shapeInfo = shapeInfo;
    }

    @Override
    public BlockInfo[][][] getBlocks() {
        return shapeInfo.getBlocks();
    }

    public MultiblockShapeInfo getShapeInfo() {
        return shapeInfo;
    }
}
