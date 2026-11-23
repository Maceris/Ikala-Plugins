package com.ikalagaming.factory.render;

import com.ikalagaming.factory.world.Chunk;
import com.ikalagaming.factory.world.ChunkPos;
import com.ikalagaming.factory.world.World;
import com.ikalagaming.graphics.scene.WaterBody;
import com.ikalagaming.graphics.scene.WaterTile;

import lombok.NonNull;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Turns the water blocks under one square of the world into Graphics' water tiles: each column's
 * surface is the top of water under air, and its bottom is the ground under that water. A square is
 * {@value #SIZE} blocks on a side, one tile per surface height in it, so a sea and a pond above it
 * in the same square are two tiles. Each tile holds the water of the biome most of its surface is
 * in. Pure: the caller reads chunks and makes bodies. See the Water design doc.
 */
public final class WaterTiles {

    /** How many blocks along each side of a square: one tile's most cells. */
    public static final int SIZE = WaterTile.MAX_CELLS;

    /** How many chunks along each side of a square. */
    public static final int CHUNKS = SIZE / World.CHUNK_SIZE;

    /**
     * A square of the world, in squares of {@value #SIZE} blocks.
     *
     * @param x Along x.
     * @param z Along z.
     */
    public record Square(int x, int z) {
        /**
         * The square a chunk is in.
         *
         * @param pos The chunk.
         * @return Its square.
         */
        public static Square of(@NonNull ChunkPos pos) {
            return new Square(Math.floorDiv(pos.x(), CHUNKS), Math.floorDiv(pos.z(), CHUNKS));
        }
    }

    /**
     * The water in one column at one surface.
     *
     * @param bottom The height of the ground under it, in blocks.
     * @param biome The biome of its top water block.
     */
    private record Column(int bottom, @NonNull String biome) {}

    /** Not made. */
    private WaterTiles() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }

    /**
     * The water tiles of a square.
     *
     * @param square The square.
     * @param chunks Finds a loaded chunk, or null.
     * @param chunkYs The chunk heights loaded in the square's columns, lowest first.
     * @param isWater Whether a block, by name, is water.
     * @param bodies The water a biome holds.
     * @return The tiles, by surface height, lowest first; none if the square has no water.
     */
    public static List<WaterTile> build(
            @NonNull Square square,
            @NonNull Function<ChunkPos, Chunk> chunks,
            int @NonNull [] chunkYs,
            @NonNull Predicate<String> isWater,
            @NonNull Function<String, WaterBody> bodies) {
        if (chunkYs.length == 0) {
            return List.of();
        }
        // Surface height to each column's water at that height, by cell index
        final Map<Integer, Column[]> surfaces = new TreeMap<>();
        final int top = (chunkYs[chunkYs.length - 1] + 1) * World.CHUNK_SIZE - 1;
        final int lowest = chunkYs[0] * World.CHUNK_SIZE;
        final Map<ChunkPos, Chunk> cache = new HashMap<>();
        for (int cz = 0; cz < SIZE; ++cz) {
            for (int cx = 0; cx < SIZE; ++cx) {
                final long x = (long) square.x() * SIZE + cx;
                final long z = (long) square.z() * SIZE + cz;
                scanColumn(chunks, cache, x, z, top, lowest, isWater, surfaces, cz * SIZE + cx);
            }
        }
        final List<WaterTile> tiles = new ArrayList<>();
        surfaces.forEach((surface, columns) -> tiles.add(tile(square, surface, columns, bodies)));
        return tiles;
    }

    /**
     * Find every surface in one column, from the top down.
     *
     * @param chunks Finds a loaded chunk.
     * @param cache Chunks looked up so far.
     * @param x The column's x.
     * @param z The column's z.
     * @param top The highest loaded block height.
     * @param lowest The lowest loaded block height.
     * @param isWater Whether a block is water.
     * @param surfaces Gets each surface's water, by surface height.
     * @param cell The column's cell index in the square.
     */
    private static void scanColumn(
            @NonNull Function<ChunkPos, Chunk> chunks,
            @NonNull Map<ChunkPos, Chunk> cache,
            long x,
            long z,
            int top,
            int lowest,
            @NonNull Predicate<String> isWater,
            @NonNull Map<Integer, Column[]> surfaces,
            int cell) {
        // Above the loaded blocks counts as air
        boolean airAbove = true;
        int y = top;
        while (y >= lowest) {
            final String block = blockAt(chunks, cache, x, y, z);
            if (block == null || !isWater.test(block)) {
                airAbove = block == null || World.AIR_NAME.equals(block);
                --y;
                continue;
            }
            // The top of a run of water
            final int waterTop = y;
            final String biome = biomeAt(chunks, cache, x, waterTop, z);
            while (y >= lowest) {
                final String below = blockAt(chunks, cache, x, y, z);
                if (below == null || !isWater.test(below)) {
                    break;
                }
                --y;
            }
            if (airAbove) {
                surfaces.computeIfAbsent(waterTop + 1, k -> new Column[SIZE * SIZE])[cell] =
                        new Column(y + 1, biome == null ? "" : biome);
            }
            airAbove = false;
        }
    }

    /**
     * Build the tile for one surface height.
     *
     * @param square The square.
     * @param surface The surface's height, the top of its water blocks.
     * @param columns The water at that height, by cell, null where there is none.
     * @param bodies The water a biome holds.
     * @return The tile.
     */
    private static WaterTile tile(
            @NonNull Square square,
            int surface,
            Column @NonNull [] columns,
            @NonNull Function<String, WaterBody> bodies) {
        final boolean[] mask = new boolean[SIZE * SIZE];
        final Map<String, Integer> biomeCounts = new HashMap<>();
        for (int i = 0; i < columns.length; ++i) {
            if (columns[i] != null) {
                mask[i] = true;
                biomeCounts.merge(columns[i].biome(), 1, Integer::sum);
            }
        }
        // Each corner's bottom is the deepest of the water cells around it
        final int corners = SIZE + 1;
        final float[] bottoms = new float[corners * corners];
        for (int z = 0; z <= SIZE; ++z) {
            for (int x = 0; x <= SIZE; ++x) {
                float deepest = 0;
                for (int dz = -1; dz <= 0; ++dz) {
                    for (int dx = -1; dx <= 0; ++dx) {
                        final int cx = x + dx;
                        final int cz = z + dz;
                        if (cx < 0 || cz < 0 || cx >= SIZE || cz >= SIZE) {
                            continue;
                        }
                        final Column column = columns[cz * SIZE + cx];
                        if (column != null) {
                            deepest = Math.min(deepest, column.bottom() - surface);
                        }
                    }
                }
                bottoms[z * corners + x] = deepest;
            }
        }
        final String biome =
                biomeCounts.entrySet().stream()
                        .max(
                                Map.Entry.<String, Integer>comparingByValue()
                                        .thenComparing(Map.Entry.comparingByKey()))
                        .map(Map.Entry::getKey)
                        .orElse("");
        // A flat surface at the top of the water blocks
        final float[] heights = new float[corners * corners];
        return new WaterTile(
                new Vector3d((double) square.x() * SIZE, surface, (double) square.z() * SIZE),
                1,
                SIZE,
                SIZE,
                heights,
                bottoms,
                mask,
                bodies.apply(biome));
    }

    /**
     * The block at a position.
     *
     * @param chunks Finds a loaded chunk.
     * @param cache Chunks looked up so far.
     * @param x Its x.
     * @param y Its y.
     * @param z Its z.
     * @return Its name, or null if its chunk isn't loaded.
     */
    private static String blockAt(
            @NonNull Function<ChunkPos, Chunk> chunks,
            @NonNull Map<ChunkPos, Chunk> cache,
            long x,
            long y,
            long z) {
        final Chunk chunk = chunkAt(chunks, cache, x, y, z);
        return chunk == null
                ? null
                : chunk.getBlock(
                                (int) Math.floorMod(x, World.CHUNK_SIZE),
                                (int) Math.floorMod(y, World.CHUNK_SIZE),
                                (int) Math.floorMod(z, World.CHUNK_SIZE))
                        .getName();
    }

    /**
     * The biome at a position.
     *
     * @param chunks Finds a loaded chunk.
     * @param cache Chunks looked up so far.
     * @param x Its x.
     * @param y Its y.
     * @param z Its z.
     * @return Its biome, or null if its chunk isn't loaded.
     */
    private static String biomeAt(
            @NonNull Function<ChunkPos, Chunk> chunks,
            @NonNull Map<ChunkPos, Chunk> cache,
            long x,
            long y,
            long z) {
        final Chunk chunk = chunkAt(chunks, cache, x, y, z);
        return chunk == null
                ? null
                : chunk.getBiome(
                        (int) Math.floorMod(x, World.CHUNK_SIZE),
                        (int) Math.floorMod(y, World.CHUNK_SIZE),
                        (int) Math.floorMod(z, World.CHUNK_SIZE));
    }

    /**
     * The chunk holding a position.
     *
     * @param chunks Finds a loaded chunk.
     * @param cache Chunks looked up so far, including missing ones.
     * @param x Its x.
     * @param y Its y.
     * @param z Its z.
     * @return The chunk, or null.
     */
    private static Chunk chunkAt(
            @NonNull Function<ChunkPos, Chunk> chunks,
            @NonNull Map<ChunkPos, Chunk> cache,
            long x,
            long y,
            long z) {
        final ChunkPos pos = ChunkPos.containing(x, y, z);
        if (cache.containsKey(pos)) {
            return cache.get(pos);
        }
        final Chunk chunk = chunks.apply(pos);
        cache.put(pos, chunk);
        return chunk;
    }
}
