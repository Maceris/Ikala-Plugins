package com.ikalagaming.graphics.frontend;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.backend.opengl.BufferUtilOpenGL;
import com.ikalagaming.graphics.backend.vulkan.BufferUtilVulkan;

import lombok.NonNull;

import java.nio.*;

public interface BufferUtil {
    /** A static instance based on the backend type. */
    BufferUtil INSTANCE =
            switch (GraphicsManager.getBackendType()) {
                case OPENGL -> new BufferUtilOpenGL();
                case VULKAN -> new BufferUtilVulkan();
            };

    /**
     * Bind a buffer based on the type.
     *
     * @param buffer The buffer to bind.
     */
    void bindBuffer(@NonNull Buffer buffer);

    /**
     * Bind a buffer to a specific binding index.
     *
     * @param buffer The buffer to bind.
     * @param index The index to bind.
     */
    void bindBuffer(@NonNull Buffer buffer, int index);

    /**
     * Send data to the currently bound buffer (make sure to bind first) using glBufferData, given
     * the type of buffer.
     *
     * @param buffer The buffer to get type info from.
     * @param data The data to buffer.
     * @param usage Expected usage patter of the buffer.
     * @see #bindBuffer(Buffer)
     */
    void bufferData(@NonNull Buffer buffer, long data, int usage);

    /**
     * Send data to the currently bound buffer (make sure to bind first) using glBufferData, given
     * the type of buffer.
     *
     * @param buffer The buffer to get type info from.
     * @param data The data to buffer.
     * @param usage Expected usage patter of the buffer.
     * @see #bindBuffer(Buffer)
     */
    void bufferData(@NonNull Buffer buffer, @NonNull ByteBuffer data, int usage);

    /**
     * Send data to the currently bound buffer (make sure to bind first) using glBufferData, given
     * the type of buffer.
     *
     * @param buffer The buffer to get type info from.
     * @param data The data to buffer.
     * @param usage Expected usage patter of the buffer.
     * @see #bindBuffer(Buffer)
     */
    void bufferData(@NonNull Buffer buffer, @NonNull ShortBuffer data, int usage);

    /**
     * Send data to the currently bound buffer (make sure to bind first) using glBufferData, given
     * the type of buffer.
     *
     * @param buffer The buffer to get type info from.
     * @param data The data to buffer.
     * @param usage Expected usage patter of the buffer.
     * @see #bindBuffer(Buffer)
     */
    void bufferData(@NonNull Buffer buffer, @NonNull IntBuffer data, int usage);

    /**
     * Send data to the currently bound buffer (make sure to bind first) using glBufferData, given
     * the type of buffer.
     *
     * @param buffer The buffer to get type info from.
     * @param data The data to buffer.
     * @param usage Expected usage patter of the buffer.
     * @see #bindBuffer(Buffer)
     */
    void bufferData(@NonNull Buffer buffer, @NonNull FloatBuffer data, int usage);

    /**
     * Send data to the currently bound buffer (make sure to bind first) using glBufferData, given
     * the type of buffer.
     *
     * @param buffer The buffer to get type info from.
     * @param data The data to buffer.
     * @param usage Expected usage patter of the buffer.
     * @see #bindBuffer(Buffer)
     */
    void bufferData(@NonNull Buffer buffer, @NonNull LongBuffer data, int usage);

    /**
     * Send data to the currently bound buffer (make sure to bind first) using glBufferData, given
     * the type of buffer.
     *
     * @param buffer The buffer to get type info from.
     * @param data The data to buffer.
     * @param usage Expected usage patter of the buffer.
     * @see #bindBuffer(Buffer)
     */
    void bufferData(@NonNull Buffer buffer, @NonNull DoubleBuffer data, int usage);

    /**
     * Send data to the currently bound buffer (make sure to bind first) using glBufferData, given
     * the type of buffer.
     *
     * @param buffer The buffer to get type info from.
     * @param data The data to buffer.
     * @param usage Expected usage patter of the buffer.
     * @see #bindBuffer(Buffer)
     */
    void bufferData(@NonNull Buffer buffer, short[] data, int usage);

    /**
     * Send data to the currently bound buffer (make sure to bind first) using glBufferData, given
     * the type of buffer.
     *
     * @param buffer The buffer to get type info from.
     * @param data The data to buffer.
     * @param usage Expected usage patter of the buffer.
     * @see #bindBuffer(Buffer)
     */
    void bufferData(@NonNull Buffer buffer, int[] data, int usage);

    /**
     * Send data to the currently bound buffer (make sure to bind first) using glBufferData, given
     * the type of buffer.
     *
     * @param buffer The buffer to get type info from.
     * @param data The data to buffer.
     * @param usage Expected usage patter of the buffer.
     * @see #bindBuffer(Buffer)
     */
    void bufferData(@NonNull Buffer buffer, float[] data, int usage);

    /**
     * Send data to the currently bound buffer (make sure to bind first) using glBufferData, given
     * the type of buffer.
     *
     * @param buffer The buffer to get type info from.
     * @param data The data to buffer.
     * @param usage Expected usage patter of the buffer.
     * @see #bindBuffer(Buffer)
     */
    void bufferData(@NonNull Buffer buffer, long[] data, int usage);

    /**
     * Send data to the currently bound buffer (make sure to bind first) using glBufferData, given
     * the type of buffer.
     *
     * @param buffer The buffer to get type info from.
     * @param data The data to buffer.
     * @param usage Expected usage patter of the buffer.
     * @see #bindBuffer(Buffer)
     */
    void bufferData(@NonNull Buffer buffer, double[] data, int usage);

    /**
     * Create a buffer of the specified type.
     *
     * @param type The buffer type to create.
     * @return The newly created buffer.
     */
    Buffer createBuffer(@NonNull Buffer.Type type);

    /**
     * Delete the provided buffer. It should not be used after this point.
     *
     * @param buffer The buffer to free.
     */
    void deleteBuffer(@NonNull Buffer buffer);

    /**
     * Unbinds the buffer, generally equivalent to binding 0.
     *
     * @param buffer The buffer to unbind.
     */
    void unbindBuffer(@NonNull Buffer buffer);

    /**
     * Unbinds the buffer from the provided index, generally equivalent to binding 0.
     *
     * @param buffer The buffer to unbind.
     * @param index The index to unbind.
     */
    void unbindBuffer(@NonNull Buffer buffer, int index);
}
