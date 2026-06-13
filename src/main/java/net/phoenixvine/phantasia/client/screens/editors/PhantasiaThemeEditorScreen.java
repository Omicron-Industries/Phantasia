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

public class PhantasiaThemeEditorScreen extends Screen {
    private final Screen parent;
    private final List<EditBoxWrapper> editBoxes = new ArrayList<>();
    private final List<CategoryHeader> categories = new ArrayList<>();
    private EditBox nameInput;
    private int scrollOffset = 0;

    // ── Staging Area for Deferred Deletions ──
    private final List<String> pendingDeletions = new ArrayList<>();

    // ── UX Preservation Records & Undo Stack ──
    private enum UndoType { COLOR_EDIT, THEME_DELETE }
    private record ThemeSnapshot(String bg, String panel, String accent, String btn, String btnHov, String text, String dim, String prog, String hilight, String name) {}
    private record CategoryHeader(String title, int x, int y) {}

    private record UndoEntry(
            UndoType type,
            ThemeSnapshot colorSnapshot,
            String deletedName
    ) {}

    private final Stack<UndoEntry> undoStack = new Stack<>();
    private ThemeSnapshot savedSnapshot;
    private String lastThemeName = null;
    private boolean isUndoing = false;

    private boolean confirmWarningActive = false;
    private String pendingAction = null;

    public PhantasiaThemeEditorScreen(Screen parent) {
        super(Component.literal("Phantasia Theme Workspace"));
        this.parent = parent;
        PhantasiaTheme.loadAllThemes();
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

        int sidebarWidth = Math.max(175, this.width / 4);
        int startX = this.width - sidebarWidth + 5;
        int startY = 38;
        int rowHeight = (this.height > 360) ? 20 : 17;

        categories.add(new CategoryHeader("■ Base Layers", startX + 5, startY)); startY += 12;
        addSettingField("BG Color", active.bg, startX, startY, sidebarWidth - 75); startY += rowHeight;
        addSettingField("Panel Color", active.panel, startX, startY, sidebarWidth - 75); startY += rowHeight + 6;

        categories.add(new CategoryHeader("■ Typography System", startX + 5, startY)); startY += 12;
        addSettingField("Text Color", active.text, startX, startY, sidebarWidth - 75); startY += rowHeight;
        addSettingField("Dim Color", active.dim, startX, startY, sidebarWidth - 75); startY += rowHeight + 6;

        categories.add(new CategoryHeader("■ Accent & Buttons", startX + 5, startY)); startY += 12;
        addSettingField("Accent", active.accent, startX, startY, sidebarWidth - 75); startY += rowHeight;
        addSettingField("Btn Color", active.btn, startX, startY, sidebarWidth - 75); startY += rowHeight;
        addSettingField("Btn Hover", active.btnHov, startX, startY, sidebarWidth - 75); startY += rowHeight + 6;

        categories.add(new CategoryHeader("■ Feedback Indicators", startX + 5, startY)); startY += 12;
        addSettingField("Progress", active.prog, startX, startY, sidebarWidth - 75); startY += rowHeight;
        addSettingField("Highlight", active.hilight, startX, startY, sidebarWidth - 75);

        int controlY = Math.max(startY + 22, this.height - 65);
        this.nameInput = new EditBox(this.font, this.width - sidebarWidth + 10, controlY, sidebarWidth - 20, 16, Component.literal("Theme ID"));
        this.nameInput.setValue(currentName);
        this.nameInput.setResponder(str -> confirmWarningActive = false);
        this.addWidget(this.nameInput);

        int btnW = sidebarWidth - 20;
        int btnX = this.width - sidebarWidth + 10;

        this.addRenderableWidget(Button.builder(Component.literal("💾 Save Theme File"), b -> triggerSaveAction())
                .bounds(btnX, controlY + 20, btnW, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("← Exit Workspace"), b -> {
            if (hasUnsavedChanges()) {
                if (!confirmWarningActive || !"EXIT".equals(pendingAction)) {
                    confirmWarningActive = true;
                    pendingAction = "EXIT";
                    return;
                }
                restoreSnapshot(savedSnapshot);
            }
            this.onClose(); // Route exit cleanly through lifecycle cleanup
        }).bounds(btnX, controlY + 40, btnW, 18).build());
    }

