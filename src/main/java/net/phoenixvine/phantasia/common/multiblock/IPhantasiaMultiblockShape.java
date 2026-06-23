package net.phoenixvine.phantasia.common.multiblock;

import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

/**
 * Mod-agnostic representation of a single multiblock shape/configuration.
 * GTCEu wraps {@code MultiblockShapeInfo} here; other mods provide their own impl.
 */
public interface IPhantasiaMultiblockShape {

    /** Raw block grid for this shape: [x][y][z]. Null entries are air. */
    PhantasiaBlockInfo[][][] getBlocks();
}
