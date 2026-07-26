package com.ikalagaming.graphics.backend.vulkan;

import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;

public class VulkanState {

    public VkInstance instance;
    public Device device = new Device();

    public long surfaceHandle;

    public static class Device {
        public VkPhysicalDevice physical;
        public VkPhysicalDeviceProperties deviceProperties = VkPhysicalDeviceProperties.malloc();
        public VkPhysicalDeviceFeatures deviceFeatures = VkPhysicalDeviceFeatures.malloc();
        public VkDevice logical;

        /**
         * The queue for graphics commands. This might or might not be the same as the present
         * queue. If using the same queue, these will share the same Java object.
         */
        public VkQueue graphicsQueue;

        /**
         * The queue for presentation commands. This might or might not be the same as the graphics
         * queue. If using the same queue, these will share the same Java object.
         */
        public VkQueue presentQueue;

        /** Used while finding appropriate queues. */
        public QueueFamilyIndices queueFamilyIndices;

        public ByteBuffer descriptorPool;
    }
}
