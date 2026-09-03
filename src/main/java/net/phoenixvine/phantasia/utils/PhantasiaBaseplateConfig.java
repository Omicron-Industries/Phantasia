package net.phoenixvine.phantasia.utils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class PhantasiaBaseplateConfig {

    private PhantasiaBaseplateConfig() {}

    private static final String DEFAULT_BLOCK = "minecraft:deepslate_bricks";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("phantasia/baseplate_blocks.json");
    private static final Map<String, String> BLOCKS = new LinkedHashMap<>();
    private static boolean loaded = false;

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (FILE.exists()) {
            try (Reader r = new FileReader(FILE)) {
                Map<String, String> m = GSON.fromJson(r, new TypeToken<Map<String, String>>() {}.getType());
                if (m != null) BLOCKS.putAll(m);
            } catch (Exception e) {
                Phantasia.LOGGER.error("[Phantasia/Baseplate] {}", e.getMessage());
            }
        }
    }

    public static String currentBaseplateBlockId() {
        ensureLoaded();
        return BLOCKS.getOrDefault(PhoenixTheme.getActiveName().toUpperCase(Locale.ROOT), DEFAULT_BLOCK);
    }

    public static void setCurrentBaseplateBlockId(String blockId) {
        ensureLoaded();
        BLOCKS.put(PhoenixTheme.getActiveName().toUpperCase(Locale.ROOT), blockId);
        try {
            if (!FILE.getParentFile().exists()) FILE.getParentFile().mkdirs();
            try (Writer w = new FileWriter(FILE)) {
                GSON.toJson(BLOCKS, w);
            }
        } catch (Exception e) {
            Phantasia.LOGGER.error("[Phantasia/Baseplate] {}", e.getMessage());
        }
    }

    public static BlockState currentBaseplateBlockState() {
        String id = currentBaseplateBlockId();
        if (id == null || id.isBlank()) return Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        String trimmed = id.trim();
        if (trimmed.equals("minecraft:air") || trimmed.equals("air")) return null;
        ResourceLocation rl = ResourceLocation.tryParse(trimmed);
        if (rl != null) {
            Block block = ForgeRegistries.BLOCKS.getValue(rl);
            if (block != null) {
                BlockState state = block.defaultBlockState();
                if (state.isAir()) return null;
                return state;
            }
        }
        return Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    }
}
