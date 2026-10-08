package com.ikalagaming.graphics.frontend.gui.util;

public class Hash {

    /**
     * Convenience method to hash a string. If the string contains "###", only the part starting at
     * the last "###" is used, so that labels can change while keeping the same ID (e.g. "Score:
     * 10###ID" and "Score: 12###ID" have the same ID). Like upstream, each "###" restarts the hash,
     * so "Label###A###B" has the same ID as "###B".
     *
     * @param name The name to hash, in isolation (no parent ID is considered).
     * @return 0 if the name is null, the hashcode of the name if not null.
     */
    public static int getID(String name) {
        int hash = 0;
        if (name != null) {
            final int idStart = name.lastIndexOf("###");
            if (idStart >= 0) {
                hash = name.substring(idStart).hashCode();
            } else {
                hash = name.hashCode();
            }
        }
        return mix(hash);
    }

    /**
     * Mix the bits of a hash, so that similar inputs give unrelated IDs. String.hashCode() is
     * linear, so without this "table2" would hash to exactly one more than "table1". ImGui derives
     * some IDs by adding small offsets to an ID (like a table ID + 1 + the column index for resize
     * borders), which relies on IDs being well distributed so they don't collide. This is the
     * MurmurHash3 finalizer, which maps 0 to 0.
     *
     * @param hash The hash.
     * @return The mixed hash.
     */
    private static int mix(int hash) {
        int h = hash;
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return h;
    }

    /**
     * Find the part of a label that should be displayed, which excludes anything from "##" onward.
     *
     * @param label The label.
     * @return The displayed part of the label.
     */
    public static String getDisplayedText(String label) {
        if (label == null) {
            return "";
        }
        final int hidden = label.indexOf("##");
        return hidden >= 0 ? label.substring(0, hidden) : label;
    }

    /**
     * Hash a name, given a parent ID. As in ImGui, an empty name produces the parent ID, so {@code
     * pushID(label)} followed by an item with an empty label has the same ID as the label would.
     *
     * @param name The name.
     * @param parentID The parent ID.
     * @return A combined hash.
     */
    public static int getID(String name, int parentID) {
        if (name != null && name.isEmpty()) {
            return parentID;
        }
        // Names at the root, like window names, hash the same as on their own
        if (parentID == 0) {
            return getID(name);
        }
        return getID(getID(name), parentID);
    }

    /**
     * Hash an ID, given a parent ID.
     *
     * @param id The ID.
     * @param parentID The parent ID.
     * @return A combined hash.
     */
    public static int getID(int id, int parentID) {
        return mix(31 * parentID + id);
    }

    /** Private constructor so this is not instantiated. */
    private Hash() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
