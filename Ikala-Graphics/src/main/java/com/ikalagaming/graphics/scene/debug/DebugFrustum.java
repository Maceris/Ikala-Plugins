package com.ikalagaming.graphics.scene.debug;

import lombok.NonNull;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector4d;

/**
 * A frustum, or any other shape with 8 corners joined like a box.
 *
 * @param corners The 8 corners in world space: the near face counterclockwise starting from the
 *     bottom left, then the far face in the same order.
 * @param color The packed RGBA color.
 * @param onTop Whether to draw over everything instead of depth testing.
 */
public record DebugFrustum(@NonNull Vector3dc[] corners, int color, boolean onTop)
        implements DebugShape {

    /** The number of corners. */
    public static final int CORNER_COUNT = 8;

    /** Copy the corners so the shape can't change after it is made. */
    public DebugFrustum {
        if (corners.length != CORNER_COUNT) {
            throw new IllegalArgumentException("A frustum has exactly 8 corners");
        }
        Vector3dc[] copies = new Vector3dc[CORNER_COUNT];
        for (int i = 0; i < CORNER_COUNT; ++i) {
            copies[i] = new Vector3d(corners[i]);
        }
        corners = copies;
    }

    /**
     * Find the corners of the volume a projection and view covers, from the inverse of the combined
     * matrix. Uses Vulkan's clip space, where depth goes from 0 at the near plane to 1 at the far
     * plane.
     *
     * @param inverseProjectionView The inverse of projection × view, mapping clip space to world
     *     space.
     * @param color The packed RGBA color.
     * @param onTop Whether to draw over everything instead of depth testing.
     * @return The frustum.
     */
    public static DebugFrustum fromInverseProjectionView(
            @NonNull Matrix4dc inverseProjectionView, int color, boolean onTop) {
        final double[][] clipCorners = {
            {-1, -1}, {1, -1}, {1, 1}, {-1, 1},
        };
        Vector3dc[] corners = new Vector3dc[CORNER_COUNT];
        Vector4d corner = new Vector4d();
        for (int depth = 0; depth < 2; ++depth) {
            for (int i = 0; i < clipCorners.length; ++i) {
                corner.set(clipCorners[i][0], clipCorners[i][1], depth, 1);
                inverseProjectionView.transform(corner);
                corners[depth * 4 + i] =
                        new Vector3d(corner.x / corner.w, corner.y / corner.w, corner.z / corner.w);
            }
        }
        return new DebugFrustum(corners, color, onTop);
    }
}
