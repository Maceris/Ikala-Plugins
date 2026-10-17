package com.ikalagaming.graphics.vulkan;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.glfw.GLFW.GLFW_ICONIFIED;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwGetWindowAttrib;
import static org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.exceptions.RenderException;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

/**
 * Where a window's frames go: its surface and swapchain, the swapchain images, and the semaphores
 * that order acquiring, rendering and presenting them. Each window has its own, all drawn in the
 * same command buffer and presented together.
 */
@Slf4j
public class Presentation {
    /** Placeholder when we know we should not be touching swapchain images. */
    public static final int INVALID_SWAPCHAIN_INDEX = -1;

    /** The swapchain image format. Supported practically everywhere, checked for each surface. */
    public static final int SWAPCHAIN_FORMAT = VK_FORMAT_B8G8R8A8_UNORM;

    /** The swapchain color space, paired with {@link #SWAPCHAIN_FORMAT}. */
    static final int SWAPCHAIN_COLOR_SPACE = VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;

    /**
     * How many milliseconds between swapchain updates we should wait between swapchain
     * regenerations while resizing the window. We delay a while so that we don't get spammed with
     * updates.
     */
    public static final long MILLIS_BETWEEN_SWAPCHAIN_REGENERATION_WHILE_RESIZING = 50;

    /** Composite alpha modes we can live with, in order of preference. */
    private static final int[] COMPOSITE_ALPHA_PREFERENCES = {
        VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR,
        VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR,
        VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR,
        VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR
    };

    /**
     * The window this presents to.
     *
     * @return The window.
     */
    @Getter private final @NonNull Window window;

    /**
     * The index of the swapchain images (/views/semaphores) for the current frame, from
     * vkAcquireNextImageKHR. Only valid during the actual frame rendering.
     */
    public int currentSwapchainIndex = INVALID_SWAPCHAIN_INDEX;

    /**
     * Signaled when the swapchain image for a frame in flight is acquired. Per window, since each
     * swapchain hands out its own images.
     */
    public final long[] imageAcquiredSemaphores = new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** Semaphores for signaling presentation. One per swapchain image. */
    public long[] renderCompleteSemaphores;

    /** The surface capabilities, queried again each time the swapchain is created. */
    private final VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.create();

    public long surfaceHandle = VK_NULL_HANDLE;
    public long swapchainHandle = VK_NULL_HANDLE;
    public long[] swapchainImages;
    public long[] swapchainImageViews;

    /** The swapchain image width in pixels, which might not match the window. */
    public int swapchainWidth;

    /** The swapchain image height in pixels, which might not match the window. */
    public int swapchainHeight;

    /** If we need to update the swapchain. */
    public boolean updateSwapchain = false;

    /** The time (in millis) when the window was last resized. */
    public long lastResize;

    /** The time (in millis) when the swapchain was last regenerated. */
    public long lastSwapchainGeneration = 0;

