package com.ikalagaming.graphics.frontend.gui.data;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Context {
    public boolean initialized;
    public IkIO io;
    public PlatformIO platformIO;
    public Style style;

    public DrawData drawData;
    public List<FontAtlas> fontAtlases;
    public Font font;
    public FontBaked fontBaked;
    public float fontSize;
    public float fontSizeBase;
    public float fontDensity;
    public float dpiScale;
    public DrawListSharedData drawListSharedData;
    public double time;
    public int frameCount;
    public int frameCountEnded;
    public int frameCountRendered;
    public boolean insideFrame;
    public List<GuiInputEvent> inputEventQueue;

    /**
     * Past input events that get processed when we start a new frame, mostly for mouse/pen trails.
     */
    public List<GuiInputEvent> inputEventTrail;

    /** Windows, sorted in display order, back to front. */
    public List<Window> windowsDisplayOrder;

    public List<Window> windowFocusOrder;
    public Map<Integer, Window> windowByID;
    public int windowActiveCount;
    public Window windowCurrent;
    public Window windowHovered;
    public Window windowHoveredUnderMovingWindow;
    public Window windowMoving;
    public Window windowWheeling;
    public Vector2f windowWheelingRefMousePosition;
    public int windowWheelingStartFrame;
    public int windowWheelingScrolledFrame;
    public float windowWheelingReleaseTimer;
    public Vector2f windowWheelingWheelRemainder;
    public Vector2f windowWheelingAxisAverage;

    public int hoveredID;
    public int hoveredIDPreviousFrame;
    public int hoveredIDPreviousFrameItemCount;
    public float hoveredIDTimer;
    public float hoveredIDInactiveTimer;
    public boolean hoveredIDAllowOverlap;
    public boolean hoveredIDDisabled;
    public int activeID;
    public float activeIDTimer;
    public boolean activeIDSeenThisFrame;
    public boolean activeIDActivatedThisFrame;
    public boolean activeIDAllowOverlap;
    public boolean activeIDRetainOnFocusLoss;
    public boolean activeIDHasBeenPressedBefore;
    public boolean activeIDHasBeenEditedBefore;
    public boolean activeIDHasBeenEditedThisFrame;
    public boolean activeIDFromShortcut;
    public MouseButton activeIDMouseButton;
    public Vector2f activeIDClickOffset;
    public Window activeIDWindow;
    public GuiInputSource activeIDSource;
    public int activeIDPreviousFrame;
    public DeactivatedItemData deactivatedItemData;
    public int lastActiveID;
    public float lastActiveIDTimer;
    public IntArrayList activeIDStack;

    public double lastKeyModsChangeTime;
    public double lastKeyModsChangeFromNoneTime;
    public double lastKeyboardKeyPressTime;

    public int currentFocusScopeID;
    public int currentItemFlags;
    public NextItemData nextItemData;
    public LastItemData lastItemData;
    public NextWindowData nextWindowData;

    public boolean debugShowGroupRects;
    public List<ColorMod> colorStack;
    public List<StyleMod> styleVariableStack;
    public List<FontStackInfo> fontStack;
    public List<FocusScopeData> focusScopeStack;
    public IntArrayList itemFlagsStack;
    public List<GroupData> groupStack;
    public List<PopupData> openPopupStack;
    public List<PopupData> beginPopupStack;
    public List<TreeNodeStackData> treeNodeStack;

    public List<Viewport> viewports;

    public boolean navCursorVisible;
    public boolean navHighlightIgnoreMouse;
    public boolean navMousePositionDirty;
    public boolean navIDAlive;
    public int navID;
    public Window navFocusedWindow;
    public int navFocusScopeID;
    public int navActivateID;
    public int navActivateDownID;
    public int navActrivatePressedID;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.ActivateFlags
     */
    public int navActivateFlags;

    public ArrayList<FocusScopeData> navFocusRoute;
    public int navHighlightActivatedID;
    public float navHighlightActivatedTimer;
    public int navNextActivateID;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.ActivateFlags
     */
    public int navNextActivateFlags;

    public GuiInputSource navInputSource;
    public int navLastValidSelectionUserData;
    public IkByte navCursorHideFrames;

    public boolean navAnyRequest;
    public boolean navInitRequest;
    public boolean navInitRequestFromMove;
    public NavItemData navInitResult;
    public boolean navMoveSubmitted;
    public boolean navMoveScoringItems;
    public boolean navMoveForwardToNextFrame;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.NavMoveFlags
     */
    public int navMoveFlags;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.ScrollFlags
     */
    public int navMoveScrollFlags;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.KeyModFlags
     */
    public int navMoveKeyMods;

    public Direction navMoveDirection;
    public Direction navMoveDirectionForDebug;
    public Rect navScoringRect;
    public Rect navScoringNoClipRect;
    public int navScoringDebugCount;
    public int navTabbingDirection;
    public int navTabbingCounter;

    /** Best move request candidate within the nav window. */
    public NavItemData navMoveResultLocal;

    /** Best move request candidate within the nav window that are mostly visible. */
    public NavItemData navMoveResultLocalVisible;

    /** Best move request candidate within the nav window's flattened hierarchy. */
    public NavItemData navMoveResultOther;

    /** First tabbing request candidate within the nav window and flattened hierarchy. */
    public NavItemData navTabbingResultFirst;

    public int navJustMovedFromFocusScopeID;
    public int navJustMovedToID;
    public int navJustMovedToFocusScopeID;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.KeyModFlags
     */
    public int navJustMovedToKeyMods;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.NavMoveFlags
     */
    public int navJustMovedToFlags;

    public NavItemData navJustMovedToItemData;

    public boolean configNavWindowingWithGamepad;
    public Window navWindowingTarget;
    public Window navWindowingTargetPrev;
    public Window navWindowingListWindow;
    public float navWindowingTimer;
    public float navWindowingHighlightAlpha;
    public GuiInputSource navWindowingInputSource;
    public boolean navWindowingToggleLayer;
    public Vector2f navWindowingAccumulatedDeltaPosition;
    public Vector2f navWindowingAccumulatedDeltaSize;

    public float dimBackgroundRatio;

    public boolean dragDropActive;
    public boolean dragDropWithinSource;
    public boolean dragDropWithinTarget;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.DragDropFlags
     */
    public int dragDropSourceFlags;

    public int dragDropSourceFrameCount;
    public MouseButton dragDropMouseButton;
    public Object dragDropPayload;
    public Rect dragDropTargetRect;
    public Rect dragDropTargetClipRect;
    public int dragDropTargetID;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.DragDropFlags
     */
    public int dragDropAcceptFlags;

    public float dragDropAcceptIDCurrentRectSurface;
    public int dragDropAcceptIDCurrent;
    public int dragDropAcceptIDPrev;
    public int dragDropAcceptFrameCount;
    public int dragDropHoldJustPressedID;
    public ByteBuffer dragDropPayloadBufferLocal;

    public ArrayList<ListClipperData> clipperTempData;

    public Table currentTable;
    public ArrayList<TableTempData> tablesTempData;
    public ArrayList<Table> tables;
    public FloatArrayList tablesLastTimeActive;

    public TabBar currentTabBar;
    public ArrayList<TabBar> tabBars;
    public IntArrayList currentTabBarStack;
    public ArrayList<ShrinkWidthItem> shrinkWidthBuffer;

    public BoxSelectState boxSelectState;
    public MultiSelectTempData currentMultiSelect;
    public int multiSelectTempDataStackSize;
    public ArrayList<MultiSelectTempData> multiSelectTempData;
    public ArrayList<MultiSelectTempData> multiSelectStorage;

    public int hoverItemDelayID;
    public int hoverItemDelayIDPreviousFrame;

    /** Used by isItemHovered(). */
    public float hoverItemDelayTimer;

    /** Used by isItemHovered(), time before tooltip hover time gets cleared. */
    public float hoverItemDelayClearTimer;

    /** ID of the item that a mouse is stationary over, reset when it leaves the item. */
    public int hoverItemUnlockedStationaryID;

    /** ID of the window that a mouse is stationary over, reset when it leaves the window. */
    public int hoverWindowUnlockedStationaryID;

    public MouseCursor mouseCursor;
    public float mouseStationaryTimer;
    public Vector2f mouseLastValidPosition;

    public InputTextState inputTextState;
    public InputTextDeactivatedState inputTextDeactivatedState;
    public int beginMenuDepth;
    public int beginComboDepth;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.ColorEditFlags
     */
    public int colorEditOptions;

    public int colorEditCurrentID;
    public int colorEditSavedID;
    public int colorEditSavedHue;
    public int colorEditSavedSaturation;
    public int colorEditSavedColor;
    public Vector4f colorPickerReference;

    public ComboPreviewData comboPreviewData;

    public Rect windowResizeBorderExpectedRect;
    public boolean windowResizeRelativeMode;

    /** 0 is scrolling to clicked location, +/- 1 is next/previous page. */
    public byte scrollbarSeekMode;

    public float scrollbarClickDistanceToCenter;

    public float sliderGrabClickOffset;
    public float sliderCurrentAccumulatedDelta;
    public boolean sliderCurrentAccumulatedDeltaDirty;

    public float dragCurrentAccumulatedDelta;
    public boolean dragCurrentAccumulatedDeltaDirty;
    public float dragSpeedDefaultRatio;

    public float disabledAlphaBackup;
    public short disabledStackSize;
    public short tooltipOverrideCount;
    public Window tooltipPreviousWindow;
    public StringBuilder clipboardHandlerData;
    public IntArrayList menuIDsSubmittedThisFrame;

    public boolean settingsLoaded;
    public float settingsDirtyTimer;

    public boolean logEnabled;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.LogFlags
     */
    public int logFlags;

    public Window logWindow;
    public int logDepthToExpand;
    public int logDepthToExpandDefault;

    public Object errorCallbackUserData;
    public Vector2f errorTooltipLockedPosition;
    public boolean errorFirst;
    public int errorCountCurrentFrame;

    public int debugDrawIDConflictCount;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.DebugLogFlags
     */
    public int debugLogFlags;

    public StringBuilder debugLogBuffer;
    public int debugLogIndex;
    public int debugLogSkippedErrors;
    public int debugBreakKeyChord;
    public IkByte debugBeginReturnValueCullDepth;
    public boolean debugItemPickerActive;

    public float[] framerateSecondPerFrame;
    public int framerateSecondPerFrameIndex;
    public int framerateSecondPerFrameCount;
    public float framerateSecondPerFrameAccumulator;
    public int captureMouseNextFrameOverride;
    public int captureKeyboardNextFrameOverride;
    public int textInputNextFrameOverride;
    public StringBuilder tempBuffer;

    public Context() {
        initialized = false;
        io = new IkIO(this);
        platformIO = null;
        style = new Style();
        drawData = new DrawData();
        fontAtlases = new ArrayList<>();
        font = null;
        fontBaked = null;
        fontSize = 0.0f;
        fontSizeBase = 0.0f;
        fontDensity = 0.0f;
        dpiScale = 0.0f;
        drawListSharedData = new DrawListSharedData();
        time = 0.0;
        frameCount = 0;
        frameCountEnded = 0;
        frameCountRendered = 0;
        insideFrame = false;
        inputEventQueue = new ArrayList<>();
        inputEventTrail = new ArrayList<>();
        windowsDisplayOrder = new ArrayList<>();
        windowFocusOrder = new ArrayList<>();
        windowByID = new HashMap<>();
        windowActiveCount = 0;
        windowCurrent = null;
        windowHovered = null;
        windowHoveredUnderMovingWindow = null;
        windowMoving = null;
        windowWheeling = null;
        windowWheelingRefMousePosition = new Vector2f(0, 0);
        windowWheelingStartFrame = 0;
        windowWheelingScrolledFrame = 0;
        windowWheelingReleaseTimer = 0.0f;
        windowWheelingWheelRemainder = new Vector2f(0, 0);
        windowWheelingAxisAverage = new Vector2f(0, 0);
        hoveredID = 0;
        hoveredIDPreviousFrame = 0;
        hoveredIDPreviousFrameItemCount = 0;
        hoveredIDTimer = 0.0f;
        hoveredIDInactiveTimer = 0.0f;
        hoveredIDAllowOverlap = false;
        hoveredIDDisabled = false;
        activeID = 0;
        activeIDTimer = 0.0f;
        activeIDSeenThisFrame = false;
        activeIDActivatedThisFrame = false;
        activeIDAllowOverlap = false;
        activeIDRetainOnFocusLoss = false;
        activeIDHasBeenPressedBefore = false;
        activeIDHasBeenEditedBefore = false;
        activeIDHasBeenEditedThisFrame = false;
        activeIDFromShortcut = false;
        activeIDMouseButton = MouseButton.NONE;
        activeIDClickOffset = new Vector2f(0, 0);
        activeIDWindow = null;
        activeIDSource = GuiInputSource.NONE;
        activeIDPreviousFrame = 0;
        deactivatedItemData = new DeactivatedItemData();
        lastActiveID = 0;
        lastActiveIDTimer = 0.0f;
        activeIDStack = new IntArrayList();
        lastKeyModsChangeTime = 0.0;
        lastKeyModsChangeFromNoneTime = 0.0;
        lastKeyboardKeyPressTime = 0.0;
        currentFocusScopeID = 0;
        currentItemFlags = 0;
        nextItemData = new NextItemData();
        lastItemData = new LastItemData();
        nextWindowData = new NextWindowData();
        debugShowGroupRects = false;
        colorStack = new ArrayList<>();
        styleVariableStack = new ArrayList<>();
        fontStack = new ArrayList<>();
        focusScopeStack = new ArrayList<>();
        itemFlagsStack = new IntArrayList();
        groupStack = new ArrayList<>();
        openPopupStack = new ArrayList<>();
        beginPopupStack = new ArrayList<>();
        treeNodeStack = new ArrayList<>();
        viewports = new ArrayList<>();
        navCursorVisible = false;
        navHighlightIgnoreMouse = false;
        navMousePositionDirty = false;
        navIDAlive = false;
        navID = 0;
        navFocusedWindow = null;
        navFocusScopeID = 0;
        navActivateID = 0;
        navActivateDownID = 0;
        navActrivatePressedID = 0;
        navActivateFlags = 0;
        navFocusRoute = new ArrayList<>();
        navHighlightActivatedID = 0;
        navHighlightActivatedTimer = 0.0f;
        navNextActivateID = 0;
        navNextActivateFlags = 0;
        navInputSource = GuiInputSource.NONE;
        navLastValidSelectionUserData = 0;
        navCursorHideFrames = new IkByte();
        navAnyRequest = false;
        navInitRequest = false;
        navInitRequestFromMove = false;
        navInitResult = new NavItemData();
        navMoveSubmitted = false;
        navMoveScoringItems = false;
        navMoveForwardToNextFrame = false;
        navMoveFlags = 0;
        navMoveScrollFlags = 0;
        navMoveKeyMods = 0;
        navMoveDirection = Direction.NONE;
        navMoveDirectionForDebug = Direction.NONE;
        navScoringRect = new Rect(0, 0, 0, 0);
        navScoringNoClipRect = new Rect(0, 0, 0, 0);
        navScoringDebugCount = 0;
        navTabbingDirection = 0;
        navTabbingCounter = 0;
        navMoveResultLocal = new NavItemData();
        navMoveResultLocalVisible = new NavItemData();
        navMoveResultOther = new NavItemData();
        navTabbingResultFirst = new NavItemData();
        navJustMovedFromFocusScopeID = 0;
        navJustMovedToID = 0;
        navJustMovedToFocusScopeID = 0;
        navJustMovedToKeyMods = 0;
        navJustMovedToFlags = 0;
        navJustMovedToItemData = new NavItemData();
        configNavWindowingWithGamepad = false;
        navWindowingTarget = null;
        navWindowingTargetPrev = null;
        navWindowingListWindow = null;
        navWindowingTimer = 0.0f;
        navWindowingHighlightAlpha = 0.0f;
        navWindowingInputSource = GuiInputSource.NONE;
        navWindowingToggleLayer = false;
        navWindowingAccumulatedDeltaPosition = new Vector2f(0, 0);
        navWindowingAccumulatedDeltaSize = new Vector2f(0, 0);
        dimBackgroundRatio = 0.0f;
        dragDropActive = false;
        dragDropWithinSource = false;
        dragDropWithinTarget = false;
        dragDropSourceFlags = 0;
        dragDropSourceFrameCount = 0;
        dragDropMouseButton = MouseButton.NONE;
        dragDropPayload = null;
        dragDropTargetRect = new Rect(0, 0, 0, 0);
        dragDropTargetClipRect = new Rect(0, 0, 0, 0);
        dragDropTargetID = 0;
        dragDropAcceptFlags = 0;
        dragDropAcceptIDCurrentRectSurface = 0.0f;
        dragDropAcceptIDCurrent = 0;
        dragDropAcceptIDPrev = 0;
        dragDropAcceptFrameCount = 0;
        dragDropHoldJustPressedID = 0;
        dragDropPayloadBufferLocal = null;
        clipperTempData = new ArrayList<>();
        currentTable = null;
        tablesTempData = new ArrayList<>();
        tables = new ArrayList<>();
        tablesLastTimeActive = new FloatArrayList();
        currentTabBar = null;
        tabBars = new ArrayList<>();
        currentTabBarStack = new IntArrayList();
        shrinkWidthBuffer = new ArrayList<>();
        boxSelectState = new BoxSelectState();
        currentMultiSelect = new MultiSelectTempData();
        multiSelectTempDataStackSize = 0;
        multiSelectTempData = new ArrayList<>();
        multiSelectStorage = new ArrayList<>();
        hoverItemDelayID = 0;
        hoverItemDelayIDPreviousFrame = 0;
        hoverItemDelayTimer = 0.0f;
        hoverItemDelayClearTimer = 0.0f;
        hoverItemUnlockedStationaryID = 0;
        hoverWindowUnlockedStationaryID = 0;
        mouseCursor = MouseCursor.ARROW;
        mouseStationaryTimer = 0.0f;
        mouseLastValidPosition = new Vector2f(0, 0);
        inputTextState = new InputTextState();
        inputTextDeactivatedState = new InputTextDeactivatedState();
        beginMenuDepth = 0;
        beginComboDepth = 0;
        colorEditOptions = 0;
        colorEditCurrentID = 0;
        colorEditSavedID = 0;
        colorEditSavedHue = 0;
        colorEditSavedSaturation = 0;
        colorEditSavedColor = 0;
        colorPickerReference = new Vector4f(0, 0, 0, 0);
        comboPreviewData = new ComboPreviewData();
        windowResizeBorderExpectedRect = new Rect(0, 0, 0, 0);
        windowResizeRelativeMode = false;
        scrollbarSeekMode = 0;
        scrollbarClickDistanceToCenter = 0.0f;
        sliderGrabClickOffset = 0.0f;
        sliderCurrentAccumulatedDelta = 0.0f;
        sliderCurrentAccumulatedDeltaDirty = false;
        dragCurrentAccumulatedDelta = 0.0f;
        dragCurrentAccumulatedDeltaDirty = false;
        dragSpeedDefaultRatio = 0.0f;
        disabledAlphaBackup = 0.0f;
        disabledStackSize = 0;
        tooltipOverrideCount = 0;
        tooltipPreviousWindow = null;
        clipboardHandlerData = new StringBuilder();
        menuIDsSubmittedThisFrame = new IntArrayList();
        settingsLoaded = false;
        settingsDirtyTimer = 0.0f;
        logEnabled = false;
        logFlags = 0;
        logWindow = null;
        logDepthToExpand = 0;
        logDepthToExpandDefault = 0;
        errorCallbackUserData = null;
        errorTooltipLockedPosition = new Vector2f(0, 0);
        errorFirst = false;
        errorCountCurrentFrame = 0;
        debugDrawIDConflictCount = 0;
        debugLogFlags = 0;
        debugLogBuffer = new StringBuilder();
        debugLogIndex = 0;
        debugLogSkippedErrors = 0;
        debugBreakKeyChord = 0;
        debugBeginReturnValueCullDepth = new IkByte();
        debugItemPickerActive = false;
        framerateSecondPerFrame = new float[60];
        framerateSecondPerFrameIndex = 0;
        framerateSecondPerFrameCount = 0;
        framerateSecondPerFrameAccumulator = 0.0f;
        captureMouseNextFrameOverride = 0;
        captureKeyboardNextFrameOverride = 0;
        textInputNextFrameOverride = 0;
        tempBuffer = new StringBuilder();
    }
}
