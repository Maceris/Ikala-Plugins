package com.ikalagaming.graphics.lod;

import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides which clusters of a level are merged and simplified together, the way Nanite does (Karis
 * et al., <i>Nanite: A Deep Dive</i>, 2021): clusters are nodes of a graph, joined by the number of
 * triangle edges they share, plus light edges to their nearest clusters so separate pieces are
 * grouped with what is near them rather than arbitrarily. Partitioning it with the least edge
 * weight cut gives groups with the shortest shared borders, so the fewest edges are locked. Groups
 * get up to {@value #MAX_CLUSTERS} clusters.
 *
 * <p>Also the corner and edge keys used to find where clusters meet.
 */
public final class Grouping {

    /** Clusters in a group at most. */
    static final int MAX_CLUSTERS = 32;

    /** How many nearest clusters each cluster is also joined to. */
    private static final int NEAREST = 4;

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Bits per packed grid coordinate. */
    private static final int CELL_BITS = 21;

    /** The mask of one packed grid coordinate. */
    private static final long CELL_MASK = (1L << CELL_BITS) - 1;

    /**
     * Group a level's clusters.
     *
     * @param dag The DAG so far.
     * @param active The clusters to group.
     * @return The groups, each a list of cluster ids, in a fixed order.
     */
    static int[][] group(@NonNull LodDag dag, int @NonNull [] active) {
        final int n = active.length;
        if (n <= MAX_CLUSTERS) {
            return new int[][] {active.clone()};
        }
        Graph.Builder builder = new Graph.Builder(n);
        // Shared edges: the first cluster to use each edge, and every later one joined to it
        int edgeCount = 0;
        for (int id : active) {
            edgeCount += dag.clusters().get(id).corners().length / AXES;
        }
        final EdgeTable firstUse = new EdgeTable(edgeCount);
        for (int i = 0; i < n; ++i) {
            final LodDag.Cluster cluster = dag.clusters().get(active[i]);
            final float[] corners = cluster.corners();
            for (int t = 0; t < corners.length; t += Clusters.TRIANGLE_VALUES) {
                for (int corner = 0; corner < AXES; ++corner) {
                    final int other =
                            firstUse.putIfAbsent(
                                    corners,
                                    t + corner * AXES,
                                    t + ((corner + 1) % AXES) * AXES,
                                    i);
                    if (other >= 0 && other != i) {
                        builder.edge(i, other, 1);
                    }
                }
            }
            builder.position(i, cluster.bound()[0], cluster.bound()[1], cluster.bound()[2]);
        }
        addNearest(dag, active, builder);
        final int[] part = GraphPartitioner.partition(builder.build(), MAX_CLUSTERS);
        final int parts = GraphPartitioner.partCount(part);
        List<IntArrayList> groups = new ArrayList<>();
        for (int p = 0; p < parts; ++p) {
            groups.add(new IntArrayList());
        }
        for (int i = 0; i < n; ++i) {
            groups.get(part[i]).addInt(active[i]);
        }
        final int[][] out = new int[parts][];
        for (int p = 0; p < parts; ++p) {
            out[p] = groups.get(p).toIntArray();
        }
        return out;
    }

    /**
     * Join each cluster to its nearest few by center, found through a grid.
     *
     * @param dag The DAG.
     * @param active The clusters.
     * @param builder The graph being built.
     */
    private static void addNearest(
            @NonNull LodDag dag, int @NonNull [] active, Graph.@NonNull Builder builder) {
        double radius = 0;
        for (int id : active) {
            radius += dag.clusters().get(id).tight()[AXES];
        }
        final double cell = Math.max(1e-6, 2 * radius / active.length);
        Map<Long, IntArrayList> grid = new HashMap<>();
        for (int i = 0; i < active.length; ++i) {
            final double[] c = dag.clusters().get(active[i]).bound();
            grid.computeIfAbsent(key(c, cell, 0, 0, 0), k -> new IntArrayList()).addInt(i);
        }
        final int[] best = new int[NEAREST];
        final double[] bestDistance = new double[NEAREST];
        for (int i = 0; i < active.length; ++i) {
            final double[] c = dag.clusters().get(active[i]).bound();
            Arrays.fill(best, -1);
            Arrays.fill(bestDistance, Double.MAX_VALUE);
            for (int dx = -1; dx <= 1; ++dx) {
                for (int dy = -1; dy <= 1; ++dy) {
                    for (int dz = -1; dz <= 1; ++dz) {
                        final IntArrayList near = grid.get(key(c, cell, dx, dy, dz));
                        if (near == null) {
                            continue;
                        }
                        for (int k = 0; k < near.size(); ++k) {
                            final int j = near.getInt(k);
                            if (j == i) {
                                continue;
                            }
                            final double[] o = dag.clusters().get(active[j]).bound();
                            final double d =
                                    (c[0] - o[0]) * (c[0] - o[0])
                                            + (c[1] - o[1]) * (c[1] - o[1])
                                            + (c[2] - o[2]) * (c[2] - o[2]);
                            insert(best, bestDistance, j, d);
                        }
                    }
                }
            }
            for (int j : best) {
                if (j >= 0) {
                    builder.edge(i, j, 1);
                }
            }
        }
    }

    /**
     * Keep the nearest few, sorted.
     *
     * @param best The nearest so far.
     * @param distance Their distances.
     * @param candidate Another cluster.
     * @param d Its distance.
     */
    private static void insert(
            int @NonNull [] best, double @NonNull [] distance, int candidate, double d) {
        for (int k = 0; k < best.length; ++k) {
            if (d < distance[k] || (d == distance[k] && candidate < best[k])) {
                // Shift the farther ones down one; arraycopy handles the overlap
                System.arraycopy(best, k, best, k + 1, best.length - k - 1);
                System.arraycopy(distance, k, distance, k + 1, distance.length - k - 1);
                best[k] = candidate;
                distance[k] = d;
                return;
            }
        }
    }

    /**
     * The grid key of the cell a point falls in, shifted by some cells.
     *
     * @param p The point.
     * @param cell The cell size.
     * @param dx Cells along x.
     * @param dy Cells along y.
     * @param dz Cells along z.
     * @return The key.
     */
    private static long key(double @NonNull [] p, double cell, int dx, int dy, int dz) {
        final long x = (long) Math.floor(p[0] / cell) + dx;
        final long y = (long) Math.floor(p[1] / cell) + dy;
        final long z = (long) Math.floor(p[2] / cell) + dz;
        return (x & CELL_MASK)
                | ((y & CELL_MASK) << CELL_BITS)
                | ((z & CELL_MASK) << (2 * CELL_BITS));
    }

    /**
     * A corner position compared by its exact bits, for finding where clusters meet.
     *
     * @param x The x bits.
     * @param y The y bits.
     * @param z The z bits.
     */
    record Corner(int x, int y, int z) {

        /**
         * The corner at an index in flat corners.
         *
         * @param corners The corners, 3 floats each.
         * @param at Where the corner starts.
         * @return The key.
         */
        static Corner of(float @NonNull [] corners, int at) {
            return new Corner(
                    Float.floatToIntBits(corners[at] + 0f),
                    Float.floatToIntBits(corners[at + 1] + 0f),
                    Float.floatToIntBits(corners[at + 2] + 0f));
        }

        /**
         * Whether this corner sorts before another.
         *
         * @param other The other corner.
         * @return True if it comes first.
         */
        boolean before(@NonNull Corner other) {
            if (x != other.x) {
                return x < other.x;
            }
            if (y != other.y) {
                return y < other.y;
            }
            return z < other.z;
        }
    }

    /**
     * An edge between two corners, in a fixed order so both directions match.
     *
     * @param a The first corner.
     * @param b The second corner.
     */
    record Edge(@NonNull Corner a, @NonNull Corner b) {

        /**
         * The edge between two corners.
         *
         * @param p One corner.
         * @param q The other.
         * @return The edge.
         */
        static Edge of(@NonNull Corner p, @NonNull Corner q) {
            return p.before(q) ? new Edge(p, q) : new Edge(q, p);
        }
    }

    /**
     * Count how many triangles use each edge, over some clusters.
     *
     * @param dag The DAG.
     * @param clusters The clusters.
     * @return The count of each edge.
     */
    static Map<Edge, Integer> edgeUses(@NonNull LodDag dag, @NonNull List<Integer> clusters) {
        Map<Edge, Integer> uses = new HashMap<>();
        for (int id : clusters) {
            final float[] corners = dag.clusters().get(id).corners();
            for (int t = 0; t < corners.length; t += Clusters.TRIANGLE_VALUES) {
                for (int corner = 0; corner < AXES; ++corner) {
                    final Corner p = Corner.of(corners, t + corner * AXES);
                    final Corner q = Corner.of(corners, t + ((corner + 1) % AXES) * AXES);
                    uses.merge(Edge.of(p, q), 1, Integer::sum);
                }
            }
        }
        return uses;
    }

    /**
     * A value for each edge between two corners, either way round, in an open addressing table of
     * the corners' exact bits, so counting a level's shared edges allocates no key objects. Corners
     * compare as {@link Corner} does.
     */
    static final class EdgeTable {
        /** Ints in a key: two corners' bits. */
        private static final int KEY = 2 * AXES;

        /** Each slot's key. */
        private int[] keys;

        /** Each slot's value, -1 where empty. */
        private int[] values;

        /** How many slots are used. */
        private int size;

        /**
         * Make a table.
         *
         * @param expected How many edges it may get, for its first size.
         */
        EdgeTable(int expected) {
            allocate(Integer.highestOneBit(Math.max(4, expected * 2 - 1)) << 1);
        }

        /**
         * Make the table empty at a size.
         *
         * @param slots The size, a power of two.
         */
        private void allocate(int slots) {
            keys = new int[slots * KEY];
            values = new int[slots];
            Arrays.fill(values, -1);
            size = 0;
        }

        /**
         * Give an edge a value unless it has one.
         *
         * @param corners Flat corners, 3 floats each.
         * @param p Where one corner starts.
         * @param q Where the other starts.
         * @param value The value, at least 0.
         * @return The edge's value before, or -1 if it had none and now has this one.
         */
        int putIfAbsent(float @NonNull [] corners, int p, int q, int value) {
            final int at = slot(corners, p, q, true);
            if (values[at] >= 0) {
                return values[at];
            }
            values[at] = value;
            added();
            return -1;
        }

        /**
         * Count one more use of an edge, from 0 for a new one.
         *
         * @param corners Flat corners, 3 floats each.
         * @param p Where one corner starts.
         * @param q Where the other starts.
         */
        void count(float @NonNull [] corners, int p, int q) {
            final int at = slot(corners, p, q, true);
            if (values[at] >= 0) {
                ++values[at];
                return;
            }
            values[at] = 1;
            added();
        }

        /**
         * An edge's value.
         *
         * @param corners Flat corners, 3 floats each.
         * @param p Where one corner starts.
         * @param q Where the other starts.
         * @return The value, or -1 if it has none.
         */
        int get(float @NonNull [] corners, int p, int q) {
            final int at = slot(corners, p, q, false);
            return at < 0 ? -1 : values[at];
        }

        /** Note a new edge, growing the table to keep it at most half full. */
        private void added() {
            if (++size * 2 > values.length) {
                grow();
            }
        }

        /**
         * The slot an edge is in, or for a new edge the empty slot it goes in, with its key set.
         *
         * @param corners Flat corners, 3 floats each.
         * @param p Where one corner starts.
         * @param q Where the other starts.
         * @param claim Whether to claim an empty slot for a new edge; if not, -1 is returned.
         * @return The slot.
         */
        private int slot(float @NonNull [] corners, int p, int q, boolean claim) {
            final int px = Float.floatToIntBits(corners[p] + 0f);
            final int py = Float.floatToIntBits(corners[p + 1] + 0f);
            final int pz = Float.floatToIntBits(corners[p + 2] + 0f);
            final int qx = Float.floatToIntBits(corners[q] + 0f);
            final int qy = Float.floatToIntBits(corners[q + 1] + 0f);
            final int qz = Float.floatToIntBits(corners[q + 2] + 0f);
            final boolean pFirst = px != qx ? px < qx : py != qy ? py < qy : pz < qz;
            final int ax = pFirst ? px : qx;
            final int ay = pFirst ? py : qy;
            final int az = pFirst ? pz : qz;
            final int bx = pFirst ? qx : px;
            final int by = pFirst ? qy : py;
            final int bz = pFirst ? qz : pz;
            final int mask = values.length - 1;
            int at = hash(ax, ay, az, bx, by, bz) & mask;
            while (values[at] >= 0) {
                final int k = at * KEY;
                if (keys[k] == ax
                        && keys[k + 1] == ay
                        && keys[k + 2] == az
                        && keys[k + 3] == bx
                        && keys[k + 4] == by
                        && keys[k + 5] == bz) {
                    return at;
                }
                at = (at + 1) & mask;
            }
            if (!claim) {
                return -1;
            }
            final int k = at * KEY;
            keys[k] = ax;
            keys[k + 1] = ay;
            keys[k + 2] = az;
            keys[k + 3] = bx;
            keys[k + 4] = by;
            keys[k + 5] = bz;
            return at;
        }

        /**
         * Mix an edge's corners' bits.
         *
         * @param ax The first corner's x bits.
         * @param ay Its y bits.
         * @param az Its z bits.
         * @param bx The second corner's x bits.
         * @param by Its y bits.
         * @param bz Its z bits.
         * @return The hash.
         */
        private static int hash(int ax, int ay, int az, int bx, int by, int bz) {
            int h = ax * 0x9E3779B1 + ay * 0x85EBCA77 + az * 0xC2B2AE3D;
            h = h * 0x27D4EB2F + bx * 0x165667B1 + by * 0x61C88647 + bz * 0x7FEB352D;
            h ^= h >>> 15;
            h *= 0x2C1B3C6D;
            h ^= h >>> 13;
            return h;
        }

        /** Double the table, keeping every edge. */
        private void grow() {
            final int[] oldKeys = keys;
            final int[] oldValues = values;
            allocate(oldValues.length * 2);
            final int mask = values.length - 1;
            for (int old = 0; old < oldValues.length; ++old) {
                if (oldValues[old] < 0) {
                    continue;
                }
                final int k = old * KEY;
                final int h =
                        hash(
                                oldKeys[k],
                                oldKeys[k + 1],
                                oldKeys[k + 2],
                                oldKeys[k + 3],
                                oldKeys[k + 4],
                                oldKeys[k + 5]);
                int at = h & mask;
                while (values[at] >= 0) {
                    at = (at + 1) & mask;
                }
                System.arraycopy(oldKeys, k, keys, at * KEY, KEY);
                values[at] = oldValues[old];
                ++size;
            }
        }
    }

    /** Static helpers only. */
    private Grouping() {}
}
