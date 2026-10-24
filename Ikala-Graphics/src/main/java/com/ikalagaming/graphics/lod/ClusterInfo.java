package com.ikalagaming.graphics.lod;

import lombok.NonNull;

import java.util.Arrays;

/**
 * What culling and streaming know of a cluster without its triangles: where it is, how coarse, what
 * made it and which materials it uses. Its triangles, the body, can be kept elsewhere and loaded
 * when its mesh is made.
 *
 * @param id Its id in its DAG.
 * @param level Its level in its DAG.
 * @param producer The group that made it, or -1 for leaves.
 * @param section The section a leaf came from, or -1.
 * @param error Its saturated error, see {@link LodDag.Cluster}.
 * @param bound The saturated sphere the error is projected from: x, y, z, radius.
 * @param tight A sphere around its own triangles, for culling.
 * @param triangleCount How many triangles it has.
 * @param materials The materials its triangles use, each once, in ascending order.
 */
public record ClusterInfo(
        int id,
        int level,
        int producer,
        int section,
        double error,
        double @NonNull [] bound,
        double @NonNull [] tight,
        int triangleCount,
        int @NonNull [] materials) {

    /**
     * What a cluster is, without its triangles.
     *
     * @param cluster The cluster.
     * @return Its info, sharing its spheres.
     */
    public static ClusterInfo of(LodDag.@NonNull Cluster cluster) {
        final int[] materials = cluster.materials().clone();
        Arrays.sort(materials);
        int distinct = 0;
        for (int i = 0; i < materials.length; ++i) {
            if (i == 0 || materials[i] != materials[i - 1]) {
                materials[distinct++] = materials[i];
            }
        }
        return new ClusterInfo(
                cluster.id(),
                cluster.level(),
                cluster.producer(),
                cluster.section(),
                cluster.error(),
                cluster.bound(),
                cluster.tight(),
                cluster.triangleCount(),
                Arrays.copyOf(materials, distinct));
    }
}
