package com.ikalagaming.graphics.frontend.gui;

import static com.ikalagaming.graphics.frontend.gui.IkGuiTestContext.chord;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.frontend.gui.data.IkBoolean;
import com.ikalagaming.graphics.frontend.gui.data.IkInt;
import com.ikalagaming.graphics.frontend.gui.data.IkString;
import com.ikalagaming.graphics.frontend.gui.data.ListClipper;
import com.ikalagaming.graphics.frontend.gui.data.Window;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.GuiInputSource;
import com.ikalagaming.graphics.frontend.gui.enums.Key;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.enums.StyleVariable;
import com.ikalagaming.graphics.frontend.gui.flags.ChildFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ItemFlags;
import com.ikalagaming.graphics.frontend.gui.flags.KeyModFlags;
import com.ikalagaming.graphics.frontend.gui.flags.NavMoveFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;
import com.ikalagaming.graphics.frontend.gui.util.KeyChord;
import com.ikalagaming.graphics.frontend.gui.util.RectFloat;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.IntConsumer;

/**
 * Navigation tests ported from the Dear ImGui test suite (imgui_tests_nav.cpp), which is MIT
 * licensed. Each test notes the name of the upstream test it is ported from.
 *
 * <p>Upstream the test windows sit on top of the test app's own windows, the demo window and a
 * "Hello, world!" window. Tests that use them call {@link #app()} from their UI.
 */
class IkGuiSuiteNavTest {
    /** The name of the demo window, which upstream calls "Dear ImGui Demo". */
    private static final String DEMO = "IkGui Demo Window";

    private IkGuiTestContext ctx;

    private final IkBoolean showDemoWindow = new IkBoolean(true);

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

    /** The windows of the test suite app. */
    private void app() {
        if (showDemoWindow.get()) {
            IkGuiDemo.showDemoWindow(showDemoWindow);
        }
        IkGui.begin("Hello, world!");
        IkGui.text("This is some useful text.");
        IkGui.checkbox("Demo Window", showDemoWindow);
        IkGui.end();
    }

    private int navWindowingKeyNext() {
        return ctx.context.configNavWindowingKeyNext;
    }

    private static int mods(int chord) {
        return KeyChord.ofMods(KeyChord.getMods(chord));
    }

    private static int keyOnly(int chord) {
        return KeyChord.of(KeyChord.getKey(chord));
    }

    /** nav_basic: opening a window from a checkbox with the keyboard focuses the new window. */
    @Test
    void testBasic() {
        ctx.setGui(this::app);
        ctx.setInputMode(GuiInputSource.KEYBOARD);
        ctx.setRef("Hello, world!");
        ctx.itemUncheck("Demo Window");
        ctx.itemCheck("Demo Window");
        assertNotNull(ctx.context.navFocusedWindow);
        assertEquals(ctx.getID("//" + DEMO), ctx.context.navFocusedWindow.id);
    }

    /** nav_scoring_1. */
    @Test
    void testScoring1() {
        ctx.setGui(
                () -> {
                    app();
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.collapsingHeader("Window options")) {
                        // A 3 way layout, where we transition to AAAA and not to the center most
                        if (IkGui.beginTable("table", 3)) {
                            final IkBoolean b = new IkBoolean(false);
                            for (String label :
                                    new String[] {"AAAA", "BBBB", "CCCC", "DDDD", "EEEE", "FFFF"}) {
                                IkGui.tableNextColumn();
                                IkGui.checkbox(label, b);
                            }
                            IkGui.endTable();
                        }
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("Test Window");
        ctx.itemClick("Window options");
        ctx.itemOpen("Window options");
        assertEquals(ctx.getID("Window options"), g.navID);
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(ctx.getID("table/AAAA"), g.navID);
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(ctx.getID("table/DDDD"), g.navID);
        ctx.keyPress(Key.ARROW_UP);
        assertEquals(ctx.getID("table/AAAA"), g.navID);
        ctx.keyPress(Key.ARROW_RIGHT);
        assertEquals(ctx.getID("table/BBBB"), g.navID);
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(ctx.getID("table/EEEE"), g.navID);
        ctx.keyPress(Key.ARROW_RIGHT);
        assertEquals(ctx.getID("table/FFFF"), g.navID);
        ctx.keyPress(Key.ARROW_RIGHT);
        assertEquals(ctx.getID("table/FFFF"), g.navID);
        ctx.keyPress(Key.ARROW_UP);
        assertEquals(ctx.getID("table/CCCC"), g.navID);

        // The preferred scoring position is recorded
        ctx.keyPress(Key.ARROW_UP);
        assertEquals(ctx.getID("Window options"), g.navID);
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(ctx.getID("table/CCCC"), g.navID);
        ctx.keyPress(Key.ARROW_LEFT);
        assertEquals(ctx.getID("table/BBBB"), g.navID);
        ctx.keyPress(Key.ARROW_UP);
        assertEquals(ctx.getID("Window options"), g.navID);
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(ctx.getID("table/BBBB"), g.navID);
        ctx.keyPress(Key.ARROW_UP);
        assertEquals(ctx.getID("Window options"), g.navID);

        // A failing move resets the preferred position on that axis
        ctx.keyPress(Key.ARROW_RIGHT);
        assertEquals(ctx.getID("Window options"), g.navID);
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(ctx.getID("table/AAAA"), g.navID);

        // The preferred position is saved and restored on focus changes
        ctx.keyPress(Key.ARROW_RIGHT);
        ctx.keyPress(Key.ARROW_UP);
        // Focus the other window
        ctx.windowFocus("//" + DEMO);
        ctx.itemOpen("//" + DEMO + "/Help");
        ctx.keyPress(Key.ARROW_DOWN);
        ctx.itemClose("//" + DEMO + "/Help");
        // Ctrl+Tab
        ctx.keyPress(navWindowingKeyNext());
        assertEquals(ctx.getID("Window options"), g.navID);
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(ctx.getID("table/BBBB"), g.navID);
    }

    /** nav_esc_popup: escape deactivates a text input without closing the popup. */
    @Test
    void testEscPopup() {
        final IkString str = new IkString(256);
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.button("Open Popup")) {
                        IkGui.openPopup("Popup");
                    }
                    if (IkGui.button("Open Modal")) {
                        IkGui.openPopup("Modal");
                    }
                    if (IkGui.beginPopup("Popup", WindowFlags.NO_SAVED_SETTINGS)
                            || IkGui.beginPopupModal("Modal", WindowFlags.NO_SAVED_SETTINGS)) {
                        IkGui.inputText("Field", str);
                        IkGui.endPopup();
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        for (int variant = 0; variant < 2; ++variant) {
            ctx.setRef("Test Window");
            ctx.navMoveTo(variant == 0 ? "Open Popup" : "Open Modal");
            ctx.navActivate();
            final Window popup = g.navFocusedWindow;
            if (variant == 0) {
                assertTrue((popup.flags & WindowFlags.INTERNAL_POPUP) != 0);
            } else {
                assertTrue((popup.flags & WindowFlags.INTERNAL_MODAL) != 0);
            }

            // Activate "Field"
            ctx.navInput();
            assertTrue(popup.active);
            assertEquals(ctx.getID("Field", popup.id), g.activeID);

            ctx.keyPress(Key.ESCAPE);
            assertTrue(popup.active);
            assertEquals(0, g.activeID);
            assertTrue(g.io.navVisible);

            ctx.keyPress(Key.ESCAPE);
            if (variant == 0) {
                // Ordinary popups are closed immediately
                assertFalse(popup.active);
            } else {
                // Modals don't close, instead they deactivate navigation
                assertTrue(popup.active);
                assertFalse(g.io.navVisible);
            }
        }
    }

    /**
     * nav_menu_alt_key: Alt toggles the menu layer, toggling the layer steals the active ID, typing
     * cancels the toggle, and escape closes a menu. In regular, child and popup windows.
     */
    @Test
    void testMenuAltKey() {
        final IkString str = new IkString(256);
        final int[] step = {0};
        final int[] inputID = {0};
        final IkBoolean open = new IkBoolean(true);
        final Runnable menuContent =
                () -> {
                    if (IkGui.beginMenuBar()) {
                        if (IkGui.beginMenu("File")) {
                            IkGui.text("Blah");
                            IkGui.endMenu();
                        }
                        if (IkGui.beginMenu("Edit")) {
                            IkGui.text("Blah");
                            IkGui.endMenu();
                        }
                        IkGui.endMenuBar();
                    }
                };
        final Runnable windowContent =
                () -> {
                    IkGui.inputText("Input", str);
                    inputID[0] = IkGui.getItemID();
                };
        ctx.setGui(
                () -> {
                    switch (step[0]) {
                        case 0 -> {
                            IkGui.begin(
                                    "Test window",
                                    null,
                                    WindowFlags.NO_SAVED_SETTINGS | WindowFlags.MENU_BAR);
                            menuContent.run();
                            windowContent.run();
                            IkGui.end();
                        }
                        case 1 -> {
                            IkGui.begin("Test window", null, WindowFlags.NO_SAVED_SETTINGS);
                            IkGui.beginChild(
                                    "Child", 500, 500, ChildFlags.BORDERS, WindowFlags.MENU_BAR);
                            menuContent.run();
                            windowContent.run();
                            IkGui.endChild();
                            IkGui.end();
                        }
                        case 2 -> {
                            if (!IkGui.isPopupOpen("Test window")) {
                                IkGui.openPopup("Test window");
                            }
                            if (IkGui.beginPopupModal(
                                    "Test window",
                                    WindowFlags.NO_SAVED_SETTINGS | WindowFlags.MENU_BAR)) {
                                menuContent.run();
                                windowContent.run();
                                IkGui.endPopup();
                            }
                        }
                        default -> {
                            // Toggling between layers when the menu layer only has the title bar
                            // buttons
                            IkGui.begin("Test window", open, WindowFlags.NO_SAVED_SETTINGS);
                            windowContent.run();
                            IkGui.end();
                        }
                    }
                });
        final var g = ctx.context;
        final int alt = KeyChord.ofMods(KeyModFlags.ALT);

        for (int s = 0; s < 4; ++s) {
            final String description = "step " + s;
            step[0] = s;
            ctx.popupCloseAll();
            ctx.yieldFrame();

            ctx.navMoveTo(inputID[0]);
            ctx.setRef("//$FOCUSED");

            assertEquals(s == 2 ? 1 : 0, g.openPopupStack.size(), description);
            assertEquals(IkGuiImplNav.NAV_LAYER_MAIN, g.navLayer, description);
            ctx.keyPress(alt);
            assertEquals(IkGuiImplNav.NAV_LAYER_MENU, g.navLayer, description);
            final String menuItem1 = s == 3 ? "#COLLAPSE" : "##MenuBar/File";
            final String menuItem2 = s == 3 ? "#CLOSE" : "##MenuBar/Edit";
            assertEquals(ctx.getID(menuItem1), g.navID, description);
            ctx.keyPress(Key.ARROW_RIGHT);
            assertEquals(ctx.getID(menuItem2), g.navID, description);

            ctx.keyPress(alt);
            assertEquals(IkGuiImplNav.NAV_LAYER_MAIN, g.navLayer, description);
            ctx.keyPress(KeyChord.ofMods(KeyModFlags.CTRL | KeyModFlags.ALT));
            assertEquals(IkGuiImplNav.NAV_LAYER_MAIN, g.navLayer, description);
            // The nav ID is reset for the menu layer
            ctx.keyPress(alt);
            assertEquals(ctx.getID(menuItem1), g.navID, description);
            ctx.keyPress(alt);

            // Toggling the layer steals the active ID
            ctx.navMoveTo(inputID[0]);
            ctx.navInput();
            assertEquals(inputID[0], g.activeID, description);
            ctx.keyPress(alt);
            assertEquals(0, g.activeID, description);
            assertEquals(IkGuiImplNav.NAV_LAYER_MENU, g.navLayer, description);
            // Escape to leave the layer
            ctx.keyPress(Key.ESCAPE);
            assertEquals(IkGuiImplNav.NAV_LAYER_MAIN, g.navLayer, description);
            assertEquals(inputID[0], g.navID, description);

            // Typing characters cancels toggling the layer
            ctx.navMoveTo(inputID[0]);
            ctx.navInput();
            assertEquals(inputID[0], g.activeID, description);
            ctx.keyDown(alt);
            ctx.keyChars("ABC");
            ctx.keyUp(alt);
            assertEquals(IkGuiImplNav.NAV_LAYER_MAIN, g.navLayer, description);
            assertEquals(inputID[0], g.activeID, description);
            ctx.keyPress(Key.ESCAPE);

            // So do other keys
            assertEquals(0, g.activeID, description);
            ctx.keyDown(alt);
            ctx.keyPress(Key.APOSTROPHE);
            ctx.keyUp(alt);
            assertEquals(IkGuiImplNav.NAV_LAYER_MAIN, g.navLayer, description);
            assertEquals(0, g.activeID, description);
        }
    }

    /** nav_menu_alt_key_disabled: Alt doesn't steal the active ID with NO_NAV_INPUTS. */
    @Test
    void testMenuAltKeyDisabled() {
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.MENU_BAR
                                    | WindowFlags.NO_NAV_INPUTS
                                    | WindowFlags.NO_SAVED_SETTINGS
                                    | WindowFlags.ALWAYS_AUTO_RESIZE);
                    if (IkGui.beginMenuBar()) {
                        if (IkGui.beginMenu("File")) {
                            IkGui.endMenu();
                        }
                        IkGui.endMenuBar();
                    }
                    IkGui.inputText("Input", new IkString(16));
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.itemInput("Input");
        assertEquals(ctx.getID("Input"), ctx.context.activeID);
        ctx.keyPress(KeyChord.ofMods(KeyModFlags.ALT));
        ctx.keyPress(Key.LEFT_ALT);
        assertEquals(ctx.getID("Input"), ctx.context.activeID);
    }

    /** nav_home_end_keys. */
    @Test
    void testHomeEndKeys() {
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(100, 150);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    for (int i = 0; i < 10; ++i) {
                        IkGui.button("Button " + i);
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        final Window window = IkGuiInternal.findWindowByName("Test Window");
        ctx.setRef("Test Window");
        ctx.setInputMode(GuiInputSource.KEYBOARD);

        assertEquals(window.getID("Button 0"), g.navID);
        assertEquals(0, window.scrollPosition.y);
        // Navigate to the middle of the window
        ctx.keyPress(Key.ARROW_DOWN, 5);
        assertEquals(window.getID("Button 5"), g.navID);
        assertTrue(window.scrollPosition.y > 0 && window.scrollPosition.y < window.scrollMax.y);
        // From the middle to the end
        ctx.keyPress(Key.END);
        assertEquals(window.getID("Button 9"), g.navID);
        assertEquals(window.scrollMax.y, window.scrollPosition.y);
        // From the end to the start
        ctx.keyPress(Key.HOME);
        assertEquals(window.getID("Button 0"), g.navID);
        assertEquals(0, window.scrollPosition.y);
    }

    /** nav_menu_wraparound: vertical wrap-around in menus. */
    @Test
    void testMenuWraparound() {
        ctx.setGui(this::app);
        final var g = ctx.context;
        ctx.setRef(DEMO);
        ctx.menuClick("Menu");
        assertEquals(ctx.getID("//$FOCUSED/New"), g.navID);
        ctx.keyPress(Key.ARROW_UP);
        assertEquals(ctx.getID("//$FOCUSED/Quit"), g.navID);
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(ctx.getID("//$FOCUSED/New"), g.navID);
    }

    /** nav_menu_close: closing menus with the left arrow key. */
    @Test
    void testMenuClose() {
        final int[] argVariant = {0};
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(600, 600);
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.MENU_BAR);
                    if (IkGui.beginMenuBar()) {
                        if (IkGui.beginMenu("Menu")) {
                            if (IkGui.beginMenu("Submenu1")) {
                                IkGui.menuItem("A");
                                IkGui.menuItem("B");
                                IkGui.menuItem("C");
                                IkGui.menuItem("D");
                                IkGui.endMenu();
                            }
                            if (IkGui.beginMenu("Submenu2")) {
                                final boolean useChild = argVariant[0] == 1;
                                if (!useChild
                                        || IkGui.beginChild(
                                                "Child",
                                                0,
                                                0,
                                                ChildFlags.BORDERS
                                                        | ChildFlags.AUTO_RESIZE_X
                                                        | ChildFlags.AUTO_RESIZE_Y,
                                                WindowFlags.NONE)) {
                                    if (IkGui.beginTabBar("Tabs")) {
                                        if (IkGui.beginTabItem("Tab 1")) {
                                            IkGui.endTabItem();
                                        }
                                        if (IkGui.beginTabItem("Tab 2")) {
                                            IkGui.endTabItem();
                                        }
                                        IkGui.endTabBar();
                                    }
                                }
                                if (useChild) {
                                    IkGui.endChild();
                                }
                                IkGui.endMenu();
                            }
                            IkGui.endMenu();
                        }
                        IkGui.endMenuBar();
                    }
                    IkGui.end();
                });
        final var g = ctx.context;

