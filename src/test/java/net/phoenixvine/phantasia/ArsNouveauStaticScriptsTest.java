package net.phoenixvine.phantasia;

import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData.ItemConditionData;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;
import net.phoenixvine.phantasia.compat.arsnouveaucompat.ArsNouveauStaticScripts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ArsNouveauStaticScriptsTest {

    record ScriptEntry(String name, PhantasiaScriptData script) {}

    static Stream<ScriptEntry> allScripts() {
        return Stream.of(
                new ScriptEntry("basicSpellTurret", ArsNouveauStaticScripts.basicSpellTurret()),
                new ScriptEntry("enchantedSpellTurret", ArsNouveauStaticScripts.enchantedSpellTurret()),
                new ScriptEntry("timerSpellTurret", ArsNouveauStaticScripts.timerSpellTurret()),
                new ScriptEntry("rotatingSpellTurret", ArsNouveauStaticScripts.rotatingSpellTurret()),
                new ScriptEntry("wixieCauldron", ArsNouveauStaticScripts.wixieCauldron()),
                new ScriptEntry("drygmy", ArsNouveauStaticScripts.drygmy()),
                new ScriptEntry("whirlisprig", ArsNouveauStaticScripts.whirlisprig()),
                new ScriptEntry("starbuncle", ArsNouveauStaticScripts.starbuncle()),
                new ScriptEntry("ritualBrazier", ArsNouveauStaticScripts.ritualBrazier()),
                new ScriptEntry("agronomicSourcelink", ArsNouveauStaticScripts.agronomicSourcelink()),
                new ScriptEntry("volcanicSourcelink", ArsNouveauStaticScripts.volcanicSourcelink()),
                new ScriptEntry("vitalicSourcelink", ArsNouveauStaticScripts.vitalicSourcelink()),
                new ScriptEntry("mycelialSourcelink", ArsNouveauStaticScripts.mycelialSourcelink()),
                new ScriptEntry("alchemicalSourcelink", ArsNouveauStaticScripts.alchemicalSourcelink()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allScripts")
    void scriptIsWellFormed(ScriptEntry entry) {
        PhantasiaScriptData script = entry.script();
        assertNotNull(script, entry.name() + " returned null");
        assertFalse(script.getMachine().isBlank(), entry.name() + ": machine ID must not be blank");
        assertFalse(script.getSteps().isEmpty(), entry.name() + ": must have at least one step");

        for (int i = 0; i < script.getSteps().size(); i++) {
            PhantasiaScriptData.StepData step = script.getSteps().get(i);
            assertTrue(step.tick >= 0, entry.name() + " step[" + i + "] tick must be >= 0");

            for (ItemConditionData item : step.items) {
                assertFalse(item.item.isBlank(),
                        entry.name() + " step[" + i + "] has an item with empty item ID");
                if (item.guideId != null || item.microsceneId != null) {
                    assertNotEquals(item.guideId, item.microsceneId,
                            entry.name() + " step[" + i +
                                    "] guideId and microsceneId are identical — likely a copy-paste error");
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allScripts")
    void machineIdMatchesArsNamespace(ScriptEntry entry) {
        assertTrue(entry.script().getMachine().startsWith("ars_nouveau:"),
                entry.name() + " machine ID should start with 'ars_nouveau:'");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allScripts")
    void stepsAreChronologicallyOrdered(ScriptEntry entry) {
        List<PhantasiaScriptData.StepData> steps = entry.script().getSteps();
        for (int i = 1; i < steps.size(); i++) {
            assertTrue(steps.get(i).tick >= steps.get(i - 1).tick,
                    entry.name() + ": step[" + i + "] tick=" + steps.get(i).tick +
                            " must be >= previous step tick=" + steps.get(i - 1).tick);
        }
    }

    @Test
    void turretScriptsIncludeSpellParchmentItem() {
        for (var entry : List.of(
                ArsNouveauStaticScripts.basicSpellTurret(),
                ArsNouveauStaticScripts.enchantedSpellTurret(),
                ArsNouveauStaticScripts.timerSpellTurret(),
                ArsNouveauStaticScripts.rotatingSpellTurret())) {

            boolean hasSpellParchment = entry.getSteps().stream()
                    .flatMap(s -> s.items.stream())
                    .anyMatch(it -> "ars_nouveau:spell_parchment".equals(it.item));
            assertTrue(hasSpellParchment,
                    entry.getMachine() + " must include spell_parchment in at least one step");
        }
    }

    @Test
    void timerTurretIncludesDominionWand() {
        boolean hasWand = ArsNouveauStaticScripts.timerSpellTurret().getSteps().stream()
                .flatMap(s -> s.items.stream())
                .anyMatch(it -> "ars_nouveau:dominion_wand".equals(it.item));
        assertTrue(hasWand, "Timer spell turret script must include the Dominion Wand item");
    }

    @Test
    void starbuncleScriptIncludesDominionWand() {
        boolean hasWand = ArsNouveauStaticScripts.starbuncle().getSteps().stream()
                .flatMap(s -> s.items.stream())
                .anyMatch(it -> "ars_nouveau:dominion_wand".equals(it.item));
        assertTrue(hasWand, "Starbuncle script must include the Dominion Wand item");
    }

    @Test
    void drygmyScriptIncludesDrygmyCharm() {
        boolean hasCharm = ArsNouveauStaticScripts.drygmy().getSteps().stream()
                .flatMap(s -> s.items.stream())
                .anyMatch(it -> "ars_nouveau:drygmy_charm".equals(it.item));
        assertTrue(hasCharm, "Drygmy script must include the Drygmy Charm item");
    }

    @Test
    void whirlisprigScriptIncludesWhirlisprigCharm() {
        boolean hasCharm = ArsNouveauStaticScripts.whirlisprig().getSteps().stream()
                .flatMap(s -> s.items.stream())
                .anyMatch(it -> "ars_nouveau:whirlisprig_charm".equals(it.item));
        assertTrue(hasCharm, "Whirlisprig script must include the Whirlisprig Charm");
    }

    @Test
    void starbuncleScriptIncludesStarbuncleCharm() {
        boolean hasCharm = ArsNouveauStaticScripts.starbuncle().getSteps().stream()
                .flatMap(s -> s.items.stream())
                .anyMatch(it -> "ars_nouveau:starbuncle_charm".equals(it.item));
        assertTrue(hasCharm, "Starbuncle script must include the Starbuncle Charm");
    }

    @Test
    void ritualBrazierScriptIncludesSunriseRitual() {
        boolean hasSunrise = ArsNouveauStaticScripts.ritualBrazier().getSteps().stream()
                .flatMap(s -> s.items.stream())
                .anyMatch(it -> "ars_nouveau:ritual_sunrise".equals(it.item));
        assertTrue(hasSunrise, "Ritual Brazier script must include the Sunrise Ritual item");
    }

    @Test
    void sourcelinkScriptsIncludeSourceJar() {
        for (var script : List.of(
                ArsNouveauStaticScripts.agronomicSourcelink(),
                ArsNouveauStaticScripts.volcanicSourcelink(),
                ArsNouveauStaticScripts.vitalicSourcelink(),
                ArsNouveauStaticScripts.mycelialSourcelink(),
                ArsNouveauStaticScripts.alchemicalSourcelink())) {

            boolean hasJar = script.getSteps().stream()
                    .flatMap(s -> s.items.stream())
                    .anyMatch(it -> "ars_nouveau:source_jar".equals(it.item));
            assertTrue(hasJar, script.getMachine() + " must include source_jar in at least one step");
        }
    }

    @Test
    void wixieCauldronScriptIncludesWixieCharm() {
        boolean hasCharm = ArsNouveauStaticScripts.wixieCauldron().getSteps().stream()
                .flatMap(s -> s.items.stream())
                .anyMatch(it -> "ars_nouveau:wixie_charm".equals(it.item));
        assertTrue(hasCharm, "Wixie Cauldron script must include the Wixie Charm item");
    }

    @Test
    void allScriptFactoriesReturnDistinctInstances() {
        assertNotSame(ArsNouveauStaticScripts.basicSpellTurret(),
                ArsNouveauStaticScripts.basicSpellTurret());
    }
}
