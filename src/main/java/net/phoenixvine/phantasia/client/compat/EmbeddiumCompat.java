package net.phoenixvine.phantasia.client.compat;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import me.jellysquid.mods.sodium.client.render.texture.SpriteUtil;

/**
 * Compat bridge for Embeddium's SpriteUtil.
 *
 * Embeddium 0.3.x on 1.20.1 Forge uses me.jellysquid.mods.sodium package and
 * exposes SpriteUtil as a plain class with static methods — no INSTANCE field.
 * (The INSTANCE pattern belongs to the newer Sodium API interface shape used
 * in 1.21+ versions after the org.embeddedt.embeddium repackage.)
 *
 * Only loaded when EMBEDDIUM_PRESENT is true (guarded in PhantasiaSpriteMarker).
 */
@OnlyIn(Dist.CLIENT)
public final class EmbeddiumCompat {

    public static void markSpriteActive(TextureAtlasSprite sprite) {
        SpriteUtil.markSpriteActive(sprite);
    }

    public static boolean hasAnimation(TextureAtlasSprite sprite) {
        return SpriteUtil.hasAnimation(sprite);
    }
}
