package com.ikalagaming.factory.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikalagaming.factory.sim.PlayerInput;

import org.junit.jupiter.api.Test;

/** Tests for a player's input buffer. */
class TestInputBuffer {

    /** An input to tell apart from the others. */
    private static PlayerInput input(int buttons) {
        return new PlayerInput(buttons, 0, 0);
    }

    /** Inputs are used on their frames, and a missing one repeats the last and counts. */
    @Test
    void testTakeAndMiss() {
        final InputBuffer buffer = new InputBuffer();
        // Nothing has arrived yet, so nothing is a miss
        assertEquals(PlayerInput.NONE, buffer.take(0));
        assertEquals(0, buffer.misses());
        assertTrue(buffer.offer(1, 1, input(1)));
        assertTrue(buffer.offer(1, 3, input(3)));
        assertEquals(2, buffer.buffered(0));
        assertEquals(input(1), buffer.take(1));
        assertEquals(input(1), buffer.take(2));
        assertEquals(1, buffer.misses());
        assertEquals(input(3), buffer.take(3));
        assertEquals(0, buffer.buffered(3));
    }

    /** Late inputs and inputs too far ahead are dropped. */
    @Test
    void testLateAndEarly() {
        final InputBuffer buffer = new InputBuffer();
        assertFalse(buffer.offer(10, 9, input(1)));
        assertTrue(buffer.offer(10, 10, input(2)));
        assertTrue(buffer.offer(10, 10 + InputBuffer.MAX_AHEAD - 1, input(3)));
        assertFalse(buffer.offer(10, 10 + InputBuffer.MAX_AHEAD, input(4)));
        assertEquals(2, buffer.buffered(9));
        // A slot reused by a later frame doesn't answer for an earlier one
        assertEquals(input(2), buffer.take(10));
        assertEquals(input(2), buffer.take(10 + 64));
        assertEquals(1, buffer.misses());
    }

    /** The same input offered twice, as the sliding window does, is used once. */
    @Test
    void testRepeats() {
        final InputBuffer buffer = new InputBuffer();
        for (int i = 0; i < 3; ++i) {
            buffer.offer(0, 0, input(1));
            buffer.offer(0, 1, input(2));
        }
        assertEquals(input(1), buffer.take(0));
        assertEquals(input(2), buffer.take(1));
        assertEquals(0, buffer.misses());
        assertEquals(0, buffer.buffered(1));
    }
}
