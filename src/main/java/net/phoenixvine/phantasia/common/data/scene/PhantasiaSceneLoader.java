package net.phoenixvine.phantasia.common.data.scene;

import net.minecraftforge.fml.loading.FMLPaths;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PhantasiaSceneLoader — lifecycle management for manual scene JSONs.
 *
 * Directory: {@code <gamedir>/phantasia/scenes/<namespace>/<name>.json}
 *
 * Unlike the script loader, scenes are not auto-generated — the directory
 * starts empty and the user creates scenes through the editor. There is no
 * discovery pass; we simply load every JSON we find.
 */
public class PhantasiaSceneLoader {

    private static final Path SCENE_DIR = FMLPaths.GAMEDIR.get()
            .resolve("phantasia/scenes");

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Called during FMLClientSetupEvent alongside {@link PhantasiaScriptLoader#discoverAndLoad()}.
     * Ensures the scenes directory exists and loads any existing scene JSONs.
     */
    public static void load() {
        ensureDir(SCENE_DIR);
        loadAll();
    }

    /** Clears the scene registry and re-reads all files from disk. */
    public static void reload() {
        PhantasiaScenes.clearAll();
        loadAll();
    }

    /**
     * Saves a scene to disk and registers it immediately.
     * The file path is derived from the scene's ID:
     * {@code "phoenixvine:ore_line"} → {@code .../scenes/phoenixvine/ore_line.json}
     *
     * @param scene the scene data to persist (must have a non-blank id)
     */
    public static void save(PhantasiaSceneData scene) {
        if (scene.id == null || scene.id.isBlank()) {
            logErr("Cannot save scene with blank id.");
            return;
        }
        Path path = pathFor(scene.id);
        try {
            ensureDir(path.getParent());
            Files.writeString(path, scene.toJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logErr("Failed to save scene '" + scene.id + "': " + e.getMessage());
            return;
        }
        PhantasiaScenes.register(scene);
        log("Saved and registered scene '" + scene.id + "'.");
    }

    /**
     * Deletes a scene's JSON file and removes it from the registry.
     * No-op if the file doesn't exist.
     */
    public static void delete(String sceneId) {
        PhantasiaScenes.remove(sceneId);
        Path path = pathFor(sceneId);
        try {
            Files.deleteIfExists(path);
            log("Deleted scene '" + sceneId + "'.");
        } catch (IOException e) {
            logErr("Failed to delete scene file for '" + sceneId + "': " + e.getMessage());
        }
    }

    /** Returns the canonical disk path for a given scene ID. */
    public static Path pathFor(String sceneId) {
        String[] parts = sceneId.contains(":") ? sceneId.split(":", 2) : new String[] { "phantasia", sceneId };
        return SCENE_DIR.resolve(parts[0]).resolve(parts[1] + ".json");
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    private static void loadAll() {
        if (!Files.exists(SCENE_DIR)) return;
        int loaded = 0, failed = 0;
        try (var stream = Files.walk(SCENE_DIR)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                if (!path.toString().endsWith(".json")) continue;
                if (loadOne(path)) loaded++;
                else failed++;
            }
        } catch (IOException e) {
            logErr("Error walking scene directory: " + e.getMessage());
        }
        log("Loaded " + loaded + " scene(s)" + (failed > 0 ? ", " + failed + " failed." : "."));
    }

    private static boolean loadOne(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            PhantasiaSceneData scene = PhantasiaSceneData.fromJson(reader);

            // Infer ID from file path if missing
            if (scene.id == null || scene.id.isBlank()) {
                scene.id = inferId(path);
            }
            if (scene.id == null || scene.id.isBlank()) {
                logErr("Could not determine ID for scene at " + path + " — skipping.");
                return false;
            }

            PhantasiaScenes.register(scene);
            return true;
        } catch (Exception e) {
            logErr("Failed to load scene at " + path + ": " + e.getMessage());
            return false;
        }
    }

    private static String inferId(Path path) {
        try {
            Path rel = SCENE_DIR.relativize(path);
            String ns = rel.getName(0).toString();
            String name = rel.subpath(1, rel.getNameCount()).toString()
                    .replace(java.io.File.separatorChar, '/')
                    .replaceAll("\\.json$", "");
            return ns + ":" + name;
        } catch (Exception e) {
            return null;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {}
    }

    private static void log(String msg) {
        net.phoenixvine.phantasia.Phantasia.LOGGER.info("[Phantasia/Scenes] {}", msg);
    }

    private static void logErr(String msg) {
        net.phoenixvine.phantasia.Phantasia.LOGGER.error("[Phantasia/Scenes] {}", msg);
    }
}
