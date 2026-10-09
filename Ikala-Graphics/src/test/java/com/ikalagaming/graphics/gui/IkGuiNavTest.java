package com.ikalagaming.graphics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.IkString;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.enums.GuiInputSource;
import com.ikalagaming.graphics.gui.enums.Key;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.flags.ConfigFlags;
import com.ikalagaming.graphics.gui.flags.KeyModFlags;
import com.ikalagaming.graphics.gui.flags.WindowFlags;
import com.ikalagaming.graphics.gui.util.KeyChord;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for keyboard navigation, key ownership, shortcuts, and capture flags. Headless. */
class IkGuiNavTest {

    private Context context;

    /** The IDs of the items submitted by the UI, in order. */
    private final int[] itemIDs = new int[8];

    /** The number of times each button was pressed. */
    private final int[] pressCounts = new int[8];

    private final Vector2f firstItemMin = new Vector2f();
    private final Vector2f firstItemMax = new Vector2f();

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        // Don't read or write an .ini file
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        context.io.mouseInsideWindow = true;
        context.io.configInputTrickleEventQueue = false;
        context.io.configFlags |= ConfigFlags.NAV_ENABLE_KEYBOARD;
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

    private void click(float x, float y, Runnable ui) {
        context.io.addMousePosEvent(x, y);
        frame(ui);
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame(ui);
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frame(ui);
    }

    private void beginHost(String name, float x) {
        IkGui.setNextWindowPos(x, 100, Condition.FIRST_USE_EVER);
        IkGui.setNextWindowSize(300, 300, Condition.FIRST_USE_EVER);
        IkGui.begin(name, null, WindowFlags.NONE);
    }

    /** A window with a column of buttons. */
    private Runnable buttons(int count) {
        return () -> {
            beginHost("Host", 100);
            for (int i = 0; i < count; ++i) {
                if (IkGui.button("Button " + i)) {
                    pressCounts[i]++;
                }
                itemIDs[i] = context.lastItemData.id;
                if (i == 0) {
                    IkGui.getItemRectMin(firstItemMin);
                    IkGui.getItemRectMax(firstItemMax);
                }
            }
            IkGui.end();
        };
    }

    @Test
    void testNewWindowInitializesNavigation() {
        final Runnable ui = buttons(3);
        frames(3, ui);
        assertEquals(context.navFocusedWindow.name, "Host");
        // The first item is selected by the init request, without showing the cursor
        assertEquals(itemIDs[0], context.navID);
        assertFalse(context.navCursorVisible);
    }

    @Test
    void testArrowKeysMoveBetweenItems() {
        final Runnable ui = buttons(3);
        frames(3, ui);

        press(Key.ARROW_DOWN, ui);
        assertEquals(itemIDs[1], context.navID);
        assertTrue(context.navCursorVisible);
        press(Key.ARROW_DOWN, ui);
        assertEquals(itemIDs[2], context.navID);
        // No wrapping in regular windows
        press(Key.ARROW_DOWN, ui);
        assertEquals(itemIDs[2], context.navID);
        press(Key.ARROW_UP, ui);
        assertEquals(itemIDs[1], context.navID);
        assertTrue(context.io.navActive);
        assertTrue(context.io.navVisible);
    }

    @Test
    void testSpaceAndEnterActivate() {
        final Runnable ui = buttons(3);
        frames(3, ui);
        press(Key.ARROW_DOWN, ui);
        press(Key.SPACE, ui);
        assertEquals(1, pressCounts[1]);
        assertEquals(0, pressCounts[0]);
        press(Key.ENTER, ui);
        assertEquals(2, pressCounts[1]);
    }

    @Test
    void testClickingFocusesAndHidesCursor() {
        final Runnable ui = buttons(3);
        frames(3, ui);
        press(Key.ARROW_DOWN, ui);
        press(Key.ARROW_DOWN, ui);
        assertEquals(itemIDs[2], context.navID);

        click(firstItemMin.x + 5, (firstItemMin.y + firstItemMax.y) / 2, ui);
        assertEquals(1, pressCounts[0]);
        assertEquals(itemIDs[0], context.navID);
        assertFalse(context.navCursorVisible);
    }

