package net.phoenixvine.phantasia.compat.arsnouveaucompat;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;

import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A static (no-recipe) multiblock definition for Ars Nouveau setups that don't
 * have a recipe registry — summoning setups, turrets, sourcelinks, etc.
 * All content is provided via suppliers so block/item registry access is deferred
 * until after mod init.
 *
 * <p>An optional {@link SceneTickHandler} can be supplied to drive per-tick
 * animations (e.g. turret recoil, rotation) without subclassing.
 */
public class ArsNouveauStaticDefinition implements IPhantasiaMultiblockDefinition {

    @FunctionalInterface
    public interface SceneTickHandler {
        void tick(PhantasiaTrackedDummyWorld level, Map<BlockPos, BlockPos> localToWorld, int sceneTick);
    }

    @FunctionalInterface
    public interface ShapeLoadHandler {
        void onLoaded(PhantasiaTrackedDummyWorld level, Map<BlockPos, BlockPos> localToWorld);
    }

    private final ResourceLocation id;
    private final String displayName;
    private final Supplier<ItemStack> iconSupplier;
    private final Supplier<BlockInfo[][][]> layoutSupplier;
    private final Supplier<PhantasiaScriptData> scriptSupplier;
    @Nullable private final SceneTickHandler sceneTickHandler;
    @Nullable private ShapeLoadHandler shapeLoadHandler;

    private final List<IPhantasiaMultiblockShape> shapes;

    // Lazy cache
    private ItemStack cachedIcon = null;
    private BlockInfo[][][] cachedLayout = null;

    public ArsNouveauStaticDefinition(ResourceLocation id,
                                       String displayName,
                                       Supplier<ItemStack> iconSupplier,
                                       Supplier<BlockInfo[][][]> layoutSupplier,
                                       Supplier<PhantasiaScriptData> scriptSupplier) {
        this(id, displayName, iconSupplier, layoutSupplier, scriptSupplier, null);
    }

    public ArsNouveauStaticDefinition(ResourceLocation id,
                                       String displayName,
                                       Supplier<ItemStack> iconSupplier,
                                       Supplier<BlockInfo[][][]> layoutSupplier,
                                       Supplier<PhantasiaScriptData> scriptSupplier,
                                       @Nullable SceneTickHandler sceneTickHandler) {
        this.id = id;
        this.displayName = displayName;
        this.iconSupplier = iconSupplier;
        this.layoutSupplier = layoutSupplier;
        this.scriptSupplier = scriptSupplier;
        this.sceneTickHandler = sceneTickHandler;

        IPhantasiaMultiblockShape shape = this::getLayout;
        this.shapes = List.of(shape);
    }

    public ArsNouveauStaticDefinition withShapeLoadHandler(ShapeLoadHandler handler) {
        this.shapeLoadHandler = handler;
        return this;
    }

    @Override public ResourceLocation getId()                             { return id; }
    @Override public String getDisplayName()                               { return displayName; }
    @Override public List<IPhantasiaMultiblockShape> getMatchingShapes()  { return shapes; }
    @Override public List<IPhantasiaMultiblockShape> getAllShapes()        { return shapes; }

    @Override
    public ItemStack getIcon() {
        if (cachedIcon == null) cachedIcon = iconSupplier.get();
        return cachedIcon;
    }

    @Override
    public void onShapeLoaded(PhantasiaTrackedDummyWorld level,
                              BlockPos origin,
                              Map<BlockPos, BlockInfo> blockMap,
                              Map<BlockPos, BlockPos> localToWorld) {
        // Ensure every tile has its level set (TrackedDummyWorld skips setLevel on registration)
        for (BlockPos worldPos : localToWorld.values()) {
            BlockEntity be = level.getBlockEntity(worldPos);
            if (be != null && be.getLevel() == null) be.setLevel(level);
        }
        // Clear entities from any previous scene opened on this definition instance
        level.clearSceneEntities();
        // Delegate to optional custom handler
        if (shapeLoadHandler != null) shapeLoadHandler.onLoaded(level, localToWorld);
    }

    private BlockInfo[][][] getLayout() {
        if (cachedLayout == null) cachedLayout = layoutSupplier.get();
        return cachedLayout;
    }

    @Nullable
    @Override
    public PhantasiaScriptData getDefaultScriptData() {
        return scriptSupplier.get();
    }

    @Override
    public void onSceneTick(PhantasiaTrackedDummyWorld level,
                             Map<BlockPos, BlockPos> localToWorld,
                             int sceneTick) {
        if (sceneTickHandler != null) sceneTickHandler.tick(level, localToWorld, sceneTick);
    }
}
