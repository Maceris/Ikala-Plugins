package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.enums.Direction;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
import com.ikalagaming.graphics.frontend.gui.util.Rect;
import com.ikalagaming.util.FloatArrayList;
import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;
import org.joml.Vector2f;
import org.joml.Vector2i;

import java.util.ArrayList;
import java.util.List;

public class Window {
    public Context context;
    public String name;
    public int id;
    public int flags;
    public int flagsAsChildWindow;
    public final List<Window> childWindows;
    public Viewport viewport;
    public final Vector2f position;

    /**
     * Only non-null if this window has a close button, used to communicate elsewhere that we closed
     * the window.
     */
    public IkBoolean open;

    /** Size, which might be smaller than the full size if minimized. */
    public final Vector2f sizeCurrent;

    /** The full size of the window when not collapsed. */
    public final Vector2f sizeFull;

    /** Size the window would like to be, given its contents. */
    public final Vector2f sizeDesired;

    /** Size that was requested for the window before it was drawn. */
    public final Vector2f sizeRequested;

    public final Vector2i padding;
    public float rounding;
    public float borderSize;
    public int titleBarHeight;
    public int menuBarHeight;

    public int idWithinParent;
    public int idAsPopupWindow;
    public final Vector2f scrollPosition;
    public final Vector2f scrollExtent;
    public final Vector2f scrollTarget;

    /** X is the width of the vertical scrollbar, y is the height of the horizontal scrollbar. */
    public final Vector2f scrollbarSizes;

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

    public final Vector2f cursorPosition;
    public final Vector2f cursorStartPosition;
    public final Vector2f cursorPreviousLinePosition;

    /**
     * Grows as content is added, and used to determine the content region next frame, for scrolling
     * and auto-resize.
     */
    public final Vector2f cursorMaxPosition;

    /**
     * Grows as content is added, and used to determine the content region next frame, for
     * auto-resize.
     */
    public final Vector2f cursorIdealMaxPosition;

    public final Vector2f lineSizePrevious;
    public final Vector2f lineSizeCurrent;
    public float baseOffsetCurrentLine;
    public float baseOffsetPreviousLine;
    public boolean sameLine;
    public boolean setPos;
    public float indent;
    public int treeDepth;

    public int currentTableIndex;
    public float currentItemWidth;
    public float currentTextWrapPosition;
    public final FloatArrayList itemWidthStack;
    public final FloatArrayList textWrapPositionStack;

    public Window(@NonNull Context context, @NonNull String name) {
        this.context = context;
        this.name = name;
        id = 0;
        flags = WindowFlags.NONE;
        flagsAsChildWindow = WindowFlags.NONE;
        childWindows = new ArrayList<>();
        viewport = null;
        position = new Vector2f(0, 0);
        open = new IkBoolean();
        sizeCurrent = new Vector2f(0, 0);
        sizeFull = new Vector2f(0, 0);
        sizeDesired = new Vector2f(0, 0);
        sizeRequested = new Vector2f(0, 0);
        padding = new Vector2i(0, 0);
        rounding = 0.0f;
        borderSize = 0.0f;
        titleBarHeight = 0;
        menuBarHeight = 0;
        idWithinParent = 0;
        idAsPopupWindow = 0;
        scrollPosition = new Vector2f(0, 0);
        scrollExtent = new Vector2f(0, 0);
        scrollTarget = new Vector2f(0, 0);
        scrollbarSizes = new Vector2f(0, 0);
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
        cursorPosition = new Vector2f(0, 0);
        cursorStartPosition = new Vector2f(0, 0);
        cursorPreviousLinePosition = new Vector2f(0, 0);
        cursorMaxPosition = new Vector2f(0, 0);
        cursorIdealMaxPosition = new Vector2f(0, 0);
        lineSizePrevious = new Vector2f(0, 0);
        lineSizeCurrent = new Vector2f(0, 0);
        baseOffsetCurrentLine = 0.0f;
        baseOffsetPreviousLine = 0.0f;
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

    /** Reset all values to defaults, so that a window can be reused internally. */
    public void reset(@NonNull Context context, @NonNull String name) {
        this.context = context;
        this.name = name;
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
        this.titleBarHeight = 0;
        this.menuBarHeight = 0;
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
        this.idStack = new IntArrayList();
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

    public boolean hasCloseButton() {
        return open != null;
    }
}
