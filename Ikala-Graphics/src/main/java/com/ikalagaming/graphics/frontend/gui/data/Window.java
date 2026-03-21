package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.enums.Direction;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
import com.ikalagaming.graphics.frontend.gui.util.Rect;
import com.ikalagaming.util.FloatArrayList;
import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;
import org.joml.Vector2i;

import java.util.ArrayList;
import java.util.List;

public class Window {
    public final String name;
    public int id;
    public int flags;
    public int flagsAsChildWindow;
    public final List<Window> childWindows;
    public Viewport viewport;
    public final Vector2i position;

    /**
     * Only non-null if this window has a close button, used to communicate elsewhere that we closed
     * the window.
     */
    public IkBoolean open;

    /** Size, which might be smaller than the full size if minimized. */
    public final Vector2i sizeCurrent;

    /** The full size of the window when not collapsed. */
    public final Vector2i sizeFull;

    /** Size the window would like to be, given its contents. */
    public final Vector2i sizeDesired;

    /** Size that was requested for the window before it was drawn. */
    public final Vector2i sizeRequested;

    public final Vector2i padding;
    public float rounding;
    public int borderSize;
    public int titleBarHeight;
    public int menuBarHeight;

    public int idWithinParent;
    public int idAsPopupWindow;
    public final Vector2i scrollPosition;
    public final Vector2i scrollExtent;
    public final Vector2i scrollTarget;

    /** X is the width of the vertical scrollbar, y is the height of the horizontal scrollbar. */
    public final Vector2i scrollbarSizes;

    public boolean showScrollbarX;
    public boolean showScrollbarY;
    public boolean active;
    public boolean collapsed;
    public boolean collapseToggleRequested;
    public boolean hidden;
    public boolean skipRenderingContents;
    public boolean reuseLastFrameContents;

    /**
     * Whether the window is in the process of appearing after being hidden or inactive, or the
     * first frame it's displayed.
     */
    public boolean appearing;

    public Direction borderBeingHovered;
    public Direction borderBeingDragged;

    public IntArrayList idStack;

    /** The outer region of the window. */
    public final Rect rectFull;

    /** The inner part of the window, excluding the title bar, menu, scroll bars. */
    public final Rect rectInner;

    /** Inner rect, but shrunk by 0.5 * padding, and clipped by the viewport or parent clip rect. */
    public final Rect rectInnerClip;

    /** The region that contains the contents, including parts scrolled out of view. */
    public final Rect rectContent;

    /** Current clipping rect, since we can push and pop clip rects. */
    public final Rect rectCurrentClip;

    public DrawList drawList;
    public Window parentWindow;
    public Window rootWindow;
    public Window rootWindowIncludingPopups;
    public Window rootWindowForTitleBarHighlight;
    public Window rootWindowForNavigation;

    public final Vector2i cursorPosition;
    public final Vector2i cursorStartPosition;
    public final Vector2i cursorPreviousLinePosition;

    /**
     * Grows as content is added, and used to determine the content region next frame, for scrolling
     * and auto-resize.
     */
    public final Vector2i cursorMaxPosition;

    /**
     * Grows as content is added, and used to determine the content region next frame, for
     * auto-resize.
     */
    public final Vector2i cursorIdealMaxPosition;

    public final Vector2i lineSizePrevious;
    public final Vector2i lineSizeCurrent;
    public int baseOffsetCurrentLine;
    public int baseOffsetPreviousLine;
    public boolean sameLine;
    public boolean setPos;
    public float indent;
    public int treeDepth;

    public int currentTableIndex;
    public float currentItemWidth;
    public float currentTextWrapPosition;
    public final FloatArrayList itemWidthStack;
    public final FloatArrayList textWrapPositionStack;

    public Window(@NonNull String name) {
        this.name = name;
        id = 0;
        flags = WindowFlags.NONE;
        flagsAsChildWindow = WindowFlags.NONE;
        childWindows = new ArrayList<>();
        viewport = null;
        position = new Vector2i(0, 0);
        open = new IkBoolean();
        sizeCurrent = new Vector2i(0, 0);
        sizeFull = new Vector2i(0, 0);
        sizeDesired = new Vector2i(0, 0);
        sizeRequested = new Vector2i(0, 0);
        padding = new Vector2i(0, 0);
        rounding = 0.0f;
        borderSize = 0;
        titleBarHeight = 0;
        menuBarHeight = 0;
        idWithinParent = 0;
        idAsPopupWindow = 0;
        scrollPosition = new Vector2i(0, 0);
        scrollExtent = new Vector2i(0, 0);
        scrollTarget = new Vector2i(0, 0);
        scrollbarSizes = new Vector2i(0, 0);
        showScrollbarX = false;
        showScrollbarY = false;
        active = false;
        collapsed = false;
        collapseToggleRequested = false;
        hidden = false;
        skipRenderingContents = false;
        reuseLastFrameContents = false;
        appearing = false;
        borderBeingHovered = Direction.NONE;
        borderBeingDragged = Direction.NONE;
        idStack = new IntArrayList();
        rectFull = new Rect(0, 0, 0, 0);
        rectInner = new Rect(0, 0, 0, 0);
        rectInnerClip = new Rect(0, 0, 0, 0);
        rectContent = new Rect(0, 0, 0, 0);
        rectCurrentClip = new Rect(0, 0, 0, 0);
        drawList = new DrawList(name);
        parentWindow = null;
        rootWindow = null;
        rootWindowIncludingPopups = null;
        rootWindowForTitleBarHighlight = null;
        rootWindowForNavigation = null;
        cursorPosition = new Vector2i(0, 0);
        cursorStartPosition = new Vector2i(0, 0);
        cursorPreviousLinePosition = new Vector2i(0, 0);
        cursorMaxPosition = new Vector2i(0, 0);
        cursorIdealMaxPosition = new Vector2i(0, 0);
        lineSizePrevious = new Vector2i(0, 0);
        lineSizeCurrent = new Vector2i(0, 0);
        baseOffsetCurrentLine = 0;
        baseOffsetPreviousLine = 0;
        sameLine = false;
        setPos = false;
        indent = 0.0f;
        treeDepth = 0;
        currentTableIndex = 0;
        currentItemWidth = 0.0f;
        currentTextWrapPosition = 0.0f;
        itemWidthStack = new FloatArrayList();
        textWrapPositionStack = new FloatArrayList();
    }

    public boolean hasCloseButton() {
        return open != null;
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
}
