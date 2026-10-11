package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

/** Voxel-based order independent transparency: splat, integrate and read one tile's column. */
class VoxelOitTest {

    private static final float EPSILON = 1e-3f;

    private static final float VIEW_DISTANCE = 200;

    private static final float LOG_SCALE = VoxelOitMath.logScale(VIEW_DISTANCE);

    private record Layer(Vector3f color, float alpha, float depth) {}

    /** Splat every layer, integrate, then weight each by what is in front of it and resolve. */
    private static Vector3f composite(Vector3f background, Layer... layers) {
        int[] column = new int[VoxelOitMath.SLICES];
        for (Layer layer : layers) {
            VoxelOitMath.splat(
                    column,
                    layer.depth(),
                    LOG_SCALE,
                    OitMath.extinction(Math.min(layer.alpha(), OitMath.MAX_OPACITY)));
        }
        float[] transmittance = VoxelOitMath.integrate(column);
        Vector4f accum = new Vector4f(0, 0, 0, 0);
        float extinction = 0;
        for (Layer layer : layers) {
            final float weight = VoxelOitMath.inFront(transmittance, layer.depth(), LOG_SCALE);
            extinction =
                    OitMath.addWeighted(accum, extinction, layer.color(), layer.alpha(), weight);
        }
        return OitMath.resolve(accum, extinction, new Vector3f(background));
    }

    /** Ordinary back to front alpha blending, the reference. */
    private static Vector3f sorted(Vector3f background, Layer... farToNear) {
        Vector3f result = new Vector3f(background);
        for (Layer layer : farToNear) {
            result.lerp(layer.color(), layer.alpha());
        }
        return result;
    }

    @Test
    void layersSlicesApartMatchSortedBlending() {
        Vector3f background = new Vector3f(0.2f, 0.4f, 0.9f);
        Layer far = new Layer(new Vector3f(0, 0, 1), 0.5f, 60);
        Layer middle = new Layer(new Vector3f(0, 1, 0), 0.6f, 20);
        Layer near = new Layer(new Vector3f(1, 0, 0), 0.3f, 5);
        Vector3f expected = sorted(background, far, middle, near);
        Vector3f anyOrder = composite(background, middle, near, far);
        assertTrue(expected.equals(anyOrder, EPSILON), expected + " vs " + anyOrder);
    }

    @Test
    void layersInOneSliceMixAsAnAverage() {
        Vector3f background = new Vector3f(0, 0, 0);
        Layer a = new Layer(new Vector3f(1, 0, 0), 0.5f, 10);
        Layer b = new Layer(new Vector3f(0, 1, 0), 0.5f, 10.01f);
        Vector3f result = composite(background, a, b);
        // Equal weights, so equal shares of the 75% the two cover
        assertEquals(result.x, result.y, EPSILON);
        assertEquals(0.75f / 2, result.x, EPSILON);
    }

    @Test
    void aSurfaceDoesNotDimItself() {
        for (float depth : new float[] {0.1f, 0.5f, 0.6f, 1, 3, 17, 150, VIEW_DISTANCE, 900}) {
            int[] column = new int[VoxelOitMath.SLICES];
            VoxelOitMath.splat(column, depth, LOG_SCALE, OitMath.extinction(0.9f));
            float[] transmittance = VoxelOitMath.integrate(column);
            assertEquals(
                    1, VoxelOitMath.inFront(transmittance, depth, LOG_SCALE), 1e-6f, "" + depth);
        }
    }

    @Test
    void whatIsInFrontDimsWhatIsBehind() {
        int[] column = new int[VoxelOitMath.SLICES];
        VoxelOitMath.splat(column, 4, LOG_SCALE, OitMath.extinction(0.4f));
        float[] transmittance = VoxelOitMath.integrate(column);
        assertEquals(0.6f, VoxelOitMath.inFront(transmittance, 30, LOG_SCALE), 1e-4f);
        assertEquals(1, VoxelOitMath.inFront(transmittance, 1, LOG_SCALE), 1e-6f);
    }

    @Test
    void splattingKeepsTheWholeExtinctionAndIntegratingClears() {
        int[] column = new int[VoxelOitMath.SLICES];
        final float extinction = OitMath.extinction(0.7f);
        VoxelOitMath.splat(column, 12.3f, LOG_SCALE, extinction);
        long total = 0;
        int touched = 0;
        for (int value : column) {
            total += value;
            touched += value == 0 ? 0 : 1;
        }
        assertEquals(extinction, total / VoxelOitMath.FIXED_POINT_SCALE, 1e-4f);
        assertTrue(touched <= 2, "Split between two slices at most");
        VoxelOitMath.integrate(column);
        for (int value : column) {
            assertEquals(0, value);
        }
    }

    @Test
    void slicesCoverTheViewDistance() {
        assertEquals(0, VoxelOitMath.sliceCoordinate(0, LOG_SCALE));
        assertEquals(0, VoxelOitMath.sliceCoordinate(VoxelOitMath.FIRST_DEPTH, LOG_SCALE));
        assertEquals(
                VoxelOitMath.SLICES, VoxelOitMath.sliceCoordinate(VIEW_DISTANCE, LOG_SCALE), 1e-3f);
        assertEquals(VoxelOitMath.SLICES, VoxelOitMath.sliceCoordinate(5000, LOG_SCALE));
        assertEquals(1, VoxelOitMath.tiles(1));
        assertEquals(240, VoxelOitMath.tiles(1920));
        assertEquals(128, VoxelOitMath.tiles(1017));
    }
}
