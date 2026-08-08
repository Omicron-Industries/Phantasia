package net.phoenixvine.phantasia.client.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
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

@OnlyIn(Dist.CLIENT)
public class PhantasiaParticleEngine {

    private static final Logger LOGGER = LogManager.getLogger("Phantasia");

    private static final Set<Object> warnedMissingProvider = Collections.newSetFromMap(new IdentityHashMap<>());

    private static final Map<ParticleRenderType, Queue<Particle>> ownedQueue = new LinkedHashMap<>();

    private static final Set<Particle> ownedParticles = Collections.newSetFromMap(new IdentityHashMap<>());

    @Nullable
    private static Field particleQueueField = null;
    private static boolean fieldsResolved = false;

    public static void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.particleEngine == null) return;
        resolveFields(mc);
        ownedQueue.clear();
        ownedParticles.clear();
        warnedMissingProvider.clear();
    }

    public static void destroy() {
        for (Queue<Particle> queue : ownedQueue.values()) {
            for (Particle p : queue) {
                try {
                    p.remove();
                } catch (Exception ignored) {}
            }
        }
        ownedQueue.clear();
        ownedParticles.clear();
        warnedMissingProvider.clear();
    }

    @Nullable
    public static ParticleEngine get() {
        return Minecraft.getInstance().particleEngine;
    }

    @Nullable
    public static Field getParticleListField() {
        return particleQueueField;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static Field providersField = null;
    private static boolean providersResolved = false;

    @Nullable
    private static Field f_hasPhysics = null;
    private static boolean hasPhysicsResolved = false;

    @SuppressWarnings("unchecked")
    public static <T extends ParticleOptions> void addParticle(T options,
                                                               double x, double y, double z,
                                                               double dx, double dy, double dz) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.particleEngine == null) return;

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
                    net.minecraft.client.multiplayer.ClientLevel clientLevel = mc.level;
                    if (clientLevel != null) {
                        particle = provider.createParticle(options, clientLevel, x, y, z, dx, dy, dz);
                    }
                }
            }

            if (particle == null) {
                if (warnedMissingProvider.add(options.getType())) {
                    LOGGER.warn("[Phantasia] addParticle: no provider for {} — skipping", options.getType());
                }
                return;
            }

            if (!hasPhysicsResolved) {
                hasPhysicsResolved = true;
                f_hasPhysics = findField(Particle.class, boolean.class, "hasPhysics", "f_107224_");
            }
            if (f_hasPhysics != null) {
                try {
                    f_hasPhysics.setBoolean(particle, false);
                } catch (Exception ignored) {}
            }

            ParticleRenderType renderType = particle.getRenderType();
            if (renderType != ParticleRenderType.NO_RENDER) {

                ownedQueue.computeIfAbsent(renderType, k -> new ArrayDeque<>()).add(particle);
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
                    if (sig.contains("ResourceLocation") && sig.contains("ParticleProvider")) {
                        providersField = f;
                        LOGGER.info("[Phantasia] providers field: {}.{}", c.getSimpleName(), f.getName());
                        return;
                    }
                } catch (Exception ignored) {}
            }
        }
        for (String name : new String[] { "providers", "f_107293_", "f_107344_", "field_78877_h" }) {
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

    public static void tick() {
        for (Queue<Particle> queue : ownedQueue.values()) {
            Iterator<Particle> it = queue.iterator();
            while (it.hasNext()) {
                Particle p = it.next();
                if (p.isAlive()) {
                    try {
                        p.tick();
                    } catch (Exception ignored) {}
                }
                if (!p.isAlive()) {
                    it.remove();
                    ownedParticles.remove(p);
                }
            }
        }
    }

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

    public static boolean isOculusBlockingParticles() {
        return false;
    }

    public static void renderDirect(
                                    net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers,
                                    net.minecraft.client.renderer.LightTexture lightTexture,
                                    Camera camera,
                                    float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.particleEngine == null || ownedParticles.isEmpty()) return;

        resolveParticleFields();

        try {
            if (ownedQueue.isEmpty()) return;

            com.mojang.blaze3d.vertex.PoseStack poseStack = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
            poseStack.pushPose();

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

            for (var entry : ownedQueue.entrySet()) {
                ParticleRenderType renderType = entry.getKey();
                Queue<Particle> queue = entry.getValue();
                if (renderType == ParticleRenderType.NO_RENDER || queue.isEmpty()) continue;

                try {
                    renderType.begin(bb, textureManager);
                    for (Particle p : queue) {
                        renderParticleManual(bb, camera, p, partialTick, rx, ry, rz, ux, uy, uz);
                    }
                    renderType.end(tesselator);
                } catch (Exception e) {
                    LOGGER.warn("[Phantasia] particle batch failed: {}", e.getMessage());
                }
            }

            lightTexture.turnOffLightLayer();

            poseStack.popPose();
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();

        } catch (Exception e) {
            LOGGER.warn("[Phantasia] renderDirect failed: {}", e.getMessage());
        }
    }

    private static boolean particleFieldsResolved = false;
    private static Field f_x, f_y, f_z;
    private static Field f_px, f_py, f_pz;
    private static Field f_rr, f_rg, f_rb, f_ra;
    private static Field f_scale;
    private static boolean scaleIsQuadSize = false;
    private static Field f_sprite;

    private static void resolveParticleFields() {
        if (particleFieldsResolved) return;
        particleFieldsResolved = true;

        try {
            List<Field> doubleFields = new ArrayList<>();
            for (Field f : Particle.class.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getType() == double.class) {
                    f.setAccessible(true);
                    doubleFields.add(f);
                }
            }

            if (doubleFields.size() >= 6) {
                f_x = doubleFields.get(0);
                f_y = doubleFields.get(1);
                f_z = doubleFields.get(2);
                f_px = doubleFields.get(3);
                f_py = doubleFields.get(4);
                f_pz = doubleFields.get(5);
            }
        } catch (Exception e) {
            LOGGER.warn("[Phantasia] Failed to resolve positional fields: {}", e.getMessage());
        }

        try {
            List<Field> floatFields = new ArrayList<>();
            for (Field f : Particle.class.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getType() == float.class) {
                    f.setAccessible(true);
                    floatFields.add(f);
                }
            }

            f_rr = findField(Particle.class, float.class, "rCol", "f_107227_", "f_107390_");
            f_rg = findField(Particle.class, float.class, "gCol", "f_107228_", "f_107389_");
            f_rb = findField(Particle.class, float.class, "bCol", "f_107229_", "f_107388_");
            f_ra = findField(Particle.class, float.class, "alpha", "f_107230_", "f_107392_");

            Field qs = findField(net.minecraft.client.particle.SingleQuadParticle.class, float.class, "quadSize",
                    "f_107663_", "f_107518_");
            if (qs != null) {
                f_scale = qs;
                scaleIsQuadSize = true;
            } else {
                f_scale = findField(Particle.class, float.class, "bbWidth", "f_107221_", "f_107395_");

                if (f_scale == null && !floatFields.isEmpty()) f_scale = floatFields.get(0);
                scaleIsQuadSize = false;
            }
        } catch (Exception e) {}

        f_sprite = findField(net.minecraft.client.particle.TextureSheetParticle.class,
                net.minecraft.client.renderer.texture.TextureAtlasSprite.class,
                "sprite", "f_108321_", "f_107534_");

        if (f_sprite == null) {
            for (Class<?> c = net.minecraft.client.particle.TextureSheetParticle.class; c != null &&
                    f_sprite == null; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType() == net.minecraft.client.renderer.texture.TextureAtlasSprite.class &&
                            !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        f.setAccessible(true);
                        f_sprite = f;
                    }
                }
            }
        }

        LOGGER.info("[Phantasia] particle fields: pos={} sprite={} scale={}(isQuadSize={})",
                f_px != null, f_sprite != null, f_scale != null, scaleIsQuadSize);
    }

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

    private static void renderParticleManual(
                                             com.mojang.blaze3d.vertex.BufferBuilder bb,
                                             Camera camera,
                                             Particle particle,
                                             float partial,
                                             float rx, float ry, float rz,
                                             float ux, float uy, float uz) {
        try {
            if (!(particle instanceof net.minecraft.client.particle.TextureSheetParticle) || f_sprite == null) {
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

        if (particleQueueField == null) {
            for (String name : new String[] { "particles", "f_107289_", "f_107347_", "field_78879_a" }) {
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
            LOGGER.error("[Phantasia] particle queue field not found — PhantasiaSpriteMarker will not work");
        else
            LOGGER.info("[Phantasia] particle queue field: {}", particleQueueField.getName());
    }
}
