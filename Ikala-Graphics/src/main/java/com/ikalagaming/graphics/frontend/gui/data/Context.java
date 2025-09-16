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
import java.util.Map;

public class Context {
    // TODO(ches) context
    private boolean initialized;
    private IkIO io;
    private PlatformIO platformIO;
    private Style style;

    private ArrayList<FontAtlas> fontAtlases;
    private Font font;
    private FontBaked fontBaked;
    private float fontSize;
    private float fontSizeBase;
    private float fontDensity;
    private float dpiScale;
    private DrawListSharedData drawListSharedData;
    private double time;
    private int frameCount;
    private int frameCountEnded;
    private int frameCountRendered;
    private boolean insideFrame;
    private ArrayList<GuiInputEvent> inputEventQueue;

    /**
     * Past input events that get processed when we start a new frame, mostly for mouse/pen trails.
     */
    private ArrayList<GuiInputEvent> inputEventTrail;

    /** Windows, sorted in display order, back to front. */
    private ArrayList<Window> windowsDisplayOrder;

    private ArrayList<Window> windowFocusOrder;
    private Map<Integer, Window> windowByID;
    private int windowActiveCount;
    private Window windowCurrent;
    private Window windowHovered;
    private Window windowHoveredUnderMovingWindow;
    private Window windowMoving;
    private Window windowWheeling;
    private Vector2f windowWheelingRefMousePosition;
    private int windowWheelingStartFrame;
    private int windowWheelingScrolledFrame;
    private float windowWheelingReleaseTimer;
    private Vector2f windowWheelingWheelRemainder;
    private Vector2f windowWheelingAxisAverage;

    private int hoveredID;
    private int hoveredIDPreviousFrame;
    private int hoveredIDPreviousFrameItemCount;
    private float hoveredIDTimer;
    private float hoveredIDInactiveTimer;
    private boolean hoveredIDAllowOverlap;
    private boolean hoveredIDDisabled;
    private int activeID;
    private int activeIDSeenThisFrame;
    private float activeIDTimer;
    private boolean activeIDActivatedThisFrame;
    private boolean activeIDAllowOverlap;
    private boolean activeIDRetainOnFocusLoss;
    private boolean activeIDHasBeenPressedBefore;
    private boolean activeIDHasBeenEditedBefore;
    private boolean activeIDHasBeenEditedThisFrame;
    private boolean activeIDFromShortcut;
    private MouseButton activeIDMouseButton;
    private Vector2f activeIDClickOffset;
    private Window activeIDWindow;
    private GuiInputSource activeIDSource;
    private int activeIDPreviousFrame;
    private DeactivatedItemData deactivatedItemData;
    private int lastActiveID;
    private float lastActiveIDTimer;

    private double lastKeyModsChangeTime;
    private double lastKeyModsChangeFromNoneTime;
    private double lastKeyboardKeyPressTime;

    private int currentFocusScopeID;
    private int currentItemFlags;
    private NextItemData nextItemData;
    private LastItemData lastItemData;
    private NextWindowData nextWindowData;

    private boolean debugShowGroupRects;
    private ArrayList<ColorMod> colorStack;
    private ArrayList<StyleMod> styleVariableStack;
    private ArrayList<FontStackInfo> fontStack;
    private ArrayList<FocusScopeData> focusScopeStack;
    private IntArrayList itemFlagsStack;
    private ArrayList<GroupData> groupStack;
    private ArrayList<PopupData> openPopupStack;
    private ArrayList<PopupData> beginPopupStack;
    private ArrayList<TreeNodeStackData> treeNodeStack;

    private ArrayList<Viewport> viewports;

    private boolean navCursorVisible;
    private boolean navHighlightIgnoreMouse;
    private boolean navMousePositionDirty;
    private boolean navIDAlive;
    private int navID;
    private Window navFocusedWindow;
    private int navFocusScopeID;
    private int navActivateID;
    private int navActivateDownID;
    private int navActrivatePressedID;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.ActivateFlags
     */
    private int navActivateFlags;

    private ArrayList<FocusScopeData> navFocusRoute;
    private int navHighlightActivatedID;
    private float navHighlightActivatedTimer;
    private int navNextActivateID;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.ActivateFlags
     */
    private int navNextActivateFlags;

    private GuiInputSource navInputSource;
    private int navLastValidSelectionUserData;
    private IkByte navCursorHideFrames;

    private boolean navAnyRequest;
    private boolean navInitRequest;
    private boolean navInitRequestFromMove;
    private NavItemData navInitResult;
    private boolean navMoveSubmitted;
    private boolean navMoveScoringItems;
    private boolean navMoveForwardToNextFrame;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.NavMoveFlags
     */
    private int navMoveFlags;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.ScrollFlags
     */
    private int navMoveScrollFlags;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.KeyModFlags
     */
    private int navMoveKeyMods;

    private Direction navMoveDirection;
    private Direction navMoveDirectionForDebug;
    private Rect navScoringRect;
    private Rect navScoringNoClipRect;
    private int navScoringDebugCount;
    private int navTabbingDirection;
    private int navTabbingCounter;

