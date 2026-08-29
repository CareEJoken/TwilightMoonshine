package twilightmoonshine.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import twilightmoonshine.TwilightMoonshine;
import twilightmoonshine.data.RecipeKnowledge;

/**
 * 神秘书页：秘密配方渠道给玩家的实物奖励（配方不再通过聊天告知，见 RecipeKnowledge.grantPage）。
 * 用 custom_data 组件绑定一份配方（RecipeKnowledge.PLANT_EXTRACT 等）。
 * 右键翻开后不消耗：首次翻开解锁该配方，同时这一页变成对应的"配方卡"——
 * 按绑定配方改名（如"暮色植物萃取液配方"）并附上附魔光效。
 * （1.21.1 尚无 ENCHANTMENT_GLINT_OVERRIDE 组件，光效由标记 + Item#isFoil 实现）
 */
public class SecretPageItem extends Item {

    /** custom_data 里存放配方 ID 的键 */
    public static final String TAG_RECIPE = "recipe";

    /** custom_data 里"已翻开"标记（开后才有配方名与附魔光效） */
    public static final String TAG_OPENED = "opened";

    public SecretPageItem(Properties properties) {
        super(properties);
    }

    /** 把配方 ID 写进 custom_data 组件（recipe 键）；"空格子" = 未绑定配方的白页 */
    public static void bindRecipe(ItemStack stack, ResourceLocation recipe) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_RECIPE, recipe.toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** 读出绑定的配方 ID；未绑定返回 null */
    public static ResourceLocation getRecipe(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        String s = data.copyTag().getString(TAG_RECIPE);
        return s.isEmpty() ? null : ResourceLocation.tryParse(s);
    }

    /** 这一页是否已翻开（翻开后才有附魔光效） */
    public static boolean isOpened(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(TAG_OPENED);
    }

    /** 打上"已翻开"标记（保留 recipe 键） */
    private static void markOpened(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data != null ? data.copyTag() : new CompoundTag();
        tag.putBoolean(TAG_OPENED, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** 已翻开的页面带附魔光效（翻开与否能进 NBT，随时重启保留） */
    @Override
    public boolean isFoil(ItemStack stack) {
        return isOpened(stack);
    }

    /** 把绑定页"变身"为配方卡：按绑定配方改名（去斜体）+ 标记翻开（附魔光效）。幂等 */
    private static void applyOpenedLook(ItemStack stack, ResourceLocation recipe) {
        Component cardName = boundRecipeDisplayName(recipe);
        if (cardName != null && !cardName.equals(stack.getHoverName())) {
            stack.set(DataComponents.CUSTOM_NAME, cardName.copy().withStyle(Style.EMPTY.withItalic(false)));
        }
        if (!isOpened(stack)) {
            markOpened(stack);
        }
    }

    /**
     * 创造物品栏专用：直接生成"已翻开"状态的配方卡（绑定配方 + 配方名 + 附魔光效），
     * 便于在创造栏分辨三张页。游戏内掉落渠道请用 {@link #bindRecipe}——保持"神秘书页"未读外观。
     */
    public static ItemStack recipeCard(ResourceLocation recipe) {
        ItemStack stack = new ItemStack(TwilightMoonshine.SECRET_PAGE.get());
        bindRecipe(stack, recipe);
        applyOpenedLook(stack, recipe);
        return stack;
    }

    /** 绑定配方对应的"配方卡"显示名（lang key）；未知配方返回 null（保持原名"神秘书页"） */
    private static Component boundRecipeDisplayName(ResourceLocation recipe) {
        if (RecipeKnowledge.PLANT_EXTRACT.equals(recipe)) {
            return Component.translatable("item.twilightmoonshine.secret_page.recipe.plant_extract");
        }
        if (RecipeKnowledge.GLOW_ESSENCE.equals(recipe)) {
            return Component.translatable("item.twilightmoonshine.secret_page.recipe.glow_essence");
        }
        if (RecipeKnowledge.ALLOY.equals(recipe)) {
            return Component.translatable("item.twilightmoonshine.secret_page.recipe.alloy");
        }
        return null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }
        ResourceLocation recipe = getRecipe(stack);
        if (recipe == null) {
            player.displayClientMessage(
                Component.translatable("item.twilightmoonshine.secret_page.blank"), true);
            return InteractionResultHolder.fail(stack);
        }
        // 翻开即成为"配方卡"：按绑定配方改名（去斜体）并附上附魔光效。幂等
        applyOpenedLook(stack, recipe);
        if (RecipeKnowledge.knows(serverPlayer, recipe)) {
            // 重复翻开：同样内容的配方提示（actionbar），不重复触发判据/配方书
            RecipeKnowledge.showRecipeMessage(serverPlayer, recipe);
            return InteractionResultHolder.success(stack);
        }
        // 首次翻开：解锁配方（grant 里带出配方材料提示 + 触发配方判据）；页面保留，不消耗
        RecipeKnowledge.grant(serverPlayer, recipe);
        return InteractionResultHolder.success(stack);
    }
}
