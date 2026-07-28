package com.ikalagaming.graphics.backend.vulkan;

import static org.lwjgl.vulkan.VK13.VK_NULL_HANDLE;

import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class VulkanState {

    public VkInstance instance = null;
    public Device device = new Device();

    /** The physical devices we found on the system. */
    public final List<PhysicalDeviceInfo> physicalDevices = new ArrayList<>();

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

        public ByteBuffer descriptorPool = null;
    }

    /** Information about the physical hardware devices. */
    public static class PhysicalDeviceInfo {
        public VkPhysicalDevice physicalDevice = null;
        public VkPhysicalDeviceProperties deviceProperties = VkPhysicalDeviceProperties.create();
        public VkPhysicalDeviceFeatures deviceFeatures = VkPhysicalDeviceFeatures.create();

        /**
         * An intermediate list of queue family properties. Once we have a surface to work with,
         * this is cleared out again and {@link #queueFamilyIndices} is populated with the indices
         * we care about.
         */
        public VkQueueFamilyProperties.Buffer queueFamilyProperties = null;

        /**
         * Used once we have a surface to check for graphics support, stores relevant queue family
         * indices once found. Will be null if we haven't looked, but exist with missing values if
         * we just couldn't find one or more queues.
         */
        public QueueFamilyIndices queueFamilyIndices = null;

        /** The surface capability information. */
        public VkSurfaceCapabilitiesKHR capabilities = null;

        /** The formats, if any. Null if there are no supported formats. */
        public VkSurfaceFormatKHR.Buffer formats = null;

        /**
         * The present modes (VkPresentModeKHR), if any. Null if there are no supported present
         * modes.
         */
        public int[] presentModes = null;
    }

    /** Info specific to a window. */
    public static class WindowInfo {
        public long surfaceHandle = VK_NULL_HANDLE;
        public long swapchainHandle = VK_NULL_HANDLE;
        public long[] swapchainImages = null;
    }
}
