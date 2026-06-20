package net.phoenixvine.phantasia.compat.gtceu;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.common.data.pattern.PhantasiaLoadedPattern;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;
import net.phoenixvine.phantasia.common.data.variant.PhantasiaVariantGroup;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;

import com.mojang.blaze3d.systems.RenderSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/** Wraps a GTCEu {@link MultiblockMachineDefinition} as an {@link IPhantasiaMultiblockDefinition}. */
public class GTCEuMultiblockDefinition implements IPhantasiaMultiblockDefinition {

    private static final Logger LOGGER = LoggerFactory.getLogger(GTCEuMultiblockDefinition.class);

    private final MultiblockMachineDefinition definition;
    private List<IPhantasiaMultiblockShape> cachedShapes;

    public GTCEuMultiblockDefinition(MultiblockMachineDefinition definition) {
        this.definition = definition;
    }

    public MultiblockMachineDefinition getDefinition() {
        return definition;
    }

    // ── IPhantasiaMultiblockDefinition ────────────────────────────────────────

    @Override
    public ResourceLocation getId() {
        return definition.getId();
    }

    @Override
    public List<IPhantasiaMultiblockShape> getMatchingShapes() {
        if (cachedShapes == null) {
            List<IPhantasiaMultiblockShape> shapes = new ArrayList<>();
            var gtShapes = definition.getMatchingShapes();
            if (gtShapes != null) {
                for (var s : gtShapes) shapes.add(new GTCEuMultiblockShape(s));
            }
            cachedShapes = Collections.unmodifiableList(shapes);
        }
        return cachedShapes;
    }

    @Override
    public List<IPhantasiaMultiblockShape> getAllShapes() {
        return getMatchingShapes();
    }

    @Override
    public String getDisplayName() {
        try {
            return definition.getBlock().getName().getString();
        } catch (Exception e) {
            return definition.getId().getPath();
        }
    }

