package com.ikalagaming.graphics.frontend.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.DockNode;
import com.ikalagaming.graphics.frontend.gui.data.IkBoolean;
import com.ikalagaming.graphics.frontend.gui.data.LastItemData;
import com.ikalagaming.graphics.frontend.gui.data.TabBar;
import com.ikalagaming.graphics.frontend.gui.data.TabItem;
import com.ikalagaming.graphics.frontend.gui.data.Table;
import com.ikalagaming.graphics.frontend.gui.data.TableSortSpecs;
import com.ikalagaming.graphics.frontend.gui.data.Viewport;
import com.ikalagaming.graphics.frontend.gui.data.Window;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.Direction;
import com.ikalagaming.graphics.frontend.gui.enums.GuiInputSource;
import com.ikalagaming.graphics.frontend.gui.enums.Key;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.enums.SortDirection;
import com.ikalagaming.graphics.frontend.gui.flags.BackendFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ConfigFlags;
import com.ikalagaming.graphics.frontend.gui.flags.HoveredFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ItemStatusFlags;
import com.ikalagaming.graphics.frontend.gui.flags.KeyModFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ScrollFlags;
import com.ikalagaming.graphics.frontend.gui.flags.TableFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ViewportFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFocusRequestFlags;
import com.ikalagaming.graphics.frontend.gui.util.Hash;
import com.ikalagaming.graphics.frontend.gui.util.KeyChord;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;

