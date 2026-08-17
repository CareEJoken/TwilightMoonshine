package twilightmoonshine.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import twilightmoonshine.TwilightMoonshine;
import twilightmoonshine.block.entity.MoonRabbitTrophyBlockEntity;

/**
 * 月兔战利品基类 — 与暮色森林的 AbstractTrophyBlock 同款行为：
 * 点击 / 通电时播放月兔叫声并冒出月亮蓝粒子，可以戴在头上。
 */
public abstract class MoonRabbitAbstractTrophyBlock extends BaseEntityBlock implements Equipable {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    protected MoonRabbitAbstractTrophyBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.getStateDefinition().any().setValue(POWERED, false));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide()) {
            boolean flag = level.hasNeighborSignal(pos);
            if (flag != state.getValue(POWERED)) {
                if (flag) {
                    this.playSound(level, pos);
                }
                level.setBlockAndUpdate(pos, state.setValue(POWERED, flag));
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        this.playSound(level, pos);
        if (level.isClientSide()) {
            this.createParticle(level, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MoonRabbitTrophyBlockEntity(pos, state);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    /** 服务端播放月兔叫声（音量音调仿谜题羊战利品：音调 ~0.7）。 */
    public void playSound(Level level, BlockPos pos) {
        if (!level.isClientSide()) {
            level.playSound(null, pos, TwilightMoonshine.MOON_RABBIT_AMBIENT.get(), SoundSource.BLOCKS,
                1.0F, level.getRandom().nextFloat() * 0.1F + 0.7F);
        }
    }

    /** 客户端冒出 10 个月亮蓝魔法粒子。 */
    public void createParticle(Level level, BlockPos pos) {
        for (int p = 0; p < 10; p++) {
            level.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.55F, 0.75F, 1.0F),
                pos.getX() + 0.5D + (level.getRandom().nextDouble() - 0.5D),
                pos.getY() + 0.5D + (level.getRandom().nextDouble() - 0.5D),
                pos.getZ() + 0.5D + (level.getRandom().nextDouble() - 0.5D),
                0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }
}
