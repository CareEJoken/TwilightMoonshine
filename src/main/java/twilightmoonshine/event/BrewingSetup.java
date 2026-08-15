package twilightmoonshine.event;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import twilightmoonshine.TwilightMoonshine;

@EventBusSubscriber(modid = TwilightMoonshine.MODID, bus = EventBusSubscriber.Bus.MOD)
public class BrewingSetup {

    @SubscribeEvent
    public static void registerBrewing(RegisterBrewingRecipesEvent event) {
        // Awkward Potion + Moon Stone Shard → Potion of Resistance
        event.getBuilder().addMix(
            Potions.AWKWARD,
            TwilightMoonshine.MOON_STONE_SHARD.get(),
            TwilightMoonshine.RESISTANCE_POTION
        );

        // Resistance + Redstone → Long Resistance
        event.getBuilder().addMix(
            TwilightMoonshine.RESISTANCE_POTION,
            Items.REDSTONE,
            TwilightMoonshine.LONG_RESISTANCE_POTION
        );

        // Resistance + Glowstone Dust → Strong Resistance
        event.getBuilder().addMix(
            TwilightMoonshine.RESISTANCE_POTION,
            Items.GLOWSTONE_DUST,
            TwilightMoonshine.STRONG_RESISTANCE_POTION
        );
    }
}
