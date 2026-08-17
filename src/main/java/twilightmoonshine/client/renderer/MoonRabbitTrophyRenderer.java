package twilightmoonshine.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import twilightmoonshine.block.MoonRabbitTrophyBlock;
import twilightmoonshine.block.MoonRabbitTrophyWallBlock;
import twilightmoonshine.block.entity.MoonRabbitTrophyBlockEntity;

/**
 * 月兔战利品渲染器 — 变换逻辑与暮色森林 TrophyRenderer 一致：
 * 地面版按 ROTATION_16 旋转，墙上版按 FACING 贴墙，GUI 里给 0.35 的仰角。
 */
public class MoonRabbitTrophyRenderer implements BlockEntityRenderer<MoonRabbitTrophyBlockEntity> {

    private final MoonRabbitTrophyModel model;

    public MoonRabbitTrophyRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new MoonRabbitTrophyModel(context.getModelSet().bakeLayer(MoonRabbitTrophyModel.LAYER_LOCATION));
    }

    @Override
    public void render(MoonRabbitTrophyBlockEntity entity, float partialTicks, PoseStack stack, MultiBufferSource buffer, int light, int overlay) {
        BlockState blockstate = entity.getBlockState();
        boolean flag = blockstate.getBlock() instanceof MoonRabbitTrophyWallBlock;
        Direction direction = flag ? blockstate.getValue(MoonRabbitTrophyWallBlock.FACING) : null;
        float f1 = 22.5F * (flag ? (2 + direction.get2DDataValue()) * 4 : blockstate.getValue(MoonRabbitTrophyBlock.ROTATION));
        render(direction, f1, this.model, stack, buffer, light, ItemDisplayContext.NONE);
    }

    public static void render(@Nullable Direction direction, float y, MoonRabbitTrophyModel model, PoseStack stack, MultiBufferSource buffer, int light, ItemDisplayContext context) {
        stack.pushPose();
        if (direction == null) {
            stack.translate(0.5D, 0.0D, 0.5D);
        } else {
            stack.translate(0.5F - direction.getStepX() * 0.249F, 0.25D, 0.5F - direction.getStepZ() * 0.249F);
        }
        stack.scale(-1.0F, -1.0F, 1.0F);
        model.setupRotationsForTrophy(0.0F, y, 0.0F, context == ItemDisplayContext.GUI ? 0.35F : direction != null ? 0.5F : 0.0F);
        model.renderTrophy(stack, buffer, light, OverlayTexture.NO_OVERLAY, -1, context);
        stack.popPose();
    }
}
