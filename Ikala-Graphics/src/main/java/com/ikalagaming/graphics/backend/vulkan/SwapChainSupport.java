package com.ikalagaming.graphics.backend.vulkan;

import lombok.NonNull;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;

import java.nio.IntBuffer;

/**
 * Swap chain support information.
 *
 * @param capabilities The surface capability information.
 * @param formats The formats, if any. Null if there are no supported formats.
 * @param presentModes The present modes (VkPresentModeKHR), if any. Null if there are no supported
 *     present modes.
 */
public record SwapChainSupport(
        @NonNull VkSurfaceCapabilitiesKHR capabilities,
        VkSurfaceFormatKHR.Buffer formats,
        IntBuffer presentModes) {
    /** Free any buffer resources. */
    void free() {
        capabilities.free();
        if (formats != null) {
            formats.free();
        }
    }
}
