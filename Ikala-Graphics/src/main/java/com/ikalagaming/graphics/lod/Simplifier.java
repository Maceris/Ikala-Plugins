package com.ikalagaming.graphics.lod;

import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;

import java.util.Arrays;

/**
 * Simplifies a mesh by collapsing edges, cheapest first, the way Liu, Zhang and Yuksel do in
 * <i>Simplifying Textured Triangle Meshes in the Wild</i> (2025):
 *
 * <ul>
 *   <li>The mesh is a simplicial 2-complex: edges come from faces, plus virtual edges between
 *       nearby parts that share no face ({@link VirtualEdges}), so separate parts can merge.
 *   <li>A collapse costs the sum of its two vertices' plane quadrics, which are summed as vertices
 *       merge so they remember the input, plus the boundary area quadric of the boundary edges
 *       around it, worked out fresh each time ({@link Quadric}).
 *   <li>The new vertex goes where that cost is least, or at the better end or the middle if that
 *       can't be solved.
 * </ul>
 *
 * <p>Added for level of detail: locked vertices never move or go, so borders shared with
 * neighboring groups stay exactly as they were; vertices along a seam, where the triangles on
 * either side don't agree on their appearance (see {@link Seams}), only slide along it onto their
 * neighbor there, so each side stays whole; and collapses that would flip a triangle over are
 * rejected. Appearance is not considered while collapsing; it is found again afterwards through the
 * collapse history ({@link Correspondence}).
 */
public final class Simplifier {

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Vertices in a triangle. */
    private static final int CORNERS = 3;

    /** The most faces an edge of a manifold mesh has. */
    private static final int MANIFOLD_FACES = 2;

    /** Values in a triangle's corners. */
    private static final int TRIANGLE_VALUES = 9;

    /** How many faces' corners a history has room for at first. */
    private static final int INITIAL_HISTORY = 64;

    /**
     * The weight a completely hidden face's plane keeps, against 1 for an open one, so hidden faces
     * still hold some shape.
     */
    private static final double MIN_VISIBILITY_WEIGHT = 0.05;

    /** The smallest weight an error is divided by, so a weightless quadric doesn't divide by 0. */
    private static final double MIN_WEIGHT = 1e-12;

    /**
     * What to simplify towards, and how.
     *
     * @param targetTriangles Stop once there are this many triangles or fewer.
     * @param virtualEdgeRadius r: parts closer than 2r are joined by virtual edges. 0 for none.
     * @param maxError Don't collapse an edge whose error, in world units, is over this. Infinity
     *     for no limit.
     * @param recordHistory Whether to record what each collapse changed, for {@link
     *     Correspondence}.
     * @param keepOpen Edges no collapse may create, as vertex pairs packed by {@link
     *     LongIntMap#pair} with the lower vertex first, sorted.
     * @param seams Seam edges, packed the same way: their vertices only collapse along them.
     * @param planes Which of a region cell's planes each vertex lies on, a bit per plane: min x, y,
     *     z, then max x, y, z. Empty outside a cell.
     * @param cell The cell's box, min x, y, z then max x, y, z, in the mesh's frame. Empty outside
     *     a cell.
     */
    public record Options(
            int targetTriangles,
            double virtualEdgeRadius,
            double maxError,
            boolean recordHistory,
            long @NonNull [] keepOpen,
            long @NonNull [] seams,
            int @NonNull [] planes,
            double @NonNull [] cell) {

        /**
         * Simplify to a triangle count with no error limit, recording history.
         *
         * @param targetTriangles The target.
         * @param virtualEdgeRadius r, or 0 for no virtual edges.
         * @return The options.
         */
        public static Options toCount(int targetTriangles, double virtualEdgeRadius) {
            return new Options(
                    targetTriangles,
                    virtualEdgeRadius,
                    Double.POSITIVE_INFINITY,
                    true,
                    new long[0],
                    new long[0],
                    new int[0],
                    new double[0]);
        }

        /**
         * The same options, with edges no collapse may create.
         *
         * @param edges The edges, packed vertex pairs, lower vertex first; sorted here.
         * @return The options.
         */
        public Options keepingOpen(long @NonNull [] edges) {
            final long[] sorted = edges.clone();
            Arrays.sort(sorted);
            return new Options(
                    targetTriangles,
                    virtualEdgeRadius,
                    maxError,
                    recordHistory,
                    sorted,
                    seams,
                    planes,
                    cell);
        }

        /**
         * The same options, with seams whose vertices only slide along them.
         *
         * @param edges The seam edges, packed vertex pairs, lower vertex first.
         * @return The options.
         */
        public Options followingSeams(long @NonNull [] edges) {
            return new Options(
                    targetTriangles,
                    virtualEdgeRadius,
                    maxError,
                    recordHistory,
                    keepOpen,
                    edges,
                    planes,
                    cell);
        }

        /**
         * The same options, inside a region cell whose neighbors are built apart: vertices on the
         * cell's planes are locked, and no collapse may join two of them on one plane by a new
         * edge, or move a vertex onto or past a plane it isn't on, so the planes keep exactly the
         * edges the neighbors have.
         *
         * @param vertexPlanes Which planes each vertex lies on, a bit per plane: min x, y, z, then
         *     max x, y, z.
         * @param box The cell's box, min x, y, z then max x, y, z, in the mesh's frame.
         * @return The options.
         */
        public Options withinCell(int @NonNull [] vertexPlanes, double @NonNull [] box) {
            return new Options(
                    targetTriangles,
                    virtualEdgeRadius,
                    maxError,
                    recordHistory,
                    keepOpen,
                    seams,
                    vertexPlanes,
                    box);
        }
    }

