package com.ikalagaming.graphics.backend.vulkan;

import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;

public class VulkanState {

    public VkInstance instance;
    public Device device;

    public long surfaceHandle;

    public static class Device {
        public VkPhysicalDevice physical;
        public VkPhysicalDeviceProperties deviceProperties = VkPhysicalDeviceProperties.malloc();
        public VkPhysicalDeviceFeatures deviceFeatures = VkPhysicalDeviceFeatures.malloc();
        public VkDevice logical;
        public VkQueue graphicsQueue;
        public VkQueue presentQueue;
        public QueueFamilyIndices queueFamilyIndices;
        public ByteBuffer descriptorPool;
    }
}
