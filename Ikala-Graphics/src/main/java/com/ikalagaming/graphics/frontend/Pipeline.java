package com.ikalagaming.graphics.frontend;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.ShaderMap;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;

/**
 * Represents the rendering pipeline. Not to be confused with things like a Vulkan pipeline, as this
 * handles all rendering of a frame from start to finish, involving potentially multiple stages,
 * shaders, and render targets.
 */
public interface Pipeline {
    /**
     * Initialize the pipeline to prepare for rendering for the first time.
     *
     * @param window The window we will be rendering to.
     * @param shaders The shaders to use for rendering.
     */
    void initialize(@NonNull Window window, @NonNull ShaderMap shaders);

    /**
     * Render a scene on the window.
     *
     * @param scene The scene to render.
     * @param shaders The shaders to use for rendering.
     */
    void render(Scene scene, ShaderMap shaders);
}
