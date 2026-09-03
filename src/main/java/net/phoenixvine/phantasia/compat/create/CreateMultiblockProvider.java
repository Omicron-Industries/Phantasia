package net.phoenixvine.phantasia.compat.create;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockProvider;
import net.phoenixvine.phantasia.compat.custom.PhantasiaCustomLayout;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CreateMultiblockProvider implements IPhantasiaMultiblockProvider {

    private final List<IPhantasiaMultiblockDefinition> definitions = new ArrayList<>();

    public CreateMultiblockProvider() {
        definitions.add(makeMixer());
        definitions.add(makePress());
        definitions.add(makeMillstone());
        definitions.add(makeCrushingWheels());
        definitions.add(makeFanWashing());
        definitions.add(makeFanSmelting());
        definitions.add(makeBlazeBurner());
        definitions.add(makeSteamEngine());
    }

    @Override
    public String getModId() {
        return "create";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean isControllerBlock(net.minecraft.world.level.block.state.BlockState s) {
        return false;
    }

    @Override
    public boolean isPartBlock(net.minecraft.world.level.block.state.BlockState s) {
        return false;
    }

    @Override
    public Optional<PhantasiaBlockInfo> resolveBlock(String id) {
        return Optional.empty();
    }

    @Override
    public Optional<IPhantasiaMultiblockDefinition> resolveFromItem(ItemStack stack) {
        return Optional.empty();
    }

    @Override
    public List<IPhantasiaMultiblockDefinition> getAllDefinitions() {
        return definitions;
    }

    @Override
    public Optional<IPhantasiaMultiblockDefinition> resolve(String machineId) {
        ResourceLocation rl = machineId.contains(":") ? new ResourceLocation(machineId) :
                new ResourceLocation("create", machineId);
        return definitions.stream().filter(d -> d.getId().equals(rl)).findFirst();
    }

    private static final PhantasiaBlockInfo AIR = PhantasiaBlockInfo.EMPTY;

    private static PhantasiaBlockInfo c(String spec) {
        BlockState state = PhantasiaCustomLayout.parseBlockState("create:" + spec);
        if (state == null || state.isAir()) return AIR;
        return PhantasiaBlockInfo.fromBlockState(state);
    }

    private static PhantasiaBlockInfo mc(String spec) {
        BlockState state = PhantasiaCustomLayout.parseBlockState("minecraft:" + spec);
        if (state == null || state.isAir()) return AIR;
        return PhantasiaBlockInfo.fromBlockState(state);
    }

    private static PhantasiaBlockInfo[][][] layers(PhantasiaBlockInfo[][]... yLayers) {
        int sizeY = yLayers.length;
        int sizeX = yLayers[0].length;
        int sizeZ = yLayers[0][0].length;
        PhantasiaBlockInfo[][][] out = new PhantasiaBlockInfo[sizeX][sizeY][sizeZ];
        for (int y = 0; y < sizeY; y++)
            for (int x = 0; x < sizeX; x++)
                for (int z = 0; z < sizeZ; z++)
                    out[x][y][z] = yLayers[y][x][z] != null ? yLayers[y][x][z] : AIR;
        return out;
    }

    private static PhantasiaScriptData script(String machineId) {
        return new PhantasiaScriptData();
    }

    private static PhantasiaScriptData.StepData step(int tick, String caption) {
        return new PhantasiaScriptData.StepData(tick, caption);
    }

    private static PhantasiaScriptData.StepData step(int tick, String caption, String show) {
        PhantasiaScriptData.StepData s = step(tick, caption);
        s.show = show;
        return s;
    }

    private static PhantasiaScriptData.HighlightData hl(int x, int y, int z) {
        return new PhantasiaScriptData.HighlightData(x, y, z);
    }

    private static PhantasiaScriptData.HighlightData hl(int x, int y, int z, String color) {
        return new PhantasiaScriptData.HighlightData(x, y, z, color);
    }

    private static CreateSceneDefinition makeMixer() {
        PhantasiaScriptData d = script("mechanical_mixer");
        PhantasiaScriptData.StepData s0 = step(0,
                "The §6Mechanical Mixer§r processes §eMixing§r recipes. Place a §6Basin§r at the base.");
        s0.highlights.add(hl(1, 0, 0));
        PhantasiaScriptData.StepData s1 = step(60,
                "Place the §6Mechanical Mixer§r directly above the Basin — it auto-faces down.");
        s1.highlights.add(hl(1, 1, 0));
        PhantasiaScriptData.StepData s2 = step(120,
                "Supply §aRotational Force§r via a §6Shaft§r connected above the Mixer's casing.");
        s2.highlights.add(hl(1, 2, 0, "#44AAFF"));
        s2.highlights.add(hl(1, 3, 0, "#44AAFF"));
        PhantasiaScriptData.StepData s3 = step(180,
                "The Mixer runs as long as it has sufficient speed. Heat the Basin with a §6Blaze Burner§r below for §cHeated§r and §cSuperheated§r recipes.");
        s3.working = true;
        d.getSteps().addAll(List.of(s0, s1, s2, s3));
        return new CreateSceneDefinition("mechanical_mixer", "Mechanical Mixer", "create:mechanical_mixer",
                () -> {
                    PhantasiaBlockInfo BASIN = c("basin");
                    PhantasiaBlockInfo MIXER = c("mechanical_mixer[facing=down]");
                    PhantasiaBlockInfo SHAFT_Y = c("shaft[axis=y]");
                    PhantasiaBlockInfo CASING = c("andesite_casing");
                    return layers(
                            new PhantasiaBlockInfo[][] { { AIR }, { BASIN }, { AIR } },
                            new PhantasiaBlockInfo[][] { { AIR }, { MIXER }, { AIR } },
                            new PhantasiaBlockInfo[][] { { AIR }, { CASING }, { AIR } },
                            new PhantasiaBlockInfo[][] { { AIR }, { SHAFT_Y }, { AIR } });
                }, d);
    }

    private static CreateSceneDefinition makePress() {
        PhantasiaScriptData d = script("mechanical_press");
        PhantasiaScriptData.StepData s0 = step(0,
                "The §6Mechanical Press§r handles §ePressing§r and §eCompacting§r recipes.");
        s0.highlights.add(hl(1, 0, 0));
        PhantasiaScriptData.StepData s1 = step(60,
                "Place a §6Depot§r or §6Basin§r beneath the Press to receive items.");
        s1.highlights.add(hl(1, 0, 0, "#AAFFAA"));
        PhantasiaScriptData.StepData s2 = step(120,
                "Supply §aRotational Force§r from above. The Press strokes down automatically when items are present.");
        s2.highlights.add(hl(1, 2, 0, "#44AAFF"));
        s2.highlights.add(hl(1, 3, 0, "#44AAFF"));
        s2.working = true;
        d.getSteps().addAll(List.of(s0, s1, s2));
        return new CreateSceneDefinition("mechanical_press", "Mechanical Press", "create:mechanical_press",
                () -> {
                    PhantasiaBlockInfo DEPOT = c("depot");
                    PhantasiaBlockInfo PRESS = c("mechanical_press[facing=down]");
                    PhantasiaBlockInfo SHAFT_Y = c("shaft[axis=y]");
                    PhantasiaBlockInfo CASING = c("andesite_casing");
                    return layers(
                            new PhantasiaBlockInfo[][] { { AIR }, { DEPOT }, { AIR } },
                            new PhantasiaBlockInfo[][] { { AIR }, { PRESS }, { AIR } },
                            new PhantasiaBlockInfo[][] { { AIR }, { CASING }, { AIR } },
                            new PhantasiaBlockInfo[][] { { AIR }, { SHAFT_Y }, { AIR } });
                }, d);
    }

    private static CreateSceneDefinition makeMillstone() {
        PhantasiaScriptData d = script("millstone");
        PhantasiaScriptData.StepData s0 = step(0,
                "The §6Millstone§r handles §eMilling§r and §eCrushing§r recipes. Items dropped on top are processed.");
        s0.highlights.add(hl(1, 0, 0));
        PhantasiaScriptData.StepData s1 = step(60,
                "Power enters through the §6Shaft§r at the top of the Millstone — connect your rotational network.");
        s1.highlights.add(hl(1, 1, 0, "#44AAFF"));
        s1.highlights.add(hl(0, 1, 0, "#44AAFF"));
        s1.highlights.add(hl(2, 1, 0, "#44AAFF"));
        PhantasiaScriptData.StepData s2 = step(120,
                "Output falls out of the sides into §6Funnels§r or a §6Belt§r below. Processing speed scales with RPM.");
        s2.working = true;
        d.getSteps().addAll(List.of(s0, s1, s2));
        return new CreateSceneDefinition("millstone", "Millstone", "create:millstone",
                () -> {
                    PhantasiaBlockInfo MILLSTONE = c("millstone");
                    PhantasiaBlockInfo SHAFT_Y = c("shaft[axis=y]");
                    PhantasiaBlockInfo COGWHEEL = c("large_cogwheel[axis=x]");
                    PhantasiaBlockInfo SHAFT_X = c("shaft[axis=x]");
                    return layers(
                            new PhantasiaBlockInfo[][] { { AIR }, { MILLSTONE }, { AIR } },
                            new PhantasiaBlockInfo[][] { { SHAFT_X }, { SHAFT_Y }, { SHAFT_X } },
                            new PhantasiaBlockInfo[][] { { AIR }, { COGWHEEL }, { AIR } });
                }, d);
    }

    private static CreateSceneDefinition makeCrushingWheels() {
        PhantasiaScriptData d = script("crushing_wheels");
        PhantasiaScriptData.StepData s0 = step(0,
                "Two §6Crushing Wheels§r placed side-by-side handle §eCrushing§r recipes — including ore doubling.");
        s0.highlights.add(hl(1, 1, 0));
        s0.highlights.add(hl(2, 1, 0));
        PhantasiaScriptData.StepData s1 = step(60,
                "The wheels must spin in §copposite directions§r — one powered, one meshing from it. Items dropped from above get crushed.");
        s1.highlights.add(hl(0, 1, 0, "#44AAFF"));
        PhantasiaScriptData.StepData s2 = step(120,
                "Crushed output falls into §6Depots§r, a §6Belt§r, or §6Funnels§r below. Minimum §a5 RPM§r required.");
        s2.highlights.add(hl(1, 0, 0, "#AAFFAA"));
        s2.highlights.add(hl(2, 0, 0, "#AAFFAA"));
        s2.working = true;
        d.getSteps().addAll(List.of(s0, s1, s2));
        return new CreateSceneDefinition("crushing_wheels", "Crushing Wheels", "create:crushing_wheel",
                () -> {
                    PhantasiaBlockInfo WHEEL = c("crushing_wheel[axis=y]");
                    PhantasiaBlockInfo SHAFT_X = c("shaft[axis=x]");
                    PhantasiaBlockInfo DEPOT = c("depot");
                    return layers(
                            new PhantasiaBlockInfo[][] { { AIR }, { DEPOT }, { DEPOT }, { AIR } },
                            new PhantasiaBlockInfo[][] { { SHAFT_X }, { WHEEL }, { WHEEL }, { AIR } });
                }, d);
    }

    private static CreateSceneDefinition makeFanWashing() {
        PhantasiaScriptData d = script("fan_washing");
        PhantasiaScriptData.StepData s0 = step(0,
                "§6Bulk Washing§r uses an §6Encased Fan§r blowing through a §ewater source block§r. Items in the airstream are washed.");
        s0.highlights.add(hl(0, 0, 0));
        PhantasiaScriptData.StepData s1 = step(60,
                "Place a §fwater source block§r directly in front of the fan's output face.");
        s1.highlights.add(hl(1, 0, 0, "#4488FF"));
        PhantasiaScriptData.StepData s2 = step(120,
                "Items thrown into the airstream — or moved through it on a §6Belt§r — are washed. A §6Depot§r or §6Belt§r collects the output.");
        s2.highlights.add(hl(2, 0, 0, "#AAFFAA"));
        PhantasiaScriptData.StepData s3 = step(180,
                "The Fan needs §aRotational Force§r from any source. Processing speed is fixed regardless of RPM.");
        s3.highlights.add(hl(0, 1, 0, "#44AAFF"));
        s3.working = true;
        d.getSteps().addAll(List.of(s0, s1, s2, s3));
        return new CreateSceneDefinition("fan_washing", "Fan: Bulk Washing", "create:encased_fan",
                () -> {
                    PhantasiaBlockInfo FAN = c("encased_fan[facing=east]");
                    PhantasiaBlockInfo WATER = mc("water[level=0]");
                    PhantasiaBlockInfo DEPOT = c("depot");
                    PhantasiaBlockInfo SHAFT_Y = c("shaft[axis=y]");
                    return layers(
                            new PhantasiaBlockInfo[][] { { FAN }, { WATER }, { DEPOT } },
                            new PhantasiaBlockInfo[][] { { SHAFT_Y }, { AIR }, { AIR } });
                }, d);
    }

    private static CreateSceneDefinition makeFanSmelting() {
        PhantasiaScriptData d = script("fan_smelting");
        PhantasiaScriptData.StepData s0 = step(0,
                "§6Bulk Smelting§r works like Washing — replace the water with a §eFire§r source in the airstream.");
        s0.highlights.add(hl(0, 1, 0));
        PhantasiaScriptData.StepData s1 = step(60,
                "Place §fFire§r (on any flammable-immune block like Stone) in front of the Fan. §6Lava§r or ��6Soul Fire§r also work — Soul Fire gives §6Haunting§r instead.");
        s1.highlights.add(hl(1, 1, 0, "#FF8800"));
        s1.highlights.add(hl(1, 0, 0));
        PhantasiaScriptData.StepData s2 = step(120,
                "Items passing through the fire stream are §eSmelted§r. Output lands in the §6Depot§r.");
        s2.highlights.add(hl(2, 1, 0, "#AAFFAA"));
        s2.working = true;
        d.getSteps().addAll(List.of(s0, s1, s2));
        return new CreateSceneDefinition("fan_smelting", "Fan: Bulk Smelting", "create:encased_fan",
                () -> {
                    PhantasiaBlockInfo FAN = c("encased_fan[facing=east]");
                    PhantasiaBlockInfo FIRE = mc("fire");
                    PhantasiaBlockInfo DEPOT = c("depot");
                    PhantasiaBlockInfo SHAFT_Y = c("shaft[axis=y]");
                    PhantasiaBlockInfo STONE = mc("stone");
                    return layers(
                            new PhantasiaBlockInfo[][] { { AIR }, { STONE }, { AIR } },
                            new PhantasiaBlockInfo[][] { { FAN }, { FIRE }, { DEPOT } },
                            new PhantasiaBlockInfo[][] { { SHAFT_Y }, { AIR }, { AIR } });
                }, d);
    }

    private static CreateSceneDefinition makeBlazeBurner() {
        PhantasiaScriptData d = script("blaze_burner");
        PhantasiaScriptData.StepData s0 = step(0,
                "The §6Blaze Burner§r provides heat to a §6Basin§r above it, enabling §cHeated§r and §cSuperheated§r recipes.");
        s0.highlights.add(hl(1, 0, 0, "#FF4400"));
        PhantasiaScriptData.StepData s1 = step(60,
                "Place the §6Basin§r directly above the Blaze Burner. The heat transfers automatically.");
        s1.highlights.add(hl(1, 1, 0));
        PhantasiaScriptData.StepData s2 = step(120,
                "Add a §6Mechanical Mixer§r above the Basin for heated mixing recipes like §eBrass§r or §eChocolate§r.");
        s2.highlights.add(hl(1, 2, 0));
        PhantasiaScriptData.StepData s3 = step(180,
                "Feed the Blaze Burner with §6Blaze Cakes§r for regular heat or §6Blaze Burner Fuel§r for Superheated. The burner must be lit before recipes start.");
        s3.highlights.add(hl(1, 0, 0, "#FF8800"));
        s3.working = true;
        d.getSteps().addAll(List.of(s0, s1, s2, s3));
        return new CreateSceneDefinition("blaze_burner", "Blaze Burner Setup", "create:blaze_burner",
                () -> {
                    PhantasiaBlockInfo BURNER = c("blaze_burner");
                    PhantasiaBlockInfo BASIN = c("basin");
                    PhantasiaBlockInfo MIXER = c("mechanical_mixer[facing=down]");
                    PhantasiaBlockInfo SHAFT_Y = c("shaft[axis=y]");
                    return layers(
                            new PhantasiaBlockInfo[][] { { AIR }, { BURNER }, { AIR } },
                            new PhantasiaBlockInfo[][] { { AIR }, { BASIN }, { AIR } },
                            new PhantasiaBlockInfo[][] { { AIR }, { MIXER }, { AIR } },
                            new PhantasiaBlockInfo[][] { { AIR }, { SHAFT_Y }, { AIR } });
                }, d);
    }

    private static CreateSceneDefinition makeSteamEngine() {
        PhantasiaScriptData d = script("steam_engine");
        PhantasiaScriptData.StepData s0 = step(0,
                "The §6Steam Engine§r converts steam into §aRotational Force§r — one of Create's primary power sources.");
        s0.highlights.add(hl(3, 0, 0));
        PhantasiaScriptData.StepData s1 = step(60,
                "The §6Boiler§r generates steam from §eWater§r (piped in) and §eHeat§r from below (Blaze Burner, Lava, Fire).");
        s1.highlights.add(hl(2, 0, 0, "#FF8800"));
        s1.highlights.add(hl(2, 1, 0, "#FF8800"));
        PhantasiaScriptData.StepData s2 = step(120,
                "A §6Fluid Tank§r stores water. Pipe it into the Boiler with §6Fluid Pipes§r.");
        s2.highlights.add(hl(0, 0, 0, "#4488FF"));
        s2.highlights.add(hl(0, 1, 0, "#4488FF"));
        s2.highlights.add(hl(1, 0, 0, "#4488FF"));
        s2.highlights.add(hl(1, 1, 0, "#4488FF"));
        PhantasiaScriptData.StepData s3 = step(180,
                "Attach a §6Flywheel§r to the engine's output for a passive §aSU bonus§r. Connect your rotational network via §6Shafts§r.");
        s3.highlights.add(hl(4, 0, 0, "#AAFFAA"));
        s3.highlights.add(hl(3, 1, 0, "#44AAFF"));
        s3.highlights.add(hl(3, 2, 0, "#44AAFF"));
        s3.working = true;
        d.getSteps().addAll(List.of(s0, s1, s2, s3));
        return new CreateSceneDefinition("steam_engine", "Steam Engine", "create:steam_engine",
                () -> {
                    PhantasiaBlockInfo ENGINE = c("steam_engine");
                    PhantasiaBlockInfo BOILER = c("boiler");
                    PhantasiaBlockInfo TANK = c("fluid_tank");
                    PhantasiaBlockInfo SHAFT_Y = c("shaft[axis=y]");
                    PhantasiaBlockInfo SHAFT_X = c("shaft[axis=x]");
                    PhantasiaBlockInfo FLYWHEEL = c("flywheel");
                    PhantasiaBlockInfo PIPE = c("fluid_pipe");
                    return layers(
                            new PhantasiaBlockInfo[][] { { TANK }, { PIPE }, { BOILER }, { ENGINE }, { FLYWHEEL } },
                            new PhantasiaBlockInfo[][] { { TANK }, { PIPE }, { BOILER }, { SHAFT_X }, { AIR } },
                            new PhantasiaBlockInfo[][] { { AIR }, { AIR }, { AIR }, { SHAFT_X }, { AIR } });
                }, d);
    }
}
