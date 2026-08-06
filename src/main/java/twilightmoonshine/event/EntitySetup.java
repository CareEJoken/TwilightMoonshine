package twilightmoonshine.event;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Rabbit;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import twilightmoonshine.TwilightMoonshine;

@EventBusSubscriber(modid = TwilightMoonshine.MODID, bus = EventBusSubscriber.Bus.MOD)
public class EntitySetup {

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(TwilightMoonshine.MOON_RABBIT.value(),
            Rabbit.createAttributes()
                .add(Attributes.MAX_HEALTH, 50.0D)
                .build());
    }
}
