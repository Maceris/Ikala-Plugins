package com.ikalagaming.graphics.frontend.gui.util;

public class Hash {

    /**
     * Convenience method to hash a string.
     *
     * @param name The name to hash, in isolation (no parent ID is considered).
     * @return 0 if the name is null, the hashcode of the name if not null.
     */
    public static int getID(String name) {
        int hash = 0;
        if (name != null) {
            // TODO(ches) should we separate the idea of strings and IDs in general?
            if (name.contains("###")) {
                String subString = name.substring(0, name.indexOf("###"));
                hash = subString.hashCode();
            } else {
                hash = name.hashCode();
            }
        }
        return hash;
    }

    /**
     * Hash a name, given a parent ID.
     *
     * @param name The name.
     * @param parentID The parent ID.
     * @return A combined hash.
     */
    public static int getID(String name, int parentID) {
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
        return 31 * parentID + id;
    }

    /** Private constructor so this is not instantiated. */
    private Hash() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
