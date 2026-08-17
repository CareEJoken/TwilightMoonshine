package twilightmoonshine.mixin;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import twilightmoonshine.moonlight.MoonlightState;

/**
 * 月光激活时压低天空变暗值（11 → 5），天光由 4 升到 10。
 * 服务端影响真实光照（刷怪判定、作物生长），客户端保持数值一致。
 * 只在天光通道生效，方块光（火把等）不受影响。
 */
@Mixin(Level.class)
public abstract class MixinLevel {

    @Inject(method = "getSkyDarken", at = @At("RETURN"), cancellable = true)
    private void twilightmoonshine$moonlightBrightness(CallbackInfoReturnable<Integer> cir) {
        Level level = (Level) (Object) this;
        cir.setReturnValue(MoonlightState.adjustSkyDarken(level, cir.getReturnValueI()));
    }
}
