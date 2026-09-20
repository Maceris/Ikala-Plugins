package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;

import lombok.NonNull;

import java.nio.*;

public class BufferUtilOpenGL {

    /**
     * Convert the buffer type to the underlying OpenGL constant.
     *
     * @param type The buffer type.
     * @return The OpenGL constant that is equivalent to the buffer type.
     */
    public static int mapBufferType(@NonNull BufferOpenGL.Type type) {
        return switch (type) {
            case DRAW_INDIRECT -> GL_DRAW_INDIRECT_BUFFER;
            case INDEXES -> GL_ELEMENT_ARRAY_BUFFER;
            case SHADER_STORAGE -> GL_SHADER_STORAGE_BUFFER;
            case UNIFORM -> GL_UNIFORM_BUFFER;
        };
    }

    public static void bindBuffer(@NonNull BufferOpenGL buffer) {
        final int type = mapBufferType(buffer.type());
        glBindBuffer(type, (int) buffer.id());
    }

    public static void bindBuffer(@NonNull BufferOpenGL buffer, int index) {
        final int type = mapBufferType(buffer.type());
        glBindBufferBase(type, index, (int) buffer.id());
    }

    public static void bufferData(@NonNull BufferOpenGL buffer, long data, int usage) {
        final int type = mapBufferType(buffer.type());
        glBufferData(type, data, usage);
    }

    public static void bufferData(
            @NonNull BufferOpenGL buffer, @NonNull ByteBuffer data, int usage) {
        final int type = mapBufferType(buffer.type());
        glBufferData(type, data, usage);
    }

    public static void bufferData(
            @NonNull BufferOpenGL buffer, @NonNull ShortBuffer data, int usage) {
        final int type = mapBufferType(buffer.type());
        glBufferData(type, data, usage);
    }

    public static void bufferData(
            @NonNull BufferOpenGL buffer, @NonNull IntBuffer data, int usage) {
        final int type = mapBufferType(buffer.type());
        glBufferData(type, data, usage);
    }

    public static void bufferData(
            @NonNull BufferOpenGL buffer, @NonNull FloatBuffer data, int usage) {
        final int type = mapBufferType(buffer.type());
        glBufferData(type, data, usage);
    }

    public static void bufferData(
            @NonNull BufferOpenGL buffer, @NonNull LongBuffer data, int usage) {
        final int type = mapBufferType(buffer.type());
        glBufferData(type, data, usage);
    }

    public static void bufferData(
            @NonNull BufferOpenGL buffer, @NonNull DoubleBuffer data, int usage) {
        final int type = mapBufferType(buffer.type());
        glBufferData(type, data, usage);
    }

    public static void bufferData(@NonNull BufferOpenGL buffer, short[] data, int usage) {
        final int type = mapBufferType(buffer.type());
        glBufferData(type, data, usage);
    }

    public static void bufferData(@NonNull BufferOpenGL buffer, int[] data, int usage) {
        final int type = mapBufferType(buffer.type());
        glBufferData(type, data, usage);
    }

    public static void bufferData(@NonNull BufferOpenGL buffer, float[] data, int usage) {
        final int type = mapBufferType(buffer.type());
        glBufferData(type, data, usage);
    }

    public static void bufferData(@NonNull BufferOpenGL buffer, long[] data, int usage) {
        final int type = mapBufferType(buffer.type());
        glBufferData(type, data, usage);
    }

    public static void bufferData(@NonNull BufferOpenGL buffer, double[] data, int usage) {
        final int type = mapBufferType(buffer.type());
        glBufferData(type, data, usage);
    }

    public static BufferOpenGL createBuffer(BufferOpenGL.@NonNull Type type) {
        return new BufferOpenGL(glGenBuffers(), type);
    }

    public static void deleteBuffer(@NonNull BufferOpenGL buffer) {
        glDeleteBuffers((int) buffer.id());
    }

    public static void unbindBuffer(@NonNull BufferOpenGL buffer) {
        final int type = mapBufferType(buffer.type());
        glBindBuffer(type, 0);
    }

    public static void unbindBuffer(@NonNull BufferOpenGL buffer, int index) {
        final int type = mapBufferType(buffer.type());
        glBindBufferBase(type, index, 0);
    }
}
