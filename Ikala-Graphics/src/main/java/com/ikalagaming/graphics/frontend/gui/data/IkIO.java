package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.flags.BackendFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ConfigFlags;
import com.ikalagaming.graphics.frontend.gui.flags.KeyModFlags;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Vector2f;

public class IkIO {
    // TODO(ches) add some kind of KeyData struct

    /** Whether we are accepting events. */
    @Getter private boolean appAcceptingEvents;

    /** Whether the app has lost focus. */
    @Getter private boolean appFocusLost;

    /**
     * @see BackendFlags
     */
    public int backendFlags;

    /**
     * @see ConfigFlags
     */
    public int configFlags;

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

    /** Save extra information with the .ini data. */
    public boolean configDebugIniSettings;

    /** Force every floating window display within a docking node. */
    public boolean configDockingAlwaysTabBar;

    /**
     * Disable window splitting, so docking is limited to merging windows together into tab bars.
     */
    public boolean configDockingNoSplit;

    /** Enable blinking cursor. */
    public boolean configInputTextCursorBlink;

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

    /** Request that we draw a cursor instead of the platform. */
    public boolean configMouseDrawCursor;

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

    /** The parent context. */
    @NonNull public final Context context;

    /** Time elapsed since last frame, in seconds. */
    public float deltaTime;

    /**
     * Main display density, for retina displays where coordinates are different from framebuffer
     * coordinates.
     */
    public final Vector2f displayFramebufferScale;

    /** Main display size, in pixels. Might change every frame. */
    public final Vector2f displaySize;

    /** One or more fonts loaded into a single texture. */
    @NonNull public FontAtlas fonts;

    /** Font to use on newFrame(). Using null will result the first font in the atlas. */
    public Font fontDefault;

    /** Path to the .ini file, set to null to disable automatic .ini loading/saving. */
    public String iniFilename;

    /** Minimum time between saving positions/sizes to .ini file, in seconds. */
    public float iniSavingRate;

    /** Whether the alt key is down. */
    public boolean keyAlt;

    /** Whether the control key is down. */
    public boolean keyCtrl;

    /**
     * Key mod flags, but merged into flags. Same as {@link #keyAlt}/{@link #keyCtrl}/{@link
     * #keyShift}/ {@link #keySuper}.
     */
    public int keyMods;

    /** When holding a key/button, time (in seconds) before it starts to repeat. */
    public float keyRepeatDelay;

    /** When holding a key/button, the rate (in seconds) at which it repeats. */
    public float keyRepeatRate;

    /** Whether the shift key is down. */
    public boolean keyShift;

    /** Whether the super key is down. */
    public boolean keySuper;

    /** Number of active windows. */
    public int metricsActiveWindows;

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

    /** Mouse delta, in pixels. Will be zero if either current or previous position are invalid. */
    public final Vector2f mouseDelta;

    /**
     * If the mouse has been double-clicked. Should probably not be modified directly. Indexes
     * correspond to {@link MouseButton#index}.
     */
    public final boolean[] mouseDoubleClicked;

    /** Distance threshold for validating a double click, in pixels. */
    public float mouseDoubleClickMaxDistance;

    /** Time for a double click, in milliseconds. */
    public long mouseDoubleClickTime;

    /**
     * Duration the mouse button has been down for, in milliseconds. Should probably not be modified
     * directly. 0 is just clicked. Indexes correspond to {@link MouseButton#index}.
     */
    public final long[] mouseDownDuration;

    /**
     * Previous duration the mouse button was down for, in milliseconds. Should probably not be
     * modified directly. Indexes correspond to {@link MouseButton#index}.
     */
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

    /** Viewport that the mouse is hovering over. */
    public Viewport mouseHoveredViewport;

    /** The mouse is currently inside a (any) window. */
    public boolean mouseInsideWindow;

    /** The mouse was inside a (any) window the previous time we checked. */
    public boolean mouseInsideWindowPrevious;

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

    /** Distance threshold before considering a mouse drag, in pixels. */
    public float mouseDragThreshold;

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

    /** Set when we want to use keyboard inputs and not dispatch them to the main application. */
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

