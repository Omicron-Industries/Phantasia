package net.phoenixvine.phantasia.compat.tfc;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockProvider;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static net.phoenixvine.phantasia.compat.tfc.TFCSceneDefinition.*;

public class TFCMultiblockProvider implements IPhantasiaMultiblockProvider {

    private final Map<String, TFCSceneDefinition> scenes = new LinkedHashMap<>();

    public TFCMultiblockProvider() {
        register(makeBloomery());
        register(makeBlastFurnace());
        register(makeCharcoalForge());
        register(makePitKiln());
    }

    private void register(TFCSceneDefinition def) {
        scenes.put(def.getId().toString(), def);
    }

    @Override
    public String getModId() {
        return "tfc";
    }

    @Override
    public boolean isAvailable() {
        return ModList.get().isLoaded("tfc");
    }

    @Override
    public Optional<IPhantasiaMultiblockDefinition> resolve(String id) {
        TFCSceneDefinition d = scenes.get(id);
        if (d != null) return Optional.of(d);
        d = scenes.get("tfc:" + id);
        return Optional.ofNullable(d);
    }

    @Override
    public Optional<PhantasiaBlockInfo> resolveBlock(String id) {
        ResourceLocation rl = id.contains(":") ? new ResourceLocation(id) : new ResourceLocation("tfc", id);
        net.minecraft.world.level.block.Block b = ForgeRegistries.BLOCKS.getValue(rl);
        if (b == null || b == net.minecraft.world.level.block.Blocks.AIR) return Optional.empty();
        return Optional.of(PhantasiaBlockInfo.fromBlockState(b.defaultBlockState()));
    }

    @Override
    public List<IPhantasiaMultiblockDefinition> getAllDefinitions() {
        return new ArrayList<>(scenes.values());
    }

    @Override
    public boolean isControllerBlock(net.minecraft.world.level.block.state.BlockState state) {
        ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (rl == null || !"tfc".equals(rl.getNamespace())) return false;
        return switch (rl.getPath()) {
            case "bloomery", "blast_furnace", "charcoal_forge", "firepit", "pit_kiln" -> true;
            default -> false;
        };
    }

    @Override
    public boolean isPartBlock(net.minecraft.world.level.block.state.BlockState state) {
        ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (rl == null || !"tfc".equals(rl.getNamespace())) return false;
        return switch (rl.getPath()) {
            case "bloomery", "blast_furnace", "charcoal_forge", "firepit", "pit_kiln", "bellows", "rock/bricks/granite", "rock/bricks/basalt", "rock/bricks/rhyolite", "rock/cobblestone/granite", "log_pile" -> true;
            default -> false;
        };
    }

    private static TFCSceneDefinition makeBloomery() {
        TFCSceneShape shape = new TFCSceneShape(() -> {
            PhantasiaBlockInfo bloomery = tfc("bloomery");
            PhantasiaBlockInfo brick = tfc("rock/bricks/granite");
            if (brick == PhantasiaBlockInfo.EMPTY) brick = tfc("rock/cobblestone/granite");

            PhantasiaBlockInfo[][][] g = new PhantasiaBlockInfo[3][4][3];
            for (PhantasiaBlockInfo[][] sl : g)
                for (PhantasiaBlockInfo[] row : sl) Arrays.fill(row, PhantasiaBlockInfo.EMPTY);

            for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) g[x][0][z] = brick;
            g[1][0][1] = bloomery;

            for (int y = 1; y <= 3; y++) {
                for (int x = 0; x < 3; x++) {
                    g[x][y][0] = brick;
                    g[x][y][2] = brick;
                }
                g[0][y][1] = brick;
                g[2][y][1] = brick;
            }

            return g;
        });

