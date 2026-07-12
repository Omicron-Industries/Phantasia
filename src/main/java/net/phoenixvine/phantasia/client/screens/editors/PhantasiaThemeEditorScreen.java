package net.phoenixvine.phantasia.client.screens.editors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.phoenixvine.phantasia.utils.PhantasiaTheme;
import net.phoenixvine.phantasia.utils.PhantasiaThemeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

public class PhantasiaThemeEditorScreen extends Screen {

    private final Screen parent;
    private final List<EditBoxWrapper> editBoxes = new ArrayList<>();
    private final List<CategoryHeader> categories = new ArrayList<>();
    private EditBox nameInput;
    private EditBox baseplateBox;
    private int scrollOffset = 0;
    private int lastPreviewPTop = 28; // synced from renderPreviewArea so mouseClicked uses the same Y layout

    // ── Staging Area for Deferred Deletions ──
    private final List<String> pendingDeletions = new ArrayList<>();

    // ── UX Preservation Records & Undo Stack ──
    private enum UndoType {
        COLOR_EDIT,
        THEME_DELETE
    }

    private record ThemeSnapshot(String bg, String panel, String accent, String btn, String btnHov, String text,
                                 String dim, String prog, String hilight, String baseplateBlock, String name) {}

    private record CategoryHeader(String title, int x, int y, int width) {}

    private record UndoEntry(
                             UndoType type,
                             ThemeSnapshot colorSnapshot,
                             String deletedName) {}

    private final Stack<UndoEntry> undoStack = new Stack<>();
    private ThemeSnapshot savedSnapshot;
    private String lastThemeName = null;
    private boolean isUndoing = false;

    private boolean confirmWarningActive = false;
    private String pendingAction = null;

