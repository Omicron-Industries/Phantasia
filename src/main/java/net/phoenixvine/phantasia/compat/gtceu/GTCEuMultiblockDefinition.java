package net.phoenixvine.phantasia.compat.gtceu;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.common.data.pattern.PhantasiaLoadedPattern;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;
import net.phoenixvine.phantasia.common.data.variant.PhantasiaVariantGroup;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;

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

    // ── GTCEu-specific variant detection (PartAbility tiers) ─────────────────

    @Override
    public List<PhantasiaVariantGroup> detectProviderVariants(PhantasiaScriptData data,
                                                              PhantasiaLoadedPattern pattern,
                                                              String machinePrefix,
                                                              Set<String> explicitIds) {
        // Build lookup maps from the loaded pattern
        Map<Block, List<BlockPos>> loadedBlockToWorldPos = new HashMap<>();
        for (Map.Entry<BlockPos, BlockPos> e : pattern.localToWorld.entrySet()) {
            BlockPos world = e.getValue();
            BlockInfo info = pattern.blockMap.get(world);
            if (info == null) continue;
            BlockState state = info.getBlockState();
            if (state == null || state.isAir()) continue;
            loadedBlockToWorldPos.computeIfAbsent(state.getBlock(), k -> new ArrayList<>()).add(world);
        }

        return detectPartAbilityGroups(machinePrefix, loadedBlockToWorldPos, pattern, explicitIds);
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
                new AbilitySpec(PartAbility.INPUT_ENERGY, "energy_hatch_in", "Energy Hatch (Input)", PhantasiaVariantGroup.Category.HATCHES_BUSES, 0, 13),
                new AbilitySpec(PartAbility.OUTPUT_ENERGY, "energy_hatch_out", "Energy Hatch (Output)", PhantasiaVariantGroup.Category.HATCHES_BUSES, 0, 13),
                new AbilitySpec(PartAbility.IMPORT_FLUIDS, "fluid_hatch_in", "Fluid Hatch (Input)", PhantasiaVariantGroup.Category.HATCHES_BUSES, 0, 13),
                new AbilitySpec(PartAbility.EXPORT_FLUIDS, "fluid_hatch_out", "Fluid Hatch (Output)", PhantasiaVariantGroup.Category.HATCHES_BUSES, 0, 13),
                new AbilitySpec(PartAbility.IMPORT_ITEMS, "item_bus_in", "Item Bus (Input)", PhantasiaVariantGroup.Category.HATCHES_BUSES, 0, 13),
                new AbilitySpec(PartAbility.EXPORT_ITEMS, "item_bus_out", "Item Bus (Output)", PhantasiaVariantGroup.Category.HATCHES_BUSES, 0, 13),
                new AbilitySpec(PartAbility.MUFFLER, "muffler_hatch", "Muffler Hatch", PhantasiaVariantGroup.Category.MUFFLERS, 0, 13));

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
