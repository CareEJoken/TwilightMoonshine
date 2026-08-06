package twilightmoonshine.client.renderer;

import net.minecraft.client.model.RabbitModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Rabbit;
import twilightmoonshine.TwilightMoonshine;

public class MoonRabbitModel extends RabbitModel<Rabbit> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath(TwilightMoonshine.MODID, "moon_rabbit"), "main");

    public MoonRabbitModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        // 几何数据来自 Blockbench 导出的 kayagurabbit（部件名/层级对齐原版 RabbitModel 以保留动画）
        partdefinition.addOrReplaceChild("left_hind_foot", CubeListBuilder.create().texOffs(8, 24).mirror()
            .addBox(-1.0F, 5.5F, -3.7F, 2.0F, 1.0F, 7.0F, new CubeDeformation(0.0F)).mirror(false),
            PartPose.offset(3.0F, 17.5F, 3.7F));

        partdefinition.addOrReplaceChild("right_hind_foot", CubeListBuilder.create().texOffs(26, 24).mirror()
            .addBox(-1.0F, 5.5F, -3.7F, 2.0F, 1.0F, 7.0F, new CubeDeformation(0.0F)).mirror(false),
            PartPose.offset(-3.0F, 17.5F, 3.7F));

        partdefinition.addOrReplaceChild("left_haunch", CubeListBuilder.create().texOffs(16, 15).mirror()
            .addBox(-1.0F, 0.0F, 0.0F, 2.0F, 4.0F, 5.0F, new CubeDeformation(0.0F)).mirror(false),
            PartPose.offsetAndRotation(3.0F, 17.5F, 3.7F, -0.3491F, 0.0F, 0.0F));

        partdefinition.addOrReplaceChild("right_haunch", CubeListBuilder.create().texOffs(30, 15).mirror()
            .addBox(-1.0F, 0.0F, 0.0F, 2.0F, 4.0F, 5.0F, new CubeDeformation(0.0F)).mirror(false),
            PartPose.offsetAndRotation(-3.0F, 17.5F, 3.7F, -0.3491F, 0.0F, 0.0F));

        partdefinition.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).mirror()
            .addBox(-3.0F, -2.0F, -10.0F, 6.0F, 5.0F, 10.0F, new CubeDeformation(0.0F)).mirror(false),
            PartPose.offsetAndRotation(0.0F, 19.0F, 8.0F, -0.3491F, 0.0F, 0.0F));

        partdefinition.addOrReplaceChild("left_front_leg", CubeListBuilder.create().texOffs(8, 15).mirror()
            .addBox(-1.0F, 0.0F, -1.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false),
            PartPose.offsetAndRotation(3.0F, 17.0F, -1.0F, -0.1745F, 0.0F, 0.0F));

        partdefinition.addOrReplaceChild("right_front_leg", CubeListBuilder.create().texOffs(0, 15).mirror()
            .addBox(-1.0F, 0.0F, -1.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false),
            PartPose.offsetAndRotation(-3.0F, 17.0F, -1.0F, -0.1745F, 0.0F, 0.0F));

        PartDefinition head = partdefinition.addOrReplaceChild("head", CubeListBuilder.create().texOffs(32, 0).mirror()
            .addBox(-2.5F, -4.0F, -5.0F, 5.0F, 4.0F, 5.0F, new CubeDeformation(0.0F)).mirror(false),
            PartPose.offset(0.0F, 16.0F, -1.0F));

        // 耳朵/鼻子挂到 root 下（原版层级，动画依赖）；几何/旋转来自用户模型
        partdefinition.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(58, 0).mirror()
            .addBox(-2.5F, -9.0F, -1.0F, 2.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false),
            PartPose.offsetAndRotation(0.0F, 16.0F, -1.0F, 0.0F, -0.2618F, 0.0F));

        partdefinition.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(52, 0).mirror()
            .addBox(0.5F, -9.0F, -1.0F, 2.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false),
            PartPose.offsetAndRotation(0.0F, 16.0F, -1.0F, 0.0F, 0.2618F, 0.0F));

        partdefinition.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(32, 9).mirror()
            .addBox(-0.5F, -2.5F, -5.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false),
            PartPose.offset(0.0F, 16.0F, -1.0F));

        partdefinition.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(52, 6).mirror()
            .addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false),
            PartPose.offsetAndRotation(0.0F, 20.0F, 7.0F, -0.3491F, 0.0F, 0.0F));

        // 用户的装饰方块：挂在 head 下（跟随头部动画）
        head.addOrReplaceChild("decoration", CubeListBuilder.create().texOffs(0, 1)
            .addBox(-1.5F, -15.0F, -5.0F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
            PartPose.offset(0.0F, 8.0F, 1.0F));

        return LayerDefinition.create(meshdefinition, 64, 32);
    }
}
