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
 * 秘密配方的两条"渠道"获取机制（月兔喂食见 MoonRabbit.mobInteract）。
 * 配方给予不是独立的摇奖，而是进度达成后的自然顺承行为：
 * - 拥抱日顺承：处于暮色之拥且 1 格内聚集 10 只暮色森林被动生物（= 拥抱日条件）时，
 *   其中一只生物会向玩家"丢"出神秘书页（暮色荧光精华），随后进入 60 秒冷却；
 *   冷却结束条件仍满足且玩家仍未获得（未掌握）则再次丢出。已掌握则彻底停止。
 * - 荧光引路顺承：玩家处于发光效果时，只会有一只被"选中"的迷雾狼/游魂（"幽灵"）
 *   变为中立并靠近——每次从周围 16 格内随机挑一只（见 LURE_SELECTED / GlowLureGoal）。
 *   它进入玩家 4 格内即触发"光之川"进度（纯靠近判据）并"丢"出神秘书页（暮色合金粉末），
 *   随后走开；丢出后进入 60 秒冷却，冷却结束时玩家仍发光、仍未获得（未掌握且背包无书页）
 *   则再随机挑一只靠近。已交付（已掌握或背包里有对应书页）则不再吸引。
 */
@EventBusSubscriber(modid = TwilightMoonshine.MODID, bus = EventBusSubscriber.Bus.GAME)
public class RecipeLearningEvents {

    /** 拥抱日顺承：需要的生物数量与判定半径（方块，与拥抱日进度一致） */
    private static final int EMBRACE_COUNT = 10;
    private static final double EMBRACE_RANGE = 1.0;
    /** 拥抱日顺承：两次丢出书页之间的冷却（tick，60 秒） */
    private static final int EMBRACE_COOLDOWN_TICKS = 1200;

    /** 荧光引路渠道：召回半径（方块）与其停止跟随时的距离平方阈值（3 格） */
    private static final double LURE_RANGE = 16.0;
    private static final double LURE_STOP_SQR = 9.0;
    /** 荧光引路渠道：丢出书页的接近半径（方块） */
    private static final double GIVE_RANGE = 4.0;
    /** 荧光引路渠道：同一位玩家两次丢出书页的冷却（tick，60 秒，与拥抱日一致） */
    private static final int LURE_TOSS_COOLDOWN_TICKS = 1200;
    /** 荧光引路渠道：选中者超过这么多 tick 还没靠近到 4 格（15 秒）就换一只，防止堵路死锁 */
    private static final int LURE_SELECT_MAX_TICKS = 300;

