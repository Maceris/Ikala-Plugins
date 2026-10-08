package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.util.RectFloat;

import org.joml.Vector2f;

/**
 * Box-selection state, currently used by multi-selection. Only one box-selection is active at a
 * time.
 */
public class BoxSelectState {
    /** The ID of the active box-select. */
    public int id;

    /** If the box-select is being dragged. */
    public boolean isActive;

    /** If we are considering starting a box-select, after an initial click. */
    public boolean isStarting;

    /** If the starting click was not on an item. */
    public boolean isStartedFromVoid;

    /** If the first item touched should act as pressed, to set the nav ID once. */
    public boolean isStartedSetNavIDOnce;

    /** Request to clear the selection. */
    public boolean requestClear;

    /** Latched key modifiers for box-select logic. */
    public int keyMods;

    /** The start position, relative to the window contents (to support scrolling). */
    public final Vector2f startPositionRelative;

    /** The end position, relative to the window contents. */
    public final Vector2f endPositionRelative;

    /** Scrolling accumulator, for high frame rates. */
    public final Vector2f scrollAccumulator;

    /** The window that the box-select is in. */
    public Window window;

    /**
     * Temporary, set/cleared by the multi-select scope that owns the active box-select. While set,
     * items supporting box-select skip clipping inside the {@link #unclipRect}.
     */
    public boolean unclipMode;

    /** The rectangle where clipping may be temporarily disabled. */
    public final RectFloat unclipRect;

    /** Per-axis versions of the unclip rect. */
    public final RectFloat[] unclipRects;

    /** The previous frame's selection rectangle, in absolute coordinates. */
    public final RectFloat boxSelectRectPrevious;

    /** The current selection rectangle, in absolute coordinates. */
    public final RectFloat boxSelectRectCurrent;

    /** Create a new, inactive box-select state. */
    public BoxSelectState() {
        startPositionRelative = new Vector2f();
        endPositionRelative = new Vector2f();
        scrollAccumulator = new Vector2f();
        unclipRect = new RectFloat(0, 0, 0, 0);
        unclipRects = new RectFloat[] {new RectFloat(0, 0, 0, 0), new RectFloat(0, 0, 0, 0)};
        boxSelectRectPrevious = new RectFloat(0, 0, 0, 0);
        boxSelectRectCurrent = new RectFloat(0, 0, 0, 0);
    }
}
