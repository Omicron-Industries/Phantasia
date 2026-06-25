package net.phoenixvine.phantasia.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.client.screens.PhantasiaSceneSelectionScreen;

import java.util.List;

import javax.annotation.Nullable;

public class PhantasiaManualItem extends Item {

    public PhantasiaManualItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            openScreen();
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @OnlyIn(Dist.CLIENT)
    private static void openScreen() {
        Minecraft.getInstance().setScreen(new PhantasiaSceneSelectionScreen(null));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.phantasia.phantasia_manual.tooltip_rightclick")
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.empty());
        lines.add(Component.literal(net.phoenixvine.phantasia.client.keybind.PhoenixKeybinds.keyDisplay() + " ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.translatable("item.phantasia.phantasia_manual.tooltip_hold")
                        .withStyle(ChatFormatting.GRAY)));
        lines.add(Component.literal("/phantasia ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.translatable("item.phantasia.phantasia_manual.tooltip_browse")
                        .withStyle(ChatFormatting.GRAY)));
        lines.add(Component.empty());
        lines.add(Component.translatable("item.phantasia.phantasia_manual.tooltip_tutorials")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.translatable("item.phantasia.phantasia_manual.tooltip_tutorials_desc")
                        .withStyle(ChatFormatting.GRAY)));
        lines.add(Component.translatable("item.phantasia.phantasia_manual.tooltip_settings")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.translatable("item.phantasia.phantasia_manual.tooltip_settings_desc")
                        .withStyle(ChatFormatting.GRAY)));
    }
}
