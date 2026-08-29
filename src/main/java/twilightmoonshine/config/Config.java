package twilightmoonshine.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /**
     * Percentage chance that the Mushroom Tower (mushroom castle) generates
     * at a Dense Mushroom Forest landmark center. 0 = vanilla TF 4.8 behavior.
     */
    public static final ModConfigSpec.IntValue MUSHROOM_TOWER_CHANCE =
        BUILDER.comment(
            "Percentage chance that the Mushroom Tower (mushroom castle) generates",
            "at a Dense Mushroom Forest landmark center. 0 = vanilla Twilight Forest 4.8 behavior.",
            "浓密蘑菇林地标中心生成蘑菇城堡（蘑菇塔）的百分比概率；设为 0 时与暮色森林原版行为一致。"
        ).translation("config.twilightmoonshine.mushroomCastleChance")
        .defineInRange("mushroomCastleChance", 50, 0, 100);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
