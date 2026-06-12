package net.phoenixvine.phantasia.common.data.variant;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phantasia.common.data.pattern.PhantasiaLoadedPattern;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScript;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;

import lombok.Getter;

import java.util.*;

/**
 * Compiled runtime representation of a single variant group.
 *
 * A variant group is a named set of positions in the dummy world where one
 * of two (or more) {@link BlockState}s can be shown. Index 0 is the primary
 * (default shown when shownByDefault=true); remaining entries are alternatives.
 *
 * <p>
 * Instances are produced by {@link #compile} and stored in
 * {@link PhantasiaScript}. The active choice for each group is held in
 * {@link PhantasiaVariantState},
 * which the renderer queries per-position in {@code resolveState()}.
 *
 * <h3>Key design fix</h3>
 * Auto-detection scans <em>all</em> available shapes for a machine, not just
 * the currently-loaded one. This is critical for machines like the Fusion
 * Reactor where shape 0 is all-casing and shape 1 has fusion glass — without
 * scanning all shapes, fusion glass would never be found.
 */
@Getter
public final class PhantasiaVariantGroup {

    private final int forcedDefaultIndex;

    public enum Category {

        OPTIONAL("Optional Blocks"),
        HATCHES_BUSES("Hatches & Buses"),
        MUFFLERS("Mufflers"),
        CASINGS("Casings");

        public final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public static Category fromString(String s) {
            if (s == null) return OPTIONAL;
            return switch (s.toLowerCase(Locale.ROOT)) {
                case "hatches_buses", "hatches", "buses" -> HATCHES_BUSES;
                case "mufflers", "muffler" -> MUFFLERS;
                case "casings", "casing" -> CASINGS;
                default -> OPTIONAL;
            };
        }
    }

    /** Stable identifier, used as key in PhantasiaVariantState. */
    private final String id;

    /** Display label shown in the Variants subscreen. */
    private final String label;

    /** Which section of the subscreen this group appears in. */
    private final Category category;

    /**
     * If true, {@code options.get(0)} (the primary) is shown by default.
     * If false, {@code options.get(options.size()-1)} (the last / fallback) is default.
     */
    private final boolean shownByDefault;

    /**
     * All valid block states for this group, in order.
     * Index 0 is always the "primary" block. For binary groups (optional/casing),
     * index 1 is the fallback. For tier groups (hatches), each tier is an entry.
     */
    private final List<BlockState> options;

    /** Display names for each option, parallel to {@link #options}. */
    private final List<String> optionLabels;

    /**
     * Maps world-space BlockPos → the index in {@link #options} that represents
     * what the dummy world currently holds at that position (i.e. the "base" state
     * as loaded). The renderer uses this to know which positions belong to this group
     * and what to substitute when the selection differs from the base index.
     */
    private final Map<BlockPos, Integer> positionBaseIndex;

    private PhantasiaVariantGroup(String id, String label, Category category,
                                  boolean shownByDefault,
                                  List<BlockState> options,
                                  List<String> optionLabels,
                                  Map<BlockPos, Integer> positionBaseIndex,
                                  int forcedDefaultIndex) { // Added parameter
        this.id = id;
        this.label = label;
        this.category = category;
        this.shownByDefault = shownByDefault;
        this.options = Collections.unmodifiableList(new ArrayList<>(options));
        this.optionLabels = Collections.unmodifiableList(new ArrayList<>(optionLabels));
        this.positionBaseIndex = Collections.unmodifiableMap(new HashMap<>(positionBaseIndex));
        this.forcedDefaultIndex = forcedDefaultIndex; // Set field
    }

    /** True if this group has more than one option (otherwise hidden in the UI). */
    public boolean hasChoice() {
        return options.size() > 1;
    }

