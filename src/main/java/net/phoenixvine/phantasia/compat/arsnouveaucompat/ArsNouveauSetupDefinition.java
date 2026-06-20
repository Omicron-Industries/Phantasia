package net.phoenixvine.phantasia.compat.arsnouveaucompat;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.common.multisetup.IPhantasiaMultiSetup;
import net.phoenixvine.phantasia.common.multisetup.IPhantasiaSetupRecipe;

import com.hollingsworth.arsnouveau.common.block.tile.EnchantingApparatusTile;
import com.hollingsworth.arsnouveau.common.block.tile.ImbuementTile;
import com.hollingsworth.arsnouveau.common.block.tile.SingleItemTile;
import com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile;
import com.hollingsworth.arsnouveau.setup.registry.SoundRegistry;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

/**
 * Bridges an {@link IPhantasiaMultiSetup} into the multiblock definition/script
 * system so Phantasia can run scene scripts against AN setups.
 */
public class ArsNouveauSetupDefinition implements IPhantasiaMultiblockDefinition {

    // Source jar drain cycle
    private static final int SOURCE_CYCLE_TICKS = 120;
    private static final int SOURCE_DRAIN_RATE = 8;
    private static final int SOURCE_JAR_MAX = 10_000;

    // Sound repeat interval (~4 s)
    private static final int SOUND_REPEAT_TICKS = 80;

    // Crafting animation durations (ticks), matching AN's internal values
    private static final int APPARATUS_CRAFT_TICKS = EnchantingApparatusTile.craftingLength; // 210
    private static final int IMBUEMENT_CRAFT_TICKS = 100; // ImbuementTile.craftTicks initial value

    private final IPhantasiaMultiSetup setup;
    private final List<IPhantasiaMultiblockShape> shapes;

    // Hold state — reset in onShapeLoaded so each scene open starts fresh
    private int holdStartSceneTick = -1;

    public ArsNouveauSetupDefinition(IPhantasiaMultiSetup setup) {
        this.setup = setup;
        IPhantasiaMultiblockShape shape = setup::getBaseLayout;
        this.shapes = List.of(shape);
    }

    @Override
    public ResourceLocation getId() {
        return setup.getId();
    }

    @Override
    public String getDisplayName() {
        return setup.getDisplayName();
    }

    @Override
    public ItemStack getIcon() {
        return setup.getIcon();
    }

    @Override
    public List<IPhantasiaMultiblockShape> getMatchingShapes() {
        return shapes;
    }

    @Override
    public List<IPhantasiaMultiblockShape> getAllShapes() {
        return shapes;
    }

    // ── shape loaded ───────────────────────────────────────────────────────────

    @Override
    public void onShapeLoaded(PhantasiaTrackedDummyWorld level, BlockPos origin,
                              Map<BlockPos, BlockInfo> blockMap,
                              Map<BlockPos, BlockPos> localToWorld,
                              @Nullable net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData script) {
        List<IPhantasiaSetupRecipe> recipes = setup.getRecipes();
        if (recipes.isEmpty()) return;

        // If the script specifies a recipe, use it directly. Fall back to the
        // hardcoded default so scenes without a recipeId still look reasonable.
        String recipeId = script != null ? script.getRecipeId() : null;
        IPhantasiaSetupRecipe recipe;
        if (recipeId != null) {
            recipe = recipes.stream()
                    .filter(r -> r.getId().toString().equals(recipeId))
                    .findFirst()
                    .orElse(null);
            if (recipe == null) return; // recipeId set but not found — skip item setup
        } else {
            String preferredPath = switch (setup.getId().getPath()) {
                case "enchanting_apparatus" -> "magebloom_seed";
                case "imbuement_chamber" -> "source_gem";
                default -> null;
            };
            recipe = preferredRecipe(recipes, preferredPath);
        }
        Map<BlockPos, ItemStack> placements = recipe.getItemPlacements();
        if (placements == null || placements.isEmpty()) return;

        holdStartSceneTick = -1;

        // Ensure every AN tile has its level set before anything can trigger a tick.
        // TrackedDummyWorld does not call setLevel() when registering block entities,
        // and EnchantingApparatusTile.tick() crashes immediately if level is null.
        for (BlockPos worldPos : localToWorld.values()) {
            BlockEntity be = level.getBlockEntity(worldPos);
            if (be != null && be.getLevel() == null) be.setLevel(level);
        }

        // Set items — apparatus/pedestals use SingleItemTile;
        // ImbuementTile extends AbstractSourceMachine directly so needs its own path.
        for (Map.Entry<BlockPos, ItemStack> entry : placements.entrySet()) {
            BlockPos worldPos = localToWorld.get(entry.getKey());
            if (worldPos == null) continue;
            BlockEntity be = level.getBlockEntity(worldPos);
            if (be instanceof ImbuementTile tile) {
                tile.stack = entry.getValue().copy();
                tile.updateBlock();
            } else if (be instanceof SingleItemTile tile) {
                tile.setStack(entry.getValue().copy());
            }
        }

        // Fill source jars and activate crafting animations
        for (BlockPos worldPos : localToWorld.values()) {
            BlockEntity be = level.getBlockEntity(worldPos);
            if (be instanceof EnchantingApparatusTile apparatus) {
                apparatus.isCrafting = true;
            } else if (be instanceof ImbuementTile tile) {
                tile.draining = true;
                tile.updateBlock();
            } else if (be instanceof SourceJarTile jar) {
                setJarSource(jar, level, SOURCE_JAR_MAX);
            }
        }
    }

