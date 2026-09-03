package net.phoenixvine.phantasia.compat.ae2;

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

import static net.phoenixvine.phantasia.compat.ae2.AE2SceneDefinition.*;

public class AE2MultiblockProvider implements IPhantasiaMultiblockProvider {

    private final Map<String, AE2SceneDefinition> scenes = new LinkedHashMap<>();

    public AE2MultiblockProvider() {
        register(makeQuantumNetworkBridge());
        register(makeCraftingCPU());
        register(makeMEController());
        register(makeSpatialIO());
        register(makePatternProvider());
        register(makeMEInterface());
        register(makeMEDrive());
        register(makeCraftingSubnet());
    }

    private void register(AE2SceneDefinition def) {
        scenes.put(def.getId().toString(), def);
    }

    @Override
    public String getModId() {
        return "ae2";
    }

    @Override
    public boolean isAvailable() {
        return ModList.get().isLoaded("ae2");
    }

    @Override
    public Optional<IPhantasiaMultiblockDefinition> resolve(String id) {
        AE2SceneDefinition d = scenes.get(id);
        if (d != null) return Optional.of(d);
        d = scenes.get("ae2:" + id);
        return Optional.ofNullable(d);
    }

    @Override
    public Optional<PhantasiaBlockInfo> resolveBlock(String id) {
        ResourceLocation rl = id.contains(":") ? new ResourceLocation(id) : new ResourceLocation("ae2", id);
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
        return rl != null && "ae2".equals(rl.getNamespace()) && "controller".equals(rl.getPath());
    }

    @Override
    public boolean isPartBlock(net.minecraft.world.level.block.state.BlockState state) {
        ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (rl == null || !"ae2".equals(rl.getNamespace())) return false;
        return switch (rl.getPath()) {
            case "controller", "quantum_ring", "quantum_link", "crafting_unit", "1k_crafting_storage", "4k_crafting_storage", "16k_crafting_storage", "64k_crafting_storage", "crafting_accelerator", "crafting_monitor", "spatial_pylon", "pattern_provider", "molecular_assembler", "interface", "drive", "energy_cell", "dense_energy_cell" -> true;
            default -> false;
        };
    }

    private static AE2SceneDefinition makeQuantumNetworkBridge() {
        AE2SceneShape shape = new AE2SceneShape(() -> {
            PhantasiaBlockInfo qr = ae2("quantum_ring");
            PhantasiaBlockInfo ql = ae2("quantum_link");

            PhantasiaBlockInfo[][][] grid = new PhantasiaBlockInfo[3][3][5];
            for (PhantasiaBlockInfo[][] xSlice : grid)
                for (PhantasiaBlockInfo[] yRow : xSlice)
                    Arrays.fill(yRow, PhantasiaBlockInfo.EMPTY);

            for (int x = 0; x < 3; x++) {
                for (int y = 0; y < 3; y++) {
                    PhantasiaBlockInfo block = (x == 1 && y == 1) ? ql : qr;
                    grid[x][y][0] = block;
                    grid[x][y][4] = block;
                }
            }
            return grid;
        });

        PhantasiaScriptData script = script("ae2:quantum_network_bridge");
        script.addStep(step(0,
                "The Quantum Network Bridge links two ME Networks across any distance — even across dimensions.",
                "all"));
        script.addStep(step(80,
                "Each bridge requires two identical 3×3 rings. This is Ring A.",
                "layer:0"));
        script.addStep(step(200,
                "Ring B is built identically, placed anywhere — even in another dimension.",
                "layer:4"));
        script.addStep(step(340,
                "The center of each ring holds a Quantum Link Card. The eight surrounding blocks are Quantum Rings.",
                "all"));
        script.addStep(workingStep(480,
                "Both rings powered and linked with Quantum Entangled Singularities — the bridge activates, connecting the two networks.",
                "all"));

        return new AE2SceneDefinition(
                new ResourceLocation("ae2", "quantum_network_bridge"),
                "Quantum Network Bridge",
                "quantum_ring",
                shape, script);
    }

