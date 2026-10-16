package com.ikalagaming.graphics.lod;

import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;

import java.util.Arrays;

/**
 * Where a group's appearance is discontinuous, so simplifying keeps it whole: the seams, edges
 * whose triangles on either side don't agree at its ends, and the charts, patches of triangles that
 * agree across every edge inside them.
 *
 * <p>What meets at a corner is its wedge: the triangle's kind (textured, projected or averaged),
 * and for a textured triangle its material, normal, texture coordinates and which way its bitangent
 * points. Wedges count as the same when their normals are within {@value #NORMAL_DEGREES} degrees
 * and their texture coordinates within {@value #UV_TOLERANCE} of each other, so float noise from
 * welding makes no seam. Tangents are left out: the corners' tangents are taken again from the
 * input after simplifying, from whichever triangle they land on, so a tangent that changes
 * direction where nothing else does holds nothing up. Projected triangles meeting each other make
 * no seam, nor do averaged ones: they have no texture coordinates to keep whole, and their normals
 * are taken again from the input after every simplification.
 *
 * <p>A vertex where two wedges meet along exactly two seam edges, and on no open border, lies along
 * a seam and may slide along it (see {@link Simplifier}); one where more meet, or a seam ends or
 * branches, is a corner and must stay put.
 */
final class Seams {

    /** Coordinates in a direction. */
    private static final int AXES = 3;

    /** Corners in a triangle. */
    private static final int CORNERS = 3;

    /** The kind of a projected triangle. */
    static final int KIND_PROJECTED = 0;

    /** The kind of a textured triangle. */
    static final int KIND_TEXTURED = 1;

    /** The kind of an averaged triangle. */
    static final int KIND_AVERAGED = 2;

    /** Normals closer than this, in degrees, are the same. */
    static final double NORMAL_DEGREES = 1;

    /** The cosine of {@link #NORMAL_DEGREES}. */
    private static final double NORMAL_COSINE = Math.cos(Math.toRadians(NORMAL_DEGREES));

    /**
     * Texture coordinates closer than this, times their size where that is over 1, are the same.
     */
    static final double UV_TOLERANCE = 1e-3;

    /** The low 16 bits of an int. */
    private static final int LOW_16 = 0xFFFF;

    /** Bits in a short. */
    private static final int SHORT_BITS = 16;

    /** The seam edges, as vertex pairs packed by {@link LongIntMap#pair}, lower vertex first. */
    final long[] edges;

    /** Which chart each triangle is in, the lowest triangle of the chart. */
    final int[] charts;

    /** The vertices that are corners, more than a seam passing through, which must stay put. */
    final boolean[] corners;

    /**
     * Keep a classification.
     *
     * @param edges The seam edges, sorted.
     * @param charts Each triangle's chart.
     * @param corners Which vertices are corners.
     */
    private Seams(long @NonNull [] edges, int @NonNull [] charts, boolean @NonNull [] corners) {
        this.edges = edges;
        this.charts = charts;
        this.corners = corners;
    }

