package com.ikalagaming.graphics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.Storage;
import com.ikalagaming.graphics.gui.data.TableColumnSortSpecs;
import com.ikalagaming.graphics.gui.data.TableSortSpecs;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.enums.SortDirection;
import com.ikalagaming.graphics.gui.flags.WindowFlags;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Tests for the tables section of the demo. */
class IkGuiDemoTablesTest {
    private Context context;

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        // Don't read or write an .ini file
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 2000);
    }

    @AfterEach
    void tearDown() {
        IkGui.destroyContext();
    }

    private static TableColumnSortSpecs spec(int userID, SortDirection direction) {
        final TableColumnSortSpecs spec = new TableColumnSortSpecs();
        spec.columnUserID = userID;
        spec.sortDirection = direction;
        return spec;
    }

    private static TableSortSpecs specs(TableColumnSortSpecs... columns) {
        final TableSortSpecs specs = new TableSortSpecs();
        specs.specs = columns;
        specs.specsCount = columns.length;
        return specs;
    }

    private static List<IkGuiDemoTables.SortItem> items() {
        final List<IkGuiDemoTables.SortItem> items = new ArrayList<>();
        items.add(new IkGuiDemoTables.SortItem(0, "Banana", 5));
        items.add(new IkGuiDemoTables.SortItem(1, "Apple", 5));
        items.add(new IkGuiDemoTables.SortItem(2, "Cherry", 1));
        items.add(new IkGuiDemoTables.SortItem(3, "Apple", 9));
        return items;
    }

    private static List<Integer> ids(List<IkGuiDemoTables.SortItem> items) {
        return items.stream().map(item -> item.id).toList();
    }

    @Test
    void testSortSingleColumn() {
        final List<IkGuiDemoTables.SortItem> items = items();
        IkGuiDemoTables.sortWithSortSpecs(
                specs(spec(IkGuiDemoTables.ITEM_COLUMN_ID, SortDirection.DESCENDING)), items);
        assertEquals(List.of(3, 2, 1, 0), ids(items));
    }

    @Test
    void testSortMultipleColumns() {
        final List<IkGuiDemoTables.SortItem> items = items();
        // Quantity descending, then name ascending
        IkGuiDemoTables.sortWithSortSpecs(
                specs(
                        spec(IkGuiDemoTables.ITEM_COLUMN_QUANTITY, SortDirection.DESCENDING),
                        spec(IkGuiDemoTables.ITEM_COLUMN_NAME, SortDirection.ASCENDING)),
                items);
        assertEquals(List.of(3, 1, 0, 2), ids(items));
    }

    @Test
    void testSortFallsBackToID() {
        final List<IkGuiDemoTables.SortItem> items = items();
        IkGuiDemoTables.sortWithSortSpecs(
                specs(spec(IkGuiDemoTables.ITEM_COLUMN_NAME, SortDirection.ASCENDING)), items);
        // The two apples keep their ID order
        assertEquals(List.of(1, 3, 0, 2), ids(items));
        // No specs at all still gives a stable order
        IkGuiDemoTables.sortWithSortSpecs(specs(), items);
        assertEquals(List.of(0, 1, 2, 3), ids(items));
    }

    @Test
    void testEverySectionRenders() {
        // Storage that reports every tree node and collapsing header as open
        final Storage openEverything =
                new Storage() {
                    @Override
                    public int getInt(int key, int defaultValue) {
                        return super.getInt(key, 1);
                    }
                };
        final Runnable ui =
                () -> {
                    IkGui.setNextWindowPos(0, 0, Condition.FIRST_USE_EVER);
                    IkGui.setNextWindowSize(1000, 1900, Condition.FIRST_USE_EVER);
                    IkGui.begin("Tables", null, WindowFlags.NONE);
                    IkGui.setStateStorage(openEverything);
                    IkGuiDemoTables.show();
                    IkGui.end();
                };
        for (int i = 0; i < 3; i++) {
            IkGui.newFrame();
            ui.run();
            IkGui.render();
        }
        assertTrue(context.windowStack.isEmpty());
        assertFalse(
                context.debugLogBuffer.snapshot().stream()
                        .anyMatch(line -> line.contains("[ikgui-error]")),
                context.debugLogBuffer.getText());
    }
}
