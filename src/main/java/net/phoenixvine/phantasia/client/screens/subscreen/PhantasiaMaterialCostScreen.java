package net.phoenixvine.phantasia.client.screens.subscreen;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.common.data.pattern.PhantasiaLoadedPattern;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;

import java.util.*;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

@OnlyIn(Dist.CLIENT)
public class PhantasiaMaterialCostScreen extends Screen {

    private static final int ROW_H = 22;
    private static final int HEADER_H = 50;
    private static final int FOOTER_H = 32;
    private static final int ICON_SZ = 16;
    private int pickerW;
    private static final int PICKER_ROW = 20;

    // ── Tabs ──────────────────────────────────────────────────────────────────
    private enum Tab {
        BLOCKS,
        INGREDIENTS,
        TOTALS
    }

    private Tab tab = Tab.BLOCKS;

    // ── State ─────────────────────────────────────────────────────────────────
    private final Screen parent;
    private final PhantasiaLoadedPattern pattern;

    private final List<BlockEntry> blockEntries = new ArrayList<>();
    private final List<IngredientNode> rootNodes = new ArrayList<>();
    private final List<IngredientNode> ingredientRows = new ArrayList<>();

    private int scrollBlocks = 0;
    private int scrollIngredients = 0;
    private int scrollTotals = 0;

    // Scrollbar Dragging State
    private boolean isDraggingScrollbar = false;
    private double dragSelectionOffset = 0.0;

    private final List<TotalEntry> totalEntries = new ArrayList<>();

    private IngredientNode pickerNode = null;
    private List<EmiRecipe> pickerRecipes = List.of();
    private int pickerX, pickerY, pickerScroll = 0;

    private EmiStack hoveredStack = null;
    private int hoveredX, hoveredY;

    // Clipboard notification feedback tick counter
    private int clipboardFeedbackTicks = 0;

    // State Self-Repair Variables
    private boolean recipesLoadedSuccessfully = false;
    private int retryTicks = 0;

    // ── Data structures ───────────────────────────────────────────────────────

    private static class BlockEntry {

        final String displayName;
        final EmiStack icon;
        final int needed, craftCount, craftOutput;

        BlockEntry(String n, EmiStack icon, int needed, int craftCount, int craftOutput) {
            this.displayName = n;
            this.icon = icon;
            this.needed = needed;
            this.craftCount = craftCount;
            this.craftOutput = craftOutput;
        }
    }

    private static class IngredientNode {

        final EmiIngredient ingredient;
        final EmiStack icon;
        final String displayName;
        final boolean isTag;
        int totalCount;
        final int depth;

        List<EmiRecipe> availableRecipes = null;
        boolean isCycle = false;
        boolean expanded = false;
        final List<IngredientNode> children = new ArrayList<>();
        IngredientNode parent = null;

        IngredientNode(EmiIngredient ingredient, EmiStack icon, String displayName,
                       boolean isTag, int totalCount, int depth) {
            this.ingredient = ingredient;
            this.icon = icon;
            this.displayName = displayName;
            this.isTag = isTag;
            this.totalCount = totalCount;
            this.depth = depth;
        }

        boolean hasRecipes() {
            return availableRecipes != null && !availableRecipes.isEmpty();
        }

        boolean canExpand() {
            return hasRecipes() && !isCycle && !expanded;
        }

        boolean canCollapse() {
            return expanded;
        }

        Set<ResourceLocation> ancestorIds() {
            Set<ResourceLocation> set = new HashSet<>();
            IngredientNode cur = this.parent;
            while (cur != null) {
                if (cur.icon != null && !cur.icon.isEmpty()) set.add(cur.icon.getId());
                cur = cur.parent;
            }
            return set;
        }
    }

    private static class TotalEntry {

        final EmiStack icon;
        final String displayName;
        final boolean isTag;
        int totalCount;

        TotalEntry(EmiStack icon, String displayName, boolean isTag, int totalCount) {
            this.icon = icon;
            this.displayName = displayName;
            this.isTag = isTag;
            this.totalCount = totalCount;
        }
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    public PhantasiaMaterialCostScreen(PhantasiaLoadedPattern pattern, Screen parent) {
        super(Component.translatable("screen.phantasia.material_cost.title"));
        this.parent = parent;
        this.pattern = pattern;
    }

