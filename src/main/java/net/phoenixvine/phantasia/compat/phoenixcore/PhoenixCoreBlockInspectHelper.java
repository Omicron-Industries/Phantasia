package net.phoenixvine.phantasia.compat.phoenixcore;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.phoenix.core.integration.phoenix_fission.common.data.block.FissionBlanketBlock;
import net.phoenix.core.integration.phoenix_fission.common.data.block.FissionCoolerBlock;
import net.phoenix.core.integration.phoenix_fission.common.data.block.FissionFuelRodBlock;
import net.phoenix.core.integration.phoenix_fission.common.data.block.FissionModeratorBlock;
import net.phoenixvine.phantasia.compat.PhantasiaBlockInspectCompat;

public final class PhoenixCoreBlockInspectHelper {

    private PhoenixCoreBlockInspectHelper() {}

    public static void register() {
        PhantasiaBlockInspectCompat.register((block, infoLines, setRole) -> {
            if (block instanceof FissionFuelRodBlock rodBlock) {
                var type = rodBlock.getFuelRodType();
                setRole.accept(Component.translatable("role.phantasia.fission_fuel_rod", type.getTier()));

                infoLines.add(Component.empty());
                infoLines.add(Component.translatable("spec.phantasia.core_analysis")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
                infoLines.add(Component.translatable("spec.phantasia.base_heat",
                        Component.translatable("spec.phantasia.unit.hu_per_tick",
                                type.getBaseHeatProduction())
                                .withStyle(ChatFormatting.RED))
                        .withStyle(ChatFormatting.GRAY));

                String biasSign = type.getNeutronBias() >= 0 ? "+" : "";
                infoLines.add(Component.translatable("spec.phantasia.neutron_bias",
                        Component.translatable("spec.phantasia.unit.percent",
                                biasSign + type.getNeutronBias())
                                .withStyle(ChatFormatting.LIGHT_PURPLE))
                        .withStyle(ChatFormatting.GRAY));
                infoLines.add(Component.translatable("spec.phantasia.cycle",
                        Component.translatable("spec.phantasia.unit.pellets_cycle",
                                type.getAmountPerCycle(), type.getDurationTicks() / 20.0)
                                .withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.GRAY));

            } else if (block instanceof FissionCoolerBlock coolerBlock) {
                var type = coolerBlock.getCoolerType();
                setRole.accept(Component.translatable("role.phantasia.thermal_coolant"));

                infoLines.add(Component.empty());
                infoLines.add(Component.translatable("spec.phantasia.cooling_data")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
                infoLines.add(Component.translatable("spec.phantasia.cooling_power",
                        Component.translatable("spec.phantasia.unit.hu_per_tick_negative",
                                type.getCoolerTemperature())
                                .withStyle(ChatFormatting.BLUE))
                        .withStyle(ChatFormatting.GRAY));
                infoLines.add(Component.translatable("spec.phantasia.consumption",
                        Component.translatable("spec.phantasia.unit.mb_per_tick",
                                type.getCoolantUsagePerTick())
                                .withStyle(ChatFormatting.LIGHT_PURPLE))
                        .withStyle(ChatFormatting.GRAY));

            } else if (block instanceof FissionModeratorBlock moderatorBlock) {
                var type = moderatorBlock.getModeratorType();
                setRole.accept(Component.translatable("role.phantasia.neutron_moderator"));

                infoLines.add(Component.empty());
                infoLines.add(Component.translatable("spec.phantasia.moderation_stats")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
                infoLines.add(Component.translatable("spec.phantasia.energy_boost",
                        Component.translatable("spec.phantasia.unit.percent_positive",
                                type.getEUBoost())
                                .withStyle(ChatFormatting.GREEN))
                        .withStyle(ChatFormatting.GRAY));
                infoLines.add(Component.translatable("spec.phantasia.fuel_discount",
                        Component.translatable("spec.phantasia.unit.percent",
                                type.getFuelDiscount())
                                .withStyle(ChatFormatting.YELLOW))
                        .withStyle(ChatFormatting.GRAY));

            } else if (block instanceof FissionBlanketBlock blanketBlock) {
                var type = blanketBlock.getBlanketType();
                setRole.accept(Component.translatable("role.phantasia.breeder_blanket"));

                infoLines.add(Component.empty());
                infoLines.add(Component.translatable("spec.phantasia.transformation_data")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
                infoLines.add(Component.translatable("spec.phantasia.target_cycle",
                        Component.translatable("spec.phantasia.unit.seconds",
                                type.getDurationTicks() / 20.0)
                                .withStyle(ChatFormatting.GOLD))
                        .withStyle(ChatFormatting.GRAY));

                var outs = type.getOutputs();
                if (!outs.isEmpty()) {
                    infoLines.add(Component.translatable("spec.phantasia.primary_byproducts")
                            .withStyle(ChatFormatting.GRAY));
                    for (int i = 0; i < Math.min(outs.size(), 2); i++) {
                        var out = outs.get(i);
                        infoLines.add(
                                Component.translatable("ui.phantasia.bullet_prefix")
                                        .withStyle(ChatFormatting.DARK_GRAY)
                                        .append(FissionFuelRodBlock.getRegistryDisplayName(out.key())
                                                .copy().withStyle(ChatFormatting.WHITE))
                                        .append(Component.translatable(
                                                "spec.phantasia.unit.weight_suffix", out.weight())
                                                .withStyle(ChatFormatting.DARK_AQUA)));
                    }
                }
            }
        });
    }
}
