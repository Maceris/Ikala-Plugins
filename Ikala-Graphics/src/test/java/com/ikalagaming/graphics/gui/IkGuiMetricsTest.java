package com.ikalagaming.graphics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.DebugItemPathQuery;
import com.ikalagaming.graphics.gui.data.DrawData;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.data.DrawTextures;
import com.ikalagaming.graphics.gui.data.IkBoolean;
import com.ikalagaming.graphics.gui.data.Table;
import com.ikalagaming.graphics.gui.data.Window;
import com.ikalagaming.graphics.gui.enums.ColorType;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.flags.ConfigFlags;
import com.ikalagaming.graphics.gui.flags.DebugLogFlags;
import com.ikalagaming.graphics.gui.util.Color;
import com.ikalagaming.graphics.gui.util.Hash;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for the metrics/debugger window and the debug tools. These run headless. */
class IkGuiMetricsTest {

    /** The sections of the metrics window. */
    private static final String[] METRICS_SECTIONS = {
        "Tools",
        "Windows",
        "DrawLists",
        "Viewports",
        "Fonts",
        "Popups",
        "TabBars",
        "Tables",
        "InputText",
        "TypingSelect",
        "MultiSelect",
        "Docking",
        "Settings",
        "Memory",
        "Inputs",
        "Internal state"
    };

    private Context context;

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

    private Window find(String name) {
        final Window window = IkGuiInternal.findWindowByName(name);
        assertNotNull(window, "No window named " + name);
        return window;
    }

    /** A UI with a bit of everything, so the inspectors have something to look at. */
    private static void busyUI(boolean[] openPopup) {
        final int dockspaceID = IkGui.dockSpaceOverViewport();
        IkGui.setNextWindowDockID(dockspaceID, Condition.FIRST_USE_EVER);
        IkGui.begin("Docked");
        IkGui.text("Docked contents");
        IkGui.end();

        IkGui.setNextWindowPos(50, 50, Condition.FIRST_USE_EVER);
        IkGui.setNextWindowSize(400, 300, Condition.FIRST_USE_EVER);
        IkGui.begin("Busy");
        IkGui.button("Button");
        if (IkGui.beginTabBar("Tabs")) {
            if (IkGui.beginTabItem("First")) {
                IkGui.text("First tab");
                IkGui.endTabItem();
            }
            if (IkGui.beginTabItem("Second")) {
                IkGui.endTabItem();
            }
            IkGui.endTabBar();
        }
        if (IkGui.beginTable("Table", 3)) {
            IkGui.tableSetupColumn("A");
            IkGui.tableSetupColumn("B");
            IkGui.tableSetupColumn("C");
            IkGui.tableHeadersRow();
            for (int row = 0; row < 3; ++row) {
                for (int column = 0; column < 3; ++column) {
                    IkGui.tableNextColumn();
                    IkGui.text(row + "," + column);
                }
            }
            IkGui.endTable();
        }
        IkGui.beginChild("Child", 0, 50);
        IkGui.text("Child contents");
        IkGui.endChild();
        if (openPopup[0]) {
            IkGui.openPopup("Popup");
            openPopup[0] = false;
        }
        if (IkGui.beginPopup("Popup")) {
            IkGui.text("Popup contents");
            IkGui.endPopup();
        }
        IkGui.end();
    }

    @Test
    void testMetricsWindowWithEverySectionOpen() {
        final IkBoolean open = new IkBoolean(true);
        final boolean[] openPopup = {false};
        final Runnable ui =
                () -> {
                    busyUI(openPopup);
                    IkGui.setNextWindowPos(500, 20, Condition.FIRST_USE_EVER);
                    IkGui.setNextWindowSize(700, 680, Condition.FIRST_USE_EVER);
                    IkGui.showMetricsWindow(open);
                };
        frames(3, ui);

        // Open every section of the metrics window, and turn on the overlays and tools
        final Window metrics = find("IkGui Metrics/Debugger");
        for (String section : METRICS_SECTIONS) {
            metrics.stateStorage.setInt(Hash.getID(section, metrics.id), 1);
        }
        final var cfg = context.debugMetricsConfig;
        cfg.showWindowsRects.set(true);
        cfg.showWindowsBeginOrder.set(true);
        cfg.showTablesRects.set(true);
        cfg.showTextEncodingViewer.set(true);
        cfg.textEncodingBuffer.set("héllo €");
        cfg.showDockingNodes.set(true);
        cfg.showDebugLog.set(true);
        cfg.showIDStackTool.set(true);
        context.io.configDebugIsDebuggerPresent = true;
        openPopup[0] = true;
        moveMouse(60, 80);
        frames(5, ui);

        assertTrue(open.get());
        assertTrue(metrics.active);
        // The tool windows were opened from the metrics window
        assertTrue(find("IkGui Debug Log").active);
        assertTrue(find("IkGui ID Stack Tool").active);
        // The overlays draw into the foreground draw list
        final DrawList foreground = context.mainViewport.foregroundDrawList;
        assertNotNull(foreground);
        assertTrue(foreground.getCommandCount() > 0);

        // Every table rect type renders
        for (int type = 0; type < 13; ++type) {
            cfg.showTablesRectsType.set(type);
            frame(ui);
        }
        // Every window rect type renders
        for (int type = 0; type < 8; ++type) {
            cfg.showWindowsRectsType.set(type);
            frame(ui);
        }
    }

