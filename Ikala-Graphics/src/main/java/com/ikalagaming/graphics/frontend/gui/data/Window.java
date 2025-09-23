package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.enums.Direction;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
import com.ikalagaming.graphics.frontend.gui.util.Rect;
import com.ikalagaming.util.FloatArrayList;
import com.ikalagaming.util.IntArrayList;

import org.joml.Vector2f;

import java.util.ArrayList;

public class Window {
    public Context parentContext;
    public String name;
    public int id;
    public int flags;
    public int flagsAsChildWindow;
    public ArrayList<Window> childWindows;
    public Viewport viewport;
    public Vector2f position;

    /**
     * Only non-null if this window has a close button, used to communicate elsewhere that we closed
     * the window.
     */
    public IkBoolean open;

    /** Size, which might be smaller than the full size if minimized. */
    public Vector2f sizeCurrent;

    /** The full size of the window when not collapsed. */
    public Vector2f sizeFull;

    /** Size the window would like to be, given its contents. */
    public Vector2f sizeDesired;

    /** Size that was requested for the window before it was drawn. */
    public Vector2f sizeRequested;

    public Vector2f padding;
    public float rounding;
    public float borderSize;
    public float titleBarHeight;
    public float menuBarHeight;

    public int idWithinParent;
    public int idAsPopupWindow;
    public Vector2f scrollPosition;
    public Vector2f scrollExtent;
    public Vector2f scrollTarget;

    /** X is the width of the vertical scrollbar, y is the height of the horizontal scrollbar. */
    public Vector2f scrollbarSizes;

    public boolean showScrollbarX;
    public boolean showScrollbarY;
    public boolean active;
    public boolean collapsed;
    public boolean collapseToggleRequested;
    public boolean hidden;
    public boolean skipRenderingContents;
    public boolean reuseLastFrameContents;
    public boolean appearing;

    public Direction borderBeingHovered;
    public Direction borderBeingDragged;

    public IntArrayList IDStack;

    /** The outer region of the window. */
    public Rect rectFull;

    /** The inner part of the window, excluding the title bar, menu, scroll bars. */
    public Rect rectInner;

    /** Inner rect, but shrunk by 0.5 * padding, and clipped by the viewport or parent clip rect. */
    public Rect rectInnerClip;

    /** The region that contains the contents, including parts scrolled out of view. */
    public Rect rectContent;

    /** Current clipping rect, since we can push and pop clip rects. */
    public Rect rectCurrentClip;

    public DrawList drawList;
    public Window parentWindow;
    public Window rootWindow;
    public Window rootWindowIncludingPopups;
    public Window rootWindowForTitleBarHighlight;
    public Window rootWindowForNavigation;

    public Vector2f cursorPosition;
    public Vector2f cursorStartPosition;
    public Vector2f cursorPreviousLinePosition;

    /**
     * Grows as content is added, and used to determine the content region next frame, for scrolling
     * and auto-resize.
     */
    public Vector2f cursorMaxPosition;

    /**
     * Grows as content is added, and used to determine the content region next frame, for
     * auto-resize.
     */
    public Vector2f cursorIdealMaxPosition;

    public Vector2f lineSizePrevious;
    public Vector2f lineSizeCurrent;
    public float baseOffsetCurrentLine;
    public float baseOffsetPreviousLine;
    public boolean sameLine;
    public boolean setPos;
    public float indent;
    public int treeDepth;

    public int currentTableIndex;
    public float currentItemWidth;
    public float currentTextWrapPosition;
    public FloatArrayList itemWidthStack;
    public FloatArrayList textWrapPositionStack;