    private void addSettingField(String label, PhantasiaTheme.ThemeColor target, int x, int y, int boxWidth) {
        EditBox box = new EditBox(this.font, x + 65, y, boxWidth, 16, Component.literal(label));
        box.setMaxLength(16);
        box.setValue(target.hex);
        box.setResponder(str -> {
            if (!isUndoing) {
                pushUndoSnapshot();
            }
            target.set(str);
            confirmWarningActive = false;
        });
        this.addWidget(box);
        this.editBoxes.add(new EditBoxWrapper(label, box));
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
                    getBoxValue("BG Color", active.bg.hex),
                    getBoxValue("Panel Color", active.panel.hex),
                    getBoxValue("Accent", active.accent.hex),
                    getBoxValue("Btn Color", active.btn.hex),
                    getBoxValue("Btn Hover", active.btnHov.hex),
                    active.btnAct.hex,
                    getBoxValue("Text Color", active.text.hex),
                    getBoxValue("Dim Color", active.dim.hex),
                    active.tlBg.hex,
                    getBoxValue("Progress", active.prog.hex),
                    active.warn.hex,
                    getBoxValue("Highlight", active.hilight.hex)
            );

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
        ThemeSnapshot current = createSnapshotInline(PhantasiaTheme.current(), nameInput != null ? nameInput.getValue() : PhantasiaTheme.getActiveName());
        if (undoStack.isEmpty() || undoStack.peek().colorSnapshot == null || !undoStack.peek().colorSnapshot.equals(current)) {
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
        ThemeSnapshot current = createSnapshotInline(PhantasiaTheme.current(), nameInput != null ? nameInput.getValue() : PhantasiaTheme.getActiveName());
        return !savedSnapshot.equals(current);
    }

    private ThemeSnapshot createSnapshotInline(PhantasiaTheme theme, String currentName) {
        return new ThemeSnapshot(theme.bg.hex, theme.panel.hex, theme.accent.hex, theme.btn.hex, theme.btnHov.hex, theme.text.hex, theme.dim.hex, theme.prog.hex, theme.hilight.hex, currentName);
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
        int sidebarWidth = Math.max(175, this.width / 4);

        g.fill(0, 0, this.width, this.height, PhantasiaThemeUtils.C_BG());
        g.fill(this.width - sidebarWidth, 0, this.width, this.height, PhantasiaThemeUtils.C_PANEL());
        PhantasiaThemeUtils.drawBorderRect(g, this.width - sidebarWidth, 0, 1, this.height, PhantasiaThemeUtils.C_BORDER());

        g.drawString(this.font, "🎨 Theme Options", this.width - sidebarWidth + 10, 10, PhantasiaThemeUtils.C_ACCENT(), false);

        if (confirmWarningActive) {
            g.drawString(this.font, "⚠ Click again to lose modifications!", this.width - sidebarWidth + 10, 22, PhantasiaThemeUtils.C_WARN(), false);
        } else if (hasUnsavedChanges()) {
            g.drawString(this.font, "● Active: " + PhantasiaTheme.getActiveName() + " (Unsaved)", this.width - sidebarWidth + 10, 22, PhantasiaThemeUtils.C_WARN(), false);
        } else {
            g.drawString(this.font, "○ Active: " + PhantasiaTheme.getActiveName(), this.width - sidebarWidth + 10, 22, PhantasiaThemeUtils.C_TEXT(), false);
        }

        for (CategoryHeader header : categories) {
            g.drawString(this.font, header.title, header.x, header.y, PhantasiaThemeUtils.C_ACCENT(), false);
        }

        for (EditBoxWrapper wrapper : editBoxes) {
            g.drawString(this.font, wrapper.label, wrapper.box.getX() - 62, wrapper.box.getY() + 4, PhantasiaThemeUtils.C_TEXT(), false);
            wrapper.box.render(g, mouseX, mouseY, partialTicks);
        }
        this.nameInput.render(g, mouseX, mouseY, partialTicks);

        renderPreviewArea(g, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTicks);
    }

    private void renderPreviewArea(GuiGraphics g, int mouseX, int mouseY) {
        int sidebarWidth = Math.max(175, this.width / 4);
        int leftW = this.width - sidebarWidth - 10;

        String animText = "✨ Animations: NONE, TRANSPARENT, RAINBOW, PASTEL_RAINBOW, GALAXY, AURORA, MAGMA";
        int availableTextWidth = leftW - 20;
        List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(Component.literal(animText), availableTextWidth);

        int animY = 12;
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            g.drawString(this.font, line, 15, animY, 0xFFFFFF44, false);
            animY += 10;
        }

        int pTop = Math.max(28, animY + 4);
        int pHeight = (this.height > 360) ? 80 : 60;
        g.fill(15, pTop, leftW, pTop + pHeight, PhantasiaThemeUtils.C_PANEL());
        PhantasiaThemeUtils.drawBorderRect(g, 15, pTop, leftW - 15, pHeight, PhantasiaThemeUtils.C_BORDER());

        g.drawString(this.font, "📊 Active System Context Panel", 25, pTop + 8, PhantasiaThemeUtils.C_TEXT(), false);
        g.drawString(this.font, "Secondary descriptive line text goes here...", 25, pTop + 22, PhantasiaThemeUtils.C_DIM(), false);

