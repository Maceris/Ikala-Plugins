package com.ikalagaming.graphics.gui;

import static com.ikalagaming.graphics.gui.IkGuiDemo.helpMarker;

import com.ikalagaming.graphics.gui.data.*;
import com.ikalagaming.graphics.gui.enums.*;
import com.ikalagaming.graphics.gui.flags.*;
import com.ikalagaming.graphics.gui.util.Color;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The "Tables & Columns" section of the demo window. */
class IkGuiDemoTables {
    /**
     * Whether to open (1) or close (0) every section this frame, or -1 to leave them as they are.
     */
    private static int openAction = -1;

    private static final IkBoolean disableIndent = new IkBoolean(false);

    /** Width and height that are a factor of the size of our font. */
    private static float textBaseWidth;

    private static float textBaseHeight;

    /** Show the "Tables & Columns" section. */
    static void show() {
        if (!IkGui.collapsingHeader("Tables & Columns")) {
            return;
        }

        // Using those as a base value to create width/height that are factor of the size of our
        // font
        textBaseWidth = IkGui.calcTextSize("A").x;
        textBaseHeight = IkGui.getTextLineHeightWithSpacing();

        IkGui.pushID("Tables");

        openAction = -1;
        if (IkGui.button("Expand all")) {
            openAction = 1;
        }
        IkGui.sameLine();
        if (IkGui.button("Collapse all")) {
            openAction = 0;
        }
        IkGui.sameLine();

        // Options
        IkGui.checkbox("Disable tree indentation", disableIndent);
        IkGui.sameLine();
        helpMarker(
                "Disable the indenting of tree nodes so demo tables can use the full window"
                        + " width.");
        IkGui.separator();
        final boolean noIndent = disableIndent.get();
        if (noIndent) {
            IkGui.pushStyleVarFloat(StyleVariable.INDENT_SPACING, 0.0f);
        }

        // About styling of tables: most settings are configured on a per-table basis via the
        // flags passed to beginTable() and tableSetupColumn(). There are however a few settings
        // that are shared and part of the style:
        //   style.cellPadding                          // Padding within each cell
        //   ColorType.TABLE_HEADER_BACKGROUND          // Table header background
        //   ColorType.TABLE_BORDER_STRONG              // Table outer and header borders
        //   ColorType.TABLE_BORDER_LIGHT               // Table inner borders
        //   ColorType.TABLE_ROW_BACKGROUND             // Table row background (even rows)
        //   ColorType.TABLE_ROW_BACKGROUND_ALT         // Table row background (odd rows)

        showBasic();
        showBordersBackground();
        showResizableStretch();
        showResizableFixed();
        showResizableMixed();
        showReorderable();
        showPadding();
        showSizingPolicies();
        showVerticalScrolling();
        showHorizontalScrolling();
        showColumnsFlags();
        showColumnsWidths();
        showNestedTables();
        showRowHeight();
        showOuterSize();
        showBackgroundColor();
        showTreeView();
        showItemWidth();
        showCustomHeaders();
        showAngledHeaders();
        showContextMenus();
        showSyncedInstances();
        showSorting();
        showAdvanced();

        IkGui.popID();

        if (noIndent) {
            IkGui.popStyleVar();
        }
    }

    /**
     * Start a section, which opens or closes along with the others when using "Expand all" and
     * "Collapse all".
     *
     * @param label The label of the tree node.
     * @return True if the section is open.
     */
    private static boolean section(String label) {
        if (openAction != -1) {
            IkGui.setNextItemOpen(openAction != 0);
        }
        return IkGui.treeNode(label);
    }

    /** Make the UI compact because there are so many fields. */
    private static void pushStyleCompact() {
        final StyleVariables style = IkGui.getStyle().variable;
        IkGui.pushStyleVarY(StyleVariable.FRAME_PADDING, (int) (style.framePadding.y * 0.60f));
        IkGui.pushStyleVarY(StyleVariable.ITEM_SPACING, (int) (style.itemSpacing.y * 0.60f));
    }

    private static void popStyleCompact() {
        IkGui.popStyleVar(2);
    }

    /** A table sizing policy, for the sizing policy combo. */
    private record SizingPolicy(int value, String name, String tooltip) {}

    private static final SizingPolicy[] SIZING_POLICIES = {
        new SizingPolicy(
                TableFlags.NONE,
                "Default",
                "Use default sizing policy:\n"
                        + "- TableFlags.SIZING_FIXED_FIT if SCROLL_X is on or if host window has"
                        + " WindowFlags.ALWAYS_AUTO_RESIZE.\n"
                        + "- TableFlags.SIZING_STRETCH_SAME otherwise."),
        new SizingPolicy(
                TableFlags.SIZING_FIXED_FIT,
                "TableFlags.SIZING_FIXED_FIT",
                "Columns default to WIDTH_FIXED (if resizable) or WIDTH_AUTO (if not resizable),"
                        + " matching contents width."),
        new SizingPolicy(
                TableFlags.SIZING_FIXED_SAME,
                "TableFlags.SIZING_FIXED_SAME",
                "Columns are all the same width, matching the maximum contents width.\n"
                        + "Implicitly disable TableFlags.RESIZABLE and enable"
                        + " TableFlags.NO_KEEP_COLUMNS_VISIBLE."),
        new SizingPolicy(
                TableFlags.SIZING_STRETCH_PROP,
                "TableFlags.SIZING_STRETCH_PROP",
                "Columns default to WIDTH_STRETCH with weights proportional to their widths."),
        new SizingPolicy(
                TableFlags.SIZING_STRETCH_SAME,
                "TableFlags.SIZING_STRETCH_SAME",
                "Columns default to WIDTH_STRETCH with same weights.")
    };

    /** Show a combo box with a choice of sizing policies. */
    private static void editTableSizingFlags(IkInt flags) {
        int index = 0;
        while (index < SIZING_POLICIES.length
                && SIZING_POLICIES[index].value()
                        != (flags.get() & TableFlags.INTERNAL_SIZING_MASK)) {
            index++;
        }
        final String preview =
                index < SIZING_POLICIES.length
                        ? (index > 0
                                ? SIZING_POLICIES[index].name().substring("TableFlags".length())
                                : SIZING_POLICIES[index].name())
                        : "";
        if (IkGui.beginCombo("Sizing Policy", preview)) {
            for (int n = 0; n < SIZING_POLICIES.length; n++) {
                if (IkGui.selectable(SIZING_POLICIES[n].name(), index == n)) {
                    flags.set(
                            (flags.get() & ~TableFlags.INTERNAL_SIZING_MASK)
                                    | SIZING_POLICIES[n].value());
                }
            }
            IkGui.endCombo();
        }
        IkGui.sameLine();
        IkGui.textDisabled("(?)");
        if (IkGui.beginItemTooltip()) {
            IkGui.pushTextWrapPos(IkGui.getTextLineHeight() * 50.0f);
            for (SizingPolicy policy : SIZING_POLICIES) {
                IkGui.separator();
                IkGui.text(policy.name() + ":");
                IkGui.separator();
                IkGui.setCursorPosX(
                        IkGui.getCursorPosX() + IkGui.getStyle().variable.indentSpacing * 0.5f);
                IkGui.textUnformatted(policy.tooltip());
            }
            IkGui.popTextWrapPos();
            IkGui.endTooltip();
        }
    }

    private static void editTableColumnsFlags(IkInt flags) {
        IkGui.checkboxFlags("DISABLED", flags, TableColumnFlags.DISABLED);
        IkGui.sameLine();
        helpMarker("Master disable flag (also hide from context menu)");
        IkGui.checkboxFlags("DEFAULT_HIDE", flags, TableColumnFlags.DEFAULT_HIDE);
        IkGui.checkboxFlags("DEFAULT_SORT", flags, TableColumnFlags.DEFAULT_SORT);
        if (IkGui.checkboxFlags("WIDTH_STRETCH", flags, TableColumnFlags.WIDTH_STRETCH)) {
            flags.set(
                    flags.get()
                            & ~(TableColumnFlags.INTERNAL_WIDTH_MASK
                                    ^ TableColumnFlags.WIDTH_STRETCH));
        }
        if (IkGui.checkboxFlags("WIDTH_FIXED", flags, TableColumnFlags.WIDTH_FIXED)) {
            flags.set(
                    flags.get()
                            & ~(TableColumnFlags.INTERNAL_WIDTH_MASK
                                    ^ TableColumnFlags.WIDTH_FIXED));
        }
        IkGui.checkboxFlags("NO_RESIZE", flags, TableColumnFlags.NO_RESIZE);
        IkGui.checkboxFlags("NO_REORDER", flags, TableColumnFlags.NO_REORDER);
        IkGui.checkboxFlags("NO_HIDE", flags, TableColumnFlags.NO_HIDE);
        IkGui.checkboxFlags("NO_CLIP", flags, TableColumnFlags.NO_CLIP);
        IkGui.checkboxFlags("NO_SORT", flags, TableColumnFlags.NO_SORT);
        IkGui.checkboxFlags("NO_SORT_ASCENDING", flags, TableColumnFlags.NO_SORT_ASCENDING);
        IkGui.checkboxFlags("NO_SORT_DESCENDING", flags, TableColumnFlags.NO_SORT_DESCENDING);
        IkGui.checkboxFlags("NO_HEADER_LABEL", flags, TableColumnFlags.NO_HEADER_LABEL);
        IkGui.checkboxFlags("NO_HEADER_WIDTH", flags, TableColumnFlags.NO_HEADER_WIDTH);
        IkGui.checkboxFlags("PREFER_SORT_ASCENDING", flags, TableColumnFlags.PREFER_SORT_ASCENDING);
        IkGui.checkboxFlags(
                "PREFER_SORT_DESCENDING", flags, TableColumnFlags.PREFER_SORT_DESCENDING);
        IkGui.checkboxFlags("INDENT_ENABLE", flags, TableColumnFlags.INDENT_ENABLE);
        IkGui.sameLine();
        helpMarker("Default for column 0");
        IkGui.checkboxFlags("INDENT_DISABLE", flags, TableColumnFlags.INDENT_DISABLE);
        IkGui.sameLine();
        helpMarker("Default for column >0");
        IkGui.checkboxFlags("ANGLED_HEADER", flags, TableColumnFlags.ANGLED_HEADER);
    }

    private static void showTableColumnsStatusFlags(int flags) {
        final IkInt copy = new IkInt(flags);
        IkGui.checkboxFlags("IS_ENABLED", copy, TableColumnFlags.IS_ENABLED);
        IkGui.checkboxFlags("IS_VISIBLE", copy, TableColumnFlags.IS_VISIBLE);
        IkGui.checkboxFlags("IS_SORTED", copy, TableColumnFlags.IS_SORTED);
        IkGui.checkboxFlags("IS_HOVERED", copy, TableColumnFlags.IS_HOVERED);
    }

