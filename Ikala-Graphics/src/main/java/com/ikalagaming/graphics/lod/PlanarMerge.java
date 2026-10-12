package com.ikalagaming.graphics.lod;

import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;

import java.util.ArrayDeque;

/**
 * Merges coplanar triangles of the same material into fewer, larger ones without changing the
 * surface at all: every plane keeps its shape, every material its area, and no vertex moves. It
 * works on any mesh at any orientation, and pays off wherever flat faces were cut finer than their
 * shape needs, as block faces, walls, floors and roofs are.
 *
 * <p>It removes vertices by half-edge collapses, moving a vertex u onto a neighbor v, only when
 * that provably leaves the surface as it was:
 *
 * <ul>
 *   <li>Locked vertices stay, and so do vertices on an open edge (used by one triangle) or one used
 *       by more than two: an open edge is where the mesh meets another, such as a section's border,
 *       and removing a vertex along it would leave a T-junction against the neighbor.
 *   <li>u's triangles are grouped by plane and material. With one group, u is inside a flat region
 *       of one material, and it can go to any neighbor.
 *   <li>With two groups that meet along exactly two edges u-a and u-b, with a, u and b in a line, u
 *       is on a straight crease or material border, and it can go to a or b along it.
 *   <li>Anything else stays: corners, bent seams, three or more groups.
 *   <li>A collapse must make no triangle degenerate or turn it over, and must pass the link
 *       condition, so the mesh stays as connected as it was.
 * </ul>
 */
public final class PlanarMerge {

    /** Coordinates in a position. */
    private static final int AXES = LodMesh.AXES;

    /** Vertices in a triangle. */
    private static final int CORNERS = LodMesh.CORNERS;

    /** How close two unit normals' dot product must be to 1 for their planes to be the same. */
    static final double SAME_PLANE = 1e-6;

    /** How close to a line, relative to its edges' lengths, a seam's vertex must be. */
    static final double STRAIGHT = 1e-6;

    /** The smallest twice-area a triangle may be left with, relative to its edges squared. */
    private static final double MIN_AREA = 1e-9;

    /** The positions, x, y, z per vertex. */
    private final double[] positions;

    /** The corners of each triangle, changed as vertices go. */
    private final int[] corners;

    /** Each triangle's material. */
    private final int[] materials;

    /** Whether each triangle is still there. */
    private final boolean[] alive;

    /** Whether each vertex is locked. */
    private final boolean[] locked;

    /** Whether each vertex has gone. */
    private final boolean[] removed;

    /** The triangles around each vertex. */
    private final IntArrayList[] around;

    /** Each triangle's unit normal, which a valid collapse never changes. */
    private final double[] normals;

    /** The mesh being merged. */
    private final LodMesh mesh;

    /**
     * Merge a mesh's coplanar triangles.
     *
     * @param mesh The mesh.
     * @return The same surface with as few vertices as these collapses allow; the mesh itself if
     *     nothing could go.
     */
    public static LodMesh merge(@NonNull LodMesh mesh) {
        final PlanarMerge merge = new PlanarMerge(mesh);
        return merge.run() ? merge.build() : mesh;
    }

    /**
     * Set up the working copy.
     *
     * @param mesh The mesh.
     */
    private PlanarMerge(@NonNull LodMesh mesh) {
        this.mesh = mesh;
        final int vertexCount = mesh.getVertexCount();
        final int triangleCount = mesh.getTriangleCount();
        positions = new double[vertexCount * AXES];
        for (int i = 0; i < positions.length; ++i) {
            positions[i] = mesh.positions[i];
        }
        corners = mesh.triangles.clone();
        materials = mesh.materials;
        alive = new boolean[triangleCount];
        locked = mesh.locked;
        removed = new boolean[vertexCount];
        around = new IntArrayList[vertexCount];
        for (int v = 0; v < vertexCount; ++v) {
            around[v] = new IntArrayList();
        }
        normals = new double[triangleCount * AXES];
        final double[] normal = new double[AXES];
        for (int t = 0; t < triangleCount; ++t) {
            if (!normal(t, -1, -1, normal)) {
                // Already degenerate: left alone, and its corners kept
                alive[t] = true;
                for (int c = 0; c < CORNERS; ++c) {
                    around[corners[t * CORNERS + c]].addInt(t);
                }
                continue;
            }
            alive[t] = true;
            System.arraycopy(normal, 0, normals, t * AXES, AXES);
            for (int c = 0; c < CORNERS; ++c) {
                around[corners[t * CORNERS + c]].addInt(t);
            }
        }
    }

