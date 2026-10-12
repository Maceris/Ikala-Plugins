package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tests for merging coplanar triangles without changing the surface. */
class PlanarMergeTest {

    /** How close areas must be, relative to the total. */
    private static final double AREA_TOLERANCE = 1e-6;

    /**
     * A grid of quads in the z = 0 plane, rotated and moved, two triangles per quad.
     *
     * @param n Quads along each side.
     * @param rotation A rotation, as a row-major 3x3 matrix.
     * @param material The material of each quad by its x and y, or null for all 0.
     * @return The mesh.
     */
    private static LodMesh grid(int n, double[] rotation, int[][] material) {
        final LodMesh.Builder builder = LodMesh.builder(new double[3]);
        final int[][] index = new int[n + 1][n + 1];
        for (int x = 0; x <= n; ++x) {
            for (int y = 0; y <= n; ++y) {
                final double[] p = rotate(rotation, new double[] {x, y, 0});
                index[x][y] = builder.vertex((float) p[0], (float) p[1], (float) p[2]);
            }
        }
        for (int x = 0; x < n; ++x) {
            for (int y = 0; y < n; ++y) {
                final int m = material == null ? 0 : material[x][y];
                builder.triangle(index[x][y], index[x + 1][y], index[x + 1][y + 1], m);
                builder.triangle(index[x][y], index[x + 1][y + 1], index[x][y + 1], m);
            }
        }
        return builder.build();
    }

    /**
     * Rotate a point.
     *
     * @param m The rotation, row major.
     * @param p The point.
     * @return The rotated point.
     */
    private static double[] rotate(double[] m, double[] p) {
        return new double[] {
            m[0] * p[0] + m[1] * p[1] + m[2] * p[2],
            m[3] * p[0] + m[4] * p[1] + m[5] * p[2],
            m[6] * p[0] + m[7] * p[1] + m[8] * p[2]
        };
    }

    /** No rotation. */
    private static final double[] IDENTITY = {1, 0, 0, 0, 1, 0, 0, 0, 1};

    /**
     * A rotation about an axis.
     *
     * @param angle The angle, in radians.
     * @param axis The axis, unit length.
     * @return The rotation, row major.
     */
    private static double[] rotation(double angle, double[] axis) {
        final double c = Math.cos(angle);
        final double s = Math.sin(angle);
        final double t = 1 - c;
        final double x = axis[0];
        final double y = axis[1];
        final double z = axis[2];
        return new double[] {
            t * x * x + c,
            t * x * y - s * z,
            t * x * z + s * y,
            t * x * y + s * z,
            t * y * y + c,
            t * y * z - s * x,
            t * x * z - s * y,
            t * y * z + s * x,
            t * z * z + c
        };
    }

    /**
     * The area of each material in each plane, the planes found by their unit normal and offset.
     *
     * @param mesh The mesh.
     * @return Area by class.
     */
    private static Map<String, Double> areas(LodMesh mesh) {
        final Map<String, Double> out = new HashMap<>();
        final double[] a = new double[3];
        final double[] b = new double[3];
        final double[] c = new double[3];
        final double[] n = new double[3];
        for (int t = 0; t < mesh.getTriangleCount(); ++t) {
            Geometry.corners(mesh, t, a, b, c);
            Geometry.normal(a, b, c, n);
            final double length = Math.sqrt(Geometry.dot(n, n));
            final double offset = Geometry.dot(n, a) / length;
            final String key =
                    mesh.material(t)
                            + " "
                            + round(n[0] / length)
                            + " "
                            + round(n[1] / length)
                            + " "
                            + round(n[2] / length)
                            + " "
                            + round(offset);
            out.merge(key, length / 2, Double::sum);
        }
        return out;
    }

