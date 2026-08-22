package twilightmoonshine.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 处方判据触发器：玩家"学会"了一份秘密配方（RecipeKnowledge.grant 成功后触发）。
 * 判据 JSON 里用 conditions.recipes 列出该判据需要的配方 ID：
 * 例如 hand_to_hand（三个判据各一张配方）与 glow_allure（暮色合金粉末配方）。
 */
public class RecipeLearnedTrigger extends SimpleCriterionTrigger<RecipeLearnedTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ResourceLocation recipe) {
        this.trigger(player, instance -> instance.matches(recipe));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, List<ResourceLocation> recipes)
        implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
            ResourceLocation.CODEC.listOf().fieldOf("recipes").forGetter(TriggerInstance::recipes)
        ).apply(instance, TriggerInstance::new));

        public boolean matches(ResourceLocation granted) {
            return this.recipes.contains(granted);
        }
    }
}
