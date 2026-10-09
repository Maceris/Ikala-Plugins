package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.TestEngineHooks;
import com.ikalagaming.graphics.gui.callback.DockNodeWindowMenuHandler;
import com.ikalagaming.graphics.gui.enums.ColorType;
import com.ikalagaming.graphics.gui.enums.Direction;
import com.ikalagaming.graphics.gui.enums.GuiInputSource;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.enums.MouseCursor;
import com.ikalagaming.graphics.gui.event.GuiInputEvent;
import com.ikalagaming.graphics.gui.flags.DebugLogFlags;
import com.ikalagaming.graphics.gui.flags.ViewportFlags;
import com.ikalagaming.graphics.gui.util.KeyChord;
import com.ikalagaming.graphics.gui.util.RectFloat;
import com.ikalagaming.util.FloatArrayList;
import com.ikalagaming.util.IntArrayList;

import org.joml.Vector2f;

import java.util.*;
import java.util.function.Consumer;

public class Context {
    /** The currently active ID, which may be 0 if there are no windows open. */
    public int activeID;

    public boolean activeIDActivatedThisFrame;
    public boolean activeIDAllowOverlap;
    public final Vector2f activeIDClickOffset;
    public int activeIDDisabledId;
    public boolean activeIDFromShortcut;
    public boolean activeIDHasBeenEditedBefore;
    public boolean activeIDHasBeenEditedThisFrame;
    public boolean activeIDHasBeenPressedBefore;

    /** Set to the active ID when the active item has been seen this frame. */
    public int activeIDIsAlive;

    public boolean activeIDIsJustActivated;
    public MouseButton activeIDMouseButton;
    public boolean activeIDNoClearOnFocusLost;
    public int activeIDPreviousFrame;
    public boolean activeIDRetainOnFocusLoss;
    public boolean activeIDSeenThisFrame;
    public GuiInputSource activeIDSource;
    public long activeIDTimer;
    public Window activeIDWindow;

    /** Set when any item has been edited this frame. */
    public boolean anyIDHasBeenEditedThisFrame;

    public int beginComboDepth;

    public int beginMenuDepth;

    /**
     * The popups we are currently inside of (between beginPopup() and endPopup()), bottom of the
     * stack is at index 0. Each entry matches the open popup stack entry at the same index.
     */
    public final List<PopupData> beginPopupStack;

    /** The box-select state, only one box-select is active at a time. */
    public final BoxSelectState boxSelectState;

    public int captureKeyboardNextFrameOverride;

    public int captureMouseNextFrameOverride;

    public StringBuilder clipboardHandlerData;

    /** Temporary data for list clippers, one per level of nested clippers. */
    public final List<ListClipperData> clipperTempData;

    /** The number of list clippers currently in use. */
    public int clipperTempDataStacked;

    /** Set temporarily while inside of the parent-most colorEdit4()/colorPicker4() call. */
    public int colorEditCurrentID;

    /** The color (RGB with no alpha) the saved hue/saturation are for. */
    public int colorEditSavedColor;

    /** Backup of the last hue associated with colorEditSavedColor, so we can restore it. */
    public float colorEditSavedHue;

    /** The ID of the last edited color edit, used to restore the hue/saturation. */
    public int colorEditSavedID;

    /** Backup of the last saturation associated with colorEditSavedColor, to restore it. */
    public float colorEditSavedSaturation;

    /** The initial color when opening a color picker popup, as RGBA. */
    public final float[] colorPickerReference;

    public final Deque<ColorMod> colorStack;
    public ComboPreviewData comboPreviewData;
    public boolean configNavWindowingWithGamepad;
    public int currentFocusScopeID;
    public int currentItemFlags;

    /** The multi-select scope being submitted, or null. */
    public MultiSelectTempData currentMultiSelect;

    public TabBar currentTabBar;

    /** The stack of tab bars we are currently inside, the last entry is the current one. */
    public final List<TabBar> currentTabBarStack;

    public Table currentTable;
    public DeactivatedItemData deactivatedItemData;
    public IkByte debugBeginReturnValueCullDepth;

    /** The key chord that activates debug break buttons, Pause by default. */
    public int debugBreakKeyChord;

    public int debugDrawIDConflictCount;

    /** The item picker tool is active, see debugStartItemPicker(). */
    public final IkBoolean debugItemPickerActive;

    /** Which mouse button picks an item with the item picker. */
    public MouseButton debugItemPickerMouseButton;

    /** The item picker will call debugBreak() when encountering this ID, or 0 for none. */
    public int debugItemPickerBreakID;

    /** The lines of the debug log, which can be added to from any thread. */
    public final DebugLogBuffer debugLogBuffer;

    /** Event flags to turn off when {@link #debugLogAutoDisableFrames} reaches 0. */
    public int debugLogAutoDisableFlags;

    /** Frames until {@link #debugLogAutoDisableFlags} are turned off, or 0 when not counting. */
    public int debugLogAutoDisableFrames;

    /** Draw a rectangle around the item with this ID when it is submitted, or 0 for none. */
    public int debugLocateID;

    /** Frames until we stop looking for {@link #debugLocateID}. */
    public int debugLocateFrames;

    /** Call debugBreak() when submitting the item with the {@link #debugLocateID}. */
    public boolean debugBreakInLocateID;

