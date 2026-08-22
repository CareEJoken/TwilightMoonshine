package twilightmoonshine.data;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import twilightmoonshine.TwilightMoonshine;
import twilightmoonshine.item.recipe.SecretRecipeLogic;

/**
 * 秘密配方的"知识"授予接口（背后是 RecipeKnowledgeData 存档）：
 * grant 成功时向玩家发送"得到配方"通知，并触发 recipe_learned 进度判据。
 * 已掌握的配方不会重复授予。获取渠道见 RecipeLearningEvents 与 MoonRabbit。
 */
public final class RecipeKnowledge {

    /** 三张秘密配方的配方 ID，也就是对应产物物品的注册名（RecipeKnowledgeData 中以此存取） */
    public static final ResourceLocation PLANT_EXTRACT =
        ResourceLocation.fromNamespaceAndPath("twilightmoonshine", "twilight_plant_extract");
    public static final ResourceLocation GLOW_ESSENCE =
        ResourceLocation.fromNamespaceAndPath("twilightmoonshine", "twilight_glow_essence");
    public static final ResourceLocation ALLOY =
        ResourceLocation.fromNamespaceAndPath("twilightmoonshine", "twilight_alloy");

    private RecipeKnowledge() {
    }

    /** 玩家是否已掌握该配方 */
    public static boolean knows(ServerPlayer player, ResourceLocation recipe) {
        return RecipeKnowledgeData.get(player.getServer()).isKnown(player.getUUID(), recipe);
    }

    /** 授予配方：未掌握 → 发通知 + 触发判据，返回 true；已掌握 → 无操作，返回 false */
    public static boolean grant(ServerPlayer player, ResourceLocation recipe) {
        if (!RecipeKnowledgeData.get(player.getServer()).grant(player.getUUID(), recipe)) {
            return false;
        }
        Component materials = SecretRecipeLogic.secretFor(player.level(), candidatesFor(recipe)).stream()
            .map(RecipeKnowledge::itemDisplayName)
            .reduce((a, b) -> a.copy().append("、").append(b))
            .orElse(Component.literal("?"));
        player.displayClientMessage(
            Component.translatable("message.twilightmoonshine.recipe_learned", itemDisplayName(recipe), materials), false);
        TwilightMoonshine.RECIPE_LEARNED_TRIGGER.get().trigger(player, recipe);
        return true;
    }

    /** 概率授予（未掌握时按 chance 摇奖，摇中才授予），用于喂月兔 / 吸引动物渠道 */
    public static boolean grantIfChance(ServerPlayer player, ResourceLocation recipe, float chance) {
        if (knows(player, recipe)) {
            return false;
        }
        if (player.getRandom().nextFloat() >= chance) {
            return false;
        }
        return grant(player, recipe);
    }

    /** 该配方对应的候选材料清单（SecretRecipe 里世界种子抽签的池子），用于通知消息 */
    private static List<ResourceLocation> candidatesFor(ResourceLocation recipe) {
        if (recipe.equals(PLANT_EXTRACT)) {
            return SecretRecipeLogic.PLANT_EXTRACT_CANDIDATES;
        }
        if (recipe.equals(GLOW_ESSENCE)) {
            return SecretRecipeLogic.GLOW_ESSENCE_CANDIDATES;
        }
        if (recipe.equals(ALLOY)) {
            return SecretRecipeLogic.ALLOY_CANDIDATES;
        }
        return List.of();
    }

    /** 用注册表名字查询物品，取它的悬停名（未查询到则显示原始 key） */
    private static Component itemDisplayName(ResourceLocation key) {
        Item item = BuiltInRegistries.ITEM.get(key);
        return item == null ? Component.literal(key.toString()) : new ItemStack(item).getHoverName();
    }
}
