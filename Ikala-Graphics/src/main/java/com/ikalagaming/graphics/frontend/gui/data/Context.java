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
import java.util.Map;

public class Context {
    public boolean initialized = false;
    public IkIO io = new IkIO();
    public PlatformIO platformIO = null;
    public Style style = new Style();

    public DrawData drawData = new DrawData();
    public ArrayList<FontAtlas> fontAtlases = new ArrayList<>();
    public Font font = null;
    public FontBaked fontBaked = null;
    public float fontSize = 0.0f;
    public float fontSizeBase = 0.0f;
    public float fontDensity = 0.0f;
    public float dpiScale = 0.0f;
    public DrawListSharedData drawListSharedData = new DrawListSharedData();
    public double time = 0.0;
    public int frameCount = 0;
    public int frameCountEnded = 0;
    public int frameCountRendered = 0;
    public boolean insideFrame = false;
    public ArrayList<GuiInputEvent> inputEventQueue = new ArrayList<>();

    /**
     * Past input events that get processed when we start a new frame, mostly for mouse/pen trails.
     */
    public ArrayList<GuiInputEvent> inputEventTrail = new ArrayList<>();

    /** Windows, sorted in display order, back to front. */
    public ArrayList<Window> windowsDisplayOrder = new ArrayList<>();

    public ArrayList<Window> windowFocusOrder = new ArrayList<>();
    public Map<Integer, Window> windowByID = new HashMap<>();
    public int windowActiveCount = 0;
    public Window windowCurrent = null;
    public Window windowHovered = null;
    public Window windowHoveredUnderMovingWindow = null;
    public Window windowMoving = null;
    public Window windowWheeling = null;
    public Vector2f windowWheelingRefMousePosition = new Vector2f(0, 0);
    public int windowWheelingStartFrame = 0;
    public int windowWheelingScrolledFrame = 0;
    public float windowWheelingReleaseTimer = 0.0f;
    public Vector2f windowWheelingWheelRemainder = new Vector2f(0, 0);
    public Vector2f windowWheelingAxisAverage = new Vector2f(0, 0);

    public int hoveredID = 0;
    public int hoveredIDPreviousFrame = 0;
    public int hoveredIDPreviousFrameItemCount = 0;
    public float hoveredIDTimer = 0.0f;
    public float hoveredIDInactiveTimer = 0.0f;
    public boolean hoveredIDAllowOverlap = false;
    public boolean hoveredIDDisabled = false;
    public int activeID = 0;
    public float activeIDTimer = 0.0f;
    public boolean activeIDSeenThisFrame = false;
    public boolean activeIDActivatedThisFrame = false;
    public boolean activeIDAllowOverlap = false;
    public boolean activeIDRetainOnFocusLoss = false;
    public boolean activeIDHasBeenPressedBefore = false;
    public boolean activeIDHasBeenEditedBefore = false;
    public boolean activeIDHasBeenEditedThisFrame = false;
    public boolean activeIDFromShortcut = false;
    public MouseButton activeIDMouseButton = MouseButton.NONE;
    public Vector2f activeIDClickOffset = new Vector2f(0, 0);
    public Window activeIDWindow = null;
    public GuiInputSource activeIDSource = GuiInputSource.NONE;
    public int activeIDPreviousFrame = 0;
    public DeactivatedItemData deactivatedItemData = new DeactivatedItemData();
    public int lastActiveID = 0;
    public float lastActiveIDTimer = 0.0f;
    public IntArrayList activeIDStack = new IntArrayList();

    public double lastKeyModsChangeTime = 0.0;
    public double lastKeyModsChangeFromNoneTime = 0.0;
    public double lastKeyboardKeyPressTime = 0.0;

    public int currentFocusScopeID = 0;
    public int currentItemFlags = 0;
    public NextItemData nextItemData = new NextItemData();
    public LastItemData lastItemData = new LastItemData();
    public NextWindowData nextWindowData = new NextWindowData();

    public boolean debugShowGroupRects = false;
    public ArrayList<ColorMod> colorStack = new ArrayList<>();
    public ArrayList<StyleMod> styleVariableStack = new ArrayList<>();
    public ArrayList<FontStackInfo> fontStack = new ArrayList<>();
    public ArrayList<FocusScopeData> focusScopeStack = new ArrayList<>();
    public IntArrayList itemFlagsStack = new IntArrayList();
    public ArrayList<GroupData> groupStack = new ArrayList<>();
    public ArrayList<PopupData> openPopupStack = new ArrayList<>();
    public ArrayList<PopupData> beginPopupStack = new ArrayList<>();
    public ArrayList<TreeNodeStackData> treeNodeStack = new ArrayList<>();

    public ArrayList<Viewport> viewports = new ArrayList<>();

