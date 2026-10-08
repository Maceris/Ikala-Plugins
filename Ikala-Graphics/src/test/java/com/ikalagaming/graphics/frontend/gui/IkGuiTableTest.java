package com.ikalagaming.graphics.frontend.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.frontend.gui.data.*;
import com.ikalagaming.graphics.frontend.gui.enums.ColorType;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.enums.SortDirection;
import com.ikalagaming.graphics.frontend.gui.enums.TableBackgroundTarget;
import com.ikalagaming.graphics.frontend.gui.flags.TableColumnFlags;
import com.ikalagaming.graphics.frontend.gui.flags.TableFlags;
import com.ikalagaming.graphics.frontend.gui.flags.TableRowFlags;
import com.ikalagaming.graphics.frontend.gui.util.Color;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tests for tables and the list clipper. These run headless, without any fonts loaded. */
class IkGuiTableTest {

    private static final float DELTA = 0.001f;

    private Context context;

    /** The table from the last frame, captured right after beginTable(). */
    private Table table;

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        context.io.displaySize.set(1280, 720);
        context.io.mouseInsideWindow = true;
        // Don't read or write an .ini file
        context.io.iniFilename = null;
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

    private void mouseButton(MouseButton button, boolean down) {
        context.io.addMouseButtonEvent(button, down);
    }

    private static void window(float width, float height, Runnable body) {
        IkGui.setNextWindowPos(50, 50, Condition.FIRST_USE_EVER);
        IkGui.setNextWindowSize(width, height, Condition.FIRST_USE_EVER);
        IkGui.begin("Tables");
        body.run();
        IkGui.end();
    }

    /**
     * Begin a table, capturing it so tests can inspect the layout.
     *
     * @return True if the table is visible.
     */
    private boolean beginTable(
            String name, int columns, int flags, float outerWidth, float outerHeight) {
        if (!IkGui.beginTable(name, columns, flags, outerWidth, outerHeight)) {
            return false;
        }
        table = IkGuiInternal.getCurrentTable();
        return true;
    }

    @Test
    void testCellsLayOutInRowsAndColumns() {
        final List<Vector2f> cells = new ArrayList<>();
        final float[] afterTableY = {0};
        final float[] tableBottom = {0};
        Runnable ui =
                () ->
                        window(
                                500,
                                400,
                                () -> {
                                    cells.clear();
                                    if (beginTable("Grid", 3, TableFlags.BORDERS, 0, 0)) {
                                        for (int row = 0; row < 2; ++row) {
                                            IkGui.tableNextRow();
                                            for (int column = 0; column < 3; ++column) {
                                                assertTrue(IkGui.tableSetColumnIndex(column));
                                                cells.add(IkGui.getCursorScreenPos());
                                                IkGui.text("Cell " + row + "," + column);
                                            }
                                        }
                                        assertEquals(3, IkGui.tableGetColumnCount());
                                        assertEquals(1, IkGui.tableGetRowIndex());
                                        assertEquals(2, IkGui.tableGetColumnIndex());
                                        IkGui.endTable();
                                        tableBottom[0] = table.outerRect.getBottom();
                                    }
                                    afterTableY[0] = IkGui.getCursorScreenPos().y;
                                    IkGui.text("After");
                                });
        frames(3, ui);

        assertEquals(6, cells.size());
        // Within a row, columns go left to right on the same line
        assertTrue(cells.get(0).x < cells.get(1).x);
        assertTrue(cells.get(1).x < cells.get(2).x);
        assertEquals(cells.get(0).y, cells.get(2).y, DELTA);
        // Rows go top to bottom, with the same column positions
        assertTrue(cells.get(3).y > cells.get(0).y);
        assertEquals(cells.get(0).x, cells.get(3).x, DELTA);
        // Items after the table go below it
        assertTrue(afterTableY[0] >= tableBottom[0]);
        assertNull(context.currentTable);
    }