    /**
     * What simplifying gave.
     *
     * @param mesh The simplified mesh. Each triangle has its input material for now; {@link
     *     Correspondence} works out the material from the input.
     * @param error The largest collapse error, in world units: about how far the simplified surface
     *     is from the input.
     * @param faceIds Which input triangle each output triangle started as. Triangles keep their
     *     identity through collapses even as their corners move.
     * @param history What each collapse changed, or null if it wasn't recorded.
     * @param collapses How many edges were collapsed.
     * @param rejectedFlips How many collapses were rejected because they would flip a triangle.
     * @param rejectedLinks How many collapses were rejected because they would make an edge
     *     non-manifold where the mesh is manifold (the link condition), or break a border another
     *     group keeps.
     * @param virtualEdges How many virtual edges were added.
     */
    public record Result(
            @NonNull LodMesh mesh,
            double error,
            int @NonNull [] faceIds,
            History history,
            int collapses,
            int rejectedFlips,
            int rejectedLinks,
            int virtualEdges) {}

    /**
     * Everything each collapse changed, as Liu et al. keep it for mapping back to the input: the
     * faces around the collapsed edge before (with their corner positions then), and the faces
     * around the new vertex after. Stored flat.
     */
    public static final class History {
        /** How many collapses there are. */
        int count;

        /** Where each collapse's faces before start in {@link #beforeFaces}, plus an end. */
        IntArrayList beforeStart = new IntArrayList();

        /** The ids of the faces around each collapsed edge before it collapsed. */
        IntArrayList beforeFaces = new IntArrayList();

        /** Their corners then, 9 values each. */
        double[] beforeCorners = new double[TRIANGLE_VALUES * INITIAL_HISTORY];

        /** Where each collapse's faces after start in {@link #afterFaces}, plus an end. */
        IntArrayList afterStart = new IntArrayList();

        /** The ids of the faces around each new vertex after its collapse, sorted. */
        IntArrayList afterFaces = new IntArrayList();

        /** Start empty. */
        History() {
            beforeStart.addInt(0);
            afterStart.addInt(0);
        }

        /**
         * Make room for more corners.
         *
         * @param faces How many faces' corners will be stored in all.
         */
        void reserve(int faces) {
            if (faces * TRIANGLE_VALUES > beforeCorners.length) {
                beforeCorners =
                        Arrays.copyOf(
                                beforeCorners,
                                Math.max(faces * TRIANGLE_VALUES, beforeCorners.length * 2));
            }
        }
    }

    /** The positions, x, y, z per vertex. */
    private final double[] positions;

    /** The quadric of each vertex. */
    private final double[] quadrics;

    /** Whether each vertex is locked. */
    private final boolean[] locked;

    /** Whether each vertex is still in the mesh. */
    private final boolean[] vertexAlive;

    /** Bumped each time a vertex changes, so stale heap entries are noticed. */
    private final int[] versions;

    /** The faces, three vertices each. */
    private final int[] faces;

    /** Whether each face is still in the mesh. */
    private final boolean[] faceAlive;

    /** The faces around each vertex. */
    private final IntArrayList[] vertexFaces;

    /** The vertices joined to each vertex by an edge, real or virtual. */
    private final IntArrayList[] neighbors;

    /** The vertices joined to each vertex by a seam edge. */
    private final IntArrayList[] seamNeighbors;

    /** Candidate collapses. */
    private final EdgeHeap heap = new EdgeHeap();

    /** The options. */
    private final Options options;

    /** The input. */
    private final LodMesh input;

    /** How many faces are still in the mesh. */
    private int faceCount;

    /** The largest error so far. */
    private double maxError;

    /** How many collapses there were. */
    private int collapses;

    /** How many collapses were rejected for flipping a triangle. */
    private int rejectedFlips;

    /**
     * How many collapses were rejected by the link condition or for breaking a border another group
     * keeps.
     */
    private int rejectedLinks;

    /** The history, or null. */
    private final History history;

    /** Scratch quadric. */
    private final double[] scratch = new double[Quadric.STRIDE];

    /** Scratch point. */
    private final double[] point = new double[AXES];

    /** Scratch point. */
    private final double[] other = new double[AXES];

    /** The new vertex position chosen by the last {@link #cost} call. */
    private final double[] chosen = new double[AXES];

    /** The end the last {@link #cost} call keeps; the other goes. */
    private int chosenKeep;

    /**
     * Each vertex's boundary area quadric, the sum over its boundary edges, kept until the vertex's
     * version changes. A collapse changes the boundary edges and positions only around the kept
     * vertex, whose neighbors' versions it bumps, so a matching version means the sum is current.
     */
    private final double[] boundary;

    /** The version each vertex's boundary quadric was summed at, or -1. */
    private final int[] boundaryVersion;

    /** Scratch corner for the flip check. */
    private final double[] cornerA = new double[AXES];

