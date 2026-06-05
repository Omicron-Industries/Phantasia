package net.phoenixvine.phantasia.client.render;

import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PhantasiaTrackedDummyWorld extends TrackedDummyWorld {

    // ── Chunk source isolation ────────────────────────────────────────────────

    /**
     * Returns DummyWorld's own isolated chunk source instead of proxying to
     * the real ClientLevel's chunk source.
     *
     * TrackedDummyWorld.getChunkSource() returns the real ClientLevel's
     * ChunkSource when proxyWorld is non-null (which it always is — we pass
     * mc.level to the constructor). This makes Embeddium's terrain scanner
     * treat SHARED_LEVEL blocks as real world terrain: it finds the chunks as
     * "loaded", builds static VBOs for them, and renders them permanently as
     * a frozen snapshot — overwriting PhantasiaWorldRenderer's output and
     * causing the initial lag spike (Embeddium rebuilding sections on first load).
     *
     * Returning super.getChunkSource() (DummyWorld's own empty chunk source)
     * makes the dummy world invisible to Embeddium's terrain pipeline entirely.
     * getBlockState() reads directly from renderedBlocks (not through the chunk
     * source) so the VBO bake is unaffected.
     */
    @Override
    public net.minecraft.world.level.chunk.ChunkSource getChunkSource() {
        return super.getChunkSource();
    }

    // ── Particle routing ──────────────────────────────────────────────────────

    /**
     * Routes animateTick particle spawns directly to mc.particleEngine.
     *
     * LDLib DummyWorld.addParticle() cannot construct Particle objects from
     * ParticleOptions — that requires ParticleProvider lookups only present in
     * mc.particleEngine. Routing here ensures particles from animateTick reach
     * the same engine GT BER particles use (via the particleProxyLevel swap in
     * drawTileEntities), so mc.particleEngine.render() draws them each frame.
     *
     * NOTE: TrackedDummyWorld.addFreshEntity(), getAllEntities(), tickWorld(),
     * and getBlockTint() are all already correctly implemented in the superclass
     * and do NOT need to be overridden here.
     * - addFreshEntity() stores entities in this.entities (Int2ObjectArrayMap)
     * - getAllEntities() returns a list from that map
     * - tickWorld() ticks entities and BE tickers each call
     * - getBlockTint() proxies to the real ClientLevel via proxyWorld
     */
    @Override
    public void addParticle(ParticleOptions particleData,
                            double x, double y, double z,
                            double xSpeed, double ySpeed, double zSpeed) {
        // Route to the dedicated PhantasiaParticleEngine rather than mc.particleEngine.
        // Using mc.particleEngine causes two problems:
        // 1. Particles appear at the dummy world's real coordinates in the actual world.
        // 2. Real-world particles bleed into Phantasia's render pass.
        // PhantasiaParticleEngine shares providers with mc.particleEngine so all GT
        // particle types (MufflerParticle etc.) are available.
        net.phoenixvine.phantasia.client.render.PhantasiaParticleEngine.addParticle(
                particleData, x, y, z, xSpeed, ySpeed, zSpeed);
    }

    @Override
    public void addAlwaysVisibleParticle(ParticleOptions particleData,
                                         double x, double y, double z,
                                         double xSpeed, double ySpeed, double zSpeed) {
        addParticle(particleData, x, y, z, xSpeed, ySpeed, zSpeed);
    }

    // ── animateTick with hasTicker guard bypass ───────────────────────────────

    /**
     * Drives one ambient animation tick for the block at {@code pos}.
     *
     * The hasTicker guard bypass: TFG's ActiveParticleBlock with hasTicker=true
     * checks level.getBlockEntity(pos) != null and returns early if so. The
     * dummy world has real BEs registered, so we temporarily remove the BE
     * during the animateTick call and restore it in a finally block.
     */
    public void tickAnimateForPos(BlockPos pos, RandomSource random) {
        BlockState state = getBlockState(pos);
        if (state.isAir()) return;
        if (!this.isClientSide) return;

        // Temporarily hide the BE so hasTicker guards in blocks like TFG's
        // ActiveParticleBlock don't bail early when a BE is present.
        BlockEntity hidden = blockEntities.remove(pos);
        try {
            state.getBlock().animateTick(state, this, pos, random);
        } finally {
            if (hidden != null) blockEntities.put(pos, hidden);
        }
    }
}
