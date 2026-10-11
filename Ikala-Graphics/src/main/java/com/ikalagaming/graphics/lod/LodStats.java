package com.ikalagaming.graphics.lod;

import lombok.NonNull;

import java.util.*;

/**
 * Measurements of a built DAG, for comparing groupings and checking the gate: per level, how well
 * it halves, how even the groups are, how much of their borders is locked and how long they took;
 * and the edit footprint, what an edit would have to rebuild.
 */
public final class LodStats {

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Nanoseconds in a millisecond. */
    private static final double NANOS_PER_MILLI = 1e6;

    /** The 95th percentile. */
    private static final double P95 = 0.95;

    /**
     * One level's numbers.
     *
     * @param level The level.
     * @param triangles Triangles at this level.
     * @param clusters Clusters at this level.
     * @param groups Groups made from this level's clusters.
     * @param halving The next level's triangles over this level's, ideally 0.5.
     * @param maxError The largest saturated error of this level's clusters.
     * @param groupClusters Clusters per group: min, median, max.
     * @param groupTriangles Triangles per group: min, median, max.
     * @param lockedShare Edges between two locked vertices, over all edges, in this level's groups.
     * @param buildMillis Group build times: total, median, 95th percentile, max.
     * @param virtualEdges Virtual edges added in this level's groups.
     * @param rejectedFlips Collapses rejected for flipping in this level's groups.
     * @param rejectedLinks Collapses rejected by the link condition in this level's groups.
     */
    public record Level(
            int level,
            int triangles,
            int clusters,
            int groups,
            double halving,
            double maxError,
            int @NonNull [] groupClusters,
            int @NonNull [] groupTriangles,
            double lockedShare,
            double @NonNull [] buildMillis,
            int virtualEdges,
            int rejectedFlips,
            int rejectedLinks) {}

    /**
     * What an edit would rebuild.
     *
     * @param edit What the edit was.
     * @param groupsPerLevel Dirty groups at each level.
     * @param triangles Triangles going into the dirty groups.
     * @param totalMillis The dirty groups' build times added up: the work on one thread.
     * @param pathMillis The slowest dirty group of each level added up: the least time with enough
     *     threads, since a level needs the one below.
     */
    public record Footprint(
            @NonNull String edit,
            int @NonNull [] groupsPerLevel,
            long triangles,
            double totalMillis,
            double pathMillis) {

        /**
         * Dirty groups in all.
         *
         * @return The count.
         */
        public int groups() {
            int total = 0;
            for (int g : groupsPerLevel) {
                total += g;
            }
            return total;
        }
    }

    /**
     * Measure every level.
     *
     * @param dag The DAG.
     * @return The levels, from the leaves up.
     */
    public static List<Level> levels(@NonNull LodDag dag) {
        List<Level> out = new ArrayList<>();
        for (int level = 0; level < dag.getLevels(); ++level) {
            int triangles = 0;
            int clusters = 0;
            double maxError = 0;
            for (LodDag.Cluster cluster : dag.clusters()) {
                if (cluster.level() == level) {
                    triangles += cluster.triangleCount();
                    ++clusters;
                    maxError = Math.max(maxError, cluster.error());
                }
            }
            final int next = dag.trianglesAt(level + 1);
            List<Integer> sizes = new ArrayList<>();
            List<Integer> groupTriangles = new ArrayList<>();
            List<Double> times = new ArrayList<>();
            long locked = 0;
            long edges = 0;
            int virtual = 0;
            int flips = 0;
            int links = 0;
            for (LodDag.Group group : dag.groups()) {
                if (group.level() != level) {
                    continue;
                }
                sizes.add(group.children().length);
                groupTriangles.add(group.trianglesIn());
                times.add(group.buildNanos() / NANOS_PER_MILLI);
                locked += group.borderEdges();
                edges += group.edges();
                virtual += group.virtualEdges();
                flips += group.rejectedFlips();
                links += group.rejectedLinks();
            }
            out.add(
                    new Level(
                            level,
                            triangles,
                            clusters,
                            sizes.size(),
                            sizes.isEmpty() || triangles == 0 ? Double.NaN : (double) next / triangles,
                            maxError,
                            spread(sizes),
                            spread(groupTriangles),
                            edges == 0 ? 0 : (double) locked / edges,
                            times(times),
                            virtual,
                            flips,
                            links));
        }
        return out;
    }

    /**
     * What rebuilding some dirty leaves would take: every group with a dirty child is dirty, and
     * so are the clusters it made, up to the roots.
     *
     * @param dag The DAG.
     * @param edit What the edit was, for the report.
     * @param dirtyLeaves Whether each leaf cluster is dirty, by cluster id.
     * @return The footprint.
     */
    public static Footprint footprint(
            @NonNull LodDag dag, @NonNull String edit, boolean @NonNull [] dirtyLeaves) {
        final boolean[] dirty = Arrays.copyOf(dirtyLeaves, dag.clusters().size());
        final int[] perLevel = new int[Math.max(1, dag.getLevels())];
        final double[] slowest = new double[perLevel.length];
        long triangles = 0;
        double total = 0;
        // Groups are numbered level by level, so one pass in order reaches every ancestor
        for (LodDag.Group group : dag.groups()) {
            boolean any = false;
            for (int child : group.children()) {
                any |= dirty[child];
            }
            if (!any) {
                continue;
            }
            for (int output : group.outputs()) {
                dirty[output] = true;
            }
            ++perLevel[group.level()];
            triangles += group.trianglesIn();
            final double millis = group.buildNanos() / NANOS_PER_MILLI;
            total += millis;
            slowest[group.level()] = Math.max(slowest[group.level()], millis);
        }
        double path = 0;
        for (double s : slowest) {
            path += s;
        }
        return new Footprint(edit, perLevel, triangles, total, path);
    }

