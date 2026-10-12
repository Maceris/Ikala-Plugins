package com.ikalagaming.graphics.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3d;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

/** Where the sky occlusion map is kept, what of it is drawn again, and how covered points fade. */
class SkyOcclusionTest {

    private static final double VIEW_DISTANCE = 192;

    private static CascadeShadowSplit plan(SkyOcclusion sky, Vector3d camera) {
        final CascadeShadowSplit view = new CascadeShadowSplit();
        sky.plan(view, camera, VIEW_DISTANCE);
        return view;
    }

    /**
     * Where a world point lands in the map, in texels, and its depth.
     *
     * @param view The view.
     * @param camera The camera it was planned for.
     * @param world The point.
     * @return The texel coordinates and depth.
     */
    private static Vector4f texelOf(CascadeShadowSplit view, Vector3d camera, Vector3d world) {
        final Vector4f clip =
                new Vector4f(
                                (float) (world.x - camera.x),
                                (float) (world.y - camera.y),
                                (float) (world.z - camera.z),
                                1)
                        .mul(view.getProjViewMatrix());
        return new Vector4f(
                (clip.x / clip.w * 0.5f + 0.5f) * view.getMapSize(),
                (clip.y / clip.w * 0.5f + 0.5f) * view.getMapSize(),
                clip.z / clip.w,
                0);
    }

    @Test
    void itMovesOnlyAtItsSteps() {
        final SkyOcclusion sky = new SkyOcclusion();
        assertEquals(SunShadowCache.Redraw.ALL, plan(sky, new Vector3d(0, 40, 0)).getRedraw());
        int moves = 0;
        for (int step = 1; step <= 200; ++step) {
            final CascadeShadowSplit view = plan(sky, new Vector3d(step * 1.0, 40, step * 0.3));
            if (view.getRedraw() == SunShadowCache.Redraw.ALL) {
                ++moves;
            } else {
                assertEquals(SunShadowCache.Redraw.NONE, view.getRedraw());
            }
        }
        // 200 m with steps a quarter of the view distance wide
        assertTrue(moves >= 2 && moves <= 8, "Moved " + moves + " times");
    }

    @Test
    void theWholeViewIsInsideAndHigherIsNearer() {
        final SkyOcclusion sky = new SkyOcclusion();
        final Vector3d camera = new Vector3d(37, 25, -81);
        final CascadeShadowSplit view = plan(sky, camera);
        for (int corner = 0; corner < 4; ++corner) {
            final Vector3d world =
                    new Vector3d(camera)
                            .add(
                                    (corner & 1) == 0 ? -VIEW_DISTANCE : VIEW_DISTANCE,
                                    0,
                                    (corner & 2) == 0 ? -VIEW_DISTANCE * 0.7 : VIEW_DISTANCE * 0.7);
            final Vector4f texel = texelOf(view, camera, world);
            assertTrue(texel.x >= 0 && texel.x <= view.getMapSize(), "Corner " + corner);
            assertTrue(texel.y >= 0 && texel.y <= view.getMapSize(), "Corner " + corner);
        }
        final Vector4f high = texelOf(view, camera, new Vector3d(camera).add(0, 10, 0));
        final Vector4f low = texelOf(view, camera, new Vector3d(camera).add(0, -10, 0));
        assertTrue(high.z < low.z && high.z > 0 && low.z < 1);
        // Depth over the range is meters
        assertEquals(20, (low.z - high.z) * view.getDepthRange(), 1e-2);
    }

    @Test
    void aChangedBoxIsDrawnAgainOnce() {
        final SkyOcclusion sky = new SkyOcclusion();
        final Vector3d camera = new Vector3d(0, 40, 0);
        plan(sky, camera);
        sky.invalidate(new double[] {16, 0, 32}, new double[] {32, 16, 48});
        final CascadeShadowSplit view = plan(sky, camera);
        assertEquals(SunShadowCache.Redraw.PART, view.getRedraw());
        final int[] rect = view.getRedrawRect();
        final Vector4f corner = texelOf(view, camera, new Vector3d(24, 8, 40));
        assertTrue(corner.x >= rect[0] && corner.x <= rect[2]);
        assertTrue(corner.y >= rect[1] && corner.y <= rect[3]);
        assertTrue((rect[2] - rect[0]) * (rect[3] - rect[1]) < 200 * 200);
        assertEquals(SunShadowCache.Redraw.NONE, plan(sky, camera).getRedraw());
    }

    @Test
    void coveredPointsFadeToTheFloor() {
        final float floor = 0.25f;
        assertEquals(1, SkyOcclusion.visibility(-3, floor), 1e-6);
        assertEquals(1, SkyOcclusion.visibility(SkyOcclusion.BIAS, floor), 1e-6);
        assertEquals(floor, SkyOcclusion.visibility(SkyOcclusion.BIAS + SkyOcclusion.FADE, floor));
        assertEquals(floor, SkyOcclusion.visibility(50, floor), 1e-6);
        float previous = 1;
        for (float covered = 0; covered < 6; covered += 0.25f) {
            final float visibility = SkyOcclusion.visibility(covered, floor);
            assertTrue(visibility <= previous);
            previous = visibility;
        }
    }
}