        for (int variant = 0; variant < 2; ++variant) {
            final String description = "variant " + variant;
            argVariant[0] = variant;
            ctx.setRef("Test Window");
            ctx.menuClick("Menu/Submenu1");
            ctx.keyPress(Key.ARROW_RIGHT);
            assertEquals(ctx.getID("//###Menu_01/A"), g.navID, description);
            assertTrue(g.navCursorVisible, description);
            ctx.keyPress(Key.ARROW_DOWN);
            assertEquals(ctx.getID("//###Menu_01/B"), g.navID, description);
            ctx.keyPress(Key.ARROW_LEFT);
            // Navigation doesn't get us to Submenu2, which is at the same height as B
            assertEquals(ctx.getID("//###Menu_00/Submenu1"), g.navID, description);

            ctx.menuClick("Menu/Submenu2");
            if (variant == 0) {
                ctx.setRef("###Menu_01");
            } else {
                final Window child = ctx.windowInfo("//###Menu_01/Child");
                assertNotNull(child, description);
                ctx.setRef(child);
            }
            ctx.itemClick("Tabs/Tab 1");
            // Activate nav, navigate to the next tab
            ctx.keyPress(Key.ARROW_RIGHT);
            assertEquals(ctx.getID("Tabs/Tab 2"), g.navID, description);
            // Navigate to the first tab, not closing the menu
            ctx.keyPress(Key.ARROW_LEFT);
            assertEquals(ctx.getID("Tabs/Tab 1"), g.navID, description);
            if (variant == 1) {
                // Navigation fails, not closing the menu
                ctx.keyPress(Key.ARROW_LEFT);
                assertEquals(ctx.getID("Tabs/Tab 1"), g.navID, description);
                // Exit the child window navigation
                ctx.keyPress(Key.ESCAPE);
            }
            // Close the 2nd level menu
            ctx.keyPress(Key.ARROW_LEFT);
            assertEquals(
                    IkGuiInternal.findWindowByName("###Menu_00"), g.navFocusedWindow, description);
            // Close the 1st level menu
            ctx.keyPress(Key.ARROW_LEFT);
            assertEquals("Test Window", g.navFocusedWindow.name, description);
        }

        // The current menu behaviors, which are inconsistent upstream too
        ctx.setRef("Test Window");
        ctx.menuClick("Menu/Submenu1");
        assertEquals(2, g.openPopupStack.size());
        // Left closes a menu if the menu item was clicked
        ctx.keyPress(Key.ARROW_LEFT);
        assertEquals(0, g.openPopupStack.size());

        ctx.menuClick("Menu");
        ctx.mouseMove("//$FOCUSED/Submenu1");
        assertEquals(2, g.openPopupStack.size());
        // Right closes a menu if the item was hovered
        ctx.keyPress(Key.ARROW_RIGHT);
        assertEquals(0, g.openPopupStack.size());

        ctx.menuClick("Menu/Submenu1");
        assertEquals(2, g.openPopupStack.size());
        // Right keeps the sub-menu open if the menu item was clicked
        ctx.keyPress(Key.ARROW_RIGHT);
        assertEquals(2, g.openPopupStack.size());
        assertEquals(ctx.getID("//$FOCUSED/A"), g.navID);
        // Down moves to the second item in the sub-menu
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(ctx.getID("//$FOCUSED/B"), g.navID);

