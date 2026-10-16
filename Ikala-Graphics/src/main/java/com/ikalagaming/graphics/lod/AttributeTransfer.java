package com.ikalagaming.graphics.lod;

import lombok.NonNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Gives a simplified mesh's textured triangles their corners' attributes again, from the textured
 * triangles they were simplified from: each output triangle finds the source triangle of its
 * material nearest a point a little inside it, and each corner takes that triangle's attributes at
 * its own position, extended past the triangle's edges if it lies outside. Looking from inside
 * rather than from the corner keeps a whole triangle on one side of a seam, where triangles of two
 * texture charts meet at the same corners. Seams' vertices slide along them as the group
 * simplifies, so a simplified seam no longer follows the source's exactly; an output triangle that
 * knows its chart, the one it started in, looks only among that chart's source triangles, so it
 * never takes another chart's attributes where the seam has moved. A chart can meet itself along a
 * seam, as a sphere's single chart does where its texture coordinates wrap, so each triangle also
 * anchors itself to a source triangle on its side: the one it started as, when the caller knows it,
 * else the one nearest its middle. A corner keeps what it found itself only if its texture
 * coordinates agree with the anchor's carried out to the corner; otherwise it takes the nearest
 * source triangle whose do. Inside a chart the two agree, and neighboring triangles' corners still
 * match.
 */
final class AttributeTransfer {

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Corners in a triangle. */
    private static final int CORNERS = 3;

    /** Floats in a triangle's corners. */
    private static final int TRIANGLE_VALUES = CORNERS * AXES;

    /** How far from a corner toward its triangle's middle the source is looked up from. */
    private static final double INSET = 1.0 / 16;

    /** How far past a source triangle's edges a corner's weights may reach before they are held. */
    private static final double MAX_REACH = 1;

    /**
     * How far a corner's own texture coordinates may be from the middle of its anchor's, before
     * they are taken to be across a seam: this many times as far as the anchor's texture
     * coordinates change over the distance between them. A seam jumps much further than that.
     */
    private static final double SIDE_UV_SHARE = 2;

    /** Added to that distance, in texture coordinates, for float noise. */
    private static final double SIDE_UV_MIN = 0.01;

    /**
     * The cosine of how far a corner's own normal may lean from its anchor's and still be kept when
     * it is across a seam: further is the other side of a hard edge. The anchor's normal, carried
     * out over a large simplified triangle, can itself be some way off.
     */
    private static final double HARD_EDGE_COSINE = Math.cos(Math.toRadians(60));

    /** How many triangles a source has room for at first. */
    private static final int INITIAL_TRIANGLES = 16;

    /** The source triangles of one material. */
    private static final class Source {
        /** Their corners. */
        final FloatArrayBuilder corners = new FloatArrayBuilder();

        /** Their corners' attributes. */
        final IntArrayBuilder attributes = new IntArrayBuilder();

        /** A tree over them, once all are in. */
        TriangleBvh tree;
    }

    /** An output triangle's chart when it isn't known: it looks among all its material's. */
    static final int ANY_CHART = -1;

    /** The sources, by material. */
    private final Map<Integer, Source> sources = new HashMap<>();

    /** The sources, by material and chart, packed by {@link LongIntMap#pair}. */
    private final Map<Long, Source> charts = new HashMap<>();

    /**
     * Add a textured source triangle.
     *
     * @param corners Its corners, 9 floats from {@code at}.
     * @param at Where they start.
     * @param attributes Its corners' attributes, {@link Attributes#PER_TRIANGLE} ints from {@code
     *     attributesAt}.
     * @param attributesAt Where they start.
     * @param material Its material.
     */
    void add(
            float @NonNull [] corners,
            int at,
            int @NonNull [] attributes,
            int attributesAt,
            int material) {
        add(corners, at, attributes, attributesAt, material, ANY_CHART);
    }

