package com.ikalagaming.graphics.lod;

import lombok.Getter;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * @param visibility How visible each triangle is from outside, 0 to 1.
     * @param attributes Each textured triangle's corners' normal, tangent and texture coordinates,
     *     {@link Attributes#PER_TRIANGLE} ints a triangle, zeros for projected triangles; or {@link
     *     Attributes#NONE} if every triangle is projected.
     * @param error The saturated error of the group that made it, 0 for leaves, in world units: a
     *     bound on how far it is from the input.
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
            float @NonNull [] visibility,
            int @NonNull [] attributes,
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
     * @param level The highest level of its children; its clusters are one above it.
     * @param children The clusters it was made from.
     * @param outputs The clusters it made.
     * @param error Its saturated error.
     * @param bound Its saturated sphere.
     * @param simplifyError How far its output is from its base geometry, measured both ways: the
     *     clusters its build started from under it, the leaves or a parent cell's inputs. Its error
     *     is that plus the bases' own, never under a child's.
     * @param quadricError The simplifier's own estimate of that, a weighted mean distance, for
     *     comparison.
     * @param trianglesIn Triangles going in.
     * @param trianglesOut Triangles coming out.
     * @param borderEdges Edges on its locked outer border.
     * @param edges All its edges.
     * @param virtualEdges Virtual edges the simplifier added.
     * @param rejectedFlips Collapses rejected for flipping a triangle.
     * @param rejectedLinks Collapses rejected for breaking a border another group keeps.
     * @param buildNanos How long it took to build.
     * @param bakeTiles The size of each output's bake tile, packed by {@link #packTile}, in output
     *     order, when it baked its children's appearance rather than keeping their materials; empty
     *     if it didn't.
     */
    public record Group(
            int id,
            int level,
            int @NonNull [] children,
            int @NonNull [] outputs,
            double error,
            double @NonNull [] bound,
            double simplifyError,
            double quadricError,
            int trianglesIn,
            int trianglesOut,
            int borderEdges,
            int edges,
            int virtualEdges,
            int rejectedFlips,
            int rejectedLinks,
            long buildNanos,
            int @NonNull [] bakeTiles) {

        /**
         * How many of its outputs carry a baked texture.
         *
         * @return The count, 0 if it didn't bake.
         */
        public int bakedClusters() {
            return bakeTiles.length;
        }

        /**
         * The texels its outputs' bake tiles hold.
         *
         * @return The count.
         */
        public long bakedTexels() {
            long total = 0;
            for (int tile : bakeTiles) {
                total += (long) tileWidth(tile) * tileHeight(tile);
            }
            return total;
        }
    }

    /**
     * Pack a bake tile's size into an int.
     *
     * @param width Its width, in texels, under 2^16.
     * @param height Its height, in texels, under 2^16.
     * @return The packed size.
     */
    public static int packTile(int width, int height) {
        return width << Short.SIZE | height;
    }

    /**
     * A packed bake tile's width.
     *
     * @param tile The size, from {@link #packTile}.
     * @return The width, in texels.
     */
    public static int tileWidth(int tile) {
        return tile >>> Short.SIZE;
    }

    /**
     * A packed bake tile's height.
     *
     * @param tile The size, from {@link #packTile}.
     * @return The height, in texels.
     */
    public static int tileHeight(int tile) {
        return tile & 0xFFFF;
    }

    /**
     * Where cluster positions are measured from, in the world.
     *
     * @return The origin, x, y, z. Don't modify it.
     */
    @Getter private final double[] origin;

    /**
     * The leaves' average edge length, which sets the scale of virtual edges, visibility and
     * distance sampling.
     *
     * @return The length, in world units.
     */
    @Getter private double leafEdge = 1;

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
     * How long working out the leaves' visibility took, or 0 if it wasn't.
     *
     * @return The time, in milliseconds.
     */
    @Getter private double visibilityMillis;

    /**
     * Where every triangle came from, if the build kept it.
     *
     * @return The lineage, or null.
     */
    @Getter private Lineage lineage;

    /** What each baked cluster's tile texels stand for, by cluster id. */
    private final Map<Integer, BakeTexels> bakes = new HashMap<>();

    /** A triangle its consumer keeps as a surface. */
    public static final byte VOLUME_NONE = 0;

    /** A triangle its consumer finds finer than its resolution, see {@link MacroSurfaces}. */
    public static final byte VOLUME_GEOMETRY = 1;

    /** A cut out triangle whose texture's detail its consumer's error is past. */
    public static final byte VOLUME_CUT_OUT = 2;

    /** Whether each consumed cluster's triangles would go to a volume, by cluster id. */
    private final Map<Integer, byte[]> volumeKinds = new HashMap<>();

    /**
     * The coverage volume of each volume cluster, by cluster id; such a cluster has no triangles.
     */
    private final Map<Integer, CoverageVolume> volumes = new HashMap<>();

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
     * Keep where every triangle came from, filled in as clusters and groups are added.
     *
     * @param lineage The empty lineage.
     */
    void setLineage(@NonNull Lineage lineage) {
        this.lineage = lineage;
    }

    /**
     * Keep what a baked cluster's tile texels stand for.
     *
     * @param cluster The cluster's id.
     * @param texels The mapping.
     */
    void setBake(int cluster, @NonNull BakeTexels texels) {
        bakes.put(cluster, texels);
    }

    /**
     * Keep whether a consumed cluster's triangles would go to a volume in its consumer.
     *
     * @param cluster The cluster's id.
     * @param kinds Each triangle's {@link #VOLUME_NONE}, {@link #VOLUME_GEOMETRY} or {@link
     *     #VOLUME_CUT_OUT}.
     */
    void setVolumeKinds(int cluster, byte @NonNull [] kinds) {
        volumeKinds.put(cluster, kinds);
    }

    /**
     * Keep a volume cluster's coverage volume.
     *
     * @param cluster The cluster's id.
     * @param volume The volume.
     */
    void setVolume(int cluster, @NonNull CoverageVolume volume) {
        volumes.put(cluster, volume);
    }

    /**
     * The coverage volume a cluster stands for its part of the input with, instead of triangles.
     *
     * @param cluster The cluster's id.
     * @return The volume, or null if the cluster isn't a volume.
     */
    public CoverageVolume volumeOf(int cluster) {
        return volumes.get(cluster);
    }

    /**
     * Whether a cluster's triangles would go to a volume in the group that consumes it.
     *
     * @param cluster The cluster's id.
     * @return Each triangle's kind, or null if it wasn't consumed or not worked out.
     */
    public byte[] volumeKindsOf(int cluster) {
        return volumeKinds.get(cluster);
    }

    /**
     * Every baked cluster's tile mapping, by cluster id, for {@link LodCellFile}.
     *
     * @return A read-only view.
     */
    Map<Integer, BakeTexels> bakes() {
        return Collections.unmodifiableMap(bakes);
    }

    /**
     * Every consumed cluster's volume kinds, by cluster id, for {@link LodCellFile}.
     *
     * @return A read-only view.
     */
    Map<Integer, byte[]> volumeKinds() {
        return Collections.unmodifiableMap(volumeKinds);
    }

    /**
     * What a baked cluster's tile texels stand for, until it is baked.
     *
     * @param cluster The cluster's id.
     * @return The mapping, or null if the cluster isn't baked or the mapping wasn't kept.
     */
    public BakeTexels bakeOf(int cluster) {
        return bakes.get(cluster);
    }

    /**
     * Note how long working out visibility took.
     *
     * @param millis The time, in milliseconds.
     */
    void setVisibilityMillis(double millis) {
        visibilityMillis = millis;
    }

    /**
     * Note the leaves' average edge length.
     *
     * @param length The length, in world units.
     */
    void setLeafEdge(double length) {
        leafEdge = length;
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

    /**
     * Roughly how many bytes its clusters' and groups' arrays hold, for memory reports.
     *
     * @return The bytes.
     */
    public long heldBytes() {
        long bytes = 0;
        for (Cluster cluster : clusters) {
            bytes +=
                    ((long) cluster.corners().length
                                            + cluster.materials().length
                                            + cluster.visibility().length)
                                    * Integer.BYTES
                            + ((long) cluster.bound().length + cluster.tight().length)
                                    * Double.BYTES;
        }
        for (Group group : groups) {
            bytes +=
                    ((long) group.children().length + group.outputs().length) * Integer.BYTES
                            + (long) group.bound().length * Double.BYTES;
        }
        return bytes;
    }
}
