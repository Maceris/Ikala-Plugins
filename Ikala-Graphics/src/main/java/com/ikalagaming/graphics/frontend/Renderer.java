package com.ikalagaming.graphics.frontend;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.util.SafeResourceLoader;

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

    @Getter
    @Setter
    class RenderConfig {
        /** Enable wireframe. */
        @Deprecated private boolean wireframe;

        /** Post-processing filter that has been selected. */
        private int selectedFilter;

        /** Whether we are actually drawing the scene. */
        @Deprecated private boolean renderingScene;

        /**
         * The list of filter names that are available. We use an array to make ImGui access easier.
         */
        private String[] filterNames;

        /**
         * Sets the post-processing filter. Must be a valid index in the array of filters or an
         * exception will be thrown.
         *
         * @param newFilter The index of the filter to use.
         */
        public void setSelectedFilter(int newFilter) {
            if (newFilter < 0 || newFilter > filterNames.length) {
                throw new IllegalArgumentException(
                        SafeResourceLoader.getStringFormatted(
                                "ILLEGAL_FILTER_SELECTION",
                                GraphicsPlugin.getResourceBundle(),
                                newFilter + "",
                                filterNames.length + ""));
            }
            selectedFilter = newFilter;
        }
    }

    /** Rendering configurations. */
    RenderConfig configuration = new RenderConfig();
}
