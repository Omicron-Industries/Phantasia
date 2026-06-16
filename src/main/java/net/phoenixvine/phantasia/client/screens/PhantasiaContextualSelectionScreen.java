package net.phoenixvine.phantasia.client.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.common.data.guides.PhantasiaGuideData;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;

import java.util.ArrayList;
import java.util.List;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

@OnlyIn(Dist.CLIENT)
public class PhantasiaContextualSelectionScreen extends PhantasiaScreen {

    private final IPhantasiaMultiblockDefinition definition;
    private final List<PhantasiaSceneData> manualScenes;
    private final List<PhantasiaGuideData> guides;
    private final boolean hasScript;

    // Unified list mapping indices directly to horizontal card items
    private final List<ContextItem> items = new ArrayList<>();

    private static final int CARD_W = 104;
    private static final int CARD_H = 86;
    private static final int CARD_PAD = 12;
    private int hoveredIndex = -1;

    private enum ItemType {
        SCRIPT,
        SCENE,
        GUIDE
    }

    private record ContextItem(ItemType type, Object data) {}

    public PhantasiaContextualSelectionScreen(IPhantasiaMultiblockDefinition definition,
                                              List<PhantasiaSceneData> manualScenes,
                                              List<PhantasiaGuideData> guides,
                                              boolean hasScript) {
        super(Component.translatable("screen.phantasia.context_selection.title"));
        this.definition = definition;
        this.manualScenes = manualScenes;
        this.guides = guides;
        this.hasScript = hasScript;

        // Compile horizontal display elements
        if (hasScript) items.add(new ContextItem(ItemType.SCRIPT, definition));
        for (PhantasiaSceneData scene : manualScenes) items.add(new ContextItem(ItemType.SCENE, scene));
        for (PhantasiaGuideData guide : guides) items.add(new ContextItem(ItemType.GUIDE, guide));
    }

    @Override
    public void hideAllInputs() {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        // Render dynamic background vignette gradient
        g.fillGradient(0, 0, this.width, this.height, C_BG(), (0xFF << 24) | (C_PANEL() & 0x00FFFFFF));

        // Subtitle Header
        String name = "";
        if (definition != null) {
            name = definition.getDisplayName();
            if (name == null || name.isEmpty()) {
                name = org.apache.commons.lang3.text.WordUtils
                        .capitalizeFully(definition.getId().getPath().replace('_', ' '));
            }
        }
        if (!name.isEmpty()) g.drawCenteredString(font, name, this.width / 2, this.height / 2 - 70, C_ACCENT());
        g.drawCenteredString(font, Component.translatable("screen.phantasia.context_selection.subtitle").getString(),
                this.width / 2, this.height / 2 - 58, C_DIM());

        // Center card grid bounds
        int totalW = items.size() * CARD_W + (items.size() - 1) * CARD_PAD;
        int startX = (this.width - totalW) / 2;
        int startY = (this.height - CARD_H) / 2 - 10;

        hoveredIndex = -1;

        // Render matched entries in a centered layout row
        for (int i = 0; i < items.size(); i++) {
            int cx = startX + i * (CARD_W + CARD_PAD);
            int cy = startY;

            boolean hov = mx >= cx && mx < cx + CARD_W && my >= cy && my < cy + CARD_H;
            if (hov) hoveredIndex = i;

            ContextItem item = items.get(i);
            renderContextCard(g, mx, my, item, cx, cy, hov);
        }

        // Render back button helper at footer edge
        int bw = 80, bh = 16;
        int bx = (this.width - bw) / 2, by = this.height / 2 + CARD_H / 2 + 20;
        boolean bHov = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
        g.fill(bx, by, bx + bw, by + bh, bHov ? C_BTN_HOV() : C_BTN());
        if (bHov) g.fill(bx, by, bx + bw, by + 1, C_ACCENT());
        g.drawCenteredString(font, "✕ Close", this.width / 2, by + 4, bHov ? C_ACCENT() : C_TEXT());
    }

