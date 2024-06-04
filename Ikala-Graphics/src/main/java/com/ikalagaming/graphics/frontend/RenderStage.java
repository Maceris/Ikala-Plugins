package com.ikalagaming.graphics.frontend;

import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;

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

    void render(@NonNull Scene scene);
}
