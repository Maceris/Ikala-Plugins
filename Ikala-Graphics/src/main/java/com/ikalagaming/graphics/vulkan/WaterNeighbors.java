package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.scene.WaterBody;
import com.ikalagaming.graphics.scene.WaterTile;

import lombok.NonNull;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Which tile's water lies across each edge of each tile, so the surface can blend the two waters'
 * optics near the edge rather than show a line where a muddy river meets the clear sea, see {@code
 * water.frag}. A tile's neighbor across an edge is the first tile found holding water just past one
 * of the edge's water cells, nearest the middle first, its surface there within {@link #MAX_STEP}
 * of this one's: a pool below a waterfall's lip isn't one. Tiles are expected to line up, as a
 * game's chunks of water do, so one neighbor stands for a whole edge. {@link #blends} says which
 * tiles have different water beside them, so the rest skip blending.
 */
public final class WaterNeighbors {

    /** The edges, in the order each tile's neighbors are listed: -x, +x, -z, +z. */
    public static final int EDGES = 4;

    /** No neighbor across an edge. */
    public static final int NONE = -1;

    /** The most two surfaces may differ in height at an edge and still be one water, in meters. */
    public static final double MAX_STEP = 0.5;

    /** The size of the buckets tiles are found by, in meters. */
    private static final double BUCKET = 64;

    /** Bits for a bucket's z in its key. */
    private static final int KEY_BITS = 32;

    /** Static helpers only. */
    private WaterNeighbors() {}

    /**
     * Find each tile's neighbors.
     *
     * @param tiles The tiles.
     * @return {@link #EDGES} entries a tile, in the tiles' order: the index of the tile across each
     *     edge, or {@link #NONE}.
     */
    public static int[] find(@NonNull List<WaterTile> tiles) {
        final Map<Long, List<Integer>> buckets = new HashMap<>();
        for (int i = 0; i < tiles.size(); ++i) {
            final WaterTile tile = tiles.get(i);
            final Vector3dc origin = tile.origin();
            final int lowX = bucket(origin.x());
            final int lowZ = bucket(origin.z());
            final int highX = bucket(origin.x() + tile.cellsX() * (double) tile.cellSize());
            final int highZ = bucket(origin.z() + tile.cellsZ() * (double) tile.cellSize());
            for (int z = lowZ; z <= highZ; ++z) {
                for (int x = lowX; x <= highX; ++x) {
                    buckets.computeIfAbsent(key(x, z), k -> new ArrayList<>()).add(i);
                }
            }
        }
        final int[] neighbors = new int[tiles.size() * EDGES];
        for (int i = 0; i < tiles.size(); ++i) {
            final WaterTile tile = tiles.get(i);
            for (int edge = 0; edge < EDGES; ++edge) {
                neighbors[i * EDGES + edge] = across(tiles, buckets, i, tile, edge);
            }
        }
        return neighbors;
    }

    /**
     * The tile across one edge of a tile: the first found holding water at its surface's height,
     * probing just past each of the edge's cells that holds water, from the middle out, so land at
     * the middle of an edge doesn't hide the water along the rest.
     *
     * @param tiles The tiles.
     * @param buckets The tiles by bucket.
     * @param self The tile's index.
     * @param tile The tile.
     * @param edge Which edge.
     * @return The neighbor's index, or {@link #NONE}.
     */
    private static int across(
            @NonNull List<WaterTile> tiles,
            @NonNull Map<Long, List<Integer>> buckets,
            int self,
            @NonNull WaterTile tile,
            int edge) {
        final boolean alongX = edge < 2;
        final boolean high = (edge & 1) == 1;
        final int cells = alongX ? tile.cellsZ() : tile.cellsX();
        final int middle = cells / 2;
        for (int step = 0; step < cells; ++step) {
            // middle, middle - 1, middle + 1, middle - 2, ...
            final int offset = (step + 1) / 2 * ((step & 1) == 1 ? -1 : 1);
            final int along = middle + offset;
            if (along < 0 || along >= cells) {
                continue;
            }
            final int cellX = alongX ? (high ? tile.cellsX() - 1 : 0) : along;
            final int cellZ = alongX ? along : (high ? tile.cellsZ() - 1 : 0);
            if (!tile.isWater(cellX, cellZ)) {
                continue;
            }
            final int found = probe(tiles, buckets, self, tile, cellX, cellZ, alongX, high);
            if (found != NONE) {
                return found;
            }
        }
        return NONE;
    }

