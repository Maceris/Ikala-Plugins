package com.ikalagaming.graphics.backend.vulkan;

import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueue;

import java.nio.ByteBuffer;

public class VulkanState {

    public Device device;

    public static class Device {
        public VkPhysicalDevice physicalDevice;
        public VkDevice logicalDevice;
        public VkQueue graphicsQueue;
        public VkQueue presentQueue;
        public int graphicsQueueFamily;
        public int presentQueueFamily;
        public ByteBuffer descriptorPool;
    }
}
