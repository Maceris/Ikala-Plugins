package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.enums.Direction;
import com.ikalagaming.graphics.frontend.gui.enums.GuiInputSource;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.enums.MouseCursor;
import com.ikalagaming.graphics.frontend.gui.event.GuiInputEvent;
import com.ikalagaming.graphics.frontend.gui.util.Rect;
import com.ikalagaming.util.FloatArrayList;
import com.ikalagaming.util.IntArrayList;

import org.joml.Vector2f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.*;

public class Context {
    public int activeID;
    public boolean activeIDActivatedThisFrame;
    public boolean activeIDAllowOverlap;
    public final Vector2f activeIDClickOffset;

    public boolean activeIDFromShortcut;
    public boolean activeIDHasBeenEditedBefore;
    public boolean activeIDHasBeenEditedThisFrame;
    public boolean activeIDHasBeenPressedBefore;
    public MouseButton activeIDMouseButton;
    public int activeIDPreviousFrame;
    public boolean activeIDRetainOnFocusLoss;
    public boolean activeIDSeenThisFrame;
    public GuiInputSource activeIDSource;
    public final IntArrayList activeIDStack;
    public float activeIDTimer;
    public Window activeIDWindow;

    public DrawList backgroundDrawList;

    public int beginComboDepth;

    public int beginMenuDepth;

    public final Deque<PopupData> beginPopupStack;
    public BoxSelectState boxSelectState;

    public int captureKeyboardNextFrameOverride;

    public int captureMouseNextFrameOverride;

    public StringBuilder clipboardHandlerData;
    public final List<ListClipperData> clipperTempData;
    public int colorEditCurrentID;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.ColorEditFlags
     */
    public int colorEditOptions;

    public int colorEditSavedColor;
    public int colorEditSavedHue;
    public int colorEditSavedID;
    public int colorEditSavedSaturation;
    public final Vector4f colorPickerReference;
    public final Deque<ColorMod> colorStack;
    public ComboPreviewData comboPreviewData;
    public boolean configNavWindowingWithGamepad;
    public int currentFocusScopeID;
    public int currentItemFlags;

    public MultiSelectTempData currentMultiSelect;
    public TabBar currentTabBar;
    public final IntArrayList currentTabBarStack;
    public Table currentTable;
    public DeactivatedItemData deactivatedItemData;
    public IkByte debugBeginReturnValueCullDepth;
    public int debugBreakKeyChord;
    public int debugDrawIDConflictCount;
    public boolean debugItemPickerActive;
    public StringBuilder debugLogBuffer;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.DebugLogFlags
     */
    public int debugLogFlags;

    public int debugLogIndex;
    public int debugLogSkippedErrors;
    public boolean debugShowGroupRects;
    public float dimBackgroundRatio;
    public float disabledAlphaBackup;
    public short disabledStackSize;

    /** The number of font points in one inch. */
    public int dpiScaleFont;

    /** The number of pixels in 1 inch. */
    public int dpiScaleScreen;

    public float dragCurrentAccumulatedDelta;
    public boolean dragCurrentAccumulatedDeltaDirty;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.DragDropFlags
     */
    public int dragDropAcceptFlags;

    public int dragDropAcceptFrameCount;
    public int dragDropAcceptIDCurrent;
    public float dragDropAcceptIDCurrentRectSurface;
    public int dragDropAcceptIDPrev;
    public boolean dragDropActive;

    public int dragDropHoldJustPressedID;
    public MouseButton dragDropMouseButton;
    public Object dragDropPayload;

    public ByteBuffer dragDropPayloadBufferLocal;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.DragDropFlags
     */
    public int dragDropSourceFlags;

    public int dragDropSourceFrameCount;
    public final Rect dragDropTargetClipRect;
    public int dragDropTargetID;

