package com.ikalagaming.graphics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.IkBoolean;
import com.ikalagaming.graphics.gui.data.Window;
import com.ikalagaming.graphics.gui.enums.Condition;
import com.ikalagaming.graphics.gui.enums.MouseButton;
import com.ikalagaming.graphics.gui.flags.PopupFlags;
import com.ikalagaming.graphics.gui.flags.WindowFlags;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for popups, modals, menus and tooltips. These run headless, without fonts loaded. */
class IkGuiPopupTest {

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

    /**
     * Click a mouse button at a position, running frames for the move, press and release.
     *
     * @param button The button to click.
     * @param x The x position.
     * @param y The y position.
     * @param ui The UI for each frame.
     */
    private void click(MouseButton button, float x, float y, Runnable ui) {
        moveMouse(x, y);
        frame(ui);
        context.io.addMouseButtonEvent(button, true);
        frame(ui);
        context.io.addMouseButtonEvent(button, false);
        frame(ui);
    }

    private static Runnable fixedWindow(String name, int flags, Runnable body) {
        return () -> {
            IkGui.setNextWindowPos(100, 100, Condition.FIRST_USE_EVER);
            IkGui.setNextWindowSize(300, 200, Condition.FIRST_USE_EVER);
            IkGui.begin(name, null, flags);
            body.run();
            IkGui.end();
        };
    }

    /**
     * Find the popup window that is open at a level of the popup stack.
     *
     * @param level The level in the open popup stack.
     * @return The popup window.
     */
    private Window openPopupWindow(int level) {
        assertTrue(context.openPopupStack.size() > level);
        final Window window = context.openPopupStack.get(level).window;
        assertNotNull(window);
        return window;
    }

    @Test
    void testOpenPopupAndCloseByClickingOutside() {
        final boolean[] shouldOpen = {false};
        final boolean[] visible = {false};
        Runnable ui =
                fixedWindow(
                        "Host",
                        WindowFlags.NONE,
                        () -> {
                            if (shouldOpen[0]) {
                                assertTrue(IkGui.openPopup("my popup"));
                                shouldOpen[0] = false;
                            }
                            visible[0] = IkGui.beginPopup("my popup");
                            if (visible[0]) {
                                IkGui.text("Inside the popup");
                                IkGui.endPopup();
                            }
                            assertTrue(IkGui.isPopupOpen("my popup") == visible[0]);
                        });

        moveMouse(150, 150);
        frames(2, ui);
        assertFalse(visible[0]);
        assertTrue(context.openPopupStack.isEmpty());

        shouldOpen[0] = true;
        frames(3, ui);
        assertTrue(visible[0]);
        assertEquals(1, context.openPopupStack.size());
        final Window popup = openPopupWindow(0);
        assertTrue(IkGuiInternal.isWindowActiveAndVisible(popup));
        // Popups open at the mouse position
        // Popups open 1 pixel to the right of the mouse, so clicking again can reopen them
        assertEquals(151, popup.position.x, DELTA);
        assertEquals(150, popup.position.y, DELTA);
        assertTrue(context.mainViewport.drawData.drawLists.contains(popup.drawList));
        assertTrue(context.beginPopupStack.isEmpty());

        // Clicking somewhere else closes it
        click(MouseButton.LEFT, 800, 600, ui);
        frame(ui);
        assertFalse(visible[0]);
        assertTrue(context.openPopupStack.isEmpty());
    }

    @Test
    void testSelectableClosesPopup() {
        final boolean[] selected = {false};
        final Vector2f itemMin = new Vector2f();
        Runnable ui =
                fixedWindow(
                        "Host",
                        WindowFlags.NONE,
                        () -> {
                            if (IkGui.beginPopup("choices")) {
                                if (IkGui.selectable("Option A")) {
                                    selected[0] = true;
                                }
                                IkGui.getItemRectMin(itemMin);
                                IkGui.endPopup();
                            }
                        });
        frame(
                () -> {
                    ui.run();
                    IkGui.begin("Host");
                    IkGui.openPopup("choices");
                    IkGui.end();
                });
        frames(3, ui);
        assertEquals(1, context.openPopupStack.size());

        click(MouseButton.LEFT, itemMin.x + 5, itemMin.y + 5, ui);
        assertTrue(selected[0]);
        frame(ui);
        assertTrue(context.openPopupStack.isEmpty());
    }

