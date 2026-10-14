package com.ikalagaming.graphics.graph;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Random;

/** Tests for the math of volume bricks. */
class VolumeMathTest {

    /**
     * A closed cube's surfaces, made a volume, block a ray through it on average as much as the
     * surfaces do: the mean chord of a unit cube is 2/3, and every chord crosses two faces.
     */
    @Test
    void testClosedCubeBlocksLikeItsFaces() {
        final double opacity = 0.7;
        final double sigma = VolumeMath.extinction(6, opacity, 1);
        final double meanChord = 4.0 / 6.0;
        assertEquals(2 * VolumeMath.opticalDepth(opacity), sigma * meanChord, 1e-12);
    }

    /**
     * Random lines through a unit cube cross a plane through its middle as often as the extinction
     * says: the optical depth along the chords, on average, matches the crossings.
     */
    @Test
    void testPlaneCrossingsMatchByMonteCarlo() {
        final Random random = new Random(7);
        long crossings = 0;
        double chords = 0;
        final int lines = 2_000_000;
        int counted = 0;
        while (counted < lines) {
            // Isotropic uniform random lines (a point in a big box, a uniform direction)
            final double[] p = {
                random.nextDouble() * 3 - 1,
                random.nextDouble() * 3 - 1,
                random.nextDouble() * 3 - 1
            };
            final double z = random.nextDouble() * 2 - 1;
            final double phi = random.nextDouble() * 2 * Math.PI;
            final double r = Math.sqrt(1 - z * z);
            final double[] d = {r * Math.cos(phi), r * Math.sin(phi), z};
            double near = Double.NEGATIVE_INFINITY;
            double far = Double.POSITIVE_INFINITY;
            for (int axis = 0; axis < 3; ++axis) {
                if (Math.abs(d[axis]) < 1e-12) {
                    if (p[axis] < 0 || p[axis] > 1) {
                        near = 1;
                        far = 0;
                    }
                    continue;
                }
                final double t0 = (0 - p[axis]) / d[axis];
                final double t1 = (1 - p[axis]) / d[axis];
                near = Math.max(near, Math.min(t0, t1));
                far = Math.min(far, Math.max(t0, t1));
            }
            if (near >= far) {
                continue;
            }
            ++counted;
            chords += far - near;
            // The plane x = 0.5 inside the cube
            final double xNear = p[0] + d[0] * near;
            final double xFar = p[0] + d[0] * far;
            if ((xNear - 0.5) * (xFar - 0.5) < 0) {
                ++crossings;
            }
        }
        final double perMeter = crossings / chords;
        // Area 1 in volume 1: half a crossing per meter
        assertEquals(0.5, perMeter, 0.01);
        assertEquals(
                VolumeMath.extinction(1, 0.5, 1), perMeter * VolumeMath.opticalDepth(0.5), 0.01);
    }

    /** Stored extinction reads back close, finer near zero, and saturates at the top. */
    @Test
    void testEncodeRoundTrip() {
        for (double sigma : new double[] {0, 0.01, 0.1, 1, 3, 8, 16}) {
            final double back = VolumeMath.decode(VolumeMath.encode(sigma));
            final double levels = VolumeMath.MAX_SEE_THROUGH;
            final double step =
                    2
                                    * Math.sqrt(sigma / VolumeMath.MAX_EXTINCTION)
                                    / levels
                                    * VolumeMath.MAX_EXTINCTION
                            + VolumeMath.MAX_EXTINCTION / (levels * levels);
            assertEquals(sigma, back, step, "sigma " + sigma);
            assertFalse(VolumeMath.isSolid(VolumeMath.encode(sigma)));
        }
        assertEquals(VolumeMath.MAX_SEE_THROUGH, VolumeMath.encode(100));
        assertEquals(0, VolumeMath.encode(-1));
    }

    /** Solid voxels sit above every see-through value, and keep their coverage. */
    @Test
    void testSolidRoundTrip() {
        for (double coverage : new double[] {0, 0.25, 0.6, 1}) {
            final int stored = VolumeMath.encodeSolid(coverage);
            assertTrue(VolumeMath.isSolid(stored));
            assertEquals(coverage, VolumeMath.coverage(stored), 0.5 / 127, "coverage " + coverage);
        }
        assertEquals(VolumeMath.FIRST_SOLID, VolumeMath.encodeSolid(-1));
        assertEquals(VolumeMath.MAX_STORED, VolumeMath.encodeSolid(2));
    }

    /** Opacity is capped, so even a solid surface gives finite extinction. */
    @Test
    void testOpacityCapped() {
        assertTrue(Double.isFinite(VolumeMath.opticalDepth(1)));
        assertEquals(VolumeMath.opticalDepth(VolumeMath.MAX_OPACITY), VolumeMath.opticalDepth(2));
    }

    /** Slots tile the atlas without overlapping. */
    @Test
    void testSlotOrigins() {
        assertArrayEquals(new int[] {0, 0, 0}, VolumeMath.slotOrigin(0));
        assertArrayEquals(new int[] {VolumeMath.BRICK, 0, 0}, VolumeMath.slotOrigin(1));
        assertArrayEquals(
                new int[] {0, VolumeMath.BRICK, 0}, VolumeMath.slotOrigin(VolumeMath.SLOTS_X));
        assertArrayEquals(
                new int[] {0, 0, VolumeMath.BRICK},
                VolumeMath.slotOrigin(VolumeMath.SLOTS_X * VolumeMath.SLOTS_Y));
        final int[] last = VolumeMath.slotOrigin(VolumeMath.SLOTS - 1);
        assertArrayEquals(
                new int[] {
                    (VolumeMath.SLOTS_X - 1) * VolumeMath.BRICK,
                    (VolumeMath.SLOTS_Y - 1) * VolumeMath.BRICK,
                    (VolumeMath.SLOTS_Z - 1) * VolumeMath.BRICK
                },
                last);
    }
}
