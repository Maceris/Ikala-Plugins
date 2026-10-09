package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.flags.ChildFlags;
import com.ikalagaming.graphics.gui.flags.NextWindowFlags;
import com.ikalagaming.graphics.gui.flags.RefreshFlags;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.gui.util.RectFloat;

import lombok.NonNull;
import org.joml.Vector2f;

import java.util.function.Consumer;

/**
 * Storage for setNextWindow*() calls, which are consumed by the next call to begin(). Only values
 * with the corresponding flag set in {@link #fieldFlags} are valid.
 *
 * @see NextWindowFlags
 */
public class NextWindowData {

    /** Override background alpha. */
    public float backgroundAlpha;

    /**
     * @see com.ikalagaming.graphics.gui.flags.ChildFlags
     */
    public int childFlags;

    public @NonNull Condition collapsedCondition;
    public boolean collapsedValue;
    public final Vector2f contentSizeValue;

    public @NonNull Condition dockCondition;
    public int dockID;

    /**
     * Which fields are set.
     *
     * @see NextWindowFlags
     */
    public int fieldFlags;

    public final Vector2f menuBarOffsetMinValue;
    public @NonNull Condition positionCondition;
    public final Vector2f positionPivot;
    public boolean positionUndock;
    public final Vector2f positionValue;

    /** Scroll position, negative values on an axis mean "don't change". */
    public final Vector2f scrollValue;

    public @NonNull Condition sizeCondition;

    /**
     * Minimum (left, top) and maximum (right, bottom) size constraints. Negative values on an axis
     * mean "keep the current size" on that axis.
     */
    public final RectFloat sizeConstraintRect;

    /** An extra programmatic size constraint, may be null. Used with the size constraint flag. */
    public Consumer<SizeCallbackData> sizeCallback;

    public final Vector2f sizeValue;

    /** The ID of the viewport to use, from setNextWindowViewport(). */
    public int viewportID;

    /** The window class to apply, copied from setNextWindowClass(). */
    public final WindowClass windowClass;

    /**
     * @see WindowFlags
     */
    public int windowFlags;

    /**
     * @see RefreshFlags
     */
    public int windowRefreshFlags;

    public NextWindowData() {
        backgroundAlpha = 1.0f;
        childFlags = ChildFlags.NONE;
        collapsedCondition = Condition.NONE;
        collapsedValue = false;
        contentSizeValue = new Vector2f(0, 0);
        dockCondition = Condition.NONE;
        dockID = 0;
        fieldFlags = NextWindowFlags.NONE;
        menuBarOffsetMinValue = new Vector2f(0, 0);
        positionCondition = Condition.NONE;
        positionPivot = new Vector2f(0, 0);
        positionUndock = false;
        positionValue = new Vector2f(0, 0);
        scrollValue = new Vector2f(0, 0);
        sizeCondition = Condition.NONE;
        sizeConstraintRect = new RectFloat(0, 0, 0, 0);
        sizeCallback = null;
        sizeValue = new Vector2f(0, 0);
        viewportID = 0;
        windowClass = new WindowClass();
        windowFlags = WindowFlags.NONE;
        windowRefreshFlags = RefreshFlags.NONE;
    }

    /**
     * Mark all fields as unset, which is done once they've been consumed. Values are not reset, but
     * they are not valid unless the relevant flag is set.
     */
    public void clearFlags() {
        fieldFlags = NextWindowFlags.NONE;
    }
}
