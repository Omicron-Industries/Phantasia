package net.phoenixvine.phantasia.mixin.embeddium;

import me.jellysquid.mods.sodium.client.render.texture.SpriteContentsExtended;
import me.jellysquid.mods.sodium.client.render.texture.SpriteUtil;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.phoenixvine.phantasia.client.PhantasiaRenderBypass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SpriteUtil.class, remap = false)
public class MixinEmbeddiumSpriteUtil {

    @Inject(method = "markSpriteActive(Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void forceSpriteActiveInUI(TextureAtlasSprite sprite, CallbackInfo ci) {
        if (PhantasiaRenderBypass.isInPhantasiaInstance()) {
            if (sprite != null) {
                // Keep the sprite active so Embeddium's ticker uploads its frames continuously
                ((SpriteContentsExtended) sprite.contents()).sodium$setActive(true);
            }
            // Cancel the rest of the method so it doesn't try to register into a non-existent ChunkBuildContext
            ci.cancel();
        }
    }
}