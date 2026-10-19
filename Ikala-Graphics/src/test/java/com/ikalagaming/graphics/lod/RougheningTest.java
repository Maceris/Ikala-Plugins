package com.ikalagaming.graphics.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for widening roughness where normals were blended away. */
class RougheningTest {

    /** A surface's own roughness, for the tests. */
    private static final float ROUGHNESS = 0.2f;

    /** The test look: a color, the roughness, not metal, the default specular. */
    private static final float[] LOOK = {0.5f, 0.5f, 0.5f, ROUGHNESS, 0, 0.5f};

    /** How wide the test surfaces are. */
    private static final float SIDE = 4;

    /** Ridges across the bumpy test surface. */
    private static final int RIDGES = 16;

    /** How high its ridges rise. */
    private static final float RIDGE_HEIGHT = 0.1f;

    /** How far around each corner the input is blended from. */
    private static final double FOOTPRINT = 1;

    /**
     * Normals that agree leave the roughness as it was; ones that differ widen it, the more the
     * more they differ, until it is 1.
     */
    @Test
    void testWidensWithSpread() {
        assertEquals(ROUGHNESS, Roughening.widen(ROUGHNESS, 1), 1e-3);
        double last = ROUGHNESS;
        for (double length : new double[] {0.99, 0.9, 0.7, 0.4, 0.1}) {
            final double widened = Roughening.widen(ROUGHNESS, length);
            assertTrue(
                    widened > last || widened == 1,
                    length + " gives " + widened + " after " + last);
            assertTrue(widened <= 1, length + " gives " + widened);
            last = widened;
        }
        assertEquals(1, Roughening.widen(ROUGHNESS, 0));
    }

    /**
     * A ridged surface flattened far out comes out rougher than its material, while a flat one
     * keeps its material's roughness.
     */
    @Test
    void testFlattenedBumpsComeOutRougher() {
        assertEquals(ROUGHNESS, transferredRoughness(0), 0.01);
        final double ridged = transferredRoughness(RIDGE_HEIGHT);
        assertTrue(ridged > ROUGHNESS + 0.05, "Ridged input came out " + ridged);
    }

    /**
     * The mean roughness a flat square takes from an input surface with ridges of a height.
     *
     * @param height How high the input's ridges rise, 0 for flat.
     * @return The mean roughness of the square's corners.
     */
    private static double transferredRoughness(float height) {
        final AverageTransfer transfer = new AverageTransfer(null);
        final float step = SIDE / RIDGES;
        for (int i = 0; i < RIDGES; ++i) {
            final float x0 = i * step;
            final float x1 = x0 + step;
            // Up one side of a ridge, or down it, along x, all the way along z
            final float y0 = i % 2 == 0 ? 0 : height;
            final float y1 = i % 2 == 0 ? height : 0;
            transfer.add(new float[] {x0, y0, 0, x0, y0, SIDE, x1, y1, SIDE}, 0, 1, 0, LOOK);
            transfer.add(new float[] {x0, y0, 0, x1, y1, SIDE, x1, y1, 0}, 0, 1, 0, LOOK);
        }
        final LodMesh.Builder builder = LodMesh.builder(new double[3]);
        final int a = builder.vertex(1, 0, 1);
        final int b = builder.vertex(1, 0, 3);
        final int c = builder.vertex(3, 0, 3);
        final int d = builder.vertex(3, 0, 1);
        builder.triangle(a, b, c, 0);
        builder.triangle(a, c, d, 0);
        final int[] attributes = transfer.transfer(builder.build(), FOOTPRINT);
        final float[] values = new float[MaterialAverages.VALUES];
        double sum = 0;
        int corners = 0;
        for (int at = 0; at < attributes.length; at += Attributes.VALUES) {
            Attributes.averageOf(attributes, at, values);
            sum += values[MaterialAverages.ROUGHNESS];
            ++corners;
        }
        return sum / corners;
    }
}