    /**
     * Create a surface for a window, check the device can present to it, and create its swapchain
     * if the window has any area.
     *
     * @param state The Vulkan state, with the device already created.
     * @param window The window to present to.
     * @throws RenderException If the device can't present to the window.
     */
    Presentation(@NonNull VulkanState state, @NonNull Window window) {
        this.window = window;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.mallocLong(1);
            checkError(
                    glfwCreateWindowSurface(
                            state.instance, window.getWindowHandle(), null, longOutput));
            surfaceHandle = longOutput.get(0);

            try {
                checkSurfaceSupport(state, stack);

                VkSemaphoreCreateInfo semaphoreCreateInfo =
                        VkSemaphoreCreateInfo.calloc(stack).sType$Default();
                for (int i = 0; i < imageAcquiredSemaphores.length; ++i) {
                    checkError(
                            vkCreateSemaphore(
                                    state.device.logical, semaphoreCreateInfo, null, longOutput));
                    imageAcquiredSemaphores[i] = longOutput.get(0);
                }
            } catch (RenderException e) {
                destroy(state);
                throw e;
            }
        }
        if (!createSwapchain(state)) {
            // Started minimized, try again once it has a size
            updateSwapchain = true;
        }
    }

    /**
     * Check that the device can present to the surface with the swapchain format. Device selection
     * only knows the queue family can present on this platform, and Vulkan wants the surface itself
     * checked before making a swapchain for it.
     *
     * @param state The Vulkan state.
     * @param stack The stack to allocate on.
     * @throws RenderException If the device can't present to the surface.
     */
    private void checkSurfaceSupport(@NonNull VulkanState state, @NonNull MemoryStack stack) {
        final VkPhysicalDevice physicalDevice = state.device.physical.physicalDevice;
        IntBuffer intOutput = stack.mallocInt(1);
        checkError(
                vkGetPhysicalDeviceSurfaceSupportKHR(
                        physicalDevice,
                        state.device.physical.queueFamilyIndices.present(),
                        surfaceHandle,
                        intOutput));
        if (intOutput.get(0) != VK_TRUE) {
            final var message = "The device can't present to the window surface";
            log.error(message);
            throw new RenderException(message);
        }

        checkError(
                vkGetPhysicalDeviceSurfaceFormatsKHR(
                        physicalDevice, surfaceHandle, intOutput, null));
        final int formatCount = intOutput.get(0);
        if (formatCount > 0) {
            VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.malloc(formatCount, stack);
            checkError(
                    vkGetPhysicalDeviceSurfaceFormatsKHR(
                            physicalDevice, surfaceHandle, intOutput, formats));
            for (int i = 0; i < formats.limit(); ++i) {
                if (formats.get(i).format() == SWAPCHAIN_FORMAT
                        && formats.get(i).colorSpace() == SWAPCHAIN_COLOR_SPACE) {
                    return;
                }
            }
        }
        final var message = "The window surface doesn't support the swapchain format";
        log.error(message);
        throw new RenderException(message);
    }

    /**
     * Pick a composite alpha mode the surface supports. Opaque is preferred, but not every platform
     * supports it.
     *
     * @param capabilities The surface capabilities.
     * @return The composite alpha flag to use.
     */
    private static int selectCompositeAlpha(@NonNull VkSurfaceCapabilitiesKHR capabilities) {
        for (int mode : COMPOSITE_ALPHA_PREFERENCES) {
            if ((capabilities.supportedCompositeAlpha() & mode) != 0) {
                return mode;
            }
        }
        // At least one bit is guaranteed to be set, so this shouldn't happen
        return VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
    }

    /** Note that the window was resized, so the swapchain is regenerated once resizing settles. */
    public void markResized() {
        updateSwapchain = true;
        lastResize = System.currentTimeMillis();
    }

    /**
     * Checks if we need to update the swapchain, and if it's been long enough since we last
     * regenerated the swapchain or resized that it's worth regenerating right now.
     *
     * @return If we should regenerate the swapchain right now.
     */
    boolean shouldRegenerateSwapchain() {
        return updateSwapchain
                && Math.max(lastResize, lastSwapchainGeneration)
                                + MILLIS_BETWEEN_SWAPCHAIN_REGENERATION_WHILE_RESIZING
                        < System.currentTimeMillis();
    }

    /**
     * Check the result of acquiring or presenting a swapchain image, and flag the swapchain for
     * regeneration if it no longer matches the surface.
     *
     * @param errorCode The result of vkAcquireNextImageKHR or vkQueuePresentKHR.
     * @return False if the swapchain was out of date and the operation did not happen. True if it
     *     happened, even if the swapchain is suboptimal and will be regenerated.
     * @throws RenderException For any other error.
     */
    boolean checkResult(int errorCode) {
        if (errorCode == VK_ERROR_OUT_OF_DATE_KHR) {
            updateSwapchain = true;
            return false;
        }
        if (errorCode == VK_SUBOPTIMAL_KHR) {
            // Still usable, so finish the frame and regenerate afterward
            updateSwapchain = true;
            return true;
        }
        checkError(errorCode);
        return true;
    }

    /**
     * Acquire the swapchain image to render this frame into, signaling this frame's acquire
     * semaphore once it is ready.
     *
     * @param state The Vulkan state.
     * @return True if an image was acquired, false if the swapchain is out of date, in which case
     *     the semaphore was not signaled and the swapchain is flagged for regeneration.
     */
    boolean acquire(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer imageIndex = stack.mallocInt(1);
            final boolean acquired =
                    checkResult(
                            vkAcquireNextImageKHR(
                                    state.device.logical,
                                    swapchainHandle,
                                    Long.MAX_VALUE,
                                    imageAcquiredSemaphores[state.frameIndex],
                                    VK_NULL_HANDLE,
                                    imageIndex));
            currentSwapchainIndex = acquired ? imageIndex.get(0) : INVALID_SWAPCHAIN_INDEX;
            return acquired;
        }
    }

    /**
     * Create the swapchain, along with its image views and semaphores. If there already is one it
     * is replaced, in which case the device must be idle.
     *
     * @param state The Vulkan state.
     * @return False if the window has no area right now (such as while minimized), in which case
     *     nothing changed and we should try again later.
     */
    boolean createSwapchain(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.mallocLong(1);
            IntBuffer intOutput = stack.mallocInt(1);
            long swapTimer = System.nanoTime();
            checkError(
                    vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
                            state.device.physical.physicalDevice, surfaceHandle, capabilities));
            log.trace(
                    "Swapchain: surface capabilities took {} ms, window visible {}, iconified {}",
                    (System.nanoTime() - swapTimer) / 1_000_000.0,
                    glfwGetWindowAttrib(window.getWindowHandle(), GLFW_VISIBLE),
                    glfwGetWindowAttrib(window.getWindowHandle(), GLFW_ICONIFIED));

            final int width;
            final int height;
            if (capabilities.currentExtent().width() == 0xFFFF_FFFF) {
                // The swapchain decides the surface size, so match the framebuffer
                width =
                        Math.clamp(
                                window.getWidth(),
                                capabilities.minImageExtent().width(),
                                capabilities.maxImageExtent().width());
                height =
                        Math.clamp(
                                window.getHeight(),
                                capabilities.minImageExtent().height(),
                                capabilities.maxImageExtent().height());
            } else {
                width = capabilities.currentExtent().width();
                height = capabilities.currentExtent().height();
            }
            if (width == 0 || height == 0) {
                // Minimized, swapchains can't be empty
                return false;
            }

            // One more than the minimum so we don't wait on the presentation engine to get one
            int imageCount = capabilities.minImageCount() + 1;
            if (capabilities.maxImageCount() > 0) {
                // 0 means there is no maximum
                imageCount = Math.min(imageCount, capabilities.maxImageCount());
            }

            final long oldSwapchain = swapchainHandle;

            /*
             * NOTE(ches) The swapchain is BGRA as that's guaranteed to be everywhere, though our app generally operates
             * in RGBA. We'll just swizzle at the last possible second. It's UNORM rather than sRGB since, like the OpenGL
             * backend, we hand it colors that are already in display space. VK_PRESENT_MODE_FIFO_KHR is a v-synced mode
             * and the only mode guaranteed to be available everywhere.
             */
            VkSwapchainCreateInfoKHR swapchainCreateInfo =
                    VkSwapchainCreateInfoKHR.calloc(stack)
                            .sType$Default()
                            .surface(surfaceHandle)
                            .minImageCount(imageCount)
                            .imageFormat(SWAPCHAIN_FORMAT)
                            .imageColorSpace(SWAPCHAIN_COLOR_SPACE)
                            .imageExtent(e -> e.set(width, height))
                            .imageArrayLayers(1)
                            .imageUsage(
                                    VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
                                            | VK_IMAGE_USAGE_TRANSFER_DST_BIT)
                            .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
                            .preTransform(capabilities.currentTransform())
                            .compositeAlpha(selectCompositeAlpha(capabilities))
                            .presentMode(VK_PRESENT_MODE_FIFO_KHR)
                            .clipped(true)
                            .oldSwapchain(oldSwapchain);

            log.trace(
                    "Swapchain: creating {}x{}, {} images, format {}, present mode {}",
                    width,
                    height,
                    imageCount,
                    SWAPCHAIN_FORMAT,
                    swapchainCreateInfo.presentMode());
            swapTimer = System.nanoTime();
            checkError(
                    vkCreateSwapchainKHR(
                            state.device.logical, swapchainCreateInfo, null, longOutput));
            log.trace(
                    "Swapchain: vkCreateSwapchainKHR took {} ms",
                    (System.nanoTime() - swapTimer) / 1_000_000.0);
            swapTimer = System.nanoTime();
            swapchainHandle = longOutput.get(0);
            swapchainWidth = width;
            swapchainHeight = height;

            // Retired by creating the new one, and the device is idle so nothing is using it
            destroySwapchainResources(state);
            if (oldSwapchain != VK_NULL_HANDLE) {
                vkDestroySwapchainKHR(state.device.logical, oldSwapchain, null);
            }

            checkError(
                    vkGetSwapchainImagesKHR(
                            state.device.logical, swapchainHandle, intOutput, null));
            final int actualImageCount = intOutput.get(0);
            LongBuffer images = stack.callocLong(actualImageCount);
            checkError(
                    vkGetSwapchainImagesKHR(
                            state.device.logical, swapchainHandle, intOutput, images));

            log.trace(
                    "Swapchain: cleanup and vkGetSwapchainImagesKHR ({} images) took {} ms",
                    actualImageCount,
                    (System.nanoTime() - swapTimer) / 1_000_000.0);
            swapTimer = System.nanoTime();

            swapchainImages = new long[actualImageCount];
            images.get(0, swapchainImages);
            swapchainImageViews = new long[actualImageCount];
            for (int i = 0; i < actualImageCount; i++) {
                VkImageViewCreateInfo viewCreateInfo =
                        VkImageViewCreateInfo.calloc(stack)
                                .sType$Default()
                                .image(swapchainImages[i])
                                .viewType(VK_IMAGE_VIEW_TYPE_2D)
                                .format(SWAPCHAIN_FORMAT)
                                .subresourceRange(
                                        VkImageSubresourceRange.calloc(stack)
                                                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                                .levelCount(1)
                                                .layerCount(1));
                checkError(
                        vkCreateImageView(state.device.logical, viewCreateInfo, null, longOutput));
                swapchainImageViews[i] = longOutput.get(0);
            }

            // One per image rather than per frame in flight, since presentation holds onto them
            renderCompleteSemaphores = new long[actualImageCount];
            VkSemaphoreCreateInfo semaphoreCreateInfo =
                    VkSemaphoreCreateInfo.calloc(stack).sType$Default();
            for (int i = 0; i < actualImageCount; i++) {
                checkError(
                        vkCreateSemaphore(
                                state.device.logical, semaphoreCreateInfo, null, longOutput));
                renderCompleteSemaphores[i] = longOutput.get(0);
            }

            log.trace(
                    "Swapchain: image views and semaphores took {} ms",
                    (System.nanoTime() - swapTimer) / 1_000_000.0);

            lastSwapchainGeneration = System.currentTimeMillis();
            updateSwapchain = false;
        }
        return true;
    }

    /**
     * Destroy the swapchain image views and the semaphores that go with them. The device must be
     * idle.
     *
     * @param state The Vulkan state.
     */
    private void destroySwapchainResources(@NonNull VulkanState state) {
        if (swapchainImageViews != null) {
            for (long view : swapchainImageViews) {
                vkDestroyImageView(state.device.logical, view, null);
            }
            swapchainImageViews = null;
        }
        if (renderCompleteSemaphores != null) {
            for (long handle : renderCompleteSemaphores) {
                vkDestroySemaphore(state.device.logical, handle, null);
            }
            renderCompleteSemaphores = null;
        }
        swapchainImages = null;
    }

    /**
     * Destroy everything, including the surface, which must happen before the window is destroyed.
     * The device must be idle.
     *
     * @param state The Vulkan state.
     */
    void destroy(@NonNull VulkanState state) {
        destroySwapchainResources(state);
        for (int i = 0; i < imageAcquiredSemaphores.length; ++i) {
            vkDestroySemaphore(state.device.logical, imageAcquiredSemaphores[i], null);
            imageAcquiredSemaphores[i] = VK_NULL_HANDLE;
        }
        vkDestroySwapchainKHR(state.device.logical, swapchainHandle, null);
        swapchainHandle = VK_NULL_HANDLE;
        vkDestroySurfaceKHR(state.instance, surfaceHandle, null);
        surfaceHandle = VK_NULL_HANDLE;
    }
}
