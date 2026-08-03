package twilightmoonshine.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.AllayModel;
import net.minecraft.client.model.geom.ModelPart;

public class KaguyaModel extends AllayModel {

    public KaguyaModel(ModelPart root) {
        super(root);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer,
                                int packedLight, int packedOverlay, int color) {
        poseStack.pushPose();
        poseStack.scale(4.0F, 4.0F, 4.0F);
        super.renderToBuffer(poseStack, consumer, packedLight, packedOverlay, color);
        poseStack.popPose();
    }
}