    @Test
    void testEscapeClearsNavItem() {
        final Runnable ui = buttons(2);
        frames(3, ui);
        press(Key.ARROW_DOWN, ui);
        assertNotEquals(0, context.navID);
        press(Key.ESCAPE, ui);
        assertEquals(0, context.navID);
    }

    @Test
    void testNavigationDisabledWithoutConfigFlag() {
        context.io.configFlags &= ~ConfigFlags.NAV_ENABLE_KEYBOARD;
        final Runnable ui = buttons(3);
        frames(3, ui);
        press(Key.ARROW_DOWN, ui);
        assertEquals(itemIDs[0], context.navID);
        assertFalse(context.io.navActive);
        press(Key.SPACE, ui);
        assertEquals(0, pressCounts[0]);
    }

    @Test
    void testTabActivatesInputs() {
        final IkString first = new IkString(32);
        final IkString second = new IkString(32);
        final Runnable ui =
                () -> {
                    beginHost("Host", 100);
                    IkGui.inputText("first", first);
                    itemIDs[0] = context.lastItemData.id;
                    IkGui.inputText("second", second);
                    itemIDs[1] = context.lastItemData.id;
                    IkGui.end();
                };
        frames(3, ui);
        press(Key.TAB, ui);
        assertEquals(itemIDs[0], context.activeID);
        press(Key.TAB, ui);
        assertEquals(itemIDs[1], context.activeID);
        context.io.addInputCharacters("hi");
        frame(ui);
        assertEquals("hi", second.get());
        // Shift+Tab goes back
        press(Key.TAB, ui, Key.LEFT_SHIFT);
        assertEquals(itemIDs[0], context.activeID);
    }

    @Test
    void testTabWorksWithoutKeyboardNavigation() {
        context.io.configFlags &= ~ConfigFlags.NAV_ENABLE_KEYBOARD;
        final IkString first = new IkString(32);
        final IkString second = new IkString(32);
        final Runnable ui =
                () -> {
                    beginHost("Host", 100);
                    IkGui.button("Skipped");
                    IkGui.inputText("first", first);
                    itemIDs[0] = context.lastItemData.id;
                    IkGui.inputText("second", second);
                    itemIDs[1] = context.lastItemData.id;
                    IkGui.end();
                };
        frames(3, ui);
        // Tabbing without keyboard navigation only stops on inputable items
        press(Key.TAB, ui);
        assertEquals(itemIDs[0], context.activeID);
        press(Key.TAB, ui);
        assertEquals(itemIDs[1], context.activeID);
    }

    @Test
    void testSetKeyboardFocusHere() {
        final IkString text = new IkString(32);
        final boolean[] focus = {false};
        final Runnable ui =
                () -> {
                    beginHost("Host", 100);
                    IkGui.button("Before");
                    if (focus[0]) {
                        IkGui.setKeyboardFocusHere();
                        focus[0] = false;
                    }
                    IkGui.inputText("text", text);
                    itemIDs[0] = context.lastItemData.id;
                    IkGui.end();
                };
        frames(3, ui);
        focus[0] = true;
        frames(2, ui);
        assertEquals(itemIDs[0], context.activeID);
        // Focus requests activate through navigation, which defaults to the keyboard source
        assertEquals(GuiInputSource.KEYBOARD, context.activeIDSource);
        // The capture flags are updated at the start of the next frame
        frame(ui);
        assertTrue(context.io.wantCaptureKeyboard);
        assertTrue(context.io.wantTextInput);
    }

    @Test
    void testSetItemDefaultFocus() {
        final Runnable ui =
                () -> {
                    beginHost("Host", 100);
                    IkGui.button("A");
                    IkGui.button("B");
                    itemIDs[1] = context.lastItemData.id;
                    IkGui.setItemDefaultFocus();
                    IkGui.button("C");
                    IkGui.end();
                };
        frames(3, ui);
        assertEquals(itemIDs[1], context.navID);
    }

