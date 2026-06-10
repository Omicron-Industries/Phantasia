package net.phoenixvine.phantasia.client.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phantasia.common.PhantasiaGuideData;
import net.phoenixvine.phantasia.common.PhantasiaGuideData.PageData;
import net.phoenixvine.phantasia.common.PhantasiaGuideRegistry;
import net.phoenixvine.phantasia.common.PhantasiaSceneData;
import net.phoenixvine.phantasia.common.PhantasiaScenes;

import java.util.*;

/**
 * PhantasiaGuideScreen — GuideME-style reader.
 *
 * Accepts two source types:
 *
 *   1. {@link PhantasiaGuideData}  — pure text + item pages, no 3D.
 *   2. {@link PhantasiaSceneData}  — derives guide pages from StepData
 *      (caption → headline, description → text, placement items → cards).
 *
 * Both are normalised into {@link GuidePage} records at init time so the
 * render path is identical. The constructor is overloaded for each source;
 * the fromScene factory also accepts a starting page index so SceneScreen
 * can deep-link into a specific step's guide text.
 *
 * Navigation: ← / → arrow keys or Space, scroll wheel within a page.
 */
@OnlyIn(Dist.CLIENT)
public class PhantasiaGuideScreen extends Screen {

    // ── Theme ─────────────────────────────────────────────────────────────────
    private static final int C_BG        = 0xFF07070E;
    private static final int C_BAR       = 0xEE0A0A14;
    private static final int C_CARD      = 0xCC101022;
    private static final int C_CARD_HOV  = 0xCC182042;
    private static final int C_ACCENT    = 0xFF4FC3F7;
    private static final int C_BTN       = 0xBB151528;
    private static final int C_BTN_HOV   = 0xBB1A2840;
    private static final int C_TEXT      = 0xFFDDDDDD;
    private static final int C_DIM       = 0xFF667788;
    private static final int C_HEAD      = 0xFFEEEEFF;
    private static final int C_NAV       = 0xDD0A0A14;
    private static final int C_RULE      = 0x334FC3F7;
    private static final int C_SCROLL    = 0x44FFFFFF;
    private static final int C_SCROLL_TH = 0xAA4FC3F7;

    private static final int TOP_H   = 22;
    private static final int NAV_H   = 30;
    private static final int COL_W   = 360;
    private static final int CARD_W  = 94;
    private static final int CARD_H  = 90;
    private static final int CARD_GAP = 6;

    // ── Normalised page model ─────────────────────────────────────────────────

    /**
     * Flat page record used for rendering — source-agnostic.
     *
     * @param headline       Large text at top of page (may be null).
     * @param text           Body text, supports \n and § codes (may be null).
     * @param cards          Item cards to show below the body.
     * @param mistakes       Mistake banners (from SceneData only).
     * @param linkedGuideId  ID of a guide to link at the bottom.
     * @param linkedSceneId  ID of a scene to link at the bottom.
     * @param editTarget     Non-null if "Edit" should open a scene editor.
     */
    private record GuidePage(
        String headline,
        String text,
        List<CardEntry> cards,
        List<PhantasiaSceneData.SceneMistakeData> mistakes,
        String linkedGuideId,
        String linkedSceneId,
        Object editTarget   // PhantasiaGuideData or PhantasiaSceneData
    ) {}

    private record CardEntry(PhantasiaSceneData.ItemConditionData item, ItemStack stack) {}

    // ── Source tracking ───────────────────────────────────────────────────────

    /** Title shown in the top bar. */
    private final String guideTitle;
    /** The original source for the "Edit" button. Either GuideData or SceneData. */
    private final Object source;

    // ── State ─────────────────────────────────────────────────────────────────

    private final Screen parent;
    private final List<GuidePage> pages = new ArrayList<>();
    private int pageIndex  = 0;
    private int scrollY    = 0;
    private int lastContentH = 0;