    /**
     * The dirty leaves of a one-block edit: every leaf of the block's section, since the section
     * is rebaked.
     *
     * @param dag The DAG.
     * @param section The section.
     * @return Whether each cluster is a dirty leaf.
     */
    public static boolean[] sectionLeaves(@NonNull LodDag dag, int section) {
        final boolean[] dirty = new boolean[dag.clusters().size()];
        for (LodDag.Cluster cluster : dag.clusters()) {
            dirty[cluster.id()] = cluster.level() == 0 && cluster.section() == section;
        }
        return dirty;
    }

    /**
     * The dirty leaves of an edit inside a sphere, like an explosion: every leaf of each section the
     * sphere touches. With a single input mesh, only the leaves the sphere touches.
     *
     * @param dag The DAG.
     * @param center The sphere's center, relative to the DAG's origin.
     * @param radius The sphere's radius.
     * @param wholeSections Whether a touched section's every leaf is dirty.
     * @return Whether each cluster is a dirty leaf.
     */
    public static boolean[] sphereLeaves(
            @NonNull LodDag dag, double @NonNull [] center, double radius, boolean wholeSections) {
        final boolean[] dirty = new boolean[dag.clusters().size()];
        Set<Integer> sections = new HashSet<>();
        for (LodDag.Cluster cluster : dag.clusters()) {
            if (cluster.level() != 0) {
                continue;
            }
            final double[] s = cluster.tight();
            double d = 0;
            for (int axis = 0; axis < AXES; ++axis) {
                d += (s[axis] - center[axis]) * (s[axis] - center[axis]);
            }
            if (Math.sqrt(d) <= radius + s[AXES]) {
                dirty[cluster.id()] = true;
                sections.add(cluster.section());
            }
        }
        if (wholeSections) {
            for (LodDag.Cluster cluster : dag.clusters()) {
                if (cluster.level() == 0 && sections.contains(cluster.section())) {
                    dirty[cluster.id()] = true;
                }
            }
        }
        return dirty;
    }

    /**
     * A text table of the levels and footprints.
     *
     * @param name What was built.
     * @param dag The DAG.
     * @param footprints Edit footprints to include.
     * @return The report.
     */
    public static String report(
            @NonNull String name, @NonNull LodDag dag, @NonNull List<Footprint> footprints) {
        StringBuilder out = new StringBuilder();
        out.append(name).append(": ").append(dag.getStopReason()).append('\n');
        out.append(
                String.format(
                        "%5s %9s %8s %6s %7s %9s %14s %16s %7s %26s %8s %6s %6s%n",
                        "level",
                        "tris",
                        "clusters",
                        "groups",
                        "halving",
                        "error",
                        "group clusters",
                        "group tris",
                        "locked",
                        "build ms (sum/p50/p95/max)",
                        "virtual",
                        "flips",
                        "links"));
        for (Level l : levels(dag)) {
            out.append(
                    String.format(
                            "%5d %9d %8d %6d %7.3f %9.4f %14s %16s %6.1f%% %26s %8d %6d %6d%n",
                            l.level(),
                            l.triangles(),
                            l.clusters(),
                            l.groups(),
                            l.halving(),
                            l.maxError(),
                            l.groupClusters()[0] + "/" + l.groupClusters()[1] + "/" + l.groupClusters()[2],
                            l.groupTriangles()[0]
                                    + "/"
                                    + l.groupTriangles()[1]
                                    + "/"
                                    + l.groupTriangles()[2],
                            100 * l.lockedShare(),
                            String.format(
                                    "%.0f/%.1f/%.1f/%.1f",
                                    l.buildMillis()[0],
                                    l.buildMillis()[1],
                                    l.buildMillis()[2],
                                    l.buildMillis()[3]),
                            l.virtualEdges(),
                            l.rejectedFlips(),
                            l.rejectedLinks()));
        }
        for (Footprint f : footprints) {
            out.append(
                    String.format(
                            "edit %-24s %4d groups %s, %,d triangles, %.0f ms of work, %.0f ms with"
                                    + " enough threads%n",
                            f.edit(),
                            f.groups(),
                            Arrays.toString(f.groupsPerLevel()),
                            f.triangles(),
                            f.totalMillis(),
                            f.pathMillis()));
        }
        return out.toString();
    }

    /**
     * Min, median and max of some counts.
     *
     * @param values The counts.
     * @return The three, or zeros if empty.
     */
    private static int[] spread(@NonNull List<Integer> values) {
        if (values.isEmpty()) {
            return new int[3];
        }
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return new int[] {sorted.get(0), sorted.get(sorted.size() / 2), sorted.get(sorted.size() - 1)};
    }

    /**
     * Total, median, 95th percentile and max of some times.
     *
     * @param values The times.
     * @return The four, or zeros if empty.
     */
    private static double[] times(@NonNull List<Double> values) {
        if (values.isEmpty()) {
            return new double[4];
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        double total = 0;
        for (double v : sorted) {
            total += v;
        }
        return new double[] {
            total,
            sorted.get(sorted.size() / 2),
            sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(P95 * sorted.size()) - 1)),
            sorted.get(sorted.size() - 1)
        };
    }

    /** Static helpers only. */
    private LodStats() {}
}
