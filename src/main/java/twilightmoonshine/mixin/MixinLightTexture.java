package twilightmoonshine.mixin;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import twilightmoonshine.moonlight.MoonlightState;

/**
 * "月光"的视觉亮度来源：直接放大光图（lightmap）的天光轴。
 *
 * 背景（对照 1.21.1 LightTexture.updateLightTexture 字节码）：
 * - 光图 16x16，外循环 i=天光等级，内循环 j=方块光等级；
 * - 天光轴 = getBrightness(dimensionType, sky) * darknessScale，再乘
 *   vec10=(darken,darken,1).lerp((1,1,1),0.35) 的颜色；
 * - 降低 float darken 会让 darknessScale 崩掉，整个光图变暗（曾导致
 *   "月亮出现地表反而变暗"），所以不能再动 getSkyDarken(float)。
 * 这里改为把天光等级按比例放大：天光 4 → 10（"亮度用10"），
 * 天光 0 保持 0（地下不会发亮），方块光轴完全不动（火把亮度不受影响）。
 */
@Mixin(LightTexture.class)
public abstract class MixinLightTexture {

    /** 天光放大倍数：TF 锁定时间下天光 4，×2.5 = 10 */
    private static final float SKY_BRIGHTEN = 2.5F;

    /**
     * updateLightTexture 中两次调用 getBrightness(DimensionType,int)：
     * ordinal 0 是外循环的天光轴（×darknessScale），ordinal 1 是内循环的方块光轴。
     * 只重定向第 0 次。
     */
    @Redirect(method = "updateLightTexture(F)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LightTexture;getBrightness(Lnet/minecraft/world/level/dimension/DimensionType;I)F",
            ordinal = 0))
    private static float twilightmoonshine$brightenSkyLight(DimensionType dimensionType, int lightLevel) {
        if (MoonlightState.isClientActive()) {
            lightLevel = Math.min(15, Math.round(lightLevel * SKY_BRIGHTEN));
        }
        return LightTexture.getBrightness(dimensionType, lightLevel);
    }
}
