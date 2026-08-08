package net.phoenixvine.phantasia.client.compat;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import me.jellysquid.mods.sodium.client.render.texture.SpriteUtil;

@OnlyIn(Dist.CLIENT)
public final class EmbeddiumCompat {

    public static void markSpriteActive(TextureAtlasSprite sprite) {
        SpriteUtil.markSpriteActive(sprite);
    }

    public static boolean hasAnimation(TextureAtlasSprite sprite) {
        return SpriteUtil.hasAnimation(sprite);
    }
}
