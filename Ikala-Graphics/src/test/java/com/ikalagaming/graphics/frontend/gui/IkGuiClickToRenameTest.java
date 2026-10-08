package com.ikalagaming.graphics.frontend.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.Window;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.Key;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Tests for the "click again to rename" demo, which uses delayed clicks. */
class IkGuiClickToRenameTest {
    /** How long each faked frame takes, in milliseconds. */
    private static final long FRAME_TIME = 50;

    private static final String[] ORIGINAL_NAMES = IkGuiDemo.renameItems.clone();

    private Context context;

    /** The y position of the middle of each item. */
    private final List<Float> itemY = new ArrayList<>();

    private float itemX;

    private final Runnable ui =
            () -> {
                IkGui.setNextWindowPos(100, 100, Condition.FIRST_USE_EVER);
                IkGui.setNextWindowSize(300, 300, Condition.FIRST_USE_EVER);
                IkGui.begin("Host", null, WindowFlags.NONE);
                IkGuiDemo.showWidgetsClickToRename();
                IkGui.end();
            };

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        // Don't read or write an .ini file
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        context.io.mouseInsideWindow = true;
        context.io.configInputTrickleEventQueue = false;
        resetDemo();
        frames(2);
        findItems();
    }

    @AfterEach
    void tearDown() {
        IkGui.destroyContext();
        resetDemo();
    }

    private static void resetDemo() {
        System.arraycopy(ORIGINAL_NAMES, 0, IkGuiDemo.renameItems, 0, ORIGINAL_NAMES.length);
        IkGuiDemo.renameSelected = -1;
        IkGuiDemo.renameEditing = -1;
        IkGuiDemo.renameLastOpened = "";
    }

    /** Run a frame that takes FRAME_TIME milliseconds. */
    private void frame() {
        context.frameStartTime -= FRAME_TIME;
        IkGui.newFrame();
        ui.run();
        IkGui.render();
    }

    private void frames(int count) {
        for (int i = 0; i < count; ++i) {
            frame();
        }
    }

    /** Move the mouse down the window, recording where each new item starts being hovered. */
    private void findItems() {
        final Window window = IkGuiInternal.findWindowByName("Host");
        itemX = window.position.x + context.style.variable.windowPadding.x + 20;
        int lastHovered = 0;
        final List<Float> starts = new ArrayList<>();
        final List<Float> ends = new ArrayList<>();
        for (float y = window.position.y; y < window.position.y + 200; y += 1) {
            context.io.addMousePosEvent(itemX, y);
            frame();
            final int hovered = context.hoveredID;
            if (hovered != lastHovered) {
                if (lastHovered != 0) {
                    ends.add(y);
                }
                if (hovered != 0) {
                    starts.add(y);
                }
                lastHovered = hovered;
            }
        }
        // The title bar and other items come first, the last items are the list
        final int count = IkGuiDemo.renameItems.length;
        for (int i = ends.size() - count; i < ends.size(); ++i) {
            itemY.add((starts.get(i) + ends.get(i)) * 0.5f);
        }
        assertEquals(count, itemY.size());
        // Let the delayed click logic settle, without anything being clicked
        frames(20);
    }

    private void click(int item) {
        context.io.addMousePosEvent(itemX, itemY.get(item));
        frame();
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame();
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frame();
    }

    @Test
    void testFirstClickOnlySelects() {
        click(2);
        assertEquals(2, IkGuiDemo.renameSelected);
        frames(20);
        // It wasn't selected before the click, so it isn't renamed
        assertEquals(-1, IkGuiDemo.renameEditing);
    }

    @Test
    void testClickAgainToRename() {
        click(2);
        frames(20);
        click(2);
        // Not right away, in case it's the start of a double click
        assertEquals(-1, IkGuiDemo.renameEditing);
        frames(20);
        assertEquals(2, IkGuiDemo.renameEditing);

        // The whole name is selected, so typing replaces it
        frames(2);
        context.io.addInputCharacters("Coconut");
        frame();
        context.io.addKeyEvent(Key.ENTER, true);
        frame();
        context.io.addKeyEvent(Key.ENTER, false);
        frames(2);
        assertEquals(-1, IkGuiDemo.renameEditing);
        assertEquals("Coconut", IkGuiDemo.renameItems[2]);
    }

    @Test
    void testEscapeKeepsTheName() {
        click(1);
        frames(20);
        click(1);
        frames(20);
        assertEquals(1, IkGuiDemo.renameEditing);
        frames(2);
        context.io.addInputCharacters("Typo");
        frame();
        context.io.addKeyEvent(Key.ESCAPE, true);
        frame();
        context.io.addKeyEvent(Key.ESCAPE, false);
        frames(2);
        assertEquals(-1, IkGuiDemo.renameEditing);
        assertEquals(ORIGINAL_NAMES[1], IkGuiDemo.renameItems[1]);
    }

    @Test
    void testDoubleClickOpens() {
        click(3);
        frames(20);
        // Already selected, but a double click opens it instead of renaming
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame();
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frame();
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame();
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frames(20);
        assertEquals(ORIGINAL_NAMES[3], IkGuiDemo.renameLastOpened);
        assertEquals(-1, IkGuiDemo.renameEditing);
        assertTrue(IkGuiDemo.renameSelected == 3);
    }
}
