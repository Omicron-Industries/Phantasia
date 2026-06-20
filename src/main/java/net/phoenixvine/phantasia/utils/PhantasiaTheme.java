package net.phoenixvine.phantasia.utils;

import net.minecraft.util.Mth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import net.phoenixvine.phantasia.Phantasia;

public class PhantasiaTheme {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File THEMES_DIR = new File("phantasia/themes");
    private static final File ACTIVE_FILE = new File(THEMES_DIR, "active.txt");
    public static final Map<String, PhantasiaTheme> REGISTRY = new LinkedHashMap<>();
    private static String activeThemeName = "COBALT";

    // ── Smart Dynamic Color Property Class ──
    public static class ThemeColor {

        public String hex;
        private transient Integer cachedStatic = null;

        public ThemeColor(String hex) {
            this.hex = hex;
        }

        public int getColor(long offset) {
            if (hex == null) return 0xFFFFFFFF;

            String clean = hex.trim().toUpperCase(Locale.ROOT);
            if (clean.startsWith("#")) {
                clean = clean.substring(1);
            }

            // Keyword Evaluation
            switch (clean) {
                case "NONE":
                case "TRANSPARENT":
                    return 0x00000000;
                case "RAINBOW":
                    return calculateRainbow(offset);
                case "PASTEL_RAINBOW":
                    return calculatePastelRainbow(offset);
                case "GALAXY":
                    return calculateGalaxy(offset);
                case "AURORA":
                    return calculateAurora(offset);
                case "MAGMA":
                    return calculateMagma(offset);
            }

            if (cachedStatic == null) {
                try {
                    cachedStatic = (int) Long.parseUnsignedLong(clean, 16);
                } catch (Exception e) {
                    cachedStatic = 0xFFFFFFFF;
                }
            }
            return cachedStatic;
        }

        public void set(String newHex) {
            this.hex = newHex;
            this.cachedStatic = null;
        }
    }

    // ── Theme Properties ──
    public ThemeColor bg, panel, accent, btn, btnHov, btnAct, text, dim, tlBg, prog, warn, hilight;
    public String baseplateBlock = "minecraft:deepslate_bricks";

    public PhantasiaTheme(String bg, String panel, String accent, String btn, String btnHov, String btnAct,
                          String text, String dim, String tlBg, String prog, String warn, String hilight,
                          String baseplateBlock) {
        this.bg = new ThemeColor(bg);
        this.panel = new ThemeColor(panel);
        this.accent = new ThemeColor(accent);
        this.btn = new ThemeColor(btn);
        this.btnHov = new ThemeColor(btnHov);
        this.btnAct = new ThemeColor(btnAct);
        this.text = new ThemeColor(text);
        this.dim = new ThemeColor(dim);
        this.tlBg = new ThemeColor(tlBg);
        this.prog = new ThemeColor(prog);
        this.warn = new ThemeColor(warn);
        this.hilight = new ThemeColor(hilight);
        this.baseplateBlock = baseplateBlock != null ? baseplateBlock : "minecraft:deepslate_bricks";
    }

    // ── Functional Getters ──
    public int bg() {
        return bg.getColor(0);
    }

    public int panel() {
        return panel.getColor(500);
    }

    public int accent() {
        return accent.getColor(1000);
    }

    public int btn() {
        return btn.getColor(1500);
    }

    public int btnHov() {
        return btnHov.getColor(2000);
    }

    public int btnAct() {
        return btnAct.getColor(2500);
    }

    public int text() {
        return text.getColor(3000);
    }

    public int dim() {
        return dim.getColor(3500);
    }

    public int tlBg() {
        return tlBg.getColor(4000);
    }

    public int prog() {
        return prog.getColor(4500);
    }

    public int warn() {
        return warn.getColor(5000);
    }

    public int hilight() {
        return hilight.getColor(5500);
    }

    /**
     * Resolves the theme's baseplate block ID to a BlockState.
     * Returns {@code null} when the block is air or blank — callers should skip baseplate placement entirely.
     * Falls back to deepslate bricks for unrecognised IDs.
     */
    public static net.minecraft.world.level.block.state.BlockState currentBaseplateBlockState() {
        String id = current().baseplateBlock;
        if (id == null || id.isBlank())
            return net.minecraft.world.level.block.Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        String trimmed = id.trim();
        if (trimmed.equals("minecraft:air") || trimmed.equals("air")) return null;
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(trimmed);
        if (rl != null) {
            net.minecraft.world.level.block.Block block = net.minecraftforge.registries.ForgeRegistries.BLOCKS
                    .getValue(rl);
            if (block != null) {
                net.minecraft.world.level.block.state.BlockState state = block.defaultBlockState();
                if (state.isAir()) return null;
                return state;
            }
        }
        return net.minecraft.world.level.block.Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    }

