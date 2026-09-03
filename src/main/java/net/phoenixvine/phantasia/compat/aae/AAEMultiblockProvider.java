package net.phoenixvine.phantasia.compat.aae;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
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

import static net.phoenixvine.phantasia.compat.aae.AAESceneDefinition.*;

public class AAEMultiblockProvider implements IPhantasiaMultiblockProvider {

    private final Map<String, AAESceneDefinition> scenes = new LinkedHashMap<>();

    public AAEMultiblockProvider() {
        register(makeQuantumSupercomputer());
        register(makeAdvancedPatternProvider());
        register(makeReactionChamber());
    }

    private void register(AAESceneDefinition def) {
        scenes.put(def.getId().toString(), def);
    }

    @Override
    public String getModId() {
        return "advanced_ae";
    }

    @Override
    public boolean isAvailable() {
        return ModList.get().isLoaded("advanced_ae");
    }

    @Override
    public Optional<IPhantasiaMultiblockDefinition> resolve(String id) {
        AAESceneDefinition d = scenes.get(id);
        if (d != null) return Optional.of(d);
        d = scenes.get("advanced_ae:" + id);
        return Optional.ofNullable(d);
    }

    @Override
    public Optional<PhantasiaBlockInfo> resolveBlock(String id) {
        ResourceLocation rl = id.contains(":") ? new ResourceLocation(id) : new ResourceLocation("advanced_ae", id);
        net.minecraft.world.level.block.Block b = ForgeRegistries.BLOCKS.getValue(rl);
        if (b == null || b == net.minecraft.world.level.block.Blocks.AIR) return Optional.empty();
        return Optional.of(PhantasiaBlockInfo.fromBlockState(b.defaultBlockState()));
    }

    @Override
    public List<IPhantasiaMultiblockDefinition> getAllDefinitions() {
        return new ArrayList<>(scenes.values());
    }

    @Override
    public boolean isControllerBlock(BlockState state) {
        ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (rl == null || !"advanced_ae".equals(rl.getNamespace())) return false;
        return switch (rl.getPath()) {
            case "quantum_core", "reaction_chamber", "quantum_crafter" -> true;
            default -> false;
        };
    }

    @Override
    public boolean isPartBlock(BlockState state) {
        ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (rl == null || !"advanced_ae".equals(rl.getNamespace())) return false;
        return switch (rl.getPath()) {
            case "quantum_core", "quantum_unit", "quantum_structure", "quantum_accelerator", "quantum_multi_threader", "quantum_storage_128", "quantum_storage_256", "data_entangler", "reaction_chamber", "quantum_crafter", "adv_pattern_provider", "small_adv_pattern_provider" -> true;
            default -> false;
        };
    }

    private static AAESceneDefinition makeQuantumSupercomputer() {
        AAESceneShape shape = new AAESceneShape(() -> {
            PhantasiaBlockInfo core = aae("quantum_core");
            PhantasiaBlockInfo unit = aae("quantum_unit");
            PhantasiaBlockInfo struc = aae("quantum_structure");
            PhantasiaBlockInfo accel = aae("quantum_accelerator");
            PhantasiaBlockInfo mt = aae("quantum_multi_threader");
            PhantasiaBlockInfo s256 = aae("quantum_storage_256");
            PhantasiaBlockInfo s128 = aae("quantum_storage_128");
            PhantasiaBlockInfo de = aae("data_entangler");

            PhantasiaBlockInfo[][][] g = new PhantasiaBlockInfo[3][3][3];
            for (PhantasiaBlockInfo[][] sl : g)
                for (PhantasiaBlockInfo[] row : sl)
                    Arrays.fill(row, struc);

            g[1][1][1] = core;

            g[0][1][1] = accel;
            g[2][1][1] = accel;
            g[1][1][0] = mt;
            g[1][1][2] = mt;

            g[0][1][0] = s256;
            g[2][1][0] = s128;
            g[0][1][2] = de;
            g[2][1][2] = unit;

            return g;
        });

        PhantasiaScriptData script = script("advanced_ae:quantum_supercomputer");
        script.addStep(step(0,
                "The Quantum Core is Advanced AE's powerhouse block. Standalone, it already provides powerful autocrafting with an integrated pattern slot.",
                "pos:1,1,1"));
        script.addStep(step(140,
                "To unlock its full potential, build the Quantum Supercomputer multiblock. Start by surrounding the core at the same height with component blocks.",
                "layer:1"));
        script.addStep(step(300,
                "Quantum Accelerators and Multi-Threaders go on the face-center positions — each one adds parallel crafting threads. Storage blocks on the corners expand the job queue.",
                "layer:1"));
        script.addStep(step(460,
                "Cap the structure above and below with solid layers of Quantum Structure blocks. The full 3×3×3 (or larger) box must be complete for the multiblock to form.",
                "layers:0-2"));
        script.addStep(step(620,
                "The Quantum Supercomputer scales by size — larger structures (up to 7×7×7) fit more components, dramatically increasing autocrafting throughput and storage capacity.",
                "all"));
        script.addStep(workingStep(780,
                "Quantum Supercomputer formed — the Core pulses with power as all components lock into a unified crafting matrix, ready to handle any autocrafting request at scale.",
                "all"));

        return new AAESceneDefinition(
                new ResourceLocation("advanced_ae", "quantum_supercomputer"),
                "Quantum Supercomputer",
                "quantum_core",
                shape, script);
    }

