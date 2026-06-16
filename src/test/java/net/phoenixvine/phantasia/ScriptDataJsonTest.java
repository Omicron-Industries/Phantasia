package net.phoenixvine.phantasia;

import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData.ItemConditionData;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that PhantasiaScriptData.fromJson() correctly deserializes all
 * fields used by scripts — including step items, guideId, hold, and cameras.
 */
class ScriptDataJsonTest {

    // ── fromJson / basic structure ──────────────────────────────────────────

    @Test
    void fromJsonParsesMinimalScript() {
        PhantasiaScriptData data = PhantasiaScriptData.fromJson("""
                {
                  "machine": "ars_nouveau:test_machine",
                  "steps": []
                }
                """);
        assertNotNull(data);
        assertEquals("ars_nouveau:test_machine", data.getMachine());
        assertNotNull(data.getSteps());
        assertTrue(data.getSteps().isEmpty());
    }

    @Test
    void fromJsonParsesStepTick() {
        PhantasiaScriptData data = PhantasiaScriptData.fromJson("""
                {
                  "machine": "test",
                  "steps": [{ "tick": 42, "caption": "Hello" }]
                }
                """);
        assertEquals(1, data.getSteps().size());
        PhantasiaScriptData.StepData step = data.getSteps().get(0);
        assertEquals(42, step.tick);
        assertEquals("Hello", step.caption);
    }

    @Test
    void fromJsonParsesStepItems() {
        PhantasiaScriptData data = PhantasiaScriptData.fromJson("""
                {
                  "machine": "test",
                  "steps": [{
                    "tick": 0,
                    "items": [
                      { "item": "ars_nouveau:source_jar", "label": "Source Jar", "type": "input" },
                      { "item": "ars_nouveau:spell_parchment", "label": "Spell Parchment",
                        "type": "catalyst", "guideId": "phoenixvine:spells" }
                    ]
                  }]
                }
                """);
        List<ItemConditionData> items = data.getSteps().get(0).items;
        assertEquals(2, items.size());

        ItemConditionData jar = items.get(0);
        assertEquals("ars_nouveau:source_jar", jar.item);
        assertEquals("Source Jar", jar.label);
        assertEquals("input", jar.type);
        assertNull(jar.guideId);

        ItemConditionData parchment = items.get(1);
        assertEquals("ars_nouveau:spell_parchment", parchment.item);
        assertEquals("catalyst", parchment.type);
        assertEquals("phoenixvine:spells", parchment.guideId);
    }

    @Test
    void fromJsonParsesHoldField() {
        PhantasiaScriptData data = PhantasiaScriptData.fromJson("""
                {
                  "machine": "test",
                  "steps": [{ "tick": 0, "hold": "an_crafting_apparatus" }]
                }
                """);
        assertEquals("an_crafting_apparatus", data.getSteps().get(0).hold);
    }

    @Test
    void fromJsonParsesGlobalMistakes() {
        PhantasiaScriptData data = PhantasiaScriptData.fromJson("""
                {
                  "machine": "test",
                  "steps": [],
                  "globalMistakes": ["First mistake", "Second mistake"]
                }
                """);
        assertEquals(2, data.getGlobalMistakes().size());
        assertEquals("First mistake", data.getGlobalMistakes().get(0));
    }

    @Test
    void fromJsonHandlesAbsentOptionalFields() {
        PhantasiaScriptData data = PhantasiaScriptData.fromJson("""
                { "machine": "x", "steps": [{ "tick": 0 }] }
                """);
        PhantasiaScriptData.StepData step = data.getSteps().get(0);
        assertNull(step.caption);
        assertNull(step.hold);
        assertNull(step.camera);
        assertNotNull(step.items);
        assertTrue(step.items.isEmpty());
    }

    @Test
    void fromJsonGuideIdNullWhenAbsent() {
        PhantasiaScriptData data = PhantasiaScriptData.fromJson("""
                {
                  "machine": "test",
                  "steps": [{ "tick": 0, "items": [
                    { "item": "ars_nouveau:source_jar", "type": "input" }
                  ]}]
                }
                """);
        assertNull(data.getSteps().get(0).items.get(0).guideId);
    }

    @Test
    void fromJsonStartCamera() {
        PhantasiaScriptData data = PhantasiaScriptData.fromJson("""
                {
                  "machine": "test",
                  "startCamera": { "yaw": -135.0, "pitch": -30.0, "zoom": 4.0 },
                  "steps": []
                }
                """);
        assertNotNull(data.getStartCamera());
        assertEquals(-135.0f, data.getStartCamera().yaw, 0.001f);
        assertEquals(-30.0f, data.getStartCamera().pitch, 0.001f);
        assertEquals(4.0f, data.getStartCamera().zoom, 0.001f);
    }

    @Test
    void fromJsonNullInputReturnsNull() {
        // Gson returns null for null input — confirm we don't NPE upstream
        assertNull(PhantasiaScriptData.fromJson((String) null));
    }
}
