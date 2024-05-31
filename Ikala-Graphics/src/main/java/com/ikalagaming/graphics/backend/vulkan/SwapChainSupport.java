package com.ikalagaming.graphics.backend.vulkan;

import lombok.NonNull;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;

import java.util.Arrays;
import java.util.Objects;

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
        int[] presentModes) {
    /** Free any buffer resources. */
    public void free() {
        capabilities.free();
        if (formats != null) {
            formats.free();
        }
    }

    /**
     * Checks if any critical support is missing.
     *
     * @return If support is missing.
     */
    public boolean isMissingSupport() {
        return formats == null || presentModes == null || presentModes.length == 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SwapChainSupport other)) {
            return false;
        }
        if (!Objects.equals(capabilities, other.capabilities)) {
            return false;
        }
        if (!Objects.equals(formats, other.formats)) {
            return false;
        }
        if (!Arrays.equals(presentModes, other.presentModes)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(capabilities, formats, Arrays.hashCode(presentModes));
    }

    @Override
    public String toString() {
        return String.format(
                "[capabilities=%s, formats=%s, presentModes=%s]",
                Objects.toString(capabilities),
                Objects.toString(formats),
                Arrays.toString(presentModes));
    }
}
