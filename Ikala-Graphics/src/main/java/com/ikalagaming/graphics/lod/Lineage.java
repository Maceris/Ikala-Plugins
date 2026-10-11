package com.ikalagaming.graphics.lod;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Where every triangle of a DAG came from, so a point on any cluster can be followed down to the
 * leaves: through the correspondence of the group that made the cluster to a triangle of one of the
 * group's children, then through that child's group, and so on. Building only maps each level to
 * the one below it; this chains those maps, for measuring how far a level has drifted from the
 * input it stands for (see {@link Fidelity}).
 *
 * <p>Kept only when asked for, since it holds every group's collapse history.
 */
public final class Lineage {

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Values in a triangle's corners. */
    private static final int TRIANGLE_VALUES = Clusters.TRIANGLE_VALUES;

    /**
     * What one group's build left for following points down.
     *
     * @param map The correspondence from its simplified mesh to its merged children.
     * @param sourceCluster The child cluster each merged triangle came from.
     * @param sourceTriangle The triangle within that child.
     */
    record Group(
            @NonNull Correspondence map,
            int @NonNull [] sourceCluster,
            int @NonNull [] sourceTriangle) {}

    /**
     * Where a point ended up.
     *
     * @param leaf The leaf cluster.
     * @param triangle The triangle within it.
     * @param material That triangle's material.
     */
    public record Landing(int leaf, int triangle, int material) {}

    /** The DAG. */
    private final LodDag dag;

    /** Each group's lineage, by group id. */
    private final List<Group> groups = new ArrayList<>();

    /**
     * Each cluster's triangles as faces of its group's merged mesh, by cluster id; null for leaves.
     */
    private final List<int[]> faces = new ArrayList<>();

    /**
     * Start an empty lineage.
     *
     * @param dag The DAG it follows.
     */
    Lineage(@NonNull LodDag dag) {
        this.dag = dag;
    }

    /**
     * Note a group's lineage; groups must be added in id order.
     *
     * @param group The group.
     */
    void addGroup(@NonNull Group group) {
        groups.add(group);
    }

    /**
     * Note which merged faces a cluster's triangles are; clusters must be added in id order.
     *
     * @param mergedFaces The faces, or null for a leaf.
     */
    void addCluster(int[] mergedFaces) {
        faces.add(mergedFaces);
    }

    /**
     * Follow a point on a cluster's triangle down to a leaf triangle.
     *
     * @param cluster The cluster.
     * @param triangle The triangle within it.
     * @param point The point, on that triangle, relative to the DAG's origin. It is moved to where
     *     it lands on the leaf.
     * @return Where it landed.
     */
    public Landing toLeaf(int cluster, int triangle, double @NonNull [] point) {
        int c = cluster;
        int t = triangle;
        LodDag.Cluster current = dag.clusters().get(c);
        while (current.level() > 0) {
            final Group group = groups.get(current.producer());
            final int merged = group.map().inputFace(point, faces.get(c)[t]);
            c = group.sourceCluster()[merged];
            t = group.sourceTriangle()[merged];
            current = dag.clusters().get(c);
        }
        return new Landing(c, t, current.materials()[t]);
    }

    /**
     * Copy a cluster triangle's corners out.
     *
     * @param cluster The cluster.
     * @param triangle The triangle.
     * @param a Its first corner.
     * @param b Its second corner.
     * @param c Its third corner.
     */
    static void corners(
            LodDag.@NonNull Cluster cluster,
            int triangle,
            double @NonNull [] a,
            double @NonNull [] b,
            double @NonNull [] c) {
        final int base = triangle * TRIANGLE_VALUES;
        for (int axis = 0; axis < AXES; ++axis) {
            a[axis] = cluster.corners()[base + axis];
            b[axis] = cluster.corners()[base + AXES + axis];
            c[axis] = cluster.corners()[base + 2 * AXES + axis];
        }
    }
}
