package net.phoenixvine.phantasia.client.screens.subscreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.common.data.variant.PhantasiaVariantGroup;
import net.phoenixvine.phantasia.common.data.variant.PhantasiaVariantState;
import net.phoenixvine.phantasia.utils.PhantasiaThemeUtils;
import net.phoenixvine.phantasia.utils.PhantasiaUIUtils;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.*;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

@OnlyIn(Dist.CLIENT)
public class PhantasiaVariantsScreen extends Screen {

    private static final int PANEL_W = 280;
    private static final int PANEL_PADDING = 12;
    private static final int SECTION_HEADER_H = 18;
    private static final int ROW_H = 22;
    private static final int DROPDOWN_W = 160;
    private static final int DROPDOWN_ITEM_H = 16;
    private int dropdownScrollY = 0;
    private final int maxDropdownHeight = 16 * 8;

    private final Screen parent;
    private final PhantasiaVariantState variantState;

    private final Map<PhantasiaVariantGroup.Category, List<PhantasiaVariantGroup>> categorised = new LinkedHashMap<>();

    private final List<PhantasiaUIUtils.ButtonAction> activeButtons = new ArrayList<>();

    @org.jetbrains.annotations.Nullable
    private String openDropdownGroupId = null;

    private int scrollY = 0;
    private int totalContentH = 0;

    public PhantasiaVariantsScreen(Screen parent, PhantasiaVariantState variantState) {
        super(Component.translatable("screen.phantasia.variants.title"));
        this.parent = parent;
        this.variantState = variantState;

        for (PhantasiaVariantGroup.Category cat : PhantasiaVariantGroup.Category.values()) {
            categorised.put(cat, new ArrayList<>());
        }
        for (PhantasiaVariantGroup group : variantState.getGroups()) {
            if (!group.hasChoice()) continue;
            categorised.get(group.getCategory()).add(group);
        }

        categorised.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0xFF000000);
        activeButtons.clear();

        int panelX = (width - PANEL_W) / 2;
        int panelY = 20;
        int panelH = height - 40;

