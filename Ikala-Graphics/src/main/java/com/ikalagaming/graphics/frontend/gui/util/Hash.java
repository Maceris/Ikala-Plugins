package com.ikalagaming.graphics.frontend.gui.util;

public class Hash {

    public static int getID(String name) {
        return getID(name, 0);
    }

    public static int getID(String name, int parentID) {
        int hash = 0;
        if (name != null) {
            hash = name.hashCode();
        }

        return 31 * parentID + hash;
    }
}
