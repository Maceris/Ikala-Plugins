package com.ikalagaming.graphics.frontend;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.ShaderMap;
import com.ikalagaming.graphics.scene.Scene;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public interface Renderer {
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
     * @param scene The scene to render.
     * @param shaders The shaders to use for rendering.
     */
    void render(@NonNull Scene scene, @NonNull ShaderMap shaders);

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
     * @param shaders The shaders that will be used for rendering.
     */
    @Deprecated
    void setupData(@NonNull Scene scene, @NonNull ShaderMap shaders);

    @Getter
    @Setter
    @Deprecated
    class RenderConfig {
        /** Enable wireframe. */
        @Deprecated private boolean wireframe;

        /** Post-processing filter that has been selected. */
        @Deprecated private int selectedFilter;

        /** Whether we are actually drawing the scene. */
        @Deprecated private boolean renderingScene;
    }

    /** Rendering configurations. */
    @Deprecated RenderConfig configuration = new RenderConfig();
}
