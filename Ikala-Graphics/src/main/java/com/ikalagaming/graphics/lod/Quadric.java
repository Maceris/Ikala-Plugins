package com.ikalagaming.graphics.lod;

import lombok.NonNull;

/**
 * Quadrics stored flat, {@value #STRIDE} doubles each, so many fit in one array: the symmetric
 * matrix A (6 values), the vector b, the constant c, and the area weight they were summed with.
 * The error at x is {@code x^T A x + 2 b^T x + c}.
 *
 * <p>Two kinds are used, as in Liu, Zhang and Yuksel, <i>Simplifying Textured Triangle Meshes in
 * the Wild</i> (2025):
 *
 * <ul>
 *   <li>Garland–Heckbert plane quadrics, each face's plane weighted by a third of its area per
 *       corner (their Eqs. 9–13). These are summed as vertices collapse, so they remember the input.
 *   <li>The Lindstrom–Turk boundary area quadric (their Eqs. 15–16): for a boundary edge ab, the
 *       squared area of the triangle the new vertex makes with it. This is recomputed for each
 *       collapse from the boundary edges around it, and never summed.
 * </ul>
 *
 * <p>Dividing the error by the weight gives a mean squared distance, so its square root is an error
 * in world units, which is what level of detail selection projects to the screen.
 */
final class Quadric {

    /** Doubles per quadric. */
    static final int STRIDE = 11;

    /** Where A's xx entry is. */
    private static final int AXX = 0;

    /** Where A's xy entry is. */
    private static final int AXY = 1;

    /** Where A's xz entry is. */
    private static final int AXZ = 2;

    /** Where A's yy entry is. */
    private static final int AYY = 3;

    /** Where A's yz entry is. */
    private static final int AYZ = 4;

    /** Where A's zz entry is. */
    private static final int AZZ = 5;

    /** Where b's x is. */
    private static final int BX = 6;

    /** Where b's y is. */
    private static final int BY = 7;

    /** Where b's z is. */
    private static final int BZ = 8;

    /** Where c is. */
    private static final int C = 9;

    /** Where the area weight is. */
    static final int WEIGHT = 10;

    /** How small A's determinant may be, relative to its scale, before it is not solved. */
    private static final double SINGULAR = 1e-9;

    /** A third, the share of a triangle's area each corner gets. */
    private static final double THIRD = 1.0 / 3;

    /**
     * Add a face's plane, weighted by a third of its area, to a quadric.
     *
     * @param q The quadrics.
     * @param at Where the quadric starts.
     * @param a The face's first corner.
     * @param b The second corner.
     * @param c The third corner.
     */
    static void addFace(
            double @NonNull [] q,
            int at,
            double @NonNull [] a,
            double @NonNull [] b,
            double @NonNull [] c) {
        final double[] n = new double[3];
        Geometry.normal(a, b, c, n);
        final double length = Math.sqrt(Geometry.dot(n, n));
        if (length == 0) {
            return;
        }
        final double weight = 0.5 * length * THIRD;
        n[0] /= length;
        n[1] /= length;
        n[2] /= length;
        final double d = -Geometry.dot(n, a);
        q[at + AXX] += weight * n[0] * n[0];
        q[at + AXY] += weight * n[0] * n[1];
        q[at + AXZ] += weight * n[0] * n[2];
        q[at + AYY] += weight * n[1] * n[1];
        q[at + AYZ] += weight * n[1] * n[2];
        q[at + AZZ] += weight * n[2] * n[2];
        q[at + BX] += weight * d * n[0];
        q[at + BY] += weight * d * n[1];
        q[at + BZ] += weight * d * n[2];
        q[at + C] += weight * d * d;
        q[at + WEIGHT] += weight;
    }

