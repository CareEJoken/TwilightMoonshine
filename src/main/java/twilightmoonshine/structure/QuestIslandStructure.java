package twilightmoonshine.structure;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import twilightforest.world.components.structures.util.LandmarkStructure;
import twilightmoonshine.TwilightMoonshine;

import java.util.Optional;

public class QuestIslandStructure extends LandmarkStructure {

    public static final MapCodec<QuestIslandStructure> CODEC = RecordCodecBuilder.mapCodec(
            instance -> landmarkCodec(instance).apply(instance, QuestIslandStructure::new));

    public QuestIslandStructure(Optional<DecorationConfig> decorationConfig, boolean centerInChunk,
                                 Optional<Holder<MapDecorationType>> structureIcon,
                                 StructureSettings structureSettings) {
        super(decorationConfig, centerInChunk, structureIcon, structureSettings);
    }

    @Override
    protected StructurePiece getFirstPiece(GenerationContext context, RandomSource random,
                                            ChunkPos chunkPos, int x, int y, int z) {
        // y == getSeaLevel() because terrainAdaptation = NONE
        return new QuestIslandPiece(x, y, z);
    }

    @Override
    public StructureType<?> type() {
        return TwilightMoonshine.QUEST_ISLAND_TYPE.get();
    }
}
