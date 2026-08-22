package twilightmoonshine.event;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import twilightmoonshine.TwilightMoonshine;
import twilightmoonshine.data.RecipeKnowledge;

/**
 * 秘密配方的两条"渠道"获取机制（月兔喂食见 MoonRabbit.mobInteract）：
 * - 暮色动物渠道：处于暮色之拥（暮色药水）且 6 格内有暮色森林动物时，每 20 tick 摇 5%
 *   概率获得暮色荧光精华配方；连续吸引动物 60 秒保底到手（中途离开判定圈计时暂停）。
 *   摇中的重置计时。已掌握的配方不重复给予。
 * - 荧光引路渠道：玩家处于发光效果时，迷雾狼/游魂（"幽灵"）被吸引并变为中立靠近；
 *   它们进入玩家 4 格内即授予暮色合金配方。
 */
@EventBusSubscriber(modid = TwilightMoonshine.MODID, bus = EventBusSubscriber.Bus.GAME)
public class RecipeLearningEvents {

    /** 暮色动物渠道：动物判定半径（方块） */
    private static final double ANIMAL_RANGE = 6.0;
    /** 暮色动物渠道：每 20 tick（1 秒）摇一次奖 */
    private static final int ANIMAL_ROLL_TICKS = 20;
    /** 暮色动物渠道：单次摇奖概率 5%（1 分钟内期望约 1 - 0.95^60 ≈ 95%） */
    private static final float ESSENCE_CHANCE = 0.05F;
    /** 暮色动物渠道：连续满足条件的 60 秒后保底授予 */
    private static final int ANIMAL_FORCE_TICKS = 1200;

    /** 荧光引路渠道：召回半径（方块）与其停止跟随时的距离平方阈值（3 格） */
    private static final double LURE_RANGE = 16.0;
    private static final double LURE_STOP_SQR = 9.0;
    /** 荧光引路渠道：授予配方的接近半径（方块） */
    private static final double GIVE_RANGE = 4.0;

