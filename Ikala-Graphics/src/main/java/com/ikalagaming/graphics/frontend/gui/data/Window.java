package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.enums.Direction;
import com.ikalagaming.graphics.frontend.gui.enums.Visibility;
import com.ikalagaming.graphics.frontend.gui.flags.ConditionAllowed;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
import com.ikalagaming.graphics.frontend.gui.util.RectInt;
import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;
import org.joml.Vector2i;

import java.util.ArrayList;
import java.util.List;

public class Window {
    public boolean active;

    /**
     * Whether the window is in the process of appearing after being hidden or inactive, or the
     * first frame it's displayed.
     */
    public boolean appearing;

    public int baseOffsetCurrentLine;
    public int baseOffsetPreviousLine;
    public Direction borderBeingDragged;
    public Direction borderBeingHovered;

    /** Thickness of the border, in pixels. 0 if there is no border. */
    public int borderSize;

    public final List<Window> childWindows;

    public boolean collapsed;

    /**
     * Flags for the collapsed conditions.
     *
     * @see ConditionAllowed
     */
    public int collapsedConditionAllowed;

    public boolean collapseToggleRequested;

    public float currentItemWidth;

    public int currentTableIndex;

    public float currentTextWrapPosition;

    /**
     * Grows as content is added, and used to determine the content region next frame, for
     * auto-resize.
     */
    public final Vector2i cursorIdealMaxPosition;

    /**
     * Grows as content is added, and used to determine the content region next frame, for scrolling
     * and auto-resize.
     */
    public final Vector2i cursorMaxPosition;

    public final Vector2i cursorPosition;
    public final Vector2i cursorPreviousLinePosition;

    public final Vector2i cursorStartPosition;
    public DrawList drawList;
    public int flags;
    public int flagsAsChildWindow;
    public boolean hidden;

    public int id;

    public int idAsPopupWindow;
    public IntArrayList idStack;
    public int idWithinParent;
    public float indent;
    public final IntArrayList itemWidthStack;
    public final Vector2i lineSizeCurrent;
    public final Vector2i lineSizePrevious;

    /** The height of the menu bar in pixels. 0 if there is no visible menu bar. */
    public int menuBarHeight;

    public final String name;

    /**
     * Only non-null if this window has a close button, used to communicate elsewhere that we closed
     * the window.
     */
    public IkBoolean open;

    public final Vector2i padding;

    public Window parentWindow;

    public final Vector2i position;

    /**
     * Flags for the position conditions.
     *
     * @see ConditionAllowed
     */
    public int positionConditionAllowed;

    /** The region that contains the contents, including parts scrolled out of view. */
    public final RectInt rectContent;

    /** Current clipping rect, since we can push and pop clip rects. */
    public final RectInt rectCurrentClip;

    /** The outer region of the window. */
    public final RectInt rectFull;

    /** The inner part of the window, excluding the title bar, menu, scroll bars. */
    public final RectInt rectInner;

    /** Inner rect, but shrunk by 0.5 * padding, and clipped by the viewport or parent clip rect. */
    public final RectInt rectInnerClip;

    public boolean reuseLastFrameContents;
    public Window rootWindow;
    public Window rootWindowForNavigation;
    public Window rootWindowForTitleBarHighlight;
    public Window rootWindowIncludingPopups;

    /**
     * Corner rounding radius for the window. Window flags are used to specify which corners are
     * rounded.
     */
    public float rounding;

    public boolean sameLine;

    /** X is the width of the vertical scrollbar, y is the height of the horizontal scrollbar. */
    public final Vector2i scrollbarSizes;

    public final Vector2i scrollExtent;

    public final Vector2i scrollPosition;

    public final Vector2i scrollTarget;
    public boolean setPos;
    public @NonNull Visibility showScrollbarX;
    public @NonNull Visibility showScrollbarY;

    /**
     * Flags for the size conditions.
     *
     * @see ConditionAllowed
     */
    public int sizeConditionAllowed;

    /** Size, which might be smaller than the full size if minimized. */
    public final Vector2i sizeCurrent;

    /** Size the window would like to be, given its contents. */
    public final Vector2i sizeDesired;

    /** The full size of the window when not collapsed. */
    public final Vector2i sizeFull;

    /** Size that was requested for the window before it was drawn. */
    public final Vector2i sizeRequested;

