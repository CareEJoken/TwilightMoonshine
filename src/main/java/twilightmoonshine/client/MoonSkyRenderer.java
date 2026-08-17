package twilightmoonshine.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * 在暮色森林天空盒上绘制一轮满月（原版 moon_phases 纹理第 0 帧）。
 * 做法完全对照 1.21.1 原版 renderSky 画月亮的部分（已逐条核对字节码）：
 * - 顶点只乘模型视图矩阵（不含投影），投影由 BufferUploader 提交时从
 *   RenderSystem 状态读取；AFTER_SKY 阶段原版尚未 applyModelViewMatrix，
 *   天空绘制正是依赖这一点，此处同样处理；
 * - 无雾、不写深度，在天空之后绘制，因此随后绘制的地形会自然遮挡月亮。
 */
public class MoonSkyRenderer {

    private static final ResourceLocation MOON_PHASES =
        ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");

    /**
     * 月亮方位角。pose = RY(-90°)·RX(角度)，顶点画在 y=-100 处：
     * 旋转后月亮方向 = (sinθ, -cosθ, 0)，θ=180° 时恰好在正头顶（天顶）。
     * 对照原版：timeOfDay=0.5（午夜）时月亮也正是 180°，即天顶。
     */
    private static final float MOON_ANGLE = 180.0F;

    /** 月亮四边形边长（原版为 20，取 30 更醒目） */
    private static final float MOON_SIZE = 30.0F;

    public static void render(RenderLevelStageEvent event) {
        PoseStack stack = new PoseStack();
        stack.mulPose(event.getModelViewMatrix());
        stack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        stack.mulPose(Axis.XP.rotationDegrees(MOON_ANGLE));
        Matrix4f mat = stack.last().pose();

        // 原版日月不受雾影响（TF 的星星也是 setupNoFog 后绘制）
        FogRenderer.setupNoFog();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        // 原版日月同款加色混合：贴图黑底像素加 0 不可见，月亮呈白色发光体，四周没有黑框
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, MOON_PHASES);

        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        // 满月 = 纹理第 0 帧（u 0~0.25，v 0~0.5），顶点顺序与 UV 照抄原版月亮的画法
        builder.addVertex(mat, -MOON_SIZE, -100.0F, MOON_SIZE).setUv(0.25F, 0.5F);
        builder.addVertex(mat, MOON_SIZE, -100.0F, MOON_SIZE).setUv(0.0F, 0.5F);
        builder.addVertex(mat, MOON_SIZE, -100.0F, -MOON_SIZE).setUv(0.0F, 0.0F);
        builder.addVertex(mat, -MOON_SIZE, -100.0F, -MOON_SIZE).setUv(0.25F, 0.0F);
        BufferUploader.drawWithShader(builder.buildOrThrow());

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        // 雾状态由后续的地形渲染自行重新设置，这里不恢复
    }
}
