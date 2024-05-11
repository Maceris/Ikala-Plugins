package com.ikalagaming.graphics.scene;

import lombok.Getter;
import org.joml.Matrix4f;

/**
 * A projection matrix. More specifically a symmetric perspective projection frustum transformation
 * for a right-handed coordinate system using OpenGL's NDC z range of [-1..+1], and its inverse.
 */
@Getter
public class Projection {
    /** The field of view in radians. */
    private static final float FOV = (float) Math.toRadians(60.0f);

    /** The far clipping plane distance, in world units. */
    public static final float Z_FAR = 1000.f;

    /** The near clipping plane distance, in world units. */
    public static final float Z_NEAR = 0.01f;

    /**
     * The inverse of the current projection matrix.
     *
     * @return The inverse projection matrix.
     */
    private final Matrix4f inverseProjectionMatrix;

    /**
     * The current projection matrix.
     *
     * @return The projection matrix.
     */
    private final Matrix4f projectionMatrix;

    /**
     * Create a new projection matrix.
     *
     * @param width The width of the screen in pixels.
     * @param height The height of the screen in pixels.
     */
    public Projection(int width, int height) {
        projectionMatrix = new Matrix4f();
        inverseProjectionMatrix = new Matrix4f();
        updateProjMatrix(width, height);
    }

    /**
     * Update the projection matrix to a new screen size.
     *
     * @param width The new width of the screen in pixels.
     * @param height The new height of the screen in pixels.
     */
    public void updateProjMatrix(int width, int height) {
        // TODO(ches) support Directx/Vulkan [0, 1] Z range, or force it to be always [0, 1]
        projectionMatrix.setPerspective(
                Projection.FOV, (float) width / height, Projection.Z_NEAR, Projection.Z_FAR);
        inverseProjectionMatrix.set(projectionMatrix).invert();
    }
}
