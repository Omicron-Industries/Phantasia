package net.phoenixvine.phantasia.client.screens;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.common.PhantasiaLoadedPattern;

import java.util.*;

/**
 * PhantasiaMaterialCostScreen
 *
 * Opened from the "🧮 Materials" button in PhantasiaSceneScreen's side panel.
 * Requires EMI to be present — guarded by PhantasiaEmiPlugin.EMI_PRESENT before opening.
 *
 * Two tabs:
 *   BLOCKS      — every distinct block in the multiblock with total count + craft count
 *   INGREDIENTS — flattened raw ingredients resolved via EMI's recipe graph (one level deep),
 *                 so GregTech, modded, and shaped/shapeless recipes all resolve correctly.
 */
@OnlyIn(Dist.CLIENT)
public class PhantasiaMaterialCostScreen extends Screen {

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int C_BG      = 0xFF080810;
    private static final int C_BTN     = 0xBB151528;
    private static final int C_BTN_HOV = 0xBB1A2840;
    private static final int C_BTN_ACT = 0xBB0D3050;
    private static final int C_ACCENT  = 0xFF4FC3F7;
    private static final int C_TEXT    = 0xFFDDDDDD;
    private static final int C_DIM     = 0xFF667788;
    private static final int C_GREEN   = 0xFF66BB6A;

    private static final int ROW_H    = 22;
    private static final int HEADER_H = 50;
    private static final int FOOTER_H = 32;
    private static final int ICON_SZ  = 16;

    // ── Tabs ──────────────────────────────────────────────────────────────────
    private enum Tab { BLOCKS, INGREDIENTS }
    private Tab tab = Tab.BLOCKS;

    // ── State ─────────────────────────────────────────────────────────────────
    private final Screen parent;
    private final PhantasiaLoadedPattern pattern;

    private final LinkedHashMap<String, BlockEntry>      blockEntries;
    private final LinkedHashMap<String, IngredientEntry> ingredientEntries;

    private int scrollBlocks      = 0;
    private int scrollIngredients = 0;

    private ItemStack hoveredStack = ItemStack.EMPTY;
    private int hoveredX, hoveredY;

    // ── Data classes ──────────────────────────────────────────────────────────

    private static class BlockEntry {
        final String    displayName;
        final ItemStack icon;
        final int       needed;
        final int       craftCount;   // 0 = no recipe / direct item
        final int       craftOutput;  // items per craft run

        BlockEntry(String displayName, ItemStack icon, int needed, int craftCount, int craftOutput) {
            this.displayName = displayName;
            this.icon        = icon;
            this.needed      = needed;
            this.craftCount  = craftCount;
            this.craftOutput = craftOutput;
        }
    }

    private static class IngredientEntry {
        final String    displayName;
        final ItemStack icon;
        int             totalCount;

        IngredientEntry(String displayName, ItemStack icon, int totalCount) {
            this.displayName = displayName;
            this.icon        = icon;
            this.totalCount  = totalCount;
        }
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    public PhantasiaMaterialCostScreen(PhantasiaLoadedPattern pattern, Screen parent) {
        super(Component.literal("Material Cost"));
        this.parent  = parent;
        this.pattern = pattern;

        // Build a name → Block lookup from the pattern's block map.
        Map<String, Block> nameToBlock = new LinkedHashMap<>();
        for (BlockInfo info : pattern.blockMap.values()) {
            BlockState state = info.getBlockState();
            if (state == null || state.isAir()) continue;
            nameToBlock.putIfAbsent(state.getBlock().getName().getString(), state.getBlock());
        }

        // Resolve one-deep crafting recipe for each block's item via EMI
        Map<Item, ResolvedRecipe> itemToRecipe = resolveViaEmi(nameToBlock);

        // ── Block entries ─────────────────────────────────────────────────────
        blockEntries = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : pattern.shoppingList.entrySet()) {
            String name   = e.getKey();
            int    needed = e.getValue();
            Block  block  = nameToBlock.get(name);
            if (block == null) continue;

            Item           item    = block.asItem();
            ItemStack      icon    = (item == Items.AIR) ? ItemStack.EMPTY : new ItemStack(item);
            ResolvedRecipe recipe  = (item != Items.AIR) ? itemToRecipe.get(item) : null;
            int craftOutput = (recipe != null) ? recipe.outputCount : 1;
            int craftCount  = (recipe != null) ? (int) Math.ceil((double) needed / craftOutput) : 0;

            blockEntries.put(name, new BlockEntry(name, icon, needed, craftCount, craftOutput));
        }