    private record Btn(int x, int y, int w, int h, Runnable action) {
        boolean hit(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private final List<Btn> btns = new ArrayList<>();

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Pure guide — source is {@link PhantasiaGuideData}. */
    public PhantasiaGuideScreen(Screen parent, PhantasiaGuideData guide) {
        super(Component.literal(guide.title));
        this.parent     = parent;
        this.guideTitle = guide.title;
        this.source     = guide;
    }

    /** Guide derived from a scene's steps — source is {@link PhantasiaSceneData}. */
    public PhantasiaGuideScreen(Screen parent, PhantasiaSceneData scene) {
        super(Component.literal(scene.name != null ? scene.name : scene.id));
        this.parent     = parent;
        this.guideTitle = scene.name != null ? scene.name : scene.id;
        this.source     = scene;
    }

    /**
     * Guide derived from a scene, opening at a specific step index.
     * Used by SceneScreen/SceneViewerScreen to deep-link to the current step.
     */
    public static PhantasiaGuideScreen fromScene(Screen parent, PhantasiaSceneData scene, int stepIndex) {
        PhantasiaGuideScreen s = new PhantasiaGuideScreen(parent, scene);
        s.pageIndex = stepIndex;
        return s;
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        pages.clear();

        if (source instanceof PhantasiaGuideData gd) {
            buildFromGuideData(gd);
        } else if (source instanceof PhantasiaSceneData sd) {
            buildFromSceneData(sd);
        }

        // Fallback page so the screen is never empty
        if (pages.isEmpty()) {
            pages.add(new GuidePage(guideTitle, null,
                List.of(), List.of(), null, null, source));
        }

        pageIndex = Mth.clamp(pageIndex, 0, pages.size() - 1);
        scrollY   = 0;
    }

    // ── Page builders ─────────────────────────────────────────────────────────

    private void buildFromGuideData(PhantasiaGuideData gd) {
        for (PageData pd : gd.pages) {
            if (!pd.hasContent()) continue;
            List<CardEntry> cards = resolveCards(pd.items);
            pages.add(new GuidePage(
                pd.headline, pd.text, cards,
                List.of(),           // no scene mistakes
                pd.guideId, pd.sceneId,
                gd                   // edit target
            ));
        }
    }

    private void buildFromSceneData(PhantasiaSceneData sd) {
        // Index mistakes by placement for later lookup
        List<PhantasiaSceneData.SceneMistakeData> sceneMistakes =
            sd.mistakes != null ? sd.mistakes : List.of();

        for (PhantasiaSceneData.StepData step : sd.steps) {
            List<CardEntry> cards = new ArrayList<>();
            if (step.showItems) {
                for (PhantasiaSceneData.PlacementData pd : sd.placements)
                    cards.addAll(resolveCards(pd.items));
            }

            List<PhantasiaSceneData.SceneMistakeData> pageErrors = sceneMistakes.stream()
                .filter(m -> m.placements == null || m.placements.isEmpty())
                .toList();

            boolean hasContent = (step.caption    != null && !step.caption.isBlank())
                || (step.description != null && !step.description.isBlank())
                || !cards.isEmpty()
                || !pageErrors.isEmpty();

            if (hasContent) {
                pages.add(new GuidePage(
                    step.caption, step.description, cards,
                    pageErrors, null, null, sd
                ));
            }
        }
    }

    private List<CardEntry> resolveCards(List<PhantasiaSceneData.ItemConditionData> items) {
        List<CardEntry> out = new ArrayList<>();
        if (items == null) return out;
        for (PhantasiaSceneData.ItemConditionData it : items)
            out.add(new CardEntry(it, resolveStack(it)));
        return out;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        btns.clear();
        g.fillGradient(0, 0, width, height, 0xFF07070E, 0xFF0D0D1E);
        renderTopBar(g, mx, my);
        renderContent(g, mx, my);
        renderNavBar(g, mx, my);
        super.render(g, mx, my, partial);
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private void renderTopBar(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, width, TOP_H, C_BAR);
        g.fill(0, TOP_H - 1, width, TOP_H, C_ACCENT);
        g.drawCenteredString(font, guideTitle, width / 2, (TOP_H - 8) / 2, C_ACCENT);

        // FIX: Removed the '4' integer argument. The 4th argument must be the String label.
        topBtnLeft(g, mx, my, 4, "← Back", this::onClose);

        if (canEdit()) {
            topBtnRight(g, mx, my, width - 4, "✏ Edit", this::openEditor);
        }
    }
    private boolean canEdit() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getAbilities().instabuild;
    }

