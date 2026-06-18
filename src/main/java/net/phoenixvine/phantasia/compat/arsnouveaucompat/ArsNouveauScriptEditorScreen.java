package net.phoenixvine.phantasia.compat.arsnouveaucompat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.client.screens.PhantasiaSceneScreen;
import net.phoenixvine.phantasia.client.screens.editors.PhantasiaScriptEditorScreen;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

/**
 * Ars Nouveau–specific script editor.
 * Identical to the generic editor except the "Items…" subscreen is replaced
 * with {@link ArsNouveauScriptStepItemEditorScreen}, which adds a quick-insert
 * chip bar for common AN item IDs.
 */
@OnlyIn(Dist.CLIENT)
public class ArsNouveauScriptEditorScreen extends PhantasiaScriptEditorScreen {

    public ArsNouveauScriptEditorScreen(PhantasiaSceneScreen parent,
                                        String machineId,
                                        PhantasiaScriptData original) {
        super(parent, machineId, original);
    }

    @Override
    protected Screen createItemEditorScreen(int stepIndex) {
        return new ArsNouveauScriptStepItemEditorScreen(this, data, stepIndex);
    }

    private static final int BADGE_COLOR = 0xFF5B6EFF; // AN blue-violet

    @Override
    protected void renderTopBarBadge(GuiGraphics g, int rightEdge) {
        String label = "Ars Nouveau";
        int w = font.width(label) + 10;
        int rx = rightEdge - w - 6;
        int y1 = 3, y2 = TOP_BAR_H - 3;
        g.fill(rx, y1, rx + w, y2, 0x33000000);
        g.fill(rx, y1, rx + w, y1 + 1, BADGE_COLOR);
        g.drawString(font, label, rx + 5, (TOP_BAR_H - 8) / 2, BADGE_COLOR, false);
    }
}
