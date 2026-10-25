package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.lod.LodStore;

import lombok.NonNull;

import java.util.function.BiConsumer;

/**
 * What entered and left a set of sections, the newest {@link #CAPACITY} changes, numbered by how
 * many there have been, so a reader that keeps up with the set takes only the changes since it last
 * looked. Guarded by its owner's lock, but for {@link #count()}.
 */
final class SectionLog {

    /** How many changes are kept, a power of two. */
    static final int CAPACITY = 1 << 16;

    /** The sections that changed, at their count modulo the capacity. */
    private final LodStore.SectionPos[] positions = new LodStore.SectionPos[CAPACITY];

    /** Whether each entered, rather than left. */
    private final boolean[] entered = new boolean[CAPACITY];

    /** How many changes there have been. */
    private volatile long count;

    /**
     * Note a change.
     *
     * @param position The section.
     * @param in Whether it entered the set, rather than left it.
     */
    void record(LodStore.@NonNull SectionPos position, boolean in) {
        final int at = (int) (count & (CAPACITY - 1));
        positions[at] = position;
        entered[at] = in;
        count += 1;
    }

    /** Forget every change so far, as when the set is emptied: readers look at it whole again. */
    void reset() {
        count = count + CAPACITY + 1;
    }

    /**
     * How many changes there have been, which changes whenever the set does.
     *
     * @return The count.
     */
    long count() {
        return count;
    }

    /**
     * The changes since a count, oldest first.
     *
     * @param since The count the reader last saw.
     * @param visitor What to do with each section and whether it entered.
     * @return The count now, or -1 if the changes since are no longer all kept.
     */
    long since(long since, @NonNull BiConsumer<LodStore.SectionPos, Boolean> visitor) {
        final long now = count;
        if (since < 0 || since > now || now - since > CAPACITY) {
            return -1;
        }
        for (long k = since; k < now; ++k) {
            final int at = (int) (k & (CAPACITY - 1));
            visitor.accept(positions[at], entered[at]);
        }
        return now;
    }
}
