package net.phoenixvine.phantasia.client.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.util.*;

import javax.annotation.Nullable;

/**
 * Phantasia particle system.
 *
 * Architecture: particles go into mc.particleEngine via normal ClientLevel.addParticle()
 * calls — no separate engine, no field swapping, no level juggling. We track which
 * particles are "ours" by identity in a Set<Particle>. At render time we iterate the
 * real engine's queue, draw only our particles ourselves (bypassing Oculus's render()
 * mixin), and suppress our particles from the normal ParticleEngine.render() pass
 * by temporarily marking them removed during that pass.
 *
 * Why not a separate ParticleEngine:
 * mc.particleEngine is final in Minecraft — reflection set silently fails.
 * ClientLevel.addParticle() always calls Minecraft.getInstance().particleEngine
 * regardless of be.setLevel() or machine-level field swaps.
 *
 * Why not call particle.render():
 * Oculus patches Particle.render() at class-load time unconditionally.
 * We use renderParticleManual() to emit quads directly into the BufferBuilder.
 */
@OnlyIn(Dist.CLIENT)
public class PhantasiaParticleEngine {

    private static final Logger LOGGER = LogManager.getLogger("Phantasia");

    // Particle types for which we've already logged a missing-provider warning.
    private static final Set<Object> warnedMissingProvider = Collections.newSetFromMap(new IdentityHashMap<>());
    // Identity-keyed (IdentityHashMap as a Set) so we don't interfere with
    // particles that happen to implement equals().
    private static Set<Particle> ownedParticles = Collections.newSetFromMap(new IdentityHashMap<>());

    // Empty dummy world used as the collision level for spawned particles.
    // Particles tick their collision check against this world — since it has no
    // blocks, getBlockCollisions(null, aabb) returns empty and particles move freely.
    // The particles still live in mc.particleEngine (Oculus sees them normally).
    @Nullable
    private static net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld particleCollisionWorld = null;

    private static net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld getParticleCollisionWorld() {
        if (particleCollisionWorld == null) {
            particleCollisionWorld = new net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld();
        }
        return particleCollisionWorld;
    }

