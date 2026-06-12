package net.phoenixvine.phantasia.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScript;

import javax.annotation.Nullable;

/**
 * A particle emitter attached to a {@link PhantasiaScript.Step}.
 *
 * Three emission modes:
 *
 * PRESET — Named visual preset. Phantasia picks a suitable vanilla particle
 * type and emission parameters automatically. Good for drawing
 * attention to positions without needing to know particle IDs.
 *
 * VANILLA — Raw particle type by registry ID (e.g. "minecraft:smoke").
 * Full control over count, spread, and speed.
 *
 * AMBIENT — Calls animateTick() at the position each interval, producing
 * whatever ambient particles the block at that position emits
 * (fire crackling, lava bubbling, etc.). The block must be present
 * in the dummy world — use this for block-specific ambient effects
 * that you want script-controlled rather than always-on.
 *
 * Emission is driven once per {@code intervalTicks} game ticks. Set to 1 for
 * continuous, 20 for once per second, etc.
 */
public record PhantasiaParticleEffect(
                                      BlockPos localPos,
                                      Mode mode,
                                      @Nullable Preset preset,
                                      @Nullable String particleId,
                                      int count,
                                      float spread,
                                      float speed,
                                      int intervalTicks,
                                      float offsetY) {

    // ── Modes ─────────────────────────────────────────────────────────────────

    public enum Mode {
        PRESET,
        VANILLA,
        AMBIENT
    }

    // ── Presets ───────────────────────────────────────────────────────────────

    public enum Preset {
        /**
         * Soft floating sparkles — good for "this slot accepts items".
         * Emits: end_rod particles drifting upward.
         */
        HIGHLIGHT,

        /**
         * Pulsing attention ring — good for "look at this block".
         * Emits: crit particles in a ring around the block center.
         */
        ATTENTION,

        /**
         * Warning indicator — good for "this is wrong / missing".
         * Emits: smoke + angry_villager particles.
         */
        WARNING,

        /**
         * Success indicator — good for "this is correct / done".
         * Emits: happy_villager + totem particles.
         */
        SUCCESS,

        /**
         * Smoke column — good for muffler / exhaust positions.
         * Emits: campfire_cosy_smoke drifting upward.
         */
        SMOKE,

        /**
         * Spark burst — good for energy / EU flow.
         * Emits: electric_spark (or crit as fallback) outward burst.
         */
        SPARK
    }

    // ── Factory methods ───────────────────────────────────────────────────────

    public static PhantasiaParticleEffect preset(BlockPos pos, Preset preset) {
        return new PhantasiaParticleEffect(pos, Mode.PRESET, preset, null,
                4, 0.3f, 0.05f, 5, 1.0f);
    }

    public static PhantasiaParticleEffect preset(BlockPos pos, Preset preset,
                                                 int intervalTicks) {
        return new PhantasiaParticleEffect(pos, Mode.PRESET, preset, null,
                4, 0.3f, 0.05f, intervalTicks, 1.0f);
    }

    public static PhantasiaParticleEffect vanilla(BlockPos pos, String particleId,
                                                  int count, float spread, float speed) {
        return new PhantasiaParticleEffect(pos, Mode.VANILLA, null, particleId,
                count, spread, speed, 1, 0.5f);
    }

    public static PhantasiaParticleEffect vanilla(BlockPos pos, String particleId,
                                                  int count, float spread, float speed,
                                                  int intervalTicks) {
        return new PhantasiaParticleEffect(pos, Mode.VANILLA, null, particleId,
                count, spread, speed, intervalTicks, 0.5f);
    }

    public static PhantasiaParticleEffect ambient(BlockPos pos) {
        return new PhantasiaParticleEffect(pos, Mode.AMBIENT, null, null,
                1, 0f, 0f, 1, 0f);
    }

    public static PhantasiaParticleEffect ambient(BlockPos pos, int intervalTicks) {
        return new PhantasiaParticleEffect(pos, Mode.AMBIENT, null, null,
                1, 0f, 0f, intervalTicks, 0f);
    }

    // ── Emission ──────────────────────────────────────────────────────────────

    /**
     * Emits this effect into {@code level} at the given game tick.
     * Returns silently if the tick doesn't match the interval.
     *
     * @param level    the dummy world (PhantasiaTrackedDummyWorld)
     * @param worldPos this effect's localPos already converted to world space
     * @param tick     current game tick (from mc.level.getGameTime())
     * @param random   random source for spread
     */
    public void emit(net.minecraft.world.level.Level level,
                     BlockPos worldPos, long tick,
                     net.minecraft.util.RandomSource random) {
        if (intervalTicks > 1 && tick % intervalTicks != 0) return;

        double cx = worldPos.getX() + 0.5;
        double cy = worldPos.getY() + offsetY;
        double cz = worldPos.getZ() + 0.5;

        switch (mode) {
            case PRESET -> emitPreset(level, cx, cy, cz, random);
            case VANILLA -> emitVanilla(level, cx, cy, cz, random);
            case AMBIENT -> {
                if (level instanceof net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld w) {
                    w.tickAnimateForPos(worldPos, random);
                }
            }
        }
    }

    private void emitPreset(net.minecraft.world.level.Level level,
                            double cx, double cy, double cz,
                            net.minecraft.util.RandomSource random) {
        if (preset == null) return;
        switch (preset) {
            case HIGHLIGHT -> {
                for (int i = 0; i < count; i++) {
                    double ox = (random.nextDouble() - 0.5) * spread;
                    double oz = (random.nextDouble() - 0.5) * spread;
                    level.addParticle(ParticleTypes.END_ROD,
                            cx + ox, cy, cz + oz,
                            0, speed + random.nextDouble() * 0.03, 0);
                }
            }
            case ATTENTION -> {
                // Ring of crit particles around the block
                for (int i = 0; i < Math.max(count, 6); i++) {
                    double angle = (2 * Math.PI * i) / Math.max(count, 6);
                    double rx = Math.cos(angle) * 0.6;
                    double rz = Math.sin(angle) * 0.6;
                    level.addParticle(ParticleTypes.CRIT,
                            cx + rx, cy, cz + rz,
                            0, speed * 0.5, 0);
                }
            }
            case WARNING -> {
                for (int i = 0; i < count; i++) {
                    double ox = (random.nextDouble() - 0.5) * spread;
                    double oz = (random.nextDouble() - 0.5) * spread;
                    level.addParticle(ParticleTypes.SMOKE, cx + ox, cy, cz + oz,
                            0, speed, 0);
                }
                level.addParticle(ParticleTypes.ANGRY_VILLAGER, cx, cy + 0.5, cz,
                        0, 0, 0);
            }
            case SUCCESS -> {
                for (int i = 0; i < count; i++) {
                    double ox = (random.nextDouble() - 0.5) * spread;
                    double oz = (random.nextDouble() - 0.5) * spread;
                    level.addParticle(ParticleTypes.HAPPY_VILLAGER,
                            cx + ox, cy, cz + oz,
                            (random.nextDouble() - 0.5) * 0.1, speed, (random.nextDouble() - 0.5) * 0.1);
                }
            }
            case SMOKE -> {
                for (int i = 0; i < count; i++) {
                    double ox = (random.nextDouble() - 0.5) * spread * 0.5;
                    double oz = (random.nextDouble() - 0.5) * spread * 0.5;
                    level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            cx + ox, cy, cz + oz,
                            0, speed + random.nextDouble() * 0.01, 0);
                }
            }
            case SPARK -> {
                for (int i = 0; i < count; i++) {
                    double vx = (random.nextDouble() - 0.5) * speed * 2;
                    double vy = random.nextDouble() * speed;
                    double vz = (random.nextDouble() - 0.5) * speed * 2;
                    level.addParticle(ParticleTypes.CRIT, cx, cy, cz, vx, vy, vz);
                }
            }
        }
    }

    private void emitVanilla(net.minecraft.world.level.Level level,
                             double cx, double cy, double cz,
                             net.minecraft.util.RandomSource random) {
        if (particleId == null) return;
        try {
            var rl = new net.minecraft.resources.ResourceLocation(particleId);
            var type = ForgeRegistries.PARTICLE_TYPES.getValue(rl);
            if (!(type instanceof SimpleParticleType spt)) return;
            for (int i = 0; i < count; i++) {
                double ox = (random.nextDouble() - 0.5) * spread;
                double oy = (random.nextDouble() - 0.5) * spread * 0.5;
                double oz = (random.nextDouble() - 0.5) * spread;
                level.addParticle(spt, cx + ox, cy + oy, cz + oz,
                        (random.nextDouble() - 0.5) * speed,
                        random.nextDouble() * speed * 0.5,
                        (random.nextDouble() - 0.5) * speed);
            }
        } catch (Exception ignored) {}
    }
}
