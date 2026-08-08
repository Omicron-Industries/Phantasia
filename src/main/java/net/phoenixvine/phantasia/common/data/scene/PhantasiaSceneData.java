package net.phoenixvine.phantasia.common.data.scene;

import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PhantasiaSceneData {

    @SerializedName("id")
    public String id = "";

    public String iconItem = "minecraft:chest";

    @SerializedName("name")
    public String name = "Unnamed Scene";

    @SerializedName("tooltipItems")
    public List<String> tooltipItems = new ArrayList<>();

    @SerializedName("startCamera")
    public PhantasiaScriptData.CameraData startCamera = null;

    @SerializedName("placements")
    public List<PlacementData> placements = new ArrayList<>();

    @SerializedName("mistakes")
    public List<SceneMistakeData> mistakes = new ArrayList<>();

    @SerializedName("steps")
    public List<StepData> steps = new ArrayList<>();

    public static class ItemConditionData {

        @SerializedName("item")
        public String item = "";

        @SerializedName("count")
        public int count = 1;

        @SerializedName("label")
        public String label = null;

        @SerializedName("description")
        public String description = null;

        @SerializedName("microsceneId")
        public String microsceneId = null;

        @SerializedName("guideId")
        public String guideId = null;

        @SerializedName("type")
        public String type = "input";

        @SerializedName("track")
        public String track = "none";

        @SerializedName("trackDuration")
        public int trackDurationTicks = 20;

        public ItemConditionData() {}

        public ItemConditionData(String item, int count, String label, String type) {
            this.item = item;
            this.count = count;
            this.label = label;
            this.type = type;
        }

        public String displayLabel() {
            if (label != null && !label.isBlank()) return label;
            return switch (type == null ? "input" : type.toLowerCase(java.util.Locale.ROOT)) {
                case "output" -> "Output";
                case "catalyst" -> "Catalyst";
                default -> "Input";
            };
        }

        public int accentColor() {
            return staticAccentFor(type);
        }

        public static int staticAccentFor(String type) {
            return switch (type == null ? "input" : type.toLowerCase(java.util.Locale.ROOT)) {
                case "output" -> 0xFF66BB6A;
                case "catalyst" -> 0xFFFFB74D;
                default -> 0xFF4FC3F7;
            };
        }

        public ItemConditionData copy() {
            ItemConditionData c = new ItemConditionData(item, count, label, type);
            c.track = track;
            c.trackDurationTicks = trackDurationTicks;
            c.description = description;
            c.microsceneId = microsceneId;
            c.guideId = guideId;
            return c;
        }
    }

    public static class PlacementData {

        @SerializedName("machine")
        public String machine = "";

        @SerializedName("x")
        public int x = 0;

        @SerializedName("y")
        public int y = 0;

        @SerializedName("z")
        public int z = 0;

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

    public static class SceneMistakeData {

        @SerializedName("id")
        public String id = "";

        @SerializedName("description")
        public String description = null;

        @SerializedName("severity")
        public String severity = "WARNING";

        @SerializedName("placements")
        public List<Integer> placements = new ArrayList<>();

        public SceneMistakeData() {}

        public SceneMistakeData(String id, String description, String severity, List<Integer> placements) {
            this.id = id;
            this.description = description;
            this.severity = severity;
            this.placements = placements != null ? new ArrayList<>(placements) : new ArrayList<>();
        }

        public static int severityColor(String severity) {
            if (severity == null) return 0xFFFFB74D;
            return switch (severity.toUpperCase(java.util.Locale.ROOT)) {
                case "INFO" -> 0xFF4FC3F7;
                case "ERROR" -> 0xFFFF5252;
                default -> 0xFFFFB74D;
            };
        }

        public int severityColor() {
            return severityColor(severity);
        }

        public SceneMistakeData copy() {
            return new SceneMistakeData(id, description, severity, placements);
        }
    }

    public static class MachineOverride {

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

        @SerializedName("hideLayer")
        public int hideLayer = -1;

        @SerializedName("hidePositions")
        public List<int[]> hidePositions = new ArrayList<>();

        @SerializedName("fakeRecipeId")
        public String fakeRecipeId = null;

        @SerializedName("particleEffects")
        public List<String> particleEffects = new ArrayList<>();

        @SerializedName("machineWorking")
        public Boolean machineWorking = null;

        @SerializedName("worldItems")
        public List<PhantasiaScriptData.WorldItemEntry> worldItems = new ArrayList<>();

        public MachineOverride() {}

        public boolean resolveWorking(boolean globalWorking) {
            return machineWorking != null ? machineWorking : globalWorking;
        }

        public String resolveFakeRecipeId(String stepFakeRecipeId) {
            return fakeRecipeId != null ? fakeRecipeId : stepFakeRecipeId;
        }

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
            for (PhantasiaScriptData.WorldItemEntry wi : worldItems) c.worldItems.add(wi.copy());
            return c;
        }
    }

    public static class StepData {

        @SerializedName("tick")
        public int tick = 0;

        @SerializedName("caption")
        public String caption = null;

        @SerializedName("description")
        public String description = null;

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

        @SerializedName("working")
        public boolean working = false;

        @SerializedName("showItems")
        public boolean showItems = true;

        @SerializedName("camera")
        public PhantasiaScriptData.CameraData camera = null;

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

        public MachineOverride getOverride(int placementIndex) {
            return machineOverrides.get(String.valueOf(placementIndex));
        }

        public void setOverride(int placementIndex, MachineOverride override) {
            machineOverrides.put(String.valueOf(placementIndex), override);
        }

        public void removeOverride(int placementIndex) {
            machineOverrides.remove(String.valueOf(placementIndex));
        }

        public boolean resolveWorking(int placementIndex) {
            MachineOverride ov = getOverride(placementIndex);
            return ov != null ? ov.resolveWorking(working) : working;
        }

        public String resolveFakeRecipeId(int placementIndex, String stepFakeRecipeId) {
            MachineOverride ov = getOverride(placementIndex);
            return ov != null ? ov.resolveFakeRecipeId(stepFakeRecipeId) : stepFakeRecipeId;
        }

        public List<String> resolveParticleEffects(int placementIndex, List<String> globalEffects) {
            MachineOverride ov = getOverride(placementIndex);
            return ov != null ? ov.resolveParticleEffects(globalEffects) :
                    (globalEffects != null ? globalEffects : new ArrayList<>());
        }
    }

    public PhantasiaSceneData() {}

    public PhantasiaSceneData(String id, String name, String iconItem) {
        this.id = id;
        this.name = name;
        this.iconItem = iconItem;
    }

    public PhantasiaSceneData(String id, String name) {
        this(id, name, "minecraft:chest");
    }

    public static PhantasiaSceneData blank(String id, String name, String iconItem) {
        PhantasiaSceneData d = new PhantasiaSceneData(id, name, iconItem);
        StepData s = new StepData(0, null);
        s.show = "all";
        d.steps.add(s);
        return d;
    }

    public PhantasiaSceneData copy() {
        PhantasiaSceneData c = new PhantasiaSceneData(id, name, iconItem);
        if (startCamera != null) c.startCamera = new PhantasiaScriptData.CameraData(
                startCamera.yaw, startCamera.pitch, startCamera.zoom,
                startCamera.lerpType, startCamera.lerpTicks);
        for (PlacementData p : placements) c.placements.add(p.copy());
        for (StepData s : steps) c.steps.add(s.copy());
        if (mistakes != null)
            for (SceneMistakeData m : mistakes) c.mistakes.add(m.copy());
        if (tooltipItems != null) c.tooltipItems = new ArrayList<>(tooltipItems);
        return c;
    }

    public String toJson() {
        return PhantasiaScriptData.GSON.toJson(this);
    }

    public static PhantasiaSceneData fromJson(String json) {
        PhantasiaSceneData d = PhantasiaScriptData.GSON.fromJson(json, PhantasiaSceneData.class);
        if (d.mistakes == null) d.mistakes = new ArrayList<>();
        if (d.tooltipItems == null) d.tooltipItems = new ArrayList<>();
        return d;
    }

    public static PhantasiaSceneData fromJson(java.io.Reader reader) {
        PhantasiaSceneData d = PhantasiaScriptData.GSON.fromJson(reader, PhantasiaSceneData.class);
        if (d.mistakes == null) d.mistakes = new ArrayList<>();
        if (d.tooltipItems == null) d.tooltipItems = new ArrayList<>();
        return d;
    }
}
