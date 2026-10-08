package com.ikalagaming.graphics.frontend.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.graphics.frontend.gui.data.Context;
import com.ikalagaming.graphics.frontend.gui.data.TextFilter;
import com.ikalagaming.graphics.frontend.gui.enums.Condition;
import com.ikalagaming.graphics.frontend.gui.enums.MouseButton;

import org.joml.Vector2f;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

/** Tests for the TextFilter helper. */
class IkGuiTextFilterTest {
    private static final List<String> LINES =
            List.of(
                    "aaa1.c",
                    "bbb1.c",
                    "ccc1.c",
                    "aaa2.cpp",
                    "bbb2.cpp",
                    "ccc2.cpp",
                    "abc.h",
                    "hello, world");

    private static List<String> filter(String filterText) {
        final TextFilter filter = new TextFilter(filterText);
        return LINES.stream().filter(filter::passFilter).toList();
    }

    @Test
    void testEmptyFilterPassesEverything() {
        final TextFilter filter = new TextFilter();
        assertFalse(filter.isActive());
        assertTrue(filter.passFilter("anything"));
        assertTrue(filter.passFilter(null));
        assertEquals(LINES, filter("   "));
    }

    @Test
    void testSingleWordIgnoresCase() {
        assertEquals(List.of("aaa1.c", "aaa2.cpp"), filter("AAA"));
    }

    @Test
    void testSpacesRequireAllWords() {
        assertEquals(List.of("aaa2.cpp"), filter("aaa cpp"));
    }

    @Test
    void testCommasMatchAnySequence() {
        assertEquals(List.of("aaa1.c", "aaa2.cpp", "abc.h"), filter("aaa,.h"));
        // Each comma separated sequence can have several required words
        assertEquals(
                List.of("aaa2.cpp", "bbb1.c"), filter("aaa cpp,bbb 1").stream().sorted().toList());
    }

    @Test
    void testExclusions() {
        assertEquals(
                List.of("bbb1.c", "ccc1.c", "bbb2.cpp", "ccc2.cpp", "abc.h", "hello, world"),
                filter("-aaa"));
        // Exclusions apply on top of inclusions, wherever they are in the filter
        assertEquals(List.of("aaa1.c"), filter("aaa -cpp"));
        assertEquals(List.of("aaa1.c"), filter("aaa,-cpp"));
    }

    @Test
    void testQuotesMatchExactSequences() {
        assertEquals(List.of("hello, world"), filter("\"o, w\""));
        // An unterminated quote runs to the end of the filter
        assertEquals(List.of("hello, world"), filter("\"lo, wor"));
    }

    @Test
    void testClearAndRebuild() {
        final TextFilter filter = new TextFilter("abc");
        assertTrue(filter.isActive());
        assertFalse(filter.passFilter("xyz"));
        filter.inputBuffer.set("xy");
        filter.build();
        assertTrue(filter.passFilter("xyz"));
        filter.clear();
        assertFalse(filter.isActive());
        assertTrue(filter.passFilter("anything"));
    }

    @Test
    void testDrawRebuildsWhenTyping() {
        final Context context = IkGui.createContext();
        try {
            context.io.iniFilename = null;
            context.io.displaySize.set(1280, 720);
            context.io.mouseInsideWindow = true;
            final TextFilter filter = new TextFilter();
            final boolean[] changed = new boolean[1];
            final Vector2f min = new Vector2f();
            final Vector2f max = new Vector2f();
            final Runnable ui =
                    () -> {
                        IkGui.setNextWindowPos(50, 50, Condition.FIRST_USE_EVER);
                        IkGui.setNextWindowSize(300, 200, Condition.FIRST_USE_EVER);
                        IkGui.begin("Filter");
                        changed[0] |= filter.draw("Filter");
                        IkGui.getItemRectMin(min);
                        IkGui.getItemRectMax(max);
                        IkGui.end();
                    };
            final Runnable frame =
                    () -> {
                        IkGui.newFrame();
                        ui.run();
                        IkGui.render();
                    };
            frame.run();
            frame.run();
            context.io.addMousePosEvent(min.x + 5, (min.y + max.y) * 0.5f);
            frame.run();
            context.io.addMouseButtonEvent(MouseButton.LEFT, true);
            frame.run();
            context.io.addMouseButtonEvent(MouseButton.LEFT, false);
            frame.run();
            context.io.addInputCharacters("bb");
            frame.run();
            frame.run();
            assertTrue(changed[0]);
            assertEquals("bb", filter.inputBuffer.get());
            assertEquals(
                    Arrays.asList("bbb1.c", "bbb2.cpp"),
                    LINES.stream().filter(filter::passFilter).toList());
        } finally {
            IkGui.destroyContext();
        }
    }
}