    @Test
    void testStretchColumnsShareWidth() {
        Runnable ui =
                () ->
                        window(
                                500,
                                400,
                                () -> {
                                    if (beginTable(
                                            "Stretch", 3, TableFlags.SIZING_STRETCH_SAME, 0, 0)) {
                                        IkGui.tableNextRow();
                                        for (int column = 0; column < 3; ++column) {
                                            IkGui.tableNextColumn();
                                            IkGui.text("x");
                                        }
                                        IkGui.endTable();
                                    }
                                });
        frames(3, ui);
        final float width0 = table.columns[0].widthGiven;
        assertTrue(width0 > 100);
        assertEquals(width0, table.columns[1].widthGiven, 1.0f);
        assertEquals(width0, table.columns[2].widthGiven, 1.0f);
    }

    @Test
    void testFixedColumnWidth() {
        Runnable ui =
                () ->
                        window(
                                500,
                                400,
                                () -> {
                                    if (beginTable("Fixed", 2, TableFlags.SIZING_FIXED_FIT, 0, 0)) {
                                        IkGui.tableSetupColumn(
                                                "A", TableColumnFlags.WIDTH_FIXED, 100.0f);
                                        IkGui.tableSetupColumn("B");
                                        IkGui.tableNextRow();
                                        IkGui.tableNextColumn();
                                        IkGui.text("a");
                                        IkGui.tableNextColumn();
                                        IkGui.text("b");
                                        IkGui.endTable();
                                    }
                                });
        frames(3, ui);
        assertEquals(100, table.columns[0].widthGiven, DELTA);
        assertEquals("A", table.columns[0].name);
    }

    /**
     * Read the commands of a draw list, returning the first detail color of each command.
     *
     * @param drawList The draw list, after rendering.
     * @return The colors, in command order.
     */
    private static List<Integer> commandColors(DrawList drawList) {
        final List<Integer> colors = new ArrayList<>();
        final int count = drawList.commandBuffer.limit() / DrawData.SIZE_OF_DRAW_COMMAND;
        for (int i = 0; i < count; ++i) {
            final int detailIndex =
                    drawList.commandBuffer.getInt(i * DrawData.SIZE_OF_DRAW_COMMAND + 4);
            colors.add(
                    drawList.pointDetailBuffer.getInt(
                            detailIndex * DrawData.SIZE_OF_POINT_DETAIL + 8));
        }
        return colors;
    }

    @Test
    void testRowBackgroundIsDrawnBehindCellContents() {
        final int rowColor = Color.rgba(10, 20, 30, 255);
        final int buttonColor = Color.rgba(40, 50, 60, 255);
        Runnable ui =
                () ->
                        window(
                                500,
                                400,
                                () -> {
                                    if (beginTable("Background", 2, TableFlags.NONE, 0, 0)) {
                                        IkGui.tableNextRow();
                                        IkGui.tableNextColumn();
                                        IkGui.pushStyleColor(ColorType.BUTTON, buttonColor);
                                        IkGui.button("Button");
                                        IkGui.popStyleColor();
                                        // The row background is set after the contents, but must
                                        // be drawn behind them
                                        IkGui.tableSetBackgroundColor(
                                                TableBackgroundTarget.ROW_BACKGROUND_1, rowColor);
                                        IkGui.tableNextColumn();
                                        IkGui.text("Text");
                                        IkGui.endTable();
                                    }
                                });
        frames(3, ui);

        final List<Integer> colors =
                commandColors(IkGuiInternal.findWindowByName("Tables").drawList);
        final int rowIndex = colors.indexOf(rowColor);
        final int buttonIndex = colors.indexOf(buttonColor);
        assertTrue(rowIndex >= 0, "Row background was not drawn");
        assertTrue(buttonIndex >= 0, "Button was not drawn");
        assertTrue(rowIndex < buttonIndex, "Row background must be drawn first");
    }

    /** A sortable table with headers, used by several tests. */
    private Runnable sortableTable(int extraFlags) {
        return () ->
                window(
                        500,
                        400,
                        () -> {
                            if (beginTable(
                                    "Sortable",
                                    3,
                                    TableFlags.SORTABLE
                                            | TableFlags.SIZING_FIXED_FIT
                                            | TableFlags.BORDERS
                                            | extraFlags,
                                    0,
                                    0)) {
                                IkGui.tableSetupColumn(
                                        "Name", TableColumnFlags.WIDTH_FIXED, 100, 10);
                                IkGui.tableSetupColumn(
                                        "Size",
                                        TableColumnFlags.WIDTH_FIXED
                                                | TableColumnFlags.DEFAULT_SORT,
                                        100,
                                        11);
                                IkGui.tableSetupColumn(
                                        "Type", TableColumnFlags.WIDTH_FIXED, 100, 12);
                                IkGui.tableHeadersRow();
                                for (int row = 0; row < 3; ++row) {
                                    IkGui.tableNextRow();
                                    for (int column = 0; column < 3; ++column) {
                                        IkGui.tableNextColumn();
                                        IkGui.text("v" + row + column);
                                    }
                                }
                                IkGui.endTable();
                            }
                        });
    }

