package net.phoenixvine.phantasia.common.data.script;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import net.phoenixvine.phantasia.client.screens.PhantasiaSceneSelectionScreen;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.PhantasiaMultiblockRegistry;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class PhantasiaScriptLoader {

    private static final Path SCRIPT_DIR = FMLPaths.GAMEDIR.get()
            .resolve("phantasia/scripts");

    private static final Path LEGACY_SCRIPT_DIR = FMLPaths.GAMEDIR.get()
            .resolve("kubejs/data/phantasia/scripts");

    public static void discoverAndLoad() {
        migrateLegacyScripts();
        ensureDir(SCRIPT_DIR);
        discoverAllMultiblocks();
        loadAll();
    }

    public static void reload() {
        PhantasiaScripts.clearAllJson();
        loadAll();
    }

    public static void save(String machineId, PhantasiaScriptData data) {
        Path path = pathFor(machineId);
        try {
            ensureDir(path.getParent());
            Files.writeString(path, data.toJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logErr("Failed to save script for " + machineId + ": " + e.getMessage());
            return;
        }

        IPhantasiaMultiblockDefinition def = resolveDefinition(machineId);
        if (def != null) {
            PhantasiaScripts.register(def, PhantasiaScript.fromData(data));
            log("Saved and hot-reloaded script for " + machineId);
        } else {
            logErr("Saved script for " + machineId + " but could not resolve definition — " +
                    "it will load on next reload.");
        }
    }

    public static Path pathFor(String machineId) {
        ResourceLocation rl = parseId(machineId);
        return SCRIPT_DIR.resolve(rl.getNamespace()).resolve(rl.getPath() + ".json");
    }

    private static boolean hasLazyReloaded = false;

    public static void reloadIfNeeded() {
        if (!hasLazyReloaded) {
            hasLazyReloaded = true;
            log("Performing lazy reload to catch late-registered machine definitions...");
            reload();
        }
    }

    public static void resetLazyReload() {
        hasLazyReloaded = false;
    }

    private static void migrateLegacyScripts() {
        if (!Files.exists(LEGACY_SCRIPT_DIR)) return;
        int migrated = 0;
        try (var stream = Files.walk(LEGACY_SCRIPT_DIR)) {
            for (Path src : (Iterable<Path>) stream::iterator) {
                if (!src.toString().endsWith(".json")) continue;
                Path rel = LEGACY_SCRIPT_DIR.relativize(src);
                Path dest = SCRIPT_DIR.resolve(rel);
                if (Files.exists(dest)) continue;
                try {
                    ensureDir(dest.getParent());
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    migrated++;
                } catch (IOException e) {
                    logErr("Migration copy failed for " + src + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            logErr("Error walking legacy script directory during migration: " + e.getMessage());
        }
        if (migrated > 0) log("Migrated " + migrated + " script(s) from legacy kubejs location.");
    }

    private static void discoverAllMultiblocks() {
        var scenes = PhantasiaSceneSelectionScreen.PHANTASIA_SCENES;

        List<IPhantasiaMultiblockDefinition> all = PhantasiaMultiblockRegistry.getAllDefinitions();
        // Show vanilla (minecraft:*) entries last, unless there are no non-vanilla entries.
        boolean hasNonVanilla = all.stream().anyMatch(d -> !"minecraft".equals(d.getId().getNamespace()));
        if (hasNonVanilla) {
            all.sort((a, b) -> {
                boolean av = "minecraft".equals(a.getId().getNamespace());
                boolean bv = "minecraft".equals(b.getId().getNamespace());
                if (av == bv) return 0;
                return av ? 1 : -1;
            });
        }

        for (IPhantasiaMultiblockDefinition def : all) {
            if (!scenes.contains(def)) {
                scenes.add(def);
            }

            String machineId = def.getId().toString();
            Path path = pathFor(machineId);
            if (!Files.exists(path)) {
                writeDefaultScript(def, machineId, path);
            } else {
                // Overwrite only if the file looks like an empty stub (< 300 bytes) and the default has steps.
                // Avoids reading every script file on every world join — the full read is expensive at scale.
                PhantasiaScriptData defaultData = def.getDefaultScriptData();
                if (defaultData != null && !defaultData.getSteps().isEmpty()) {
                    try {
                        if (Files.size(path) < 300) {
                            PhantasiaScriptData onDisk = PhantasiaScriptData
                                    .fromJson(Files.newBufferedReader(path, java.nio.charset.StandardCharsets.UTF_8));
                            if (onDisk.getSteps().isEmpty()) {
                                writeDefaultScript(def, machineId, path);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        log("Discovered " + scenes.size() + " multiblock machines.");
    }

    private static void writeDefaultScript(IPhantasiaMultiblockDefinition def,
                                           String machineId, Path path) {
        try {
            ensureDir(path.getParent());
            PhantasiaScriptData data = def.getDefaultScriptData();
            if (data == null) data = PhantasiaScriptData.defaultFor(machineId);
            Files.writeString(path, data.toJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logErr("Could not write default script for " + machineId + ": " + e.getMessage());
        }
    }

    private static void loadAll() {
        if (!Files.exists(SCRIPT_DIR)) return;
        int loaded = 0, failed = 0;
        try (var stream = Files.walk(SCRIPT_DIR)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                if (!path.toString().endsWith(".json")) continue;
                if (loadOne(path)) loaded++;
                else failed++;
            }
        } catch (IOException e) {
            logErr("Error walking script directory: " + e.getMessage());
        }
        log("Loaded " + loaded + " scripts" + (failed > 0 ? ", " + failed + " failed." : "."));
    }

    private static boolean loadOne(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            PhantasiaScriptData data = PhantasiaScriptData.fromJson(reader);
            if (data.getMachine() == null || data.getMachine().isBlank()) {
                data = inferMachine(data, path);
            }
            if (data.getMachine() == null || data.getMachine().isBlank()) {
                logErr("Could not determine machine ID for " + path + " — skipping.");
                return false;
            }

            IPhantasiaMultiblockDefinition def = resolveDefinition(data.getMachine());
            if (def == null) {
                logErr("No multiblock definition found for \"" + data.getMachine() + "\" (from " + path.getFileName() +
                        ") — script will not apply. " +
                        "Check the machine ID, or call /phantasia reload after world load.");
                return false;
            }

            PhantasiaScripts.register(def, PhantasiaScript.fromData(data));
            return true;
        } catch (Exception e) {
            logErr("Failed to load " + path + ": " + e.getMessage());
            return false;
        }
    }

    private static PhantasiaScriptData inferMachine(PhantasiaScriptData original, Path path) {
        try {
            Path relative = SCRIPT_DIR.relativize(path);
            String namespace = relative.getName(0).toString();
            String rest = relative.subpath(1, relative.getNameCount()).toString()
                    .replace(File.separatorChar, '/').replaceAll("\\.json$", "");
            String machineId = namespace + ":" + rest;
            PhantasiaScriptData fixed = original.copy();
            String json = fixed.toJson()
                    .replaceFirst("\"machine\"\\s*:\\s*\"[^\"]*\"",
                            "\"machine\": \"" + machineId + "\"");
            return PhantasiaScriptData.fromJson(json);
        } catch (Exception e) {
            return original;
        }
    }

    private static IPhantasiaMultiblockDefinition resolveDefinition(String machineId) {
        return PhantasiaMultiblockRegistry.resolve(machineId).orElse(null);
    }

    private static ResourceLocation parseId(String machineId) {
        return machineId.contains(":") ? new ResourceLocation(machineId) : new ResourceLocation("gtceu", machineId);
    }

    private static void ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {}
    }

    private static void log(String msg) {
        net.phoenixvine.phantasia.Phantasia.LOGGER.info("[Phantasia] {}", msg);
    }

    private static void logErr(String msg) {
        net.phoenixvine.phantasia.Phantasia.LOGGER.error("[Phantasia] {}", msg);
    }
}
