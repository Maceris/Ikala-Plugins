package com.ikalagaming.graphics.backend.vulkan;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.ShaderMap;
import com.ikalagaming.graphics.frontend.Pipeline;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;

public class PipelineVulkan implements Pipeline {

    @Override
    public void initialize(@NonNull Window window, @NonNull ShaderMap shaders) {}

    @Override
    public void render(Scene scene, ShaderMap shaders) {}
}
