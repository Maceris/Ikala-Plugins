package com.ikalagaming.graphics.frontend;

import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;

public interface RenderStage {
    void render(@NonNull Scene scene);
}
