package com.ikalagaming.graphics.lod;

import lombok.NonNull;

import java.util.Arrays;

/**
 * What each texel of a baked cluster's tile stands for: the point on one of its group's children it
 * maps back to, through the group's collapse history (see {@link Correspondence}), and the point on
 * the cluster's own triangle it lies on. Baking then samples the child's appearance at the first,
 * and turns its normal into the second's tangent space.
 *
 * <p>A texel whose center is on a triangle in the tile takes that point. One within {@value
 * BakeLayout#GUTTER} texel of a triangle, in the gap around its chart, takes the triangle's nearest
 * point instead, so filtering at a chart's edge only ever blends in the chart's own appearance.
 * Texels further from every triangle stand for nothing and stay empty.
 */
public final class BakeTexels {

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Corners in a triangle. */
    private static final int CORNERS = 3;

    /** Values in a triangle's corners. */
    private static final int TRIANGLE_VALUES = CORNERS * AXES;

    /** A texel that stands for nothing. */
    public static final int EMPTY = -1;

    /** Half a texel, the distance from its corner to its center. */
    private static final double HALF = 0.5;

    /** The tile's side, in texels. */
    private final int tile;

    /** Each texel's child cluster, by id, row by row, or {@link #EMPTY}. */
    private final int[] childClusters;

    /** Each texel's triangle in its child cluster. */
    private final int[] childTriangles;

    /** Each texel's weights on its child triangle's second and third corners, two a texel. */
    private final float[] childWeights;

    /** Each texel's triangle in the baked cluster. */
    private final int[] ownTriangles;

    /** Each texel's weights on its own triangle's second and third corners, two a texel. */
    private final float[] ownWeights;

    /**
     * Keep a mapping.
     *
     * @param tile The tile's side.
     */
    private BakeTexels(int tile) {
        this.tile = tile;
        final int texels = tile * tile;
        childClusters = new int[texels];
        Arrays.fill(childClusters, EMPTY);
        childTriangles = new int[texels];
        childWeights = new float[texels * 2];
        ownTriangles = new int[texels];
        ownWeights = new float[texels * 2];
    }

    /**
     * The tile's side.
     *
     * @return The side, in texels.
     */
    public int tile() {
        return tile;
    }

    /**
     * The child cluster a texel maps to.
     *
     * @param texel The texel, row by row.
     * @return The child's id, or {@link #EMPTY}.
     */
    public int childCluster(int texel) {
        return childClusters[texel];
    }

    /**
     * The triangle in its child cluster a texel maps to.
     *
     * @param texel The texel.
     * @return The triangle.
     */
    public int childTriangle(int texel) {
        return childTriangles[texel];
    }

    /**
     * A texel's barycentric weight on its child triangle's corner.
     *
     * @param texel The texel.
     * @param corner The corner, 0 to 2.
     * @return The weight.
     */
    public float childWeight(int texel, int corner) {
        return weight(childWeights, texel, corner);
    }

    /**
     * The baked cluster's triangle a texel lies on.
     *
     * @param texel The texel.
     * @return The triangle.
     */
    public int ownTriangle(int texel) {
        return ownTriangles[texel];
    }

    /**
     * A texel's barycentric weight on its own triangle's corner.
     *
     * @param texel The texel.
     * @param corner The corner, 0 to 2.
     * @return The weight.
     */
    public float ownWeight(int texel, int corner) {
        return weight(ownWeights, texel, corner);
    }

    /**
     * One of three weights kept as two.
     *
     * @param weights The weights, two a texel.
     * @param texel The texel.
     * @param corner The corner.
     * @return The weight.
     */
    private static float weight(float[] weights, int texel, int corner) {
        return corner == 0
                ? 1 - weights[texel * 2] - weights[texel * 2 + 1]
                : weights[texel * 2 + corner - 1];
    }

    /**
     * Where a merged triangle of a group came from.
     *
     * @see DagBuilder
     */
    @FunctionalInterface
    interface Source {
        /**
         * The child triangle a merged triangle came from.
         *
         * @param mergedFace The merged triangle.
         * @return The child cluster's id and its triangle in it.
         */
        int[] of(int mergedFace);
    }