    /** Call debugBreak() in begin() for the window with this ID, or 0 for none. */
    public int debugBreakInWindow;

    /** Call debugBreak() in beginTable() for the table with this ID, or 0 for none. */
    public int debugBreakInTable;

    /** Call debugBreak() when routing this key chord, or 0 for none. */
    public int debugBreakInShortcutRouting;

    /**
     * The ID functions call the ID stack tool hook when they produce this ID, or 0 for none. Set to
     * the next ID the tool is looking for.
     */
    public int debugHookIDInfoID;

    /** The current query of the ID stack tool. */
    public final DebugItemPathQuery debugItemPathQuery;

    /** The test engine to report items to, or null for none. */
    public TestEngineHooks testEngine;

    /** Whether to call the test engine hooks for items. */
    public boolean testEngineHookItems;

    /** State for the ID stack tool window. */
    public final IDStackTool debugIDStackTool;

    /** Options for the metrics window. */
    public final MetricsConfig debugMetricsConfig;

    /** The style color that is flashing, or null for none. */
    public ColorType debugFlashStyleColor;

    /** The current color of the flashing style color, which overrides the style. */
    public int debugFlashStyleColorValue;

    /** How long the style color will keep flashing, in milliseconds. */
    public long debugFlashStyleColorTime;

    /**
     * Which events are recorded in the debug log, volatile since errors can be reported from any
     * thread. Only the GUI thread writes this, so read-modify-write updates there are safe.
     *
     * @see com.ikalagaming.graphics.gui.flags.DebugLogFlags
     */
    public volatile int debugLogFlags;

    /**
     * The thread that last started a frame, which is the one allowed to use the GUI. Errors
     * reported from other threads mention their thread instead of the current window. Volatile
     * because the reference is only ever replaced as a whole, never mutated.
     */
    @SuppressWarnings("java:S3077")
    public volatile Thread guiThread;

    public boolean debugShowGroupRects;
    public float dimBackgroundRatio;

    /** Draw list used to dim the background behind modal windows. */
    public DrawList dimBackgroundDrawList;

    public float disabledAlphaBackup;
    public short disabledStackSize;

    public DockContext dockContext;

    /** Displays the window menu of dock nodes, which can be replaced to customize the menu. */
    public DockNodeWindowMenuHandler dockNodeWindowMenuHandler;

    /** [DEBUG] The dock node under the mouse. Not used for actual docking. */
    public DockNode debugHoveredDockNode;

    /** The number of font points in one inch. */
    public int dpiScaleFont;

    /** The number of pixels in 1 inch. */
    public int dpiScaleScreen;

    public float dragCurrentAccumulatedDelta;
    public boolean dragCurrentAccumulatedDeltaDirty;

    /**
     * Flags passed to acceptDragDropPayload() this frame.
     *
     * @see com.ikalagaming.graphics.gui.flags.DragDropFlags
     */
    public int dragDropAcceptFlagsCurrent;

    /**
     * Flags passed to acceptDragDropPayload() last frame.
     *
     * @see com.ikalagaming.graphics.gui.flags.DragDropFlags
     */
    public int dragDropAcceptFlagsPrev;

    /** Last time a target expressed a desire to accept the source. */
    public int dragDropAcceptFrameCount;

    /** Target item ID, set at the time of accepting the payload. */
    public int dragDropAcceptIDCurrent;

    /**
     * Target item surface area, we resolve overlapping targets by prioritizing the smaller surface.
     */
    public float dragDropAcceptIDCurrentRectSurface;

    /**
     * Target item ID from the previous frame, stored to allow for overlapping drag and drop
     * targets.
     */
    public int dragDropAcceptIDPrev;

    public boolean dragDropActive;

    /** Set when holding a payload just made buttonBehavior() return a press. */
    public int dragDropHoldJustPressedID;

    /** The mouse button carrying the drag, NONE for external sources with no button held. */
    public MouseButton dragDropMouseButton;

    public final Payload dragDropPayload;

    /**
     * @see com.ikalagaming.graphics.gui.flags.DragDropFlags
     */
    public int dragDropSourceFlags;

    public int dragDropSourceFrameCount;

    /** The clip rectangle at the time the current target candidate was drawn. */
    public final RectFloat dragDropTargetClipRect;

    /** The viewport ID when the current target candidate is a full viewport, otherwise 0. */
    public int dragDropTargetFullViewport;

    public int dragDropTargetID;

    /** The rectangle of the current target candidate, we favor small targets when overlapping. */
    public final RectFloat dragDropTargetRect;

    /** Set when within a beginDragDropSource()/endDragDropSource() block. */
    public boolean dragDropWithinSource;

    /** Set when within a beginDragDropTarget()/endDragDropTarget() block. */
    public boolean dragDropWithinTarget;

    public float dragSpeedDefaultRatio;

    /**
     * The value of the active drag/slider when it was activated, so the change can be canceled. A
     * Long for integer types and a Double for floating point types, so it's exact.
     */
    public Number activeIDValueOnActivation;

    /**
     * The textures used by draw lists this frame, shared between the draw data of all viewports.
     */
    public final DrawTextures drawTextures;

    /**
     * Called with the message for each error about incorrect API usage, after it was logged. For
     * advanced uses, like showing errors in your own UI.
     */
    public Consumer<String> errorCallback;

