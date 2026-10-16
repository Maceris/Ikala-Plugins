package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

/** Tests for finding seams and simplifying along them. */
class SeamsTest {

    /** Texture coordinates added past the cut, so the two sides don't meet in texture space. */
    private static final float JUMP = 10;

    /**
     * A flat grid in the xz plane, facing up, whose texture coordinates jump at a column: a seam
     * from one side of the grid to the other. Every triangle is textured.
     *
     * @param size Squares along each side.
     * @param cut The column where the texture coordinates jump.
     * @param jitter How far, in degrees, the normals of the right side lean, to check tolerances.
     * @param jump Whether the texture coordinates jump at the column.
     * @return The mesh.
     */
    static LodMesh cutGrid(int size, int cut, double jitter, boolean jump) {
        final LodMesh.Builder builder = LodMesh.builder(new double[3]);
        final int[][] index = new int[size + 1][size + 1];
        for (int x = 0; x <= size; ++x) {
            for (int z = 0; z <= size; ++z) {
                index[x][z] = builder.vertex(x, 0, z);
            }
        }
        for (int x = 0; x < size; ++x) {
            for (int z = 0; z < size; ++z) {
                final boolean right = x >= cut;
                final int[][] corners = {
                    {x, z}, {x, z + 1}, {x + 1, z}, {x + 1, z}, {x, z + 1}, {x + 1, z + 1}
                };
                for (int t = 0; t < 2; ++t) {
                    final int[] attributes = new int[Attributes.PER_TRIANGLE];
                    for (int c = 0; c < 3; ++c) {
                        final int[] at = corners[t * 3 + c];
                        corner(
                                attributes,
                                c,
                                at[0] + (right && jump ? JUMP : 0),
                                at[1],
                                right ? jitter : 0);
                    }
                    builder.triangle(
                            index[corners[t * 3][0]][corners[t * 3][1]],
                            index[corners[t * 3 + 1][0]][corners[t * 3 + 1][1]],
                            index[corners[t * 3 + 2][0]][corners[t * 3 + 2][1]],
                            0,
                            1,
                            attributes);
                }
            }
        }
        return builder.build();
    }

    /**
     * Write one corner's attributes: facing up, leaning a little about z, tangent along x.
     *
     * @param out Where they go.
     * @param corner Which corner.
     * @param u The u texture coordinate.
     * @param v The v texture coordinate.
     * @param lean How far the normal leans, in degrees.
     */
    private static void corner(int[] out, int corner, float u, float v, double lean) {
        final double radians = Math.toRadians(lean);
        final int at = corner * Attributes.VALUES;
        out[at] =
                Attributes.encodeDirection(
                        new float[] {(float) Math.sin(radians), (float) Math.cos(radians), 0});
        out[at + 1] = Attributes.encodeDirection(new float[] {1, 0, 0}) & ~1;
        out[at + 2] = (Float.floatToFloat16(u) & 0xFFFF) | (Float.floatToFloat16(v) << Short.SIZE);
    }

    /**
     * Every triangle's kind and attributes, as the builder gives {@link Seams#find}.
     *
     * @param mesh The mesh.
     * @return The attributes.
     */
    private static int[] attributesOf(LodMesh mesh) {
        final int[] out = new int[mesh.getTriangleCount() * Attributes.PER_TRIANGLE];
        for (int t = 0; t < mesh.getTriangleCount(); ++t) {
            mesh.attributes(t, out, t * Attributes.PER_TRIANGLE);
        }
        return out;
    }

    /**
     * Find a textured mesh's seams.
     *
     * @param mesh The mesh.
     * @return The seams.
     */
    private static Seams seamsOf(LodMesh mesh) {
        final int[] kinds = new int[mesh.getTriangleCount()];
        Arrays.fill(kinds, Seams.KIND_TEXTURED);
        return Seams.find(mesh, kinds, attributesOf(mesh));
    }

