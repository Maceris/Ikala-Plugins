package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;

import com.ikalagaming.graphics.frontend.Buffer;
import com.ikalagaming.graphics.frontend.BufferUtil;

import lombok.NonNull;

import java.nio.*;

public class BufferUtilOpenGL implements BufferUtil {
    /**
     * Convert the buffer type to the underlying OpenGL constant.
     *
     * @param type The buffer type.
     * @return The OpenGL constant that is equivalent to the buffer type.
     */
    public static int mapBufferType(@NonNull Buffer.Type type) {
        return switch (type) {
            case DRAW_INDIRECT -> GL_DRAW_INDIRECT_BUFFER;
            case INDEXES -> GL_ELEMENT_ARRAY_BUFFER;
            case SHADER_STORAGE -> GL_SHADER_STORAGE_BUFFER;
            case UNIFORM -> GL_UNIFORM_BUFFER;
        };
    }

    @Override
    public void bindBuffer(@NonNull Buffer buffer) {
        final int type = mapBufferType(buffer.type());
        glBindBuffer(type, (int) buffer.id());
    }

    @Override
    public void bindBuffer(@NonNull Buffer buffer, int index) {
        final int type = mapBufferType(buffer.type());
        glBindBufferBase(type, index, (int) buffer.id());
    }

    public void bufferData(@NonNull Buffer buffer, long data, int usage) {
        final int type = mapBufferType(buffer.type());
        glBufferData(type, data, usage);
    }

    public void bufferData(@NonNull Buffer buffer, @NonNull ByteBuffer data, int usage) {
        final int type = mapBufferType(buffer.type());
        glBufferData(type, data, usage);
    }

    public void bufferData(@NonNull Buffer buffer, @NonNull ShortBuffer data, int usage) {
        final int type = mapBufferType(buffer.type());
        glBufferData(type, data, usage);
    }

    public void bufferData(@NonNull Buffer buffer, @NonNull IntBuffer data, int usage) {
        final int type = mapBufferType(buffer.type());
        glBufferData(type, data, usage);
    }

    public void bufferData(@NonNull Buffer buffer, @NonNull FloatBuffer data, int usage) {
        final int type = mapBufferType(buffer.type());
        glBufferData(type, data, usage);
    }

    public void bufferData(@NonNull Buffer buffer, @NonNull LongBuffer data, int usage) {
        final int type = mapBufferType(buffer.type());
        glBufferData(type, data, usage);
    }

    public void bufferData(@NonNull Buffer buffer, @NonNull DoubleBuffer data, int usage) {
        final int type = mapBufferType(buffer.type());
        glBufferData(type, data, usage);
    }

    public void bufferData(@NonNull Buffer buffer, short[] data, int usage) {
        final int type = mapBufferType(buffer.type());
        glBufferData(type, data, usage);
    }

    public void bufferData(@NonNull Buffer buffer, int[] data, int usage) {
        final int type = mapBufferType(buffer.type());
        glBufferData(type, data, usage);
    }

    public void bufferData(@NonNull Buffer buffer, float[] data, int usage) {
        final int type = mapBufferType(buffer.type());
        glBufferData(type, data, usage);
    }

    public void bufferData(@NonNull Buffer buffer, long[] data, int usage) {
        final int type = mapBufferType(buffer.type());
        glBufferData(type, data, usage);
    }

    public void bufferData(@NonNull Buffer buffer, double[] data, int usage) {
        final int type = mapBufferType(buffer.type());
        glBufferData(type, data, usage);
    }

    @Override
    public Buffer createBuffer(Buffer.@NonNull Type type) {
        return new Buffer(glGenBuffers(), type);
    }

    @Override
    public void deleteBuffer(@NonNull Buffer buffer) {
        glDeleteBuffers((int) buffer.id());
    }

    @Override
    public void unbindBuffer(@NonNull Buffer buffer) {
        final int type = mapBufferType(buffer.type());
        glBindBuffer(type, 0);
    }

    @Override
    public void unbindBuffer(@NonNull Buffer buffer, int index) {
        final int type = mapBufferType(buffer.type());
        glBindBufferBase(type, index, 0);
    }
}
