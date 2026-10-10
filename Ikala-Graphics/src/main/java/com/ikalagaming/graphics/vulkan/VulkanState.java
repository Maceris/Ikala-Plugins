package com.ikalagaming.graphics.vulkan;

import static org.lwjgl.vulkan.VK10.VK_FORMAT_UNDEFINED;
import static org.lwjgl.vulkan.VK13.VK_NULL_HANDLE;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;

import lombok.NonNull;
import org.lwjgl.vulkan.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Global state for Vulkan. */
public class VulkanState {

    /** The Vulkan instance. */
    public VkInstance instance = null;

    /** The VkDebugUtilsMessengerEXT for validation messages, VK_NULL_HANDLE if not enabled. */
    public long debugMessenger = VK_NULL_HANDLE;

    /** The global bindless texture array. */
    public BindlessTextures bindlessTextures = null;

    /** Which plugin owns each texture handed out as a handle, by bindless slot. */
    public TextureRegistry textureRegistry = null;

    /** Where upload data waits for the render thread to copy it to the GPU. */
    public StagingRing stagingRing = null;

    /** Texture uploads waiting for the render thread. */
    public TextureUploads textureUploads = null;

    /** The shared vertex and index buffers every scene mesh lives in. */
    public GeometryArena geometry = null;

    /** For submitting work outside the frame, like texture uploads. */
    public ImmediateCommands immediateCommands = null;

    /** The command pool for graphics commands. */
    public long commandPoolGraphics = VK_NULL_HANDLE;

    /**
     * The command pool for transfer commands, may be the same as {@link #commandBuffersGraphics}.
     */
    public long commandPoolTransfer = VK_NULL_HANDLE;

    /** Per-frame graphics command buffers, each index should be a different buffer. */
    public final VkCommandBuffer[] commandBuffersGraphics =
            new VkCommandBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /**
     * Command buffer for transfer commands. If we don't have a dedicated queue for transfers
     * ({@link #hasSeparateTransferQueue}), this will be null, and we'll be forced to transfer
     * buffers at the start of the next graphics command buffer.
     */
    public VkCommandBuffer commandBufferTransfer = null;

    /** Device information. */
    public final Device device = new Device();

    /** Fences for signaling frames. One per frame in flight. */
    public final long[] fences = new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /**
     * The current frame index, values in the range [0, {@link
     * com.ikalagaming.graphics.GraphicsManager#MAX_FRAMES_IN_FLIGHT}).
     */
    public int frameIndex = 0;

    /**
     * Whether we have a dedicated command queue for transfers. We try to have a separate queue,
     * preferably on a separate queue family corresponding to DMA hardware, but could be forced to
     * use the graphics queue. If this is true we can transfer data in the background, if false data
     * must be transferred synchronously at the start of the next frame.
     */
    public boolean hasSeparateTransferQueue = false;

    /** Semaphores for signaling presentation. One per frame in flight. */
    public final long[] imageAcquiredSemaphores = new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** The physical devices we found on the system. */
    public final List<PhysicalDeviceInfo> physicalDevices = new ArrayList<>();

    /** Shader data buffers per frame. */
    public final PerFrameData[] perFrameData =
            new PerFrameData[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /**
     * The actual size of the intermediary textures. Distinct from the window/swapchain size, since
     * we can keep larger textures and just use a smaller region of the image.
     */
    public final VkExtent3D realSize = VkExtent3D.create();

    /** The Vulkan Memory Allocator handle. */
    public long vmaAllocator = VK_NULL_HANDLE;

    /** Info specific to windows. */
    public final Map<Window, WindowInfo> windows = new HashMap<>();

    /**
     * Resource frees that have to wait until no frame in flight can be using the resource, one list
     * per frame in flight. A list is run once the fence for that frame index has been waited on.
     */
    public final List<List<Runnable>> deferredFrees = createDeferredFreeLists();

    /**
     * Create the per-frame lists of deferred frees.
     *
     * @return One empty list per frame in flight.
     */
    private static List<List<Runnable>> createDeferredFreeLists() {
        List<List<Runnable>> result = new ArrayList<>();
        for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
            result.add(new ArrayList<>());
        }
        return result;
    }

    /**
     * Whether the render thread is recording the frame at {@link #frameIndex}, as opposed to
     * between frames. Render thread only.
     */
    public boolean recordingFrame = false;

    /**
     * Free something once no frame that could be using it is still in flight. Render thread only.
     *
     * <p>While a frame is recording, that frame may use the resource, so it waits for this frame
     * index's fence, which comes around again once every earlier frame is done too. Between frames,
     * the newest frame using it is the one submitted last, so it waits for that frame's fence
     * instead; waiting for the next frame's would free it while the last one still runs.
     *
     * @param free The code that frees the resource.
     */
    public void deferFree(@NonNull Runnable free) {
        final int index =
                recordingFrame
                        ? frameIndex
                        : (frameIndex + GraphicsManager.MAX_FRAMES_IN_FLIGHT - 1)
                                % GraphicsManager.MAX_FRAMES_IN_FLIGHT;
        deferredFrees.get(index).add(free);
    }

