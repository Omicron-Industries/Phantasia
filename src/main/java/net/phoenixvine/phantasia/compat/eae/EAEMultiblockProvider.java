package net.phoenixvine.phantasia.compat.eae;

import net.minecraft.resources.ResourceLocation;
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

import static net.phoenixvine.phantasia.compat.eae.EAESceneDefinition.*;

public class EAEMultiblockProvider implements IPhantasiaMultiblockProvider {

    private final Map<String, EAESceneDefinition> scenes = new LinkedHashMap<>();

    public EAEMultiblockProvider() {
        register(makeAssemblerMatrix());
        register(makeExtendedPatternProvider());
        register(makeOversizedInterface());
    }

    private void register(EAESceneDefinition def) {
        scenes.put(def.getId().toString(), def);
    }

    @Override
    public String getModId() {
        return "expatternprovider";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Optional<IPhantasiaMultiblockDefinition> resolve(String id) {
        EAESceneDefinition d = scenes.get(id);
        if (d != null) return Optional.of(d);
        d = scenes.get("expatternprovider:" + id);
        return Optional.ofNullable(d);
    }

    @Override
    public Optional<PhantasiaBlockInfo> resolveBlock(String id) {
        ResourceLocation rl = id.contains(":") ? new ResourceLocation(id) :
                new ResourceLocation("expatternprovider", id);
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
        if (rl == null || !"expatternprovider".equals(rl.getNamespace())) return false;
        return switch (rl.getPath()) {
            case "assembler_matrix_frame", "ex_pattern_provider", "oversize_interface" -> true;
            default -> false;
        };
    }

    @Override
    public boolean isPartBlock(net.minecraft.world.level.block.state.BlockState state) {
        ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (rl == null || !"expatternprovider".equals(rl.getNamespace())) return false;
        return switch (rl.getPath()) {
            case "assembler_matrix_frame", "assembler_matrix_wall", "assembler_matrix_glass", "assembler_matrix_pattern", "assembler_matrix_crafter", "ex_pattern_provider", "oversize_interface" -> true;
            default -> false;
        };
    }

    private static EAESceneDefinition makeAssemblerMatrix() {
        EAESceneShape shape = new EAESceneShape(() -> {
            PhantasiaBlockInfo frame = eae("assembler_matrix_frame");
            PhantasiaBlockInfo wall = eae("assembler_matrix_wall");
            PhantasiaBlockInfo pattern = eae("assembler_matrix_pattern");
            PhantasiaBlockInfo crafter = eae("assembler_matrix_crafter");
            if (wall == PhantasiaBlockInfo.EMPTY) wall = frame;

            PhantasiaBlockInfo[][][] g = new PhantasiaBlockInfo[3][3][3];
            for (PhantasiaBlockInfo[][] sl : g)
                for (PhantasiaBlockInfo[] row : sl)
                    Arrays.fill(row, PhantasiaBlockInfo.EMPTY);

            for (int x = 0; x < 3; x++) {
                for (int y = 0; y < 3; y++) {
                    for (int z = 0; z < 3; z++) {
                        boolean onSurface = (x == 0 || x == 2) || (y == 0 || y == 2) || (z == 0 || z == 2);
                        if (!onSurface) continue;
                        int edgeDims = 0;
                        if (x == 0 || x == 2) edgeDims++;
                        if (y == 0 || y == 2) edgeDims++;
                        if (z == 0 || z == 2) edgeDims++;
                        g[x][y][z] = (edgeDims >= 2) ? frame : wall;
                    }
                }
            }

            g[1][1][0] = pattern;
            g[1][1][1] = crafter;

            return g;
        });

        PhantasiaScriptData script = script("expatternprovider:assembler_matrix");
        script.addStep(step(0,
                "The Assembler Matrix is a self-contained autocrafting multiblock. Its shell is built from Frame, Wall, Pattern, and Crafter blocks — no external Pattern Providers or Molecular Assemblers needed.",
                "all"));
        script.addStep(step(140,
                "Build the hollow shell using Assembler Matrix Frame on all corners and edges, and Assembler Matrix Wall on the flat face centers.",
                "all"));
        script.addStep(step(300,
                "Replace one or more wall blocks with an Assembler Matrix Pattern block. These are the built-in pattern providers — load them with Encoded Patterns to define what the matrix crafts.",
                "pos:1,1,0"));
        script.addStep(step(460,
                "Fill the hollow interior with Assembler Matrix Crafter blocks. These are the built-in crafters — the matrix distributes work across all of them automatically.",
                "pos:1,1,1"));
        script.addStep(step(620,
                "Larger boxes mean more interior crafter slots and more pattern face blocks, scaling throughput without any external components.",
                "all"));
        script.addStep(workingStep(780,
                "Matrix running — encoded patterns are spread across every interior crafter simultaneously, providing far higher throughput than a conventional provider and assembler cluster.",
                "all"));

        return new EAESceneDefinition(
                new ResourceLocation("expatternprovider", "assembler_matrix"),
                "Assembler Matrix",
                "expatternprovider:assembler_matrix_frame",
                shape, script);
    }

    private static EAESceneDefinition makeExtendedPatternProvider() {
        EAESceneShape shape = new EAESceneShape(() -> {
            PhantasiaBlockInfo expp = eae("ex_pattern_provider");
            PhantasiaBlockInfo ma = ae2("molecular_assembler");

            PhantasiaBlockInfo[][][] g = new PhantasiaBlockInfo[3][1][3];
            for (PhantasiaBlockInfo[][] sl : g)
                Arrays.fill(sl[0], PhantasiaBlockInfo.EMPTY);

            g[1][0][1] = expp;
            g[0][0][1] = ma;
            g[2][0][1] = ma;
            g[1][0][0] = ma;
            g[1][0][2] = ma;
            return g;
        });

        PhantasiaScriptData script = script("expatternprovider:ex_pattern_provider");
        script.addStep(step(0,
                "The Extended Pattern Provider is a drop-in upgrade to the base ME Pattern Provider, holding significantly more Encoded Patterns in a single block.",
                "pos:1,0,1"));
        script.addStep(step(120,
                "It functions identically to the standard provider: place Molecular Assemblers on any of its 6 faces to receive work automatically.",
                "all"));
        script.addStep(step(280,
                "The higher pattern capacity means fewer provider blocks needed for complex crafting trees — ideal for large multi-step autocraft setups.",
                "pos:1,0,1"));
        script.addStep(workingStep(420,
                "Patterns loaded — the extended provider distributes work across all adjacent assemblers simultaneously, just like its standard counterpart but at greater scale.",
                "all"));

        return new EAESceneDefinition(
                new ResourceLocation("expatternprovider", "ex_pattern_provider"),
                "Extended Pattern Provider",
                "expatternprovider:ex_pattern_provider",
                shape, script);
    }

    private static EAESceneDefinition makeOversizedInterface() {
        EAESceneShape shape = new EAESceneShape(() -> {
            PhantasiaBlockInfo ovIface = eae("oversize_interface");
            PhantasiaBlockInfo ctrl = ae2("controller");
            PhantasiaBlockInfo drive = ae2("drive");

            PhantasiaBlockInfo[][][] g = new PhantasiaBlockInfo[3][2][1];
            for (PhantasiaBlockInfo[][] sl : g)
                Arrays.fill(sl[0], PhantasiaBlockInfo.EMPTY);

            g[0][0][0] = ctrl;
            g[0][1][0] = drive;
            g[1][0][0] = ovIface;
            g[2][0][0] = ctrl;
            g[2][1][0] = drive;
            return g;
        });

        PhantasiaScriptData script = script("expatternprovider:oversize_interface");
        script.addStep(step(0,
                "The Oversized Interface is Extended AE's expanded ME Interface. It supports far more import/export slots and higher transfer throughput than the standard Interface.",
                "pos:1,0,0"));
        script.addStep(step(120,
                "Like the base Interface, it bridges two ME Networks or connects a subnet back to the main network — but with significantly increased bandwidth.",
                "all"));
        script.addStep(step(300,
                "Use it when a standard Interface becomes the bottleneck: large crafting subnets, heavy item transfer pipelines, or cross-network logistics setups.",
                "pos:1,0,0"));
        script.addStep(workingStep(440,
                "Both networks online — the Oversized Interface handles high-volume item flow without the throughput limits of its standard counterpart.",
                "all"));

        return new EAESceneDefinition(
                new ResourceLocation("expatternprovider", "oversize_interface"),
                "Oversized Interface",
                "expatternprovider:oversize_interface",
                shape, script);
    }
}