        // ── Ingredient entries (one level deep) ───────────────────────────────
        ingredientEntries = new LinkedHashMap<>();
        for (BlockEntry be : blockEntries.values()) {
            if (be.craftCount == 0) {
                // No recipe — the item itself is the raw ingredient
                ingredientEntries
                        .computeIfAbsent(be.displayName,
                                k -> new IngredientEntry(be.displayName, be.icon, 0))
                        .totalCount += be.needed;
            } else {
                Item           item   = be.icon.isEmpty() ? Items.AIR : be.icon.getItem();
                ResolvedRecipe recipe = itemToRecipe.get(item);
                if (recipe == null) continue;
                for (Map.Entry<String, IngredientEntry> ing : recipe.ingredients.entrySet()) {
                    IngredientEntry src = ing.getValue();
                    ingredientEntries
                            .computeIfAbsent(ing.getKey(),
                                    k -> new IngredientEntry(src.displayName, src.icon, 0))
                            .totalCount += src.totalCount * be.craftCount;
                }
            }
        }

        // Sort by count desc
        List<Map.Entry<String, IngredientEntry>> sorted = new ArrayList<>(ingredientEntries.entrySet());
        sorted.sort((a, b) -> b.getValue().totalCount - a.getValue().totalCount);
        ingredientEntries.clear();
        sorted.forEach(e -> ingredientEntries.put(e.getKey(), e.getValue()));
    }

    // ── EMI recipe resolution ─────────────────────────────────────────────────

    /** Holds the output count and per-ingredient amounts for one EMI recipe. */
    private static class ResolvedRecipe {
        final int outputCount;
        /** ingredient display name → IngredientEntry (icon + per-craft count) */
        final LinkedHashMap<String, IngredientEntry> ingredients = new LinkedHashMap<>();

        ResolvedRecipe(int outputCount) { this.outputCount = outputCount; }
    }

    /**
     * For each block in nameToBlock, asks EMI for recipes that produce the block's
     * item form and picks the first result. Ingredients are read from
     * {@link EmiIngredient#getEmiStacks()} — this works for vanilla shaped/shapeless,
     * all JEI-compat recipes, and any recipe type with an EMI handler.
     */
    private Map<Item, ResolvedRecipe> resolveViaEmi(Map<String, Block> nameToBlock) {
        Map<Item, ResolvedRecipe> result = new HashMap<>();
        try {
            var emiRecipeManager = EmiApi.getRecipeManager();
            for (Block block : nameToBlock.values()) {
                Item item = block.asItem();
                if (item == Items.AIR) continue;
                if (result.containsKey(item)) continue;

                EmiStack target = EmiStack.of(item);
                List<EmiRecipe> recipes = emiRecipeManager.getRecipesByOutput(target);
                if (recipes.isEmpty()) continue;

                // Prefer the first recipe — EMI puts the most relevant first
                EmiRecipe recipe = recipes.get(0);

                // Output count: find the output stack that matches our item
                int outputCount = 1;
                for (EmiIngredient out : recipe.getOutputs()) {
                    for (EmiStack es : out.getEmiStacks()) {
                        if (!es.isEmpty() && es.getItemStack().getItem() == item) {
                            outputCount = Math.max(1, (int) es.getAmount());
                            break;
                        }
                    }
                }

                ResolvedRecipe resolved = new ResolvedRecipe(outputCount);

                // Ingredients: each slot is an EmiIngredient; use first stack as representative
                for (EmiIngredient ing : recipe.getInputs()) {
                    List<EmiStack> stacks = ing.getEmiStacks();
                    if (stacks.isEmpty()) continue;
                    EmiStack    rep      = stacks.get(0);
                    if (rep.isEmpty()) continue;
                    ItemStack   repStack = rep.getItemStack();
                    String      ingName  = repStack.getHoverName().getString();
                    int         perCraft = Math.max(1, (int) ing.getAmount());

                    resolved.ingredients
                            .computeIfAbsent(ingName,
                                    k -> new IngredientEntry(ingName, repStack.copy(), 0))
                            .totalCount += perCraft;
                }

                result.put(item, resolved);
            }
        } catch (Exception ignored) {
            // EMI not ready or recipe lookup failed — ingredient tab will be empty
        }
        return result;
    }

    // ── Screen rendering ──────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, C_BG);
        g.fill(0, 0, this.width, 1, C_ACCENT);
        g.fill(0, this.height - 1, this.width, this.height, C_ACCENT);

