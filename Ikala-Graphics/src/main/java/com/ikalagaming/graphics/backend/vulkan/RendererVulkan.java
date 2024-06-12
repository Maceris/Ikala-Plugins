package com.ikalagaming.graphics.backend.vulkan;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.ShaderMap;
import com.ikalagaming.graphics.frontend.Renderer;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;

public class RendererVulkan implements Renderer {

    @Override
    public void initialize(@NonNull Window window, @NonNull ShaderMap shaders) {}

    @Override
    public void cleanup() {}

    @Override
    public void render(@NonNull Scene scene, @NonNull ShaderMap shaders) {}

    @Override
    public void resize(int width, int height) {}

    @Override
    public void setupData(@NonNull Scene scene, @NonNull ShaderMap shaders) {}
}
