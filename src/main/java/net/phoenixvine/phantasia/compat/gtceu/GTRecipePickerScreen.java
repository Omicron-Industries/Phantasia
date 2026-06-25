package net.phoenixvine.phantasia.compat.gtceu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.client.screens.PhantasiaScreen;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

/**
 * Modal subscreen for picking a GTCEu recipe ID.
 * Shows all gtceu-namespaced recipes in a searchable, scrollable list.
 */
@OnlyIn(Dist.CLIENT)
public class GTRecipePickerScreen extends Screen {

    private static final int ROW_H = 16;
    private static final int HEADER_H = 30;

    private final Screen parent;
    private final String currentId;
    private final Consumer<String> onPick;

    private EditBox searchBox;
    private int scrollOffset = 0;

    private int pw, ph, px, py;

    private List<String> allIds = null;

    public GTRecipePickerScreen(Screen parent, String currentId, Consumer<String> onPick) {
        super(Component.translatable("screen.phantasia.gt_recipe_picker.title"));
        this.parent = parent;
        this.currentId = currentId;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        super.init();
        pw = Math.min(500, this.width - 40);
        ph = Math.min(340, this.height - 60);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;

        searchBox = addRenderableWidget(new EditBox(font, px + 8, py + HEADER_H + 2, pw - 16, 14, Component.empty()));
        searchBox.setMaxLength(128);
        searchBox.setHint(Component.translatable("screen.phantasia.gt_recipe_picker.hint_search"));
        searchBox.setBordered(false);
        searchBox.setResponder(v -> { scrollOffset = 0; });
        setInitialFocus(searchBox);

        allIds = null;
    }

    private List<String> buildEntries() {
        if (allIds == null) allIds = loadAllGTRecipeIds();
        String query = searchBox != null ? searchBox.getValue().toLowerCase().trim() : "";
        if (query.isEmpty()) return allIds;
        List<String> out = new ArrayList<>();
        for (String id : allIds) {
            if (id.contains(query)) out.add(id);
        }
        return out;
    }

    private static List<String> loadAllGTRecipeIds() {
        List<String> out = new ArrayList<>();
        try {
            var level = Minecraft.getInstance().level;
            if (level != null) {
                for (Recipe<?> r : level.getRecipeManager().getRecipes()) {
                    ResourceLocation id = r.getId();
                    if ("gtceu".equals(id.getNamespace())) {
                        out.add(id.toString());
                    }
                }
            }
        } catch (Exception ignored) {}
        out.sort(Comparator.naturalOrder());
        return out;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        // Render parent as background if possible
        if (parent instanceof PhantasiaScreen ps) {
            ps.renderAsBackground(g, partial);
        } else {
            g.fill(0, 0, this.width, this.height, C_BG());
        }
        g.fill(0, 0, this.width, this.height, 0x80000000);

        g.fill(px, py, px + pw, py + ph, C_PANEL());
        g.fill(px, py, px + pw, py + 1, C_ACCENT());
        g.drawCenteredString(font, "Pick GT Recipe", px + pw / 2, py + 10, C_ACCENT());

        // Search bar background
        int sbY = py + HEADER_H;
        g.fill(px + 4, sbY, px + pw - 4, sbY + 18, 0xFF111111);
        g.fill(px + 4, sbY, px + pw - 4, sbY + 1, 0xFF333333);

        super.render(g, mx, my, partial);

        // List
        int listY = sbY + 18;
        int listH = ph - (listY - py) - 26;
        int maxRows = listH / ROW_H;
        List<String> entries = buildEntries();
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, entries.size() - maxRows)));

        g.enableScissor(px, listY, px + pw, listY + listH);
        for (int i = 0; i < maxRows && (i + scrollOffset) < entries.size(); i++) {
            String id = entries.get(i + scrollOffset);
            int ry = listY + i * ROW_H;
            boolean hov = isIn(mx, my, px + 4, ry, pw - 8, ROW_H);
            boolean sel = id.equals(currentId);
            int bg = sel ? 0xFF1A2A4A : (hov ? 0xFF222233 : 0);
            if (bg != 0) g.fill(px + 4, ry, px + pw - 4, ry + ROW_H, bg);
            if (sel) g.fill(px + 4, ry, px + 6, ry + ROW_H, C_ACCENT());
            g.drawString(font, id, px + 10, ry + (ROW_H - 8) / 2, sel ? C_ACCENT() : (hov ? C_TEXT() : C_DIM()), false);
        }
        g.disableScissor();

        // Scrollbar
        if (entries.size() > maxRows && maxRows > 0) {
            int sbX = px + pw - 5;
            float frac = (float) scrollOffset / Math.max(1, entries.size() - maxRows);
            int thumbH = Math.max(16, listH * maxRows / entries.size());
            int thumbY = listY + (int) (frac * (listH - thumbH));
            g.fill(sbX, listY, sbX + 4, listY + listH, 0xFF111111);
            g.fill(sbX, thumbY, sbX + 4, thumbY + thumbH, 0xFF555566);
        }

        // Bottom bar
        int bbY = py + ph - 22;
        g.fill(px, bbY, px + pw, py + ph, 0xFF111111);
        boolean clearHov = isIn(mx, my, px + 8, bbY + 4, 60, 14);
        g.fill(px + 8, bbY + 4, px + 68, bbY + 18, clearHov ? C_BTN_HOV() : C_BTN());
        g.drawCenteredString(font, "Clear", px + 38, bbY + 7, clearHov ? C_ACCENT() : C_DIM());
        boolean closeHov = isIn(mx, my, px + pw - 68, bbY + 4, 60, 14);
        g.fill(px + pw - 68, bbY + 4, px + pw - 8, bbY + 18, closeHov ? C_BTN_HOV() : C_BTN());
        g.drawCenteredString(font, "Close", px + pw - 38, bbY + 7, closeHov ? C_ACCENT() : C_DIM());

        // Result count
        g.drawString(font, entries.size() + " recipes", px + pw - 8 - font.width(entries.size() + " recipes"),
                py + 10, C_DIM(), false);
    }

    private static boolean isIn(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;

        int sbY = py + HEADER_H;
        int listY = sbY + 18;
        int listH = ph - (listY - py) - 26;
        int maxRows = listH / ROW_H;
        List<String> entries = buildEntries();
        for (int i = 0; i < maxRows && (i + scrollOffset) < entries.size(); i++) {
            int ry = listY + i * ROW_H;
            if (isIn((int) mx, (int) my, px + 4, ry, pw - 8, ROW_H)) {
                onPick.accept(entries.get(i + scrollOffset));
                Minecraft.getInstance().setScreen(parent);
                return true;
            }
        }

        int bbY = py + ph - 22;
        if (isIn((int) mx, (int) my, px + 8, bbY + 4, 60, 14)) {
            onPick.accept(null);
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        if (isIn((int) mx, (int) my, px + pw - 68, bbY + 4, 60, 14)) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scrollOffset = Math.max(0, scrollOffset - (int) Math.signum(delta));
        return true;
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (kc == GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return super.keyPressed(kc, sc, mod);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
