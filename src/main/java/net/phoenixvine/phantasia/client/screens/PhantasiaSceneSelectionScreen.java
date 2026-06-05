package net.phoenixvine.phantasia.client.screens;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phantasia.common.PhantasiaSceneData;
import net.phoenixvine.phantasia.common.PhantasiaScenes;
import net.phoenixvine.phantasia.common.PhantasiaScript;
import net.phoenixvine.phantasia.common.PhantasiaScripts;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PhantasiaSceneSelectionScreen
 *
 * Card-grid selection screen with an active search filter. Each card shows:
 * - The controller block or configured scene icon as a 2D item sprite
 * - Machine name / Scene name
 * - Script step count (green dot = has custom script / elements)
 */
@OnlyIn(Dist.CLIENT)
public class PhantasiaSceneSelectionScreen extends Screen {

    public static final List<MultiblockMachineDefinition> PHANTASIA_SCENES = new ArrayList<>();

    // Runtime filtered list matching the search query
    private final List<MultiblockMachineDefinition> filteredScenes = new ArrayList<>();
    private final List<PhantasiaSceneData> filteredManualScenes = new ArrayList<>();

    private enum Tab {
        MULTIBLOCKS,
        SCENES
    }

    private Tab activeTab = Tab.MULTIBLOCKS;

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int C_BG = 0xFF080810;
    private static final int C_BG_BOT = 0xFF0B0B18;
    private static final int C_ACCENT = 0xFF4FC3F7;
    private static final int C_CARD = 0xBB111128;
    private static final int C_CARD_HOV = 0xBB182040;
    private static final int C_TEXT = 0xFFDDDDDD;
    private static final int C_DIM = 0xFF667788;
    private static final int C_SCRIPT = 0xFF66BB6A;
    private static final int C_BTN = 0xBB151530;
    private static final int C_BTN_HOV = 0xBB1A2840;

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int CARD_W = 104;
    private static final int CARD_H = 86;
    private static final int CARD_PAD = 8;
    private static final int COLS = 3;
    private static final int HEADER_H = 52;
    private static final int TAB_H = 16;
    private static final int SEARCH_H = 24;
    private static final int FOOTER_H = 30;

    private final Screen parent;
    private EditBox searchBox;
    private int scrollOffset = 0; // in rows
    private int hoveredCard = -1;

