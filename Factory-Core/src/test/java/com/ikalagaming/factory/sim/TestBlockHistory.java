package com.ikalagaming.factory.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for stepping against the blocks as they were at a frame. */
class TestBlockHistory {

    /** Everything is solid now. */
    private static final BlockView NOW = (x, y, z) -> true;

    /**
     * A change stamped F happened after movement in F, so F still sees the old block and F + 1 the
     * new one; forgetting it leaves the blocks as they are now.
     */
    @Test
    void testStamp() {
        final BlockHistory history = new BlockHistory();
        history.record(1, 2, 3, 10, false);
        final BlockHistory.AtFrame view = new BlockHistory.AtFrame(NOW, history);
        view.setFrame(10);
        assertFalse(view.solid(1, 2, 3));
        view.setFrame(11);
        assertTrue(view.solid(1, 2, 3));
        // Other blocks are as they are now
        view.setFrame(10);
        assertTrue(view.solid(1, 2, 4));
        history.forgetBefore(11);
        assertEquals(0, history.size());
        assertTrue(view.solid(1, 2, 3));
    }

    /** With several changes to one block, a frame sees the block as it was before the next one. */
    @Test
    void testSeveralChanges() {
        final BlockHistory history = new BlockHistory();
        // Air until a block was placed at 5, which was broken again at 8
        history.record(0, 0, 0, 5, false);
        history.record(0, 0, 0, 8, true);
        final BlockHistory.AtFrame view = new BlockHistory.AtFrame((x, y, z) -> false, history);
        view.setFrame(4);
        assertFalse(view.solid(0, 0, 0));
        view.setFrame(6);
        assertTrue(view.solid(0, 0, 0));
        view.setFrame(8);
        assertTrue(view.solid(0, 0, 0));
        view.setFrame(9);
        assertFalse(view.solid(0, 0, 0));
        history.forgetBefore(6);
        assertEquals(1, history.size());
        view.setFrame(7);
        assertTrue(view.solid(0, 0, 0));
    }

    /**
     * Blocks far enough apart to share a packed key are still told apart, and the ring grows and
     * forgets in order.
     */
    @Test
    void testSharedKeysAndGrowth() {
        final BlockHistory history = new BlockHistory();
        final int far = 1 << 24;
        assertEquals(BlockHistory.key(3, 0, 0), BlockHistory.key(3 + far, 0, 0));
        history.record(3, 0, 0, 1, false);
        history.record(3 + far, 0, 0, 2, true);
        final BlockHistory.AtFrame view = new BlockHistory.AtFrame(NOW, history);
        view.setFrame(1);
        assertFalse(view.solid(3, 0, 0));
        // Only the change stamped 2 is about the far block
        view.setFrame(2);
        assertTrue(view.solid(3, 0, 0));
        assertTrue(view.solid(3 + far, 0, 0));
        // Many more than the ring first holds
        for (int i = 0; i < 1000; ++i) {
            history.record(i, 5, -i, 10 + i, false);
        }
        assertEquals(1002, history.size());
        view.setFrame(500);
        assertFalse(view.solid(490, 5, -490));
        view.setFrame(501);
        assertTrue(view.solid(490, 5, -490));
        assertFalse(view.solid(999, 5, -999));
        history.forgetBefore(800);
        assertEquals(210, history.size());
        view.setFrame(0);
        assertTrue(view.solid(3, 0, 0));
        assertTrue(view.solid(100, 5, -100));
        assertFalse(view.solid(900, 5, -900));
    }
}
