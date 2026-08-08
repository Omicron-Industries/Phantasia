package net.phoenixvine.phantasia.client.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.client.screens.editors.PhantasiaSceneEditorScreen;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneData;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaSceneLoader;
import net.phoenixvine.phantasia.common.data.scene.PhantasiaScenes;

import org.lwjgl.glfw.GLFW;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

@OnlyIn(Dist.CLIENT)
public class PhantasiaSceneCreateScreen extends Screen {

    private final Screen parent;
    private EditBox idBox;
    private EditBox nameBox;
    private EditBox iconBox;
    private String errorMsg = null;

    public PhantasiaSceneCreateScreen(Screen parent) {
        super(Component.translatable("screen.phantasia.scene_create.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int pw = Math.min(300, this.width - 20), ph = 130;
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;

        idBox = addRenderableWidget(new EditBox(font, px + 80, py + 16, pw - 88, 14, Component.empty()));
        idBox.setMaxLength(64);
        idBox.setHint(Component.translatable("screen.phantasia.scene_create.hint_id"));
        idBox.setResponder(v -> errorMsg = null);

        nameBox = addRenderableWidget(new EditBox(font, px + 80, py + 36, pw - 88, 14, Component.empty()));
        nameBox.setMaxLength(64);
        nameBox.setHint(Component.translatable("screen.phantasia.scene_create.hint_name"));

        iconBox = addRenderableWidget(new EditBox(font, px + 80, py + 56, pw - 88, 14, Component.empty()));
        iconBox.setMaxLength(128);
        iconBox.setValue("minecraft:chest");
        iconBox.setHint(Component.translatable("screen.phantasia.scene_create.hint_icon"));

        setInitialFocus(idBox);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, this.width, this.height, C_BG());

        int pw = Math.min(300, this.width - 20), ph = 130;
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;

        g.fill(px, py, px + pw, py + ph, C_PANEL());
        g.fill(px, py, px + pw, py + 1, C_ACCENT());

        g.drawCenteredString(font, Component.translatable("screen.phantasia.scene_create.title").getString(),
                px + pw / 2, py + 4, C_ACCENT());

        g.drawString(font, Component.translatable("screen.phantasia.scene_create.label_id").getString(), px + 6,
                py + 19, C_DIM(), false);
        g.drawString(font, Component.translatable("screen.phantasia.scene_create.label_name").getString(), px + 6,
                py + 39, C_DIM(), false);
        g.drawString(font, Component.translatable("screen.phantasia.scene_create.label_icon").getString(), px + 6,
                py + 59, C_DIM(), false);

        super.render(g, mx, my, partial);

        if (errorMsg != null)
            g.drawCenteredString(font, errorMsg, px + pw / 2, py + 76, C_WARN());

        int btnY = py + ph - 22;
        drawBtn(g, mx, my, px + pw / 2 - 118, btnY, 110, 14,
                Component.translatable("screen.phantasia.scene_create.btn_create").getString(), C_GREEN());
        drawBtn(g, mx, my, px + pw / 2 + 8, btnY, 110, 14,
                Component.translatable("screen.phantasia.scene_create.btn_cancel").getString(), C_BTN());
    }

    private void drawBtn(GuiGraphics g, int mx, int my, int x, int y, int w, int h, String label, int col) {
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

        int pw = Math.min(300, this.width - 20), ph = 130;
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;
        int btnY = py + ph - 22;

        if (mx >= px + pw / 2 - 118 && mx < px + pw / 2 - 8 && my >= btnY && my < btnY + 14) {
            tryCreate();
            return true;
        }

        if (mx >= px + pw / 2 + 8 && mx < px + pw / 2 + 118 && my >= btnY && my < btnY + 14) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (kc == GLFW.GLFW_KEY_ENTER || kc == GLFW.GLFW_KEY_KP_ENTER) {
            tryCreate();
            return true;
        }
        if (kc == GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return super.keyPressed(kc, sc, mod);
    }

    private void tryCreate() {
        String id = idBox.getValue().trim();
        String name = nameBox.getValue().trim();
        String iconItem = iconBox.getValue().trim();

        if (id.isEmpty()) {
            errorMsg = Component.translatable("screen.phantasia.scene_create.err_blank_id").getString();
            return;
        }
        if (!id.contains(":")) {
            errorMsg = Component.translatable("screen.phantasia.scene_create.err_no_namespace").getString();
            return;
        }
        if (PhantasiaScenes.has(id)) {
            errorMsg = Component.translatable("screen.phantasia.scene_create.err_duplicate").getString();
            return;
        }
        if (name.isEmpty()) name = id.replace('_', ' ').replace(':', ' ').trim();
        if (iconItem.isEmpty()) iconItem = "minecraft:chest";

        PhantasiaSceneData scene = PhantasiaSceneData.blank(id, name, iconItem);
        PhantasiaSceneLoader.save(scene);
        Minecraft.getInstance().setScreen(new PhantasiaSceneEditorScreen(parent, scene));
    }
}