    /**
     * Round to three places, with -0 as 0, for grouping by plane.
     *
     * @param value The value.
     * @return The rounded value.
     */
    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0 + 0.0;
    }

    /**
     * Check two meshes have the same area of each material in each plane.
     *
     * @param before The input.
     * @param after The merged mesh.
     */
    private static void assertSameAreas(LodMesh before, LodMesh after) {
        final Map<String, Double> expected = areas(before);
        final Map<String, Double> actual = areas(after);
        assertEquals(expected.keySet(), actual.keySet());
        double total = 0;
        for (double area : expected.values()) {
            total += area;
        }
        for (Map.Entry<String, Double> entry : expected.entrySet()) {
            assertEquals(
                    entry.getValue(),
                    actual.get(entry.getKey()),
                    AREA_TOLERANCE * total,
                    entry.getKey());
        }
    }

    /**
     * The open edges, by their corners' positions.
     *
     * @param mesh The mesh.
     * @return The open edges.
     */
    private static Set<String> openEdges(LodMesh mesh) {
        final Map<String, Integer> uses = new HashMap<>();
        for (int t = 0; t < mesh.getTriangleCount(); ++t) {
            for (int c = 0; c < 3; ++c) {
                final String p = point(mesh, mesh.corner(t, c));
                final String q = point(mesh, mesh.corner(t, (c + 1) % 3));
                final String key = p.compareTo(q) < 0 ? p + "|" + q : q + "|" + p;
                uses.merge(key, 1, Integer::sum);
            }
        }
        final Set<String> out = new HashSet<>();
        uses.forEach(
                (key, count) -> {
                    if (count == 1) {
                        out.add(key);
                    }
                });
        return out;
    }

    /**
     * A vertex's position as a key.
     *
     * @param mesh The mesh.
     * @param v The vertex.
     * @return The key.
     */
    private static String point(LodMesh mesh, int v) {
        return mesh.position(v, 0) + "," + mesh.position(v, 1) + "," + mesh.position(v, 2);
    }

    /**
     * Check a merge kept the surface: the same areas, the same open edges, nothing turned over.
     *
     * @param before The input.
     * @param after The merged mesh.
     */
    private static void assertSameSurface(LodMesh before, LodMesh after) {
        assertSameAreas(before, after);
        assertEquals(openEdges(before), openEdges(after), "The open edges stay as they were");
    }

    /** A flat grid keeps only its border, at any orientation. */
    @Test
    void testFlatGridKeepsOnlyItsBorder() {
        final int n = 8;
        for (double[] rotation :
                new double[][] {
                    IDENTITY, rotation(Math.toRadians(37), new double[] {0.48, 0.6, 0.64})
                }) {
            final LodMesh input = grid(n, rotation, null);
            final LodMesh merged = PlanarMerge.merge(input);
            assertSameSurface(input, merged);
            // Only the 4n border vertices stay, which a fan over them covers in 4n - 2 triangles
            assertEquals(4 * n, merged.getVertexCount());
            assertEquals(4 * n - 2, merged.getTriangleCount());
        }
    }

    /** Materials keep their areas and the corners of their borders. */
    @Test
    void testMaterialBordersStay() {
        final int n = 8;
        final int[][] material = new int[n][n];
        for (int x = 0; x < n; ++x) {
            for (int y = 0; y < n; ++y) {
                // An L of material 1 with a step in its border
                material[x][y] = (x < 3 || y < 2) ? 1 : 0;
            }
        }
        final LodMesh input = grid(n, IDENTITY, material);
        final LodMesh merged = PlanarMerge.merge(input);
        assertSameSurface(input, merged);
        assertTrue(merged.getTriangleCount() < input.getTriangleCount() / 2);
    }

    /**
     * Along a crease, vertices go where the line stays straight, and both planes keep their area.
     */
    @Test
    void testCreaseMerges() {
        final int n = 6;
        final LodMesh.Builder builder = LodMesh.builder(new double[3]);
        // A floor and a wall meeting along the x axis
        final int[][] floor = new int[n + 1][n + 1];
        final int[][] wall = new int[n + 1][n + 1];
        for (int x = 0; x <= n; ++x) {
            for (int k = 0; k <= n; ++k) {
                floor[x][k] = builder.vertex(x, 0, k);
                wall[x][k] = builder.vertex(x, k, 0);
            }
        }
        for (int x = 0; x < n; ++x) {
            for (int k = 0; k < n; ++k) {
                builder.triangle(floor[x][k], floor[x][k + 1], floor[x + 1][k + 1], 0);
                builder.triangle(floor[x][k], floor[x + 1][k + 1], floor[x + 1][k], 0);
                builder.triangle(wall[x][k], wall[x + 1][k], wall[x + 1][k + 1], 0);
                builder.triangle(wall[x][k], wall[x + 1][k + 1], wall[x][k + 1], 0);
            }
        }
        final LodMesh input = builder.build();
        final LodMesh merged = PlanarMerge.merge(input);
        assertSameSurface(input, merged);
        assertTrue(merged.getTriangleCount() < input.getTriangleCount() / 4);
    }

    /** A curved surface has nothing to merge. */
    @Test
    void testSphereStays() {
        final LodMesh.Builder builder = LodMesh.builder(new double[3]);
        final int rings = 12;
        final int segments = 16;
        final int[][] index = new int[rings + 1][segments];
        for (int r = 0; r <= rings; ++r) {
            final double phi = Math.PI * r / rings;
            for (int s = 0; s < segments; ++s) {
                final double theta = 2 * Math.PI * s / segments;
                index[r][s] =
                        builder.vertex(
                                (float) (Math.sin(phi) * Math.cos(theta)),
                                (float) Math.cos(phi),
                                (float) (Math.sin(phi) * Math.sin(theta)));
            }
        }
        for (int r = 0; r < rings; ++r) {
            for (int s = 0; s < segments; ++s) {
                final int t = (s + 1) % segments;
                builder.triangle(index[r][s], index[r + 1][s], index[r + 1][t], 0);
                builder.triangle(index[r][s], index[r + 1][t], index[r][t], 0);
            }
        }
        final LodMesh input = builder.build();
        final LodMesh merged = PlanarMerge.merge(input);
        assertEquals(input.getTriangleCount(), merged.getTriangleCount());
    }

    /** Locked vertices stay, even inside a flat region. */
    @Test
    void testLockedVerticesStay() {
        final LodMesh input = grid(4, IDENTITY, null);
        final LodMesh.Builder builder = LodMesh.builder(new double[3]);
        final int[] remap = new int[input.getVertexCount()];
        for (int v = 0; v < remap.length; ++v) {
            remap[v] = builder.vertex(input.position(v, 0), input.position(v, 1), 0);
        }
        builder.lock(builder.find(2, 2, 0));
        for (int t = 0; t < input.getTriangleCount(); ++t) {
            builder.triangle(
                    remap[input.corner(t, 0)],
                    remap[input.corner(t, 1)],
                    remap[input.corner(t, 2)],
                    0);
        }
        final LodMesh merged = PlanarMerge.merge(builder.build());
        boolean kept = false;
        for (int v = 0; v < merged.getVertexCount(); ++v) {
            kept |= merged.position(v, 0) == 2 && merged.position(v, 1) == 2;
        }
        assertTrue(kept);
    }

    /** Terrain sections keep every plane and material exactly, with far fewer triangles. */
    @Test
    void testTerrainSurfaceUnchanged() {
        int before = 0;
        int after = 0;
        final List<LodMesh> sections = new ArrayList<>(DagTest.terrain(2));
        for (LodMesh input : sections) {
            final LodMesh merged = PlanarMerge.merge(input);
            assertSameSurface(input, merged);
            assertTrue(Hausdorff.between(merged, input, 0.25) < 1e-4, "The same surface");
            before += input.getTriangleCount();
            after += merged.getTriangleCount();
        }
        System.out.printf("Terrain leaves: %,d triangles merged to %,d%n", before, after);
        assertTrue(after < before, before + " to " + after);
    }
}
