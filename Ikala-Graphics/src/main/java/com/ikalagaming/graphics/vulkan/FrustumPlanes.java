package com.ikalagaming.graphics.vulkan;

import lombok.NonNull;
import org.joml.Matrix4fc;
import org.joml.Vector3dc;
import org.joml.Vector3fc;
import org.joml.Vector4f;

/**
 * Frustum planes for culling, and the box test the culling shader does with them. A plane is four
 * floats {@code (a, b, c, d)}, and a point {@code p} is on its inside when {@code a*px + b*py +
 * c*pz + d >= 0}. A frustum is six planes, 24 floats, in render space.
 *
 * <p>The planes come from a projection × view matrix the way JOML extracts them, which assumes
 * OpenGL's depth range: with Vulkan's 0 to 1 depth, the near plane comes out a little behind the
 * real one, which only keeps a few more things than needed.
 */
public final class FrustumPlanes {

    /** How many floats a frustum's planes take. */
    public static final int FLOATS = 6 * 4;

    /**
     * Extract the planes of a projection × view matrix in render space.
     *
     * @param projectionView The matrix.
     * @param out Receives the planes, {@link #FLOATS} floats.
     */
    public static void extract(@NonNull Matrix4fc projectionView, float @NonNull [] out) {
        Vector4f plane = new Vector4f();
        for (int i = 0; i < 6; ++i) {
            projectionView.frustumPlane(i, plane);
            out[i * 4] = plane.x;
            out[i * 4 + 1] = plane.y;
            out[i * 4 + 2] = plane.z;
            out[i * 4 + 3] = plane.w;
        }
    }

    /**
     * Extract the planes of a point of view somewhere other than the camera, moved into the
     * camera's render space. The move is worked out in double precision, so a frozen observer far
     * from the origin still culls exactly what it saw.
     *
     * @param projectionView The point of view's projection × view matrix, relative to its own
     *     position.
     * @param from The point of view's world position.
     * @param camera The camera's world position, the render space origin.
     * @param out Receives the planes, {@link #FLOATS} floats.
     */
    public static void extract(
            @NonNull Matrix4fc projectionView,
            @NonNull Vector3dc from,
            @NonNull Vector3dc camera,
            float @NonNull [] out) {
        extract(projectionView, out);
        // A point p in render space is at p + (camera - from) relative to the point of view
        final double dx = camera.x() - from.x();
        final double dy = camera.y() - from.y();
        final double dz = camera.z() - from.z();
        for (int i = 0; i < 6; ++i) {
            final int base = i * 4;
            out[base + 3] =
                    (float)
                            (out[base + 3]
                                    + out[base] * dx
                                    + out[base + 1] * dy
                                    + out[base + 2] * dz);
        }
    }

    /**
     * Planes that accept everything, for drawing without culling.
     *
     * @param out Receives the planes, {@link #FLOATS} floats.
     */
    public static void acceptAll(float @NonNull [] out) {
        for (int i = 0; i < 6; ++i) {
            out[i * 4] = 0;
            out[i * 4 + 1] = 0;
            out[i * 4 + 2] = 0;
            out[i * 4 + 3] = 1;
        }
    }

    /**
     * Whether a box is at least partly inside every plane, as the culling shader decides it: the
     * box's corner farthest along each plane's normal must be inside.
     *
     * @param planes The planes, {@link #FLOATS} floats.
     * @param center The box's center.
     * @param extents Half the box's size along each axis.
     * @return True if the box may be visible.
     */
    public static boolean boxInside(
            float @NonNull [] planes, @NonNull Vector3fc center, @NonNull Vector3fc extents) {
        for (int i = 0; i < 6; ++i) {
            final int base = i * 4;
            final float distance =
                    planes[base] * center.x()
                            + planes[base + 1] * center.y()
                            + planes[base + 2] * center.z()
                            + planes[base + 3];
            final float radius =
                    Math.abs(planes[base]) * extents.x()
                            + Math.abs(planes[base + 1]) * extents.y()
                            + Math.abs(planes[base + 2]) * extents.z();
            if (distance + radius < 0) {
                return false;
            }
        }
        return true;
    }

    /** Static helpers only. */
    private FrustumPlanes() {}
}
