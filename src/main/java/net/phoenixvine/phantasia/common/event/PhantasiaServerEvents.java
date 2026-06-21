package net.phoenixvine.phantasia.common.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.common.PhantasiaItems;

/**
 * Server-side events. Runs on both integrated and dedicated servers.
 */
@Mod.EventBusSubscriber(modid = Phantasia.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PhantasiaServerEvents {

    private static final String NBT_KEY = "phantasia_manual_given";

    private PhantasiaServerEvents() {}

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (net.phoenixvine.phantasia.configs.PhantasiaConfigs.INSTANCE != null &&
                !net.phoenixvine.phantasia.configs.PhantasiaConfigs.INSTANCE.phantasiaUI.giveBookOnFirstJoin)
            return;

        CompoundTag data = player.getPersistentData();
        if (!data.getBoolean(NBT_KEY)) {
            data.putBoolean(NBT_KEY, true);
            ItemStack manual = new ItemStack(PhantasiaItems.PHANTASIA_MANUAL.get());
            // Try hotbar slot 8 (far right) first, then any empty slot
            if (player.getInventory().getItem(8).isEmpty()) {
                player.getInventory().setItem(8, manual);
            } else {
                player.getInventory().add(manual);
            }
        }
    }
}
