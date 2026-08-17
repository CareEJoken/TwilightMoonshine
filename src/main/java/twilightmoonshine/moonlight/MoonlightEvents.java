package twilightmoonshine.moonlight;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import twilightmoonshine.TwilightMoonshine;

/**
 * 玩家登录 / 切换维度时，把当前"月光"状态补发给该玩家，
 * 保证所有客户端的标志与服务端一致。
 */
@EventBusSubscriber(modid = TwilightMoonshine.MODID, bus = EventBusSubscriber.Bus.GAME)
public class MoonlightEvents {

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        sync(event.getEntity());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        sync(event.getEntity());
    }

    private static void sync(Player player) {
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.level() instanceof ServerLevel serverLevel) {
            boolean active = MoonlightState.isTwilightForest(serverLevel)
                && MoonlightWorldData.get(serverLevel).isActive();
            serverPlayer.connection.send(new MoonlightSyncPayload(active));
        }
    }
}
