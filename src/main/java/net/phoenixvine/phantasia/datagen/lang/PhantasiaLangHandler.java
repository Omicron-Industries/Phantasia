package net.phoenixvine.phantasia.datagen.lang;

import com.tterrag.registrate.providers.RegistrateLangProvider;

public class PhantasiaLangHandler {

    public static void init(RegistrateLangProvider provider) {
        // Keybinds
        provider.add("key.categories.phantasia", "Phantasia");
        provider.add("key.phantasia.phantasia_menu", "Open Phantasia Menu");

        // --- Screen: Block Inspector ---
        provider.add("screen.phantasia.block_inspector.title", "Block Inspector");
        provider.add("screen.phantasia.block_inspector.header", "TECHNICAL BLOCK INSPECTOR");
        provider.add("screen.phantasia.block_inspector.designation", "DESIGNATION");
        provider.add("screen.phantasia.block_inspector.local_pos", "LOCAL POS");
        provider.add("screen.phantasia.block_inspector.specs_utility", "SPECIFICATIONS & UTILITY");
        provider.add("screen.phantasia.block_inspector.properties", "BLOCKSTATE PROPERTIES");
        provider.add("screen.phantasia.block_inspector.btn_emi", "EMI RECIPES");
        provider.add("screen.phantasia.block_inspector.btn_close", "CLOSE DATA");

        // Roles
        provider.add("role.phantasia.standard", "Standard Component");
        provider.add("role.phantasia.controller", "MULTIBLOCK CONTROLLER");
        provider.add("role.phantasia.lamp", "INDICATOR LAMP");
        provider.add("role.phantasia.tesla", "TESLA ENERGY STORAGE");

        // Technical Specs Labels
        provider.add("spec.phantasia.tech_specs", "TECHNICAL SPECIFICATIONS:");
        provider.add("spec.phantasia.max_heat", " - Max Heat: %s");
        provider.add("spec.phantasia.material", " - Material: %s");
        provider.add("spec.phantasia.energy_discount", " - Energy Discount: %s");

        // Phoenix Core Data
        provider.add("spec.phantasia.core_analysis", "CORE ANALYSIS:");
        provider.add("spec.phantasia.base_heat", " • Base Heat: %s");
        provider.add("spec.phantasia.neutron_bias", " • Neutron Bias: %s");
        provider.add("spec.phantasia.cycle", " • Cycle: %s");

        provider.add("spec.phantasia.cooling_data", "COOLING DATA:");
        provider.add("spec.phantasia.cooling_power", " • Cooling Power: %s");
        provider.add("spec.phantasia.consumption", " • Consumption: %s");

        provider.add("spec.phantasia.moderation_stats", "MODERATION STATS:");
        provider.add("spec.phantasia.energy_boost", " • Energy Boost: %s");
        provider.add("spec.phantasia.fuel_discount", " • Fuel Discount: %s");

        provider.add("spec.phantasia.transformation_data", "TRANSFORMATION DATA:");
        provider.add("spec.phantasia.target_cycle", " • Target Cycle: %s");
        provider.add("spec.phantasia.primary_byproducts", " • Primary Byproducts:");
        provider.add("screen.phantasia.material_cost.title", "Material Cost");
        provider.add("screen.phantasia.footprint.title", "Footprint");
        provider.add("screen.phantasia.block_filter.title", "Block Filter");
        provider.add("tooltip.phantasia.hold_to_phantasize", "§6§l» §b[%s] §7Hold to Phantasize");
        provider.add("ui.phantasia.color_code_display", "§%s (§%s)");
        provider.add("screen.phantasia.variants.title", "Variants");
        provider.add("screen.phantasia.hide_pos.title", "Hide Positions");
        provider.add("screen.phantasia.hide_pos.hint", "x, y, z");
        provider.add("screen.phantasia.scene_selection.title", "Phantasia");
        provider.add("screen.phantasia.scene_selection.search_box", "Search...");
        provider.add("screen.phantasia.scene_selection.search_hint", "Search machines...");
    }

    public static void multiLang(RegistrateLangProvider provider, String key, String... values) {
        for (var i = 0; i < values.length; i++) {
            provider.add(getSubKey(key, i), values[i]);
        }
    }

    protected static void multilineLang(RegistrateLangProvider provider, String key, String multiline) {
        var lines = multiline.split("\n");
        multiLang(provider, key, lines);
    }

    protected static String getSubKey(String key, int index) {
        return key + "." + index;
    }
}