        PhantasiaScriptData script = script("tfc:bloomery");
        script.addStep(step(0,
                "The Bloomery converts Iron Ore into Pig Iron (Bloom). It is your first step into metalworking.",
                "all"));
        script.addStep(step(100,
                "Build a 3×3 base of any non-flammable stone blocks. The Bloomery block sits in the center.",
                "layer:0"));
        script.addStep(step(240,
                "Stack hollow stone walls at least 3 blocks high around the Bloomery block. The top must remain open.",
                "layers:1-3"));
        script.addStep(step(400,
                "Fill the interior with alternating Charcoal and Iron Ore, then light the Bloomery block.",
                "all"));
        script.addStep(workingStep(540,
                "The chimney ignites. When the fuel burns down, a Bloom forms inside — extract it with a Hammer on an Anvil to get Wrought Iron.",
                "all"));

        return new TFCSceneDefinition(
                new ResourceLocation("tfc", "bloomery"),
                "Bloomery",
                "tfc:bloomery",
                shape, script);
    }

    private static TFCSceneDefinition makeBlastFurnace() {
        TFCSceneShape shape = new TFCSceneShape(() -> {
            PhantasiaBlockInfo blastFurnace = tfc("blast_furnace");
            PhantasiaBlockInfo brick = tfc("rock/bricks/granite");
            if (brick == PhantasiaBlockInfo.EMPTY) brick = tfc("rock/cobblestone/granite");
            PhantasiaBlockInfo bellows = tfc("bellows");

            PhantasiaBlockInfo[][][] g = new PhantasiaBlockInfo[3][5][4];
            for (PhantasiaBlockInfo[][] sl : g)
                for (PhantasiaBlockInfo[] row : sl) Arrays.fill(row, PhantasiaBlockInfo.EMPTY);

            for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) g[x][0][z] = brick;
            g[1][0][1] = blastFurnace;

            for (int y = 1; y <= 4; y++) {
                for (int x = 0; x < 3; x++) {
                    g[x][y][0] = brick;
                    g[x][y][2] = brick;
                }
                g[0][y][1] = brick;
                g[2][y][1] = brick;
            }

            g[1][1][3] = bellows;

            return g;
        });

        PhantasiaScriptData script = script("tfc:blast_furnace");
        script.addStep(step(0,
                "The Blast Furnace converts Pig Iron (Bloom) into Steel — the strongest metal in base TFC.",
                "all"));
        script.addStep(step(100,
                "Build the same 3×3 hollow chimney as a Bloomery, but at least 4 blocks tall. Use heat-resistant blocks.",
                "layers:0-4"));
        script.addStep(step(260,
                "The Blast Furnace block sits in the center of the base. It is lit from above.",
                "layer:0"));
        script.addStep(step(400,
                "Bellows must be placed adjacent and driven by a Windmill or Waterwheel to maintain the temperature required for steel.",
                "pos:1,1,3"));
        script.addStep(step(560,
                "Load alternating Pig Iron and Charcoal from the top. The furnace needs time at high temperature to convert the metal.",
                "all"));
        script.addStep(workingStep(700,
                "The furnace ignites and runs at extreme heat. Steel Ingots collect at the base when the process completes.",
                "all"));

        return new TFCSceneDefinition(
                new ResourceLocation("tfc", "blast_furnace"),
                "Blast Furnace",
                "tfc:blast_furnace",
                shape, script);
    }

    private static TFCSceneDefinition makeCharcoalForge() {
        TFCSceneShape shape = new TFCSceneShape(() -> {
            PhantasiaBlockInfo forge = tfc("charcoal_forge");
            PhantasiaBlockInfo brick = tfc("rock/bricks/granite");
            if (brick == PhantasiaBlockInfo.EMPTY) brick = tfc("rock/cobblestone/granite");
            PhantasiaBlockInfo bellows = tfc("bellows");

            PhantasiaBlockInfo[][][] g = new PhantasiaBlockInfo[3][2][3];
            for (PhantasiaBlockInfo[][] sl : g)
                for (PhantasiaBlockInfo[] row : sl) Arrays.fill(row, PhantasiaBlockInfo.EMPTY);

            g[0][0][1] = brick;
            g[1][0][1] = forge;
            g[2][0][1] = brick;
            g[0][0][2] = brick;
            g[1][0][2] = brick;
            g[2][0][2] = brick;

            g[1][1][2] = bellows;

            return g;
        });

        PhantasiaScriptData script = script("tfc:charcoal_forge");
        script.addStep(step(0,
                "The Charcoal Forge heats metal pieces for working on the Anvil. It is the primary smithing tool throughout early and mid-game.",
                "all"));
        script.addStep(step(100,
                "Place the Charcoal Forge block with at least 3 solid non-flammable blocks on the back and sides. The front must be open.",
                "layer:0"));
        script.addStep(step(240,
                "Right-click with Charcoal to fuel it, then use a Firestarter or Flint & Steel to ignite.",
                "all"));
        script.addStep(step(380,
                "A Bellows placed above or beside the forge raises heat output, reaching temperatures needed for Steel and higher-tier metals.",
                "pos:1,1,2"));
        script.addStep(workingStep(520,
                "At maximum heat, the forge glows intensely. Place metal items inside and monitor their temperature — remove them at the right moment to work on the Anvil.",
                "all"));

        return new TFCSceneDefinition(
                new ResourceLocation("tfc", "charcoal_forge"),
                "Charcoal Forge",

                "tfc:metal/tuyere/wrought_iron",
                shape, script);
    }

    private static TFCSceneDefinition makePitKiln() {
        TFCSceneShape shape = new TFCSceneShape(() -> {
            PhantasiaBlockInfo pitKiln = tfc("pit_kiln");
            PhantasiaBlockInfo dirt = PhantasiaBlockInfo.fromBlockState(
                    net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
            PhantasiaBlockInfo log = tfc("log_pile");
            if (log == PhantasiaBlockInfo.EMPTY) log = PhantasiaBlockInfo.fromBlockState(
                    net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState());

            PhantasiaBlockInfo[][][] g = new PhantasiaBlockInfo[3][4][3];
            for (PhantasiaBlockInfo[][] sl : g)
                for (PhantasiaBlockInfo[] row : sl) Arrays.fill(row, PhantasiaBlockInfo.EMPTY);

            for (int y = 0; y <= 1; y++) {
                for (int x = 0; x < 3; x++) {
                    g[x][y][0] = dirt;
                    g[x][y][2] = dirt;
                }
                g[0][y][1] = dirt;
                g[2][y][1] = dirt;
            }

            g[1][2][1] = pitKiln;
            g[1][3][1] = log;

            return g;
        });

        PhantasiaScriptData script = script("tfc:pit_kiln");
        script.addStep(step(0,
                "The Pit Kiln fires ceramic and pottery items. It is the earliest kiln available and requires no crafting station to build.",
                "all"));
        script.addStep(step(100,
                "Dig a 1×1 hole at least 1 block deep. The surrounding dirt walls act as insulation during the burn.",
                "layers:0-1"));
        script.addStep(step(240,
                "Right-click the pit with unfired ceramic items to fill it (up to 8 items). A Pit Kiln block appears.",
                "layer:2"));
        script.addStep(step(380,
                "Stack logs on top of the Pit Kiln (up to 8). More logs give a longer burn, safely firing more items.",
                "layer:3"));
        script.addStep(workingStep(520,
                "Light the logs. The fire slowly burns down into the pit, firing the ceramics. Do NOT dig it up while still burning.",
                "all"));
        script.addStep(step(660,
                "Once cool, right-click to retrieve your fired ceramics. Fired items are permanent — unfired ones crumble in water.",
                "all"));

        return new TFCSceneDefinition(
                new ResourceLocation("tfc", "pit_kiln"),
                "Pit Kiln",

                "tfc:ceramic/unfired_bowl",
                shape, script);
    }
}
