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

    /** Runs of edges per worker the face contacts are searched in, so uneven runs even out. */
    private static final int RUNS_PER_THREAD = 4;

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
     * @param mergePlanar Whether to merge each input's coplanar triangles of a material first,
     *     which leaves the surface exactly as it was with fewer triangles (see {@link
     *     PlanarMerge}).
     */
    public record Options(
            double virtualEdgeFactor,
            boolean lockOpenBorder,
            int threads,
            boolean keepLineage,
            double visibilityReach,
            boolean mergePlanar) {

        /**
         * Options that merge coplanar triangles first.
         *
         * @param virtualEdgeFactor r for virtual edges, as a multiple of the average edge length.
         * @param lockOpenBorder Whether the input's open border is locked at every level.
         * @param threads Worker threads.
         * @param keepLineage Whether to keep where every triangle came from.
         * @param visibilityReach How far a leaf triangle's rays must get to count as seen.
         */
        public Options(
                double virtualEdgeFactor,
                boolean lockOpenBorder,
                int threads,
                boolean keepLineage,
                double visibilityReach) {
            this(virtualEdgeFactor, lockOpenBorder, threads, keepLineage, visibilityReach, true);
        }

        /**
         * The same options, on some other number of threads.
         *
         * @param count The threads.
         * @return The options.
         */
        public Options withThreads(int count) {
            return new Options(
                    virtualEdgeFactor,
                    lockOpenBorder,
                    count,
                    keepLineage,
                    visibilityReach,
                    mergePlanar);
        }

        /**
         * The same options, merging coplanar triangles or not.
         *
         * @param merge Whether to merge them.
         * @return The options.
         */
        public Options withMergePlanar(boolean merge) {
            return new Options(
                    virtualEdgeFactor,
                    lockOpenBorder,
                    threads,
                    keepLineage,
                    visibilityReach,
                    merge);
        }

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
     * @param simplifyError How far its output is from its base geometry, measured.
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
     * @param bases Each output cluster's base triangles, packed by {@link #base}.
     */
    private record Built(
            int[] children,
            int level,
            List<float[]> corners,
            List<int[]> materials,
            List<float[]> visibility,
            List<int[]> attributes,
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
            List<int[]> faces,
            List<long[]> bases) {}

    /** The options. */
    private final Options options;

    /**
     * The base triangles each cluster stands for, by cluster id, each packed by {@link #base}: the
     * triangles of the clusters the build started from, which are the leaves, or a parent cell's
     * inputs. When a group splits its output into clusters, each of its base triangles goes to the
     * output cluster nearest it, so the clusters share them out exactly. A group's error is
     * measured against its base triangles rather than added up level by level, so it stays the true
     * distance from them. Written between levels only, so the workers can read it while they build.
     */
    private final List<long[]> basis = new ArrayList<>();

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
                            top.attributes(),
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
     * Merge each input's coplanar triangles, on the workers.
     *
     * @param inputs The meshes.
     * @param pool The workers.
     * @return The merged meshes, in the same order.
     */
    private static List<LodMesh> merged(
            @NonNull List<LodMesh> inputs, @NonNull ExecutorService pool) {
        final List<java.util.concurrent.Future<LodMesh>> futures = new ArrayList<>();
        for (LodMesh mesh : inputs) {
            futures.add(pool.submit(() -> PlanarMerge.merge(mesh)));
        }
        final List<LodMesh> out = new ArrayList<>(inputs.size());
        for (java.util.concurrent.Future<LodMesh> future : futures) {
            try {
                out.add(future.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted merging planar triangles", e);
            } catch (java.util.concurrent.ExecutionException e) {
                throw new IllegalStateException("Merging planar triangles failed", e.getCause());
            }
        }
        return out;
    }

    /**
     * Split each input into leaf clusters, work out how visible their triangles are, and find the
     * open border.
     *
     * @param inputs The meshes.
     * @param pool The workers.
     */
    private void leaves(@NonNull List<LodMesh> given, @NonNull ExecutorService pool) {
        final List<LodMesh> inputs = options.mergePlanar() ? merged(given, pool) : given;
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
                                Clusters.attributes(mesh, triangles),
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
            for (Grouping.Edge edge :
                    rim(all.stream().mapToInt(Integer::intValue).toArray(), true)) {
                openBorder.add(edge.a());
                openBorder.add(edge.b());
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
        // Each cluster the build starts from stands for its own triangles
        while (basis.size() < dag.clusters().size()) {
            final int id = basis.size();
            final long[] own = new long[dag.clusters().get(id).triangleCount()];
            for (int t = 0; t < own.length; ++t) {
                own[t] = base(id, t);
            }
            basis.add(own);
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
            lockTJunctions(owners, groups);
            lockFaceContacts(owners, groups, pool);
            if (cellMin != null) {
                lockCellBoundary(owners, active);
            }
            final List<Grouping.Edge> rim = rim(active, false);
            lockRimNearBorders(owners, rim);
            List<Future<Built>> futures = new ArrayList<>();
            for (int[] group : groups) {
                futures.add(pool.submit(() -> buildGroup(group, owners, rim)));
            }
            int trianglesIn = 0;
            int trianglesOut = 0;
            IntArrayList next = new IntArrayList();
            // Every group finishes before any is added, since adding writes what the workers read
            final List<Built> builts = new ArrayList<>(futures.size());
            for (Future<Built> future : futures) {
                try {
                    builts.add(future.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    dag.setStopReason("interrupted");
                    return;
                } catch (ExecutionException e) {
                    throw new IllegalStateException("A LOD group failed to build", e.getCause());
                }
            }
            for (Built built : builts) {
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
     * A level's edges, each once per triangle using it, in the direction that triangle runs.
     *
     * @param active The level's clusters.
     * @param tags Receives each edge's cluster's index in {@code active}.
     * @param open Receives whether each is open, used by one triangle.
     * @param triangles Receives each edge's triangle, numbered through all of {@code active}'s
     *     triangles in order.
     * @return The edges, x, y, z of the start then the end.
     */
    private List<float[]> edges(
            int @NonNull [] active,
            @NonNull IntArrayList tags,
            @NonNull List<Boolean> open,
            @NonNull IntArrayList triangles) {
        final List<Integer> clusters = new ArrayList<>();
        for (int id : active) {
            clusters.add(id);
        }
        final Map<Grouping.Edge, Integer> uses = Grouping.edgeUses(dag, clusters);
        final List<float[]> out = new ArrayList<>();
        int triangle = 0;
        for (int i = 0; i < active.length; ++i) {
            final float[] corners = dag.clusters().get(active[i]).corners();
            for (int t = 0; t < corners.length; t += TRIANGLE_VALUES, ++triangle) {
                for (int corner = 0; corner < AXES; ++corner) {
                    final int p = t + corner * AXES;
                    final int q = t + ((corner + 1) % AXES) * AXES;
                    final Grouping.Edge edge =
                            Grouping.Edge.of(
                                    Grouping.Corner.of(corners, p), Grouping.Corner.of(corners, q));
                    out.add(
                            new float[] {
                                corners[p],
                                corners[p + 1],
                                corners[p + 2],
                                corners[q],
                                corners[q + 1],
                                corners[q + 2]
                            });
                    tags.addInt(i);
                    open.add(uses.get(edge) == 1);
                    triangles.addInt(triangle);
                }
            }
        }
        return out;
    }

    /**
     * Lock where groups meet without sharing corners: both ends of every edge that shares a stretch
     * of its line with another group's edge. Both groups then keep every point where either's edges
     * end along the line, so neither moves its side off it, nor drops a corner the other's edges
     * still meet. Edges the groups share exactly are locked already, by their corners.
     *
     * @param owners Which group uses each corner; the corners found become -1.
     * @param groups The groups.
     */
    private void lockTJunctions(
            @NonNull Map<Grouping.Corner, Integer> owners, int @NonNull [][] groups) {
        int total = 0;
        for (int[] group : groups) {
            total += group.length;
        }
        final int[] active = new int[total];
        final int[] groupOf = new int[total];
        int at = 0;
        for (int g = 0; g < groups.length; ++g) {
            for (int id : groups[g]) {
                active[at] = id;
                groupOf[at++] = g;
            }
        }
        final IntArrayList tags = new IntArrayList();
        final List<float[]> edges = edges(active, tags, new ArrayList<>(), new IntArrayList());
        final TJunctions index = new TJunctions();
        for (int i = 0; i < edges.size(); ++i) {
            index.add(start(edges.get(i)), end(edges.get(i)), groupOf[tags.getInt(i)], -1, -1);
        }
        for (int i = 0; i < edges.size(); ++i) {
            final float[] edge = edges.get(i);
            if (index.overlapsOther(start(edge), end(edge), groupOf[tags.getInt(i)])) {
                owners.put(Grouping.Corner.of(edge, 0), -1);
                owners.put(Grouping.Corner.of(edge, AXES), -1);
            }
        }
    }

    /**
     * Lock where a group's open edge ends against the inside of another group's face, as a slab's
     * side does against a wall: the face's corners, so the face stays where the edge meets it, and
     * the edge's ends, so the edge stays on the face. Where the edge runs along one of the face's
     * edges instead, {@link #lockTJunctions} keeps it.
     *
     * <p>The open edges are searched on the workers, a run of them each, and what they find is
     * locked in edge order, so the result doesn't depend on how they were split.
     *
     * @param owners Which group uses each corner; the corners found become -1.
     * @param groups The groups.
     * @param pool The workers.
     */
    private void lockFaceContacts(
            @NonNull Map<Grouping.Corner, Integer> owners,
            int @NonNull [][] groups,
            @NonNull ExecutorService pool) {
        int total = 0;
        for (int[] group : groups) {
            total += group.length;
        }
        final int[] active = new int[total];
        final int[] groupOf = new int[total];
        int at = 0;
        for (int g = 0; g < groups.length; ++g) {
            for (int id : groups[g]) {
                active[at] = id;
                groupOf[at++] = g;
            }
        }
        // Every triangle of the level, with its cluster's index in active
        int corners = 0;
        for (int id : active) {
            corners += dag.clusters().get(id).corners().length;
        }
        final float[] all = new float[corners];
        final int[] clusterOf = new int[corners / TRIANGLE_VALUES];
        at = 0;
        for (int i = 0; i < active.length; ++i) {
            final float[] own = dag.clusters().get(active[i]).corners();
            System.arraycopy(own, 0, all, at, own.length);
            Arrays.fill(clusterOf, at / TRIANGLE_VALUES, (at + own.length) / TRIANGLE_VALUES, i);
            at += own.length;
        }
        final TriangleBvh tree = new TriangleBvh(all);
        final IntArrayList tags = new IntArrayList();
        final List<Boolean> open = new ArrayList<>();
        final IntArrayList triangles = new IntArrayList();
        final List<float[]> edges = edges(active, tags, open, triangles);
        final int runs = Math.max(1, Math.min(edges.size(), options.threads() * RUNS_PER_THREAD));
        final List<Future<List<Grouping.Corner>>> futures = new ArrayList<>(runs);
        for (int run = 0; run < runs; ++run) {
            final int from = (int) ((long) edges.size() * run / runs);
            final int to = (int) ((long) edges.size() * (run + 1) / runs);
            futures.add(
                    pool.submit(
                            () ->
                                    faceContacts(
                                            from, to, edges, open, tags, triangles, tree, all,
                                            clusterOf, groupOf)));
        }
        for (Future<List<Grouping.Corner>> future : futures) {
            final List<Grouping.Corner> found;
            try {
                found = future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException e) {
                throw new IllegalStateException("Finding face contacts failed", e.getCause());
            }
            for (Grouping.Corner corner : found) {
                owners.put(corner, -1);
            }
        }
    }

    /**
     * Find where a run of open edges ends against the inside of another group's face, see {@link
     * #lockFaceContacts}. On a worker.
     *
     * @param from The first edge.
     * @param to Past the last edge.
     * @param edges Every edge of the level.
     * @param open Whether each is open.
     * @param tags Each one's cluster's index in the active clusters.
     * @param triangles Each one's own triangle, which its search skips.
     * @param tree Every triangle of the level.
     * @param all Their corners.
     * @param clusterOf Each triangle's cluster's index in the active clusters.
     * @param groupOf Each active cluster's group.
     * @return The corners to lock, in edge order.
     */
    private static List<Grouping.Corner> faceContacts(
            int from,
            int to,
            @NonNull List<float[]> edges,
            @NonNull List<Boolean> open,
            @NonNull IntArrayList tags,
            @NonNull IntArrayList triangles,
            @NonNull TriangleBvh tree,
            float @NonNull [] all,
            int @NonNull [] clusterOf,
            int @NonNull [] groupOf) {
        final List<Grouping.Corner> found = new ArrayList<>();
        final TriangleBvh.Scratch scratch = new TriangleBvh.Scratch();
        final double touch = TJunctions.TOUCH * TJunctions.TOUCH;
        final double[] point = new double[AXES];
        for (int i = from; i < to; ++i) {
            if (!open.get(i)) {
                continue;
            }
            final float[] edge = edges.get(i);
            final double[] a = start(edge);
            final double[] b = end(edge);
            final int mine = tags.getInt(i);
            final int samples = TJunctions.samples(a, b);
            boolean contact = false;
            for (int k = 0; k < samples; ++k) {
                TJunctions.sample(a, b, k, samples, point);
                if (tree.nearest(point, touch, touch, triangles.getInt(i), scratch) >= touch) {
                    continue;
                }
                final int other = clusterOf[scratch.triangle];
                if (groupOf[other] == groupOf[mine]
                        || onTriangleEdge(all, scratch.triangle, point)) {
                    continue;
                }
                contact = true;
                for (int corner = 0; corner < AXES; ++corner) {
                    found.add(
                            Grouping.Corner.of(
                                    all, scratch.triangle * TRIANGLE_VALUES + corner * AXES));
                }
            }
            if (contact) {
                found.add(Grouping.Corner.of(edge, 0));
                found.add(Grouping.Corner.of(edge, AXES));
            }
        }
        return found;
    }

    /**
     * Whether a point is on one of a triangle's edges.
     *
     * @param corners Every triangle's corners.
     * @param triangle The triangle.
     * @param point The point.
     * @return True if it is within touching distance of one.
     */
    private static boolean onTriangleEdge(
            float @NonNull [] corners, int triangle, double @NonNull [] point) {
        final int base = triangle * TRIANGLE_VALUES;
        for (int corner = 0; corner < AXES; ++corner) {
            final int p = base + corner * AXES;
            final int q = base + ((corner + 1) % AXES) * AXES;
            final double[] from = {corners[p], corners[p + 1], corners[p + 2]};
            final double[] to = {corners[q], corners[q + 1], corners[q + 2]};
            if (Geometry.segmentDistanceSquared(from, to, point, point)
                    < TJunctions.TOUCH * TJunctions.TOUCH) {
                return true;
            }
        }
        return false;
    }

    /**
     * An open edge's start.
     *
     * @param edge The edge, start then end.
     * @return x, y, z.
     */
    private static double[] start(float @NonNull [] edge) {
        return new double[] {edge[0], edge[1], edge[2]};
    }

    /**
     * An open edge's end.
     *
     * @param edge The edge, start then end.
     * @return x, y, z.
     */
    private static double[] end(float @NonNull [] edge) {
        return new double[] {edge[AXES], edge[AXES + 1], edge[AXES + 2]};
    }

    /**
     * A level's open edges: those only one of its triangles uses, optionally less those that edges
     * running the other way cover, which are where parts meet at T-junctions rather than the edge
     * of the input. Ears along a border can be anywhere an edge is open, so guarding them takes
     * every open edge.
     *
     * @param active The level's clusters.
     * @param seamless Whether to leave out edges where parts meet at T-junctions.
     * @return The edges.
     */
    private List<Grouping.Edge> rim(int @NonNull [] active, boolean seamless) {
        List<Grouping.Edge> out = new ArrayList<>();
        final List<Boolean> open = new ArrayList<>();
        final List<float[]> edges = edges(active, new IntArrayList(), open, new IntArrayList());
        final TJunctions index = new TJunctions();
        for (float[] edge : edges) {
            index.add(start(edge), end(edge), 0, -1, -1);
        }
        for (int i = 0; i < edges.size(); ++i) {
            final float[] edge = edges.get(i);
            if (open.get(i) && !(seamless && index.covered(start(edge), end(edge)))) {
                out.add(
                        Grouping.Edge.of(
                                Grouping.Corner.of(edge, 0), Grouping.Corner.of(edge, AXES)));
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
     * Lock the far end of every open edge of a group's merged mesh that has one end locked, the way
     * {@link #lockRimNearBorders} does for the level's rim. Welding T-junctions leaves open edges
     * the level never had, and an ear's tip on one of them, collapsed, would take the only triangle
     * on the group's side of a locked border edge with it.
     *
     * @param mesh The merged mesh, whose locks change.
     */
    private static void lockEars(@NonNull LodMesh mesh) {
        final LongIntMap uses = new LongIntMap(mesh.getTriangleCount() * AXES);
        final IntArrayList counts = new IntArrayList();
        for (int t = 0; t < mesh.getTriangleCount(); ++t) {
            for (int corner = 0; corner < AXES; ++corner) {
                final int v = mesh.corner(t, corner);
                final int w = mesh.corner(t, (corner + 1) % AXES);
                final long key = LongIntMap.pair(Math.min(v, w), Math.max(v, w));
                final int at = uses.putIfAbsent(key, counts.size());
                if (at == LongIntMap.MISSING) {
                    counts.addInt(1);
                } else {
                    counts.setInt(at, counts.getInt(at) + 1);
                }
            }
        }
        final IntArrayList lock = new IntArrayList();
        for (int t = 0; t < mesh.getTriangleCount(); ++t) {
            for (int corner = 0; corner < AXES; ++corner) {
                final int v = mesh.corner(t, corner);
                final int w = mesh.corner(t, (corner + 1) % AXES);
                if (counts.getInt(uses.get(LongIntMap.pair(Math.min(v, w), Math.max(v, w)))) != 1
                        || mesh.isLocked(v) == mesh.isLocked(w)) {
                    continue;
                }
                lock.addInt(mesh.isLocked(v) ? w : v);
            }
        }
        for (int i = 0; i < lock.size(); ++i) {
            mesh.setLocked(lock.getInt(i), true);
        }
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
     * Pack a base triangle: its cluster's id and its index there.
     *
     * @param cluster The cluster's id.
     * @param triangle The triangle's index in it.
     * @return The packed triangle.
     */
    private static long base(int cluster, int triangle) {
        return ((long) cluster << Integer.SIZE) | (triangle & 0xFFFFFFFFL);
    }

    /**
     * A packed base triangle's cluster.
     *
     * @param packed The packed triangle.
     * @return The cluster's id.
     */
    private static int baseCluster(long packed) {
        return (int) (packed >>> Integer.SIZE);
    }

    /**
     * A packed base triangle's index in its cluster.
     *
     * @param packed The packed triangle.
     * @return The index.
     */
    private static int baseTriangle(long packed) {
        return (int) packed;
    }

    /**
     * The base triangles some clusters stand for, together.
     *
     * @param clusters The clusters.
     * @return The packed base triangles.
     */
    private long[] basisOf(int @NonNull [] clusters) {
        int total = 0;
        for (int id : clusters) {
            total += basis.get(id).length;
        }
        final long[] out = new long[total];
        int at = 0;
        for (int id : clusters) {
            final long[] own = basis.get(id);
            System.arraycopy(own, 0, out, at, own.length);
            at += own.length;
        }
        return out;
    }

    /**
     * Share a group's base triangles out among its output clusters: each goes to the cluster with
     * the triangle nearest its middle.
     *
     * @param bases The group's packed base triangles.
     * @param outCorners Each output cluster's corners.
     * @return Each output cluster's packed base triangles.
     */
    private List<long[]> shareOut(long @NonNull [] bases, @NonNull List<float[]> outCorners) {
        final List<long[]> out = new ArrayList<>(outCorners.size());
        if (outCorners.size() == 1) {
            out.add(bases);
            return out;
        }
        int total = 0;
        for (float[] corners : outCorners) {
            total += corners.length;
        }
        final float[] all = new float[total];
        final int[] owner = new int[total / TRIANGLE_VALUES];
        int at = 0;
        for (int i = 0; i < outCorners.size(); ++i) {
            final float[] corners = outCorners.get(i);
            System.arraycopy(corners, 0, all, at, corners.length);
            java.util.Arrays.fill(
                    owner, at / TRIANGLE_VALUES, (at + corners.length) / TRIANGLE_VALUES, i);
            at += corners.length;
        }
        final TriangleBvh tree = new TriangleBvh(all);
        final TriangleBvh.Scratch scratch = new TriangleBvh.Scratch();
        final double[] middle = new double[AXES];
        final List<IntArrayList> chosen = new ArrayList<>(outCorners.size());
        for (int i = 0; i < outCorners.size(); ++i) {
            chosen.add(new IntArrayList());
        }
        for (int b = 0; b < bases.length; ++b) {
            final float[] corners = dag.clusters().get(baseCluster(bases[b])).corners();
            final int t = baseTriangle(bases[b]) * TRIANGLE_VALUES;
            for (int axis = 0; axis < AXES; ++axis) {
                middle[axis] =
                        (corners[t + axis]
                                        + corners[t + AXES + axis]
                                        + corners[t + 2 * AXES + axis])
                                / AXES;
            }
            tree.nearest(middle, Double.POSITIVE_INFINITY, scratch);
            chosen.get(owner[scratch.triangle]).addInt(b);
        }
        for (IntArrayList picks : chosen) {
            final long[] own = new long[picks.size()];
            for (int i = 0; i < own.length; ++i) {
                own[i] = bases[picks.getInt(i)];
            }
            out.add(own);
        }
        return out;
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
        final LodMesh.Builder builder = LodMesh.builder(dag.getOrigin());
        int trianglesIn = 0;
        double childError = 0;
        List<double[]> spheres = new ArrayList<>();
        final IntArrayList sourceCluster = new IntArrayList();
        final IntArrayList sourceTriangle = new IntArrayList();
        // Textured triangles' attributes, to give the output's again, and what meets at each
        // vertex, to keep seams: where textured triangles meet with different attributes or
        // materials, or meet projected ones
        final AttributeTransfer transfer = new AttributeTransfer();
        final Map<Integer, int[]> meeting = new HashMap<>();
        boolean anyTextured = false;
        for (int id : children) {
            anyTextured |= dag.clusters().get(id).attributes().length > 0;
        }
        for (int id : children) {
            final LodDag.Cluster cluster = dag.clusters().get(id);
            final float[] corners = cluster.corners();
            final int[] attributes = cluster.attributes();
            for (int t = 0; t < cluster.triangleCount(); ++t) {
                final int[] v = new int[AXES];
                final boolean textured = Attributes.textured(attributes, t);
                final int material = cluster.materials()[t];
                for (int corner = 0; corner < AXES; ++corner) {
                    final int at = t * TRIANGLE_VALUES + corner * AXES;
                    v[corner] = builder.vertex(corners[at], corners[at + 1], corners[at + 2]);
                    final Grouping.Corner key = Grouping.Corner.of(corners, at);
                    if (owners.getOrDefault(key, -1) < 0 || openBorder.contains(key)) {
                        builder.lock(v[corner]);
                    }
                    if (anyTextured
                            && isSeam(
                                    meeting,
                                    v[corner],
                                    textured,
                                    material,
                                    attributes,
                                    t * Attributes.PER_TRIANGLE + corner * Attributes.VALUES)) {
                        builder.lock(v[corner]);
                    }
                }
                if (textured) {
                    transfer.add(
                            corners,
                            t * TRIANGLE_VALUES,
                            attributes,
                            t * Attributes.PER_TRIANGLE,
                            material);
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
        LodMesh merged = builder.build();
        // Join the group's parts where they meet at T-junctions, so it simplifies one surface
        final IntArrayList from = new IntArrayList();
        final LodMesh welded = TJunctions.weld(merged, from);
        if (welded != merged) {
            final int[] clusters = sourceCluster.toIntArray();
            final int[] triangles = sourceTriangle.toIntArray();
            sourceCluster.clear();
            sourceTriangle.clear();
            for (int i = 0; i < from.size(); ++i) {
                sourceCluster.addInt(clusters[from.getInt(i)]);
                sourceTriangle.addInt(triangles[from.getInt(i)]);
            }
            merged = welded;
        }
        lockEars(merged);
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
        List<int[]> outAttributes = new ArrayList<>();
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
            outAttributes.add(
                    transfer.isEmpty() ? Attributes.NONE : transfer.transfer(corners, materials));
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
        // Measure how far the output is from the base triangles it stands for, and add the bases'
        // own error, which is 0 for leaves: by the triangle inequality that bounds how far the
        // output is from the leaves, without adding up every level's step on the way. Never under
        // a child's, so a cluster's error doesn't fall going up.
        final long[] bases = basisOf(children);
        double baseError = 0;
        LodMesh.Builder baseBuilder = LodMesh.builder(dag.getOrigin());
        for (long packed : bases) {
            final LodDag.Cluster base = dag.clusters().get(baseCluster(packed));
            final int t = baseTriangle(packed);
            baseError = Math.max(baseError, base.error());
            final float[] corners = base.corners();
            final int[] v = new int[AXES];
            for (int corner = 0; corner < AXES; ++corner) {
                final int at = t * TRIANGLE_VALUES + corner * AXES;
                v[corner] = baseBuilder.vertex(corners[at], corners[at + 1], corners[at + 2]);
            }
            baseBuilder.triangle(v[0], v[1], v[2], base.materials()[t], base.visibility()[t]);
        }
        final double simplifyError =
                Hausdorff.between(
                        result.mesh(), baseBuilder.build(), MEASURE_SPACING * edge * EDGE_GROWTH);
        final double error = Math.max(childError, baseError + simplifyError);
        final List<long[]> outBases = shareOut(bases, outCorners);
        final double[] bound = Clusters.enclose(spheres);
        final int[] edgeCounts = countEdges(merged);
        return new Built(
                children,
                level,
                outCorners,
                outMaterials,
                outVisibility,
                outAttributes,
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
                outFaces,
                outBases);
    }

    /**
     * Note what meets at a vertex, and whether that makes it a seam the simplifier must keep:
     * textured triangles meeting there with different attributes or materials, or a textured
     * triangle meeting a projected one. Projected triangles meeting each other make no seam.
     *
     * @param meeting What has met at each vertex so far: textured or not, the material, and for a
     *     textured triangle its corner's attributes.
     * @param vertex The vertex.
     * @param textured Whether this triangle is textured.
     * @param material Its material.
     * @param attributes Its cluster's attributes.
     * @param at Where this corner's start.
     * @return True if the vertex is a seam.
     */
    private static boolean isSeam(
            @NonNull Map<Integer, int[]> meeting,
            int vertex,
            boolean textured,
            int material,
            int @NonNull [] attributes,
            int at) {
        final int[] here =
                textured
                        ? new int[] {
                            1, material, attributes[at], attributes[at + 1], attributes[at + 2]
                        }
                        : new int[] {0, material};
        final int[] before = meeting.putIfAbsent(vertex, here);
        if (before == null || (before[0] == 0 && !textured)) {
            return false;
        }
        return !Arrays.equals(before, here);
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
            basis.add(built.bases().get(i));
            dag.add(
                    new LodDag.Cluster(
                            outputs[i],
                            level + 1,
                            built.corners().get(i),
                            built.materials().get(i),
                            built.visibility().get(i),
                            built.attributes().get(i),
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
