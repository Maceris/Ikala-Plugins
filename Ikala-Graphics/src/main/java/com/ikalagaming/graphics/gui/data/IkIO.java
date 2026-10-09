package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.enums.*;
import com.ikalagaming.graphics.gui.event.GuiInputEvent;
import com.ikalagaming.graphics.gui.flags.BackendFlags;
import com.ikalagaming.graphics.gui.flags.ColorEditFlags;
import com.ikalagaming.graphics.gui.flags.ConfigFlags;
import com.ikalagaming.graphics.gui.flags.KeyModFlags;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class IkIO {

    /** Whether we are accepting events. */
    @Getter private boolean appAcceptingEvents;

    /** Whether the app has lost focus. */
    public boolean appFocusLost;

    /**
     * @see BackendFlags
     */
    public int backendFlags;

    /**
     * @see ConfigFlags
     */
    public int configFlags;

    /**
     * Current settings for colorEdit/colorPicker widgets. Must have exactly one bit of each of
     * {@link ColorEditFlags#DISPLAY_MASK}, {@link ColorEditFlags#DATA_TYPE_MASK}, {@link
     * ColorEditFlags#PICKER_MASK}, and {@link ColorEditFlags#INPUT_MASK}. Defaults to {@link
     * ColorEditFlags#DEFAULT_OPTIONS}. May be further edited by users through the options menu,
     * unless you also use {@link ColorEditFlags#NO_OPTIONS}.
     *
     * @see ColorEditFlags
     */
    public int configColorEditFlags;

    /**
     * Used to test begin/end and beginChild/endChild behaviors. Some calls to begin/beginChild will
     * return false, cycles through window depths.
     */
    public boolean configDebugBeginReturnValueLoop;

    /**
     * The first calls to begin/beginChild will return false. Must be set before creating the
     * windows, or it won't have an effect on the window.
     */
    public boolean configDebugBeginReturnValueOnce;

    /** Ignore focus loss, notably for avoiding the input data being cleared when focus is lost. */
    public boolean configDebugIgnoreFocusLoss;

    /**
     * Enable various debug tools showing buttons that will call debugBreak(), for when a debugger
     * is attached. Put a breakpoint in IkGuiInternal.debugBreak() to stop in the code that
     * submitted the item or window.
     */
    public boolean configDebugIsDebuggerPresent;

    /** Save extra information with the .ini data. */
    public boolean configDebugIniSettings;

    /**
     * Highlight and show an error message tooltip when multiple items have conflicting identifiers.
     * Conflicts are found when hovering, as the hovered item is cheap to compare against.
     */
    public boolean configDebugHighlightIdConflicts;

    /** Show an "Item Picker" button in the ID conflict tooltip. */
    public boolean configDebugHighlightIdConflictsShowItemPicker;

    /**
     * Try to recover from incorrect API usage, such as a missing end() or popID(), so the
     * application can keep running. Errors are reported according to the configErrorRecoveryEnable
     * options. Recovery is not perfect nor guaranteed, it is a feature to ease development. Without
     * recovery, mismatched calls leave the stacks broken for the following frames.
     */
    public boolean configErrorRecovery;

    /**
     * Throw an {@link com.ikalagaming.graphics.gui.IkGuiUserError} when incorrect API usage is
     * detected, to stop at the misuse. Off by default, so errors are logged and recovered from.
     */
    public boolean configErrorRecoveryEnableAssert;

    /** Log incorrect API usage to the logger and the debug log. */
    public boolean configErrorRecoveryEnableDebugLog;

    /** Show incorrect API usage in an error tooltip, and outline the window it happened in. */
    public boolean configErrorRecoveryEnableTooltip;

    /**
     * Enable loading/saving the last used date (YYYYMMDD) in some .ini entries, making things
     * easier to audit and allowing tools to clean up old data.
     */
    public boolean configIniSettingsSaveLastUsedDate;

    /**
     * Number of months after which unused .ini entries are discarded on load, 0 to disable.
     * Requires platformIO.platformSessionDate to be set. Entries without a last used date are
     * always discarded when this is enabled.
     */
    public int configIniSettingsAutoDiscardMonths;

    /** Force every floating window display within a docking node. */
    public boolean configDockingAlwaysTabBar;

    /**
     * Disable window splitting, so docking is limited to merging windows together into tab bars.
     */
    public boolean configDockingNoSplit;

    /** Disable window merging into the same tab bar, so docking is limited to splitting windows. */
    public boolean configDockingNoDockingOver;

    /**
     * Enable docking only while holding shift, instead of disabling docking while holding shift.
     * Reduces visual noise and allows dropping in a wider space.
     */
    public boolean configDockingWithShift;

    /**
     * Make windows transparent while they are being dragged for docking, so the docking preview on
     * the target can be seen through them.
     */
    public boolean configDockingTransparentPayload;

    /**
     * Scale windows (position and size) when the DPI of their viewport changes, when using multiple
     * viewports. This is a lossy operation.
     */
    public boolean configDpiScaleViewports;

    /** Enable blinking cursor. */
    public boolean configInputTextCursorBlink;

    /** Pressing Enter will keep the item active and select its contents (single-line only). */
    public boolean configInputTextEnterKeepActive;

    /**
     * Spread out some events (e.g. button down + up) in the queue over multiple frames, for
     * smoother handling in lower frame rates.
     */
    public boolean configInputTrickleEventQueue;

    /**
     * Swap to OS X behaviors:
     *
     * <ul>
     *   <li>Swap super and control keys
     *   <li>Use alt instead of ctrl for text editing cursor movement
     *   <li>Shortcuts use super instead of ctrl
     *   <li>Line/text start/end using cmd+arrows instead of home/end
     *   <li>Double-click selects by word instead of the whole text
     *   <li>Multi-selection in lists uses super instead of ctrl
     * </ul>
     */
    public boolean configMacOSXBehaviors;

    /**
     * Timer (in seconds) between freeing temporary window/table memory buffers when unused. Set to
     * -1.0f to disable.
     */
    public float configMemoryCompactTimer;

    /**
     * Request that IkGui draws a mouse cursor itself, instead of the platform. A cursor drawn with
     * the GUI will feel more laggy than a hardware cursor, but will be more in sync with the other
     * visuals. Some applications use both kinds of cursors, e.g. only drawing the cursor while
     * resizing or dragging something. The platform backend hides its cursor while this is set.
     */
    public boolean configMouseDrawCursor;

    /** Sets io.wantCaptureKeyboard when io.navActive is set. */
    public boolean configNavCaptureKeyboard;

    /**
     * Swap the activate and cancel gamepad buttons (A and B), matching the typical
     * "Nintendo/Japanese style" gamepad layout.
     */
    public boolean configNavSwapGamepadButtons;

    /**
     * [Beta] Turn drag widgets into a text input with a simple mouse click and release, without
     * moving. Not desirable on devices without a keyboard.
     */
    public boolean configDragClickToInputText;

    /**
     * [Experimental] Ctrl+C copies the contents of the focused window to the clipboard. This is
     * experimental because it has known issues with nested begin()/end() pairs, the text output
     * quality varies, and the text is in submission order rather than spatial order.
     */
    public boolean configWindowsCopyContentsWithCtrlC;

    /**
     * Scroll page by page when clicking outside the scrollbar grab. When disabled, always scroll to
     * the clicked location. When enabled, Shift+Click scrolls to the clicked location.
     */
    public boolean configScrollbarScrollByPage;

    /**
     * Directional/tabbing navigation teleports the mouse cursor. May be useful on TV/console
     * systems where moving a virtual mouse is difficult. Will update io.mousePosition and set
     * io.wantSetMousePosition.
     */
    public boolean configNavMoveSetMousePosition;

    /**
     * Pressing Escape can clear the focused item and navigation ID/highlight. Set to false if you
     * want to always keep the highlight on.
     */
    public boolean configNavEscapeClearFocusItem;

    /** Pressing Escape can clear the focused window as well (superset of the item option). */
    public boolean configNavEscapeClearFocusWindow;

    /**
     * Using directional navigation keys makes the cursor visible. Clicking the mouse hides the
     * cursor.
     */
    public boolean configNavCursorVisibleAuto;

    /** The navigation cursor is always visible. */
    public boolean configNavCursorVisibleAlways;

    /**
     * The mouse position has been altered and the backend should reposition the mouse on the next
     * frame. Set only when configNavMoveSetMousePosition is enabled.
     */
    public boolean wantSetMousePosition;

    /**
     * Makes all floating windows use their own viewports. Otherwise, they are merged into the main
     * viewport when overlapping it.
     */
    public boolean configViewportsNoAutoMerge;

    /** Disable the default window decoration flag for secondary viewports. */
    public boolean configViewportsNoDecoration;

    /**
     * When false, set secondary viewport's parentViewportId to the main viewport ID by default.
     * When true, all viewports will be top-level OS windows.
     */
    public boolean configViewportsNoDefaultParent;

    /** Disable default task bar icon flag for secondary viewports. */
    public boolean configViewportsNoTaskBarIcon;

    /** When a platform window is focused, the corresponding focus is applied to our windows. */
    public boolean configViewportsPlatformFocusSetsWindowFocus;

    /** Enable moving windows only when clicking on the title bar, for windows with title bars. */
    public boolean configWindowsMoveFromTitleBarOnly;

    /** Enable resizing of windows from edges and lower-right corner. */
    public boolean configWindowsResizeFromEdges;

    /** Time elapsed since last frame, in milliseconds. */
    public long deltaTime;

    /**
     * Main display density, for retina displays where coordinates are different from framebuffer
     * coordinates.
     */
    public final Vector2f displayFramebufferScale;

    /** Main display size, in pixels. Might change every frame. */
    public final Vector2f displaySize;

    /** The queue of all events. */
    private final Queue<GuiInputEvent> eventQueue;

    private final ReentrantLock eventQueueLock;

    /** One or more fonts loaded into a single texture. */
    public final FontAtlas fonts;

    /** Path to the .ini file, set to null to disable automatic .ini loading/saving. */
    public String iniFilename;

    /** Minimum time between saving positions/sizes to .ini file, in milliseconds. */
    public long iniSavingRate;

    /** Path to the log file, the default file for logToFile() when no file is specified. */
    public String logFilename;

    /** Whether the alt key is down. */
    public boolean keyAlt;

    /** Whether the control key is down. */
    public boolean keyCtrl;

    /**
     * Key mod flags, but merged into flags. Same as {@link #keyAlt}/{@link #keyCtrl}/{@link
     * #keyShift}/ {@link #keySuper}.
     */
    public int keyMods;

    /** When holding a key/button, time (in milliseconds) before it starts to repeat. */
    public long keyRepeatDelay;

    /** When holding a key/button, the rate (in milliseconds) at which it repeats. */
    public long keyRepeatRate;

    /** Whether each key is currently down. Indexes correspond to {@link Key#ordinal()}. */
    public final boolean[] keysDown;

    /**
     * The analog value of each key, 0 to 1, for gamepad keys. Indexes correspond to {@link
     * Key#ordinal()}.
     */
    public final float[] keysAnalogValue;

    /**
     * Whether each modifier key (ctrl, shift, alt, super) is down from events for the modifier keys
     * themselves, like {@link Key#MOD_CTRL}, as opposed to the left/right keys.
     */
    private final boolean[] modKeyEventsDown;

    /**
     * Duration each key has been down for, in milliseconds. 0 is just pressed, -1 is not pressed.
     * Indexes correspond to {@link Key#ordinal()}.
     */
    public final long[] keysDownDuration;

    /** {@link #keysDownDuration} for the previous frame. */
    public final long[] keysDownDurationPrevious;

    /** Characters that were input this frame, as text. Cleared at the end of each frame. */
    public final StringBuilder inputQueueCharacters;

    /** Whether the shift key is down. */
    public boolean keyShift;

    /** Whether the super key is down. */
    public boolean keySuper;

    /** Number of active windows. */
    public int metricsActiveWindows;

    /**
     * Estimate of the application framerate (rolling average over 60 frames), in frames per second.
     * Solely for convenience.
     */
    public float framerate;

    /** Indices output during last call to render(). */
    public int metricsRenderIndices;

    /** Vertices output during last call to render(). */
    public int metricsRenderVertices;

    /** Number of visible windows. */
    public int metricsRenderWindows;

    /**
     * Mouse button went from not down to down. Should probably not be modified directly. Indexes
     * correspond to {@link MouseButton#index}.
     */
    public final boolean[] mouseClicked;

    /**
     * Position at the time of clicking. Should probably not be modified directly. Indexes
     * correspond to {@link MouseButton#index}.
     */
    public final Vector2f[] mouseClickedPosition;

    /**
     * 0 is not clicked, 1 is mouse clicked, 2 is double-clicked, etc. When going from not down to
     * down. Should probably not be modified directly. Indexes correspond to {@link
     * MouseButton#index}.
     */
    public final short[] mouseClickedCount;

    /**
     * Count successive number of clicks, reset after another click is done. Should probably not be
     * modified directly. Indexes correspond to {@link MouseButton#index}.
     */
    public final short[] mouseClickedLastCount;

    /**
     * Time of last click, in milliseconds. Should probably not be modified directly. Indexes
     * correspond to {@link MouseButton#index}.
     */
    public final long[] mouseClickedTime;

    /**
     * Used for OS X, set to true when the current click was a ctrl+click that simulated a right
     * click.
     */
    public boolean mouseCtrlLeftAsRightClick;

    /**
     * Change in mouse position, in pixels, since the last frame. Will be zero if either current or
     * previous position are invalid.
     */
    public final Vector2f mouseDelta;

    /** Distance threshold for validating a double click, in pixels. */
    public float mouseDoubleClickMaxDistance;

    /** Time for a double click, in milliseconds. */
    public long mouseDoubleClickTime;

    /**
     * Time for a delayed single click when using getItemClickedCountWithSingleClickDelay() or
     * isMouseReleasedWithDelay(), in milliseconds. Must be larger than mouseDoubleClickTime.
     */
    public long mouseSingleClickDelay;

    /**
     * Duration the mouse button has been down for, in milliseconds. Should probably not be modified
     * directly. 0 is just clicked, -1 is not down. Indexes correspond to {@link MouseButton#index}.
     */
    public final long[] mouseDownDuration;

    /** {@link #mouseDownDuration} for the previous frame. */
    public final long[] mouseDownDurationPrevious;

    /**
     * If a mouse button is down. Should probably not be modified directly. Indexes correspond to
     * {@link MouseButton#index}.
     */
    public final boolean[] mouseDown;

    /**
     * If a button was clicked inside a gui window or over empty space while there was a popup.
     * Should probably not be modified directly. Indexes correspond to {@link MouseButton#index}.
     */
    public final boolean[] mouseDownOwned;

    /**
     * If a button was clicked inside a gui window. Should probably not be modified directly.
     * Indexes correspond to {@link MouseButton#index}.
     */
    public final boolean[] mouseDownOwnedUnlessPopupClose;

    /**
     * The maximum distance (absolute) on each axis that the mouse has traveled from the clicking
     * point, while down, in pixels. Should probably not be modified directly. Indexes correspond to
     * {@link MouseButton#index}.
     */
    public final Vector2f[] mouseDragMaxDistanceAbsolute;

    /**
     * The squared maximum distance on each axis that the mouse has traveled from teh clicking point
     * while down, in pixels. Should probably not be modified directly. Indexes correspond to {@link
     * MouseButton#index}.
     */
    public final float[] mouseDragMaxDistanceSquare;

    /** Distance threshold before considering a mouse drag, in pixels. */
    public float mouseDragThreshold;

    /**
     * The ID of the viewport that the OS mouse is hovering over, when using multiple viewports. If
     * possible, this should ignore viewports with {@link
     * com.ikalagaming.graphics.gui.flags.ViewportFlags#NO_INPUTS} set. Set {@link
     * BackendFlags#HAS_MOUSE_HOVERED_VIEWPORT} if you can provide this info, otherwise we infer it
     * from the positions and last focused time of the viewports we know about (ignoring other OS
     * windows). Modified with addMouseViewportEvent().
     */
    public int mouseHoveredViewport;

    /** The mouse is currently inside a (any) window. */
    public boolean mouseInsideWindow;

    /**
     * Mouse position, in pixels. Set to (-{@link Float#MAX_VALUE}, -{@link Float#MAX_VALUE}) if
     * mouse is unavailable.
     */
    public final Vector2f mousePosition;

    /** Previous mouse position. */
    public final Vector2f mousePositionPrevious;

    /**
     * Mouse button went from down to not down. Should probably not be modified directly. Indexes
     * correspond to {@link MouseButton#index}.
     */
    public final boolean[] mouseReleased;

    /**
     * Time since last mouse release, in milliseconds. Mostly for distinguishing two single clicks
     * from a double click. Should probably not be modified directly. Indexes correspond to {@link
     * MouseButton#index}.
     */
    public final long[] mouseReleasedTime;

    public @NonNull MouseSource mouseSource;

    /** How long the mouse has been still, in milliseconds. */
    public long mouseStationaryTimer;

    /**
     * Mouse wheel vertical. 1 unit scrolls about 5 lines of text, positive values scroll up and
     * negative values scroll down.
     */
    public float mouseWheel;

    /** Mouse wheel horizontal, positive values scroll left, and negative values scroll right. */
    public float mouseWheelH;

    /** On non-mac systems, holding shift swaps vertical mouse wheel scrolling to horizontal. */
    public boolean mouseWheelRequestAxisSwap;

    /**
     * Keyboard/gamepad navigation is currently allowed, i.e. window focused and doesn't have nav
     * input disabled.
     */
    public boolean navActive;

    /** Keyboard/gamepad navigation highlight is visible and allowed. */
    public boolean navVisible;

    /** Touch pen pressure, 0.0f to 1.0f, should be >0 only when the first mouse input is down. */
    public float penPressure;

    /**
     * Set when we want to capture keyboard inputs and not dispatch them to the main application.
     */
    public boolean wantCaptureKeyboard;

    /** Set when we want to capture mouse inputs and not dispatch them to the main application. */
    public boolean wantCaptureMouse;

    /**
     * Set when we want to capture mouse inputs when a click over an empty area is expected to close
     * a popup.
     */
    public boolean wantCaptureMouseUnlessPopupClose;

    /**
     * When manual .ini save/load is active ({@link #iniFilename} is null), this signals to the
     * application that we want to save ini settings. You should reset this to false yourself after
     * saving settings.
     */
    public boolean wantSaveIniSettings;

    /** For mobile/console, we want to display an on-screen keyboard for textual inputs. */
    public boolean wantTextInput;

    public IkIO() {
        appAcceptingEvents = true;
        appFocusLost = false;
        backendFlags = BackendFlags.NONE;
        configFlags = ConfigFlags.NONE;
        configColorEditFlags = ColorEditFlags.DEFAULT_OPTIONS;
        configDebugBeginReturnValueLoop = false;
        configDebugBeginReturnValueOnce = false;
        configDebugIgnoreFocusLoss = false;
        configDebugIsDebuggerPresent = false;
        configDebugIniSettings = false;
        configDebugHighlightIdConflicts = true;
        configDebugHighlightIdConflictsShowItemPicker = true;
        configNavSwapGamepadButtons = false;
        configDragClickToInputText = false;
        configWindowsCopyContentsWithCtrlC = false;
        configScrollbarScrollByPage = true;
        configErrorRecovery = true;
        configErrorRecoveryEnableAssert = false;
        configErrorRecoveryEnableDebugLog = true;
        configErrorRecoveryEnableTooltip = true;
        configIniSettingsSaveLastUsedDate = true;
        configIniSettingsAutoDiscardMonths = 0;
        configDockingAlwaysTabBar = false;
        configDockingNoSplit = false;
        configDockingNoDockingOver = false;
        configDockingWithShift = false;
        configDockingTransparentPayload = false;
        configDpiScaleViewports = false;
        configInputTextCursorBlink = true;
        configInputTextEnterKeepActive = false;
        configInputTrickleEventQueue = true;
        configMacOSXBehaviors = false;
        configMemoryCompactTimer = 60.0f;
        configMouseDrawCursor = false;
        configNavCaptureKeyboard = true;
        configNavMoveSetMousePosition = false;
        configNavEscapeClearFocusItem = true;
        configNavEscapeClearFocusWindow = false;
        configNavCursorVisibleAuto = true;
        configNavCursorVisibleAlways = false;
        wantSetMousePosition = false;
        configViewportsNoAutoMerge = false;
        configViewportsNoDecoration = true;
        configViewportsNoDefaultParent = true;
        configViewportsNoTaskBarIcon = false;
        configViewportsPlatformFocusSetsWindowFocus = true;
        configWindowsMoveFromTitleBarOnly = false;
        configWindowsResizeFromEdges = true;
        deltaTime = 0;
        displayFramebufferScale = new Vector2f(1, 1);
        displaySize = new Vector2f(0, 0);
        eventQueue = new ArrayDeque<>();
        eventQueueLock = new ReentrantLock();
        fonts = new FontAtlas();
        iniFilename = "ikgui.ini";
        iniSavingRate = 5000;
        logFilename = "ikgui_log.txt";
        keyAlt = false;
        keyCtrl = false;
        keyMods = KeyModFlags.NONE;
        keyRepeatDelay = 275;
        keyRepeatRate = 50;
        keysDown = new boolean[Key.values().length];
        keysAnalogValue = new float[Key.values().length];
        modKeyEventsDown = new boolean[4];
        keysDownDuration = new long[Key.values().length];
        Arrays.fill(keysDownDuration, -1);
        keysDownDurationPrevious = new long[Key.values().length];
        Arrays.fill(keysDownDurationPrevious, -1);
        inputQueueCharacters = new StringBuilder();
        keyShift = false;
        keySuper = false;
        metricsActiveWindows = 0;
        framerate = 0.0f;
        metricsRenderIndices = 0;
        metricsRenderVertices = 0;
        metricsRenderWindows = 0;
        mouseClicked = new boolean[MouseButton.COUNT];
        mouseClickedPosition = new Vector2f[MouseButton.COUNT];
        for (int i = 0; i < MouseButton.COUNT; ++i) {
            mouseClickedPosition[i] = new Vector2f(0, 0);
        }
        mouseClickedCount = new short[MouseButton.COUNT];
        mouseClickedLastCount = new short[MouseButton.COUNT];
        mouseClickedTime = new long[MouseButton.COUNT];
        mouseCtrlLeftAsRightClick = false;
        mouseDelta = new Vector2f(0, 0);
        mouseDoubleClickMaxDistance = 6.0f;
        mouseDoubleClickTime = 300;
        mouseSingleClickDelay = 500;
        mouseDownDuration = new long[MouseButton.COUNT];
        Arrays.fill(mouseDownDuration, -1);
        mouseDownDurationPrevious = new long[MouseButton.COUNT];
        Arrays.fill(mouseDownDurationPrevious, -1);
        mouseDown = new boolean[MouseButton.COUNT];
        mouseDownOwned = new boolean[MouseButton.COUNT];
        mouseDownOwnedUnlessPopupClose = new boolean[MouseButton.COUNT];
        mouseDragMaxDistanceAbsolute = new Vector2f[MouseButton.COUNT];
        for (int i = 0; i < MouseButton.COUNT; ++i) {
            mouseDragMaxDistanceAbsolute[i] = new Vector2f(0, 0);
        }
        mouseDragMaxDistanceSquare = new float[MouseButton.COUNT];
        mouseDragThreshold = 6.0f;
        mouseHoveredViewport = 0;
        mouseInsideWindow = false;
        mousePosition = new Vector2f(-Float.MAX_VALUE, -Float.MAX_VALUE);
        mouseReleased = new boolean[MouseButton.COUNT];
        mouseReleasedTime = new long[MouseButton.COUNT];
        // Far in the past, so a delayed release isn't reported before the first release
        Arrays.fill(mouseReleasedTime, Long.MIN_VALUE / 2);
        mousePositionPrevious = new Vector2f(-Float.MAX_VALUE, -Float.MAX_VALUE);
        mouseSource = MouseSource.MOUSE;
        mouseStationaryTimer = 0;
        mouseWheel = 0.0f;
        mouseWheelH = 0.0f;
        mouseWheelRequestAxisSwap = false;
        navActive = false;
        navVisible = false;
        penPressure = 0.0f;
        wantCaptureKeyboard = false;
        wantCaptureMouse = false;
        wantCaptureMouseUnlessPopupClose = false;
        wantSaveIniSettings = false;
        wantTextInput = false;
    }

    public void addConfigFlags(int flags) {
        configFlags = configFlags | flags;
    }

    public void removeConfigFlags(int flags) {
        configFlags = configFlags & ~flags;
    }

    public boolean hasConfigFlags(int flags) {
        return (configFlags & flags) != 0;
    }

    public void addBackendFlags(int flags) {
        backendFlags = backendFlags | flags;
    }

    public void removeBackendFlags(int flags) {
        backendFlags = backendFlags & ~flags;
    }

    /**
     * The number of input events waiting to be processed, for debugging.
     *
     * @return The number of queued events.
     */
    public int getEventQueueSize() {
        eventQueueLock.lock();
        try {
            return eventQueue.size();
        } finally {
            eventQueueLock.unlock();
        }
    }

    public boolean hasBackendFlags(int flags) {
        return (backendFlags & flags) != 0;
    }

    /**
     * Find the latest queued event of a type, to filter out duplicate events. Call while holding
     * the event queue lock.
     *
     * @param type The event type.
     * @param matches Checks the event data, for events of a specific key or button.
     * @return The latest event, or null if there is none queued.
     */
    private GuiInputEvent findLatestEvent(
            @NonNull GuiInputEventType type,
            @NonNull java.util.function.Predicate<GuiInputEvent.EventData> matches) {
        final var iterator = ((ArrayDeque<GuiInputEvent>) eventQueue).descendingIterator();
        while (iterator.hasNext()) {
            final GuiInputEvent event = iterator.next();
            if (event.type() == type && matches.test(event.data())) {
                return event;
            }
        }
        return null;
    }

    public void addFocusEvent(boolean focused) {
        if (!appAcceptingEvents) {
            return;
        }
        eventQueueLock.lock();
        try {
            // Filter duplicates
            final GuiInputEvent latest = findLatestEvent(GuiInputEventType.FOCUS, data -> true);
            final boolean latestFocused =
                    latest != null
                            ? ((GuiInputEvent.Focused) latest.data()).focused()
                            : !appFocusLost;
            if (latestFocused == focused || (configDebugIgnoreFocusLoss && !focused)) {
                return;
            }
            eventQueue.add(
                    new GuiInputEvent(
                            GuiInputEventType.FOCUS,
                            GuiInputSource.NONE,
                            new GuiInputEvent.Focused(focused)));
        } finally {
            eventQueueLock.unlock();
        }
    }

    public void addKeyEvent(Key key, boolean down) {
        addKeyAnalogEvent(key, down, down ? 1.0f : 0.0f);
    }

    /**
     * Queue a key event with an analog value, for gamepad keys.
     *
     * @param key The key.
     * @param down Whether the key is down.
     * @param analogValue The analog value, from 0 to 1.
     */
    public void addKeyAnalogEvent(Key key, boolean down, float analogValue) {
        if (key == Key.NONE || !appAcceptingEvents) {
            return;
        }
        if (key.isMouseKey()) {
            log.error(
                    "Can't submit {} as a key event, mouse keys are set from the mouse events",
                    key);
            return;
        }

        // macOS: swap Cmd (super) and Ctrl
        if (configMacOSXBehaviors) {
            key =
                    switch (key) {
                        case MOD_SUPER -> Key.MOD_CTRL;
                        case MOD_CTRL -> Key.MOD_SUPER;
                        case LEFT_SUPER -> Key.LEFT_CTRL;
                        case RIGHT_SUPER -> Key.RIGHT_CTRL;
                        case LEFT_CTRL -> Key.LEFT_SUPER;
                        case RIGHT_CTRL -> Key.RIGHT_SUPER;
                        default -> key;
                    };
        }

        eventQueueLock.lock();
        try {
            // Filter duplicates, modifier keys and gamepad analog values are commonly spammed
            final Key eventKey = key;
            final GuiInputEvent latest =
                    findLatestEvent(
                            GuiInputEventType.KEY,
                            data -> ((GuiInputEvent.KeyPress) data).key() == eventKey);
            final boolean latestDown =
                    latest != null
                            ? ((GuiInputEvent.KeyPress) latest.data()).down()
                            : keysDown[key.ordinal()];
            final float latestAnalog =
                    latest != null
                            ? ((GuiInputEvent.KeyPress) latest.data()).analogValue()
                            : keysAnalogValue[key.ordinal()];
            if (latestDown == down && latestAnalog == analogValue) {
                return;
            }
            eventQueue.add(
                    new GuiInputEvent(
                            GuiInputEventType.KEY,
                            key.isGamepadKey() ? GuiInputSource.GAMEPAD : GuiInputSource.KEYBOARD,
                            new GuiInputEvent.KeyPress(key, down, analogValue)));
        } finally {
            eventQueueLock.unlock();
        }
    }

    public void addMousePosEvent(float x, float y) {
        if (!appAcceptingEvents) {
            return;
        }
        // The same flooring as updateMouseInputs()
        final float posX = x > -Float.MAX_VALUE ? (float) Math.floor(x) : x;
        final float posY = y > -Float.MAX_VALUE ? (float) Math.floor(y) : y;
        eventQueueLock.lock();
        try {
            // Filter duplicates
            final GuiInputEvent latest =
                    findLatestEvent(GuiInputEventType.MOUSE_POSITION, data -> true);
            final float latestX =
                    latest != null
                            ? ((GuiInputEvent.MousePosition) latest.data()).posX()
                            : mousePosition.x;
            final float latestY =
                    latest != null
                            ? ((GuiInputEvent.MousePosition) latest.data()).posY()
                            : mousePosition.y;
            if (latestX == posX && latestY == posY) {
                return;
            }
            eventQueue.add(
                    new GuiInputEvent(
                            GuiInputEventType.MOUSE_POSITION,
                            GuiInputSource.MOUSE,
                            new GuiInputEvent.MousePosition(posX, posY, mouseSource)));
        } finally {
            eventQueueLock.unlock();
        }
    }

    /**
     * Register a mouse button event.
     *
     * @param button The button in question.
     * @param down True if the button is now down, false if it's now up.
     */
    public void addMouseButtonEvent(@NonNull MouseButton button, boolean down) {
        if (!appAcceptingEvents) {
            return;
        }
        // macOS: Ctrl (super) + left click is converted into a right click, handle the held button
        if (configMacOSXBehaviors && button == MouseButton.LEFT && mouseCtrlLeftAsRightClick) {
            // The order matters, this event still releases the right button
            button = MouseButton.RIGHT;
            if (!down) {
                mouseCtrlLeftAsRightClick = false;
            }
        }

        eventQueueLock.lock();
        try {
            // Filter duplicates
            final MouseButton eventButton = button;
            final GuiInputEvent latest =
                    findLatestEvent(
                            GuiInputEventType.MOUSE_BUTTON,
                            data -> ((GuiInputEvent.MouseButton) data).button() == eventButton);
            final boolean latestDown =
                    latest != null
                            ? ((GuiInputEvent.MouseButton) latest.data()).down()
                            : mouseDown[button.index];
            if (latestDown == down) {
                return;
            }

            // macOS: convert Ctrl (super) + left click into a right click. This is the physical
            // Ctrl key, which is super for us.
            if (configMacOSXBehaviors && button == MouseButton.LEFT && down) {
                final GuiInputEvent latestSuper =
                        findLatestEvent(
                                GuiInputEventType.KEY,
                                data -> ((GuiInputEvent.KeyPress) data).key() == Key.MOD_SUPER);
                if (latestSuper != null
                        ? ((GuiInputEvent.KeyPress) latestSuper.data()).down()
                        : keySuper) {
                    mouseCtrlLeftAsRightClick = true;
                    button = MouseButton.RIGHT;
                    final GuiInputEvent latestRight =
                            findLatestEvent(
                                    GuiInputEventType.MOUSE_BUTTON,
                                    data ->
                                            ((GuiInputEvent.MouseButton) data).button()
                                                    == MouseButton.RIGHT);
                    if (latestRight != null
                            ? ((GuiInputEvent.MouseButton) latestRight.data()).down()
                            : mouseDown[MouseButton.RIGHT.index]) {
                        return;
                    }
                }
            }

            eventQueue.add(
                    new GuiInputEvent(
                            GuiInputEventType.MOUSE_BUTTON,
                            GuiInputSource.MOUSE,
                            new GuiInputEvent.MouseButton(button, down, mouseSource)));
        } finally {
            eventQueueLock.unlock();
        }
    }

    public void addMouseWheelEvent(float wheelX, float wheelY) {
        // Filter duplicates, wheel values are relative so this is easy
        if (!appAcceptingEvents || (wheelX == 0.0f && wheelY == 0.0f)) {
            return;
        }
        eventQueueLock.lock();
        try {
            eventQueue.add(
                    new GuiInputEvent(
                            GuiInputEventType.MOUSE_WHEEL,
                            GuiInputSource.MOUSE,
                            new GuiInputEvent.MouseWheel(wheelX, wheelY, mouseSource)));
        } finally {
            eventQueueLock.unlock();
        }
    }

    public void addMouseViewportEvent(int id) {
        if (!appAcceptingEvents) {
            return;
        }
        eventQueueLock.lock();
        try {
            GuiInputEvent event =
                    new GuiInputEvent(
                            GuiInputEventType.MOUSE_VIEWPORT,
                            GuiInputSource.NONE,
                            new GuiInputEvent.Viewport(id));
            eventQueue.add(event);
        } finally {
            eventQueueLock.unlock();
        }
    }

    /**
     * Queue a new character input.
     *
     * @param c The character that was typed.
     */
    public void addInputCharacter(char c) {
        if (!appAcceptingEvents || c == 0) {
            return;
        }
        eventQueueLock.lock();
        try {
            GuiInputEvent event =
                    new GuiInputEvent(
                            GuiInputEventType.TEXT,
                            GuiInputSource.KEYBOARD,
                            new GuiInputEvent.Text(c));
            eventQueue.add(event);
        } finally {
            eventQueueLock.unlock();
        }
    }

    /**
     * Queue a unicode code point as character input, which might be more than one UTF-16 character.
     *
     * @param codePoint The unicode code point that was typed.
     */
    public void addInputCharacter(int codePoint) {
        if (!Character.isValidCodePoint(codePoint)) {
            return;
        }
        for (char c : Character.toChars(codePoint)) {
            addInputCharacter(c);
        }
    }

    /**
     * Queue all the characters in a string as new character inputs.
     *
     * @param str The text that was input.
     */
    public void addInputCharacters(@NonNull String str) {
        for (int i = 0; i < str.length(); ++i) {
            addInputCharacter(str.charAt(i));
        }
    }

    public void setAppAcceptingEvents(boolean acceptingEvents) {
        appAcceptingEvents = acceptingEvents;
    }

    /** Clear out (discard) everything in the event queue. */
    public void clearEventQueue() {
        eventQueueLock.lock();
        try {
            eventQueue.clear();
        } finally {
            eventQueueLock.unlock();
        }
    }

    /** Clear the current keyboard and gamepad state, as if all keys were released. */
    public void clearInputKeys() {
        for (Key key : Key.values()) {
            // The mouse keys are cleared by clearInputMouse()
            if (key.isMouseKey()) {
                continue;
            }
            clearKey(key);
        }
        java.util.Arrays.fill(modKeyEventsDown, false);
        keyCtrl = false;
        keyShift = false;
        keyAlt = false;
        keySuper = false;
        keyMods = KeyModFlags.NONE;
        inputQueueCharacters.setLength(0);
    }

    /** Clear the current mouse state, as if all buttons were released. */
    public void clearInputMouse() {
        for (Key key : Key.values()) {
            if (key.isMouseKey()) {
                clearKey(key);
            }
        }
        for (int i = 0; i < MouseButton.COUNT; ++i) {
            mouseDown[i] = false;
            mouseDownDuration[i] = -1;
            mouseDownDurationPrevious[i] = -1;
        }
        mouseWheel = 0;
        mouseWheelH = 0;
    }

    private void clearKey(@NonNull Key key) {
        final int index = key.ordinal();
        keysDown[index] = false;
        keysDownDuration[index] = -1;
        keysDownDurationPrevious[index] = -1;
    }

    public boolean getMouseDown(@NonNull MouseButton button) {
        return mouseDown[button.index];
    }

    public long getMouseClickedTime(@NonNull MouseButton button) {
        return mouseClickedTime[button.index];
    }

    public boolean getMouseClicked(@NonNull MouseButton button) {
        return mouseClicked[button.index];
    }

    public boolean getMouseDoubleClicked(@NonNull MouseButton button) {
        return mouseClickedCount[button.index] == 2;
    }

    public int getMouseClickedCount(@NonNull MouseButton button) {
        return mouseClickedCount[button.index];
    }

    public int getMouseClickedLastCount(@NonNull MouseButton button) {
        return mouseClickedLastCount[button.index];
    }

    public boolean getMouseReleased(@NonNull MouseButton button) {
        return mouseReleased[button.index];
    }

    public boolean getMouseDownOwned(@NonNull MouseButton button) {
        return mouseDownOwned[button.index];
    }

    public boolean getMouseDownOwnedUnlessPopupClose(@NonNull MouseButton button) {
        return mouseDownOwnedUnlessPopupClose[button.index];
    }

    public long getMouseDownDuration(@NonNull MouseButton button) {
        return mouseDownDuration[button.index];
    }

    public float getMouseDragMaxDistanceSqr(@NonNull MouseButton button) {
        return mouseDragMaxDistanceSquare[button.index];
    }

    /**
     * Check if a key is currently held down.
     *
     * @param key The key.
     * @return True if the key is down.
     */
    public boolean getKeyDown(@NonNull Key key) {
        return keysDown[key.ordinal()];
    }

    /**
     * How long the key has been held down for.
     *
     * @param key The key.
     * @return The duration in milliseconds, 0 if just pressed this frame, -1 if not down.
     */
    public long getKeyDownDuration(@NonNull Key key) {
        return keysDownDuration[key.ordinal()];
    }

    private void handleKey(@NonNull Key key, boolean down, float analogValue) {
        keysDown[key.ordinal()] = down;
        keysAnalogValue[key.ordinal()] = analogValue;
        switch (key) {
            case MOD_CTRL -> modKeyEventsDown[0] = down;
            case MOD_SHIFT -> modKeyEventsDown[1] = down;
            case MOD_ALT -> modKeyEventsDown[2] = down;
            case MOD_SUPER -> modKeyEventsDown[3] = down;
            default -> {}
        }
        // Backends may submit either the modifier keys themselves or the left/right keys
        keyCtrl = modKeyEventsDown[0] || getKeyDown(Key.LEFT_CTRL) || getKeyDown(Key.RIGHT_CTRL);
        keyShift = modKeyEventsDown[1] || getKeyDown(Key.LEFT_SHIFT) || getKeyDown(Key.RIGHT_SHIFT);
        keyAlt = modKeyEventsDown[2] || getKeyDown(Key.LEFT_ALT) || getKeyDown(Key.RIGHT_ALT);
        keySuper = modKeyEventsDown[3] || getKeyDown(Key.LEFT_SUPER) || getKeyDown(Key.RIGHT_SUPER);
        keysDown[Key.MOD_CTRL.ordinal()] = keyCtrl;
        keysDown[Key.MOD_SHIFT.ordinal()] = keyShift;
        keysDown[Key.MOD_ALT.ordinal()] = keyAlt;
        keysDown[Key.MOD_SUPER.ordinal()] = keySuper;
    }

    /**
     * The modifiers from the current modifier key state, bypassing key ownership.
     *
     * @return The modifiers.
     * @see KeyModFlags
     */
    private int getMergedModsFromKeys() {
        int mods = KeyModFlags.NONE;
        if (keysDown[Key.MOD_CTRL.ordinal()]) {
            mods |= KeyModFlags.CTRL;
        }
        if (keysDown[Key.MOD_SHIFT.ordinal()]) {
            mods |= KeyModFlags.SHIFT;
        }
        if (keysDown[Key.MOD_ALT.ordinal()]) {
            mods |= KeyModFlags.ALT;
        }
        if (keysDown[Key.MOD_SUPER.ordinal()]) {
            mods |= KeyModFlags.SUPER;
        }
        return mods;
    }

    /**
     * Whether a key chord might produce a character, mimicking the logic in input text widgets.
     *
     * @param mods The modifiers held.
     * @param key The key.
     * @return True if the key might be for character input.
     */
    private boolean isKeyPotentiallyCharInput(int mods, @NonNull Key key) {
        // When the right mods are pressed it can't be character input
        final boolean ignoreCharInputs =
                ((mods & KeyModFlags.CTRL) != 0 && (mods & KeyModFlags.ALT) == 0)
                        || (configMacOSXBehaviors && (mods & KeyModFlags.CTRL) != 0);
        if (ignoreCharInputs || key == Key.NONE) {
            return false;
        }
        return IkGui.getContext().keysMayBeCharInput[key.ordinal()];
    }

    /**
     * Handle mouse down event.
     *
     * @param button The mouse button.
     * @param value True if the button is now down, false if it's now up.
     */
    private void handleMouseDown(@NonNull MouseButton button, boolean value) {
        final int index = button.index;
        if (index < 0 || index >= MouseButton.COUNT) {
            log.warn("Invalid mouse button index {} in setMouseDown", index);
            return;
        }
        mouseDown[index] = value;
    }

    private void handleMouseViewport(int id) {
        mouseHoveredViewport = id;
    }

    private void handleMouseWheel(float wheelX, float wheelY) {
        mouseWheelH += wheelX;
        mouseWheel += wheelY;
    }

    /**
     * Process input events, called internally. When {@link #configInputTrickleEventQueue} is set,
     * multiple changes to the same input (e.g. a mouse button pressed and released) are spread over
     * multiple frames so that very fast inputs are not lost. Events that are not processed this
     * frame are left in the queue for next frame.
     */
    public void processInputEvents() {
        final boolean trickle = configInputTrickleEventQueue;
        // Only trickle characters and keys when working with a text input
        final boolean trickleInterleavedNonCharKeysAndText =
                trickle && IkGui.getContext().wantTextInputNextFrame == 1;
        boolean mouseMoved = false;
        boolean mouseWheeled = false;
        boolean keyChanged = false;
        boolean keyChangedNonChar = false;
        boolean textInputted = false;
        int mouseButtonChanged = 0;
        final boolean[] keysChanged = new boolean[keysDown.length];
        final boolean noKeyboard = (configFlags & ConfigFlags.NO_KEYBOARD) != 0;

        eventQueueLock.lock();
        try {
            processing:
            while (!eventQueue.isEmpty()) {
                final GuiInputEvent event = eventQueue.peek();
                switch (event.type()) {
                    case GuiInputEventType.MOUSE_POSITION -> {
                        if (wantSetMousePosition) {
                            break;
                        }
                        // Trickling: stop if we already handled a mouse button change
                        if (trickle
                                && (mouseButtonChanged != 0
                                        || mouseWheeled
                                        || keyChanged
                                        || textInputted)) {
                            break processing;
                        }
                        final GuiInputEvent.MousePosition data =
                                (GuiInputEvent.MousePosition) event.data();
                        mousePosition.set(data.posX(), data.posY());
                        mouseSource = data.source();
                        mouseMoved = true;
                    }
                    case GuiInputEventType.MOUSE_BUTTON -> {
                        final GuiInputEvent.MouseButton data =
                                (GuiInputEvent.MouseButton) event.data();
                        final int buttonMask = 1 << data.button().index;
                        // Trickling: stop if we got multiple actions on the same button
                        if (trickle && ((mouseButtonChanged & buttonMask) != 0 || mouseWheeled)) {
                            break processing;
                        }
                        // Touch screens have no initial hover
                        if (trickle && data.source() == MouseSource.TOUCH_SCREEN && mouseMoved) {
                            break processing;
                        }
                        handleMouseDown(data.button(), data.down());
                        mouseSource = data.source();
                        mouseButtonChanged |= buttonMask;
                    }
                    case GuiInputEventType.MOUSE_WHEEL -> {
                        // Trickling: stop if we got multiple actions on the event
                        if (trickle && (mouseMoved || mouseButtonChanged != 0)) {
                            break processing;
                        }
                        final GuiInputEvent.MouseWheel data =
                                (GuiInputEvent.MouseWheel) event.data();
                        handleMouseWheel(data.wheelX(), data.wheelY());
                        mouseSource = data.source();
                        mouseWheeled = true;
                    }
                    case GuiInputEventType.MOUSE_VIEWPORT -> {
                        final GuiInputEvent.Viewport data = (GuiInputEvent.Viewport) event.data();
                        handleMouseViewport(data.id());
                    }
                    case GuiInputEventType.KEY -> {
                        if (noKeyboard) {
                            break;
                        }
                        final GuiInputEvent.KeyPress data = (GuiInputEvent.KeyPress) event.data();
                        final int keyIndex = data.key().ordinal();
                        // Trickling: stop if we got multiple actions on the same key
                        if (trickle
                                && keysDown[keyIndex] != data.down()
                                && (keysChanged[keyIndex] || mouseButtonChanged != 0)) {
                            break processing;
                        }
                        final boolean keyIsPotentiallyForCharInput =
                                isKeyPotentiallyCharInput(getMergedModsFromKeys(), data.key());
                        if (trickleInterleavedNonCharKeysAndText
                                && textInputted
                                && !keyIsPotentiallyForCharInput) {
                            break processing;
                        }
                        // Analog changes alone don't count, so they don't block other events
                        if (keysDown[keyIndex] != data.down()) {
                            keyChanged = true;
                            keysChanged[keyIndex] = true;
                            if (trickleInterleavedNonCharKeysAndText
                                    && !keyIsPotentiallyForCharInput) {
                                keyChangedNonChar = true;
                            }
                        }
                        handleKey(data.key(), data.down(), data.analogValue());
                    }
                    case GuiInputEventType.TEXT -> {
                        if (noKeyboard) {
                            break;
                        }
                        // Trickling: stop if keys or the mouse have been interacted with
                        if (trickle && (mouseButtonChanged != 0 || mouseMoved || mouseWheeled)) {
                            break processing;
                        }
                        if (trickleInterleavedNonCharKeysAndText && keyChangedNonChar) {
                            break processing;
                        }
                        final GuiInputEvent.Text data = (GuiInputEvent.Text) event.data();
                        inputQueueCharacters.append(data.character());
                        if (trickleInterleavedNonCharKeysAndText) {
                            textInputted = true;
                        }
                    }
                    case GuiInputEventType.FOCUS -> {
                        // Processed in newFrame(), to give multi-viewport backends a chance to
                        // queue a focus loss and gain in the same frame
                        final GuiInputEvent.Focused data = (GuiInputEvent.Focused) event.data();
                        appFocusLost = !data.focused();
                    }
                    case GuiInputEventType.NONE -> {}
                }
                eventQueue.poll();
            }
        } finally {
            eventQueueLock.unlock();
        }

        // Clear the button state when the focus is lost, so e.g. releasing Alt after Alt+Tab
        // doesn't toggle the menu
        if (appFocusLost) {
            clearInputKeys();
            clearInputMouse();
        }
    }

    /**
     * Update the keyboard input information at the start of a frame. Called internally.
     *
     * @param deltaTime The time since the last frame, in milliseconds.
     */
    public void updateKeyboardInputs(long deltaTime) {
        // Update the mouse key aliases
        for (MouseButton button : MouseButton.values()) {
            if (button != MouseButton.NONE) {
                keysDown[Key.fromMouseButton(button).ordinal()] = mouseDown[button.index];
            }
        }
        keysDown[Key.MOUSE_WHEEL_X.ordinal()] = mouseWheelH != 0.0f;
        keysDown[Key.MOUSE_WHEEL_Y.ordinal()] = mouseWheel != 0.0f;

        keyMods = KeyModFlags.NONE;
        if (keyCtrl) {
            keyMods |= KeyModFlags.CTRL;
        }
        if (keyShift) {
            keyMods |= KeyModFlags.SHIFT;
        }
        if (keyAlt) {
            keyMods |= KeyModFlags.ALT;
        }
        if (keySuper) {
            keyMods |= KeyModFlags.SUPER;
        }

        for (int i = 0; i < keysDown.length; ++i) {
            keysDownDurationPrevious[i] = keysDownDuration[i];
            if (keysDown[i]) {
                keysDownDuration[i] = keysDownDuration[i] < 0 ? 0 : keysDownDuration[i] + deltaTime;
            } else {
                keysDownDuration[i] = -1;
            }
        }
    }

    /**
     * Update the mouse input information at the start of a frame. Called internally.
     *
     * @param time The current time, in milliseconds.
     * @param deltaTime The time since the last frame, in milliseconds.
     */
    public void updateMouseInputs(long time, long deltaTime) {
        boolean anyDown = false;
        for (int i = 0; i < MouseButton.COUNT; ++i) {
            anyDown = anyDown || mouseDown[i];
        }
        // When the mouse leaves the window and isn't dragging, we don't know where it is
        if (!mouseInsideWindow && !anyDown) {
            mousePosition.set(-Float.MAX_VALUE, -Float.MAX_VALUE);
        }

        // As a standard behavior, holding shift while using the vertical mouse wheel scrolls
        // horizontally. This isn't done on macOS, where the OS input layer handles it.
        mouseWheelRequestAxisSwap = keyShift && !configMacOSXBehaviors;

        if (IkGui.isMousePosValid(mousePosition)) {
            // Round mouse position to avoid spreading non-rounded positions
            mousePosition.set(
                    (float) Math.floor(mousePosition.x), (float) Math.floor(mousePosition.y));
        }

        if (IkGui.isMousePosValid(mousePosition) && IkGui.isMousePosValid(mousePositionPrevious)) {
            mouseDelta.set(mousePosition).sub(mousePositionPrevious);
        } else {
            mouseDelta.set(0, 0);
        }

        if (mouseDelta.lengthSquared() > 0) {
            mouseStationaryTimer = 0;
        } else {
            mouseStationaryTimer += deltaTime;
        }

        for (int i = 0; i < MouseButton.COUNT; ++i) {
            mouseClicked[i] = mouseDown[i] && mouseDownDuration[i] < 0;
            mouseClickedCount[i] = 0;
            mouseReleased[i] = !mouseDown[i] && mouseDownDuration[i] >= 0;
            if (mouseReleased[i]) {
                mouseReleasedTime[i] = time;
            }
            mouseDownDurationPrevious[i] = mouseDownDuration[i];
            if (mouseDown[i]) {
                mouseDownDuration[i] =
                        mouseDownDuration[i] < 0 ? 0 : mouseDownDuration[i] + deltaTime;
            } else {
                mouseDownDuration[i] = -1;
            }

            if (mouseClicked[i]) {
                boolean isRepeatedClick = false;
                if (time - mouseClickedTime[i] < mouseDoubleClickTime) {
                    float distanceSquared =
                            IkGui.isMousePosValid(mousePosition)
                                    ? mousePosition.distanceSquared(mouseClickedPosition[i])
                                    : 0.0f;
                    if (distanceSquared
                            < mouseDoubleClickMaxDistance * mouseDoubleClickMaxDistance) {
                        isRepeatedClick = true;
                    }
                }
                if (isRepeatedClick) {
                    mouseClickedLastCount[i] += 1;
                } else {
                    mouseClickedLastCount[i] = 1;
                }
                mouseClickedTime[i] = time;
                mouseClickedPosition[i].set(mousePosition);
                mouseClickedCount[i] = mouseClickedLastCount[i];
                mouseDragMaxDistanceAbsolute[i].set(0, 0);
                mouseDragMaxDistanceSquare[i] = 0;
            } else if (mouseDown[i]) {
                // Track the maximum distance from the click position, used for drag thresholds
                float deltaX = 0;
                float deltaY = 0;
                if (IkGui.isMousePosValid(mousePosition)) {
                    deltaX = mousePosition.x - mouseClickedPosition[i].x;
                    deltaY = mousePosition.y - mouseClickedPosition[i].y;
                }
                mouseDragMaxDistanceSquare[i] =
                        Math.max(mouseDragMaxDistanceSquare[i], deltaX * deltaX + deltaY * deltaY);
                mouseDragMaxDistanceAbsolute[i].set(
                        Math.max(mouseDragMaxDistanceAbsolute[i].x, Math.abs(deltaX)),
                        Math.max(mouseDragMaxDistanceAbsolute[i].y, Math.abs(deltaY)));
            }
        }
    }
}