    /** 暮色动物渠道的计时期：玩家 UUID → 已连续满足条件且尚未领取的 tick 数 */
    private static final Map<UUID, Integer> ANIMAL_TICKS = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        // Post 在客户端也会触发，instanceof ServerPlayer 直接过滤掉客户端
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        checkEssenceFromAnimals(player);
        checkAlloyFromLuredMobs(player);
    }

    /** 暮色动物渠道：暮色之拥 + 6 格内有暮色森林动物 → 每 1 秒摇 5%，60 秒保底 */
    private static void checkEssenceFromAnimals(ServerPlayer player) {
        if (RecipeKnowledge.knows(player, RecipeKnowledge.GLOW_ESSENCE)) return;
        if (!player.hasEffect(TwilightMoonshine.TWILIGHT_EMBRACE)) return;
        AABB box = player.getBoundingBox().inflate(ANIMAL_RANGE);
        if (player.level().getEntitiesOfClass(Animal.class, box,
            e -> e.isAlive() && TwilightEmbraceEvents.isTwilightMob(e)).isEmpty()) {
            return; // 判定条件中断，计时暂停（仍在附近时从头累积）
        }

        int ticks = ANIMAL_TICKS.merge(player.getUUID(), 1, Integer::sum);
        if (ticks >= ANIMAL_FORCE_TICKS) {
            ANIMAL_TICKS.remove(player.getUUID());
            RecipeKnowledge.grant(player, RecipeKnowledge.GLOW_ESSENCE);
            return;
        }
        if (ticks % ANIMAL_ROLL_TICKS == 0
            && RecipeKnowledge.grantIfChance(player, RecipeKnowledge.GLOW_ESSENCE, ESSENCE_CHANCE)) {
            ANIMAL_TICKS.remove(player.getUUID());
        }
    }

    /** 荧光引路渠道：发光 + 4 格内有迷雾狼/游魂 → 授予暮色合金配方 */
    private static void checkAlloyFromLuredMobs(ServerPlayer player) {
        if (RecipeKnowledge.knows(player, RecipeKnowledge.ALLOY)) return;
        if (!player.hasEffect(MobEffects.GLOWING)) return;
        AABB box = player.getBoundingBox().inflate(GIVE_RANGE);
        if (player.level().getEntitiesOfClass(Mob.class, box,
            e -> e.isAlive() && isLureMob(e)).isEmpty()) {
            return;
        }
        // grant 对已掌握是幂等空操作，不会重复发通知
        RecipeKnowledge.grant(player, RecipeKnowledge.ALLOY);
    }

    /**
     * 发光期间迷雾狼/游魂把发光玩家设为目标时清空——"变为中立"，
     * 原有仇恨则由 onEntityTick 每 tick 清除（只拦得住新目标，清不掉旧目标）。
     */
    @SubscribeEvent
    public static void onTargetChange(LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!isLureMob(event.getEntity())) return;
        if (event.getNewAboutToBeSetTarget() instanceof LivingEntity target
            && target.hasEffect(MobEffects.GLOWING)) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    /** 每个 tick 清掉迷雾狼/游魂对发光玩家的旧目标（发光前就锁定的仇恨） */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) return;
        if (!isLureMob(mob)) return;
        if (mob.getTarget() instanceof LivingEntity target && target.hasEffect(MobEffects.GLOWING)) {
            mob.setTarget(null);
        }
    }

    /** 迷雾狼/游魂进入世界时挂上"被发光玩家吸引"的跟随目标（优先级 2） */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || event.getLevel().isClientSide()) return;
        if (isLureMob(mob)) {
            mob.goalSelector.addGoal(2, new GlowLureGoal(mob, 1.1));
        }
    }

    /** 服务器停止时清空计时，避免客户端残留数据 */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ANIMAL_TICKS.clear();
    }

    /** 是否是会被发光玩家吸引的暮色生物：迷雾狼（twilightforest:mist_wolf）或游魂（twilightforest:wraith） */
    private static boolean isLureMob(Entity entity) {
        if (!TwilightEmbraceEvents.isTwilightMob(entity)) return false;
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
        return path.equals("mist_wolf") || path.equals("wraith");
    }

    /** 最近的发光玩家（未旁观、同维度、16 格内）；与暮色之拥的跟随目标一样用 level.players() 搜索 */
    private static Player nearestGlowingPlayer(Mob mob) {
        Player best = null;
        double bestSqr = LURE_RANGE * LURE_RANGE;
        for (Player player : mob.level().players()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            if (!player.hasEffect(MobEffects.GLOWING)) continue;
            double distSqr = player.distanceToSqr(mob);
            if (distSqr < bestSqr) {
                bestSqr = distSqr;
                best = player;
            }
        }
        return best;
    }

    /**
     * 被发光玩家吸引的目标：有发光玩家在 16 格内就一直运行
     * （与暮色之拥的跟随目标同一思路，锁定 MOVE/LOOK 保持"中立靠近"）。
     * 途中玩家靠近到 4 格内交付配方后（或发光效果消失时），立即转为"离开"：
     * 朝远离玩家的方向走开约 12 格；离开途中仍保持中立（目标拦截见 onTargetChange /
     * onEntityTick），不会攻击玩家。对已经交付过配方的玩家不再吸引。
     */
    private static class GlowLureGoal extends Goal {

        /** 离开的最长持续时间（tick），防止目标点绕不过去时一直卡住 */
        private static final int DEPART_MAX_TICKS = 200;
        /** 离开点到玩家的距离（方块） */
        private static final double DEPART_DISTANCE = 12.0;

        private final Mob mob;
        private final double speedModifier;
        private Player target;
        /** 非空 = 离开阶段（配方已交付 / 效果已消失） */
        private Vec3 departPoint;
        private int departTicks;

        GlowLureGoal(Mob mob, double speedModifier) {
            this.mob = mob;
            this.speedModifier = speedModifier;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            this.target = nearestGlowingPlayer(this.mob);
            // 已交付过配方的玩家不再被吸引（交付后走开，不回头）
            return this.target != null
                && !(this.target instanceof ServerPlayer serverPlayer && RecipeKnowledge.knows(serverPlayer, RecipeKnowledge.ALLOY));
        }

        @Override
        public boolean canContinueToUse() {
            if (this.target == null || !this.target.isAlive()) return false;
            if (this.departPoint != null) {
                // 走到离开点或超过 10 秒后结束这场"离开"
                return this.departTicks++ < DEPART_MAX_TICKS && this.mob.distanceToSqr(this.departPoint) > 2.0;
            }
            // 配方已交付或发光效果消失 → 不再吸引，转为离开
            boolean delivered = this.target instanceof ServerPlayer serverPlayer
                && RecipeKnowledge.knows(serverPlayer, RecipeKnowledge.ALLOY);
            if (delivered || !this.target.hasEffect(MobEffects.GLOWING)) {
                this.startDepart();
            }
            return true;
        }

        @Override
        public void stop() {
            this.target = null;
            this.departPoint = null;
            this.mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (this.departPoint != null) {
                // 离开阶段：不再锁定玩家，直直走远（离开途中中立，不攻击玩家）
                this.mob.getNavigation().moveTo(this.departPoint.x, this.departPoint.y, this.departPoint.z, this.speedModifier);
                return;
            }
            if (this.target == null) return;
            this.mob.getLookControl().setLookAt(this.target, 10.0F, this.mob.getMaxHeadXRot());
            // 吸引到 3 格内就停下注视（近到 4 格内早已交付配方、转而离开）
            if (this.mob.distanceToSqr(this.target) > LURE_STOP_SQR) {
                this.mob.getNavigation().moveTo(this.target, this.speedModifier);
            } else {
                this.mob.getNavigation().stop();
            }
        }

        /** 从当前位置朝远离玩家的方向选一个离开点 */
        private void startDepart() {
            Vec3 offset = this.mob.position().subtract(this.target.position());
            if (offset.lengthSqr() < 0.01) {
                offset = new Vec3(1.0, 0.0, 0.0);
            } else {
                offset = offset.scale(DEPART_DISTANCE / offset.length());
            }
            this.departPoint = this.mob.position().add(offset);
            this.departTicks = 0;
        }
    }
}
