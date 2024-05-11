package com.ikalagaming.graphics.backend.base;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.graph.GBuffer;

import lombok.NonNull;

/** Defines the methods that are required for dealing with GBuffers in each backend. */
public interface GBufferHandler {
    /**
     * Initialize the buffers, setting up for use.
     *
     * @param buffer The GBuffer to set up.
     * @param window Window information to pull size details from.
     */
    void initialize(@NonNull GBuffer buffer, @NonNull Window window);

    /**
     * Clean up the buffer resources.
     *
     * @param buffer The GBuffer to clean up.
     */
    void cleanup(@NonNull GBuffer buffer);
}
