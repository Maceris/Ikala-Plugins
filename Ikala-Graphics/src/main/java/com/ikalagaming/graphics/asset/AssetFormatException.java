package com.ikalagaming.graphics.asset;

import java.io.Serial;

/** An asset container could not be read or written because it breaks the container format. */
public class AssetFormatException extends RuntimeException {

    /** Generated ID. */
    @Serial private static final long serialVersionUID = 4123170596321508712L;

    /**
     * Create an exception with a message.
     *
     * @param message What is wrong with the container.
     */
    public AssetFormatException(String message) {
        super(message);
    }

    /**
     * Create an exception with a message and a cause.
     *
     * @param message What is wrong with the container.
     * @param cause What caused the problem.
     */
    public AssetFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
