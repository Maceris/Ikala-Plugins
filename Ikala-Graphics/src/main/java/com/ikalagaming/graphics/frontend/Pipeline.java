package com.ikalagaming.graphics.frontend;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;

public interface Pipeline {
    /**
     * Initialize the pipeline to prepare for rendering for the first time.
     *
     * @param window The window we will be rendering to.
     */
    void initialize(@NonNull Window window);

    /** Clean up all the rendering resources. */
    void cleanup();

    /**
     * Render a scene on the window.
     *
     * @param window The window we are drawing in.
     * @param scene The scene to render.
     */
    void render(@NonNull Window window, @NonNull Scene scene);

    /**
     * Update the buffers and GUI when we resize the screen.
     *
     * @param width The new screen width in pixels.
     * @param height The new screen height in pixels.
     */
    void resize(int width, int height);

    /**
     * Set up model data before rendering.
     *
     * @param scene The scene to read models from.
     */
    @Deprecated
    void setupData(@NonNull Scene scene);
}
