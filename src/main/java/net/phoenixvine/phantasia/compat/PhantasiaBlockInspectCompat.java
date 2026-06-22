package net.phoenixvine.phantasia.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Compat-mod inspection hook for PhantasiaBlockInspectScreen.
 * Each optional dependency registers an inspector here when its mod is present.
 * PhantasiaBlockInspectScreen calls {@link #apply} without ever importing
 * any optional-mod classes, so it is safe to load when those mods are absent.
 */
public final class PhantasiaBlockInspectCompat {

    @FunctionalInterface
    public interface BlockInspector {

        void inspect(Block block, List<Component> infoLines, Consumer<Component> setRole);
    }

    private static final List<BlockInspector> inspectors = new ArrayList<>();

    private PhantasiaBlockInspectCompat() {}

    public static void register(BlockInspector inspector) {
        inspectors.add(inspector);
    }

    public static void apply(Block block, List<Component> infoLines, Consumer<Component> setRole) {
        for (BlockInspector inspector : inspectors) {
            inspector.inspect(block, infoLines, setRole);
        }
    }
}
