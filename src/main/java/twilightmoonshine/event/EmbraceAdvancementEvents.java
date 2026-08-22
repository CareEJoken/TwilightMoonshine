package twilightmoonshine.event;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import twilightmoonshine.TwilightMoonshine;

/**
 * 暮色之拥相关进度检测（仅服务端玩家 tick 时检查）：
 * - 拥抱日（embrace_hug）：效果生效时，周围 1 格内聚集 10 只暮色森林被动生物
 * - 夕阳甲虫派对（embrace_beetles）：周围 4 格内
 *   同时存在黏液甲虫、喷火甲虫、巨钳甲虫各至少 1 只，
 *   且周围 6 格内有正在播放的唱片机（无需暮色之拥）
 * 满足条件即触发对应判据（触发器见 EmbraceProximityTrigger）。
 */
@EventBusSubscriber(modid = TwilightMoonshine.MODID, bus = EventBusSubscriber.Bus.GAME)
public class EmbraceAdvancementEvents {

    /** 拥抱日：需要的生物数量和判定半径（方块） */
    private static final int HUG_COUNT = 10;
    private static final double HUG_RANGE = 1.0;
    /** 甲虫派对：判定半径（方块） */
    private static final double BEETLE_RANGE = 4.0;
    /** 唱片机：判定半径（方块） */
    private static final double JUKEBOX_RANGE = 6.0;

    private static final ResourceLocation SLIME_BEETLE =
        ResourceLocation.fromNamespaceAndPath("twilightforest", "slime_beetle");
    private static final ResourceLocation FIRE_BEETLE =
        ResourceLocation.fromNamespaceAndPath("twilightforest", "fire_beetle");
    private static final ResourceLocation PINCH_BEETLE =
        ResourceLocation.fromNamespaceAndPath("twilightforest", "pinch_beetle");

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        // Post 在客户端也会触发，instanceof ServerPlayer 直接过滤掉客户端
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        checkEmbraceHug(player);
        checkEmbraceBeetles(player);
    }

    /** 拥抱日：暮色之拥 + 1 格内 10 只暮色森林被动生物（Animal 且 twilightforest 命名空间） */
    private static void checkEmbraceHug(ServerPlayer player) {
        if (!player.hasEffect(TwilightMoonshine.TWILIGHT_EMBRACE)) return;
        AABB box = player.getBoundingBox().inflate(HUG_RANGE);
        int count = player.level().getEntitiesOfClass(Animal.class, box,
            e -> e.isAlive() && TwilightEmbraceEvents.isTwilightMob(e)).size();
        if (count >= HUG_COUNT) {
            TwilightMoonshine.EMBRACE_HUG_TRIGGER.get().trigger(player);
        }
    }

    /** 甲虫派对：4 格内三种暮色甲虫各至少 1 只 + 6 格内正在播放的唱片机（无需暮色之拥） */
    private static void checkEmbraceBeetles(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(BEETLE_RANGE);
        boolean slime = false, fire = false, pinch = false;
        for (Entity entity : player.level().getEntities(player, box,
            e -> e.isAlive() && TwilightEmbraceEvents.isTwilightMob(e))) {
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (key == null) continue;
            if (key.equals(SLIME_BEETLE)) {
                slime = true;
            } else if (key.equals(FIRE_BEETLE)) {
                fire = true;
            } else if (key.equals(PINCH_BEETLE)) {
                pinch = true;
            }
            if (slime && fire && pinch) break;
        }
        if (!(slime && fire && pinch)) return;
        // 唱片机扫描逐方块进行、比实体扫描重，每 20 tick 才做一次
        if (player.tickCount % 20 != 0) return;
        if (!hasPlayingJukeboxNearby(player)) return;
        TwilightMoonshine.EMBRACE_BEETLES_TRIGGER.get().trigger(player);
    }

    /** 玩家周围 6 格内是否存在正在播放唱片的唱片机（has_record=true） */
    private static boolean hasPlayingJukeboxNearby(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(JUKEBOX_RANGE);
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = player.level().getBlockState(pos);
            if (state.is(Blocks.JUKEBOX) && state.getValue(JukeboxBlock.HAS_RECORD)) return true;
        }
        return false;
    }
}
