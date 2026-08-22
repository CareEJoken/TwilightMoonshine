package twilightmoonshine.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

/**
 * 简单判据触发器：条件检测在 EmbraceAdvancementEvents 里完成（暮色之拥 + 身边生物），
 * 满足时调用 trigger() 授予进度。判据 JSON 只需引用触发器 ID：
 * embrace_hug（拥抱日） / embrace_beetles（可爱的暮色虫子们）。
 */
public class EmbraceProximityTrigger extends SimpleCriterionTrigger<EmbraceProximityTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player) {
        this.trigger(player, instance -> true);
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player)
        ).apply(instance, TriggerInstance::new));
    }
}
