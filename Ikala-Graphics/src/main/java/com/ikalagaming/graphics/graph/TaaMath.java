package com.ikalagaming.graphics.graph;

import lombok.NonNull;
import org.joml.Matrix4d;
import org.joml.Matrix4fc;
import org.joml.Vector3dc;

/**
 * The math of temporal anti-aliasing that doesn't need the GPU: where each frame's samples sit
 * within their pixels, and how a pixel finds where it was last frame. {@code taa.frag} does the
 * rest.
 */
public final class TaaMath {

    /**
     * How many frames the sample positions take to repeat. Eight points of the Halton (2, 3)
     * sequence cover a pixel evenly.
     */
    public static final int JITTER_SAMPLES = 8;

    /** The bases of the Halton sequence along x and y. */
    private static final int BASE_X = 2;

    private static final int BASE_Y = 3;

    /** Private constructor so this class is not instantiated. */
    private TaaMath() {}

    /**
     * One element of the Halton sequence: the index's digits in the base, mirrored behind the
     * point.
     *
     * @param index The index, from 1.
     * @param base The base, a prime.
     * @return A value in [0, 1).
     */
    public static double halton(int index, int base) {
        double result = 0;
        double fraction = 1;
        for (int i = index; i > 0; i /= base) {
            fraction /= base;
            result += fraction * (i % base);
        }
        return result;
    }

    /**
     * Where a frame's sample sits within its pixel, from its middle.
     *
     * @param frame The frame's number.
     * @return The offset in pixels along x and y, each in [-0.5, 0.5).
     */
    public static double[] jitter(long frame) {
        // Skip the sequence's first element, 0, which would put every eighth frame on the middle
        final int index = (int) Math.floorMod(frame, JITTER_SAMPLES) + 1;
        return new double[] {halton(index, BASE_X) - 0.5, halton(index, BASE_Y) - 0.5};
    }

    /**
     * The matrix taking a point in this frame's clip space to where it was in last frame's clip
     * space, for a world that stood still. Positions are relative to the camera, so the camera's
     * move between the frames sits between the two.
     *
     * @param projection This frame's projection, without jitter.
     * @param view This frame's view matrix, which only rotates.
     * @param previousProjection Last frame's projection, without jitter.
     * @param previousView Last frame's view matrix.
     * @param cameraMove How far the camera moved since last frame, this position less the last.
     * @return The matrix.
     */
    public static Matrix4d reprojection(
            @NonNull Matrix4fc projection,
            @NonNull Matrix4fc view,
            @NonNull Matrix4fc previousProjection,
            @NonNull Matrix4fc previousView,
            @NonNull Vector3dc cameraMove) {
        final Matrix4d toRender = new Matrix4d(projection).mul(new Matrix4d(view)).invert();
        return new Matrix4d(previousProjection)
                .mul(new Matrix4d(previousView))
                .translate(cameraMove.x(), cameraMove.y(), cameraMove.z())
                .mul(toRender);
    }
}