    /** 拥抱日顺承的冷却计时：玩家 UUID → 距下次丢出还差多少 tick（条件断开时暂停） */
    private static final Map<UUID, Integer> EMBRACE_PAGE_COOLDOWN = new HashMap<>();
    /** 荧光引路渠道的投掷冷却：玩家 UUID → 剩余 tick（发光与否都走秒） */
    private static final Map<UUID, Integer> LURE_TOSS_COOLDOWN = new HashMap<>();
    /** 荧光引路渠道：每位玩家当前被"选中"靠近的迷雾狼/游魂（玩家 UUID → 生物 UUID；同时只会有一只） */
    private static final Map<UUID, UUID> LURE_SELECTED = new HashMap<>();
    /** 荧光引路渠道：选中计时（玩家 UUID → 已选中多少 tick，超时换一只） */
    private static final Map<UUID, Integer> LURE_SELECT_TICKS = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        // Post 在客户端也会触发，instanceof ServerPlayer 直接过滤掉客户端
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        checkEmbraceRecipePage(player);
        checkAlloyFromLuredMobs(player);
    }

    /** 拥抱日顺承：条件达成（暮色之拥 + 1 格内 10 只暮色动物）→ 立即由一只动物丢出书页，之后 60 秒冷却循环 */
    private static void checkEmbraceRecipePage(ServerPlayer player) {
        if (RecipeKnowledge.knows(player, RecipeKnowledge.GLOW_ESSENCE)) {
            EMBRACE_PAGE_COOLDOWN.remove(player.getUUID());
            return; // 已掌握：不再丢
        }
        if (!player.hasEffect(TwilightMoonshine.TWILIGHT_EMBRACE)) return;
        AABB box = player.getBoundingBox().inflate(EMBRACE_RANGE);
        java.util.List<Animal> animals = player.level().getEntitiesOfClass(Animal.class, box,
            e -> e.isAlive() && TwilightEmbraceEvents.isTwilightMob(e));
        if (animals.size() < EMBRACE_COUNT) return; // 条件中断，冷却暂停

        UUID uuid = player.getUUID();
        Integer remaining = EMBRACE_PAGE_COOLDOWN.get(uuid);
        if (remaining == null) {
            // 首次达成：立即丢出（进度达成的"承上启下"一刻）
            EMBRACE_PAGE_COOLDOWN.put(uuid, EMBRACE_COOLDOWN_TICKS);
        } else if (remaining > 0) {
            EMBRACE_PAGE_COOLDOWN.put(uuid, remaining - 1);
            return; // 仍在冷却
        } else {
            // 冷却结束且条件仍满足 → 再来一张
            EMBRACE_PAGE_COOLDOWN.put(uuid, EMBRACE_COOLDOWN_TICKS);
        }
        // 选最近的一只暮色动物，从它头顶把书页丢向玩家
        Animal nearest = null;
        double bestSqr = Double.MAX_VALUE;
        for (Animal animal : animals) {
            double d = animal.distanceToSqr(player);
            if (d < bestSqr) {
                bestSqr = d;
                nearest = animal;
            }
        }
        if (nearest != null) {
            Vec3 from = nearest.position().add(0.0, nearest.getEyeHeight(), 0.0);
            RecipeKnowledge.tossPage(player.serverLevel(), from, player, RecipeKnowledge.GLOW_ESSENCE);
        }
    }

    /**
     * 荧光引路顺承：发光即触发"光之川"进度（4 格内有迷雾狼/游魂，纯靠近判据）；
     * 同时只保留一只被"选中"的迷雾狼/游魂靠近（每次从 16 格内随机挑一只），
     * 它靠近到 4 格内由它丢出书页，随后走开（GlowLureGoal 检测到不再被选中），
     * 并进入 60 秒冷却。冷却结束仍发光且仍未获得（未掌握且背包无书页）时，
     * 重新随机挑选一只靠近；选中者 15 秒没能靠近到 4 格则换一只。
     */
    private static void checkAlloyFromLuredMobs(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (RecipeKnowledge.knows(player, RecipeKnowledge.ALLOY)
            || RecipeKnowledge.hasPage(player, RecipeKnowledge.ALLOY)) {
            LURE_TOSS_COOLDOWN.remove(uuid);
            clearLureSelection(uuid);
            return; // 已交付：不再丢、不再吸引
        }
        // 冷却计时不因发光中断，持续跳秒
        Integer remaining = LURE_TOSS_COOLDOWN.get(uuid);
        if (remaining != null) {
            if (remaining > 1) {
                LURE_TOSS_COOLDOWN.put(uuid, remaining - 1);
            } else {
                LURE_TOSS_COOLDOWN.remove(uuid);
            }
        }
        if (!player.hasEffect(MobEffects.GLOWING)) {
            clearLureSelection(uuid); // 不发光：解除选中（选中者的离开见 GlowLureGoal）
            return;
        }

        // 光之川：发光 + 4 格内有迷雾狼/游魂即为触发（纯靠近判据，与选中/冷却无关）
        AABB giveBox = player.getBoundingBox().inflate(GIVE_RANGE);
        boolean nearLure = !player.level().getEntitiesOfClass(Mob.class, giveBox,
            e -> e.isAlive() && isLureMob(e)).isEmpty();
        if (nearLure) {
            TwilightMoonshine.LURE_APPROACH_TRIGGER.get().trigger(player);
        }
        if (LURE_TOSS_COOLDOWN.containsKey(uuid)) return; // 冷却中：不选新者（选中的已走开）

        // 校验当前选中的那只是否仍有效（存活、仍为引诱生物、仍在 16 格内）
        UUID selectedId = LURE_SELECTED.get(uuid);
        Mob selected = selectedId == null ? null
            : player.serverLevel().getEntity(selectedId) instanceof Mob mob && mob.isAlive() ? mob : null;
        if (selectedId != null && (selected == null || !isLureMob(selected)
            || selected.distanceToSqr(player) > LURE_RANGE * LURE_RANGE)) {
            clearLureSelection(uuid);
            selectedId = null;
            selected = null;
        }
        if (selected != null) {
            if (selected.distanceToSqr(player) <= GIVE_RANGE * GIVE_RANGE) {
                // 选中者靠近到 4 格内：由它丢出书页 + 60 秒冷却，丢出后它走开（GlowLureGoal）
                LURE_TOSS_COOLDOWN.put(uuid, LURE_TOSS_COOLDOWN_TICKS);
                clearLureSelection(uuid);
                Vec3 from = selected.position().add(0.0, selected.getEyeHeight(), 0.0);
                RecipeKnowledge.tossPage(player.serverLevel(), from, player, RecipeKnowledge.ALLOY);
                return;
            }
            // 15 秒内没能靠近到 4 格 → 换一只；否则继续等它靠近
            if (LURE_SELECT_TICKS.merge(uuid, 1, Integer::sum) > LURE_SELECT_MAX_TICKS) {
                clearLureSelection(uuid);
            } else {
                return;
            }
        }

        // 没有选中者（首次/失效/超时）：随机挑一只 16 格内的迷雾狼/游魂靠近，优先没被其他玩家选中的
        AABB box = player.getBoundingBox().inflate(LURE_RANGE);
        java.util.List<Mob> candidates = player.level().getEntitiesOfClass(Mob.class, box,
            e -> e.isAlive() && isLureMob(e));
        if (!candidates.isEmpty()) {
            java.util.List<Mob> free = candidates.stream()
                .filter(m -> !LURE_SELECTED.containsValue(m.getUUID())).toList();
            java.util.List<Mob> pool = free.isEmpty() ? candidates : free;
            LURE_SELECTED.put(uuid, pool.get(player.getRandom().nextInt(pool.size())).getUUID());
            LURE_SELECT_TICKS.put(uuid, 0);
        }
    }

    /** 清掉该玩家的选中状态及其计时 */
    private static void clearLureSelection(UUID uuid) {
        LURE_SELECTED.remove(uuid);
        LURE_SELECT_TICKS.remove(uuid);
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
        EMBRACE_PAGE_COOLDOWN.clear();
        LURE_TOSS_COOLDOWN.clear();
        LURE_SELECTED.clear();
        LURE_SELECT_TICKS.clear();
    }

    /** 是否是会被发光玩家吸引的暮色生物：迷雾狼（twilightforest:mist_wolf）或游魂（twilightforest:wraith） */
    private static boolean isLureMob(Entity entity) {
        if (!TwilightEmbraceEvents.isTwilightMob(entity)) return false;
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
        return path.equals("mist_wolf") || path.equals("wraith");
    }

    /** 该迷雾狼/游魂是否正是玩家当前"被选中"的那一只 */
    private static boolean isSelectedLurer(Mob mob, ServerPlayer player) {
        return mob.getUUID().equals(LURE_SELECTED.get(player.getUUID()));
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
     * 被发光玩家"选中"靠近的目标：它是玩家 16 格内随机选中的那一只
     * （选中状态见 checkAlloyFromLuredMobs / isSelectedLurer），锁定 MOVE/LOOK 保持
     * "中立靠近"（与暮色之拥的跟随目标同一思路）。丢出书页后（不再被选中）或
     * 玩家的背包里已有/已掌握该配方、发光效果消失时，立即转为"离开"：
     * 朝远离玩家的方向走开约 12 格；离开途中仍保持中立
     * （目标拦截见 onTargetChange / onEntityTick），不会攻击玩家。
     * 未被选中的迷雾狼/游魂不受影响，不会跟随。
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

        /** 是否已交付（掌握配方或背包里已有对应书页） */
        private static boolean delivered(ServerPlayer player) {
            return RecipeKnowledge.knows(player, RecipeKnowledge.ALLOY)
                || RecipeKnowledge.hasPage(player, RecipeKnowledge.ALLOY);
        }

        @Override
        public boolean canUse() {
            this.target = nearestGlowingPlayer(this.mob);
            // 只有玩家当前"选中"的那一只才会靠近；已交付过配方的玩家不再被吸引
            return this.target instanceof ServerPlayer serverPlayer
                && !delivered(serverPlayer)
                && isSelectedLurer(this.mob, serverPlayer);
        }

        @Override
        public boolean canContinueToUse() {
            if (this.target == null || !this.target.isAlive()) return false;
            if (this.departPoint != null) {
                // 走到离开点或超过 10 秒后结束这场"离开"
                return this.departTicks++ < DEPART_MAX_TICKS && this.mob.distanceToSqr(this.departPoint) > 2.0;
            }
            // 丢出书页后不再被选中（见 checkAlloyFromLuredMobs）、配方已交付（书页进包也算）
            // 或发光效果消失 → 不再吸引，转为离开
            if (this.target instanceof ServerPlayer serverPlayer) {
                boolean done = delivered(serverPlayer) || !isSelectedLurer(this.mob, serverPlayer);
                if (done || !this.target.hasEffect(MobEffects.GLOWING)) {
                    this.startDepart();
                }
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
            // 吸引到 3 格内就停下注视（近到 4 格内早已丢出书页、转而离开）
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