    /**
     * Collapse every vertex that can go, until none can.
     *
     * @return True if any went.
     */
    private boolean run() {
        final ArrayDeque<Integer> queue = new ArrayDeque<>();
        final boolean[] queued = new boolean[removed.length];
        for (int v = 0; v < removed.length; ++v) {
            queue.add(v);
            queued[v] = true;
        }
        boolean any = false;
        while (!queue.isEmpty()) {
            final int u = queue.poll();
            queued[u] = false;
            if (removed[u]) {
                continue;
            }
            final int[] neighbors = neighbors(u);
            if (tryRemove(u)) {
                any = true;
                for (int w : neighbors) {
                    if (!removed[w] && !queued[w]) {
                        queue.add(w);
                        queued[w] = true;
                    }
                }
            }
        }
        return any;
    }

    /**
     * Remove a vertex if a collapse allows it.
     *
     * @param u The vertex.
     * @return True if it went.
     */
    private boolean tryRemove(int u) {
        if (locked[u] || around[u].isEmpty()) {
            return false;
        }
        final int[] neighbors = neighbors(u);
        // Every edge around it must be used by exactly two triangles
        for (int w : neighbors) {
            if (sharedFaces(u, w) != 2) {
                return false;
            }
        }
        // Group its triangles: the first triangle of each group stands for it
        final IntArrayList faces = around[u];
        final int[] group = new int[faces.size()];
        int groups = 0;
        final int[] firsts = new int[2];
        for (int k = 0; k < faces.size(); ++k) {
            final int t = faces.getInt(k);
            int g = -1;
            for (int j = 0; j < groups; ++j) {
                if (sameClass(t, firsts[j])) {
                    g = j;
                    break;
                }
            }
            if (g < 0) {
                if (groups == firsts.length) {
                    return false;
                }
                firsts[groups] = t;
                g = groups++;
            }
            group[k] = g;
        }
        if (groups == 1) {
            for (int v : neighbors) {
                if (tryCollapse(u, v)) {
                    return true;
                }
            }
            return false;
        }
        // Two groups: the edges between them must be two, in a straight line through u
        int a = -1;
        int b = -1;
        for (int w : neighbors) {
            if (isSeam(u, w, faces, group)) {
                if (a < 0) {
                    a = w;
                } else if (b < 0) {
                    b = w;
                } else {
                    return false;
                }
            }
        }
        if (b < 0 || !straight(a, u, b)) {
            return false;
        }
        return tryCollapse(u, a) || tryCollapse(u, b);
    }

    /**
     * Whether an edge from u separates its two groups.
     *
     * @param u The vertex.
     * @param w The other end.
     * @param faces The triangles around u.
     * @param group Each one's group.
     * @return True if its two triangles are in different groups.
     */
    private boolean isSeam(int u, int w, @NonNull IntArrayList faces, int @NonNull [] group) {
        int first = -1;
        for (int k = 0; k < faces.size(); ++k) {
            if (uses(faces.getInt(k), w)) {
                if (first < 0) {
                    first = group[k];
                } else {
                    return first != group[k];
                }
            }
        }
        return false;
    }

