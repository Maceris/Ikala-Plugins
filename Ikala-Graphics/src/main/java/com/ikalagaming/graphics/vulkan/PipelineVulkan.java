package com.ikalagaming.graphics.vulkan;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.lwjgl.vulkan.VkCommandBuffer;

@RequiredArgsConstructor
public class PipelineVulkan {

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
        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[state.frameIndex];
        for (RenderStage stage : renderStages) {
            stage.render(scene, window, state, renderConfig);
            state.frameTimings.mark(commandBuffer, state.frameIndex, stage.name());
        }
    }
}
