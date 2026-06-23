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

/**
 * Backported PatternState tracking utility tailored specifically to
 * match your older GTCEu MultiblockControllerMachine specification.
 */
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

    // Fallback error storage utilizing a generic object list since PatternError doesn't exist
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

    /**
     * Re-engineered block-change tracking logic modified to match your
     * specific MultiblockControllerMachine API footprint.
     */
    public void onBlockStateChanged(BlockPos pos, BlockState oldState, BlockState newState) {
        if (controller == null || controller.getLevel() == null) return;
        if (!(controller.getLevel() instanceof ServerLevel serverLevel)) return;

        if (pos.equals(controllerPos)) {
            // 1. Handle Controller block itself being broken/altered
            if (!newState.is(controller.self().getBlockState().getBlock())) {
                controller.onStructureInvalid(); // Converted from invalidateStructure(name)

                // Uses your exact MultiblockWorldSavedData package and implementation
                MultiblockState mState = controller.getMultiblockState();
                if (mState != null) {
                    MultiblockWorldSavedData.getOrCreate(serverLevel).removeMapping(mState);
                }
            }
        } else {
            // 2. Handle a non-controller component changing block state
            if (oldState.getBlock() == newState.getBlock()) {
                // Ignore minor blockstate changes (like technical ticks or fluid/active property shifts)
                return;
            }

            MultiblockState mState = controller.getMultiblockState();

            // Your controller does not use named paths, so we evaluate the structure globally
            if (controller.isFormed()) {
                // If it was already formed, check if the change broke the layout
                // (In your version, this is handled on next async ticking tick or manually via checkPattern)
                if (mState != null && mState.hasError()) {
                    controller.onStructureInvalid();
                    MultiblockWorldSavedData.getOrCreate(serverLevel).removeMapping(mState);
                }
            } else {
                // If it wasn't formed, explicitly queue an updated block check request
                // via your native sync validation trigger
                if (controller.getPatternLock().tryLock()) {
                    try {
                        // Triggers standard structure formation sequences on your version
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
