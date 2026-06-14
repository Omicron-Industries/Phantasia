package net.phoenixvine.phantasia.common.data.variant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import javax.annotation.Nullable;

/**
 * Client-side singleton that tracks the currently selected option index for
 * each {@link PhantasiaVariantGroup}.
 *
 * <p>
 * The renderer calls {@link #resolveState(BlockPos, BlockState)} during
 * {@code scheduleBake()} to substitute a variant block state before baking.
 *
 * <p>
 * Selections are persisted to disk so user choices remain active across
 * world restarts and client reboots.
 */
@OnlyIn(Dist.CLIENT)
public final class PhantasiaVariantState {

    // ── Persistence Setup ─────────────────────────────────────────────────────

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("phantasia_variants.json");

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static final PhantasiaVariantState INSTANCE = new PhantasiaVariantState();

    public static PhantasiaVariantState get() {
        return INSTANCE;
    }

    private PhantasiaVariantState() {
        loadSelections();
    }

    // ── State ─────────────────────────────────────────────────────────────────

    /** * prefixedGroupId → currently selected option index.
     * Persistent cache of the user's preferences across all machines.
     */
    private final Map<String, Integer> selections = new LinkedHashMap<>();

    /** Compiled groups for the currently open machine */
    private List<PhantasiaVariantGroup> groups = Collections.emptyList();

    /** Reverse lookup: world BlockPos → the group that owns this position. */
    private final Map<BlockPos, PhantasiaVariantGroup> positionToGroup = new HashMap<>();

    /** Callback fired whenever a selection changes (triggers a renderer rebake). */
    @Nullable
    private Runnable onChangeCallback = null;

    // ── File I/O ──────────────────────────────────────────────────────────────

    private void loadSelections() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                Type type = new TypeToken<Map<String, Integer>>(){}.getType();
                Map<String, Integer> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    this.selections.putAll(loaded);
                }
            } catch (Exception e) {
                System.err.println("[Phantasia] Failed to load variant selections: " + e.getMessage());
            }
        }
    }

    private void saveSelections() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(this.selections, writer);
            }
        } catch (Exception e) {
            System.err.println("[Phantasia] Failed to save variant selections: " + e.getMessage());
        }
    }

    // ── API ───────────────────────────────────────────────────────────────────

    /**
     * Loads a new set of variant groups for the current machine.
     * Respects previously saved selections, otherwise applies the group default.
     */
    public void loadGroups(List<PhantasiaVariantGroup> newGroups) {
        groups = new ArrayList<>(newGroups);
        positionToGroup.clear();

        for (PhantasiaVariantGroup group : groups) {
            // Only initialize to default if the user hasn't saved a preference for this group yet
            selections.putIfAbsent(group.getId(), group.defaultIndex());

            for (BlockPos wp : group.getPositionBaseIndex().keySet()) {
                positionToGroup.put(wp, group);
            }
        }
    }

    public List<PhantasiaVariantGroup> getGroups() {
        return Collections.unmodifiableList(groups);
    }

    public int getSelection(String groupId) {
        PhantasiaVariantGroup group = groups.stream()
                .filter(g -> g.getId().equals(groupId))
                .findFirst().orElse(null);
        return selections.getOrDefault(groupId, group != null ? group.defaultIndex() : 0);
    }

    /**
     * Sets the selected option index, triggers a rebake, and persists the choice to disk.
     */
    public void setSelection(String groupId, int index) {
        Integer current = selections.get(groupId);
        if (current != null && current == index) return;

        selections.put(groupId, index);
        saveSelections();

        if (onChangeCallback != null) onChangeCallback.run();
    }

    public void setOnChangeCallback(@Nullable Runnable callback) {
        this.onChangeCallback = callback;
    }

    public BlockState resolveState(BlockPos worldPos, BlockState baseState) {
        PhantasiaVariantGroup group = positionToGroup.get(worldPos);
        if (group == null) return baseState;

        int selectedIdx = selections.getOrDefault(group.getId(), group.defaultIndex());
        List<BlockState> options = group.getOptions();
        if (selectedIdx < 0 || selectedIdx >= options.size()) return baseState;

        BlockState target = options.get(selectedIdx);
        if (target == baseState || target.getBlock() == baseState.getBlock()) return baseState;

        net.minecraft.world.level.block.state.StateDefinition<net.minecraft.world.level.block.Block, BlockState> targetDef = target
                .getBlock().getStateDefinition();
        for (net.minecraft.world.level.block.state.properties.Property<?> prop : baseState.getBlock()
                .getStateDefinition().getProperties()) {
            if (targetDef.getProperty(prop.getName()) != null) {
                target = copyProperty(target, baseState, prop);
            }
        }
        return target;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState copyProperty(
            BlockState target, BlockState source,
            net.minecraft.world.level.block.state.properties.Property<T> prop) {
        try {
            net.minecraft.world.level.block.state.properties.Property<T> targetProp = (net.minecraft.world.level.block.state.properties.Property<T>) target
                    .getBlock().getStateDefinition().getProperty(prop.getName());
            if (targetProp == null) return target;
            T value = source.getValue(prop);
            if (target.getValues().containsKey(targetProp)) {
                return target.setValue(targetProp, value);
            }
        } catch (Exception ignored) {}
        return target;
    }

    /** Clears active rendering state. Called when the scene screen fully closes. */
    public void clear() {
        groups = Collections.emptyList();
        positionToGroup.clear();
        onChangeCallback = null;
    }
}