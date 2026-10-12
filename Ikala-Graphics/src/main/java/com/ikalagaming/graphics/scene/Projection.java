package com.ikalagaming.graphics.scene;

import lombok.Getter;
import org.joml.Matrix4f;

/**
 * A projection matrix. More specifically a symmetric perspective projection frustum transformation
 * for a right-handed coordinate system using Vulkan's NDC z range of [0..+1], and its inverse.
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
     * The projection matrix moved by this frame's sample offset, for temporal anti-aliasing: what
     * everything drawing the scene and rebuilding positions from its depth uses. The same as {@link
     * #projectionMatrix} while there is no offset.
     *
     * @return The jittered projection matrix.
     */
    private final Matrix4f jitteredProjectionMatrix;

    /**
     * The inverse of {@link #jitteredProjectionMatrix}.
     *
     * @return The inverse jittered projection matrix.
     */
    private final Matrix4f inverseJitteredProjectionMatrix;

    /** The screen's width in pixels. */
    private int width;

    /** The screen's height in pixels. */
    private int height;

    /**
     * This frame's sample offset along x, in pixels from each pixel's middle.
     *
     * @return The offset.
     */
    private float jitterX;

    /**
     * This frame's sample offset along y, in pixels from each pixel's middle.
     *
     * @return The offset.
     */
    private float jitterY;

    /**
     * Create a new projection matrix.
     *
     * @param width The width of the screen in pixels.
     * @param height The height of the screen in pixels.
     */
    public Projection(int width, int height) {
        projectionMatrix = new Matrix4f();
        inverseProjectionMatrix = new Matrix4f();
        jitteredProjectionMatrix = new Matrix4f();
        inverseJitteredProjectionMatrix = new Matrix4f();
        updateProjMatrix(width, height);
    }

    /**
     * Update the projection matrix to a new screen size.
     *
     * @param width The new width of the screen in pixels.
     * @param height The new height of the screen in pixels.
     */
    public void updateProjMatrix(int width, int height) {
        // Vulkan clip space depth is [0, 1]
        projectionMatrix.setPerspective(
                Projection.FOV, (float) width / height, Projection.Z_NEAR, Projection.Z_FAR, true);
        inverseProjectionMatrix.set(projectionMatrix).invert();
        this.width = width;
        this.height = height;
        setJitter(jitterX, jitterY);
    }

    /**
     * Move every sample by a fraction of a pixel, for temporal anti-aliasing. Render thread only.
     *
     * @param x The offset along x, in pixels.
     * @param y The offset along y, in pixels.
     */
    public void setJitter(float x, float y) {
        jitterX = x;
        jitterY = y;
        // A shift in clip space, scaled by w so it is the same everywhere on the screen: two units
        // of normalized device coordinates span the screen, and w is minus the view space z, which
        // these entries multiply
        jitteredProjectionMatrix.set(projectionMatrix);
        if (width > 0 && height > 0) {
            jitteredProjectionMatrix.m20(projectionMatrix.m20() - 2 * x / width);
            jitteredProjectionMatrix.m21(projectionMatrix.m21() - 2 * y / height);
        }
        inverseJitteredProjectionMatrix.set(jitteredProjectionMatrix).invert();
    }
}
