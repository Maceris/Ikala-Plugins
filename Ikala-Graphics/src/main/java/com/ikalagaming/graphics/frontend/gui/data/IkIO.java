package com.ikalagaming.graphics.frontend.gui.data;

import com.ikalagaming.graphics.frontend.gui.flags.BackendFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ConfigFlags;
import com.ikalagaming.graphics.frontend.gui.flags.KeyModFlags;

import lombok.Getter;
import lombok.NonNull;
import org.joml.Vector2f;

public class IkIO {

    @Getter private boolean appAcceptingEvents;

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
     * Used for OS X, set to true when the current click was a ctrl+click that simulated a right
     * click.
     */
    public boolean mouseCtrlLeftAsRightClick;

    /** Mouse delta, in pixels. Will be zero if either current or previous position are invalid. */
    public final Vector2f mouseDelta;

    /** Distance threshold for validating a double-click, in pixels. */
    public float mouseDoubleClickMaxDist;

    /** Time for a double click, in seconds. */
    public float mouseDoubleClickTime;

    /** Distance threshold before considering a mouse drag, in pixels. */
    public float mouseDragThreshold;

    /** Viewport that the mouse is hovering over. */
    public Viewport mouseHoveredViewport;

    /**
     * Mouse position, in pixels. Set to (-{@link Float#MAX_VALUE}, -{@link Float#MAX_VALUE}) if
     * mouse is unavailable.
     */
    public final Vector2f mousePosition;

    /** Previous mouse position. */
    public final Vector2f mousePositionPrevious;

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
        mouseCtrlLeftAsRightClick = false;
        mouseDelta = new Vector2f(0, 0);
        mouseDoubleClickMaxDist = 6.0f;
        mouseDoubleClickTime = 0.30f;
        mouseDragThreshold = 6.0f;
        mouseHoveredViewport = null;
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
    }

    public void addMouseButtonEvent(int button, boolean down) {
        // TODO(ches) complete this
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

    public boolean[] getMouseDown() {
        // TODO(ches) complete this
        return null;
    }

    public boolean getMouseDown(int idx) {
        // TODO(ches) complete this
        return false;
    }

    public void setMouseDown(boolean[] value) {
        // TODO(ches) complete this
    }

    public void setMouseDown(int idx, boolean value) {
        // TODO(ches) complete this
    }

    public Object[] getKeysData() {
        // TODO(ches) complete this, add some kind of KeyData struct
        return null;
    }

    public void setKeysData(Object[] value) {
        // TODO(ches) complete this
    }

    public Vector2f[] getMouseClickedPos() {
        // TODO(ches) complete this
        return null;
    }

    public void setMouseClickedPos(Vector2f[] value) {
        // TODO(ches) complete this
    }

    public double[] getMouseClickedTime() {
        // TODO(ches) complete this
        return null;
    }

    public double getMouseClickedTime(int idx) {
        // TODO(ches) complete this
        return 0;
    }

    public void setMouseClickedTime(double[] value) {
        // TODO(ches) complete this
    }

    public void setMouseClickedTime(int idx, double value) {
        // TODO(ches) complete this
    }

    public boolean[] getMouseClicked() {
        // TODO(ches) complete this
        return null;
    }

    public boolean getMouseClicked(int idx) {
        // TODO(ches) complete this
        return false;
    }

    public void setMouseClicked(boolean[] value) {
        // TODO(ches) complete this
    }

    public void setMouseClicked(int idx, boolean value) {
        // TODO(ches) complete this
    }

    public boolean[] getMouseDoubleClicked() {
        // TODO(ches) complete this
        return null;
    }

    public boolean getMouseDoubleClicked(int idx) {
        // TODO(ches) complete this
        return false;
    }

    public void setMouseDoubleClicked(boolean[] value) {
        // TODO(ches) complete this
    }

    public void setMouseDoubleClicked(int idx, boolean value) {
        // TODO(ches) complete this
    }

    public int[] getMouseClickedCount() {
        // TODO(ches) complete this
        return null;
    }

    public int getMouseClickedCount(int idx) {
        // TODO(ches) complete this
        return 0;
    }

    public void setMouseClickedCount(int[] value) {
        // TODO(ches) complete this
    }

    public void setMouseClickedCount(int idx, int value) {
        // TODO(ches) complete this
    }

    public int[] getMouseClickedLastCount() {
        // TODO(ches) complete this
        return null;
    }

    public int getMouseClickedLastCount(int idx) {
        // TODO(ches) complete this
        return 0;
    }

    public void setMouseClickedLastCount(int[] value) {
        // TODO(ches) complete this
    }

    public void setMouseClickedLastCount(int idx, int value) {
        // TODO(ches) complete this
    }

    public boolean[] getMouseReleased() {
        // TODO(ches) complete this
        return null;
    }

    public boolean getMouseReleased(int idx) {
        // TODO(ches) complete this
        return false;
    }

    public void setMouseReleased(boolean[] value) {
        // TODO(ches) complete this
    }

    public void setMouseReleased(int idx, boolean value) {
        // TODO(ches) complete this
    }

    public boolean[] getMouseDownOwned() {
        // TODO(ches) complete this
        return null;
    }

    public boolean getMouseDownOwned(int idx) {
        // TODO(ches) complete this
        return false;
    }

    public void setMouseDownOwned(boolean[] value) {
        // TODO(ches) complete this
    }

    public void setMouseDownOwned(int idx, boolean value) {
        // TODO(ches) complete this
    }

    public boolean[] getMouseDownOwnedUnlessPopupClose() {
        // TODO(ches) complete this
        return null;
    }

    public boolean getMouseDownOwnedUnlessPopupClose(int idx) {
        // TODO(ches) complete this
        return false;
    }

    public void setMouseDownOwnedUnlessPopupClose(boolean[] value) {
        // TODO(ches) complete this
    }

    public void setMouseDownOwnedUnlessPopupClose(int idx, boolean value) {
        // TODO(ches) complete this
    }

    public float[] getMouseDownDuration() {
        // TODO(ches) complete this
        return null;
    }

    public float getMouseDownDuration(int idx) {
        // TODO(ches) complete this
        return 0;
    }

    public void setMouseDownDuration(float[] value) {
        // TODO(ches) complete this
    }

    public void setMouseDownDuration(int idx, float value) {
        // TODO(ches) complete this
    }

    public float[] getMouseDownDurationPrev() {
        // TODO(ches) complete this
        return null;
    }

    public float getMouseDownDurationPrev(int idx) {
        // TODO(ches) complete this
        return 0;
    }

    public void setMouseDownDurationPrev(float[] value) {
        // TODO(ches) complete this
    }

    public void setMouseDownDurationPrev(int idx, float value) {
        // TODO(ches) complete this
    }

    public Vector2f[] getMouseDragMaxDistanceAbs() {
        // TODO(ches) complete this
        return null;
    }

    public void setMouseDragMaxDistanceAbs(Vector2f[] value) {
        // TODO(ches) complete this
    }

    public float[] getMouseDragMaxDistanceSqr() {
        // TODO(ches) complete this
        return null;
    }

    public float getMouseDragMaxDistanceSqr(int idx) {
        // TODO(ches) complete this
        return 0;
    }

    public void setMouseDragMaxDistanceSqr(float[] value) {
        // TODO(ches) complete this
    }

    public void setMouseDragMaxDistanceSqr(int idx, float value) {
        // TODO(ches) complete this
    }
}
