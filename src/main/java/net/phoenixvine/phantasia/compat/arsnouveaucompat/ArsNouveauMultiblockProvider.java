package net.phoenixvine.phantasia.compat.arsnouveaucompat;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockProvider;
import net.phoenixvine.phantasia.common.multisetup.PhantasiaMultiSetupRegistry;

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

/**
 * Exposes Ars Nouveau setups as {@link IPhantasiaMultiblockDefinition} entries.
 * Recipe-based setups (apparatus, imbuement) come from {@link PhantasiaMultiSetupRegistry}.
 * Static layout setups (turrets, sourcelinks, summoning) are registered directly here.
 */
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
        // Check static defs first
        for (ArsNouveauStaticDefinition def : staticDefs) {
            if (def.getId().toString().equals(machineId)) return Optional.of(def);
        }
        // Fall back to recipe-based setups
        return PhantasiaMultiSetupRegistry.resolve(machineId)
                .map(ArsNouveauSetupDefinition::new);
    }

    @Override
    public Optional<BlockInfo> resolveBlock(String id) {
        return Optional.empty();
    }

    @Override
    public List<IPhantasiaMultiblockDefinition> getAllDefinitions() {
        List<IPhantasiaMultiblockDefinition> all = new ArrayList<>();
        // Recipe-based setups (apparatus, imbuement)
        PhantasiaMultiSetupRegistry.getAllSetups().stream()
                .map(s -> (IPhantasiaMultiblockDefinition) new ArsNouveauSetupDefinition(s))
                .forEach(all::add);
        // Static layout setups
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
        // Check static defs
        for (ArsNouveauStaticDefinition def : staticDefs) {
            if (ItemStack.isSameItem(def.getIcon(), stack)) return Optional.of(def);
        }
        // Recipe-based setups
        return PhantasiaMultiSetupRegistry.getAllSetups().stream()
                .filter(s -> ItemStack.isSameItem(s.getIcon(), stack))
                .findFirst()
                .map(ArsNouveauSetupDefinition::new);
    }

    // ── static definition registration ────────────────────────────────────────

    private static List<ArsNouveauStaticDefinition> buildStaticDefs() {
        List<ArsNouveauStaticDefinition> list = new ArrayList<>();

        // ── Spell Turrets ──────────────────────────────────────────────────────
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

        // ── Wixie Cauldron ─────────────────────────────────────────────────────
        list.add(new ArsNouveauStaticDefinition(
                new ResourceLocation("ars_nouveau", "wixie_cauldron"),
                "Wixie Cauldron",
                () -> new ItemStack(BlockRegistry.WIXIE_CAULDRON.get()),
                ArsNouveauLayoutBuilder::wixieCauldronBase,
                ArsNouveauStaticScripts::wixieCauldron,
                sourceConsumerHandler()));

        // ── Bookwyrm ───────────────────────────────────────────────────────────
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

        // ── Summoning setups ───────────────────────────────────────────────────
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

        // ── Ritual Brazier ─────────────────────────────────────────────────────
        list.add(new ArsNouveauStaticDefinition(
                new ResourceLocation("ars_nouveau", "ritual_brazier"),
                "Ritual Brazier",
                () -> new ItemStack(BlockRegistry.RITUAL_BLOCK.get()),
                ArsNouveauLayoutBuilder::ritualBrazierBase,
                ArsNouveauStaticScripts::ritualBrazier,
                ritualBrazierTickHandler()).withShapeLoadHandler(ArsNouveauMultiblockProvider::activateBrazier));

        // ── Sourcelinks ────────────────────────────────────────────────────────
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

    /**
     * Every 40 ticks, triggers a recoil animation on the first BasicSpellTurretTile in the scene.
     * EnchantedTurretTile and TimerSpellTurretTile both extend BasicSpellTurretTile, so this
     * handler works for all three recoil-style turrets.
     */
    private static ArsNouveauStaticDefinition.SceneTickHandler recoilHandler() {
        return (level, localToWorld, sceneTick) -> {
            if (sceneTick % 40 != 0) return;
            for (BlockPos worldPos : localToWorld.values()) {
                BlockEntity be = level.getBlockEntity(worldPos);
                if (be instanceof BasicSpellTurretTile turret) {
                    turret.startAnimation(0); // sets playRecoil = true → GeckoLib "recoil" clip
                    break;
                }
            }
        };
    }

    /**
     * Each tick, increments the rotating turret's needed rotation and snaps the
     * interpolated fields so the renderer sees smooth continuous rotation.
     */
    private static ArsNouveauStaticDefinition.SceneTickHandler rotatingHandler() {
        return (level, localToWorld, sceneTick) -> {
            for (BlockPos worldPos : localToWorld.values()) {
                BlockEntity be = level.getBlockEntity(worldPos);
                if (be instanceof RotatingTurretTile turret) {
                    turret.neededRotationX += 2.0f;
                    // Drive clientNeededX and rotationX directly since scene ticks don't call BE.tick()
                    turret.clientNeededX = turret.neededRotationX;
                    turret.rotationX = turret.neededRotationX;
                    break;
                }
            }
        };
    }

    /**
     * Walks a single scene entity in a circle around the scene center.
     * {@code radius} is in blocks, {@code speed} is radians per tick (~0.02 = one lap in ~5 seconds).
     * xOld/zOld and yRotO are updated before each move so the renderer interpolates smoothly.
     */
    private static ArsNouveauStaticDefinition.SceneTickHandler mobWalkTickHandler(double radius, double speed) {
        return (level, localToWorld, sceneTick) -> {
            var entities = new java.util.ArrayList<>(level.getAllEntities());
            if (entities.isEmpty()) return;
            net.minecraft.world.entity.Entity entity = entities.get(0);

            // Compute the walk center from the first block in the world map
            // (spawnEntity placed the mob at worldCenter + 0.5, + 1.0 y)
            // We store center as the mob's spawn base, derived from localToWorld (2,1,2)
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

            // Save previous position for interpolation
            // Capture all interpolation anchors BEFORE making any change.
            entity.xOld = entity.getX();
            entity.yOld = entity.getY();
            entity.zOld = entity.getZ();
            entity.yRotO = entity.getYRot();
            if (entity instanceof net.minecraft.world.entity.LivingEntity living0) {
                living0.yHeadRotO = living0.getYHeadRot();
                living0.yBodyRotO = living0.yBodyRot;
            }

            entity.setPos(newX, groundY, newZ);

            // Face the direction of movement (tangent = angle + 90°)
            float yaw = (float) Math.toDegrees(angle + Math.PI / 2.0);
            entity.setYRot(yaw);
            if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                living.setYHeadRot(yaw);
                living.yBodyRot = yaw;
            }
        };
    }

    /**
     * Animates the bookwyrm entity shuttling back and forth between the Storage
     * Lectern (local 2,1,2) and the chest at (0,1,2), hovering slightly above each.
     * Period: 120 ticks — 20 idle at lectern, 40 travel, 20 idle at chest, 40 return.
     */
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

            double hoverY = 1.2; // hover height above block top
            double ax = wLectern.getX() + 0.5, az = wLectern.getZ() + 0.5;
            double bx = wChest.getX() + 0.5, bz = wChest.getZ() + 0.5;
            double groundY = wLectern.getY();

            // Phase within the 120-tick cycle
            int period = 120;
            int phase = (int) (sceneTick % period);
            // 0-19: idle at lectern; 20-59: travel to chest; 60-79: idle at chest; 80-119: return
            double t;
            double entityX, entityZ, yaw;
            if (phase < 20) {
                entityX = ax;
                entityZ = az;
                yaw = (float) Math.toDegrees(Math.atan2(bz - az, bx - ax));
            } else if (phase < 60) {
                t = (phase - 20) / 40.0;
                t = t * t * (3 - 2 * t); // smoothstep
                entityX = ax + (bx - ax) * t;
                entityZ = az + (bz - az) * t;
                yaw = (float) Math.toDegrees(Math.atan2(bz - az, bx - ax));
            } else if (phase < 80) {
                entityX = bx;
                entityZ = bz;
                yaw = (float) Math.toDegrees(Math.atan2(az - bz, ax - bx));
            } else {
                t = (phase - 80) / 40.0;
                t = t * t * (3 - 2 * t); // smoothstep
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

    /**
     * Drains source jars at a steady rate so the scene shows active Source consumption.
     * Resets jars to full every SOURCE_CYCLE_TICKS. Used by the Wixie Cauldron.
     */
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

    /**
     * Gradually fills Source Jars (as if a Sourcelink is producing into them), and
     * spawns a follow-projectile particle from the sourcelink toward each jar every
     * 100 ticks so the flow is visually clear.
     */
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
                    // Particle burst every 100 ticks from sourcelink → jar
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

    /**
     * Sets the ritual brazier tile to decorative (continuous glow particles)
     * with a warm golden Sun colour, and ensures the LIT blockstate is applied.
     */
    private static void activateBrazier(net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld level,
                                        Map<BlockPos, BlockPos> localToWorld) {
        for (BlockPos worldPos : localToWorld.values()) {
            BlockEntity be = level.getBlockEntity(worldPos);
            if (be instanceof RitualBrazierTile brazier) {
                if (brazier.getLevel() == null) brazier.setLevel(level);
                brazier.isDecorative = true;
                brazier.color = new ParticleColor(255, 220, 30); // warm sun-gold
                break;
            }
        }
    }

    /**
     * Each tick, calls RitualBrazierTile.tick() so the decorative particle effect runs.
     * The tile's tick() emits glow particles when isDecorative && level.isClientSide.
     */
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

    /**
     * Places a creature entity at the scene center so the summoning setup
     * shows the actual mob that would be summoned.
     * The entity is placed on top of the drygmy/whirlisprig/starbuncle block (CY+1).
     */
    private static void spawnEntity(net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld level,
                                    Map<BlockPos, BlockPos> localToWorld,
                                    net.minecraft.world.entity.Entity entity) {
        if (entity == null) return;
        // Find the world position of local (2,1,2) — the creature spawn point
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

    // ── shared source jar helper ───────────────────────────────────────────────

    private static void setJarSource(SourceJarTile jar,
                                     net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld level,
                                     int amount) {
        if (jar.getLevel() == null) jar.setLevel(level);
        jar.setSource(amount);
        jar.setChanged();
    }
}
