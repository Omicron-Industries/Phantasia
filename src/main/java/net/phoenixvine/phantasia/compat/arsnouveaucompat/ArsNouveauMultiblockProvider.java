package net.phoenixvine.phantasia.compat.arsnouveaucompat;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockProvider;
import net.phoenixvine.phantasia.common.multisetup.PhantasiaMultiSetupRegistry;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import com.hollingsworth.arsnouveau.api.registry.RitualRegistry;
import com.hollingsworth.arsnouveau.client.particle.ParticleColor;
import com.hollingsworth.arsnouveau.client.particle.ParticleUtil;
import com.hollingsworth.arsnouveau.common.block.tile.BasicSpellTurretTile;
import com.hollingsworth.arsnouveau.common.block.tile.RitualBrazierTile;
import com.hollingsworth.arsnouveau.common.block.tile.RotatingTurretTile;
import com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile;
import com.hollingsworth.arsnouveau.common.block.tile.SourcelinkTile;
import com.hollingsworth.arsnouveau.setup.registry.BlockRegistry;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import com.hollingsworth.arsnouveau.setup.registry.ModEntities;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ArsNouveauMultiblockProvider implements IPhantasiaMultiblockProvider {

    private final List<ArsNouveauStaticDefinition> staticDefs;

    public ArsNouveauMultiblockProvider() {
        staticDefs = buildStaticDefs();
    }

    @Override
    public String getModId() {
        return "ars_nouveau";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Optional<IPhantasiaMultiblockDefinition> resolve(String machineId) {
        for (ArsNouveauStaticDefinition def : staticDefs) {
            if (def.getId().toString().equals(machineId)) return Optional.of(def);
        }

        return PhantasiaMultiSetupRegistry.resolve(machineId)
                .map(ArsNouveauSetupDefinition::new);
    }

    @Override
    public Optional<PhantasiaBlockInfo> resolveBlock(String id) {
        return Optional.empty();
    }

    @Override
    public List<IPhantasiaMultiblockDefinition> getAllDefinitions() {
        List<IPhantasiaMultiblockDefinition> all = new ArrayList<>();

        PhantasiaMultiSetupRegistry.getAllSetups().stream()
                .map(s -> (IPhantasiaMultiblockDefinition) new ArsNouveauSetupDefinition(s))
                .forEach(all::add);

        all.addAll(staticDefs);
        return all;
    }

    @Override
    public boolean isControllerBlock(BlockState state) {
        return false;
    }

    @Override
    public boolean isPartBlock(BlockState state) {
        return false;
    }

    @Override
    public Optional<IPhantasiaMultiblockDefinition> resolveFromItem(ItemStack stack) {
        for (ArsNouveauStaticDefinition def : staticDefs) {
            if (ItemStack.isSameItem(def.getIcon(), stack)) return Optional.of(def);
        }

        return PhantasiaMultiSetupRegistry.getAllSetups().stream()
                .filter(s -> ItemStack.isSameItem(s.getIcon(), stack))
                .findFirst()
                .map(ArsNouveauSetupDefinition::new);
    }

    private static List<ArsNouveauStaticDefinition> buildStaticDefs() {
        List<ArsNouveauStaticDefinition> list = new ArrayList<>();

        list.add(new ArsNouveauStaticDefinition(
                new ResourceLocation("ars_nouveau", "basic_spell_turret"),
                "Basic Spell Turret",
                () -> new ItemStack(BlockRegistry.BASIC_SPELL_TURRET.get()),
                () -> ArsNouveauLayoutBuilder.spellTurretBase(BlockRegistry.BASIC_SPELL_TURRET.get()),
                ArsNouveauStaticScripts::basicSpellTurret,
                recoilHandler()));
        list.add(new ArsNouveauStaticDefinition(
                new ResourceLocation("ars_nouveau", "enchanted_spell_turret"),
                "Enchanted Spell Turret",
                () -> new ItemStack(BlockRegistry.ENCHANTED_SPELL_TURRET.get()),
                () -> ArsNouveauLayoutBuilder.spellTurretBase(BlockRegistry.ENCHANTED_SPELL_TURRET.get()),
                ArsNouveauStaticScripts::enchantedSpellTurret,
                recoilHandler()));
        list.add(new ArsNouveauStaticDefinition(
                new ResourceLocation("ars_nouveau", "timer_spell_turret"),
                "Timer Spell Turret",
                () -> new ItemStack(BlockRegistry.TIMER_SPELL_TURRET.get()),
                () -> ArsNouveauLayoutBuilder.spellTurretBase(BlockRegistry.TIMER_SPELL_TURRET.get()),
                ArsNouveauStaticScripts::timerSpellTurret,
                recoilHandler()));
        list.add(new ArsNouveauStaticDefinition(
                new ResourceLocation("ars_nouveau", "rotating_spell_turret"),
                "Rotating Spell Turret",
                () -> new ItemStack(BlockRegistry.ROTATING_TURRET.get()),
                () -> ArsNouveauLayoutBuilder.spellTurretBase(BlockRegistry.ROTATING_TURRET.get()),
                ArsNouveauStaticScripts::rotatingSpellTurret,
                rotatingHandler()));

        list.add(new ArsNouveauStaticDefinition(
                new ResourceLocation("ars_nouveau", "wixie_cauldron"),
                "Wixie Cauldron",
                () -> new ItemStack(BlockRegistry.WIXIE_CAULDRON.get()),
                ArsNouveauLayoutBuilder::wixieCauldronBase,
                ArsNouveauStaticScripts::wixieCauldron,
                sourceConsumerHandler()));

        list.add(new ArsNouveauStaticDefinition(
                new ResourceLocation("ars_nouveau", "bookwyrm"),
                "Bookwyrm",
                () -> new ItemStack(ItemsRegistry.BOOKWYRM_CHARM.get()),
                ArsNouveauLayoutBuilder::bookwyrmBase,
                ArsNouveauStaticScripts::bookwyrm,
                bookwyrmShuttleHandler())
                .withShapeLoadHandler(
                        (level, l2w) -> spawnEntity(level, l2w,
                                ModEntities.ENTITY_BOOKWYRM_TYPE.get().create(level))));

        list.add(new ArsNouveauStaticDefinition(
                new ResourceLocation("ars_nouveau", "drygmy"),
                "Drygmy",
                () -> new ItemStack(ItemsRegistry.DRYGMY_CHARM.get()),
                ArsNouveauLayoutBuilder::drygmyBase,
                ArsNouveauStaticScripts::drygmy,
                mobWalkTickHandler(1.2, 0.022))
                .withShapeLoadHandler(
                        (level, l2w) -> spawnEntity(level, l2w,
                                ModEntities.ENTITY_DRYGMY.get().create(level))));
        list.add(new ArsNouveauStaticDefinition(
                new ResourceLocation("ars_nouveau", "whirlisprig"),
                "Whirlisprig",
                () -> new ItemStack(ItemsRegistry.WHIRLISPRIG_CHARM.get()),
                ArsNouveauLayoutBuilder::whirlisprigBase,
                ArsNouveauStaticScripts::whirlisprig,
                mobWalkTickHandler(1.4, 0.030))
                .withShapeLoadHandler(
                        (level, l2w) -> spawnEntity(level, l2w,
                                ModEntities.WHIRLISPRIG_TYPE.get().create(level))));
        list.add(new ArsNouveauStaticDefinition(
                new ResourceLocation("ars_nouveau", "starbuncle"),
                "Starbuncle",
                () -> new ItemStack(ItemsRegistry.STARBUNCLE_CHARM.get()),
                ArsNouveauLayoutBuilder::starbuncleBase,
                ArsNouveauStaticScripts::starbuncle,
                mobWalkTickHandler(1.1, 0.025))
                .withShapeLoadHandler(
                        (level, l2w) -> spawnEntity(level, l2w,
                                ModEntities.STARBUNCLE_TYPE.get().create(level))));

        list.add(new ArsNouveauStaticDefinition(
                new ResourceLocation("ars_nouveau", "ritual_brazier"),
                "Ritual Brazier",
                () -> new ItemStack(BlockRegistry.RITUAL_BLOCK.get()),
                ArsNouveauLayoutBuilder::ritualBrazierBase,
                ArsNouveauStaticScripts::ritualBrazier,
                ritualBrazierTickHandler())
                .withScriptAwareShapeLoadHandler(ArsNouveauMultiblockProvider::activateBrazierWithScript));

        ArsNouveauStaticDefinition.SceneTickHandler sourcelinkFill = sourcelinkFillHandler();
        list.add(new ArsNouveauStaticDefinition(
                new ResourceLocation("ars_nouveau", "agronomic_sourcelink"),
                "Agronomic Sourcelink",
                () -> new ItemStack(BlockRegistry.AGRONOMIC_SOURCELINK.get()),
                () -> ArsNouveauLayoutBuilder.sourcelinkBase(BlockRegistry.AGRONOMIC_SOURCELINK.get()),
                ArsNouveauStaticScripts::agronomicSourcelink,
                sourcelinkFill));
        list.add(new ArsNouveauStaticDefinition(
                new ResourceLocation("ars_nouveau", "volcanic_sourcelink"),
                "Volcanic Sourcelink",
                () -> new ItemStack(BlockRegistry.VOLCANIC_BLOCK.get()),
                () -> ArsNouveauLayoutBuilder.sourcelinkBase(BlockRegistry.VOLCANIC_BLOCK.get()),
                ArsNouveauStaticScripts::volcanicSourcelink,
                sourcelinkFill));
        list.add(new ArsNouveauStaticDefinition(
                new ResourceLocation("ars_nouveau", "vitalic_sourcelink"),
                "Vitalic Sourcelink",
                () -> new ItemStack(BlockRegistry.VITALIC_BLOCK.get()),
                () -> ArsNouveauLayoutBuilder.sourcelinkBase(BlockRegistry.VITALIC_BLOCK.get()),
                ArsNouveauStaticScripts::vitalicSourcelink,
                sourcelinkFill));
        list.add(new ArsNouveauStaticDefinition(
                new ResourceLocation("ars_nouveau", "mycelial_sourcelink"),
                "Mycelial Sourcelink",
                () -> new ItemStack(BlockRegistry.MYCELIAL_BLOCK.get()),
                () -> ArsNouveauLayoutBuilder.sourcelinkBase(BlockRegistry.MYCELIAL_BLOCK.get()),
                ArsNouveauStaticScripts::mycelialSourcelink,
                sourcelinkFill));
        list.add(new ArsNouveauStaticDefinition(
                new ResourceLocation("ars_nouveau", "alchemical_sourcelink"),
                "Alchemical Sourcelink",
                () -> new ItemStack(BlockRegistry.ALCHEMICAL_BLOCK.get()),
                () -> ArsNouveauLayoutBuilder.sourcelinkBase(BlockRegistry.ALCHEMICAL_BLOCK.get()),
                ArsNouveauStaticScripts::alchemicalSourcelink,
                sourcelinkFill));

        return list;
    }

    private static ArsNouveauStaticDefinition.SceneTickHandler recoilHandler() {
        return (level, localToWorld, sceneTick) -> {
            if (sceneTick % 40 != 0) return;
            for (BlockPos worldPos : localToWorld.values()) {
                BlockEntity be = level.getBlockEntity(worldPos);
                if (be instanceof BasicSpellTurretTile turret) {
                    turret.startAnimation(0);
                    break;
                }
            }
        };
    }

    private static ArsNouveauStaticDefinition.SceneTickHandler rotatingHandler() {
        return (level, localToWorld, sceneTick) -> {
            for (BlockPos worldPos : localToWorld.values()) {
                BlockEntity be = level.getBlockEntity(worldPos);
                if (be instanceof RotatingTurretTile turret) {
                    turret.neededRotationX += 2.0f;

                    turret.clientNeededX = turret.neededRotationX;
                    turret.rotationX = turret.neededRotationX;
                    break;
                }
            }
        };
    }

    private static ArsNouveauStaticDefinition.SceneTickHandler mobWalkTickHandler(double radius, double speed) {
        return (level, localToWorld, sceneTick) -> {
            var entities = new java.util.ArrayList<>(level.getAllEntities());
            if (entities.isEmpty()) return;
            net.minecraft.world.entity.Entity entity = entities.get(0);

            BlockPos centerLocal = new BlockPos(2, 1, 2);
            BlockPos worldCenter = localToWorld.get(centerLocal);
            if (worldCenter == null) {
                if (localToWorld.isEmpty()) return;
                worldCenter = localToWorld.values().iterator().next();
            }
            double cx = worldCenter.getX() + 0.5;
            double cz = worldCenter.getZ() + 0.5;
            double groundY = worldCenter.getY() + 1.0;

            double angle = sceneTick * speed;
            double newX = cx + radius * Math.cos(angle);
            double newZ = cz + radius * Math.sin(angle);

            entity.xOld = entity.getX();
            entity.yOld = entity.getY();
            entity.zOld = entity.getZ();
            entity.yRotO = entity.getYRot();
            if (entity instanceof net.minecraft.world.entity.LivingEntity living0) {
                living0.yHeadRotO = living0.getYHeadRot();
                living0.yBodyRotO = living0.yBodyRot;
            }

            entity.setPos(newX, groundY, newZ);

            float yaw = (float) Math.toDegrees(angle + Math.PI / 2.0);
            entity.setYRot(yaw);
            if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                living.setYHeadRot(yaw);
                living.yBodyRot = yaw;
            }
        };
    }

    private static ArsNouveauStaticDefinition.SceneTickHandler bookwyrmShuttleHandler() {
        return (level, localToWorld, sceneTick) -> {
            var entities = new java.util.ArrayList<>(level.getAllEntities());
            if (entities.isEmpty()) return;
            net.minecraft.world.entity.Entity entity = entities.get(0);

            BlockPos lecternLocal = new BlockPos(2, 1, 2);
            BlockPos chestLocal = new BlockPos(0, 1, 2);
            BlockPos wLectern = localToWorld.get(lecternLocal);
            BlockPos wChest = localToWorld.get(chestLocal);
            if (wLectern == null || wChest == null) return;

            double hoverY = 1.2;
            double ax = wLectern.getX() + 0.5, az = wLectern.getZ() + 0.5;
            double bx = wChest.getX() + 0.5, bz = wChest.getZ() + 0.5;
            double groundY = wLectern.getY();

            int period = 120;
            int phase = (int) (sceneTick % period);

            double t;
            double entityX, entityZ, yaw;
            if (phase < 20) {
                entityX = ax;
                entityZ = az;
                yaw = (float) Math.toDegrees(Math.atan2(bz - az, bx - ax));
            } else if (phase < 60) {
                t = (phase - 20) / 40.0;
                t = t * t * (3 - 2 * t);
                entityX = ax + (bx - ax) * t;
                entityZ = az + (bz - az) * t;
                yaw = (float) Math.toDegrees(Math.atan2(bz - az, bx - ax));
            } else if (phase < 80) {
                entityX = bx;
                entityZ = bz;
                yaw = (float) Math.toDegrees(Math.atan2(az - bz, ax - bx));
            } else {
                t = (phase - 80) / 40.0;
                t = t * t * (3 - 2 * t);
                entityX = bx + (ax - bx) * t;
                entityZ = bz + (az - bz) * t;
                yaw = (float) Math.toDegrees(Math.atan2(az - bz, ax - bx));
            }

            entity.xOld = entity.getX();
            entity.yOld = entity.getY();
            entity.zOld = entity.getZ();
            entity.yRotO = entity.getYRot();
            if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                living.yHeadRotO = living.getYHeadRot();
                living.yBodyRotO = living.yBodyRot;
            }

            entity.setPos(entityX, groundY + hoverY, entityZ);
            entity.setYRot((float) yaw);
            if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                living.setYHeadRot((float) yaw);
                living.yBodyRot = (float) yaw;
            }
        };
    }

    private static final int SOURCE_CYCLE_TICKS = 120;
    private static final int SOURCE_DRAIN_RATE = 8;
    private static final int SOURCE_JAR_MAX = 10_000;

    private static ArsNouveauStaticDefinition.SceneTickHandler sourceConsumerHandler() {
        return (level, localToWorld, sceneTick) -> {
            int phase = sceneTick % SOURCE_CYCLE_TICKS;
            for (BlockPos worldPos : localToWorld.values()) {
                BlockEntity be = level.getBlockEntity(worldPos);
                if (be == null) continue;
                if (be.getLevel() == null) be.setLevel(level);
                if (be instanceof SourceJarTile jar) {
                    if (phase == 0) {
                        setJarSource(jar, level, SOURCE_JAR_MAX);
                    } else {
                        setJarSource(jar, level, Math.max(0, jar.getSource() - SOURCE_DRAIN_RATE));
                    }
                }
            }
        };
    }

    private static ArsNouveauStaticDefinition.SceneTickHandler sourcelinkFillHandler() {
        return (level, localToWorld, sceneTick) -> {
            int phase = sceneTick % SOURCE_CYCLE_TICKS;
            BlockPos sourcelinkPos = null;
            SourcelinkTile sourcelinkTile = null;

            for (BlockPos worldPos : localToWorld.values()) {
                BlockEntity be = level.getBlockEntity(worldPos);
                if (be == null) continue;
                if (be.getLevel() == null) be.setLevel(level);
                if (be instanceof SourcelinkTile sl) {
                    sourcelinkPos = worldPos;
                    sourcelinkTile = sl;
                    break;
                }
            }

            for (BlockPos worldPos : localToWorld.values()) {
                BlockEntity be = level.getBlockEntity(worldPos);
                if (be == null) continue;
                if (be.getLevel() == null) be.setLevel(level);
                if (be instanceof SourceJarTile jar) {
                    if (phase == 0) {
                        setJarSource(jar, level, 0);
                    } else {
                        setJarSource(jar, level, Math.min(SOURCE_JAR_MAX, jar.getSource() + SOURCE_DRAIN_RATE));
                    }

                    if (sourcelinkPos != null && sourcelinkTile != null && sceneTick % 100 == 0) {
                        try {
                            ParticleUtil.spawnFollowProjectile(level, sourcelinkPos, worldPos,
                                    sourcelinkTile.getColor());
                        } catch (Exception ignored) {}
                    }
                }
            }
        };
    }

    private static void activateBrazier(net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld level,
                                        Map<BlockPos, BlockPos> localToWorld) {
        for (BlockPos worldPos : localToWorld.values()) {
            BlockEntity be = level.getBlockEntity(worldPos);
            if (be instanceof RitualBrazierTile brazier) {
                if (brazier.getLevel() == null) brazier.setLevel(level);
                brazier.isDecorative = true;
                brazier.color = new ParticleColor(255, 220, 30);
                break;
            }
        }
    }

    private static void activateBrazierWithScript(PhantasiaTrackedDummyWorld level,
                                                  Map<BlockPos, BlockPos> localToWorld,
                                                  @javax.annotation.Nullable PhantasiaScriptData script) {
        activateBrazier(level, localToWorld);
        if (script != null && script.getRecipeId() != null) {
            placeRitualTablet(level, localToWorld, script.getRecipeId());
        }
    }

    private static void placeRitualTablet(PhantasiaTrackedDummyWorld level,
                                          Map<BlockPos, BlockPos> localToWorld,
                                          String recipeId) {
        ResourceLocation ritualId = new ResourceLocation(recipeId);
        if (!RitualRegistry.getRitualMap().containsKey(ritualId)) return;
        for (BlockPos worldPos : localToWorld.values()) {
            BlockEntity be = level.getBlockEntity(worldPos);
            if (be instanceof RitualBrazierTile brazier) {
                if (brazier.getLevel() == null) brazier.setLevel(level);
                brazier.setRitual(ritualId);
                break;
            }
        }
    }

    private static ArsNouveauStaticDefinition.SceneTickHandler ritualBrazierTickHandler() {
        return (level, localToWorld, sceneTick) -> {
            for (BlockPos worldPos : localToWorld.values()) {
                BlockEntity be = level.getBlockEntity(worldPos);
                if (be instanceof RitualBrazierTile brazier) {
                    if (brazier.getLevel() == null) brazier.setLevel(level);
                    try {
                        brazier.tick();
                    } catch (Exception ignored) {}
                    break;
                }
            }
        };
    }

    private static void spawnEntity(net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld level,
                                    Map<BlockPos, BlockPos> localToWorld,
                                    net.minecraft.world.entity.Entity entity) {
        if (entity == null) return;

        BlockPos centerLocal = new BlockPos(2, 1, 2);
        BlockPos worldCenter = localToWorld.get(centerLocal);
        if (worldCenter == null && !localToWorld.isEmpty()) {
            worldCenter = localToWorld.values().iterator().next();
        }
        if (worldCenter == null) return;
        entity.setPos(worldCenter.getX() + 0.5, worldCenter.getY() + 1.0, worldCenter.getZ() + 0.5);
        entity.xOld = entity.getX();
        entity.yOld = entity.getY();
        entity.zOld = entity.getZ();
        level.addSceneEntity(entity);
    }

    private static void setJarSource(SourceJarTile jar,
                                     net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld level,
                                     int amount) {
        if (jar.getLevel() == null) jar.setLevel(level);
        jar.setSource(amount);
        jar.setChanged();
    }
}
