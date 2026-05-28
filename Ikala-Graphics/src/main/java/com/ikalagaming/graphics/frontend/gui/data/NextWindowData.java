package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.flags.ChildFlags;
import com.ikalagaming.graphics.frontend.gui.flags.NextWindowFlags;
import com.ikalagaming.graphics.frontend.gui.flags.RefreshFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;

import lombok.NonNull;
import org.joml.Vector2i;

public class NextWindowData {

    /** Override background alpha. */
    public float backgroundAlpha;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.ChildFlags
     */
    public int childFlags;

    public @NonNull Condition collapsedCondition;
    public boolean collapsedValue;
    public final Vector2i contentSizeValue;

    public @NonNull Condition dockCondition;
    public int dockID;

    /**
     * Which fields of the next window data are set.
     *
     * @see com.ikalagaming.graphics.frontend.gui.flags.NextWindowFlags
     */
    public int fieldFlags;

    public final Vector2i menuBarOffsetMinValue;

    public @NonNull Condition positionCondition;
    public final Vector2i positionPivot;
    public boolean positionUndock;
    public final Vector2i positionValue;
    public final Vector2i scrollValue;
    public @NonNull Condition sizeCondition;
    public final RectFloat sizeConstraintRect;
    public final Vector2i sizeValue;

    /** Only non-null if we specified the viewport for the next window. */
    public Viewport viewport;

    /**
     * Only honored by beginTable.
     *
     * @see com.ikalagaming.graphics.frontend.gui.flags.WindowFlags
     */
    public int windowFlags;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.RefreshFlags
     */
    public int windowRefreshFlags;

    public NextWindowData() {
        backgroundAlpha = 1.0f;
        childFlags = ChildFlags.NONE;
        collapsedCondition = Condition.NONE;
        collapsedValue = false;
        contentSizeValue = new Vector2i(0, 0);
        dockCondition = Condition.NONE;
        dockID = 0;
        fieldFlags = NextWindowFlags.NONE;
        menuBarOffsetMinValue = new Vector2i(0, 0);
        positionCondition = Condition.NONE;
        positionPivot = new Vector2i(0, 0);
        positionUndock = false;
        positionValue = new Vector2i(0, 0);
        scrollValue = new Vector2i(0, 0);
        sizeCondition = Condition.NONE;
        sizeConstraintRect = new RectFloat(0, 0, 0, 0);
        sizeValue = new Vector2i(0, 0);
        viewport = null;
        windowFlags = WindowFlags.NONE;
        windowRefreshFlags = RefreshFlags.NONE;
    }
}
