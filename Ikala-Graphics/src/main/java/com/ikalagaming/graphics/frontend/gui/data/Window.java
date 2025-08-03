package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.enums.Direction;
import com.ikalagaming.graphics.frontend.gui.util.Rect;
import com.ikalagaming.util.FloatArrayList;
import com.ikalagaming.util.IntArrayList;

import org.joml.Vector2f;

import java.util.ArrayList;

public class Window {
    private Context parentContext;
    private String name;
    private int id;
    private int flags;
    private int flagsAsChildWindow;
    private ArrayList<Window> childWindows;
    private Viewport viewport;
    private Vector2f position;

    /** Size, which might be smaller than the full size if minimized. */
    private Vector2f sizeCurrent;

    /** The full size of the window when not collapsed. */
    private Vector2f sizeFull;

    /** Size the window would like to be, given its contents. */
    private Vector2f sizeDesired;

    /** Size that was requested for the window before it was drawn. */
    private Vector2f sizeRequested;

    private Vector2f padding;
    private float rounding;
    private float borderSize;
    private float titleBarHeight;
    private float menuBarHeight;

    private int idWithinParent;
    private int idAsPopupWindow;
    private Vector2f scrollPosition;
    private Vector2f scrollExtent;
    private Vector2f scrollTarget;

    /** X is the width of the vertical scrollbar, y is the height of the horizontal scrollbar. */
    private Vector2f scrollbarSizes;

    boolean showScrollbarX;
    boolean showScrollbarY;
    boolean active;
    boolean collapsed;
    boolean collapseToggleRequested;
    boolean hidden;
    boolean skipRenderingContents;
    boolean reuseLastFrameContents;
    boolean appearing;
    boolean hasCloseButton;

    private Direction borderBeingHovered;
    private Direction borderBeingDragged;

    IntArrayList IDStack;

    /** The outer region of the window. */
    private Rect rectFull;

    /** The inner part of the window, excluding the title bar, menu, scroll bars. */
    private Rect rectInner;

    /** Inner rect, but shrunk by 0.5 * padding, and clipped by the viewport or parent clip rect. */
    private Rect rectInnerClip;

    /** The region that contains the contents, including parts scrolled out of view. */
    private Rect rectContent;

    /** Current clipping rect, since we can push and pop clip rects. */
    private Rect rectCurrentClip;

    private DrawList drawList;
    private Window parentWindow;
    private Window rootWindow;
    private Window rootWindowIncludingPopups;
    private Window rootWindowForTitleBarHighlight;
    private Window rootWindowForNavigation;

    private Vector2f cursorPosition;
    private Vector2f cursorStartPosition;
    private Vector2f cursorPreviousLinePosition;

    /**
     * Grows as content is added, and used to determine the content region next frame, for scrolling
     * and auto-resize.
     */
    private Vector2f cursorMaxPosition;

    /**
     * Grows as content is added, and used to determine the content region next frame, for
     * auto-resize.
     */
    private Vector2f cursorIdealMaxPosition;

    private Vector2f lineSizePrevious;
    private Vector2f lineSizeCurrent;
    private float baseOffsetCurrentLine;
    private float baseOffsetPreviousLine;
    private boolean sameLine;
    private boolean setPos;
    private float indent;
    private int treeDepth;

    private int currentTableIndex;
    private float currentItemWidth;
    private float currentTextWrapPosition;
    private FloatArrayList itemWidthStack;
    private FloatArrayList textWrapPositionStack;
}
