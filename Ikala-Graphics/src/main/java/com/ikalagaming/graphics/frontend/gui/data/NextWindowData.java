package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.util.Rect;

import org.joml.Vector2f;

public class NextWindowData {
    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.NextWindowFlags
     */
    private int fieldFlags;

    private Condition positionCondition;
    private Vector2f positionValue;
    private Condition sizeCondition;
    private Vector2f sizeValue;
    private Condition collapsedCondition;
    private boolean collapsedValue;
    private Vector2f contentSizeValue;
    private Vector2f scrollValue;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.WindowFlags
     */
    private int windowFlags;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.ChildFlags
     */
    private int childFlags;

    private Rect sizeConstraintRect;
    private float backgroundAlpha;
    private Vector2f menuBarOffsetMinValue;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.RefreshFlags
     */
    private int windowRefreshFlags;
}