    /** The number of errors shown in the error tooltip this frame. */
    public int errorCountCurrentFrame;

    /** Whether no error has been reported yet, so the first one also logs the settings. */
    public boolean errorFirst;

    /** The position of the error tooltip, which is locked while Ctrl is held. */
    public final Vector2f errorTooltipLockedPosition;

    /** Set when multiple items with the same ID are found, the ID of those items. */
    public int debugDrawIdConflictsID;

    /** The number of items with conflicting IDs, which is locked while Ctrl is held. */
    public int debugDrawIdConflictsCount;

    public final List<FocusScopeData> focusScopeStack;

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

    /**
     * The time, in milliseconds, when the that the last frame started on as far as we are
     * concerned. Used for calculating delta time.
     */
    public long frameStartTime;

    /**
     * Where the frame start times come from, in milliseconds. The system clock by default, tests
     * may replace it to control time exactly.
     */
    public java.util.function.LongSupplier timeSource = System::currentTimeMillis;

    public float[] framerateSecondPerFrame;

    public float framerateSecondPerFrameAccumulator;
    public int framerateSecondPerFrameCount;
    public int framerateSecondPerFrameIndex;
    public final Deque<GroupData> groupStack;

    public int hoveredID;

    public boolean hoveredIDAllowOverlap;
    public boolean hoveredIDDisabled;
    public long hoveredIDInactiveTimer;

    public int hoveredIDPreviousFrame;
    public int hoveredIDPreviousFrameItemCount;
    public long hoveredIDTimer;

    /** Used by isItemHovered(), time before tooltip hover time gets cleared. */
    public long hoverItemDelayClearTimer;

    public int hoverItemDelayID;
    public int hoverItemDelayIDPreviousFrame;

    /** Used by isItemHovered(). */
    public long hoverItemDelayTimer;

    /** ID of the item that a mouse is stationary over, reset when it leaves the item. */
    public int hoverItemUnlockedStationaryID;

    /** ID of the window that a mouse is stationary over, reset when it leaves the window. */
    public int hoverWindowUnlockedStationaryID;

    public final IntArrayList idStack;

    public boolean initialized;

    public final List<GuiInputEvent> inputEventQueue;

    /**
     * Past input events that get processed when we start a new frame, mostly for mouse/pen trails.
     */
    public final List<GuiInputEvent> inputEventTrail;

    public InputTextDeactivatedState inputTextDeactivatedState;
    public InputTextState inputTextState;

    /** State for getTypingSelectRequest(). */
    public final TypingSelectState typingSelectState;

    /**
     * ID of the input text to reactivate on the next frame (for configInputTextEnterKeepActive).
     */
    public int inputTextReactivateID;

    /** Temporary text input when using Ctrl+Click on a slider, etc. */
    public int tempInputID;

    /** The layer navigation is focused on, 0 for the main layer and 1 for the menu layer. */
    public int navLayer;

    /**
     * The item flags of the navigation item.
     *
     * @see com.ikalagaming.graphics.gui.flags.ItemFlags
     */
    public int navIDItemFlags;

    /** The direction used to clip results for the current move request. */
    public Direction navMoveClipDirection;

    /** Whether the last navigation move was from tabbing. */
    public boolean navJustMovedToIsTabbing;

    /** Whether the last navigation move was from an init request. */
    public boolean navJustMovedToIsInit;

    /** Whether the last navigation move landed on an item with selection user data. */
    public boolean navJustMovedToHasSelectionData;

    /** Enable tabbing (Tab, Shift+Tab). Enabled by default, regardless of keyboard navigation. */
    public boolean configNavEnableTabbing;

    /** The key chord to focus the next window (Ctrl+Tab by default). 0 to disable. */
    public int configNavWindowingKeyNext;

    /** The key chord to focus the previous window (Ctrl+Shift+Tab by default). 0 to disable. */
    public int configNavWindowingKeyPrevious;

    /** The window being animated for Ctrl+Tab, which may be fading out. */
    public Window navWindowingTargetAnim;

    /** The key that toggles the menu layer (Alt). */
    public Key navWindowingToggleKey;

    /**
     * Override for io.wantCaptureKeyboard on the next frame: -1 for no override, 0 for false, 1 for
     * true.
     */
    public int wantCaptureKeyboardNextFrame;

    /**
     * Override for io.wantCaptureMouse on the next frame: -1 for no override, 0 for false, 1 for
     * true.
     */
    public int wantCaptureMouseNextFrame;

    /** io.wantTextInput for the next frame: -1 for no override, 0 for false, 1 for true. */
    public int wantTextInputNextFrame;

    /** IME data for the current frame, filled in by input text widgets. */
    public final PlatformImeData platformImeData;

    /** IME data for the previous frame, to detect changes. */
    public final PlatformImeData platformImeDataPrevious;

    /** The owner of each key, indexed by Key.ordinal(). */
    public final KeyOwnerData[] keysOwnerData;

    /** Shortcut routing for each key, indexed by Key.ordinal(). */
    public final List<KeyRoutingData>[] keysRoutingTable;

    /** Which keys may produce character input when pressed without modifiers. */
    public final boolean[] keysMayBeCharInput;

    /**
     * The navigation directions the active item is using, as a bit mask of {@code 1 <<
     * direction.ordinal()}. Navigation won't use those directions while the item is active.
     */
    public int activeIDUsingNavDirMask;