    @Test
    void testWantCaptureKeyboardOverride() {
        // Active keyboard navigation captures the keyboard by default
        context.io.configNavCaptureKeyboard = false;
        final Runnable ui = buttons(1);
        frames(2, ui);
        assertFalse(context.io.wantCaptureKeyboard);
        IkGui.newFrame();
        IkGui.setNextFrameWantCaptureKeyboard(true);
        IkGui.render();
        frame(ui);
        assertTrue(context.io.wantCaptureKeyboard);
        // The override only lasts one frame
        frame(ui);
        assertFalse(context.io.wantCaptureKeyboard);
    }

    @Test
    void testShortcutRoutesToFocusedWindow() {
        final int[] counts = new int[2];
        final int chord = KeyChord.of(KeyModFlags.CTRL, Key.S);
        final Runnable ui =
                () -> {
                    beginHost("Left", 0);
                    if (IkGui.shortcut(chord)) {
                        counts[0]++;
                    }
                    IkGui.end();
                    beginHost("Right", 400);
                    if (IkGui.shortcut(chord)) {
                        counts[1]++;
                    }
                    IkGui.end();
                };
        frames(3, ui);
        // The last window to appear is focused
        assertEquals("Right", context.navFocusedWindow.name);
        press(Key.S, ui, Key.LEFT_CTRL);
        assertEquals(0, counts[0]);
        assertEquals(1, counts[1]);

        // Without the modifier, the shortcut doesn't trigger
        press(Key.S, ui);
        assertEquals(1, counts[1]);
    }

    @Test
    void testKeyOwnership() {
        final Runnable ui = buttons(1);
        frames(2, ui);
        context.io.addKeyEvent(Key.A, true);
        IkGui.newFrame();
        final int owner = 1234;
        IkGuiImplKeys.setKeyOwner(Key.A, owner, 0);
        assertTrue(IkGuiImplKeys.isKeyPressed(Key.A, 0, owner));
        assertFalse(IkGuiImplKeys.isKeyPressed(Key.A, 0, 5678));
        // Unlocked keys can still be read by code that doesn't use ownership
        assertTrue(IkGui.isKeyPressed(Key.A));
        IkGui.render();
    }

    @Test
    void testKeyChordNames() {
        assertEquals(
                "Ctrl+Shift+S",
                KeyChord.getName(KeyChord.of(KeyModFlags.CTRL | KeyModFlags.SHIFT, Key.S)));
        assertEquals("PageDown", KeyChord.getName(KeyChord.of(Key.PAGE_DOWN)));
        assertEquals("Ctrl", KeyChord.getName(KeyChord.ofMods(KeyModFlags.CTRL)));
    }

    @Test
    void testSpaceTweaksSliderWithArrows() {
        final int[] value = {5};
        final Runnable ui =
                () -> {
                    beginHost("Host", 100);
                    IkGui.sliderInt("slider", value, 0, 10);
                    itemIDs[0] = context.lastItemData.id;
                    IkGui.end();
                };
        frames(3, ui);
        assertEquals(itemIDs[0], context.navID);
        // The init request doesn't show the cursor, a directional key does
        assertFalse(context.navCursorVisible);
        press(Key.ARROW_DOWN, ui);
        assertTrue(context.navCursorVisible);

        // Space activates for tweaking, and arrows change the value
        press(Key.SPACE, ui);
        assertEquals(itemIDs[0], context.activeID);
        press(Key.ARROW_RIGHT, ui);
        assertEquals(6, value[0]);
        press(Key.ARROW_LEFT, ui);
        press(Key.ARROW_LEFT, ui);
        assertEquals(4, value[0]);
        // Space again deactivates
        press(Key.SPACE, ui);
        assertEquals(0, context.activeID);
    }

    @Test
    void testEnterEditsSliderAsText() {
        final int[] value = {5};
        final Runnable ui =
                () -> {
                    beginHost("Host", 100);
                    IkGui.sliderInt("slider", value, 0, 10);
                    itemIDs[0] = context.lastItemData.id;
                    IkGui.end();
                };
        frames(3, ui);
        press(Key.ARROW_DOWN, ui);
        press(Key.ENTER, ui);
        frame(ui);
        assertTrue(IkGuiImplInputText.tempInputIsActive(itemIDs[0]));
        context.io.addInputCharacters("8");
        frame(ui);
        press(Key.ENTER, ui);
        assertEquals(8, value[0]);
    }

