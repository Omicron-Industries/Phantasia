package net.phoenixvine.phantasia.client.render;

import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

@OnlyIn(Dist.CLIENT)
public class PhantasiaTrackedDummyWorld extends TrackedDummyWorld {

    // ── Scene entity list ──────────────────────────────────────────────────────

    private final List<Entity> sceneEntities = new ArrayList<>();

    public void addSceneEntity(Entity e) { sceneEntities.add(e); }
    public void clearSceneEntities()     { sceneEntities.clear(); }

    /** Returns scene-local entities so PhantasiaWorldRenderer can render them. */
    @Override
    public List<Entity> getAllEntities() {
        return Collections.unmodifiableList(sceneEntities);
    }

    // ── Chunk source isolation ────────────────────────────────────────────────

    /**
     * Returns DummyWorld's own isolated chunk source instead of proxying to
     * the real ClientLevel's chunk source.
     */
    @Override
    public net.minecraft.world.level.chunk.ChunkSource getChunkSource() {
        return super.getChunkSource();
    }

    @Override
    public List<VoxelShape> getBlockCollisions(@Nullable Entity entity, AABB aabb) {
        // FIX: Vanilla particles always pass null for the entity.
        // By returning an empty list here, particles lose their collision
        // geometry and will fly freely without freezing on invisible blocks.
        if (entity == null) {
            return java.util.Collections.emptyList();
        }

        List<VoxelShape> collisions = new ArrayList<>();

        int minX = net.minecraft.util.Mth.floor(aabb.minX) - 1;
        int maxX = net.minecraft.util.Mth.floor(aabb.maxX) + 1;
        int minY = net.minecraft.util.Mth.floor(aabb.minY) - 1;
        int maxY = net.minecraft.util.Mth.floor(aabb.maxY) + 1;
        int minZ = net.minecraft.util.Mth.floor(aabb.minZ) - 1;
        int maxZ = net.minecraft.util.Mth.floor(aabb.maxZ) + 1;

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        CollisionContext context = CollisionContext.of(entity);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutablePos.set(x, y, z);
                    BlockState state = this.getBlockState(mutablePos);
                    if (!state.isAir()) {
                        VoxelShape shape = state.getCollisionShape(this, mutablePos, context);
                        if (!shape.isEmpty()) {
                            VoxelShape movedShape = shape.move(x, y, z);
                            if (Shapes.joinIsNotEmpty(movedShape, Shapes.create(aabb),
                                    net.minecraft.world.phys.shapes.BooleanOp.AND)) {
                                collisions.add(movedShape);
                            }
                        }
                    }
                }
            }
        }
        return collisions;
    }

    // ── Particle routing ──────────────────────────────────────────────────────

    /**
     * Routes animateTick particle spawns directly to mc.particleEngine.
     */
    @Override
    public void addParticle(ParticleOptions particleData,
                            double x, double y, double z,
                            double xSpeed, double ySpeed, double zSpeed) {
        // Route to the dedicated PhantasiaParticleEngine rather than mc.particleEngine.
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
