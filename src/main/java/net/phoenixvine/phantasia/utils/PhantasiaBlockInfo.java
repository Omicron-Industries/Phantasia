package net.phoenixvine.phantasia.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public final class PhantasiaBlockInfo {

    public static final PhantasiaBlockInfo EMPTY = new PhantasiaBlockInfo(Blocks.AIR.defaultBlockState(), null);

    private final BlockState blockState;
    @Nullable
    private final CompoundTag nbt;

    private PhantasiaBlockInfo(BlockState blockState, @Nullable CompoundTag nbt) {
        this.blockState = blockState;
        this.nbt = nbt;
    }

    public static PhantasiaBlockInfo fromBlockState(BlockState state) {
        return new PhantasiaBlockInfo(state, null);
    }

    public static PhantasiaBlockInfo fromBlock(Block block) {
        return fromBlockState(block.defaultBlockState());
    }

    public BlockState getBlockState() {
        return blockState;
    }

    /** Creates a new BlockEntity for this block, without setting its level. Returns null if block has no BE. */
    @Nullable
    public BlockEntity getBlockEntity(BlockPos pos) {
        return getBlockEntity(null, pos);
    }

    /**
     * Creates a new BlockEntity for this block. If world is non-null, sets its level. Returns null if block has no BE.
     */
    @Nullable
    public BlockEntity getBlockEntity(@Nullable Level world, BlockPos pos) {
        if (blockState.isAir() || !(blockState.getBlock() instanceof EntityBlock eb)) return null;
        BlockEntity be = eb.newBlockEntity(pos, blockState);
        if (be == null) return null;
        if (nbt != null) be.load(nbt);
        if (world != null) be.setLevel(world);
        return be;
    }
}
