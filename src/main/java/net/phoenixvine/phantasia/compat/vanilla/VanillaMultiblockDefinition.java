package net.phoenixvine.phantasia.compat.vanilla;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

public final class VanillaMultiblockDefinition implements IPhantasiaMultiblockDefinition {

    private final ResourceLocation id;
    private final String displayName;
    private final ItemStack icon;
    private final List<IPhantasiaMultiblockShape> shapes;
    @Nullable
    private final PhantasiaScriptData defaultScript;
    @Nullable
    private final EntityType<? extends LivingEntity> mobType;

    private final boolean showBeam;

    private final boolean showConduit;

    @Nullable
    private BlockPos beaconWorldPos;

    @Nullable
    private BlockPos conduitWorldPos;

    @Nullable
    private BlockPos mobWorldPos;

    @Nullable
    private LivingEntity cachedMob;

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
        return icon;
    }

    @Override
    public List<IPhantasiaMultiblockShape> getMatchingShapes() {
        return shapes;
    }

    @Override
    public List<IPhantasiaMultiblockShape> getAllShapes() {
        return shapes;
    }

    @Override
    @Nullable
    public PhantasiaScriptData getDefaultScriptData() {
        return defaultScript;
    }

    @Override
    public boolean shouldAutoDetectVariants() {
        return false;
    }

    @Override
    public void onShapeLoaded(PhantasiaTrackedDummyWorld level, BlockPos origin,
                              Map<BlockPos, PhantasiaBlockInfo> blockMap,
                              Map<BlockPos, BlockPos> localToWorld) {
        onShapeLoaded(level, origin, blockMap, localToWorld, null);
    }

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
            net.phoenixvine.phantasia.Phantasia.LOGGER.info("[Phantasia] onShapeLoaded beacon: pos={}", beaconWorldPos);
        }

        if (showConduit) {
            conduitWorldPos = null;
            for (Map.Entry<BlockPos, PhantasiaBlockInfo> e : blockMap.entrySet()) {
                if (e.getValue() != null && e.getValue().getBlockState().is(Blocks.CONDUIT)) {
                    conduitWorldPos = localToWorld.getOrDefault(e.getKey(), e.getKey());
                    break;
                }
            }
            net.phoenixvine.phantasia.Phantasia.LOGGER.info("[Phantasia] onShapeLoaded conduit: pos={}",
                    conduitWorldPos);
        }

        level.clearPreRenderHooks();
        if (showBeam) level.addPreRenderHook(() -> applyBeamState(level));
        if (showConduit) level.addPreRenderHook(() -> applyConduitState(level));

        level.clearPostTickHooks();
        if (showBeam) level.addPostTickHook(() -> applyBeamState(level));
        if (showConduit) level.addPostTickHook(() -> applyConduitState(level));

        if (mobType != null) {

            level.clearSceneEntities();
            cachedMob = null;

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
                mobWorldPos = new BlockPos((int) (sumX / count), maxY - 2, (int) (sumZ / count));
            }
        }
    }

    @Override
    public void applyWorkingState(PhantasiaTrackedDummyWorld level,
                                  java.util.Set<net.minecraft.core.BlockPos> positions,
                                  java.util.Map<net.minecraft.core.BlockPos, net.phoenixvine.phantasia.utils.PhantasiaBlockInfo> blockMap,
                                  boolean working) {
        if (showBeam && beaconWorldPos == null) {
            for (java.util.Map.Entry<BlockPos, PhantasiaBlockInfo> e : blockMap.entrySet()) {
                if (e.getValue() != null && e.getValue().getBlockState().is(Blocks.BEACON)) {
                    beaconWorldPos = e.getKey();
                    break;
                }
            }
        }
        if (showConduit && conduitWorldPos == null) {
            for (java.util.Map.Entry<BlockPos, PhantasiaBlockInfo> e : blockMap.entrySet()) {
                if (e.getValue() != null && e.getValue().getBlockState().is(Blocks.CONDUIT)) {
                    conduitWorldPos = e.getKey();
                    break;
                }
            }
        }
        setMachineWorking(level, working);
    }

    @Override
    public void setMachineWorking(PhantasiaTrackedDummyWorld level, boolean working) {
        isWorking = working;
        applyBeamState(level);
        applyConduitState(level);

        if (mobType == null) return;

        level.clearSceneEntities();
        cachedMob = null;
        if (!working || mobWorldPos == null) return;

        var mob = mobType.create(level);
        if (mob == null) return;
        if (mob instanceof Mob m) {
            m.setNoAi(true);
            m.setSilent(true);
        }
        mob.setPos(mobWorldPos.getX() + 0.5, mobWorldPos.getY(), mobWorldPos.getZ() + 0.5);
        mob.xOld = mob.getX();
        mob.yOld = mob.getY();
        mob.zOld = mob.getZ();
        mob.yRotO = 0f;
        level.addSceneEntity(mob);
        cachedMob = mob;
    }

    @Override
    public void onSceneTick(PhantasiaTrackedDummyWorld level,
                            Map<BlockPos, BlockPos> localToWorld, int sceneTick) {
        if (showBeam) applyBeamState(level);
        if (showConduit) applyConduitState(level);

        if (cachedMob != null) {
            float yaw = (sceneTick % 160) * (360f / 160f);
            cachedMob.setYRot(yaw);
            cachedMob.yRotO = yaw;
        }
    }

    private void applyBeamState(PhantasiaTrackedDummyWorld level) {
        if (!showBeam) return;
        if (beaconWorldPos == null) {
            net.phoenixvine.phantasia.Phantasia.LOGGER
                    .warn("[Phantasia] applyBeamState: beaconWorldPos null (isWorking={})", isWorking);
            return;
        }
        var be = level.getBlockEntity(beaconWorldPos);
        if (!(be instanceof BeaconBlockEntity beacon)) {
            net.phoenixvine.phantasia.Phantasia.LOGGER.warn("[Phantasia] applyBeamState: got {} at {} (isWorking={})",
                    be, beaconWorldPos, isWorking);
            return;
        }
        List<BeaconBlockEntity.BeaconBeamSection> sections = beamSectionsMutable(beacon);
        if (sections == null) return;
        net.phoenixvine.phantasia.Phantasia.LOGGER.info(
                "[Phantasia] applyBeamState: isWorking={} sections.size()={} beSystem.id={}", isWorking,
                sections.size(), System.identityHashCode(beacon));
        if (isWorking) {
            if (sections.isEmpty()) sections.add(new BeaconBlockEntity.BeaconBeamSection(new float[] { 1f, 1f, 1f }));
        } else {
            sections.clear();
        }
        net.phoenixvine.phantasia.Phantasia.LOGGER.info("[Phantasia] applyBeamState: after => sections.size()={}",
                sections.size());
    }

    private void applyConduitState(PhantasiaTrackedDummyWorld level) {
        if (!showConduit) return;
        if (conduitWorldPos == null) {
            net.phoenixvine.phantasia.Phantasia.LOGGER
                    .warn("[Phantasia] applyConduitState: conduitWorldPos null (isWorking={})", isWorking);
            return;
        }
        var be = level.getBlockEntity(conduitWorldPos);
        if (be == null) {
            net.phoenixvine.phantasia.Phantasia.LOGGER.warn("[Phantasia] applyConduitState: no BE at {} (isWorking={})",
                    conduitWorldPos, isWorking);
            return;
        }

        boolean applied = false;
        for (Class<?> cls = be.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                if (f.getType() != boolean.class) continue;
                String name = f.getName();
                if (!name.equals("isActive") && !name.toLowerCase(java.util.Locale.ROOT).contains("active")) continue;
                try {
                    f.setAccessible(true);
                    f.set(be, isWorking);
                    applied = true;
                    break;
                } catch (Exception ignored) {}
            }
            if (applied) break;
        }
        if (!applied) {
            net.phoenixvine.phantasia.Phantasia.LOGGER.warn(
                    "[Phantasia] conduit: could not find isActive field on {} (isWorking={})", be.getClass().getName(),
                    isWorking);
        }
    }

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

            java.util.ArrayList<BeaconBlockEntity.BeaconBeamSection> fresh = new java.util.ArrayList<>();
            f.set(beacon, fresh);
            return fresh;
        } catch (Exception e) {
            net.phoenixvine.phantasia.Phantasia.LOGGER.warn("[Phantasia] beacon beam reflection failed: {}",
                    e.getMessage());
            return null;
        }
    }
}
