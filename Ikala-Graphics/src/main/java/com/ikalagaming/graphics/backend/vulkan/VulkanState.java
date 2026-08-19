package com.ikalagaming.graphics.backend.vulkan;

import static org.lwjgl.vulkan.VK10.VK_FORMAT_UNDEFINED;
import static org.lwjgl.vulkan.VK13.VK_NULL_HANDLE;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.backend.base.State;

import org.lwjgl.vulkan.*;

import java.util.ArrayList;
import java.util.List;

/** Global state for Vulkan. */
public class VulkanState implements State {

    /** The Vulkan instance. */
    public VkInstance instance = null;

    /** The command pool to allocate command buffers from. */
    public long commandPool = VK_NULL_HANDLE;

    /** Command buffers for each frame. */
    public final VkCommandBuffer[] commandBuffers =
            new VkCommandBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** Device information. */
    public final Device device = new Device();

    /** Fences for signaling frames. One per frame in flight. */
    public final long[] fences = new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /**
     * The current frame index, values in the range [0, {@link
     * com.ikalagaming.graphics.GraphicsManager#MAX_FRAMES_IN_FLIGHT}).
     */
    public int frameIndex = 0;

    /** Semaphores for signaling presentation. One per frame in flight. */
    public final long[] imageAcquiredSemaphores = new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** The physical devices we found on the system. */
    public final List<PhysicalDeviceInfo> physicalDevices = new ArrayList<>();

    /** Shader data buffers per frame. */
    public final PerFrameData[] shaderDataBuffers =
            new PerFrameData[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** The Vulkan Memory Allocator handle. */
    public long vmaAllocator = VK_NULL_HANDLE;

    /** Info specific to windows. */
    public final List<WindowInfo> windows = new ArrayList<>();

    public static class Device {
        /** The physical device this corresponds to, for reference. */
        public PhysicalDeviceInfo physical = null;

        public VkDevice logical = null;

        /**
         * The queue for graphics commands. This might or might not be the same as the present
         * queue. If using the same queue, these will share the same Java object.
         */
        public VkQueue graphicsQueue = null;

        /**
         * The queue for presentation commands. This might or might not be the same as the graphics
         * queue. If using the same queue, these will share the same Java object.
         */
        public VkQueue presentQueue = null;
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
         * An intermediate list of queue family properties. Once we have a surface to work with,
         * this is cleared out again and {@link #queueFamilyIndices} is populated with the indices
         * we care about.
         */
        public VkQueueFamilyProperties.Buffer queueFamilyProperties = null;
    }

    /** Info specific to a window. */
    public static class WindowInfo {
        public long surfaceHandle = VK_NULL_HANDLE;
        public long swapchainHandle = VK_NULL_HANDLE;
        public long[] swapchainImages = null;
        public TextureInfo depthImage = null;

        /** Semaphores for signaling presentation. One per swapchain image. */
        public long[] renderCompleteSemaphores;
    }
}
