package net.phoenixvine.phantasia.common;

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
     * Persists a guide back to the user's config directory.
     * Called by {@link net.phoenixvine.phantasia.client.screens.PhantasiaGuideEditorScreen}.
     */
    public static void save(PhantasiaGuideData guide) {
        register(guide);
        try {
            java.nio.file.Path dir = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config/phantasia/guides");
            java.nio.file.Files.createDirectories(dir);
            String fileName = guide.id.replace(":", "/") + ".json";
            java.nio.file.Path file = dir.resolve(fileName);
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, guide.toJson());
        } catch (Exception e) {
            net.phoenixvine.phantasia.Phantasia.LOGGER.error(
                    "[Phantasia] Failed to save guide {}: {}", guide.id, e.getMessage());
        }
    }
}