    // ── scene tick ─────────────────────────────────────────────────────────────

    @Override
    public void onSceneTick(PhantasiaTrackedDummyWorld level,
                            Map<BlockPos, BlockPos> localToWorld,
                            int sceneTick) {
        // Drain source jars during the drain phase, then snap back to full
        int phase = sceneTick % SOURCE_CYCLE_TICKS;
        for (BlockPos worldPos : localToWorld.values()) {
            BlockEntity be = level.getBlockEntity(worldPos);
            if (be == null) continue;
            // Defensively ensure level is set — TrackedDummyWorld skips setLevel()
            if (be.getLevel() == null) be.setLevel(level);
            if (be instanceof SourceJarTile jar) {
                if (phase == 0) {
                    setJarSource(jar, level, SOURCE_JAR_MAX);
                } else {
                    setJarSource(jar, level, Math.max(0, jar.getSource() - SOURCE_DRAIN_RATE));
                }
            } else if (be instanceof EnchantingApparatusTile apparatus) {
                apparatus.isCrafting = true;
            } else if (be instanceof ImbuementTile tile) {
                tile.draining = true;
            }
        }

        // Play the apparatus channel sound periodically
        if (sceneTick % SOUND_REPEAT_TICKS == 0) {
            try {
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundRegistry.APPARATUS_CHANNEL.get(), 0.6f));
            } catch (Exception ignored) {}
        }
    }

    // ── hold ───────────────────────────────────────────────────────────────────

    @Override
    public boolean shouldHoldStep(PhantasiaTrackedDummyWorld level,
                                  Map<BlockPos, BlockPos> localToWorld,
                                  String holdId,
                                  int sceneTick) {
        if (!holdId.startsWith("an_crafting")) return false;

        if (holdStartSceneTick < 0) {
            // First call — record the start tick. The crafting flags are already
            // live from onSceneTick, so the animation is already running.
            holdStartSceneTick = sceneTick;
        }

        int duration = holdId.equals("an_crafting_apparatus") ? APPARATUS_CRAFT_TICKS : IMBUEMENT_CRAFT_TICKS;

        boolean done = (sceneTick - holdStartSceneTick) >= duration;
        if (done) holdStartSceneTick = -1; // reset so the hold can be re-used if script loops
        return !done;
    }

    // ── source jar helpers ─────────────────────────────────────────────────────

    private static void setJarSource(SourceJarTile jar, PhantasiaTrackedDummyWorld level, int amount) {
        if (jar.getLevel() == null) jar.setLevel(level);
        jar.setSource(amount); // internally calls updateBlock()
        jar.setChanged();
    }

    // ── recipe selection ───────────────────────────────────────────────────────

    private static IPhantasiaSetupRecipe preferredRecipe(List<IPhantasiaSetupRecipe> recipes,
                                                         @Nullable String outputPath) {
        if (outputPath == null) return recipes.get(0);
        Optional<IPhantasiaSetupRecipe> found = recipes.stream().filter(r -> {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(r.getOutput().getItem());
            return key != null && key.getPath().equals(outputPath);
        }).findFirst();
        return found.orElseGet(() -> recipes.get(0));
    }

    // ── builtin scripts ────────────────────────────────────────────────────────

    @Nullable
    @Override
    public PhantasiaScriptData getDefaultScriptData() {
        String id = setup.getId().toString();
        if (id.equals("ars_nouveau:enchanting_apparatus")) return buildApparatusScript();
        if (id.equals("ars_nouveau:imbuement_chamber")) return buildImbuementScript();
        return null;
    }

    private static PhantasiaScriptData buildApparatusScript() {
        // Layout: Arcane Core at (2,1,2); apparatus at (2,2,2); cardinal pedestals at y=1;
        // diagonal pedestals at corners y=1; stone floor at y=0.
        // Items are placed on all tiles in onShapeLoaded — revealing a pedestal shows its item.
        return PhantasiaScriptData.fromJson(
                """
                        {
                          "machine": "ars_nouveau:enchanting_apparatus",
                          "startCamera": { "yaw": -135.0, "pitch": -25.0, "zoom": 3.0 },
                          "steps": [
                            {
                              "tick": 0,
                              "caption": "The Enchanting Apparatus crafts magical items using Source. It requires an Arcane Core block directly underneath.",
                              "show": "pos",
                              "positions": [[2,1,2],[2,2,2]],
                              "camera": { "yaw": -135.0, "pitch": -25.0, "zoom": 3.0, "lerpType": "SNAP", "lerpTicks": 0 }
                            },
                            {
                              "tick": 50,
                              "caption": "Add Arcane Pedestals at the cardinal positions and place an ingredient on each.",
                              "show": "pos",
                              "positions": [[2,1,2],[2,2,2],[0,1,2]],
                              "camera": { "yaw": -135.0, "pitch": -35.0, "zoom": 3.5, "lerpType": "EASE", "lerpTicks": 12 }
                            },
                            {
                              "tick": 80,
                              "show": "pos",
                              "positions": [[2,1,2],[2,2,2],[0,1,2],[4,1,2]],
                              "camera": { "yaw": -120.0, "pitch": -38.0, "zoom": 3.8, "lerpType": "EASE", "lerpTicks": 10 }
                            },
                            {
                              "tick": 110,
                              "show": "pos",
                              "positions": [[2,1,2],[2,2,2],[0,1,2],[4,1,2],[2,1,0]],
                              "camera": { "yaw": -140.0, "pitch": -40.0, "zoom": 4.0, "lerpType": "EASE", "lerpTicks": 10 }
                            },
                            {
                              "tick": 140,
                              "caption": "For recipes that need more than 4 ingredients, add pedestals at the diagonal corners — up to 8 total.",
                              "show": "pos",
                              "positions": [[2,1,2],[2,2,2],[0,1,2],[4,1,2],[2,1,0],[2,1,4]],
                              "camera": { "yaw": -150.0, "pitch": -42.0, "zoom": 4.2, "lerpType": "EASE", "lerpTicks": 10 }
                            },
                            {
                              "tick": 165,
                              "show": "pos",
                              "positions": [[2,1,2],[2,2,2],[0,1,2],[4,1,2],[2,1,0],[2,1,4],[0,1,0]],
                              "camera": { "yaw": -155.0, "pitch": -44.0, "zoom": 4.4, "lerpType": "EASE", "lerpTicks": 8 }
                            },
                            {
                              "tick": 185,
                              "show": "pos",
                              "positions": [[2,1,2],[2,2,2],[0,1,2],[4,1,2],[2,1,0],[2,1,4],[0,1,0],[0,1,4]],
                              "camera": { "yaw": -160.0, "pitch": -46.0, "zoom": 4.6, "lerpType": "EASE", "lerpTicks": 8 }
                            },
                            {
                              "tick": 205,
                              "show": "pos",
                              "positions": [[2,1,2],[2,2,2],[0,1,2],[4,1,2],[2,1,0],[2,1,4],[0,1,0],[0,1,4],[4,1,0]],
                              "camera": { "yaw": -162.0, "pitch": -48.0, "zoom": 4.7, "lerpType": "EASE", "lerpTicks": 8 }
                            },
                            {
                              "tick": 225,
                              "show": "pos",
                              "positions": [[2,1,2],[2,2,2],[0,1,2],[4,1,2],[2,1,0],[2,1,4],[0,1,0],[0,1,4],[4,1,0],[4,1,4]],
                              "camera": { "yaw": -165.0, "pitch": -50.0, "zoom": 4.8, "lerpType": "EASE", "lerpTicks": 8 }
                            },
                            {
                              "tick": 270,
                              "caption": "Place the reagent in the Apparatus, then right-click to begin. Source is consumed from nearby Source Jars.",
                              "show": "all",
                              "camera": { "yaw": -145.0, "pitch": -48.0, "zoom": 5.0, "lerpType": "EASE", "lerpTicks": 20 }
                            },
                            {
                              "tick": 330,
                              "caption": "Crafting in progress — the Apparatus draws Source and the pedestals glow as the recipe runs.",
                              "show": "all",
                              "hold": "an_crafting_apparatus",
                              "camera": { "yaw": -145.0, "pitch": -48.0, "zoom": 5.0, "lerpType": "SNAP", "lerpTicks": 0 }
                            },
                            {
                              "tick": 331,
                              "caption": "Recipe complete! The reagent is consumed and the output appears in the Apparatus.",
                              "show": "all",
                              "camera": { "yaw": -135.0, "pitch": -30.0, "zoom": 3.5, "lerpType": "EASE", "lerpTicks": 20 }
                            }
                          ],
                          "globalMistakes": [
                            "A nearby Source Jar with enough Source is required — the recipe will not start without it.",
                            "Arcane Pedestals must be within 3 blocks of the Apparatus on the same Y level."
                          ],
                          "mistakes": [],
                          "optionalGroups": []
                        }
                        """);
    }

    private static PhantasiaScriptData buildImbuementScript() {
        // Layout: chamber at (2,1,2); Source Jars at cardinal offsets (y=1); stone floor at y=0.
        return PhantasiaScriptData.fromJson(
                """
                        {
                          "machine": "ars_nouveau:imbuement_chamber",
                          "startCamera": { "yaw": -135.0, "pitch": -25.0, "zoom": 3.0 },
                          "steps": [
                            {
                              "tick": 0,
                              "caption": "The Imbuement Chamber imbues items with magical properties by consuming Source from nearby Source Jars.",
                              "show": "pos",
                              "positions": [[2,1,2]],
                              "camera": { "yaw": -135.0, "pitch": -25.0, "zoom": 3.0, "lerpType": "SNAP", "lerpTicks": 0 }
                            },
                            {
                              "tick": 60,
                              "caption": "Place Source Jars at all 4 cardinal positions and fill them with Source before starting.",
                              "show": "pos",
                              "positions": [[2,1,2],[0,1,2]],
                              "camera": { "yaw": -135.0, "pitch": -38.0, "zoom": 3.8, "lerpType": "EASE", "lerpTicks": 12 }
                            },
                            {
                              "tick": 90,
                              "show": "pos",
                              "positions": [[2,1,2],[0,1,2],[4,1,2]],
                              "camera": { "yaw": -145.0, "pitch": -40.0, "zoom": 4.0, "lerpType": "EASE", "lerpTicks": 10 }
                            },
                            {
                              "tick": 120,
                              "show": "pos",
                              "positions": [[2,1,2],[0,1,2],[4,1,2],[2,1,0]],
                              "camera": { "yaw": -150.0, "pitch": -43.0, "zoom": 4.2, "lerpType": "EASE", "lerpTicks": 10 }
                            },
                            {
                              "tick": 150,
                              "show": "pos",
                              "positions": [[2,1,2],[0,1,2],[4,1,2],[2,1,0],[2,1,4]],
                              "camera": { "yaw": -155.0, "pitch": -45.0, "zoom": 4.5, "lerpType": "EASE", "lerpTicks": 10 }
                            },
                            {
                              "tick": 200,
                              "caption": "Drop the item to imbue inside the Chamber. Source drains from the jars as the process runs.",
                              "show": "all",
                              "camera": { "yaw": -145.0, "pitch": -45.0, "zoom": 4.8, "lerpType": "EASE", "lerpTicks": 18 }
                            },
                            {
                              "tick": 260,
                              "caption": "Imbuing in progress — Source flows into the Chamber as it transforms the item.",
                              "show": "all",
                              "hold": "an_crafting_imbuement",
                              "camera": { "yaw": -145.0, "pitch": -45.0, "zoom": 4.8, "lerpType": "SNAP", "lerpTicks": 0 }
                            },
                            {
                              "tick": 261,
                              "caption": "Imbuement complete! The transformed item can now be collected from the Chamber.",
                              "show": "all",
                              "camera": { "yaw": -135.0, "pitch": -28.0, "zoom": 3.5, "lerpType": "EASE", "lerpTicks": 20 }
                            }
                          ],
                          "globalMistakes": [
                            "Source Jars must contain enough Source to complete the recipe before it will begin.",
                            "Source Jars must be at the 4 cardinal positions within 3 blocks of the Chamber."
                          ],
                          "mistakes": [],
                          "optionalGroups": []
                        }
                        """);
    }
}
