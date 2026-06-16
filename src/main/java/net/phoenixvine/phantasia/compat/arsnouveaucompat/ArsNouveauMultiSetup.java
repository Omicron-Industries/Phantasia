package net.phoenixvine.phantasia.compat.arsnouveaucompat;

import com.hollingsworth.arsnouveau.api.enchanting_apparatus.EnchantingApparatusRecipe;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import com.hollingsworth.arsnouveau.setup.registry.RecipeRegistry;
import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.phoenixvine.phantasia.common.multisetup.IPhantasiaMultiSetup;
import net.phoenixvine.phantasia.common.multisetup.IPhantasiaSetupRecipe;

import java.util.ArrayList;
import java.util.List;

/** Represents one Ars Nouveau machine type (Enchanting Apparatus or Imbuement Chamber). */
public class ArsNouveauMultiSetup implements IPhantasiaMultiSetup {

    public enum SetupType { ENCHANTING_APPARATUS, IMBUEMENT_CHAMBER }

    private final SetupType type;
    private final ResourceLocation id;
    private final String displayName;

    // Lazily resolved after registries are populated
    private ItemStack cachedIcon = null;
    private BlockInfo[][][] cachedLayout = null;

    // Lazily populated from recipe manager on first call
    private List<IPhantasiaSetupRecipe> cachedRecipes = null;

    public ArsNouveauMultiSetup(SetupType type, ResourceLocation id, String displayName) {
        this.type = type;
        this.id = id;
        this.displayName = displayName;
    }

    @Override public ResourceLocation getId() { return id; }
    @Override public String getDisplayName() { return displayName; }

    @Override
    public ItemStack getIcon() {
        if (cachedIcon == null) {
            cachedIcon = switch (type) {
                case ENCHANTING_APPARATUS ->
                        new ItemStack(com.hollingsworth.arsnouveau.setup.registry.BlockRegistry.ENCHANTING_APP_BLOCK.get());
                case IMBUEMENT_CHAMBER ->
                        new ItemStack(com.hollingsworth.arsnouveau.setup.registry.BlockRegistry.IMBUEMENT_BLOCK.get());
            };
        }
        return cachedIcon;
    }

    @Override
    public BlockInfo[][][] getBaseLayout() {
        if (cachedLayout == null) {
            cachedLayout = switch (type) {
                case ENCHANTING_APPARATUS -> ArsNouveauLayoutBuilder.enchantingApparatusBase(8);
                case IMBUEMENT_CHAMBER    -> ArsNouveauLayoutBuilder.imbuementChamberBase();
            };
        }
        return cachedLayout;
    }

    @Override
    public List<IPhantasiaSetupRecipe> getRecipes() {
        if (cachedRecipes != null) return cachedRecipes;
        cachedRecipes = new ArrayList<>();

        var mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return cachedRecipes;
        RecipeManager rm = mc.getConnection().getRecipeManager();

        switch (type) {
            case ENCHANTING_APPARATUS -> loadApparatusRecipes(rm);
            case IMBUEMENT_CHAMBER -> loadImbuementRecipes(rm);
        }

        return cachedRecipes;
    }

    /** Invalidate cache on resource reload so recipes stay current. */
    public void invalidateCache() {
        cachedRecipes = null;
    }

    // ── loaders ────────────────────────────────────────────────────────────────

    private void loadApparatusRecipes(RecipeManager rm) {
        rm.getAllRecipesFor(RecipeRegistry.APPARATUS_TYPE.get()).forEach(recipe -> {
            // No instanceof needed because the registry type guarantees this object type
            EnchantingApparatusRecipe r = (EnchantingApparatusRecipe) recipe;

            List<ItemStack> pedItems = r.pedestalItems.stream()
                    .map(ing -> ing.getItems().length > 0 ? ing.getItems()[0] : ItemStack.EMPTY)
                    .toList();
            ItemStack reagent = r.reagent.getItems().length > 0
                    ? r.reagent.getItems()[0] : ItemStack.EMPTY;

            var result = ArsNouveauLayoutBuilder.enchantingApparatusRecipe(pedItems, reagent);

            List<ItemStack> allInputs = new ArrayList<>(pedItems);
            allInputs.add(0, reagent);

            cachedRecipes.add(new ArsNouveauSetupRecipe(
                    r.getId(),
                    r.result.copy(),
                    allInputs,
                    r.sourceCost,
                    result.layout(),
                    result.itemPlacements()
            ));
        });
    }

    private void loadImbuementRecipes(RecipeManager rm) {
        rm.getAllRecipesFor(RecipeRegistry.IMBUEMENT_TYPE.get()).forEach(recipe -> {
            // No instanceof needed because the registry type guarantees this object type
            ImbuementRecipe r = (ImbuementRecipe) recipe;

            ItemStack input = r.input.getItems().length > 0 ? r.input.getItems()[0] : ItemStack.EMPTY;
            var result = ArsNouveauLayoutBuilder.imbuementRecipe(input);

            cachedRecipes.add(new ArsNouveauSetupRecipe(
                    r.getId(),
                    r.output.copy(),
                    List.of(input),
                    r.source,
                    result.layout(),
                    result.itemPlacements()
            ));
        });
    }
}