    // ── Content ───────────────────────────────────────────────────────────────

    private void renderContent(GuiGraphics g, int mx, int my) {
        if (pages.isEmpty()) return;
        GuidePage page = pages.get(pageIndex);

        int areaTop    = TOP_H;
        int areaBottom = height - NAV_H;

        int colW = Math.min(COL_W, width - 48);
        int colX = (width - colW) / 2;

        g.enableScissor(0, areaTop, width, areaBottom);

        int y = areaTop + 14 - scrollY;

        // ── Headline ─────────────────────────────────────────────────────────
        if (page.headline() != null && !page.headline().isBlank()) {
            g.fill(colX, y, colX + colW, y + 1, C_ACCENT);
            y += 7;

            float scale  = 1.5f;
            int   scaledW = (int)(colW / scale);
            for (var line : font.split(Component.literal(page.headline()), scaledW)) {
                g.pose().pushPose();
                g.pose().translate(colX, y, 0);
                g.pose().scale(scale, scale, 1f);
                g.drawString(font, line, 0, 0, C_HEAD, false);
                g.pose().popPose();
                y += (int)(font.lineHeight * scale) + 2;
            }
            y += 4;
        } else {
            g.fill(colX, y, colX + colW, y + 1, C_RULE);
            y += 8;
        }

        // Step / page counter
        if (pages.size() > 1) {
            g.drawString(font,
                (source instanceof PhantasiaGuideData ? "Page " : "Step ")
                + (pageIndex + 1) + " of " + pages.size(),
                colX, y, C_DIM, false);
            y += font.lineHeight + 5;
        }
        y += 4;

        // ── Body text ────────────────────────────────────────────────────────
        if (page.text() != null && !page.text().isBlank()) {
            // Split on explicit \n first, then wrap each paragraph
            for (String para : page.text().split("\n", -1)) {
                if (para.isBlank()) {
                    y += font.lineHeight / 2; // paragraph gap
                } else {
                    for (var line : font.split(Component.literal(para), colW)) {
                        g.drawString(font, line, colX, y, C_TEXT, false);
                        y += font.lineHeight + 2;
                    }
                }
            }
            y += 10;
        }

        // ── Item cards ───────────────────────────────────────────────────────
        if (!page.cards().isEmpty()) {
            g.fill(colX, y, colX + colW, y + 1, C_RULE);
            y += 6;
            g.drawString(font, "Items", colX, y, C_DIM, false);
            y += font.lineHeight + 6;

            int perRow = Math.max(1, (colW + CARD_GAP) / (CARD_W + CARD_GAP));
            int rowStartY = y;
            int col = 0;

            for (int i = 0; i < page.cards().size(); i++) {
                CardEntry ce = page.cards().get(i);
                int cx = colX + col * (CARD_W + CARD_GAP);
                int cy = rowStartY;
                int cyScreen = cy + scrollY;

                boolean hov = over(mx, my, cx, cyScreen, CARD_W, CARD_H)
                    && cyScreen >= areaTop && cyScreen < areaBottom;

                renderCard(g, ce, cx, cy, hov);

                final CardEntry fce = ce;
                btns.add(new Btn(cx, cyScreen, CARD_W, CARD_H, () -> onCardClick(fce)));

                col++;
                if (col >= perRow) {
                    col = 0;
                    rowStartY += CARD_H + CARD_GAP;
                }
            }
            y = rowStartY + (col > 0 ? CARD_H + CARD_GAP : 0) + 8;
        }

        // ── Mistake banners ───────────────────────────────────────────────────
        for (PhantasiaSceneData.SceneMistakeData m : page.mistakes()) {
            y = renderMistakeBanner(g, m, colX, y, colW) + 6;
        }

        // ── Link buttons ─────────────────────────────────────────────────────
        if (page.linkedGuideId() != null) {
            y = renderLinkBtn(g, mx, my, colX, y, colW,
                "Continue reading →",
                () -> openLinkedGuide(page.linkedGuideId())) + 6;
        }
        if (page.linkedSceneId() != null) {
            y = renderLinkBtn(g, mx, my, colX, y, colW,
                "▶ View in 3D →",
                () -> openLinkedScene(page.linkedSceneId())) + 6;
        }

        g.disableScissor();

        // Scrollbar
        lastContentH = (y + scrollY) - (areaTop + 14);
        int areaH    = areaBottom - areaTop;
        if (lastContentH > areaH) {
            int sbX    = width - 5;
            int maxSc  = lastContentH - areaH;
            int thumbH = Math.max(16, areaH * areaH / lastContentH);
            int thumbY = areaTop + (maxSc > 0 ? (areaH - thumbH) * scrollY / maxSc : 0);
            g.fill(sbX, areaTop, sbX + 3, areaBottom, C_SCROLL);
            g.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, C_SCROLL_TH);
        }
    }