    public PhantasiaSceneSelectionScreen(Screen parent) {
        super(Component.literal("Phantasia"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        // Compute search bar width matching the exact layout grid width
        int totalGridW = COLS * CARD_W + (COLS - 1) * CARD_PAD;
        int searchX = (this.width - totalGridW) / 2;
        int searchY = HEADER_H + 4;

        // Initialize Minecraft's built-in text field widget
        this.searchBox = new EditBox(this.font, searchX, searchY, totalGridW, 16, Component.literal("Search..."));
        this.searchBox.setHint(Component.literal("Search machines...").withStyle(style -> style.withColor(0xFF888888)));
        this.searchBox.setBordered(true);
        this.searchBox.setMaxLength(32);

        // Listen for typing events to update results actively
        this.searchBox.setResponder(this::onSearchChanged);

        this.addWidget(this.searchBox);
        this.setInitialFocus(this.searchBox);

        // Re-run filter on init to capture initial list state or retain previous queries
        updateFilteredList();
    }

    private void onSearchChanged(String query) {
        updateFilteredList();
    }

    private void updateFilteredList() {
        String query = searchBox != null ? searchBox.getValue().toLowerCase(Locale.ROOT).trim() : "";

        // Multiblocks
        filteredScenes.clear();
        for (MultiblockMachineDefinition def : PHANTASIA_SCENES) {
            if (query.isEmpty()) {
                filteredScenes.add(def);
                continue;
            }
            String name = def.getLangValue();
            if (name != null && name.toLowerCase(Locale.ROOT).contains(query)) {
                filteredScenes.add(def);
                continue;
            }
            String idPath = def.getId().getPath().replace('_', ' ');
            if (idPath.toLowerCase(Locale.ROOT).contains(query)) filteredScenes.add(def);
        }

        // Manual scenes
        filteredManualScenes.clear();
        for (PhantasiaSceneData scene : PhantasiaScenes.all()) {
            if (query.isEmpty()) {
                filteredManualScenes.add(scene);
                continue;
            }
            if (scene.name != null && scene.name.toLowerCase(Locale.ROOT).contains(query)) {
                filteredManualScenes.add(scene);
                continue;
            }
            if (scene.id != null && scene.id.toLowerCase(Locale.ROOT).contains(query)) filteredManualScenes.add(scene);
        }

        this.scrollOffset = 0;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Rendering
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fillGradient(0, 0, this.width, this.height, C_BG, C_BG_BOT);
        renderHeader(g, mx, my);

        if (this.searchBox != null)
            this.searchBox.render(g, mx, my, partial);

        if (activeTab == Tab.MULTIBLOCKS) renderCards(g, mx, my);
        else renderSceneCards(g, mx, my);

        renderFooter(g, mx, my);
    }

    private void renderHeader(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, this.width, HEADER_H, 0xCC0A0A14);
        g.fill(0, HEADER_H - 2, this.width, HEADER_H, C_ACCENT);
        g.drawCenteredString(font, "\u2736 Phantasia", this.width / 2, 8, C_ACCENT);
        g.drawCenteredString(font, "Select a Multiblock or Scene to Explore", this.width / 2, 20, C_DIM);

        // Tab row
        int tabY = 32;
        int totalGridW = COLS * CARD_W + (COLS - 1) * CARD_PAD;
        int tabStartX = (this.width - totalGridW) / 2;

        renderTab(g, mx, my, tabStartX, tabY, "Multiblocks", Tab.MULTIBLOCKS);
        renderTab(g, mx, my, tabStartX + 104, tabY, "Scenes", Tab.SCENES);
    }

    private void renderTab(GuiGraphics g, int mx, int my, int x, int y, String label, Tab tab) {
        int w = font.width(label) + 16;
        boolean act = (activeTab == tab);
        boolean hov = isOver(mx, my, x, y, w, TAB_H);
        g.fill(x, y, x + w, y + TAB_H, act ? C_BTN_HOV : (hov ? C_BTN_HOV : C_BTN));
        if (act) g.fill(x, y + TAB_H - 2, x + w, y + TAB_H, C_ACCENT);
        g.drawString(font, label, x + 8, y + 4, act ? C_ACCENT : C_DIM, false);
    }

    private void renderCards(GuiGraphics g, int mx, int my) {
        int totalW = COLS * CARD_W + (COLS - 1) * CARD_PAD;
        int startX = (this.width - totalW) / 2;
        int startY = HEADER_H + SEARCH_H + 6;
        int maxRows = visibleRows();

        hoveredCard = -1;

        if (filteredScenes.isEmpty()) {
            g.drawCenteredString(font, "No matching machines found", this.width / 2, startY + 20, C_DIM);
            return;
        }

        for (int i = 0; i < filteredScenes.size(); i++) {
            int row = i / COLS - scrollOffset;
            int col = i % COLS;
            if (row < 0 || row >= maxRows) continue;

            int cx = startX + col * (CARD_W + CARD_PAD);
            int cy = startY + row * (CARD_H + CARD_PAD);

            boolean hov = mx >= cx && mx < cx + CARD_W && my >= cy && my < cy + CARD_H;
            if (hov) hoveredCard = i;

            renderCard(g, mx, my, filteredScenes.get(i), cx, cy, hov);
        }
    }

    private void renderCard(GuiGraphics g, int mx, int my,
                            MultiblockMachineDefinition def,
                            int cx, int cy, boolean hovered) {
        // 1. Card background and borders
        g.fill(cx, cy, cx + CARD_W, cy + CARD_H, hovered ? C_CARD_HOV : C_CARD);
        g.fill(cx, cy, cx + CARD_W, cy + 2, hovered ? C_ACCENT : 0x664FC3F7);
        if (hovered) {
            g.fill(cx, cy, cx + 1, cy + CARD_H, C_ACCENT);
            g.fill(cx + CARD_W - 1, cy, cx + CARD_W, cy + CARD_H, C_ACCENT);
            g.fill(cx, cy + CARD_H - 1, cx + CARD_W, cy + CARD_H, C_ACCENT);
        }

        // 2. Block icon (2D Item Sprite)
        Block block = def.getBlock();
        if (block != null) {
            ItemStack stack = new ItemStack(block);
            int iconSize = 32;
            int iconX = cx + (CARD_W - iconSize) / 2;
            int iconY = cy + 6;

            g.pose().pushPose();
            g.pose().translate(iconX, iconY, 0);
            g.pose().scale(2f, 2f, 1f);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
        }

        // 3. Machine name with Truncation and Fallback
        String name = def.getLangValue();

        if (name == null || name.isEmpty() || name.contains("gtceu.multiblock.")) {
            name = def.getId().getPath().replace('_', ' ');
            name = org.apache.commons.lang3.text.WordUtils.capitalizeFully(name);
        }

        int maxWidth = CARD_W - 8;
        int nameY = cy + CARD_H - 22;

        if (font.width(name) > maxWidth) {
            name = font.plainSubstrByWidth(name, maxWidth - font.width("...")) + "...";
        }

        g.drawString(font, name, cx + 4, nameY, hovered ? C_ACCENT : C_TEXT, false);

        // 4. Script info
        boolean hasScript = PhantasiaScripts.has(def);
        if (hasScript) {
            g.fill(cx + CARD_W - 8, cy + 4, cx + CARD_W - 4, cy + 8, C_SCRIPT);
            PhantasiaScript script = PhantasiaScripts.get(def);
            String steps = script.getSteps().size() + " steps";
            g.drawString(font, steps, cx + 4, cy + CARD_H - 10, C_DIM, false);
        } else {
            g.drawString(font, "No script", cx + 4, cy + CARD_H - 10, C_DIM, false);
        }
    }

    private void renderSceneCards(GuiGraphics g, int mx, int my) {
        int totalW = COLS * CARD_W + (COLS - 1) * CARD_PAD;
        int startX = (this.width - totalW) / 2;
        int startY = HEADER_H + SEARCH_H + 6;
        int maxRows = visibleRows();

        hoveredCard = -1;

        // "＋ New Scene" card is always first
        int newCardRow = 0 - scrollOffset;
        int newCardCol = 0;
        if (newCardRow >= 0 && newCardRow < maxRows) {
            int cx = startX + newCardCol * (CARD_W + CARD_PAD);
            int cy = startY + newCardRow * (CARD_H + CARD_PAD);
            boolean hov = isOver(mx, my, cx, cy, CARD_W, CARD_H);
            if (hov) hoveredCard = -2;
            renderNewSceneCard(g, cx, cy, hov);
        }

        // Existing scene cards (offset by 1 for the new-scene card)
        for (int i = 0; i < filteredManualScenes.size(); i++) {
            int slot = i + 1;
            int row = slot / COLS - scrollOffset;
            int col = slot % COLS;
            if (row < 0 || row >= maxRows) continue;

            int cx = startX + col * (CARD_W + CARD_PAD);
            int cy = startY + row * (CARD_H + CARD_PAD);
            boolean hov = isOver(mx, my, cx, cy, CARD_W, CARD_H);
            if (hov) hoveredCard = i;

            renderSceneCard(g, mx, my, filteredManualScenes.get(i), cx, cy, hov);
        }
    }

    private void renderNewSceneCard(GuiGraphics g, int cx, int cy, boolean hov) {
        g.fill(cx, cy, cx + CARD_W, cy + CARD_H, hov ? C_CARD_HOV : C_CARD);
        g.fill(cx, cy, cx + CARD_W, cy + 2, hov ? C_ACCENT : 0x334FC3F7);
        if (hov) {
            g.fill(cx, cy, cx + 1, cy + CARD_H, C_ACCENT);
            g.fill(cx + CARD_W - 1, cy, cx + CARD_W, cy + CARD_H, C_ACCENT);
            g.fill(cx, cy + CARD_H - 1, cx + CARD_W, cy + CARD_H, C_ACCENT);
        }
        g.drawCenteredString(font, "+", cx + CARD_W / 2, cy + 28, hov ? C_ACCENT : C_DIM);
        g.drawCenteredString(font, "New Scene", cx + CARD_W / 2, cy + CARD_H - 20, hov ? C_ACCENT : C_DIM);
    }

    private void renderSceneCard(GuiGraphics g, int mx, int my,
                                 PhantasiaSceneData scene, int cx, int cy, boolean hov) {
        g.fill(cx, cy, cx + CARD_W, cy + CARD_H, hov ? C_CARD_HOV : C_CARD);
        g.fill(cx, cy, cx + CARD_W, cy + 2, hov ? C_ACCENT : 0x664FC3F7);
        if (hov) {
            g.fill(cx, cy, cx + 1, cy + CARD_H, C_ACCENT);
            g.fill(cx + CARD_W - 1, cy, cx + CARD_W, cy + CARD_H, C_ACCENT);
            g.fill(cx, cy + CARD_H - 1, cx + CARD_W, cy + CARD_H, C_ACCENT);
        }

        // 2. Scene Icon rendering pipeline using Forge registries
        String iconRes = scene.iconItem != null ? scene.iconItem : "minecraft:chest";
        ResourceLocation rl = iconRes.contains(":") ?
                new ResourceLocation(iconRes) :
                new ResourceLocation("minecraft", iconRes);

        Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null || item == Items.AIR) {
            item = Items.CHEST; // Safe visualization fallback boundary
        }

        ItemStack stack = new ItemStack(item);
        int iconSize = 32;
        int iconX = cx + (CARD_W - iconSize) / 2;
        int iconY = cy + 6;

        g.pose().pushPose();
        g.pose().translate(iconX, iconY, 0);
        g.pose().scale(2f, 2f, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();

        // 3. Name
        String name = scene.name != null && !scene.name.isBlank() ? scene.name : scene.id;
        int maxWidth = CARD_W - 8;
        if (font.width(name) > maxWidth)
            name = font.plainSubstrByWidth(name, maxWidth - font.width("...")) + "...";
        g.drawString(font, name, cx + 4, cy + CARD_H - 22, hov ? C_ACCENT : C_TEXT, false);

        // 4. Subtitle Machine and Step Counts
        int count = scene.placements.size();
        String countStr = count + " machine" + (count == 1 ? "" : "s");
        g.drawString(font, countStr, cx + 4, cy + CARD_H - 11, C_DIM, false);

        // Green notifier dot if manual configuration holds components
        if (!scene.placements.isEmpty())
            g.fill(cx + CARD_W - 8, cy + 4, cx + CARD_W - 4, cy + 8, C_SCRIPT);
    }

    private void renderFooter(GuiGraphics g, int mx, int my) {
        int fy = this.height - FOOTER_H;
        g.fill(0, fy, this.width, this.height, 0xCC0A0A14);
        g.fill(0, fy, this.width, fy + 1, 0x44FFFFFF);

        int itemCount = activeTab == Tab.MULTIBLOCKS ? filteredScenes.size() : filteredManualScenes.size() + 1;
        int totalRows = (itemCount + COLS - 1) / COLS;
        if (totalRows > visibleRows())
            g.drawCenteredString(font, "\u25B2 \u25BC  scroll to see more", this.width / 2, fy + 4, C_DIM);

        // Back button
        int bw = 80, bh = 18;
        int bx = (this.width - bw) / 2, by = fy + (FOOTER_H - bh) / 2;
        boolean bHov = isOver(mx, my, bx, by, bw, bh);
        g.fill(bx, by, bx + bw, by + bh, bHov ? C_BTN_HOV : C_BTN);
        if (bHov) {
            g.fill(bx, by, bx + bw, by + 1, C_ACCENT);
            g.fill(bx, by + bh - 1, bx + bw, by + bh, C_ACCENT);
        }
        g.drawString(font, "\u2190 Back", bx + (bw - font.width("\u2190 Back")) / 2, by + 5,
                bHov ? C_ACCENT : C_TEXT, false);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Input
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (this.searchBox != null && this.searchBox.mouseClicked(mx, my, btn))
            return true;

        // Tab clicks
        int tabY = 32;
        int totalGridW = COLS * CARD_W + (COLS - 1) * CARD_PAD;
        int tabStartX = (this.width - totalGridW) / 2;
        if (isOver((int) mx, (int) my, tabStartX, tabY, font.width("Multiblocks") + 16, TAB_H)) {
            if (activeTab != Tab.MULTIBLOCKS) {
                activeTab = Tab.MULTIBLOCKS;
                scrollOffset = 0;
                updateFilteredList();
            }
            return true;
        }
        if (isOver((int) mx, (int) my, tabStartX + 104, tabY, font.width("Scenes") + 16, TAB_H)) {
            if (activeTab != Tab.SCENES) {
                activeTab = Tab.SCENES;
                scrollOffset = 0;
                updateFilteredList();
            }
            return true;
        }

        // Back button
        int fy = this.height - FOOTER_H;
        int bw = 80, bh = 18;
        int bx = (this.width - bw) / 2, by = fy + (FOOTER_H - bh) / 2;
        if (isOver((int) mx, (int) my, bx, by, bw, bh)) {
            onClose();
            return true;
        }

        if (activeTab == Tab.MULTIBLOCKS) {
            if (hoveredCard >= 0 && hoveredCard < filteredScenes.size()) {
                Minecraft.getInstance().setScreen(
                        new PhantasiaSceneScreen(filteredScenes.get(hoveredCard), this));
                return true;
            }
        } else {
            if (hoveredCard == -2) {
                Minecraft.getInstance().setScreen(new PhantasiaSceneCreateScreen(this));
                return true;
            }
            if (hoveredCard >= 0 && hoveredCard < filteredManualScenes.size()) {
                Minecraft.getInstance().setScreen(
                        new PhantasiaSceneEditorScreen(this, filteredManualScenes.get(hoveredCard)));
                return true;
            }
        }

        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int itemCount = activeTab == Tab.MULTIBLOCKS ? filteredScenes.size() : filteredManualScenes.size() + 1;
        int totalRows = (itemCount + COLS - 1) / COLS;
        int maxScroll = Math.max(0, totalRows - visibleRows());
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + (delta > 0 ? -1 : 1)));
        return true;
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (this.searchBox != null && this.searchBox.keyPressed(kc, sc, mod)) {
            return true;
        }
        if (kc == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(kc, sc, mod);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.searchBox != null && this.searchBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int visibleRows() {
        return Math.max(1, (this.height - HEADER_H - SEARCH_H - FOOTER_H - 8) / (CARD_H + CARD_PAD));
    }

    private boolean isOver(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
