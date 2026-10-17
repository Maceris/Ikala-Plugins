package com.ikalagaming.graphics.lod;

import lombok.NonNull;

import java.util.List;

/**
 * Splits a mesh into clusters of at most a set number of triangles, by partitioning its dual graph:
 * triangles are nodes, and triangles sharing an edge are joined, so clusters are compact and their
 * shared borders short, which leaves the simplifier the most to work with (Karis et al., <i>Nanite:
 * A Deep Dive</i>, 2021). The same split is used for the leaves and for each simplified group. Also
 * the sphere helpers clusters and groups use.
 */
public final class Clusters {

    /** Triangles in a cluster at most, as in Nanite. */
    public static final int MAX_TRIANGLES = 128;

    /** Coordinates in a position. */
    private static final int AXES = 3;

    /** Vertices in a triangle. */
    private static final int CORNERS = 3;

    /** Values in a triangle's corners. */
    static final int TRIANGLE_VALUES = 9;

    /**
     * Split a mesh's triangles into clusters.
     *
     * @param mesh The mesh.
     * @param cap The most triangles a cluster may have.
     * @return The triangles of each cluster.
     */
    public static int[][] split(@NonNull LodMesh mesh, int cap) {
        return split(mesh, cap, 1);
    }

    /**
     * Split a mesh's triangles into clusters, a big mesh on several threads, giving the same
     * clusters as on one.
     *
     * @param mesh The mesh.
     * @param cap The most triangles a cluster may have.
     * @param threads How many threads to split on.
     * @return The triangles of each cluster.
     */
    public static int[][] split(@NonNull LodMesh mesh, int cap, int threads) {
        final int n = mesh.getTriangleCount();
        if (n <= cap) {
            final int[] all = new int[n];
            for (int t = 0; t < n; ++t) {
                all[t] = t;
            }
            return new int[][] {all};
        }
        Graph.Builder builder = new Graph.Builder(n);
        final LongIntMap firstUse = new LongIntMap(n * CORNERS);
        for (int t = 0; t < n; ++t) {
            double x = 0;
            double y = 0;
            double z = 0;
            for (int corner = 0; corner < CORNERS; ++corner) {
                final int v = mesh.corner(t, corner);
                x += mesh.position(v, 0);
                y += mesh.position(v, 1);
                z += mesh.position(v, 2);
                final int w = mesh.corner(t, (corner + 1) % CORNERS);
                final int other =
                        firstUse.putIfAbsent(LongIntMap.pair(Math.min(v, w), Math.max(v, w)), t);
                if (other != LongIntMap.MISSING) {
                    builder.edge(t, other, 1);
                }
            }
            builder.position(t, x / CORNERS, y / CORNERS, z / CORNERS);
        }
        final int[] part = GraphPartitioner.partition(builder.build(), cap, threads);
        final int parts = GraphPartitioner.partCount(part);
        final int[] sizes = new int[parts];
        for (int p : part) {
            ++sizes[p];
        }
        final int[][] clusters = new int[parts][];
        for (int p = 0; p < parts; ++p) {
            clusters[p] = new int[sizes[p]];
        }
        final int[] fill = new int[parts];
        for (int t = 0; t < n; ++t) {
            clusters[part[t]][fill[part[t]]++] = t;
        }
        return clusters;
    }

    /**
     * Copy some triangles' corners out of a mesh, flat.
     *
     * @param mesh The mesh.
     * @param triangles The triangles.
     * @param offset Added to every position, to move it to another origin.
     * @return The corners, 9 floats per triangle.
     */
    static float[] corners(
            @NonNull LodMesh mesh, int @NonNull [] triangles, float @NonNull [] offset) {
        final float[] out = new float[triangles.length * TRIANGLE_VALUES];
        for (int i = 0; i < triangles.length; ++i) {
            for (int corner = 0; corner < CORNERS; ++corner) {
                final int v = mesh.corner(triangles[i], corner);
                for (int axis = 0; axis < AXES; ++axis) {
                    out[i * TRIANGLE_VALUES + corner * AXES + axis] =
                            mesh.position(v, axis) + offset[axis];
                }
            }
        }
        return out;
    }

