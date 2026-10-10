package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * The frustum planes the culling shader gets, and the box test it does with them, mirrored in Java.
 */
class FrustumCullTest {

    /** A camera at the origin looking down -z, 90 degrees across, 0.1 to 100 deep. */
    private static float[] camera() {
        Matrix4f projectionView =
                new Matrix4f().perspective((float) Math.toRadians(90), 1, 0.1f, 100, true);
        float[] planes = new float[FrustumPlanes.FLOATS];
        FrustumPlanes.extract(projectionView, planes);
        return planes;
    }

    private static boolean visible(float[] planes, float x, float y, float z, float half) {
        return FrustumPlanes.boxInside(
                planes, new Vector3f(x, y, z), new Vector3f(half, half, half));
    }

    @Test
    void boxesAreKeptOnlyWhenTheyReachInside() {
        float[] planes = camera();
        assertTrue(visible(planes, 0, 0, -10, 1), "Straight ahead");
        assertFalse(visible(planes, 0, 0, 10, 1), "Behind");
        assertFalse(visible(planes, 0, 0, -200, 1), "Past the far plane");
        // 45 degrees to the side is the edge of a 90 degree view
        assertFalse(visible(planes, 20, 0, -10, 1), "Off to the right");
        assertTrue(visible(planes, 10.5f, 0, -10, 1), "Straddling the right plane");
        assertFalse(visible(planes, 0, -20, -10, 1), "Below");
        assertTrue(visible(planes, 0, 0, 0, 1), "Around the camera, straddling the near plane");
    }

    @Test
    void rotatedAndScaledInstancesUseTheirTransformedBounds() {
        float[] planes = camera();
        // A long thin mesh, 20 along x but thin on the others, off to the right 5 ahead, where
        // the right plane is at x = 5
        Vector3f localExtents = new Vector3f(10, 0.1f, 0.1f);
        Matrix4f flat = new Matrix4f().translation(14, 0, -5);
        assertTrue(boxInside(planes, flat, localExtents), "Its left end reaches into view");
        // Stood up along y, it no longer reaches
        Quaternionf upright = new Quaternionf().rotateZ((float) (Math.PI / 2));
        Matrix4f turned =
                new Matrix4f()
                        .translationRotateScale(
                                14, 0, -5, upright.x, upright.y, upright.z, upright.w, 1, 1, 1);
        assertFalse(boxInside(planes, turned, localExtents));
        // Shrunk to half its length, the flat one doesn't reach either
        Matrix4f small = new Matrix4f(flat).scale(0.5f);
        assertFalse(boxInside(planes, small, localExtents));
    }

    /** The shader's transform of a box: move the center, grow the extents by the abs matrix. */
    private static boolean boxInside(float[] planes, Matrix4f model, Vector3f localExtents) {
        Vector3f center = model.transformPosition(new Vector3f());
        Matrix3f absolute = new Matrix3f(model);
        absolute.m00(Math.abs(absolute.m00())).m01(Math.abs(absolute.m01()));
        absolute.m02(Math.abs(absolute.m02())).m10(Math.abs(absolute.m10()));
        absolute.m11(Math.abs(absolute.m11())).m12(Math.abs(absolute.m12()));
        absolute.m20(Math.abs(absolute.m20())).m21(Math.abs(absolute.m21()));
        absolute.m22(Math.abs(absolute.m22()));
        Vector3f extents = absolute.transform(new Vector3f(localExtents));
        return FrustumPlanes.boxInside(planes, center, extents);
    }

    @Test
    void orthographicCascadesCullSideways() {
        // A light looking down, covering 20 blocks across and 50 deep from 25 above
        Matrix4f cascade =
                new Matrix4f()
                        .ortho(-10, 10, -10, 10, 0, 50, true)
                        .mul(new Matrix4f().lookAt(0, 25, 0, 0, 0, 0, 0, 0, -1));
        float[] planes = new float[FrustumPlanes.FLOATS];
        FrustumPlanes.extract(cascade, planes);
        assertTrue(visible(planes, 0, 0, 0, 1));
        assertTrue(visible(planes, 9, 0, 9, 1));
        assertFalse(visible(planes, 15, 0, 0, 1), "Outside the light's square");
        assertFalse(visible(planes, 0, -40, 0, 1), "Below its depth range");
    }

    @Test
    void aFrozenObserverFarAwayCullsWhatItSaw() {
        // The observer stopped a billion blocks out, and the camera flew 50 blocks behind it
        Vector3d observer = new Vector3d(1e9, 0, -1e9);
        Vector3d camera = new Vector3d(1e9, 0, -1e9 + 50);
        Matrix4f projectionView =
                new Matrix4f().perspective((float) Math.toRadians(90), 1, 0.1f, 100, true);
        float[] planes = new float[FrustumPlanes.FLOATS];
        FrustumPlanes.extract(projectionView, observer, camera, planes);

        // 10 in front of the observer is 60 in front of the camera, in render space
        assertTrue(visible(planes, 0, 0, -60, 1));
        // Right in front of the camera is behind the observer
        assertFalse(visible(planes, 0, 0, -5, 1));
    }

    @Test
    void disabledCullingKeepsEverything() {
        float[] planes = new float[FrustumPlanes.FLOATS];
        FrustumPlanes.acceptAll(planes);
        assertTrue(visible(planes, 1e6f, -1e6f, 1e6f, 0));
    }
}
