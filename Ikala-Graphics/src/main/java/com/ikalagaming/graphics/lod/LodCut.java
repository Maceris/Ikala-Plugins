package com.ikalagaming.graphics.lod;

import lombok.NonNull;

/**
 * Chooses which clusters of a DAG to draw from a viewpoint, by Nanite's parallel rule (Karis et
 * al., <i>Nanite: A Deep Dive</i>, 2021): a cluster is drawn when its own error, projected to the
 * screen, is at or under the threshold, and its parent group's is over it. Each cluster decides on
 * its own, with no walking of the DAG, which is what lets the GPU do it in one pass later.
 *
 * <p>Errors are projected from the nearest point of each saturated sphere (Ponchio, 2008, §3.6.1),
 * so a parent's projected error is never under a child's and every path from a leaf to a root
 * switches exactly once.
 */
public final class LodCut {

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** The nearest a sphere is treated as being, so errors stay finite. */
    private static final double MIN_DISTANCE = 1e-6;

    /**
     * Where the cut is seen from.
     *
     * @param x The eye's x, in the world.
     * @param y The eye's y.
     * @param z The eye's z.
     * @param pixelsPerUnit Screen pixels per world unit at distance 1: the viewport height divided
     *     by {@code 2 tan(fov / 2)}.
     */
    public record View(double x, double y, double z, double pixelsPerUnit) {

        /**
         * A view from a vertical field of view and screen height.
         *
         * @param x The eye's x.
         * @param y The eye's y.
         * @param z The eye's z.
         * @param fovY The vertical field of view, in radians.
         * @param heightPixels The viewport height.
         * @return The view.
         */
        public static View of(double x, double y, double z, double fovY, int heightPixels) {
            return new View(x, y, z, heightPixels / (2 * Math.tan(fovY / 2)));
        }
    }

    /**
     * Choose the clusters to draw.
     *
     * @param dag The DAG.
     * @param view The viewpoint.
     * @param thresholdPixels The most error, in pixels, a drawn cluster may have.
     * @return Whether each cluster is drawn.
     */
    public static boolean[] select(
            @NonNull LodDag dag, @NonNull View view, double thresholdPixels) {
        final int n = dag.clusters().size();
        final boolean[] drawn = new boolean[n];
        final double[] eye = {
            view.x() - dag.getOrigin()[0],
            view.y() - dag.getOrigin()[1],
            view.z() - dag.getOrigin()[2]
        };
        for (int i = 0; i < n; ++i) {
            final LodDag.Cluster cluster = dag.clusters().get(i);
            final double own =
                    projected(cluster.error(), cluster.bound(), eye, view.pixelsPerUnit());
            final double parent =
                    projected(dag.parentError(i), dag.parentBound(i), eye, view.pixelsPerUnit());
            drawn[i] = own <= thresholdPixels && parent > thresholdPixels;
        }
        return drawn;
    }

    /**
     * An error projected to the screen from the nearest point of a sphere.
     *
     * @param error The error, in world units; infinity stays infinity.
     * @param sphere The sphere, x, y, z, radius, relative to the DAG's origin.
     * @param eye The eye, relative to the DAG's origin.
     * @param pixelsPerUnit Pixels per world unit at distance 1.
     * @return The error in pixels.
     */
    public static double projected(
            double error, double @NonNull [] sphere, double @NonNull [] eye, double pixelsPerUnit) {
        if (Double.isInfinite(error)) {
            return error;
        }
        if (error == 0) {
            return 0;
        }
        final double dx = sphere[0] - eye[0];
        final double dy = sphere[1] - eye[1];
        final double dz = sphere[2] - eye[2];
        final double distance =
                Math.max(MIN_DISTANCE, Math.sqrt(dx * dx + dy * dy + dz * dz) - sphere[AXES]);
        return error * pixelsPerUnit / distance;
    }

    /**
     * How many triangles a cut draws.
     *
     * @param dag The DAG.
     * @param drawn Whether each cluster is drawn.
     * @return The triangle count.
     */
    public static int triangles(@NonNull LodDag dag, boolean @NonNull [] drawn) {
        int count = 0;
        for (int i = 0; i < drawn.length; ++i) {
            if (drawn[i]) {
                count += dag.clusters().get(i).triangleCount();
            }
        }
        return count;
    }

    /** Static helpers only. */
    private LodCut() {}
}
