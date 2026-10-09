package com.ikalagaming.graphics.scene;

import com.ikalagaming.graphics.scene.debug.DebugFrustum;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * The point of view used to decide what is visible, which level of detail to draw, and what to
 * stream in. Usually it follows the camera, but it can be frozen with {@link
 * Scene#freezeObserver()} so those decisions stay put while the camera flies around to inspect
 * them.
 *
 * <p>Culling, level of detail selection and streaming must use {@link Scene#getObserver()}, never
 * the camera directly. Rendering itself always uses the camera.
 */
@Getter
public final class Observer {

    /**
     * The position in world space.
     *
     * @return The position.
     */
    private final Vector3dc position;

    /**
     * The view matrix, which only rotates, like {@link Camera#getViewMatrix()}.
     *
     * @return The view matrix.
     */
    private final Matrix4fc viewMatrix;

    /**
     * The projection matrix.
     *
     * @return The projection matrix.
     */
    private final Matrix4fc projectionMatrix;

    /**
     * Capture the current point of view of a camera.
     *
     * @param camera The camera.
     * @param projection The projection used with the camera.
     */
    public Observer(@NonNull Camera camera, @NonNull Projection projection) {
        position = new Vector3d(camera.getPosition());
        viewMatrix = new Matrix4f(camera.getViewMatrix());
        projectionMatrix = new Matrix4f(projection.getProjectionMatrix());
    }

    /**
     * The inverse of projection × view, mapping clip space to world space, in double precision so
     * it stays exact far from the origin.
     *
     * @return A new matrix.
     */
    public Matrix4d inverseProjectionView() {
        return new Matrix4d(projectionMatrix)
                .mul(new Matrix4d(viewMatrix))
                .invert()
                .translateLocal(position.x(), position.y(), position.z());
    }

    /**
     * Make a debug frustum showing what the observer sees.
     *
     * @param color The packed RGBA color.
     * @param onTop Whether to draw over everything instead of depth testing.
     * @return The frustum, in world space.
     */
    public DebugFrustum frustum(int color, boolean onTop) {
        return DebugFrustum.fromInverseProjectionView(inverseProjectionView(), color, onTop);
    }
}