    public static PhantasiaTheme current() {
        return REGISTRY.getOrDefault(activeThemeName, REGISTRY.get("COBALT"));
    }

    public static void setActive(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        if (REGISTRY.containsKey(upper)) {
            activeThemeName = upper;
            // Persist the player's choice so it survives world restarts
            try {
                if (!THEMES_DIR.exists()) THEMES_DIR.mkdirs();
                try (Writer w = new FileWriter(ACTIVE_FILE)) {
                    w.write(upper);
                }
            } catch (Exception e) {
                Phantasia.LOGGER.error("[Phantasia/Theme] {}", e.getMessage());
            }
        }
    }

    public static String getActiveName() {
        return activeThemeName;
    }

    // Safety filter to prevent modifying or dropping internal core profiles
    public static boolean isBuiltIn(String name) {
        String n = name.toUpperCase(Locale.ROOT);
        return n.equals("COBALT") || n.equals("RAINBOW") || n.equals("AMETHYST") ||
                n.equals("MINECRAFT") || n.equals("CRIMSON") || n.equals("EMERALD") ||
                n.equals("VOID") || n.equals("CYBERPUNK") || n.equals("QUARTZ") ||
                n.equals("LIGHT_RAINBOW");
    }

    // ── Math & Animation Formulas ──
    private static int calculateRainbow(long offset) {
        float hue = (float) ((System.currentTimeMillis() + offset) % 6000L) / 6000F;
        return hsvToRgb(hue, 0.75F, 0.90F);
    }

    private static int calculatePastelRainbow(long offset) {
        float hue = (float) ((System.currentTimeMillis() + offset) % 12000L) / 12000F;
        return hsvToRgb(hue, 0.38F, 0.95F);
    }

    private static int calculateGalaxy(long offset) {
        double time = (System.currentTimeMillis() + offset) / 4000.0;
        float wave = (float) (Math.sin(time) * 0.5 + 0.5);
        return hsvToRgb(0.75F + (wave * 0.14F), 0.85F, 0.90F);
    }

    private static int calculateAurora(long offset) {
        double time = (System.currentTimeMillis() + offset) / 3200.0;
        float wave = (float) (Math.sin(time) * 0.5 + 0.5);
        return hsvToRgb(0.32F + (wave * 0.18F), 0.90F, 0.85F);
    }

    private static int calculateMagma(long offset) {
        double time = (System.currentTimeMillis() + offset) / 5000.0;
        float wave = (float) (Math.sin(time) * 0.5 + 0.5);
        return hsvToRgb(0.0F + (wave * 0.10F), 0.95F, 0.50F + (wave * 0.45F));
    }

    private static int hsvToRgb(float h, float s, float v) {
        int i = (int) (h * 6);
        float f = h * 6 - i;
        float p = v * (1f - s);
        float q = v * (1f - s * f);
        float t = v * (1f - s * (1f - f));
        float r, g, b;
        switch (i % 6) {
            case 0 -> {
                r = v;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = v;
                b = p;
            }
            case 2 -> {
                r = p;
                g = v;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = v;
            }
            case 4 -> {
                r = t;
                g = p;
                b = v;
            }
            default -> {
                r = v;
                g = p;
                b = q;
            }
        }
        return (0xFF << 24) | (Mth.clamp((int) (r * 255), 0, 255) << 16) | (Mth.clamp((int) (g * 255), 0, 255) << 8) |
                Mth.clamp((int) (b * 255), 0, 255);
    }

