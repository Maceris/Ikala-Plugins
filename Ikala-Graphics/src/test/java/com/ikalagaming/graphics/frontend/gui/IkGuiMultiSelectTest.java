package com.ikalagaming.graphics.frontend.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.IkBoolean;
import com.ikalagaming.graphics.frontend.gui.data.ListClipper;
import com.ikalagaming.graphics.frontend.gui.data.MultiSelectIO;
import com.ikalagaming.graphics.frontend.gui.data.SelectionBasicStorage;
import com.ikalagaming.graphics.frontend.gui.data.SelectionExternalStorage;
import com.ikalagaming.graphics.frontend.gui.data.SelectionRequest;
import com.ikalagaming.graphics.frontend.gui.data.SelectionUserData;
import com.ikalagaming.graphics.frontend.gui.data.TypingSelectRequest;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.Key;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.enums.SelectionRequestType;
import com.ikalagaming.graphics.frontend.gui.flags.ConfigFlags;
import com.ikalagaming.graphics.frontend.gui.flags.MultiSelectFlags;
import com.ikalagaming.graphics.frontend.gui.flags.TreeNodeFlags;
import com.ikalagaming.graphics.frontend.gui.flags.TypingSelectFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

/** Tests for multi-select, box-select, the selection storage helpers and typing-select. */
class IkGuiMultiSelectTest {
    private static final int ITEM_COUNT = 10;

    private Context context;

    /** The selection used by the list UI. */
    private SelectionBasicStorage selection;

    /** The rectangles of the items, captured while submitting them. */
    private final Vector2f[] itemMin = new Vector2f[ITEM_COUNT];

    private final Vector2f[] itemMax = new Vector2f[ITEM_COUNT];

    /** Which items reported isItemToggledSelection() in the last frame. */
    private final boolean[] toggled = new boolean[ITEM_COUNT];

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        // Don't read or write an .ini file
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        context.io.mouseInsideWindow = true;
        context.io.configInputTrickleEventQueue = false;
        context.io.configFlags |= ConfigFlags.NAV_ENABLE_KEYBOARD;
        selection = new SelectionBasicStorage();
        for (int i = 0; i < ITEM_COUNT; ++i) {
            itemMin[i] = new Vector2f();
            itemMax[i] = new Vector2f();
        }
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

    private void press(Key key, Runnable ui, Key... modifiers) {
        for (Key modifier : modifiers) {
            context.io.addKeyEvent(modifier, true);
        }
        context.io.addKeyEvent(key, true);
        frame(ui);
        context.io.addKeyEvent(key, false);
        for (Key modifier : modifiers) {
            context.io.addKeyEvent(modifier, false);
        }
        frame(ui);
    }

    private void click(float x, float y, Runnable ui, Key... modifiers) {
        for (Key modifier : modifiers) {
            context.io.addKeyEvent(modifier, true);
        }
        context.io.addMousePosEvent(x, y);
        frame(ui);
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame(ui);
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frame(ui);
        for (Key modifier : modifiers) {
            context.io.addKeyEvent(modifier, false);
        }
        frame(ui);
    }

    private void clickItem(int index, Runnable ui, Key... modifiers) {
        click(
                (itemMin[index].x + itemMax[index].x) * 0.5f,
                (itemMin[index].y + itemMax[index].y) * 0.5f,
                ui,
                modifiers);
    }

    private void beginHost() {
        IkGui.setNextWindowPos(100, 100, Condition.FIRST_USE_EVER);
        IkGui.setNextWindowSize(300, 400, Condition.FIRST_USE_EVER);
        IkGui.begin("List", null, WindowFlags.NONE);
    }

    /** A window with a list of selectables in a multi-select scope. */
    private Runnable list(int flags) {
        return () -> {
            beginHost();
            MultiSelectIO io = IkGui.beginMultiSelect(flags, selection.getSize(), ITEM_COUNT);
            selection.applyRequests(io);
            for (int i = 0; i < ITEM_COUNT; ++i) {
                IkGui.setNextItemSelectionUserData(i);
                IkGui.selectable("Item " + i, selection.contains(i));
                IkGui.getItemRectMin(itemMin[i]);
                IkGui.getItemRectMax(itemMax[i]);
                toggled[i] = IkGui.isItemToggledSelection();
            }
            io = IkGui.endMultiSelect();
            selection.applyRequests(io);
            IkGui.end();
        };
    }

