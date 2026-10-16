package com.ikalagaming.converter.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.graph.Material;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/** Tests for the scene view's picking rays and the material editor's copies. */
class SceneViewTest {

    @Test
    void testRayThroughTheMiddleIsForward() {
        final double pitch = 0.3;
        final double yaw = -1.1;
        Vector3d ray = SceneView.rayDirection(0.5, 0.5, 16 / 9.0, pitch, yaw);
        Vector3d forward = OrbitCamera.forward(pitch, yaw, new Vector3d());
        assertTrue(ray.distance(forward) < 1e-9);
        // The top edge of the screen is half the field of view above the middle
        Vector3d top = SceneView.rayDirection(0.5, 0, 1, 0, 0);
        assertEquals(OrbitCamera.FIELD_OF_VIEW / 2, Math.atan2(top.y, -top.z), 1e-9);
        // Right of the middle is to the right, +x with no rotation
        assertTrue(SceneView.rayDirection(1, 0.5, 1, 0, 0).x > 0);
    }

    @Test
    void testCopyKeepsEverything() {
        Material source = new Material();
        source.getBaseColor().set(0.1f, 0.2f, 0.3f, 0.4f);
        source.setRoughness(0.7f);
        source.setMetallic(0.6f);
        source.getEmissive().set(2, 3, 4);
        source.setNormalGreenDown(true);
        source.setPackedOcclusion(true);
        source.setTransparency(Material.Transparency.CUTOUT);
        Material copy = MaterialEditor.copy(source);
        assertNotSame(source.getBaseColor(), copy.getBaseColor());
        assertEquals(source.getBaseColor(), copy.getBaseColor());
        assertEquals(0.7f, copy.getRoughness());
        assertEquals(0.6f, copy.getMetallic());
        assertEquals(source.getEmissive(), copy.getEmissive());
        assertTrue(copy.isNormalGreenDown());
        assertTrue(copy.isPackedOcclusion());
        assertSame(Material.Transparency.CUTOUT, copy.getTransparency());
    }
}
