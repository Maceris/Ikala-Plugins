package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;

public interface RenderStage {
    enum Type {
        ANIMATION,
        CULL,
        DEBUG,
        DEBUG_NORMALS,
        FILTER,
        GUI,
        INSTANCES,
        LIGHT,
        SCENE,
        SHADOW,
        SKYBOX
    }

    /** Set up the render stage. Must be called before rendering, should not be called twice. */
    default void initialize(@NonNull VulkanState state) {}

    /** Clean up any resources for the stage. Calling render is not valid after this point. */
    default void cleanup(@NonNull VulkanState state) {}

    void render(Scene scene, @NonNull Window window, @NonNull VulkanState state, int renderConfig);
}