    private int[] selected() {
        return selection.getSelectedItems();
    }

    @Test
    void testClickSelectsOnlyThatItem() {
        final Runnable ui = list(MultiSelectFlags.NONE);
        frames(2, ui);
        clickItem(2, ui);
        assertArrayEquals(new int[] {2}, selected());
        clickItem(5, ui);
        assertArrayEquals(new int[] {5}, selected());
        assertEquals(1, selection.getSize());
    }

    @Test
    void testCtrlClickTogglesItems() {
        final Runnable ui = list(MultiSelectFlags.NONE);
        frames(2, ui);
        clickItem(2, ui);
        clickItem(5, ui, Key.LEFT_CTRL);
        assertArrayEquals(new int[] {2, 5}, selected());
        clickItem(2, ui, Key.LEFT_CTRL);
        assertArrayEquals(new int[] {5}, selected());
    }

    @Test
    void testShiftClickSelectsRange() {
        final Runnable ui = list(MultiSelectFlags.NONE);
        frames(2, ui);
        clickItem(2, ui);
        clickItem(6, ui, Key.LEFT_SHIFT);
        assertArrayEquals(new int[] {2, 3, 4, 5, 6}, selected());

        // Shift+Click backwards from the same source
        clickItem(0, ui, Key.LEFT_SHIFT);
        assertArrayEquals(new int[] {0, 1, 2}, selected());
    }

    @Test
    void testSingleSelectIgnoresModifiers() {
        final Runnable ui = list(MultiSelectFlags.SINGLE_SELECT);
        frames(2, ui);
        clickItem(2, ui);
        clickItem(6, ui, Key.LEFT_SHIFT);
        assertArrayEquals(new int[] {6}, selected());
        clickItem(3, ui, Key.LEFT_CTRL);
        assertArrayEquals(new int[] {3}, selected());
    }

    @Test
    void testSelectAllAndClearOnEscape() {
        final Runnable ui = list(MultiSelectFlags.CLEAR_ON_ESCAPE);
        frames(2, ui);
        clickItem(2, ui);
        press(Key.A, ui, Key.LEFT_CTRL);
        assertEquals(ITEM_COUNT, selection.getSize());
        press(Key.ESCAPE, ui);
        assertEquals(0, selection.getSize());
    }

    @Test
    void testNoSelectAllFlag() {
        final Runnable ui = list(MultiSelectFlags.NO_SELECT_ALL);
        frames(2, ui);
        clickItem(2, ui);
        press(Key.A, ui, Key.LEFT_CTRL);
        assertArrayEquals(new int[] {2}, selected());
    }

    @Test
    void testKeyboardNavigationSelects() {
        final Runnable ui = list(MultiSelectFlags.NONE);
        frames(2, ui);
        clickItem(2, ui);
        press(Key.ARROW_DOWN, ui);
        assertArrayEquals(new int[] {3}, selected());

        // Shift+Arrow extends the selection from the range source
        press(Key.ARROW_DOWN, ui, Key.LEFT_SHIFT);
        press(Key.ARROW_DOWN, ui, Key.LEFT_SHIFT);
        assertArrayEquals(new int[] {3, 4, 5}, selected());

        // Ctrl+Arrow moves without changing the selection
        press(Key.ARROW_DOWN, ui, Key.LEFT_CTRL);
        assertArrayEquals(new int[] {3, 4, 5}, selected());
        // Ctrl+Space toggles the focused item
        press(Key.SPACE, ui, Key.LEFT_CTRL);
        assertArrayEquals(new int[] {3, 4, 5, 6}, selected());
    }

    @Test
    void testItemToggledSelectionIsReported() {
        final boolean[] seen = new boolean[ITEM_COUNT];
        final Runnable list = list(MultiSelectFlags.NONE);
        final Runnable ui =
                () -> {
                    list.run();
                    for (int i = 0; i < ITEM_COUNT; ++i) {
                        seen[i] |= toggled[i];
                    }
                };
        frames(2, ui);
        clickItem(4, ui);
        assertTrue(seen[4]);
        assertFalse(seen[3]);
    }

