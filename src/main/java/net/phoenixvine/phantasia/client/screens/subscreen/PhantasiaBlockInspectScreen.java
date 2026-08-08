package net.phoenixvine.phantasia.client.screens.subscreen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phantasia.client.screens.PhantasiaSceneScreen;
import net.phoenixvine.phantasia.common.data.pattern.PhantasiaLoadedPattern;
import net.phoenixvine.phantasia.common.data.variant.PhantasiaVariantState;
import net.phoenixvine.phantasia.compat.PhantasiaBlockInspectCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

@OnlyIn(Dist.CLIENT)
public class PhantasiaBlockInspectScreen extends Screen {

    private final BlockPos pos;
    private final PhantasiaLoadedPattern pattern;
    private final Screen parent;
    private final BlockState state;
    private final List<Component> infoLines = new ArrayList<>();
    private Component machineRole = Component.translatable("role.phantasia.standard");

    public PhantasiaBlockInspectScreen(BlockPos pos, PhantasiaLoadedPattern pattern, Screen parent) {
        super(Component.translatable("screen.phantasia.block_inspector.title"));
        this.pos = pos;
        this.pattern = pattern;
        this.parent = parent;

        BlockState dynamicState = null;
        if (parent instanceof PhantasiaSceneScreen scene && scene.script != null) {
            for (var group : scene.script.getVariantGroups()) {
                if (group.getPositionBaseIndex().containsKey(pos)) {
                    int activeSel = PhantasiaVariantState.get().getSelection(group.getId());
                    if (activeSel >= 0 && activeSel < group.getOptions().size()) {
                        dynamicState = group.getOptions().get(activeSel);
                    }
                    break;
                }
            }
        }

        this.state = (dynamicState != null) ? dynamicState : PhantasiaSceneScreen.SHARED_LEVEL.getBlockState(pos);
        collectData();
    }

    private void collectData() {
        ItemStack stack = new ItemStack(state.getBlock().asItem());
        if (!stack.isEmpty()) {
            infoLines.addAll(stack.getTooltipLines(Minecraft.getInstance().player, TooltipFlag.Default.NORMAL));
        }

        var block = state.getBlock();

        if (pattern.controllerWorldPos != null && pos.equals(pattern.controllerWorldPos)) {
            machineRole = Component.translatable("role.phantasia.controller");
        }
        PhantasiaBlockInspectCompat.apply(block, infoLines, role -> this.machineRole = role);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, C_BG());

        g.fill(20, 35, this.width - 20, 36, C_ACCENT());
        g.drawString(font, Component.translatable("screen.phantasia.block_inspector.header"), 25, 22, C_ACCENT(),
                false);

        int leftCol = 30;
        int leftColWidth = 120;
        int rightCol = 170;
        int maxRightWidth = this.width - rightCol - 40;
        int y = 55;

        g.pose().pushPose();
        g.pose().translate(leftCol, y, 100);
        g.pose().scale(4.0f, 4.0f, 1.0f);
        g.renderFakeItem(new ItemStack(state.getBlock()), 0, 0);
        g.pose().popPose();

        y += 75;

        String name = state.getBlock().getName().getString();
        for (FormattedCharSequence seq : font.split(Component.literal(name), leftColWidth)) {
            g.drawString(font, seq, leftCol, y, 0xFFFFFFFF, false);
            y += 10;
        }

        y += 4;

        String id = ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();
        for (FormattedCharSequence seq : font.split(Component.literal(id).withStyle(ChatFormatting.ITALIC),
                leftColWidth)) {
            g.drawString(font, seq, leftCol, y, C_DIM(), false);
            y += 10;
        }

        y += 20;
        g.drawString(font, Component.translatable("screen.phantasia.block_inspector.designation"), leftCol, y,
                C_ACCENT(), false);
        y += 12;

        for (FormattedCharSequence seq : font.split(machineRole, leftColWidth)) {
            g.drawString(font, seq, leftCol, y, 0xFFFFB74D, false);
            y += 10;
        }

