package net.phoenixvine.phantasia.client.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phantasia.client.screens.editors.PhantasiaGuideEditorScreen;
import net.phoenixvine.phantasia.client.screens.editors.PhantasiaSceneEditorScreen;
import net.phoenixvine.phantasia.client.screens.editors.PhantasiaThemeEditorScreen;
import net.phoenixvine.phantasia.client.tutorial.PhantasiaTutorials;
import net.phoenixvine.phantasia.client.tutorial.TutorialSequence;
import net.phoenixvine.phantasia.common.data.guides.PhantasiaGuideData;
import net.phoenixvine.phantasia.common.data.guides.PhantasiaGuideRegistry;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaScenes;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScript;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScripts;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.configs.PhantasiaConfigs;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

/**
 * PhantasiaSceneSelectionScreen
 *
 * Card-grid selection screen with an active search filter. Each card shows:
 * - The controller block or configured scene icon as a 2D item sprite
 * - Machine name / Scene name
 * - Script step count (green dot = has custom script / elements)
 */
@OnlyIn(Dist.CLIENT)
public class PhantasiaSceneSelectionScreen extends PhantasiaScreen {

    public static final List<IPhantasiaMultiblockDefinition> PHANTASIA_SCENES = new ArrayList<>();

    // Runtime filtered list matching the search query
    private final List<IPhantasiaMultiblockDefinition> filteredScenes = new ArrayList<>();
    private final List<PhantasiaSceneData> filteredManualScenes = new ArrayList<>();
    private final List<PhantasiaGuideData> filteredGuides = new ArrayList<>();

    private enum Tab {
        MULTIBLOCKS,
        SCENES,
        GUIDES,
        TUTORIALS,
        SETTINGS
    }

    private Tab activeTab = Tab.MULTIBLOCKS;

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int C_BG_BOT = 0xFF0B0B18;
    private static final int C_CARD = 0xBB111128;
    private static final int C_CARD_HOV = 0xBB182040;
    private static final int C_SCRIPT = 0xFF66BB6A;

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
    private int scrollOffset = 0; // in rows (card grid tabs)
    private int settingsScrollPx = 0; // pixel scroll for the settings panel
    private int tutPlayerScroll = 0;
    private int tutDevScroll = 0;
    private int hoveredCard = -1;

    /** null = All mods; otherwise filters multiblock tab to this namespace */
    private String modFilter = null;

    public PhantasiaSceneSelectionScreen(Screen parent) {
        super(Component.translatable("screen.phantasia.scene_selection.title"));
        this.parent = parent;
    }

    @Override
    public void hideAllInputs() {}

    @Override
    protected void init() {
        super.init();

        // Compute search bar width matching the exact layout grid width
        int totalGridW = COLS * CARD_W + (COLS - 1) * CARD_PAD;
        int searchX = (this.width - totalGridW) / 2;
        int searchY = HEADER_H + 4;

        // Initialize Minecraft's built-in text field widget
        this.searchBox = new EditBox(this.font, searchX, searchY, totalGridW, 16,
                Component.translatable("screen.phantasia.scene_selection.search_box"));
        this.searchBox.setHint(Component.translatable("screen.phantasia.scene_selection.search_hint")
                .withStyle(style -> style.withColor(0xFF888888)));
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
        for (IPhantasiaMultiblockDefinition def : PHANTASIA_SCENES) {
            if (modFilter != null && !modFilter.equals(def.getId().getNamespace())) continue;
            if (query.isEmpty()) {
                filteredScenes.add(def);
                continue;
            }
            String name = def.getDisplayName();
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

        // Guides
        filteredGuides.clear();
        for (PhantasiaGuideData guide : PhantasiaGuideRegistry.all()) {
            if (query.isEmpty()) {
                filteredGuides.add(guide);
                continue;
            }
            if (guide.title != null && guide.title.toLowerCase(Locale.ROOT).contains(query)) {
                filteredGuides.add(guide);
                continue;
            }
            if (guide.id != null && guide.id.toLowerCase(Locale.ROOT).contains(query)) filteredGuides.add(guide);
        }

        this.scrollOffset = 0;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Rendering
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fillGradient(0, 0, this.width, this.height, C_BG(), C_BG_BOT);
        renderHeader(g, mx, my);

        if (this.searchBox != null)
            this.searchBox.render(g, mx, my, partial);

        if (activeTab == Tab.MULTIBLOCKS) {
            renderModFilterPills(g, mx, my);
            renderCards(g, mx, my);
        } else if (activeTab == Tab.SCENES) renderSceneCards(g, mx, my);
        else if (activeTab == Tab.GUIDES) renderGuideCards(g, mx, my);
        else if (activeTab == Tab.TUTORIALS) renderTutorialCards(g, mx, my);
        else renderSettings(g, mx, my);

        renderFooter(g, mx, my);
    }

    /** Returns the left X of each tab (Multiblocks, Scenes, Guides, Tutorials, Settings), centered on screen. */
    private int[] computeTabXs() {
        String[] labels = { "Multiblocks", "Scenes", "Guides", "Tutorials", "⚙ Settings" };
        int gap = 6;
        int total = 0;
        for (String l : labels) total += font.width(l) + 16;
        total += gap * (labels.length - 1);
        int x = Math.max(4, (this.width - total) / 2);
        int[] xs = new int[labels.length];
        for (int i = 0; i < labels.length; i++) {
            xs[i] = x;
            x += font.width(labels[i]) + 16 + gap;
        }
        return xs;
    }

    private void renderHeader(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, this.width, HEADER_H, 0xCC0A0A14);
        g.fill(0, HEADER_H - 2, this.width, HEADER_H, C_ACCENT());
        g.drawCenteredString(font, "\u2736 Phantasia", this.width / 2, 8, C_ACCENT());
        g.drawCenteredString(font, Component.translatable("screen.phantasia.scene_selection.subtitle").getString(),
                this.width / 2, 20, C_DIM());

        // Tab row — centered on actual screen width so tabs don't clip at narrow GUI scales.
        int tabY = 32;
        int[] txs = computeTabXs();
        renderTab(g, mx, my, txs[0], tabY, "Multiblocks", Tab.MULTIBLOCKS);
        renderTab(g, mx, my, txs[1], tabY, "Scenes", Tab.SCENES);
        renderTab(g, mx, my, txs[2], tabY, "Guides", Tab.GUIDES);
        renderTab(g, mx, my, txs[3], tabY, "Tutorials", Tab.TUTORIALS);
        renderTab(g, mx, my, txs[4], tabY, "⚙ Settings", Tab.SETTINGS);
    }

    private void renderTab(GuiGraphics g, int mx, int my, int x, int y, String label, Tab tab) {
        int w = font.width(label) + 16;
        boolean act = (activeTab == tab);
        boolean hov = isOver(mx, my, x, y, w, TAB_H);
        g.fill(x, y, x + w, y + TAB_H, act ? C_BTN_HOV() : (hov ? C_BTN_HOV() : C_BTN()));
        if (act) g.fill(x, y + TAB_H - 2, x + w, y + TAB_H, C_ACCENT());
        g.drawString(font, label, x + 8, y + 4, act ? C_ACCENT() : C_DIM(), false);
    }

    @Nullable
    private List<String> cachedModNamespaces;

    private List<String> getModNamespaces() {
        if (cachedModNamespaces == null) {
            java.util.LinkedHashSet<String> ns = new java.util.LinkedHashSet<>();
            for (IPhantasiaMultiblockDefinition def : PHANTASIA_SCENES) {
                ns.add(def.getId().getNamespace());
            }
            cachedModNamespaces = new java.util.ArrayList<>(ns);
        }
        return cachedModNamespaces;
    }

