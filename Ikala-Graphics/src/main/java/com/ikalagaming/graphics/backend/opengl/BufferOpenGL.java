package com.ikalagaming.graphics.backend.opengl;

import com.ikalagaming.graphics.frontend.Buffer;

/**
 * Represents a buffer.
 *
 * @param id The unique id of the buffer.
 * @param type The type of buffer.
 */
public record BufferOpenGL(long id, Type type) implements Buffer {
    /** The different kinds of buffer that are supported. */
    public enum Type {
        DRAW_INDIRECT,
        INDEXES,
        SHADER_STORAGE,
        UNIFORM,
    }
}