    /**
     * The center of a header cell of the current table.
     *
     * @param column The column index.
     * @return The center.
     */
    private Vector2f headerCenter(int column) {
        return new Vector2f(
                (table.columns[column].minX + table.columns[column].maxX) * 0.5f,
                table.workRect.getTop() + 5);
    }

    private void click(Vector2f position, Runnable ui) {
        moveMouse(position.x, position.y);
        frames(2, ui);
        mouseButton(MouseButton.LEFT, true);
        frames(2, ui);
        mouseButton(MouseButton.LEFT, false);
        frames(2, ui);
    }

    @Test
    void testSortSpecs() {
        final TableSortSpecs[] specs = {null};
        Runnable base = sortableTable(TableFlags.NONE);
        Runnable ui =
                () ->
                        window(
                                500,
                                400,
                                () -> {
                                    if (beginTable(
                                            "Sortable",
                                            3,
                                            TableFlags.SORTABLE | TableFlags.SIZING_FIXED_FIT,
                                            0,
                                            0)) {
                                        IkGui.tableSetupColumn(
                                                "Name", TableColumnFlags.WIDTH_FIXED, 100, 10);
                                        IkGui.tableSetupColumn(
                                                "Size",
                                                TableColumnFlags.WIDTH_FIXED
                                                        | TableColumnFlags.DEFAULT_SORT,
                                                100,
                                                11);
                                        IkGui.tableSetupColumn(
                                                "Type", TableColumnFlags.WIDTH_FIXED, 100, 12);
                                        IkGui.tableHeadersRow();
                                        specs[0] = IkGui.tableGetSortSpecs();
                                        IkGui.endTable();
                                    }
                                });
        frame(ui);
        assertNotNull(specs[0]);
        assertTrue(specs[0].specsDirty);
        assertEquals(1, specs[0].specsCount);
        assertEquals(1, specs[0].specs[0].columnIndex);
        assertEquals(11, specs[0].specs[0].columnUserID);
        assertEquals(SortDirection.ASCENDING, specs[0].specs[0].sortDirection);
        specs[0].specsDirty = false;
        frames(2, ui);
        assertFalse(specs[0].specsDirty);

        // Clicking the sorted header flips the direction
        click(headerCenter(1), ui);
        assertTrue(specs[0].specsDirty);
        assertEquals(SortDirection.DESCENDING, specs[0].specs[0].sortDirection);

        // Clicking another header sorts on that column instead
        click(headerCenter(0), ui);
        assertEquals(1, specs[0].specsCount);
        assertEquals(0, specs[0].specs[0].columnIndex);
        assertFalse(base == null);
    }

    @Test
    void testUnsortableTableHasNoSortSpecs() {
        final TableSortSpecs[] specs = {new TableSortSpecs()};
        frame(
                () ->
                        window(
                                500,
                                400,
                                () -> {
                                    if (beginTable("Plain", 2, TableFlags.NONE, 0, 0)) {
                                        specs[0] = IkGui.tableGetSortSpecs();
                                        IkGui.endTable();
                                    }
                                }));
        assertNull(specs[0]);
    }

    @Test
    void testHideColumn() {
        final boolean[] hide = {false};
        Runnable ui =
                () ->
                        window(
                                500,
                                400,
                                () -> {
                                    if (beginTable("Hideable", 3, TableFlags.HIDEABLE, 0, 0)) {
                                        if (hide[0]) {
                                            IkGui.tableSetColumnEnabled(1, false);
                                        }
                                        IkGui.tableNextRow();
                                        for (int column = 0; column < 3; ++column) {
                                            IkGui.tableNextColumn();
                                            IkGui.text("x");
                                        }
                                        IkGui.endTable();
                                    }
                                });
        frames(2, ui);
        assertTrue((table.columns[1].flags & TableColumnFlags.IS_ENABLED) != 0);
        hide[0] = true;
        frames(2, ui);
        assertFalse(table.columns[1].isEnabled);
        assertEquals(2, table.columnsEnabledCount);
        assertEquals(0, table.columns[1].flags & TableColumnFlags.IS_ENABLED);
    }

