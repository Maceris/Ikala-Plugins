package com.ikalagaming.graphics.lod;

import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds a level of detail DAG the way Nanite does (Karis et al., <i>Nanite: A Deep Dive</i>,
 * 2021): split the input into clusters, then level by level, group clusters, merge each group,
 * simplify it to half with its outer border locked, and split it back into clusters, until one
 * cluster is left or a level stops halving.
 *
 * <p>Input is a list of meshes, each with its own origin: the sections of a world, or one mesh.
 * Leaf clusters never span two input meshes, so editing a section only dirties its own leaves.
 * Groups in a level are built in parallel.
 *
 * <p>Locked vertices: those a group shares with clusters in other groups of the same level, so
 * clusters of different levels meet exactly along group borders, which is what {@link CrackCheck}
 * tests. The input's open border (edges with one triangle, such as the rim of the loaded area) is
 * simplified like any other edge unless asked to lock it: nothing is drawn beyond it, and when what
 * lies beyond loads, the sections along it are rebaked and their groups rebuilt anyway.
 */
@Slf4j
public final class DagBuilder {

    /** Levels at most, as a safety net. */
    private static final int MAX_LEVELS = 32;

    /** A level whose triangles are at least this share of the last has stopped halving. */
    private static final double STALLED = 0.95;

    /**
     * How much longer a simplified group's edges are than its input's: half the triangles over the
     * same area, so each twice the area.
     */
    private static final double EDGE_GROWTH = Math.sqrt(2);

    /**
     * How far apart samples are when measuring a group's error, in edges of its simplified mesh, so
     * each group takes about as many samples whatever its level.
     */
    private static final double MEASURE_SPACING = 0.5;

    /** The default visibility reach, in leaf edges. */
    private static final double DEFAULT_VISIBILITY_REACH = 12;

    /** Nanoseconds in a millisecond. */
    private static final double NANOS_PER_MILLI = 1e6;

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Values in a triangle's corners. */
    private static final int TRIANGLE_VALUES = Clusters.TRIANGLE_VALUES;

    /**
     * How to build.
     *
     * @param virtualEdgeFactor r for virtual edges as a multiple of each group's average edge
     *     length; 0 for none.
     * @param lockOpenBorder Whether the input's open border is locked at every level, keeping it at
     *     full resolution.
     * @param threads Worker threads for building groups.
     * @param keepLineage Whether to keep where every triangle came from, for {@link Fidelity}. It
     *     holds every group's collapse history.
     * @param visibilityReach How far a leaf triangle's rays must get to count as seen from outside,
     *     as a multiple of the leaves' average edge length; 0 to treat every triangle as open (see
     *     {@link Visibility}).
     */
    public record Options(
            double virtualEdgeFactor,
            boolean lockOpenBorder,
            int threads,
            boolean keepLineage,
            double visibilityReach) {

        /**
         * The defaults: r half the leaves' edge length, the open border simplified, every core but
         * one, the lineage kept, and visibility out to 12 leaf edges, about 13 blocks on block
         * terrain.
         *
         * @return The options.
         */
        public static Options defaults() {
            return new Options(
                    0.5,
                    false,
                    Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
                    true,
                    DEFAULT_VISIBILITY_REACH);
        }
    }