    /**
     * Find a mesh's seams and charts.
     *
     * @param mesh The mesh.
     * @param kinds Each triangle's kind.
     * @param attributes Each triangle's corners' attributes, {@link Attributes#PER_TRIANGLE} ints a
     *     triangle, used for textured triangles only.
     * @return The seams.
     */
    static Seams find(@NonNull LodMesh mesh, int @NonNull [] kinds, int @NonNull [] attributes) {
        final int triangles = mesh.getTriangleCount();
        final int vertices = mesh.getVertexCount();
        final float[] normals = new float[triangles * CORNERS * AXES];
        final float[] uvs = new float[triangles * CORNERS * 2];
        final float[] scratch = new float[AXES];
        for (int t = 0; t < triangles; ++t) {
            if (kinds[t] != KIND_TEXTURED) {
                continue;
            }
            for (int c = 0; c < CORNERS; ++c) {
                final int at = t * Attributes.PER_TRIANGLE + c * Attributes.VALUES;
                Attributes.decodeDirection(attributes[at], scratch);
                System.arraycopy(scratch, 0, normals, (t * CORNERS + c) * AXES, AXES);
                final int uv = attributes[at + 2];
                uvs[(t * CORNERS + c) * 2] = Float.float16ToFloat((short) (uv & LOW_16));
                uvs[(t * CORNERS + c) * 2 + 1] = Float.float16ToFloat((short) (uv >>> SHORT_BITS));
            }
        }
        final Corner same = new Corner(mesh, kinds, attributes, normals, uvs);
        // Each corner's wedge at its vertex: corners in the same wedge share an id
        final int[] wedge = new int[triangles * CORNERS];
        final IntArrayList[] wedgesAt = new IntArrayList[vertices];
        for (int tc = 0; tc < wedge.length; ++tc) {
            final int v = mesh.corner(tc / CORNERS, tc % CORNERS);
            IntArrayList known = wedgesAt[v];
            if (known == null) {
                known = new IntArrayList();
                wedgesAt[v] = known;
            }
            int id = -1;
            for (int k = 0; k < known.size() && id < 0; ++k) {
                if (same.wedge(known.getInt(k), tc)) {
                    id = k;
                }
            }
            if (id < 0) {
                id = known.size();
                known.addInt(tc);
            }
            wedge[tc] = id;
        }
        // Each edge's first triangle, and how many triangles use it
        final LongIntMap index = new LongIntMap(triangles * CORNERS);
        final int[] firstTriangle = new int[triangles * CORNERS];
        final int[] uses = new int[triangles * CORNERS];
        final boolean[] seam = new boolean[triangles * CORNERS];
        final long[] keys = new long[triangles * CORNERS];
        final int[] parent = new int[triangles];
        for (int t = 0; t < triangles; ++t) {
            parent[t] = t;
        }
        // Charts join across edges used by exactly two triangles that agree, once all are seen
        final int[] secondTriangle = new int[triangles * CORNERS];
        int edgeCount = 0;
        for (int t = 0; t < triangles; ++t) {
            for (int c = 0; c < CORNERS; ++c) {
                final int a = mesh.corner(t, c);
                final int b = mesh.corner(t, (c + 1) % CORNERS);
                if (a == b) {
                    continue;
                }
                final long key = LongIntMap.pair(Math.min(a, b), Math.max(a, b));
                int e = index.putIfAbsent(key, edgeCount);
                if (e == LongIntMap.MISSING) {
                    e = edgeCount++;
                    keys[e] = key;
                    firstTriangle[e] = t;
                    uses[e] = 1;
                    continue;
                }
                ++uses[e];
                secondTriangle[e] = t;
                final int first = firstTriangle[e];
                if (wedgeAt(mesh, wedge, first, a) != wedgeAt(mesh, wedge, t, a)
                        || wedgeAt(mesh, wedge, first, b) != wedgeAt(mesh, wedge, t, b)) {
                    seam[e] = true;
                }
            }
        }
        final int[] seamEdgesAt = new int[vertices];
        final boolean[] open = new boolean[vertices];
        final IntArrayList seamEdges = new IntArrayList();
        for (int e = 0; e < edgeCount; ++e) {
            final int a = (int) (keys[e] >>> Integer.SIZE);
            final int b = (int) keys[e];
            if (uses[e] == 1) {
                open[a] = true;
                open[b] = true;
            }
            if (seam[e]) {
                ++seamEdgesAt[a];
                ++seamEdgesAt[b];
                seamEdges.addInt(e);
            } else if (uses[e] == 2) {
                union(parent, firstTriangle[e], secondTriangle[e]);
            }
        }
        final boolean[] cornerVertex = new boolean[vertices];
        for (int v = 0; v < vertices; ++v) {
            final int count = wedgesAt[v] == null ? 0 : wedgesAt[v].size();
            if (count <= 1 && seamEdgesAt[v] == 0) {
                continue;
            }
            cornerVertex[v] = count != 2 || seamEdgesAt[v] != 2 || open[v];
        }
        final long[] edges = new long[seamEdges.size()];
        for (int i = 0; i < edges.length; ++i) {
            edges[i] = keys[seamEdges.getInt(i)];
        }
        Arrays.sort(edges);
        final int[] charts = new int[triangles];
        for (int t = 0; t < triangles; ++t) {
            charts[t] = find(parent, t);
        }
        return new Seams(edges, charts, cornerVertex);
    }

