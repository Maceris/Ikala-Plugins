package com.ikalagaming.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_HIDDEN;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL;

import com.ikalagaming.graphics.gui.enums.MouseCursor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Tests for choosing the GLFW cursor IkGui asks for, using a fake window. */
class GlfwMouseCursorsTest {
    /** A window that records the cursor calls. */
    private static class FakeWindow implements GlfwMouseCursors.CursorTarget {
        int mode = GLFW_CURSOR_NORMAL;
        long cursor = 0;
        final List<String> calls = new ArrayList<>();

        @Override
        public int getCursorMode() {
            return mode;
        }

        @Override
        public void setCursorMode(int mode) {
            this.mode = mode;
            calls.add("mode " + mode);
        }

        @Override
        public void setCursor(long cursor) {
            this.cursor = cursor;
            calls.add("cursor " + cursor);
        }
    }

    private FakeWindow window;
    private GlfwMouseCursors cursors;

    /** A fake GLFW handle for a cursor. */
    private static long handle(MouseCursor cursor) {
        return 100 + cursor.ordinal();
    }

    @BeforeEach
    void setUp() {
        window = new FakeWindow();
        final long[] handles = new long[MouseCursor.values().length];
        for (MouseCursor cursor : MouseCursor.values()) {
            if (cursor != MouseCursor.NONE) {
                handles[cursor.ordinal()] = handle(cursor);
            }
        }
        // Pretend the platform doesn't have this one
        handles[MouseCursor.NOT_ALLOWED.ordinal()] = 0;
        cursors = new GlfwMouseCursors(handles);
    }

    @Test
    void testSetsTheCursorShape() {
        cursors.update(window, MouseCursor.TEXT_INPUT, false, false);
        assertEquals(handle(MouseCursor.TEXT_INPUT), window.cursor);
        assertEquals(GLFW_CURSOR_NORMAL, window.mode);

        cursors.update(window, MouseCursor.RESIZE_NW_SE, false, false);
        assertEquals(handle(MouseCursor.RESIZE_NW_SE), window.cursor);
    }

    @Test
    void testUnchangedCursorIsNotSetAgain() {
        cursors.update(window, MouseCursor.HAND, false, false);
        window.calls.clear();
        cursors.update(window, MouseCursor.HAND, false, false);
        cursors.update(window, MouseCursor.HAND, false, false);
        assertEquals(List.of(), window.calls);
    }

    @Test
    void testMissingCursorFallsBackToTheArrow() {
        cursors.update(window, MouseCursor.NOT_ALLOWED, false, false);
        assertEquals(handle(MouseCursor.ARROW), window.cursor);
    }

    @Test
    void testNoneHidesTheCursor() {
        cursors.update(window, MouseCursor.ARROW, false, false);
        cursors.update(window, MouseCursor.NONE, false, false);
        assertEquals(GLFW_CURSOR_HIDDEN, window.mode);

        // Showing a cursor again sets the shape and shows it
        cursors.update(window, MouseCursor.ARROW, false, false);
        assertEquals(GLFW_CURSOR_NORMAL, window.mode);
        assertEquals(handle(MouseCursor.ARROW), window.cursor);
    }

    @Test
    void testDisabledCursorIsLeftAlone() {
        cursors.update(window, MouseCursor.HAND, false, false);
        // e.g. captured for a camera
        window.mode = GLFW_CURSOR_DISABLED;
        window.calls.clear();
        cursors.update(window, MouseCursor.TEXT_INPUT, false, false);
        cursors.update(window, MouseCursor.NONE, false, false);
        assertEquals(List.of(), window.calls);

        // When the cursor is enabled again, the shape is set again even if it didn't change
        window.mode = GLFW_CURSOR_NORMAL;
        window.cursor = 0;
        cursors.update(window, MouseCursor.HAND, false, false);
        assertEquals(handle(MouseCursor.HAND), window.cursor);
    }

    @Test
    void testNoMouseCursorChangeIsRespected() {
        cursors.update(window, MouseCursor.TEXT_INPUT, true, false);
        cursors.update(window, MouseCursor.NONE, true, false);
        assertEquals(List.of(), window.calls);
    }

    @Test
    void testDrawnCursorHidesTheOsCursor() {
        cursors.update(window, MouseCursor.HAND, false, false);
        cursors.update(window, MouseCursor.HAND, false, true);
        assertEquals(GLFW_CURSOR_HIDDEN, window.mode);

        // When IkGui stops drawing it, the OS cursor is shown with the right shape again
        window.cursor = 0;
        cursors.update(window, MouseCursor.HAND, false, false);
        assertEquals(GLFW_CURSOR_NORMAL, window.mode);
        assertEquals(handle(MouseCursor.HAND), window.cursor);
    }

    @Test
    void testCursorHiddenElsewhereIsShownAgain() {
        cursors.update(window, MouseCursor.ARROW, false, false);
        window.mode = GLFW_CURSOR_HIDDEN;
        cursors.update(window, MouseCursor.ARROW, false, false);
        assertEquals(GLFW_CURSOR_NORMAL, window.mode);
    }
}
