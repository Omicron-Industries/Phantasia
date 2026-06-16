package net.phoenixvine.phantasia.common;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.common.data.*;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.FusionReactorMachine;

import net.phoenixvine.phantasia.utils.StructureAssetReader;

import static com.gregtechceu.gtceu.api.data.tag.TagPrefix.block;
import static com.gregtechceu.gtceu.api.data.tag.TagPrefix.frameGt;
import static com.gregtechceu.gtceu.api.pattern.Predicates.blocks;
import static com.gregtechceu.gtceu.common.data.GCYMBlocks.*;
import static com.gregtechceu.gtceu.common.data.GTBlocks.*;
import static com.gregtechceu.gtceu.common.data.models.GTMachineModels.createWorkableCasingMachineModel;
import static net.minecraft.world.level.block.Blocks.*;
import static net.phoenixvine.phantasia.compat.gtceu.PhantasiaGTCompat.PHANTASIA_REGISTRATE;


public class PhantasiaTestMultiblocks {

    public static void init() {}

    // 1. Read the massive layout smoothly from resources during initialization
    private static final String[][] TETO_AISLES = StructureAssetReader
            .readAislesFromAsset("/assets/phantasia/structures/teto_structure.txt");

    public static final MachineDefinition TETONIUM = PHANTASIA_REGISTRATE
            .multiblock("synchronized_chimera_core", holder -> new FusionReactorMachine(holder, GTValues.UIV))
            .rotationState(RotationState.NON_Y_AXIS)
            .langValue("Synchronized Chimera Core")
            .recipeType(GTRecipeTypes.DUMMY_RECIPES)
            .recipeModifiers(GTRecipeModifiers.OC_NON_PERFECT)
            .appearanceBlock(GCYMBlocks.CASING_HIGH_TEMPERATURE_SMELTING)
            .pattern(definition -> {
                // Start your Factory Block Pattern
                FactoryBlockPattern patternBuilder = FactoryBlockPattern.start();

                // 2. Loop through every single aisle array we read from the file
                for (String[] aisle : TETO_AISLES) {
                    patternBuilder = patternBuilder.aisle(aisle);
                }

                // 3. Define the predicates mapping the letters to blocks
                return patternBuilder

                        .where("a", Predicates.air())
                        .where("b", Predicates.blocks(CASING_HIGH_TEMPERATURE_SMELTING.get()))
                        .where("c", Predicates.blocks(CASING_NONCONDUCTING.get()))
                        .where("d", Predicates.blocks(HEAT_VENT.get()))
                        .where("e", Predicates.blocks(CASING_EXTREME_ENGINE_INTAKE.get()))
                        .where("f", Predicates.blocks(CASING_TUNGSTENSTEEL_GEARBOX.get()))
                        .where("g", Predicates.blocks(COIL_NAQUADAH.get()))
                        .where("h", Predicates.blocks(FUSION_CASING_MK3.get()))
                        .where("i", Predicates.blocks(CASING_INDUSTRIAL_STEAM.get()))
                        .where("j", Predicates.blocks(FUSION_GLASS.get()))
                        .where("k", Predicates.blocks(FUSION_COIL.get()))
                        .where("l", Predicates.blocks(CASING_PTFE_INERT.get()))
                        .where("m", Predicates.blocks(ELECTROLYTIC_CELL.get()))
                        .where("n", Predicates.blocks(CASING_VIBRATION_SAFE.get()))
                        .where("o", Predicates.blocks(SUPERCONDUCTING_COIL.get()))
                        .where("p", Predicates.blocks(ChemicalHelper.getBlock(frameGt,
                                GTMaterials.Tritanium)))
                        .where("q", Predicates.blocks(CASING_LASER_SAFE_ENGRAVING.get()))
                        .where("r", Predicates.blocks(CASING_SECURE_MACERATION.get()))
                        .where("s", Predicates.blocks(CASING_STRESS_PROOF.get()))
                        .where("t", Predicates.blocks(BATTERY_LAPOTRONIC_UV.get()))
                        .where("u", Predicates.blocks(CASING_PALLADIUM_SUBSTATION.get()))
                        .where("v", Predicates.blocks(ChemicalHelper.getBlock(frameGt,
                                GTMaterials.Duranium)))
                        .where("w", Predicates.blocks(CASING_STAINLESS_CLEAN.get()))
                        .where("x", Predicates.blocks(HIGH_POWER_CASING.get()))
                        .where("y", Predicates.blocks(MOLYBDENUM_DISILICIDE_COIL_BLOCK.get()))
                        .where("z", Predicates.blocks(CASING_TITANIUM_STABLE.get()))
                        .where("A", Predicates.blocks(FIREBOX_TITANIUM.get()))
                        .where("B", Predicates.blocks(COIL_TRITANIUM.get()))
                        .where("C", Predicates.blocks(CASING_ENGINE_INTAKE.get()))
                        .where("D", Predicates.blocks(CASING_TITANIUM_GEARBOX.get()))
                        .where("E", Predicates.blocks(CASING_TITANIUM_PIPE.get()))
                        .where("F", Predicates.blocks(ChemicalHelper.getBlock(block,
                                GTMaterials.Plutonium241)))
                        .where("G", Predicates.blocks(ChemicalHelper.getBlock(block,
                                GTMaterials.Neutronium)))
                        .where("H", Predicates.blocks(HERMETIC_CASING_UHV.get()))
                        .where("I", Predicates.blocks(CASING_SHOCK_PROOF.get()))
                        .where("J", Predicates.blocks(CASING_ATOMIC.get()))
                        .where("K", Predicates.blocks(CASING_TUNGSTENSTEEL_ROBUST.get()))
                        .where("L", Predicates.blocks(BATTERY_ULTIMATE_UHV.get()))
                        .where("M", Predicates.blocks(ChemicalHelper.getBlock(frameGt,
                                GTMaterials.Neutronium)))
                        .where("N", Predicates.blocks(WHITE_WOOL))
                        .where("O", Predicates.blocks(NETHER_WART_BLOCK))
                        .where("P", Predicates.blocks(FILTER_CASING_STERILE.get()))
                        .where("Q", Predicates.blocks(GOLD_BLOCK))
                        .where("R", Predicates.blocks(CASING_LARGE_SCALE_ASSEMBLING.get()))
                        .where("S", Predicates.controller(Predicates.blocks(definition.get())))
                        .where("T", Predicates.blocks(ADVANCED_COMPUTER_CASING.get()))
                        .build();
            })
            .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
            .model(createWorkableCasingMachineModel(
                    GTCEu.id("block/casings/gcym/high_temperature_smelting_casing"),
                    GTCEu.id("block/multiblock/fusion_reactor")))
            .register();
}
