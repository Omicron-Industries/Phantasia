package net.phoenixvine.phantasia.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;
import net.phoenixvine.phantasia.client.compat.EmbeddiumCompat;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Marks animated sprites active each frame when Embeddium/Rubidium is present.
 *
 * Embeddium culls animation updates for sprites not visible in the player camera
 * frustum. Since the dummy world uses an isolated chunk source, none of its
 * sprites are ever auto-marked. This class marks them manually each frame.
 *
 * Strategy: scan the blocks and particles atlases ONCE to find all animated
 * sprites, cache them, then mark only those cached sprites each frame.
 * This reduces the per-frame cost from O(all atlas sprites) to O(animated sprites),
 * which is typically a few hundred entries vs the full atlas of thousands.
 *
 * Call invalidateCache() after a bake swap or atlas reload to force a rescan.
 */
@OnlyIn(Dist.CLIENT)
public final class PhantasiaSpriteMarker {

    public static final boolean EMBEDDIUM_PRESENT = ModList.get().isLoaded("embeddium") ||
            ModList.get().isLoaded("rubidium");

    // Cached set of animated sprites found during the last atlas scan.
    // Null means the cache needs building; empty set means no animated sprites found.
    private static Set<TextureAtlasSprite> cachedAnimatedSprites = null;

    // TextureAtlas sprites map field — resolved once
    private static Field spritesField = null;
    private static boolean atlasFieldResolved = false;

    // TextureSheetParticle.sprite field — resolved once
    private static Field particleSpriteField = null;
    private static boolean particleSpriteResolved = false;

    private PhantasiaSpriteMarker() {}

    /**
     * Invalidate the animated-sprite cache. Call this after a bake swap or
     * whenever the atlas may have changed (resource reload, etc.).
     * The next markAll() call will rebuild the cache with a full atlas scan.
     */
    public static void invalidateCache() {
        cachedAnimatedSprites = null;
    }

    /**
     * Mark all animated sprites in the blocks and particles atlases active.
     * On the first call (or after invalidateCache()), scans the atlas and
     * builds a cache. Subsequent calls just iterate the cache — O(animated)
     * instead of O(all sprites).
     *
     * The blockSprites parameter is kept for API compatibility but is unused;
     * the full-atlas approach covers BER overlays and emissives that never
     * appear in baked quads.
     */
    public static void markAll(Set<TextureAtlasSprite> blockSprites) {
        if (!EMBEDDIUM_PRESENT) return;

        if (cachedAnimatedSprites == null) {
            buildCache();
        }

        // Fast path: just mark each cached animated sprite active.
        for (TextureAtlasSprite sprite : cachedAnimatedSprites) {
            try {
                EmbeddiumCompat.markSpriteActive(sprite);
            } catch (Exception ignored) {}
        }

        // Particle sprites change each frame (particles are created/destroyed),
        // so they can't be cached — still iterate live each frame, but this is
        // a small list bounded by active particle count.
        markPhantasiaParticleSprites();
    }

    /** For backwards compatibility — called from bakeLayer. No-op now. */
    public static void addIfAnimated(TextureAtlasSprite sprite, Set<TextureAtlasSprite> set) {
        set.add(sprite);
    }

    // ── Cache build ───────────────────────────────────────────────────────────

    private static void buildCache() {
        Set<TextureAtlasSprite> found = new HashSet<>();
        collectAnimatedFromAtlas(TextureAtlas.LOCATION_BLOCKS, found);
        collectAnimatedFromAtlas(TextureAtlas.LOCATION_PARTICLES, found);
        cachedAnimatedSprites = found;
    }

    private static void collectAnimatedFromAtlas(net.minecraft.resources.ResourceLocation atlasLocation,
                                                 Set<TextureAtlasSprite> out) {
        Minecraft mc = Minecraft.getInstance();
        try {
            var texture = mc.getTextureManager().getTexture(atlasLocation);
            if (!(texture instanceof TextureAtlas atlas)) return;

            if (!atlasFieldResolved) {
                atlasFieldResolved = true;
                for (Class<?> c = TextureAtlas.class; c != null; c = c.getSuperclass()) {
                    for (Field f : c.getDeclaredFields()) {
                        if (Map.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            Object val = f.get(atlas);
                            if (val instanceof Map<?, ?> m && !m.isEmpty()) {
                                Object first = m.values().iterator().next();
                                if (first instanceof TextureAtlasSprite) {
                                    spritesField = f;
                                    break;
                                }
                            }
                        }
                    }
                    if (spritesField != null) break;
                }
            }

            if (spritesField == null) return;

            @SuppressWarnings("unchecked")
            Map<?, TextureAtlasSprite> sprites = (Map<?, TextureAtlasSprite>) spritesField.get(atlas);
            // Mark ALL sprites, not just hasAnimation() ones. Some mods (GTCEu coils, firebox
            // casings) use custom animation drivers that Embeddium tracks separately and that
            // hasAnimation() doesn't report. The cost is just a flag write per sprite (~10k
            // entries in a full atlas, <0.1ms), far cheaper than missing animations.
            out.addAll(sprites.values());
        } catch (Exception ignored) {}
    }

    // ── Particle sprite marking ───────────────────────────────────────────────

    /**
     * Marks sprites from PhantasiaParticleEngine's isolated particle list.
     * Not cached since particle lists are dynamic.
     */
    private static void markPhantasiaParticleSprites() {
        var engine = PhantasiaParticleEngine.get();
        if (engine == null) return;

        var queueField = PhantasiaParticleEngine.getParticleListField();
        if (queueField == null) return;

        if (!particleSpriteResolved) {
            particleSpriteResolved = true;
            try {
                for (Class<?> c = TextureSheetParticle.class; c != null; c = c.getSuperclass()) {
                    for (Field f : c.getDeclaredFields()) {
                        if (TextureAtlasSprite.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            particleSpriteField = f;
                            break;
                        }
                    }
                    if (particleSpriteField != null) break;
                }
            } catch (Exception ignored) {}
        }

        if (particleSpriteField == null) return;

        try {
            @SuppressWarnings("unchecked")
            Map<?, Queue<Particle>> particleMap = (Map<?, Queue<Particle>>) queueField.get(engine);
            for (Queue<Particle> queue : particleMap.values()) {
                for (Particle particle : queue) {
                    try {
                        Object sprite = particleSpriteField.get(particle);
                        if (sprite instanceof TextureAtlasSprite tas) {
                            EmbeddiumCompat.markSpriteActive(tas);
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }
}