    // ── Card ──────────────────────────────────────────────────────────────────

    private void renderCard(GuiGraphics g, CardEntry ce, int cx, int cy, boolean hov) {
        PhantasiaSceneData.ItemConditionData it = ce.item();
        int accent = it.accentColor();

        g.fill(cx, cy, cx + CARD_W, cy + CARD_H, hov ? C_CARD_HOV : C_CARD);
        g.fill(cx, cy, cx + CARD_W, cy + 2, accent);
        if (hov) {
            g.fill(cx,              cy, cx + 1,      cy + CARD_H, accent);
            g.fill(cx + CARD_W - 1, cy, cx + CARD_W, cy + CARD_H, accent);
            g.fill(cx, cy + CARD_H - 1, cx + CARD_W, cy + CARD_H, accent);
        }

        // 32×32 icon (2× scale, 16×16 item)
        int iconSize = 32;
        int iconX = cx + (CARD_W - iconSize) / 2;
        int iconY = cy + 8;
        if (!ce.stack().isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(iconX, iconY, 100);
            g.pose().scale(2f, 2f, 1f);
            g.renderItem(ce.stack(), 0, 0);
            g.pose().popPose();
        } else {
            g.fill(iconX + 2, iconY + 2, iconX + iconSize - 2, iconY + iconSize - 2, 0x44FF0000);
            g.drawCenteredString(font, "?", cx + CARD_W / 2, iconY + iconSize / 2 - 4, 0xFFFF5252);
        }

        // Count badge
        if (it.count > 1) {
            String cnt = String.valueOf(it.count);
            g.drawString(font, cnt,
                cx + CARD_W - 4 - font.width(cnt), cy + CARD_H - 30, 0xFFFFFFFF, true);
        }

        // Label — truncated
        String label = it.displayLabel();
        if (font.width(label) > CARD_W - 6)
            label = font.plainSubstrByWidth(label, CARD_W - 6 - font.width("…")) + "…";
        g.drawCenteredString(font, label, cx + CARD_W / 2, cy + CARD_H - 26,
            hov ? 0xFFFFFFFF : C_TEXT);

        // Type pill
        String pillTxt = switch (it.type == null ? "input" : it.type.toLowerCase(Locale.ROOT)) {
            case "output"   -> "Out ▲";
            case "catalyst" -> "Cat ◆";
            default         -> "In  ▼";
        };
        int pillW = font.width(pillTxt) + 8;
        int pillX = cx + (CARD_W - pillW) / 2;
        int pillY = cy + CARD_H - 14;
        int pillBg = (accent & 0xFFFFFF) | (hov ? 0xCC000000 : 0x88000000);
        g.fill(pillX, pillY, pillX + pillW, pillY + 11, pillBg);
        g.drawString(font, pillTxt, pillX + 4, pillY + 2, 0xFFFFFFFF, false);

        // Microscene indicator
        if (it.microsceneId != null && !it.microsceneId.isBlank())
            g.drawString(font, "▶", cx + CARD_W - 9, cy + 3,
                hov ? C_ACCENT : 0x554FC3F7, false);
    }

