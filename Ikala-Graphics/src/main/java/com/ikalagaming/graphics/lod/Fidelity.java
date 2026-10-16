package com.ikalagaming.graphics.lod;

import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;

import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

/**
 * How faithful each level of a DAG is to the input it stands for. Every triangle is sampled at
 * evenly spread points, and each point is followed down to a leaf through the DAG's {@link
 * Lineage}, giving the input's material there and how far the point had to move.
 *
 * <p>Per level, it measures:
 *
 * <ul>
 *   <li><b>Mismatch:</b> the share of area whose input material differs from the material the
 *       triangle was given.
 *   <li><b>Unavoidable:</b> the share of area that differs from the triangle's most common input
 *       material. One material per triangle can't do better; the rest of the mismatch is the
 *       assignment's.
 *   <li><b>Distance:</b> how far each level is from the input, both ways (see {@link Distance}),
 *       against the errors the cut uses.
 *   <li><b>Material shares:</b> each material's share of area, as assigned and as found under the
 *       samples, both over all area and over area seen from above (area times the normal's upward
 *       part), against the input's own shares.
 * </ul>
 */
public final class Fidelity {

    /** Each triangle is cut into this many rows of smaller ones, one sample in each. */
    private static final int SUBDIVISIONS = 4;

    /** Samples per triangle. */
    public static final int SAMPLES = SUBDIVISIONS * SUBDIVISIONS;

    /** Marks a triangle whose assigned material isn't its most common input material. */
    public static final int NOT_MAJORITY = 0x80;

    /** Masks a triangle's mark down to its mismatching samples. */
    public static final int MISMATCH_MASK = 0x7F;

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Values kept per material: assigned area, assigned area from above, then the same mapped. */
    private static final int SHARE_VALUES = 4;

    /** Where a material's share of area, as assigned, is in {@link Level#shares}. */
    public static final int ASSIGNED = 0;

    /** Where its share of area seen from above, as assigned, is. */
    public static final int ASSIGNED_UP = 1;

    /** Where its share of area, as found under the samples, is. */
    public static final int MAPPED = 2;

    /** Where its share of area seen from above, as found under the samples, is. */
    public static final int MAPPED_UP = 3;

    /** A third. */
    private static final double THIRD = 1.0 / 3;

    /** Two thirds. */
    private static final double TWO_THIRDS = 2.0 / 3;

    /** Percent in a whole. */
    private static final double PERCENT = 100;

    /** Each leaf triangle is cut into this many rows for sampling the input side of distance. */
    private static final int INPUT_SUBDIVISIONS = 2;

    /** Leaf samples that share a triangle's area; the corners follow them. */
    private static final int INPUT_INTERIOR = INPUT_SUBDIVISIONS * INPUT_SUBDIVISIONS;

    /** How far apart distance samples are, in leaf edges. */
    static final double SPACING = 0.5;

    /** Values in a triangle's corners. */
    private static final int TRIANGLE_VALUES = Clusters.TRIANGLE_VALUES;

    /**
     * Leaf triangles less visible than this don't count toward the in distance: input nothing can
     * see, such as a cave's walls, may rightly be simplified away.
     */
    static final float VISIBLE = 0.1f;

    /** The percentile of distance reported. */
    private static final double PERCENTILE = 0.95;

    /** Distances this much over the stored error are still within it, for rounding. */
    private static final double TOLERANCE = 1e-4;

    /**
     * One level's numbers.
     *
     * @param level The level.
     * @param triangles Its triangles.
     * @param mismatch The share of area whose input material isn't the assigned one.
     * @param unavoidable The share of area whose input material isn't the triangle's most common.
     * @param mismatchUp The same as mismatch, over area seen from above.
     * @param unavoidableUp The same as unavoidable, over area seen from above.
     * @param distance How far the level is from the input, against the error it stores.
     * @param shares Per material: its share of area as assigned, of area from above as assigned, of
     *     area as mapped, and of area from above as mapped.
     */
    public record Level(
            int level,
            int triangles,
            double mismatch,
            double unavoidable,
            double mismatchUp,
            double unavoidableUp,
            @NonNull Distance distance,
            @NonNull SortedMap<Integer, double[]> shares) {

        /**
         * The share of area given a material other than the most common one under it.
         *
         * @return The assignment's own error.
         */
        public double assignment() {
            return mismatch - unavoidable;
        }

        /**
         * The same as {@link #assignment}, over area seen from above.
         *
         * @return The assignment's own error from above.
         */
        public double assignmentUp() {
            return mismatchUp - unavoidableUp;
        }
    }