    /**
     * What one group's build made, before ids are assigned.
     *
     * @param children The clusters it was made from.
     * @param level The highest level of its children.
     * @param corners Each output cluster's corners.
     * @param materials Each output cluster's materials.
     * @param visibility Each output cluster's visibility.
     * @param tight Each output cluster's tight sphere.
     * @param error The group's saturated error.
     * @param bound The group's saturated sphere.
     * @param simplifyError The simplification's own error, measured.
     * @param quadricError The simplifier's estimate of it.
     * @param trianglesIn Triangles in.
     * @param trianglesOut Triangles out.
     * @param borderEdges Edges between two locked vertices.
     * @param edges All edges.
     * @param virtualEdges Virtual edges added.
     * @param rejectedFlips Collapses rejected for flipping.
     * @param rejectedLinks Collapses rejected by the link condition.
     * @param nanos How long it took.
     * @param lineage Where its merged triangles came from, or null if not kept.
     * @param faces Each output cluster's triangles as faces of the merged mesh, or null if not
     *     kept.
     */
    private record Built(
            int[] children,
            int level,
            List<float[]> corners,
            List<int[]> materials,
            List<float[]> visibility,
            List<double[]> tight,
            double error,
            double[] bound,
            double simplifyError,
            double quadricError,
            int trianglesIn,
            int trianglesOut,
            int borderEdges,
            int edges,
            int virtualEdges,
            int rejectedFlips,
            int rejectedLinks,
            long nanos,
            Lineage.Group lineage,
            List<int[]> faces) {}

    /** The options. */
    private final Options options;

    /** The DAG being built. */
    private final LodDag dag;

    /** Corners on the input's open border, never moved. */
    private final Set<Grouping.Corner> openBorder = new HashSet<>();

    /** The leaves' average edge length, which virtual edge radii scale from. */
    private double leafEdge;

    /** Stop once this many clusters or fewer are left. */
    private int stopAt = 1;

    /**
     * The minimum corner of the region cell being built, relative to the DAG's origin, or null when
     * not building a cell.
     */
    private double[] cellMin;

    /** The maximum corner of the region cell being built, or null. */
    private double[] cellMax;

    /**
     * Set up a build.
     *
     * @param origin The DAG's origin.
     * @param options How to build.
     */
    private DagBuilder(double @NonNull [] origin, @NonNull Options options) {
        this.options = options;
        dag = new LodDag(origin);
        if (options.keepLineage()) {
            dag.setLineage(new Lineage(dag));
        }
    }

