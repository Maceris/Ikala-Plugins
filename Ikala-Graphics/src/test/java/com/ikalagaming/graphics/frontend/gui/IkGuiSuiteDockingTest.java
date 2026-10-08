package com.ikalagaming.graphics.frontend.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.frontend.gui.data.DockNode;
import com.ikalagaming.graphics.frontend.gui.data.IkInt;
import com.ikalagaming.graphics.frontend.gui.data.IkString;
import com.ikalagaming.graphics.frontend.gui.data.TabBar;
import com.ikalagaming.graphics.frontend.gui.data.Viewport;
import com.ikalagaming.graphics.frontend.gui.data.Window;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.Direction;
import com.ikalagaming.graphics.frontend.gui.enums.Key;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.enums.StyleVariable;
import com.ikalagaming.graphics.frontend.gui.flags.ChildFlags;
import com.ikalagaming.graphics.frontend.gui.flags.DockNodeFlags;
import com.ikalagaming.graphics.frontend.gui.flags.KeyModFlags;
import com.ikalagaming.graphics.frontend.gui.flags.TabItemFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
import com.ikalagaming.graphics.frontend.gui.util.Hash;
import com.ikalagaming.graphics.frontend.gui.util.KeyChord;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Docking tests ported from the Dear ImGui test suite (imgui_tests_docking.cpp), which is MIT
 * licensed. Each test notes the name of the upstream test it is ported from.
 *
 * <p>The upstream test engine docks windows by dragging them with the mouse. The driver does the
 * same with its own helpers (see {@link IkGuiTestContext#dockInto(int, int,
 * com.ikalagaming.graphics.frontend.gui.enums.Direction, boolean)}), dropping on the boxes the
 * library uses for its docking preview.
 *
 * <p>Upstream clears the docking state of the windows on the first frame of many tests, so other
 * tests don't affect them. Every test here starts with a fresh context, so that's left out.
 */
