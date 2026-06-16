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

    /**
     * Item condition hints displayed in the GuideME-style panel alongside the
     * autogenned 3D scene. Same model as {@link PhantasiaSceneData.ItemConditionData}
     * — supports label, description, track animation, and an optional
     * {@code microsceneId} that opens {@link net.phoenixvine.phantasia.client.screens.PhantasiaItemMicrosceneScreen}
     * when clicked.
     */
    @SerializedName("items")
    private List<PhantasiaSceneData.ItemConditionData> items = new ArrayList<>();

    @SerializedName("mistakes")
    private List<MistakeData> mistakes = new ArrayList<>();

    @SerializedName("globalMistakes")
    private List<String> globalMistakes = new ArrayList<>();

    @SerializedName("optionalGroups")
    private List<OptionalGroupData> optionalGroups = new ArrayList<>();

    @Getter
    public static class OptionalGroupData {

        /**
         * Unique identifier for this group, used as the key in
         * {@link net.phoenixvine.phantasia.client.screens.PhantasiaVariantState}.
         * E.g. "fusion_glass", "energy_hatch", "muffler_hatch".
         */
        @SerializedName("id")
        public String id = "";

        /** Human-readable name shown in the Variants subscreen. */
        @SerializedName("label")
        public String label = "";

        /**
         * Which UI category this group appears under.
         * Valid values: "optional", "hatches_buses", "mufflers", "casings".
         * Defaults to "optional". Script authors can override to move a group
         * to a more appropriate category.
         */
        @SerializedName("category")
        public String category = "optional";

        /**
         * Whether the primary block is shown by default ({@code true}) or
         * the fallback ({@code false}).
         * For most .or() cases, primary is the decorative/cheaper option,
         * fallback is the structural block (casing, etc.).
         * For fusion glass specifically, primary = glass (correct/cheap),
         * fallback = casing (expensive/weird).
         */
        @SerializedName("shownByDefault")
        public boolean shownByDefault = true;

        /**
         * Resource location of the primary block state, e.g.
         * by {@link PhantasiaScript#fromData}.
         * Null = auto-detect from pattern.
         */
        @SerializedName("primaryBlock")
        public String primaryBlock = null;

        /**
         * Resource location of the fallback block state. Null = auto-detect.
         */
        @SerializedName("fallbackBlock")
        public String fallbackBlock = null;

        /**
         * If true, this group was auto-detected from the GTCEu pattern and
         * the script has not overridden it. Used to distinguish auto entries
         * from manual ones during hot-reload merging.
         */
        @SerializedName("autoDetected")
        public boolean autoDetected = false;

        /**
         * All positions (in local pattern coords) that belong to this group.
         * Each entry can optionally override {@code primaryBlock}/{@code fallbackBlock}
         * for that specific position.
         */
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

        /**
         * Per-position override for the primary block.
         * Null = inherit from the group's {@code primaryBlock}.
         */
        @SerializedName("primaryBlock")
        public String primaryBlock = null;

        /**
         * Per-position override for the fallback block.
         * Null = inherit from the group's {@code fallbackBlock}.
         */
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

        /**
         * If set, the scene screen pauses at this step and calls
         * {@link net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition#shouldHoldStep}
         * with this id each tick. Playback resumes when that method returns false.
         */
        @SerializedName("hold")
        public String hold = null;

        /**
         * Whether the item panel is visible during this step.
         * Defaults to {@code true}. Set to {@code false} on steps focused on
         * structure assembly where recipe context would be distracting.
         */
        @SerializedName("showItems")
        public boolean showItems = true;

        /**
         * Item condition hints displayed in the GuideME-style panel during
         * this step. Empty list = no panel shown (even if {@link #showItems}
         * is true). Items support labels, descriptions, track animations, and
         * optional {@code microsceneId} links.
         */
        @SerializedName("items")
        public List<PhantasiaSceneData.ItemConditionData> items = new ArrayList<>();

        @SerializedName("camera")
        public CameraData camera = null;

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
            c.showItems = showItems;
            for (PhantasiaSceneData.ItemConditionData it : items) c.items.add(it.copy());
            c.camera = camera == null ? null : new CameraData(camera.yaw, camera.pitch, camera.zoom,
                    camera.lerpType, camera.lerpTicks);
            for (int[] p : positions) c.positions.add(new int[] { p[0], p[1], p[2] });
            for (int[] p : hidePositions) c.hidePositions.add(new int[] { p[0], p[1], p[2] });
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
        for (StepData s : steps) c.steps.add(s.copy());
        for (PhantasiaSceneData.ItemConditionData it : items) c.items.add(it.copy());
        for (MistakeData m : mistakes) {
            c.mistakes.add(new MistakeData(m.x, m.y, m.z, m.label, m.color));
        }
        c.globalMistakes.addAll(globalMistakes);
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
