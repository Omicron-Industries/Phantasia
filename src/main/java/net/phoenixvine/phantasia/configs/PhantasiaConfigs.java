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

        @Configurable
        @Configurable.Comment({
                "Controls default camera behaviour in the scene and script viewers.",
                "true (default) - Steps/scripts drive the camera; the player is locked out.",
                "                 The player can toggle the lock button in the viewer at any time.",
                "false           - Camera is always free; steps/scripts never move it."
        })
        public boolean scriptLockCamera = true;

        @Configurable
        @Configurable.Comment("When true, scripts begin playing automatically when the viewer opens.")
        public boolean autoPlayScripts = true;

        @Configurable
        @Configurable.Comment("When true, a floor baseplate is rendered under the machine preview in scene viewers.")
        public boolean showBaseplate = true;

        @Configurable
        @Configurable.Comment({
                "Multiplier for camera orbit (drag) speed. 1.0 = default, 2.0 = twice as fast."
        })
        public float cameraSensitivity = 1.0f;

        @Configurable
        @Configurable.Comment({
                "Multiplier for scroll zoom speed. 1.0 = default, 2.0 = twice as fast."
        })
        public float scrollZoomSpeed = 1.0f;

        @Configurable
        @Configurable.Comment({
                "Controls when async streaming baking kicks in for large scenes.",
                "PERFORMANCE: streams at 500 blocks — lowest GPU spike, best for weak hardware.",
                "BALANCED: streams at 4 000 blocks — default.",
                "QUALITY: streams at 20 000 blocks — bakes more before displaying, higher GPU spike."
        })
        public StreamingMode streamingMode = StreamingMode.BALANCED;

        @Configurable
        @Configurable.Comment({
                "Maximum number of animation ticks processed per render frame.",
                "Lower values reduce frame time spikes when opening large animated scenes.",
                "Default: 2048."
        })
        public int animateTickBudget = 2048;

        @Configurable
        @Configurable.Comment("When true, the Phantasia guidebook is given to each player on their first join.")
        public boolean giveBookOnFirstJoin = true;

        public enum DisplayMode {
            TOOLTIP_ONLY,
            JADE_ONLY,
            HOTBAR_ONLY,
            TOOLTIP_JADE,
            TOOLTIP_HOTBAR
        }

        public enum StreamingMode {
            PERFORMANCE,
            BALANCED,
            QUALITY
        }
    }
}
