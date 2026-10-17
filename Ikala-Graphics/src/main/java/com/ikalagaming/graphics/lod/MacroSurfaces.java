package com.ikalagaming.graphics.lod;

import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Tells a mesh's macro-surfaces at some resolution from what is finer than it, after Loubet and
 * Neyret, <i>Hybrid mesh-volume LoDs for all-scale pre-filtering of complex 3D assets</i> (2017),
 * §3. A surface is a macro-surface at a resolution if, around each of its points, it crosses a
 * sphere that wide in one connected piece, as a wall or a floor does; a small free part doesn't
 * reach the sphere at all, and a thin rod or strip crosses it twice. What is finer than the
 * resolution, like twigs, wires and grass, is better stood for by a volume than by triangles, which
 * can't keep its coverage once simplified.
 *
 * <p>Each triangle is tested at its center: the surface is explored from it, through shared edges,
 * as far as it reaches inside the ball, and the edges with one end inside the ball and one outside
 * are where it crosses the sphere. The two crossings of a triangle the sphere cuts are joined, so
 * the crossings fall into pieces, as many as the times the surface crosses the sphere. One piece
 * marks a possible macro-surface; a triangle wider than the ball is one by itself. Then only
 * connected runs of those wider than the ball are kept, so a bump smaller than the resolution on an
 * otherwise large surface doesn't count.
 *
 * <p>Their test can't see a surface made of separate pieces, as models build walls from panels,
 * frames and trims that touch without sharing edges: each piece smaller than the ball never reaches
 * the sphere. So a triangle is also on a macro-surface where the surface around it fills the ball's
 * disc: the area within the ball, seen along the triangle's normal, at least {@link #COVERAGE} of
 * the disc's, whatever its pieces. A strip, a rod or scattered small parts cover a sliver of it. A
 * run found this way is as wide as the ball by its making.
 */
public final class MacroSurfaces {

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Corners in a triangle. */
    private static final int CORNERS = 3;

    /** Marks an edge slot nothing uses. */
    private static final int NONE = -1;

    /**
     * The share of the ball's disc the surface around a triangle must cover, seen along its normal,
     * to be a macro-surface whatever its pieces: half, so a surface's edge still counts.
     */
    static final double COVERAGE = 0.5;

    /** How alike two triangles' facing must be, by the cosine between them, to be one surface. */
    private static final double ALIKE = 0.7;

    /** Bits of each grid coordinate in a cell's key. */
    private static final int KEY_BITS = 21;

    /** The low {@link #KEY_BITS} bits. */
    private static final long KEY_MASK = (1L << KEY_BITS) - 1;

    /**
     * Which of a mesh's triangles are on macro-surfaces at a resolution.
     *
     * @param mesh The mesh, its vertices welded so neighbors share edges.
     * @param radius The resolution: the radius of the sphere around each triangle's center.
     * @return Whether each triangle is on a macro-surface.
     */
    public static boolean @NonNull [] classify(@NonNull LodMesh mesh, double radius) {
        final int n = mesh.getTriangleCount();
        final boolean[] macro = new boolean[n];
        if (radius <= 0) {
            Arrays.fill(macro, true);
            return macro;
        }
        // The disc test first, which is cheap: what it finds needs no exploring
        final boolean[] covered = covered(mesh, radius);
        final Adjacency adjacency = new Adjacency(mesh);
        final double radiusSquared = radius * radius;
        final double[] center = new double[AXES];
        // Scratch for each test, cleared by the triangles and edges it touched
        final int[] seen = new int[n];
        Arrays.fill(seen, NONE);
        final IntArrayList open = new IntArrayList();
        final int[] edgeParent = new int[adjacency.edgeCount()];
        final int[] edgeSeen = new int[adjacency.edgeCount()];
        Arrays.fill(edgeSeen, NONE);
        for (int t = 0; t < n; ++t) {
            centerOf(mesh, t, center);
            if (covered[t] || widerThan(mesh, t, center, radiusSquared)) {
                macro[t] = true;
                continue;
            }
            // Explore the surface inside the ball, joining each cut triangle's crossings
            int crossings = 0;
            int pieces = 0;
            open.clear();
            open.addInt(t);
            seen[t] = t;
            while (!open.isEmpty()) {
                final int u = open.pop();
                int first = NONE;
                for (int c = 0; c < CORNERS; ++c) {
                    final int a = mesh.corner(u, c);
                    final int b = mesh.corner(u, (c + 1) % CORNERS);
                    final boolean aInside = inside(mesh, a, center, radiusSquared);
                    final boolean bInside = inside(mesh, b, center, radiusSquared);
                    final int edge = adjacency.edgeOf(u, c);
                    if (aInside != bInside) {
                        // The sphere crosses this edge: one point of the crossing
                        if (edgeSeen[edge] != t) {
                            edgeSeen[edge] = t;
                            edgeParent[edge] = edge;
                            ++crossings;
                            ++pieces;
                        }
                        if (first == NONE) {
                            first = edge;
                        } else if (union(edgeParent, first, edge)) {
                            --pieces;
                        }
                    }
                    // Go on through edges with an end inside the ball
                    if (aInside || bInside) {
                        final int other = adjacency.across(u, c);
                        if (other != NONE && seen[other] != t) {
                            seen[other] = t;
                            open.addInt(other);
                        }
                    }
                }
            }
            macro[t] = crossings > 0 && pieces == 1;
        }
        keepWide(mesh, adjacency, macro, radius);
        for (int t = 0; t < n; ++t) {
            macro[t] |= covered[t];
        }
        return macro;
    }

