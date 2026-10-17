package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for telling macro-surfaces from what is finer than a resolution. */
class MacroSurfacesTest {

    /** The resolution the tests ask at. */
    private static final double RADIUS = 1;

    /** Cells along each side of the test grid. */
    private static final int GRID = 8;

    /** How many sides a tube has around. */
    private static final int TUBE_SIDES = 12;

    /** How many rings a tube has along. */
    private static final int TUBE_RINGS = 32;

    /** How long a tube or strip is. */
    private static final float LENGTH = 8;

    /**
     * A flat grid of unit cells on the xz plane.
     *
     * @return The mesh.
     */
    private static LodMesh grid() {
        final LodMesh.Builder builder = LodMesh.builder(new double[3]);
        for (int x = 0; x < GRID; ++x) {
            for (int z = 0; z < GRID; ++z) {
                final int a = builder.vertex(x, 0, z);
                final int b = builder.vertex(x + 1, 0, z);
                final int c = builder.vertex(x + 1, 0, z + 1);
                final int d = builder.vertex(x, 0, z + 1);
                builder.triangle(a, d, c, 0);
                builder.triangle(a, c, b, 0);
            }
        }
        return builder.build();
    }

    /**
     * A tube along x.
     *
     * @param radius Its radius.
     * @return The mesh.
     */
    private static LodMesh tube(double radius) {
        final LodMesh.Builder builder = LodMesh.builder(new double[3]);
        final int[][] v = new int[TUBE_RINGS + 1][TUBE_SIDES];
        for (int r = 0; r <= TUBE_RINGS; ++r) {
            for (int s = 0; s < TUBE_SIDES; ++s) {
                final double angle = 2 * Math.PI * s / TUBE_SIDES;
                v[r][s] =
                        builder.vertex(
                                LENGTH * r / TUBE_RINGS,
                                (float) (radius * Math.cos(angle)),
                                (float) (radius * Math.sin(angle)));
            }
        }
        for (int r = 0; r < TUBE_RINGS; ++r) {
            for (int s = 0; s < TUBE_SIDES; ++s) {
                final int n = (s + 1) % TUBE_SIDES;
                builder.triangle(v[r][s], v[r + 1][s], v[r + 1][n], 0);
                builder.triangle(v[r][s], v[r + 1][n], v[r][n], 0);
            }
        }
        return builder.build();
    }

    /**
     * A flat strip along x, of a width.
     *
     * @param width Its width.
     * @return The mesh.
     */
    private static LodMesh strip(float width) {
        final LodMesh.Builder builder = LodMesh.builder(new double[3]);
        for (int r = 0; r < TUBE_RINGS; ++r) {
            final float x0 = LENGTH * r / TUBE_RINGS;
            final float x1 = LENGTH * (r + 1) / TUBE_RINGS;
            final int a = builder.vertex(x0, 0, 0);
            final int b = builder.vertex(x1, 0, 0);
            final int c = builder.vertex(x1, 0, width);
            final int d = builder.vertex(x0, 0, width);
            builder.triangle(a, d, c, 0);
            builder.triangle(a, c, b, 0);
        }
        return builder.build();
    }

    /**
     * A closed cube.
     *
     * @param side Its side.
     * @return The mesh.
     */
    private static LodMesh cube(float side) {
        final LodMesh.Builder builder = LodMesh.builder(new double[3]);
        final int[] v = new int[8];
        for (int i = 0; i < 8; ++i) {
            v[i] =
                    builder.vertex(
                            (i & 1) != 0 ? side : 0,
                            (i & 2) != 0 ? side : 0,
                            (i & 4) != 0 ? side : 0);
        }
        final int[][] faces = {
            {0, 2, 3, 1}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 4, 6, 2}, {1, 3, 7, 5}
        };
        for (int[] f : faces) {
            builder.triangle(v[f[0]], v[f[1]], v[f[2]], 0);
            builder.triangle(v[f[0]], v[f[2]], v[f[3]], 0);
        }
        return builder.build();
    }

    /**
     * Whether every triangle has a mark.
     *
     * @param marks The marks.
     * @param value The mark.
     * @return True if all of them do.
     */
    private static boolean all(boolean[] marks, boolean value) {
        for (boolean mark : marks) {
            if (mark != value) {
                return false;
            }
        }
        return true;
    }

    /** A flat surface wider than the resolution is a macro-surface everywhere, edges too. */
    @Test
    void testFlatGridIsMacro() {
        assertTrue(all(MacroSurfaces.classify(grid(), RADIUS), true));
    }

    /**
     * A flat square made of long thin triangles, fanned from one corner to every point along the
     * far sides, as merging coplanar triangles leaves walls, is still a macro-surface.
     */
    @Test
    void testFannedSquareIsMacro() {
        final LodMesh.Builder builder = LodMesh.builder(new double[3]);
        final int corner = builder.vertex(0, 0, 0);
        int last = builder.vertex(GRID, 0, 0);
        for (int i = 1; i <= 2 * GRID; ++i) {
            final float x = i <= GRID ? GRID : 2 * GRID - i;
            final float z = i <= GRID ? i : GRID;
            final int next = builder.vertex(x, 0, z);
            builder.triangle(corner, next, last, 0);
            last = next;
        }
        assertTrue(all(MacroSurfaces.classify(builder.build(), RADIUS), true));
    }

    /**
     * A wall of separate tiles, each smaller than the resolution and sharing no edge with the next,
     * is still a macro-surface, as models build walls from panels.
     */
    @Test
    void testTiledWallIsMacro() {
        final LodMesh.Builder builder = LodMesh.builder(new double[3]);
        final float tile = 0.45f;
        for (int x = 0; x < GRID * 2; ++x) {
            for (int z = 0; z < GRID * 2; ++z) {
                final float x0 = x * 0.5f;
                final float z0 = z * 0.5f;
                final int a = builder.vertex(x0, 0, z0);
                final int b = builder.vertex(x0 + tile, 0, z0);
                final int c = builder.vertex(x0 + tile, 0, z0 + tile);
                final int d = builder.vertex(x0, 0, z0 + tile);
                builder.triangle(a, d, c, 0);
                builder.triangle(a, c, b, 0);
            }
        }
        assertTrue(all(MacroSurfaces.classify(builder.build(), RADIUS), true));
    }

    /** A strip or a tube thinner than the resolution crosses the sphere twice: finer than it. */
    @Test
    void testThinStripsAndTubesAreFiner() {
        assertTrue(all(MacroSurfaces.classify(strip(0.1f), RADIUS), false));
        assertTrue(all(MacroSurfaces.classify(tube(0.1), RADIUS), false));
    }

    /** A tube much wider than the resolution is a surface again. */
    @Test
    void testWideTubeIsMacro() {
        assertTrue(all(MacroSurfaces.classify(tube(4), RADIUS), true));
    }

    /** A free part smaller than the resolution never reaches the sphere: finer than it. */
    @Test
    void testSmallPartIsFiner() {
        assertTrue(all(MacroSurfaces.classify(cube(0.3f), RADIUS), false));
    }

    /** At no resolution, everything is a macro-surface. */
    @Test
    void testNoResolutionKeepsAll() {
        assertTrue(all(MacroSurfaces.classify(cube(0.3f), 0), true));
    }
}
