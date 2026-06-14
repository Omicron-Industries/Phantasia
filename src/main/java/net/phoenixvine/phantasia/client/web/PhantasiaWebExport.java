package net.phoenixvine.phantasia.client.web;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.common.data.pattern.PhantasiaScenePattern;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaScenes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class PhantasiaWebExport {

    public static ExportResult export(Path docsDir) {
        int sceneCount = 0;
        int blockCount = 0;
        StringBuilder errors = new StringBuilder();

        try {
            Path scenesDir = docsDir.resolve("data/scenes");
            Path patternsDir = docsDir.resolve("data/patterns");
            Files.createDirectories(scenesDir);
            Files.createDirectories(patternsDir);

            var gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
            JsonArray manifest = new JsonArray();

            for (PhantasiaSceneData scene : PhantasiaScenes.all()) {
                try {
                    String safeId = scene.id.replace(":", "/");

                    // Export scene JSON
                    String sceneJson = scene.toJson();
                    Path sceneFile = scenesDir.resolve(safeId + ".json");
                    Files.createDirectories(sceneFile.getParent());
                    Files.writeString(sceneFile, sceneJson);

                    // Build pattern and export block positions
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

                    // Add to manifest
                    JsonObject entry = new JsonObject();
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
                    errors.append("Error exporting ").append(scene.id).append(": ").append(e.getMessage()).append("\n");
                }
            }

            Files.writeString(docsDir.resolve("data/manifest.json"), gson.toJson(manifest));

        } catch (IOException e) {
            errors.append("Fatal IO error: ").append(e.getMessage());
        }

        return new ExportResult(sceneCount, blockCount, errors.toString());
    }

    private static JsonArray serializeBlockMap(PhantasiaScenePattern pattern) {
        JsonArray blocks = new JsonArray();

        for (Map.Entry<BlockPos, BlockInfo> e : pattern.mergedBlockMap.entrySet()) {
            BlockPos pos = e.getKey();
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

            // Tag which placement this block belongs to
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

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String stateValueToString(BlockState state, Property<T> prop) {
        return prop.getName(state.getValue(prop));
    }

    public record ExportResult(int scenes, int blocks, String errors) {
        public boolean hasErrors() { return !errors.isBlank(); }
        public boolean success() { return scenes > 0; }
    }
}