    public final Rect dragDropTargetRect;
    public boolean dragDropWithinSource;
    public boolean dragDropWithinTarget;
    public float dragSpeedDefaultRatio;
    public DrawData drawData;
    public DrawListSharedData drawListSharedData;
    public Object errorCallbackUserData;
    public int errorCountCurrentFrame;
    public boolean errorFirst;
    public final Vector2f errorTooltipLockedPosition;

    public final Deque<FocusScopeData> focusScopeStack;

    /**
     * The currently active font. If null, or when we see an unsupported glyph, we will look through
     * {@link #fontFallbacks} for fonts that support a character.
     */
    public Font font;

    /**
     * The list of fallback fonts, in order that we want to check them. If there is no {@link #font}
     * set, or a glyph is not found in the active font, this will be traversed until we find a font
     * that does support the glyph or run out of fonts.
     */
    public final List<Font> fontFallbacks;

    /** The character to use if no loaded fonts support a character. */
    public char fontFallbackChar;

    /** The current font size to use. */
    public int fontSize;

    public final Deque<FontBackup> fontStack;
    public DrawList foregroundDrawList;

    /**
     * The number of frames that have been started ({@link IkGui#newFrame()}). Changes each time we
     * start a new frame, incrementing but will wrap around to 0 eventually, so only guaranteed to
     * be useful when compared to equality with {@link #frameCountEnded} and {@link
     * #frameCountRendered}.
     */
    public int frameCount;

    /**
     * The number of frames that have been completed ({@link IkGui#endFrame()}, which is usually
     * implicitly called by {@link IkGui#render()}). Changes each time we start a new frame,
     * incrementing but will wrap around to 0 eventually, so only guaranteed to be useful when
     * compared to equality with {@link #frameCount} and {@link #frameCountRendered}.
     */
    public int frameCountEnded;

    /**
     * The number of frames that have been rendered ({@link IkGui#render()}). Changes each time we
     * start a new frame, incrementing but will wrap around to 0 eventually, so only guaranteed to
     * be useful when compared to equality with {@link #frameCount} and {@link #frameCountEnded}.
     */
    public int frameCountRendered;

    public float[] framerateSecondPerFrame;

    public float framerateSecondPerFrameAccumulator;
    public int framerateSecondPerFrameCount;
    public int framerateSecondPerFrameIndex;
    public final Deque<GroupData> groupStack;

    public int hoveredID;

    public boolean hoveredIDAllowOverlap;
    public boolean hoveredIDDisabled;
    public float hoveredIDInactiveTimer;

    public int hoveredIDPreviousFrame;
    public int hoveredIDPreviousFrameItemCount;
    public float hoveredIDTimer;

    /** Used by isItemHovered(), time before tooltip hover time gets cleared. */
    public float hoverItemDelayClearTimer;

    public int hoverItemDelayID;
    public int hoverItemDelayIDPreviousFrame;

    /** Used by isItemHovered(). */
    public float hoverItemDelayTimer;

    /** ID of the item that a mouse is stationary over, reset when it leaves the item. */
    public int hoverItemUnlockedStationaryID;

    /** ID of the window that a mouse is stationary over, reset when it leaves the window. */
    public int hoverWindowUnlockedStationaryID;

    public boolean initialized;

    public final List<GuiInputEvent> inputEventQueue;

    /**
     * Past input events that get processed when we start a new frame, mostly for mouse/pen trails.
     */
    public final List<GuiInputEvent> inputEventTrail;

    public InputTextDeactivatedState inputTextDeactivatedState;
    public InputTextState inputTextState;
    public boolean insideFrame;
    public IkIO io;
    public final IntArrayList itemFlagsStack;

    public int lastActiveID;

    public float lastActiveIDTimer;

    public LastItemData lastItemData;

    public double lastKeyboardKeyPressTime;

    public double lastKeyModsChangeFromNoneTime;
    public double lastKeyModsChangeTime;
    public int logDepthToExpand;

    public int logDepthToExpandDefault;

