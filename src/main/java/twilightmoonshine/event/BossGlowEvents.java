package twilightmoonshine.event;

import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import twilightmoonshine.TwilightMoonshine;

/**
 * 暮色岗哨守卫：暮色八位 Boss 中的任意一只获得发光效果时，
 * 向 16 格内最近的玩家触发 boss_glowing 判据（分段进度，每只 Boss 一段）。
 */
@EventBusSubscriber(modid = TwilightMoonshine.MODID, bus = EventBusSubscriber.Bus.GAME)
public class BossGlowEvents {

    /** 岗哨守卫要覆盖的八位暮色 Boss 实体注册名 */
    private static final Set<ResourceLocation> TWILIGHT_BOSSES = Set.of(
        ResourceLocation.fromNamespaceAndPath("twilightforest", "naga"),
        ResourceLocation.fromNamespaceAndPath("twilightforest", "lich"),
        ResourceLocation.fromNamespaceAndPath("twilightforest", "minotaur"),
        ResourceLocation.fromNamespaceAndPath("twilightforest", "hydra"),
        ResourceLocation.fromNamespaceAndPath("twilightforest", "yeti"),
        ResourceLocation.fromNamespaceAndPath("twilightforest", "snow_queen"),
        ResourceLocation.fromNamespaceAndPath("twilightforest", "knight_phantom"),
        ResourceLocation.fromNamespaceAndPath("twilightforest", "ur_ghast"));

    /** 判据授予的检索半径（方块） */
    private static final double GIVE_RANGE = 16.0;

    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (!event.getEffectInstance().getEffect().is(MobEffects.GLOWING)) return;
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (key == null || !TWILIGHT_BOSSES.contains(key)) return;

        // 最近的玩家（未旁观、同维度、16 格内）作为该段的进度完成者
        Player best = null;
        double bestSqr = GIVE_RANGE * GIVE_RANGE;
        for (Player player : mob.level().players()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            double distSqr = player.distanceToSqr(mob);
            if (distSqr < bestSqr) {
                bestSqr = distSqr;
                best = player;
            }
        }
        if (best instanceof ServerPlayer serverPlayer) {
            TwilightMoonshine.BOSS_GLOWING_TRIGGER.get().trigger(serverPlayer, key);
        }
    }
}
