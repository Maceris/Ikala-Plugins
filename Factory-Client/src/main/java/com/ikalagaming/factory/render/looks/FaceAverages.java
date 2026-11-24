package com.ikalagaming.factory.render.looks;

import com.ikalagaming.graphics.graph.MeshData;

import lombok.NonNull;
import org.joml.Vector3f;

import java.awt.image.BufferedImage;

/**
 * The colors a model's faces show from its texture, averaged by which way they point: up, down and
 * to the sides. Far away a block is drawn as flat colors by direction, so a grass block, whose one
 * texture holds its side, top and bottom side by side, needs the top's color on top rather than the
 * whole texture's.
 *
 * <p>Each triangle is sampled at points spread over it, weighed by its area, at the texels its
 * texture coordinates land on, as the pixel-sharp sampler would read them. Texels mostly
 * see-through, the holes in leaves, count for nothing.
 *
 * @param up The faces pointing up, linear red, green and blue, or null if none do.
 * @param down The faces pointing down, or null if none do.
 * @param side The rest, or null if there are none.
 */
public record FaceAverages(Vector3f up, Vector3f down, Vector3f side) {

    /** How far up or down a normal must point to count as up or down, as a cosine. */
    static final float FACING = 0.5f;

    /** Samples along each edge of a triangle, so a triangle gets about this squared over 2. */
    static final int SAMPLES = 12;

    /** How much two averages may differ in any channel and still count as the same. */
    static final float SAME = 0.01f;

    /** The alpha below which a texel is a hole. */
    private static final int HOLE_ALPHA = 128;

    /** Floats before the texture coordinates in a vertex. */
    private static final int UV_OFFSET = 12;

    /** Floats before the normal in a vertex. */
    private static final int NORMAL_OFFSET = 3;

    /** Bits to shift for red in a packed color. */
    private static final int RED_SHIFT = 16;

    /** Bits to shift for green in a packed color. */
    private static final int GREEN_SHIFT = 8;

    /** Bits to shift for alpha in a packed color. */
    private static final int ALPHA_SHIFT = 24;

    /** The largest channel value. */
    private static final int CHANNEL_MAX = 255;

    /** The directions, as indices into the sums. */
    private static final int UP = 0;

    private static final int DOWN = 1;

    private static final int SIDE = 2;

    /**
     * Average a texture over a model's faces by direction.
     *
     * @param mesh The model, with texture coordinates from 0 to 1 across the texture, rows down.
     * @param texture The texture, sRGB.
     * @return The averages.
     */
    public static FaceAverages of(@NonNull MeshData mesh, @NonNull BufferedImage texture) {
        final float[] vertices = mesh.getVertexData();
        final int[] indices = mesh.getIndices();
        final int stride = MeshData.VERTEX_SIZE_IN_FLOATS;
        final double[][] sums = new double[3][4];
        final float[] linear = linearTable();
        for (int t = 0; t + 2 < indices.length; t += 3) {
            final int a = indices[t] * stride;
            final int b = indices[t + 1] * stride;
            final int c = indices[t + 2] * stride;
            final double area = area(vertices, a, b, c);
            if (area <= 0) {
                continue;
            }
            final float normalY =
                    (vertices[a + NORMAL_OFFSET + 1]
                                    + vertices[b + NORMAL_OFFSET + 1]
                                    + vertices[c + NORMAL_OFFSET + 1])
                            / 3;
            final double[] sum = sums[normalY >= FACING ? UP : normalY <= -FACING ? DOWN : SIDE];
            final double weight = area / (SAMPLES * (SAMPLES + 1) / 2.0);
            for (int i = 0; i < SAMPLES; ++i) {
                for (int j = 0; i + j < SAMPLES; ++j) {
                    // A point inside the triangle, away from its edges
                    final double wb = (i + 1.0 / 3) / SAMPLES;
                    final double wc = (j + 1.0 / 3) / SAMPLES;
                    final double wa = 1 - wb - wc;
                    final double u =
                            wa * vertices[a + UV_OFFSET]
                                    + wb * vertices[b + UV_OFFSET]
                                    + wc * vertices[c + UV_OFFSET];
                    final double v =
                            wa * vertices[a + UV_OFFSET + 1]
                                    + wb * vertices[b + UV_OFFSET + 1]
                                    + wc * vertices[c + UV_OFFSET + 1];
                    final int argb = texel(texture, u, v);
                    if ((argb >>> ALPHA_SHIFT) < HOLE_ALPHA) {
                        continue;
                    }
                    sum[0] += weight * linear[(argb >> RED_SHIFT) & CHANNEL_MAX];
                    sum[1] += weight * linear[(argb >> GREEN_SHIFT) & CHANNEL_MAX];
                    sum[2] += weight * linear[argb & CHANNEL_MAX];
                    sum[3] += weight;
                }
            }
        }
        return new FaceAverages(average(sums[UP]), average(sums[DOWN]), average(sums[SIDE]));
    }