    @Test
    void testOpenPopupEveryFrameDoesNotReopen() {
        final int[] openedCount = {0};
        Runnable ui =
                fixedWindow(
                        "Host",
                        WindowFlags.NONE,
                        () -> {
                            if (IkGui.openPopup("spam")) {
                                openedCount[0]++;
                            }
                            if (IkGui.beginPopup("spam")) {
                                IkGui.text("Hi");
                                IkGui.endPopup();
                            }
                        });
        frames(5, ui);
        assertEquals(1, openedCount[0]);
        assertEquals(1, context.openPopupStack.size());
        assertTrue(IkGuiInternal.isWindowActiveAndVisible(openPopupWindow(0)));
    }

    @Test
    void testContextMenuOpensOnRightClick() {
        final Vector2f buttonMin = new Vector2f();
        final boolean[] menuVisible = {false};
        Runnable ui =
                fixedWindow(
                        "Host",
                        WindowFlags.NONE,
                        () -> {
                            IkGui.button("Right click me");
                            IkGui.getItemRectMin(buttonMin);
                            menuVisible[0] = IkGui.beginPopupContextItem();
                            if (menuVisible[0]) {
                                IkGui.text("Context");
                                IkGui.endPopup();
                            }
                        });
        frames(2, ui);
        assertFalse(menuVisible[0]);

        click(MouseButton.RIGHT, buttonMin.x + 3, buttonMin.y + 3, ui);
        frames(2, ui);
        assertTrue(menuVisible[0]);
        assertEquals(1, context.openPopupStack.size());

        // Right-clicking in the void closes it again
        click(MouseButton.RIGHT, 1000, 650, ui);
        frame(ui);
        assertFalse(menuVisible[0]);
    }

    @Test
    void testContextWindowAndVoid() {
        final boolean[] windowMenu = {false};
        final boolean[] voidMenu = {false};
        Runnable ui =
                fixedWindow(
                        "Host",
                        WindowFlags.NONE,
                        () -> {
                            windowMenu[0] = IkGui.beginPopupContextWindow();
                            if (windowMenu[0]) {
                                IkGui.text("Window menu");
                                IkGui.endPopup();
                            }
                            voidMenu[0] = IkGui.beginPopupContextVoid();
                            if (voidMenu[0]) {
                                IkGui.text("Void menu");
                                IkGui.endPopup();
                            }
                        });
        frames(2, ui);
        click(MouseButton.RIGHT, 250, 250, ui);
        frames(2, ui);
        assertTrue(windowMenu[0]);
        assertFalse(voidMenu[0]);

        click(MouseButton.RIGHT, 1000, 650, ui);
        frames(2, ui);
        assertFalse(windowMenu[0]);
        assertTrue(voidMenu[0]);
    }

