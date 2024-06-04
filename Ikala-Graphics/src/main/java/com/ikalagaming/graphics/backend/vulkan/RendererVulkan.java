package com.ikalagaming.graphics.backend.vulkan;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.ShaderCache;
import com.ikalagaming.graphics.frontend.Renderer;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;

public class RendererVulkan implements Renderer {

    @Override
    public void initialize(@NonNull Window window) {}

    @Override
    public void cleanup() {}

    @Override
    public void render(@NonNull Scene scene, @NonNull ShaderCache shaders) {}

    @Override
    public void resize(int width, int height) {}

    @Override
    public void setupData(@NonNull Scene scene, @NonNull ShaderCache shaders) {}
}
