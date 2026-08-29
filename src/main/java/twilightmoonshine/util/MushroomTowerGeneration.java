package twilightmoonshine.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import twilightmoonshine.config.Config;

/**
 * Shared Mushroom Tower dice roll.
 * <p>
 * Both the map-landmark logic ({@code LegacyLandmarkPlacements}) and the
 * engine ({@code ChunkGenerator}) gate on this same pure function, so the
 * magic map and the generated world always agree on a given center.
 */
public final class MushroomTowerGeneration {

    /** Biome context of the landmark center currently being generated (engine-side). */
    public record CenterContext(int centerChunkX, int centerChunkZ, ResourceKey<Biome> biome) {}

    /**
     * Only the worldgen thread sets this: {@code ChunkGenerator.createStructures}
     * records the biome of the center chunk it is evaluating, and
     * {@code pickVarietyLandmark} reads it, so the Dense Mushroom Forest
     * restriction never leaks to other biomes. The server thread's map calls
     * never set it, so they always pass {@code null} here.
     */
    public static final ThreadLocal<CenterContext> CENTER_CONTEXT = new ThreadLocal<>();

    /** True when the tower should generate at this landmark center chunk. */
    public static boolean shouldGenerate(int centerChunkX, int centerChunkZ) {
        int chance = Config.MUSHROOM_TOWER_CHANCE.get();
        if (chance <= 0) return false;
        if (chance >= 100) return true;
        RandomSource rng = RandomSource.create(Mth.getSeed(centerChunkX, 0, centerChunkZ));
        return rng.nextInt(100) < chance;
    }

    /** Recomputes the landmark center chunk coordinate for a chunk lying in a 16-chunk cell. */
    public static int landmarkCenterChunk(int chunkCoord) {
        return Math.round(chunkCoord / 16F) * 16;
    }

    private MushroomTowerGeneration() {}
}
