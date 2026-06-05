package net.phoenixvine.phantasia.mixin.aiimprovements;

import net.minecraft.world.entity.ai.control.LookControl;
import net.phoenixvine.phantasia.client.PhantasiaRenderBypass;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Targets the core vanilla AI look controller.
 * This skips look calculations and prevents AI Improvements modifications
 * from messing with entity rotations inside Phantasia custom preview scenes.
 */
@Mixin(LookControl.class)
public class MixinVanillaLookBypass {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void cancelLookTickDuringPhantasia(CallbackInfo ci) {
        if (PhantasiaRenderBypass.isInPhantasiaInstance()) {
            // Stop the look controller tracking processing entirely
            // while inside our virtual world scenes
            ci.cancel();
        }
    }
}
