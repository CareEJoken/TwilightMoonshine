package twilightmoonshine.moonlight;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import twilightmoonshine.TwilightMoonshine;

/**
 * "唤月"状态的共享逻辑：
 * - 只在暮色森林维度生效
 * - 服务端状态存于 MoonlightWorldData（随存档持久化），客户端由广播同步（clientActive）
 * - 视觉亮度：客户端由 MixinLightTexture 直接放大光图天光轴（天光 4→10），
 *   不能走压低 getSkyDarken(float) 的路子（会让光图整体变暗）；
 * - 逻辑亮度：服务端 MixinLevel 压低 getSkyDarken 整数值（11→5，天光 10），
 *   作用于刷怪等判定路径
 */
public final class MoonlightState {

    /** 天空变暗值减量：TF 锁定时间下 darken=11（天光 4），减 6 后天光 = 10 */
    public static final int DARKEN_REDUCTION = 6;

    /** 暮色森林维度注册键 */
    private static final ResourceKey<Level> TF_DIMENSION = ResourceKey.create(Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath("twilightforest", "twilight_forest"));

    /** 客户端标志：由服务端 MoonlightSyncPayload 同步 */
    private static boolean clientActive;

    private MoonlightState() {
    }

    public static boolean isTwilightForest(Level level) {
        return level.dimension() == TF_DIMENSION;
    }

    public static boolean isClientActive() {
        return clientActive;
    }

    public static void setClientActive(boolean active) {
        MoonlightState.clientActive = active;
    }

    /** 该世界是否处于"月光"状态（客户端读标志，服务端读存档数据） */
    public static boolean isActive(Level level) {
        if (level.isClientSide) {
            return clientActive;
        }
        if (level instanceof ServerLevel serverLevel) {
            return MoonlightWorldData.get(serverLevel).isActive();
        }
        return false;
    }

    /** 天空变暗值的统一入口（mixin Level.getSkyDarken 调用） */
    public static int adjustSkyDarken(Level level, int darken) {
        if (!isTwilightForest(level) || !isActive(level)) return darken;
        return Math.max(0, darken - DARKEN_REDUCTION);
    }
}
