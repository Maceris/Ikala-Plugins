package com.ikalagaming.converter.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import lombok.NonNull;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/** The workbench's one undo stack: order across editors, joined drags, forgetting. */
class EditHistoryTest {
    /** The values the steps change, by name, as several editors' files. */
    private final Map<String, Integer> values = new HashMap<>();

    /**
     * A step setting one value.
     *
     * @param key The value's name.
     * @param before It before.
     * @param after It after.
     */
    private record Set(String key, int before, int after, Map<String, Integer> values)
            implements EditHistory.Step {
        @Override
        public String name() {
            return "Set " + key;
        }

        @Override
        public void undo() {
            values.put(key, before);
        }

        @Override
        public void redo() {
            values.put(key, after);
        }

        @Override
        public EditHistory.Step join(EditHistory.@NonNull Step later) {
            return later instanceof Set next && next.key.equals(key)
                    ? new Set(key, before, next.after, values)
                    : null;
        }
    }

    /**
     * Set a value and record it.
     *
     * @param history The stack.
     * @param key The value.
     * @param value Its new value.
     * @param held Whether the control is still held.
     */
    private void set(EditHistory history, String key, int value, boolean held) {
        final int before = values.getOrDefault(key, 0);
        values.put(key, value);
        history.push(new Set(key, before, value, values), key, held);
    }

    @Test
    void undoCrossesEditorsInTheOrderEditsWereMade() {
        final EditHistory history = new EditHistory();
        set(history, "details", 1, false);
        set(history, "graph", 2, false);
        set(history, "details", 3, false);
        assertEquals("Set details", history.undo());
        assertEquals(1, values.get("details"));
        assertEquals("Set graph", history.undo());
        assertEquals(0, values.get("graph"));
        assertEquals("Set graph", history.redo());
        assertEquals(2, values.get("graph"));
        set(history, "look", 4, false);
        assertNull(history.nextRedo(), "A new edit forgets what was undone");
        assertEquals("Set look", history.nextUndo());
    }

    @Test
    void aDragIsOneStep() {
        final EditHistory history = new EditHistory();
        set(history, "roughness", 1, true);
        set(history, "roughness", 2, true);
        set(history, "roughness", 3, true);
        history.endJoin();
        set(history, "roughness", 4, false);
        assertEquals("Set roughness", history.undo());
        assertEquals(3, values.get("roughness"), "The drag ended: the next change is its own");
        history.undo();
        assertEquals(0, values.get("roughness"), "The whole drag undone in one step");
        assertNull(history.undo());
    }

    @Test
    void differentThingsDontJoin() {
        final EditHistory history = new EditHistory();
        set(history, "a", 1, true);
        set(history, "b", 2, true);
        history.undo();
        assertEquals(1, values.get("a"));
        assertEquals(0, values.get("b"));
    }

    @Test
    void forgottenStepsAreSkipped() {
        final EditHistory history = new EditHistory();
        set(history, "details", 1, false);
        set(history, "graph", 2, false);
        history.forget(step -> "Set graph".equals(step.name()));
        assertEquals("Set details", history.undo());
        assertEquals(2, values.get("graph"), "The forgotten edit stays");
    }

    @Test
    void onlyTheNewestStepsAreKept() {
        final EditHistory history = new EditHistory();
        for (int i = 1; i <= EditHistory.DEPTH + 5; ++i) {
            set(history, "a", i, false);
        }
        int undone = 0;
        while (history.undo() != null) {
            ++undone;
        }
        assertEquals(EditHistory.DEPTH, undone);
        assertEquals(5, values.get("a"));
    }
}
