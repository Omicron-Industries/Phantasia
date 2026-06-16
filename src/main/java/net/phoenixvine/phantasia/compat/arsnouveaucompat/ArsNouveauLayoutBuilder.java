package net.phoenixvine.phantasia.compat.arsnouveaucompat;


import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import com.hollingsworth.arsnouveau.common.block.RitualBrazierBlock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hollingsworth.arsnouveau.setup.registry.BlockRegistry;

/**
 * Builds BlockInfo[][][] layouts and item placement maps for Ars Nouveau setups.
 * All layouts are 5×2×5 (x, y, z) with the central block at (2, 1, 2).
 */
final class ArsNouveauLayoutBuilder {

    // Cardinal and diagonal pedestal offsets from center (same Y)
    private static final int[][] PEDESTAL_OFFSETS_4 = {
            {-2, 0,  0}, { 2, 0,  0},
            { 0, 0, -2}, { 0, 0,  2}
    };
    private static final int[][] PEDESTAL_OFFSETS_8 = {
            {-2, 0,  0}, { 2, 0,  0},
            { 0, 0, -2}, { 0, 0,  2},
            {-2, 0, -2}, {-2, 0,  2},
            { 2, 0, -2}, { 2, 0,  2}
    };

    private static final int CX = 2, CY = 1, CZ = 2;
    // Apparatus sits on top of an Arcane Core, so it renders one block higher
    private static final int APP_Y = 2;
    private static final int W = 5, H = 3, D = 5;

    /** Base Enchanting Apparatus layout — apparatus on Arcane Core + up to 8 empty pedestals. */
    static BlockInfo[][][] enchantingApparatusBase(int pedestalCount) {
        BlockInfo[][][] grid = emptyGrid();
        grid[CX][CY][CZ]   = BlockInfo.fromBlock(BlockRegistry.ARCANE_CORE_BLOCK.get());
        grid[CX][APP_Y][CZ] = BlockInfo.fromBlock(BlockRegistry.ENCHANTING_APP_BLOCK.get());

        int[][] offsets = pedestalCount <= 4 ? PEDESTAL_OFFSETS_4 : PEDESTAL_OFFSETS_8;
        int count = Math.min(pedestalCount, offsets.length);
        for (int i = 0; i < count; i++) {
            int px = CX + offsets[i][0];
            int py = CY + offsets[i][1];
            int pz = CZ + offsets[i][2];
            grid[px][py][pz] = BlockInfo.fromBlock(BlockRegistry.ARCANE_PEDESTAL.get());
        }
        return grid;
    }

    /**
     * Builds the layout AND item-placement map for one EnchantingApparatusRecipe.
     * Returns a pair: [0] = BlockInfo grid, [1] = Map<BlockPos, ItemStack>.
     */
    static LayoutResult enchantingApparatusRecipe(
            List<ItemStack> pedestalItems, ItemStack reagent) {

        int count = pedestalItems.size();
        BlockInfo[][][] grid = enchantingApparatusBase(count);

        Map<BlockPos, ItemStack> placements = new LinkedHashMap<>();
        // Reagent goes in the apparatus itself (which is one block above the Arcane Core)
        placements.put(new BlockPos(CX, APP_Y, CZ), reagent.copy());

        int[][] offsets = count <= 4 ? PEDESTAL_OFFSETS_4 : PEDESTAL_OFFSETS_8;
        for (int i = 0; i < Math.min(count, offsets.length); i++) {
            BlockPos pos = new BlockPos(CX + offsets[i][0], CY, CZ + offsets[i][2]);
            placements.put(pos, pedestalItems.get(i).copy());
        }

        return new LayoutResult(grid, placements);
    }

    /** Base Imbuement Chamber layout — chamber + 4 source jars at cardinal positions. */
    static BlockInfo[][][] imbuementChamberBase() {
        BlockInfo[][][] grid = emptyGrid();
        grid[CX][CY][CZ] = BlockInfo.fromBlock(BlockRegistry.IMBUEMENT_BLOCK.get());
        for (int[] off : PEDESTAL_OFFSETS_4) {
            grid[CX + off[0]][CY][CZ + off[2]] = BlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        }
        return grid;
    }

