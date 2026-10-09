package com.ikalagaming.graphics.scene;

import lombok.Getter;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * A camera in the world. Its position is in double precision, so it stays exact far from the
 * origin. Rendering happens in render space, which is world space moved so that the camera sits at
 * the origin, so the view matrix only rotates.
 */
public class Camera {
    /** A constant value used to clamp rotation, pre-calculated for convenience. */
    private static final float TWO_PI = (float) Math.PI * 2;

    private static final float X_MIN = (float) Math.toRadians(-90);
    private static final float X_MAX = (float) Math.toRadians(90);

    /**
     * Clamps the float between -90 and 90 degrees. NaN is considered less than the minimum.
     *
     * @param value The value we are clamping.
     * @return The value clamped between -90 and 90 degrees.
     */
    private static float clampRotationX(final float value) {
        if (Float.isNaN(value) || (value < X_MIN)) {
            return X_MIN;
        }
        return Math.min(value, X_MAX);
    }

    /** Used to calculate movements. */
    private final Vector3f temp;

    /**
     * The inverse of the view matrix, which converts view space back to render space.
     *
     * @return The inverse view matrix.
     */
    @Getter private final Matrix4f invViewMatrix;

    /**
     * The position of the camera in world space, which is the origin of render space.
     *
     * @return The position vector.
     */
    @Getter private final Vector3d position;

    /**
     * The rotation of the camera.
     *
     * @return The rotation vector.
     */
    @Getter private final Vector2f rotation;

    /**
     * The view matrix, which converts render space (world space relative to the camera) to view
     * space. It only rotates, since the camera is at the origin of render space.
     *
     * @return The current view matrix.
     */
    @Getter private final Matrix4f viewMatrix;

    /** Creates a new camera with default values. */
    public Camera() {
        temp = new Vector3f();
        position = new Vector3d();
        viewMatrix = new Matrix4f();
        invViewMatrix = new Matrix4f();
        rotation = new Vector2f();
    }

    /**
     * Add rotation to the current rotation.
     *
     * @param pitch The pitch component to add, in radians.
     * @param yaw The yaw component to add, in radians.
     */
    public void addRotation(float pitch, float yaw) {
        rotation.add(pitch, yaw);
        recalculate();
    }

    /**
     * Move backwards by the given increment.
     *
     * @param inc The amount to move by.
     */
    public void moveBackwards(float inc) {
        invViewMatrix.transformDirection(0, 0, 1, temp);
        position.add(temp.mul(inc));
        recalculate();
    }

    /**
     * Move down by the given increment.
     *
     * @param inc The amount to move by.
     */
    public void moveDown(float inc) {
        invViewMatrix.transformDirection(0, 1, 0, temp);
        position.sub(temp.mul(inc));
        recalculate();
    }

    /**
     * Move forwards by the given increment.
     *
     * @param inc The amount to move by.
     */
    public void moveForward(float inc) {
        invViewMatrix.transformDirection(0, 0, 1, temp);
        position.sub(temp.mul(inc));
        recalculate();
    }

    /**
     * Move left by the given increment.
     *
     * @param inc The amount to move by.
     */
    public void moveLeft(float inc) {
        invViewMatrix.transformDirection(1, 0, 0, temp);
        position.sub(temp.mul(inc));
        recalculate();
    }

    /**
     * Move right by the given increment.
     *
     * @param inc The amount to move by.
     */
    public void moveRight(float inc) {
        invViewMatrix.transformDirection(1, 0, 0, temp);
        position.add(temp.mul(inc));
        recalculate();
    }

    /**
     * Move up by the given increment.
     *
     * @param inc The amount to move by.
     */
    public void moveUp(float inc) {
        invViewMatrix.transformDirection(0, 1, 0, temp);
        position.add(temp.mul(inc));
        recalculate();
    }

    /** Recalculate the view and inverse view matrices. */
    private void recalculate() {
        rotation.x = clampRotationX(rotation.x);
        rotation.y %= Camera.TWO_PI;
        rotation.y = (rotation.y + Camera.TWO_PI) % Camera.TWO_PI;

        viewMatrix.identity().rotateX(rotation.x).rotateY(rotation.y);
        invViewMatrix.set(viewMatrix).invert();
    }

    /**
     * Set the position of the camera.
     *
     * @param x The new x position, in world space.
     * @param y The new y position, in world space.
     * @param z The new z position, in world space.
     */
    public void setPosition(double x, double y, double z) {
        position.set(x, y, z);
        recalculate();
    }

    /**
     * Set the rotation of the camera.
     *
     * @param x The new x (pitch) rotation, in radians.
     * @param y The new y (yaw) rotation, in radians.
     */
    public void setRotation(float x, float y) {
        rotation.set(x, y);
        recalculate();
    }
}
