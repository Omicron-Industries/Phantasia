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

import javax.annotation.Nullable;
import java.util.List;

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
        lines.add(Component.literal("Right-click to open Phantasia.")
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.empty());
        lines.add(Component.literal("[P] ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal("Hold near any machine to open its guide.")
                        .withStyle(ChatFormatting.GRAY)));
        lines.add(Component.literal("/phantasia ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal("Browse all content.")
                        .withStyle(ChatFormatting.GRAY)));
        lines.add(Component.empty());
        lines.add(Component.literal("Tutorials tab")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" — interactive guides with animated walkthroughs.")
                        .withStyle(ChatFormatting.GRAY)));
    }
}
