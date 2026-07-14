package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.IkGuiInternal;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.Direction;
import com.ikalagaming.graphics.frontend.gui.enums.Visibility;
import com.ikalagaming.graphics.frontend.gui.flags.ConditionAllowed;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;
import com.ikalagaming.util.FloatArrayList;
import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Window {
    public boolean active;

    /**
     * The radius for blending around the edges to/from transparent. 0 means no blending, positive
     * numbers indicate the radius fading from an opaque (well, "regular" colored) center to fully
     * transparent edge, negative numbers indicate an opaque (unmodified) edge fading to a
     * transparent center. Window flags are used to specify which corners are involved.
     */
    public float alphaRadius;

    /**
     * Whether the window is in the process of appearing after being hidden or inactive, or the
     * first frame it's displayed.
     */
    public boolean appearing;

    public IkByte autoFitFramesX;
    public IkByte autoFitFramesY;

    public boolean autoFitOnlyGrows;

    public float baseOffsetCurrentLine;
    public float baseOffsetPreviousLine;

    /**
     * Number of begin calls during the current frame. Normally 0 or 1, but could be more if
     * appending using multiple begin/end pairs.
     */
    public short beginCount;

    /** The number of begin calls from the previous frame. */
    public short beginCountPreviousFrame;

    public short beginOrderWithinContext;
    public short beginOrderWithinParent;

    public Direction borderBeingDragged;
    public Direction borderBeingHovered;

    /** Thickness of the border, in pixels. 0 if there is no border. */
    public float borderSize;

    public final List<Window> childWindows;

    /**
     * The locations in the canvas of items that care about mouse input. These are in the order they
     * are added, but since the cursor can be moved around this means they're probably not in any
     * kind of spatial ordering. This does not account for scrolling within the window and clipping
     * to the window size, that's calculated separately.
     */
    public final List<ClickableItem>
            clickableItems; // TODO(ches) I think we can delete this and detect clicks immediately

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

    /** Disable window interactions for N frames. */
    public IkByte disableInputsFrames;

    /**
     * Flags for the dock conditions.
     *
     * @see ConditionAllowed
     */
    public int dockConditionAllowed;

    /**
     * Backup of the last valid dockNode ID, so single windows remember their dock node ID even when
     * not bound anymore.
     */
    public int dockID;

    /** If docking artifacts are actually visible. When set, dockNode will be non-null. */
    public boolean dockIsActive;

    /**
     * Which dock node the window is docked into. Prefer checking {@link #dockIsActive}, as this can
     * be set even if the dock node is hidden.
     */
    public DockNode dockNode;

    /** The node that we own, for parent windows. */
    public DockNode dockNodeAsHost;

    public boolean dockNodeIsVisible;

    /**
     * Order of the last time the window was visible within it's dock node. Used to reorder windows
     * that are reappearing in the same frame. It's possible to have the same value between windows
     * that were active and windows that were none.
     */
    public short dockOrder;

    public WindowDockStyle dockStyle;

    /** If the window is visible this frame, the corresponding tab is selected. */
    public boolean dockTabIsVisible;

    public boolean dockTabWantClose;

    public DrawList drawList;
    public int flags;
    public int flagsPreviousFrame;
    public int flagsAsChildWindow;
    public boolean hidden;

    /** Hide the window for n frames. */
    public IkByte hiddenFramesCanSkipItems;

    /**
     * Hide the window for N frames while allowing items to be submitted, so we can measure their
     * size
     */
    public IkByte hiddenFramesCannotSkipItems;

    /** Hide the window until frame N at render() time only */
    public IkByte hiddenFramesForRenderOnly;

    /**
     * Location of a rectangular hole in the window that ignores hit tests. Zero values if not
     * needed.
     */
    public final Vector2f hitTestHolePosition;

    /**
     * Size of a rectangular hole in the window that ignores hit tests. Zero values if not needed.
     */
    public final Vector2f hitTestHoleSize;

    public int id;

    public int idAsPopupWindow;

    /** ID of the "#MOVE" element. */
    public int idMove;

    public IntArrayList idStack;

    /** ID of the "#TAB" element. */
    public int idTab;

    public int idWithinParent;
    public float indent;
    public boolean isExplicitChild;
    public boolean isFallbackWindow;
    public final IntArrayList itemWidthStack;
    public int lastFrameActive;
    public long lastTimeActive;
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

    public Window parentWindowInBeginStack;
    public Window parentWindowForFocusRoute;

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

    /** The inner part of the window, excluding the title bar, menu, scroll bars. */
    public final RectFloat rectInner;

    /** Inner rect, but shrunk by 0.5 * padding, and clipped by the viewport or parent clip rect. */
    public final RectFloat rectInnerClip;

    /** The outer region of the window. */
    public final RectFloat rectOuter;

    /** {@link #rectOuter} just after setup in begin. {@link #rectOuter} for root window. */
    public final RectFloat rectOuterClipped;

    public boolean reuseLastFrameContents;
    public Window rootWindow;
    public Window rootWindowForNavigation;
    public Window rootWindowForTitleBarHighlight;
    public Window rootWindowPopupTree;
    public Window rootWindowDockTree;

    /**
     * Corner rounding radius for the window. Window flags are used to specify which corners are
     * rounded.
     */
    public float rounding;

    public boolean sameLine;

    /** Stores the window pivot. (0,0) is the top left, (1,1) is the bottom right. */
    public final Vector2f setWindowPosPivot;

    /**
     * Stores the window position when using a non-zero pivot, as we have to defer setting the
     * window position until we know the size.
     */
    public final Vector2f setWindowPosValue;

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

    /** Size,(==sizeFull or collapsed title bar size). */
    public final Vector2f size;

    /** The full size of the window when not collapsed. */
    public final Vector2f sizeFull;

    public boolean skipRenderingContents;
    public final FloatArrayList textWrapPositionStack;

    /** Height of the title bar, in pixels. 0 if there is no visible title bar. */
    public float titleBarHeight;

    public int treeDepth;
    public Viewport viewport;
    public int viewportID;
    public boolean viewportOwned;
    public boolean wasActive;

    public WindowClass windowClass;

    /** Set to true when any widget accesses the window. */
    public boolean writeAccessed;

    public Window(@NonNull String name) {
        // TODO(ches) handle loading from ini, update values based on what we find (or don't find)
        active = false;
        alphaRadius = 0;
        appearing = false;
        autoFitFramesX = new IkByte();
        autoFitFramesY = new IkByte();
        autoFitOnlyGrows = false;
        baseOffsetCurrentLine = 0.0f;
        baseOffsetPreviousLine = 0.0f;
        beginCount = 0;
        beginCountPreviousFrame = 0;
        beginOrderWithinContext = 0;
        beginOrderWithinParent = 0;
        borderBeingDragged = Direction.NONE;
        borderBeingHovered = Direction.NONE;
        borderSize = 0.0f;
        childWindows = new ArrayList<>();
        clickableItems = new ArrayList<>();
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
        disableInputsFrames = new IkByte();
        dockID = 0;
        dockConditionAllowed = ConditionAllowed.ALL;
        dockIsActive = false;
        dockNode = null;
        dockNodeAsHost = null;
        dockNodeIsVisible = false;
        dockOrder = 0;
        dockStyle = new WindowDockStyle();
        dockTabIsVisible = false;
        dockTabWantClose = false;
        drawList = new DrawList(name);
        flags = WindowFlags.NONE;
        flagsPreviousFrame = WindowFlags.NONE;
        flagsAsChildWindow = WindowFlags.NONE;
        hidden = false;
        hiddenFramesCanSkipItems = new IkByte();
        hiddenFramesCannotSkipItems = new IkByte();
        hiddenFramesForRenderOnly = new IkByte();
        hitTestHolePosition = new Vector2f(0.0f, 0.0f);
        hitTestHoleSize = new Vector2f(0.0f, 0.0f);
        id = 0;
        idAsPopupWindow = 0;
        idStack = new IntArrayList();
        idTab = 0;
        idWithinParent = 0;
        indent = 0.0f;
        isExplicitChild = false;
        isFallbackWindow = false;
        itemWidthStack = new IntArrayList();
        lastFrameActive = -1;
        lastTimeActive = 0;
        lineSizeCurrent = new Vector2f(0.0f, 0.0f);
        lineSizePrevious = new Vector2f(0.0f, 0.0f);
        menuBarHeight = 0.0f;
        this.name = name;
        open = new IkBoolean();
        padding = new Vector2f(0.0f, 0.0f);
        parentWindow = null;
        parentWindowInBeginStack = null;
        parentWindowForFocusRoute = null;
        position = new Vector2f(0.0f, 0.0f);
        positionConditionAllowed = ConditionAllowed.ALL;
        rectContent = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        rectCurrentClip = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        rectInner = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        rectInnerClip = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        rectOuter = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        rectOuterClipped = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        reuseLastFrameContents = false;
        rootWindow = this;
        rootWindowForNavigation = this;
        rootWindowForTitleBarHighlight = this;
        rootWindowPopupTree = this;
        rootWindowDockTree = this;
        rounding = 0.0f;
        sameLine = false;
        setWindowPosPivot = new Vector2f(0.0f, 0.0f);
        setWindowPosValue = new Vector2f(0.0f, 0.0f);
        scrollbarSizes = new Vector2f(0.0f, 0.0f);
        scrollExtent = new Vector2f(0.0f, 0.0f);
        scrollPosition = new Vector2f(0.0f, 0.0f);
        scrollTarget = new Vector2f(0.0f, 0.0f);
        setPos = false;
        showScrollbarX = Visibility.IF_REQUIRED;
        showScrollbarY = Visibility.IF_REQUIRED;
        sizeConditionAllowed = ConditionAllowed.ALL;
        size = new Vector2f(0.0f, 0.0f);
        sizeFull = new Vector2f(0.0f, 0.0f);
        skipRenderingContents = false;
        textWrapPositionStack = new FloatArrayList();
        titleBarHeight = 0.0f;
        treeDepth = 0;
        viewport = null;
        viewportID = 0;
        viewportOwned = false;
        wasActive = false;
        windowClass = new WindowClass();
        writeAccessed = false;
    }

    public boolean hasCloseButton() {
        return open != null;
    }

    public void setConditionAllowFlags(final int flags, final boolean enabled) {
        positionConditionAllowed =
                enabled ? (positionConditionAllowed | flags) : (positionConditionAllowed & ~flags);
        sizeConditionAllowed =
                enabled ? (sizeConditionAllowed | flags) : (sizeConditionAllowed & ~flags);
        collapsedConditionAllowed =
                enabled
                        ? (collapsedConditionAllowed | flags)
                        : (collapsedConditionAllowed & ~flags);
        dockConditionAllowed =
                enabled ? (dockConditionAllowed | flags) : (dockConditionAllowed & ~flags);
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

    public void setWindowDock(int dockID, @NonNull Condition condition) {
        if (!ConditionAllowed.shouldResolve(condition, sizeConditionAllowed)) {
            return;
        }
        dockConditionAllowed &=
                ~(ConditionAllowed.ONCE
                        | ConditionAllowed.FIRST_USE_EVER
                        | ConditionAllowed.APPEARING);

        if (this.dockID == dockID) {
            return;
        }

        DockNode newNode = IkGuiInternal.dockContextFindNodeByID(dockID);
        if (newNode != null && newNode.isSplitNode()) {
            newNode = IkGuiInternal.dockNodeGetRootNode(newNode);
            if (newNode.isCentralNode()) {
                if (!newNode.centralNode.isCentralNode()) {
                    log.error(
                            "Central node flag set, but not a central node. Something was set up wrong.");
                    return;
                }
                dockID = newNode.centralNode.id;
            } else {
                dockID = newNode.lastFocusedNodeID;
            }
        }
        if (this.dockID == dockID) {
            return;
        }
        if (this.dockNode != null) {
            IkGuiInternal.dockNodeRemoveWindow(this.dockNode, this, 0);
        }
        this.dockID = dockID;
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