    private void renderModFilterPills(GuiGraphics g, int mx, int my) {
        java.util.List<String> namespaces = getModNamespaces();
        if (namespaces.size() <= 1) return;

        int totalGridW = COLS * CARD_W + (COLS - 1) * CARD_PAD;
        int gridStartX = (this.width - totalGridW) / 2;

        int maxW = font.width("All") + 12;
        for (String ns : namespaces) {
            maxW = Math.max(maxW, font.width(formatModName(ns)) + 12);
        }

        int startX = gridStartX - maxW - 12;
        if (startX < 2) return;
        int py = HEADER_H + SEARCH_H + 6;
        // Reserve space for the footer and a potential "▼" overflow indicator.
        int maxPy = this.height - FOOTER_H - 18;

        // "All" pill
        String allLabel = "All";
        boolean allSel = modFilter == null;
        boolean allHov = isOver(mx, my, startX, py, maxW, 12);
        g.fill(startX, py, startX + maxW, py + 12, allSel ? C_BTN_ACT() : (allHov ? C_BTN_HOV() : C_BTN()));
        if (allSel) g.fill(startX, py, startX + 2, py + 12, C_ACCENT());
        g.drawString(font, allLabel, startX + 6, py + 2, allSel ? C_ACCENT() : C_TEXT(), false);
        py += 16;

        int hidden = 0;
        for (String ns : namespaces) {
            if (py > maxPy) {
                hidden++;
                continue;
            }
            String label = formatModName(ns);
            boolean sel = ns.equals(modFilter);
            boolean hov = isOver(mx, my, startX, py, maxW, 12);
            g.fill(startX, py, startX + maxW, py + 12, sel ? C_BTN_ACT() : (hov ? C_BTN_HOV() : C_BTN()));
            if (sel) g.fill(startX, py, startX + 2, py + 12, C_ACCENT());
            g.drawString(font, label, startX + 6, py + 2, sel ? C_ACCENT() : C_TEXT(), false);
            py += 16;
        }
        if (hidden > 0) {
            g.drawCenteredString(font, "▼ +" + hidden, startX + maxW / 2, py + 2, C_DIM());
        }
    }

    private boolean handleModFilterPillClick(int mx, int my) {
        java.util.List<String> namespaces = getModNamespaces();
        if (namespaces.size() <= 1) return false;

        int totalGridW = COLS * CARD_W + (COLS - 1) * CARD_PAD;
        int gridStartX = (this.width - totalGridW) / 2;

        int maxW = font.width("All") + 12;
        for (String ns : namespaces) {
            maxW = Math.max(maxW, font.width(formatModName(ns)) + 12);
        }

        int startX = gridStartX - maxW - 12;
        if (startX < 2) return false;
        int py = HEADER_H + SEARCH_H + 6;
        int maxPy = this.height - FOOTER_H - 18;

        if (isOver(mx, my, startX, py, maxW, 12)) {
            modFilter = null;
            scrollOffset = 0;
            updateFilteredList();
            return true;
        }
        py += 16;

        for (String ns : namespaces) {
            if (py > maxPy) break;
            if (isOver(mx, my, startX, py, maxW, 12)) {
                modFilter = ns.equals(modFilter) ? null : ns;
                scrollOffset = 0;
                updateFilteredList();
                return true;
            }
            py += 16;
        }
        return false;
    }

