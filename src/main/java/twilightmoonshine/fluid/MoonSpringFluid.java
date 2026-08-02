package twilightmoonshine.fluid;

import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import twilightmoonshine.TwilightMoonshine;

public class MoonSpringFluid {

    public static BaseFlowingFluid.Properties createProperties() {
        return new BaseFlowingFluid.Properties(
                () -> TwilightMoonshine.MOON_SPRING_TYPE.value(),
                () -> TwilightMoonshine.MOON_SPRING_SOURCE.value(),
                () -> TwilightMoonshine.MOON_SPRING_FLOWING.value()
        ).bucket(() -> TwilightMoonshine.MOON_SPRING_BUCKET.value())
         .block(() -> TwilightMoonshine.MOON_SPRING_BLOCK.value())
         .tickRate(5)
         .slopeFindDistance(4)
         .levelDecreasePerBlock(1)
         .explosionResistance(100.0F);
    }
}
