package com.ikalagaming.graphics.frontend.gui.data;

/** Used for storing an invalid selection constant. */
public class SelectionUserData {

    public static final Object INVALID = new InvalidClass();

    /** Makes sure we can't have something equal to INVALID. */
    private static class InvalidClass {
        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof InvalidClass;
        }

        InvalidClass() {}
    }

    private SelectionUserData() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
