package net.phoenixvine.phantasia.client.render;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@OnlyIn(Dist.CLIENT)
public class PhantasiaTrackedDummyWorld extends PhantasiaDummyWorld {

    // ── Block tracking ────────────────────────────────────────────────────────

    private Predicate<BlockPos> renderFilter;
    public final Map<BlockPos, PhantasiaBlockInfo> renderedBlocks = new HashMap<>();
    public final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();
    public final Vector3f minPos = new Vector3f(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
    public final Vector3f maxPos = new Vector3f(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);

    public void clear() {
        renderedBlocks.clear();
        blockEntities.clear();
    }

    public void addBlocks(Map<BlockPos, PhantasiaBlockInfo> blocks) {
        blocks.forEach(this::addBlock);
    }

    public void addBlock(BlockPos pos, PhantasiaBlockInfo info) {
        if (info.getBlockState().getBlock() == Blocks.AIR) return;
        renderedBlocks.put(pos, info);
        blockEntities.remove(pos);
        minPos.x = Math.min(minPos.x, pos.getX());
        minPos.y = Math.min(minPos.y, pos.getY());
        minPos.z = Math.min(minPos.z, pos.getZ());
        maxPos.x = Math.max(maxPos.x, pos.getX());
        maxPos.y = Math.max(maxPos.y, pos.getY());
        maxPos.z = Math.max(maxPos.z, pos.getZ());
    }

    public PhantasiaBlockInfo removeBlock(BlockPos pos) {
        blockEntities.remove(pos);
        return renderedBlocks.remove(pos);
    }

    public void setInnerBlockEntity(@Nonnull BlockEntity be) {
        blockEntities.put(be.getBlockPos(), be);
    }

    @Override
    public void setBlockEntity(@Nonnull BlockEntity be) {
        blockEntities.put(be.getBlockPos(), be);
    }

    @Override
    public boolean setBlock(@Nonnull BlockPos pos, @Nonnull BlockState state, int flags, int recursionLeft) {
        renderedBlocks.put(pos, PhantasiaBlockInfo.fromBlockState(state));
        blockEntities.remove(pos);
        return true;
    }

    @Override
    public BlockEntity getBlockEntity(@Nonnull BlockPos pos) {
        if (renderFilter != null && !renderFilter.test(pos)) return null;
        BlockEntity existing = blockEntities.get(pos);
        if (existing != null) return existing;
        // Build outside the map to avoid recursive computeIfAbsent (Java HashMap does not support re-entrant compute)
        BlockEntity created = renderedBlocks.getOrDefault(pos, PhantasiaBlockInfo.EMPTY).getBlockEntity(this, pos);
        if (created != null) blockEntities.put(pos, created);
        return created;
    }

    @Override
    public BlockState getBlockState(@Nonnull BlockPos pos) {
        if (renderFilter != null && !renderFilter.test(pos)) return Blocks.AIR.defaultBlockState();
        BlockState override = variantOverrides.get(pos);
        return override != null ? override :
                renderedBlocks.getOrDefault(pos, PhantasiaBlockInfo.EMPTY).getBlockState();
    }

    public Vector3f getSize() {
        return new Vector3f(maxPos.x - minPos.x + 1, maxPos.y - minPos.y + 1, maxPos.z - minPos.z + 1);
    }

    public Vector3f getMinPos() {
        return minPos;
    }

    public Vector3f getMaxPos() {
        return maxPos;
    }

    public Map<BlockPos, PhantasiaBlockInfo> getRenderedBlocks() {
        return renderedBlocks;
    }

    public void setRenderFilter(Predicate<BlockPos> filter) {
        this.renderFilter = filter;
    }

    // Post-tick hooks run after all BEs have been ticked.
    // Register here to re-apply manual state (e.g. beacon beam sections) that vanilla ticks reset.
    private final List<Runnable> postTickHooks = new ArrayList<>();

    public void addPostTickHook(Runnable hook) {
        postTickHooks.add(hook);
    }

    public void clearPostTickHooks() {
        postTickHooks.clear();
    }

    // Pre-render hooks run at the start of drawTileEntities, immediately before any BER is called.
    // Use this to force state (e.g. beacon beamSections) onto freshly-created BEs, since a bake
    // may have evicted and recreated the cached BE since the last post-tick hook ran.
    private final List<Runnable> preRenderHooks = new ArrayList<>();

    public void addPreRenderHook(Runnable hook) {
        preRenderHooks.add(hook);
    }

    public void clearPreRenderHooks() {
        preRenderHooks.clear();
    }

    public void runPreRenderHooks() {
        for (Runnable hook : preRenderHooks) {
            try {
                hook.run();
            } catch (Exception e) {
                net.phoenixvine.phantasia.Phantasia.LOGGER.warn("[Phantasia] preRenderHook error: {}", e.getMessage());
            }
        }
    }

    public void tickWorld() {
        for (Map.Entry<BlockPos, PhantasiaBlockInfo> entry : renderedBlocks.entrySet()) {
            BlockState state = entry.getValue().getBlockState();
            BlockEntity be = getBlockEntity(entry.getKey());
            if (be != null && be.getType().isValid(state)) {
                try {
                    @SuppressWarnings("unchecked")
                    BlockEntityTicker<BlockEntity> ticker = (BlockEntityTicker<BlockEntity>) state.getTicker(this,
                            be.getType());
                    if (ticker != null) ticker.tick(this, entry.getKey(), state, be);
                } catch (Exception e) {
                    net.phoenixvine.phantasia.Phantasia.LOGGER.error(
                            "Error ticking dummy world BE at {} type {}", entry.getKey(), be.getType(), e);
                }
            }
        }
        for (Runnable hook : postTickHooks) {
            try {
                hook.run();
            } catch (Exception e) {
                net.phoenixvine.phantasia.Phantasia.LOGGER.warn("[Phantasia] postTickHook error: {}", e.getMessage());
            }
        }
    }

    // ── Scene entity list ──────────────────────────────────────────────────────

    private final List<Entity> sceneEntities = new ArrayList<>();

    public void addSceneEntity(Entity e) {
        sceneEntities.add(e);
    }

    public void clearSceneEntities() {
        sceneEntities.clear();
    }

    public List<Entity> getAllEntities() {
        return Collections.unmodifiableList(sceneEntities);
    }

    // ── Variant state overlay ─────────────────────────────────────────────────

    private final Map<BlockPos, BlockState> variantOverrides = new HashMap<>();

    public void setVariantOverride(BlockPos pos, BlockState state) {
        variantOverrides.put(pos, state);
    }

    public void clearVariantOverrides() {
        variantOverrides.clear();
    }

    // ── Chunk source isolation ────────────────────────────────────────────────

    @Override
    public net.minecraft.world.level.chunk.ChunkSource getChunkSource() {
        return super.getChunkSource();
    }

    @Override
    public List<VoxelShape> getBlockCollisions(@Nullable Entity entity, AABB aabb) {
        if (entity == null) return Collections.emptyList();
        List<VoxelShape> collisions = new ArrayList<>();
        int minX = Mth.floor(aabb.minX) - 1;
        int maxX = Mth.floor(aabb.maxX) + 1;
        int minY = Mth.floor(aabb.minY) - 1;
        int maxY = Mth.floor(aabb.maxY) + 1;
        int minZ = Mth.floor(aabb.minZ) - 1;
        int maxZ = Mth.floor(aabb.maxZ) + 1;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        CollisionContext ctx = CollisionContext.of(entity);
        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
            mutablePos.set(x, y, z);
            BlockState state = getBlockState(mutablePos);
            if (!state.isAir()) {
                VoxelShape shape = state.getCollisionShape(this, mutablePos, ctx);
                if (!shape.isEmpty()) {
                    VoxelShape moved = shape.move(x, y, z);
                    if (Shapes.joinIsNotEmpty(moved, Shapes.create(aabb), BooleanOp.AND)) collisions.add(moved);
                }
            }
        }
        return collisions;
    }

    // ── Particle routing ──────────────────────────────────────────────────────

    @Override
    public void addParticle(ParticleOptions data, double x, double y, double z, double vx, double vy, double vz) {
        PhantasiaParticleEngine.addParticle(data, x, y, z, vx, vy, vz);
    }

    @Override
    public void addAlwaysVisibleParticle(ParticleOptions data, double x, double y, double z, double vx, double vy,
                                         double vz) {
        addParticle(data, x, y, z, vx, vy, vz);
    }

    // ── animateTick ───────────────────────────────────────────────────────────

    public void tickAnimateForPos(BlockPos pos, RandomSource random) {
        BlockState state = getBlockState(pos);
        if (state.isAir() || !this.isClientSide) return;
        BlockEntity hidden = blockEntities.remove(pos);
        try {
            state.getBlock().animateTick(state, this, pos, random);
        } finally {
            if (hidden != null) blockEntities.put(pos, hidden);
        }
    }
}
