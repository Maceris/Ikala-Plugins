package com.ikalagaming.graphics.frontend;

import com.ikalagaming.graphics.Window;

import lombok.NonNull;

public interface Instance {

    /**
     * Initialize the instance to prepare for rendering for the first time.
     *
     * @param window The window we will be rendering to.
     */
    void initialize(@NonNull Window window);

    /** Clean up all the rendering resources. */
    void cleanup();

    /**
     * Do any periodic resource management that is required. Things like deleting resources in the
     * resource queue.
     */
    void processResources();

    TextureLoader getTextureLoader();

    Pipeline getPipeline();
}
