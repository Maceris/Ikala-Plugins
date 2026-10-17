package com.ikalagaming.graphics.lod;

import lombok.NonNull;

import java.util.Arrays;

/**
 * Distances and closest points between points, segments and triangles, in doubles. The closest
 * point routines follow Ericson, <i>Real-Time Collision Detection</i> (2005), §5.1.
 */
final class Geometry {

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Below this, a length or determinant is treated as zero. */
    private static final double EPSILON = 1e-12;

    /** Distinct entries of a symmetric 3x3 matrix. */
    private static final int COVARIANCES = 6;

    /** Jacobi sweeps at most; a 3x3 matrix converges in a handful. */
    private static final int JACOBI_SWEEPS = 16;

    /**
     * A triangle's area.
     *
     * @param mesh The mesh.
     * @param triangle The triangle.
     * @return The area.
     */
    static double triangleArea(@NonNull LodMesh mesh, int triangle) {
        final double[] a = new double[AXES];
        final double[] b = new double[AXES];
        final double[] c = new double[AXES];
        corners(mesh, triangle, a, b, c);
        return area(a, b, c);
    }

    /**
     * Read a triangle's corners.
     *
     * @param mesh The mesh.
     * @param triangle The triangle.
     * @param a The first corner, written.
     * @param b The second corner, written.
     * @param c The third corner, written.
     */
    static void corners(
            @NonNull LodMesh mesh,
            int triangle,
            double @NonNull [] a,
            double @NonNull [] b,
            double @NonNull [] c) {
        for (int axis = 0; axis < AXES; ++axis) {
            a[axis] = mesh.position(mesh.corner(triangle, 0), axis);
            b[axis] = mesh.position(mesh.corner(triangle, 1), axis);
            c[axis] = mesh.position(mesh.corner(triangle, 2), axis);
        }
    }

    /**
     * The area of a triangle.
     *
     * @param a The first corner.
     * @param b The second corner.
     * @param c The third corner.
     * @return The area.
     */
    static double area(double @NonNull [] a, double @NonNull [] b, double @NonNull [] c) {
        final double[] n = new double[AXES];
        normal(a, b, c, n);
        return 0.5 * Math.sqrt(dot(n, n));
    }

    /**
     * The unnormalized normal of a triangle, (b - a) × (c - a).
     *
     * @param a The first corner.
     * @param b The second corner.
     * @param c The third corner.
     * @param out The normal, written.
     */
    static void normal(
            double @NonNull [] a,
            double @NonNull [] b,
            double @NonNull [] c,
            double @NonNull [] out) {
        final double ux = b[0] - a[0];
        final double uy = b[1] - a[1];
        final double uz = b[2] - a[2];
        final double vx = c[0] - a[0];
        final double vy = c[1] - a[1];
        final double vz = c[2] - a[2];
        out[0] = uy * vz - uz * vy;
        out[1] = uz * vx - ux * vz;
        out[2] = ux * vy - uy * vx;
    }

