package com.ikalagaming.factory.sim;

import com.ikalagaming.util.LongIntMap;

import lombok.NonNull;

/**
 * Recent block changes, each stamped with the command frame it happened in, so movement can be
 * stepped against the blocks as they were at any recent frame. A change stamped F happened after
 * the movement of frame F, so movement in frame F sees it only if it is stamped before F. See the
 * Command Frames design doc, "Blocks in Frames".
 *
 * <p>Only whether each block was solid before is kept, since that is all movement reads. Changes
 * live in primitive arrays used as a ring, oldest first, and a map from each block's packed
 * position to the oldest change with that key, chained to the later ones, so nothing is boxed and
 * adding and forgetting allocate nothing once the ring is big enough. One thread only.
 */
public final class BlockHistory {

    /** What a chain ends with. */
    private static final int NONE = -1;

    /** The ring's first size, a power of two. */
    private static final int START_CAPACITY = 64;

    /** The bits of a packed x or z. */
    private static final int WIDE_BITS = 24;

    /** The bits of a packed y. */
    private static final int Y_BITS = 16;

    /** Masks for the packed parts. */
    private static final long WIDE_MASK = (1L << WIDE_BITS) - 1;

    private static final long Y_MASK = (1L << Y_BITS) - 1;

    /** The frame each change happened in, by slot. */
    private long[] frames = new long[START_CAPACITY];

    /** Each change's block, by slot. */
    private int[] xs = new int[START_CAPACITY];

    private int[] ys = new int[START_CAPACITY];
    private int[] zs = new int[START_CAPACITY];

    /** Whether each changed block was solid before, by slot. */
    private boolean[] wasSolid = new boolean[START_CAPACITY];

    /** The slot of the next change with the same key, or {@link #NONE}, by slot. */
    private int[] next = new int[START_CAPACITY];

    /** The slot of the oldest change, by packed position. */
    private final LongIntMap oldest = new LongIntMap(START_CAPACITY);

    /** The slot of the oldest change kept. */
    private int head;

    /** How many changes are kept. */
    private int size;

    /**
     * A box around every kept change, so blocks outside it are answered without a lookup. It only
     * grows until everything is forgotten.
     */
    private int minX = Integer.MAX_VALUE;

    private int minY = Integer.MAX_VALUE;
    private int minZ = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE;
    private int maxY = Integer.MIN_VALUE;
    private int maxZ = Integer.MIN_VALUE;

    /**
     * A block's position packed into a key: 24 bits each of x and z, 16 of y. Blocks further out
     * than those hold share keys with others, so every lookup still checks the whole position.
     *
     * @param x The block's x.
     * @param y The block's y.
     * @param z The block's z.
     * @return The key.
     */
    static long key(int x, int y, int z) {
        return (x & WIDE_MASK) << (WIDE_BITS + Y_BITS) | (z & WIDE_MASK) << Y_BITS | y & Y_MASK;
    }

    /**
     * Keep a change. Changes at one position must come in frame order, as the server sends them.
     *
     * @param x The block's x.
     * @param y The block's y.
     * @param z The block's z.
     * @param frame The command frame it happened in.
     * @param solidBefore Whether the block was solid before.
     */
    public void record(int x, int y, int z, long frame, boolean solidBefore) {
        if (size == frames.length) {
            grow();
        }
        final int slot = (head + size) & (frames.length - 1);
        frames[slot] = frame;
        xs[slot] = x;
        ys[slot] = y;
        zs[slot] = z;
        wasSolid[slot] = solidBefore;
        next[slot] = NONE;
        ++size;
        final long key = key(x, y, z);
        int at = oldest.get(key);
        if (at == LongIntMap.MISSING) {
            oldest.put(key, slot);
        } else {
            while (next[at] != NONE) {
                at = next[at];
            }
            next[at] = slot;
        }
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        minZ = Math.min(minZ, z);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
        maxZ = Math.max(maxZ, z);
    }

    /** Double the ring, keeping the changes in order. */
    private void grow() {
        final long[] oldFrames = frames;
        final int[] oldXs = xs;
        final int[] oldYs = ys;
        final int[] oldZs = zs;
        final boolean[] oldSolid = wasSolid;
        final int oldHead = head;
        final int count = size;
        final int capacity = oldFrames.length << 1;
        frames = new long[capacity];
        xs = new int[capacity];
        ys = new int[capacity];
        zs = new int[capacity];
        wasSolid = new boolean[capacity];
        next = new int[capacity];
        oldest.clear();
        head = 0;
        size = 0;
        for (int i = 0; i < count; ++i) {
            final int from = (oldHead + i) & (oldFrames.length - 1);
            record(oldXs[from], oldYs[from], oldZs[from], oldFrames[from], oldSolid[from]);
        }
    }

    /**
     * Forget changes from before a frame, which nothing replays any more.
     *
     * @param frame The oldest frame to keep changes from.
     */
    public void forgetBefore(long frame) {
        while (size > 0 && frames[head] < frame) {
            final long key = key(xs[head], ys[head], zs[head]);
            // The oldest change overall is the oldest with its key, so it heads its chain
            if (next[head] == NONE) {
                oldest.remove(key);
            } else {
                oldest.put(key, next[head]);
            }
            head = (head + 1) & (frames.length - 1);
            --size;
        }
        if (size == 0) {
            clear();
        }
    }

    /**
     * How many changes are kept.
     *
     * @return The count.
     */
    public int size() {
        return size;
    }

    /** Forget everything, as when leaving a world. */
    public void clear() {
        oldest.clear();
        head = 0;
        size = 0;
        minX = Integer.MAX_VALUE;
        minY = Integer.MAX_VALUE;
        minZ = Integer.MAX_VALUE;
        maxX = Integer.MIN_VALUE;
        maxY = Integer.MIN_VALUE;
        maxZ = Integer.MIN_VALUE;
    }

    /**
     * Whether a block was solid for movement in a frame, if a change since then says.
     *
     * @param x The block's x.
     * @param y The block's y.
     * @param z The block's z.
     * @param frame The command frame being stepped.
     * @return 1 if solid, 0 if not, or -1 if no kept change is stamped at or after the frame, so
     *     the blocks as they are now answer.
     */
    int solidAt(int x, int y, int z, long frame) {
        if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) {
            return -1;
        }
        int at = oldest.get(key(x, y, z));
        while (at != LongIntMap.MISSING) {
            if (frames[at] >= frame && xs[at] == x && ys[at] == y && zs[at] == z) {
                return wasSolid[at] ? 1 : 0;
            }
            at = next[at];
        }
        return -1;
    }

    /**
     * A view of some blocks as movement saw them in a frame, which can be moved to another frame
     * without making a new view.
     */
    public static final class AtFrame implements BlockView {

        /** The blocks as they are now. */
        private final BlockView now;

        /** The changes since. */
        private final BlockHistory history;

        /** The frame being stepped. */
        private long frame;

        /**
         * A view.
         *
         * @param now The blocks as they are now.
         * @param history The changes since.
         */
        public AtFrame(@NonNull BlockView now, @NonNull BlockHistory history) {
            this.now = now;
            this.history = history;
        }

        /**
         * Look at another frame.
         *
         * @param newFrame The command frame being stepped.
         */
        public void setFrame(long newFrame) {
            frame = newFrame;
        }

        @Override
        public boolean solid(int x, int y, int z) {
            final int then = history.solidAt(x, y, z, frame);
            return then < 0 ? now.solid(x, y, z) : then == 1;
        }
    }
}