        y += 20;
        BlockPos lp = pattern.toLocal(pos);
        if (lp != null) {
            g.drawString(font, Component.translatable("screen.phantasia.block_inspector.local_pos"), leftCol, y,
                    C_ACCENT(), false);
            y += 12;
            g.drawString(font, "X: " + lp.getX(), leftCol, y, C_TEXT(), false);
            g.drawString(font, "Y: " + lp.getY(), leftCol + 40, y, C_TEXT(), false);
            g.drawString(font, "Z: " + lp.getZ(), leftCol + 80, y, C_TEXT(), false);
        }

        y = 55;
        g.drawString(font, Component.translatable("screen.phantasia.block_inspector.specs_utility"), rightCol, y - 15,
                C_ACCENT(), false);

        for (Component line : infoLines) {
            for (FormattedCharSequence sequence : font.split(line, maxRightWidth)) {
                g.drawString(font, sequence, rightCol, y, C_TEXT(), false);
                y += 10;
            }
            y += 2;
            if (y > this.height - 80) break;
        }

        y += 10;

        if (!state.getValues().isEmpty()) {
            g.fill(rightCol, y, rightCol + 120, y + 1, 0x22FFFFFF);
            y += 10;
            g.drawString(font, Component.translatable("screen.phantasia.block_inspector.properties"), rightCol, y,
                    C_DIM(), false);
            y += 12;
            for (Map.Entry<Property<?>, Comparable<?>> entry : state.getValues().entrySet()) {
                String combined = entry.getKey().getName() + ": " + entry.getValue();
                for (FormattedCharSequence seq : font.split(Component.literal(combined), maxRightWidth)) {
                    g.drawString(font, seq, rightCol, y, C_ACCENT(), false);
                    y += 10;
                }
                if (y > this.height - 60) break;
            }
        }

        int[] bl = btnLayout();
        int bxClose = bl[0], bxEmi = bl[1], by = bl[2];

        ItemStack stack = new ItemStack(state.getBlock().asItem());
        if (!stack.isEmpty()) {
            boolean emiHov = isOver(mx, my, bxEmi, by, BTN_W, BTN_H);
            drawThemedBtn(g, font, bxEmi, by, BTN_W, BTN_H,
                    Component.translatable("screen.phantasia.block_inspector.btn_emi").getString(), emiHov, C_BTN());
        }

        boolean hov = isOver(mx, my, bxClose, by, BTN_W, BTN_H);
        drawThemedBtn(g, font, bxClose, by, BTN_W, BTN_H,
                Component.translatable("screen.phantasia.block_inspector.btn_close").getString(), hov, C_BTN());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int[] bl = btnLayout();
        int bxClose = bl[0], bxEmi = bl[1], by = bl[2];

        ItemStack itemStack = new ItemStack(state.getBlock().asItem());
        if (!itemStack.isEmpty()) {
            if (isOver((int) mx, (int) my, bxEmi, by, BTN_W, BTN_H)) {
                var emiStack = dev.emi.emi.api.stack.EmiStack.of(itemStack);
                var manager = dev.emi.emi.api.EmiApi.getRecipeManager();

                if (!manager.getRecipesByOutput(emiStack).isEmpty()) {
                    dev.emi.emi.api.EmiApi.displayRecipes(emiStack);
                } else if (!manager.getRecipesByInput(emiStack).isEmpty()) {
                    dev.emi.emi.api.EmiApi.displayUses(emiStack);
                } else {
                    dev.emi.emi.api.EmiApi.displayRecipes(emiStack);
                }
                return true;
            }
        }

        if (isOver((int) mx, (int) my, bxClose, by, BTN_W, BTN_H)) {
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final int BTN_W = 120;
    private static final int BTN_H = 20;

    private int[] btnLayout() {
        int bxEmi = this.width - BTN_W - 30;
        int bxClose = new ItemStack(state.getBlock().asItem()).isEmpty() ? this.width - BTN_W - 30 :
                this.width - BTN_W * 2 - 40;
        int by = this.height - 40;
        return new int[] { bxClose, bxEmi, by };
    }

    private boolean isOver(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
