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

    private static final int C_BG = 0xFF020205; // Solid Black
    private static final int C_ACCENT = 0xFF4FC3F7; // Phantasia Cyan
    private static final int C_TEXT = 0xFFDDDDDD;
    private static final int C_DIM = 0xFF778899;

    private final BlockPos pos;
    private final PhantasiaLoadedPattern pattern;
    private final Screen parent;
    private final BlockState state;
    private final List<Component> infoLines = new ArrayList<>();
    private String machineRole = "Standard Component";

    public PhantasiaBlockInspectScreen(BlockPos pos, PhantasiaLoadedPattern pattern, Screen parent) {
        super(Component.literal("Block Inspector"));
        this.pos = pos;
        this.pattern = pattern;
        this.parent = parent;

        // Query VariantState first to get the dynamically selected variant choice
        BlockState dynamicState = null;
        if (parent instanceof PhantasiaSceneScreen scene && scene.script != null) {
            for (var group : scene.script.getVariantGroups()) {
                if (group.getPositionBaseIndex().containsKey(pos)) {
                    int activeSel = PhantasiaVariantState.get()
                            .getSelection(group.getId());
                    // FIX: Grab the BlockState directly from the flat options list using the active index
                    if (activeSel >= 0 && activeSel < group.getOptions().size()) {
                        dynamicState = group.getOptions().get(activeSel);
                    }
                    break;
                }
            }
        }

        // Fallback to standard level blocks if no dynamic variant exists
        this.state = (dynamicState != null) ? dynamicState : PhantasiaSceneScreen.SHARED_LEVEL.getBlockState(pos);

        collectData();
    }

    private void collectData() {
        ItemStack stack = new ItemStack(state.getBlock().asItem());
        if (!stack.isEmpty()) {
            // Force tooltips to always show "Shift" info automatically in the inspector
            infoLines.addAll(stack.getTooltipLines(Minecraft.getInstance().player, TooltipFlag.Default.NORMAL));
        }

        var block = state.getBlock();

        // 1. Controller Verification
        if (pattern.controllerWorldPos != null && pos.equals(pattern.controllerWorldPos)) {
            machineRole = "MULTIBLOCK CONTROLLER";
        }
        // 2. Specialized Coils (using your CoilBlock code)
        else if (block instanceof CoilBlock coilBlock) {
            ICoilType type = coilBlock.coilType;
            machineRole = type.getName().toUpperCase() + " HEATING COIL";

            // Add technical specs directly to the info lines
            infoLines.add(Component.empty());
            infoLines.add(Component.literal("TECHNICAL SPECIFICATIONS:").withStyle(ChatFormatting.AQUA));
            infoLines.add(Component.literal(" - Max Heat: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(type.getCoilTemperature() + "K").withStyle(ChatFormatting.GOLD)));
            infoLines.add(Component.literal(" - Material: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(type.getMaterial().getName()).withStyle(ChatFormatting.WHITE)));
            infoLines.add(Component.literal(" - Energy Discount: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(type.getEnergyDiscount() + "x").withStyle(ChatFormatting.GREEN)));
        }
        // 3. Fission Components & Tesla Network (Phoenix)
        if (net.minecraftforge.fml.loading.LoadingModList.get().getModFileById("phoenixcore") != null) {
            // Pass references to an isolated method so the JVM doesn't pre-verify missing Phoenix classes
            tryAppendPhoenixData(block, infoLines);
        }
        // 5. GregTech Lamps
        else if (block instanceof LampBlock) {
            machineRole = "INDICATOR LAMP";
        }
        // 6. GT Machine Parts (Buses/Hatches)
        else if (block instanceof MetaMachineBlock) {
            try {
                for (Field field : PartAbility.class.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) && field.getType() == PartAbility.class) {
                        PartAbility ability = (PartAbility) field.get(null);
                        if (ability != null && ability.isApplicable(block)) {
                            machineRole = ability.getName().toUpperCase().replace("_", " ");
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Safely processes tooltips and roles for PhoenixCore blocks.
     * This method is isolated to prevent NoClassDefFoundErrors at runtime.
     */
    private void tryAppendPhoenixData(net.minecraft.world.level.block.Block block,
                                      List<net.minecraft.network.chat.Component> infoLines) {
        // 1. FISSION FUEL RODS
        if (block instanceof FissionFuelRodBlock rodBlock) { // Make sure your package paths match exactly!
            var type = rodBlock.getFuelRodType();
            this.machineRole = "FISSION FUEL ROD [T" + type.getTier() + "]"; // Or update your class field / return
                                                                             // value

            infoLines.add(net.minecraft.network.chat.Component.empty());
            infoLines.add(net.minecraft.network.chat.Component.literal("CORE ANALYSIS:")
                    .withStyle(net.minecraft.ChatFormatting.AQUA, net.minecraft.ChatFormatting.BOLD));
            infoLines.add(net.minecraft.network.chat.Component.literal(" • Base Heat: ")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)
                    .append(net.minecraft.network.chat.Component.literal(type.getBaseHeatProduction() + " HU/t")
                            .withStyle(net.minecraft.ChatFormatting.RED)));
            infoLines.add(net.minecraft.network.chat.Component.literal(" • Neutron Bias: ")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)
                    .append(net.minecraft.network.chat.Component
                            .literal((type.getNeutronBias() >= 0 ? "+" : "") + type.getNeutronBias() + "%")
                            .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE)));
            infoLines.add(net.minecraft.network.chat.Component.literal(" • Cycle: ")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)
                    .append(net.minecraft.network.chat.Component
                            .literal(type.getAmountPerCycle() + " pellets / " + (type.getDurationTicks() / 20.0) + "s")
                            .withStyle(net.minecraft.ChatFormatting.WHITE)));
        }

        // 2. COOLERS
        else if (block instanceof FissionCoolerBlock coolerBlock) {
            var type = coolerBlock.getCoolerType();
            this.machineRole = "THERMAL COOLANT MODULE";

            infoLines.add(net.minecraft.network.chat.Component.empty());
            infoLines.add(net.minecraft.network.chat.Component.literal("COOLING DATA:")
                    .withStyle(net.minecraft.ChatFormatting.AQUA, net.minecraft.ChatFormatting.BOLD));
            infoLines.add(net.minecraft.network.chat.Component.literal(" • Cooling Power: ")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)
                    .append(net.minecraft.network.chat.Component.literal("-" + type.getCoolerTemperature() + " HU/t")
                            .withStyle(net.minecraft.ChatFormatting.BLUE)));
            infoLines.add(net.minecraft.network.chat.Component.literal(" • Consumption: ")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)
                    .append(net.minecraft.network.chat.Component.literal(type.getCoolantUsagePerTick() + " mB/t")
                            .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE)));
        }

        // 3. MODERATORS
        else if (block instanceof FissionModeratorBlock moderatorBlock) {
            var type = moderatorBlock.getModeratorType();
            this.machineRole = "NEUTRON MODERATOR";

            infoLines.add(net.minecraft.network.chat.Component.empty());
            infoLines.add(net.minecraft.network.chat.Component.literal("MODERATION STATS:")
                    .withStyle(net.minecraft.ChatFormatting.AQUA, net.minecraft.ChatFormatting.BOLD));
            infoLines.add(net.minecraft.network.chat.Component.literal(" • Energy Boost: ")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)
                    .append(net.minecraft.network.chat.Component.literal("+" + type.getEUBoost() + "%")
                            .withStyle(net.minecraft.ChatFormatting.GREEN)));
            infoLines.add(net.minecraft.network.chat.Component.literal(" • Fuel Discount: ")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)
                    .append(net.minecraft.network.chat.Component.literal(type.getFuelDiscount() + "%")
                            .withStyle(net.minecraft.ChatFormatting.YELLOW)));
        }

        // 4. BREEDER BLANKETS
        else if (block instanceof FissionBlanketBlock blanketBlock) {
            var type = blanketBlock.getBlanketType();
            this.machineRole = "BREEDER BLANKET";

            infoLines.add(net.minecraft.network.chat.Component.empty());
            infoLines.add(net.minecraft.network.chat.Component.literal("TRANSFORMATION DATA:")
                    .withStyle(net.minecraft.ChatFormatting.AQUA, net.minecraft.ChatFormatting.BOLD));
            infoLines.add(net.minecraft.network.chat.Component.literal(" • Target Cycle: ")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)
                    .append(net.minecraft.network.chat.Component.literal((type.getDurationTicks() / 20.0) + "s")
                            .withStyle(net.minecraft.ChatFormatting.GOLD)));

            // List Top 2 Potential Outputs
            var outs = type.getOutputs();
            if (!outs.isEmpty()) {
                infoLines.add(net.minecraft.network.chat.Component.literal(" • Primary Byproducts:")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
                for (int i = 0; i < Math.min(outs.size(), 2); i++) {
                    var out = outs.get(i);
                    infoLines.add(net.minecraft.network.chat.Component.literal("   - ")
                            .withStyle(net.minecraft.ChatFormatting.DARK_GRAY)
                            .append(FissionFuelRodBlock.getRegistryDisplayName(out.key()).copy()
                                    .withStyle(net.minecraft.ChatFormatting.WHITE))
                            .append(net.minecraft.network.chat.Component.literal(" (W:" + out.weight() + ")")
                                    .withStyle(net.minecraft.ChatFormatting.DARK_AQUA)));
                }
            }
        }
        // 5. TESLA ENERGY STORAGE
        else if (block instanceof TeslaBatteryBlock) {
            this.machineRole = "TESLA ENERGY STORAGE";
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // Full black background
        g.fill(0, 0, this.width, this.height, C_BG);

        // Header Decoration
        g.fill(20, 35, this.width - 20, 36, C_ACCENT);
        g.drawString(font, "TECHNICAL BLOCK INSPECTOR", 25, 22, C_ACCENT, false);

        int leftCol = 30;
        int leftColWidth = 120;
        int rightCol = 170;
        int maxRightWidth = this.width - rightCol - 40;
        int y = 55;

        // --- LEFT COLUMN: IDENTITY ---

        // Block Icon
        g.pose().pushPose();
        g.pose().translate(leftCol, y, 100);
        g.pose().scale(4.0f, 4.0f, 1.0f);
        g.renderFakeItem(new ItemStack(state.getBlock()), 0, 0);
        g.pose().popPose();

        y += 75;

        // Wrapped Block Name
        String name = state.getBlock().getName().getString();
        for (FormattedCharSequence seq : font.split(Component.literal(name), leftColWidth)) {
            g.drawString(font, seq, leftCol, y, 0xFFFFFFFF, false);
            y += 10;
        }

        y += 4;

        // Wrapped Registry ID (Fixes bleeding)
        String id = ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();
        for (FormattedCharSequence seq : font.split(Component.literal(id).withStyle(ChatFormatting.ITALIC),
                leftColWidth)) {
            g.drawString(font, seq, leftCol, y, C_DIM, false);
            y += 10;
        }

        y += 20;
        g.drawString(font, "DESIGNATION", leftCol, y, C_ACCENT, false);
        y += 12;
        // Wrapped Role
        for (FormattedCharSequence seq : font.split(Component.literal(machineRole), leftColWidth)) {
            g.drawString(font, seq, leftCol, y, 0xFFFFB74D, false);
            y += 10;
        }

        y += 20;
        BlockPos lp = pattern.toLocal(pos);
        if (lp != null) {
            g.drawString(font, "LOCAL POS", leftCol, y, C_ACCENT, false);
            y += 12;
            g.drawString(font, "X: " + lp.getX(), leftCol, y, C_TEXT, false);
            g.drawString(font, "Y: " + lp.getY(), leftCol + 40, y, C_TEXT, false);
            g.drawString(font, "Z: " + lp.getZ(), leftCol + 80, y, C_TEXT, false);
        }

        // --- RIGHT COLUMN: DATA & TOOLTIPS ---
        y = 55;

        // Header for tooltips
        g.drawString(font, "SPECIFICATIONS & UTILITY", rightCol, y - 15, C_ACCENT, false);

        for (Component line : infoLines) {
            for (FormattedCharSequence sequence : font.split(line, maxRightWidth)) {
                g.drawString(font, sequence, rightCol, y, C_TEXT, false);
                y += 10;
            }
            y += 2;
            if (y > this.height - 80) break;
        }

        y += 10;

        // BlockState Data
        if (!state.getValues().isEmpty()) {
            g.fill(rightCol, y, rightCol + 120, y + 1, 0x22FFFFFF);
            y += 10;
            g.drawString(font, "BLOCKSTATE PROPERTIES", rightCol, y, C_DIM, false);
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

        // --- FOOTER BUTTONS (EMI Capability Added) ---
        int bw = 120, bh = 20;
        int bxClose = this.width - bw - 30;
        int by = this.height - 40;

        ItemStack itemStack = new ItemStack(state.getBlock().asItem()); // cite: 2
        if (!itemStack.isEmpty()) {
            // Shift Close button slightly left to make room for EMI button on the right
            bxClose = this.width - (bw * 2) - 40;
            int bxEmi = this.width - bw - 30;

            boolean emiHov = mx >= bxEmi && mx < bxEmi + bw && my >= by && my < by + bh;
            g.fill(bxEmi, by, bxEmi + bw, by + bh, emiHov ? 0xFF2A3A5A : 0xFF151A25);
            if (emiHov) {
                g.fill(bxEmi, by, bxEmi + bw, by + 1, C_ACCENT); // cite: 2
                g.fill(bxEmi, by + bh - 1, bxEmi + bw, by + bh, C_ACCENT); // cite: 2
            }
            g.drawCenteredString(font, "EMI RECIPES", bxEmi + bw / 2, by + 6, emiHov ? C_ACCENT : C_TEXT);
        }

        // --- CLOSE DATA BUTTON ---
        boolean hov = mx >= bxClose && mx < bxClose + bw && my >= by && my < by + bh; // cite: 2
        g.fill(bxClose, by, bxClose + bw, by + bh, hov ? 0xFF2A3A5A : 0xFF151A25); // cite: 2
        if (hov) {
            g.fill(bxClose, by, bxClose + bw, by + 1, C_ACCENT); // cite: 2
            g.fill(bxClose, by + bh - 1, bxClose + bw, by + bh, C_ACCENT); // cite: 2
        }
        g.drawCenteredString(font, "CLOSE DATA", bxClose + bw / 2, by + 6, hov ? C_ACCENT : C_TEXT); // cite: 2
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int bw = 120, bh = 20; // cite: 2
        int bxClose = this.width - bw - 30; // cite: 2
        int by = this.height - 40; // cite: 2

        ItemStack itemStack = new ItemStack(state.getBlock().asItem()); // cite: 2
        if (!itemStack.isEmpty()) {
            bxClose = this.width - (bw * 2) - 40;
            int bxEmi = this.width - bw - 30;

            // Check EMI Recipes Button Click
            if (mx >= bxEmi && mx < bxEmi + bw && my >= by && my < by + bh) {
                var emiStack = dev.emi.emi.api.stack.EmiStack.of(itemStack);
                var manager = dev.emi.emi.api.EmiApi.getRecipeManager();

                // 1. Check if there are any recipes where this block is the OUTPUT
                if (!manager.getRecipesByOutput(emiStack).isEmpty()) {
                    dev.emi.emi.api.EmiApi.displayRecipes(emiStack); // cite: 1.1
                }
                // 2. Fallback: Check if it has recipes where it is an INGREDIENT (like Stonecutting base blocks)
                else if (!manager.getRecipesByInput(emiStack).isEmpty()) {
                    dev.emi.emi.api.EmiApi.displayUses(emiStack); // cite: 1.1
                }
                // 3. Last resort: If EMI has absolutely nothing indexed, just default to the recipe view safely
                else {
                    dev.emi.emi.api.EmiApi.displayRecipes(emiStack); // cite: 1.1
                }
                return true;
            }
        }

        // Check Close Data Button Click
        if (mx >= bxClose && mx < bxClose + bw && my >= by && my < by + bh) { // cite: 2
            onClose(); // cite: 2
            return true; // cite: 2
        } // cite: 2
        return super.mouseClicked(mx, my, btn); // cite: 2
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
