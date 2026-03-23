package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.enums.Direction;
import com.ikalagaming.graphics.frontend.gui.enums.Visibility;
import com.ikalagaming.graphics.frontend.gui.flags.ConditionAllowed;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;
import com.ikalagaming.util.FloatArrayList;
import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class Window {
    public boolean active;

    /**
     * Whether the window is in the process of appearing after being hidden or inactive, or the
     * first frame it's displayed.
     */
    public boolean appearing;

    public float baseOffsetCurrentLine;
    public float baseOffsetPreviousLine;
    public Direction borderBeingDragged;
    public Direction borderBeingHovered;

    /** Thickness of the border, in pixels. 0 if there is no border. */
    public float borderSize;

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
    public final Vector2f cursorIdealMaxPosition;

    /**
     * Grows as content is added, and used to determine the content region next frame, for scrolling
     * and auto-resize.
     */
    public final Vector2f cursorMaxPosition;

    public final Vector2f cursorPosition;
    public final Vector2f cursorPreviousLinePosition;

    public final Vector2f cursorStartPosition;
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
    public final Vector2f lineSizeCurrent;
    public final Vector2f lineSizePrevious;

    /** The height of the menu bar in pixels. 0 if there is no visible menu bar. */
    public float menuBarHeight;

    public final String name;

    /**
     * Only non-null if this window has a close button, used to communicate elsewhere that we closed
     * the window.
     */
    public IkBoolean open;

    public final Vector2f padding;

    public Window parentWindow;

    public final Vector2f position;

    /**
     * Flags for the position conditions.
     *
     * @see ConditionAllowed
     */
    public int positionConditionAllowed;

    /** The region that contains the contents, including parts scrolled out of view. */
    public final RectFloat rectContent;

    /** Current clipping rect, since we can push and pop clip rects. */
    public final RectFloat rectCurrentClip;

    /** The outer region of the window. */
    public final RectFloat rectFull;

    /** The inner part of the window, excluding the title bar, menu, scroll bars. */
    public final RectFloat rectInner;

    /** Inner rect, but shrunk by 0.5 * padding, and clipped by the viewport or parent clip rect. */
    public final RectFloat rectInnerClip;

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
    public final Vector2f scrollbarSizes;

    public final Vector2f scrollExtent;

    public final Vector2f scrollPosition;

    public final Vector2f scrollTarget;
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
    public final Vector2f sizeCurrent;

    /** Size the window would like to be, given its contents. */
    public final Vector2f sizeDesired;

    /** The full size of the window when not collapsed. */
    public final Vector2f sizeFull;

    /** Size that was requested for the window before it was drawn. */
    public final Vector2f sizeRequested;

    public boolean skipRenderingContents;
    public final FloatArrayList textWrapPositionStack;

    /** Height of the title bar, in pixels. 0 if there is no visible title bar. */
    public float titleBarHeight;

    public int treeDepth;
    public Viewport viewport;

    public Window(@NonNull String name) {
        // TODO(ches) handle loading from ini, update values based on what we find (or don't find)
        active = false;
        appearing = false;
        baseOffsetCurrentLine = 0.0f;
        baseOffsetPreviousLine = 0.0f;
        borderBeingDragged = Direction.NONE;
        borderBeingHovered = Direction.NONE;
        borderSize = 0.0f;
        childWindows = new ArrayList<>();
        collapsed = false;
        collapsedConditionAllowed = ConditionAllowed.ALL;
        collapseToggleRequested = false;
        currentItemWidth = 0.0f;
        currentTableIndex = 0;
        currentTextWrapPosition = 0.0f;
        cursorIdealMaxPosition = new Vector2f(0.0f, 0.0f);
        cursorMaxPosition = new Vector2f(0.0f, 0.0f);
        cursorPosition = new Vector2f(0.0f, 0.0f);
        cursorPreviousLinePosition = new Vector2f(0.0f, 0.0f);
        cursorStartPosition = new Vector2f(0.0f, 0.0f);
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
        lineSizeCurrent = new Vector2f(0.0f, 0.0f);
        lineSizePrevious = new Vector2f(0.0f, 0.0f);
        menuBarHeight = 0.0f;
        this.name = name;
        open = new IkBoolean();
        padding = new Vector2f(0.0f, 0.0f);
        parentWindow = null;
        position = new Vector2f(0.0f, 0.0f);
        positionConditionAllowed = ConditionAllowed.ALL;
        rectContent = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        rectCurrentClip = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        rectFull = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        rectInner = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        rectInnerClip = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        reuseLastFrameContents = false;
        rootWindow = null;
        rootWindowForNavigation = null;
        rootWindowForTitleBarHighlight = null;
        rootWindowIncludingPopups = null;
        rounding = 0.0f;
        sameLine = false;
        scrollbarSizes = new Vector2f(0.0f, 0.0f);
        scrollExtent = new Vector2f(0.0f, 0.0f);
        scrollPosition = new Vector2f(0.0f, 0.0f);
        scrollTarget = new Vector2f(0.0f, 0.0f);
        setPos = false;
        showScrollbarX = Visibility.IF_REQUIRED;
        showScrollbarY = Visibility.IF_REQUIRED;
        sizeConditionAllowed = ConditionAllowed.ALL;
        sizeCurrent = new Vector2f(0.0f, 0.0f);
        sizeDesired = new Vector2f(0.0f, 0.0f);
        sizeFull = new Vector2f(0.0f, 0.0f);
        sizeRequested = new Vector2f(0.0f, 0.0f);
        skipRenderingContents = false;
        textWrapPositionStack = new FloatArrayList();
        titleBarHeight = 0.0f;
        treeDepth = 0;
        viewport = null;
    }

    public boolean hasCloseButton() {
        return open != null;
    }

    public void updateCursorAfterDrawing(float lastItemWidth, float lastItemHeight) {
        if (sameLine) {
            lineSizeCurrent.set(
                    lineSizeCurrent.x + lastItemWidth, Math.max(lineSizeCurrent.y, lastItemHeight));
        } else {
            lineSizeCurrent.set(lastItemWidth, lastItemHeight);
        }
        cursorPosition.add(lastItemWidth, 0.0f);
        sameLine = false;
    }

    public void updateCursorBeforeDrawing() {
        if (!sameLine) {
            lineSizePrevious.set(lineSizeCurrent);
            lineSizeCurrent.set(0.0f, 0.0f);
            cursorPreviousLinePosition.set(cursorPosition);
            cursorPosition.set(cursorStartPosition.x, cursorPosition.y);
            cursorPosition.add(
                    0.0f, lineSizePrevious.y + IkGui.getContext().style.variable.itemSpacing.y);
        }
    }
}
