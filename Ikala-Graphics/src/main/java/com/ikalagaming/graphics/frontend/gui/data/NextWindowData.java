package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.util.Rect;

import org.joml.Vector2f;

public class NextWindowData {
    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.NextWindowFlags
     */
    public int fieldFlags;

    public Condition positionCondition;
    public Vector2f positionValue;
    public Condition sizeCondition;
    public Vector2f sizeValue;
    public Condition collapsedCondition;
    public boolean collapsedValue;
    public Vector2f contentSizeValue;
    public Vector2f scrollValue;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.WindowFlags
     */
    public int windowFlags;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.ChildFlags
     */
    public int childFlags;

    public Rect sizeConstraintRect;
    public float backgroundAlpha;
    public Vector2f menuBarOffsetMinValue;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.RefreshFlags
     */
    public int windowRefreshFlags;
}