    // ── Hot Reloading IO Subsystem ──
    public static void loadAllThemes() {
        REGISTRY.clear();

        REGISTRY.put("COBALT", new PhantasiaTheme("FF080810", "EE0C0C1A", "FF4FC3F7", "BB151528", "BB1A2840",
                "BB0D3050", "FFDDDDDD", "FF667788", "FF0F1820", "FF4FC3F7", "FFFFB74D", "FFFFEB3B",
                "minecraft:deepslate_bricks"));
        REGISTRY.put("RAINBOW", new PhantasiaTheme("FF090909", "EE111111", "RAINBOW", "BB1A1A1A", "BB2A2A2A",
                "BB202040", "FFEEEEEE", "FF666666", "FF101010", "RAINBOW", "FFFFB74D", "RAINBOW", "minecraft:glass"));
        REGISTRY.put("AMETHYST", new PhantasiaTheme("FF08060F", "EE100818", "FFB39DDB", "BB1A0A2E", "BB2A1048",
                "BB3D1A6E", "FFE8D5FF", "FF7755AA", "FF0C0514", "FFFFD700", "FFFF9800", "FFFFD700",
                "minecraft:calcite"));
        REGISTRY.put("MINECRAFT", new PhantasiaTheme("FF373737", "FF2D2D2D", "FFFFAA00", "FF5A5A5A", "FF6E6E6E",
                "FF4A4A8A", "FFFFFF", "FFAAAAAA", "FF1E1E1E", "FF55FF55", "FFFF5555", "FFFFFF55",
                "minecraft:stone_bricks"));
        REGISTRY.put("CRIMSON", new PhantasiaTheme("FF120505", "EE1C0A0A", "FFFF5252", "BB2A1010", "BB421414",
                "BB5A1818", "FFFFEAEA", "FF996666", "FF180808", "FFFF5252", "FFFFB74D", "FFFFEB3B",
                "minecraft:nether_bricks"));
        REGISTRY.put("EMERALD", new PhantasiaTheme("FF040A06", "EE08140C", "FF69F0AE", "BB0D2415", "BB143A22",
                "BB1B5230", "FFE0F2F1", "FF669977", "FF060F09", "FF69F0AE", "FFFFB74D", "FFFFEB3B",
                "minecraft:mossy_cobblestone"));
        REGISTRY.put("VOID", new PhantasiaTheme("FF020204", "EE050508", "FFE0E0FF", "BB101018", "BB181826", "BB252538",
                "FFF5F5F5", "FF555566", "FF08080C", "FF80DEEA", "FFFF8A80", "FFFFFF8D", "minecraft:obsidian"));
        REGISTRY.put("CYBERPUNK", new PhantasiaTheme("FF050308", "EE0A0512", "FFFF007F", "BB140520", "BB280A40",
                "BB00E5FF", "FF00E5FF", "FF7A20A0", "FF0A0015", "FFFF007F", "FFFFEA00", "FFFF007F",
                "minecraft:black_concrete"));
        REGISTRY.put("QUARTZ", new PhantasiaTheme("FFEAEAEA", "EEF5F5F5", "FF1976D2", "FFDCDCDC", "FFCECECE",
                "FFB4B4B4", "FF212121", "FF666666", "FFE0E0E0", "FF4CAF50", "FFF57C00", "FFFBC02D",
                "minecraft:quartz_bricks"));
        REGISTRY.put("LIGHT_RAINBOW",
                new PhantasiaTheme("FFEAEAEA", "EEF5F5F5", "PASTEL_RAINBOW", "FFDCDCDC", "FFCECECE", "FFB4B4B4",
                        "FF212121", "FF666666", "FFE0E0E0", "PASTEL_RAINBOW", "FFF57C00", "PASTEL_RAINBOW",
                        "minecraft:white_concrete"));

        if (!THEMES_DIR.exists()) THEMES_DIR.mkdirs();
        File[] files = THEMES_DIR.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                try (Reader r = new FileReader(file)) {
                    PhantasiaTheme loaded = GSON.fromJson(r, PhantasiaTheme.class);
                    if (loaded != null) {
                        if (loaded.baseplateBlock == null) loaded.baseplateBlock = "minecraft:deepslate_bricks";
                        String name = file.getName().substring(0, file.getName().length() - 5).toUpperCase(Locale.ROOT);
                        REGISTRY.put(name, loaded);
                    }
                } catch (Exception e) {
                    Phantasia.LOGGER.error("[Phantasia/Theme] {}", e.getMessage());
                }
            }
        }

        // ── Restore the player's last chosen theme ──
        if (ACTIVE_FILE.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(ACTIVE_FILE))) {
                String saved = br.readLine();
                if (saved != null) {
                    saved = saved.trim().toUpperCase(Locale.ROOT);
                    if (REGISTRY.containsKey(saved)) {
                        activeThemeName = saved;
                    }
                }
            } catch (Exception e) {
                Phantasia.LOGGER.error("[Phantasia/Theme] {}", e.getMessage());
            }
        }
    }

    public static void saveThemeToDisk(String name, PhantasiaTheme theme) {
        REGISTRY.put(name.toUpperCase(Locale.ROOT), theme);
        try {
            if (!THEMES_DIR.exists()) THEMES_DIR.mkdirs();
            File file = new File(THEMES_DIR, name.toLowerCase(Locale.ROOT) + ".json");
            try (Writer w = new FileWriter(file)) {
                GSON.toJson(theme, w);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void deleteThemeFromDisk(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        if (isBuiltIn(upper)) return;
        REGISTRY.remove(upper);
        try {
            File file = new File(THEMES_DIR, name.toLowerCase(Locale.ROOT) + ".json");
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