    public boolean navCursorVisible = false;
    public boolean navHighlightIgnoreMouse = false;
    public boolean navMousePositionDirty = false;
    public boolean navIDAlive = false;
    public int navID = 0;
    public Window navFocusedWindow = null;
    public int navFocusScopeID = 0;
    public int navActivateID = 0;
    public int navActivateDownID = 0;
    public int navActrivatePressedID = 0;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.ActivateFlags
     */
    public int navActivateFlags = 0;

    public ArrayList<FocusScopeData> navFocusRoute = new ArrayList<>();
    public int navHighlightActivatedID = 0;
    public float navHighlightActivatedTimer = 0.0f;
    public int navNextActivateID = 0;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.ActivateFlags
     */
    public int navNextActivateFlags = 0;

    public GuiInputSource navInputSource = GuiInputSource.NONE;
    public int navLastValidSelectionUserData = 0;
    public IkByte navCursorHideFrames = new IkByte();

    public boolean navAnyRequest = false;
    public boolean navInitRequest = false;
    public boolean navInitRequestFromMove = false;
    public NavItemData navInitResult = new NavItemData();
    public boolean navMoveSubmitted = false;
    public boolean navMoveScoringItems = false;
    public boolean navMoveForwardToNextFrame = false;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.NavMoveFlags
     */
    public int navMoveFlags = 0;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.ScrollFlags
     */
    public int navMoveScrollFlags = 0;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.KeyModFlags
     */
    public int navMoveKeyMods = 0;

    public Direction navMoveDirection = Direction.NONE;
    public Direction navMoveDirectionForDebug = Direction.NONE;
    public Rect navScoringRect = new Rect(0, 0, 0, 0);
    public Rect navScoringNoClipRect = new Rect(0, 0, 0, 0);
    public int navScoringDebugCount = 0;
    public int navTabbingDirection = 0;
    public int navTabbingCounter = 0;

    /** Best move request candidate within the nav window. */
    public NavItemData navMoveResultLocal = new NavItemData();

    /** Best move request candidate within the nav window that are mostly visible. */
    public NavItemData navMoveResultLocalVisible = new NavItemData();

    /** Best move request candidate within the nav window's flattened hierarchy. */
    public NavItemData navMoveResultOther = new NavItemData();

    /** First tabbing request candidate within the nav window and flattened hierarchy. */
    public NavItemData navTabbingResultFirst = new NavItemData();

    public int navJustMovedFromFocusScopeID = 0;
    public int navJustMovedToID = 0;
    public int navJustMovedToFocusScopeID = 0;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.KeyModFlags
     */
    public int navJustMovedToKeyMods = 0;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.NavMoveFlags
     */
    public int navJustMovedToFlags = 0;

    public NavItemData navJustMovedToItemData = new NavItemData();

    public boolean configNavWindowingWithGamepad = false;
    public Window navWindowingTarget = null;
    public Window navWindowingTargetPrev = null;
    public Window navWindowingListWindow = null;
    public float navWindowingTimer = 0.0f;
    public float navWindowingHighlightAlpha = 0.0f;
    public GuiInputSource navWindowingInputSource = GuiInputSource.NONE;
    public boolean navWindowingToggleLayer = false;
    public Vector2f navWindowingAccumulatedDeltaPosition = new Vector2f(0, 0);
    public Vector2f navWindowingAccumulatedDeltaSize = new Vector2f(0, 0);

    public float dimBackgroundRatio = 0.0f;

    public boolean dragDropActive = false;
    public boolean dragDropWithinSource = false;
    public boolean dragDropWithinTarget = false;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.DragDropFlags
     */
    public int dragDropSourceFlags = 0;

    public int dragDropSourceFrameCount = 0;
    public MouseButton dragDropMouseButton = MouseButton.NONE;
    public Object dragDropPayload = null;
    public Rect dragDropTargetRect = new Rect(0, 0, 0, 0);
    public Rect dragDropTargetClipRect = new Rect(0, 0, 0, 0);
    public int dragDropTargetID = 0;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.DragDropFlags
     */
    public int dragDropAcceptFlags = 0;

    public float dragDropAcceptIDCurrentRectSurface = 0.0f;
    public int dragDropAcceptIDCurrent = 0;
    public int dragDropAcceptIDPrev = 0;
    public int dragDropAcceptFrameCount = 0;
    public int dragDropHoldJustPressedID = 0;
    public ByteBuffer dragDropPayloadBufferLocal = null;

    public ArrayList<ListClipperData> clipperTempData = new ArrayList<>();

    public Table currentTable = null;
    public ArrayList<TableTempData> tablesTempData = new ArrayList<>();
    public ArrayList<Table> tables = new ArrayList<>();
    public FloatArrayList tablesLastTimeActive = new FloatArrayList();

