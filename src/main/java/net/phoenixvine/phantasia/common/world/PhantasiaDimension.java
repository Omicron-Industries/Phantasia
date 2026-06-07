package net.phoenixvine.phantasia.common.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * PhantasiaDimension
 *
 * Declares the ResourceKey for the Phantasia scene dimension and holds the server
 * level reference so the client can ask for warm-start validation.
 *
 * ── How the dimension is created ─────────────────────────────────────────────
 * Forge 1.20.1 dimensions are defined entirely via datapack JSON — there is no
 * Java-side DimensionType registration call at runtime. You declare the dimension
 * by placing JSON files in your mod's resources:
 *
 *   resources/data/phantasia/dimension/scene.json
 *   resources/data/phantasia/dimension_type/scene.json
 *
 * The content for both files is included below as Javadoc so you can copy them
 * to the right paths. The mod registers the codec for the void chunk generator
 * via DeferredRegister (see bottom of this file).
 *
 * ── Warm-start flow ───────────────────────────────────────────────────────────
 * On ServerAboutToStartEvent (fired before world tick, after level creation),
 * we capture the ServerLevel for LEVEL_KEY. PhantasiaSlotVersions uses this
 * to check whether a machine's slot column is already saved in the dimension's
 * chunk storage, which makes the client-side loadPattern() skip the expensive
 * shape.getBlocks() iteration.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * DATAPACK FILE 1: resources/data/phantasia/dimension_type/scene.json
 * ─────────────────────────────────────────────────────────────────────────────
 * {
 *   "ultrawarm": false,
 *   "natural": false,
 *   "coordinate_scale": 1.0,
 *   "has_skylight": false,
 *   "has_ceiling": false,
 *   "ambient_light": 1.0,
 *   "monster_spawn_block_light_limit": 0,
 *   "monster_spawn_light_level": {
 *     "type": "minecraft:uniform",
 *     "value": { "min_inclusive": 0, "max_inclusive": 7 }
 *   },
 *   "piglin_safe": true,
 *   "bed_works": false,
 *   "respawn_anchor_works": false,
 *   "has_raids": false,
 *   "logical_height": 384,
 *   "min_y": 0,
 *   "height": 384,
 *   "infiniburn": "#minecraft:infiniburn_overworld",
 *   "effects": "minecraft:the_end"
 * }
 *
 * ambient_light=1.0 → full-bright (no skylight needed, matches Phantasia's BER pass).
 * effects=the_end   → no sky rendering, no star/moon/sun, no fog gradient.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * DATAPACK FILE 2: resources/data/phantasia/dimension/scene.json
 * ─────────────────────────────────────────────────────────────────────────────
 * {
 *   "type": "phantasia:scene",
 *   "generator": {
 *     "type": "phantasia:void"
 *   }
 * }
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * CHUNK GENERATOR CODEC REGISTRATION (add to your main mod class or a dedicated
 * Registrar class, called during mod construction):
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *   private static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GEN_CODECS =
 *       DeferredRegister.create(Registries.CHUNK_GENERATOR, "phantasia");
 *
 *   public static final RegistryObject<Codec<PhantasiaVoidChunkGenerator>> VOID_GENERATOR_CODEC =
 *       CHUNK_GEN_CODECS.register("void", () -> PhantasiaVoidChunkGenerator.CODEC);
 *
 *   // In mod constructor:
 *   CHUNK_GEN_CODECS.register(modEventBus);
 */
@Mod.EventBusSubscriber(modid = "phantasia", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PhantasiaDimension {

    private PhantasiaDimension() {}

    /** ResourceKey for the Phantasia scene level. */
    public static final ResourceKey<Level> LEVEL_KEY = ResourceKey.create(
            Registries.DIMENSION,
            new ResourceLocation("phantasia", "scene"));

    /** ResourceKey for the dimension type. */
    public static final ResourceKey<DimensionType> DIMENSION_TYPE_KEY = ResourceKey.create(
            Registries.DIMENSION_TYPE,
            new ResourceLocation("phantasia", "scene"));

    /** ResourceKey for the level stem (used in LevelStem registry). */
    public static final ResourceKey<LevelStem> LEVEL_STEM_KEY = ResourceKey.create(
            Registries.LEVEL_STEM,
            new ResourceLocation("phantasia", "scene"));

    /**
     * Cached server level for the Phantasia scene dimension.
     * Set on ServerAboutToStartEvent, cleared on server stop.
     * Null on the client — client-side callers should use the server-level
     * only for chunk-existence checks via the integrated server's level map.
     */
    @org.jetbrains.annotations.Nullable
    private static net.minecraft.server.level.ServerLevel serverLevel = null;

    @org.jetbrains.annotations.Nullable
    public static net.minecraft.server.level.ServerLevel getServerLevel() {
        return serverLevel;
    }

    // ── Server lifecycle ──────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        // Level map is populated before this event fires.
        serverLevel = event.getServer().getLevel(LEVEL_KEY);
        if (serverLevel == null) {
            // Dimension not loaded (e.g. first launch before the datapack JSON is
            // recognised). This is non-fatal — warm-start simply won't work until
            // the dimension exists.
            net.phoenixvine.phantasia.Phantasia.LOGGER.warn(
                    "[Phantasia] Scene dimension '{}' not found — cold loads will be used. " +
                    "Ensure dimension JSON is correctly placed in resources/data/phantasia/dimension/scene.json",
                    LEVEL_KEY.location());
        } else {
            net.phoenixvine.phantasia.Phantasia.LOGGER.info(
                    "[Phantasia] Scene dimension '{}' loaded ({})",
                    LEVEL_KEY.location(),
                    serverLevel.dimensionTypeId().location());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        serverLevel = null;
    }

    // ── Warm-start chunk existence check ─────────────────────────────────────

    /**
     * Returns true if the chunk column at the given world-space block position
     * is already saved in the Phantasia scene dimension's region files.
     *
     * Used by PhantasiaSceneScreen.loadPattern() to decide whether to take the
     * warm path (blocks already placed) or the cold path (full rebuild).
     *
     * Must be called from the server thread or the integrated server's main
     * thread (which is the same as the client thread in singleplayer).
     */
    public static boolean isChunkSaved(net.minecraft.core.BlockPos pos) {
        net.minecraft.server.level.ServerLevel sl = serverLevel;
        if (sl == null) return false;
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        try {
            // 1. Check if the chunk is cached and fully active in the chunk source
            if (sl.getChunkSource().getChunkNow(cx, cz) != null) {
                return true;
            }

            // 2. FIX: Check if the chunk exists in the server level's memory cache.
            // This tells us if the chunk's data structure (including its POIs) is warm
            // without executing a blocking disk read.
            return sl.getChunkSource().hasChunk(cx, cz);

        } catch (Exception ignored) {
            // If we can't query safely (e.g. off-thread), conservatively return false
            // and take the cold path.
            return false;
        }
    }

    /**
     * Forces a chunk to be loaded (and saved if dirty) in the Phantasia dimension.
     * Call this after cold-placing pattern blocks so the dimension persists to disk.
     *
     * The chunk will be ticked, serialised, and written to the region file on the
     * next server auto-save or on world close.
     */
    public static void forceChunkLoad(net.minecraft.core.BlockPos pos) {
        net.minecraft.server.level.ServerLevel sl = serverLevel;
        if (sl == null) return;
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        try {
            // requestChunk returns the chunk, generating it if needed.
            // Adding a ticket keeps it loaded long enough to be dirtied and flushed.
            sl.getChunkSource().addRegionTicket(
                    net.minecraft.server.level.TicketType.FORCED,
                    new net.minecraft.world.level.ChunkPos(cx, cz),
                    2,   // radius 2 — loads the chunk and its neighbours
                    new net.minecraft.world.level.ChunkPos(cx, cz));
        } catch (Exception e) {
            net.phoenixvine.phantasia.Phantasia.LOGGER.warn(
                    "[Phantasia] forceChunkLoad failed at {}: {}", pos, e.getMessage());
        }
    }
}
