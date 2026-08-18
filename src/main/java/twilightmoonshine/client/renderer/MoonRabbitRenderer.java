package twilightmoonshine.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Rabbit;
import twilightmoonshine.entity.MoonRabbit;

public class MoonRabbitRenderer extends MobRenderer<Rabbit, MoonRabbitModel> {

    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("twilightmoonshine", "textures/entity/moon_rabbit/moon_rabbit.png");

    public MoonRabbitRenderer(EntityRendererProvider.Context context) {
        super(context, new MoonRabbitModel(context.bakeLayer(MoonRabbitModel.LAYER_LOCATION)), 0.6F);
    }

    @Override
    public ResourceLocation getTextureLocation(Rabbit entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(Rabbit entity, PoseStack poseStack, float partialTick) {
        // 只应用基础 2.0 倍（月兔本体体型）。
        // 膨胀等级由 getScale() 驱动：1.21.1 原版 LivingEntityRenderer.render
        // 会自动乘上 getScale()（0 级 ×1.0 → 4 级 ×2.0，客户端带平滑动画），
        // 此处再乘会导致双重放大。
        poseStack.scale(2.0F, 2.0F, 2.0F);
    }
}
