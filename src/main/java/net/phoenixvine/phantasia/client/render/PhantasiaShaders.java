package net.phoenixvine.phantasia.client.render;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@OnlyIn(Dist.CLIENT)
// REMOVED: @Mod.EventBusSubscriber annotation completely gone so Forge doesn't crawl it
public final class PhantasiaShaders {

    private PhantasiaShaders() {}

    @Nullable
    public static volatile ShaderInstance PHANTASIA_BLOCK = null;

    @Nullable
    private static volatile ResourceProvider CACHED_PROVIDER = null;

    private static final AtomicReference<RecompileRequest> PENDING = new AtomicReference<>(null);

    // REMOVED: public static void onRegisterShaders(RegisterShadersEvent event)

    // ── Safe Runtime Entry Point ──────────────────────────────────────────────
    public static void safeInit(ResourceProvider resourceProvider) {
        // Cache the stable context manually
        CACHED_PROVIDER = resourceProvider;

        // Force a default initialization compile safely outside vanilla's early asset registration hook loops
        recompileForPattern(0, false);
    }

    // ── Public API ────────────────────────────────────────────────────────────
    public static void recompileForPattern(int blockCount, boolean useSSBO) {
        ResourceProvider provider = CACHED_PROVIDER;
        if (provider == null) {
            return;
        }
        PENDING.set(new RecompileRequest(blockCount, useSSBO, provider));

        if (RenderSystem.isOnRenderThread()) {
            flushPending();
        } else {
            RenderSystem.recordRenderCall(PhantasiaShaders::flushPending);
        }
    }

    public static void invalidate() {
        ShaderInstance old = PHANTASIA_BLOCK;
        PHANTASIA_BLOCK = null;
        if (old != null) {
            RenderSystem.recordRenderCall(old::close);
        }
    }

    public static void flushPending() {
        RecompileRequest req = PENDING.getAndSet(null);
        if (req == null) return;

        int words = (req.blockCount + 31) / 32;
        String define = req.useSSBO
                ? "#define PHANTASIA_LARGE_MACHINE 1\n#define MAX_BLOCKS_DIV32 " + words + "\n"
                : "#define MAX_BLOCKS_DIV32 " + words + "\n";

        try {
            ResourceProvider injecting = injectDefine(req.provider, define);

            ShaderInstance next = new ShaderInstance(
                    injecting,
                    new ResourceLocation("phantasia", "phantasia_block"),
                    DefaultVertexFormat.BLOCK
            );

            ShaderInstance old = PHANTASIA_BLOCK;
            PHANTASIA_BLOCK = next;
            if (old != null) old.close();
        } catch (Exception e) {
            System.err.println("[Phantasia] phantasia_block shader compile failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ── Define-injecting ResourceProvider ─────────────────────────────────────
    private static ResourceProvider injectDefine(ResourceProvider base, String define) {
        return location -> {
            ResourceLocation targetLocation = location;
            String path = location.getPath();

            if (location.getNamespace().equals("phantasia") && !path.contains("phantasia_block")) {
                targetLocation = new ResourceLocation("minecraft", path);
            }

            Optional<Resource> resource = base.getResource(targetLocation);
            if (resource.isEmpty()) return resource;

            boolean ours = path.contains("phantasia_block")
                    && (path.endsWith(".vsh") || path.endsWith(".fsh"));
            if (!ours) return resource;

            try {
                byte[] src = resource.get().open().readAllBytes();
                String text = new String(src, StandardCharsets.UTF_8);

                int nl = text.indexOf('\n');
                String patched = nl >= 0
                        ? text.substring(0, nl + 1) + define + text.substring(nl + 1)
                        : define + text;

                byte[] patchedBytes = patched.getBytes(StandardCharsets.UTF_8);
                Resource inner = resource.get();

                return Optional.of(new Resource(
                        inner.source(),
                        () -> new ByteArrayInputStream(patchedBytes),
                        inner::metadata
                ));
            } catch (IOException e) {
                return resource;
            }
        };
    }

    private record RecompileRequest(int blockCount, boolean useSSBO, ResourceProvider provider) {}
}