package com.ikalagaming.graphics.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** How much room the level of detail draw lists get, see {@link LodCullMath#listRoom}. */
class LodListRoomTest {

    /** Far more slots than any list here needs. */
    private static final int SLOTS = 1 << 24;

    @Test
    void startsAtTheMinimum() {
        assertEquals(LodCullMath.MIN_LIST_ROOM, LodCullMath.listRoom(0, 0, SLOTS));
        assertEquals(LodCullMath.MIN_LIST_ROOM, LodCullMath.listRoom(0, 100, SLOTS));
    }

    @Test
    void growsToTwiceTheCountRoundedUp() {
        final int room = LodCullMath.listRoom(LodCullMath.MIN_LIST_ROOM, 30_000, SLOTS);
        assertEquals(65_536, room);
        assertTrue(room >= 2 * 30_000);
        // Exactly a power of two needs no more
        assertEquals(65_536, LodCullMath.listRoom(LodCullMath.MIN_LIST_ROOM, 32_768, SLOTS));
    }

    @Test
    void neverShrinks() {
        final int room = LodCullMath.listRoom(LodCullMath.MIN_LIST_ROOM, 50_000, SLOTS);
        assertEquals(room, LodCullMath.listRoom(room, 10, SLOTS));
    }

    @Test
    void neverPassesTheSlots() {
        assertEquals(5000, LodCullMath.listRoom(LodCullMath.MIN_LIST_ROOM, 1_000_000, 5000));
        assertEquals(1, LodCullMath.listRoom(0, 0, 0));
    }

    @Test
    void anOverflowingListGetsRoomForItsCount() {
        // Counts go past the room when a list overflows; the next room fits them
        final int room = LodCullMath.listRoom(8192, 9000, SLOTS);
        assertTrue(room >= 9000, "" + room);
    }
}
