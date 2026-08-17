package twilightmoonshine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import twilightmoonshine.TwilightMoonshine;
import twilightmoonshine.moonlight.MoonlightState;

/**
 * 客户端"唤月"渲染：月光激活且处于暮色森林时，在天空阶段之后画满月。
 */
@EventBusSubscriber(modid = TwilightMoonshine.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class MoonlightSkyEvents {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (!MoonlightState.isClientActive() || !MoonlightState.isTwilightForest(level)) return;

        MoonSkyRenderer.render(event);
    }
}
