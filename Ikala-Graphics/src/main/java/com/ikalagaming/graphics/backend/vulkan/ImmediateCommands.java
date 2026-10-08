package com.ikalagaming.graphics.backend.vulkan;

import static com.ikalagaming.graphics.backend.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import lombok.NonNull;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.function.Consumer;

/**
 * Records and submits command buffers outside the frame, waiting for them to finish before
 * returning. Used for things like uploading textures, where we want the work done before carrying
 * on and don't mind stalling.
 *
 * <p>Commands go to the graphics queue, so resources don't need queue family ownership transfers
 * before the frame uses them. This must be called from the render thread, since queue submission
 * needs to be externally synchronized.
 */
public class ImmediateCommands {

    /** The VkCommandPool for the command buffer. */
    private long commandPool;

    /** The command buffer we record into. */
    private VkCommandBuffer commandBuffer;

    /** VkFence that is signaled when the submitted commands finish. */
    private long fence;

    /**
     * Set up the command pool, buffer, and fence.
     *
     * @param state The Vulkan state, with a logical device already created.
     */
    public ImmediateCommands(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            VkCommandPoolCreateInfo commandPoolCreateInfo =
                    VkCommandPoolCreateInfo.calloc(stack)
                            .sType$Default()
                            .flags(
                                    VK_COMMAND_POOL_CREATE_TRANSIENT_BIT
                                            | VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                            .queueFamilyIndex(state.device.physical.queueFamilyIndices.graphics());
            checkError(
                    vkCreateCommandPool(
                            state.device.logical, commandPoolCreateInfo, null, longOutput));
            commandPool = longOutput.get(0);

            VkCommandBufferAllocateInfo commandBufferAllocateInfo =
                    VkCommandBufferAllocateInfo.calloc(stack)
                            .sType$Default()
                            .commandPool(commandPool)
                            .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                            .commandBufferCount(1);
            PointerBuffer commandBuffers = stack.callocPointer(1);
            checkError(
                    vkAllocateCommandBuffers(
                            state.device.logical, commandBufferAllocateInfo, commandBuffers));
            commandBuffer = new VkCommandBuffer(commandBuffers.get(0), state.device.logical);

            VkFenceCreateInfo fenceCreateInfo = VkFenceCreateInfo.calloc(stack).sType$Default();
            checkError(vkCreateFence(state.device.logical, fenceCreateInfo, null, longOutput));
            fence = longOutput.get(0);
        }
    }

    /**
     * Record commands, submit them to the graphics queue, and wait for them to finish.
     *
     * @param state The Vulkan state.
     * @param recorder Records the commands into the provided command buffer, which has already been
     *     started and will be ended afterward.
     */
    public void submit(@NonNull VulkanState state, @NonNull Consumer<VkCommandBuffer> recorder) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            checkError(vkResetCommandBuffer(commandBuffer, 0));

            VkCommandBufferBeginInfo beginInfo =
                    VkCommandBufferBeginInfo.calloc(stack)
                            .sType$Default()
                            .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            checkError(vkBeginCommandBuffer(commandBuffer, beginInfo));
            recorder.accept(commandBuffer);
            checkError(vkEndCommandBuffer(commandBuffer));

            VkCommandBufferSubmitInfo.Buffer commandBufferSubmitInfos =
                    VkCommandBufferSubmitInfo.calloc(1, stack);
            commandBufferSubmitInfos.get(0).sType$Default().commandBuffer(commandBuffer);

            VkSubmitInfo2.Buffer submitInfos = VkSubmitInfo2.calloc(1, stack);
            submitInfos.get(0).sType$Default().pCommandBufferInfos(commandBufferSubmitInfos);

            checkError(vkQueueSubmit2(state.device.graphicsQueue, submitInfos, fence));

            LongBuffer fences = stack.longs(fence);
            checkError(vkWaitForFences(state.device.logical, fences, true, Long.MAX_VALUE));
            checkError(vkResetFences(state.device.logical, fences));
        }
    }

    /**
     * Destroy the Vulkan objects. Must not be called while a submission is in progress.
     *
     * @param state The Vulkan state.
     */
    public void cleanup(@NonNull VulkanState state) {
        vkDestroyFence(state.device.logical, fence, null);
        fence = VK_NULL_HANDLE;
        vkDestroyCommandPool(state.device.logical, commandPool, null);
        commandPool = VK_NULL_HANDLE;
        commandBuffer = null;
    }
}
