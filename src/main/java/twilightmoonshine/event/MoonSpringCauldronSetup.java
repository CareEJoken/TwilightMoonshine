package twilightmoonshine.event;

import net.minecraft.core.cauldron.CauldronInteraction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import twilightmoonshine.TwilightMoonshine;

@EventBusSubscriber(modid = TwilightMoonshine.MODID, bus = EventBusSubscriber.Bus.MOD)
public class MoonSpringCauldronSetup {

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CauldronInteraction.EMPTY.map().put(
                    TwilightMoonshine.MOON_SPRING_BUCKET.value(),
                    CauldronInteraction.FILL_WATER
            );
            CauldronInteraction.WATER.map().put(
                    TwilightMoonshine.MOON_SPRING_BUCKET.value(),
                    CauldronInteraction.FILL_WATER
            );
        });
    }
}
