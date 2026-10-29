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
 *
 * <p>Collapses leave fans of long, thin triangles from the corners that stayed. Afterwards, edges
 * between two triangles of the same plane and material are flipped until no triangle's circle holds
 * its neighbor's far corner (Lawson's flips to a Delaunay triangulation), which gives the same
 * surface and count in the best shaped triangles those corners allow.
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

    /**
     * How far below 0 the cotangents of the two angles facing an edge must sum before it flips, so
     * edges of a square, which could go either way, don't flip back and forth.
     */
    private static final double FLIP_MARGIN = 1e-9;

    /** Passes over every edge at most while flipping, as a safety net. */
    private static final int MAX_FLIP_PASSES = 64;

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

    /**
     * Whether each triangle is textured: left as it is, since merging would have to keep its
     * texture coordinates one affine map, and its corners kept, as if locked.
     */
    private final boolean[] textured;

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
        final boolean collapsed = merge.run();
        final boolean flipped = merge.flip();
        return collapsed || flipped ? merge.build() : mesh;
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
        locked = mesh.locked.clone();
        textured = new boolean[triangleCount];
        for (int t = 0; t < triangleCount; ++t) {
            textured[t] = mesh.isTextured(t);
            if (textured[t]) {
                for (int c = 0; c < CORNERS; ++c) {
                    locked[corners[t * CORNERS + c]] = true;
                }
            }
        }
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
            final IntArrayList neighbors = neighbors(u, runNeighbors);
            if (tryRemove(u)) {
                any = true;
                for (int i = 0; i < neighbors.size(); ++i) {
                    final int w = neighbors.getInt(i);
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
        final IntArrayList neighbors = neighbors(u, removeNeighbors);
        // Every edge around it must be used by exactly two triangles
        for (int n = 0; n < neighbors.size(); ++n) {
            final int w = neighbors.getInt(n);
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
            for (int n = 0; n < neighbors.size(); ++n) {
                final int v = neighbors.getInt(n);
                if (tryCollapse(u, v)) {
                    return true;
                }
            }
            return false;
        }
        // Two groups: the edges between them must be two, in a straight line through u
        int a = -1;
        int b = -1;
        for (int n = 0; n < neighbors.size(); ++n) {
            final int w = neighbors.getInt(n);
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
        final IntArrayList next = neighbors(u, collapseNeighbors);
        for (int n = 0; n < next.size(); ++n) {
            final int w = next.getInt(n);
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
            if (!normal(t, u, v, normal) || dotAt(normal, normals, t) < 1 - SAME_PLANE) {
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
        final double[][] p = cornerScratch;
        double longest = 0;
        for (int c = 0; c < CORNERS; ++c) {
            int vertex = corners[t * CORNERS + c];
            if (vertex == from) {
                vertex = to;
            }
            System.arraycopy(positions, vertex * AXES, p[c], 0, AXES);
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
        return materials[t] == materials[s] && dotAt(normals, t, normals, s) >= 1 - SAME_PLANE;
    }

    /**
     * The dot product of a vector with a triple of an array, as {@link Geometry#dot} gives it,
     * without copying the triple out.
     *
     * @param a The vector.
     * @param values The array.
     * @param index Which triple.
     * @return The dot product.
     */
    private static double dotAt(double @NonNull [] a, double @NonNull [] values, int index) {
        final int at = index * AXES;
        return a[0] * values[at] + a[1] * values[at + 1] + a[2] * values[at + 2];
    }

    /**
     * The dot product of two triples of arrays.
     *
     * @param a One array.
     * @param i Its triple.
     * @param b The other.
     * @param j Its triple.
     * @return The dot product.
     */
    private static double dotAt(double @NonNull [] a, int i, double @NonNull [] b, int j) {
        final int x = i * AXES;
        final int y = j * AXES;
        return a[x] * b[y] + a[x + 1] * b[y + 1] + a[x + 2] * b[y + 2];
    }

    /**
     * The corners of a triangle {@link #normal} works on, reused: normals are found for every
     * collapse tried, too often to allocate for.
     */
    private final double[][] cornerScratch = new double[CORNERS][AXES];

    /**
     * The neighbors {@link #run} holds while it tries a vertex, reused: one list for each caller,
     * since each calls the next while holding its own, and neighbors are gathered for every vertex
     * tried, too often to allocate for.
     */
    private final IntArrayList runNeighbors = new IntArrayList();

    /** The neighbors {@link #tryRemove} holds while it tries collapses. */
    private final IntArrayList removeNeighbors = new IntArrayList();

    /** The neighbors {@link #tryCollapse} looks through. */
    private final IntArrayList collapseNeighbors = new IntArrayList();

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
     * @param out Receives each once, cleared first.
     * @return The list given.
     */
    private IntArrayList neighbors(int u, @NonNull IntArrayList out) {
        out.clear();
        final IntArrayList faces = around[u];
        for (int k = 0; k < faces.size(); ++k) {
            final int t = faces.getInt(k);
            for (int c = 0; c < CORNERS; ++c) {
                final int w = corners[t * CORNERS + c];
                if (w != u && !holds(out, w)) {
                    out.addInt(w);
                }
            }
        }
        return out;
    }

    /**
     * Whether a list holds a value, without boxing it as {@link IntArrayList#contains} does.
     *
     * @param list The list.
     * @param value The value.
     * @return True if it does.
     */
    private static boolean holds(@NonNull IntArrayList list, int value) {
        for (int i = 0; i < list.size(); ++i) {
            if (list.getInt(i) == value) {
                return true;
            }
        }
        return false;
    }

    /**
     * Flip edges between two triangles of the same plane and material until the triangulation is
     * Delaunay: an edge flips when the angles facing it sum to more than half a turn, which the
     * other diagonal of its quad always improves. Open edges and seams never flip, and a flip must
     * leave both triangles facing the way they did, so the surface stays exactly as it was.
     *
     * @return True if any edge flipped.
     */
    private boolean flip() {
        boolean any = false;
        for (int pass = 0; pass < MAX_FLIP_PASSES; ++pass) {
            boolean flipped = false;
            for (int t = 0; t < alive.length; ++t) {
                if (!alive[t]) {
                    continue;
                }
                for (int c = 0; c < CORNERS; ++c) {
                    if (tryFlip(t, c)) {
                        flipped = true;
                        break;
                    }
                }
            }
            if (!flipped) {
                break;
            }
            any = true;
        }
        return any;
    }

    /**
     * Flip one triangle's edge if that makes it more Delaunay.
     *
     * @param t The triangle, with corners a, b, c in order from {@code first}.
     * @param first The corner the edge starts at; the edge is a to b.
     * @return True if it flipped.
     */
    private boolean tryFlip(int t, int first) {
        if (textured[t]) {
            return false;
        }
        final int a = corners[t * CORNERS + first];
        final int b = corners[t * CORNERS + (first + 1) % CORNERS];
        final int c = corners[t * CORNERS + (first + 2) % CORNERS];
        if (sharedFaces(a, b) != 2) {
            return false;
        }
        // The other triangle on the edge, and its far corner d
        int s = -1;
        int d = -1;
        final IntArrayList faces = around[a];
        for (int k = 0; k < faces.size(); ++k) {
            final int other = faces.getInt(k);
            if (other != t && uses(other, b)) {
                s = other;
                for (int corner = 0; corner < CORNERS; ++corner) {
                    final int w = corners[other * CORNERS + corner];
                    if (w != a && w != b) {
                        d = w;
                    }
                }
            }
        }
        if (s < 0 || d < 0 || d == c || textured[s] || !sameClass(t, s) || isNeighbor(c, d)) {
            return false;
        }
        if (cotangent(c, a, b) + cotangent(d, a, b) >= -FLIP_MARGIN) {
            return false;
        }
        // The new triangles a, d, c and d, b, c must face as the old ones did
        final double[] normal = new double[AXES];
        final double[] facing = slice(normals, t);
        if (!normalOf(a, d, c, normal)
                || Geometry.dot(normal, facing) < 1 - SAME_PLANE
                || !normalOf(d, b, c, normal)
                || Geometry.dot(normal, facing) < 1 - SAME_PLANE) {
            return false;
        }
        corners[t * CORNERS] = a;
        corners[t * CORNERS + 1] = d;
        corners[t * CORNERS + 2] = c;
        corners[s * CORNERS] = d;
        corners[s * CORNERS + 1] = b;
        corners[s * CORNERS + 2] = c;
        removeFace(b, t);
        around[d].addInt(t);
        removeFace(a, s);
        around[c].addInt(s);
        return true;
    }

    /**
     * The cotangent of the angle at a vertex facing an edge.
     *
     * @param apex The vertex.
     * @param a One end of the edge.
     * @param b The other end.
     * @return The cotangent; negative for an obtuse angle.
     */
    private double cotangent(int apex, int a, int b) {
        final double[] u = new double[AXES];
        final double[] v = new double[AXES];
        for (int axis = 0; axis < AXES; ++axis) {
            u[axis] = positions[a * AXES + axis] - positions[apex * AXES + axis];
            v[axis] = positions[b * AXES + axis] - positions[apex * AXES + axis];
        }
        final double cx = u[1] * v[2] - u[2] * v[1];
        final double cy = u[2] * v[0] - u[0] * v[2];
        final double cz = u[0] * v[1] - u[1] * v[0];
        final double cross = Math.sqrt(cx * cx + cy * cy + cz * cz);
        return cross == 0 ? Double.NEGATIVE_INFINITY : Geometry.dot(u, v) / cross;
    }

    /**
     * The unit normal of three vertices.
     *
     * @param a The first corner.
     * @param b The second.
     * @param c The third.
     * @param out Receives the normal.
     * @return False if they are degenerate.
     */
    private boolean normalOf(int a, int b, int c, double @NonNull [] out) {
        final double[] pa = slice(positions, a);
        final double[] pb = slice(positions, b);
        final double[] pc = slice(positions, c);
        final double longest =
                Math.max(
                        Geometry.distanceSquared(pa, pb),
                        Math.max(
                                Geometry.distanceSquared(pb, pc),
                                Geometry.distanceSquared(pc, pa)));
        Geometry.normal(pa, pb, pc, out);
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
     * Make the merged mesh: the triangles that are left, in their order, on the vertices they use.
     *
     * @return The mesh.
     */
    private LodMesh build() {
        final LodMesh.Builder builder =
                LodMesh.builder(mesh.getOrigin().clone()).leafError(mesh.getLeafError());
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
                    if (mesh.isLocked(v)) {
                        builder.lock(remap[v]);
                    }
                }
                out[c] = remap[v];
            }
            // A textured triangle is as it came, so its corners' attributes still fit
            if (textured[t]) {
                final int[] attributes = new int[Attributes.PER_TRIANGLE];
                mesh.attributes(t, attributes, 0);
                builder.triangle(
                        out[0], out[1], out[2], materials[t], mesh.visibility(t), attributes);
            } else {
                builder.triangle(out[0], out[1], out[2], materials[t], mesh.visibility(t));
            }
        }
        return builder.build();
    }
}
