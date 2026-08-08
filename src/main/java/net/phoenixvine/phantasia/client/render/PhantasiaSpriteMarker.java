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

@OnlyIn(Dist.CLIENT)
public final class PhantasiaSpriteMarker {

    public static final boolean EMBEDDIUM_PRESENT = ModList.get().isLoaded("embeddium") ||
            ModList.get().isLoaded("rubidium");

    private static Set<TextureAtlasSprite> cachedAnimatedSprites = null;

    private static Field spritesField = null;
    private static boolean atlasFieldResolved = false;

    private static Field particleSpriteField = null;
    private static boolean particleSpriteResolved = false;

    private PhantasiaSpriteMarker() {}

    public static void invalidateCache() {
        cachedAnimatedSprites = null;
    }

    public static void markAll(Set<TextureAtlasSprite> blockSprites) {
        if (!EMBEDDIUM_PRESENT) return;

        if (cachedAnimatedSprites == null) {
            buildCache();
        }

        for (TextureAtlasSprite sprite : cachedAnimatedSprites) {
            try {
                EmbeddiumCompat.markSpriteActive(sprite);
            } catch (Exception ignored) {}
        }

        markPhantasiaParticleSprites();
    }

    public static void addIfAnimated(TextureAtlasSprite sprite, Set<TextureAtlasSprite> set) {
        set.add(sprite);
    }

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

            out.addAll(sprites.values());
        } catch (Exception ignored) {}
    }

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
