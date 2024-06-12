package com.ikalagaming.graphics.frontend;

import com.ikalagaming.graphics.scene.Scene;

public interface RenderStage {
    enum Type {
        ANIMATION,
        FILTER,
        GUI,
        LIGHT,
        SCENE,
        SHADOW,
        SKYBOX
    }

    void render(Scene scene);
}
