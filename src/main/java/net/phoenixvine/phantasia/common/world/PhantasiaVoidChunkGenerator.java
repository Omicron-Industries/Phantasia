package net.phoenixvine.phantasia.common.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * PhantasiaVoidChunkGenerator
 *
 * Produces completely empty chunks with no terrain, structures, features, or
 * surface rules. Used for the Phantasia scene dimension where all blocks are
 * placed explicitly by PhantasiaSceneScreen.loadPattern().
 *
 * ── Registration ─────────────────────────────────────────────────────────────
 * Register this generator under the codec key "phantasia:void" in your
 * DeferredRegister<Codec<? extends ChunkGenerator>> (CHUNK_GENERATORS registry).
 *
 * In datapacks / dimension JSON:
 *   "generator": { "type": "phantasia:void" }
 *
 * ── Biome ─────────────────────────────────────────────────────────────────────
 * Uses a FixedBiomeSource locked to minecraft:the_void so no vanilla surface
 * rules, sky colour, fog, or ambient sound apply.
 */
public class PhantasiaVoidChunkGenerator extends ChunkGenerator {

    public static final ResourceLocation CODEC_KEY =
            new ResourceLocation("phantasia", "void");

    // Codec takes no parameters — the generator is completely stateless.
    public static final Codec<PhantasiaVoidChunkGenerator> CODEC =
            RecordCodecBuilder.create(instance -> instance
                    .group(net.minecraft.resources.RegistryOps.retrieveElement(Biomes.THE_VOID))
                    .apply(instance, PhantasiaVoidChunkGenerator::new));

    public PhantasiaVoidChunkGenerator(Holder<Biome> voidBiome) {
        super(new FixedBiomeSource(voidBiome));
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    // ── Generation — all no-ops ───────────────────────────────────────────────

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            Executor executor, Blender blender, RandomState randomState,
            StructureManager structureManager, ChunkAccess chunk) {
        // No terrain noise — all chunks are empty. Pattern blocks are placed
        // by PhantasiaSceneScreen via setBlock() after chunk load.
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager,
                             RandomState randomState, ChunkAccess chunk) {
        // No surface — void.
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState,
                             BiomeManager biomeManager, StructureManager structureManager,
                             ChunkAccess chunk, GenerationStep.Carving step) {
        // No carvers.
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // No mob spawning.
    }

    @Override
    public void applyBiomeDecoration(net.minecraft.world.level.WorldGenLevel level, net.minecraft.world.level.chunk.ChunkAccess chunk,
                                     net.minecraft.world.level.StructureManager structureManager) {
        // No features or decorations.
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        info.add("Phantasia Void Dimension");
    }

    // ── Height queries ────────────────────────────────────────────────────────

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type,
                             LevelHeightAccessor level, RandomState randomState) {
        return level.getMinBuildHeight();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level,
                                     RandomState randomState) {
        // Empty column — air all the way down.
        return new NoiseColumn(level.getMinBuildHeight(), new BlockState[0]);
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return -63; // no sea
    }

    @Override
    public int getMinY() {
        return 0;
    }


}
