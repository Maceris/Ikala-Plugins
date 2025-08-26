package com.ikalagaming.graphics.frontend;

/**
 * Represents a buffer.
 *
 * @param id The unique id of the buffer.
 * @param type The type of buffer.
 */
public record Buffer(long id, Type type) {
    /** The different kinds of buffer that are supported. */
    public enum Type {
        INDEXES,
        SHADER_STORAGE,
        UNIFORM,
    }
}
