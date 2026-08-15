"""
Fill small air pockets (≤4 connected blocks) with stone in a .schem file.

Requires:  pip install nbtlib

Usage:  python fill_cavities.py quest_island.schem [--max-size N] [--dry-run]
"""

import gzip
import io
import sys
from collections import deque
from pathlib import Path

try:
    import nbtlib
except ImportError:
    print("Please install nbtlib first:  pip install nbtlib")
    sys.exit(1)


def fill_small_cavities(schem_path: str, max_size: int = 6, dry_run: bool = False):
    print(f"Reading {schem_path}...")

    # Read .schem (gzip-compressed NBT)
    with gzip.open(schem_path, "rb") as f:
        root = nbtlib.File.from_fileobj(f)

    width  = root["Width"].real
    height = root["Height"].real
    length = root["Length"].real
    print(f"  Dimensions: {width} x {height} x {length} blocks")

    # Build palette: string → index
    palette_tag = root["Palette"]
    palette = {}
    for name, idx in palette_tag.items():
        # nbtlib stores the key as-is; value is a numeric tag
        palette[str(name)] = int(idx.real)

    # Find air index
    air_idx = palette.get("minecraft:air")
    if air_idx is None:
        print("  No air in palette, nothing to do.")
        return

    # Find curing_block index (from hole_filler_mod)
    curing_name = "hole_filler_mod:curing_block"
    curing_idx = palette.get(curing_name, -1)
    if curing_idx >= 0:
        print(f"  Found {curing_name} at palette index {curing_idx}")

    # Add stone to palette if missing
    stone_name = "minecraft:stone"
    if stone_name not in palette:
        new_idx = int(root["PaletteMax"].real)
        palette[stone_name] = new_idx
        palette_tag[stone_name] = nbtlib.tag.Int(new_idx)
        root["PaletteMax"] = nbtlib.tag.Int(new_idx + 1)
        print(f"  Added 'minecraft:stone' to palette as index {new_idx}")
    stone_idx = palette[stone_name]

    # Decode block data (varint byte array → list of palette indices)
    raw = root["BlockData"].real
    total = width * height * length
    print(f"  Decoding {total:,} block entries (varint)...")
    indices = _decode_varints(raw, total)
    print(f"  Parsed {len(indices):,} entries")

    # Blocks treated as fillable void (air + curing blocks from hole_filler_mod)
    void_indices = {air_idx}
    if curing_idx >= 0:
        void_indices.add(curing_idx)

    # YZX → index helper
    def idx3(x, y, z):
        return (y * length + z) * width + x

    # Block types whose neighbors should NOT be filled (marker blocks)
    glass_name = "minecraft:light_blue_stained_glass"
    glass_idx = palette.get(glass_name, -1)

    # Find light block index (may have properties like [level=...])
    # Must NOT match light_blue_stained_glass — use exact or bracket match
    light_idx = -1
    for name, idx in palette.items():
        s = str(name)
        if s == "minecraft:light" or s.startswith("minecraft:light["):
            light_idx = int(idx.real)
            print(f"  Found light block '{name}' at palette index {light_idx}")
            break

    # BFS flood-fill for air pockets
    visited = bytearray(total)
    filled = 0
    pockets_filled = 0
    skipped_glass = 0
    skipped_light = 0
    # stats
    total_air = 0
    size_1 = size_2_4 = size_5_10 = size_11_50 = size_51_plus = 0

    queue = [0] * total
    component = [0] * total

    for y in range(height):
        for z in range(length):
            for x in range(width):
                i = idx3(x, y, z)
                if visited[i]:
                    continue
                if indices[i] not in void_indices:
                    visited[i] = 1
                    continue

                # BFS
                head = tail = 0
                queue[tail] = i; tail += 1
                visited[i] = 1
                comp_size = 0
                component[comp_size] = i; comp_size += 1

                while head < tail:
                    cur = queue[head]; head += 1
                    cx = cur % width
                    cz = (cur // width) % length
                    cy = cur // (width * length)

                    for dx, dy, dz in [(1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)]:
                        nx, ny, nz = cx + dx, cy + dy, cz + dz
                        if 0 <= nx < width and 0 <= ny < height and 0 <= nz < length:
                            ni = idx3(nx, ny, nz)
                            if not visited[ni] and indices[ni] in void_indices:
                                visited[ni] = 1
                                queue[tail] = ni; tail += 1
                                component[comp_size] = ni; comp_size += 1

                # stats
                total_air += comp_size
                if   comp_size == 1:       size_1 += 1
                elif comp_size <= 4:       size_2_4 += 1
                elif comp_size <= 10:      size_5_10 += 1
                elif comp_size <= 50:      size_11_50 += 1
                else:                      size_51_plus += 1

                if comp_size <= max_size:
                    # Check: skip if any neighbor is light_blue_stained_glass or light block
                    near_glass = False
                    near_light = False
                    skip = False
                    for j in range(comp_size):
                        cur = component[j]
                        cx = cur % width
                        cz = (cur // width) % length
                        cy = cur // (width * length)
                        for dx, dy, dz in [(1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)]:
                            nx, ny, nz = cx + dx, cy + dy, cz + dz
                            if 0 <= nx < width and 0 <= ny < height and 0 <= nz < length:
                                ni = idx3(nx, ny, nz)
                                if glass_idx >= 0 and indices[ni] == glass_idx:
                                    near_glass = True
                                    skip = True
                                    break
                                if light_idx >= 0 and indices[ni] == light_idx:
                                    near_light = True
                                    skip = True
                                    break
                        if skip:
                            break
                    if near_glass:
                        skipped_glass += 1
                    elif near_light:
                        skipped_light += 1
                    else:
                        pockets_filled += 1
                        for j in range(comp_size):
                            indices[component[j]] = stone_idx
                        filled += comp_size

    print(f"  Total air blocks: {total_air:,}")
    print(f"  Air components: 1-block={size_1}, 2-4={size_2_4}, 5-10={size_5_10}, 11-50={size_11_50}, 51+={size_51_plus}")
    print(f"  Found {pockets_filled} small air pockets ({filled} blocks → stone)")
    if skipped_glass:
        print(f"  Skipped {skipped_glass} pockets near light_blue_stained_glass")
    if skipped_light:
        print(f"  Skipped {skipped_light} pockets near minecraft:light")

    # Step 2: replace stone → air if any neighbor is a light block
    if light_idx >= 0:
        light_removed = 0
        for y in range(height):
            for z in range(length):
                for x in range(width):
                    i = idx3(x, y, z)
                    if indices[i] != stone_idx:
                        continue
                    has_light = False
                    for dx, dy, dz in [(1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)]:
                        nx, ny, nz = x + dx, y + dy, z + dz
                        if 0 <= nx < width and 0 <= ny < height and 0 <= nz < length:
                            if indices[idx3(nx, ny, nz)] == light_idx:
                                has_light = True
                                break
                    if has_light:
                        indices[i] = air_idx
                        light_removed += 1
        if light_removed:
            print(f"  Light-adjacent stone → air: {light_removed} blocks")
        else:
            print(f"  No stone blocks adjacent to minecraft:light")

    if dry_run:
        print("  [DRY RUN] No file written.")
        return

    # Re-encode varints
    new_data = _encode_varints(indices)
    root["BlockData"] = nbtlib.tag.ByteArray(list(new_data))

    # Write back
    print(f"  Writing {schem_path}...")
    with gzip.open(schem_path, "wb") as f:
        root.write(f)
    print("  Done.")


def _decode_varints(data: bytes, count: int) -> list[int]:
    result = []
    pos = 0
    for _ in range(count):
        value = 0; shift = 0
        while True:
            b = data[pos]; pos += 1
            value |= (b & 0x7F) << shift
            if (b & 0x80) == 0:
                break
            shift += 7
        result.append(value)
    return result


def _encode_varints(values: list[int]) -> bytes:
    parts = bytearray()
    for v in values:
        while True:
            b = v & 0x7F
            v >>= 7
            if v != 0:
                b |= 0x80
            parts.append(b)
            if v == 0:
                break
    return bytes(parts)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    path = sys.argv[1]
    max_sz = 6
    dry = False
    for arg in sys.argv[2:]:
        if arg == "--dry-run":
            dry = True
        elif arg.startswith("--max-size="):
            max_sz = int(arg.split("=")[1])
    fill_small_cavities(path, max_sz, dry)
