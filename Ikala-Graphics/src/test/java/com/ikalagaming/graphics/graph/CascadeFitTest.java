package com.ikalagaming.graphics.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.scene.Camera;
import com.ikalagaming.graphics.scene.Projection;
import com.ikalagaming.graphics.scene.Scene;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

/** How the sun's shadow cascades are fitted around the view. */
class CascadeFitTest {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final Vector3f SUN = new Vector3f(0.3f, -0.85f, 0.4f).normalize();

    private static CascadeShadowSplit[] fit(Camera camera, double shadowDistance) {
        CascadeShadowSplit[] cascades =
                new CascadeShadowSplit[CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT];
        for (int i = 0; i < cascades.length; ++i) {
            cascades[i] = new CascadeShadowSplit();
        }
        CascadeShadowSplit.update(
                cascades,
                new Projection(WIDTH, HEIGHT).getInverseProjectionMatrix(),
                camera.getInvViewMatrix(),
                camera.getPosition(),
                SUN,
                shadowDistance);
        return cascades;
    }

    private static Camera camera(double x, double y, double z, float pitch, float yaw) {
        Camera camera = new Camera();
        camera.setPosition(x, y, z);
        camera.setRotation(pitch, yaw);
        return camera;
    }

    /**
     * Where a world point lands in a cascade's shadow map, in texels.
     *
     * @param cascade The cascade.
     * @param camera The camera it was fitted for, whose position is the render space origin.
     * @param world The world point.
     * @return The texel coordinates, x and y.
     */
    private static Vector3f texelOf(CascadeShadowSplit cascade, Camera camera, Vector3d world) {
        Vector4f clip =
                new Vector4f(
                                (float) (world.x - camera.getPosition().x()),
                                (float) (world.y - camera.getPosition().y()),
                                (float) (world.z - camera.getPosition().z()),
                                1)
                        .mul(cascade.getProjViewMatrix());
        return new Vector3f(
                (clip.x / clip.w * 0.5f + 0.5f) * CascadeShadowSplit.SHADOW_MAP_SIZE,
                (clip.y / clip.w * 0.5f + 0.5f) * CascadeShadowSplit.SHADOW_MAP_SIZE,
                clip.z / clip.w);
    }

    @Test
    void splitsGrowAndEndAtTheShadowDistance() {
        float[] depths = CascadeShadowSplit.splitDepths(Scene.DEFAULT_VIEW_DISTANCE);
        float previous = Projection.Z_NEAR;
        for (float depth : depths) {
            assertTrue(depth > previous);
            previous = depth;
        }
        assertEquals(Scene.DEFAULT_VIEW_DISTANCE, depths[depths.length - 1], 1e-3);
    }

    @Test
    void turningKeepsTheTexelSize() {
        CascadeShadowSplit[] facing = fit(camera(10, 40, -5, 0.3f, 0), 192);
        for (float yaw = 0.1f; yaw < 2 * Math.PI; yaw += 0.37f) {
            CascadeShadowSplit[] turned = fit(camera(10, 40, -5, 0.3f, yaw), 192);
            for (int i = 0; i < facing.length; ++i) {
                assertEquals(facing[i].getTexelSize(), turned[i].getTexelSize(), "Cascade " + i);
            }
        }
    }

    @Test
    void movingKeepsTheWorldOnTheSameTexelGrid() {
        // A point on the ground, and the camera creeping past it by less than a texel at a time
        Vector3d ground = new Vector3d(3.3, 0, 7.9);
        Camera start = camera(0, 20, 0, 0.6f, 0.5f);
        CascadeShadowSplit[] first = fit(start, 192);
        for (int step = 1; step <= 20; ++step) {
            double offset = step * 0.0037;
            Camera moved = camera(offset, 20, offset * 0.5, 0.6f, 0.5f);
            CascadeShadowSplit[] later = fit(moved, 192);
            for (int i = 0; i < first.length; ++i) {
                Vector3f before = texelOf(first[i], start, ground);
                Vector3f after = texelOf(later[i], moved, ground);
                // Snapped by whole texels, so the fraction within a texel never changes
                float dx = after.x - before.x;
                float dy = after.y - before.y;
                assertEquals(Math.round(dx), dx, 0.01, "Cascade " + i + " step " + step);
                assertEquals(Math.round(dy), dy, 0.01, "Cascade " + i + " step " + step);
            }
        }
    }

    @Test
    void theSliceAndCastersTowardTheSunAreInside() {
        Camera camera = camera(-40, 25, 12, 0.4f, 2.1f);
        CascadeShadowSplit[] cascades = fit(camera, 192);
        Matrix4f inverseProjection = new Projection(WIDTH, HEIGHT).getInverseProjectionMatrix();
        float near = Projection.Z_NEAR;
        for (CascadeShadowSplit cascade : cascades) {
            float far = -cascade.getSplitDistance();
            // Every corner of the slice is inside the map, with the filter's texels to spare
            for (int y = -1; y <= 1; y += 2) {
                for (int x = -1; x <= 1; x += 2) {
                    Vector4f point = new Vector4f(x, y, 0.5f, 1).mul(inverseProjection);
                    Vector3f ray = new Vector3f(point.x, point.y, point.z).div(point.w);
                    ray.div(-ray.z);
                    for (float depth : new float[] {near, far}) {
                        Vector3f render =
                                camera.getInvViewMatrix()
                                        .transformPosition(new Vector3f(ray).mul(depth));
                        Vector3d world = new Vector3d(render).add(camera.getPosition());
                        Vector3f texel = texelOf(cascade, camera, world);
                        assertTrue(
                                texel.x >= 2 && texel.x <= CascadeShadowSplit.SHADOW_MAP_SIZE - 2);
                        assertTrue(
                                texel.y >= 2 && texel.y <= CascadeShadowSplit.SHADOW_MAP_SIZE - 2);
                        assertTrue(texel.z > 0 && texel.z < 1);

                        // And something standing the caster reach toward the sun from it
                        Vector3d caster =
                                new Vector3d(world)
                                        .sub(
                                                new Vector3d(SUN)
                                                        .mul(
                                                                CascadeShadowSplit.CASTER_REACH
                                                                        * 0.99));
                        assertTrue(texelOf(cascade, camera, caster).z > 0, "Caster clipped");
                    }
                }
            }
            near = far;
        }
    }
}