    /**
     * Run the deferred frees for a frame index. Only call this once the GPU is done with that
     * frame, such as after waiting on its fence.
     *
     * @param index The frame index to free resources for.
     */
    public void runDeferredFrees(int index) {
        List<Runnable> frees = deferredFrees.get(index);
        frees.forEach(Runnable::run);
        frees.clear();
    }

    public static class Device {
        /** The physical device this corresponds to, for reference. */
        public PhysicalDeviceInfo physical = null;

        public VkDevice logical = null;

        /** The queue for graphics commands. */
        public VkQueue graphicsQueue = null;

        /**
         * Used for transferring data to the GPU. This might or might not be the same as the
         * graphics queue. If using the same queue, these will share the same Java object.
         */
        public VkQueue transferQueue = null;
    }

    /** Information about the physical hardware devices. */
    public static class PhysicalDeviceInfo {

        /** The surface capability information. */
        public VkSurfaceCapabilitiesKHR capabilities = null;

        /** The depth format we selected from among the list that this device supports. */
        public int depthFormat = VK_FORMAT_UNDEFINED;

        public VkPhysicalDeviceFeatures deviceFeatures = VkPhysicalDeviceFeatures.create();
        public VkPhysicalDeviceProperties deviceProperties = VkPhysicalDeviceProperties.create();

        /** The formats, if any. Null if there are no supported formats. */
        public VkSurfaceFormatKHR.Buffer formats = null;

        /**
         * The maximum number of sampled image descriptors we can support in descriptor set. We
         * start off with as much as the engine can handle, and trim it down if the device doesn't
         * support that many.
         */
        public int maxBindlessImages = VulkanInstance.MAX_BINDLESS_TEXTURE_COUNT;

        public VkPhysicalDevice physicalDevice = null;

        /**
         * The present modes (VkPresentModeKHR), if any. Null if there are no supported present
         * modes.
         */
        public int[] presentModes = null;

        /**
         * Used once we have a surface to check for graphics support, stores relevant queue family
         * indices once found. Will be null if we haven't looked, but exist with missing values if
         * we just couldn't find one or more queues.
         */
        public QueueFamilyIndices queueFamilyIndices = null;

        /**
         * Whether the device has VK_KHR_portability_subset, meaning it's not fully conformant (like
         * MoltenVK) and we have to enable the extension.
         */
        public boolean portabilitySubset = false;

        /**
         * An intermediate list of queue family properties. Once we have a surface to work with,
         * this is cleared out again and {@link #queueFamilyIndices} is populated with the indices
         * we care about.
         */
        public VkQueueFamilyProperties.Buffer queueFamilyProperties = null;
    }

    /** Info specific to a window. */
    public static class WindowInfo {
        /** Placeholder when we know we should not be touching swapchain images. */
        public static final int INVALID_SWAPCHAIN_INDEX = -1;

        /**
         * The index of the swapchain images (/views/semaphores) for the current frame, from
         * vkAcquireNextImageKHR. Only valid during the actual frame rendering.
         */
        public int currentSwapchainIndex;

        /** Semaphores for signaling presentation. One per swapchain image. */
        public long[] renderCompleteSemaphores;

        public long surfaceHandle;
        public long swapchainHandle;
        public long[] swapchainImages;
        public long[] swapchainImageViews;

        /** The swapchain image width in pixels, which might not match the window. */
        public int swapchainWidth;

        /** The swapchain image height in pixels, which might not match the window. */
        public int swapchainHeight;

        /** If we need to update the swapchain. */
        public boolean updateSwapchain;

        /** The time (in millis) when the window was last resized. */
        public long lastResize;

        /** The time (in millis) when the swapchain was last regenerated. */
        public long lastSwapchainGeneration;

        public final @NonNull Window window;

        /**
         * Create a struct for the specified window.
         *
         * @param window The window this is related to.
         */
        public WindowInfo(@NonNull Window window) {
            this.currentSwapchainIndex = INVALID_SWAPCHAIN_INDEX;
            this.renderCompleteSemaphores = null;
            this.surfaceHandle = VK_NULL_HANDLE;
            this.swapchainHandle = VK_NULL_HANDLE;
            this.swapchainImages = null;
            this.swapchainImageViews = null;
            this.updateSwapchain = false;
            this.lastSwapchainGeneration = 0;
            this.window = window;
        }
    }
}
