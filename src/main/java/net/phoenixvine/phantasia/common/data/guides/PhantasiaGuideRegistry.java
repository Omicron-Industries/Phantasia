package net.phoenixvine.phantasia.common.data.guides;

import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.client.screens.editors.PhantasiaGuideEditorScreen;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaScenes;

import java.util.*;

/**
 * Client-side registry for loaded {@link PhantasiaGuideData} instances.
 * Populated on login by scanning {@code phantasia/guides/<namespace>/<name>.json}
 * from the resource pack stack, analogous to {@link PhantasiaScenes}.
 */
public final class PhantasiaGuideRegistry {

    private PhantasiaGuideRegistry() {}

    /** Built-in guides shipped with Phantasia itself. Never cleared on reload. */
    private static final Map<String, PhantasiaGuideData> BUILTINS = new LinkedHashMap<>();
    private static final Map<String, PhantasiaGuideData> GUIDES = new LinkedHashMap<>();

    public static void registerBuiltin(PhantasiaGuideData guide) {
        if (guide.id != null && !guide.id.isBlank())
            BUILTINS.put(guide.id, guide);
    }

    public static void register(PhantasiaGuideData guide) {
        if (guide.id != null && !guide.id.isBlank())
            GUIDES.put(guide.id, guide);
    }

    public static PhantasiaGuideData get(String id) {
        if (id == null) return null;
        PhantasiaGuideData d = GUIDES.get(id);
        return d != null ? d : BUILTINS.get(id);
    }

    public static Collection<PhantasiaGuideData> all() {
        // Builtins appear first; user guides can shadow them by ID.
        Map<String, PhantasiaGuideData> combined = new LinkedHashMap<>(BUILTINS);
        combined.putAll(GUIDES);
        return Collections.unmodifiableCollection(combined.values());
    }

    public static void clear() {
        GUIDES.clear();
        // BUILTINS are intentionally not cleared on reload.
    }

    /**
     * Called by {@link PhantasiaGuideEditorScreen}.
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
