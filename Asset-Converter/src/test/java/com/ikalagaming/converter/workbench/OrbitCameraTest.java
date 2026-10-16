package com.ikalagaming.converter.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.scene.Camera;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/** Tests for turning the camera around a point. */
class OrbitCameraTest {

    /**
     * Where a camera is looking at, a distance in front of it.
     *
     * @param camera The camera.
     * @param distance How far in front.
     * @return The point.
     */
    private static Vector3d lookingAt(Camera camera, double distance) {
        return OrbitCamera.forward(camera.getRotation().x, camera.getRotation().y, new Vector3d())
                .mul(distance)
                .add(camera.getPosition());
    }

    @Test
    void testForwardMatchesTheCamera() {
        // No rotation looks down -z; positive pitch looks down
        Vector3d ahead = OrbitCamera.forward(0, 0, new Vector3d());
        assertEquals(-1, ahead.z, 1e-9);
        assertTrue(OrbitCamera.forward(0.5, 0, new Vector3d()).y < 0);
    }

    @Test
    void testFrameLooksAtTheCenter() {
        Camera camera = new Camera();
        camera.setRotation(0.4f, 1.0f);
        OrbitCamera orbit = new OrbitCamera();
        Vector3d center = new Vector3d(10, 2, -3);
        orbit.frame(camera, center, 5);
        assertTrue(orbit.getDistance() > 5);
        assertTrue(lookingAt(camera, orbit.getDistance()).distance(center) < 1e-5);
    }

    @Test
    void testTurnAndZoomKeepTheTarget() {
        Camera camera = new Camera();
        OrbitCamera orbit = new OrbitCamera();
        Vector3d center = new Vector3d(1, 2, 3);
        orbit.frame(camera, center, 2);
        orbit.turn(camera, center, 120, 40);
        assertTrue(lookingAt(camera, orbit.getDistance()).distance(center) < 1e-5);
        final double before = orbit.getDistance();
        orbit.zoom(camera, 2);
        assertEquals(
                before * OrbitCamera.ZOOM_STEP * OrbitCamera.ZOOM_STEP, orbit.getDistance(), 1e-9);
        assertEquals(orbit.getDistance(), camera.getPosition().distance(center), 1e-5);
    }

    @Test
    void testFollowsAFlownCamera() {
        Camera camera = new Camera();
        OrbitCamera orbit = new OrbitCamera();
        orbit.frame(camera, new Vector3d(), 1);
        camera.setPosition(50, 0, 0);
        orbit.follow(camera);
        // The point moved with the camera, the same distance in front
        assertTrue(orbit.getTarget().distance(lookingAt(camera, orbit.getDistance())) < 1e-9);
    }

    @Test
    void testTurningAroundAPivotOffToTheSide() {
        Camera camera = new Camera();
        camera.setPosition(0, 2, 10);
        camera.setRotation(0.2f, 0.3f);
        OrbitCamera orbit = new OrbitCamera();
        // Off to the side of where the camera looks
        Vector3d pivot = new Vector3d(-4, 0, 0);
        final double before = camera.getPosition().distance(pivot);
        // Where the pivot is seen from the camera stays the same as it swings around
        Vector3d seen = new Vector3d(pivot).sub(camera.getPosition());
        OrbitCamera.orientation(camera.getRotation().x, camera.getRotation().y)
                .transpose()
                .transform(seen);
        orbit.turn(camera, pivot, 150, -60);
        assertEquals(before, camera.getPosition().distance(pivot), 1e-4);
        Vector3d after = new Vector3d(pivot).sub(camera.getPosition());
        OrbitCamera.orientation(camera.getRotation().x, camera.getRotation().y)
                .transpose()
                .transform(after);
        assertTrue(after.distance(seen) < 1e-4);
        // The orientation turns -z to where the camera looks
        Vector3d look = OrbitCamera.orientation(0.2, 0.3).transform(new Vector3d(0, 0, -1));
        assertTrue(look.distance(OrbitCamera.forward(0.2, 0.3, new Vector3d())) < 1e-9);
    }
}
