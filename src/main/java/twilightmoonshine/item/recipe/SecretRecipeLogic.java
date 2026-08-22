package twilightmoonshine.item.recipe;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * 秘密材料逻辑（工作台秘密配方与 /moonshine_secret 共用）：
 * 每种产物有 5 个候选材料，每存档按世界种子抽 3 个作为"秘密材料"，
 * 工作台需恰好放入这 3 种（各 1 个）才能合成，不同存档各不相同。
 */
public final class SecretRecipeLogic {

    /** 暮色植物萃取液候选：暮色蕨、暮色苔藓、mayapple、根须、巨型荷花（与配方 JSON 一致） */
    public static final List<ResourceLocation> PLANT_EXTRACT_CANDIDATES = List.of(
        tf("fiddlehead"), tf("moss_patch"), tf("mayapple"), tf("root_strand"), tf("huge_water_lily"));

    /** 暮色荧光精华候选：荧光蘑菇、火炬浆果、火炬浆果植物、萤火虫、巨魔莓（与配方 JSON 一致） */
    public static final List<ResourceLocation> GLOW_ESSENCE_CANDIDATES = List.of(
        tf("mushgloom"), tf("torchberries"), tf("torchberry_plant"), tf("firefly"), tf("trollber"));

    /** 暮色合金候选：锻铁锭、铁木锭、钢叶、炽铁锭、骑士金属锭（与配方 JSON 一致） */
    public static final List<ResourceLocation> ALLOY_CANDIDATES = List.of(
        tf("wrought_iron_bar"), tf("ironwood_ingot"), tf("steeleaf_ingot"), tf("fiery_ingot"),
        tf("knightmetal_ingot"));

    /** 固定盐：与种子异或后抽秘密材料 */
    private static final long SECRET_SALT = 0x9E3779B97F4A7C15L;

    private SecretRecipeLogic() {
    }

    /** 当前世界的秘密三材料：按世界种子从候选中抽 3 个，不同存档各不相同（保留抽取顺序） */
    public static Set<ResourceLocation> secretFor(Level level, List<ResourceLocation> candidates) {
        long seed = level instanceof ServerLevel serverLevel ? serverLevel.getSeed() : 0L;
        RandomSource random = RandomSource.create(seed ^ SECRET_SALT);
        List<ResourceLocation> pool = new ArrayList<>(candidates);
        Set<ResourceLocation> secret = new LinkedHashSet<>();
        for (int i = 0; i < 3 && !pool.isEmpty(); i++) {
            secret.add(pool.remove(random.nextInt(pool.size())));
        }
        return secret;
    }

    /** 秘密材料对应的物品集合（解析失败的候选被跳过） */
    public static Set<Item> secretItemsFor(Level level, List<ResourceLocation> candidates) {
        Set<Item> items = new LinkedHashSet<>();
        for (ResourceLocation key : secretFor(level, candidates)) {
            Item item = BuiltInRegistries.ITEM.get(key);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    private static ResourceLocation tf(String path) {
        return ResourceLocation.fromNamespaceAndPath("twilightforest", path);
    }
}
