package com.ikalagaming.graphics.frontend.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.IkInt;
import com.ikalagaming.graphics.frontend.gui.data.Window;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;
import com.ikalagaming.graphics.frontend.gui.flags.WindowFlags;

import org.joml.Vector2f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for list boxes. These run headless, without fonts loaded. */
class IkGuiListBoxTest {

    /** How much precision we expect in float comparisons. */
    private static final float DELTA = 0.001f;

    private static final String[] ITEMS = {
        "Apple",
        "Banana",
        "Cherry",
        "Kiwi",
        "Mango",
        "Orange",
        "Pineapple",
        "Strawberry",
        "Watermelon"
    };

    private Context context;

    /** The bounding box of the last item, recorded by the UI code in tests. */
    private final Vector2f itemMin = new Vector2f();

    private final Vector2f itemMax = new Vector2f();

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

    private void click(float x, float y, Runnable ui) {
        context.io.addMousePosEvent(x, y);
        frame(ui);
        context.io.addMouseButtonEvent(MouseButton.LEFT, true);
        frame(ui);
        context.io.addMouseButtonEvent(MouseButton.LEFT, false);
        frame(ui);
    }

    private Runnable window(Runnable body) {
        return () -> {
            IkGui.setNextWindowPos(100, 100, Condition.FIRST_USE_EVER);
            IkGui.setNextWindowSize(500, 400, Condition.FIRST_USE_EVER);
            IkGui.begin("Host", null, WindowFlags.NONE);
            body.run();
            IkGui.getItemRectMin(itemMin);
            IkGui.getItemRectMax(itemMax);
            IkGui.end();
        };
    }

    /**
     * Find the child window that hosts a list box.
     *
     * @return The list box window.
     */
    private Window listBoxWindow() {
        final Window host = IkGuiInternal.findWindowByName("Host");
        assertNotNull(host);
        assertEquals(1, host.childWindows.size());
        return host.childWindows.getFirst();
    }

    @Test
    void testListBoxSelectsClickedItem() {
        final IkInt current = new IkInt(0);
        final boolean[] changed = {false};
        Runnable ui = window(() -> changed[0] |= IkGui.listBox("Fruit", current, ITEMS, 4));
        frames(3, ui);
        assertFalse(changed[0]);

        final Window list = listBoxWindow();
        // The frame style uses the frame padding inside the list
        assertEquals(context.style.variable.framePadding.x, list.padding.x, DELTA);
        assertEquals(context.style.variable.framePadding.y, list.padding.y, DELTA);
        // 4 items tall, plus a quarter item so it's obvious the list scrolls
        final float lineHeight = IkGui.getTextLineHeightWithSpacing();
        assertEquals(
                (int) (lineHeight * 4.25f + context.style.variable.framePadding.y * 2),
                list.size.y,
                DELTA);
        assertTrue(list.scrollMax.y > 0);

        // Click the third item
        final float itemY = list.position.y + list.padding.y + lineHeight * 2 + 2;
        click(list.position.x + list.padding.x + 4, itemY, ui);
        assertTrue(changed[0]);
        assertEquals(2, current.get());
    }

    @Test
    void testListBoxGroupIncludesLabel() {
        final IkInt current = new IkInt(0);
        final boolean[] hovered = {false};
        Runnable ui =
                window(
                        () -> {
                            IkGui.listBox("A label", current, ITEMS);
                            hovered[0] = IkGui.isItemHovered();
                        });
        frames(3, ui);
        final Window list = listBoxWindow();
        // The last item is the whole group, which extends past the frame to cover the label
        assertEquals(list.position.x, itemMin.x, DELTA);
        assertTrue(itemMax.x > list.position.x + list.size.x);

        // Hovering the label counts as hovering the list box
        context.io.addMousePosEvent(itemMax.x - 2, itemMin.y + 4);
        frames(2, ui);
        assertTrue(hovered[0]);
    }

    @Test
    void testDefaultHeightFitsAboutSevenItems() {
        Runnable ui =
                window(
                        () -> {
                            if (IkGui.beginListBox("##custom")) {
                                for (String item : ITEMS) {
                                    IkGui.selectable(item);
                                }
                                IkGui.endListBox();
                            }
                        });
        frames(3, ui);
        final Window list = listBoxWindow();
        final float expected =
                (int)
                        (IkGui.getTextLineHeightWithSpacing() * 7.25f
                                + context.style.variable.framePadding.y * 2);
        assertEquals(expected, list.size.y, DELTA);
        // Hidden labels don't add any width
        assertEquals(list.position.x + list.size.x, itemMax.x, DELTA);
    }

    @Test
    void testCustomWidthAndHeight() {
        Runnable ui =
                window(
                        () -> {
                            if (IkGui.beginListBox("Sized", 120, 60)) {
                                IkGui.text("Inside");
                                IkGui.endListBox();
                            }
                        });
        frames(3, ui);
        final Window list = listBoxWindow();
        assertEquals(120, list.size.x, DELTA);
        assertEquals(60, list.size.y, DELTA);
        assertTrue(context.windowStack.isEmpty());
    }
}