    private static AE2SceneDefinition makeCraftingCPU() {
        AE2SceneShape shape = new AE2SceneShape(() -> {
            PhantasiaBlockInfo unit = ae2("crafting_unit");
            PhantasiaBlockInfo s1k = ae2("1k_crafting_storage");
            PhantasiaBlockInfo s4k = ae2("4k_crafting_storage");
            PhantasiaBlockInfo acc = ae2("crafting_accelerator");
            PhantasiaBlockInfo mon = ae2("crafting_monitor");

            PhantasiaBlockInfo[][][] g = new PhantasiaBlockInfo[3][3][3];
            for (int x = 0; x < 3; x++)
                for (int y = 0; y < 3; y++)
                    for (int z = 0; z < 3; z++)
                        g[x][y][z] = unit;

            g[0][0][0] = s1k;
            g[2][0][0] = s4k;
            g[0][0][2] = s4k;
            g[2][0][2] = s1k;
            g[1][0][0] = acc;
            g[1][0][2] = acc;
            g[0][0][1] = acc;
            g[2][0][1] = acc;

            g[1][1][0] = mon;
            g[0][1][1] = acc;
            g[2][1][1] = acc;
            g[1][1][2] = acc;

            g[0][2][0] = s4k;
            g[2][2][0] = s1k;
            g[0][2][2] = s1k;
            g[2][2][2] = s4k;
            g[1][2][0] = acc;
            g[1][2][2] = acc;
            g[0][2][1] = acc;
            g[2][2][1] = acc;

            return g;
        });

        PhantasiaScriptData script = script("ae2:crafting_cpu");
        script.addStep(step(0,
                "The Crafting CPU handles large or multi-step autocrafting jobs. It must be a solid rectangular box.",
                "all"));
        script.addStep(step(80,
                "Crafting Units are the basic filler block. Every CPU needs at least one.",
                "layer:1"));
        script.addStep(step(200,
                "Crafting Storage (1k, 4k, 16k, 64k) holds the items being processed mid-craft.",
                "pos:0,0,0:2,0,2"));
        script.addStep(step(340,
                "Crafting Co-Processors (Accelerators) each add one parallel crafting thread, speeding up complex jobs.",
                "all"));
        script.addStep(step(480,
                "The Crafting Monitor shows the currently running job. It must sit on an outer face of the CPU.",
                "pos:1,1,0"));
        script.addStep(workingStep(600,
                "CPU connected and active — the blocks lock together into their formed state, ready to process autocrafting requests.",
                "all"));

        return new AE2SceneDefinition(
                new ResourceLocation("ae2", "crafting_cpu"),
                "Crafting CPU",
                "crafting_unit",
                shape, script);
    }

    private static AE2SceneDefinition makeMEController() {
        AE2SceneShape shape = new AE2SceneShape(() -> {
            PhantasiaBlockInfo ctrl = ae2("controller");
            PhantasiaBlockInfo drv = ae2("drive");

            PhantasiaBlockInfo[][][] g = new PhantasiaBlockInfo[3][2][1];
            for (PhantasiaBlockInfo[][] xSlice : g)
                for (PhantasiaBlockInfo[] yRow : xSlice)
                    Arrays.fill(yRow, PhantasiaBlockInfo.EMPTY);

            g[0][0][0] = drv;
            g[1][0][0] = ctrl;
            g[2][0][0] = drv;
            g[1][1][0] = ctrl;

            return g;
        });

        PhantasiaScriptData script = script("ae2:me_controller");
        script.addStep(step(0,
                "The ME Controller is the heart of every ME Network. It must receive power to operate.",
                "pos:1,0,0"));
        script.addStep(step(120,
                "Controllers can be stacked up to 7×7×7 to increase the channel capacity of your network.",
                "all"));
        script.addStep(step(260,
                "ME Drives connect to the controller (directly or via cable) and hold Storage Cells.",
                "pos:0,0,0:2,0,0"));
        script.addStep(step(400,
                "Each face of the controller provides 32 channels. Dense cables carry up to 32; glass cables carry 8.",
                "all"));
        script.addStep(workingStep(540,
                "The controller comes online — its faces pulse with channel activity, ready to serve the network.",
                "all"));

        return new AE2SceneDefinition(
                new ResourceLocation("ae2", "me_controller"),
                "ME Controller",
                "controller",
                shape, script);
    }

