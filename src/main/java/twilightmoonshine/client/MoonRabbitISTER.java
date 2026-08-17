package twilightmoonshine.client;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import twilightmoonshine.TwilightMoonshine;
import twilightmoonshine.block.MoonRabbitTrophyBlock;
import twilightmoonshine.client.renderer.MoonRabbitTrophyModel;
import twilightmoonshine.client.renderer.MoonRabbitTrophyRenderer;

import java.util.function.Supplier;

/**
 * 月兔战利品的物品渲染器（仿暮色森林 ISTER）：
 * GUI 里先画一层月亮蓝底板，再画一个 30° 俯角的等距 3D 兔头并缓慢旋转，
 * 创造物品栏 / 物品栏图标即由此获得 ISO 视角效果。
 */
public class MoonRabbitISTER extends BlockEntityWithoutLevelRenderer {

    public static final Supplier<MoonRabbitISTER> INSTANCE = Suppliers.memoize(MoonRabbitISTER::new);

    public static final IClientItemExtensions CLIENT_ITEM_EXTENSION = Util.make(() -> new IClientItemExtensions() {
        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return INSTANCE.get();
        }
    });

    private final ItemStack backdropStack = new ItemStack(TwilightMoonshine.MOON_RABBIT_TROPHY.get());
    private MoonRabbitTrophyModel trophy;

    private MoonRabbitISTER() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.trophy = new MoonRabbitTrophyModel(Minecraft.getInstance().getEntityModels().bakeLayer(MoonRabbitTrophyModel.LAYER_LOCATION));
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        this.trophy = new MoonRabbitTrophyModel(Minecraft.getInstance().getEntityModels().bakeLayer(MoonRabbitTrophyModel.LAYER_LOCATION));
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext camera, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof MoonRabbitTrophyBlock) {
            Minecraft minecraft = Minecraft.getInstance();
            if (camera == ItemDisplayContext.GUI) {
                // 底板：月亮蓝战利品背板（平面 item 模型）
                ModelResourceLocation back = ModelResourceLocation.standalone(
                    ResourceLocation.fromNamespaceAndPath(TwilightMoonshine.MODID, "item/moon_rabbit_trophy_back"));
                BakedModel modelBack = minecraft.getItemRenderer().getItemModelShaper().getModelManager().getModel(back);

                Lighting.setupForFlatItems();
                MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
                pose.pushPose();
                Lighting.setupForFlatItems();
                pose.translate(0.5F, 0.5F, -1.5F);
                minecraft.getItemRenderer().render(this.backdropStack, ItemDisplayContext.GUI, false, pose, bufferSource,
                    15728880, OverlayTexture.NO_OVERLAY, modelBack.applyTransform(camera, pose, false));
                pose.popPose();
                bufferSource.endBatch();
                Lighting.setupFor3DItems();

                // 3D 兔头：30° 俯角 + 缓慢自转
                pose.pushPose();
                pose.translate(0.5F, 0.5F, 0.5F);
                pose.mulPose(Axis.XP.rotationDegrees(30));
                // 与 TF ISTER 相同的 tick 计时旋转（20°/秒，暂停时停在 -45°）
                pose.mulPose(Axis.YN.rotationDegrees(!minecraft.isPaused() ? ClientGameEvents.time % 360 : -45));
                pose.translate(-0.5F, -0.5F, -0.5F);
                pose.translate(0.0F, 0.25F, 0.0F);
                MoonRabbitTrophyRenderer.render(null, 180.0F, this.trophy, pose, buffers, light, camera);
                pose.popPose();
            } else {
                MoonRabbitTrophyRenderer.render(null, 180.0F, this.trophy, pose, buffers, light, camera);
            }
        }
    }
}
