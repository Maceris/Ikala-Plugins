package com.ikalagaming.graphics.backend.base;

import com.ikalagaming.graphics.scene.Scene;

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

    void render(Scene scene);
}