        ctx.popupCloseAll();
        ctx.menuClick("Menu");
        ctx.mouseMove("//$FOCUSED/Submenu1");
        assertEquals(2, g.openPopupStack.size());
        // Left keeps the sub-menu open if the menu item was hovered
        ctx.keyPress(Key.ARROW_LEFT);
        assertEquals(2, g.openPopupStack.size());
        assertEquals(ctx.getID("//$FOCUSED/A"), g.navID);
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(ctx.getID("//$FOCUSED/B"), g.navID);
    }

    /** nav_menu_menuset: navigating across a set of menus outside a menu bar. */
    @Test
    void testMenuMenuset() {
        final float[] ff = {0.5f};
        ctx.setGui(
                () -> {
                    if (IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS)) {
                        if (IkGui.beginMenu("Menu1")) {
                            IkGui.menuItem("MenuItem1");
                            IkGui.text("hovered: " + IkGui.isItemHovered());
                            IkGui.endMenu();
                        }
                        if (IkGui.beginMenu("Menu2")) {
                            IkGui.sliderFloat("float1", ff, 0.0f, 1.0f);
                            IkGui.menuItem("MenuItem2");
                            IkGui.text("hovered: " + IkGui.isItemHovered());
                            IkGui.menuItem("MenuItem2b");
                            IkGui.menuItem("MenuItem2c");
                            IkGui.endMenu();
                        }
                        IkGui.text("hovered: " + IkGui.isItemHovered());
                        if (IkGui.beginMenu("Menu3")) {
                            IkGui.menuItem("MenuItem3");
                            IkGui.endMenu();
                        }
                        IkGui.menuItem("MenuItemOutside");
                        IkGui.text("hovered: " + IkGui.isItemHovered());
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("//Test Window");
        // Get out of the way
        ctx.mouseMove("MenuItemOutside");
        ctx.navMoveTo("Menu1");
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(ctx.getID("Menu2"), g.navID);
        ctx.keyPress(Key.ARROW_RIGHT);
        assertEquals(ctx.getID("//$FOCUSED/float1"), g.navID);
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(ctx.getID("//$FOCUSED/MenuItem2"), g.navID);
        ctx.keyPress(Key.ARROW_LEFT);
        assertEquals(ctx.getID("Menu2"), g.navID);
    }

    /** nav_ctrl_tab_focusing. */
    @Test
    void testCtrlTabFocusing() {
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Window 1",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.textUnformatted("Not empty space");
                    IkGui.end();

                    IkGui.begin(
                            "Window 2",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.button("Button Out");
                    IkGui.beginChild("Child", 50, 50, ChildFlags.BORDERS, WindowFlags.NONE);
                    IkGui.button("Button In");
                    IkGui.endChild();
                    IkGui.end();
                });
        final var g = ctx.context;
        final int chordCtrlTab = navWindowingKeyNext();

        for (int testN = 0; testN < 2; ++testN) {
            final String description = "case " + testN;
            ctx.dockClear("Window 1", "Window 2");
            if (testN == 1) {
                ctx.dockInto("Window 1", "Window 2");
            }

            // Set up the window focus order
            ctx.windowFocus("Window 1");
            ctx.windowFocus("Window 2");

            ctx.keyPress(chordCtrlTab);
            assertSame(ctx.getWindowByRef("Window 1"), g.navFocusedWindow, description);

            // A slow Ctrl+Tab, to make sure the UI appears
            ctx.keyDown(mods(chordCtrlTab));
            ctx.keyPress(keyOnly(chordCtrlTab));
            ctx.sleep(0.5f);
            assertSame(ctx.getWindowByRef("Window 2"), g.navWindowingTarget, description);
            assertFalse(g.navWindowingTarget.skipItems, description);
            ctx.keyUp(mods(chordCtrlTab));
            assertSame(ctx.getWindowByRef("Window 2"), g.navFocusedWindow, description);

            // The previous windowing key combination, with the UI
            ctx.keyDown(mods(chordCtrlTab));
            ctx.keyPress(keyOnly(chordCtrlTab));
            ctx.sleep(0.5f);
            assertSame(ctx.getWindowByRef("Window 1"), g.navWindowingTarget, description);
            assertFalse(g.navWindowingTarget.skipItems, description);
            ctx.keyPress(chord(KeyModFlags.SHIFT, KeyChord.getKey(chordCtrlTab)));
            assertSame(ctx.getWindowByRef("Window 2"), g.navWindowingTarget, description);
            assertFalse(g.navWindowingTarget.skipItems, description);
            ctx.keyUp(mods(chordCtrlTab));
            assertSame(ctx.getWindowByRef("Window 2"), g.navFocusedWindow, description);

            // Set up the window focus order, focusing the child window
            ctx.windowFocus("Window 1");
            ctx.windowFocus("Window 2");
            ctx.itemClick(ctx.getID("Button In", ctx.windowInfo("Window 2/Child").id));

            ctx.keyPress(chordCtrlTab);
            assertSame(ctx.getWindowByRef("Window 1"), g.navFocusedWindow, description);

            // Ctrl+Tab from no focused window
            IkGuiInternal.focusWindow(null, 0);
            ctx.keyPress(chordCtrlTab);
            assertSame(ctx.getWindowByRef("Window 1"), g.navFocusedWindow, description);
        }
    }

    /** nav_ctrl_tab_popups: Ctrl+Tab closes open popups. */
    @Test
    void testCtrlTabPopups() {
        ctx.setGui(
                () -> {
                    app();
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.textUnformatted("Not empty space");
                    if (IkGui.button("Popup 1")) {
                        IkGui.openPopup("Popup 1");
                    }
                    if (IkGui.beginPopup("Popup 1")) {
                        IkGui.text("This is Popup 1");
                        IkGui.endPopup();
                    }
                    if (IkGui.button("Popup 2")) {
                        IkGui.openPopup("Popup 2");
                    }
                    if (IkGui.beginPopup("Popup 2", WindowFlags.INTERNAL_CHILD_WINDOW)) {
                        IkGui.text("This is Popup 2");
                        IkGui.endPopup();
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        final int chordCtrlTab = navWindowingKeyNext();

        ctx.dockClear(DEMO, "Test Window");
        ctx.windowFocus(DEMO);
        ctx.windowFocus("Test Window");
        ctx.itemClick("Test Window/Popup 1");
        ctx.keyDown(mods(chordCtrlTab));
        // To the test window
        ctx.keyPress(Key.TAB);
        // To the demo window
        ctx.keyPress(Key.TAB);
        ctx.keyUp(mods(chordCtrlTab));
        ctx.yieldFrames(2);
        assertSame(ctx.getWindowByRef(DEMO), g.navFocusedWindow);
        assertEquals(0, g.openPopupStack.size());

        ctx.keyPress(chordCtrlTab);
        assertSame(ctx.getWindowByRef("Test Window"), g.navFocusedWindow);
        ctx.itemClick("Test Window/Popup 2");
        // To the demo window
        ctx.keyPress(chordCtrlTab);
        ctx.yieldFrames(2);
        assertSame(ctx.getWindowByRef(DEMO), g.navFocusedWindow);
        assertEquals(0, g.openPopupStack.size());
    }

    /** nav_ctrl_tab_nav_id_restore: the nav ID is restored when Ctrl+Tab focuses a window. */
    @Test
    void testCtrlTabNavIdRestore() {
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Window 1",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.button("Button 1");
                    IkGui.end();

                    IkGui.begin(
                            "Window 2",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.beginChild("Child", 50, 50, ChildFlags.BORDERS, WindowFlags.NONE);
                    IkGui.button("Button 2");
                    IkGui.endChild();
                    IkGui.end();
                });
        final var g = ctx.context;
        final int chordCtrlTab = navWindowingKeyNext();

        for (int testN = 0; testN < 2; ++testN) {
            final String description = "case " + testN;
            ctx.dockClear("Window 1", "Window 2");
            if (testN == 1) {
                ctx.dockInto("Window 2", "Window 1");
            }

            final int win1ButtonID = ctx.getID("Window 1/Button 1");
            final int win2ButtonID = ctx.getID("Button 2", ctx.windowInfo("Window 2/Child").id);

            // Focus Window 1, navigate to the button
            ctx.windowFocus("Window 1");
            ctx.navMoveTo(win1ButtonID);

            // Focus Window 2, ensure the nav ID changed, navigate to the button
            ctx.windowFocus("Window 2");
            assertNotEquals(win1ButtonID, g.navID, description);
            ctx.navMoveTo(win2ButtonID);

            // Ctrl+Tab back to the previous window, the nav ID is restored
            ctx.keyPress(chordCtrlTab);
            assertEquals(win1ButtonID, g.navID, description);

            ctx.keyPress(chordCtrlTab);
            assertEquals(win2ButtonID, g.navID, description);
        }
    }

    /** nav_ctrl_tab_auto_menu_layer: Ctrl+Tab picks the only nav layer with items. */
    @Test
    void testCtrlTabAutoMenuLayer() {
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Window 1", null, WindowFlags.NO_SAVED_SETTINGS | WindowFlags.MENU_BAR);
                    if (IkGui.beginMenuBar()) {
                        if (IkGui.beginMenu("File")) {
                            IkGui.endMenu();
                        }
                        if (IkGui.beginMenu("Edit")) {
                            IkGui.endMenu();
                        }
                        IkGui.endMenuBar();
                    }
                    IkGui.text("Hello");
                    IkGui.end();

                    IkGui.begin("Window 2", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.button("Hello");
                    IkGui.end();
                });
        final var g = ctx.context;
        final int chordCtrlTab = navWindowingKeyNext();

        ctx.windowFocus("Window 1");
        ctx.windowFocus("Window 2");
        ctx.keyPress(chordCtrlTab);
        assertEquals("Window 1", g.navFocusedWindow.name);
        assertEquals(IkGuiImplNav.NAV_LAYER_MENU, g.navLayer);
        ctx.keyPress(chordCtrlTab);
        assertEquals("Window 2", g.navFocusedWindow.name);
        assertEquals(IkGuiImplNav.NAV_LAYER_MAIN, g.navLayer);
    }

    /** nav_ctrl_tab_takes_activeid_away. */
    @Test
    void testCtrlTabTakesActiveIdAway() {
        final IkString str1 = new IkString(256);
        final IkString str2 = new IkString(256);
        ctx.setGui(
                () -> {
                    IkGui.begin("Test window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.text("This is test window 1");
                    IkGui.inputText("InputText", str1);
                    IkGui.end();
                    IkGui.begin("Test window 2", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.text("This is test window 2");
                    IkGui.inputText("InputText", str2);
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setInputMode(GuiInputSource.KEYBOARD);
        ctx.setRef("Test window 1");
        ctx.itemInput("InputText");
        ctx.keyCharsAppend("123");
        assertEquals(ctx.getID("InputText"), g.activeID);
        ctx.keyPress(navWindowingKeyNext());
        assertEquals(0, g.activeID);
        ctx.sleep(1.0f);
    }

    /** nav_ctrl_tab_customization: custom windowing key combinations. */
    @Test
    void testCtrlTabCustomization() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.textUnformatted("Oh dear");
                    IkGui.end();

                    IkGui.begin("Window 2", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.textUnformatted("Oh dear");
                    IkGui.end();
                });
        final var g = ctx.context;

        // Configure custom windowing key combinations
        g.configNavWindowingKeyNext = chord(KeyModFlags.ALT, Key.N);
        g.configNavWindowingKeyPrevious = chord(KeyModFlags.ALT, Key.P);

        // Dock the windows together, an edge case where the docking tab bar gets Alt instead of
        // the windowing
        ctx.dockClear("Window 1", "Window 2");
        ctx.dockInto("Window 1", "Window 2");

        // Set up the window focus order
        ctx.windowFocus("Window 1");
        ctx.windowFocus("Window 2");

        // The default windowing keys don't change the focus
        ctx.keyPress(chord(KeyModFlags.CTRL, Key.TAB));
        assertSame(ctx.getWindowByRef("Window 2"), g.navFocusedWindow);
        ctx.keyPress(chord(KeyModFlags.CTRL | KeyModFlags.SHIFT, Key.TAB));
        assertSame(ctx.getWindowByRef("Window 2"), g.navFocusedWindow);

        // The custom windowing key does
        ctx.keyPress(chord(KeyModFlags.ALT, Key.N));
        assertSame(ctx.getWindowByRef("Window 1"), g.navFocusedWindow);

        // A slow windowing key combination, to make sure the UI appears
        ctx.keyDown(KeyChord.ofMods(KeyModFlags.ALT));
        ctx.keyPress(Key.N);
        ctx.sleep(0.5f);
        assertSame(ctx.getWindowByRef("Window 2"), g.navWindowingTarget);
        assertFalse(g.navWindowingTarget.skipItems);
        ctx.keyUp(KeyChord.ofMods(KeyModFlags.ALT));
        assertSame(ctx.getWindowByRef("Window 2"), g.navFocusedWindow);

        // The previous windowing key combination, with the UI
        ctx.keyDown(KeyChord.ofMods(KeyModFlags.ALT));
        ctx.keyPress(Key.N);
        ctx.sleep(0.5f);
        assertSame(ctx.getWindowByRef("Window 1"), g.navWindowingTarget);
        assertFalse(g.navWindowingTarget.skipItems);
        ctx.keyPress(Key.P);
        assertSame(ctx.getWindowByRef("Window 2"), g.navWindowingTarget);
        assertFalse(g.navWindowingTarget.skipItems);
        ctx.keyUp(KeyChord.ofMods(KeyModFlags.ALT));
        assertSame(ctx.getWindowByRef("Window 2"), g.navFocusedWindow);

        // Disable the windowing key combinations
        g.configNavWindowingKeyNext = 0;
        g.configNavWindowingKeyPrevious = 0;

        ctx.windowFocus("Window 1");
        ctx.windowFocus("Window 2");

        ctx.keyPress(chord(KeyModFlags.CTRL, Key.TAB));
        assertSame(ctx.getWindowByRef("Window 2"), g.navFocusedWindow);
        ctx.keyPress(chord(KeyModFlags.CTRL | KeyModFlags.SHIFT, Key.TAB));
        assertSame(ctx.getWindowByRef("Window 2"), g.navFocusedWindow);
    }

    /** nav_activate: remote activateItemByID(). */
    @Test
    void testActivate() {
        final IkString str = new IkString(256);
        final int[] step = {0};
        final int[] count = {0};
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    final int activateTarget =
                            ctx.getID(
                                    step[0] == 0
                                            ? "//Test Window/Button"
                                            : "//Test Window/InputText");
                    if (IkGui.button("Activate Src 0")) {
                        IkGuiImplNav.activateItemByID(activateTarget);
                    }
                    if (IkGui.button("Button")) {
                        count[0]++;
                    }
                    IkGui.sameLine();
                    IkGui.text(String.valueOf(count[0]));
                    IkGui.inputText("InputText", str);
                    if (IkGui.button("Activate Src 1")) {
                        IkGuiImplNav.activateItemByID(activateTarget);
                    }
                    IkGui.end();

                    IkGui.begin("Window 2", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.button("Activate Src 2")) {
                        IkGuiImplNav.activateItemByID(activateTarget);
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        assertEquals(0, count[0]);
        ctx.setRef("Test Window");

        step[0] = 0;
        ctx.yieldFrame();
        ctx.itemClick("Activate Src 0");
        assertEquals(1, count[0]);
        ctx.itemClick("Activate Src 1");
        assertEquals(2, count[0]);
        ctx.itemClick("//Window 2/Activate Src 2");
        assertEquals(3, count[0]);

        step[0] = 1;
        ctx.yieldFrame();
        ctx.itemClick("Activate Src 0");
        assertEquals(ctx.getID("//Test Window/InputText"), g.navID);
        assertEquals(ctx.getID("//Test Window/InputText"), g.activeID);
        // Wait for the route
        ctx.yieldFrame();
        ctx.keyPress(Key.ESCAPE);
        assertEquals(0, g.activeID);
        ctx.itemClick("//Window 2/Activate Src 2");
        assertEquals(ctx.getID("//Test Window/InputText"), g.navID);
        assertEquals(ctx.getID("//Test Window/InputText"), g.activeID);
    }

    /** nav_input. */
    @Test
    void testInput() {
        final IkString str = new IkString(256);
        final int[] count = {0};
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.inputText("InputText", str);
                    if (IkGui.button("Button")) {
                        count[0]++;
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        final int inputID = ctx.getID("//Test Window/InputText");

        ctx.setRef("Test Window");
        ctx.navMoveTo("InputText");

        // Activate to apply and release the active ID
        ctx.navInput();
        assertEquals(inputID, g.activeID);
        ctx.keyCharsReplaceEnter("0");
        assertEquals("0", str.get());
        ctx.navInput();
        ctx.keyCharsReplace("123");
        assertEquals("123", str.get());
        // Validates even with configInputTextEnterKeepActive
        ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ENTER));
        assertEquals(0, g.activeID);
        assertEquals("123", str.get());

        // Cancel to revert and release the active ID
        ctx.navInput();
        ctx.keyCharsReplaceEnter("0");
        assertEquals("0", str.get());
        ctx.navInput();
        ctx.keyCharsReplace("123");
        ctx.keyPress(Key.ESCAPE);
        assertEquals(0, g.activeID);
        assertEquals("0", str.get());

        // Input again to release the active ID and apply
        ctx.navInput();
        ctx.keyCharsReplaceEnter("0");
        assertEquals("0", str.get());
        ctx.navInput();
        ctx.keyCharsReplace("123");
        ctx.keyPress(chord(KeyModFlags.SHIFT, Key.ENTER));
        assertEquals(0, g.activeID);
        assertEquals("123", str.get());

        // Input triggers buttons
        count[0] = 0;
        ctx.navMoveTo("Button");
        ctx.navInput();
        assertTrue(count[0] > 0);
    }

    /**
     * nav_focus_restore_on_missing_window: the nav ID is restored when focusing another window, or
     * when a window stops being submitted.
     */
    @Test
    void testFocusRestoreOnMissingWindow() {
        final boolean[] showWindow2 = {true};
        ctx.setGui(
                () -> {
                    IkGui.begin(
                            "Window 1",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.button("Button 1");
                    IkGui.button("Button 2");
                    IkGui.beginChild("Child", 100, 100, ChildFlags.BORDERS, WindowFlags.NONE);
                    IkGui.button("Button 3");
                    IkGui.button("Button 4");
                    IkGui.endChild();
                    IkGui.button("Button 5");
                    IkGui.end();

                    if (!showWindow2[0]) {
                        return;
                    }
                    IkGui.begin(
                            "Window 2",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    IkGui.button("Button 2");
                    IkGui.end();
                });
        final var g = ctx.context;

        for (int testN = 0; testN < 2; ++testN) {
            final String description = "case " + testN;
            showWindow2[0] = true;
            ctx.yieldFrames(2);
            ctx.dockClear("Window 1", "Window 2");
            if (testN == 1) {
                ctx.dockInto("Window 2", "Window 1");
            }
            ctx.windowFocus("Window 1");
            ctx.navMoveTo("Window 1/Button 1");

            ctx.windowFocus("Window 2");
            ctx.navMoveTo("Window 2/Button 2");

            ctx.windowFocus("Window 1");
            assertEquals(ctx.getID("Window 1/Button 1"), g.navID, description);

            ctx.windowFocus("Window 2");
            assertEquals(ctx.getID("Window 2/Button 2"), g.navID, description);

            showWindow2[0] = false;
            ctx.yieldFrames(2);
            assertEquals(ctx.getID("Window 1/Button 1"), g.navID, description);
        }

        // Entering a child window and leaving it
        ctx.windowFocus("Window 1");
        ctx.navMoveTo("Window 1/Child");
        assertEquals(ctx.getID("Window 1/Child"), g.navID);
        assertEquals(0, g.navFocusedWindow.flags & WindowFlags.INTERNAL_CHILD_WINDOW);

        // Enter the child window
        ctx.navActivate();
        assertTrue((g.navFocusedWindow.flags & WindowFlags.INTERNAL_CHILD_WINDOW) != 0);
        // Manipulate something
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(ctx.getID("//$FOCUSED/Button 4"), g.navID);
        ctx.navActivate();
        // Leave the child window
        ctx.keyPress(Key.ESCAPE);
        // The focus resumes at the last location before entering the child window
        assertEquals(ctx.getID("Window 1/Child"), g.navID);
    }

    /** nav_focus_clear_on_void: clicking on empty space clears the navigation focus. */
    @Test
    void testFocusClearOnVoid() {
        ctx.setGui(this::app);
        final var g = ctx.context;
        ctx.setRef(DEMO);

        for (int mouseButton = 0; mouseButton < 2; ++mouseButton) {
            final String description = "button " + mouseButton;
            ctx.itemOpen("Help");
            ctx.itemClose("Help");
            assertEquals(ctx.getID("Help"), g.navID, description);
            assertSame(ctx.getWindowByRef(""), g.navFocusedWindow, description);
            assertTrue(g.io.wantCaptureMouse, description);
            assertTrue(g.io.wantCaptureKeyboard, description);

            ctx.mouseClickOnVoid(mouseButton == 0 ? MouseButton.LEFT : MouseButton.RIGHT);
            if (mouseButton == 0) {
                assertEquals(null, g.navFocusedWindow, description);
                assertFalse(g.io.wantCaptureMouse, description);
                assertFalse(g.io.wantCaptureKeyboard, description);
            }
        }
    }

    /** nav_focus_scope_1: focus scopes are inherited by child windows only when flattened. */
    @Test
    void testFocusScope1() {
        final boolean[] checked = {false};
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    final int focusScopeID = IkGui.getID("MyScope");
                    final var g = ctx.context;

                    assertEquals(IkGui.getID(""), g.currentFocusScopeID);
                    IkGui.beginChild("Child 1", 100, 100);
                    assertEquals(IkGui.getID(""), g.currentFocusScopeID);
                    IkGui.endChild();

                    IkGuiImplNav.pushFocusScope(focusScopeID);
                    assertEquals(focusScopeID, g.currentFocusScopeID);
                    IkGui.beginChild("Child 1", 100, 100);
                    // Appending
                    assertEquals(IkGui.getID(""), g.currentFocusScopeID);
                    IkGui.endChild();
                    IkGui.beginChild("Child 2", 100, 100);
                    // A new child
                    assertEquals(IkGui.getID(""), g.currentFocusScopeID);
                    IkGui.endChild();
                    assertEquals(focusScopeID, g.currentFocusScopeID);

                    // Windows don't inherit
                    IkGui.begin("Test Window 2", null, WindowFlags.NO_SAVED_SETTINGS);
                    assertEquals(IkGui.getID(""), g.currentFocusScopeID);
                    IkGui.end();

                    // Unless it's a flattened child
                    IkGui.beginChild(
                            "Child 3",
                            100,
                            100,
                            ChildFlags.BORDERS | ChildFlags.NAV_FLATTENED,
                            WindowFlags.NONE);
                    assertEquals(focusScopeID, g.currentFocusScopeID);
                    IkGui.endChild();
                    IkGuiImplNav.popFocusScope();

                    assertEquals(IkGui.getID(""), g.currentFocusScopeID);
                    IkGui.end();
                    checked[0] = true;
                });
        ctx.yieldFrames(3);
        assertTrue(checked[0]);
    }

    /** nav_focus_scope_2: tabbing stays within a focus scope. */
    @Test
    void testFocusScope2() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.button("0");

                    IkGui.separator();
                    IkGuiImplNav.pushFocusScope(IkGui.getID("Scope 1"));
                    IkGui.button("1A");
                    IkGui.button("1B");
                    IkGui.button("1C");
                    IkGuiImplNav.popFocusScope();

                    IkGui.separator();
                    IkGuiImplNav.pushFocusScope(IkGui.getID("Scope 2"));
                    IkGui.button("2A");
                    IkGui.button("2B");
                    IkGui.button("2C");
                    IkGuiImplNav.popFocusScope();

                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("Test Window");
        ctx.itemClick("0");
        assertEquals(ctx.getID("0"), g.navID);
        ctx.keyPress(Key.TAB);
        assertEquals(ctx.getID("0"), g.navID);

        ctx.itemClick("1A");
        // The highlight reappears after a mouse click
        ctx.keyPress(Key.TAB);
        assertEquals(ctx.getID("1A"), g.navID);
        ctx.keyPress(Key.TAB);
        assertEquals(ctx.getID("1B"), g.navID);
        ctx.keyPress(Key.TAB);
        assertEquals(ctx.getID("1C"), g.navID);
        ctx.keyPress(Key.TAB);
        assertEquals(ctx.getID("1A"), g.navID);
        ctx.keyPress(chord(KeyModFlags.SHIFT, Key.TAB));
        assertEquals(ctx.getID("1C"), g.navID);

        ctx.itemClick("2C");
        // The highlight reappears after a mouse click
        ctx.keyPress(Key.TAB);
        assertEquals(ctx.getID("2C"), g.navID);
        ctx.keyPress(Key.TAB);
        assertEquals(ctx.getID("2A"), g.navID);
    }

    /** nav_focus_default: setting the default focus with setItemDefaultFocus(). */
    @Test
    void testFocusDefault() {
        final boolean[] showWindows = {true};
        final boolean[] setFocus = {false};
        final IkString buf = new IkString(32);
        ctx.setGui(
                () -> {
                    if (!showWindows[0]) {
                        return;
                    }
                    final float h = IkGui.getFrameHeight() * 3 + 1;
                    // Sensitive to the window width, setItemDefaultFocus() doesn't keep both edges
                    // of the item visible by default
                    IkGui.setNextWindowSize(200, h);
                    IkGui.begin(
                            "Window", null, WindowFlags.NO_SAVED_SETTINGS | WindowFlags.MENU_BAR);
                    if (IkGui.beginMenuBar()) {
                        IkGui.menuItem("Item 1");
                        IkGui.menuItem("Item 2");
                        IkGui.endMenuBar();
                    }
                    IkGui.setCursorPosY(IkGui.getCursorPosY() + 200);
                    IkGui.inputText("##1", buf);
                    IkGui.inputText("##2", buf);
                    if (setFocus[0]) {
                        IkGui.setItemDefaultFocus();
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        final Window window = ctx.getWindowByRef("Window");

        // Variant 0: ##2 is not scrolled into view, and ##1 is focused
        // Variant 1: ##2 is scrolled into view and focused, with setItemDefaultFocus()
        for (int variant = 0; variant < 2; ++variant) {
            final String description = "variant " + variant;
            setFocus[0] = variant == 1;
            showWindows[0] = true;
            ctx.yieldFrames(2);
            assertEquals(IkGuiImplNav.NAV_LAYER_MAIN, g.navLayer, description);
            if (!setFocus[0]) {
                assertEquals(ctx.getID("Window/##1"), g.navID, description);
            } else {
                assertEquals(ctx.getID("Window/##2"), g.navID, description);
            }
            final IkGuiTestContext.ItemInfo input = ctx.itemInfo("Window/##2");
            assertEquals(setFocus[0], window.rectInnerClip.contains(input.rect), description);
            // Reset the scrolling
            IkGuiInternal.setScrollY(window, 0);
            showWindows[0] = false;
            ctx.yieldFrames(2);
        }
    }

    /** nav_focus_default_multi: default focus for multiple windows at once. */
    @Test
    void testFocusDefaultMulti() {
        ctx.setGui(
                () -> {
                    for (int n = 0; n < 4; ++n) {
                        IkGui.begin(
                                "Window " + (n + 1),
                                null,
                                WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                        if (n < 3) {
                            IkGui.button("Button 1");
                            IkGui.button("Button 2");
                            if (n >= 1) {
                                IkGui.setItemDefaultFocus();
                            }
                            IkGui.button("Button 3");
                        }
                        IkGui.end();
                    }
                });
        ctx.yieldFrames(2);
        // Upstream marks the checks for the other windows as broken
        final Window window4 = ctx.getWindowByRef("Window 4");
        assertEquals(0, window4.navLastIDs[0]);
    }

    /** nav_flattened: navigating through children with ChildFlags.NAV_FLATTENED. */
    @Test
    void testFlattened() {
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);

                    IkGui.beginGroup();
                    IkGui.button("Button 1");
                    IkGui.button("Button 2");
                    IkGui.button("Button 3");
                    IkGui.endGroup();

                    IkGui.sameLine();
                    IkGui.beginChild("Child 1", 200, 200, ChildFlags.NAV_FLATTENED, 0);
                    IkGui.button("Child 1 Button 1");
                    IkGui.button("Child 1 Button 2");
                    IkGui.button("Child 1 Button 3");
                    IkGui.endChild();

                    IkGui.sameLine();
                    IkGui.beginChild("Child 2", 200, 200, ChildFlags.NAV_FLATTENED, 0);
                    IkGui.button("Child 2 Button 1");
                    IkGui.button("Child 2 Button 2");
                    IkGui.button("Child 2 Button 3");
                    IkGui.endChild();

                    IkGui.sameLine();
                    IkGui.beginChild("Child 3", 200, 200, ChildFlags.NAV_FLATTENED, 0);
                    IkGui.beginChild("Child 3B", 0, 0, ChildFlags.NAV_FLATTENED, 0);
                    IkGui.button("Child 3B Button 1");
                    IkGui.button("Child 3B Button 2");
                    IkGui.button("Child 3B Button 3");
                    IkGui.endChild();
                    IkGui.endChild();

                    IkGui.end();
                });
        final var g = ctx.context;

        // Auto fit
        ctx.windowResize("//Test Window", 0, 0);

        // Navigating in the parent
        assertEquals(ctx.getID("//Test Window/Button 1"), g.navID);
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(ctx.getID("//Test Window/Button 2"), g.navID);
        ctx.keyPress(Key.ARROW_RIGHT);

        // Parent to child
        assertEquals(ctx.getID("//$FOCUSED/Child 1 Button 2"), g.navID);
        ctx.keyPress(Key.ARROW_UP);
        assertEquals(ctx.getID("//$FOCUSED/Child 1 Button 1"), g.navID);

        // Child to parent
        ctx.keyPress(Key.ARROW_LEFT);
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(ctx.getID("//Test Window/Button 2"), g.navID);

        // Parent to child to child
        ctx.keyPress(Key.ARROW_RIGHT);
        ctx.keyPress(Key.ARROW_RIGHT);
        assertEquals(ctx.getID("//$FOCUSED/Child 2 Button 2"), g.navID);

        // Child to nested child
        ctx.keyPress(Key.ARROW_RIGHT);
        assertEquals(ctx.getID("//$FOCUSED/Child 3B Button 2"), g.navID);

        // Losing and restoring the focus by clicking on the resize grip
        final Window window = ctx.getWindowByRef("//Test Window");
        ctx.itemClick(IkGuiImplWindows.getWindowResizeID(window, 0));
        assertEquals(ctx.getID("//$FOCUSED/Child 3B Button 2"), g.navID);
    }

    /** nav_focus_flattened_default: default focus with ChildFlags.NAV_FLATTENED. */
    @Test
    void testFocusFlattenedDefault() {
        final boolean[] showWindows = {false};
        final boolean[] setFocus = {false};
        ctx.setGui(
                () -> {
                    if (!showWindows[0]) {
                        return;
                    }
                    IkGui.begin(
                            "Window 1",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);

                    IkGui.beginChild("Child 0", 200, 200, ChildFlags.NAV_FLATTENED, 0);
                    IkGui.button("Child 0 Button 1");
                    IkGui.button("Child 0 Button 2");
                    IkGui.button("Child 0 Button 3");
                    IkGui.endChild();

                    IkGui.sameLine();
                    IkGui.beginGroup();
                    IkGui.button("Button 1");
                    IkGui.button("Button 2");
                    IkGui.button("Button 3");
                    IkGui.endGroup();

                    IkGui.sameLine();
                    IkGui.beginChild("Child 1", 200, 200, ChildFlags.NAV_FLATTENED, 0);
                    IkGui.button("Child 1 Button 1");
                    IkGui.button("Child 1 Button 2");
                    if (setFocus[0]) {
                        IkGui.setItemDefaultFocus();
                    }
                    IkGui.button("Child 1 Button 3");
                    IkGui.endChild();

                    IkGui.end();
                });
        final var g = ctx.context;

        // Implicit default focus on a flattened window
        showWindows[0] = true;
        setFocus[0] = false;
        ctx.yieldFrames(2);
        assertEquals(ctx.getID("Child 0 Button 1", ctx.windowInfo("Window 1/Child 0").id), g.navID);
        showWindows[0] = false;
        ctx.yieldFrames(2);

        // setItemDefaultFocus() on a flattened window
        showWindows[0] = true;
        setFocus[0] = true;
        ctx.yieldFrames(2);
        assertEquals(ctx.getID("Child 1 Button 2", ctx.windowInfo("Window 1/Child 1").id), g.navID);
    }

    /** nav_highlight: the navigation cursor and mouse hover highlight flags. */
    @Test
    void testHighlight() {
        ctx.setGui(this::app);
        final var g = ctx.context;
        ctx.setRef(DEMO);
        ctx.mouseMove("Help");
        assertFalse(g.navCursorVisible);
        assertFalse(g.navHighlightItemUnderNav);
        ctx.navMoveTo("Configuration");
        assertTrue(g.navCursorVisible);
        assertTrue(g.navHighlightItemUnderNav);
        ctx.keyPress(Key.ARROW_UP);
        assertEquals(ctx.getID("Help"), g.navID);
        assertTrue(g.navCursorVisible);
        assertTrue(g.navHighlightItemUnderNav);
        ctx.mouseMove("Help");
        // Moving the mouse doesn't hide the cursor, but the nav item is no longer hovered
        assertTrue(g.navCursorVisible);
        assertFalse(g.navHighlightItemUnderNav);

        ctx.keyPress(KeyChord.ofMods(KeyModFlags.ALT));
        assertTrue(g.navCursorVisible);
        assertTrue(g.navHighlightItemUnderNav);

        // Switching from the mouse to the keyboard
        ctx.itemOpen("Inputs & Focus");
        ctx.itemOpen("Tabbing");
        ctx.itemClick("Tabbing/2");
        assertFalse(g.navCursorVisible);
        ctx.keyPress(Key.TAB);
        assertEquals(ctx.getID("Tabbing/3"), g.navID);
        assertTrue(g.navCursorVisible);
    }

    /** nav_appended_popup: navigating menus appended over multiple begin/end calls. */
    @Test
    void testAppendedPopup() {
        ctx.setGui(
                () -> {
                    if (IkGui.beginMainMenuBar()) {
                        if (IkGui.beginMenu("Menu")) {
                            IkGui.menuItem("a");
                            IkGui.menuItem("b");
                            IkGui.endMenu();
                        }
                        IkGui.endMainMenuBar();
                    }
                    if (IkGui.beginMainMenuBar()) {
                        if (IkGui.beginMenu("Menu")) {
                            IkGui.menuItem("c");
                            IkGui.menuItem("d");
                            IkGui.endMenu();
                        }
                        IkGui.endMainMenuBar();
                    }
                });
        ctx.setRef("##MainMenuBar");

        // Open the menu, focusing the first item
        ctx.menuClick("Menu");
        ctx.keyPress(KeyChord.ofMods(KeyModFlags.ALT));
        ctx.setRef(ctx.context.navFocusedWindow);

        // Navigate to "c"
        assertEquals(ctx.getID("a"), ctx.context.navID);
        ctx.keyPress(Key.ARROW_DOWN);
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(ctx.getID("c"), ctx.context.navID);
        ctx.keyPress(Key.ARROW_DOWN);
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(ctx.getID("a"), ctx.context.navID);
    }

    /**
     * nav_from_clipped_item: navigating from a focused item that is scrolled out of view. Upstream
     * also tests the gamepad, which resumes from the first visible item instead.
     */
    @Test
    void testFromClippedItem() {
        final Vector2f windowSize = new Vector2f();
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(windowSize.x, windowSize.y, Condition.ALWAYS);
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS
                                    | WindowFlags.ALWAYS_VERTICAL_SCROLLBAR
                                    | WindowFlags.ALWAYS_HORIZONTAL_SCROLLBAR);
                    final float scrollbarSize = ctx.context.style.variable.scrollbarSize;
                    for (int y = 0; y < 6; ++y) {
                        for (int x = 0; x < 6; ++x) {
                            IkGui.button("Button " + x + "," + y);
                            if (x < 5) {
                                IkGui.sameLine();
                            }
                            // The window size is such that only 3x3 buttons are visible at a time
                            if (windowSize.y == 0.0f && y == 3 && x == 2) {
                                windowSize.set(
                                        IkGui.getCursorPosX() + scrollbarSize,
                                        IkGui.getCursorPosY() + scrollbarSize);
                            }
                        }
                    }
                    IkGui.end();
                });
        ctx.yieldFrames(2);
        ctx.setRef("Test Window");
        final Window window = ctx.getWindowByRef("");
        final RectFloat focusRect = new RectFloat();
        final Runnable checkFocusVisible =
                () -> {
                    IkGuiImplNav.windowRectRelToAbs(
                            window, window.navRectRelative[IkGuiImplNav.NAV_LAYER_MAIN], focusRect);
                    // The item scrolled into view is fully visible
                    assertTrue(window.rectInner.contains(focusRect));
                };

        ctx.setInputMode(GuiInputSource.KEYBOARD);

        // Down, starting from the previous nav ID
        ctx.navMoveTo("Button 0,0");
        ctx.scrollToX("", 0.0f);
        ctx.scrollToBottom("");
        ctx.keyPress(Key.ARROW_DOWN);
        assertEquals(ctx.getID("Button 0,1"), ctx.context.navID);
        // Move in the opposite direction to scroll the focused item into view
        ctx.keyPress(Key.ARROW_UP);
        checkFocusVisible.run();

        // Up
        ctx.navMoveTo("Button 0,4");
        ctx.scrollToX("", 0);
        ctx.scrollToTop("");
        ctx.keyPress(Key.ARROW_UP);
        assertEquals(ctx.getID("Button 0,3"), ctx.context.navID);
        ctx.keyPress(Key.ARROW_DOWN);
        checkFocusVisible.run();

        // Right
        ctx.navMoveTo("Button 0,0");
        ctx.scrollToX("", window.scrollMax.x);
        ctx.scrollToTop("");
        ctx.keyPress(Key.ARROW_RIGHT);
        assertEquals(ctx.getID("Button 1,0"), ctx.context.navID);
        ctx.keyPress(Key.ARROW_LEFT);
        checkFocusVisible.run();

        // Left
        ctx.navMoveTo("Button 4,0");
        ctx.scrollToX("", 0);
        ctx.scrollToTop("");
        ctx.keyPress(Key.ARROW_LEFT);
        assertEquals(ctx.getID("Button 3,0"), ctx.context.navID);
        ctx.keyPress(Key.ARROW_RIGHT);
        checkFocusVisible.run();
    }

    /**
     * Submit some items, through a list clipper or not.
     *
     * @param useClipper Whether to use a clipper.
     * @param count The number of items.
     * @param item Submits an item by index.
     */
    private static void items(boolean useClipper, int count, IntConsumer item) {
        if (useClipper) {
            final ListClipper clipper = new ListClipper();
            clipper.begin(count);
            while (clipper.step()) {
                for (int i = clipper.displayStart; i < clipper.displayEnd; ++i) {
                    item.accept(i);
                }
            }
        } else {
            for (int i = 0; i < count; ++i) {
                item.accept(i);
            }
        }
    }

    /** nav_page_home_end_arrows: page up/down, home/end and the arrows with navigable items. */
    @Test
    void testPageHomeEndArrows() {
        final boolean[] useClipper = {false};
        final float[] windowHeight = {0};
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(100, windowHeight[0], Condition.ALWAYS);
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS
                                    | WindowFlags.ALWAYS_VERTICAL_SCROLLBAR
                                    | WindowFlags.ALWAYS_HORIZONTAL_SCROLLBAR
                                    | WindowFlags.NO_COLLAPSE);
                    windowHeight[0] =
                            (float)
                                    Math.floor(
                                            IkGui.getCursorPosY()
                                                    + IkGui.getTextLineHeightWithSpacing() * 20.8f
                                                    + ctx.context.style.variable.scrollbarSize);
                    items(
                            useClipper[0],
                            200,
                            i -> {
                                IkGui.smallButton("OK " + i);
                                IkGui.sameLine();
                                IkGui.smallButton("OK B" + i);
                            });
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("Test Window");
        final Window window = ctx.getWindowByRef("");

        for (int step = 0; step < 2; ++step) {
            final String description = "step " + step;
            useClipper[0] = step == 1;

            // Page up/down and home/end with navigable items
            ctx.yieldFrames(2);
            g.navID = 0;
            ctx.scrollToX("", 0.0f);
            ctx.scrollToY("", 0.0f);

            // The focus moves to the last visible button
            ctx.keyPress(Key.PAGE_DOWN);
            assertEquals(ctx.getID("OK 20"), g.navID, description);
            // The window doesn't scroll
            assertEquals(0.0f, window.scrollPosition.y, description);
            // The focus moves to the next page
            ctx.keyPress(Key.PAGE_DOWN);
            assertEquals(ctx.getID("OK 40"), g.navID, description);
            assertTrue(window.scrollPosition.y > 0.0f, description);
            // Focus the last item
            ctx.keyPress(Key.END);
            assertEquals(window.scrollMax.y, window.scrollPosition.y, description);
            assertEquals(ctx.getID("OK 199"), g.navID, description);
            // Focus the first visible item in the last section, without scrolling
            ctx.keyPress(Key.PAGE_UP);
            assertEquals(ctx.getID("OK 179"), g.navID, description);
            assertEquals(window.scrollMax.y, window.scrollPosition.y, description);
            // Focus the first item of the previous page
            ctx.keyPress(Key.PAGE_UP);
            assertEquals(ctx.getID("OK 159"), g.navID, description);
            assertTrue(
                    0 < window.scrollPosition.y && window.scrollPosition.y < window.scrollMax.y,
                    description);
            // Focus the item in the second column, the scrollbar moves
            ctx.keyPress(Key.ARROW_RIGHT);
            assertTrue(window.scrollPosition.x > 0.0f, description);
            assertEquals(ctx.getID("OK B159"), g.navID, description);
            ctx.keyPress(Key.ARROW_LEFT);
            assertEquals(ctx.getID("OK 159"), g.navID, description);
            // Focus the very first item, scrolling to the start
            ctx.keyPress(Key.HOME);
            assertEquals(ctx.getID("OK 0"), g.navID, description);
            assertEquals(0.0f, window.scrollPosition.y, description);

            // The arrow keys with navigable items
            ctx.keyPress(Key.ARROW_DOWN);
            assertEquals(0.0f, window.scrollPosition.y, description);
            assertEquals(ctx.getID("OK 1"), g.navID, description);
            ctx.keyPress(Key.ARROW_UP);
            assertEquals(0.0f, window.scrollPosition.y, description);
            assertEquals(ctx.getID("OK 0"), g.navID, description);
            assertEquals(0.0f, window.scrollPosition.x, description);
            ctx.keyPress(Key.ARROW_RIGHT);
            assertTrue(window.scrollPosition.x > 0.0f, description);
            assertEquals(ctx.getID("OK B0"), g.navID, description);
            ctx.keyPress(Key.ARROW_LEFT);
            assertEquals(0.0f, window.scrollPosition.x, description);
            assertEquals(ctx.getID("OK 0"), g.navID, description);
        }
    }

    /** nav_scroll_when_no_items: navigation keys scroll windows without navigable items. */
    @Test
    void testScrollWhenNoItems() {
        final boolean[] useClipper = {false};
        final float[] windowHeight = {0};
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(
                            IkGui.calcTextSize("OK 8").x, windowHeight[0], Condition.ALWAYS);
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS
                                    | WindowFlags.ALWAYS_VERTICAL_SCROLLBAR
                                    | WindowFlags.ALWAYS_HORIZONTAL_SCROLLBAR
                                    | WindowFlags.NO_COLLAPSE);
                    windowHeight[0] =
                            (float)
                                    Math.floor(
                                            IkGui.getCursorPosY()
                                                    + IkGui.getFrameHeightWithSpacing() * 3.0f
                                                    + ctx.context.style.variable.scrollbarSize);
                    items(
                            useClipper[0],
                            20,
                            i -> {
                                IkGui.textUnformatted("OK " + i);
                                IkGui.sameLine();
                                IkGui.textUnformatted("OK 5" + i);
                            });
                    IkGui.end();
                });
        ctx.yieldFrames(2);
        ctx.setRef("Test Window");
        final Window window = ctx.getWindowByRef("");

        for (int step = 0; step < 2; ++step) {
            final String description = "step " + step;
            useClipper[0] = step == 1;
            ctx.yieldFrame();

            // Page up/down and home/end without any navigable items
            assertTrue(window.scrollMax.y > 0.0f, description);
            IkGuiInternal.setScrollY(window, 0.0f);
            IkGuiInternal.setScrollX(window, 0.0f);

            // Scrolled down some, but not to the bottom
            ctx.keyHold(KeyChord.of(Key.PAGE_DOWN), 0.1f);
            assertTrue(
                    0 < window.scrollPosition.y && window.scrollPosition.y < window.scrollMax.y,
                    description);
            // Scrolled all the way to the bottom
            ctx.keyPress(Key.END);
            assertEquals(window.scrollMax.y, window.scrollPosition.y, description);
            // Scrolled up some, but not all the way to the top
            final float lastScroll = window.scrollPosition.y;
            ctx.keyHold(KeyChord.of(Key.PAGE_UP), 0.1f);
            assertTrue(
                    0 < window.scrollPosition.y && window.scrollPosition.y < lastScroll,
                    description);
            // Scrolled all the way to the top
            ctx.keyPress(Key.HOME);
            assertEquals(0.0f, window.scrollPosition.y, description);

            // The arrow keys without any navigable items scroll by a tick
            ctx.keyHold(KeyChord.of(Key.ARROW_DOWN), 0.1f);
            assertTrue(window.scrollPosition.y > 0.0f, description);
            ctx.keyHold(KeyChord.of(Key.ARROW_UP), 0.1f);
            assertEquals(0.0f, window.scrollPosition.y, description);
            assertEquals(0.0f, window.scrollPosition.x, description);
            ctx.keyHold(KeyChord.of(Key.ARROW_RIGHT), 0.1f);
            assertTrue(window.scrollPosition.x > 0.0f, description);
            ctx.keyHold(KeyChord.of(Key.ARROW_LEFT), 0.1f);
            assertEquals(0.0f, window.scrollPosition.x, description);
        }
    }

    /** nav_tabbing_basic: Tab cycles through items. */
    @Test
    void testTabbingBasic() {
        final int[] widgetType = {0};
        final float[][] floats = new float[5][1];
        final IkString[] bufs = new IkString[5];
        for (int i = 0; i < 5; ++i) {
            bufs[i] = new IkString("buf" + i, 256);
        }
        ctx.setGui(
                () -> {
                    final var workPos = IkGui.getMainViewport().workPosition;
                    IkGui.setNextWindowPos(workPos.x + 10, workPos.y + 10);
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.ALWAYS_AUTO_RESIZE);
                    for (int n = 0; n < 10; ++n) {
                        IkGui.text("Filler");
                    }
                    final java.util.function.BiConsumer<String, Integer> outWidget =
                            (label, n) -> {
                                switch (widgetType[0]) {
                                    case 0 -> IkGui.inputText(label, bufs[n]);
                                    case 1 -> IkGui.sliderFloat(label, floats[n], 0.0f, 1.0f);
                                    case 2 -> IkGui.dragFloat(label, floats[n], 0.1f, 0.0f, 1.0f);
                                    default ->
                                            IkGui.inputTextMultiline(
                                                    label,
                                                    bufs[n],
                                                    0,
                                                    IkGui.getTextLineHeight() * 2);
                                }
                            };
                    outWidget.accept("Item0", 0);
                    outWidget.accept("Item1", 1);
                    IkGui.pushItemFlag(ItemFlags.NO_TAB_STOP, true);
                    outWidget.accept("Item2", 2);
                    IkGui.popItemFlag();
                    outWidget.accept("Item3", 3);
                    outWidget.accept("Item4", 4);
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("Test Window");
        // Upstream also has a step for tabbing through clipped widgets, which it doesn't support
        for (widgetType[0] = 0; widgetType[0] < 4; ++widgetType[0]) {
            final String description = "widget type " + widgetType[0];
            ctx.yieldFrame();
            assertEquals(0, g.activeID, description);
            ctx.keyPress(Key.TAB);
            assertEquals(ctx.getID("Item0"), g.activeID, description);
            ctx.keyPress(Key.TAB);
            assertEquals(ctx.getID("Item1"), g.activeID, description);
            ctx.keyPress(Key.TAB);
            assertEquals(ctx.getID("Item3"), g.activeID, description);
            ctx.keyPress(Key.TAB);
            assertEquals(ctx.getID("Item4"), g.activeID, description);

            // Wrapping
            ctx.keyPress(Key.TAB);
            assertEquals(ctx.getID("Item0"), g.activeID, description);

            // Shift+Tab
            ctx.keyPress(chord(KeyModFlags.SHIFT, Key.TAB));
            assertEquals(ctx.getID("Item4"), g.activeID, description);
            ctx.keyPress(chord(KeyModFlags.SHIFT, Key.TAB));
            assertEquals(ctx.getID("Item3"), g.activeID, description);
            ctx.keyPress(chord(KeyModFlags.SHIFT, Key.TAB));
            assertEquals(ctx.getID("Item1"), g.activeID, description);
            ctx.keyPress(chord(KeyModFlags.SHIFT, Key.TAB));
            assertEquals(ctx.getID("Item0"), g.activeID, description);
            ctx.keyPress(Key.ESCAPE);

            // Leaving an item without a tab stop
            ctx.itemInput("Item2");
            assertEquals(ctx.getID("Item2"), g.activeID, description);
            ctx.keyPress(Key.TAB);
            assertEquals(ctx.getID("Item3"), g.activeID, description);
            ctx.itemInput("Item2");
            assertEquals(ctx.getID("Item2"), g.activeID, description);
            ctx.keyPress(chord(KeyModFlags.SHIFT, Key.TAB));
            assertEquals(ctx.getID("Item1"), g.activeID, description);

            // Activating with the mouse hides the nav cursor
            ctx.itemInput("Item0");
            ctx.keyPress(Key.ESCAPE);
        }
    }

    /** nav_tabbing_clipped: tabbing through clipped items in a list clipper. */
    @Test
    void testTabbingClipped() {
        final IkInt value = new IkInt(0);
        ctx.setGui(
                () -> {
                    // Make sure items are clipped
                    IkGui.setNextWindowSize(400, 100);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    items(true, 50, n -> IkGui.inputInt("Input" + n, value, 0, 0));
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("Test Window");
        for (int n = 0; n < 52; ++n) {
            ctx.keyPress(Key.TAB);
            assertEquals(ctx.getID("Input" + (n % 50)), g.activeID, "tab " + n);
        }
        // On 51
        for (int n = 0; n < 4; ++n) {
            assertEquals(ctx.getID("Input" + ((51 - n) % 50)), g.activeID);
            ctx.keyPress(chord(KeyModFlags.SHIFT, Key.TAB));
            assertEquals(ctx.getID("Input" + ((50 - n) % 50)), g.activeID);
        }
    }

    /** nav_tabbing_flattened: tabbing through flattened, clipped child windows. */
    @Test
    void testTabbingFlattened() {
        final IkInt value = new IkInt(0);
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    final float childHeight = IkGui.getFrameHeightWithSpacing() * 3;
                    final int childFlags = ChildFlags.BORDERS | ChildFlags.NAV_FLATTENED;
                    for (int n = 0; n < 4; ++n) {
                        IkGui.inputInt("Input" + n, value, 0, 0);
                    }
                    IkGui.beginChild("Child 1", 0, childHeight, childFlags, 0);
                    for (int n = 4; n < 8; ++n) {
                        IkGui.inputInt("Input" + n, value, 0, 0);
                    }
                    IkGui.endChild();
                    for (int n = 8; n < 10; ++n) {
                        IkGui.inputInt("Input" + n, value, 0, 0);
                    }
                    IkGui.beginChild("Child 2", 0, childHeight, childFlags, 0);
                    for (int n = 10; n < 14; ++n) {
                        IkGui.inputInt("Input" + n, value, 0, 0);
                    }
                    IkGui.beginChild("Child 3", 0, childHeight, childFlags, 0);
                    for (int n = 14; n < 18; ++n) {
                        IkGui.inputInt("Input" + n, value, 0, 0);
                    }
                    IkGui.endChild();
                    for (int n = 18; n < 20; ++n) {
                        IkGui.inputInt("Input" + n, value, 0, 0);
                    }
                    IkGui.endChild();
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("Test Window");
        for (int n = 0; n < 22; ++n) {
            ctx.keyPress(Key.TAB);
            final IkGuiTestContext.ItemInfo item = ctx.itemInfo(g.activeID, "the active item");
            assertEquals("Input" + (n % 20), item.label);
        }
        // On 21
        for (int n = 0; n < 4; ++n) {
            assertEquals(
                    "Input" + ((21 - n) % 20), ctx.itemInfo(g.activeID, "the active item").label);
            ctx.keyPress(chord(KeyModFlags.SHIFT, Key.TAB));
            assertEquals(
                    "Input" + ((20 - n) % 20), ctx.itemInfo(g.activeID, "the active item").label);
        }
    }

    /** nav_focus_api: setKeyboardFocusHere(). */
    @Test
    void testFocusApi() {
        final IkString str1 = new IkString(256);
        final IkString str2 = new IkString(256);
        final float[] floatArray = new float[4];
        final int[] step = {0};
        final boolean[] bool1 = {false};
        final IkGuiTestContext.ItemStatus status = new IkGuiTestContext.ItemStatus();
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(300, 200, Condition.APPEARING);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    switch (step[0]) {
                        case 1 -> {
                            IkGui.setKeyboardFocusHere();
                            IkGui.inputText("Text1", str1);
                            status.querySet(false);
                        }
                        case 2 -> {
                            IkGui.inputText("Text2", str1);
                            status.querySet(false);
                            IkGui.setKeyboardFocusHere(-1);
                        }
                        case 3 -> {
                            // Multiple overriding calls in the same frame, the last one wins
                            IkGui.setKeyboardFocusHere();
                            IkGui.inputText("Text1", str1);
                            IkGui.setKeyboardFocusHere();
                            IkGui.inputText("Text2", str1);
                        }
                        case 4 -> {
                            // A sub-component
                            IkGui.setKeyboardFocusHere(2);
                            IkGui.sliderFloat4("Float4", floatArray, 0.0f, 1.0f);
                        }
                        case 5 -> {
                            if (bool1[0]) {
                                IkGui.setKeyboardFocusHere();
                                IkGui.inputText("Text1", str1);
                            }
                            IkGui.inputText("NoFocus1", str2);
                            status.querySet(false);
                        }
                        case 6 -> {
                            if (bool1[0]) {
                                IkGui.inputText("Text1", str1);
                                IkGui.setKeyboardFocusHere(-1);
                            }
                            IkGui.inputText("NoFocus1", str2);
                            status.querySet(false);
                        }
                        case 7 -> {
                            IkGui.pushItemFlag(ItemFlags.NO_TAB_STOP, true);
                            IkGui.setKeyboardFocusHere();
                            IkGui.inputText("Text1", str1);
                            status.querySet(false);
                            IkGui.popItemFlag();
                        }
                        case 8 -> {
                            IkGui.setKeyboardFocusHere();
                            IkGui.button("Button1");
                            status.querySet(false);
                        }
                        case 9 -> {
                            if (IkGui.button("Focus")) {
                                IkGui.setKeyboardFocusHere();
                            }
                            IkGui.inputTextMultiline("TextMultiline1", str1);
                            status.querySet(false);
                        }
                        case 10 -> {
                            final boolean focus = IkGui.button("Focus");
                            IkGui.inputTextMultiline("TextMultiline2", str1);
                            status.querySet(false);
                            if (focus) {
                                IkGui.setKeyboardFocusHere(-1);
                            }
                        }
                        case 11 -> {
                            // setKeyboardFocusHere(0) doesn't wrap
                            IkGui.inputText("Text1", str1);
                            IkGui.inputText("Text2", str1);
                            IkGui.setKeyboardFocusHere(0);
                            IkGui.begin("Test Window 2", null, WindowFlags.NO_SAVED_SETTINGS);
                            IkGui.inputText("Text3", str1);
                            IkGui.end();
                        }
                        case 12 -> {
                            // setKeyboardFocusHere(-1) doesn't wrap
                            IkGui.setKeyboardFocusHere(-1);
                            IkGui.inputText("Text1", str1);
                            IkGui.inputText("Text2", str1);
                            IkGui.begin("Test Window 2", null, WindowFlags.NO_SAVED_SETTINGS);
                            IkGui.inputText("Text3", str1);
                            IkGui.end();
                        }
                        default -> {}
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("Test Window");

        // Focusing the next item with setKeyboardFocusHere(0)
        step[0] = 1;
        ctx.yieldFrame();
        assertEquals(0, g.activeID);
        assertEquals(0, status.activated);
        ctx.yieldFrame();
        assertEquals(ctx.getID("Text1"), g.activeID);
        assertEquals(1, status.activated);

        // The active ID gets cleared when not alive
        step[0] = 0;
        ctx.yieldFrames(2);
        assertEquals(0, g.activeID);

        // Focusing the previous item with setKeyboardFocusHere(-1)
        step[0] = 2;
        ctx.yieldFrame();
        assertEquals(0, g.activeID);
        assertEquals(0, status.activated);
        ctx.yieldFrame();
        assertEquals(ctx.getID("Text2"), g.activeID);
        assertEquals(1, status.activated);

        step[0] = 0;
        ctx.yieldFrames(2);

        // Focusing the next item with setKeyboardFocusHere(0), from a button
        step[0] = 9;
        ctx.yieldFrame();
        assertEquals(0, g.activeID);
        assertEquals(0, status.active);
        ctx.itemClick("Focus");
        ctx.yieldFrames(2);
        assertEquals(ctx.getID("TextMultiline1"), g.activeID);
        assertEquals(1, status.active);

        // The active ID gets cleared when not alive
        step[0] = 0;
        ctx.yieldFrames(2);
        assertEquals(0, g.activeID);

        // Focusing the previous item with setKeyboardFocusHere(-1), from a button
        step[0] = 10;
        ctx.yieldFrame();
        assertEquals(0, g.activeID);
        assertEquals(0, status.active);
        ctx.itemClick("Focus");
        ctx.yieldFrames(2);
        assertEquals(ctx.getID("TextMultiline2"), g.activeID);
        assertEquals(1, status.active);

        // setKeyboardFocusHere(0) doesn't wrap
        step[0] = 11;
        ctx.yieldFrames(3);
        assertEquals(0, g.activeID);

        // setKeyboardFocusHere(-1) doesn't wrap
        step[0] = 12;
        ctx.yieldFrames(3);
        assertEquals(0, g.activeID);

        // Multiple overriding calls in the same frame, the last one wins
        step[0] = 0;
        ctx.yieldFrames(2);
        step[0] = 3;
        ctx.yieldFrames(2);
        assertEquals(ctx.getID("Text2"), g.activeID);

        // Accessing a sub-component
        step[0] = 0;
        ctx.yieldFrames(2);
        step[0] = 4;
        ctx.yieldFrames(2);
        assertEquals(ctx.getID("Float4/$$2"), g.activeID);

        // Focusing the next item when it disappears
        step[0] = 5;
        bool1[0] = true;
        ctx.yieldFrames(2);
        assertEquals(ctx.getID("Text1"), g.activeID);
        assertEquals(0, status.activated);
        bool1[0] = false;
        ctx.yieldFrame();
        assertEquals(0, status.activated);
        step[0] = 0;
        ctx.yieldFrame();

        // Focusing the previous item when it disappears
        step[0] = 6;
        bool1[0] = true;
        ctx.yieldFrames(2);
        assertEquals(ctx.getID("Text1"), g.activeID);
        assertEquals(0, status.activated);
        bool1[0] = false;
        ctx.yieldFrames(2);
        assertEquals(0, g.activeID);
        assertEquals(0, status.activated);

        // Focusing an item without a tab stop
        step[0] = 7;
        ctx.yieldFrames(2);
        assertEquals(ctx.getID("Text1"), g.activeID);
        assertEquals(1, status.activated);

        // The text input can be used while it is repeatedly refocused
        str1.set("");
        ctx.keyCharsReplaceEnter("Hello");
        assertEquals("Hello", str1.get());
        ctx.keyCharsReplaceEnter("");
        assertEquals("", str1.get());

        // setKeyboardFocusHere() on a button doesn't press it
        step[0] = 8;
        bool1[0] = true;
        ctx.yieldFrames(2);
        assertEquals(ctx.getID("Button1"), g.navID);
        assertEquals(0, status.activated);
    }

    /** nav_focus_restore_menu: the nav ID is restored after activating a menu item. */
    @Test
    void testFocusRestoreMenu() {
        ctx.setGui(this::app);
        final var g = ctx.context;
        final Window demoWindow = ctx.getWindowByRef(DEMO);

        for (int testN = 0; testN < 2; ++testN) {
            final String description = "case " + testN;
            ctx.setRef(0);
            ctx.dockClear(DEMO, "Hello, world!");
            if (testN == 0) {
                ctx.dockInto(DEMO, "Hello, world!");
            }
            ctx.setRef(DEMO);
            ctx.itemCloseAll("");

            // Simple focus restoration
            ctx.navMoveTo("Configuration");
            // Focus the menu
            ctx.keyPress(KeyChord.ofMods(KeyModFlags.ALT));
            assertEquals(IkGuiImplNav.NAV_LAYER_MENU, g.navLayer, description);
            // Open the menu, focusing its first item
            ctx.navActivate();
            // Activate the first item in the menu
            ctx.navActivate();
            // The nav ID was restored
            assertEquals(ctx.getID("Configuration"), g.navID, description);

            // Focus restoration to an item within a child window
            ctx.navMoveTo("Layout & Scrolling");
            ctx.navActivate();
            ctx.navMoveTo("Scrolling");
            ctx.navActivate();
            final Window childWindow = ctx.windowInfo("Scrolling/scrolling");
            assertNotNull(childWindow, description);
            ctx.setRef(childWindow);
            // Buttons don't register their IDs when out of view
            ctx.scrollToY(demoWindow, childWindow.position.y - demoWindow.cursorStartPosition.y);
            // Focus an item within a child window
            ctx.navMoveTo("$$1/1");
            // Focus the menu
            ctx.keyPress(KeyChord.ofMods(KeyModFlags.ALT));
            // Open the menu, focusing its first item
            ctx.navActivate();
            // Activate the first item in the menu
            ctx.navActivate();
            // The nav ID was restored
            assertEquals(ctx.getID("$$1/1"), g.navID, description);

            ctx.setRef(DEMO);
            ctx.itemClose("Scrolling");
            ctx.itemClose("Layout & Scrolling");
        }
    }

    /** nav_focus_api_clipped: setKeyboardFocusHere() on clipped items. */
    @Test
    void testFocusApiClipped() {
        final IkString str = new IkString(256);
        final int[] step = {0};
        final IkGuiTestContext.ItemStatus[] status = {
            new IkGuiTestContext.ItemStatus(),
            new IkGuiTestContext.ItemStatus(),
            new IkGuiTestContext.ItemStatus()
        };
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(300, 200, Condition.APPEARING);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    for (int i = 0; i < 3; ++i) {
                        for (int n = 0; n < 50; ++n) {
                            IkGui.text("Dummy " + n);
                        }
                        IkGui.inputText("Text" + (i + 1), str);
                        status[i].querySet(false);
                        if (step[0] == i + 1) {
                            IkGui.setKeyboardFocusHere(-1);
                        }
                    }
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("Test Window");

        assertEquals(0, g.activeID);
        step[0] = 1;
        // Takes two frames
        ctx.yieldFrame();
        assertEquals(0, g.activeID);
        ctx.yieldFrame();
        assertEquals(ctx.getID("Text1"), g.activeID);
        assertEquals(1, status[0].visible);
        assertEquals(0, status[1].visible);
        assertEquals(0, status[2].visible);
        ctx.yieldFrames(2);

        step[0] = 2;
        ctx.yieldFrames(2);
        assertEquals(ctx.getID("Text2"), g.activeID);
        assertEquals(0, status[0].visible);
        assertEquals(1, status[1].visible);
        assertEquals(0, status[2].visible);

        step[0] = 3;
        ctx.yieldFrames(2);
        assertEquals(ctx.getID("Text3"), g.activeID);
        assertEquals(0, status[0].visible);
        assertEquals(0, status[1].visible);
        assertEquals(1, status[2].visible);

        step[0] = 1;
        ctx.yieldFrame();
        step[0] = 0;
        ctx.yieldFrame();
        assertEquals(ctx.getID("Text1"), g.activeID);
        assertEquals(1, status[0].visible);
        assertEquals(0, status[1].visible);
        assertEquals(0, status[2].visible);
    }

    /** nav_focus_api_remote: setKeyboardFocusHere() across windows. */
    @Test
    void testFocusApiRemote() {
        ctx.setGui(
                () -> {
                    int setFocus = -1;
                    IkGui.begin("Test Window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.button("Focus A")) {
                        setFocus = 0;
                    }
                    if (IkGui.button("Focus B")) {
                        setFocus = 1;
                    }
                    IkGui.end();

                    IkGui.begin("Test Window 2", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.button("Focus A")) {
                        setFocus = 0;
                    }
                    if (IkGui.button("Focus B")) {
                        setFocus = 1;
                    }
                    IkGui.end();

                    final IkString dummy = new IkString(16);
                    IkGui.begin("Test Window 1", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (setFocus == 0) {
                        IkGui.setKeyboardFocusHere();
                    }
                    IkGui.inputText("Item A", dummy);
                    IkGui.end();
                    IkGui.begin("Test Window 2", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (setFocus == 1) {
                        IkGui.setKeyboardFocusHere();
                    }
                    IkGui.inputText("Item B", dummy);
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.itemClick("//Test Window 1/Focus A");
        assertEquals(ctx.getID("//Test Window 1/Item A"), g.activeID);
        ctx.itemClick("//Test Window 1/Focus B");
        assertEquals(ctx.getID("//Test Window 2/Item B"), g.activeID);
        ctx.itemClick("//Test Window 2/Focus A");
        assertEquals(ctx.getID("//Test Window 1/Item A"), g.activeID);
        ctx.itemClick("//Test Window 2/Focus B");
        assertEquals(ctx.getID("//Test Window 2/Item B"), g.activeID);
    }

    /** nav_focus_api_on_window_void: a focus request made when clicking a text item. */
    @Test
    void testFocusApiOnWindowVoid() {
        final Vector2f pos = new Vector2f();
        final float[] value = {0};
        ctx.setGui(
                () -> {
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    IkGui.text("Some Text");
                    final Vector2f min = new Vector2f();
                    final Vector2f max = new Vector2f();
                    IkGui.getItemRectMin(min);
                    IkGui.getItemRectMax(max);
                    pos.set(min).add(max).mul(0.5f);
                    final boolean focus = IkGui.isItemClicked();
                    IkGui.sliderFloat("float", value, 0.0f, 1.0f);
                    if (focus) {
                        IkGui.setKeyboardFocusHere(-1);
                    }
                    IkGui.end();
                });
        ctx.setRef("Test Window");
        ctx.windowFocus("");
        ctx.mouseMoveToPos(pos.x, pos.y);
        ctx.mouseClick(MouseButton.LEFT);
        assertEquals(ctx.getID("float"), ctx.context.activeID);
    }

    private static int dpad(boolean gamepad, Key keyboardKey) {
        if (!gamepad) {
            return KeyChord.of(keyboardKey);
        }
        return KeyChord.of(
                switch (keyboardKey) {
                    case ARROW_UP -> Key.GAMEPAD_DPAD_UP;
                    case ARROW_DOWN -> Key.GAMEPAD_DPAD_DOWN;
                    case ARROW_LEFT -> Key.GAMEPAD_DPAD_LEFT;
                    default -> Key.GAMEPAD_DPAD_RIGHT;
                });
    }

    /** nav_wrapping: the wrapping and looping move flags. */
    @Test
    void testWrapping() {
        final IkInt wrapFlags = new IkInt(NavMoveFlags.WRAP_Y);
        final IkBoolean altLayout = new IkBoolean(false);
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(300, 300, Condition.APPEARING);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    for (int ny = 0; ny < 4; ++ny) {
                        for (int nx = 0; nx < 4; ++nx) {
                            if (!altLayout.get() && ny == 3 && nx >= 2) {
                                continue;
                            }
                            if (altLayout.get() && nx == 3 && ny >= 2) {
                                continue;
                            }
                            if (nx > 0) {
                                IkGui.sameLine();
                            }
                            IkGui.selectable(ny + "," + nx, true, 0, 40, 40);
                        }
                    }
                    if (wrapFlags.get() != 0) {
                        IkGuiImplNav.navMoveRequestTryWrapping(
                                IkGuiInternal.getCurrentWindow(), wrapFlags.get());
                    }
                    final Window window = IkGuiInternal.getCurrentWindow();
                    final float optionsX = window.position.x;
                    final float optionsY = window.position.y + window.size.y;
                    IkGui.end();

                    IkGui.setNextWindowPos(optionsX, optionsY);
                    IkGui.begin(
                            "Test Options",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS | WindowFlags.NO_FOCUS_ON_APPEARING);
                    IkGui.radioButton("NavMoveFlags.NONE", wrapFlags, 0);
                    IkGui.radioButton("NavMoveFlags.WRAP_X", wrapFlags, NavMoveFlags.WRAP_X);
                    IkGui.radioButton("NavMoveFlags.WRAP_Y", wrapFlags, NavMoveFlags.WRAP_Y);
                    IkGui.radioButton("NavMoveFlags.LOOP_X", wrapFlags, NavMoveFlags.LOOP_X);
                    IkGui.radioButton("NavMoveFlags.LOOP_Y", wrapFlags, NavMoveFlags.LOOP_Y);
                    IkGui.checkbox("AltLayout", altLayout);
                    IkGui.end();
                });
        final var g = ctx.context;
        ctx.setRef("Test Window");

        for (int inputSource = 0; inputSource < 2; ++inputSource) {
            final boolean gamepad = inputSource == 1;
            final String description = gamepad ? "gamepad" : "keyboard";
            if (gamepad) {
                ctx.setInputMode(GuiInputSource.GAMEPAD);
                ctx.setInputMode(GuiInputSource.MOUSE);
            }
            final int keyUp = dpad(gamepad, Key.ARROW_UP);
            final int keyDown = dpad(gamepad, Key.ARROW_DOWN);
            final int keyLeft = dpad(gamepad, Key.ARROW_LEFT);
            final int keyRight = dpad(gamepad, Key.ARROW_RIGHT);

            wrapFlags.set(0);
            ctx.itemClick("0,0");
            ctx.keyPress(keyRight);
            assertEquals(ctx.getID("0,1"), g.navID, description);
            ctx.keyPress(keyRight);
            ctx.keyPress(keyRight);
            assertEquals(ctx.getID("0,3"), g.navID, description);
            ctx.keyPress(keyRight);
            assertEquals(ctx.getID("0,3"), g.navID, description);
            ctx.itemClick("3,1");
            ctx.keyPress(keyRight);
            assertEquals(ctx.getID("3,1"), g.navID, description);
            ctx.itemClick("2,2");
            ctx.keyPress(keyDown);
            assertEquals(ctx.getID("3,1"), g.navID, description);
            ctx.keyPress(keyUp);
            // The preferred position is restored
            assertEquals(ctx.getID("2,2"), g.navID, description);

            wrapFlags.set(NavMoveFlags.WRAP_X);
            ctx.itemClick("0,0");
            ctx.keyPress(keyRight);
            assertEquals(ctx.getID("0,1"), g.navID, description);
            ctx.keyPress(keyRight);
            ctx.keyPress(keyRight);
            assertEquals(ctx.getID("0,3"), g.navID, description);
            ctx.keyPress(keyRight);
            assertEquals(ctx.getID("1,0"), g.navID, description);
            // Mash to get to the end of the list
            ctx.keyPress(keyRight, 50);
            assertEquals(ctx.getID("3,1"), g.navID, description);
            ctx.keyPress(keyLeft);
            ctx.keyPress(keyLeft);
            assertEquals(ctx.getID("2,3"), g.navID, description);

            wrapFlags.set(NavMoveFlags.LOOP_X);
            ctx.itemClick("0,0");
            ctx.keyPress(keyRight);
            assertEquals(ctx.getID("0,1"), g.navID, description);
            ctx.keyPress(keyRight);
            ctx.keyPress(keyRight);
            assertEquals(ctx.getID("0,3"), g.navID, description);
            ctx.keyPress(keyRight);
            assertEquals(ctx.getID("0,0"), g.navID, description);
            ctx.keyPress(keyLeft);
            assertEquals(ctx.getID("0,3"), g.navID, description);
            ctx.itemClick("3,0");
            ctx.keyPress(keyRight);
            assertEquals(ctx.getID("3,1"), g.navID, description);
            ctx.keyPress(keyRight);
            assertEquals(ctx.getID("3,0"), g.navID, description);
            ctx.keyPress(keyLeft);
            assertEquals(ctx.getID("3,1"), g.navID, description);
            ctx.keyPress(keyLeft);
            assertEquals(ctx.getID("3,0"), g.navID, description);

            wrapFlags.set(NavMoveFlags.LOOP_Y);
            ctx.itemClick("0,0");
            ctx.keyPress(keyRight);
            assertEquals(ctx.getID("0,1"), g.navID, description);
            ctx.keyPress(keyDown);
            ctx.keyPress(keyDown);
            ctx.keyPress(keyDown);
            assertEquals(ctx.getID("3,1"), g.navID, description);
            ctx.keyPress(keyDown);
            assertEquals(ctx.getID("0,1"), g.navID, description);
            ctx.keyPress(keyUp);
            assertEquals(ctx.getID("3,1"), g.navID, description);
        }
    }

    /** nav_wrapping_clipped: wrapping with a list clipper. */
    @Test
    void testWrappingClipped() {
        final IkInt value = new IkInt(0);
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(300, 300, Condition.APPEARING);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    items(true, 50, n -> IkGui.inputInt("Input" + n, value, 0, 0));
                    IkGuiImplNav.navMoveRequestTryWrapping(
                            IkGuiInternal.getCurrentWindow(), NavMoveFlags.LOOP_Y);
                    IkGui.end();
                });
        ctx.yieldFrame();
        final var g = ctx.context;
        ctx.setRef("Test Window");

        for (int inputSource = 0; inputSource < 2; ++inputSource) {
            final boolean gamepad = inputSource == 1;
            final String description = gamepad ? "gamepad" : "keyboard";
            if (gamepad) {
                ctx.setInputMode(GuiInputSource.GAMEPAD);
            }
            final int keyUp = dpad(gamepad, Key.ARROW_UP);
            final int keyDown = dpad(gamepad, Key.ARROW_DOWN);
            for (int n = 0; n < 52; ++n) {
                assertEquals(ctx.getID("Input" + (n % 50)), g.navID, description);
                ctx.keyPress(keyDown);
                assertEquals(ctx.getID("Input" + ((n + 1) % 50)), g.navID, description);
            }
            // On 51
            for (int n = 0; n < 4; ++n) {
                assertEquals(ctx.getID("Input" + ((52 - n) % 50)), g.navID, description);
                ctx.keyPress(keyUp);
                assertEquals(ctx.getID("Input" + ((51 - n) % 50)), g.navID, description);
            }
            ctx.navMoveTo("Input0");
        }
    }

    /**
     * nav_columns_clip: clipping within tables, which aren't scrollable containers. Upstream also
     * tests legacy columns, which aren't ported.
     */
    @Test
    void testColumnsClip() {
        ctx.setGui(
                () -> {
                    IkGui.pushStyleVarFloat2(StyleVariable.BUTTON_TEXT_ALIGN, 0.0f, 0.0f);
                    IkGui.setNextWindowSize(200, 500);
                    IkGui.begin("Test Window", null, WindowFlags.NO_SAVED_SETTINGS);
                    if (IkGui.beginTable("table", 2)) {
                        IkGui.tableNextColumn();
                        IkGui.button("2-SMALL");
                        IkGui.tableNextColumn();
                        IkGui.button("2-LARGE", 400, 0);
                        IkGui.tableNextColumn();
                        IkGui.button("3-LARGE", 400, 0);
                        IkGui.tableNextColumn();
                        IkGui.button("3-SMALL");
                        IkGui.endTable();
                    }
                    IkGui.end();
                    IkGui.popStyleVar();
                });
        final var g = ctx.context;
        ctx.setRef("Test Window");
        final String[][] sets = {
            {"table/2-SMALL", "table/2-LARGE"}, {"table/3-LARGE", "table/3-SMALL"}
        };
        for (String[] set : sets) {
            final String itemL = set[0];
            final String itemR = set[1];
            ctx.itemClick(itemL);
            assertEquals(ctx.getID(itemL), g.navID, itemL);
            ctx.keyPress(Key.ARROW_RIGHT);
            assertEquals(ctx.getID(itemR), g.navID, itemL);
            // No-op
            ctx.keyPress(Key.ARROW_RIGHT);
            assertEquals(ctx.getID(itemR), g.navID, itemL);
            ctx.keyPress(Key.ARROW_LEFT);
            assertEquals(ctx.getID(itemL), g.navID, itemL);
            // No-op
            ctx.keyPress(Key.ARROW_LEFT);
            assertEquals(ctx.getID(itemL), g.navID, itemL);
        }
    }

    /** nav_from_clipped_item, gamepad variant: navigation resumes from the next visible item. */
    @Test
    void testFromClippedItemGamepad() {
        final Vector2f windowSize = new Vector2f();
        ctx.setGui(
                () -> {
                    IkGui.setNextWindowSize(windowSize.x, windowSize.y, Condition.ALWAYS);
                    IkGui.begin(
                            "Test Window",
                            null,
                            WindowFlags.NO_SAVED_SETTINGS
                                    | WindowFlags.ALWAYS_VERTICAL_SCROLLBAR
                                    | WindowFlags.ALWAYS_HORIZONTAL_SCROLLBAR);
                    final float scrollbarSize = ctx.context.style.variable.scrollbarSize;
                    for (int y = 0; y < 6; ++y) {
                        for (int x = 0; x < 6; ++x) {
                            IkGui.button("Button " + x + "," + y);
                            if (x < 5) {
                                IkGui.sameLine();
                            }
                            if (windowSize.y == 0.0f && y == 3 && x == 2) {
                                windowSize.set(
                                        IkGui.getCursorPosX() + scrollbarSize,
                                        IkGui.getCursorPosY() + scrollbarSize);
                            }
                        }
                    }
                    IkGui.end();
                });
        ctx.yieldFrames(2);
        ctx.setRef("Test Window");
        final Window window = ctx.getWindowByRef("");
        final RectFloat focusRect = new RectFloat();
        final Runnable checkFocusVisible =
                () -> {
                    IkGuiImplNav.windowRectRelToAbs(
                            window, window.navRectRelative[IkGuiImplNav.NAV_LAYER_MAIN], focusRect);
                    assertTrue(window.rectInner.contains(focusRect));
                };
        ctx.setInputMode(GuiInputSource.GAMEPAD);

        // Down, starting from the first visible item
        ctx.navMoveTo("Button 0,0");
        ctx.scrollToX("", 0.0f);
        ctx.scrollToBottom("");
        ctx.keyPress(Key.GAMEPAD_DPAD_DOWN);
        assertEquals(ctx.getID("Button 0,3"), ctx.context.navID);
        ctx.keyPress(Key.ARROW_UP);
        checkFocusVisible.run();

        // Up
        ctx.navMoveTo("Button 0,4");
        ctx.scrollToX("", 0);
        ctx.scrollToTop("");
        ctx.keyPress(Key.GAMEPAD_DPAD_UP);
        assertEquals(ctx.getID("Button 0,2"), ctx.context.navID);
        ctx.keyPress(Key.ARROW_DOWN);
        checkFocusVisible.run();

        // Right
        ctx.navMoveTo("Button 0,0");
        ctx.scrollToX("", window.scrollMax.x);
        ctx.scrollToTop("");
        ctx.keyPress(Key.GAMEPAD_DPAD_RIGHT);
        assertEquals(ctx.getID("Button 3,0"), ctx.context.navID);
        ctx.keyPress(Key.ARROW_LEFT);
        checkFocusVisible.run();

        // Left
        ctx.navMoveTo("Button 4,0");
        ctx.scrollToX("", 0);
        ctx.scrollToTop("");
        ctx.keyPress(Key.GAMEPAD_DPAD_LEFT);
        assertEquals(ctx.getID("Button 2,0"), ctx.context.navID);
        ctx.keyPress(Key.GAMEPAD_DPAD_RIGHT);
        checkFocusVisible.run();
    }
}
