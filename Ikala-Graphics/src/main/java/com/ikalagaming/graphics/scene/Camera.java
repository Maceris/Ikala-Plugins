/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.Utils;

import lombok.Getter;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

/** Represents a camera in the world space. */
public class Camera {
    /** A constant value used to clamp rotation, pre-calculated for convenience. */
    private static final float TWO_PI = (float) Math.PI * 2;

    /** Used to calculate movements. */
    private Vector3f temp;

    /**
     * The inverse of the view matrix.
     *
     * @return The inverse view matrix.
     */
    @Getter private Matrix4f invViewMatrix;

    /**
     * The position of the camera.
     *
     * @return The position vector.
     */
    @Getter private Vector3f position;

    /**
     * The rotation of the camera.
     *
     * @return The rotation vector.
     */
    @Getter private Vector2f rotation;

    /**
     * The view matrix.
     *
     * @return The current view matrix.
     */
    @Getter private Matrix4f viewMatrix;

    /** Creates a new camera with default values. */
    public Camera() {
        temp = new Vector3f();
        position = new Vector3f();
        viewMatrix = new Matrix4f();
        invViewMatrix = new Matrix4f();
        rotation = new Vector2f();
    }

    /**
     * Add rotation to the current rotation.
     *
     * @param x The x (pitch) rotation increment.
     * @param y The y (yaw) rotation increment.
     */
    public void addRotation(float x, float y) {
        rotation.add(x, y);
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
        rotation.x =
                Utils.clampFloat(
                        rotation.x, (float) Math.toRadians(-90), (float) Math.toRadians(90));

        rotation.y %= Camera.TWO_PI;
        rotation.y = (rotation.y + Camera.TWO_PI) % Camera.TWO_PI;

        viewMatrix
                .identity()
                .rotateX(rotation.x)
                .rotateY(rotation.y)
                .translate(-position.x, -position.y, -position.z);
        invViewMatrix.set(viewMatrix).invert();
    }

    /**
     * Set the position of the camera.
     *
     * @param x The new x position.
     * @param y The new y position.
     * @param z The new z position.
     */
    public void setPosition(float x, float y, float z) {
        position.set(x, y, z);
        recalculate();
    }

    /**
     * Set the rotation of the camera.
     *
     * @param x The new x (pitch) rotation.
     * @param y The new y (yaw) rotation.
     */
    public void setRotation(float x, float y) {
        rotation.set(x, y);
        recalculate();
    }
}