    /**
     * Add a textured source triangle in a chart.
     *
     * @param corners Its corners, 9 floats from {@code at}.
     * @param at Where they start.
     * @param attributes Its corners' attributes, {@link Attributes#PER_TRIANGLE} ints from {@code
     *     attributesAt}.
     * @param attributesAt Where they start.
     * @param material Its material.
     * @param chart Its chart, or {@link #ANY_CHART}.
     */
    void add(
            float @NonNull [] corners,
            int at,
            int @NonNull [] attributes,
            int attributesAt,
            int material,
            int chart) {
        final Source source = sources.computeIfAbsent(material, key -> new Source());
        source.corners.add(corners, at, TRIANGLE_VALUES);
        source.attributes.add(attributes, attributesAt, Attributes.PER_TRIANGLE);
        if (chart != ANY_CHART) {
            final Source inChart =
                    charts.computeIfAbsent(LongIntMap.pair(material, chart), key -> new Source());
            inChart.corners.add(corners, at, TRIANGLE_VALUES);
            inChart.attributes.add(attributes, attributesAt, Attributes.PER_TRIANGLE);
        }
    }

    /**
     * Whether a material had textured source triangles.
     *
     * @param material The material.
     * @return True if output triangles of it get attributes.
     */
    boolean isTextured(int material) {
        return sources.containsKey(material);
    }

    /**
     * Whether any source triangle was textured.
     *
     * @return True if there is anything to transfer.
     */
    boolean isEmpty() {
        return sources.isEmpty();
    }

    /**
     * Work out a cluster's attributes.
     *
     * @param corners Its triangles' corners, 9 floats each.
     * @param materials Its triangles' materials.
     * @return Its attributes, or {@link Attributes#NONE} if none of its triangles is textured.
     */
    int[] transfer(float @NonNull [] corners, int @NonNull [] materials) {
        final int[] anyChart = new int[materials.length];
        java.util.Arrays.fill(anyChart, ANY_CHART);
        return transfer(corners, materials, anyChart);
    }

    /**
     * Work out a cluster's attributes, each triangle looking within its chart where it has one.
     *
     * @param corners Its triangles' corners, 9 floats each.
     * @param materials Its triangles' materials.
     * @param chartOf Its triangles' charts, or {@link #ANY_CHART}.
     * @return Its attributes, or {@link Attributes#NONE} if none of its triangles is textured.
     */
    int[] transfer(float @NonNull [] corners, int @NonNull [] materials, int @NonNull [] chartOf) {
        return transfer(corners, materials, chartOf, null);
    }

    /**
     * The source triangles a cluster's triangles started as, which are on their side of any seam.
     *
     * @param corners Each one's corners, 9 floats each.
     * @param attributes Each one's corners' attributes, {@link Attributes#PER_TRIANGLE} ints each.
     * @param materials Each one's material.
     */
    record Origins(
            float @NonNull [] corners, int @NonNull [] attributes, int @NonNull [] materials) {}

