package twilightmoonshine.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Rabbit;

public class MoonRabbitRenderer extends MobRenderer<Rabbit, MoonRabbitModel> {

    private static final ResourceLocation TEXTURE =
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
        poseStack.scale(2.0F, 2.0F, 2.0F);
    }
}
