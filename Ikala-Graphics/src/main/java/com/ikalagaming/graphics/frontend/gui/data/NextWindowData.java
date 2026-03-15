package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.flags.ChildFlags;
import com.ikalagaming.graphics.frontend.gui.flags.NextWindowFlags;
import com.ikalagaming.graphics.frontend.gui.flags.RefreshFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
import com.ikalagaming.graphics.frontend.gui.util.Rect;

import org.joml.Vector2i;

public class NextWindowData {
    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.NextWindowFlags
     */
    public int fieldFlags;

    public Condition positionCondition;
    public final Vector2i positionValue;
    public final Vector2i positionPivot;
    public Condition sizeCondition;
    public final Vector2i sizeValue;
    public Condition collapsedCondition;
    public boolean collapsedValue;
    public final Vector2i contentSizeValue;
    public final Vector2i scrollValue;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.WindowFlags
     */
    public int windowFlags;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.ChildFlags
     */
    public int childFlags;

    public final Rect sizeConstraintRect;
    public float backgroundAlpha;
    public final Vector2i menuBarOffsetMinValue;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.RefreshFlags
     */
    public int windowRefreshFlags;

    /** Only non-null if we specified the viewport for the next window. */
    public Viewport viewport;

    public NextWindowData() {
        fieldFlags = NextWindowFlags.NONE;
        positionCondition = Condition.NONE;
        positionValue = new Vector2i(0, 0);
        positionPivot = new Vector2i(0, 0);
        sizeCondition = Condition.NONE;
        sizeValue = new Vector2i(0, 0);
        collapsedCondition = Condition.NONE;
        collapsedValue = false;
        contentSizeValue = new Vector2i(0, 0);
        scrollValue = new Vector2i(0, 0);
        windowFlags = WindowFlags.NONE;
        childFlags = ChildFlags.NONE;
        sizeConstraintRect = new Rect(0, 0, 0, 0);
        backgroundAlpha = 1.0f;
        menuBarOffsetMinValue = new Vector2i(0, 0);
        windowRefreshFlags = RefreshFlags.NONE;
        viewport = null;
    }
}
