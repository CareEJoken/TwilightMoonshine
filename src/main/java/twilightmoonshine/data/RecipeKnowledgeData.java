package twilightmoonshine.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 玩家"已掌握的秘密配方"知识存档（存在主世界维度，随存档保存）：
 * 每个玩家是一组配方 ID（twilightmoonshine:twilight_plant_extract 等）。
 * 授予入口见 RecipeKnowledge.grant；授予时会触发 recipe_learned 进度判据。
 */
public final class RecipeKnowledgeData extends SavedData {

    private static final String NAME = "twilightmoonshine_recipe_knowledge";

    /** 已掌握配方的玩家（键 = 玩家 UUID → 已掌握配方 ID 集合） */
    private final Map<UUID, Set<ResourceLocation>> known = new HashMap<>();

    private RecipeKnowledgeData() {
    }

    public static RecipeKnowledgeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public static Factory<RecipeKnowledgeData> factory() {
        return new SavedData.Factory<>(RecipeKnowledgeData::new, RecipeKnowledgeData::load, null);
    }

    public static RecipeKnowledgeData load(CompoundTag tag, HolderLookup.Provider provider) {
        RecipeKnowledgeData data = new RecipeKnowledgeData();
        for (Tag entry : tag.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag playerTag = (CompoundTag) entry;
            Set<ResourceLocation> recipes = new HashSet<>();
            for (Tag recipe : playerTag.getList("recipes", Tag.TAG_STRING)) {
                recipes.add(ResourceLocation.parse(recipe.getAsString()));
            }
            data.known.put(playerTag.getUUID("uuid"), recipes);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag players = new ListTag();
        this.known.forEach((uuid, recipes) -> {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("uuid", uuid);
            ListTag list = new ListTag();
            for (ResourceLocation recipe : recipes) {
                list.add(StringTag.valueOf(recipe.toString()));
            }
            playerTag.put("recipes", list);
            players.add(playerTag);
        });
        tag.put("players", players);
        return tag;
    }

    public boolean isKnown(UUID playerId, ResourceLocation recipe) {
        return this.known.getOrDefault(playerId, Set.of()).contains(recipe);
    }

    /** 登记玩家掌握某配方；已掌握则返回 false（调用方就不会重复给予） */
    public boolean grant(UUID playerId, ResourceLocation recipe) {
        Set<ResourceLocation> recipes = this.known.computeIfAbsent(playerId, k -> new HashSet<>());
        if (!recipes.add(recipe)) {
            return false;
        }
        this.setDirty();
        return true;
    }
}
