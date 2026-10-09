package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PipelineVulkan {

    /** How many lights of each type (spot, point) that are currently supported. */
    public static final int MAX_LIGHTS_SUPPORTED = 1000;

    /** The list of render stages that this renderer uses. */
    private final RenderStage[] renderStages;

    /** The render config that was used to build this pipeline. */
    private final int renderConfig;

    /**
     * Run each render stage in order.
     *
     * @param scene The scene to render.
     * @param window The window we are rendering to.
     * @param state The Vulkan state.
     */
    public void render(Scene scene, @NonNull Window window, @NonNull VulkanState state) {
        for (RenderStage stage : renderStages) {
            stage.render(scene, window, state, renderConfig);
        }
    }
}
