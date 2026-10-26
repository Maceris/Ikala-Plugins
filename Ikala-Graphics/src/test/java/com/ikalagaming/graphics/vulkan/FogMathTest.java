package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The math of volumetric fog, against cases with known answers. */
class FogMathTest {

    /** Slices of a view distance, as the froxel volume has them. */
    private static double[] sliceLengths(double viewDistance) {
        final float logScale = VoxelOitMath.logScale(viewDistance);
        final double[] lengths = new double[VoxelOitMath.SLICES];
        for (int slice = 0; slice < lengths.length; ++slice) {
            lengths[slice] =
                    FogMath.sliceDepth(slice + 1, logScale) - FogMath.sliceDepth(slice, logScale);
        }
        return lengths;
    }

    /** Slice depths undo the slice coordinate, from the first depth to the view distance. */
    @Test
    void testSliceDepthUndoesSliceCoordinate() {
        final float logScale = VoxelOitMath.logScale(500);
        assertEquals(VoxelOitMath.FIRST_DEPTH, FogMath.sliceDepth(0, logScale), 1e-9);
        assertEquals(500, FogMath.sliceDepth(VoxelOitMath.SLICES, logScale), 0.01);
        for (double depth : new double[] {1, 7.5, 42, 300}) {
            final double slice = VoxelOitMath.sliceCoordinate((float) depth, logScale);
            assertEquals(depth, FogMath.sliceDepth(slice, logScale), depth * 1e-5);
        }
    }

    /**
     * Even fog with no light lets e^(-sigma d) through to each depth, and with light s per meter
     * gathers s / sigma (1 - e^(-sigma d)), however long the slices are.
     */
    @Test
    void testEvenFogMatchesTheClosedForm() {
        final double sigma = 0.02;
        final double light = 3;
        final double[] lengths = sliceLengths(1000);
        final int n = lengths.length;
        final double[] extinction = new double[n];
        final double[] scattered = new double[n * FogMath.CHANNELS];
        java.util.Arrays.fill(extinction, sigma);
        java.util.Arrays.fill(scattered, light);
        final double[] gathered = new double[n * FogMath.CHANNELS];
        final double[] through = new double[n];
        FogMath.integrate(scattered, extinction, lengths, gathered, through);
        double depth = VoxelOitMath.FIRST_DEPTH;
        for (int slice = 0; slice < n; ++slice) {
            depth += lengths[slice];
            final double d = depth - VoxelOitMath.FIRST_DEPTH;
            assertEquals(Math.exp(-sigma * d), through[slice], 1e-9);
            for (int c = 0; c < FogMath.CHANNELS; ++c) {
                assertEquals(
                        light / sigma * (1 - Math.exp(-sigma * d)),
                        gathered[slice * FogMath.CHANNELS + c],
                        1e-6);
            }
        }
    }

    /** Clear media gather light by length, and let everything through. */
    @Test
    void testClearMediaGatherByLength() {
        final double[] lengths = {1, 2, 4};
        final double[] gathered = new double[9];
        final double[] through = new double[3];
        FogMath.integrate(
                new double[] {1, 1, 1, 1, 1, 1, 1, 1, 1},
                new double[3],
                lengths,
                gathered,
                through);
        assertEquals(7, gathered[6], 1e-12);
        assertEquals(1, through[2], 1e-12);
    }

    /**
     * A surface behind fog keeps its light where the fog lets it through and takes the fog's in the
     * rest: deep fog shows only the fog's own light.
     */
    @Test
    void testApplyBlendsTowardTheFog() {
        final double[] out = new double[3];
        FogMath.apply(new double[] {1, 0.5, 0}, new double[] {0.2, 0.2, 0.2}, 0.25, out);
        assertEquals(0.45, out[0], 1e-12);
        assertEquals(0.325, out[1], 1e-12);
        assertEquals(0.2, out[2], 1e-12);
    }

    /** The phase function integrates to 1 over the sphere, and is even when g is 0. */
    @Test
    void testPhaseIntegratesToOne() {
        assertEquals(1 / (4 * Math.PI), FogMath.henyeyGreenstein(0.3, 0), 1e-12);
        for (double g : new double[] {0, 0.5, -0.3, 0.8}) {
            final int steps = 20_000;
            double sum = 0;
            for (int i = 0; i < steps; ++i) {
                final double mu = -1 + (i + 0.5) * 2.0 / steps;
                sum += FogMath.henyeyGreenstein(mu, g) * 2 * Math.PI * 2.0 / steps;
            }
            assertEquals(1, sum, 1e-3, "g " + g);
        }
        assertTrue(FogMath.henyeyGreenstein(1, 0.5) > FogMath.henyeyGreenstein(-1, 0.5));
    }

    /** Height fog is its density at and below the base, and falls off by e every 1 / falloff. */
    @Test
    void testHeightFog() {
        assertEquals(0.1, FogMath.heightFog(-20, 0.1, 0.05, 0), 1e-12);
        assertEquals(0.1, FogMath.heightFog(0, 0.1, 0.05, 0), 1e-12);
        assertEquals(0.1 / Math.E, FogMath.heightFog(20, 0.1, 0.05, 0), 1e-12);
        assertEquals(0.1, FogMath.heightFog(500, 0.1, 0, 0), 1e-12);
    }

    /** Shapes' signed distances and soft edges. */
    @Test
    void testShapes() {
        assertEquals(-1, FogMath.boxDistance(0, 0, 0, 1, 2, 3), 1e-12);
        assertEquals(1, FogMath.boxDistance(2, 0, 0, 1, 2, 3), 1e-12);
        assertEquals(Math.sqrt(2), FogMath.boxDistance(2, 3, 0, 1, 2, 3), 1e-12);
        assertEquals(-2, FogMath.sphereDistance(0, 1, 0, 3), 1e-12);
        assertEquals(1, FogMath.cylinderDistance(3, 0, 0, 2, 5), 1e-12);
        assertEquals(1, FogMath.cylinderDistance(0, 6, 0, 2, 5), 1e-12);
        assertEquals(-2, FogMath.cylinderDistance(0, 0, 0, 2, 5), 1e-12);
        assertEquals(0, FogMath.edgeWeight(0.5, 2), 1e-12);
        assertEquals(1, FogMath.edgeWeight(-3, 2), 1e-12);
        assertEquals(0.5, FogMath.edgeWeight(-1, 2), 1e-12);
        assertEquals(1, FogMath.edgeWeight(-0.01, 0), 1e-12);
    }
}
