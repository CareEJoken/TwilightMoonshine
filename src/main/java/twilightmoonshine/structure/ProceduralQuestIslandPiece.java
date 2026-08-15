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
import twilightmoonshine.config.Config;
import twilightmoonshine.util.SchematicReader;
import twilightmoonshine.util.SchematicReader.Schematic;

import java.io.IOException;
import java.io.InputStream;

/**
 * Places a WorldEdit .schem file as the quest island structure.
 * <p>
 * The schematic is centered horizontally on the structure origin and
 * centered vertically so the island straddles the lake surface.
 * Place the .schem export at
 * {@code data/twilightforest/structure/quest_island/quest_island.schem}
 * in the mod's resources.
 */
public class ProceduralQuestIslandPiece extends StructurePiece {

    private static final String SCHEM_PATH =
        "data/twilightforest/structure/quest_island/quest_island.schem";

    /** Vertical offset applied to the schematic placement (blocks). */
    private static final int Y_OFFSET = 17;
    private static Schematic CACHED_SCHEMATIC;
    private static boolean loadAttempted;

    private final int originX;
    private final int originY;
    private final int originZ;

    public ProceduralQuestIslandPiece(int cx, int y, int cz) {
        super(TwilightMoonshine.QUEST_ISLAND_PIECE.value(), 0,
            makeBoundingBox(cx, y, cz));
        this.originX = cx;
        this.originY = y;
        this.originZ = cz;
    }

    public ProceduralQuestIslandPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(TwilightMoonshine.QUEST_ISLAND_PIECE.value(), tag);
        this.originX = tag.getInt("OX");
        this.originY = tag.getInt("OY");
        this.originZ = tag.getInt("OZ");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("OX", this.originX);
        tag.putInt("OY", this.originY);
        tag.putInt("OZ", this.originZ);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager,
                            ChunkGenerator generator, RandomSource random,
                            BoundingBox structureBoundingBox, ChunkPos chunkPos, BlockPos pos) {

        Schematic schem = getSchematic();
        if (schem == null) {
            TwilightMoonshine.LOGGER.error("QuestIsland: failed to load {}", SCHEM_PATH);
            return;
        }

        // Center horizontally; center vertically on originY so the island
        // straddles the water surface regardless of which dimension the
        // schematic was exported from.
        int baseX = originX - schem.width() / 2;
        int baseY = originY - schem.height() / 2 + Y_OFFSET;
        int baseZ = originZ - schem.length() / 2;

        // Intersection of schematic with the CHUNK-LOCAL bounding box.
        // Use structureBoundingBox (parameter), NOT boundingBox (field —
        // that's the full structure extent).
        int startX = Math.max(0, structureBoundingBox.minX() - baseX);
        int startY = Math.max(0, structureBoundingBox.minY() - baseY);
        int startZ = Math.max(0, structureBoundingBox.minZ() - baseZ);
        int endX = Math.min(schem.width() - 1, structureBoundingBox.maxX() - baseX);
        int endY = Math.min(schem.height() - 1, structureBoundingBox.maxY() - baseY);
        int endZ = Math.min(schem.length() - 1, structureBoundingBox.maxZ() - baseZ);

        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        double glassRatio = Config.LIGHT_BLUE_TO_BLUE_GLASS_RATIO.get();

        // Place schematic blocks in this chunk — flag 0 = no updates,
        // bulk placement during worldgen doesn't need them.
        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    BlockState state = schem.blockAt(x, y, z);
                    if (state.isAir()) continue;

                    // Randomly replace light_blue_stained_glass with blue_stained_glass
                    if (state.is(Blocks.LIGHT_BLUE_STAINED_GLASS)
                        && random.nextDouble() < glassRatio) {
                        state = Blocks.BLUE_STAINED_GLASS.defaultBlockState();
                    }

                    mpos.set(baseX + x, baseY + y, baseZ + z);
                    level.setBlock(mpos, state, 0);
                }
            }
        }
    }

    private static Schematic getSchematic() {
        if (!loadAttempted) {
            loadAttempted = true;
            // Try classpath root first (with leading /), then classloader.
            InputStream raw = ProceduralQuestIslandPiece.class.getResourceAsStream("/" + SCHEM_PATH);
            if (raw == null) {
                raw = ProceduralQuestIslandPiece.class.getClassLoader()
                    .getResourceAsStream(SCHEM_PATH);
            }
            InputStream in = raw;
            if (in != null) {
                try (in) {
                    CACHED_SCHEMATIC = SchematicReader.read(in);
                    TwilightMoonshine.LOGGER.info("QuestIsland: loaded schematic {} ({}x{}x{})",
                        SCHEM_PATH, CACHED_SCHEMATIC.width(),
                        CACHED_SCHEMATIC.height(), CACHED_SCHEMATIC.length());
                } catch (IOException e) {
                    TwilightMoonshine.LOGGER.error("QuestIsland: error reading {}", SCHEM_PATH, e);
                }
            } else {
                TwilightMoonshine.LOGGER.error("QuestIsland: resource not found — {}", SCHEM_PATH);
            }
        }
        return CACHED_SCHEMATIC;
    }

    private static BoundingBox makeBoundingBox(int cx, int y, int cz) {
        Schematic s = getSchematic();
        int hw, hd, hUp, hDown;
        if (s != null) {
            hw = s.width() / 2 + 2;
            hd = s.length() / 2 + 2;
            hUp = s.height() / 2 + 2;
            hDown = s.height() / 2 + 2;
        } else {
            hw = 22; hd = 22; hUp = 10; hDown = 10;
        }
        return new BoundingBox(cx - hw, y - hDown, cz - hd,
                               cx + hw, y + hUp,  cz + hd);
    }
}
