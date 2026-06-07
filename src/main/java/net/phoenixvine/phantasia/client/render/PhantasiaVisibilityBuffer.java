package net.phoenixvine.phantasia.client.render;

import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL43;

import java.nio.IntBuffer;
import java.util.Map;
import java.util.Set;

import org.lwjgl.BufferUtils;

/**
 * PhantasiaVisibilityBuffer
 *
 * Manages a GPU buffer (UBO or SSBO) containing a packed bitmask that controls
 * per-block visibility in the Phantasia block shader without any CPU VBO rebake.
 *
 * Each block in the pattern gets an integer ID in [0, blockCount). The bit at
 * position ID in the bitmask is 1 if that block is visible, 0 if hidden.
 * The shader reads this bit and calls discard for hidden fragments.
 *
 * ── UBO vs SSBO ──────────────────────────────────────────────────────────────
 * GL guarantees GL_MAX_UNIFORM_BLOCK_SIZE >= 16 KB. For large machines the
 * bitmask may exceed this. If so, we fall back to an SSBO (GL 4.3+, guaranteed
 * min 128 MB). We detect this at construction time and compile the matching
 * shader variant. Desktop drivers (Nvidia/AMD/Intel Arc) support SSBO on Forge
 * 1.20.1's minimum GL target (4.3), so this is safe in practice.
 *
 * ── Partial update optimisation ──────────────────────────────────────────────
 * setVisible() tracks the dirty range (min/max changed word) and uploads only
 * that range via glBufferSubData. For a 32-block layer step change on a 3M-block
 * Fusion Reactor this is typically 1–4 words (~4–16 bytes) of upload, not 375 KB.
 */
@OnlyIn(Dist.CLIENT)
public final class PhantasiaVisibilityBuffer implements AutoCloseable {

    // GL binding points — must not clash with Forge/LDLib bindings.
    public static final int UBO_BINDING_POINT  = 7;
    public static final int SSBO_BINDING_POINT = 7; // same index, different target

    private final int bufHandle;
    private final boolean useSSBO;
    private final int wordCount;
    private final int[] bits;           // CPU mirror
    private final IntBuffer uploadBuf;  // direct-mapped view for glBufferSubData

    private PhantasiaVisibilityBuffer(int bufHandle, boolean useSSBO, int wordCount) {
        this.bufHandle  = bufHandle;
        this.useSSBO    = useSSBO;
        this.wordCount  = wordCount;
        this.bits       = new int[wordCount];
        this.uploadBuf  = BufferUtils.createIntBuffer(wordCount);
    }

    /**
     * Allocates a visibility buffer sized for {@code blockCount} blocks.
     * Must be called on the render thread (GL context required).
     */
    public static PhantasiaVisibilityBuffer create(int blockCount) {
        int wordCount = (blockCount + 31) / 32;
        int bytes     = wordCount * 4;

        int maxUbo    = GL20.glGetInteger(GL31.GL_MAX_UNIFORM_BLOCK_SIZE);
        boolean ssbo  = bytes > maxUbo;

        int target  = ssbo ? GL43.GL_SHADER_STORAGE_BUFFER : GL31.GL_UNIFORM_BUFFER;
        int handle  = GL15.glGenBuffers();
        GL15.glBindBuffer(target, handle);
        GL15.glBufferData(target, bytes, GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(target, 0);

        return new PhantasiaVisibilityBuffer(handle, ssbo, wordCount);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public boolean isSSBO() { return useSSBO; }
    public int handle()     { return bufHandle; }

    /**
     * Computes the #define string that must be injected into the shader source
     * before compilation. The shader uses MAX_BLOCKS_DIV32 to declare its array size.
     */
    public String shaderDefine(int blockCount) {
        int words = (blockCount + 31) / 32;
        if (useSSBO) {
            return "#define PHANTASIA_LARGE_MACHINE 1\n#define MAX_BLOCKS_DIV32 " + words + "\n";
        } else {
            return "#define MAX_BLOCKS_DIV32 " + words + "\n";
        }
    }

    // ── Visibility update ─────────────────────────────────────────────────────

    /**
     * Updates the GPU bitmask to reflect {@code visible}.
     * Only the changed words are uploaded (partial glBufferSubData).
     * Safe to call every frame; the common case (no change) uploads nothing.
     *
     * @param visible set of visible BlockPos
     * @param posToId mapping from BlockPos to compact integer block ID
     */
    public void setVisible(Set<BlockPos> visible, Map<BlockPos, Integer> posToId) {
        // Rebuild the CPU mirror from scratch.
        // Faster than diffing — the bit-set rebuild is O(visible.size())
        // and visible is typically much smaller than blockCount.
        int minDirtyWord = wordCount;
        int maxDirtyWord = -1;

        // Compute new bits in a scratch array so we can compare against current.
        int[] next = new int[wordCount]; // zero-initialised
        for (BlockPos pos : visible) {
            Integer id = posToId.get(pos);
            if (id == null) continue;
            int w = id >> 5;
            next[w] |= (1 << (id & 31));
        }

        // Find dirty range.
        for (int i = 0; i < wordCount; i++) {
            if (next[i] != bits[i]) {
                bits[i]  = next[i];
                if (i < minDirtyWord) minDirtyWord = i;
                if (i > maxDirtyWord) maxDirtyWord = i;
            }
        }

        if (maxDirtyWord < minDirtyWord) return; // nothing changed

        int target = useSSBO ? GL43.GL_SHADER_STORAGE_BUFFER : GL31.GL_UNIFORM_BUFFER;
        GL15.glBindBuffer(target, bufHandle);

        // Upload only the dirty range.
        int rangeLen = maxDirtyWord - minDirtyWord + 1;
        uploadBuf.clear();
        uploadBuf.limit(rangeLen);
        for (int i = 0; i < rangeLen; i++) uploadBuf.put(bits[minDirtyWord + i]);
        uploadBuf.rewind();
        GL15.glBufferSubData(target, (long) minDirtyWord * 4, uploadBuf);

        GL15.glBindBuffer(target, 0);
    }

    /** Bind this buffer to its fixed binding point. Call before shader.apply(). */
    public void bind() {
        if (useSSBO) {
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, SSBO_BINDING_POINT, bufHandle);
        } else {
            GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, UBO_BINDING_POINT, bufHandle);
        }
    }

    /** Unbind. Call after draw. */
    public void unbind() {
        int target = useSSBO ? GL43.GL_SHADER_STORAGE_BUFFER : GL31.GL_UNIFORM_BUFFER;
        GL30.glBindBufferBase(target, useSSBO ? SSBO_BINDING_POINT : UBO_BINDING_POINT, 0);
    }

    @Override
    public void close() {
        GL15.glDeleteBuffers(bufHandle);
    }
}
