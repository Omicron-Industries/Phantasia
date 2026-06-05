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
import java.lang.reflect.Method;
import java.util.*;

/**
 * Marks animated sprites active each frame when Embeddium/Rubidium is present.
 *
 * Embeddium culls animation updates for sprites not visible in the player camera
 * frustum. Since the dummy world uses an isolated chunk source, none of its
 * sprites are ever auto-marked. This class marks them manually each frame.
 *
 * Strategy: mark ALL animated sprites in the blocks atlas and particles atlas,
 * rather than only sprites from baked quads. This covers:
 * - Block textures from baked quads (coils, hatches, etc.)
 * - BER overlay textures (controller active overlay, machine emissives)
 * which are looked up at render time and never appear in baked quads
 * - Particle sprites from PhantasiaParticleEngine's isolated particle list
 *
 * Marking all animated atlas sprites is cheap — hasAnimation() is a field read,
 * and only a small fraction of atlas sprites are animated.
 */
@OnlyIn(Dist.CLIENT)
public final class PhantasiaSpriteMarker {

    public static final boolean EMBEDDIUM_PRESENT = ModList.get().isLoaded("embeddium") ||
            ModList.get().isLoaded("rubidium");

    // TextureAtlas.getSprites() or equivalent — resolved once
    private static Method getSpritesMethod = null;
    private static Field spritesField = null;
    private static boolean atlasFieldResolved = false;

    // TextureSheetParticle.sprite field — resolved once
    private static Field particleSpriteField = null;
    private static boolean particleSpriteResolved = false;

    private PhantasiaSpriteMarker() {}

    /**
     * Mark all animated sprites in the blocks and particles atlases active.
     * Called once per render frame from PhantasiaWorldRenderer.
     * The blockSprites set (from baked quads) is kept for compatibility but
     * is now supplemented by a full atlas scan.
     */
    public static void markAll(Set<TextureAtlasSprite> blockSprites) {
        if (!EMBEDDIUM_PRESENT) return;

        markAtlas(TextureAtlas.LOCATION_BLOCKS);
        markAtlas(TextureAtlas.LOCATION_PARTICLES);
        markPhantasiaParticleSprites();
    }

    /** For backwards compatibility — called from bakeLayer. No-op now since we mark whole atlas. */
    public static void addIfAnimated(TextureAtlasSprite sprite, Set<TextureAtlasSprite> set) {
        // Still add to set so the set isn't empty (used as a bake signal elsewhere)
        set.add(sprite);
    }

    // ── Atlas marking ─────────────────────────────────────────────────────────

    private static void markAtlas(net.minecraft.resources.ResourceLocation atlasLocation) {
        Minecraft mc = Minecraft.getInstance();
        var atlasManager = mc.getTextureManager();
        try {
            var texture = atlasManager.getTexture(atlasLocation);
            if (!(texture instanceof TextureAtlas atlas)) return;

            // Reflect to get the sprites collection — field name varies by mapping
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

            if (spritesField != null) {
                @SuppressWarnings("unchecked")
                Map<?, TextureAtlasSprite> sprites = (Map<?, TextureAtlasSprite>) spritesField.get(atlas);
                for (TextureAtlasSprite sprite : sprites.values()) {
                    try {
                        if (EmbeddiumCompat.hasAnimation(sprite)) {
                            EmbeddiumCompat.markSpriteActive(sprite);
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }

    // ── Particle sprite marking ───────────────────────────────────────────────

    /**
     * Marks sprites from PhantasiaParticleEngine's isolated particle list.
     * mc.particleEngine is NOT used here — our particles are isolated.
     */
    private static void markPhantasiaParticleSprites() {
        var engine = PhantasiaParticleEngine.get();
        if (engine == null) return;

        var queueField = PhantasiaParticleEngine.getParticleListField();
        if (queueField == null) return;

        if (!particleSpriteResolved) {
            particleSpriteResolved = true;
            try {
                Class<?> tsp = TextureSheetParticle.class;
                for (Class<?> c = tsp; c != null; c = c.getSuperclass()) {
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
