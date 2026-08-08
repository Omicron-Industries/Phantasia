package net.phoenixvine.phantasia.compat.arsnouveaucompat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import com.hollingsworth.arsnouveau.common.block.RitualBrazierBlock;
import com.hollingsworth.arsnouveau.setup.registry.BlockRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ArsNouveauLayoutBuilder {

    private static final int[][] PEDESTAL_OFFSETS_4 = {
            { -2, 0, 0 }, { 2, 0, 0 },
            { 0, 0, -2 }, { 0, 0, 2 }
    };
    private static final int[][] PEDESTAL_OFFSETS_8 = {
            { -2, 0, 0 }, { 2, 0, 0 },
            { 0, 0, -2 }, { 0, 0, 2 },
            { -2, 0, -2 }, { -2, 0, 2 },
            { 2, 0, -2 }, { 2, 0, 2 }
    };

    private static final int CX = 2, CY = 1, CZ = 2;

    private static final int APP_Y = 2;
    private static final int W = 5, H = 3, D = 5;

    static PhantasiaBlockInfo[][][] enchantingApparatusBase(int pedestalCount) {
        PhantasiaBlockInfo[][][] grid = emptyGrid();
        grid[CX][CY][CZ] = PhantasiaBlockInfo.fromBlock(BlockRegistry.ARCANE_CORE_BLOCK.get());
        grid[CX][APP_Y][CZ] = PhantasiaBlockInfo.fromBlock(BlockRegistry.ENCHANTING_APP_BLOCK.get());

        int[][] offsets = pedestalCount <= 4 ? PEDESTAL_OFFSETS_4 : PEDESTAL_OFFSETS_8;
        int count = Math.min(pedestalCount, offsets.length);
        for (int i = 0; i < count; i++) {
            int px = CX + offsets[i][0];
            int py = CY + offsets[i][1];
            int pz = CZ + offsets[i][2];
            grid[px][py][pz] = PhantasiaBlockInfo.fromBlock(BlockRegistry.ARCANE_PEDESTAL.get());
        }
        return grid;
    }

    static LayoutResult enchantingApparatusRecipe(
                                                  List<ItemStack> pedestalItems, ItemStack reagent) {
        int count = pedestalItems.size();
        PhantasiaBlockInfo[][][] grid = enchantingApparatusBase(count);

        Map<BlockPos, ItemStack> placements = new LinkedHashMap<>();

        placements.put(new BlockPos(CX, APP_Y, CZ), reagent.copy());

        int[][] offsets = count <= 4 ? PEDESTAL_OFFSETS_4 : PEDESTAL_OFFSETS_8;
        for (int i = 0; i < Math.min(count, offsets.length); i++) {
            BlockPos pos = new BlockPos(CX + offsets[i][0], CY, CZ + offsets[i][2]);
            placements.put(pos, pedestalItems.get(i).copy());
        }

        return new LayoutResult(grid, placements);
    }

    static PhantasiaBlockInfo[][][] imbuementChamberBase() {
        PhantasiaBlockInfo[][][] grid = emptyGrid();
        grid[CX][CY][CZ] = PhantasiaBlockInfo.fromBlock(BlockRegistry.IMBUEMENT_BLOCK.get());
        for (int[] off : PEDESTAL_OFFSETS_4) {
            grid[CX + off[0]][CY][CZ + off[2]] = PhantasiaBlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        }
        return grid;
    }

    static LayoutResult imbuementRecipe(ItemStack input) {
        PhantasiaBlockInfo[][][] grid = imbuementChamberBase();
        Map<BlockPos, ItemStack> placements = new LinkedHashMap<>();
        placements.put(new BlockPos(CX, CY, CZ), input.copy());
        return new LayoutResult(grid, placements);
    }

    static PhantasiaBlockInfo[][][] spellTurretBase(net.minecraft.world.level.block.Block turretBlock) {
        PhantasiaBlockInfo[][][] grid = emptyGrid();
        grid[CX][CY][CZ] = PhantasiaBlockInfo.fromBlock(turretBlock);
        for (int[] off : PEDESTAL_OFFSETS_4) {
            grid[CX + off[0]][CY][CZ + off[2]] = PhantasiaBlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        }

        grid[CX][CY][CZ + 2] = PhantasiaBlockInfo.fromBlock(Blocks.CHEST);
        return grid;
    }

    static PhantasiaBlockInfo[][][] wixieCauldronBase() {
        PhantasiaBlockInfo[][][] grid = emptyGrid();
        grid[CX][CY][CZ] = PhantasiaBlockInfo.fromBlock(BlockRegistry.WIXIE_CAULDRON.get());

        grid[CX - 1][CY][CZ] = PhantasiaBlockInfo.fromBlock(BlockRegistry.ARCANE_PEDESTAL.get());
        grid[CX + 1][CY][CZ] = PhantasiaBlockInfo.fromBlock(BlockRegistry.ARCANE_PEDESTAL.get());
        grid[CX][CY][CZ - 1] = PhantasiaBlockInfo.fromBlock(BlockRegistry.ARCANE_PEDESTAL.get());
        grid[CX][CY][CZ + 1] = PhantasiaBlockInfo.fromBlock(BlockRegistry.ARCANE_PEDESTAL.get());

        for (int[] off : PEDESTAL_OFFSETS_4) {
            grid[CX + off[0]][CY][CZ + off[2]] = PhantasiaBlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        }

        grid[CX - 2][CY][CZ - 2] = PhantasiaBlockInfo.fromBlock(BlockRegistry.POTION_JAR.get());
        return grid;
    }

    static PhantasiaBlockInfo[][][] drygmyBase() {
        PhantasiaBlockInfo[][][] grid = new PhantasiaBlockInfo[W][H][D];

        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++)
                grid[x][0][z] = PhantasiaBlockInfo.fromBlock(Blocks.GRASS_BLOCK);

        grid[CX][0][CZ] = PhantasiaBlockInfo.fromBlock(Blocks.MOSSY_COBBLESTONE);

        grid[CX][CY][CZ] = PhantasiaBlockInfo.fromBlock(BlockRegistry.DRYGMY_BLOCK.get());

        for (int[] off : PEDESTAL_OFFSETS_4) {
            grid[CX + off[0]][CY][CZ + off[2]] = PhantasiaBlockInfo.fromBlock(BlockRegistry.MOB_JAR.get());
        }
        return grid;
    }

    static PhantasiaBlockInfo[][][] whirlisprigBase() {
        PhantasiaBlockInfo[][][] grid = new PhantasiaBlockInfo[W][H][D];

        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++)
                grid[x][0][z] = PhantasiaBlockInfo.fromBlock(Blocks.GRASS_BLOCK);

        grid[CX][CY][CZ] = PhantasiaBlockInfo.fromBlock(BlockRegistry.WHIRLISPRIG_FLOWER.get());

        grid[CX - 2][CY][CZ] = PhantasiaBlockInfo.fromBlock(Blocks.OAK_LOG);
        grid[CX + 2][CY][CZ] = PhantasiaBlockInfo.fromBlock(Blocks.DANDELION);
        grid[CX][CY][CZ - 2] = PhantasiaBlockInfo.fromBlock(Blocks.POPPY);

        grid[CX][CY][CZ + 1] = PhantasiaBlockInfo.fromBlock(Blocks.CHEST);

        grid[CX + 2][CY][CZ + 2] = PhantasiaBlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        return grid;
    }

    static PhantasiaBlockInfo[][][] starbuncleBase() {
        PhantasiaBlockInfo[][][] grid = new PhantasiaBlockInfo[W][H][D];
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++)
                grid[x][0][z] = PhantasiaBlockInfo.fromBlock(Blocks.GRASS_BLOCK);

        grid[CX][CY][CZ] = PhantasiaBlockInfo.fromBlock(BlockRegistry.ARCHWOOD_CHEST.get());
        grid[CX - 2][CY][CZ] = PhantasiaBlockInfo.fromBlock(BlockRegistry.ARCHWOOD_CHEST.get());
        grid[CX + 2][CY][CZ] = PhantasiaBlockInfo.fromBlock(BlockRegistry.ARCHWOOD_CHEST.get());
        grid[CX][CY][CZ - 2] = PhantasiaBlockInfo.fromBlock(BlockRegistry.ARCHWOOD_CHEST.get());

        grid[CX][CY][CZ + 2] = PhantasiaBlockInfo.fromBlock(BlockRegistry.SOURCEBERRY_BUSH.get());
        return grid;
    }

    static PhantasiaBlockInfo[][][] bookwyrmBase() {
        PhantasiaBlockInfo[][][] grid = emptyGrid();
        grid[CX][CY][CZ] = PhantasiaBlockInfo.fromBlock(BlockRegistry.CRAFTING_LECTERN.get());
        grid[CX - 2][CY][CZ] = PhantasiaBlockInfo.fromBlock(BlockRegistry.ARCHWOOD_CHEST.get());
        grid[CX + 2][CY][CZ] = PhantasiaBlockInfo.fromBlock(BlockRegistry.ARCHWOOD_CHEST.get());
        grid[CX][CY][CZ - 2] = PhantasiaBlockInfo.fromBlock(BlockRegistry.ARCHWOOD_CHEST.get());
        return grid;
    }

    static PhantasiaBlockInfo[][][] ritualBrazierBase() {
        PhantasiaBlockInfo[][][] grid = emptyGrid();

        grid[CX][CY][CZ] = PhantasiaBlockInfo.fromBlockState(
                BlockRegistry.RITUAL_BLOCK.get().defaultBlockState().setValue(RitualBrazierBlock.LIT, true));

        for (int[] off : PEDESTAL_OFFSETS_4) {
            grid[CX + off[0]][CY][CZ + off[2]] = PhantasiaBlockInfo.fromBlock(BlockRegistry.BRAZIER_RELAY.get());
        }

        grid[CX - 2][CY][CZ - 2] = PhantasiaBlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        grid[CX - 2][CY][CZ + 2] = PhantasiaBlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        grid[CX + 2][CY][CZ - 2] = PhantasiaBlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        grid[CX + 2][CY][CZ + 2] = PhantasiaBlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        return grid;
    }

    static PhantasiaBlockInfo[][][] sourcelinkBase(net.minecraft.world.level.block.Block sourcelinkBlock) {
        PhantasiaBlockInfo[][][] grid = emptyGrid();
        grid[CX][CY][CZ] = PhantasiaBlockInfo.fromBlock(sourcelinkBlock);
        for (int[] off : PEDESTAL_OFFSETS_4) {
            grid[CX + off[0]][CY][CZ + off[2]] = PhantasiaBlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        }
        return grid;
    }

    private static PhantasiaBlockInfo[][][] emptyGrid() {
        PhantasiaBlockInfo[][][] grid = new PhantasiaBlockInfo[W][H][D];

        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++)
                grid[x][0][z] = PhantasiaBlockInfo.fromBlock(Blocks.STONE_BRICKS);
        return grid;
    }

    record LayoutResult(PhantasiaBlockInfo[][][] layout, Map<BlockPos, ItemStack> itemPlacements) {}

    private ArsNouveauLayoutBuilder() {}
}
