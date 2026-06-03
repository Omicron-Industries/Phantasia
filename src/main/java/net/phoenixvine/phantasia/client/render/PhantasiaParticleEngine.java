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

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Isolated ParticleEngine for Phantasia.
 *
 * Uses a fresh ParticleEngine instance so Oculus never intercepts our
 * particles (it only hooks mc.particleEngine). Shares providers and
 * spriteSets from mc.particleEngine so all mod particle types and their
 * textures work correctly.
 *
 * Ticking: we tick particles manually (age/move each particle) rather than
 * calling instance.tick() which does expensive work assuming normal pipeline
 * context. This avoids the lag seen with the full engine tick.
 */
@OnlyIn(Dist.CLIENT)
public class PhantasiaParticleEngine {

    private static final Logger LOGGER = LogManager.getLogger("Phantasia");

    @Nullable private static ParticleEngine instance = null;

    // All map fields on ParticleEngine — resolved once, cached.
    // We copy all of them from mc.particleEngine except the particle queue
    // (which we want isolated). Covers providers, spriteSets, and any others.
    private static final List<Field> allMapFields = new ArrayList<>();
    @Nullable private static Field particleQueueField = null; // the Map<ParticleRenderType, Queue<Particle>>
    private static boolean fieldsResolved = false;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public static void init() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.particleEngine == null) return;

        instance = new ParticleEngine(level, mc.getTextureManager());
        resolveFields(mc);
        copySharedFields(mc);
    }


    // ADD THIS METHOD HERE
    @Nullable
    public static Field getParticleListField() {
        return particleQueueField;
    }

    public static void destroy() {
        if (instance != null) {
            // Clear the particle queue to free memory
            if (particleQueueField != null) {
                try {
                    Object map = particleQueueField.get(instance);
                    if (map instanceof Map<?,?> m) m.clear();
                } catch (Exception ignored) {}
            }
        }
        instance = null;
        // Reset so the next init() call re-scans fields on the fresh engine.
        // Without this, re-opening the screen after close skips resolveFields()
        // (fieldsResolved is still true) and particleQueueField stays null.
        fieldsResolved = false;
    }

    @Nullable
    public static ParticleEngine get() {
        return instance;
    }

    // ── Particle creation ─────────────────────────────────────────────────────

    public static <T extends ParticleOptions> void addParticle(T options,
                                                               double x, double y, double z,
                                                               double dx, double dy, double dz) {
        if (instance == null) {
            // Fallback — shouldn't happen if init() was called
            Minecraft mc = Minecraft.getInstance();
            if (mc.particleEngine != null) {
                try { mc.particleEngine.createParticle(options, x, y, z, dx, dy, dz); }
                catch (Exception ignored) {}
            }
            return;
        }
        try {
            instance.createParticle(options, x, y, z, dx, dy, dz);
        } catch (Exception ignored) {}
    }

    // ── Direct render ─────────────────────────────────────────────────────────

    /**
     * Renders all particles directly without calling ParticleEngine.render().
     *
     * Oculus mixins target ParticleEngine.render() and SingleQuadParticle.render()
     * at the class level — every call goes through Oculus's pipeline regardless
     * of which engine instance calls it. By grouping particles by render type
     * and calling each particle's render() ourselves (inside a try/catch so
     * Oculus's injected code fails silently), we bypass the interception.
     *
     * The try/catch per-particle is critical: Oculus's mixin throws or discards
     * when not in the geometry pass. We catch that and move on.
     */
    /**
     * Returns true if Oculus/Iris shaders are currently active.
     * When true, particle rendering is skipped — Oculus intercepts both
     * ParticleEngine.render() and Particle.render() at the class level,
     * making it impossible to render particles outside its pipeline.
     * Checked via reflection on IrisApi to avoid a hard dependency.
     */
    private static Boolean oculusPresent = null;
    private static java.lang.reflect.Method irisIsShaderPackInUse = null;

    public static boolean isOculusBlockingParticles() {
        if (oculusPresent == null) {
            try {
                Class<?> irisApi = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                java.lang.reflect.Method getInstance = irisApi.getMethod("getInstance");
                Object instance = getInstance.invoke(null);
                irisIsShaderPackInUse = instance.getClass().getMethod("isShaderPackInUse");
                irisIsShaderPackInUse.invoke(instance); // test call
                oculusPresent = true;
            } catch (Exception e) {
                oculusPresent = false;
            }
        }
        if (!oculusPresent || irisIsShaderPackInUse == null) return false;
        try {
            Object instance = irisIsShaderPackInUse.getDeclaringClass()
                    .getMethod("getInstance").invoke(null);
            return Boolean.TRUE.equals(irisIsShaderPackInUse.invoke(instance));
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public static void renderDirect(
            net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers,
            net.minecraft.client.renderer.LightTexture lightTexture,
            net.minecraft.client.Camera camera,
            float partialTick) {
        if (instance == null || particleQueueField == null) return;

        try {
            Map<net.minecraft.client.particle.ParticleRenderType, Queue<Particle>> particleMap =
                    (Map<net.minecraft.client.particle.ParticleRenderType, Queue<Particle>>)
                            particleQueueField.get(instance);

            if (particleMap.isEmpty()) return;

            lightTexture.turnOnLightLayer();
            com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();

            // ── FIX 1: Bind the correct shader and reset color ──
            com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getParticleShader);
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            var tesselator = com.mojang.blaze3d.vertex.Tesselator.getInstance();
            var bb = tesselator.getBuilder();
            var textureManager = Minecraft.getInstance().getTextureManager();

            for (var entry : particleMap.entrySet()) {
                var renderType = entry.getKey();
                var queue = entry.getValue();
                if (queue.isEmpty() || renderType == net.minecraft.client.particle.ParticleRenderType.NO_RENDER) continue;

                try {
                    renderType.begin(bb, textureManager);

                    for (Particle particle : queue) {
                        try {
                            particle.render(bb, camera, partialTick);
                        } catch (Exception e) {
                            LOGGER.warn("[Phantasia] Particle render crashed: {}", e.getMessage());
                        }
                    }

                    // ── FIX 2: Use the renderType's end() to restore GL states ──
                    renderType.end(tesselator);

                } catch (Exception e) {
                    LOGGER.warn("[Phantasia] Particle batch crashed: {}", e.getMessage());
                }
            }

            lightTexture.turnOffLightLayer();
        } catch (Exception e) {
            LOGGER.warn("[Phantasia] PhantasiaParticleEngine.renderDirect failed: {}", e.getMessage());
        }
    }

    /**
     * Ticks all particles in the isolated engine.
     *
     * We DON'T call instance.tick() because ParticleEngine.tick() does things
     * like updateParticleEngine() which assumes it's in the normal pipeline
     * context and causes lag. Instead we iterate the particle queue directly
     * and tick each particle, removing dead ones.
     */
    @SuppressWarnings("unchecked")
    public static void tick() {
        if (instance == null || particleQueueField == null) return;
        try {
            Map<ParticleRenderType, Queue<Particle>> particleMap =
                    (Map<ParticleRenderType, Queue<Particle>>) particleQueueField.get(instance);

            for (Map.Entry<ParticleRenderType, Queue<Particle>> entry : particleMap.entrySet()) {
                Queue<Particle> queue = entry.getValue();
                Queue<Particle> surviving = new ArrayDeque<>();
                for (Particle particle : queue) {
                    try {
                        particle.tick();
                        if (particle.isAlive()) surviving.add(particle);
                    } catch (Exception ignored) {}
                }
                queue.clear();
                queue.addAll(surviving);
            }
            // Clean up empty queues
            particleMap.entrySet().removeIf(e -> e.getValue().isEmpty());
        } catch (Exception ignored) {}
    }

    // ── Field resolution ──────────────────────────────────────────────────────

    /**
     * Copies all map fields from mc.particleEngine EXCEPT the particle queue.
     * This shares providers (ResourceLocation → ParticleProvider) and
     * spriteSets (ResourceLocation → MutableSpriteSet) by reference, so all
     * registered particle types and their sprite animations work correctly.
     */
    private static void copySharedFields(Minecraft mc) {
        if (instance == null) return;
        for (Field f : allMapFields) {
            if (f == particleQueueField) continue; // keep our own queue
            try {
                Object val = f.get(mc.particleEngine);
                f.set(instance, val);
                LOGGER.debug("[Phantasia] PhantasiaParticleEngine: shared field {}", f.getName());
            } catch (Exception e) {
                LOGGER.warn("[Phantasia] PhantasiaParticleEngine: failed to share {}: {}", f.getName(), e.getMessage());
            }
        }
    }

    private static void resolveFields(Minecraft mc) {
        if (fieldsResolved) return;
        fieldsResolved = true;
        allMapFields.clear();

        for (Class<?> c = ParticleEngine.class; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                allMapFields.add(f);

                // Identify the particle queue field by checking if values are Queues.
                // Primary strategy: check a non-empty map's first value.
                // Fallback strategy: check the generic type signature for Queue —
                // needed when mc.particleEngine has no particles yet at init time
                // (empty map → cannot inspect values), which would otherwise leave
                // particleQueueField null and silently disable ticking.
                if (particleQueueField == null) {
                    try {
                        Object val = f.get(mc.particleEngine);
                        if (val instanceof Map<?,?> m && !m.isEmpty()) {
                            Object firstVal = m.values().iterator().next();
                            if (firstVal instanceof Queue) {
                                particleQueueField = f;
                                LOGGER.info("[Phantasia] PhantasiaParticleEngine: particle queue field = {}.{}", c.getSimpleName(), f.getName());
                            }
                        } else {
                            // Empty map at init time — fall back to generic type signature.
                            // Map<ParticleRenderType, Queue<Particle>> will contain "Queue" in its type name.
                            java.lang.reflect.Type generic = f.getGenericType();
                            if (generic != null && generic.getTypeName().contains("Queue")) {
                                particleQueueField = f;
                                LOGGER.info("[Phantasia] PhantasiaParticleEngine: particle queue field by generic sig = {}.{}", c.getSimpleName(), f.getName());
                            }
                        }
                    } catch (Exception ignored) {}
                }

                LOGGER.info("[Phantasia] PhantasiaParticleEngine: map field {}.{}", c.getSimpleName(), f.getName());
            }
        }

        // Named fallback for particle queue if scan didn't find it (empty engine at init time)
        if (particleQueueField == null) {
            for (String name : new String[]{"particles", "f_107347_", "field_78879_a"}) {
                for (Field f : allMapFields) {
                    if (f.getName().equals(name)) {
                        particleQueueField = f;
                        LOGGER.info("[Phantasia] PhantasiaParticleEngine: particle queue field by name = {}", name);
                        break;
                    }
                }
                if (particleQueueField != null) break;
            }
        }

        if (particleQueueField == null) {
            LOGGER.warn("[Phantasia] PhantasiaParticleEngine: particle queue field not found — ticking disabled");
        }

        LOGGER.info("[Phantasia] PhantasiaParticleEngine: {} map fields found, queue field = {}",
                allMapFields.size(), particleQueueField != null ? particleQueueField.getName() : "null");
    }
}