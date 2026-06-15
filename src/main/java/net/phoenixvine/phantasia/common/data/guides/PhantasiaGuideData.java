package net.phoenixvine.phantasia.common.data.guides;

import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * PhantasiaGuideData — standalone text + item guide, independent of any machine
 * or scene. Stored at: {@code phantasia/guides/<namespace>/<name>.json}
 *
 * These are pure reading material — no 3D viewport, no multiblock, no placements.
 * Ideal for: crafting recipes, progression notes, lore pages, item explanations.
 *
 * Can also be referenced from a {@link PhantasiaSceneData} step or a
 * {@link PhantasiaScriptData} step via {@code guideId} to show a guide panel
 * alongside the 3D viewer without embedding the text directly in the step.
 *
 * ── JSON schema ──────────────────────────────────────────────────────────────
 * {
 * "id": "phoenixvine:ore_processing_intro",
 * "title": "Ore Processing",
 * "iconItem": "gtceu:raw_iron",
 * "pages": [
 * {
 * "headline": "What is ore processing?",
 * "text": "Ore processing is the conversion of raw ores into refined metals.\n\nGregTech provides several tiers...",
 * "items": [
 * { "item": "gtceu:raw_iron", "label": "Raw Ore", "type": "input" },
 * { "item": "gtceu:iron_ingot", "label": "Ingot", "type": "output" }
 * ]
 * },
 * {
 * "headline": "Tier 1 — Furnace",
 * "text": "The simplest route: smelt raw ore directly in a furnace.",
 * "guideId": "phoenixvine:furnace_smelting"
 * }
 * ]
 * }
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class PhantasiaGuideData {

    // ── Top-level fields ──────────────────────────────────────────────────────

    @SerializedName("id")
    public String id = "";

    /** Display title shown in the header bar and selection screen. */
    @SerializedName("title")
    public String title = "Untitled Guide";

    /** Registry ID of an item used as the card icon in selection screens. */
    @SerializedName("iconItem")
    public String iconItem = "minecraft:book";

    /** Optional short subtitle shown under the card icon. */
    @SerializedName("subtitle")
    public String subtitle = null;

    /** Tag string for filtering in the selection screen (e.g. "processing", "lore"). */
    @SerializedName("tag")
    public String tag = null;

    /**
     * Item/block registry IDs that trigger the tooltip keybind for this guide.
     * E.g. ["minecraft:iron_ore", "gtceu:wafer"] — holding or looking at one of
     * these items/blocks shows the hold-to-open tooltip.
     */
    @SerializedName("tooltipItems")
    public List<String> tooltipItems = new ArrayList<>();

    @SerializedName("pages")
    public List<PageData> pages = new ArrayList<>();

    // ── Page ─────────────────────────────────────────────────────────────────

    /**
     * One page in the guide. Rendered as: headline → body text → item cards.
     * All fields are optional — a page with only text and no headline is valid.
     */
    public static class PageData {

        /**
         * Short headline rendered at 1.5× above the body text.
         * Null = no headline on this page (starts directly with body text).
         */
        @SerializedName("headline")
        public String headline = null;

        @SerializedName("scriptId") // Adds field mapping to your guide json schemas
        public String scriptId = null;

        /**
         * Body text. Supports newlines (\n) and Minecraft § colour codes.
         * Wrapped automatically to the column width.
         * Null or blank = text-free page (items only, or pure headline).
         */
        @SerializedName("text")
        public String text = null;

        /**
         * Item cards shown below the body text.
         * Reuses {@link PhantasiaSceneData.ItemConditionData} so item labels,
         * types, microscene links, and animation tracks work identically.
         */
        @SerializedName("items")
        public List<PhantasiaSceneData.ItemConditionData> items = new ArrayList<>();

        /**
         * Optional ID of another {@link PhantasiaGuideData} to embed as a
         * sub-page link at the bottom of this page (rendered as a clickable
         * "Continue reading →" button rather than inlining all its content).
         * Null = no link.
         */
        @SerializedName("guideId")
        public String guideId = null;

        /**
         * Optional ID of a {@link PhantasiaSceneData} whose 3D viewer opens
         * when the user clicks the scene link button on this page.
         * Useful for connecting guide text to the relevant machine visualisation.
         */
        @SerializedName("sceneId")
        public String sceneId = null;

        public PageData() {}

        public PageData(String headline, String text) {
            this.headline = headline;
            this.text = text;
        }

        public PageData copy() {
            PageData c = new PageData(headline, text);
            c.guideId = guideId;
            c.sceneId = sceneId;
            c.scriptId = scriptId; // Include new field in object copies
            for (PhantasiaSceneData.ItemConditionData it : items) c.items.add(it.copy());
            return c;
        }

        public boolean hasContent() {
            return (headline != null && !headline.isBlank()) || (text != null && !text.isBlank()) || !items.isEmpty() ||
                    guideId != null || sceneId != null || scriptId != null; // Update content check
        }
    }

    // ── Constructors ─────────────────────────────────────────────────────────

    public PhantasiaGuideData() {}

    public PhantasiaGuideData(String id, String title) {
        this.id = id;
        this.title = title;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public static PhantasiaGuideData blank(String id, String title, String iconItem) {
        PhantasiaGuideData d = new PhantasiaGuideData(id, title);
        d.iconItem = iconItem;
        d.pages.add(new PageData(title, null));
        return d;
    }

    public PhantasiaGuideData copy() {
        PhantasiaGuideData c = new PhantasiaGuideData(id, title);
        c.iconItem = iconItem;
        c.subtitle = subtitle;
        c.tag = tag;
        if (tooltipItems != null) c.tooltipItems = new ArrayList<>(tooltipItems);
        for (PageData p : pages) c.pages.add(p.copy());
        return c;
    }

    // ── Gson codec ────────────────────────────────────────────────────────────

    public String toJson() {
        return PhantasiaScriptData.GSON.toJson(this);
    }

    public static PhantasiaGuideData fromJson(String json) {
        PhantasiaGuideData d = PhantasiaScriptData.GSON.fromJson(json, PhantasiaGuideData.class);
        if (d.pages == null) d.pages = new ArrayList<>();
        if (d.tooltipItems == null) d.tooltipItems = new ArrayList<>();
        return d;
    }

    public static PhantasiaGuideData fromJson(java.io.Reader reader) {
        PhantasiaGuideData d = PhantasiaScriptData.GSON.fromJson(reader, PhantasiaGuideData.class);
        if (d.pages == null) d.pages = new ArrayList<>();
        if (d.tooltipItems == null) d.tooltipItems = new ArrayList<>();
        return d;
    }
}