    /**
     * Which triangles the surface around fills the ball's disc of, by {@link #COVERAGE}: the area
     * of the triangles whose centers are within the ball, each seen along the triangle's normal.
     *
     * @param mesh The mesh.
     * @param radius The ball's radius.
     * @return Whether each triangle's disc is covered.
     */
    private static boolean @NonNull [] covered(@NonNull LodMesh mesh, double radius) {
        final int n = mesh.getTriangleCount();
        final double[] centers = new double[n * AXES];
        final double[] normals = new double[n * AXES];
        final double[] areas = new double[n];
        final double[] a = new double[AXES];
        final double[] b = new double[AXES];
        final double[] c = new double[AXES];
        final double[] normal = new double[AXES];
        final Map<Long, IntArrayList> grid = new HashMap<>();
        for (int t = 0; t < n; ++t) {
            Geometry.corners(mesh, t, a, b, c);
            Geometry.normal(a, b, c, normal);
            final double length = Math.sqrt(Geometry.dot(normal, normal));
            areas[t] = length / 2;
            for (int axis = 0; axis < AXES; ++axis) {
                centers[t * AXES + axis] = (a[axis] + b[axis] + c[axis]) / CORNERS;
                normals[t * AXES + axis] = length > 0 ? normal[axis] / length : 0;
            }
            grid.computeIfAbsent(cellKey(centers, t, radius, 0, 0, 0), k -> new IntArrayList())
                    .addInt(t);
        }
        final boolean[] out = new boolean[n];
        final double disc = Math.PI * radius * radius;
        final double radiusSquared = radius * radius;
        for (int t = 0; t < n; ++t) {
            double area = 0;
            for (int dx = -1; dx <= 1 && area < COVERAGE * disc; ++dx) {
                for (int dy = -1; dy <= 1; ++dy) {
                    for (int dz = -1; dz <= 1; ++dz) {
                        final IntArrayList near = grid.get(cellKey(centers, t, radius, dx, dy, dz));
                        if (near == null) {
                            continue;
                        }
                        for (int k = 0; k < near.size(); ++k) {
                            final int u = near.getInt(k);
                            double distance = 0;
                            double facing = 0;
                            for (int axis = 0; axis < AXES; ++axis) {
                                final double d =
                                        centers[u * AXES + axis] - centers[t * AXES + axis];
                                distance += d * d;
                                facing += normals[u * AXES + axis] * normals[t * AXES + axis];
                            }
                            if (distance < radiusSquared) {
                                area += areas[u] * Math.abs(facing);
                            }
                        }
                    }
                }
            }
            out[t] = area >= COVERAGE * disc;
        }
        // A surface's edge covers less of its disc: what lies within the ball of a covered
        // triangle and faces about the same way is the same surface
        final boolean[] grown = out.clone();
        for (int t = 0; t < n; ++t) {
            if (out[t]) {
                continue;
            }
            for (int dx = -1; dx <= 1 && !grown[t]; ++dx) {
                for (int dy = -1; dy <= 1 && !grown[t]; ++dy) {
                    for (int dz = -1; dz <= 1 && !grown[t]; ++dz) {
                        final IntArrayList near = grid.get(cellKey(centers, t, radius, dx, dy, dz));
                        if (near == null) {
                            continue;
                        }
                        for (int k = 0; k < near.size() && !grown[t]; ++k) {
                            final int u = near.getInt(k);
                            if (!out[u]) {
                                continue;
                            }
                            double distance = 0;
                            double facing = 0;
                            for (int axis = 0; axis < AXES; ++axis) {
                                final double d =
                                        centers[u * AXES + axis] - centers[t * AXES + axis];
                                distance += d * d;
                                facing += normals[u * AXES + axis] * normals[t * AXES + axis];
                            }
                            grown[t] = distance < radiusSquared && Math.abs(facing) >= ALIKE;
                        }
                    }
                }
            }
        }
        return grown;
    }

