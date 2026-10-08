package com.ikalagaming.graphics.frontend.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.frontend.gui.data.IkBoolean;
import com.ikalagaming.graphics.frontend.gui.data.IkInt;
import com.ikalagaming.graphics.frontend.gui.data.ListClipper;
import com.ikalagaming.graphics.frontend.gui.data.Table;
import com.ikalagaming.graphics.frontend.gui.data.TableColumn;
import com.ikalagaming.graphics.frontend.gui.data.TableSettings;
import com.ikalagaming.graphics.frontend.gui.data.TableSortSpecs;
import com.ikalagaming.graphics.frontend.gui.data.Window;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.Direction;
import com.ikalagaming.graphics.frontend.gui.enums.Key;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.enums.SortDirection;
import com.ikalagaming.graphics.frontend.gui.enums.StyleVariable;
import com.ikalagaming.graphics.frontend.gui.enums.TableBackgroundTarget;
import com.ikalagaming.graphics.frontend.gui.flags.ChildFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ItemStatusFlags;
import com.ikalagaming.graphics.frontend.gui.flags.KeyModFlags;
import com.ikalagaming.graphics.frontend.gui.flags.SelectableFlags;
import com.ikalagaming.graphics.frontend.gui.flags.TableColumnFlags;
import com.ikalagaming.graphics.frontend.gui.flags.TableFlags;
import com.ikalagaming.graphics.frontend.gui.flags.TableRowFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
import com.ikalagaming.graphics.frontend.gui.util.Color;
import com.ikalagaming.graphics.frontend.gui.util.Hash;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Table tests ported from the Dear ImGui test suite (imgui_tests_tables.cpp), which is MIT
 * licensed. Each test notes the name of the upstream test it is ported from.
 *
 * <p>Upstream discards each table's instance and settings on the first frame of many tests, so
 * other tests don't affect them. Every test here starts with a fresh context, so that's left out.
 */
class IkGuiSuiteTablesTest {
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

    private static final float EPSILON = 0.0001f;

    /** The state shared by many of the table tests, like upstream's TableTestingVars. */
    private static class TableTestingVars {
        int tableFlags = TableFlags.NONE;
        final Vector2f outerSize = new Vector2f(-Float.MIN_VALUE, 0.0f);
        int windowFlags = 0;
        final Vector2f windowSize = new Vector2f(0.0f, 0.0f);
        final Vector2f itemSize = new Vector2f(0.0f, 0.0f);
        int columnsCount = 3;
        final int[] columnFlags = new int[6];
        final float[] widths = new float[6];
        final RectFloat outTableItemRect = new RectFloat(0, 0, 0, 0);
        final Vector2f outTableLayoutSize = new Vector2f();
        final Vector2f outOuterCursorMaxPosOnEndTable = new Vector2f();
        final Vector2f outOuterIdealMaxPosOnEndTable = new Vector2f();
        boolean outTableIsItemHovered;
        int step = 0;
    }

    /** Record the table item and the outer window cursor, after endTable(). */
    private static void helperFillBounds(TableTestingVars vars) {
        final var outerWindow = IkGuiInternal.context.windowCurrent;
        vars.outTableIsItemHovered = IkGui.isItemHovered();
        final Vector2f min = IkGui.getItemRectMin();
        final Vector2f max = IkGui.getItemRectMax();
        vars.outTableItemRect.set(min.x, min.y, max.x, max.y);
        vars.outTableLayoutSize.set(max.x - min.x, max.y - min.y);
        vars.outOuterCursorMaxPosOnEndTable.set(outerWindow.cursorMaxPosition);
        vars.outOuterIdealMaxPosOnEndTable
                .set(outerWindow.cursorMaxPosition)
                .max(outerWindow.cursorIdealMaxPosition);
    }

    /** A cell callback for the table helpers. */
    private interface CellFunction {
        void submit(int column, int line);
    }

    /** Submit cells, calling a function for each visible cell. */
    private static void helperTableSubmitCellsCustom(int countW, int countH, CellFunction cell) {
        for (int line = 0; line < countH; ++line) {
            IkGui.tableNextRow();
            for (int column = 0; column < countW; ++column) {
                if (!IkGui.tableSetColumnIndex(column) && column > 0) {
                    continue;
                }
                cell.submit(column, line);
            }
        }
    }

    /** Submit cells with buttons filling the cell width. */
    private static void helperTableSubmitCellsButtonFill(int countW, int countH) {
        helperTableSubmitCellsCustom(
                countW,
                countH,
                (column, line) -> IkGui.button(line + "," + column, -Float.MIN_VALUE, 0.0f));
    }

    /** Submit cells with buttons of a fixed width. */
    private static void helperTableSubmitCellsButtonFix(int countW, int countH) {
        helperTableSubmitCellsCustom(
                countW, countH, (column, line) -> IkGui.button(line + "," + column, 100.0f, 0.0f));
    }

    /** Submit cells with text. */
    private static void helperTableSubmitCellsText(int countW, int countH) {
        helperTableSubmitCellsCustom(
                countW, countH, (column, line) -> IkGui.text(line + "," + column));
    }

    /**
     * Submit a table with column sizing policies from a description, like "WWW" or "FFW", where F
     * is fixed and W is stretch, and lower case letters are hidden by default. Upstream uses an
     * alternate font for the headers, here they use the default font.
     */
    private static void helperTableWithResizingPolicies(
            String tableID, int tableFlags, String columnsDesc) {
        final int columnsCount = columnsDesc.length();
        if (!IkGui.beginTable(tableID, columnsCount, tableFlags)) {
            return;
        }
        for (int column = 0; column < columnsCount; ++column) {
            final char policy = columnsDesc.charAt(column);
            int columnFlags = TableColumnFlags.NONE;
            if (policy >= 'a' && policy <= 'z') {
                columnFlags |= TableColumnFlags.DEFAULT_HIDE;
            }
            if (policy == 'f' || policy == 'F') {
                columnFlags |= TableColumnFlags.WIDTH_FIXED;
            } else if (policy == 'w' || policy == 'W') {
                columnFlags |= TableColumnFlags.WIDTH_STRETCH;
            } else {
                throw new IllegalArgumentException("Unknown policy " + policy);
            }
            IkGui.tableSetupColumn(String.valueOf(policy) + (column + 1), columnFlags);
        }
        IkGui.tableHeadersRow();
        for (int row = 0; row < 2; ++row) {
            IkGui.tableNextRow();
            for (int column = 0; column < columnsCount; ++column) {
                IkGui.tableSetColumnIndex(column);
                final char policy = columnsDesc.charAt(column);
                String columnDesc = "Unknown";
                if (policy == 'F' || policy == 'f') {
                    columnDesc = "Fixed";
                }
                if (policy == 'W' || policy == 'w') {
                    columnDesc = "Stretch";
                }
                IkGui.text(columnDesc + " " + row + "," + column);
            }
        }
        IkGui.endTable();
    }

    /** Find a table by reference. */
    private Table findTable(String ref) {
        return IkGuiImplTables.tableFindByID(ctx.getID(ref));
    }

    /** The ID of a column's resize border, like upstream's TableGetColumnResizeID(). */
    private static int resizeID(Table table, int column) {
        return IkGuiImplTables.tableGetColumnResizeID(table, column, 0);
    }