import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Drives a headless IkGui context for tests, scripting input the way a user would: finding items by
 * path, moving the mouse to them, clicking, and pressing keys. Each action runs as many frames as
 * it needs, so tests read like a sequence of user actions followed by checks.
 *
 * <p>This mirrors the scripting style of the Dear ImGui Test Engine, so tests from the Dear ImGui
 * test suite translate closely, but it is a separate and much simpler implementation.
 *
 * <p>References are paths of IDs separated by "/", resolved like {@code pushID()} calls:
 *
 * <ul>
 *   <li>"Window/Item" is relative to the reference from {@link #setRef(String)}.
 *   <li>"//Window/Item" ignores the current reference.
 *   <li>"//$FOCUSED/Item" is relative to the focused window.
 *   <li>"$$5" hashes the integer 5, as {@code pushID(5)} would.
 *   <li>"\\/" is a literal slash inside an ID.
 * </ul>
 */
class IkGuiTestContext implements TestEngineHooks {
    /** What we know about an item from the hooks. */
    static class ItemInfo {
        /** The ID of the item. */
        int id;

        /** The label of the item, if a widget reported one. */
        String label;

        /** The window the item was submitted in. */
        Window window;

        /** The bounding box of the item, in screen coordinates. */
        final RectFloat rect = new RectFloat();

        /** The visible part of the item, clipped by the clip rectangle it was submitted with. */
        final RectFloat rectClipped = new RectFloat();

        /** The navigation layer the item is in. */
        int navLayer;

        /** The item flags. */
        int itemFlags;

        /** The status flags, including the test engine only flags like checked and opened. */
        int statusFlags;

        /** The frame the item was last submitted in. */
        int frame = -1;

        /**
         * Check status flags of the item.
         *
         * @param flags The status flags to check.
         * @return True if all the flags are set.
         * @see ItemStatusFlags
         */
        boolean has(int flags) {
            return (statusFlags & flags) == flags;
        }
    }

    /** Counts of the last item's status over frames, queried by UI code after an item. */
    static class ItemStatus {
        int retValue;
        int hovered;
        int hoveredAllowDisabled;
        int active;
        int focused;
        int clicked;
        int visible;
        int edited;
        int activated;
        int deactivated;
        int deactivatedAfterEdit;

        /** Reset all the counts. */
        void clear() {
            retValue = 0;
            hovered = 0;
            hoveredAllowDisabled = 0;
            active = 0;
            focused = 0;
            clicked = 0;
            visible = 0;
            edited = 0;
            activated = 0;
            deactivated = 0;
            deactivatedAfterEdit = 0;
        }

        /**
         * Set the counts to the status of the last item this frame.
         *
         * @param ret The return value of the item.
         */
        void querySet(boolean ret) {
            clear();
            queryInc(ret);
        }

        /**
         * Add the status of the last item this frame to the counts.
         *
         * @param ret The return value of the item.
         */
        void queryInc(boolean ret) {
            retValue += ret ? 1 : 0;
            hovered += IkGui.isItemHovered() ? 1 : 0;
            hoveredAllowDisabled += IkGui.isItemHovered(HoveredFlags.ALLOW_WHEN_DISABLED) ? 1 : 0;
            active += IkGui.isItemActive() ? 1 : 0;
            focused += IkGui.isItemFocused() ? 1 : 0;
            clicked += IkGui.isItemClicked() ? 1 : 0;
            visible += IkGui.isItemVisible() ? 1 : 0;
            edited += IkGui.isItemEdited() ? 1 : 0;
            activated += IkGui.isItemActivated() ? 1 : 0;
            deactivated += IkGui.isItemDeactivated() ? 1 : 0;
            deactivatedAfterEdit += IkGui.isItemDeactivatedAfterEdit() ? 1 : 0;
        }
    }

    /** The name of the demo window, which upstream calls "Dear ImGui Demo". */
    static final String DEMO = "IkGui Demo Window";

    /** Whether the test suite app shows the demo window. */
    final IkBoolean showDemoWindow = new IkBoolean(true);

    /**
     * Show the windows of the Dear ImGui test suite app, which upstream tests run on top of: the
     * demo window and a "Hello, world!" window.
     */
    void showApp() {
        if (showDemoWindow.get()) {
            IkGuiDemo.showDemoWindow(showDemoWindow);
        }
        IkGui.begin("Hello, world!");
        IkGui.text("This is some useful text.");
        IkGui.checkbox("Demo Window", showDemoWindow);
        IkGui.checkbox("Another Window", showAnotherWindow);
        IkGui.sliderFloat("float", appFloat, 0.0f, 1.0f);
        IkGui.colorEdit3("clear color", appClearColor);
        if (IkGui.button("Button")) {
            appCounter++;
        }
        IkGui.sameLine();
        IkGui.text("counter = " + appCounter);
        IkGui.end();

        if (showAnotherWindow.get()) {
            IkGui.begin("Another Window", showAnotherWindow);
            IkGui.text("Hello from another window!");
            if (IkGui.button("Close Me")) {
                showAnotherWindow.set(false);
            }
            IkGui.end();
        }
    }

    /** Whether the test suite app shows its second window. */
    final IkBoolean showAnotherWindow = new IkBoolean(false);

    /** The value of the app's "float" slider. */
    final float[] appFloat = {0.0f};

    /** The value of the app's "clear color" color edit. */
    final float[] appClearColor = {0.45f, 0.55f, 0.60f, 1.00f};

    /** How many times the app's button was pressed. */
    int appCounter = 0;

    /** The time each frame takes, in milliseconds. */
    static final long FRAME_TIME = 16;

    /** How many frames to wait for an item to appear before failing. */
    private static final int ITEM_WAIT_FRAMES = 4;

    /** The context being driven. */
    final Context context;

    /** The UI code to run every frame. */
    private Runnable gui = () -> {};

    /** Items seen through the hooks, by ID. */
    private final Map<Integer, ItemInfo> items = new HashMap<>();

    /** The reference path, or null when the reference is an ID or empty. */
    private String refPath;

    /** The base ID for relative references. */
    private int refID;

    /**
     * How item actions are performed. With the keyboard, clicks move the navigation cursor to the
     * item and activate it instead.
     */
    private GuiInputSource inputMode = GuiInputSource.MOUSE;

    /**
     * Whether a frame has been started and not rendered yet. Like upstream, test code runs in the
     * middle of each frame, after the UI code and before the frame ends, so it sees state like the
     * mouse wheel before the end of the frame clears it.
     */
    private boolean frameOpen;

    /** The fake clock for the context, in milliseconds. */
    private long fakeTime = 1000;

    /** The current mouse position. */
    private final Vector2f mousePosition = new Vector2f(-1, -1);

    /**
     * Create a new context with test friendly settings, driven by this test context. Call {@link
     * #destroy()} when done.
     */
    /** The fake platform backend, when multiple viewports are enabled. */
    private FakeViewportPlatform platform;

    /** Platform events to apply before the next frame starts. */
    private final java.util.ArrayDeque<Runnable> platformEvents = new java.util.ArrayDeque<>();

    IkGuiTestContext() {
        context = IkGui.createContext();
        // Don't read or write an .ini file
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 800);
        context.io.mouseInsideWindow = true;
        // Match the metrics of the default font of the Dear ImGui test suite, a 13 pixel font
        context.dpiScaleScreen = context.dpiScaleFont;
        context.fontSize = 13;
        // The same configuration as the Dear ImGui test suite app
        context.io.configFlags |= ConfigFlags.NAV_ENABLE_KEYBOARD | ConfigFlags.DOCKING_ENABLE;
        context.timeSource = () -> fakeTime;
        context.testEngine = this;
        context.testEngineHookItems = true;
    }

    /** Destroy the context. */
    void destroy() {
        if (frameOpen) {
            IkGui.render();
            frameOpen = false;
        }
        if (platform != null) {
            IkGui.destroyPlatformWindows();
        }
        IkGui.destroyContext();
    }

    /**
     * Enable multiple viewports, with a fake platform backend. The main viewport is at the origin
     * of the desktop, so positions are the same as without viewports. Call this before the first
     * frame.
     */
    void enableViewports() {
        context.io.configFlags |= ConfigFlags.VIEWPORTS_ENABLE;
        platform = new FakeViewportPlatform(context, 0, 0);
    }

    /**
     * Move a platform window, like the OS or the user moving it outside of the GUI.
     *
     * @param viewport The viewport of the platform window.
     * @param x The new x position.
     * @param y The new y position.
     */
    void viewportPlatformSetWindowPos(Viewport viewport, float x, float y) {
        assertNotNull(platform, "Viewports aren't enabled");
        platformEvents.add(
                () -> {
                    platform.get(viewport).position.set(x, y);
                    viewport.platformRequestMove = true;
                });
        yieldFrames(2);
    }

    /**
     * Give a platform window the OS focus, like the user clicking on it outside of the GUI.
     *
     * @param viewport The viewport of the platform window.
     */
    void viewportPlatformSetWindowFocus(Viewport viewport) {
        assertNotNull(platform, "Viewports aren't enabled");
        platformEvents.add(() -> platform.focus(viewport));
        yieldFrames(2);
    }

    /**
     * Close a platform window, like the user clicking its OS close button.
     *
     * @param viewport The viewport of the platform window.
     */
    void viewportPlatformCloseWindow(Viewport viewport) {
        assertNotNull(platform, "Viewports aren't enabled");
        platformEvents.add(() -> viewport.platformRequestClose = true);
        yieldFrames(2);
    }

    /**
     * Click on an empty area of a viewport, outside any window.
     *
     * @param button The mouse button.
     * @param viewport The viewport.
     */
    void mouseClickOnVoid(MouseButton button, Viewport viewport) {
        for (float y = viewport.position.y + viewport.size.y - 2;
                y > viewport.position.y;
                y -= 20) {
            for (float x = viewport.position.x + viewport.size.x - 2;
                    x > viewport.position.x;
                    x -= 20) {
                if (!anyWindowContains(x, y) && !otherViewportContains(viewport, x, y)) {
                    mouseMoveToPos(x, y);
                    mouseClick(button);
                    return;
                }
            }
        }
        fail("Unable to find an empty area to click on in viewport " + viewport.id);
    }

    private boolean otherViewportContains(Viewport viewport, float x, float y) {
        for (Viewport other : context.viewports) {
            // The main viewport is behind the others
            if (other != viewport
                    && other != context.mainViewport
                    && x >= other.position.x
                    && y >= other.position.y
                    && x < other.position.x + other.size.x
                    && y < other.position.y + other.size.y) {
                return true;
            }
        }
        return false;
    }

    /**
     * Set the UI code to run every frame, and run a frame.
     *
     * @param gui The UI code.
     */
    void setGui(Runnable gui) {
        this.gui = gui;
        // Run the UI once before the test continues, so its windows exist
        yieldFrame();
    }

    // ---------------------------------------------------------------------------------------------
    // Hooks
    // ---------------------------------------------------------------------------------------------

    @Override
    public void itemAdd(int id, RectFloat bb, LastItemData itemData) {
        final ItemInfo info = items.computeIfAbsent(id, key -> new ItemInfo());
        final Window window = context.windowCurrent;
        info.id = id;
        info.window = window;
        info.rect.set(bb);
        info.rectClipped.set(bb);
        if (window != null) {
            info.rectClipped.clipWithFull(window.rectCurrentClip);
        }
        info.navLayer = window != null ? window.navLayerCurrent : 0;
        info.itemFlags = itemData != null ? itemData.itemFlags : 0;
        info.statusFlags = itemData != null ? itemData.statusFlags : 0;
        info.frame = context.frameCount;
    }

    @Override
    public void itemInfo(int id, String label, int statusFlags) {
        final ItemInfo info = items.computeIfAbsent(id, key -> new ItemInfo());
        info.id = id;
        info.label = label;
        info.statusFlags = statusFlags;
    }

    // ---------------------------------------------------------------------------------------------
    // Frames and time
    // ---------------------------------------------------------------------------------------------

    /** Run a frame. */
    void yieldFrame() {
        yieldFrame(FRAME_TIME);
    }

    /**
     * Run a frame that takes some amount of time.
     *
     * @param frameTime How long the frame takes, in milliseconds.
     */
    private void yieldFrame(long frameTime) {
        // Finish the open frame
        if (frameOpen) {
            IkGui.render();
            if (platform != null) {
                IkGui.updatePlatformWindows();
                IkGui.renderPlatformWindowsDefault();
            }
        }
        // Platform events arrive between frames, like a backend polling them before newFrame()
        while (!platformEvents.isEmpty()) {
            platformEvents.removeFirst().run();
        }
        fakeTime += frameTime;
        IkGui.newFrame();
        gui.run();
        frameOpen = true;
    }

    /**
     * The frame that the items seen through the hooks were last submitted in.
     *
     * @return The frame count of the last frame the UI code ran in.
     */
    private int lastSubmittedFrame() {
        return context.frameCount;
    }

    /**
     * Run some frames.
     *
     * @param count The number of frames.
     */
    void yieldFrames(int count) {
        for (int i = 0; i < count; ++i) {
            yieldFrame();
        }
    }

    /**
     * Run frames of a fixed length until exactly some time has passed, like upstream's
     * SleepNoSkip(). Useful for precise timing, like key repeats.
     *
     * @param seconds The time to wait, in seconds.
     * @param frameSeconds The length of each frame, in seconds.
     */
    void sleepNoSkip(float seconds, float frameSeconds) {
        long remaining = Math.round(seconds * 1000.0);
        final long step = Math.max(1, Math.round(frameSeconds * 1000.0));
        while (remaining > 0) {
            final long frameTime = Math.min(step, remaining);
            yieldFrame(frameTime);
            remaining -= frameTime;
        }
    }

    /**
     * Run frames until some time has passed.
     *
     * @param seconds The time to wait, in seconds.
     */
    void sleep(float seconds) {
        final long end = context.time + (long) (seconds * 1000.0f);
        while (context.time < end) {
            yieldFrame();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // References
    // ---------------------------------------------------------------------------------------------

    /**
     * Set the base reference for relative paths, and uncollapse its window if it is one.
     *
     * @param ref The reference path.
     */
    void setRef(String ref) {
        refPath = ref;
        refID = getID(ref, 0);
        final Window window = getWindowByRef("");
        if (window != null && window.collapsed) {
            IkGuiImplWindows.setWindowCollapsed(window, false, Condition.ALWAYS);
            yieldFrame();
        }
    }

    /**
     * Set the base reference for relative paths to an ID.
     *
     * @param id The ID.
     */
    void setRef(int id) {
        refPath = null;
        refID = id;
    }

    /**
     * Set the base reference to a window.
     *
     * @param window The window.
     */
    void setRef(Window window) {
        setRef(window.id);
    }

    /**
     * Calculate the ID of a reference, relative to the current reference.
     *
     * @param ref The reference path.
     * @return The ID.
     */
    int getID(String ref) {
        return getID(ref, refID);
    }

    /**
     * Calculate the ID of a reference path.
     *
     * @param ref The reference path.
     * @param seed The ID that relative paths start from.
     * @return The ID.
     */
    int getID(String ref, int seed) {
        final int wildcard = ref.indexOf("**/");
        if (wildcard >= 0) {
            // Wait a few frames for the item to show up, for example in a popup that just opened
            for (int i = 0; ; ++i) {
                final Integer id =
                        findWildcardID(
                                ref.substring(0, wildcard), ref.substring(wildcard + 3), seed);
                if (id != null) {
                    return id;
                }
                if (i >= ITEM_WAIT_FRAMES) {
                    fail("Unable to find an item matching " + ref);
                }
                yieldFrame();
            }
        }
        String path = ref;
        if (path.startsWith("//$FOCUSED")) {
            path = path.substring("//$FOCUSED".length());
            assertNotNull(context.navFocusedWindow, "//$FOCUSED used with no focused window");
            seed = context.navFocusedWindow.id;
        } else if (path.startsWith("//")) {
            path = path.substring(2);
            seed = 0;
        } else if (path.startsWith("/") && refPath != null) {
            // The root of the reference window
            path = path.substring(1);
            seed = getID(firstSegment(refPath), 0);
        }
        int id = seed;
        for (String segment : splitPath(path)) {
            if (segment.startsWith("$$")) {
                id = Hash.getID(Integer.parseInt(segment.substring(2)), id);
            } else {
                id = Hash.getID(segment, id);
            }
        }
        return id;
    }

    /**
     * Resolve a wildcard ("**" followed by a slash) reference, which matches an item at any depth
     * inside a window or its child windows.
     *
     * @param base The reference before the wildcard, for the window to search in.
     * @param rest The reference after the wildcard.
     * @param seed The ID that relative paths start from.
     * @return The ID of the item that was found, or null if there is none.
     */
    private Integer findWildcardID(String base, String rest, int seed) {
        final Window root =
                base.isEmpty()
                        ? IkGuiInternal.findWindowByID(seed)
                        : IkGuiInternal.findWindowByID(getID(base, seed));
        // Search the window and its child windows first, then other windows like its popups
        for (int pass = 0; pass < 2; ++pass) {
            for (Window window : context.windowByID.values()) {
                final boolean inTree = root == null || window.rootWindow == root.rootWindow;
                if (inTree != (pass == 0)) {
                    continue;
                }
                // The item is relative to the window
                final int id = getID(rest, window.id);
                final ItemInfo info = items.get(id);
                if (info != null && info.frame == lastSubmittedFrame()) {
                    return id;
                }
            }
        }
        // Items nested deeper in the ID stack, like combo items under pushID(index), by label
        final List<String> segments = splitPath(rest);
        final String label = segments.isEmpty() ? "" : segments.getLast();
        for (ItemInfo info : items.values()) {
            if (info.frame == lastSubmittedFrame()
                    && label.equals(info.label)
                    && info.window != null
                    && (root == null || info.window.rootWindow == root.rootWindow)) {
                return info.id;
            }
        }
        return null;
    }

    /**
     * Split a path into IDs, handling escaped slashes.
     *
     * @param path The path.
     * @return The segments, skipping empty ones.
     */
    static List<String> splitPath(String path) {
        final List<String> result = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        for (int i = 0; i < path.length(); ++i) {
            final char c = path.charAt(i);
            if (c == '\\' && i + 1 < path.length() && path.charAt(i + 1) == '/') {
                current.append('/');
                ++i;
            } else if (c == '/') {
                if (!current.isEmpty()) {
                    result.add(current.toString());
                }
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            result.add(current.toString());
        }
        return result;
    }

    /**
     * Escape the slashes in a single path segment, so it can be put back in a reference.
     *
     * @param segment The segment.
     * @return The escaped segment.
     */
    private static String escapeSegment(String segment) {
        return segment.replace("/", "\\/");
    }

    private static String firstSegment(String path) {
        final List<String> segments = splitPath(path.startsWith("//") ? path.substring(2) : path);
        return segments.isEmpty() ? "" : segments.getFirst();
    }

    /**
     * Find a window by reference.
     *
     * @param ref The reference path, "" for the current reference.
     * @return The window, or null if there is none.
     */
    Window getWindowByRef(String ref) {
        return IkGuiInternal.findWindowByID(getID(ref));
    }

    /**
     * Find a child window by its name inside a parent window.
     *
     * @param parentRef The parent window reference.
     * @param childName The name passed to beginChild().
     * @return The child window, or null if there is none.
     */
    Window getChildWindow(String parentRef, String childName) {
        final Window parent = getWindowByRef(parentRef);
        assertNotNull(parent, "No window " + parentRef);
        // Child windows are named after their ID within the parent window
        final int childID = Hash.getID(childName, parent.id);
        return IkGuiInternal.findWindowByName(
                String.format("%s/%s_%08X", parent.name, childName, childID));
    }

    /**
     * Find the child window created by an item, like the child of a multi-line text input or a
     * child window with a name.
     *
     * @param ref The reference path of the item or child window.
     * @return The child window, or null if there is none.
     */
    Window windowInfo(String ref) {
        final int id = getID(ref);
        final String suffix = String.format("%08X", id);
        for (Window window : context.windowByID.values()) {
            if (window.parentWindow != null
                    && (window.name.endsWith("_" + suffix) || window.name.endsWith("/" + suffix))) {
                return window;
            }
        }
        return IkGuiInternal.findWindowByID(id);
    }

    /**
     * The ID of a window's scrollbar.
     *
     * @param window The window.
     * @param vertical True for the vertical scrollbar, false for the horizontal one.
     * @return The ID of the scrollbar.
     */
    static int getWindowScrollbarID(Window window, boolean vertical) {
        return Hash.getID(vertical ? "#SCROLLY" : "#SCROLLX", window.id);
    }

    /**
     * Find an item by reference, waiting a few frames for it to show up.
     *
     * @param ref The reference path.
     * @return The item.
     */
    ItemInfo itemInfo(String ref) {
        final int id = getID(ref);
        ItemInfo info = itemInfoOrNull(id);
        // Like upstream, open the tree nodes along the path if the item isn't there
        if (info == null && autoOpenFullPath(ref)) {
            info = itemInfoOrNull(id);
        }
        // An item in a docked window that isn't the selected tab only shows up once the window is
        // focused, which selects its tab
        if (info == null && focusHiddenDockedWindow(ref)) {
            info = itemInfoOrNull(id);
        }
        if (info == null) {
            fail("Unable to find item " + ref);
        }
        return info;
    }

    /**
     * Focus the window at the start of an absolute reference path, if it is docked behind another
     * tab.
     *
     * @param ref The reference path.
     * @return True if a window was focused.
     */
    private boolean focusHiddenDockedWindow(String ref) {
        if (!ref.startsWith("//")) {
            return false;
        }
        final List<String> segments = splitPath(ref.substring(2));
        if (segments.size() < 2) {
            return false;
        }
        final Window window = getWindowByRef("//" + escapeSegment(segments.getFirst()));
        if (window == null || !window.dockIsActive || !window.hidden) {
            return false;
        }
        windowFocus(window);
        yieldFrame();
        return true;
    }

    /**
     * Open the closed tree nodes along a reference path, so the item at the end shows up.
     *
     * @param ref The reference path.
     * @return True if anything was opened.
     */
    private boolean autoOpenFullPath(String ref) {
        if (ref.contains("**") || ref.contains("$")) {
            return false;
        }
        final boolean absolute = ref.startsWith("//");
        final List<String> segments = splitPath(absolute ? ref.substring(2) : ref);
        boolean opened = false;
        final StringBuilder prefix = new StringBuilder(absolute ? "//" : "");
        for (int i = 0; i < segments.size() - 1; ++i) {
            if (i > 0) {
                prefix.append('/');
            }
            prefix.append(segments.get(i).replace("/", "\\/"));
            final ItemInfo parent = items.get(getID(prefix.toString()));
            if (parent != null
                    && parent.frame == lastSubmittedFrame()
                    && parent.has(ItemStatusFlags.OPENABLE)
                    && !parent.has(ItemStatusFlags.OPENED)) {
                itemClick(parent.id);
                yieldFrame();
                opened = true;
            }
        }
        return opened;
    }

    /**
     * Find an item by ID, waiting a few frames for it to show up.
     *
     * @param id The ID.
     * @param description What to call the item in failure messages.
     * @return The item.
     */
    ItemInfo itemInfo(int id, String description) {
        final ItemInfo info = itemInfoOrNull(id);
        if (info == null) {
            fail("Unable to find item " + description);
        }
        return info;
    }

    /**
     * Find an item by reference, waiting a few frames for it to show up.
     *
     * @param ref The reference path.
     * @return The item, or null if it wasn't submitted.
     */
    ItemInfo itemInfoOrNull(String ref) {
        return itemInfoOrNull(getID(ref));
    }

    private ItemInfo itemInfoOrNull(int id) {
        for (int i = 0; i <= ITEM_WAIT_FRAMES; ++i) {
            final ItemInfo info = items.get(id);
            if (info != null && info.frame >= lastSubmittedFrame() - 1 && info.frame >= 0) {
                return info;
            }
            yieldFrame();
        }
        return null;
    }

    /**
     * Check whether an item was submitted in the last frame, without waiting.
     *
     * @param ref The reference path.
     * @return True if the item exists.
     */
    boolean itemExists(String ref) {
        return itemExists(getID(ref));
    }

    /**
     * Check whether an item was submitted in the last frame, without waiting.
     *
     * @param id The item ID.
     * @return True if the item exists.
     */
    boolean itemExists(int id) {
        final ItemInfo info = items.get(id);
        return info != null && info.frame == lastSubmittedFrame();
    }

    // ---------------------------------------------------------------------------------------------
    // Mouse
    // ---------------------------------------------------------------------------------------------

    /**
     * Switch the input mode, as a user would by using the mouse or keyboard.
     *
     * @param inputSource The input source.
     */
    void setInputMode(GuiInputSource inputSource) {
        inputMode = inputSource;
        if (inputSource == GuiInputSource.GAMEPAD) {
            context.io.configFlags |= ConfigFlags.NAV_ENABLE_GAMEPAD;
            context.io.backendFlags |= BackendFlags.HAS_GAMEPAD;
        }
        if (inputSource == GuiInputSource.MOUSE) {
            context.io.addMousePosEvent(mousePosition.x, mousePosition.y);
            context.navCursorVisible = false;
            yieldFrame();
        } else {
            context.navInputSource = inputSource;
            IkGuiImplNav.setNavCursorVisible(true);
            yieldFrame();
        }
    }

    /**
     * Move the mouse to a position, in screen coordinates.
     *
     * @param x The x position.
     * @param y The y position.
     */
    void mouseMoveToPos(float x, float y) {
        mousePosition.set(x, y);
        context.io.addMousePosEvent(x, y);
        // One frame to apply the position, one for the hovered ID to settle
        yieldFrames(2);
    }

    /**
     * Move the mouse over an item, scrolling to it and bringing its window to the front if needed.
     *
     * @param ref The item reference.
     */
    void mouseMove(String ref) {
        mouseMove(itemInfo(ref), ref);
    }

    /**
     * Move the mouse over an item without focusing its window, like upstream's NoFocusWindow flag.
     * Popups that are open over the item's window stay open.
     *
     * @param ref The item reference.
     */
    void mouseMoveNoFocus(String ref) {
        final ItemInfo item = itemInfo(ref);
        final boolean backup = focusOnMove;
        focusOnMove = false;
        try {
            mouseMove(item, ref);
        } finally {
            focusOnMove = backup;
        }
    }

    /**
     * Click an item without focusing its window, like upstream's NoFocusWindow flag.
     *
     * @param ref The item reference.
     */
    void itemClickNoFocus(String ref) {
        itemClickNoFocus(ref, MouseButton.LEFT);
    }

    /**
     * Click an item without focusing its window, like upstream's NoFocusWindow flag.
     *
     * @param ref The item reference.
     * @param button The mouse button.
     */
    void itemClickNoFocus(String ref, MouseButton button) {
        mouseMoveNoFocus(ref);
        mouseClick(button);
    }

    /**
     * Whether moving the mouse to an item focuses its window, which closes popups over it, like a
     * user clicking into that window would.
     */
    private boolean focusOnMove = true;

    /**
     * Move the mouse over an item, scrolling to it and bringing its window to the front if needed.
     *
     * @param id The item ID.
     */
    void mouseMove(int id) {
        final String description = String.format("0x%08X", id);
        mouseMove(itemInfo(id, description), description);
    }

    private void mouseMove(ItemInfo item, String description) {
        // Like a user, check that the mouse ended up over the item, and try again if the layout
        // moved in the meantime, for example from scrolling
        boolean centered = false;
        for (int attempt = 0; attempt < 3; ++attempt) {
            mouseMoveOnce(item, description);
            final int hovered = context.hoveredID;
            if (hovered == 0 || hovered == item.id || item.window == null) {
                return;
            }
            final ItemInfo hoveredItem = items.get(hovered);
            if (hoveredItem == null || hoveredItem.window == null) {
                // Something that isn't an item is in the way, like a resize grip, so scroll the
                // item to the middle of the window
                if (centered || item.navLayer != IkGuiImplNav.NAV_LAYER_MAIN) {
                    return;
                }
                IkGuiImplNav.scrollToRectEx(
                        item.window,
                        item.rect,
                        ScrollFlags.KEEP_VISIBLE_EDGE_X | ScrollFlags.ALWAYS_CENTER_Y,
                        new Vector2f());
                yieldFrames(2);
                centered = true;
            }
            item = itemInfo(item.id, description);
        }
    }

    /**
     * Find the tab bar that contains a tab.
     *
     * @param tabID The tab ID.
     * @return The tab bar, or null if the item isn't a tab.
     */
    private TabBar findTabBarWithTab(int tabID) {
        for (TabBar tabBar : context.tabBars.values()) {
            if (IkGuiInternal.tabBarFindTabByID(tabBar, tabID) != null) {
                return tabBar;
            }
        }
        return null;
    }

    private void mouseMoveOnce(ItemInfo item, String description) {
        final Window window = item.window;
        // Like upstream, focus the item's window, which closes popups over it. This is skipped
        // when the window is itself in a popup, so moving through menus doesn't close them.
        if (focusOnMove && window != null && !context.openPopupStack.isEmpty()) {
            boolean inPopup = false;
            for (var popup : context.openPopupStack) {
                if (popup.window != null && popup.window == window.rootWindow) {
                    inPopup = true;
                    break;
                }
            }
            if (!inPopup) {
                IkGuiImplPopups.closePopupsOverWindow(window, false);
                yieldFrames(2);
                item = itemInfo(item.id, description);
            }
        }
        // Scroll the item into view if it is clipped
        if (window != null
                && item.navLayer == IkGuiImplNav.NAV_LAYER_MAIN
                && !window.rectInnerClip.contains(item.rect)) {
            // Center the item vertically, like a user scrolling to see it, so it doesn't end up
            // at an edge where something like box selection would scroll more
            IkGuiImplNav.scrollToRectEx(
                    window,
                    item.rect,
                    ScrollFlags.KEEP_VISIBLE_EDGE_X | ScrollFlags.ALWAYS_CENTER_Y,
                    new Vector2f());
            yieldFrames(2);
            item = itemInfo(item.id, description);
        }
        // Scroll a tab bar so a tab is visible, if it is clipped
        if (item.rectClipped.getWidth() < item.rect.getWidth()) {
            final TabBar tabBar = findTabBarWithTab(item.id);
            if (tabBar != null) {
                tabBar.nextScrollToTabID = item.id;
                yieldFrame();
                for (int i = 0;
                        i < 60 && tabBar.scrollingAnimation != tabBar.scrollingTarget;
                        ++i) {
                    yieldFrame();
                }
                yieldFrame();
                item = itemInfo(item.id, description);
            }
        }
        final RectFloat target =
                item.rectClipped.getWidth() > 0 && item.rectClipped.getHeight() > 0
                        ? item.rectClipped
                        : item.rect;
        // Moving to the item always moves the mouse, like a user would
        if (mousePosition.x == target.getCenterX() && mousePosition.y == target.getCenterY()) {
            mouseMoveToPos(mousePosition.x + 1, mousePosition.y);
        }
        mouseMoveToPos(target.getCenterX(), target.getCenterY());

        // Bring the window to the front if something else is in the way
        if (window != null
                && (context.windowHovered == null
                        || context.windowHovered.rootWindow != window.rootWindow)) {
            IkGuiInternal.bringWindowToDisplayFront(window.rootWindow);
            yieldFrames(2);
        }
    }

    /**
     * Move the mouse to a position instantly, for precise frame timing.
     *
     * @param x The x position.
     * @param y The y position.
     * @param yield Whether to run a frame afterward.
     */
    void mouseTeleportToPos(float x, float y, boolean yield) {
        mousePosition.set(x, y);
        context.io.addMousePosEvent(x, y);
        if (yield) {
            yieldFrame();
        }
    }

    /**
     * Move the mouse to the left or right edge of an item, like upstream's MoveToEdgeL/R flags.
     *
     * @param ref The item reference.
     * @param right True for the right edge, false for the left edge.
     */
    void mouseMoveToEdge(String ref, boolean right) {
        mouseMove(ref);
        for (int attempt = 0; attempt < 2; ++attempt) {
            final ItemInfo item = itemInfo(ref);
            final RectFloat rect =
                    item.rectClipped.getWidth() > 0 && item.rectClipped.getHeight() > 0
                            ? item.rectClipped
                            : item.rect;
            mouseMoveToPos(right ? rect.getRight() - 1 : rect.getLeft() + 1, rect.getCenterY());
            if (context.hoveredID == 0
                    || context.hoveredID == item.id
                    || item.window == null
                    || item.navLayer != IkGuiImplNav.NAV_LAYER_MAIN) {
                return;
            }
            // Something is in the way at the edge, like a resize grip, so scroll the item to the
            // middle of the window
            IkGuiImplNav.scrollToRectEx(
                    item.window,
                    item.rect,
                    ScrollFlags.KEEP_VISIBLE_EDGE_X | ScrollFlags.ALWAYS_CENTER_Y,
                    new Vector2f());
            yieldFrames(2);
        }
    }

    /**
     * Drag with the left mouse button from where the mouse is, by an offset.
     *
     * @param deltaX The horizontal offset.
     * @param deltaY The vertical offset.
     */
    void mouseDragWithDelta(float deltaX, float deltaY) {
        mouseDown(MouseButton.LEFT);
        mouseMoveToPos(mousePosition.x + deltaX, mousePosition.y + deltaY);
        mouseUp(MouseButton.LEFT);
    }

    /**
     * Open a combo and click one of its items, from a path like "Combo/Item".
     *
     * @param path The combo reference and the item label.
     */
    void comboClick(String path) {
        final int split = path.lastIndexOf('/');
        // The popup of a top level combo
        final String popupName = "##Combo_00";
        final Window popup = IkGuiInternal.findWindowByName(popupName);
        final boolean isOpen =
                popup != null
                        && popup.active
                        && context.openPopupStack.stream().anyMatch(data -> data.window == popup);
        if (!isOpen) {
            itemClick(path.substring(0, split));
        }
        itemClick("//" + popupName + "/**/" + path.substring(split + 1));
    }

    /**
     * Press a mouse button.
     *
     * @param button The button.
     */
    void mouseDown(MouseButton button) {
        // Make sure this isn't taken as a double click with an earlier click, without running any
        // frames, as if enough time had passed since the earlier click
        final long sinceLastClick = context.time - context.io.mouseClickedTime[button.index];
        if (sinceLastClick < context.io.mouseDoubleClickTime) {
            context.io.mouseClickedTime[button.index] =
                    context.time - context.io.mouseDoubleClickTime - 1;
        }
        context.io.addMouseButtonEvent(button, true);
        if (platform != null) {
            // Like an OS, clicking a platform window focuses it, unless it asks not to be
            final Viewport viewport =
                    IkGuiImplViewports.findHoveredViewportFromPlatformWindowStack(
                            mousePosition.x, mousePosition.y);
            if (viewport != null && (viewport.flags & ViewportFlags.NO_FOCUS_ON_CLICK) == 0) {
                platformEvents.add(() -> platform.focus(viewport));
            }
        }
        yieldFrame();
    }

    /**
     * Release a mouse button.
     *
     * @param button The button.
     */
    void mouseUp(MouseButton button) {
        context.io.addMouseButtonEvent(button, false);
        yieldFrame();
    }

    /**
     * Click a mouse button where the mouse is.
     *
     * @param button The button.
     */
    void mouseClick(MouseButton button) {
        mouseDown(button);
        mouseUp(button);
        // Give a frame for items to react
        yieldFrame();
    }

    /**
     * Click a mouse button several times quickly, like upstream's MouseClickMulti().
     *
     * @param button The button.
     * @param count The number of clicks.
     */
    void mouseClickMulti(MouseButton button, int count) {
        for (int n = 0; n < count; ++n) {
            if (n == 0) {
                // The first press starts a new click sequence
                mouseDown(button);
            } else {
                context.io.addMouseButtonEvent(button, true);
                yieldFrame();
            }
            context.io.addMouseButtonEvent(button, false);
            yieldFrame();
        }
        yieldFrame();
    }

    /**
     * Double click a mouse button where the mouse is.
     *
     * @param button The button.
     */
    void mouseDoubleClick(MouseButton button) {
        // The first press starts a new click sequence
        mouseDown(button);
        context.io.addMouseButtonEvent(button, false);
        yieldFrame();
        context.io.addMouseButtonEvent(button, true);
        yieldFrame();
        context.io.addMouseButtonEvent(button, false);
        yieldFrames(2);
    }

    /**
     * Find a position on the display that isn't covered by any window.
     *
     * @return The position, or null if there is none.
     */
    Vector2f findVoidPosition() {
        final Vector2f size = context.io.displaySize;
        for (float y = size.y - 2; y > 0; y -= 40) {
            for (float x = size.x - 2; x > 0; x -= 40) {
                if (!anyWindowContains(x, y)) {
                    return new Vector2f(x, y);
                }
            }
        }
        return null;
    }

    /** Move the mouse to an empty area of the display, outside any window. */
    void mouseMoveToVoid() {
        final Vector2f pos = findVoidPosition();
        assertNotNull(pos, "Unable to find an empty area");
        mouseMoveToPos(pos.x, pos.y);
    }

    /** Click on an empty area of the display, outside any window. */
    void mouseClickOnVoid() {
        mouseClickOnVoid(MouseButton.LEFT);
    }

    /**
     * Click on an empty area of the display, outside any window.
     *
     * @param button The mouse button.
     */
    void mouseClickOnVoid(MouseButton button) {
        final Vector2f size = context.io.displaySize;
        for (float y = size.y - 2; y > 0; y -= 40) {
            for (float x = size.x - 2; x > 0; x -= 40) {
                if (!anyWindowContains(x, y)) {
                    mouseMoveToPos(x, y);
                    mouseClick(button);
                    return;
                }
            }
        }
        fail("Unable to find an empty area to click on");
    }

    private boolean anyWindowContains(float x, float y) {
        for (Window window : context.windowDisplayOrder) {
            if (window.active && !window.hidden && window.getRect().contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Scroll the mouse wheel where the mouse is.
     *
     * @param wheelX The horizontal amount.
     * @param wheelY The vertical amount.
     */
    void mouseWheel(float wheelX, float wheelY) {
        context.io.addMouseWheelEvent(wheelX, wheelY);
        yieldFrames(2);
    }

    // ---------------------------------------------------------------------------------------------
    // Items
    // ---------------------------------------------------------------------------------------------

    /**
     * Click an item with the left mouse button.
     *
     * @param ref The item reference.
     */
    void itemClick(String ref) {
        itemClick(ref, MouseButton.LEFT);
    }

    /**
     * Click an item.
     *
     * @param ref The item reference.
     * @param button The mouse button.
     */
    void itemClick(String ref, MouseButton button) {
        if (inputMode != GuiInputSource.MOUSE && button == MouseButton.LEFT) {
            navMoveTo(ref);
            navActivate();
            return;
        }
        mouseMove(ref);
        mouseClick(button);
    }

    /**
     * Click an item by ID with a mouse button.
     *
     * @param id The item ID.
     * @param button The mouse button.
     */
    void itemClick(int id, MouseButton button) {
        mouseMove(id);
        mouseClick(button);
    }

    /**
     * Click an item with the left mouse button.
     *
     * @param id The item ID.
     */
    void itemClick(int id) {
        mouseMove(id);
        mouseClick(MouseButton.LEFT);
    }

    /**
     * Press the mouse on an item, drag it over another item, and release it there.
     *
     * @param srcRef The item to drag from.
     * @param dstRef The item to drop on.
     */
    void itemDragAndDrop(String srcRef, String dstRef) {
        mouseMove(srcRef);
        mouseDown(MouseButton.LEFT);
        // Move past the drag threshold first
        mouseMoveToPos(mousePosition.x + context.io.mouseDragThreshold + 1, mousePosition.y);
        mouseMove(dstRef);
        yieldFrame();
        mouseUp(MouseButton.LEFT);
        yieldFrame();
    }

    /**
     * Move a window, so that its top left corner is at a position.
     *
     * @param ref The window reference.
     * @param x The new x position.
     * @param y The new y position.
     */
    void windowMove(String ref, float x, float y) {
        final Window window = getWindowByRef(ref);
        assertNotNull(window, "No window " + ref);
        IkGuiImplWindows.setWindowPos(dockHostOrSelf(window), x, y, Condition.ALWAYS);
        yieldFrames(2);
    }

    /**
     * Move a window.
     *
     * @param window The window.
     * @param x The new x position.
     * @param y The new y position.
     */
    void windowMove(Window window, float x, float y) {
        IkGuiImplWindows.setWindowPos(dockHostOrSelf(window), x, y, Condition.ALWAYS);
        yieldFrames(2);
    }

    /**
     * Get the window that positions a window: the host window of its dock tree if it is docked,
     * otherwise the window itself.
     *
     * @param window The window.
     * @return The window to move or resize.
     */
    static Window dockHostOrSelf(Window window) {
        if (window.dockIsActive && window.dockNode != null) {
            final DockNode root = IkGuiImplDocking.dockNodeGetRootNode(window.dockNode);
            if (root.hostWindow != null && !root.isDockSpace()) {
                return root.hostWindow;
            }
        }
        return window;
    }

    /** Move the mouse just past the drag threshold, so a held button starts dragging. */
    void mouseLiftDragThreshold() {
        mouseMoveToPos(mousePosition.x + context.io.mouseDragThreshold + 1, mousePosition.y);
    }

    /**
     * Press the left mouse button on an item, drag it over another item, and release it there.
     *
     * @param srcID The item to drag from.
     * @param dstID The item to drop on.
     */
    void itemDragAndDrop(int srcID, int dstID) {
        mouseMove(srcID);
        mouseDown(MouseButton.LEFT);
        mouseLiftDragThreshold();
        mouseMove(dstID);
        yieldFrame();
        mouseUp(MouseButton.LEFT);
        yieldFrame();
    }

    /**
     * Get the ID of a table column header, as submitted by tableHeadersRow().
     *
     * @param table The table.
     * @param label The column label.
     * @return The header ID.
     */
    static int tableGetHeaderID(Table table, String label) {
        for (int column = 0; column < table.columnsCount; ++column) {
            if (label.equals(IkGuiImplTables.tableGetColumnName(table, column))) {
                // tableHeadersRow() pushes the column index under the table instance ID
                return Hash.getID(label, Hash.getID(column, table.id));
            }
        }
        fail("No column " + label);
        return 0;
    }

    /**
     * Get the ID of a table column header by its index, as submitted by tableHeadersRow().
     *
     * @param table The table.
     * @param column The column index.
     * @return The header ID.
     */
    static int tableGetHeaderID(Table table, int column) {
        return tableGetHeaderID(table, column, 0);
    }

    /**
     * Get the ID of a column header in an instance of a table, as submitted by tableHeadersRow().
     *
     * @param table The table.
     * @param column The column index.
     * @param instance The instance number.
     * @return The header ID.
     */
    static int tableGetHeaderID(Table table, int column, int instance) {
        return Hash.getID(
                IkGuiImplTables.tableGetColumnName(table, column),
                Hash.getID(column, table.getInstanceID(instance)));
    }

    /**
     * Open a table's context menu by right clicking a column header.
     *
     * @param ref The table reference.
     * @param column The column whose header to click, or -1 for the right-most visible column.
     */
    void tableOpenContextMenu(String ref, int column) {
        final Table table = IkGuiImplTables.tableFindByID(getID(ref));
        assertNotNull(table, "No table " + ref);
        final int targetColumn = column < 0 ? table.rightMostEnabledColumn : column;
        itemClick(tableGetHeaderID(table, targetColumn), MouseButton.RIGHT);
    }

    /**
     * Find a table by ID, failing if it doesn't exist.
     *
     * @param tableID The table ID.
     * @return The table.
     */
    private static Table tableByID(int tableID) {
        final Table table = IkGuiImplTables.tableFindByID(tableID);
        assertNotNull(table, String.format("No table 0x%08X", tableID));
        return table;
    }

    /**
     * Find the index of a table column by its label.
     *
     * @param table The table.
     * @param label The column label.
     * @return The column index.
     */
    static int tableFindColumn(Table table, String label) {
        for (int column = 0; column < table.columnsCount; ++column) {
            if (label.equals(IkGuiImplTables.tableGetColumnName(table, column))) {
                return column;
            }
        }
        fail("No column " + label);
        return -1;
    }

    /**
     * Click a table column header, which cycles the sort direction on sortable tables.
     *
     * @param tableID The table ID.
     * @param label The column label.
     * @return The sort direction of the column after the click.
     */
    SortDirection tableClickHeader(int tableID, String label) {
        return tableClickHeader(tableID, label, KeyModFlags.NONE);
    }

    /**
     * Click a table column header while holding modifier keys, which cycles the sort direction on
     * sortable tables.
     *
     * @param tableID The table ID.
     * @param label The column label.
     * @param mods The modifier keys to hold (KeyModFlags).
     * @return The sort direction of the column after the click.
     */
    SortDirection tableClickHeader(int tableID, String label, int mods) {
        final Table table = tableByID(tableID);
        final int column = tableFindColumn(table, label);
        if (mods != KeyModFlags.NONE) {
            keyDown(KeyChord.ofMods(mods));
        }
        itemClick(tableGetHeaderID(table, column));
        if (mods != KeyModFlags.NONE) {
            keyUp(KeyChord.ofMods(mods));
        }
        return table.columns[column].sortDirection;
    }

    /**
     * Get the up-to-date sort specs of a table.
     *
     * @param tableID The table ID.
     * @return The sort specs, or null if the table isn't sortable.
     */
    static TableSortSpecs tableGetSortSpecs(int tableID) {
        final Table table = tableByID(tableID);
        if ((table.flags & TableFlags.SORTABLE) == 0) {
            return null;
        }
        IkGuiImplTables.tableSortSpecsBuild(table);
        return table.sortSpecs;
    }

    /**
     * Resize a table column by dragging its right border.
     *
     * @param tableID The table ID.
     * @param column The column index.
     * @param width The width to resize to.
     */
    void tableResizeColumn(int tableID, int column, float width) {
        final Table table = tableByID(tableID);
        final float delta = width - table.columns[column].widthGiven;
        itemDragWithDelta(IkGuiImplTables.tableGetColumnResizeID(table, column, 0), delta, 0.0f);
        assertEquals(width, table.columns[column].widthGiven, "column " + column + " width");
    }

    /**
     * Resize a table column by dragging its right border.
     *
     * @param tableID The table ID.
     * @param label The column label.
     * @param width The width to resize to.
     */
    void tableResizeColumn(int tableID, String label, float width) {
        tableResizeColumn(tableID, tableFindColumn(tableByID(tableID), label), width);
    }

    /**
     * Show or hide a table column using the table context menu.
     *
     * @param tableID The table ID.
     * @param label The column label.
     * @param enabled Whether the column should be enabled.
     */
    void tableSetColumnEnabled(int tableID, String label, boolean enabled) {
        final Table table = tableByID(tableID);
        final int column = tableFindColumn(table, label);
        if (table.columns[column].isUserEnabled == enabled) {
            return;
        }
        // Open the menu from a visible header
        final int headerColumn = table.columns[column].isEnabled ? column : -1;
        final int targetColumn = headerColumn < 0 ? table.rightMostEnabledColumn : headerColumn;
        itemClick(tableGetHeaderID(table, targetColumn), MouseButton.RIGHT);
        final int popupWindowID = popupGetWindowID(Hash.getID("##ContextMenu", table.id));
        itemClick(Hash.getID(label, popupWindowID));
        popupCloseAll();
        yieldFrame();
        assertEquals(enabled, table.columns[column].isUserEnabled, "column " + label + " enabled");
    }

    /**
     * Remove a table instance, so that it is created from scratch the next time it is submitted.
     * Settings are kept.
     *
     * @param tableID The table ID.
     */
    static void tableDiscardInstance(int tableID) {
        final Context context = IkGuiInternal.context;
        final Table table = context.tablesByID.remove(tableID);
        if (table != null) {
            context.tables.remove(table);
        }
    }

    /**
     * Remove the saved settings of a table, and unbind them from the table instance if it exists.
     *
     * @param tableID The table ID.
     */
    static void tableDiscardSettings(int tableID) {
        final Context context = IkGuiInternal.context;
        context.settingsTables.removeIf(settings -> settings.id == tableID);
        final Table table = IkGuiImplTables.tableFindByID(tableID);
        if (table != null) {
            table.settings = null;
        }
    }

    /**
     * Remove both a table instance and its settings.
     *
     * @param tableID The table ID.
     */
    static void tableDiscardInstanceAndSettings(int tableID) {
        tableDiscardSettings(tableID);
        tableDiscardInstance(tableID);
    }

    /**
     * Find a section in .ini data.
     *
     * @param ini The .ini data.
     * @param header The section header line, like "[Table][0x12345678,4]".
     * @return The section, including the header line, or null if it is not present.
     */
    static String findIniSection(String ini, String header) {
        final int start = ini.indexOf(header + "\n");
        if (start < 0) {
            return null;
        }
        int end = ini.indexOf("\n[", start + header.length());
        end = end < 0 ? ini.length() : end + 1;
        return ini.substring(start, end);
    }

    /**
     * Move a window so that a point relative to its size is at a position.
     *
     * @param ref The window reference.
     * @param x The x position.
     * @param y The y position.
     * @param pivotX The horizontal pivot, 0 for the left edge and 1 for the right edge.
     * @param pivotY The vertical pivot, 0 for the top edge and 1 for the bottom edge.
     */
    void windowMove(String ref, float x, float y, float pivotX, float pivotY) {
        final Window window = windowByRef(ref);
        final Window host = dockHostOrSelf(window);
        windowMove(ref, x - host.size.x * pivotX, y - host.size.y * pivotY);
    }

    /**
     * Press a mouse button on an item, drag it over another item, and release it there.
     *
     * @param srcRef The item to drag from.
     * @param dstRef The item to drop on.
     * @param button The mouse button to drag with.
     */
    void itemDragAndDrop(String srcRef, String dstRef, MouseButton button) {
        mouseMove(srcRef);
        mouseDown(button);
        mouseLiftDragThreshold();
        mouseMove(dstRef);
        yieldFrame();
        mouseUp(button);
        yieldFrame();
    }

    /**
     * Press the mouse on an item, drag it over another item, hold it there for a while, and then
     * release it.
     *
     * @param srcRef The item to drag from.
     * @param dstRef The item to hold over.
     */
    void itemDragOverAndHold(String srcRef, String dstRef) {
        mouseMove(srcRef);
        mouseDown(MouseButton.LEFT);
        mouseLiftDragThreshold();
        mouseMove(dstRef);
        sleep(1.0f);
        mouseUp(MouseButton.LEFT);
    }

    /**
     * A point in a window's title bar to click or drag on, away from the buttons.
     *
     * @param window The window.
     * @return The point in screen coordinates.
     */
    static Vector2f windowTitleBarPoint(Window window) {
        // A docked window's title bar is its tab
        final DockNode node = window.dockIsActive ? window.dockNode : null;
        if (node != null && node.tabBar != null && !node.isHiddenTabBar() && !node.isNoTabBar()) {
            final TabBar tabBar = node.tabBar;
            for (TabItem tab : tabBar.tabs) {
                if (tab.window == window) {
                    final float left =
                            tabBar.barRect.getLeft() + tab.offset - tabBar.scrollingAnimation;
                    return new Vector2f(left + tab.width * 0.5f, tabBar.barRect.getCenterY());
                }
            }
        }
        final float height = window.titleBarHeight;
        // Past the collapse button, but still inside narrow windows
        final float x =
                Math.min(height * 2, Math.max(window.size.x * 0.5f, window.size.x - height * 0.5f));
        return new Vector2f(window.position.x + x, window.position.y + height * 0.5f);
    }

    /**
     * Double click an item with the left mouse button.
     *
     * @param ref The item reference.
     */
    void itemDoubleClick(String ref) {
        mouseMove(ref);
        mouseDoubleClick(MouseButton.LEFT);
    }

    /**
     * Double click an item with the left mouse button.
     *
     * @param id The item ID.
     */
    void itemDoubleClick(int id) {
        mouseMove(id);
        mouseDoubleClick(MouseButton.LEFT);
    }

    /**
     * Start text input on an item, using Ctrl+Click.
     *
     * @param ref The item reference.
     */
    void itemInput(String ref) {
        itemInput(getID(ref));
    }

    /**
     * Drag an item with the left mouse button by an offset.
     *
     * @param id The item ID.
     * @param deltaX The horizontal offset.
     * @param deltaY The vertical offset.
     */
    void itemDragWithDelta(int id, float deltaX, float deltaY) {
        mouseMove(id);
        mouseDragWithDelta(deltaX, deltaY);
    }

    /**
     * Drag an item with the left mouse button by an offset.
     *
     * @param ref The item reference.
     * @param deltaX The horizontal offset.
     * @param deltaY The vertical offset.
     */
    void itemDragWithDelta(String ref, float deltaX, float deltaY) {
        itemDragWithDelta(getID(ref), deltaX, deltaY);
    }

    /**
     * Drag an item with the left mouse button to a position, and release it there.
     *
     * @param ref The item reference.
     * @param x The x position to release at.
     * @param y The y position to release at.
     */
    void itemDragToPos(String ref, float x, float y) {
        mouseMove(ref);
        mouseDown(MouseButton.LEFT);
        mouseLiftDragThreshold();
        mouseMoveToPos(x, y);
        mouseUp(MouseButton.LEFT);
    }

    /**
     * Start text input on an item, using Ctrl+Click.
     *
     * @param id The item ID.
     */
    void itemInput(int id) {
        mouseMove(id);
        keyDown(KeyChord.ofMods(KeyModFlags.CTRL));
        mouseClick(MouseButton.LEFT);
        keyUp(KeyChord.ofMods(KeyModFlags.CTRL));
    }

    /**
     * Type a value into an item, replacing its contents, and press Enter.
     *
     * @param ref The item reference.
     * @param value The value to type.
     */
    void itemInputValue(String ref, Number value) {
        itemInputValue(ref, String.valueOf(value));
    }

    /**
     * Type a value into an item, replacing its contents, and press Enter.
     *
     * @param ref The item reference.
     * @param value The value to type.
     */
    void itemInputValue(String ref, String value) {
        itemInput(ref);
        keyCharsReplaceEnter(value);
    }

    /**
     * Hold the mouse button down on an item for some frames.
     *
     * @param ref The item reference.
     * @param frames The number of frames.
     */
    void itemHoldForFrames(String ref, int frames) {
        mouseMove(ref);
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        yieldFrames(frames);
        mouseUp(MouseButton.LEFT);
    }

    /**
     * Open an openable item like a tree node, if it isn't already open.
     *
     * @param ref The item reference.
     */
    void itemOpen(String ref) {
        setOpen(ref, true);
    }

    /**
     * Close an openable item like a tree node, if it is open.
     *
     * @param ref The item reference.
     */
    void itemClose(String ref) {
        setOpen(ref, false);
    }

    private void setOpen(String ref, boolean open) {
        // Like a user, click again if the first click didn't take, for example because it only
        // closed a popup
        for (int attempt = 0; attempt < 2; ++attempt) {
            if (itemInfo(ref).has(ItemStatusFlags.OPENED) == open) {
                break;
            }
            itemClick(ref);
            yieldFrame();
        }
        assertEquals(
                open,
                itemInfo(ref).has(ItemStatusFlags.OPENED),
                ref + " should be " + (open ? "open" : "closed"));
    }

    /**
     * Close every open item, like tree nodes and collapsing headers, directly inside a window.
     *
     * @param ref The window reference, "" for the current reference.
     */
    void itemCloseAll(String ref) {
        final Window window = windowByRef(ref);
        for (int attempt = 0; attempt < 100; ++attempt) {
            ItemInfo open = null;
            for (ItemInfo info : items.values()) {
                if (info.window == window
                        && info.frame == lastSubmittedFrame()
                        && info.has(ItemStatusFlags.OPENABLE | ItemStatusFlags.OPENED)
                        && (open == null || info.rect.getTop() > open.rect.getTop())) {
                    // Close the lowest one first, so closing doesn't move the others
                    open = info;
                }
            }
            if (open == null) {
                return;
            }
            mouseMove(open, open.label);
            mouseClick(MouseButton.LEFT);
        }
        fail("Unable to close all the items in " + ref);
    }

    /**
     * Check a checkable item like a checkbox, if it isn't already checked.
     *
     * @param ref The item reference.
     */
    void itemCheck(String ref) {
        setChecked(ref, true);
    }

    /**
     * Uncheck a checkable item like a checkbox, if it is checked.
     *
     * @param ref The item reference.
     */
    void itemUncheck(String ref) {
        setChecked(ref, false);
    }

    private void setChecked(String ref, boolean checked) {
        if (itemInfo(ref).has(ItemStatusFlags.CHECKED) != checked) {
            itemClick(ref);
        }
        yieldFrame();
        assertEquals(checked, itemInfo(ref).has(ItemStatusFlags.CHECKED));
    }

    /**
     * Activate an item with keyboard navigation.
     *
     * @param ref The item reference.
     */
    void itemNavActivate(String ref) {
        navMoveTo(ref);
        navActivate();
    }

    // ---------------------------------------------------------------------------------------------
    // Keyboard
    // ---------------------------------------------------------------------------------------------

    private void addKeyChordEvents(int chord, boolean down) {
        final int mods = KeyChord.getMods(chord);
        final Key key = KeyChord.getKey(chord);
        if (down) {
            addModEvents(mods, true);
        }
        if (key != Key.NONE) {
            context.io.addKeyEvent(key, down);
        }
        if (!down) {
            addModEvents(mods, false);
        }
    }

    /**
     * Submit modifier key events like a real backend, with both the modifier and the left key.
     *
     * @param mods The modifiers.
     * @param down Whether they are down.
     */
    private void addModEvents(int mods, boolean down) {
        if ((mods & KeyModFlags.CTRL) != 0) {
            context.io.addKeyEvent(Key.MOD_CTRL, down);
            context.io.addKeyEvent(Key.LEFT_CTRL, down);
        }
        if ((mods & KeyModFlags.SHIFT) != 0) {
            context.io.addKeyEvent(Key.MOD_SHIFT, down);
            context.io.addKeyEvent(Key.LEFT_SHIFT, down);
        }
        if ((mods & KeyModFlags.ALT) != 0) {
            context.io.addKeyEvent(Key.MOD_ALT, down);
            context.io.addKeyEvent(Key.LEFT_ALT, down);
        }
        if ((mods & KeyModFlags.SUPER) != 0) {
            context.io.addKeyEvent(Key.MOD_SUPER, down);
            context.io.addKeyEvent(Key.LEFT_SUPER, down);
        }
    }

    /**
     * Combine modifiers with a key.
     *
     * @param mods The modifiers.
     * @param key The key.
     * @return The key chord.
     * @see KeyModFlags
     */
    static int chord(int mods, Key key) {
        return KeyChord.of(mods, key);
    }

    /**
     * Press a key or key chord and hold it down.
     *
     * @param chord The key chord.
     */
    void keyDown(int chord) {
        addKeyChordEvents(chord, true);
        yieldFrames(2);
    }

    /**
     * Release a key or key chord.
     *
     * @param chord The key chord.
     */
    void keyUp(int chord) {
        addKeyChordEvents(chord, false);
        yieldFrames(2);
    }

    /**
     * Press a key.
     *
     * @param key The key.
     */
    void keyDown(Key key) {
        keyDown(KeyChord.of(key));
    }

    /**
     * Release a key.
     *
     * @param key The key.
     */
    void keyUp(Key key) {
        keyUp(KeyChord.of(key));
    }

    /**
     * Press and release a key.
     *
     * @param key The key.
     */
    void keyPress(Key key) {
        keyPress(KeyChord.of(key), 1);
    }

    /**
     * Press and release a key some number of times.
     *
     * @param key The key.
     * @param count The number of presses.
     */
    void keyPress(Key key, int count) {
        keyPress(KeyChord.of(key), count);
    }

    /**
     * Press and release a key chord.
     *
     * @param chord The key chord.
     */
    void keyPress(int chord) {
        keyPress(chord, 1);
    }

    /**
     * Press and release a key chord some number of times.
     *
     * @param chord The key chord.
     * @param count The number of presses.
     */
    void keyPress(int chord, int count) {
        for (int i = 0; i < count; ++i) {
            addKeyChordEvents(chord, true);
            yieldFrame();
            addKeyChordEvents(chord, false);
            yieldFrame();
            // Give a frame for items to react
            yieldFrame();
        }
    }

    /**
     * Press or release a key chord, then let some time pass, without the extra frames the other key
     * functions add. Useful for precise timing.
     *
     * @param chord The key chord.
     * @param down Whether to press or release it.
     * @param seconds How long to wait afterward, in seconds.
     */
    void keySetEx(int chord, boolean down, float seconds) {
        addKeyChordEvents(chord, down);
        if (seconds > 0) {
            sleep(seconds);
        }
    }

    /**
     * Hold a key chord down for some time.
     *
     * @param chord The key chord.
     * @param seconds How long to hold it, in seconds.
     */
    void keyHold(int chord, float seconds) {
        addKeyChordEvents(chord, true);
        sleep(seconds);
        addKeyChordEvents(chord, false);
        yieldFrame();
    }

    /**
     * Type characters.
     *
     * @param chars The characters.
     */
    void keyChars(String chars) {
        context.io.addInputCharacters(chars);
        yieldFrame();
    }

    /**
     * Move to the end of the text and type characters.
     *
     * @param chars The characters.
     */
    void keyCharsAppend(String chars) {
        keyPress(Key.END);
        keyChars(chars);
    }

    /**
     * Move to the end of the text, type characters, and press Shift+Enter.
     *
     * @param chars The characters.
     */
    void keyCharsAppendEnter(String chars) {
        keyPress(Key.END);
        keyChars(chars);
        keyPress(chord(KeyModFlags.SHIFT, Key.ENTER));
    }

    /**
     * Select all the text and replace it.
     *
     * @param chars The characters to replace the text with.
     */
    void keyCharsReplace(String chars) {
        keyPress(chord(KeyModFlags.CTRL, Key.A));
        if (chars.isEmpty()) {
            keyPress(Key.DELETE);
        } else {
            keyChars(chars);
        }
    }

    /**
     * Select all the text, replace it, and press Enter.
     *
     * @param chars The characters to replace the text with.
     */
    void keyCharsReplaceEnter(String chars) {
        keyCharsReplace(chars);
        keyPress(Key.ENTER);
    }

    // ---------------------------------------------------------------------------------------------
    // Navigation
    // ---------------------------------------------------------------------------------------------

    /**
     * Move the navigation cursor straight to an item, focusing its window.
     *
     * @param ref The item reference.
     */
    void navMoveTo(String ref) {
        navMoveTo(itemInfo(ref), ref);
    }

    /**
     * Move the navigation cursor straight to an item, focusing its window.
     *
     * @param id The item ID.
     */
    void navMoveTo(int id) {
        final String description = String.format("0x%08X", id);
        navMoveTo(itemInfo(id, description), description);
    }

    private void navMoveTo(ItemInfo item, String ref) {
        windowFocus(item.window);
        item = itemInfo(item.id, ref);
        final RectFloat rectRelative = new RectFloat();
        rectRelative.set(item.rect);
        rectRelative.translate(-item.window.position.x, -item.window.position.y);
        IkGuiImplNav.setNavID(item.id, item.navLayer, 0, rectRelative);
        IkGuiImplNav.setNavCursorVisible(true);
        context.navHighlightItemUnderNav = true;
        context.navMousePositionDirty = true;
        IkGuiImplNav.scrollToRectEx(item.window, item.rect, ScrollFlags.NONE, new Vector2f());
        do {
            yieldFrame();
        } while (context.navMoveSubmitted);
        assertEquals(item.id, context.navID, "Unable to set the nav ID to " + ref);
    }

    /** Activate the item under the navigation cursor, by pressing Space. */
    void navActivate() {
        yieldFrame();
        keyPress(Key.SPACE);
    }

    /** Input on the item under the navigation cursor, by pressing Enter. */
    void navInput() {
        keyPress(Key.ENTER);
    }

    // ---------------------------------------------------------------------------------------------
    // Windows, popups, menus
    // ---------------------------------------------------------------------------------------------

    /**
     * Focus a window.
     *
     * @param ref The window reference.
     */
    void windowFocus(String ref) {
        final Window window = getWindowByRef(ref);
        assertNotNull(window, "No window " + ref);
        windowFocus(window);
    }

    /**
     * Focus a window.
     *
     * @param window The window.
     */
    void windowFocus(Window window) {
        bringPlatformWindowToFront(window);
        IkGuiInternal.focusWindow(window, WindowFocusRequestFlags.NONE);
        yieldFrames(2);
    }

    /**
     * With multiple viewports, give the platform window of a window the OS focus, which brings it
     * in front of the other platform windows like a user would. This runs a frame when the focus
     * changes, so the GUI handles the platform focus before anything else focuses a window.
     *
     * @param window The window.
     */
    private void bringPlatformWindowToFront(Window window) {
        if (platform == null || window.viewport == null) {
            return;
        }
        final Viewport viewport = window.viewport;
        final FakeViewportPlatform.FakeWindow platformWindow = platform.get(viewport);
        if (platformWindow != null && !platformWindow.focused) {
            platformEvents.add(() -> platform.focus(viewport));
            yieldFrame();
        }
    }

    /**
     * Close a window with its close button.
     *
     * @param ref The window reference.
     */
    void windowClose(String ref) {
        final Window window = windowByRef(ref);
        final DockNode node = window.dockIsActive ? window.dockNode : null;
        if (node != null && node.tabBar != null && !node.isHiddenTabBar() && !node.isNoTabBar()) {
            // A docked window's close button is on its tab, and only shows up when the tab is
            // hovered or selected
            mouseMove(window.idTab);
            itemClick(Hash.getID("#CLOSE", window.id));
        } else if (node != null) {
            // Without a tab bar, the close button is on the dock node title bar
            itemClick(Hash.getID("#CLOSE", node.id));
        } else {
            itemClick(Hash.getID("#CLOSE", window.id));
        }
    }

    /**
     * Collapse or expand a window with its title bar's collapse button, if it isn't already in that
     * state.
     *
     * @param ref The window reference.
     * @param collapsed True to collapse, false to expand.
     */
    void windowCollapse(String ref, boolean collapsed) {
        final Window window = windowByRef(ref);
        if (window.collapsed == collapsed) {
            return;
        }
        itemClick(Hash.getID("#COLLAPSE", window.id));
        yieldFrame();
        assertEquals(collapsed, window.collapsed, "Window " + ref + " collapsed");
    }

    /**
     * Resize a window.
     *
     * @param ref The window reference.
     * @param width The new width.
     * @param height The new height.
     */
    void windowResize(String ref, float width, float height) {
        final Window window = getWindowByRef(ref);
        assertNotNull(window, "No window " + ref);
        windowResize(dockHostOrSelf(window), width, height);
    }

    /**
     * Resize a window. A negative size auto-fits that axis.
     *
     * @param window The window.
     * @param width The new width.
     * @param height The new height.
     */
    void windowResize(Window window, float width, float height) {
        if (width >= 0.0f && height >= 0.0f) {
            IkGuiImplWindows.setWindowSize(window, width, height, Condition.ALWAYS);
            yieldFrames(2);
            return;
        }
        // A negative size auto-fits that axis, by double clicking the resize grip or border like a
        // user would. A size of 0 on the other axis keeps the current size.
        final float otherWidth = width > 0.0f ? width : window.size.x;
        final float otherHeight = height > 0.0f ? height : window.size.y;
        if (otherWidth != window.size.x || otherHeight != window.size.y) {
            IkGuiImplWindows.setWindowSize(window, otherWidth, otherHeight, Condition.ALWAYS);
            yieldFrames(2);
        }
        final int n;
        if (width < 0.0f && height < 0.0f) {
            // The bottom right grip
            n = 0;
        } else if (width < 0.0f) {
            // The right border
            n = 4 + Direction.RIGHT.ordinal();
        } else {
            // The bottom border
            n = 4 + Direction.DOWN.ordinal();
        }
        itemDoubleClick(IkGuiImplWindows.getWindowResizeID(window, n));
        yieldFrames(2);
    }

    /**
     * Undock windows.
     *
     * @param refs The window references.
     */
    void dockClear(String... refs) {
        for (String ref : refs) {
            // Windows that don't exist yet have nothing to undock
            final Window window = getWindowByRef(ref);
            if (window == null) {
                continue;
            }
            if (window.dockNode != null || window.dockID != 0) {
                IkGuiImplDocking.dockContextQueueUndockWindow(window);
            }
        }
        yieldFrames(2);
    }

    /**
     * Dock a window into another window, as a tab.
     *
     * @param srcRef The reference of the window to dock.
     * @param dstRef The reference of the window to dock into.
     */
    void dockInto(String srcRef, String dstRef) {
        dockInto(getID(srcRef), getID(dstRef), Direction.NONE, false);
    }

    /**
     * Dock a window into another window or dock node, by dragging it there with the mouse.
     *
     * @param srcRef The reference of the window to dock.
     * @param dstRef The reference of the window to dock into.
     * @param splitDirection The direction to split the target in, or NONE to dock as a tab.
     */
    void dockInto(String srcRef, String dstRef, Direction splitDirection) {
        dockInto(getID(srcRef), getID(dstRef), splitDirection, false);
    }

    /**
     * Dock a window into another window or dock node, by dragging it there with the mouse.
     *
     * @param srcRef The reference of the window to dock.
     * @param dstRef The reference of the window to dock into.
     * @param splitDirection The direction to split the target in, or NONE to dock as a tab.
     * @param splitOuter Whether to split the root node of the target, using the outer drop boxes.
     */
    void dockInto(String srcRef, String dstRef, Direction splitDirection, boolean splitOuter) {
        dockInto(getID(srcRef), getID(dstRef), splitDirection, splitOuter);
    }

    /**
     * Dock a window into a dock node, as a tab, by dragging it there with the mouse.
     *
     * @param srcRef The reference of the window to dock.
     * @param dstID The ID of the window or dock node to dock into.
     */
    void dockInto(String srcRef, int dstID) {
        dockInto(getID(srcRef), dstID, Direction.NONE, false);
    }

    /**
     * Dock a window or dock node into a window, as a tab, by dragging it there with the mouse.
     *
     * @param srcID The ID of the window or dock node to dock.
     * @param dstRef The reference of the window to dock into.
     */
    void dockInto(int srcID, String dstRef) {
        dockInto(srcID, getID(dstRef), Direction.NONE, false);
    }

    /**
     * Hide or show the tab bar of a dock node, with the window menu and the unhide button.
     *
     * @param node The dock node.
     * @param hidden Whether the tab bar should be hidden.
     */
    void dockNodeHideTabBar(DockNode node, boolean hidden) {
        if (hidden == node.isHiddenTabBar()) {
            return;
        }
        if (hidden) {
            itemClick(IkGuiImplDocking.dockNodeGetWindowMenuButtonID(node));
            itemClick("//$FOCUSED/###HideTabBar");
        } else {
            // The unhide button is a small triangle in the top left corner of the node
            // Aim past the resize border hover padding of the host window, which overlaps it
            final float size = IkGuiInternal.truncate(IkGuiInternal.getFontSize() * 0.55f);
            final float offset = (size + context.windowBorderHoverPadding) * 0.5f;
            mouseMoveToPos(node.position.x + offset, node.position.y + offset);
            mouseClick(MouseButton.LEFT);
        }
        yieldFrames(2);
        assertEquals(hidden, node.isHiddenTabBar(), "hidden tab bar");
    }

    /**
     * Dock a window or dock node into another window or dock node, by dragging it there with the
     * mouse. The source is grabbed by its tab if it is docked with a visible tab bar, by the empty
     * part of the title bar if it is a node, and by the title bar otherwise. It is dropped on the
     * drop box for the split direction, using the same layout the library uses for its docking
     * preview.
     *
     * @param srcID The ID of the window or dock node to dock.
     * @param dstID The ID of the window or dock node to dock into.
     * @param splitDirection The direction to split the target in, or NONE to dock as a tab.
     * @param splitOuter Whether to split the root node of the target, using the outer drop boxes.
     */
    void dockInto(int srcID, int dstID, Direction splitDirection, boolean splitOuter) {
        final Window srcWindow = IkGuiInternal.findWindowByID(srcID);
        final DockNode srcNode =
                srcWindow == null ? IkGuiImplDocking.dockContextFindNodeByID(srcID) : null;
        assertTrue(srcWindow != null || srcNode != null, String.format("No source 0x%08X", srcID));
        final Window dstWindow = IkGuiInternal.findWindowByID(dstID);
        final DockNode dstNode =
                dstWindow == null ? IkGuiImplDocking.dockContextFindNodeByID(dstID) : null;
        assertTrue(dstWindow != null || dstNode != null, String.format("No target 0x%08X", dstID));

        // Bring the target to the front, so nothing else is in the way of the drop
        final Window dstFocus =
                dstWindow != null
                        ? dstWindow
                        : IkGuiImplDocking.dockNodeGetRootNode(dstNode).hostWindow;
        if (dstFocus != null) {
            bringPlatformWindowToFront(dstFocus);
            IkGuiInternal.focusWindow(dstFocus, WindowFocusRequestFlags.NONE);
            yieldFrames(2);
        }
        // Then bring the source above it, so it can be grabbed
        final Window srcFocus =
                srcWindow != null
                        ? dockHostOrSelf(srcWindow)
                        : IkGuiImplDocking.dockNodeGetRootNode(srcNode).hostWindow;
        if (srcFocus != null && srcFocus != dstFocus) {
            bringPlatformWindowToFront(srcFocus);
            IkGuiInternal.focusWindow(
                    srcWindow != null ? srcWindow : srcFocus, WindowFocusRequestFlags.NONE);
            yieldFrames(2);
        }

        // Grab the source
        final Vector2f grab;
        if (srcWindow != null) {
            final DockNode node = srcWindow.dockIsActive ? srcWindow.dockNode : null;
            if (node != null
                    && node.tabBar != null
                    && !node.isHiddenTabBar()
                    && !node.isNoTabBar()) {
                final ItemInfo tab = itemInfo(srcWindow.idTab, "tab of " + srcWindow.name);
                grab = new Vector2f(tab.rect.getCenterX(), tab.rect.getCenterY());
            } else {
                grab = windowTitleBarPoint(srcWindow);
            }
        } else if (srcNode.isRootNode()) {
            // Dragging the empty part of the title bar moves the whole dock tree
            grab = dockNodeTitleBarPoint(srcNode);
        } else {
            // Dragging the window menu button undocks the node
            final ItemInfo button =
                    itemInfo(
                            IkGuiImplDocking.dockNodeGetWindowMenuButtonID(srcNode),
                            "window menu button");
            grab = new Vector2f(button.rect.getCenterX(), button.rect.getCenterY());
        }
        mouseMoveToPos(grab.x, grab.y);
        mouseDown(MouseButton.LEFT);
        mouseLiftDragThreshold();

        // Find the drop box, now that dragging the source may have changed the layout
        final DockNode targetNode =
                dstNode != null ? dstNode : (dstWindow.dockIsActive ? dstWindow.dockNode : null);
        final RectFloat targetRect;
        if (targetNode != null) {
            final DockNode root = IkGuiImplDocking.dockNodeGetRootNode(targetNode);
            final DockNode reference = splitOuter || !targetNode.isVisible ? root : targetNode;
            targetRect =
                    new RectFloat(
                            reference.position.x,
                            reference.position.y,
                            reference.position.x + reference.size.x,
                            reference.position.y + reference.size.y);
        } else {
            targetRect = new RectFloat(dstWindow.getRect());
        }
        final RectFloat dropRect = new RectFloat();
        IkGuiImplDocking.dockNodeCalcDropRectsAndTestMousePos(
                targetRect, splitDirection, dropRect, splitOuter, null);
        mouseMoveToPos(dropRect.getCenterX(), dropRect.getCenterY());
        yieldFrame();
        mouseUp(MouseButton.LEFT);
        yieldFrames(2);
    }

    /**
     * Get a point in the empty part of a dock node's title bar, past its tabs. Dragging from there
     * moves the whole node.
     *
     * @param node The dock node.
     * @return The point, in screen coordinates.
     */
    static Vector2f dockNodeTitleBarPoint(DockNode node) {
        final TabBar tabBar = node.tabBar;
        if (tabBar == null || node.isHiddenTabBar() || node.isNoTabBar()) {
            return windowTitleBarPoint(node.hostWindow);
        }
        final RectFloat bar = tabBar.barRect;
        float tabsRight = bar.getLeft();
        for (TabItem tab : tabBar.tabs) {
            tabsRight =
                    Math.max(
                            tabsRight,
                            bar.getLeft() + tab.offset + tab.width - tabBar.scrollingAnimation);
        }
        return new Vector2f(
                (Math.min(tabsRight, bar.getRight()) + bar.getRight()) * 0.5f, bar.getCenterY());
    }

    /**
     * Whether a window is not docked, or is the only window in a floating dock node.
     *
     * @param window The window.
     * @return Whether the window is undocked or standalone.
     */
    static boolean windowIsUndockedOrStandalone(Window window) {
        if (window.dockNode == null) {
            return true;
        }
        return dockNodeIsStandalone(window.dockNode);
    }

    /**
     * Whether a dock ID refers to nothing, or to a floating node with at most one window.
     *
     * @param dockID The dock node ID, may be 0.
     * @return Whether the ID is undocked or standalone.
     */
    static boolean dockIdIsUndockedOrStandalone(int dockID) {
        if (dockID == 0) {
            return true;
        }
        final DockNode node = IkGuiImplDocking.dockContextFindNodeByID(dockID);
        return node == null || dockNodeIsStandalone(node);
    }

    private static boolean dockNodeIsStandalone(DockNode node) {
        return node.isRootNode()
                && !node.isDockSpace()
                && node.isLeafNode()
                && node.windows.size() <= 1;
    }

    /**
     * Undock a window by dragging its tab out of its dock node.
     *
     * @param ref The window reference.
     */
    void undockWindow(String ref) {
        final Window window = windowByRef(ref);
        assertNotNull(window.dockNode, "Window " + ref + " isn't docked");
        final ItemInfo tab = itemInfo(window.idTab, "tab of " + window.name);
        mouseMoveToPos(tab.rect.getCenterX(), tab.rect.getCenterY());
        mouseDown(MouseButton.LEFT);
        // Far enough below the tab bar to undock
        final float distance =
                Math.max(context.io.mouseDragThreshold, IkGuiInternal.getFontSize()) * 4;
        dragWithoutDocking(tab.rect.getCenterX(), tab.rect.getBottom() + distance);
    }

    /**
     * Undock a whole dock node by dragging its window menu button.
     *
     * @param nodeID The dock node ID.
     */
    void undockNode(int nodeID) {
        final DockNode node = IkGuiImplDocking.dockContextFindNodeByID(nodeID);
        assertNotNull(node, String.format("No dock node 0x%08X", nodeID));
        final ItemInfo button =
                itemInfo(
                        IkGuiImplDocking.dockNodeGetWindowMenuButtonID(node), "window menu button");
        final Vector2f grab = new Vector2f(button.rect.getCenterX(), button.rect.getCenterY());
        mouseMoveToPos(grab.x, grab.y);
        mouseDown(MouseButton.LEFT);
        final float distance =
                Math.max(context.io.mouseDragThreshold, IkGuiInternal.getFontSize()) * 4;
        dragWithoutDocking(grab.x, grab.y + distance);
    }

    /**
     * Move the held mouse to a position and release it there, with docking disabled. Holding Shift
     * disables docking, unless the IO is configured to only dock while holding Shift.
     *
     * @param x The x position to release at.
     * @param y The y position to release at.
     */
    private void dragWithoutDocking(float x, float y) {
        final boolean holdShift = !context.io.configDockingWithShift;
        if (holdShift) {
            keyDown(KeyChord.ofMods(KeyModFlags.SHIFT));
        }
        mouseMoveToPos(x, y);
        mouseUp(MouseButton.LEFT);
        if (holdShift) {
            keyUp(KeyChord.ofMods(KeyModFlags.SHIFT));
        }
        yieldFrames(2);
    }

    /**
     * Check that the tabs of a tab bar are in an order.
     *
     * @param tabBar The tab bar.
     * @param order The expected tab names, in order. Docked windows are matched by window name.
     * @return Whether the order matches.
     */
    static boolean tabBarCompareOrder(TabBar tabBar, String... order) {
        if (tabBar.tabs.size() != order.length) {
            return false;
        }
        for (int n = 0; n < order.length; n++) {
            final TabItem tab = tabBar.tabs.get(n);
            final String name =
                    tab.window != null ? tab.window.name : IkGuiImplTabs.tabBarGetTabName(tab);
            if (!order[n].equals(name)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Close a tab by clicking its close button.
     *
     * @param ref The tab reference.
     */
    void tabClose(String ref) {
        // The close button of an unselected tab only shows up when hovering the tab
        mouseMove(ref);
        itemClick(Hash.getID("#CLOSE", getID(ref)));
    }

    /**
     * Move a window by dragging its title bar, like a user would. Unlike windowMove(), this goes
     * through the normal input handling, so it doesn't work when something blocks the window.
     *
     * @param ref The window reference.
     * @param x The new x position.
     * @param y The new y position.
     */
    void windowMoveByDrag(String ref, float x, float y) {
        final Window window = windowByRef(ref);
        final Vector2f start = windowTitleBarPoint(window);
        mouseMoveToPos(start.x, start.y);
        mouseDown(MouseButton.LEFT);
        // Give the window a frame to start moving before the mouse does, like a user would
        yieldFrame();
        mouseMoveToPos(start.x + (x - window.position.x), start.y + (y - window.position.y));
        mouseUp(MouseButton.LEFT);
    }

    /** Close the top popup. */
    void popupCloseOne() {
        if (!context.openPopupStack.isEmpty()) {
            IkGuiImplPopups.closePopupToLevel(context.openPopupStack.size() - 1, true);
        }
        yieldFrames(2);
    }

    /**
     * Get the window ID of a popup that isn't a modal, like upstream's PopupGetWindowID().
     *
     * @param ref The reference of the popup, like "Window/Popup".
     * @return The window ID.
     */
    int popupGetWindowID(String ref) {
        return popupGetWindowID(getID(ref));
    }

    /**
     * Get the window ID of a popup that isn't a modal, like upstream's PopupGetWindowID().
     *
     * @param popupID The popup ID.
     * @return The window ID.
     */
    static int popupGetWindowID(int popupID) {
        return Hash.getID(String.format("##Popup_%08x", popupID));
    }

    /** Close all the popups. */
    void popupCloseAll() {
        if (!context.openPopupStack.isEmpty()) {
            IkGuiImplPopups.closePopupToLevel(0, true);
        }
        yieldFrames(2);
    }

    /**
     * Click through a menu path like "File/Recent/Item", starting from the menu bar of the
     * reference window, or from the reference window itself if it is a menu, popup, or window
     * without a menu bar.
     *
     * @param path The menu path.
     */
    void menuClick(String path) {
        menuAction(path, true);
    }

    /**
     * Hover through a menu path like "File/Recent/Item", opening each menu along the way by
     * hovering it, without clicking anything. Useful while a mouse button is held down.
     *
     * @param path The menu path.
     */
    void menuHover(String path) {
        menuAction(path, false);
    }

    private String menuAction(String path, boolean clickLast) {
        final boolean backup = focusOnMove;
        try {
            return menuActionSegments(path, clickLast);
        } finally {
            focusOnMove = backup;
        }
    }

    /**
     * Go through a menu path.
     *
     * @param path The menu path.
     * @param clickLast Whether to click the last item, or only hover it.
     * @return The reference of the last item.
     */
    private String menuActionSegments(String path, boolean clickLast) {
        String lastRef = null;
        final List<String> segments = new java.util.ArrayList<>(splitPath(path));
        final boolean absolute = path.startsWith("//");
        // The depth of the menu that the second segment opens
        int baseMenuDepth = 0;
        String firstPrefix = "";
        final Window refWindow;
        if (absolute) {
            // An absolute path starts with the window that has the menu
            final String windowSegment = escapeSegment(segments.removeFirst());
            refWindow = getWindowByRef("//" + windowSegment);
            firstPrefix = "//" + windowSegment + "/";
        } else {
            refWindow = getWindowByRef("");
        }
        if (refWindow != null) {
            final java.util.regex.Matcher menuName =
                    java.util.regex.Pattern.compile("###Menu_(\\d+)").matcher(refWindow.name);
            if (menuName.find()) {
                baseMenuDepth = Integer.parseInt(menuName.group(1)) + 1;
            } else if ((refWindow.flags & WindowFlags.MENU_BAR) != 0) {
                firstPrefix += "##MenuBar/";
            }
        }
        for (int depth = 0; depth < segments.size(); ++depth) {
            final String itemRef;
            if (depth == 0) {
                itemRef = firstPrefix + escapeSegment(segments.getFirst());
            } else {
                itemRef =
                        String.format(
                                "//###Menu_%02d/%s",
                                baseMenuDepth + depth - 1, escapeSegment(segments.get(depth)));
            }
            final ItemInfo item = itemInfo(itemRef);
            // The first item focuses its window like a user clicking into it, unless its menu is
            // already open. Moving along the rest of the path must not close the menus it opens.
            focusOnMove = depth == 0 && focusOnMove && !item.has(ItemStatusFlags.OPENED);
            final boolean last = depth == segments.size() - 1;
            boolean mouseHeld = false;
            for (boolean down : context.io.mouseDown) {
                mouseHeld |= down;
            }
            if (!clickLast && (last || item.has(ItemStatusFlags.OPENED) || mouseHeld)) {
                // Only hover. With a button held down, menus open on hover.
                mouseMove(itemRef);
            } else if (!last && item.has(ItemStatusFlags.OPENED)) {
                mouseMove(itemRef);
            } else {
                itemClick(itemRef);
            }
            lastRef = itemRef;
        }
        return lastRef;
    }

    /**
     * Check a checkable menu item, like upstream's MenuCheck(). The menus close afterward.
     *
     * @param path The menu path, like "Tools/Debug Log".
     */
    void menuCheck(String path) {
        menuSetChecked(path, true);
    }

    /**
     * Uncheck a checkable menu item, like upstream's MenuUncheck(). The menus close afterward.
     *
     * @param path The menu path, like "Tools/Debug Log".
     */
    void menuUncheck(String path) {
        menuSetChecked(path, false);
    }

    private void menuSetChecked(String path, boolean checked) {
        final String lastRef = menuAction(path, false);
        if (itemInfo(lastRef).has(ItemStatusFlags.CHECKED) != checked) {
            mouseClick(MouseButton.LEFT);
        }
        popupCloseAll();
    }

    /**
     * Check or uncheck every checkable item directly in a menu, like upstream's MenuCheckAll() and
     * MenuUncheckAll().
     *
     * @param menuPath The path of the menu, like "Examples".
     * @param checked True to check the items, false to uncheck them.
     */
    void menuSetAllChecked(String menuPath, boolean checked) {
        final int depth = splitPath(menuPath).size() - 1;
        final java.util.Set<Integer> done = new java.util.HashSet<>();
        for (int attempt = 0; attempt < 100; ++attempt) {
            menuClick(menuPath);
            final Window menu = getWindowByRef(String.format("//###Menu_%02d", depth));
            ItemInfo next = null;
            for (ItemInfo info : items.values()) {
                if (info.window == menu
                        && info.frame == lastSubmittedFrame()
                        && info.has(ItemStatusFlags.CHECKABLE)
                        && info.has(ItemStatusFlags.CHECKED) != checked
                        && !done.contains(info.id)
                        && (next == null || info.rect.getTop() < next.rect.getTop())) {
                    next = info;
                }
            }
            if (next == null) {
                popupCloseAll();
                return;
            }
            done.add(next.id);
            itemClick(next.id);
            popupCloseAll();
        }
    }

    /**
     * Open a combo and click each of its items in turn, like upstream's ComboClickAll().
     *
     * @param ref The combo reference.
     */
    void comboClickAll(String ref) {
        itemClick(ref);
        final Window popup = getWindowByRef("//##Combo_00");
        assertNotNull(popup, "No combo popup for " + ref);
        final List<Integer> ids = new ArrayList<>();
        for (ItemInfo info : items.values()) {
            if (info.window == popup && info.frame == lastSubmittedFrame()) {
                ids.add(info.id);
            }
        }
        popupCloseAll();
        for (int id : ids) {
            itemClick(ref);
            itemClick(id);
        }
        popupCloseAll();
    }

    /**
     * Open the closed openable items in a window and its child windows, like upstream's
     * ItemOpenAll(). Each pass opens the items that are showing, which shows more of them.
     *
     * @param ref The window reference, "" for the current reference.
     * @param maxPasses The maximum number of passes, or -1 for no limit.
     */
    void itemOpenAll(String ref, int maxPasses) {
        final Window window = windowByRef(ref);
        final java.util.Set<Integer> tried = new java.util.HashSet<>();
        for (int pass = 0; maxPasses < 0 ? pass < 100 : pass < maxPasses; ++pass) {
            final List<ItemInfo> closed = new ArrayList<>();
            for (ItemInfo info : items.values()) {
                if (info.window != null
                        && (info.window == window || info.window.rootWindow == window)
                        && info.frame == lastSubmittedFrame()
                        && info.has(ItemStatusFlags.OPENABLE)
                        && !info.has(ItemStatusFlags.OPENED)
                        && !tried.contains(info.id)) {
                    closed.add(info);
                }
            }
            if (closed.isEmpty()) {
                return;
            }
            closed.sort((a, b) -> Float.compare(a.rect.getTop(), b.rect.getTop()));
            for (ItemInfo info : closed) {
                tried.add(info.id);
                final ItemInfo current = items.get(info.id);
                if (current == null
                        || current.frame != lastSubmittedFrame()
                        || current.has(ItemStatusFlags.OPENED)) {
                    continue;
                }
                itemClick(info.id);
                yieldFrame();
                final ItemInfo after = items.get(info.id);
                if (after != null && !after.has(ItemStatusFlags.OPENED)) {
                    // Some tree nodes only open from their arrow
                    mouseMoveToPos(after.rect.getLeft() + 1, after.rect.getCenterY());
                    mouseClick(MouseButton.LEFT);
                    yieldFrame();
                }
                popupCloseAll();
            }
        }
    }

    /**
     * Press and hold the left mouse button on an item, like upstream's ItemHold().
     *
     * @param ref The item reference.
     * @param seconds How long to hold the button.
     */
    void itemHold(String ref, float seconds) {
        mouseMove(ref);
        mouseDown(MouseButton.LEFT);
        sleepNoSkip(seconds, 1.0f / 60.0f);
        mouseUp(MouseButton.LEFT);
    }

    private Window windowByRef(String ref) {
        final Window window = getWindowByRef(ref);
        assertNotNull(window, "No window " + ref);
        return window;
    }

    /**
     * Scroll a window to the top.
     *
     * @param ref The window reference.
     */
    void scrollToTop(String ref) {
        scrollToY(windowByRef(ref), 0.0f);
    }

    /**
     * Scroll a window to the top.
     *
     * @param window The window.
     */
    void scrollToTop(Window window) {
        scrollToY(window, 0.0f);
    }

    /**
     * Scroll a window to the bottom.
     *
     * @param ref The window reference.
     */
    void scrollToBottom(String ref) {
        scrollToBottom(windowByRef(ref));
    }

    /**
     * Scroll a window to the bottom.
     *
     * @param window The window.
     */
    void scrollToBottom(Window window) {
        scrollToY(window, window.scrollMax.y);
    }

    /**
     * Scroll a window vertically.
     *
     * @param ref The window reference.
     * @param scrollY The scroll position.
     */
    void scrollToY(String ref, float scrollY) {
        scrollToY(windowByRef(ref), scrollY);
    }

    /**
     * Scroll a window vertically.
     *
     * @param window The window.
     * @param scrollY The scroll position.
     */
    void scrollToY(Window window, float scrollY) {
        IkGuiInternal.setScrollY(window, scrollY);
        yieldFrames(2);
    }

    /**
     * Scroll a window horizontally.
     *
     * @param ref The window reference.
     * @param scrollX The scroll position.
     */
    void scrollToX(String ref, float scrollX) {
        scrollToX(windowByRef(ref), scrollX);
    }

    /**
     * Scroll a window horizontally.
     *
     * @param window The window.
     * @param scrollX The scroll position.
     */
    void scrollToX(Window window, float scrollX) {
        IkGuiInternal.setScrollX(window, scrollX);
        yieldFrames(2);
    }

    // ---------------------------------------------------------------------------------------------
    // Checks
    // ---------------------------------------------------------------------------------------------

    /** Failed checks from UI code, reported by {@link #assertNoErrors()}. */
    private final List<String> failedChecks = new ArrayList<>();

    /**
     * Check a condition from UI code, like upstream's IM_CHECK() in a GUI function. Failures are
     * recorded rather than thrown, so the frame can finish, and are reported by {@link
     * #assertNoErrors()}.
     *
     * @param condition The condition that should be true.
     * @param description What is being checked.
     */
    void check(boolean condition, String description) {
        if (!condition) {
            failedChecks.add("frame " + context.frameCount + ": " + description);
        }
    }

    /**
     * Check that two values are equal from UI code.
     *
     * @param expected The expected value.
     * @param actual The actual value.
     * @param description What is being checked.
     * @see #check(boolean, String)
     */
    void checkEquals(Object expected, Object actual, String description) {
        check(
                java.util.Objects.equals(expected, actual),
                description + ": expected " + expected + " but was " + actual);
    }

    /**
     * Expect the library to report errors, like upstream's NoRecoveryWarnings flag, so they don't
     * fail the test. Failed checks still do.
     */
    void expectErrors() {
        errorsExpected = true;
    }

    /** Whether errors reported by the library are expected, see {@link #expectErrors()}. */
    private boolean errorsExpected = false;

    /** Check that the library did not report any errors, and no checks from UI code failed. */
    void assertNoErrors() {
        assertTrue(failedChecks.isEmpty(), "Failed checks: " + failedChecks);
        if (errorsExpected) {
            return;
        }
        assertFalse(
                context.debugLogBuffer.snapshot().stream()
                        .anyMatch(line -> line.contains("[ikgui-error]")),
                context.debugLogBuffer.getText());
    }
}
