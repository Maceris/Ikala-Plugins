package com.ikalagaming.graphics.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class CameraTest {

    private static final double FAR = 1_000_000_000.0;

    @Test
    void positionKeepsSmallMovesFarFromTheOrigin() {
        Camera camera = new Camera();
        camera.setPosition(FAR, 0, 0);

        camera.moveRight(0.01f);

        // A float at a billion can only step by 64, so this would be lost
        assertEquals(FAR + 0.01, camera.getPosition().x, 1e-6);
        assertEquals(0.0, camera.getPosition().y, 1e-9);
        assertEquals(0.0, camera.getPosition().z, 1e-9);
    }

    @Test
    void setPositionRoundTripsLargeDoubles() {
        Camera camera = new Camera();
        camera.setPosition(FAR + 0.125, -FAR, 12_345_678.5);

        assertEquals(FAR + 0.125, camera.getPosition().x);
        assertEquals(-FAR, camera.getPosition().y);
        assertEquals(12_345_678.5, camera.getPosition().z);
    }

    @Test
    void viewMatrixOnlyRotates() {
        Camera camera = new Camera();
        camera.setPosition(FAR, 5, -FAR);
        camera.setRotation(0.5f, 1.25f);

        Vector3f translation = camera.getViewMatrix().getTranslation(new Vector3f());
        assertEquals(0, translation.x, 1e-6);
        assertEquals(0, translation.y, 1e-6);
        assertEquals(0, translation.z, 1e-6);

        // The camera is at the origin of render space, so it stays there in view space
        Vector3f origin = camera.getViewMatrix().transformPosition(new Vector3f());
        assertEquals(0, origin.length(), 1e-6);
    }
}
