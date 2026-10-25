package com.ikalagaming.graphics.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.scene.Camera;
import com.ikalagaming.graphics.scene.Projection;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import java.util.Random;

/** How the sun's far cascades are kept in place and what of them is drawn again. */
class SunShadowCacheTest {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final double SHADOW_DISTANCE = 192;
    private static final Vector3f SUN = new Vector3f(0.3f, -0.85f, 0.4f).normalize();

    /** A cache and the cascades it fills out each frame. */
    private static final class Frames {
        final SunShadowCache cache = new SunShadowCache();
        final CascadeShadowSplit[] cascades =
                new CascadeShadowSplit[CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT];

        Frames() {
            for (int i = 0; i < cascades.length; ++i) {
                cascades[i] = new CascadeShadowSplit();
            }
        }

        CascadeShadowSplit[] plan(Camera camera, Vector3f sun) {
            cache.plan(
                    cascades,
                    new Projection(WIDTH, HEIGHT).getInverseProjectionMatrix(),
                    camera.getInvViewMatrix(),
                    camera.getPosition(),
                    sun,
                    SHADOW_DISTANCE,
                    true);
            return cascades;
        }
    }

    private static Camera camera(double x, double y, double z, float pitch, float yaw) {
        Camera camera = new Camera();
        camera.setPosition(x, y, z);
        camera.setRotation(pitch, yaw);
        return camera;
    }

