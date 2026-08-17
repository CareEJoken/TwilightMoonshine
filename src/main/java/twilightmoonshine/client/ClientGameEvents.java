package twilightmoonshine.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import twilightmoonshine.TwilightMoonshine;

/**
 * 客户端 tick 计时器 — 仿暮色森林 ClientGameEvents.time：
 * 战利品 GUI 图标的旋转角度 = time % 360，每游戏 tick 前进 1°（20°/秒），
 * 游戏暂停时冻结。与谜题羊等 TF 战利品的旋转机制完全一致，
 * 避免了挂钟时间（Util.getMillis）在暂停/卡顿时速度对不上的问题。
 */
@EventBusSubscriber(modid = TwilightMoonshine.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientGameEvents {

    public static int time = 0;

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!Minecraft.getInstance().isPaused()) {
            time++;
        }
    }
}
