package net.phoenixvine.phantasia.compat.arsnouveaucompat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.client.screens.editors.PhantasiaScriptEditorScreen;
import net.phoenixvine.phantasia.client.screens.editors.PhantasiaScriptStepItemEditorScreen;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

@OnlyIn(Dist.CLIENT)
public class ArsNouveauScriptStepItemEditorScreen extends PhantasiaScriptStepItemEditorScreen {

    private static final String[][] AN_QUICK_ITEMS = {
            { "ars_nouveau:source_jar", "Source Jar", "input" },
            { "ars_nouveau:spell_parchment", "Spell Parchment", "catalyst" },
            { "ars_nouveau:dominion_wand", "Dominion Wand", "catalyst" },
            { "ars_nouveau:wixie_charm", "Wixie Charm", "catalyst" },
            { "ars_nouveau:drygmy_charm", "Drygmy Charm", "catalyst" },
            { "ars_nouveau:whirlisprig_charm", "Whirlisprig Charm", "catalyst" },
            { "ars_nouveau:starbuncle_charm", "Starbuncle Charm", "catalyst" },
            { "ars_nouveau:ritual_sunrise", "Sunrise Ritual", "catalyst" },
            { "ars_nouveau:ritual_moonfall", "Moonfall Ritual", "catalyst" },
            { "ars_nouveau:ritual_overgrowth", "Overgrowth Ritual", "catalyst" },
            { "ars_nouveau:source_gem", "Source Gem", "input" },
            { "ars_nouveau:basic_spell_turret", "Basic Turret", "catalyst" },
            { "ars_nouveau:enchanted_spell_turret", "Enchanted Turret", "catalyst" },
            { "ars_nouveau:timer_spell_turret", "Timer Turret", "catalyst" },
            { "ars_nouveau:rotating_spell_turret", "Rotating Turret", "catalyst" },
    };

    private static final int CHIP_H = 14;
    private static final int CHIP_PAD = 3;

    private final PhantasiaScriptEditorScreen scriptEditorParent;
    private final PhantasiaScriptData scriptData;
    private final int stepIndex;

    public ArsNouveauScriptStepItemEditorScreen(PhantasiaScriptEditorScreen parent,
                                                PhantasiaScriptData scriptData,
                                                int stepIndex) {
        super(parent, scriptData, stepIndex);
        this.scriptEditorParent = parent;
        this.scriptData = scriptData;
        this.stepIndex = stepIndex;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        renderQuickInsertBar(g, mx, my);
    }

    private void renderQuickInsertBar(GuiGraphics g, int mx, int my) {
        int barY = height - 28;
        g.fill(0, barY - 1, width, barY, C_BORDER());
        g.fill(0, barY, width, height, 0xDD0A0A14);

        g.drawString(font, "Quick insert:", 6, barY + (28 - 8) / 2, C_DIM(), false);
        int labelW = font.width("Quick insert:") + 10;

        int bx = labelW;
        for (String[] chip : AN_QUICK_ITEMS) {
            String id = chip[0];
            String label = chip[1];
            String type = chip[2];
            int chipW = font.width(label) + 10;

            if (bx + chipW > width - 4) break;

            int ac = PhantasiaSceneData.ItemConditionData.staticAccentFor(type);
            boolean hov = isOver(mx, my, bx, barY + 7, chipW, CHIP_H);

            g.fill(bx, barY + 7, bx + chipW, barY + 7 + CHIP_H, hov ? C_BTN_HOV() : C_BTN());
            g.fill(bx, barY + 7, bx + chipW, barY + 8, ac);
            g.drawString(font, label, bx + 5, barY + 10, hov ? ac : C_TEXT(), false);

            final String fId = id;
            final String fLabel = label;
            final String fType = type;
            btns.add(new Btn(bx, barY + 7, chipW, CHIP_H, () -> quickInsert(fId, fLabel, fType)));

            bx += chipW + CHIP_PAD;
        }
    }

    private void quickInsert(String itemId, String label, String type) {
        scriptEditorParent.checkpoint();
        PhantasiaSceneData.ItemConditionData item = new PhantasiaSceneData.ItemConditionData(itemId, 1, label, type);
        item.track = "none";
        item.trackDurationTicks = 20;
        scriptData.getSteps().get(stepIndex).items.add(item);
        scriptEditorParent.dirty = true;
    }
}
