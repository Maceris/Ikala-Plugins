package com.ikalagaming.graphics.frontend;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;

public interface Instance {

    /**
     * Initialize the instance to prepare for rendering for the first time.
     *
     * @param window The window we will be rendering to.
     * @return Whether we were successful. False if there were (unrecoverable) errors.
     */
    boolean initialize(@NonNull Window window);

    /** Clean up all the rendering resources. */
    void cleanup();

    /**
     * Do any periodic resource management that is required. Things like deleting resources in the
     * resource queue.
     */
    void processResources();

    /**
     * Return the texture loader for this instance.
     *
     * @return The texture loader.
     */
    TextureLoader getTextureLoader();

    /**
     * Render a scene on the window.
     *
     * @param scene The scene to render.
     */
    void render(@NonNull Scene scene);

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

    /**
     * Set up a model before rendering for the first time. For example, creating buffers. This must only be called
     * once for a model, and only after it's fully loaded (e.g. animations set up).
     *
     * @param model The model to set up.
     */
    void initialize(@NonNull Model model);

    /**
     * Change over to another rendering pipeline.
     *
     * @param config The configuration specifying the pipeline to switch to.
     * @see RenderConfig For details on obtaining the values to pass here.
     */
    void swapPipeline(final int config);
}