    /** Layout + placements for one ImbuementRecipe. */
    static LayoutResult imbuementRecipe(ItemStack input) {
        BlockInfo[][][] grid = imbuementChamberBase();
        Map<BlockPos, ItemStack> placements = new LinkedHashMap<>();
        placements.put(new BlockPos(CX, CY, CZ), input.copy());
        return new LayoutResult(grid, placements);
    }

    // ── Spell Turret ───────────────────────────────────────────────────────────
    // Turret at centre (2,1,2); Source Jars at the 4 cardinals; chest behind it.

    static BlockInfo[][][] spellTurretBase(net.minecraft.world.level.block.Block turretBlock) {
        BlockInfo[][][] grid = emptyGrid();
        grid[CX][CY][CZ] = BlockInfo.fromBlock(turretBlock);
        for (int[] off : PEDESTAL_OFFSETS_4) {
            grid[CX + off[0]][CY][CZ + off[2]] = BlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        }
        // Chest directly behind the turret (+Z face, at (2,1,4)) for item pickup/place spells
        grid[CX][CY][CZ + 2] = BlockInfo.fromBlock(Blocks.CHEST);
        return grid;
    }

    // ── Wixie Cauldron ─────────────────────────────────────────────────────────
    // Cauldron at centre; 4 adjacent pedestals; 4 Source Jars one block out; Potion Jar corner.

    static BlockInfo[][][] wixieCauldronBase() {
        BlockInfo[][][] grid = emptyGrid();
        grid[CX][CY][CZ] = BlockInfo.fromBlock(BlockRegistry.WIXIE_CAULDRON.get());
        // Adjacent pedestals (1 block away on same Y)
        grid[CX - 1][CY][CZ]     = BlockInfo.fromBlock(BlockRegistry.ARCANE_PEDESTAL.get());
        grid[CX + 1][CY][CZ]     = BlockInfo.fromBlock(BlockRegistry.ARCANE_PEDESTAL.get());
        grid[CX][CY][CZ - 1]     = BlockInfo.fromBlock(BlockRegistry.ARCANE_PEDESTAL.get());
        grid[CX][CY][CZ + 1]     = BlockInfo.fromBlock(BlockRegistry.ARCANE_PEDESTAL.get());
        // Source Jars at the 4 cardinals (2 out)
        for (int[] off : PEDESTAL_OFFSETS_4) {
            grid[CX + off[0]][CY][CZ + off[2]] = BlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        }
        // Potion Jar at a corner for potion autocrafting setup context
        grid[CX - 2][CY][CZ - 2] = BlockInfo.fromBlock(BlockRegistry.POTION_JAR.get());
        return grid;
    }

    // ── Drygmy ─────────────────────────────────────────────────────────────────
    // Drygmy Stone at centre on mossy cobblestone; Mob Jars at cardinals.
    // The mossy cobblestone converts into the Drygmy Henge after using the charm.