    /**
     * Add the boundary area quadric of edge ab: half the squared area of the triangle (x, a, b),
     * from s = b - a and t = a × b, as {@code |t + s × x|^2 / 2}. It carries no area weight.
     *
     * @param q The quadrics.
     * @param at Where the quadric starts.
     * @param a The edge's start.
     * @param b The edge's end.
     */
    static void addBoundaryEdge(
            double @NonNull [] q, int at, double @NonNull [] a, double @NonNull [] b) {
        final double sx = b[0] - a[0];
        final double sy = b[1] - a[1];
        final double sz = b[2] - a[2];
        final double tx = a[1] * b[2] - a[2] * b[1];
        final double ty = a[2] * b[0] - a[0] * b[2];
        final double tz = a[0] * b[1] - a[1] * b[0];
        final double ss = sx * sx + sy * sy + sz * sz;
        // A = [s]x^T [s]x = |s|^2 I - s s^T, b = [s]x^T t = t x s
        q[at + AXX] += 0.5 * (ss - sx * sx);
        q[at + AXY] += 0.5 * (-sx * sy);
        q[at + AXZ] += 0.5 * (-sx * sz);
        q[at + AYY] += 0.5 * (ss - sy * sy);
        q[at + AYZ] += 0.5 * (-sy * sz);
        q[at + AZZ] += 0.5 * (ss - sz * sz);
        q[at + BX] += 0.5 * (ty * sz - tz * sy);
        q[at + BY] += 0.5 * (tz * sx - tx * sz);
        q[at + BZ] += 0.5 * (tx * sy - ty * sx);
        q[at + C] += 0.5 * (tx * tx + ty * ty + tz * tz);
    }

    /**
     * Add one quadric to another.
     *
     * @param to The quadrics to add to.
     * @param at Where that quadric starts.
     * @param from The quadrics to add from.
     * @param fromAt Where that quadric starts.
     */
    static void add(double @NonNull [] to, int at, double @NonNull [] from, int fromAt) {
        for (int i = 0; i < STRIDE; ++i) {
            to[at + i] += from[fromAt + i];
        }
    }

    /**
     * The error of a quadric at a point.
     *
     * @param q The quadrics.
     * @param at Where the quadric starts.
     * @param x The point.
     * @return {@code x^T A x + 2 b^T x + c}, at least 0.
     */
    static double error(double @NonNull [] q, int at, double @NonNull [] x) {
        final double ax = q[at + AXX] * x[0] + q[at + AXY] * x[1] + q[at + AXZ] * x[2];
        final double ay = q[at + AXY] * x[0] + q[at + AYY] * x[1] + q[at + AYZ] * x[2];
        final double az = q[at + AXZ] * x[0] + q[at + AYZ] * x[1] + q[at + AZZ] * x[2];
        final double e =
                x[0] * ax
                        + x[1] * ay
                        + x[2] * az
                        + 2 * (q[at + BX] * x[0] + q[at + BY] * x[1] + q[at + BZ] * x[2])
                        + q[at + C];
        return Math.max(0, e);
    }

    /**
     * The point of least error, solving {@code A x = -b}.
     *
     * @param q The quadrics.
     * @param at Where the quadric starts.
     * @param out The point, written if it was solved.
     * @return False if A is too close to singular to solve.
     */
    static boolean minimum(double @NonNull [] q, int at, double @NonNull [] out) {
        final double a = q[at + AXX];
        final double b = q[at + AXY];
        final double c = q[at + AXZ];
        final double d = q[at + AYY];
        final double e = q[at + AYZ];
        final double f = q[at + AZZ];
        final double c00 = d * f - e * e;
        final double c01 = c * e - b * f;
        final double c02 = b * e - c * d;
        final double det = a * c00 + b * c01 + c * c02;
        final double scale = a + d + f;
        if (scale <= 0 || Math.abs(det) <= SINGULAR * scale * scale * scale) {
            return false;
        }
        final double c11 = a * f - c * c;
        final double c12 = b * c - a * e;
        final double c22 = a * d - b * b;
        final double bx = -q[at + BX];
        final double by = -q[at + BY];
        final double bz = -q[at + BZ];
        out[0] = (c00 * bx + c01 * by + c02 * bz) / det;
        out[1] = (c01 * bx + c11 * by + c12 * bz) / det;
        out[2] = (c02 * bx + c12 * by + c22 * bz) / det;
        return true;
    }

    /** Static helpers only. */
    private Quadric() {}
}
