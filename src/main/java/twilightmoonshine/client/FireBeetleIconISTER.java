package twilightmoonshine.client;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import twilightforest.client.model.entity.FireBeetleModel;
import twilightmoonshine.TwilightMoonshine;

import java.util.function.Supplier;

/**
 * 喷火甲虫图标物品渲染器（"夕阳甲虫乐队"进度图标用）：
 * GUI 里以 30° 俯角 + 缓慢自转渲染 TF 喷火甲虫 3D 模型，仿月兔战利品 ISTER。
 */
public class FireBeetleIconISTER extends BlockEntityWithoutLevelRenderer {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath("twilightforest", "textures/entity/firebeetle.png");

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath(TwilightMoonshine.MODID, "fire_beetle_icon"), "main");

    public static final Supplier<FireBeetleIconISTER> INSTANCE = Suppliers.memoize(FireBeetleIconISTER::new);

    public static final IClientItemExtensions CLIENT_ITEM_EXTENSION = Util.make(() -> new IClientItemExtensions() {
        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return INSTANCE.get();
        }
    });

    private FireBeetleModel beetle;

    private FireBeetleIconISTER() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.beetle = new FireBeetleModel(Minecraft.getInstance().getEntityModels().bakeLayer(LAYER_LOCATION));
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        this.beetle = new FireBeetleModel(Minecraft.getInstance().getEntityModels().bakeLayer(LAYER_LOCATION));
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext camera, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        if (camera == ItemDisplayContext.GUI) {
            Lighting.setupFor3DItems();
            pose.pushPose();
            // 最终画面空间微调：甲虫整体再往左上方挪 ~2.4px（0.15 单位），图标居中余量
            pose.translate(0.05F, 17.5F/16.0F, 0.0F);
            // pose.translate(0.5F, 0.5F, 0.5F);          // 图标中心
            pose.mulPose(Axis.XP.rotationDegrees(30));   // 30° 俯角
            // 与月兔战利品相同的 tick 计时旋转（20°/秒，暂停时停在 -45°）
            pose.mulPose(Axis.YN.rotationDegrees(!minecraft.isPaused() ? ClientGameEvents.time % 360 : -45));
            //pose.translate(-0.5F, -0.5F, -0.5F);       // 旋转后回到模型坐标系原点
            //pose.translate(0.0F, 0.25F, 0.0F);         // 抬高 0.25（同"完美的羊"谜题羊奖杯图标）
            pose.scale(0.6F, 0.6F, 0.6F);                // 甲虫宽约 26px，缩到 15.6px 适配 16px 图标
            this.renderBeetle(pose, buffers, light);
            pose.popPose();
        } else {
            this.renderBeetle(pose, buffers, light);
        }
    }

    private void renderBeetle(PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        // 180°：正面朝屏幕（同"完美的羊"奖杯 GUI 的 180° 朝向），否则甲虫是背对着观众
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.scale(-1.0F, -1.0F, 1.0F);
        // 参考"完美的羊"奖杯的做法，模型中心才是旋转中心；TF 甲虫模型几何全部位于
        // y≈[13,22]（模型单位）的"底板上方"，把几何中心平移回原点，图标才真正居中
        pose.translate(0.0F,  0.0F, 0.0F);
        this.beetle.setupAnim(null, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        this.beetle.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