    @Test
    void testInspectorsWhenOpen() {
        final boolean[] openPopup = {false};
        final Runnable busy = () -> busyUI(openPopup);
        frames(3, busy);
        final Window busyWindow = find("Busy");
        final Table table = IkGuiInternal.tableFindByID(busyWindow.getID("Table"));

        final Runnable ui =
                () -> {
                    busyUI(openPopup);
                    IkGui.setNextWindowPos(600, 20, Condition.FIRST_USE_EVER);
                    IkGui.setNextWindowSize(600, 680, Condition.FIRST_USE_EVER);
                    IkGui.begin("Inspectors");
                    IkGui.setNextItemOpen(true, Condition.ALWAYS);
                    IkGuiImplMetrics.debugNodeWindow(busyWindow, "Window");
                    IkGui.setNextItemOpen(true, Condition.ALWAYS);
                    IkGuiImplMetrics.debugNodeDrawList(
                            busyWindow, busyWindow.viewport, busyWindow.drawList, "DrawList");
                    if (table != null) {
                        IkGui.setNextItemOpen(true, Condition.ALWAYS);
                        IkGuiImplMetrics.debugNodeTable(table);
                    }
                    for (var tabBar : context.tabBars.values()) {
                        IkGui.setNextItemOpen(true, Condition.ALWAYS);
                        IkGuiImplMetrics.debugNodeTabBar(tabBar, "TabBar");
                    }
                    for (var node : context.dockContext.nodes.values()) {
                        IkGui.setNextItemOpen(true, Condition.ALWAYS);
                        IkGuiImplMetrics.debugNodeDockNode(node, "Node");
                    }
                    IkGui.setNextItemOpen(true, Condition.ALWAYS);
                    IkGuiImplMetrics.debugNodeViewport(context.mainViewport);
                    IkGui.setNextItemOpen(true, Condition.ALWAYS);
                    IkGuiImplMetrics.debugNodeStorage(busyWindow.stateStorage, "Storage");
                    IkGuiImplMetrics.debugNodeInputTextState(context.inputTextState);
                    IkGui.debugTextEncoding("a€😀");
                    IkGuiImplMetrics.debugRenderKeyboardPreview(IkGui.getWindowDrawList());
                    IkGui.end();
                };
        frames(4, ui);
        assertNotNull(table);
        assertTrue(find("Inspectors").active);
    }

    @Test
    void testDrawListCountsWhileWritingAndAfterRendering() {
        final DrawList drawList = new DrawList("Test");
        drawList.addRectFilled(0, 0, 10, 10, Color.WHITE);
        drawList.addLine(0, 0, 10, 10, Color.WHITE);
        final int commands = drawList.getCommandCount();
        final int vertices = drawList.getVertexCount();
        final int points = drawList.getPointCount();
        final int details = drawList.getPointDetailCount();
        assertEquals(2, commands);
        assertEquals(commands * 6, vertices);

        // The counts are the same once the buffers are flipped for rendering
        drawList.prepareForRender();
        assertEquals(commands, drawList.getCommandCount());
        assertEquals(vertices, drawList.getVertexCount());
        assertEquals(points, drawList.getPointCount());
        assertEquals(details, drawList.getPointDetailCount());

        drawList.clear();
        assertEquals(0, drawList.getCommandCount());
        assertEquals(0, drawList.getVertexCount());
    }

    @Test
    void testDebugLogRecordsEnabledEvents() {
        final boolean[] openPopup = {false};
        final Runnable ui =
                () -> {
                    IkGui.begin("Log");
                    if (openPopup[0]) {
                        IkGui.openPopup("Popup");
                        openPopup[0] = false;
                    }
                    if (IkGui.beginPopup("Popup")) {
                        IkGui.endPopup();
                    }
                    IkGui.end();
                };
        frames(2, ui);

        // Popup events aren't logged by default
        openPopup[0] = true;
        frames(2, ui);
        assertFalse(
                context.debugLogBuffer.snapshot().stream()
                        .anyMatch(line -> line.contains("[popup]")));

        context.debugLogFlags |= DebugLogFlags.EVENT_POPUP;
        IkGuiImplPopups.closePopupToLevel(0, false);
        openPopup[0] = true;
        frames(2, ui);
        final int popupID = find("Log").getID("Popup");
        assertTrue(
                context.debugLogBuffer.snapshot().stream()
                        .anyMatch(
                                line ->
                                        line.contains(
                                                String.format(
                                                        "[popup] OpenPopup(\"Popup\" -> 0x%08X)",
                                                        popupID))),
                context.debugLogBuffer.getText());
        // Lines start with the frame number
        assertTrue(context.debugLogBuffer.get(0).matches("\\[\\d{5}] .*"));

        // The window shows the log
        frames(2, () -> IkGui.showDebugLogWindow());
        assertTrue(find("IkGui Debug Log").active);
    }

