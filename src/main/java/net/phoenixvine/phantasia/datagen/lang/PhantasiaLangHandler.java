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
        provider.add("spec.phantasia.base_heat", " \u2022 Base Heat: %s");
        provider.add("spec.phantasia.neutron_bias", " \u2022 Neutron Bias: %s");
        provider.add("spec.phantasia.cycle", " \u2022 Cycle: %s");

        provider.add("spec.phantasia.cooling_data", "COOLING DATA:");
        provider.add("spec.phantasia.cooling_power", " \u2022 Cooling Power: %s");
        provider.add("spec.phantasia.consumption", " \u2022 Consumption: %s");

        provider.add("spec.phantasia.moderation_stats", "MODERATION STATS:");
        provider.add("spec.phantasia.energy_boost", " \u2022 Energy Boost: %s");
        provider.add("spec.phantasia.fuel_discount", " \u2022 Fuel Discount: %s");

        provider.add("spec.phantasia.transformation_data", "TRANSFORMATION DATA:");
        provider.add("spec.phantasia.target_cycle", " \u2022 Target Cycle: %s");
        provider.add("spec.phantasia.primary_byproducts", " \u2022 Primary Byproducts:");
        provider.add("screen.phantasia.material_cost.title", "Material Cost");
        provider.add("screen.phantasia.footprint.title", "Footprint");
        provider.add("screen.phantasia.block_filter.title", "Block Filter");
        provider.add("tooltip.phantasia.hold_to_phantasize", "§6§l» §b[%s§b] §7Hold to Phantasize");
        provider.add("ui.phantasia.color_code_display", "§%s (§%s)");
        provider.add("screen.phantasia.variants.title", "Variants");
        provider.add("screen.phantasia.hide_pos.title", "Hide Positions");
        provider.add("screen.phantasia.hide_pos.hint", "x, y, z");
        provider.add("screen.phantasia.scene_selection.title", "Phantasia");
        provider.add("screen.phantasia.scene_selection.search_box", "Search...");
        provider.add("screen.phantasia.scene_selection.search_hint", "Search machines...");

        // --- Screen: editor ---
        provider.add("screen.phantasia.editor.close_confirm_title", "Unsaved changes — discard and close?");
        provider.add("screen.phantasia.editor.close_confirm_body", "All edits since your last save will be lost.");
        provider.add("screen.phantasia.editor.close_confirm_discard", "✕ Discard & Close");
        provider.add("screen.phantasia.editor.close_confirm_keep", "↩ Keep Editing");

        // --- Screen: scene_create ---
        provider.add("screen.phantasia.scene_create.title", "New Scene");
        provider.add("screen.phantasia.scene_create.label_id", "Scene ID:");
        provider.add("screen.phantasia.scene_create.label_name", "Name:");
        provider.add("screen.phantasia.scene_create.label_icon", "Icon Item:");
        provider.add("screen.phantasia.scene_create.btn_create", "✓ Create");
        provider.add("screen.phantasia.scene_create.btn_cancel", "✕ Cancel");
        provider.add("screen.phantasia.scene_create.hint_id", "namespace:scene_name");
        provider.add("screen.phantasia.scene_create.hint_name", "Display Name");
        provider.add("screen.phantasia.scene_create.hint_icon", "minecraft:chest");
        provider.add("screen.phantasia.scene_create.err_blank_id", "Scene ID cannot be blank.");
        provider.add("screen.phantasia.scene_create.err_no_namespace", "ID must be namespace:name (e.g. mod:my_line)");
        provider.add("screen.phantasia.scene_create.err_duplicate", "A scene with that ID already exists.");

        // --- Screen: guide_editor ---
        provider.add("screen.phantasia.guide_editor.title", "Guide Editor");
        provider.add("screen.phantasia.guide_editor.label_title", "Title:");
        provider.add("screen.phantasia.guide_editor.label_icon", "Icon:");
        provider.add("screen.phantasia.guide_editor.label_item_id", "Item ID:");
        provider.add("screen.phantasia.guide_editor.label_label", "Label:");
        provider.add("screen.phantasia.guide_editor.label_count", "Count:");
        provider.add("screen.phantasia.guide_editor.label_type", "Type:");
        provider.add("screen.phantasia.guide_editor.section_pages", "Pages");
        provider.add("screen.phantasia.guide_editor.btn_add_page", "+ Add");
        provider.add("screen.phantasia.guide_editor.btn_add_item", "+ Item");
        provider.add("screen.phantasia.guide_editor.btn_apply_item", "✓ Apply Item");
        provider.add("screen.phantasia.guide_editor.btn_back", "← Back");
        provider.add("screen.phantasia.guide_editor.no_pages", "No pages yet — add one →");
        provider.add("screen.phantasia.guide_editor.headline_editor_label",
                "Headline Editor (& codes will remain as text):");
        provider.add("screen.phantasia.guide_editor.body_editor_label",
                "Body Multiline Editor (Auto Wrapping Engine Active):");
        provider.add("screen.phantasia.guide_editor.items_config", "Items Configuration");
        provider.add("screen.phantasia.guide_editor.hint_icon_item", "icon item id");
        provider.add("screen.phantasia.guide_editor.hint_item_id", "item id (e.g. gtceu:iron_ingot)");
        provider.add("screen.phantasia.guide_editor.hint_label", "label");

        // --- Screen: hide_pos_editor ---
        provider.add("screen.phantasia.hide_pos_editor.title", "Hide Positions");
        provider.add("screen.phantasia.hide_pos_editor.btn_done", "✓ Done");
        provider.add("screen.phantasia.hide_pos_editor.btn_clear", "✕ Clear All");
        provider.add("screen.phantasia.hide_pos_editor.hint_click", "Left-click to hide block");
        provider.add("screen.phantasia.hide_pos_editor.hint_click_verbose", "Click a block in the viewport to hide it");
        provider.add("screen.phantasia.hide_pos_editor.section_positions", "Hidden Positions");
        provider.add("screen.phantasia.hide_pos_editor.empty", "Nothing hidden yet.");
        provider.add("screen.phantasia.hide_pos_editor.empty_hint", "Click blocks in the viewport");
        provider.add("screen.phantasia.hide_pos_editor.label_add", "Add:");
        provider.add("screen.phantasia.hide_pos_editor.btn_add", "Add");
        provider.add("screen.phantasia.hide_pos_editor.err_invalid_number", "Invalid number.");
        provider.add("screen.phantasia.hide_pos_editor.err_need_xyz", "Need 3 numbers: x, y, z");
        provider.add("screen.phantasia.hide_pos_editor.err_already_hidden", "Already hidden.");
        provider.add("screen.phantasia.hide_pos_editor.err_enter_xyz", "Enter x, y, z first.");
        provider.add("screen.phantasia.hide_pos_editor.hint_xyz", "x, y, z");

        // --- Screen: placement_editor ---
        provider.add("screen.phantasia.placement_editor.title", "Edit Placement");
        provider.add("screen.phantasia.placement_editor.label_offset", "Offset:");
        provider.add("screen.phantasia.placement_editor.label_item", "Item:");
        provider.add("screen.phantasia.placement_editor.label_count", "Count:");
        provider.add("screen.phantasia.placement_editor.label_label", "Label:");
        provider.add("screen.phantasia.placement_editor.label_track", "Track:");
        provider.add("screen.phantasia.placement_editor.label_ticks", "ticks:");
        provider.add("screen.phantasia.placement_editor.label_desc", "Desc:");
        provider.add("screen.phantasia.placement_editor.label_scene", "Scene:");
        provider.add("screen.phantasia.placement_editor.label_machine_id", "Machine ID:");
        provider.add("screen.phantasia.placement_editor.label_x", "X");
        provider.add("screen.phantasia.placement_editor.label_y", "Y");
        provider.add("screen.phantasia.placement_editor.label_z", "Z");
        provider.add("screen.phantasia.placement_editor.btn_apply", "✓ Apply");
        provider.add("screen.phantasia.placement_editor.btn_move", "✓ Move");
        provider.add("screen.phantasia.placement_editor.btn_add_item", "✓ Add Item");
        provider.add("screen.phantasia.placement_editor.btn_apply_close", "✓ Apply & close");
        provider.add("screen.phantasia.placement_editor.hint_machine_id", "gtceu:electric_blast_furnace");
        provider.add("screen.phantasia.placement_editor.hint_item_id", "namespace:item_id");
        provider.add("screen.phantasia.placement_editor.hint_label_auto", "auto");
        provider.add("screen.phantasia.placement_editor.hint_ticks", "20");
        provider.add("screen.phantasia.placement_editor.hint_desc", "Optional description...");
        provider.add("screen.phantasia.placement_editor.hint_scene", "phantasia:scene_id (optional)");

        // --- Screen: scene_mistakes_editor ---
        provider.add("screen.phantasia.scene_mistakes_editor.title", "Layout Mistakes");
        provider.add("screen.phantasia.scene_mistakes_editor.btn_done", "✓ Done");
        provider.add("screen.phantasia.scene_mistakes_editor.label_severity", "Severity:");
        provider.add("screen.phantasia.scene_mistakes_editor.label_id", "ID:");
        provider.add("screen.phantasia.scene_mistakes_editor.label_desc", "Desc:");
        provider.add("screen.phantasia.scene_mistakes_editor.label_placements", "Placements:");
        provider.add("screen.phantasia.scene_mistakes_editor.btn_add", "✓ Add Mistake");
        provider.add("screen.phantasia.scene_mistakes_editor.btn_apply", "✓ Apply");
        provider.add("screen.phantasia.scene_mistakes_editor.btn_new", "+ New Mistake");
        provider.add("screen.phantasia.scene_mistakes_editor.empty", "No layout mistakes defined yet.");
        provider.add("screen.phantasia.scene_mistakes_editor.hint_id", "mistake_id");
        provider.add("screen.phantasia.scene_mistakes_editor.hint_desc", "Describe the mistake...");
        provider.add("screen.phantasia.scene_mistakes_editor.hint_placements", "0, 1, 2...");
        provider.add("screen.phantasia.scene_mistakes_editor.err_blank_id", "ID cannot be blank.");
        provider.add("screen.phantasia.scene_mistakes_editor.err_duplicate_id", "ID already used: ");

        // --- Screen: scene_editor ---
        provider.add("screen.phantasia.scene_editor.title", "Scene Editor");
        provider.add("screen.phantasia.scene_editor.section_placements", "Placements");
        provider.add("screen.phantasia.scene_editor.label_name", "Name:");
        provider.add("screen.phantasia.scene_editor.label_icon", "Icon:");
        provider.add("screen.phantasia.scene_editor.label_show", "Show:");
        provider.add("screen.phantasia.scene_editor.label_global", "Global:");
        provider.add("screen.phantasia.scene_editor.label_layer", "Layer:");
        provider.add("screen.phantasia.scene_editor.label_hidepos", "HidePos:");
        provider.add("screen.phantasia.scene_editor.label_working", "Working:");
        provider.add("screen.phantasia.scene_editor.label_recipe", "Recipe:");
        provider.add("screen.phantasia.scene_editor.label_particles", "Particles:");
        provider.add("screen.phantasia.scene_editor.label_zoom", "Zoom:");
        provider.add("screen.phantasia.scene_editor.label_tick", "Tick:");
        provider.add("screen.phantasia.scene_editor.label_desc", "Desc:");
        provider.add("screen.phantasia.scene_editor.label_total", "Total:");
        provider.add("screen.phantasia.scene_editor.label_step", "STEP");
        provider.add("screen.phantasia.scene_editor.label_add_machine", "+ Add machine:");
        provider.add("screen.phantasia.scene_editor.val_global", "Global");
        provider.add("screen.phantasia.scene_editor.val_over", "over");
        provider.add("screen.phantasia.scene_editor.val_ticks", "ticks");
        provider.add("screen.phantasia.scene_editor.val_override", "◆ override");
        provider.add("screen.phantasia.scene_editor.btn_clear_cam", "✕ Clear");
        provider.add("screen.phantasia.scene_editor.no_cam_override",
                "No camera override on this step — click Capture Cam to add one.");
        provider.add("screen.phantasia.scene_editor.hint_caption", "Caption for this step...");
        provider.add("screen.phantasia.scene_editor.hint_desc", "Guide description (shown in Guide view)...");
        provider.add("screen.phantasia.scene_editor.hint_lerp_ticks", "20");
        provider.add("screen.phantasia.scene_editor.hint_zoom", "auto");
        provider.add("screen.phantasia.scene_editor.hint_hidepos", "x,y,z; ...");
        provider.add("screen.phantasia.scene_editor.hint_recipe", "namespace:recipe_id");
        provider.add("screen.phantasia.scene_editor.hint_particles", "minecraft:flame; ...");
        provider.add("screen.phantasia.scene_editor.hint_machine_id", "gtceu:electric_blast_furnace");

        // --- Screen: script_step_item_editor ---
        provider.add("screen.phantasia.script_step_item_editor.label_count", "Count:");
        provider.add("screen.phantasia.script_step_item_editor.label_label", "Label:");
        provider.add("screen.phantasia.script_step_item_editor.label_track", "Track:");
        provider.add("screen.phantasia.script_step_item_editor.label_ticks", "ticks:");
        provider.add("screen.phantasia.script_step_item_editor.btn_add_item", "✓ Add Item");
        provider.add("screen.phantasia.script_step_item_editor.btn_apply_close", "✓ Apply & close");
        provider.add("screen.phantasia.script_step_item_editor.btn_add_section", "+ Add Item");
        provider.add("screen.phantasia.script_step_item_editor.empty", "No items on this step yet.");
        provider.add("screen.phantasia.script_step_item_editor.hint_item_id", "namespace:item_id");
        provider.add("screen.phantasia.script_step_item_editor.hint_label_auto", "auto");
        provider.add("screen.phantasia.script_step_item_editor.hint_ticks", "20");
        provider.add("screen.phantasia.script_step_item_editor.hint_desc", "Description shown in item popup...");
        provider.add("screen.phantasia.script_step_item_editor.hint_scene", "phantasia:scene_id (optional)");
        provider.add("screen.phantasia.script_step_item_editor.hint_guide_id", "namespace:guide_id (optional)");

        // --- Screen: script_editor ---
        provider.add("screen.phantasia.script_editor.title", "Editor");
        provider.add("screen.phantasia.script_editor.label_colour", "Colour:");
        provider.add("screen.phantasia.script_editor.label_recipe", "Recipe:");
        provider.add("screen.phantasia.script_editor.label_show", "Show:");
        provider.add("screen.phantasia.script_editor.label_tick", "Tick:");
        provider.add("screen.phantasia.script_editor.label_total", "Total:");
        provider.add("screen.phantasia.script_editor.label_step", "STEP");
        provider.add("screen.phantasia.script_editor.label_zoom", "Zoom:");
        provider.add("screen.phantasia.script_editor.label_hideY", "HideY:");
        provider.add("screen.phantasia.script_editor.label_over", "over");
        provider.add("screen.phantasia.script_editor.label_ticks", "ticks");
        provider.add("screen.phantasia.script_editor.label_layer_filter", "Layer");
        provider.add("screen.phantasia.script_editor.label_layer_filter2", "filter");
        provider.add("screen.phantasia.script_editor.label_start_cam", "Start Cam");
        provider.add("screen.phantasia.script_editor.label_target_offset", "Target offset");
        provider.add("screen.phantasia.script_editor.label_parts_filter", "Parts filter");
        provider.add("screen.phantasia.script_editor.label_quick_select", "Quick select:");
        provider.add("screen.phantasia.script_editor.label_custom_expr", "Custom expr:");
        provider.add("screen.phantasia.script_editor.label_global_note", "Global note:");
        provider.add("screen.phantasia.script_editor.btn_start_cam", "⊙ Start Cam");
        provider.add("screen.phantasia.script_editor.btn_clear_cam", "✕ Clear");
        provider.add("screen.phantasia.script_editor.btn_clear_all", "✕  Clear — show all blocks");
        provider.add("screen.phantasia.script_editor.no_cam_override",
                "No camera override on this step — click Capture Cam to add one.");
        provider.add("screen.phantasia.script_editor.hint_hide_layer", "e.g. 1,2");
        provider.add("screen.phantasia.script_editor.hint_recipe", "gtceu:fusion/recipe_name");
        provider.add("screen.phantasia.script_editor.hint_lerp_ticks", "20");
        provider.add("screen.phantasia.script_editor.hint_zoom", "auto");
        provider.add("screen.phantasia.script_editor.hint_range_min", "min");
        provider.add("screen.phantasia.script_editor.hint_range_max", "max");
        provider.add("screen.phantasia.script_editor.hint_parts_expr", "(@coil | @ability(muffler)) & !@block(bronze)");
        provider.add("screen.phantasia.script_editor.hint_duration", "auto");

        // --- Screen: scene_selection ---
        provider.add("screen.phantasia.scene_selection.header", "✶ Phantasia");
        provider.add("screen.phantasia.scene_selection.subtitle", "Select a Multiblock or Scene to Explore");
        provider.add("screen.phantasia.scene_selection.no_results", "No matching machines found");
        provider.add("screen.phantasia.scene_selection.no_script", "No script");
        provider.add("screen.phantasia.scene_selection.btn_new_scene", "New Scene");
        provider.add("screen.phantasia.scene_selection.btn_new_guide", "New Guide");
        provider.add("screen.phantasia.scene_selection.btn_view", "▶ View");
        provider.add("screen.phantasia.scene_selection.btn_read", "Read");
        provider.add("screen.phantasia.scene_selection.btn_back", "← Back");
        provider.add("screen.phantasia.scene_selection.no_guides", "No guides yet.");
        provider.add("screen.phantasia.scene_selection.no_guides_hint", "Click \"+ New Guide\" to create one.");
        provider.add("screen.phantasia.scene_selection.scroll_hint", "▲ ▼  scroll to see more");

        // --- Screen: guide ---
        provider.add("screen.phantasia.guide.label_items", "Items");
        provider.add("screen.phantasia.guide.btn_close", "Close");

        // --- Screen: item_microscene ---
        provider.add("screen.phantasia.item_microscene.no_description", "No description.");
        provider.add("screen.phantasia.item_microscene.btn_back", "← Back");

        // --- Screen: scene ---
        provider.add("screen.phantasia.scene.label_show", "Show:");
        provider.add("screen.phantasia.scene.label_layer", "Layer:");
        provider.add("screen.phantasia.scene.label_build_step", "Build Step:");

        // --- Screen: theme_editor ---
        provider.add("screen.phantasia.theme_editor.title", "Phantasia Theme Workspace");
        provider.add("screen.phantasia.theme_editor.label_theme_id", "Theme ID");
        provider.add("screen.phantasia.theme_editor.btn_save", "Save Theme File");
        provider.add("screen.phantasia.theme_editor.btn_exit", "← Exit Workspace");

        // Static UI Titles & Fallbacks
        provider.add("role.phantasia.heating_coil", "%s HEATING COIL");
        provider.add("role.phantasia.fission_fuel_rod", "FISSION FUEL ROD [T%s]");
        provider.add("role.phantasia.thermal_coolant", "THERMAL COOLANT MODULE");
        provider.add("role.phantasia.neutron_moderator", "NEUTRON MODERATOR");
        provider.add("role.phantasia.breeder_blanket", "BREEDER BLANKET");

        // Tooltip formatting bits
        provider.add("spec.phantasia.unit.hu_per_tick", "%s HU/t");
        provider.add("spec.phantasia.unit.hu_per_tick_negative", "-%s HU/t");
        provider.add("spec.phantasia.unit.mb_per_tick", "%s mB/t");
        provider.add("spec.phantasia.unit.percent_positive", "+%s%%");
        provider.add("spec.phantasia.unit.percent", "%s%%");
        provider.add("spec.phantasia.unit.seconds", "%ss");
        provider.add("spec.phantasia.unit.pellets_cycle", "%s pellets / %ss");
        provider.add("spec.phantasia.unit.weight_suffix", " (W:%s)");

        provider.add("ui.phantasia.raw_value", "%s");
        provider.add("screen.phantasia.script_step_item.title", "Step Items — Step %s");
        provider.add("ui.phantasia.bullet_prefix", "   - ");

        // --- Shared UI buttons ---
        provider.add("ui.phantasia.btn_close", "✕ Close");
        provider.add("ui.phantasia.btn_close_plain", "Close");
        provider.add("ui.phantasia.btn_add", "+ Add");

        // --- Welcome overlay ---
        provider.add("ui.phantasia.welcome.installed", "Phantasia is installed!");
        provider.add("ui.phantasia.welcome.instruction", "Press [P] near a machine, or /phantasia to browse.");
        provider.add("ui.phantasia.welcome.open_guide", "Click here to open Getting Started →");

        // --- Screen: scene_selection (new keys) ---
        provider.add("screen.phantasia.scene_selection.badge_dev", "DEV");
        provider.add("screen.phantasia.scene_selection.section_camera", "Camera");
        provider.add("screen.phantasia.scene_selection.section_display", "Display & Playback");
        provider.add("screen.phantasia.scene_selection.label_display_mode", "Display mode");
        provider.add("screen.phantasia.scene_selection.section_performance", "Performance");
        provider.add("screen.phantasia.scene_selection.label_scene_streaming", "Scene streaming");
        provider.add("screen.phantasia.scene_selection.section_appearance", "Appearance");
        provider.add("screen.phantasia.scene_selection.hint_scroll", "▲ ▼  scroll to see more");

        // --- Screen: guide_editor (new keys) ---
        provider.add("screen.phantasia.guide_editor.section_items", "Items Configuration (%d):");
        provider.add("screen.phantasia.guide_editor.section_tooltip_items", "Tooltip Items (%d):");
        provider.add("screen.phantasia.guide_editor.btn_guide_link", "Guide: %s");
        provider.add("screen.phantasia.guide_editor.btn_scene_link", "Scene: %s");
        provider.add("screen.phantasia.guide_editor.btn_script_link", "Script: %s");
        provider.add("screen.phantasia.guide_editor.picker_guide", "Select Guide Link");
        provider.add("screen.phantasia.guide_editor.picker_scene", "Select Scene Link");
        provider.add("screen.phantasia.guide_editor.picker_script", "Select Script Link");

        // --- Screen: scene (viewer) labels ---
        provider.add("screen.phantasia.scene.label_size", "Size:");

        // --- Screen: scene_editor (new keys) ---
        provider.add("screen.phantasia.scene_editor.section_tooltip_items", "Tooltip Items (%d):");
        provider.add("screen.phantasia.scene_editor.label_item", "Item:");
        provider.add("screen.phantasia.scene_editor.label_source", "Source:");
        provider.add("screen.phantasia.scene_editor.hint_item_blank",
                "Leave Item blank to only set source. Leave Source blank to only set item. [Esc] deselects.");

        // --- Screen: script_step_item_editor (new keys) ---
        provider.add("screen.phantasia.script_step_item_editor.label_item", "Item:");

        // --- Screen: script_editor (new keys) ---
        provider.add("screen.phantasia.script_editor.tab_parts", "Parts…");
        provider.add("screen.phantasia.script_editor.tab_items", "Items…");
        provider.add("screen.phantasia.script_editor.label_item", "Item:");
        provider.add("screen.phantasia.script_editor.label_source", "Source:");
        provider.add("screen.phantasia.script_editor.hint_item_blank",
                "Leave Item blank to only set source. Leave Source blank to only set item. [Esc] deselects.");
        provider.add("screen.phantasia.script_editor.warn_recipe_override",
                "⚠ recipeId is set — this entry overrides recipe-placed items at this position");
        provider.add("screen.phantasia.script_editor.btn_add_global_mistake", "+ Add Global Mistake");
        provider.add("screen.phantasia.script_editor.section_global_mistakes", "⚠ Global Mistakes");

        // --- Screen: placement_step_editor ---
        provider.add("screen.phantasia.placement_step_editor.label_show", "Show:");
        provider.add("screen.phantasia.placement_step_editor.btn_global", "Global");
        provider.add("screen.phantasia.placement_step_editor.label_min", "min:");
        provider.add("screen.phantasia.placement_step_editor.label_max", "max:");
        provider.add("screen.phantasia.placement_step_editor.btn_edit_positions", "✎ Edit Positions (%d)");
        provider.add("screen.phantasia.placement_step_editor.label_working", "Working:");
        provider.add("screen.phantasia.placement_step_editor.label_recipe", "Recipe:");
        provider.add("screen.phantasia.placement_step_editor.label_particles", "Particles:");
        provider.add("screen.phantasia.placement_step_editor.btn_edit_placement", "✎ Edit placement / items →");
        provider.add("screen.phantasia.placement_step_editor.btn_clear_override", "✕ Clear override");
        provider.add("screen.phantasia.placement_step_editor.label_layer", "Layer");
        provider.add("screen.phantasia.placement_step_editor.label_filter", "filter");

        // --- Screen: block_filter ---
        provider.add("screen.phantasia.block_filter.title_count", "Block Filter — %d block types");
        provider.add("screen.phantasia.block_filter.hint_select", "Select which blocks to highlight");
        provider.add("screen.phantasia.block_filter.label_heatmap", "Heatmap Layers:");
        provider.add("screen.phantasia.block_filter.hint_inspect", "Click a block in the Shopping tab to inspect it.");
        provider.add("screen.phantasia.block_filter.msg_no_block", "No block at this position.");
        provider.add("screen.phantasia.block_filter.header_coords", "COORDINATES");
        provider.add("screen.phantasia.block_filter.header_specs", "SPECIFICATIONS & UTILITY");
        provider.add("screen.phantasia.block_filter.feat_block_entity", "⚡ Has Block Entity");
        provider.add("screen.phantasia.block_filter.feat_controller", "★ Multiblock Controller");
        provider.add("screen.phantasia.block_filter.feat_hatch", "🔗 Component: Hatch / Bus");
        provider.add("screen.phantasia.block_filter.feat_energy", "⚡ System: Energy I/O");
        provider.add("screen.phantasia.block_filter.header_blockstate", "BLOCKSTATE PROPERTIES");

        // --- Screen: footprint ---
        provider.add("screen.phantasia.footprint.label_layer", "Layer (Y):");
        provider.add("screen.phantasia.footprint.label_jump_to", "Jump to:");
        provider.add("screen.phantasia.footprint.label_inspecting", "Inspecting:");
        provider.add("screen.phantasia.footprint.feat_block_entity", "⚡ Block Entity");
        provider.add("screen.phantasia.footprint.feat_controller", "★ Controller");
        provider.add("screen.phantasia.footprint.label_layer_blocks", "Layer blocks:");

        // --- Screen: material_cost ---
        provider.add("screen.phantasia.material_cost.hint_search", "Search recipe type... (e.g. blast)");
        provider.add("screen.phantasia.material_cost.title_count", "Material Cost  —  %d block types");
        provider.add("screen.phantasia.material_cost.emi_syncing", "EMI Synchronizing...");
        provider.add("screen.phantasia.material_cost.label_direct", "direct");
        provider.add("screen.phantasia.material_cost.msg_no_ingredients", "No ingredients found.");
        provider.add("screen.phantasia.material_cost.msg_no_totals", "No totals yet.");
        provider.add("screen.phantasia.material_cost.hint_leaf_costs",
                "Current leaf costs — expand items in Ingredients to break down further");
        provider.add("screen.phantasia.material_cost.msg_copied", "✔ Copied to clipboard!");
        provider.add("screen.phantasia.material_cost.hint_controls", "▶ expand  •  ▼ right-click collapse  •  ↺ cycle");
        provider.add("screen.phantasia.material_cost.btn_copy_list", "Copy List");

        // --- Screen: variants (new keys) ---
        provider.add("screen.phantasia.variants.label_variants", "§bVariants");
        provider.add("screen.phantasia.variants.msg_no_variants", "§7No variants available for this machine.");

        // --- Screen: text_input ---
        provider.add("screen.phantasia.text_input.label_colors", "Colors: ");

        // --- Screen: scene_viewer ---
        provider.add("screen.phantasia.scene_viewer.btn_back", "← Back");
        provider.add("screen.phantasia.scene_viewer.btn_edit", "✏ Edit");
        provider.add("screen.phantasia.scene_viewer.btn_guide", "📖 Guide");
        provider.add("screen.phantasia.scene_viewer.btn_center", "⊕ Center");

        // --- Screen: scene_mistakes_editor (additional keys) ---
        provider.add("screen.phantasia.scene_mistakes_editor.header", "⚠ Layout Mistakes  —  %d defined");
        provider.add("screen.phantasia.scene_mistakes_editor.bottom_hint",
                "Mistakes flag incorrect machine placements in the scene viewer  —  %d placement(s) available");

        // --- Screen: placement_editor (additional keys) ---
        provider.add("screen.phantasia.placement_editor.btn_back", "← Back");
        provider.add("screen.phantasia.placement_editor.section_recipe_items", "Recipe Item Conditions");
        provider.add("screen.phantasia.placement_editor.section_recipe_items_hint",
                "(displayed in scene viewer alongside this machine)");
        provider.add("screen.phantasia.placement_editor.no_items", "No items yet.");
        provider.add("screen.phantasia.placement_editor.section_add_item", "+ Add Item");
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
