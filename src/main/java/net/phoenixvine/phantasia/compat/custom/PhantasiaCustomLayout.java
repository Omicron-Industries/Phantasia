package net.phoenixvine.phantasia.compat.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import com.google.gson.*;

import java.util.*;

public class PhantasiaCustomLayout {

    public String id;
    public String displayName;
    public String iconBlock;

    public final Map<BlockPos, String> blocks = new LinkedHashMap<>();

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("id", id);
        root.addProperty("displayName", displayName);
        if (iconBlock != null && !iconBlock.isBlank()) root.addProperty("iconBlock", iconBlock);

        JsonArray arr = new JsonArray();
        for (Map.Entry<BlockPos, String> e : blocks.entrySet()) {
            JsonObject b = new JsonObject();
            b.addProperty("x", e.getKey().getX());
            b.addProperty("y", e.getKey().getY());
            b.addProperty("z", e.getKey().getZ());
            b.addProperty("block", e.getValue());
            arr.add(b);
        }
        root.add("blocks", arr);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    public static PhantasiaCustomLayout fromJson(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        PhantasiaCustomLayout layout = new PhantasiaCustomLayout();
        layout.id = root.has("id") ? root.get("id").getAsString() : "phantasia:unnamed";
        layout.displayName = root.has("displayName") ? root.get("displayName").getAsString() : "Custom Scene";
        layout.iconBlock = root.has("iconBlock") ? root.get("iconBlock").getAsString() : null;
        if (root.has("blocks")) {
            for (JsonElement el : root.getAsJsonArray("blocks")) {
                JsonObject b = el.getAsJsonObject();
                int x = b.get("x").getAsInt();
                int y = b.get("y").getAsInt();
                int z = b.get("z").getAsInt();
                layout.blocks.put(new BlockPos(x, y, z), b.get("block").getAsString());
            }
        }
        return layout;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static BlockState parseBlockState(String str) {
        if (str == null || str.isBlank()) return Blocks.AIR.defaultBlockState();
        String id = str;
        String props = null;
        int bracket = str.indexOf('[');
        if (bracket >= 0) {
            id = str.substring(0, bracket);
            props = str.substring(bracket + 1, str.endsWith("]") ? str.length() - 1 : str.length());
        }
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        if (block == null || block == Blocks.AIR) return Blocks.AIR.defaultBlockState();
        BlockState state = block.defaultBlockState();
        if (props != null) {
            StateDefinition<Block, BlockState> def = block.getStateDefinition();
            for (String part : props.split(",")) {
                String[] kv = part.split("=", 2);
                if (kv.length != 2) continue;
                Property prop = def.getProperty(kv[0].trim());
                if (prop == null) continue;
                Optional<Comparable> val = prop.getValue(kv[1].trim());
                if (val.isPresent()) state = state.setValue(prop, val.get());
            }
        }
        return state;
    }

    public static String serializeBlockState(BlockState state) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (key == null) return "minecraft:air";
        String base = key.toString();
        Collection<Property<?>> props = state.getProperties();
        if (props.isEmpty()) return base;
        StringJoiner sj = new StringJoiner(",", base + "[", "]");
        for (Property<?> p : props) {
            sj.add(p.getName() + "=" + state.getValue(p));
        }
        return sj.toString();
    }

    public int maxX() {
        return blocks.keySet().stream().mapToInt(BlockPos::getX).max().orElse(0);
    }

    public int maxY() {
        return blocks.keySet().stream().mapToInt(BlockPos::getY).max().orElse(0);
    }

    public int maxZ() {
        return blocks.keySet().stream().mapToInt(BlockPos::getZ).max().orElse(0);
    }
}