    /**
     * The wedge id of a triangle's corner at a vertex.
     *
     * @param mesh The mesh.
     * @param wedge Each corner's wedge id.
     * @param t The triangle.
     * @param v The vertex, one of its corners.
     * @return The id.
     */
    private static int wedgeAt(LodMesh mesh, int[] wedge, int t, int v) {
        for (int c = 0; c < CORNERS; ++c) {
            if (mesh.corner(t, c) == v) {
                return wedge[t * CORNERS + c];
            }
        }
        return -1;
    }

    /**
     * Join two triangles' charts.
     *
     * @param parent Each triangle's parent in the union find.
     * @param a One triangle.
     * @param b The other.
     */
    private static void union(int[] parent, int a, int b) {
        final int ra = find(parent, a);
        final int rb = find(parent, b);
        if (ra != rb) {
            // The lower root wins, so a chart's id doesn't depend on the order edges are seen
            parent[Math.max(ra, rb)] = Math.min(ra, rb);
        }
    }

    /**
     * A triangle's chart root, flattening the path to it.
     *
     * @param parent Each triangle's parent in the union find.
     * @param t The triangle.
     * @return The root.
     */
    private static int find(int[] parent, int t) {
        int root = t;
        while (parent[root] != root) {
            root = parent[root];
        }
        while (parent[t] != root) {
            final int next = parent[t];
            parent[t] = root;
            t = next;
        }
        return root;
    }

    /**
     * Whether a vertex must stay put.
     *
     * @param vertex The vertex.
     * @return True if it is a corner of seams.
     */
    boolean isCorner(int vertex) {
        return corners[vertex];
    }

    /** Compares corners' wedges. */
    private static final class Corner {
        /** The mesh. */
        private final LodMesh mesh;

        /** Each triangle's kind. */
        private final int[] kinds;

        /** Each triangle's corners' attributes. */
        private final int[] attributes;

        /** Each corner's decoded normal. */
        private final float[] normals;

        /** Each corner's decoded texture coordinates. */
        private final float[] uvs;

        /**
         * Set up comparisons.
         *
         * @param mesh The mesh.
         * @param kinds Each triangle's kind.
         * @param attributes Each triangle's corners' attributes.
         * @param normals Each corner's decoded normal.
         * @param uvs Each corner's decoded texture coordinates.
         */
        Corner(LodMesh mesh, int[] kinds, int[] attributes, float[] normals, float[] uvs) {
            this.mesh = mesh;
            this.kinds = kinds;
            this.attributes = attributes;
            this.normals = normals;
            this.uvs = uvs;
        }

        /**
         * Whether two corners at the same vertex are in the same wedge.
         *
         * @param a One corner, as triangle times 3 plus corner.
         * @param b The other.
         * @return True if they agree.
         */
        boolean wedge(int a, int b) {
            final int ta = a / CORNERS;
            final int tb = b / CORNERS;
            if (kinds[ta] != kinds[tb]) {
                return false;
            }
            if (kinds[ta] != KIND_TEXTURED) {
                return true;
            }
            if (mesh.material(ta) != mesh.material(tb)) {
                return false;
            }
            final int at = ta * Attributes.PER_TRIANGLE + (a % CORNERS) * Attributes.VALUES;
            final int bt = tb * Attributes.PER_TRIANGLE + (b % CORNERS) * Attributes.VALUES;
            if (Attributes.flipped(attributes[at + 1]) != Attributes.flipped(attributes[bt + 1])) {
                return false;
            }
            final double dot =
                    normals[a * AXES] * normals[b * AXES]
                            + normals[a * AXES + 1] * normals[b * AXES + 1]
                            + normals[a * AXES + 2] * normals[b * AXES + 2];
            if (dot < NORMAL_COSINE) {
                return false;
            }
            for (int k = 0; k < 2; ++k) {
                final float u = uvs[a * 2 + k];
                final float w = uvs[b * 2 + k];
                final double tolerance =
                        UV_TOLERANCE * Math.max(1, Math.max(Math.abs(u), Math.abs(w)));
                if (Math.abs(u - w) > tolerance) {
                    return false;
                }
            }
            return true;
        }
    }
}
