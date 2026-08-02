package twilightmoonshine.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import twilightmoonshine.TwilightMoonshine;

public class QuestIslandPiece extends StructurePiece {

    private final int centerX;
    private final int centerZ;
    private final int islandY;

    public QuestIslandPiece(int cx, int waterLevel, int cz) {
        super(TwilightMoonshine.QUEST_ISLAND_PIECE.value(), 0,
                new BoundingBox(cx - 8, waterLevel - 3, cz - 8, cx + 8, waterLevel + 6, cz + 8));
        this.centerX = cx;
        this.centerZ = cz;
        this.islandY = waterLevel + 1;
    }

    public QuestIslandPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(TwilightMoonshine.QUEST_ISLAND_PIECE.value(), tag);
        this.centerX = tag.getInt("CX");
        this.centerZ = tag.getInt("CZ");
        this.islandY = tag.getInt("IY");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("CX", this.centerX);
        tag.putInt("CZ", this.centerZ);
        tag.putInt("IY", this.islandY);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager,
                             ChunkGenerator generator, RandomSource random,
                             BoundingBox structureBoundingBox, ChunkPos chunkPos, BlockPos pos) {
        int waterLevel = this.islandY - 1;

        // Island base — circular, radius 5
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (dx * dx + dz * dz > 25) continue;
                if (dx == 0 && dz == 0) continue;  // well shaft — skip island surface here
                place(level, Blocks.GRASS_BLOCK.defaultBlockState(), centerX + dx, islandY, centerZ + dz, structureBoundingBox);
                place(level, Blocks.DIRT.defaultBlockState(), centerX + dx, islandY - 1, centerZ + dz, structureBoundingBox);
                place(level, Blocks.STONE.defaultBlockState(), centerX + dx, islandY - 2, centerZ + dz, structureBoundingBox);
                place(level, Blocks.STONE.defaultBlockState(), centerX + dx, islandY - 3, centerZ + dz, structureBoundingBox);
            }
        }

        // Sea lantern at the bottom of the well shaft (replaces water block at surface)
        place(level, Blocks.SEA_LANTERN.defaultBlockState(), centerX, waterLevel, centerZ, structureBoundingBox);

        // Well walls — 3×3 ring, hollow center, 2 blocks tall
        for (int dy = 1; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    place(level, Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
                            centerX + dx, islandY + dy, centerZ + dz, structureBoundingBox);
                }
            }
        }
    }

    private static void place(WorldGenLevel level, BlockState state, int x, int y, int z, BoundingBox bb) {
        BlockPos pos = new BlockPos(x, y, z);
        if (bb.isInside(pos)) {
            level.setBlock(pos, state, 2);
        }
    }
}
