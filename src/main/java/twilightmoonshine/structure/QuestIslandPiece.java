package twilightmoonshine.structure;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import twilightforest.init.TFBlocks;
import twilightmoonshine.TwilightMoonshine;

public class QuestIslandPiece extends StructurePiece {

    private static final ResourceLocation TEMPLATE_LOC =
            ResourceLocation.fromNamespaceAndPath("twilightforest", "quest_island/quest_island");

    private final int centerX;
    private final int centerZ;
    private final int structureY;

    public QuestIslandPiece(int cx, int y, int cz) {
        super(TwilightMoonshine.QUEST_ISLAND_PIECE.value(), 0,
                new BoundingBox(cx - 8, y - 7, cz - 7, cx + 8, y + 14, cz + 8));
        this.centerX = cx;
        this.centerZ = cz;
        this.structureY = y;
    }

    public QuestIslandPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(TwilightMoonshine.QUEST_ISLAND_PIECE.value(), tag);
        this.centerX = tag.getInt("CX");
        this.centerZ = tag.getInt("CZ");
        this.structureY = tag.getInt("SY");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("CX", this.centerX);
        tag.putInt("CZ", this.centerZ);
        tag.putInt("SY", this.structureY);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager,
                             ChunkGenerator generator, RandomSource random,
                             BoundingBox structureBoundingBox, ChunkPos chunkPos, BlockPos pos) {

        StructureTemplateManager templateManager = level.getLevel().getStructureManager();
        StructureTemplate template = templateManager.getOrCreate(TEMPLATE_LOC);
        BlockPos placementPos = new BlockPos(centerX - 8, structureY - 5, centerZ - 7);

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setBoundingBox(this.boundingBox);

        template.placeInWorld(level, placementPos, placementPos, settings,
                RandomSource.create(level.getSeed() + centerX * 49157L + centerZ * 61657L),
                2);

        // Randomize grass vs moss blocks (50% chance each)
        RandomSource decoRNG = RandomSource.create(level.getSeed() ^ centerX * 3129871L + centerZ * 116129781L);
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int x = boundingBox.minX(); x <= boundingBox.maxX(); x++) {
            for (int y = boundingBox.minY(); y <= boundingBox.maxY(); y++) {
                for (int z = boundingBox.minZ(); z <= boundingBox.maxZ(); z++) {
                    mpos.set(x, y, z);
                    if (!boundingBox.isInside(mpos)) continue;
                    BlockState state = level.getBlockState(mpos);
                    if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MOSS_BLOCK)) {
                        // skip if water above
                        BlockPos above = mpos.above();
                        if (boundingBox.isInside(above) && level.getBlockState(above).is(Blocks.WATER)) {
                            continue;
                        }
                        // 1:1 random
                        level.setBlock(mpos, decoRNG.nextBoolean()
                                ? Blocks.GRASS_BLOCK.defaultBlockState()
                                : Blocks.MOSS_BLOCK.defaultBlockState(), 2);

                        // randomize plant above: 60% air, 15% grass, 10% moss carpet, 5% mayapple, 5% fiddlehead, 5% tall grass
                        if (boundingBox.isInside(above)) {
                            BlockState aboveState = level.getBlockState(above);
                            if (aboveState.is(Blocks.AIR) || aboveState.is(Blocks.MOSS_CARPET)
                                    || aboveState.is(TFBlocks.FIDDLEHEAD.get())
                                    || aboveState.is(Blocks.SHORT_GRASS)
                                    || aboveState.is(Blocks.TALL_GRASS)) {
                                // clear old tall grass upper half
                                if (aboveState.is(Blocks.TALL_GRASS) && boundingBox.isInside(above.above())) {
                                    level.setBlock(above.above(), Blocks.AIR.defaultBlockState(), 3);
                                }
                                int r = decoRNG.nextInt(100);
                                if (r < 60) {
                                    level.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
                                } else if (r < 75) {
                                    level.setBlock(above, Blocks.SHORT_GRASS.defaultBlockState(), 2);
                                } else if (r < 85) {
                                    level.setBlock(above, Blocks.MOSS_CARPET.defaultBlockState(), 2);
                                } else if (r < 90) {
                                    level.setBlock(above, TFBlocks.MAYAPPLE.get().defaultBlockState(), 2);
                                } else if (r < 95) {
                                    level.setBlock(above, TFBlocks.FIDDLEHEAD.get().defaultBlockState(), 2);
                                } else {
                                    level.setBlock(above, Blocks.TALL_GRASS.defaultBlockState()
                                            .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), 3);
                                    if (boundingBox.isInside(above.above())) {
                                        level.setBlock(above.above(), Blocks.TALL_GRASS.defaultBlockState()
                                                .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), 3);
                                    }
                                }
                            }
                        }
                    } else if (state.is(Blocks.STONE_BRICKS) || state.is(Blocks.MOSSY_STONE_BRICKS)) {
                        // 3:7 random — 70% stone bricks, 30% mossy
                        level.setBlock(mpos, decoRNG.nextInt(10) < 7
                                ? Blocks.STONE_BRICKS.defaultBlockState()
                                : Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), 2);
                    } else if (state.is(Blocks.STONE_BRICK_STAIRS) || state.is(Blocks.MOSSY_STONE_BRICK_STAIRS)) {
                        // 3:7 random — 70% stone brick stairs, 30% mossy
                        BlockState replacement = decoRNG.nextInt(10) < 7
                                ? Blocks.STONE_BRICK_STAIRS.withPropertiesOf(state)
                                : Blocks.MOSSY_STONE_BRICK_STAIRS.withPropertiesOf(state);
                        level.setBlock(mpos, replacement, 2);
                    } else if (state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE)) {
                        // 3:7 random — 30% cobble, 70% mossy
                        level.setBlock(mpos, decoRNG.nextInt(10) < 7
                                ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                                : Blocks.COBBLESTONE.defaultBlockState(), 2);
                    } else if ((state.is(Blocks.AIR) || state.is(Blocks.COBBLESTONE_WALL) || state.is(Blocks.MOSSY_COBBLESTONE_WALL))
                            && isCobblestoneBelow(level, mpos, boundingBox)) {
                        // above cobblestone: 50% air, 35% mossy wall, 15% cobble wall
                        int r = decoRNG.nextInt(100);
                        if (r < 50) {
                            level.setBlock(mpos, Blocks.AIR.defaultBlockState(), 3);
                        } else if (r < 85) {
                            level.setBlock(mpos, Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState(), 11);
                        } else {
                            level.setBlock(mpos, Blocks.COBBLESTONE_WALL.defaultBlockState(), 11);
                        }
                    }
                }
            }
        }

        // Recalculate wall connections explicitly, one wall at a time.
        // We cannot rely on updateNeighbourShapes during worldgen because its
        // internal Block.updateOrDestroy call may skip setBlock when the state
        // appears unchanged to the chunk. Instead, compute the corrected state
        // via updateShape per direction and setBlock it directly.
        mpos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos nPos = new BlockPos.MutableBlockPos();
        for (int x = boundingBox.minX(); x <= boundingBox.maxX(); x++) {
            for (int y = boundingBox.minY(); y <= boundingBox.maxY(); y++) {
                for (int z = boundingBox.minZ(); z <= boundingBox.maxZ(); z++) {
                    mpos.set(x, y, z);
                    if (!boundingBox.isInside(mpos)) continue;
                    BlockState state = level.getBlockState(mpos);
                    if (!(state.is(Blocks.COBBLESTONE_WALL) || state.is(Blocks.MOSSY_COBBLESTONE_WALL)))
                        continue;

                    // Compute correct wall shape from all 6 neighbors
                    BlockState corrected = state;
                    for (Direction dir : Direction.values()) {
                        nPos.setWithOffset(mpos, dir);
                        BlockState nState = level.getBlockState(nPos);
                        corrected = corrected.updateShape(dir, nState, level, mpos, nPos);
                    }

                    if (corrected != state) {
                        level.setBlock(mpos, corrected, Block.UPDATE_ALL_IMMEDIATE);
                    }
                }
            }
        }
    }

    private static boolean isCobblestoneBelow(WorldGenLevel level, BlockPos pos, BoundingBox bb) {
        BlockPos below = pos.below();
        if (!bb.isInside(below)) return false;
        BlockState belowState = level.getBlockState(below);
        return belowState.is(Blocks.COBBLESTONE) || belowState.is(Blocks.MOSSY_COBBLESTONE);
    }
}