    public boolean logEnabled;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.LogFlags
     */
    public int logFlags;

    public Window logWindow;
    public Viewport mainViewport;
    public final IntArrayList menuIDsSubmittedThisFrame;
    public MouseCursor mouseCursor;
    public final Vector2f mouseLastValidPosition;
    public float mouseStationaryTimer;
    public final List<MultiSelectTempData> multiSelectStorage;
    public final List<MultiSelectTempData> multiSelectTempData;
    public int multiSelectTempDataStackSize;
    public int navActivateDownID;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.ActivateFlags
     */
    public int navActivateFlags;

    public int navActivateID;
    public int navActivatePressedID;
    public boolean navAnyRequest;

    public IkByte navCursorHideFrames;

    public boolean navCursorVisible;
    public Window navFocusedWindow;
    public final List<FocusScopeData> navFocusRoute;
    public int navFocusScopeID;
    public int navHighlightActivatedID;
    public float navHighlightActivatedTimer;

    public boolean navHighlightIgnoreMouse;

    public int navID;
    public boolean navIDAlive;
    public boolean navInitRequest;
    public boolean navInitRequestFromMove;
    public NavItemData navInitResult;
    public GuiInputSource navInputSource;

    public int navJustMovedFromFocusScopeID;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.NavMoveFlags
     */
    public int navJustMovedToFlags;

    public int navJustMovedToFocusScopeID;
    public int navJustMovedToID;
    public NavItemData navJustMovedToItemData;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.KeyModFlags
     */
    public int navJustMovedToKeyMods;

    public int navLastValidSelectionUserData;
    public boolean navMousePositionDirty;
    public Direction navMoveDirection;

    public Direction navMoveDirectionForDebug;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.NavMoveFlags
     */
    public int navMoveFlags;

    public boolean navMoveForwardToNextFrame;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.KeyModFlags
     */
    public int navMoveKeyMods;

    /** Best move request candidate within the nav window. */
    public NavItemData navMoveResultLocal;

    /** Best move request candidate within the nav window that are mostly visible. */
    public NavItemData navMoveResultLocalVisible;

    /** Best move request candidate within the nav window's flattened hierarchy. */
    public NavItemData navMoveResultOther;

    public boolean navMoveScoringItems;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.ScrollFlags
     */
    public int navMoveScrollFlags;

    public boolean navMoveSubmitted;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.ActivateFlags
     */
    public int navNextActivateFlags;

    public int navNextActivateID;
    public int navScoringDebugCount;
    public final Rect navScoringNoClipRect;

    public final Rect navScoringRect;
    public int navTabbingCounter;
    public int navTabbingDirection;

    /** First tabbing request candidate within the nav window and flattened hierarchy. */
    public NavItemData navTabbingResultFirst;

    public final Vector2f navWindowingAccumulatedDeltaPosition;

    public final Vector2f navWindowingAccumulatedDeltaSize;
    public float navWindowingHighlightAlpha;
    public GuiInputSource navWindowingInputSource;
    public Window navWindowingListWindow;
    public Window navWindowingTarget;
    public Window navWindowingTargetPrev;

    public float navWindowingTimer;

    public boolean navWindowingToggleLayer;
    public NextItemData nextItemData;

    public NextWindowData nextWindowData;

    public final Deque<PopupData> openPopupStack;

    public PlatformIO platformIO;
    public float scrollbarClickDistanceToCenter;

    /** 0 is scrolling to clicked location, +/- 1 is next/previous page. */
    public byte scrollbarSeekMode;

    public float settingsDirtyTimer;
    public boolean settingsLoaded;
    public final List<ShrinkWidthItem> shrinkWidthBuffer;

    public float sliderCurrentAccumulatedDelta;
    public boolean sliderCurrentAccumulatedDeltaDirty;
    public float sliderGrabClickOffset;
    public final Style style;
    public final Deque<StyleMod> styleVariableStack;
    public final List<TabBar> tabBars;

