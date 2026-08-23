package twilightmoonshine.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import twilightforest.init.TFBlocks;
import twilightmoonshine.TwilightMoonshine;
import twilightmoonshine.config.Config;
import twilightmoonshine.entity.MoonRabbit;
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

        // --- 植被：每个草/苔方块上方均匀随机种草/高草/暮色蕨/mayapple ---
        // 保留 schema 里的草/苔分布（不做 50/50 互换），只做"草方块上有植物"这一件事。
        RandomSource decoRng = RandomSource.create(
            Mth.getSeed(originX, originY, originZ) ^ level.getSeed());
        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    if (!schem.blockAt(x, y, z).is(Blocks.GRASS_BLOCK)
                        && !schem.blockAt(x, y, z).is(Blocks.MOSS_BLOCK)) {
                        continue;
                    }
                    mpos.set(baseX + x, baseY + y, baseZ + z);
                    BlockPos above = mpos.above();
                    if (!structureBoundingBox.isInside(above)) continue;
                    BlockState aboveState = level.getBlockState(above);
                    if (!aboveState.isAir()) continue;

                    BlockPos aboveAbove = above.above();
                    int choice = decoRng.nextInt(100);
                    if (choice < 40) {
                        continue; // 40% 保持空气（不放植物）
                    } else if (choice < 55) {
                        level.setBlock(above, Blocks.SHORT_GRASS.defaultBlockState(), 2);
                    } else if (choice < 70) {
                        // 高草：下+上两段，仅当上上格也是空气才放上段
                        level.setBlock(above, Blocks.TALL_GRASS.defaultBlockState()
                            .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), 2);
                        if (structureBoundingBox.isInside(aboveAbove)
                            && level.getBlockState(aboveAbove).isAir()) {
                            level.setBlock(aboveAbove, Blocks.TALL_GRASS.defaultBlockState()
                                .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), 2);
                        }
                    } else if (choice < 85) {
                        level.setBlock(above, TFBlocks.FIDDLEHEAD.get().defaultBlockState(), 2);
                    } else {
                        level.setBlock(above, TFBlocks.MAYAPPLE.get().defaultBlockState(), 2);
                    }
                }
            }
        }

        // --- 月兔：四块石砖/苔石砖（喷泉平台四个角）中确定性选一块生成 ---
        // 选择只依赖世界种子+结构原点，因此所有 chunk 的 postProcess 得到同一块砖；
        // 且只在"选中的砖位于当前 chunk 交集内"时生成 → 恰好生成一次。
        // 位置是新 schema 的石砖/苔石砖（x31/y36/z38 附近，上方为空气，见 schem_analyze）。
        int[] spawnX = {33, 30, 37, 34};
        int[] spawnY = {36, 36, 36, 36};
        int[] spawnZ = {38, 41, 42, 45};
        RandomSource rabbitRng = RandomSource.create(
            Mth.getSeed(originX, originY, originZ) ^ level.getSeed() ^ 0x74756E2CBC1AL);
        int idx = rabbitRng.nextInt(spawnX.length);
        BlockPos spawnPos = new BlockPos(
            baseX + spawnX[idx], baseY + spawnY[idx], baseZ + spawnZ[idx]);
        if (structureBoundingBox.isInside(spawnPos)) {
            BlockState ground = level.getBlockState(spawnPos);
            BlockState spawnCell = level.getBlockState(spawnPos.above());
            if ((ground.is(Blocks.STONE_BRICKS) || ground.is(Blocks.MOSSY_STONE_BRICKS))
                && spawnCell.isAir()) {
                MoonRabbit rabbit = new MoonRabbit(
                    TwilightMoonshine.MOON_RABBIT.get(), level.getLevel());
                rabbit.setPos(spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5);
                rabbit.setYRot(rabbitRng.nextFloat() * 360.0F);
                rabbit.setPersistenceRequired();
                rabbit.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos),
                    MobSpawnType.STRUCTURE, null);
                level.getLevel().addFreshEntity(rabbit);
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