        g.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xE0101018);
        PhantasiaThemeUtils.drawBorderRect(g, panelX, panelY, PANEL_W, panelH, C_BORDER());

        int titleBarH = 20;
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + titleBarH, 0xFF161625);
        g.drawCenteredString(font, Component.translatable("screen.phantasia.variants.label_variants").getString(),
                panelX + PANEL_W / 2, panelY + 6, 0xFFFFFF);

        regBtn(g, mx, my, panelX + PANEL_W - 18, panelY + 3, 14, 14, "✕", () -> onClose());

        int contentX = panelX + PANEL_PADDING;
        int contentW = PANEL_W - PANEL_PADDING * 2;
        int contentY = panelY + titleBarH + 4;
        int contentH = panelH - titleBarH - 8;

        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        int scissorY = (int) ((height - contentY - contentH) * scale);
        RenderSystem.enableScissor(
                (int) (contentX * scale), scissorY,
                (int) (contentW * scale), (int) (contentH * scale));

        int y = contentY - scrollY;
        totalContentH = 0;

        if (categorised.isEmpty()) {
            g.drawString(font, Component.translatable("screen.phantasia.variants.msg_no_variants").getString(),
                    contentX, y + 4, 0xAAAAAA);
            totalContentH = 20;
        } else {
            for (Map.Entry<PhantasiaVariantGroup.Category, List<PhantasiaVariantGroup>> entry : categorised
                    .entrySet()) {
                PhantasiaVariantGroup.Category cat = entry.getKey();
                List<PhantasiaVariantGroup> groups = entry.getValue();

                g.fill(contentX, y, contentX + contentW, y + SECTION_HEADER_H, 0xFF1E1E30);
                g.drawString(font, "§e" + cat.displayName, contentX + 4, y + 5, 0xFFFFFF);
                y += SECTION_HEADER_H + 2;
                totalContentH += SECTION_HEADER_H + 2;

                for (PhantasiaVariantGroup group : groups) {
                    y = renderGroupRow(g, mx, my, contentX, contentW, y, group);
                    totalContentH += ROW_H + 2;
                }

                totalContentH += 4;
                y += 4;
            }
        }

        RenderSystem.disableScissor();

        if (openDropdownGroupId != null) {
            renderOpenDropdown(g, mx, my, panelX, panelY + titleBarH + 4,
                    contentX, contentW, panelY, panelH);
        }

        if (totalContentH > contentH) {
            int trackH = contentH;
            int thumbH = Math.max(20, contentH * contentH / totalContentH);
            int thumbY = contentY + (scrollY * (trackH - thumbH) / (totalContentH - contentH));
            g.fill(panelX + PANEL_W - 5, contentY, panelX + PANEL_W - 2,
                    contentY + trackH, 0xFF222233);
            g.fill(panelX + PANEL_W - 5, thumbY, panelX + PANEL_W - 2,
                    thumbY + thumbH, 0xFF6666AA);
        }

        super.render(g, mx, my, partial);
    }

    private int renderGroupRow(GuiGraphics g, int mx, int my,
                               int contentX, int contentW, int y,
                               PhantasiaVariantGroup group) {
        int rowY = y;

        boolean hovered = my >= rowY && my < rowY + ROW_H;
        int rowBg = hovered ? 0xFF1A1A2A : 0x00000000;
        g.fill(contentX, rowY, contentX + contentW, rowY + ROW_H, rowBg);

        String label = trunc(group.getLabel(), contentW - DROPDOWN_W - 12);
        g.drawString(font, label, contentX + 4, rowY + 7, 0xCCCCCC);

        int ddX = contentX + contentW - DROPDOWN_W;
        int ddY = rowY + 3;
        int ddH = ROW_H - 6;

        int selectedIdx = variantState.getSelection(group.getId());
        List<String> labels = group.getOptionLabels();
        String selectedLabel = selectedIdx >= 0 && selectedIdx < labels.size() ? labels.get(selectedIdx) : "?";

        boolean ddOpen = group.getId().equals(openDropdownGroupId);
        boolean ddHov = isOver(mx, my, ddX, ddY, DROPDOWN_W, ddH);

        int ddBg = ddOpen ? C_BTN_ACT() : (ddHov ? brighten(C_BTN(), 0.15f) : C_BTN());
        g.fill(ddX, ddY, ddX + DROPDOWN_W, ddY + ddH, ddBg);
        PhantasiaThemeUtils.drawBorderRect(g, ddX, ddY, DROPDOWN_W, ddH, C_BORDER());

        String truncLabel = trunc(selectedLabel, DROPDOWN_W - 18);
        g.drawString(font, truncLabel, ddX + 4, ddY + 3, 0xFFFFFF);
        g.drawString(font, ddOpen ? "▲" : "▼", ddX + DROPDOWN_W - 12, ddY + 3, 0xAAAAAA);

        final String gid = group.getId();
        activeButtons.add(new PhantasiaUIUtils.ButtonAction(ddX, ddY, DROPDOWN_W, ddH, () -> {
            openDropdownGroupId = gid.equals(openDropdownGroupId) ? null : gid;
        }));

        return rowY + ROW_H + 2;
    }

    private void renderOpenDropdown(GuiGraphics g, int mx, int my,
                                    int panelX, int contentStartY,
                                    int contentX, int contentW,
                                    int panelY, int panelH) {
        PhantasiaVariantGroup openGroup = variantState.getGroups().stream()
                .filter(gr -> gr.getId().equals(openDropdownGroupId))
                .findFirst().orElse(null);
        if (openGroup == null) {
            openDropdownGroupId = null;
            return;
        }

        int ddX = contentX + contentW - DROPDOWN_W;
        int numOptions = openGroup.getOptions().size();

        int rowY = contentStartY - scrollY;
        outer:
        for (Map.Entry<PhantasiaVariantGroup.Category, List<PhantasiaVariantGroup>> entry : categorised.entrySet()) {
            rowY += SECTION_HEADER_H + 2;
            for (PhantasiaVariantGroup g2 : entry.getValue()) {
                if (g2.getId().equals(openDropdownGroupId)) {
                    break outer;
                }
                rowY += ROW_H + 2;
            }
            rowY += 4;
        }

        int dropY = rowY + ROW_H - 1;

        int totalListH = numOptions * DROPDOWN_ITEM_H;
        int idealListH = Math.min(totalListH, maxDropdownHeight);

        int maxAllowedH = (panelY + panelH - 4) - dropY;

        int visibleListH = Math.max(DROPDOWN_ITEM_H, Math.min(idealListH, maxAllowedH));

        int maxScroll = Math.max(0, totalListH - visibleListH);
        if (dropdownScrollY > maxScroll) dropdownScrollY = maxScroll;
        if (dropdownScrollY < 0) dropdownScrollY = 0;

        g.pose().pushPose();
        g.pose().translate(0, 0, 300.0F);

        g.fill(ddX + 2, dropY + 2, ddX + DROPDOWN_W + 2, dropY + visibleListH + 2, 0x66000000);

        g.fill(ddX, dropY, ddX + DROPDOWN_W, dropY + visibleListH, 0xFF151520);
        PhantasiaThemeUtils.drawBorderRect(g, ddX, dropY, DROPDOWN_W, visibleListH, C_BORDER());

        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        int scissorY = (int) ((height - dropY - visibleListH) * scale);
        RenderSystem.enableScissor(
                (int) (ddX * scale), scissorY,
                (int) (DROPDOWN_W * scale), (int) (visibleListH * scale));

        int selectedIdx = variantState.getSelection(openGroup.getId());

        for (int i = 0; i < numOptions; i++) {
            int itemY = dropY + (i * DROPDOWN_ITEM_H) - dropdownScrollY;

            boolean itemHov = isOver(mx, my, ddX, itemY, DROPDOWN_W, DROPDOWN_ITEM_H) &&
                    isOver(mx, my, ddX, dropY, DROPDOWN_W, visibleListH);

            boolean itemSel = i == selectedIdx;

            int itemBg = itemSel ? 0xFF2A2A50 : (itemHov ? 0xFF1E1E35 : 0x00000000);
            g.fill(ddX, itemY, ddX + DROPDOWN_W, itemY + DROPDOWN_ITEM_H, itemBg);

            String optLabel = i < openGroup.getOptionLabels().size() ? openGroup.getOptionLabels().get(i) :
                    "Option " + i;
            int textColor = itemSel ? 0xAABBFF : (itemHov ? 0xEEEEEE : 0xBBBBBB);

            g.drawString(font, trunc(optLabel, DROPDOWN_W - 20), ddX + 4, itemY + 4, textColor);

            if (itemSel) {
                g.drawString(font, "✔", ddX + DROPDOWN_W - 12, itemY + 4, 0x66AAFF);
            }

            final int finalI = i;
            final String gid = openGroup.getId();
            activeButtons.add(new PhantasiaUIUtils.ButtonAction(ddX, itemY, DROPDOWN_W,
                    DROPDOWN_ITEM_H, () -> {
                        variantState.setSelection(gid, finalI);
                        openDropdownGroupId = null;
                        dropdownScrollY = 0;
                    }));
        }

        RenderSystem.disableScissor();

        if (totalListH > visibleListH) {
            int sbW = 2;
            int sbX = ddX + DROPDOWN_W - sbW - 1;
            int thumbH = Math.max(8, visibleListH * visibleListH / totalListH);
            int thumbY = dropY + (dropdownScrollY * (visibleListH - thumbH) / (totalListH - visibleListH));
            g.fill(sbX, dropY, sbX + sbW, dropY + visibleListH, 0x33FFFFFF);
            g.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, 0xAA6666AA);
        }

        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {

            if (openDropdownGroupId != null) {

                int ddX = ((width - PANEL_W) / 2) + PANEL_PADDING + (PANEL_W - PANEL_PADDING * 2) - DROPDOWN_W;

                int rowY = (20 + 20 + 4) - scrollY;
                outer:
                for (Map.Entry<PhantasiaVariantGroup.Category, List<PhantasiaVariantGroup>> entry : categorised
                        .entrySet()) {
                    rowY += SECTION_HEADER_H + 2;
                    for (PhantasiaVariantGroup g2 : entry.getValue()) {
                        if (g2.getId().equals(openDropdownGroupId)) break outer;
                        rowY += ROW_H + 2;
                    }
                    rowY += 4;
                }
                int dropY = rowY + ROW_H - 1;

                int totalListH = variantState.getGroups().stream()
                        .filter(gr -> gr.getId().equals(openDropdownGroupId))
                        .findFirst().map(gr -> gr.getOptions().size() * DROPDOWN_ITEM_H).orElse(0);
                int idealListH = Math.min(totalListH, maxDropdownHeight);
                int maxAllowedH = (20 + (height - 40) - 4) - dropY;
                int visibleListH = Math.max(DROPDOWN_ITEM_H, Math.min(idealListH, maxAllowedH));

                boolean clickIsInsideDropdownArea = isOver((int) mx, (int) my, ddX, dropY, DROPDOWN_W, visibleListH);

                if (clickIsInsideDropdownArea) {

                    for (int i = activeButtons.size() - 1; i >= 0; i--) {
                        PhantasiaUIUtils.ButtonAction action = activeButtons.get(i);

                        if (action.hit(mx, my) && action.y() >= dropY &&
                                (action.y() + action.h()) <= (dropY + visibleListH)) {
                            action.action().run();
                            return true;
                        }
                    }
                    return true;

                } else {

                    for (int i = activeButtons.size() - 1; i >= 0; i--) {
                        PhantasiaUIUtils.ButtonAction action = activeButtons.get(i);
                        if (action.hit(mx, my)) {
                            action.action().run();
                            return true;
                        }
                    }

                    openDropdownGroupId = null;
                    dropdownScrollY = 0;
                    return true;
                }
            }

            for (int i = activeButtons.size() - 1; i >= 0; i--) {
                PhantasiaUIUtils.ButtonAction action = activeButtons.get(i);
                if (action.hit(mx, my)) {
                    action.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (openDropdownGroupId != null) {
            PhantasiaVariantGroup openGroup = variantState.getGroups().stream()
                    .filter(gr -> gr.getId().equals(openDropdownGroupId))
                    .findFirst().orElse(null);

            if (openGroup != null) {
                int totalListH = openGroup.getOptions().size() * DROPDOWN_ITEM_H;
                int visibleListH = Math.min(totalListH, maxDropdownHeight);
                int maxScroll = Math.max(0, totalListH - visibleListH);

                if (maxScroll > 0) {
                    dropdownScrollY = Math.max(0,
                            Math.min(dropdownScrollY - (int) (delta * DROPDOWN_ITEM_H), maxScroll));
                    return true;
                }
            }
        }

        scrollY = Math.max(0, Math.min(scrollY - (int) (delta * 12),
                Math.max(0, totalContentH - (height - 60))));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void regBtn(GuiGraphics g, int mx, int my,
                        int x, int y, int w, int h, String label, Runnable action) {
        boolean hov = isOver(mx, my, x, y, w, h);
        PhantasiaThemeUtils.drawThemedBtn(g, font, x, y, w, h, label, hov, C_BTN());
        activeButtons.add(new PhantasiaUIUtils.ButtonAction(x, y, w, h, action));
    }

    private boolean isOver(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private String trunc(String s, int maxPx) {
        if (s == null) return "";
        while (font.width(s) > maxPx && s.length() > 2)
            s = s.substring(0, s.length() - 2) + "…";
        return s;
    }

    private static int brighten(int argb, float amount) {
        int a = (argb >> 24) & 0xFF;
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * (1 + amount)));
        int g = Math.min(255, (int) (((argb >> 8) & 0xFF) * (1 + amount)));
        int b = Math.min(255, (int) ((argb & 0xFF) * (1 + amount)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
