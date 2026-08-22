package twilightmoonshine.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;
import twilightmoonshine.TwilightMoonshine;

/**
 * 夕阳甲虫乐队的"三重奏"演出（仅服务端，宽松触发）：
 * 只要某台唱片机在播放，且粘液甲虫、喷火甲虫、巨型钳虫各至少 1 只出现在它附近 6 格内，
 * 三只甲虫就以该唱片机为舞台就地演出：
 * - 三只虫错开一拍轮流叫唤，偶尔穿插自己的叫声（不再回放音符盒乐器声，唱片机的音乐就是配乐）；
 * - 轮到的那只虫在节拍上原地小跳一下，头顶冒出自己颜色的粒子（粘液=绿、喷火=橙、钳虫=蓝）；
 * - 演出期间虫子停下寻路与攻击，整只虫（含身体）旋转面向唱片机，按节拍上下弹跳；
 * - 唱片停、虫死/走远或区块卸载即散场。
 * 不需要玩家参与，也不依赖暮色之拥（进度判定在 EmbraceAdvancementEvents 里另行检查）。
 */
@EventBusSubscriber(modid = TwilightMoonshine.MODID, bus = EventBusSubscriber.Bus.GAME)
public class BeetleTrioEvents {

    /** 玩家周围扫描播放中唱片机的半径（方块） */
    private static final double JUKEBOX_SCAN_RANGE = 8.0;
    /** 舞台半径：唱片机周围找三种甲虫（方块） */
    private static final double STAGE_RANGE = 6.0;
    /** 每 N tick 一个节拍 */
    private static final int BEAT_TICKS = 8;
    /** 每 N tick 重新扫描玩家附近，为新的播放中唱片机组队 */
    private static final int SCAN_INTERVAL = 20;

    private static final ResourceLocation SLIME_BEETLE =
        ResourceLocation.fromNamespaceAndPath("twilightforest", "slime_beetle");
    private static final ResourceLocation FIRE_BEETLE =
        ResourceLocation.fromNamespaceAndPath("twilightforest", "fire_beetle");
    private static final ResourceLocation PINCH_BEETLE =
        ResourceLocation.fromNamespaceAndPath("twilightforest", "pinch_beetle");

    /** TF 甲虫自己的叫声（按位置播放，SoundEvent 只包 ID，不依赖注册顺序） */
    private static final SoundEvent SLIME_AMBIENT = tfSound("entity.twilightforest.slime_beetle.ambient");
    private static final SoundEvent FIRE_AMBIENT = tfSound("entity.twilightforest.fire_beetle.ambient");
    private static final SoundEvent PINCH_AMBIENT = tfSound("entity.twilightforest.pinch_beetle.ambient");

    /**
     * 三个声部（与乐队成员顺序一致：0=粘液低音、1=喷火铃铛、2=钳虫拨弦）。
     * 只保留粒子颜色与各自的叫声，不再回放音符盒乐器声。
     */
    private static final SpeciesPart[] PARTS = {
        new SpeciesPart(110, 220, 90, SLIME_AMBIENT),
        new SpeciesPart(240, 120, 40, FIRE_AMBIENT),
        new SpeciesPart(90, 130, 240, PINCH_AMBIENT),
    };