    /** Returns the index that should be selected by default. */
    /** Returns the index that should be selected by default. */
    public int defaultIndex() {
        if (forcedDefaultIndex != -1) {
            return forcedDefaultIndex;
        }
        return shownByDefault ? 0 : options.size() - 1;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Entry point
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Compiles all variant groups for the given script + pattern.
     *
     * @param data       script data (may contain explicit optionalGroups)
     * @param definition the multiblock definition
     * @param pattern    the currently-loaded pattern (provides world-space positions)
     * @param allShapes  ALL available shapes for this machine — required so that
     *                   auto-detection can find blocks that only appear in some shapes
     *                   (e.g. fusion glass only appears in shape index 1)
     */
    public static List<PhantasiaVariantGroup> compile(
                                                      PhantasiaScriptData data,
                                                      MultiblockMachineDefinition definition,
                                                      PhantasiaLoadedPattern pattern,
                                                      List<MultiblockShapeInfo> allShapes) {
        List<PhantasiaVariantGroup> result = new ArrayList<>();
        Set<String> explicitIds = new HashSet<>();

        // 1. Script-defined (manual) groups — highest priority
        for (PhantasiaScriptData.OptionalGroupData ogd : data.getOptionalGroups()) {
            PhantasiaVariantGroup group = compileManual(ogd, pattern);
            if (group != null && group.hasChoice()) {
                result.add(group);
                explicitIds.add(ogd.getId());
            }
        }

        // 2. Auto-detected groups — scan ALL shapes so we find blocks that only
        // appear in some variants (e.g. fusion glass in shape 1 but not shape 0).
        try {
            List<PhantasiaVariantGroup> autoGroups = autoDetect(definition, pattern, allShapes, explicitIds);
            result.addAll(autoGroups);
        } catch (Exception e) {
            System.err.println("[Phantasia] WARNING: variant auto-detection failed for " + definition.getId() + ": " +
                    e.getMessage());
            e.printStackTrace();
        }

        return Collections.unmodifiableList(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Manual compilation
    // ─────────────────────────────────────────────────────────────────────────

    private static PhantasiaVariantGroup compileManual(
                                                       PhantasiaScriptData.OptionalGroupData ogd,
                                                       PhantasiaLoadedPattern pattern) {
        BlockState primary = resolveBlock(ogd.getPrimaryBlock());
        BlockState fallback = resolveBlock(ogd.getFallbackBlock());
        if (primary == null || fallback == null) return null;

        List<BlockState> options = List.of(primary, fallback);
        List<String> labels = List.of(blockDisplayName(primary), blockDisplayName(fallback));

        Map<BlockPos, Integer> posMap = new HashMap<>();
        for (PhantasiaScriptData.VariantPositionData vpd : ogd.getPositions()) {
            BlockPos local = new BlockPos(vpd.x, vpd.y, vpd.z);
            BlockPos world = pattern.localToWorld.get(local);
            if (world == null) continue;
            posMap.put(world, 0); // primary is the base in manual groups
        }
        if (posMap.isEmpty()) return null;

        return new PhantasiaVariantGroup(
                ogd.getId(), ogd.getLabel(),
                Category.fromString(ogd.getCategory()),
                ogd.isShownByDefault(),
                options, labels, posMap, -1); // -1 means no forced default
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Auto-detection
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Scans ALL shapes to union the set of blocks that appear across variants,
     * then produces groups for:
     * <ol>
     * <li>PartAbility hatch/bus/muffler tiers</li>
     * <li>Optional decorative blocks (blocks that appear in some shapes but
     * not others, e.g. fusion glass vs casing)</li>
     * </ol>
     */
    private static List<PhantasiaVariantGroup> autoDetect(
                                                          MultiblockMachineDefinition definition,
                                                          PhantasiaLoadedPattern pattern,
                                                          List<MultiblockShapeInfo> allShapes,
                                                          Set<String> excludeIds) {
        if (allShapes == null || allShapes.isEmpty()) return Collections.emptyList();

        // ── Build a unified block inventory across all shapes ─────────────────
        // blocksInAnyShape: every block that appears in at least one shape
        // blocksInAllShapes: blocks that appear in every shape (structural)
        // positionsByBlock: for the LOADED (current) shape, world pos → block
        //
        // We use the first shape's dimensions as the reference grid, since all
        // shapes for a machine share the same local coordinate space.

        // Collect block counts per position per shape (local coords)
        // localPos → Set<Block> across all shapes
        Map<BlockPos, Set<Block>> localPosToAllBlocks = new HashMap<>();
        // Block → world positions in the LOADED shape only (for positionBaseIndex)
        Map<Block, List<BlockPos>> loadedShapeBlockToWorldPos = new HashMap<>();

        // Populate loadedShapeBlockToWorldPos from the current pattern
        for (Map.Entry<BlockPos, BlockPos> e : pattern.localToWorld.entrySet()) {
            BlockPos local = e.getKey();
            BlockPos world = e.getValue();
            // Get block from SHARED_LEVEL via blockMap
            BlockInfo info = pattern.blockMap.get(world);
            if (info == null) continue;
            BlockState state = info.getBlockState();
            if (state == null || state.isAir()) continue;
            Block block = state.getBlock();
            loadedShapeBlockToWorldPos.computeIfAbsent(block, k -> new ArrayList<>()).add(world);
            localPosToAllBlocks.computeIfAbsent(local, k -> new HashSet<>()).add(block);
        }

        // Also scan all other shapes to find blocks that aren't in the loaded shape
        for (MultiblockShapeInfo shape : allShapes) {
            BlockInfo[][][] raw = shape.getBlocks();
            if (raw == null) continue;
            for (int x = 0; x < raw.length; x++) {
                if (raw[x] == null) continue;
                for (int y = 0; y < raw[x].length; y++) {
                    if (raw[x][y] == null) continue;
                    for (int z = 0; z < raw[x][y].length; z++) {
                        BlockInfo info = raw[x][y][z];
                        if (info == null) continue;
                        BlockState state = info.getBlockState();
                        if (state == null || state.isAir()) continue;
                        BlockPos local = new BlockPos(x, y, z);
                        localPosToAllBlocks.computeIfAbsent(local, k -> new HashSet<>())
                                .add(state.getBlock());
                    }
                }
            }
        }

        List<PhantasiaVariantGroup> result = new ArrayList<>();
        result.addAll(detectPartAbilityGroups(definition, loadedShapeBlockToWorldPos,
                pattern, excludeIds));
        result.addAll(detectOptionalBlocks(localPosToAllBlocks, loadedShapeBlockToWorldPos,
                pattern, excludeIds));

        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hatch / Bus / Muffler tier detection
    // ─────────────────────────────────────────────────────────────────────────

    private static List<PhantasiaVariantGroup> detectPartAbilityGroups(
                                                                       MultiblockMachineDefinition definition,
                                                                       Map<Block, List<BlockPos>> loadedBlockToWorldPos,
                                                                       PhantasiaLoadedPattern pattern,
                                                                       Set<String> excludeIds) {
        List<PhantasiaVariantGroup> result = new ArrayList<>();

        record AbilitySpec(PartAbility ability, String id, String label,
                           Category category, int minTier, int maxTier) {}

        List<AbilitySpec> specs = List.of(
                new AbilitySpec(PartAbility.INPUT_ENERGY, "energy_hatch_in", "Energy Hatch (Input)",
                        Category.HATCHES_BUSES, 0, 13),
                new AbilitySpec(PartAbility.OUTPUT_ENERGY, "energy_hatch_out", "Energy Hatch (Output)",
                        Category.HATCHES_BUSES, 0, 13),
                new AbilitySpec(PartAbility.IMPORT_FLUIDS, "fluid_hatch_in", "Fluid Hatch (Input)",
                        Category.HATCHES_BUSES, 0, 13),
                new AbilitySpec(PartAbility.EXPORT_FLUIDS, "fluid_hatch_out", "Fluid Hatch (Output)",
                        Category.HATCHES_BUSES, 0, 13),
                new AbilitySpec(PartAbility.IMPORT_ITEMS, "item_bus_in", "Item Bus (Input)", Category.HATCHES_BUSES, 0,
                        13),
                new AbilitySpec(PartAbility.EXPORT_ITEMS, "item_bus_out", "Item Bus (Output)", Category.HATCHES_BUSES,
                        0, 13),
                new AbilitySpec(PartAbility.MUFFLER, "muffler_hatch", "Muffler Hatch", Category.MUFFLERS, 0, 13));

        for (AbilitySpec spec : specs) {
            if (excludeIds.contains(spec.id())) continue;

            List<BlockState> tierStates = new ArrayList<>();
            List<String> tierLabels = new ArrayList<>();
            try {
                Block[] abilityBlocks = spec.ability()
                        .getBlockRange(spec.minTier(), spec.maxTier())
                        .toArray(Block[]::new);
                for (Block b : abilityBlocks) {
                    if (b == null) continue;
                    BlockState st = b.defaultBlockState();
                    tierStates.add(st);
                    tierLabels.add(blockDisplayName(st));
                }
            } catch (Exception ignored) {
                continue;
            }

            if (tierStates.size() < 2) continue;

            // Find which tier is loaded and which positions it occupies
            Map<BlockPos, Integer> posMap = new HashMap<>();
            int defaultIdx = 0;
            boolean found = false;
            for (int ti = 0; ti < tierStates.size(); ti++) {
                List<BlockPos> positions = loadedBlockToWorldPos.get(tierStates.get(ti).getBlock());
                if (positions == null || positions.isEmpty()) continue;
                for (BlockPos wp : positions) posMap.put(wp, ti);
                if (!found) {
                    defaultIdx = ti;
                    found = true;
                }
            }
            if (posMap.isEmpty()) continue;

            final int finalDefaultIdx = defaultIdx;
            // Replace the anonymous subclass with a standard instantiation:
            result.add(new PhantasiaVariantGroup(
                    spec.id(), spec.label(), spec.category(),
                    true,
                    tierStates, tierLabels, posMap,
                    defaultIdx));
        }

        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Optional block detection (e.g. fusion glass)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Detects positions where different shapes place different blocks.
     *
     * For each local position that has more than one distinct non-machine block
     * across all shapes, we create an optional group. The block present in the
     * LOADED shape is the "base"; the alternative(s) are the other options.
     *
     * Example: fusion glass positions have glass in shape 1, casing in shape 0.
     * The loaded shape (usually 0) has casing at those positions, so casing is
     * the base (index 0). Glass is index 1. We set {@code shownByDefault=false}
     * so the default selection is the last index (glass), meaning the UI starts
     * showing glass even though the loaded shape has casing there — the renderer
     * substitutes glass in during the bake.
     *
     * Wait — actually we want to show glass by default because that's the better
     * build. So: primary = glass (index 0), fallback = casing (index 1),
     * shownByDefault = true (glass shown). The loaded shape has casing, but
     * positionBaseIndex maps those positions to index 1 (casing), and the
     * default selection is index 0 (glass) — so the renderer will substitute
     * glass in for casing positions on first load. Correct.
     */
    private static List<PhantasiaVariantGroup> detectOptionalBlocks(
                                                                    Map<BlockPos, Set<Block>> localPosToAllBlocks,
                                                                    Map<Block, List<BlockPos>> loadedBlockToWorldPos,
                                                                    PhantasiaLoadedPattern pattern,
                                                                    Set<String> excludeIds) {
        // Group positions by their "variant signature" — the frozenset of blocks
        // that appear across shapes at that position. Positions with the same
        // signature belong to the same optional group.
        // variantKey → list of local positions
        Map<String, List<BlockPos>> signatureToLocalPositions = new LinkedHashMap<>();
        // variantKey → set of blocks
        Map<String, Set<Block>> signatureToBlocks = new LinkedHashMap<>();

        for (Map.Entry<BlockPos, Set<Block>> entry : localPosToAllBlocks.entrySet()) {
            Set<Block> blocks = entry.getValue();
            if (blocks.size() < 2) continue; // only one block type here, not optional

            // Filter out machine blocks — they're handled by PartAbility detection
            Set<Block> nonMachine = new HashSet<>();
            for (Block b : blocks) {
                if (!isMachineBlock(b) && b != Blocks.AIR) nonMachine.add(b);
            }
            if (nonMachine.size() < 2) continue;

            // Build a stable key from sorted block IDs
            List<String> ids = new ArrayList<>();
            for (Block b : nonMachine) {
                ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(b);
                ids.add(rl != null ? rl.toString() : b.getDescriptionId());
            }
            Collections.sort(ids);
            String key = String.join("|", ids);

            signatureToLocalPositions.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(entry.getKey());
            signatureToBlocks.putIfAbsent(key, nonMachine);
        }

        List<PhantasiaVariantGroup> result = new ArrayList<>();

        for (Map.Entry<String, List<BlockPos>> sigEntry : signatureToLocalPositions.entrySet()) {
            Set<Block> blocks = signatureToBlocks.get(sigEntry.getKey());
            List<BlockPos> localPositions = sigEntry.getValue();

            // Sort blocks: fewer-occurrences first (decorative/optional),
            // more-occurrences last (structural/casing fallback)
            List<Block> sortedBlocks = new ArrayList<>(blocks);
            sortedBlocks.sort(Comparator.comparingInt(b -> {
                List<BlockPos> wp = loadedBlockToWorldPos.get(b);
                return wp != null ? wp.size() : 0; // blocks not in loaded shape sort first
            }));

            // The rarest block (or absent from loaded shape) = primary (index 0)
            // The most common block (structural casing) = fallback (index 1+)
            List<BlockState> options = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (Block b : sortedBlocks) {
                options.add(b.defaultBlockState());
                labels.add(blockDisplayName(b.defaultBlockState()));
            }

            // Build group ID from the primary block's resource location
            Block primaryBlock = sortedBlocks.get(0);
            ResourceLocation primaryRl = ForgeRegistries.BLOCKS.getKey(primaryBlock);
            String groupId = "optional_" + (primaryRl != null ? primaryRl.toString().replace(":", "_") : "unknown");
            if (excludeIds.contains(groupId)) continue;

            // Build positionBaseIndex — map world pos → index of the block
            // that's CURRENTLY in the loaded shape at that position.
            // Positions where the loaded shape has the fallback (casing) get index 1.
            // Positions where the loaded shape already has the primary get index 0.
            Map<BlockPos, Integer> posMap = new HashMap<>();
            for (BlockPos local : localPositions) {
                BlockPos world = pattern.localToWorld.get(local);
                if (world == null) continue;
                // What block is loaded here?
                BlockInfo info = pattern.blockMap.get(world);
                Block loadedBlock = (info != null && !info.getBlockState().isAir()) ? info.getBlockState().getBlock() :
                        null;
                int baseIdx = 1; // default: assume fallback (casing) is loaded
                if (loadedBlock != null) {
                    for (int i = 0; i < sortedBlocks.size(); i++) {
                        if (sortedBlocks.get(i) == loadedBlock) {
                            baseIdx = i;
                            break;
                        }
                    }
                }
                posMap.put(world, baseIdx);
            }
            if (posMap.isEmpty()) continue;

            // shownByDefault=true means the primary (decorative, index 0) is the
            // default selection. Since defaultIndex()=0 and most positions have
            // baseIdx=1 (casing loaded), the renderer will substitute the primary
            // (glass) in during bake. This is what we want — show the nicer option.
            result.add(new PhantasiaVariantGroup(
                    groupId,
                    blockDisplayName(options.get(0)), // group label = primary block name
                    Category.OPTIONAL,
                    true,    // show primary (decorative) by default
                    options, labels, posMap, -1));
        }

        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    public static BlockState resolveBlock(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            ResourceLocation rl = new ResourceLocation(id);
            Block block = ForgeRegistries.BLOCKS.getValue(rl);
            if (block == null || block == Blocks.AIR) return null;
            return block.defaultBlockState();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isMachineBlock(Block b) {
        return b instanceof com.gregtechceu.gtceu.api.block.MetaMachineBlock;
    }

    public static String blockDisplayName(BlockState state) {
        if (state == null) return "Unknown";
        try {
            return state.getBlock().getName().getString();
        } catch (Exception e) {
            return state.getBlock().getDescriptionId();
        }
    }
}
