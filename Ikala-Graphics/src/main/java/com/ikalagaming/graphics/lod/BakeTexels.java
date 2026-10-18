package com.ikalagaming.graphics.lod;

import lombok.NonNull;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /** The tile's width, in texels. */
    private final int width;

    /** The tile's height, in texels. */
    private final int height;

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

    /** Each splat's texel, row by row, in increasing order. */
    private int[] splatTexels = new int[0];

    /** Each splat's child cluster, by id. */
    private int[] splatClusters = new int[0];

    /** Each splat's triangle in its child cluster. */
    private int[] splatTriangles = new int[0];

    /** Each splat's weights on its child triangle's second and third corners, two a splat. */
    private float[] splatWeights = new float[0];

    /** How much of its texel each splat covers, 0 to 1 or more where splats pile up. */
    private float[] splatCoverage = new float[0];

    /**
     * Keep a mapping.
     *
     * @param width The tile's width.
     * @param height The tile's height.
     */
    private BakeTexels(int width, int height) {
        this.width = width;
        this.height = height;
        final int texels = width * height;
        childClusters = new int[texels];
        Arrays.fill(childClusters, EMPTY);
        childTriangles = new int[texels];
        childWeights = new float[texels * 2];
        ownTriangles = new int[texels];
        ownWeights = new float[texels * 2];
    }

    /**
     * How many bytes {@link #write} writes.
     *
     * @return The size.
     */
    int bytes() {
        final int texels = width * height;
        return Integer.BYTES * 3
                + texels * (Integer.BYTES * 3 + Float.BYTES * 4)
                + splatTexels.length * (Integer.BYTES * 3 + Float.BYTES * 3);
    }

    /**
     * Write the mapping whole: its size, each texel's child cluster, child triangle and own
     * triangle, their weights, then its splats.
     *
     * @param out Where it goes, little endian.
     */
    void write(@NonNull ByteBuffer out) {
        out.putInt(width).putInt(height);
        for (int value : childClusters) {
            out.putInt(value);
        }
        for (int value : childTriangles) {
            out.putInt(value);
        }
        for (int value : ownTriangles) {
            out.putInt(value);
        }
        for (float value : childWeights) {
            out.putFloat(value);
        }
        for (float value : ownWeights) {
            out.putFloat(value);
        }
        out.putInt(splatTexels.length);
        for (int s = 0; s < splatTexels.length; ++s) {
            out.putInt(splatTexels[s]).putInt(splatClusters[s]).putInt(splatTriangles[s]);
            out.putFloat(splatWeights[s * 2]).putFloat(splatWeights[s * 2 + 1]);
            out.putFloat(splatCoverage[s]);
        }
    }

    /**
     * Read a mapping {@link #write} wrote.
     *
     * @param in Where it is, little endian.
     * @return The mapping.
     * @throws IllegalArgumentException If its counts make no sense.
     */
    static BakeTexels read(@NonNull ByteBuffer in) {
        final int width = in.getInt();
        final int height = in.getInt();
        if (width <= 0 || height <= 0 || (long) width * height * Integer.BYTES > in.remaining()) {
            throw new IllegalArgumentException("A bake of " + width + " x " + height + " texels");
        }
        final BakeTexels out = new BakeTexels(width, height);
        for (int i = 0; i < out.childClusters.length; ++i) {
            out.childClusters[i] = in.getInt();
        }
        for (int i = 0; i < out.childTriangles.length; ++i) {
            out.childTriangles[i] = in.getInt();
        }
        for (int i = 0; i < out.ownTriangles.length; ++i) {
            out.ownTriangles[i] = in.getInt();
        }
        for (int i = 0; i < out.childWeights.length; ++i) {
            out.childWeights[i] = in.getFloat();
        }
        for (int i = 0; i < out.ownWeights.length; ++i) {
            out.ownWeights[i] = in.getFloat();
        }
        final int splats = in.getInt();
        if (splats < 0 || (long) splats * (Integer.BYTES * 3 + Float.BYTES * 3) > in.remaining()) {
            throw new IllegalArgumentException("A bake with " + splats + " splats");
        }
        out.splatTexels = new int[splats];
        out.splatClusters = new int[splats];
        out.splatTriangles = new int[splats];
        out.splatWeights = new float[splats * 2];
        out.splatCoverage = new float[splats];
        for (int s = 0; s < splats; ++s) {
            out.splatTexels[s] = in.getInt();
            out.splatClusters[s] = in.getInt();
            out.splatTriangles[s] = in.getInt();
            out.splatWeights[s * 2] = in.getFloat();
            out.splatWeights[s * 2 + 1] = in.getFloat();
            out.splatCoverage[s] = in.getFloat();
        }
        return out;
    }

    /**
     * The tile's width.
     *
     * @return The width, in texels.
     */
    public int width() {
        return width;
    }

    /**
     * The tile's height.
     *
     * @return The height, in texels.
     */
    public int height() {
        return height;
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
     * How many splats the tile has: input surface no texel samples, laid onto the texels it stands
     * over, see {@link #splat}.
     *
     * @return The count.
     */
    public int splatCount() {
        return splatTexels.length;
    }

    /**
     * The texel a splat lands on. Splats are in increasing order of texel.
     *
     * @param splat The splat.
     * @return The texel, row by row.
     */
    public int splatTexel(int splat) {
        return splatTexels[splat];
    }

    /**
     * The child cluster a splat samples.
     *
     * @param splat The splat.
     * @return The child's id.
     */
    public int splatCluster(int splat) {
        return splatClusters[splat];
    }

    /**
     * The triangle in its child cluster a splat samples.
     *
     * @param splat The splat.
     * @return The triangle.
     */
    public int splatTriangle(int splat) {
        return splatTriangles[splat];
    }

    /**
     * A splat's barycentric weight on its child triangle's corner.
     *
     * @param splat The splat.
     * @param corner The corner, 0 to 2.
     * @return The weight.
     */
    public float splatWeight(int splat, int corner) {
        return weight(splatWeights, splat, corner);
    }

    /**
     * How much of its texel a splat covers: the area of input it stands for, seen along the baked
     * surface, over the texel's.
     *
     * @param splat The splat.
     * @return The share, 0 to 1, or more where splats pile up.
     */
    public float splatCoverage(int splat) {
        return splatCoverage[splat];
    }

    /**
     * Lay a group's input that no texel of its tiles samples onto those tiles, as splats: parts
     * simplification took away, like a lamp on a table, and detail smaller than a texel. Each such
     * visible input triangle is sampled about a texel apart, each sample goes to the nearest output
     * triangle facing its way, and the area it stands for, seen along that triangle, becomes a
     * share of the texel it lands on. Baking blends those in over what the texel samples itself, so
     * a far group keeps the color of what it no longer has the triangles for.
     *
     * @param tiles The group's baked clusters' mappings, given their splats here.
     * @param corners Each baked cluster's corners.
     * @param layouts Each baked cluster's layout.
     * @param merged The group's merged triangles.
     * @param sources Where each merged triangle came from.
     * @param dag The DAG, for the children's corners.
     */
    static void splat(
            @NonNull List<BakeTexels> tiles,
            @NonNull List<float[]> corners,
            @NonNull List<BakeLayout> layouts,
            @NonNull LodMesh merged,
            @NonNull Source sources,
            @NonNull LodDag dag) {
        if (tiles.isEmpty()) {
            return;
        }
        // The child triangles some texel samples already
        final Set<Long> sampled = new HashSet<>();
        for (BakeTexels tile : tiles) {
            for (int texel = 0; texel < tile.childClusters.length; ++texel) {
                if (tile.childClusters[texel] != EMPTY) {
                    sampled.add(pair(tile.childClusters[texel], tile.childTriangles[texel]));
                }
            }
        }
        // Every output triangle, and which tile it is in
        int total = 0;
        double spacing = Double.MAX_VALUE;
        for (int i = 0; i < tiles.size(); ++i) {
            total += corners.get(i).length;
            spacing = Math.min(spacing, layouts.get(i).texelSize());
        }
        final float[] all = new float[total];
        final int[] tileOf = new int[total / TRIANGLE_VALUES];
        final int[] firstOf = new int[tiles.size()];
        int filled = 0;
        for (int i = 0; i < tiles.size(); ++i) {
            final float[] own = corners.get(i);
            System.arraycopy(own, 0, all, filled, own.length);
            firstOf[i] = filled / TRIANGLE_VALUES;
            Arrays.fill(tileOf, firstOf[i], firstOf[i] + own.length / TRIANGLE_VALUES, i);
            filled += own.length;
        }
        final TriangleBvh tree = new TriangleBvh(all);
        final TriangleBvh.Scratch scratch = new TriangleBvh.Scratch();
        final List<Map<Splat, double[]>> found = new ArrayList<>();
        for (int i = 0; i < tiles.size(); ++i) {
            found.add(new HashMap<>());
        }
        final double[] a = new double[AXES];
        final double[] b = new double[AXES];
        final double[] c = new double[AXES];
        final double[] point = new double[AXES];
        final double[] inputNormal = new double[AXES];
        final double[] outputNormal = new double[AXES];
        final double[] closest = new double[AXES];
        final double[] own = new double[CORNERS];
        final double[] childWeights = new double[CORNERS];
        final double[] oa = new double[AXES];
        final double[] ob = new double[AXES];
        final double[] oc = new double[AXES];
        for (int f = 0; f < merged.getTriangleCount(); ++f) {
            if (merged.visibility(f) < Fidelity.VISIBLE) {
                continue;
            }
            final int[] child = sources.of(f);
            if (sampled.contains(pair(child[0], child[1]))) {
                continue;
            }
            Geometry.corners(merged, f, a, b, c);
            Geometry.normal(a, b, c, inputNormal);
            if (!unit(inputNormal)) {
                continue;
            }
            final float[] childCorners = dag.clusters().get(child[0]).corners();
            Hausdorff.sample(
                    a,
                    b,
                    c,
                    spacing,
                    false,
                    point,
                    (p, area) -> {
                        if (area <= 0) {
                            return;
                        }
                        tree.nearest(p, Double.POSITIVE_INFINITY, scratch);
                        final int t = scratch.triangle;
                        final int tile = tileOf[t];
                        final int local = t - firstOf[tile];
                        final float[] tileCorners = corners.get(tile);
                        read(tileCorners, local, 0, oa);
                        read(tileCorners, local, 1, ob);
                        read(tileCorners, local, 2, oc);
                        Geometry.normal(oa, ob, oc, outputNormal);
                        if (!unit(outputNormal)) {
                            return;
                        }
                        final double facing = Geometry.dot(inputNormal, outputNormal);
                        if (facing <= 0) {
                            return;
                        }
                        // Where on the output triangle, and so which texel
                        Geometry.closestOnTriangle(p, oa, ob, oc, closest);
                        AttributeTransfer.weights(
                                tileCorners, local * TRIANGLE_VALUES, closest, own);
                        final BakeTexels into = tiles.get(tile);
                        final float[] uvs = layouts.get(tile).uvs();
                        double u = 0;
                        double v = 0;
                        for (int k = 0; k < CORNERS; ++k) {
                            u += own[k] * uvs[(local * CORNERS + k) * 2];
                            v += own[k] * uvs[(local * CORNERS + k) * 2 + 1];
                        }
                        final int x =
                                Math.clamp((int) Math.floor(u * into.width), 0, into.width - 1);
                        final int y =
                                Math.clamp((int) Math.floor(v * into.height), 0, into.height - 1);
                        final int texel = y * into.width + x;
                        if (into.childClusters[texel] == EMPTY) {
                            return;
                        }
                        final double texelSize = layouts.get(tile).texelSize();
                        final double coverage = area * facing / (texelSize * texelSize);
                        AttributeTransfer.weights(
                                childCorners, child[1] * TRIANGLE_VALUES, p, childWeights);
                        final double[] sum =
                                found.get(tile)
                                        .computeIfAbsent(
                                                new Splat(texel, child[0], child[1]),
                                                k -> new double[SPLAT_SUMS]);
                        sum[0] += coverage;
                        sum[1] += coverage * childWeights[1];
                        sum[2] += coverage * childWeights[2];
                    });
        }
        for (int i = 0; i < tiles.size(); ++i) {
            final List<Map.Entry<Splat, double[]>> entries =
                    new ArrayList<>(found.get(i).entrySet());
            entries.sort(
                    Comparator.comparingInt((Map.Entry<Splat, double[]> e) -> e.getKey().texel())
                            .thenComparingInt(e -> e.getKey().cluster())
                            .thenComparingInt(e -> e.getKey().triangle()));
            final BakeTexels tile = tiles.get(i);
            final int n = entries.size();
            tile.splatTexels = new int[n];
            tile.splatClusters = new int[n];
            tile.splatTriangles = new int[n];
            tile.splatWeights = new float[n * 2];
            tile.splatCoverage = new float[n];
            for (int s = 0; s < n; ++s) {
                final Splat key = entries.get(s).getKey();
                final double[] sum = entries.get(s).getValue();
                tile.splatTexels[s] = key.texel();
                tile.splatClusters[s] = key.cluster();
                tile.splatTriangles[s] = key.triangle();
                tile.splatWeights[s * 2] = (float) (sum[1] / sum[0]);
                tile.splatWeights[s * 2 + 1] = (float) (sum[2] / sum[0]);
                tile.splatCoverage[s] = (float) sum[0];
            }
        }
    }

    /** Sums kept per splat: coverage, and coverage times the second and third weights. */
    private static final int SPLAT_SUMS = 3;

    /**
     * A splat's key: the texel it lands on and the child triangle it samples.
     *
     * @param texel The texel.
     * @param cluster The child cluster.
     * @param triangle The child triangle.
     */
    private record Splat(int texel, int cluster, int triangle) {}

    /**
     * A child triangle as one key.
     *
     * @param cluster The child cluster.
     * @param triangle Its triangle.
     * @return The key.
     */
    private static long pair(int cluster, int triangle) {
        return (long) cluster << Integer.SIZE | (triangle & 0xFFFFFFFFL);
    }

    /**
     * Scale a direction to unit length.
     *
     * @param direction The direction, scaled in place.
     * @return False if it has no length.
     */
    private static boolean unit(double @NonNull [] direction) {
        final double length = Math.sqrt(Geometry.dot(direction, direction));
        if (length <= 0) {
            return false;
        }
        for (int axis = 0; axis < AXES; ++axis) {
            direction[axis] /= length;
        }
        return true;
    }

    /**
     * Read a triangle's corner.
     *
     * @param corners The corners, 9 floats a triangle.
     * @param t The triangle.
     * @param corner The corner.
     * @param out Receives the corner.
     */
    private static void read(float @NonNull [] corners, int t, int corner, double @NonNull [] out) {
        for (int axis = 0; axis < AXES; ++axis) {
            out[axis] = corners[t * TRIANGLE_VALUES + corner * AXES + axis];
        }
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
     * @param width The tile's width.
     * @param height The tile's height.
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
            int width,
            int height,
            int @NonNull [] faces,
            @NonNull Correspondence map,
            @NonNull Source sources,
            @NonNull LodDag dag) {
        final BakeTexels out = new BakeTexels(width, height);
        final int triangles = corners.length / TRIANGLE_VALUES;
        // How far each texel's center is from the triangle it took, 0 inside one
        final double[] taken = new double[width * height];
        Arrays.fill(taken, Double.MAX_VALUE);
        final double[] at = new double[2];
        final double[] weights = new double[CORNERS];
        final double[] point = new double[AXES];
        final double[] childWeights = new double[CORNERS];
        final double[][] scratch = new double[SCRATCH][AXES];
        for (int t = 0; t < triangles; ++t) {
            final double[] u = new double[CORNERS];
            final double[] v = new double[CORNERS];
            double lowU = Double.MAX_VALUE;
            double lowV = Double.MAX_VALUE;
            double highU = -Double.MAX_VALUE;
            double highV = -Double.MAX_VALUE;
            for (int k = 0; k < CORNERS; ++k) {
                u[k] = uvs[(t * CORNERS + k) * 2] * width;
                v[k] = uvs[(t * CORNERS + k) * 2 + 1] * height;
                lowU = Math.min(lowU, u[k]);
                lowV = Math.min(lowV, v[k]);
                highU = Math.max(highU, u[k]);
                highV = Math.max(highV, v[k]);
            }
            final int x0 = Math.max(0, (int) Math.floor(lowU - BakeLayout.GUTTER - HALF));
            final int y0 = Math.max(0, (int) Math.floor(lowV - BakeLayout.GUTTER - HALF));
            final int x1 = Math.min(width - 1, (int) Math.ceil(highU + BakeLayout.GUTTER));
            final int y1 = Math.min(height - 1, (int) Math.ceil(highV + BakeLayout.GUTTER));
            for (int y = y0; y <= y1; ++y) {
                for (int x = x0; x <= x1; ++x) {
                    at[0] = x + HALF;
                    at[1] = y + HALF;
                    final double distance = closestWeights(u, v, at, weights, scratch);
                    final int texel = y * width + x;
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
        return closestWeights(u, v, p, out, new double[SCRATCH][AXES]);
    }

    /** Points {@link #closestWeights} needs for scratch: the triangle's, the point, the closest. */
    static final int SCRATCH = 5;

    /**
     * The barycentric weights of the closest point of a flat triangle to a point, and how far it
     * is, with scratch kept between calls.
     *
     * @param u The triangle's corners' u.
     * @param v Their v.
     * @param p The point.
     * @param out Receives the weights.
     * @param scratch {@link #SCRATCH} points of 3 doubles to use.
     * @return The distance, 0 if the point is inside.
     */
    static double closestWeights(
            double @NonNull [] u,
            double @NonNull [] v,
            double @NonNull [] p,
            double @NonNull [] out,
            double @NonNull [][] scratch) {
        final double[] a = flat(scratch[0], u[0], v[0]);
        final double[] b = flat(scratch[1], u[1], v[1]);
        final double[] c = flat(scratch[2], u[2], v[2]);
        final double[] q = flat(scratch[3], p[0], p[1]);
        final double[] closest = scratch[4];
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

    /**
     * Set a point on the plane z = 0.
     *
     * @param out The point.
     * @param x Its x.
     * @param y Its y.
     * @return The point.
     */
    private static double[] flat(double @NonNull [] out, double x, double y) {
        out[0] = x;
        out[1] = y;
        out[2] = 0;
        return out;
    }
}
