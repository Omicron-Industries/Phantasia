package net.phoenixvine.phantasia.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * PhantasiaVertexConsumer
 *
 * Wraps an existing VertexConsumer (the main geometry buffer) and, in parallel,
 * records one integer block ID per vertex into an internal growable buffer.
 *
 * After baking, call {@link #blockIdBuffer()} to get a ready-to-upload IntBuffer
 * containing exactly one int per vertex (in vertex-emission order). This is
 * uploaded as a second VBO alongside the main geometry VBO and bound at vertex
 * attribute location {@link #ATTRIB_LOC} so the Phantasia block shader can look
 * up the visibility bitmask per fragment.
 *
 * ── Usage pattern ────────────────────────────────────────────────────────────
 *
 *   PhantasiaVertexConsumer pvc = new PhantasiaVertexConsumer(innerBuffer);
 *   pvc.setCurrentBlockId(idForThisBlock);
 *   // ... render block geometry into pvc ...
 *   pvc.setCurrentBlockId(idForNextBlock);
 *   // ...
 *   IntBuffer ids = pvc.blockIdBuffer(); // upload to blockIdVbo
 */
@OnlyIn(Dist.CLIENT)
public final class PhantasiaVertexConsumer implements VertexConsumer {

    /** Vertex attribute location for the block ID in the Phantasia block shader. */
    public static final int ATTRIB_LOC = 5;

    private final VertexConsumer inner;
    private int currentBlockId = 0;

    // Growable list of block IDs, one per vertex.
    // We use a List<Integer> during bake (unknown vertex count) and finalise to
    // a direct IntBuffer at the end. Memory is not a concern here — the bake runs
    // on a background thread and the buffer is discarded after GPU upload.
    private final List<Integer> ids = new ArrayList<>(4096);

    public PhantasiaVertexConsumer(VertexConsumer inner) {
        this.inner = inner;
    }

    // ── Block ID control ──────────────────────────────────────────────────────

    /** Call before emitting each block's geometry. */
    public void setCurrentBlockId(int id) {
        this.currentBlockId = id;
    }

    // ── Build result ──────────────────────────────────────────────────────────

    /**
     * Returns a direct-order IntBuffer containing one block ID per vertex.
     * The buffer is positioned at 0 and ready to pass to glBufferData.
     * Only valid after all geometry has been emitted.
     */
    public IntBuffer blockIdBuffer() {
        IntBuffer buf = ByteBuffer.allocateDirect(ids.size() * 4)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer();
        for (int id : ids) buf.put(id);
        buf.rewind();
        return buf;
    }

    public int vertexCount() {
        return ids.size();
    }

    // ── VertexConsumer delegation ─────────────────────────────────────────────

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        ids.add(currentBlockId);
        return inner.vertex(x, y, z);
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        return inner.color(r, g, b, a);
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        return inner.uv(u, v);
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        return inner.overlayCoords(u, v);
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        return inner.uv2(u, v);
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        return inner.normal(x, y, z);
    }

    @Override
    public void endVertex() {
        inner.endVertex();
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {
        inner.defaultColor(r, g, b, a);
    }

    @Override
    public void unsetDefaultColor() {
        inner.unsetDefaultColor();
    }
}
