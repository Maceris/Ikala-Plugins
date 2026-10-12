package com.ikalagaming.graphics.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector4d;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

/** Where temporal anti-aliasing puts each frame's samples, and how pixels find their history. */
class TaaMathTest {

    private static final double EPSILON = 1e-6;

    /** Halton's first few elements in base 2 and 3. */
    @Test
    void haltonMirrorsTheDigits() {
        assertEquals(0.5, TaaMath.halton(1, 2), EPSILON);
        assertEquals(0.25, TaaMath.halton(2, 2), EPSILON);
        assertEquals(0.75, TaaMath.halton(3, 2), EPSILON);
        assertEquals(1.0 / 3, TaaMath.halton(1, 3), EPSILON);
        assertEquals(2.0 / 3, TaaMath.halton(2, 3), EPSILON);
        assertEquals(1.0 / 9, TaaMath.halton(3, 3), EPSILON);
    }

    /** Each cycle's samples are different, stay in the pixel and repeat after the cycle. */
    @Test
    void jitterCoversThePixelAndRepeats() {
        final Set<String> seen = new HashSet<>();
        double sumX = 0;
        double sumY = 0;
        for (int frame = 0; frame < TaaMath.JITTER_SAMPLES; ++frame) {
            final double[] offset = TaaMath.jitter(frame);
            assertTrue(offset[0] >= -0.5 && offset[0] < 0.5);
            assertTrue(offset[1] >= -0.5 && offset[1] < 0.5);
            assertTrue(seen.add(offset[0] + "," + offset[1]));
            final double[] again = TaaMath.jitter(frame + TaaMath.JITTER_SAMPLES);
            assertEquals(offset[0], again[0], EPSILON);
            assertEquals(offset[1], again[1], EPSILON);
            sumX += offset[0];
            sumY += offset[1];
        }
        // Spread around the middle, not off to one side
        assertEquals(0, sumX / TaaMath.JITTER_SAMPLES, 0.1);
        assertEquals(0, sumY / TaaMath.JITTER_SAMPLES, 0.1);
    }

    /** Standing still, every pixel is where it was. */
    @Test
    void aStillCameraReprojectsInPlace() {
        final Matrix4f projection = projection();
        final Matrix4f view = new Matrix4f().rotateY(0.3f);
        final Matrix4d reprojection =
                TaaMath.reprojection(projection, view, projection, view, new Vector3d());
        final Vector4d clip = new Vector4d(0.25, -0.4, 0.7, 1);
        final Vector4d previous = reprojection.transform(new Vector4d(clip));
        assertEquals(clip.x, previous.x / previous.w, EPSILON);
        assertEquals(clip.y, previous.y / previous.w, EPSILON);
        assertEquals(clip.z, previous.z / previous.w, EPSILON);
    }

    /** A point in the world lands where last frame's camera saw it, after the camera moves. */
    @Test
    void aMovingCameraFindsWhereAPointWas() {
        final Matrix4f projection = projection();
        final Vector3d previousCamera = new Vector3d(100, 20, -40);
        final Vector3d camera = new Vector3d(101.5, 20.25, -39);
        final Matrix4f previousView = new Matrix4f().rotateY(0.2f);
        final Matrix4f view = new Matrix4f().rotateY(0.25f).rotateX(0.05f);
        final Vector3d world = new Vector3d(103, 21, -50);

        final Vector4d clip = clip(projection, view, world, camera);
        final Vector4d expected = clip(projection, previousView, world, previousCamera);
        final Matrix4d reprojection =
                TaaMath.reprojection(
                        projection,
                        view,
                        projection,
                        previousView,
                        new Vector3d(camera).sub(previousCamera));
        final Vector4d previous = reprojection.transform(new Vector4d(clip).div(clip.w));
        assertEquals(expected.x / expected.w, previous.x / previous.w, EPSILON);
        assertEquals(expected.y / expected.w, previous.y / previous.w, EPSILON);
        assertEquals(expected.z / expected.w, previous.z / previous.w, EPSILON);
    }

    private static Matrix4f projection() {
        return new Matrix4f()
                .setPerspective((float) Math.toRadians(60), 16f / 9, 0.01f, 1000, true);
    }

    /** Where a world point lands in a camera's clip space, positions relative to the camera. */
    private static Vector4d clip(
            Matrix4f projection, Matrix4f view, Vector3d world, Vector3d camera) {
        final Vector3d render = new Vector3d(world).sub(camera);
        return new Matrix4d(projection)
                .mul(new Matrix4d(view))
                .transform(new Vector4d(render.x, render.y, render.z, 1));
    }
}
