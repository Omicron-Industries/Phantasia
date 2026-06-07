package net.phoenixvine.phantasia.common.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/**
 * PhantasiaSlotVersions
 *
 * Persists shape-hash and script-hash stamps per machine so that changed patterns
 * are detected and their dimension slots re-placed on the next cold open.
 *
 * ── Warm-start gate ──────────────────────────────────────────────────────────
 * isValid() now checks THREE conditions, all of which must be true:
 *
 *   1. The hash stamp file matches (shape and script haven't changed).
 *   2. The Phantasia dimension chunk at the slot origin is actually saved
 *      on disk (PhantasiaDimension.isChunkSaved). Handles first-launch, world
 *      copy, or manually deleted region files.
 *   3. The dimension ServerLevel is available (integrated server is running).
 *      If not (e.g. LAN client, dedicated client-only install), always cold.
 *
 * ── File location ─────────────────────────────────────────────────────────────
 * <gamedir>/phantasia/slot_versions.dat
 * Written atomically via a .tmp file to avoid corruption on crash.
 */
public final class PhantasiaSlotVersions {

    private PhantasiaSlotVersions() {}

    private static final Path VERSIONS_FILE = FMLPaths.GAMEDIR.get()
            .resolve("phantasia/slot_versions.dat");

    private static CompoundTag data = null;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns true if a warm load is valid for the given machine:
     *  - Hash stamps match (shape + script unchanged since last cold load).
     *  - The Phantasia dimension chunk for this slot exists on disk.
     *
     * @param machineId  registry ID of the machine definition
     * @param shapeHash  result of hashShape() for the current shape
     * @param scriptHash result of hashScript() for the current script
     * @param origin     world-space slot origin from PhantasiaSlotAllocator
     */
    public static boolean isValid(ResourceLocation machineId, int shapeHash, int scriptHash,
                                  net.minecraft.core.BlockPos origin) {
        // Hash check first — cheapest.
        ensureLoaded();
        String key = machineId.toString();
        if (!data.contains(key)) return false;
        CompoundTag entry = data.getCompound(key);
        if (entry.getInt("shapeHash") != shapeHash) return false;
        if (entry.getInt("scriptHash") != scriptHash) return false;

        // Dimension chunk check — requires integrated server to be running.
        // On a dedicated server client, PhantasiaDimension.getServerLevel() is null,
        // so we conservatively return false (cold load).
        if (!PhantasiaDimension.isChunkSaved(origin)) return false;

        return true;
    }

    /**
     * Records hashes after a successful cold placement. Subsequent opens with the
     * same shape and script hashes will take the warm path (if chunk is saved).
     */
    public static void put(ResourceLocation machineId, int shapeHash, int scriptHash) {
        ensureLoaded();
        CompoundTag entry = new CompoundTag();
        entry.putInt("shapeHash", shapeHash);
        entry.putInt("scriptHash", scriptHash);
        data.put(machineId.toString(), entry);
        save();
    }

    /** Forces cold load on next open for this machine. */
    public static void invalidate(ResourceLocation machineId) {
        ensureLoaded();
        data.remove(machineId.toString());
        save();
    }

    /** Forces cold load on next open for all machines. */
    public static void invalidateAll() {
        data = new CompoundTag();
        save();
    }

    // ── Hash helpers ──────────────────────────────────────────────────────────

    /**
     * Stable shape hash from BlockInfo[][][]. Hashes the full block state string
     * (including property values) for every cell, so geometry-changing state
     * changes (facing, ACTIVE=true defaults, etc.) also invalidate the slot.
     */
    public static int hashShape(com.lowdragmc.lowdraglib.utils.BlockInfo[][][] blocks) {
        if (blocks == null) return 0;
        String[][][] keys = new String[blocks.length][][];
        for (int x = 0; x < blocks.length; x++) {
            keys[x] = new String[blocks[x].length][];
            for (int y = 0; y < blocks[x].length; y++) {
                keys[x][y] = new String[blocks[x][y].length];
                for (int z = 0; z < blocks[x][y].length; z++) {
                    var bi = blocks[x][y][z];
                    keys[x][y][z] = bi == null || bi.getBlockState() == null
                            ? "" : bi.getBlockState().toString();
                }
            }
        }
        return Arrays.deepHashCode(keys);
    }

    /** Script hash from the JSON string. Detects any script edit between sessions. */
    public static int hashScript(net.phoenixvine.phantasia.common.PhantasiaScriptData scriptData) {
        if (scriptData == null) return 0;
        return scriptData.toJson().hashCode();
    }

    // ── I/O ───────────────────────────────────────────────────────────────────

    private static void ensureLoaded() {
        if (data != null) return;
        if (Files.exists(VERSIONS_FILE)) {
            try {
                data = NbtIo.read(VERSIONS_FILE.toFile());
            } catch (IOException e) {
                System.err.println("[Phantasia] Could not read slot_versions.dat: " + e.getMessage());
            }
        }
        if (data == null) data = new CompoundTag();
    }

    private static void save() {
        try {
            Files.createDirectories(VERSIONS_FILE.getParent());
            Path tmp = VERSIONS_FILE.resolveSibling("slot_versions.dat.tmp");
            NbtIo.write(data, tmp.toFile());
            Files.move(tmp, VERSIONS_FILE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("[Phantasia] Could not save slot_versions.dat: " + e.getMessage());
        }
    }
}
