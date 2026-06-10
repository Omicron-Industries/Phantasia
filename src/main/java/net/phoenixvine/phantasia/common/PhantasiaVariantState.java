package net.phoenixvine.phantasia.common;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.*;

import javax.annotation.Nullable;

/**
 * Client-side singleton that tracks the currently selected option index for
 * each {@link PhantasiaVariantGroup}.
 *
 * <p>
 * The renderer calls {@link #resolveState(BlockPos, BlockState)} during
 * {@code scheduleBake()} to substitute a variant block state before baking.
 * If a position belongs to a group whose selection differs from the base
 * index, the resolved state is the option at the selected index.
 *
 * <p>
 * Cleared when the scene screen opens a new machine or closes.
 */
@OnlyIn(Dist.CLIENT)
public final class PhantasiaVariantState {

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static PhantasiaVariantState INSTANCE = new PhantasiaVariantState();

    public static PhantasiaVariantState get() {
        return INSTANCE;
    }

    private PhantasiaVariantState() {}

    // ── State ─────────────────────────────────────────────────────────────────

    /** groupId → currently selected option index */
    private final Map<String, Integer> selections = new LinkedHashMap<>();

    /** Compiled groups for the currently open machine */
    private List<PhantasiaVariantGroup> groups = Collections.emptyList();

    /**
     * Reverse lookup: world BlockPos → the group that owns this position.
     * Built in {@link #loadGroups} for O(1) per-position lookups in the bake.
     */
    private final Map<BlockPos, PhantasiaVariantGroup> positionToGroup = new HashMap<>();

    /** Callback fired whenever a selection changes (triggers a renderer rebake). */
    @Nullable
    private Runnable onChangeCallback = null;

    // ── API ───────────────────────────────────────────────────────────────────

    /**
     * Loads a new set of variant groups for the current machine.
     * Resets all selections to their group defaults.
     */
    public void loadGroups(List<PhantasiaVariantGroup> newGroups) {
        groups = new ArrayList<>(newGroups);
        selections.clear();
        positionToGroup.clear();

        for (PhantasiaVariantGroup group : groups) {
            // Initialise selection to the group default
            selections.put(group.getId(), group.defaultIndex());
            // Populate reverse lookup
            for (BlockPos wp : group.getPositionBaseIndex().keySet()) {
                positionToGroup.put(wp, group);
            }
        }
    }

    /**
     * Returns all loaded groups (includes single-option groups — filter with
     * {@link PhantasiaVariantGroup#hasChoice()}).
     */
    public List<PhantasiaVariantGroup> getGroups() {
        return Collections.unmodifiableList(groups);
    }

    /**
     * Returns the currently selected option index for the given group id.
     * Falls back to the group's default index if not yet set.
     */
    public int getSelection(String groupId) {
        PhantasiaVariantGroup group = groups.stream()
                .filter(g -> g.getId().equals(groupId))
                .findFirst().orElse(null);
        return selections.getOrDefault(groupId, group != null ? group.defaultIndex() : 0);
    }

    /**
     * Sets the selected option index for the given group and triggers a rebake.
     */
    public void setSelection(String groupId, int index) {
        Integer current = selections.get(groupId);
        if (current != null && current == index) return;
        selections.put(groupId, index);
        if (onChangeCallback != null) onChangeCallback.run();
    }

    /**
     * Registers a callback that fires whenever any selection changes.
     * The scene screen registers this to call {@code renderer.requestBake()}.
     */
    public void setOnChangeCallback(@Nullable Runnable callback) {
        this.onChangeCallback = callback;
    }

    /**
     * Called by the renderer during {@code scheduleBake()} for every visible position.
     * If the position belongs to a variant group and the current selection differs from
     * the base index, returns a resolved {@link BlockState} for that position.
     *
     * <p>
     * Crucially, properties that exist on BOTH the base state and the target block
     * (e.g. {@code facing}, {@code waterlogged}) are copied from the base state onto
     * the resolved state. This preserves muffler/hatch orientation and other directional
     * properties that were set when the dummy world was originally populated.
     *
     * @param worldPos  world-space position being baked
     * @param baseState the block state currently in the dummy world at this position
     * @return the resolved block state to bake (may be baseState if no substitution needed)
     */
    public BlockState resolveState(BlockPos worldPos, BlockState baseState) {
        PhantasiaVariantGroup group = positionToGroup.get(worldPos);
        if (group == null) return baseState;

        int selectedIdx = selections.getOrDefault(group.getId(), group.defaultIndex());
        List<BlockState> options = group.getOptions();
        if (selectedIdx < 0 || selectedIdx >= options.size()) return baseState;

        BlockState target = options.get(selectedIdx);
        if (target == baseState || target.getBlock() == baseState.getBlock()) return baseState;

        // Copy shared properties from the base state onto the target.
        // This preserves facing, waterlogged, powered, etc. — anything the
        // target block's state supports that the base block also had set.
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

    /** Clears all state. Called when the scene screen fully closes. */
    public void clear() {
        groups = Collections.emptyList();
        selections.clear();
        positionToGroup.clear();
        onChangeCallback = null;
    }
}