    private void renderContextCard(GuiGraphics g, int mx, int my, ContextItem item, int cx, int cy, boolean hovered) {
        int cardBg = (0xBB << 24) | (C_PANEL() & 0x00FFFFFF);
        int cardHoverBg = (0xBB << 24) | (C_BTN_HOV() & 0x00FFFFFF);
        g.fill(cx, cy, cx + CARD_W, cy + CARD_H, hovered ? cardHoverBg : cardBg);
        g.fill(cx, cy, cx + CARD_W, cy + 2, hovered ? C_ACCENT() : C_BORDER());

        if (hovered) {
            g.fill(cx, cy, cx + 1, cy + CARD_H, C_ACCENT());
            g.fill(cx + CARD_W - 1, cy, cx + CARD_W, cy + CARD_H, C_ACCENT());
            g.fill(cx, cy + CARD_H - 1, cx + CARD_W, cy + CARD_H, C_ACCENT());
        }

        // Setup custom visual configurations based on sub-item features
        String cardTitle = "";
        String badgeLabel = "";

        g.pose().pushPose();
        g.pose().translate(cx + (CARD_W - 32) / 2, cy + 10, 0);
        g.pose().scale(2f, 2f, 1f);

        switch (item.type()) {
            case SCRIPT -> {
                g.renderItem(definition.getIcon(), 0, 0);
                cardTitle = "Structure Script";
                badgeLabel = "§aReady";
            }
            case SCENE -> {
                PhantasiaSceneData scene = (PhantasiaSceneData) item.data();
                String res = scene.iconItem != null ? scene.iconItem : "minecraft:chest";
                var rl = res.contains(":") ? new net.minecraft.resources.ResourceLocation(res) :
                        new net.minecraft.resources.ResourceLocation("minecraft", res);
                var resolvedItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
                g.renderItem(new net.minecraft.world.item.ItemStack(
                        resolvedItem != null ? resolvedItem : net.minecraft.world.item.Items.CHEST), 0, 0);
                cardTitle = scene.name != null && !scene.name.isBlank() ? scene.name : "Custom Scene";
                badgeLabel = "🎬 Scene";
            }
            case GUIDE -> {
                PhantasiaGuideData guide = (PhantasiaGuideData) item.data();
                String res = guide.iconItem != null ? guide.iconItem : "minecraft:book";
                var rl = res.contains(":") ? new net.minecraft.resources.ResourceLocation(res) :
                        new net.minecraft.resources.ResourceLocation("minecraft", res);
                var resolvedItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
                g.renderItem(new net.minecraft.world.item.ItemStack(
                        resolvedItem != null ? resolvedItem : net.minecraft.world.item.Items.BOOK), 0, 0);
                cardTitle = guide.title != null && !guide.title.isBlank() ? guide.title : "Manual Guide";
                badgeLabel = "📖 Manual";
            }
        }
        g.pose().popPose();

        // Truncate names cleanly inside cards
        int maxW = CARD_W - 8;
        if (font.width(cardTitle) > maxW) {
            cardTitle = font.plainSubstrByWidth(cardTitle, maxW - font.width("...")) + "...";
        }

        g.drawCenteredString(font, cardTitle, cx + CARD_W / 2, cy + CARD_H - 32, hovered ? C_ACCENT() : C_TEXT());
        g.drawCenteredString(font, badgeLabel, cx + CARD_W / 2, cy + CARD_H - 18, C_DIM());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int bw = 80, bh = 16;
        int bx = (this.width - bw) / 2, by = this.height / 2 + CARD_H / 2 + 20;
        if (mx >= bx && mx < bx + bw && my >= by && my < by + bh) {
            onClose();
            return true;
        }

        if (hoveredIndex >= 0 && hoveredIndex < items.size()) {
            ContextItem selected = items.get(hoveredIndex);
            Minecraft mc = Minecraft.getInstance();

            switch (selected.type()) {
                case SCRIPT -> mc.setScreen(new PhantasiaSceneScreen(definition, null));
                case SCENE -> mc.setScreen(new PhantasiaSceneViewerScreen(null, (PhantasiaSceneData) selected.data()));
                case GUIDE -> mc.setScreen(new PhantasiaGuideScreen(null, (PhantasiaGuideData) selected.data()));
            }
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }
}
