package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

/** The occlusion test the culling shader does against the depth pyramid, mirrored in Java. */
class OcclusionCullTest {

    /** An odd sized screen, so the leftover texels are exercised too. */
    private static final int WIDTH = 67;

    private static final int HEIGHT = 45;

    /** How far away the wall is. */
    private static final float WALL_DISTANCE = 10;

    /** A camera at the origin looking down -z, 90 degrees high, 0.1 to 100 deep. */
    private static Matrix4f camera() {
        return new Matrix4f()
                .perspective((float) Math.toRadians(90), (float) WIDTH / HEIGHT, 0.1f, 100, true);
    }

    /** The depth a point straight ahead at a distance ends up with. */
    private static float depthAt(Matrix4f projectionView, float distance) {
        Vector4f clip = projectionView.transform(new Vector4f(0, 0, -distance, 1));
        return clip.z / clip.w;
    }

    /**
     * A screen with a wall across the columns from {@code firstColumn} on, and nothing but the
     * cleared far plane elsewhere.
     */
    private static DepthPyramidMath.Pyramid wall(Matrix4f projectionView, int firstColumn) {
        float wallDepth = depthAt(projectionView, WALL_DISTANCE);
        float[] depth = new float[WIDTH * HEIGHT];
        for (int y = 0; y < HEIGHT; ++y) {
            for (int x = 0; x < WIDTH; ++x) {
                depth[y * WIDTH + x] = x >= firstColumn ? wallDepth : 1;
            }
        }
        return DepthPyramidMath.build(depth, WIDTH, HEIGHT);
    }

    private static boolean occluded(
            Matrix4f projectionView,
            DepthPyramidMath.Pyramid pyramid,
            float x,
            float y,
            float z,
            float half) {
        return DepthPyramidMath.occluded(
                projectionView, new Vector3f(x, y, z), new Vector3f(half, half, half), pyramid);
    }

    @Test
    void onlyBoxesWhollyBehindTheWallAreHidden() {
        Matrix4f projectionView = camera();
        DepthPyramidMath.Pyramid pyramid = wall(projectionView, 0);
        assertTrue(occluded(projectionView, pyramid, 0, 0, -20, 1), "Behind the wall");
        assertTrue(occluded(projectionView, pyramid, 3, -2, -50, 4), "Far behind, off center");
        assertFalse(occluded(projectionView, pyramid, 0, 0, -5, 1), "In front of the wall");
        assertFalse(
                occluded(projectionView, pyramid, 0, 0, -WALL_DISTANCE - 0.5f, 1),
                "Poking through the wall");
        assertFalse(
                occluded(projectionView, pyramid, 0, 0, 0, 1),
                "Around the camera, reaching behind the near plane");
    }

    @Test
    void boxesPeekingPastTheWallsEdgeAreSeen() {
        Matrix4f projectionView = camera();
        // The wall covers the right half of the screen
        DepthPyramidMath.Pyramid pyramid = wall(projectionView, WIDTH / 2);
        assertTrue(occluded(projectionView, pyramid, 6, 0, -20, 1), "Behind the right half");
        assertFalse(occluded(projectionView, pyramid, -6, 0, -20, 1), "Off to the open left");
        assertFalse(occluded(projectionView, pyramid, 0, 0, -20, 1), "Straddling the wall's edge");
    }

    @Test
    void aPyramidDrawnElsewhereIsMovedIntoTheCamerasSpace() {
        // An observer frozen a billion units out, and the camera moved 5 to its right since
        Vector3d observer = new Vector3d(1e9, 0, 0);
        Vector3d cameraPosition = new Vector3d(1e9 + 5, 0, 0);
        Matrix4f observerProjectionView = camera();
        DepthPyramidMath.Pyramid pyramid = wall(observerProjectionView, 0);
        Matrix4f moved =
                DepthPyramidMath.moveInto(
                        observerProjectionView, observer, cameraPosition, new Matrix4f());
        // 20 in front of the observer is 5 to the camera's left
        assertTrue(occluded(moved, pyramid, -5, 0, -20, 1), "Behind the observer's wall");
        assertFalse(occluded(moved, pyramid, -5, 0, -5, 1), "In front of the observer's wall");

        Matrix4f unmoved =
                DepthPyramidMath.moveInto(
                        observerProjectionView, cameraPosition, cameraPosition, new Matrix4f());
        assertEquals(observerProjectionView, unmoved, "Drawn from the camera, nothing moves");
    }
}