    /**
     * Copy some triangles' corners' attributes out of a mesh.
     *
     * @param mesh The mesh.
     * @param triangles The triangles.
     * @return The attributes, {@link Attributes#PER_TRIANGLE} ints a triangle, or {@link
     *     Attributes#NONE} if none of them is textured.
     */
    static int[] attributes(@NonNull LodMesh mesh, int @NonNull [] triangles) {
        boolean any = false;
        for (int triangle : triangles) {
            any |= mesh.isTextured(triangle);
        }
        if (!any) {
            return Attributes.NONE;
        }
        final int[] out = new int[triangles.length * Attributes.PER_TRIANGLE];
        for (int i = 0; i < triangles.length; ++i) {
            mesh.attributes(triangles[i], out, i * Attributes.PER_TRIANGLE);
        }
        return out;
    }

    /**
     * The surface some triangles cover.
     *
     * @param corners Their corners, {@link #TRIANGLE_VALUES} floats a triangle.
     * @return The area they add up to.
     */
    static double area(float @NonNull [] corners) {
        final double[] a = new double[3];
        final double[] b = new double[3];
        final double[] c = new double[3];
        double total = 0;
        for (int at = 0; at + TRIANGLE_VALUES <= corners.length; at += TRIANGLE_VALUES) {
            for (int axis = 0; axis < 3; ++axis) {
                a[axis] = corners[at + axis];
                b[axis] = corners[at + 3 + axis];
                c[axis] = corners[at + 6 + axis];
            }
            total += Geometry.area(a, b, c);
        }
        return total;
    }

    /**
     * A sphere around some corners: the center of their bounds, and the farthest corner from it.
     *
     * @param corners The corners, 3 floats each.
     * @return The sphere: x, y, z, radius.
     */
    static double[] tightSphere(float @NonNull [] corners) {
        final double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        final double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int i = 0; i + 2 < corners.length; i += AXES) {
            for (int axis = 0; axis < AXES; ++axis) {
                min[axis] = Math.min(min[axis], corners[i + axis]);
                max[axis] = Math.max(max[axis], corners[i + axis]);
            }
        }
        final double[] sphere = new double[LodDag.SPHERE_VALUES];
        for (int axis = 0; axis < AXES; ++axis) {
            sphere[axis] = 0.5 * (min[axis] + max[axis]);
        }
        double radius = 0;
        for (int i = 0; i + 2 < corners.length; i += AXES) {
            final double dx = corners[i] - sphere[0];
            final double dy = corners[i + 1] - sphere[1];
            final double dz = corners[i + 2] - sphere[2];
            radius = Math.max(radius, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        sphere[AXES] = radius;
        return sphere;
    }

    /**
     * A sphere holding other spheres: the center of their bounds, and the radius reaching the
     * farthest edge of any of them.
     *
     * @param spheres The spheres, x, y, z, radius each.
     * @return The enclosing sphere.
     */
    static double[] enclose(@NonNull List<double[]> spheres) {
        final double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        final double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (double[] s : spheres) {
            for (int axis = 0; axis < AXES; ++axis) {
                min[axis] = Math.min(min[axis], s[axis] - s[AXES]);
                max[axis] = Math.max(max[axis], s[axis] + s[AXES]);
            }
        }
        final double[] out = new double[LodDag.SPHERE_VALUES];
        for (int axis = 0; axis < AXES; ++axis) {
            out[axis] = 0.5 * (min[axis] + max[axis]);
        }
        double radius = 0;
        for (double[] s : spheres) {
            final double dx = s[0] - out[0];
            final double dy = s[1] - out[1];
            final double dz = s[2] - out[2];
            radius = Math.max(radius, Math.sqrt(dx * dx + dy * dy + dz * dz) + s[AXES]);
        }
        out[AXES] = radius;
        return out;
    }

    /** Static helpers only. */
    private Clusters() {}
}
