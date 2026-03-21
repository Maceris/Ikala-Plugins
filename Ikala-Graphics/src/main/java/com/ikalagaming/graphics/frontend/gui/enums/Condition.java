package com.ikalagaming.graphics.frontend.gui.enums;

public enum Condition {
    /** No condition (always set the variable), same as always. */
    NONE,
    /** No condition (always set the variable), same as none. */
    ALWAYS,
    /** Set the variable once per runtime session. Only the first call will succeed. */
    ONCE,
    /** Set the variable if the object has no persistently saved data. */
    FIRST_USE_EVER,
    /**
     * Set the variable if the object has is appearing for the first time, or is appearing after
     * being hidden or inactive.
     */
    APPEARING,
}