    /**
     * Work out a cluster's attributes, each triangle looking within its chart where it has one, and
     * anchored to the source triangle it started as where that is given.
     *
     * @param corners Its triangles' corners, 9 floats each.
     * @param materials Its triangles' materials.
     * @param chartOf Its triangles' charts, or {@link #ANY_CHART}.
     * @param origins Where its triangles started, or null if that isn't known.
     * @return Its attributes, or {@link Attributes#NONE} if none of its triangles is textured.
     */
    int[] transfer(
            float @NonNull [] corners,
            int @NonNull [] materials,
            int @NonNull [] chartOf,
            Origins origins) {
        boolean any = false;
        for (int material : materials) {
            any |= isTextured(material);
        }
        if (!any) {
            return Attributes.NONE;
        }
        final int[] out = new int[materials.length * Attributes.PER_TRIANGLE];
        final TriangleBvh.Scratch scratch = new TriangleBvh.Scratch();
        final double[] middle = new double[AXES];
        final double[] point = new double[AXES];
        final double[] corner = new double[AXES];
        final double[] weights = new double[CORNERS];
        final int[] anchored = new int[Attributes.PER_TRIANGLE];
        final int[] own = new int[Attributes.VALUES];
        for (int t = 0; t < materials.length; ++t) {
            Source source =
                    chartOf[t] == ANY_CHART
                            ? null
                            : charts.get(LongIntMap.pair(materials[t], chartOf[t]));
            if (source == null) {
                // A material given by correspondence that its chart never had
                source = sources.get(materials[t]);
            }
            if (source == null) {
                continue;
            }
            if (source.tree == null) {
                source.tree = new TriangleBvh(source.corners.toArray());
            }
            final float[] sourceCorners = source.corners.array();
            final int[] sourceAttributes = source.attributes.array();
            final int base = t * TRIANGLE_VALUES;
            for (int axis = 0; axis < AXES; ++axis) {
                middle[axis] =
                        (corners[base + axis]
                                        + corners[base + AXES + axis]
                                        + corners[base + 2 * AXES + axis])
                                / CORNERS;
            }
            // The triangle it started as is on its side for sure; failing that, the nearest
            float[] anchorCorners = sourceCorners;
            int[] anchorAttributes = sourceAttributes;
            int anchor;
            if (origins != null
                    && origins.materials()[t] == materials[t]
                    && Attributes.textured(origins.attributes(), t)
                    && !Attributes.isAveraged(origins.attributes(), t * Attributes.PER_TRIANGLE)) {
                anchorCorners = origins.corners();
                anchorAttributes = origins.attributes();
                anchor = t;
            } else {
                source.tree.nearest(middle, Double.MAX_VALUE, scratch);
                anchor = scratch.triangle;
                if (anchor < 0) {
                    continue;
                }
            }
            for (int c = 0; c < CORNERS; ++c) {
                for (int axis = 0; axis < AXES; ++axis) {
                    corner[axis] = corners[base + c * AXES + axis];
                }
                weights(anchorCorners, anchor * TRIANGLE_VALUES, corner, weights);
                Attributes.blend(
                        anchorAttributes,
                        anchor * Attributes.PER_TRIANGLE,
                        weights,
                        anchored,
                        c * Attributes.VALUES);
            }
            final Anchor side = Anchor.of(anchorCorners, anchorAttributes, anchor);
            for (int c = 0; c < CORNERS; ++c) {
                for (int axis = 0; axis < AXES; ++axis) {
                    corner[axis] = corners[base + c * AXES + axis];
                    point[axis] = corner[axis] + (middle[axis] - corner[axis]) * INSET;
                }
                final int to = t * Attributes.PER_TRIANGLE + c * Attributes.VALUES;
                source.tree.nearest(point, Double.MAX_VALUE, scratch);
                final int s = scratch.triangle;
                if (s >= 0) {
                    weights(sourceCorners, s * TRIANGLE_VALUES, corner, weights);
                    Attributes.blend(
                            sourceAttributes, s * Attributes.PER_TRIANGLE, weights, own, 0);
                    if (side.holds(own, corner)) {
                        System.arraycopy(own, 0, out, to, Attributes.VALUES);
                        continue;
                    }
                    // Across a seam from the anchor: the nearest source on the anchor's side
                    final int at = c * Attributes.VALUES;
                    final int[] candidate = new int[Attributes.VALUES];
                    final double[] candidateWeights = new double[CORNERS];
                    source.tree.nearest(
                            point,
                            Double.MAX_VALUE,
                            0,
                            other -> {
                                weights(
                                        sourceCorners,
                                        other * TRIANGLE_VALUES,
                                        corner,
                                        candidateWeights);
                                Attributes.blend(
                                        sourceAttributes,
                                        other * Attributes.PER_TRIANGLE,
                                        candidateWeights,
                                        candidate,
                                        0);
                                return side.holds(candidate, corner);
                            },
                            scratch);
                    final int found = scratch.triangle;
                    if (found >= 0) {
                        weights(sourceCorners, found * TRIANGLE_VALUES, corner, weights);
                        Attributes.blend(
                                sourceAttributes,
                                found * Attributes.PER_TRIANGLE,
                                weights,
                                out,
                                to);
                        continue;
                    }
                    System.arraycopy(anchored, at, out, to, Attributes.VALUES);
                    if (sameSurface(own, anchored, at)) {
                        out[to] = own[0];
                    }
                    continue;
                }
                System.arraycopy(anchored, c * Attributes.VALUES, out, to, Attributes.VALUES);
            }
        }
        return out;
    }

