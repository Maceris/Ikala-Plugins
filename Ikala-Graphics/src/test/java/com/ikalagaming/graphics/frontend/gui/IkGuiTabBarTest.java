package com.ikalagaming.graphics.frontend.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.IkBoolean;
import com.ikalagaming.graphics.frontend.gui.data.IkInt;
import com.ikalagaming.graphics.frontend.gui.data.TabBar;
import com.ikalagaming.graphics.frontend.gui.data.TabItem;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.flags.TabBarFlags;
import com.ikalagaming.graphics.frontend.gui.flags.TabItemFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Tests for tab bars and combo boxes. These run headless, without fonts loaded. */
class IkGuiTabBarTest {

    /** How much precision we expect in float comparisons. */
    private static final float DELTA = 0.001f;

    private Context context;

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        // Don't read or write an .ini file
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        context.io.mouseInsideWindow = true;
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

    private void click(MouseButton button, float x, float y, Runnable ui) {
        moveMouse(x, y);
        frame(ui);
        context.io.addMouseButtonEvent(button, true);
        frame(ui);
        context.io.addMouseButtonEvent(button, false);
        frame(ui);
    }

    private static Runnable fixedWindow(float width, Runnable body) {
        return () -> {
            IkGui.setNextWindowPos(100, 100, Condition.FIRST_USE_EVER);
            IkGui.setNextWindowSize(width, 300, Condition.FIRST_USE_EVER);
            IkGui.begin("Host", null, WindowFlags.NONE);
            body.run();
            IkGui.end();
        };
    }

    /**
     * Fetch the only tab bar that exists.
     *
     * @return The tab bar.
     */
    private TabBar onlyTabBar() {
        assertEquals(1, context.tabBars.size());
        return context.tabBars.values().iterator().next();
    }

    /**
     * Find a tab by its label.
     *
     * @param tabBar The tab bar.
     * @param label The label.
     * @return The tab.
     */
    private static TabItem tab(TabBar tabBar, String label) {
        for (TabItem tab : tabBar.tabs) {
            if (label.equals(tab.name)) {
                return tab;
            }
        }
        throw new AssertionError("No tab named " + label);
    }

    /**
     * Click in the middle of a tab.
     *
     * @param tabBar The tab bar.
     * @param label The tab label.
     * @param ui The UI for each frame.
     */
    private void clickTab(TabBar tabBar, String label, Runnable ui) {
        final TabItem tab = tab(tabBar, label);
        final Vector2f position = IkGuiImplTabs.tabBarGetTabPos(tabBar, tab, new Vector2f());
        click(MouseButton.LEFT, position.x + 3, position.y + 3, ui);
    }

    /** Which tab contents were visible during the last frame. */
    private final List<String> visibleContents = new ArrayList<>();

    private void tabItem(String label, IkBoolean open, int flags) {
        if (IkGui.beginTabItem(label, open, flags)) {
            visibleContents.add(label);
            IkGui.text("Contents of " + label);
            IkGui.endTabItem();
        }
    }

    private Runnable threeTabs(int tabBarFlags) {
        return fixedWindow(
                400,
                () -> {
                    visibleContents.clear();
                    if (IkGui.beginTabBar("tabs", tabBarFlags)) {
                        tabItem("Avocado", null, TabItemFlags.NONE);
                        tabItem("Broccoli", null, TabItemFlags.NONE);
                        tabItem("Cucumber", null, TabItemFlags.NONE);
                        IkGui.endTabBar();
                    }
                });
    }