    @Override
    public ItemStack getIcon() {
        try {
            return new ItemStack(definition.getBlock());
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * After blocks are placed in the dummy world, find the controller BE and fire
     * {@code onStructureFormed()} on the render thread so GT machine state is correct.
     */
    @Override
    public void onShapeLoaded(PhantasiaTrackedDummyWorld level, BlockPos origin,
                              Map<BlockPos, BlockInfo> blockMap,
                              Map<BlockPos, BlockPos> localToWorld) {
        List<IMultiPart> parts = new ArrayList<>();
        MultiblockControllerMachine controller = null;

        for (BlockPos wp : localToWorld.values()) {
            try {
                var be = level.getBlockEntity(wp);
                if (!(be instanceof MetaMachineBlockEntity mmbe)) continue;
                var machine = mmbe.getMetaMachine();
                if (machine instanceof MultiblockControllerMachine ctrl && controller == null) {
                    controller = ctrl;
                } else if (machine instanceof IMultiPart part) {
                    parts.add(part);
                }
            } catch (Exception ignored) {}
        }

        if (controller == null) return;

        final MultiblockControllerMachine ctrl = controller;
        final List<IMultiPart> partsCopy = List.copyOf(parts);
        RenderSystem.recordRenderCall(() -> {
            try {
                var mState = ctrl.getMultiblockState();
                if (mState != null) {
                    mState.setError(null);
                    mState.getMatchContext().set("parts", new HashSet<>(partsCopy));
                }
                ctrl.getPatternLock().lock();
                try {
                    ctrl.onStructureFormed();
                } finally {
                    ctrl.getPatternLock().unlock();
                }
                LOGGER.debug("[Phantasia] onStructureFormed simulated for {}", definition.getId());
            } catch (Exception e) {
                LOGGER.error("[Phantasia] onStructureFormed failed for {}: {}", definition.getId(), e.getMessage(), e);
            }
        });
    }

    @Override
    public void setMachineWorking(PhantasiaTrackedDummyWorld level, boolean working) {
        if (level == null) return;
        try {
            for (var be : level.blockEntities.values()) {
                if (!(be instanceof MetaMachineBlockEntity mmbe)) continue;
                var machine = mmbe.getMetaMachine();
                if (!(machine instanceof WorkableMultiblockMachine workable)) continue;
                RecipeLogic logic = workable.getRecipeLogic();
                if (logic != null) {
                    logic.setStatus(working ? RecipeLogic.Status.WORKING : RecipeLogic.Status.IDLE);
                }
                return;
            }
        } catch (Exception ignored) {}
    }

    // ── GTCEu-specific variant detection ─────────────────────────────────────

    @Override
    public List<PhantasiaVariantGroup> detectProviderVariants(PhantasiaScriptData data,
                                                              PhantasiaLoadedPattern pattern,
                                                              String machinePrefix,
                                                              Set<String> explicitIds) {
        return new ArrayList<>(detectPredicateVariants(machinePrefix, pattern, explicitIds));
    }

    /**
     * Reads the BlockPattern's TraceabilityPredicate grid directly.
     * Any position whose predicate has multiple SimplePredicate components (i.e. was built
     * with .or()) becomes a variant group — one option per SimplePredicate, using its
     * candidates supplier for the block list.
     *
     * Groups are keyed by a sorted signature of all candidate block IDs so the ID is
     * stable across world restarts regardless of predicate ordering.
     */
    private List<PhantasiaVariantGroup> detectPredicateVariants(
            String machinePrefix, PhantasiaLoadedPattern pattern, Set<String> excludeIds) {

        com.gregtechceu.gtceu.api.pattern.BlockPattern blockPattern;
        try {
            var factory = definition.getPatternFactory();
            if (factory == null) return List.of();
            blockPattern = factory.get();
            if (blockPattern == null) return List.of();
        } catch (Exception e) {
            return List.of();
        }

        com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate[][][] grid;
        try {
            var f = com.gregtechceu.gtceu.api.pattern.BlockPattern.class.getDeclaredField("blockMatches");
            f.setAccessible(true);
            grid = (com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate[][][]) f.get(blockPattern);
        } catch (Exception e) {
            return List.of();
        }

        // Signature → list of local positions that share the same predicate candidate set.
        // Using LinkedHashMap for deterministic iteration order.
        Map<String, List<BlockPos>> sigToLocal = new LinkedHashMap<>();
        // Signature → ordered list of candidate BlockInfo[] per SimplePredicate component.
        Map<String, List<BlockInfo[]>> sigToCandidates = new LinkedHashMap<>();

        for (int x = 0; x < grid.length; x++) {
            if (grid[x] == null) continue;
            for (int y = 0; y < grid[x].length; y++) {
                if (grid[x][y] == null) continue;
                for (int z = 0; z < grid[x][y].length; z++) {
                    var tp = grid[x][y][z];
                    if (tp == null || tp.common == null || tp.common.size() < 2) continue;

                    // Collect candidate blocks per component; skip if any has no candidates.
                    List<BlockInfo[]> componentCandidates = new ArrayList<>();
                    boolean valid = true;
                    for (var sp : tp.common) {
                        if (sp.candidates == null) { valid = false; break; }
                        BlockInfo[] cands;
                        try { cands = sp.candidates.get(); } catch (Exception ex) { valid = false; break; }
                        if (cands == null || cands.length == 0) { valid = false; break; }
                        componentCandidates.add(cands);
                    }
                    if (!valid || componentCandidates.size() < 2) continue;

                    // Build a deterministic signature from sorted block IDs across all components.
                    List<String> sigParts = new ArrayList<>();
                    for (BlockInfo[] cands : componentCandidates) {
                        List<String> ids = new ArrayList<>();
                        for (BlockInfo bi : cands) {
                            if (bi == null || bi.getBlockState() == null) continue;
                            var rl = net.minecraftforge.registries.ForgeRegistries.BLOCKS
                                    .getKey(bi.getBlockState().getBlock());
                            if (rl != null) ids.add(rl.toString());
                        }
                        Collections.sort(ids);
                        sigParts.add(String.join(",", ids));
                    }
                    Collections.sort(sigParts);
                    String sig = String.join("|", sigParts);

                    sigToLocal.computeIfAbsent(sig, k -> new ArrayList<>()).add(new BlockPos(x, y, z));
                    sigToCandidates.putIfAbsent(sig, componentCandidates);
                }
            }
        }

        List<PhantasiaVariantGroup> result = new ArrayList<>();

        for (Map.Entry<String, List<BlockPos>> entry : sigToLocal.entrySet()) {
            String sig = entry.getKey();
            // Use a stable hash of the signature so the ID is short but deterministic.
            // String.hashCode() is specified in the Java Language Spec — stable across JVMs.
            String groupId = machinePrefix + "::predicate_" + Integer.toUnsignedString(sig.hashCode(), 16);
            if (excludeIds.contains(groupId)) continue;

            List<BlockInfo[]> componentCandidates = sigToCandidates.get(sig);
            List<BlockState> options = new ArrayList<>();
            List<String> labels = new ArrayList<>();

            // Use the first candidate of each component as the representative block state.
            for (BlockInfo[] cands : componentCandidates) {
                BlockState rep = null;
                for (BlockInfo bi : cands) {
                    if (bi != null && bi.getBlockState() != null && !bi.getBlockState().isAir()) {
                        rep = bi.getBlockState();
                        break;
                    }
                }
                if (rep == null) continue;
                options.add(rep);
                labels.add(PhantasiaVariantGroup.blockDisplayName(rep));
            }
            if (options.size() < 2) continue;

            // Skip groups where every option is a MetaMachine part (hatch, bus, muffler, etc.)
            // — there's no visual difference between tiers so these aren't useful variants.
            boolean allParts = options.stream().allMatch(
                    s -> s.getBlock() instanceof com.gregtechceu.gtceu.api.block.MetaMachineBlock);
            if (allParts) continue;

            // Map world positions and determine which option index is currently loaded.
            Map<BlockPos, Integer> posMap = new HashMap<>();
            int defaultIdx = 0;
            boolean foundDefault = false;

            for (BlockPos local : entry.getValue()) {
                BlockPos world = pattern.localToWorld.get(local);
                if (world == null) continue;
                BlockInfo info = pattern.blockMap.get(world);
                if (info == null || info.getBlockState() == null) { posMap.put(world, 0); continue; }
                Block loaded = info.getBlockState().getBlock();

                int idx = 0;
                outer:
                for (int ci = 0; ci < componentCandidates.size(); ci++) {
                    for (BlockInfo bi : componentCandidates.get(ci)) {
                        if (bi != null && bi.getBlockState() != null
                                && bi.getBlockState().getBlock() == loaded) {
                            idx = ci;
                            break outer;
                        }
                    }
                }
                posMap.put(world, idx);
                if (!foundDefault) { defaultIdx = idx; foundDefault = true; }
            }
            if (posMap.isEmpty()) continue;

            result.add(PhantasiaVariantGroup.create(
                    groupId,
                    labels.get(0) + " / " + labels.get(1) + (options.size() > 2 ? " …" : ""),
                    PhantasiaVariantGroup.Category.OPTIONAL,
                    true, options, labels, posMap, defaultIdx));
        }

        return result;
    }

    private static List<PhantasiaVariantGroup> detectPartAbilityGroups(
                                                                       String machinePrefix,
                                                                       Map<Block, List<BlockPos>> loadedBlockToWorldPos,
                                                                       PhantasiaLoadedPattern pattern,
                                                                       Set<String> excludeIds) {
        List<PhantasiaVariantGroup> result = new ArrayList<>();

        record AbilitySpec(PartAbility ability, String id, String label,
                           PhantasiaVariantGroup.Category category, int minTier, int maxTier) {}

        List<AbilitySpec> specs = List.of(
                new AbilitySpec(PartAbility.INPUT_ENERGY, "energy_hatch_in", "Energy Hatch (Input)",
                        PhantasiaVariantGroup.Category.HATCHES_BUSES, 0, 13),
                new AbilitySpec(PartAbility.OUTPUT_ENERGY, "energy_hatch_out", "Energy Hatch (Output)",
                        PhantasiaVariantGroup.Category.HATCHES_BUSES, 0, 13),
                new AbilitySpec(PartAbility.IMPORT_FLUIDS, "fluid_hatch_in", "Fluid Hatch (Input)",
                        PhantasiaVariantGroup.Category.HATCHES_BUSES, 0, 13),
                new AbilitySpec(PartAbility.EXPORT_FLUIDS, "fluid_hatch_out", "Fluid Hatch (Output)",
                        PhantasiaVariantGroup.Category.HATCHES_BUSES, 0, 13),
                new AbilitySpec(PartAbility.IMPORT_ITEMS, "item_bus_in", "Item Bus (Input)",
                        PhantasiaVariantGroup.Category.HATCHES_BUSES, 0, 13),
                new AbilitySpec(PartAbility.EXPORT_ITEMS, "item_bus_out", "Item Bus (Output)",
                        PhantasiaVariantGroup.Category.HATCHES_BUSES, 0, 13),
                new AbilitySpec(PartAbility.MUFFLER, "muffler_hatch", "Muffler Hatch",
                        PhantasiaVariantGroup.Category.MUFFLERS, 0, 13));

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
                    tierLabels.add(PhantasiaVariantGroup.blockDisplayName(st));
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

            result.add(PhantasiaVariantGroup.create(
                    machinePrefix + "::" + spec.id(), spec.label(), spec.category(),
                    true, tierStates, tierLabels, posMap, defaultIdx));
        }

        return result;
    }

    // ── equals / hashCode based on GT definition identity ────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GTCEuMultiblockDefinition other)) return false;
        return definition.equals(other.definition);
    }

    @Override
    public int hashCode() {
        return definition.hashCode();
    }

    @Override
    public String toString() {
        return "GTCEuMultiblockDefinition[" + definition.getId() + "]";
    }
}
