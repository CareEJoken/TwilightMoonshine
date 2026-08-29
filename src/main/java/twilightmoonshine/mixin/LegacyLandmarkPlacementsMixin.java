package twilightmoonshine.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import twilightforest.init.TFBiomes;
import twilightforest.init.TFStructures;
import twilightforest.util.landmarks.LegacyLandmarkPlacements;
import twilightmoonshine.config.Config;
import twilightmoonshine.util.MushroomTowerGeneration;
import twilightmoonshine.util.MushroomTowerGeneration.CenterContext;

import java.util.Optional;

/**
 * Consistent Mushroom Tower selection for the magic map.
 * <p>
 * The tower structure set now carries a {@code structure_grid_lock} (like
 * TF's own landmark sets), so the engine only generates it where
 * {@code pickVarietyLandmark} selects it. This mixin adds the config-driven
 * dice to that same pure function: the engine-side context set by
 * {@link ChunkGeneratorMixin} supplies the biome, and the map side rolls
 * directly in {@code pickLandmarkForChunk} where the LevelReader is in hand.
 */
@Mixin(value = LegacyLandmarkPlacements.class, remap = false)
public class LegacyLandmarkPlacementsMixin {

    /**
     * Engine-side: {@code pickVarietyLandmark} is shared by every landmark
     * set's grid lock. When the worldgen thread is evaluating a center in the
     * Dense Mushroom Forest, roll the dice and make the tower the chosen
     * variety. On a miss it falls through to vanilla logic, so the other
     * landmark sets (hollow hills, etc.) win that center instead.
     */
    @Inject(method = "pickVarietyLandmark", at = @At("HEAD"), cancellable = true)
    private static void mushroomTowerVariety(int chunkX, int chunkZ,
                                             CallbackInfoReturnable<ResourceKey<Structure>> cir) {
        CenterContext ctx = MushroomTowerGeneration.CENTER_CONTEXT.get();
        // 只在引擎生成"当前中心 chunk"的上下文中掷骰子（ctx 里存的是原始 chunk 坐标，
        // 必须与传入参数比较；round 后的标称格坐标不是同一个量）。其它任何调用
        // （例如服务器线程的地图侧 fallback）都会因为 ctx 为 null 而落回原版逻辑。
        if (ctx == null || ctx.centerChunkX() != chunkX || ctx.centerChunkZ() != chunkZ) return;
        if (!ctx.biome().equals(TFBiomes.DENSE_MUSHROOM_FOREST)) return;
        if (Config.MUSHROOM_TOWER_CHANCE.get() <= 0) return;
        int cx = MushroomTowerGeneration.landmarkCenterChunk(chunkX);
        int cz = MushroomTowerGeneration.landmarkCenterChunk(chunkZ);
        if (MushroomTowerGeneration.shouldGenerate(cx, cz)) {
            cir.setReturnValue(TFStructures.MUSHROOM_TOWER);
        }
    }

    /**
     * Map-side: {@code pickLandmarkForChunk} only has a {@link LevelReader} in
     * scope at its own biome lookup. When that lookup resolves the Dense
     * Mushroom Forest and the dice passes, report the tower as the landmark;
     * otherwise let vanilla logic run (it falls back to
     * {@code pickVarietyLandmark}, which matches the engine).
     */
    @Inject(method = "pickLandmarkForChunk", at = @At("HEAD"), cancellable = true)
    private static void mushroomTowerMapLandmark(int chunkX, int chunkZ, LevelReader world,
                                                 CallbackInfoReturnable<ResourceKey<Structure>> cir) {
        if (Config.MUSHROOM_TOWER_CHANCE.get() <= 0) return;
        int cx = MushroomTowerGeneration.landmarkCenterChunk(chunkX);
        int cz = MushroomTowerGeneration.landmarkCenterChunk(chunkZ);
        BlockPos pos = new BlockPos((cx << 4) + 8, 0, (cz << 4) + 8);
        Optional<ResourceKey<Biome>> biome = world.getBiome(pos).unwrapKey();
        if (biome.isPresent() && biome.get().equals(TFBiomes.DENSE_MUSHROOM_FOREST)
            && MushroomTowerGeneration.shouldGenerate(cx, cz)) {
            cir.setReturnValue(TFStructures.MUSHROOM_TOWER);
        }
    }
}
