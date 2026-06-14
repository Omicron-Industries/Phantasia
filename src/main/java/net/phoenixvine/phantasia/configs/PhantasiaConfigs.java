package net.phoenixvine.phantasia.configs;

import net.phoenixvine.phantasia.Phantasia;

import dev.toma.configuration.Configuration;
import dev.toma.configuration.config.Config;
import dev.toma.configuration.config.ConfigHolder;
import dev.toma.configuration.config.Configurable;
import dev.toma.configuration.config.format.ConfigFormats;

@Config(id = Phantasia.MOD_ID)
public class PhantasiaConfigs {

    public static PhantasiaConfigs INSTANCE;
    public static ConfigHolder<PhantasiaConfigs> CONFIG_HOLDER;

    public static void init() {
        CONFIG_HOLDER = Configuration.registerConfig(PhantasiaConfigs.class, ConfigFormats.yaml());
        INSTANCE = CONFIG_HOLDER.getConfigInstance();
    }

    @Configurable
    public PhantasiaUIConfig phantasiaUI = new PhantasiaUIConfig();

    public static class PhantasiaUIConfig {

        @Configurable
        @Configurable.Comment({
                "TOOLTIP_ONLY: Displays the UI element purely as an item tooltip.",
                "JADE_ONLY: Integrates the display exclusively into Jade/WTHIT overlays.",
                "HOTBAR_ONLY: Shows the display only above the hotbar.",
                "TOOLTIP_JADE: Enables both the item tooltip and Jade overlay displays.",
                "TOOLTIP_HOTBAR: Enables both the item tooltip and hotbar displays."
        })
        public DisplayMode displayMode = DisplayMode.TOOLTIP_JADE;

        @Configurable
        @Configurable.Comment("Ticks required to hold the key to open the menu (20 ticks = 1 second).")
        public int activationTicks = 20;



        @Configurable
        @Configurable.Comment("The block ID used for the baseplate/floor in the scene preview.")
        public String baseplateBlock = "minecraft:deepslate_bricks";

        public enum DisplayMode {
            TOOLTIP_ONLY,
            JADE_ONLY,
            HOTBAR_ONLY,
            TOOLTIP_JADE,
            TOOLTIP_HOTBAR
        }
    }
}