    public boolean skipRenderingContents;
    public final IntArrayList textWrapPositionStack;

    /** Height of the title bar, in pixels. 0 if there is no visible title bar. */
    public int titleBarHeight;

    public int treeDepth;
    public Viewport viewport;

    public Window(@NonNull String name) {
        // TODO(ches) handle loading from ini, update values based on what we find (or don't find)
        active = false;
        appearing = false;
        baseOffsetCurrentLine = 0;
        baseOffsetPreviousLine = 0;
        borderBeingDragged = Direction.NONE;
        borderBeingHovered = Direction.NONE;
        borderSize = 0;
        childWindows = new ArrayList<>();
        collapsed = false;
        collapsedConditionAllowed = ConditionAllowed.ALL;
        collapseToggleRequested = false;
        currentItemWidth = 0.0f;
        currentTableIndex = 0;
        currentTextWrapPosition = 0.0f;
        cursorIdealMaxPosition = new Vector2i(0, 0);
        cursorMaxPosition = new Vector2i(0, 0);
        cursorPosition = new Vector2i(0, 0);
        cursorPreviousLinePosition = new Vector2i(0, 0);
        cursorStartPosition = new Vector2i(0, 0);
        drawList = new DrawList(name);
        flags = WindowFlags.NONE;
        flagsAsChildWindow = WindowFlags.NONE;
        hidden = false;
        id = 0;
        idAsPopupWindow = 0;
        idStack = new IntArrayList();
        idWithinParent = 0;
        indent = 0.0f;
        itemWidthStack = new IntArrayList();
        lineSizeCurrent = new Vector2i(0, 0);
        lineSizePrevious = new Vector2i(0, 0);
        menuBarHeight = 0;
        this.name = name;
        open = new IkBoolean();
        padding = new Vector2i(0, 0);
        parentWindow = null;
        position = new Vector2i(0, 0);
        positionConditionAllowed = ConditionAllowed.ALL;
        rectContent = new RectInt(0, 0, 0, 0);
        rectCurrentClip = new RectInt(0, 0, 0, 0);
        rectFull = new RectInt(0, 0, 0, 0);
        rectInner = new RectInt(0, 0, 0, 0);
        rectInnerClip = new RectInt(0, 0, 0, 0);
        reuseLastFrameContents = false;
        rootWindow = null;
        rootWindowForNavigation = null;
        rootWindowForTitleBarHighlight = null;
        rootWindowIncludingPopups = null;
        rounding = 0.0f;
        sameLine = false;
        scrollbarSizes = new Vector2i(0, 0);
        scrollExtent = new Vector2i(0, 0);
        scrollPosition = new Vector2i(0, 0);
        scrollTarget = new Vector2i(0, 0);
        setPos = false;
        showScrollbarX = Visibility.IF_REQUIRED;
        showScrollbarY = Visibility.IF_REQUIRED;
        sizeConditionAllowed = ConditionAllowed.ALL;
        sizeCurrent = new Vector2i(0, 0);
        sizeDesired = new Vector2i(0, 0);
        sizeFull = new Vector2i(0, 0);
        sizeRequested = new Vector2i(0, 0);
        skipRenderingContents = false;
        textWrapPositionStack = new IntArrayList();
        titleBarHeight = 0;
        treeDepth = 0;
        viewport = null;
    }

    public boolean hasCloseButton() {
        return open != null;
    }

    public void updateCursorAfterDrawing(int lastItemWidth, int lastItemHeight) {
        if (sameLine) {
            lineSizeCurrent.set(
                    lineSizeCurrent.x + lastItemWidth, Math.max(lineSizeCurrent.y, lastItemHeight));
        } else {
            lineSizeCurrent.set(lastItemWidth, lastItemHeight);
        }
        cursorPosition.add(lastItemWidth, 0);
        sameLine = false;
    }

    public void updateCursorBeforeDrawing() {
        if (!sameLine) {
            lineSizePrevious.set(lineSizeCurrent);
            lineSizeCurrent.set(0, 0);
            cursorPreviousLinePosition.set(cursorPosition);
            cursorPosition.set(cursorStartPosition.x, cursorPosition.y);
            cursorPosition.add(
                    0, lineSizePrevious.y + IkGui.getContext().style.variable.itemSpacing.y);
        }
    }
}
