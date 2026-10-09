package com.ikalagaming.graphics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.gui.data.Context;
import com.ikalagaming.graphics.gui.data.DrawList;
import com.ikalagaming.graphics.gui.data.Viewport;
import com.ikalagaming.graphics.gui.enums.MouseCursor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for the software mouse cursor drawn for io.configMouseDrawCursor. */
class IkGuiMouseCursorTest {
    private Context context;

    @BeforeEach
    void setUp() {
        context = IkGui.createContext();
        // Don't read or write an .ini file
        context.io.iniFilename = null;
        context.io.displaySize.set(1280, 720);
        context.io.mouseInsideWindow = true;
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

    /** The number of rectangles drawn for a cursor: two shadows, the border, and the fill. */
    private static int expectedRectangles(MouseCursor cursor) {
        final IkGuiImplMouseCursor.CursorShape shape = IkGuiImplMouseCursor.getShape(cursor);
        return 3 * shape.border().runs().size() + shape.fill().runs().size();
    }

    /**
     * The number of commands in the main viewport's foreground draw list, if it was rendered.
     *
     * @return The command count, or 0 if the list wasn't part of the draw data.
     */
    private int foregroundCommands() {
        final Viewport viewport = context.mainViewport;
        final DrawList foreground = viewport.foregroundDrawList;
        if (foreground == null || !viewport.drawData.drawLists.contains(foreground)) {
            return 0;
        }
        return foreground.getCommandCount();
    }

    @Test
    void testEveryCursorHasAShape() {
        for (MouseCursor cursor : MouseCursor.values()) {
            final IkGuiImplMouseCursor.CursorShape shape = IkGuiImplMouseCursor.getShape(cursor);
            assertFalse(shape.fill().runs().isEmpty(), cursor.toString());
            assertFalse(shape.border().runs().isEmpty(), cursor.toString());
            // All the pixels are inside the cursor
            for (IkGuiImplMouseCursor.Layer layer :
                    new IkGuiImplMouseCursor.Layer[] {shape.fill(), shape.border()}) {
                for (int[] run : layer.runs()) {
                    assertTrue(run[0] >= 0 && run[0] + run[2] <= shape.width(), cursor.toString());
                    assertTrue(run[1] >= 0 && run[1] < shape.height(), cursor.toString());
                }
            }
        }
    }

    @Test
    void testArrowMatchesTheArt() {
        final IkGuiImplMouseCursor.CursorShape arrow =
                IkGuiImplMouseCursor.getShape(MouseCursor.ARROW);
        assertEquals(12, arrow.width());
        assertEquals(19, arrow.height());
        assertEquals(0, arrow.offsetX());
        assertEquals(0, arrow.offsetY());
        // The tip of the arrow is a single border pixel at the hotspot
        final int[] first = arrow.border().runs().getFirst();
        assertEquals(0, first[0]);
        assertEquals(0, first[1]);
        assertEquals(1, first[2]);
        // The text cursor's hotspot is in the middle of the I-beam
        final IkGuiImplMouseCursor.CursorShape text =
                IkGuiImplMouseCursor.getShape(MouseCursor.TEXT_INPUT);
        assertEquals(1, text.offsetX());
        assertEquals(8, text.offsetY());
    }

    @Test
    void testNoCursorIsDrawnByDefault() {
        context.io.addMousePosEvent(100, 100);
        frame(() -> {});
        frame(() -> {});
        assertEquals(0, foregroundCommands());
    }

    @Test
    void testCursorIsDrawn() {
        context.io.configMouseDrawCursor = true;
        context.io.addMousePosEvent(100, 100);
        frame(() -> {});
        frame(() -> {});
        assertEquals(expectedRectangles(MouseCursor.ARROW), foregroundCommands());

        // The cursor IkGui wants is drawn
        frame(() -> IkGui.setMouseCursor(MouseCursor.HAND));
        assertEquals(expectedRectangles(MouseCursor.HAND), foregroundCommands());
    }

    @Test
    void testNoneDrawsNothing() {
        context.io.configMouseDrawCursor = true;
        context.io.addMousePosEvent(100, 100);
        frame(() -> {});
        frame(() -> IkGui.setMouseCursor(MouseCursor.NONE));
        assertEquals(0, foregroundCommands());
    }

    @Test
    void testCursorOutsideTheViewportIsNotDrawn() {
        context.io.configMouseDrawCursor = true;
        // The mouse position is invalid until the mouse moves
        frame(() -> {});
        frame(() -> {});
        assertEquals(0, foregroundCommands());

        context.io.addMousePosEvent(5000, 5000);
        frame(() -> {});
        frame(() -> {});
        assertEquals(0, foregroundCommands());
    }
}
