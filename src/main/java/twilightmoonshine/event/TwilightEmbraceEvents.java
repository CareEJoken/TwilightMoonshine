package twilightmoonshine.event;

import java.util.EnumSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.extensions.IEntityExtension;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import twilightmoonshine.TwilightMoonshine;

/**
 * 暮色之拥效果的行为实现：
 * - 暮色森林的敌对生物（Monster）不会攻击拥有该效果的玩家（"变友好"）
 * - 暮色森林的友好生物（Animal）会跟随拥有该效果的玩家
 * - 带效果的玩家主动攻击某只暮色生物后，该生物永久"破中立"：
 *   敌对生物开始正常反击、动物不再跟随（标记记录在生物 NBT，随存档保存）
 * 只作用于 twilightforest 命名空间的生物，不影响原版生物。
 */
@EventBusSubscriber(modid = TwilightMoonshine.MODID, bus = EventBusSubscriber.Bus.GAME)
public class TwilightEmbraceEvents {

    /** 效果作用半径（方块） */
    private static final double CHARM_RANGE = 32.0;
    private static final double CHARM_RANGE_SQR = CHARM_RANGE * CHARM_RANGE;
    /** 跟随的距离平方阈值：比这个更近就不再靠近 */
    private static final double FOLLOW_STOP_SQR = 6.0;
    /** 生物 NBT 中"已被带效果玩家攻击过"的标记键 */
    private static final String AGGROED_KEY = "TwilightMoonshineAggroed";

    /**
     * 给暮色森林的友好生物挂上跟随目标（优先级 2）。
     * 高于它们的躲避玩家目标（3/4）——否则会出现"靠近你又跑开"的来回横跳；
     * 低于惊慌目标（1）——被攻击时仍会先逃跑。
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof Animal animal && isTwilightMob(animal)) {
            animal.goalSelector.addGoal(2, new CharmFollowGoal(animal));
        }
    }

    /**
     * 带效果的玩家攻击暮色生物 → 该生物被打上破中立标记，
     * 从此不再被安抚、不再跟随（随生物 NBT 存入存档）。
     */
    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof Mob mob) || !isTwilightMob(mob)) return;
        if (!(event.getSource().getEntity() instanceof Player player)
            || !player.hasEffect(TwilightMoonshine.TWILIGHT_EMBRACE)) return;

        ((IEntityExtension) mob).getPersistentData().putBoolean(AGGROED_KEY, true);
    }

    /**
     * 暮色森林敌对生物把带有效果的玩家设为仇恨目标时，清空该目标。
     * 已破中立的生物除外——它们可以正常锁定并反击。
     */
    @SubscribeEvent
    public static void onTargetChange(LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        if (event.getNewAboutToBeSetTarget() instanceof Player player
            && event.getEntity() instanceof Monster monster
            && isTwilightMob(monster)
            && !isAggroed(monster)
            && player.hasEffect(TwilightMoonshine.TWILIGHT_EMBRACE)) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    /**
     * 玩家刚获得效果时，让附近正盯着玩家的敌对暮色生物立刻放弃目标
     * （避免效果生效前已经建立的仇恨持续到下一个 tick）。破中立者除外。
     */
    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        if (!event.getEffectInstance().getEffect().is(TwilightMoonshine.TWILIGHT_EMBRACE)) return;

        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(CHARM_RANGE),
            m -> isTwilightMob(m) && m instanceof Monster && !isAggroed(m) && m.getTarget() == player)) {
            mob.setTarget(null);
        }
    }

    /**
     * 每个 tick 在生物 AI 之前（Pre 阶段）处理敌对暮色生物：
     * 只要当前目标是带效果的玩家就立刻清空。
     * 必须每 tick 检查：LivingChangeTargetEvent 只能拦下"新目标设为玩家"，
     * 清不掉喝药前就已锁定的旧目标，等每秒清理就会留出被打一下的窗口。
     * 已破中立的生物跳过，保留正常反击能力。
     * （友好生物的跟随由 CharmFollowGoal 实现，见 onEntityJoin）
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Monster monster)) return;
        if (monster.level().isClientSide() || !isTwilightMob(monster)) return;

        if (!isAggroed(monster)
            && monster.getTarget() instanceof Player player
            && player.hasEffect(TwilightMoonshine.TWILIGHT_EMBRACE)) {
            monster.setTarget(null);
        }
    }

    /** 实体类型是否来自 twilightforest 命名空间 */
    private static boolean isTwilightMob(Entity entity) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key != null && key.getNamespace().equals("twilightforest");
    }

    /** 该生物是否已被带效果的玩家攻击过（破中立，不再被安抚/跟随） */
    private static boolean isAggroed(Mob mob) {
        return ((IEntityExtension) mob).getPersistentData().getBoolean(AGGROED_KEY);
    }

    /** 最近的拥有暮色之拥效果的玩家（旁观模式不算） */
    private static Player findCharmedPlayer(Level level, LivingEntity from) {
        Player best = null;
        double bestSqr = CHARM_RANGE_SQR;
        for (Player player : level.players()) {
            if (player.isSpectator()) continue;
            if (!player.hasEffect(TwilightMoonshine.TWILIGHT_EMBRACE)) continue;
            double distSqr = player.distanceToSqr(from);
            if (distSqr < bestSqr) {
                bestSqr = distSqr;
                best = player;
            }
        }
        return best;
    }

    /**
     * 跟随目标：暮色友好生物主动向带效果的玩家靠拢。
     * 只要带效果玩家在 32 格内，本目标就持续运行（锁定 MOVE/LOOK 标志），
     * 这样它们自带的"躲避玩家"目标（优先级 3/4）在整个效果期间都无法触发，
     * 不会出现"掉头逃跑几步又折返"的反复横跳；靠近后只保持注视、不再移动。
     * 惊慌目标（优先级 1）仍可打断本目标——被攻击时先逃跑。
     */
    private static class CharmFollowGoal extends Goal {

        private final Animal animal;
        private int timeToRecalcPath;

        CharmFollowGoal(Animal animal) {
            this.animal = animal;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (isAggroed(animal)) return false;
            return findCharmedPlayer(animal.level(), animal) != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void start() {
            this.timeToRecalcPath = 0;
        }

        @Override
        public void stop() {
            animal.getNavigation().stop();
        }

        @Override
        public void tick() {
            Player player = findCharmedPlayer(animal.level(), animal);
            if (player == null) return;

            animal.getLookControl().setLookAt(player, 10.0F, animal.getMaxHeadXRot());
            // 离得够近就停住（只保持注视）；每 10 tick 重算一次路径，避免寻路抖动
            if (animal.distanceToSqr(player) > FOLLOW_STOP_SQR) {
                if (--this.timeToRecalcPath <= 0) {
                    this.timeToRecalcPath = 10;
                    animal.getNavigation().moveTo(player, 1.0D);
                }
            }
        }
    }
}
