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
        // 抗性药水的酿造配方已取消（物品本身保留）

        // 水 + 月石碎片 → 微微发亮的药水（基底，无效果，类似平凡的药水）
        event.getBuilder().addMix(
            Potions.WATER,
            TwilightMoonshine.MOON_STONE_SHARD.get(),
            TwilightMoonshine.FAINTLY_GLOWING_POTION
        );

        // 微微发亮的药水 + 荧光精华 → 荧光药水
        event.getBuilder().addMix(
            TwilightMoonshine.FAINTLY_GLOWING_POTION,
            TwilightMoonshine.GLOW_ESSENCE.get(),
            TwilightMoonshine.GLOW_POTION
        );

        // 荧光药水 + 红石 → 延长版荧光药水（无加强版）
        event.getBuilder().addMix(
            TwilightMoonshine.GLOW_POTION,
            Items.REDSTONE,
            TwilightMoonshine.LONG_GLOW_POTION
        );

        // 微微发亮的药水 + 暮色植物萃取液 → 暮色药水
        event.getBuilder().addMix(
            TwilightMoonshine.FAINTLY_GLOWING_POTION,
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
