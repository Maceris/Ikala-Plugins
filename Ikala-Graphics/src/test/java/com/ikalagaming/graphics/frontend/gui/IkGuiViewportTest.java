package com.ikalagaming.graphics.frontend.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.DrawList;
import com.ikalagaming.graphics.frontend.gui.data.IkBoolean;
import com.ikalagaming.graphics.frontend.gui.data.Viewport;
import com.ikalagaming.graphics.frontend.gui.data.Window;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.flags.BackendFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ConfigFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ViewportFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Tests for multiple viewports, using a fake platform backend. These run headless, without fonts
 * loaded.
 */
class IkGuiViewportTest {

    /** How much precision we expect in float comparisons. */
    private static final float DELTA = 0.001f;

    /** Where the application window (main viewport) is on the desktop. */
    private static final float MAIN_X = 100;

    private static final float MAIN_Y = 100;

    private Context context;
    private FakeViewportPlatform platform;

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        // Don't read or write an .ini file
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        context.io.mouseInsideWindow = true;
        context.io.configFlags |= ConfigFlags.VIEWPORTS_ENABLE;
        // Process one input event per frame for each input, so we can be precise about timing
        context.io.configInputTrickleEventQueue = true;
        platform = new FakeViewportPlatform(context, MAIN_X, MAIN_Y);
    }

    @AfterEach
    void tearDown() {
        IkGui.destroyPlatformWindows();
        IkGui.destroyContext();
    }

    /**
     * Run a frame, including the platform window updates that the main loop would do.
     *
     * @param ui The UI for the frame.
     */
    private static void frame(Runnable ui) {
        IkGui.newFrame();
        ui.run();
        IkGui.render();
        IkGui.updatePlatformWindows();
        IkGui.renderPlatformWindowsDefault();
    }

    private static void frames(int count, Runnable ui) {
        for (int i = 0; i < count; ++i) {
            frame(ui);
        }
    }

    /**
     * Move the mouse, in desktop coordinates.
     *
     * @param x The x position.
     * @param y The y position.
     */
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
        frames(2, ui);
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frames(3, ui);
    }

    /**
     * Submit a simple window.
     *
     * @param name The window name.
     * @param x The initial x position, in desktop coordinates.
     * @param y The initial y position, in desktop coordinates.
     * @param open The open state, may be null.
     */
    private static void window(String name, float x, float y, IkBoolean open) {
        IkGui.setNextWindowPos(x, y, Condition.FIRST_USE_EVER);
        IkGui.setNextWindowSize(300, 200, Condition.FIRST_USE_EVER);
        if (open != null && !open.get()) {
            return;
        }
        IkGui.begin(name, open, WindowFlags.NONE);
        IkGui.text("Contents of " + name);
        IkGui.end();
    }

    private Window find(String name) {
        final Window window = IkGuiInternal.findWindowByName(name);
        assertNotNull(window, "No window named " + name);
        return window;
    }

    @Test
    void testViewportsRequireBackendSupport() {
        context.io.backendFlags &=
                ~(BackendFlags.PLATFORM_HAS_VIEWPORTS | BackendFlags.RENDERER_HAS_VIEWPORTS);
        final Runnable ui = () -> window("Outside", 2000, 300, null);
        frames(3, ui);

        // The feature is turned off, and everything stays in the main viewport
        assertEquals(0, context.io.configFlags & ConfigFlags.VIEWPORTS_ENABLE);
        final Window window = find("Outside");
        assertSame(context.mainViewport, window.viewport);
        assertFalse(window.viewportOwned);
        assertEquals(1, context.viewports.size());
        assertEquals(List.of(context.mainViewport), context.platformIO.viewports);
        assertTrue(platform.events.isEmpty());

        // The main viewport stays at the origin, ignoring the platform window position
        assertEquals(0, context.mainViewport.position.x, DELTA);
        assertEquals(0, context.mainViewport.position.y, DELTA);
    }

    @Test
    void testMainViewportUsesPlatformPosition() {
        final Runnable ui = () -> window("Inside", MAIN_X + 50, MAIN_Y + 50, null);
        frames(3, ui);

        final Viewport mainViewport = IkGui.getMainViewport();
        assertEquals(Viewport.DEFAULT_ID, mainViewport.id);
        assertEquals(MAIN_X, mainViewport.position.x, DELTA);
        assertEquals(MAIN_Y, mainViewport.position.y, DELTA);
        assertEquals(1280, mainViewport.size.x, DELTA);
        assertEquals(720, mainViewport.size.y, DELTA);
        assertEquals(0, mainViewport.platformMonitor);

        // Windows that fit stay in the main viewport
        final Window window = find("Inside");
        assertSame(mainViewport, window.viewport);
        assertFalse(window.viewportOwned);
        assertTrue(platform.events.isEmpty());

        // The draw data is positioned at the main viewport
        assertNotNull(IkGui.getDrawData());
        assertEquals(MAIN_X, IkGui.getDrawData().displayPosition.x, DELTA);
        assertEquals(MAIN_Y, IkGui.getDrawData().displayPosition.y, DELTA);
        assertTrue(IkGui.getDrawData().drawLists.contains(window.drawList));
    }

    @Test
    void testWindowOutsideMainViewportGetsItsOwnViewport() {
        final Runnable ui = () -> window("Outside", 2000, 300, null);
        frames(3, ui);

        final Window window = find("Outside");
        final Viewport viewport = window.viewport;
        assertNotSame(context.mainViewport, viewport);
        assertTrue(window.viewportOwned);
        assertSame(window, viewport.window);
        assertEquals(window.id, viewport.id);
        assertEquals(1, viewport.platformMonitor);

        // A platform window was created, shown, and placed at the window
        assertEquals(List.of("create Outside"), platform.events);
        final FakeViewportPlatform.FakeWindow fake = platform.get(viewport);
        assertNotNull(fake);
        assertTrue(fake.shown);
        assertEquals("Outside", fake.title);
        assertEquals(2000, fake.position.x, DELTA);
        assertEquals(300, fake.position.y, DELTA);
        assertEquals(300, fake.size.x, DELTA);
        assertEquals(200, fake.size.y, DELTA);

        // Secondary viewports default to having no decorations, and the window inside drops its
        // rounding to fill the platform window
        assertTrue((viewport.flags & ViewportFlags.NO_DECORATION) != 0);
        assertTrue((viewport.flags & ViewportFlags.NO_RENDERER_CLEAR) != 0);
        assertEquals(0.0f, window.rounding, DELTA);

        // The window is drawn into its own viewport, not the main one
        assertTrue(context.platformIO.viewports.contains(viewport));
        assertTrue(viewport.drawData.valid);
        assertTrue(viewport.drawData.drawLists.contains(window.drawList));
        assertFalse(context.mainViewport.drawData.drawLists.contains(window.drawList));
        assertEquals(2000, viewport.drawData.displayPosition.x, DELTA);
        assertEquals(300, viewport.drawData.displayPosition.y, DELTA);

        // Both share the same texture list
        assertSame(context.mainViewport.drawData.textures, viewport.drawData.textures);

        // The default render function renders and swaps the secondary viewport
        assertTrue(platform.renderCount > 0);
        assertEquals(platform.renderCount, platform.swapCount);
    }

    @Test
    void testDraggingWindowOutAndBack() {
        final Runnable ui = () -> window("Drag", MAIN_X + 50, MAIN_Y + 50, null);
        frames(3, ui);
        final Window window = find("Drag");
        assertSame(context.mainViewport, window.viewport);

        // Drag by the title bar out of the main viewport, onto empty desktop space
        final float grabX = MAIN_X + 60;
        final float grabY = MAIN_Y + 55;
        drag(grabX, grabY, grabX + 1300, grabY + 200, ui);
        assertNotSame(context.mainViewport, window.viewport);
        assertTrue(window.viewportOwned);
        final Viewport viewport = window.viewport;
        assertTrue(viewport.platformWindowCreated);
        assertEquals(window.position.x, platform.get(viewport).position.x, DELTA);
        assertEquals(window.position.y, platform.get(viewport).position.y, DELTA);
        // The moving window doesn't take inputs once released
        assertEquals(0, viewport.flags & ViewportFlags.NO_INPUTS);

        // Drag it back inside the main viewport, which merges it back
        drag(grabX + 1300, grabY + 200, grabX + 100, grabY + 100, ui);
        assertSame(context.mainViewport, window.viewport);
        assertFalse(window.viewportOwned);
        frames(3, ui);
        assertFalse(context.viewports.contains(viewport));
        assertEquals(List.of("create Drag", "destroy Drag"), platform.events);
        assertTrue(context.mainViewport.drawData.drawLists.contains(window.drawList));
    }

    @Test
    void testPlatformMoveAndResize() {
        final Runnable ui = () -> window("Moved", 2000, 300, null);
        frames(3, ui);
        final Window window = find("Moved");
        final Viewport viewport = window.viewport;

        // The OS moved and resized the platform window
        platform.get(viewport).position.set(2100, 400);
        platform.get(viewport).size.set(500, 450);
        viewport.platformRequestMove = true;
        viewport.platformRequestResize = true;
        frame(ui);

        assertEquals(2100, window.position.x, DELTA);
        assertEquals(400, window.position.y, DELTA);
        assertEquals(500, window.size.x, DELTA);
        assertEquals(450, window.size.y, DELTA);
        // The requests are cleared once handled
        assertFalse(viewport.platformRequestMove);
        assertFalse(viewport.platformRequestResize);
    }

    @Test
    void testPlatformCloseRequest() {
        final IkBoolean open = new IkBoolean(true);
        final Runnable ui = () -> window("Closable", 2000, 300, open);
        frames(3, ui);
        final Viewport viewport = find("Closable").viewport;
        assertNotSame(context.mainViewport, viewport);

        // e.g. Alt+F4 on the platform window
        viewport.platformRequestClose = true;
        frames(4, ui);
        assertFalse(open.get());
        assertFalse(context.viewports.contains(viewport));
        assertTrue(platform.events.contains("destroy Closable"));
    }

    @Test
    void testPlatformFocusFocusesWindow() {
        final Runnable ui =
                () -> {
                    window("Inside", MAIN_X + 50, MAIN_Y + 50, null);
                    window("Outside", 2000, 300, null);
                };
        frames(3, ui);
        final Window inside = find("Inside");
        final Window outside = find("Outside");
        IkGuiInternal.focusWindow(inside, 0);
        platform.mainWindow.focused = true;
        frames(2, ui);
        assertSame(inside, context.navFocusedWindow);

        // The user clicked the platform window (e.g. its taskbar entry), which focuses our window
        platform.mainWindow.focused = false;
        platform.get(outside.viewport).focused = true;
        frames(2, ui);
        assertSame(outside, context.navFocusedWindow);
        assertTrue((outside.viewport.flags & ViewportFlags.IS_FOCUSED) != 0);
        assertTrue(
                outside.viewport.lastFocusedStampCount
                        > context.mainViewport.lastFocusedStampCount);
    }

    @Test
    void testMouseOnlyHoversWindowsInMouseViewport() {
        context.io.backendFlags |= BackendFlags.HAS_MOUSE_HOVERED_VIEWPORT;
        final Runnable ui =
                () -> {
                    window("Inside", MAIN_X + 50, MAIN_Y + 50, null);
                    window("Outside", 2000, 300, null);
                };
        frames(3, ui);
        final Window outside = find("Outside");

        moveMouse(2050, 350);
        context.io.addMouseViewportEvent(outside.viewport.id);
        frames(2, ui);
        assertSame(outside.viewport, context.mouseViewport);
        assertSame(outside, context.windowHovered);

        // If the backend says the mouse is over the main viewport (e.g. another OS window is in
        // front of ours), our window isn't hovered
        context.io.addMouseViewportEvent(Viewport.DEFAULT_ID);
        frames(2, ui);
        assertSame(context.mainViewport, context.mouseViewport);
        assertNull(context.windowHovered);
    }

    @Test
    void testForegroundDrawListsArePerViewport() {
        final DrawList[] lists = new DrawList[2];
        final Runnable ui =
                () -> {
                    IkGui.setNextWindowPos(2000, 300, Condition.FIRST_USE_EVER);
                    IkGui.setNextWindowSize(300, 200, Condition.FIRST_USE_EVER);
                    IkGui.begin("Outside");
                    // Without a viewport, this uses the viewport of the current window
                    lists[0] = IkGui.getForegroundDrawList();
                    lists[0].addRectFilled(2010, 310, 2020, 320, 0xFF0000FF);
                    IkGui.end();
                    lists[1] = IkGui.getForegroundDrawList(IkGui.getMainViewport());
                    lists[1].addRectFilled(MAIN_X, MAIN_Y, MAIN_X + 10, MAIN_Y + 10, 0xFF0000FF);
                };
        frames(3, ui);
        final Viewport viewport = find("Outside").viewport;
        assertNotSame(lists[0], lists[1]);
        assertSame(viewport.foregroundDrawList, lists[0]);
        assertSame(lists[0], viewport.drawData.drawLists.getLast());
        assertSame(lists[1], context.mainViewport.drawData.drawLists.getLast());
        // The background draw lists weren't used, so they aren't rendered
        assertNull(viewport.backgroundDrawList);
    }

    @Test
    void testSettingsRememberViewport() {
        final Runnable ui = () -> window("Saved", 2000, 300, null);
        frames(3, ui);
        final int viewportID = find("Saved").viewportID;

        // Positions in the settings are relative to the viewport position, so the window at the
        // origin of its viewport doesn't need a position
        final String ini = IkGui.saveIniSettingsToMemory();
        assertTrue(
                ini.contains(
                        String.format(
                                "[Window][Saved]\nViewportPos=2000,300\nViewportId=0x%08X\n"
                                        + "Size=300,200\n",
                                viewportID)),
                ini);
        // Windows in the main viewport are relative to the main viewport
        assertTrue(ini.contains("[Window][Debug##Default]\nPos=60,60\n"), ini);

        // Loading them into a new context restores the viewport
        IkGui.destroyPlatformWindows();
        IkGui.destroyContext();
        setUp();
        IkGui.loadIniSettingsFromMemory(ini);
        frames(
                3,
                () -> {
                    IkGui.begin("Saved");
                    IkGui.end();
                });
        final Window window = find("Saved");
        assertEquals(2000, window.position.x, DELTA);
        assertEquals(300, window.position.y, DELTA);
        assertTrue(window.viewportOwned);
        assertEquals(viewportID, window.viewport.id);
    }

    @Test
    void testPopupCanExtendOutsideMainViewport() {
        final boolean[] open = {false};
        final Runnable ui =
                () -> {
                    IkGui.setNextWindowPos(MAIN_X + 1000, MAIN_Y + 50, Condition.FIRST_USE_EVER);
                    IkGui.setNextWindowSize(270, 200, Condition.FIRST_USE_EVER);
                    IkGui.begin("Host");
                    if (open[0]) {
                        IkGui.openPopup("Popup");
                        open[0] = false;
                    }
                    IkGui.setNextWindowSize(200, 100, Condition.ALWAYS);
                    if (IkGui.beginPopup("Popup")) {
                        IkGui.text("Popup contents");
                        IkGui.endPopup();
                    }
                    IkGui.end();
                };
        frames(3, ui);

        // Open the popup near the right edge of the main viewport, so it extends past it
        moveMouse(MAIN_X + 1250, MAIN_Y + 100);
        frame(ui);
        open[0] = true;
        frames(4, ui);
        assertEquals(1, context.openPopupStack.size());
        final Window popup = context.openPopupStack.getFirst().window;
        assertNotNull(popup);
        assertTrue(popup.active);
        // Popups are allowed to extend onto the monitor, so they don't get pushed back inside
        assertTrue(popup.position.x + popup.size.x > MAIN_X + 1280);
        assertTrue(popup.viewportOwned);
        assertTrue((popup.viewport.flags & ViewportFlags.NO_FOCUS_ON_APPEARING) != 0);
        assertTrue((popup.viewport.flags & ViewportFlags.NO_TASK_BAR_ICON) != 0);
        assertSame(find("Host").viewport, popup.viewport.parentViewport);
    }

    @Test
    void testTogglingViewportsTranslatesWindows() {
        context.io.configFlags &= ~ConfigFlags.VIEWPORTS_ENABLE;
        final Runnable ui = () -> window("Toggle", 50, 50, null);
        frames(3, ui);
        final Window window = find("Toggle");
        assertEquals(50, window.position.x, DELTA);
        assertEquals(0, context.mainViewport.position.x, DELTA);

        // When enabling viewports, the main viewport moves to the platform window position, and
        // windows move with it so they stay in the same place on the screen
        context.io.configFlags |= ConfigFlags.VIEWPORTS_ENABLE;
        frames(2, ui);
        assertEquals(MAIN_X, context.mainViewport.position.x, DELTA);
        assertEquals(MAIN_X + 50, window.position.x, DELTA);
        assertEquals(MAIN_Y + 50, window.position.y, DELTA);
        assertSame(context.mainViewport, window.viewport);

        // Moving the main platform window moves the windows inside it
        platform.mainWindow.position.set(MAIN_X + 10, MAIN_Y + 20);
        frames(2, ui);
        assertEquals(MAIN_X + 60, window.position.x, DELTA);
        assertEquals(MAIN_Y + 70, window.position.y, DELTA);
    }

    @Test
    void testDestroyPlatformWindows() {
        final Runnable ui =
                () -> {
                    window("First", 2000, 300, null);
                    window("Second", 2400, 300, null);
                };
        frames(3, ui);
        assertEquals(3, context.platformIO.viewports.size());
        platform.events.clear();

        IkGui.destroyPlatformWindows();
        // Every viewport gets a destroy call, including the main viewport
        assertEquals(3, platform.events.size());
        for (Viewport viewport : context.viewports) {
            assertNull(viewport.platformUserData);
            assertNull(viewport.platformHandle);
        }
        // The main viewport is always considered created, as the application owns it
        assertTrue(context.mainViewport.platformWindowCreated);
    }

    @Test
    void testDockingIntoMainViewportMergesViewport() {
        context.io.configFlags |= ConfigFlags.DOCKING_ENABLE;
        final Runnable ui =
                () -> {
                    IkGui.dockSpaceOverViewport();
                    window("Floating", 2000, 300, null);
                };
        frames(3, ui);
        final Window window = find("Floating");
        final Viewport viewport = window.viewport;
        assertTrue(window.viewportOwned);

        // Drag the window by its title bar into the middle of the dockspace
        drag(2050, 305, MAIN_X + 640, MAIN_Y + 360, ui);
        frames(3, ui);
        assertTrue(window.dockIsActive);
        assertSame(context.mainViewport, window.viewport);
        assertFalse(context.viewports.contains(viewport));
        assertTrue(platform.events.contains("destroy Floating"));
    }
}
