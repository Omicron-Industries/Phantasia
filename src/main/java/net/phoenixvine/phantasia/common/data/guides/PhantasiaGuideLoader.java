package net.phoenixvine.phantasia.common.data.guides;

import net.minecraft.client.Minecraft;
import net.phoenixvine.phantasia.Phantasia;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public final class PhantasiaGuideLoader {

    private PhantasiaGuideLoader() {}

    public static void load() {
        PhantasiaGuideRegistry.clear();
        Phantasia.LOGGER.info("[Phantasia] Discovering standalone guides...");

        try {

            Path guidesDir = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("phantasia/guides");

            if (!Files.exists(guidesDir)) {
                Files.createDirectories(guidesDir);
                Phantasia.LOGGER.info("[Phantasia] Created guides directory at: {}", guidesDir);
                return;
            }

            try (Stream<Path> walk = Files.walk(guidesDir)) {
                walk.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".json"))
                        .forEach(PhantasiaGuideLoader::loadGuideFile);
            }

            PhantasiaBuiltinGuides.register();
            Phantasia.LOGGER.info("[Phantasia] Successfully loaded {} guide(s).", PhantasiaGuideRegistry.all().size());

        } catch (Exception e) {
            Phantasia.LOGGER.error("[Phantasia] Critical error walking guide directories: {}", e.getMessage(), e);
        }
    }

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
