package net.phoenixvine.phantasia.client.screens.subscreen;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

/**
 * PhantasiaTextInputScreen
 *
 * An expanded modal subscreen providing a fully integrated multiline edit
 * environment featuring an integrated cursor insertion color palette.
 */
@OnlyIn(Dist.CLIENT)
public class PhantasiaTextInputScreen extends Screen {

    // Standard Minecraft Formats: 0-9, a-f, and r (Reset)
    private static final char[] COLOR_CODES = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd',
            'e', 'f', 'r' };
    private static final int[] COLOR_VALUES = {
            0xFF000000, 0xFF0000AA, 0xFF00AA00, 0xFF00AAAA, 0xFFAA0000, 0xFFAA00AA, 0xFFFFAA00, 0xFFAAAAAA,
            0xFF555555, 0xFF5555FF, 0xFF55FF55, 0xFF55FFFF, 0xFFFF5555, 0xFFFF55FF, 0xFFFFFF55, 0xFFFFFFFF,
            0xFFFFFFFF
    };

    private final Screen parent;
    private final String title;
    private final String hint;
    private final String initial;
    private final int maxLength;
    private final Consumer<String> onConfirm;

    private CustomTextArea inputBox;

    private int pw, ph, px, py;
    private int btnY;

    public PhantasiaTextInputScreen(Screen parent, String title, String hint,
                                    String initial, int maxLength,
                                    Consumer<String> onConfirm) {
        super(Component.literal(title));
        this.parent = parent;
        this.title = title;
        this.hint = hint;
        this.initial = initial != null ? initial : "";
        this.maxLength = maxLength;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        super.init();

        this.pw = Math.min(440, this.width - 40);
        this.ph = 180;
        this.px = (this.width - this.pw) / 2;
        this.py = (this.height - this.ph) / 2;
        this.btnY = this.py + this.ph - 22;

        inputBox = addRenderableWidget(new CustomTextArea(px + 8, py + 24, pw - 16, ph - 70, Component.empty()));
        inputBox.setValue(initial);
        setInitialFocus(inputBox);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, this.width, this.height, C_BG());

        g.fill(px, py, px + pw, py + ph, C_PANEL());
        g.fill(px, py, px + pw, py + 1, C_ACCENT());

        g.drawCenteredString(font, title, px + pw / 2, py + 6, C_ACCENT());

        super.render(g, mx, my, partial);

        renderColorPicker(g, mx, my);

        int half = pw / 2 - 4;
        drawBtn(g, mx, my, px + 8, btnY, half, 14, "✓ Confirm", C_GREEN());
        drawBtn(g, mx, my, px + pw / 2 + 4, btnY, half, 14, "✕ Cancel", C_BTN());
    }

    private void renderColorPicker(GuiGraphics g, int mx, int my) {
        String colorsLabel = Component.translatable("screen.phantasia.text_input.label_colors").getString();
        int labelWidth = font.width(colorsLabel);
        int startX = px + 8;
        int pickerY = btnY - 20;

        g.drawString(font, colorsLabel, startX, pickerY + 2, C_DIM());

        int boxX = startX + labelWidth;
        int size = 12;
        int gap = 3;

        for (int i = 0; i < COLOR_CODES.length; i++) {
            int cx = boxX + i * (size + gap);
            boolean hov = mx >= cx && mx < cx + size && my >= pickerY && my < pickerY + size;

            g.fill(cx, pickerY, cx + size, pickerY + size, COLOR_VALUES[i]);
            g.renderOutline(cx, pickerY, size, size, hov ? C_ACCENT() : 0xFF444444);

            if (hov) {
                g.renderTooltip(font,
                        Component.translatable("ui.phantasia.color_code_display", COLOR_CODES[i], COLOR_CODES[i]), mx,
                        my);
            }
        }
    }

    private void drawBtn(GuiGraphics g, int mx, int my, int x, int y, int w, int h,
                         String label, int col) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, hov ? C_BTN_HOV() : col);
        if (hov) {
            g.fill(x, y, x + w, y + 1, C_ACCENT());
            g.fill(x, y + h - 1, x + w, y + h, C_ACCENT());
        }
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, hov ? C_ACCENT() : C_TEXT());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;

        int half = pw / 2 - 4;
        if (mx >= px + 8 && mx < px + 8 + half && my >= btnY && my < btnY + 14) {
            confirm();
            return true;
        }
        if (mx >= px + pw / 2 + 4 && mx < px + pw && my >= btnY && my < btnY + 14) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }

        int labelWidth = font.width(Component.translatable("screen.phantasia.text_input.label_colors").getString());
        int boxX = px + 8 + labelWidth;
        int pickerY = btnY - 20;
        int size = 12;
        int gap = 3;

        for (int i = 0; i < COLOR_CODES.length; i++) {
            int cx = boxX + i * (size + gap);
            if (mx >= cx && mx < cx + size && my >= pickerY && my < pickerY + size) {
                setInitialFocus(inputBox);
                inputBox.forceInsertBypassingFilters("§" + COLOR_CODES[i]);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if ((kc == GLFW.GLFW_KEY_ENTER || kc == GLFW.GLFW_KEY_KP_ENTER) && !hasShiftDown()) {
            confirm();
            return true;
        }
        if (kc == GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return super.keyPressed(kc, sc, mod);
    }

    private void confirm() {
        String val = inputBox != null ? inputBox.getValue() : initial;
        onConfirm.accept(val);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Bespoke CustomTextArea
     * Directly maps UI interactions onto MultilineTextField's internal structures.
     */
    private class CustomTextArea extends AbstractWidget {

        private final MultilineTextField textField;
        private final List<LinePos> linesCache = new ArrayList<>(); // Track rendered positions for accurate clicking

        public CustomTextArea(int x, int y, int width, int height, Component message) {
            super(x, y, width, height, message);
            this.textField = new MultilineTextField(PhantasiaTextInputScreen.this.font, width - 12);
            this.textField.setCharacterLimit(maxLength);
        }

        public void setValue(String val) {
            this.textField.setValue(val);
        }

        public String getValue() {
            return this.textField.value();
        }

        public MultilineTextField getTextField() {
            return this.textField;
        }

        public void forceInsertBypassingFilters(String insertionText) {
            String fullText = this.textField.value();
            int currentCursor = this.textField.cursor();

            int start = currentCursor;
            int end = currentCursor;

            if (this.textField.hasSelection()) {
                String sel = this.textField.getSelectedText();
                start = fullText.indexOf(sel);
                if (start != -1) {
                    end = start + sel.length();
                } else {
                    start = currentCursor;
                }
            }

            StringBuilder builder = new StringBuilder(fullText);
            builder.replace(start, end, insertionText);

            String updatedText = builder.toString();
            if (updatedText.length() <= maxLength) {
                this.textField.setValue(updatedText);

                int nextCursorPos = start + insertionText.length();
                this.textField.seekCursor(net.minecraft.client.gui.components.Whence.ABSOLUTE, nextCursorPos);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            g.fill(getX(), getY(), getX() + width, getY() + height, 0xFF000000);
            g.renderOutline(getX(), getY(), width, height, isFocused() ? C_ACCENT() : 0xFF444444);

            int textX = getX() + 6;
            int textY = getY() + 6;

            int cursorIdx = this.textField.cursor();
            String fullText = this.textField.value();

            // Clear and rebuild our coordinate cache map on each draw pass
            linesCache.clear();
            if (fullText.isEmpty()) {
                linesCache.add(new LinePos(0, 0, ""));
            } else {
                PhantasiaTextInputScreen.this.font.getSplitter().splitLines(fullText, width - 12, Style.EMPTY, false,
                        (style, start, end) -> linesCache.add(new LinePos(start, end, fullText.substring(start, end))));
                if (fullText.endsWith("\n")) {
                    linesCache.add(new LinePos(fullText.length(), fullText.length(), ""));
                }
            }

            // Render Selection Box Highlighting Tracks if selection is active
            if (this.textField.hasSelection()) {
                String selectedText = this.textField.getSelectedText();
                int selectIdx = fullText.indexOf(selectedText);
                int selectEnd = selectIdx + selectedText.length();

                for (int i = 0; i < linesCache.size(); i++) {
                    LinePos line = linesCache.get(i);
                    int lineY = textY + (i * 9);

                    if (selectEnd > line.start && selectIdx < line.end) {
                        int selStartInLine = Math.max(selectIdx, line.start) - line.start;
                        int selEndInLine = Math.min(selectEnd, line.end) - line.start;

                        String beforeSel = line.text.substring(0, selStartInLine);
                        String selText = line.text.substring(selStartInLine, selEndInLine);

                        int hX1 = textX + PhantasiaTextInputScreen.this.font.width(beforeSel);
                        int hX2 = hX1 + PhantasiaTextInputScreen.this.font.width(selText);

                        g.fill(hX1, lineY, hX2, lineY + 9, 0xFF2244AA);
                    }
                }
            }

            // Render text lines and active blinking caret positions
            for (int i = 0; i < linesCache.size(); i++) {
                LinePos line = linesCache.get(i);
                int lineY = textY + (i * 9);

                g.drawString(PhantasiaTextInputScreen.this.font, line.text, textX, lineY, C_TEXT(), false);

                if (isFocused() && cursorIdx >= line.start && cursorIdx <= line.end) {
                    if ((System.currentTimeMillis() / 500) % 2 == 0) {
                        int offset = cursorIdx - line.start;
                        String sub = line.text.substring(0, Math.min(offset, line.text.length()));
                        int cx = textX + PhantasiaTextInputScreen.this.font.width(sub);
                        g.fill(cx, lineY, cx + 1, lineY + 9, C_ACCENT());
                    }
                }
            }
        }

        /**
         * Helper method to accurately translate screen space coordinates to character indices
         */
        private int getCursorIndexFromCoordinates(double mx, double my) {
            if (linesCache.isEmpty()) return 0;

            int clickedLineIdx = (int) ((my - (getY() + 6)) / 9);
            clickedLineIdx = Math.max(0, Math.min(clickedLineIdx, linesCache.size() - 1));

            LinePos clickedLine = linesCache.get(clickedLineIdx);
            int localClickX = (int) (mx - (getX() + 6));

            String lineText = clickedLine.text;
            int rawCharOffset = 0;
            int currentVisualWidth = 0;

            while (rawCharOffset < lineText.length()) {
                if (lineText.charAt(rawCharOffset) == '§' && rawCharOffset + 1 < lineText.length()) {
                    rawCharOffset += 2;
                    continue;
                }

                String visualSubstring = lineText.substring(0, rawCharOffset + 1);
                currentVisualWidth = PhantasiaTextInputScreen.this.font.width(visualSubstring);

                if (currentVisualWidth > localClickX) {
                    break;
                }

                rawCharOffset++;
            }

            return clickedLine.start + rawCharOffset;
        }

        /**
         * Safely alters only the cursor index via reflection to preserve the dragging selection anchor.
         */
        private void setCursorIndexDirectly(int position) {
            try {
                java.lang.reflect.Field cursorField;
                try {
                    cursorField = MultilineTextField.class.getDeclaredField("cursor");
                } catch (NoSuchFieldException e) {
                    // Fallback to production SRG name for 1.20.x if running obfuscated
                    cursorField = MultilineTextField.class.getDeclaredField("f_239201_");
                }
                cursorField.setAccessible(true);
                cursorField.setInt(this.textField, position);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            if (mx >= getX() && mx < getX() + width && my >= getY() && my < getY() + height) {
                this.setFocused(true);
                int targetCursor = getCursorIndexFromCoordinates(mx, my);

                // Initial click sets both cursor and anchor to the same spot (collapsing selection)
                this.textField.seekCursor(net.minecraft.client.gui.components.Whence.ABSOLUTE, targetCursor);
                return true;
            }
            this.setFocused(false);
            return false;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
            if (this.isFocused() && btn == 0) {
                int targetCursor = getCursorIndexFromCoordinates(mx, my);

                // Change ONLY the cursor, which forces the text field to stretch the selection range
                setCursorIndexDirectly(targetCursor);
                return true;
            }
            return super.mouseDragged(mx, my, btn, dx, dy);
        }

        @Override
        public boolean keyPressed(int kc, int sc, int mod) {
            if (!this.isFocused()) return false;

            if ((kc == GLFW.GLFW_KEY_ENTER || kc == GLFW.GLFW_KEY_KP_ENTER) && hasShiftDown()) {
                this.textField.insertText("\n");
                return true;
            }

            if (this.textField.keyPressed(kc)) {
                return true;
            }
            return super.keyPressed(kc, sc, mod);
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            if (this.isFocused() && SharedConstants.isAllowedChatCharacter(codePoint)) {
                this.textField.insertText(Character.toString(codePoint));
                return true;
            }
            return false;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {}

        private static class LinePos {

            final int start;
            final int end;
            final String text;

            LinePos(int start, int end, String text) {
                this.start = start;
                this.end = end;
                this.text = text;
            }
        }
    }
}