package com.ikalagaming.graphics.lod;

import lombok.NonNull;

import java.util.*;
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
 *   <li><b>Distance:</b> how far points moved to land on the input, against the error the cut uses.
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

    /**
     * One level's numbers.
     *
     * @param level The level.
     * @param triangles Its triangles.
     * @param mismatch The share of area whose input material isn't the assigned one.
     * @param unavoidable The share of area whose input material isn't the triangle's most common.
     * @param mismatchUp The same as mismatch, over area seen from above.
     * @param unavoidableUp The same as unavoidable, over area seen from above.
     * @param meanDistance How far points moved to land on the input, averaged over area.
     * @param maxDistance The farthest any point moved.
     * @param maxError The largest error the level's clusters carry, for comparison.
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
            double meanDistance,
            double maxDistance,
            double maxError,
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
     * What sampling one cluster found.
     *
     * @param marks Each triangle's mismatching samples, with {@link #NOT_MAJORITY}.
     * @param area Its area.
     * @param mismatchArea Area whose input material isn't the assigned one.
     * @param unavoidableArea Area whose input material isn't the most common one.
     * @param upArea Its area seen from above.
     * @param mismatchUp Mismatching area seen from above.
     * @param unavoidableUp Unavoidably mismatching area seen from above.
     * @param distanceArea Distance times area, summed.
     * @param maxDistance The farthest distance.
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
            double distanceArea,
            double maxDistance,
            Map<Integer, double[]> materials) {}

    /** The levels, from the leaves up. */
    private final List<Level> levels;

    /** Each cluster's triangle marks, by cluster id. */
    private final byte[][] marks;

    /**
     * Measure a DAG.
     *
     * @param levels The levels.
     * @param marks Each cluster's triangle marks.
     */
    private Fidelity(@NonNull List<Level> levels, byte @NonNull [][] marks) {
        this.levels = levels;
        this.marks = marks;
    }

    /**
     * Sample every triangle of a DAG and follow the samples to the input. Runs on the common pool.
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
        final double[][] samples = samplePoints();
        final Sampled[] sampled =
                IntStream.range(0, dag.clusters().size())
                        .parallel()
                        .mapToObj(id -> sample(dag, lineage, dag.clusters().get(id), samples))
                        .toArray(Sampled[]::new);
        final byte[][] marks = new byte[sampled.length][];
        for (int i = 0; i < sampled.length; ++i) {
            marks[i] = sampled[i].marks();
        }
        List<Level> levels = new ArrayList<>();
        for (int level = 0; level < dag.getLevels(); ++level) {
            levels.add(level(dag, sampled, level));
        }
        return new Fidelity(Collections.unmodifiableList(levels), marks);
    }

    /**
     * Barycentric sample points, the centers of a triangle cut into {@link #SUBDIVISIONS} rows of
     * equal smaller triangles, so each stands for the same area.
     *
     * @return The points, as weights of the second and third corners.
     */
    private static double[][] samplePoints() {
        final double[][] points = new double[SAMPLES][];
        int k = 0;
        for (int i = 0; i < SUBDIVISIONS; ++i) {
            for (int j = 0; i + j < SUBDIVISIONS; ++j) {
                // The upright small triangle at (i, j)
                points[k++] = new double[] {(i + THIRD) / SUBDIVISIONS, (j + THIRD) / SUBDIVISIONS};
                if (i + j < SUBDIVISIONS - 1) {
                    // The upside down one beside it
                    points[k++] =
                            new double[] {
                                (i + TWO_THIRDS) / SUBDIVISIONS, (j + TWO_THIRDS) / SUBDIVISIONS
                            };
                }
            }
        }
        return points;
    }

    /**
     * Sample one cluster.
     *
     * @param dag The DAG.
     * @param lineage Its lineage.
     * @param cluster The cluster.
     * @param samples The sample points.
     * @return What it found.
     */
    private static Sampled sample(
            @NonNull LodDag dag,
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
        final double[] start = new double[AXES];
        final Map<Integer, Integer> counts = new HashMap<>();
        double area = 0;
        double mismatchArea = 0;
        double unavoidableArea = 0;
        double upTotal = 0;
        double mismatchUp = 0;
        double unavoidableUp = 0;
        double distanceArea = 0;
        double maxDistance = 0;
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
                    start[axis] = a[axis] + s[0] * (b[axis] - a[axis]) + s[1] * (c[axis] - a[axis]);
                }
                System.arraycopy(start, 0, point, 0, AXES);
                final int found = lineage.toLeaf(cluster.id(), t, point).material();
                counts.merge(found, 1, Integer::sum);
                if (found != assigned) {
                    ++mismatched;
                }
                final double[] under =
                        materials.computeIfAbsent(found, k -> new double[SHARE_VALUES]);
                under[MAPPED] += sampleArea;
                under[MAPPED_UP] += sampleUp;
                final double d = Math.sqrt(Geometry.distanceSquared(start, point));
                distanceArea += d * sampleArea;
                maxDistance = Math.max(maxDistance, d);
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
                distanceArea,
                maxDistance,
                materials);
    }

    /**
     * Add up one level's clusters.
     *
     * @param dag The DAG.
     * @param sampled What each cluster's sampling found.
     * @param level The level.
     * @return The level's numbers.
     */
    private static Level level(@NonNull LodDag dag, Sampled @NonNull [] sampled, int level) {
        int triangles = 0;
        double area = 0;
        double mismatch = 0;
        double unavoidable = 0;
        double up = 0;
        double mismatchUp = 0;
        double unavoidableUp = 0;
        double distance = 0;
        double maxDistance = 0;
        double maxError = 0;
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
            distance += s.distanceArea();
            maxDistance = Math.max(maxDistance, s.maxDistance());
            maxError = Math.max(maxError, cluster.error());
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
                area == 0 ? 0 : distance / area,
                maxDistance,
                maxError,
                totals);
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
                                + "%5s %9s %26s %26s %10s %9s %9s%n",
                        SAMPLES,
                        "level",
                        "tris",
                        "all (mis/unavoid/assign)",
                        "above (mis/unavoid/assign)",
                        "mean dist",
                        "max dist",
                        "error"));
        for (Level l : levels) {
            out.append(
                    String.format(
                            "%5d %9d %26s %26s %10.3f %9.3f %9.3f%n",
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
                                    PERCENT * l.assignmentUp()),
                            l.meanDistance(),
                            l.maxDistance(),
                            l.maxError()));
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
