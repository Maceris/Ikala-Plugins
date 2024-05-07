package com.ikalagaming.graphics.backend.base;

import com.ikalagaming.graphics.frontend.Texture;

import lombok.NonNull;

import java.nio.ByteBuffer;

public interface TextureHandler {
    void bind(@NonNull Texture texture);

    void cleanup(@NonNull Texture texture);

    Texture load(@NonNull ByteBuffer buffer, final int width, final int height);

    Texture load(@NonNull String texturePath);
}
