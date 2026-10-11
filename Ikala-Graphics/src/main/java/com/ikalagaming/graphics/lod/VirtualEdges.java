package com.ikalagaming.graphics.lod;

import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Virtual edges join parts of a mesh that are close but share no face, so simplification can merge
 * them, as in Liu, Zhang and Yuksel, <i>Simplifying Textured Triangle Meshes in the Wild</i>
 * (2025), §4.1.1: two triangles from different connected components whose distance is under 2r get
 * an edge between their closest pair of vertices.
 *
 * <p>Every block in a section is its own little component where it touches others only along edges
 * that welding already joins, but floating blocks, leaves and the gaps between separate meshes need
 * these edges to simplify together.
 */
final class VirtualEdges {

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Values in a triangle's corners. */
    private static final int TRIANGLE_VALUES = 9;

    /** Bits per packed grid coordinate. */
    private static final int CELL_BITS = 21;

    /** The mask of one packed grid coordinate. */
    private static final long CELL_MASK = (1L << CELL_BITS) - 1;

    /** Room for this many close pairs at first. */
    private static final int INITIAL_PAIRS = 64;

    /**
     * Find the virtual edges of a mesh.
     *
     * @param mesh The mesh.
     * @param radius r: triangles closer than 2r are joined. Zero or less finds none.
     * @return The edges, as vertex pairs, two ints each.
     */
    static int[] find(@NonNull LodMesh mesh, double radius) {
        if (radius <= 0 || mesh.getTriangleCount() == 0) {
            return new int[0];
        }
        final int[] component = components(mesh);
        if (oneComponent(component)) {
            // Nothing to join
            return new int[0];
        }
        final int[] triangleComponent = new int[mesh.getTriangleCount()];
        for (int t = 0; t < triangleComponent.length; ++t) {
            triangleComponent[t] = component[mesh.corner(t, 0)];
        }
        final double reach = 2 * radius;
        final double cell = cellSize(mesh, reach);
        // Each triangle goes in the cells its own bounds touch
        final LongIntMap cellIndex = new LongIntMap(mesh.getTriangleCount());
        final List<IntArrayList> cells = new ArrayList<>();
        final double[] min = new double[AXES];
        final double[] max = new double[AXES];
        for (int t = 0; t < mesh.getTriangleCount(); ++t) {
            bounds(mesh, t, 0, min, max);
            for (long x = cellOf(min[0], cell); x <= cellOf(max[0], cell); ++x) {
                for (long y = cellOf(min[1], cell); y <= cellOf(max[1], cell); ++y) {
                    for (long z = cellOf(min[2], cell); z <= cellOf(max[2], cell); ++z) {
                        int index = cellIndex.putIfAbsent(pack(x, y, z), cells.size());
                        if (index == LongIntMap.MISSING) {
                            index = cells.size();
                            cells.add(new IntArrayList());
                        }
                        cells.get(index).addInt(t);
                    }
                }
            }
        }
        // Each triangle looks through the cells its bounds grown by 2r touch, for later triangles
        // of
        // other components; a triangle closer than 2r is in one of them. The stamp skips a
        // triangle met in several cells.
        final int[] seenBy = new int[mesh.getTriangleCount()];
        Arrays.fill(seenBy, -1);
        long[] pairs = new long[INITIAL_PAIRS];
        int pairCount = 0;
        final double[] a = new double[TRIANGLE_VALUES];
        final double[] b = new double[TRIANGLE_VALUES];
        final double limit = reach * reach;
        for (int ta = 0; ta < mesh.getTriangleCount(); ++ta) {
            bounds(mesh, ta, reach, min, max);
            boolean loaded = false;
            for (long x = cellOf(min[0], cell); x <= cellOf(max[0], cell); ++x) {
                for (long y = cellOf(min[1], cell); y <= cellOf(max[1], cell); ++y) {
                    for (long z = cellOf(min[2], cell); z <= cellOf(max[2], cell); ++z) {
                        final int index = cellIndex.get(pack(x, y, z));
                        if (index == LongIntMap.MISSING) {
                            continue;
                        }
                        final IntArrayList triangles = cells.get(index);
                        for (int i = 0; i < triangles.size(); ++i) {
                            final int tb = triangles.getInt(i);
                            if (tb <= ta
                                    || seenBy[tb] == ta
                                    || triangleComponent[ta] == triangleComponent[tb]) {
                                continue;
                            }
                            seenBy[tb] = ta;
                            if (!loaded) {
                                load(mesh, ta, a);
                                loaded = true;
                            }
                            load(mesh, tb, b);
                            if (Geometry.triangleDistanceSquared(a, b) < limit) {
                                if (pairCount == pairs.length) {
                                    pairs = Arrays.copyOf(pairs, pairs.length * 2);
                                }
                                pairs[pairCount++] = closestPair(mesh, ta, tb);
                            }
                        }
                    }
                }
            }
        }
        // Many triangle pairs share their closest vertices: keep each edge once
        Arrays.sort(pairs, 0, pairCount);
        final IntArrayList found = new IntArrayList();
        for (int i = 0; i < pairCount; ++i) {
            if (i == 0 || pairs[i] != pairs[i - 1]) {
                found.addInt((int) (pairs[i] >>> Integer.SIZE));
                found.addInt((int) pairs[i]);
            }
        }
        return found.toIntArray();
    }

