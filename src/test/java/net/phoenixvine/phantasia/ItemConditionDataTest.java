package net.phoenixvine.phantasia;

import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData.ItemConditionData;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemConditionDataTest {

    @Test
    void defaultFieldValues() {
        ItemConditionData data = new ItemConditionData();
        assertEquals("", data.item);
        assertEquals(1, data.count);
        assertNull(data.label);
        assertNull(data.description);
        assertNull(data.microsceneId);
        assertNull(data.guideId);
        assertEquals("input", data.type);
        assertEquals("none", data.track);
        assertEquals(20, data.trackDurationTicks);
    }

    @Test
    void copyPreservesAllFields() {
        ItemConditionData src = new ItemConditionData();
        src.item = "ars_nouveau:source_gem";
        src.count = 3;
        src.label = "Source Gem";
        src.type = "output";
        src.description = "Produces Source.";
        src.microsceneId = "phantasia:source_gem_scene";
        src.guideId = "phoenixvine:source_gem_guide";
        src.track = "pulse";
        src.trackDurationTicks = 40;

        ItemConditionData copy = src.copy();

        assertNotSame(src, copy);
        assertEquals(src.item, copy.item);
        assertEquals(src.count, copy.count);
        assertEquals(src.label, copy.label);
        assertEquals(src.type, copy.type);
        assertEquals(src.description, copy.description);
        assertEquals(src.microsceneId, copy.microsceneId);
        assertEquals(src.guideId, copy.guideId);
        assertEquals(src.track, copy.track);
        assertEquals(src.trackDurationTicks, copy.trackDurationTicks);
    }

    @Test
    void copyGuideIdIsNullWhenUnset() {
        ItemConditionData src = new ItemConditionData("ars_nouveau:wand", 1, "Wand", "catalyst");
        ItemConditionData copy = src.copy();
        assertNull(copy.guideId, "guideId should remain null if never set");
    }

    @Test
    void displayLabelUsesExplicitLabelWhenSet() {
        ItemConditionData data = new ItemConditionData("item:foo", 1, "My Label", "input");
        assertEquals("My Label", data.displayLabel());
    }

    @Test
    void displayLabelFallsBackToTypeDerived() {
        assertEquals("Input", new ItemConditionData("x", 1, null, "input").displayLabel());
        assertEquals("Output", new ItemConditionData("x", 1, null, "output").displayLabel());
        assertEquals("Catalyst", new ItemConditionData("x", 1, null, "catalyst").displayLabel());
    }

    @Test
    void accentColorMatchesType() {
        assertEquals(0xFF4FC3F7, ItemConditionData.staticAccentFor("input"));
        assertEquals(0xFF66BB6A, ItemConditionData.staticAccentFor("output"));
        assertEquals(0xFFFFB74D, ItemConditionData.staticAccentFor("catalyst"));
    }

    @Test
    void accentColorHandlesNullType() {
        assertDoesNotThrow(() -> ItemConditionData.staticAccentFor(null));
        assertEquals(0xFF4FC3F7, ItemConditionData.staticAccentFor(null));
    }
}