    /** Fill a table with "Hello column,row" text. */
    private static void helloRows(int rows, int columns, String prefix) {
        for (int row = 0; row < rows; row++) {
            IkGui.tableNextRow();
            for (int column = 0; column < columns; column++) {
                IkGui.tableSetColumnIndex(column);
                IkGui.text(String.format("%s %d,%d", prefix, column, row));
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Basic
    // ---------------------------------------------------------------------------------------------

    private static void showBasic() {
        if (!section("Basic")) {
            return;
        }
        // Here we will showcase three different ways to output a table. They are very simple
        // variations of the same thing!

        // [Method 1] Using tableNextRow() to create a new row, and tableSetColumnIndex() to select
        // the column. In many situations, this is the most flexible and easy to use pattern.
        helpMarker(
                "Using tableNextRow() + calling tableSetColumnIndex() _before_ each cell, in a"
                        + " loop.");
        if (IkGui.beginTable("table1", 3)) {
            for (int row = 0; row < 4; row++) {
                IkGui.tableNextRow();
                for (int column = 0; column < 3; column++) {
                    IkGui.tableSetColumnIndex(column);
                    IkGui.text(String.format("Row %d Column %d", row, column));
                }
            }
            IkGui.endTable();
        }

        // [Method 2] Using tableNextColumn() called multiple times, instead of using a for loop
        // + tableSetColumnIndex(). This is generally more convenient when you have code manually
        // submitting the contents of each column.
        helpMarker(
                "Using tableNextRow() + calling tableNextColumn() _before_ each cell, manually.");
        if (IkGui.beginTable("table2", 3)) {
            for (int row = 0; row < 4; row++) {
                IkGui.tableNextRow();
                IkGui.tableNextColumn();
                IkGui.text("Row " + row);
                IkGui.tableNextColumn();
                IkGui.text("Some contents");
                IkGui.tableNextColumn();
                IkGui.text("123.456");
            }
            IkGui.endTable();
        }

        // [Method 3] We call tableNextColumn() _before_ each cell. We never call tableNextRow(),
        // as tableNextColumn() will automatically wrap around and create new rows as needed. This
        // is generally more convenient when your cells all contain the same type of data.
        helpMarker(
                "Only using tableNextColumn(), which tends to be convenient for tables where every"
                        + " cell contains the same type of contents.");
        if (IkGui.beginTable("table3", 3)) {
            for (int item = 0; item < 14; item++) {
                IkGui.tableNextColumn();
                IkGui.text("Item " + item);
            }
            IkGui.endTable();
        }
        IkGui.treePop();
    }

    // ---------------------------------------------------------------------------------------------
    // Borders, background
    // ---------------------------------------------------------------------------------------------

    private static final IkInt bordersFlags =
            new IkInt(TableFlags.BORDERS | TableFlags.ROW_BACKGROUND);
    private static final IkBoolean bordersDisplayHeaders = new IkBoolean(false);

    /** 0 for text contents, 1 for buttons filling the cells. */
    private static final IkInt bordersContentsType = new IkInt(0);

    private static void showBordersBackground() {
        if (!section("Borders, background")) {
            return;
        }
        // Expose a few borders related flags interactively
        final IkInt flags = bordersFlags;
        pushStyleCompact();
        IkGui.checkboxFlags("TableFlags.ROW_BACKGROUND", flags, TableFlags.ROW_BACKGROUND);
        IkGui.checkboxFlags("TableFlags.BORDERS", flags, TableFlags.BORDERS);
        IkGui.sameLine();
        helpMarker(
                "TableFlags.BORDERS\n = TableFlags.BORDERS_INNER_V\n | TableFlags.BORDERS_OUTER_V\n"
                        + " | TableFlags.BORDERS_INNER_H\n | TableFlags.BORDERS_OUTER_H");
        IkGui.indent();

        IkGui.checkboxFlags("TableFlags.BORDERS_H", flags, TableFlags.BORDERS_H);
        IkGui.indent();
        IkGui.checkboxFlags("TableFlags.BORDERS_OUTER_H", flags, TableFlags.BORDERS_OUTER_H);
        IkGui.checkboxFlags("TableFlags.BORDERS_INNER_H", flags, TableFlags.BORDERS_INNER_H);
        IkGui.unindent();

        IkGui.checkboxFlags("TableFlags.BORDERS_V", flags, TableFlags.BORDERS_V);
        IkGui.indent();
        IkGui.checkboxFlags("TableFlags.BORDERS_OUTER_V", flags, TableFlags.BORDERS_OUTER_V);
        IkGui.checkboxFlags("TableFlags.BORDERS_INNER_V", flags, TableFlags.BORDERS_INNER_V);
        IkGui.unindent();

        IkGui.checkboxFlags("TableFlags.BORDERS_OUTER", flags, TableFlags.BORDERS_OUTER);
        IkGui.checkboxFlags("TableFlags.BORDERS_INNER", flags, TableFlags.BORDERS_INNER);
        IkGui.unindent();

        IkGui.alignTextToFramePadding();
        IkGui.text("Cell contents:");
        IkGui.sameLine();
        IkGui.radioButton("Text", bordersContentsType, 0);
        IkGui.sameLine();
        IkGui.radioButton("FillButton", bordersContentsType, 1);
        IkGui.checkbox("Display headers", bordersDisplayHeaders);
        IkGui.checkboxFlags("TableFlags.NO_BORDERS_IN_BODY", flags, TableFlags.NO_BORDERS_IN_BODY);
        IkGui.sameLine();
        helpMarker(
                "Disable vertical borders in columns Body (borders will always appear in"
                        + " Headers)");
        popStyleCompact();

        if (IkGui.beginTable("table1", 3, flags.get())) {
            // Display headers so we can inspect their interaction with borders. Headers are not
            // the main purpose of this section of the demo, see other sections for details.
            if (bordersDisplayHeaders.get()) {
                IkGui.tableSetupColumn("One");
                IkGui.tableSetupColumn("Two");
                IkGui.tableSetupColumn("Three");
                IkGui.tableHeadersRow();
            }

            for (int row = 0; row < 5; row++) {
                IkGui.tableNextRow();
                for (int column = 0; column < 3; column++) {
                    IkGui.tableSetColumnIndex(column);
                    final String text = String.format("Hello %d,%d", column, row);
                    if (bordersContentsType.get() == 0) {
                        IkGui.textUnformatted(text);
                    } else {
                        IkGui.button(text, -Float.MIN_VALUE, 0.0f);
                    }
                }
            }
            IkGui.endTable();
        }
        IkGui.treePop();
    }

    // ---------------------------------------------------------------------------------------------
    // Resizable
    // ---------------------------------------------------------------------------------------------

    private static final IkInt resizableStretchFlags =
            new IkInt(
                    TableFlags.SIZING_STRETCH_SAME
                            | TableFlags.RESIZABLE
                            | TableFlags.BORDERS_OUTER
                            | TableFlags.BORDERS_V
                            | TableFlags.CONTEXT_MENU_IN_BODY);

    private static void showResizableStretch() {
        if (!section("Resizable, stretch")) {
            return;
        }
        // By default, if we don't enable SCROLL_X the sizing policy for each column is "Stretch".
        // All columns maintain a sizing weight, and they will occupy all available width.
        final IkInt flags = resizableStretchFlags;
        pushStyleCompact();
        IkGui.checkboxFlags("TableFlags.RESIZABLE", flags, TableFlags.RESIZABLE);
        IkGui.checkboxFlags("TableFlags.BORDERS_V", flags, TableFlags.BORDERS_V);
        IkGui.sameLine();
        helpMarker(
                "Using the RESIZABLE flag automatically enables the BORDERS_INNER_V flag as well,"
                        + " this is why the resize borders are still showing when unchecking"
                        + " this.");
        popStyleCompact();

        if (IkGui.beginTable("table1", 3, flags.get())) {
            helloRows(5, 3, "Hello");
            IkGui.endTable();
        }
        IkGui.treePop();
    }

    private static final IkInt resizableFixedFlags =
            new IkInt(
                    TableFlags.SIZING_FIXED_FIT
                            | TableFlags.RESIZABLE
                            | TableFlags.BORDERS_OUTER
                            | TableFlags.BORDERS_V
                            | TableFlags.CONTEXT_MENU_IN_BODY);

    private static void showResizableFixed() {
        if (!section("Resizable, fixed")) {
            return;
        }
        // Here we use SIZING_FIXED_FIT (even though SCROLL_X is not set), so columns will adopt
        // the "Fixed" policy and will maintain a fixed width regardless of the whole available
        // width (unless the table is small). If there is not enough available width to fit all
        // columns, they will however be resized down.
        helpMarker(
                "Using RESIZABLE + SIZING_FIXED_FIT flags.\n"
                        + "Fixed-width columns generally makes more sense if you want to use"
                        + " horizontal scrolling.\n\n"
                        + "Double-click a column border to auto-fit the column to its contents.");
        pushStyleCompact();
        IkGui.checkboxFlags(
                "TableFlags.NO_HOST_EXTEND_X", resizableFixedFlags, TableFlags.NO_HOST_EXTEND_X);
        popStyleCompact();

        if (IkGui.beginTable("table1", 3, resizableFixedFlags.get())) {
            helloRows(5, 3, "Hello");
            IkGui.endTable();
        }
        IkGui.treePop();
    }

    private static void showResizableMixed() {
        if (!section("Resizable, mixed")) {
            return;
        }
        helpMarker(
                "Using tableSetupColumn() to alter resizing policy on a per-column basis.\n\n"
                        + "When combining Fixed and Stretch columns, generally you only want one,"
                        + " maybe two trailing columns to use WIDTH_STRETCH.");
        final int flags =
                TableFlags.SIZING_FIXED_FIT
                        | TableFlags.ROW_BACKGROUND
                        | TableFlags.BORDERS
                        | TableFlags.RESIZABLE
                        | TableFlags.REORDERABLE
                        | TableFlags.HIDEABLE;

        if (IkGui.beginTable("table1", 3, flags)) {
            IkGui.tableSetupColumn("AAA", TableColumnFlags.WIDTH_FIXED);
            IkGui.tableSetupColumn("BBB", TableColumnFlags.WIDTH_FIXED);
            IkGui.tableSetupColumn("CCC", TableColumnFlags.WIDTH_STRETCH);
            IkGui.tableHeadersRow();
            for (int row = 0; row < 5; row++) {
                IkGui.tableNextRow();
                for (int column = 0; column < 3; column++) {
                    IkGui.tableSetColumnIndex(column);
                    IkGui.text(
                            String.format(
                                    "%s %d,%d", column == 2 ? "Stretch" : "Fixed", column, row));
                }
            }
            IkGui.endTable();
        }
        if (IkGui.beginTable("table2", 6, flags)) {
            IkGui.tableSetupColumn("AAA", TableColumnFlags.WIDTH_FIXED);
            IkGui.tableSetupColumn("BBB", TableColumnFlags.WIDTH_FIXED);
            IkGui.tableSetupColumn(
                    "CCC", TableColumnFlags.WIDTH_FIXED | TableColumnFlags.DEFAULT_HIDE);
            IkGui.tableSetupColumn("DDD", TableColumnFlags.WIDTH_STRETCH);
            IkGui.tableSetupColumn("EEE", TableColumnFlags.WIDTH_STRETCH);
            IkGui.tableSetupColumn(
                    "FFF", TableColumnFlags.WIDTH_STRETCH | TableColumnFlags.DEFAULT_HIDE);
            IkGui.tableHeadersRow();
            for (int row = 0; row < 5; row++) {
                IkGui.tableNextRow();
                for (int column = 0; column < 6; column++) {
                    IkGui.tableSetColumnIndex(column);
                    IkGui.text(
                            String.format(
                                    "%s %d,%d", column >= 3 ? "Stretch" : "Fixed", column, row));
                }
            }
            IkGui.endTable();
        }
        IkGui.treePop();
    }

    // ---------------------------------------------------------------------------------------------
    // Reorderable, hideable, with headers
    // ---------------------------------------------------------------------------------------------

    private static final IkInt reorderableFlags =
            new IkInt(
                    TableFlags.RESIZABLE
                            | TableFlags.REORDERABLE
                            | TableFlags.HIDEABLE
                            | TableFlags.BORDERS_OUTER
                            | TableFlags.BORDERS_V);

    private static void showReorderable() {
        if (!section("Reorderable, hideable, with headers")) {
            return;
        }
        helpMarker(
                "Click and drag column headers to reorder columns.\n\n"
                        + "Right-click on a header to open a context menu.");
        final IkInt flags = reorderableFlags;
        pushStyleCompact();
        IkGui.checkboxFlags("TableFlags.RESIZABLE", flags, TableFlags.RESIZABLE);
        IkGui.checkboxFlags("TableFlags.REORDERABLE", flags, TableFlags.REORDERABLE);
        IkGui.checkboxFlags("TableFlags.HIDEABLE", flags, TableFlags.HIDEABLE);
        IkGui.checkboxFlags("TableFlags.NO_BORDERS_IN_BODY", flags, TableFlags.NO_BORDERS_IN_BODY);
        IkGui.checkboxFlags(
                "TableFlags.NO_BORDERS_IN_BODY_UNTIL_RESIZE",
                flags,
                TableFlags.NO_BORDERS_IN_BODY_UNTIL_RESIZE);
        IkGui.sameLine();
        helpMarker(
                "Disable vertical borders in columns Body until hovered for resize (borders will"
                        + " always appear in Headers)");
        IkGui.checkboxFlags(
                "TableFlags.HIGHLIGHT_HOVERED_COLUMN", flags, TableFlags.HIGHLIGHT_HOVERED_COLUMN);
        popStyleCompact();

        if (IkGui.beginTable("table1", 3, flags.get())) {
            // Submit column names with tableSetupColumn() and call tableHeadersRow() to create a
            // row with a header in each column. (Later we will show how tableSetupColumn() has
            // other uses, optional flags, sizing weight etc.)
            IkGui.tableSetupColumn("One");
            IkGui.tableSetupColumn("Two");
            IkGui.tableSetupColumn("Three");
            IkGui.tableHeadersRow();
            helloRows(6, 3, "Hello");
            IkGui.endTable();
        }

        // Use an outer width of 0 instead of the default to make the table as tight as possible
        // (only valid when no scrolling and no stretch column)
        if (IkGui.beginTable("table2", 3, flags.get() | TableFlags.SIZING_FIXED_FIT, 0.0f, 0.0f)) {
            IkGui.tableSetupColumn("One");
            IkGui.tableSetupColumn("Two");
            IkGui.tableSetupColumn("Three");
            IkGui.tableHeadersRow();
            helloRows(6, 3, "Fixed");
            IkGui.endTable();
        }
        IkGui.treePop();
    }

    // ---------------------------------------------------------------------------------------------
    // Padding
    // ---------------------------------------------------------------------------------------------

    private static final IkInt paddingFlags1 = new IkInt(TableFlags.BORDERS_V);
    private static final IkBoolean paddingShowHeaders = new IkBoolean(false);
    private static final IkInt paddingFlags2 =
            new IkInt(TableFlags.BORDERS | TableFlags.ROW_BACKGROUND);
    private static final float[] paddingCellPadding = {0.0f, 0.0f};
    private static final IkBoolean paddingShowFrameBackground = new IkBoolean(true);
    private static final IkString[] paddingTexts = new IkString[3 * 5];

    static {
        for (int i = 0; i < paddingTexts.length; i++) {
            paddingTexts[i] = new IkString("edit me", 16);
        }
    }

    private static void showPadding() {
        if (!section("Padding")) {
            return;
        }
        // First example: showcase use of padding flags and effect of BORDERS_OUTER_V and
        // BORDERS_INNER_V on X padding. We don't expose BORDERS_OUTER_H and BORDERS_INNER_H here
        // because they have no effect on X padding.
        helpMarker(
                "We often want outer padding activated when any using features which makes the"
                        + " edges of a column visible:\n"
                        + "e.g.:\n"
                        + "- BORDERS_OUTER_V\n"
                        + "- any form of row selection\n"
                        + "Because of this, activating BORDERS_OUTER_V sets the default to"
                        + " PAD_OUTER_X. Using PAD_OUTER_X or NO_PAD_OUTER_X you can override the"
                        + " default.\n\n"
                        + "Actual padding values are using style.cellPadding.\n\n"
                        + "In this demo we don't show horizontal borders to emphasize how they"
                        + " don't affect default horizontal padding.");

        final IkInt flags1 = paddingFlags1;
        pushStyleCompact();
        IkGui.checkboxFlags("TableFlags.PAD_OUTER_X", flags1, TableFlags.PAD_OUTER_X);
        IkGui.sameLine();
        helpMarker("Enable outer-most padding (default if TableFlags.BORDERS_OUTER_V is set)");
        IkGui.checkboxFlags("TableFlags.NO_PAD_OUTER_X", flags1, TableFlags.NO_PAD_OUTER_X);
        IkGui.sameLine();
        helpMarker("Disable outer-most padding (default if TableFlags.BORDERS_OUTER_V is not set)");
        IkGui.checkboxFlags("TableFlags.NO_PAD_INNER_X", flags1, TableFlags.NO_PAD_INNER_X);
        IkGui.sameLine();
        helpMarker(
                "Disable inner padding between columns (double inner padding if BORDERS_OUTER_V is"
                        + " on, single inner padding if BORDERS_OUTER_V is off)");
        IkGui.checkboxFlags("TableFlags.BORDERS_OUTER_V", flags1, TableFlags.BORDERS_OUTER_V);
        IkGui.checkboxFlags("TableFlags.BORDERS_INNER_V", flags1, TableFlags.BORDERS_INNER_V);
        IkGui.checkbox("show_headers", paddingShowHeaders);
        popStyleCompact();

        if (IkGui.beginTable("table_padding", 3, flags1.get())) {
            if (paddingShowHeaders.get()) {
                IkGui.tableSetupColumn("One");
                IkGui.tableSetupColumn("Two");
                IkGui.tableSetupColumn("Three");
                IkGui.tableHeadersRow();
            }

            for (int row = 0; row < 5; row++) {
                IkGui.tableNextRow();
                for (int column = 0; column < 3; column++) {
                    IkGui.tableSetColumnIndex(column);
                    if (row == 0) {
                        IkGui.text(
                                String.format("Avail %.2f", IkGui.getContentRegionAvailable().x));
                    } else {
                        IkGui.button(
                                String.format("Hello %d,%d", column, row), -Float.MIN_VALUE, 0.0f);
                    }
                }
            }
            IkGui.endTable();
        }

        // Second example: set style.cellPadding to (0,0) or a custom value
        helpMarker("Setting style.cellPadding to (0,0) or a custom value.");
        final IkInt flags2 = paddingFlags2;
        pushStyleCompact();
        IkGui.checkboxFlags("TableFlags.BORDERS", flags2, TableFlags.BORDERS);
        IkGui.checkboxFlags("TableFlags.BORDERS_H", flags2, TableFlags.BORDERS_H);
        IkGui.checkboxFlags("TableFlags.BORDERS_V", flags2, TableFlags.BORDERS_V);
        IkGui.checkboxFlags("TableFlags.BORDERS_INNER", flags2, TableFlags.BORDERS_INNER);
        IkGui.checkboxFlags("TableFlags.BORDERS_OUTER", flags2, TableFlags.BORDERS_OUTER);
        IkGui.checkboxFlags("TableFlags.ROW_BACKGROUND", flags2, TableFlags.ROW_BACKGROUND);
        IkGui.checkboxFlags("TableFlags.RESIZABLE", flags2, TableFlags.RESIZABLE);
        IkGui.checkbox("show_widget_frame_bg", paddingShowFrameBackground);
        IkGui.sliderFloat2("CellPadding", paddingCellPadding, 0.0f, 10.0f, "%.0f");
        popStyleCompact();

        IkGui.pushStyleVarFloat2(
                StyleVariable.CELL_PADDING, paddingCellPadding[0], paddingCellPadding[1]);
        if (IkGui.beginTable("table_padding_2", 3, flags2.get())) {
            final boolean frameBackground = paddingShowFrameBackground.get();
            if (!frameBackground) {
                IkGui.pushStyleColor(ColorType.FRAME_BACKGROUND, 0);
            }
            for (int cell = 0; cell < 3 * 5; cell++) {
                IkGui.tableNextColumn();
                IkGui.setNextItemWidth(-Float.MIN_VALUE);
                IkGui.pushID(cell);
                IkGui.inputText("##cell", paddingTexts[cell]);
                IkGui.popID();
            }
            if (!frameBackground) {
                IkGui.popStyleColor();
            }
            IkGui.endTable();
        }
        IkGui.popStyleVar();
        IkGui.treePop();
    }

    // ---------------------------------------------------------------------------------------------
    // Sizing policies
    // ---------------------------------------------------------------------------------------------

    private static final IkInt sizingFlags1 =
            new IkInt(
                    TableFlags.BORDERS_V
                            | TableFlags.BORDERS_OUTER_H
                            | TableFlags.ROW_BACKGROUND
                            | TableFlags.CONTEXT_MENU_IN_BODY);
    private static final IkInt[] sizingPolicyFlags = {
        new IkInt(TableFlags.SIZING_FIXED_FIT),
        new IkInt(TableFlags.SIZING_FIXED_SAME),
        new IkInt(TableFlags.SIZING_STRETCH_PROP),
        new IkInt(TableFlags.SIZING_STRETCH_SAME)
    };
    private static final IkInt sizingAdvancedFlags =
            new IkInt(
                    TableFlags.SCROLL_Y
                            | TableFlags.BORDERS
                            | TableFlags.ROW_BACKGROUND
                            | TableFlags.RESIZABLE);
    private static final IkInt sizingContentsType = new IkInt(0);
    private static final int[] sizingColumnCount = {3};
    private static final IkString sizingText = new IkString(32);

    private static void showSizingPolicies() {
        if (!section("Sizing policies")) {
            return;
        }
        final IkInt flags1 = sizingFlags1;
        pushStyleCompact();
        IkGui.checkboxFlags("TableFlags.RESIZABLE", flags1, TableFlags.RESIZABLE);
        IkGui.checkboxFlags("TableFlags.NO_HOST_EXTEND_X", flags1, TableFlags.NO_HOST_EXTEND_X);
        popStyleCompact();

        for (int tableIndex = 0; tableIndex < 4; tableIndex++) {
            IkGui.pushID(tableIndex);
            IkGui.setNextItemWidth(textBaseWidth * 30);
            editTableSizingFlags(sizingPolicyFlags[tableIndex]);

            // To make it easier to understand the different sizing policy, for each policy we
            // display one table where the columns have equal contents width, and one where the
            // columns have different contents width.
            final int tableFlags = sizingPolicyFlags[tableIndex].get() | flags1.get();
            if (IkGui.beginTable("table1", 3, tableFlags)) {
                for (int row = 0; row < 3; row++) {
                    IkGui.tableNextRow();
                    for (int column = 0; column < 3; column++) {
                        IkGui.tableNextColumn();
                        IkGui.text("Oh dear");
                    }
                }
                IkGui.endTable();
            }
            if (IkGui.beginTable("table2", 3, tableFlags)) {
                for (int row = 0; row < 3; row++) {
                    IkGui.tableNextRow();
                    IkGui.tableNextColumn();
                    IkGui.text("AAAA");
                    IkGui.tableNextColumn();
                    IkGui.text("BBBBBBBB");
                    IkGui.tableNextColumn();
                    IkGui.text("CCCCCCCCCCCC");
                }
                IkGui.endTable();
            }
            IkGui.popID();
        }

        IkGui.spacing();
        IkGui.textUnformatted("Advanced");
        IkGui.sameLine();
        helpMarker(
                "This section allows you to interact and see the effect of various sizing"
                        + " policies depending on whether Scroll is enabled and the contents of"
                        + " your columns.");

        final IkInt flags = sizingAdvancedFlags;
        pushStyleCompact();
        IkGui.pushID("Advanced");
        IkGui.pushItemWidth(textBaseWidth * 30);
        editTableSizingFlags(flags);
        IkGui.combo(
                "Contents",
                sizingContentsType,
                new String[] {
                    "Show width", "Short Text", "Long Text", "Button", "Fill Button", "InputText"
                });
        if (sizingContentsType.get() == 4) {
            IkGui.sameLine();
            helpMarker(
                    "Be mindful that using right-alignment (e.g. size.x = -Float.MIN_VALUE)"
                            + " creates a feedback loop where contents width can feed into"
                            + " auto-column width can feed into contents width.");
        }
        IkGui.dragInt("Columns", sizingColumnCount, 0.1f, 1, 64, "%d", SliderFlags.ALWAYS_CLAMP);
        IkGui.checkboxFlags("TableFlags.RESIZABLE", flags, TableFlags.RESIZABLE);
        IkGui.checkboxFlags("TableFlags.PRECISE_WIDTHS", flags, TableFlags.PRECISE_WIDTHS);
        IkGui.sameLine();
        helpMarker(
                "Disable distributing remainder width to stretched columns (width allocation on a"
                        + " 100-wide table with 3 columns: Without this flag: 33,33,34. With this"
                        + " flag: 33,33,33). With larger number of columns, resizing will appear"
                        + " to be less smooth.");
        IkGui.checkboxFlags("TableFlags.SCROLL_X", flags, TableFlags.SCROLL_X);
        IkGui.checkboxFlags("TableFlags.SCROLL_Y", flags, TableFlags.SCROLL_Y);
        IkGui.checkboxFlags("TableFlags.NO_CLIP", flags, TableFlags.NO_CLIP);
        IkGui.popItemWidth();
        IkGui.popID();
        popStyleCompact();

        final int columnCount = sizingColumnCount[0];
        if (IkGui.beginTable("table2", columnCount, flags.get(), 0.0f, textBaseHeight * 7)) {
            for (int cell = 0; cell < 10 * columnCount; cell++) {
                IkGui.tableNextColumn();
                final int column = IkGui.tableGetColumnIndex();
                final int row = IkGui.tableGetRowIndex();

                IkGui.pushID(cell);
                final String label = String.format("Hello %d,%d", column, row);
                switch (sizingContentsType.get()) {
                    case 1 -> IkGui.textUnformatted(label);
                    case 2 ->
                            IkGui.text(
                                    String.format(
                                            "Some %s text %d,%d\nOver two lines..",
                                            column == 0 ? "long" : "longeeer", column, row));
                    case 3 -> IkGui.button(label);
                    case 4 -> IkGui.button(label, -Float.MIN_VALUE, 0.0f);
                    case 5 -> {
                        IkGui.setNextItemWidth(-Float.MIN_VALUE);
                        IkGui.inputText("##", sizingText);
                    }
                    default ->
                            IkGui.text(
                                    String.format("W: %.1f", IkGui.getContentRegionAvailable().x));
                }
                IkGui.popID();
            }
            IkGui.endTable();
        }
        IkGui.treePop();
    }

    // ---------------------------------------------------------------------------------------------
    // Scrolling
    // ---------------------------------------------------------------------------------------------

    private static final IkInt verticalScrollFlags =
            new IkInt(
                    TableFlags.SCROLL_Y
                            | TableFlags.ROW_BACKGROUND
                            | TableFlags.BORDERS_OUTER
                            | TableFlags.BORDERS_V
                            | TableFlags.RESIZABLE
                            | TableFlags.REORDERABLE
                            | TableFlags.HIDEABLE);

    private static void showVerticalScrolling() {
        if (!section("Vertical scrolling, with clipping")) {
            return;
        }
        helpMarker(
                "Here we activate SCROLL_Y, which will create a child window container to allow"
                        + " hosting scrollable contents.\n\n"
                        + "We also demonstrate using ListClipper to virtualize the submission of"
                        + " many items.");
        pushStyleCompact();
        IkGui.checkboxFlags("TableFlags.SCROLL_Y", verticalScrollFlags, TableFlags.SCROLL_Y);
        popStyleCompact();

        // When using SCROLL_X or SCROLL_Y we need to specify a size for our table container!
        // Otherwise by default the table will fit all available space, like a beginChild() call.
        if (IkGui.beginTable(
                "table_scrolly", 3, verticalScrollFlags.get(), 0.0f, textBaseHeight * 8)) {
            // Make the top row always visible
            IkGui.tableSetupScrollFreeze(0, 1);
            IkGui.tableSetupColumn("One", TableColumnFlags.NONE);
            IkGui.tableSetupColumn("Two", TableColumnFlags.NONE);
            IkGui.tableSetupColumn("Three", TableColumnFlags.NONE);
            IkGui.tableHeadersRow();

            // Demonstrate using the clipper for large vertical lists
            final ListClipper clipper = new ListClipper();
            clipper.begin(1000);
            while (clipper.step()) {
                for (int row = clipper.displayStart; row < clipper.displayEnd; row++) {
                    IkGui.tableNextRow();
                    for (int column = 0; column < 3; column++) {
                        IkGui.tableSetColumnIndex(column);
                        IkGui.text(String.format("Hello %d,%d", column, row));
                    }
                }
            }
            IkGui.endTable();
        }
        IkGui.treePop();
    }

    private static final IkInt horizontalScrollFlags =
            new IkInt(
                    TableFlags.SCROLL_X
                            | TableFlags.SCROLL_Y
                            | TableFlags.ROW_BACKGROUND
                            | TableFlags.BORDERS_OUTER
                            | TableFlags.BORDERS_V
                            | TableFlags.RESIZABLE
                            | TableFlags.REORDERABLE
                            | TableFlags.HIDEABLE);
    private static final int[] horizontalFreezeColumns = {1};
    private static final int[] horizontalFreezeRows = {1};
    private static final IkInt horizontalScrollFlags2 =
            new IkInt(
                    TableFlags.SIZING_STRETCH_SAME
                            | TableFlags.SCROLL_X
                            | TableFlags.SCROLL_Y
                            | TableFlags.BORDERS_OUTER
                            | TableFlags.ROW_BACKGROUND
                            | TableFlags.CONTEXT_MENU_IN_BODY);
    private static final float[] horizontalInnerWidth = {1000.0f};

    private static void showHorizontalScrolling() {
        if (!section("Horizontal scrolling")) {
            return;
        }
        helpMarker(
                "When SCROLL_X is enabled, the default sizing policy becomes"
                        + " TableFlags.SIZING_FIXED_FIT, as automatically stretching columns"
                        + " doesn't make much sense with horizontal scrolling.\n\n"
                        + "Also note that as of the current version, you will almost always want"
                        + " to enable SCROLL_Y along with SCROLL_X, because the container window"
                        + " won't automatically extend vertically to fix contents (this may be"
                        + " improved in future versions).");
        final IkInt flags = horizontalScrollFlags;
        pushStyleCompact();
        IkGui.checkboxFlags("TableFlags.RESIZABLE", flags, TableFlags.RESIZABLE);
        IkGui.checkboxFlags("TableFlags.SCROLL_X", flags, TableFlags.SCROLL_X);
        IkGui.checkboxFlags("TableFlags.SCROLL_Y", flags, TableFlags.SCROLL_Y);
        IkGui.setNextItemWidth(IkGui.getFrameHeight());
        IkGui.dragInt(
                "freeze_cols", horizontalFreezeColumns, 0.2f, 0, 9, null, SliderFlags.NO_INPUT);
        IkGui.setNextItemWidth(IkGui.getFrameHeight());
        IkGui.dragInt("freeze_rows", horizontalFreezeRows, 0.2f, 0, 9, null, SliderFlags.NO_INPUT);
        popStyleCompact();

        // When using SCROLL_X or SCROLL_Y we need to specify a size for our table container!
        // Otherwise by default the table will fit all available space, like a beginChild() call.
        final float outerHeight = textBaseHeight * 8;
        if (IkGui.beginTable("table_scrollx", 7, flags.get(), 0.0f, outerHeight)) {
            IkGui.tableSetupScrollFreeze(horizontalFreezeColumns[0], horizontalFreezeRows[0]);
            // Make the first column not hideable to match our use of tableSetupScrollFreeze()
            IkGui.tableSetupColumn("Line #", TableColumnFlags.NO_HIDE);
            IkGui.tableSetupColumn("One");
            IkGui.tableSetupColumn("Two");
            IkGui.tableSetupColumn("Three");
            IkGui.tableSetupColumn("Four");
            IkGui.tableSetupColumn("Five");
            IkGui.tableSetupColumn("Six");
            IkGui.tableHeadersRow();
            for (int row = 0; row < 20; row++) {
                IkGui.tableNextRow();
                for (int column = 0; column < 7; column++) {
                    // Both tableNextColumn() and tableSetColumnIndex() return true when a column
                    // is visible or performing width measurement. Because here we know that all
                    // our columns are contributing the same to row height, and column 0 is always
                    // visible, we only always submit this one column and can skip others.
                    if (!IkGui.tableSetColumnIndex(column) && column > 0) {
                        continue;
                    }
                    if (column == 0) {
                        IkGui.text("Line " + row);
                    } else {
                        IkGui.text(String.format("Hello world %d,%d", column, row));
                    }
                }
            }
            IkGui.endTable();
        }

        IkGui.spacing();
        IkGui.textUnformatted("Stretch + ScrollX");
        IkGui.sameLine();
        helpMarker(
                "Showcase using Stretch columns + SCROLL_X together: this is rather unusual and"
                        + " only makes sense when specifying an 'inner_width' for the table!\n"
                        + "Without an explicit value, inner_width is == outer_size.x and therefore"
                        + " using Stretch columns along with SCROLL_X doesn't make sense.");
        pushStyleCompact();
        IkGui.pushID("flags3");
        IkGui.pushItemWidth(textBaseWidth * 30);
        IkGui.checkboxFlags("TableFlags.SCROLL_X", horizontalScrollFlags2, TableFlags.SCROLL_X);
        IkGui.dragFloat("inner_width", horizontalInnerWidth, 1.0f, 0.0f, Float.MAX_VALUE, "%.1f");
        IkGui.popItemWidth();
        IkGui.popID();
        popStyleCompact();
        if (IkGui.beginTable(
                "table2",
                7,
                horizontalScrollFlags2.get(),
                0.0f,
                outerHeight,
                horizontalInnerWidth[0])) {
            for (int cell = 0; cell < 20 * 7; cell++) {
                IkGui.tableNextColumn();
                IkGui.text(
                        String.format(
                                "Hello world %d,%d",
                                IkGui.tableGetColumnIndex(), IkGui.tableGetRowIndex()));
            }
            IkGui.endTable();
        }
        IkGui.treePop();
    }

    // ---------------------------------------------------------------------------------------------
    // Columns flags
    // ---------------------------------------------------------------------------------------------

    private static final String[] COLUMN_NAMES = {"One", "Two", "Three"};
    private static final IkInt[] columnFlags = {
        new IkInt(TableColumnFlags.DEFAULT_SORT),
        new IkInt(TableColumnFlags.NONE),
        new IkInt(TableColumnFlags.DEFAULT_HIDE)
    };

    /** The flags from tableGetColumnFlags(). */
    private static final int[] columnFlagsOut = {0, 0, 0};

    private static void showColumnsFlags() {
        if (!section("Columns flags")) {
            return;
        }
        // Create a first table just to show all the options/flags we want to make visible in our
        // example!
        final int columnCount = 3;
        if (IkGui.beginTable("table_columns_flags_checkboxes", columnCount, TableFlags.NONE)) {
            pushStyleCompact();
            for (int column = 0; column < columnCount; column++) {
                IkGui.tableNextColumn();
                IkGui.pushID(column);
                IkGui.alignTextToFramePadding();
                IkGui.text("'" + COLUMN_NAMES[column] + "'");
                IkGui.spacing();
                IkGui.text("Input flags:");
                editTableColumnsFlags(columnFlags[column]);
                IkGui.spacing();
                IkGui.text("Output flags:");
                IkGui.beginDisabled();
                showTableColumnsStatusFlags(columnFlagsOut[column]);
                IkGui.endDisabled();
                IkGui.popID();
            }
            popStyleCompact();
            IkGui.endTable();
        }

        // Create the real table we care about for the example! We use a scrolling table to be
        // able to showcase the difference between the IS_ENABLED and IS_VISIBLE flags above,
        // otherwise in a non-scrolling table columns are always visible (unless using
        // NO_KEEP_COLUMNS_VISIBLE + resizing the parent window down).
        final int flags =
                TableFlags.SIZING_FIXED_FIT
                        | TableFlags.SCROLL_X
                        | TableFlags.SCROLL_Y
                        | TableFlags.ROW_BACKGROUND
                        | TableFlags.BORDERS_OUTER
                        | TableFlags.BORDERS_V
                        | TableFlags.RESIZABLE
                        | TableFlags.REORDERABLE
                        | TableFlags.HIDEABLE
                        | TableFlags.SORTABLE;
        if (IkGui.beginTable("table_columns_flags", columnCount, flags, 0.0f, textBaseHeight * 9)) {
            boolean hasAngledHeader = false;
            for (int column = 0; column < columnCount; column++) {
                hasAngledHeader |=
                        (columnFlags[column].get() & TableColumnFlags.ANGLED_HEADER) != 0;
                IkGui.tableSetupColumn(COLUMN_NAMES[column], columnFlags[column].get());
            }
            if (hasAngledHeader) {
                IkGui.tableAngledHeadersRow();
            }
            IkGui.tableHeadersRow();
            for (int column = 0; column < columnCount; column++) {
                columnFlagsOut[column] = IkGui.tableGetColumnFlags(column);
            }
            final float indentStep = (float) ((int) textBaseWidth / 2);
            for (int row = 0; row < 8; row++) {
                // Add some indentation to demonstrate usage of per-column INDENT_ENABLE and
                // INDENT_DISABLE flags
                IkGui.indent(indentStep);
                IkGui.tableNextRow();
                for (int column = 0; column < columnCount; column++) {
                    IkGui.tableSetColumnIndex(column);
                    IkGui.text(
                            (column == 0 ? "Indented " : "Hello ")
                                    + IkGui.tableGetColumnName(column));
                }
            }
            IkGui.unindent(indentStep * 8.0f);
            IkGui.endTable();
        }
        IkGui.treePop();
    }

    // ---------------------------------------------------------------------------------------------
    // Columns widths
    // ---------------------------------------------------------------------------------------------

    private static final IkInt widthsFlags1 =
            new IkInt(TableFlags.BORDERS | TableFlags.NO_BORDERS_IN_BODY_UNTIL_RESIZE);
    private static final IkInt widthsFlags2 = new IkInt(TableFlags.NONE);

    /** Show the available width in the first row, and "Hello" in the others. */
    private static void widthRows(int rows, int columns) {
        for (int row = 0; row < rows; row++) {
            IkGui.tableNextRow();
            for (int column = 0; column < columns; column++) {
                IkGui.tableSetColumnIndex(column);
                if (row == 0) {
                    IkGui.text(String.format("(w: %5.1f)", IkGui.getContentRegionAvailable().x));
                } else {
                    IkGui.text(String.format("Hello %d,%d", column, row));
                }
            }
        }
    }

    private static void showColumnsWidths() {
        if (!section("Columns widths")) {
            return;
        }
        helpMarker("Using tableSetupColumn() to setup default width.");

        pushStyleCompact();
        IkGui.checkboxFlags("TableFlags.RESIZABLE", widthsFlags1, TableFlags.RESIZABLE);
        IkGui.checkboxFlags(
                "TableFlags.NO_BORDERS_IN_BODY_UNTIL_RESIZE",
                widthsFlags1,
                TableFlags.NO_BORDERS_IN_BODY_UNTIL_RESIZE);
        popStyleCompact();
        if (IkGui.beginTable("table1", 3, widthsFlags1.get())) {
            // We could also set SIZING_FIXED_FIT on the table and all columns will default to
            // WIDTH_FIXED
            IkGui.tableSetupColumn("one", TableColumnFlags.WIDTH_FIXED, 100.0f);
            IkGui.tableSetupColumn("two", TableColumnFlags.WIDTH_FIXED, 200.0f);
            // Default to auto
            IkGui.tableSetupColumn("three", TableColumnFlags.WIDTH_FIXED);
            IkGui.tableHeadersRow();
            widthRows(4, 3);
            IkGui.endTable();
        }

        helpMarker(
                "Using tableSetupColumn() to setup explicit width.\n\n"
                        + "Unless NO_KEEP_COLUMNS_VISIBLE is set, fixed columns with set width may"
                        + " still be shrunk down if there's not enough space in the host.");

        pushStyleCompact();
        IkGui.checkboxFlags(
                "TableFlags.NO_KEEP_COLUMNS_VISIBLE",
                widthsFlags2,
                TableFlags.NO_KEEP_COLUMNS_VISIBLE);
        IkGui.checkboxFlags("TableFlags.BORDERS_INNER_V", widthsFlags2, TableFlags.BORDERS_INNER_V);
        IkGui.checkboxFlags("TableFlags.BORDERS_OUTER_V", widthsFlags2, TableFlags.BORDERS_OUTER_V);
        popStyleCompact();
        if (IkGui.beginTable("table2", 4, widthsFlags2.get())) {
            // We could also set SIZING_FIXED_FIT on the table and then all columns will default
            // to WIDTH_FIXED
            IkGui.tableSetupColumn("", TableColumnFlags.WIDTH_FIXED, 100.0f);
            IkGui.tableSetupColumn("", TableColumnFlags.WIDTH_FIXED, textBaseWidth * 15.0f);
            IkGui.tableSetupColumn("", TableColumnFlags.WIDTH_FIXED, textBaseWidth * 30.0f);
            IkGui.tableSetupColumn("", TableColumnFlags.WIDTH_FIXED, textBaseWidth * 15.0f);
            widthRows(5, 4);
            IkGui.endTable();
        }
        IkGui.treePop();
    }

    // ---------------------------------------------------------------------------------------------
    // Nested tables
    // ---------------------------------------------------------------------------------------------

    private static void showNestedTables() {
        if (!section("Nested tables")) {
            return;
        }
        helpMarker("This demonstrates embedding a table into another table cell.");

        final int flags =
                TableFlags.BORDERS
                        | TableFlags.RESIZABLE
                        | TableFlags.REORDERABLE
                        | TableFlags.HIDEABLE;
        if (IkGui.beginTable("table_nested1", 2, flags)) {
            IkGui.tableSetupColumn("A0");
            IkGui.tableSetupColumn("A1");
            IkGui.tableHeadersRow();

            IkGui.tableNextColumn();
            IkGui.text("A0 Row 0");
            final float rowsHeight =
                    textBaseHeight * 2.0f + IkGui.getStyle().variable.cellPadding.y * 2.0f;
            if (IkGui.beginTable("table_nested2", 2, flags)) {
                IkGui.tableSetupColumn("B0");
                IkGui.tableSetupColumn("B1");
                IkGui.tableHeadersRow();

                IkGui.tableNextRow(TableRowFlags.NONE, rowsHeight);
                IkGui.tableNextColumn();
                IkGui.text("B0 Row 0");
                IkGui.tableNextColumn();
                IkGui.text("B1 Row 0");
                IkGui.tableNextRow(TableRowFlags.NONE, rowsHeight);
                IkGui.tableNextColumn();
                IkGui.text("B0 Row 1");
                IkGui.tableNextColumn();
                IkGui.text("B1 Row 1");

                IkGui.endTable();
            }
            IkGui.tableNextColumn();
            IkGui.text("A1 Row 0");
            IkGui.tableNextColumn();
            IkGui.text("A0 Row 1");
            IkGui.tableNextColumn();
            IkGui.text("A1 Row 1");
            IkGui.endTable();
        }
        IkGui.treePop();
    }

    // ---------------------------------------------------------------------------------------------
    // Row height
    // ---------------------------------------------------------------------------------------------

    private static void showRowHeight() {
        if (!section("Row height")) {
            return;
        }
        final StyleVariables style = IkGui.getStyle().variable;
        helpMarker(
                "You can pass a 'minRowHeight' to tableNextRow().\n\n"
                        + "Rows are padded with 'style.cellPadding.y' on top and bottom, so"
                        + " effectively the minimum row height will always be >="
                        + " 'style.cellPadding.y * 2.0f'.\n\n"
                        + "We cannot honor a _maximum_ row height as that would require a unique"
                        + " clipping rectangle per row.");
        if (IkGui.beginTable("table_row_height", 1, TableFlags.BORDERS)) {
            for (int row = 0; row < 8; row++) {
                final float minRowHeight =
                        (int) (textBaseHeight * 0.30f * row + style.cellPadding.y * 2.0f);
                IkGui.tableNextRow(TableRowFlags.NONE, minRowHeight);
                IkGui.tableNextColumn();
                IkGui.text(String.format("min_row_height = %.2f", minRowHeight));
            }
            IkGui.endTable();
        }

        helpMarker(
                "Showcase using sameLine(0,0) to share Current Line Height between cells.\n\n"
                        + "Please note that Tables Row Height is not the same thing as Current"
                        + " Line Height, as a table cell may contains multiple lines.");
        if (IkGui.beginTable("table_share_lineheight", 2, TableFlags.BORDERS)) {
            final float[] color = {0.13f, 0.26f, 0.40f, 1.0f};
            IkGui.tableNextRow();
            IkGui.tableNextColumn();
            IkGui.colorButton("##1", color, ColorEditFlags.NONE, 40, 40);
            IkGui.tableNextColumn();
            IkGui.text("Line 1");
            IkGui.text("Line 2");

            IkGui.tableNextRow();
            IkGui.tableNextColumn();
            IkGui.colorButton("##2", color, ColorEditFlags.NONE, 40, 40);
            IkGui.tableNextColumn();
            // Reuse the line height from the previous column
            IkGui.sameLine(0.0f, 0.0f);
            IkGui.text("Line 1, with sameLine(0,0)");
            IkGui.text("Line 2");

            IkGui.endTable();
        }

        helpMarker(
                "Showcase altering cellPadding.y between rows. Note that cellPadding.x is locked"
                        + " for the entire table.");
        if (IkGui.beginTable("table_changing_cellpadding_y", 1, TableFlags.BORDERS)) {
            for (int row = 0; row < 8; row++) {
                if (row % 3 == 2) {
                    IkGui.pushStyleVarY(StyleVariable.CELL_PADDING, 20.0f);
                }
                IkGui.tableNextRow(TableRowFlags.NONE);
                IkGui.tableNextColumn();
                IkGui.text(String.format("CellPadding.y = %.2f", style.cellPadding.y));
                if (row % 3 == 2) {
                    IkGui.popStyleVar();
                }
            }
            IkGui.endTable();
        }
        IkGui.treePop();
    }

    // ---------------------------------------------------------------------------------------------
    // Outer size
    // ---------------------------------------------------------------------------------------------

    private static final IkInt outerSizeFlags =
            new IkInt(
                    TableFlags.BORDERS
                            | TableFlags.RESIZABLE
                            | TableFlags.CONTEXT_MENU_IN_BODY
                            | TableFlags.ROW_BACKGROUND
                            | TableFlags.SIZING_FIXED_FIT
                            | TableFlags.NO_HOST_EXTEND_X);

    /** Fill a table with "Cell column,row" text. */
    private static void cellRows(int rows, int columns, float minRowHeight) {
        for (int row = 0; row < rows; row++) {
            IkGui.tableNextRow(TableRowFlags.NONE, minRowHeight);
            for (int column = 0; column < columns; column++) {
                IkGui.tableNextColumn();
                IkGui.text(String.format("Cell %d,%d", column, row));
            }
        }
    }

    private static void showOuterSize() {
        if (!section("Outer size")) {
            return;
        }
        // Showcasing use of NO_HOST_EXTEND_X and NO_HOST_EXTEND_Y. Important to note how the two
        // flags have slightly different behaviors!
        IkGui.text("Using NoHostExtendX and NoHostExtendY:");
        pushStyleCompact();
        IkGui.checkboxFlags(
                "TableFlags.NO_HOST_EXTEND_X", outerSizeFlags, TableFlags.NO_HOST_EXTEND_X);
        IkGui.sameLine();
        helpMarker(
                "Make outer width auto-fit to columns, overriding outer_size.x value.\n\n"
                        + "Only available when SCROLL_X/SCROLL_Y are disabled and Stretch columns"
                        + " are not used.");
        IkGui.checkboxFlags(
                "TableFlags.NO_HOST_EXTEND_Y", outerSizeFlags, TableFlags.NO_HOST_EXTEND_Y);
        IkGui.sameLine();
        helpMarker(
                "Make outer height stop exactly at outer_size.y (prevent auto-extending table past"
                        + " the limit).\n\n"
                        + "Only available when SCROLL_X/SCROLL_Y are disabled. Data below the limit"
                        + " will be clipped and not visible.");
        popStyleCompact();

        if (IkGui.beginTable("table1", 3, outerSizeFlags.get(), 0.0f, textBaseHeight * 5.5f)) {
            cellRows(10, 3, 0.0f);
            IkGui.endTable();
        }
        IkGui.sameLine();
        IkGui.text("Hello!");

        IkGui.spacing();

        IkGui.text("Using explicit size:");
        final int flags = TableFlags.BORDERS | TableFlags.ROW_BACKGROUND;
        if (IkGui.beginTable("table2", 3, flags, textBaseWidth * 30, 0.0f)) {
            cellRows(5, 3, 0.0f);
            IkGui.endTable();
        }
        IkGui.sameLine();
        if (IkGui.beginTable("table3", 3, flags, textBaseWidth * 30, 0.0f)) {
            cellRows(3, 3, textBaseHeight * 1.5f + IkGui.getStyle().variable.cellPadding.y * 2.0f);
            IkGui.endTable();
        }
        IkGui.treePop();
    }

    // ---------------------------------------------------------------------------------------------
    // Background color
    // ---------------------------------------------------------------------------------------------

    private static final IkInt backgroundFlags = new IkInt(TableFlags.ROW_BACKGROUND);
    private static final IkInt backgroundRowType = new IkInt(1);
    private static final IkInt backgroundRowTarget = new IkInt(1);
    private static final IkInt backgroundCellType = new IkInt(1);

    private static void showBackgroundColor() {
        if (!section("Background color")) {
            return;
        }
        pushStyleCompact();
        IkGui.checkboxFlags("TableFlags.BORDERS", backgroundFlags, TableFlags.BORDERS);
        IkGui.checkboxFlags(
                "TableFlags.ROW_BACKGROUND", backgroundFlags, TableFlags.ROW_BACKGROUND);
        IkGui.sameLine();
        helpMarker(
                "TableFlags.ROW_BACKGROUND automatically sets ROW_BACKGROUND_0 to alternative"
                        + " colors pulled from the Style.");
        IkGui.combo("row bg type", backgroundRowType, new String[] {"None", "Red", "Gradient"});
        IkGui.combo(
                "row bg target",
                backgroundRowTarget,
                new String[] {"ROW_BACKGROUND_0", "ROW_BACKGROUND_1"});
        IkGui.sameLine();
        helpMarker(
                "Target ROW_BACKGROUND_0 to override the alternating odd/even colors,\n"
                        + "Target ROW_BACKGROUND_1 to blend with them.");
        IkGui.combo("cell bg type", backgroundCellType, new String[] {"None", "Blue"});
        IkGui.sameLine();
        helpMarker("We are colorizing cells to B1->C2 here.");
        popStyleCompact();

        if (IkGui.beginTable("table1", 5, backgroundFlags.get())) {
            for (int row = 0; row < 6; row++) {
                IkGui.tableNextRow();

                // Demonstrate setting a row background color with
                // tableSetBackgroundColor(ROW_BACKGROUND_X, ...). We use a transparent color so we
                // can see the one behind in case our target is ROW_BACKGROUND_1 and
                // ROW_BACKGROUND_0 was already targeted by the ROW_BACKGROUND flag.
                if (backgroundRowType.get() != 0) {
                    final int rowColor =
                            backgroundRowType.get() == 1
                                    ? Color.rgba(0.7f, 0.3f, 0.3f, 0.65f)
                                    : Color.rgba(0.2f + row * 0.1f, 0.2f, 0.2f, 0.65f);
                    IkGui.tableSetBackgroundColor(
                            backgroundRowTarget.get() == 0
                                    ? TableBackgroundTarget.ROW_BACKGROUND_0
                                    : TableBackgroundTarget.ROW_BACKGROUND_1,
                            IkGui.getColorU32(rowColor));
                }

                // Fill cells
                for (int column = 0; column < 5; column++) {
                    IkGui.tableSetColumnIndex(column);
                    IkGui.text("" + (char) ('A' + row) + (char) ('0' + column));

                    // Change the background of cells B1->C2. Demonstrate setting a cell background
                    // color with tableSetBackgroundColor(CELL_BACKGROUND, ...), which is blended
                    // over the row and column background colors. We can also pass a column number
                    // as a third parameter and do this outside the column loop.
                    if (row >= 1
                            && row <= 2
                            && column >= 1
                            && column <= 2
                            && backgroundCellType.get() == 1) {
                        IkGui.tableSetBackgroundColor(
                                TableBackgroundTarget.CELL_BACKGROUND,
                                IkGui.getColorU32(Color.rgba(0.3f, 0.3f, 0.7f, 0.65f)));
                    }
                }
            }
            IkGui.endTable();
        }
        IkGui.treePop();
    }

    // ---------------------------------------------------------------------------------------------
    // Tree view
    // ---------------------------------------------------------------------------------------------

    /** A node of the dummy file system, in a flat array. */
    private record TreeViewNode(
            String name, String type, int size, int childIndex, int childCount) {}

    private static final TreeViewNode[] TREE_VIEW_NODES = {
        new TreeViewNode("Root with Long Name", "Folder", -1, 1, 3),
        new TreeViewNode("Music", "Folder", -1, 4, 2),
        new TreeViewNode("Textures", "Folder", -1, 6, 3),
        new TreeViewNode("desktop.ini", "System file", 1024, -1, -1),
        new TreeViewNode("File1_a.wav", "Audio file", 123_000, -1, -1),
        new TreeViewNode("File1_b.wav", "Audio file", 456_000, -1, -1),
        new TreeViewNode("Image001.png", "Image file", 203_128, -1, -1),
        new TreeViewNode("Copy of Image001.png", "Image file", 203_256, -1, -1),
        new TreeViewNode("Copy of Image001 (Final2).png", "Image file", 203_512, -1, -1),
    };

    private static final IkInt treeViewNodeFlags =
            new IkInt(
                    TreeNodeFlags.SPAN_ALL_COLUMNS
                            | TreeNodeFlags.DEFAULT_OPEN
                            | TreeNodeFlags.DRAW_LINES_FULL);

    private static void showTreeViewNode(int index) {
        final TreeViewNode node = TREE_VIEW_NODES[index];
        IkGui.tableNextRow();
        IkGui.tableNextColumn();
        final boolean isFolder = node.childCount() > 0;

        int nodeFlags = treeViewNodeFlags.get();
        if (index != 0) {
            // Only demonstrate this on the root node
            nodeFlags &= ~TreeNodeFlags.LABEL_SPAN_ALL_COLUMNS;
        }

        if (isFolder) {
            final boolean open = IkGui.treeNodeEx(node.name(), nodeFlags);
            if ((nodeFlags & TreeNodeFlags.LABEL_SPAN_ALL_COLUMNS) == 0) {
                IkGui.tableNextColumn();
                IkGui.textDisabled("--");
                IkGui.tableNextColumn();
                IkGui.textUnformatted(node.type());
            }
            if (open) {
                for (int child = 0; child < node.childCount(); child++) {
                    showTreeViewNode(node.childIndex() + child);
                }
                IkGui.treePop();
            }
        } else {
            IkGui.treeNodeEx(
                    node.name(),
                    nodeFlags
                            | TreeNodeFlags.LEAF
                            | TreeNodeFlags.BULLET
                            | TreeNodeFlags.NO_TREE_PUSH_ON_OPEN);
            IkGui.tableNextColumn();
            IkGui.text(String.valueOf(node.size()));
            IkGui.tableNextColumn();
            IkGui.textUnformatted(node.type());
        }
    }

    private static void showTreeView() {
        if (!section("Tree view")) {
            return;
        }
        final int tableFlags =
                TableFlags.BORDERS_V
                        | TableFlags.BORDERS_OUTER_H
                        | TableFlags.RESIZABLE
                        | TableFlags.ROW_BACKGROUND
                        | TableFlags.NO_BORDERS_IN_BODY;

        final IkInt flags = treeViewNodeFlags;
        IkGui.checkboxFlags("TreeNodeFlags.SPAN_FULL_WIDTH", flags, TreeNodeFlags.SPAN_FULL_WIDTH);
        IkGui.checkboxFlags(
                "TreeNodeFlags.SPAN_LABEL_WIDTH", flags, TreeNodeFlags.SPAN_LABEL_WIDTH);
        IkGui.checkboxFlags(
                "TreeNodeFlags.SPAN_ALL_COLUMNS", flags, TreeNodeFlags.SPAN_ALL_COLUMNS);
        IkGui.checkboxFlags(
                "TreeNodeFlags.LABEL_SPAN_ALL_COLUMNS",
                flags,
                TreeNodeFlags.LABEL_SPAN_ALL_COLUMNS);
        IkGui.sameLine();
        helpMarker("Useful if you know that you aren't displaying contents in other columns");

        helpMarker(
                "See \"Columns flags\" section to configure how indentation is applied to"
                        + " individual columns.");
        if (IkGui.beginTable("3ways", 3, tableFlags)) {
            // The first column will use the default WIDTH_STRETCH when SCROLL_X is off and
            // WIDTH_FIXED when SCROLL_X is on
            IkGui.tableSetupColumn("Name", TableColumnFlags.NO_HIDE);
            IkGui.tableSetupColumn("Size", TableColumnFlags.WIDTH_FIXED, textBaseWidth * 12.0f);
            IkGui.tableSetupColumn("Type", TableColumnFlags.WIDTH_FIXED, textBaseWidth * 18.0f);
            IkGui.tableHeadersRow();
            showTreeViewNode(0);
            IkGui.endTable();
        }
        IkGui.treePop();
    }

    // ---------------------------------------------------------------------------------------------
    // Item width
    // ---------------------------------------------------------------------------------------------

    private static final float[] itemWidthValue = {0.0f};

    private static void showItemWidth() {
        if (!section("Item width")) {
            return;
        }
        helpMarker(
                "Showcase using pushItemWidth() and how it is preserved on a per-column"
                        + " basis.\n\n"
                        + "Note that on auto-resizing non-resizable fixed columns, querying the"
                        + " content width for e.g. right-alignment doesn't make sense.");
        if (IkGui.beginTable("table_item_width", 3, TableFlags.BORDERS)) {
            IkGui.tableSetupColumn("small");
            IkGui.tableSetupColumn("half");
            IkGui.tableSetupColumn("right-align");
            IkGui.tableHeadersRow();

            for (int row = 0; row < 3; row++) {
                IkGui.tableNextRow();
                if (row == 0) {
                    // Set up the item width once (instead of setting up every time, which is also
                    // possible but less efficient)
                    IkGui.tableSetColumnIndex(0);
                    IkGui.pushItemWidth(textBaseWidth * 3.0f);
                    IkGui.tableSetColumnIndex(1);
                    IkGui.pushItemWidth(-IkGui.getContentRegionAvailable().x * 0.5f);
                    IkGui.tableSetColumnIndex(2);
                    IkGui.pushItemWidth(-Float.MIN_VALUE);
                }

                // Draw our contents
                IkGui.pushID(row);
                IkGui.tableSetColumnIndex(0);
                IkGui.sliderFloat("float0", itemWidthValue, 0.0f, 1.0f);
                IkGui.tableSetColumnIndex(1);
                IkGui.sliderFloat("float1", itemWidthValue, 0.0f, 1.0f);
                IkGui.tableSetColumnIndex(2);
                // No visible label since it's right-aligned
                IkGui.sliderFloat("##float2", itemWidthValue, 0.0f, 1.0f);
                IkGui.popID();
            }
            IkGui.endTable();
        }
        IkGui.treePop();
    }

    // ---------------------------------------------------------------------------------------------
    // Custom headers
    // ---------------------------------------------------------------------------------------------

    /** Dummy entire-column selection storage. */
    private static final IkBoolean[] customHeadersSelected = {
        new IkBoolean(false), new IkBoolean(false), new IkBoolean(false)
    };

    private static void showCustomHeaders() {
        if (!section("Custom headers")) {
            return;
        }
        // Demonstrate using tableHeader() calls instead of tableHeadersRow()
        final int columnCount = 3;
        if (IkGui.beginTable(
                "table_custom_headers",
                columnCount,
                TableFlags.BORDERS | TableFlags.REORDERABLE | TableFlags.HIDEABLE)) {
            IkGui.tableSetupColumn("Apricot");
            IkGui.tableSetupColumn("Banana");
            IkGui.tableSetupColumn("Cherry");

            // Instead of calling tableHeadersRow() we'll submit custom headers ourselves. A
            // different approach is also possible:
            // - Specify TableColumnFlags.NO_HEADER_LABEL in some tableSetupColumn() call.
            // - Call tableHeadersRow() normally. This will submit tableHeader() with no name.
            // - Then call tableSetColumnIndex() to position yourself in the column and submit
            //   your stuff e.g. checkbox().
            IkGui.tableNextRow(TableRowFlags.HEADERS);
            for (int column = 0; column < columnCount; column++) {
                IkGui.tableSetColumnIndex(column);
                // Retrieve the name passed to tableSetupColumn()
                final String columnName = IkGui.tableGetColumnName(column);
                IkGui.pushID(column);
                IkGui.pushStyleVarFloat2(StyleVariable.FRAME_PADDING, 0, 0);
                IkGui.checkbox("##checkall", customHeadersSelected[column]);
                IkGui.popStyleVar();
                IkGui.sameLine(0.0f, IkGui.getStyle().variable.itemInnerSpacing.x);
                IkGui.tableHeader(columnName);
                IkGui.popID();
            }

            // Submit the table contents
            for (int row = 0; row < 5; row++) {
                IkGui.tableNextRow();
                for (int column = 0; column < 3; column++) {
                    IkGui.tableSetColumnIndex(column);
                    IkGui.selectable(
                            String.format("Cell %d,%d", column, row),
                            customHeadersSelected[column].get());
                }
            }
            IkGui.endTable();
        }
        IkGui.treePop();
    }

    // ---------------------------------------------------------------------------------------------
    // Angled headers
    // ---------------------------------------------------------------------------------------------

    private static final String[] ANGLED_COLUMN_NAMES = {
        "Track", "cabasa", "ride", "smash", "tom-hi", "tom-mid", "tom-low", "hihat-o", "hihat-c",
        "snare-s", "snare-c", "clap", "rim", "kick"
    };
    private static final int ANGLED_ROW_COUNT = 12;
    private static final IkInt angledTableFlags =
            new IkInt(
                    TableFlags.SIZING_FIXED_FIT
                            | TableFlags.SCROLL_X
                            | TableFlags.SCROLL_Y
                            | TableFlags.BORDERS_OUTER
                            | TableFlags.BORDERS_INNER_H
                            | TableFlags.HIDEABLE
                            | TableFlags.RESIZABLE
                            | TableFlags.REORDERABLE
                            | TableFlags.HIGHLIGHT_HOVERED_COLUMN);
    private static final IkInt angledColumnFlags =
            new IkInt(TableColumnFlags.ANGLED_HEADER | TableColumnFlags.WIDTH_FIXED);

    /** Dummy selection storage. */
    private static final IkBoolean[] angledBools =
            newBooleans(ANGLED_COLUMN_NAMES.length * ANGLED_ROW_COUNT);

    private static final int[] angledFrozenColumns = {1};
    private static final int[] angledFrozenRows = {2};

    private static IkBoolean[] newBooleans(int count) {
        final IkBoolean[] result = new IkBoolean[count];
        for (int i = 0; i < count; i++) {
            result[i] = new IkBoolean(false);
        }
        return result;
    }

    private static void showAngledHeaders() {
        if (!section("Angled headers")) {
            return;
        }
        final int columnCount = ANGLED_COLUMN_NAMES.length;
        final IkInt tableFlags = angledTableFlags;
        IkGui.checkboxFlags("SCROLL_X", tableFlags, TableFlags.SCROLL_X);
        IkGui.checkboxFlags("SCROLL_Y", tableFlags, TableFlags.SCROLL_Y);
        IkGui.checkboxFlags("RESIZABLE", tableFlags, TableFlags.RESIZABLE);
        IkGui.checkboxFlags("SORTABLE", tableFlags, TableFlags.SORTABLE);
        IkGui.checkboxFlags("NO_BORDERS_IN_BODY", tableFlags, TableFlags.NO_BORDERS_IN_BODY);
        IkGui.checkboxFlags(
                "HIGHLIGHT_HOVERED_COLUMN", tableFlags, TableFlags.HIGHLIGHT_HOVERED_COLUMN);
        IkGui.setNextItemWidth(IkGui.getTextLineHeight() * 8);
        IkGui.sliderInt("Frozen columns", angledFrozenColumns, 0, 2);
        IkGui.setNextItemWidth(IkGui.getTextLineHeight() * 8);
        IkGui.sliderInt("Frozen rows", angledFrozenRows, 0, 2);
        IkGui.checkboxFlags(
                "Disable header contributing to column width",
                angledColumnFlags,
                TableColumnFlags.NO_HEADER_WIDTH);

        if (IkGui.treeNode("Style settings")) {
            final StyleVariables style = IkGui.getStyle().variable;
            IkGui.sameLine();
            helpMarker("Giving access to some style values in this demo for convenience.");
            // The port stores this angle in degrees
            final float[] angle = {style.tableAngledHeadersAngle};
            IkGui.setNextItemWidth(IkGui.getTextLineHeight() * 8);
            if (IkGui.sliderFloat(
                    "style.tableAngledHeadersAngle", angle, -50.0f, 50.0f, "%.0f deg")) {
                style.tableAngledHeadersAngle = angle[0];
            }
            final float[] align = {
                style.tableAngledHeadersTextAlign.x, style.tableAngledHeadersTextAlign.y
            };
            IkGui.setNextItemWidth(IkGui.getTextLineHeight() * 8);
            if (IkGui.sliderFloat2(
                    "style.tableAngledHeadersTextAlign", align, 0.0f, 1.0f, "%.2f")) {
                style.tableAngledHeadersTextAlign.set(align[0], align[1]);
            }
            IkGui.treePop();
        }

        if (IkGui.beginTable(
                "table_angled_headers", columnCount, tableFlags.get(), 0.0f, textBaseHeight * 12)) {
            IkGui.tableSetupColumn(
                    ANGLED_COLUMN_NAMES[0], TableColumnFlags.NO_HIDE | TableColumnFlags.NO_REORDER);
            for (int n = 1; n < columnCount; n++) {
                IkGui.tableSetupColumn(ANGLED_COLUMN_NAMES[n], angledColumnFlags.get());
            }
            IkGui.tableSetupScrollFreeze(angledFrozenColumns[0], angledFrozenRows[0]);

            // Draw angled headers for all columns with the ANGLED_HEADER flag
            IkGui.tableAngledHeadersRow();
            // Draw the remaining headers and allow access to the context menu and other functions
            IkGui.tableHeadersRow();
            for (int row = 0; row < ANGLED_ROW_COUNT; row++) {
                IkGui.pushID(row);
                IkGui.tableNextRow();
                IkGui.tableSetColumnIndex(0);
                IkGui.alignTextToFramePadding();
                IkGui.text("Track " + row);
                for (int column = 1; column < columnCount; column++) {
                    if (IkGui.tableSetColumnIndex(column)) {
                        IkGui.pushID(column);
                        IkGui.checkbox("", angledBools[row * columnCount + column]);
                        IkGui.popID();
                    }
                }
                IkGui.popID();
            }
            IkGui.endTable();
        }
        IkGui.treePop();
    }

    // ---------------------------------------------------------------------------------------------
    // Context menus
    // ---------------------------------------------------------------------------------------------

    private static final IkInt contextMenuFlags =
            new IkInt(
                    TableFlags.RESIZABLE
                            | TableFlags.REORDERABLE
                            | TableFlags.HIDEABLE
                            | TableFlags.BORDERS
                            | TableFlags.CONTEXT_MENU_IN_BODY);

    private static void showContextMenus() {
        if (!section("Context menus")) {
            return;
        }
        // Demonstrate creating custom context menus inside columns, while playing nice with the
        // context menus provided by tableHeadersRow()/tableHeader()
        helpMarker(
                "By default, right-clicking over a tableHeadersRow()/tableHeader() line will open"
                        + " the default context-menu.\n"
                        + "Using TableFlags.CONTEXT_MENU_IN_BODY we also allow right-clicking over"
                        + " columns body.");

        pushStyleCompact();
        IkGui.checkboxFlags(
                "TableFlags.CONTEXT_MENU_IN_BODY",
                contextMenuFlags,
                TableFlags.CONTEXT_MENU_IN_BODY);
        popStyleCompact();

        // Context menus: first example
        // [1.1] Right-click on the tableHeadersRow() line to open the default table context menu.
        // [1.2] Right-click in columns also opens the default table context menu (if
        //       CONTEXT_MENU_IN_BODY is set)
        final int columnCount = 3;
        if (IkGui.beginTable("table_context_menu", columnCount, contextMenuFlags.get())) {
            IkGui.tableSetupColumn("One");
            IkGui.tableSetupColumn("Two");
            IkGui.tableSetupColumn("Three");

            // [1.1] Right-click on the tableHeadersRow() line to open the default table context
            // menu
            IkGui.tableHeadersRow();

            // Submit dummy contents
            for (int row = 0; row < 4; row++) {
                IkGui.tableNextRow();
                for (int column = 0; column < columnCount; column++) {
                    IkGui.tableSetColumnIndex(column);
                    IkGui.text(String.format("Cell %d,%d", column, row));
                }
            }
            IkGui.endTable();
        }

        // Context menus: second example
        // [2.1] Right-click on the tableHeadersRow() line to open the default table context menu.
        // [2.2] Right-click on the ".." to open a custom popup
        // [2.3] Right-click in columns to open another custom popup
        helpMarker(
                "Demonstrate mixing table context menu (over header), item context button (over"
                        + " button) and custom per-column context menu (over column body).");
        final int flags2 =
                TableFlags.RESIZABLE
                        | TableFlags.SIZING_FIXED_FIT
                        | TableFlags.REORDERABLE
                        | TableFlags.HIDEABLE
                        | TableFlags.BORDERS;
        if (IkGui.beginTable("table_context_menu_2", columnCount, flags2)) {
            IkGui.tableSetupColumn("One");
            IkGui.tableSetupColumn("Two");
            IkGui.tableSetupColumn("Three");

            // [2.1] Right-click on the tableHeadersRow() line to open the default table context
            // menu
            IkGui.tableHeadersRow();
            for (int row = 0; row < 4; row++) {
                IkGui.tableNextRow();
                for (int column = 0; column < columnCount; column++) {
                    // Submit dummy contents
                    IkGui.tableSetColumnIndex(column);
                    IkGui.text(String.format("Cell %d,%d", column, row));
                    IkGui.sameLine();

                    // [2.2] Right-click on the ".." to open a custom popup
                    IkGui.pushID(row * columnCount + column);
                    IkGui.smallButton("..");
                    if (IkGui.beginPopupContextItem()) {
                        IkGui.text(
                                String.format(
                                        "This is the popup for Button(\"..\") in Cell %d,%d",
                                        column, row));
                        if (IkGui.button("Close")) {
                            IkGui.closeCurrentPopup();
                        }
                        IkGui.endPopup();
                    }
                    IkGui.popID();
                }
            }

            // [2.3] Right-click anywhere in columns to open another custom popup. Instead of
            // testing for !isAnyItemHovered() we could also call openPopup() with
            // PopupFlags.NO_OPEN_OVER_EXISTING_POPUP to manage popup priority, as the popup
            // triggers (are we hovering a column) are overlapping.
            int hoveredColumn = -1;
            for (int column = 0; column < columnCount + 1; column++) {
                IkGui.pushID(column);
                if ((IkGui.tableGetColumnFlags(column) & TableColumnFlags.IS_HOVERED) != 0) {
                    hoveredColumn = column;
                }
                if (hoveredColumn == column
                        && !IkGui.isAnyItemHovered()
                        && IkGui.isMouseReleased(MouseButton.RIGHT)) {
                    IkGui.openPopup("MyPopup");
                }
                if (IkGui.beginPopup("MyPopup")) {
                    if (column == columnCount) {
                        IkGui.text(
                                "This is a custom popup for unused space after the last column.");
                    } else {
                        IkGui.text("This is a custom popup for Column " + column);
                    }
                    if (IkGui.button("Close")) {
                        IkGui.closeCurrentPopup();
                    }
                    IkGui.endPopup();
                }
                IkGui.popID();
            }

            IkGui.endTable();
            IkGui.text("Hovered column: " + hoveredColumn);
        }
        IkGui.treePop();
    }

    // ---------------------------------------------------------------------------------------------
    // Synced instances
    // ---------------------------------------------------------------------------------------------

    private static final IkInt syncedFlags =
            new IkInt(
                    TableFlags.RESIZABLE
                            | TableFlags.REORDERABLE
                            | TableFlags.HIDEABLE
                            | TableFlags.BORDERS
                            | TableFlags.SIZING_FIXED_FIT
                            | TableFlags.NO_SAVED_SETTINGS);

    private static void showSyncedInstances() {
        if (!section("Synced instances")) {
            return;
        }
        // Demonstrate creating multiple tables with the same ID
        helpMarker(
                "Multiple tables with the same identifier will share their settings, width,"
                        + " visibility, order etc.");

        final IkInt flags = syncedFlags;
        IkGui.checkboxFlags("TableFlags.RESIZABLE", flags, TableFlags.RESIZABLE);
        IkGui.checkboxFlags("TableFlags.SCROLL_Y", flags, TableFlags.SCROLL_Y);
        IkGui.checkboxFlags("TableFlags.SIZING_FIXED_FIT", flags, TableFlags.SIZING_FIXED_FIT);
        IkGui.checkboxFlags(
                "TableFlags.HIGHLIGHT_HOVERED_COLUMN", flags, TableFlags.HIGHLIGHT_HOVERED_COLUMN);
        for (int n = 0; n < 3; n++) {
            final boolean open =
                    IkGui.collapsingHeader("Synced Table " + n, null, TreeNodeFlags.DEFAULT_OPEN);
            if (open
                    && IkGui.beginTable(
                            "Table",
                            3,
                            flags.get(),
                            0.0f,
                            IkGui.getTextLineHeightWithSpacing() * 5)) {
                IkGui.tableSetupColumn("One");
                IkGui.tableSetupColumn("Two");
                IkGui.tableSetupColumn("Three");
                IkGui.tableHeadersRow();
                // Make the second table have a scrollbar to verify that additional decoration is
                // not affecting column positions
                final int cellCount = n == 1 ? 27 : 9;
                for (int cell = 0; cell < cellCount; cell++) {
                    IkGui.tableNextColumn();
                    IkGui.text("this cell " + cell);
                }
                IkGui.endTable();
            }
        }
        IkGui.treePop();
    }

    // ---------------------------------------------------------------------------------------------
    // Sorting
    // ---------------------------------------------------------------------------------------------

    private static final String[] TEMPLATE_ITEM_NAMES = {
        "Banana",
        "Apple",
        "Cherry",
        "Watermelon",
        "Grapefruit",
        "Strawberry",
        "Mango",
        "Kiwi",
        "Orange",
        "Pineapple",
        "Blueberry",
        "Plum",
        "Coconut",
        "Pear",
        "Apricot"
    };

    // The user IDs of the columns, passed to tableSetupColumn() and stored in the sort specs
    static final int ITEM_COLUMN_ID = 0;
    static final int ITEM_COLUMN_NAME = 1;
    private static final int ITEM_COLUMN_ACTION = 2;
    static final int ITEM_COLUMN_QUANTITY = 3;
    private static final int ITEM_COLUMN_DESCRIPTION = 4;

    /** A row of the sorting demos. */
    static final class SortItem {
        final int id;
        final String name;
        int quantity;

        SortItem(int id, String name, int quantity) {
            this.id = id;
            this.name = name;
            this.quantity = quantity;
        }
    }

    /**
     * Sort items using the table sort specs. We identify columns using the user ID that we passed
     * to tableSetupColumn(). We could also identify them using the column index, which is simpler!
     */
    static void sortWithSortSpecs(TableSortSpecs sortSpecs, List<SortItem> items) {
        final Comparator<SortItem> comparator =
                (a, b) -> {
                    for (int n = 0; n < sortSpecs.specsCount; n++) {
                        final TableColumnSortSpecs spec = sortSpecs.specs[n];
                        final int delta =
                                switch (spec.columnUserID) {
                                    case ITEM_COLUMN_ID -> Integer.compare(a.id, b.id);
                                    case ITEM_COLUMN_QUANTITY ->
                                            Integer.compare(a.quantity, b.quantity);
                                    case ITEM_COLUMN_NAME, ITEM_COLUMN_DESCRIPTION ->
                                            a.name.compareTo(b.name);
                                    default -> 0;
                                };
                        if (delta != 0) {
                            return spec.sortDirection == SortDirection.ASCENDING ? delta : -delta;
                        }
                    }
                    // Always have a way to differentiate items. Your own compare function may want
                    // to avoid falling back on implicit sort specs, e.g. a name compare if it
                    // wasn't already part of the sort specs.
                    return Integer.compare(a.id, b.id);
                };
        items.sort(comparator);
    }

    private static final List<SortItem> sortingItems = new ArrayList<>();
    private static final IkInt sortingFlags =
            new IkInt(
                    TableFlags.RESIZABLE
                            | TableFlags.REORDERABLE
                            | TableFlags.HIDEABLE
                            | TableFlags.SORTABLE
                            | TableFlags.SORT_MULTI
                            | TableFlags.ROW_BACKGROUND
                            | TableFlags.BORDERS_OUTER
                            | TableFlags.BORDERS_V
                            | TableFlags.NO_BORDERS_IN_BODY
                            | TableFlags.SCROLL_Y);

    private static void showSorting() {
        if (!section("Sorting")) {
            return;
        }
        // Demonstrate using the sorting facilities. This is a simplified version of the
        // "Advanced" example, where we mostly focus on the code necessary to handle sorting. Note
        // that the "Advanced" example also showcases manually triggering a sort (e.g. if item
        // quantities have been modified).

        // Create the item list
        if (sortingItems.isEmpty()) {
            for (int n = 0; n < 50; n++) {
                sortingItems.add(
                        new SortItem(
                                n,
                                TEMPLATE_ITEM_NAMES[n % TEMPLATE_ITEM_NAMES.length],
                                (n * n - n) % 20));
            }
        }

        // Options
        final IkInt flags = sortingFlags;
        pushStyleCompact();
        IkGui.checkboxFlags("TableFlags.SORT_MULTI", flags, TableFlags.SORT_MULTI);
        IkGui.sameLine();
        helpMarker(
                "When sorting is enabled: hold shift when clicking headers to sort on multiple"
                        + " column. tableGetSortSpecs() may return specs where (specsCount > 1).");
        IkGui.checkboxFlags("TableFlags.SORT_TRISTATE", flags, TableFlags.SORT_TRISTATE);
        IkGui.sameLine();
        helpMarker(
                "When sorting is enabled: allow no sorting, disable default sorting."
                        + " tableGetSortSpecs() may return specs where (specsCount == 0).");
        popStyleCompact();

        if (IkGui.beginTable("table_sorting", 4, flags.get(), 0.0f, textBaseHeight * 15, 0.0f)) {
            // Declare columns. We use the "userID" parameter of tableSetupColumn() to specify a
            // user ID that will be stored in the sort specifications, so our sort function can
            // identify a column given our own identifier. We could also identify them based on
            // their index! This demonstrates a mixture of the sort-related flags:
            // - TableColumnFlags.DEFAULT_SORT
            // - TableColumnFlags.NO_SORT / NO_SORT_ASCENDING / NO_SORT_DESCENDING
            // - TableColumnFlags.PREFER_SORT_ASCENDING / PREFER_SORT_DESCENDING
            IkGui.tableSetupColumn(
                    "ID",
                    TableColumnFlags.DEFAULT_SORT | TableColumnFlags.WIDTH_FIXED,
                    0.0f,
                    ITEM_COLUMN_ID);
            IkGui.tableSetupColumn("Name", TableColumnFlags.WIDTH_FIXED, 0.0f, ITEM_COLUMN_NAME);
            IkGui.tableSetupColumn(
                    "Action",
                    TableColumnFlags.NO_SORT | TableColumnFlags.WIDTH_FIXED,
                    0.0f,
                    ITEM_COLUMN_ACTION);
            IkGui.tableSetupColumn(
                    "Quantity",
                    TableColumnFlags.PREFER_SORT_DESCENDING | TableColumnFlags.WIDTH_STRETCH,
                    0.0f,
                    ITEM_COLUMN_QUANTITY);
            // Make the row always visible
            IkGui.tableSetupScrollFreeze(0, 1);
            IkGui.tableHeadersRow();

            // Sort our data if the sort specs have been changed!
            final TableSortSpecs sortSpecs = IkGui.tableGetSortSpecs();
            if (sortSpecs != null && sortSpecs.specsDirty) {
                sortWithSortSpecs(sortSpecs, sortingItems);
                sortSpecs.specsDirty = false;
            }

            // Demonstrate using the clipper for large vertical lists
            final ListClipper clipper = new ListClipper();
            clipper.begin(sortingItems.size());
            while (clipper.step()) {
                for (int row = clipper.displayStart; row < clipper.displayEnd; row++) {
                    // Display a data item
                    final SortItem item = sortingItems.get(row);
                    IkGui.pushID(item.id);
                    IkGui.tableNextRow();
                    IkGui.tableNextColumn();
                    IkGui.text(String.format("%04d", item.id));
                    IkGui.tableNextColumn();
                    IkGui.textUnformatted(item.name);
                    IkGui.tableNextColumn();
                    IkGui.smallButton("None");
                    IkGui.tableNextColumn();
                    IkGui.text(String.valueOf(item.quantity));
                    IkGui.popID();
                }
            }
            IkGui.endTable();
        }
        IkGui.treePop();
    }

    // ---------------------------------------------------------------------------------------------
    // Advanced
    // ---------------------------------------------------------------------------------------------

    private static final IkInt advancedFlags =
            new IkInt(
                    TableFlags.RESIZABLE
                            | TableFlags.REORDERABLE
                            | TableFlags.HIDEABLE
                            | TableFlags.SORTABLE
                            | TableFlags.SORT_MULTI
                            | TableFlags.ROW_BACKGROUND
                            | TableFlags.BORDERS
                            | TableFlags.NO_BORDERS_IN_BODY
                            | TableFlags.SCROLL_X
                            | TableFlags.SCROLL_Y
                            | TableFlags.SIZING_FIXED_FIT);
    private static final IkInt advancedColumnsBaseFlags = new IkInt(TableColumnFlags.NONE);
    private static final String[] ADVANCED_CONTENTS_TYPES = {
        "Text", "Button", "SmallButton", "FillButton", "Selectable", "Selectable (span row)"
    };

    /** Which of ADVANCED_CONTENTS_TYPES to show in the first column. */
    private static final IkInt advancedContentsType = new IkInt(5);

    private static final int[] advancedFreezeColumns = {1};
    private static final int[] advancedFreezeRows = {1};
    private static final int[] advancedItemsCount = {TEMPLATE_ITEM_NAMES.length * 2};
    private static final float[] advancedOuterSize = {0.0f, 0.0f};
    private static boolean advancedOuterSizeSet = false;
    private static final float[] advancedRowMinHeight = {0.0f};
    private static final float[] advancedInnerWidthWithScroll = {0.0f};
    private static final IkBoolean advancedOuterSizeEnabled = new IkBoolean(true);
    private static final IkBoolean advancedShowHeaders = new IkBoolean(true);
    private static final IkBoolean advancedShowWrappedText = new IkBoolean(false);
    private static final IkBoolean advancedShowDebugDetails = new IkBoolean(false);
    private static final List<SortItem> advancedItems = new ArrayList<>();
    private static final List<Integer> advancedSelection = new ArrayList<>();
    private static boolean advancedItemsNeedSort = false;

    private static void showAdvancedOptions() {
        final IkInt flags = advancedFlags;
        // Make the UI compact because there are so many fields
        pushStyleCompact();
        IkGui.pushItemWidth(textBaseWidth * 28.0f);

        if (IkGui.treeNodeEx("Features:", TreeNodeFlags.DEFAULT_OPEN)) {
            IkGui.checkboxFlags("TableFlags.RESIZABLE", flags, TableFlags.RESIZABLE);
            IkGui.checkboxFlags("TableFlags.REORDERABLE", flags, TableFlags.REORDERABLE);
            IkGui.checkboxFlags("TableFlags.HIDEABLE", flags, TableFlags.HIDEABLE);
            IkGui.checkboxFlags("TableFlags.SORTABLE", flags, TableFlags.SORTABLE);
            IkGui.checkboxFlags(
                    "TableFlags.NO_SAVED_SETTINGS", flags, TableFlags.NO_SAVED_SETTINGS);
            IkGui.checkboxFlags(
                    "TableFlags.CONTEXT_MENU_IN_BODY", flags, TableFlags.CONTEXT_MENU_IN_BODY);
            IkGui.treePop();
        }

        if (IkGui.treeNodeEx("Decorations:", TreeNodeFlags.DEFAULT_OPEN)) {
            IkGui.checkboxFlags("TableFlags.ROW_BACKGROUND", flags, TableFlags.ROW_BACKGROUND);
            IkGui.checkboxFlags("TableFlags.BORDERS_V", flags, TableFlags.BORDERS_V);
            IkGui.checkboxFlags("TableFlags.BORDERS_OUTER_V", flags, TableFlags.BORDERS_OUTER_V);
            IkGui.checkboxFlags("TableFlags.BORDERS_INNER_V", flags, TableFlags.BORDERS_INNER_V);
            IkGui.checkboxFlags("TableFlags.BORDERS_H", flags, TableFlags.BORDERS_H);
            IkGui.checkboxFlags("TableFlags.BORDERS_OUTER_H", flags, TableFlags.BORDERS_OUTER_H);
            IkGui.checkboxFlags("TableFlags.BORDERS_INNER_H", flags, TableFlags.BORDERS_INNER_H);
            IkGui.checkboxFlags(
                    "TableFlags.NO_BORDERS_IN_BODY", flags, TableFlags.NO_BORDERS_IN_BODY);
            IkGui.sameLine();
            helpMarker(
                    "Disable vertical borders in columns Body (borders will always appear in"
                            + " Headers)");
            IkGui.checkboxFlags(
                    "TableFlags.NO_BORDERS_IN_BODY_UNTIL_RESIZE",
                    flags,
                    TableFlags.NO_BORDERS_IN_BODY_UNTIL_RESIZE);
            IkGui.sameLine();
            helpMarker(
                    "Disable vertical borders in columns Body until hovered for resize (borders"
                            + " will always appear in Headers)");
            IkGui.treePop();
        }

        if (IkGui.treeNodeEx("Sizing:", TreeNodeFlags.DEFAULT_OPEN)) {
            editTableSizingFlags(flags);
            IkGui.sameLine();
            helpMarker(
                    "In the Advanced demo we override the policy of each column so those"
                            + " table-wide settings have less effect that typical.");
            IkGui.checkboxFlags("TableFlags.NO_HOST_EXTEND_X", flags, TableFlags.NO_HOST_EXTEND_X);
            IkGui.sameLine();
            helpMarker(
                    "Make outer width auto-fit to columns, overriding outer_size.x value.\n\n"
                            + "Only available when SCROLL_X/SCROLL_Y are disabled and Stretch"
                            + " columns are not used.");
            IkGui.checkboxFlags("TableFlags.NO_HOST_EXTEND_Y", flags, TableFlags.NO_HOST_EXTEND_Y);
            IkGui.sameLine();
            helpMarker(
                    "Make outer height stop exactly at outer_size.y (prevent auto-extending table"
                            + " past the limit).\n\n"
                            + "Only available when SCROLL_X/SCROLL_Y are disabled. Data below the"
                            + " limit will be clipped and not visible.");
            IkGui.checkboxFlags(
                    "TableFlags.NO_KEEP_COLUMNS_VISIBLE",
                    flags,
                    TableFlags.NO_KEEP_COLUMNS_VISIBLE);
            IkGui.sameLine();
            helpMarker("Only available if SCROLL_X is disabled.");
            IkGui.checkboxFlags("TableFlags.PRECISE_WIDTHS", flags, TableFlags.PRECISE_WIDTHS);
            IkGui.sameLine();
            helpMarker(
                    "Disable distributing remainder width to stretched columns (width allocation"
                            + " on a 100-wide table with 3 columns: Without this flag: 33,33,34."
                            + " With this flag: 33,33,33). With larger number of columns,"
                            + " resizing will appear to be less smooth.");
            IkGui.checkboxFlags("TableFlags.NO_CLIP", flags, TableFlags.NO_CLIP);
            IkGui.sameLine();
            helpMarker(
                    "Disable clipping rectangle for every individual columns (reduce draw command"
                            + " count, items will be able to overflow into other columns)."
                            + " Generally incompatible with scroll freeze options.");
            IkGui.treePop();
        }

        if (IkGui.treeNodeEx("Padding:", TreeNodeFlags.DEFAULT_OPEN)) {
            IkGui.checkboxFlags("TableFlags.PAD_OUTER_X", flags, TableFlags.PAD_OUTER_X);
            IkGui.checkboxFlags("TableFlags.NO_PAD_OUTER_X", flags, TableFlags.NO_PAD_OUTER_X);
            IkGui.checkboxFlags("TableFlags.NO_PAD_INNER_X", flags, TableFlags.NO_PAD_INNER_X);
            IkGui.treePop();
        }

        if (IkGui.treeNodeEx("Scrolling:", TreeNodeFlags.DEFAULT_OPEN)) {
            IkGui.checkboxFlags("TableFlags.SCROLL_X", flags, TableFlags.SCROLL_X);
            IkGui.sameLine();
            IkGui.setNextItemWidth(IkGui.getFrameHeight());
            IkGui.dragInt(
                    "freeze_cols", advancedFreezeColumns, 0.2f, 0, 9, null, SliderFlags.NO_INPUT);
            IkGui.checkboxFlags("TableFlags.SCROLL_Y", flags, TableFlags.SCROLL_Y);
            IkGui.sameLine();
            IkGui.setNextItemWidth(IkGui.getFrameHeight());
            IkGui.dragInt(
                    "freeze_rows", advancedFreezeRows, 0.2f, 0, 9, null, SliderFlags.NO_INPUT);
            IkGui.treePop();
        }

        if (IkGui.treeNodeEx("Sorting:", TreeNodeFlags.DEFAULT_OPEN)) {
            IkGui.checkboxFlags("TableFlags.SORT_MULTI", flags, TableFlags.SORT_MULTI);
            IkGui.sameLine();
            helpMarker(
                    "When sorting is enabled: hold shift when clicking headers to sort on multiple"
                            + " column. tableGetSortSpecs() may return specs where (specsCount >"
                            + " 1).");
            IkGui.checkboxFlags("TableFlags.SORT_TRISTATE", flags, TableFlags.SORT_TRISTATE);
            IkGui.sameLine();
            helpMarker(
                    "When sorting is enabled: allow no sorting, disable default sorting."
                            + " tableGetSortSpecs() may return specs where (specsCount == 0).");
            IkGui.treePop();
        }

        if (IkGui.treeNodeEx("Headers:", TreeNodeFlags.DEFAULT_OPEN)) {
            IkGui.checkbox("show_headers", advancedShowHeaders);
            IkGui.checkboxFlags(
                    "TableFlags.HIGHLIGHT_HOVERED_COLUMN",
                    flags,
                    TableFlags.HIGHLIGHT_HOVERED_COLUMN);
            IkGui.checkboxFlags(
                    "TableColumnFlags.ANGLED_HEADER",
                    advancedColumnsBaseFlags,
                    TableColumnFlags.ANGLED_HEADER);
            IkGui.sameLine();
            helpMarker(
                    "Enable ANGLED_HEADER on all columns. Best enabled on selected narrow columns"
                            + " (see \"Angled headers\" section of the demo).");
            IkGui.treePop();
        }

        if (IkGui.treeNodeEx("Other:", TreeNodeFlags.DEFAULT_OPEN)) {
            IkGui.checkbox("show_wrapped_text", advancedShowWrappedText);

            IkGui.dragFloat2("##OuterSize", advancedOuterSize);
            IkGui.sameLine(0.0f, IkGui.getStyle().variable.itemInnerSpacing.x);
            IkGui.checkbox("outer_size", advancedOuterSizeEnabled);
            IkGui.sameLine();
            helpMarker(
                    "If scrolling is disabled (SCROLL_X and SCROLL_Y not set):\n"
                            + "- The table is output directly in the parent window.\n"
                            + "- outer_size.x < 0.0f will right-align the table.\n"
                            + "- outer_size.x = 0.0f will narrow fit the table unless there are"
                            + " any Stretch columns.\n"
                            + "- outer_size.y then becomes the minimum size for the table, which"
                            + " will extend vertically if there are more rows (unless"
                            + " NO_HOST_EXTEND_Y is set).");

            // From a user point of view we will tend to use 'inner_width' differently depending on
            // whether our table is embedding scrolling. To facilitate toying with this demo we
            // will actually pass 0.0f to beginTable() when SCROLL_X is disabled.
            IkGui.dragFloat(
                    "inner_width (when ScrollX active)",
                    advancedInnerWidthWithScroll,
                    1.0f,
                    0.0f,
                    Float.MAX_VALUE);

            IkGui.dragFloat("row_min_height", advancedRowMinHeight, 1.0f, 0.0f, Float.MAX_VALUE);
            IkGui.sameLine();
            helpMarker("Specify height of the Selectable item.");

            IkGui.dragInt("items_count", advancedItemsCount, 0.1f, 0, 9999);
            IkGui.combo("items_type (first column)", advancedContentsType, ADVANCED_CONTENTS_TYPES);
            IkGui.treePop();
        }

        IkGui.popItemWidth();
        popStyleCompact();
        IkGui.spacing();
    }

    private static void showAdvanced() {
        if (!section("Advanced")) {
            return;
        }
        // In this example we'll expose most table flags and settings. For specific flags and
        // settings refer to the corresponding section for more detailed explanation. This section
        // is mostly useful to experiment with combining certain flags or settings with each
        // other.
        if (!advancedOuterSizeSet) {
            advancedOuterSize[1] = textBaseHeight * 12;
            advancedOuterSizeSet = true;
        }
        if (IkGui.treeNode("Options")) {
            showAdvancedOptions();
            IkGui.treePop();
        }

        // Update the item list if we changed the number of items
        final int itemsCount = advancedItemsCount[0];
        if (advancedItems.size() != itemsCount) {
            advancedItems.clear();
            for (int n = 0; n < itemsCount; n++) {
                final int templateIndex = n % TEMPLATE_ITEM_NAMES.length;
                advancedItems.add(
                        new SortItem(
                                n,
                                TEMPLATE_ITEM_NAMES[templateIndex],
                                templateIndex == 3 ? 10 : templateIndex == 4 ? 20 : 0));
            }
        }

        final DrawList parentDrawList = IkGui.getWindowDrawList();
        final int parentCommandCount = parentDrawList.getCommandCount();
        float scrollX = 0;
        float scrollY = 0;
        float scrollMaxX = 0;
        float scrollMaxY = 0;
        DrawList tableDrawList = null;

        // Submit the table
        final int flags = advancedFlags.get();
        final int columnsBaseFlags = advancedColumnsBaseFlags.get();
        final float innerWidth =
                (flags & TableFlags.SCROLL_X) != 0 ? advancedInnerWidthWithScroll[0] : 0.0f;
        final boolean outerSizeEnabled = advancedOuterSizeEnabled.get();
        if (IkGui.beginTable(
                "table_advanced",
                6,
                flags,
                outerSizeEnabled ? advancedOuterSize[0] : 0,
                outerSizeEnabled ? advancedOuterSize[1] : 0,
                innerWidth)) {
            // Declare columns. We use the "userID" parameter of tableSetupColumn() to specify a
            // user ID that will be stored in the sort specifications, so our sort function can
            // identify a column given our own identifier. We could also identify them based on
            // their index!
            IkGui.tableSetupColumn(
                    "ID",
                    columnsBaseFlags
                            | TableColumnFlags.DEFAULT_SORT
                            | TableColumnFlags.WIDTH_FIXED
                            | TableColumnFlags.NO_HIDE,
                    0.0f,
                    ITEM_COLUMN_ID);
            IkGui.tableSetupColumn(
                    "Name",
                    columnsBaseFlags | TableColumnFlags.WIDTH_FIXED,
                    0.0f,
                    ITEM_COLUMN_NAME);
            IkGui.tableSetupColumn(
                    "Action",
                    columnsBaseFlags | TableColumnFlags.NO_SORT | TableColumnFlags.WIDTH_FIXED,
                    0.0f,
                    ITEM_COLUMN_ACTION);
            IkGui.tableSetupColumn(
                    "Quantity",
                    columnsBaseFlags | TableColumnFlags.PREFER_SORT_DESCENDING,
                    0.0f,
                    ITEM_COLUMN_QUANTITY);
            IkGui.tableSetupColumn(
                    "Description",
                    columnsBaseFlags
                            | ((flags & TableFlags.NO_HOST_EXTEND_X) != 0
                                    ? TableColumnFlags.NONE
                                    : TableColumnFlags.WIDTH_STRETCH),
                    0.0f,
                    ITEM_COLUMN_DESCRIPTION);
            IkGui.tableSetupColumn(
                    "Hidden",
                    columnsBaseFlags | TableColumnFlags.DEFAULT_HIDE | TableColumnFlags.NO_SORT);
            IkGui.tableSetupScrollFreeze(advancedFreezeColumns[0], advancedFreezeRows[0]);

            // Sort our data if the sort specs have been changed!
            final TableSortSpecs sortSpecs = IkGui.tableGetSortSpecs();
            if (sortSpecs != null && sortSpecs.specsDirty) {
                advancedItemsNeedSort = true;
            }
            if (sortSpecs != null && advancedItemsNeedSort && advancedItems.size() > 1) {
                sortWithSortSpecs(sortSpecs, advancedItems);
                sortSpecs.specsDirty = false;
            }
            advancedItemsNeedSort = false;

            // Take note of whether we are currently sorting based on the quantity, we will use
            // this to trigger sorting when we know the data of this column has been modified
            final boolean sortsUsingQuantity =
                    (IkGui.tableGetColumnFlags(3) & TableColumnFlags.IS_SORTED) != 0;

            // Show headers
            if (advancedShowHeaders.get()
                    && (columnsBaseFlags & TableColumnFlags.ANGLED_HEADER) != 0) {
                IkGui.tableAngledHeadersRow();
            }
            if (advancedShowHeaders.get()) {
                IkGui.tableHeadersRow();
            }

            // Show data, demonstrating using the clipper for large vertical lists
            final float rowMinHeight = advancedRowMinHeight[0];
            final int contentsType = advancedContentsType.get();
            final ListClipper clipper = new ListClipper();
            clipper.begin(advancedItems.size());
            while (clipper.step()) {
                for (int row = clipper.displayStart; row < clipper.displayEnd; row++) {
                    final SortItem item = advancedItems.get(row);
                    final boolean isSelected = advancedSelection.contains(item.id);
                    IkGui.pushID(item.id);
                    IkGui.tableNextRow(TableRowFlags.NONE, rowMinHeight);

                    // For the demo purpose we can select among different types of items submitted
                    // in the first column
                    IkGui.tableSetColumnIndex(0);
                    final String label = String.format("%04d", item.id);
                    switch (contentsType) {
                        case 0 -> IkGui.textUnformatted(label);
                        case 1 -> IkGui.button(label);
                        case 2 -> IkGui.smallButton(label);
                        case 3 -> IkGui.button(label, -Float.MIN_VALUE, 0.0f);
                        default -> {
                            final int selectableFlags =
                                    contentsType == 5
                                            ? SelectableFlags.SPAN_ALL_COLUMNS
                                                    | SelectableFlags.ALLOW_OVERLAP
                                            : SelectableFlags.NONE;
                            if (IkGui.selectable(
                                    label, isSelected, selectableFlags, 0, rowMinHeight)) {
                                if (IkGui.getIO().keyCtrl) {
                                    if (isSelected) {
                                        advancedSelection.remove(Integer.valueOf(item.id));
                                    } else {
                                        advancedSelection.add(item.id);
                                    }
                                } else {
                                    advancedSelection.clear();
                                    advancedSelection.add(item.id);
                                }
                            }
                        }
                    }

                    if (IkGui.tableSetColumnIndex(1)) {
                        IkGui.textUnformatted(item.name);
                    }

                    // Here we demonstrate marking our data set as needing to be sorted again if we
                    // modified a quantity, and we are currently sorting on the column showing the
                    // quantity. To avoid triggering a sort while holding the button, we only
                    // trigger it when the button has been released.
                    if (IkGui.tableSetColumnIndex(2)) {
                        if (IkGui.smallButton("Chop")) {
                            item.quantity += 1;
                        }
                        if (sortsUsingQuantity && IkGui.isItemDeactivated()) {
                            advancedItemsNeedSort = true;
                        }
                        IkGui.sameLine();
                        if (IkGui.smallButton("Eat")) {
                            item.quantity -= 1;
                        }
                        if (sortsUsingQuantity && IkGui.isItemDeactivated()) {
                            advancedItemsNeedSort = true;
                        }
                    }

                    if (IkGui.tableSetColumnIndex(3)) {
                        IkGui.text(String.valueOf(item.quantity));
                    }

                    IkGui.tableSetColumnIndex(4);
                    if (advancedShowWrappedText.get()) {
                        IkGui.textWrapped("Lorem ipsum dolor sit amet");
                    } else {
                        IkGui.text("Lorem ipsum dolor sit amet");
                    }

                    if (IkGui.tableSetColumnIndex(5)) {
                        IkGui.text("1234");
                    }
                    IkGui.popID();
                }
            }

            // Store some info to display debug details below
            scrollX = IkGui.getScrollX();
            scrollY = IkGui.getScrollY();
            scrollMaxX = IkGui.getScrollMaxX();
            scrollMaxY = IkGui.getScrollMaxY();
            tableDrawList = IkGui.getWindowDrawList();
            IkGui.endTable();
        }
        IkGui.checkbox("Debug details", advancedShowDebugDetails);
        if (advancedShowDebugDetails.get() && tableDrawList != null) {
            IkGui.sameLine(0.0f, 0.0f);
            final int tableCommandCount = tableDrawList.getCommandCount();
            if (tableDrawList == parentDrawList) {
                IkGui.text(
                        String.format(
                                ": DrawCmd: +%d (in same window)",
                                tableCommandCount - parentCommandCount));
            } else {
                IkGui.text(
                        String.format(
                                ": DrawCmd: +%d (in child window), Scroll: (%.0f/%.0f)"
                                        + " (%.0f/%.0f)",
                                tableCommandCount - 1, scrollX, scrollMaxX, scrollY, scrollMaxY));
            }
        }
        IkGui.treePop();
    }
}
