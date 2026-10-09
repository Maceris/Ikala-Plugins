package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.util.RectFloat;

import lombok.NonNull;
import org.joml.Vector2f;

/**
 * The bounds of a connected monitor/display, and its DPI. This is required when enabling multiple
 * viewports, and is used for multiple DPI support, as well as clamping the position of popups and
 * tooltips, so they don't straddle multiple monitors.
 */
public class PlatformMonitor {
    /** The position of the area displayed on this monitor, in desktop coordinates. */
    public final Vector2f mainPosition;

    /** The size of the area displayed on this monitor. */
    public final Vector2f mainSize;

    /**
     * The position of the area without task bars, side bars, menu bars. Used to avoid positioning
     * popups/tooltips inside those regions. If you don't have this info, copy the main position.
     */
    public final Vector2f workPosition;

    /**
     * The size of the area without task bars, side bars, menu bars. If you don't have this info,
     * copy the main size.
     */
    public final Vector2f workSize;

    /** The DPI scale of the monitor, where 1.0 is 96 DPI. */
    public float dpiScale;

    /** Backend dependent data, like a GLFW monitor handle. */
    public Object platformHandle;

    public PlatformMonitor() {
        mainPosition = new Vector2f(0, 0);
        mainSize = new Vector2f(0, 0);
        workPosition = new Vector2f(0, 0);
        workSize = new Vector2f(0, 0);
        dpiScale = 1.0f;
        platformHandle = null;
    }

    /**
     * Copy the values from another monitor.
     *
     * @param other The monitor to copy.
     * @return This monitor, for chaining.
     */
    public PlatformMonitor set(@NonNull PlatformMonitor other) {
        mainPosition.set(other.mainPosition);
        mainSize.set(other.mainSize);
        workPosition.set(other.workPosition);
        workSize.set(other.workSize);
        dpiScale = other.dpiScale;
        platformHandle = other.platformHandle;
        return this;
    }

    /**
     * Fetch the main rectangle of the monitor.
     *
     * @param output Where to store the result.
     * @return The output rectangle, for convenience.
     */
    public RectFloat getMainRect(@NonNull RectFloat output) {
        output.set(
                mainPosition.x,
                mainPosition.y,
                mainPosition.x + mainSize.x,
                mainPosition.y + mainSize.y);
        return output;
    }

    /**
     * Fetch the work rectangle of the monitor.
     *
     * @param output Where to store the result.
     * @return The output rectangle, for convenience.
     */
    public RectFloat getWorkRect(@NonNull RectFloat output) {
        output.set(
                workPosition.x,
                workPosition.y,
                workPosition.x + workSize.x,
                workPosition.y + workSize.y);
        return output;
    }
}
