package twilightmoonshine.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.item.ItemDisplayContext;
import twilightmoonshine.TwilightMoonshine;

/**
 * 月兔战利品模型 — 只渲染月兔的头部（含耳朵/鼻子/额饰）。
 * 几何数据与 MoonRabbitModel 的头部完全一致，只是把所有部件挂到 head 下，
 * 并整体上移到原点（仿 TF createJappaTrophy 的做法），方便战利品渲染定位。
 */
public class MoonRabbitTrophyModel extends HierarchicalModel<Rabbit> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath(TwilightMoonshine.MODID, "moon_rabbit_trophy"), "main");

    private final ModelPart root;
    private final ModelPart head;

    public MoonRabbitTrophyModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(Rabbit entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // 战利品不需要动画
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        // 头部整体：战利品渲染约定 block_y = -model_y/16，所以让头部跨 model y -9..0
        // （兔耳顶 -9，头底部 0 → 配合 1.25 缩放占方块内 y 0..~0.7，与 TF 谜题羊一致）
        // 关键：ModelPart 绕部件原点（PartPose 所在点）旋转 —— 原点就是 yaw 的轴心。
        // 头盒必须以部件原点为中心（z -2.5..2.5），否则地面版旋转时头部会绕盒子的
        // 后边缘甩出去（朝北居中、朝南偏出 ~5px），墙版也会四面墙各不同。
        // 子部件 z 已按新轴心重新对齐，模型渲染观感与之前完全一致。
        PartDefinition head = partdefinition.addOrReplaceChild("head", CubeListBuilder.create().texOffs(32, 0)
                .addBox(-2.5F, -4.0F, -2.5F, 5.0F, 4.0F, 5.0F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        // 额饰（挂在 head 下，跟随头部旋转）
        head.addOrReplaceChild("decoration", CubeListBuilder.create().texOffs(0, 1)
                .addBox(-1.5F, -15.0F, -1.5F, 3.0F, 3.0F, 1.0F),
            PartPose.offset(0.0F, 8.0F, 0.0F));

        // 耳朵/鼻子在实体模型里与 head 同位置挂在 root 下，这里改挂到 head 下
        head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(58, 0)
                .addBox(-2.5F, -9.0F, 1.5F, 2.0F, 5.0F, 1.0F),
            PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.2618F, 0.0F));

        head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(52, 0)
                .addBox(0.5F, -9.0F, 1.5F, 2.0F, 5.0F, 1.0F),
            PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.2618F, 0.0F));

        head.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(32, 9)
                .addBox(-0.5F, -2.5F, -3.0F, 1.0F, 1.0F, 1.0F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 32);
    }

    public void setupRotationsForTrophy(float x, float y, float z, float mouthAngle) {
        this.head.yRot = y * Mth.DEG_TO_RAD;
        this.head.xRot = z * Mth.DEG_TO_RAD;
    }

    public void renderTrophy(PoseStack stack, MultiBufferSource buffer, int light, int overlay, int color, ItemDisplayContext context) {
        // 模型已按 TF 战利品约定建模（z 相对原点对称、头底贴 y=0），
        // 定位全部由 TrophyRenderer/ISTER 的通用变换链完成，这里只做缩放。
        // 若需微调物品形态，可仿 TF 谜题羊按 context 加小位移（如 context != NONE 时 translate z +0.5）。
        stack.scale(1.25F, 1.25F, 1.25F);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(MoonRabbitRenderer.TEXTURE));
        this.head.render(stack, consumer, light, overlay, color);
    }
}
