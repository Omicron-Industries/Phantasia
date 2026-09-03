package net.phoenixvine.phantasia.compat.ie;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import java.util.Arrays;
import java.util.List;

public class IEMultiblockShape implements IPhantasiaMultiblockShape {

    private final List<StructureTemplate.StructureBlockInfo> structureBlocks;
    private PhantasiaBlockInfo[][][] cached;

    public IEMultiblockShape(List<StructureTemplate.StructureBlockInfo> structureBlocks) {
        this.structureBlocks = structureBlocks;
    }

    @Override
    public PhantasiaBlockInfo[][][] getBlocks() {
        if (cached != null) return cached;

        int maxX = 0, maxY = 0, maxZ = 0;
        for (StructureTemplate.StructureBlockInfo info : structureBlocks) {
            BlockPos p = info.pos();
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }
        int sx = maxX + 1, sy = maxY + 1, sz = maxZ + 1;

        PhantasiaBlockInfo[][][] grid = new PhantasiaBlockInfo[sx][sy][sz];
        for (PhantasiaBlockInfo[][] plane : grid)
            for (PhantasiaBlockInfo[] row : plane)
                Arrays.fill(row, PhantasiaBlockInfo.EMPTY);

        for (StructureTemplate.StructureBlockInfo info : structureBlocks) {
            if (info.state() == null) continue;
            BlockPos p = info.pos();
            if (p.getX() < sx && p.getY() < sy && p.getZ() < sz)
                grid[p.getX()][p.getY()][p.getZ()] = PhantasiaBlockInfo.fromBlockState(info.state());
        }

        cached = grid;
        return grid;
    }
}
