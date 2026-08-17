package twilightmoonshine.moonlight;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import twilightmoonshine.TwilightMoonshine;

@EventBusSubscriber(modid = TwilightMoonshine.MODID, bus = EventBusSubscriber.Bus.MOD)
public class MoonlightNetwork {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(MoonlightSyncPayload.TYPE, MoonlightSyncPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> MoonlightState.setClientActive(payload.active())));
    }
}
