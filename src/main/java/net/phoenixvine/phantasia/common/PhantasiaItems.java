package net.phoenixvine.phantasia.common;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.common.item.PhantasiaManualItem;
import net.phoenixvine.phantasia.common.item.PhantasiaWandItem;

public final class PhantasiaItems {

    private PhantasiaItems() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Phantasia.MOD_ID);

    public static final RegistryObject<PhantasiaManualItem> PHANTASIA_MANUAL = ITEMS.register("phantasia_manual",
            () -> new PhantasiaManualItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<PhantasiaWandItem> CAPTURE_WAND = ITEMS.register("capture_wand",
            () -> new PhantasiaWandItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
