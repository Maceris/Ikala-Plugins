package com.ikalagaming.graphics.frontend.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.IkInt;
import com.ikalagaming.graphics.frontend.gui.data.PlatformIO;
import com.ikalagaming.graphics.frontend.gui.data.Window;
import com.ikalagaming.graphics.frontend.gui.enums.ColorType;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.Key;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.enums.MouseCursor;
import com.ikalagaming.graphics.frontend.gui.flags.ConfigFlags;
import com.ikalagaming.graphics.frontend.gui.flags.ItemFlags;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Tests for text links, small helpers and the combo clipper. */
class IkGuiTextLinkTest {
    private Context context;

    /** The paths that were opened with the shell. */
    private final List<String> opened = new ArrayList<>();

    /** How many times the link was clicked. */
    private int clicks;

    /** The rectangle of the link, captured while submitting it. */
    private final Vector2f linkMin = new Vector2f();

    private final Vector2f linkMax = new Vector2f();

    /** The rectangle of the context menu item, captured while submitting it. */
    private final Vector2f menuItemMin = new Vector2f();

    private final Vector2f menuItemMax = new Vector2f();

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        // Don't read or write an .ini file
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        context.io.mouseInsideWindow = true;
        context.io.configInputTrickleEventQueue = false;
        context.io.configFlags |= ConfigFlags.NAV_ENABLE_KEYBOARD;
        // Never actually open anything during tests
        context.platformIO.openInShellFunction =
                path -> {
                    opened.add(path);
                    return true;
                };
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

    private static void beginHost() {
        IkGui.setNextWindowPos(100, 100, Condition.FIRST_USE_EVER);
        IkGui.setNextWindowSize(300, 300, Condition.FIRST_USE_EVER);
        IkGui.begin("Host", null, WindowFlags.NONE);
    }

    private void click(Vector2f min, Vector2f max, MouseButton button, Runnable ui) {
        context.io.addMousePosEvent((min.x + max.x) * 0.5f, (min.y + max.y) * 0.5f);
        frame(ui);
        context.io.addMouseButtonEvent(button, true);
        frame(ui);
        context.io.addMouseButtonEvent(button, false);
        frame(ui);
    }

    private void press(Key key, Runnable ui) {
        context.io.addKeyEvent(key, true);
        frame(ui);
        context.io.addKeyEvent(key, false);
        frame(ui);
    }

    /** A window with a link that opens a URL, and records its rectangles. */
    private Runnable linkUI(String label, String url) {
        return () -> {
            beginHost();
            if (IkGui.textLinkOpenURL(label, url)) {
                clicks++;
            }
            IkGui.getItemRectMin(linkMin);
            IkGui.getItemRectMax(linkMax);
            // The context menu of the link is the last opened popup
            if (!context.openPopupStack.isEmpty()) {
                final Window popup = context.openPopupStack.getLast().window;
                if (popup != null && popup.active) {
                    menuItemMin.set(popup.position).add(0, popup.size.y * 0.5f);
                    menuItemMax.set(popup.position).add(popup.size.x, popup.size.y * 0.5f);
                }
            }
            IkGui.end();
        };
    }

    @Test
    void testTextLinkClick() {
        final Runnable ui =
                () -> {
                    beginHost();
                    if (IkGui.textLink("A link")) {
                        clicks++;
                    }
                    IkGui.getItemRectMin(linkMin);
                    IkGui.getItemRectMax(linkMax);
                    IkGui.end();
                };
        frames(2, ui);
        // The link is the size of the text
        final Vector2f textSize = IkGui.calcTextSize("A link");
        assertEquals(textSize.x, linkMax.x - linkMin.x, 0.001f);
        assertEquals(textSize.y, linkMax.y - linkMin.y, 0.001f);

        click(linkMin, linkMax, MouseButton.LEFT, ui);
        assertEquals(1, clicks);
        // Links are not opened by the plain version
        assertTrue(opened.isEmpty());
    }

    @Test
    void testTextLinkHoverCursor() {
        final Runnable ui = linkUI("Link", "https://example.com");
        frames(2, ui);
        context.io.addMousePosEvent(linkMin.x + 1, linkMin.y + 1);
        frames(2, ui);
        assertEquals(MouseCursor.HAND, IkGui.getMouseCursor());

        context.io.addMousePosEvent(linkMax.x + 50, linkMax.y + 50);
        frames(2, ui);
        assertNotEquals(MouseCursor.HAND, IkGui.getMouseCursor());
    }