    // ── Mistake banner ────────────────────────────────────────────────────────

    private int renderMistakeBanner(GuiGraphics g,
            PhantasiaSceneData.SceneMistakeData m, int x, int y, int colW) {
        if (m.description == null || m.description.isBlank()) return y;
        int col = m.severityColor();
        String icon = switch (m.severity == null ? "WARNING"
                : m.severity.toUpperCase(Locale.ROOT)) {
            case "ERROR" -> "✖ ";
            case "INFO"  -> "ℹ ";
            default      -> "⚠ ";
        };
        var lines = font.split(Component.literal(icon + m.description), colW - 16);
        int bannerH = lines.size() * (font.lineHeight + 1) + 8;
        g.fill(x, y, x + colW, y + bannerH, (col & 0xFFFFFF) | 0x33000000);
        g.fill(x, y, x + 2, y + bannerH, col);
        int ty = y + 4;
        for (var line : lines) {
            g.drawString(font, line, x + 8, ty, col, false);
            ty += font.lineHeight + 1;
        }
        return y + bannerH;
    }

    // ── Link button ───────────────────────────────────────────────────────────

    private int renderLinkBtn(GuiGraphics g, int mx, int my,
            int colX, int y, int colW, String label, Runnable action) {
        int bw = font.width(label) + 16;
        int bh = 14;
        boolean hov = over(mx, my, colX, y, bw, bh);
        g.fill(colX, y, colX + bw, y + bh, hov ? C_BTN_HOV : C_BTN);
        if (hov) g.fill(colX, y, colX + bw, y + 1, C_ACCENT);
        g.drawString(font, label, colX + 8, y + 3, hov ? C_ACCENT : C_TEXT, false);
        btns.add(new Btn(colX, y + scrollY, bw, bh, action));
        return y + bh;
    }

    // ── Nav bar ───────────────────────────────────────────────────────────────

    private void renderNavBar(GuiGraphics g, int mx, int my) {
        int navY = height - NAV_H;
        g.fill(0, navY, width, height, C_NAV);
        g.fill(0, navY, width, navY + 1, 0x33FFFFFF);

        int midX = width / 2;
        int bY = navY + 6, bH = NAV_H - 12;

        navBtn(g, mx, my, midX - font.width("◀  Prev") - 14 - 26, bY,
            font.width("◀  Prev") + 14, bH, "◀  Prev",
            pageIndex > 0, () -> navigate(-1));

        g.drawCenteredString(font, (pageIndex + 1) + " / " + pages.size(),
            midX, bY + (bH - 8) / 2, C_DIM);

        navBtn(g, mx, my, midX + 26, bY,
            font.width("Next  ▶") + 14, bH, "Next  ▶",
            pageIndex < pages.size() - 1, () -> navigate(+1));
    }