    /**
     * The tile holding water just past one of a tile's edge cells, at its surface's height.
     *
     * @param tiles The tiles.
     * @param buckets The tiles by bucket.
     * @param self The tile's index.
     * @param tile The tile.
     * @param cellX The edge cell along x.
     * @param cellZ The edge cell along z.
     * @param alongX Whether the edge faces along x.
     * @param high Whether it faces the positive way.
     * @return The tile's index, or {@link #NONE}.
     */
    private static int probe(
            @NonNull List<WaterTile> tiles,
            @NonNull Map<Long, List<Integer>> buckets,
            int self,
            @NonNull WaterTile tile,
            int cellX,
            int cellZ,
            boolean alongX,
            boolean high) {
        final double size = tile.cellSize();
        final double outward = high ? 1.5 : -0.5;
        final double x = tile.origin().x() + (cellX + (alongX ? outward : 0.5)) * size;
        final double z = tile.origin().z() + (cellZ + (alongX ? 0.5 : outward)) * size;
        // The surface's height at the edge's corner by the cell
        final int cornerX = alongX ? (high ? cellX + 1 : cellX) : cellX;
        final int cornerZ = alongX ? cellZ : (high ? cellZ + 1 : cellZ);
        final double height = tile.origin().y() + tile.heightAt(cornerX, cornerZ);
        final List<Integer> near = buckets.get(key(bucket(x), bucket(z)));
        if (near == null) {
            return NONE;
        }
        for (int other : near) {
            if (other == self) {
                continue;
            }
            final WaterTile candidate = tiles.get(other);
            final double localX = (x - candidate.origin().x()) / candidate.cellSize();
            final double localZ = (z - candidate.origin().z()) / candidate.cellSize();
            if (localX < 0
                    || localZ < 0
                    || localX >= candidate.cellsX()
                    || localZ >= candidate.cellsZ()
                    || !candidate.isWater((int) localX, (int) localZ)) {
                continue;
            }
            final double theirs =
                    candidate.origin().y()
                            + candidate.heightAt((int) Math.rint(localX), (int) Math.rint(localZ));
            if (Math.abs(theirs - height) <= MAX_STEP) {
                return other;
            }
        }
        return NONE;
    }

    /**
     * Which tiles have different water beside them, across an edge or diagonally, the x neighbors'
     * z neighbors and the z neighbors' x neighbors: only those blend.
     *
     * @param tiles The tiles.
     * @param neighbors Their neighbors, see {@link #find}.
     * @return Whether each tile blends.
     */
    public static boolean[] blends(@NonNull List<WaterTile> tiles, int @NonNull [] neighbors) {
        return blends(neighbors, (a, b) -> sameWater(tiles.get(a).body(), tiles.get(b).body()));
    }

    /**
     * Which tiles have a different kind of tile beside them, across an edge or diagonally, the x
     * neighbors' z neighbors and the z neighbors' x neighbors: water, or wind.
     *
     * @param neighbors The tiles' neighbors, see {@link #find}.
     * @param same Whether two tiles, by index, are the same kind.
     * @return Whether each tile blends.
     */
    public static boolean[] blends(int @NonNull [] neighbors, @NonNull Same same) {
        final boolean[] blends = new boolean[neighbors.length / EDGES];
        for (int i = 0; i < blends.length; ++i) {
            boolean differs = false;
            for (int edge = 0; edge < EDGES && !differs; ++edge) {
                final int other = neighbors[i * EDGES + edge];
                if (other == NONE) {
                    continue;
                }
                differs = !same.test(i, other);
                // Diagonally: across the other axis's two edges from there
                final int first = edge < 2 ? 2 : 0;
                for (int turn = first; turn < first + 2 && !differs; ++turn) {
                    final int diagonal = neighbors[other * EDGES + turn];
                    differs = diagonal != NONE && !same.test(i, diagonal);
                }
            }
            blends[i] = differs;
        }
        return blends;
    }

    /** Whether two tiles, by index, are the same kind, for {@link #blends}. */
    @FunctionalInterface
    public interface Same {
        /**
         * Whether two tiles are the same kind.
         *
         * @param a One tile's index.
         * @param b The other's.
         * @return True if they are.
         */
        boolean test(int a, int b);
    }

    /**
     * Whether two bodies hold the same water, so their optics match.
     *
     * @param a One.
     * @param b The other.
     * @return True if their concentrations match.
     */
    private static boolean sameWater(@NonNull WaterBody a, @NonNull WaterBody b) {
        return a.chlorophyll() == b.chlorophyll()
                && a.cdom() == b.cdom()
                && a.sediment() == b.sediment();
    }

    /**
     * The bucket a coordinate falls in.
     *
     * @param value The coordinate, in meters.
     * @return The bucket.
     */
    private static int bucket(double value) {
        return (int) Math.floor(value / BUCKET);
    }

    /**
     * A bucket's key.
     *
     * @param x The bucket along x.
     * @param z The bucket along z.
     * @return The key.
     */
    private static long key(int x, int z) {
        return ((long) x << KEY_BITS) ^ (z & 0xFFFF_FFFFL);
    }
}
