package com.ikalagaming.graphics.ui;

/** A layout axis. */
enum Axis {
    /** Horizontal. */
    X,
    /** Vertical. */
    Y;

    /**
     * The index of this axis in size and position arrays.
     *
     * @return 0 for x, 1 for y.
     */
    int index() {
        return ordinal();
    }

    /**
     * The other axis.
     *
     * @return The perpendicular axis.
     */
    Axis other() {
        return this == X ? Y : X;
    }
}
