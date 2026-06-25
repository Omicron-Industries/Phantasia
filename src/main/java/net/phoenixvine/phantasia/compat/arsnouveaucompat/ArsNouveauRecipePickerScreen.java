package net.phoenixvine.phantasia.compat.arsnouveaucompat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;
import net.phoenixvine.phantasia.common.multisetup.IPhantasiaSetupRecipe;
import net.phoenixvine.phantasia.common.multisetup.PhantasiaMultiSetupRegistry;

import com.hollingsworth.arsnouveau.api.registry.RitualRegistry;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

/**
 * Recipe picker modal for the AN script editor.
 * Shows three tabs: Apparatus, Imbuement, Ritual.
 * Selecting an entry writes its ID to the script's {@code recipeId} field.
 */
@OnlyIn(Dist.CLIENT)
public class ArsNouveauRecipePickerScreen extends Screen {

    private static final int AN_BLUE = 0xFF5B6EFF;
    private static final int ROW_H = 16;
    private static final int HEADER_H = 22;

    private final Screen parent;
    private final PhantasiaScriptData data;
    private final Consumer<String> onPick;

    private enum Tab {
        APPARATUS,
        IMBUEMENT,
        RITUAL
    }

    private Tab activeTab = Tab.RITUAL;

    private EditBox searchBox;
    private int scrollOffset = 0;

    // panel bounds (set in init)
    private int pw, ph, px, py;

    public ArsNouveauRecipePickerScreen(Screen parent, PhantasiaScriptData data, Consumer<String> onPick) {
        super(Component.translatable("screen.phantasia.ars_recipe_picker.title"));
        this.parent = parent;
        this.data = data;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        super.init();
        pw = Math.min(480, this.width - 40);
        ph = Math.min(320, this.height - 60);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;

        searchBox = addRenderableWidget(
                new EditBox(font, px + 8, py + HEADER_H + 24 + 2, pw - 16, 14, Component.empty()));
        searchBox.setMaxLength(80);
        searchBox.setHint(Component.translatable("screen.phantasia.ars_recipe_picker.hint_search"));
        searchBox.setBordered(false);
        setInitialFocus(searchBox);
    }

    // ── data helpers ─────────────────────────────────────────────────────────

    private record Entry(String id, String label) {}

    private List<Entry> buildEntries() {
        String query = searchBox != null ? searchBox.getValue().toLowerCase() : "";
        List<Entry> out = new ArrayList<>();

        switch (activeTab) {
            case APPARATUS -> {
                PhantasiaMultiSetupRegistry.getAllSetups().stream()
                        .filter(s -> s.getId().getPath().contains("enchanting_apparatus"))
                        .flatMap(s -> s.getRecipes().stream())
                        .sorted(Comparator.comparing(r -> r.getId().toString()))
                        .forEach(r -> {
                            String id = r.getId().toString();
                            String label = labelFor(r);
                            if (query.isEmpty() || id.contains(query) || label.toLowerCase().contains(query))
                                out.add(new Entry(id, label));
                        });
            }
            case IMBUEMENT -> {
                PhantasiaMultiSetupRegistry.getAllSetups().stream()
                        .filter(s -> s.getId().getPath().contains("imbuement_chamber"))
                        .flatMap(s -> s.getRecipes().stream())
                        .sorted(Comparator.comparing(r -> r.getId().toString()))
                        .forEach(r -> {
                            String id = r.getId().toString();
                            String label = labelFor(r);
                            if (query.isEmpty() || id.contains(query) || label.toLowerCase().contains(query))
                                out.add(new Entry(id, label));
                        });
            }
            case RITUAL -> {
                RitualRegistry.getRitualItemMap().entrySet().stream()
                        .sorted(Comparator.comparing(e -> e.getKey().toString()))
                        .forEach(e -> {
                            String id = e.getKey().toString();
                            String label = e.getValue().getDescription().getString();
                            if (query.isEmpty() || id.contains(query) || label.toLowerCase().contains(query))
                                out.add(new Entry(id, label));
                        });
            }
        }
        return out;
    }