    /**
     * Map a baked cluster's tile.
     *
     * @param corners The cluster's corners.
     * @param uvs Its corners' places in the tile, from {@link BakeLayout#uvs()}.
     * @param tile The tile's side.
     * @param faces Each of its triangles' face id in the group's simplification, which is the
     *     merged triangle it started as.
     * @param map The group's correspondence.
     * @param sources Where each merged triangle came from.
     * @param dag The DAG, for the children's corners.
     * @return The mapping.
     */
    static BakeTexels of(
            float @NonNull [] corners,
            float @NonNull [] uvs,
            int tile,
            int @NonNull [] faces,
            @NonNull Correspondence map,
            @NonNull Source sources,
            @NonNull LodDag dag) {
        final BakeTexels out = new BakeTexels(tile);
        final int triangles = corners.length / TRIANGLE_VALUES;
        // How far each texel's center is from the triangle it took, 0 inside one
        final double[] taken = new double[tile * tile];
        Arrays.fill(taken, Double.MAX_VALUE);
        final double[] at = new double[2];
        final double[] weights = new double[CORNERS];
        final double[] point = new double[AXES];
        final double[] childWeights = new double[CORNERS];
        for (int t = 0; t < triangles; ++t) {
            final double[] u = new double[CORNERS];
            final double[] v = new double[CORNERS];
            double lowU = Double.MAX_VALUE;
            double lowV = Double.MAX_VALUE;
            double highU = -Double.MAX_VALUE;
            double highV = -Double.MAX_VALUE;
            for (int k = 0; k < CORNERS; ++k) {
                u[k] = uvs[(t * CORNERS + k) * 2] * tile;
                v[k] = uvs[(t * CORNERS + k) * 2 + 1] * tile;
                lowU = Math.min(lowU, u[k]);
                lowV = Math.min(lowV, v[k]);
                highU = Math.max(highU, u[k]);
                highV = Math.max(highV, v[k]);
            }
            final int x0 = Math.max(0, (int) Math.floor(lowU - BakeLayout.GUTTER - HALF));
            final int y0 = Math.max(0, (int) Math.floor(lowV - BakeLayout.GUTTER - HALF));
            final int x1 = Math.min(tile - 1, (int) Math.ceil(highU + BakeLayout.GUTTER));
            final int y1 = Math.min(tile - 1, (int) Math.ceil(highV + BakeLayout.GUTTER));
            for (int y = y0; y <= y1; ++y) {
                for (int x = x0; x <= x1; ++x) {
                    at[0] = x + HALF;
                    at[1] = y + HALF;
                    final double distance = closestWeights(u, v, at, weights);
                    final int texel = y * tile + x;
                    if (distance > BakeLayout.GUTTER || distance >= taken[texel]) {
                        continue;
                    }
                    taken[texel] = distance;
                    for (int axis = 0; axis < AXES; ++axis) {
                        point[axis] =
                                weights[0] * corners[t * TRIANGLE_VALUES + axis]
                                        + weights[1] * corners[t * TRIANGLE_VALUES + AXES + axis]
                                        + weights[2]
                                                * corners[t * TRIANGLE_VALUES + 2 * AXES + axis];
                    }
                    final int merged = map.inputFace(point, faces[t]);
                    final int[] child = sources.of(merged);
                    final LodDag.Cluster cluster = dag.clusters().get(child[0]);
                    AttributeTransfer.weights(
                            cluster.corners(), child[1] * TRIANGLE_VALUES, point, childWeights);
                    out.childClusters[texel] = child[0];
                    out.childTriangles[texel] = child[1];
                    out.childWeights[texel * 2] = (float) childWeights[1];
                    out.childWeights[texel * 2 + 1] = (float) childWeights[2];
                    out.ownTriangles[texel] = t;
                    out.ownWeights[texel * 2] = (float) weights[1];
                    out.ownWeights[texel * 2 + 1] = (float) weights[2];
                }
            }
        }
        return out;
    }

    /**
     * The barycentric weights of the closest point of a flat triangle to a point, and how far it
     * is.
     *
     * @param u The triangle's corners' u.
     * @param v Their v.
     * @param p The point.
     * @param out Receives the weights.
     * @return The distance, 0 if the point is inside.
     */
    static double closestWeights(
            double @NonNull [] u,
            double @NonNull [] v,
            double @NonNull [] p,
            double @NonNull [] out) {
        final double[] a = {u[0], v[0], 0};
        final double[] b = {u[1], v[1], 0};
        final double[] c = {u[2], v[2], 0};
        final double[] q = {p[0], p[1], 0};
        final double[] closest = new double[AXES];
        Geometry.closestOnTriangle(q, a, b, c, closest);
        // Weights of the closest point, which is on the triangle
        final double e1u = u[1] - u[0];
        final double e1v = v[1] - v[0];
        final double e2u = u[2] - u[0];
        final double e2v = v[2] - v[0];
        final double det = e1u * e2v - e2u * e1v;
        if (det == 0) {
            out[0] = 1;
            out[1] = 0;
            out[2] = 0;
        } else {
            final double du = closest[0] - u[0];
            final double dv = closest[1] - v[0];
            out[1] = (du * e2v - e2u * dv) / det;
            out[2] = (e1u * dv - du * e1v) / det;
            out[0] = 1 - out[1] - out[2];
        }
        return Math.sqrt(Geometry.distanceSquared(q, closest));
    }
}
