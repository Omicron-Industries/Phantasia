package net.phoenixvine.phantasia.common.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Maps every MultiblockMachineDefinition to a deterministic, stable XZ slot in the
 * Phantasia scene dimension. Slots never change between sessions — same machine always
 * gets the same origin so saves from one session are valid in the next.
 *
 * Layout: 256×256 grid of 512×512 columns. Patterns are placed at Y=50 within their
 * column. Total addressable space: 131072×131072 blocks, plenty for any modpack.
 *
 * Hash stability: uses only the registry ID string, which is fixed for a given mod
 * version. Changing a machine's ID (via renaming, reregistration) will produce a new
 * slot and trigger a cold-load for that machine.
 *
 * ── Rendering vs. persistence ─────────────────────────────────────────────────
 * The dimension slot (from {@link #originFor}) is only used for persistence — it is
 * written to the Phantasia scene dimension's region files so that warm-start loads
 * can skip the expensive shape.getBlocks() iteration.
 *
 * For rendering, {@link #renderOrigin} is always returned as a small fixed position
 * near (8, 50, 8). SHARED_LEVEL (the TrackedDummyWorld) only ever holds one machine's
 * blocks at a time, so all machines can share the same local render origin. This keeps
 * all baked VBO vertex coordinates small, preserving the float32 precision that GT's
 * 0.001-block machine overlay offset requires to avoid z-fighting.
 */
public final class PhantasiaSlotAllocator {

    private PhantasiaSlotAllocator() {}

    public static final int SLOT_WIDTH = 512;
    public static final int GRID_SIDE  = 256;
    public static final int ORIGIN_Y   = 50;

    /**
     * Fixed render origin used by SHARED_LEVEL (the TrackedDummyWorld).
     * All machines share this local origin for rendering — only one machine is
     * ever loaded into SHARED_LEVEL at a time, so there is no conflict.
     * Keeping this near (0,0,0) ensures baked vertex coordinates are small floats.
     */
    public static final BlockPos RENDER_ORIGIN = new BlockPos(8, ORIGIN_Y, 8);

    /**
     * Returns the world-space origin for the given machine's pattern slot in the
     * Phantasia scene dimension. Used exclusively for dimension persistence (warm-start
     * chunk existence checks and region file writes). Do NOT use this for SHARED_LEVEL
     * block placement — use {@link #RENDER_ORIGIN} instead.
     */
    public static BlockPos originFor(ResourceLocation machineId) {
        // Positive-only hash so slot indices are always in [0, GRID_SIDE*GRID_SIDE).
        int hash = machineId.toString().hashCode() & 0x7FFF_FFFF;
        int slotX = hash % GRID_SIDE;
        int slotZ = (hash / GRID_SIDE) % GRID_SIDE;
        return new BlockPos(slotX * SLOT_WIDTH + 8, ORIGIN_Y, slotZ * SLOT_WIDTH + 8);
    }
}