    private void renderCards(GuiGraphics g, int mx, int my) {
        int totalW = COLS * CARD_W + (COLS - 1) * CARD_PAD;
        int startX = (this.width - totalW) / 2;
        int startY = HEADER_H + SEARCH_H + 6; // Grid stays locked at the top bounds
        int maxRows = visibleRows();

        hoveredCard = -1;

        if (filteredScenes.isEmpty()) {
            g.drawCenteredString(font,
                    Component.translatable("screen.phantasia.scene_selection.no_results").getString(), this.width / 2,
                    startY + 20, C_DIM());
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
                            IPhantasiaMultiblockDefinition def,
                            int cx, int cy, boolean hovered) {
        int cardBg = (0xBB << 24) | (C_PANEL() & 0x00FFFFFF);
        int cardHoverBg = (0xBB << 24) | (C_BTN_HOV() & 0x00FFFFFF);
        g.fill(cx, cy, cx + CARD_W, cy + CARD_H, hovered ? cardHoverBg : cardBg);

        g.fill(cx, cy, cx + CARD_W, cy + 2, hovered ? C_ACCENT() : C_BORDER());
        if (hovered) {
            g.fill(cx, cy, cx + 1, cy + CARD_H, C_ACCENT());
            g.fill(cx + CARD_W - 1, cy, cx + CARD_W, cy + CARD_H, C_ACCENT());
            g.fill(cx, cy + CARD_H - 1, cx + CARD_W, cy + CARD_H, C_ACCENT());
        }

        // Block icon (2D Item Sprite)
        ItemStack icon = def.getIcon();
        if (!icon.isEmpty()) {
            int iconSize = 32;
            int iconX = cx + (CARD_W - iconSize) / 2;
            int iconY = cy + 6;

            g.pose().pushPose();
            g.pose().translate(iconX, iconY, 0);
            g.pose().scale(2f, 2f, 1f);
            g.renderItem(icon, 0, 0);
            g.pose().popPose();
        }

        // Machine name
        String name = def.getDisplayName();
        if (name == null || name.isEmpty()) {
            name = def.getId().getPath().replace('_', ' ');
            name = org.apache.commons.lang3.text.WordUtils.capitalizeFully(name);
        }

        int maxWidth = CARD_W - 8;
        int nameY = cy + CARD_H - 22;

        if (font.width(name) > maxWidth) {
            name = font.plainSubstrByWidth(name, maxWidth - font.width("...")) + "...";
        }

        g.drawString(font, name, cx + 4, nameY, hovered ? C_ACCENT() : C_TEXT(), false);

        // Script info (Green status dot switches to theme's progress feedback color)
        boolean hasScript = PhantasiaScripts.has(def);
        if (hasScript) {
            g.fill(cx + CARD_W - 8, cy + 4, cx + CARD_W - 4, cy + 8, C_GREEN());
            PhantasiaScript script = PhantasiaScripts.get(def);
            String steps = script.getSteps().size() + " steps";
            g.drawString(font, steps, cx + 4, cy + CARD_H - 10, C_DIM(), false);
        } else {
            g.drawString(font, Component.translatable("screen.phantasia.scene_selection.no_script").getString(), cx + 4,
                    cy + CARD_H - 10, C_DIM(), false);
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
        int cardBg = (0xBB << 24) | (C_PANEL() & 0x00FFFFFF);
        int cardHoverBg = (0xBB << 24) | (C_BTN_HOV() & 0x00FFFFFF);
        g.fill(cx, cy, cx + CARD_W, cy + CARD_H, hov ? cardHoverBg : cardBg);

        g.fill(cx, cy, cx + CARD_W, cy + 2, hov ? C_ACCENT() : C_BORDER());
        if (hov) {
            g.fill(cx, cy, cx + 1, cy + CARD_H, C_ACCENT());
            g.fill(cx + CARD_W - 1, cy, cx + CARD_W, cy + CARD_H, C_ACCENT());
            g.fill(cx, cy + CARD_H - 1, cx + CARD_W, cy + CARD_H, C_ACCENT());
        }
        g.drawCenteredString(font, "+", cx + CARD_W / 2, cy + 28, hov ? C_ACCENT() : C_DIM());
        g.drawCenteredString(font, Component.translatable("screen.phantasia.scene_selection.btn_new_scene").getString(),
                cx + CARD_W / 2, cy + CARD_H - 20, hov ? C_ACCENT() : C_DIM());
    }

    private void renderSceneCard(GuiGraphics g, int mx, int my,
                                 PhantasiaSceneData scene, int cx, int cy, boolean hov) {
        int cardBg = (0xBB << 24) | (C_PANEL() & 0x00FFFFFF);
        int cardHoverBg = (0xBB << 24) | (C_BTN_HOV() & 0x00FFFFFF);
        g.fill(cx, cy, cx + CARD_W, cy + CARD_H, hov ? cardHoverBg : cardBg);

        g.fill(cx, cy, cx + CARD_W, cy + 2, hov ? C_ACCENT() : C_BORDER());
        if (hov) {
            g.fill(cx, cy, cx + 1, cy + CARD_H, C_ACCENT());
            g.fill(cx + CARD_W - 1, cy, cx + CARD_W, cy + CARD_H, C_ACCENT());
            g.fill(cx, cy + CARD_H - 1, cx + CARD_W, cy + CARD_H, C_ACCENT());
        }

        // Icon
        String iconRes = scene.iconItem != null ? scene.iconItem : "minecraft:chest";
        ResourceLocation rl = iconRes.contains(":") ?
                new ResourceLocation(iconRes) :
                new ResourceLocation("minecraft", iconRes);
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null || item == Items.AIR) item = Items.CHEST;

        ItemStack stack = new ItemStack(item);
        int iconSize = 32;
        int iconX = cx + (CARD_W - iconSize) / 2;
        int iconY = cy + 6;
        g.pose().pushPose();
        g.pose().translate(iconX, iconY, 0);
        g.pose().scale(2f, 2f, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();

        // Name
        String name = scene.name != null && !scene.name.isBlank() ? scene.name : scene.id;
        int maxWidth = CARD_W - 8;
        if (font.width(name) > maxWidth)
            name = font.plainSubstrByWidth(name, maxWidth - font.width("...")) + "...";
        g.drawString(font, name, cx + 4, cy + CARD_H - 34, hov ? C_ACCENT() : C_TEXT(), false);

        // Machine/step count
        int count = scene.placements.size();
        String countStr = count + " machine" + (count == 1 ? "" : "s");
        int steps = scene.steps != null ? scene.steps.size() : 0;
        if (steps > 0) countStr += "  " + steps + " step" + (steps == 1 ? "" : "s");
        g.drawString(font, countStr, cx + 4, cy + CARD_H - 23, C_DIM(), false);

        if (!scene.placements.isEmpty())
            g.fill(cx + CARD_W - 8, cy + 4, cx + CARD_W - 4, cy + 8, C_GREEN());

        // ── Action buttons ───────────────────
        boolean hasGuide = scene.steps != null && scene.steps.stream()
                .anyMatch(s -> (s.caption != null && !s.caption.isBlank()) ||
                        (s.description != null && !s.description.isBlank()) || (s.showItems && scene.placements.stream()
                                .anyMatch(p -> !p.items.isEmpty())));

        int btnY = cy + CARD_H - 12;
        int btnH = 11;

        // View Button
        int viewW = font.width(Component.translatable("screen.phantasia.scene_selection.btn_view").getString()) + 6;
        int viewX = cx + 3;
        boolean viewHov = isOver(mx, my, viewX, btnY, viewW, btnH);

        int viewIdleBg = (0x44 << 24) | (C_PANEL() & 0x00FFFFFF);
        g.fill(viewX, btnY, viewX + viewW, btnY + btnH, viewHov ? C_BTN_HOV() : (hov ? cardBg : viewIdleBg));
        if (viewHov) g.fill(viewX, btnY, viewX + viewW, btnY + 1, C_ACCENT());
        g.drawString(font, Component.translatable("screen.phantasia.scene_selection.btn_view").getString(), viewX + 3,
                btnY + 2,
                viewHov ? C_ACCENT() : (hov ? C_TEXT() : C_DIM()), false);

        // Guide Button
        if (hasGuide) {
            int guideW = font.width(Component.translatable("screen.phantasia.scene_selection.btn_read").getString()) +
                    6;
            int guideX = cx + CARD_W - 3 - guideW;
            boolean guideHov = isOver(mx, my, guideX, btnY, guideW, btnH);

            int guideIdleBg = (0x44 << 24) | (C_PANEL() & 0x00FFFFFF);
            g.fill(guideX, btnY, guideX + guideW, btnY + btnH, guideHov ? C_BTN_HOV() : (hov ? cardBg : guideIdleBg));
            if (guideHov) g.fill(guideX, btnY, guideX + guideW, btnY + 1, C_ACCENT());
            g.drawString(font, Component.translatable("screen.phantasia.scene_selection.btn_read").getString(),
                    guideX + 3, btnY + 2,
                    guideHov ? C_ACCENT() : (hov ? C_TEXT() : C_DIM()), false);
        }
    }

    // ── Guide cards (Guides tab) ───────────────────────────────────────────────

    private void renderGuideCards(GuiGraphics g, int mx, int my) {
        int totalW = COLS * CARD_W + (COLS - 1) * CARD_PAD;
        int startX = (this.width - totalW) / 2;
        int startY = HEADER_H + SEARCH_H + 6;
        int maxRows = visibleRows();

        hoveredCard = -1;

        // "＋ New Guide" card always at index 0 in grid
        int newRow = 0 - scrollOffset;
        if (newRow >= 0 && newRow < maxRows) {
            int cx = startX;
            int cy = startY + newRow * (CARD_H + CARD_PAD);
            boolean hov = isOver(mx, my, cx, cy, CARD_W, CARD_H);
            if (hov) hoveredCard = -2;

            int cardBg = (0xBB << 24) | (C_PANEL() & 0x00FFFFFF);
            int cardHoverBg = (0xBB << 24) | (C_BTN_HOV() & 0x00FFFFFF);
            g.fill(cx, cy, cx + CARD_W, cy + CARD_H, hov ? cardHoverBg : cardBg);

            g.fill(cx, cy, cx + CARD_W, cy + 2, hov ? C_ACCENT() : C_BORDER());
            if (hov) {
                g.fill(cx, cy, cx + 1, cy + CARD_H, C_ACCENT());
                g.fill(cx + CARD_W - 1, cy, cx + CARD_W, cy + CARD_H, C_ACCENT());
                g.fill(cx, cy + CARD_H - 1, cx + CARD_W, cy + CARD_H, C_ACCENT());
            }
            g.drawCenteredString(font, "+", cx + CARD_W / 2, cy + 24, hov ? C_ACCENT() : C_DIM());
            g.drawCenteredString(font,
                    Component.translatable("screen.phantasia.scene_selection.btn_new_guide").getString(),
                    cx + CARD_W / 2, cy + CARD_H - 22,
                    hov ? C_ACCENT() : C_DIM());
        }

        if (filteredGuides.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.phantasia.scene_selection.no_guides").getString(),
                    this.width / 2, startY + 30, C_DIM());
            g.drawCenteredString(font,
                    Component.translatable("screen.phantasia.scene_selection.no_guides_hint").getString(),
                    this.width / 2, startY + 44, C_DIM());
        }

        for (int i = 0; i < filteredGuides.size(); i++) {
            int gridPos = i + 1; // +1 for New Guide card
            int row = gridPos / COLS - scrollOffset;
            int col = gridPos % COLS;
            if (row < 0 || row >= maxRows) continue;

            int cx = startX + col * (CARD_W + CARD_PAD);
            int cy = startY + row * (CARD_H + CARD_PAD);
            boolean hov = isOver(mx, my, cx, cy, CARD_W, CARD_H);
            if (hov) hoveredCard = i;

            renderGuideCard(g, mx, my, filteredGuides.get(i), cx, cy, hov);
        }
    }

    private void renderGuideCard(GuiGraphics g, int mx, int my,
                                 PhantasiaGuideData guide, int cx, int cy, boolean hov) {
        int cardBg = (0xBB << 24) | (C_PANEL() & 0x00FFFFFF);
        int cardHoverBg = (0xBB << 24) | (C_BTN_HOV() & 0x00FFFFFF);
        g.fill(cx, cy, cx + CARD_W, cy + CARD_H, hov ? cardHoverBg : cardBg);

        g.fill(cx, cy, cx + CARD_W, cy + 2, hov ? C_ACCENT() : C_BORDER());
        if (hov) {
            g.fill(cx, cy, cx + 1, cy + CARD_H, C_ACCENT());
            g.fill(cx + CARD_W - 1, cy, cx + CARD_W, cy + CARD_H, C_ACCENT());
            g.fill(cx, cy + CARD_H - 1, cx + CARD_W, cy + CARD_H, C_ACCENT());
        }

        // Icon
        String iconRes = guide.iconItem != null ? guide.iconItem : "minecraft:book";
        try {
            net.minecraft.resources.ResourceLocation rl = iconRes.contains(":") ?
                    new net.minecraft.resources.ResourceLocation(iconRes) :
                    new net.minecraft.resources.ResourceLocation("minecraft", iconRes);
            net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
            if (item == null || item == net.minecraft.world.item.Items.AIR)
                item = net.minecraft.world.item.Items.BOOK;
            int iconSize = 32;
            int iconX = cx + (CARD_W - iconSize) / 2;
            int iconY = cy + 8;
            g.pose().pushPose();
            g.pose().translate(iconX, iconY, 0);
            g.pose().scale(2f, 2f, 1f);
            g.renderItem(new net.minecraft.world.item.ItemStack(item), 0, 0);
            g.pose().popPose();
        } catch (Exception ignored) {}

        // Title
        String title = guide.title != null && !guide.title.isBlank() ? guide.title : guide.id;
        int maxW = CARD_W - 8;
        if (font.width(title) > maxW) title = font.plainSubstrByWidth(title, maxW - font.width("…")) + "…";
        g.drawString(font, title, cx + 4, cy + CARD_H - 33, hov ? C_ACCENT() : C_TEXT(), false);

        // Page count + tag
        int pages = guide.pages != null ? guide.pages.size() : 0;
        String sub = pages + " page" + (pages == 1 ? "" : "s");
        if (guide.tag != null && !guide.tag.isBlank()) sub += "  #" + guide.tag;
        g.drawString(font, sub, cx + 4, cy + CARD_H - 22, C_DIM(), false);

        // ── Open / Edit buttons at bottom ───────────────────────────────────────
        int btnY = cy + CARD_H - 12, btnH = 11;
        int buttonIdleBg = (0x44 << 24) | (C_PANEL() & 0x00FFFFFF);

        // Read Button
        int openW = font.width("📖 Read") + 6;
        boolean openHov = isOver(mx, my, cx + 3, btnY, openW, btnH);

        g.fill(cx + 3, btnY, cx + 3 + openW, btnY + btnH, openHov ? C_BTN_HOV() : (hov ? cardBg : buttonIdleBg));
        if (openHov) g.fill(cx + 3, btnY, cx + 3 + openW, btnY + 1, C_ACCENT());
        g.drawString(font, "📖 Read", cx + 6, btnY + 2, openHov ? C_ACCENT() : (hov ? C_TEXT() : C_DIM()), false);

        // Edit Button
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.getAbilities().instabuild) {
            int editW = font.width("✏") + 6;
            int editX = cx + CARD_W - 3 - editW;
            boolean editHov = isOver(mx, my, editX, btnY, editW, btnH);

            g.fill(editX, btnY, editX + editW, btnY + btnH, editHov ? C_BTN_HOV() : (hov ? cardBg : buttonIdleBg));
            if (editHov) g.fill(editX, btnY, editX + editW, btnY + 1, C_ACCENT());
            g.drawString(font, "✏", editX + 3, btnY + 2, editHov ? C_ACCENT() : (hov ? C_TEXT() : C_DIM()), false);
        }
    }

