package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.enums.*;
import com.ikalagaming.graphics.frontend.gui.event.GuiInputEvent;
import com.ikalagaming.graphics.frontend.gui.flags.BackendFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ConfigFlags;
import com.ikalagaming.graphics.frontend.gui.flags.KeyModFlags;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector2f;

import java.util.ArrayDeque;
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

    /** Whether navigation captures the keyboard. */
    public boolean configNavCaptureKeyboard;

    /** Whether navigation sets the mosue position. */
    public boolean configNavMoveSetMousePosition;

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
     * Duration the mouse button has been down for, in milliseconds. Should probably not be modified
     * directly. 0 is just clicked. Indexes correspond to {@link MouseButton#index}.
     */
    public final long[] mouseDownDuration;

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

    /** Viewport that the mouse is hovering over. */
    public Viewport mouseHoveredViewport;

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

    /**
     * Set when we will want to capture keyboard inputs next frame, and not dispatch them to the
     * main application.
     */
    public boolean wantCaptureKeyboardNextFrame;

    /** Set when we want to capture mouse inputs and not dispatch them to the main application. */
    public boolean wantCaptureMouse;

    /**
     * Set when we will want to capture mouse inputs next frame, and not dispatch them to the main
     * application.
     */
    public boolean wantCaptureMouseNextFrame;

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

    /**
     * For mobile/console, we will want to display an on-screen keyboard for textual inputs next
     * frame.
     */
    public boolean wantTextInputNextFrame;

    public IkIO() {
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
        configNavCaptureKeyboard = false;
        configNavMoveSetMousePosition = false;
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
        mouseDownDuration = new long[MouseButton.COUNT];
        mouseDown = new boolean[MouseButton.COUNT];
        mouseDownOwned = new boolean[MouseButton.COUNT];
        mouseDownOwnedUnlessPopupClose = new boolean[MouseButton.COUNT];
        mouseDragMaxDistanceAbsolute = new Vector2f[MouseButton.COUNT];
        for (int i = 0; i < MouseButton.COUNT; ++i) {
            mouseDragMaxDistanceAbsolute[i] = new Vector2f(0, 0);
        }
        mouseDragMaxDistanceSquare = new float[MouseButton.COUNT];
        mouseDragThreshold = 6.0f;
        mouseHoveredViewport = null;
        mouseInsideWindow = false;
        mousePosition = new Vector2f(-Float.MAX_VALUE, -Float.MAX_VALUE);
        mouseReleased = new boolean[MouseButton.COUNT];
        mouseReleasedTime = new long[MouseButton.COUNT];
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
        wantCaptureKeyboardNextFrame = false;
        wantCaptureMouse = false;
        wantCaptureMouseNextFrame = false;
        wantCaptureMouseUnlessPopupClose = false;
        wantSaveIniSettings = false;
        wantTextInput = false;
        wantTextInputNextFrame = false;
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

    public void addFocusEvent(boolean focused) {
        if (!appAcceptingEvents) {
            return;
        }
        try {
            eventQueueLock.lock();
            GuiInputEvent event =
                    new GuiInputEvent(
                            GuiInputEventType.FOCUS,
                            GuiInputSource.NONE,
                            new GuiInputEvent.Focused(focused));
            eventQueue.add(event);
        } finally {
            eventQueueLock.unlock();
        }
    }

    public void addKeyEvent(Key key, boolean down) {
        if (!appAcceptingEvents) {
            return;
        }
        try {
            eventQueueLock.lock();
            GuiInputEvent event =
                    new GuiInputEvent(
                            GuiInputEventType.KEY,
                            GuiInputSource.NONE,
                            new GuiInputEvent.KeyPress(key, down, 0));
            eventQueue.add(event);
        } finally {
            eventQueueLock.unlock();
        }
    }

    public void addKeyAnalogEvent(Key key, boolean down, float analogValue) {
        if (!appAcceptingEvents) {
            return;
        }
        try {
            eventQueueLock.lock();
            GuiInputEvent event =
                    new GuiInputEvent(
                            GuiInputEventType.KEY,
                            GuiInputSource.NONE,
                            new GuiInputEvent.KeyPress(key, down, analogValue));
            eventQueue.add(event);
        } finally {
            eventQueueLock.unlock();
        }
    }

    public void addMousePosEvent(float x, float y) {
        if (!appAcceptingEvents) {
            return;
        }
        // TODO(ches) we can probably ignore most of these to be honest
        try {
            eventQueueLock.lock();
            GuiInputEvent event =
                    new GuiInputEvent(
                            GuiInputEventType.MOUSE_POSITION,
                            GuiInputSource.NONE,
                            new GuiInputEvent.MousePosition(x, y, mouseSource));
            eventQueue.add(event);
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
        try {
            eventQueueLock.lock();
            GuiInputEvent event =
                    new GuiInputEvent(
                            GuiInputEventType.MOUSE_BUTTON,
                            GuiInputSource.NONE,
                            new GuiInputEvent.MouseButton(button, down, mouseSource));
            eventQueue.add(event);
        } finally {
            eventQueueLock.unlock();
        }
    }

    public void addMouseWheelEvent(float wheelX, float wheelY) {
        if (!appAcceptingEvents) {
            return;
        }
        try {
            eventQueueLock.lock();
            GuiInputEvent event =
                    new GuiInputEvent(
                            GuiInputEventType.MOUSE_WHEEL,
                            GuiInputSource.NONE,
                            new GuiInputEvent.MouseWheel(wheelX, wheelY, mouseSource));
            eventQueue.add(event);
        } finally {
            eventQueueLock.unlock();
        }
    }

    public void addMouseViewportEvent(int id) {
        if (!appAcceptingEvents) {
            return;
        }
        try {
            eventQueueLock.lock();
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

    public void addInputCharacter(char c) {
        // TODO(ches) complete this
    }

    public void addInputCharacterUTF16(char c) {
        // TODO(ches) complete this
    }

    public void addInputCharactersUTF8(@NonNull String str) {
        // TODO(ches) complete this
    }

    public void setAppAcceptingEvents(boolean acceptingEvents) {
        // TODO(ches) complete this
        appAcceptingEvents = acceptingEvents;
    }

    /** Clear out (discard) everything in the event queue. */
    public void clearEventQueue() {
        try {
            eventQueueLock.lock();
            eventQueue.clear();
        } finally {
            eventQueueLock.unlock();
        }
    }

    /** Reset fields indicating state has changed this frame, in preparation for a new frame. */
    public void clearFrameSpecificValues() {
        mouseDelta.set(0, 0);
        for (int i = 0; i < MouseButton.COUNT; ++i) {
            mouseClicked[i] = false;
            mouseReleased[i] = false;
        }
    }

    public void clearInputKeys() {
        // TODO(ches) complete this
    }

    public void clearInputMouse() {
        // TODO(ches) complete this
    }

    public boolean getMouseDown(@NonNull MouseButton button) {
        return mouseDown[button.index];
    }

    public double getMouseClickedTime(@NonNull MouseButton button) {
        return mouseClickedTime[button.index];
    }

    public void setMouseClickedTime(@NonNull MouseButton button, long value) {
        mouseClickedTime[button.index] = value;
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

    public boolean getMouseDownOwnedUnlessPopupClose(int index) {
        // TODO(ches) complete this
        return false;
    }

    public float getMouseDownDuration(@NonNull MouseButton button) {
        return mouseDownDuration[button.index];
    }

    public float getMouseDragMaxDistanceSqr(int index) {
        // TODO(ches) complete this
        return 0;
    }

    private void handleFocus(boolean focused) {
        // TODO(ches) complete this
        if (!focused && !configDebugIgnoreFocusLoss) {
            appFocusLost = true;
        }
    }

    private void handleKey(@NonNull Key key, boolean down, float analogValue) {
        // TODO(ches) complete this
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
        final boolean oldValue = mouseDown[index];
        if (oldValue != value) {
            mouseDown[index] = value;
            if (value) {
                final long lastClick = mouseClickedTime[index];
                final long thisClick = IkGui.getContext().frameStartTime;
                final boolean repeatedClick =
                        (thisClick - lastClick > mouseDoubleClickTime)
                                && (mouseClickedPosition[index].distance(mousePosition)
                                        <= mouseDoubleClickMaxDistance);
                mouseClickedTime[index] = thisClick;
                mouseClicked[index] = true;
                mouseClickedPosition[index].set(mousePosition);

                if (repeatedClick) {
                    mouseClickedLastCount[index] += 1;
                } else {
                    mouseClickedLastCount[index] = 1;
                }
                mouseClickedCount[index] = mouseClickedLastCount[index];
                mouseDownDuration[index] = 0;
            } else {
                mouseReleasedTime[index] = IkGui.getContext().frameStartTime;
                mouseReleased[index] = true;
            }
            // TODO(ches) handle owned / popup logic
        }
    }

    private void handleMousePosition(float x, float y) {
        mouseStationaryTimer = 0;
        if (mouseInsideWindow) {
            // It's in a window now
            mousePosition.set(x, y);
            float displaceX = mousePosition.x - mousePositionPrevious.x;
            float displaceY = mousePosition.y - mousePositionPrevious.y;
            if (mousePositionPrevious.x != -Float.MAX_VALUE
                    && mousePositionPrevious.y != -Float.MAX_VALUE) {
                mouseDelta.add(displaceX, displaceY);
            } else {
                mouseDelta.set(0, 0);
            }
        } else {
            // It's not in a window now
            mousePosition.set(-Float.MAX_VALUE, -Float.MAX_VALUE);
            mouseDelta.set(0, 0);
        }
    }

    private void handleMouseViewport(int id) {
        // TODO(ches) complete this
    }

    public void handleMouseWheel(float wheelX, float wheelY) {
        if (mouseWheelRequestAxisSwap) {
            mouseWheel += wheelY;
            mouseWheelH += wheelX;
        } else {
            mouseWheel += wheelX;
            mouseWheelH += wheelY;
        }
    }

    /** Process input events, called internally. */
    public void processInputEvents() {
        try {
            eventQueueLock.lock();

            while (!eventQueue.isEmpty()) {
                GuiInputEvent event = eventQueue.poll();
                switch (event.type()) {
                    case GuiInputEventType.FOCUS:
                        {
                            GuiInputEvent.Focused data = (GuiInputEvent.Focused) event.data();
                            handleFocus(data.focused());
                        }
                        break;
                    case GuiInputEventType.KEY:
                        {
                            GuiInputEvent.KeyPress data = (GuiInputEvent.KeyPress) event.data();
                            handleKey(data.key(), data.down(), data.analogValue());
                        }
                        break;
                    case GuiInputEventType.MOUSE_BUTTON:
                        {
                            GuiInputEvent.MouseButton data =
                                    (GuiInputEvent.MouseButton) event.data();
                            handleMouseDown(data.button(), data.down());
                        }
                        break;
                    case GuiInputEventType.MOUSE_POSITION:
                        {
                            GuiInputEvent.MousePosition data =
                                    (GuiInputEvent.MousePosition) event.data();
                            handleMousePosition(data.posX(), data.posY());
                        }
                        break;
                    case GuiInputEventType.MOUSE_VIEWPORT:
                        {
                            GuiInputEvent.Viewport data = (GuiInputEvent.Viewport) event.data();
                            handleMouseViewport(data.id());
                        }
                        break;
                    case GuiInputEventType.MOUSE_WHEEL:
                        {
                            GuiInputEvent.MouseWheel data = (GuiInputEvent.MouseWheel) event.data();
                            handleMouseWheel(data.wheelX(), data.wheelY());
                        }
                        break;
                }
            }

        } finally {
            eventQueueLock.unlock();
        }
    }

    /** Update the mouse input information at the start of a frame. Called internally. */
    public void updateMouseInputs() {
        for (int index = 0; index < MouseButton.COUNT; index++) {
            if (mouseDown[index]) {
                mouseDownDuration[index] += deltaTime;
            } else {
                mouseReleasedTime[index] += deltaTime;
            }
        }
        if (mouseDelta.x == 0 && mouseDelta.y == 0) {
            mouseStationaryTimer += deltaTime;
        }
    }
}