    /**
     * The grid cell key of a point, moved by some cells.
     *
     * @param points Points, three values each.
     * @param index Which point.
     * @param cell The cell size.
     * @param dx Cells along x.
     * @param dy Cells along y.
     * @param dz Cells along z.
     * @return The key.
     */
    private static long cellKey(
            double @NonNull [] points, int index, double cell, int dx, int dy, int dz) {
        final long x = (long) Math.floor(points[index * AXES] / cell) + dx;
        final long y = (long) Math.floor(points[index * AXES + 1] / cell) + dy;
        final long z = (long) Math.floor(points[index * AXES + 2] / cell) + dz;
        return (x & KEY_MASK) << (2 * KEY_BITS) | (y & KEY_MASK) << KEY_BITS | (z & KEY_MASK);
    }

    /**
     * Keep only the connected runs of macro triangles wider than the resolution.
     *
     * @param mesh The mesh.
     * @param adjacency Its edges.
     * @param macro Each triangle's mark, cleared for the runs too small.
     * @param radius The resolution.
     */
    private static void keepWide(
            @NonNull LodMesh mesh,
            @NonNull Adjacency adjacency,
            boolean @NonNull [] macro,
            double radius) {
        final int n = macro.length;
        final int[] run = new int[n];
        Arrays.fill(run, NONE);
        final IntArrayList open = new IntArrayList();
        final IntArrayList members = new IntArrayList();
        final double[] low = new double[AXES];
        final double[] high = new double[AXES];
        for (int start = 0; start < n; ++start) {
            if (!macro[start] || run[start] != NONE) {
                continue;
            }
            members.clear();
            open.addInt(start);
            run[start] = start;
            Arrays.fill(low, Double.POSITIVE_INFINITY);
            Arrays.fill(high, Double.NEGATIVE_INFINITY);
            while (!open.isEmpty()) {
                final int u = open.pop();
                members.addInt(u);
                for (int c = 0; c < CORNERS; ++c) {
                    final int v = mesh.corner(u, c);
                    for (int axis = 0; axis < AXES; ++axis) {
                        low[axis] = Math.min(low[axis], mesh.position(v, axis));
                        high[axis] = Math.max(high[axis], mesh.position(v, axis));
                    }
                    final int other = adjacency.across(u, c);
                    if (other != NONE && macro[other] && run[other] == NONE) {
                        run[other] = start;
                        open.addInt(other);
                    }
                }
            }
            double diagonal = 0;
            for (int axis = 0; axis < AXES; ++axis) {
                diagonal += (high[axis] - low[axis]) * (high[axis] - low[axis]);
            }
            // Its bounding sphere is about half the box's diagonal across
            if (Math.sqrt(diagonal) / 2 <= radius) {
                for (int i = 0; i < members.size(); ++i) {
                    macro[members.getInt(i)] = false;
                }
            }
        }
    }

    /**
     * A triangle's center.
     *
     * @param mesh The mesh.
     * @param t The triangle.
     * @param out Receives the center.
     */
    private static void centerOf(@NonNull LodMesh mesh, int t, double @NonNull [] out) {
        Arrays.fill(out, 0);
        for (int c = 0; c < CORNERS; ++c) {
            final int v = mesh.corner(t, c);
            for (int axis = 0; axis < AXES; ++axis) {
                out[axis] += mesh.position(v, axis) / CORNERS;
            }
        }
    }