    @Test
    void testBoxSelectFromVoid() {
        final Runnable ui =
                list(MultiSelectFlags.BOX_SELECT_1D | MultiSelectFlags.CLEAR_ON_CLICK_VOID);
        frames(2, ui);
        final float x = (itemMin[0].x + itemMax[0].x) * 0.5f;
        final float startY = itemMax[ITEM_COUNT - 1].y + 40.0f;

        // Press in empty space below the items, then drag up over the last three
        context.io.addMousePosEvent(x, startY);
        frame(ui);
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame(ui);
        final float endY = (itemMin[7].y + itemMax[7].y) * 0.5f;
        for (int step = 1; step <= 4; ++step) {
            context.io.addMousePosEvent(x, startY + (endY - startY) * step / 4.0f);
            frame(ui);
        }
        assertTrue(context.boxSelectState.isActive);
        frame(ui);
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frames(2, ui);
        assertFalse(context.boxSelectState.isActive);
        assertArrayEquals(new int[] {7, 8, 9}, selected());

        // Clicking the void clears the selection
        click(x, startY, ui);
        assertEquals(0, selection.getSize());
    }

    @Test
    void testCheckboxesUseRangeSelect() {
        final boolean[] checked = new boolean[ITEM_COUNT];
        final SelectionExternalStorage storage =
                new SelectionExternalStorage((index, value) -> checked[index] = value);
        final Runnable ui =
                () -> {
                    beginHost();
                    final int flags =
                            MultiSelectFlags.NO_AUTO_SELECT
                                    | MultiSelectFlags.NO_AUTO_CLEAR
                                    | MultiSelectFlags.CLEAR_ON_ESCAPE;
                    MultiSelectIO io = IkGui.beginMultiSelect(flags, -1, ITEM_COUNT);
                    storage.applyRequests(io);
                    for (int i = 0; i < ITEM_COUNT; ++i) {
                        IkGui.setNextItemSelectionUserData(i);
                        final IkBoolean value = new IkBoolean(checked[i]);
                        IkGui.checkbox("Check " + i, value);
                        IkGui.getItemRectMin(itemMin[i]);
                        IkGui.getItemRectMax(itemMax[i]);
                    }
                    io = IkGui.endMultiSelect();
                    storage.applyRequests(io);
                    IkGui.end();
                };
        frames(2, ui);
        clickItem(1, ui);
        assertTrue(checked[1]);
        clickItem(3, ui);
        // NO_AUTO_CLEAR keeps the other checkbox checked
        assertTrue(checked[1] && checked[3]);
        clickItem(6, ui, Key.LEFT_SHIFT);
        for (int i = 0; i < ITEM_COUNT; ++i) {
            assertEquals(i == 1 || (i >= 3 && i <= 6), checked[i], "checkbox " + i);
        }
    }

    @Test
    void testTreeNodesSelectWithoutOpening() {
        final boolean[] open = new boolean[3];
        final float[] arrowX = new float[1];
        final Runnable ui =
                () -> {
                    beginHost();
                    MultiSelectIO io = IkGui.beginMultiSelect(MultiSelectFlags.NONE, -1, 3);
                    selection.applyRequests(io);
                    for (int i = 0; i < 3; ++i) {
                        IkGui.setNextItemSelectionUserData(i);
                        final int flags =
                                selection.contains(i) ? TreeNodeFlags.SELECTED : TreeNodeFlags.NONE;
                        open[i] = IkGui.treeNodeEx("Node " + i, flags);
                        IkGui.getItemRectMin(itemMin[i]);
                        IkGui.getItemRectMax(itemMax[i]);
                        arrowX[0] = itemMin[i].x + context.fontSize * 0.5f + 2.0f;
                        if (open[i]) {
                            IkGui.treePop();
                        }
                    }
                    io = IkGui.endMultiSelect();
                    selection.applyRequests(io);
                    IkGui.end();
                };
        frames(2, ui);

        // Clicking the label selects but doesn't open (OPEN_ON_ARROW is the default)
        clickItem(1, ui);
        assertArrayEquals(new int[] {1}, selected());
        assertFalse(open[1]);

        // Clicking the arrow opens without changing the selection
        click(arrowX[0], (itemMin[0].y + itemMax[0].y) * 0.5f, ui);
        assertTrue(open[0]);
        assertArrayEquals(new int[] {1}, selected());

        // Double-clicking the label opens it
        final float y = (itemMin[2].y + itemMax[2].y) * 0.5f;
        final float x = (itemMin[2].x + itemMax[2].x) * 0.5f;
        context.io.addMousePosEvent(x, y);
        frame(ui);
        for (int i = 0; i < 2; ++i) {
            context.io.addMouseButtonEvent(MouseButton.LEFT, true);
            frame(ui);
            context.io.addMouseButtonEvent(MouseButton.LEFT, false);
            frame(ui);
        }
        frame(ui);
        assertTrue(open[2]);
    }

