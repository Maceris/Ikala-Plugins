package com.ikalagaming.graphics.backend.base;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;

public interface RenderStage {
    enum Type {
        ANIMATION,
        FILTER,
        GUI,
        @Deprecated
        GUI_LEGACY,
        LIGHT,
        SCENE,
        SHADOW,
        SKYBOX
    }

    /** Set up the render stage. Must be called before rendering, should not be called twice. */
    default void initialize(@NonNull State state) {}

    /** Clean up any resources for the stage. Calling render is not valid after this point. */
    default void cleanup(@NonNull State state) {}

    // TODO(ches) fix this once we throw away OpenGL
    void render(Scene scene, @NonNull Window window, State state, int renderConfig);
}
