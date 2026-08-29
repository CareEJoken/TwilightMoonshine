package twilightmoonshine.entity;

import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.ClimbOnTopOfPowderSnowGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import twilightmoonshine.TwilightMoonshine;
import twilightmoonshine.data.RecipeKnowledge;

public class MoonRabbit extends Rabbit {

    public MoonRabbit(EntityType<? extends Rabbit> type, Level level) {
        super(type, level);
        // 修复 MC-150224（官方 1.21.4 才修复）：替换 MoveControl，
        // 解决兔子跳跃时水平速度丢失、原地跳的问题
        // （jumpControl 保持原版 RabbitJumpControl——Rabbit 内部有类型转换，不能替换）
        this.moveControl = new MoonRabbitMoveControl(this);
    }

    // ==================== 膨胀 / 喷嚏交互 ====================
    // 喂食（胡萝卜、金胡萝卜除外）→ 膨胀 1~2 级；达到 4 级时体型翻倍，
    // 发出喷嚏声，延迟后喷出战利品并恢复 0 级（参考熊猫打喷嚏）

    public static final int MAX_LEVEL = 4;
    public static final int SNEEZE_DELAY_TICKS = 40; // 2 秒
    private static final float SLIME_BALL_CHANCE = 0.4F; // 固定 40% 粘液球，60% 月石碎片
    /** 喷嚏战利品中喷出暮色植物萃取液配方的概率（已掌握则不会再给） */
    private static final float RECIPE_SNEEZE_CHANCE = 0.5F;