    @Test
    void testPlainTreeNodeStillOpensOnClick() {
        final boolean[] open = new boolean[1];
        final Runnable ui =
                () -> {
                    beginHost();
                    open[0] = IkGui.treeNode("Node");
                    IkGui.getItemRectMin(itemMin[0]);
                    IkGui.getItemRectMax(itemMax[0]);
                    if (open[0]) {
                        IkGui.treePop();
                    }
                    IkGui.end();
                };
        frames(2, ui);
        clickItem(0, ui);
        assertTrue(open[0]);
        clickItem(0, ui);
        assertFalse(open[0]);
    }

    @Test
    void testTreeNodeStorageID() {
        final boolean[] open = new boolean[1];
        final boolean[] queried = new boolean[1];
        final boolean[] close = new boolean[1];
        final Runnable ui =
                () -> {
                    beginHost();
                    if (close[0]) {
                        IkGuiInternal.treeNodeSetOpen(1234, false);
                        close[0] = false;
                    }
                    IkGui.pushID("Somewhere deep");
                    IkGui.setNextItemStorageID(1234);
                    open[0] = IkGui.treeNode("Node");
                    IkGui.getItemRectMin(itemMin[0]);
                    IkGui.getItemRectMax(itemMax[0]);
                    if (open[0]) {
                        IkGui.treePop();
                    }
                    IkGui.popID();
                    // The open state can be read without knowing the ID stack
                    queried[0] = IkGuiInternal.treeNodeGetOpen(1234);
                    IkGui.end();
                };
        frames(2, ui);
        assertFalse(queried[0]);
        clickItem(0, ui);
        assertTrue(open[0]);
        assertTrue(queried[0]);

        close[0] = true;
        frames(2, ui);
        assertFalse(open[0]);
        assertFalse(queried[0]);
    }

    @Test
    void testClipperWithSelectAll() {
        final int count = 1000;
        final int[] submitted = new int[1];
        final Runnable ui =
                () -> {
                    beginHost();
                    MultiSelectIO io =
                            IkGui.beginMultiSelect(
                                    MultiSelectFlags.BOX_SELECT_1D, selection.getSize(), count);
                    selection.applyRequests(io);
                    final ListClipper clipper = new ListClipper();
                    clipper.begin(count);
                    if (io.rangeSourceItem != SelectionUserData.INVALID) {
                        clipper.includeItemByIndex((int) io.rangeSourceItem);
                    }
                    submitted[0] = 0;
                    while (clipper.step()) {
                        for (int i = clipper.displayStart; i < clipper.displayEnd; ++i) {
                            IkGui.setNextItemSelectionUserData(i);
                            IkGui.selectable("Item " + i, selection.contains(i));
                            if (i < ITEM_COUNT) {
                                IkGui.getItemRectMin(itemMin[i]);
                                IkGui.getItemRectMax(itemMax[i]);
                            }
                            ++submitted[0];
                        }
                    }
                    io = IkGui.endMultiSelect();
                    selection.applyRequests(io);
                    IkGui.end();
                };
        frames(3, ui);
        assertTrue(submitted[0] < count);
        clickItem(1, ui);
        press(Key.A, ui, Key.LEFT_CTRL);
        assertEquals(count, selection.getSize());
    }

    @Test
    void testMissingEndMultiSelectIsRecovered() {
        frame(
                () -> {
                    beginHost();
                    IkGui.beginMultiSelect(MultiSelectFlags.NONE);
                    IkGui.end();
                });
        assertNull(context.currentMultiSelect);
        assertEquals(0, context.multiSelectTempDataStackSize);
        assertTrue(
                context.debugLogBuffer.snapshot().stream()
                        .anyMatch(line -> line.contains("Missing endMultiSelect()")),
                context.debugLogBuffer.getText());
    }

