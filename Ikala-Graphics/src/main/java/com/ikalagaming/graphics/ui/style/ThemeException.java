package com.ikalagaming.graphics.ui.style;

import java.io.Serial;

/** A theme could not be loaded or used, with the place in the theme and the reason. */
public class ThemeException extends RuntimeException {

    /** Generated ID. */
    @Serial private static final long serialVersionUID = -2361482950213478823L;

    /**
     * Create an exception.
     *
     * @param message Where in the theme the problem is, and what it is.
     */
    public ThemeException(String message) {
        super(message);
    }

    /**
     * Create an exception with a cause.
     *
     * @param message Where in the theme the problem is, and what it is.
     * @param cause What caused it.
     */
    public ThemeException(String message, Throwable cause) {
        super(message, cause);
    }
}
