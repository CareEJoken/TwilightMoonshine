package twilightmoonshine.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 暮色岗哨守卫：某只暮色 Boss 获得发光效果时触发。
 * 判据 JSON 的 conditions.boss 指定 Boss 的实体注册名（twilightforest:naga 等），
 * 分段进度给每只 Boss 各写一个 criterion；触发时刻取最近的非旁观玩家
 * 作为该段的进度完成者（见 BossGlowEvents）。
 */
public class BossGlowingTrigger extends SimpleCriterionTrigger<BossGlowingTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ResourceLocation boss) {
        this.trigger(player, instance -> instance.matches(boss));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, ResourceLocation boss)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
            ResourceLocation.CODEC.fieldOf("boss").forGetter(TriggerInstance::boss)
        ).apply(instance, TriggerInstance::new));

        public boolean matches(ResourceLocation granted) {
            return this.boss.equals(granted);
        }
    }
}