    @Test
    void testModalBlocksWindowsBehindAndCloses() {
        final boolean[] modalVisible = {false};
        final boolean[] buttonHovered = {false};
        final boolean[] closeNow = {false};
        final IkBoolean modalOpen = new IkBoolean(true);
        Runnable ui =
                fixedWindow(
                        "Host",
                        WindowFlags.NONE,
                        () -> {
                            IkGui.button("Behind");
                            buttonHovered[0] = IkGui.isItemHovered();
                            modalVisible[0] = IkGui.beginPopupModal("Modal", modalOpen);
                            if (modalVisible[0]) {
                                IkGui.text("Are you sure?");
                                if (closeNow[0]) {
                                    IkGui.closeCurrentPopup();
                                }
                                IkGui.endPopup();
                            }
                        });

        moveMouse(115, 135);
        frames(2, ui);
        assertTrue(buttonHovered[0]);
        // Not opened yet, so the open flag gets reset
        assertFalse(modalOpen.get());
        modalOpen.set(true);

        frame(
                () -> {
                    ui.run();
                    IkGui.begin("Host");
                    IkGui.openPopup("Modal");
                    IkGui.end();
                });
        // The modal was not open yet during that frame, which reset the flag
        modalOpen.set(true);
        frames(3, ui);
        assertTrue(modalVisible[0]);
        assertFalse(buttonHovered[0]);
        final Window modal = openPopupWindow(0);
        assertSame(modal, IkGuiInternal.getTopmostPopupModal());

        // Modals are centered in the viewport
        assertEquals(640, modal.position.x + modal.size.x * 0.5f, 1.0f);
        assertEquals(360, modal.position.y + modal.size.y * 0.5f, 1.0f);

        // The dimmed background is drawn right before the modal. The dimming fades in over time,
        // and these frames run too fast for it to have started, so force it on.
        context.dimBackgroundRatio = 1.0f;
        frame(ui);
        final var drawLists = context.mainViewport.drawData.drawLists;
        final int modalIndex = drawLists.indexOf(modal.drawList);
        assertTrue(modalIndex > 0);
        assertSame(context.dimBackgroundDrawList, drawLists.get(modalIndex - 1));

        // Clicking outside a modal does not close it
        click(MouseButton.LEFT, 1200, 700, ui);
        frame(ui);
        assertTrue(modalVisible[0]);

        closeNow[0] = true;
        frames(2, ui);
        assertFalse(modalVisible[0]);
        assertTrue(context.openPopupStack.isEmpty());
        assertFalse(modalOpen.get());
    }

    @Test
    void testModalClosedThroughOpenFlag() {
        final IkBoolean modalOpen = new IkBoolean(true);
        final boolean[] visible = {false};
        Runnable ui =
                fixedWindow(
                        "Host",
                        WindowFlags.NONE,
                        () -> {
                            visible[0] = IkGui.beginPopupModal("Closable", modalOpen);
                            if (visible[0]) {
                                IkGui.text("Close me");
                                IkGui.endPopup();
                            }
                        });
        frame(
                () -> {
                    IkGui.begin("Host");
                    IkGui.openPopup("Closable");
                    IkGui.end();
                    ui.run();
                });
        frames(2, ui);
        assertTrue(visible[0]);

        modalOpen.set(false);
        frame(ui);
        assertFalse(visible[0]);
        assertTrue(context.openPopupStack.isEmpty());
    }

    @Test
    void testIsPopupOpenFlags() {
        final boolean[] results = new boolean[4];
        Runnable ui =
                fixedWindow(
                        "Host",
                        WindowFlags.NONE,
                        () -> {
                            results[0] = IkGui.isPopupOpen(0, PopupFlags.ANY_POPUP);
                            if (IkGui.beginPopup("outer")) {
                                // Inside the outer popup, the next level is empty
                                results[1] = IkGui.isPopupOpen(0, PopupFlags.ANY_POPUP_ID);
                                results[2] = IkGui.isPopupOpen(0, PopupFlags.ANY_POPUP);
                                IkGui.endPopup();
                            }
                            results[3] = IkGui.isPopupOpen("outer");
                        });
        frame(
                () -> {
                    IkGui.begin("Host");
                    IkGui.openPopup("outer");
                    IkGui.end();
                });
        frames(2, ui);
        assertTrue(results[0]);
        assertFalse(results[1]);
        assertTrue(results[2]);
        assertTrue(results[3]);
    }

