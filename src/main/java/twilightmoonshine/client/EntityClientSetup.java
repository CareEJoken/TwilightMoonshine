package twilightmoonshine.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import twilightmoonshine.client.renderer.MoonRabbitModel;
import twilightmoonshine.client.renderer.MoonRabbitRenderer;
import twilightmoonshine.TwilightMoonshine;

@EventBusSubscriber(modid = TwilightMoonshine.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class EntityClientSetup {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TwilightMoonshine.MOON_RABBIT.value(), MoonRabbitRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MoonRabbitModel.LAYER_LOCATION, MoonRabbitModel::createBodyLayer);
    }
}