    static BlockInfo[][][] drygmyBase() {
        BlockInfo[][][] grid = new BlockInfo[W][H][D];
        // Grass floor
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++)
                grid[x][0][z] = BlockInfo.fromBlock(Blocks.GRASS_BLOCK);
        // Mossy cobblestone base the charm is used on (shown pre-conversion)
        grid[CX][0][CZ] = BlockInfo.fromBlock(Blocks.MOSSY_COBBLESTONE);
        // Drygmy Stone summoned on top
        grid[CX][CY][CZ] = BlockInfo.fromBlock(BlockRegistry.DRYGMY_BLOCK.get());
        // Mob Jars at cardinal positions — each jar holds a different mob for variety bonus
        for (int[] off : PEDESTAL_OFFSETS_4) {
            grid[CX + off[0]][CY][CZ + off[2]] = BlockInfo.fromBlock(BlockRegistry.MOB_JAR.get());
        }
        return grid;
    }

    // ── Whirlisprig ────────────────────────────────────────────────────────────
    // Whirlisprig Flower at centre; natural blocks/flowers around it; chest beside it.

    static BlockInfo[][][] whirlisprigBase() {
        BlockInfo[][][] grid = new BlockInfo[W][H][D];
        // Grass floor with some variety
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++)
                grid[x][0][z] = BlockInfo.fromBlock(Blocks.GRASS_BLOCK);
        // Whirlisprig Flower at centre (the charm is used on any flower, represented here)
        grid[CX][CY][CZ] = BlockInfo.fromBlock(BlockRegistry.WHIRLISPRIG_FLOWER.get());
        // Archwood log to show it generates wood; flowers for mood diversity
        grid[CX - 2][CY][CZ]     = BlockInfo.fromBlock(Blocks.OAK_LOG);
        grid[CX + 2][CY][CZ]     = BlockInfo.fromBlock(Blocks.DANDELION);
        grid[CX][CY][CZ - 2]     = BlockInfo.fromBlock(Blocks.POPPY);
        // Chest beside the Whirlisprig — required for it to output items
        grid[CX][CY][CZ + 1]     = BlockInfo.fromBlock(Blocks.CHEST);
        // Source Jar for source supply
        grid[CX + 2][CY][CZ + 2] = BlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        return grid;
    }

    // ── Starbuncle ─────────────────────────────────────────────────────────────
    // Starbuncle on the ground (shown as the stone block it's placed on);
    // Archwood Chests it moves items between; Source Berry Bush it harvests.

    static BlockInfo[][][] starbuncleBase() {
        BlockInfo[][][] grid = new BlockInfo[W][H][D];
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++)
                grid[x][0][z] = BlockInfo.fromBlock(Blocks.GRASS_BLOCK);
        // Starbuncle sits on the ground, represented by the Archwood chest below it
        // for clarity — actually place the archwood chest arrangement
        grid[CX][CY][CZ]         = BlockInfo.fromBlock(BlockRegistry.ARCHWOOD_CHEST.get());
        grid[CX - 2][CY][CZ]     = BlockInfo.fromBlock(BlockRegistry.ARCHWOOD_CHEST.get());
        grid[CX + 2][CY][CZ]     = BlockInfo.fromBlock(BlockRegistry.ARCHWOOD_CHEST.get());
        grid[CX][CY][CZ - 2]     = BlockInfo.fromBlock(BlockRegistry.ARCHWOOD_CHEST.get());
        // Source Berry Bush the Starbuncle can auto-harvest
        grid[CX][CY][CZ + 2]     = BlockInfo.fromBlock(BlockRegistry.SOURCEBERRY_BUSH.get());
        return grid;
    }

    // ── Ritual Brazier ─────────────────────────────────────────────────────────
    // Ritual Brazier at centre; Brazier Relays at cardinals to extend ritual range;
    // Source Jars at diagonals.

    static BlockInfo[][][] ritualBrazierBase() {
        BlockInfo[][][] grid = emptyGrid();
        // LIT=true so the brazier renders as active (baked geometry includes the flame)
        grid[CX][CY][CZ] = BlockInfo.fromBlockState(
                BlockRegistry.RITUAL_BLOCK.get().defaultBlockState().setValue(RitualBrazierBlock.LIT, true));
        // Brazier Relays extend the ritual radius and count as valid ritual blocks
        for (int[] off : PEDESTAL_OFFSETS_4) {
            grid[CX + off[0]][CY][CZ + off[2]] = BlockInfo.fromBlock(BlockRegistry.BRAZIER_RELAY.get());
        }
        // Source Jars at diagonals
        grid[CX - 2][CY][CZ - 2] = BlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        grid[CX - 2][CY][CZ + 2] = BlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        grid[CX + 2][CY][CZ - 2] = BlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        grid[CX + 2][CY][CZ + 2] = BlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        return grid;
    }

    // ── Sourcelink setups ──────────────────────────────────────────────────────
    // Shared pattern: sourcelink at centre, Source Jars at cardinals.

    static BlockInfo[][][] sourcelinkBase(net.minecraft.world.level.block.Block sourcelinkBlock) {
        BlockInfo[][][] grid = emptyGrid();
        grid[CX][CY][CZ] = BlockInfo.fromBlock(sourcelinkBlock);
        for (int[] off : PEDESTAL_OFFSETS_4) {
            grid[CX + off[0]][CY][CZ + off[2]] = BlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        }
        return grid;
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static BlockInfo[][][] emptyGrid() {
        BlockInfo[][][] grid = new BlockInfo[W][H][D];
        // Floor of stone bricks as base plate
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++)
                grid[x][0][z] = BlockInfo.fromBlock(Blocks.STONE_BRICKS);
        return grid;
    }

    record LayoutResult(BlockInfo[][][] layout, Map<BlockPos, ItemStack> itemPlacements) {}

    private ArsNouveauLayoutBuilder() {}
}