    @Test
    void testMenuBarMenuOpensAndItemActivates() {
        final Vector2f fileMin = new Vector2f();
        final Vector2f itemMin = new Vector2f();
        final boolean[] menuOpen = {false};
        final boolean[] activated = {false};
        final IkBoolean toggle = new IkBoolean(false);
        Runnable ui =
                fixedWindow(
                        "Menus",
                        WindowFlags.MENU_BAR,
                        () -> {
                            if (IkGui.beginMenuBar()) {
                                menuOpen[0] = IkGui.beginMenu("File");
                                IkGui.getItemRectMin(fileMin);
                                if (menuOpen[0]) {
                                    if (IkGui.menuItem("Open", "Ctrl+O")) {
                                        activated[0] = true;
                                    }
                                    IkGui.getItemRectMin(itemMin);
                                    IkGui.menuItem("Toggle", null, toggle);
                                    IkGui.separator();
                                    IkGui.menuItem("Disabled", null, false, false);
                                    IkGui.endMenu();
                                }
                                IkGui.endMenuBar();
                            }
                            IkGui.text("Body");
                        });
        frames(2, ui);
        assertFalse(menuOpen[0]);
        final Window host = IkGuiInternal.findWindowByName("Menus");
        // The menu is laid out within the menu bar
        assertTrue(fileMin.y >= host.position.y + host.titleBarHeight - 1);
        assertTrue(fileMin.y < host.position.y + host.titleBarHeight + host.menuBarHeight);

        click(MouseButton.LEFT, fileMin.x + 3, fileMin.y + 3, ui);
        frames(2, ui);
        assertTrue(menuOpen[0]);
        final Window menuWindow = openPopupWindow(0);
        assertTrue((menuWindow.flags & WindowFlags.INTERNAL_CHILD_MENU) != 0);
        // The menu is placed below the menu bar
        assertTrue(menuWindow.position.y >= host.position.y + host.titleBarHeight);
        assertTrue(IkGuiInternal.isWindowActiveAndVisible(menuWindow));

        click(MouseButton.LEFT, itemMin.x + 3, itemMin.y + 3, ui);
        assertTrue(activated[0]);
        frame(ui);
        assertFalse(menuOpen[0]);
        assertTrue(context.openPopupStack.isEmpty());
    }

    @Test
    void testSubMenuOpensOnHover() {
        final Vector2f subMin = new Vector2f();
        final boolean[] subOpen = {false};
        Runnable ui =
                fixedWindow(
                        "Host",
                        WindowFlags.NONE,
                        () -> {
                            if (IkGui.beginPopup("root menu")) {
                                IkGui.menuItem("First");
                                if (IkGui.beginMenu("More")) {
                                    subOpen[0] = true;
                                    IkGui.menuItem("Nested");
                                    IkGui.endMenu();
                                } else {
                                    subOpen[0] = false;
                                }
                                IkGui.getItemRectMin(subMin);
                                IkGui.endPopup();
                            }
                        });
        moveMouse(200, 150);
        frame(
                () -> {
                    IkGui.begin("Host");
                    IkGui.openPopup("root menu");
                    IkGui.end();
                });
        frames(3, ui);
        assertEquals(1, context.openPopupStack.size());
        assertFalse(subOpen[0]);

        moveMouse(subMin.x + 4, subMin.y + 4);
        frames(4, ui);
        assertTrue(subOpen[0]);
        assertEquals(2, context.openPopupStack.size());
        final Window root = openPopupWindow(0);
        final Window sub = openPopupWindow(1);
        assertSame(root, sub.parentWindow);
        // Sub-menus appear to the right of their parent
        assertTrue(sub.position.x >= root.position.x + root.size.x * 0.5f);
    }

    @Test
    void testMainMenuBarClaimsWorkArea() {
        final boolean[] visible = {false};
        Runnable ui =
                () -> {
                    visible[0] = IkGui.beginMainMenuBar();
                    if (visible[0]) {
                        if (IkGui.beginMenu("File")) {
                            IkGui.endMenu();
                        }
                        IkGui.endMainMenuBar();
                    }
                };
        frames(3, ui);
        assertTrue(visible[0]);
        final Window bar = IkGuiInternal.findWindowByName("##MainMenuBar");
        assertNotNull(bar);
        assertEquals(0, bar.position.x, DELTA);
        assertEquals(0, bar.position.y, DELTA);
        assertEquals(1280, bar.size.x, DELTA);
        assertEquals(IkGui.getFrameHeight(), bar.size.y, DELTA);
        assertEquals(IkGui.getFrameHeight(), context.mainViewport.workPosition.y, DELTA);
        assertEquals(720 - IkGui.getFrameHeight(), context.mainViewport.workSize.y, DELTA);
    }