    /** The active item is using all keyboard keys, so other code should not read them. */
    public boolean activeIDUsingAllKeyboardKeys;

    /** Value replacement when displaying a mixed value. Set to null to display the real value. */
    public String mixedValueLabel;

    public boolean insideFrame;
    public IkIO io;
    public final IntArrayList itemFlagsStack;

    public int lastActiveID;

    public long lastActiveIDTimer;

    /** Whether the active item was selected at the time it was activated. */
    public boolean activeIDWasSelected;

    /**
     * Whether the active item was the only selected item at the time it was activated. Also true if
     * the selection size is unknown.
     */
    public boolean activeIDWasSoleSelected;

    /**
     * Whether the last active item was selected at the time it was activated. Useful along with
     * getItemClickedCountWithSingleClickDelay(), to check the selection at the time of the click.
     */
    public boolean lastActiveIDWasSelected;

    /** Whether the last active item was the only selected item at the time it was activated. */
    public boolean lastActiveIDWasSoleSelected;

    public LastItemData lastItemData;

    public double lastKeyboardKeyPressTime;

    public double lastKeyModsChangeFromNoneTime;
    public double lastKeyModsChangeTime;

    /** The key modifiers on the previous frame, to detect changes. */
    public int lastKeyModsFrame;

    /** The frame a keyboard key was last pressed on. */
    public int lastKeyboardKeyPressFrame;

    /** The text captured while logging, before it is written to the output. */
    public final StringBuilder logBuffer;

    /** The tree depth when logging started, used to indent logged text. */
    public int logDepthRef;

    /** Tree nodes are automatically opened up to this depth while logging. */
    public int logDepthToExpand;

    /** The default for logDepthToExpand, when an explicit depth is not passed in. */
    public int logDepthToExpandDefault;

    public boolean logEnabled;

    /**
     * @see com.ikalagaming.graphics.gui.flags.LogFlags
     */
    public int logFlags;

    /** Whether the next logged item is the first on its line, so it should be indented. */
    public boolean logLineFirstItem;

    /** The y position of the last logged text, used to decide when to insert new lines. */
    public float logLinePosY;

    /** Text logged before the next rendered text, e.g. "[" for buttons. May be null. */
    public String logNextPrefix;

    /** Text logged after the next rendered text, e.g. "]" for buttons. May be null. */
    public String logNextSuffix;

    /** Where to write logged text for TTY and file logging, null when buffering. */
    public java.io.Writer logOutput;

    public Window logWindow;

    /** Disable item clipping while logging, so all items are captured. */
    public boolean itemUnclipByLog;

    /** The main viewport, which is always the first entry in {@link #viewports}. */
    public Viewport mainViewport;

    public final IntArrayList menuIDsSubmittedThisFrame;
    public MouseCursor mouseCursor;
    public final Vector2f mouseLastValidPosition;

    /** Persistent multi-select states, by ID. */
    public final Map<Integer, MultiSelectState> multiSelectStorage;

    /**
     * Temporary multi-select data for nested scopes. Entries are reused, so use {@link
     * #multiSelectTempDataStackSize} instead of the list size.
     */
    public final List<MultiSelectTempData> multiSelectTempData;

    /** How many multi-select scopes are currently nested. */
    public int multiSelectTempDataStackSize;

    public int navActivateDownID;

    /**
     * @see com.ikalagaming.graphics.gui.flags.ActivateFlags
     */
    public int navActivateFlags;

    public int navActivateID;
    public int navActivatePressedID;
    public boolean navAnyRequest;

    public int navCursorHideFrames;

    public boolean navCursorVisible;
    public Window navFocusedWindow;
    public final List<FocusScopeData> navFocusRoute;
    public int navFocusScopeID;
    public int navHighlightActivatedID;
    public long navHighlightActivatedTimer;

    /**
     * Disable mouse hovering highlight. Highlight the navigation focused item instead of the mouse
     * hovered item.
     */
    public boolean navHighlightItemUnderNav;

    public int navID;
    public boolean navIDAlive;
    public boolean navInitRequest;
    public boolean navInitRequestFromMove;
    public NavItemData navInitResult;
    public GuiInputSource navInputSource;

    public int navJustMovedFromFocusScopeID;

    /**
     * @see com.ikalagaming.graphics.gui.flags.NavMoveFlags
     */
    public int navJustMovedToFlags;

    public int navJustMovedToFocusScopeID;
    public int navJustMovedToID;
    public NavItemData navJustMovedToItemData;

    /**
     * @see com.ikalagaming.graphics.gui.flags.KeyModFlags
     */
    public int navJustMovedToKeyMods;

    public long navLastValidSelectionUserData;
    public boolean navMousePositionDirty;
    public Direction navMoveDirection;

    public Direction navMoveDirectionForDebug;

    /**
     * @see com.ikalagaming.graphics.gui.flags.NavMoveFlags
     */
    public int navMoveFlags;

    public boolean navMoveForwardToNextFrame;

    /**
     * @see com.ikalagaming.graphics.gui.flags.KeyModFlags
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
     * @see com.ikalagaming.graphics.gui.flags.ScrollFlags
     */
    public int navMoveScrollFlags;

    public boolean navMoveSubmitted;

    /**
     * @see com.ikalagaming.graphics.gui.flags.ActivateFlags
     */
    public int navNextActivateFlags;