    /**
     * How far one level is from the input, measured both ways, against the errors its groups store.
     * Out is from points on the level to the nearest leaf triangle: a surface that moved off the
     * input. In is from points on the visible leaves to the nearest triangle of the level: input
     * that went missing, charged to the group that made that triangle. A group's measured distance
     * is the larger of its two maxima, and the cut is only right if it is no more than the group's
     * stored error.
     *
     * @param outMean The out distance, averaged over area.
     * @param outP95 The out distance 95% of the area is within.
     * @param outMax The largest out distance.
     * @param inMean The in distance, averaged over visible input area.
     * @param inP95 The in distance 95% of that area is within.
     * @param inMax The largest in distance.
     * @param maxError The largest error the level's groups store.
     * @param groups The groups that made the level.
     * @param underBounded Those whose measured distance is over their stored error.
     * @param worstRatio The largest measured distance over stored error of any group.
     */
    public record Distance(
            double outMean,
            double outP95,
            double outMax,
            double inMean,
            double inP95,
            double inMax,
            double maxError,
            int groups,
            int underBounded,
            double worstRatio) {

        /** The leaves' distance: nothing moved. */
        static final Distance NONE = new Distance(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * What measuring one group's distance found.
     *
     * @param out Each out sample's distance.
     * @param outArea The area each stands for.
     * @param inMax The largest in distance charged to the group.
     */
    private record Measured(float[] out, float[] outArea, double inMax) {

        /**
         * The larger of the two maxima.
         *
         * @return The group's measured distance.
         */
        double max() {
            double max = inMax;
            for (float d : out) {
                max = Math.max(max, d);
            }
            return max;
        }
    }

    /**
     * What sampling one cluster found.
     *
     * @param marks Each triangle's mismatching samples, with {@link #NOT_MAJORITY}.
     * @param area Its area.
     * @param mismatchArea Area whose input material isn't the assigned one.
     * @param unavoidableArea Area whose input material isn't the most common one.
     * @param upArea Its area seen from above.
     * @param mismatchUp Mismatching area seen from above.
     * @param unavoidableUp Unavoidably mismatching area seen from above.
     * @param materials Per material, the four areas.
     */
    private record Sampled(
            byte[] marks,
            double area,
            double mismatchArea,
            double unavoidableArea,
            double upArea,
            double mismatchUp,
            double unavoidableUp,
            Map<Integer, double[]> materials) {}

    /** The levels, from the leaves up. */
    private final List<Level> levels;

    /** Each cluster's triangle marks, by cluster id. */
    private final byte[][] marks;

    /** Each group's measured distance, by group id. */
    private final double[] groupDistances;

    /**
     * Measure a DAG.
     *
     * @param levels The levels.
     * @param marks Each cluster's triangle marks.
     * @param groupDistances Each group's measured distance.
     */
    private Fidelity(
            @NonNull List<Level> levels,
            byte @NonNull [][] marks,
            double @NonNull [] groupDistances) {
        this.levels = levels;
        this.marks = marks;
        this.groupDistances = groupDistances;
    }

    /**
     * Sample every triangle of a DAG and follow the samples to the input, on a pool of its own of
     * lowest priority daemon threads, so measuring in the background leaves the rest of the CPU,
     * and the render thread, alone.
     *
     * @param dag The DAG, built with its lineage kept.
     * @param threads How many threads to measure on.
     * @return The measurements.
     * @throws IllegalArgumentException If the lineage wasn't kept.
     */
    public static Fidelity measure(@NonNull LodDag dag, int threads) {
        final ForkJoinPool pool =
                new ForkJoinPool(
                        Math.max(1, threads),
                        owner -> {
                            final ForkJoinWorkerThread worker =
                                    ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(
                                            owner);
                            worker.setName("LOD fidelity " + worker.getPoolIndex());
                            worker.setDaemon(true);
                            worker.setPriority(Thread.MIN_PRIORITY);
                            return worker;
                        },
                        null,
                        false);
        try {
            // Parallel streams run on the pool of the task that starts them
            return pool.submit(() -> measure(dag)).join();
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Sample every triangle of a DAG and follow the samples to the input. Runs on the common pool,
     * or the pool of the task it is called from.
     *
     * @param dag The DAG, built with its lineage kept.
     * @return The measurements.
     * @throws IllegalArgumentException If the lineage wasn't kept.
     */
    public static Fidelity measure(@NonNull LodDag dag) {
        final Lineage lineage = dag.getLineage();
        if (lineage == null) {
            throw new IllegalArgumentException("Measuring fidelity needs the DAG's lineage");
        }
        final double[][] samples = samplePoints(SUBDIVISIONS);
        final Sampled[] sampled =
                IntStream.range(0, dag.clusters().size())
                        .parallel()
                        .mapToObj(id -> sample(lineage, dag.clusters().get(id), samples))
                        .toArray(Sampled[]::new);
        final byte[][] marks = new byte[sampled.length][];
        for (int i = 0; i < sampled.length; ++i) {
            marks[i] = sampled[i].marks();
        }
        final TriangleBvh leaves = new TriangleBvh(leafCorners(dag));
        // Out samples are spread by size; leaves are small, so a few each will do
        final double spacing = dag.getLeafEdge() * SPACING;
        // The input side: every leaf sample's distance to each level, as a whole
        final double[][] inputSamples = inputSamples();
        final int[] leafStart = new int[dag.clusters().size()];
        final float[] inputArea = inputAreas(dag, inputSamples.length, leafStart);
        final Input[] in = new Input[dag.getLevels()];
        IntStream.range(1, dag.getLevels())
                .parallel()
                .forEach(
                        level ->
                                in[level] =
                                        inputDistances(
                                                dag, level, leafStart, inputArea, inputSamples));
        final Measured[] measured =
                IntStream.range(0, dag.groups().size())
                        .parallel()
                        .mapToObj(
                                id -> {
                                    final LodDag.Group group = dag.groups().get(id);
                                    return measure(
                                            dag,
                                            leaves,
                                            group,
                                            spacing,
                                            in[group.level() + 1].groupMax()[id]);
                                })
                        .toArray(Measured[]::new);
        List<Level> levels = new ArrayList<>();
        for (int level = 0; level < dag.getLevels(); ++level) {
            levels.add(
                    level(
                            dag,
                            sampled,
                            measured,
                            level == 0 ? null : in[level].distances(),
                            inputArea,
                            level));
        }
        final double[] groupDistances = new double[measured.length];
        for (int i = 0; i < measured.length; ++i) {
            groupDistances[i] = measured[i].max();
        }
        return new Fidelity(Collections.unmodifiableList(levels), marks, groupDistances);
    }

    /**
     * Barycentric sample points, the centers of a triangle cut into rows of equal smaller
     * triangles, so each stands for the same area.
     *
     * @param subdivisions The rows.
     * @return The points, as weights of the second and third corners: subdivisions^2 of them.
     */
    private static double[][] samplePoints(int subdivisions) {
        final double[][] points = new double[subdivisions * subdivisions][];
        int k = 0;
        for (int i = 0; i < subdivisions; ++i) {
            for (int j = 0; i + j < subdivisions; ++j) {
                // The upright small triangle at (i, j)
                points[k++] = new double[] {(i + THIRD) / subdivisions, (j + THIRD) / subdivisions};
                if (i + j < subdivisions - 1) {
                    // The upside down one beside it
                    points[k++] =
                            new double[] {
                                (i + TWO_THIRDS) / subdivisions, (j + TWO_THIRDS) / subdivisions
                            };
                }
            }
        }
        return points;
    }

    /**
     * Where to sample each leaf triangle: the centers of its four quarters, which share its area,
     * then its corners, which count only toward the maximum.
     *
     * @return The points, as weights of the second and third corners.
     */
    private static double[][] inputSamples() {
        final double[][] interior = samplePoints(INPUT_SUBDIVISIONS);
        final double[][] points = Arrays.copyOf(interior, interior.length + AXES);
        points[interior.length] = new double[] {0, 0};
        points[interior.length + 1] = new double[] {1, 0};
        points[interior.length + 2] = new double[] {0, 1};
        return points;
    }

    /**
     * Every leaf triangle's corners, in one array.
     *
     * @param dag The DAG.
     * @return The corners, 9 floats per triangle.
     */
    private static float[] leafCorners(@NonNull LodDag dag) {
        return corners(
                dag,
                IntStream.range(0, dag.clusters().size())
                        .filter(id -> dag.clusters().get(id).level() == 0)
                        .toArray());
    }

    /**
     * Some clusters' corners, in one array.
     *
     * @param dag The DAG.
     * @param ids The clusters.
     * @return The corners, 9 floats per triangle.
     */
    private static float[] corners(@NonNull LodDag dag, int @NonNull [] ids) {
        int size = 0;
        for (int id : ids) {
            size += dag.clusters().get(id).corners().length;
        }
        final float[] out = new float[size];
        int at = 0;
        for (int id : ids) {
            final float[] corners = dag.clusters().get(id).corners();
            System.arraycopy(corners, 0, out, at, corners.length);
            at += corners.length;
        }
        return out;
    }

    /**
     * The area each leaf sample stands for, 0 for corners and for samples on hidden triangles, and
     * where each leaf's samples start.
     *
     * @param dag The DAG.
     * @param perTriangle Samples per triangle.
     * @param leafStart Where each leaf's samples start, by cluster id, written.
     * @return The areas, every leaf's samples in turn.
     */
    private static float[] inputAreas(
            @NonNull LodDag dag, int perTriangle, int @NonNull [] leafStart) {
        int size = 0;
        for (LodDag.Cluster cluster : dag.clusters()) {
            if (cluster.level() == 0) {
                leafStart[cluster.id()] = size;
                size += cluster.triangleCount() * perTriangle;
            }
        }
        final float[] areas = new float[size];
        final double[] a = new double[AXES];
        final double[] b = new double[AXES];
        final double[] c = new double[AXES];
        for (LodDag.Cluster cluster : dag.clusters()) {
            if (cluster.level() != 0) {
                continue;
            }
            for (int t = 0; t < cluster.triangleCount(); ++t) {
                if (cluster.visibility()[t] < VISIBLE) {
                    continue;
                }
                Lineage.corners(cluster, t, a, b, c);
                final float area = (float) (Geometry.area(a, b, c) / INPUT_INTERIOR);
                final int at = leafStart[cluster.id()] + t * perTriangle;
                Arrays.fill(areas, at, at + INPUT_INTERIOR, area);
            }
        }
        return areas;
    }

    /**
     * The input side of one level's distance.
     *
     * @param distances Each leaf sample's distance to the level.
     * @param groupMax The largest distance charged to each group.
     */
    private record Input(float @NonNull [] distances, double @NonNull [] groupMax) {}

    /**
     * Each visible leaf sample's distance to the nearest triangle of a level, which is charged to
     * the group that made that triangle. A level is measured as a whole, since the leaves under a
     * cluster are shared with its producing group's other clusters, and it includes clusters below
     * it that nothing was made from, which still stand for their part.
     *
     * @param dag The DAG.
     * @param level The level.
     * @param leafStart Where each leaf's samples start.
     * @param inputArea Each sample's area, 0 for hidden ones, which are left at 0.
     * @param inputSamples Where to sample each leaf triangle.
     * @return The distances, every leaf's samples in turn, and each group's largest.
     */
    private static Input inputDistances(
            @NonNull LodDag dag,
            int level,
            int @NonNull [] leafStart,
            float @NonNull [] inputArea,
            double @NonNull [][] inputSamples) {
        final int[] standing =
                IntStream.range(0, dag.clusters().size())
                        .filter(id -> standsAt(dag, id, level))
                        .toArray();
        // The group charged for each standing triangle: its maker, if it made this level
        final IntArrayList charged = new IntArrayList();
        for (int id : standing) {
            final LodDag.Cluster cluster = dag.clusters().get(id);
            final int group = cluster.level() == level ? cluster.producer() : -1;
            for (int t = 0; t < cluster.triangleCount(); ++t) {
                charged.addInt(group);
            }
        }
        final TriangleBvh tree = new TriangleBvh(corners(dag, standing));
        final TriangleBvh.Scratch scratch = new TriangleBvh.Scratch();
        final float[] out = new float[inputArea.length];
        final double[] groupMax = new double[dag.groups().size()];
        final double[] a = new double[AXES];
        final double[] b = new double[AXES];
        final double[] c = new double[AXES];
        final double[] point = new double[AXES];
        for (LodDag.Cluster cluster : dag.clusters()) {
            if (cluster.level() != 0) {
                continue;
            }
            for (int t = 0; t < cluster.triangleCount(); ++t) {
                final int at = leafStart[cluster.id()] + t * inputSamples.length;
                if (inputArea[at] == 0) {
                    continue;
                }
                Lineage.corners(cluster, t, a, b, c);
                for (int k = 0; k < inputSamples.length; ++k) {
                    final double[] s = inputSamples[k];
                    for (int axis = 0; axis < AXES; ++axis) {
                        point[axis] =
                                a[axis] + s[0] * (b[axis] - a[axis]) + s[1] * (c[axis] - a[axis]);
                    }
                    final double d =
                            Math.sqrt(tree.nearest(point, Double.POSITIVE_INFINITY, scratch));
                    out[at + k] = (float) d;
                    final int group = scratch.triangle < 0 ? -1 : charged.getInt(scratch.triangle);
                    if (group >= 0) {
                        groupMax[group] = Math.max(groupMax[group], d);
                    }
                }
            }
        }
        return new Input(out, groupMax);
    }

    /**
     * Whether a cluster stands for its part of the input at a level: it is on that level, or below
     * it with nothing made from it.
     *
     * @param dag The DAG.
     * @param id The cluster.
     * @param level The level.
     * @return True if it does.
     */
    private static boolean standsAt(@NonNull LodDag dag, int id, int level) {
        final int own = dag.clusters().get(id).level();
        return own == level || (own < level && dag.consumer(id) < 0);
    }

    /**
     * Measure how far one group's output is from the input, both ways.
     *
     * @param dag The DAG.
     * @param leaves A tree over every leaf triangle.
     * @param group The group.
     * @param spacing About how far apart to sample its output.
     * @param inMax The largest in distance charged to the group.
     * @return What it found.
     */
    private static Measured measure(
            @NonNull LodDag dag,
            @NonNull TriangleBvh leaves,
            LodDag.@NonNull Group group,
            double spacing,
            double inMax) {
        final TriangleBvh.Scratch scratch = new TriangleBvh.Scratch();
        final float[] output = corners(dag, group.outputs());
        final Sampler out = new Sampler();
        Hausdorff.each(
                output,
                null,
                spacing,
                (point, area) ->
                        out.add(
                                Math.sqrt(leaves.nearest(point, Double.POSITIVE_INFINITY, scratch)),
                                area));
        return new Measured(out.distances(), out.areas(), inMax);
    }

    /** Sample distances and the areas they stand for, as they are found. */
    private static final class Sampler {
        /** Room for this many at first. */
        private static final int CAPACITY = 1024;

        /** The distances. */
        private float[] distances = new float[CAPACITY];

        /** The areas. */
        private float[] areas = new float[CAPACITY];

        /** How many there are. */
        private int size;

        /**
         * Add one.
         *
         * @param distance The distance.
         * @param area The area it stands for.
         */
        void add(double distance, double area) {
            if (size == distances.length) {
                distances = Arrays.copyOf(distances, size * 2);
                areas = Arrays.copyOf(areas, size * 2);
            }
            distances[size] = (float) distance;
            areas[size] = (float) area;
            ++size;
        }

        /**
         * The distances found.
         *
         * @return A copy.
         */
        float[] distances() {
            return Arrays.copyOf(distances, size);
        }

        /**
         * The areas found.
         *
         * @return A copy.
         */
        float[] areas() {
            return Arrays.copyOf(areas, size);
        }
    }

    /**
     * Sample one cluster.
     *
     * @param lineage The DAG's lineage.
     * @param cluster The cluster.
     * @param samples The sample points.
     * @return What it found.
     */
    private static Sampled sample(
            @NonNull Lineage lineage,
            LodDag.@NonNull Cluster cluster,
            double @NonNull [][] samples) {
        final byte[] marks = new byte[cluster.triangleCount()];
        final Map<Integer, double[]> materials = new HashMap<>();
        final double[] a = new double[AXES];
        final double[] b = new double[AXES];
        final double[] c = new double[AXES];
        final double[] n = new double[AXES];
        final double[] point = new double[AXES];
        final Map<Integer, Integer> counts = new HashMap<>();
        double area = 0;
        double mismatchArea = 0;
        double unavoidableArea = 0;
        double upTotal = 0;
        double mismatchUp = 0;
        double unavoidableUp = 0;
        for (int t = 0; t < cluster.triangleCount(); ++t) {
            Lineage.corners(cluster, t, a, b, c);
            Geometry.normal(a, b, c, n);
            final double length = Math.sqrt(Geometry.dot(n, n));
            final double triangleArea = length / 2;
            final double upArea = length == 0 ? 0 : triangleArea * Math.max(0, n[1] / length);
            final double sampleArea = triangleArea / SAMPLES;
            final double sampleUp = upArea / SAMPLES;
            final int assigned = cluster.materials()[t];
            final double[] own = materials.computeIfAbsent(assigned, k -> new double[SHARE_VALUES]);
            own[ASSIGNED] += triangleArea;
            own[ASSIGNED_UP] += upArea;
            counts.clear();
            int mismatched = 0;
            for (double[] s : samples) {
                for (int axis = 0; axis < AXES; ++axis) {
                    point[axis] = a[axis] + s[0] * (b[axis] - a[axis]) + s[1] * (c[axis] - a[axis]);
                }
                final int found = lineage.toLeaf(cluster.id(), t, point).material();
                counts.merge(found, 1, Integer::sum);
                if (found != assigned) {
                    ++mismatched;
                }
                final double[] under =
                        materials.computeIfAbsent(found, k -> new double[SHARE_VALUES]);
                under[MAPPED] += sampleArea;
                under[MAPPED_UP] += sampleUp;
            }
            int most = 0;
            for (int count : counts.values()) {
                most = Math.max(most, count);
            }
            final boolean notMajority = counts.getOrDefault(assigned, 0) < most;
            marks[t] = (byte) (mismatched | (notMajority ? NOT_MAJORITY : 0));
            area += triangleArea;
            mismatchArea += mismatched * sampleArea;
            unavoidableArea += (SAMPLES - most) * sampleArea;
            upTotal += upArea;
            mismatchUp += mismatched * sampleUp;
            unavoidableUp += (SAMPLES - most) * sampleUp;
        }
        return new Sampled(
                marks,
                area,
                mismatchArea,
                unavoidableArea,
                upTotal,
                mismatchUp,
                unavoidableUp,
                materials);
    }

    /**
     * Add up one level's clusters.
     *
     * @param dag The DAG.
     * @param sampled What each cluster's sampling found.
     * @param measured What each group's distance measuring found.
     * @param in Each leaf sample's distance to the level, null for the leaves.
     * @param inputArea Each leaf sample's area.
     * @param level The level.
     * @return The level's numbers.
     */
    private static Level level(
            @NonNull LodDag dag,
            Sampled @NonNull [] sampled,
            Measured @NonNull [] measured,
            float[] in,
            float @NonNull [] inputArea,
            int level) {
        int triangles = 0;
        double area = 0;
        double mismatch = 0;
        double unavoidable = 0;
        double up = 0;
        double mismatchUp = 0;
        double unavoidableUp = 0;
        final SortedMap<Integer, double[]> totals = new TreeMap<>();
        for (LodDag.Cluster cluster : dag.clusters()) {
            if (cluster.level() != level) {
                continue;
            }
            final Sampled s = sampled[cluster.id()];
            triangles += cluster.triangleCount();
            area += s.area();
            mismatch += s.mismatchArea();
            unavoidable += s.unavoidableArea();
            up += s.upArea();
            mismatchUp += s.mismatchUp();
            unavoidableUp += s.unavoidableUp();
            for (Map.Entry<Integer, double[]> entry : s.materials().entrySet()) {
                final double[] total =
                        totals.computeIfAbsent(entry.getKey(), k -> new double[SHARE_VALUES]);
                for (int i = 0; i < SHARE_VALUES; ++i) {
                    total[i] += entry.getValue()[i];
                }
            }
        }
        // Areas to shares
        final double[] sums = new double[SHARE_VALUES];
        for (double[] total : totals.values()) {
            for (int i = 0; i < SHARE_VALUES; ++i) {
                sums[i] += total[i];
            }
        }
        for (double[] total : totals.values()) {
            for (int i = 0; i < SHARE_VALUES; ++i) {
                total[i] = sums[i] == 0 ? 0 : total[i] / sums[i];
            }
        }
        return new Level(
                level,
                triangles,
                area == 0 ? 0 : mismatch / area,
                area == 0 ? 0 : unavoidable / area,
                up == 0 ? 0 : mismatchUp / up,
                up == 0 ? 0 : unavoidableUp / up,
                level == 0 ? Distance.NONE : distance(dag, measured, in, inputArea, level),
                totals);
    }

    /**
     * Add up the distances of the groups that made a level.
     *
     * @param dag The DAG.
     * @param measured What each group's measuring found.
     * @param in Each leaf sample's distance to the level.
     * @param inputArea Each leaf sample's area.
     * @param level The level, above the leaves.
     * @return The level's distances.
     */
    private static Distance distance(
            @NonNull LodDag dag,
            Measured @NonNull [] measured,
            float @NonNull [] in,
            float @NonNull [] inputArea,
            int level) {
        List<float[]> out = new ArrayList<>();
        List<float[]> outArea = new ArrayList<>();
        double maxError = 0;
        int groups = 0;
        int underBounded = 0;
        double worstRatio = 0;
        for (LodDag.Group group : dag.groups()) {
            if (group.level() != level - 1) {
                continue;
            }
            final Measured m = measured[group.id()];
            out.add(m.out());
            outArea.add(m.outArea());
            maxError = Math.max(maxError, group.error());
            ++groups;
            final double distance = m.max();
            if (distance > group.error() + TOLERANCE) {
                ++underBounded;
            }
            worstRatio = Math.max(worstRatio, distance / Math.max(group.error(), TOLERANCE));
        }
        final double[] outStats = stats(out, outArea);
        final double[] inStats = stats(List.of(in), List.of(inputArea));
        return new Distance(
                outStats[0],
                outStats[1],
                outStats[2],
                inStats[0],
                inStats[1],
                inStats[2],
                maxError,
                groups,
                underBounded,
                worstRatio);
    }

    /**
     * The area-weighted mean, 95th percentile and max of some distances.
     *
     * @param distances The distances, in parts.
     * @param areas The area each stands for, in the same parts.
     * @return Mean, percentile and max, all 0 if there are none.
     */
    static double[] stats(@NonNull List<float[]> distances, @NonNull List<float[]> areas) {
        int size = 0;
        for (float[] part : distances) {
            size += part.length;
        }
        // Distances are never negative, so their float bits sort as they do; the index rides below
        final long[] keys = new long[size];
        final float[] weight = new float[size];
        double total = 0;
        double weighted = 0;
        double max = 0;
        int k = 0;
        for (int p = 0; p < distances.size(); ++p) {
            final float[] part = distances.get(p);
            final float[] area = areas.get(p);
            for (int i = 0; i < part.length; ++i) {
                keys[k] = ((long) Float.floatToIntBits(part[i]) << Integer.SIZE) | k;
                weight[k] = area[i];
                total += area[i];
                weighted += (double) part[i] * area[i];
                max = Math.max(max, part[i]);
                ++k;
            }
        }
        if (total == 0) {
            return new double[] {0, 0, max};
        }
        Arrays.sort(keys);
        double percentile = max;
        double below = 0;
        for (long key : keys) {
            below += weight[(int) key];
            if (below >= PERCENTILE * total) {
                percentile = Float.intBitsToFloat((int) (key >>> Integer.SIZE));
                break;
            }
        }
        return new double[] {weighted / total, percentile, max};
    }

    /**
     * How far a group's output really is from the input: the larger of its out and in maxima (see
     * {@link Distance}), to compare with the error it stores.
     *
     * @param group The group.
     * @return The distance, in world units.
     */
    public double groupDistance(int group) {
        return groupDistances[group];
    }

    /**
     * The levels.
     *
     * @return Each level's numbers, from the leaves up.
     */
    public List<Level> levels() {
        return levels;
    }

    /**
     * A triangle's mark: how many of its {@link #SAMPLES} samples landed on another material
     * ({@link #MISMATCH_MASK}), and {@link #NOT_MAJORITY} if its material isn't the most common.
     *
     * @param cluster The cluster.
     * @param triangle The triangle.
     * @return The mark.
     */
    public int mark(int cluster, int triangle) {
        return marks[cluster][triangle] & (MISMATCH_MASK | NOT_MAJORITY);
    }

    /**
     * A text report: the error table, then material shares by area and from above.
     *
     * @param materialName A name for each material index.
     * @return The report.
     */
    public String report(@NonNull IntFunction<String> materialName) {
        StringBuilder out = new StringBuilder();
        out.append(
                String.format(
                        "Fidelity to the input, %d samples per triangle; mismatch, unavoidable and"
                                + " assignment over all area, then over area seen from above:%n"
                                + "%5s %9s %26s %26s%n",
                        SAMPLES,
                        "level",
                        "tris",
                        "all (mis/unavoid/assign)",
                        "above (mis/unavoid/assign)"));
        for (Level l : levels) {
            out.append(
                    String.format(
                            "%5d %9d %26s %26s%n",
                            l.level(),
                            l.triangles(),
                            String.format(
                                    "%.1f/%.1f/%.1f%%",
                                    PERCENT * l.mismatch(),
                                    PERCENT * l.unavoidable(),
                                    PERCENT * l.assignment()),
                            String.format(
                                    "%.1f/%.1f/%.1f%%",
                                    PERCENT * l.mismatchUp(),
                                    PERCENT * l.unavoidableUp(),
                                    PERCENT * l.assignmentUp())));
        }
        out.append(
                String.format(
                        "Distance to the input (out: from the level; in: from visible input), mean"
                                + "/p95/max, against the stored error:%n"
                                + "%5s %24s %24s %9s %15s %7s%n",
                        "level", "out", "in", "error", "under-bounded", "worst"));
        for (Level l : levels) {
            final Distance d = l.distance();
            out.append(
                    String.format(
                            "%5d %24s %24s %9.3f %15s %7.2f%n",
                            l.level(),
                            String.format("%.3f/%.3f/%.3f", d.outMean(), d.outP95(), d.outMax()),
                            String.format("%.3f/%.3f/%.3f", d.inMean(), d.inP95(), d.inMax()),
                            d.maxError(),
                            d.underBounded() + " of " + d.groups(),
                            d.worstRatio()));
        }
        final SortedSet<Integer> materials = new TreeSet<>();
        for (Level l : levels) {
            materials.addAll(l.shares().keySet());
        }
        shareTable(
                out,
                "Material share of area, assigned (found under samples):",
                materials,
                materialName,
                ASSIGNED);
        shareTable(
                out,
                "Material share of area seen from above, assigned (found under samples):",
                materials,
                materialName,
                ASSIGNED_UP);
        return out.toString();
    }

    /**
     * Add a table of material shares.
     *
     * @param out Where to write.
     * @param title The table's title.
     * @param materials The materials, as columns.
     * @param materialName A name for each material index.
     * @param assigned Which share is the assigned one; the mapped one is {@link #MAPPED} later.
     */
    private void shareTable(
            @NonNull StringBuilder out,
            @NonNull String title,
            @NonNull SortedSet<Integer> materials,
            @NonNull IntFunction<String> materialName,
            int assigned) {
        out.append(title).append('\n');
        out.append(String.format("%5s", "level"));
        for (int material : materials) {
            out.append(String.format(" %15s", materialName.apply(material)));
        }
        out.append('\n');
        for (Level l : levels) {
            out.append(String.format("%5d", l.level()));
            for (int material : materials) {
                final double[] share = l.shares().getOrDefault(material, new double[SHARE_VALUES]);
                out.append(
                        String.format(
                                " %6.1f (%5.1f)%%",
                                PERCENT * share[assigned], PERCENT * share[assigned + MAPPED]));
            }
            out.append('\n');
        }
    }
}