    @Test
    void testTextLinkOpenURL() {
        final Runnable ui = linkUI("Homepage", "https://example.com");
        frames(2, ui);
        click(linkMin, linkMax, MouseButton.LEFT, ui);
        assertEquals(1, clicks);
        assertEquals(List.of("https://example.com"), opened);
    }

    @Test
    void testTextLinkOpenURLUsesLabel() {
        final Runnable ui = linkUI("https://example.com/label", null);
        frames(2, ui);
        click(linkMin, linkMax, MouseButton.LEFT, ui);
        assertEquals(List.of("https://example.com/label"), opened);
    }

    @Test
    void testTextLinkOpenURLWithoutHandler() {
        context.platformIO.openInShellFunction = null;
        final Runnable ui = linkUI("Homepage", "https://example.com");
        frames(2, ui);
        click(linkMin, linkMax, MouseButton.LEFT, ui);
        // Still reports the click, but has nothing to open with
        assertEquals(1, clicks);
    }

    @Test
    void testTextLinkCopyLink() {
        final Runnable ui = linkUI("Homepage", "https://example.com/copy");
        frames(2, ui);
        click(linkMin, linkMax, MouseButton.RIGHT, ui);
        frames(2, ui);
        assertFalse(context.openPopupStack.isEmpty(), "The context menu should be open");
        // The context menu only has one item
        click(menuItemMin, menuItemMax, MouseButton.LEFT, ui);
        frames(2, ui);
        assertEquals("https://example.com/copy", IkGui.getClipboardText());
        assertTrue(context.openPopupStack.isEmpty());
        assertTrue(opened.isEmpty());
    }

    @Test
    void testClearPlatformHandlersKeepsDefaultShell() {
        context.platformIO.clearPlatformHandlers();
        assertTrue(context.platformIO.openInShellFunction != null);
        assertFalse(PlatformIO.openInShellDefault(""));
        assertFalse(PlatformIO.openInShellDefault(null));
    }

    @Test
    void testTextLinkColorInEveryTheme() {
        IkGui.styleColorsLight();
        assertEquals(IkGui.getColor(ColorType.HEADER_ACTIVE), IkGui.getColor(ColorType.TEXT_LINK));
        IkGui.styleColorsClassic();
        assertEquals(IkGui.getColor(ColorType.HEADER_ACTIVE), IkGui.getColor(ColorType.TEXT_LINK));
        IkGui.styleColorsDark();
        assertEquals(IkGui.getColor(ColorType.HEADER_ACTIVE), IkGui.getColor(ColorType.TEXT_LINK));
    }

    @Test
    void testGetItemFlags() {
        final int[] flags = new int[2];
        frame(
                () -> {
                    beginHost();
                    IkGui.button("Normal");
                    flags[0] = IkGui.getItemFlags();
                    IkGui.pushItemFlag(ItemFlags.NO_TAB_STOP, true);
                    IkGui.button("No tab stop");
                    flags[1] = IkGui.getItemFlags();
                    IkGui.popItemFlag();
                    IkGui.end();
                });
        assertEquals(0, flags[0] & ItemFlags.NO_TAB_STOP);
        assertEquals(ItemFlags.NO_TAB_STOP, flags[1] & ItemFlags.NO_TAB_STOP);
    }

    @Test
    void testGetWindowDpiScale() {
        final float[] scale = new float[2];
        frame(
                () -> {
                    beginHost();
                    scale[0] = IkGui.getWindowDpiScale();
                    scale[1] = IkGui.getWindowViewport().dpiScale;
                    IkGui.end();
                });
        assertEquals(scale[1], scale[0]);
    }

    @Test
    void testComboClipsItemsAndKeepsSelection() {
        final String[] items = new String[1000];
        for (int i = 0; i < items.length; ++i) {
            items[i] = "Item " + i;
        }
        final IkInt item = new IkInt(900);
        final Runnable ui =
                () -> {
                    beginHost();
                    IkGui.combo("combo", item, items);
                    IkGui.end();
                };
        frames(3, ui);
        press(Key.ARROW_DOWN, ui);
        press(Key.ENTER, ui);
        frames(2, ui);
        assertFalse(context.openPopupStack.isEmpty());
        // The selected item is always submitted, so it can be focused and scrolled to
        final Window popup = context.openPopupStack.getLast().window;
        assertTrue(popup.scrollPosition.y > 0, "The popup should scroll to the selected item");
        press(Key.ARROW_DOWN, ui);
        press(Key.ENTER, ui);
        frames(2, ui);
        assertEquals(901, item.get());
        assertTrue(context.openPopupStack.isEmpty());
    }
}
