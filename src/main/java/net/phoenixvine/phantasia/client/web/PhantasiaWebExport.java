package net.phoenixvine.phantasia.client.web;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.common.data.guides.PhantasiaGuideData;
import net.phoenixvine.phantasia.common.data.guides.PhantasiaGuideRegistry;
import net.phoenixvine.phantasia.common.data.pattern.PhantasiaScenePattern;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaScenes;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScript;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScripts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class PhantasiaWebExport {

    public static ExportResult export(Path docsDir) {
        int sceneCount = 0, scriptCount = 0, guideCount = 0;
        int blockCount = 0;
        StringBuilder errors = new StringBuilder();

        var gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();

        // ── Scenes ─────────────────────────────────────────────────────────────
        try {
            Path scenesDir   = docsDir.resolve("data/scenes");
            Path patternsDir = docsDir.resolve("data/patterns");
            Files.createDirectories(scenesDir);
            Files.createDirectories(patternsDir);

            JsonArray manifest = new JsonArray();

            for (PhantasiaSceneData scene : PhantasiaScenes.all()) {
                try {
                    String safeId = scene.id.replace(":", "/");

                    Files.createDirectories(scenesDir.resolve(safeId).getParent());
                    Files.writeString(scenesDir.resolve(safeId + ".json"), scene.toJson());

                    PhantasiaTrackedDummyWorld tempWorld = new PhantasiaTrackedDummyWorld();
                    PhantasiaScenePattern pattern = PhantasiaScenePattern.build(scene, tempWorld);

                    if (pattern != null) {
                        JsonArray blocks = serializeBlockMap(pattern);
                        blockCount += blocks.size();
                        Path patternFile = patternsDir.resolve(safeId + ".json");
                        Files.createDirectories(patternFile.getParent());
                        Files.writeString(patternFile, gson.toJson(blocks));
                    } else {
                        errors.append("No pattern for: ").append(scene.id).append("\n");
                    }

                    JsonObject entry = new JsonObject();
                    entry.addProperty("type", "scene");
                    entry.addProperty("id", scene.id);
                    entry.addProperty("name", scene.name);
                    entry.addProperty("iconItem", scene.iconItem);
                    entry.addProperty("hasPattern", pattern != null);
                    if (!scene.placements.isEmpty()) {
                        JsonArray placements = new JsonArray();
                        for (var p : scene.placements) placements.add(p.machine);
                        entry.add("machines", placements);
                    }
                    manifest.add(entry);
                    sceneCount++;
                } catch (Exception e) {
                    errors.append("Error exporting scene ").append(scene.id).append(": ").append(e.getMessage()).append("\n");
                }
            }

            Files.writeString(docsDir.resolve("data/manifest.json"), gson.toJson(manifest));

        } catch (IOException e) {
            errors.append("Fatal IO error (scenes): ").append(e.getMessage()).append("\n");
        }

        // ── Scripts ────────────────────────────────────────────────────────────
        try {
            Path scriptDataDir  = docsDir.resolve("data/script-data");
            Path scriptPatsDir  = docsDir.resolve("data/script-patterns");
            Files.createDirectories(scriptDataDir);
            Files.createDirectories(scriptPatsDir);

            JsonArray scriptsManifest = new JsonArray();

            for (Map.Entry<MultiblockMachineDefinition, PhantasiaScript> entry : PhantasiaScripts.allEntries()) {
                MultiblockMachineDefinition def = entry.getKey();
                PhantasiaScript script          = entry.getValue();
                ResourceLocation machineId      = def.getId();
                String safeId = machineId.getNamespace() + "/" + machineId.getPath();

                try {
                    // Script step data
                    Path dataFile = scriptDataDir.resolve(safeId + ".json");
                    Files.createDirectories(dataFile.getParent());
                    Files.writeString(dataFile, script.getSourceData().toJson());

                    // Machine pattern (first shape)
                    List<MultiblockShapeInfo> shapes = def.getMatchingShapes();
                    boolean hasPattern = false;
                    if (shapes != null && !shapes.isEmpty()) {
                        BlockInfo[][][] raw = shapes.get(0).getBlocks();
                        JsonArray patternBlocks = serializeRawPattern(raw);
                        blockCount += patternBlocks.size();
                        Path patFile = scriptPatsDir.resolve(safeId + ".json");
                        Files.createDirectories(patFile.getParent());
                        Files.writeString(patFile, gson.toJson(patternBlocks));
                        hasPattern = true;
                    }

                    JsonObject mEntry = new JsonObject();
                    mEntry.addProperty("type", "script");
                    mEntry.addProperty("id", machineId.toString());
                    mEntry.addProperty("name", machineId.getPath().replace('_', ' '));
                    mEntry.addProperty("iconItem", machineId.toString());
                    mEntry.addProperty("hasPattern", hasPattern);
                    scriptsManifest.add(mEntry);
                    scriptCount++;
                } catch (Exception e) {
                    errors.append("Error exporting script ").append(machineId).append(": ").append(e.getMessage()).append("\n");
                }
            }

            Files.writeString(docsDir.resolve("data/scripts-manifest.json"), gson.toJson(scriptsManifest));

        } catch (IOException e) {
            errors.append("Fatal IO error (scripts): ").append(e.getMessage()).append("\n");
        }

        // ── Guides ─────────────────────────────────────────────────────────────
        try {
            Path guidesDir = docsDir.resolve("data/guides");
            Files.createDirectories(guidesDir);

            JsonArray guidesManifest = new JsonArray();

            for (PhantasiaGuideData guide : PhantasiaGuideRegistry.all()) {
                try {
                    String safeId = guide.id.replace(":", "/");
                    Path guideFile = guidesDir.resolve(safeId + ".json");
                    Files.createDirectories(guideFile.getParent());
                    Files.writeString(guideFile, guide.toJson());

                    JsonObject gEntry = new JsonObject();
                    gEntry.addProperty("type", "guide");
                    gEntry.addProperty("id", guide.id);
                    gEntry.addProperty("title", guide.title);
                    if (guide.subtitle != null) gEntry.addProperty("subtitle", guide.subtitle);
                    gEntry.addProperty("iconItem", guide.iconItem);
                    if (guide.tag != null) gEntry.addProperty("tag", guide.tag);
                    guidesManifest.add(gEntry);
                    guideCount++;
                } catch (Exception e) {
                    errors.append("Error exporting guide ").append(guide.id).append(": ").append(e.getMessage()).append("\n");
                }
            }

            Files.writeString(docsDir.resolve("data/guides-manifest.json"), gson.toJson(guidesManifest));

        } catch (IOException e) {
            errors.append("Fatal IO error (guides): ").append(e.getMessage()).append("\n");
        }

        return new ExportResult(sceneCount, scriptCount, guideCount, blockCount, errors.toString());
    }

    // ── Scene block map (with placement tags) ──────────────────────────────────

    private static JsonArray serializeBlockMap(PhantasiaScenePattern pattern) {
        JsonArray blocks = new JsonArray();

        for (Map.Entry<BlockPos, BlockInfo> e : pattern.mergedBlockMap.entrySet()) {
            BlockPos pos  = e.getKey();
            BlockInfo info = e.getValue();
            BlockState state = info.getBlockState();
            if (state == null || state.isAir()) continue;

            JsonObject block = new JsonObject();
            block.addProperty("x", pos.getX());
            block.addProperty("y", pos.getY());
            block.addProperty("z", pos.getZ());

            var rl = ForgeRegistries.BLOCKS.getKey(state.getBlock());
            block.addProperty("block", rl != null ? rl.toString() : "minecraft:stone");

            JsonObject props = new JsonObject();
            for (Property<?> prop : state.getProperties()) {
                props.addProperty(prop.getName(), stateValueToString(state, prop));
            }
            if (props.size() > 0) block.add("props", props);

            boolean tagged = false;
            for (int i = 0; i < pattern.placements.size(); i++) {
                var pe = pattern.placements.get(i);
                if (pe.worldPositions.contains(pos)) {
                    block.addProperty("p", i);
                    tagged = true;
                    break;
                } else if (pe.baseplatePositions.contains(pos)) {
                    block.addProperty("p", i);
                    block.addProperty("bp", true);
                    tagged = true;
                    break;
                }
            }
            if (!tagged) block.addProperty("bp", true);

            blocks.add(block);
        }

        return blocks;
    }

    // ── Raw BlockInfo[][][] → flat block list (for scripts) ───────────────────

    private static JsonArray serializeRawPattern(BlockInfo[][][] raw) {
        JsonArray blocks = new JsonArray();
        for (int x = 0; x < raw.length; x++) {
            if (raw[x] == null) continue;
            for (int y = 0; y < raw[x].length; y++) {
                if (raw[x][y] == null) continue;
                for (int z = 0; z < raw[x][y].length; z++) {
                    BlockInfo info = raw[x][y][z];
                    if (info == null) continue;
                    BlockState state = info.getBlockState();
                    if (state == null || state.isAir()) continue;

                    JsonObject block = new JsonObject();
                    block.addProperty("x", x);
                    block.addProperty("y", y);
                    block.addProperty("z", z);

                    var rl = ForgeRegistries.BLOCKS.getKey(state.getBlock());
                    block.addProperty("block", rl != null ? rl.toString() : "minecraft:stone");

                    JsonObject props = new JsonObject();
                    for (Property<?> prop : state.getProperties()) {
                        props.addProperty(prop.getName(), stateValueToString(state, prop));
                    }
                    if (props.size() > 0) block.add("props", props);

                    blocks.add(block);
                }
            }
        }
        return blocks;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String stateValueToString(BlockState state, Property<T> prop) {
        return prop.getName(state.getValue(prop));
    }

    public record ExportResult(int scenes, int scripts, int guides, int blocks, String errors) {
        public boolean hasErrors() { return !errors.isBlank(); }
        public boolean success()   { return scenes > 0 || scripts > 0 || guides > 0; }
    }
}
