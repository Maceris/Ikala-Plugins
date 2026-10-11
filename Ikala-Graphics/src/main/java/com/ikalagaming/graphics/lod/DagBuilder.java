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
 * <p>Locked vertices: those a group shares with clusters in other groups of the same level, and, if
 * asked, those on the input's open border (edges with one triangle), which then never change at any
 * level. With the open border locked, every cut has exactly the input's open edges, which is what
 * {@link CrackCheck} tests.
 */
@Slf4j
public final class DagBuilder {

    /** Levels at most, as a safety net. */
    private static final int MAX_LEVELS = 32;

    /** A level whose triangles are at least this share of the last has stopped halving. */
    private static final double STALLED = 0.95;

    /**
     * How much the virtual edge radius grows each level, as clusters spread over twice the area.
     */
    private static final double VIRTUAL_EDGE_GROWTH = Math.sqrt(2);

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Values in a triangle's corners. */
    private static final int TRIANGLE_VALUES = Clusters.TRIANGLE_VALUES;

    /**
     * How to build.
     *
     * @param virtualEdgeFactor r for virtual edges as a multiple of the leaves' average edge
     *     length, growing by √2 a level; 0 for none.
     * @param lockOpenBorder Whether the input's open border is locked at every level.
     * @param threads Worker threads for building groups.
     * @param keepLineage Whether to keep where every triangle came from, for {@link Fidelity}. It
     *     holds every group's collapse history.
     */
    public record Options(
            double virtualEdgeFactor, boolean lockOpenBorder, int threads, boolean keepLineage) {

        /**
         * The defaults: r half the leaves' edge length, the open border locked, every core but one,
         * and the lineage kept.
         *
         * @return The options.
         */
        public static Options defaults() {
            return new Options(
                    0.5, true, Math.max(1, Runtime.getRuntime().availableProcessors() - 1), true);
        }
    }

    /**
     * What one group's build made, before ids are assigned.
     *
     * @param children The clusters it was made from.
     * @param corners Each output cluster's corners.
     * @param materials Each output cluster's materials.
     * @param tight Each output cluster's tight sphere.
     * @param error The group's saturated error.
     * @param bound The group's saturated sphere.
     * @param simplifyError The simplification's own error.
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
            List<float[]> corners,
            List<int[]> materials,
            List<double[]> tight,
            double error,
            double[] bound,
            double simplifyError,
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
        builder.leaves(inputs);
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
     * Split each input into leaf clusters, and find the open border.
     *
     * @param inputs The meshes.
     */
    private void leaves(@NonNull List<LodMesh> inputs) {
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
                for (int i = 0; i < triangles.length; ++i) {
                    materials[i] = mesh.material(triangles[i]);
                }
                final double[] tight = Clusters.tightSphere(corners);
                dag.add(
                        new LodDag.Cluster(
                                dag.clusters().size(),
                                0,
                                corners,
                                materials,
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
     * Build level after level.
     *
     * @param pool The workers.
     */
    private void levels(@NonNull ExecutorService pool) {
        int[] active = new int[dag.clusters().size()];
        for (int i = 0; i < active.length; ++i) {
            active[i] = i;
        }
        int level = 0;
        while (true) {
            if (active.length <= 1) {
                dag.setStopReason("one cluster left at level " + level);
                return;
            }
            if (level >= MAX_LEVELS) {
                dag.setStopReason("reached " + MAX_LEVELS + " levels");
                return;
            }
            final int[][] groups = Grouping.group(dag, active);
            final Map<Grouping.Corner, Integer> owners = owners(groups);
            List<Future<Built>> futures = new ArrayList<>();
            for (int[] group : groups) {
                final int currentLevel = level;
                futures.add(pool.submit(() -> buildGroup(group, owners, currentLevel)));
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
                add(built, level, next);
            }
            active = next.toIntArray();
            ++level;
            if (trianglesOut >= STALLED * trianglesIn) {
                dag.setStopReason(
                        String.format(
                                "stopped halving at level %d (%d of %d triangles)",
                                level, trianglesOut, trianglesIn));
                return;
            }
        }
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
     * Merge, simplify and split one group. On a worker.
     *
     * @param children The group's clusters.
     * @param owners Which group uses each corner.
     * @param level The children's level.
     * @return What it made.
     */
    private Built buildGroup(
            int @NonNull [] children, @NonNull Map<Grouping.Corner, Integer> owners, int level) {
        final long start = System.nanoTime();
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
                if (builder.triangle(v[0], v[1], v[2], cluster.materials()[t])) {
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
        final double radius =
                options.virtualEdgeFactor() * leafEdge * Math.pow(VIRTUAL_EDGE_GROWTH, level);
        final Simplifier.Result result =
                Simplifier.simplify(
                        merged, Simplifier.Options.toCount(merged.getTriangleCount() / 2, radius));
        Correspondence.assignMaterials(merged, result);
        // Split
        List<float[]> outCorners = new ArrayList<>();
        List<int[]> outMaterials = new ArrayList<>();
        List<double[]> outTight = new ArrayList<>();
        List<int[]> outFaces = options.keepLineage() ? new ArrayList<>() : null;
        final float[] noOffset = new float[AXES];
        for (int[] triangles : Clusters.split(result.mesh(), Clusters.MAX_TRIANGLES)) {
            final float[] corners = Clusters.corners(result.mesh(), triangles, noOffset);
            final int[] materials = new int[triangles.length];
            for (int i = 0; i < triangles.length; ++i) {
                materials[i] = result.mesh().material(triangles[i]);
            }
            final double[] tight = Clusters.tightSphere(corners);
            outCorners.add(corners);
            outMaterials.add(materials);
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
        final double error = Math.max(childError, result.error());
        final double[] bound = Clusters.enclose(spheres);
        final int[] edgeCounts = countEdges(merged);
        return new Built(
                children,
                outCorners,
                outMaterials,
                outTight,
                error,
                bound,
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
     * Count a mesh's edges, and those between two locked vertices.
     *
     * @param mesh The mesh.
     * @return The locked edges, then all edges.
     */
    private static int[] countEdges(@NonNull LodMesh mesh) {
        Set<Long> edges = new HashSet<>();
        int locked = 0;
        for (int t = 0; t < mesh.getTriangleCount(); ++t) {
            for (int corner = 0; corner < AXES; ++corner) {
                final int v = mesh.corner(t, corner);
                final int w = mesh.corner(t, (corner + 1) % AXES);
                if (edges.add(((long) Math.min(v, w) << Integer.SIZE) | Math.max(v, w))
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
     * @param level The children's level.
     * @param next Where the new clusters' ids go.
     */
    private void add(@NonNull Built built, int level, @NonNull IntArrayList next) {
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