    public PhantasiaThemeEditorScreen(Screen parent) {
        super(Component.translatable("screen.phantasia.theme_editor.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.editBoxes.clear();
        this.categories.clear();

        PhantasiaTheme active = PhantasiaTheme.current();
        String currentName = PhantasiaTheme.getActiveName();

        if (!currentName.equals(lastThemeName)) {
            lastThemeName = currentName;
            savedSnapshot = createSnapshotInline(active, currentName);
            undoStack.clear();
            pendingDeletions.clear(); // Clear staged queue if hard-swapping active workspace
            confirmWarningActive = false;
            pendingAction = null;
        }

        int sidebarWidth = Math.max(185, this.width / 4);
        int startX = this.width - sidebarWidth + 8;
        int startY = 44;
        int rowHeight = (this.height > 360) ? 20 : 17;
        int boxWidth = sidebarWidth - 92; // leave room for swatch (18px) + label (66px) + padding

        categories.add(new CategoryHeader("Base Layers", startX, startY, sidebarWidth - 16));
        startY += 14;
        addSettingField("BG Color", active.bg, startX, startY, boxWidth);
        startY += rowHeight;
        addSettingField("Panel Color", active.panel, startX, startY, boxWidth);
        startY += rowHeight;

        baseplateBox = new EditBox(this.font, startX + 72, startY, boxWidth, 16,
                Component.translatable("screen.phantasia.theme_editor.label_baseplate"));
        baseplateBox.setMaxLength(128);
        baseplateBox.setValue(active.baseplateBlock != null ? active.baseplateBlock : "minecraft:deepslate_bricks");
        baseplateBox.setHint(Component.translatable("screen.phantasia.theme_editor.hint_baseplate"));
        baseplateBox.setResponder(str -> {
            if (!isUndoing) pushUndoSnapshot();
            PhantasiaTheme.current().baseplateBlock = str.isBlank() ? "minecraft:deepslate_bricks" : str.trim();
            confirmWarningActive = false;
        });
        this.addWidget(baseplateBox);
        startY += rowHeight + 8;

        categories.add(new CategoryHeader("Typography", startX, startY, sidebarWidth - 16));
        startY += 14;
        addSettingField("Text Color", active.text, startX, startY, boxWidth);
        startY += rowHeight;
        addSettingField("Dim Color", active.dim, startX, startY, boxWidth);
        startY += rowHeight + 8;

        categories.add(new CategoryHeader("Accent & Buttons", startX, startY, sidebarWidth - 16));
        startY += 14;
        addSettingField("Accent", active.accent, startX, startY, boxWidth);
        startY += rowHeight;
        addSettingField("Btn Color", active.btn, startX, startY, boxWidth);
        startY += rowHeight;
        addSettingField("Btn Hover", active.btnHov, startX, startY, boxWidth);
        startY += rowHeight + 8;

        categories.add(new CategoryHeader("Feedback", startX, startY, sidebarWidth - 16));
        startY += 14;
        addSettingField("Progress", active.prog, startX, startY, boxWidth);
        startY += rowHeight;
        addSettingField("Highlight", active.hilight, startX, startY, boxWidth);

        int controlY = Math.max(startY + 24, this.height - 68);
        this.nameInput = new EditBox(this.font, this.width - sidebarWidth + 8, controlY, sidebarWidth - 16, 16,
                Component.literal(Component.translatable("screen.phantasia.theme_editor.label_theme_id").getString()));
        this.nameInput.setValue(currentName);
        this.nameInput.setResponder(str -> confirmWarningActive = false);
        this.addWidget(this.nameInput);

        int btnW = (sidebarWidth - 20) / 2 - 2;
        int btnX1 = this.width - sidebarWidth + 8;
        int btnX2 = btnX1 + btnW + 4;

        this.addRenderableWidget(Button
                .builder(Component.translatable("screen.phantasia.theme_editor.btn_save"), b -> triggerSaveAction())
                .bounds(btnX1, controlY + 20, btnW, 18).build());

        this.addRenderableWidget(Button.builder(
                Component.literal(Component.translatable("screen.phantasia.theme_editor.btn_exit").getString()), b -> {
                    if (hasUnsavedChanges()) {
                        if (!confirmWarningActive || !"EXIT".equals(pendingAction)) {
                            confirmWarningActive = true;
                            pendingAction = "EXIT";
                            return;
                        }
                        restoreSnapshot(savedSnapshot);
                    }
                    this.onClose();
                }).bounds(btnX2, controlY + 20, btnW, 18).build());
    }

    private void addSettingField(String label, PhantasiaTheme.ThemeColor target, int x, int y, int boxWidth) {
        // swatch (14px) + gap (4px) = 18px offset before box; label is drawn at render time
        EditBox box = new EditBox(this.font, x + 88, y, boxWidth, 16, Component.literal(label));
        box.setMaxLength(16);
        box.setValue(target.getHex());
        box.setResponder(str -> {
            if (!isUndoing) pushUndoSnapshot();
            target.set(str);
            confirmWarningActive = false;
        });
        this.addWidget(box);
        this.editBoxes.add(new EditBoxWrapper(label, box, target));
    }

    private String getBoxValue(String label, String fallback) {
        for (EditBoxWrapper wrapper : editBoxes) {
            if (wrapper.label.equals(label)) {
                return wrapper.box.getValue();
            }
        }
        return fallback;
    }

    private void triggerSaveAction() {
        String themeId = nameInput.getValue().trim().toUpperCase(Locale.ROOT);
        if (!themeId.isEmpty()) {
            PhantasiaTheme active = PhantasiaTheme.current();
            PhantasiaTheme newTheme = new PhantasiaTheme(
                    getBoxValue("BG Color", active.bg.getHex()),
                    getBoxValue("Panel Color", active.panel.getHex()),
                    getBoxValue("Accent", active.accent.getHex()),
                    getBoxValue("Btn Color", active.btn.getHex()),
                    getBoxValue("Btn Hover", active.btnHov.getHex()),
                    active.btnAct.getHex(),
                    getBoxValue("Text Color", active.text.getHex()),
                    getBoxValue("Dim Color", active.dim.getHex()),
                    active.tlBg.getHex(),
                    getBoxValue("Progress", active.prog.getHex()),
                    active.warn.getHex(),
                    getBoxValue("Highlight", active.hilight.getHex()),
                    baseplateBox != null ? baseplateBox.getValue().trim() : active.baseplateBlock);

            // If user overwrites a theme they previously marked for deletion, drop it from staging
            pendingDeletions.remove(themeId);

            PhantasiaTheme.saveThemeToDisk(themeId, newTheme);
            PhantasiaTheme.setActive(themeId);

            savedSnapshot = createSnapshotInline(PhantasiaTheme.current(), themeId);
            confirmWarningActive = false;
            pendingAction = null;
            init();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Screen.hasControlDown()) {
            if (keyCode == 90) { // Ctrl + Z
                tryUndo();
                return true;
            } else if (keyCode == 83) { // Ctrl + S
                triggerSaveAction();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void pushUndoSnapshot() {
        ThemeSnapshot current = createSnapshotInline(PhantasiaTheme.current(),
                nameInput != null ? nameInput.getValue() : PhantasiaTheme.getActiveName());
        if (undoStack.isEmpty() || undoStack.peek().colorSnapshot == null ||
                !undoStack.peek().colorSnapshot.equals(current)) {
            undoStack.push(new UndoEntry(UndoType.COLOR_EDIT, current, null));
            if (undoStack.size() > 50) {
                undoStack.remove(0);
            }
        }
    }

    private void tryUndo() {
        if (undoStack.isEmpty()) return;
        isUndoing = true;

        UndoEntry historicalRecord = undoStack.pop();
        if (historicalRecord.type == UndoType.COLOR_EDIT) {
            ThemeSnapshot previousState = historicalRecord.colorSnapshot;
            restoreSnapshot(previousState);

            for (EditBoxWrapper wrapper : editBoxes) {
                switch (wrapper.label) {
                    case "BG Color" -> wrapper.box.setValue(previousState.bg);
                    case "Panel Color" -> wrapper.box.setValue(previousState.panel);
                    case "Accent" -> wrapper.box.setValue(previousState.accent);
                    case "Btn Color" -> wrapper.box.setValue(previousState.btn);
                    case "Btn Hover" -> wrapper.box.setValue(previousState.btnHov);
                    case "Text Color" -> wrapper.box.setValue(previousState.text);
                    case "Dim Color" -> wrapper.box.setValue(previousState.dim);
                    case "Progress" -> wrapper.box.setValue(previousState.prog);
                    case "Highlight" -> wrapper.box.setValue(previousState.hilight);
                }
            }
            if (baseplateBox != null && previousState.baseplateBlock != null) {
                baseplateBox.setValue(previousState.baseplateBlock);
            }
            if (nameInput != null) {
                nameInput.setValue(previousState.name);
            }
        } else if (historicalRecord.type == UndoType.THEME_DELETE) {
            // Instant, non-destructive memory recovery by dropping it from the staging bucket
            pendingDeletions.remove(historicalRecord.deletedName.toUpperCase(Locale.ROOT));
            this.init();
        }

        confirmWarningActive = false;
        isUndoing = false;
    }

    private boolean hasUnsavedChanges() {
        if (savedSnapshot == null) return false;
        ThemeSnapshot current = createSnapshotInline(PhantasiaTheme.current(),
                nameInput != null ? nameInput.getValue() : PhantasiaTheme.getActiveName());
        return !savedSnapshot.equals(current);
    }

    private ThemeSnapshot createSnapshotInline(PhantasiaTheme theme, String currentName) {
        return new ThemeSnapshot(theme.bg.getHex(),
                theme.panel.getHex(),
                theme.accent.getHex(),
                theme.btn.getHex(),
                theme.btnHov.getHex(),
                theme.text.getHex(),
                theme.dim.getHex(),
                theme.prog.getHex(),
                theme.hilight.getHex(),
                theme.baseplateBlock, currentName);
    }

    private void restoreSnapshot(ThemeSnapshot target) {
        PhantasiaTheme active = PhantasiaTheme.current();
        active.bg.set(target.bg);
        active.panel.set(target.panel);
        active.accent.set(target.accent);
        active.btn.set(target.btn);
        active.btnHov.set(target.btnHov);
        active.text.set(target.text);
        active.dim.set(target.dim);
        active.prog.set(target.prog);
        active.hilight.set(target.hilight);
        active.baseplateBlock = target.baseplateBlock != null ? target.baseplateBlock : "minecraft:deepslate_bricks";
    }

    // ── Get filtered themes currently not hidden inside staging ──
    private List<String> getVisibleRegistryNames() {
        List<String> visible = new ArrayList<>();
        for (String name : PhantasiaTheme.REGISTRY.keySet()) {
            if (!pendingDeletions.contains(name.toUpperCase(Locale.ROOT))) {
                visible.add(name);
            }
        }
        return visible;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        int sidebarWidth = Math.max(185, this.width / 4);
        int splitX = this.width - sidebarWidth;

        // Left panel background
        g.fill(0, 0, splitX, this.height, PhantasiaThemeUtils.C_BG());
        // Sidebar background
        g.fill(splitX, 0, this.width, this.height, PhantasiaThemeUtils.C_PANEL());
        // Divider accent line
        g.fill(splitX, 0, splitX + 1, this.height, PhantasiaThemeUtils.C_ACCENT());

        // Sidebar header bar
        g.fill(splitX, 0, this.width, 34, C_BTN_ACT());
        g.fill(splitX, 33, this.width, 34, C_ACCENT());
        g.drawString(this.font, "Theme Editor", splitX + 8, 6, C_ACCENT(), false);

        // Status / unsaved indicator
        if (confirmWarningActive) {
            g.fill(splitX, 18, this.width, 33, 0x33FF2200);
            g.drawString(this.font, "Click again to discard!", splitX + 8, 21, C_WARN(), false);
        } else if (hasUnsavedChanges()) {
            g.drawString(this.font, "● " + PhantasiaTheme.getActiveName() + " (unsaved)",
                    splitX + 8, 21, C_WARN(), false);
        } else {
            g.drawString(this.font, "○ " + PhantasiaTheme.getActiveName(),
                    splitX + 8, 21, C_DIM(), false);
        }

        // Category section headers — filled strip + underline
        for (CategoryHeader hdr : categories) {
            g.fill(hdr.x - 4, hdr.y - 1, hdr.x + hdr.width, hdr.y + 10, 0x22FFFFFF);
            g.fill(hdr.x - 4, hdr.y + 9, hdr.x + hdr.width, hdr.y + 10, C_ACCENT());
            g.drawString(this.font, hdr.title, hdr.x, hdr.y, C_ACCENT(), false);
        }

        // Color fields: label + swatch + box
        for (EditBoxWrapper wrapper : editBoxes) {
            int lx = wrapper.box.getX() - 84; // = startX + 4 (label left-edge)
            int ly = wrapper.box.getY() + 4;
            g.drawString(this.font, wrapper.label, lx, ly, C_TEXT(), false);

            // Live color swatch
            int swatchRaw = parseHexSafe(wrapper.box.getValue());
            int sx = wrapper.box.getX() - 18;
            int sy = wrapper.box.getY();
            g.fill(sx - 1, sy - 1, sx + 15, sy + 17, C_BORDER()); // border
            g.fill(sx, sy, sx + 14, sy + 16, swatchRaw | 0xFF000000);

            wrapper.box.render(g, mouseX, mouseY, partialTicks);
        }

        if (baseplateBox != null) {
            g.drawString(this.font, "Baseplate", baseplateBox.getX() - 68, baseplateBox.getY() + 4, C_TEXT(), false);
            baseplateBox.render(g, mouseX, mouseY, partialTicks);
        }

        // Theme ID label + input
        if (nameInput != null) {
            g.drawString(this.font, "Theme ID", nameInput.getX(), nameInput.getY() - 10, C_DIM(), false);
            nameInput.render(g, mouseX, mouseY, partialTicks);
        }

        renderPreviewArea(g, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTicks);

        // Keyboard hint at very bottom of sidebar
        g.drawString(this.font, "Ctrl+S Save   Ctrl+Z Undo",
                splitX + 8, this.height - 10, C_DIM(), false);
    }

    private void renderPreviewArea(GuiGraphics g, int mouseX, int mouseY) {
        int sidebarWidth = Math.max(185, this.width / 4);
        int leftW = this.width - sidebarWidth - 10;

        // Animation hint line
        String animText = "Animations: NONE  TRANSPARENT  RAINBOW  PASTEL_RAINBOW  GALAXY  AURORA  MAGMA";
        List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(Component.literal(animText), leftW - 20);
        int animY = 10;
        for (var line : lines) {
            g.drawString(this.font, line, 12, animY, 0xFF888844, false);
            animY += 10;
        }

        int pTop = Math.max(28, animY + 6);
        lastPreviewPTop = pTop;
        int pHeight = (this.height > 360) ? 90 : 64;

        // System panel preview
        g.fill(12, pTop, leftW, pTop + pHeight, C_PANEL());
        PhantasiaThemeUtils.drawBorderRect(g, 12, pTop, leftW - 12, pHeight, C_BORDER());
        g.drawString(this.font, "System Context Panel", 22, pTop + 7, C_TEXT(), false);
        g.drawString(this.font, "Secondary descriptive text goes here...", 22, pTop + 19, C_DIM(), false);

        // Color chip row — live preview of all theme colors in miniature
        int chipX = 22;
        int chipY = pTop + 32;
        int[] chips = { C_BG(), C_PANEL(), C_ACCENT(), C_BTN(), C_BTN_HOV(), C_TEXT(), C_DIM(), C_PROG(), C_HILIGHT() };
        String[] chipTips = { "BG", "Panel", "Accent", "Btn", "Hov", "Text", "Dim", "Prog", "Hi" };
        for (int ci = 0; ci < chips.length; ci++) {
            g.fill(chipX - 1, chipY - 1, chipX + 13, chipY + 13, C_BORDER());
            g.fill(chipX, chipY, chipX + 12, chipY + 12, chips[ci]);
            if (mouseX >= chipX && mouseX < chipX + 12 && mouseY >= chipY && mouseY < chipY + 12)
                g.drawString(this.font, chipTips[ci], chipX, chipY + 14, C_DIM(), false);
            chipX += 16;
        }

        // Progress bar
        int barW = leftW - 34;
        int barY = pTop + pHeight - 14;
        if (barW > 20) {
            g.fill(22, barY, 22 + barW, barY + 8, C_BG());
            int fill = (int) ((System.currentTimeMillis() / 20) % barW);
            g.fill(22, barY, 22 + fill, barY + 8, C_PROG());
            PhantasiaThemeUtils.drawBorderRect(g, 22, barY, barW, 8, C_BORDER());
        }

        // Component tests section
        int btnTitleY = pTop + pHeight + 10;
        g.drawString(this.font, "Components", 12, btnTitleY, C_ACCENT(), false);

        int btnY = btnTitleY + 13;
        int singleBtnW = Math.min(110, (leftW - 30) / 3);
        if (singleBtnW > 10) {
            boolean h1 = mouseX >= 12 && mouseX <= 12 + singleBtnW && mouseY >= btnY && mouseY <= btnY + 18;
            boolean h2 = mouseX >= 16 + singleBtnW && mouseX <= 16 + singleBtnW * 2 && mouseY >= btnY &&
                    mouseY <= btnY + 18;
            boolean h3 = mouseX >= 20 + singleBtnW * 2 && mouseX <= 20 + singleBtnW * 3 && mouseY >= btnY &&
                    mouseY <= btnY + 18;
            PhantasiaThemeUtils.drawThemedBtn(g, this.font, 12, btnY, singleBtnW, 18, "Primary", h1, C_BTN());
            PhantasiaThemeUtils.drawIconBtn(g, this.font, 16 + singleBtnW, btnY, singleBtnW, 18, "*", "Icon", h2,
                    C_BTN());
            PhantasiaThemeUtils.drawThemedBtn(g, this.font, 20 + singleBtnW * 2, btnY, singleBtnW, 18, "Accent", h3,
                    C_ACCENT());
        }

        // Theme list
        int listTitleY = btnY + 28;
        g.drawString(this.font, "Available Themes", 12, listTitleY, C_TEXT(), false);
        g.drawString(this.font, "click to swap", 12 + this.font.width("Available Themes") + 8,
                listTitleY, C_DIM(), false);

        int itemY = listTitleY + 14;
        List<String> registryNames = getVisibleRegistryNames();

        int maxVisibleThemes = Math.max(1, (this.height - itemY - 15) / 16);
        int maxScroll = Math.max(0, registryNames.size() - maxVisibleThemes);
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll);

        for (int i = scrollOffset; i < registryNames.size() && itemY < this.height - 15; i++) {
            String registeredName = registryNames.get(i);
            boolean isSelected = registeredName.equals(PhantasiaTheme.getActiveName());
            boolean isHovered = mouseX >= 12 && mouseX <= leftW && mouseY >= itemY && mouseY <= itemY + 14;

            int rowBg = isSelected ? C_BTN_ACT() : isHovered ? C_BTN_HOV() : C_PANEL();
            int textCol = isSelected ? C_HILIGHT() : isHovered ? C_ACCENT() : C_TEXT();
            g.fill(12, itemY, leftW, itemY + 14, rowBg);
            PhantasiaThemeUtils.drawBorderRect(g, 12, itemY, leftW - 12, 14, C_BORDER());

            // Small accent dot from that theme
            PhantasiaTheme theme = PhantasiaTheme.REGISTRY.get(registeredName);
            if (theme != null) {
                g.fill(17, itemY + 3, 25, itemY + 11, theme.accent());
            }

            g.drawString(this.font, (isSelected ? "● " : "○ ") + registeredName, 28, itemY + 3, textCol, false);

            if (!PhantasiaTheme.isBuiltIn(registeredName)) {
                int dX = leftW - 14;
                int dY = itemY + 1;
                boolean delHov = mouseX >= dX && mouseX <= dX + 12 && mouseY >= dY && mouseY <= dY + 12;
                g.drawString(this.font, "x", dX + 2, dY + 2, delHov ? 0xFFFF5555 : 0x66FF5555, false);
            }
            itemY += 16;
        }

        if (maxScroll > 0)
            g.drawString(this.font, "scroll", leftW - 36, listTitleY, C_DIM(), false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int sidebarWidth = Math.max(185, this.width / 4);
            int leftW = this.width - sidebarWidth - 10;

            int pTop = lastPreviewPTop;
            int pHeight = (this.height > 360) ? 90 : 64;
            int btnTitleY = pTop + pHeight + 10;
            int btnY = btnTitleY + 13;
            int listTitleY = btnY + 28;
            int itemY = listTitleY + 14;

            List<String> registryNames = getVisibleRegistryNames();

            for (int i = scrollOffset; i < registryNames.size() && itemY < this.height - 15; i++) {
                String targetTheme = registryNames.get(i);

                if (mouseY >= itemY && mouseY <= itemY + 14) {
                    // Click targetted the staging clear option zone
                    if (!PhantasiaTheme.isBuiltIn(targetTheme)) {
                        int dX = leftW - 14;
                        if (mouseX >= dX && mouseX <= dX + 12) {
                            // Safe layout staging: Hide instantly inside the current UI workspace
                            pendingDeletions.add(targetTheme.toUpperCase(Locale.ROOT));
                            undoStack.push(new UndoEntry(UndoType.THEME_DELETE, null, targetTheme));

                            if (targetTheme.equalsIgnoreCase(PhantasiaTheme.getActiveName())) {
                                PhantasiaTheme.setActive("COBALT"); // Fallback safety layer
                            }
                            confirmWarningActive = false;
                            pendingAction = null;
                            this.init();
                            return true;
                        }
                    }

                    if (mouseX >= 12 && mouseX <= leftW - 16) {
                        if (hasUnsavedChanges()) {
                            if (!confirmWarningActive || !targetTheme.equals(pendingAction)) {
                                confirmWarningActive = true;
                                pendingAction = targetTheme;
                                return true;
                            }
                            restoreSnapshot(savedSnapshot);
                        }
                        PhantasiaTheme.setActive(targetTheme);
                        confirmWarningActive = false;
                        pendingAction = null;
                        this.init();
                        return true;
                    }
                }
                itemY += 16;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int sidebarWidth = Math.max(185, this.width / 4);
        if (mouseX < this.width - sidebarWidth - 5) {
            List<String> registryNames = getVisibleRegistryNames();
            int pTop = lastPreviewPTop;
            int pHeight = (this.height > 360) ? 90 : 64;
            int btnTitleY = pTop + pHeight + 10;
            int btnY = btnTitleY + 13;
            int listTitleY = btnY + 28;
            int itemY = listTitleY + 14;

            int maxVisibleThemes = Math.max(1, (this.height - itemY - 15) / 16);
            int maxScroll = Math.max(0, registryNames.size() - maxVisibleThemes);

            this.scrollOffset = Mth.clamp(this.scrollOffset - (int) delta, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // ── Core Screen Lifecycle Termination Override ──
    @Override
    public void onClose() {
        // Execute permanent disk purges only now when leaving the UI context safely
        for (String themeName : pendingDeletions) {
            PhantasiaTheme.deleteThemeFromDisk(themeName);
        }
        pendingDeletions.clear();
        Minecraft.getInstance().setScreen(this.parent);
    }

    private record EditBoxWrapper(String label, EditBox box, PhantasiaTheme.ThemeColor color) {}

    private static int parseHexSafe(String hex) {
        if (hex == null || hex.isBlank()) return 0x000000;
        String clean = hex.replaceAll("[^0-9A-Fa-f]", "");
        if (clean.length() < 6) return 0x000000;
        try {
            return (int) (Long.parseLong(clean.substring(0, 6), 16) & 0xFFFFFFFFL);
        } catch (NumberFormatException e) {
            return 0x000000;
        }
    }
}
