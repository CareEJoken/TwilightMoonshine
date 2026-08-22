package twilightmoonshine.data;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundRecipePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import twilightmoonshine.TwilightMoonshine;
import twilightmoonshine.item.SecretPageItem;
import twilightmoonshine.item.recipe.SecretRecipeLogic;

/**
 * 秘密配方的"知识"授予接口（背后是 RecipeKnowledgeData 存档）：
 * 获取渠道只发"神秘书页"实物（grantPage，不发聊天消息）；
 * 玩家右键开启神秘书页后才真正解锁（grant：发出配方材料提示 + 触发 recipe_learned 判据）。
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

    /** 解锁配方（开启神秘书页时调用）：未掌握 → 材料提示 + 触发判据，返回 true；已掌握 → 无操作，返回 false */
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
        // 特殊配方（CustomRecipe）会被 ServerRecipeBook.addRecipes 的 isSpecial() 过滤直接跳过，
        // 进不了原版解锁流。这里仿照其实现手动构造 ADD 包发到客户端：
        // 客户端会弹出原版"新的配方!"toast（showNotification 恒为 true），
        // 书内展示则仍会被客户端 collectRecipe 的 isSpecial() 过滤，配方书不会被污染。
        player.connection.send(new ClientboundRecipePacket(
            ClientboundRecipePacket.State.ADD,
            List.of(recipe),
            List.of(),
            player.getRecipeBook().getBookSettings()));
        return true;
    }

    /** 给予神秘书页（实物奖励，不发聊天消息）：未掌握才给；返回是否给出 */
    public static boolean grantPage(ServerPlayer player, ResourceLocation recipe) {
        if (knows(player, recipe)) {
            return false;
        }
        ItemStack page = new ItemStack(TwilightMoonshine.SECRET_PAGE.get());
        SecretPageItem.bindRecipe(page, recipe);
        // 背包放不下时掉在玩家脚边，保证给到
        if (!player.getInventory().add(page)) {
            player.drop(page, false);
        }
        return true;
    }

    /** 概率给予神秘书页（未掌握时按 chance 摇奖，摇中才给），用于喂月兔 / 吸引动物渠道 */
    public static boolean grantPageIfChance(ServerPlayer player, ResourceLocation recipe, float chance) {
        if (knows(player, recipe) || player.getRandom().nextFloat() >= chance) {
            return false;
        }
        return grantPage(player, recipe);
    }

    /** 玩家背包（主槽位）里是否持有绑定了该配方的神秘书页 */
    public static boolean hasPage(ServerPlayer player, ResourceLocation recipe) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(TwilightMoonshine.SECRET_PAGE.get())
                && recipe.equals(SecretPageItem.getRecipe(stack))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 由生物向玩家"丢"出一张神秘书页：书页实体从 from 位置抛向玩家头顶，
     * 落地前即可拾取（拾取延迟 0）。用于拥抱日 / 荧光引路的顺承给予。
     */
    public static void tossPage(ServerLevel level, Vec3 from, ServerPlayer player, ResourceLocation recipe) {
        ItemStack page = new ItemStack(TwilightMoonshine.SECRET_PAGE.get());
        SecretPageItem.bindRecipe(page, recipe);
        ItemEntity item = new ItemEntity(level, from.x, from.y, from.z, page);
        Vec3 dir = player.position().add(0.0, player.getEyeHeight(), 0.0).subtract(from);
        double len = dir.length();
        item.setDeltaMovement(len < 0.01 ? new Vec3(0.0, 0.35, 0.0)
            : dir.scale(0.35 / len).add(0.0, 0.35, 0.0));
        item.setPickUpDelay(0);
        level.addFreshEntity(item);
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
