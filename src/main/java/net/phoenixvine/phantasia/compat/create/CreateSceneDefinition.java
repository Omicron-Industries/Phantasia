package net.phoenixvine.phantasia.compat.create;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nullable;

public class CreateSceneDefinition implements IPhantasiaMultiblockDefinition {

    private final ResourceLocation id;
    private final String displayName;
    private final String iconBlock;
    private final IPhantasiaMultiblockShape shape;
    @Nullable
    private final PhantasiaScriptData scriptData;

    private final Supplier<PhantasiaBlockInfo[][][]> blocksSupplier;
    @Nullable
    private PhantasiaBlockInfo[][][] cachedBlocks;
    @Nullable
    private ItemStack cachedIcon;

    public CreateSceneDefinition(String path, String displayName, String iconBlock,
                                 Supplier<PhantasiaBlockInfo[][][]> blocksSupplier,
                                 @Nullable PhantasiaScriptData scriptData) {
        this.id = new ResourceLocation("create", path);
        this.displayName = displayName;
        this.iconBlock = iconBlock;
        this.blocksSupplier = blocksSupplier;
        this.shape = this::resolveBlocks;
        this.scriptData = scriptData;
    }

    private PhantasiaBlockInfo[][][] resolveBlocks() {
        if (cachedBlocks == null) cachedBlocks = blocksSupplier.get();
        return cachedBlocks;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public ItemStack getIcon() {
        if (cachedIcon == null) {
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(iconBlock));
            cachedIcon = block != null && block != Blocks.AIR ? new ItemStack(block) :
                    new ItemStack(Items.CRAFTING_TABLE);
        }
        return cachedIcon;
    }

    @Override
    public List<IPhantasiaMultiblockShape> getMatchingShapes() {
        return List.of(shape);
    }

    @Override
    public List<IPhantasiaMultiblockShape> getAllShapes() {
        return List.of(shape);
    }

    @Override
    @Nullable
    public PhantasiaScriptData getDefaultScriptData() {
        return scriptData;
    }

    private static final float WORKING_SPEED = 32f;
    private static final String KINETIC_BE = "com.simibubi.create.content.kinetics.base.KineticBlockEntity";

    private static final java.util.concurrent.ConcurrentHashMap<Class<?>, java.util.Optional<java.lang.reflect.Field>> kbeFieldCache = new java.util.concurrent.ConcurrentHashMap<>();

    @Nullable
    private static java.lang.reflect.Field resolveSpeedField(Class<?> cls) {
        java.util.Optional<java.lang.reflect.Field> cached = kbeFieldCache.get(cls);
        if (cached != null) return cached.orElse(null);

        Class<?> c = cls;
        while (c != null) {
            if (KINETIC_BE.equals(c.getName())) {
                try {
                    java.lang.reflect.Field f = c.getDeclaredField("speed");
                    f.setAccessible(true);
                    kbeFieldCache.put(cls, java.util.Optional.of(f));
                    return f;
                } catch (Exception ignored) {
                    break;
                }
            }
            c = c.getSuperclass();
        }
        kbeFieldCache.put(cls, java.util.Optional.empty());
        return null;
    }

    @Override
    public void applyWorkingState(PhantasiaTrackedDummyWorld level, Set<BlockPos> positions,
                                  Map<BlockPos, PhantasiaBlockInfo> blockMap, boolean working) {
        float targetSpeed = working ? WORKING_SPEED : 0f;
        for (BlockPos pos : positions) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) continue;
            java.lang.reflect.Field f = resolveSpeedField(be.getClass());
            if (f != null) {
                try {
                    f.set(be, targetSpeed);
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public boolean shouldAutoDetectVariants() {
        return false;
    }
}
