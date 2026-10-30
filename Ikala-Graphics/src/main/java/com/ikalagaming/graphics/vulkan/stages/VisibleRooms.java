package com.ikalagaming.graphics.vulkan.stages;

import com.ikalagaming.graphics.vulkan.RangeAllocator;

import lombok.NonNull;

import java.util.Arrays;

/**
 * Where each mesh slot's room in the culled visible lists is, kept from frame to frame: a slot
 * keeps its room while its instances fit, and only a slot that needs more, or none, moves. Laid out
 * afresh each frame, one slot's room starting where the last ended, a section streaming in moved
 * every room after it, and every draw command with it, which then had to be written again.
 *
 * <p>Each list is {@link #capacity()} entries long, the same for every list, so a command's first
 * instance is its list's start plus its slot's room. The capacity only grows, and the commands are
 * all written again when it does. Pure Java, so it can be checked without a GPU.
 */
final class VisibleRooms {

    /** How many entries each list starts with. */
    static final int INITIAL_CAPACITY = 4096;

    /** What a room changing tells. */
    @FunctionalInterface
    interface Moved {
        /**
         * A slot's room moved, grew or went, so its commands must be written again.
         *
         * @param kind The kind of mesh, by {@link com.ikalagaming.graphics.MeshKind} ordinal.
         * @param slot The slot.
         */
        void moved(int kind, int slot);
    }

    /** Hands out the rooms. */
    private final RangeAllocator rooms;

    /** Where each kind's slots' rooms start, by kind then slot. */
    private final int[][] starts;

    /** How long each kind's slots' rooms are. */
    private final int[][] sizes;

    /**
     * Start with no rooms.
     *
     * @param kinds How many kinds of mesh there are.
     */
    VisibleRooms(int kinds) {
        this(kinds, INITIAL_CAPACITY);
    }

    /**
     * Start with no rooms, and lists of a length.
     *
     * @param kinds How many kinds of mesh there are.
     * @param capacity How many entries each list starts with.
     */
    VisibleRooms(int kinds, int capacity) {
        rooms = new RangeAllocator(capacity, Integer.MAX_VALUE / InstanceDrawUpdate.LIST_COUNT);
        starts = new int[kinds][0];
        sizes = new int[kinds][0];
    }

    /**
     * Give each slot room for as many instances as it needs, moving only those that need more or
     * none.
     *
     * @param need How many instances each slot needs room for, by kind then slot.
     * @param moved Told of each slot whose room changed.
     * @return How many entries each list has, the capacity.
     */
    int place(int @NonNull [] @NonNull [] need, @NonNull Moved moved) {
        for (int kind = 0; kind < need.length; ++kind) {
            final int[] wanted = need[kind];
            if (starts[kind].length < wanted.length) {
                starts[kind] = Arrays.copyOf(starts[kind], wanted.length);
                sizes[kind] = Arrays.copyOf(sizes[kind], wanted.length);
            }
            final int[] start = starts[kind];
            final int[] size = sizes[kind];
            for (int slot = 0; slot < start.length; ++slot) {
                final int count = slot < wanted.length ? wanted[slot] : 0;
                if (count <= size[slot] && (count > 0 || size[slot] == 0)) {
                    continue;
                }
                if (size[slot] > 0) {
                    rooms.free(start[slot], size[slot]);
                }
                start[slot] = count > 0 ? rooms.allocate(count) : 0;
                size[slot] = count;
                moved.moved(kind, slot);
            }
        }
        return rooms.getCapacity();
    }

    /**
     * Where each slot's room starts, for one kind.
     *
     * @param kind The kind of mesh, by ordinal.
     * @return The starts, by slot, at least as long as the last need given; not a copy.
     */
    int[] starts(int kind) {
        return starts[kind];
    }

    /**
     * How many entries each list has.
     *
     * @return The capacity.
     */
    int capacity() {
        return rooms.getCapacity();
    }
}
