package net.phoenixvine.phantasia.compat.custom;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.loading.FMLPaths;
import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockProvider;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class PhantasiaCustomProvider implements IPhantasiaMultiblockProvider {

    public static final Path CUSTOM_DIR = FMLPaths.GAMEDIR.get().resolve("phantasia/multiblocks");

    private final Map<ResourceLocation, PhantasiaCustomMultiblockDefinition> loaded = new LinkedHashMap<>();

    @Override
    public String getModId() {
        return "phantasia";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    public void reload() {
        loaded.clear();
        if (!Files.exists(CUSTOM_DIR)) {
            try {
                Files.createDirectories(CUSTOM_DIR);
            } catch (IOException ignored) {}
            return;
        }
        try (var stream = Files.walk(CUSTOM_DIR, 1)) {
            stream.filter(p -> p.toString().endsWith(".json")).forEach(this::loadFile);
        } catch (IOException e) {
            Phantasia.LOGGER.error("[Phantasia/Custom] Failed to scan custom dir: {}", e.getMessage());
        }
        Phantasia.LOGGER.info("[Phantasia/Custom] Loaded {} custom scene(s).", loaded.size());
    }

    private void loadFile(Path path) {
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            PhantasiaCustomLayout layout = PhantasiaCustomLayout.fromJson(json);
            PhantasiaCustomMultiblockDefinition def = new PhantasiaCustomMultiblockDefinition(layout);
            loaded.put(def.getId(), def);
        } catch (Exception e) {
            Phantasia.LOGGER.error("[Phantasia/Custom] Failed to load {}: {}", path.getFileName(), e.getMessage());
        }
    }

    public void save(PhantasiaCustomLayout layout) {
        try {
            Files.createDirectories(CUSTOM_DIR);
            String safeName = layout.id.replace(":", "_").replace("/", "_");
            Path dest = CUSTOM_DIR.resolve(safeName + ".json");
            Files.writeString(dest, layout.toJson(), StandardCharsets.UTF_8);
            PhantasiaCustomMultiblockDefinition def = new PhantasiaCustomMultiblockDefinition(layout);
            loaded.put(def.getId(), def);
            Phantasia.LOGGER.info("[Phantasia/Custom] Saved custom multiblock '{}'.", layout.id);
        } catch (IOException e) {
            Phantasia.LOGGER.error("[Phantasia/Custom] Failed to save '{}': {}", layout.id, e.getMessage());
        }
    }

    @Override
    public List<IPhantasiaMultiblockDefinition> getAllDefinitions() {
        return new ArrayList<>(loaded.values());
    }

    @Override
    public Optional<IPhantasiaMultiblockDefinition> resolve(String machineId) {
        ResourceLocation rl = machineId.contains(":") ? new ResourceLocation(machineId) :
                new ResourceLocation("phantasia", machineId);
        return Optional.ofNullable(loaded.get(rl));
    }

    @Override
    public Optional<PhantasiaBlockInfo> resolveBlock(String id) {
        return Optional.empty();
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
        return Optional.empty();
    }
}
