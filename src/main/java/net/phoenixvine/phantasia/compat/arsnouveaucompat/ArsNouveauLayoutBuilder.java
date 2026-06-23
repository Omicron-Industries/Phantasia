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

/**
 * Builds PhantasiaBlockInfo[][][] layouts and item placement maps for Ars Nouveau setups.
 * All layouts are 5×2×5 (x, y, z) with the central block at (2, 1, 2).
 */
final class ArsNouveauLayoutBuilder {

    // Cardinal and diagonal pedestal offsets from center (same Y)
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
    // Apparatus sits on top of an Arcane Core, so it renders one block higher
    private static final int APP_Y = 2;
    private static final int W = 5, H = 3, D = 5;

    /** Base Enchanting Apparatus layout — apparatus on Arcane Core + up to 8 empty pedestals. */
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

    /**
     * Builds the layout AND item-placement map for one EnchantingApparatusRecipe.
     * Returns a pair: [0] = PhantasiaBlockInfo grid, [1] = Map<BlockPos, ItemStack>.
     */
    static LayoutResult enchantingApparatusRecipe(
                                                  List<ItemStack> pedestalItems, ItemStack reagent) {
        int count = pedestalItems.size();
        PhantasiaBlockInfo[][][] grid = enchantingApparatusBase(count);

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
    static PhantasiaBlockInfo[][][] imbuementChamberBase() {
        PhantasiaBlockInfo[][][] grid = emptyGrid();
        grid[CX][CY][CZ] = PhantasiaBlockInfo.fromBlock(BlockRegistry.IMBUEMENT_BLOCK.get());
        for (int[] off : PEDESTAL_OFFSETS_4) {
            grid[CX + off[0]][CY][CZ + off[2]] = PhantasiaBlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        }
        return grid;
    }

    /** Layout + placements for one ImbuementRecipe. */
    static LayoutResult imbuementRecipe(ItemStack input) {
        PhantasiaBlockInfo[][][] grid = imbuementChamberBase();
        Map<BlockPos, ItemStack> placements = new LinkedHashMap<>();
        placements.put(new BlockPos(CX, CY, CZ), input.copy());
        return new LayoutResult(grid, placements);
    }

    // ── Spell Turret ───────────────────────────────────────────────────────────
    // Turret at centre (2,1,2); Source Jars at the 4 cardinals; chest behind it.

    static PhantasiaBlockInfo[][][] spellTurretBase(net.minecraft.world.level.block.Block turretBlock) {
        PhantasiaBlockInfo[][][] grid = emptyGrid();
        grid[CX][CY][CZ] = PhantasiaBlockInfo.fromBlock(turretBlock);
        for (int[] off : PEDESTAL_OFFSETS_4) {
            grid[CX + off[0]][CY][CZ + off[2]] = PhantasiaBlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        }
        // Chest directly behind the turret (+Z face, at (2,1,4)) for item pickup/place spells
        grid[CX][CY][CZ + 2] = PhantasiaBlockInfo.fromBlock(Blocks.CHEST);
        return grid;
    }

    // ── Wixie Cauldron ─────────────────────────────────────────────────────────
    // Cauldron at centre; 4 adjacent pedestals; 4 Source Jars one block out; Potion Jar corner.

    static PhantasiaBlockInfo[][][] wixieCauldronBase() {
        PhantasiaBlockInfo[][][] grid = emptyGrid();
        grid[CX][CY][CZ] = PhantasiaBlockInfo.fromBlock(BlockRegistry.WIXIE_CAULDRON.get());
        // Adjacent pedestals (1 block away on same Y)
        grid[CX - 1][CY][CZ] = PhantasiaBlockInfo.fromBlock(BlockRegistry.ARCANE_PEDESTAL.get());
        grid[CX + 1][CY][CZ] = PhantasiaBlockInfo.fromBlock(BlockRegistry.ARCANE_PEDESTAL.get());
        grid[CX][CY][CZ - 1] = PhantasiaBlockInfo.fromBlock(BlockRegistry.ARCANE_PEDESTAL.get());
        grid[CX][CY][CZ + 1] = PhantasiaBlockInfo.fromBlock(BlockRegistry.ARCANE_PEDESTAL.get());
        // Source Jars at the 4 cardinals (2 out)
        for (int[] off : PEDESTAL_OFFSETS_4) {
            grid[CX + off[0]][CY][CZ + off[2]] = PhantasiaBlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        }
        // Potion Jar at a corner for potion autocrafting setup context
        grid[CX - 2][CY][CZ - 2] = PhantasiaBlockInfo.fromBlock(BlockRegistry.POTION_JAR.get());
        return grid;
    }

    // ── Drygmy ─────────────────────────────────────────────────────────────────
    // Drygmy Stone at centre on mossy cobblestone; Mob Jars at cardinals.
    // The mossy cobblestone converts into the Drygmy Henge after using the charm.

    static PhantasiaBlockInfo[][][] drygmyBase() {
        PhantasiaBlockInfo[][][] grid = new PhantasiaBlockInfo[W][H][D];
        // Grass floor
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++)
                grid[x][0][z] = PhantasiaBlockInfo.fromBlock(Blocks.GRASS_BLOCK);
        // Mossy cobblestone base the charm is used on (shown pre-conversion)
        grid[CX][0][CZ] = PhantasiaBlockInfo.fromBlock(Blocks.MOSSY_COBBLESTONE);
        // Drygmy Stone summoned on top
        grid[CX][CY][CZ] = PhantasiaBlockInfo.fromBlock(BlockRegistry.DRYGMY_BLOCK.get());
        // Mob Jars at cardinal positions — each jar holds a different mob for variety bonus
        for (int[] off : PEDESTAL_OFFSETS_4) {
            grid[CX + off[0]][CY][CZ + off[2]] = PhantasiaBlockInfo.fromBlock(BlockRegistry.MOB_JAR.get());
        }
        return grid;
    }

