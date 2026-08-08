package net.phoenixvine.phantasia.common.data.script;

import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class PhantasiaScriptData {

    @SerializedName("machine")
    private String machine = "";

    @Setter
    @SerializedName("startCamera")
    private StartCameraData startCamera = null;

    @SerializedName("steps")
    private List<StepData> steps = new ArrayList<>();

    @Setter
    @SerializedName("scriptDuration")
    private int scriptDuration = -1;

    @Setter
    @SerializedName("expandable")
    private boolean expandable = false;

    @Setter
    @SerializedName("recipeId")
    private String recipeId = null;

    @SerializedName("items")
    private List<PhantasiaSceneData.ItemConditionData> items = new ArrayList<>();

    @SerializedName("globalMistakes")
    private List<String> globalMistakes = new ArrayList<>();

    @SerializedName("optionalGroups")
    private List<OptionalGroupData> optionalGroups = new ArrayList<>();

    @Getter
    public static class OptionalGroupData {

        @SerializedName("id")
        public String id = "";

        @SerializedName("label")
        public String label = "";

        @SerializedName("category")
        public String category = "optional";

        @SerializedName("shownByDefault")
        public boolean shownByDefault = true;

        @SerializedName("primaryBlock")
        public String primaryBlock = null;

        @SerializedName("fallbackBlock")
        public String fallbackBlock = null;

        @SerializedName("autoDetected")
        public boolean autoDetected = false;

        @SerializedName("additionalBlocks")
        public List<String> additionalBlocks = new ArrayList<>();

        @SerializedName("positions")
        public List<VariantPositionData> positions = new ArrayList<>();

        public OptionalGroupData() {}

        public OptionalGroupData(String id, String label, String category,
                                 boolean shownByDefault) {
            this.id = id;
            this.label = label;
            this.category = category;
            this.shownByDefault = shownByDefault;
        }

        public OptionalGroupData copy() {
            OptionalGroupData c = new OptionalGroupData(id, label, category, shownByDefault);
            c.primaryBlock = primaryBlock;
            c.fallbackBlock = fallbackBlock;
            c.autoDetected = autoDetected;
            c.additionalBlocks.addAll(additionalBlocks);
            for (VariantPositionData p : positions) c.positions.add(p.copy());
            return c;
        }
    }

    @Getter
    public static class VariantPositionData {

        @SerializedName("x")
        public int x = 0;
        @SerializedName("y")
        public int y = 0;
        @SerializedName("z")
        public int z = 0;

        @SerializedName("primaryBlock")
        public String primaryBlock = null;

        @SerializedName("fallbackBlock")
        public String fallbackBlock = null;

        public VariantPositionData() {}

        public VariantPositionData(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public VariantPositionData copy() {
            VariantPositionData c = new VariantPositionData(x, y, z);
            c.primaryBlock = primaryBlock;
            c.fallbackBlock = fallbackBlock;
            return c;
        }
    }

    @Getter
    public static class StepData {

        @SerializedName("tick")
        public int tick = 0;

        @SerializedName("caption")
        public String caption = null;

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

        @SerializedName("fakeRecipeId")
        public String fakeRecipeId = null;

        @SerializedName("hold")
        public String hold = null;

        @SerializedName("layerCount")
        public int layerCount = -1;

        @SerializedName("showItems")
        public boolean showItems = true;

        @SerializedName("items")
        public List<PhantasiaSceneData.ItemConditionData> items = new ArrayList<>();

        @SerializedName("worldItems")
        public List<WorldItemEntry> worldItems = new ArrayList<>();

        @SerializedName("camera")
        public CameraData camera = null;

        @SerializedName("mistakes")
        public List<MistakeData> mistakes = new ArrayList<>();

        @SerializedName("highlights")
        public List<HighlightData> highlights = new ArrayList<>();

        @SerializedName("blockTransitions")
        public List<BlockTransitionData> blockTransitions = new ArrayList<>();

        public StepData() {}

        public StepData(int tick, String caption) {
            this.tick = tick;
            this.caption = caption;
        }

        public StepData copy() {
            StepData c = new StepData(tick, caption);
            c.show = show;
            c.layer = layer;
            c.layerMin = layerMin;
            c.layerMax = layerMax;
            c.hideLayer = hideLayer;
            c.working = working;

            c.fakeRecipeId = fakeRecipeId;
            c.layerCount = layerCount;
            c.showItems = showItems;
            for (PhantasiaSceneData.ItemConditionData it : items) c.items.add(it.copy());
            c.camera = camera == null ? null : new CameraData(camera.yaw, camera.pitch, camera.zoom,
                    camera.lerpType, camera.lerpTicks);
            for (int[] p : positions) c.positions.add(new int[] { p[0], p[1], p[2] });
            for (int[] p : hidePositions) c.hidePositions.add(new int[] { p[0], p[1], p[2] });
            for (WorldItemEntry wi : worldItems) c.worldItems.add(wi.copy());
            for (MistakeData m : mistakes) c.mistakes.add(new MistakeData(m.x, m.y, m.z, m.label, m.color));
            for (HighlightData h : highlights) c.highlights.add(h.copy());
            for (BlockTransitionData bt : blockTransitions) c.blockTransitions.add(bt.copy());
            return c;
        }
    }

    @Getter
    public static class HighlightData {

        @SerializedName("x")
        public int x = 0;
        @SerializedName("y")
        public int y = 0;
        @SerializedName("z")
        public int z = 0;

        @SerializedName("color")
        public String color = null;

        public HighlightData() {}

        public HighlightData(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public HighlightData(int x, int y, int z, String color) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color;
        }

        public int argb() {
            if (color == null || color.isBlank()) return 0xCCFFCC44;
            try {
                String c = color.startsWith("#") ? color.substring(1) : color;
                int rgb = (int) Long.parseLong(c, 16);
                return 0xCC000000 | (rgb & 0xFFFFFF);
            } catch (NumberFormatException e) {
                return 0xCCFFCC44;
            }
        }

        public HighlightData copy() {
            return new HighlightData(x, y, z, color);
        }
    }

    @Getter
    public static class BlockTransitionData {

        @SerializedName("x")
        public int x = 0;
        @SerializedName("y")
        public int y = 0;
        @SerializedName("z")
        public int z = 0;

        @SerializedName("state")
        public String state = "";

        public BlockTransitionData() {}

        public BlockTransitionData(int x, int y, int z, String state) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.state = state;
        }

        public BlockTransitionData copy() {
            return new BlockTransitionData(x, y, z, state);
        }
    }

    @Getter
    public static class WorldItemEntry {

        @SerializedName("x")
        public int x = 0;

        @SerializedName("y")
        public int y = 0;

        @SerializedName("z")
        public int z = 0;

        @SerializedName("item")
        public String item = "";

        @SerializedName("sourceAmount")
        public int sourceAmount = -1;

        public WorldItemEntry() {}

        public WorldItemEntry(int x, int y, int z, String item) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.item = item;
        }

        public WorldItemEntry copy() {
            WorldItemEntry c = new WorldItemEntry(x, y, z, item);
            c.sourceAmount = sourceAmount;
            return c;
        }
    }

    @Getter
    public static class StartCameraData {

        @SerializedName("yaw")
        public float yaw = Float.NaN;

        @SerializedName("pitch")
        public float pitch = Float.NaN;

        @SerializedName("zoom")
        public float zoom = -1f;

        @SerializedName("targetOffsetX")
        public float targetOffsetX = 0f;

        @SerializedName("targetOffsetY")
        public float targetOffsetY = 0f;

        @SerializedName("targetOffsetZ")
        public float targetOffsetZ = 0f;

        public StartCameraData() {}

        public boolean hasYaw() {
            return !Float.isNaN(yaw);
        }

        public boolean hasPitch() {
            return !Float.isNaN(pitch);
        }

        public boolean hasZoom() {
            return zoom > 0f;
        }

        public boolean hasTargetOffset() {
            return targetOffsetX != 0f || targetOffsetY != 0f || targetOffsetZ != 0f;
        }

        public StartCameraData copy() {
            StartCameraData c = new StartCameraData();
            c.yaw = yaw;
            c.pitch = pitch;
            c.zoom = zoom;
            c.targetOffsetX = targetOffsetX;
            c.targetOffsetY = targetOffsetY;
            c.targetOffsetZ = targetOffsetZ;
            return c;
        }
    }

    @Getter
    public static class CameraData {

        @SerializedName("yaw")
        public float yaw = -135f;

        @SerializedName("pitch")
        public float pitch = -35f;

        @SerializedName("zoom")
        public float zoom = -1f;

        @SerializedName("lerpType")
        public String lerpType = "SNAP";

        @SerializedName("lerpTicks")
        public int lerpTicks = 0;

        public CameraData() {}

        public CameraData(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public CameraData(float yaw, float pitch, float zoom) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.zoom = zoom;
        }

        public CameraData(float yaw, float pitch, float zoom, String lerpType, int lerpTicks) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.zoom = zoom;
            this.lerpType = lerpType;
            this.lerpTicks = lerpTicks;
        }
    }

    @Getter
    public static class MistakeData {

        @SerializedName("x")
        public int x = 0;
        @SerializedName("y")
        public int y = 0;
        @SerializedName("z")
        public int z = 0;
        @SerializedName("label")
        public String label = "";

        @SerializedName("color")
        public String color = "FFB74D";

        public MistakeData() {}

        public MistakeData(int x, int y, int z, String label) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.label = label;
        }

        public MistakeData(int x, int y, int z, String label, String color) {
            this(x, y, z, label);
            this.color = color;
        }

        public int colorArgb() {
            try {
                return (int) (Long.parseLong(color, 16) | 0xFF000000L);
            } catch (NumberFormatException e) {
                return 0xFFFFB74D;
            }
        }
    }

    public PhantasiaScriptData() {}

    public PhantasiaScriptData(String machine) {
        this.machine = machine;
    }

    public void addStep(StepData step) {
        steps.add(step);
    }

    public static PhantasiaScriptData defaultFor(String machine) {
        PhantasiaScriptData d = new PhantasiaScriptData(machine);
        StepData s = new StepData(0, null);
        s.show = "all";
        d.steps.add(s);
        return d;
    }

    public static PhantasiaScriptData simpleFor(String machine, String caption) {
        PhantasiaScriptData d = new PhantasiaScriptData(machine);
        StepData s = new StepData(0, caption);
        s.show = "all";
        d.steps.add(s);
        return d;
    }

    public PhantasiaScriptData copy() {
        PhantasiaScriptData c = new PhantasiaScriptData(machine);
        c.startCamera = startCamera == null ? null : startCamera.copy();
        c.scriptDuration = scriptDuration;
        c.expandable = expandable;
        c.recipeId = recipeId;
        for (StepData s : steps) c.steps.add(s.copy());
        for (PhantasiaSceneData.ItemConditionData it : items) c.items.add(it.copy());
        c.globalMistakes.addAll(globalMistakes);
        for (OptionalGroupData g : optionalGroups) c.optionalGroups.add(g.copy());
        return c;
    }

    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    public String toJson() {
        return GSON.toJson(this);
    }

    public static PhantasiaScriptData fromJson(String json) {
        return GSON.fromJson(json, PhantasiaScriptData.class);
    }

    public static PhantasiaScriptData fromJson(java.io.Reader reader) {
        return GSON.fromJson(reader, PhantasiaScriptData.class);
    }
}