    @Test
    void testDebugLogAutoDisable() {
        context.debugLogFlags |= DebugLogFlags.EVENT_NAV;
        context.debugLogAutoDisableFlags = DebugLogFlags.EVENT_NAV;
        context.debugLogAutoDisableFrames = 2;
        frames(2, () -> {});
        assertEquals(0, context.debugLogFlags & DebugLogFlags.EVENT_NAV);
        assertEquals(DebugLogFlags.NONE, context.debugLogAutoDisableFlags);
    }

    @Test
    void testErrorsAreLoggedOrCounted() {
        // Errors are logged by default
        frame(() -> IkGui.begin("Forgot to end"));
        assertTrue(
                context.debugLogBuffer.snapshot().stream()
                        .anyMatch(line -> line.contains("[ikgui-error]") && line.contains("end()")),
                context.debugLogBuffer.getText());

        // When error events are off, they are counted as skipped
        context.debugLogBuffer.clear();
        context.debugLogFlags &= ~DebugLogFlags.EVENT_ERROR;
        final int skipped = context.debugLogBuffer.getSkippedErrors();
        frame(() -> IkGui.begin("Forgot to end"));
        assertTrue(context.debugLogBuffer.isEmpty());
        assertTrue(context.debugLogBuffer.getSkippedErrors() > skipped);
    }

    @Test
    void testErrorsFromDataClassesReachTheDebugLog() {
        // Data classes report through the public wrapper
        frame(() -> context.mainViewport.drawData.getDrawListCommandCount(99));
        assertTrue(
                context.debugLogBuffer.snapshot().stream()
                        .anyMatch(
                                line ->
                                        line.contains("[ikgui-error]")
                                                && line.contains(
                                                        "Index 99 out of bounds in getDrawList")),
                context.debugLogBuffer.getText());
    }

    @Test
    void testDrawListIndexEqualToCountIsOutOfBounds() {
        frame(() -> {});
        final DrawData drawData = context.mainViewport.drawData;
        final int count = drawData.getDrawListCount();
        // One past the last draw list is reported, rather than throwing
        assertEquals(0, drawData.getDrawListCommandCount(count));
        assertTrue(
                context.debugLogBuffer.snapshot().stream()
                        .anyMatch(
                                line ->
                                        line.contains(
                                                "Index "
                                                        + count
                                                        + " out of bounds in getDrawList")),
                context.debugLogBuffer.getText());
    }

    @Test
    void testErrorsFromOtherThreadsAreLoggedSafely() throws InterruptedException {
        frame(() -> {});
        final int threadCount = 4;
        final int errorsPerThread = 200;
        final Thread[] threads = new Thread[threadCount];
        for (int t = 0; t < threadCount; ++t) {
            // Each thread has its own draw data, so only the log is shared
            final DrawData drawData = new DrawData(new DrawTextures());
            threads[t] =
                    new Thread(
                            () -> {
                                for (int i = 0; i < errorsPerThread; ++i) {
                                    drawData.getDrawListCommandCount(99);
                                }
                            },
                            "LogWorker" + t);
            threads[t].start();
        }
        // Keep drawing the log while it's being written to
        while (java.util.Arrays.stream(threads).anyMatch(Thread::isAlive)) {
            frame(() -> IkGui.showDebugLogWindow());
        }
        for (Thread thread : threads) {
            thread.join();
        }

        final var lines = context.debugLogBuffer.snapshot();
        assertEquals(
                threadCount * errorsPerThread,
                lines.stream().filter(line -> line.contains("On thread 'LogWorker")).count());
        assertTrue(lines.stream().noneMatch(line -> line.contains("In window")));
    }

