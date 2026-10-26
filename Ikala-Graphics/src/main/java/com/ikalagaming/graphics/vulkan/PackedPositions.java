package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.lod.LodStore;

import lombok.NonNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A set of section positions kept in flat arrays as well as a map, so going through all of them
 * reads one array of corners rather than following a hash entry and a record for each: the volume
 * atlas looks for the nearest of tens of thousands of waiting sections as the camera moves.
 */
final class PackedPositions {
    /** How many values a corner has in {@link #corners}. */
    static final int CORNER_VALUES = 3;

    /** The capacity to start with. */
    private static final int INITIAL_CAPACITY = 64;

    /** Where each position is in the arrays. */
    private final Map<LodStore.SectionPos, Integer> index = new HashMap<>();

    /** The positions, the first {@link #size} of them. */
    private LodStore.SectionPos[] positions = new LodStore.SectionPos[INITIAL_CAPACITY];

    /** Each position's x, y and z, in sections, in the same order. */
    private int[] corners = new int[INITIAL_CAPACITY * CORNER_VALUES];

    /** How many positions there are. */
    private int size;

    /**
     * Add a position.
     *
     * @param position The position.
     * @return True if it wasn't there already.
     */
    boolean add(LodStore.@NonNull SectionPos position) {
        if (index.containsKey(position)) {
            return false;
        }
        if (size == positions.length) {
            positions = Arrays.copyOf(positions, size * 2);
            corners = Arrays.copyOf(corners, size * 2 * CORNER_VALUES);
        }
        positions[size] = position;
        corners[size * CORNER_VALUES] = position.x();
        corners[size * CORNER_VALUES + 1] = position.y();
        corners[size * CORNER_VALUES + 2] = position.z();
        index.put(position, size);
        ++size;
        return true;
    }

    /**
     * Remove a position, moving the last one into its place.
     *
     * @param position The position.
     * @return True if it was there.
     */
    boolean remove(LodStore.@NonNull SectionPos position) {
        final Integer at = index.remove(position);
        if (at == null) {
            return false;
        }
        final int last = --size;
        if (at != last) {
            positions[at] = positions[last];
            System.arraycopy(
                    corners, last * CORNER_VALUES, corners, at * CORNER_VALUES, CORNER_VALUES);
            index.put(positions[at], at);
        }
        positions[last] = null;
        return true;
    }

    /**
     * Whether a position is in the set.
     *
     * @param position The position.
     * @return True if it is.
     */
    boolean contains(LodStore.@NonNull SectionPos position) {
        return index.containsKey(position);
    }

    /**
     * How many positions there are.
     *
     * @return The count.
     */
    int size() {
        return size;
    }

    /**
     * Whether there are none.
     *
     * @return True if empty.
     */
    boolean isEmpty() {
        return size == 0;
    }

    /**
     * The positions, the first {@link #size()} of them, to read only.
     *
     * @return The array, which changes as positions are added and removed.
     */
    LodStore.SectionPos[] positions() {
        return positions;
    }

    /**
     * Each position's corner, x, y and z in sections, in the order of {@link #positions()}, to read
     * only.
     *
     * @return The array, which changes as positions are added and removed.
     */
    int[] corners() {
        return corners;
    }
}
