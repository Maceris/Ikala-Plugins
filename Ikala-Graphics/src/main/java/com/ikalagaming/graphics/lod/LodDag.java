package com.ikalagaming.graphics.lod;

import lombok.Getter;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A level of detail DAG, as Nanite builds it: clusters of triangles at several levels, where each
 * group of clusters at one level was merged, simplified and split into the clusters of the next.
 * Any cut through it, chosen per cluster by the parallel rule in {@link LodCut}, gives a mesh with
 * no cracks, because every group's outer border was locked while it was simplified.
 *
 * <p>Errors and bounding spheres are saturated: a group's error is at least its children's, and its
 * sphere holds theirs, and every cluster a group makes stores the group's error and sphere, so
 * every cluster in a group makes the same choice and every path from a leaf to a root changes
 * choice only once.
 */
public final class LodDag {

    /** Values in a sphere: center x, y, z and radius. */
    static final int SPHERE_VALUES = 4;

    /**
     * One cluster.
     *
     * @param id Its index.
     * @param level 0 for leaves, one more for each simplification above them.
     * @param corners Its triangles' corners, 9 floats per triangle, relative to the DAG's origin.
     * @param materials Each triangle's material.
     * @param error The saturated error of the group that made it, 0 for leaves, in world units.
     * @param bound The saturated sphere the error is projected from: x, y, z, radius.
     * @param tight A sphere around its own triangles, for culling.
     * @param producer The group that made it, or -1 for leaves.
     * @param section The section a leaf came from, or -1.
     */
    public record Cluster(
            int id,
            int level,
            float @NonNull [] corners,
            int @NonNull [] materials,
            double error,
            double @NonNull [] bound,
            double @NonNull [] tight,
            int producer,
            int section) {

        /**
         * How many triangles it has.
         *
         * @return The count.
         */
        public int triangleCount() {
            return materials.length;
        }
    }

    /**
     * One group: clusters merged, simplified and split.
     *
     * @param id Its index.
     * @param level The level of its children.
     * @param children The clusters it was made from.
     * @param outputs The clusters it made.
     * @param error Its saturated error.
     * @param bound Its saturated sphere.
     * @param simplifyError The error simplifying it added, before saturation.
     * @param trianglesIn Triangles going in.
     * @param trianglesOut Triangles coming out.
     * @param borderEdges Edges on its locked outer border.
     * @param edges All its edges.
     * @param virtualEdges Virtual edges the simplifier added.
     * @param rejectedFlips Collapses rejected for flipping a triangle.
     * @param rejectedLinks Collapses rejected by the link condition.
     * @param buildNanos How long it took to build.
     */
    public record Group(
            int id,
            int level,
            int @NonNull [] children,
            int @NonNull [] outputs,
            double error,
            double @NonNull [] bound,
            double simplifyError,
            int trianglesIn,
            int trianglesOut,
            int borderEdges,
            int edges,
            int virtualEdges,
            int rejectedFlips,
            int rejectedLinks,
            long buildNanos) {}

    /**
     * Where cluster positions are measured from, in the world.
     *
     * @return The origin, x, y, z. Don't modify it.
     */
    @Getter private final double[] origin;

    /** The clusters, by id. */
    private final List<Cluster> clusters = new ArrayList<>();

    /** The groups, by id. */
    private final List<Group> groups = new ArrayList<>();

    /** The group that consumed each cluster, -1 for roots. */
    private int[] consumer = new int[0];

    /**
     * How many levels there are.
     *
     * @return The level count.
     */
    @Getter private int levels;

    /**
     * Why building stopped, for reports.
     *
     * @return A short reason.
     */
    @Getter private String stopReason = "";

    /**
     * Start an empty DAG.
     *
     * @param origin Where positions are measured from.
     */
    LodDag(double @NonNull [] origin) {
        this.origin = origin;
    }

    /**
     * Add a cluster; its id must be the next one.
     *
     * @param cluster The cluster.
     */
    void add(@NonNull Cluster cluster) {
        if (cluster.id() != clusters.size()) {
            throw new IllegalArgumentException("Clusters must be added in id order");
        }
        clusters.add(cluster);
        levels = Math.max(levels, cluster.level() + 1);
        if (consumer.length < clusters.size()) {
            final int[] grown = new int[Math.max(clusters.size(), consumer.length * 2)];
            java.util.Arrays.fill(grown, -1);
            System.arraycopy(consumer, 0, grown, 0, consumer.length);
            consumer = grown;
        }
    }

    /**
     * Add a group, marking its children as consumed by it.
     *
     * @param group The group.
     */
    void add(@NonNull Group group) {
        if (group.id() != groups.size()) {
            throw new IllegalArgumentException("Groups must be added in id order");
        }
        groups.add(group);
        for (int child : group.children()) {
            consumer[child] = group.id();
        }
    }

    /**
     * Note why building stopped.
     *
     * @param reason The reason.
     */
    void setStopReason(@NonNull String reason) {
        stopReason = reason;
    }

    /**
     * The clusters.
     *
     * @return Every cluster, by id.
     */
    public List<Cluster> clusters() {
        return Collections.unmodifiableList(clusters);
    }

    /**
     * The groups.
     *
     * @return Every group, by id.
     */
    public List<Group> groups() {
        return Collections.unmodifiableList(groups);
    }

    /**
     * The group that consumed a cluster.
     *
     * @param cluster The cluster.
     * @return The group, or -1 if it is a root.
     */
    public int consumer(int cluster) {
        return consumer[cluster];
    }

    /**
     * The error of a cluster's parent group, which decides when the cluster gives way to it.
     *
     * @param cluster The cluster.
     * @return The parent's error, or infinity for a root.
     */
    public double parentError(int cluster) {
        final int group = consumer[cluster];
        return group < 0 ? Double.POSITIVE_INFINITY : groups.get(group).error();
    }

    /**
     * The sphere a cluster's parent error is projected from.
     *
     * @param cluster The cluster.
     * @return The parent group's sphere, or the cluster's own for a root.
     */
    public double[] parentBound(int cluster) {
        final int group = consumer[cluster];
        return group < 0 ? clusters.get(cluster).bound() : groups.get(group).bound();
    }

    /**
     * How many triangles are at a level.
     *
     * @param level The level.
     * @return The triangle count.
     */
    public int trianglesAt(int level) {
        int count = 0;
        for (Cluster cluster : clusters) {
            if (cluster.level() == level) {
                count += cluster.triangleCount();
            }
        }
        return count;
    }
}