        hoveredStack = ItemStack.EMPTY;

        g.drawString(font, "Material Cost  —  " + pattern.shoppingList.size() + " block types",
                8, 8, C_ACCENT, false);

        renderTabs(g, mx, my);

        switch (tab) {
            case BLOCKS      -> renderBlocksTab(g, mx, my);
            case INGREDIENTS -> renderIngredientsTab(g, mx, my);
        }

        renderFooter(g, mx, my);

        if (!hoveredStack.isEmpty()) {
            g.renderTooltip(font, hoveredStack, hoveredX, hoveredY);
        }
    }

    // ── Tabs bar ──────────────────────────────────────────────────────────────

    private void renderTabs(GuiGraphics g, int mx, int my) {
        int tw = this.width / 2;
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab     t   = tabs[i];
            int     bx  = i * tw;
            boolean act = tab == t;
            boolean hov = isOver(mx, my, bx, 20, tw, 18);
            g.fill(bx, 20, bx + tw, 38, act ? C_BTN_ACT : (hov ? C_BTN_HOV : C_BTN));
            g.fill(bx, 37, bx + tw, 38, act ? C_ACCENT : C_DIM);
            String label = (t == Tab.BLOCKS)
                    ? "Blocks (" + blockEntries.size() + ")"
                    : "Ingredients (" + ingredientEntries.size() + ")";
            g.drawCenteredString(font, label, bx + tw / 2, 25, act ? C_ACCENT : C_TEXT);
        }
    }

    // ── BLOCKS tab ────────────────────────────────────────────────────────────

    private void renderBlocksTab(GuiGraphics g, int mx, int my) {
        int startY   = HEADER_H;
        int contentH = this.height - startY - FOOTER_H;
        g.fill(0, startY, this.width, startY + contentH, 0x22FFFFFF);

        int x = 8, w = this.width - 16;
        int y = startY - scrollBlocks;

        for (BlockEntry be : blockEntries.values()) {
            if (y + ROW_H >= startY && y <= startY + contentH) {
                boolean hov = isOver(mx, my, x, y, w, ROW_H - 2);
                g.fill(x, y, x + w, y + ROW_H - 2, hov ? C_BTN_HOV : 0);
                if (hov) g.fill(x, y, x + w, y + 1, 0x33FFFFFF);

                int cx = x + 2;

                // Count badge
                String neededStr = String.valueOf(be.needed);
                int bw = font.width(neededStr) + 8;
                g.fill(cx, y + 2, cx + bw, y + ROW_H - 4, 0xBB1A2840);
                g.drawString(font, neededStr, cx + 4, y + 6, C_ACCENT, false);
                cx += bw + 4;

                // Icon
                if (!be.icon.isEmpty()) {
                    g.renderFakeItem(be.icon, cx, y + 3);
                    if (hov && isOver(mx, my, cx, y + 3, ICON_SZ, ICON_SZ)) {
                        hoveredStack = be.icon; hoveredX = mx; hoveredY = my;
                    }
                    cx += ICON_SZ + 4;
                }

                // Name
                g.drawString(font, trunc(be.displayName, this.width - cx - 110), cx, y + 6, C_TEXT, false);

                // Craft info (right-aligned)
                if (be.craftCount > 0) {
                    String s = "\u00D7 " + be.craftCount + " craft" + (be.craftCount != 1 ? "s" : "")
                            + (be.craftOutput > 1 ? "  (\u2192" + be.craftOutput + ")" : "");
                    g.drawString(font, s, x + w - font.width(s) - 4, y + 6, C_GREEN, false);
                } else {
                    g.drawString(font, "direct", x + w - font.width("direct") - 4, y + 6, C_DIM, false);
                }
            }
            y += ROW_H;
        }
        renderScrollbar(g, startY, contentH, scrollBlocks, blockEntries.size() * ROW_H);
    }

    // ── INGREDIENTS tab ───────────────────────────────────────────────────────

    private void renderIngredientsTab(GuiGraphics g, int mx, int my) {
        int startY   = HEADER_H;
        int contentH = this.height - startY - FOOTER_H;
        g.fill(0, startY, this.width, startY + contentH, 0x22FFFFFF);

        if (ingredientEntries.isEmpty()) {
            g.drawCenteredString(font,
                    "EMI found no recipes for any blocks in this structure.",
                    this.width / 2, startY + 30, C_DIM);
            return;
        }

        int x = 8, w = this.width - 16;
        int y = startY - scrollIngredients;

        for (IngredientEntry ie : ingredientEntries.values()) {
            if (y + ROW_H >= startY && y <= startY + contentH) {
                boolean hov = isOver(mx, my, x, y, w, ROW_H - 2);
                g.fill(x, y, x + w, y + ROW_H - 2, hov ? C_BTN_HOV : 0);
                if (hov) g.fill(x, y, x + w, y + 1, 0x33FFFFFF);

                int cx = x + 2;

                // Count badge
                String cntStr = String.valueOf(ie.totalCount);
                int bw = font.width(cntStr) + 8;
                g.fill(cx, y + 2, cx + bw, y + ROW_H - 4, 0xBB1A2840);
                g.drawString(font, cntStr, cx + 4, y + 6, C_ACCENT, false);
                cx += bw + 4;

                // Icon
                if (!ie.icon.isEmpty()) {
                    g.renderFakeItem(ie.icon, cx, y + 3);
                    if (hov && isOver(mx, my, cx, y + 3, ICON_SZ, ICON_SZ)) {
                        hoveredStack = ie.icon; hoveredX = mx; hoveredY = my;
                    }
                    cx += ICON_SZ + 4;
                }

                g.drawString(font, trunc(ie.displayName, this.width - cx - 8), cx, y + 6, C_TEXT, false);
            }
            y += ROW_H;
        }
        renderScrollbar(g, startY, contentH, scrollIngredients, ingredientEntries.size() * ROW_H);
    }

    // ── Footer ────────────────────────────────────────────────────────────────

    private void renderFooter(GuiGraphics g, int mx, int my) {
        int fy = this.height - FOOTER_H;
        g.fill(0, fy, this.width, fy + 1, C_ACCENT);
        int bw = 80, bh = 16;
        int bx = this.width / 2 - bw / 2, by = fy + (FOOTER_H - bh) / 2;
        boolean hov = isOver(mx, my, bx, by, bw, bh);
        g.fill(bx, by, bx + bw, by + bh, hov ? C_BTN_HOV : C_BTN);
        g.fill(bx, by, bx + bw, by + 1, C_DIM);
        g.drawCenteredString(font, "Close", bx + bw / 2, by + 4, C_TEXT);
    }

    // ── Scrollbar ─────────────────────────────────────────────────────────────

    private void renderScrollbar(GuiGraphics g, int startY, int contentH, int scrollY, int totalH) {
        if (totalH <= contentH) return;
        int trackH = contentH - 4;
        int thumbH = Math.max(20, trackH * contentH / totalH);
        int thumbY = startY + 2 + (int) ((long)(trackH - thumbH) * scrollY / Math.max(1, totalH - contentH));
        g.fill(this.width - 5, startY + 2, this.width - 2, startY + 2 + trackH, 0x33FFFFFF);
        g.fill(this.width - 5, thumbY,     this.width - 2, thumbY + thumbH,      C_ACCENT);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int tw = this.width / 2;
        for (int i = 0; i < Tab.values().length; i++) {
            if (isOver((int) mx, (int) my, i * tw, 20, tw, 18)) { tab = Tab.values()[i]; return true; }
        }
        int fy = this.height - FOOTER_H, bw = 80, bh = 16;
        int bx = this.width / 2 - bw / 2, by = fy + (FOOTER_H - bh) / 2;
        if (isOver((int) mx, (int) my, bx, by, bw, bh)) { onClose(); return true; }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int contentH = this.height - HEADER_H - FOOTER_H, step = 15;
        if (tab == Tab.BLOCKS) {
            scrollBlocks = clampScroll(scrollBlocks + (delta > 0 ? -step : step), blockEntries.size(), contentH);
        } else {
            scrollIngredients = clampScroll(scrollIngredients + (delta > 0 ? -step : step), ingredientEntries.size(), contentH);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (kc == 256) { onClose(); return true; }
        return super.keyPressed(kc, sc, mod);
    }

    @Override public void onClose()        { Minecraft.getInstance().setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int clampScroll(int val, int itemCount, int contentH) {
        return Math.max(0, Math.min(Math.max(0, itemCount * ROW_H - contentH), val));
    }

    private boolean isOver(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private String trunc(String s, int maxPx) {
        if (s == null) return "";
        while (font.width(s) > maxPx && s.length() > 2) s = s.substring(0, s.length() - 2) + "\u2026";
        return s;
    }
}