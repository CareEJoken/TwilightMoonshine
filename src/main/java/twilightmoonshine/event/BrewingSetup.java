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
        // 粗制的药水 + 暮色合金粉末 → 抗性药水
        event.getBuilder().addMix(
            Potions.AWKWARD,
            TwilightMoonshine.TWILIGHT_ALLOY.get(),
            TwilightMoonshine.RESISTANCE_POTION
        );

        // 抗性药水 + 红石 → 延长版抗性药水
        event.getBuilder().addMix(
            TwilightMoonshine.RESISTANCE_POTION,
            Items.REDSTONE,
            TwilightMoonshine.LONG_RESISTANCE_POTION
        );

        // 抗性药水 + 荧石 → 加强版抗性药水
        event.getBuilder().addMix(
            TwilightMoonshine.RESISTANCE_POTION,
            Items.GLOWSTONE_DUST,
            TwilightMoonshine.STRONG_RESISTANCE_POTION
        );

        // 水 + 月石碎片 → 微微发亮的药水（基底，无效果，类似平凡的药水）
        event.getBuilder().addMix(
            Potions.WATER,
            TwilightMoonshine.MOON_STONE_SHARD.get(),
            TwilightMoonshine.FAINTLY_GLOWING_POTION
        );

        // 微微发亮的药水 + 荧光精华 → 荧光药水（粗制的药水也可作基底，见下方）
        event.getBuilder().addMix(
            TwilightMoonshine.FAINTLY_GLOWING_POTION,
            TwilightMoonshine.GLOW_ESSENCE.get(),
            TwilightMoonshine.GLOW_POTION
        );

        // 粗制的药水 + 荧光精华 → 荧光药水（微微发亮的药水不再是必需基底）
        event.getBuilder().addMix(
            Potions.AWKWARD,
            TwilightMoonshine.GLOW_ESSENCE.get(),
            TwilightMoonshine.GLOW_POTION
        );

        // 荧光药水 + 红石 → 延长版荧光药水（无加强版）
        event.getBuilder().addMix(
            TwilightMoonshine.GLOW_POTION,
            Items.REDSTONE,
            TwilightMoonshine.LONG_GLOW_POTION
        );

        // 微微发亮的药水 + 暮色植物萃取液 → 暮色药水（粗制的药水也可作基底，见下方）
        event.getBuilder().addMix(
            TwilightMoonshine.FAINTLY_GLOWING_POTION,
            TwilightMoonshine.TWILIGHT_PLANT_EXTRACT.get(),
            TwilightMoonshine.TWILIGHT_POTION
        );

        // 粗制的药水 + 暮色植物萃取液 → 暮色药水
        event.getBuilder().addMix(
            Potions.AWKWARD,
            TwilightMoonshine.TWILIGHT_PLANT_EXTRACT.get(),
            TwilightMoonshine.TWILIGHT_POTION
        );

        // 暮色药水 + 红石 → 延长版暮色药水（无加强版）
        event.getBuilder().addMix(
            TwilightMoonshine.TWILIGHT_POTION,
            Items.REDSTONE,
            TwilightMoonshine.LONG_TWILIGHT_POTION
        );
    }
}