    public final List<Table> tables;
    public FloatArrayList tablesLastTimeActive;

    public final List<TableTempData> tablesTempData;

    public StringBuilder tempBuffer;

    public int textInputNextFrameOverride;
    public double time;
    public short tooltipOverrideCount;

    public Window tooltipPreviousWindow;
    public final Deque<TreeNodeStackData> treeNodeStack;
    public final List<Viewport> viewports;
    public int windowActiveCount;

    public final Map<Integer, Window> windowByID;

    public Window windowCurrent;

    public final List<Window> windowFocusOrder;
    public Window windowHovered;
    public Window windowHoveredUnderMovingWindow;
    public Window windowMoving;
    public final Rect windowResizeBorderExpectedRect;
    public boolean windowResizeRelativeMode;

    /** Windows, sorted in display order, back to front. */
    public final List<Window> windowsDisplayOrder;

    public Window windowWheeling;
    public final Vector2f windowWheelingAxisAverage;
    public final Vector2f windowWheelingRefMousePosition;
    public float windowWheelingReleaseTimer;
    public int windowWheelingScrolledFrame;
    public int windowWheelingStartFrame;
    public final Vector2f windowWheelingWheelRemainder;

    public Context() {
        activeID = 0;
        activeIDActivatedThisFrame = false;
        activeIDAllowOverlap = false;
        activeIDClickOffset = new Vector2f(0, 0);
        activeIDFromShortcut = false;
        activeIDHasBeenEditedBefore = false;
        activeIDHasBeenEditedThisFrame = false;
        activeIDHasBeenPressedBefore = false;
        activeIDMouseButton = MouseButton.NONE;
        activeIDPreviousFrame = 0;
        activeIDRetainOnFocusLoss = false;
        activeIDSeenThisFrame = false;
        activeIDSource = GuiInputSource.NONE;
        activeIDStack = new IntArrayList();
        activeIDTimer = 0.0f;
        activeIDWindow = null;
        backgroundDrawList = new DrawList("Background");
        beginComboDepth = 0;
        beginMenuDepth = 0;
        beginPopupStack = new ArrayDeque<>();
        boxSelectState = new BoxSelectState();
        captureKeyboardNextFrameOverride = 0;
        captureMouseNextFrameOverride = 0;
        clipboardHandlerData = new StringBuilder();
        clipperTempData = new ArrayList<>();
        colorEditCurrentID = 0;
        colorEditOptions = 0;
        colorEditSavedColor = 0;
        colorEditSavedHue = 0;
        colorEditSavedID = 0;
        colorEditSavedSaturation = 0;
        colorPickerReference = new Vector4f(0, 0, 0, 0);
        colorStack = new ArrayDeque<>();
        comboPreviewData = new ComboPreviewData();
        configNavWindowingWithGamepad = false;
        currentFocusScopeID = 0;
        currentItemFlags = 0;
        currentMultiSelect = new MultiSelectTempData();
        currentTabBar = null;
        currentTabBarStack = new IntArrayList();
        currentTable = null;
        deactivatedItemData = new DeactivatedItemData();
        debugBeginReturnValueCullDepth = new IkByte();
        debugBreakKeyChord = 0;
        debugDrawIDConflictCount = 0;
        debugItemPickerActive = false;
        debugLogBuffer = new StringBuilder();
        debugLogFlags = 0;
        debugLogIndex = 0;
        debugLogSkippedErrors = 0;
        debugShowGroupRects = false;
        dimBackgroundRatio = 0.0f;
        disabledAlphaBackup = 0.0f;
        disabledStackSize = 0;
        dpiScaleFont = 72;
        dpiScaleScreen = 96;
        dragCurrentAccumulatedDelta = 0.0f;
        dragCurrentAccumulatedDeltaDirty = false;
        dragDropAcceptFlags = 0;
        dragDropAcceptFrameCount = 0;
        dragDropAcceptIDCurrent = 0;
        dragDropAcceptIDCurrentRectSurface = 0.0f;
        dragDropAcceptIDPrev = 0;
        dragDropActive = false;
        dragDropHoldJustPressedID = 0;
        dragDropMouseButton = MouseButton.NONE;
        dragDropPayload = null;
        dragDropPayloadBufferLocal = null;
        dragDropSourceFlags = 0;
        dragDropSourceFrameCount = 0;
        dragDropTargetClipRect = new Rect(0, 0, 0, 0);
        dragDropTargetID = 0;
        dragDropTargetRect = new Rect(0, 0, 0, 0);
        dragDropWithinSource = false;
        dragDropWithinTarget = false;
        dragSpeedDefaultRatio = 0.0f;
        drawData = new DrawData();
        drawListSharedData = new DrawListSharedData();
        errorCallbackUserData = null;
        errorCountCurrentFrame = 0;
        errorFirst = false;
        errorTooltipLockedPosition = new Vector2f(0, 0);
        focusScopeStack = new ArrayDeque<>();
        font = null;
        fontFallbacks = new LinkedList<>();
        fontFallbackChar = '?';
        fontSize = 12;
        fontStack = new ArrayDeque<>();
        foregroundDrawList = new DrawList("Foreground");
        frameCount = 0;
        frameCountEnded = 0;
        frameCountRendered = 0;
        framerateSecondPerFrame = new float[60];
        framerateSecondPerFrameAccumulator = 0.0f;
        framerateSecondPerFrameCount = 0;
        framerateSecondPerFrameIndex = 0;
        groupStack = new ArrayDeque<>();
        hoveredID = 0;
        hoveredIDAllowOverlap = false;
        hoveredIDDisabled = false;
        hoveredIDInactiveTimer = 0.0f;
        hoveredIDPreviousFrame = 0;
        hoveredIDPreviousFrameItemCount = 0;
        hoveredIDTimer = 0.0f;
        hoverItemDelayClearTimer = 0.0f;
        hoverItemDelayID = 0;
        hoverItemDelayIDPreviousFrame = 0;
        hoverItemDelayTimer = 0.0f;
        hoverItemUnlockedStationaryID = 0;
        hoverWindowUnlockedStationaryID = 0;
        initialized = false;
        inputEventQueue = new ArrayList<>();
        inputEventTrail = new ArrayList<>();
        inputTextDeactivatedState = new InputTextDeactivatedState();
        inputTextState = new InputTextState();
        insideFrame = false;
        io = new IkIO();
        itemFlagsStack = new IntArrayList();
        lastActiveID = 0;
        lastActiveIDTimer = 0.0f;
        lastItemData = new LastItemData();
        lastKeyboardKeyPressTime = 0.0;
        lastKeyModsChangeFromNoneTime = 0.0;
        lastKeyModsChangeTime = 0.0;
        logDepthToExpand = 0;
        logDepthToExpandDefault = 0;
        logEnabled = false;
        logFlags = 0;
        logWindow = null;
        mainViewport = null;
        menuIDsSubmittedThisFrame = new IntArrayList();
        mouseCursor = MouseCursor.ARROW;
        mouseLastValidPosition = new Vector2f(0, 0);
        mouseStationaryTimer = 0.0f;
        multiSelectStorage = new ArrayList<>();
        multiSelectTempData = new ArrayList<>();
        multiSelectTempDataStackSize = 0;
        navActivateDownID = 0;
        navActivateFlags = 0;
        navActivateID = 0;
        navActivatePressedID = 0;
        navAnyRequest = false;
        navCursorHideFrames = new IkByte();
        navCursorVisible = false;
        navFocusedWindow = null;
        navFocusRoute = new ArrayList<>();
        navFocusScopeID = 0;
        navHighlightActivatedID = 0;
        navHighlightActivatedTimer = 0.0f;
        navHighlightIgnoreMouse = false;
        navID = 0;
        navIDAlive = false;
        navInitRequest = false;
        navInitRequestFromMove = false;
        navInitResult = new NavItemData();
        navInputSource = GuiInputSource.NONE;
        navJustMovedFromFocusScopeID = 0;
        navJustMovedToFlags = 0;
        navJustMovedToFocusScopeID = 0;
        navJustMovedToID = 0;
        navJustMovedToItemData = new NavItemData();
        navJustMovedToKeyMods = 0;
        navLastValidSelectionUserData = 0;
        navMousePositionDirty = false;
        navMoveDirection = Direction.NONE;
        navMoveDirectionForDebug = Direction.NONE;
        navMoveFlags = 0;
        navMoveForwardToNextFrame = false;
        navMoveKeyMods = 0;
        navMoveResultLocal = new NavItemData();
        navMoveResultLocalVisible = new NavItemData();
        navMoveResultOther = new NavItemData();
        navMoveScoringItems = false;
        navMoveScrollFlags = 0;
        navMoveSubmitted = false;
        navNextActivateFlags = 0;
        navNextActivateID = 0;
        navScoringDebugCount = 0;
        navScoringNoClipRect = new Rect(0, 0, 0, 0);
        navScoringRect = new Rect(0, 0, 0, 0);
        navTabbingCounter = 0;
        navTabbingDirection = 0;
        navTabbingResultFirst = new NavItemData();
        navWindowingAccumulatedDeltaPosition = new Vector2f(0, 0);
        navWindowingAccumulatedDeltaSize = new Vector2f(0, 0);
        navWindowingHighlightAlpha = 0.0f;
        navWindowingInputSource = GuiInputSource.NONE;
        navWindowingListWindow = null;
        navWindowingTarget = null;
        navWindowingTargetPrev = null;
        navWindowingTimer = 0.0f;
        navWindowingToggleLayer = false;
        nextItemData = new NextItemData();
        nextWindowData = new NextWindowData();
        openPopupStack = new ArrayDeque<>();
        platformIO = null;
        scrollbarClickDistanceToCenter = 0.0f;
        scrollbarSeekMode = 0;
        settingsDirtyTimer = 0.0f;
        settingsLoaded = false;
        shrinkWidthBuffer = new ArrayList<>();
        sliderCurrentAccumulatedDelta = 0.0f;
        sliderCurrentAccumulatedDeltaDirty = false;
        sliderGrabClickOffset = 0.0f;
        style = new Style();
        styleVariableStack = new ArrayDeque<>();
        tabBars = new ArrayList<>();
        tables = new ArrayList<>();
        tablesLastTimeActive = new FloatArrayList();
        tablesTempData = new ArrayList<>();
        tempBuffer = new StringBuilder();
        textInputNextFrameOverride = 0;
        time = 0.0;
        tooltipOverrideCount = 0;
        tooltipPreviousWindow = null;
        treeNodeStack = new ArrayDeque<>();
        viewports = new ArrayList<>();
        windowActiveCount = 0;
        windowByID = new HashMap<>();
        windowCurrent = null;
        windowFocusOrder = new ArrayList<>();
        windowHovered = null;
        windowHoveredUnderMovingWindow = null;
        windowMoving = null;
        windowResizeBorderExpectedRect = new Rect(0, 0, 0, 0);
        windowResizeRelativeMode = false;
        windowsDisplayOrder = new ArrayList<>();
        windowWheeling = null;
        windowWheelingAxisAverage = new Vector2f(0, 0);
        windowWheelingRefMousePosition = new Vector2f(0, 0);
        windowWheelingReleaseTimer = 0.0f;
        windowWheelingScrolledFrame = 0;
        windowWheelingStartFrame = 0;
        windowWheelingWheelRemainder = new Vector2f(0, 0);

        drawData.drawLists.add(backgroundDrawList);
        drawData.drawLists.add(foregroundDrawList);
    }
}