    /** Scratch corner for the flip check. */
    private final double[] cornerB = new double[AXES];

    /** Scratch corner for the flip check. */
    private final double[] cornerC = new double[AXES];

    /** Scratch normal for the flip check. */
    private final double[] normalBefore = new double[AXES];

    /** Scratch normal for the flip check. */
    private final double[] normalAfter = new double[AXES];

    /**
     * Set up a simplification.
     *
     * @param input The mesh.
     * @param options How to simplify.
     */
    private Simplifier(@NonNull LodMesh input, @NonNull Options options) {
        this.input = input;
        this.options = options;
        final int n = input.getVertexCount();
        final int m = input.getTriangleCount();
        // Widened from float one by one: no array copy converts element types
        positions = new double[n * AXES];
        for (int i = 0; i < n * AXES; ++i) {
            positions[i] = input.positions[i];
        }
        quadrics = new double[n * Quadric.STRIDE];
        locked = input.locked.clone();
        vertexAlive = new boolean[n];
        Arrays.fill(vertexAlive, true);
        versions = new int[n];
        boundary = new double[n * Quadric.STRIDE];
        boundaryVersion = new int[n];
        Arrays.fill(boundaryVersion, -1);
        faces = input.triangles.clone();
        faceAlive = new boolean[m];
        Arrays.fill(faceAlive, true);
        faceCount = m;
        vertexFaces = new IntArrayList[n];
        neighbors = new IntArrayList[n];
        seamNeighbors = new IntArrayList[n];
        for (int v = 0; v < n; ++v) {
            vertexFaces[v] = new IntArrayList();
            neighbors[v] = new IntArrayList();
            seamNeighbors[v] = new IntArrayList();
        }
        history = options.recordHistory() ? new History() : null;
    }

    /**
     * Simplify a mesh.
     *
     * @param input The mesh, which isn't changed.
     * @param options How far and how.
     * @return The simplified mesh and what it took.
     */
    public static Result simplify(@NonNull LodMesh input, @NonNull Options options) {
        Simplifier simplifier = new Simplifier(input, options);
        final int virtualEdges = simplifier.connect();
        simplifier.run();
        return simplifier.result(virtualEdges);
    }

    /**
     * Build the adjacency and quadrics, and add the virtual edges.
     *
     * @return How many virtual edges were added.
     */
    private int connect() {
        final double[] a = new double[AXES];
        final double[] b = new double[AXES];
        final double[] c = new double[AXES];
        for (int f = 0; f < faceAlive.length; ++f) {
            final int va = faces[f * CORNERS];
            final int vb = faces[f * CORNERS + 1];
            final int vc = faces[f * CORNERS + 2];
            vertexFaces[va].addInt(f);
            vertexFaces[vb].addInt(f);
            vertexFaces[vc].addInt(f);
            link(va, vb);
            link(vb, vc);
            link(vc, va);
            load(va, a);
            load(vb, b);
            load(vc, c);
            // Hidden faces pull less on where vertices go, so they give way to visible ones
            final double weight =
                    MIN_VISIBILITY_WEIGHT + (1 - MIN_VISIBILITY_WEIGHT) * input.visibility[f];
            Quadric.addFace(quadrics, va * Quadric.STRIDE, a, b, c, weight);
            Quadric.addFace(quadrics, vb * Quadric.STRIDE, a, b, c, weight);
            Quadric.addFace(quadrics, vc * Quadric.STRIDE, a, b, c, weight);
        }
        for (long seam : options.seams()) {
            linkSeam((int) (seam >>> Integer.SIZE), (int) seam);
        }
        final int[] virtual = VirtualEdges.find(input, options.virtualEdgeRadius());
        for (int i = 0; i + 1 < virtual.length; i += 2) {
            link(virtual[i], virtual[i + 1]);
        }
        for (int v = 0; v < neighbors.length; ++v) {
            for (int k = 0; k < neighbors[v].size(); ++k) {
                final int u = neighbors[v].getInt(k);
                if (v < u) {
                    push(v, u);
                }
            }
        }
        return virtual.length / 2;
    }

    /** Collapse edges until the target is reached or nothing more can go. */
    private void run() {
        while (faceCount > options.targetTriangles() && !heap.isEmpty()) {
            heap.pop();
            final int a = heap.first;
            final int b = heap.second;
            if (!vertexAlive[a]
                    || !vertexAlive[b]
                    || versions[a] != heap.firstVersion
                    || versions[b] != heap.secondVersion) {
                continue;
            }
            // Recompute: the cost was current, but the chosen position isn't kept in the heap
            final double cost = cost(a, b);
            if (Double.isNaN(cost)) {
                continue;
            }
            final double error = errorOf(cost, a, b);
            if (error > options.maxError()) {
                // The heap is ordered by cost, not error, so cheaper-looking edges may still fit
                continue;
            }
            // The end cost() chose to keep: a locked one, or one on a seam
            final int keep = chosenKeep;
            final int remove = keep == a ? b : a;
            if (createsKeptOpen(keep, remove)
                    || dropsLockedEdge(keep, remove)
                    || foldsOntoLockedEdge(keep, remove)
                    || joinsOnPlane(keep, remove)
                    || leavesCell(keep, chosen)
                    || (manifoldAt(keep) && manifoldAt(remove) && !linkCondition(keep, remove))) {
                ++rejectedLinks;
                continue;
            }
            if (flips(keep, remove, chosen)) {
                ++rejectedFlips;
                continue;
            }
            collapse(keep, remove, chosen);
            maxError = Math.max(maxError, error);
            ++collapses;
        }
    }

