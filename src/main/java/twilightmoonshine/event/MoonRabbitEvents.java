package twilightmoonshine.event;

import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Ocelot;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import twilightmoonshine.TwilightMoonshine;
import twilightmoonshine.entity.MoonRabbit;

@EventBusSubscriber(modid = TwilightMoonshine.MODID, bus = EventBusSubscriber.Bus.GAME)
public class MoonRabbitEvents {

    // 狐狸、猫、豹猫不会把月兔设为攻击目标
    @SubscribeEvent
    public static void preventPredators(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof MoonRabbit
            && (event.getEntity() instanceof Fox || event.getEntity() instanceof Cat || event.getEntity() instanceof Ocelot)) {
            event.setNewAboutToBeSetTarget(null);
        }
    }
}