    // ── Settings layout constants (shared between render and click) ──────────────
    // All positions are relative to panelY. Computed once so render ↔ click never drift.
    // rowH=18 is the standard row height.
    private static final int S_ROW_H = 18;

    /**
     * Returns the y-positions of each interactive settings row.
     * panelY is the virtual top of the content (already offset by scroll).
     */
    private int[] settingsRowYs(int panelY) {
        int rh = S_ROW_H;
        int y = panelY + 12;
        // Camera section
        y += rh - 4;               // past section label
        int camY = y;              // [0] camera toggle
        y += rh;
        y += 3 * (rh - 4) + 4;    // past 3 wrapped desc lines + small gap
        int sensY = y;             // [1] camera sensitivity stepper
        y += rh;
        int zoomY = y;             // [2] zoom speed stepper
        y += rh + 8;               // + gap before border
        // Display section
        y += 8;                    // past border gap
        y += rh - 4;               // past section label
        int dispY = y;             // [3] display mode stepper
        y += rh;
        int ticksY = y;            // [4] activation ticks stepper
        y += rh;
        int autoPlayY = y;         // [5] auto-play toggle
        y += rh;
        int baseplateY = y;        // [6] show baseplate toggle
        y += rh + 8;
        // Performance section
        y += 8;                    // past border gap
        y += rh - 4;               // past section label
        int streamY = y;           // [7] streaming mode cycler
        y += rh;
        y += 2 * (rh - 4) + 8;    // past 2 desc lines + gap before border
        // Appearance section
        y += 8;                    // past border gap
        y += rh - 4;               // past section label
        int themeY = y;            // [8] theme editor button
        return new int[] { camY, sensY, zoomY, dispY, ticksY, autoPlayY, baseplateY, streamY, themeY };
    }

    /** Total pixel height of the settings content (used to cap scroll). */
    private int settingsContentH() {
        int[] ys = settingsRowYs(0); // relative to virtual panelY=0
        return ys[8] + 20 + 12;     // themeY + button height + bottom padding
    }