    private static AE2SceneDefinition makeSpatialIO() {
        AE2SceneShape shape = new AE2SceneShape(() -> {
            PhantasiaBlockInfo pylon = ae2("spatial_pylon");
            PhantasiaBlockInfo port = ae2("spatial_io_port");

            PhantasiaBlockInfo[][][] g = new PhantasiaBlockInfo[5][3][3];
            for (PhantasiaBlockInfo[][] xSlice : g)
                for (PhantasiaBlockInfo[] yRow : xSlice)
                    Arrays.fill(yRow, PhantasiaBlockInfo.EMPTY);

            g[0][1][1] = port;

            for (int y = 0; y < 3; y++) {
                g[1][y][0] = pylon;
                g[3][y][0] = pylon;
                g[1][y][2] = pylon;
                g[3][y][2] = pylon;
            }

            return g;
        });

        PhantasiaScriptData script = script("ae2:spatial_io");
        script.addStep(step(0,
                "Spatial IO lets you move entire regions of the world — blocks, entities, and all — into a Spatial Storage Cell.",
                "all"));
        script.addStep(step(100,
                "Spatial Pylons define the corners of the region to capture. They must form a complete rectangular cage.",
                "pos:1,0,0:3,2,0:1,0,2:3,2,2"));
        script.addStep(step(240,
                "The minimum region is 2×2×2. Larger pylon cages capture larger regions but cost proportionally more power.",
                "all"));
        script.addStep(step(380,
                "The Spatial IO Port triggers the capture. It must be connected to your ME Network and have a Spatial Storage Cell inserted.",
                "pos:0,1,1"));
        script.addStep(step(520,
                "Activating the port consumes enormous power (proportional to region volume) and teleports the contents into the cell.",
                "all"));
        script.addStep(step(660,
                "The stored region can be pasted back at any time — even to a different location. Perfect for moving your base.",
                "all"));

        return new AE2SceneDefinition(
                new ResourceLocation("ae2", "spatial_io"),
                "Spatial IO",
                "spatial_pylon",
                shape, script);
    }

    private static AE2SceneDefinition makePatternProvider() {
        AE2SceneShape shape = new AE2SceneShape(() -> {
            PhantasiaBlockInfo pp = ae2("pattern_provider");
            PhantasiaBlockInfo ma = ae2("molecular_assembler");

            PhantasiaBlockInfo[][][] g = new PhantasiaBlockInfo[3][1][3];
            for (PhantasiaBlockInfo[][] sl : g)
                Arrays.fill(sl[0], PhantasiaBlockInfo.EMPTY);

            g[1][0][1] = pp;
            g[0][0][1] = ma;
            g[2][0][1] = ma;
            g[1][0][0] = ma;
            g[1][0][2] = ma;
            return g;
        });

        PhantasiaScriptData script = script("ae2:pattern_provider");
        script.addStep(step(0,
                "The Pattern Provider is the heart of AE2 autocrafting. It holds Encoded Patterns and pushes ingredients to adjacent machines.",
                "pos:1,0,1"));
        script.addStep(step(100,
                "Molecular Assemblers are the simplest crafting machines. Place them on any face of a Pattern Provider to receive work automatically.",
                "all"));
        script.addStep(step(240,
                "Each Molecular Assembler processes one pattern at a time. Surround the provider on all 6 faces for 6× throughput.",
                "all"));
        script.addStep(step(400,
                "Insert a Crafting Card into the Pattern Provider to make it pull its own ingredients from the ME Network instead of waiting for a push.",
                "pos:1,0,1"));
        script.addStep(workingStep(540,
                "Patterns loaded, ingredients flowing — the assemblers craft in parallel and push finished items back into the network.",
                "all"));

        return new AE2SceneDefinition(
                new ResourceLocation("ae2", "pattern_provider"),
                "Pattern Provider",
                "pattern_provider",
                shape, script);
    }

    private static AE2SceneDefinition makeMEInterface() {
        AE2SceneShape shape = new AE2SceneShape(() -> {
            PhantasiaBlockInfo ctrl = ae2("controller");
            PhantasiaBlockInfo iface = ae2("interface");
            PhantasiaBlockInfo drive = ae2("drive");

            PhantasiaBlockInfo[][][] g = new PhantasiaBlockInfo[3][2][1];
            for (PhantasiaBlockInfo[][] sl : g)
                Arrays.fill(sl[0], PhantasiaBlockInfo.EMPTY);

            g[0][0][0] = ctrl;
            g[0][1][0] = drive;
            g[1][0][0] = iface;
            g[2][0][0] = ctrl;
            g[2][1][0] = iface;
            return g;
        });

        PhantasiaScriptData script = script("ae2:me_interface");
        script.addStep(step(0,
                "The ME Interface bridges two ME Networks — or connects a subnet back to the main network. Items and channels flow through it.",
                "pos:1,0,0"));
        script.addStep(step(120,
                "The left side represents a main ME Network: a Controller and Drive array handling all your base storage.",
                "pos:0,0,0:0,1,0"));
        script.addStep(step(280,
                "The right side is a dedicated autocrafting subnet — its own Controller and Pattern Provider cluster, isolated from main network channels.",
                "pos:2,0,0:2,1,0"));
        script.addStep(step(440,
                "The Interface between them passes crafting requests and items in both directions. The subnet uses no channels on the main network except the one the interface itself occupies.",
                "all"));
        script.addStep(workingStep(580,
                "Main network online, subnet active — the interface ferries ingredients and results transparently, keeping your channel budget lean.",
                "all"));

        return new AE2SceneDefinition(
                new ResourceLocation("ae2", "me_interface"),
                "ME Interface",
                "interface",
                shape, script);
    }