    public IkIO(@NonNull Context context) {
        appAcceptingEvents = true;
        appFocusLost = false;
        backendFlags = BackendFlags.NONE;
        configFlags = ConfigFlags.NONE;
        configDebugBeginReturnValueLoop = false;
        configDebugBeginReturnValueOnce = false;
        configDebugIgnoreFocusLoss = false;
        configDebugIniSettings = false;
        configDockingAlwaysTabBar = false;
        configDockingNoSplit = false;
        configInputTextCursorBlink = true;
        configInputTrickleEventQueue = true;
        configMacOSXBehaviors = false;
        configMemoryCompactTimer = 60.0f;
        configMouseDrawCursor = false;
        configViewportsNoAutoMerge = false;
        configViewportsNoDecoration = true;
        configViewportsNoDefaultParent = true;
        configViewportsNoTaskBarIcon = false;
        configViewportsPlatformFocusSetsWindowFocus = true;
        configWindowsMoveFromTitleBarOnly = false;
        configWindowsResizeFromEdges = true;
        this.context = context;
        deltaTime = 1.0f / 60.0f;
        displayFramebufferScale = new Vector2f(1, 1);
        displaySize = new Vector2f(0, 0);
        fonts = new FontAtlas(); // TODO(ches) update this
        fontDefault = null;
        iniFilename = "ikgui.ini";
        iniSavingRate = 5.0f;
        keyAlt = false;
        keyCtrl = false;
        keyMods = KeyModFlags.NONE;
        keyRepeatDelay = 0.250f;
        keyRepeatRate = 0.050f;
        keyShift = false;
        keySuper = false;
        metricsActiveWindows = 0;
        metricsRenderIndices = 0;
        metricsRenderVertices = 0;
        metricsRenderWindows = 0;
        mouseClicked = new boolean[MouseButton.COUNT];
        mouseClickedPosition = new Vector2f[MouseButton.COUNT];
        mouseClickedCount = new short[MouseButton.COUNT];
        mouseClickedLastCount = new short[MouseButton.COUNT];
        mouseClickedTime = new long[MouseButton.COUNT];
        mouseCtrlLeftAsRightClick = false;
        mouseDelta = new Vector2f(0, 0);
        mouseDoubleClicked = new boolean[MouseButton.COUNT];
        mouseDoubleClickMaxDistance = 6.0f;
        mouseDoubleClickTime = 300;
        mouseDownDuration = new long[MouseButton.COUNT];
        mouseDownDurationPrevious = new long[MouseButton.COUNT];
        mouseDown = new boolean[MouseButton.COUNT];
        mouseDownOwned = new boolean[MouseButton.COUNT];
        mouseDownOwnedUnlessPopupClose = new boolean[MouseButton.COUNT];
        mouseDragMaxDistanceAbsolute = new Vector2f[MouseButton.COUNT];
        mouseDragMaxDistanceSquare = new float[MouseButton.COUNT];
        mouseDragThreshold = 6.0f;
        mouseReleased = new boolean[MouseButton.COUNT];
        mouseReleasedTime = new long[MouseButton.COUNT];
        mouseHoveredViewport = null;
        mouseInsideWindow = false;
        mouseInsideWindowPrevious = false;
        mousePosition = new Vector2f(-Float.MAX_VALUE, -Float.MAX_VALUE);
        mousePositionPrevious = new Vector2f(-Float.MAX_VALUE, -Float.MAX_VALUE);
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

    public boolean hasBackendFlags(int flags) {
        return (backendFlags & flags) != 0;
    }

    public void addKeyEvent(int key, boolean down) {
        // TODO(ches) complete this
    }

    public void addKeyAnalogEvent(int key, boolean down, float v) {
        // TODO(ches) complete this
    }

    public void addMousePosEvent(float x, float y) {
        // TODO(ches) complete this

        if (mouseInsideWindow) {
            // It's in a window now
            float displaceX = mousePosition.x - mousePositionPrevious.x;
            float displaceY = mousePosition.y - mousePositionPrevious.y;

            mousePositionPrevious.set(mousePosition);
            mousePosition.set(x, y);
            if (mouseInsideWindowPrevious) {
                mouseDelta.set(displaceX, displaceY);
            } else {
                mouseDelta.set(0, 0);
            }
        } else {
            // TODO(ches) Check for being in the window
            // It's not in a window now
            mousePositionPrevious.set(mousePosition);
            mousePosition.set(-Float.MAX_VALUE, -Float.MAX_VALUE);
            mouseDelta.set(0, 0);
        }

        mouseInsideWindowPrevious = mouseInsideWindow;
    }

    public void addMouseButtonEvent(int button, boolean down) {
        // TODO(ches) complete this
    }

    public void addMouseButtonEvent(@NonNull MouseButton button, boolean down) {
        addMouseButtonEvent(button.index, down);
    }

    public void addMouseWheelEvent(float whX, float whY) {
        // TODO(ches) complete this
    }

    public void addMouseSourceEvent(int source) {
        // TODO(ches) complete this
    }

    public void addMouseViewportEvent(int id) {
        // TODO(ches) complete this
    }

    public void addFocusEvent(boolean focused) {
        if (!focused) {
            appFocusLost = true;
        }
        // TODO(ches) complete this
    }

    public void addInputCharacter(int c) {
        // TODO(ches) complete this
    }

    public void addInputCharacterUTF16(short c) {
        // TODO(ches) complete this
    }

    public void addInputCharactersUTF8(String str) {
        // TODO(ches) complete this
    }

    public void setAppAcceptingEvents(boolean acceptingEvents) {
        // TODO(ches) complete this
        appAcceptingEvents = acceptingEvents;
    }

    public void clearEventsQueue() {
        // TODO(ches) complete this
    }

    public void clearInputKeys() {
        // TODO(ches) complete this
    }

    public void clearInputMouse() {
        // TODO(ches) complete this
    }

    public boolean getMouseDown(int index) {
        if (index < 0 || index >= MouseButton.COUNT) {
            return false;
        }
        return mouseDown[index];
    }

    public boolean getMouseDown(@NonNull MouseButton button) {
        return getMouseDown(button.index);
    }

    public void setMouseDown(int index, boolean value) {
        if (index < 0 || index >= MouseButton.COUNT) {
            return;
        }
        final boolean oldValue = mouseDown[index];
        mouseDown[index] = value;
        if (oldValue != value) {
            // TODO(ches) handle clicking logic, timers
        }
    }

    public void setMouseDown(@NonNull MouseButton button, boolean value) {
        setMouseDown(button.index, value);
    }

    public long getMouseClickedTime(int index) {
        if (index < 0 || index >= MouseButton.COUNT) {
            return 0;
        }
        return mouseClickedTime[index];
    }

    public double getMouseClickedTime(@NonNull MouseButton button) {
        return getMouseClickedTime(button.index);
    }

    public void setMouseClickedTime(int index, long value) {
        if (index < 0 || index >= MouseButton.COUNT) {
            return;
        }
        mouseClickedTime[index] = value;
    }

    public boolean getMouseClicked(int idx) {
        // TODO(ches) complete this
        return false;
    }

    public void setMouseClicked(int idx, boolean value) {
        // TODO(ches) complete this
    }

    public boolean getMouseDoubleClicked(int idx) {
        // TODO(ches) complete this
        return false;
    }

    public void setMouseDoubleClicked(int idx, boolean value) {
        // TODO(ches) complete this
    }

    public int getMouseClickedCount(int idx) {
        // TODO(ches) complete this
        return 0;
    }

    public void setMouseClickedCount(int idx, int value) {
        // TODO(ches) complete this
    }

    public int getMouseClickedLastCount(int idx) {
        // TODO(ches) complete this
        return 0;
    }

    public void setMouseClickedLastCount(int idx, int value) {
        // TODO(ches) complete this
    }

    public boolean getMouseReleased(int idx) {
        // TODO(ches) complete this
        return false;
    }

    public void setMouseReleased(int idx, boolean value) {
        // TODO(ches) complete this
    }

    public boolean getMouseDownOwned(int idx) {
        // TODO(ches) complete this
        return false;
    }

    public void setMouseDownOwned(int idx, boolean value) {
        // TODO(ches) complete this
    }

    public boolean getMouseDownOwnedUnlessPopupClose(int idx) {
        // TODO(ches) complete this
        return false;
    }

    public void setMouseDownOwnedUnlessPopupClose(int idx, boolean value) {
        // TODO(ches) complete this
    }

    public float getMouseDownDuration(int idx) {
        // TODO(ches) complete this
        return 0;
    }

    public void setMouseDownDuration(int idx, float value) {
        // TODO(ches) complete this
    }

    public float getMouseDownDurationPrev(int idx) {
        // TODO(ches) complete this
        return 0;
    }

    public void setMouseDownDurationPrev(int idx, float value) {
        // TODO(ches) complete this
    }

    public float getMouseDragMaxDistanceSqr(int idx) {
        // TODO(ches) complete this
        return 0;
    }

    public void setMouseDragMaxDistanceSqr(int idx, float value) {
        // TODO(ches) complete this
    }
}