    /**
     * Where a world point lands in a cascade's map, in texels, and its depth.
     *
     * @param cascade The cascade.
     * @param camera The camera it was planned for.
     * @param world The world point.
     * @return The texel coordinates and depth.
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
                (clip.x / clip.w * 0.5f + 0.5f) * cascade.getMapSize(),
                (clip.y / clip.w * 0.5f + 0.5f) * cascade.getMapSize(),
                clip.z / clip.w);
    }

    @Test
    void theFirstFrameDrawsEverythingWhole() {
        CascadeShadowSplit[] cascades = new Frames().plan(camera(0, 30, 0, 0.3f, 0), SUN);
        for (int i = 0; i < cascades.length; ++i) {
            assertEquals(SunShadowCache.Redraw.ALL, cascades[i].getRedraw(), "Cascade " + i);
            assertEquals(SunShadowCache.CACHED[i], cascades[i].isCached());
        }
    }

    @Test
    void turningDrawsNothingAgain() {
        Frames frames = new Frames();
        frames.plan(camera(5, 30, -7, 0.3f, 0), SUN);
        for (float yaw = 0.2f; yaw < 2 * Math.PI; yaw += 0.41f) {
            CascadeShadowSplit[] cascades = frames.plan(camera(5, 30, -7, 0.3f, yaw), SUN);
            for (int i = 0; i < cascades.length; ++i) {
                if (SunShadowCache.CACHED[i]) {
                    assertEquals(SunShadowCache.Redraw.NONE, cascades[i].getRedraw());
                    assertTrue(cascades[i].isStaticCulledAway());
                }
            }
        }
    }

    @Test
    void walkingMovesACascadeOnlyAtItsSteps() {
        Frames frames = new Frames();
        Camera start = camera(0, 30, 0, 0.3f, 1.1f);
        CascadeShadowSplit[] first = frames.plan(start, SUN);
        Vector3d ground = new Vector3d(11.3, 2, -4.7);
        Vector3f[] before = new Vector3f[first.length];
        for (int i = 0; i < first.length; ++i) {
            before[i] = texelOf(first[i], start, ground);
        }
        int[] moves = new int[first.length];
        for (int step = 1; step <= 400; ++step) {
            Camera moved = camera(step * 0.25, 30, step * 0.1, 0.3f, 1.1f);
            CascadeShadowSplit[] cascades = frames.plan(moved, SUN);
            for (int i = 0; i < cascades.length; ++i) {
                if (!SunShadowCache.CACHED[i]) {
                    continue;
                }
                Vector3f texel = texelOf(cascades[i], moved, ground);
                if (cascades[i].getRedraw() == SunShadowCache.Redraw.SCROLL) {
                    moves[i] += 1;
                    // Shifted by just as far as the ground moved in the map, at the same depth
                    int[] scroll = cascades[i].getScroll();
                    assertEquals(
                            before[i].x + scroll[0],
                            texel.x,
                            0.02,
                            "Cascade " + i + " step " + step);
                    assertEquals(
                            before[i].y + scroll[1],
                            texel.y,
                            0.02,
                            "Cascade " + i + " step " + step);
                    assertEquals(before[i].z, texel.z, 1e-5, "Cascade " + i + " step " + step);
                } else if (cascades[i].getRedraw() == SunShadowCache.Redraw.ALL) {
                    moves[i] += 1;
                    // Moved by whole texels, so the world stays on the same grid
                    float dx = texel.x - before[i].x;
                    float dy = texel.y - before[i].y;
                    assertEquals(Math.round(dx), dx, 0.02, "Cascade " + i + " step " + step);
                    assertEquals(Math.round(dy), dy, 0.02, "Cascade " + i + " step " + step);
                } else {
                    // In place, the ground stays on the same texel
                    assertEquals(before[i].x, texel.x, 0.02, "Cascade " + i + " step " + step);
                    assertEquals(before[i].y, texel.y, 0.02, "Cascade " + i + " step " + step);
                }
                before[i] = texel;
            }
        }
        // 100 meters: the middle cascade moves now and then, the far one less often
        assertTrue(moves[1] > 0 && moves[1] < 100, "Middle moved " + moves[1]);
        assertTrue(moves[2] < moves[1], "Far moved " + moves[2]);
    }

    @Test
    void walkingShiftsTheMapsAlongInsteadOfDrawingThemWhole() {
        Frames frames = new Frames();
        frames.plan(camera(0, 30, 0, 0.3f, 1.1f), SUN);
        int[] scrolls = new int[SunShadowCache.CACHED.length];
        int[] wholes = new int[SunShadowCache.CACHED.length];
        for (int step = 1; step <= 800; ++step) {
            Camera moved = camera(step * 0.25, 30 + step * 0.01, step * 0.1, 0.3f, 1.1f);
            CascadeShadowSplit[] cascades = frames.plan(moved, SUN);
            for (int i = 0; i < cascades.length; ++i) {
                if (!SunShadowCache.CACHED[i]) {
                    continue;
                }
                CascadeShadowSplit cascade = cascades[i];
                if (cascade.getRedraw() == SunShadowCache.Redraw.ALL) {
                    wholes[i] += 1;
                } else if (cascade.getRedraw() == SunShadowCache.Redraw.SCROLL) {
                    scrolls[i] += 1;
                    // The strip is the texels the shift brought in from outside the map, along
                    // one side
                    int[] scroll = cascade.getScroll();
                    int[] rect = cascade.getRedrawRect();
                    int size = cascade.getMapSize();
                    assertTrue((scroll[0] == 0) != (scroll[1] == 0), "Along one axis");
                    long area = (long) (rect[2] - rect[0]) * (rect[3] - rect[1]);
                    assertEquals((long) Math.abs(scroll[0] + scroll[1]) * size, area);
                    if (scroll[0] > 0) {
                        assertEquals(0, rect[0]);
                    } else if (scroll[0] < 0) {
                        assertEquals(size, rect[2]);
                    } else if (scroll[1] > 0) {
                        assertEquals(0, rect[1]);
                    } else {
                        assertEquals(size, rect[3]);
                    }
                    assertFalse(cascade.isStaticCulledAway());
                }
            }
        }
        // 200 meters: the cascades move many times, and are drawn whole only now and then
        for (int i = 0; i < scrolls.length; ++i) {
            if (SunShadowCache.CACHED[i]) {
                assertTrue(scrolls[i] > 0, "Cascade " + i + " never shifted");
                assertTrue(
                        wholes[i] * 4 < scrolls[i],
                        "Cascade " + i + ": " + wholes[i] + " whole, " + scrolls[i] + " shifted");
            }
        }
    }

    @Test
    void aChangedBoxIsDrawnAfterAShift() {
        Frames frames = new Frames();
        frames.plan(camera(0, 30, 0, 0.3f, 1.1f), SUN);
        double[] min = {2, 22, 2};
        double[] max = {10, 30, 10};
        // Walk until the middle cascade shifts, with the box changed just before
        for (int step = 1; step <= 400; ++step) {
            frames.cache.invalidate(min, max);
            Camera moved = camera(step * 0.25, 30, step * 0.1, 0.3f, 1.1f);
            CascadeShadowSplit[] cascades = frames.plan(moved, SUN);
            if (cascades[1].getRedraw() != SunShadowCache.Redraw.SCROLL) {
                continue;
            }
            // The box waits a frame, then is drawn where it now is in the map
            CascadeShadowSplit[] next = frames.plan(moved, SUN);
            assertEquals(SunShadowCache.Redraw.PART, next[1].getRedraw());
            int[] rect = next[1].getRedrawRect();
            for (int c = 0; c < 8; ++c) {
                Vector3d corner =
                        new Vector3d(
                                (c & 1) == 0 ? min[0] : max[0],
                                (c & 2) == 0 ? min[1] : max[1],
                                (c & 4) == 0 ? min[2] : max[2]);
                Vector3f texel = texelOf(next[1], moved, corner);
                assertTrue(texel.x >= rect[0] && texel.x <= rect[2], "Corner " + c);
                assertTrue(texel.y >= rect[1] && texel.y <= rect[3], "Corner " + c);
            }
            return;
        }
        throw new AssertionError("The middle cascade never shifted");
    }

    @Test
    void theSliceAlwaysFits() {
        Frames frames = new Frames();
        Random random = new Random(5);
        Matrix4f inverseProjection = new Projection(WIDTH, HEIGHT).getInverseProjectionMatrix();
        for (int frame = 0; frame < 200; ++frame) {
            Camera camera =
                    camera(
                            frame * 1.7,
                            20 + random.nextDouble() * 40,
                            frame * -0.9,
                            (float) (random.nextDouble() * 1.2 - 0.6),
                            (float) (random.nextDouble() * 2 * Math.PI));
            CascadeShadowSplit[] cascades = frames.plan(camera, SUN);
            for (int i = 0; i < cascades.length; ++i) {
                if (!SunShadowCache.CACHED[i]) {
                    continue;
                }
                float far = -cascades[i].getSplitDistance();
                for (int y = -1; y <= 1; y += 2) {
                    for (int x = -1; x <= 1; x += 2) {
                        Vector4f point = new Vector4f(x, y, 0.5f, 1).mul(inverseProjection);
                        Vector3f ray = new Vector3f(point.x, point.y, point.z).div(point.w);
                        ray.div(-ray.z).mul(far);
                        Vector3f render = camera.getInvViewMatrix().transformPosition(ray);
                        Vector3d world = new Vector3d(render).add(camera.getPosition());
                        Vector3f texel = texelOf(cascades[i], camera, world);
                        int size = cascades[i].getMapSize();
                        assertTrue(texel.x >= 2 && texel.x <= size - 2, "Frame " + frame);
                        assertTrue(texel.y >= 2 && texel.y <= size - 2, "Frame " + frame);
                        assertTrue(texel.z > 0 && texel.z < 1, "Frame " + frame);
                    }
                }
            }
        }
    }

    @Test
    void aSunStepIsCaughtUpOneCascadeAFrame() {
        Frames frames = new Frames();
        Camera camera = camera(0, 30, 0, 0.3f, 0);
        frames.plan(camera, SUN);
        // Less than a step: nothing moves
        Vector3f nudged = new Vector3f(SUN).rotateY((float) Math.toRadians(0.1)).normalize();
        for (CascadeShadowSplit cascade : frames.plan(camera, nudged)) {
            if (cascade.isCached()) {
                assertEquals(SunShadowCache.Redraw.NONE, cascade.getRedraw());
            }
        }
        // A whole degree: the middle cascade first, the far one the frame after
        Vector3f turned = new Vector3f(SUN).rotateY((float) Math.toRadians(1)).normalize();
        CascadeShadowSplit[] first = frames.plan(camera, turned);
        assertEquals(SunShadowCache.Redraw.ALL, first[1].getRedraw());
        assertEquals(SunShadowCache.Redraw.NONE, first[2].getRedraw());
        CascadeShadowSplit[] second = frames.plan(camera, turned);
        assertEquals(SunShadowCache.Redraw.NONE, second[1].getRedraw());
        assertEquals(SunShadowCache.Redraw.ALL, second[2].getRedraw());
        CascadeShadowSplit[] third = frames.plan(camera, turned);
        assertEquals(SunShadowCache.Redraw.NONE, third[1].getRedraw());
        assertEquals(SunShadowCache.Redraw.NONE, third[2].getRedraw());
    }

    @Test
    void aChangedBoxIsDrawnAgainOnce() {
        Frames frames = new Frames();
        Camera camera = camera(0, 30, 0, 0.3f, 0);
        frames.plan(camera, SUN);
        double[] min = {2, 22, 2};
        double[] max = {10, 30, 10};
        frames.cache.invalidate(min, max);
        CascadeShadowSplit[] cascades = frames.plan(camera, SUN);
        for (int i = 0; i < cascades.length; ++i) {
            if (!SunShadowCache.CACHED[i]) {
                continue;
            }
            CascadeShadowSplit cascade = cascades[i];
            assertEquals(SunShadowCache.Redraw.PART, cascade.getRedraw(), "Cascade " + i);
            assertFalse(cascade.isStaticCulledAway());
            int[] rect = cascade.getRedrawRect();
            // Every corner of the box is inside the part, and the part is small
            for (int c = 0; c < 8; ++c) {
                Vector3d corner =
                        new Vector3d(
                                (c & 1) == 0 ? min[0] : max[0],
                                (c & 2) == 0 ? min[1] : max[1],
                                (c & 4) == 0 ? min[2] : max[2]);
                Vector3f texel = texelOf(cascade, camera, corner);
                assertTrue(texel.x >= rect[0] && texel.x <= rect[2], "Cascade " + i);
                assertTrue(texel.y >= rect[1] && texel.y <= rect[3], "Cascade " + i);
            }
            long area = (long) (rect[2] - rect[0]) * (rect[3] - rect[1]);
            long whole = (long) cascade.getMapSize() * cascade.getMapSize();
            assertTrue(area < whole / 4, "Cascade " + i + " part " + area);
        }
        for (CascadeShadowSplit cascade : frames.plan(camera, SUN)) {
            if (cascade.isCached()) {
                assertEquals(SunShadowCache.Redraw.NONE, cascade.getRedraw());
            }
        }
    }

    @Test
    void turnedOffEveryCascadeIsFittedAndDrawnWhole() {
        Frames frames = new Frames();
        Camera camera = camera(0, 30, 0, 0.3f, 0);
        frames.plan(camera, SUN);
        frames.cache.plan(
                frames.cascades,
                new Projection(WIDTH, HEIGHT).getInverseProjectionMatrix(),
                camera.getInvViewMatrix(),
                camera.getPosition(),
                SUN,
                SHADOW_DISTANCE,
                false);
        for (CascadeShadowSplit cascade : frames.cascades) {
            assertFalse(cascade.isCached());
            assertEquals(SunShadowCache.Redraw.ALL, cascade.getRedraw());
        }
        // Turned on again, the cached ones start over
        CascadeShadowSplit[] again = frames.plan(camera, SUN);
        for (int i = 0; i < again.length; ++i) {
            assertEquals(SunShadowCache.Redraw.ALL, again[i].getRedraw());
        }
    }
}
