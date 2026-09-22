package com.ikalagaming.graphics.backend.vulkan;

import com.ikalagaming.graphics.frontend.Format;
import com.ikalagaming.graphics.frontend.Texture;
import com.ikalagaming.graphics.frontend.TextureLoader;

import lombok.NonNull;

import java.nio.ByteBuffer;

public class TextureLoaderVulkan implements TextureLoader {
    @Override
    public Texture loadBindless(ByteBuffer buffer, @NonNull Format format, int width, int height) {
        // TODO(ches) implement this
        TextureInfoVulkan info = new TextureInfoVulkan();
        return new Texture(0, 0, info);
    }

    @Override
    public Texture load(ByteBuffer buffer, @NonNull Format format, int width, int height) {
        // TODO(ches) implement this
        TextureInfoVulkan info = new TextureInfoVulkan();
        return new Texture(0, 0, info);
    }

    @Override
    public Texture loadBindless(@NonNull String texturePath) {
        // TODO(ches) implement this
        TextureInfoVulkan info = new TextureInfoVulkan();
        return new Texture(0, 0, info);
    }

    @Override
    public Texture load(@NonNull String texturePath) {
        // TODO(ches) implement this
        TextureInfoVulkan info = new TextureInfoVulkan();
        return new Texture(0, 0, info);
    }
}