    public int navNextActivateID;

    /** The item that navigation has requested a context menu (popup) for. */
    public int navOpenContextMenuItemID;

    /** The window containing the item that navigation requested a context menu (popup) for. */
    public int navOpenContextMenuWindowID;

    public int navScoringDebugCount;
    public final RectFloat navScoringNoClipRect;

    public final RectFloat navScoringRect;
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

    public long navWindowingTimer;

    public boolean navWindowingToggleLayer;
    public NextItemData nextItemData;

    public NextWindowData nextWindowData;

    /**
     * Which popups are open, bottom of the stack is at index 0. There is one open popup per level
     * of the popup hierarchy.
     */
    public final List<PopupData> openPopupStack;

    public PlatformIO platformIO;
    public float scrollbarClickDistanceToCenter;

    /** 0 is scrolling to clicked location, +/- 1 is next/previous page. */
    public byte scrollbarSeekMode;

    /** The current session date as YYYYMMDD, copied from the platform IO each frame. */
    public int sessionDate;

    /** Time until settings are saved, in milliseconds, or 0 if they are not dirty. */
    public long settingsDirtyTimer;

    /** Handlers for each type of entry in the .ini file. */
    public final List<SettingsHandler> settingsHandlers;

    /** The last .ini data that was loaded or saved. */
    public final StringBuilder settingsIniData;

    public boolean settingsLoaded;
    public final List<WindowSettings> settingsWindows;
    public final List<ShrinkWidthItem> shrinkWidthBuffer;

    public float sliderCurrentAccumulatedDelta;
    public boolean sliderCurrentAccumulatedDeltaDirty;
    public float sliderGrabClickOffset;
    public ErrorRecoveryState stackSizesInBegin;
    public ErrorRecoveryState stackSizesInNewFrame;
    public final Style style;
    public final Deque<StyleMod> styleVariableStack;

    /** Tab bars that are not part of a dock node, by ID. */
    public final Map<Integer, TabBar> tabBars;

    /** All tables, indexed by their table index. */
    public final List<Table> tables;

    /** Tables by ID. */
    public final Map<Integer, Table> tablesByID;

    /** Settings for tables. */
    public final List<TableSettings> settingsTables;

    /** The number of tables currently being submitted (nested). */
    public int tablesTempDataStacked;

    public FloatArrayList tablesLastTimeActive;

    public final List<TableTempData> tablesTempData;

    public StringBuilder tempBuffer;

    public int textInputNextFrameOverride;

    /** Total time elapsed since the context was initialized, in milliseconds. */
    public long time;

    public short tooltipOverrideCount;

    public Window tooltipPreviousWindow;
    public final Deque<TreeNodeStackData> treeNodeStack;

    /**
     * All viewports, starting with the main viewport. Some of these may not be visible, see {@link
     * PlatformIO#viewports} for the list of viewports to render.
     */
    public final List<Viewport> viewports;

    /** The viewport we are currently outputting into, from the current window. May be null. */
    public Viewport currentViewport;

    /**
     * The viewport that the mouse is interacting with this frame. Windows in other viewports are
     * not hovered.
     */
    public Viewport mouseViewport;

    /**
     * The last viewport that was hovered by the mouse, even if the mouse is not currently hovering
     * any viewport. May be null.
     */
    public Viewport mouseLastHoveredViewport;

    /** The ID of the viewport that the platform reported as focused last time we checked. */
    public int platformLastFocusedViewportID;

    /**
     * A copy of the first monitor, or a fake monitor covering the main viewport when there are no
     * monitors. Used when a viewport isn't on a known monitor.
     */
    public final PlatformMonitor fallbackMonitor;

    /**
     * The bounding box of the work area of all monitors. Used to let windows straddle monitors
     * while being moved.
     */
    public final RectFloat platformMonitorsFullWorkRect;

    /** The number of viewports that have been created, for debugging. */
    public int viewportCreatedCount;

    /** The number of platform windows that have been created, for debugging. */
    public int platformWindowsCreatedCount;

    /**
     * Incremented every time a viewport is focused. Viewports store this when they are focused, so
     * we can infer the z-order of platform windows.
     */
    public int viewportFocusedStampCount;

    /** The DPI scale of the current viewport. */
    public float currentDpiScale;

    /**
     * The configuration flags for the current frame, copied from the IO at the start of a frame.
     */
    public int configFlagsCurrentFrame;

    /** The configuration flags from the previous frame. */
    public int configFlagsLastFrame;

    /** The last frame count where updatePlatformWindows() was called. */
    public int frameCountPlatformEnded;

    public int windowActiveCount;

    /**
     * Extra space around the border of a window that counts as still hovering over the window, to
     * make resizing easier. Calculated based on the style variables for touch padding and border
     * hover padding.
     */
    public float windowBorderHoverPadding;

    public final Map<Integer, Window> windowByID;

    public Window windowCurrent;

    /**
     * All windows, sorted in display order, back to front. Child windows are always after their
     * parent.
     */
    public final List<Window> windowDisplayOrder;

    /** Root windows, sorted in focus order, back to front. */
    public final List<Window> windowFocusOrder;

    public Window windowHovered;
    public Window windowHoveredBeforeClear;
    public Window windowHoveredUnderMovingWindow;
    public Window windowMoving;
    public final RectFloat windowResizeBorderExpectedRect;
    public boolean windowResizeRelativeMode;