    /**
     * Whether three points are in a line, with the middle one between the others.
     *
     * @param a One end.
     * @param m The middle.
     * @param b The other end.
     * @return True if they are.
     */
    private boolean straight(int a, int m, int b) {
        final double[] ma = new double[AXES];
        final double[] mb = new double[AXES];
        for (int axis = 0; axis < AXES; ++axis) {
            ma[axis] = positions[a * AXES + axis] - positions[m * AXES + axis];
            mb[axis] = positions[b * AXES + axis] - positions[m * AXES + axis];
        }
        final double cx = ma[1] * mb[2] - ma[2] * mb[1];
        final double cy = ma[2] * mb[0] - ma[0] * mb[2];
        final double cz = ma[0] * mb[1] - ma[1] * mb[0];
        final double lengths = Math.sqrt(Geometry.dot(ma, ma) * Geometry.dot(mb, mb));
        return Math.sqrt(cx * cx + cy * cy + cz * cz) <= STRAIGHT * lengths
                && Geometry.dot(ma, mb) < 0;
    }

    /**
     * Move u onto v if that leaves every triangle where it was and the mesh as connected.
     *
     * @param u The vertex that goes.
     * @param v The vertex it goes to.
     * @return True if it was done.
     */
    private boolean tryCollapse(int u, int v) {
        // Link condition: the only vertices both are next to are the far corners of their two
        // shared triangles
        int sharedFar = 0;
        for (int w : neighbors(u)) {
            if (w != v && isNeighbor(v, w)) {
                if (!sharesTriangleWith(u, v, w)) {
                    return false;
                }
                ++sharedFar;
            }
        }
        if (sharedFar != 2) {
            return false;
        }
        // Every triangle that stays must keep its plane and facing
        final double[] normal = new double[AXES];
        final IntArrayList faces = around[u];
        for (int k = 0; k < faces.size(); ++k) {
            final int t = faces.getInt(k);
            if (uses(t, v)) {
                continue;
            }
            if (!normal(t, u, v, normal)
                    || Geometry.dot(normal, slice(normals, t)) < 1 - SAME_PLANE) {
                return false;
            }
        }
        collapse(u, v);
        return true;
    }

    /**
     * Do a collapse: drop the triangles on the edge and move u's others to v.
     *
     * @param u The vertex that goes.
     * @param v The vertex it goes to.
     */
    private void collapse(int u, int v) {
        final IntArrayList faces = new IntArrayList(around[u]);
        for (int k = 0; k < faces.size(); ++k) {
            final int t = faces.getInt(k);
            if (uses(t, v)) {
                alive[t] = false;
                for (int c = 0; c < CORNERS; ++c) {
                    removeFace(corners[t * CORNERS + c], t);
                }
                continue;
            }
            for (int c = 0; c < CORNERS; ++c) {
                if (corners[t * CORNERS + c] == u) {
                    corners[t * CORNERS + c] = v;
                }
            }
            around[v].addInt(t);
        }
        around[u] = new IntArrayList();
        removed[u] = true;
    }

    /**
     * Take a triangle off a vertex's list.
     *
     * @param vertex The vertex.
     * @param triangle The triangle.
     */
    private void removeFace(int vertex, int triangle) {
        final IntArrayList faces = around[vertex];
        for (int k = 0; k < faces.size(); ++k) {
            if (faces.getInt(k) == triangle) {
                faces.setInt(k, faces.getInt(faces.size() - 1));
                faces.remove(faces.size() - 1);
                return;
            }
        }
    }

    /**
     * A triangle's unit normal, optionally with one corner moved to another vertex's position.
     *
     * @param t The triangle.
     * @param from The corner to move, or -1.
     * @param to The vertex whose position it takes.
     * @param out Receives the normal.
     * @return False if the triangle is degenerate.
     */
    private boolean normal(int t, int from, int to, double @NonNull [] out) {
        final double[][] p = new double[CORNERS][];
        double longest = 0;
        for (int c = 0; c < CORNERS; ++c) {
            int vertex = corners[t * CORNERS + c];
            if (vertex == from) {
                vertex = to;
            }
            p[c] = slice(positions, vertex);
        }
        for (int c = 0; c < CORNERS; ++c) {
            longest = Math.max(longest, Geometry.distanceSquared(p[c], p[(c + 1) % CORNERS]));
        }
        Geometry.normal(p[0], p[1], p[2], out);
        final double length = Math.sqrt(Geometry.dot(out, out));
        if (length <= MIN_AREA * longest || length == 0) {
            return false;
        }
        for (int axis = 0; axis < AXES; ++axis) {
            out[axis] /= length;
        }
        return true;
    }

