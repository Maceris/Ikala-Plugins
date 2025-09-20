package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.enums.Direction;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
import com.ikalagaming.graphics.frontend.gui.util.Rect;
import com.ikalagaming.util.FloatArrayList;
import com.ikalagaming.util.IntArrayList;

import org.joml.Vector2f;

import java.util.ArrayList;

public class Window {
    public Context parentContext = null;
    public String name = null;
    public int id = 0;
    public int flags = WindowFlags.NONE;
    public int flagsAsChildWindow = WindowFlags.NONE;
    public ArrayList<Window> childWindows = new ArrayList<>();
    public Viewport viewport = null;
    public Vector2f position = new Vector2f(0, 0);

    /**
     * Only non-null if this window has a close button, used to communicate elsewhere that we closed
     * the window.
     */
    public IkBoolean open = null;

    /** Size, which might be smaller than the full size if minimized. */
    public Vector2f sizeCurrent = new Vector2f(0, 0);

    /** The full size of the window when not collapsed. */
    public Vector2f sizeFull = new Vector2f(0, 0);

    /** Size the window would like to be, given its contents. */
    public Vector2f sizeDesired = new Vector2f(0, 0);

    /** Size that was requested for the window before it was drawn. */
    public Vector2f sizeRequested = new Vector2f(0, 0);

    public Vector2f padding = new Vector2f(0, 0);
    public float rounding = 0.0f;
    public float borderSize = 0.0f;
    public float titleBarHeight = 0.0f;
    public float menuBarHeight = 0.0f;

    public int idWithinParent = 0;
    public int idAsPopupWindow = 0;
    public Vector2f scrollPosition = new Vector2f(0, 0);
    public Vector2f scrollExtent = new Vector2f(0, 0);
    public Vector2f scrollTarget = new Vector2f(0, 0);

    /** X is the width of the vertical scrollbar, y is the height of the horizontal scrollbar. */
    public Vector2f scrollbarSizes = new Vector2f(0, 0);

    public boolean showScrollbarX = false;
    public boolean showScrollbarY = false;
    public boolean active = false;
    public boolean collapsed = false;
    public boolean collapseToggleRequested = false;
    public boolean hidden = false;
    public boolean skipRenderingContents = false;
    public boolean reuseLastFrameContents = false;
    public boolean appearing = false;

    public Direction borderBeingHovered = Direction.NONE;
    public Direction borderBeingDragged = Direction.NONE;

    public IntArrayList IDStack = new IntArrayList();

    /** The outer region of the window. */
    public Rect rectFull = new Rect(0, 0, 0, 0);

    /** The inner part of the window, excluding the title bar, menu, scroll bars. */
    public Rect rectInner = new Rect(0, 0, 0, 0);

    /** Inner rect, but shrunk by 0.5 * padding, and clipped by the viewport or parent clip rect. */
    public Rect rectInnerClip = new Rect(0, 0, 0, 0);

    /** The region that contains the contents, including parts scrolled out of view. */
    public Rect rectContent = new Rect(0, 0, 0, 0);

    /** Current clipping rect, since we can push and pop clip rects. */
    public Rect rectCurrentClip = new Rect(0, 0, 0, 0);

    public DrawList drawList = new DrawList(); // TODO(ches) do we need to add this somewhere else?
    public Window parentWindow = null;
    public Window rootWindow = null;
    public Window rootWindowIncludingPopups = null;
    public Window rootWindowForTitleBarHighlight = null;
    public Window rootWindowForNavigation = null;

    public Vector2f cursorPosition = new Vector2f(0, 0);
    public Vector2f cursorStartPosition = new Vector2f(0, 0);
    public Vector2f cursorPreviousLinePosition = new Vector2f(0, 0);

    /**
     * Grows as content is added, and used to determine the content region next frame, for scrolling
     * and auto-resize.
     */
    public Vector2f cursorMaxPosition = new Vector2f(0, 0);

    /**
     * Grows as content is added, and used to determine the content region next frame, for
     * auto-resize.
     */
    public Vector2f cursorIdealMaxPosition = new Vector2f(0, 0);

    public Vector2f lineSizePrevious = new Vector2f(0, 0);
    public Vector2f lineSizeCurrent = new Vector2f(0, 0);
    public float baseOffsetCurrentLine = 0.0f;
    public float baseOffsetPreviousLine = 0.0f;
    public boolean sameLine = false;
    public boolean setPos = false;
    public float indent = 0.0f;
    public int treeDepth = 0;

    public int currentTableIndex = 0;
    public float currentItemWidth = 0.0f;
    public float currentTextWrapPosition = 0.0f;
    public FloatArrayList itemWidthStack = new FloatArrayList();
    public FloatArrayList textWrapPositionStack = new FloatArrayList();

    /** Reset all values to defaults, so that a window can be reused internally. */
    public void reset() {
        this.parentContext = null;
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

    public boolean hasCloseButton() {
        return open != null;
    }
}