    /**
     * A vertex by where it is.
     *
     * @param mesh The mesh.
     * @param x Its x.
     * @param z Its z.
     * @return The vertex.
     */
    private static int at(LodMesh mesh, float x, float z) {
        for (int v = 0; v < mesh.getVertexCount(); ++v) {
            if (mesh.position(v, 0) == x && mesh.position(v, 2) == z) {
                return v;
            }
        }
        throw new AssertionError("No vertex at " + x + ", " + z);
    }

    @Test
    void testASeamAcrossTheGrid() {
        final LodMesh mesh = cutGrid(4, 2, 0, true);
        final Seams seams = seamsOf(mesh);
        // The column x = 2 from z = 0 to 4: four seam edges
        assertEquals(4, seams.edges.length);
        // Its ends are on the grid's open border, so they stay; the middle may slide
        assertTrue(seams.isCorner(at(mesh, 2, 0)));
        assertTrue(seams.isCorner(at(mesh, 2, 4)));
        for (int z = 1; z < 4; ++z) {
            assertFalse(seams.isCorner(at(mesh, 2, z)));
        }
        assertFalse(seams.isCorner(at(mesh, 1, 1)));
        // Two charts, one each side
        final int left = seams.charts[0];
        int[] count = new int[2];
        for (int t = 0; t < mesh.getTriangleCount(); ++t) {
            final boolean right =
                    mesh.position(mesh.corner(t, 0), 0) >= 2
                            && mesh.position(mesh.corner(t, 1), 0) >= 2
                            && mesh.position(mesh.corner(t, 2), 0) >= 2;
            if (right) {
                assertNotEquals(left, seams.charts[t]);
                ++count[1];
            } else {
                assertEquals(left, seams.charts[t]);
                ++count[0];
            }
        }
        assertEquals(16, count[0]);
        assertEquals(16, count[1]);
    }

    @Test
    void testNoiseMakesNoSeam() {
        // With continuous texture coordinates, normals half a degree apart are the same, and ten
        // degrees apart make a seam: a hard edge
        assertEquals(0, seamsOf(cutGrid(4, 2, 0.5, false)).edges.length);
        assertEquals(4, seamsOf(cutGrid(4, 2, 10, false)).edges.length);
    }

    @Test
    void testSimplifyingAlongTheSeam() {
        final int size = 12;
        final int cut = 6;
        final LodMesh mesh = cutGrid(size, cut, 0, true);
        final Seams seams = seamsOf(mesh);
        for (int v = 0; v < mesh.getVertexCount(); ++v) {
            if (seams.isCorner(v)) {
                mesh.setLocked(v, true);
            }
        }
        final Simplifier.Result result =
                Simplifier.simplify(
                        mesh, Simplifier.Options.toCount(8, 0).followingSeams(seams.edges));
        final LodMesh out = result.mesh();
        // The seam's middle vertices were free to go, so it got far below locking them all
        assertTrue(out.getTriangleCount() <= 8, "Got " + out.getTriangleCount());
        for (int t = 0; t < out.getTriangleCount(); ++t) {
            // Every triangle stays on its side of the cut, and in the chart it started in
            boolean left = true;
            boolean right = true;
            for (int c = 0; c < 3; ++c) {
                final float x = out.position(out.corner(t, c), 0);
                left &= x <= cut + 1e-4f;
                right &= x >= cut - 1e-4f;
            }
            assertTrue(left || right, "Triangle " + t + " crosses the seam");
            final int chart = seams.charts[result.faceIds()[t]];
            assertEquals(right && !left, chart != seams.charts[0]);
        }
        // Whatever is on the seam's line is still on it
        for (int v = 0; v < out.getVertexCount(); ++v) {
            final float x = out.position(v, 0);
            if (Math.abs(x - cut) < 1e-3f) {
                assertEquals(cut, x, 1e-5f);
            }
        }
    }
}
