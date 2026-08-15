package twilightmoonshine.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/**
 * Reads WorldEdit .schem files (Sponge Schematic v2/v3).
 *
 * <p>The .schem format uses a palette + varint-encoded block data array,
 * making it much more compact than vanilla structure NBT — suitable for
 * large builds that exceed the 48³ structure-block limit.
 *
 * <p>Usage:
 * <pre>{@code
 *   Schematic schematic = SchematicReader.read(inputStream);
 *   for (int x = 0; x < schematic.width(); x++) {
 *       for (int y = 0; y < schematic.height(); y++) {
 *           for (int z = 0; z < schematic.length(); z++) {
 *               BlockState state = schematic.blockAt(x, y, z);
 *               // ...
 *           }
 *       }
 *   }
 * }</pre>
 */
public final class SchematicReader {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SchematicReader() {}

    public static Schematic read(InputStream input) throws IOException {
        CompoundTag tag = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
        int version = tag.getInt("Version");

        short width  = tag.getShort("Width");
        short height = tag.getShort("Height");
        short length = tag.getShort("Length");

        int[] offset = tag.contains("Offset") ? tag.getIntArray("Offset") : new int[]{0, 0, 0};

        // --- palette ---
        CompoundTag paletteTag = tag.getCompound("Palette");
        int paletteMax = tag.getInt("PaletteMax");
        BlockState[] palette = buildPalette(paletteTag, paletteMax);

        // --- block data: varint-encoded, YZX order ---
        byte[] raw = tag.getByteArray("BlockData");
        int blockCount = width * height * length;
        BlockState[] blocks = new BlockState[blockCount];
        ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < blockCount; i++) {
            int paletteIdx = readVarInt(buf);
            // Sponge v2+: YZX order  →  index = (y * length + z) * width + x
            int y = i / (length * width);
            int remainder = i % (length * width);
            int z = remainder / width;
            int x = remainder % width;
            blocks[i] = palette[paletteIdx];
        }

        // Reorder from YZX to XYZ for easier access
        BlockState[] xyz = new BlockState[blockCount];
        for (int i = 0; i < blockCount; i++) {
            int y = i / (length * width);
            int rem = i % (length * width);
            int z = rem / width;
            int x = rem % width;
            xyz[x + z * width + y * width * length] = blocks[i];
        }

        return new Schematic(width, height, length, offset, xyz);
    }

    /**
     * Read a Minecraft-style variable-length integer from the buffer.
     */
    private static int readVarInt(ByteBuffer buf) {
        int value = 0;
        int shift = 0;
        while (true) {
            int b = buf.get() & 0xFF;
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }
        return value;
    }

    /**
     * Build palette: palette tag maps "blockstate_string" → integer index.
     * This method reverses it to array index → BlockState.
     */
    private static BlockState[] buildPalette(CompoundTag paletteTag, int paletteMax) {
        BlockState[] palette = new BlockState[paletteMax];

        int unrecognized = 0;
        for (String key : paletteTag.getAllKeys()) {
            int index = paletteTag.getInt(key);
            palette[index] = parseBlockState(key);
            if (palette[index].isAir() && !key.contains("air") && !key.contains("Air")) {
                unrecognized++;
                if (unrecognized <= 5) {
                    LOGGER.warn("Schematic: unrecognized block '{}' → air", key);
                }
            }
        }
        if (unrecognized > 0) {
            LOGGER.warn("Schematic: {} palette entries not found in registry", unrecognized);
        }

        // Fill any missing entries with AIR
        for (int i = 0; i < palette.length; i++) {
            if (palette[i] == null) {
                palette[i] = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            }
        }

        return palette;
    }

    /**
     * Parse a palette string like "minecraft:stone"
     * or "minecraft:oak_fence[east=true,north=false]" into a BlockState.
     */
    private static BlockState parseBlockState(String raw) {
        // Split into block ID and optional property list
        int bracket = raw.indexOf('[');
        String blockId = bracket < 0 ? raw : raw.substring(0, bracket);

        ResourceLocation rl = ResourceLocation.parse(blockId);
        Block block = BuiltInRegistries.BLOCK.get(rl);

        if (block == null) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }

        BlockState state = block.defaultBlockState();

        if (bracket >= 0 && raw.endsWith("]")) {
            String props = raw.substring(bracket + 1, raw.length() - 1);
            if (!props.isEmpty()) {
                for (String entry : props.split(",")) {
                    String[] kv = entry.split("=", 2);
                    if (kv.length != 2) continue;
                    state = applyProperty(state, block, kv[0].trim(), kv[1].trim());
                }
            }
        }

        return state;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends Comparable<T>> BlockState applyProperty(
            BlockState state, Block block, String name, String value) {
        Property<T> prop = (Property<T>) block.getStateDefinition().getProperty(name);
        if (prop == null) return state;
        return prop.getValue(value).map(v -> state.setValue(prop, v)).orElse(state);
    }

    // ------------------------------------------------------------------ //
    //                          Result type                                //
    // ------------------------------------------------------------------ //

    public record Schematic(int width, int height, int length, int[] offset,
                            BlockState[] blocks) {

        public BlockState blockAt(int x, int y, int z) {
            if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= length) {
                return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            }
            return blocks[index(x, y, z)];
        }

        private int index(int x, int y, int z) {
            return x + z * width + y * width * length;
        }

        /** Returns a new Schematic with all ≤4-block air pockets filled with stone. */
        public Schematic fillSmallCavities() {
            int total = width * height * length;
            boolean[] visited = new boolean[total];
            BlockState[] result = blocks.clone();
            BlockState stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
            BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

            int[] queue = new int[total];
            int[] component = new int[total];

            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        int idx = index(x, y, z);
                        if (visited[idx] || !result[idx].isAir()) continue;

                        // BFS to find connected air component
                        int head = 0, tail = 0;
                        queue[tail++] = idx;
                        visited[idx] = true;
                        int compIdx = 0;
                        component[compIdx++] = idx;

                        while (head < tail) {
                            int cur = queue[head++];
                            int cx = cur % width;
                            int cz = (cur / width) % length;
                            int cy = cur / (width * length);

                            // 6 neighbors
                            int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
                            for (int[] d : dirs) {
                                int nx = cx + d[0], ny = cy + d[1], nz = cz + d[2];
                                if (nx < 0 || nx >= width || ny < 0 || ny >= height || nz < 0 || nz >= length) continue;
                                int nidx = index(nx, ny, nz);
                                if (!visited[nidx] && result[nidx].isAir()) {
                                    visited[nidx] = true;
                                    queue[tail++] = nidx;
                                    component[compIdx++] = nidx;
                                }
                            }
                        }

                        // Fill small pockets with stone
                        if (compIdx <= 4) {
                            for (int i = 0; i < compIdx; i++) {
                                result[component[i]] = stone;
                            }
                        }
                    }
                }
            }

            return new Schematic(width, height, length, offset, result);
        }
    }
}