    private static AE2SceneDefinition makeMEDrive() {
        AE2SceneShape shape = new AE2SceneShape(() -> {
            PhantasiaBlockInfo ctrl = ae2("controller");
            PhantasiaBlockInfo drive = ae2("drive");
            PhantasiaBlockInfo cell = ae2("dense_energy_cell");

            PhantasiaBlockInfo[][][] g = new PhantasiaBlockInfo[3][2][1];
            for (PhantasiaBlockInfo[][] sl : g)
                Arrays.fill(sl[0], PhantasiaBlockInfo.EMPTY);

            g[0][0][0] = ctrl;
            g[0][1][0] = ctrl;
            g[1][0][0] = drive;
            g[1][1][0] = drive;
            g[2][0][0] = cell;
            return g;
        });

        PhantasiaScriptData script = script("ae2:me_drive");
        script.addStep(step(0,
                "The ME Drive is the primary storage block in AE2. It accepts up to 10 Storage Cells, each holding thousands of item types.",
                "pos:1,0,0:1,1,0"));
        script.addStep(step(120,
                "Storage Cells come in 1k, 4k, 16k, 64k, and 256k variants (item count, not stack size). Higher tiers store exponentially more but cost more bytes per type.",
                "pos:1,0,0:1,1,0"));
        script.addStep(step(280,
                "Drives must be connected to a Controller to join the ME Network and consume exactly 1 channel per drive.",
                "pos:0,0,0:0,1,0"));
        script.addStep(step(440,
                "ME Networks require constant power. An Energy Cell or Acceptor buffers energy from your power system — without power, the network goes offline.",
                "pos:2,0,0"));
        script.addStep(workingStep(580,
                "Network powered and drives online — your entire inventory is now digitised and accessible from any terminal on the network.",
                "all"));

        return new AE2SceneDefinition(
                new ResourceLocation("ae2", "me_drive"),
                "ME Drive",
                "drive",
                shape, script);
    }

    private static AE2SceneDefinition makeCraftingSubnet() {
        AE2SceneShape shape = new AE2SceneShape(() -> {
            PhantasiaBlockInfo ctrl = ae2("controller");
            PhantasiaBlockInfo pp = ae2("pattern_provider");
            PhantasiaBlockInfo ma = ae2("molecular_assembler");
            PhantasiaBlockInfo iface = ae2("interface");

            PhantasiaBlockInfo[][][] g = new PhantasiaBlockInfo[3][3][2];
            for (PhantasiaBlockInfo[][] sl : g)
                for (PhantasiaBlockInfo[] row : sl)
                    Arrays.fill(row, PhantasiaBlockInfo.EMPTY);

            g[1][2][0] = ctrl;

            g[0][2][1] = iface;

            g[0][1][0] = pp;
            g[1][1][0] = pp;
            g[2][1][0] = pp;

            g[0][0][0] = ma;
            g[1][0][0] = ma;
            g[2][0][0] = ma;

            return g;
        });

        PhantasiaScriptData script = script("ae2:crafting_subnet");
        script.addStep(step(0,
                "A Crafting Subnet is a separate ME Network dedicated entirely to autocrafting — keeping crafting channels isolated from your main storage network.",
                "all"));
        script.addStep(step(100,
                "The subnet has its own Controller. It runs independently, so crafting jobs never compete with storage access for channels.",
                "pos:1,2,0"));
        script.addStep(step(240,
                "An ME Interface connects the subnet to the main network. It passes crafting requests and finished items through using only one channel on the main side.",
                "pos:0,2,1"));
        script.addStep(step(400,
                "Pattern Providers in the subnet hold your Encoded Patterns. Each pushes ingredients to the Molecular Assembler directly below it.",
                "pos:0,1,0:1,1,0:2,1,0"));
        script.addStep(step(560,
                "Molecular Assemblers receive ingredients from their Pattern Provider and push finished items back through the subnet's Interface to main storage.",
                "pos:0,0,0:1,0,0:2,0,0"));
        script.addStep(workingStep(700,
                "Subnet online — autocrafting runs in full isolation. Scale by adding more controllers (up to 7×7×7) and more provider/assembler pairs.",
                "all"));

        return new AE2SceneDefinition(
                new ResourceLocation("ae2", "crafting_subnet"),
                "Crafting Subnet",
                "pattern_provider",
                shape, script);
    }
}
