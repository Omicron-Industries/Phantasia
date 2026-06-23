package net.phoenixvine.phantasia.client.render;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;

import javax.annotation.Nullable;

public class PhantasiaDummyChunkSource extends ChunkSource {

    private final PhantasiaDummyWorld world;

    public PhantasiaDummyChunkSource(PhantasiaDummyWorld world) {
        this.world = world;
    }

    @Override
    @Nullable
    public ChunkAccess getChunk(int x, int z, ChunkStatus requiredStatus, boolean load) {
        return null;
    }

    @Override
    @Nullable
    public LightChunk getChunkForLighting(int x, int z) {
        return null;
    }

    @Override
    public BlockGetter getLevel() {
        return world;
    }

    @Override
    public void tick(java.util.function.BooleanSupplier hasTimeLeft, boolean tickChunks) {}

    @Override
    public String gatherStats() {
        return "PhantasiaDummyChunkSource";
    }

    @Override
    public int getLoadedChunksCount() {
        return 0;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return world.getLightEngine();
    }
}