    @Test
    void testTreeNodeOpensAndClosesWithArrows() {
        final boolean[] open = {false};
        final Runnable ui =
                () -> {
                    beginHost("Host", 100);
                    open[0] = IkGui.treeNode("Tree");
                    itemIDs[0] = context.lastItemData.id;
                    if (open[0]) {
                        IkGui.button("Child");
                        itemIDs[1] = context.lastItemData.id;
                        IkGui.treePop();
                    }
                    IkGui.end();
                };
        frames(3, ui);
        assertEquals(itemIDs[0], context.navID);
        press(Key.ARROW_RIGHT, ui);
        assertTrue(open[0]);
        press(Key.ARROW_DOWN, ui);
        assertEquals(itemIDs[1], context.navID);
        press(Key.ARROW_UP, ui);
        press(Key.ARROW_LEFT, ui);
        assertFalse(open[0]);
        // Enter toggles too
        press(Key.ENTER, ui);
        assertTrue(open[0]);
    }

    @Test
    void testLeftJumpsToParentTreeNode() {
        final boolean[] open = {true};
        final Runnable ui =
                () -> {
                    beginHost("Host", 100);
                    IkGui.setNextItemOpen(true, Condition.ONCE);
                    open[0] =
                            IkGui.treeNodeEx(
                                    "Tree",
                                    com.ikalagaming.graphics.gui.flags.TreeNodeFlags
                                            .NAV_LEFT_JUMPS_TO_PARENT);
                    itemIDs[0] = context.lastItemData.id;
                    if (open[0]) {
                        IkGui.button("Child");
                        itemIDs[1] = context.lastItemData.id;
                        IkGui.treePop();
                    }
                    IkGui.end();
                };
        frames(3, ui);
        press(Key.ARROW_DOWN, ui);
        assertEquals(itemIDs[1], context.navID);
        press(Key.ARROW_LEFT, ui);
        assertEquals(itemIDs[0], context.navID);
        assertTrue(open[0]);
    }

    @Test
    void testAltTogglesMenuLayerAndMenusOpen() {
        final int[] clicked = {0};
        final Runnable ui =
                () -> {
                    IkGui.setNextWindowPos(100, 100, Condition.FIRST_USE_EVER);
                    IkGui.setNextWindowSize(300, 300, Condition.FIRST_USE_EVER);
                    IkGui.begin("Host", null, WindowFlags.MENU_BAR);
                    if (IkGui.beginMenuBar()) {
                        if (IkGui.beginMenu("File")) {
                            if (IkGui.menuItem("Open")) {
                                clicked[0]++;
                            }
                            IkGui.endMenu();
                        }
                        itemIDs[1] = IkGui.getID("File");
                        IkGui.endMenuBar();
                    }
                    IkGui.button("Body");
                    itemIDs[0] = context.lastItemData.id;
                    IkGui.end();
                };
        frames(3, ui);
        assertEquals(itemIDs[0], context.navID);

        // Pressing and releasing Alt moves to the menu layer
        press(Key.LEFT_ALT, ui);
        assertEquals(IkGuiImplNav.NAV_LAYER_MENU, context.navLayer);
        frame(ui);
        // Down opens the menu, the first item gets focus, Space activates it
        press(Key.ARROW_DOWN, ui);
        frames(2, ui);
        assertFalse(context.openPopupStack.isEmpty());
        press(Key.SPACE, ui);
        assertEquals(1, clicked[0]);
        frames(2, ui);
        assertTrue(context.openPopupStack.isEmpty());
    }