    @Test
    void testNestedScopesRestoreTheOuterScope() {
        frame(
                () -> {
                    beginHost();
                    IkGui.beginMultiSelect(MultiSelectFlags.NONE);
                    final var outer = context.currentMultiSelect;
                    IkGui.pushID("Inner");
                    IkGui.beginMultiSelect(MultiSelectFlags.NONE);
                    IkGui.endMultiSelect();
                    IkGui.popID();
                    assertEquals(outer, context.currentMultiSelect);
                    IkGui.endMultiSelect();
                    assertNull(context.currentMultiSelect);
                    IkGui.end();
                });
        assertEquals(2, context.multiSelectStorage.size());
    }

    @Test
    void testSelectionRequestsAreLogged() {
        context.debugLogFlags |=
                com.ikalagaming.graphics.frontend.gui.flags.DebugLogFlags.EVENT_SELECTION;
        final Runnable ui = list(MultiSelectFlags.NONE);
        frames(2, ui);
        clickItem(2, ui);
        assertTrue(
                context.debugLogBuffer.snapshot().stream()
                        .anyMatch(line -> line.contains("Request: SetRange 2..2")),
                context.debugLogBuffer.getText());
    }

    // ---------------------------------------------------------------------------------------------
    // Selection storage helpers
    // ---------------------------------------------------------------------------------------------

    private static MultiSelectIO io(int itemsCount, SelectionRequest... requests) {
        final MultiSelectIO io = new MultiSelectIO();
        io.itemsCount = itemsCount;
        io.requests.addAll(Arrays.asList(requests));
        return io;
    }

    private static SelectionRequest setAll(boolean selected) {
        return new SelectionRequest(
                SelectionRequestType.SET_ALL,
                selected,
                0,
                SelectionUserData.INVALID,
                SelectionUserData.INVALID);
    }

    private static SelectionRequest setRange(
            boolean selected, int direction, long first, long last) {
        return new SelectionRequest(
                SelectionRequestType.SET_RANGE, selected, direction, first, last);
    }

    @Test
    void testBasicStorageAppliesRequests() {
        final SelectionBasicStorage storage = new SelectionBasicStorage();
        storage.applyRequests(io(5, setAll(true)));
        assertEquals(5, storage.getSize());
        storage.applyRequests(io(5, setRange(false, 1, 1, 3)));
        assertArrayEquals(new int[] {0, 4}, storage.getSelectedItems());
        storage.applyRequests(io(5, setAll(false), setRange(true, 1, 2, 2)));
        assertArrayEquals(new int[] {2}, storage.getSelectedItems());
    }

    @Test
    void testBasicStoragePreservesOrder() {
        final SelectionBasicStorage storage = new SelectionBasicStorage();
        storage.preserveOrder = true;
        storage.setItemSelected(7, true);
        storage.setItemSelected(2, true);
        // A backwards range is ordered from its source
        storage.applyRequests(io(10, setRange(true, -1, 4, 5)));
        assertArrayEquals(new int[] {7, 2, 5, 4}, storage.getSelectedItems());

        // Without preserving the order, items come in ID order
        storage.preserveOrder = false;
        assertArrayEquals(new int[] {2, 4, 5, 7}, storage.getSelectedItems());
        int sum = 0;
        for (int id : storage) {
            sum += id;
        }
        assertEquals(18, sum);
    }

    @Test
    void testBasicStorageAdapterAndSwap() {
        final List<Integer> ids = List.of(100, 200, 300);
        final SelectionBasicStorage storage = new SelectionBasicStorage();
        storage.adapterIndexToStorageID = ids::get;
        storage.applyRequests(io(3, setRange(true, 1, 0, 1)));
        assertTrue(storage.contains(100) && storage.contains(200) && !storage.contains(300));

        final SelectionBasicStorage other = new SelectionBasicStorage();
        other.setItemSelected(5, true);
        storage.swap(other);
        assertArrayEquals(new int[] {5}, storage.getSelectedItems());
        assertArrayEquals(new int[] {100, 200}, other.getSelectedItems());
    }

