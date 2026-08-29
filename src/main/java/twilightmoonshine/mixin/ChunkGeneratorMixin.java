package twilightmoonshine.mixin;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import twilightforest.world.components.biomesources.TFBiomeProvider;
import twilightmoonshine.util.MushroomTowerGeneration;
import twilightmoonshine.util.MushroomTowerGeneration.CenterContext;

/**
 * Feeds the engine-side biome context into
 * {@link LegacyLandmarkPlacementsMixin}'s variety dice.
 * <p>
 * {@code createStructures} runs per chunk on the worldgen thread; the biome of
 * the center quad is recorded before any landmark placement runs, and
 * {@code pickVarietyLandmark} reads it. This only affects the worldgen
 * thread's own landmark lookups — the server thread (magic map / locate) has
 * no context and uses {@code pickLandmarkForChunk}'s own biome check instead,
 * so both sides agree on the exact same dice.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {

    // ChunkGenerator#biomeSource 是 protected 字段，mixin 里经 @Accessor 读取
    @Accessor("biomeSource")
    abstract BiomeSource twilightmoonshine$getBiomeSource();

    @Inject(method = "createStructures", at = @At("HEAD"))
    private void twilightmoonshine$recordCenterBiome(RegistryAccess registryAccess,
                                                     ChunkGeneratorStructureState state,
                                                     StructureManager structureManager,
                                                     ChunkAccess chunk,
                                                     StructureTemplateManager templateManager,
                                                     CallbackInfo ci) {
        // structure_starts 阶段（createStructures 所在 chunk 状态）chunk 的 noise biomes
        // 尚未生成（MC 的 biomes 阶段在结构阶段之后），chunk.getNoiseBiome 会抛
        // "Asking for biomes before we have biomes"。改用 TFBiomeProvider#getMainBiome，
        // 与 LandmarkStructure.findValidGenerationPoint 判定"该中心是否能生成塔"时用的
        // 同一查询和同一坐标公式，保证 dice 条件与实际生成一致。
        //
        // 非暮色维度（overworld 等）的 biomeSource 不是 TFBiomeProvider，直接跳过，
        // ctx 保持 null，pickVarietyLandmark 的校验也不会让旧值串扰（见 mixin 的坐标校验）。
        if (!(this.twilightmoonshine$getBiomeSource() instanceof TFBiomeProvider provider)) return;
        int biomeX = (Math.round(chunk.getPos().x / 16F) << 6) + 2;
        int biomeZ = (Math.round(chunk.getPos().z / 16F) << 6) + 2;
        ResourceKey<Biome> key = provider.getMainBiome(biomeX, biomeZ).unwrapKey().orElse(null);
        if (key != null) {
            MushroomTowerGeneration.CENTER_CONTEXT.set(
                new CenterContext(chunk.getPos().x, chunk.getPos().z, key));
        }
    }
}