    private void navBtn(GuiGraphics g, int mx, int my,
            int x, int y, int w, int h, String label, boolean enabled, Runnable action) {
        boolean hov = enabled && over(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, hov ? C_BTN_HOV : (enabled ? C_BTN : 0x33111128));
        if (hov) g.fill(x, y, x + w, y + 1, C_ACCENT);
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2,
            hov ? C_ACCENT : (enabled ? C_TEXT : C_DIM));
        if (enabled) btns.add(new Btn(x, y, w, h, action));
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        for (Btn b : btns) if (b.hit(mx, my)) { b.action().run(); return true; }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int areaH    = height - NAV_H - TOP_H;
        int maxScroll = Math.max(0, lastContentH - areaH);
        scrollY = Mth.clamp(scrollY - (int)(delta * 14), 0, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (kc == 256) { onClose(); return true; }
        if (kc == 262 || kc == 32) { navigate(+1); return true; }  // → or Space
        if (kc == 263)              { navigate(-1); return true; }  // ←
        if (kc == 265) { scrollY = Math.max(0, scrollY - 14); return true; } // ↑
        if (kc == 264) { scrollY += 14; return true; }              // ↓
        return super.keyPressed(kc, sc, mod);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void navigate(int delta) {
        int next = Mth.clamp(pageIndex + delta, 0, pages.size() - 1);
        if (next != pageIndex) { pageIndex = next; scrollY = 0; }
    }

    private void onCardClick(CardEntry ce) {
        String mid = ce.item().microsceneId;
        if (mid == null || mid.isBlank()) return;
        PhantasiaGuideData guide = PhantasiaGuideRegistry.get(mid);
        if (guide != null) {
            Minecraft.getInstance().setScreen(new PhantasiaGuideScreen(this, guide));
            return;
        }
        PhantasiaSceneData scene = PhantasiaScenes.all().stream()
            .filter(s -> mid.equals(s.id)).findFirst().orElse(null);
        if (scene != null)
            Minecraft.getInstance().setScreen(new PhantasiaGuideScreen(this, scene));
    }

    private void openLinkedGuide(String id) {
        PhantasiaGuideData guide = PhantasiaGuideRegistry.get(id);
        if (guide != null)
            Minecraft.getInstance().setScreen(new PhantasiaGuideScreen(this, guide));
    }

    private void openLinkedScene(String id) {
        PhantasiaSceneData scene = PhantasiaScenes.all().stream()
            .filter(s -> id.equals(s.id)).findFirst().orElse(null);
        if (scene != null)
            Minecraft.getInstance().setScreen(new PhantasiaSceneViewerScreen(this, scene));
    }

    private void openEditor() {
        if (source instanceof PhantasiaGuideData gd)
            Minecraft.getInstance().setScreen(new PhantasiaGuideEditorScreen(this, gd));
        else if (source instanceof PhantasiaSceneData sd)
            Minecraft.getInstance().setScreen(new PhantasiaSceneEditorScreen(this, sd));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ItemStack resolveStack(PhantasiaSceneData.ItemConditionData it) {
        if (it.item == null || it.item.isBlank()) return ItemStack.EMPTY;
        try {
            ResourceLocation rl = it.item.contains(":")
                ? new ResourceLocation(it.item)
                : new ResourceLocation("minecraft", it.item);
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            return (item == null || item == Items.AIR)
                ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, it.count));
        } catch (Exception e) { return ItemStack.EMPTY; }
    }

    private boolean over(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
    private boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // Add these helper methods to the bottom of PhantasiaGuideScreen

    private void topBtnLeft(GuiGraphics g, int mx, int my, int x, String label, Runnable action) {
        int bw = font.width(label) + 12;
        int bh = TOP_H - 6;
        int by = (TOP_H - bh) / 2; // Automatically centers vertically in the top bar
        boolean hov = over(mx, my, x, by, bw, bh);

        g.fill(x, by, x + bw, by + bh, hov ? C_BTN_HOV : C_BTN);
        if (hov) g.fill(x, by, x + bw, by + 1, C_ACCENT);
        g.drawString(font, label, x + 6, by + (bh - 8) / 2, hov ? C_ACCENT : C_TEXT, false);

        btns.add(new Btn(x, by, bw, bh, action));
    }

    private void topBtnRight(GuiGraphics g, int mx, int my, int rightX, String label, Runnable action) {
        int bw = font.width(label) + 12;
        int bh = TOP_H - 6;
        int bx = rightX - bw; // Offset to the left from the right edge
        int by = (TOP_H - bh) / 2;
        boolean hov = over(mx, my, bx, by, bw, bh);

        g.fill(bx, by, bx + bw, by + bh, hov ? C_BTN_HOV : C_BTN);
        if (hov) g.fill(bx, by, bx + bw, by + 1, C_ACCENT);
        g.drawString(font, label, bx + 6, by + (bh - 8) / 2, hov ? C_ACCENT : C_TEXT, false);

        btns.add(new Btn(bx, by, bw, bh, action));
    }

}