    @Override
    protected void init() {
        super.init();

        this.pickerW = Math.min(280, this.width - 20);

        this.blockEntries.clear();
        this.rootNodes.clear();
        this.ingredientRows.clear();
        this.totalEntries.clear();
        this.closePicker();

        Map<String, Block> nameToBlock = new LinkedHashMap<>();
        for (BlockInfo info : pattern.blockMap.values()) {
            BlockState state = info.getBlockState();
            if (state == null || state.isAir()) continue;
            Block block = state.getBlock();
            if (block.asItem() == Items.AIR) continue;
            nameToBlock.putIfAbsent(block.getName().getString(), block);
        }

        List<EmiStack> blockStacks = nameToBlock.values().stream()
                .map(Block::asItem).filter(i -> i != Items.AIR)
                .map(EmiStack::of).toList();
        Map<ResourceLocation, List<EmiRecipe>> itemRecipes = lookupRecipes(blockStacks);

        boolean foundAnyRecipes = false;

        for (Map.Entry<String, Integer> e : pattern.shoppingList.entrySet()) {
            Block block = nameToBlock.get(e.getKey());
            if (block == null) continue;
            Item item = block.asItem();
            if (item == Items.AIR) continue;
            int needed = e.getValue();

            EmiStack stack = EmiStack.of(item);
            List<EmiRecipe> recipes = itemRecipes.getOrDefault(stack.getId(), List.of());

            if (!recipes.isEmpty()) {
                foundAnyRecipes = true;
            }

            EmiRecipe pref = preferCrafting(recipes);
            int craftOutput = pref != null ? recipeOutputCount(pref, stack) : 1;
            int craftCount = pref != null ? (int) Math.ceil((double) needed / craftOutput) : 0;
            blockEntries.add(new BlockEntry(e.getKey(), stack, needed, craftCount, craftOutput));
        }

        Map<String, IngredientNode> merged = new LinkedHashMap<>();
        for (BlockEntry be : blockEntries) {
            EmiStack stack = be.icon;
            List<EmiRecipe> recipes = itemRecipes.getOrDefault(stack.getId(), List.of());
            EmiRecipe pref = preferCrafting(recipes);

            if (pref != null) {
                int outputCount = recipeOutputCount(pref, stack);
                int craftCount = (int) Math.ceil((double) be.needed / outputCount);
                String outputNs = registryNamespace(stack);
                for (IngredientNode child : extractIngredients(pref, craftCount, 0, outputNs)) {
                    String key = child.icon == null || child.icon.isEmpty() ? child.displayName :
                            child.icon.getId().toString();
                    IngredientNode existing = merged.get(key);
                    if (existing == null) merged.put(key, child);
                    else existing.totalCount += child.totalCount;
                }
            } else {
                String key = stack.getId().toString();
                merged.computeIfAbsent(key, k -> {
                    IngredientNode n = new IngredientNode(stack, stack, be.displayName, false, 0, 0);
                    n.availableRecipes = List.of();
                    return n;
                }).totalCount += be.needed;
            }
        }

        List<EmiStack> rootStacks = merged.values().stream()
                .filter(n -> n.icon != null && !n.icon.isEmpty())
                .map(n -> n.icon).toList();
        Map<ResourceLocation, List<EmiRecipe>> rootRecipes = lookupRecipes(rootStacks);
        List<IngredientNode> roots = new ArrayList<>(merged.values());

        for (IngredientNode n : roots) {
            if (n.availableRecipes == null && n.icon != null && !n.icon.isEmpty()) {
                List<EmiRecipe> recipes = rootRecipes.getOrDefault(n.icon.getId(), List.of());

                n.availableRecipes = recipes.stream()
                        .filter(r -> r.getCategory() != null &&
                                !r.getCategory().getId().getPath().contains("tag"))
                        .toList();

                if (!n.availableRecipes.isEmpty()) foundAnyRecipes = true;
            } else if (n.availableRecipes == null) {
                n.availableRecipes = List.of();
            }
        }
        roots.sort((a, b) -> b.totalCount - a.totalCount);

        rootNodes.addAll(roots);
        ingredientRows.addAll(roots);
        rebuildTotals();

        this.recipesLoadedSuccessfully = pattern.shoppingList.isEmpty() || foundAnyRecipes;
        this.retryTicks = 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.recipesLoadedSuccessfully) {
            this.retryTicks++;
            if (this.retryTicks >= 10) {
                this.retryTicks = 0;
                this.init(Minecraft.getInstance(), this.width, this.height);
            }
        }
        if (clipboardFeedbackTicks > 0) {
            clipboardFeedbackTicks--;
        }
    }

    // ── EMI / recipe helpers ──────────────────────────────────────────────────

