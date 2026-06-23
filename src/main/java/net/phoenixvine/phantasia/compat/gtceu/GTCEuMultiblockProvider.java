package net.phoenixvine.phantasia.compat.gtceu;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.item.MetaMachineItem;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockProvider;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import java.util.*;

/**
 * Provides GTCEu multiblock definitions to Phantasia's registry.
 * Only instantiated and registered when GTCEu is on the classpath.
 */
public class GTCEuMultiblockProvider implements IPhantasiaMultiblockProvider {

    private final Map<ResourceLocation, GTCEuMultiblockDefinition> cache = new HashMap<>();

    @Override
    public String getModId() {
        return "gtceu";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Optional<IPhantasiaMultiblockDefinition> resolve(String machineId) {
        try {
            ResourceLocation rl = machineId.contains(":") ? new ResourceLocation(machineId) :
                    new ResourceLocation("gtceu", machineId);
            MachineDefinition def = GTRegistries.MACHINES.get(rl);
            if (def instanceof MultiblockMachineDefinition multi) {
                return Optional.of(cache.computeIfAbsent(rl, k -> new GTCEuMultiblockDefinition(multi)));
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    @Override
    public Optional<PhantasiaBlockInfo> resolveBlock(String id) {
        try {
            ResourceLocation rl = id.contains(":") ? new ResourceLocation(id) : new ResourceLocation("gtceu", id);
            MachineDefinition def = GTRegistries.MACHINES.get(rl);
            if (def != null) {
                var block = def.getBlock();
                if (block != null) return Optional.of(PhantasiaBlockInfo.fromBlockState(block.defaultBlockState()));
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    @Override
    public List<IPhantasiaMultiblockDefinition> getAllDefinitions() {
        List<IPhantasiaMultiblockDefinition> result = new ArrayList<>();
        for (MachineDefinition def : GTRegistries.MACHINES) {
            if (def instanceof MultiblockMachineDefinition multi) {
                ResourceLocation id = multi.getId();
                result.add(cache.computeIfAbsent(id, k -> new GTCEuMultiblockDefinition(multi)));
            }
        }
        return result;
    }

    @Override
    public Optional<IPhantasiaMultiblockDefinition> resolveFromItem(ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        if (!(stack.getItem() instanceof MetaMachineItem machineItem)) return Optional.empty();
        var def = machineItem.getDefinition();
        if (!(def instanceof MultiblockMachineDefinition multi)) return Optional.empty();
        return Optional.of(cache.computeIfAbsent(multi.getId(), k -> new GTCEuMultiblockDefinition(multi)));
    }

    @Override
    public boolean isControllerBlock(BlockState state) {
        return state.getBlock() instanceof MetaMachineBlock mmb &&
                mmb.getDefinition() instanceof MultiblockMachineDefinition;
    }

    @Override
    public boolean isPartBlock(BlockState state) {
        if (!(state.getBlock() instanceof MetaMachineBlock mmb)) return false;
        if (mmb.getDefinition() instanceof MultiblockMachineDefinition) return false;
        String path = mmb.getDefinition().getId().getPath();
        return path.contains("hatch") || path.contains("bus") || path.contains("port") || path.contains("storage") ||
                path.contains("input") || path.contains("output") || path.contains("muffler") ||
                path.contains("maintenance");
    }

    /** Resolve a looked-at block to its multiblock definition, if it's a GT controller. */
    public Optional<IPhantasiaMultiblockDefinition> resolveControllerFromBlock(BlockState state) {
        if (!(state.getBlock() instanceof MetaMachineBlock mmb)) return Optional.empty();
        if (!(mmb.getDefinition() instanceof MultiblockMachineDefinition multi)) return Optional.empty();
        return Optional.of(cache.computeIfAbsent(multi.getId(), k -> new GTCEuMultiblockDefinition(multi)));
    }

    @Override
    public boolean isFunctionalBlock(BlockState state) {
        if (state.isAir()) return false;
        return state.getBlock() instanceof MetaMachineBlock || state.getBlock().getDescriptionId().contains("frame") ||
                state.getBlock().getDescriptionId().contains("gearbox");
    }
}
