package net.phoenixvine.phantasia.common;

import net.phoenixvine.phantasia.Phantasia;

import java.util.*;

/**
 * Client-side registry for loaded {@link PhantasiaGuideData} instances.
 * Populated on login by scanning {@code phantasia/guides/<namespace>/<name>.json}
 * from the resource pack stack, analogous to {@link PhantasiaScenes}.
 */
public final class PhantasiaGuideRegistry {

    private PhantasiaGuideRegistry() {}

    private static final Map<String, PhantasiaGuideData> GUIDES = new LinkedHashMap<>();

    public static void register(PhantasiaGuideData guide) {
        if (guide.id != null && !guide.id.isBlank())
            GUIDES.put(guide.id, guide);
    }

    public static PhantasiaGuideData get(String id) {
        return id == null ? null : GUIDES.get(id);
    }

    public static Collection<PhantasiaGuideData> all() {
        return Collections.unmodifiableCollection(GUIDES.values());
    }

    public static void clear() {
        GUIDES.clear();
    }

    /**
     * Called by {@link net.phoenixvine.phantasia.client.screens.PhantasiaGuideEditorScreen}.
     */
    public static void save(PhantasiaGuideData guide) {
        if (guide.id == null || guide.id.isBlank()) {
            Phantasia.LOGGER.error("[Phantasia] Cannot save a guide with a missing or empty ID!");
            return;
        }

        register(guide);
        try {
            java.nio.file.Path dir = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("phantasia/guides");
            java.nio.file.Files.createDirectories(dir);

            // Replace colon with an underscore so it saves cleanly as a single file in the directory
            String sanitizedName = guide.id.replace(":", "_") + ".json";
            java.nio.file.Path file = dir.resolve(sanitizedName);

            java.nio.file.Files.writeString(file, guide.toJson());
            Phantasia.LOGGER.info("[Phantasia] Successfully saved guide file to: {}", file);
        } catch (Exception e) {
            Phantasia.LOGGER.error("[Phantasia] Failed to save guide to disk: ", e);
        }
    }
}
