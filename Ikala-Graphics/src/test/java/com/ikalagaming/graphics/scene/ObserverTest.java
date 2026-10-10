package com.ikalagaming.graphics.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.scene.debug.DebugFrustum;

import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.jupiter.api.Test;

class ObserverTest {

    private static final double FAR = 1_000_000_000.0;

    @Test
    void observerFollowsTheCamera() {
        Camera camera = new Camera();
        Projection projection = new Projection(800, 600);
        camera.setPosition(FAR, 2, 3);

        Observer observer = new Observer(camera, projection);

        assertEquals(new Vector3d(FAR, 2, 3), observer.getPosition());
    }

    @Test
    void frustumIsCenteredOnTheObserverInWorldSpace() {
        Camera camera = new Camera();
        Projection projection = new Projection(800, 600);
        camera.setPosition(FAR, 0, -FAR);

        DebugFrustum frustum = new Observer(camera, projection).frustum(0, false);

        // The near plane is tiny and right in front of the camera, the far plane is far ahead
        Vector3d nearCenter = new Vector3d();
        Vector3d farCenter = new Vector3d();
        for (int i = 0; i < 4; ++i) {
            nearCenter.add(frustum.corners()[i]);
            farCenter.add(frustum.corners()[i + 4]);
        }
        nearCenter.div(4);
        farCenter.div(4);
        assertEquals(Projection.Z_NEAR, nearCenter.distance(camera.getPosition()), 1e-3);
        // The float projection matrix only keeps the far plane to about a percent once inverted
        assertEquals(
                Projection.Z_FAR, farCenter.distance(camera.getPosition()), Projection.Z_FAR / 100);
        // A camera with no rotation looks down -z
        assertTrue(farCenter.z < camera.getPosition().z());
    }

    @Test
    void frozenObserverStaysPutWhileTheCameraMoves() {
        // The material buffer starts empty, so the scene never touches Vulkan
        Scene scene = new Scene(800, 600);
        scene.getCamera().setPosition(1, 2, 3);
        assertFalse(scene.isObserverFrozen());

        scene.freezeObserver();
        scene.getCamera().setPosition(100, 200, 300);

        assertTrue(scene.isObserverFrozen());
        Vector3dc frozen = scene.getObserver().getPosition();
        assertEquals(new Vector3d(1, 2, 3), frozen);

        scene.unfreezeObserver();
        assertFalse(scene.isObserverFrozen());
        assertEquals(new Vector3d(100, 200, 300), scene.getObserver().getPosition());
    }
}