    /**
     * Whether two averages are the same, near enough that a variant for one would look no
     * different.
     *
     * @param a One, or null.
     * @param b The other, or null.
     * @return True if both are null, or both are set and close in every channel.
     */
    public static boolean same(Vector3f a, Vector3f b) {
        if (a == null || b == null) {
            return a == b;
        }
        return Math.abs(a.x - b.x) <= SAME
                && Math.abs(a.y - b.y) <= SAME
                && Math.abs(a.z - b.z) <= SAME;
    }

    /**
     * The texel a texture coordinate lands on, clamped to the texture as the sampler is.
     *
     * @param texture The texture.
     * @param u Across, 0 to 1.
     * @param v Down, 0 to 1.
     * @return The texel, packed ARGB.
     */
    private static int texel(@NonNull BufferedImage texture, double u, double v) {
        final int x =
                (int) Math.clamp(Math.floor(u * texture.getWidth()), 0, texture.getWidth() - 1);
        final int y =
                (int) Math.clamp(Math.floor(v * texture.getHeight()), 0, texture.getHeight() - 1);
        return texture.getRGB(x, y);
    }

    /**
     * A triangle's area.
     *
     * @param vertices The vertex data.
     * @param a The first corner's offset.
     * @param b The second's.
     * @param c The third's.
     * @return The area.
     */
    private static double area(float @NonNull [] vertices, int a, int b, int c) {
        final double abx = vertices[b] - vertices[a];
        final double aby = vertices[b + 1] - vertices[a + 1];
        final double abz = vertices[b + 2] - vertices[a + 2];
        final double acx = vertices[c] - vertices[a];
        final double acy = vertices[c + 1] - vertices[a + 1];
        final double acz = vertices[c + 2] - vertices[a + 2];
        final double x = aby * acz - abz * acy;
        final double y = abz * acx - abx * acz;
        final double z = abx * acy - aby * acx;
        return Math.sqrt(x * x + y * y + z * z) / 2;
    }

    /**
     * An average from its sums.
     *
     * @param sum Red, green and blue times weight, then the weight.
     * @return The average, or null if nothing was counted.
     */
    private static Vector3f average(double @NonNull [] sum) {
        if (sum[3] <= 0) {
            return null;
        }
        return new Vector3f(
                (float) (sum[0] / sum[3]), (float) (sum[1] / sum[3]), (float) (sum[2] / sum[3]));
    }

    /**
     * Each 8 bit sRGB value in linear light.
     *
     * @return The table.
     */
    private static float[] linearTable() {
        final float[] out = new float[CHANNEL_MAX + 1];
        for (int i = 0; i <= CHANNEL_MAX; ++i) {
            final double s = (double) i / CHANNEL_MAX;
            out[i] = (float) (s <= 0.040_45 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4));
        }
        return out;
    }
}