    @Test
    void testFirstTabSelectedAndClickingSelects() {
        Runnable ui = threeTabs(TabBarFlags.NONE);
        frame(ui);
        // On the very first frame, only the first tab is known and it shows its contents
        assertEquals(List.of("Avocado"), visibleContents);
        frames(2, ui);
        assertEquals(List.of("Avocado"), visibleContents);

        final TabBar tabBar = onlyTabBar();
        assertEquals(3, tabBar.tabs.size());
        assertEquals(tab(tabBar, "Avocado").id, tabBar.selectedTabID);

        // Tabs are laid out left to right without overlapping
        final TabItem a = tab(tabBar, "Avocado");
        final TabItem b = tab(tabBar, "Broccoli");
        final TabItem c = tab(tabBar, "Cucumber");
        assertEquals(0, a.offset, DELTA);
        assertTrue(b.offset >= a.offset + a.width);
        assertTrue(c.offset >= b.offset + b.width);

        clickTab(tabBar, "Cucumber", ui);
        frame(ui);
        assertEquals(List.of("Cucumber"), visibleContents);
        assertEquals(c.id, tabBar.selectedTabID);
    }

    @Test
    void testContentsLaidOutBelowTabBar() {
        final Vector2f textMin = new Vector2f();
        final float[] barBottom = {0};
        Runnable ui =
                fixedWindow(
                        400,
                        () -> {
                            if (IkGui.beginTabBar("tabs")) {
                                if (IkGui.beginTabItem("Only")) {
                                    IkGui.text("Hello");
                                    IkGui.getItemRectMin(textMin);
                                    IkGui.endTabItem();
                                }
                                barBottom[0] = context.currentTabBar.barRect.getBottom();
                                IkGui.endTabBar();
                            }
                        });
        frames(3, ui);
        assertTrue(textMin.y >= barBottom[0]);
    }

    @Test
    void testCloseButtonClosesTab() {
        final IkBoolean openB = new IkBoolean(true);
        Runnable ui =
                fixedWindow(
                        400,
                        () -> {
                            visibleContents.clear();
                            if (IkGui.beginTabBar("tabs")) {
                                tabItem("Avocado", null, TabItemFlags.NONE);
                                tabItem("Broccoli", openB, TabItemFlags.NONE);
                                IkGui.endTabBar();
                            }
                        });
        frames(3, ui);
        final TabBar tabBar = onlyTabBar();
        clickTab(tabBar, "Broccoli", ui);
        frame(ui);
        assertEquals(List.of("Broccoli"), visibleContents);

        // The close button is always visible on the selected tab, on the right edge
        final TabItem b = tab(tabBar, "Broccoli");
        final Vector2f position = IkGuiImplTabs.tabBarGetTabPos(tabBar, b, new Vector2f());
        final float fontSize = IkGui.getFontSize();
        final float buttonX =
                position.x + b.width - context.style.variable.framePadding.x - fontSize;
        final float buttonY = position.y + context.style.variable.framePadding.y;
        click(MouseButton.LEFT, buttonX + fontSize * 0.5f, buttonY + fontSize * 0.5f, ui);
        assertFalse(openB.get());

        frames(2, ui);
        assertEquals(1, tabBar.tabs.size());
        assertEquals(List.of("Avocado"), visibleContents);
    }

    @Test
    void testSetSelectedAndSetTabItemClosed() {
        final boolean[] selectC = {false};
        final boolean[] closeA = {false};
        Runnable ui =
                fixedWindow(
                        400,
                        () -> {
                            visibleContents.clear();
                            if (IkGui.beginTabBar("tabs")) {
                                if (closeA[0]) {
                                    IkGui.setTabItemClosed("Avocado");
                                }
                                tabItem("Avocado", null, TabItemFlags.NONE);
                                tabItem("Broccoli", null, TabItemFlags.NONE);
                                tabItem(
                                        "Cucumber",
                                        null,
                                        selectC[0] ? TabItemFlags.SET_SELECTED : TabItemFlags.NONE);
                                IkGui.endTabBar();
                            }
                        });
        frames(3, ui);
        selectC[0] = true;
        frame(ui);
        selectC[0] = false;
        frame(ui);
        assertEquals(List.of("Cucumber"), visibleContents);

        closeA[0] = true;
        frame(ui);
        // The tab is removed during layout, and is re-added as a new tab since we still submit it
        final TabBar tabBar = onlyTabBar();
        assertEquals("Broccoli", tabBar.tabs.getFirst().name);
    }

