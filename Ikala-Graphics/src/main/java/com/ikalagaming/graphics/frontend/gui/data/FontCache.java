package com.ikalagaming.graphics.frontend.gui.data;

public class FontCache {
    public static class CacheElement {
        /** Previous element in the doubly-linked list. */
        public CacheElement previous;

        /** Next element in the doubly-linked list. */
        public CacheElement next;

        /** The next value in the case of collisions. */
        public CacheElement chain;

        /** The actual character value. */
        public final char value;

        // TODO(ches) should we just do an index? does this manage the texture, and thus need a
        // size??
        public final int valueX;
        public final int valueY;

        public CacheElement(final char value, final int x, final int y) {
            this.previous = null;
            this.next = null;
            this.chain = null;
            this.value = value;
            this.valueX = x;
            this.valueY = y;
        }
    }

    public static int hashCharacter(char c) {
        // TODO(ches) hash, probably also include multi-characters in hash
        return 0;
    }

    /**
     * Given 12pt font characters are roughly 16x16px, this is how many would fit in a 2048x2048
     * texture. It's important this number be a power of 2 size, so we can just lop off bits from a
     * hash to get the cache value. Larger entries might not fit in the texture (larger font,
     * combined ligatures), so this is only the size of the lookup table, not how many characters
     * fit in the atlas. That has to be determined by actually looking for space.
     */
    private static final int CACHE_SIZE = 1 << 14;

    private int size;
    private CacheElement[] hash;

    private final CacheElement head;
    private final CacheElement tail;

    public FontCache() {
        head = new CacheElement(' ', -1, -1);
        tail = new CacheElement(' ', -1, -1);

        head.next = tail;
        head.previous = null;
        tail.next = null;
        tail.previous = head;
        size = 0;
        hash = new CacheElement[CACHE_SIZE];
    }

    /**
     * Add a character to the cache.
     *
     * @param c The character to add.
     * @return True if it is freshly added, false if it was already in the cache.
     */
    public boolean add(char c, int fontSize) {
        // TODO(ches) do this

        // hash char
        // check if null in array there
        // If not null, and char matches, move to the "most recently used" part of the list
        // If null, need to insert and add to most recently used part of list
        return true;
    }

    private void moveToFront(CacheElement element) {
        // TODO(ches) do this
    }

    private void remove(CacheElement element) {
        // TODO(ches) do this
    }
}