    private void renderSettings(GuiGraphics g, int mx, int my) {
        int totalGridW = COLS * CARD_W + (COLS - 1) * CARD_PAD;
        int panelX = (this.width - totalGridW) / 2;
        int panelYScreen = HEADER_H + SEARCH_H - 4;           // fixed screen position of panel top
        int panelW = totalGridW;
        int panelH = this.height - panelYScreen - FOOTER_H - 4;
        int rh = S_ROW_H;

        // Background + top border drawn at fixed screen coords
        g.fill(panelX, panelYScreen, panelX + panelW, panelYScreen + panelH, C_PANEL());
        g.fill(panelX, panelYScreen, panelX + panelW, panelYScreen + 1, C_BORDER());

        // Scrollbar
        int contentH = settingsContentH();
        if (contentH > panelH) {
            int trackH = panelH - 4;
            int thumbH = Math.max(20, trackH * panelH / contentH);
            int thumbY = panelYScreen + 2 + (trackH - thumbH) * settingsScrollPx / Math.max(1, contentH - panelH);
            g.fill(panelX + panelW - 4, panelYScreen + 2, panelX + panelW - 2, panelYScreen + panelH - 2, 0x33FFFFFF);
            g.fill(panelX + panelW - 4, thumbY, panelX + panelW - 2, thumbY + thumbH, 0xAAFFFFFF);
        }

        // Scissor to panel area so scrolled content doesn't bleed into header/footer
        com.mojang.blaze3d.platform.GlStateManager._enableScissorTest();
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        com.mojang.blaze3d.platform.GlStateManager._scissorBox(
                (int) (panelX * scale),
                (int) ((this.height - panelYScreen - panelH) * scale),
                (int) (panelW * scale),
                (int) (panelH * scale));

        // Virtual panelY is shifted upward by the scroll amount
        int panelY = panelYScreen - settingsScrollPx;
        int rowX = panelX + 12;

        PhantasiaConfigs.PhantasiaUIConfig cfg = PhantasiaConfigs.INSTANCE.phantasiaUI;
        int[] ys = settingsRowYs(panelY);
        int arrowW = font.width("◄") + 8;

        // ── Camera section ────────────────────────────────────────────────────
        g.drawString(font, Component.translatable("screen.phantasia.scene_selection.section_camera").getString(), rowX,
                panelY + 12, C_ACCENT(), false);

        renderToggleRow(g, mx, my, panelX, panelW, rowX, ys[0], "Camera follows steps", cfg.scriptLockCamera);
        String camDesc = cfg.scriptLockCamera ?
                "ON: scripts/steps drive the camera exclusively. The player cannot move it until they toggle the 🔒 lock button inside the viewer." :
                "OFF: camera is always yours — scripts and steps never move it. Use the 🔒 lock button to give control back to scripts.";
        int descMaxW = panelW - 28;
        java.util.List<net.minecraft.util.FormattedCharSequence> descLines = font
                .split(net.minecraft.network.chat.Component.literal(camDesc), descMaxW);
        int descY = ys[0] + rh;
        for (int li = 0; li < Math.min(descLines.size(), 3); li++) {
            g.drawString(font, descLines.get(li), rowX + 4, descY + li * (rh - 4), C_DIM(), false);
        }

        // Camera sensitivity stepper [1]
        String sensLabel = "Camera sensitivity  (" + String.format("%.2f", cfg.cameraSensitivity) + "x)";
        g.drawString(font, sensLabel, rowX + 4, ys[1] + 2, C_TEXT(), false);
        String sensStr = String.format("%.2f", cfg.cameraSensitivity);
        int sensW = arrowW + font.width(sensStr) + 8 + arrowW;
        int sensX = panelX + panelW - 12 - sensW;
        boolean sensHovL = isOver(mx, my, sensX, ys[1], arrowW, 13);
        boolean sensHovR = isOver(mx, my, sensX + sensW - arrowW, ys[1], arrowW, 13);
        g.fill(sensX, ys[1], sensX + sensW, ys[1] + 13, C_BTN());
        g.drawString(font, "◄", sensX + 4, ys[1] + 3, sensHovL ? C_ACCENT() : C_DIM(), false);
        g.drawCenteredString(font, sensStr, sensX + sensW / 2, ys[1] + 3, C_TEXT());
        g.drawString(font, "►", sensX + sensW - arrowW + 4, ys[1] + 3, sensHovR ? C_ACCENT() : C_DIM(), false);

        // Zoom speed stepper [2]
        String zoomLabel = "Scroll zoom speed  (" + String.format("%.2f", cfg.scrollZoomSpeed) + "x)";
        g.drawString(font, zoomLabel, rowX + 4, ys[2] + 2, C_TEXT(), false);
        String zoomStr = String.format("%.2f", cfg.scrollZoomSpeed);
        int zoomW = arrowW + font.width(zoomStr) + 8 + arrowW;
        int zoomX = panelX + panelW - 12 - zoomW;
        boolean zoomHovL = isOver(mx, my, zoomX, ys[2], arrowW, 13);
        boolean zoomHovR = isOver(mx, my, zoomX + zoomW - arrowW, ys[2], arrowW, 13);
        g.fill(zoomX, ys[2], zoomX + zoomW, ys[2] + 13, C_BTN());
        g.drawString(font, "◄", zoomX + 4, ys[2] + 3, zoomHovL ? C_ACCENT() : C_DIM(), false);
        g.drawCenteredString(font, zoomStr, zoomX + zoomW / 2, ys[2] + 3, C_TEXT());
        g.drawString(font, "►", zoomX + zoomW - arrowW + 4, ys[2] + 3, zoomHovR ? C_ACCENT() : C_DIM(), false);

        // ── Display section ───────────────────────────────────────────────────
        int borderY1 = ys[2] + rh + 8;  // matches settingsRowYs: zoomY + rh + 8
        g.fill(panelX + 6, borderY1, panelX + panelW - 6, borderY1 + 1, C_BORDER());
        g.drawString(font, Component.translatable("screen.phantasia.scene_selection.section_display").getString(), rowX,
                borderY1 + 8, C_ACCENT(), false);

        g.drawString(font, Component.translatable("screen.phantasia.scene_selection.label_display_mode").getString(),
                rowX + 4, ys[3] + 2, C_TEXT(), false);
        String dispStr = cfg.displayMode.name().replace('_', ' ');
        int dispW = arrowW + font.width(dispStr) + 8 + arrowW;
        int dispX = panelX + panelW - 12 - dispW;
        boolean dispHovL = isOver(mx, my, dispX, ys[3], arrowW, 13);
        boolean dispHovR = isOver(mx, my, dispX + dispW - arrowW, ys[3], arrowW, 13);
        g.fill(dispX, ys[3], dispX + dispW, ys[3] + 13, C_BTN());
        g.drawString(font, "◄", dispX + 4, ys[3] + 3, dispHovL ? C_ACCENT() : C_DIM(), false);
        g.drawCenteredString(font, dispStr, dispX + dispW / 2, ys[3] + 3, C_TEXT());
        g.drawString(font, "►", dispX + dispW - arrowW + 4, ys[3] + 3, dispHovR ? C_ACCENT() : C_DIM(), false);

        String tickLabel = "Activation hold  (" + cfg.activationTicks + " ticks = " +
                String.format("%.1f", cfg.activationTicks / 20f) + "s)";
        g.drawString(font, tickLabel, rowX + 4, ys[4] + 2, C_TEXT(), false);
        String tickStr = String.valueOf(cfg.activationTicks);
        int tickW = arrowW + font.width(tickStr) + 8 + arrowW;
        int tickX = panelX + panelW - 12 - tickW;
        boolean tickHovL = isOver(mx, my, tickX, ys[4], arrowW, 13);
        boolean tickHovR = isOver(mx, my, tickX + tickW - arrowW, ys[4], arrowW, 13);
        g.fill(tickX, ys[4], tickX + tickW, ys[4] + 13, C_BTN());
        g.drawString(font, "◄", tickX + 4, ys[4] + 3, tickHovL ? C_ACCENT() : C_DIM(), false);
        g.drawCenteredString(font, tickStr, tickX + tickW / 2, ys[4] + 3, C_TEXT());
        g.drawString(font, "►", tickX + tickW - arrowW + 4, ys[4] + 3, tickHovR ? C_ACCENT() : C_DIM(), false);

        renderToggleRow(g, mx, my, panelX, panelW, rowX, ys[5], "Auto-play scripts on open", cfg.autoPlayScripts);
        renderToggleRow(g, mx, my, panelX, panelW, rowX, ys[6], "Show floor baseplate in previews", cfg.showBaseplate);

        // ── Performance section ───────────────────────────────────────────────
        int borderY2 = ys[6] + rh + 8;
        g.fill(panelX + 6, borderY2, panelX + panelW - 6, borderY2 + 1, C_BORDER());
        g.drawString(font, Component.translatable("screen.phantasia.scene_selection.section_performance").getString(),
                rowX, borderY2 + 8, C_ACCENT(), false);

        g.drawString(font, Component.translatable("screen.phantasia.scene_selection.label_scene_streaming").getString(),
                rowX + 4, ys[7] + 2, C_TEXT(), false);
        String streamStr = cfg.streamingMode.name();
        int streamW = arrowW + font.width(streamStr) + 8 + arrowW;
        int streamX = panelX + panelW - 12 - streamW;
        boolean streamHovL = isOver(mx, my, streamX, ys[7], arrowW, 13);
        boolean streamHovR = isOver(mx, my, streamX + streamW - arrowW, ys[7], arrowW, 13);
        g.fill(streamX, ys[7], streamX + streamW, ys[7] + 13, C_BTN());
        g.drawString(font, "◄", streamX + 4, ys[7] + 3, streamHovL ? C_ACCENT() : C_DIM(), false);
        g.drawCenteredString(font, streamStr, streamX + streamW / 2, ys[7] + 3, C_TEXT());
        g.drawString(font, "►", streamX + streamW - arrowW + 4, ys[7] + 3, streamHovR ? C_ACCENT() : C_DIM(), false);
        String streamDesc = switch (cfg.streamingMode) {
            case PERFORMANCE -> "PERFORMANCE: starts displaying the scene sooner with fewer blocks baked. Reduces GPU spikes — best for weak hardware.";
            case BALANCED -> "BALANCED: waits for a moderate amount of baking before displaying. Good default for most machines.";
            case QUALITY -> "QUALITY: bakes as much as possible before displaying. Smoothest result but causes a longer initial load spike.";
        };
        java.util.List<net.minecraft.util.FormattedCharSequence> streamDescLines = font
                .split(net.minecraft.network.chat.Component.literal(streamDesc), descMaxW);
        int streamDescY = ys[7] + rh;
        for (int li = 0; li < Math.min(streamDescLines.size(), 2); li++) {
            g.drawString(font, streamDescLines.get(li), rowX + 4, streamDescY + li * (rh - 4), C_DIM(), false);
        }

        // ── Appearance section ────────────────────────────────────────────────
        int borderY3 = ys[7] + rh + 2 * (rh - 4) + 8;  // matches settingsRowYs
        g.fill(panelX + 6, borderY3, panelX + panelW - 6, borderY3 + 1, C_BORDER());
        g.drawString(font, Component.translatable("screen.phantasia.scene_selection.section_appearance").getString(),
                rowX, borderY3 + 8, C_ACCENT(), false);

        String themeLabel = "🎨 Open Theme Editor →";
        int themeBtnW = font.width(themeLabel) + 16;
        int themeBtnX = panelX + (panelW - themeBtnW) / 2;
        boolean themeHov = isOver(mx, my, themeBtnX, ys[8], themeBtnW, 16);
        g.fill(themeBtnX, ys[8], themeBtnX + themeBtnW, ys[8] + 16, themeHov ? C_BTN_HOV() : C_BTN());
        if (themeHov) {
            g.fill(themeBtnX, ys[8], themeBtnX + themeBtnW, ys[8] + 1, C_ACCENT());
            g.fill(themeBtnX, ys[8] + 15, themeBtnX + themeBtnW, ys[8] + 16, C_ACCENT());
        }
        g.drawCenteredString(font, themeLabel, themeBtnX + themeBtnW / 2, ys[8] + 4,
                themeHov ? C_ACCENT() : C_TEXT());

        com.mojang.blaze3d.platform.GlStateManager._disableScissorTest();
    }

    /** Render a labelled ON/OFF toggle row at the given y. */
    private void renderToggleRow(GuiGraphics g, int mx, int my,
                                 int panelX, int panelW, int rowX,
                                 int y, String label, boolean value) {
        g.drawString(font, label, rowX + 4, y + 2, C_TEXT(), false);
        String tog = value ? "ON" : "OFF";
        int togW = font.width(tog) + 10;
        int togX = panelX + panelW - 12 - togW;
        boolean hov = isOver(mx, my, togX, y, togW, 13);
        g.fill(togX, y, togX + togW, y + 13, hov ? C_BTN_HOV() : C_BTN());
        if (value) g.fill(togX, y, togX + 2, y + 13, C_GREEN());
        g.drawString(font, tog, togX + 5, y + 3, value ? C_GREEN() : C_DIM(), false);
    }

