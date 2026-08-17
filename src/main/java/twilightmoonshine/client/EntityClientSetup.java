package twilightmoonshine.client;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import twilightmoonshine.TwilightMoonshine;
import twilightmoonshine.client.renderer.MoonRabbitModel;
import twilightmoonshine.client.renderer.MoonRabbitRenderer;
import twilightmoonshine.client.renderer.MoonRabbitTrophyModel;
import twilightmoonshine.client.renderer.MoonRabbitTrophyRenderer;

@EventBusSubscriber(modid = TwilightMoonshine.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class EntityClientSetup {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TwilightMoonshine.MOON_RABBIT.value(), MoonRabbitRenderer::new);
        event.registerBlockEntityRenderer(TwilightMoonshine.MOON_RABBIT_TROPHY_BLOCK_ENTITY.get(), MoonRabbitTrophyRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MoonRabbitModel.LAYER_LOCATION, MoonRabbitModel::createBodyLayer);
        event.registerLayerDefinition(MoonRabbitTrophyModel.LAYER_LOCATION, MoonRabbitTrophyModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(MoonRabbitISTER.CLIENT_ITEM_EXTENSION, TwilightMoonshine.MOON_RABBIT_TROPHY.get());
    }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        // 战利品物品图标的月亮蓝底板（ISTER 以 standalone 变体获取，
        // 不注册会回退到 missing 模型 → 黑紫色方块错误）
        event.register(ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(TwilightMoonshine.MODID, "item/moon_rabbit_trophy_back")));
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        // 药水液体颜色来自效果颜色：发光(0x94A061 黄绿)与暮色之拥(0x7BBF4C 绿)太接近，
        // 看起来像同一张贴图。这里把荧光药水覆盖成亮黄色，其余药水保持原版着色。
        event.register((stack, tintIndex) -> {
            if (tintIndex == 0) {
                PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
                if (contents.potion()
                    .map(holder -> holder.is(TwilightMoonshine.GLOW_POTION) || holder.is(TwilightMoonshine.LONG_GLOW_POTION))
                    .orElse(false)) {
                    return FastColor.ARGB32.color(255, 255, 255, 85);
                }
                return contents.getColor();
            }
            return -1;
        }, Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION, Items.TIPPED_ARROW);
    }
}
