package net.phoenixvine.phantasia.compat.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import java.util.Arrays;
import java.util.Map;

public class PhantasiaCustomShape implements IPhantasiaMultiblockShape {

    private final PhantasiaCustomLayout layout;
    private PhantasiaBlockInfo[][][] cached;

    public PhantasiaCustomShape(PhantasiaCustomLayout layout) {
        this.layout = layout;
    }

    public void invalidate() {
        cached = null;
    }

    @Override
    public PhantasiaBlockInfo[][][] getBlocks() {
        if (cached != null) return cached;

        int sx = layout.maxX() + 1;
        int sy = layout.maxY() + 1;
        int sz = layout.maxZ() + 1;
        if (sx == 0 || sy == 0 || sz == 0) {
            cached = new PhantasiaBlockInfo[1][1][1];
            cached[0][0][0] = PhantasiaBlockInfo.EMPTY;
            return cached;
        }

        PhantasiaBlockInfo[][][] grid = new PhantasiaBlockInfo[sx][sy][sz];
        for (PhantasiaBlockInfo[][] plane : grid)
            for (PhantasiaBlockInfo[] row : plane)
                Arrays.fill(row, PhantasiaBlockInfo.EMPTY);

        for (Map.Entry<BlockPos, String> e : layout.blocks.entrySet()) {
            BlockPos p = e.getKey();
            BlockState state = PhantasiaCustomLayout.parseBlockState(e.getValue());
            if (!state.isAir() && p.getX() < sx && p.getY() < sy && p.getZ() < sz)
                grid[p.getX()][p.getY()][p.getZ()] = PhantasiaBlockInfo.fromBlockState(state);
        }

        cached = grid;
        return cached;
    }
}