    @Test
    void testResizeColumnByDraggingBorder() {
        Runnable ui = sortableTable(TableFlags.RESIZABLE);
        frames(3, ui);
        final float startWidth = table.columns[0].widthGiven;
        final float borderX = table.columns[0].maxX;
        final float borderY = table.workRect.getTop() + 30;

        moveMouse(borderX, borderY);
        frames(2, ui);
        mouseButton(MouseButton.LEFT, true);
        frames(2, ui);
        moveMouse(borderX + 30, borderY);
        frames(3, ui);
        mouseButton(MouseButton.LEFT, false);
        frames(3, ui);

        assertEquals(startWidth + 30, table.columns[0].widthGiven, 1.0f);
    }

    @Test
    void testReorderByDraggingHeader() {
        Runnable ui = sortableTable(TableFlags.REORDERABLE);
        frames(3, ui);
        final Vector2f start = headerCenter(0);
        // Cross the right edge of the column once, crossing it again would move it again
        final float targetX = table.columns[0].maxX + 20;

        moveMouse(start.x, start.y);
        frames(2, ui);
        mouseButton(MouseButton.LEFT, true);
        frames(2, ui);
        // Move in steps so the mouse delta is positive
        for (float x = start.x + 10; x <= targetX; x += 10) {
            moveMouse(x, start.y);
            frames(2, ui);
        }
        mouseButton(MouseButton.LEFT, false);
        frames(3, ui);

        assertEquals(1, table.columns[0].displayOrder);
        assertEquals(0, table.columns[1].displayOrder);
        assertEquals(1, table.displayOrderToIndex[0]);
    }

    @Test
    void testContextMenuOpensOnRightClick() {
        Runnable ui = sortableTable(TableFlags.RESIZABLE | TableFlags.HIDEABLE);
        frames(3, ui);
        final Vector2f header = headerCenter(1);
        moveMouse(header.x, header.y);
        frames(2, ui);
        mouseButton(MouseButton.RIGHT, true);
        frames(2, ui);
        mouseButton(MouseButton.RIGHT, false);
        frames(3, ui);
        assertTrue(table.isContextPopupOpen);
        assertEquals(1, table.contextPopupColumn);
        assertFalse(context.openPopupStack.isEmpty());
    }

    @Test
    void testSettingsSaveAndLoad() {
        Runnable ui = sortableTable(TableFlags.RESIZABLE | TableFlags.REORDERABLE);
        frames(3, ui);
        String ini = IkGui.saveIniSettingsToMemory();
        final String header = String.format("[Table][0x%08X,3]", table.id);
        assertTrue(ini.contains(header), ini);
        assertTrue(ini.contains("Sort=0v"), ini);

        // Load a different width and order into a fresh context
        final int tableID = table.id;
        IkGui.destroyContext();
        setUp();
        IkGui.loadIniSettingsFromMemory(
                String.format(
                        "[Table][0x%08X,3]\n"
                                + "Column 0  Width=150 Order=1\n"
                                + "Column 1  Width=100 Order=0 Sort=0^\n"
                                + "Column 2  Width=100 Order=2\n",
                        tableID));
        frames(3, ui);
        assertEquals(150, table.columns[0].widthGiven, DELTA);
        assertEquals(1, table.columns[0].displayOrder);
        assertEquals(0, table.columns[1].displayOrder);
        assertEquals(SortDirection.DESCENDING, table.columns[1].sortDirection);
    }

