package twilightmoonshine.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import twilightmoonshine.TwilightMoonshine;
import twilightmoonshine.moonlight.MoonlightState;
import twilightmoonshine.moonlight.MoonlightSyncPayload;
import twilightmoonshine.moonlight.MoonlightWorldData;

/**
 * 月之铃：右键切换暮色森林的"月光"状态（月亮出现 + 天光提升）。
 * 开关式，只在暮色森林维度生效，带短暂冷却。
 */
public class MoonBellItem extends Item {

    public MoonBellItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }
        ServerLevel serverLevel = (ServerLevel) level;

        if (!MoonlightState.isTwilightForest(serverLevel)) {
            player.displayClientMessage(
                Component.translatable("item.twilightmoonshine.moon_bell.wrong_dimension"), true);
            return InteractionResultHolder.fail(stack);
        }

        MoonlightWorldData data = MoonlightWorldData.get(serverLevel);
        boolean nowActive = !data.isActive();
        data.setActive(nowActive);

        player.getCooldowns().addCooldown(this, 20);
        player.displayClientMessage(Component.translatable(
            nowActive ? "item.twilightmoonshine.moon_bell.on" : "item.twilightmoonshine.moon_bell.off"), true);
        MoonlightSyncPayload.broadcast(serverLevel, nowActive);
        // 暂时复用原版钟声（block.bell.resonate），以后换自定义音效
        serverLevel.playSound(null, player.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 2.0F, 1.0F);
        // TODO 满月终临（未完成，临时禁用）：
        // 在暮色森林中成功使用一次月之铃（启动/关闭月光都算一次摇铃）
        // if (player instanceof ServerPlayer serverPlayer) {
        //     TwilightMoonshine.MOON_BELL_USED_TRIGGER.get().trigger(serverPlayer);
        // }
        return InteractionResultHolder.consume(stack);
    }
}
