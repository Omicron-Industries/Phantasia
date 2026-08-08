package net.phoenixvine.phantasia.mixin.aiimprovements;

import net.minecraft.world.entity.ai.control.LookControl;
import net.phoenixvine.phantasia.client.event.PhantasiaRenderBypass;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LookControl.class)
public class MixinVanillaLookBypass {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void cancelLookTickDuringPhantasia(CallbackInfo ci) {
        if (PhantasiaRenderBypass.isInPhantasiaInstance()) {

            ci.cancel();
        }
    }
}
