package com.ikalagaming.graphics.bake;

import lombok.Getter;

/**
 * One of the six faces of a box, named by the direction it faces. A face mask holds one bit per
 * face, at {@link #bit()}.
 *
 * <p>Each face has a 2D grid across it, for coverage masks: {@code u} along {@link #getUAxis()} and
 * {@code v} along {@link #getVAxis()}, both increasing with the world axis. Opposite faces share
 * the same axes, so the faces of two boxes that touch line up cell for cell.
 */
@Getter
public enum Face {
    /** Facing -X. */
    NEG_X(0, -1),
    /** Facing +X. */
    POS_X(0, 1),
    /** Facing -Y. */
    NEG_Y(1, -1),
    /** Facing +Y. */
    POS_Y(1, 1),
    /** Facing -Z. */
    NEG_Z(2, -1),
    /** Facing +Z. */
    POS_Z(2, 1);

    /** Every face, in order, without allocating a new array each time. */
    public static final Face[] ALL = values();

    /** The value used in place of a face for triangles that aren't on one. */
    public static final byte NONE = -1;

    /**
     * The axis the face is perpendicular to: 0 for x, 1 for y, 2 for z. -- GETTER -- The axis.
     *
     * @return The axis index.
     */
    private final int axis;

    /**
     * Which way along the axis the face points, -1 or 1. -- GETTER -- The direction.
     *
     * @return -1 or 1.
     */
    private final int sign;

    /**
     * The axis that {@code u} runs along on the face's grid. -- GETTER -- The u axis.
     *
     * @return The axis index.
     */
    private final int uAxis;

    /**
     * The axis that {@code v} runs along on the face's grid. -- GETTER -- The v axis.
     *
     * @return The axis index.
     */
    private final int vAxis;

    /**
     * Set up a face.
     *
     * @param axis The axis it is perpendicular to.
     * @param sign Which way it points.
     */
    Face(int axis, int sign) {
        this.axis = axis;
        this.sign = sign;
        // The two other axes, in order
        this.uAxis = axis == 0 ? 2 : 0;
        this.vAxis = axis == 1 ? 2 : 1;
    }

    /**
     * The face's bit in a face mask.
     *
     * @return The bit.
     */
    public int bit() {
        return 1 << ordinal();
    }

    /**
     * The face pointing the other way.
     *
     * @return The opposite face.
     */
    public Face opposite() {
        return of(axis, -sign);
    }

    /**
     * Find a face by axis and direction.
     *
     * @param axis 0 for x, 1 for y, 2 for z.
     * @param sign -1 or 1.
     * @return The face.
     */
    public static Face of(int axis, int sign) {
        return ALL[axis * 2 + (sign > 0 ? 1 : 0)];
    }
}