    @Test
    void testIDStackToolShowsPath() {
        final Vector2f buttonMin = new Vector2f();
        final Runnable ui =
                () -> {
                    IkGui.setNextWindowPos(50, 50, Condition.FIRST_USE_EVER);
                    IkGui.setNextWindowSize(300, 200, Condition.FIRST_USE_EVER);
                    IkGui.begin("Window");
                    IkGui.pushID("Group");
                    IkGui.pushID(5);
                    IkGui.button("Button");
                    IkGui.getItemRectMin(buttonMin);
                    IkGui.popID();
                    IkGui.popID();
                    IkGui.end();
                    IkGui.setNextWindowPos(600, 50, Condition.FIRST_USE_EVER);
                    IkGui.showIDStackToolWindow();
                };
        frames(3, ui);
        moveMouse(buttonMin.x + 3, buttonMin.y + 3);
        // One level of the stack is resolved each frame
        frames(12, ui);

        final DebugItemPathQuery query = context.debugItemPathQuery;
        assertTrue(query.complete);
        assertEquals(4, query.results.size());
        assertEquals(find("Window").id, query.results.get(0).id);
        assertEquals("Group", query.results.get(1).description);
        assertEquals("5", query.results.get(2).description);
        assertEquals("Button", query.results.get(3).description);
        assertEquals(context.hoveredIDPreviousFrame, query.mainID);
    }

    @Test
    void testItemPickerPicksHoveredItem() {
        final Vector2f buttonMin = new Vector2f();
        final int[] buttonID = new int[1];
        final Runnable ui =
                () -> {
                    IkGui.setNextWindowPos(50, 50, Condition.FIRST_USE_EVER);
                    IkGui.setNextWindowSize(300, 200, Condition.FIRST_USE_EVER);
                    IkGui.begin("Window");
                    IkGui.button("Button");
                    buttonID[0] = context.lastItemData.id;
                    IkGui.getItemRectMin(buttonMin);
                    IkGui.end();
                };
        frames(3, ui);
        IkGui.debugStartItemPicker();
        moveMouse(buttonMin.x + 3, buttonMin.y + 3);
        frames(2, ui);
        assertTrue(context.debugItemPickerActive.get());

        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        IkGui.newFrame();
        // Picking happens at the start of the frame, and the break happens when the item is
        // submitted
        assertFalse(context.debugItemPickerActive.get());
        assertEquals(buttonID[0], context.debugItemPickerBreakID);
        ui.run();
        IkGui.render();

        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frame(ui);
        assertEquals(0, context.debugItemPickerBreakID);
    }

    @Test
    void testLocateItemDrawsHighlight() {
        final int[] buttonID = new int[1];
        final Runnable ui =
                () -> {
                    IkGui.begin("Window");
                    IkGui.button("Button");
                    buttonID[0] = context.lastItemData.id;
                    IkGui.end();
                };
        frames(3, ui);
        IkGuiInternal.debugLocateItem(buttonID[0]);
        frame(ui);
        // The item was found, and we stop looking for it
        assertEquals(0, context.debugLocateID);
        assertTrue(context.mainViewport.foregroundDrawList.getCommandCount() >= 2);
    }

    @Test
    void testFlashStyleColorOverridesPushes() {
        final int buttonColor = IkGuiImplUtils.getColor(ColorType.BUTTON);
        frame(() -> IkGui.debugFlashStyleColor(ColorType.BUTTON));
        IkGui.newFrame();
        IkGui.pushStyleColor(ColorType.BUTTON, Color.rgba(1, 2, 3, 4));
        final int flashing = IkGuiImplUtils.getColor(ColorType.BUTTON);
        IkGui.popStyleColor();
        IkGui.render();
        assertNotEquals(buttonColor, flashing);
        assertNotEquals(Color.rgba(1, 2, 3, 4), flashing);
        assertEquals(0xFF, flashing & 0xFF);

        // After half a second it stops flashing
        context.frameStartTime -= 600;
        frames(2, () -> {});
        assertEquals(buttonColor, IkGuiImplUtils.getColor(ColorType.BUTTON));
    }

    @Test
    void testFramerateIsRollingAverage() {
        for (int i = 0; i < 10; ++i) {
            context.frameStartTime = System.currentTimeMillis() - 20;
            frame(() -> {});
        }
        // The very first frame has no previous frame, and timing isn't exact, so allow some slack
        assertEquals(50.0f, context.io.framerate, 5.0f);
    }

    @Test
    void testDebugBreakButtonsInWindowNode() {
        context.io.configDebugIsDebuggerPresent = true;
        final Runnable ui =
                () -> {
                    IkGui.begin("Target");
                    IkGui.end();
                    IkGui.begin("Inspector");
                    IkGui.setNextItemOpen(true, Condition.ALWAYS);
                    IkGuiImplMetrics.debugNodeWindow(find("Target"), "Window");
                    IkGui.end();
                };
        frames(3, ui);
        // The break button doesn't break by itself
        assertEquals(0, context.debugBreakInWindow);
        assertTrue(find("Inspector").active);

        // Requesting a break in the window is cleared by the metrics window after one cycle
        context.debugBreakInWindow = find("Target").id;
        frame(() -> IkGui.showMetricsWindow());
        assertEquals(0, context.debugBreakInWindow);
    }
}
