package com.ikalagaming.graphics.ui.spec;

import java.io.Serial;

/** A UI spec could not be loaded or opened, with where in the spec the problem is. */
public class SpecException extends RuntimeException {

    /** Generated ID. */
    @Serial private static final long serialVersionUID = 7206341139081236472L;

    /**
     * Create an exception.
     *
     * @param message Where in the spec the problem is, and what it is.
     */
    public SpecException(String message) {
        super(message);
    }

    /**
     * Create an exception with a cause.
     *
     * @param message Where in the spec the problem is, and what it is.
     * @param cause What caused it.
     */
    public SpecException(String message, Throwable cause) {
        super(message, cause);
    }
}
