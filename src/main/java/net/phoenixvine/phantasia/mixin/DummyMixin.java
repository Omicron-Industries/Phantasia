package net.phoenixvine.phantasia.mixin;

import net.minecraft.world.level.levelgen.WorldgenRandom;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = WorldgenRandom.class, remap = false)
public class DummyMixin {}