    /**
     * The source triangle an output triangle anchors itself to, nearest its middle, which says
     * which side of any seam the triangle is on.
     *
     * @param middle Its middle.
     * @param uv Its texture coordinates there.
     * @param flipped Whether its bitangent is flipped.
     * @param density How fast its texture coordinates change, per world unit, at most.
     */
    private record Anchor(double[] middle, double[] uv, boolean flipped, double density) {

        /**
         * Describe a source triangle.
         *
         * @param corners The sources' corners.
         * @param attributes The sources' attributes.
         * @param triangle The triangle.
         * @return The anchor.
         */
        static Anchor of(float @NonNull [] corners, int @NonNull [] attributes, int triangle) {
            final double[] middle = new double[AXES];
            final double[] uv = new double[2];
            final float[][] uvs = new float[CORNERS][];
            for (int c = 0; c < CORNERS; ++c) {
                uvs[c] =
                        Attributes.textureCoordinates(
                                attributes,
                                triangle * Attributes.PER_TRIANGLE + c * Attributes.VALUES);
                for (int axis = 0; axis < AXES; ++axis) {
                    middle[axis] += corners[triangle * TRIANGLE_VALUES + c * AXES + axis] / CORNERS;
                }
                uv[0] += uvs[c][0] / CORNERS;
                uv[1] += uvs[c][1] / CORNERS;
            }
            double density = 0;
            for (int c = 0; c < CORNERS; ++c) {
                final int d = (c + 1) % CORNERS;
                double length = 0;
                for (int axis = 0; axis < AXES; ++axis) {
                    final double step =
                            corners[triangle * TRIANGLE_VALUES + d * AXES + axis]
                                    - corners[triangle * TRIANGLE_VALUES + c * AXES + axis];
                    length += step * step;
                }
                length = Math.sqrt(length);
                if (length > 0) {
                    final double change =
                            Math.max(
                                    Math.abs(uvs[d][0] - uvs[c][0]),
                                    Math.abs(uvs[d][1] - uvs[c][1]));
                    density = Math.max(density, change / length);
                }
            }
            final boolean flipped =
                    Attributes.flipped(attributes[triangle * Attributes.PER_TRIANGLE + 1]);
            return new Anchor(middle, uv, flipped, density);
        }

        /**
         * Whether attributes found for a corner are on the anchor's side of any texture seam: the
         * same way round, and texture coordinates no further from the anchor's than its own change
         * over the distance.
         *
         * @param found The attributes found, a corner's {@link Attributes#VALUES} ints from 0.
         * @param corner Where the corner is.
         * @return True if they are on the anchor's side.
         */
        boolean holds(int @NonNull [] found, double @NonNull [] corner) {
            if (Attributes.flipped(found[1]) != flipped) {
                return false;
            }
            double distance = 0;
            for (int axis = 0; axis < AXES; ++axis) {
                final double step = corner[axis] - middle[axis];
                distance += step * step;
            }
            final double reach = SIDE_UV_MIN + SIDE_UV_SHARE * density * Math.sqrt(distance);
            final float[] at = Attributes.textureCoordinates(found, 0);
            return Math.abs(at[0] - uv[0]) <= reach && Math.abs(at[1] - uv[1]) <= reach;
        }
    }