    /**
     * Whether every vertex is in the same component.
     *
     * @param component Each vertex's component.
     * @return True if there is only one.
     */
    private static boolean oneComponent(int @NonNull [] component) {
        for (int c : component) {
            if (c != component[0]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Label each vertex with its connected component, joining vertices that share a triangle.
     *
     * @param mesh The mesh.
     * @return The component of each vertex.
     */
    static int[] components(@NonNull LodMesh mesh) {
        final int[] parent = new int[mesh.getVertexCount()];
        for (int v = 0; v < parent.length; ++v) {
            parent[v] = v;
        }
        for (int t = 0; t < mesh.getTriangleCount(); ++t) {
            union(parent, mesh.corner(t, 0), mesh.corner(t, 1));
            union(parent, mesh.corner(t, 0), mesh.corner(t, 2));
        }
        for (int v = 0; v < parent.length; ++v) {
            parent[v] = root(parent, v);
        }
        return parent;
    }

    /**
     * Find a set's root, halving the path on the way.
     *
     * @param parent The parents.
     * @param v The element.
     * @return Its root.
     */
    private static int root(int @NonNull [] parent, int v) {
        while (parent[v] != v) {
            parent[v] = parent[parent[v]];
            v = parent[v];
        }
        return v;
    }

    /**
     * Join two sets.
     *
     * @param parent The parents.
     * @param a One element.
     * @param b Another element.
     */
    private static void union(int @NonNull [] parent, int a, int b) {
        final int ra = root(parent, a);
        final int rb = root(parent, b);
        if (ra != rb) {
            parent[Math.max(ra, rb)] = Math.min(ra, rb);
        }
    }

    /**
     * The grid cell size: at least the reach, and not so small that big triangles fill thousands of
     * cells.
     *
     * @param mesh The mesh.
     * @param reach 2r.
     * @return The cell size.
     */
    private static double cellSize(@NonNull LodMesh mesh, double reach) {
        double extent = 0;
        final double[] min = new double[AXES];
        final double[] max = new double[AXES];
        for (int t = 0; t < mesh.getTriangleCount(); ++t) {
            bounds(mesh, t, 0, min, max);
            extent += Math.max(max[0] - min[0], Math.max(max[1] - min[1], max[2] - min[2]));
        }
        return Math.max(reach, extent / mesh.getTriangleCount());
    }

    /**
     * A triangle's bounds, grown by a margin.
     *
     * @param mesh The mesh.
     * @param t The triangle.
     * @param margin How far to grow them.
     * @param min The minimum corner, written.
     * @param max The maximum corner, written.
     */
    private static void bounds(
            @NonNull LodMesh mesh,
            int t,
            double margin,
            double @NonNull [] min,
            double @NonNull [] max) {
        for (int axis = 0; axis < AXES; ++axis) {
            double lo = Double.MAX_VALUE;
            double hi = -Double.MAX_VALUE;
            for (int corner = 0; corner < AXES; ++corner) {
                final double value = mesh.position(mesh.corner(t, corner), axis);
                lo = Math.min(lo, value);
                hi = Math.max(hi, value);
            }
            min[axis] = lo - margin;
            max[axis] = hi + margin;
        }
    }

    /**
     * The grid cell a coordinate falls in.
     *
     * @param value The coordinate.
     * @param cell The cell size.
     * @return The cell.
     */
    private static long cellOf(double value, double cell) {
        return (long) Math.floor(value / cell);
    }

    /**
     * Pack a cell into a long.
     *
     * @param x The cell's x.
     * @param y The cell's y.
     * @param z The cell's z.
     * @return The key.
     */
    private static long pack(long x, long y, long z) {
        return (x & CELL_MASK)
                | ((y & CELL_MASK) << CELL_BITS)
                | ((z & CELL_MASK) << (2 * CELL_BITS));
    }

    /**
     * Read a triangle's corners.
     *
     * @param mesh The mesh.
     * @param t The triangle.
     * @param out The 9 values, written.
     */
    private static void load(@NonNull LodMesh mesh, int t, double @NonNull [] out) {
        for (int corner = 0; corner < AXES; ++corner) {
            for (int axis = 0; axis < AXES; ++axis) {
                out[corner * AXES + axis] = mesh.position(mesh.corner(t, corner), axis);
            }
        }
    }

    /**
     * The closest pair of vertices of two triangles.
     *
     * @param mesh The mesh.
     * @param ta The first triangle.
     * @param tb The second triangle.
     * @return The pair, packed with the lower vertex first.
     */
    private static long closestPair(@NonNull LodMesh mesh, int ta, int tb) {
        double best = Double.MAX_VALUE;
        int bestA = -1;
        int bestB = -1;
        for (int i = 0; i < AXES; ++i) {
            final int va = mesh.corner(ta, i);
            for (int j = 0; j < AXES; ++j) {
                final int vb = mesh.corner(tb, j);
                double d = 0;
                for (int axis = 0; axis < AXES; ++axis) {
                    final double delta = mesh.position(va, axis) - mesh.position(vb, axis);
                    d += delta * delta;
                }
                if (d < best) {
                    best = d;
                    bestA = va;
                    bestB = vb;
                }
            }
        }
        return LongIntMap.pair(Math.min(bestA, bestB), Math.max(bestA, bestB));
    }

    /** Static helpers only. */
    private VirtualEdges() {}
}
