package com.ikalagaming.graphics.vulkan;

import lombok.NonNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.function.Function;

/**
 * The sky's spherical harmonics as {@code environment.comp} takes them and {@code environment.glsl}
 * reads them, see {@link Environment}; keep them in step with this.
 *
 * <p>The sky's radiance is projected onto the real spherical harmonics up to the second band, 9
 * coefficients, over the texels of a cube map, each weighted by its solid angle. The diffuse light
 * on a surface is the coefficients convolved with the cosine lobe (Ramamoorthi and Hanrahan, "An
 * Efficient Representation for Irradiance Environment Maps", 2001), over pi: the radiance that, the
 * same from every direction, would light it as much.
 */
public final class EnvironmentMath {

    /** How many coefficients there are. */
    public static final int COUNT = Environment.HARMONICS_COUNT;

    /** Faces of a cube. */
    private static final int FACES = Environment.FACES;

    /** Y00, the constant band. */
    private static final double BAND_0 = 0.282_095;

    /** The first band's factor. */
    private static final double BAND_1 = 0.488_603;

    /** The second band's factor for xy, yz and xz. */
    private static final double BAND_2_CROSS = 1.092_548;

    /** The second band's factor for 3z^2 - 1. */
    private static final double BAND_2_Z = 0.315_392;

    /** The second band's factor for x^2 - y^2. */
    private static final double BAND_2_XY = 0.546_274;

    /** The cosine lobe's first band over pi. */
    private static final double LOBE_1 = 2.0 / 3.0;

    /** The cosine lobe's second band over pi. */
    private static final double LOBE_2 = 0.25;

    /**
     * The harmonics at a unit direction.
     *
     * @param d The direction.
     * @return The 9 values.
     */
    public static double[] basis(@NonNull Vector3dc d) {
        return new double[] {
            BAND_0,
            BAND_1 * d.y(),
            BAND_1 * d.z(),
            BAND_1 * d.x(),
            BAND_2_CROSS * d.x() * d.y(),
            BAND_2_CROSS * d.y() * d.z(),
            BAND_2_Z * (3 * d.z() * d.z() - 1),
            BAND_2_CROSS * d.x() * d.z(),
            BAND_2_XY * (d.x() * d.x() - d.y() * d.y())
        };
    }

    /**
     * The direction through a texel of a cube face, as Vulkan samples cube maps, not normalized.
     *
     * @param face The face, +x, -x, +y, -y, +z, -z.
     * @param s Across the face, from -1 to 1.
     * @param t Down the face, from -1 to 1.
     * @return The direction.
     */
    public static Vector3d cubeDirection(int face, double s, double t) {
        return switch (face) {
            case 0 -> new Vector3d(1, -t, -s);
            case 1 -> new Vector3d(-1, -t, s);
            case 2 -> new Vector3d(s, 1, t);
            case 3 -> new Vector3d(s, -1, -t);
            case 4 -> new Vector3d(s, -t, 1);
            default -> new Vector3d(-s, -t, -1);
        };
    }

    /**
     * Project a sky onto the harmonics over the texels of a cube map, as the harmonics pass does.
     *
     * @param sky The radiance toward each unit direction, one channel.
     * @param size The cube's width and height, in texels.
     * @return The 9 coefficients.
     */
    public static double[] project(@NonNull Function<Vector3dc, Double> sky, int size) {
        final double[] coefficients = new double[COUNT];
        for (int face = 0; face < FACES; ++face) {
            for (int y = 0; y < size; ++y) {
                for (int x = 0; x < size; ++x) {
                    final double s = (x + 0.5) / size * 2 - 1;
                    final double t = (y + 0.5) / size * 2 - 1;
                    final double r2 = 1 + s * s + t * t;
                    final double solidAngle = 4 / ((double) size * size * r2 * Math.sqrt(r2));
                    final Vector3d d = cubeDirection(face, s, t).normalize();
                    final double radiance = sky.apply(d);
                    final double[] y9 = basis(d);
                    for (int k = 0; k < COUNT; ++k) {
                        coefficients[k] += radiance * y9[k] * solidAngle;
                    }
                }
            }
        }
        return coefficients;
    }

    /**
     * The sky's average radiance, from the first coefficient.
     *
     * @param coefficients The 9 coefficients.
     * @return The average.
     */
    public static double average(double @NonNull [] coefficients) {
        return coefficients[0] * BAND_0;
    }

    /**
     * The irradiance on a surface facing a direction, over pi, never below zero.
     *
     * @param coefficients The 9 coefficients.
     * @param n The unit direction the surface faces.
     * @return The irradiance over pi.
     */
    public static double diffuse(double @NonNull [] coefficients, @NonNull Vector3dc n) {
        final double[] y9 = basis(n);
        double e = coefficients[0] * y9[0];
        for (int k = 1; k <= 3; ++k) {
            e += LOBE_1 * coefficients[k] * y9[k];
        }
        for (int k = 4; k < COUNT; ++k) {
            e += LOBE_2 * coefficients[k] * y9[k];
        }
        return Math.max(e, 0);
    }

    /** Private constructor so this class is not instantiated. */
    private EnvironmentMath() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
