package twilightmoonshine.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /**
     * Chance that light_blue_stained_glass is replaced with blue_stained_glass
     * during quest island placement.
     * <p>
     * 0.0 = all light blue<br>
     * 0.5 = 1:1 ratio (default)<br>
     * 1.0 = all blue
     */
    public static final ModConfigSpec.DoubleValue LIGHT_BLUE_TO_BLUE_GLASS_RATIO =
        BUILDER.comment(
            "Ratio of light_blue_stained_glass replaced with blue_stained_glass.",
            "0.0 = all light blue, 0.5 = 1:1, 1.0 = all blue"
        ).defineInRange("lightBlueToBlueGlassRatio", 0.5, 0.0, 1.0);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