    /**
     * Push an edge's collapse onto the heap, unless it can't collapse.
     *
     * @param a One end.
     * @param b The other end.
     */
    private void push(int a, int b) {
        final double cost = cost(a, b);
        if (!Double.isNaN(cost)) {
            heap.push(cost, a, b, versions[a], versions[b]);
        }
    }

    /**
     * Work out an edge's collapse cost and where the new vertex goes, into {@link #chosen}.
     *
     * @param a One end.
     * @param b The other end.
     * @return The cost, or NaN if it can't collapse: neither end can go, since both are locked, or
     *     on seams this edge doesn't follow.
     */
    private double cost(int a, int b) {
        final boolean aGoes = removable(a, b);
        final boolean bGoes = removable(b, a);
        if (!aGoes && !bGoes) {
            return Double.NaN;
        }
        Arrays.fill(scratch, 0);
        Quadric.add(scratch, 0, quadrics, a * Quadric.STRIDE);
        Quadric.add(scratch, 0, quadrics, b * Quadric.STRIDE);
        // The memoryless boundary area term, from the boundary edges at either end
        Quadric.add(scratch, 0, boundaryOf(a), a * Quadric.STRIDE);
        Quadric.add(scratch, 0, boundaryOf(b), b * Quadric.STRIDE);
        final boolean aFixed = locked[a] || onSeam(a);
        final boolean bFixed = locked[b] || onSeam(b);
        if (aFixed || bFixed) {
            // The new vertex stays where a fixed end is: a locked vertex doesn't move, and one on
            // a seam moves only along it, onto its neighbor there
            double best = Double.NaN;
            if (aFixed && bGoes) {
                load(a, chosen);
                best = Quadric.error(scratch, 0, chosen);
                chosenKeep = a;
            }
            if (bFixed && aGoes) {
                load(b, point);
                final double atB = Quadric.error(scratch, 0, point);
                if (Double.isNaN(best) || atB < best) {
                    best = atB;
                    System.arraycopy(point, 0, chosen, 0, AXES);
                    chosenKeep = b;
                }
            }
            return best;
        }
        chosenKeep = a;
        if (Quadric.minimum(scratch, 0, chosen)) {
            return Quadric.error(scratch, 0, chosen);
        }
        // Fall back to the best of the ends and the middle
        load(a, point);
        load(b, other);
        double best = Quadric.error(scratch, 0, point);
        System.arraycopy(point, 0, chosen, 0, AXES);
        final double atB = Quadric.error(scratch, 0, other);
        if (atB < best) {
            best = atB;
            System.arraycopy(other, 0, chosen, 0, AXES);
        }
        for (int axis = 0; axis < AXES; ++axis) {
            point[axis] = 0.5 * (point[axis] + other[axis]);
        }
        final double atMiddle = Quadric.error(scratch, 0, point);
        if (atMiddle < best) {
            best = atMiddle;
            System.arraycopy(point, 0, chosen, 0, AXES);
        }
        return best;
    }

    /**
     * Whether a vertex can go in a collapse of one of its edges: not if it is locked, and if it is
     * on a seam, only along the seam.
     *
     * @param v The vertex.
     * @param other The edge's other end.
     * @return True if it can go.
     */
    private boolean removable(int v, int other) {
        return !locked[v] && (!onSeam(v) || isSeamEdge(v, other));
    }

    /**
     * Whether a vertex is on a seam.
     *
     * @param v The vertex.
     * @return True if a seam edge reaches it.
     */
    private boolean onSeam(int v) {
        return !seamNeighbors[v].isEmpty();
    }

    /**
     * Whether two vertices are joined by a seam edge.
     *
     * @param a One vertex.
     * @param b The other.
     * @return True if a seam edge joins them.
     */
    private boolean isSeamEdge(int a, int b) {
        final IntArrayList around = seamNeighbors[a];
        for (int k = 0; k < around.size(); ++k) {
            if (around.getInt(k) == b) {
                return true;
            }
        }
        return false;
    }

    /**
     * Join two vertices by a seam edge, unless they already are.
     *
     * @param a One vertex.
     * @param b The other.
     */
    private void linkSeam(int a, int b) {
        if (a != b && !isSeamEdge(a, b)) {
            seamNeighbors[a].addInt(b);
            seamNeighbors[b].addInt(a);
        }
    }

    /**
     * Make sure a vertex's boundary area quadric, the sum over every boundary edge at it, is
     * current. Seam edges count as boundary edges, so seams keep their shape as open borders do.
     *
     * @param v The vertex.
     * @return The array holding it, at {@code v * Quadric.STRIDE}.
     */
    private double[] boundaryOf(int v) {
        if (boundaryVersion[v] == versions[v]) {
            return boundary;
        }
        final int at = v * Quadric.STRIDE;
        Arrays.fill(boundary, at, at + Quadric.STRIDE, 0);
        final IntArrayList around = neighbors[v];
        for (int k = 0; k < around.size(); ++k) {
            final int u = around.getInt(k);
            if (sharedFaces(v, u) == 1 || isSeamEdge(v, u)) {
                load(v, point);
                load(u, other);
                Quadric.addBoundaryEdge(boundary, at, point, other);
            }
        }
        boundaryVersion[v] = versions[v];
        return boundary;
    }

