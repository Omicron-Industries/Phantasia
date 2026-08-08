package net.phoenixvine.phantasia.common.data.pattern;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PatternState {

    @Getter
    protected @Nullable BlockPos controllerPos;
    @Getter
    protected @Nullable MultiblockControllerMachine controller;
    @Getter
    @Setter
    protected boolean isFormed = false;
    @Getter
    protected volatile boolean isFlipped = false;
    @Setter
    @Getter
    protected boolean actualFlipped = false;
    @Setter
    protected boolean shouldUpdate = true;

    @Getter
    protected @Nullable List<Object> errors;

    @Setter
    @Getter
    protected CheckState state = CheckState.UNINITIALIZED;

    @Getter
    protected final Long2ObjectMap<PhantasiaBlockInfo> cache = new Long2ObjectOpenHashMap<>();

    public void setController(MultiblockControllerMachine controller, BlockPos controllerPos) {
        this.controller = controller;
        this.controllerPos = controllerPos;
    }

    @ApiStatus.Internal
    public void setFlipped(boolean flipped) {
        isFlipped = flipped;
    }

    public boolean shouldUpdate() {
        return shouldUpdate;
    }

    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    public void setError(@Nullable Object error) {
        this.errors = error != null ? List.of(error) : null;
    }

    public void setErrors(@Nullable List<Object> error) {
        this.errors = error;
    }

    public void onBlockStateChanged(BlockPos pos, BlockState oldState, BlockState newState) {
        if (controller == null || controller.getLevel() == null) return;
        if (!(controller.getLevel() instanceof ServerLevel serverLevel)) return;

        if (pos.equals(controllerPos)) {

            if (!newState.is(controller.self().getBlockState().getBlock())) {
                controller.onStructureInvalid();

                MultiblockState mState = controller.getMultiblockState();
                if (mState != null) {
                    MultiblockWorldSavedData.getOrCreate(serverLevel).removeMapping(mState);
                }
            }
        } else {

            if (oldState.getBlock() == newState.getBlock()) {

                return;
            }

            MultiblockState mState = controller.getMultiblockState();

            if (controller.isFormed()) {

                if (mState != null && mState.hasError()) {
                    controller.onStructureInvalid();
                    MultiblockWorldSavedData.getOrCreate(serverLevel).removeMapping(mState);
                }
            } else {

                if (controller.getPatternLock().tryLock()) {
                    try {

                        controller.onStructureFormed();
                    } finally {
                        controller.getPatternLock().unlock();
                    }
                }
            }
        }
    }

    @Getter
    public enum CheckState {

        VALID_UNCACHED(true, false),
        VALID_CACHED(true, true),
        INVALID_CACHED(false, true),
        INVALID_UNCACHED(false, false),
        UNINITIALIZED(false, false);

        private final boolean valid;
        private final boolean cached;

        CheckState(boolean valid, boolean cached) {
            this.valid = valid;
            this.cached = cached;
        }
    }
}
