package com.ikalagaming.graphics.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class EntityTest {

    private static final double FAR = 1_000_000_000.0;

    @Test
    void renderMatrixIsExactNearTheOriginFarFromTheWorldOrigin() {
        Vector3d position = new Vector3d(FAR + 0.25, -3e8, 5);
        Vector3d origin = new Vector3d(FAR, -3e8, 0);

        Matrix4f matrix =
                Entity.renderMatrix(position, origin, new Quaternionf(), 1, new Matrix4f());

        Vector3f translation = matrix.getTranslation(new Vector3f());
        assertEquals(0.25f, translation.x);
        assertEquals(0.0f, translation.y);
        assertEquals(5.0f, translation.z);
    }

    @Test
    void renderMatrixAppliesRotationAndScale() {
        Vector3d position = new Vector3d(FAR + 1, 0, 0);
        Vector3d origin = new Vector3d(FAR, 0, 0);
        // A quarter turn around y takes +x to -z
        Quaternionf rotation = new Quaternionf().rotationY((float) (Math.PI / 2));

        Matrix4f matrix = Entity.renderMatrix(position, origin, rotation, 2, new Matrix4f());

        Vector3f point = matrix.transformPosition(new Vector3f(1, 0, 0));
        assertEquals(1.0f, point.x, 1e-5f);
        assertEquals(0.0f, point.y, 1e-5f);
        assertEquals(-2.0f, point.z, 1e-5f);
    }
}