    @Test
    void testAutoSelectNewTabs() {
        final boolean[] addTab = {false};
        Runnable ui =
                fixedWindow(
                        400,
                        () -> {
                            visibleContents.clear();
                            if (IkGui.beginTabBar("tabs", TabBarFlags.AUTO_SELECT_NEW_TABS)) {
                                tabItem("Avocado", null, TabItemFlags.NONE);
                                if (addTab[0]) {
                                    tabItem("New", null, TabItemFlags.NONE);
                                }
                                IkGui.endTabBar();
                            }
                        });
        frames(3, ui);
        assertEquals(List.of("Avocado"), visibleContents);
        addTab[0] = true;
        frames(3, ui);
        assertEquals(List.of("New"), visibleContents);
    }

    @Test
    void testTabItemButton() {
        final int[] clicks = {0};
        Runnable ui =
                fixedWindow(
                        400,
                        () -> {
                            visibleContents.clear();
                            if (IkGui.beginTabBar("tabs")) {
                                tabItem("Avocado", null, TabItemFlags.NONE);
                                if (IkGui.tabItemButton("+", TabItemFlags.TRAILING)) {
                                    clicks[0]++;
                                }
                                IkGui.endTabBar();
                            }
                        });
        frames(3, ui);
        final TabBar tabBar = onlyTabBar();
        final TabItem button = tabBar.tabs.getLast();
        assertEquals("+", button.name);
        // Trailing tabs are placed after the central tabs, and stay within the bar
        final TabItem avocado = tab(tabBar, "Avocado");
        assertTrue(button.offset >= avocado.offset + avocado.width);
        assertTrue(button.offset + button.width <= tabBar.barRect.getWidth());

        clickTab(tabBar, "+", ui);
        assertEquals(1, clicks[0]);
        // Buttons are never selected
        frame(ui);
        assertEquals(List.of("Avocado"), visibleContents);
    }

    @Test
    void testShrinkFittingPolicy() {
        Runnable ui =
                fixedWindow(
                        150,
                        () -> {
                            if (IkGui.beginTabBar("tabs", TabBarFlags.FITTING_POLICY_SHRINK)) {
                                for (int i = 0; i < 6; ++i) {
                                    tabItem("A long tab name " + i, null, TabItemFlags.NONE);
                                }
                                IkGui.endTabBar();
                            }
                        });
        frames(4, ui);
        final TabBar tabBar = onlyTabBar();
        assertFalse(tabBar.scrollButtonEnabled);
        // All tabs fit within the bar
        final TabItem last = tabBar.tabs.getLast();
        assertTrue(last.offset + last.width <= tabBar.barRect.getWidth() + DELTA);
        assertTrue(tabBar.widthAllTabsIdeal > tabBar.barRect.getWidth());
        for (TabItem tab : tabBar.tabs) {
            assertTrue(tab.width < tab.contentWidth);
        }
    }

    @Test
    void testScrollFittingPolicyShowsButtons() {
        Runnable ui =
                fixedWindow(
                        150,
                        () -> {
                            if (IkGui.beginTabBar("tabs", TabBarFlags.FITTING_POLICY_SCROLL)) {
                                for (int i = 0; i < 6; ++i) {
                                    tabItem("A long tab name " + i, null, TabItemFlags.NONE);
                                }
                                IkGui.endTabBar();
                            }
                        });
        frames(4, ui);
        final TabBar tabBar = onlyTabBar();
        assertTrue(tabBar.scrollButtonEnabled);
        // Tabs are not shrunk
        for (TabItem tab : tabBar.tabs) {
            assertEquals(tab.contentWidth, tab.width, DELTA);
        }
        // The scroll buttons take space from the bar
        assertTrue(tabBar.barRect.getRight() < tabBar.separatorMaxX);

        // Select the last tab programmatically, which scrolls it into view
        final TabItem last = tabBar.tabs.getLast();
        tabBar.nextSelectedTabID = last.id;
        frames(2, ui);
        assertTrue(tabBar.scrollingTarget > 0);
    }

