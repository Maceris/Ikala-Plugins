package com.ikalagaming.graphics.backend.vulkan;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.base.ShaderMap;
import com.ikalagaming.graphics.backend.base.State;
import com.ikalagaming.graphics.frontend.Pipeline;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PipelineVulkan implements Pipeline {

    /** How many lights of each type (spot, point) that are currently supported. */
    public static final int MAX_LIGHTS_SUPPORTED = 1000;

    /** The list of render stages that this renderer uses. */
    private final RenderStage[] renderStages;

    @Override
    public void initialize(@NonNull Window window, @NonNull ShaderMap shaders) {}

    @Override
    public void render(Scene scene, ShaderMap shaders, @NonNull Window window, State state) {
        for (RenderStage stage : renderStages) {
            stage.render(scene, window, state);
        }
    }
}