    @Test
    void testExternalStorageAppliesRequests() {
        final boolean[] values = new boolean[4];
        final SelectionExternalStorage storage =
                new SelectionExternalStorage((index, value) -> values[index] = value);
        storage.applyRequests(io(4, setAll(true), setRange(false, 1, 1, 2)));
        assertArrayEquals(new boolean[] {true, false, false, true}, values);
    }

    // ---------------------------------------------------------------------------------------------
    // Typing-select
    // ---------------------------------------------------------------------------------------------

    private static final String[] NAMES = {"Apple", "Banana", "Blueberry", "Cherry", "Bean"};

    /** Submit a frame that reads a typing-select request, returning the matched index. */
    private int typeFrame(String typed, int flags, int navIndex) {
        context.io.addInputCharacters(typed);
        final int[] result = {-2};
        frame(
                () -> {
                    beginHost();
                    final TypingSelectRequest request = IkGuiInternal.getTypingSelectRequest(flags);
                    result[0] =
                            IkGuiInternal.typingSelectFindMatch(
                                    request, NAMES.length, index -> NAMES[index], navIndex);
                    IkGui.end();
                });
        return result[0];
    }

    @Test
    void testTypingSelectFindsBestLeadingMatch() {
        frame(() -> {});
        assertEquals(1, typeFrame("b", TypingSelectFlags.NONE, -1));
        assertEquals(2, typeFrame("l", TypingSelectFlags.NONE, -1));
        // No new input means no new request
        assertEquals(-1, typeFrame("", TypingSelectFlags.NONE, -1));
        assertEquals("bl", context.typingSelectState.searchBuffer.toString());
        // Matching ignores case
        context.typingSelectState.clear();
        assertEquals(3, typeFrame("CH", TypingSelectFlags.NONE, -1));
    }

    @Test
    void testTypingSelectSingleCharModeCyclesMatches() {
        frame(() -> {});
        final int flags = TypingSelectFlags.ALLOW_SINGLE_CHAR_MODE;
        assertEquals(1, typeFrame("b", flags, -1));
        assertEquals(2, typeFrame("b", flags, 1));
        assertEquals(4, typeFrame("b", flags, 2));
        // Wraps around to the first match
        assertEquals(1, typeFrame("b", flags, 4));
        // After four repeats we lock into single char mode, so the buffer doesn't grow
        assertTrue(context.typingSelectState.singleCharModeLock);
        assertEquals(2, typeFrame("b", flags, 1));
        assertEquals("bbbb", context.typingSelectState.searchBuffer.toString());
    }

    @Test
    void testTypingSelectBufferTimesOut() {
        frame(() -> {});
        typeFrame("ch", TypingSelectFlags.NONE, -1);
        assertEquals("ch", context.typingSelectState.searchBuffer.toString());
        context.frameStartTime -= 2000;
        assertEquals(1, typeFrame("b", TypingSelectFlags.NONE, -1));
        assertEquals("b", context.typingSelectState.searchBuffer.toString());
    }

    @Test
    void testTypingSelectBackspace() {
        frame(() -> {});
        typeFrame("blu", TypingSelectFlags.ALLOW_BACKSPACE, -1);
        context.io.addKeyEvent(Key.BACKSPACE, true);
        typeFrame("", TypingSelectFlags.ALLOW_BACKSPACE, -1);
        context.io.addKeyEvent(Key.BACKSPACE, false);
        frame(() -> {});
        assertEquals("bl", context.typingSelectState.searchBuffer.toString());

        // Without the flag, backspace clears the buffer
        context.io.addKeyEvent(Key.BACKSPACE, true);
        typeFrame("", TypingSelectFlags.NONE, -1);
        context.io.addKeyEvent(Key.BACKSPACE, false);
        assertEquals("", context.typingSelectState.searchBuffer.toString());
    }

    @Test
    void testTypingSelectIgnoresLeadingBlanks() {
        frame(() -> {});
        assertEquals(1, typeFrame("  ba", TypingSelectFlags.NONE, -1));
        assertNotNull(context.typingSelectState.request.searchBuffer);
        assertEquals("ba", context.typingSelectState.request.searchBuffer);
    }
}
