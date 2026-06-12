package net.phoenixvine.phantasia.common.data.guides;

import net.minecraft.client.Minecraft;
import net.phoenixvine.phantasia.Phantasia;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Discovers and loads standalone text guides from the local configuration folder.
 * Populates the {@link PhantasiaGuideRegistry}.
 */
public final class PhantasiaGuideLoader {

    private PhantasiaGuideLoader() {}

    public static void load() {
        PhantasiaGuideRegistry.clear();
        Phantasia.LOGGER.info("[Phantasia] Discovering standalone guides...");

        try {
            // Locate config/phantasia/guides directory
            Path guidesDir = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("phantasia/guides");

            if (!Files.exists(guidesDir)) {
                Files.createDirectories(guidesDir);
                Phantasia.LOGGER.info("[Phantasia] Created guides directory at: {}", guidesDir);
                return;
            }

            // Walk the folder and search for all .json files recursively
            try (Stream<Path> walk = Files.walk(guidesDir)) {
                walk.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".json"))
                        .forEach(PhantasiaGuideLoader::loadGuideFile);
            }

            Phantasia.LOGGER.info("[Phantasia] Successfully loaded {} guide(s).", PhantasiaGuideRegistry.all().size());

        } catch (Exception e) {
            Phantasia.LOGGER.error("[Phantasia] Critical error walking guide directories: {}", e.getMessage(), e);
        }
    }

    /**
     * Clears the guide registry and re-reads all files from disk.
     */
    public static void reload() {
        load();
    }

    private static void loadGuideFile(Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            PhantasiaGuideData guide = PhantasiaGuideData.fromJson(reader);

            if (guide != null && guide.id != null && !guide.id.isBlank()) {
                PhantasiaGuideRegistry.register(guide);
                Phantasia.LOGGER.debug("[Phantasia] Loaded guide: {}", guide.id);
            } else {
                Phantasia.LOGGER.warn("[Phantasia] Skipped guide file {} (Missing or empty ID)", path.getFileName());
            }
        } catch (Exception e) {
            Phantasia.LOGGER.error("[Phantasia] Failed to parse guide JSON at {}: {}", path, e.getMessage());
        }
    }
}