    /**
     * Whether two triangles around a shared vertex are in the same plane and material.
     *
     * @param t One triangle.
     * @param s The other.
     * @return True if they are.
     */
    private boolean sameClass(int t, int s) {
        return materials[t] == materials[s]
                && Geometry.dot(slice(normals, t), slice(normals, s)) >= 1 - SAME_PLANE;
    }

    /**
     * Three values of an array.
     *
     * @param values The array.
     * @param index Which triple.
     * @return A copy of it.
     */
    private static double[] slice(double @NonNull [] values, int index) {
        return new double[] {
            values[index * AXES], values[index * AXES + 1], values[index * AXES + 2]
        };
    }

    /**
     * Whether a triangle has a vertex as a corner.
     *
     * @param t The triangle.
     * @param vertex The vertex.
     * @return True if it does.
     */
    private boolean uses(int t, int vertex) {
        return corners[t * CORNERS] == vertex
                || corners[t * CORNERS + 1] == vertex
                || corners[t * CORNERS + 2] == vertex;
    }

    /**
     * How many triangles use an edge.
     *
     * @param a One end.
     * @param b The other.
     * @return The count.
     */
    private int sharedFaces(int a, int b) {
        int count = 0;
        final IntArrayList faces = around[a];
        for (int k = 0; k < faces.size(); ++k) {
            if (uses(faces.getInt(k), b)) {
                ++count;
            }
        }
        return count;
    }

    /**
     * Whether some triangle has all three vertices.
     *
     * @param a One.
     * @param b Another.
     * @param c The third.
     * @return True if one does.
     */
    private boolean sharesTriangleWith(int a, int b, int c) {
        final IntArrayList faces = around[a];
        for (int k = 0; k < faces.size(); ++k) {
            final int t = faces.getInt(k);
            if (uses(t, b) && uses(t, c)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether two vertices share an edge.
     *
     * @param a One.
     * @param b The other.
     * @return True if a triangle uses both.
     */
    private boolean isNeighbor(int a, int b) {
        return sharedFaces(a, b) > 0;
    }

    /**
     * The vertices next to a vertex.
     *
     * @param u The vertex.
     * @return Each once.
     */
    private int[] neighbors(int u) {
        final IntArrayList out = new IntArrayList();
        final IntArrayList faces = around[u];
        for (int k = 0; k < faces.size(); ++k) {
            final int t = faces.getInt(k);
            for (int c = 0; c < CORNERS; ++c) {
                final int w = corners[t * CORNERS + c];
                if (w != u && !out.contains(w)) {
                    out.addInt(w);
                }
            }
        }
        return out.toIntArray();
    }

    /**
     * Make the merged mesh: the triangles that are left, in their order, on the vertices they use.
     *
     * @return The mesh.
     */
    private LodMesh build() {
        final LodMesh.Builder builder = LodMesh.builder(mesh.getOrigin().clone());
        final int[] remap = new int[removed.length];
        java.util.Arrays.fill(remap, -1);
        for (int t = 0; t < alive.length; ++t) {
            if (!alive[t]) {
                continue;
            }
            final int[] out = new int[CORNERS];
            for (int c = 0; c < CORNERS; ++c) {
                final int v = corners[t * CORNERS + c];
                if (remap[v] < 0) {
                    remap[v] =
                            builder.vertex(
                                    mesh.position(v, 0), mesh.position(v, 1), mesh.position(v, 2));
                    if (locked[v]) {
                        builder.lock(remap[v]);
                    }
                }
                out[c] = remap[v];
            }
            builder.triangle(out[0], out[1], out[2], materials[t], mesh.visibility(t));
        }
        return builder.build();
    }
}
