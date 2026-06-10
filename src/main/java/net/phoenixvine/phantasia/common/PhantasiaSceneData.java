package net.phoenixvine.phantasia.common;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PhantasiaSceneData — serialisable form of a manual multi-machine scene.
 *
 * Stored at: phantasia/scenes/<namespace>/<name>.json
 *
 * Steps reuse {@link PhantasiaScriptData.StepData} for camera, caption, tick,
 * and global visibility. Each step may additionally declare per-placement
 * visibility overrides via {@link StepData#machineOverrides}, keyed by
 * placement index (as a string for Gson compatibility).
 *
 * ── JSON schema ───────────────────────────────────────────────────────────────
 * {
 * "id": "phoenixvine:ore_processing_line",
 * "name": "Ore Processing Line",
 * "placements": [
 * { "machine": "gtceu:electric_blast_furnace", "x": 0, "y": 0, "z": 0 },
 * { "machine": "gtceu:large_chemical_reactor", "x": 0, "y": 0, "z": 20 }
 * ],
 * "mistakes": [
 * {
 * "id": "too_close",
 * "description": "These two machines are placed too close together.",
 * "severity": "WARNING",
 * "placements": [0, 1]
 * }
 * ],
 * "steps": [
 * {
 * "tick": 0,
 * "caption": "First, the EBF smelts the ore.",
 * "show": "all",
 * "machineOverrides": {
 * "0": {
 * "show": "layer", "layer": 2,
 * "machineWorking": true,
 * "fakeRecipeId": "gtceu:ebf_iron"
 * },
 * "1": { "show": "all", "hideLayer": 3, "machineWorking": false }
 * },
 * "camera": { "yaw": -135, "pitch": -30, "lerpType": "EASE_OUT", "lerpTicks": 20 }
 * }
 * ]
 * }
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class PhantasiaSceneData {

    @SerializedName("id")
    public String id = "";

    public String iconItem = "minecraft:chest";

    @SerializedName("name")
    public String name = "Unnamed Scene";

    @SerializedName("placements")
    public List<PlacementData> placements = new ArrayList<>();

    /**
     * Layout mistake rules for this scene.
     * Each entry defines a named condition that flags incorrect machine placement
     * (e.g. machines too close, misaligned, or missing). The scene viewer can
     * highlight the relevant placements and show the player what went wrong.
     * Null-safe: always initialised to an empty list on load.
     */
    @SerializedName("mistakes")
    public List<SceneMistakeData> mistakes = new ArrayList<>();

    @SerializedName("steps")
    public List<StepData> steps = new ArrayList<>();

    // ── Item condition ────────────────────────────────────────────────────────

    /**
     * An item displayed alongside a placement to explain a recipe condition —
     * e.g. what the machine requires as input, produces as output, or consumes
     * as a catalyst. Pure display; no gameplay effect.
     *
     * JSON:
     * { "item": "gtceu:wafer", "count": 1, "label": "Input", "type": "input" }
     */
    public static class ItemConditionData {

        /** Registry ID of the item, e.g. {@code "gtceu:wafer"}. */
        @SerializedName("item")
        public String item = "";

        /** Stack size shown in the HUD strip. Defaults to 1. */
        @SerializedName("count")
        public int count = 1;

        /**
         * Short human-readable label rendered beneath the item icon.
         * {@code null} means auto-label derived from {@link #type}.
         */
        @SerializedName("label")
        public String label = null;

        /**
         * Rich text description shown in the item panel alongside the icon.
         * Supports Minecraft § colour codes. If null, only the label is shown.
         */
        @SerializedName("description")
        public String description = null;

        /**
         * Optional ID of another {@link PhantasiaSceneData} to open when the
         * user clicks this item in the panel. Null = no click action.
         * Example: {@code "phantasia:wafer_production"}.
         */
        @SerializedName("microsceneId")
        public String microsceneId = null;

        /**
         * Relationship to the machine: {@code "input"}, {@code "output"},
         * or {@code "catalyst"}. Drives the accent colour in the HUD strip
         * (blue / green / yellow) and the auto-label when {@link #label} is null.
         */
        @SerializedName("type")
        public String type = "input";

        /**
         * Animation track for this item icon in the 2D scene overlay.
         * {@code "none"} (default) — icon stays fixed in its strip slot.
         * {@code "left"} — slides in from the left and disappears right.
         * {@code "right"} — slides in from the right and disappears left.
         * {@code "up"} — slides upward and fades.
         * {@code "down"} — slides downward and fades.
         * {@code "pulse"} — bobs gently in place (attention effect).
         *
         * The animation loops every {@link #trackDurationTicks} ticks.
         */
        @SerializedName("track")
        public String track = "none";

        /**
         * How many ticks one full animation cycle takes. Defaults to 20 (1 second).
         * Ignored when {@link #track} is {@code "none"}.
         */
        @SerializedName("trackDuration")
        public int trackDurationTicks = 20;

        public ItemConditionData() {}

        public ItemConditionData(String item, int count, String label, String type) {
            this.item = item;
            this.count = count;
            this.label = label;
            this.type = type;
        }

        /** Returns the label to display: explicit label if set, else type-derived. */
        public String displayLabel() {
            if (label != null && !label.isBlank()) return label;
            return switch (type == null ? "input" : type.toLowerCase(java.util.Locale.ROOT)) {
                case "output" -> "Output";
                case "catalyst" -> "Catalyst";
                default -> "Input";
            };
        }

        /** Accent colour for the HUD strip border/header, by type. */
        public int accentColor() {
            return staticAccentFor(type);
        }

        /** Static variant so the editor can call it without an instance. */
        public static int staticAccentFor(String type) {
            return switch (type == null ? "input" : type.toLowerCase(java.util.Locale.ROOT)) {
                case "output" -> 0xFF66BB6A; // green
                case "catalyst" -> 0xFFFFB74D; // amber
                default -> 0xFF4FC3F7; // blue
            };
        }

        public ItemConditionData copy() {
            ItemConditionData c = new ItemConditionData(item, count, label, type);
            c.track = track;
            c.trackDurationTicks = trackDurationTicks;
            c.description = description;
            c.microsceneId = microsceneId;
            return c;
        }
    }

    // ── Placement ─────────────────────────────────────────────────────────────

    public static class PlacementData {

        @SerializedName("machine")
        public String machine = "";

        /** World-space offset of this machine's origin from the scene origin. */
        @SerializedName("x")
        public int x = 0;

        @SerializedName("y")
        public int y = 0;

        @SerializedName("z")
        public int z = 0;

        /**
         * Items to display alongside this placement as recipe condition hints.
         * Shown as a HUD strip of item icons in the scene viewer and editor.
         * These are purely visual — they have no gameplay effect.
         */
        @SerializedName("items")
        public List<ItemConditionData> items = new ArrayList<>();

        public PlacementData() {}

        public PlacementData(String machine, int x, int y, int z) {
            this.machine = machine;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public PlacementData copy() {
            PlacementData c = new PlacementData(machine, x, y, z);
            for (ItemConditionData it : items) c.items.add(it.copy());
            return c;
        }
    }

    // ── Layout mistake rule ───────────────────────────────────────────────────

    /**
     * A named layout mistake rule. Each entry describes a condition that
     * indicates incorrect placement of machines in the scene — for example
     * two machines placed too close together, a machine facing the wrong way,
     * or a required machine missing from the layout entirely.
     *
     * The scene viewer uses these entries to highlight the offending placements
     * (using the {@link #severity} colour) and show the player {@link #description}.
     *
     * JSON:
     * {
     * "id": "too_close",
     * "description": "Machines 0 and 1 are too close — leave at least 4 blocks between them.",
     * "severity": "WARNING",
     * "placements": [0, 1]
     * }
     */
    public static class SceneMistakeData {

        /**
         * Unique identifier for this mistake within the scene.
         * Used as a key for tracking whether a mistake has been acknowledged.
         * Example: {@code "too_close"}, {@code "missing_output_hatch"}.
         */
        @SerializedName("id")
        public String id = "";

        /**
         * Human-readable explanation shown to the player when this mistake
         * is active. Supports Minecraft § colour codes.
         */
        @SerializedName("description")
        public String description = null;

        /**
         * Severity level — controls the highlight colour used in the scene viewer.
         * One of:
         * {@code "INFO"} — informational, blue (0xFF4FC3F7)
         * {@code "WARNING"} — caution, amber (0xFFFFB74D) [default]
         * {@code "ERROR"} — critical, red (0xFFFF5252)
         */
        @SerializedName("severity")
        public String severity = "WARNING";

        /**
         * Indices into {@link PhantasiaSceneData#placements} that this mistake
         * applies to. The viewer will highlight these placements with the
         * severity colour. Empty or null means the mistake is scene-wide
         * (no specific placement highlighted).
         */
        @SerializedName("placements")
        public List<Integer> placements = new ArrayList<>();

        public SceneMistakeData() {}

        public SceneMistakeData(String id, String description, String severity, List<Integer> placements) {
            this.id = id;
            this.description = description;
            this.severity = severity;
            this.placements = placements != null ? new ArrayList<>(placements) : new ArrayList<>();
        }

        /**
         * Returns the accent colour for the given severity string.
         * Safe to call with null (returns WARNING amber).
         */
        public static int severityColor(String severity) {
            if (severity == null) return 0xFFFFB74D;
            return switch (severity.toUpperCase(java.util.Locale.ROOT)) {
                case "INFO" -> 0xFF4FC3F7;
                case "ERROR" -> 0xFFFF5252;
                default -> 0xFFFFB74D; // WARNING
            };
        }

        /** Convenience accessor for this instance's severity colour. */
        public int severityColor() {
            return severityColor(severity);
        }

        public SceneMistakeData copy() {
            return new SceneMistakeData(id, description, severity, placements);
        }
    }

    // ── Per-placement visibility override ─────────────────────────────────────

    /**
     * Visibility and simulation settings scoped to a single placement within
     * a step. Tick, caption, and camera are always step-level.
     * All fields default to "no override" sentinels (null / -1 / empty).
     */
    public static class MachineOverride {

        /**
         * Visibility mode for this placement in this step.
         * Same values as {@link PhantasiaScriptData.StepData#show}.
         * Null = inherit step-level show mode.
         */
        @SerializedName("show")
        public String show = null;

        @SerializedName("layer")
        public int layer = 0;

        @SerializedName("layerMin")
        public int layerMin = 0;

        @SerializedName("layerMax")
        public int layerMax = 0;

        @SerializedName("positions")
        public List<int[]> positions = new ArrayList<>();

        /** ≥ 0 to hide that Y layer within this placement. -1 = none. */
        @SerializedName("hideLayer")
        public int hideLayer = -1;

        @SerializedName("hidePositions")
        public List<int[]> hidePositions = new ArrayList<>();

        // ── Feature 1: per-machine recipe simulation ──────────────────────────

        /**
         * ID of a recipe to fake-load on this specific machine when the step
         * is active.
         * Null = no override; the scene's global working state determines
         * whether any recipe is simulated.
         * Example: {@code "gtceu:ebf_iron_ingot"}.
         */
        @SerializedName("fakeRecipeId")
        public String fakeRecipeId = null;

        /**
         * Particle effects to spawn on this machine during this step.
         * Each entry is a namespaced particle ID, e.g. {@code "minecraft:flame"}.
         * Null or empty = inherit global particle behaviour (none by default).
         *
         * Multiple effects are stored as a list so they can be combined:
         * ["minecraft:flame", "minecraft:smoke"]
         */
        @SerializedName("particleEffects")
        public List<String> particleEffects = new ArrayList<>();

        // ── Feature 2: per-machine working state ─────────────────────────────

        /**
         * Override the active/working state for this specific machine.
         *
         * {@code null} — inherit the scene's global {@code working} flag
         * (set by the step or toggled in the viewer).
         * {@code false} — force this machine idle regardless of global state.
         * {@code true} — force this machine working regardless of global state.
         *
         * This allows scenes where machine A is actively processing while
         * machine B is idle (e.g. to show a handoff between two steps).
         */
        @SerializedName("machineWorking")
        public Boolean machineWorking = null;

        public MachineOverride() {}

        /**
         * Resolves the effective working state for this machine given the
         * scene-level global flag.
         *
         * @param globalWorking the scene's current global {@code working} boolean
         * @return {@code true} if this machine should be shown as actively working
         */
        public boolean resolveWorking(boolean globalWorking) {
            return machineWorking != null ? machineWorking : globalWorking;
        }

        /**
         * Returns the effective fake recipe ID, falling back to the step-level
         * value when no per-machine override is set.
         *
         * @param stepFakeRecipeId the step's global fakeRecipeId (may be null)
         * @return the recipe ID to load, or null for none
         */
        public String resolveFakeRecipeId(String stepFakeRecipeId) {
            return fakeRecipeId != null ? fakeRecipeId : stepFakeRecipeId;
        }

        /**
         * Returns the effective particle effects list.
         * If this override has its own effects, they replace (not merge with)
         * any global effects. Returns the global list when this override is empty.
         *
         * @param globalEffects the step's global particle effects (may be null)
         * @return non-null list of particle IDs to spawn
         */
        public List<String> resolveParticleEffects(List<String> globalEffects) {
            if (particleEffects != null && !particleEffects.isEmpty()) return particleEffects;
            return globalEffects != null ? globalEffects : new ArrayList<>();
        }

        public MachineOverride copy() {
            MachineOverride c = new MachineOverride();
            c.show = show;
            c.layer = layer;
            c.layerMin = layerMin;
            c.layerMax = layerMax;
            c.hideLayer = hideLayer;
            c.fakeRecipeId = fakeRecipeId;
            c.machineWorking = machineWorking;
            for (int[] p : positions) c.positions.add(new int[] { p[0], p[1], p[2] });
            for (int[] p : hidePositions) c.hidePositions.add(new int[] { p[0], p[1], p[2] });
            if (particleEffects != null) c.particleEffects = new ArrayList<>(particleEffects);
            return c;
        }
    }

    // ── Step ─────────────────────────────────────────────────────────────────

    /**
     * Scene step. Extends the machine-script step with a map of per-placement
     * visibility overrides. All other fields are identical to
     * {@link PhantasiaScriptData.StepData}.
     */
    public static class StepData {

        @SerializedName("tick")
        public int tick = 0;

        @SerializedName("caption")
        public String caption = null;

        /**
         * Rich body text shown beneath the headline in the guide viewer.
         * Supports Minecraft § colour codes and wraps to the column width.
         * Null or blank = no body text for this step.
         */
        @SerializedName("description")
        public String description = null;

        /**
         * Global visibility mode applied to ALL placements unless overridden.
         * Same values as {@link PhantasiaScriptData.StepData#show}.
         */
        @SerializedName("show")
        public String show = "all";

        @SerializedName("layer")
        public int layer = 0;

        @SerializedName("layerMin")
        public int layerMin = 0;

        @SerializedName("layerMax")
        public int layerMax = 0;

        @SerializedName("positions")
        public List<int[]> positions = new ArrayList<>();

        @SerializedName("hideLayer")
        public int hideLayer = -1;

        @SerializedName("hidePositions")
        public List<int[]> hidePositions = new ArrayList<>();

        /**
         * Global working state for this step. Individual machines may override
         * this via {@link MachineOverride#machineWorking}.
         */
        @SerializedName("working")
        public boolean working = false;

        /**
         * Whether to display item-condition strips for placements that have
         * {@link PlacementData#items} defined. Defaults to {@code true}.
         * Set to {@code false} on steps where the item overlay would be
         * distracting (e.g. a step focused on structure assembly).
         */
        @SerializedName("showItems")
        public boolean showItems = true;

        @SerializedName("camera")
        public PhantasiaScriptData.CameraData camera = null;

        /**
         * Per-placement visibility and simulation overrides.
         * Key = placement index as string (e.g. "0", "1").
         * Absent key = placement uses global step settings.
         */
        @SerializedName("machineOverrides")
        public Map<String, MachineOverride> machineOverrides = new LinkedHashMap<>();

        public StepData() {}

        public StepData(int tick, String caption) {
            this.tick = tick;
            this.caption = caption;
        }

        public StepData copy() {
            StepData c = new StepData(tick, caption);
            c.description = description;
            c.show = show;
            c.layer = layer;
            c.layerMin = layerMin;
            c.layerMax = layerMax;
            c.hideLayer = hideLayer;
            c.working = working;
            c.showItems = showItems;
            c.camera = camera == null ? null : new PhantasiaScriptData.CameraData(
                    camera.yaw, camera.pitch, camera.zoom,
                    camera.lerpType, camera.lerpTicks);
            for (int[] p : positions) c.positions.add(new int[] { p[0], p[1], p[2] });
            for (int[] p : hidePositions) c.hidePositions.add(new int[] { p[0], p[1], p[2] });
            for (Map.Entry<String, MachineOverride> e : machineOverrides.entrySet())
                c.machineOverrides.put(e.getKey(), e.getValue().copy());
            return c;
        }

        /** Returns the override for the given placement index, or null if none set. */
        public MachineOverride getOverride(int placementIndex) {
            return machineOverrides.get(String.valueOf(placementIndex));
        }

        /** Sets (or replaces) the override for the given placement index. */
        public void setOverride(int placementIndex, MachineOverride override) {
            machineOverrides.put(String.valueOf(placementIndex), override);
        }

        /** Removes the override for the given placement index (reverts to global). */
        public void removeOverride(int placementIndex) {
            machineOverrides.remove(String.valueOf(placementIndex));
        }

        /**
         * Resolves the effective working state for a given placement, checking
         * its machine override before falling back to this step's global value.
         */
        public boolean resolveWorking(int placementIndex) {
            MachineOverride ov = getOverride(placementIndex);
            return ov != null ? ov.resolveWorking(working) : working;
        }

        /**
         * Resolves the effective fake recipe ID for a given placement.
         * Per-machine override takes priority over any step-level value.
         *
         * @param stepFakeRecipeId the step-level recipe ID (from PhantasiaScriptData, may be null)
         */
        public String resolveFakeRecipeId(int placementIndex, String stepFakeRecipeId) {
            MachineOverride ov = getOverride(placementIndex);
            return ov != null ? ov.resolveFakeRecipeId(stepFakeRecipeId) : stepFakeRecipeId;
        }

        /**
         * Resolves the effective particle effects for a given placement.
         * Per-machine override replaces global effects when non-empty.
         *
         * @param globalEffects the step-level particle effects list (may be null)
         */
        public List<String> resolveParticleEffects(int placementIndex, List<String> globalEffects) {
            MachineOverride ov = getOverride(placementIndex);
            return ov != null ? ov.resolveParticleEffects(globalEffects) :
                    (globalEffects != null ? globalEffects : new ArrayList<>());
        }
    }

    // ── Constructors ─────────────────────────────────────────────────────────

    public PhantasiaSceneData() {}

    public PhantasiaSceneData(String id, String name, String iconItem) {
        this.id = id;
        this.name = name;
        this.iconItem = iconItem;
    }

    public PhantasiaSceneData(String id, String name) {
        this(id, name, "minecraft:chest");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public static PhantasiaSceneData blank(String id, String name, String iconItem) {
        PhantasiaSceneData d = new PhantasiaSceneData(id, name, iconItem);
        StepData s = new StepData(0, null);
        s.show = "all";
        d.steps.add(s);
        return d;
    }

    public PhantasiaSceneData copy() {
        PhantasiaSceneData c = new PhantasiaSceneData(id, name, iconItem);
        for (PlacementData p : placements) c.placements.add(p.copy());
        for (StepData s : steps) c.steps.add(s.copy());
        if (mistakes != null)
            for (SceneMistakeData m : mistakes) c.mistakes.add(m.copy());
        return c;
    }

    // ── Gson codec ────────────────────────────────────────────────────────────

    public String toJson() {
        return PhantasiaScriptData.GSON.toJson(this);
    }

    public static PhantasiaSceneData fromJson(String json) {
        PhantasiaSceneData d = PhantasiaScriptData.GSON.fromJson(json, PhantasiaSceneData.class);
        if (d.mistakes == null) d.mistakes = new ArrayList<>();
        return d;
    }

    public static PhantasiaSceneData fromJson(java.io.Reader reader) {
        PhantasiaSceneData d = PhantasiaScriptData.GSON.fromJson(reader, PhantasiaSceneData.class);
        if (d.mistakes == null) d.mistakes = new ArrayList<>();
        return d;
    }
}
