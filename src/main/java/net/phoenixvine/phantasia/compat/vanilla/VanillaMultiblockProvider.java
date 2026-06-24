package net.phoenixvine.phantasia.compat.vanilla;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockProvider;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class VanillaMultiblockProvider implements IPhantasiaMultiblockProvider {

    private static final List<IPhantasiaMultiblockDefinition> DEFINITIONS = new ArrayList<>();

    static {
        DEFINITIONS.add(buildBeacon());
        DEFINITIONS.add(buildNetherPortal());
        DEFINITIONS.add(buildEndPortal());
        DEFINITIONS.add(buildConduit());
        DEFINITIONS.add(buildEnchantingSetup());
        DEFINITIONS.add(buildWither());
        DEFINITIONS.add(buildIronGolem());
        DEFINITIONS.add(buildSnowGolem());
    }

    @Override public String getModId() { return "minecraft"; }
    @Override public boolean isAvailable() { return true; }
    @Override public List<IPhantasiaMultiblockDefinition> getAllDefinitions() { return DEFINITIONS; }
    @Override public boolean isControllerBlock(BlockState state) { return false; }
    @Override public boolean isPartBlock(BlockState state) { return false; }
    @Override public Optional<PhantasiaBlockInfo> resolveBlock(String id) { return Optional.empty(); }

    @Override
    public Optional<IPhantasiaMultiblockDefinition> resolve(String machineId) {
        for (var def : DEFINITIONS) {
            if (def.getId().toString().equals(machineId) || def.getId().getPath().equals(machineId))
                return Optional.of(def);
        }
        return Optional.empty();
    }

    // ── Structure builders ────────────────────────────────────────────────────

    private static IPhantasiaMultiblockDefinition buildBeacon() {
        PhantasiaBlockInfo iron   = b(Blocks.IRON_BLOCK.defaultBlockState());
        PhantasiaBlockInfo beacon = b(Blocks.BEACON.defaultBlockState());

        List<IPhantasiaMultiblockShape> shapes = new ArrayList<>();
        for (int tier = 1; tier <= 4; tier++)
            shapes.add(shape(beaconTierGrid(iron, beacon, tier)));

        PhantasiaScriptData script = new PhantasiaScriptData("minecraft:beacon");
        script.addStep(step(0,
                "Place the pyramid base of iron, gold, diamond, netherite, or emerald blocks. Tier 1 is a 3x3 layer.",
                "layer:0", false));
        script.addStep(step(60, "Place the Beacon on top of the pyramid.", "all", false));
        script.addStep(step(120,
                "The Beacon activates and shoots a beam of light into the sky! Right-click to choose a status effect.",
                "all", true));

        // Single material variant group: one dropdown cycles iron/gold/diamond/netherite/emerald.
        List<PhantasiaScriptData.VariantPositionData> ironPositions = new ArrayList<>();
        for (int tier = 1; tier <= 4; tier++) {
            for (int layer = 0; layer < tier; layer++) {
                int size   = (tier - layer) * 2 + 1;
                int offset = (9 - size) / 2;
                for (int x = offset; x < offset + size; x++)
                    for (int z = offset; z < offset + size; z++)
                        ironPositions.add(new PhantasiaScriptData.VariantPositionData(x, layer, z));
            }
        }
        var seen = new java.util.HashSet<Long>();
        var uniquePositions = new ArrayList<PhantasiaScriptData.VariantPositionData>();
        for (var p : ironPositions) {
            long key = ((long) p.x << 20) | ((long) p.y << 10) | p.z;
            if (seen.add(key)) uniquePositions.add(p);
        }

        PhantasiaScriptData.OptionalGroupData matGroup = new PhantasiaScriptData.OptionalGroupData(
                "base_material", "Base Blocks", "optional", true);
        matGroup.primaryBlock = "minecraft:iron_block";
        matGroup.additionalBlocks.add("minecraft:gold_block");
        matGroup.additionalBlocks.add("minecraft:diamond_block");
        matGroup.additionalBlocks.add("minecraft:netherite_block");
        matGroup.additionalBlocks.add("minecraft:emerald_block");
        matGroup.positions.addAll(uniquePositions);
        script.getOptionalGroups().add(matGroup);

        return defWithBeam("minecraft:beacon", "Beacon", new ItemStack(Items.BEACON), shapes, script);
    }

    private static IPhantasiaMultiblockDefinition buildNetherPortal() {
        PhantasiaBlockInfo obsidian = b(Blocks.OBSIDIAN.defaultBlockState());
        PhantasiaBlockInfo portal = b(Blocks.NETHER_PORTAL.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_AXIS, net.minecraft.core.Direction.Axis.X));

        PhantasiaBlockInfo[][][] grid = new PhantasiaBlockInfo[4][5][1];
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 5; y++) {
                boolean edge = x == 0 || x == 3 || y == 0 || y == 4;
                grid[x][y][0] = edge ? obsidian : portal;
            }
        }

        PhantasiaScriptData script = new PhantasiaScriptData("minecraft:nether_portal");
        script.addStep(step(0,
                "Build a rectangular frame of Obsidian - at least 4 wide and 5 tall. The corners are optional.",
                "layer:0", false));
        script.addStep(step(60, "Add the remaining obsidian sides.", "all", false));
        script.addStep(step(120, "Use Flint and Steel (or fire) on the interior to ignite the portal.", "all", true));

        return def("minecraft:nether_portal", "Nether Portal", new ItemStack(Items.OBSIDIAN),
                List.of(shape(grid)), script);
    }

    private static IPhantasiaMultiblockDefinition buildEndPortal() {
        BlockState frameN = Blocks.END_PORTAL_FRAME.defaultBlockState()
                .setValue(net.minecraft.world.level.block.EndPortalFrameBlock.FACING, Direction.SOUTH);
        BlockState frameS = Blocks.END_PORTAL_FRAME.defaultBlockState()
                .setValue(net.minecraft.world.level.block.EndPortalFrameBlock.FACING, Direction.NORTH);
        BlockState frameW = Blocks.END_PORTAL_FRAME.defaultBlockState()
                .setValue(net.minecraft.world.level.block.EndPortalFrameBlock.FACING, Direction.EAST);
        BlockState frameE = Blocks.END_PORTAL_FRAME.defaultBlockState()
                .setValue(net.minecraft.world.level.block.EndPortalFrameBlock.FACING, Direction.WEST);
        BlockState frameNE = frameN.setValue(net.minecraft.world.level.block.EndPortalFrameBlock.HAS_EYE, true);
        BlockState frameSE = frameS.setValue(net.minecraft.world.level.block.EndPortalFrameBlock.HAS_EYE, true);
        BlockState frameWE = frameW.setValue(net.minecraft.world.level.block.EndPortalFrameBlock.HAS_EYE, true);
        BlockState frameEE = frameE.setValue(net.minecraft.world.level.block.EndPortalFrameBlock.HAS_EYE, true);

        PhantasiaBlockInfo endPortal = b(Blocks.END_PORTAL.defaultBlockState());

        // Single flat 5x1x5 shape: eye-frames on the outside ring, portal in the 3x3 center.
        PhantasiaBlockInfo[][][] grid = new PhantasiaBlockInfo[5][1][5];
        grid[1][0][0] = b(frameNE); grid[2][0][0] = b(frameNE); grid[3][0][0] = b(frameNE);
        grid[1][0][4] = b(frameSE); grid[2][0][4] = b(frameSE); grid[3][0][4] = b(frameSE);
        grid[0][0][1] = b(frameWE); grid[0][0][2] = b(frameWE); grid[0][0][3] = b(frameWE);
        grid[4][0][1] = b(frameEE); grid[4][0][2] = b(frameEE); grid[4][0][3] = b(frameEE);
        for (int x = 1; x <= 3; x++)
            for (int z = 1; z <= 3; z++)
                grid[x][0][z] = endPortal;

        // Build the 12 frame positions for step 0 show="pos" (no-eye frames shown first).
        PhantasiaScriptData.StepData step0 = new PhantasiaScriptData.StepData();
        step0.tick = 0;
        step0.caption = "Place 12 End Portal Frame blocks in a ring - 3 on each side. Each frame must face inward toward the centre.";
        step0.working = false;
        step0.show = "pos";
        step0.positions = new java.util.ArrayList<>();
        int[][] framePositions = {
            {1,0,0},{2,0,0},{3,0,0},
            {1,0,4},{2,0,4},{3,0,4},
            {0,0,1},{0,0,2},{0,0,3},
            {4,0,1},{4,0,2},{4,0,3}
        };
        for (int[] fp : framePositions) step0.positions.add(fp);
        // Also include the 3×3 portal block positions so players can see where the portal will form.
        for (int x = 1; x <= 3; x++)
            for (int z = 1; z <= 3; z++)
                step0.positions.add(new int[]{x, 0, z});

        PhantasiaScriptData script = new PhantasiaScriptData("minecraft:end_portal");
        script.addStep(step0);
        script.addStep(step(60,
                "Insert an Eye of Ender into each frame. Once all 12 are filled, the portal activates instantly.",
                "all", false));
        script.addStep(step(120, "The End Portal is open. Step through to reach The End.", "all", true));

        return def("minecraft:end_portal", "End Portal", new ItemStack(Items.ENDER_EYE),
                List.of(shape(grid)), script);
    }

    private static IPhantasiaMultiblockDefinition buildConduit() {
        PhantasiaBlockInfo prismarine = b(Blocks.PRISMARINE.defaultBlockState());
        PhantasiaBlockInfo conduit = b(Blocks.CONDUIT.defaultBlockState());

        PhantasiaBlockInfo[][][] grid = new PhantasiaBlockInfo[5][5][5];
        for (int a = 0; a < 5; a++) {
            for (int bv = 0; bv < 5; bv++) {
                boolean edge = a == 0 || a == 4 || bv == 0 || bv == 4;
                if (!edge) continue;
                grid[2][a][bv] = prismarine;
                grid[a][2][bv] = prismarine;
                grid[a][bv][2] = prismarine;
            }
        }
        grid[2][2][2] = conduit;

        PhantasiaScriptData script = new PhantasiaScriptData("minecraft:conduit");
        script.addStep(step(0,
                "Place a Conduit (crafted from a Heart of the Sea + 8 Nautilus Shells) in the water.",
                "pos:2,2,2", false));
        script.addStep(step(60,
                "Build a frame of Prismarine, Prismarine Bricks, or Sea Lanterns around it - at least 16 blocks arranged in rings on each axis.",
                "all", false));
        script.addStep(step(120,
                "The Conduit activates, granting Conduit Power (underwater breathing, mining speed, and vision) to nearby players.",
                "all", true));

        return defWithConduit("minecraft:conduit", "Conduit", new ItemStack(Items.CONDUIT), List.of(shape(grid)), script);
    }

    private static IPhantasiaMultiblockDefinition buildEnchantingSetup() {
        PhantasiaBlockInfo table     = b(Blocks.ENCHANTING_TABLE.defaultBlockState());
        PhantasiaBlockInfo bookshelf = b(Blocks.BOOKSHELF.defaultBlockState());

        PhantasiaBlockInfo[][][] grid = new PhantasiaBlockInfo[5][2][5];
        grid[2][0][2] = table;
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                if (x == 2 && z == 2) continue;
                if (x == 0 || x == 4 || z == 0 || z == 4) {
                    grid[x][0][z] = bookshelf;
                    grid[x][1][z] = bookshelf;
                }
            }
        }

        PhantasiaScriptData script = new PhantasiaScriptData("minecraft:enchanting_setup");
        script.addStep(step(0, "Place an Enchanting Table.", "pos:2,0,2", false));
        script.addStep(step(60,
                "Surround it with Bookshelves - up to 15 bookshelves, placed 2 blocks away with a 1-block air gap.",
                "all", false));
        script.addStep(step(120,
                "With all 15 bookshelves in place, the Enchanting Table can offer Level 30 enchantments.",
                "all", true));

        return def("minecraft:enchanting_setup", "Enchanting Setup", new ItemStack(Items.ENCHANTING_TABLE),
                List.of(shape(grid)), script);
    }

    private static IPhantasiaMultiblockDefinition buildWither() {
        PhantasiaBlockInfo sand  = b(Blocks.SOUL_SAND.defaultBlockState());
        PhantasiaBlockInfo skull = b(Blocks.WITHER_SKELETON_SKULL.defaultBlockState());

        PhantasiaBlockInfo[][][] grid = new PhantasiaBlockInfo[3][3][1];
        grid[1][0][0] = sand;
        grid[1][1][0] = sand;
        grid[0][1][0] = sand;
        grid[2][1][0] = sand;
        grid[1][2][0] = skull;
        grid[0][2][0] = skull;
        grid[2][2][0] = skull;

        PhantasiaScriptData script = new PhantasiaScriptData("minecraft:wither");
        script.addStep(step(0,
                "Place Soul Sand (or Soul Soil) in a T-shape - one block on the bottom, three across the top.",
                "layer:0", false));
        script.addStep(step(60,
                "Place one block on the vertical stem (do not place the skulls yet!).", "layer:1", false));
        script.addStep(step(120,
                "Place three Wither Skeleton Skulls across the top of the T. The Wither spawns immediately - be ready!",
                "none", true));

        return defWithMob("minecraft:wither", "Wither", new ItemStack(Items.WITHER_SKELETON_SKULL),
                List.of(shape(grid)), script, net.minecraft.world.entity.EntityType.WITHER, 80);
    }

    private static IPhantasiaMultiblockDefinition buildIronGolem() {
        PhantasiaBlockInfo iron    = b(Blocks.IRON_BLOCK.defaultBlockState());
        PhantasiaBlockInfo pumpkin = b(Blocks.CARVED_PUMPKIN.defaultBlockState());

        PhantasiaBlockInfo[][][] grid = new PhantasiaBlockInfo[3][3][1];
        grid[1][0][0] = iron;
        grid[0][1][0] = iron;
        grid[1][1][0] = iron;
        grid[2][1][0] = iron;
        grid[1][2][0] = pumpkin;

        PhantasiaScriptData script = new PhantasiaScriptData("minecraft:iron_golem");
        script.addStep(step(0,
                "Place one Iron Block on the ground, then two more on either side of a second Iron Block placed on top - forming a T.",
                "layer:1", false));
        script.addStep(step(60,
                "Place a Carved Pumpkin (not a Jack o'Lantern) on top. The Iron Golem spawns immediately.",
                "none", true));

        return defWithMob("minecraft:iron_golem", "Iron Golem", new ItemStack(Items.IRON_BLOCK),
                List.of(shape(grid)), script, net.minecraft.world.entity.EntityType.IRON_GOLEM, 60);
    }

    private static IPhantasiaMultiblockDefinition buildSnowGolem() {
        PhantasiaBlockInfo snow    = b(Blocks.SNOW_BLOCK.defaultBlockState());
        PhantasiaBlockInfo pumpkin = b(Blocks.CARVED_PUMPKIN.defaultBlockState());

        PhantasiaBlockInfo[][][] grid = new PhantasiaBlockInfo[1][3][1];
        grid[0][0][0] = snow;
        grid[0][1][0] = snow;
        grid[0][2][0] = pumpkin;

        PhantasiaScriptData script = new PhantasiaScriptData("minecraft:snow_golem");
        script.addStep(step(0, "Stack two Snow Blocks on the ground.", "layers:0-1", false));
        script.addStep(step(60,
                "Place a Carved Pumpkin on top. The Snow Golem spawns and will leave a trail of snow as it walks!",
                "none", true));

        return defWithMob("minecraft:snow_golem", "Snow Golem", new ItemStack(Items.SNOW_BLOCK),
                List.of(shape(grid)), script, net.minecraft.world.entity.EntityType.SNOW_GOLEM, 40);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static PhantasiaBlockInfo b(BlockState state) {
        return PhantasiaBlockInfo.fromBlockState(state);
    }

    private static IPhantasiaMultiblockShape shape(PhantasiaBlockInfo[][][] grid) {
        return new VanillaMultiblockShape(grid);
    }

    private static PhantasiaBlockInfo[][][] beaconTierGrid(PhantasiaBlockInfo iron,
                                                            PhantasiaBlockInfo beacon, int tier) {
        int gs = 9, maxY = 5, centre = 4;
        PhantasiaBlockInfo[][][] grid = new PhantasiaBlockInfo[gs][maxY][gs];
        for (int layer = 0; layer < tier; layer++) {
            int size   = (tier - layer) * 2 + 1;
            int offset = (gs - size) / 2;
            for (int x = offset; x < offset + size; x++)
                for (int z = offset; z < offset + size; z++)
                    grid[x][layer][z] = iron;
        }
        grid[centre][tier][centre] = beacon;
        return grid;
    }

    private static IPhantasiaMultiblockShape sliceY(PhantasiaBlockInfo[][][] src, int y) {
        int sx = src.length, sz = sx > 0 ? src[0][0].length : 0;
        PhantasiaBlockInfo[][][] grid = new PhantasiaBlockInfo[sx][1][sz];
        for (int x = 0; x < sx; x++)
            for (int z = 0; z < sz; z++)
                grid[x][0][z] = src[x][y][z];
        return new VanillaMultiblockShape(grid);
    }

    private static PhantasiaScriptData.StepData step(int tick, String caption, String show, boolean working) {
        PhantasiaScriptData.StepData s = new PhantasiaScriptData.StepData();
        s.tick = tick;
        s.caption = caption;
        s.working = working;
        if (show.startsWith("layer:")) {
            s.show = "layer";
            s.layer = Integer.parseInt(show.substring(6));
        } else if (show.startsWith("layers:")) {
            s.show = "layers";
            String[] parts = show.substring(7).split("-");
            s.layerMin = Integer.parseInt(parts[0]);
            s.layerMax = Integer.parseInt(parts[1]);
        } else if (show.startsWith("pos:")) {
            s.show = "pos";
            String[] parts = show.substring(4).split(",");
            s.positions = new java.util.ArrayList<>();
            s.positions.add(new int[]{
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())});
        } else if ("none".equals(show)) {
            s.show = "pos";
        } else {
            s.show = show;
        }
        return s;
    }

    private static IPhantasiaMultiblockDefinition def(String id, String name, ItemStack icon,
                                                      List<IPhantasiaMultiblockShape> shapes,
                                                      PhantasiaScriptData script) {
        return new VanillaMultiblockDefinition(new ResourceLocation(id), name, icon, shapes, script, null, 0, false, false);
    }

    private static IPhantasiaMultiblockDefinition defWithBeam(String id, String name, ItemStack icon,
                                                              List<IPhantasiaMultiblockShape> shapes,
                                                              PhantasiaScriptData script) {
        return new VanillaMultiblockDefinition(new ResourceLocation(id), name, icon, shapes, script, null, 0, true, false);
    }

    private static IPhantasiaMultiblockDefinition defWithConduit(String id, String name, ItemStack icon,
                                                                 List<IPhantasiaMultiblockShape> shapes,
                                                                 PhantasiaScriptData script) {
        return new VanillaMultiblockDefinition(new ResourceLocation(id), name, icon, shapes, script, null, 0, false, true);
    }

    private static IPhantasiaMultiblockDefinition defWithMob(String id, String name, ItemStack icon,
                                                             List<IPhantasiaMultiblockShape> shapes,
                                                             PhantasiaScriptData script,
                                                             net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.LivingEntity> mobType,
                                                             int scale) {
        return new VanillaMultiblockDefinition(new ResourceLocation(id), name, icon, shapes, script, mobType, scale, false, false);
    }
}