    /**
     * table_width_explicit: specifying a width in tableSetupColumn() or not, toggling
     * TableFlags.RESIZABLE, and fixed or auto widths.
     */
    @Test
    void testTableWidthExplicit() {
        final TableTestingVars vars = new TableTestingVars();
        final IkInt tableFlags = new IkInt(0);
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(800.0f, 0.0f, Condition.APPEARING);
                    IkGui.begin("Test window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    tableFlags.set(vars.tableFlags);
                    IkGui.checkboxFlags("TableFlags.RESIZABLE", tableFlags, TableFlags.RESIZABLE);
                    vars.tableFlags = tableFlags.get();
                    if (IkGui.beginTable(
                            "table1",
                            5,
                            vars.tableFlags | TableFlags.SIZING_FIXED_FIT | TableFlags.BORDERS_V)) {
                        final int cf = TableColumnFlags.NO_HEADER_WIDTH;
                        // Contents: 80
                        IkGui.tableSetupColumn("Set100", cf, 150.0f);
                        // Contents: 90
                        IkGui.tableSetupColumn("Def", cf);
                        // Contents: 100
                        IkGui.tableSetupColumn("Fix", cf | TableColumnFlags.WIDTH_FIXED);
                        // Contents: 110
                        IkGui.tableSetupColumn(
                                "Auto",
                                cf
                                        | (vars.columnFlags[3] != 0
                                                ? vars.columnFlags[3]
                                                : TableColumnFlags.NO_RESIZE));
                        IkGui.tableSetupColumn("Stretch", cf | TableColumnFlags.WIDTH_STRETCH);
                        IkGui.tableHeadersRow();
                        for (int row = 0; row < 5; ++row) {
                            IkGui.tableNextRow();
                            IkGui.pushID(row);
                            for (int column = 0; column < 4; ++column) {
                                IkGui.tableNextColumn();
                                if (row == 0) {
                                    IkGui.text(
                                            String.format(
                                                    "%.1f", IkGui.getContentRegionAvailable().x));
                                } else {
                                    IkGui.button(
                                            "W " + (80 + column * 10),
                                            80.0f + column * 10.0f,
                                            0.0f);
                                }
                            }
                            IkGui.popID();
                        }
                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test window 1");
        final Table table = findTable("table1");

        for (int n = 0; n < 2; ++n) {
            final String d = "pass " + n;
            if (n == 0) {
                vars.tableFlags = TableFlags.NONE;
            } else {
                vars.tableFlags |= TableFlags.RESIZABLE;
            }
            ctx.yieldFrame();
            assertEquals(150.0f, table.columns[0].widthRequest, d);
            if ((vars.tableFlags & TableFlags.RESIZABLE) != 0) {
                assertEquals(80.0f, table.columns[0].widthAuto, d);
            } else {
                assertEquals(150.0f, table.columns[0].widthAuto, d);
            }
            assertEquals(150.0f, table.columns[0].widthGiven, d);
            assertEquals(90.0f, table.columns[1].widthAuto, d);
            assertEquals(90.0f, table.columns[1].widthGiven, d);
            assertEquals(100.0f, table.columns[2].widthAuto, d);
            assertEquals(100.0f, table.columns[2].widthGiven, d);
            assertEquals(110.0f, table.columns[3].widthAuto, d);
            assertEquals(110.0f, table.columns[3].widthGiven, d);
            assertTrue(table.columns[4].widthAuto > 1.0f, d);
        }

        // Resize down, and check the widths are restored after clearing RESIZABLE
        vars.columnFlags[3] = TableColumnFlags.WIDTH_FIXED;
        ctx.itemDragWithDelta(resizeID(table, 0), -20.0f, 0);
        ctx.itemDragWithDelta(resizeID(table, 1), -30.0f, 0);
        ctx.itemDragWithDelta(resizeID(table, 2), -40.0f, 0);
        ctx.itemDragWithDelta(resizeID(table, 3), -50.0f, 0);
        // The resizes happened
        assertEquals(150.0f - 20.0f, table.columns[0].widthRequest);
        assertEquals(90.0f - 30.0f, table.columns[1].widthRequest);
        assertEquals(100.0f - 40.0f, table.columns[2].widthRequest);
        assertEquals(110.0f - 50.0f, table.columns[3].widthRequest);
        vars.columnFlags[3] = TableColumnFlags.WIDTH_FIXED | TableColumnFlags.NO_RESIZE;
        ctx.yieldFrame();
        // Auto restores
        assertEquals(110.0f, table.columns[3].widthGiven);
        vars.tableFlags &= ~TableFlags.RESIZABLE;
        ctx.yieldFrame();
        // Explicit, default, fixed and auto all restore
        assertEquals(150.0f, table.columns[0].widthGiven);
        assertEquals(90.0f, table.columns[1].widthGiven);
        assertEquals(100.0f, table.columns[2].widthGiven);
        assertEquals(110.0f, table.columns[3].widthGiven);

        // Resize up, and check the widths are restored after clearing RESIZABLE
        vars.tableFlags |= TableFlags.RESIZABLE;
        vars.columnFlags[3] = TableColumnFlags.WIDTH_FIXED;
        ctx.yieldFrame();
        ctx.itemDoubleClick(resizeID(table, 2));
        // Fixed restores
        assertEquals(100.0f, table.columns[2].widthGiven);
        ctx.itemDragWithDelta(resizeID(table, 0), 20.0f, 0);
        ctx.itemDragWithDelta(resizeID(table, 1), 30.0f, 0);
        ctx.itemDragWithDelta(resizeID(table, 2), 40.0f, 0);
        ctx.itemDragWithDelta(resizeID(table, 3), 50.0f, 0);
        assertEquals(150.0f + 20.0f, table.columns[0].widthRequest);
        assertEquals(90.0f + 30.0f, table.columns[1].widthRequest);
        assertEquals(100.0f + 40.0f, table.columns[2].widthRequest);
        assertEquals(110.0f + 50.0f, table.columns[3].widthRequest);
        vars.columnFlags[3] = TableColumnFlags.WIDTH_FIXED | TableColumnFlags.NO_RESIZE;
        ctx.yieldFrame();
        assertEquals(110.0f, table.columns[3].widthGiven);
        vars.tableFlags &= ~TableFlags.RESIZABLE;
        ctx.yieldFrame();
        assertEquals(150.0f, table.columns[0].widthGiven);
        assertEquals(90.0f, table.columns[1].widthGiven);
        assertEquals(100.0f, table.columns[2].widthGiven);
        assertEquals(110.0f, table.columns[3].widthGiven);
    }

    /** table_width_explicit_weight: specifying a weight in tableSetupColumn(). */
    @Test
    void testTableWidthExplicitWeight() {
        final IkInt tableFlags = new IkInt(0);
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(600.0f, 0.0f, Condition.APPEARING);
                    IkGui.begin("Test window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.checkboxFlags("TableFlags.RESIZABLE", tableFlags, TableFlags.RESIZABLE);
                    if (IkGui.beginTable("table1", 3, TableFlags.BORDERS_V)) {
                        IkGui.tableSetupColumn("1.0f", TableColumnFlags.WIDTH_STRETCH, 1.0f);
                        IkGui.tableSetupColumn("2.0f", TableColumnFlags.WIDTH_STRETCH, 2.0f);
                        IkGui.tableSetupColumn("3.0f", TableColumnFlags.WIDTH_STRETCH, 3.0f);
                        IkGui.tableHeadersRow();
                        for (int n = 0; n < 4 * 3; ++n) {
                            IkGui.tableNextColumn();
                            IkGui.text(String.format("%.1f", IkGui.getContentRegionAvailable().x));
                        }
                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test window 1");
        final Table table = findTable("table1");
        for (int n = 0; n < 2; ++n) {
            if (n == 0) {
                tableFlags.set(TableFlags.NONE);
            } else {
                tableFlags.set(tableFlags.get() | TableFlags.RESIZABLE);
            }
            ctx.yieldFrame();
            assertEquals(1.0f, table.columns[0].stretchWeight);
            assertEquals(2.0f, table.columns[1].stretchWeight);
            assertEquals(3.0f, table.columns[2].stretchWeight);
            assertTrue(
                    (table.columns[0].widthRequest / table.columns[0].stretchWeight)
                                    - (table.columns[1].widthRequest
                                            / table.columns[1].stretchWeight)
                            <= 1.0f);
            assertTrue(
                    (table.columns[1].widthRequest / table.columns[1].stretchWeight)
                                    - (table.columns[2].widthRequest
                                            / table.columns[2].stretchWeight)
                            <= 1.0f);
        }
    }

    /** table_width_distrib: equal widths for stretched columns. */
    @Test
    void testTableWidthDistrib() {
        final IkBoolean precise = new IkBoolean(false);
        ctx.setGui(
                () -> {
                    final var style = IkGuiInternal.context.style.variable;
                    final Vector2f viewportPos = IkGui.getMainViewport().position;
                    IkGui.setNextWindowPos(
                            viewportPos.x + 10, viewportPos.y + 10, Condition.FIRST_USE_EVER);
                    IkGui.setNextWindowSize(400, 0, Condition.APPEARING);
                    IkGui.begin("Test window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    // {column count, flags}
                    final int[][] testCases = {
                        {1, TableFlags.NONE},
                        {2, TableFlags.NONE},
                        {3, TableFlags.NONE},
                        {9, TableFlags.NONE},
                        {1, TableFlags.BORDERS_INNER_V},
                        {2, TableFlags.BORDERS_INNER_V},
                        {3, TableFlags.BORDERS_INNER_V},
                        {1, TableFlags.BORDERS_OUTER_V},
                        {2, TableFlags.BORDERS_OUTER_V},
                        {3, TableFlags.BORDERS_OUTER_V},
                        {1, TableFlags.BORDERS},
                        {2, TableFlags.BORDERS},
                        {3, TableFlags.BORDERS},
                        {9, TableFlags.BORDERS},
                    };
                    IkGui.checkbox("TableFlags.PRECISE_WIDTHS", precise);
                    final float maxVariance = precise.get() ? 0.0f : 1.0f;
                    IkGui.text(String.format("(width variance should be <= %.2f)", maxVariance));
                    IkGui.pushStyleVarFloat2(
                            StyleVariable.ITEM_SPACING,
                            style.itemSpacing.x,
                            (float) Math.floor(style.itemSpacing.y * 0.80f));
                    for (int n = 0; n < testCases.length; ++n) {
                        final int columnCount = testCases[n][0];
                        IkGui.pushID(n);
                        IkGui.spacing();
                        IkGui.button("..", -Float.MIN_VALUE, 5.0f);
                        int flags = testCases[n][1] | TableFlags.BORDERS_OUTER_H;
                        if (precise.get()) {
                            flags |= TableFlags.PRECISE_WIDTHS;
                        }
                        if (IkGui.beginTable("table1", columnCount, flags)) {
                            IkGui.tableNextRow();
                            float minW = Float.MAX_VALUE;
                            float maxW = -Float.MAX_VALUE;
                            for (int c = 0; c < columnCount; ++c) {
                                IkGui.tableSetColumnIndex(c);
                                final float w = IkGui.getContentRegionAvailable().x;
                                minW = Math.min(w, minW);
                                maxW = Math.max(w, maxW);
                                IkGui.alignTextToFramePadding();
                                IkGui.text(String.format("Width %.2f", w));
                                IkGui.button("..", -Float.MIN_VALUE, 5.0f);
                            }
                            final float variance = maxW - minW;
                            ctx.check(
                                    variance <= maxVariance, "case " + n + " variance " + variance);
                            IkGui.endTable();
                        }
                        IkGui.popID();
                    }
                    IkGui.popStyleVar();
                    IkGui.end();
                });
        precise.set(false);
        ctx.yieldFrames(2);
        precise.set(true);
        ctx.yieldFrames(2);
    }

    /** table_width_keep_visible: keeping columns visible when the table is narrower than them. */
    @Test
    void testTableWidthKeepVisible() {
        final float[] width = {150.0f};
        ctx.setGui(
                () -> {
                    IkGui.begin("Test window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    // The first test duplicates table_width_distrib
                    if (IkGui.beginTable("table0", 2)) {
                        for (int row = 0; row < 2; ++row) {
                            IkGui.tableNextColumn();
                            IkGui.text(
                                    String.format(
                                            "Width %.2f", IkGui.getContentRegionAvailable().x));
                            IkGui.tableNextColumn();
                            IkGui.text(
                                    String.format(
                                            "Width %.2f", IkGui.getContentRegionAvailable().x));
                        }
                        final Table table = IkGuiInternal.context.currentTable;
                        ctx.check(
                                Math.abs(table.columns[0].widthGiven - table.columns[1].widthGiven)
                                        <= 1.0f,
                                "case 0 widths");
                        IkGui.endTable();
                    }

                    IkGui.dragFloat("Width", width, 1.0f, 10.0f, 500.0f);
                    final int[] testFlags = {
                        TableFlags.BORDERS,
                        TableFlags.BORDERS_OUTER,
                        TableFlags.BORDERS_INNER,
                        TableFlags.NONE
                    };
                    for (int n = 0; n < 4; ++n) {
                        // The total width is smaller than the sum of the column widths
                        if (IkGui.beginTable("table" + (n + 1), 4, testFlags[n], width[0], 100)) {
                            final String d = "case " + (n + 1);
                            IkGui.tableSetupColumn("One", TableColumnFlags.WIDTH_FIXED, 200.0f);
                            IkGui.tableSetupColumn("Two", TableColumnFlags.WIDTH_FIXED, 50.0f);
                            IkGui.tableSetupColumn("Three", TableColumnFlags.WIDTH_FIXED, 70.0f);
                            IkGui.tableSetupColumn("Four", TableColumnFlags.WIDTH_FIXED, 80.0f);
                            helperTableSubmitCellsText(4, 4);
                            final Table table = IkGuiInternal.context.currentTable;
                            // All the widths are smaller than requested
                            for (int c = 0; c < 4; ++c) {
                                ctx.check(
                                        table.columns[c].widthGiven < table.columns[c].widthRequest,
                                        d + " column " + c + " smaller");
                            }
                            // The widths of columns 1 to 3 are equal, and so are their visible
                            // areas
                            ctx.checkEquals(
                                    table.columns[1].widthGiven,
                                    table.columns[2].widthGiven,
                                    d + " 1-2");
                            ctx.checkEquals(
                                    table.columns[1].clipRect.getWidth(),
                                    table.columns[2].clipRect.getWidth(),
                                    d + " clip 1-2");
                            ctx.checkEquals(
                                    table.columns[1].widthGiven,
                                    table.columns[3].widthGiven,
                                    d + " 1-3");
                            IkGui.endTable();
                        }
                    }
                    IkGui.end();
                });
        ctx.yieldFrames(3);
    }

    /** table_width_same: TableFlags.SIZING_FIXED_SAME and SIZING_STRETCH_SAME. */
    @Test
    void testTableWidthSame() {
        final boolean[] firstFrame = {true};
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(500, 300, Condition.APPEARING);
                    IkGui.begin("Test window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    for (int n = 0; n < 3; ++n) {
                        int flags = TableFlags.BORDERS;
                        if (n == 0 || n == 2) {
                            flags |= TableFlags.SIZING_FIXED_SAME;
                        }
                        if (n == 1) {
                            flags |= TableFlags.SIZING_STRETCH_SAME;
                        }
                        IkGui.text("TEST CASE " + n);
                        if (IkGui.beginTable("table" + n, 4, flags)) {
                            if (n == 2) {
                                // Mixed, which isn't very well defined
                                IkGui.tableSetupColumn("", TableColumnFlags.WIDTH_FIXED);
                                IkGui.tableSetupColumn("", TableColumnFlags.WIDTH_STRETCH);
                                IkGui.tableSetupColumn("", TableColumnFlags.WIDTH_FIXED);
                                IkGui.tableSetupColumn("", TableColumnFlags.WIDTH_STRETCH);
                            }
                            IkGui.tableNextColumn();
                            IkGui.text("AAA");
                            IkGui.tableNextColumn();
                            IkGui.text("AAAA");
                            IkGui.tableNextColumn();
                            IkGui.tableNextColumn();
                            IkGui.text("AA");

                            final Table table = IkGuiInternal.context.currentTable;
                            final String d = "case " + n;
                            if (!firstFrame[0]) {
                                if (n == 0) {
                                    for (int c = 0; c < 4; ++c) {
                                        ctx.checkEquals(
                                                IkGui.calcTextSize("AAAA").x,
                                                table.columns[c].widthGiven,
                                                d + " column " + c);
                                    }
                                }
                                if (n == 0 || n == 1) {
                                    for (int c = 1; c < 4; ++c) {
                                        ctx.check(
                                                Math.abs(
                                                                table.columns[0].widthGiven
                                                                        - table.columns[c]
                                                                                .widthGiven)
                                                        <= 1.0f,
                                                d + " same " + c);
                                    }
                                }
                                if (n == 2) {
                                    ctx.checkEquals(
                                            IkGui.calcTextSize("AAA").x,
                                            table.columns[0].widthGiven,
                                            d + " column 0");
                                    ctx.checkEquals(
                                            IkGui.calcTextSize("AAA").x,
                                            table.columns[2].widthGiven,
                                            d + " column 2");
                                    ctx.check(
                                            Math.abs(
                                                            table.columns[0].widthGiven
                                                                    - table.columns[2].widthGiven)
                                                    <= 1.0f,
                                            d + " fixed");
                                    ctx.check(
                                            Math.abs(
                                                            table.columns[1].widthGiven
                                                                    - table.columns[3].widthGiven)
                                                    <= 1.0f,
                                            d + " stretch");
                                }
                            }
                            IkGui.endTable();
                            IkGui.spacing();
                        }
                    }
                    IkGui.end();
                    firstFrame[0] = false;
                });
        ctx.yieldFrames(3);
    }

    /** table_width_autofit: the auto-fit functions in the table context menu, and double clicks. */
    @Test
    void testTableWidthAutofit() {
        final int[] table2Flags = {0};
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(500, 300, Condition.APPEARING);
                    IkGui.begin("Test window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    for (int t = 0; t < 2; ++t) {
                        final int sizing =
                                t == 0
                                        ? TableFlags.SIZING_FIXED_FIT
                                        : TableFlags.SIZING_STRETCH_SAME;
                        if (IkGui.beginTable(
                                "table" + t,
                                4,
                                TableFlags.BORDERS | TableFlags.RESIZABLE | sizing)) {
                            IkGui.tableSetupColumn("A");
                            IkGui.tableSetupColumn("B");
                            IkGui.tableSetupColumn("C");
                            // The initial width
                            IkGui.tableSetupColumn("F99", TableColumnFlags.WIDTH_FIXED, 99.0f);
                            IkGui.tableHeadersRow();
                            IkGui.tableNextColumn();
                            IkGui.text("AA");
                            IkGui.tableNextColumn();
                            IkGui.text("AAAA");
                            IkGui.tableNextColumn();
                            IkGui.text("AAAAAA");
                            IkGui.tableNextColumn();
                            IkGui.text("AAAAAAAA");
                            IkGui.endTable();
                        }
                    }
                    if (IkGui.beginTable("table2", 3, table2Flags[0])) {
                        IkGui.tableSetupColumn("A");
                        IkGui.tableSetupColumn("B");
                        IkGui.tableSetupColumn("C");
                        IkGui.tableHeadersRow();
                        IkGui.tableNextColumn();
                        IkGui.textUnformatted("Short text.");
                        IkGui.tableNextColumn();
                        IkGui.textUnformatted("A very long text that may not fit.");
                        IkGui.tableNextColumn();
                        IkGui.textUnformatted("Short text.");
                        IkGui.endTable();
                    }
                    IkGui.end();
                });

        // "Size All" on fixed columns
        {
            ctx.setRef("Test window 1");
            final Table table = findTable("table0");
            assertEquals(99.0f, table.columns[3].widthGiven);
            ctx.itemDragWithDelta(resizeID(table, 0), 50.0f, 0);
            ctx.itemDragWithDelta(resizeID(table, 1), -20.0f, 0);
            ctx.itemDragWithDelta(resizeID(table, 2), 20.0f, 0);
            ctx.tableOpenContextMenu("table0", -1);
            ctx.setRef("//$FOCUSED");
            ctx.itemClick("###SizeAll");
            assertEquals(IkGui.calcTextSize("AA").x, table.columns[0].widthGiven);
            assertEquals(IkGui.calcTextSize("AAAA").x, table.columns[1].widthGiven);
            assertEquals(IkGui.calcTextSize("AAAAAA").x, table.columns[2].widthGiven);
            // "Size All" is a best fit size, not a revert to the default
            assertEquals(IkGui.calcTextSize("AAAAAAAA").x, table.columns[3].widthGiven);

            // Double click
            ctx.itemDragWithDelta(resizeID(table, 0), 50.0f, 0);
            ctx.mouseDoubleClick(MouseButton.LEFT);
            assertEquals(IkGui.calcTextSize("AA").x, table.columns[0].widthGiven);
        }

        // "Size One" and "Size All" on stretch columns
        {
            ctx.setRef("Test window 1");
            final Table table = findTable("table1");
            assertTrue(Math.abs(table.columns[0].widthGiven - table.columns[2].widthGiven) <= 1.0f);
            assertEquals(99.0f, table.columns[3].widthGiven);
            ctx.itemDragWithDelta(resizeID(table, 0), 50.0f, 0);
            ctx.itemDragWithDelta(resizeID(table, 1), -20.0f, 0);

            final String[] texts = {"AA", "AAAA", "AAAAAA"};
            for (int c = 0; c < 3; ++c) {
                ctx.setRef("Test window 1");
                ctx.tableOpenContextMenu("table1", c);
                ctx.setRef("//$FOCUSED");
                ctx.itemClick("###SizeOne");
                assertEquals(
                        IkGui.calcTextSize(texts[c]).x, table.columns[c].widthGiven, "column " + c);
                if (c < 2) {
                    assertEquals(99.0f, table.columns[3].widthGiven);
                }
            }

            ctx.setRef("Test window 1");
            ctx.tableOpenContextMenu("table1", 0);
            ctx.setRef("//$FOCUSED");
            ctx.itemClick("###SizeAll");
            assertEquals(1.0f, table.columns[0].stretchWeight);
            assertEquals(1.0f, table.columns[1].stretchWeight);
            assertEquals(1.0f, table.columns[2].stretchWeight);
            // Upstream notes that "Size All" with mixed columns is a best fit, not a revert
            assertEquals(IkGui.calcTextSize("AAAAAAAA").x, table.columns[3].widthGiven);
        }

        // Auto-sizing a window with a table of equal stretch columns. Upstream notes the
        // resizable variant is broken.
        table2Flags[0] = TableFlags.BORDERS;
        ctx.yieldFrame();
        ctx.setRef("Test window 1");
        final Table table = findTable("table2");
        ctx.windowResize("", 800, 200);
        ctx.itemDoubleClick(IkGuiImplWindows.getWindowResizeID(ctx.getWindowByRef(""), 0));
        assertTrue(
                table.columns[1].widthGiven
                        >= IkGui.calcTextSize("A very long text that may not fit.").x);
    }

    /** table_padding: cell padding and borders. */
    @Test
    void testTablePadding() {
        final int[] step = {0};
        final float[] expectedWidth = {0};
        ctx.setGui(
                () -> {
                    final float cellPadding = IkGuiInternal.context.style.variable.cellPadding.x;
                    final float borderSize = 1.0f;
                    IkGui.begin(
                            "Test window 1",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.setNextItemWidth(30.0f);
                    IkGui.sliderInt("Step", step, 0, 3);

                    int flags = TableFlags.SIZING_FIXED_FIT;
                    switch (step[0]) {
                        case 0 -> expectedWidth[0] = 120.0f + 150.0f + cellPadding * 2.0f;
                        case 1 -> {
                            flags |= TableFlags.BORDERS_INNER_V;
                            expectedWidth[0] = 120.0f + 150.0f + cellPadding * 2.0f + borderSize;
                        }
                        case 2 -> {
                            flags |= TableFlags.BORDERS_OUTER_V;
                            expectedWidth[0] =
                                    120.0f + 150.0f + cellPadding * 4.0f + borderSize * 2.0f;
                        }
                        default -> {
                            flags |= TableFlags.BORDERS_INNER_V | TableFlags.BORDERS_OUTER_V;
                            expectedWidth[0] =
                                    120.0f + 150.0f + cellPadding * 4.0f + borderSize * 3.0f;
                        }
                    }
                    if (IkGui.beginTable("table1", 2, flags)) {
                        IkGui.tableSetupColumn("0", TableColumnFlags.WIDTH_FIXED, 120.0f);
                        IkGui.tableSetupColumn("1", TableColumnFlags.WIDTH_FIXED, 150.0f);
                        IkGui.tableNextRow();
                        IkGui.tableSetColumnIndex(0);
                        IkGui.button("120", 120, 0);
                        IkGui.text(String.format("(%.1f)", IkGui.getContentRegionAvailable().x));
                        IkGui.button("...", IkGui.getContentRegionAvailable().x, 0);

                        IkGui.tableSetColumnIndex(1);
                        IkGui.button("150", 150, 0);
                        IkGui.text(String.format("(%.1f)", IkGui.getContentRegionAvailable().x));
                        IkGui.button("...", IkGui.getContentRegionAvailable().x, 0);
                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test window 1");
        final Window window = ctx.getWindowByRef("");
        final Table table = findTable("table1");
        for (int s = 0; s < 4; ++s) {
            final String d = "step " + s;
            step[0] = s;
            // The window content width reflects the old step, and the ideal width the new one
            ctx.yieldFrame();
            assertEquals(
                    120.0f, table.columns[0].contentMaxXUnfrozen - table.columns[0].workMinX, d);
            assertEquals(
                    150.0f, table.columns[1].contentMaxXUnfrozen - table.columns[1].workMinX, d);
            assertEquals(expectedWidth[0], table.columnsAutoFitWidth, d);
            // The inner window updates to the right ideal width, and auto-resizes to it
            ctx.yieldFrame();
            assertEquals(expectedWidth[0], window.contentSizeIdeal.x, d);
            // The inner window updates to the right content width
            ctx.yieldFrame();
            assertEquals(expectedWidth[0], window.contentSize.x, d);
        }
    }

    /** table_functions_without_table: table functions outside a table. */
    @Test
    void testTableFunctionsWithoutTable() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Test window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    ctx.checkEquals(0, IkGui.tableGetColumnIndex(), "column index");
                    ctx.checkEquals(false, IkGui.tableSetColumnIndex(42), "set column index");
                    ctx.checkEquals(false, IkGui.tableNextColumn(), "next column");
                    ctx.checkEquals(0, IkGui.tableGetColumnFlags(0), "column flags");
                    ctx.checkEquals(null, IkGui.tableGetColumnName(), "column name");
                    IkGui.end();
                });
        ctx.yieldFrame();
    }

    /** table_sizing_policies: the table sizing policies, with and without resizing. */
    @Test
    void testTableSizingPolicies() {
        final TableTestingVars vars = new TableTestingVars();
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(500.0f, 500.0f, Condition.APPEARING);
                    IkGui.begin("Test window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.beginTable(
                            "table1", 3, vars.tableFlags, vars.outerSize.x, vars.outerSize.y)) {
                        // Wider than the longest label
                        vars.widths[0] = IkGui.calcTextSize("Oh dear").x + 9.0f;
                        if (vars.step == 1) {
                            IkGui.tableSetupColumn("", 0);
                            IkGui.tableSetupColumn(
                                    "", TableColumnFlags.WIDTH_FIXED, vars.widths[0]);
                            IkGui.tableSetupColumn("", 0);
                        }
                        for (int row = 0; row < 3; ++row) {
                            IkGui.tableNextRow();
                            for (int c = 0; c < 3; ++c) {
                                IkGui.tableNextColumn();
                                IkGui.text("Oh dear");
                            }
                        }
                        IkGui.endTable();
                    }
                    if (IkGui.beginTable(
                            "table2", 3, vars.tableFlags, vars.outerSize.x, vars.outerSize.y)) {
                        vars.widths[1] = IkGui.calcTextSize("CCCCCCCC").x + 9.0f;
                        if (vars.step == 1) {
                            IkGui.tableSetupColumn("", 0);
                            IkGui.tableSetupColumn(
                                    "", TableColumnFlags.WIDTH_FIXED, vars.widths[1]);
                            IkGui.tableSetupColumn("", 0);
                        }
                        for (int row = 0; row < 3; ++row) {
                            IkGui.tableNextRow();
                            IkGui.tableNextColumn();
                            IkGui.text("AAA");
                            IkGui.tableNextColumn();
                            IkGui.text("BBBBBB");
                            IkGui.tableNextColumn();
                            IkGui.text("CCCCCCCC");
                        }
                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        final int[] policies = {
            TableFlags.SIZING_FIXED_FIT,
            TableFlags.SIZING_FIXED_SAME,
            TableFlags.SIZING_STRETCH_PROP,
            TableFlags.SIZING_STRETCH_SAME
        };
        final float aaa = IkGui.calcTextSize("AAA").x;
        final float bbb = IkGui.calcTextSize("BBBBBB").x;
        final float ccc = IkGui.calcTextSize("CCCCCCCC").x;
        final float ohDear = IkGui.calcTextSize("Oh dear").x;
        ctx.setRef("Test window 1");
        for (int s = 0; s < 4 * 2; ++s) {
            final boolean fixedFill = (s & 1) != 0;
            final int policy = policies[s / 2];
            vars.tableFlags = policy;
            vars.outerSize.set(fixedFill ? -Float.MIN_VALUE : 0.0f, 0.0f);
            vars.step = 0;
            final Table table1 = findTable("table1");
            final Table table2 = findTable("table2");

            for (int resize = 0; resize < 2; ++resize) {
                final String d = "step " + s + " resize " + resize;
                if (resize == 1) {
                    vars.tableFlags |= TableFlags.RESIZABLE;
                    ctx.yieldFrames(2);
                    ctx.itemDragWithDelta(resizeID(table1, 0), 20.0f, 0);
                    ctx.itemDragWithDelta(resizeID(table1, 1), 20.0f, 0);
                    ctx.itemDragWithDelta(resizeID(table2, 0), 20.0f, 0);
                    ctx.itemDragWithDelta(resizeID(table2, 1), 20.0f, 0);
                    vars.tableFlags &= ~TableFlags.RESIZABLE;
                }
                ctx.yieldFrames(2);

                if (policy == TableFlags.SIZING_FIXED_FIT
                        || policy == TableFlags.SIZING_FIXED_SAME) {
                    for (int c = 0; c < 3; ++c) {
                        assertEquals(
                                ohDear, table1.columns[c].widthRequest, d + " table1 column " + c);
                    }
                }
                if (policy == TableFlags.SIZING_FIXED_FIT) {
                    assertEquals(aaa, table2.columns[0].widthRequest, d);
                    assertEquals(bbb, table2.columns[1].widthRequest, d);
                    assertEquals(ccc, table2.columns[2].widthRequest, d);
                }
                if (policy == TableFlags.SIZING_FIXED_SAME) {
                    for (int c = 0; c < 3; ++c) {
                        assertEquals(
                                ccc, table2.columns[c].widthRequest, d + " table2 column " + c);
                    }
                }
                if (policy == TableFlags.SIZING_STRETCH_SAME
                        || policy == TableFlags.SIZING_STRETCH_PROP) {
                    assertEquals(1.0f, table1.columns[0].stretchWeight, d);
                    assertEquals(1.0f, table1.columns[1].stretchWeight, d);
                }
                if (policy == TableFlags.SIZING_STRETCH_SAME) {
                    assertEquals(1.0f, table2.columns[0].stretchWeight, d);
                    assertEquals(1.0f, table2.columns[1].stretchWeight, d);
                }
                if (policy == TableFlags.SIZING_STRETCH_PROP) {
                    final var columns = table2.columns;
                    assertEquals(
                            bbb / aaa,
                            columns[1].stretchWeight / columns[0].stretchWeight,
                            0.001f,
                            d);
                    assertEquals(
                            ccc / aaa,
                            columns[2].stretchWeight / columns[0].stretchWeight,
                            0.001f,
                            d);
                    assertEquals(
                            ccc / bbb,
                            columns[2].stretchWeight / columns[1].stretchWeight,
                            0.001f,
                            d);
                }
            }

            ctx.sleep(0.5f);
            vars.step = 1;
            ctx.sleep(0.5f);
            assertEquals(vars.widths[0], table1.columns[1].widthRequest, "step " + s);
            assertEquals(vars.widths[1], table2.columns[1].widthRequest, "step " + s);
        }
    }

    /** table_resizing_behaviors: resizing tables with different column sizing policies. */
    @Test
    void testTableResizingBehaviors() {
        final IkInt tableFlags =
                new IkInt(
                        TableFlags.RESIZABLE
                                | TableFlags.HIDEABLE
                                | TableFlags.REORDERABLE
                                | TableFlags.BORDERS
                                | TableFlags.NO_SAVED_SETTINGS);
        ctx.setGui(
                () -> {
                    final Vector2f viewportPos = IkGui.getMainViewport().position;
                    IkGui.setNextWindowPos(
                            viewportPos.x + 20, viewportPos.y + 5, Condition.APPEARING);
                    IkGui.setNextWindowSize(500.0f, 0.0f, Condition.APPEARING);
                    IkGui.begin("Test window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.checkboxFlags("TableFlags.RESIZABLE", tableFlags, TableFlags.RESIZABLE);
                    final int flags = tableFlags.get();
                    // Upstream labels each table with the expected resizing behaviors
                    helperTableWithResizingPolicies("table1", flags, "FFFff");
                    IkGui.spacing();
                    helperTableWithResizingPolicies("table2", flags, "FFW");
                    IkGui.spacing();
                    helperTableWithResizingPolicies("table3", flags, "WWWw");
                    IkGui.spacing();
                    helperTableWithResizingPolicies("table4", flags, "WFFF");
                    IkGui.spacing();
                    helperTableWithResizingPolicies("table5", flags, "WF");
                    IkGui.spacing();
                    helperTableWithResizingPolicies("table6", flags, "WWF");
                    IkGui.spacing();
                    helperTableWithResizingPolicies("table7", flags, "WFW");
                    IkGui.spacing();
                    helperTableWithResizingPolicies("table8", flags, "FWF");
                    IkGui.spacing();
                    helperTableWithResizingPolicies("table9", flags, "WWWFFWWW");
                    IkGui.spacing();
                    IkGui.end();
                });
        // Upstream's warm-up frames let the tables measure their columns
        ctx.yieldFrame();
        ctx.setRef("Test window 1");
        // The fixed columns don't span the entire width of the table
        Table table = findTable("table1");
        assertTrue(table.columnsGivenWidth + 1.0f < table.innerWindow.rectContent.getWidth());
        final float[] initialWidths = new float[table.columnsCount];
        for (int c = 0; c >= 0; c = table.columns[c].nextEnabledColumn) {
            final var current = table.columns[c];
            final var previous =
                    current.prevEnabledColumn >= 0
                            ? table.columns[current.prevEnabledColumn]
                            : null;
            final var next =
                    current.nextEnabledColumn >= 0
                            ? table.columns[current.nextEnabledColumn]
                            : null;
            final float widthCurrent = current.widthGiven;
            final float widthPrevious = previous != null ? previous.widthGiven : 0;
            final float widthNext = next != null ? next.widthGiven : 0;
            final float widthTotal = table.columnsGivenWidth;
            // Save the initial size for later
            initialWidths[c] = current.widthGiven;

            // Resize the column
            final float moveBy = -30.0f;
            ctx.itemDragWithDelta(resizeID(table, c), moveBy, 0);

            final String d = "column " + c;
            // The previous and next column widths don't change
            assertTrue(previous == null || previous.widthGiven == widthPrevious, d);
            assertEquals(widthCurrent + moveBy, current.widthGiven, d);
            assertTrue(next == null || next.widthGiven == widthNext, d);
            // The empty space after the last column shrinks
            assertEquals(widthTotal + moveBy, table.columnsGivenWidth, d);
        }
        assertTrue(table.columnsGivenWidth + 1 < table.innerWindow.rectContent.getWidth());

        // Fitting columns
        {
            // The columns are smaller than their contents, after the resizing
            for (int c = 0; c >= 0; c = table.columns[c].nextEnabledColumn) {
                assertTrue(table.columns[c].widthGiven < initialWidths[c], "column " + c);
            }

            // Fit the right-most column, which restores its size
            final int right = table.rightMostEnabledColumn;
            ctx.setRef("Test window 1");
            ctx.itemClick(IkGuiTestContext.tableGetHeaderID(table, "F3"), MouseButton.RIGHT);
            ctx.setRef("//$FOCUSED");
            ctx.itemClick("###SizeOne");
            assertEquals(initialWidths[right], table.columns[right].widthGiven);

            // The other columns aren't affected
            for (int c = 0;
                    c >= 0 && c < table.rightMostEnabledColumn;
                    c = table.columns[c].nextEnabledColumn) {
                assertTrue(table.columns[c].widthGiven < initialWidths[c], "column " + c);
            }

            // Fit the rest of the columns, which fits them all to their contents
            ctx.setRef("Test window 1");
            ctx.itemClick(IkGuiTestContext.tableGetHeaderID(table, "F3"), MouseButton.RIGHT);
            ctx.setRef("//$FOCUSED");
            ctx.itemClick("###SizeAll");
            for (int c = 0; c >= 0; c = table.columns[c].nextEnabledColumn) {
                assertEquals(initialWidths[c], table.columns[c].widthGiven, "column " + c);
            }
        }

        // The columns span the entire width of the table
        ctx.setRef("Test window 1");
        table = findTable("table2");
        assertEquals(table.innerWindow.rectContent.getWidth(), table.columnsGivenWidth);

        // Check which visible columns have resize handles
        for (int c = 0; c >= 0; c = table.columns[c].nextEnabledColumn) {
            final int handle = resizeID(table, c);
            if (c == table.rightMostEnabledColumn) {
                // W
                assertFalse(ctx.itemExists(handle), "column " + c);
            } else {
                // F
                assertTrue(ctx.itemExists(handle), "column " + c);
            }
        }
        assertEquals(table.innerWindow.rectContent.getWidth(), table.columnsGivenWidth);
    }

    /** table_clip: which columns are visible in a table that scrolls horizontally. */
    @Test
    void testTableClip() {
        final int[] frame = {-2};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test window 1",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    if (IkGui.beginTable(
                            "table1",
                            4,
                            TableFlags.SCROLL_X | TableFlags.SIZING_FIXED_FIT | TableFlags.BORDERS,
                            200,
                            200)) {
                        IkGui.tableSetupColumn("One", 0, 80);
                        IkGui.tableSetupColumn("Two", 0, 80);
                        IkGui.tableSetupColumn("Three", 0, 80);
                        IkGui.tableSetupColumn("Four", 0, 80);
                        for (int row = 0; row < 2; ++row) {
                            IkGui.tableNextRow();
                            final boolean[] visible = new boolean[4];
                            for (int c = 0; c < 4; ++c) {
                                visible[c] = IkGui.tableSetColumnIndex(c);
                                IkGui.text(visible[c] ? "1" : "0");
                            }
                            if (frame[0] > 1) {
                                ctx.check(visible[0], "column 0");
                                ctx.check(visible[1], "column 1");
                                // Half visible
                                ctx.check(visible[2], "column 2");
                                ctx.check(!visible[3], "column 3");
                            }
                        }
                        IkGui.endTable();
                    }
                    IkGui.end();
                    frame[0]++;
                });
        // Upstream finishes after the checks pass once
        ctx.yieldFrames(5);
    }