    /**
     * Build a DAG.
     *
     * @param inputs The meshes, each with its own origin. Leaf clusters stay within one.
     * @param options How to build.
     * @return The DAG.
     * @throws IllegalArgumentException If there is nothing to build from.
     */
    public static LodDag build(@NonNull List<LodMesh> inputs, @NonNull Options options) {
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("A DAG needs at least one mesh");
        }
        DagBuilder builder = new DagBuilder(inputs.get(0).getOrigin().clone(), options);
        ExecutorService pool =
                Executors.newFixedThreadPool(options.threads(), daemonThreads("LOD build"));
        try {
            builder.leaves(inputs, pool);
            builder.levels(pool);
        } finally {
            pool.shutdownNow();
        }
        return builder.dag;
    }

    /**
     * Build the DAG of one region cell from its sections. The cell's boundary planes are locked,
     * with the ring of vertices beside them (see {@link #lockCellBoundary}), so that whatever is
     * built on the other side of a plane meets this cell exactly at every level. Building stops
     * once few clusters are left, for the parent cell to take further.
     *
     * @param sections The cell's section meshes, each with its own origin.
     * @param origin Where the DAG's positions are measured from.
     * @param cellMin The cell's minimum corner, relative to the origin.
     * @param cellMax The cell's maximum corner, relative to the origin.
     * @param stopAt Stop once this many clusters or fewer are left.
     * @param options How to build.
     * @return The DAG.
     * @throws IllegalArgumentException If there is nothing to build from.
     */
    public static LodDag buildCell(
            @NonNull List<LodMesh> sections,
            double @NonNull [] origin,
            double @NonNull [] cellMin,
            double @NonNull [] cellMax,
            int stopAt,
            @NonNull Options options) {
        if (sections.isEmpty()) {
            throw new IllegalArgumentException("A cell needs at least one section");
        }
        DagBuilder builder = new DagBuilder(origin.clone(), options);
        builder.cellMin = cellMin.clone();
        builder.cellMax = cellMax.clone();
        builder.stopAt = stopAt;
        ExecutorService pool =
                Executors.newFixedThreadPool(options.threads(), daemonThreads("LOD build"));
        try {
            builder.leaves(sections, pool);
            builder.levels(pool);
        } finally {
            pool.shutdownNow();
        }
        return builder.dag;
    }

    /**
     * Build the DAG of a parent region cell from its children's top clusters, which become its
     * first clusters, keeping their levels, errors and spheres. Only the parent's own boundary is
     * locked, so the planes between its children simplify from here on.
     *
     * @param tops The children's top clusters, relative to the origin; their ids are ignored.
     * @param leafEdge The leaves' average edge length, which scales virtual edges and sampling.
     * @param origin Where the DAG's positions are measured from.
     * @param cellMin The cell's minimum corner, relative to the origin.
     * @param cellMax The cell's maximum corner, relative to the origin.
     * @param stopAt Stop once this many clusters or fewer are left.
     * @param options How to build.
     * @return The DAG, whose first clusters are the tops in order.
     * @throws IllegalArgumentException If there is nothing to build from.
     */
    public static LodDag buildParent(
            @NonNull List<LodDag.Cluster> tops,
            double leafEdge,
            double @NonNull [] origin,
            double @NonNull [] cellMin,
            double @NonNull [] cellMax,
            int stopAt,
            @NonNull Options options) {
        if (tops.isEmpty()) {
            throw new IllegalArgumentException("A parent cell needs at least one cluster");
        }
        DagBuilder builder = new DagBuilder(origin.clone(), options);
        builder.cellMin = cellMin.clone();
        builder.cellMax = cellMax.clone();
        builder.stopAt = stopAt;
        builder.leafEdge = leafEdge;
        builder.dag.setLeafEdge(leafEdge);
        for (LodDag.Cluster top : tops) {
            builder.dag.add(
                    new LodDag.Cluster(
                            builder.dag.clusters().size(),
                            top.level(),
                            top.corners(),
                            top.materials(),
                            top.visibility(),
                            top.error(),
                            top.bound(),
                            top.tight(),
                            -1,
                            -1));
            if (builder.dag.getLineage() != null) {
                builder.dag.getLineage().addCluster(null);
            }
        }
        ExecutorService pool =
                Executors.newFixedThreadPool(options.threads(), daemonThreads("LOD build"));
        try {
            builder.levels(pool);
        } finally {
            pool.shutdownNow();
        }
        return builder.dag;
    }

    /**
     * Name the worker threads, and make them daemons.
     *
     * @param prefix The name prefix.
     * @return The factory.
     */
    static ThreadFactory daemonThreads(@NonNull String prefix) {
        final AtomicInteger count = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + " " + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * Split each input into leaf clusters, work out how visible their triangles are, and find the
     * open border.
     *
     * @param inputs The meshes.
     * @param pool The workers.
     */
    private void leaves(@NonNull List<LodMesh> inputs, @NonNull ExecutorService pool) {
        double edgeLength = 0;
        long edgeCount = 0;
        for (int section = 0; section < inputs.size(); ++section) {
            final LodMesh mesh = inputs.get(section);
            final float[] offset = new float[AXES];
            for (int axis = 0; axis < AXES; ++axis) {
                offset[axis] = (float) (mesh.getOrigin()[axis] - dag.getOrigin()[axis]);
            }
            for (int[] triangles : Clusters.split(mesh, Clusters.MAX_TRIANGLES)) {
                final float[] corners = Clusters.corners(mesh, triangles, offset);
                final int[] materials = new int[triangles.length];
                final float[] visibility = new float[triangles.length];
                for (int i = 0; i < triangles.length; ++i) {
                    materials[i] = mesh.material(triangles[i]);
                    visibility[i] = mesh.visibility(triangles[i]);
                }
                final double[] tight = Clusters.tightSphere(corners);
                dag.add(
                        new LodDag.Cluster(
                                dag.clusters().size(),
                                0,
                                corners,
                                materials,
                                visibility,
                                0,
                                tight,
                                tight,
                                -1,
                                section));
                if (dag.getLineage() != null) {
                    dag.getLineage().addCluster(null);
                }
            }
            for (int t = 0; t < mesh.getTriangleCount(); ++t) {
                for (int corner = 0; corner < AXES; ++corner) {
                    final int v = mesh.corner(t, corner);
                    final int w = mesh.corner(t, (corner + 1) % AXES);
                    double d = 0;
                    for (int axis = 0; axis < AXES; ++axis) {
                        final double delta = mesh.position(v, axis) - mesh.position(w, axis);
                        d += delta * delta;
                    }
                    edgeLength += Math.sqrt(d);
                    ++edgeCount;
                }
            }
        }
        leafEdge = edgeCount == 0 ? 1 : edgeLength / edgeCount;
        dag.setLeafEdge(leafEdge);
        if (options.visibilityReach() > 0) {
            leafVisibility(options.visibilityReach() * leafEdge, pool);
        }
        if (options.lockOpenBorder()) {
            List<Integer> all = new ArrayList<>();
            for (int i = 0; i < dag.clusters().size(); ++i) {
                all.add(i);
            }
            for (Map.Entry<Grouping.Edge, Integer> edge : Grouping.edgeUses(dag, all).entrySet()) {
                if (edge.getValue() == 1) {
                    openBorder.add(edge.getKey().a());
                    openBorder.add(edge.getKey().b());
                }
            }
        }
    }

    /**
     * Work out how visible every leaf triangle is from outside, against all the leaves, and store
     * it in the leaves.
     *
     * @param reach How far a ray must get to escape, in world units.
     * @param pool The workers.
     */
    private void leafVisibility(double reach, @NonNull ExecutorService pool) {
        final long start = System.nanoTime();
        int total = 0;
        for (LodDag.Cluster cluster : dag.clusters()) {
            total += cluster.corners().length;
        }
        final float[] all = new float[total];
        int at = 0;
        for (LodDag.Cluster cluster : dag.clusters()) {
            System.arraycopy(cluster.corners(), 0, all, at, cluster.corners().length);
            at += cluster.corners().length;
        }
        final float[] seen = Visibility.compute(all, reach, pool);
        int t = 0;
        for (LodDag.Cluster cluster : dag.clusters()) {
            System.arraycopy(seen, t, cluster.visibility(), 0, cluster.triangleCount());
            t += cluster.triangleCount();
        }
        dag.setVisibilityMillis((System.nanoTime() - start) / NANOS_PER_MILLI);
    }

    /**
     * Build level after level.
     *
     * @param pool The workers.
     */
    private void levels(@NonNull ExecutorService pool) {
        int[] active = new int[dag.clusters().size()];
        for (int i = 0; i < active.length; ++i) {
            active[i] = i;
        }
        int round = 0;
        while (true) {
            final int top = dag.getLevels() - 1;
            if (active.length <= stopAt) {
                dag.setStopReason(
                        (active.length == 1 ? "one cluster" : active.length + " clusters")
                                + " left at level "
                                + top);
                return;
            }
            if (round >= MAX_LEVELS) {
                dag.setStopReason("reached " + MAX_LEVELS + " levels");
                return;
            }
            final int[][] groups = Grouping.group(dag, active);
            final Map<Grouping.Corner, Integer> owners = owners(groups);
            if (cellMin != null) {
                lockCellBoundary(owners, active);
            }
            final List<Grouping.Edge> rim = rim(active);
            lockRimNearBorders(owners, rim);
            List<Future<Built>> futures = new ArrayList<>();
            for (int[] group : groups) {
                futures.add(pool.submit(() -> buildGroup(group, owners, rim)));
            }
            int trianglesIn = 0;
            int trianglesOut = 0;
            IntArrayList next = new IntArrayList();
            for (Future<Built> future : futures) {
                final Built built;
                try {
                    built = future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    dag.setStopReason("interrupted");
                    return;
                } catch (ExecutionException e) {
                    throw new IllegalStateException("A LOD group failed to build", e.getCause());
                }
                trianglesIn += built.trianglesIn();
                trianglesOut += built.trianglesOut();
                add(built, next);
            }
            active = next.toIntArray();
            ++round;
            if (trianglesOut >= STALLED * trianglesIn) {
                dag.setStopReason(
                        String.format(
                                "stopped halving at level %d (%d of %d triangles)",
                                dag.getLevels() - 1, trianglesOut, trianglesIn));
                return;
            }
        }
    }

    /**
     * Lock a region cell's boundary: every corner on one of its six planes, and every corner that
     * shares a triangle with one. What lies across a plane is built separately, so no group here
     * knows its open edges; with the ring beside the plane locked too, no collapse can join two
     * corners on the plane by a new edge, the way closing a slit or an ear would, so the plane's
     * edges stay exactly as the input has them until the parent cell unlocks them.
     *
     * @param owners Which group uses each corner, -1 for locked; the boundary's corners become -1.
     * @param active This level's clusters.
     */
    private void lockCellBoundary(
            @NonNull Map<Grouping.Corner, Integer> owners, int @NonNull [] active) {
        final Set<Grouping.Corner> lock = new HashSet<>();
        for (int id : active) {
            final float[] corners = dag.clusters().get(id).corners();
            for (int t = 0; t < corners.length; t += TRIANGLE_VALUES) {
                boolean touches = false;
                for (int corner = 0; corner < AXES; ++corner) {
                    touches |= onCellPlane(corners, t + corner * AXES);
                }
                if (touches) {
                    for (int corner = 0; corner < AXES; ++corner) {
                        lock.add(Grouping.Corner.of(corners, t + corner * AXES));
                    }
                }
            }
        }
        lock.forEach(corner -> owners.put(corner, -1));
    }

    /**
     * Whether a corner lies on one of the cell's planes.
     *
     * @param corners Corner positions.
     * @param at Where the corner starts.
     * @return True if it does.
     */
    private boolean onCellPlane(float @NonNull [] corners, int at) {
        for (int axis = 0; axis < AXES; ++axis) {
            final float value = corners[at + axis];
            if (value == (float) cellMin[axis] || value == (float) cellMax[axis]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Which group uses each corner this level: the group's index, or -1 for corners more than one
     * group uses, which are locked.
     *
     * @param groups The groups.
     * @return The owner of each corner.
     */
    private Map<Grouping.Corner, Integer> owners(int @NonNull [][] groups) {
        Map<Grouping.Corner, Integer> owners = new HashMap<>();
        for (int g = 0; g < groups.length; ++g) {
            final int group = g;
            for (int id : groups[g]) {
                final float[] corners = dag.clusters().get(id).corners();
                for (int i = 0; i < corners.length; i += AXES) {
                    owners.merge(
                            Grouping.Corner.of(corners, i), group, (a, b) -> a.equals(b) ? a : -1);
                }
            }
        }
        return owners;
    }

    /**
     * A level's open edges: those only one of its triangles uses.
     *
     * @param active The level's clusters.
     * @return The edges.
     */
    private List<Grouping.Edge> rim(int @NonNull [] active) {
        List<Integer> clusters = new ArrayList<>();
        for (int id : active) {
            clusters.add(id);
        }
        List<Grouping.Edge> out = new ArrayList<>();
        for (Map.Entry<Grouping.Edge, Integer> edge : Grouping.edgeUses(dag, clusters).entrySet()) {
            if (edge.getValue() == 1) {
                out.add(edge.getKey());
            }
        }
        return out;
    }

    /**
     * Lock the rim vertices next to corners that groups share. A rim vertex beside a shared corner
     * can be the tip of an ear, the only triangle on its group's side of a border edge the groups
     * share; collapsing the tip would take that triangle and leave the other group's edge facing
     * nothing. Keeping these vertices also stops a rim from collapsing onto the shared corners,
     * while the rest of it still simplifies.
     *
     * @param owners Which group uses each corner, -1 for shared; rim corners to lock become -1.
     * @param rim This level's open edges.
     */
    private static void lockRimNearBorders(
            @NonNull Map<Grouping.Corner, Integer> owners, @NonNull List<Grouping.Edge> rim) {
        List<Grouping.Corner> lock = new ArrayList<>();
        for (Grouping.Edge edge : rim) {
            final boolean aShared = owners.getOrDefault(edge.a(), -1) < 0;
            final boolean bShared = owners.getOrDefault(edge.b(), -1) < 0;
            if (aShared && !bShared) {
                lock.add(edge.b());
            } else if (bShared && !aShared) {
                lock.add(edge.a());
            }
        }
        lock.forEach(corner -> owners.put(corner, -1));
    }

    /**
     * The edges a group must not create: open edges of other groups between two corners it shares
     * with them. A hole or slit in the input can have its sides in two groups, one with the edge
     * a–b and the other with the path a–w–b. If the second closed it with its own a–b, a cut
     * drawing its coarser level next to the first group would leave a–w–b uncovered.
     *
     * @param builder The builder of the group's merged mesh, to find corners by position.
     * @param merged The merged mesh.
     * @param rim The level's open edges.
     * @return The edges, as packed vertex pairs of the merged mesh.
     */
    private static long[] keepOpen(
            LodMesh.@NonNull Builder builder,
            @NonNull LodMesh merged,
            @NonNull List<Grouping.Edge> rim) {
        final LongIntMap own = new LongIntMap(merged.getTriangleCount() * AXES);
        for (int t = 0; t < merged.getTriangleCount(); ++t) {
            for (int corner = 0; corner < AXES; ++corner) {
                final int v = merged.corner(t, corner);
                final int w = merged.corner(t, (corner + 1) % AXES);
                own.putIfAbsent(LongIntMap.pair(Math.min(v, w), Math.max(v, w)), 0);
            }
        }
        final IntArrayList out = new IntArrayList();
        for (Grouping.Edge edge : rim) {
            final int a = find(builder, edge.a());
            final int b = find(builder, edge.b());
            if (a >= 0 && b >= 0) {
                final long key = LongIntMap.pair(Math.min(a, b), Math.max(a, b));
                if (own.get(key) == LongIntMap.MISSING) {
                    out.addInt(Math.min(a, b));
                    out.addInt(Math.max(a, b));
                }
            }
        }
        final long[] keys = new long[out.size() / 2];
        for (int i = 0; i < keys.length; ++i) {
            keys[i] = LongIntMap.pair(out.getInt(2 * i), out.getInt(2 * i + 1));
        }
        return keys;
    }

    /**
     * The merged mesh's vertex at a corner.
     *
     * @param builder The builder.
     * @param corner The corner.
     * @return The vertex, or -1 if the group has none there.
     */
    private static int find(LodMesh.@NonNull Builder builder, Grouping.@NonNull Corner corner) {
        return builder.find(
                Float.intBitsToFloat(corner.x()),
                Float.intBitsToFloat(corner.y()),
                Float.intBitsToFloat(corner.z()));
    }

    /**
     * Merge, simplify and split one group. On a worker.
     *
     * @param children The group's clusters.
     * @param owners Which group uses each corner.
     * @param rim This level's open edges.
     * @return What it made.
     */
    private Built buildGroup(
            int @NonNull [] children,
            @NonNull Map<Grouping.Corner, Integer> owners,
            @NonNull List<Grouping.Edge> rim) {
        final long start = System.nanoTime();
        // Children can be of different levels in a parent cell; the group is above the highest
        int level = 0;
        for (int id : children) {
            level = Math.max(level, dag.clusters().get(id).level());
        }
        // Merge
        LodMesh.Builder builder = LodMesh.builder(dag.getOrigin());
        int trianglesIn = 0;
        double childError = 0;
        List<double[]> spheres = new ArrayList<>();
        final IntArrayList sourceCluster = new IntArrayList();
        final IntArrayList sourceTriangle = new IntArrayList();
        for (int id : children) {
            final LodDag.Cluster cluster = dag.clusters().get(id);
            final float[] corners = cluster.corners();
            for (int t = 0; t < cluster.triangleCount(); ++t) {
                final int[] v = new int[AXES];
                for (int corner = 0; corner < AXES; ++corner) {
                    final int at = t * TRIANGLE_VALUES + corner * AXES;
                    v[corner] = builder.vertex(corners[at], corners[at + 1], corners[at + 2]);
                    final Grouping.Corner key = Grouping.Corner.of(corners, at);
                    if (owners.getOrDefault(key, -1) < 0 || openBorder.contains(key)) {
                        builder.lock(v[corner]);
                    }
                }
                if (builder.triangle(
                        v[0], v[1], v[2], cluster.materials()[t], cluster.visibility()[t])) {
                    sourceCluster.addInt(id);
                    sourceTriangle.addInt(t);
                }
            }
            trianglesIn += cluster.triangleCount();
            childError = Math.max(childError, cluster.error());
            spheres.add(cluster.bound());
        }
        final LodMesh merged = builder.build();
        // Simplify to half, then map materials back
        // Scaled by the group's own edges rather than its level: levels climb faster in region
        // cells, whose parents count up from their highest child, but triangle sizes don't
        final double edge = averageEdge(merged);
        final double radius = options.virtualEdgeFactor() * edge;
        final Simplifier.Result result =
                Simplifier.simplify(
                        merged,
                        Simplifier.Options.toCount(merged.getTriangleCount() / 2, radius)
                                .keepingOpen(keepOpen(builder, merged, rim)));
        Correspondence.assignMaterials(merged, result);
        // Split
        List<float[]> outCorners = new ArrayList<>();
        List<int[]> outMaterials = new ArrayList<>();
        List<float[]> outVisibility = new ArrayList<>();
        List<double[]> outTight = new ArrayList<>();
        List<int[]> outFaces = options.keepLineage() ? new ArrayList<>() : null;
        final float[] noOffset = new float[AXES];
        for (int[] triangles : Clusters.split(result.mesh(), Clusters.MAX_TRIANGLES)) {
            final float[] corners = Clusters.corners(result.mesh(), triangles, noOffset);
            final int[] materials = new int[triangles.length];
            final float[] visibility = new float[triangles.length];
            for (int i = 0; i < triangles.length; ++i) {
                materials[i] = result.mesh().material(triangles[i]);
                visibility[i] = result.mesh().visibility(triangles[i]);
            }
            final double[] tight = Clusters.tightSphere(corners);
            outCorners.add(corners);
            outMaterials.add(materials);
            outVisibility.add(visibility);
            outTight.add(tight);
            spheres.add(tight);
            if (outFaces != null) {
                final int[] faces = new int[triangles.length];
                for (int i = 0; i < triangles.length; ++i) {
                    faces[i] = result.faceIds()[triangles[i]];
                }
                outFaces.add(faces);
            }
        }
        // Measure how far the output is from the children, and add the children's own error: by the
        // triangle inequality that bounds how far the output is from the input
        final double simplifyError =
                Hausdorff.between(result.mesh(), merged, MEASURE_SPACING * edge * EDGE_GROWTH);
        final double error = childError + simplifyError;
        final double[] bound = Clusters.enclose(spheres);
        final int[] edgeCounts = countEdges(merged);
        return new Built(
                children,
                level,
                outCorners,
                outMaterials,
                outVisibility,
                outTight,
                error,
                bound,
                simplifyError,
                result.error(),
                trianglesIn,
                result.mesh().getTriangleCount(),
                edgeCounts[0],
                edgeCounts[1],
                result.virtualEdges(),
                result.rejectedFlips(),
                result.rejectedLinks(),
                System.nanoTime() - start,
                options.keepLineage()
                        ? new Lineage.Group(
                                new Correspondence(merged, result.history()),
                                sourceCluster.toIntArray(),
                                sourceTriangle.toIntArray())
                        : null,
                outFaces);
    }

    /**
     * A mesh's average edge length, each edge counted once.
     *
     * @param mesh The mesh.
     * @return The length, or the leaves' if the mesh has no edges.
     */
    private double averageEdge(@NonNull LodMesh mesh) {
        final LongIntMap seen = new LongIntMap(mesh.getTriangleCount() * AXES);
        double total = 0;
        for (int t = 0; t < mesh.getTriangleCount(); ++t) {
            for (int corner = 0; corner < AXES; ++corner) {
                final int v = mesh.corner(t, corner);
                final int w = mesh.corner(t, (corner + 1) % AXES);
                if (seen.putIfAbsent(LongIntMap.pair(Math.min(v, w), Math.max(v, w)), 0)
                        == LongIntMap.MISSING) {
                    double d = 0;
                    for (int axis = 0; axis < AXES; ++axis) {
                        final double delta = mesh.position(v, axis) - mesh.position(w, axis);
                        d += delta * delta;
                    }
                    total += Math.sqrt(d);
                }
            }
        }
        return seen.size() == 0 ? leafEdge : total / seen.size();
    }

    /**
     * Count a mesh's edges, and those between two locked vertices.
     *
     * @param mesh The mesh.
     * @return The locked edges, then all edges.
     */
    private static int[] countEdges(@NonNull LodMesh mesh) {
        final LongIntMap edges = new LongIntMap(mesh.getTriangleCount() * AXES);
        int locked = 0;
        for (int t = 0; t < mesh.getTriangleCount(); ++t) {
            for (int corner = 0; corner < AXES; ++corner) {
                final int v = mesh.corner(t, corner);
                final int w = mesh.corner(t, (corner + 1) % AXES);
                if (edges.putIfAbsent(LongIntMap.pair(Math.min(v, w), Math.max(v, w)), 0)
                                == LongIntMap.MISSING
                        && mesh.isLocked(v)
                        && mesh.isLocked(w)) {
                    ++locked;
                }
            }
        }
        return new int[] {locked, edges.size()};
    }

    /**
     * Add a built group and its clusters to the DAG.
     *
     * @param built The group.
     * @param next Where the new clusters' ids go.
     */
    private void add(@NonNull Built built, @NonNull IntArrayList next) {
        final int level = built.level();
        final int groupId = dag.groups().size();
        final int[] outputs = new int[built.corners().size()];
        for (int i = 0; i < outputs.length; ++i) {
            outputs[i] = dag.clusters().size();
            dag.add(
                    new LodDag.Cluster(
                            outputs[i],
                            level + 1,
                            built.corners().get(i),
                            built.materials().get(i),
                            built.visibility().get(i),
                            built.error(),
                            built.bound(),
                            built.tight().get(i),
                            groupId,
                            -1));
            next.addInt(outputs[i]);
            if (dag.getLineage() != null) {
                dag.getLineage().addCluster(built.faces().get(i));
            }
        }
        if (dag.getLineage() != null) {
            dag.getLineage().addGroup(built.lineage());
        }
        dag.add(
                new LodDag.Group(
                        groupId,
                        level,
                        built.children(),
                        outputs,
                        built.error(),
                        built.bound(),
                        built.simplifyError(),
                        built.quadricError(),
                        built.trianglesIn(),
                        built.trianglesOut(),
                        built.borderEdges(),
                        built.edges(),
                        built.virtualEdges(),
                        built.rejectedFlips(),
                        built.rejectedLinks(),
                        built.nanos()));
    }
}
