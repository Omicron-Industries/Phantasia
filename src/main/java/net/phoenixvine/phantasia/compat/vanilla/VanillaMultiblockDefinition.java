package net.phoenixvine.phantasia.compat.vanilla;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public final class VanillaMultiblockDefinition implements IPhantasiaMultiblockDefinition {

    private final ResourceLocation id;
    private final String displayName;
    private final ItemStack icon;
    private final List<IPhantasiaMultiblockShape> shapes;
    @Nullable private final PhantasiaScriptData defaultScript;
    @Nullable private final EntityType<? extends LivingEntity> mobType;
    /** If true, a beacon beam is shown in the dummy world on the working step. */
    private final boolean showBeam;
    /** If true, a conduit block entity is activated in the working step. */
    private final boolean showConduit;

    /** World-space position of the beacon block, populated in onShapeLoaded. */
    @Nullable private BlockPos beaconWorldPos;
    /** World-space position of the conduit block, populated in onShapeLoaded. */
    @Nullable private BlockPos conduitWorldPos;
    /** World-space position where the mob should spawn (top-center of structure). */
    @Nullable private BlockPos mobWorldPos;
    /** Currently spawned scene entity (null when not in working step). */
    @Nullable private LivingEntity cachedMob;
    /** Tracks whether the working step is active so onSceneTick can re-apply the beam. */
    private boolean isWorking = false;

    VanillaMultiblockDefinition(ResourceLocation id, String displayName, ItemStack icon,
                                List<IPhantasiaMultiblockShape> shapes,
                                @Nullable PhantasiaScriptData defaultScript,
                                @Nullable EntityType<? extends LivingEntity> mobType,
                                int mobRenderScaleUnused,
                                boolean showBeam,
                                boolean showConduit) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.shapes = shapes;
        this.defaultScript = defaultScript;
        this.mobType = mobType;
        this.showBeam = showBeam;
        this.showConduit = showConduit;
    }

    @Override public ResourceLocation getId() { return id; }
    @Override public String getDisplayName() { return displayName; }
    @Override public ItemStack getIcon() { return icon; }
    @Override public List<IPhantasiaMultiblockShape> getMatchingShapes() { return shapes; }
    @Override public List<IPhantasiaMultiblockShape> getAllShapes() { return shapes; }
    @Override @Nullable public PhantasiaScriptData getDefaultScriptData() { return defaultScript; }
    @Override public boolean shouldAutoDetectVariants() { return false; }

    @Override
    public void onShapeLoaded(PhantasiaTrackedDummyWorld level, BlockPos origin,
                              Map<BlockPos, PhantasiaBlockInfo> blockMap,
                              Map<BlockPos, BlockPos> localToWorld,
                              @Nullable PhantasiaScriptData script) {
        if (showBeam) {
            beaconWorldPos = null;
            for (Map.Entry<BlockPos, PhantasiaBlockInfo> e : blockMap.entrySet()) {
                if (e.getValue() != null && e.getValue().getBlockState().is(Blocks.BEACON)) {
                    beaconWorldPos = localToWorld.getOrDefault(e.getKey(), e.getKey());
                    break;
                }
            }
        }

        if (showConduit) {
            conduitWorldPos = null;
            for (Map.Entry<BlockPos, PhantasiaBlockInfo> e : blockMap.entrySet()) {
                if (e.getValue() != null && e.getValue().getBlockState().is(Blocks.CONDUIT)) {
                    conduitWorldPos = localToWorld.getOrDefault(e.getKey(), e.getKey());
                    break;
                }
            }
        }

        // Register post-tick hooks so vanilla ticks (which reset beam/conduit state) are immediately
        // corrected without needing to skip those ticks entirely.
        level.clearPostTickHooks();
        if (showBeam) level.addPostTickHook(() -> applyBeamState(level));
        if (showConduit) level.addPostTickHook(() -> applyConduitState(level));

        if (mobType != null) {
            // Clear any previously spawned mob when shape reloads.
            level.clearSceneEntities();
            cachedMob = null;

            // Compute spawn position: center (XZ) of all blocks, one above the highest Y.
            int maxY = Integer.MIN_VALUE;
            long sumX = 0, sumZ = 0;
            int count = 0;
            for (Map.Entry<BlockPos, PhantasiaBlockInfo> e : blockMap.entrySet()) {
                if (e.getValue() == null || e.getValue().getBlockState().isAir()) continue;
                BlockPos wp = localToWorld.getOrDefault(e.getKey(), e.getKey());
                maxY = Math.max(maxY, wp.getY());
                sumX += wp.getX();
                sumZ += wp.getZ();
                count++;
            }
            if (count > 0) {
                mobWorldPos = new BlockPos((int)(sumX / count), maxY - 2, (int)(sumZ / count));
            }
        }
    }

    @Override
    public void setMachineWorking(PhantasiaTrackedDummyWorld level, boolean working) {
        isWorking = working;
        applyBeamState(level);
        applyConduitState(level);

        if (mobType == null) return;
        // Remove any previously spawned mob.
        level.clearSceneEntities();
        cachedMob = null;
        if (!working || mobWorldPos == null) return;

        var mob = mobType.create(level);
        if (mob == null) return;
        if (mob instanceof Mob m) { m.setNoAi(true); m.setSilent(true); }
        mob.setPos(mobWorldPos.getX() + 0.5, mobWorldPos.getY(), mobWorldPos.getZ() + 0.5);
        mob.xOld = mob.getX(); mob.yOld = mob.getY(); mob.zOld = mob.getZ();
        mob.yRotO = 0f;
        level.addSceneEntity(mob);
        cachedMob = mob;
    }

    @Override
    public void onSceneTick(PhantasiaTrackedDummyWorld level,
                            Map<BlockPos, BlockPos> localToWorld, int sceneTick) {
        // Re-apply beam every tick: a partial bake can recreate the beacon BE, losing beam sections.
        if (showBeam) applyBeamState(level);
        if (showConduit) applyConduitState(level);

        if (cachedMob != null) {
            float yaw = (sceneTick % 160) * (360f / 160f);
            cachedMob.setYRot(yaw);
            cachedMob.yRotO = yaw;
        }
    }

    private void applyBeamState(PhantasiaTrackedDummyWorld level) {
        if (!showBeam || beaconWorldPos == null) return;
        var be = level.getBlockEntity(beaconWorldPos);
        if (!(be instanceof BeaconBlockEntity beacon)) return;
        List<BeaconBlockEntity.BeaconBeamSection> sections = beamSectionsMutable(beacon);
        if (sections == null) return;
        if (isWorking) {
            if (sections.isEmpty()) sections.add(new BeaconBlockEntity.BeaconBeamSection(new float[]{1f, 1f, 1f}));
        } else {
            sections.clear();
        }
    }

    private void applyConduitState(PhantasiaTrackedDummyWorld level) {
        if (!showConduit || conduitWorldPos == null) return;
        var be = level.getBlockEntity(conduitWorldPos);
        if (be == null) return;
        try {
            java.lang.reflect.Field f = be.getClass().getDeclaredField("isActive");
            f.setAccessible(true);
            f.set(be, isWorking);
        } catch (Exception e) {
            net.phoenixvine.phantasia.Phantasia.LOGGER.warn("[Phantasia] conduit isActive reflection failed: {}", e.getMessage());
        }
    }

    /**
     * Returns a mutable beamSections list for the beacon.
     * When setRemoved() is called on the BE, it replaces the ArrayList with ImmutableList.of().
     * We detect this and swap in a fresh ArrayList via reflection so we can populate it.
     */
    @SuppressWarnings("unchecked")
    private static List<BeaconBlockEntity.BeaconBeamSection> beamSectionsMutable(BeaconBlockEntity beacon) {
        var sections = beacon.getBeamSections();
        if (sections instanceof java.util.ArrayList) return sections;
        try {
            java.lang.reflect.Field f = BeaconBlockEntity.class.getDeclaredField("beamSections");
            f.setAccessible(true);
            Object current = f.get(beacon);
            if (current instanceof java.util.ArrayList) {
                return (List<BeaconBlockEntity.BeaconBeamSection>) current;
            }
            // Field holds ImmutableList.of() — replace with a fresh mutable list.
            java.util.ArrayList<BeaconBlockEntity.BeaconBeamSection> fresh = new java.util.ArrayList<>();
            f.set(beacon, fresh);
            return fresh;
        } catch (Exception e) {
            net.phoenixvine.phantasia.Phantasia.LOGGER.warn("[Phantasia] beacon beam reflection failed: {}", e.getMessage());
            return null;
        }
    }
}
