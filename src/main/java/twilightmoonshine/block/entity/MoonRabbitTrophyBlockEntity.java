package twilightmoonshine.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import twilightmoonshine.TwilightMoonshine;

public class MoonRabbitTrophyBlockEntity extends BlockEntity {

    public MoonRabbitTrophyBlockEntity(BlockPos pos, BlockState state) {
        super(TwilightMoonshine.MOON_RABBIT_TROPHY_BLOCK_ENTITY.get(), pos, state);
    }
}