    /**
     * How many faces use an edge.
     *
     * @param a One end.
     * @param b The other end.
     * @return The count: 0 for a virtual edge, 1 on a boundary, 2 inside, more where faces fan.
     */
    private int sharedFaces(int a, int b) {
        int count = 0;
        final IntArrayList around = vertexFaces[a];
        for (int k = 0; k < around.size(); ++k) {
            final int f = around.getInt(k);
            if (uses(f, b)) {
                ++count;
            }
        }
        return count;
    }

    /**
     * The error of a cost in world units: the square root of the mean squared distance.
     *
     * @param cost The quadric error.
     * @param a One end.
     * @param b The other end.
     * @return The error.
     */
    private double errorOf(double cost, int a, int b) {
        final double weight =
                quadrics[a * Quadric.STRIDE + Quadric.WEIGHT]
                        + quadrics[b * Quadric.STRIDE + Quadric.WEIGHT];
        return Math.sqrt(cost / Math.max(weight, MIN_WEIGHT));
    }

    /**
     * Whether a collapse would take the last face off an edge between two locked vertices: a face
     * using both ends goes, and with it its edge from the kept vertex to its third corner, unless
     * the removed vertex's edge to that corner has another face to carry it over. A border shared
     * with another group is such an edge, and losing it leaves a crack next to the other group,
     * however the boundary around it changed since the locks were set.
     *
     * @param keep The vertex that stays.
     * @param remove The vertex that goes.
     * @return True if it would.
     */
    private boolean dropsLockedEdge(int keep, int remove) {
        if (!locked[keep]) {
            return false;
        }
        final IntArrayList around = vertexFaces[remove];
        for (int k = 0; k < around.size(); ++k) {
            final int f = around.getInt(k);
            if (!faceAlive[f] || !uses(f, keep)) {
                continue;
            }
            for (int corner = 0; corner < CORNERS; ++corner) {
                final int y = faces[f * CORNERS + corner];
                if (y != keep
                        && y != remove
                        && locked[y]
                        && sharedFaces(keep, y) == 1
                        && sharedFaces(remove, y) == 1) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The link condition: a collapse keeps the mesh's edges as manifold as they were only if every
     * vertex sharing a face with both ends is opposite the edge in a face that uses it. Otherwise
     * two edges merge into one used by more faces, such as a sphere's cap pinched into a fin.
     * Vertices joined only by virtual edges don't count, so separate parts can still merge.
     *
     * <p>It only holds where the mesh is manifold around both ends: block content welded by virtual
     * edges meets in junctions of three or more faces everywhere, and keeping the edges around them
     * as they were stopped it halving. There measured errors still bound how far the surface moves,
     * and {@link #foldsOntoLockedEdge} keeps borders as they were.
     *
     * @param keep One end.
     * @param remove The other end.
     * @return True if the collapse is allowed.
     */
    private boolean linkCondition(int keep, int remove) {
        final IntArrayList around = vertexFaces[remove];
        for (int k = 0; k < around.size(); ++k) {
            final int f = around.getInt(k);
            for (int corner = 0; corner < CORNERS; ++corner) {
                final int x = faces[f * CORNERS + corner];
                if (x == keep || x == remove || !sharesFace(keep, x)) {
                    continue;
                }
                if (!opposite(keep, remove, x)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Whether a vertex's edges are manifold: none has more than two faces.
     *
     * @param v The vertex.
     * @return True if so.
     */
    private boolean manifoldAt(int v) {
        final IntArrayList around = vertexFaces[v];
        for (int k = 0; k < around.size(); ++k) {
            final int f = around.getInt(k);
            for (int corner = 0; corner < CORNERS; ++corner) {
                final int u = faces[f * CORNERS + corner];
                if (u != v && sharedFaces(v, u) > MANIFOLD_FACES) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Whether a vertex is opposite an edge, in a face that uses all three.
     *
     * @param a One end.
     * @param b The other end.
     * @param x The vertex.
     * @return True if a face uses a, b and x.
     */
    private boolean opposite(int a, int b, int x) {
        return sharedFaces(a, b, x) > 0;
    }

    /**
     * Whether two vertices are corners of a common face.
     *
     * @param a One vertex.
     * @param b The other.
     * @return True if a face uses both.
     */
    private boolean sharesFace(int a, int b) {
        final IntArrayList around = vertexFaces[a];
        for (int k = 0; k < around.size(); ++k) {
            if (uses(around.getInt(k), b)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a collapse would join the kept vertex to another on one of the cell's planes by an
     * edge the mesh doesn't have: a new edge on the plane, which the neighbor across it doesn't
     * have, as closing a slit or an ear along the plane would make.
     *
     * @param keep The vertex that stays.
     * @param remove The vertex that goes.
     * @return True if it would.
     */
    private boolean joinsOnPlane(int keep, int remove) {
        final int[] planes = options.planes();
        if (planes.length == 0 || planes[keep] == 0) {
            return false;
        }
        final IntArrayList around = vertexFaces[remove];
        for (int k = 0; k < around.size(); ++k) {
            final int f = around.getInt(k);
            if (uses(f, keep)) {
                continue;
            }
            for (int corner = 0; corner < CORNERS; ++corner) {
                final int u = faces[f * CORNERS + corner];
                if (u != remove && (planes[u] & planes[keep]) != 0 && !sharesFace(keep, u)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a collapse would put the kept vertex on or past one of the cell's planes it isn't on,
     * where the neighbor across has nothing to meet it.
     *
     * @param keep The vertex that stays.
     * @param to Where it goes.
     * @return True if it would.
     */
    private boolean leavesCell(int keep, double @NonNull [] to) {
        final double[] cell = options.cell();
        if (cell.length == 0) {
            return false;
        }
        final int on = options.planes()[keep];
        for (int axis = 0; axis < AXES; ++axis) {
            final float at = (float) to[axis];
            if ((on & (1 << axis)) == 0 && at <= (float) cell[axis]) {
                return true;
            }
            if ((on & (1 << (axis + AXES))) == 0 && at >= (float) cell[axis + AXES]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a collapse would fold faces onto an edge between two locked vertices: leave an edge
     * the mesh has with more faces than it has. A border shared with another group has one face
     * here, and the other group's levels meet it with theirs; a second face on it from this group
     * is a fin drawn along the border, in front of whatever the other group draws there. A new edge
     * between locked vertices is an ordinary edge across the group.
     *
     * @param keep The vertex that stays.
     * @param remove The vertex that goes.
     * @return True if it would.
     */
    private boolean foldsOntoLockedEdge(int keep, int remove) {
        if (!locked[keep]) {
            return false;
        }
        final IntArrayList around = vertexFaces[remove];
        for (int k = 0; k < around.size(); ++k) {
            final int f = around.getInt(k);
            if (!faceAlive[f] || uses(f, keep)) {
                continue;
            }
            for (int corner = 0; corner < CORNERS; ++corner) {
                final int y = faces[f * CORNERS + corner];
                if (y == remove || !locked[y]) {
                    continue;
                }
                // Faces on keep-y now, those moving over from remove-y, less those the collapse
                // drops, which used keep, remove and y and counted on both
                final int before = sharedFaces(keep, y);
                final int dropped = sharedFaces(keep, remove, y);
                final int after = before + sharedFaces(remove, y) - 2 * dropped;
                if (before > 0 && after > before) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * How many faces use three vertices.
     *
     * @param a One.
     * @param b Another.
     * @param c The third.
     * @return The count.
     */
    private int sharedFaces(int a, int b, int c) {
        int count = 0;
        final IntArrayList around = vertexFaces[a];
        for (int k = 0; k < around.size(); ++k) {
            final int f = around.getInt(k);
            if (uses(f, b) && uses(f, c)) {
                ++count;
            }
        }
        return count;
    }

    /**
     * Whether a collapse would join the kept vertex to one of the removed vertex's neighbors by an
     * edge that must stay out of the mesh, such as an edge another group already has on the rim of
     * a hole the two share: closing it here would leave a crack where the other group's coarser
     * level meets this one.
     *
     * @param keep The vertex that stays.
     * @param remove The vertex that goes.
     * @return True if it would.
     */
    private boolean createsKeptOpen(int keep, int remove) {
        final long[] keepOpen = options.keepOpen();
        if (keepOpen.length == 0) {
            return false;
        }
        final IntArrayList around = neighbors[remove];
        for (int k = 0; k < around.size(); ++k) {
            final int u = around.getInt(k);
            if (u != keep
                    && Arrays.binarySearch(
                                    keepOpen, LongIntMap.pair(Math.min(keep, u), Math.max(keep, u)))
                            >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether moving two vertices to one position would turn a face over.
     *
     * @param keep The vertex that stays.
     * @param remove The vertex that goes.
     * @param to Where the kept vertex goes.
     * @return True if a face that survives would face the other way.
     */
    private boolean flips(int keep, int remove, double @NonNull [] to) {
        return flipsAround(keep, remove, to) || flipsAround(remove, keep, to);
    }

    /**
     * Whether moving a vertex would turn over one of its faces that doesn't also use the other end.
     *
     * @param v The vertex that moves.
     * @param otherEnd The other end of the edge.
     * @param to Where it goes.
     * @return True if a face flips.
     */
    private boolean flipsAround(int v, int otherEnd, double @NonNull [] to) {
        final double[] a = cornerA;
        final double[] b = cornerB;
        final double[] c = cornerC;
        final double[] before = normalBefore;
        final double[] after = normalAfter;
        final IntArrayList around = vertexFaces[v];
        for (int k = 0; k < around.size(); ++k) {
            final int f = around.getInt(k);
            if (uses(f, otherEnd)) {
                // This face disappears with the edge
                continue;
            }
            load(faces[f * CORNERS], a);
            load(faces[f * CORNERS + 1], b);
            load(faces[f * CORNERS + 2], c);
            Geometry.normal(a, b, c, before);
            if (Geometry.dot(before, before) == 0) {
                continue;
            }
            final int corner = cornerOf(f, v);
            final double[] moved = corner == 0 ? a : (corner == 1 ? b : c);
            System.arraycopy(to, 0, moved, 0, AXES);
            Geometry.normal(a, b, c, after);
            if (Geometry.dot(before, after) <= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Collapse an edge: the kept vertex moves, takes on the removed vertex's faces, edges and
     * quadric, and faces that used both ends go.
     *
     * @param keep The vertex that stays.
     * @param remove The vertex that goes.
     * @param to Where the kept vertex goes.
     */
    private void collapse(int keep, int remove, double @NonNull [] to) {
        if (history != null) {
            recordBefore(keep, remove);
        }
        for (int axis = 0; axis < AXES; ++axis) {
            positions[keep * AXES + axis] = to[axis];
        }
        Quadric.add(quadrics, keep * Quadric.STRIDE, quadrics, remove * Quadric.STRIDE);
        // Move the removed vertex's faces over, dropping those that used both ends. Copied first,
        // since dropping a face takes it out of this list
        for (int f : vertexFaces[remove].toIntArray()) {
            if (!faceAlive[f]) {
                continue;
            }
            if (uses(f, keep)) {
                kill(f);
            } else {
                faces[f * CORNERS + cornerOf(f, remove)] = keep;
                vertexFaces[keep].addInt(f);
            }
        }
        vertexFaces[remove] = new IntArrayList();
        removeDuplicateFaces(keep);
        // Move the removed vertex's edges over
        final IntArrayList edges = neighbors[remove];
        for (int k = 0; k < edges.size(); ++k) {
            final int u = edges.getInt(k);
            neighbors[u].removeInt(remove);
            if (u != keep) {
                link(keep, u);
            }
        }
        neighbors[remove] = new IntArrayList();
        neighbors[keep].removeInt(remove);
        // A seam the removed vertex was on now runs through the kept one
        final IntArrayList seamEdges = seamNeighbors[remove];
        for (int k = 0; k < seamEdges.size(); ++k) {
            final int u = seamEdges.getInt(k);
            seamNeighbors[u].removeInt(remove);
            if (u != keep) {
                linkSeam(keep, u);
            }
        }
        seamNeighbors[remove] = new IntArrayList();
        vertexAlive[remove] = false;
        if (history != null) {
            recordAfter(keep);
        }
        requeue(keep);
    }

    /**
     * Remove a face from the mesh.
     *
     * @param f The face.
     */
    private void kill(int f) {
        faceAlive[f] = false;
        --faceCount;
        for (int corner = 0; corner < CORNERS; ++corner) {
            vertexFaces[faces[f * CORNERS + corner]].removeInt(f);
        }
    }

    /**
     * Remove faces around a vertex that now use the same three vertices as another.
     *
     * @param v The vertex.
     */
    private void removeDuplicateFaces(int v) {
        final IntArrayList around = vertexFaces[v];
        for (int i = 0; i < around.size(); ++i) {
            final int f = around.getInt(i);
            for (int j = i + 1; j < around.size(); ++j) {
                final int g = around.getInt(j);
                if (sameCorners(f, g)) {
                    kill(g);
                    --j;
                }
            }
        }
    }

    /**
     * Whether two faces use the same vertices facing the same way. Two faces on the same vertices
     * facing opposite ways are both kept: they are the two sides of a sheet, such as a wall one
     * block thick collapsed flat, and dropping either leaves a hole where back faces are culled.
     *
     * @param f One face.
     * @param g The other.
     * @return True if g repeats f.
     */
    private boolean sameCorners(int f, int g) {
        final int a = faces[f * CORNERS];
        final int b = faces[f * CORNERS + 1];
        final int c = faces[f * CORNERS + 2];
        if (!uses(g, a) || !uses(g, b) || !uses(g, c)) {
            return false;
        }
        // Same winding if b follows a in g, as it does in f
        final int at = cornerOf(g, a);
        return faces[g * CORNERS + (at + 1) % CORNERS] == b;
    }

    /**
     * Requeue every edge whose cost a collapse may have changed: those at the kept vertex and at
     * its neighbors, since the boundary edges around them may have changed.
     *
     * @param keep The kept vertex.
     */
    private void requeue(int keep) {
        final IntArrayList around = neighbors[keep];
        ++versions[keep];
        for (int k = 0; k < around.size(); ++k) {
            ++versions[around.getInt(k)];
        }
        for (int k = 0; k < around.size(); ++k) {
            final int u = around.getInt(k);
            push(keep, u);
            final IntArrayList second = neighbors[u];
            for (int j = 0; j < second.size(); ++j) {
                final int w = second.getInt(j);
                // Edges between two neighbors are pushed once; edges back to keep already were
                if (w != keep && (u < w || !isNeighbor(keep, w))) {
                    push(u, w);
                }
            }
        }
    }

    /**
     * Whether two vertices are joined by an edge.
     *
     * @param a One vertex.
     * @param b The other.
     * @return True if joined.
     */
    private boolean isNeighbor(int a, int b) {
        final IntArrayList around = neighbors[a];
        for (int k = 0; k < around.size(); ++k) {
            if (around.getInt(k) == b) {
                return true;
            }
        }
        return false;
    }

    /**
     * Join two vertices by an edge, unless they already are.
     *
     * @param a One vertex.
     * @param b The other.
     */
    private void link(int a, int b) {
        if (a != b && !isNeighbor(a, b)) {
            neighbors[a].addInt(b);
            neighbors[b].addInt(a);
        }
    }

    /**
     * Whether a face uses a vertex.
     *
     * @param f The face.
     * @param v The vertex.
     * @return True if one of its corners is v.
     */
    private boolean uses(int f, int v) {
        return faces[f * CORNERS] == v
                || faces[f * CORNERS + 1] == v
                || faces[f * CORNERS + 2] == v;
    }

    /**
     * Which corner of a face a vertex is.
     *
     * @param f The face.
     * @param v The vertex, which the face uses.
     * @return 0, 1 or 2.
     */
    private int cornerOf(int f, int v) {
        if (faces[f * CORNERS] == v) {
            return 0;
        }
        return faces[f * CORNERS + 1] == v ? 1 : 2;
    }

    /**
     * Read a vertex's position.
     *
     * @param v The vertex.
     * @param out The position, written.
     */
    private void load(int v, double @NonNull [] out) {
        out[0] = positions[v * AXES];
        out[1] = positions[v * AXES + 1];
        out[2] = positions[v * AXES + 2];
    }

    /**
     * Record the faces around an edge before it collapses, with their corners.
     *
     * @param keep The kept vertex.
     * @param remove The removed vertex.
     */
    private void recordBefore(int keep, int remove) {
        final IntArrayList ids = history.beforeFaces;
        final int start = ids.size();
        addFacesOf(keep, ids, start);
        addFacesOf(remove, ids, start);
        history.reserve(ids.size());
        for (int k = start; k < ids.size(); ++k) {
            final int f = ids.getInt(k);
            for (int corner = 0; corner < CORNERS; ++corner) {
                final int v = faces[f * CORNERS + corner];
                for (int axis = 0; axis < AXES; ++axis) {
                    history.beforeCorners[k * TRIANGLE_VALUES + corner * AXES + axis] =
                            positions[v * AXES + axis];
                }
            }
        }
        history.beforeStart.addInt(ids.size());
    }

    /**
     * Add a vertex's live faces to a list, skipping ones already added since a start.
     *
     * @param v The vertex.
     * @param ids The list.
     * @param start Where this collapse's faces start in it.
     */
    private void addFacesOf(int v, @NonNull IntArrayList ids, int start) {
        final IntArrayList around = vertexFaces[v];
        for (int k = 0; k < around.size(); ++k) {
            final int f = around.getInt(k);
            boolean seen = false;
            for (int j = start; j < ids.size() && !seen; ++j) {
                seen = ids.getInt(j) == f;
            }
            if (!seen) {
                ids.addInt(f);
            }
        }
    }

    /**
     * Record the faces around the new vertex after a collapse, sorted.
     *
     * @param keep The new vertex.
     */
    private void recordAfter(int keep) {
        final int[] sorted = vertexFaces[keep].toIntArray();
        Arrays.sort(sorted);
        for (int f : sorted) {
            history.afterFaces.addInt(f);
        }
        history.afterStart.addInt(history.afterFaces.size());
        ++history.count;
    }

    /**
     * Gather the simplified mesh.
     *
     * @param virtualEdges How many virtual edges were added.
     * @return The result.
     */
    private Result result(int virtualEdges) {
        final int[] remap = new int[vertexAlive.length];
        Arrays.fill(remap, -1);
        int vertices = 0;
        for (int v = 0; v < vertexAlive.length; ++v) {
            if (vertexAlive[v] && !vertexFaces[v].isEmpty()) {
                remap[v] = vertices++;
            }
        }
        float[] outPositions = new float[vertices * AXES];
        boolean[] outLocked = new boolean[vertices];
        for (int v = 0; v < vertexAlive.length; ++v) {
            if (remap[v] >= 0) {
                for (int axis = 0; axis < AXES; ++axis) {
                    outPositions[remap[v] * AXES + axis] = (float) positions[v * AXES + axis];
                }
                outLocked[remap[v]] = locked[v];
            }
        }
        int[] outTriangles = new int[faceCount * CORNERS];
        int[] outMaterials = new int[faceCount];
        float[] outVisibility = new float[faceCount];
        int[] faceIds = new int[faceCount];
        int t = 0;
        for (int f = 0; f < faceAlive.length; ++f) {
            if (!faceAlive[f]) {
                continue;
            }
            for (int corner = 0; corner < CORNERS; ++corner) {
                outTriangles[t * CORNERS + corner] = remap[faces[f * CORNERS + corner]];
            }
            outMaterials[t] = input.materials[f];
            outVisibility[t] = input.visibility[f];
            faceIds[t] = f;
            ++t;
        }
        LodMesh mesh =
                new LodMesh(
                        input.getOrigin(),
                        outPositions,
                        vertices,
                        outTriangles,
                        t,
                        outMaterials,
                        outVisibility,
                        outLocked,
                        null,
                        input.getLeafError());
        return new Result(
                mesh,
                maxError,
                faceIds,
                history,
                collapses,
                rejectedFlips,
                rejectedLinks,
                virtualEdges);
    }
}