    public final List<WindowStackData> windowStack;

    public Window windowWheeling;

    /** The ID of the child window that we are ending, used for error checking. */
    public int withinEndChildID;

    /** The ID of the popup window that we are ending, used for error checking. */
    public int withinEndPopupID;

    public final Vector2f windowWheelingAxisAverage;
    public final Vector2f windowWheelingRefMousePosition;
    public long windowWheelingReleaseTimer;
    public int windowWheelingScrolledFrame;
    public int windowWheelingStartFrame;
    public final Vector2f windowWheelingWheelRemainder;

    /** Set by {@link IkGui#newFrame()}, cleared by {@link IkGui#endFrame()}. */
    public boolean withinFrameScope;

    /**
     * Set by {@link IkGui#newFrame()}, cleared by {@link IkGui#endFrame()} when the implicit debug
     * window has been pushed.
     */
    public boolean withinFrameScopeWithImplicitWindow;

    public Context() {
        activeID = 0;
        activeIDActivatedThisFrame = false;
        activeIDAllowOverlap = false;
        activeIDClickOffset = new Vector2f(0, 0);
        activeIDDisabledId = 0;
        activeIDFromShortcut = false;
        activeIDHasBeenEditedBefore = false;
        activeIDHasBeenEditedThisFrame = false;
        activeIDHasBeenPressedBefore = false;
        activeIDIsAlive = 0;
        activeIDIsJustActivated = false;
        activeIDMouseButton = MouseButton.NONE;
        activeIDNoClearOnFocusLost = false;
        activeIDPreviousFrame = 0;
        activeIDRetainOnFocusLoss = false;
        activeIDSeenThisFrame = false;
        activeIDSource = GuiInputSource.NONE;
        activeIDTimer = 0;
        activeIDWindow = null;
        anyIDHasBeenEditedThisFrame = false;
        beginComboDepth = 0;
        beginMenuDepth = 0;
        beginPopupStack = new ArrayList<>();
        boxSelectState = new BoxSelectState();
        captureKeyboardNextFrameOverride = 0;
        captureMouseNextFrameOverride = 0;
        clipboardHandlerData = new StringBuilder();
        clipperTempData = new ArrayList<>();
        clipperTempDataStacked = 0;
        colorEditCurrentID = 0;
        colorEditSavedColor = 0;
        colorEditSavedHue = 0.0f;
        colorEditSavedID = 0;
        colorEditSavedSaturation = 0.0f;
        colorPickerReference = new float[4];
        colorStack = new ArrayDeque<>();
        comboPreviewData = new ComboPreviewData();
        configNavWindowingWithGamepad = false;
        currentFocusScopeID = 0;
        currentItemFlags = 0;
        currentMultiSelect = null;
        currentTabBar = null;
        currentTabBarStack = new ArrayList<>();
        currentTable = null;
        deactivatedItemData = new DeactivatedItemData();
        debugBeginReturnValueCullDepth = new IkByte();
        debugBreakKeyChord = KeyChord.of(Key.PAUSE);
        debugDrawIDConflictCount = 0;
        debugItemPickerActive = new IkBoolean(false);
        debugItemPickerMouseButton = MouseButton.LEFT;
        debugItemPickerBreakID = 0;
        debugLogBuffer = new DebugLogBuffer();
        debugLogFlags = DebugLogFlags.EVENT_ERROR | DebugLogFlags.OUTPUT_TO_LOGGER;
        debugLogAutoDisableFlags = DebugLogFlags.NONE;
        debugLogAutoDisableFrames = 0;
        debugLocateID = 0;
        debugLocateFrames = 0;
        debugBreakInLocateID = false;
        debugBreakInWindow = 0;
        debugBreakInTable = 0;
        debugBreakInShortcutRouting = 0;
        debugHookIDInfoID = 0;
        debugItemPathQuery = new DebugItemPathQuery();
        testEngine = null;
        testEngineHookItems = false;
        debugIDStackTool = new IDStackTool();
        debugMetricsConfig = new MetricsConfig();
        debugFlashStyleColor = null;
        debugFlashStyleColorValue = 0;
        debugFlashStyleColorTime = 0;
        guiThread = null;
        debugShowGroupRects = false;
        dimBackgroundRatio = 0.0f;
        dimBackgroundDrawList = new DrawList("DimBackground");
        disabledAlphaBackup = 0.0f;
        disabledStackSize = 0;
        dockContext = new DockContext();
        dockNodeWindowMenuHandler = null;
        debugHoveredDockNode = null;
        dpiScaleFont = 72;
        dpiScaleScreen = 96;
        dragCurrentAccumulatedDelta = 0.0f;
        dragCurrentAccumulatedDeltaDirty = false;
        dragDropAcceptFlagsCurrent = 0;
        dragDropAcceptFlagsPrev = 0;
        dragDropAcceptFrameCount = -1;
        dragDropAcceptIDCurrent = 0;
        dragDropAcceptIDCurrentRectSurface = 0.0f;
        dragDropAcceptIDPrev = 0;
        dragDropActive = false;
        dragDropHoldJustPressedID = 0;
        dragDropMouseButton = MouseButton.NONE;
        dragDropPayload = new Payload();
        dragDropSourceFlags = 0;
        dragDropSourceFrameCount = -1;
        dragDropTargetClipRect = new RectFloat(0, 0, 0, 0);
        dragDropTargetFullViewport = 0;
        dragDropTargetID = 0;
        dragDropTargetRect = new RectFloat(0, 0, 0, 0);
        dragDropWithinSource = false;
        dragDropWithinTarget = false;
        dragSpeedDefaultRatio = 1.0f / 100.0f;
        drawTextures = new DrawTextures();
        errorCallback = null;
        errorCountCurrentFrame = 0;
        errorFirst = true;
        debugDrawIdConflictsID = 0;
        debugDrawIdConflictsCount = 0;
        errorTooltipLockedPosition = new Vector2f(0, 0);
        focusScopeStack = new ArrayList<>();
        font = null;
        fontFallbacks = new LinkedList<>();
        fontFallbackChar = '?';
        fontSize = 12;
        fontStack = new ArrayDeque<>();
        frameCount = 0;
        frameCountEnded = 0;
        frameCountRendered = 0;
        frameStartTime = 0;
        framerateSecondPerFrame = new float[60];
        framerateSecondPerFrameAccumulator = 0.0f;
        framerateSecondPerFrameCount = 0;
        framerateSecondPerFrameIndex = 0;
        groupStack = new ArrayDeque<>();
        hoveredID = 0;
        hoveredIDAllowOverlap = false;
        hoveredIDDisabled = false;
        hoveredIDInactiveTimer = 0;
        hoveredIDPreviousFrame = 0;
        hoveredIDPreviousFrameItemCount = 0;
        hoveredIDTimer = 0;
        hoverItemDelayClearTimer = 0;
        hoverItemDelayID = 0;
        hoverItemDelayIDPreviousFrame = 0;
        hoverItemDelayTimer = 0;
        hoverItemUnlockedStationaryID = 0;
        hoverWindowUnlockedStationaryID = 0;
        idStack = new IntArrayList();
        initialized = false;
        inputEventQueue = new ArrayList<>();
        inputEventTrail = new ArrayList<>();
        inputTextDeactivatedState = new InputTextDeactivatedState();
        inputTextState = new InputTextState();
        typingSelectState = new TypingSelectState();
        inputTextReactivateID = 0;
        tempInputID = 0;
        navLayer = 0;
        navIDItemFlags = 0;
        navMoveClipDirection = Direction.NONE;
        navJustMovedToIsTabbing = false;
        navJustMovedToIsInit = false;
        navJustMovedToHasSelectionData = false;
        configNavEnableTabbing = true;
        configNavWindowingKeyNext =
                com.ikalagaming.graphics.gui.util.KeyChord.of(
                        com.ikalagaming.graphics.gui.flags.KeyModFlags.CTRL, Key.TAB);
        configNavWindowingKeyPrevious =
                com.ikalagaming.graphics.gui.util.KeyChord.of(
                        com.ikalagaming.graphics.gui.flags.KeyModFlags.CTRL
                                | com.ikalagaming.graphics.gui.flags.KeyModFlags.SHIFT,
                        Key.TAB);
        navWindowingTargetAnim = null;
        navWindowingToggleKey = Key.NONE;
        wantCaptureKeyboardNextFrame = -1;
        wantCaptureMouseNextFrame = -1;
        wantTextInputNextFrame = -1;
        platformImeData = new PlatformImeData();
        platformImeDataPrevious = new PlatformImeData();
        final Key[] keys = Key.values();
        keysOwnerData = new KeyOwnerData[keys.length];
        keysMayBeCharInput = new boolean[keys.length];
        @SuppressWarnings("unchecked")
        final List<KeyRoutingData>[] routingTable = new List[keys.length];
        keysRoutingTable = routingTable;
        for (Key key : keys) {
            keysOwnerData[key.ordinal()] = new KeyOwnerData();
            keysRoutingTable[key.ordinal()] = new ArrayList<>();
            // Keys that may produce characters when typed (A-Z, 0-9, symbols, space)
            keysMayBeCharInput[key.ordinal()] =
                    key.isKeyboardKey()
                            && (key.ordinal() < Key.ARROW_DOWN.ordinal() || key == Key.SPACE)
                            && key != Key.NUM_LOCK
                            && key != Key.NUMPAD_ENTER;
        }
        activeIDUsingNavDirMask = 0;
        activeIDUsingAllKeyboardKeys = false;
        mixedValueLabel = "-";
        insideFrame = false;
        io = new IkIO();
        itemFlagsStack = new IntArrayList();
        lastActiveID = 0;
        lastActiveIDTimer = 0;
        activeIDWasSelected = false;
        activeIDWasSoleSelected = false;
        lastActiveIDWasSelected = false;
        lastActiveIDWasSoleSelected = false;
        lastItemData = new LastItemData();
        lastKeyboardKeyPressTime = 0.0;
        lastKeyModsChangeFromNoneTime = 0.0;
        lastKeyModsChangeTime = 0.0;
        logBuffer = new StringBuilder();
        logDepthRef = 0;
        logDepthToExpand = 2;
        logDepthToExpandDefault = 2;
        logLineFirstItem = false;
        logLinePosY = Float.MAX_VALUE;
        logNextPrefix = null;
        logNextSuffix = null;
        logOutput = null;
        itemUnclipByLog = false;
        logEnabled = false;
        logFlags = 0;
        logWindow = null;
        mainViewport = new Viewport(drawTextures);
        menuIDsSubmittedThisFrame = new IntArrayList();
        mouseCursor = MouseCursor.ARROW;
        mouseLastValidPosition = new Vector2f(0, 0);
        multiSelectStorage = new HashMap<>();
        multiSelectTempData = new ArrayList<>();
        multiSelectTempDataStackSize = 0;
        navActivateDownID = 0;
        navActivateFlags = 0;
        navActivateID = 0;
        navActivatePressedID = 0;
        navAnyRequest = false;
        navCursorHideFrames = 0;
        navCursorVisible = false;
        navFocusedWindow = null;
        navFocusRoute = new ArrayList<>();
        navFocusScopeID = 0;
        navHighlightActivatedID = 0;
        navHighlightActivatedTimer = 0;
        navHighlightItemUnderNav = false;
        navID = 0;
        navIDAlive = false;
        navInitRequest = false;
        navInitRequestFromMove = false;
        navInitResult = new NavItemData();
        navInputSource = GuiInputSource.KEYBOARD;
        navJustMovedFromFocusScopeID = 0;
        navJustMovedToFlags = 0;
        navJustMovedToFocusScopeID = 0;
        navJustMovedToID = 0;
        navJustMovedToItemData = new NavItemData();
        navJustMovedToKeyMods = 0;
        navLastValidSelectionUserData = -1;
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
        navOpenContextMenuItemID = 0;
        navOpenContextMenuWindowID = 0;
        navScoringDebugCount = 0;
        navScoringNoClipRect = new RectFloat(0, 0, 0, 0);
        navScoringRect = new RectFloat(0, 0, 0, 0);
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
        navWindowingTimer = 0;
        navWindowingToggleLayer = false;
        nextItemData = new NextItemData();
        nextWindowData = new NextWindowData();
        openPopupStack = new ArrayList<>();
        platformIO = new PlatformIO();
        scrollbarClickDistanceToCenter = 0.0f;
        scrollbarSeekMode = 0;
        settingsDirtyTimer = 0;
        settingsLoaded = false;
        settingsWindows = new ArrayList<>();
        settingsHandlers = new ArrayList<>();
        settingsIniData = new StringBuilder();
        sessionDate = 0;
        shrinkWidthBuffer = new ArrayList<>();
        sliderCurrentAccumulatedDelta = 0.0f;
        sliderCurrentAccumulatedDeltaDirty = false;
        sliderGrabClickOffset = 0.0f;
        stackSizesInBegin = new ErrorRecoveryState();
        stackSizesInNewFrame = new ErrorRecoveryState();
        style = new Style();
        styleVariableStack = new ArrayDeque<>();
        tabBars = new HashMap<>();
        tables = new ArrayList<>();
        tablesByID = new HashMap<>();
        settingsTables = new ArrayList<>();
        tablesTempDataStacked = 0;
        tablesLastTimeActive = new FloatArrayList();
        tablesTempData = new ArrayList<>();
        tempBuffer = new StringBuilder();
        textInputNextFrameOverride = 0;
        time = 0;
        tooltipOverrideCount = 0;
        tooltipPreviousWindow = null;
        treeNodeStack = new ArrayDeque<>();
        viewports = new ArrayList<>();
        currentViewport = null;
        mouseViewport = null;
        mouseLastHoveredViewport = null;
        platformLastFocusedViewportID = 0;
        fallbackMonitor = new PlatformMonitor();
        platformMonitorsFullWorkRect = new RectFloat(0, 0, 0, 0);
        viewportCreatedCount = 0;
        platformWindowsCreatedCount = 0;
        viewportFocusedStampCount = 0;
        currentDpiScale = 1.0f;
        configFlagsCurrentFrame = 0;
        configFlagsLastFrame = 0;
        frameCountPlatformEnded = -1;
        windowActiveCount = 0;
        windowBorderHoverPadding = 0.0f;
        windowByID = new HashMap<>();
        windowCurrent = null;
        windowDisplayOrder = new ArrayList<>();
        windowFocusOrder = new ArrayList<>();
        windowHovered = null;
        windowHoveredBeforeClear = null;
        windowHoveredUnderMovingWindow = null;
        windowMoving = null;
        windowResizeBorderExpectedRect = new RectFloat(0, 0, 0, 0);
        windowResizeRelativeMode = false;
        windowStack = new ArrayList<>();
        windowWheeling = null;
        withinEndChildID = 0;
        withinEndPopupID = 0;
        windowWheelingAxisAverage = new Vector2f(0, 0);
        windowWheelingRefMousePosition = new Vector2f(0, 0);
        windowWheelingReleaseTimer = 0;
        windowWheelingScrolledFrame = 0;
        windowWheelingStartFrame = 0;
        windowWheelingWheelRemainder = new Vector2f(0, 0);
        withinFrameScope = false;
        withinFrameScopeWithImplicitWindow = false;

        // Create the default viewport
        mainViewport.id = Viewport.DEFAULT_ID;
        mainViewport.index = 0;
        mainViewport.platformWindowCreated = true;
        mainViewport.flags = ViewportFlags.OWNED_BY_APP;
        viewports.add(mainViewport);
        viewportCreatedCount++;
        platformIO.viewports.add(mainViewport);
    }
}