    @Test
    void testComboOpensWithNavigationAndFocusesSelectedItem() {
        final com.ikalagaming.graphics.gui.data.IkInt item =
                new com.ikalagaming.graphics.gui.data.IkInt(2);
        final String[] items = {"A", "B", "C", "D"};
        final Runnable ui =
                () -> {
                    beginHost("Host", 100);
                    IkGui.combo("combo", item, items);
                    IkGui.end();
                };
        frames(3, ui);
        press(Key.ARROW_DOWN, ui);
        press(Key.ENTER, ui);
        frames(2, ui);
        assertFalse(context.openPopupStack.isEmpty());
        // The selected item is focused by default
        press(Key.ARROW_DOWN, ui);
        press(Key.ENTER, ui);
        frames(2, ui);
        assertEquals(3, item.get());
        assertTrue(context.openPopupStack.isEmpty());
    }

    @Test
    void testComboWithGetter() {
        final com.ikalagaming.graphics.gui.data.IkInt item =
                new com.ikalagaming.graphics.gui.data.IkInt(2);
        final java.util.Set<Integer> fetched = new java.util.HashSet<>();
        final Runnable ui =
                () -> {
                    beginHost("Host", 100);
                    IkGui.combo(
                            "combo",
                            item,
                            i -> {
                                fetched.add(i);
                                return "Item " + i;
                            },
                            1000);
                    IkGui.end();
                };
        frames(3, ui);
        // Only the preview is fetched while the combo is closed
        assertEquals(java.util.Set.of(2), fetched);

        press(Key.ARROW_DOWN, ui);
        press(Key.ENTER, ui);
        frames(2, ui);
        assertFalse(context.openPopupStack.isEmpty());
        // The popup only fetches the visible items
        assertTrue(fetched.size() < 100, "Fetched " + fetched.size() + " items");
        press(Key.ARROW_DOWN, ui);
        press(Key.ENTER, ui);
        frames(2, ui);
        assertEquals(3, item.get());
        assertTrue(context.openPopupStack.isEmpty());
    }

    @Test
    void testEscapeClosesPopup() {
        final Runnable ui =
                () -> {
                    beginHost("Host", 100);
                    if (IkGui.button("Open")) {
                        IkGui.openPopup("popup");
                    }
                    if (IkGui.beginPopup("popup")) {
                        IkGui.button("Inside");
                        IkGui.endPopup();
                    }
                    IkGui.end();
                };
        frames(3, ui);
        press(Key.ARROW_DOWN, ui);
        press(Key.SPACE, ui);
        frames(2, ui);
        assertFalse(context.openPopupStack.isEmpty());
        press(Key.ESCAPE, ui);
        frames(2, ui);
        assertTrue(context.openPopupStack.isEmpty());
    }

    @Test
    void testNavigatingIntoChildWindow() {
        final Runnable ui =
                () -> {
                    beginHost("Host", 100);
                    IkGui.button("Before");
                    itemIDs[0] = context.lastItemData.id;
                    IkGui.beginChild("child", 200, 100);
                    IkGui.button("In child");
                    itemIDs[1] = context.lastItemData.id;
                    IkGui.endChild();
                    itemIDs[2] = context.lastItemData.id;
                    IkGui.end();
                };
        frames(3, ui);
        press(Key.ARROW_DOWN, ui);
        // The child window itself is the navigation target
        assertEquals(itemIDs[2], context.navID);
        // Enter navigates into the child
        press(Key.ENTER, ui);
        frames(2, ui);
        assertEquals(itemIDs[1], context.navID);
        assertTrue(context.navFocusedWindow.name.contains("child"));
        // Escape goes back to the parent
        press(Key.ESCAPE, ui);
        assertEquals(itemIDs[2], context.navID);
    }

    @Test
    void testCtrlTabSwitchesWindows() {
        final Runnable ui =
                () -> {
                    beginHost("Left", 0);
                    IkGui.button("A");
                    IkGui.end();
                    beginHost("Right", 400);
                    IkGui.button("B");
                    IkGui.end();
                };
        frames(3, ui);
        assertEquals("Right", context.navFocusedWindow.name);
        press(Key.TAB, ui, Key.LEFT_CTRL);
        frames(2, ui);
        assertEquals("Left", context.navFocusedWindow.name);
    }
}