    /**
     * table_clip_auto_resize: a host window with ALWAYS_AUTO_RESIZE still coarsely clips tables.
     */
    @Test
    void testTableClipAutoResize() {
        final int[] tableVisibleCount = {0};
        final int[] frame = {-2};
        ctx.setGui(
                () -> {
                    final var viewport = IkGui.getMainViewport();
                    IkGui.setNextWindowPos(
                            viewport.position.x, viewport.position.y + viewport.size.y - 80.0f);
                    IkGui.begin(
                            "Test window 1",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.dummy(100, 100);
                    if (IkGui.beginTable(
                            "table1",
                            4,
                            TableFlags.SCROLL_X | TableFlags.SIZING_FIXED_FIT | TableFlags.BORDERS,
                            200,
                            200)) {
                        tableVisibleCount[0]++;
                        IkGui.tableNextColumn();
                        IkGui.text("Hello");
                        IkGui.endTable();
                    }
                    IkGui.end();
                    frame[0]++;
                });
        ctx.yieldFrames(13);
        // 0 or 1
        assertTrue(tableVisibleCount[0] <= 1, "visible " + tableVisibleCount[0] + " times");
    }

    /**
     * table_clip_all_columns: a clipper in a table so small only the scrollbar shows, with no cell
     * padding.
     */
    @Test
    void testTableClipAllColumns() {
        final boolean[] tableIsVisible = {false};
        final int[] columnsVisibleMask = {0};
        ctx.setGui(
                () -> {
                    tableIsVisible[0] = false;
                    columnsVisibleMask[0] = 0;
                    final var style = IkGuiInternal.context.style.variable;
                    IkGui.setNextWindowSize(500, 500, Condition.APPEARING);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    // The clipper's step() used to fail here, from no vertical advance
                    IkGui.pushStyleVarFloat2(StyleVariable.CELL_PADDING, 0, 0);
                    // Offset everything
                    final Vector2f pos = IkGui.getCursorScreenPos();
                    IkGui.setCursorScreenPos(
                            pos.x + style.windowMinSize.x, pos.y + style.windowMinSize.y);
                    if (IkGui.beginTable("table_scrolly", 3, TableFlags.SCROLL_Y)) {
                        tableIsVisible[0] = true;
                        final ListClipper clipper = new ListClipper();
                        clipper.begin(100);
                        while (clipper.step()) {
                            for (int row = clipper.displayStart; row < clipper.displayEnd; ++row) {
                                for (int column = 0; column < 3; ++column) {
                                    // Always eagerly testing this
                                    if (!IkGui.tableNextColumn()) {
                                        continue;
                                    }
                                    columnsVisibleMask[0] |= 1 << IkGui.tableGetColumnIndex();
                                    IkGui.text("Hello " + column + "," + row);
                                }
                            }
                        }
                        IkGui.endTable();
                    }
                    IkGui.popStyleVar();
                    IkGui.end();
                });
        final var style = ctx.context.style.variable;
        ctx.setRef("Test Window");
        // Everything is visible
        ctx.windowResize("", 500, 500);
        assertTrue(tableIsVisible[0]);
        assertEquals(0x07, columnsVisibleMask[0]);
        // Nothing is visible
        ctx.windowResize("", style.windowMinSize.x, style.windowMinSize.y);
        assertFalse(tableIsVisible[0]);
        assertEquals(0x00, columnsVisibleMask[0]);

        // Only the scrollbar is visible, which used to crash upstream with a clipper and no cell
        // padding
        ctx.windowResize(
                "",
                style.windowMinSize.x + style.windowPadding.x * 2 + style.scrollbarSize,
                style.windowMinSize.y + 500.0f);
        assertTrue(tableIsVisible[0]);
        // By design, beginTable() makes a column visible if none would be
        assertEquals(0x01, columnsVisibleMask[0]);
    }

    /** table_clipped_outer_query: tables and children can be queried as items even when clipped. */
    @Test
    void testTableClippedOuterQuery() {
        final IkBoolean emptyInside = new IkBoolean(false);
        final IkBoolean multiInstances = new IkBoolean(false);
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(300.0f, 500.0f);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.checkbox("EmptyInside", emptyInside);
                    IkGui.checkbox("Multi-instances", multiInstances);
                    for (int n = 0; n < 6; ++n) {
                        IkGui.text("table" + n);
                        IkGui.beginChild(
                                "child" + n,
                                -Float.MIN_VALUE,
                                100,
                                ChildFlags.BORDERS | ChildFlags.FRAME_STYLE);
                        // This affects the endChild() submission path
                        if (!emptyInside.get()) {
                            IkGui.button("Button");
                        }
                        IkGui.endChild();

                        final int instances = multiInstances.get() ? 3 : 1;
                        for (int i = 0; i < instances; ++i) {
                            if (IkGui.beginTable(
                                    "table" + n,
                                    3,
                                    TableFlags.SCROLL_Y | TableFlags.BORDERS,
                                    -Float.MIN_VALUE,
                                    200)) {
                                if (!emptyInside.get()) {
                                    IkGui.tableNextColumn();
                                    IkGui.button("Button");
                                }
                                IkGui.endTable();
                            }
                        }
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        for (int s = 0; s < 3; ++s) {
            emptyInside.set(s == 1);
            multiInstances.set(s == 2);
            ctx.yieldFrames(4);
            for (int n = 0; n < 6; ++n) {
                assertTrue(ctx.itemExists("table" + n), "step " + s + " table " + n);
            }
            // Double as a test for children
            for (int n = 0; n < 6; ++n) {
                assertTrue(ctx.itemExists("child" + n), "step " + s + " child " + n);
            }
        }
        // Upstream scrolls to the item vertically
        ctx.mouseMove("table4");
    }

    /** table_empty: a table with no contents. */
    @Test
    void testTableEmpty() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Test window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.beginTable("table1", 3)) {
                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        ctx.yieldFrames(2);
    }

    /** table_default_sort_order: the default sort order assignment. */
    @Test
    void testTableDefaultSortOrder() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Test window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.beginTable("table1", 4, TableFlags.SORTABLE | TableFlags.SORT_MULTI);
                    IkGui.tableSetupColumn("0", TableColumnFlags.NONE);
                    IkGui.tableSetupColumn("1", TableColumnFlags.DEFAULT_SORT);
                    IkGui.tableSetupColumn(
                            "1",
                            TableColumnFlags.DEFAULT_SORT | TableColumnFlags.PREFER_SORT_ASCENDING);
                    IkGui.tableSetupColumn(
                            "2",
                            TableColumnFlags.DEFAULT_SORT
                                    | TableColumnFlags.PREFER_SORT_DESCENDING);
                    final TableSortSpecs specs = IkGui.tableGetSortSpecs();
                    IkGui.tableHeadersRow();
                    ctx.checkEquals(3, specs.specsCount, "count");
                    ctx.checkEquals(1, specs.specs[0].columnIndex, "column 0");
                    ctx.checkEquals(2, specs.specs[1].columnIndex, "column 1");
                    ctx.checkEquals(3, specs.specs[2].columnIndex, "column 2");
                    ctx.checkEquals(
                            SortDirection.ASCENDING, specs.specs[0].sortDirection, "direction 0");
                    ctx.checkEquals(
                            SortDirection.ASCENDING, specs.specs[1].sortDirection, "direction 1");
                    ctx.checkEquals(
                            SortDirection.DESCENDING, specs.specs[2].sortDirection, "direction 2");
                    IkGui.endTable();
                    IkGui.end();
                });
        ctx.yieldFrames(2);
    }

    /** table_max_columns: a table with the maximum number of columns. */
    @Test
    void testTableMaxColumns() {
        final int[] frame = {-2};
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(300, 400);
                    IkGui.begin("Test window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    final int columnsCount = IkGuiImplTables.TABLE_MAX_COLUMNS - 1;
                    IkGui.beginTable(
                            "table1", columnsCount, TableFlags.SCROLL_X | TableFlags.SCROLL_Y);
                    IkGui.tableSetupScrollFreeze(0, 10);
                    for (int n = 0; n < columnsCount; ++n) {
                        IkGui.tableSetupColumn(String.format("C%03d", n));
                    }
                    IkGui.tableHeadersRow();
                    for (int i = 0; i < 20; ++i) {
                        IkGui.tableNextRow();
                        for (int n = 0; n < columnsCount; ++n) {
                            IkGui.tableNextColumn();
                            IkGui.text("Data");
                        }
                    }
                    if (frame[0] < 1) {
                        IkGui.setScrollHereY(1.0f);
                    }
                    IkGui.endTable();
                    IkGui.end();
                    frame[0]++;
                });
        // Upstream finishes when the frame count reaches 2
        ctx.yieldFrames(4);
    }

    /**
     * table_synced_1: two tables with the same ID (synced instances), in one or two windows, and
     * resizing them.
     */
    @Test
    void testTableSynced1() {
        final boolean[] multiWindow = {false};
        final boolean[] sideBySide = {false};
        final boolean[] differSizes = {false};
        final boolean[] retest = {true};
        final int[] clickCounters = new int[3];
        ctx.setGui(
                () -> {
                    final int columnCount = 3;
                    final float[] columnWidths = new float[columnCount];
                    float firstInstanceWidth = 0.0f;
                    Window firstWindow = null;
                    for (int instance = 0; instance < 2; ++instance) {
                        final float width = 400.0f + (differSizes[0] ? instance : 0) * 50.0f;
                        IkGui.setNextWindowSize(
                                width, multiWindow[0] ? 210.0f : 320.0f, Condition.ALWAYS);
                        if (multiWindow[0] && instance == 1) {
                            if (sideBySide[0]) {
                                IkGui.setNextWindowPos(
                                        firstWindow.position.x + firstWindow.size.x + 10.0f,
                                        firstWindow.position.y,
                                        Condition.ALWAYS);
                            } else {
                                IkGui.setNextWindowPos(
                                        firstWindow.position.x,
                                        firstWindow.position.y + firstWindow.size.y + 10.0f,
                                        Condition.ALWAYS);
                            }
                        }
                        if (multiWindow[0] || instance == 0) {
                            IkGui.begin(
                                    "Test window - " + instance,
                                    null,
                                    WindowFlags.NO_SAVED_SETTINGS);
                            final IkBoolean mw = new IkBoolean(multiWindow[0]);
                            IkGui.checkbox("Multi-window table", mw);
                        }
                        if (instance == 0) {
                            firstWindow = IkGuiInternal.context.windowCurrent;
                        }
                        // A shared ID
                        if (multiWindow[0]) {
                            IkGuiInternal.pushOverrideID(123);
                        }
                        IkGui.beginTable(
                                "table",
                                columnCount,
                                TableFlags.NO_SAVED_SETTINGS
                                        | TableFlags.RESIZABLE
                                        | TableFlags.BORDERS
                                        | TableFlags.REORDERABLE);
                        for (int c = 0; c < columnCount; ++c) {
                            IkGui.tableSetupColumn(
                                    "Header",
                                    c != 0
                                            ? TableColumnFlags.WIDTH_FIXED
                                            : TableColumnFlags.WIDTH_STRETCH);
                        }
                        IkGui.tableHeadersRow();
                        for (int r = 0; r < 5; ++r) {
                            IkGui.tableNextRow();
                            for (int c = 0; c < columnCount; ++c) {
                                // The second table has larger data, to try to confuse the syncing
                                IkGui.tableNextColumn();
                                IkGui.text(instance != 0 ? "Long Data" : "Data");
                                if (r == 0 && c == 0 && IkGui.button("Button")) {
                                    clickCounters[instance]++;
                                }
                            }
                        }
                        if (retest[0]) {
                            final Table table = IkGuiInternal.context.currentTable;
                            if (instance == 0) {
                                // Save the column widths of the first instance
                                for (int c = 0; c < columnCount; ++c) {
                                    columnWidths[c] = table.columns[c].widthGiven;
                                }
                                firstInstanceWidth = table.workRect.getWidth();
                            } else {
                                // The columns keep their proportions across instances
                                final float tableWidth = table.workRect.getWidth();
                                for (int c = 0; c < columnCount; ++c) {
                                    ctx.checkEquals(
                                            firstInstanceWidth / columnWidths[c],
                                            tableWidth / table.columns[c].widthGiven,
                                            "column " + c + " proportion");
                                }
                            }
                        }
                        IkGui.endTable();
                        if (multiWindow[0]) {
                            IkGui.popID();
                        }
                        if (multiWindow[0] || instance == 1) {
                            IkGui.end();
                        }
                    }
                    retest[0] = false;
                });
        for (int variant = 0; variant < 2 * 2 * 2; ++variant) {
            multiWindow[0] = (variant & 1) != 0;
            sideBySide[0] = (variant & 2) != 0;
            differSizes[0] = (variant & 4) != 0;
            // Not applicable
            if (!multiWindow[0] && (sideBySide[0] || differSizes[0])) {
                continue;
            }
            // Upstream notes that resizing columns of instances with different sizes isn't perfect
            if (differSizes[0]) {
                continue;
            }
            final String d = "variant " + variant;
            ctx.yieldFrames(2);
            final Table table =
                    IkGuiImplTables.tableFindByID(
                            Hash.getID(
                                    "table",
                                    multiWindow[0] ? 123 : ctx.getID("//Test window - 0")));
            assertNotNull(table, d);
            for (int instance = 0; instance < 2; ++instance) {
                // Click a header
                ctx.itemClick(IkGuiTestContext.tableGetHeaderID(table, 2, instance));

                // Resize a column in the second table. The direction depends on the current width,
                // so the resize stays around the first third of the table across runs.
                final float direction =
                        (table.columnsGivenWidth * 0.3f) < table.columns[0].widthGiven
                                ? -1.0f
                                : 1.0f;
                // Different lengths for different instances
                final float length = 30.0f + 10.0f * instance;
                final float columnWidth = table.columns[0].widthGiven;
                ctx.itemDragWithDelta(
                        IkGuiImplTables.tableGetColumnResizeID(table, 0, instance),
                        length * direction,
                        0.0f);
                // Check the column widths again on the next frame
                retest[0] = true;
                ctx.yieldFrame();
                assertEquals(
                        columnWidth + length * direction,
                        table.columns[0].widthGiven,
                        d + " instance " + instance);
            }

            // References
            clickCounters[0] = 0;
            clickCounters[1] = 0;
            ctx.itemClick(Hash.getID("Button", table.getInstanceID(0)));
            assertTrue(clickCounters[0] == 1 && clickCounters[1] == 0, d);
            ctx.itemClick(Hash.getID("Button", table.getInstanceID(1)));
            assertTrue(clickCounters[0] == 1 && clickCounters[1] == 1, d);
            if (!multiWindow[0]) {
                clickCounters[0] = 0;
                clickCounters[1] = 0;
                ctx.itemClick("//Test window - 0/table/Button");
                assertTrue(clickCounters[0] == 1 && clickCounters[1] == 0, d);
                ctx.itemClick("//Test window - 0/table/##Instances/$$1/Button");
                assertTrue(clickCounters[0] == 1 && clickCounters[1] == 1, d);
            }
        }
    }

    /** table_synced_2: the shared decoration width of synced instances. */
    @Test
    void testTableSynced2() {
        final int[] step = {0};
        final float[] width = {0};
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.beginTable(
                            "tab", 2, TableFlags.RESIZABLE | TableFlags.SCROLL_Y, 0.0f, 0.0f)) {
                        IkGui.tableNextRow();
                        IkGui.tableNextColumn();
                        if (step[0] == 1) {
                            width[0] = IkGui.getContentRegionAvailable().x;
                        }
                        if (step[0] == 2) {
                            ctx.checkEquals(width[0], IkGui.getContentRegionAvailable().x, "width");
                        }
                        // This used to make the parent column width flicker upstream
                        IkGui.beginChild("foo", 0, -2);
                        IkGui.endChild();
                        IkGui.tableNextColumn();
                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.windowResize("", 300, 300);
        // Write
        step[0] = 1;
        ctx.yieldFrames(2);
        // Check, while resizing only vertically
        step[0] = 2;
        ctx.windowResize("", 300, 200);
        ctx.windowResize("", 300, 100);
    }

    /** table_synced_3_autofit: auto-fitting synced instances, including an indented one. */
    @Test
    void testTableSynced3Autofit() {
        final int[] step = {0};
        final IkBoolean doChecks = new IkBoolean(false);
        ctx.setGui(
                () -> {
                    final var style = IkGuiInternal.context.style.variable;
                    final float indent = 40.0f;
                    final float width0 = IkGui.calcTextSize("Very very long text 1").x;
                    final float width1 = step[0] == 0 ? IkGui.calcTextSize("Text 1").x : width0;
                    // Upstream marks the cell spacing as needing a fix
                    final float cellSpacing = 2;
                    final float windowWidth =
                            (width0 * 2)
                                    + (style.cellPadding.x * 4)
                                    + (cellSpacing * 2) * indent
                                    + (style.windowPadding.x * 2.0f);
                    IkGui.setNextWindowSize(windowWidth, 0.0f, Condition.APPEARING);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.checkbox("Do checks", doChecks);
                    IkGui.sliderInt("Step", step, 0, 1);
                    final boolean check = doChecks.get();
                    final java.util.function.BiConsumer<Float, String> checkAvail =
                            (expected, d) -> {
                                if (check) {
                                    ctx.checkEquals(
                                            expected, IkGui.getContentRegionAvailable().x, d);
                                }
                            };
                    final int flags = TableFlags.BORDERS | TableFlags.SIZING_FIXED_FIT;
                    if (IkGui.beginTable("test_table", 2, flags)) {
                        IkGui.tableNextColumn();
                        IkGui.text("Very very long text 1");
                        checkAvail.accept(width0, "first table column 0");
                        IkGui.tableNextColumn();
                        IkGui.text("Text 1");
                        checkAvail.accept(width1, "first table column 1");
                        IkGui.endTable();
                    }
                    for (int n = 0; n < 2; ++n) {
                        // The third table is indented. The table width logic stores absolute
                        // coordinates, so this is worth checking.
                        if (n == 1) {
                            IkGui.indent(indent);
                        }
                        if (IkGui.beginTable("test_table", 2, flags)) {
                            IkGui.tableNextColumn();
                            checkAvail.accept(width0, "table " + n + " column 0");
                            IkGui.text("Text 1");
                            IkGui.tableNextColumn();
                            checkAvail.accept(width1, "table " + n + " column 1");
                            IkGui.text("Text 2");
                            IkGui.endTable();
                        }
                    }
                    IkGui.unindent(indent);
                    if (step[0] == 1 && IkGui.beginTable("test_table", 2, flags)) {
                        IkGui.tableNextColumn();
                        checkAvail.accept(width0, "last table column 0");
                        IkGui.text("Text 1");
                        IkGui.tableNextColumn();
                        IkGui.text("Very very long text 1");
                        checkAvail.accept(width1, "last table column 1");
                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        for (int s = 0; s < 2; ++s) {
            step[0] = s;
            doChecks.set(false);
            ctx.yieldFrames(2);
            doChecks.set(true);
            ctx.yieldFrames(2);
        }
    }

    /** table_two_tables_in_tooltip: two tables in a tooltip don't keep expanding it. */
    @Test
    void testTableTwoTablesInTooltip() {
        final float[] tooltipWidth = {0};
        ctx.setGui(
                () -> {
                    IkGui.begin("Bug Report", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.button("Test Tooltip");
                    if (IkGui.isItemHovered()) {
                        IkGui.beginTooltip();
                        for (int i = 0; i < 2; ++i) {
                            if (IkGui.beginTable("table" + i, 2)) {
                                IkGui.tableSetupColumn("Header1");
                                IkGui.tableSetupColumn("Header2");
                                IkGui.tableHeadersRow();
                                IkGui.tableNextRow();
                                IkGui.tableSetColumnIndex(0);
                                IkGui.textUnformatted("Test1");
                                IkGui.tableSetColumnIndex(1);
                                IkGui.textUnformatted("Test2");
                                IkGui.endTable();
                            }
                        }
                        tooltipWidth[0] = IkGuiInternal.context.windowCurrent.size.x;
                        IkGui.endTooltip();
                    }
                    IkGui.end();
                });
        ctx.setRef("Bug Report");
        ctx.mouseMove("Test Tooltip");
        ctx.sleep(0.5f);
        final float width = tooltipWidth[0];
        for (int n = 0; n < 3; ++n) {
            ctx.yieldFrame();
            assertEquals(width, tooltipWidth[0], "frame " + n);
        }
    }

    /** The specs of one column, for tests that change the columns a table submits. */
    private static class TableColumnSpecs {
        final String label;
        final int flags;
        final float widthOrWeight;

        TableColumnSpecs(String label, int flags, float widthOrWeight) {
            this.label = label == null ? "" : label;
            this.flags = flags;
            this.widthOrWeight = widthOrWeight;
        }
    }

    /**
     * Column specs for a table, for tests that change the columns a table submits. Upstream has two
     * sets of specs and an editor UI for interactive use, which the ported tests don't need.
     */
    private static class TableSpecsVars {
        int tableFlags;
        final List<TableColumnSpecs> columns = new ArrayList<>();
        String tableName;

        void clear() {
            columns.clear();
        }

        void addColumn(String label) {
            addColumn(label, 0, 0.0f);
        }

        void addColumn(String label, int flags, float widthOrWeight) {
            columns.add(new TableColumnSpecs(label, flags, widthOrWeight));
        }

        void showTable(String name) {
            if (columns.isEmpty() || !IkGui.beginTable(name, columns.size(), tableFlags)) {
                return;
            }
            for (TableColumnSpecs spec : columns) {
                if (!spec.label.isEmpty()) {
                    IkGui.tableSetupColumn(spec.label, spec.flags, spec.widthOrWeight);
                }
            }
            IkGui.tableHeadersRow();
            for (int row = 0; row < 8; row++) {
                IkGui.tableNextRow();
                for (int col = 0; col < columns.size(); col++) {
                    if (IkGui.tableNextColumn()) {
                        IkGui.text(Integer.toString(col));
                    }
                }
            }
            IkGui.endTable();
        }
    }

    /** table_settings_1: saving and loading table settings, for all combinations of changes. */
    @Test
    void testTableSettings1() {
        final int[] windowFlags = {WindowFlags.NO_SAVED_SETTINGS};
        final boolean[] callGetSortSpecs = {false};
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(300, 200, Condition.APPEARING);
                    IkGui.begin("Table Settings", null, windowFlags[0]);
                    if (IkGui.beginTable(
                            "table1",
                            4,
                            TableFlags.RESIZABLE
                                    | TableFlags.HIDEABLE
                                    | TableFlags.REORDERABLE
                                    | TableFlags.SORTABLE)) {
                        IkGui.tableSetupColumn("Col0");
                        IkGui.tableSetupColumn("Col1", TableColumnFlags.DEFAULT_HIDE);
                        IkGui.tableSetupColumn("Col2", TableColumnFlags.WIDTH_FIXED, 50.0f);
                        IkGui.tableSetupColumn("Col3", TableColumnFlags.WIDTH_STRETCH);
                        IkGui.tableHeadersRow();
                        // Test against tableGetSortSpecs() having side effects
                        if (callGetSortSpecs[0]) {
                            IkGui.tableGetSortSpecs();
                        }
                        helperTableSubmitCellsButtonFill(4, 3);
                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        ctx.setRef("Table Settings");
        final int tableID = ctx.getID("table1");

        // The number of tested settings, which is the number of bits used to iterate over all
        // combinations
        final int numberOfSettings = 6;
        for (int mask = 0; mask < (1 << numberOfSettings); mask++) {
            callGetSortSpecs[0] = ((mask >> 0) & 1) == 0;
            final boolean col0SortedDesc = ((mask >> 1) & 1) != 0;
            final boolean col1Hidden = ((mask >> 2) & 1) != 0;
            final boolean col0Reordered = ((mask >> 3) & 1) != 0;
            final boolean col2Resized = ((mask >> 4) & 1) != 0;
            final boolean col3Resized = ((mask >> 5) & 1) != 0;
            final String d = "permutation " + mask;

            final java.util.function.Consumer<Table> checkInitialSettings =
                    table -> {
                        assertEquals(0, table.columns[0].sortOrder, d);
                        assertEquals(-1, table.columns[1].sortOrder, d);
                        assertEquals(-1, table.columns[2].sortOrder, d);
                        assertEquals(-1, table.columns[3].sortOrder, d);
                        assertEquals(SortDirection.ASCENDING, table.columns[0].sortDirection, d);

                        assertTrue(table.columns[0].isEnabled, d);
                        assertFalse(table.columns[1].isEnabled, d);
                        assertTrue(table.columns[2].isEnabled, d);
                        assertTrue(table.columns[3].isEnabled, d);

                        assertEquals(0, table.columns[0].displayOrder, d);
                        assertEquals(1, table.columns[1].displayOrder, d);
                        assertEquals(2, table.columns[2].displayOrder, d);
                        assertEquals(3, table.columns[3].displayOrder, d);

                        assertEquals(50.0f, table.columns[2].widthRequest, d);
                        assertEquals(50.0f, table.columns[2].widthGiven, d);
                        assertEquals(1.0f, table.columns[3].stretchWeight, d);
                    };
            final java.util.function.Consumer<Table> checkModifiedSettings =
                    table -> {
                        assertEquals(0, table.columns[0].sortOrder, d);
                        assertEquals(
                                col0SortedDesc ? SortDirection.DESCENDING : SortDirection.ASCENDING,
                                table.columns[0].sortDirection,
                                d);
                        assertEquals(!col1Hidden, table.columns[1].isEnabled, d);
                        assertEquals(
                                col0Reordered ? (col1Hidden ? 2 : 1) : 0,
                                table.columns[0].displayOrder,
                                d);
                        assertEquals(col0Reordered ? 0 : 1, table.columns[1].displayOrder, d);
                        assertEquals(
                                col0Reordered ? (col1Hidden ? 1 : 2) : 2,
                                table.columns[2].displayOrder,
                                d);
                        assertEquals(col2Resized ? 60.0f : 50.0f, table.columns[2].widthRequest, d);
                        assertEquals(col2Resized ? 60.0f : 50.0f, table.columns[2].widthGiven, d);
                        assertEquals(col3Resized ? 0.2f : 1.0f, table.columns[3].stretchWeight, d);
                    };

            // Discard the previous table state, and rebuild the table on the next frame
            IkGuiTestContext.tableDiscardInstanceAndSettings(tableID);
            ctx.yieldFrame();

            // 1/4: check the initial settings
            Table table = IkGuiImplTables.tableFindByID(tableID);
            checkInitialSettings.accept(table);

            // Modify the table. Upstream notes this should eventually simulate user inputs.
            if (col0SortedDesc) {
                table.columns[0].sortOrder = 0;
                table.columns[0].sortDirection = SortDirection.DESCENDING;
            }
            table.columns[1].isEnabled = !col1Hidden;
            table.columns[1].isUserEnabledNextFrame = !col1Hidden;
            // This must take effect before reordering
            ctx.yieldFrame();

            if (col0Reordered) {
                IkGuiImplTables.tableQueueSetColumnDisplayOrder(table, 0, col1Hidden ? 2 : 1);
                ctx.yieldFrame();
            }
            if (col2Resized) {
                table.columns[2].widthRequest = 60;
                table.columns[2].autoFitQueue = 0x00;
            }
            if (col3Resized) {
                table.columns[3].stretchWeight = 0.2f;
                table.columns[3].autoFitQueue = 0x00;
            }
            ctx.yieldFrame();

            // 2/4: check the modified settings
            checkModifiedSettings.accept(table);

            // Save the table settings and destroy the table
            table.flags &= ~TableFlags.NO_SAVED_SETTINGS;
            IkGuiImplTableSettings.tableSaveSettings(table);
            final String tableIniSection =
                    IkGuiTestContext.findIniSection(
                            IkGui.saveIniSettingsToMemory(),
                            String.format("[Table][0x%08X,%d]", table.id, table.columnsCount));
            assertNotNull(tableIniSection, d);

            // Recreate the table with no settings
            IkGuiTestContext.tableDiscardInstanceAndSettings(tableID);
            ctx.yieldFrame();
            table = IkGuiImplTables.tableFindByID(tableID);

            // 3/4: check that the settings were reset
            checkInitialSettings.accept(table);

            // Load the saved settings
            IkGui.loadIniSettingsFromMemory(tableIniSection);
            // Allow loading settings for the test window and table
            windowFlags[0] = WindowFlags.NONE;
            // Load the settings, which is also a chance to mess up the loaded state
            ctx.yieldFrame();
            // Prevent saving settings for the test window and table when testing is done
            windowFlags[0] = WindowFlags.NO_SAVED_SETTINGS;
            // One more frame, so NO_SAVED_SETTINGS takes effect
            ctx.yieldFrame();

            // 4/4: check the table has the saved settings
            checkModifiedSettings.accept(table);
        }
    }

    /** table_settings_2: auto-fit widths of hidden columns across resets and discards. */
    @Test
    void testTableSettings2() {
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(300, 200, Condition.APPEARING);
                    IkGui.begin("Table Settings", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.beginTable(
                            "table1",
                            3,
                            TableFlags.RESIZABLE
                                    | TableFlags.HIDEABLE
                                    | TableFlags.REORDERABLE
                                    | TableFlags.SORTABLE)) {
                        IkGui.tableSetupColumn("Col0");
                        IkGui.tableSetupColumn("Col1");
                        IkGui.tableSetupColumn("Col2", TableColumnFlags.DEFAULT_HIDE);
                        IkGui.tableHeadersRow();
                        helperTableSubmitCellsButtonFix(3, 3);
                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        ctx.setRef("Table Settings");
        final int tableID = ctx.getID("table1");

        IkGuiTestContext.tableDiscardInstanceAndSettings(tableID);
        ctx.yieldFrame();
        Table table = IkGuiImplTables.tableFindByID(tableID);
        assertTrue(table.columns[0].isEnabled);
        assertTrue(table.columns[1].isEnabled);
        assertFalse(table.columns[2].isEnabled);
        ctx.yieldFrame();
        assertEquals(100.0f, table.columns[0].widthAuto);
        assertEquals(100.0f, table.columns[1].widthAuto);
        assertTrue(table.columns[2].widthRequest <= 0.0f);
        assertTrue(table.columns[2].widthAuto <= 0.0f);
        table.columns[2].isUserEnabledNextFrame = true;
        ctx.yieldFrame();
        assertTrue(table.columns[2].widthAuto <= 0.0f);
        ctx.yieldFrame();
        assertEquals(100.0f, table.columns[2].widthAuto);

        // The auto width is preserved on reset
        IkGuiImplTableSettings.tableResetSettings(table);
        ctx.yieldFrame();
        assertFalse(table.columns[2].isEnabled);
        assertEquals(100.0f, table.columns[2].widthAuto);
        table.columns[2].isUserEnabledNextFrame = true;
        ctx.yieldFrame();
        assertEquals(100.0f, table.columns[2].widthAuto);

        // A full discard loses the auto width, but auto-fit should kick in as soon as the column is
        // enabled again
        IkGuiTestContext.tableDiscardInstanceAndSettings(tableID);
        // Get well past any traces of auto-fitting
        ctx.yieldFrames(10);
        table = IkGuiImplTables.tableFindByID(tableID);
        assertFalse(table.columns[2].isEnabled);
        assertTrue(table.columns[2].widthRequest < 0.0f);
        assertEquals(0.0f, table.columns[2].widthAuto);
        table.columns[2].isUserEnabledNextFrame = true;
        ctx.yieldFrame();
        assertTrue(table.columns[2].widthAuto <= 0.0f);
        ctx.yieldFrame();
        assertEquals(100.0f, table.columns[2].widthAuto);
    }

    /** table_settings_3: a table with default settings still gets an entry in the .ini data. */
    @Test
    void testTableSettings3() {
        final boolean[] showContents = {false};
        final int[] windowFlags = {WindowFlags.NO_SAVED_SETTINGS};
        ctx.setGui(
                () -> {
                    if (!showContents[0]) {
                        return;
                    }
                    IkGui.setNextWindowSize(300, 200, Condition.APPEARING);
                    IkGui.begin("Table Settings", null, windowFlags[0]);
                    if (IkGui.beginTable(
                            "table1",
                            3,
                            TableFlags.RESIZABLE | TableFlags.HIDEABLE | TableFlags.REORDERABLE)) {
                        IkGui.tableSetupColumn("Col0", TableColumnFlags.WIDTH_STRETCH, 1.0f);
                        IkGui.tableSetupColumn("Col1", TableColumnFlags.WIDTH_STRETCH, 1.0f);
                        IkGui.tableSetupColumn("Col2", TableColumnFlags.WIDTH_STRETCH, 1.0f);
                        IkGui.tableHeadersRow();
                        helperTableSubmitCellsButtonFix(3, 3);
                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        final int tableID = ctx.getID("//Table Settings/table1");
        IkGuiTestContext.tableDiscardInstanceAndSettings(tableID);
        Table table = IkGuiImplTables.tableFindByID(tableID);
        assertEquals(null, table);

        showContents[0] = true;
        windowFlags[0] &= ~WindowFlags.NO_SAVED_SETTINGS;
        ctx.yieldFrame();
        table = IkGuiImplTables.tableFindByID(tableID);
        assertFalse(table.isSettingsDirty);
        IkGui.saveIniSettingsToMemory();
        ctx.yieldFrame();
        assertEquals(0, table.settingsLoadedFlags);
        // The settings are bound
        final TableSettings settings = IkGuiImplTableSettings.tableGetBoundSettings(table);
        assertNotNull(settings);
        assertEquals(0, settings.saveFlags);
    }

    /** table_settings_4: loading .ini data that is missing a column resets that column. */
    @Test
    void testTableSettings4() {
        final TableSpecsVars vars = new TableSpecsVars();
        vars.tableFlags =
                TableFlags.RESIZABLE
                        | TableFlags.REORDERABLE
                        | TableFlags.HIDEABLE
                        | TableFlags.SORTABLE
                        | TableFlags.ROW_BACKGROUND
                        | TableFlags.BORDERS
                        | TableFlags.SIZING_FIXED_FIT;
        ctx.setGui(
                () -> {
                    final float fontSize = IkGuiInternal.getFontSize();
                    IkGui.setNextWindowSize(fontSize * 35, fontSize * 50, Condition.APPEARING);
                    // Without NO_SAVED_SETTINGS
                    IkGui.begin("Test Window", null, WindowFlags.NONE);
                    vars.showTable("Table1");
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        final int tableID = ctx.getID("Table1");

        vars.clear();
        vars.addColumn("AAA");
        vars.addColumn("BBB");
        vars.addColumn("CCC");
        ctx.yieldFrames(2);

        final String iniData = IkGui.saveIniSettingsToMemory();
        IkGuiTestContext.tableDiscardInstanceAndSettings(tableID);
        vars.addColumn("DDD", 0, 55.0f);
        ctx.yieldFrames(2);
        // The discard means this is a new instance
        final Table table2 = IkGuiImplTables.tableFindByID(tableID);
        assertNotNull(table2);
        assertEquals(55.0f, table2.columns[3].widthRequest);
        assertTrue(table2.columns[3].isUserEnabled);

        ctx.tableResizeColumn(tableID, "DDD", 199.0f);
        ctx.tableSetColumnEnabled(tableID, "DDD", false);
        assertEquals(199.0f, table2.columns[3].widthRequest);
        assertFalse(table2.columns[3].isUserEnabled);

        // The data for column 3 is missing, so it should be reset
        IkGui.loadIniSettingsFromMemory(iniData);
        ctx.yieldFrames(2);
        assertEquals(55.0f, table2.columns[3].widthRequest);
        assertTrue(table2.columns[3].isUserEnabled);
    }

    /** table_topology_change_1: preserving column state when the submitted columns change. */
    @Test
    void testTableTopologyChange1() {
        final TableSpecsVars vars = new TableSpecsVars();
        vars.tableFlags =
                TableFlags.RESIZABLE
                        | TableFlags.REORDERABLE
                        | TableFlags.HIDEABLE
                        | TableFlags.SORTABLE
                        | TableFlags.ROW_BACKGROUND
                        | TableFlags.BORDERS
                        | TableFlags.SIZING_FIXED_FIT;
        ctx.setGui(
                () -> {
                    final float fontSize = IkGuiInternal.getFontSize();
                    IkGui.setNextWindowSize(fontSize * 35, fontSize * 50, Condition.APPEARING);
                    // Without NO_SAVED_SETTINGS
                    IkGui.begin("Test Window", null, WindowFlags.NONE);
                    vars.showTable("Table1");
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        final int tableID = ctx.getID("Table1");

        for (int step = 0; step < 4; step++) {
            // Step 0: change the specs while the table is active, which mostly exercises
            // tableReconcileColumns()
            // Step 1: change the specs after discarding the table instance (reloading from
            // settings), which exercises tableLoadSettingsForColumns()
            // Step 2: change the specs after saving .ini data and discarding both the instance and
            // settings, which simulates an app restart with a modified build
            // Step 3: change the specs while the table is active, with NO_SAVED_SETTINGS
            // (preserved through the runtime reconcile only)
            final String d = "step " + step;
            final int s = step;
            IkGuiTestContext.tableDiscardInstanceAndSettings(tableID);
            final boolean useSettings = step != 3;
            vars.tableFlags |= TableFlags.REORDERABLE;
            vars.tableFlags =
                    useSettings
                            ? vars.tableFlags & ~TableFlags.NO_SAVED_SETTINGS
                            : vars.tableFlags | TableFlags.NO_SAVED_SETTINGS;

            // Simulate the topology change happening "offline", depending on the step
            final Runnable disturb =
                    () -> {
                        if (s == 1) {
                            IkGuiTestContext.tableDiscardInstance(tableID);
                        } else if (s == 2) {
                            // In theory this should be very close to the discard above, but this
                            // is checked for completeness
                            final String iniData = IkGui.saveIniSettingsToMemory();
                            IkGuiTestContext.tableDiscardInstanceAndSettings(tableID);
                            IkGui.loadIniSettingsFromMemory(iniData);
                        }
                    };

            vars.clear();
            vars.addColumn("One");
            vars.addColumn("Two");
            vars.addColumn("Three");
            ctx.yieldFrames(2);
            Table table = IkGuiImplTables.tableFindByID(tableID);
            assertNotNull(table, d);
            if (useSettings) {
                final TableSettings settings = IkGuiImplTableSettings.tableGetBoundSettings(table);
                assertNotNull(settings, d);
                assertEquals(3, settings.columnsCount, d);
            }
            ctx.tableClickHeader(tableID, "One");

            // Resize "Two", sort by "Two", and hide "Three"
            ctx.tableResizeColumn(tableID, "Two", 200.0f);
            assertEquals(200.0f, table.columns[1].widthRequest, d);
            assertEquals(1, table.columns[1].displayOrder, d);
            assertTrue(table.columns[1].sortOrder != 0, d);
            assertEquals(SortDirection.ASCENDING, ctx.tableClickHeader(tableID, "Two"), d);
            assertEquals(0, table.columns[1].sortOrder, d);
            assertEquals(SortDirection.ASCENDING, table.columns[1].sortDirection, d);
            ctx.tableSetColumnEnabled(tableID, "Three", false);
            assertFalse(table.columns[2].isUserEnabled, d);

            disturb.run();

            // Reordering columns
            vars.clear();
            vars.addColumn("One");
            vars.addColumn("Three");
            vars.addColumn("Two");
            ctx.yieldFrames(2);
            table = IkGuiImplTables.tableFindByID(tableID);
            assertNotNull(table, d);
            assertTrue(table.columns[1].widthRequest != 200.0f, d);
            assertEquals(2, table.columns[1].displayOrder, d);
            // "Three" kept its hidden state
            assertFalse(table.columns[1].isUserEnabled, d);
            // "Two" kept its size
            assertEquals(200.0f, table.columns[2].widthRequest, d);
            assertEquals(1, table.columns[2].displayOrder, d);
            // "Two" kept its sort state
            assertEquals(0, table.columns[2].sortOrder, d);
            assertEquals(SortDirection.ASCENDING, table.columns[2].sortDirection, d);
            if (useSettings) {
                final TableSettings settings = IkGuiImplTableSettings.tableGetBoundSettings(table);
                assertNotNull(settings, d);
                assertEquals(3, settings.columnsCount, d);
            }

            disturb.run();

            // Reducing the count
            vars.clear();
            vars.addColumn("Two");
            ctx.yieldFrames(2);
            table = IkGuiImplTables.tableFindByID(tableID);
            assertNotNull(table, d);
            assertEquals(200.0f, table.columns[0].widthRequest, d);
            assertEquals(0, table.columns[0].displayOrder, d);
            assertEquals(0, table.columns[0].sortOrder, d);
            if (useSettings) {
                final TableSettings settings = IkGuiImplTableSettings.tableGetBoundSettings(table);
                assertNotNull(settings, d);
                assertEquals(1, settings.columnsCount, d);
            }

            disturb.run();

            // Growing the count, reintroducing "Three"
            vars.clear();
            vars.addColumn("AAA");
            vars.addColumn("BBB");
            vars.addColumn("Three");
            vars.addColumn("Two");
            ctx.yieldFrames(2);
            table = IkGuiImplTables.tableFindByID(tableID);
            assertNotNull(table, d);
            assertEquals(200.0f, table.columns[3].widthRequest, d);
            // The order is preserved
            assertEquals(0, table.columns[3].displayOrder, d);
            assertEquals(0, table.columns[3].sortOrder, d);
            // New columns are visible
            assertTrue(table.columns[0].isUserEnabled, d);
            assertTrue(table.columns[1].isUserEnabled, d);
            // The settings of "Three" were discarded when it stopped being submitted, so the
            // hidden state is lost, the same as a new column
            assertTrue(table.columns[2].isUserEnabled, d);
            if (useSettings) {
                final TableSettings settings = IkGuiImplTableSettings.tableGetBoundSettings(table);
                assertNotNull(settings, d);
                assertEquals(4, settings.columnsCount, d);
            }

            disturb.run();

            // Remove the labels, so tableSetupColumn() isn't called at all
            vars.clear();
            vars.addColumn(null);
            vars.addColumn(null);
            vars.addColumn(null);
            vars.addColumn(null);
            ctx.yieldFrames(2);
            table = IkGuiImplTables.tableFindByID(tableID);
            assertNotNull(table, d);
            assertEquals(200.0f, table.columns[3].widthRequest, d);
            // The order is preserved
            assertEquals(0, table.columns[3].displayOrder, d);
            assertEquals(0, table.columns[3].sortOrder, d);
            assertTrue(table.columns[0].isUserEnabled, d);
            assertTrue(table.columns[1].isUserEnabled, d);
            assertTrue(table.columns[2].isUserEnabled, d);

            disturb.run();

            // Restoring with duplicates
            vars.clear();
            vars.addColumn("A");
            vars.addColumn("V1###V");
            vars.addColumn("V2###V");
            ctx.yieldFrames(2);
            table = IkGuiImplTables.tableFindByID(tableID);
            ctx.tableResizeColumn(tableID, 0, 100.0f);
            ctx.tableResizeColumn(tableID, 1, 110.0f);
            ctx.tableResizeColumn(tableID, 2, 120.0f);
            assertEquals(100.0f, table.columns[0].widthRequest, d);
            assertEquals(110.0f, table.columns[1].widthRequest, d);
            assertEquals(120.0f, table.columns[2].widthRequest, d);
            disturb.run();
            vars.clear();
            vars.addColumn("A", 0, 66.0f);
            vars.addColumn("NEW", 0, 66.0f);
            vars.addColumn("V1###V", 0, 66.0f);
            vars.addColumn("V2###V", 0, 66.0f);
            ctx.yieldFrames(2);
            table = IkGuiImplTables.tableFindByID(tableID);
            assertEquals(100.0f, table.columns[0].widthRequest, d);
            assertEquals(66.0f, table.columns[1].widthRequest, d);
            assertEquals(110.0f, table.columns[2].widthRequest, d);
            // Picks the first available match by ID
            assertEquals(120.0f, table.columns[3].widthRequest, d);

            // Restoring with duplicates (alternate). This tends to check for subtle differences
            // between tableReconcileColumns() and the tableLoadSettingsForColumns() path.
            vars.clear();
            vars.addColumn("x");
            vars.addColumn("y");
            vars.addColumn("z");
            ctx.yieldFrames(2);
            table = IkGuiImplTables.tableFindByID(tableID);
            ctx.tableResizeColumn(tableID, 0, 100.0f);
            ctx.tableResizeColumn(tableID, 1, 110.0f);
            ctx.tableResizeColumn(tableID, 2, 120.0f);
            disturb.run();
            vars.clear();
            vars.addColumn("y", 0, 66.0f);
            vars.addColumn("y", 0, 67.0f);
            vars.addColumn("y", 0, 68.0f);
            ctx.yieldFrames(2);
            table = IkGuiImplTables.tableFindByID(tableID);
            assertEquals(110.0f, table.columns[0].widthRequest, d);
            // The remaining ones
            assertEquals(100.0f, table.columns[1].widthRequest, d);
            assertEquals(120.0f, table.columns[2].widthRequest, d);

            disturb.run();

            // Duplicates
            vars.clear();
            vars.addColumn("AA");
            vars.addColumn("AA");
            vars.addColumn("BB");
            vars.addColumn("BB");
            ctx.yieldFrames(2);
            table = IkGuiImplTables.tableFindByID(tableID);
            ctx.tableResizeColumn(tableID, 0, 70.0f);
            ctx.tableResizeColumn(tableID, 1, 80.0f);
            ctx.tableResizeColumn(tableID, 2, 90.0f);
            ctx.tableResizeColumn(tableID, 3, 100.0f);
            disturb.run();
            ctx.yieldFrames(2);
            table = IkGuiImplTables.tableFindByID(tableID);
            assertEquals(70.0f, table.columns[0].widthRequest, d);
            assertEquals(80.0f, table.columns[1].widthRequest, d);
            assertEquals(90.0f, table.columns[2].widthRequest, d);
            assertEquals(100.0f, table.columns[3].widthRequest, d);

            // Non-reorderable tables (#9570)
            vars.tableFlags &= ~TableFlags.REORDERABLE;
            for (int subStep = 0; subStep < 2; subStep++) {
                vars.clear();
                vars.addColumn("Months");
                vars.addColumn("CC");
                vars.addColumn("DD");
                ctx.yieldFrames(2);
                table = IkGuiImplTables.tableFindByID(tableID);
                for (int n = 0; n < 3; n++) {
                    assertEquals(n, table.columns[n].displayOrder, d);
                }
                if (subStep == 1) {
                    ctx.tableResizeColumn(tableID, 0, 70.0f);
                    ctx.tableResizeColumn(tableID, 1, 72.0f);
                }

                vars.clear();
                vars.addColumn("Months");
                vars.addColumn("AA");
                vars.addColumn("BB");
                vars.addColumn("CC");
                vars.addColumn("DD");
                ctx.yieldFrames(2);
                table = IkGuiImplTables.tableFindByID(tableID);
                for (int n = 0; n < 3; n++) {
                    assertEquals(n, table.columns[n].displayOrder, d + " sub step " + subStep);
                }
            }
        }
    }

    /**
     * Get the size of a window's item width stack. The stack is a FloatArrayList from Ikala-Core,
     * which isn't on the test classpath, so this uses reflection.
     *
     * @param window The window.
     * @return The number of entries in the stack.
     */
    private static int itemWidthStackSize(Window window) {
        try {
            final Object stack = Window.class.getField("itemWidthStack").get(window);
            return (int) stack.getClass().getMethod("size").invoke(stack);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** table_item_width: item widths inside tables, and the item width stack around them. */
    @Test
    void testTableItemWidth() {
        final int[] int1 = {0};
        final int[] intArray = {0, 0};
        ctx.setGui(
                () -> {
                    final var g = IkGuiInternal.context;
                    IkGui.setNextWindowSize(300, 200, Condition.ALWAYS);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    final Window window = g.windowCurrent;
                    IkGui.pushItemWidth(50.0f);
                    ctx.checkEquals(50.0f, window.currentItemWidth, "pushed width");
                    if (IkGui.beginTable("table1", 2, TableFlags.BORDERS)) {
                        IkGui.tableNextRow();
                        IkGui.tableSetColumnIndex(0);
                        final float columnWidth = IkGui.getContentRegionAvailable().x;
                        IkGui.dragInt("row 1 item 1", int1);
                        IkGui.pushItemWidth(-Float.MIN_VALUE);
                        ctx.checkEquals(columnWidth, IkGui.calcItemWidth(), "row 1 calc width");
                        IkGui.dragInt("##row 1 item 2", int1);
                        ctx.checkEquals(
                                columnWidth, g.lastItemData.rect.getWidth(), "row 1 item width");

                        IkGui.tableNextRow();
                        IkGui.tableSetColumnIndex(0);
                        IkGui.dragInt("##row 1 item 1", int1);
                        ctx.checkEquals(columnWidth, IkGui.calcItemWidth(), "row 2 calc width");
                        ctx.checkEquals(
                                columnWidth, g.lastItemData.rect.getWidth(), "row 2 item width");
                        // popItemWidth() is intentionally left out, as this is currently allowed

                        IkGui.endTable();
                    }
                    ctx.checkEquals(50.0f, window.currentItemWidth, "width after table");
                    ctx.checkEquals(1, itemWidthStackSize(window), "stack size after table");
                    IkGui.popItemWidth();

                    // popItemWidth() at the bottom of the stack doesn't restore a wrong default
                    // (#3760)
                    if (IkGui.beginTable("table2", 2, TableFlags.BORDERS)) {
                        IkGui.tableNextRow();
                        IkGui.tableSetColumnIndex(0);

                        final float defaultColumnWidth = window.currentItemWidth;

                        IkGui.pushItemWidth(60.0f);
                        ctx.checkEquals(60.0f, IkGui.calcItemWidth(), "table2 calc width");
                        IkGui.dragInt2("##row 1 item 1", intArray);
                        ctx.checkEquals(60.0f, g.lastItemData.rect.getWidth(), "table2 item width");
                        ctx.checkEquals(60.0f, window.currentItemWidth, "table2 current width");

                        IkGui.tableNextRow();
                        IkGui.tableSetColumnIndex(0);
                        ctx.checkEquals(60.0f, window.currentItemWidth, "table2 row 2 width");
                        IkGui.popItemWidth();
                        ctx.checkEquals(
                                defaultColumnWidth, window.currentItemWidth, "table2 popped width");

                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        ctx.yieldFrames(3);
    }

    /** table_sorting: sort directions, multi-sort, tristate and per-column sort flags. */
    @Test
    void testTableSorting() {
        final int[] tableFlags = {TableFlags.SORTABLE | TableFlags.SORT_MULTI};
        final int[] column0Flags = {TableColumnFlags.NONE};
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(600, 80, Condition.APPEARING);
                    IkGui.begin("Test window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.beginTable("table1", 6, tableFlags[0])) {
                        IkGui.tableSetupColumn("Default", column0Flags[0]);
                        IkGui.tableSetupColumn(
                                "PreferSortAscending", TableColumnFlags.PREFER_SORT_ASCENDING);
                        IkGui.tableSetupColumn(
                                "PreferSortDescending", TableColumnFlags.PREFER_SORT_DESCENDING);
                        IkGui.tableSetupColumn("NoSort", TableColumnFlags.NO_SORT);
                        IkGui.tableSetupColumn(
                                "NoSortAscending", TableColumnFlags.NO_SORT_ASCENDING);
                        IkGui.tableSetupColumn(
                                "NoSortDescending", TableColumnFlags.NO_SORT_DESCENDING);
                        IkGui.tableHeadersRow();
                        helperTableSubmitCellsButtonFill(6, 1);
                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test window");
        final int tableID = ctx.getID("table1");
        final Table table = IkGuiImplTables.tableFindByID(tableID);
        ctx.yieldFrame();
        final int shift = KeyModFlags.SHIFT;

        // The table has no default sorting flags, so check the implicit default sorting
        TableSortSpecs sortSpecs = IkGuiTestContext.tableGetSortSpecs(tableID);
        assertNotNull(sortSpecs);
        assertEquals(1, sortSpecs.specsCount);
        assertEquals(0, sortSpecs.specs[0].columnIndex);
        assertEquals(0, sortSpecs.specs[0].sortOrder);
        assertEquals(SortDirection.ASCENDING, sortSpecs.specs[0].sortDirection);

        // Sorted implicitly by calling tableGetSortSpecs()
        assertEquals(SortDirection.DESCENDING, ctx.tableClickHeader(tableID, "Default"));
        assertEquals(SortDirection.ASCENDING, ctx.tableClickHeader(tableID, "Default"));
        assertEquals(SortDirection.ASCENDING, ctx.tableClickHeader(tableID, "PreferSortAscending"));
        assertEquals(
                SortDirection.DESCENDING, ctx.tableClickHeader(tableID, "PreferSortAscending"));

        // Not holding shift doesn't multi-sort
        sortSpecs = IkGuiTestContext.tableGetSortSpecs(tableID);
        assertNotNull(sortSpecs);
        assertEquals(1, sortSpecs.specsCount);

        // Holding shift includes all the sortable columns in the multi-sort
        assertEquals(
                SortDirection.DESCENDING,
                ctx.tableClickHeader(tableID, "PreferSortDescending", shift));
        assertEquals(
                SortDirection.ASCENDING,
                ctx.tableClickHeader(tableID, "PreferSortDescending", shift));
        sortSpecs = IkGuiTestContext.tableGetSortSpecs(tableID);
        assertEquals(2, sortSpecs.specsCount);

        assertEquals(SortDirection.NONE, ctx.tableClickHeader(tableID, "NoSort", shift));
        assertEquals(SortDirection.NONE, ctx.tableClickHeader(tableID, "NoSort", shift));
        sortSpecs = IkGuiTestContext.tableGetSortSpecs(tableID);
        assertEquals(2, sortSpecs.specsCount);

        assertEquals(
                SortDirection.DESCENDING, ctx.tableClickHeader(tableID, "NoSortAscending", shift));
        assertEquals(
                SortDirection.DESCENDING, ctx.tableClickHeader(tableID, "NoSortAscending", shift));
        assertEquals(
                SortDirection.ASCENDING, ctx.tableClickHeader(tableID, "NoSortDescending", shift));
        assertEquals(
                SortDirection.ASCENDING, ctx.tableClickHeader(tableID, "NoSortDescending", shift));
        sortSpecs = IkGuiTestContext.tableGetSortSpecs(tableID);
        assertEquals(4, sortSpecs.specsCount);

        // Disabling multi-sort leaves only one sorted column
        tableFlags[0] = TableFlags.SORTABLE;
        ctx.yieldFrame();
        sortSpecs = IkGuiTestContext.tableGetSortSpecs(tableID);
        assertEquals(1, sortSpecs.specsCount);

        // Disabling sorting completely means no sort specs are returned
        tableFlags[0] = TableFlags.NONE;
        ctx.yieldFrame();
        assertEquals(null, IkGuiTestContext.tableGetSortSpecs(tableID));

        // Tristate mode. Upstream doesn't check for null specs here, as the settings are preserved
        // in the columns and restored on the NONE to SORTABLE transition.
        tableFlags[0] = TableFlags.SORTABLE | TableFlags.SORT_TRISTATE;
        ctx.yieldFrame();
        assertEquals(SortDirection.ASCENDING, ctx.tableClickHeader(tableID, "Default"));
        assertEquals(SortDirection.DESCENDING, ctx.tableClickHeader(tableID, "Default"));
        assertEquals(SortDirection.NONE, ctx.tableClickHeader(tableID, "Default"));
        assertEquals(SortDirection.ASCENDING, ctx.tableClickHeader(tableID, "Default"));

        tableFlags[0] = TableFlags.SORTABLE | TableFlags.SORT_TRISTATE | TableFlags.SORT_MULTI;
        ctx.yieldFrame();

        assertEquals(SortDirection.ASCENDING, ctx.tableClickHeader(tableID, "PreferSortAscending"));
        sortSpecs = IkGuiTestContext.tableGetSortSpecs(tableID);
        assertEquals(1, sortSpecs.specsCount);

        // Shift and triple click to turn a second column back into not sorting
        assertEquals(SortDirection.ASCENDING, ctx.tableClickHeader(tableID, "Default", shift));
        assertEquals(SortDirection.DESCENDING, ctx.tableClickHeader(tableID, "Default", shift));
        sortSpecs = IkGuiTestContext.tableGetSortSpecs(tableID);
        assertEquals(2, sortSpecs.specsCount);
        assertEquals(1, sortSpecs.specs[0].columnIndex);
        assertEquals(0, sortSpecs.specs[1].columnIndex);
        assertEquals(SortDirection.NONE, ctx.tableClickHeader(tableID, "Default", shift));
        sortSpecs = IkGuiTestContext.tableGetSortSpecs(tableID);
        assertEquals(1, sortSpecs.specsCount);
        assertEquals(1, sortSpecs.specs[0].columnIndex);

        // Shift and triple click to turn the first column back into not sorting, while preserving
        // the second one (making it first)
        assertEquals(SortDirection.ASCENDING, ctx.tableClickHeader(tableID, "Default", shift));
        assertEquals(
                SortDirection.DESCENDING,
                ctx.tableClickHeader(tableID, "PreferSortAscending", shift));
        sortSpecs = IkGuiTestContext.tableGetSortSpecs(tableID);
        assertEquals(2, sortSpecs.specsCount);
        assertEquals(1, sortSpecs.specs[0].columnIndex);
        assertEquals(0, sortSpecs.specs[1].columnIndex);
        assertEquals(
                SortDirection.NONE, ctx.tableClickHeader(tableID, "PreferSortAscending", shift));
        sortSpecs = IkGuiTestContext.tableGetSortSpecs(tableID);
        assertEquals(1, sortSpecs.specsCount);
        assertEquals(0, sortSpecs.specs[0].columnIndex);

        // Tristate with the per-column NO_SORT_ASCENDING / NO_SORT_DESCENDING flags
        column0Flags[0] = TableColumnFlags.NO_SORT_ASCENDING;
        ctx.yieldFrame();
        sortSpecs = IkGuiTestContext.tableGetSortSpecs(tableID);
        assertEquals(0, sortSpecs.specs[0].columnIndex);
        assertEquals(SortDirection.DESCENDING, sortSpecs.specs[0].sortDirection);
        assertEquals(SortDirection.NONE, ctx.tableClickHeader(tableID, "Default"));
        assertEquals(SortDirection.DESCENDING, ctx.tableClickHeader(tableID, "Default"));
        column0Flags[0] = TableColumnFlags.NO_SORT_DESCENDING;
        ctx.yieldFrame();
        sortSpecs = IkGuiTestContext.tableGetSortSpecs(tableID);
        assertEquals(0, sortSpecs.specs[0].columnIndex);
        assertEquals(SortDirection.ASCENDING, sortSpecs.specs[0].sortDirection);
        assertEquals(SortDirection.NONE, ctx.tableClickHeader(tableID, "Default"));
        assertEquals(SortDirection.ASCENDING, ctx.tableClickHeader(tableID, "Default"));

        // Disable all sorting
        column0Flags[0] = TableColumnFlags.NONE;
        assertEquals(SortDirection.DESCENDING, ctx.tableClickHeader(tableID, "Default"));
        assertEquals(SortDirection.NONE, ctx.tableClickHeader(tableID, "Default"));
        sortSpecs = IkGuiTestContext.tableGetSortSpecs(tableID);
        assertEquals(0, sortSpecs.specsCount);

        // Disable the SORT_TRISTATE flag
        tableFlags[0] &= ~TableFlags.SORT_TRISTATE;
        ctx.yieldFrame();
        sortSpecs = IkGuiTestContext.tableGetSortSpecs(tableID);
        assertEquals(1, sortSpecs.specsCount);

        // Updating the sort direction when the column flags change
        final TableColumn column0 = table.columns[0];
        tableFlags[0] = TableFlags.SORTABLE;
        assertEquals(0, column0.flags & TableColumnFlags.NO_SORT);
        column0Flags[0] = TableColumnFlags.NO_SORT_ASCENDING | TableColumnFlags.NO_SORT_DESCENDING;
        ctx.yieldFrame();
        assertTrue((column0.flags & TableColumnFlags.NO_SORT) != 0);

        column0.sortDirection = SortDirection.ASCENDING;
        column0Flags[0] = TableColumnFlags.NO_SORT_ASCENDING;
        ctx.yieldFrame();
        assertEquals(SortDirection.DESCENDING, column0.sortDirection);

        column0.sortDirection = SortDirection.DESCENDING;
        column0Flags[0] = TableColumnFlags.NO_SORT_DESCENDING;
        ctx.yieldFrame();
        assertEquals(SortDirection.ASCENDING, column0.sortDirection);
    }

    /** table_freezing: freezing rows and columns of a scrolling table. */
    @Test
    void testTableFreezing() {
        final int[] freezeColumns = {0};
        final int[] freezeRows = {0};
        final boolean[] rowVisible = new boolean[5];
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    java.util.Arrays.fill(rowVisible, false);
                    final int columnCount = 20;

                    // For single digit columns
                    final float column00ApproxWidth = IkGui.calcTextSize("2,22").x;
                    // For double digit columns
                    final float column10ApproxWidth = IkGui.calcTextSize("22,22").x;
                    // The table is narrower than its contents
                    final float tableWidth =
                            column00ApproxWidth * (10 - 5) + column10ApproxWidth * 10;

                    final int flags =
                            TableFlags.SCROLL_X
                                    | TableFlags.SCROLL_Y
                                    | TableFlags.BORDERS
                                    | TableFlags.ROW_BACKGROUND;
                    final float height =
                            (IkGui.getTextLineHeight()
                                            + IkGuiInternal.context.style.variable.cellPadding.y
                                                    * 2)
                                    * 7;
                    if (IkGui.beginTable("table1", columnCount, flags, tableWidth, height)) {
                        IkGui.tableSetupScrollFreeze(freezeColumns[0], freezeRows[0]);
                        for (int i = 0; i < columnCount; i++) {
                            IkGui.tableSetupColumn("Col" + i);
                        }
                        IkGui.tableHeadersRow();

                        for (int line = 0; line < 15; line++) {
                            IkGui.tableNextRow();
                            for (int column = 0; column < columnCount; column++) {
                                if (!IkGui.tableSetColumnIndex(column)) {
                                    continue;
                                }
                                IkGui.textUnformatted(line + "," + column);
                                if (line < 5) {
                                    rowVisible[line] |= IkGui.isItemVisible();
                                }
                            }
                        }
                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        final Table table = IkGuiImplTables.tableFindByID(ctx.getID("table1"));

        // Reset the scroll, if there is any
        ctx.scrollToX(table.outerWindow, 0.0f);
        ctx.scrollToY(table.outerWindow, 0.0f);
        ctx.scrollToX(table.innerWindow, 0.0f);
        ctx.scrollToY(table.innerWindow, 0.0f);
        ctx.yieldFrame();

        // No initial freezing
        assertEquals(0, table.freezeColumnsRequest);
        assertEquals(0, table.freezeColumnsCount);
        assertEquals(0, table.freezeRowsRequest);
        assertEquals(0, table.freezeRowsCount);

        // The first five columns and rows are visible at the start
        final float column0Width = table.columns[0].widthGiven;
        for (int i = 0; i < 5; i++) {
            assertTrue(table.columns[i].isVisibleX, "column " + i);
            assertTrue(rowVisible[i], "row " + i);
        }

        // Scroll to the bottom right of the table
        ctx.scrollToX(table.innerWindow, table.innerWindow.scrollMax.x);
        ctx.scrollToY(table.innerWindow, table.innerWindow.scrollMax.y);
        ctx.yieldFrame();

        // The first five columns and rows are no longer visible
        for (int i = 0; i < 5; i++) {
            assertFalse(table.columns[i].isVisibleX, "column " + i);
            assertFalse(rowVisible[i], "row " + i);
        }

        // The clipped column 0 didn't get smaller
        assertEquals(column0Width, table.columns[0].widthGiven);

        // Freezing rows
        for (int freezeCount = 1; freezeCount <= 3; freezeCount++) {
            freezeColumns[0] = 0;
            freezeRows[0] = freezeCount;
            ctx.yieldFrame();
            assertEquals(freezeCount, table.freezeRowsRequest);
            assertEquals(freezeCount, table.freezeRowsCount);

            // The first row is the headers
            if (freezeCount >= 1) {
                assertTrue(table.isUsingHeaders);
            }

            // The other rows are content
            if (freezeCount >= 2) {
                for (int row = 0; row < 3; row++) {
                    assertEquals(
                            row < freezeCount - 1,
                            rowVisible[row],
                            "freeze " + freezeCount + " row " + row);
                }
            }
        }

        // Freezing columns
        for (int freezeCount = 1; freezeCount <= 3; freezeCount++) {
            freezeColumns[0] = freezeCount;
            freezeRows[0] = 0;
            ctx.yieldFrame();
            assertEquals(freezeCount, table.freezeColumnsRequest);
            assertEquals(freezeCount, table.freezeColumnsCount);
            ctx.yieldFrame();
            assertEquals(column0Width, table.columns[0].widthGiven);

            // Frozen columns are visible
            for (int column = 0; column < 4; column++) {
                assertEquals(
                        column < freezeCount,
                        table.columns[column].isVisibleX,
                        "freeze " + freezeCount + " column " + column);
            }
        }
    }

    /** table_freezing_scroll: navigation scrolling with frozen rows and columns. */
    @Test
    void testTableFreezingScroll() {
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    // Upstream notes that RESIZABLE breaks navigation here, when buttons overlap
                    if (IkGui.beginTable("table1", 2, TableFlags.SCROLL_Y, 300, 300)) {
                        IkGui.tableSetupScrollFreeze(0, 1);
                        IkGui.tableSetupColumn("AAA");
                        IkGui.tableSetupColumn("BBB");
                        IkGui.tableHeadersRow();
                        for (int row = 0; row < 30; ++row) {
                            IkGui.tableNextRow();
                            IkGui.tableNextColumn();
                            IkGui.selectable(
                                    Integer.toString(row), false, SelectableFlags.SPAN_ALL_COLUMNS);
                            IkGui.tableNextColumn();
                            IkGui.text("Example text");
                        }
                        IkGui.endTable();
                    }
                    if (IkGui.beginTable("table2", 30, TableFlags.SCROLL_X, 300, 300)) {
                        IkGui.tableSetupScrollFreeze(1, 0);
                        for (int row = 0; row < 30; ++row) {
                            IkGui.tableNextRow();
                            for (int col = 0; col < 30; col++) {
                                IkGui.tableNextColumn();
                                IkGui.button(row + "," + col);
                            }
                        }
                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        final var g = IkGuiInternal.context;
        final java.util.function.Consumer<Object> checkIsFullyVisible =
                ref -> {
                    final IkGuiTestContext.ItemInfo info =
                            ref instanceof Integer id
                                    ? ctx.itemInfo(id, "header")
                                    : ctx.itemInfo((String) ref);
                    assertEquals(info.rect.getLeft(), info.rectClipped.getLeft(), ref + " left");
                    assertEquals(info.rect.getTop(), info.rectClipped.getTop(), ref + " top");
                    assertEquals(info.rect.getRight(), info.rectClipped.getRight(), ref + " right");
                    assertEquals(
                            info.rect.getBottom(), info.rectClipped.getBottom(), ref + " bottom");
                };

        {
            final Table table = IkGuiImplTables.tableFindByID(ctx.getID("//Test Window/table1"));
            assertNotNull(table);
            ctx.setRef(table.id);
            ctx.scrollToTop(table.innerWindow);

            final int headerA = IkGuiTestContext.tableGetHeaderID(table, "AAA");
            ctx.itemClick(headerA, MouseButton.LEFT);
            assertEquals(headerA, g.navID);
            ctx.keyPress(Key.ARROW_DOWN);
            assertEquals(ctx.getID("0"), g.navID);
            ctx.keyPress(Key.ARROW_DOWN);
            assertEquals(ctx.getID("1"), g.navID);
            ctx.keyPress(Key.ARROW_DOWN, 25);
            assertEquals(ctx.getID("26"), g.navID);
            checkIsFullyVisible.accept("26");
            checkIsFullyVisible.accept(headerA);
            ctx.keyPress(Key.ARROW_UP, 20);
            assertEquals(ctx.getID("6"), g.navID);
            checkIsFullyVisible.accept("6");
            checkIsFullyVisible.accept(headerA);
            ctx.keyPress(Key.ARROW_UP, 5);
            assertEquals(ctx.getID("1"), g.navID);
            checkIsFullyVisible.accept("1");
            checkIsFullyVisible.accept(headerA);
            ctx.keyPress(Key.ARROW_UP);
            assertEquals(ctx.getID("0"), g.navID);
            checkIsFullyVisible.accept("0");
            checkIsFullyVisible.accept(headerA);
        }
        {
            final Table table = IkGuiImplTables.tableFindByID(ctx.getID("//Test Window/table2"));
            assertNotNull(table);
            ctx.setRef(table.id);
            ctx.scrollToTop(table.innerWindow);

            ctx.itemClick("0,0");
            assertEquals(ctx.getID("0,0"), g.navID);
            ctx.keyPress(Key.ARROW_RIGHT);
            assertEquals(ctx.getID("0,1"), g.navID);
            ctx.keyPress(Key.ARROW_RIGHT);
            assertEquals(ctx.getID("0,2"), g.navID);
            ctx.keyPress(Key.ARROW_RIGHT, 25);
            assertEquals(ctx.getID("0,27"), g.navID);
            checkIsFullyVisible.accept("0,27");
            checkIsFullyVisible.accept("0,0");
            ctx.keyPress(Key.ARROW_LEFT, 20);
            assertEquals(ctx.getID("0,7"), g.navID);
            checkIsFullyVisible.accept("0,7");
            checkIsFullyVisible.accept("0,0");
            ctx.keyPress(Key.ARROW_LEFT, 5);
            assertEquals(ctx.getID("0,2"), g.navID);
            checkIsFullyVisible.accept("0,2");
            checkIsFullyVisible.accept("0,0");
        }
    }

    /** table_freezing_column_swap: freezing columns that have been reordered. */
    @Test
    void testTableFreezingColumnSwap() {
        final int[] freezeColumns = {0};
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(250.0f, 100.0f, Condition.APPEARING);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.beginTable(
                            "table1", 4, TableFlags.REORDERABLE | TableFlags.SCROLL_X)) {
                        IkGui.tableSetupScrollFreeze(freezeColumns[0], 0);
                        IkGui.tableSetupColumn("1");
                        IkGui.tableSetupColumn("2");
                        IkGui.tableSetupColumn("3");
                        IkGui.tableSetupColumn("4");
                        IkGui.tableHeadersRow();
                        helperTableSubmitCellsText(4, 5);
                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        final Table table = IkGuiImplTables.tableFindByID(ctx.getID("table1"));

        // The default order is set initially
        assertTrue(table.isDefaultDisplayOrder);
        for (int i = 0; i < table.columnsCount; i++) {
            assertEquals(i, table.displayOrderToIndex[i]);
        }

        ctx.itemDragAndDrop(
                IkGuiTestContext.tableGetHeaderID(table, "1"),
                IkGuiTestContext.tableGetHeaderID(table, "2"));
        ctx.itemDragAndDrop(
                IkGuiTestContext.tableGetHeaderID(table, "3"),
                IkGuiTestContext.tableGetHeaderID(table, "4"));
        assertEquals(1, table.displayOrderToIndex[0]);
        assertEquals(0, table.displayOrderToIndex[1]);
        assertEquals(3, table.displayOrderToIndex[2]);
        assertEquals(2, table.displayOrderToIndex[3]);
        freezeColumns[0] = 2;
        ctx.yieldFrame();
        assertEquals(1, table.displayOrderToIndex[0]);
        assertEquals(0, table.displayOrderToIndex[1]);
        assertEquals(3, table.displayOrderToIndex[2]);
        assertEquals(2, table.displayOrderToIndex[3]);
    }

    /** table_auto_resize: an auto-resizing window fits the size of a table it contains. */
    @Test
    void testTableAutoResize() {
        // Emulates upstream's frame count, which is 0 on the first frame after the warm-up
        final int[] frameCount = {-2};
        final boolean[] finished = {false};
        final boolean[] checked = {false};
        ctx.setGui(
                () -> {
                    final var g = IkGuiInternal.context;
                    final boolean firstGuiFrame = frameCount[0] == -2;
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    if (IkGui.beginTable("table1", 2, TableFlags.SIZING_FIXED_FIT)) {
                        IkGui.tableNextRow();
                        IkGui.tableNextColumn();
                        IkGui.button("test", 50, 0);
                        IkGui.tableNextColumn();
                        IkGui.button("test2", 101, 0);
                        final Table table = g.currentTable;
                        final Window window = g.windowCurrent;
                        if (!firstGuiFrame && !finished[0]) {
                            ctx.checkEquals(50.0f, table.columns[0].widthAuto, "column 0 auto");
                            ctx.checkEquals(
                                    50.0f, table.columns[0].widthRequest, "column 0 request");
                            ctx.check(table.columns[0].widthGiven <= 50.0f, "column 0 given");
                            ctx.checkEquals(101.0f, table.columns[1].widthAuto, "column 1 auto");
                            ctx.checkEquals(
                                    101.0f, table.columns[1].widthRequest, "column 1 request");
                            ctx.check(table.columns[1].widthGiven <= 101.0f, "column 1 given");
                        }
                        if (frameCount[0] == 1 && !finished[0]) {
                            ctx.checkEquals(
                                    table.columnsAutoFitWidth,
                                    window.contentSize.x,
                                    "content size");
                            checked[0] = true;
                            finished[0] = true;
                        }
                        IkGui.endTable();
                    }
                    IkGui.end();
                    frameCount[0]++;
                });
        ctx.yieldFrames(4);
        assertTrue(checked[0]);
    }

    /** table_reported_size: an auto-resizing window fits the size of a table it contains. */
    @Test
    void testTableReportedSize() {
        final int[] count = {5};
        final int[] step = {0};
        final int[] windowFlags = {WindowFlags.NONE};
        final float[] width = {0};
        ctx.setGui(
                () -> {
                    final var g = IkGuiInternal.context;
                    IkGui.begin(
                            "Test Window", null, WindowFlags.NO_SAVED_SETTINGS | windowFlags[0]);
                    IkGui.text("Step " + step[0] + " columns " + count[0]);
                    IkGui.text("table outer width: " + width[0]);
                    IkGui.text("window content width: " + g.windowCurrent.contentSize.x);
                    final int colRowCount = count[0];
                    if (IkGui.beginTable("table1", colRowCount)) {
                        if (step[0] == 0 || step[0] == 1) {
                            for (int i = 0; i < colRowCount; i++) {
                                IkGui.tableSetupColumn(
                                        "Col" + i, TableColumnFlags.WIDTH_FIXED, 100.0f);
                            }
                            IkGui.tableHeadersRow();
                        }
                        if (step[0] == 2 || step[0] == 3) {
                            // No column width, window auto-fit and an auto-filling button create a
                            // feedback loop
                            for (int i = 0; i < colRowCount; i++) {
                                IkGui.tableSetupColumn("Col" + i);
                            }
                            IkGui.tableHeadersRow();
                        }
                        for (int row = 0; row < colRowCount; row++) {
                            IkGui.tableNextRow();
                            for (int column = 0; column < colRowCount; column++) {
                                IkGui.tableSetColumnIndex(column);
                                final float w = IkGui.getContentRegionAvailable().x;
                                final String label = String.format("%.0f %d,%d", w, row, column);
                                if (step[0] == 0 || step[0] == 2) {
                                    IkGui.button(label, -Float.MIN_VALUE, 0.0f);
                                } else {
                                    IkGui.button(label, 100.0f, 0.0f);
                                }
                            }
                        }
                        width[0] = g.currentTable.outerRect.getWidth();
                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        final var g = IkGuiInternal.context;
        ctx.setRef("Test Window");
        final Window window = ctx.getWindowByRef("");
        final Table table = IkGuiImplTables.tableFindByID(ctx.getID("table1"));

        for (int s = 0; s < 4; s++) {
            count[0] = 5;
            step[0] = s;
            windowFlags[0] = WindowFlags.NONE;
            ctx.yieldFrames(2);

            // Upstream notes that things fail if the width is too small and gets clipped
            ctx.windowResize("", 20.0f * count[0], 50);

            for (int columnStep = 0; columnStep < 3; columnStep++) {
                final String d = "step " + s + " with " + count[0] + " columns";
                windowFlags[0] = WindowFlags.ALWAYS_AUTO_RESIZE;
                ctx.yieldFrame();

                // The window is big enough to contain the entire table
                assertTrue(window.getRect().contains(window.rectContent), d);
                assertEquals(window.rectContent.getLeft(), table.outerRect.getLeft(), d);
                assertEquals(window.rectContent.getRight(), table.outerRect.getRight(), d);
                assertEquals(window.rectContent.getBottom(), table.outerRect.getBottom(), d);

                final float expectedTableWidth =
                        count[0] * 100.0f
                                + (count[0] - 1) * (g.style.variable.cellPadding.x * 2.0f);
                final float expectedTableHeight =
                        count[0] * (IkGui.getFrameHeight() + g.style.variable.cellPadding.y * 2.0f)
                                + (IkGui.getTextLineHeight()
                                        + g.style.variable.cellPadding.y * 2.0f);
                assertEquals(expectedTableHeight, table.outerRect.getHeight(), d);

                if (s == 2) {
                    // No column width specs and an auto-filling button means the table keeps its
                    // width
                    assertTrue(table.outerRect.getWidth() < expectedTableWidth, d);
                    assertTrue(window.contentSizeIdeal.x < expectedTableWidth, d);
                } else {
                    assertEquals(expectedTableWidth, table.outerRect.getWidth(), d);
                    assertEquals(expectedTableWidth, window.contentSizeIdeal.x, d);
                }

                count[0]++;
                // Changing the column count recreates the table
                ctx.yieldFrames(2);
            }
        }
    }

    /** table_reported_size_2: resizable columns report their current size. */
    @Test
    void testTableReportedSize2() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.pushStyleVarFloat2(StyleVariable.CELL_PADDING, 0.0f, 0.0f);
                    if (IkGui.beginTable(
                            "table1", 3, TableFlags.RESIZABLE | TableFlags.SIZING_FIXED_FIT)) {
                        IkGui.tableSetupColumn("", 0, 50.0f);
                        IkGui.tableSetupColumn("", 0, 100.0f);
                        IkGui.tableSetupColumn("", 0, 50.0f);
                        IkGui.tableNextColumn();
                        IkGui.text("Hello");
                        IkGui.tableSetBackgroundColor(
                                TableBackgroundTarget.CELL_BACKGROUND,
                                Color.rgba(255, 0, 0, 50),
                                0);
                        IkGui.tableSetBackgroundColor(
                                TableBackgroundTarget.CELL_BACKGROUND,
                                Color.rgba(0, 255, 0, 50),
                                1);
                        IkGui.tableSetBackgroundColor(
                                TableBackgroundTarget.CELL_BACKGROUND,
                                Color.rgba(0, 0, 255, 50),
                                2);
                        IkGui.endTable();
                    }
                    IkGui.popStyleVar();
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        final int tableID = ctx.getID("table1");
        final Table table = IkGuiImplTables.tableFindByID(tableID);

        final float extraWidth =
                (table.outerPaddingX * 2.0f)
                        + (table.cellSpacingX1 + table.cellSpacingX2)
                                * (table.columnsEnabledCount - 1)
                        + (table.cellPaddingX * 2.0f) * table.columnsEnabledCount;
        ctx.tableResizeColumn(tableID, 1, 100.0f);
        assertEquals(50.0f + 100.0f + 50.0f + extraWidth, table.outerWindow.contentSize.x);

        ctx.tableResizeColumn(tableID, 1, 80.0f);
        assertEquals(50.0f + 80.0f + 50.0f + extraWidth, table.outerWindow.contentSize.x);
    }

    /** table_reported_size_outer: the sizes a table reports to its outer window. */
    @Test
    void testTableReportedSizeOuter() {
        final TableTestingVars vars = new TableTestingVars();
        vars.windowSize.set(300.0f, 300.0f);
        vars.itemSize.set(100.0f, 200.0f);
        vars.tableFlags |= TableFlags.SIZING_FIXED_FIT;
        ctx.setGui(
                () -> {
                    IkGui.pushStyleVarFloat2(StyleVariable.ITEM_SPACING, 0.0f, 0.0f);
                    IkGui.pushStyleVarFloat2(StyleVariable.CELL_PADDING, 0.0f, 0.0f);
                    if (vars.windowSize.x != 0.0f && vars.windowSize.y != 0.0f) {
                        IkGui.setNextWindowSize(
                                vars.windowSize.x, vars.windowSize.y, Condition.ALWAYS);
                    }
                    IkGui.pushStyleVarFloat2(StyleVariable.WINDOW_PADDING, 0.0f, 0.0f);
                    IkGui.begin(
                            "Test Window",
                            null,
                            vars.windowFlags
                                    | WindowFlags.HORIZONTAL_SCROLLBAR
                                    | WindowFlags.NO_SAVED_SETTINGS
                                    | WindowFlags.NO_TITLE_BAR);
                    IkGui.popStyleVar();
                    vars.outTableLayoutSize.set(0.0f, 0.0f);
                    vars.outTableIsItemHovered = false;
                    if (IkGui.beginTable(
                            "table1", 1, vars.tableFlags, vars.outerSize.x, vars.outerSize.y)) {
                        IkGui.tableNextColumn();
                        IkGui.tableSetBackgroundColor(
                                TableBackgroundTarget.ROW_BACKGROUND_0, Color.rgba(0, 255, 0, 20));
                        IkGui.button(
                                String.format(
                                        "-- %dx%d --",
                                        (int) vars.itemSize.x, (int) vars.itemSize.y),
                                vars.itemSize.x,
                                vars.itemSize.y);
                        IkGui.endTable();
                        helperFillBounds(vars);
                    }
                    IkGui.end();
                    IkGui.popStyleVar(2);
                    // Upstream also shows an options window for interactive use
                });
        ctx.setRef("Test Window");
        final Table table = IkGuiImplTables.tableFindByID(ctx.getID("table1"));
        // Smaller than the width of 50 set below
        IkGuiInternal.context.style.variable.windowMinSize.set(10.0f, 10.0f);
        final float fltMin = Float.MIN_VALUE;

        // [1] Widths, non-scrolling. This uses horizontal borders only, to not interfere with the
        // widths.

        // Special case: NO_HOST_EXTEND_X (was outer_size.x == 0.0f), no scroll and no stretch
        // means MinFitX
        vars.tableFlags =
                TableFlags.NO_SAVED_SETTINGS
                        | TableFlags.BORDERS_OUTER_H
                        | TableFlags.SIZING_FIXED_FIT
                        | TableFlags.NO_HOST_EXTEND_X;
        vars.itemSize.set(200.0f, 100.0f);
        vars.windowSize.set(300.0f, 300.0f);
        vars.outerSize.set(0.0f, 0.0f);
        ctx.yieldFrames(3);
        assertEquals(200.0f, table.outerWindow.contentSize.x, "[1a]");
        assertEquals(200.0f, table.outerWindow.contentSizeIdeal.x, "[1a]");
        vars.windowSize.set(50.0f, 300.0f);
        ctx.yieldFrames(2);
        assertEquals(200.0f, table.outerWindow.contentSize.x, "[1b]");
        assertEquals(200.0f, table.outerWindow.contentSizeIdeal.x, "[1b]");

        vars.tableFlags =
                TableFlags.NO_SAVED_SETTINGS
                        | TableFlags.BORDERS_OUTER_H
                        | TableFlags.SIZING_FIXED_FIT;
        vars.windowSize.set(300.0f, 300.0f);
        vars.outerSize.set(-fltMin, 0.0f);
        ctx.yieldFrames(2);
        // 200 rather than 300, since outer_size.x <= 0.0f reports the best fit width
        assertEquals(200.0f, table.outerWindow.contentSize.x, "[1c]");
        assertEquals(200.0f, table.outerWindow.contentSizeIdeal.x, "[1c]");
        vars.windowSize.set(50.0f, 300.0f);
        ctx.yieldFrames(2);
        assertEquals(50.0f, table.outerWindow.contentSize.x, "[1d]");
        assertEquals(200.0f, table.outerWindow.contentSizeIdeal.x, "[1d]");

        vars.windowSize.set(300.0f, 300.0f);
        vars.outerSize.set(-30.0f, 0.0f);
        ctx.yieldFrames(2);
        assertEquals(200.0f, table.outerWindow.contentSize.x, "[1e]");
        assertEquals(200.0f + 30.0f, table.outerWindow.contentSizeIdeal.x, "[1e]");
        vars.windowSize.set(50.0f, 300.0f);
        ctx.yieldFrames(2);
        assertEquals(50.0f - 30.0f, table.outerWindow.contentSize.x, "[1f]");
        assertEquals(200.0f + 30.0f, table.outerWindow.contentSizeIdeal.x, "[1f]");

        vars.windowSize.set(300.0f, 300.0f);
        vars.outerSize.set(100.0f, 0.0f);
        ctx.yieldFrames(2);
        assertEquals(100.0f, table.outerWindow.contentSize.x, "[1g]");
        assertEquals(100.0f, table.outerWindow.contentSizeIdeal.x, "[1g]");

        vars.windowSize.set(300.0f, 300.0f);
        vars.outerSize.set(300.0f, 0.0f);
        ctx.yieldFrames(2);
        assertEquals(300.0f, table.outerWindow.contentSize.x, "[1h]");
        assertEquals(300.0f, table.outerWindow.contentSizeIdeal.x, "[1h]");
        vars.windowSize.set(100.0f, 300.0f);
        ctx.yieldFrames(2);
        assertEquals(300.0f, table.outerWindow.contentSize.x, "[1i]");
        assertEquals(300.0f, table.outerWindow.contentSizeIdeal.x, "[1i]");

        // [2] Heights, non-scrolling

        // outer_size -FLT_MIN, button 200, window 300 -> layout 300, ideal 200
        vars.tableFlags = TableFlags.NO_SAVED_SETTINGS | TableFlags.BORDERS;
        vars.itemSize.set(100.0f, 200.0f);
        vars.outerSize.set(0.0f, -fltMin);
        vars.windowSize.set(300.0f, 300.0f);
        ctx.yieldFrames(2);
        assertEquals(300.0f, vars.outTableLayoutSize.y, "[2a]");
        assertEquals(200.0f, table.outerWindow.contentSize.y, "[2a]");

        // outer_size -FLT_MIN, button 200, window 100 -> layout 200, ideal 200
        vars.tableFlags &= ~TableFlags.NO_HOST_EXTEND_Y;
        vars.outerSize.set(0.0f, -fltMin);
        vars.windowSize.set(300.0f, 100.0f);
        ctx.yieldFrames(2);
        assertEquals(200.0f, vars.outTableLayoutSize.y, "[2b]");
        assertEquals(200.0f, table.outerWindow.contentSize.y, "[2b]");

        // With NO_HOST_EXTEND_Y -> layout 1, ideal 200
        vars.tableFlags |= TableFlags.NO_HOST_EXTEND_Y;
        vars.outerSize.set(0.0f, -fltMin);
        vars.windowSize.set(300.0f, 100.0f);
        ctx.yieldFrames(2);
        assertEquals(100.0f, vars.outTableLayoutSize.y, "[2c]");
        assertEquals(100.0f, table.outerWindow.contentSize.y, "[2c]");
        assertEquals(200.0f, table.outerWindow.contentSizeIdeal.y, "[2c]");

        // outer_size 0, button 200, window 300 -> layout 200, ideal 200
        vars.tableFlags &= ~TableFlags.NO_HOST_EXTEND_Y;
        vars.outerSize.set(0.0f, 0.0f);
        vars.windowSize.set(300.0f, 300.0f);
        ctx.yieldFrames(2);
        assertEquals(200.0f, vars.outTableLayoutSize.y, "[2d]");
        assertEquals(200.0f, table.outerWindow.contentSize.y, "[2d]");
        assertFalse(table.outerWindow.scrollbarY, "[2d]");

        // outer_size 0, button 200, window 100 -> layout 200, ideal 200 (scroll)
        vars.outerSize.set(0.0f, 0.0f);
        vars.windowSize.set(300.0f, 100.0f);
        ctx.yieldFrames(2);
        assertEquals(200.0f, vars.outTableLayoutSize.y, "[2e]");
        assertEquals(200.0f, table.outerWindow.contentSize.y, "[2e]");
        assertTrue(table.outerWindow.scrollbarY, "[2e]");

        // outer_size 100, button 200, window 300 -> layout 200, ideal 200
        vars.outerSize.set(0.0f, 100.0f);
        vars.windowSize.set(300.0f, 300.0f);
        ctx.yieldFrames(2);
        assertEquals(200.0f, vars.outTableLayoutSize.y, "[2f]");
        assertEquals(200.0f, table.outerWindow.contentSize.y, "[2f]");
        assertFalse(table.outerWindow.scrollbarY, "[2f]");

        // outer_size 100, button 200, window 300, NO_HOST_EXTEND_Y -> layout 100, ideal 200
        vars.tableFlags |= TableFlags.NO_HOST_EXTEND_Y;
        vars.outerSize.set(0.0f, 100.0f);
        vars.windowSize.set(300.0f, 300.0f);
        ctx.yieldFrames(2);
        assertEquals(100.0f, vars.outTableLayoutSize.y, "[2g]");
        assertEquals(100.0f, table.outerWindow.contentSize.y, "[2g]");
        assertFalse(table.outerWindow.scrollbarY, "[2g]");

        // outer_size 300, button 200, window 100 -> layout 300, ideal 300 (scroll)
        vars.tableFlags |= TableFlags.NO_HOST_EXTEND_Y;
        vars.outerSize.set(0.0f, 300.0f);
        vars.windowSize.set(300.0f, 100.0f);
        ctx.yieldFrames(2);
        assertEquals(300.0f, vars.outTableLayoutSize.y, "[2h]");
        assertEquals(300.0f, table.outerWindow.contentSize.y, "[2h]");
        assertTrue(table.outerWindow.scrollbarY, "[2h]");

        // Widths, heights and scrolling
        for (int step = 3; step <= 5; step++) {
            final int axis = step == 3 ? 0 : 1;
            final int other = axis ^ 1;
            final String d = "[" + step + "] ";
            final int s = step;
            final Runnable scroll =
                    () -> {
                        if (s == 4) {
                            ctx.scrollToTop(table.innerWindow);
                        } else if (s == 5) {
                            ctx.scrollToBottom(table.innerWindow);
                        }
                    };
            final java.util.function.Supplier<Boolean> scrollbar =
                    () -> axis == 0 ? table.outerWindow.scrollbarX : table.outerWindow.scrollbarY;

            vars.itemSize.setComponent(axis, 200.0f);
            vars.itemSize.setComponent(other, 100.0f);

            vars.tableFlags = TableFlags.NO_SAVED_SETTINGS | TableFlags.SCROLL_X;
            vars.windowFlags = step == 5 ? WindowFlags.NONE : WindowFlags.ALWAYS_AUTO_RESIZE;
            vars.windowSize.set(300.0f, 300.0f);
            // Right or bottom aligned
            vars.outerSize.setComponent(axis, 0.0f);
            vars.outerSize.setComponent(other, 220.0f);
            ctx.yieldFrames(3);
            final Vector2f outerSize =
                    new Vector2f(table.outerRect.getWidth(), table.outerRect.getHeight());
            assertEquals(300.0f, outerSize.get(axis), d + "a");
            assertEquals(200.0f, table.innerWindow.contentSize.get(axis), d + "a");
            assertEquals(200.0f, table.innerWindow.contentSizeIdeal.get(axis), d + "a");
            // 200 rather than 300, since outer_size.x <= 0.0f reports the best fit width
            assertEquals(200.0f, table.outerWindow.contentSize.get(axis), d + "a");
            assertEquals(200.0f, table.outerWindow.contentSizeIdeal.get(axis), d + "a");
            vars.windowSize.setComponent(axis, 100.0f);
            ctx.yieldFrames(2);
            scroll.run();
            assertEquals(200.0f, table.innerWindow.contentSize.get(axis), d + "b");
            assertEquals(200.0f, table.innerWindow.contentSizeIdeal.get(axis), d + "b");
            assertEquals(100.0f, table.outerWindow.contentSize.get(axis), d + "b");
            assertFalse(scrollbar.get(), d + "b");
            assertEquals(200.0f, table.outerWindow.contentSizeIdeal.get(axis), d + "b");

            vars.tableFlags = TableFlags.NO_SAVED_SETTINGS | TableFlags.SCROLL_X;
            vars.windowSize.set(300.0f, 300.0f);
            // Right or bottom aligned
            vars.outerSize.setComponent(axis, -fltMin);
            vars.outerSize.setComponent(other, 0.0f);
            ctx.yieldFrames(2);
            assertEquals(200.0f, table.innerWindow.contentSize.get(axis), d + "c");
            assertEquals(200.0f, table.innerWindow.contentSizeIdeal.get(axis), d + "c");
            assertEquals(200.0f, table.outerWindow.contentSize.get(axis), d + "c");
            assertEquals(200.0f, table.outerWindow.contentSizeIdeal.get(axis), d + "c");
            vars.windowSize.setComponent(axis, 100.0f);
            ctx.yieldFrames(2);
            scroll.run();
            assertEquals(200.0f, table.innerWindow.contentSize.get(axis), d + "d");
            assertEquals(200.0f, table.innerWindow.contentSizeIdeal.get(axis), d + "d");
            assertEquals(100.0f, table.outerWindow.contentSize.get(axis), d + "d");
            assertFalse(scrollbar.get(), d + "d");
            assertEquals(200.0f, table.outerWindow.contentSizeIdeal.get(axis), d + "d");

            vars.tableFlags = TableFlags.NO_SAVED_SETTINGS | TableFlags.SCROLL_X;
            vars.windowSize.set(300.0f, 300.0f);
            vars.outerSize.setComponent(axis, 100.0f);
            vars.outerSize.setComponent(other, 0.0f);
            // The small item size submitted before means column 0 is at its minimum width, which
            // is reported to the inner window
            ctx.yieldFrame();
            // The inner window updates to the small contents width, and column 0 resizes to fit
            ctx.yieldFrame();
            // The inner window updates to the correct contents width
            ctx.yieldFrame();
            scroll.run();
            assertEquals(200.0f, table.innerWindow.contentSize.get(axis), d + "e");
            assertEquals(200.0f, table.innerWindow.contentSizeIdeal.get(axis), d + "e");
            assertEquals(100.0f, table.outerWindow.contentSize.get(axis), d + "e");
            assertEquals(100.0f, table.outerWindow.contentSizeIdeal.get(axis), d + "e");

            vars.tableFlags = TableFlags.NO_SAVED_SETTINGS | TableFlags.SCROLL_X;
            vars.windowSize.set(300.0f, 300.0f);
            vars.outerSize.setComponent(axis, 300.0f);
            vars.outerSize.setComponent(other, 0.0f);
            ctx.yieldFrames(2);
            assertEquals(200.0f, table.innerWindow.contentSize.get(axis), d + "f");
            assertEquals(300.0f, table.outerWindow.contentSize.get(axis), d + "f");
            vars.windowSize.setComponent(axis, 100.0f);
            vars.windowSize.setComponent(other, 300.0f);
            ctx.yieldFrames(2);
            scroll.run();
            assertEquals(200.0f, table.innerWindow.contentSize.get(axis), d + "g");
            assertEquals(300.0f, table.outerWindow.contentSize.get(axis), d + "g");

            if (axis == 1) {
                vars.tableFlags = TableFlags.NO_SAVED_SETTINGS | TableFlags.SCROLL_X;
                vars.windowFlags = WindowFlags.NONE;
                vars.windowSize.y = 200.0f;
                vars.outerSize.set(0.0f, 0.0f);
                vars.itemSize.set(0.0f, 1000.0f);
                ctx.yieldFrames(2);
                assertEquals(1000.0f, table.outerWindow.contentSizeIdeal.get(axis), d + "h");
                scroll.run();
                assertEquals(1000.0f, table.outerWindow.contentSizeIdeal.get(axis), d + "h");
            }
        }

        // A table with SCROLL_Y reserves horizontal space for the vertical scrollbar (#7651)
        vars.tableFlags = TableFlags.NO_SAVED_SETTINGS | TableFlags.SCROLL_Y;
        vars.windowFlags = WindowFlags.ALWAYS_AUTO_RESIZE;
        vars.windowSize.set(0.0f, 0.0f);
        vars.outerSize.set(0.0f, 100.0f);
        vars.itemSize.set(200.0f, 500.0f);
        ctx.yieldFrames(4);
        assertEquals(
                200.0f + table.innerWindow.scrollbarSizes.x, table.outerWindow.size.x, "#7651");
        assertEquals(
                200.0f + table.innerWindow.scrollbarSizes.x,
                table.outerWindow.contentSize.x,
                "#7651");
        ctx.yieldFrame();

        // #9352
        vars.tableFlags = TableFlags.NO_SAVED_SETTINGS | TableFlags.SCROLL_X;
        vars.windowFlags = WindowFlags.NONE;
        vars.windowSize.set(0.0f, 0.0f);
        vars.outerSize.set(0.0f, 0.0f);
        vars.itemSize.set(500.0f, 500.0f);
        ctx.yieldFrames(2);

        ctx.windowResize("", 50, 50);
        // Auto-resize on x
        ctx.windowResize("", -1.0f, 0.0f);
        assertEquals(
                500.0f + IkGuiInternal.context.style.variable.scrollbarSize,
                table.innerWindow.size.x,
                "#9352 x");
        assertEquals(50.0f, table.innerWindow.size.y, "#9352 x");

        // Auto-resize on y
        ctx.windowResize("", 0.0f, -1.0f);
        assertEquals(500.0f, table.innerWindow.size.y, "#9352 y");

        // Auto-resize on both axes at the same time
        ctx.windowResize("", -1.0f, -1.0f);
        assertEquals(500.0f, table.innerWindow.size.x, "#9352 both");
        assertEquals(500.0f, table.innerWindow.size.y, "#9352 both");
        assertEquals(500.0f, table.outerWindow.size.x, "#9352 both");
        assertEquals(500.0f, table.outerWindow.size.y, "#9352 both");
        // Upstream has more checks here marked as broken, for resizing both axes from a small size
        // where a scrollbar appeared
    }

    /**
     * table_reported_size_outer_clipped: a scrolling table doesn't clip itself while the outer
     * window measures its size.
     */
    @Test
    void testTableReportedSizeOuterClipped() {
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(-1.0f, -1.0f, Condition.APPEARING);
                    if (IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS)) {
                        // Not strictly required, but a tab bar is typically a widget that might
                        // add a delay
                        if (IkGui.beginTabBar("Tabs")) {
                            if (IkGui.beginTabItem("Tab1")) {
                                if (IkGui.beginTable(
                                        "Table1",
                                        4,
                                        TableFlags.SCROLL_Y
                                                | TableFlags.NO_SAVED_SETTINGS
                                                | TableFlags.RESIZABLE)) {
                                    for (int n = 0; n < 4; n++) {
                                        IkGui.tableSetupColumn("Column" + n);
                                    }
                                    IkGui.tableHeadersRow();
                                    for (int n = 0; n < 4 * 5; n++) {
                                        if (IkGui.tableNextColumn()) {
                                            IkGui.text("Test Item " + n);
                                        }
                                    }
                                    IkGui.endTable();
                                }
                                IkGui.endTabItem();
                            }
                            IkGui.endTabBar();
                        }
                    }
                    IkGui.end();
                });
        ctx.yieldFrames(3);
        ctx.setRef("Test Window");

        final Window window = ctx.getWindowByRef("");
        assertNotNull(window);
        final Vector2f size = new Vector2f(window.size);
        assertTrue(size.x > IkGui.calcTextSize("Column0Column1Column2Column3").x);

        // Auto-resize, and check the size didn't change
        ctx.windowResize("", -1.0f, -1.0f);
        assertEquals(size, window.size);
    }

    /** table_nav_layer: frozen cells use the menu navigation layer while scrolled. */
    @Test
    void testTableNavLayer() {
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    if (IkGui.beginTable(
                            "table1",
                            6,
                            TableFlags.SCROLL_X | TableFlags.SCROLL_Y,
                            400,
                            IkGui.getTextLineHeightWithSpacing() * 5)) {
                        IkGui.tableSetupScrollFreeze(2, 2);
                        helperTableSubmitCellsCustom(
                                6,
                                20,
                                (column, line) -> {
                                    final Window window = IkGuiInternal.context.windowCurrent;
                                    final int layer = window.navLayerCurrent;
                                    if (line < 2 && window.scrollPosition.y > 0.0f) {
                                        ctx.checkEquals(
                                                1, layer, "layer of " + column + "," + line);
                                    } else {
                                        ctx.checkEquals(
                                                0, layer, "layer of " + column + "," + line);
                                    }
                                    IkGui.text(layer + " ............");
                                });
                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        final Table table = IkGuiImplTables.tableFindByID(ctx.getID("table1"));

        ctx.scrollToX(table.innerWindow, 0.0f);
        ctx.scrollToY(table.innerWindow, 0.0f);
        ctx.yieldFrames(2);
        ctx.scrollToX(table.innerWindow, table.innerWindow.scrollMax.x);
        ctx.scrollToY(table.innerWindow, table.innerWindow.scrollMax.y);
        ctx.yieldFrames(2);
    }

    /** table_hidden_output: submitting into hidden and clipped cells. */
    @Test
    void testTableHiddenOutput() {
        // Emulates upstream's frame count, which is 0 on the first frame after the warm-up
        final int[] frameCount = {-2};
        final boolean[] checked = {false};
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(200.0f, 100.0f, Condition.APPEARING);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.beginTable("table1", 30, TableFlags.SCROLL_Y | TableFlags.SCROLL_X)) {
                        final Window window = IkGuiInternal.context.windowCurrent;
                        for (int line = 0; line < 40; line++) {
                            IkGui.tableNextRow();
                            for (int column = 0; column < 30; column++) {
                                final boolean columnVisible = IkGui.tableSetColumnIndex(column);

                                if (line == 0 && column == 29 && frameCount[0] == 2) {
                                    // The last column is scrolled out of view. Upstream would also
                                    // like skipItems to be set here eventually.
                                    ctx.check(!columnVisible, "last column hidden");
                                    ctx.checkEquals(
                                            0.0f, window.rectCurrentClip.getWidth(), "clip width");
                                    checked[0] = true;
                                }

                                if (!columnVisible) {
                                    // Upstream draws into hidden cells too, without testing it
                                    final Vector2f pos = IkGui.getCursorScreenPos();
                                    window.drawList.addCircle(
                                            pos.x, pos.y, 100.0f, Color.rgba(255, 255, 255, 255));
                                    continue;
                                }
                                IkGui.textUnformatted("eek");
                            }
                        }
                        IkGui.endTable();
                    }
                    IkGui.end();
                    frameCount[0]++;
                });
        ctx.yieldFrames(4);
        assertTrue(checked[0]);
    }

    /** table_varying_columns_count: changing the column count of an existing table. */
    @Test
    void testTableVaryingColumnsCount() {
        final TableTestingVars vars = new TableTestingVars();
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window", null, WindowFlags.NO_SAVED_SETTINGS | vars.windowFlags);
                    vars.columnsCount = Math.max(vars.columnsCount, 1);
                    if (IkGui.beginTable("table1", vars.columnsCount, vars.tableFlags)) {
                        helperTableSubmitCellsButtonFix(vars.columnsCount, 7);
                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        vars.windowFlags = WindowFlags.ALWAYS_AUTO_RESIZE;
        ctx.yieldFrame();
        ctx.setRef("Test Window");
        final Table table = IkGuiImplTables.tableFindByID(ctx.getID("table1"));
        final int[] columnCount = {10, 15, 13, 18, 12, 20, 19};
        for (int count : columnCount) {
            vars.columnsCount = count;
            final int oldCount = table.columnsCount;
            final float w0 = oldCount >= 2 ? table.columns[0].widthGiven : -1.0f;
            final float w1 = oldCount >= 2 ? table.columns[1].widthGiven : -1.0f;
            ctx.yieldFrame();
            assertEquals(count, table.columnsCount);
            if (oldCount >= 2) {
                assertEquals(w0, table.columns[0].widthGiven, "count " + count);
                assertEquals(w1, table.columns[1].widthGiven, "count " + count);
            }
            ctx.yieldFrame();
            if (oldCount >= 2) {
                assertEquals(w0, table.columns[0].widthGiven, "count " + count);
                assertEquals(w1, table.columns[1].widthGiven, "count " + count);
            }
        }
    }

    /** table_cov_misc: column naming, inner width, clearing RESIZABLE, and hiding columns. */
    @Test
    void testTableCovMisc() {
        final TableTestingVars vars = new TableTestingVars();
        vars.columnFlags[0] = TableColumnFlags.WIDTH_FIXED;
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(250.0f, 100.0f, Condition.APPEARING);
                    IkGui.begin("Test window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.beginTable(
                            "table1",
                            4,
                            TableFlags.SCROLL_X | TableFlags.HIDEABLE | TableFlags.RESIZABLE,
                            0,
                            0,
                            300.0f)) {
                        IkGui.tableSetupColumn(
                                "One",
                                vars.columnFlags[0],
                                (vars.columnFlags[0] & TableColumnFlags.WIDTH_FIXED) != 0
                                        ? 100.0f
                                        : 0,
                                0);
                        IkGui.tableSetupColumn("Two", vars.columnFlags[1]);
                        IkGui.tableSetupColumn("Three", vars.columnFlags[2]);
                        IkGui.tableSetupColumn(null, vars.columnFlags[3]);
                        IkGui.tableHeadersRow();
                        helperTableSubmitCellsText(4, 5);
                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test window 1");
        final int tableID = ctx.getID("table1");
        final Table table = IkGuiImplTables.tableFindByID(tableID);
        final TableColumn column0 = table.columns[0];
        assertEquals(100.0f, column0.widthRequest);
        assertEquals(100.0f, column0.widthGiven);

        // Column naming
        assertEquals("Three", IkGuiImplTables.tableGetColumnName(table, 2));
        assertEquals("", IkGuiImplTables.tableGetColumnName(table, 3));

        // The inner width parameter of beginTable(). The second check is only true when an inner
        // window is used, because of SCROLL_X.
        assertEquals(300.0f, table.innerWidth);
        assertEquals(table.innerWidth, table.innerWindow.contentSize.x);

        // The resizable flag is cleared when no columns are resizable
        assertTrue((table.flags & TableFlags.RESIZABLE) != 0);
        for (int i = 0; i < table.columnsCount; i++) {
            vars.columnFlags[i] |= TableColumnFlags.NO_RESIZE;
        }
        ctx.yieldFrame();
        assertEquals(0, table.flags & TableFlags.RESIZABLE);

        // Hiding columns
        ctx.tableSetColumnEnabled(tableID, "One", false);
        assertFalse(column0.isEnabled);
        ctx.tableSetColumnEnabled(tableID, "One", true);
        assertTrue(column0.isEnabled);
    }

    /** table_reorder: reordering columns, and resetting the order from the body context menu. */
    @Test
    void testTableReorder() {
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(250.0f, 100.0f, Condition.APPEARING);
                    IkGui.begin("Test window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.beginTable(
                            "table1",
                            4,
                            TableFlags.REORDERABLE | TableFlags.CONTEXT_MENU_IN_BODY)) {
                        IkGui.tableSetupColumn("One");
                        IkGui.tableSetupColumn("Two");
                        IkGui.tableSetupColumn("Three");
                        IkGui.tableSetupColumn("Four");
                        IkGui.tableHeadersRow();
                        helperTableSubmitCellsText(4, 4);
                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test window 1");
        final Table table = IkGuiImplTables.tableFindByID(ctx.getID("table1"));

        // The default order is set initially
        assertTrue(table.isDefaultDisplayOrder);
        for (int i = 0; i < table.columnsCount; i++) {
            assertEquals(i, table.displayOrderToIndex[i]);
        }

        // Swap two columns
        ctx.itemDragAndDrop(
                IkGuiTestContext.tableGetHeaderID(table, "Two"),
                IkGuiTestContext.tableGetHeaderID(table, "Three"));

        // The new order
        assertFalse(table.isDefaultDisplayOrder);
        assertEquals(0, table.displayOrderToIndex[0]);
        assertEquals(2, table.displayOrderToIndex[1]);
        assertEquals(1, table.displayOrderToIndex[2]);
        assertEquals(3, table.displayOrderToIndex[3]);

        // Reset the order from the CONTEXT_MENU_IN_BODY location
        ctx.mouseMoveToPos(table.innerClipRect.getCenterX(), table.innerClipRect.getCenterY());
        ctx.mouseClick(MouseButton.RIGHT);
        ctx.setRef("//$FOCUSED");
        ctx.menuClick("Reset/###ResetOrder");

        // The default order is back
        assertTrue(table.isDefaultDisplayOrder);
        for (int i = 0; i < table.columnsCount; i++) {
            assertEquals(i, table.displayOrderToIndex[i]);
        }
    }

    /** table_custom_header: widgets in a custom table header can be clicked. */
    @Test
    void testTableCustomHeader() {
        final IkBoolean checkAll = new IkBoolean(false);
        final IkGuiTestContext.ItemStatus status = new IkGuiTestContext.ItemStatus();
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(250.0f, 100.0f, Condition.APPEARING);
                    IkGui.begin("Test window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    final int columnsCount = 1;
                    if (IkGui.beginTable("table1", columnsCount)) {
                        IkGui.tableSetupColumn("One");
                        IkGui.tableNextRow(TableRowFlags.HEADERS);
                        IkGui.tableSetColumnIndex(0);
                        IkGui.tableHeader(IkGui.tableGetColumnName(0));
                        final boolean ret = IkGui.checkbox("##checkall", checkAll);
                        status.queryInc(ret);
                        IkGui.tableNextRow();
                        helperTableSubmitCellsButtonFix(columnsCount, 1);
                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test window 1");
        assertEquals(0, status.retValue);
        assertEquals(0, status.hovered);
        assertEquals(0, status.clicked);
        ctx.itemClick(ctx.getID("##checkall", ctx.getID("table1")));
        assertTrue(status.retValue > 0);
        assertTrue(status.hovered > 0);
        assertTrue(status.clicked > 0);
    }

    /** table_scroll_on_resize: a column stays visible after resizing it. */
    @Test
    void testTableScrollOnResize() {
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(250.0f, 100.0f, Condition.APPEARING);
                    IkGui.begin("Test window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.beginTable("table1", 3, TableFlags.RESIZABLE | TableFlags.SCROLL_X)) {
                        IkGui.tableSetupColumn("One");
                        IkGui.tableSetupColumn("Two");
                        IkGui.tableSetupColumn("Three");
                        IkGui.tableHeadersRow();
                        IkGui.tableNextRow();
                        for (int column = 0; column < IkGui.tableGetColumnCount(); column++) {
                            IkGui.tableSetColumnIndex(column);
                            IkGui.button(Integer.toString(column));
                        }
                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test window 1");
        final Window window = ctx.getWindowByRef("");
        final Table table = IkGuiImplTables.tableFindByID(ctx.getID("table1"));
        final int resizeID = IkGuiImplTables.tableGetColumnResizeID(table, 0, 0);

        // Left to right, and the column is still fully visible at the end of the resize
        ctx.itemDragWithDelta(resizeID, window.rectInner.getWidth() * 2.0f, 0.0f);
        IkGuiTestContext.ItemInfo info = ctx.itemInfo("table1/0");
        assertTrue(info.id != 0);
        assertTrue(
                window.rectCurrentClip.getLeft() <= table.columns[0].clipRect.getLeft()
                        && window.rectCurrentClip.getRight()
                                >= table.columns[0].clipRect.getRight());

        // Right to left, and the column is still fully visible at the end of the resize
        ctx.itemDragWithDelta(resizeID, -window.rectInner.getWidth() * 2.0f, 0.0f);
        info = ctx.itemInfo("table1/0");
        assertTrue(info.id != 0);
        assertTrue(
                window.rectCurrentClip.getLeft() <= table.columns[0].clipRect.getLeft()
                        && window.rectCurrentClip.getRight()
                                >= table.columns[0].clipRect.getRight());
    }

    /** table_nested: nested tables, which also stresses nested child windows. */
    @Test
    void testTableNested() {
        final int[] levels = {16};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.sliderInt("Levels", levels, 1, 30);
                    int tableBegunInto = 0;
                    for (int n = 0; n < levels[0]; n++) {
                        final int columnCount = 3;
                        int flags = TableFlags.BORDERS | TableFlags.SIZING_STRETCH_SAME;
                        if ((n & 1) != 0) {
                            flags |= TableFlags.SCROLL_X;
                        } else if ((n & 2) != 0) {
                            flags |= TableFlags.SCROLL_Y;
                        }
                        final float outerSize = 800.f - n * 16.0f;
                        if (!IkGui.beginTable(
                                "table " + n, columnCount, flags, outerSize, outerSize)) {
                            break;
                        }
                        tableBegunInto++;
                        IkGui.tableSetupColumn("head " + n);
                        IkGui.tableSetupColumn(null, TableColumnFlags.WIDTH_FIXED, 20.0f);
                        IkGui.tableSetupColumn(null, TableColumnFlags.WIDTH_FIXED, 20.0f);
                        IkGui.tableHeadersRow();
                        if (n + 1 < levels[0]) {
                            IkGui.tableNextRow();
                            IkGui.tableNextColumn();
                        }
                    }
                    for (int n = 0; n < tableBegunInto; n++) {
                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        ctx.yieldFrames(3);
    }

    /** table_hidden_columns: the last item data is cleared in hidden columns. */
    @Test
    void testTableHiddenColumns() {
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(250.0f, 100.0f, Condition.APPEARING);
                    IkGui.begin("Test window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.beginTable("table1", 2, TableFlags.HIDEABLE)) {
                        final var g = IkGuiInternal.context;
                        IkGui.tableSetupColumn("One");
                        IkGui.tableSetupColumn("Two", TableColumnFlags.DEFAULT_HIDE);
                        IkGui.tableHeadersRow();
                        for (int column = 0; column < 2; column++) {
                            IkGui.tableNextColumn();
                            ctx.checkEquals(0, g.lastItemData.id, "id before " + column);
                            ctx.checkEquals(
                                    0, g.lastItemData.statusFlags, "status before " + column);

                            IkGui.button(Integer.toString(column));
                            if (column == 0) {
                                // The visible column
                                ctx.checkEquals(IkGui.getID("0"), g.lastItemData.id, "visible id");
                                if (IkGui.isItemHovered()) {
                                    ctx.check(
                                            (g.lastItemData.statusFlags
                                                            & ItemStatusFlags.HOVERED_RECT)
                                                    != 0,
                                            "hovered rect");
                                }
                            }
                            if (column == 1) {
                                // The hidden column
                                ctx.checkEquals(0, g.lastItemData.id, "hidden id");
                                ctx.checkEquals(0, g.lastItemData.statusFlags, "hidden status");
                            }
                        }
                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test window 1");
        // Make sure the last item status has the HOVERED_RECT flag
        ctx.mouseMove("table1/0");
        // One more frame, so the checks in the GUI function run
        ctx.yieldFrames(2);
    }

    /** table_sameline_between_columns: sameLine() between table columns. */
    @Test
    void testTableSamelineBetweenColumns() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Test window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.text("Hello");

                    if (IkGui.beginTable("columns1", 3, TableFlags.BORDERS)) {
                        IkGui.tableNextColumn();

                        // sameLine(0, 0) doesn't change the position, even in the first column
                        Vector2f p1 = IkGui.getCursorScreenPos();
                        IkGui.sameLine(0.0f, 0.0f);
                        ctx.checkEquals(p1.x, IkGui.getCursorScreenPos().x, "sameLine x");
                        ctx.checkEquals(p1.y, IkGui.getCursorScreenPos().y, "sameLine y");
                        IkGui.dummy(32, 32);

                        IkGui.tableNextColumn();
                        // Starts at the same height as before
                        ctx.checkEquals(p1.y, IkGui.getCursorScreenPos().y, "column 1 start");
                        IkGui.text("Test");
                        // The line height isn't automatically shared
                        ctx.checkEquals(
                                p1.y + IkGui.getTextLineHeightWithSpacing(),
                                IkGui.getCursorScreenPos().y,
                                "column 1 after text");

                        // sameLine() pulls the line position and height from before
                        IkGui.tableNextRow();
                        IkGui.tableNextColumn();
                        final Vector2f p0 = IkGui.getCursorScreenPos();
                        IkGui.dummy(4.0f, 4.0f);
                        p1 = IkGui.getCursorScreenPos();
                        IkGui.dummy(32, 32);

                        IkGui.tableNextColumn();
                        ctx.checkEquals(p0.y, IkGui.getCursorScreenPos().y, "row 2 column 1 start");
                        IkGui.sameLine(0.0f, 0.0f);
                        ctx.checkEquals(p1.y, IkGui.getCursorScreenPos().y, "row 2 sameLine");
                        IkGui.text("Test");
                        // The line height is shared
                        ctx.checkEquals(
                                p1.y + 32 + IkGuiInternal.context.style.variable.itemSpacing.y,
                                IkGui.getCursorScreenPos().y,
                                "row 2 after text");

                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        ctx.yieldFrames(2);
    }

    /** table_sameline_before_nextrow: a sameLine() before a row change doesn't leak into it. */
    @Test
    void testTableSamelineBeforeNextrow() {
        ctx.setGui(
                () -> {
                    IkGui.pushStyleVarFloat2(StyleVariable.FRAME_PADDING, 0, 0);
                    IkGui.begin("Test window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.text("Hello");

                    if (IkGui.beginTable("columns1", 3)) {
                        IkGui.tableNextColumn();
                        IkGui.collapsingHeader("AAAA");
                        IkGui.sameLine();
                        IkGui.text("*1");
                        IkGui.tableNextColumn();
                        IkGui.text("BBBB");
                        IkGui.tableNextColumn();
                        IkGui.text("CCCC\nTwoLines\nThreeLines");

                        IkGui.tableNextColumn();
                        IkGui.collapsingHeader("AAAA2");
                        Vector2f p1 = IkGui.getItemRectMin();
                        IkGui.sameLine();
                        IkGui.text("*2");
                        Vector2f p2 = IkGui.getItemRectMin();
                        ctx.checkEquals(p1.y, p2.y, "row 2");
                        IkGui.tableNextColumn();
                        IkGui.text("BBBB2");
                        IkGui.tableNextColumn();
                        IkGui.text("CCCC2\nTwoLines\nThreeLines");
                        // Note the extra sameLine() here
                        IkGui.sameLine();

                        IkGui.tableNextColumn();
                        IkGui.collapsingHeader("AAAA3");
                        p1 = IkGui.getItemRectMin();
                        IkGui.sameLine();
                        IkGui.text("*3");
                        p2 = IkGui.getItemRectMin();
                        // The sameLine() of the previous row doesn't leak into this one
                        ctx.checkEquals(p1.y, p2.y, "row 3");
                        IkGui.tableNextColumn();
                        IkGui.text("BBBB3");
                        IkGui.tableNextColumn();
                        IkGui.text("CCCC3\nTwoLines\nThreeLines");
                        IkGui.endTable();
                    }
                    IkGui.end();
                    IkGui.popStyleVar();
                });
        ctx.yieldFrames(2);
    }

    /** table_hovered_column: the IS_HOVERED column flag. */
    @Test
    void testTableHoveredColumn() {
        final int[] counters = new int[5];
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.text("Hello");
                    // Enlarge the window so all the columns are visible
                    IkGui.dummy(500, 0.0f);

                    if (IkGui.beginTable(
                            "table",
                            3,
                            TableFlags.BORDERS
                                    | TableFlags.NO_SAVED_SETTINGS
                                    | TableFlags.SIZING_STRETCH_SAME)) {
                        for (int row = 0; row < 5; row++) {
                            IkGui.tableNextRow();
                            IkGui.pushID(row);

                            IkGui.tableSetColumnIndex(0);
                            IkGui.selectable(
                                    "##selectable",
                                    false,
                                    SelectableFlags.SPAN_ALL_COLUMNS
                                            | SelectableFlags.ALLOW_OVERLAP);
                            IkGui.sameLine(0.0f, 0.0f);
                            IkGui.button("Col0");
                            if ((IkGui.tableGetColumnFlags() & TableColumnFlags.IS_HOVERED) != 0) {
                                IkGui.sameLine();
                                IkGui.text("Hovered");
                            }

                            IkGui.tableSetColumnIndex(1);
                            IkGui.button("Col1");
                            IkGui.sameLine();
                            IkGui.text(Integer.toString(counters[row]));
                            if ((IkGui.tableGetColumnFlags() & TableColumnFlags.IS_HOVERED) != 0) {
                                IkGui.sameLine();
                                if (IkGui.arrowButton("<", Direction.LEFT)) {
                                    counters[row]--;
                                }
                                IkGui.sameLine();
                                if (IkGui.arrowButton(">", Direction.RIGHT)) {
                                    counters[row]++;
                                }
                            }

                            IkGui.tableSetColumnIndex(2);
                            if ((IkGui.tableGetColumnFlags() & TableColumnFlags.IS_HOVERED) != 0) {
                                IkGui.text("Hovered");
                            }
                            IkGui.popID();
                        }
                        IkGui.endTable();
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        final Table table = IkGuiImplTables.tableFindByID(ctx.getID("table"));

        // Upstream notes these could use the public tableGetHoveredColumn()
        ctx.mouseMove("table/$$1/Col0");
        assertEquals(0, table.hoveredColumnBody);

        ctx.mouseMove("table/$$1/Col1");
        assertEquals(1, table.hoveredColumnBody);
        ctx.mouseMove("table/$$1/<");
        assertEquals(1, table.hoveredColumnBody);
        assertEquals(0, counters[1]);
        ctx.itemClick("table/$$1/<");
        assertEquals(1, table.hoveredColumnBody);
        assertEquals(-1, counters[1]);
        ctx.itemClick("table/$$1/>");
        ctx.itemClick("table/$$1/>");
        assertEquals(1, table.hoveredColumnBody);
        assertEquals(1, counters[1]);
    }

    /** table_hovered_row: tableGetHoveredRow(). */
    @Test
    void testTableHoveredRow() {
        final int[] hoveredRow = {0};
        final int[] hoveredColumn = {0};
        ctx.setGui(
                () -> {
                    // Make the window smaller than the line count, so it scrolls
                    IkGui.setNextWindowSize(
                            0.0f, IkGui.getFrameHeightWithSpacing() * 10, Condition.ALWAYS);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.beginTable(
                            "table1",
                            3,
                            TableFlags.SCROLL_Y | TableFlags.ROW_BACKGROUND | TableFlags.BORDERS_V,
                            0.0f,
                            -IkGui.getTextLineHeightWithSpacing())) {
                        IkGui.tableSetupScrollFreeze(0, 1);
                        for (int n = 0; n < 3; n++) {
                            IkGui.tableSetupColumn("Column" + n);
                        }
                        IkGui.tableHeadersRow();
                        helperTableSubmitCellsButtonFix(3, 30);
                        hoveredRow[0] = IkGuiInternal.tableGetHoveredRow();
                        hoveredColumn[0] = IkGui.tableGetHoveredColumn();
                        IkGui.endTable();
                    }
                    IkGui.text("Hovered row " + hoveredRow[0] + ", column " + hoveredColumn[0]);
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        final Table table = IkGuiImplTables.tableFindByID(ctx.getID("table1"));
        // Upstream warms up for an extra frame, so the window isn't hidden by its first auto-fit
        ctx.yieldFrame();
        ctx.mouseMove(IkGuiTestContext.tableGetHeaderID(table, "Column0"));
        assertEquals(0, hoveredRow[0]);
        ctx.mouseMove(IkGuiTestContext.tableGetHeaderID(table, "Column2"));
        assertEquals(0, hoveredRow[0]);
        ctx.mouseMove("table1/0,0");
        assertEquals(1, hoveredRow[0]);
        ctx.mouseMove("table1/1,0");
        assertEquals(2, hoveredRow[0]);
        ctx.scrollToBottom(table.innerWindow);
        ctx.mouseMove(IkGuiTestContext.tableGetHeaderID(table, "Column0"));
        // At this point the frozen row overlaps some other row
        assertEquals(0, hoveredRow[0]);
    }
}