    // The real particle queue in mc.particleEngine — resolved once.
    @Nullable
    private static Field particleQueueField = null;
    private static boolean fieldsResolved = false;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public static void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.particleEngine == null) return;
        resolveFields(mc);
        ownedParticles.clear();
        warnedMissingProvider.clear();
        particleCollisionWorld = null;
    }

    public static void destroy() {
        // Mark all owned particles as removed so mc.particleEngine culls them
        // on its next tick — they won't render or linger in the real world.
        for (Particle p : ownedParticles) {
            try {
                p.remove();
            } catch (Exception ignored) {}
        }
        ownedParticles.clear();
        warnedMissingProvider.clear();
        particleCollisionWorld = null;
    }

    // Kept for call-site compatibility
    @Nullable
    public static ParticleEngine get() {
        return Minecraft.getInstance().particleEngine;
    }

    @Nullable
    public static Field getParticleListField() {
        return particleQueueField;
    }

    // ── Particle creation ─────────────────────────────────────────────────────

    /**
     * Adds a particle to mc.particleEngine and registers it as owned by Phantasia.
     *
     * We snapshot the queue before and after createParticle() to find the new
     * particle by identity — createParticle() doesn't return the particle it adds.
     * The new particle is added to ownedParticles for tracking.
     */
    @SuppressWarnings("unchecked")
    // Providers map field on ParticleEngine (ResourceLocation → ParticleProvider)
    @Nullable
    private static Field providersField = null;
    private static boolean providersResolved = false;

    @SuppressWarnings("unchecked")
    public static <T extends ParticleOptions> void addParticle(T options,
                                                               double x, double y, double z,
                                                               double dx, double dy, double dz) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.particleEngine == null || particleQueueField == null) return;

        // We cannot use mc.particleEngine.createParticle() because it checks
        // level.isLoaded(pos) before creating the particle. The scene blocks live at
        // (0-N, 50, 0-N) which is almost never loaded in mc.level, so createParticle
        // silently returns null every time. We bypass it by calling the provider directly.
        try {
            if (!providersResolved) resolveProvidersField(mc);

            Particle particle = null;

            if (providersField != null) {
                Map<net.minecraft.resources.ResourceLocation, net.minecraft.client.particle.ParticleProvider<?>> providers = (Map<net.minecraft.resources.ResourceLocation, net.minecraft.client.particle.ParticleProvider<?>>) providersField
                        .get(mc.particleEngine);
                net.minecraft.resources.ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE
                        .getKey(options.getType());
                @SuppressWarnings("rawtypes")
                net.minecraft.client.particle.ParticleProvider provider = key != null ? providers.get(key) : null;
                if (provider != null) {
                    particle = provider.createParticle(options, getParticleCollisionWorld().getAsClientWorld().get(), x,
                            y, z, dx, dy, dz);
                }
            }

            if (particle == null) {
                if (warnedMissingProvider.add(options.getType())) {
                    LOGGER.warn("[Phantasia] addParticle: no provider for {} — skipping", options.getType());
                }
                return;
            }

            // Add directly to the queue, bypassing the chunk-loaded distance check
            Map<ParticleRenderType, Queue<Particle>> queueMap = (Map<ParticleRenderType, Queue<Particle>>) particleQueueField
                    .get(mc.particleEngine);
            ParticleRenderType renderType = particle.getRenderType();
            if (renderType != ParticleRenderType.NO_RENDER) {
                queueMap.computeIfAbsent(renderType, k -> new ArrayDeque<>()).add(particle);
                ownedParticles.add(particle);
            }
        } catch (Exception e) {
            LOGGER.warn("[Phantasia] addParticle failed: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void resolveProvidersField(Minecraft mc) {
        providersResolved = true;
        for (Class<?> c = ParticleEngine.class; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    java.lang.reflect.Type generic = f.getGenericType();
                    String sig = generic != null ? generic.getTypeName() : "";
                    // Provider map: Map<ResourceLocation, ParticleProvider<?>>
                    if (sig.contains("ResourceLocation") && sig.contains("ParticleProvider")) {
                        providersField = f;
                        LOGGER.info("[Phantasia] providers field: {}.{}", c.getSimpleName(), f.getName());
                        return;
                    }
                } catch (Exception ignored) {}
            }
        }
        // Named fallback
        for (String name : new String[] { "providers", "f_107344_", "field_78877_h" }) {
            try {
                Field f = ParticleEngine.class.getDeclaredField(name);
                f.setAccessible(true);
                providersField = f;
                LOGGER.info("[Phantasia] providers field by name: {}", name);
                return;
            } catch (Exception ignored) {}
        }
        LOGGER.error("[Phantasia] providers field not found — particles will not work");
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    /**
     * Removes dead owned particles from the tracking set.
     * mc.particleEngine ticks and removes them from its queue itself.
     */
    public static void tick() {
        ownedParticles.removeIf(p -> !p.isAlive());
    }

    // ── Oculus ────────────────────────────────────────────────────────────────

    private static Boolean oculusPresent = null;

    public static boolean isOculusPresent() {
        if (oculusPresent == null) {
            try {
                Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                oculusPresent = true;
            } catch (ClassNotFoundException e) {
                oculusPresent = false;
            }
        }
        return oculusPresent;
    }

    // Kept for call-site compat — always false now (we always render, never skip)
    public static boolean isOculusBlockingParticles() {
        return false;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    /**
     * Renders only owned particles, using manual quad emission to bypass Oculus.
     *
     * To prevent owned particles from also being drawn by the normal
     * ParticleEngine.render() pass (which would double-draw them, or worse,
     * go through Oculus's mixin), we temporarily mark them removed before
     * the normal render pass runs, then restore them after. This method is
     * called BEFORE the normal game render loop from PhantasiaWorldRenderer,
     * so owned particles are invisible to the normal pass for that frame.
     *
     * For non-Oculus environments, particle.render() would work, but we use
     * the manual path unconditionally so behaviour is identical with/without Oculus.
     */
    @SuppressWarnings("unchecked")
    public static void renderDirect(
                                    net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers,
                                    net.minecraft.client.renderer.LightTexture lightTexture,
                                    Camera camera,
                                    float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.particleEngine == null || particleQueueField == null || ownedParticles.isEmpty()) return;

        resolveParticleFields();

        try {
            Map<ParticleRenderType, Queue<Particle>> particleMap = (Map<ParticleRenderType, Queue<Particle>>) particleQueueField
                    .get(mc.particleEngine);
            if (particleMap.isEmpty()) return;

            lightTexture.turnOnLightLayer();
            com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
            com.mojang.blaze3d.systems.RenderSystem.setShader(
                    net.minecraft.client.renderer.GameRenderer::getParticleShader);
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            var tesselator = com.mojang.blaze3d.vertex.Tesselator.getInstance();
            var bb = tesselator.getBuilder();
            var textureManager = mc.getTextureManager();

            Quaternionf camRot = camera.rotation();
            Vector3f right = camRot.transform(new Vector3f(1, 0, 0));
            Vector3f up = camRot.transform(new Vector3f(0, 1, 0));
            float rx = right.x, ry = right.y, rz = right.z;
            float ux = up.x, uy = up.y, uz = up.z;

            for (var entry : particleMap.entrySet()) {
                ParticleRenderType renderType = entry.getKey();
                Queue<Particle> queue = entry.getValue();
                if (renderType == ParticleRenderType.NO_RENDER) continue;

                // Collect owned particles in this bucket
                List<Particle> toRender = new ArrayList<>();
                for (Particle p : queue) {
                    if (ownedParticles.contains(p)) toRender.add(p);
                }
                if (toRender.isEmpty()) continue;

                try {
                    renderType.begin(bb, textureManager);
                    for (Particle p : toRender) {
                        renderParticleManual(bb, camera, p, partialTick, rx, ry, rz, ux, uy, uz);
                    }
                    renderType.end(tesselator);
                } catch (Exception e) {
                    LOGGER.warn("[Phantasia] particle batch failed: {}", e.getMessage());
                }
            }

            lightTexture.turnOffLightLayer();
        } catch (Exception e) {
            LOGGER.warn("[Phantasia] renderDirect failed: {}", e.getMessage());
        }
    }

    // ── Particle field cache ───────────────────────────────────────────────────

    private static boolean particleFieldsResolved = false;
    private static Field f_x, f_y, f_z;           // xo/yo/zo (previous pos, double)
    private static Field f_px, f_py, f_pz;        // x/y/z (current pos, double)
    private static Field f_rr, f_rg, f_rb, f_ra;  // rCol, gCol, bCol, alpha (float)
    private static Field f_scale;                   // quadSize from SingleQuadParticle (float, half-size)
    private static boolean scaleIsQuadSize = false;
    private static Field f_sprite;                 // TextureAtlasSprite from TextureSheetParticle

    private static void resolveParticleFields() {
        if (particleFieldsResolved) return;
        particleFieldsResolved = true;

        f_x = findField(Particle.class, double.class, "xo", "f_107374_");
        f_y = findField(Particle.class, double.class, "yo", "f_107373_");
        f_z = findField(Particle.class, double.class, "zo", "f_107372_");
        f_px = findField(Particle.class, double.class, "x", "f_107382_");
        f_py = findField(Particle.class, double.class, "y", "f_107383_");
        f_pz = findField(Particle.class, double.class, "z", "f_107381_");
        f_rr = findField(Particle.class, float.class, "rCol", "f_107390_");
        f_rg = findField(Particle.class, float.class, "gCol", "f_107389_");
        f_rb = findField(Particle.class, float.class, "bCol", "f_107388_");
        f_ra = findField(Particle.class, float.class, "alpha", "f_107392_");

        // Prefer quadSize (SingleQuadParticle) over bbWidth (Particle) — quadSize is
        // the actual render half-size; bbWidth is the hitbox full-width.
        Field qs = findField(net.minecraft.client.particle.SingleQuadParticle.class,
                float.class, "quadSize", "f_107518_");
        if (qs != null) {
            f_scale = qs;
            scaleIsQuadSize = true;
        } else {
            f_scale = findField(Particle.class, float.class, "bbWidth", "f_107395_");
            scaleIsQuadSize = false;
        }

        // TextureAtlasSprite field on TextureSheetParticle — try names then type-scan
        f_sprite = findField(net.minecraft.client.particle.TextureSheetParticle.class,
                net.minecraft.client.renderer.texture.TextureAtlasSprite.class,
                "sprite", "f_107534_");
        if (f_sprite == null) {
            for (Class<?> c = net.minecraft.client.particle.TextureSheetParticle.class; c != null &&
                    f_sprite == null; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType() == net.minecraft.client.renderer.texture.TextureAtlasSprite.class &&
                            !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        f.setAccessible(true);
                        f_sprite = f;
                        LOGGER.info("[Phantasia] sprite field by type-scan: {}.{}", c.getSimpleName(), f.getName());
                    }
                }
            }
        }

        LOGGER.info("[Phantasia] particle fields: pos={} sprite={} scale={}(isQuadSize={})",
                f_px != null, f_sprite != null, f_scale != null, scaleIsQuadSize);
    }

    @Nullable
    private static Field findField(Class<?> owner, Class<?> type, String... names) {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            for (String name : names) {
                try {
                    Field f = c.getDeclaredField(name);
                    if (f.getType() == type) {
                        f.setAccessible(true);
                        return f;
                    }
                } catch (NoSuchFieldException ignored) {}
            }
        }
        return null;
    }

    // ── Manual quad emission ──────────────────────────────────────────────────

    private static void renderParticleManual(
                                             com.mojang.blaze3d.vertex.BufferBuilder bb,
                                             Camera camera,
                                             Particle particle,
                                             float partial,
                                             float rx, float ry, float rz,
                                             float ux, float uy, float uz) {
        try {
            if (!(particle instanceof net.minecraft.client.particle.TextureSheetParticle) || f_sprite == null) {
                // Non-TextureSheetParticle — call render() directly (Oculus doesn't patch these)
                particle.render(bb, camera, partial);
                return;
            }

            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = (net.minecraft.client.renderer.texture.TextureAtlasSprite) f_sprite
                    .get(particle);
            if (sprite == null) return;

            double prevX = f_x != null ? f_x.getDouble(particle) : 0;
            double prevY = f_y != null ? f_y.getDouble(particle) : 0;
            double prevZ = f_z != null ? f_z.getDouble(particle) : 0;
            double curX = f_px != null ? f_px.getDouble(particle) : 0;
            double curY = f_py != null ? f_py.getDouble(particle) : 0;
            double curZ = f_pz != null ? f_pz.getDouble(particle) : 0;

            float dx = (float) (net.minecraft.util.Mth.lerp(partial, prevX, curX) - camera.getPosition().x);
            float dy = (float) (net.minecraft.util.Mth.lerp(partial, prevY, curY) - camera.getPosition().y);
            float dz = (float) (net.minecraft.util.Mth.lerp(partial, prevZ, curZ) - camera.getPosition().z);

            float r = f_rr != null ? f_rr.getFloat(particle) : 1f;
            float g = f_rg != null ? f_rg.getFloat(particle) : 1f;
            float b = f_rb != null ? f_rb.getFloat(particle) : 1f;
            float a = f_ra != null ? f_ra.getFloat(particle) : 1f;
            float rawScale = f_scale != null ? f_scale.getFloat(particle) : 0.1f;
            float hw = scaleIsQuadSize ? rawScale : rawScale / 2f;

            float u0 = sprite.getU0(), u1 = sprite.getU1();
            float v0 = sprite.getV0(), v1 = sprite.getV1();
            int light = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;

            bb.vertex(dx + (-rx - ux) * hw, dy + (-ry - uy) * hw, dz + (-rz - uz) * hw).uv(u1, v1).color(r, g, b, a)
                    .uv2(light).endVertex();
            bb.vertex(dx + (-rx + ux) * hw, dy + (-ry + uy) * hw, dz + (-rz + uz) * hw).uv(u1, v0).color(r, g, b, a)
                    .uv2(light).endVertex();
            bb.vertex(dx + (rx + ux) * hw, dy + (ry + uy) * hw, dz + (rz + uz) * hw).uv(u0, v0).color(r, g, b, a)
                    .uv2(light).endVertex();
            bb.vertex(dx + (rx - ux) * hw, dy + (ry - uy) * hw, dz + (rz - uz) * hw).uv(u0, v1).color(r, g, b, a)
                    .uv2(light).endVertex();

        } catch (Exception e) {
            LOGGER.debug("[Phantasia] renderParticleManual: {}", e.getMessage());
        }
    }

    // ── Queue field resolution ────────────────────────────────────────────────

    private static void resolveFields(Minecraft mc) {
        if (fieldsResolved) return;
        fieldsResolved = true;

        for (Class<?> c = ParticleEngine.class; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);

                if (particleQueueField == null) {
                    try {
                        java.lang.reflect.Type generic = f.getGenericType();
                        String sig = generic != null ? generic.getTypeName() : "";
                        if (sig.contains("ParticleRenderType") && sig.contains("Queue")) {
                            particleQueueField = f;
                            LOGGER.info("[Phantasia] queue field by sig: {}.{}", c.getSimpleName(), f.getName());
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        // Named fallback
        if (particleQueueField == null) {
            for (String name : new String[] { "particles", "f_107347_", "field_78879_a" }) {
                try {
                    Field f = ParticleEngine.class.getDeclaredField(name);
                    f.setAccessible(true);
                    particleQueueField = f;
                    LOGGER.info("[Phantasia] queue field by name: {}", name);
                    break;
                } catch (Exception ignored) {}
            }
        }

        if (particleQueueField == null)
            LOGGER.error("[Phantasia] particle queue field not found — particles will not work");
        else
            LOGGER.info("[Phantasia] particle queue field: {}", particleQueueField.getName());
    }
}