    /**
     * Whether a triangle reaches past the ball around its center with every corner, so it spans the
     * sphere by itself.
     *
     * @param mesh The mesh.
     * @param t The triangle.
     * @param center Its center.
     * @param radiusSquared The ball's radius, squared.
     * @return True if every corner is outside the ball.
     */
    private static boolean widerThan(
            @NonNull LodMesh mesh, int t, double @NonNull [] center, double radiusSquared) {
        for (int c = 0; c < CORNERS; ++c) {
            if (inside(mesh, mesh.corner(t, c), center, radiusSquared)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a vertex is inside a ball.
     *
     * @param mesh The mesh.
     * @param v The vertex.
     * @param center The ball's center.
     * @param radiusSquared Its radius, squared.
     * @return True if it is.
     */
    private static boolean inside(
            @NonNull LodMesh mesh, int v, double @NonNull [] center, double radiusSquared) {
        double sum = 0;
        for (int axis = 0; axis < AXES; ++axis) {
            final double d = mesh.position(v, axis) - center[axis];
            sum += d * d;
        }
        return sum < radiusSquared;
    }

    /**
     * Join two edges' pieces.
     *
     * @param parent The union-find parents.
     * @param a One edge.
     * @param b The other.
     * @return True if they were in different pieces.
     */
    private static boolean union(int @NonNull [] parent, int a, int b) {
        final int ra = root(parent, a);
        final int rb = root(parent, b);
        if (ra == rb) {
            return false;
        }
        parent[Math.max(ra, rb)] = Math.min(ra, rb);
        return true;
    }

    /**
     * Find an edge's piece, halving the path on the way.
     *
     * @param parent The union-find parents.
     * @param e The edge.
     * @return Its piece's root.
     */
    private static int root(int @NonNull [] parent, int e) {
        while (parent[e] != e) {
            parent[e] = parent[parent[e]];
            e = parent[e];
        }
        return e;
    }

    /** A mesh's edges: an id per undirected edge, and the triangle across each. */
    private static final class Adjacency {

        /** Each triangle corner's edge id, from that corner to the next. */
        private final int[] edges;

        /** Each triangle corner's neighbor across its edge, or {@link #NONE}. */
        private final int[] across;

        /** How many edges there are. */
        private final int edgeCount;

        /**
         * Find a mesh's edges. An edge three or more triangles share is joined to the first two.
         *
         * @param mesh The mesh.
         */
        Adjacency(@NonNull LodMesh mesh) {
            final int n = mesh.getTriangleCount();
            edges = new int[n * CORNERS];
            across = new int[n * CORNERS];
            Arrays.fill(across, NONE);
            final LongIntMap firstUse = new LongIntMap(n * CORNERS);
            int count = 0;
            for (int t = 0; t < n; ++t) {
                for (int c = 0; c < CORNERS; ++c) {
                    final int a = mesh.corner(t, c);
                    final int b = mesh.corner(t, (c + 1) % CORNERS);
                    final long key = LongIntMap.pair(Math.min(a, b), Math.max(a, b));
                    final int slot = t * CORNERS + c;
                    final int other = firstUse.putIfAbsent(key, slot);
                    if (other == LongIntMap.MISSING) {
                        edges[slot] = count++;
                    } else {
                        edges[slot] = edges[other];
                        if (across[other] == NONE) {
                            across[other] = t;
                        }
                        across[slot] = other / CORNERS;
                    }
                }
            }
            edgeCount = count;
        }

        /**
         * A triangle corner's edge id.
         *
         * @param t The triangle.
         * @param c The corner, its edge going to the next corner.
         * @return The edge's id.
         */
        int edgeOf(int t, int c) {
            return edges[t * CORNERS + c];
        }

        /**
         * The triangle across a triangle corner's edge.
         *
         * @param t The triangle.
         * @param c The corner.
         * @return The neighbor, or {@link #NONE}.
         */
        int across(int t, int c) {
            return across[t * CORNERS + c];
        }

        /**
         * How many edges there are.
         *
         * @return The count.
         */
        int edgeCount() {
            return edgeCount;
        }
    }

    /** Static helpers only. */
    private MacroSurfaces() {}
}
