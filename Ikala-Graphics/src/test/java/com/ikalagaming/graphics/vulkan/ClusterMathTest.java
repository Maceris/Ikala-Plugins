package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.scene.Projection;
import com.ikalagaming.graphics.scene.Scene;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

/** The cluster grid, and the tests light culling uses to sort lights into it. */
class ClusterMathTest {

    /** A 1080p screen, so the tiles come out square. */
    private static final int WIDTH = 1920;

    private static final int HEIGHT = 1080;

    private static final float LOG_SCALE = ClusterMath.logScale(Scene.DEFAULT_VIEW_DISTANCE);

    private static Matrix4f inverseProjection() {
        return new Projection(WIDTH, HEIGHT).getInverseProjectionMatrix();
    }

    @Test
    void slicesGrowWithDepthAndCoverTheViewDistance() {
        assertEquals(0, ClusterMath.slice(0.01f, LOG_SCALE));
        assertEquals(0, ClusterMath.slice(0.99f, LOG_SCALE), "The first slice is the first meter");
        assertEquals(1, ClusterMath.slice(1, LOG_SCALE));
        int previous = 0;
        for (float depth = 0.05f; depth < Projection.Z_FAR; depth *= 1.01f) {
            int slice = ClusterMath.slice(depth, LOG_SCALE);
            assertTrue(slice >= previous, "At " + depth);
            assertTrue(slice >= 0 && slice < ClusterMath.Z, "At " + depth);
            previous = slice;
        }
        // The view distance is where the last slice starts holding everything beyond
        assertEquals(
                ClusterMath.Z - 1,
                ClusterMath.slice((float) Scene.DEFAULT_VIEW_DISTANCE * 0.999f, LOG_SCALE));
        assertEquals(ClusterMath.Z - 1, ClusterMath.slice(Projection.Z_FAR, LOG_SCALE));
    }

    @Test
    void eachSliceHoldsTheDepthsBetweenItsEdges() {
        for (int slice = 0; slice < ClusterMath.Z; ++slice) {
            float start = ClusterMath.sliceStart(slice, LOG_SCALE);
            float end = ClusterMath.sliceEnd(slice, LOG_SCALE);
            assertTrue(end > start, "Slice " + slice);
            float middle = slice == 0 ? end / 2 : (float) Math.sqrt(start * end);
            assertEquals(slice, ClusterMath.slice(middle, LOG_SCALE), "Slice " + slice);
        }
        assertEquals(
                Scene.DEFAULT_VIEW_DISTANCE,
                ClusterMath.sliceStart(ClusterMath.Z, LOG_SCALE),
                1e-3,
                "The slices after the first end at the view distance");
    }

    @Test
    void everyPixelsViewPositionIsInsideItsClustersBox() {
        final Matrix4f inverse = inverseProjection();
        final Matrix4f projection = new Projection(WIDTH, HEIGHT).getProjectionMatrix();
        final Vector3f min = new Vector3f();
        final Vector3f max = new Vector3f();
        final float slack = 1e-3f;
        final float[] depths = {0.3f, 2, 7.5f, 40, 150, 500};
        for (float depth : depths) {
            for (int py = 5; py < HEIGHT; py += 97) {
                for (int px = 3; px < WIDTH; px += 131) {
                    float u = (px + 0.5f) / WIDTH;
                    float v = (py + 0.5f) / HEIGHT;
                    // Where the pixel's ray is at this depth, as lights.frag works it out
                    Vector3f ray = new Vector3f();
                    ClusterMath.ray(u, v, inverse, ray);
                    Vector3f position = ray.mul(depth);
                    // And it lands back on the same pixel
                    Vector4f clip = new Vector4f(position, 1).mul(projection);
                    assertEquals(u * 2 - 1, clip.x / clip.w, 1e-4);
                    assertEquals(1 - v * 2, clip.y / clip.w, 1e-4);

                    int x = Math.min((int) (u * ClusterMath.X), ClusterMath.X - 1);
                    int y = Math.min((int) (v * ClusterMath.Y), ClusterMath.Y - 1);
                    int z = ClusterMath.slice(depth, LOG_SCALE);
                    ClusterMath.bounds(x, y, z, inverse, LOG_SCALE, min, max);
                    assertTrue(
                            position.x >= min.x - slack
                                    && position.y >= min.y - slack
                                    && position.z >= min.z - slack
                                    && position.x <= max.x + slack
                                    && position.y <= max.y + slack
                                    && position.z <= max.z + slack,
                            () -> position + " outside " + min + " to " + max);
                }
            }
        }
    }

    @Test
    void indicesAreDistinct() {
        Set<Integer> seen = new HashSet<>();
        for (int z = 0; z < ClusterMath.Z; ++z) {
            for (int y = 0; y < ClusterMath.Y; ++y) {
                for (int x = 0; x < ClusterMath.X; ++x) {
                    int index = ClusterMath.index(x, y, z);
                    assertTrue(index >= 0 && index < ClusterMath.COUNT);
                    assertTrue(seen.add(index));
                }
            }
        }
    }

    @Test
    void aLightReachesTheClustersItsSphereTouches() {
        final Matrix4f inverse = inverseProjection();
        final Vector3f min = new Vector3f();
        final Vector3f max = new Vector3f();
        // Straight ahead, 20 m out: the middle tiles
        final int z = ClusterMath.slice(20, LOG_SCALE);
        ClusterMath.bounds(ClusterMath.X / 2, ClusterMath.Y / 2, z, inverse, LOG_SCALE, min, max);
        final Vector3f center = new Vector3f(min).add(max).mul(0.5f);

        assertTrue(ClusterMath.sphereTouchesBox(center, 0.1f, min, max), "Inside");
        // Just past the box's edge, reaching it or not
        Vector3f beside = new Vector3f(max.x + 2, center.y, center.z);
        assertTrue(ClusterMath.sphereTouchesBox(beside, 2.01f, min, max));
        assertFalse(ClusterMath.sphereTouchesBox(beside, 1.99f, min, max));
        // A light a meter past its range from the box
        Vector3f behindCamera = new Vector3f(0, 0, 10);
        float toBox = 10 + ClusterMath.sliceStart(z, LOG_SCALE);
        assertFalse(ClusterMath.sphereTouchesBox(behindCamera, toBox - 1, min, max));
    }

    @Test
    void aSpotlightOnlyReachesWhatItPointsAt() {
        final Vector3f min = new Vector3f(-1, -1, -21);
        final Vector3f max = new Vector3f(1, 1, -19);
        final Vector3f position = new Vector3f(0, 0, -10);
        final float range = 20;
        final float cos30 = (float) Math.cos(Math.toRadians(30));

        Vector3f towards = new Vector3f(0, 0, -1);
        assertTrue(ClusterMath.coneTouchesBox(position, towards, range, cos30, min, max));
        Vector3f away = new Vector3f(0, 0, 1);
        assertFalse(
                ClusterMath.coneTouchesBox(position, away, range, cos30, min, max), "Behind it");
        Vector3f sideways = new Vector3f(1, 0, 0);
        assertFalse(
                ClusterMath.coneTouchesBox(position, sideways, range, cos30, min, max),
                "Outside its angle");
        // A cone wider than a half sphere reaches back past its sides, but not straight behind
        float cos120 = (float) Math.cos(Math.toRadians(120));
        assertTrue(ClusterMath.coneTouchesBox(position, sideways, range, cos120, min, max));
        assertFalse(ClusterMath.coneTouchesBox(position, away, range, cos120, min, max));
        // Out of reach, however it points
        assertFalse(ClusterMath.coneTouchesBox(position, towards, 5, cos30, min, max));
    }
}