    /**
     * A dot product.
     *
     * @param a The first vector.
     * @param b The second vector.
     * @return a · b.
     */
    static double dot(double @NonNull [] a, double @NonNull [] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /**
     * The squared distance between two points.
     *
     * @param a The first point.
     * @param b The second point.
     * @return The squared distance.
     */
    static double distanceSquared(double @NonNull [] a, double @NonNull [] b) {
        final double dx = a[0] - b[0];
        final double dy = a[1] - b[1];
        final double dz = a[2] - b[2];
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * The closest point on a triangle to a point (Ericson §5.1.5).
     *
     * @param p The point.
     * @param a The first corner.
     * @param b The second corner.
     * @param c The third corner.
     * @param out The closest point, written.
     */
    static void closestOnTriangle(
            double @NonNull [] p,
            double @NonNull [] a,
            double @NonNull [] b,
            double @NonNull [] c,
            double @NonNull [] out) {
        final double abx = b[0] - a[0];
        final double aby = b[1] - a[1];
        final double abz = b[2] - a[2];
        final double acx = c[0] - a[0];
        final double acy = c[1] - a[1];
        final double acz = c[2] - a[2];
        final double apx = p[0] - a[0];
        final double apy = p[1] - a[1];
        final double apz = p[2] - a[2];
        final double d1 = abx * apx + aby * apy + abz * apz;
        final double d2 = acx * apx + acy * apy + acz * apz;
        if (d1 <= 0 && d2 <= 0) {
            set(out, a);
            return;
        }
        final double bpx = p[0] - b[0];
        final double bpy = p[1] - b[1];
        final double bpz = p[2] - b[2];
        final double d3 = abx * bpx + aby * bpy + abz * bpz;
        final double d4 = acx * bpx + acy * bpy + acz * bpz;
        if (d3 >= 0 && d4 <= d3) {
            set(out, b);
            return;
        }
        final double vc = d1 * d4 - d3 * d2;
        if (vc <= 0 && d1 >= 0 && d3 <= 0) {
            final double v = d1 / (d1 - d3);
            out[0] = a[0] + v * abx;
            out[1] = a[1] + v * aby;
            out[2] = a[2] + v * abz;
            return;
        }
        final double cpx = p[0] - c[0];
        final double cpy = p[1] - c[1];
        final double cpz = p[2] - c[2];
        final double d5 = abx * cpx + aby * cpy + abz * cpz;
        final double d6 = acx * cpx + acy * cpy + acz * cpz;
        if (d6 >= 0 && d5 <= d6) {
            set(out, c);
            return;
        }
        final double vb = d5 * d2 - d1 * d6;
        if (vb <= 0 && d2 >= 0 && d6 <= 0) {
            final double w = d2 / (d2 - d6);
            out[0] = a[0] + w * acx;
            out[1] = a[1] + w * acy;
            out[2] = a[2] + w * acz;
            return;
        }
        final double va = d3 * d6 - d5 * d4;
        if (va <= 0 && (d4 - d3) >= 0 && (d5 - d6) >= 0) {
            final double w = (d4 - d3) / ((d4 - d3) + (d5 - d6));
            out[0] = b[0] + w * (c[0] - b[0]);
            out[1] = b[1] + w * (c[1] - b[1]);
            out[2] = b[2] + w * (c[2] - b[2]);
            return;
        }
        final double denom = 1 / (va + vb + vc);
        final double v = vb * denom;
        final double w = vc * denom;
        out[0] = a[0] + abx * v + acx * w;
        out[1] = a[1] + aby * v + acy * w;
        out[2] = a[2] + abz * v + acz * w;
    }

    /**
     * The squared distance between two segments (Ericson §5.1.9).
     *
     * @param p1 The first segment's start.
     * @param q1 The first segment's end.
     * @param p2 The second segment's start.
     * @param q2 The second segment's end.
     * @return The squared distance.
     */
    static double segmentDistanceSquared(
            double @NonNull [] p1,
            double @NonNull [] q1,
            double @NonNull [] p2,
            double @NonNull [] q2) {
        final double[] d1 = {q1[0] - p1[0], q1[1] - p1[1], q1[2] - p1[2]};
        final double[] d2 = {q2[0] - p2[0], q2[1] - p2[1], q2[2] - p2[2]};
        final double[] r = {p1[0] - p2[0], p1[1] - p2[1], p1[2] - p2[2]};
        final double a = dot(d1, d1);
        final double e = dot(d2, d2);
        final double f = dot(d2, r);
        double s;
        double t;
        if (a <= EPSILON && e <= EPSILON) {
            return distanceSquared(p1, p2);
        }
        if (a <= EPSILON) {
            s = 0;
            t = Math.clamp(f / e, 0.0, 1.0);
        } else {
            final double c = dot(d1, r);
            if (e <= EPSILON) {
                t = 0;
                s = Math.clamp(-c / a, 0.0, 1.0);
            } else {
                final double b = dot(d1, d2);
                final double denom = a * e - b * b;
                s = denom > EPSILON ? Math.clamp((b * f - c * e) / denom, 0.0, 1.0) : 0;
                t = (b * s + f) / e;
                if (t < 0) {
                    t = 0;
                    s = Math.clamp(-c / a, 0.0, 1.0);
                } else if (t > 1) {
                    t = 1;
                    s = Math.clamp((b - c) / a, 0.0, 1.0);
                }
            }
        }
        final double x = p1[0] + d1[0] * s - (p2[0] + d2[0] * t);
        final double y = p1[1] + d1[1] * s - (p2[1] + d2[1] * t);
        final double z = p1[2] + d1[2] * s - (p2[2] + d2[2] * t);
        return x * x + y * y + z * z;
    }

    /**
     * Whether a segment crosses a triangle.
     *
     * @param p The segment's start.
     * @param q The segment's end.
     * @param a The first corner.
     * @param b The second corner.
     * @param c The third corner.
     * @return True if they intersect.
     */
    static boolean segmentCrossesTriangle(
            double @NonNull [] p,
            double @NonNull [] q,
            double @NonNull [] a,
            double @NonNull [] b,
            double @NonNull [] c) {
        final double[] n = new double[AXES];
        normal(a, b, c, n);
        final double dp = n[0] * (p[0] - a[0]) + n[1] * (p[1] - a[1]) + n[2] * (p[2] - a[2]);
        final double dq = n[0] * (q[0] - a[0]) + n[1] * (q[1] - a[1]) + n[2] * (q[2] - a[2]);
        if ((dp > 0 && dq > 0) || (dp < 0 && dq < 0) || Math.abs(dp - dq) <= EPSILON) {
            return false;
        }
        final double t = dp / (dp - dq);
        final double[] hit = {
            p[0] + t * (q[0] - p[0]), p[1] + t * (q[1] - p[1]), p[2] + t * (q[2] - p[2])
        };
        final double[] closest = new double[AXES];
        closestOnTriangle(hit, a, b, c, closest);
        return distanceSquared(hit, closest) <= EPSILON;
    }

    /**
     * The squared distance between two triangles: zero if they cross, otherwise the least of the
     * corner-to-triangle and edge-to-edge distances.
     *
     * @param a The first triangle's corners, 9 values.
     * @param b The second triangle's corners, 9 values.
     * @return The squared distance.
     */
    static double triangleDistanceSquared(double @NonNull [] a, double @NonNull [] b) {
        final double[][] ta = {{a[0], a[1], a[2]}, {a[3], a[4], a[5]}, {a[6], a[7], a[8]}};
        final double[][] tb = {{b[0], b[1], b[2]}, {b[3], b[4], b[5]}, {b[6], b[7], b[8]}};
        for (int i = 0; i < AXES; ++i) {
            final int j = (i + 1) % AXES;
            if (segmentCrossesTriangle(ta[i], ta[j], tb[0], tb[1], tb[2])
                    || segmentCrossesTriangle(tb[i], tb[j], ta[0], ta[1], ta[2])) {
                return 0;
            }
        }
        double best = Double.MAX_VALUE;
        final double[] closest = new double[AXES];
        for (int i = 0; i < AXES; ++i) {
            closestOnTriangle(ta[i], tb[0], tb[1], tb[2], closest);
            best = Math.min(best, distanceSquared(ta[i], closest));
            closestOnTriangle(tb[i], ta[0], ta[1], ta[2], closest);
            best = Math.min(best, distanceSquared(tb[i], closest));
        }
        for (int i = 0; i < AXES; ++i) {
            for (int j = 0; j < AXES; ++j) {
                best =
                        Math.min(
                                best,
                                segmentDistanceSquared(
                                        ta[i], ta[(i + 1) % AXES], tb[j], tb[(j + 1) % AXES]));
            }
        }
        return best;
    }

    /**
     * How wide each connected part of a mesh is: the middle side of a box around it along its
     * principal axes. Taking a part away changes no pixel farther than that from the rest of the
     * picture, from any side: a flat panel is as wide as its shorter side, a thin rod or strip as
     * its thickness, a cube as its side. A part bent around corners gets a looser box, so more.
     *
     * @param mesh The mesh.
     * @param part Each vertex's part, by its root vertex, as {@link VirtualEdges#components} gives.
     * @return Each root vertex's part's width; other vertices' entries are 0.
     */
    static double @NonNull [] partWidths(@NonNull LodMesh mesh, int @NonNull [] part) {
        final int vertices = mesh.getVertexCount();
        final int[] count = new int[vertices];
        final double[] mean = new double[vertices * AXES];
        for (int v = 0; v < vertices; ++v) {
            ++count[part[v]];
            for (int axis = 0; axis < AXES; ++axis) {
                mean[part[v] * AXES + axis] += mesh.position(v, axis);
            }
        }
        for (int v = 0; v < vertices; ++v) {
            for (int axis = 0; axis < AXES; ++axis) {
                mean[v * AXES + axis] /= Math.max(1, count[v]);
            }
        }
        // Covariances about the means: xx, yy, zz, xy, xz, yz
        final double[] covariance = new double[vertices * COVARIANCES];
        for (int v = 0; v < vertices; ++v) {
            final int root = part[v];
            final double x = mesh.position(v, 0) - mean[root * AXES];
            final double y = mesh.position(v, 1) - mean[root * AXES + 1];
            final double z = mesh.position(v, 2) - mean[root * AXES + 2];
            final int at = root * COVARIANCES;
            covariance[at] += x * x;
            covariance[at + 1] += y * y;
            covariance[at + 2] += z * z;
            covariance[at + 3] += x * y;
            covariance[at + 4] += x * z;
            covariance[at + 5] += y * z;
        }
        final double[] axes = new double[vertices * AXES * AXES];
        final double[] matrix = new double[AXES * AXES];
        final double[] vectors = new double[AXES * AXES];
        for (int root = 0; root < vertices; ++root) {
            if (part[root] != root) {
                continue;
            }
            final int at = root * COVARIANCES;
            matrix[0] = covariance[at];
            matrix[4] = covariance[at + 1];
            matrix[8] = covariance[at + 2];
            matrix[1] = covariance[at + 3];
            matrix[3] = covariance[at + 3];
            matrix[2] = covariance[at + 4];
            matrix[6] = covariance[at + 4];
            matrix[5] = covariance[at + 5];
            matrix[7] = covariance[at + 5];
            eigenvectors(matrix, vectors);
            System.arraycopy(vectors, 0, axes, root * AXES * AXES, AXES * AXES);
        }
        // Each part's extent along each of its axes
        final double[] lo = new double[vertices * AXES];
        final double[] hi = new double[vertices * AXES];
        Arrays.fill(lo, Double.POSITIVE_INFINITY);
        Arrays.fill(hi, Double.NEGATIVE_INFINITY);
        for (int v = 0; v < vertices; ++v) {
            final int root = part[v];
            for (int k = 0; k < AXES; ++k) {
                double along = 0;
                for (int axis = 0; axis < AXES; ++axis) {
                    along += mesh.position(v, axis) * axes[(root * AXES + k) * AXES + axis];
                }
                lo[root * AXES + k] = Math.min(lo[root * AXES + k], along);
                hi[root * AXES + k] = Math.max(hi[root * AXES + k], along);
            }
        }
        final double[] widths = new double[vertices];
        final double[] sides = new double[AXES];
        for (int root = 0; root < vertices; ++root) {
            if (part[root] != root) {
                continue;
            }
            for (int k = 0; k < AXES; ++k) {
                sides[k] = hi[root * AXES + k] - lo[root * AXES + k];
            }
            Arrays.sort(sides);
            widths[root] = sides[1];
        }
        return widths;
    }

    /**
     * The eigenvectors of a symmetric 3x3 matrix, by cyclic Jacobi rotations.
     *
     * @param matrix The matrix, row by row; overwritten.
     * @param vectors The unit eigenvectors, one per row, written.
     */
    static void eigenvectors(double @NonNull [] matrix, double @NonNull [] vectors) {
        Arrays.fill(vectors, 0);
        for (int k = 0; k < AXES; ++k) {
            vectors[k * AXES + k] = 1;
        }
        for (int sweep = 0; sweep < JACOBI_SWEEPS; ++sweep) {
            final double off =
                    matrix[1] * matrix[1] + matrix[2] * matrix[2] + matrix[5] * matrix[5];
            if (off <= EPSILON * EPSILON) {
                return;
            }
            for (int p = 0; p < AXES - 1; ++p) {
                for (int q = p + 1; q < AXES; ++q) {
                    final double apq = matrix[p * AXES + q];
                    if (Math.abs(apq) <= EPSILON * EPSILON) {
                        continue;
                    }
                    // The rotation that zeroes the p, q entry
                    final double theta = (matrix[q * AXES + q] - matrix[p * AXES + p]) / (2 * apq);
                    final double t =
                            Math.signum(theta == 0 ? 1 : theta)
                                    / (Math.abs(theta) + Math.sqrt(theta * theta + 1));
                    final double c = 1 / Math.sqrt(t * t + 1);
                    final double s = t * c;
                    for (int k = 0; k < AXES; ++k) {
                        final double kp = matrix[k * AXES + p];
                        final double kq = matrix[k * AXES + q];
                        matrix[k * AXES + p] = c * kp - s * kq;
                        matrix[k * AXES + q] = s * kp + c * kq;
                    }
                    for (int k = 0; k < AXES; ++k) {
                        final double pk = matrix[p * AXES + k];
                        final double qk = matrix[q * AXES + k];
                        matrix[p * AXES + k] = c * pk - s * qk;
                        matrix[q * AXES + k] = s * pk + c * qk;
                    }
                    for (int k = 0; k < AXES; ++k) {
                        final double vp = vectors[p * AXES + k];
                        final double vq = vectors[q * AXES + k];
                        vectors[p * AXES + k] = c * vp - s * vq;
                        vectors[q * AXES + k] = s * vp + c * vq;
                    }
                }
            }
        }
    }

    /**
     * Copy a point.
     *
     * @param out Where to copy to.
     * @param from What to copy.
     */
    private static void set(double @NonNull [] out, double @NonNull [] from) {
        out[0] = from[0];
        out[1] = from[1];
        out[2] = from[2];
    }

    /** Static helpers only. */
    private Geometry() {}
}
