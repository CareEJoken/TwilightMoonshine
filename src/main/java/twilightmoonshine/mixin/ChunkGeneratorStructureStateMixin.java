package twilightmoonshine.mixin;

import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link ChunkGeneratorStructureState}'s private biome source, so
 * {@link LandmarkGridPlacementMixin} can resolve the center biome on the
 * server thread — mirroring the engine-side query in {@link ChunkGeneratorMixin}.
 */
@Mixin(ChunkGeneratorStructureState.class)
public interface ChunkGeneratorStructureStateMixin {

    // ChunkGeneratorStructureState#biomeSource 是 private final 字段，无 getter
    @Accessor("biomeSource")
    BiomeSource twilightmoonshine$getBiomeSource();
}
