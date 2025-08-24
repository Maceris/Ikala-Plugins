package com.ikalagaming.graphics.backend.opengl;

import com.ikalagaming.graphics.frontend.Buffer;
import lombok.NonNull;

import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;

public class BufferUtil {

    /**
     * Convert the buffer type to the underlying OpenGL constant.
     * @param type The buffer type.
     * @return The OpenGL constant that is equivalent to the buffer type.
     */
    public static int mapBufferType(@NonNull Buffer.Type type) {
        return switch (type) {
            case UNIFORM -> GL_UNIFORM_BUFFER;
            case SHADER_STORAGE -> GL_SHADER_STORAGE_BUFFER;
        };
    }

    /**
     * Bind the specified buffer at the provided index.
     *
     * @param buffer THe buffer to bind.
     * @param index The index to bind to.
     */
    public static void bind(@NonNull Buffer buffer, int index) {
        int type = mapBufferType(buffer.type());
        glBindBufferBase(type, index, (int) buffer.id());
    }

    /**
     * Bind the specified buffer.
     *
     * @param buffer The buffer to bind.
     */
    public static void bind(@NonNull Buffer buffer) {
        int type = mapBufferType(buffer.type());
        glBindBuffer(type, (int) buffer.id());
    }

    /** Private constructor so this is not instantiated. */
    private BufferUtil() {
        throw new UnsupportedOperationException("This utility class should not be instantiated");
    }
}
