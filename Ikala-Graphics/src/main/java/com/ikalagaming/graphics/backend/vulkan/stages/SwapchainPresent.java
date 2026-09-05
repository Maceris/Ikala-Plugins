package com.ikalagaming.graphics.backend.vulkan.stages;

import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK13.*;
import static org.lwjgl.vulkan.VK13.vkCmdPipelineBarrier2;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.base.State;
import com.ikalagaming.graphics.backend.vulkan.VulkanState;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier2;
import org.lwjgl.vulkan.VkImageSubresourceRange;

public class SwapchainPresent implements RenderStage {

    @Override
    public void initialize(@NonNull State state) {
        RenderStage.super.initialize(state);
    }

    @Override
    public void cleanup(@NonNull State state) {
        RenderStage.super.cleanup(state);
    }

    @Override
    public void render(Scene scene, @NonNull Window window, State state, int renderConfig) {
        // TODO(ches) remove this when we don't have the nothingburger state?
        VulkanState vulkanState = (VulkanState) state;
        final VkCommandBuffer commandBuffer =
                vulkanState.commandBuffersGraphics[vulkanState.frameIndex];

        VulkanState.WindowInfo windowInfo = vulkanState.windows.get(window);

        final long swapchainImage = windowInfo.swapchainImages[windowInfo.currentSwapchainIndex];
        final long depthImage = windowInfo.depthImage.texture;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier2.Buffer outputBarriers = VkImageMemoryBarrier2.calloc(2, stack);
            outputBarriers
                    .get(0)
                    .sType$Default()
                    .srcStageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .srcAccessMask(0)
                    .dstStageMask(VK_PIPELINE_STAGE_2_TRANSFER_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .image(swapchainImage)
                    .subresourceRange(
                            VkImageSubresourceRange.calloc(stack)
                                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                    .levelCount(1)
                                    .layerCount(1));
            // TODO(ches) transfer the texture a state where we can read it:
            /*
             * .srcStageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
             * .srcAccessMask(0)
             * .dstStageMask(VK_PIPELINE_STAGE_2_TRANSFER_BIT)
             * .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
             * .oldLayout(VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL)
             * .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
             */
            // TODO(ches) why do we even have the depth here?
            outputBarriers
                    .get(1)
                    .sType$Default()
                    .srcStageMask(VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT)
                    .srcAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT)
                    .dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .newLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
                    .image(depthImage)
                    .subresourceRange(
                            VkImageSubresourceRange.calloc(stack)
                                    .aspectMask(
                                            VK_IMAGE_ASPECT_DEPTH_BIT | VK_IMAGE_ASPECT_STENCIL_BIT)
                                    .levelCount(1)
                                    .layerCount(1));

            VkDependencyInfo barrierDependencyInfo =
                    VkDependencyInfo.calloc(stack)
                            .sType$Default()
                            .pImageMemoryBarriers(outputBarriers);
            vkCmdPipelineBarrier2(commandBuffer, barrierDependencyInfo);
            // TODO(ches) vkCmdBlitImage

            VkImageMemoryBarrier2.Buffer barrierPresents = VkImageMemoryBarrier2.calloc(1, stack);
            barrierPresents
                    .get(0)
                    .sType$Default()
                    .srcStageMask(VK_PIPELINE_STAGE_2_TRANSFER_BIT)
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT)
                    .dstAccessMask(0)
                    .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
                    .image(swapchainImage)
                    .subresourceRange(
                            VkImageSubresourceRange.calloc(stack)
                                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                    .levelCount(1)
                                    .layerCount(1));
            VkDependencyInfo barrierPresentDependencyInfo =
                    VkDependencyInfo.calloc(stack)
                            .sType$Default()
                            .pImageMemoryBarriers(barrierPresents);
            vkCmdPipelineBarrier2(commandBuffer, barrierPresentDependencyInfo);
        }
    }
}