    /** Best move request candidate within the nav window. */
    private NavItemData navMoveResultLocal;

    /** Best move request candidate within the nav window that are mostly visible. */
    private NavItemData navMoveResultLocalVisible;

    /** Best move request candidate within the nav window's flattened hierarchy. */
    private NavItemData navMoveResultOther;

    /** First tabbing request candidate within the nav window and flattened hierarchy. */
    private NavItemData navTabbingResultFirst;

    private int navJustMovedFromFocusScopeID;
    private int navJustMovedToID;
    private int navJustMovedToFocusScopeID;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.KeyModFlags
     */
    private int navJustMovedToKeyMods;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.NavMoveFlags
     */
    private int navJustMovedToFlags;

    private NavItemData navJustMovedToItemData;

    private boolean configNavWindowingWithGamepad;
    private Window navWindowingTarget;
    private Window navWindowingTargetPrev;
    private Window navWindowingListWindow;
    private float navWindowingTimer;
    private float navWindowingHighlightAlpha;
    private GuiInputSource navWindowingInputSource;
    private boolean navWindowingToggleLayer;
    private Vector2f navWindowingAccumulatedDeltaPosition;
    private Vector2f navWindowingAccumulatedDeltaSize;

    private float dimBackgroundRatio;

    private boolean dragDropActive;
    private boolean dragDropWithinSource;
    private boolean dragDropWithinTarget;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.DragDropFlags
     */
    private int dragDropSourceFlags;

    private int dragDropSourceFrameCount;
    private MouseButton dragDropMouseButton;
    private Object dragDropPayload;
    private Rect dragDropTargetRect;
    private Rect dragDropTargetClipRect;
    private int dragDropTargetID;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.DragDropFlags
     */
    private int dragDropAcceptFlags;

    private float dragDropAcceptIDCurrentRectSurface;
    private int dragDropAcceptIDCurrent;
    private int dragDropAcceptIDPrev;
    private int dragDropAcceptFrameCount;
    private int dragDropHoldJustPressedID;
    private ByteBuffer dragDropPayloadBufferLocal;

    private ArrayList<ListClipperData> clipperTempData;

    private Table currentTable;
    private ArrayList<TableTempData> tablesTempData;
    private ArrayList<Table> tables;
    private FloatArrayList tablesLastTimeActive;

    private TabBar currentTabBar;
    private ArrayList<TabBar> tabBars;
    private IntArrayList currentTabBarStack;
    private ArrayList<ShrinkWidthItem> shrinkWidthBuffer;

    private BoxSelectState boxSelectState;
    private MultiSelectTempData currentMultiSelect;
    private int multiSelectTempDataStackSize;
    private ArrayList<MultiSelectTempData> multiSelectTempData;
    private ArrayList<MultiSelectTempData> multiSelectStorage;

    private int hoverItemDelayID;
    private int hoverItemDelayIDPreviousFrame;

    /** Used by isItemHovered(). */
    private float hoverItemDelayTimer;

    /** Used by isItemHovered(), time before tooltip hover time gets cleared. */
    private float hoverItemDelayClearTimer;

    /** ID of the item that a mouse is stationary over, reset when it leaves the item. */
    private int hoverItemUnlockedStationaryID;

    /** ID of the window that a mouse is stationary over, reset when it leaves the window. */
    private int hoverWindowUnlockedStationaryID;

    private MouseCursor mouseCursor;
    private float mouseStationaryTimer;
    private Vector2f mouseLastValidPosition;

    private InputTextState inputTextState;
    private InputTextDeactivatedState inputTextDeactivatedState;
    private int beginMenuDepth;
    private int beginComboDepth;

    /**
     * @see com.ikalagaming.graphics.frontend.gui.flags.ColorEditFlags
     */
    private int colorEditOptions;

    private int colorEditCurrentID;
    private int colorEditSavedID;
    private int colorEditSavedHue;
    private int colorEditSavedSaturation;
    private int colorEditSavedColor;
    private Vector4f colorPickerReference;

    private ComboPreviewData comboPreviewData;

    private Rect windowResizeBorderExpectedRect;
    private boolean windowResizeRelativeMode;

    /** 0 is scrolling to clicked location, +/- 1 is next/previous page. */
    private byte scrollbarSeekMode;

    private float scrollbarClickDistanceToCenter;

    private float sliderGrabClickOffset;
    private float sliderCurrentAccumulatedDelta;
    private boolean sliderCurrentAccumulatedDeltaDirty;

    private float dragCurrentAccumulatedDelta;
    private boolean dragCurrentAccumulatedDeltaDirty;
    private float dragSpeedDefaultRatio;

    private float disabledAlphaBackup;
    private short disabledStackSize;
    private short tooltipOverrideCount;
    private Window tooltipPreviousWindow;
    private StringBuilder clipboardHandlerData;
    private IntArrayList menuIDsSubmittedThisFrame;
}
