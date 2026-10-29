package com.ikalagaming.graphics.vulkan.stages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Rooms in the culled lists that stay put while they fit. */
class VisibleRoomsTest {

    private static List<int[]> place(VisibleRooms rooms, int[][] need) {
        final List<int[]> moved = new ArrayList<>();
        rooms.place(need, (kind, slot) -> moved.add(new int[] {kind, slot}));
        return moved;
    }

    private static void assertNoOverlap(VisibleRooms rooms, int[][] need) {
        final Set<Integer> used = new HashSet<>();
        for (int kind = 0; kind < need.length; ++kind) {
            for (int slot = 0; slot < need[kind].length; ++slot) {
                for (int i = 0; i < need[kind][slot]; ++i) {
                    final int entry = rooms.starts(kind)[slot] + i;
                    assertTrue(entry < rooms.capacity(), "Inside the list");
                    assertTrue(used.add(entry), "No two rooms share an entry");
                }
            }
        }
    }

    @Test
    void roomsStayPutWhileOthersComeAndGo() {
        final VisibleRooms rooms = new VisibleRooms(2, 16);
        final int[][] need = {{1, 2, 0, 3}, {0, 1}};
        assertEquals(4, place(rooms, need).size(), "Each slot needing room got some");
        assertNoOverlap(rooms, need);
        final int kept = rooms.starts(0)[3];

        // A slot before it grows, one goes, a new one comes: the rest stay
        need[0][1] = 5;
        need[0][0] = 0;
        need[0][2] = 1;
        final List<int[]> moved = place(rooms, need);
        assertEquals(3, moved.size());
        for (int[] slot : moved) {
            assertFalse(slot[0] == 0 && slot[1] == 3, "Slot 3 wasn't touched");
        }
        assertEquals(kept, rooms.starts(0)[3]);
        assertNoOverlap(rooms, need);

        // Fewer instances than its room holds keep the room
        need[0][1] = 2;
        assertTrue(place(rooms, need).isEmpty());
    }

    @Test
    void theListsGrowWhenRoomRunsOut() {
        final VisibleRooms rooms = new VisibleRooms(1, 4);
        final int[][] need = {{3, 3, 3}};
        place(rooms, need);
        assertTrue(rooms.capacity() >= 9, "" + rooms.capacity());
        assertNoOverlap(rooms, need);
    }

    @Test
    void moreSlotsGetRoomToo() {
        final VisibleRooms rooms = new VisibleRooms(1, 8);
        place(rooms, new int[][] {{1}});
        final int[][] need = {{1, 0, 2}};
        final List<int[]> moved = place(rooms, need);
        assertEquals(1, moved.size(), "Only the new slot with instances");
        assertEquals(2, moved.getFirst()[1]);
        assertNoOverlap(rooms, need);
    }
}
