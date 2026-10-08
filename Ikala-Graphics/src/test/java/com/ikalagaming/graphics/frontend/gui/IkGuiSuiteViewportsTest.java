package com.ikalagaming.graphics.frontend.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.frontend.gui.data.IkBoolean;
import com.ikalagaming.graphics.frontend.gui.data.Viewport;
import com.ikalagaming.graphics.frontend.gui.data.Window;
import com.ikalagaming.graphics.frontend.gui.data.WindowClass;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.Direction;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.flags.ChildFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ColorEditFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ViewportFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Multiple viewport tests ported from the Dear ImGui test suite (imgui_tests_viewports.cpp), which
 * is MIT licensed. Each test notes the name of the upstream test it is ported from.
 *
 * <p>Upstream only runs these with viewports enabled and a backend that supports them. These run
 * headless on a fake platform backend ({@link FakeViewportPlatform}), with the main viewport at the
 * origin of the desktop.
 */
class IkGuiSuiteViewportsTest {
    private IkGuiTestContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new IkGuiTestContext();
        ctx.enableViewports();
    }

    @AfterEach
    void tearDown() {
        try {
            ctx.assertNoErrors();
        } finally {
            ctx.destroy();
        }
    }

    /** Check that a vector has some value. */
    private static void assertVec(float x, float y, Vector2f actual, String description) {
        assertEquals(x, actual.x, description + " x");
        assertEquals(y, actual.y, description + " y");
    }

    /** viewport_basic_1: windows moving in and out of the main viewport. */
    @Test
    void testViewportBasic1() {
        final int[] step = {0};
        final int[] count = {0};
        ctx.setGui(
                () -> {
                    final Viewport mainViewport = IkGui.getMainViewport();
                    if (step[0] == 0) {
                        IkGui.setNextWindowSize(200, 200, Condition.ALWAYS);
                        IkGui.setNextWindowPos(
                                mainViewport.workPosition.x + 20,
                                mainViewport.workPosition.y + 20,
                                Condition.ALWAYS);
                    }
                    if (step[0] == 2) {
                        final Window window = IkGuiInternal.findWindowByName("Test Window");
                        if (IkGuiInternal.context.windowMoving == window) {
                            count[0]++;
                        }
                        if (count[0] >= 1) {
                            return;
                        }
                    }
                    // NO_BRING_TO_FRONT_ON_FOCUS avoids a stray viewport in between preventing a
                    // merge. Upstream notes that it should test both.
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.NO_BRING_TO_FRONT_ON_FOCUS);
                    IkGui.text("hello!");
                    IkGui.end();
                });
        ctx.setRef("Test Window");

        // Inside the main viewport
        final Window window = ctx.getWindowByRef("");
        final Viewport mainViewport = IkGui.getMainViewport();
        step[0] = 0;
        ctx.yieldFrame();
        assertSame(mainViewport, window.viewport);

        // Outside, which creates its own viewport
        step[0] = 1;
        ctx.windowMoveByDrag("", mainViewport.position.x - 20.0f, mainViewport.position.y - 20.0f);
        assertNotSame(mainViewport, window.viewport);
        assertTrue(window.viewportOwned);
        assertSame(window, window.viewport.window);

        // Back inside
        ctx.windowMoveByDrag(
                "", mainViewport.workPosition.x + 20.0f, mainViewport.workPosition.y + 20.0f);
        assertSame(mainViewport, window.viewport);
        assertNull(window.viewport.window);
        assertVec(
                mainViewport.workPosition.x + 20.0f,
                mainViewport.workPosition.y + 20.0f,
                window.position,
                "pos");

        // The viewport disappearing while moving
        ctx.windowMoveByDrag("", mainViewport.position.x - 20.0f, mainViewport.position.y - 20.0f);
        step[0] = 2;
        count[0] = 0;
        ctx.windowMoveByDrag(
                "", mainViewport.workPosition.x + 100.0f, mainViewport.workPosition.y + 100.0f);
        assertFalse(
                window.position.x == mainViewport.workPosition.x + 100.0f
                        && window.position.y == mainViewport.workPosition.y + 100.0f);
    }

    /**
     * viewport_translate: windows hosted by the main viewport move with it, when the platform
     * window moves (#7985).
     */
    @Test
    void testViewportTranslate() {
        ctx.setGui(
                () -> {
                    final Viewport mainViewport = IkGui.getMainViewport();
                    IkGui.begin("Test Window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.end();

                    IkGui.setNextWindowViewport(mainViewport.id);
                    IkGui.begin("Test Window 2", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.end();
                });
        final Viewport mainViewport = IkGui.getMainViewport();
        final Vector2f mainViewportBackupPos = new Vector2f(mainViewport.position);

        for (int step = 0; step < 4; step++) {
            final String d = "step " + step;
            // Fully contained, or overlapping the edge
            final boolean fullyContained = (step & 1) == 0;
            // An automatic viewport, or locked to the main viewport
            final boolean lockedToMainViewport = (step & 2) != 0;
            final Window window =
                    ctx.getWindowByRef(
                            lockedToMainViewport ? "//Test Window 2" : "//Test Window 1");
            ctx.windowResize(window, 100, 100);
            final Vector2f pos1 =
                    new Vector2f(
                            mainViewport.position.x
                                    + mainViewport.size.x
                                    - (fullyContained ? 150.0f : 50.0f),
                            mainViewport.position.y + 20);
            ctx.windowMove(window, pos1.x, pos1.y);
            if (fullyContained || lockedToMainViewport) {
                assertSame(mainViewport, window.viewport, d);
            } else {
                assertNotSame(mainViewport, window.viewport, d);
            }
            assertVec(pos1.x, pos1.y, window.position, d + " pos1");
            ctx.viewportPlatformSetWindowPos(
                    mainViewport, mainViewport.position.x - 30.0f, mainViewport.position.y);
            final Vector2f pos2 =
                    new Vector2f(
                            mainViewport.position.x
                                    + mainViewport.size.x
                                    - (fullyContained ? 150.0f : 50.0f),
                            mainViewport.position.y + 20);
            if (fullyContained || lockedToMainViewport) {
                assertSame(mainViewport, window.viewport, d);
                assertVec(pos2.x, pos2.y, window.position, d + " pos2");
            } else {
                assertNotSame(mainViewport, window.viewport, d);
                assertVec(pos1.x, pos1.y, window.position, d + " pos1 kept");
            }
            ctx.viewportPlatformSetWindowPos(
                    mainViewport, mainViewportBackupPos.x, mainViewportBackupPos.y);
        }
    }

    /** viewport_parent_id: the value of the parent viewport ID, including bug #4756. */
    @Test
    void testViewportParentId() {
        final WindowClass[] windowClass = {new WindowClass()};
        final boolean[] setWindowClass = {false};
        ctx.setGui(
                () -> {
                    if (setWindowClass[0]) {
                        IkGui.setNextWindowClass(windowClass[0]);
                    }
                    IkGui.setNextWindowSize(50, 50, Condition.ALWAYS);
                    IkGui.begin("Test Window with Class", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.end();
                });
        final var g = IkGuiInternal.context;
        ctx.setRef("Test Window with Class");
        final Window window = ctx.getWindowByRef("");

        // Reset the class, as it currently persists if not set
        windowClass[0] = new WindowClass();
        setWindowClass[0] = true;
        ctx.yieldFrame();

        // The default value
        final Vector2f mainPos = IkGui.getMainViewport().position;
        ctx.windowMove("", mainPos.x + 100, mainPos.y + 100);
        assertFalse(window.viewportOwned);
        assertEquals(-1, window.windowClass.parentViewportID);

        // Unset. The result depends on the value of configViewportsNoDefaultParent.
        windowClass[0].viewportFlagsOverrideSet = ViewportFlags.NO_AUTO_MERGE;
        setWindowClass[0] = true;

        g.io.configViewportsNoDefaultParent = true;
        ctx.yieldFrame();
        assertEquals(0, window.viewport.parentViewportID);

        g.io.configViewportsNoDefaultParent = false;
        ctx.yieldFrame();
        assertEquals(IkGui.getMainViewport().id, window.viewport.parentViewportID);

        // Explicitly set the parent viewport ID. 0 may or may not be a special value; currently
        // it isn't.
        windowClass[0].parentViewportID = 0;
        ctx.yieldFrame();
        assertEquals(0, window.viewport.parentViewportID);

        // This is definitely not a special value
        windowClass[0].parentViewportID = 0x12345678;
        ctx.yieldFrame();
        assertEquals(0x12345678, window.viewport.parentViewportID);
    }

    /** viewport_platform_focus: platform focus leads to GUI focus (#6299). */
    @Test
    void testViewportPlatformFocus() {
        ctx.setGui(
                () -> {
                    final Viewport mainViewport = IkGui.getMainViewport();
                    IkGui.setNextWindowPos(
                            mainViewport.position.x + 10,
                            mainViewport.position.y + 10,
                            Condition.ALWAYS);
                    // This assumes it fits in the host viewport
                    IkGui.setNextWindowSize(200, 200, Condition.ALWAYS);
                    IkGui.begin("Window A", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.button("Button A");
                    IkGui.beginChild("Child A", 100, 100, ChildFlags.BORDERS);
                    IkGui.button("Button Child A");
                    IkGui.endChild();
                    IkGui.end();

                    IkGui.setNextWindowPos(
                            mainViewport.position.x + mainViewport.size.x,
                            mainViewport.position.y + 10,
                            Condition.ALWAYS);
                    IkGui.begin("Window B", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.button("Button B");
                    IkGui.beginChild("Child B", 100, 100, ChildFlags.BORDERS);
                    IkGui.button("Button Child B");
                    IkGui.endChild();
                    IkGui.end();
                });
        final var g = IkGuiInternal.context;
        final Window windowA = ctx.getWindowByRef("//Window A");
        final Window windowB = ctx.getWindowByRef("//Window B");
        final Window windowChildA = ctx.getChildWindow("//Window A", "Child A");
        final Window windowChildB = ctx.getChildWindow("//Window B", "Child B");
        assertTrue(
                windowA != null && windowB != null && windowChildA != null && windowChildB != null);
        ctx.yieldFrame();

        final Viewport viewportA = windowA.viewport;
        final Viewport viewportB = windowB.viewport;
        assertNotNull(viewportA);
        assertEquals(IkGui.getMainViewport().id, windowA.viewportID);
        assertNotNull(viewportB);
        assertTrue(windowB.viewportID != IkGui.getMainViewport().id);

        ctx.itemClick("//Window A/Button A");
        assertSame(windowA, g.navFocusedWindow);
        // A no-op
        ctx.viewportPlatformSetWindowFocus(viewportA);
        assertSame(windowA, g.navFocusedWindow);

        ctx.viewportPlatformSetWindowFocus(viewportB);
        assertSame(windowB, g.navFocusedWindow);

        ctx.viewportPlatformSetWindowFocus(viewportA);
        assertSame(windowA, g.navFocusedWindow);
        ctx.setRef(windowChildA);
        ctx.itemClick("Button Child A");
        assertSame(windowChildA, g.navFocusedWindow);
        ctx.viewportPlatformSetWindowFocus(viewportB);
        assertSame(windowB, g.navFocusedWindow);
        ctx.viewportPlatformSetWindowFocus(viewportA);
        assertSame(windowChildA, g.navFocusedWindow);

        ctx.viewportPlatformSetWindowFocus(viewportB);
        ctx.setRef(windowChildB);
        ctx.itemClick("Button Child B");
        assertSame(windowChildB, g.navFocusedWindow);
        ctx.viewportPlatformSetWindowFocus(viewportA);
        assertSame(windowChildA, g.navFocusedWindow);
        ctx.viewportPlatformSetWindowFocus(viewportB);
        assertSame(windowChildB, g.navFocusedWindow);

        ctx.mouseClickOnVoid(MouseButton.LEFT, viewportA);
        assertNull(g.navFocusedWindow);

        // The mouse actions are propagated to the focus
        assertTrue((viewportA.flags & ViewportFlags.IS_FOCUSED) != 0);
        assertEquals(0, viewportB.flags & ViewportFlags.IS_FOCUSED);
        assertTrue(viewportA.lastFocusedStampCount > viewportB.lastFocusedStampCount);

        // A null focus is kept after focusing viewport A again, which didn't have focus
        ctx.setRef("");
        ctx.itemClick("//Window B/Button B");
        assertTrue((viewportB.flags & ViewportFlags.IS_FOCUSED) != 0);
        assertSame(windowB, g.navFocusedWindow);
        ctx.viewportPlatformSetWindowFocus(viewportA);
        assertNull(g.navFocusedWindow);
    }

    /** viewport_platform_focus_2: more platform focus leading to GUI focus (#6299). */
    @Test
    void testViewportPlatformFocus2() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Window A", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.button("Button A");
                    IkGui.end();

                    IkGui.begin("Window B", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.button("Button B");
                    IkGui.end();
                });
        final var g = IkGuiInternal.context;
        final Viewport mainViewport = IkGui.getMainViewport();
        final Window windowA = ctx.getWindowByRef("//Window A");
        final Window windowB = ctx.getWindowByRef("//Window B");

        // Move all the other windows which could be in the way into the main viewport
        for (Window window : new java.util.ArrayList<>(g.windowFocusOrder)) {
            if (window != windowA
                    && window != windowB
                    && window.viewport != mainViewport
                    && window.wasActive) {
                ctx.windowResize(window, 100, 100);
                ctx.windowMove(window, mainViewport.position.x + 10, mainViewport.position.y + 10);
                assertSame(mainViewport, window.viewport, window.name);
            }
        }

        ctx.windowResize("//Window A", 100, 100);
        ctx.windowMove("//Window A", mainViewport.position.x + 10, mainViewport.position.y + 10);
        assertSame(mainViewport, windowA.viewport);

        ctx.windowResize("//Window B", 100, 100);
        ctx.windowMove("//Window B", mainViewport.position.x + 30, mainViewport.position.y + 30);
        assertSame(mainViewport, windowB.viewport);

        ctx.windowMoveByDrag(
                "//Window B",
                mainViewport.position.x + mainViewport.size.x,
                mainViewport.position.y);
        assertNotSame(mainViewport, windowB.viewport);
        assertSame(windowB, g.navFocusedWindow);

        ctx.windowMoveByDrag(
                "//Window B", mainViewport.position.x + 30, mainViewport.position.y + 30);
        assertSame(mainViewport, windowB.viewport);
        assertSame(windowB, g.navFocusedWindow);
    }

    /**
     * viewport_platform_focus_3: closing a popup in a viewport and opening another one, where the
     * temporary focus of the parent viewport shouldn't interfere (#6462).
     */
    @Test
    void testViewportPlatformFocus3() {
        ctx.setGui(
                () -> {
                    // ALWAYS_AUTO_RESIZE makes it small
                    if (IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE)) {
                        if (IkGui.beginPopup("second")) {
                            IkGui.text("success message!");
                            IkGui.endPopup();
                        }

                        boolean openSecondPopup = false;
                        if (IkGui.beginPopup("first")) {
                            if (IkGui.button("open second popup")) {
                                openSecondPopup = true;
                            }
                            IkGui.endPopup();
                        }
                        if (openSecondPopup) {
                            IkGui.openPopup("second");
                        }

                        if (IkGui.button("open first popup")) {
                            IkGui.openPopup("first");
                        }
                    }
                    IkGui.end();
                });
        final var g = IkGuiInternal.context;
        final Viewport mainViewport = IkGui.getMainViewport();
        // Upstream warms up for an extra frame, so the auto-resizing window has its size
        ctx.yieldFrame();
        final Window window = ctx.getWindowByRef("//Test Window");
        assertNotNull(window);
        ctx.windowMove(
                window,
                mainViewport.position.x + mainViewport.size.x - window.size.x - 2.0f,
                mainViewport.position.y + 10.0f);
        assertSame(mainViewport, window.viewport);

        ctx.itemClick("//Test Window/open first popup");
        // Let the popup finish appearing, so it has its size and its own viewport
        ctx.yieldFrame();
        final Window popup1 = g.navFocusedWindow;
        assertTrue(popup1 != null && (popup1.flags & WindowFlags.INTERNAL_POPUP) != 0);
        assertNotSame(mainViewport, popup1.viewport);

        // Before the upstream fix for #6462, this depended on whether the backend handled the
        // NO_FOCUS_ON_CLICK viewport flag
        ctx.itemClick("//$FOCUSED/open second popup");
        ctx.yieldFrame();
        final Window popup2 = g.navFocusedWindow;
        assertTrue(popup2 != null && (popup2.flags & WindowFlags.INTERNAL_POPUP) != 0);
        assertNotSame(popup1, popup2);
        assertNotSame(mainViewport, popup2.viewport);
    }

    /**
     * viewport_platform_focus_4: a docking edge case with a modal, which used to crash in
     * findBlockingModal() (#6462).
     */
    @Test
    void testViewportPlatformFocus4() {
        ctx.setGui(
                () -> {
                    if (IkGui.begin("Test Window 1", null, WindowFlags.NO_SAVED_SETTINGS)) {
                        if (IkGui.button("Open modal")) {
                            IkGui.openPopup("Modal");
                        }
                        if (IkGui.beginPopupModal("Modal")) {
                            if (IkGui.button("Close")) {
                                IkGui.closeCurrentPopup();
                            }
                            IkGui.endPopup();
                        }
                    }
                    IkGui.end();
                    IkGui.begin("Sibling", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.end();

                    IkGui.begin("Test Window 2", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.end();
                });
        final var g = IkGuiInternal.context;
        final Viewport mainViewport = IkGui.getMainViewport();

        // Dock and move, so the dock node has its own viewport
        ctx.dockClear("Test Window 1", "Sibling", "Test Window 2");
        final Window window1 = ctx.getWindowByRef("//Test Window 1");
        assertNotNull(window1);
        ctx.windowMove(
                window1, mainViewport.position.x + mainViewport.size.x, mainViewport.position.y);
        assertNotSame(mainViewport, window1.viewport);
        ctx.dockInto("Sibling", "Test Window 1");

        final Window window2 = ctx.getWindowByRef("//Test Window 2");
        assertNotNull(window2);
        final RectFloatView rect1 = new RectFloatView(window1);
        ctx.windowMove(window2, rect1.left, rect1.bottom + 10.0f);
        assertNotSame(mainViewport, window2.viewport);
        assertNotSame(window1.viewport, window2.viewport);

        ctx.itemClick("//Test Window 1/Open modal");

        final Window windowModal = g.navFocusedWindow;
        assertNotNull(windowModal);
        final RectFloatView rect2 = new RectFloatView(window2);
        ctx.windowMove(windowModal, rect2.left, rect2.bottom + 10.0f);

        // This used to crash in findBlockingModal()
        ctx.viewportPlatformSetWindowFocus(window2.viewport);
    }

    /** The edges of a window rectangle. */
    private record RectFloatView(float left, float bottom) {
        RectFloatView(Window window) {
            this(window.position.x, window.position.y + window.size.y);
        }
    }

    /** The GUI shared by several viewport tests, with two closable windows. */
    private static void genericGuiFuncTwoWindows(IkBoolean showWindow1, IkBoolean showWindow2) {
        final float[] color = {1.0f, 0.5f, 0.5f, 1.0f};
        if (showWindow1.get()) {
            IkGui.begin("Window 1", showWindow1, WindowFlags.NO_SAVED_SETTINGS);
            IkGui.colorButton("dummy", color, ColorEditFlags.NO_TOOLTIP, 250, 250);
            IkGui.end();
        }
        if (showWindow2.get()) {
            IkGui.begin("Window 2", showWindow2, WindowFlags.NO_SAVED_SETTINGS);
            IkGui.colorButton("dummy", color, ColorEditFlags.NO_TOOLTIP, 250, 250);
            IkGui.end();
        }
    }

    /** viewport_platform_close: closing a platform window closes the windows in it. */
    @Test
    void testViewportPlatformClose() {
        final IkBoolean showWindow1 = new IkBoolean(true);
        final IkBoolean showWindow2 = new IkBoolean(true);
        ctx.setGui(() -> genericGuiFuncTwoWindows(showWindow1, showWindow2));
        final Viewport mainViewport = IkGui.getMainViewport();

        ctx.dockClear("Window 1", "Window 2");
        ctx.windowMove(
                "Window 1", mainViewport.position.x + mainViewport.size.x, mainViewport.position.y);
        ctx.dockInto("Window 2", "Window 1");
        assertTrue(showWindow1.get());
        assertTrue(showWindow2.get());

        final Window window1 = ctx.getWindowByRef("Window 1");
        ctx.windowFocus("Window 1");
        ctx.viewportPlatformCloseWindow(window1.viewport);

        assertFalse(showWindow1.get());
        assertFalse(showWindow2.get());
    }

    /**
     * viewport_platform_close_2: closing a platform window with nested windows and dockspaces
     * (#8887).
     */
    @Test
    void testViewportPlatformClose2() {
        final boolean[] parentIsOpen = {true};
        final IkBoolean parentOpen = new IkBoolean(true);
        final IkBoolean[] childIsOpen = {
            new IkBoolean(true), new IkBoolean(true), new IkBoolean(true)
        };
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.button("Reopen Parent")) {
                        parentOpen.set(true);
                    }
                    if (IkGui.button("Reopen All")) {
                        parentOpen.set(true);
                        for (IkBoolean child : childIsOpen) {
                            child.set(true);
                        }
                    }
                    IkGui.end();

                    IkGui.setNextWindowSize(800.f, 1000.f, Condition.FIRST_USE_EVER);
                    if (parentOpen.get()) {
                        IkGui.begin("Parent Window", parentOpen, WindowFlags.NO_SAVED_SETTINGS);
                        final int dockspaceID = IkGui.getID("dockspace");
                        IkGui.dockSpace(dockspaceID, 500, 500);
                        for (int i = 0; i < 3; ++i) {
                            if (childIsOpen[i].get()) {
                                IkGui.setNextWindowDockID(dockspaceID, Condition.ALWAYS);
                                IkGui.begin(
                                        "Docked Window " + i,
                                        childIsOpen[i],
                                        WindowFlags.NO_SAVED_SETTINGS);
                                IkGui.end();
                            }
                        }
                        IkGui.end();
                    }
                    parentIsOpen[0] = parentOpen.get();
                });
        ctx.itemClick("//Test Window/Reopen All");
        assertTrue(
                parentOpen.get()
                        && childIsOpen[0].get()
                        && childIsOpen[1].get()
                        && childIsOpen[2].get());

        // Ensure it's in a standalone viewport
        ctx.dockClear("Parent Window");
        final Viewport mainViewport = IkGui.getMainViewport();
        ctx.windowMove(
                "Parent Window",
                mainViewport.position.x + mainViewport.size.x,
                mainViewport.position.y);
        final Window window = ctx.getWindowByRef("Parent Window");
        assertNotSame(mainViewport, window.viewport);

        // Close it using the platform decoration
        ctx.viewportPlatformCloseWindow(window.viewport);
        assertFalse(parentOpen.get());
        assertTrue(childIsOpen[0].get());
        assertTrue(childIsOpen[1].get());
        assertTrue(childIsOpen[2].get());

        ctx.itemClick("//Test Window/Reopen All");
        ctx.windowClose("Parent Window");
        assertFalse(parentOpen.get());
        assertTrue(childIsOpen[0].get());
        assertTrue(childIsOpen[1].get());
        assertTrue(childIsOpen[2].get());
    }

    /**
     * viewport_owner_change_1: a floating window in its own viewport keeps the viewport when
     * toggling always-tab-bar, so there is no viewport recreation or flickering.
     */
    @Test
    void testViewportOwnerChange1() {
        final IkBoolean showWindow1 = new IkBoolean(true);
        final IkBoolean showWindow2 = new IkBoolean(true);
        ctx.setGui(() -> genericGuiFuncTwoWindows(showWindow1, showWindow2));
        final var g = IkGuiInternal.context;
        showWindow2.set(false);
        ctx.dockClear("Window 1");
        g.io.configViewportsNoAutoMerge = false;
        g.io.configDockingAlwaysTabBar = false;
        ctx.yieldFrames(2);

        for (int variant = 0; variant < 2; variant++) {
            final String d = "variant " + variant;
            g.io.configViewportsNoAutoMerge = variant == 1;

            final Window window1 = ctx.getWindowByRef("Window 1");
            ctx.dockClear("Window 1");
            final Viewport mainViewport = IkGui.getMainViewport();
            ctx.windowMove(
                    "Window 1",
                    mainViewport.position.x + mainViewport.size.x,
                    mainViewport.position.y);
            assertTrue(window1.rootWindowDockTree.viewportOwned, d);

            int prevViewportCreatedCount = g.viewportCreatedCount;
            g.io.configDockingAlwaysTabBar = true;
            ctx.yieldFrames(3);
            assertEquals(prevViewportCreatedCount, g.viewportCreatedCount, d + " always tab bar");

            prevViewportCreatedCount = g.viewportCreatedCount;
            g.io.configDockingAlwaysTabBar = false;
            ctx.yieldFrames(3);
            assertEquals(prevViewportCreatedCount, g.viewportCreatedCount, d + " no tab bar");
        }
    }

    /**
     * viewport_owner_change_2: two windows docked together in their own viewport, with one of them
     * closed and shown again.
     */
    @Test
    void testViewportOwnerChange2() {
        final IkBoolean showWindow1 = new IkBoolean(true);
        final IkBoolean showWindow2 = new IkBoolean(true);
        ctx.setGui(() -> genericGuiFuncTwoWindows(showWindow1, showWindow2));
        final var g = IkGuiInternal.context;
        g.io.configViewportsNoAutoMerge = false;
        g.io.configDockingAlwaysTabBar = false;
        ctx.yieldFrames(2);
        ctx.dockClear("Window 1", "Window 2");

        for (int variant = 0; variant < 8; variant++) {
            final String d = "variant " + variant;
            g.io.configDockingAlwaysTabBar = (variant & 1) != 0;
            g.io.configViewportsNoAutoMerge = (variant & 2) != 0;

            final Window window1 = ctx.getWindowByRef("Window 1");
            final Viewport mainViewport = IkGui.getMainViewport();
            ctx.windowMove(
                    "Window 1",
                    mainViewport.position.x + mainViewport.size.x,
                    mainViewport.position.y);

            ctx.windowResize("Window 1", 255, 255);
            ctx.windowResize("Window 2", 255, 255);

            ctx.dockInto("Window 2", "Window 1");
            assertTrue(window1.rootWindowDockTree.viewportOwned, d);

            // Variant flag 4 tries both orders. Upstream would like to compare the created
            // viewport count, but notes there are still some spots where a viewport gets created
            // and then removed, so it uses the platform window count.

            // Close a window
            int prevCount = g.platformWindowsCreatedCount;
            ctx.windowClose((variant & 4) == 0 ? "Window 2" : "Window 1");
            ctx.yieldFrames(3);
            assertEquals(prevCount, g.platformWindowsCreatedCount, d + " close");

            // Show it again
            prevCount = g.platformWindowsCreatedCount;
            if ((variant & 4) == 0) {
                showWindow2.set(true);
            } else {
                showWindow1.set(true);
            }
            ctx.yieldFrames(3);
            assertEquals(prevCount, g.platformWindowsCreatedCount, d + " reappear");

            // Dock
            prevCount = g.platformWindowsCreatedCount;
            if ((variant & 4) == 0) {
                ctx.dockInto("Window 2", "Window 1", Direction.DOWN);
            } else {
                ctx.dockInto("Window 1", "Window 2", Direction.DOWN);
            }
            ctx.yieldFrames(3);
            assertEquals(prevCount + 1, g.platformWindowsCreatedCount, d + " dock");
        }
    }
}