    private boolean handleSettingsClick(int mx, int my) {
        int totalGridW = COLS * CARD_W + (COLS - 1) * CARD_PAD;
        int panelX = (this.width - totalGridW) / 2;
        int panelW = totalGridW;
        int panelYScreen = HEADER_H + SEARCH_H - 4;
        int panelH = this.height - panelYScreen - FOOTER_H - 4;
        // Reject clicks outside the panel area
        if (my < panelYScreen || my > panelYScreen + panelH) return false;
        int panelY = panelYScreen - settingsScrollPx; // virtual top, shifted by scroll
        int arrowW = font.width("◄") + 8;

        PhantasiaConfigs.PhantasiaUIConfig cfg = PhantasiaConfigs.INSTANCE.phantasiaUI;
        int[] ys = settingsRowYs(panelY);

        // [0] Camera toggle
        int camBtnW = font.width(cfg.scriptLockCamera ? "ON" : "OFF") + 10;
        if (isOver(mx, my, panelX + panelW - 12 - camBtnW, ys[0], camBtnW, 13)) {
            cfg.scriptLockCamera = !cfg.scriptLockCamera;
            savePhantasiaConfig();
            return true;
        }

        // [1] Camera sensitivity
        String sensStr = String.format("%.2f", cfg.cameraSensitivity);
        int sensW = arrowW + font.width(sensStr) + 8 + arrowW;
        int sensX = panelX + panelW - 12 - sensW;
        if (isOver(mx, my, sensX, ys[1], arrowW, 13)) {
            cfg.cameraSensitivity = Math.round(Math.max(0.25f, cfg.cameraSensitivity - 0.25f) * 100f) / 100f;
            savePhantasiaConfig();
            return true;
        }
        if (isOver(mx, my, sensX + sensW - arrowW, ys[1], arrowW, 13)) {
            cfg.cameraSensitivity = Math.round(Math.min(3.0f, cfg.cameraSensitivity + 0.25f) * 100f) / 100f;
            savePhantasiaConfig();
            return true;
        }

        // [2] Scroll zoom speed
        String zoomStr = String.format("%.2f", cfg.scrollZoomSpeed);
        int zoomW = arrowW + font.width(zoomStr) + 8 + arrowW;
        int zoomX = panelX + panelW - 12 - zoomW;
        if (isOver(mx, my, zoomX, ys[2], arrowW, 13)) {
            cfg.scrollZoomSpeed = Math.round(Math.max(0.25f, cfg.scrollZoomSpeed - 0.25f) * 100f) / 100f;
            savePhantasiaConfig();
            return true;
        }
        if (isOver(mx, my, zoomX + zoomW - arrowW, ys[2], arrowW, 13)) {
            cfg.scrollZoomSpeed = Math.round(Math.min(3.0f, cfg.scrollZoomSpeed + 0.25f) * 100f) / 100f;
            savePhantasiaConfig();
            return true;
        }

        // [3] Display mode
        String dispStr = cfg.displayMode.name().replace('_', ' ');
        int dispW = arrowW + font.width(dispStr) + 8 + arrowW;
        int dispX = panelX + panelW - 12 - dispW;
        PhantasiaConfigs.PhantasiaUIConfig.DisplayMode[] dmVals = PhantasiaConfigs.PhantasiaUIConfig.DisplayMode
                .values();
        if (isOver(mx, my, dispX, ys[3], arrowW, 13)) {
            cfg.displayMode = dmVals[(cfg.displayMode.ordinal() - 1 + dmVals.length) % dmVals.length];
            savePhantasiaConfig();
            return true;
        }
        if (isOver(mx, my, dispX + dispW - arrowW, ys[3], arrowW, 13)) {
            cfg.displayMode = dmVals[(cfg.displayMode.ordinal() + 1) % dmVals.length];
            savePhantasiaConfig();
            return true;
        }

        // [4] Activation ticks
        int tickW = arrowW + font.width(String.valueOf(cfg.activationTicks)) + 8 + arrowW;
        int tickX = panelX + panelW - 12 - tickW;
        if (isOver(mx, my, tickX, ys[4], arrowW, 13)) {
            cfg.activationTicks = Math.max(1, cfg.activationTicks - 1);
            savePhantasiaConfig();
            return true;
        }
        if (isOver(mx, my, tickX + tickW - arrowW, ys[4], arrowW, 13)) {
            cfg.activationTicks = Math.min(200, cfg.activationTicks + 1);
            savePhantasiaConfig();
            return true;
        }

        // [5] Auto-play toggle
        int apW = font.width(cfg.autoPlayScripts ? "ON" : "OFF") + 10;
        if (isOver(mx, my, panelX + panelW - 12 - apW, ys[5], apW, 13)) {
            cfg.autoPlayScripts = !cfg.autoPlayScripts;
            savePhantasiaConfig();
            return true;
        }

        // [6] Show baseplate toggle
        int bpW = font.width(cfg.showBaseplate ? "ON" : "OFF") + 10;
        if (isOver(mx, my, panelX + panelW - 12 - bpW, ys[6], bpW, 13)) {
            cfg.showBaseplate = !cfg.showBaseplate;
            savePhantasiaConfig();
            return true;
        }

        // [7] Streaming mode
        PhantasiaConfigs.PhantasiaUIConfig.StreamingMode[] smVals = PhantasiaConfigs.PhantasiaUIConfig.StreamingMode
                .values();
        String streamStr = cfg.streamingMode.name();
        int streamW = arrowW + font.width(streamStr) + 8 + arrowW;
        int streamX = panelX + panelW - 12 - streamW;
        if (isOver(mx, my, streamX, ys[7], arrowW, 13)) {
            cfg.streamingMode = smVals[(cfg.streamingMode.ordinal() - 1 + smVals.length) % smVals.length];
            savePhantasiaConfig();
            return true;
        }
        if (isOver(mx, my, streamX + streamW - arrowW, ys[7], arrowW, 13)) {
            cfg.streamingMode = smVals[(cfg.streamingMode.ordinal() + 1) % smVals.length];
            savePhantasiaConfig();
            return true;
        }

        // [8] Theme editor button
        String themeLabel = "🎨 Open Theme Editor →";
        int themeBtnW = font.width(themeLabel) + 16;
        int themeBtnX = panelX + (panelW - themeBtnW) / 2;
        if (isOver(mx, my, themeBtnX, ys[8], themeBtnW, 16)) {
            Minecraft.getInstance().setScreen(new PhantasiaThemeEditorScreen(this));
            return true;
        }
        return false;
    }

    /** Writes current config values to the toma YAML file and syncs ConfigValue state. */
    private void savePhantasiaConfig() {
        try {
            PhantasiaConfigs.PhantasiaUIConfig ui = PhantasiaConfigs.INSTANCE.phantasiaUI;
            java.io.File configDir = new java.io.File(
                    Minecraft.getInstance().gameDirectory, "config");
            String ext = PhantasiaConfigs.CONFIG_HOLDER.getFormat().fileExt();
            String filename = PhantasiaConfigs.CONFIG_HOLDER.getFilename();
            java.io.File file = new java.io.File(configDir, filename + "." + ext);
            StringBuilder yaml = new StringBuilder();
            yaml.append("phantasiaUI:\n");
            yaml.append("  displayMode: ").append(ui.displayMode.name()).append("\n\n");
            yaml.append("  activationTicks: ").append(ui.activationTicks).append("\n\n");
            yaml.append("  scriptLockCamera: ").append(ui.scriptLockCamera).append("\n\n");
            yaml.append("  autoPlayScripts: ").append(ui.autoPlayScripts).append("\n\n");
            yaml.append("  showBaseplate: ").append(ui.showBaseplate).append("\n\n");
            yaml.append("  cameraSensitivity: ").append(ui.cameraSensitivity).append("\n\n");
            yaml.append("  scrollZoomSpeed: ").append(ui.scrollZoomSpeed).append("\n\n");
            yaml.append("  streamingMode: ").append(ui.streamingMode.name()).append("\n");
            java.nio.file.Files.writeString(file.toPath(), yaml.toString());
        } catch (Exception e) {
            net.phoenixvine.phantasia.Phantasia.LOGGER.warn("Failed to save Phantasia config: {}", e.getMessage());
        }
    }