    private static Map<ResourceLocation, List<EmiRecipe>> lookupRecipes(Collection<EmiStack> stacks) {
        Map<ResourceLocation, List<EmiRecipe>> result = new HashMap<>();
        try {
            var rm = EmiApi.getRecipeManager();
            if (rm == null) return result;

            for (EmiStack stack : stacks) {
                if (stack.isEmpty() || result.containsKey(stack.getId())) continue;
                List<EmiRecipe> recipes = rm.getRecipesByOutput(stack);
                if (recipes != null) {
                    result.put(stack.getId(), recipes);
                } else {
                    result.put(stack.getId(), List.of());
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private static EmiRecipe preferCrafting(List<EmiRecipe> recipes) {
        if (recipes.isEmpty()) return null;

        for (EmiRecipe r : recipes) {
            String path = r.getCategory().getId().getPath();
            if (path.contains("crafting") || path.contains("assembl")) return r;
        }

        EmiRecipe best = null;
        for (EmiRecipe r : recipes) {
            String path = r.getCategory().getId().getPath();
            if (path.contains("macerat") || path.contains("scrap") || path.contains("recycl") ||
                    path.contains("deconstruct") || path.contains("shred"))
                continue;
            if (best == null) best = r;
        }
        return best != null ? best : recipes.get(0);
    }

    private static int recipeOutputCount(EmiRecipe recipe, EmiStack target) {
        for (EmiIngredient out : recipe.getOutputs()) {
            for (EmiStack es : out.getEmiStacks()) {
                if (!es.isEmpty() && es.getId().equals(target.getId()))
                    return Math.max(1, (int) es.getAmount());
            }
        }
        return 1;
    }

    private static String registryNamespace(EmiStack stack) {
        return stack.getId() != null ? stack.getId().getNamespace() : "minecraft";
    }

    private static List<IngredientNode> extractIngredients(
                                                           EmiRecipe recipe, int craftCount, int depth,
                                                           String outputNs) {
        Map<String, IngredientNode> seen = new LinkedHashMap<>();

        for (EmiIngredient slot : recipe.getInputs()) {
            List<EmiStack> stacks = slot.getEmiStacks();
            if (stacks.isEmpty()) continue;

            if (stacks.size() > 1) {
                continue;
            }

            boolean isTag = false;
            EmiStack rep = stacks.get(0);
            if (rep.isEmpty()) continue;

            int perCraft = Math.max(1, (int) slot.getAmount());
            String key = rep.getId().toString();

            String displayName;
            if (!rep.getItemStack().isEmpty()) {
                displayName = rep.getItemStack().getHoverName().getString();
            } else {
                String path = rep.getId().getPath().replace('_', ' ');
                displayName = Character.toUpperCase(path.charAt(0)) + path.substring(1);
            }

            final String finalDisplayName = displayName;

            IngredientNode node = seen.computeIfAbsent(key,
                    k -> new IngredientNode(slot, rep, finalDisplayName, isTag, 0, depth));
            node.totalCount += perCraft * craftCount;
        }
        return new ArrayList<>(seen.values());
    }

    // ── Tree operations ───────────────────────────────────────────────────────

    private void expandNode(IngredientNode node, EmiRecipe recipe) {
        node.children.clear();
        node.expanded = false;

        EmiStack target = node.icon;
        int outputCount = recipeOutputCount(recipe, target);
        int craftCount = (int) Math.ceil((double) node.totalCount / outputCount);
        String outputNs = registryNamespace(target);

        List<IngredientNode> children = extractIngredients(recipe, craftCount, node.depth + 1, outputNs);
        Set<ResourceLocation> ancestors = node.ancestorIds();
        ancestors.add(target.getId());

        List<EmiStack> childStacks = children.stream()
                .filter(c -> c.icon != null && !c.icon.isEmpty())
                .map(c -> c.icon).toList();
        Map<ResourceLocation, List<EmiRecipe>> childRecipes = lookupRecipes(childStacks);

        for (IngredientNode child : children) {
            child.parent = node;
            if (child.icon != null && !child.icon.isEmpty()) {
                ResourceLocation id = child.icon.getId();
                List<EmiRecipe> rawRecipes = childRecipes.getOrDefault(id, List.of());

                child.availableRecipes = rawRecipes.stream()
                        .filter(r -> r.getCategory() != null &&
                                !r.getCategory().getId().getPath().contains("tag"))
                        .toList();

                if (ancestors.contains(id)) child.isCycle = true;
            } else {
                child.availableRecipes = List.of();
            }
        }

        node.children.addAll(children);
        node.expanded = true;
        rebuildRows();
        rebuildTotals();
    }

    private void collapseNode(IngredientNode node) {
        node.expanded = false;
        node.children.clear();
        rebuildRows();
        rebuildTotals();
    }

    private void rebuildRows() {
        ingredientRows.clear();
        for (IngredientNode root : rootNodes) flattenInto(ingredientRows, root);
    }

    private void flattenInto(List<IngredientNode> out, IngredientNode node) {
        out.add(node);
        if (node.expanded) for (IngredientNode child : node.children) flattenInto(out, child);
    }

    private void rebuildTotals() {
        Map<String, TotalEntry> map = new LinkedHashMap<>();
        for (IngredientNode root : rootNodes) collectLeaves(root, map);
        totalEntries.clear();
        List<TotalEntry> sorted = new ArrayList<>(map.values());
        sorted.sort((a, b) -> b.totalCount - a.totalCount);
        totalEntries.addAll(sorted);
    }

    private void collectLeaves(IngredientNode node, Map<String, TotalEntry> map) {
        boolean isLeaf = !node.expanded;
        if (isLeaf) {
            String key = node.icon == null || node.icon.isEmpty() ? node.displayName : node.icon.getId().toString();
            map.computeIfAbsent(key,
                    k -> new TotalEntry(node.icon, node.displayName, node.isTag, 0)).totalCount += node.totalCount;
        } else {
            for (IngredientNode child : node.children) collectLeaves(child, map);
        }
    }

    private void copyTotalsToClipboard() {
        if (totalEntries.isEmpty()) return;
        StringBuilder sb = new StringBuilder("=== Material Shopping List ===\n");
        for (TotalEntry te : totalEntries) {
            sb.append(String.format("- [ ] %d x %s\n", te.totalCount, te.displayName));
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(sb.toString());
        clipboardFeedbackTicks = 40; // Show a confirmation message for 2 seconds
    }

    // ── Recipe picker ─────────────────────────────────────────────────────────
    private void openPicker(IngredientNode node, int sx, int sy) {
        pickerNode = node;
        this.isPickerSearchFocused = false;
        pickerSearchQuery = "";

        if (node.availableRecipes != null) {
            pickerRecipes = node.availableRecipes.stream()
                    .filter(r -> r.getCategory() != null &&
                            !r.getCategory().getId().getPath().contains("tag"))
                    .toList();
        } else {
            pickerRecipes = List.of();
        }

        filteredPickerRecipes = new ArrayList<>(pickerRecipes);
        pickerScroll = 0;

        pickerX = Math.min(sx + 4, this.width - pickerW - 4);
        pickerY = Math.max(HEADER_H, Math.min(sy, this.height - FOOTER_H - pickerVisibleRows() * PICKER_ROW - 44));
    }

    private void closePicker() {
        this.isPickerSearchFocused = false;
        pickerNode = null;
        pickerSearchQuery = "";
        filteredPickerRecipes = List.of();
    }

    private int pickerVisibleRows() {
        if (filteredPickerRecipes.isEmpty()) return 1;
        int maxH = this.height - FOOTER_H - HEADER_H - 44;
        return Math.min(filteredPickerRecipes.size(), maxH / PICKER_ROW);
    }

    private void updatePickerSearch() {
        if (pickerSearchQuery.isEmpty()) {
            filteredPickerRecipes = new ArrayList<>(pickerRecipes);
        } else {
            String query = pickerSearchQuery.toLowerCase(Locale.ROOT);
            filteredPickerRecipes = pickerRecipes.stream()
                    .filter(r -> {
                        String cat = r.getCategory().getId().getPath().toLowerCase(Locale.ROOT);
                        String id = r.getId() != null ? r.getId().getPath().toLowerCase(Locale.ROOT) : "";
                        return cat.contains(query) || id.contains(query);
                    })
                    .toList();
        }
        int max = Math.max(0, filteredPickerRecipes.size() - pickerVisibleRows());
        pickerScroll = Math.max(0, Math.min(max, pickerScroll));
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (pickerNode != null) {
            if (kc == 256) {
                closePicker();
                return true;
            }
            if (this.isPickerSearchFocused && kc == 259) {
                if (!pickerSearchQuery.isEmpty()) {
                    pickerSearchQuery = pickerSearchQuery.substring(0, pickerSearchQuery.length() - 1);
                    updatePickerSearch();
                }
                return true;
            }
        }
        if (kc == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(kc, sc, mod);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (pickerNode != null && this.isPickerSearchFocused) {
            if (codePoint >= 32 && codePoint != 127) {
                pickerSearchQuery += codePoint;
                updatePickerSearch();
                return true;
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    private boolean isPickerSearchFocused = false;

    private void renderPicker(GuiGraphics g, int mx, int my, float pt) {
        if (pickerNode == null) return;
        int rows = pickerVisibleRows();
        int ph = rows * PICKER_ROW + 40;

        g.fill(pickerX - 1, pickerY - 1, pickerX + pickerW + 1, pickerY + ph + 1, 0xFF000000);
        g.fill(pickerX, pickerY, pickerX + pickerW, pickerY + ph, 0xFF0E0E1C);
        g.fill(pickerX, pickerY, pickerX + pickerW, pickerY + 1, C_ACCENT());
        g.fill(pickerX, pickerY + ph - 1, pickerX + pickerW, pickerY + ph, C_ACCENT());

        g.drawString(font, trunc("Recipe for: " + pickerNode.displayName, pickerW - 8),
                pickerX + 4, pickerY + 5, C_ACCENT(), false);

        int sX = pickerX + 4, sY = pickerY + 16, sW = pickerW - 8, sH = 14;

        g.fill(sX - 1, sY - 1, sX + sW + 1, sY + sH + 1, this.isPickerSearchFocused ? C_ACCENT() : 0xFF000000);
        g.fill(sX, sY, sX + sW, sY + sH, 0xBB05050A);
        g.fill(sX, sY, sX + sW, sY + 1, 0x44FFFFFF);

        if (pickerSearchQuery.isEmpty()) {
            g.drawString(font, "Search recipe type... (e.g. blast)", sX + 4, sY + 3, C_DIM(), false);
        } else {
            String cursor = (this.isPickerSearchFocused && (Util.getMillis() / 500 % 2 == 0)) ? "_" : "";
            g.drawString(font, trunc(pickerSearchQuery, sW - 12) + cursor, sX + 4, sY + 3, C_TEXT(), false);
        }

        g.enableScissor(pickerX, pickerY + 34, pickerX + pickerW, pickerY + ph - 4);

        int ry = pickerY + 34;
        for (int i = pickerScroll; i < filteredPickerRecipes.size() && i < pickerScroll + rows; i++) {
            EmiRecipe recipe = filteredPickerRecipes.get(i);
            int rowY = ry + (i - pickerScroll) * PICKER_ROW;
            boolean hov = isOver(mx, my, pickerX, rowY, pickerW, PICKER_ROW - 1);
            g.fill(pickerX, rowY, pickerX + pickerW, rowY + PICKER_ROW - 1, hov ? C_BTN_HOV() : 0);
            if (hov) g.fill(pickerX, rowY, pickerX + pickerW, rowY + 1, 0x33FFFFFF);

            int cx = pickerX + 4;

            EmiStack iconToRender = EmiStack.EMPTY;
            for (EmiIngredient out : recipe.getOutputs()) {
                for (EmiStack es : out.getEmiStacks()) {
                    if (!es.isEmpty()) {
                        if (iconToRender.isEmpty()) iconToRender = es;
                        if (pickerNode.icon != null && es.getId().equals(pickerNode.icon.getId())) {
                            iconToRender = es;
                            break;
                        }
                    }
                }
                if (!iconToRender.isEmpty() && pickerNode.icon != null &&
                        iconToRender.getId().equals(pickerNode.icon.getId()))
                    break;
            }

            if (!iconToRender.isEmpty()) {
                iconToRender.render(g, cx, rowY + 2, pt);
                if (hov && isOver(mx, my, cx, rowY + 2, ICON_SZ, ICON_SZ)) {
                    hoveredStack = iconToRender;
                    hoveredX = mx;
                    hoveredY = my;
                }
                cx += ICON_SZ + 4;
            }

            String cat = recipe.getCategory().getId().getPath().replace("_", " ");
            String recId = recipe.getId() != null ? "  [" + recipe.getId().getPath() + "]" : "";
            g.drawString(font, trunc(cat + recId, pickerW - (cx - pickerX) - 8), cx, rowY + 6, C_TEXT(), false);
        }

        g.disableScissor();
    }

    private String pickerSearchQuery = "";
    private List<EmiRecipe> filteredPickerRecipes = List.of();

    // ── Screen rendering ──────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, C_BG());
        g.fill(0, 0, this.width, 1, C_ACCENT());
        g.fill(0, this.height - 1, this.width, this.height, C_ACCENT());

        hoveredStack = null;

        g.drawString(font, "Material Cost  —  " + blockEntries.size() + " block types",
                8, 8, C_ACCENT(), false);

        if (!this.recipesLoadedSuccessfully) {
            g.drawString(font, "EMI Synchronizing...", this.width - 120, 8, C_ORANGE(), false);
        }

        renderTabs(g, mx, my);
        switch (tab) {
            case BLOCKS -> renderBlocksTab(g, mx, my, pt);
            case INGREDIENTS -> renderIngredientsTab(g, mx, my, pt);
            case TOTALS -> renderTotalsTab(g, mx, my, pt);
        }
        renderFooter(g, mx, my);
        renderPicker(g, mx, my, pt);

        if (hoveredStack != null && !hoveredStack.isEmpty()) {
            if (!hoveredStack.getItemStack().isEmpty()) {
                g.renderTooltip(font, hoveredStack.getItemStack(), hoveredX, hoveredY);
            } else {
                List<Component> textLines = hoveredStack.getTooltipText();
                if (textLines != null && !textLines.isEmpty()) {
                    g.renderComponentTooltip(font, textLines, hoveredX, hoveredY);
                } else {
                    String path = hoveredStack.getId().getPath().replace('_', ' ');
                    path = Character.toUpperCase(path.charAt(0)) + path.substring(1);
                    g.renderComponentTooltip(font, List.of(Component.literal(path)), hoveredX, hoveredY);
                }
            }
        }
    }

    private void renderTabs(GuiGraphics g, int mx, int my) {
        Tab[] tabs = Tab.values();
        int tw = this.width / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int bx = i * tw;
            boolean act = tab == t, hov = isOver(mx, my, bx, 20, tw, 18);
            g.fill(bx, 20, bx + tw, 38, act ? C_BTN_ACT() : (hov ? C_BTN_HOV() : C_BTN()));
            g.fill(bx, 37, bx + tw, 38, act ? C_ACCENT() : C_DIM());
            String label = switch (t) {
                case BLOCKS -> "Blocks (" + blockEntries.size() + ")";
                case INGREDIENTS -> "Ingredients (" + ingredientRows.size() + ")";
                case TOTALS -> "Totals (" + totalEntries.size() + ")";
            };
            g.drawCenteredString(font, label, bx + tw / 2, 25, act ? C_ACCENT() : C_TEXT());
        }
    }

    // ── BLOCKS tab ────────────────────────────────────────────────────────────

    private void renderBlocksTab(GuiGraphics g, int mx, int my, float pt) {
        int startY = HEADER_H, contentH = this.height - startY - FOOTER_H;
        g.fill(0, startY, this.width, startY + contentH, 0x22FFFFFF);
        g.enableScissor(0, startY, this.width, startY + contentH);

        int x = 8, w = this.width - 16, y = startY - scrollBlocks;
        for (BlockEntry be : blockEntries) {
            if (y + ROW_H > startY && y < startY + contentH) {
                boolean hov = isOver(mx, my, x, y, w, ROW_H - 2);
                g.fill(x, y, x + w, y + ROW_H - 2, hov ? C_BTN_HOV() : 0);
                if (hov) g.fill(x, y, x + w, y + 1, 0x33FFFFFF);
                int cx = x + 2;

                cx = drawBadge(g, String.valueOf(be.needed), cx, y, C_ACCENT());
                if (be.icon != null && !be.icon.isEmpty()) {
                    be.icon.render(g, cx, y + 3, pt);
                    if (hov && isOver(mx, my, cx, y + 3, ICON_SZ, ICON_SZ)) {
                        hoveredStack = be.icon;
                        hoveredX = mx;
                        hoveredY = my;
                    }
                    cx += ICON_SZ + 4;
                }
                g.drawString(font, trunc(be.displayName, this.width - cx - 110), cx, y + 6, C_TEXT(), false);
                if (be.craftCount > 0) {
                    String s = "\u00D7 " + be.craftCount + " craft" + (be.craftCount != 1 ? "s" : "") +
                            (be.craftOutput > 1 ? "  (\u2192" + be.craftOutput + ")" : "");
                    g.drawString(font, s, x + w - font.width(s) - 4, y + 6, C_GREEN(), false);
                } else {
                    g.drawString(font, "direct", x + w - font.width("direct") - 4, y + 6, C_DIM(), false);
                }
            }
            y += ROW_H;
        }
        g.disableScissor();
        renderScrollbar(g, startY, contentH, scrollBlocks, blockEntries.size() * ROW_H);
    }

    // ── INGREDIENTS tab ───────────────────────────────────────────────────────

    private void renderIngredientsTab(GuiGraphics g, int mx, int my, float pt) {
        int startY = HEADER_H, contentH = this.height - startY - FOOTER_H;
        g.fill(0, startY, this.width, startY + contentH, 0x22FFFFFF);

        if (ingredientRows.isEmpty()) {
            g.drawCenteredString(font, "No ingredients found.", this.width / 2, startY + 30, C_DIM());
            return;
        }

        g.enableScissor(0, startY, this.width, startY + contentH);
        int x = 8, w = this.width - 16, y = startY - scrollIngredients;

        for (IngredientNode node : ingredientRows) {
            if (y + ROW_H > startY && y < startY + contentH) {
                int indent = node.depth * 12;
                int rx = x + indent, rw = w - indent;
                boolean pickerActive = pickerNode == node;
                boolean hov = !pickerActive && isOver(mx, my, rx, y, rw, ROW_H - 2);

                g.fill(rx, y, rx + rw, y + ROW_H - 2,
                        pickerActive ? C_BTN_ACT() : (hov ? C_BTN_HOV() : 0));
                if (hov || pickerActive) g.fill(rx, y, rx + rw, y + 1, 0x33FFFFFF);

                if (node.depth > 0) {
                    int lx = x + (node.depth - 1) * 12 + 5;
                    g.fill(lx, y, lx + 1, y + ROW_H / 2, 0x44FFFFFF);
                    g.fill(lx, y + ROW_H / 2, x + node.depth * 12, y + ROW_H / 2 + 1, 0x44FFFFFF);
                }

                int cx = rx + 2;

                if (node.isCycle) {
                    g.drawString(font, "\u21BA", cx, y + 7, C_CYCLE(), false);
                } else if (node.canExpand()) {
                    g.drawString(font, "\u25B6", cx, y + 7, C_ACCENT(), false);
                } else if (node.canCollapse()) {
                    g.drawString(font, "\u25BC", cx, y + 7, C_ORANGE(), false);
                } else {
                    g.drawString(font, "\u2022", cx, y + 7, C_DIM(), false);
                }
                cx += 10;

                cx = drawBadge(g, String.valueOf(node.totalCount), cx, y,
                        node.isCycle ? C_CYCLE() : C_ACCENT());

                if (node.icon != null && !node.icon.isEmpty()) {
                    node.icon.render(g, cx, y + 3, pt);
                    if (hov && isOver(mx, my, cx, y + 3, ICON_SZ, ICON_SZ)) {
                        hoveredStack = node.icon;
                        hoveredX = mx;
                        hoveredY = my;
                    }
                    cx += ICON_SZ + 4;
                }

                int nameColor = node.isCycle ? C_CYCLE() : (node.isTag ? C_ACCENT() : C_TEXT());
                int hintW = node.hasRecipes() && node.availableRecipes.size() > 1 ? 50 : 8;
                g.drawString(font, trunc(node.displayName, rw - (cx - rx) - hintW), cx, y + 6,
                        nameColor, false);

                if (node.hasRecipes() && node.availableRecipes.size() > 1) {
                    String hint = node.availableRecipes.size() + " opt";
                    g.drawString(font, hint, rx + rw - font.width(hint) - 4, y + 6, C_DIM(), false);
                }
            }
            y += ROW_H;
        }

        g.disableScissor();
        renderScrollbar(g, startY, contentH, scrollIngredients, ingredientRows.size() * ROW_H);
    }

    // ── TOTALS tab ────────────────────────────────────────────────────────────

    private void renderTotalsTab(GuiGraphics g, int mx, int my, float pt) {
        int startY = HEADER_H, contentH = this.height - startY - FOOTER_H;
        g.fill(0, startY, this.width, startY + contentH, 0x22FFFFFF);

        if (totalEntries.isEmpty()) {
            g.drawCenteredString(font, "No totals yet.", this.width / 2, startY + 30, C_DIM());
            return;
        }

        g.drawString(font, "Current leaf costs — expand items in Ingredients to break down further",
                8, startY + 4, C_DIM(), false);

        int padding = 8;
        int availableW = this.width - padding * 2;
        int cellW = 150;
        int cols = Math.max(1, availableW / cellW);
        cellW = availableW / cols;

        g.enableScissor(0, startY + 14, this.width, startY + contentH);

        for (int i = 0; i < totalEntries.size(); i++) {
            TotalEntry te = totalEntries.get(i);
            int col = i % cols;
            int row = i / cols;
            int cellX = padding + col * cellW;
            int cellY = startY + 14 + row * ROW_H - scrollTotals;

            if (cellY + ROW_H > startY + 14 && cellY < startY + contentH) {
                boolean hov = isOver(mx, my, cellX, cellY, cellW - 4, ROW_H - 2);
                g.fill(cellX, cellY, cellX + cellW - 4, cellY + ROW_H - 2, hov ? C_BTN_HOV() : 0);
                if (hov) g.fill(cellX, cellY, cellX + cellW - 4, cellY + 1, 0x33FFFFFF);

                int cx = cellX + 4;

                if (te.icon != null && !te.icon.isEmpty()) {
                    te.icon.render(g, cx, cellY + 3, pt);
                    if (hov && isOver(mx, my, cx, cellY + 3, ICON_SZ, ICON_SZ)) {
                        hoveredStack = te.icon;
                        hoveredX = mx;
                        hoveredY = my;
                    }
                    cx += ICON_SZ + 4;
                }

                cx = drawBadge(g, String.valueOf(te.totalCount), cx, cellY, C_ACCENT());

                int nameColor = te.isTag ? C_ACCENT() : C_TEXT();
                int maxNameW = cellX + cellW - 6 - cx;
                g.drawString(font, trunc(te.displayName, maxNameW), cx, cellY + 6, nameColor, false);
            }
        }

        g.disableScissor();

        int totalRows = (int) Math.ceil((double) totalEntries.size() / cols);
        renderScrollbar(g, startY + 14, contentH - 14, scrollTotals, totalRows * ROW_H);
    }

    // ── Footer ────────────────────────────────────────────────────────────────

    private void renderFooter(GuiGraphics g, int mx, int my) {
        int fy = this.height - FOOTER_H;
        g.fill(0, fy, this.width, fy + 1, C_ACCENT());

        if (clipboardFeedbackTicks > 0) {
            g.drawString(font, "\u2714 Copied to clipboard!", 8, fy + 9, C_GREEN(), false);
        } else {
            g.drawString(font, "\u25B6 expand  \u2022  \u25BC right-click collapse  \u2022  \u21BA cycle",
                    8, fy + 9, C_DIM(), false);
        }

        // Close Button
        int bw = 60, bh = 16, bx = this.width - bw - 8, by = fy + (FOOTER_H - bh) / 2;
        boolean hov = isOver(mx, my, bx, by, bw, bh);
        g.fill(bx, by, bx + bw, by + bh, hov ? C_BTN_HOV() : C_BTN());
        g.fill(bx, by, bx + bw, by + 1, C_DIM());
        g.drawCenteredString(font, "Close", bx + bw / 2, by + 4, C_TEXT());

        // Copy List Button
        int cbw = 90;
        int cbx = bx - cbw - 6;
        boolean cbHov = isOver(mx, my, cbx, by, cbw, bh);
        g.fill(cbx, by, cbx + cbw, by + bh, cbHov ? C_BTN_HOV() : C_BTN());
        g.fill(cbx, by, cbx + cbw, by + 1, C_DIM());
        g.drawCenteredString(font, "Copy List", cbx + cbw / 2, by + 4, totalEntries.isEmpty() ? C_DIM() : C_TEXT());
    }

    // ── Scrollbar Helpers ─────────────────────────────────────────────────────

    private int getScrollbarTrackHeight(int contentH) {
        return contentH - 4;
    }

    private int getScrollbarThumbHeight(int contentH, int totalH) {
        if (totalH <= contentH) return 0;
        return Math.max(20, getScrollbarTrackHeight(contentH) * contentH / totalH);
    }

    private int getScrollbarThumbY(int startY, int contentH, int scrollY, int totalH) {
        int trackH = getScrollbarTrackHeight(contentH);
        int thumbH = getScrollbarThumbHeight(contentH, totalH);
        return startY + 2 + (int) ((long) (trackH - thumbH) * scrollY / Math.max(1, totalH - contentH));
    }

    private void renderScrollbar(GuiGraphics g, int startY, int contentH, int scrollY, int totalH) {
        int thumbH = getScrollbarThumbHeight(contentH, totalH);
        if (thumbH == 0) return;
        int trackH = getScrollbarTrackHeight(contentH);
        int thumbY = getScrollbarThumbY(startY, contentH, scrollY, totalH);
        g.fill(this.width - 5, startY + 2, this.width - 2, startY + 2 + trackH, 0x33FFFFFF);
        g.fill(this.width - 5, thumbY, this.width - 2, thumbY + thumbH, C_ACCENT());
    }

    private void handleScrollbarDragging(double my) {
        int startY = HEADER_H;
        int contentH = this.height - startY - FOOTER_H;
        int totalH = 0;

        if (tab == Tab.TOTALS) {
            startY += 14;
            contentH -= 14;
            int padding = 8;
            int availableW = this.width - padding * 2;
            int cols = Math.max(1, availableW / 150);
            totalH = ((int) Math.ceil((double) totalEntries.size() / cols)) * ROW_H;
        } else if (tab == Tab.BLOCKS) {
            totalH = blockEntries.size() * ROW_H;
        } else if (tab == Tab.INGREDIENTS) {
            totalH = ingredientRows.size() * ROW_H;
        }

        if (totalH <= contentH) return;

        int trackH = getScrollbarTrackHeight(contentH);
        int thumbH = getScrollbarThumbHeight(contentH, totalH);

        double currentThumbTopY = my - dragSelectionOffset;
        double relativePos = currentThumbTopY - (startY + 2);
        double maxTrackTravel = trackH - thumbH;

        double percentage = Math.max(0.0, Math.min(1.0, relativePos / Math.max(1.0, maxTrackTravel)));
        int targetScroll = (int) (percentage * (totalH - contentH));

        if (tab == Tab.BLOCKS) scrollBlocks = targetScroll;
        else if (tab == Tab.INGREDIENTS) scrollIngredients = targetScroll;
        else if (tab == Tab.TOTALS) scrollTotals = targetScroll;
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int imx = (int) mx, imy = (int) my;

        if (pickerNode != null) {
            int rows = pickerVisibleRows();
            int ph = rows * PICKER_ROW + 40;

            int sX = pickerX + 4;
            int sY = pickerY + 16;
            int sW = pickerW - 8;
            int sH = 14;

            if (isOver(imx, imy, sX, sY, sW, sH)) {
                this.isPickerSearchFocused = true;
                return true;
            }

            if (isOver(imx, imy, pickerX, pickerY + 36, pickerW, rows * PICKER_ROW)) {
                this.isPickerSearchFocused = false;
                int idx = (imy - (pickerY + 36)) / PICKER_ROW + pickerScroll;
                if (idx >= 0 && idx < filteredPickerRecipes.size()) {
                    IngredientNode node = pickerNode;
                    EmiRecipe selectedRecipe = filteredPickerRecipes.get(idx);
                    closePicker();
                    expandNode(node, selectedRecipe);
                    return true;
                }
            }

            if (isOver(imx, imy, pickerX, pickerY, pickerW, ph)) {
                this.isPickerSearchFocused = false;
                return true;
            }

            closePicker();
            return true;
        }

        // Handle tabs click
        Tab[] tabs = Tab.values();
        int tw = this.width / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            if (isOver(imx, imy, i * tw, 20, tw, 18)) {
                tab = tabs[i];
                isDraggingScrollbar = false;
                return true;
            }
        }

        // Handle footer clicks
        int fy = this.height - FOOTER_H, bw = 60, bh = 16;
        int bx = this.width - bw - 8, by = fy + (FOOTER_H - bh) / 2;
        if (isOver(imx, imy, bx, by, bw, bh)) {
            onClose();
            return true;
        }

        int cbw = 90;
        int cbx = bx - cbw - 6;
        if (isOver(imx, imy, cbx, by, cbw, bh)) {
            copyTotalsToClipboard();
            return true;
        }

        // Test for scrollbar click & grab hook initiation
        int startY = HEADER_H;
        int contentH = this.height - startY - FOOTER_H;
        int totalH = 0;
        int currentScrollY = 0;

        if (tab == Tab.TOTALS) {
            startY += 14;
            contentH -= 14;
            int padding = 8;
            int availableW = this.width - padding * 2;
            int cols = Math.max(1, availableW / 150);
            totalH = ((int) Math.ceil((double) totalEntries.size() / cols)) * ROW_H;
            currentScrollY = scrollTotals;
        } else if (tab == Tab.BLOCKS) {
            totalH = blockEntries.size() * ROW_H;
            currentScrollY = scrollBlocks;
        } else if (tab == Tab.INGREDIENTS) {
            totalH = ingredientRows.size() * ROW_H;
            currentScrollY = scrollIngredients;
        }

        int thumbH = getScrollbarThumbHeight(contentH, totalH);
        if (thumbH > 0 && isOver(imx, imy, this.width - 6, startY, 6, contentH)) {
            int thumbY = getScrollbarThumbY(startY, contentH, currentScrollY, totalH);
            if (imy >= thumbY && imy <= thumbY + thumbH) {
                dragSelectionOffset = imy - thumbY;
            } else {
                dragSelectionOffset = (double) thumbH / 2.0;
            }
            isDraggingScrollbar = true;
            handleScrollbarDragging(my);
            return true;
        }

        if (isOver(imx, imy, 0, startY, this.width, contentH)) {
            if (tab == Tab.INGREDIENTS) {
                int x = 8, w = this.width - 16, y = HEADER_H - scrollIngredients;
                for (IngredientNode node : ingredientRows) {
                    if (y + ROW_H > HEADER_H && y < HEADER_H + (this.height - HEADER_H - FOOTER_H)) {
                        int rx = x + node.depth * 12, rw = w - node.depth * 12;
                        if (isOver(imx, imy, rx, y, rw, ROW_H - 2)) {
                            if (btn == 1 && node.canCollapse()) {
                                collapseNode(node);
                                return true;
                            }
                            if (btn == 0 && node.canExpand()) {
                                if (node.availableRecipes.size() == 1)
                                    expandNode(node, node.availableRecipes.get(0));
                                else
                                    openPicker(node, imx, imy);
                                return true;
                            }
                        }
                    }
                    y += ROW_H;
                }
            }
        }

        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (isDraggingScrollbar && btn == 0) {
            handleScrollbarDragging(my);
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 0) {
            isDraggingScrollbar = false;
        }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int imx = (int) mx, imy = (int) my;
        if (pickerNode != null) {
            int ph = pickerVisibleRows() * PICKER_ROW + 24;
            if (isOver(imx, imy, pickerX, pickerY, pickerW, ph)) {
                int max = Math.max(0, pickerRecipes.size() - pickerVisibleRows());
                pickerScroll = Math.max(0, Math.min(max, pickerScroll + (delta > 0 ? -1 : 1)));
                return true;
            }
        }
        int contentH = this.height - HEADER_H - FOOTER_H, step = 15;
        switch (tab) {
            case BLOCKS -> scrollBlocks = clampScroll(scrollBlocks + (delta > 0 ? -step : step), blockEntries.size(),
                    contentH);
            case INGREDIENTS -> scrollIngredients = clampScroll(scrollIngredients + (delta > 0 ? -step : step),
                    ingredientRows.size(), contentH);
            case TOTALS -> {
                int padding = 8;
                int availableW = this.width - padding * 2;
                int cols = Math.max(1, availableW / 150);
                int totalRows = (int) Math.ceil((double) totalEntries.size() / cols);
                scrollTotals = clampScroll(scrollTotals + (delta > 0 ? -step : step), totalRows, contentH - 14);
            }
        }
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int drawBadge(GuiGraphics g, String text, int cx, int y, int textColor) {
        int bw = font.width(text) + 8;
        g.fill(cx, y + 2, cx + bw, y + ROW_H - 4, 0xBB1A2840);
        g.drawString(font, text, cx + 4, y + 6, textColor, false);
        return cx + bw + 4;
    }

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
