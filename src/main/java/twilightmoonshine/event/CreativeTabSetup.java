package twilightmoonshine.event;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import twilightmoonshine.TwilightMoonshine;
import twilightmoonshine.data.RecipeKnowledge;
import twilightmoonshine.item.SecretPageItem;

@EventBusSubscriber(modid = TwilightMoonshine.MODID, bus = EventBusSubscriber.Bus.MOD)
public class CreativeTabSetup {

    @SubscribeEvent
    public static void buildTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() != TwilightMoonshine.MOONSHINE_TAB.get()) return;

        event.accept(TwilightMoonshine.MOON_STONE_SHARD.get());
        event.accept(TwilightMoonshine.MOON_STONE_SHARD_PILE.get());
        event.accept(TwilightMoonshine.MOON_STONE_BRICK.get());
        event.accept(TwilightMoonshine.MOON_STONE_ITEM.get());
        event.accept(TwilightMoonshine.MOON_STONE_STAIRS_ITEM.get());
        event.accept(TwilightMoonshine.MOON_STONE_SLAB_ITEM.get());
        event.accept(TwilightMoonshine.MOON_STONE_WALL_ITEM.get());
        event.accept(TwilightMoonshine.GLOW_ESSENCE.get());
        event.accept(TwilightMoonshine.TWILIGHT_PLANT_EXTRACT.get());
        event.accept(TwilightMoonshine.TWILIGHT_ALLOY.get());

        // 神秘书页 — 三张绑好配方的"配方卡"（直接标注配方名 + 附魔光效，便于分辨；右键即可解锁）
        event.accept(SecretPageItem.recipeCard(RecipeKnowledge.PLANT_EXTRACT));
        event.accept(SecretPageItem.recipeCard(RecipeKnowledge.GLOW_ESSENCE));
        event.accept(SecretPageItem.recipeCard(RecipeKnowledge.ALLOY));

        // TODO 月之铃（未完成，临时禁用）：event.accept(TwilightMoonshine.MOON_BELL.get());
        event.accept(TwilightMoonshine.MOON_RABBIT_TROPHY.get());
        event.accept(TwilightMoonshine.MOON_RABBIT_SPAWN_EGG.get());

        // 抗性药水 — 酿造配方已移除，物品保留
        addPotionFamily(event,
            TwilightMoonshine.RESISTANCE_POTION,
            TwilightMoonshine.LONG_RESISTANCE_POTION,
            TwilightMoonshine.STRONG_RESISTANCE_POTION);

        // 微微发亮的药水 — 无效果基底，平凡药水式四种形态
        addPotionForms(event, TwilightMoonshine.FAINTLY_GLOWING_POTION);

        // 荧光药水 — 普通 + 延长，含喷溅/滞留形态（无强效、无药箭）
        event.accept(potionStack(Items.POTION, TwilightMoonshine.GLOW_POTION));
        event.accept(potionStack(Items.SPLASH_POTION, TwilightMoonshine.GLOW_POTION));
        event.accept(potionStack(Items.LINGERING_POTION, TwilightMoonshine.GLOW_POTION));
        event.accept(potionStack(Items.POTION, TwilightMoonshine.LONG_GLOW_POTION));
        event.accept(potionStack(Items.SPLASH_POTION, TwilightMoonshine.LONG_GLOW_POTION));
        event.accept(potionStack(Items.LINGERING_POTION, TwilightMoonshine.LONG_GLOW_POTION));

        // 暮色药水 — 普通 + 延长，含喷溅/滞留形态与药箭（无强效）
        event.accept(potionStack(Items.POTION, TwilightMoonshine.TWILIGHT_POTION));
        event.accept(potionStack(Items.SPLASH_POTION, TwilightMoonshine.TWILIGHT_POTION));
        event.accept(potionStack(Items.LINGERING_POTION, TwilightMoonshine.TWILIGHT_POTION));
        event.accept(potionStack(Items.TIPPED_ARROW, TwilightMoonshine.TWILIGHT_POTION));
        event.accept(potionStack(Items.POTION, TwilightMoonshine.LONG_TWILIGHT_POTION));
        event.accept(potionStack(Items.SPLASH_POTION, TwilightMoonshine.LONG_TWILIGHT_POTION));
        event.accept(potionStack(Items.LINGERING_POTION, TwilightMoonshine.LONG_TWILIGHT_POTION));
        event.accept(potionStack(Items.TIPPED_ARROW, TwilightMoonshine.LONG_TWILIGHT_POTION));

        // 月光私酿放在最后
        event.accept(TwilightMoonshine.MOONSHINE.get());
    }

    private static void addPotionFamily(BuildCreativeModeTabContentsEvent event,
                                        Holder<Potion> normal,
                                        Holder<Potion> longPotion,
                                        Holder<Potion> strong) {
        addPotionForms(event, normal);
        event.accept(potionStack(Items.POTION, longPotion));
        event.accept(potionStack(Items.SPLASH_POTION, longPotion));
        event.accept(potionStack(Items.LINGERING_POTION, longPotion));
        event.accept(potionStack(Items.TIPPED_ARROW, longPotion));
        event.accept(potionStack(Items.POTION, strong));
        event.accept(potionStack(Items.SPLASH_POTION, strong));
        event.accept(potionStack(Items.LINGERING_POTION, strong));
        event.accept(potionStack(Items.TIPPED_ARROW, strong));
    }

    private static void addPotionForms(BuildCreativeModeTabContentsEvent event, Holder<Potion> potion) {
        event.accept(potionStack(Items.POTION, potion));
        event.accept(potionStack(Items.SPLASH_POTION, potion));
        event.accept(potionStack(Items.LINGERING_POTION, potion));
        event.accept(potionStack(Items.TIPPED_ARROW, potion));
    }

    private static ItemStack potionStack(Item item, Holder<Potion> potion) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
        return stack;
    }
}
