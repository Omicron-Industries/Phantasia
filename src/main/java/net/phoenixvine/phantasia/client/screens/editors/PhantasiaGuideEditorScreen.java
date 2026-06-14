package net.phoenixvine.phantasia.client.screens.editors;

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
import net.phoenixvine.phantasia.client.screens.PhantasiaGuideScreen;
import net.phoenixvine.phantasia.common.data.guides.PhantasiaGuideData;
import net.phoenixvine.phantasia.common.data.guides.PhantasiaGuideData.PageData;
import net.phoenixvine.phantasia.common.data.guides.PhantasiaGuideLoader;
import net.phoenixvine.phantasia.common.data.guides.PhantasiaGuideRegistry;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaScenes;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScripts;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class PhantasiaGuideEditorScreen extends PhantasiaEditorScreen {

    // ── Theme ─────────────────────────────────────────────────────────────────

    private static final int ROW_H = 18;

    // ── State ─────────────────────────────────────────────────────────────────
    private final Screen parent;
    private PhantasiaGuideData data;
    private boolean dirty = false;

    // Resizable Sidebar Config
    private int rightWidth = 280;
    private boolean isResizingSidebar = false;

    // Undo
    private static final int MAX_UNDO = 20;
    private final ArrayDeque<PhantasiaGuideData> undoStack = new ArrayDeque<>();

    // Selection tracking
    private int selectedPage = 0;
    private int pageScrollOffset = 0;
    private int itemGridScrollOffset = 0;

    private int rightItemsScrollOffset = 0;

    // Custom Interactive Multi-line Text Engine State
    private int activeTextFocus = 1;
    private int cursorLine = 0;
    private int cursorColumn = 0;
    private int frameTickCounter = 0;

    // Wrapped Lines Cache for rendering/input calculations
    private final List<WrappedLineMapping> cachedWrappedLines = new ArrayList<>();

    // Standard Inputs
    private EditBox titleBox;
    private EditBox iconItemBox;

    // Item management variables
    private int selectedItem = -1;
    private EditBox itemIdBox;
    private EditBox itemLabelBox;
    private EditBox itemCountBox;
    private String itemTypeSelected = "input";


    private record WrappedLineMapping(String text, int originalLineIdx, int originalCharStart) {}


    // ─────────────────────────────────────────────────────────────────────────

    public PhantasiaGuideEditorScreen(Screen parent, PhantasiaGuideData data) {
        super(Component.literal("Guide Editor"));
        this.parent = parent;
        this.data = data.copy();
    }

    @Override
    protected void init() {
        super.init();
        buildWidgets();
        populateFromPage();
    }

    private void buildWidgets() {
        clearWidgets();

        titleBox = addW(new EditBox(font, 0, 0, 160, 12, Component.empty()));
        titleBox.setMaxLength(128);
        titleBox.setValue(data.title != null ? data.title : "");
        titleBox.setResponder(v -> {
            data.title = v.isBlank() ? "Untitled" : v;
            dirty = true;
        });

        iconItemBox = addW(new EditBox(font, 0, 0, 120, 12, Component.empty()));
        iconItemBox.setMaxLength(128);
        iconItemBox.setValue(data.iconItem != null ? data.iconItem : "minecraft:book");
        iconItemBox.setHint(Component.literal("icon item id"));
        iconItemBox.setResponder(v -> {
            data.iconItem = v.isBlank() ? "minecraft:book" : v;
            dirty = true;
        });

        itemIdBox = addW(new EditBox(font, 0, 0, rightWidth - 12, 12, Component.empty()));
        itemIdBox.setMaxLength(128);
        itemIdBox.setHint(Component.literal("item id (e.g. gtceu:iron_ingot)"));

        itemLabelBox = addW(new EditBox(font, 0, 0, 100, 12, Component.empty()));
        itemLabelBox.setMaxLength(64);
        itemLabelBox.setHint(Component.literal("label"));

        itemCountBox = addW(new EditBox(font, 0, 0, 36, 12, Component.empty()));
        itemCountBox.setMaxLength(4);
        itemCountBox.setFilter(s -> s.matches("\\d*"));
        itemCountBox.setValue("1");
    }

    private void populateFromPage() {
        selectedPage = Math.min(selectedPage, Math.max(0, data.pages.size() - 1));
        if (data.pages.isEmpty()) {
            hidePageInputs();
            return;
        }
        cursorLine = 0;
        cursorColumn = 0;
        clearItemInputs();
    }

    private void hidePageInputs() {
        for (var b : List.of(itemIdBox, itemLabelBox, itemCountBox)) {
            if (b != null) {
                b.visible = false;
                b.active = false;
            }
        }
    }

    private void clearItemInputs() {
        itemIdBox.setValue("");
        itemLabelBox.setValue("");
        itemCountBox.setValue("1");
        itemTypeSelected = "input";
    }

    // ── Midline Terminal-Style Text Calculations Engine (With Word Wrap) ───────

    private List<String> getEditableLines() {
        PageData p = page();
        String raw = (activeTextFocus == 0) ? (p.headline != null ? p.headline : "") : (p.text != null ? p.text : "");
        return new ArrayList<>(Arrays.asList(raw.split("\n", -1)));
    }

    private void saveEditableLines(List<String> lines) {
        String merged = String.join("\n", lines);
        PageData p = page();
        if (activeTextFocus == 0) {
            p.headline = merged;
        } else {
            p.text = merged;
        }
        dirty = true;
    }

    private void recalculateWrappedLines(List<String> originalLines, int maxPixelWidth) {
        cachedWrappedLines.clear();
        for (int i = 0; i < originalLines.size(); i++) {
            String origLine = originalLines.get(i);
            if (origLine.isEmpty()) {
                cachedWrappedLines.add(new WrappedLineMapping("", i, 0));
                continue;
            }

            var splitProperties = font.getSplitter().splitLines(origLine, maxPixelWidth,
                    net.minecraft.network.chat.Style.EMPTY);
            int charIdxOffset = 0;
            for (var split : splitProperties) {
                String subText = split.getString();
                cachedWrappedLines.add(new WrappedLineMapping(subText, i, charIdxOffset));
                charIdxOffset += subText.length();
            }
        }
    }

    private int findClosestColumnIndex(String rawLine, double clickX, int textStartX) {
        double targetX = clickX - textStartX;
        if (targetX <= 0) return 0;

        int closestColumn = 0;
        int currentPixelWidth = 0;
        int minDistance = Integer.MAX_VALUE;

        int i = 0;
        while (i <= rawLine.length()) {
            int dist = Math.abs(currentPixelWidth - (int) targetX);
            if (dist < minDistance) {
                minDistance = dist;
                closestColumn = i;
            }
            if (i >= rawLine.length()) break;
            char c = rawLine.charAt(i);
            currentPixelWidth += font.width(String.valueOf(c));
            i++;
        }
        return closestColumn;
    }

    private boolean isVanillaBoxFocused() {
        return titleBox.isFocused() || iconItemBox.isFocused() ||
                itemIdBox.isFocused() || itemLabelBox.isFocused() || itemCountBox.isFocused();
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (isVanillaBoxFocused()) return super.charTyped(codePoint, modifiers);

        if (codePoint >= 32 && codePoint != 127 && !data.pages.isEmpty()) {
            List<String> lines = getEditableLines();
            ensureCursorBounds(lines);

            String targetLine = lines.get(cursorLine);
            String left = targetLine.substring(0, cursorColumn);
            String right = targetLine.substring(cursorColumn);

            lines.set(cursorLine, left + codePoint + right);
            cursorColumn++;
            saveEditableLines(lines);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isVanillaBoxFocused()) return super.keyPressed(keyCode, scanCode, modifiers);
        if (data.pages.isEmpty()) return super.keyPressed(keyCode, scanCode, modifiers);

        List<String> lines = getEditableLines();
        ensureCursorBounds(lines);

        if (keyCode == InputConstants.KEY_BACKSPACE) {
            if (cursorColumn > 0) {
                String targetLine = lines.get(cursorLine);
                String left = targetLine.substring(0, cursorColumn - 1);
                String right = targetLine.substring(cursorColumn);
                lines.set(cursorLine, left + right);
                cursorColumn--;
                saveEditableLines(lines);
                return true;
            } else if (cursorLine > 0) {
                int previousLineIdx = cursorLine - 1;
                String previousLine = lines.get(previousLineIdx);
                cursorColumn = previousLine.length();

                lines.set(previousLineIdx, previousLine + lines.get(cursorLine));
                lines.remove(cursorLine);
                cursorLine--;
                saveEditableLines(lines);
                return true;
            }
        }

        if (keyCode == InputConstants.KEY_DELETE) {
            String targetLine = lines.get(cursorLine);
            if (cursorColumn < targetLine.length()) {
                String left = targetLine.substring(0, cursorColumn);
                String right = targetLine.substring(cursorColumn + 1);
                lines.set(cursorLine, left + right);
                saveEditableLines(lines);
                return true;
            } else if (cursorLine < lines.size() - 1) {
                lines.set(cursorLine, targetLine + lines.get(cursorLine + 1));
                lines.remove(cursorLine + 1);
                saveEditableLines(lines);
                return true;
            }
        }

        if (keyCode == InputConstants.KEY_LEFT) {
            if (cursorColumn > 0) cursorColumn--;
            else if (cursorLine > 0) {
                cursorLine--;
                cursorColumn = lines.get(cursorLine).length();
            }
            return true;
        }
        if (keyCode == InputConstants.KEY_RIGHT) {
            String targetLine = lines.get(cursorLine);
            if (cursorColumn < targetLine.length()) cursorColumn++;
            else if (cursorLine < lines.size() - 1) {
                cursorLine++;
                cursorColumn = 0;
            }
            return true;
        }
        if (keyCode == InputConstants.KEY_UP) {
            if (cursorLine > 0) {
                cursorLine--;
                cursorColumn = Math.min(cursorColumn, lines.get(cursorLine).length());
            } else if (activeTextFocus == 1) {
                activeTextFocus = 0;
                lines = getEditableLines();
                cursorLine = lines.size() - 1;
                cursorColumn = lines.get(cursorLine).length();
            }
            return true;
        }
        if (keyCode == InputConstants.KEY_DOWN) {
            if (cursorLine < lines.size() - 1) {
                cursorLine++;
                cursorColumn = Math.min(cursorColumn, lines.get(cursorLine).length());
            } else if (activeTextFocus == 0) {
                activeTextFocus = 1;
                cursorLine = 0;
                cursorColumn = 0;
            }
            return true;
        }

        if (keyCode == InputConstants.KEY_HOME) {
            cursorColumn = 0;
            return true;
        }
        if (keyCode == InputConstants.KEY_END) {
            cursorColumn = lines.get(cursorLine).length();
            return true;
        }

        if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            String targetLine = lines.get(cursorLine);
            String left = targetLine.substring(0, cursorColumn);
            String right = targetLine.substring(cursorColumn);

            lines.set(cursorLine, left);
            lines.add(cursorLine + 1, right);
            cursorLine++;
            cursorColumn = 0;
            saveEditableLines(lines);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_S && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            save();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_Z && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            undo();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void ensureCursorBounds(List<String> lines) {
        if (lines.isEmpty()) lines.add("");
        cursorLine = Math.max(0, Math.min(cursorLine, lines.size() - 1));
        cursorColumn = Math.max(0, Math.min(cursorColumn, lines.get(cursorLine).length()));
    }

    @Override
    public void tick() {
        super.tick();
        frameTickCounter++;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        btns.clear();
        hideAllInputs();

        g.fill(0, 0, width, height, C_BG);
        renderTopBar(g, mx, my);
        renderLeftInteractivePanel(g, mx, my);
        renderRightPanel(g, mx, my);
        renderResizeDivider(g, mx, my);

        super.render(g, mx, my, partial);
    }

    private void renderTopBar(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, width, TOP_BAR_H, C_BAR);
        g.fill(0, TOP_BAR_H - 1, width, TOP_BAR_H, C_ACCENT);

        topBtn(g, mx, my, 4, "← Back", this::onClose);

        g.drawString(font, "Title:", 4 + font.width("← Back") + 18, (TOP_BAR_H - 8) / 2, C_DIM, false);
        int titleX = 4 + font.width("← Back") + 18 + font.width("Title:") + 4;
        placeBox(titleBox, titleX, 4, 160, 13);

        int iconX = titleX + 164;
        g.drawString(font, "Icon:", iconX, (TOP_BAR_H - 8) / 2, C_DIM, false);
        placeBox(iconItemBox, iconX + font.width("Icon:") + 4, 4, 120, 13);

        int rx = width - 4;
        rx = topBtnR(g, mx, my, rx, dirty ? "💾 Save*" : "💾 Save", this::save);
        rx = topBtnR(g, mx, my, rx - 4, "▶ Preview", this::openPreview);
        if (!undoStack.isEmpty()) topBtnR(g, mx, my, rx - 4, "↩ Undo", this::undo);
    }

    private void renderResizeDivider(GuiGraphics g, int mx, int my) {
        int dividerX = width - rightWidth;
        boolean hover = mx >= dividerX - 2 && mx <= dividerX + 2 && my > TOP_BAR_H;
        g.fill(dividerX - 1, TOP_BAR_H, dividerX + 1, height, (hover || isResizingSidebar) ? C_ACCENT : 0x44FFFFFF);
    }

    // ── Left Custom Terminal-Style Interactive Input Engine ─────────────────────

    private void renderLeftInteractivePanel(GuiGraphics g, int mx, int my) {
        int previewW = width - rightWidth;
        int colW = Math.min(500, previewW - 48);
        int colX = previewW / 2 - colW / 2;

        g.fill(0, TOP_BAR_H, previewW, height, 0xFF070710);
        g.fill(previewW - 1, TOP_BAR_H, previewW, height, C_ACCENT);

        if (data.pages.isEmpty()) {
            g.drawCenteredString(font, "No pages yet — add one →", previewW / 2, height / 2, C_DIM);
            return;
        }

        PageData p = page();
        int y = TOP_BAR_H + 12;
        int currentFocusBeforeRender = activeTextFocus;

        // ── 1. INTERACTIVE HEADLINE ───────
        activeTextFocus = 0;
        List<String> headlineLines = getEditableLines();
        boolean hlFocused = (currentFocusBeforeRender == 0);
        int hlBoxBg = hlFocused ? 0xFF0D1C2A : 0xBB0D131A;
        int hlBoxHeight = 16 + (headlineLines.size() * (font.lineHeight + 2));

        g.fill(colX - 4, y, colX + colW + 4, y + hlBoxHeight, hlBoxBg);
        g.renderOutline(colX - 4, y, colW + 8, hlBoxHeight, hlFocused ? C_ACCENT : 0xFF223544);
        g.drawString(font, "Headline Editor (& codes will remain as text):", colX, y + 2, C_DIM, false);

        int hlRenderY = y + 14;
        g.enableScissor(colX - 2, y, colX + colW + 2, y + hlBoxHeight);

        for (int i = 0; i < headlineLines.size(); i++) {
            String rawLine = headlineLines.get(i);

            int scrollOffsetX = 0;
            int pixelOffset = font.width(rawLine.substring(0, Math.min(cursorColumn, rawLine.length())));

            if (hlFocused && cursorLine == i && pixelOffset > colW - 12) {
                scrollOffsetX = pixelOffset - (colW - 12);
            }

            g.drawString(font, rawLine, colX + 4 - scrollOffsetX, hlRenderY, 0xFFFFFF, false);

            if (hlFocused && cursorLine == i) {
                if ((frameTickCounter / 10) % 2 == 0) {
                    g.fill(colX + 4 + pixelOffset - scrollOffsetX, hlRenderY - 1,
                            colX + 6 + pixelOffset - scrollOffsetX, hlRenderY + 9, 0xFF00FF00);
                }
            }
            hlRenderY += font.lineHeight + 2;
        }
        g.disableScissor();

        if (isOver(mx, my, colX - 4, y, colW + 8, hlBoxHeight)) {
            final int boxYStart = y + 14;
            btns.add(new Btn(colX - 4, y, colW + 8, hlBoxHeight, () -> {
                activeTextFocus = 0;
                int relativeClickY = my - boxYStart;
                int targetLineIdx = relativeClickY / (font.lineHeight + 2);
                List<String> currentLines = getEditableLines();
                cursorLine = Math.max(0, Math.min(targetLineIdx, currentLines.size() - 1));
                cursorColumn = findClosestColumnIndex(currentLines.get(cursorLine), mx, colX + 4);
            }));
        }

        y += hlBoxHeight + 8;

        // ── 2. INTERACTIVE MULTI-LINE BODY TEXT ───
        activeTextFocus = 1;
        List<String> bodyLines = getEditableLines();
        boolean bodyFocused = (currentFocusBeforeRender == 1);

        recalculateWrappedLines(bodyLines, colW - 8);

        int bodyBoxBg = bodyFocused ? 0xFF091612 : 0xBB0A0F0D;
        int bodyAreaHeight = height - y - 120;
        if (bodyAreaHeight < 90) bodyAreaHeight = 90;

        g.fill(colX - 4, y, colX + colW + 4, y + bodyAreaHeight, bodyBoxBg);
        g.renderOutline(colX - 4, y, colW + 8, bodyAreaHeight, bodyFocused ? C_GREEN : 0xFF1B2B24);
        g.drawString(font, "Body Multiline Editor (Auto Wrapping Engine Active):", colX, y + 3, C_DIM, false);

        int bodyRenderY = y + 15;
        g.enableScissor(colX - 2, y, colX + colW + 2, y + bodyAreaHeight);

        for (int i = 0; i < cachedWrappedLines.size(); i++) {
            if (bodyRenderY + font.lineHeight > y + bodyAreaHeight - 4) break;

            WrappedLineMapping wrappedLine = cachedWrappedLines.get(i);
            g.drawString(font, wrappedLine.text, colX + 4, bodyRenderY, 0xFFFFFF, false);

            if (bodyFocused && cursorLine == wrappedLine.originalLineIdx) {
                int localLineCursorStart = cursorColumn - wrappedLine.originalCharStart;
                if (localLineCursorStart >= 0 && localLineCursorStart <= wrappedLine.text.length()) {
                    boolean isLastSegmentForOrigLine = (i == cachedWrappedLines.size() - 1) ||
                            (cachedWrappedLines.get(i + 1).originalLineIdx != cursorLine);
                    if (localLineCursorStart < wrappedLine.text.length() || isLastSegmentForOrigLine) {
                        if ((frameTickCounter / 10) % 2 == 0) {
                            int cxOffset = font.width(wrappedLine.text.substring(0, localLineCursorStart));
                            g.fill(colX + 4 + cxOffset, bodyRenderY - 1, colX + 6 + cxOffset, bodyRenderY + 9,
                                    0xFF00FF00);
                        }
                    }
                }
            }
            bodyRenderY += font.lineHeight + 2;
        }
        g.disableScissor();

        if (isOver(mx, my, colX - 4, y, colW + 8, bodyAreaHeight)) {
            final int boxYStart = y + 15;
            btns.add(new Btn(colX - 4, y, colW + 8, bodyAreaHeight, () -> {
                activeTextFocus = 1;
                int relativeClickY = my - boxYStart;
                int wrappedLineIdx = relativeClickY / (font.lineHeight + 2);
                if (!cachedWrappedLines.isEmpty()) {
                    wrappedLineIdx = Math.max(0, Math.min(wrappedLineIdx, cachedWrappedLines.size() - 1));
                    WrappedLineMapping targetWrapped = cachedWrappedLines.get(wrappedLineIdx);
                    cursorLine = targetWrapped.originalLineIdx;
                    int localCol = findClosestColumnIndex(targetWrapped.text, mx, colX + 4);
                    cursorColumn = targetWrapped.originalCharStart + localCol;
                }
            }));
        }

        activeTextFocus = currentFocusBeforeRender;
        y += bodyAreaHeight + 8;

        // ── 3. SCROLLABLE ITEM CARDS GRID (With Clamped Bound Scrolling Logic) ─────────────────────
        if (!p.items.isEmpty()) {
            g.fill(colX, y, colX + colW, y + 1, 0x334FC3F7);
            y += 4;

            int gridHeight = height - y - 6;
            g.enableScissor(colX - 4, y, colX + colW + 4, height - 2);

            int perRow = Math.max(1, (colW + 6) / (94 + 6));

            // Dynamic bounds containment calculation
            int totalRows = (int) Math.ceil((double) p.items.size() / perRow);
            int maxScroll = Math.max(0, (totalRows * 94) - gridHeight);
            if (itemGridScrollOffset > maxScroll) {
                itemGridScrollOffset = maxScroll;
            }

            int col = 0;
            int rowY = y - itemGridScrollOffset;

            for (int i = 0; i < p.items.size(); i++) {
                PhantasiaSceneData.ItemConditionData it = p.items.get(i);
                int cx = colX + col * (94 + 6);
                if (rowY + 85 >= y && rowY < y + gridHeight) {
                    renderMiniCard(g, it, cx, rowY);
                }
                col++;
                if (col >= perRow) {
                    col = 0;
                    rowY += 94;
                }
            }
            g.disableScissor();
        }
    }

    private void renderMiniCard(GuiGraphics g, PhantasiaSceneData.ItemConditionData it, int cx, int cy) {
        g.fill(cx, cy, cx + 94, cy + 85, 0xCC101022);
        g.fill(cx, cy, cx + 94, cy + 2, it.accentColor());

        ItemStack stack = resolveStack(it);
        if (!stack.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(cx + (94 - 32) / 2, cy + 8, 100);
            g.pose().scale(2f, 2f, 1f);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
        }
        String label = it.displayLabel();
        if (font.width(label) > 88) label = font.plainSubstrByWidth(label, 85) + "…";
        g.drawCenteredString(font, label, cx + 47, cy + 70, 0xFFDDDDDD);
    }

    // ── Right panel ───────────────────────────────────────────────────────────

    private void renderRightPanel(GuiGraphics g, int mx, int my) {
        int px = width - rightWidth;
        g.fill(px, TOP_BAR_H, width, height, C_PANEL);

        int y = TOP_BAR_H + 4;

        g.fill(px, y, width, y + 14, C_BAR);
        g.drawString(font, "Pages", px + 6, y + 3, C_ACCENT, false);
        int addW = font.width("+ Add") + 8;
        boolean addHov = isOver(mx, my, width - addW - 4, y + 2, addW, 12);
        g.fill(width - addW - 4, y + 2, width - 4, y + 14, addHov ? C_BTN_HOV : C_BTN);
        g.drawString(font, "+ Add", width - addW - 1, y + 4, addHov ? C_ACCENT : C_DIM, false);
        btns.add(new Btn(width - addW - 4, y + 2, addW, 12, this::addPage));
        y += 16;

        int listTop = y;
        int listH = Math.min(data.pages.size() * ROW_H, 4 * ROW_H);
        for (int i = pageScrollOffset; i < data.pages.size() && y < listTop + listH + ROW_H; i++) {
            PageData p = data.pages.get(i);
            boolean sel = i == selectedPage;
            boolean hov = isOver(mx, my, px, y, rightWidth - 20, ROW_H);

            g.fill(px, y, width, y + ROW_H - 1, sel ? C_BTN_ACT : (hov ? C_BTN_HOV : (i % 2 == 0 ? 0x11FFFFFF : 0)));
            if (sel) g.fill(px, y, px + 2, y + ROW_H - 1, C_ACCENT);

            String lbl = (i + 1) + ". ";
            lbl += (p.headline != null && !p.headline.isBlank()) ? p.headline :
                    (p.text != null ? p.text.split("\n")[0] : "(empty)");
            if (font.width(lbl) > rightWidth - 30)
                lbl = font.plainSubstrByWidth(lbl, rightWidth - 30 - font.width("…")) + "…";

            g.drawString(font, lbl, px + 6, y + (ROW_H - 8) / 2, sel ? C_ACCENT : C_TEXT, false);

            boolean delHov = isOver(mx, my, width - 18, y + 2, 14, ROW_H - 4);
            g.fill(width - 18, y + 2, width - 4, y + ROW_H - 2, delHov ? 0xBB3A0A0A : C_BTN);
            g.drawCenteredString(font, "✕", width - 11, y + (ROW_H - 8) / 2, delHov ? C_RED : C_DIM);
            final int fi = i;
            btns.add(new Btn(px, y, rightWidth - 20, ROW_H, () -> selectPage(fi)));
            btns.add(new Btn(width - 18, y + 2, 14, ROW_H - 4, () -> deletePage(fi)));

            y += ROW_H;
        }

        y += 4;
        g.fill(px, y, width, y + 1, 0x22FFFFFF);
        y += 6;

        if (data.pages.isEmpty()) return;

        // ── ITEMS LIST ROW HEADER ──
        g.drawString(font, "Items Configuration (" + page().items.size() + "):", px + 4, y, C_DIM, false);
        int addItemW = font.width("+ Item") + 8;
        boolean aiHov = isOver(mx, my, width - addItemW - 4, y - 1, addItemW, 12);
        g.fill(width - addItemW - 4, y - 1, width - 4, y + 11, aiHov ? C_BTN_HOV : C_BTN);
        g.drawString(font, "+ Item", width - addItemW - 1, y + 1, aiHov ? C_ACCENT : C_DIM, false);
        btns.add(new Btn(width - addItemW - 4, y - 1, addItemW, 12, this::addItem));
        y += font.lineHeight + 3;

        // ── SCROLLABLE LIST CLIP-CONTAINER ENGINE ──
        int itemBoxAreaH = 50; // Total screen space reserved for the items window list
        g.enableScissor(px, y, width, y + itemBoxAreaH);

        // Auto-adjusting max containment math to avoid scroll overshoot
        int maxItemsScroll = Math.max(0, (page().items.size() * 16) - itemBoxAreaH);
        if (rightItemsScrollOffset > maxItemsScroll) {
            rightItemsScrollOffset = maxItemsScroll;
        }

        int renderY = y - rightItemsScrollOffset;
        for (int i = 0; i < page().items.size(); i++) {
            PhantasiaSceneData.ItemConditionData it = page().items.get(i);
            boolean isel = i == selectedItem;
            boolean ihov = isOver(mx, my, px + 4, renderY, rightWidth - 24, 14);

            // Only process click buttons and text if visually inside the panel viewport boundaries
            if (renderY + 14 >= y && renderY <= y + itemBoxAreaH) {
                g.fill(px + 4, renderY, width - 20, renderY + 14, isel ? C_BTN_ACT : (ihov ? C_BTN_HOV : C_BTN));
                if (isel) g.fill(px + 4, renderY, px + 6, renderY + 14, it.accentColor());

                String itLbl = it.displayLabel() + " — " + it.item;
                if (font.width(itLbl) > rightWidth - 34) itLbl = font.plainSubstrByWidth(itLbl, rightWidth - 34) + "…";

                g.drawString(font, itLbl, px + 8, renderY + 3, isel ? C_ACCENT : C_TEXT, false);

                boolean diHov = isOver(mx, my, width - 18, renderY, 14, 14);
                g.fill(width - 18, renderY, width - 4, renderY + 14, diHov ? 0xBB3A0A0A : C_BTN);
                g.drawCenteredString(font, "✕", width - 11, renderY + 3, diHov ? C_RED : C_DIM);

                final int fi = i;
                btns.add(new Btn(px + 4, renderY, rightWidth - 24, 14, () -> selectItem(fi)));
                btns.add(new Btn(width - 18, renderY, 14, 14, () -> removeItem(fi)));
            }
            renderY += 16;
        }
        g.disableScissor();
        y += itemBoxAreaH + 4; // Shift static elements down below the clipping boundaries

        // ── ITEM CONFIGURATION FORM FIELD INTERFACES ──
        g.drawString(font, "Item ID:", px + 4, y, C_DIM, false);
        placeBox(itemIdBox, px + 4 + font.width("Item ID:") + 3, y - 1, rightWidth - 8 - font.width("Item ID:") - 3,
                12);
        y += 14;

        g.drawString(font, "Label:", px + 4, y, C_DIM, false);
        placeBox(itemLabelBox, px + 4 + font.width("Label:") + 3, y - 1, 100, 12);
        y += 14;

        g.drawString(font, "Count:", px + 4, y, C_DIM, false);
        placeBox(itemCountBox, px + 4 + font.width("Count:") + 3, y - 1, 36, 12);
        y += 14;

        g.drawString(font, "Type:", px + 4, y, C_DIM, false);
        int tx = px + 4 + font.width("Type:") + 4;
        for (String t : new String[] { "input", "output", "catalyst" }) {
            int tw = font.width(t) + 8;
            boolean tsel = t.equals(itemTypeSelected);
            boolean thov = isOver(mx, my, tx, y - 1, tw, 12);
            g.fill(tx, y - 1, tx + tw, y + 11, tsel ? C_BTN_ACT : (thov ? C_BTN_HOV : C_BTN));
            if (tsel) g.fill(tx, y - 1, tx + tw, y, PhantasiaSceneData.ItemConditionData.staticAccentFor(t));
            g.drawString(font, t, tx + 4, y + 1, tsel ? C_ACCENT : C_DIM, false);
            final String ft = t;
            btns.add(new Btn(tx, y - 1, tw, 12, () -> { itemTypeSelected = ft; }));
            tx += tw + 4;
        }
        y += 16;

        int apW = font.width("✓ Apply Item") + 10;
        boolean apHov = isOver(mx, my, px + 4, y, apW, 13);
        g.fill(px + 4, y, px + 4 + apW, y + 13, apHov ? C_BTN_HOV : C_BTN);
        g.drawString(font, "✓ Apply Item", px + 8, y + 3, apHov ? C_ACCENT : C_TEXT, false);
        btns.add(new Btn(px + 4, y, apW, 13, this::applyItemEdit));
        y += 18;

        g.fill(px + 4, y, width - 4, y + 1, 0x22FFFFFF);
        y += 6;

        // ── LINKED REGISTRY ENTITY SELECTION BUTTONS ──
        String gl = page().guideId != null && !page().guideId.isBlank() &&
                PhantasiaGuideRegistry.get(page().guideId) != null ? page().guideId : "None";
        boolean glHov = isOver(mx, my, px + 4, y, rightWidth - 8, 14);
        g.fill(px + 4, y, width - 4, y + 14, glHov ? C_BTN_HOV : C_BTN);
        String guideBtnLabel = "Guide: " + gl;
        if (font.width(guideBtnLabel) > rightWidth - 12)
            guideBtnLabel = font.plainSubstrByWidth(guideBtnLabel, rightWidth - 16) + "…";
        g.drawCenteredString(font, guideBtnLabel, px + rightWidth / 2, y + 3, glHov ? C_ACCENT : C_TEXT);
        btns.add(new Btn(px + 4, y, rightWidth - 8, 14, () -> {
            List<String> guideIds = PhantasiaGuideRegistry.all().stream().map(guide -> guide.id).toList();
            Minecraft.getInstance().setScreen(new RegistrySearchScreen(this, "Select Guide Link", guideIds, id -> {
                page().guideId = id;
                dirty = true;
            }));
        }));
        y += 18;

        String sl = page().sceneId != null && !page().sceneId.isBlank() &&
                PhantasiaScenes.all().stream().anyMatch(scene -> scene.id.equals(page().sceneId)) ? page().sceneId :
                        "None";
        boolean slHov = isOver(mx, my, px + 4, y, rightWidth - 8, 14);
        g.fill(px + 4, y, width - 4, y + 14, slHov ? C_BTN_HOV : C_BTN);
        String sceneBtnLabel = "Scene: " + sl;
        if (font.width(sceneBtnLabel) > rightWidth - 12)
            sceneBtnLabel = font.plainSubstrByWidth(sceneBtnLabel, rightWidth - 16) + "…";
        g.drawCenteredString(font, sceneBtnLabel, px + rightWidth / 2, y + 3, slHov ? C_ACCENT : C_TEXT);
        btns.add(new Btn(px + 4, y, rightWidth - 8, 14, () -> {
            List<String> sceneIds = PhantasiaScenes.all().stream().map(scene -> scene.id).toList();
            Minecraft.getInstance().setScreen(new RegistrySearchScreen(this, "Select Scene Link", sceneIds, id -> {
                page().sceneId = id;
                dirty = true;
            }));
        }));
        y += 18;

        String slk = page().scriptId != null && !page().scriptId.isBlank() &&
                com.gregtechceu.gtceu.api.registry.GTRegistries.MACHINES.values().stream().anyMatch(
                        def -> def instanceof com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition multi &&
                                PhantasiaScripts.has(multi) && def.getId().toString().equals(page().scriptId)) ?
                                        page().scriptId : "None";
        boolean slkHov = isOver(mx, my, px + 4, y, rightWidth - 8, 14);
        g.fill(px + 4, y, width - 4, y + 14, slkHov ? C_BTN_HOV : C_BTN);
        String scriptBtnLabel = "Script: " + slk;
        if (font.width(scriptBtnLabel) > rightWidth - 12)
            scriptBtnLabel = font.plainSubstrByWidth(scriptBtnLabel, rightWidth - 16) + "…";
        g.drawCenteredString(font, scriptBtnLabel, px + rightWidth / 2, y + 3, slkHov ? C_ACCENT : C_TEXT);

        btns.add(new Btn(px + 4, y, rightWidth - 8, 14, () -> {
            List<String> scriptIds = com.gregtechceu.gtceu.api.registry.GTRegistries.MACHINES.values().stream()
                    .filter(def -> def instanceof com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition multi &&
                            PhantasiaScripts.has(multi))
                    .map(def -> def.getId().toString())
                    .toList();
            Minecraft.getInstance().setScreen(new RegistrySearchScreen(this, "Select Script Link", scriptIds, id -> {
                page().scriptId = id;
                dirty = true;
            }));
        }));
        y += 18;
    }

    // ── Button Actions ────────────────────────────────────────────────────────

    private void addPage() {
        checkpoint();
        PageData p = new PageData();
        data.pages.add(p);
        selectedPage = data.pages.size() - 1;
        dirty = true;
        populateFromPage();
    }

    private void deletePage(int idx) {
        if (idx < 0 || idx >= data.pages.size()) return;
        checkpoint();
        data.pages.remove(idx);
        selectedPage = Math.min(selectedPage, Math.max(0, data.pages.size() - 1));
        dirty = true;
        populateFromPage();
    }

    private void selectPage(int idx) {
        if (idx == selectedPage) return;
        selectedPage = idx;
        populateFromPage();
    }

    private void addItem() {
        selectedItem = -1;
        clearItemInputs();
    }

    private void selectItem(int idx) {
        selectedItem = idx;
        if (idx >= 0 && idx < page().items.size()) {
            PhantasiaSceneData.ItemConditionData it = page().items.get(idx);
            itemIdBox.setValue(it.item != null ? it.item : "");
            itemLabelBox.setValue(it.label != null ? it.label : "");
            itemCountBox.setValue(String.valueOf(it.count));
            itemTypeSelected = it.type != null ? it.type : "input";
        }
    }

    private void removeItem(int idx) {
        if (idx < 0 || idx >= page().items.size()) return;
        checkpoint();
        page().items.remove(idx);
        selectedItem = -1;
        dirty = true;
    }

    private void applyItemEdit() {
        String id = itemIdBox.getValue().trim();
        if (id.isBlank()) return;
        checkpoint();
        int cnt = 1;
        try {
            cnt = Math.max(1, Integer.parseInt(itemCountBox.getValue().trim()));
        } catch (NumberFormatException ignored) {}

        PhantasiaSceneData.ItemConditionData it = new PhantasiaSceneData.ItemConditionData(
                id, cnt, itemLabelBox.getValue().isBlank() ? null : itemLabelBox.getValue(), itemTypeSelected);

        if (selectedItem >= 0 && selectedItem < page().items.size()) page().items.set(selectedItem, it);
        else {
            page().items.add(it);
            selectedItem = page().items.size() - 1;
        }
        dirty = true;
    }

    private void save() {
        PhantasiaGuideRegistry.save(data);
        PhantasiaGuideLoader.load();
        dirty = false;
    }

    private void openPreview() {
        Minecraft.getInstance().setScreen(new PhantasiaGuideScreen(this, data));
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        data = undoStack.pop();
        dirty = true;
        selectedPage = Math.min(selectedPage, Math.max(0, data.pages.size() - 1));
        buildWidgets();
        populateFromPage();
    }

    private void checkpoint() {
        undoStack.push(data.copy());
        if (undoStack.size() > MAX_UNDO) undoStack.removeLast();
    }

    // ── Screen Overrides & Mouse Helpers ──────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int dividerX = width - rightWidth;
        if (mx >= dividerX - 2 && mx <= dividerX + 2 && my > TOP_BAR_H) {
            isResizingSidebar = true;
            return true;
        }

        for (Btn b : btns) if (b.hit(mx, my)) {
            b.action().run();
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 0) {
            isResizingSidebar = false;
        }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (isResizingSidebar) {
            int newWidth = width - (int) mx;
            rightWidth = Math.max(160, Math.min(width - 100, newWidth));
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int previewW = width - rightWidth;
        if (mx < previewW) {
            // Scroll the grid preview on the left side
            itemGridScrollOffset = Math.max(0, itemGridScrollOffset - (int) (delta * 16));
            return true;
        } else {
            // Scroll the items list inside the sidebar on the right side
            rightItemsScrollOffset = Math.max(0, rightItemsScrollOffset - (int) (delta * 12));
            return true;
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private PageData page() {
        if (data.pages.isEmpty()) data.pages.add(new PageData());
        return data.pages.get(Math.min(selectedPage, data.pages.size() - 1));
    }

    @Override
    protected void hideAllInputs() {
        for (var b : List.of(titleBox, iconItemBox, itemIdBox, itemLabelBox, itemCountBox)) {
            if (b != null) {
                b.visible = false;
                b.active = false;
            }
        }
    }


    private void topBtn(GuiGraphics g, int mx, int my, int x, String lbl, Runnable act) {
        int w = font.width(lbl) + 10, h = TOP_BAR_H - 6;
        boolean hov = isOver(mx, my, x, 3, w, h);
        g.fill(x, 3, x + w, 3 + h, hov ? C_BTN_HOV : C_BTN);
        if (hov) g.fill(x, 3, x + w, 4, C_ACCENT);
        g.drawString(font, lbl, x + 5, (TOP_BAR_H - 8) / 2, hov ? C_ACCENT : C_TEXT, false);
        btns.add(new Btn(x, 3, w, h, act));
    }

    private int topBtnR(GuiGraphics g, int mx, int my, int rx, String lbl, Runnable act) {
        int w = font.width(lbl) + 10, h = TOP_BAR_H - 6, x = rx - w;
        boolean hov = isOver(mx, my, x, 3, w, h);
        g.fill(x, 3, x + w, 3 + h, hov ? C_BTN_HOV : C_BTN);
        if (hov) g.fill(x, 3, x + w, 4, C_ACCENT);
        g.drawString(font, lbl, x + 5, (TOP_BAR_H - 8) / 2, hov ? C_ACCENT : C_TEXT, false);
        btns.add(new Btn(x, 3, w, h, act));
        return x - 4;
    }

    private static ItemStack resolveStack(PhantasiaSceneData.ItemConditionData it) {
        if (it.item == null || it.item.isBlank()) return ItemStack.EMPTY;
        try {
            ResourceLocation rl = it.item.contains(":") ? new ResourceLocation(it.item) :
                    new ResourceLocation("minecraft", it.item);
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            return (item == null || item == Items.AIR) ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, it.count));
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }



    // ── Registry Search Screen (Self-Contained Lookup Window) ──────────────────

    private static class RegistrySearchScreen extends Screen {

        private final Screen parent;
        private final String titleText;
        private final List<String> elements;
        private final Consumer<String> onSelected;

        private EditBox searchBox;
        private List<String> filteredElements = new ArrayList<>();
        private int scrollOffset = 0;

        protected RegistrySearchScreen(Screen parent, String titleText, List<String> elements,
                                       Consumer<String> onSelected) {
            super(Component.literal(titleText));
            this.parent = parent;
            this.titleText = titleText;
            this.elements = elements;
            this.onSelected = onSelected;
            this.filteredElements.addAll(elements);
        }

        @Override
        protected void init() {
            super.init();
            this.searchBox = new EditBox(this.font, width / 2 - 100, 34, 200, 14, Component.empty());
            this.searchBox.setResponder(text -> {
                this.filteredElements = elements.stream()
                        .filter(s -> s.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT)))
                        .toList();
                this.scrollOffset = 0;
            });
            this.addRenderableWidget(this.searchBox);
            this.setInitialFocus(this.searchBox);
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            g.fill(0, 0, width, height, 0xEE080812);

            // Render explicit interactive Back Button
            int btnW = font.width("← Back") + 10;
            boolean backHov = mx > 10 && mx < 10 + btnW && my > 10 && my < 24;
            g.fill(10, 10, 10 + btnW, 24, backHov ? 0xBB1A2840 : 0xBB151528);
            if (backHov) g.fill(10, 10, 10 + btnW, 11, 0xFF4FC3F7);
            g.drawString(font, "← Back", 15, 13, backHov ? 0xFF4FC3F7 : 0xFFDDDDDD, false);

            g.drawCenteredString(font, this.titleText, width / 2, 16, 0xFF4FC3F7);

            int y = 60;
            for (int i = scrollOffset; i < filteredElements.size() && y < height - 30; i++) {
                String s = filteredElements.get(i);
                boolean hov = mx > width / 2 - 100 && mx < width / 2 + 100 && my > y && my < y + 14;
                g.fill(width / 2 - 100, y, width / 2 + 100, y + 14, hov ? 0xFF4FC3F7 : 0x22FFFFFF);
                g.drawString(font, s, width / 2 - 96, y + 3, hov ? 0x000000 : 0xFFDDDDDD, false);
                y += 16;
            }
            super.render(g, mx, my, pt);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            if (btn == 0) {
                // Handle header Back button interaction execution
                int btnW = font.width("← Back") + 10;
                if (mx > 10 && mx < 10 + btnW && my > 10 && my < 24) {
                    Minecraft.getInstance().setScreen(parent);
                    return true;
                }

                int y = 60;
                for (int i = scrollOffset; i < filteredElements.size() && y < height - 30; i++) {
                    if (mx > width / 2 - 100 && mx < width / 2 + 100 && my > y && my < y + 14) {
                        this.onSelected.accept(filteredElements.get(i));
                        Minecraft.getInstance().setScreen(parent);
                        return true;
                    }
                    y += 16;
                }
            }
            return super.mouseClicked(mx, my, btn);
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double delta) {
            this.scrollOffset = Math.max(0, Math.min(filteredElements.size() - 1, this.scrollOffset - (int) delta));
            return true;
        }

        @Override
        public void onClose() {
            Minecraft.getInstance().setScreen(parent);
        }
    }
}