    @Test
    void testSetTooltipFollowsMouse() {
        final Vector2f buttonMin = new Vector2f();
        Runnable ui =
                fixedWindow(
                        "Host",
                        WindowFlags.NONE,
                        () -> {
                            IkGui.button("Hover me");
                            IkGui.getItemRectMin(buttonMin);
                            if (IkGui.isItemHovered()) {
                                IkGui.setTooltip("Helpful text");
                            }
                        });
        frames(2, ui);
        moveMouse(buttonMin.x + 3, buttonMin.y + 3);
        frames(3, ui);

        final Window tooltip = IkGuiInternal.findWindowByName("##Tooltip_00");
        assertNotNull(tooltip);
        assertTrue(tooltip.active);
        assertTrue((tooltip.flags & WindowFlags.INTERNAL_TOOLTIP) != 0);
        // Tooltips are placed past the mouse cursor so they don't cover it
        assertTrue(tooltip.position.x > buttonMin.x + 3);
        assertTrue(tooltip.position.y > buttonMin.y + 3);
        // And drawn on top of everything else (the foreground list is only added when used)
        final var drawLists = context.mainViewport.drawData.drawLists;
        assertSame(tooltip.drawList, drawLists.getLast());

        // Moving away hides it
        moveMouse(1000, 600);
        frames(2, ui);
        assertFalse(tooltip.active);
    }

    @Test
    void testSetTooltipOverridesPrevious() {
        Runnable ui =
                fixedWindow(
                        "Host",
                        WindowFlags.NONE,
                        () -> {
                            IkGui.setTooltip("First");
                            IkGui.setTooltip("Second");
                        });
        frames(3, ui);
        final Window first = IkGuiInternal.findWindowByName("##Tooltip_00");
        final Window second = IkGuiInternal.findWindowByName("##Tooltip_01");
        assertNotNull(first);
        assertNotNull(second);
        assertTrue(first.hidden);
        assertFalse(second.hidden);
    }

    @Test
    void testItemTooltipOverDisabledItemIsNotDisabled() {
        final float[] alphaInTooltip = {0};
        final boolean[] shown = {false};
        final Vector2f buttonMin = new Vector2f();
        context.style.variable.hoverDelayShort = 0;
        context.style.variable.hoverStationaryDelay = 0;
        Runnable ui =
                fixedWindow(
                        "Host",
                        WindowFlags.NONE,
                        () -> {
                            IkGui.beginDisabled();
                            IkGui.button("Disabled");
                            IkGui.getItemRectMin(buttonMin);
                            shown[0] = IkGui.beginItemTooltip();
                            if (shown[0]) {
                                alphaInTooltip[0] = context.style.variable.alpha;
                                IkGui.text("Why it is disabled");
                                IkGui.endTooltip();
                            }
                            IkGui.endDisabled();
                        });
        frames(2, ui);
        moveMouse(buttonMin.x + 3, buttonMin.y + 3);
        frames(4, ui);
        assertTrue(shown[0]);
        assertEquals(1.0f, alphaInTooltip[0], DELTA);
        assertEquals(1.0f, context.style.variable.alpha, DELTA);
    }

    @Test
    void testPopupClosedWhenFocusingAnotherWindow() {
        final boolean[] visible = {false};
        Runnable ui =
                () -> {
                    fixedWindow(
                                    "Host",
                                    WindowFlags.NONE,
                                    () -> {
                                        visible[0] = IkGui.beginPopup("p");
                                        if (visible[0]) {
                                            IkGui.text("Popup");
                                            IkGui.endPopup();
                                        }
                                    })
                            .run();
                    IkGui.setNextWindowPos(700, 100, Condition.FIRST_USE_EVER);
                    IkGui.setNextWindowSize(200, 200, Condition.FIRST_USE_EVER);
                    IkGui.begin("Other");
                    IkGui.text("Other window");
                    IkGui.end();
                };
        moveMouse(150, 150);
        // Create both windows first, as newly appearing windows take focus and close popups
        frames(2, ui);
        frame(
                () -> {
                    ui.run();
                    IkGui.begin("Host");
                    IkGui.openPopup("p");
                    IkGui.end();
                });
        frames(3, ui);
        assertTrue(visible[0]);

        click(MouseButton.LEFT, 800, 250, ui);
        frame(ui);
        assertFalse(visible[0]);
        assertSame(IkGuiInternal.findWindowByName("Other"), context.navFocusedWindow);
    }
}
