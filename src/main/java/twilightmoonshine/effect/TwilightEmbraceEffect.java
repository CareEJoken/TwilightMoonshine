package twilightmoonshine.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 暮色之拥效果 — 自身没有逐 tick 逻辑，只是一个标记效果：
 * 具体行为（暮色森林友好生物跟随 / 敌对生物友好）由 TwilightEmbraceEvents 监听事件实现。
 */
public class TwilightEmbraceEffect extends MobEffect {

    public TwilightEmbraceEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false;
    }
}
