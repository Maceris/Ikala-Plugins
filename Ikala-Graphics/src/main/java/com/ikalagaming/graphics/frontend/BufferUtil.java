package com.ikalagaming.graphics.frontend;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.backend.opengl.BufferUtilOpenGL;
import com.ikalagaming.graphics.backend.vulkan.BufferUtilVulkan;

import lombok.NonNull;

public interface BufferUtil {
    /** A static instance based on the backend type. */
    BufferUtil _INSTANCE =
            switch (GraphicsManager.getBackendType()) {
                case OPENGL -> new BufferUtilOpenGL();
                case VULKAN -> new BufferUtilVulkan();
            };

    /**
     * Fetch the appropriate instance based on the backend type.
     *
     * @return The buffer util instance that works on the current backend.
     */
    static BufferUtil getInstance() {
        return _INSTANCE;
    }

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
