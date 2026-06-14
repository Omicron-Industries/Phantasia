package net.phoenixvine.phantasia.client.screens.subscreen;

import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.common.block.CoilBlock;
import com.gregtechceu.gtceu.common.block.LampBlock;

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
import net.phoenix.core.integration.phoenix_fission.common.data.block.FissionBlanketBlock;
import net.phoenix.core.integration.phoenix_fission.common.data.block.FissionCoolerBlock;
import net.phoenix.core.integration.phoenix_fission.common.data.block.FissionFuelRodBlock;
import net.phoenix.core.integration.phoenix_fission.common.data.block.FissionModeratorBlock;
import net.phoenix.core.integration.phoenix_tesla_network.common.block.TeslaBatteryBlock;
import net.phoenixvine.phantasia.client.screens.PhantasiaSceneScreen;
import net.phoenixvine.phantasia.common.data.pattern.PhantasiaLoadedPattern;
import net.phoenixvine.phantasia.common.data.variant.PhantasiaVariantState;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class PhantasiaBlockInspectScreen extends Screen {

    private static final int C_BG = 0xFF020205;
    private static final int C_ACCENT = 0xFF4FC3F7;
    private static final int C_TEXT = 0xFFDDDDDD;
    private static final int C_DIM = 0xFF778899;

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
        else if (block instanceof CoilBlock coilBlock) {
            ICoilType type = coilBlock.coilType;
            machineRole = Component.literal(type.getName().toUpperCase() + " HEATING COIL");

            infoLines.add(Component.empty());
            infoLines.add(Component.translatable("spec.phantasia.tech_specs").withStyle(ChatFormatting.AQUA));

            infoLines.add(Component.translatable("spec.phantasia.max_heat",
                    Component.literal(type.getCoilTemperature() + "K").withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.GRAY));

            infoLines.add(Component.translatable("spec.phantasia.material",
                    Component.literal(type.getMaterial().getName()).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));

            infoLines.add(Component.translatable("spec.phantasia.energy_discount",
                    Component.literal(type.getEnergyDiscount() + "x").withStyle(ChatFormatting.GREEN)).withStyle(ChatFormatting.GRAY));
        }
        if (net.minecraftforge.fml.loading.LoadingModList.get().getModFileById("phoenixcore") != null) {
            tryAppendPhoenixData(block, infoLines);
        }
        else if (block instanceof LampBlock) {
            machineRole = Component.translatable("role.phantasia.lamp");
        }
        else if (block instanceof MetaMachineBlock) {
            try {
                for (Field field : PartAbility.class.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) && field.getType() == PartAbility.class) {
                        PartAbility ability = (PartAbility) field.get(null);
                        if (ability != null && ability.isApplicable(block)) {
                            machineRole = Component.literal(ability.getName().toUpperCase().replace("_", " "));
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private void tryAppendPhoenixData(net.minecraft.world.level.block.Block block, List<Component> infoLines) {
        if (block instanceof FissionFuelRodBlock rodBlock) {
            var type = rodBlock.getFuelRodType();
            this.machineRole = Component.literal("FISSION FUEL ROD [T" + type.getTier() + "]");

            infoLines.add(Component.empty());
            infoLines.add(Component.translatable("spec.phantasia.core_analysis").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));

            infoLines.add(Component.translatable("spec.phantasia.base_heat",
                    Component.literal(type.getBaseHeatProduction() + " HU/t").withStyle(ChatFormatting.RED)).withStyle(ChatFormatting.GRAY));

            infoLines.add(Component.translatable("spec.phantasia.neutron_bias",
                    Component.literal((type.getNeutronBias() >= 0 ? "+" : "") + type.getNeutronBias() + "%").withStyle(ChatFormatting.LIGHT_PURPLE)).withStyle(ChatFormatting.GRAY));

            infoLines.add(Component.translatable("spec.phantasia.cycle",
                    Component.literal(type.getAmountPerCycle() + " pellets / " + (type.getDurationTicks() / 20.0) + "s").withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
        }
        else if (block instanceof FissionCoolerBlock coolerBlock) {
            var type = coolerBlock.getCoolerType();
            this.machineRole = Component.literal("THERMAL COOLANT MODULE");

            infoLines.add(Component.empty());
            infoLines.add(Component.translatable("spec.phantasia.cooling_data").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));

            infoLines.add(Component.translatable("spec.phantasia.cooling_power",
                    Component.literal("-" + type.getCoolerTemperature() + " HU/t").withStyle(ChatFormatting.BLUE)).withStyle(ChatFormatting.GRAY));

            infoLines.add(Component.translatable("spec.phantasia.consumption",
                    Component.literal(type.getCoolantUsagePerTick() + " mB/t").withStyle(ChatFormatting.LIGHT_PURPLE)).withStyle(ChatFormatting.GRAY));
        }
        else if (block instanceof FissionModeratorBlock moderatorBlock) {
            var type = moderatorBlock.getModeratorType();
            this.machineRole = Component.literal("NEUTRON MODERATOR");

            infoLines.add(Component.empty());
            infoLines.add(Component.translatable("spec.phantasia.moderation_stats").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));

            infoLines.add(Component.translatable("spec.phantasia.energy_boost",
                    Component.literal("+" + type.getEUBoost() + "%").withStyle(ChatFormatting.GREEN)).withStyle(ChatFormatting.GRAY));

            infoLines.add(Component.translatable("spec.phantasia.fuel_discount",
                    Component.literal(type.getFuelDiscount() + "%").withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GRAY));
        }
        else if (block instanceof FissionBlanketBlock blanketBlock) {
            var type = blanketBlock.getBlanketType();
            this.machineRole = Component.literal("BREEDER BLANKET");

            infoLines.add(Component.empty());
            infoLines.add(Component.translatable("spec.phantasia.transformation_data").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));

            infoLines.add(Component.translatable("spec.phantasia.target_cycle",
                    Component.literal((type.getDurationTicks() / 20.0) + "s").withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.GRAY));

            var outs = type.getOutputs();
            if (!outs.isEmpty()) {
                infoLines.add(Component.translatable("spec.phantasia.primary_byproducts").withStyle(ChatFormatting.GRAY));
                for (int i = 0; i < Math.min(outs.size(), 2); i++) {
                    var out = outs.get(i);
                    infoLines.add(Component.literal("   - ").withStyle(ChatFormatting.DARK_GRAY)
                            .append(FissionFuelRodBlock.getRegistryDisplayName(out.key()).copy().withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(" (W:" + out.weight() + ")").withStyle(ChatFormatting.DARK_AQUA)));
                }
            }
        }
        else if (block instanceof TeslaBatteryBlock) {
            this.machineRole = Component.translatable("role.phantasia.tesla");
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, C_BG);

        g.fill(20, 35, this.width - 20, 36, C_ACCENT);
        g.drawString(font, Component.translatable("screen.phantasia.block_inspector.header"), 25, 22, C_ACCENT, false);

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
        for (FormattedCharSequence seq : font.split(Component.literal(id).withStyle(ChatFormatting.ITALIC), leftColWidth)) {
            g.drawString(font, seq, leftCol, y, C_DIM, false);
            y += 10;
        }

        y += 20;
        g.drawString(font, Component.translatable("screen.phantasia.block_inspector.designation"), leftCol, y, C_ACCENT, false);
        y += 12;

        for (FormattedCharSequence seq : font.split(machineRole, leftColWidth)) {
            g.drawString(font, seq, leftCol, y, 0xFFFFB74D, false);
            y += 10;
        }

        y += 20;
        BlockPos lp = pattern.toLocal(pos);
        if (lp != null) {
            g.drawString(font, Component.translatable("screen.phantasia.block_inspector.local_pos"), leftCol, y, C_ACCENT, false);
            y += 12;
            g.drawString(font, "X: " + lp.getX(), leftCol, y, C_TEXT, false);
            g.drawString(font, "Y: " + lp.getY(), leftCol + 40, y, C_TEXT, false);
            g.drawString(font, "Z: " + lp.getZ(), leftCol + 80, y, C_TEXT, false);
        }

        y = 55;
        g.drawString(font, Component.translatable("screen.phantasia.block_inspector.specs_utility"), rightCol, y - 15, C_ACCENT, false);

        for (Component line : infoLines) {
            for (FormattedCharSequence sequence : font.split(line, maxRightWidth)) {
                g.drawString(font, sequence, rightCol, y, C_TEXT, false);
                y += 10;
            }
            y += 2;
            if (y > this.height - 80) break;
        }

        y += 10;

        if (!state.getValues().isEmpty()) {
            g.fill(rightCol, y, rightCol + 120, y + 1, 0x22FFFFFF);
            y += 10;
            g.drawString(font, Component.translatable("screen.phantasia.block_inspector.properties"), rightCol, y, C_DIM, false);
            y += 12;
            for (Map.Entry<Property<?>, Comparable<?>> entry : state.getValues().entrySet()) {
                String combined = entry.getKey().getName() + ": " + entry.getValue();
                for (FormattedCharSequence seq : font.split(Component.literal(combined), maxRightWidth)) {
                    g.drawString(font, seq, rightCol, y, C_ACCENT, false);
                    y += 10;
                }
                if (y > this.height - 60) break;
            }
        }

        int bw = 120, bh = 20;
        int bxClose = this.width - bw - 30;
        int by = this.height - 40;

        ItemStack stack = new ItemStack(state.getBlock().asItem());
        if (!stack.isEmpty()) {
            bxClose = this.width - (bw * 2) - 40;
            int bxEmi = this.width - bw - 30;

            boolean emiHov = mx >= bxEmi && mx < bxEmi + bw && my >= by && my < by + bh;
            g.fill(bxEmi, by, bxEmi + bw, by + bh, emiHov ? 0xFF2A3A5A : 0xFF151A25);
            if (emiHov) {
                g.fill(bxEmi, by, bxEmi + bw, by + 1, C_ACCENT);
                g.fill(bxEmi, by + bh - 1, bxEmi + bw, by + bh, C_ACCENT);
            }
            g.drawCenteredString(font, Component.translatable("screen.phantasia.block_inspector.btn_emi"), bxEmi + bw / 2, by + 6, emiHov ? C_ACCENT : C_TEXT);
        }

        boolean hov = mx >= bxClose && mx < bxClose + bw && my >= by && my < by + bh;
        g.fill(bxClose, by, bxClose + bw, by + bh, hov ? 0xFF2A3A5A : 0xFF151A25);
        if (hov) {
            g.fill(bxClose, by, bxClose + bw, by + 1, C_ACCENT);
            g.fill(bxClose, by + bh - 1, bxClose + bw, by + bh, C_ACCENT);
        }
        g.drawCenteredString(font, Component.translatable("screen.phantasia.block_inspector.btn_close"), bxClose + bw / 2, by + 6, hov ? C_ACCENT : C_TEXT);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int bw = 120, bh = 20;
        int bxClose = this.width - bw - 30;
        int by = this.height - 40;

        ItemStack itemStack = new ItemStack(state.getBlock().asItem());
        if (!itemStack.isEmpty()) {
            bxClose = this.width - (bw * 2) - 40;
            int bxEmi = this.width - bw - 30;

            if (mx >= bxEmi && mx < bxEmi + bw && my >= by && my < by + bh) {
                var emiStack = dev.emi.emi.api.stack.EmiStack.of(itemStack);
                var manager = dev.emi.emi.api.EmiApi.getRecipeManager();

                if (!manager.getRecipesByOutput(emiStack).isEmpty()) {
                    dev.emi.emi.api.EmiApi.displayRecipes(emiStack);
                }
                else if (!manager.getRecipesByInput(emiStack).isEmpty()) {
                    dev.emi.emi.api.EmiApi.displayUses(emiStack);
                }
                else {
                    dev.emi.emi.api.EmiApi.displayRecipes(emiStack);
                }
                return true;
            }
        }

        if (mx >= bxClose && mx < bxClose + bw && my >= by && my < by + bh) {
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
}