    @Test
    void testSettingsSaveIgnoresDefaultLocale() {
        final Locale previous = Locale.getDefault();
        // German uses a comma as the decimal separator, which the loader can't parse
        Locale.setDefault(Locale.GERMANY);
        try {
            Runnable ui =
                    () ->
                            window(
                                    500,
                                    400,
                                    () -> {
                                        if (beginTable(
                                                "Stretch",
                                                2,
                                                TableFlags.RESIZABLE
                                                        | TableFlags.SIZING_STRETCH_PROP,
                                                0,
                                                0)) {
                                            IkGui.tableSetupColumn(
                                                    "A", TableColumnFlags.WIDTH_STRETCH, 1.5f);
                                            IkGui.tableSetupColumn(
                                                    "B", TableColumnFlags.WIDTH_STRETCH, 0.5f);
                                            IkGui.tableNextRow();
                                            IkGui.tableNextColumn();
                                            IkGui.text("a");
                                            IkGui.tableNextColumn();
                                            IkGui.text("b");
                                            IkGui.endTable();
                                        }
                                    });
            frames(3, ui);

            // Columns are only saved once they differ from the defaults, so load some weights
            final int tableID = table.id;
            IkGui.destroyContext();
            setUp();
            IkGui.loadIniSettingsFromMemory(
                    String.format(
                            Locale.ROOT,
                            "[Table][0x%08X,2]\n"
                                    + "Column 0  Weight=1.2500\n"
                                    + "Column 1  Weight=0.7500\n",
                            tableID));
            frames(3, ui);
            final String ini = IkGui.saveIniSettingsToMemory();
            assertTrue(ini.contains("Weight=1.2500"), ini);
            assertTrue(ini.contains("Weight=0.7500"), ini);
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void testScrollingTableWithClipperOnlySubmitsVisibleRows() {
        final int[] submitted = {0};
        final ListClipper clipper = new ListClipper();
        Runnable ui =
                () ->
                        window(
                                500,
                                400,
                                () -> {
                                    submitted[0] = 0;
                                    if (beginTable(
                                            "Scrolling",
                                            2,
                                            TableFlags.SCROLL_Y | TableFlags.ROW_BACKGROUND,
                                            0,
                                            200)) {
                                        IkGui.tableSetupScrollFreeze(0, 1);
                                        IkGui.tableSetupColumn("A");
                                        IkGui.tableSetupColumn("B");
                                        IkGui.tableHeadersRow();
                                        clipper.begin(1000);
                                        while (clipper.step()) {
                                            for (int row = clipper.displayStart;
                                                    row < clipper.displayEnd;
                                                    ++row) {
                                                submitted[0]++;
                                                IkGui.tableNextRow();
                                                IkGui.tableNextColumn();
                                                IkGui.text("Row " + row);
                                                IkGui.tableNextColumn();
                                                IkGui.text("Value");
                                            }
                                        }
                                        IkGui.endTable();
                                    }
                                });
        frames(3, ui);
        assertTrue(submitted[0] > 3, "Visible rows should be submitted");
        assertTrue(submitted[0] < 50, "Only visible rows should be submitted, got " + submitted[0]);
        assertNull(clipper.tempData);
        assertEquals(0, context.clipperTempDataStacked);
        // The inner window is scrollable over all the rows
        assertTrue(table.innerWindow.scrollMax.y > 1000);
    }

    @Test
    void testListClipperInWindow() {
        final List<Integer> displayed = new ArrayList<>();
        final float[] endY = {0};
        final float[] startY = {0};
        final float[] lineHeight = {0};
        Runnable ui =
                () ->
                        window(
                                300,
                                200,
                                () -> {
                                    displayed.clear();
                                    lineHeight[0] = IkGui.getTextLineHeightWithSpacing();
                                    startY[0] = IkGui.getCursorScreenPos().y;
                                    final ListClipper clipper = new ListClipper();
                                    clipper.begin(10_000);
                                    while (clipper.step()) {
                                        for (int i = clipper.displayStart;
                                                i < clipper.displayEnd;
                                                ++i) {
                                            displayed.add(i);
                                            IkGui.text("Line " + i);
                                        }
                                    }
                                    endY[0] = IkGui.getCursorScreenPos().y;
                                });
        frames(3, ui);
        assertTrue(displayed.contains(0));
        assertTrue(displayed.size() < 50, "Got " + displayed.size());
        assertFalse(displayed.contains(9999));
        // The cursor is advanced past all the items
        assertEquals(startY[0] + 10_000 * lineHeight[0], endY[0], 1.0f);
    }

    @Test
    void testListClipperIncludesRequestedItems() {
        final List<Integer> displayed = new ArrayList<>();
        Runnable ui =
                () ->
                        window(
                                300,
                                200,
                                () -> {
                                    displayed.clear();
                                    final ListClipper clipper = new ListClipper();
                                    clipper.begin(10_000);
                                    clipper.includeItemByIndex(5000);
                                    while (clipper.step()) {
                                        for (int i = clipper.displayStart;
                                                i < clipper.displayEnd;
                                                ++i) {
                                            displayed.add(i);
                                            IkGui.text("Line " + i);
                                        }
                                    }
                                });
        frames(3, ui);
        assertTrue(displayed.contains(5000));
        assertTrue(displayed.size() < 60, "Got " + displayed.size());
    }

    @Test
    void testAngledHeaders() {
        Runnable ui =
                () ->
                        window(
                                500,
                                400,
                                () -> {
                                    if (beginTable(
                                            "Angled",
                                            3,
                                            TableFlags.SIZING_FIXED_FIT | TableFlags.BORDERS,
                                            0,
                                            0)) {
                                        IkGui.tableSetupColumn("Name");
                                        IkGui.tableSetupColumn(
                                                "Angled One", TableColumnFlags.ANGLED_HEADER);
                                        IkGui.tableSetupColumn(
                                                "Angled Two", TableColumnFlags.ANGLED_HEADER);
                                        IkGui.tableAngledHeadersRow();
                                        IkGui.tableHeadersRow();
                                        IkGui.tableNextRow();
                                        IkGui.tableNextColumn();
                                        IkGui.text("Row");
                                        IkGui.endTable();
                                    }
                                });
        frames(3, ui);
        assertTrue(table.angledHeadersHeight > 0);
        assertEquals(2, table.angledHeadersCount);
        assertNull(context.currentTable);
    }

    @Test
    void testNestedTables() {
        final Table[] outer = {null};
        final Table[] inner = {null};
        final Table[] afterInner = {null};
        Runnable ui =
                () ->
                        window(
                                500,
                                400,
                                () -> {
                                    if (IkGui.beginTable("Outer", 2, TableFlags.BORDERS)) {
                                        outer[0] = IkGuiInternal.getCurrentTable();
                                        IkGui.tableNextRow();
                                        IkGui.tableNextColumn();
                                        if (IkGui.beginTable("Inner", 2, TableFlags.BORDERS)) {
                                            inner[0] = IkGuiInternal.getCurrentTable();
                                            IkGui.tableNextRow();
                                            IkGui.tableNextColumn();
                                            IkGui.text("Inner");
                                            IkGui.endTable();
                                        }
                                        afterInner[0] = IkGuiInternal.getCurrentTable();
                                        IkGui.tableNextColumn();
                                        IkGui.text("Outer");
                                        IkGui.endTable();
                                    }
                                });
        frames(3, ui);
        assertNotNull(inner[0]);
        assertTrue(inner[0] != outer[0]);
        assertTrue(afterInner[0] == outer[0], "The outer table is current again");
        assertNull(context.currentTable);
        assertEquals(0, context.tablesTempDataStacked);
    }

    @Test
    void testMissingEndTableIsRecovered() {
        Runnable ui =
                () -> {
                    IkGui.begin("Broken");
                    if (IkGui.beginTable("Unclosed", 2, TableFlags.SCROLL_Y, 0, 100)) {
                        IkGui.tableNextRow();
                        IkGui.tableNextColumn();
                        IkGui.text("x");
                        // Missing endTable()
                    }
                    IkGui.end();
                };
        frames(3, ui);
        assertNull(context.currentTable);
        assertEquals(0, context.tablesTempDataStacked);
    }

    @Test
    void testHeaderRowFlags() {
        final int[] rowFlags = {-1};
        frames(
                2,
                () ->
                        window(
                                500,
                                400,
                                () -> {
                                    if (beginTable("Headers", 2, TableFlags.NONE, 0, 0)) {
                                        IkGui.tableSetupColumn("First");
                                        IkGui.tableSetupColumn("Second");
                                        IkGui.tableHeadersRow();
                                        rowFlags[0] = table.rowFlags;
                                        assertEquals("Second", IkGui.tableGetColumnName(1));
                                        IkGui.endTable();
                                    }
                                }));
        assertEquals(TableRowFlags.HEADERS, rowFlags[0]);
        assertTrue(table.isUsingHeaders);
    }

    /** Load the test font from the test resources, and make it the current font. */
    private void loadTestFont() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/fonts/NotoSans.ttf")) {
            assertNotNull(stream, "Missing test font");
            assertTrue(context.io.fonts.loadFont("NotoSans", stream.readAllBytes()));
        }
        IkGui.setFont("NotoSans", 16);
    }