    private static String labelFor(IPhantasiaSetupRecipe r) {
        ItemStack out = r.getOutput();
        if (out != null && !out.isEmpty()) return out.getHoverName().getString();
        String path = r.getId().getPath();
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    // ── rendering ────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, this.width, this.height, 0xA0000000);

        g.fill(px, py, px + pw, py + ph, C_PANEL());
        g.fill(px, py, px + pw, py + 1, AN_BLUE);

        g.drawCenteredString(font, "Pick Recipe / Ritual", px + pw / 2, py + 7, AN_BLUE);

        // tabs
        int tabY = py + HEADER_H;
        int tabW = pw / 3;
        for (Tab t : Tab.values()) {
            int tx = px + t.ordinal() * tabW;
            boolean act = (t == activeTab);
            boolean hov = isOver(mx, my, tx, tabY, tabW, 20);
            g.fill(tx, tabY, tx + tabW, tabY + 20, act ? C_BTN_ACT() : (hov ? C_BTN_HOV() : C_BTN()));
            if (act) g.fill(tx, tabY + 19, tx + tabW, tabY + 20, AN_BLUE);
            g.drawCenteredString(font, tabLabel(t), tx + tabW / 2, tabY + 6, act ? AN_BLUE : C_DIM());
        }

        // search bar background
        int sbY = tabY + 20;
        g.fill(px + 4, sbY, px + pw - 4, sbY + 18, 0xFF111111);
        g.fill(px + 4, sbY, px + pw - 4, sbY + 1, 0xFF333333);

        super.render(g, mx, my, partial);

        // list
        int listY = sbY + 18;
        int listH = ph - (listY - py) - 24;
        int maxRows = listH / ROW_H;

        List<Entry> entries = buildEntries();
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, entries.size() - maxRows)));

        g.enableScissor(px, listY, px + pw, listY + listH);
        for (int i = 0; i < maxRows && (i + scrollOffset) < entries.size(); i++) {
            Entry e = entries.get(i + scrollOffset);
            int ry = listY + i * ROW_H;
            boolean hov = isOver(mx, my, px + 4, ry, pw - 8, ROW_H);
            boolean selected = e.id().equals(data.getRecipeId());
            int bg = selected ? 0xFF1A2A4A : (hov ? 0xFF222222 : 0);
            if (bg != 0) g.fill(px + 4, ry, px + pw - 4, ry + ROW_H, bg);
            if (selected) g.fill(px + 4, ry, px + 6, ry + ROW_H, AN_BLUE);
            int textColor = selected ? AN_BLUE : (hov ? C_TEXT() : C_DIM());
            g.drawString(font, e.label(), px + 10, ry + (ROW_H - 8) / 2, textColor, false);
            if (hov) {
                g.drawString(font, e.id(), px + pw - 4 - font.width(e.id()), ry + (ROW_H - 8) / 2, 0xFF444466, false);
            }
        }
        g.disableScissor();

        // scrollbar
        if (entries.size() > maxRows) {
            int sbX = px + pw - 5;
            float frac = (float) scrollOffset / (entries.size() - maxRows);
            int thumbH = Math.max(16, listH * maxRows / entries.size());
            int thumbY = listY + (int) (frac * (listH - thumbH));
            g.fill(sbX, listY, sbX + 4, listY + listH, 0xFF111111);
            g.fill(sbX, thumbY, sbX + 4, thumbY + thumbH, 0xFF555566);
        }

        // bottom bar
        int bbY = py + ph - 22;
        g.fill(px, bbY, px + pw, py + ph, 0xFF111111);
        // Clear button
        boolean clearHov = isOver(mx, my, px + 8, bbY + 4, 60, 14);
        g.fill(px + 8, bbY + 4, px + 68, bbY + 18, clearHov ? C_BTN_HOV() : C_BTN());
        g.drawCenteredString(font, "Clear", px + 38, bbY + 7, clearHov ? C_ACCENT() : C_DIM());
        // Close button
        boolean closeHov = isOver(mx, my, px + pw - 68, bbY + 4, 60, 14);
        g.fill(px + pw - 68, bbY + 4, px + pw - 8, bbY + 18, closeHov ? C_BTN_HOV() : C_BTN());
        g.drawCenteredString(font, "Close", px + pw - 38, bbY + 7, closeHov ? C_ACCENT() : C_DIM());
    }

    private static String tabLabel(Tab t) {
        return switch (t) {
            case APPARATUS -> "Apparatus";
            case IMBUEMENT -> "Imbuement";
            case RITUAL -> "Ritual";
        };
    }

    private static boolean isOver(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ── input ────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;

        int tabY = py + HEADER_H;
        int tabW = pw / 3;
        for (Tab t : Tab.values()) {
            int tx = px + t.ordinal() * tabW;
            if (isOver((int) mx, (int) my, tx, tabY, tabW, 20)) {
                activeTab = t;
                scrollOffset = 0;
                return true;
            }
        }

        // list rows
        int listY = tabY + 20 + 18;
        int listH = ph - (listY - py) - 24;
        int maxRows = listH / ROW_H;
        List<Entry> entries = buildEntries();
        for (int i = 0; i < maxRows && (i + scrollOffset) < entries.size(); i++) {
            int ry = listY + i * ROW_H;
            if (isOver((int) mx, (int) my, px + 4, ry, pw - 8, ROW_H)) {
                String picked = entries.get(i + scrollOffset).id();
                onPick.accept(picked);
                Minecraft.getInstance().setScreen(parent);
                return true;
            }
        }

        // bottom buttons
        int bbY = py + ph - 22;
        if (isOver((int) mx, (int) my, px + 8, bbY + 4, 60, 14)) {
            onPick.accept(null);
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        if (isOver((int) mx, (int) my, px + pw - 68, bbY + 4, 60, 14)) {
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