    /**
     * Whether a corner's own normal is on the same side of any hard edge as its anchor's.
     *
     * @param own The corner's own attributes.
     * @param anchored The anchor's attributes at the triangle's corners.
     * @param at Where this corner's are in them.
     * @return True if its own normal may be kept.
     */
    private static boolean sameSurface(int @NonNull [] own, int @NonNull [] anchored, int at) {
        final float[] n = new float[AXES];
        final float[] m = new float[AXES];
        Attributes.decodeDirection(own[0], n);
        Attributes.decodeDirection(anchored[at], m);
        return n[0] * m[0] + n[1] * m[1] + n[2] * m[2] >= HARD_EDGE_COSINE;
    }

    /**
     * A point's barycentric weights in a triangle's plane, held within reach of the triangle so a
     * corner far outside it doesn't take wild values.
     *
     * @param corners Triangles' corners.
     * @param at Where the triangle's start.
     * @param p The point.
     * @param out Receives the three weights, summing to 1.
     */
    static void weights(
            float @NonNull [] corners, int at, double @NonNull [] p, double @NonNull [] out) {
        final double[] e1 = new double[AXES];
        final double[] e2 = new double[AXES];
        final double[] d = new double[AXES];
        for (int axis = 0; axis < AXES; ++axis) {
            final double a = corners[at + axis];
            e1[axis] = corners[at + AXES + axis] - a;
            e2[axis] = corners[at + 2 * AXES + axis] - a;
            d[axis] = p[axis] - a;
        }
        final double d11 = dot(e1, e1);
        final double d12 = dot(e1, e2);
        final double d22 = dot(e2, e2);
        final double d1 = dot(d, e1);
        final double d2 = dot(d, e2);
        final double denominator = d11 * d22 - d12 * d12;
        if (denominator <= 0) {
            out[0] = 1;
            out[1] = 0;
            out[2] = 0;
            return;
        }
        double v = (d22 * d1 - d12 * d2) / denominator;
        double w = (d11 * d2 - d12 * d1) / denominator;
        v = Math.clamp(v, -MAX_REACH, 1 + MAX_REACH);
        w = Math.clamp(w, -MAX_REACH, 1 + MAX_REACH);
        out[0] = 1 - v - w;
        out[1] = v;
        out[2] = w;
    }

    /**
     * The dot product of two vectors.
     *
     * @param a The first.
     * @param b The second.
     * @return Their dot product.
     */
    private static double dot(double @NonNull [] a, double @NonNull [] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /** A growing float array. */
    private static final class FloatArrayBuilder {
        /** The values. */
        private float[] values = new float[TRIANGLE_VALUES * INITIAL_TRIANGLES];

        /** How many there are. */
        private int size;

        /**
         * Append some values.
         *
         * @param from Where they come from.
         * @param at Where they start.
         * @param count How many.
         */
        void add(float @NonNull [] from, int at, int count) {
            if (size + count > values.length) {
                values = Arrays.copyOf(values, Math.max(values.length * 2, size + count));
            }
            System.arraycopy(from, at, values, size, count);
            size += count;
        }

        /**
         * The values, the array itself.
         *
         * @return The array, longer than the values.
         */
        float[] array() {
            return values;
        }

        /**
         * The values, trimmed.
         *
         * @return A copy.
         */
        float[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }

    /** A growing int array. */
    private static final class IntArrayBuilder {
        /** The values. */
        private int[] values = new int[Attributes.PER_TRIANGLE * INITIAL_TRIANGLES];

        /** How many there are. */
        private int size;

        /**
         * Append some values.
         *
         * @param from Where they come from.
         * @param at Where they start.
         * @param count How many.
         */
        void add(int @NonNull [] from, int at, int count) {
            if (size + count > values.length) {
                values = Arrays.copyOf(values, Math.max(values.length * 2, size + count));
            }
            System.arraycopy(from, at, values, size, count);
            size += count;
        }

        /**
         * The values, the array itself.
         *
         * @return The array, longer than the values.
         */
        int[] array() {
            return values;
        }
    }
}
