package net.phoenixvine.phantasia.common.data.variant;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phantasia.common.data.pattern.PhantasiaLoadedPattern;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.common.multiblock.PhantasiaMultiblockRegistry;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import lombok.Getter;

import java.util.*;

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

    private final String id;

    private final String label;

    private final Category category;

    private final boolean shownByDefault;

    private final List<BlockState> options;

    private final List<String> optionLabels;

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

    public static PhantasiaVariantGroup create(String id, String label, Category category,
                                               boolean shownByDefault,
                                               List<BlockState> options, List<String> optionLabels,
                                               Map<BlockPos, Integer> positionBaseIndex,
                                               int forcedDefaultIndex) {
        return new PhantasiaVariantGroup(id, label, category, shownByDefault,
                options, optionLabels, positionBaseIndex, forcedDefaultIndex);
    }

    public boolean hasChoice() {
        return options.size() > 1;
    }

    public int defaultIndex() {
        if (forcedDefaultIndex != -1) {
            return forcedDefaultIndex;
        }
        return shownByDefault ? 0 : options.size() - 1;
    }

    public static List<PhantasiaVariantGroup> compile(
                                                      PhantasiaScriptData data,
                                                      IPhantasiaMultiblockDefinition definition,
                                                      PhantasiaLoadedPattern pattern) {
        List<PhantasiaVariantGroup> result = new ArrayList<>();
        Set<String> explicitIds = new HashSet<>();

        String machinePrefix = definition.getId().toString();

        for (PhantasiaScriptData.OptionalGroupData ogd : data.getOptionalGroups()) {
            PhantasiaVariantGroup group = compileManual(machinePrefix, ogd, pattern);
            if (group != null && group.hasChoice()) {
                result.add(group);
                explicitIds.add(ogd.getId());
            }
        }

        try {
            result.addAll(definition.detectProviderVariants(data, pattern, machinePrefix, explicitIds));
        } catch (Exception e) {
            net.phoenixvine.phantasia.Phantasia.LOGGER.warn("[Phantasia] Provider variant detection failed for {}: {}",
                    definition.getId(), e.getMessage());
        }

        if (definition.shouldAutoDetectVariants()) {
            try {
                result.addAll(detectOptionalBlocks(machinePrefix, definition.getAllShapes(), pattern, explicitIds));
            } catch (Exception e) {
                net.phoenixvine.phantasia.Phantasia.LOGGER.warn(
                        "[Phantasia] Optional block detection failed for {}: {}",
                        definition.getId(), e.getMessage());
            }
        }

        return Collections.unmodifiableList(result);
    }

    private static PhantasiaVariantGroup compileManual(
                                                       String machinePrefix,
                                                       PhantasiaScriptData.OptionalGroupData ogd,
                                                       PhantasiaLoadedPattern pattern) {
        BlockState primary = resolveBlock(ogd.getPrimaryBlock());
        if (primary == null) return null;

        List<BlockState> options = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        options.add(primary);
        labels.add(blockDisplayName(primary));

        if (ogd.getFallbackBlock() != null && !ogd.getFallbackBlock().isBlank()) {
            BlockState fallback = resolveBlock(ogd.getFallbackBlock());
            if (fallback != null) {
                options.add(fallback);
                labels.add(blockDisplayName(fallback));
            }
        }
        for (String extra : ogd.getAdditionalBlocks()) {
            BlockState s = resolveBlock(extra);
            if (s != null) {
                options.add(s);
                labels.add(blockDisplayName(s));
            }
        }
        if (options.size() < 2) return null;

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

    public static List<PhantasiaVariantGroup> detectOptionalBlocks(
                                                                   String machinePrefix,
                                                                   List<IPhantasiaMultiblockShape> allShapes,
                                                                   PhantasiaLoadedPattern pattern,
                                                                   Set<String> excludeIds) {
        Map<BlockPos, Set<Block>> localPosToAllBlocks = new HashMap<>();
        Map<Block, List<BlockPos>> loadedShapeBlockToWorldPos = new HashMap<>();

        for (Map.Entry<BlockPos, BlockPos> e : pattern.localToWorld.entrySet()) {
            BlockPos local = e.getKey();
            BlockPos world = e.getValue();
            PhantasiaBlockInfo info = pattern.blockMap.get(world);
            if (info == null) continue;
            BlockState state = info.getBlockState();
            if (state == null || state.isAir()) continue;
            Block block = state.getBlock();
            loadedShapeBlockToWorldPos.computeIfAbsent(block, k -> new ArrayList<>()).add(world);
            localPosToAllBlocks.computeIfAbsent(local, k -> new HashSet<>()).add(block);
        }

        for (IPhantasiaMultiblockShape shape : allShapes) {
            PhantasiaBlockInfo[][][] raw = shape.getBlocks();
            if (raw == null) continue;
            for (int x = 0; x < raw.length; x++) {
                if (raw[x] == null) continue;
                for (int y = 0; y < raw[x].length; y++) {
                    if (raw[x][y] == null) continue;
                    for (int z = 0; z < raw[x][y].length; z++) {
                        PhantasiaBlockInfo info = raw[x][y][z];
                        if (info == null) continue;
                        BlockState state = info.getBlockState();
                        if (state == null || state.isAir()) continue;
                        BlockPos local = new BlockPos(x, y, z);
                        localPosToAllBlocks.computeIfAbsent(local, k -> new HashSet<>()).add(state.getBlock());
                    }
                }
            }
        }

        Map<String, List<BlockPos>> signatureToLocalPositions = new LinkedHashMap<>();
        Map<String, Set<Block>> signatureToBlocks = new LinkedHashMap<>();

        for (Map.Entry<BlockPos, Set<Block>> entry : localPosToAllBlocks.entrySet()) {
            Set<Block> blocks = entry.getValue();
            if (blocks.size() < 2) continue;

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
            List<BlockPos> localPositions = sigEntry.getValue();

            List<Block> sortedBlocks = new ArrayList<>(blocks);

            sortedBlocks.sort(Comparator
                    .comparingInt((Block b) -> {
                        List<BlockPos> wp = loadedShapeBlockToWorldPos.get(b);
                        return wp != null ? wp.size() : 0;
                    })
                    .thenComparing(b -> {
                        ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(b);
                        return rl != null ? rl.toString() : b.getDescriptionId();
                    }));

            List<BlockState> options = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (Block b : sortedBlocks) {
                options.add(b.defaultBlockState());
                labels.add(blockDisplayName(b.defaultBlockState()));
            }

            String groupId = "optional_" + sigEntry.getKey().replace(":", "_").replace("|", "__");
            if (excludeIds.contains(groupId)) continue;

            Map<BlockPos, Integer> posMap = new HashMap<>();
            for (BlockPos local : localPositions) {
                BlockPos world = pattern.localToWorld.get(local);
                if (world == null) continue;
                PhantasiaBlockInfo info = pattern.blockMap.get(world);
                Block loadedBlock = (info != null && !info.getBlockState().isAir()) ? info.getBlockState().getBlock() :
                        null;
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
        return PhantasiaMultiblockRegistry.isControllerBlock(b.defaultBlockState()) ||
                PhantasiaMultiblockRegistry.isPartBlock(b.defaultBlockState());
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
