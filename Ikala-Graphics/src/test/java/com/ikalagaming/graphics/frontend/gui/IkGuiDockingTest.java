package com.ikalagaming.graphics.frontend.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.DockNode;
import com.ikalagaming.graphics.frontend.gui.data.IkBoolean;
import com.ikalagaming.graphics.frontend.gui.data.IkInt;
import com.ikalagaming.graphics.frontend.gui.data.Window;
import com.ikalagaming.graphics.frontend.gui.data.WindowClass;
import com.ikalagaming.graphics.frontend.gui.enums.Axis;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.Direction;
import com.ikalagaming.graphics.frontend.gui.enums.Key;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.flags.ConfigFlags;
import com.ikalagaming.graphics.frontend.gui.flags.DockNodeFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
import com.ikalagaming.graphics.frontend.gui.util.Hash;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Tests for docking. These run headless, without fonts loaded. */
class IkGuiDockingTest {

    /** How much precision we expect in float comparisons. */
    private static final float DELTA = 0.001f;

    private Context context;

    /** Which windows had their contents visible during the last frame. */
    private final List<String> visibleWindows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        // Don't read or write an .ini file
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        context.io.mouseInsideWindow = true;
        context.io.configFlags |= ConfigFlags.DOCKING_ENABLE;
        // Process one input event per frame for each input, so we can be precise about timing
        context.io.configInputTrickleEventQueue = true;
    }

    @AfterEach
    void tearDown() {
        IkGui.destroyContext();
    }

    private static void frame(Runnable ui) {
        IkGui.newFrame();
        ui.run();
        IkGui.render();
    }

    private static void frames(int count, Runnable ui) {
        for (int i = 0; i < count; ++i) {
            frame(ui);
        }
    }

    private void moveMouse(float x, float y) {
        context.io.addMousePosEvent(x, y);
    }

    /**
     * Drag the mouse from one point to another, in several steps.
     *
     * @param fromX The start x.
     * @param fromY The start y.
     * @param toX The end x.
     * @param toY The end y.
     * @param ui The UI for each frame.
     */
    private void drag(float fromX, float fromY, float toX, float toY, Runnable ui) {
        moveMouse(fromX, fromY);
        frame(ui);
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame(ui);
        final int steps = 8;
        for (int i = 1; i <= steps; ++i) {
            final float t = i / (float) steps;
            moveMouse(fromX + (toX - fromX) * t, fromY + (toY - fromY) * t);
            frame(ui);
        }
        // Hold still over the target so the drop preview is up to date
        frames(2, ui);
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frames(3, ui);
    }

    /**
     * Submit a simple window, tracking whether its contents are visible.
     *
     * @param name The window name.
     * @param x The initial x position, negative to not set a position. Setting a position undocks
     *     windows, as in ImGui.
     * @param y The initial y position.
     * @param open The open state, may be null.
     */
    private void window(String name, float x, float y, IkBoolean open) {
        if (x >= 0) {
            IkGui.setNextWindowPos(x, y, Condition.FIRST_USE_EVER);
        }
        IkGui.setNextWindowSize(300, 200, Condition.FIRST_USE_EVER);
        if (open != null && !open.get()) {
            return;
        }
        if (IkGui.begin(name, open, WindowFlags.NONE)) {
            visibleWindows.add(name);
            IkGui.text("Contents of " + name);
        }
        IkGui.end();
    }

    private Window find(String name) {
        final Window window = IkGuiInternal.findWindowByName(name);
        assertNotNull(window, "No window named " + name);
        return window;
    }

    private Runnable twoWindowsOverViewport() {
        return () -> {
            visibleWindows.clear();
            final int dockspaceID = IkGui.dockSpaceOverViewport();
            IkGui.setNextWindowDockID(dockspaceID, Condition.FIRST_USE_EVER);
            window("First", -1, -1, null);
            IkGui.setNextWindowDockID(dockspaceID, Condition.FIRST_USE_EVER);
            window("Second", -1, -1, null);
        };
    }

    @Test
    void dockingDisabledDoesNothing() {
        context.io.configFlags &= ~ConfigFlags.DOCKING_ENABLE;
        final int[] id = new int[1];
        frames(
                3,
                () -> {
                    IkGui.begin("Host", null, WindowFlags.NONE);
                    id[0] = IkGui.dockSpace(IkGui.getID("Space"));
                    IkGui.end();
                });
        assertEquals(0, id[0]);
        assertTrue(context.dockContext.nodes.isEmpty());
    }

    @Test
    void dockSpaceOverViewportCoversWorkArea() {
        final int[] id = new int[1];
        frames(3, () -> id[0] = IkGui.dockSpaceOverViewport());
        assertNotEquals(0, id[0]);
        final DockNode node = IkGuiInternal.dockBuilderGetNode(id[0]);
        assertNotNull(node);
        assertTrue(node.isDockSpace());
        assertTrue(node.isCentralNode());
        assertTrue(node.isRootNode());
        assertEquals(0, node.position.x, DELTA);
        assertEquals(0, node.position.y, DELTA);
        assertEquals(1280, node.size.x, DELTA);
        assertEquals(720, node.size.y, DELTA);
    }

    @Test
    void windowsDockedIntoTheSameNodeShareATabBar() {
        final Runnable ui = twoWindowsOverViewport();
        frames(5, ui);

        final Window first = find("First");
        final Window second = find("Second");
        assertTrue(first.dockIsActive);
        assertTrue(second.dockIsActive);
        assertSame(first.dockNode, second.dockNode);
        final DockNode node = first.dockNode;
        assertNotNull(node.tabBar);
        assertEquals(2, node.tabBar.tabs.size());
        assertTrue(
                IkGuiInternal.findWindowByName("First").dockTabIsVisible
                        ^ IkGuiInternal.findWindowByName("Second").dockTabIsVisible);

        // Only one of the docked windows shows its contents
        assertEquals(1, visibleWindows.size());

        // The docked windows fill the node
        assertEquals(node.position.x, first.position.x, DELTA);
        assertEquals(node.position.y, first.position.y, DELTA);
        assertEquals(node.size.x, first.size.x, DELTA);
        assertEquals(node.size.y, first.size.y, DELTA);
    }

    @Test
    void isWindowDockedAndGetWindowDockID() {
        final boolean[] docked = new boolean[2];
        final int[] dockIDs = new int[2];
        final int[] spaceID = new int[1];
        frames(
                5,
                () -> {
                    spaceID[0] = IkGui.dockSpaceOverViewport();
                    IkGui.setNextWindowDockID(spaceID[0], Condition.FIRST_USE_EVER);
                    IkGui.begin("Docked", null, WindowFlags.NONE);
                    docked[0] = IkGui.isWindowDocked();
                    dockIDs[0] = IkGui.getWindowDockID();
                    IkGui.end();
                    IkGui.begin("Floating", null, WindowFlags.NONE);
                    docked[1] = IkGui.isWindowDocked();
                    dockIDs[1] = IkGui.getWindowDockID();
                    IkGui.end();
                });
        assertTrue(docked[0]);
        assertEquals(spaceID[0], dockIDs[0]);
        assertFalse(docked[1]);
        assertEquals(0, dockIDs[1]);
    }

    @Test
    void clickingATabSelectsItsWindow() {
        final Runnable ui = twoWindowsOverViewport();
        frames(5, ui);
        final DockNode node = find("First").dockNode;
        final String hidden = find("First").dockTabIsVisible ? "Second" : "First";
        final Window hiddenWindow = find(hidden);

        final Vector2f tabPosition =
                IkGuiImplTabs.tabBarGetTabPos(
                        node.tabBar,
                        IkGuiInternal.tabBarFindTabByID(node.tabBar, hiddenWindow.idTab),
                        new Vector2f());
        moveMouse(tabPosition.x + 3, tabPosition.y + 3);
        frame(ui);
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame(ui);
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frames(3, ui);

        assertTrue(hiddenWindow.dockTabIsVisible);
        assertEquals(List.of(hidden), visibleWindows);
        assertSame(hiddenWindow, context.navFocusedWindow);
    }

    @Test
    void dockBuilderSplitLayout() {
        final int spaceID = Hash.getID("MainSpace");
        final IkInt left = new IkInt();
        final IkInt right = new IkInt();
        final Runnable ui =
                () -> {
                    visibleWindows.clear();
                    IkGui.setNextWindowPos(0, 0, Condition.ALWAYS);
                    IkGui.setNextWindowSize(1000, 600, Condition.ALWAYS);
                    IkGui.begin(
                            "Host", null, WindowFlags.NO_TITLE_BAR | WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGuiInternal.dockBuilderGetNode(spaceID) == null) {
                        IkGuiInternal.dockBuilderAddNode(
                                spaceID, DockNodeFlags.INTERNAL_DOCK_SPACE);
                        IkGuiInternal.dockBuilderSetNodeSize(spaceID, 984, 584);
                        IkGuiInternal.dockBuilderSplitNode(
                                spaceID, Direction.LEFT, 0.25f, left, right);
                        IkGuiInternal.dockBuilderDockWindow("Tools", left.get());
                        IkGuiInternal.dockBuilderDockWindow("Document", right.get());
                        IkGuiInternal.dockBuilderFinish(spaceID);
                    }
                    IkGui.dockSpace(spaceID);
                    IkGui.end();
                    window("Tools", -1, -1, null);
                    window("Document", -1, -1, null);
                };
        frames(5, ui);

        final DockNode root = IkGuiInternal.dockBuilderGetNode(spaceID);
        assertTrue(root.isSplitNode());
        assertEquals(Axis.X, root.splitAxis);
        final DockNode leftNode = IkGuiInternal.dockBuilderGetNode(left.get());
        final DockNode rightNode = IkGuiInternal.dockBuilderGetNode(right.get());
        assertSame(leftNode, root.childNodes[0]);
        assertSame(rightNode, root.childNodes[1]);
        assertTrue(rightNode.isCentralNode());
        assertSame(rightNode, IkGuiInternal.dockBuilderGetCentralNode(spaceID));

        final Window tools = find("Tools");
        final Window document = find("Document");
        assertSame(leftNode, tools.dockNode);
        assertSame(rightNode, document.dockNode);
        // Both are visible, as they are in different nodes
        assertTrue(visibleWindows.contains("Tools"));
        assertTrue(visibleWindows.contains("Document"));
        // A single window in a split node still docks
        assertTrue(tools.dockIsActive);

        // The left node is about a quarter of the width, and the nodes sit side by side
        final float separator = context.style.variable.dockingSeparatorSize;
        assertEquals(root.size.x - separator, leftNode.size.x + rightNode.size.x, DELTA);
        assertEquals(root.size.x * 0.25f, leftNode.size.x, 2.0f);
        assertEquals(
                leftNode.position.x + leftNode.size.x + separator, rightNode.position.x, DELTA);
        assertEquals(leftNode.position.x, tools.position.x, DELTA);
        assertEquals(rightNode.position.x, document.position.x, DELTA);
        assertEquals(rightNode.size.x, document.size.x, DELTA);
    }

    @Test
    void draggingTheSplitterResizesNodes() {
        final int spaceID = Hash.getID("SplitSpace");
        final IkInt left = new IkInt();
        final IkInt right = new IkInt();
        final Runnable ui =
                () -> {
                    if (IkGuiInternal.dockBuilderGetNode(spaceID) == null) {
                        IkGuiInternal.dockBuilderAddNode(
                                spaceID, DockNodeFlags.INTERNAL_DOCK_SPACE);
                        IkGuiInternal.dockBuilderSetNodeSize(spaceID, 1280, 720);
                        IkGuiInternal.dockBuilderSplitNode(
                                spaceID, Direction.LEFT, 0.5f, left, right);
                        IkGuiInternal.dockBuilderDockWindow("Left", left.get());
                        IkGuiInternal.dockBuilderDockWindow("Right", right.get());
                        IkGuiInternal.dockBuilderFinish(spaceID);
                    }
                    IkGui.dockSpaceOverViewport(spaceID, null);
                    window("Left", -1, -1, null);
                    window("Right", -1, -1, null);
                };
        frames(5, ui);
        final DockNode leftNode = IkGuiInternal.dockBuilderGetNode(left.get());
        final DockNode rightNode = IkGuiInternal.dockBuilderGetNode(right.get());
        final float originalLeftWidth = leftNode.size.x;
        final float splitterX = leftNode.position.x + leftNode.size.x + 1.0f;
        final float splitterY = leftNode.position.y + leftNode.size.y * 0.5f;

        drag(splitterX, splitterY, splitterX + 100, splitterY, ui);

        assertEquals(originalLeftWidth + 100, leftNode.size.x, 1.0f);
        assertEquals(
                leftNode.position.x + leftNode.size.x + context.style.variable.dockingSeparatorSize,
                rightNode.position.x,
                DELTA);
        assertEquals(leftNode.size.x, find("Left").size.x, DELTA);
    }

    @Test
    void dragWindowOntoAnotherWindowDocksIt() {
        final Runnable ui =
                () -> {
                    visibleWindows.clear();
                    window("Target", 100, 100, null);
                    window("Payload", 600, 400, null);
                };
        frames(3, ui);
        final Window target = find("Target");
        final Window payload = find("Payload");
        assertNull(target.dockNode);
        assertNull(payload.dockNode);

        // Drag the payload by its title bar onto the center drop box of the target
        final float targetCenterX = target.position.x + target.size.x * 0.5f;
        final float targetCenterY = target.position.y + target.size.y * 0.5f;
        drag(payload.position.x + 40, payload.position.y + 5, targetCenterX, targetCenterY, ui);
        frames(3, ui);

        assertNotNull(target.dockNode);
        assertSame(target.dockNode, payload.dockNode);
        final DockNode node = target.dockNode;
        assertTrue(node.isFloatingNode());
        assertEquals(2, node.windows.size());
        assertNotNull(node.hostWindow);
        assertTrue(target.dockIsActive);
        assertTrue(payload.dockIsActive);
        // The payload was dropped last, so it is selected
        assertTrue(payload.dockTabIsVisible);
        assertEquals(List.of("Payload"), visibleWindows);
        // The host window took the place of the target
        assertEquals(100, node.hostWindow.position.x, DELTA);
        assertEquals(100, node.hostWindow.position.y, DELTA);
    }

    @Test
    void dragWindowOntoTheSideOfAnotherWindowSplits() {
        final Runnable ui =
                () -> {
                    visibleWindows.clear();
                    window("Target", 100, 100, null);
                    window("Payload", 700, 400, null);
                };
        frames(3, ui);
        final Window target = find("Target");
        final Window payload = find("Payload");

        // Find the right drop box of the target
        final Vector2f dropPosition = new Vector2f();
        assertTrue(
                IkGuiInternal.dockContextCalcDropPosForDocking(
                        target, null, payload, null, Direction.RIGHT, false, dropPosition));
        drag(payload.position.x + 40, payload.position.y + 5, dropPosition.x, dropPosition.y, ui);
        frames(3, ui);

        assertNotNull(target.dockNode);
        assertNotNull(payload.dockNode);
        assertNotEquals(target.dockNode, payload.dockNode);
        final DockNode root = IkGuiInternal.dockNodeGetRootNode(target.dockNode);
        assertSame(root, IkGuiInternal.dockNodeGetRootNode(payload.dockNode));
        assertEquals(Axis.X, root.splitAxis);
        assertSame(target.dockNode, root.childNodes[0]);
        assertSame(payload.dockNode, root.childNodes[1]);
        assertTrue(visibleWindows.contains("Target"));
        assertTrue(visibleWindows.contains("Payload"));
        assertTrue(payload.position.x > target.position.x);
    }

    @Test
    void holdingShiftDisablesDocking() {
        final Runnable ui =
                () -> {
                    window("Target", 100, 100, null);
                    window("Payload", 600, 400, null);
                };
        frames(3, ui);
        final Window target = find("Target");
        final Window payload = find("Payload");
        final float targetCenterX = target.position.x + target.size.x * 0.5f;
        final float targetCenterY = target.position.y + target.size.y * 0.5f;
        context.io.addKeyEvent(Key.LEFT_SHIFT, true);
        frame(ui);
        drag(payload.position.x + 40, payload.position.y + 5, targetCenterX, targetCenterY, ui);
        frames(3, ui);

        assertNull(target.dockNode);
        assertNull(payload.dockNode);
        // The window just moved instead
        assertEquals(targetCenterX - 40, payload.position.x, 1.0f);
    }

    @Test
    void dragTabOutUndocksWindow() {
        final Runnable ui = twoWindowsOverViewport();
        frames(5, ui);
        final Window first = find("First");
        final Window second = find("Second");
        final DockNode node = first.dockNode;
        final Window dragged = first.dockTabIsVisible ? first : second;
        final Window remaining = dragged == first ? second : first;

        final Vector2f tabPosition =
                IkGuiImplTabs.tabBarGetTabPos(
                        node.tabBar,
                        IkGuiInternal.tabBarFindTabByID(node.tabBar, dragged.idTab),
                        new Vector2f());
        drag(tabPosition.x + 5, tabPosition.y + 5, tabPosition.x + 200, tabPosition.y + 300, ui);
        frames(3, ui);

        assertNull(dragged.dockNode);
        assertFalse(dragged.dockIsActive);
        assertSame(node, remaining.dockNode);
        assertEquals(1, node.windows.size());
        // The undocked window moved with the mouse
        assertTrue(dragged.position.y > node.position.y + 100);
    }

    @Test
    void closeButtonOfADockedWindowClosesIt() {
        final IkBoolean firstOpen = new IkBoolean(true);
        final IkBoolean secondOpen = new IkBoolean(true);
        final Runnable ui =
                () -> {
                    visibleWindows.clear();
                    final int dockspaceID = IkGui.dockSpaceOverViewport();
                    IkGui.setNextWindowDockID(dockspaceID, Condition.FIRST_USE_EVER);
                    window("First", -1, -1, firstOpen);
                    IkGui.setNextWindowDockID(dockspaceID, Condition.FIRST_USE_EVER);
                    window("Second", -1, -1, secondOpen);
                };
        frames(5, ui);
        final DockNode node = find("First").dockNode;
        assertTrue(node.hasCloseButton);

        // Close the visible window with the close button of the dock node
        final Vector2f closeButton = new Vector2f();
        IkGuiImplDocking.dockNodeCalcTabBarLayout(node, null, null, null, closeButton);
        final float buttonSize = IkGuiInternal.getFontSize();
        moveMouse(closeButton.x + buttonSize * 0.5f, closeButton.y + buttonSize * 0.5f);
        frame(ui);
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame(ui);
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frames(3, ui);

        // The close button of the node closes all windows in it
        assertFalse(firstOpen.get());
        assertFalse(secondOpen.get());
    }

    @Test
    void windowClassesPreventDocking() {
        final WindowClass toolClass = new WindowClass();
        toolClass.classID = 1234;
        toolClass.dockingAllowUnclassed = false;
        final Runnable ui =
                () -> {
                    window("Target", 100, 100, null);
                    IkGui.setNextWindowClass(toolClass);
                    window("Payload", 600, 400, null);
                };
        frames(3, ui);
        final Window target = find("Target");
        final Window payload = find("Payload");
        assertEquals(1234, payload.windowClass.classID);
        assertFalse(IkGuiImplDocking.dockNodeIsDropAllowed(target, payload));

        final float targetCenterX = target.position.x + target.size.x * 0.5f;
        final float targetCenterY = target.position.y + target.size.y * 0.5f;
        drag(payload.position.x + 40, payload.position.y + 5, targetCenterX, targetCenterY, ui);
        frames(3, ui);
        assertNull(target.dockNode);
        assertNull(payload.dockNode);
    }

    @Test
    void keepAliveOnlyKeepsWindowsDocked() {
        final boolean[] showDockspace = {true};
        final int spaceID = Hash.getID("KeepAlive");
        final Runnable ui =
                () -> {
                    visibleWindows.clear();
                    IkGui.setNextWindowPos(0, 0, Condition.ALWAYS);
                    IkGui.setNextWindowSize(800, 600, Condition.ALWAYS);
                    IkGui.begin("Host", null, WindowFlags.NONE);
                    IkGui.dockSpace(
                            spaceID,
                            0,
                            0,
                            showDockspace[0] ? DockNodeFlags.NONE : DockNodeFlags.KEEP_ALIVE_ONLY);
                    IkGui.end();
                    IkGui.setNextWindowDockID(spaceID, Condition.FIRST_USE_EVER);
                    window("Docked", -1, -1, null);
                };
        frames(5, ui);
        final Window docked = find("Docked");
        assertNotNull(docked.dockNode);
        assertTrue(docked.dockIsActive);

        showDockspace[0] = false;
        frames(3, ui);
        assertNotNull(docked.dockNode);
        assertEquals(spaceID, docked.dockID);

        showDockspace[0] = true;
        frames(3, ui);
        assertNotNull(docked.dockNode);
        assertTrue(docked.dockIsActive);
        assertTrue(visibleWindows.contains("Docked"));
    }

    @Test
    void removingTheDockspaceUndocksWindows() {
        final boolean[] showDockspace = {true};
        final int spaceID = Hash.getID("Removed");
        final Runnable ui =
                () -> {
                    IkGui.setNextWindowPos(0, 0, Condition.ALWAYS);
                    IkGui.setNextWindowSize(800, 600, Condition.ALWAYS);
                    IkGui.begin("Host", null, WindowFlags.NONE);
                    if (showDockspace[0]) {
                        IkGui.dockSpace(spaceID);
                    }
                    IkGui.end();
                    IkGui.setNextWindowDockID(spaceID, Condition.FIRST_USE_EVER);
                    window("Docked", -1, -1, null);
                };
        frames(5, ui);
        final Window docked = find("Docked");
        assertNotNull(docked.dockNode);

        showDockspace[0] = false;
        frames(3, ui);
        assertNull(docked.dockNode);
        assertFalse(docked.dockIsActive);
    }

    @Test
    void settingsRoundTrip() {
        final int spaceID = Hash.getID("SavedSpace");
        final IkInt left = new IkInt();
        final IkInt right = new IkInt();
        final Runnable ui =
                () -> {
                    if (IkGuiInternal.dockBuilderGetNode(spaceID) == null) {
                        IkGuiInternal.dockBuilderAddNode(
                                spaceID, DockNodeFlags.INTERNAL_DOCK_SPACE);
                        IkGuiInternal.dockBuilderSetNodeSize(spaceID, 1280, 720);
                        IkGuiInternal.dockBuilderSplitNode(
                                spaceID, Direction.UP, 0.3f, left, right);
                        IkGuiInternal.dockBuilderDockWindow("Top", left.get());
                        IkGuiInternal.dockBuilderDockWindow("Bottom A", right.get());
                        IkGuiInternal.dockBuilderDockWindow("Bottom B", right.get());
                        IkGuiInternal.dockBuilderFinish(spaceID);
                    }
                    IkGui.dockSpaceOverViewport(spaceID, null);
                    window("Top", -1, -1, null);
                    window("Bottom A", -1, -1, null);
                    window("Bottom B", -1, -1, null);
                };
        frames(5, ui);
        final DockNode bottom = IkGuiInternal.dockBuilderGetNode(right.get());
        final float topHeight = IkGuiInternal.dockBuilderGetNode(left.get()).size.y;
        final String ini = IkGui.saveIniSettingsToMemory();
        assertTrue(ini.contains("[Docking][Data]"), ini);
        assertTrue(
                ini.contains(String.format("DockSpace ID=0x%08X", spaceID))
                        || ini.contains(String.format("DockSpace   ID=0x%08X", spaceID)),
                ini);
        assertTrue(ini.contains(String.format("DockId=0x%08X", right.get())), ini);
        assertTrue(ini.contains("Split=Y"), ini);
        assertTrue(ini.contains("CentralNode=1"), ini);
        assertEquals(2, bottom.windows.size());

        // Load into a fresh context, the layout should be restored without the builder
        IkGui.destroyContext();
        context = IkGui.createContext();
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        context.io.mouseInsideWindow = true;
        context.io.configFlags |= ConfigFlags.DOCKING_ENABLE;
        IkGui.loadIniSettingsFromMemory(ini);
        final Runnable reloaded =
                () -> {
                    IkGui.dockSpaceOverViewport(spaceID, null);
                    window("Top", -1, -1, null);
                    window("Bottom A", -1, -1, null);
                    window("Bottom B", -1, -1, null);
                };
        frames(5, reloaded);

        final Window top = find("Top");
        final Window bottomA = find("Bottom A");
        final Window bottomB = find("Bottom B");
        assertEquals(left.get(), top.dockID);
        assertEquals(right.get(), bottomA.dockID);
        assertSame(bottomA.dockNode, bottomB.dockNode);
        assertEquals(2, bottomA.dockNode.windows.size());
        assertEquals(topHeight, top.dockNode.size.y, 1.0f);
        assertEquals(Axis.Y, IkGuiInternal.dockBuilderGetNode(spaceID).splitAxis);
    }

    @Test
    void setNextWindowPosUndocks() {
        final boolean[] move = {false};
        final Runnable ui =
                () -> {
                    final int dockspaceID = IkGui.dockSpaceOverViewport();
                    IkGui.setNextWindowDockID(dockspaceID, Condition.FIRST_USE_EVER);
                    if (move[0]) {
                        IkGui.setNextWindowPos(300, 300, Condition.ALWAYS);
                    }
                    IkGui.begin("Docked", null, WindowFlags.NONE);
                    IkGui.end();
                };
        frames(5, ui);
        final Window docked = find("Docked");
        assertNotNull(docked.dockNode);

        move[0] = true;
        frames(2, ui);
        assertNull(docked.dockNode);
        assertEquals(300, docked.position.x, DELTA);
    }

    @Test
    void noDockingWindowsAreNotDocked() {
        frames(
                5,
                () -> {
                    final int dockspaceID = IkGui.dockSpaceOverViewport();
                    IkGui.setNextWindowDockID(dockspaceID, Condition.ALWAYS);
                    IkGui.begin("Undockable", null, WindowFlags.NO_DOCKING);
                    IkGui.end();
                });
        assertNull(find("Undockable").dockNode);
    }

    @Test
    void alwaysTabBarGivesSingleWindowsATabBar() {
        context.io.configDockingAlwaysTabBar = true;
        final Runnable ui =
                () -> {
                    visibleWindows.clear();
                    window("Alone", 100, 100, null);
                };
        frames(6, ui);
        final Window alone = find("Alone");
        assertNotNull(alone.dockNode);
        assertNotNull(alone.dockNode.hostWindow);
        assertTrue(alone.dockIsActive);
        assertNotNull(alone.dockNode.tabBar);
        assertEquals(1, alone.dockNode.tabBar.tabs.size());
        assertEquals(List.of("Alone"), visibleWindows);
    }

    @Test
    void dockBuilderRemoveNodeUndocksEverything() {
        final int spaceID = Hash.getID("RemoveMe");
        final Runnable ui =
                () -> {
                    IkGui.dockSpaceOverViewport(spaceID, null);
                    IkGui.setNextWindowDockID(spaceID, Condition.FIRST_USE_EVER);
                    window("One", -1, -1, null);
                    IkGui.setNextWindowDockID(spaceID, Condition.FIRST_USE_EVER);
                    window("Two", -1, -1, null);
                };
        frames(5, ui);
        assertNotNull(find("One").dockNode);

        IkGui.newFrame();
        IkGuiInternal.dockBuilderRemoveNode(spaceID);
        IkGui.render();
        assertNull(find("One").dockNode);
        assertNull(find("Two").dockNode);
        assertEquals(0, find("One").dockID);
    }

    @Test
    void autoHideTabBarHidesSingleWindowTabBars() {
        final int spaceID = Hash.getID("AutoHide");
        final Runnable ui =
                () -> {
                    visibleWindows.clear();
                    IkGui.dockSpaceOverViewport(spaceID, null, DockNodeFlags.AUTO_HIDE_TAB_BAR);
                    IkGui.setNextWindowDockID(spaceID, Condition.FIRST_USE_EVER);
                    window("Single", -1, -1, null);
                };
        frames(6, ui);
        final Window single = find("Single");
        assertNotNull(single.dockNode);
        assertTrue(single.dockNode.isHiddenTabBar());
        assertTrue((single.flags & WindowFlags.NO_TITLE_BAR) != 0);
        assertEquals(List.of("Single"), visibleWindows);
    }
}