    public Window(Context context) {
        parentContext = context;
        childWindows = new ArrayList<>();
        position = new Vector2f(0, 0);
        sizeCurrent = new Vector2f(0, 0);
        sizeFull = new Vector2f(0, 0);
        sizeDesired = new Vector2f(0, 0);
        sizeRequested = new Vector2f(0, 0);
        padding = new Vector2f(0, 0);
        scrollPosition = new Vector2f(0, 0);
        scrollExtent = new Vector2f(0, 0);
        scrollTarget = new Vector2f(0, 0);
        scrollbarSizes = new Vector2f(0, 0);
        IDStack = new IntArrayList();
        rectFull = new Rect(0, 0, 0, 0);
        rectInner = new Rect(0, 0, 0, 0);
        rectInnerClip = new Rect(0, 0, 0, 0);
        rectContent = new Rect(0, 0, 0, 0);
        rectCurrentClip = new Rect(0, 0, 0, 0);
        drawList = new DrawList();
        context.drawData.drawLists.add(drawList);
        cursorPosition = new Vector2f(0, 0);
        cursorStartPosition = new Vector2f(0, 0);
        cursorPreviousLinePosition = new Vector2f(0, 0);
        cursorMaxPosition = new Vector2f(0, 0);
        cursorIdealMaxPosition = new Vector2f(0, 0);
        lineSizePrevious = new Vector2f(0, 0);
        lineSizeCurrent = new Vector2f(0, 0);
        itemWidthStack = new FloatArrayList();
        textWrapPositionStack = new FloatArrayList();

        reset();
    }

    /** Reset all values to defaults, so that a window can be reused internally. */
    public void reset() {
        this.name = null;
        this.id = 0;
        this.flags = WindowFlags.NONE;
        this.flagsAsChildWindow = WindowFlags.NONE;
        this.childWindows.clear();
        this.viewport = null;
        this.position.set(0, 0);
        this.open = null;
        this.sizeCurrent.set(0, 0);
        this.sizeFull.set(0, 0);
        this.sizeDesired.set(0, 0);
        this.sizeRequested.set(0, 0);
        this.padding.set(0, 0);
        this.rounding = 0.0f;
        this.borderSize = 0.0f;
        this.titleBarHeight = 0.0f;
        this.menuBarHeight = 0.0f;
        this.idWithinParent = 0;
        this.idAsPopupWindow = 0;
        this.scrollPosition.set(0, 0);
        this.scrollExtent.set(0, 0);
        this.scrollTarget.set(0, 0);
        this.scrollbarSizes.set(0, 0);
        this.showScrollbarX = false;
        this.showScrollbarY = false;
        this.active = false;
        this.collapsed = false;
        this.collapseToggleRequested = false;
        this.hidden = false;
        this.skipRenderingContents = false;
        this.reuseLastFrameContents = false;
        this.appearing = false;
        this.borderBeingHovered = Direction.NONE;
        this.borderBeingDragged = Direction.NONE;
        this.IDStack = new IntArrayList();
        this.rectFull.set(0, 0, 0, 0);
        this.rectInner.set(0, 0, 0, 0);
        this.rectInnerClip.set(0, 0, 0, 0);
        this.rectContent.set(0, 0, 0, 0);
        this.rectCurrentClip.set(0, 0, 0, 0);
        this.drawList.clear();
        this.parentWindow = null;
        this.rootWindow = null;
        this.rootWindowIncludingPopups = null;
        this.rootWindowForTitleBarHighlight = null;
        this.rootWindowForNavigation = null;
        this.cursorPosition.set(0, 0);
        this.cursorStartPosition.set(0, 0);
        this.cursorPreviousLinePosition.set(0, 0);
        this.cursorMaxPosition.set(0, 0);
        this.cursorIdealMaxPosition.set(0, 0);
        this.lineSizePrevious.set(0, 0);
        this.lineSizeCurrent.set(0, 0);
        this.baseOffsetCurrentLine = 0.0f;
        this.baseOffsetPreviousLine = 0.0f;
        this.sameLine = false;
        this.setPos = false;
        this.indent = 0.0f;
        this.treeDepth = 0;
        this.currentTableIndex = 0;
        this.currentItemWidth = 0.0f;
        this.currentTextWrapPosition = 0.0f;
        this.itemWidthStack.clear();
        this.textWrapPositionStack.clear();
    }

    public void destroy() {
        parentContext.drawData.drawLists.remove(drawList);
    }

    public boolean hasCloseButton() {
        return open != null;
    }
}
