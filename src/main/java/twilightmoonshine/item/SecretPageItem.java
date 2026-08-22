package twilightmoonshine.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import twilightmoonshine.data.RecipeKnowledge;

/**
 * 神秘书页：秘密配方渠道给玩家的实物奖励（配方不再通过聊天告知，见 RecipeKnowledge.grantPage）。
 * 用 custom_data 组件绑定一份配方（RecipeKnowledge.PLANT_EXTRACT 等）；
 * 右键打开后消耗自身、解锁该配方（授予知识：对已掌握/空白页不消耗）。
 */
public class SecretPageItem extends Item {

    /** custom_data 里存放配方 ID 的键 */
    public static final String TAG_RECIPE = "recipe";

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
        if (RecipeKnowledge.knows(serverPlayer, recipe)) {
            player.displayClientMessage(
                Component.translatable("item.twilightmoonshine.secret_page.known"), true);
            return InteractionResultHolder.fail(stack);
        }
        // 解锁配方（grant 里带出配方材料提示 + 触发配方判据），然后消耗这张纸
        RecipeKnowledge.grant(serverPlayer, recipe);
        stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }
}
