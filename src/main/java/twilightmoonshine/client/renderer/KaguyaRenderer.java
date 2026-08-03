package twilightmoonshine.client.renderer;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.allay.Allay;

public class KaguyaRenderer extends MobRenderer<Allay, KaguyaModel> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/allay/allay.png");

    public KaguyaRenderer(EntityRendererProvider.Context context) {
        super(context, new KaguyaModel(context.bakeLayer(ModelLayers.ALLAY)), 1.6F);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(Allay entity) {
        return TEXTURE;
    }

    @Override
    protected int getBlockLightLevel(Allay entity, BlockPos pos) {
        return 15;
    }
}