    /** 按维度索引的活跃乐队（键 = 唱片机坐标） */
    private static final Map<ResourceKey<Level>, Map<BlockPos, BeetleBand>> BANDS = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % SCAN_INTERVAL == 0) {
            scanForNewBands(server);
        }
        tickBands(server);
    }

    /** 粗扫描：为每个玩家附近正在播放的唱片机尝试组一支乐队（每 SCAN_INTERVAL 一次） */
    private static void scanForNewBands(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            Map<BlockPos, BeetleBand> bands = BANDS.computeIfAbsent(level.dimension(), k -> new HashMap<>());
            for (BlockPos pos : findPlayingJukeboxes(level, player.blockPosition(), JUKEBOX_SCAN_RANGE)) {
                if (bands.containsKey(pos)) continue;
                BeetleBand band = tryFormBand(level, pos);
                if (band != null) bands.put(pos, band);
            }
        }
    }

    /** 节拍循环：每 tick 校验并让乐队成员站定，节拍 tick 上再演一拍；散场即移除 */
    private static void tickBands(MinecraftServer server) {
        boolean beat = server.getTickCount() % BEAT_TICKS == 0;
        Iterator<Map.Entry<ResourceKey<Level>, Map<BlockPos, BeetleBand>>> dims = BANDS.entrySet().iterator();
        while (dims.hasNext()) {
            Map.Entry<ResourceKey<Level>, Map<BlockPos, BeetleBand>> dim = dims.next();
            ServerLevel level = server.getLevel(dim.getKey());
            if (level == null) {
                dims.remove();
                continue;
            }
            Iterator<Map.Entry<BlockPos, BeetleBand>> bands = dim.getValue().entrySet().iterator();
            while (bands.hasNext()) {
                BeetleBand band = bands.next().getValue();
                if (band.level != level
                    || !level.hasChunkAt(band.jukebox)
                    || !isPlayingJukebox(level, band.jukebox)
                    || !band.isValid()) {
                    bands.remove();
                    continue;
                }
                band.holdAndFace();
                if (beat) band.performBeat();
            }
        }
    }

    /** 以 pos 为中心扫描范围内正在播放的唱片机（has_record=true） */
    private static List<BlockPos> findPlayingJukeboxes(ServerLevel level, BlockPos center, double range) {
        List<BlockPos> found = new ArrayList<>();
        AABB box = new AABB(center).inflate(range);
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (isPlayingJukebox(level, pos)) found.add(pos.immutable());
        }
        return found;
    }

    private static boolean isPlayingJukebox(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.JUKEBOX) && state.getValue(JukeboxBlock.HAS_RECORD);
    }

    /** 组队：唱片机在播且 6 格内三种甲虫各至少 1 只，则成团 */
    private static BeetleBand tryFormBand(ServerLevel level, BlockPos jukebox) {
        if (!isPlayingJukebox(level, jukebox)) return null;
        AABB box = new AABB(jukebox).inflate(STAGE_RANGE);
        Entity slime = null, fire = null, pinch = null;
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box, Entity::isAlive)) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (id == null) continue;
            if (id.equals(SLIME_BEETLE) && slime == null) {
                slime = entity;
            } else if (id.equals(FIRE_BEETLE) && fire == null) {
                fire = entity;
            } else if (id.equals(PINCH_BEETLE) && pinch == null) {
                pinch = entity;
            }
            if (slime != null && fire != null && pinch != null) {
                return new BeetleBand(level, jukebox, slime, fire, pinch);
            }
        }
        return null;
    }

    private static SoundEvent tfSound(String path) {
        return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("twilightforest", path));
    }

    /** 一个声部：粒子颜色（RGB 0-255）、甲虫自己的叫声 */
    private record SpeciesPart(int red, int green, int blue, SoundEvent ambient) {}

    /** 一支活跃的乐队：舞台唱片机 + 三名成员 */
    private static final class BeetleBand {
        final ServerLevel level;
        final BlockPos jukebox;
        final Entity slime;
        final Entity fire;
        final Entity pinch;

        BeetleBand(ServerLevel level, BlockPos jukebox, Entity slime, Entity fire, Entity pinch) {
            this.level = level;
            this.jukebox = jukebox;
            this.slime = slime;
            this.fire = fire;
            this.pinch = pinch;
        }

        /** 三只甲虫都活着且没有远离舞台（含 1.5 格滞回余量，避免边缘反复进出） */
        boolean isValid() {
            double max = (STAGE_RANGE + 1.5) * (STAGE_RANGE + 1.5);
            Vec3 center = Vec3.atCenterOf(jukebox);
            return memberOk(slime, center, max) && memberOk(fire, center, max) && memberOk(pinch, center, max);
        }

        private static boolean memberOk(Entity entity, Vec3 center, double maxDistSqr) {
            return entity.isAlive() && entity.distanceToSqr(center) <= maxDistSqr;
        }

        /** 按声部顺序取成员：0=粘液、1=喷火、2=钳虫 */
        Entity member(int index) {
            return switch (index) {
                case 0 -> slime;
                case 1 -> fire;
                default -> pinch;
            };
        }

        /** 每 tick 压制成员 AI：停寻路、清仇恨、原地站定并整只（含身体）转向唱片机 */
        void holdAndFace() {
            Vec3 center = Vec3.atCenterOf(jukebox);
            for (int i = 0; i < 3; i++) {
                if (member(i) instanceof Mob mob) {
                    mob.getNavigation().stop();
                    mob.setTarget(null);
                    mob.setSprinting(false);
                    // 身体 yaw 直接对准唱片机（原版 lookAt 的朝向公式）：转的是整只虫，不只是头
                    float yaw = (float) (Mth.atan2(center.z - mob.getZ(), center.x - mob.getX()) * Mth.RAD_TO_DEG) - 90.0F;
                    mob.setYRot(yaw);
                    mob.yBodyRot = yaw;
                    mob.setYHeadRot(yaw);
                    // 虫子还想移动（走动/追击/攻击距离调整）：水平速度清零，只允许上下蹦
                    Vec3 mv = mob.getDeltaMovement();
                    if (mv.x != 0.0 || mv.z != 0.0) {
                        mob.setDeltaMovement(0.0, mv.y, 0.0);
                    }
                }
            }
        }

        /** 演一拍：本拍轮到的那只虫叫唤、冒粒子、原地小跳；朝向已由 holdAndFace 每 tick 保持 */
        void performBeat() {
            long beat = level.getGameTime() / BEAT_TICKS + Math.floorMod(jukebox.asLong(), 3);
            int who = (int) Math.floorMod(beat, 3);
            Entity beetle = member(who);
            SpeciesPart part = PARTS[who];
            double x = beetle.getX();
            double y = beetle.getY() + beetle.getBbHeight() + 0.4;
            double z = beetle.getZ();

            // 偶尔叫一声（音高随机浮动），配乐就用唱片机自己的音乐
            if (level.random.nextInt(4) == 0) {
                level.playSound(null, x, y, z, part.ambient, SoundSource.NEUTRAL, 0.5F,
                    0.8F + level.random.nextFloat() * 0.4F);
            }

            // 头顶冒出自己颜色的粒子（1.21.1 没有彩色音符粒子 NoteParticleOption，用彩色粉尘代替）
            level.sendParticles(
                new DustParticleOptions(new Vector3f(part.red / 255F, part.green / 255F, part.blue / 255F), 0.5F),
                x, y + 0.2, z, 1, 0.15, 0.15, 0.15, 0.0);

            // 节拍上原地小跳一下（初速 0.35 的起跳约 8.7 tick 落地，正好合上一拍）
            if (beetle.onGround()) {
                beetle.setDeltaMovement(0.0, 0.35, 0.0);
            }
        }
    }
}