    private static AAESceneDefinition makeAdvancedPatternProvider() {
        AAESceneShape shape = new AAESceneShape(() -> {
            PhantasiaBlockInfo app = aae("adv_pattern_provider");
            PhantasiaBlockInfo ma = ae2("molecular_assembler");

            PhantasiaBlockInfo[][][] g = new PhantasiaBlockInfo[3][1][3];
            for (PhantasiaBlockInfo[][] sl : g)
                Arrays.fill(sl[0], PhantasiaBlockInfo.EMPTY);

            g[1][0][1] = app;
            g[0][0][1] = ma;
            g[2][0][1] = ma;
            g[1][0][0] = ma;
            g[1][0][2] = ma;
            return g;
        });

        PhantasiaScriptData script = script("advanced_ae:adv_pattern_provider");
        script.addStep(step(0,
                "The Advanced Pattern Provider is a supercharged ME Pattern Provider. It holds far more patterns and supports directional push — targeting a specific face instead of broadcasting to all adjacent machines.",
                "pos:1,0,1"));
        script.addStep(step(140,
                "Like the standard provider, place Molecular Assemblers on its faces to receive work. Each assembler processes one pattern at a time.",
                "all"));
        script.addStep(step(300,
                "Configure the push direction to send ingredients only to the assembler on a specific face — useful when different faces connect to different machine types.",
                "pos:1,0,1"));
        script.addStep(step(460,
                "Set push_direction to 'all' (the default) to broadcast to every adjacent machine simultaneously, maximising throughput for large pattern sets.",
                "all"));
        script.addStep(workingStep(600,
                "Provider active — patterns distributed, assemblers crafting in parallel with full directional control over ingredient routing.",
                "all"));

        return new AAESceneDefinition(
                new ResourceLocation("advanced_ae", "adv_pattern_provider"),
                "Advanced Pattern Provider",
                "adv_pattern_provider",
                shape, script);
    }

    private static AAESceneDefinition makeReactionChamber() {
        AAESceneShape shape = new AAESceneShape(() -> {
            PhantasiaBlockInfo rc = aae("reaction_chamber");
            PhantasiaBlockInfo ctrl = ae2("controller");

            PhantasiaBlockInfo[][][] g = new PhantasiaBlockInfo[2][1][1];
            g[0][0][0] = ctrl;
            g[1][0][0] = rc;
            return g;
        });

        PhantasiaScriptData script = script("advanced_ae:reaction_chamber");
        script.addStep(step(0,
                "The Reaction Chamber is Advanced AE's crafting machine for special patterns — primarily used to craft Quantum Alloy and other advanced AE2 materials.",
                "pos:1,0,0"));
        script.addStep(step(140,
                "Connect it directly to your ME Network via any cable or controller. It draws ingredients from the network and pushes results back automatically.",
                "all"));
        script.addStep(step(300,
                "Encode your Reaction Chamber recipes as Processing Patterns in an ME Pattern Encoder and insert them into an adjacent Pattern Provider — the chamber handles the rest.",
                "pos:1,0,0"));
        script.addStep(workingStep(440,
                "Reaction Chamber active — ingredients consumed, quantum reaction in progress. Finished materials are pushed back into the ME Network on completion.",
                "all"));

        return new AAESceneDefinition(
                new ResourceLocation("advanced_ae", "reaction_chamber"),
                "Reaction Chamber",
                "reaction_chamber",
                shape, script);
    }
}