    // ── Whirlisprig ────────────────────────────────────────────────────────────
    // Whirlisprig Flower at centre; natural blocks/flowers around it; chest beside it.

    static PhantasiaBlockInfo[][][] whirlisprigBase() {
        PhantasiaBlockInfo[][][] grid = new PhantasiaBlockInfo[W][H][D];
        // Grass floor with some variety
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++)
                grid[x][0][z] = PhantasiaBlockInfo.fromBlock(Blocks.GRASS_BLOCK);
        // Whirlisprig Flower at centre (the charm is used on any flower, represented here)
        grid[CX][CY][CZ] = PhantasiaBlockInfo.fromBlock(BlockRegistry.WHIRLISPRIG_FLOWER.get());
        // Archwood log to show it generates wood; flowers for mood diversity
        grid[CX - 2][CY][CZ] = PhantasiaBlockInfo.fromBlock(Blocks.OAK_LOG);
        grid[CX + 2][CY][CZ] = PhantasiaBlockInfo.fromBlock(Blocks.DANDELION);
        grid[CX][CY][CZ - 2] = PhantasiaBlockInfo.fromBlock(Blocks.POPPY);
        // Chest beside the Whirlisprig — required for it to output items
        grid[CX][CY][CZ + 1] = PhantasiaBlockInfo.fromBlock(Blocks.CHEST);
        // Source Jar for source supply
        grid[CX + 2][CY][CZ + 2] = PhantasiaBlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        return grid;
    }

    // ── Starbuncle ─────────────────────────────────────────────────────────────
    // Starbuncle on the ground (shown as the stone block it's placed on);
    // Archwood Chests it moves items between; Source Berry Bush it harvests.

    static PhantasiaBlockInfo[][][] starbuncleBase() {
        PhantasiaBlockInfo[][][] grid = new PhantasiaBlockInfo[W][H][D];
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++)
                grid[x][0][z] = PhantasiaBlockInfo.fromBlock(Blocks.GRASS_BLOCK);
        // Starbuncle sits on the ground, represented by the Archwood chest below it
        // for clarity — actually place the archwood chest arrangement
        grid[CX][CY][CZ] = PhantasiaBlockInfo.fromBlock(BlockRegistry.ARCHWOOD_CHEST.get());
        grid[CX - 2][CY][CZ] = PhantasiaBlockInfo.fromBlock(BlockRegistry.ARCHWOOD_CHEST.get());
        grid[CX + 2][CY][CZ] = PhantasiaBlockInfo.fromBlock(BlockRegistry.ARCHWOOD_CHEST.get());
        grid[CX][CY][CZ - 2] = PhantasiaBlockInfo.fromBlock(BlockRegistry.ARCHWOOD_CHEST.get());
        // Source Berry Bush the Starbuncle can auto-harvest
        grid[CX][CY][CZ + 2] = PhantasiaBlockInfo.fromBlock(BlockRegistry.SOURCEBERRY_BUSH.get());
        return grid;
    }

    // ── Bookwyrm ────────────────────────────────────────────────────────────────
    // Storage Lectern at centre (bookwyrm's home); Archwood Chests at cardinals
    // that the bookwyrm shuttles items to and from.

    static PhantasiaBlockInfo[][][] bookwyrmBase() {
        PhantasiaBlockInfo[][][] grid = emptyGrid();
        grid[CX][CY][CZ] = PhantasiaBlockInfo.fromBlock(BlockRegistry.CRAFTING_LECTERN.get());
        grid[CX - 2][CY][CZ] = PhantasiaBlockInfo.fromBlock(BlockRegistry.ARCHWOOD_CHEST.get());
        grid[CX + 2][CY][CZ] = PhantasiaBlockInfo.fromBlock(BlockRegistry.ARCHWOOD_CHEST.get());
        grid[CX][CY][CZ - 2] = PhantasiaBlockInfo.fromBlock(BlockRegistry.ARCHWOOD_CHEST.get());
        return grid;
    }

    // ── Ritual Brazier ─────────────────────────────────────────────────────────
    // Ritual Brazier at centre; Brazier Relays at cardinals to extend ritual range;
    // Source Jars at diagonals.

    static PhantasiaBlockInfo[][][] ritualBrazierBase() {
        PhantasiaBlockInfo[][][] grid = emptyGrid();
        // LIT=true so the brazier renders as active (baked geometry includes the flame)
        grid[CX][CY][CZ] = PhantasiaBlockInfo.fromBlockState(
                BlockRegistry.RITUAL_BLOCK.get().defaultBlockState().setValue(RitualBrazierBlock.LIT, true));
        // Brazier Relays extend the ritual radius and count as valid ritual blocks
        for (int[] off : PEDESTAL_OFFSETS_4) {
            grid[CX + off[0]][CY][CZ + off[2]] = PhantasiaBlockInfo.fromBlock(BlockRegistry.BRAZIER_RELAY.get());
        }
        // Source Jars at diagonals
        grid[CX - 2][CY][CZ - 2] = PhantasiaBlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        grid[CX - 2][CY][CZ + 2] = PhantasiaBlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        grid[CX + 2][CY][CZ - 2] = PhantasiaBlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        grid[CX + 2][CY][CZ + 2] = PhantasiaBlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        return grid;
    }

    // ── Sourcelink setups ──────────────────────────────────────────────────────
    // Shared pattern: sourcelink at centre, Source Jars at cardinals.

    static PhantasiaBlockInfo[][][] sourcelinkBase(net.minecraft.world.level.block.Block sourcelinkBlock) {
        PhantasiaBlockInfo[][][] grid = emptyGrid();
        grid[CX][CY][CZ] = PhantasiaBlockInfo.fromBlock(sourcelinkBlock);
        for (int[] off : PEDESTAL_OFFSETS_4) {
            grid[CX + off[0]][CY][CZ + off[2]] = PhantasiaBlockInfo.fromBlock(BlockRegistry.SOURCE_JAR.get());
        }
        return grid;
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static PhantasiaBlockInfo[][][] emptyGrid() {
        PhantasiaBlockInfo[][][] grid = new PhantasiaBlockInfo[W][H][D];
        // Floor of stone bricks as base plate
        for (int x = 0; x < W; x++)
            for (int z = 0; z < D; z++)
                grid[x][0][z] = PhantasiaBlockInfo.fromBlock(Blocks.STONE_BRICKS);
        return grid;
    }

    record LayoutResult(PhantasiaBlockInfo[][][] layout, Map<BlockPos, ItemStack> itemPlacements) {}

    private ArsNouveauLayoutBuilder() {}
}