    private void renderFooter(GuiGraphics g, int mx, int my) {
        int fy = this.height - FOOTER_H;
        g.fill(0, fy, this.width, this.height, 0xCC0A0A14);
        g.fill(0, fy, this.width, fy + 1, 0x44FFFFFF);

        int itemCount = activeTab == Tab.MULTIBLOCKS ? filteredScenes.size() :
                activeTab == Tab.GUIDES ? filteredGuides.size() + 1 : filteredManualScenes.size() + 1;
        int totalRows = (itemCount + COLS - 1) / COLS;
        if (totalRows > visibleRows())
            g.drawCenteredString(font,
                    Component.translatable("screen.phantasia.scene_selection.hint_scroll").getString(), this.width / 2,
                    fy + 4, C_DIM());

        // Back button \u2014 on Settings tab this returns to Multiblocks, otherwise closes the screen
        String backLabel = activeTab == Tab.SETTINGS ? "\u2190 Back to List" : "\u2190 Back";
        int bw = font.width(backLabel) + 24, bh = 18;
        int bx = (this.width - bw) / 2, by = fy + (FOOTER_H - bh) / 2;
        boolean bHov = isOver(mx, my, bx, by, bw, bh);
        g.fill(bx, by, bx + bw, by + bh, bHov ? C_BTN_HOV() : C_BTN());
        if (bHov) {
            g.fill(bx, by, bx + bw, by + 1, C_ACCENT());
            g.fill(bx, by + bh - 1, bx + bw, by + bh, C_ACCENT());
        }
        g.drawString(font, backLabel, bx + (bw - font.width(backLabel)) / 2, by + 5,
                bHov ? C_ACCENT() : C_TEXT(), false);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Input
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (this.searchBox != null && this.searchBox.mouseClicked(mx, my, btn))
            return true;

        // Tab clicks — positions must match renderHeader exactly.
        int tabY = 32;
        int[] txs = computeTabXs();
        String[] tabLabels = { "Multiblocks", "Scenes", "Guides", "Tutorials", "⚙ Settings" };
        if (isOver((int) mx, (int) my, txs[0], tabY, font.width(tabLabels[0]) + 16, TAB_H)) {
            if (activeTab != Tab.MULTIBLOCKS) {
                activeTab = Tab.MULTIBLOCKS;
                scrollOffset = 0;
                updateFilteredList();
            }
            return true;
        }
        if (isOver((int) mx, (int) my, txs[1], tabY, font.width(tabLabels[1]) + 16, TAB_H)) {
            if (activeTab != Tab.SCENES) {
                activeTab = Tab.SCENES;
                scrollOffset = 0;
                updateFilteredList();
            }
            return true;
        }
        if (isOver((int) mx, (int) my, txs[2], tabY, font.width(tabLabels[2]) + 16, TAB_H)) {
            if (activeTab != Tab.GUIDES) {
                activeTab = Tab.GUIDES;
                scrollOffset = 0;
                updateFilteredList();
            }
            return true;
        }
        if (isOver((int) mx, (int) my, txs[3], tabY, font.width(tabLabels[3]) + 16, TAB_H)) {
            if (activeTab != Tab.TUTORIALS) {
                activeTab = Tab.TUTORIALS;
                scrollOffset = 0;
                tutPlayerScroll = 0;
                tutDevScroll = 0;
            }
            return true;
        }
        if (isOver((int) mx, (int) my, txs[4], tabY, font.width(tabLabels[4]) + 16, TAB_H)) {
            if (activeTab != Tab.SETTINGS) {
                activeTab = Tab.SETTINGS;
                scrollOffset = 0;
                settingsScrollPx = 0;
            }
            return true;
        }

        // Settings tab interactions
        if (activeTab == Tab.SETTINGS) {
            if (handleSettingsClick((int) mx, (int) my)) return true;
        }

        // Back button — Settings tab goes back to Multiblocks, otherwise closes
        String backLabel = activeTab == Tab.SETTINGS ? "← Back to List" : "← Back";
        int fy = this.height - FOOTER_H;
        int bw = font.width(backLabel) + 24, bh = 18;
        int bx = (this.width - bw) / 2, by = fy + (FOOTER_H - bh) / 2;
        if (isOver((int) mx, (int) my, bx, by, bw, bh)) {
            if (activeTab == Tab.SETTINGS) {
                activeTab = Tab.MULTIBLOCKS;
                scrollOffset = 0;
                updateFilteredList();
            } else {
                onClose();
            }
            return true;
        }

        if (activeTab == Tab.MULTIBLOCKS) {
            if (handleModFilterPillClick((int) mx, (int) my)) return true;
            if (hoveredCard >= 0 && hoveredCard < filteredScenes.size()) {
                Minecraft.getInstance().setScreen(
                        new PhantasiaSceneScreen(filteredScenes.get(hoveredCard), this));
                return true;
            }
        } else if (activeTab == Tab.GUIDES) {
            if (hoveredCard == -2) {
                PhantasiaGuideData blank = PhantasiaGuideData.blank(
                        "phantasia:new_guide_" + System.currentTimeMillis(),
                        Component.translatable("screen.phantasia.scene_selection.btn_new_guide").getString(),
                        "minecraft:book");
                Minecraft.getInstance().setScreen(new PhantasiaGuideEditorScreen(this, blank));
                return true;
            }
            if (hoveredCard >= 0 && hoveredCard < filteredGuides.size()) {
                PhantasiaGuideData guide = filteredGuides.get(hoveredCard);

                // Hit-test the ✏ edit button (admin only)
                if (Minecraft.getInstance().player != null &&
                        Minecraft.getInstance().player.getAbilities().instabuild) {
                    int totalW = COLS * CARD_W + (COLS - 1) * CARD_PAD;
                    int startX = (this.width - totalW) / 2;
                    int startY = HEADER_H + SEARCH_H + 6;
                    int gridPos = hoveredCard + 1;
                    int row = gridPos / COLS - scrollOffset;
                    int col = gridPos % COLS;
                    int cx = startX + col * (CARD_W + CARD_PAD);
                    int cy = startY + row * (CARD_H + CARD_PAD);
                    int btnY = cy + CARD_H - 12, btnH = 11;
                    int editW = font.width("✏") + 6;
                    int editX = cx + CARD_W - 3 - editW;
                    if (isOver((int) mx, (int) my, editX, btnY, editW, btnH)) {
                        Minecraft.getInstance().setScreen(new PhantasiaGuideEditorScreen(this, guide));
                        return true;
                    }
                }
                Minecraft.getInstance().setScreen(new PhantasiaGuideScreen(this, guide));
                return true;
            }
        } else if (activeTab == Tab.SCENES) {
            if (hoveredCard == -2) {
                PhantasiaSceneData blank = PhantasiaSceneData.blank(
                        "phantasia:new_scene_" + System.currentTimeMillis(),
                        "New Scene",
                        "minecraft:chest");
                Minecraft.getInstance().setScreen(new PhantasiaSceneEditorScreen(this, blank));
                return true;
            }
            if (hoveredCard >= 0 && hoveredCard < filteredManualScenes.size()) {
                PhantasiaSceneData scene = filteredManualScenes.get(hoveredCard);

                int totalW = COLS * CARD_W + (COLS - 1) * CARD_PAD;
                int startX = (this.width - totalW) / 2;
                int startY = HEADER_H + SEARCH_H + 6;
                int gridPos = hoveredCard + 1;
                int row = gridPos / COLS - scrollOffset;
                int col = gridPos % COLS;
                int cx = startX + col * (CARD_W + CARD_PAD);
                int cy = startY + row * (CARD_H + CARD_PAD);

                int btnY = cy + CARD_H - 12;
                int btnH = 11;

                boolean hasGuide = scene.steps != null && scene.steps.stream()
                        .anyMatch(s -> (s.caption != null && !s.caption.isBlank()) ||
                                (s.description != null && !s.description.isBlank()) ||
                                (s.showItems && scene.placements.stream()
                                        .anyMatch(p -> !p.items.isEmpty())));
                if (hasGuide) {
                    int guideW = font
                            .width(Component.translatable("screen.phantasia.scene_selection.btn_read").getString()) + 6;
                    int guideX = cx + CARD_W - 3 - guideW;
                    if (isOver((int) mx, (int) my, guideX, btnY, guideW, btnH)) {
                        Minecraft.getInstance().setScreen(new PhantasiaGuideScreen(this, scene));
                        return true;
                    }
                }

                Minecraft.getInstance().setScreen(new PhantasiaSceneViewerScreen(this, scene));
                return true;
            }
        } else if (activeTab == Tab.TUTORIALS) {
            List<TutorialSequence> allTuts = PhantasiaTutorials.all();
            if (hoveredCard >= 0 && hoveredCard < allTuts.size()) {
                Minecraft.getInstance().setScreen(
                        new PhantasiaTutorialScreen(this, allTuts.get(hoveredCard)));
                return true;
            }
        }

        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (activeTab == Tab.SETTINGS) {
            int panelYScreen = HEADER_H + SEARCH_H - 4;
            int panelH = this.height - panelYScreen - FOOTER_H - 4;
            int maxScroll = Math.max(0, settingsContentH() - panelH);
            settingsScrollPx = Math.max(0, Math.min(maxScroll, settingsScrollPx + (delta > 0 ? -12 : 12)));
            return true;
        }
        if (activeTab == Tab.TUTORIALS) {
            int contentTop = HEADER_H + SEARCH_H + 6;
            int contentBot = this.height - FOOTER_H;
            int midY = (contentTop + contentBot) / 2;
            int labelH = font.lineHeight + 4;
            int stride = CARD_H + CARD_PAD;
            int dir = delta > 0 ? -1 : 1;
            if (my < midY) {
                long playerCount = PhantasiaTutorials.all().stream()
                        .filter(t -> TutorialSequence.PLAYER.equals(t.category)).count();
                int totalRows = (int) ((playerCount + COLS - 1) / COLS);
                int panelRows = Math.max(1, (midY - 3 - (contentTop + labelH + 4)) / stride);
                tutPlayerScroll = Math.max(0, Math.min(Math.max(0, totalRows - panelRows), tutPlayerScroll + dir));
            } else {
                long devCount = PhantasiaTutorials.all().stream()
                        .filter(t -> TutorialSequence.DEV.equals(t.category)).count();
                int totalRows = (int) ((devCount + COLS - 1) / COLS);
                int panelRows = Math.max(1, (contentBot - (midY + 2 + labelH + 4)) / stride);
                tutDevScroll = Math.max(0, Math.min(Math.max(0, totalRows - panelRows), tutDevScroll + dir));
            }
            return true;
        }
        int itemCount;
        if (activeTab == Tab.MULTIBLOCKS) itemCount = filteredScenes.size();
        else if (activeTab == Tab.GUIDES) itemCount = filteredGuides.size() + 1;
        else if (activeTab == Tab.SCENES) itemCount = filteredManualScenes.size() + 1;
        else itemCount = 0;
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

    // ── Tutorial cards ────────────────────────────────────────────────────────

    private void renderTutorialCards(GuiGraphics g, int mx, int my) {
        List<TutorialSequence> allTuts = PhantasiaTutorials.all();
        List<TutorialSequence> playerTuts = allTuts.stream()
                .filter(t -> TutorialSequence.PLAYER.equals(t.category)).toList();
        List<TutorialSequence> devTuts = allTuts.stream()
                .filter(t -> TutorialSequence.DEV.equals(t.category)).toList();

        int totalW = COLS * CARD_W + (COLS - 1) * CARD_PAD;
        int startX = (this.width - totalW) / 2;
        int contentTop = HEADER_H + SEARCH_H + 6;
        int contentBot = this.height - FOOTER_H;
        int midY = (contentTop + contentBot) / 2;

        hoveredCard = -1;

        // Player section — top half
        renderTutorialSection(g, mx, my, playerTuts, 0,
                "For Players", C_ACCENT(), false, startX, contentTop, midY - 3, tutPlayerScroll);

        // Divider
        g.fill(startX, midY - 1, startX + totalW, midY, 0x44FFFFFF);

        // Dev section — bottom half
        renderTutorialSection(g, mx, my, devTuts, playerTuts.size(),
                "For Pack Authors", C_WARN(), true, startX, midY + 2, contentBot, tutDevScroll);
    }

    private void renderTutorialSection(GuiGraphics g, int mx, int my,
                                       List<TutorialSequence> list, int indexOffset,
                                       String label, int labelColor, boolean isDev,
                                       int startX, int panelTop, int panelBot, int scroll) {
        int stride = CARD_H + CARD_PAD;
        int labelH = font.lineHeight + 4;
        int cardsTop = panelTop + labelH + 4;
        int panelRows = Math.max(1, (panelBot - cardsTop) / stride);
        int totalRows = (list.size() + COLS - 1) / COLS;

        g.drawString(font, label, startX, panelTop + 2, labelColor, false);

        g.enableScissor(0, cardsTop, this.width, panelBot);
        for (int i = 0; i < list.size(); i++) {
            TutorialSequence seq = list.get(i);
            int row = i / COLS - scroll;
            int col = i % COLS;
            if (row < 0 || row >= panelRows) continue;

            int cx = startX + col * (CARD_W + CARD_PAD);
            int cy = cardsTop + row * stride;
            boolean hov = isOver(mx, my, cx, cy, CARD_W, CARD_H) && my >= cardsTop && my < panelBot;
            if (hov) hoveredCard = indexOffset + i;

            g.fill(cx, cy, cx + CARD_W, cy + CARD_H, hov ? C_CARD_HOV : C_CARD);
            g.fill(cx, cy, cx + CARD_W, cy + 1, isDev ? C_WARN() : C_ACCENT());

            ResourceLocation iconRL = null;
            try {
                iconRL = new ResourceLocation(seq.iconItem);
            } catch (Exception ignored) {}
            if (iconRL != null) {
                Item iconItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(iconRL);
                if (iconItem != null && iconItem != Items.AIR)
                    g.renderItem(new ItemStack(iconItem), cx + 4, cy + 4);
            }

            var titleLines = font.split(seq.title, CARD_W - 26);
            int titleY = cy + 6;
            for (int tl = 0; tl < Math.min(titleLines.size(), 2); tl++, titleY += 9)
                g.drawString(font, titleLines.get(tl), cx + 22, titleY, isDev ? C_WARN() : C_ACCENT(), false);

            java.util.List<net.minecraft.util.FormattedCharSequence> descLines = font.split(seq.description,
                    CARD_W - 8);
            int ty = titleLines.size() >= 2 ? cy + 26 : cy + 20;
            int maxDescLines = titleLines.size() >= 2 ? 4 : 5;
            for (int li = 0; li < Math.min(descLines.size(), maxDescLines); li++)
                g.drawString(font, descLines.get(li), cx + 4, ty + li * 10, C_DIM(), false);

            g.drawString(font, seq.slides.size() + " slides", cx + 4, cy + CARD_H - 12, C_DIM(), false);
            if (isDev)
                g.drawString(font, Component.translatable("screen.phantasia.scene_selection.badge_dev").getString(),
                        cx + CARD_W - font.width(
                                Component.translatable("screen.phantasia.scene_selection.badge_dev").getString()) - 4,
                        cy + CARD_H - 12, C_DIM(), false);

            if (hov) {
                String btnLabel = "▶ Start";
                int bw = font.width(btnLabel) + 6;
                int bx2 = cx + CARD_W - bw - 4;
                int by2 = cy + CARD_H - 12;
                g.fill(bx2 - 1, by2 - 1, bx2 + bw + 1, by2 + 10, C_BTN());
                g.drawString(font, btnLabel, bx2 + 3, by2 + 1, C_ACCENT(), false);
            }
        }
        g.disableScissor();

        // Scroll overflow indicators — rendered outside scissor so they're always visible
        int totalW = COLS * CARD_W + (COLS - 1) * CARD_PAD;
        int indicatorX = startX + totalW / 2;
        if (scroll > 0) {
            g.drawCenteredString(font, "▲", indicatorX, cardsTop - font.lineHeight - 1, 0x88FFFFFF);
        }
        if (scroll + panelRows < totalRows) {
            g.drawCenteredString(font, "▼", indicatorX, panelBot - font.lineHeight - 1, 0x88FFFFFF);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int visibleRows() {
        return Math.max(1, (this.height - HEADER_H - SEARCH_H - FOOTER_H - 8) / (CARD_H + CARD_PAD));
    }

    private String formatModName(String ns) {
        if ("ars_nouveau".equals(ns)) return "Ars";
        if ("gtceu".equals(ns)) return "GT";
        return org.apache.commons.lang3.text.WordUtils.capitalizeFully(ns.replace('_', ' '));
    }
}