    private static final EntityDataAccessor<Byte> DATA_LEVEL =
        SynchedEntityData.defineId(MoonRabbit.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_SNEEZE_TICKS =
        SynchedEntityData.defineId(MoonRabbit.class, EntityDataSerializers.INT);

    // 客户端膨胀动画（不同步，仅客户端使用）：animScale 为当前 tick 的动画值，
    // prevAnimScale 为上一 tick 的值，渲染时按帧 partialTick 在两者间插值
    private float prevAnimScale = 1.0F;
    private float animScale = 1.0F;

    /** 喂到 4 级的玩家：喷嚏战利品里喷出的配方给这位玩家（喷嚏结束后清空，随 NBT 存档） */
    private UUID recipeFeeder;
    /** 是否已经打过第一次喷嚏：第一次必喷出神秘书页，之后回归 50% 概率（随 NBT 存档） */
    private boolean firstSneezeDone;
    /** 药水交换：已收的药水类别位（1=暮色、2=荧光、4=抗性），集齐三瓶送月光私酿（随 NBT 存档） */
    private byte potionTradeBits;
    /** 首次集齐三瓶药水时的纪念：第一次完成兑换额外送一座月兔战利品，之后只送私酿（随 NBT 存档） */
    private boolean trophyGiven;

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LEVEL, (byte) 0);
        builder.define(DATA_SNEEZE_TICKS, -1); // -1 = 无喷嚏进行中
    }

    public int getInflateLevel() {
        return this.entityData.get(DATA_LEVEL);
    }

    private void setInflateLevel(int level) {
        this.entityData.set(DATA_LEVEL, (byte) Math.max(0, Math.min(MAX_LEVEL, level)));
        this.refreshDimensions();
    }

    public int getSneezeTicks() {
        return this.entityData.get(DATA_SNEEZE_TICKS);
    }

    private void setSneezeTicks(int ticks) {
        this.entityData.set(DATA_SNEEZE_TICKS, ticks);
    }

    // 待机状态：进食膨胀中 / 喷嚏前奏中 → 不主动走动、不被胡萝卜引诱（原地待机）
    private boolean isBusy() {
        return this.getInflateLevel() > 0 || this.getSneezeTicks() > 0;
    }

    // 体型系数：0 级 1.0 → 4 级 2.0，每级 +25%（L1=1.25、L2=1.5、L3=1.75）。
    // 服务端返回瞬时值（命中箱立即变化）；客户端返回平滑动画值：
    // 每 tick 更新 prev/cur，渲染时按当前帧 partialTick 插值，逐帧平滑不跳变。
    // 1.21.1 原版渲染器会自动应用 getScale()，渲染器 scale() 中只保留基础 2.0 倍。
    @Override
    public float getScale() {
        float target = 1.0F + 0.25F * this.getInflateLevel();
        if (!this.level().isClientSide) {
            return target;
        }
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        return Mth.lerp(partialTick, this.prevAnimScale, this.animScale);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DATA_LEVEL.equals(key)) {
            this.refreshDimensions();
        }
        super.onSyncedDataUpdated(key);
    }

    @Override
    public void tick() {
        super.tick();
        // 客户端平滑膨胀动画：记录上一 tick 值，逐 tick 匀速逼近目标
        // （约 6 tick 过渡一级），配合 getScale() 的帧插值实现逐帧平滑
        if (this.level().isClientSide) {
            this.prevAnimScale = this.animScale;
            this.animScale = Mth.approach(this.animScale, 1.0F + 0.25F * this.getInflateLevel(), 0.04F);
        }
        if (!this.level().isClientSide && this.getSneezeTicks() > 0) {
            this.setSneezeTicks(this.getSneezeTicks() - 1);
            if (this.getSneezeTicks() == 0) {
                this.doSneeze();
            }
        }
    }

    // 喂食交互：
    // - 胡萝卜/金胡萝卜 → 惹怒月兔（生气粒子，不冒爱心，不消耗）
    // - 其他可食用物品 → 膨胀。满级或喷嚏进行中时不再接受投喂。
    // - 蒲公英不再是食物，交互无效果。
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // --- 药水交换（与喂食分开，给药水月兔不变大）：
        // 本模组三种药水（暮色/荧光/抗性，含延长/强化版）各交一瓶 → 月兔给月光私酿 ---
        int potionBit = tradePotionBit(stack);
        if (potionBit != 0) {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
            return acceptTradePotion(serverPlayer, stack, potionBit);
        }
        if (this.isCarrotFood(stack)) {
            if (this.level().isClientSide) {
                return InteractionResult.SUCCESS;
            }
            this.showAngryParticles();
            return InteractionResult.SUCCESS;
        }
        if (this.isGrowthFood(stack)) {
            if (this.level().isClientSide) {
                return InteractionResult.CONSUME;
            }
            if (this.getInflateLevel() >= MAX_LEVEL || this.getSneezeTicks() > 0) {
                return InteractionResult.PASS;
            }
            int oldLevel = this.getInflateLevel();
            int newLevel = Math.min(MAX_LEVEL, oldLevel + 1 + this.random.nextInt(2)); // 每次 +1~2 级
            this.setInflateLevel(newLevel);
            this.playSound(this.getEatingSound(stack), 1.0F, 1.0F);
            stack.consume(1, player);
            // 跺跺脚！：喂食让月兔变大即触发（喂到任意新等级）
            if (player instanceof ServerPlayer serverPlayer) {
                TwilightMoonshine.MOON_RABBIT_INFLATE_TRIGGER.get().trigger(serverPlayer);
            }
            if (newLevel >= MAX_LEVEL) {
                // 记下喂到 4 级的玩家：配方实际喷出在喷嚏战利品里（doSneeze / spawnSneezeLoot）
                if (player instanceof ServerPlayer serverPlayer) {
                    this.recipeFeeder = serverPlayer.getUUID();
                }
                this.startSneezeSequence();
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    /** 药水交换：暮色药水（普通/延长） */
    private static final Set<ResourceKey<Potion>> POTION_TRADE_TWILIGHT = Set.of(
        TwilightMoonshine.TWILIGHT_POTION.getKey(),
        TwilightMoonshine.LONG_TWILIGHT_POTION.getKey());
    /** 荧光药水（普通/延长） */
    private static final Set<ResourceKey<Potion>> POTION_TRADE_GLOW = Set.of(
        TwilightMoonshine.GLOW_POTION.getKey(),
        TwilightMoonshine.LONG_GLOW_POTION.getKey());
    /** 抗性药水（普通/延长/强化） */
    private static final Set<ResourceKey<Potion>> POTION_TRADE_RESISTANCE = Set.of(
        TwilightMoonshine.RESISTANCE_POTION.getKey(),
        TwilightMoonshine.LONG_RESISTANCE_POTION.getKey(),
        TwilightMoonshine.STRONG_RESISTANCE_POTION.getKey());

    /** 瓶装药水且是本模组三种药水之一 → 返回类别位（1=暮色、2=荧光、4=抗性），否则 0（药箭不算） */
    private static int tradePotionBit(ItemStack stack) {
        if (!stack.is(Items.POTION) && !stack.is(Items.SPLASH_POTION) && !stack.is(Items.LINGERING_POTION)) {
            return 0;
        }
        return stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
            .potion()
            .map(p -> {
                if (p.unwrapKey().map(POTION_TRADE_TWILIGHT::contains).orElse(false)) {
                    return 1;
                }
                if (p.unwrapKey().map(POTION_TRADE_GLOW::contains).orElse(false)) {
                    return 2;
                }
                if (p.unwrapKey().map(POTION_TRADE_RESISTANCE::contains).orElse(false)) {
                    return 4;
                }
                return 0;
            })
            .orElse(0);
    }

    /** 收下一种药水：1=暮色、2=荧光、4=抗性；集齐三种送一瓶月光私酿（然后重置再收） */
    private InteractionResult acceptTradePotion(ServerPlayer player, ItemStack stack, int give) {
        if ((this.potionTradeBits & give) != 0) {
            // 同类已收过：不消耗，提醒一下
            player.displayClientMessage(
                Component.translatable("message.twilightmoonshine.potion_trade.duplicate"), true);
            return InteractionResult.SUCCESS;
        }
        Component potionName = stack.getHoverName();
        this.potionTradeBits = (byte) (this.potionTradeBits | give);
        stack.shrink(1);
        // 喝药水用"啜饮"音效（不是吃东西的声音）
        this.playSound(SoundEvents.GENERIC_DRINK, 1.0F, 1.0F);
        if (this.potionTradeBits == 7) {
            // 集齐三种：兑一杯月光私酿，重新计数
            this.potionTradeBits = 0;
            if (!this.trophyGiven) {
                // 首次完成：私酿之外再送一座月兔战利品（只送一次）。
                // 用合并消息而不用两条——displayClientMessage(true) 是 actionbar，
                // 两条连着发只有最后一条看得见
                this.trophyGiven = true;
                player.displayClientMessage(
                    Component.translatable("message.twilightmoonshine.potion_trade.complete_trophy"), true);
                player.addItem(new ItemStack(TwilightMoonshine.MOONSHINE.get()));
                player.addItem(new ItemStack(TwilightMoonshine.MOON_RABBIT_TROPHY.get()));
            } else {
                player.displayClientMessage(
                    Component.translatable("message.twilightmoonshine.potion_trade.complete"), true);
                player.addItem(new ItemStack(TwilightMoonshine.MOONSHINE.get()));
            }
        } else {
            player.displayClientMessage(
                Component.translatable("message.twilightmoonshine.potion_trade.taken", potionName), true);
        }
        return InteractionResult.SUCCESS;
    }

    private boolean isCarrotFood(ItemStack stack) {
        return stack.is(Items.CARROT) || stack.is(Items.GOLDEN_CARROT);
    }

    private boolean isGrowthFood(ItemStack stack) {
        // 1.20.5+ 食物是数据组件：有 FOOD 组件即可食用（排除胡萝卜/金胡萝卜）
        return stack.get(DataComponents.FOOD) != null && !this.isCarrotFood(stack);
    }

    // 喂错食物：村民生气粒子（头部上方）
    private void showAngryParticles() {
        for (int i = 0; i < 3 + this.random.nextInt(3); i++) {
            double x = this.getX() + (this.random.nextDouble() - 0.5) * this.getBbWidth();
            double y = this.getY() + this.getBbHeight() * 0.85 + this.random.nextDouble() * 0.3;
            double z = this.getZ() + (this.random.nextDouble() - 0.5) * this.getBbWidth();
            this.level().addParticle(ParticleTypes.ANGRY_VILLAGER, x, y, z, 0.0, 0.0, 0.0);
        }
    }

    private void startSneezeSequence() {
        this.setSneezeTicks(SNEEZE_DELAY_TICKS);
        // 准备阶段：喷嚏前兆（吸气）；正式喷嚏音效见 doSneeze（喷出物品时）
        this.playSound(TwilightMoonshine.MOON_RABBIT_PRE_SNEEZE.get(), 1.0F, this.getVoicePitch() * 0.8F);
    }

    private void doSneeze() {
        this.playSound(TwilightMoonshine.MOON_RABBIT_SNEEZE.get(), 1.0F, this.getVoicePitch());
        this.spawnSneezeLoot();
        this.setInflateLevel(0);
    }

    // 战利品：固定 40% 粘液球 0~2 个，60% 月石碎片 1~3 个；
    // 另有一次 50% 的战利品——"神秘书页"（喷给喂到 4 级的玩家，右键翻开才解锁配方；已掌握则不会再给）
    private void spawnSneezeLoot() {
        RandomSource random = this.getRandom();
        ItemStack loot;
        if (random.nextFloat() < SLIME_BALL_CHANCE) {
            loot = new ItemStack(Items.SLIME_BALL, random.nextInt(3)); // 0~2
        } else {
            loot = new ItemStack(TwilightMoonshine.MOON_STONE_SHARD.get(), 1 + random.nextInt(3)); // 1~3
        }
        if (!loot.isEmpty()) {
            ItemEntity item = new ItemEntity(this.level(), this.getX(),
                this.getY() + this.getEyeHeight() * 0.8, this.getZ(), loot);
            item.setDeltaMovement(
                (random.nextDouble() - 0.5) * 0.3,
                0.25 + random.nextDouble() * 0.15,
                (random.nextDouble() - 0.5) * 0.3);
            this.level().addFreshEntity(item);
        }
        // 配方战利品：喂到 4 级的玩家若还在线，就由喷嚏掷出"神秘书页"（实物）。
        // 第一次喷嚏必得（100%），之后回归 50% 概率；已掌握则不会再给
        if (this.recipeFeeder != null && this.level().getServer() != null) {
            ServerPlayer feeder = this.level().getServer().getPlayerList().getPlayer(this.recipeFeeder);
            if (feeder != null) {
                float chance = this.firstSneezeDone ? RECIPE_SNEEZE_CHANCE : 1.0F;
                RecipeKnowledge.grantPageIfChance(feeder, RecipeKnowledge.PLANT_EXTRACT, chance);
            }
        }
        this.firstSneezeDone = true;
        this.recipeFeeder = null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("MoonInflateLevel", (byte) this.getInflateLevel());
        tag.putInt("MoonSneezeTicks", this.getSneezeTicks());
        if (this.recipeFeeder != null) {
            tag.putUUID("MoonRecipeFeeder", this.recipeFeeder);
        }
        tag.putBoolean("MoonFirstSneeze", this.firstSneezeDone);
        tag.putByte("MoonPotionTrade", this.potionTradeBits);
        tag.putBoolean("MoonTrophyGiven", this.trophyGiven);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_LEVEL, (byte) Math.max(0, Math.min(MAX_LEVEL, tag.getByte("MoonInflateLevel"))));
        this.entityData.set(DATA_SNEEZE_TICKS, tag.getInt("MoonSneezeTicks"));
        this.recipeFeeder = tag.hasUUID("MoonRecipeFeeder") ? tag.getUUID("MoonRecipeFeeder") : null;
        this.firstSneezeDone = tag.getBoolean("MoonFirstSneeze");
        this.potionTradeBits = tag.getByte("MoonPotionTrade");
        this.trophyGiven = tag.getBoolean("MoonTrophyGiven");
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(1, new ClimbOnTopOfPowderSnowGoal(this, this.level()));
        this.goalSelector.addGoal(1, new PanicGoal(this, 2.2));
        // 不注册 BreedGoal：月兔无法繁殖
        // 胡萝卜/金胡萝卜引诱跟随（蒲公英不再是食物）；膨胀/喷嚏中不受引诱
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.0,
            p -> p.is(Items.CARROT) || p.is(Items.GOLDEN_CARROT), false) {
            @Override
            public boolean canUse() {
                return !isBusy() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !isBusy() && super.canContinueToUse();
            }
        });
        // 不躲避玩家；保留躲避狼和怪物的本能
        this.goalSelector.addGoal(4, new AvoidEntityGoal<>(this, Wolf.class, 10.0F, 2.2, 2.2));
        this.goalSelector.addGoal(4, new AvoidEntityGoal<>(this, Monster.class, 4.0F, 2.2, 2.2));
        // 膨胀/喷嚏中不闲逛（原地待机；goal 停止时自动 navigation.stop()）
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6) {
            @Override
            public boolean canUse() {
                return !isBusy() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !isBusy() && super.canContinueToUse();
            }
        });
        this.goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 10.0F));
        // 修复：原版兔子缺少 RandomLookAroundGoal 导致无限徘徊（官方 1.21.4 修复）
        this.goalSelector.addGoal(12, new RandomLookAroundGoal(this));
    }

    // 没有幼年形态
    @Override
    public boolean isBaby() {
        return false;
    }

    // 无法繁殖（双保险：BreedGoal 已去掉）
    @Override
    public boolean canBreed() {
        return false;
    }

    // 胡萝卜/金胡萝卜：可引诱跟随，但喂食会惹怒月兔（见 mobInteract）；蒲公英不再是食物
    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.CARROT) || stack.is(Items.GOLDEN_CARROT);
    }

    // --- 专属声音（字幕显示"月兔：XXX"）---
    @Override
    protected SoundEvent getJumpSound() {
        return TwilightMoonshine.MOON_RABBIT_JUMP.get();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return TwilightMoonshine.MOON_RABBIT_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return TwilightMoonshine.MOON_RABBIT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return TwilightMoonshine.MOON_RABBIT_DEATH.get();
    }

    // --- MC-150224 修复（官方 1.21.4 / 24w46a 才修复）---
    // 修复跳跃高度：原版代码里 "speedModifier <= 0.6 → f=0.2F" 会覆盖爬高判断 "f=0.5F"，
    // 导致兔子爬一格高障碍时跳得太矮。修复：检测到爬高需求直接返回 0.5F，低速判断只用于普通跳跃。
    @Override
    protected float getJumpPower() {
        if (this.horizontalCollision || this.moveControl.hasWanted() && this.moveControl.getWantedY() > this.getY() + 0.5) {
            return this.getJumpPower(0.5F / 0.42F);
        }
        Path path = this.navigation.getPath();
        if (path != null && !path.isDone() && path.getNextEntityPos(this).y > this.getY() + 0.5) {
            return this.getJumpPower(0.5F / 0.42F);
        }
        return this.getJumpPower((this.moveControl.getSpeedModifier() <= 0.6 ? 0.2F : 0.3F) / 0.42F);
    }

    // --- MC-150224 修复：MoveControl 避免每 tick 重复设置速度（官方 1.21.4 修复）---
    public static class MoonRabbitMoveControl extends MoveControl {
        private final MoonRabbit rabbit;
        private double nextJumpSpeed;
        private double lastSetSpeed = -1.0;

        public MoonRabbitMoveControl(MoonRabbit rabbit) {
            super(rabbit);
            this.rabbit = rabbit;
        }

        @Override
        public void tick() {
            Rabbit.RabbitJumpControl jumpControl = (Rabbit.RabbitJumpControl) this.rabbit.jumpControl;
            if (this.rabbit.onGround() && !this.rabbit.jumping && !jumpControl.wantJump()) {
                this.setSpeedIfNeeded(0.0);
            } else if (this.hasWanted()) {
                this.setSpeedIfNeeded(this.nextJumpSpeed);
            }
            super.tick();
        }

        // 修复：setSpeedModifier 内部会执行 setWantedPosition 重设目标位置，
        // 每 tick 重复调用导致兔子跳跃中/目标结束后仍追旧目标（官方 1.21.4 修复）
        private void setSpeedIfNeeded(double speed) {
            if (this.lastSetSpeed != speed) {
                this.rabbit.setSpeedModifier(speed);
                this.lastSetSpeed = speed;
            }
        }

        @Override
        public void setWantedPosition(double x, double y, double z, double speed) {
            if (this.rabbit.isInWater()) {
                speed = 1.5;
            }
            super.setWantedPosition(x, y, z, speed);
            if (speed > 0.0) {
                this.nextJumpSpeed = speed;
            }
        }
    }
}
