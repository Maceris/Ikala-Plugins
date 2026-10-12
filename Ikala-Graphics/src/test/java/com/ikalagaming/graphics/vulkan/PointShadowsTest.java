package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.LightHandle;
import com.ikalagaming.graphics.scene.lights.LightRegistry;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Which lights get point shadow maps, and how their faces are kept and drawn again. */
class PointShadowsTest {

    private static final String OWNER = "test";
    private static final Vector3d EYE = new Vector3d(0, 2, 0);
    private static final Vector3f WARM = new Vector3f(1, 0.85f, 0.6f);

    private static float[] everywhere() {
        final float[] planes = new float[FrustumPlanes.FLOATS];
        FrustumPlanes.acceptAll(planes);
        return planes;
    }

    private static LightHandle torch(LightRegistry registry, double x, double y, double z) {
        final LightHandle handle =
                registry.addPoint(OWNER, new Vector3d(x, y, z), WARM, 1, Float.NaN);
        registry.setCastsShadows(OWNER, handle, true);
        return handle;
    }

    private static void plan(PointShadows points, LightRegistry registry, int budget) {
        points.plan(registry, EYE, everywhere(), EYE, budget, 2);
    }

    /** Draw every face that is due, as the shadow stage would, until none is. */
    private static int drawAll(PointShadows points) {
        int drawn = 0;
        List<PointShadows.Face> faces;
        while (!(faces = points.facesToDraw()).isEmpty()) {
            faces.forEach(points::drawn);
            drawn += faces.size();
        }
        return drawn;
    }

    @Test
    void aNewMapCastsShadowsOnceEveryFaceIsDrawn() {
        final LightRegistry registry = new LightRegistry();
        final LightHandle light = torch(registry, 5, 2, 0);
        final PointShadows points = new PointShadows();
        plan(points, registry, 4);
        assertEquals(1, points.getMapCount());
        assertEquals(-1, points.mapOf(light.slot()), "Not before its faces are drawn");
        assertEquals(PointShadows.FACES, drawAll(points));
        plan(points, registry, 4);
        assertTrue(points.mapOf(light.slot()) >= 0);
        // Kept: nothing to draw again
        assertTrue(points.facesToDraw().isEmpty());
    }

    @Test
    void lightsThatCastNoShadowsGetNoMap() {
        final LightRegistry registry = new LightRegistry();
        registry.addPoint(OWNER, new Vector3d(5, 2, 0), WARM, 1, Float.NaN);
        final PointShadows points = new PointShadows();
        plan(points, registry, 4);
        assertEquals(0, points.getMapCount());
        assertEquals(0, points.getCastingLights());
    }

    @Test
    void movingOrAChangeNearbyDrawsTheFacesAgain() {
        final LightRegistry registry = new LightRegistry();
        final LightHandle light = torch(registry, 5, 2, 0);
        final PointShadows points = new PointShadows();
        plan(points, registry, 4);
        drawAll(points);
        // A section far away changes nothing
        points.invalidate(new double[] {200, 0, 200}, new double[] {216, 16, 216});
        assertTrue(points.facesToDraw().isEmpty());
        // One next to it does, and the map keeps casting shadows meanwhile
        points.invalidate(new double[] {0, 0, 0}, new double[] {16, 16, 16});
        assertEquals(PointShadows.FACES, points.facesToDraw().size());
        drawAll(points);
        registry.move(OWNER, light, new Vector3d(6, 2, 0));
        plan(points, registry, 4);
        assertTrue(points.mapOf(light.slot()) >= 0);
        assertEquals(PointShadows.FACES, points.facesToDraw().size());
    }

    @Test
    void aTightGroupSharesAMapAndFacesAreSpreadOverFrames() {
        final LightRegistry registry = new LightRegistry();
        final Set<Integer> group = new HashSet<>();
        for (int i = 0; i < 4; ++i) {
            group.add(torch(registry, 10 + 0.3 * i, 2, 0).slot());
        }
        for (int i = 1; i <= 5; ++i) {
            torch(registry, -15.0 * i, 2, 5);
        }
        final PointShadows points = new PointShadows();
        plan(points, registry, 3);
        assertEquals(3, points.getMapCount());
        // At most a frame's worth of faces at a time
        assertTrue(points.facesToDraw().size() <= PointShadows.MAX_FACE_REDRAWS_PER_FRAME);
        drawAll(points);
        plan(points, registry, 3);
        final Set<Integer> maps = new HashSet<>();
        for (int slot : group) {
            maps.add(points.mapOf(slot));
        }
        assertEquals(1, maps.size(), "The torches share one map");
        assertTrue(maps.iterator().next() >= 0);
        assertEquals(group.size() + 2, points.getShadowedLights());
    }

    @Test
    void eachFaceSeesItsSideAndTilesDontOverlap() {
        final LightRegistry registry = new LightRegistry();
        torch(registry, 5, 2, 0);
        final PointShadows points = new PointShadows();
        plan(points, registry, 1);
        final int map = points.maps().get(0);
        final Vector3d center = new Vector3d(points.center(map));
        final Vector3f[] sides = {
            new Vector3f(3, 0.5f, -1),
            new Vector3f(-3, 1, 0.5f),
            new Vector3f(0.5f, 3, 1),
            new Vector3f(-1, -3, 0.5f),
            new Vector3f(0.5f, 1, 3),
            new Vector3f(1, -0.5f, -3)
        };
        final Set<String> tiles = new HashSet<>();
        for (int face = 0; face < PointShadows.FACES; ++face) {
            // Render space with the camera at the eye
            final Matrix4f matrix = points.faceMatrix(map, face, EYE, new Matrix4f());
            final Vector4f clip =
                    new Vector4f(
                                    (float) (center.x - EYE.x) + sides[face].x,
                                    (float) (center.y - EYE.y) + sides[face].y,
                                    (float) (center.z - EYE.z) + sides[face].z,
                                    1)
                            .mul(matrix);
            final float x = clip.x / clip.w;
            final float y = clip.y / clip.w;
            final float z = clip.z / clip.w;
            assertTrue(Math.abs(x) < 1 && Math.abs(y) < 1, "Face " + face);
            assertTrue(z > 0 && z < 1, "Face " + face);
            final int[] origin = PointShadows.tileOrigin(map, face);
            assertTrue(tiles.add(origin[0] + "," + origin[1]));
            assertTrue(origin[0] + PointShadows.TILE_SIZE <= PointShadows.ATLAS_SIZE);
            assertTrue(origin[1] + PointShadows.TILE_SIZE <= PointShadows.ATLAS_SIZE);
        }
    }
}