class IkGuiSuiteDockingTest {
    private IkGuiTestContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new IkGuiTestContext();
    }

    @AfterEach
    void tearDown() {
        try {
            ctx.assertNoErrors();
        } finally {
            ctx.destroy();
        }
    }

    /** The state shared by many of the docking tests, like upstream's DockingTestsGenericVars. */
    private static class DockingTestsGenericVars {
        int step = 0;
        int dockSpaceID = 0;
        final int[] nodesID = new int[10];
        boolean showDockspace = true;
        final boolean[] showWindow = new boolean[10];
        final boolean[] showWindowGroups = {true, true};
        final int[] appearingCount = new int[10];

        void setShowWindows(int count, boolean show) {
            for (int i = 0; i < count; i++) {
                showWindow[i] = show;
            }
        }
    }

    /**
     * Get a window name for the generic docking GUI.
     *
     * @param n The window index.
     * @return "AAA", "BBB", "CCC", and so on.
     */
    private static String dockingTestsGetWindowName(int n) {
        final char c = (char) ('A' + n);
        return "" + c + c + c;
    }

    /**
     * The generic docking test GUI: a "Test Window" with a dockspace, and the windows that are
     * shown. Upstream also shows a config window for interactive use.
     */
    private static void dockingTestsGenericGuiFunc(DockingTestsGenericVars vars) {
        IkGui.setNextWindowSize(
                200, 200, com.ikalagaming.graphics.frontend.gui.enums.Condition.APPEARING);
        IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
        if (vars.showDockspace) {
            vars.dockSpaceID = IkGui.getID("dockspace");
            IkGui.dockSpace(vars.dockSpaceID);
        }
        IkGui.end();

        for (int n = 0; n < vars.showWindow.length; n++) {
            if (vars.showWindow[n] && vars.showWindowGroups[n / 5]) {
                IkGui.setNextWindowSize(
                        300.0f,
                        200.0f,
                        com.ikalagaming.graphics.frontend.gui.enums.Condition.APPEARING);
                final String windowName = dockingTestsGetWindowName(n);
                IkGui.begin(windowName, null, WindowFlags.NO_SAVED_SETTINGS);
                vars.appearingCount[n] += IkGui.isWindowAppearing() ? 1 : 0;
                IkGui.text("This is '" + windowName + "'");
                IkGui.text(
                        String.format(
                                "ID = %08X, DockID = %08X",
                                IkGuiInternal.context.windowCurrent.id, IkGui.getWindowDockID()));
                IkGui.text("AppearingCount = " + vars.appearingCount[n]);
                IkGui.button("Button 1");
                IkGui.button("Button 2");
                IkGui.end();
            }
        }
    }

    /** Check that two vectors are equal. */
    private static void assertVec(float x, float y, Vector2f actual, String description) {
        assertEquals(x, actual.x, description + " x");
        assertEquals(y, actual.y, description + " y");
    }

    /** docking_move_does_not_dock: moving windows over each other doesn't dock them. */
    @Test
    void testDockingMoveDoesNotDock() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.end();
                    IkGui.begin("Window 2", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.end();
                });
        final Vector2f viewportPos = IkGui.getMainViewport().position;
        ctx.windowResize("Window 1", 200, 200);
        ctx.windowResize("Window 2", 200, 200);
        ctx.windowMove("Window 1", viewportPos.x + 300, viewportPos.y + 300);
        ctx.windowMove("Window 2", viewportPos.x + 300, viewportPos.y + 300);
        ctx.windowMove("Window 1", viewportPos.x + 100, viewportPos.y + 100);
        ctx.windowMove("Window 2", viewportPos.x + 100, viewportPos.y + 100);
        assertTrue(IkGuiTestContext.windowIsUndockedOrStandalone(ctx.getWindowByRef("Window 1")));
        assertTrue(IkGuiTestContext.windowIsUndockedOrStandalone(ctx.getWindowByRef("Window 2")));
    }

    /** docking_basic_1: docking windows by dragging them. */
    @Test
    void testDockingBasic1() {
        final DockingTestsGenericVars vars = new DockingTestsGenericVars();
        vars.setShowWindows(2, true);
        ctx.setGui(() -> dockingTestsGenericGuiFunc(vars));
        final var io = IkGuiInternal.context.io;
        for (int testCase = 0; testCase < 2; testCase++) {
            io.configDockingAlwaysTabBar = testCase == 1;
            final String d = "always tab bar " + io.configDockingAlwaysTabBar;

            final Window windowA = ctx.getWindowByRef("AAA");
            final Window windowB = ctx.getWindowByRef("BBB");
            final Vector2f viewportPos = IkGui.getMainViewport().position;

            // The initial state
            ctx.dockClear("AAA", "BBB");
            if (io.configDockingAlwaysTabBar) {
                assertTrue(
                        windowA.dockID != 0
                                && windowB.dockID != 0
                                && windowA.dockID != windowB.dockID,
                        d);
            } else {
                assertTrue(windowA.dockID == 0 && windowB.dockID == 0, d);
            }
            ctx.windowResize("//AAA", 200, 200);
            ctx.windowMove("//AAA", viewportPos.x + 100, viewportPos.y + 100);
            ctx.windowResize("//BBB", 200, 200);
            ctx.windowMove("//BBB", viewportPos.x + 200, viewportPos.y + 200);

            // Dock once
            ctx.dockInto("AAA", "BBB");
            assertNotNull(windowA.dockNode, d);
            assertSame(windowA.dockNode, windowB.dockNode, d);
            assertVec(
                    viewportPos.x + 200,
                    viewportPos.y + 200,
                    windowA.dockNode.position,
                    d + " node");
            assertVec(viewportPos.x + 200, viewportPos.y + 200, windowA.position, d + " AAA");
            assertVec(viewportPos.x + 200, viewportPos.y + 200, windowB.position, d + " BBB");
            final int dockID = windowB.dockID;
            ctx.sleep(0.5f);

            {
                // Undock AAA, and BBB still refers to the node
                ctx.dockClear("AAA");
                assertTrue(IkGuiTestContext.windowIsUndockedOrStandalone(windowA), d);
                assertEquals(dockID, windowB.dockID, d);

                // Intentionally move both floating windows away
                ctx.windowMove("//AAA", viewportPos.x + 100, viewportPos.y + 100);
                ctx.windowResize("//AAA", 100, 100);
                ctx.windowMove("//BBB", viewportPos.x + 300, viewportPos.y + 300);
                // This should already be the case
                ctx.windowResize("//BBB", 200, 200);

                // Dock again. BBB still refers to the dock ID, which makes this different from the
                // first docking.
                ctx.dockInto("//AAA", "//BBB", Direction.NONE);
                assertEquals(dockID, windowA.dockID, d);
                assertEquals(dockID, windowB.dockID, d);
                assertVec(
                        viewportPos.x + 300,
                        viewportPos.y + 300,
                        windowA.position,
                        d + " AAA again");
                assertVec(
                        viewportPos.x + 300,
                        viewportPos.y + 300,
                        windowB.position,
                        d + " BBB again");
                assertVec(200, 200, windowA.size, d + " AAA size");
                assertVec(200, 200, windowB.size, d + " BBB size");
                assertVec(
                        viewportPos.x + 300,
                        viewportPos.y + 300,
                        windowA.dockNode.position,
                        d + " node again");
                assertVec(200, 200, windowA.dockNode.size, d + " node size");
            }

            {
                // Undock AAA, and BBB still refers to the node
                ctx.dockClear("AAA");
                assertTrue(IkGuiTestContext.windowIsUndockedOrStandalone(windowA), d);
                assertEquals(dockID, windowB.dockID, d);

                // Intentionally move both floating windows away
                ctx.windowMove("//AAA", viewportPos.x + 100, viewportPos.y + 100);
                ctx.windowMove("//BBB", viewportPos.x + 200, viewportPos.y + 200);

                // Dock on the side. BBB still refers to the dock ID, which makes this different
                // from the first docking.
                ctx.dockInto("//AAA", "//BBB", Direction.LEFT);
                assertNotNull(windowA.dockNode, d);
                assertEquals(dockID, windowA.dockNode.parentNode.id, d);
                assertEquals(dockID, windowB.dockNode.parentNode.id, d);
                assertVec(
                        viewportPos.x + 200,
                        viewportPos.y + 200,
                        windowA.dockNode.parentNode.position,
                        d + " parent");
                assertVec(
                        viewportPos.x + 200,
                        viewportPos.y + 200,
                        windowA.position,
                        d + " AAA side");
                assertTrue(windowB.position.x > windowA.position.x, d);
                assertEquals(viewportPos.y + 200, windowB.position.y, d);
            }
        }
    }

    /** docking_api_builder_1: basic use of the DockBuilder API. */
    @Test
    void testDockingApiBuilder1() {
        // Emulates upstream's frame count, which is 0 on the first frame after the warm-up
        final int[] frame = {-2};
        final int[] dockID = {0};
        final boolean[] checked = {false, false};
        ctx.setGui(
                () -> {
                    final int[] ids = new int[3];
                    IkGui.begin("AAAA", null, WindowFlags.NO_SAVED_SETTINGS);
                    ids[0] = IkGui.getWindowDockID();
                    IkGui.text("This is AAAA");
                    IkGui.end();

                    IkGui.begin("BBBB", null, WindowFlags.NO_SAVED_SETTINGS);
                    ids[1] = IkGui.getWindowDockID();
                    IkGui.text("This is BBBB");
                    IkGui.end();

                    IkGui.begin("CCCC", null, WindowFlags.NO_SAVED_SETTINGS);
                    ids[2] = IkGui.getWindowDockID();
                    IkGui.text("This is CCCC (longer)");
                    IkGui.end();

                    if (frame[0] == 1) {
                        ctx.check(
                                IkGuiTestContext.dockIdIsUndockedOrStandalone(ids[0]),
                                "AAAA undocked");
                        ctx.check(
                                IkGuiTestContext.dockIdIsUndockedOrStandalone(ids[1]),
                                "BBBB undocked");
                        ctx.check(
                                IkGuiTestContext.dockIdIsUndockedOrStandalone(ids[2]),
                                "CCCC undocked");
                        checked[0] = true;
                    }
                    if (frame[0] == 11) {
                        ctx.check(dockID[0] != 0, "dock ID");
                        ctx.checkEquals(dockID[0], ids[0], "AAAA docked");
                        ctx.check(ids[0] == ids[1] && ids[0] == ids[2], "all docked");
                        checked[1] = true;
                    }
                    frame[0]++;
                });
        ctx.dockClear("AAAA", "BBBB", "CCCC");
        while (frame[0] < 10) {
            ctx.yieldFrame();
        }

        dockID[0] = IkGuiInternal.dockBuilderAddNode(0, DockNodeFlags.NONE);
        final Vector2f viewportPos = IkGui.getMainViewport().position;
        IkGuiInternal.dockBuilderSetNodePos(dockID[0], viewportPos.x + 100, viewportPos.y + 100);
        IkGuiInternal.dockBuilderSetNodeSize(dockID[0], 200, 200);
        IkGuiInternal.dockBuilderDockWindow("AAAA", dockID[0]);
        IkGuiInternal.dockBuilderDockWindow("BBBB", dockID[0]);
        IkGuiInternal.dockBuilderDockWindow("CCCC", dockID[0]);
        while (frame[0] < 20) {
            ctx.yieldFrame();
        }
        assertTrue(checked[0] && checked[1]);
    }

    /** docking_api_set_next: the setNextWindowDockID() API. */
    @Test
    void testDockingApiSetNext() {
        final int[] frame = {-2};
        final int[] id = {0};
        final boolean[] finished = {false};
        ctx.setGui(
                () -> {
                    if (finished[0]) {
                        return;
                    }
                    if (id[0] == 0) {
                        id[0] = IkGuiInternal.dockContextGenerateNodeID();
                    }

                    IkGui.setNextWindowDockID(id[0], Condition.ALWAYS);
                    IkGui.begin("AAAA", null, WindowFlags.NO_SAVED_SETTINGS);
                    ctx.checkEquals(id[0], IkGui.getWindowDockID(), "AAAA");
                    IkGui.text("This is AAAA");
                    IkGui.end();

                    IkGui.setNextWindowDockID(id[0], Condition.ALWAYS);
                    IkGui.begin("BBBB", null, WindowFlags.NO_SAVED_SETTINGS);
                    ctx.checkEquals(id[0], IkGui.getWindowDockID(), "BBBB");
                    IkGui.text("This is BBBB");
                    IkGui.end();

                    if (frame[0] == 3) {
                        finished[0] = true;
                    }
                    frame[0]++;
                });
        while (!finished[0]) {
            ctx.yieldFrame();
        }
    }

    /**
     * docking_into_parent_node: docking into a parent node forwards the docking into the central
     * node or the last focused node.
     */
    @Test
    void testDockingIntoParentNode() {
        final int[] frame = {-2};
        final int[] ids = new int[2];
        final boolean[] checked = {false};
        ctx.setGui(
                () -> {
                    final int dockspaceID = Hash.getID("Dockspace");
                    // Upstream does this on the first test frame, which is after the warm-up
                    if (frame[0] == 0) {
                        IkGuiInternal.dockBuilderRemoveNode(dockspaceID);
                        IkGuiInternal.dockBuilderDockWindow("AAAA", 0);
                        IkGuiInternal.dockBuilderDockWindow("BBBB", 0);
                    }
                    if (frame[0] == 2) {
                        final IkInt split0 = new IkInt();
                        final IkInt split1 = new IkInt();
                        IkGuiInternal.dockBuilderSplitNode(
                                dockspaceID, Direction.LEFT, 0.5f, split0, split1);
                        ids[0] = split0.get();
                        ids[1] = split1.get();
                        ctx.check(ids[0] != 0 && ids[1] != 0, "split IDs");
                        // Try docking BBBB into the root dockspace
                        IkGuiInternal.dockBuilderDockWindow("BBBB", dockspaceID);
                        final Window window = IkGuiInternal.findWindowByName("BBBB");
                        // We've been forwarded
                        ctx.check(window.dockID != dockspaceID, "forwarded");
                        ctx.checkEquals(ids[1], window.dockID, "forwarded to the other node");
                        checked[0] = true;
                    }

                    IkGui.begin("AAAA", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.dockSpace(dockspaceID);
                    IkGui.end();

                    IkGui.begin("BBBB", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.end();
                    frame[0]++;
                });
        while (frame[0] <= 10) {
            ctx.yieldFrame();
        }
        assertTrue(checked[0]);
    }

    /** docking_auto_nodes_size: always-tab-bar transitions don't break the window size. */
    @Test
    void testDockingAutoNodesSize() {
        final int[] frame = {-2};
        ctx.setGui(
                () -> {
                    final float expected = 200;
                    if (frame[0] == 0) {
                        IkGui.setNextWindowDockID(0);
                        IkGui.setNextWindowSize(expected, expected, Condition.ALWAYS);
                    }
                    IkGui.begin("AAAA", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.text("This is AAAA");
                    final Vector2f actual = IkGui.getWindowSize();
                    if (frame[0] >= 0) {
                        ctx.checkEquals(expected, actual.x, "width at frame " + frame[0]);
                        ctx.checkEquals(expected, actual.y, "height at frame " + frame[0]);
                    }
                    IkGui.end();
                    frame[0]++;
                });
        // The test function starts after the warm-up
        ctx.yieldFrame();
        final var io = IkGuiInternal.context.io;
        io.configDockingAlwaysTabBar = false;
        ctx.yieldFrames(4);
        io.configDockingAlwaysTabBar = true;
        ctx.yieldFrames(4);
        io.configDockingAlwaysTabBar = false;
        ctx.yieldFrames(4);
    }

    /**
     * docking_focus_1: focusing a docked window, and an item inside it, while its tab selection is
     * delayed.
     */
    @Test
    void testDockingFocus1() {
        final int[] frame = {-2};
        final int[] dockID = {0};
        final IkString str1 = new IkString(64);
        final IkString str2 = new IkString(64);
        final boolean[] checked = new boolean[4];
        ctx.setGui(
                () -> {
                    if (frame[0] == 0) {
                        dockID[0] = IkGuiInternal.dockBuilderAddNode(0, DockNodeFlags.NONE);
                        final Vector2f viewportPos = IkGui.getMainViewport().position;
                        IkGuiInternal.dockBuilderSetNodePos(
                                dockID[0], viewportPos.x + 100, viewportPos.y + 100);
                        IkGuiInternal.dockBuilderSetNodeSize(dockID[0], 200, 200);
                        IkGuiInternal.dockBuilderDockWindow("AAAA", dockID[0]);
                        IkGuiInternal.dockBuilderDockWindow("BBBB", dockID[0]);
                        IkGuiInternal.dockBuilderDockWindow("CCCC", dockID[0]);
                    }

                    if (frame[0] == 10) {
                        IkGui.setNextWindowFocus();
                    }
                    IkGui.begin("AAAA", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (frame[0] == 10) {
                        ctx.check(IkGui.isWindowFocused(), "AAAA focused");
                        IkGui.setKeyboardFocusHere();
                    }
                    IkGui.inputText("Input", str1);
                    if (frame[0] == 10) {
                        ctx.check(!IkGui.isItemActive(), "AAAA input not active yet");
                        checked[0] = true;
                    }
                    if (frame[0] == 11) {
                        ctx.check(IkGui.isItemActive(), "AAAA input active");
                        checked[1] = true;
                    }
                    IkGui.end();

                    if (frame[0] == 50) {
                        IkGui.setNextWindowFocus();
                    }
                    IkGui.begin("BBBB", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (frame[0] == 50) {
                        ctx.check(IkGui.isWindowFocused(), "BBBB focused");
                        IkGui.setKeyboardFocusHere();
                    }
                    IkGui.inputText("Input", str2);
                    if (frame[0] == 50) {
                        ctx.check(!IkGui.isItemActive(), "BBBB input not active yet");
                        checked[2] = true;
                    }
                    if (frame[0] == 51) {
                        ctx.check(IkGui.isItemActive(), "BBBB input active");
                        checked[3] = true;
                    }
                    IkGui.end();
                    frame[0]++;
                });
        while (frame[0] <= 60) {
            ctx.yieldFrame();
        }
        for (boolean c : checked) {
            assertTrue(c);
        }
    }

    /** docking_undock_tabs_and_nodes: undocking windows and nodes. */
    @Test
    void testDockingUndockTabsAndNodes() {
        ctx.setGui(
                () -> {
                    ctx.showApp();
                    for (int i = 0; i < 4; i++) {
                        IkGui.setNextWindowSize(400, 200, Condition.ALWAYS);
                        IkGui.begin("Window " + (i + 1), null, WindowFlags.NO_SAVED_SETTINGS);
                        IkGui.textUnformatted("lorem ipsum");
                        IkGui.end();
                    }
                });
        final var io = IkGuiInternal.context.io;
        final Vector2f viewportPos = IkGui.getMainViewport().position;
        final Vector2f viewportSize = IkGui.getMainViewport().size;
        final Window window1 = ctx.getWindowByRef("Window 1");
        final Window window2 = ctx.getWindowByRef("Window 2");
        final Window window3 = ctx.getWindowByRef("Window 3");
        final Window window4 = ctx.getWindowByRef("Window 4");
        final Window demoWindow = ctx.getWindowByRef(IkGuiTestContext.DEMO);
        final float centerX = viewportPos.x + viewportSize.x * 0.5f;
        final float centerY = viewportPos.y + viewportSize.y * 0.5f;

        ctx.dockClear("Window 1", "Window 2", IkGuiTestContext.DEMO);
        ctx.windowMove(IkGuiTestContext.DEMO, centerX, centerY, 0.5f, 0.5f);
        ctx.windowMove("Window 1", centerX, centerY, 0.5f, 0.5f);
        ctx.windowMove("Window 2", centerX, centerY, 0.5f, 0.5f);

        // Undocking from a tab
        ctx.dockInto("Window 1", "Window 2");
        assertNotNull(window1.dockNode);
        assertSame(window1.dockNode, window2.dockNode);
        ctx.undockWindow("Window 1");
        assertTrue(IkGuiTestContext.windowIsUndockedOrStandalone(window1));
        assertTrue(IkGuiTestContext.windowIsUndockedOrStandalone(window2));

        // Undocking from the collapse button
        for (Direction direction :
                new Direction[] {Direction.LEFT, Direction.RIGHT, Direction.UP, Direction.DOWN}) {
            // Undocking with one and two windows
            for (int n = 0; n < 2; n++) {
                final String d = direction + " with " + (n + 1) + " windows";
                ctx.dockClear("Window 1", "Window 2", IkGuiTestContext.DEMO);
                ctx.yieldFrame();
                ctx.dockInto("Window 1", IkGuiTestContext.DEMO, direction);
                if (n != 0) {
                    ctx.dockInto("Window 2", "Window 1");
                }
                assertNotNull(demoWindow.dockNode, d);
                assertNotNull(demoWindow.dockNode.parentNode, d);
                assertNotNull(window1.dockNode, d);
                if (n != 0) {
                    assertSame(window1.dockNode, window2.dockNode, d);
                    assertSame(window1.dockNode.parentNode, window2.dockNode.parentNode, d);
                }
                assertSame(window1.dockNode.parentNode, demoWindow.dockNode.parentNode, d);
                ctx.undockNode(window1.dockNode.id);
                assertNotNull(window1.dockNode, d);
                if (n != 0) {
                    assertSame(window1.dockNode, window2.dockNode, d);
                    assertEquals(null, window2.dockNode.parentNode, d);
                }
                assertEquals(null, window1.dockNode.parentNode, d);
            }
        }

        final float h = window1.titleBarHeight;
        for (int n = 0; n < 3; n++) {
            final String d = "case " + n;
            ctx.dockClear("Window 1", "Window 2", "Window 3", "Window 4");
            ctx.dockInto("Window 2", "Window 1");
            ctx.dockInto("Window 3", "Window 2", Direction.RIGHT);
            ctx.dockInto("Window 4", "Window 3");

            final DockNode node1 = window1.dockNode;
            final DockNode node2 = window2.dockNode;
            final DockNode node3 = window3.dockNode;
            final DockNode node4 = window4.dockNode;
            assertNotNull(node1, d);
            assertNotNull(node2, d);
            assertNotNull(node3, d);
            assertNotNull(node4, d);

            switch (n) {
                case 0 -> {
                    // Undocking one tab
                    ctx.undockWindow("Window 4");
                    // The dragged window got undocked
                    assertTrue(IkGuiTestContext.windowIsUndockedOrStandalone(window4), d);
                    // The dock nodes of the other windows stay the same
                    assertSame(node1, window1.dockNode, d);
                    assertSame(node2, window2.dockNode, d);
                    assertSame(node3, window3.dockNode, d);
                    assertSame(window1.dockNode.hostWindow, window2.dockNode.hostWindow, d);
                    assertSame(window1.dockNode.hostWindow, window3.dockNode.hostWindow, d);
                }
                case 1 -> {
                    // Undocking an entire node
                    ctx.itemDragWithDelta(
                            IkGuiInternal.dockNodeGetWindowMenuButtonID(window4.dockNode),
                            h * -2,
                            h * -2);
                    // The dock nodes may have changed, but no window was undocked
                    assertNotNull(window1.dockNode, d);
                    assertNotNull(window2.dockNode, d);
                    assertNotNull(window3.dockNode, d);
                    assertNotNull(window4.dockNode, d);
                    // Windows 1 and 2 are in one dock tree
                    assertSame(window1.dockNode.hostWindow, window2.dockNode.hostWindow, d);
                    // Windows 3 and 4 are in one dock tree
                    assertSame(window3.dockNode.hostWindow, window4.dockNode.hostWindow, d);
                    // Both groups belong to separate trees
                    assertTrue(window1.dockNode.hostWindow != window3.dockNode.hostWindow, d);
                }
                case 2 -> {
                    // The dock state of all the windows didn't change
                    assertSame(node1, window1.dockNode, d);
                    assertSame(node2, window2.dockNode, d);
                    assertSame(node3, window3.dockNode, d);
                    assertSame(node4, window4.dockNode, d);
                    assertSame(window1.dockNode.hostWindow, window2.dockNode.hostWindow, d);
                    assertSame(window3.dockNode.hostWindow, window4.dockNode.hostWindow, d);
                    assertSame(window1.dockNode.hostWindow, window3.dockNode.hostWindow, d);

                    // Moving both nodes by dragging empty space. Resize the host so there is empty
                    // space in the dock node title bar.
                    final float minWidth =
                            (IkGui.calcTextSize("Window 11").x + IkGui.getFrameHeight() * 3) * 4.0f;
                    final Window host = window1.dockNode.hostWindow;
                    ctx.windowResize(host, minWidth, host.size.y);

                    final RectFloat bar = window4.dockNode.tabBar.barRect;
                    final float emptyX = bar.getRight() - 1;
                    final float emptyY = bar.getBottom() - h * 0.5f;
                    final float dragOffset = io.mouseDragThreshold;
                    final Vector2f pos = new Vector2f(window4.dockNode.hostWindow.position);

                    // Aim at the empty space
                    ctx.mouseMoveToPos(emptyX, emptyY);
                    // Holding shift disables docking
                    if (!io.configDockingWithShift) {
                        ctx.keyDown(KeyChord.ofMods(KeyModFlags.SHIFT));
                    }
                    ctx.mouseDragWithDelta(dragOffset, dragOffset);
                    if (!io.configDockingWithShift) {
                        ctx.keyUp(KeyChord.ofMods(KeyModFlags.SHIFT));
                    }
                    // The entire dock tree moved
                    assertVec(
                            pos.x + dragOffset,
                            pos.y + dragOffset,
                            window4.dockNode.hostWindow.position,
                            d);
                }
                default -> throw new IllegalStateException();
            }
        }
    }

    /** docking_hide_tabbar: hiding and showing the tab bar of the nodes in a two way split. */
    @Test
    void testDockingHideTabbar() {
        final DockingTestsGenericVars vars = new DockingTestsGenericVars();
        vars.setShowWindows(2, true);
        ctx.setGui(() -> dockingTestsGenericGuiFunc(vars));
        final Window[] windows = {ctx.getWindowByRef("AAA"), ctx.getWindowByRef("BBB")};
        for (Window window : windows) {
            ctx.dockClear("BBB", "AAA");
            ctx.dockInto("BBB", "AAA", Direction.RIGHT);

            // A two way split, with the window docked and its tab bar visible
            assertTrue(window.dockIsActive, window.name);
            assertFalse(window.dockNode.isHiddenTabBar(), window.name);

            // Hide the tab bar, then show it again
            ctx.dockNodeHideTabBar(window.dockNode, true);
            ctx.dockNodeHideTabBar(window.dockNode, false);
        }
    }

    /** The GUI for docking_over_child and docking_over_dockspace. */
    private static void dockingOverGui(int variant, int[] dockID) {
        IkGui.setNextWindowSize(300, 200, Condition.ALWAYS);
        IkGui.pushStyleVarFloat2(StyleVariable.WINDOW_PADDING, 0, 0);
        IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
        switch (variant) {
            case 0 -> {
                IkGui.beginChild(
                        "Child", 300, 200 - IkGuiInternal.context.windowCurrent.titleBarHeight);
                IkGui.endChild();
            }
            case 1 -> dockID[0] = IkGui.dockSpace(IkGui.getID("TestDockspace"));
            default -> throw new IllegalStateException();
        }
        IkGui.end();
        IkGui.popStyleVar();

        final Vector2f viewportPos = IkGui.getMainViewport().position;
        IkGui.setNextWindowPos(viewportPos.x + 100, viewportPos.y + 100, Condition.APPEARING);
        IkGui.setNextWindowSize(300, 200, Condition.APPEARING);
        IkGui.begin("Dock Window", null, WindowFlags.NO_SAVED_SETTINGS);
        IkGui.end();
    }

    /** The test for docking_over_child and docking_over_dockspace. */
    private void dockingOverTest(int variant, int[] dockID) {
        final Window dockWindow = ctx.getWindowByRef("Dock Window");
        final Window testWindow = ctx.getWindowByRef("Test Window");

        ctx.dockClear("Dock Window", "Test Window");
        switch (variant) {
            case 0 -> {
                ctx.dockInto("Dock Window", "Test Window");
                assertNotNull(dockWindow.dockNode);
                assertNotNull(testWindow.dockNode);
                assertSame(dockWindow.dockNode.hostWindow, testWindow.dockNode.hostWindow);
            }
            case 1 -> {
                final DockNode dockNode = IkGuiInternal.dockBuilderGetNode(dockID[0]);
                ctx.dockInto("Dock Window", dockNode.id);
                assertNotNull(dockWindow.dockNode);
                assertTrue(IkGuiTestContext.windowIsUndockedOrStandalone(testWindow));
                assertFalse(IkGuiTestContext.windowIsUndockedOrStandalone(dockWindow));
                assertEquals(dockID[0], dockWindow.dockNode.id);
            }
            default -> throw new IllegalStateException();
        }
    }

    /** docking_over_child: docking into a window that is entirely covered by a child window. */
    @Test
    void testDockingOverChild() {
        final int[] dockID = {0};
        ctx.setGui(() -> dockingOverGui(0, dockID));
        dockingOverTest(0, dockID);
    }

    /** docking_over_dockspace: docking into a floating dockspace. */
    @Test
    void testDockingOverDockspace() {
        final int[] dockID = {0};
        ctx.setGui(() -> dockingOverGui(1, dockID));
        dockingOverTest(1, dockID);
    }

    /** docking_tab_order: the order of docked window tabs. */
    @Test
    void testDockingTabOrder() {
        final DockingTestsGenericVars vars = new DockingTestsGenericVars();
        vars.setShowWindows(3, true);
        ctx.setGui(() -> dockingTestsGenericGuiFunc(vars));
        final var style = IkGuiInternal.context.style.variable;

        for (int step = 0; step < 3; step++) {
            final String d = "step " + step;
            vars.step = step;
            vars.showDockspace = vars.step == 1;

            ctx.dockClear("Test Window", "AAA", "BBB", "CCC");
            // Upstream notes that aiming at the left-most tab fails with a very small node,
            // because the collapse button hit test is too large
            ctx.windowResize("Test Window", 300, 200);
            ctx.windowResize("AAA", 300, 200);

            // All into a dockspace
            if (step == 1) {
                ctx.dockInto("AAA", vars.dockSpaceID);
            }
            // All into a split node of the test window
            if (step == 2) {
                ctx.dockInto("AAA", "Test Window", Direction.RIGHT);
            }
            ctx.dockInto("BBB", "AAA");
            ctx.dockInto("CCC", "AAA");

            final Window windowA = ctx.getWindowByRef("AAA");
            final Window windowC = ctx.getWindowByRef("CCC");
            assertNotNull(windowA.dockNode, d);

            // The initial tab order
            TabBar tabBar = windowA.dockNode.tabBar;
            assertNotNull(tabBar, d);
            final String[] initialOrder = {"AAA", "BBB", "CCC"};
            assertTrue(IkGuiTestContext.tabBarCompareOrder(tabBar, initialOrder), d);

            // Dragging past the edge of a tab, but without entering another tab, doesn't reorder
            Vector2f tabPos =
                    IkGuiImplTabs.tabBarGetTabPos(tabBar, tabBar.tabs.get(0), new Vector2f());
            ctx.itemDragToPos("AAA/#TAB", tabPos.x + style.itemInnerSpacing.x * 0.5f, tabPos.y);
            tabBar = windowA.dockNode.tabBar;
            assertTrue(IkGuiTestContext.tabBarCompareOrder(tabBar, initialOrder), d + " after AAA");
            tabPos = IkGuiImplTabs.tabBarGetTabPos(tabBar, tabBar.tabs.get(0), new Vector2f());
            ctx.itemDragToPos("BBB/#TAB", tabPos.x + tabBar.tabs.get(0).width + 1.0f, tabPos.y);
            tabBar = windowA.dockNode.tabBar;
            assertTrue(IkGuiTestContext.tabBarCompareOrder(tabBar, initialOrder), d + " after BBB");
            assertEquals(windowC.idTab, tabBar.tabs.get(2).id, d);

            // Mix the tabs, so the order becomes CCC, BBB, AAA. This also checks dragging way
            // past the tab bar.
            ctx.itemDragWithDelta(
                    "AAA/#TAB", tabBar.tabs.get(2).offset + tabBar.tabs.get(2).width, 0.0f);
            ctx.itemDragWithDelta(
                    "CCC/#TAB", -tabBar.tabs.get(1).offset - tabBar.tabs.get(1).width, 0.0f);
            // Avoid crashes if the tab got undocked
            assertNotNull(windowA.dockNode, d);
            tabBar = windowA.dockNode.tabBar;
            assertTrue(
                    IkGuiTestContext.tabBarCompareOrder(tabBar, "CCC", "BBB", "AAA"),
                    d + " rearranged");

            // Hide CCC and BBB, and show them together on the same frame
            vars.showWindow[1] = false;
            vars.showWindow[2] = false;
            ctx.yieldFrames(2);
            vars.showWindow[1] = true;
            vars.showWindow[2] = true;
            ctx.yieldFrames(2);

            // After appearing again, the windows that were hidden keep their order relative to each
            // other, and go to the end of the tab bar together
            tabBar = windowA.dockNode.tabBar;
            assertTrue(
                    IkGuiTestContext.tabBarCompareOrder(tabBar, "AAA", "CCC", "BBB"),
                    d + " reappeared");
        }
    }

    /** docking_tab_order_hidden_tabbar: tab order with a one window node that has no tab bar. */
    @Test
    void testDockingTabOrderHiddenTabbar() {
        final DockingTestsGenericVars vars = new DockingTestsGenericVars();
        vars.showDockspace = false;
        vars.setShowWindows(4, true);
        ctx.setGui(() -> dockingTestsGenericGuiFunc(vars));
        ctx.dockClear("AAA", "BBB", "CCC", "DDD");
        ctx.dockInto("BBB", "AAA");
        ctx.dockInto("DDD", "CCC");
        vars.showWindow[3] = false;
        ctx.yieldFrame();

        final Window windowA = ctx.getWindowByRef("AAA");
        final Window windowC = ctx.getWindowByRef("CCC");
        assertTrue(windowC.dockID != 0);
        assertNotNull(windowC.dockNode);
        assertTrue(windowC.dockID != windowA.dockID);
        ctx.dockInto(windowA.dockNode.id, windowC.id, Direction.NONE, false);
        assertSame(windowA.dockNode, windowC.dockNode);

        assertTrue(
                IkGuiTestContext.tabBarCompareOrder(windowC.dockNode.tabBar, "CCC", "AAA", "BBB"));
    }

    /** docking_tab_order_preserve: the tab order is kept when an entire dock node is moved. */
    @Test
    void testDockingTabOrderPreserve() {
        final DockingTestsGenericVars vars = new DockingTestsGenericVars();
        vars.showDockspace = false;
        vars.setShowWindows(5, true);
        ctx.setGui(() -> dockingTestsGenericGuiFunc(vars));
        final Window windowB = ctx.getWindowByRef("BBB");
        final Window windowC = ctx.getWindowByRef("CCC");
        ctx.dockClear("AAA", "BBB", "CCC", "DDD", "EEE");
        ctx.windowMove("EEE", windowB.position.x + windowB.size.x + 10.0f, windowB.position.y);
        ctx.dockInto("AAA", "BBB");
        ctx.dockInto("CCC", "EEE", Direction.RIGHT);
        ctx.dockInto("DDD", "CCC");

        final TabBar tabBar = windowC.dockNode.tabBar;
        ctx.itemDragWithDelta(
                "CCC/#TAB", tabBar.tabs.get(1).offset + tabBar.tabs.get(1).width, 0.0f);
        assertNotNull(windowC.dockNode);
        ctx.dockInto(windowC.dockNode.id, "AAA");
        assertTrue(
                IkGuiTestContext.tabBarCompareOrder(
                        windowB.dockNode.tabBar, "BBB", "AAA", "DDD", "CCC"));
    }

    /** docking_tab_focus_restore: restoring the focus order of tabs that appear together. */
    @Test
    void testDockingTabFocusRestore() {
        final DockingTestsGenericVars vars = new DockingTestsGenericVars();
        vars.setShowWindows(5, true);
        ctx.setGui(() -> dockingTestsGenericGuiFunc(vars));
        final var g = IkGuiInternal.context;
        // Upstream unchecks the demo's dockspace example here, which the generic GUI doesn't show

        for (int step = 0; step < 3; step++) {
            final String d = "step " + step;
            vars.step = step;
            vars.showDockspace = vars.step == 1;

            ctx.dockClear("Test Window", "AAA", "BBB", "CCC", "DDD", "EEE");
            ctx.windowResize("Test Window", 300, 200);
            ctx.windowResize("AAA", 300, 200);

            // All into a dockspace
            if (step == 1) {
                ctx.dockInto("AAA", vars.dockSpaceID);
            }
            // All into a split node of the test window
            if (step == 2) {
                ctx.dockInto("AAA", "Test Window", Direction.RIGHT);
            }
            ctx.dockInto("BBB", "AAA");
            ctx.dockInto("CCC", "AAA");

            // DDD and EEE are docked separately, because the focus restore issues are different
            // depending on whether the node has the final focus or not
            ctx.dockInto("DDD", "AAA", Direction.DOWN);
            ctx.dockInto("EEE", "DDD");

            final Window windowA = ctx.getWindowByRef("AAA");
            final Window windowB = ctx.getWindowByRef("BBB");
            final Window windowC = ctx.getWindowByRef("CCC");
            final Window windowD = ctx.getWindowByRef("DDD");
            final Window windowE = ctx.getWindowByRef("EEE");
            final DockNode node1 = windowA.dockNode;
            final DockNode node2 = windowD.dockNode;
            assertNotNull(node1, d);
            assertNotNull(node2, d);
            assertTrue(node1 != node2, d);

            ctx.windowFocus("//DDD");
            assertEquals(windowD.idTab, node2.selectedTabID, d);

            ctx.windowFocus("//BBB");
            TabBar tabBar1 = node1.tabBar;
            assertEquals(windowB.idTab, node1.selectedTabID, d);
            assertEquals(windowB.idTab, tabBar1.selectedTabID, d);
            assertSame(windowB, g.navFocusedWindow, d);

            // Node 1
            assertTrue(windowA.hidden, d);
            assertFalse(windowB.hidden, d);
            assertTrue(windowC.hidden, d);
            // Node 2
            assertFalse(windowD.hidden, d);
            assertTrue(windowE.hidden, d);
            final float w = tabBar1.widthAllTabs;

            // Hide all the tabs, and show them together on the same frame
            vars.setShowWindows(5, false);
            ctx.yieldFrame();
            assertFalse(windowA.active, d);
            assertFalse(windowB.active, d);
            assertFalse(windowC.active, d);
            assertFalse(windowD.active, d);
            assertFalse(windowE.active, d);
            ctx.yieldFrame();

            // Show DDD and EEE first
            vars.showWindow[3] = true;
            vars.showWindow[4] = true;
            ctx.yieldFrame();
            assertTrue(windowD.active, d);
            assertTrue(windowE.active, d);
            assertTrue(windowD.hidden, d);
            assertTrue(windowE.hidden, d);
            // Then AAA, BBB and CCC
            vars.setShowWindows(5, true);
            ctx.yieldFrame();
            assertTrue(windowA.active, d);
            assertTrue(windowB.active, d);
            assertTrue(windowC.active, d);
            assertTrue(windowD.active, d);
            assertTrue(windowE.active, d);
            assertTrue(windowA.hidden, d);
            assertTrue(windowB.hidden, d);
            assertTrue(windowC.hidden, d);

            // BBB should have focus
            ctx.yieldFrame();
            tabBar1 = windowA.dockNode.tabBar;
            assertEquals(w, tabBar1.widthAllTabs, d);
            final TabBar tabBar2 = windowD.dockNode.tabBar;

            // Focus restore (#2304) for a node with no final focus
            assertFalse(windowD.hidden, d);
            assertTrue(windowE.hidden, d);
            assertEquals(windowD.idTab, node2.selectedTabID, d);
            assertEquals(windowD.idTab, tabBar2.selectedTabID, d);

            // Focus restore (#2304) for the node that has the final focus
            assertEquals(windowB.idTab, node1.selectedTabID, d);
            assertEquals(windowB.idTab, tabBar1.selectedTabID, d);
            assertTrue(windowA.hidden, d);
            assertFalse(windowB.hidden, d);
            assertTrue(windowC.hidden, d);
            // Upstream has more checks here that are marked as broken
        }
    }

    /** docking_tab_amend: appending to a dock node tab bar. */
    @Test
    void testDockingTabAmend() {
        final int[] buttonClicks = {0};
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(200, 100, Condition.APPEARING);
                    IkGui.begin("AAA", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.textUnformatted("Not empty.");
                    final Window window = IkGuiInternal.context.windowCurrent;
                    if (window.dockNode != null
                            && IkGuiInternal.dockNodeBeginAmendTabBar(window.dockNode)) {
                        if (IkGui.tabItemButton("+", TabItemFlags.TRAILING)) {
                            buttonClicks[0]++;
                        }
                        IkGuiInternal.dockNodeEndAmendTabBar();
                    }
                    IkGui.end();

                    IkGui.setNextWindowSize(200, 100, Condition.APPEARING);
                    IkGui.begin("BBB", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.textUnformatted("Not empty.");
                    IkGui.end();
                });
        final Window window = ctx.getWindowByRef("AAA");
        ctx.dockClear("AAA", "BBB");
        ctx.dockInto("BBB", "AAA");
        assertEquals(0, buttonClicks[0]);
        assertTrue(window.dockID != 0);
        ctx.itemClick(ctx.getID("+", window.dockID));
        assertEquals(1, buttonClicks[0]);
    }

    /** docking_tab_clipped_is_hovered: isItemHovered() on tabs, and on clipped contents. */
    @Test
    void testDockingTabClippedIsHovered() {
        final boolean[] hovered = new boolean[4];
        ctx.setGui(
                () -> {
                    IkGui.begin("AAA", null, WindowFlags.NO_SAVED_SETTINGS);
                    hovered[0] = IkGui.isItemHovered();
                    IkGui.text("IsItemHovered: " + hovered[0]);
                    IkGui.button("Button");
                    hovered[1] = IkGui.isItemHovered();
                    IkGui.text("IsItemHovered: " + hovered[1]);
                    IkGui.end();

                    IkGui.begin("BBB", null, WindowFlags.NO_SAVED_SETTINGS);
                    hovered[2] = IkGui.isItemHovered();
                    IkGui.text("IsItemHovered: " + hovered[2]);
                    IkGui.button("Button");
                    hovered[3] = IkGui.isItemHovered();
                    IkGui.text("IsItemHovered: " + hovered[3]);
                    IkGui.end();
                });
        ctx.dockClear("AAA", "BBB");
        ctx.dockInto("BBB", "AAA");

        ctx.itemClick("AAA/#TAB");
        assertTrue(hovered[0]);
        // The buttons don't report as hovered
        assertFalse(hovered[1]);
        assertFalse(hovered[2]);
        assertFalse(hovered[3]);
        ctx.mouseDown(MouseButton.LEFT);
        ctx.yieldFrames(2);
        assertTrue(hovered[0]);
        assertFalse(hovered[1]);
        assertFalse(hovered[2]);
        assertFalse(hovered[3]);
        ctx.mouseUp(MouseButton.LEFT);

        ctx.itemClick("BBB/#TAB");
        assertFalse(hovered[0]);
        assertFalse(hovered[1]);
        assertTrue(hovered[2]);
        assertFalse(hovered[3]);
    }

    /**
     * docking_dockspace_item_query: the item functions work on dockSpace(), which is based on but
     * doesn't use beginChild()/endChild().
     */
    @Test
    void testDockingDockspaceItemQuery() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    final int id = IkGui.getID("foo");
                    IkGui.dockSpace(id, 200, 300);
                    final Vector2f size = IkGui.getItemRectSize();
                    final int id2 = IkGui.getItemID();
                    ctx.check(size.x == 200.0f && size.y == 300.0f, "item size");
                    ctx.checkEquals(id, id2, "item ID");
                    IkGui.end();
                });
        ctx.yieldFrames(2);
    }

    /** docking_dockspace_keep_alive: the KEEP_ALIVE_ONLY dockspace flag. */
    @Test
    void testDockingDockspaceKeepAlive() {
        final int[] flags = {DockNodeFlags.NONE};
        final boolean[] showDockspace = {true};
        final boolean[] showMainMenuBar = {true};
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(300, 200, Condition.ALWAYS);
                    if (showMainMenuBar[0]) {
                        IkGui.beginMainMenuBar();
                        IkGui.endMainMenuBar();
                    }

                    IkGui.setNextWindowSize(200, 100, Condition.APPEARING);
                    IkGui.begin("Window A", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (showDockspace[0]) {
                        IkGui.dockSpace(IkGui.getID("A2"), 0, 0, flags[0]);
                    }
                    IkGui.end();

                    IkGui.setNextWindowSize(150, 100, Condition.ALWAYS);
                    IkGui.begin("Window B", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.end();
                });
        final Window window1 = ctx.getWindowByRef("Window A");
        final Window window2 = ctx.getWindowByRef("Window B");
        final int dockID = ctx.getID("Window A/A2");

        // "Window A" has the dockspace "A2". Dock "Window B" into "A2".
        ctx.dockClear("Window B", "Window A");
        ctx.windowCollapse("Window A", false);
        ctx.windowCollapse("Window B", false);
        ctx.dockInto("Window B", dockID);
        // "Window A" isn't docked
        assertTrue(IkGuiTestContext.windowIsUndockedOrStandalone(window1));
        // "Window B" was docked into the dockspace
        assertEquals(dockID, window2.dockID);

        // "Window A" calls dockSpace() with KEEP_ALIVE_ONLY. "Window B" is still docked into "A2",
        // and both are hidden at this point.
        flags[0] = DockNodeFlags.KEEP_ALIVE_ONLY;
        ctx.yieldFrame();
        assertFalse(window1.dockIsActive);
        assertTrue(IkGuiTestContext.windowIsUndockedOrStandalone(window1));
        // "Window B" wasn't collapsed, the dockspace is kept alive, and the window stays docked
        // but invisible
        assertFalse(window2.collapsed);
        assertTrue(window2.dockIsActive);
        assertEquals(dockID, window2.dockID);
        assertNotNull(window2.dockNode);
        assertTrue(window2.hidden);

        // "Window A" is collapsed, with a regular dockSpace() call without KEEP_ALIVE_ONLY, which
        // becomes automatic
        flags[0] = DockNodeFlags.NONE;
        ctx.windowCollapse("Window A", true);
        assertTrue(window1.collapsed);
        assertFalse(window1.dockIsActive);
        assertFalse(window2.collapsed);
        assertTrue(window2.dockIsActive);
        assertEquals(dockID, window2.dockID);
        assertNotNull(window2.dockNode);
        assertTrue(window2.hidden);

        // A window submitted before "Window A" is hidden (#4757)
        showMainMenuBar[0] = false;
        ctx.yieldFrame();
        assertTrue(window1.collapsed);
        assertFalse(window1.dockIsActive);
        assertFalse(window2.collapsed);
        assertTrue(window2.dockIsActive);
        assertEquals(dockID, window2.dockID);
        assertNotNull(window2.dockNode);
        assertTrue(window2.hidden);

        // "Window A" stops submitting the "A2" dockspace, so "Window B" is undocked
        showDockspace[0] = false;
        ctx.yieldFrame();
        assertTrue(window1.collapsed);
        assertFalse(window1.dockIsActive);
        assertTrue(IkGuiTestContext.windowIsUndockedOrStandalone(window1));
        assertFalse(window2.collapsed);
        // The dockspace is no longer kept alive, so the window is undocked and shows up
        assertFalse(window2.dockIsActive);
        assertTrue(IkGuiTestContext.windowIsUndockedOrStandalone(window2));
        assertFalse(window2.hidden);
    }

    /** docking_dockspace_passthru_hover: hovering through a passthrough central node. */
    @Test
    void testDockingDockspacePassthruHover() {
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowPos(100, 100, Condition.APPEARING);
                    IkGui.setNextWindowSize(300, 200, Condition.APPEARING);
                    // Not technically needed, but it helps understand the purpose of this test
                    IkGui.setNextWindowBgAlpha(0.1f);
                    IkGui.begin(
                            "Window 1",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.NO_DOCKING);
                    IkGui.dockSpace(
                            IkGui.getID("DockSpace"), 0, 0, DockNodeFlags.PASSTHROUGH_CENTRAL_NODE);
                    IkGui.end();

                    IkGui.setNextWindowPos(100, 100, Condition.APPEARING);
                    IkGui.setNextWindowSize(300, 200, Condition.APPEARING);
                    IkGui.begin(
                            "Window 2",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.NO_DOCKING);
                    IkGui.end();
                });
        final var g = IkGuiInternal.context;
        final Window window1 = ctx.getWindowByRef("Window 1");
        final Window window2 = ctx.getWindowByRef("Window 2");
        ctx.windowFocus("Window 1");
        final RectFloat rect = window1.getRect();
        ctx.mouseMoveToPos(rect.getCenterX(), rect.getCenterY());
        assertSame(window1, g.navFocusedWindow);
        assertSame(window2, g.windowHovered);
        assertEquals(null, g.debugHoveredDockNode);
        ctx.mouseClick(MouseButton.LEFT);
        assertSame(window2, g.navFocusedWindow);
    }

    /** docking_dockspace_passthru_padding: the hit test hole of a passthrough dockspace (#3733). */
    @Test
    void testDockingDockspacePassthruPadding() {
        final int[] dockID = {0};
        ctx.setGui(
                () -> {
                    dockID[0] =
                            IkGui.dockSpaceOverViewport(
                                    0,
                                    IkGui.getMainViewport(),
                                    DockNodeFlags.PASSTHROUGH_CENTRAL_NODE);
                    for (String name : new String[] {"Left", "Right", "Up", "Down"}) {
                        IkGui.setNextWindowSize(300, 200, Condition.APPEARING);
                        IkGui.begin(name, null, WindowFlags.NO_SAVED_SETTINGS);
                        IkGui.end();
                    }
                });
        final Viewport viewport = IkGui.getMainViewport();
        final Window window =
                ctx.getWindowByRef(String.format("WindowOverViewport_%08X", viewport.id));
        ctx.dockClear("Left", "Up", "Right", "Down");

        // An empty dockspace has no padding
        assertNotNull(window);
        assertEquals(0, window.hitTestHolePosition.x);
        assertEquals(0, window.hitTestHolePosition.y);
        assertEquals(viewport.size.x, window.hitTestHoleSize.x);
        assertEquals(viewport.size.y, window.hitTestHoleSize.y);

        ctx.dockInto(ctx.getID("Left"), dockID[0], Direction.LEFT, true);
        ctx.dockInto(ctx.getID("Up"), dockID[0], Direction.UP, true);
        ctx.dockInto(ctx.getID("Right"), dockID[0], Direction.RIGHT, true);
        ctx.dockInto(ctx.getID("Down"), dockID[0], Direction.DOWN, true);
        ctx.yieldFrame();

        // Windows docked around the dockspace make the central hole smaller by their size and some
        // padding
        final Window left = ctx.getWindowByRef("Left");
        final Window up = ctx.getWindowByRef("Up");
        final Window right = ctx.getWindowByRef("Right");
        final Window down = ctx.getWindowByRef("Down");
        assertTrue(
                viewport.position.x + window.hitTestHolePosition.x > left.position.x + left.size.x);
        assertTrue(viewport.position.y + window.hitTestHolePosition.y > up.position.y + up.size.y);
        assertTrue(
                viewport.position.x + window.hitTestHolePosition.x + window.hitTestHoleSize.x
                        < right.position.x);
        assertTrue(
                viewport.position.y + window.hitTestHolePosition.y + window.hitTestHoleSize.y
                        < down.position.y);
    }

    /** docking_preserve_docking_info: the docking info of closed windows is kept (#3716). */
    @Test
    void testDockingPreserveDockingInfo() {
        final DockingTestsGenericVars vars = new DockingTestsGenericVars();
        vars.setShowWindows(3, true);
        ctx.setGui(() -> dockingTestsGenericGuiFunc(vars));
        final Window window1 = ctx.getWindowByRef("AAA");
        final Window window2 = ctx.getWindowByRef("BBB");
        ctx.dockClear("AAA", "BBB", "CCC");
        ctx.dockInto(ctx.getID("AAA"), vars.dockSpaceID, Direction.LEFT, false);
        ctx.dockInto("BBB", "AAA");
        vars.showWindow[0] = false;
        ctx.yieldFrames(3);
        ctx.dockInto("CCC", "BBB", Direction.DOWN);
        vars.showWindow[0] = true;
        ctx.yieldFrames(3);
        assertTrue(window1.dockID != 0);
        assertEquals(window1.dockID, window2.dockID);
    }

    /** docking_focus_from_menu: focusing docked windows from the window menu. */
    @Test
    void testDockingFocusFromMenu() {
        final DockingTestsGenericVars vars = new DockingTestsGenericVars();
        vars.setShowWindows(3, true);
        ctx.setGui(() -> dockingTestsGenericGuiFunc(vars));
        final var g = IkGuiInternal.context;
        final Window[] windows = {
            ctx.getWindowByRef("AAA"), ctx.getWindowByRef("BBB"), ctx.getWindowByRef("CCC")
        };
        ctx.dockClear("AAA", "BBB", "CCC");
        ctx.dockInto("BBB", "AAA");
        ctx.dockInto("CCC", "BBB");

        for (Window window : windows) {
            ctx.itemClick(
                    IkGuiInternal.dockNodeGetWindowMenuButtonID(
                            window.rootWindowDockTree.dockNodeAsHost));
            ctx.setRef("//$FOCUSED");
            ctx.itemClick(window.name);
            assertSame(window, g.navFocusedWindow, window.name);
        }
    }

    /** docking_focus_from_host: focusing docked windows in different ways. */
    @Test
    void testDockingFocusFromHost() {
        final DockingTestsGenericVars vars = new DockingTestsGenericVars();
        vars.setShowWindows(2, true);
        ctx.setGui(() -> dockingTestsGenericGuiFunc(vars));
        final var g = IkGuiInternal.context;
        final Window[] windows = {ctx.getWindowByRef("AAA"), ctx.getWindowByRef("BBB")};
        ctx.dockClear("AAA", "BBB");
        ctx.dockInto("BBB", "AAA", Direction.RIGHT);

        // Focusing a window by clicking on it
        for (Window window : windows) {
            final RectFloat rect = window.getRect();
            ctx.mouseMoveToPos(rect.getCenterX(), rect.getCenterY());
            ctx.mouseClick(MouseButton.LEFT);
            assertSame(window, g.navFocusedWindow, window.name + " body");
        }

        // Focusing a window by clicking its tab
        for (Window window : windows) {
            ctx.itemClick(window.idTab);
            assertSame(window, g.navFocusedWindow, window.name + " tab");
        }

        // Focusing a window by clicking empty space in its tab bar
        for (Window window : windows) {
            final RectFloat bar = window.dockNode.tabBar.barRect;
            ctx.mouseMoveToPos(bar.getCenterX() + bar.getWidth() * 0.4f, bar.getCenterY());
            ctx.mouseClick(MouseButton.LEFT);
            assertSame(window, g.navFocusedWindow, window.name + " tab bar");
        }

        // Interacting with the collapse button focuses the window
        for (Window window : windows) {
            ctx.itemClick(IkGuiInternal.dockNodeGetWindowMenuButtonID(window.dockNode));
            ctx.keyPress(Key.ESCAPE);
            assertSame(window, g.navFocusedWindow, window.name + " collapse button");
        }

        // Interacting with the splitter. Upstream would check that the focus is kept, but notes
        // that it fails because of a bug (#3820).
        final Window rootDock = g.navFocusedWindow.rootWindowDockTree;
        final int splitterID =
                ctx.getID(rootDock.name + "/$$" + rootDock.dockNodeAsHost.id + "/##Splitter");
        ctx.mouseMove(splitterID);
        ctx.mouseDragWithDelta(0.0f, 10.0f);
    }

    /** docking_focus_from_host_nav: focusing docked windows with keyboard navigation (#8997). */
    @Test
    void testDockingFocusFromHostNav() {
        final DockingTestsGenericVars vars = new DockingTestsGenericVars();
        vars.setShowWindows(3, true);
        ctx.setGui(() -> dockingTestsGenericGuiFunc(vars));
        final var g = IkGuiInternal.context;
        final Window[] windows = {
            ctx.getWindowByRef("AAA"), ctx.getWindowByRef("BBB"), ctx.getWindowByRef("CCC")
        };
        ctx.dockClear("AAA", "BBB", "CCC");
        ctx.dockInto("BBB", "AAA", Direction.NONE);
        ctx.dockInto("CCC", "AAA", Direction.NONE);
        for (Window window : windows) {
            window.navLastIDs[0] = 0;
            window.navLastIDs[1] = 0;
        }
        assertSame(windows[2], g.navFocusedWindow);
        assertEquals(ctx.getID("//CCC/Button 1"), g.navID);

        ctx.keyPress(KeyChord.ofMods(KeyModFlags.ALT));
        ctx.keyPress(Key.ARROW_LEFT);
        ctx.keyPress(Key.ARROW_LEFT);
        ctx.keyPress(Key.SPACE);
        assertSame(windows[0], g.navFocusedWindow);
        // The newly appearing window got its navigation initialized
        assertEquals(ctx.getID("//AAA/Button 1"), g.navID);
    }

    /** docking_focus_nodes_1: the dock node focused flag, with a child window in the host. */
    @Test
    void testDockingFocusNodes1() {
        final int[] dockID = {0};
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    dockID[0] = IkGui.getID("Dockspace");
                    IkGui.dockSpace(dockID[0], 0.0f, 0.0f, DockNodeFlags.PASSTHROUGH_CENTRAL_NODE);
                    for (String name : new String[] {"A", "B", "C"}) {
                        IkGui.setNextWindowSize(300, 200, Condition.APPEARING);
                        IkGui.begin(name, null, WindowFlags.NO_SAVED_SETTINGS);
                        IkGui.end();
                    }
                    final DockNode node = IkGuiInternal.dockBuilderGetCentralNode(dockID[0]);
                    if (node != null) {
                        IkGui.setCursorScreenPos(node.position.x + 10, node.position.y + 10);
                        IkGui.beginChild(
                                "InjectedChild",
                                node.size.x - 200,
                                node.size.y - 20,
                                ChildFlags.BORDERS);
                        IkGui.endChild();
                    }
                    IkGui.end();
                });
        ctx.windowResize("Test Window", 600.0f, 600.0f);
        ctx.dockClear("A", "B", "C");
        ctx.dockInto(ctx.getID("A"), dockID[0], Direction.LEFT, true);
        ctx.dockInto(ctx.getID("B"), dockID[0], Direction.DOWN, true);
        ctx.dockInto("C", "B");
        final Window window = ctx.getWindowByRef("A");
        final Window windowChild = ctx.getChildWindow("Test Window", "InjectedChild");
        final DockNode nodeRoot = IkGuiInternal.dockNodeGetRootNode(window.dockNode);
        final DockNode nodeSplit = nodeRoot.childNodes[0];
        final DockNode nodeA = nodeSplit.childNodes[0];
        final DockNode nodeCentral = nodeSplit.childNodes[1];
        final DockNode nodeBC = nodeRoot.childNodes[1];

        // Only the clicked node is focused
        for (String name : new String[] {"A", "B", "C"}) {
            final Vector2f point = IkGuiTestContext.windowTitleBarPoint(ctx.getWindowByRef(name));
            ctx.mouseMoveToPos(point.x, point.y);
            ctx.mouseClick(MouseButton.LEFT);
            assertFalse(nodeCentral.isFocused, name);
            assertFalse(nodeRoot.isFocused, name);
            assertFalse(nodeSplit.isFocused, name);
            assertEquals("A".equals(name), nodeA.isFocused, name);
            assertEquals(!"A".equals(name), nodeBC.isFocused, name);
        }

        // Clicking another node and then the central node doesn't focus any other nodes
        final RectFloat childRect = windowChild.getRect();
        ctx.mouseMoveToPos(childRect.getRight() + 10.0f, childRect.getTop() + 10.0f);
        ctx.mouseClick(MouseButton.LEFT);
        assertFalse(nodeRoot.isFocused);
        assertFalse(nodeSplit.isFocused);
        assertFalse(nodeA.isFocused);
        assertFalse(nodeBC.isFocused);
        // Upstream would check that the central node gets focus even with
        // PASSTHROUGH_CENTRAL_NODE, but marks it as broken

        final Vector2f point = IkGuiTestContext.windowTitleBarPoint(ctx.getWindowByRef("C"));
        ctx.mouseMoveToPos(point.x, point.y);
        ctx.mouseClick(MouseButton.LEFT);
        ctx.mouseMoveToPos(childRect.getCenterX(), childRect.getCenterY());
        ctx.mouseClick(MouseButton.LEFT);
        assertFalse(nodeRoot.isFocused);
        assertFalse(nodeSplit.isFocused);
        assertFalse(nodeA.isFocused);
        assertFalse(nodeBC.isFocused);
        assertFalse(nodeCentral.isFocused);
    }

    /** docking_focus_nodes_nested: the dock node focused flag with nested dockspaces. */
    @Test
    void testDockingFocusNodesNested() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.dockSpace(IkGui.getID("Dockspace1"));

                    IkGui.begin("A1", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.text("SpacerSpacerSpacer-SpacerSpacerSpacer");
                    IkGui.text("SpacerSpacerSpacer-SpacerSpacerSpacer");
                    IkGui.dockSpace(IkGui.getID("Dockspace2"));
                    IkGui.end();
                    IkGui.begin("A2", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.text("SpacerSpacerSpacer");
                    IkGui.text("SpacerSpacerSpacer");
                    IkGui.end();

                    IkGui.begin("B1", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.text("SpacerSpacerSpacer");
                    IkGui.text("SpacerSpacerSpacer");
                    IkGui.dockSpace(IkGui.getID("Dockspace3"));
                    IkGui.end();
                    IkGui.begin("B2", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.text("SpacerSpacerSpacer");
                    IkGui.text("SpacerSpacerSpacer");
                    IkGui.end();

                    IkGui.begin("C1", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.text("SpacerSpacerSpacer");
                    IkGui.text("SpacerSpacerSpacer");
                    IkGui.end();
                    IkGui.begin("C2", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.text("SpacerSpacerSpacer");
                    IkGui.text("SpacerSpacerSpacer");
                    IkGui.end();

                    IkGui.end();
                });
        ctx.windowResize("Test Window", 600.0f, 600.0f);
        ctx.dockClear("A1", "A2", "B1", "B2", "C1", "C2");
        ctx.dockInto("A1", "//Test Window/Dockspace1", Direction.LEFT, true);
        ctx.dockInto("A2", "//Test Window/Dockspace1", Direction.DOWN, true);
        ctx.dockInto("B1", "//A1/Dockspace2", Direction.LEFT, true);
        ctx.dockInto("B2", "//A1/Dockspace2", Direction.DOWN, true);
        ctx.dockInto("C1", "//B1/Dockspace3", Direction.LEFT, true);
        // A very small node made this land over the splitter, which was broken upstream before
        // 2022/10/24
        ctx.dockInto("C2", "//B1/Dockspace3", Direction.DOWN, true);

        final String[] names = {"A1", "B1", "C1", "A2", "B2", "C2"};
        final boolean[][] expected = {
            // After clicking A1
            {true, false, false, false, false, false},
            // After clicking C1
            {true, true, true, false, false, false},
            // After clicking A2
            {false, false, false, true, false, false},
            // After clicking B2
            {true, false, false, false, true, false},
        };
        final String[] clicks = {"A1", "C1", "A2", "B2"};
        for (int c = 0; c < clicks.length; c++) {
            // Clicking a docked window's tab
            ctx.itemClick(ctx.getWindowByRef(clicks[c]).idTab);
            for (int n = 0; n < names.length; n++) {
                assertEquals(
                        expected[c][n],
                        ctx.getWindowByRef(names[n]).dockNode.isFocused,
                        "after clicking " + clicks[c] + ": " + names[n]);
            }
        }
    }

    /** docking_tab_state: the tab state is restored in independent and split windows. */
    @Test
    void testDockingTabState() {
        final DockingTestsGenericVars vars = new DockingTestsGenericVars();
        vars.setShowWindows(6, true);
        ctx.setGui(() -> dockingTestsGenericGuiFunc(vars));
        final Window windowA = ctx.getWindowByRef("AAA");
        final Window windowD = ctx.getWindowByRef("DDD");

        for (int variant = 0; variant < 2; variant++) {
            final String d = "variant " + variant;
            ctx.dockClear("AAA", "BBB", "CCC", "DDD", "EEE", "FFF");

            if (variant == 1) {
                ctx.dockInto("DDD", "AAA", Direction.RIGHT);
            }
            ctx.dockInto("BBB", "AAA");
            ctx.dockInto("CCC", "BBB");
            ctx.dockInto("EEE", "DDD");
            ctx.dockInto("FFF", "EEE");

            TabBar tabBarA = windowA.dockNode.tabBar;
            TabBar tabBarD = windowD.dockNode.tabBar;

            // Rearrange the tabs and set the active tab
            ctx.itemDragAndDrop("CCC/#TAB", "AAA/#TAB");
            ctx.itemDragAndDrop("DDD/#TAB", "FFF/#TAB");
            ctx.itemClick("AAA/#TAB");
            ctx.itemClick("FFF/#TAB");

            // The initial order
            assertTrue(IkGuiTestContext.tabBarCompareOrder(tabBarA, "CCC", "AAA", "BBB"), d);
            assertTrue(IkGuiTestContext.tabBarCompareOrder(tabBarD, "EEE", "FFF", "DDD"), d);

            assertTrue(tabBarA.tabs.size() >= 2, d);
            assertEquals(tabBarA.tabs.get(1).id, tabBarA.visibleTabID, d);
            assertEquals(tabBarD.tabs.get(1).id, tabBarD.visibleTabID, d);

            // Hide and show all the windows
            vars.setShowWindows(7, false);
            ctx.yieldFrames(2);
            vars.setShowWindows(7, true);
            ctx.yieldFrames(2);

            // The tab order is kept
            tabBarA = windowA.dockNode.tabBar;
            tabBarD = windowD.dockNode.tabBar;
            assertTrue(
                    IkGuiTestContext.tabBarCompareOrder(tabBarA, "CCC", "AAA", "BBB"),
                    d + " after hiding");
            assertTrue(
                    IkGuiTestContext.tabBarCompareOrder(tabBarD, "EEE", "FFF", "DDD"),
                    d + " after hiding");
            // Upstream would also check that the active window is kept, but notes it fails
            // because of a bug
        }
    }

    /** docking_undock_simple: undocking the second window of a two way split keeps the node. */
    @Test
    void testDockingUndockSimple() {
        final DockingTestsGenericVars vars = new DockingTestsGenericVars();
        vars.setShowWindows(2, true);
        ctx.setGui(() -> dockingTestsGenericGuiFunc(vars));
        final Window window0 = ctx.getWindowByRef("AAA");
        final Window window1 = ctx.getWindowByRef("BBB");
        ctx.dockClear("AAA", "BBB");
        ctx.dockInto("BBB", "AAA", Direction.RIGHT);
        assertNotNull(window0.dockNode);
        assertNotNull(window1.dockNode);
        final DockNode originalNode = window0.dockNode.parentNode;
        ctx.undockWindow("BBB");
        // Undocking BBB keeps a parent dock node in AAA
        assertSame(originalNode, window0.dockNode);
        assertTrue(IkGuiTestContext.windowIsUndockedOrStandalone(window1));
    }

    /** docking_undock_large: undocking a window from a dockspace covering the whole viewport. */
    @Test
    void testDockingUndockLarge() {
        ctx.setGui(
                () -> {
                    final int dockspaceID =
                            IkGui.dockSpaceOverViewport(
                                    0,
                                    IkGui.getMainViewport(),
                                    DockNodeFlags.PASSTHROUGH_CENTRAL_NODE);
                    IkGui.setNextWindowDockID(dockspaceID, Condition.APPEARING);
                    IkGui.begin("Window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.text("This is window 1");
                    IkGui.end();
                });
        final Viewport mainViewport = IkGui.getMainViewport();
        final Window window = ctx.getWindowByRef("Window 1");
        assertNotNull(window.dockNode);

        final Vector2f initialSize = new Vector2f(mainViewport.size);
        assertEquals(initialSize.x, window.size.x);
        assertEquals(initialSize.y, window.size.y);

        // Undock
        final float fontSize = IkGuiInternal.getFontSize();
        ctx.itemDragWithDelta("Window 1/#TAB", fontSize * 3, fontSize * 3);
        assertTrue(IkGuiTestContext.windowIsUndockedOrStandalone(window));

        // Without multiple viewports, the window is always smaller than the main viewport
        final Vector2f expectSize = new Vector2f(mainViewport.size).mul(0.90f);
        assertTrue(window.size.x <= expectSize.x);
        assertTrue(window.size.y <= expectSize.y);
    }

    /** docking_undock_from_dockspace_size: undocking a whole node keeps its size. */
    @Test
    void testDockingUndockFromDockspaceSize() {
        final DockingTestsGenericVars vars = new DockingTestsGenericVars();
        vars.setShowWindows(2, true);
        final int[] frame = {-2};
        ctx.setGui(
                () -> {
                    // Upstream does this on the first test frame, which is after the warm-up
                    if (frame[0] == 0) {
                        final int dockspaceID = ctx.getID("//Test Window/dockspace");
                        IkGuiInternal.dockBuilderRemoveNode(dockspaceID);
                        IkGuiInternal.dockBuilderAddNode(
                                dockspaceID, DockNodeFlags.INTERNAL_DOCK_SPACE);
                        IkGuiInternal.dockBuilderDockWindow("AAA", dockspaceID);
                        IkGuiInternal.dockBuilderDockWindow("BBB", dockspaceID);
                    }
                    dockingTestsGenericGuiFunc(vars);
                    frame[0]++;
                });
        // The test function starts after the warm-up
        ctx.yieldFrames(2);
        final int dockspaceID = ctx.getID("//Test Window/dockspace");

        final Window windowA = IkGuiInternal.findWindowByName("AAA");
        final Window windowB = IkGuiInternal.findWindowByName("BBB");
        final Vector2f windowASizeOld = new Vector2f(windowA.size);
        final Vector2f windowBSizeOld = new Vector2f(windowB.size);
        assertEquals(dockspaceID, windowA.dockNode.id);

        ctx.undockNode(dockspaceID);

        assertNotNull(windowA.dockNode);
        assertNotNull(windowB.dockNode);
        assertSame(windowA.dockNode, windowB.dockNode);
        // Actually undocked
        assertTrue(windowA.dockNode.id != dockspaceID);
        assertEquals(windowB.size, windowA.dockNode.size);
        assertEquals(windowASizeOld, windowA.size);
        assertEquals(windowBSizeOld, windowB.size);
    }

    /**
     * docking_undock_focus_retention: a window being undocked keeps focus, even when another window
     * appears because of it (#3392).
     */
    @Test
    void testDockingUndockFocusRetention() {
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(200, 100, Condition.ALWAYS);
                    if (IkGui.begin("Window A", null, WindowFlags.NO_SAVED_SETTINGS)) {
                        IkGui.textUnformatted("lorem ipsum");

                        IkGui.begin("Window A2", null, WindowFlags.NO_SAVED_SETTINGS);
                        IkGui.textUnformatted("lorem ipsum");
                        IkGui.end();
                    }
                    IkGui.end();

                    IkGui.setNextWindowSize(200, 100, Condition.ALWAYS);
                    IkGui.begin("Window B", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.textUnformatted("lorem ipsum");
                    IkGui.end();
                });
        final var g = IkGuiInternal.context;
        ctx.windowCollapse("Window A", false);
        ctx.dockClear("Window A", "Window B");
        final Window windowB = ctx.getWindowByRef("Window B");
        final Window windowA2 = ctx.getWindowByRef("Window A2");

        // Window A2 is visible when all the windows are undocked
        assertTrue(windowA2.active);
        ctx.windowFocus("Window A");
        ctx.dockInto("Window B", "Window A");

        // Window A2 is hidden when Window B is docked, as B becomes the active window in the dock
        assertFalse(windowA2.active);

        // Undock Window B, checking things in the middle of the operation
        if (!g.io.configDockingWithShift) {
            // Disable docking
            ctx.keyDown(KeyChord.ofMods(KeyModFlags.SHIFT));
        }
        ctx.mouseMove(windowB.idTab);
        ctx.mouseDown(MouseButton.LEFT);

        final float h = windowB.titleBarHeight;
        ctx.mouseMoveToPos(g.io.mousePosition.x - h * 2, g.io.mousePosition.y - h * 2);
        ctx.yieldFrame();

        // Window A2 becomes visible during the undock, but doesn't capture the focus
        assertTrue(windowA2.active);
        assertSame(windowB, g.windowMoving);
        assertSame(windowB, g.windowFocusOrder.getLast());

        ctx.mouseUp(MouseButton.LEFT);
        if (!g.io.configDockingWithShift) {
            ctx.keyUp(KeyChord.ofMods(KeyModFlags.SHIFT));
        }
    }

    /**
     * docking_sizing_1: the sizes of the windows in a dock node tree, before and after resizing.
     */
    @Test
    void testDockingSizing1() {
        final DockingTestsGenericVars vars = new DockingTestsGenericVars();
        final boolean[] firstGuiFrame = {true};
        ctx.setGui(
                () -> {
                    final int[] ids = vars.nodesID;
                    if (firstGuiFrame[0]) {
                        firstGuiFrame[0] = false;
                        final int rootID = IkGui.getID("Test Node");
                        ids[0] = rootID;
                        IkGuiInternal.dockBuilderRemoveNode(ids[0]);
                        IkGuiInternal.dockBuilderAddNode(
                                ids[0], DockNodeFlags.INTERNAL_CENTRAL_NODE);
                        final Vector2f viewportPos = IkGui.getMainViewport().position;
                        IkGuiInternal.dockBuilderSetNodePos(
                                ids[0], viewportPos.x + 20, viewportPos.y + 20);
                        IkGuiInternal.dockBuilderSetNodeSize(ids[0], 1000, 500);

                        final IkInt a = new IkInt();
                        final IkInt b = new IkInt();
                        IkGuiInternal.dockBuilderSplitNode(ids[0], Direction.RIGHT, 0.20f, a, b);
                        ids[1] = a.get();
                        ids[0] = b.get();
                        IkGuiInternal.dockBuilderSplitNode(
                                ids[0], Direction.LEFT, 0.20f / 0.80f + 0.002f, a, b);
                        ids[2] = a.get();
                        ids[0] = b.get();
                        IkGuiInternal.dockBuilderSplitNode(ids[0], Direction.DOWN, 0.20f, a, b);
                        ids[3] = a.get();
                        ids[0] = b.get();
                        IkGuiInternal.dockBuilderDockWindow("dockMainId", ids[0]);
                        IkGuiInternal.dockBuilderDockWindow("dockTopRightId", ids[1]);
                        IkGuiInternal.dockBuilderDockWindow("dockTopLeftId", ids[2]);
                        IkGuiInternal.dockBuilderDockWindow("dockDownId", ids[3]);
                        IkGuiInternal.dockBuilderFinish(rootID);
                    }

                    // Upstream notes that dockBuilderDockWindow() doesn't work on windows that
                    // haven't been created and use NO_SAVED_SETTINGS, so these don't use it
                    final float separator =
                            IkGuiInternal.context.style.variable.dockingSeparatorSize;
                    final String[] names = {
                        "dockMainId", "dockTopRightId", "dockTopLeftId", "dockDownId"
                    };
                    for (int n = 0; n < names.length; n++) {
                        IkGui.begin(names[n], null, WindowFlags.NONE);
                        final Vector2f sz = IkGui.getWindowSize();
                        if (vars.step == 1) {
                            ctx.checkEquals(ids[n], IkGui.getWindowDockID(), names[n] + " dock ID");
                        }
                        switch (n) {
                            case 0 -> {
                                if (vars.step == 1) {
                                    ctx.check(
                                            Math.abs(sz.x - (1000 - 200 - 200)) <= separator * 2,
                                            "main width");
                                    ctx.check(
                                            Math.abs(sz.y - (500 - 100)) <= separator * 2,
                                            "main height");
                                }
                                if (vars.step == 2) {
                                    ctx.check(
                                            Math.abs(sz.x - (1000 - 300 - 300)) <= separator * 2,
                                            "main width after");
                                    ctx.check(
                                            Math.abs(sz.y - (400 - 100)) <= separator * 2,
                                            "main height after");
                                }
                            }
                            case 1 -> {
                                if (vars.step == 1) {
                                    ctx.check(
                                            Math.abs(sz.x - 200) <= separator * 2,
                                            "top right width");
                                    ctx.check(
                                            Math.abs(sz.y - 500) <= separator * 2,
                                            "top right height");
                                }
                                // Upstream also checks the width at step 2, but marks it as broken
                                // since L|C|R doesn't resize neatly yet
                                if (vars.step == 2) {
                                    ctx.check(
                                            Math.abs(sz.y - 400) <= separator * 2,
                                            "top right height after");
                                }
                            }
                            case 2 -> {
                                if (vars.step == 1) {
                                    ctx.check((sz.x - 200) <= separator * 2, "top left width");
                                    ctx.check((sz.y - 500) <= separator * 2, "top left height");
                                }
                                if (vars.step == 2) {
                                    ctx.check(
                                            (sz.y - 400) <= separator * 2, "top left height after");
                                }
                            }
                            default -> {
                                if (vars.step == 1 || vars.step == 2) {
                                    ctx.check((sz.y - 100) <= separator * 2, "down height");
                                }
                            }
                        }
                        IkGui.text(String.format("(%.1f,%.1f)", sz.x, sz.y));
                        IkGui.end();
                    }
                });
        vars.step = 0;
        ctx.yieldFrames(2);

        vars.step = 1;
        final DockNode rootNode =
                IkGuiInternal.dockNodeGetRootNode(
                        IkGuiInternal.dockBuilderGetNode(vars.nodesID[0]));
        assertNotNull(rootNode);
        final Window hostWindow = rootNode.hostWindow;
        assertNotNull(hostWindow);
        assertVec(1000, 500, hostWindow.size, "host size");
        ctx.yieldFrames(2);

        vars.step = 0;
        ctx.windowResize(hostWindow, 800, 400);
        vars.step = 2;
        ctx.yieldFrames(2);
    }

    /** docking_split_payload: transferring a full node payload, even with hidden windows. */
    @Test
    void testDockingSplitPayload() {
        final DockingTestsGenericVars vars = new DockingTestsGenericVars();
        vars.setShowWindows(3, true);
        ctx.setGui(() -> dockingTestsGenericGuiFunc(vars));
        final Window windowA = ctx.getWindowByRef("AAA");
        final Window windowB = ctx.getWindowByRef("BBB");
        final Window windowC = ctx.getWindowByRef("CCC");

        // The variants:
        // BBB | CCC
        // Hide BBB
        // Dock CCC into AAA (0) or the dockspace (1)
        // Undock CCC (2, 3)
        // Show BBB
        for (int variant = 0; variant < 4; variant++) {
            final String d = "variant " + variant;
            vars.showDockspace = (variant & 1) != 0;
            vars.setShowWindows(3, true);
            ctx.dockClear("AAA", "BBB", "CCC");
            // BBB | CCC
            ctx.dockInto("BBB", "CCC", Direction.LEFT);
            // Hide BBB
            vars.showWindow[1] = false;
            ctx.yieldFrame();

            final int dockDest = vars.showDockspace ? vars.dockSpaceID : windowA.id;
            // Upstream notes that docking from a single visible tab (part of a hidden iceberg of
            // nodes) and docking from the collapse button don't behave the same yet, so it docks
            // the node
            ctx.dockInto(windowC.dockID, dockDest, Direction.NONE, false);

            // Undock CCC with BBB hidden
            if (variant >= 2) {
                ctx.undockWindow("CCC");
            }

            // variant 0: (BBB) | AAA
            // variant 1: [ (BBB) | CCC ], node CCC is central
            // variant 2: (BBB) | AAA + CCC
            // variant 3: [ ] + (BBB) | CCC, desirable but currently BBB is simply undocked
            // Show BBB
            vars.showWindow[1] = true;
            ctx.yieldFrame();

            DockNode rootNode;
            switch (variant) {
                case 0 -> {
                    // (BBB) | AAA, CCC becomes BBB | AAA, CCC
                    rootNode = IkGuiInternal.dockNodeGetRootNode(windowA.dockNode);
                    assertEquals(1, rootNode.childNodes[0].windows.size(), d);
                    assertEquals(windowB.dockID, rootNode.childNodes[0].id, d);
                    assertEquals(2, rootNode.childNodes[1].windows.size(), d);
                    assertEquals(windowA.dockID, rootNode.childNodes[1].id, d);
                    assertEquals(windowC.dockID, rootNode.childNodes[1].id, d);
                }
                case 1 -> {
                    // [ (BBB) | CCC ] becomes [ BBB | CCC ]
                    rootNode = IkGuiInternal.dockNodeGetRootNode(windowC.dockNode);
                    assertTrue(rootNode.isDockSpace(), d);
                    assertEquals(1, rootNode.childNodes[0].windows.size(), d);
                    assertEquals(windowB.dockID, rootNode.childNodes[0].id, d);
                    assertEquals(1, rootNode.childNodes[1].windows.size(), d);
                    assertEquals(windowC.dockID, rootNode.childNodes[1].id, d);
                    assertTrue(rootNode.childNodes[1].isCentralNode(), d);
                }
                case 2 -> {
                    // (BBB) | AAA + CCC becomes BBB | AAA + CCC
                    rootNode = IkGuiInternal.dockNodeGetRootNode(windowA.dockNode);
                    assertEquals(windowB.dockID, rootNode.childNodes[0].id, d);
                    assertEquals(windowA.dockID, rootNode.childNodes[1].id, d);
                    assertTrue(rootNode.childNodes[1].id != windowC.dockID, d);
                }
                case 3 -> {
                    // Desirable would be [ ] + BBB | CCC, but currently it's [ BBB | ... ] + CCC.
                    // The dock node of the dockspace is left empty. Upstream checks the desirable
                    // result only with IMGUI_BROKEN_TESTS.
                    rootNode = IkGuiInternal.dockBuilderGetNode(dockDest);
                    assertTrue(rootNode.isDockSpace(), d);
                    assertNotNull(rootNode.childNodes[0], d);
                    assertEquals(windowB.dockID, rootNode.childNodes[0].id, d);
                    assertEquals(0, windowC.dockID, d);
                }
                default -> throw new IllegalStateException();
            }
        }
    }

    /** docking_window_appearing: the appearing state of docked windows. */
    @Test
    void testDockingWindowAppearing() {
        final boolean[] show = {false, false};
        final int[] appearing = {0, 0};
        final boolean[] withWindowAppending = {false};
        final int[] setNextBDockID = {0};
        ctx.setGui(
                () -> {
                    if (show[0]) {
                        IkGui.setNextWindowSize(300, 100, Condition.APPEARING);
                        if (IkGui.begin("AAA")) {
                            appearing[0] += IkGui.isWindowAppearing() ? 1 : 0;
                            IkGui.textUnformatted("AAA");
                            IkGui.text(
                                    String.format(
                                            "AppearingCount: %d, DockNode: %x",
                                            appearing[0], IkGui.getWindowDockID()));
                        }
                        IkGui.end();
                        if (withWindowAppending[0]) {
                            IkGui.begin("AAA");
                            IkGui.text("(appending)");
                            IkGui.end();
                        }
                    }
                    if (show[1]) {
                        if (setNextBDockID[0] != 0) {
                            IkGui.setNextWindowDockID(setNextBDockID[0], Condition.APPEARING);
                            setNextBDockID[0] = 0;
                        }
                        IkGui.setNextWindowSize(300, 100, Condition.APPEARING);
                        final boolean ret = IkGui.begin("BBB");
                        final boolean isAppearing = IkGui.isWindowAppearing();
                        if (ret) {
                            appearing[1] += isAppearing ? 1 : 0;
                            IkGui.textUnformatted("BBB");
                            IkGui.text(
                                    String.format(
                                            "AppearingCount: %d, DockNode: %x",
                                            appearing[1], IkGui.getWindowDockID()));
                        }
                        IkGui.end();
                        if (withWindowAppending[0]) {
                            IkGui.begin("BBB");
                            IkGui.text("(appending)");
                            IkGui.end();
                        }
                    }
                    IkGui.setNextWindowSize(300, 100, Condition.APPEARING);
                    IkGui.begin("CCC");
                    IkGui.textUnformatted("CCC");
                    IkGui.end();
                });
        final java.util.function.IntConsumer reappearWindow =
                n -> {
                    show[n] = false;
                    ctx.yieldFrames(2);
                    show[n] = true;
                    ctx.yieldFrames(2);
                };
        for (int variant = 0; variant < 2; variant++) {
            final String d = "variant " + variant;
            show[0] = false;
            show[1] = false;
            withWindowAppending[0] = variant == 1;
            appearing[0] = 0;
            appearing[1] = 0;

            ctx.dockClear("AAA", "BBB", "CCC");

            // Not docked
            show[0] = true;
            ctx.yieldFrames(2);
            assertEquals(1, appearing[0], d);
            show[1] = true;
            ctx.yieldFrames(2);
            assertEquals(1, appearing[1], d);

            // Docked as tabs
            ctx.dockInto("BBB", "AAA");
            assertEquals(1, appearing[0], d);
            assertEquals(1, appearing[1], d);
            reappearWindow.accept(1);
            assertEquals(2, appearing[1], d);

            // Docked as splits
            ctx.dockClear("AAA", "BBB");
            assertEquals(1, appearing[0], d);
            assertEquals(2, appearing[1], d);
            ctx.dockInto("BBB", "AAA", Direction.RIGHT);
            assertEquals(1, appearing[0], d);
            assertEquals(2, appearing[1], d);
            reappearWindow.accept(1);
            assertEquals(1, appearing[0], d);
            assertEquals(3, appearing[1], d);

            // Docked with setNextWindowDockID()
            final Window windowA = ctx.getWindowByRef("AAA");
            setNextBDockID[0] = windowA.dockID;
            show[1] = false;
            ctx.dockClear("AAA", "BBB", "CCC");
            ctx.dockInto("CCC", "AAA");
            reappearWindow.accept(1);
            assertEquals(4, appearing[1], d);

            // Switching tabs
            ctx.windowFocus("AAA");
            ctx.yieldFrame();
            assertEquals(2, appearing[0], d);
        }
    }

    /**
     * docking_window_appearing_layout: windows appearing again, and how they affect the layout of
     * the other windows across frames.
     */
    @Test
    void testDockingWindowAppearingLayout() {
        final boolean[] show = {false, false};
        final boolean[] swapShowOrder = {false};
        final Vector2f posA = new Vector2f();
        final Vector2f posB = new Vector2f();
        final Vector2f sizeA = new Vector2f();
        final Vector2f sizeB = new Vector2f();
        ctx.setGui(
                () -> {
                    for (int n = 0; n < 2; n++) {
                        if (show[0]
                                && ((!swapShowOrder[0] && n == 0)
                                        || (swapShowOrder[0] && n == 1))) {
                            IkGui.begin("AAA");
                            IkGui.textUnformatted("AAA");
                            posA.set(IkGui.getWindowPos());
                            sizeA.set(IkGui.getWindowSize());
                            IkGui.end();
                        }
                        if (show[1]
                                && ((swapShowOrder[0] && n == 0)
                                        || (!swapShowOrder[0] && n == 1))) {
                            IkGui.begin("BBB");
                            IkGui.textUnformatted("BBB");
                            posB.set(IkGui.getWindowPos());
                            sizeB.set(IkGui.getWindowSize());
                            IkGui.end();
                        }
                    }
                });
        show[0] = true;
        show[1] = true;
        ctx.dockClear("AAA", "BBB");
        ctx.windowResize("AAA", 400, 800);
        ctx.windowResize("BBB", 400, 800);
        ctx.dockInto("BBB", "AAA", Direction.DOWN);

        final Window windowA = ctx.getWindowByRef("AAA");
        final Window windowB = ctx.getWindowByRef("BBB");

        // Both submission orders
        for (int variant = 0; variant < 2; variant++) {
            final String d = "variant " + variant;
            swapShowOrder[0] = variant == 1;
            assertTrue(sizeA.x == 400 && sizeA.y <= 400, d);
            assertTrue(sizeB.x == 400 && sizeB.y <= 400, d);

            // Hiding AAA (top)
            show[0] = false;
            ctx.yieldFrame();
            ctx.check(sizeB.y <= 400, d + " hide AAA frame 1");
            ctx.yieldFrame();
            ctx.check(sizeB.y == 800, d + " hide AAA frame 2");

            // Showing AAA (top). Thoroughly clear traces of the old size, to further test that it
            // gets calculated again.
            show[0] = true;
            windowA.size.set(666.0f, 666.0f);
            windowA.sizeFull.set(666.0f, 666.0f);
            sizeA.set(666.0f, 666.0f);
            IkGuiInternal.dockBuilderGetNode(windowA.dockID).size.set(666.0f, 666.0f);
            ctx.yieldFrame();
            ctx.check(sizeA.y <= 400, d + " show AAA frame 1, AAA");
            // The behavior changed upstream on 2021/12/02
            ctx.check(sizeB.y == 800, d + " show AAA frame 1, BBB");
            ctx.yieldFrame();
            ctx.check(sizeA.y <= 400, d + " show AAA frame 2, AAA");
            ctx.check(sizeB.y <= 400, d + " show AAA frame 2, BBB");

            // Hiding BBB (bottom)
            show[1] = false;
            ctx.yieldFrame();
            ctx.check(sizeA.y <= 400, d + " hide BBB frame 1");
            ctx.yieldFrame();
            ctx.check(sizeA.y == 800, d + " hide BBB frame 2");

            // Showing BBB (bottom)
            show[1] = true;
            windowB.size.set(666.0f, 666.0f);
            windowB.sizeFull.set(666.0f, 666.0f);
            sizeB.set(666.0f, 666.0f);
            IkGuiInternal.dockBuilderGetNode(windowB.dockID).size.set(666.0f, 666.0f);
            ctx.yieldFrame();
            // The behavior changed upstream on 2021/12/02
            ctx.check(sizeA.y == 800, d + " show BBB frame 1, AAA");
            ctx.check(sizeB.y <= 400, d + " show BBB frame 1, BBB");
            ctx.yieldFrame();
            ctx.check(sizeA.y <= 400, d + " show BBB frame 2, AAA");
            ctx.check(sizeB.y <= 400, d + " show BBB frame 2, BBB");
        }
    }

    /**
     * docking_popup_parent: a modal popup in a docked window, followed by another window (#5401).
     */
    @Test
    void testDockingPopupParent() {
        final boolean[] firstGuiFrame = {true};
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    final int dockspaceID = IkGui.getID("RootDockspace");
                    if (firstGuiFrame[0]) {
                        firstGuiFrame[0] = false;
                        IkGuiInternal.dockBuilderRemoveNode(dockspaceID);
                        IkGui.dockSpace(dockspaceID);
                        final IkInt node1 = new IkInt();
                        final int node2 =
                                IkGuiInternal.dockBuilderSplitNode(
                                        dockspaceID, Direction.RIGHT, 0.42f, null, node1);
                        IkGuiInternal.dockBuilderDockWindow("WindowWithPopup", node1.get());
                        IkGuiInternal.dockBuilderDockWindow("WindowAfterPopup", node2);
                        IkGuiInternal.dockBuilderFinish(dockspaceID);
                    } else {
                        IkGui.dockSpace(dockspaceID);
                    }
                    IkGui.end();

                    IkGui.begin("WindowWithPopup");
                    if (!IkGui.isPopupOpen("popup")) {
                        IkGui.openPopup("popup");
                    }
                    if (IkGui.beginPopupModal("popup")) {
                        IkGui.endPopup();
                    }
                    IkGui.end();

                    // This crashed upstream (#5401)
                    IkGui.begin("WindowAfterPopup");
                    IkGui.end();
                });
        ctx.yieldFrames(3);
    }

    /** docking_dockspace_tab_amend: amending the tab bar of a dockspace node (#5515). */
    @Test
    void testDockingDockspaceTabAmend() {
        final int[] dockID = {0};
        final int[] buttonClicks = {0};
        ctx.setGui(
                () -> {
                    dockID[0] = IkGui.dockSpaceOverViewport();
                    final Window windowDemo = ctx.getWindowByRef("//" + IkGuiTestContext.DEMO);
                    // The demo window doesn't exist yet on the first frame, since it is shown
                    // after this
                    // Upstream notes that it can be confusing which dock node to use here, so it
                    // uses the node the demo window is docked in
                    final DockNode node = windowDemo == null ? null : windowDemo.dockNode;
                    if (node != null && IkGuiInternal.dockNodeBeginAmendTabBar(node)) {
                        if (IkGui.tabItemButton("+", TabItemFlags.LEADING)) {
                            buttonClicks[0]++;
                        }
                        IkGuiInternal.dockNodeEndAmendTabBar();
                    }
                    // The upstream test app shows its windows after the test GUI, which matters
                    // here: a window submitted before a dockspace can't be docked into it
                    ctx.showApp();
                });
        final var g = IkGuiInternal.context;
        final Window windowDemo = ctx.getWindowByRef(IkGuiTestContext.DEMO);
        ctx.dockInto(ctx.getID(IkGuiTestContext.DEMO), dockID[0], Direction.NONE, false);
        assertSame(windowDemo, g.windowFocusOrder.getLast());

        // The appended button works. Upstream would also check that the dock host stays
        // focused, but marks it as broken.
        ctx.itemClick(ctx.getID("+", windowDemo.dockNode.id));
        assertEquals(1, buttonClicks[0]);

        // tabItemButton() doesn't trigger an assert
        ctx.itemClick(ctx.getID("#CLOSE", windowDemo.dockNode.id));
        ctx.itemCheck("Hello, world!/Demo Window");

        // tabItemButton() made a dockspace child window get inserted into the focus order, which
        // eventually caused a crash upstream (#5515)
        ctx.menuCheck("//" + IkGuiTestContext.DEMO + "/Tools/Metrics\\/Debugger");
        ctx.windowClose("IkGui Metrics\\/Debugger");
        for (Window window : g.windowFocusOrder) {
            // Docked windows are converted to child windows, and are valid in this list
            if (!window.dockNodeIsVisible) {
                assertTrue(
                        (window.flags & WindowFlags.INTERNAL_CHILD_WINDOW) == 0
                                || (window.flags & WindowFlags.INTERNAL_POPUP) != 0,
                        window.name);
            }
        }
    }

    /** docking_settings_invalid_1: recovering from invalid docking settings (#9070). */
    @Test
    void testDockingSettingsInvalid1() {
        final String iniData =
                "[Window][Window0000]\n"
                        + "Pos=121,16\n"
                        + "Size=479,759\n"
                        + "DockId=0x00000002\n"
                        + "[Window][Window0001]\n"
                        + "DockId=0x00000003\n"
                        + "[Window][Window0002]\n"
                        + "Pos=0,16\n"
                        + "Size=119,759\n"
                        + "DockId=0x00000004\n"
                        + "[Window][WindowOverViewport_11111111]"
                        + "Pos=0,16\n"
                        + "Size=600,759\n"
                        + "[Window][Window0003]\n"
                        + "Size=450,350\n"
                        + "[Docking][Data]\n"
                        + "DockSpace     ID=0x08BD597D Window=0x1BBC0F80 Pos=100,116 Size=600,759 Split=X\n"
                        + "  DockNode    ID=0x00000002 Parent=0x08BD597D SizeRef=479,800 CentralNode=1 NoTabBar=1 HiddenTabBar=1 Selected=0x5EE3988C\n"
                        + "    DockNode  ID=0x00000004 Parent=0x00000001 SizeRef=119,399 NoTabBar=1 HiddenTabBar=1 Selected=0x35623F2B\n"
                        + "    DockNode  ID=0x00000004 Parent=0x00000001 SizeRef=119,399 NoTabBar=1 HiddenTabBar=1 Selected=0x35623F2B\n"
                        + "  DockNode    ID=0x00000002 Parent=0x08BD597D SizeRef=479,800 CentralNode=1 NoTabBar=1 HiddenTabBar=1 Selected=0x5EE3988C\n";
        // Upstream creates a separate context for this. Each test here has a fresh context, so
        // the settings are loaded into it before its first frame with docking.
        IkGui.loadIniSettingsFromMemory(iniData);
        ctx.setGui(IkGui::dockSpaceOverViewport);
        ctx.yieldFrames(2);
    }
}
