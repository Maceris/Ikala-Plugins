package com.ikalagaming.graphics.gui.data;

import org.joml.Vector2f;

/**
 * The data passed to a size constraint callback set with setNextWindowSizeConstraints(). The
 * callback may change {@link #desiredSize} to apply programmatic constraints, like a fixed aspect
 * ratio.
 */
public class SizeCallbackData {
    /** The window position, for reference. Read only. */
    public final Vector2f position = new Vector2f();

    /** The current window size. Read only. */
    public final Vector2f currentSize = new Vector2f();

    /**
     * The desired size, based on the user's mouse position and the min/max constraints. Write to
     * this field to restrain resizing.
     */
    public final Vector2f desiredSize = new Vector2f();
}