    @Test
    void testReorderByDragging() {
        Runnable ui = threeTabs(TabBarFlags.REORDERABLE);
        frames(3, ui);
        final TabBar tabBar = onlyTabBar();
        final TabItem a = tab(tabBar, "Avocado");
        final TabItem c = tab(tabBar, "Cucumber");
        final Vector2f start = IkGuiImplTabs.tabBarGetTabPos(tabBar, a, new Vector2f());
        final float targetX = tabBar.barRect.getLeft() + c.offset + c.width - 2;

        moveMouse(start.x + 3, start.y + 3);
        frame(ui);
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame(ui);
        for (float x = start.x + 13; x < targetX; x += 10) {
            moveMouse(x, start.y + 3);
            frame(ui);
        }
        moveMouse(targetX, start.y + 3);
        frames(2, ui);
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frames(2, ui);

        assertEquals("Avocado", tabBar.tabs.getLast().name);
    }

    @Test
    void testTabListPopupButton() {
        Runnable ui = threeTabs(TabBarFlags.TAB_LIST_POPUP_BUTTON);
        frames(3, ui);
        final TabBar tabBar = onlyTabBar();
        // The button takes space on the left of the bar
        assertTrue(tabBar.barRect.getLeft() > tabBar.separatorMinX + 5);

        final float buttonX = tabBar.barRect.getLeft() - 5;
        final float buttonY = tabBar.barRect.getTop() + 3;
        click(MouseButton.LEFT, buttonX, buttonY, ui);
        frames(2, ui);
        assertEquals(1, context.openPopupStack.size());
        assertNotNull(context.openPopupStack.getFirst().window);
    }

    @Test
    void testAppendingToTabBar() {
        Runnable ui =
                fixedWindow(
                        400,
                        () -> {
                            visibleContents.clear();
                            if (IkGui.beginTabBar("tabs")) {
                                tabItem("Avocado", null, TabItemFlags.NONE);
                                IkGui.endTabBar();
                            }
                            IkGui.text("Between");
                            if (IkGui.beginTabBar("tabs")) {
                                tabItem("Broccoli", null, TabItemFlags.NONE);
                                IkGui.endTabBar();
                            }
                        });
        frames(3, ui);
        final TabBar tabBar = onlyTabBar();
        assertEquals(2, tabBar.tabs.size());
        assertEquals(2, tabBar.beginCount);
    }

    @Test
    void testComboSelectsItem() {
        final IkInt current = new IkInt(0);
        final boolean[] changed = {false};
        final Vector2f comboMin = new Vector2f();
        final String[] items = {"Apple", "Banana", "Cherry"};
        Runnable ui =
                fixedWindow(
                        400,
                        () -> {
                            if (IkGui.combo("Fruit", current, items)) {
                                changed[0] = true;
                            }
                            IkGui.getItemRectMin(comboMin);
                        });
        frames(3, ui);
        click(MouseButton.LEFT, comboMin.x + 5, comboMin.y + 5, ui);
        frames(2, ui);
        assertEquals(1, context.openPopupStack.size());
        final var popup = context.openPopupStack.getFirst().window;
        assertNotNull(popup);
        // The popup opens below the combo
        assertTrue(popup.position.y >= comboMin.y + IkGui.getFrameHeight() - 1);

        // Click the third item
        final float itemHeight = IkGui.getTextLineHeight() + context.style.variable.itemSpacing.y;
        final float itemY = popup.position.y + popup.padding.y + itemHeight * 2 + 2;
        click(MouseButton.LEFT, popup.position.x + popup.padding.x + 4, itemY, ui);
        assertTrue(changed[0]);
        assertEquals(2, current.get());
        frame(ui);
        assertTrue(context.openPopupStack.isEmpty());
    }
}
