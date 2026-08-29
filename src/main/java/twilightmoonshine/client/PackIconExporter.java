package twilightmoonshine.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import twilightmoonshine.TwilightMoonshine;

import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.file.Path;

/**
 * pack.png 导出器（客户端命令 /tm-pack-icon）：
 * 在 512×512 离屏 RenderTarget 上，按物品栏 GUI 图标同款管线渲染
 * 月兔战利品（月亮蓝底板 + 30° 俯角兔头），回读像素后写为 pack.png。
 * 暂停游戏再执行可获得兔头 -45° 静止帧。
 */
@EventBusSubscriber(modid = TwilightMoonshine.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class PackIconExporter {

    private static final int SIZE = 512;
    private static final Path OUTPUT =
        Path.of("D:/twilight forest/TwilightMoonshine/src/main/resources/pack.png");

    private PackIconExporter() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("tm-pack-icon")
            .executes(ctx -> {
                try {
                    exportIcon();
                    ctx.getSource().sendSuccess(
                        () -> Component.literal("pack.png 已导出 → " + OUTPUT.toAbsolutePath()), false);
                } catch (Throwable t) {
                    ctx.getSource().sendFailure(
                        Component.literal("pack.png 导出失败：" + t.getMessage()));
                }
                return 1;
            }));
    }

    /**
     * 2×2 全白 lightmap 纹理：实体/3D 物品着色器从 GL 纹理单元 2 采样 lightmap，
     * GUI 会话里没有绑定 → 采样全黑 → 图标发暗。绑全白 = 恒定满亮度。
     */
    private static int createWhiteLightmap() {
        IntBuffer pixels = BufferUtils.createIntBuffer(4);
        for (int i = 0; i < 4; i++) pixels.put(0xFFFFFFFF);
        pixels.rewind();
        int id = GlStateManager._genTexture();
        GlStateManager._bindTexture(id);
        GlStateManager._texParameter(3553, 10241, 9728);  // MIN NEAREST
        GlStateManager._texParameter(3553, 10240, 9728);  // MAG NEAREST
        GlStateManager._texParameter(3553, 10242, 33071); // WRAP CLAMP_TO_EDGE
        GlStateManager._texParameter(3553, 10243, 33071);
        GlStateManager._texImage2D(3553, 0, 6408, 2, 2, 0, 6408, 5121, pixels);
        return id;
    }

    private static void exportIcon() throws IOException {
        Minecraft mc = Minecraft.getInstance();

        // 复刻 GameRenderer GUI 会话矩阵（正交投影 + 模型视图平移），
        // 保证图标 z≈150 落在 GUI 深度窗口内，与物品栏渲染完全一致
        float farPlane = ClientHooks.getGuiFarPlane();
        RenderSystem.setProjectionMatrix(
            new Matrix4f().setOrtho(0.0F, SIZE, SIZE, 0.0F, 1000.0F, farPlane),
            VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().translation(0.0F, 0.0F, 10000.0F - farPlane);
        RenderSystem.applyModelViewMatrix();

        TextureTarget target = null;
        try {
            target = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
            target.setClearColor(0.0F, 0.0F, 0.0F, 1.0F); // 黑色不透明底
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);

            int whiteLightmap = createWhiteLightmap();
            try {
                // 绑到单元 2（实体着色器 lightmap 采样点）
                GlStateManager._activeTexture(0x84C2); // GL_TEXTURE2
                GlStateManager._bindTexture(whiteLightmap);
                GlStateManager._activeTexture(0x84C0); // 恢复单元 0

                Lighting.setupFor3DItems();
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

                GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
                graphics.pose().pushPose();
                // 放大 32 倍：16px 图标 → 512px 画布；renderItem 内部自带 scale(16,-16,16)，
                // 此处 y 不取负（取负会与内部的负叠加，导致上下颠倒）
                graphics.pose().translate(SIZE / 2.0F, SIZE / 2.0F, 0.0F);
                graphics.pose().scale(32.0F, 32.0F, 1.0F);
                graphics.renderItem(new ItemStack(TwilightMoonshine.MOON_RABBIT_TROPHY.get()), -8, -8);
                graphics.pose().popPose();
                graphics.flush();

                // 回读：不强制 alpha=255（保留贴图透明角），flipY 把 GL 行序转 PNG 行序
                NativeImage image = new NativeImage(SIZE, SIZE, false);
                try {
                    RenderSystem.bindTexture(target.getColorTextureId());
                    image.downloadTexture(0, false);
                    image.flipY();
                    image.writeToFile(OUTPUT);
                } finally {
                    image.close();
                }
            } finally {
                GlStateManager._activeTexture(0x84C2);
                GlStateManager._bindTexture(0); // 解绑单元 2
                GlStateManager._activeTexture(0x84C0);
                TextureUtil.releaseTextureId(whiteLightmap);
            }
        } finally {
            if (target != null) target.destroyBuffers();
            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.applyModelViewMatrix();
            mc.getMainRenderTarget().bindWrite(true);
        }
    }
}