    public TabBar currentTabBar = null;
    public ArrayList<TabBar> tabBars = new ArrayList<>();
    public IntArrayList currentTabBarStack = new IntArrayList();
    public ArrayList<ShrinkWidthItem> shrinkWidthBuffer = new ArrayList<>();

    public BoxSelectState boxSelectState = new BoxSelectState();
    public MultiSelectTempData currentMultiSelect = new MultiSelectTempData();
    public int multiSelectTempDataStackSize = 0;
    public ArrayList<MultiSelectTempData> multiSelectTempData = new ArrayList<>();
    public ArrayList<MultiSelectTempData> multiSelectStorage = new ArrayList<>();

    public int hoverItemDelayID = 0;
    public int hoverItemDelayIDPreviousFrame = 0;

    /** Used by isItemHovered(). */
    public float hoverItemDelayTimer = 0.0f;

    /** Used by isItemHovered(), time before tooltip hover time gets cleared. */
    public float hoverItemDelayClearTimer = 0.0f;

    /** ID of the item that a mouse is stationary over, reset when it leaves the item. */
    public int hoverItemUnlockedStationaryID = 0;

    /** ID of the window that a mouse is stationary over, reset when it leaves the window. */
    public int hoverWindowUnlockedStationaryID = 0;

    public MouseCursor mouseCursor = MouseCursor.ARROW;
    public float mouseStationaryTimer = 0.0f;
    public Vector2f mouseLastValidPosition = new Vector2f(0, 0);

    public InputTextState inputTextState = new InputTextState();
    public InputTextDeactivatedState inputTextDeactivatedState = new InputTextDeactivatedState();
    public int beginMenuDepth = 0;
    public int beginComboDepth = 0;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.ColorEditFlags
     */
    public int colorEditOptions = 0;

    public int colorEditCurrentID = 0;
    public int colorEditSavedID = 0;
    public int colorEditSavedHue = 0;
    public int colorEditSavedSaturation = 0;
    public int colorEditSavedColor = 0;
    public Vector4f colorPickerReference = new Vector4f(0, 0, 0, 0);

    public ComboPreviewData comboPreviewData = new ComboPreviewData();

    public Rect windowResizeBorderExpectedRect = new Rect(0, 0, 0, 0);
    public boolean windowResizeRelativeMode = false;

    /** 0 is scrolling to clicked location, +/- 1 is next/previous page. */
    public byte scrollbarSeekMode = 0;

    public float scrollbarClickDistanceToCenter = 0.0f;

    public float sliderGrabClickOffset = 0.0f;
    public float sliderCurrentAccumulatedDelta = 0.0f;
    public boolean sliderCurrentAccumulatedDeltaDirty = false;

    public float dragCurrentAccumulatedDelta = 0.0f;
    public boolean dragCurrentAccumulatedDeltaDirty = false;
    public float dragSpeedDefaultRatio = 0.0f;

    public float disabledAlphaBackup = 0.0f;
    public short disabledStackSize = 0;
    public short tooltipOverrideCount = 0;
    public Window tooltipPreviousWindow = null;
    public StringBuilder clipboardHandlerData = new StringBuilder();
    public IntArrayList menuIDsSubmittedThisFrame = new IntArrayList();

    public boolean settingsLoaded = false;
    public float settingsDirtyTimer = 0.0f;

    public boolean logEnabled = false;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.LogFlags
     */
    public int logFlags = 0;

    public Window logWindow = null;
    public int logDepthToExpand = 0;
    public int logDepthToExpandDefault = 0;

    public Object errorCallbackUserData = null;
    public Vector2f errorTooltipLockedPosition = new Vector2f(0, 0);
    public boolean errorFirst = false;
    public int errorCountCurrentFrame = 0;

    public int debugDrawIDConflictCount = 0;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.DebugLogFlags
     */
    public int debugLogFlags = 0;

    public StringBuilder debugLogBuffer = new StringBuilder();
    public int debugLogIndex = 0;
    public int debugLogSkippedErrors = 0;
    public int debugBreakKeyChord = 0;
    public IkByte debugBeginReturnValueCullDepth = new IkByte();
    public boolean debugItemPickerActive = false;

    public float[] framerateSecondPerFrame = new float[60];
    public int framerateSecondPerFrameIndex = 0;
    public int framerateSecondPerFrameCount = 0;
    public float framerateSecondPerFrameAccumulator = 0.0f;
    public int captureMouseNextFrameOverride = 0;
    public int captureKeyboardNextFrameOverride = 0;
    public int textInputNextFrameOverride = 0;
    public StringBuilder tempBuffer = new StringBuilder();
}
