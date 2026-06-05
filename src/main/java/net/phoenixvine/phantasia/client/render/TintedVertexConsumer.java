package net.phoenixvine.phantasia.client.render;

import net.minecraft.client.renderer.RenderType;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Thin {@link VertexConsumer} wrapper that applies a per-block alpha tint.
 */
public final class TintedVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private RenderType currentRenderType; // Tracks the drawing layer safely

    // Per-fluid-chunk offset (cleared after each block)
    private double offsetX, offsetY, offsetZ;

    // Per-block tint — alpha is the key channel for show/hide transitions
    private float r = 1f, g = 1f, b = 1f, a = 1f;

    public TintedVertexConsumer(VertexConsumer delegate) {
        this.delegate = delegate;
    }

    /**
     * Updates the context with the active RenderType being drawn by the pipeline.
     */
    public void setCurrentRenderType(RenderType renderType) {
        this.currentRenderType = renderType;
    }

    // ── Tint control ──────────────────────────────────────────────────────────

    public void setAlpha(float alpha) {
        this.a = alpha;
    }

    public void setTint(float r, float g, float b, float a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    public void resetTint() {
        r = 1f;
        g = 1f;
        b = 1f;
        a = 1f;
    }

    // ── Fluid offset control ──────────────────────────────────────────────────

    public void addOffset(double ox, double oy, double oz) {
        offsetX += ox;
        offsetY += oy;
        offsetZ += oz;
    }

    public void clearOffset() {
        offsetX = 0;
        offsetY = 0;
        offsetZ = 0;
    }

    // ── VertexConsumer implementation ─────────────────────────────────────────

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        return delegate.vertex(x + offsetX, y + offsetY, z + offsetZ);
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        return delegate.color(
                (int) (red * r),
                (int) (green * g),
                (int) (blue * b),
                (int) (alpha * a));
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        // ─── REMOVED REFLECTION/SPRITE MARKING INTERCEPTIONS ──────────────
        // All tracking blocks are completely axed. Direct straight-line proxy.
        // ──────────────────────────────────────────────────────────────────
        return delegate.uv(u, v);
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        return delegate.overlayCoords(u, v);
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        return delegate.uv2(u, v);
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        return delegate.normal(x, y, z);
    }

    @Override
    public void endVertex() {
        delegate.endVertex();
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {
        delegate.defaultColor(r, g, b, a);
    }

    @Override
    public void unsetDefaultColor() {
        delegate.unsetDefaultColor();
    }
}
