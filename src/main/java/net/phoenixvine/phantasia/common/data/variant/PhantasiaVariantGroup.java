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
                                  int forcedDefaultIndex) {
        this.id = id;
        this.label = label;
        this.category = category;
        this.shownByDefault = shownByDefault;
        this.options = Collections.unmodifiableList(new ArrayList<>(options));
        this.optionLabels = Collections.unmodifiableList(new ArrayList<>(optionLabels));
        this.positionBaseIndex = Collections.unmodifiableMap(new HashMap<>(positionBaseIndex));
        this.forcedDefaultIndex = forcedDefaultIndex;
    }

    /** True if this group has more than one option (otherwise hidden in the UI). */
    public boolean hasChoice() {
        return options.size() > 1;
    }

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
     */
    public static List<PhantasiaVariantGroup> compile(
            PhantasiaScriptData data,
            MultiblockMachineDefinition definition,
            PhantasiaLoadedPattern pattern,
            List<MultiblockShapeInfo> allShapes) {
        List<PhantasiaVariantGroup> result = new ArrayList<>();
        Set<String> explicitIds = new HashSet<>();

        // Create a unique prefix based on the multiblock's ID (e.g., "gtceu:fusion_reactor")
        String machinePrefix = definition.getId().toString();

        // 1. Script-defined (manual) groups — highest priority
        for (PhantasiaScriptData.OptionalGroupData ogd : data.getOptionalGroups()) {
            PhantasiaVariantGroup group = compileManual(machinePrefix, ogd, pattern);
            if (group != null && group.hasChoice()) {
                result.add(group);
                explicitIds.add(ogd.getId());
            }
        }

        // 2. Auto-detected groups
        try {
            List<PhantasiaVariantGroup> autoGroups = autoDetect(machinePrefix, definition, pattern, allShapes, explicitIds);
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
            String machinePrefix,
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
            posMap.put(world, 0);
        }
        if (posMap.isEmpty()) return null;

        return new PhantasiaVariantGroup(
                machinePrefix + "::" + ogd.getId(), ogd.getLabel(),
                Category.fromString(ogd.getCategory()),
                ogd.isShownByDefault(),
                options, labels, posMap, -1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Auto-detection
    // ─────────────────────────────────────────────────────────────────────────

    private static List<PhantasiaVariantGroup> autoDetect(
            String machinePrefix,
            MultiblockMachineDefinition definition,
            PhantasiaLoadedPattern pattern,
            List<MultiblockShapeInfo> allShapes,
            Set<String> excludeIds) {
        if (allShapes == null || allShapes.isEmpty()) return Collections.emptyList();

        Map<BlockPos, Set<Block>> localPosToAllBlocks = new HashMap<>();
        Map<Block, List<BlockPos>> loadedShapeBlockToWorldPos = new HashMap<>();

        for (Map.Entry<BlockPos, BlockPos> e : pattern.localToWorld.entrySet()) {
            BlockPos local = e.getKey();
            BlockPos world = e.getValue();
            BlockInfo info = pattern.blockMap.get(world);
            if (info == null) continue;
            BlockState state = info.getBlockState();
            if (state == null || state.isAir()) continue;
            Block block = state.getBlock();
            loadedShapeBlockToWorldPos.computeIfAbsent(block, k -> new ArrayList<>()).add(world);
            localPosToAllBlocks.computeIfAbsent(local, k -> new HashSet<>()).add(block);
        }

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
        result.addAll(detectPartAbilityGroups(machinePrefix, definition, loadedShapeBlockToWorldPos, pattern, excludeIds));
        result.addAll(detectOptionalBlocks(machinePrefix, localPosToAllBlocks, loadedShapeBlockToWorldPos, pattern, excludeIds));

        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hatch / Bus / Muffler tier detection
    // ─────────────────────────────────────────────────────────────────────────

    private static List<PhantasiaVariantGroup> detectPartAbilityGroups(
            String machinePrefix,
            MultiblockMachineDefinition definition,
            Map<Block, List<BlockPos>> loadedBlockToWorldPos,
            PhantasiaLoadedPattern pattern,
            Set<String> excludeIds) {
        List<PhantasiaVariantGroup> result = new ArrayList<>();

        record AbilitySpec(PartAbility ability, String id, String label,
                           Category category, int minTier, int maxTier) {}

        List<AbilitySpec> specs = List.of(
                new AbilitySpec(PartAbility.INPUT_ENERGY, "energy_hatch_in", "Energy Hatch (Input)", Category.HATCHES_BUSES, 0, 13),
                new AbilitySpec(PartAbility.OUTPUT_ENERGY, "energy_hatch_out", "Energy Hatch (Output)", Category.HATCHES_BUSES, 0, 13),
                new AbilitySpec(PartAbility.IMPORT_FLUIDS, "fluid_hatch_in", "Fluid Hatch (Input)", Category.HATCHES_BUSES, 0, 13),
                new AbilitySpec(PartAbility.EXPORT_FLUIDS, "fluid_hatch_out", "Fluid Hatch (Output)", Category.HATCHES_BUSES, 0, 13),
                new AbilitySpec(PartAbility.IMPORT_ITEMS, "item_bus_in", "Item Bus (Input)", Category.HATCHES_BUSES, 0, 13),
                new AbilitySpec(PartAbility.EXPORT_ITEMS, "item_bus_out", "Item Bus (Output)", Category.HATCHES_BUSES, 0, 13),
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

            result.add(new PhantasiaVariantGroup(
                    machinePrefix + "::" + spec.id(), spec.label(), spec.category(),
                    true,
                    tierStates, tierLabels, posMap,
                    defaultIdx));
        }

        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Optional block detection (e.g. fusion glass)
    // ─────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────
    // Optional block detection (e.g. fusion glass)
    // ─────────────────────────────────────────────────────────────────────────

    private static List<PhantasiaVariantGroup> detectOptionalBlocks(
            String machinePrefix,
            Map<BlockPos, Set<Block>> localPosToAllBlocks,
            Map<Block, List<BlockPos>> loadedBlockToWorldPos,
            PhantasiaLoadedPattern pattern,
            Set<String> excludeIds) {
        Map<String, List<BlockPos>> signatureToLocalPositions = new LinkedHashMap<>();
        Map<String, Set<Block>> signatureToBlocks = new LinkedHashMap<>();

        for (Map.Entry<BlockPos, Set<Block>> entry : localPosToAllBlocks.entrySet()) {
            Set<Block> blocks = entry.getValue();
            if (blocks.size() < 2) continue;

            // ... (keep machine block filtering the same) ...
            Set<Block> nonMachine = new HashSet<>();
            for (Block b : blocks) {
                if (!isMachineBlock(b) && b != Blocks.AIR) nonMachine.add(b);
            }
            if (nonMachine.size() < 2) continue;

            List<String> ids = new ArrayList<>();
            for (Block b : nonMachine) {
                ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(b);
                ids.add(rl != null ? rl.toString() : b.getDescriptionId());
            }
            Collections.sort(ids);
            String key = String.join("|", ids);

            signatureToLocalPositions.computeIfAbsent(key, k -> new ArrayList<>()).add(entry.getKey());
            signatureToBlocks.putIfAbsent(key, nonMachine);
        }

        List<PhantasiaVariantGroup> result = new ArrayList<>();

        for (Map.Entry<String, List<BlockPos>> sigEntry : signatureToLocalPositions.entrySet()) {
            Set<Block> blocks = signatureToBlocks.get(sigEntry.getKey());

            // FIX: Removed the trailing comma after BlockPos
            List<BlockPos> localPositions = sigEntry.getValue();

            List<Block> sortedBlocks = new ArrayList<>(blocks);
            sortedBlocks.sort(Comparator.comparingInt(b -> {
                List<BlockPos> wp = loadedBlockToWorldPos.get(b);
                return wp != null ? wp.size() : 0;
            }));

            List<BlockState> options = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (Block b : sortedBlocks) {
                options.add(b.defaultBlockState());
                labels.add(blockDisplayName(b.defaultBlockState()));
            }

            Block primaryBlock = sortedBlocks.get(0);
            ResourceLocation primaryRl = ForgeRegistries.BLOCKS.getKey(primaryBlock);
            String groupId = "optional_" + (primaryRl != null ? primaryRl.toString().replace(":", "_") : "unknown");
            if (excludeIds.contains(groupId)) continue;

            Map<BlockPos, Integer> posMap = new HashMap<>();
            for (BlockPos local : localPositions) {
                BlockPos world = pattern.localToWorld.get(local);
                if (world == null) continue;
                BlockInfo info = pattern.blockMap.get(world);
                Block loadedBlock = (info != null && !info.getBlockState().isAir()) ? info.getBlockState().getBlock() : null;
                int baseIdx = 1;
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

            result.add(new PhantasiaVariantGroup(
                    machinePrefix + "::" + groupId,
                    blockDisplayName(options.get(0)),
                    Category.OPTIONAL,
                    true,
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