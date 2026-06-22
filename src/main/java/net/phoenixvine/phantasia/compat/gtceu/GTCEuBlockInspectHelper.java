package net.phoenixvine.phantasia.compat.gtceu;

import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.common.block.CoilBlock;
import com.gregtechceu.gtceu.common.block.LampBlock;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.phoenixvine.phantasia.compat.PhantasiaBlockInspectCompat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class GTCEuBlockInspectHelper {

    private GTCEuBlockInspectHelper() {}

    public static void register() {
        PhantasiaBlockInspectCompat.register((block, infoLines, setRole) -> {
            if (block instanceof LampBlock) {
                setRole.accept(Component.translatable("role.phantasia.lamp"));
            } else if (block instanceof CoilBlock coilBlock) {
                ICoilType type = coilBlock.coilType;
                setRole.accept(Component.translatable("role.phantasia.heating_coil",
                        type.getName().toUpperCase()));

                infoLines.add(Component.empty());
                infoLines.add(Component.translatable("spec.phantasia.tech_specs")
                        .withStyle(ChatFormatting.AQUA));
                infoLines.add(Component.translatable("spec.phantasia.max_heat",
                        Component.literal(type.getCoilTemperature() + "K")
                                .withStyle(ChatFormatting.GOLD))
                        .withStyle(ChatFormatting.GRAY));
                infoLines.add(Component.translatable("spec.phantasia.material",
                        Component.literal(type.getMaterial().getName())
                                .withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.GRAY));
                infoLines.add(Component.translatable("spec.phantasia.energy_discount",
                        Component.literal(type.getEnergyDiscount() + "x")
                                .withStyle(ChatFormatting.GREEN))
                        .withStyle(ChatFormatting.GRAY));
            } else if (block instanceof MetaMachineBlock) {
                try {
                    for (Field field : PartAbility.class.getDeclaredFields()) {
                        if (Modifier.isStatic(field.getModifiers()) && field.getType() == PartAbility.class) {
                            PartAbility ability = (PartAbility) field.get(null);
                            if (ability != null && ability.isApplicable(block)) {
                                setRole.accept(Component.literal(
                                        ability.getName().toUpperCase().replace("_", " ")));
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        });
    }
}