    /**
     * Read the rotation (cos, sin) of every text command in a draw list.
     *
     * @param drawList The draw list, after rendering.
     * @return The rotations.
     */
    private static List<Vector2f> textRotations(DrawList drawList) {
        final List<Vector2f> rotations = new ArrayList<>();
        final int count = drawList.commandBuffer.limit() / DrawData.SIZE_OF_DRAW_COMMAND;
        for (int i = 0; i < count; ++i) {
            final int offset = i * DrawData.SIZE_OF_DRAW_COMMAND;
            if (drawList.commandBuffer.getInt(offset + 16)
                    != DrawList.ElementType.TEXT.getTypeID()) {
                continue;
            }
            final int point = drawList.commandBuffer.getInt(offset) * DrawData.SIZE_OF_POINT;
            rotations.add(
                    new Vector2f(
                            drawList.pointBuffer.getFloat(point + 8),
                            drawList.pointBuffer.getFloat(point + 12)));
        }
        return rotations;
    }

    @Test
    void testAngledHeaderTextIsRotated() throws IOException {
        loadTestFont();
        Runnable ui =
                () ->
                        window(
                                500,
                                400,
                                () -> {
                                    if (beginTable(
                                            "Angled",
                                            2,
                                            TableFlags.SIZING_FIXED_FIT | TableFlags.BORDERS,
                                            0,
                                            0)) {
                                        IkGui.tableSetupColumn("Name");
                                        IkGui.tableSetupColumn(
                                                "Angled", TableColumnFlags.ANGLED_HEADER);
                                        IkGui.tableAngledHeadersRow();
                                        IkGui.tableHeadersRow();
                                        IkGui.tableNextRow();
                                        IkGui.tableNextColumn();
                                        IkGui.text("Row");
                                        IkGui.endTable();
                                    }
                                });
        frames(3, ui);

        final List<Vector2f> rotations =
                textRotations(IkGuiInternal.findWindowByName("Tables").drawList);
        final float angle =
                (float) Math.toRadians(context.style.variable.tableAngledHeadersAngle)
                        - (float) (Math.PI * 0.5);
        int rotated = 0;
        int upright = 0;
        for (Vector2f rotation : rotations) {
            if (Math.abs(rotation.x - 1) < DELTA && Math.abs(rotation.y) < DELTA) {
                upright++;
            } else {
                assertEquals(Math.cos(angle), rotation.x, DELTA);
                assertEquals(Math.sin(angle), rotation.y, DELTA);
                rotated++;
            }
        }
        // "Angled" is drawn rotated, the other text is upright
        assertEquals("Angled".length(), rotated);
        assertTrue(upright > 0);
    }

    @Test
    void testTableTextWithFont() throws IOException {
        loadTestFont();
        final float[] textWidth = {0};
        Runnable ui =
                () ->
                        window(
                                500,
                                400,
                                () -> {
                                    if (beginTable("Text", 2, TableFlags.SIZING_FIXED_FIT, 0, 0)) {
                                        IkGui.tableNextRow();
                                        IkGui.tableNextColumn();
                                        IkGui.text("Some text");
                                        textWidth[0] = IkGui.getItemRectSize().x;
                                        IkGui.tableNextColumn();
                                        IkGui.text("x");
                                        IkGui.endTable();
                                    }
                                });
        frames(4, ui);
        // Fixed fit columns size to their contents
        assertTrue(textWidth[0] > 0);
        assertEquals(textWidth[0], table.columns[0].widthGiven, 1.0f);
        assertFalse(textRotations(IkGuiInternal.findWindowByName("Tables").drawList).isEmpty());
    }
}
