package twilightmoonshine.entity;

import net.minecraft.tags.ItemTags;
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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import twilightmoonshine.TwilightMoonshine;

public class MoonRabbit extends Rabbit {

    public MoonRabbit(EntityType<? extends Rabbit> type, Level level) {
        super(type, level);
        // 修复 MC-150224（官方 1.21.4 才修复）：替换 MoveControl，
        // 解决兔子跳跃时水平速度丢失、原地跳的问题
        // （jumpControl 保持原版 RabbitJumpControl——Rabbit 内部有类型转换，不能替换）
        this.moveControl = new MoonRabbitMoveControl(this);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(1, new ClimbOnTopOfPowderSnowGoal(this, this.level()));
        this.goalSelector.addGoal(1, new PanicGoal(this, 2.2));
        // 不注册 BreedGoal：月兔无法繁殖
        // 胡萝卜引诱跟随（TemptGoal 使用 RABBIT_FOOD 标签）
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.0, p -> p.is(ItemTags.RABBIT_FOOD), false));
        // 不躲避玩家；保留躲避狼和怪物的本能
        this.goalSelector.addGoal(4, new AvoidEntityGoal<>(this, Wolf.class, 10.0F, 2.2, 2.2));
        this.goalSelector.addGoal(4, new AvoidEntityGoal<>(this, Monster.class, 4.0F, 2.2, 2.2));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
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

    // 接受胡萝卜（喂食有爱心反馈，但不会繁殖）
    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.RABBIT_FOOD);
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
