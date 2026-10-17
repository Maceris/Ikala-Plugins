package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests for measuring the distance between meshes. */
class HausdorffTest {

    /** How far apart samples are in these tests. */
    private static final double SPACING = 0.25;

    /** Quads along each side of the test grids. */
    private static final int SIZE = 4;

    /**
     * A flat square grid on the xz plane, facing up, at a height.
     *
     * @param builder Where it goes.
     * @param height Its y.
     */
    private static void grid(LodMesh.Builder builder, float height) {
        for (int x = 0; x < SIZE; ++x) {
            for (int z = 0; z < SIZE; ++z) {
                final int a = builder.vertex(x, height, z);
                final int b = builder.vertex(x + 1, height, z);
                final int c = builder.vertex(x + 1, height, z + 1);
                final int d = builder.vertex(x, height, z + 1);
                builder.triangle(a, d, c, 0);
                builder.triangle(a, c, b, 0);
            }
        }
    }

    /**
     * A flat grid.
     *
     * @param height Its y.
     * @return The mesh.
     */
    private static LodMesh flat(float height) {
        final LodMesh.Builder builder = LodMesh.builder(new double[3]);
        grid(builder, height);
        return builder.build();
    }

    /**
     * A flat grid with a thin spike standing on it.
     *
     * @param tip The spike's height.
     * @param visibility How visible the spike is.
     * @return The mesh.
     */
    private static LodMesh spiked(float tip, float visibility) {
        final LodMesh.Builder builder = LodMesh.builder(new double[3]);
        grid(builder, 0);
        final int a = builder.vertex(1, 0, 1);
        final int b = builder.vertex(1.5f, 0, 1);
        final int top = builder.vertex(1.25f, tip, 1);
        builder.triangle(a, b, top, 0, visibility);
        builder.triangle(b, a, top, 0, visibility);
        return builder.build();
    }

    /** A mesh is no distance from itself. */
    @Test
    void testSameMeshIsNoDistance() {
        assertEquals(0, Hausdorff.between(flat(0), flat(0), SPACING), 1e-9);
    }

    /** A plane is as far from a parallel one as they are apart. */
    @Test
    void testParallelPlanes() {
        assertEquals(0.75, Hausdorff.between(flat(0.75f), flat(0), SPACING), 1e-6);
    }

    /** Removing a visible spike costs its height, and a hidden one nothing. */
    @Test
    void testRemovedInputCountsOnlyIfVisible() {
        assertEquals(3, Hausdorff.between(flat(0), spiked(3, 1), SPACING), 1e-6);
        assertEquals(0, Hausdorff.between(flat(0), spiked(3, 0), SPACING), 1e-9);
    }

    /** A large triangle bridging a gap is sampled over the gap, not only at its corners. */
    @Test
    void testBridgeOverAGapIsSampled() {
        // Two strips with a gap between them, and one wide quad over both
        final LodMesh.Builder input = LodMesh.builder(new double[3]);
        final int a = input.vertex(0, 0, 0);
        final int b = input.vertex(1, 0, 0);
        final int c = input.vertex(1, 0, 4);
        final int d = input.vertex(0, 0, 4);
        input.triangle(a, d, c, 0);
        input.triangle(a, c, b, 0);
        final int e = input.vertex(3, 0, 0);
        final int f = input.vertex(4, 0, 0);
        final int g = input.vertex(4, 0, 4);
        final int h = input.vertex(3, 0, 4);
        input.triangle(e, h, g, 0);
        input.triangle(e, g, f, 0);
        final LodMesh.Builder bridge = LodMesh.builder(new double[3]);
        final int p = bridge.vertex(0, 0, 0);
        final int q = bridge.vertex(4, 0, 0);
        final int r = bridge.vertex(4, 0, 4);
        final int s = bridge.vertex(0, 0, 4);
        bridge.triangle(p, s, r, 0);
        bridge.triangle(p, r, q, 0);
        // The middle of the gap is a block from either strip
        assertEquals(1, Hausdorff.between(bridge.build(), input.build(), SPACING), 0.05);
    }

    /**
     * A free part taken away costs its width, however far it floated from what is left, while an
     * attached spike, which is no part of its own, still costs its height.
     */
    @Test
    void testRemovedFreePartCostsItsWidth() {
        final LodMesh.Builder builder = LodMesh.builder(new double[3]);
        grid(builder, 0);
        // A thin strip floating 2 above the grid, 0.1 wide and 2 long, on a slant
        final int a = builder.vertex(1, 2, 1);
        final int b = builder.vertex(1.1f, 2, 1);
        final int c = builder.vertex(2.1f, 2, 3);
        final int d = builder.vertex(2, 2, 3);
        builder.triangle(a, d, c, 0);
        builder.triangle(a, c, b, 0);
        assertEquals(0.1, Hausdorff.between(flat(0), builder.build(), SPACING), 0.02);
        assertEquals(3, Hausdorff.between(flat(0), spiked(3, 1), SPACING), 1e-6);
    }

    /** A part's width is the middle side of a box along its principal axes. */
    @Test
    void testPartWidths() {
        // A flat panel is as wide as its shorter side, slanted or not
        final LodMesh.Builder builder = LodMesh.builder(new double[3]);
        final int a = builder.vertex(0, 0, 0);
        final int b = builder.vertex(2, 2, 0);
        final int c = builder.vertex(2, 2, 1);
        final int d = builder.vertex(0, 0, 1);
        builder.triangle(a, b, c, 0);
        builder.triangle(a, c, d, 0);
        final LodMesh panel = builder.build();
        final int[] part = VirtualEdges.components(panel);
        assertEquals(1, Geometry.partWidths(panel, part)[part[a]], 1e-6);
        // A grid of 4 by 4
        final LodMesh grid = flat(0);
        final int[] gridPart = VirtualEdges.components(grid);
        assertEquals(SIZE, Geometry.partWidths(grid, gridPart)[gridPart[0]], 1e-6);
    }
}
