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
 * texture charts meet at the same corners; the simplifier keeps seams' vertices, so an output
 * triangle lies within one chart.
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

    /** The sources, by material. */
    private final Map<Integer, Source> sources = new HashMap<>();

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
        final Source source = sources.computeIfAbsent(material, key -> new Source());
        source.corners.add(corners, at, TRIANGLE_VALUES);
        source.attributes.add(attributes, attributesAt, Attributes.PER_TRIANGLE);
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
        for (int t = 0; t < materials.length; ++t) {
            final Source source = sources.get(materials[t]);
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
            for (int c = 0; c < CORNERS; ++c) {
                for (int axis = 0; axis < AXES; ++axis) {
                    corner[axis] = corners[base + c * AXES + axis];
                    point[axis] = corner[axis] + (middle[axis] - corner[axis]) * INSET;
                }
                source.tree.nearest(point, Double.MAX_VALUE, scratch);
                final int s = scratch.triangle;
                if (s < 0) {
                    continue;
                }
                weights(sourceCorners, s * TRIANGLE_VALUES, corner, weights);
                Attributes.blend(
                        sourceAttributes,
                        s * Attributes.PER_TRIANGLE,
                        weights,
                        out,
                        t * Attributes.PER_TRIANGLE + c * Attributes.VALUES);
            }
        }
        return out;
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
