package twilightmoonshine.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import twilightforest.init.TFBiomes;
import twilightforest.init.TFStructures;
import twilightforest.util.landmarks.LegacyLandmarkPlacements;
import twilightforest.world.components.biomesources.TFBiomeProvider;
import twilightforest.world.components.structures.placements.LandmarkGridPlacement;
import twilightmoonshine.config.Config;
import twilightmoonshine.util.MushroomTowerGeneration;
import twilightmoonshine.util.MushroomTowerGeneration.CenterContext;

import java.util.Optional;

/**
 * Makes {@code /locate} (and any other structure search on the server thread)
 * aware of the Mushroom Tower on the landmark grid.
 * <p>
 * TF locates landmark structures through {@code WorldUtil.findNearestMapLandmark},
 * which accepts a center only when {@link LandmarkGridPlacement#isPlacementChunk}
 * passes — that is, when {@code pickVarietyLandmark} picks the placement's lock.
 * The tower's row in that dice table was commented out in TF, and the config dice
 * in {@link LegacyLandmarkPlacementsMixin} only fires on the worldgen thread (it
 * reads a thread-local biome context set by {@link ChunkGeneratorMixin}). So the
 * engine generates towers while the server thread's locate search never accepts a
 * center for them — exactly the reported symptom.
 * <p>
 * This injection runs the same decision the engine made — center chunk,
 * dense mushroom forest, config dice — resolving the biome from
 * {@link ChunkGeneratorStructureState} instead of the thread-local.
 * Non-tower placements and the worldgen thread (which has its own context) fall
 * through to vanilla logic.
 */
@Mixin(value = LandmarkGridPlacement.class, remap = false)
public abstract class LandmarkGridPlacementMixin {

    @Shadow(remap = false)
    @Final
    private Optional<ResourceKey<Structure>> landmark;

    @Inject(method = "isPlacementChunk", at = @At("HEAD"), cancellable = true)
    private void twilightmoonshine$mushroomTowerOnServerThread(ChunkGeneratorStructureState state,
                                                                int chunkX,
                                                                int chunkZ,
                                                                CallbackInfoReturnable<Boolean> cir) {
        // 引擎生成线程有自己的上下文（CENTER_CONTEXT），pickVarietyLandmark 的注入会返回塔，
        // 这里只接管没有上下文的调用方（服务器线程的 locate 等）。二者用同一骰子，结果一致。
        CenterContext ctx = MushroomTowerGeneration.CENTER_CONTEXT.get();
        if (ctx != null) return;

        // 只对锁定蘑菇塔的 placement 接管；其它 landmark 结构本来就在 TF 的骰子表里，
        // 服务线程的原有判定就是正确的。isPlacementChunk 会被逐 chunk 调用（locate 的
        // 网格扫描、StructureCheck 等），所以先排斥非中心 chunk——与 vanilla 的结果一致
        // （vanilla 对非中心返回 false）。
        if (this.landmark.isEmpty() || !this.landmark.get().equals(TFStructures.MUSHROOM_TOWER)) return;
        if (!LegacyLandmarkPlacements.chunkHasLandmarkCenter(chunkX, chunkZ)) {
            cir.setReturnValue(false);
            return;
        }

        // 与引擎侧一致的判定：中心 chunk 位于 Dense Mushroom Forest 且骰子通过才算"归属塔"。
        // vanilla 的 pickVarietyLandmark 表里没有塔，此路返回恒为 false；这里替它补上
        // 引擎同一套判定，使服务器线程（locate）与生成线程看到同一结果。
        BiomeSource biomeSource = ((ChunkGeneratorStructureStateMixin) (Object) state).twilightmoonshine$getBiomeSource();
        if (!(biomeSource instanceof TFBiomeProvider provider)) return;
        int biomeX = (Math.round(chunkX / 16F) << 6) + 2;
        int biomeZ = (Math.round(chunkZ / 16F) << 6) + 2;
        ResourceKey<Biome> key = provider.getMainBiome(biomeX, biomeZ).unwrapKey().orElse(null);
        boolean towerCenter = key != null && key.equals(TFBiomes.DENSE_MUSHROOM_FOREST)
            && MushroomTowerGeneration.shouldGenerate(
                MushroomTowerGeneration.landmarkCenterChunk(chunkX),
                MushroomTowerGeneration.landmarkCenterChunk(chunkZ)
            );
        cir.setReturnValue(towerCenter);
    }
}
