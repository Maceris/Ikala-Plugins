package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.IkGuiInternal;
import com.ikalagaming.graphics.gui.enums.Direction;
import com.ikalagaming.graphics.gui.enums.LayoutType;
import com.ikalagaming.graphics.gui.flags.ConditionAllowed;
import com.ikalagaming.graphics.gui.flags.ItemStatusFlags;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.gui.util.Hash;
import com.ikalagaming.graphics.gui.util.RectFloat;
import com.ikalagaming.util.FloatArrayList;
import com.ikalagaming.util.IntArrayList;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Window {
    /** Set to true when the window is submitted (begin is called) during the current frame. */
    public boolean active;

    /**
     * How far in from each edge, in pixels, the window background and title bar fade from
     * transparent to their regular color, as (left, top, right, bottom). 0 means a hard edge.
     * Locked in from the style for the frame when the window begins.
     */
    public final Vector4f edgeFade;

    /** Whether the edge fade is inverted, so the fading edges are opaque instead. */
    public boolean edgeFadeInvert;

    /**
     * Whether the window is in the process of appearing after being hidden or inactive, or the
     * first frame it's displayed.
     */
    public boolean appearing;

    /** Number of frames remaining where we automatically fit the window width to the contents. */
    public IkByte autoFitFramesX;

    /** Number of frames remaining where we automatically fit the window height to the contents. */
    public IkByte autoFitFramesY;

    /** If true, auto-fitting only allows the window to grow, never shrink. */
    public boolean autoFitOnlyGrows;

    /** The text baseline offset of the current line, used to vertically align text. */
    public float baseOffsetCurrentLine;

    /** The text baseline offset of the previous line, used to vertically align text. */
    public float baseOffsetPreviousLine;

    /**
     * Number of begin calls during the current frame. Normally 0 or 1, but could be more if
     * appending using multiple begin/end pairs.
     */
    public short beginCount;

    /** The number of begin calls from the previous frame. */
    public short beginCountPreviousFrame;

    /** The order in which this window was begun this frame, out of all windows. */
    public short beginOrderWithinContext;

    /** The order in which this window was begun this frame, out of all children of the parent. */
    public short beginOrderWithinParent;

    /**
     * Which resize border is currently being dragged by the mouse, {@link Direction#NONE} if none.
     */
    public Direction borderBeingDragged;

    /**
     * Which resize border is currently being hovered by the mouse, {@link Direction#NONE} if none.
     */
    public Direction borderBeingHovered;

    /** Thickness of the border, in pixels. 0 if there is no border. */
    public float borderSize;

    /** The ID of the child, as submitted by the parent window with beginChild(). */
    public int childID;

    /** Item status flags of the child window item, as seen by the parent window. */
    public int childItemStatusFlags;

    /** Child windows submitted this frame, in submission order. */
    public final List<Window> childWindows;

    /** Whether the window is currently collapsed down to just the title bar. */
    public boolean collapsed;

    /**
     * Flags for the collapsed conditions.
     *
     * @see ConditionAllowed
     */
    public int collapsedConditionAllowed;

    /** Set when we want to toggle collapsed state, which will happen next frame. */
    public boolean collapseToggleRequested;

    /** Size of the contents last frame, used for scrolling and auto-resize. */
    public final Vector2f contentSize;

    /** Size of the contents set explicitly by the user, or 0 on an axis if not set. */
    public final Vector2f contentSizeExplicit;

    /**
     * Ideal size of the contents last frame, used for auto-resize. May be larger than {@link
     * #contentSize} if items wanted more space than they got.
     */
    public final Vector2f contentSizeIdeal;

    /** The current item width, which is used by default for widgets that have a width. */
    public float currentItemWidth;

    /** The index of the current table, or -1 if there is no current table. */
    public int currentTableIndex;

    /**
     * The current text wrap position, in window local coordinates. Negative means no wrapping, 0
     * means wrap at the end of the content region.
     */
    public float currentTextWrapPosition;

    /**
     * Grows as content is added, and used to determine the content region next frame, for
     * auto-resize. Used to track things that would like more space than they had available.
     */
    public final Vector2f cursorIdealMaxPosition;

    /**
     * Grows as content is added, and used to determine the content region next frame, for scrolling
     * and auto-resize.
     */
    public final Vector2f cursorMaxPosition;

    /** The current layout cursor position in screen coordinates, where the next item goes. */
    public final Vector2f cursorPosition;

    /** The position right after the last item on the previous line, used for sameLine(). */
    public final Vector2f cursorPreviousLinePosition;

    /** The initial cursor position for content, in screen coordinates. */
    public final Vector2f cursorStartPosition;

    /**
     * The precision lost in {@link #cursorStartPosition} due to very large scrolling amounts. The
     * list clipper uses this to compensate.
     */
    public final Vector2f cursorStartPositionLossyness;

    /** Outer decoration size on the left (we don't currently put decorations on the left). */
    public float decoOuterSizeX1;

    /** Outer decoration size on the right (vertical scrollbar). */
    public float decoOuterSizeX2;

    /** Outer decoration size on the top (title bar, menu bar). */
    public float decoOuterSizeY1;

    /** Outer decoration size on the bottom (horizontal scrollbar). */
    public float decoOuterSizeY2;

    /** Inner decoration size on the left (e.g. table frozen columns). */
    public float decoInnerSizeX1;

    /** Inner decoration size on the top (e.g. table frozen rows). */
    public float decoInnerSizeY1;

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

    /**
     * Item status flags of the tab in the dock node tab bar, so isItemXXX() queries work after
     * begin() for docked windows.
     *
     * @see com.ikalagaming.graphics.gui.flags.ItemStatusFlags
     */
    public int dockTabItemStatusFlags;

    /** Rectangle of the tab in the dock node tab bar. */
    public final RectFloat dockTabItemRect;

    public DrawList drawList;
    public int flags;
    public int flagsPreviousFrame;
    public int flagsAsChildWindow;

    /** Index in the context's focus order list, or -1 if not in the list (child windows). */
    public short focusOrder;

    /** The horizontal offset of the current group, if any. */
    public float groupOffset;

    /** Whether the window has a close button this frame. */
    public boolean hasCloseButton;

    /** Whether the window is hidden this frame. */
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

    /** The ID of the window, based on the name. */
    public final int id;

    public int idAsPopupWindow;

    /** ID of the "#MOVE" element. */
    public final int idMove;

    /**
     * The stack of IDs, which starts with the window ID and has IDs pushed onto it with pushID().
     */
    public final IntArrayList idStack;

    /** Persistent state for items in the window, such as whether tree nodes are open. */
    public final Storage stateStorage;

    /**
     * The storage used by items in the window this frame, which is normally {@link #stateStorage}
     * but can be replaced with setStateStorage(). Reset at the start of each frame.
     */
    public Storage currentStateStorage;

    /** ID of the "#TAB" element. */
    public final int idTab;

    public int idWithinParent;

    /** The current indentation from the left of the window, in pixels. */
    public float indent;

    /**
     * The offset to the current table column, applied when starting a new line. Tables set this so
     * that multiple lines in a cell line up with the start of the cell.
     */
    public float columnsOffset;

    public boolean isExplicitChild;
    public boolean isFallbackWindow;

    /** The item width before we pushed a new one, for restoring with popItemWidth(). */
    public final FloatArrayList itemWidthStack;

    /** The default item width for the window, calculated when we begin the window. */
    public float itemWidthDefault;

    /** The frame that the window was last focused on. */
    public int lastFrameJustFocused;

    /** The frame that the window was last submitted (begin was called). */
    public int lastFrameActive;

    /** The time (in milliseconds) that the window was last submitted (begin was called). */
    public long lastTimeActive;

    /** The layout direction, vertical by default. */
    public LayoutType layoutType;

    /** The size of the current line. */
    public final Vector2f lineSizeCurrent;

    /** The size of the previous line. */
    public final Vector2f lineSizePrevious;

    /** The height of the menu bar in pixels. 0 if there is no visible menu bar. */
    public float menuBarHeight;

    /** The (vertical) offset of the menu bar. */
    public final Vector2f menuBarOffset;

    /** True while we are between beginMenuBar() and endMenuBar() for this window. */
    public boolean menuBarAppending;

    /** Simplified column layout data for menu items, so they line up. */
    public final MenuColumns menuColumns;

    /**
     * The navigation layer items are currently being submitted to, 0 for the main layer and 1 for
     * the menu layer (title bar, menu bar).
     */
    public int navLayerCurrent;

    /** The last known navigation ID for each layer (0 for none). */
    public final int[] navLastIDs;

    /** The reference rectangle for each navigation layer, relative to the window position. */
    public final RectFloat[] navRectRelative;

    /**
     * Preferred position on each axis for scoring navigation moves, relative to the window
     * position, Float.MAX_VALUE for none. Stored on the root window for navigation.
     */
    public final Vector2f[] navPreferredScoringPositionRelative;

    /** The focus scope ID at the root of the window. */
    public int navRootFocusScopeID;

    /** When going to the menu bar, we remember the child window we came from. */
    public Window navLastChildNavWindow;

    /** Which navigation layers have been submitted to, as a bit mask ({@code 1 << layer}). */
    public int navLayersActiveMask;

    /**
     * Which navigation layers have been submitted to this frame, as a bit mask ({@code 1 <<
     * layer}).
     */
    public int navLayersActiveMaskNext;

    /** Whether the current location may be scrolled horizontally when moving left/right. */
    public boolean navIsScrollPushableX;

    /** Hide the navigation cursor for one frame. */
    public boolean navHideHighlightOneFrame;

    /** Whether the window can scroll vertically, for navigation scrolling. */
    public boolean navWindowHasScrollY;

    /**
     * The last direction used to position this window as a popup/tooltip, so that it stays in place
     * when possible. {@link Direction#NONE} if not positioned that way yet.
     */
    public Direction autoPosLastDirection;

    /** The name of the window, which is also used for the ID. */
    public String name;

    /** The window padding at the time of creation, locked for the frame. */
    public final Vector2f padding;

    /** The layout type of the parent window. */
    public LayoutType parentLayoutType;

    public Window parentWindow;

    public Window parentWindowInBeginStack;
    public Window parentWindowForFocusRoute;

    /** The position of the top left of the window, in screen coordinates. */
    public final Vector2f position;

    /**
     * Flags for the position conditions.
     *
     * @see ConditionAllowed
     */
    public int positionConditionAllowed;

    /**
     * The legacy content region, which by default is the region leading to no scrolling. Used for
     * mouse wheel scrolling, child sizes, getContentRegionAvailable().
     */
    public final RectFloat rectContent;

    /** Current clipping rect, since we can push and pop clip rects. */
    public final RectFloat rectCurrentClip;

    /** The inner part of the window, excluding the title bar, menu, scroll bars. */
    public final RectFloat rectInner;

    /** Inner rect, but shrunk by 0.5 * border, and clipped by the viewport or parent clip rect. */
    public final RectFloat rectInnerClip;

    /** The outer region of the window. */
    public final RectFloat rectOuter;

    /** {@link #rectOuter} after being clipped by the parent window or viewport. */
    public final RectFloat rectOuterClipped;

    /**
     * Backup of the work rect, which is restored for the parent when entering/exiting things like
     * tables and columns.
     */
    public final RectFloat rectParentWork;

    /**
     * The region where items are expected to be laid out, accounting for padding and scrolling. The
     * contents may extend past this.
     */
    public final RectFloat rectWork;

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

    /** Whether the next item will be placed on the same line as the previous one. */
    public boolean sameLine;

    /** Whether the cursor was explicitly set by the user since the last item. */
    public boolean setPos;

    /** Stores the window pivot. (0,0) is the top left, (1,1) is the bottom right. */
    public final Vector2f setWindowPosPivot;

    /**
     * Stores the window position when using a non-zero pivot, as we have to defer setting the
     * window position until we know the size.
     */
    public final Vector2f setWindowPosValue;

    /** Whether the horizontal scrollbar is visible. */
    public boolean scrollbarX;

    /** Whether the vertical scrollbar is visible. */
    public boolean scrollbarY;

    /**
     * Toggle history for the horizontal scrollbar, used to detect feedback loops where the scroll
     * bar keeps appearing and disappearing.
     */
    public int scrollbarXStabilizeToggledHistory;

    /** X is the width of the vertical scrollbar, y is the height of the horizontal scrollbar. */
    public final Vector2f scrollbarSizes;

    /** The maximum scroll position, based on contents and the window size. */
    public final Vector2f scrollMax;

    /** The current scroll position. */
    public final Vector2f scrollPosition;

    /**
     * The position we want to scroll to, or {@link Float#MAX_VALUE} on an axis if there is no
     * target.
     */
    public final Vector2f scrollTarget;

    /**
     * 0 = scroll so that the target is at the top/left, 0.5 = centered, 1 = scroll so the target is
     * at the bottom/right.
     */
    public final Vector2f scrollTargetCenterRatio;

    /** If positive, snap to edges when the scroll target is within this distance. */
    public final Vector2f scrollTargetEdgeSnapDist;

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

    /**
     * Set when items can be skipped (window is collapsed or hidden), so that widget functions can
     * early-out without doing any work.
     */
    public boolean skipItems;

    /** Stack of text wrap positions, for restoring with popTextWrapPos(). */
    public final FloatArrayList textWrapPositionStack;

    /** Height of the title bar, in pixels. 0 if there is no visible title bar. */
    public float titleBarHeight;

    public int treeDepth;

    /** Which tree depths have data stored on the tree node stack, as a bit mask. */
    public int treeHasStackDataDepthMask;

    /**
     * Bits for the tree depths that use TreeNodeFlags.DRAW_LINES_TO_NODES and still need to record
     * the position of clipped child nodes.
     */
    public int treeRecordsClippedNodesY2Mask;

    /** The viewport the window is in. Always set after begin(), but may be null between frames. */
    public Viewport viewport;

    /**
     * The ID of the viewport the window is in. This is the master data, which may be set when
     * {@link #viewport} is null, like when loaded from settings.
     */
    public int viewportID;

    /**
     * The last known position of the viewport, so it can be saved in settings and restored. Max
     * values when unknown.
     */
    public final Vector2f viewportPosition;

    /**
     * Allow the window to extend beyond its viewport onto the given monitor index, or -1 when not
     * allowed.
     */
    public int viewportAllowPlatformMonitorExtend;

    /** The settings entry for this window, if it has one, so we don't need to search for it. */
    public WindowSettings settings;

    public boolean viewportOwned;

    /** Whether the window was active last frame. */
    public boolean wasActive;

    public WindowClass windowClass;

    /** Item status flags for the window itself (title bar), which become the last item data. */
    public int windowItemStatusFlags;

    /** Set to true when any widget accesses the window. */
    public boolean writeAccessed;

    public Window(@NonNull String name) {
        active = false;
        edgeFade = new Vector4f(0, 0, 0, 0);
        edgeFadeInvert = false;
        appearing = false;
        autoFitFramesX = new IkByte();
        autoFitFramesY = new IkByte();
        autoFitOnlyGrows = false;
        baseOffsetCurrentLine = 0.0f;
        baseOffsetPreviousLine = 0.0f;
        beginCount = 0;
        beginCountPreviousFrame = 0;
        beginOrderWithinContext = -1;
        beginOrderWithinParent = -1;
        borderBeingDragged = Direction.NONE;
        borderBeingHovered = Direction.NONE;
        borderSize = 0.0f;
        childID = 0;
        childItemStatusFlags = ItemStatusFlags.NONE;
        childWindows = new ArrayList<>();
        collapsed = false;
        collapsedConditionAllowed = ConditionAllowed.ALL;
        collapseToggleRequested = false;
        contentSize = new Vector2f(0.0f, 0.0f);
        contentSizeExplicit = new Vector2f(0.0f, 0.0f);
        contentSizeIdeal = new Vector2f(0.0f, 0.0f);
        currentItemWidth = 0.0f;
        currentTableIndex = -1;
        currentTextWrapPosition = -1.0f;
        cursorIdealMaxPosition = new Vector2f(0.0f, 0.0f);
        cursorMaxPosition = new Vector2f(0.0f, 0.0f);
        cursorPosition = new Vector2f(0.0f, 0.0f);
        cursorPreviousLinePosition = new Vector2f(0.0f, 0.0f);
        cursorStartPosition = new Vector2f(0.0f, 0.0f);
        cursorStartPositionLossyness = new Vector2f(0.0f, 0.0f);
        decoOuterSizeX1 = 0.0f;
        decoOuterSizeX2 = 0.0f;
        decoOuterSizeY1 = 0.0f;
        decoOuterSizeY2 = 0.0f;
        decoInnerSizeX1 = 0.0f;
        decoInnerSizeY1 = 0.0f;
        disableInputsFrames = new IkByte();
        dockID = 0;
        dockConditionAllowed = ConditionAllowed.ALL;
        dockIsActive = false;
        dockNode = null;
        dockNodeAsHost = null;
        dockNodeIsVisible = false;
        dockOrder = -1;
        dockStyle = new WindowDockStyle();
        dockTabIsVisible = false;
        dockTabWantClose = false;
        dockTabItemStatusFlags = ItemStatusFlags.NONE;
        dockTabItemRect = new RectFloat();
        drawList = new DrawList(name);
        flags = WindowFlags.NONE;
        flagsPreviousFrame = WindowFlags.NONE;
        flagsAsChildWindow = WindowFlags.NONE;
        focusOrder = -1;
        groupOffset = 0.0f;
        columnsOffset = 0.0f;
        hasCloseButton = false;
        hidden = false;
        hiddenFramesCanSkipItems = new IkByte();
        hiddenFramesCannotSkipItems = new IkByte();
        hiddenFramesForRenderOnly = new IkByte();
        hitTestHolePosition = new Vector2f(0.0f, 0.0f);
        hitTestHoleSize = new Vector2f(0.0f, 0.0f);
        id = Hash.getID(name);
        idAsPopupWindow = 0;
        idMove = Hash.getID("#MOVE", id);
        idStack = new IntArrayList();
        stateStorage = new Storage();
        currentStateStorage = stateStorage;
        idStack.push(id);
        idTab = Hash.getID("#TAB", id);
        idWithinParent = 0;
        indent = 0.0f;
        isExplicitChild = false;
        isFallbackWindow = false;
        itemWidthStack = new FloatArrayList();
        itemWidthDefault = 0.0f;
        lastFrameJustFocused = -1;
        lastFrameActive = -1;
        lastTimeActive = -1;
        layoutType = LayoutType.VERTICAL;
        lineSizeCurrent = new Vector2f(0.0f, 0.0f);
        lineSizePrevious = new Vector2f(0.0f, 0.0f);
        menuBarHeight = 0.0f;
        menuBarOffset = new Vector2f(0.0f, 0.0f);
        menuBarAppending = false;
        menuColumns = new MenuColumns();
        navLayerCurrent = 0;
        navLastIDs = new int[] {0, 0};
        navRectRelative = new RectFloat[] {new RectFloat(0, 0, 0, 0), new RectFloat(0, 0, 0, 0)};
        navPreferredScoringPositionRelative =
                new Vector2f[] {
                    new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE),
                    new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE)
                };
        navRootFocusScopeID = 0;
        navLastChildNavWindow = null;
        navLayersActiveMask = 0;
        navLayersActiveMaskNext = 0;
        navIsScrollPushableX = true;
        navHideHighlightOneFrame = false;
        navWindowHasScrollY = false;
        autoPosLastDirection = Direction.NONE;
        this.name = name;
        padding = new Vector2f(0.0f, 0.0f);
        parentLayoutType = LayoutType.VERTICAL;
        parentWindow = null;
        parentWindowInBeginStack = null;
        parentWindowForFocusRoute = null;
        position = new Vector2f(60.0f, 60.0f);
        positionConditionAllowed = ConditionAllowed.ALL;
        rectContent = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        rectCurrentClip = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        rectInner = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        rectInnerClip = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        rectOuter = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        rectOuterClipped = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        rectParentWork = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        rectWork = new RectFloat(0.0f, 0.0f, 0.0f, 0.0f);
        rootWindow = this;
        rootWindowForNavigation = this;
        rootWindowForTitleBarHighlight = this;
        rootWindowPopupTree = this;
        rootWindowDockTree = this;
        rounding = 0.0f;
        sameLine = false;
        setPos = false;
        setWindowPosPivot = new Vector2f(0.0f, 0.0f);
        setWindowPosValue = new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE);
        scrollbarX = false;
        scrollbarY = false;
        scrollbarXStabilizeToggledHistory = 0;
        scrollbarSizes = new Vector2f(0.0f, 0.0f);
        scrollMax = new Vector2f(0.0f, 0.0f);
        scrollPosition = new Vector2f(0.0f, 0.0f);
        scrollTarget = new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE);
        scrollTargetCenterRatio = new Vector2f(0.5f, 0.5f);
        scrollTargetEdgeSnapDist = new Vector2f(0.0f, 0.0f);
        sizeConditionAllowed = ConditionAllowed.ALL;
        size = new Vector2f(0.0f, 0.0f);
        sizeFull = new Vector2f(0.0f, 0.0f);
        skipItems = false;
        textWrapPositionStack = new FloatArrayList();
        titleBarHeight = 0.0f;
        treeDepth = 0;
        viewport = null;
        viewportID = 0;
        viewportPosition = new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE);
        viewportAllowPlatformMonitorExtend = -1;
        settings = null;
        viewportOwned = false;
        wasActive = false;
        windowClass = new WindowClass();
        windowItemStatusFlags = ItemStatusFlags.NONE;
        writeAccessed = false;
    }

    /**
     * Calculate an ID based on the current ID stack of the window.
     *
     * @param name The name to hash.
     * @return The resulting ID.
     */
    public int getID(String name) {
        final int id = Hash.getID(name, idStack.peek());
        IkGuiInternal.debugHookIDInfo(id, name);
        return id;
    }

    /**
     * Calculate an ID based on the current ID stack of the window.
     *
     * @param value The integer to hash.
     * @return The resulting ID.
     */
    public int getID(int value) {
        final int id = Hash.getID(value, idStack.peek());
        IkGuiInternal.debugHookIDInfo(id, value);
        return id;
    }

    /**
     * Calculate an ID from a rectangle, relative to the window contents start, based on the current
     * ID stack of the window. Used for items that don't have an ID of their own. The ID won't
     * survive any repositioning or resizing of the item.
     *
     * @param rect The rectangle in screen space.
     * @return The resulting ID.
     */
    public int getIDFromRectangle(@NonNull RectFloat rect) {
        final float offsetX = cursorStartPosition.x;
        final float offsetY = cursorStartPosition.y;
        int id = idStack.peek();
        id = Hash.getID(Float.floatToIntBits(rect.getLeft() - offsetX), id);
        id = Hash.getID(Float.floatToIntBits(rect.getTop() - offsetY), id);
        id = Hash.getID(Float.floatToIntBits(rect.getRight() - offsetX), id);
        id = Hash.getID(Float.floatToIntBits(rect.getBottom() - offsetY), id);
        return id;
    }

    /**
     * The rectangle of the whole window, in screen coordinates.
     *
     * @return A new rectangle from the position to the position plus the size.
     */
    public RectFloat getRect() {
        return new RectFloat(position.x, position.y, position.x + size.x, position.y + size.y);
    }

    /**
     * Calculate the rectangle for the title bar, in screen coordinates.
     *
     * @param output Where to store the result.
     * @return The output rectangle, for convenience.
     */
    public RectFloat getTitleBarRect(@NonNull RectFloat output) {
        output.set(position.x, position.y, position.x + sizeFull.x, position.y + titleBarHeight);
        return output;
    }

    /**
     * Calculate the rectangle for the menu bar, in screen coordinates.
     *
     * @param output Where to store the result.
     * @return The output rectangle, for convenience.
     */
    public RectFloat getMenuBarRect(@NonNull RectFloat output) {
        final float y1 = position.y + titleBarHeight;
        output.set(position.x, y1, position.x + sizeFull.x, y1 + menuBarHeight);
        return output;
    }

    /**
     * Update the outer rectangle to match the current position and size.
     *
     * @return The outer rectangle, for convenience.
     */
    public RectFloat updateRectOuter() {
        rectOuter.set(position.x, position.y, position.x + size.x, position.y + size.y);
        return rectOuter;
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
}
