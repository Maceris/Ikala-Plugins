package com.ikalagaming.graphics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.TextureInfo;
import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.IkBoolean;
import com.ikalagaming.graphics.gui.data.TableColumnSortSpecs;
import com.ikalagaming.graphics.gui.data.TableSortSpecs;
import com.ikalagaming.graphics.gui.data.Window;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.enums.SortDirection;
import com.ikalagaming.graphics.gui.flags.ConfigFlags;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Tests for the example apps of the demo. */
class IkGuiDemoExamplesTest {
    private Context context;

    /** Every example app's visibility flag. */
    private static final IkBoolean[] ALL_APPS = {
        IkGuiDemoExamples.showMainMenuBar,
        IkGuiDemoExamples.showAppAssetsBrowser,
        IkGuiDemoExamples.showAppConsole,
        IkGuiDemoExamples.showAppCustomRendering,
        IkGuiDemoExamples.showAppDocuments,
        IkGuiDemoExamples.showAppDockSpace,
        IkGuiDemoExamples.showAppImageViewer,
        IkGuiDemoExamples.showAppLog,
        IkGuiDemoExamples.showAppPropertyEditor,
        IkGuiDemoExamples.showAppLayout,
        IkGuiDemoExamples.showAppSimpleOverlay,
        IkGuiDemoExamples.showAppAutoResize,
        IkGuiDemoExamples.showAppConstrainedResize,
        IkGuiDemoExamples.showAppFullscreen,
        IkGuiDemoExamples.showAppLongText,
        IkGuiDemoExamples.showAppWindowTitles
    };

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        // Don't read or write an .ini file
        context.io.iniFilename = null;
        context.io.displaySize.set(1600, 1200);
        context.io.mouseInsideWindow = true;
        // A stand-in for the texture the rendering backend sets, for the image viewer
        context.io.fonts.texture = new TextureInfo() {};
    }

    @AfterEach
    void tearDown() {
        for (IkBoolean app : ALL_APPS) {
            app.set(false);
        }
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

    private boolean logHasErrors() {
        return context.debugLogBuffer.snapshot().stream()
                .anyMatch(line -> line.contains("[ikgui-error]"));
    }

    @Test
    void testEveryExampleRenders() {
        for (IkBoolean app : ALL_APPS) {
            app.set(true);
        }
        frames(4, () -> IkGuiDemo.showDemoWindow(new IkBoolean(true)));
        assertFalse(logHasErrors(), context.debugLogBuffer.getText());
        assertTrue(context.windowStack.isEmpty());

        final Window console = IkGuiInternal.findWindowByName("Example: Console");
        assertNotNull(console);
        assertTrue(console.active);
        assertNotNull(IkGuiInternal.findWindowByName("Example: Assets Browser"));
        assertNotNull(IkGuiInternal.findWindowByName("Same title as another window##2"));
    }

    @Test
    void testEveryExampleRendersWithDocking() {
        context.io.configFlags |= ConfigFlags.DOCKING_ENABLE;
        for (IkBoolean app : ALL_APPS) {
            app.set(true);
        }
        frames(4, () -> IkGuiDemo.showDemoWindow(new IkBoolean(true)));
        assertFalse(logHasErrors(), context.debugLogBuffer.getText());
        assertTrue(context.windowStack.isEmpty());
    }

    @Test
    void testExamplesWithEverythingOpen() {
        IkGuiDemoExamples.showAppPropertyEditor.set(true);
        IkGuiDemoExamples.showAppCustomRendering.set(true);
        // Open every tree node in the property editor
        final Runnable ui =
                () -> {
                    IkGuiDemoExamples.showExampleApps();
                    // The tree is in a child window, which has its own storage
                    for (Window window : context.windowDisplayOrder) {
                        if (!window.name.startsWith("Example: Property editor")) {
                            continue;
                        }
                        for (IkGuiDemo.ExampleTreeNode node : IkGuiDemo.getDemoTree().children) {
                            window.stateStorage.setInt(node.uid, 1);
                        }
                    }
                };
        frames(4, ui);
        assertFalse(logHasErrors(), context.debugLogBuffer.getText());
        assertTrue(context.windowStack.isEmpty());
    }

    @Test
    void testConsoleCommands() {
        final IkGuiDemoExamples.ExampleAppConsole console =
                new IkGuiDemoExamples.ExampleAppConsole();
        final int before = console.items.size();
        console.execCommand("help");
        // The command, the header, and the 4 commands
        assertEquals(before + 6, console.items.size());
        assertEquals("# help\n", console.items.get(before));

        console.execCommand("history");
        console.execCommand("HELP");
        // Repeated commands move to the end of the history, ignoring case
        assertEquals(List.of("history", "HELP"), console.history);

        console.execCommand("clear");
        assertTrue(console.items.isEmpty());
        console.execCommand("nope");
        assertEquals("Unknown command: 'nope'\n", console.items.getLast());
    }

    @Test
    void testLogLineOffsets() {
        final IkGuiDemoExamples.ExampleAppLog log = new IkGuiDemoExamples.ExampleAppLog();
        log.addLog("one\ntwo\n");
        log.addLog("three %d\n", 3);
        assertEquals(List.of(0, 4, 8, 16), log.lineOffsets);
        log.clear();
        assertEquals(List.of(0), log.lineOffsets);
        assertEquals(0, log.buffer.length());
    }

    @Test
    void testSizeConstraintCallback() {
        final Runnable ui =
                () -> {
                    IkGui.setNextWindowSize(400, 100, Condition.ALWAYS);
                    // Keep a 2:1 aspect ratio
                    IkGui.setNextWindowSizeConstraints(
                            0,
                            0,
                            Float.MAX_VALUE,
                            Float.MAX_VALUE,
                            data -> data.desiredSize.y = data.desiredSize.x / 2);
                    IkGui.begin("Constrained");
                    IkGui.end();
                };
        frames(3, ui);
        final Window window = IkGuiInternal.findWindowByName("Constrained");
        assertEquals(400, window.size.x, 0.001f);
        assertEquals(200, window.size.y, 0.001f);
    }

    private static TableSortSpecs specs(TableColumnSortSpecs... columns) {
        final TableSortSpecs specs = new TableSortSpecs();
        specs.specs = columns;
        specs.specsCount = columns.length;
        return specs;
    }

    private static TableColumnSortSpecs spec(int columnIndex, SortDirection direction) {
        final TableColumnSortSpecs spec = new TableColumnSortSpecs();
        spec.columnIndex = columnIndex;
        spec.sortDirection = direction;
        return spec;
    }

    @Test
    void testAssetsSort() {
        final List<IkGuiDemoExamples.ExampleAsset> items = new ArrayList<>();
        items.add(new IkGuiDemoExamples.ExampleAsset(0, 1));
        items.add(new IkGuiDemoExamples.ExampleAsset(1, 0));
        items.add(new IkGuiDemoExamples.ExampleAsset(2, 1));
        items.add(new IkGuiDemoExamples.ExampleAsset(3, 0));

        // Type descending, then by ID
        IkGuiDemoExamples.ExampleAsset.sortWithSortSpecs(
                specs(spec(1, SortDirection.DESCENDING)), items);
        assertEquals(List.of(0, 2, 1, 3), items.stream().map(item -> item.id()).toList());

        // Index descending
        IkGuiDemoExamples.ExampleAsset.sortWithSortSpecs(
                specs(spec(0, SortDirection.DESCENDING)), items);
        assertEquals(List.of(3, 2, 1, 0), items.stream().map(item -> item.id()).toList());

        // No specs sorts by ID
        IkGuiDemoExamples.ExampleAsset.sortWithSortSpecs(specs(), items);
        assertEquals(List.of(0, 1, 2, 3), items.stream().map(item -> item.id()).toList());
    }

    @Test
    void testDocumentsCloseQueue() {
        final IkBoolean open = new IkBoolean(true);
        IkGuiDemoExamples.showAppDocuments.set(true);
        final Runnable ui = () -> IkGuiDemoExamples.showExampleAppDocuments(open);
        frames(3, ui);

        // A clean document closes right away, a dirty one asks first
        final IkGuiDemoExamples.ExampleAppDocuments app = IkGuiDemoExamples.documentsApp;
        final IkGuiDemoExamples.MyDocument lettuce = app.documents.get(0);
        final IkGuiDemoExamples.MyDocument eggplant = app.documents.get(1);
        eggplant.dirty = true;
        app.closeQueue.add(lettuce);
        frame(ui);
        assertFalse(lettuce.open.get());
        assertTrue(app.closeQueue.isEmpty());

        app.closeQueue.add(eggplant);
        frames(2, ui);
        // The document stays open and queued while the "Save?" popup asks
        assertTrue(eggplant.open.get());
        assertEquals(List.of(eggplant), app.closeQueue);
        assertTrue(
                context.openPopupStack.stream()
                        .anyMatch(
                                popup ->
                                        popup.window != null
                                                && popup.window.name.startsWith("Save?")));
        // Clean up for other tests
        app.closeQueue.clear();
        lettuce.doOpen();
        eggplant.dirty = false;
    }
}
