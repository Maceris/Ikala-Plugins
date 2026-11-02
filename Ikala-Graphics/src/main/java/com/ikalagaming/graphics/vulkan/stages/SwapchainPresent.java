package com.ikalagaming.graphics.vulkan.stages;

import static org.lwjgl.vulkan.VK13.*;
import static org.lwjgl.vulkan.VK13.vkCmdPipelineBarrier2;

import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.vulkan.Presentation;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.SceneTarget;
import com.ikalagaming.graphics.vulkan.TextureInfoVulkan;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

/**
 * Copies the finished scene into the scene window's swapchain image. The image is left as a
 * transfer destination, and the window's GUI pass draws over it and readies it to present. A {@link
 * com.ikalagaming.graphics.vulkan.FrameCapture} someone asked for copies it here too.
 */
@Slf4j
public class SwapchainPresent implements RenderStage {

    @Override
    public void initialize(@NonNull VulkanState vulkanState) {
        log.debug("Initializing swapchain render");
    }

    @Override
    public void cleanup(@NonNull VulkanState vulkanState) {
        // not needed
    }

    @Override
    public void render(
            Scene scene,
            @NonNull SceneTarget sceneTarget,
            @NonNull VulkanState vulkanState,
            RenderConfig renderConfig) {
        final VkCommandBuffer commandBuffer =
                vulkanState.commandBuffersGraphics[vulkanState.frameIndex];

        final Presentation presentation = sceneTarget.presentation();
        final long sourceImage =
                vulkanState.perFrameData[vulkanState.frameIndex].finalTexture.texture;
        final int renderWidth = Math.min(sceneTarget.width(), vulkanState.realSize.width());
        final int renderHeight = Math.min(sceneTarget.height(), vulkanState.realSize.height());

        final TextureInfoVulkan headlessImage =
                vulkanState.perFrameData[vulkanState.frameIndex].headlessImage;
        if (presentation == null && headlessImage == null) {
            // No window and no stand-in for one: only frame captures read the image
            readyToCopy(commandBuffer, sourceImage);
            vulkanState.frameCapture.record(
                    commandBuffer, vulkanState, sourceImage, renderWidth, renderHeight);
            return;
        }
        // Headless, the stand-in for the window's swapchain image takes the scene, and the GUI
        // pass and frame captures follow
        final long swapchainImage =
                presentation == null
                        ? headlessImage.texture
                        : presentation.swapchainImages[presentation.currentSwapchainIndex];
        final int destWidth =
                presentation == null ? vulkanState.realSize.width() : presentation.swapchainWidth;
        final int destHeight =
                presentation == null ? vulkanState.realSize.height() : presentation.swapchainHeight;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier2.Buffer outputBarriers = VkImageMemoryBarrier2.calloc(2, stack);
            outputBarriers
                    .get(0)
                    .sType$Default()
                    .srcStageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .srcAccessMask(0)
                    .dstStageMask(VK_PIPELINE_STAGE_2_BLIT_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .image(swapchainImage)
                    .subresourceRange(
                            VkImageSubresourceRange.calloc(stack)
                                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                    .levelCount(1)
                                    .layerCount(1));
            outputBarriers
                    .get(1)
                    .sType$Default()
                    .srcStageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
                    // Make the rendering visible to the blit
                    .srcAccessMask(VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_2_TRANSFER_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .image(sourceImage)
                    .subresourceRange(
                            VkImageSubresourceRange.calloc(stack)
                                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                    .levelCount(1)
                                    .layerCount(1));

            VkDependencyInfo barrierDependencyInfo =
                    VkDependencyInfo.calloc(stack)
                            .sType$Default()
                            .pImageMemoryBarriers(outputBarriers);
            vkCmdPipelineBarrier2(commandBuffer, barrierDependencyInfo);

            VkImageSubresourceLayers sourceLayers =
                    VkImageSubresourceLayers.calloc(stack)
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .layerCount(1)
                            .mipLevel(0);
            // The same region the other stages render to
            VkOffset3D.Buffer sourceOffsets = VkOffset3D.calloc(2, stack);
            sourceOffsets.get(0).set(0, 0, 0);
            sourceOffsets.get(1).set(renderWidth, renderHeight, 1);

            VkImageSubresourceLayers destLayers =
                    VkImageSubresourceLayers.calloc(stack)
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .layerCount(1)
                            .mipLevel(0);
            VkOffset3D.Buffer destOffsets = VkOffset3D.calloc(2, stack);
            destOffsets.get(0).set(0, 0, 0);
            // Usually the same size, but the swapchain can briefly disagree with the window
            destOffsets.get(1).set(destWidth, destHeight, 1);

            VkImageBlit.Buffer blitRegions = VkImageBlit.calloc(1, stack);
            blitRegions
                    .get(0)
                    .srcOffsets(sourceOffsets)
                    .dstOffsets(destOffsets)
                    .srcSubresource(sourceLayers)
                    .dstSubresource(destLayers);

            vkCmdBlitImage(
                    commandBuffer,
                    sourceImage,
                    VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    swapchainImage,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    blitRegions,
                    VK_FILTER_LINEAR);
        }
        if (presentation != null) {
            // Before the GUI goes over it in the window
            vulkanState.frameCapture.record(
                    commandBuffer, vulkanState, sourceImage, renderWidth, renderHeight);
        }
    }

    /**
     * Make the finished scene a transfer source, as copying it to the window would.
     *
     * @param commandBuffer The frame's command buffer.
     * @param image The scene's final image, after its last color write.
     */
    private static void readyToCopy(VkCommandBuffer commandBuffer, long image) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier2.Buffer barrier =
                    VkImageMemoryBarrier2.calloc(1, stack)
                            .sType$Default()
                            .srcStageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
                            .srcAccessMask(VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT)
                            .dstStageMask(VK_PIPELINE_STAGE_2_TRANSFER_BIT)
                            .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                            .oldLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                            .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                            .image(image)
                            .subresourceRange(
                                    r ->
                                            r.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                                    .levelCount(1)
                                                    .layerCount(1));
            vkCmdPipelineBarrier2(
                    commandBuffer,
                    VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barrier));
        }
    }
}