        int barW = leftW - 40;
        int barY = pTop + pHeight - 18;
        if (barW > 20) {
            g.fill(25, barY, 25 + barW, barY + 10, PhantasiaThemeUtils.C_BG());
            int fillProgress = (int) ((System.currentTimeMillis() / 20) % barW);
            g.fill(25, barY, 25 + fillProgress, barY + 10, PhantasiaThemeUtils.C_PROG());
            PhantasiaThemeUtils.drawBorderRect(g, 25, barY, barW, 10, PhantasiaThemeUtils.C_BORDER());
        }

        int btnTitleY = pTop + pHeight + 10;
        g.drawString(this.font, "Interactive Component Tests", 15, btnTitleY, PhantasiaThemeUtils.C_ACCENT(), false);

        int btnY = btnTitleY + 14;
        int singleBtnW = Math.min(120, (leftW - 25) / 2);

        if (singleBtnW > 10) {
            boolean hovBtn1 = mouseX >= 15 && mouseX <= 15 + singleBtnW && mouseY >= btnY && mouseY <= btnY + 20;
            PhantasiaThemeUtils.drawThemedBtn(g, this.font, 15, btnY, singleBtnW, 20, "Button", hovBtn1, PhantasiaThemeUtils.C_BTN());

            boolean hovBtn2 = mouseX >= 25 + singleBtnW && mouseX <= 25 + (singleBtnW * 2) && mouseY >= btnY && mouseY <= btnY + 20;
            PhantasiaThemeUtils.drawIconBtn(g, this.font, 25 + singleBtnW, btnY, singleBtnW, 20, "⭐", "Icon", hovBtn2, PhantasiaThemeUtils.C_BTN());
        }

        int listTitleY = btnY + 28;
        g.drawString(this.font, "📁 Available Themes (Click to Swap)", 15, listTitleY, PhantasiaThemeUtils.C_TEXT(), false);

        int itemY = listTitleY + 14;
        List<String> registryNames = getVisibleRegistryNames(); // Dynamic filtered state view

        int maxVisibleThemes = Math.max(1, (this.height - itemY - 15) / 16);
        int maxScroll = Math.max(0, registryNames.size() - maxVisibleThemes);
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll);

        for (int i = scrollOffset; i < registryNames.size() && itemY < this.height - 15; i++) {
            String registeredName = registryNames.get(i);
            boolean isSelected = registeredName.equals(PhantasiaTheme.getActiveName());
            boolean isHovered = mouseX >= 15 && mouseX <= leftW && mouseY >= itemY && mouseY <= itemY + 14;

            int displayColor = isSelected ? PhantasiaThemeUtils.C_HILIGHT() : (isHovered ? PhantasiaThemeUtils.C_ACCENT() : PhantasiaThemeUtils.C_TEXT());
            g.fill(15, itemY, leftW, itemY + 14, isSelected ? PhantasiaThemeUtils.C_BTN_ACT() : (isHovered ? PhantasiaThemeUtils.C_BTN_HOV() : PhantasiaThemeUtils.C_PANEL()));
            PhantasiaThemeUtils.drawBorderRect(g, 15, itemY, leftW - 15, 14, PhantasiaThemeUtils.C_BORDER());

            g.drawString(this.font, (isSelected ? "● " : "○ ") + registeredName, 22, itemY + 3, displayColor, false);

            if (!PhantasiaTheme.isBuiltIn(registeredName)) {
                int dX = leftW - 14;
                int dY = itemY + 1;
                boolean isDeleteHovered = mouseX >= dX && mouseX <= dX + 12 && mouseY >= dY && mouseY <= dY + 12;
                g.drawString(this.font, "✕", dX + 2, dY + 2, isDeleteHovered ? 0xFFFF5555 : 0x88FF5555, false);
            }

            itemY += 16;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int sidebarWidth = Math.max(175, this.width / 4);
            int leftW = this.width - sidebarWidth - 10;

            int pTop = 28;
            int pHeight = (this.height > 360) ? 80 : 60;
            int btnTitleY = pTop + pHeight + 10;
            int btnY = btnTitleY + 14;
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

                    if (mouseX >= 15 && mouseX <= leftW - 16) {
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
        int sidebarWidth = Math.max(175, this.width / 4);
        if (mouseX < this.width - sidebarWidth - 5) {
            List<String> registryNames = getVisibleRegistryNames();
            int pTop = 28;
            int pHeight = (this.height > 360) ? 80 : 60;
            int btnTitleY = pTop + pHeight + 10;
            int btnY = btnTitleY + 14;
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

    private record EditBoxWrapper(String label, EditBox box) {}
}