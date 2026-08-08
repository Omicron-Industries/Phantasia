package net.phoenixvine.phantasia.common.data.guides;

import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class PhantasiaGuideData {

    @SerializedName("id")
    public String id = "";

    @SerializedName("title")
    public String title = "Untitled Guide";

    @SerializedName("iconItem")
    public String iconItem = "minecraft:book";

    @SerializedName("subtitle")
    public String subtitle = null;

    @SerializedName("tag")
    public String tag = null;

    @SerializedName("tooltipItems")
    public List<String> tooltipItems = new ArrayList<>();

    @SerializedName("pages")
    public List<PageData> pages = new ArrayList<>();

    @SerializedName("mistakes")
    public List<PhantasiaSceneData.SceneMistakeData> mistakes = new ArrayList<>();

    public static class PageData {

        @SerializedName("headline")
        public String headline = null;

        @SerializedName("scriptId")
        public String scriptId = null;

        @SerializedName("text")
        public String text = null;

        @SerializedName("items")
        public List<PhantasiaSceneData.ItemConditionData> items = new ArrayList<>();

        @SerializedName("guideId")
        public String guideId = null;

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
            c.scriptId = scriptId;
            for (PhantasiaSceneData.ItemConditionData it : items) c.items.add(it.copy());
            return c;
        }

        public boolean hasContent() {
            return (headline != null && !headline.isBlank()) || (text != null && !text.isBlank()) || !items.isEmpty() ||
                    guideId != null || sceneId != null || scriptId != null;
        }
    }

    public PhantasiaGuideData() {}

    public PhantasiaGuideData(String id, String title) {
        this.id = id;
        this.title = title;
    }

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
        if (mistakes != null) for (PhantasiaSceneData.SceneMistakeData m : mistakes) c.mistakes.add(m.copy());
        return c;
    }

    public String toJson() {
        return PhantasiaScriptData.GSON.toJson(this);
    }

    public static PhantasiaGuideData fromJson(String json) {
        PhantasiaGuideData d = PhantasiaScriptData.GSON.fromJson(json, PhantasiaGuideData.class);
        if (d.pages == null) d.pages = new ArrayList<>();
        if (d.tooltipItems == null) d.tooltipItems = new ArrayList<>();
        if (d.mistakes == null) d.mistakes = new ArrayList<>();
        return d;
    }

    public static PhantasiaGuideData fromJson(java.io.Reader reader) {
        PhantasiaGuideData d = PhantasiaScriptData.GSON.fromJson(reader, PhantasiaGuideData.class);
        if (d.pages == null) d.pages = new ArrayList<>();
        if (d.tooltipItems == null) d.tooltipItems = new ArrayList<>();
        if (d.mistakes == null) d.mistakes = new ArrayList<>();
        return d;
    }
}
