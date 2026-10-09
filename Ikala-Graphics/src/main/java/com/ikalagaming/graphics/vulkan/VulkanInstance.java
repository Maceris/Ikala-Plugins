package com.ikalagaming.graphics.vulkan;

import static org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface;
import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.KHRPortabilityEnumeration.*;
import static org.lwjgl.vulkan.KHRPortabilitySubset.VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceProperties;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.*;
import com.ikalagaming.graphics.BufferHolder;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.exceptions.RenderException;
import com.ikalagaming.graphics.exceptions.ShaderException;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.gui.IkGui;
import com.ikalagaming.graphics.gui.data.IkIO;
import com.ikalagaming.graphics.gui.flags.ConfigFlags;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.util.vma.VmaVulkanFunctions;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class VulkanInstance {

    /**
     * The maximum number of bindless textures we could support. The actual number that is supported
     * may be lower due to runtime physical GPU limits, but it will never be higher.
     */
    public static final int MAX_BINDLESS_TEXTURE_COUNT = 10_000;

    /**
     * How many milliseconds between swapchain updates we should wait between swapchain
     * regenerations while resizing the window. We delay a while so that we don't get spammed with
     * updates.
     */
    public static final long MILLIS_BETWEEN_SWAPCHAIN_REGENERATION_WHILE_RESIZING = 50;

    private static final List<String> REQUIRED_INSTANCE_EXTENSION_NAMES =
            List.of(VK_KHR_SURFACE_EXTENSION_NAME);
    private static final ByteBuffer[] REQUIRED_INSTANCE_EXTENSIONS =
            REQUIRED_INSTANCE_EXTENSION_NAMES.stream()
                    .map(MemoryUtil::memASCII)
                    .toArray(ByteBuffer[]::new);

    private static final List<String> REQUIRED_DEVICE_EXTENSION_NAMES =
            List.of(VK_KHR_SWAPCHAIN_EXTENSION_NAME);
    private static final ByteBuffer[] REQUIRED_DEVICE_EXTENSIONS =
            REQUIRED_DEVICE_EXTENSION_NAMES.stream()
                    .map(MemoryUtil::memASCII)
                    .toArray(ByteBuffer[]::new);

    /** The list of validation layers we want if validation is enabled. */
    private static final String[] VALIDATION_LAYERS = {"VK_LAYER_KHRONOS_validation"};

    /**
     * Whether to try to enable validation layers and logging. If the layers aren't installed, as
     * when running directly on MoltenVK without a loader, we log a warning and run without them.
     */
    private static final boolean ENABLE_VALIDATION = true;

    /** The swapchain image format. Supported practically everywhere, checked during selection. */
    private static final int SWAPCHAIN_FORMAT = VK_FORMAT_B8G8R8A8_UNORM;

    /** The swapchain color space, paired with {@link #SWAPCHAIN_FORMAT}. */
    private static final int SWAPCHAIN_COLOR_SPACE = VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;

    /** Composite alpha modes we can live with, in order of preference. */
    private static final int[] COMPOSITE_ALPHA_PREFERENCES = {
        VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR,
        VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR,
        VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR,
        VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR
    };

    /**
     * Check for an error, and if there is one then log it and throw an exception.
     *
     * @param errorCode The result from a Vulkan function.
     * @throws RenderException If the error code is not 0.
     */
    public static void checkError(int errorCode) {
        if (errorCode != 0) {
            final String errorName =
                    switch (errorCode) {
                            // Vulkan 1.0 errors
                        case VK_ERROR_OUT_OF_HOST_MEMORY -> "VK_ERROR_OUT_OF_HOST_MEMORY";
                        case VK_ERROR_OUT_OF_DEVICE_MEMORY -> "VK_ERROR_OUT_OF_DEVICE_MEMORY";
                        case VK_ERROR_INITIALIZATION_FAILED -> "VK_ERROR_INITIALIZATION_FAILED";
                        case VK_ERROR_DEVICE_LOST -> "VK_ERROR_DEVICE_LOST";
                        case VK_ERROR_MEMORY_MAP_FAILED -> "VK_ERROR_MEMORY_MAP_FAILED";
                        case VK_ERROR_LAYER_NOT_PRESENT -> "VK_ERROR_LAYER_NOT_PRESENT";
                        case VK_ERROR_EXTENSION_NOT_PRESENT -> "VK_ERROR_EXTENSION_NOT_PRESENT";
                        case VK_ERROR_FEATURE_NOT_PRESENT -> "VK_ERROR_FEATURE_NOT_PRESENT";
                        case VK_ERROR_INCOMPATIBLE_DRIVER -> "VK_ERROR_INCOMPATIBLE_DRIVER";
                        case VK_ERROR_TOO_MANY_OBJECTS -> "VK_ERROR_TOO_MANY_OBJECTS";
                        case VK_ERROR_FORMAT_NOT_SUPPORTED -> "VK_ERROR_FORMAT_NOT_SUPPORTED";
                        case VK_ERROR_FRAGMENTED_POOL -> "VK_ERROR_FRAGMENTED_POOL";
                        case VK_ERROR_UNKNOWN -> "VK_ERROR_UNKNOWN";
                        case VK_ERROR_VALIDATION_FAILED -> "VK_ERROR_VALIDATION_FAILED";

                            // Vulkan 1.1 errors
                        case VK_ERROR_OUT_OF_POOL_MEMORY -> "VK_ERROR_OUT_OF_POOL_MEMORY";
                        case VK_ERROR_INVALID_EXTERNAL_HANDLE -> "VK_ERROR_INVALID_EXTERNAL_HANDLE";

                            // Vulkan 1.2 errors
                        case VK_ERROR_FRAGMENTATION -> "VK_ERROR_FRAGMENTATION";
                        case VK_ERROR_INVALID_OPAQUE_CAPTURE_ADDRESS ->
                                "VK_ERROR_INVALID_OPAQUE_CAPTURE_ADDRESS";

                            // Surfaces and swapchains
                        case VK_ERROR_SURFACE_LOST_KHR -> "VK_ERROR_SURFACE_LOST_KHR";
                        case VK_ERROR_NATIVE_WINDOW_IN_USE_KHR ->
                                "VK_ERROR_NATIVE_WINDOW_IN_USE_KHR";
                        case VK_ERROR_OUT_OF_DATE_KHR -> "VK_ERROR_OUT_OF_DATE_KHR";
                        case VK_SUBOPTIMAL_KHR -> "VK_SUBOPTIMAL_KHR";

                            // Not errors, but not success either
                        case VK_NOT_READY -> "VK_NOT_READY";
                        case VK_TIMEOUT -> "VK_TIMEOUT";
                        case VK_INCOMPLETE -> "VK_INCOMPLETE";

                            // Vulkan 1.3 errors
                            // Nothing for now

                            // Fallback
                        default -> "Unrecognized error code";
                    };

            var message =
                    SafeResourceLoader.format(
                            "Vulkan error {} ({})", String.format("0x%X", errorCode), errorName);
            log.error(message);
            throw new RenderException(message);
        }
    }

    /**
     * Log a debug message from Vulkan. Intended to be used by the {@link #debugLogger}, not called
     * by us.
     *
     * @param messageSeverity The severity of the message.
     * @param messageTypes The type(s) of the message.
     * @param callbackDataPointer A pointer for messenger callback data.
     * @param userDataPointer Ignored by us.
     * @return VK_FALSE, as mandated by Vulkan.
     */
    private static int logDebugMessage(
            int messageSeverity, int messageTypes, long callbackDataPointer, long userDataPointer) {
        final var messageFormat = "[{}] {} - {}";

        VkDebugUtilsMessengerCallbackDataEXT data =
                VkDebugUtilsMessengerCallbackDataEXT.create(callbackDataPointer);

        final String type = mapDebugMessageTypeName(messageTypes);

        if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0) {
            log.error(messageFormat, type, data.pMessageIdNameString(), data.pMessageString());
        } else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0) {
            log.warn(messageFormat, type, data.pMessageIdNameString(), data.pMessageString());
        } else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT) != 0) {
            log.debug(messageFormat, type, data.pMessageIdNameString(), data.pMessageString());
        } else {
            // Info or anything else
            log.info(messageFormat, type, data.pMessageIdNameString(), data.pMessageString());
        }

        return VK_FALSE;
    }

    /**
     * Convert a debug message type to a string form.
     *
     * @param types The message type provided by Vulkan.
     * @return The string name for debugging.
     */
    private static String mapDebugMessageTypeName(int types) {
        if ((types & VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT) != 0) {
            return "General";
        }
        if ((types & VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT) != 0) {
            return "Validation";
        }
        if ((types & VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT) != 0) {
            return "Performance";
        }
        return "Unknown";
    }

    /**
     * Populate the list of required instance extensions based on what we need to run.
     *
     * @param requiredExtensionNames The buffer to store the required extension names in.
     */
    private static void populateRequiredExtensions(@NonNull PointerBuffer requiredExtensionNames) {
        PointerBuffer glfwExtensionNames = glfwGetRequiredInstanceExtensions();
        if (glfwExtensionNames == null) {
            final var message = "Failed to find required GLFW extension names";
            log.error(message);
            throw new RenderException(message);
        }

        for (int i = 0; i < glfwExtensionNames.limit(); ++i) {
            requiredExtensionNames.put(glfwExtensionNames.get(i));
        }

        assert REQUIRED_INSTANCE_EXTENSIONS.length == REQUIRED_INSTANCE_EXTENSION_NAMES.size();

        var limit = glfwExtensionNames.limit();

        for (int i = 0; i < REQUIRED_INSTANCE_EXTENSIONS.length; ++i) {
            boolean duplicate = false;
            for (int j = 0; j < limit; ++j) {
                if (requiredExtensionNames
                        .getStringASCII(j)
                        .equals(REQUIRED_INSTANCE_EXTENSION_NAMES.get(i))) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                requiredExtensionNames.put(REQUIRED_INSTANCE_EXTENSIONS[i]);
                ++limit;
            }
        }
    }

    /**
     * Checks if we need to update the swapchain, and if it's been long enough since we last
     * regenerated the swapchain or resized that it's worth regenerating right now.
     *
     * @param windowInfo The info about the window we are interested in.
     * @return If we should regenerate the swapchain right now.
     */
    private static boolean shouldRegenerateSwapchain(@NonNull VulkanState.WindowInfo windowInfo) {
        return windowInfo.updateSwapchain
                && Math.max(windowInfo.lastResize, windowInfo.lastSwapchainGeneration)
                                + MILLIS_BETWEEN_SWAPCHAIN_REGENERATION_WHILE_RESIZING
                        < System.currentTimeMillis();
    }

    private final IntBuffer intOutput = MemoryUtil.memAllocInt(1);
    private final LongBuffer longOutput = MemoryUtil.memAllocLong(1);
    private final PointerBuffer pointerOutput = MemoryUtil.memAllocPointer(1);

    private final VkDebugUtilsMessengerCallbackEXT debugLogger =
            VkDebugUtilsMessengerCallbackEXT.create(VulkanInstance::logDebugMessage);

    /** Tracks the state and handles. */
    private final VulkanState state = new VulkanState();

    private int renderConfig;
    private PipelineVulkan pipeline;
    private TextureLoaderVulkan textureLoader;
    private ShaderMap shaderMap;
    private PipelineManagerVulkan pipelineManager;

    /**
     * Check that the specified layers are available, and log a warning if any are not.
     *
     * @param availableLayerNames The layer names that are available.
     * @param requiredLayerNames The layers that we want.
     * @return Whether all the layers are available.
     */
    private boolean checkLayers(
            @NonNull VkLayerProperties.Buffer availableLayerNames,
            PointerBuffer requiredLayerNames) {

        List<String> missingLayers = new ArrayList<>();

        for (int i = 0; i < requiredLayerNames.limit(); ++i) {
            boolean found = false;

            final String required = requiredLayerNames.getStringASCII(i);

            for (int j = 0; j < availableLayerNames.capacity(); ++j) {
                availableLayerNames.position(j);
                if (required.equals(availableLayerNames.layerNameString())) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                missingLayers.add(required);
            }
        }

        if (!missingLayers.isEmpty()) {
            final var layerNames = String.join(", ", missingLayers);
            log.warn("Vulkan layers missing, running without them: {}", layerNames);

            if (log.isDebugEnabled()) {
                List<String> layers = new ArrayList<>();
                for (int j = 0; j < availableLayerNames.capacity(); ++j) {
                    var layer = availableLayerNames.get(j);
                    layers.add(
                            String.format(
                                    "%s (%s)", layer.layerNameString(), layer.descriptionString()));
                }
                log.debug("Found Vulkan layers: {}", String.join(", ", layers));
            }
            return false;
        }
        return true;
    }

    /**
     * Find the names of all the instance extensions available.
     *
     * @return The extension names.
     */
    private List<String> getAvailableInstanceExtensions() {
        checkError(vkEnumerateInstanceExtensionProperties((String) null, intOutput, null));
        List<String> result = new ArrayList<>();
        // Can be too big for the stack, so it's garbage collected instead
        VkExtensionProperties.Buffer properties = VkExtensionProperties.create(intOutput.get(0));
        checkError(vkEnumerateInstanceExtensionProperties((String) null, intOutput, properties));
        for (int i = 0; i < intOutput.get(0); ++i) {
            result.add(properties.get(i).extensionNameString());
        }
        return result;
    }

    /**
     * Check the result of acquiring or presenting a swapchain image, and flag the swapchain for
     * regeneration if it no longer matches the surface.
     *
     * @param errorCode The result of vkAcquireNextImageKHR or vkQueuePresentKHR.
     * @param windowInfo The window we are interested in.
     * @return False if the swapchain was out of date and the operation did not happen. True if it
     *     happened, even if the swapchain is suboptimal and will be regenerated.
     * @throws RenderException For any other error.
     */
    private boolean checkSwapchain(int errorCode, @NonNull VulkanState.WindowInfo windowInfo) {
        if (errorCode == VK_ERROR_OUT_OF_DATE_KHR) {
            windowInfo.updateSwapchain = true;
            return false;
        }
        if (errorCode == VK_SUBOPTIMAL_KHR) {
            // Still usable, so finish the frame and regenerate afterward
            windowInfo.updateSwapchain = true;
            return true;
        }
        checkError(errorCode);
        return true;
    }

    /** Clean up all the rendering resources. */
    public void cleanup() {
        checkError(vkDeviceWaitIdle(state.device.logical));

        pipelineManager.cleanup(state);
        // Queues them up for deletion below
        shaderMap.clearAll();
        // Uploads that never happened, then whatever plugins didn't release
        state.textureUploads.clear(state);
        state.textureRegistry.removeAll().forEach(textureLoader::delete);

        DeletionQueue.Entry nextEntry = GraphicsManager.getDeletionQueue().pop();
        while (nextEntry != null) {
            deleteResource(nextEntry);
            nextEntry = GraphicsManager.getDeletionQueue().pop();
        }
        // The device is idle, so everything deferred can go now
        for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
            state.runDeferredFrees(i);
        }
        textureLoader.cleanup();
        state.bindlessTextures.cleanup(state);
        state.bindlessTextures = null;
        state.textureRegistry = null;
        state.textureUploads = null;
        state.stagingRing.cleanup(state);
        state.stagingRing = null;
        state.immediateCommands.cleanup(state);
        state.immediateCommands = null;
        // Created in initializeGui()
        IkGui.destroyContext();

        for (VulkanState.WindowInfo windowInfo : state.windows.values()) {
            cleanupWindow(windowInfo);
        }
        state.windows.clear();

        for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
            vkDestroyFence(state.device.logical, state.fences[i], null);
            state.fences[i] = VK_NULL_HANDLE;
            vkDestroySemaphore(state.device.logical, state.imageAcquiredSemaphores[i], null);
            state.imageAcquiredSemaphores[i] = VK_NULL_HANDLE;
        }

        // Frees the command buffers allocated from them too
        vkDestroyCommandPool(state.device.logical, state.commandPoolGraphics, null);
        state.commandPoolGraphics = VK_NULL_HANDLE;
        if (state.hasSeparateTransferQueue) {
            vkDestroyCommandPool(state.device.logical, state.commandPoolTransfer, null);
            state.commandPoolTransfer = VK_NULL_HANDLE;
        }
        vmaDestroyAllocator(state.vmaAllocator);
        state.vmaAllocator = VK_NULL_HANDLE;
        vkDestroyDevice(state.device.logical, null);
        cleanupPhysicalDeviceInfo(state.device.physical);
        state.device.physical = null;
        if (state.debugMessenger != VK_NULL_HANDLE) {
            vkDestroyDebugUtilsMessengerEXT(state.instance, state.debugMessenger, null);
            state.debugMessenger = VK_NULL_HANDLE;
        }
        vkDestroyInstance(state.instance, null);
        state.instance = null;

        debugLogger.free();
        MemoryUtil.memFree(intOutput);
        MemoryUtil.memFree(longOutput);
        MemoryUtil.memFree(pointerOutput);
    }

    /**
     * Drop references to the device info structs. They are all garbage collected buffers (created
     * with create() rather than calloc()), so they must not be freed manually.
     *
     * @param deviceInfo The struct to clean up.
     */
    private void cleanupPhysicalDeviceInfo(@NonNull VulkanState.PhysicalDeviceInfo deviceInfo) {
        deviceInfo.capabilities = null;
        deviceInfo.formats = null;
        deviceInfo.queueFamilyProperties = null;
    }

    /**
     * Destroy the swapchain image views and the semaphores that go with them. The device must be
     * idle.
     *
     * @param windowInfo The window to clean up after.
     */
    private void cleanupSwapchainResources(@NonNull VulkanState.WindowInfo windowInfo) {
        if (windowInfo.swapchainImageViews != null) {
            for (long view : windowInfo.swapchainImageViews) {
                vkDestroyImageView(state.device.logical, view, null);
            }
            windowInfo.swapchainImageViews = null;
        }
        if (windowInfo.renderCompleteSemaphores != null) {
            for (long handle : windowInfo.renderCompleteSemaphores) {
                vkDestroySemaphore(state.device.logical, handle, null);
            }
            windowInfo.renderCompleteSemaphores = null;
        }
        windowInfo.swapchainImages = null;
    }

    private void cleanupWindow(VulkanState.WindowInfo windowInfo) {
        cleanupSwapchainResources(windowInfo);
        vkDestroySwapchainKHR(state.device.logical, windowInfo.swapchainHandle, null);
        windowInfo.swapchainHandle = VK_NULL_HANDLE;
        vkDestroySurfaceKHR(state.instance, windowInfo.surfaceHandle, null);
        windowInfo.surfaceHandle = VK_NULL_HANDLE;
    }

    /**
     * Set up a surface for a window, and selects the physical device.
     *
     * @param window The window.
     */
    private void createSurface(@NonNull Window window) {
        VulkanState.WindowInfo windowInfo = new VulkanState.WindowInfo(window);
        if (state.windows.containsKey(window)) {
            log.error("Trying to create a surface for a window twice");
        }
        state.windows.put(window, windowInfo);

        checkError(
                glfwCreateWindowSurface(
                        state.instance, window.getWindowHandle(), null, longOutput));
        windowInfo.surfaceHandle = longOutput.get(0);

        state.device.physical = selectPhysicalDevice(windowInfo.surfaceHandle);
        state.physicalDevices.remove(state.device.physical);
        state.physicalDevices.forEach(this::cleanupPhysicalDeviceInfo);
        state.physicalDevices.clear();

        state.hasSeparateTransferQueue =
                state.device.physical.queueFamilyIndices.roomForSeparateTransferQueue();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceVulkan11Features enabledVk11Features =
                    VkPhysicalDeviceVulkan11Features.calloc(stack);
            enabledVk11Features.sType$Default().shaderDrawParameters(true).pNext(VK_NULL_HANDLE);

            VkPhysicalDeviceVulkan12Features enabledVk12Features =
                    VkPhysicalDeviceVulkan12Features.calloc(stack);
            enabledVk12Features
                    .sType$Default()
                    .bufferDeviceAddress(true)
                    .descriptorBindingPartiallyBound(true)
                    .descriptorBindingSampledImageUpdateAfterBind(true)
                    .descriptorBindingStorageBufferUpdateAfterBind(true)
                    .descriptorBindingUpdateUnusedWhilePending(true)
                    .descriptorBindingVariableDescriptorCount(true)
                    .descriptorIndexing(true)
                    .runtimeDescriptorArray(true)
                    .shaderSampledImageArrayNonUniformIndexing(true)
                    .pNext(enabledVk11Features.address());

            VkPhysicalDeviceVulkan13Features enabledVk13Features =
                    VkPhysicalDeviceVulkan13Features.calloc(stack);
            enabledVk13Features
                    .sType$Default()
                    .dynamicRendering(true)
                    .shaderDemoteToHelperInvocation(true)
                    .synchronization2(true)
                    .pNext(enabledVk12Features.address());

            VkPhysicalDeviceFeatures enabledVkFeatures = VkPhysicalDeviceFeatures.calloc(stack);
            enabledVkFeatures
                    .samplerAnisotropy(true)
                    .fillModeNonSolid(true)
                    // Scene draws use indirect commands with several draws and base instances
                    .multiDrawIndirect(true)
                    .drawIndirectFirstInstance(true);

            VkDeviceQueueCreateInfo.Buffer deviceQueueCreateInfos;

            if (!state.hasSeparateTransferQueue) {
                deviceQueueCreateInfos = VkDeviceQueueCreateInfo.calloc(1, stack);

                deviceQueueCreateInfos
                        .get(0)
                        .sType$Default()
                        .pNext(NULL)
                        .flags(0)
                        .queueFamilyIndex(state.device.physical.queueFamilyIndices.graphics())
                        .pQueuePriorities(stack.floats(1.0f));
            } else if (state.device.physical.queueFamilyIndices.transfer()
                    == state.device.physical.queueFamilyIndices.graphics()) {
                // We have 1 queue family, with 2 queues
                deviceQueueCreateInfos = VkDeviceQueueCreateInfo.calloc(2, stack);

                deviceQueueCreateInfos
                        .get(0)
                        .sType$Default()
                        .pNext(NULL)
                        .flags(0)
                        .queueFamilyIndex(state.device.physical.queueFamilyIndices.graphics())
                        .pQueuePriorities(stack.floats(1.0f, 0.5f));
            } else {
                // We have 2 queue families with 1 queue each
                deviceQueueCreateInfos = VkDeviceQueueCreateInfo.calloc(2, stack);

                deviceQueueCreateInfos
                        .get(0)
                        .sType$Default()
                        .pNext(NULL)
                        .flags(0)
                        .queueFamilyIndex(state.device.physical.queueFamilyIndices.graphics())
                        .pQueuePriorities(stack.floats(1.0f));

                deviceQueueCreateInfos
                        .get(1)
                        .sType$Default()
                        .pNext(NULL)
                        .flags(0)
                        .queueFamilyIndex(state.device.physical.queueFamilyIndices.transfer())
                        .pQueuePriorities(stack.floats(1.0f));
            }

            PointerBuffer deviceExtensionNames =
                    stack.mallocPointer(REQUIRED_DEVICE_EXTENSIONS.length + 1);
            Arrays.stream(REQUIRED_DEVICE_EXTENSIONS).forEach(deviceExtensionNames::put);
            if (state.device.physical.portabilitySubset) {
                // Required when the device has it, such as MoltenVK on macOS
                deviceExtensionNames.put(stack.ASCII(VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME));
            }
            deviceExtensionNames.flip();

            VkDeviceCreateInfo deviceCreateInfo = VkDeviceCreateInfo.create();
            deviceCreateInfo
                    .sType$Default()
                    .pEnabledFeatures(enabledVkFeatures)
                    .pNext(enabledVk13Features.address())
                    .pQueueCreateInfos(deviceQueueCreateInfos)
                    .ppEnabledExtensionNames(deviceExtensionNames);

            checkError(
                    vkCreateDevice(
                            state.device.physical.physicalDevice,
                            deviceCreateInfo,
                            null,
                            pointerOutput));

            state.device.logical =
                    new VkDevice(
                            pointerOutput.get(0),
                            state.device.physical.physicalDevice,
                            deviceCreateInfo);
        }

        vkGetDeviceQueue(
                state.device.logical,
                state.device.physical.queueFamilyIndices.graphics(),
                0,
                pointerOutput);
        final long graphicsQueueHandle = pointerOutput.get(0);
        state.device.graphicsQueue = new VkQueue(graphicsQueueHandle, state.device.logical);

        if (state.hasSeparateTransferQueue) {
            int queueIndex = 0;
            if (state.device.physical.queueFamilyIndices.transfer()
                    == state.device.physical.queueFamilyIndices.graphics()) {
                // We are stuck sharing a queue family with graphics, even though there's room for 2
                // queues
                queueIndex = 1;
            }
            vkGetDeviceQueue(
                    state.device.logical,
                    state.device.physical.queueFamilyIndices.transfer(),
                    queueIndex,
                    pointerOutput);
            final long queueHandle = pointerOutput.get(0);
            state.device.transferQueue = new VkQueue(queueHandle, state.device.logical);
        } else {
            state.device.transferQueue = state.device.graphicsQueue;
        }
    }

    /**
     * Create the swapchain for a window, along with its image views and semaphores. If the window
     * already has one it is replaced, in which case the device must be idle.
     *
     * @param windowInfo The window to create a swapchain for.
     * @return False if the window has no area right now (such as while minimized), in which case
     *     nothing changed and we should try again later.
     */
    private boolean createSwapchain(@NonNull VulkanState.WindowInfo windowInfo) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSurfaceCapabilitiesKHR capabilities = state.device.physical.capabilities;
            checkError(
                    vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
                            state.device.physical.physicalDevice,
                            windowInfo.surfaceHandle,
                            capabilities));

            final int width;
            final int height;
            if (capabilities.currentExtent().width() == 0xFFFF_FFFF) {
                // The swapchain decides the surface size, so match the framebuffer
                width =
                        Math.clamp(
                                windowInfo.window.getWidth(),
                                capabilities.minImageExtent().width(),
                                capabilities.maxImageExtent().width());
                height =
                        Math.clamp(
                                windowInfo.window.getHeight(),
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

            final long oldSwapchain = windowInfo.swapchainHandle;

            /*
             * NOTE(ches) The swapchain is BGRA as that's guaranteed to be everywhere, though our app generally operates
             * in RGBA. We'll just swizzle at the last possible second. It's UNORM rather than sRGB since, like the OpenGL
             * backend, we hand it colors that are already in display space. VK_PRESENT_MODE_FIFO_KHR is a v-synced mode
             * and the only mode guaranteed to be available everywhere.
             */
            VkSwapchainCreateInfoKHR swapchainCreateInfo =
                    VkSwapchainCreateInfoKHR.calloc(stack)
                            .sType$Default()
                            .surface(windowInfo.surfaceHandle)
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

            checkError(
                    vkCreateSwapchainKHR(
                            state.device.logical, swapchainCreateInfo, null, longOutput));
            windowInfo.swapchainHandle = longOutput.get(0);
            windowInfo.swapchainWidth = width;
            windowInfo.swapchainHeight = height;

            // Retired by creating the new one, and the device is idle so nothing is using it
            cleanupSwapchainResources(windowInfo);
            if (oldSwapchain != VK_NULL_HANDLE) {
                vkDestroySwapchainKHR(state.device.logical, oldSwapchain, null);
            }

            checkError(
                    vkGetSwapchainImagesKHR(
                            state.device.logical, windowInfo.swapchainHandle, intOutput, null));
            final int actualImageCount = intOutput.get(0);
            LongBuffer images = stack.callocLong(actualImageCount);
            checkError(
                    vkGetSwapchainImagesKHR(
                            state.device.logical, windowInfo.swapchainHandle, intOutput, images));

            windowInfo.swapchainImages = new long[actualImageCount];
            images.get(0, windowInfo.swapchainImages);
            windowInfo.swapchainImageViews = new long[actualImageCount];
            for (int i = 0; i < actualImageCount; i++) {
                VkImageViewCreateInfo viewCreateInfo =
                        VkImageViewCreateInfo.calloc(stack)
                                .sType$Default()
                                .image(windowInfo.swapchainImages[i])
                                .viewType(VK_IMAGE_VIEW_TYPE_2D)
                                .format(SWAPCHAIN_FORMAT)
                                .subresourceRange(
                                        VkImageSubresourceRange.calloc(stack)
                                                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                                .levelCount(1)
                                                .layerCount(1));
                checkError(
                        vkCreateImageView(state.device.logical, viewCreateInfo, null, longOutput));
                windowInfo.swapchainImageViews[i] = longOutput.get(0);
            }

            // One per image rather than per frame in flight, since presentation holds onto them
            windowInfo.renderCompleteSemaphores = new long[actualImageCount];
            VkSemaphoreCreateInfo semaphoreCreateInfo =
                    VkSemaphoreCreateInfo.calloc(stack).sType$Default();
            for (int i = 0; i < actualImageCount; i++) {
                checkError(
                        vkCreateSemaphore(
                                state.device.logical, semaphoreCreateInfo, null, longOutput));
                windowInfo.renderCompleteSemaphores[i] = longOutput.get(0);
            }

            windowInfo.lastSwapchainGeneration = System.currentTimeMillis();
            windowInfo.updateSwapchain = false;
        }
        return true;
    }

    private void createSynchronizationInfo() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreCreateInfo semaphoreCreateInfo =
                    VkSemaphoreCreateInfo.calloc(stack).sType$Default();
            VkFenceCreateInfo fenceCreateInfo =
                    VkFenceCreateInfo.calloc(stack)
                            .sType$Default()
                            .flags(VK_FENCE_CREATE_SIGNALED_BIT);

            assert state.fences.length == GraphicsManager.MAX_FRAMES_IN_FLIGHT;
            assert state.imageAcquiredSemaphores.length == GraphicsManager.MAX_FRAMES_IN_FLIGHT;

            for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
                checkError(vkCreateFence(state.device.logical, fenceCreateInfo, null, longOutput));
                state.fences[i] = longOutput.get(0);
                checkError(
                        vkCreateSemaphore(
                                state.device.logical, semaphoreCreateInfo, null, longOutput));
                state.imageAcquiredSemaphores[i] = longOutput.get(0);
            }
        }
    }

    /**
     * Set up the vulkan instance.
     *
     * @param window The window we are setting up to render with.
     * @throws RenderException If an unrecoverable issue occurs setting up vulkan.
     */
    private void createVulkanInstance(@NonNull Window window) {
        try (BufferHolder freeThese = new BufferHolder()) {
            PointerBuffer requiredExtensionNames = PointerBuffer.allocateDirect(64);

            populateRequiredExtensions(requiredExtensionNames);
            final List<String> availableExtensions = getAvailableInstanceExtensions();

            /*
             * Implementations that aren't fully conformant, like MoltenVK on macOS, are only listed if we ask for them.
             * Through the Vulkan loader, they are hidden without this.
             */
            final boolean portability =
                    availableExtensions.contains(VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME);
            if (portability) {
                ByteBuffer portabilityName =
                        MemoryUtil.memASCII(VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME);
                freeThese.add(portabilityName);
                requiredExtensionNames.put(portabilityName);
            }

            PointerBuffer requiredLayerNames = null;
            boolean validation = ENABLE_VALIDATION;

            if (validation && !availableExtensions.contains(VK_EXT_DEBUG_UTILS_EXTENSION_NAME)) {
                log.warn(
                        "Missing {}, running without validation",
                        VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
                validation = false;
            }

            if (validation) {
                requiredLayerNames = PointerBuffer.allocateDirect(VALIDATION_LAYERS.length);
                for (String validationLayer : VALIDATION_LAYERS) {
                    ByteBuffer converted = MemoryUtil.memASCII(validationLayer);
                    freeThese.add(converted);
                    requiredLayerNames.put(converted);
                }
                requiredLayerNames.flip();

                checkError(vkEnumerateInstanceLayerProperties(intOutput, null));

                VkLayerProperties.Buffer availableLayers =
                        VkLayerProperties.create(intOutput.get(0));
                checkError(vkEnumerateInstanceLayerProperties(intOutput, availableLayers));

                // Not installed with the Vulkan runtime or MoltenVK, only the SDK
                validation = checkLayers(availableLayers, requiredLayerNames);
                if (!validation) {
                    requiredLayerNames = null;
                }
            }

            if (validation) {
                ByteBuffer debugUtils = MemoryUtil.memASCII(VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
                freeThese.add(debugUtils);
                requiredExtensionNames.put(debugUtils);
            }
            requiredExtensionNames.flip();

            ByteBuffer appName = MemoryUtil.memUTF8(window.getTitle());
            freeThese.add(appName);
            ByteBuffer engineName = MemoryUtil.memUTF8("Ikala Engine");
            freeThese.add(engineName);

            var applicationInfo =
                    VkApplicationInfo.create()
                            .sType$Default()
                            .pNext(NULL)
                            .pApplicationName(appName)
                            .applicationVersion(1)
                            .pEngineName(engineName)
                            .engineVersion(1)
                            .apiVersion(VK_MAKE_API_VERSION(0, 1, 3, 0));

            var instanceInfo =
                    VkInstanceCreateInfo.create()
                            .sType$Default()
                            .pNext(NULL)
                            .flags(
                                    portability
                                            ? VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR
                                            : 0)
                            .pApplicationInfo(applicationInfo)
                            .ppEnabledLayerNames(requiredLayerNames)
                            .ppEnabledExtensionNames(requiredExtensionNames);

            VkDebugUtilsMessengerCreateInfoEXT debugCreateInfo = null;

            if (validation) {
                debugCreateInfo =
                        VkDebugUtilsMessengerCreateInfoEXT.create()
                                .sType$Default()
                                .pNext(NULL)
                                .flags(0)
                                .messageSeverity(
                                        VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT
                                                | VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT)
                                .messageType(
                                        VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT
                                                | VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT
                                                | VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)
                                .pfnUserCallback(debugLogger)
                                .pUserData(NULL);
                instanceInfo.pNext(debugCreateInfo.address());
            }

            int error = vkCreateInstance(instanceInfo, null, pointerOutput);
            if (error == VK_ERROR_INCOMPATIBLE_DRIVER) {
                var message = "Could not find a compatible Vulkan driver";
                log.error(message);
                throw new RenderException(message);
            }
            if (error == VK_ERROR_EXTENSION_NOT_PRESENT) {
                var message = "Could not find a required Vulkan extension";
                log.error(message);
                throw new RenderException(message);
            }
            if (error != 0) {
                var message =
                        SafeResourceLoader.format(
                                "Failed to create a Vulkan instance, error code {}", error);
                log.error(message);
                throw new RenderException(message);
            }

            state.instance = new VkInstance(pointerOutput.get(0), instanceInfo);

            if (validation) {
                // The create info chained above only covers instance creation and destruction
                checkError(
                        vkCreateDebugUtilsMessengerEXT(
                                state.instance, debugCreateInfo, null, longOutput));
                state.debugMessenger = longOutput.get(0);
            }

            checkError(vkEnumeratePhysicalDevices(state.instance, intOutput, null));

            if (intOutput.get(0) <= 0) {
                log.error("Could not find number of physical devices");
                return;
            }
            PointerBuffer physicalDevices = PointerBuffer.allocateDirect(intOutput.get(0));
            checkError(vkEnumeratePhysicalDevices(state.instance, intOutput, physicalDevices));

            for (int i = 0; i < physicalDevices.limit(); ++i) {
                VulkanState.PhysicalDeviceInfo deviceInfo = new VulkanState.PhysicalDeviceInfo();
                deviceInfo.physicalDevice =
                        new VkPhysicalDevice(physicalDevices.get(i), state.instance);

                vkGetPhysicalDeviceFeatures(deviceInfo.physicalDevice, deviceInfo.deviceFeatures);
                vkGetPhysicalDeviceProperties(
                        deviceInfo.physicalDevice, deviceInfo.deviceProperties);

                try (MemoryStack stack = MemoryStack.stackPush()) {
                    VkPhysicalDeviceVulkan12Properties vk12Properties =
                            VkPhysicalDeviceVulkan12Properties.calloc(stack).sType$Default();

                    VkPhysicalDeviceProperties2 deviceProperties2 =
                            VkPhysicalDeviceProperties2.calloc(stack)
                                    .sType$Default()
                                    .pNext(vk12Properties);

                    vkGetPhysicalDeviceProperties2(deviceInfo.physicalDevice, deviceProperties2);

                    deviceInfo.maxBindlessImages =
                            Math.min(
                                    deviceInfo.maxBindlessImages,
                                    Math.min(
                                            vk12Properties
                                                    .maxDescriptorSetUpdateAfterBindSampledImages(),
                                            vk12Properties
                                                    .maxPerStageDescriptorUpdateAfterBindSampledImages()));
                }

                try (MemoryStack stack = MemoryStack.stackPush()) {
                    int depthFormat = VK_FORMAT_UNDEFINED;
                    final int[] depthFormatList =
                            new int[] {VK_FORMAT_D32_SFLOAT_S8_UINT, VK_FORMAT_D24_UNORM_S8_UINT};
                    for (int format : depthFormatList) {
                        VkFormatProperties2 formatProperties =
                                VkFormatProperties2.calloc(stack).sType$Default();
                        vkGetPhysicalDeviceFormatProperties2(
                                deviceInfo.physicalDevice, format, formatProperties);
                        if ((formatProperties.formatProperties().optimalTilingFeatures()
                                        & VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT)
                                != 0) {
                            depthFormat = format;
                            break;
                        }
                    }
                    if (depthFormat == VK_FORMAT_UNDEFINED) {
                        /*
                         * Can't happen (tm) unless the spec changes, log error and barrel forwards until something breaks
                         */
                        log.error("Couldn't find a desirable depth format");
                    }
                    deviceInfo.depthFormat = depthFormat;
                }

                vkGetPhysicalDeviceQueueFamilyProperties(
                        deviceInfo.physicalDevice, intOutput, null);
                // NOTE(ches) it's important that we use a buffer that doesn't need manual freeing
                deviceInfo.queueFamilyProperties = VkQueueFamilyProperties.create(intOutput.get(0));
                vkGetPhysicalDeviceQueueFamilyProperties(
                        deviceInfo.physicalDevice, intOutput, deviceInfo.queueFamilyProperties);

                state.physicalDevices.add(deviceInfo);
            }
        }
    }

    /**
     * Process resource deletion.
     *
     * @param entry The deletion queue entry to handle.
     */
    private void deleteResource(@NonNull DeletionQueue.Entry entry) {
        switch (entry.type()) {
            case BUFFER -> {
                var buffer = (SharedBuffer) entry.resource();
                state.deferFree(() -> SharedBuffer.free(buffer, state));
            }
            case SHADER -> {
                var shader = (ShaderVulkan) entry.resource();
                shader.free();
            }
            case TEXTURE -> textureLoader.delete((TextureInfoVulkan) entry.resource());
        }
    }

    /**
     * Fetch the current pipeline config.
     *
     * @return The current pipeline configuration.
     * @see RenderConfig
     */
    public int getPipelineConfig() {
        return renderConfig;
    }

    /**
     * Return the texture loader for this instance.
     *
     * @return The texture loader.
     */
    public TextureLoaderVulkan getTextureLoader() {
        return textureLoader;
    }

    /**
     * Initialize the instance to prepare for rendering for the first time.
     *
     * @param window The window we will be rendering to.
     * @return Whether we were successful. False if there were (unrecoverable) errors.
     */
    public boolean initialize(@NonNull Window window) {
        createVulkanInstance(window);
        createSurface(window);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VmaVulkanFunctions vkFunctions =
                    VmaVulkanFunctions.calloc(stack).set(state.instance, state.device.logical);

            VmaAllocatorCreateInfo vmaAllocatorCreateInfo =
                    VmaAllocatorCreateInfo.calloc(stack)
                            .flags(VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT)
                            .physicalDevice(state.device.physical.physicalDevice)
                            .device(state.device.logical)
                            .pVulkanFunctions(vkFunctions)
                            .instance(state.instance);
            checkError(vmaCreateAllocator(vmaAllocatorCreateInfo, pointerOutput));
            state.vmaAllocator = pointerOutput.get(0);
        }

        VulkanState.WindowInfo windowInfo = state.windows.get(window);
        if (!createSwapchain(windowInfo)) {
            // Started minimized, try again once it has a size
            windowInfo.updateSwapchain = true;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandPoolCreateInfo commandPoolCreateInfo =
                    VkCommandPoolCreateInfo.calloc(stack)
                            .sType$Default()
                            .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                            .queueFamilyIndex(state.device.physical.queueFamilyIndices.graphics());
            checkError(
                    vkCreateCommandPool(
                            state.device.logical, commandPoolCreateInfo, null, longOutput));
            state.commandPoolGraphics = longOutput.get(0);

            VkCommandBufferAllocateInfo commandBufferAllocateInfo =
                    VkCommandBufferAllocateInfo.calloc(stack)
                            .sType$Default()
                            .commandPool(state.commandPoolGraphics)
                            .commandBufferCount(GraphicsManager.MAX_FRAMES_IN_FLIGHT);

            PointerBuffer commandBuffers =
                    stack.callocPointer(GraphicsManager.MAX_FRAMES_IN_FLIGHT);
            checkError(
                    vkAllocateCommandBuffers(
                            state.device.logical, commandBufferAllocateInfo, commandBuffers));
            for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
                state.commandBuffersGraphics[i] =
                        new VkCommandBuffer(commandBuffers.get(i), state.device.logical);
            }
        }

        if (state.hasSeparateTransferQueue) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkCommandPoolCreateInfo commandPoolCreateInfo =
                        VkCommandPoolCreateInfo.calloc(stack)
                                .sType$Default()
                                .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                                .queueFamilyIndex(
                                        state.device.physical.queueFamilyIndices.transfer());
                checkError(
                        vkCreateCommandPool(
                                state.device.logical, commandPoolCreateInfo, null, longOutput));
                state.commandPoolTransfer = longOutput.get(0);

                VkCommandBufferAllocateInfo commandBufferAllocateInfo =
                        VkCommandBufferAllocateInfo.calloc(stack)
                                .sType$Default()
                                .commandPool(state.commandPoolTransfer)
                                .commandBufferCount(1);

                PointerBuffer commandBuffers = stack.callocPointer(1);
                checkError(
                        vkAllocateCommandBuffers(
                                state.device.logical, commandBufferAllocateInfo, commandBuffers));
                state.commandBufferTransfer =
                        new VkCommandBuffer(commandBuffers.get(0), state.device.logical);
            }
        }

        state.immediateCommands = new ImmediateCommands(state);
        state.bindlessTextures = new BindlessTextures(state);
        state.textureRegistry = new TextureRegistry(state.bindlessTextures.getCapacity());
        state.stagingRing = new StagingRing(state);
        state.textureUploads = new TextureUploads();
        textureLoader = new TextureLoaderVulkan(state);
        shaderMap = new ShaderMap();
        initializeShaders();
        initializeGui(window);
        pipelineManager = new PipelineManagerVulkan(window, shaderMap, state);
        renderConfig = RenderConfig.builder().withGui().build();
        pipeline = pipelineManager.getPipeline(renderConfig);

        createSynchronizationInfo();
        return true;
    }

    /** Set up the animation shader and uniforms. */
    private void initializeAnimationShader() {
        List<ShaderVulkan.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new ShaderVulkan.ShaderModuleData(
                        "shaders/anim.comp",
                        ShaderVulkan.Type.COMPUTE,
                        ShaderVulkan.Location.BUNDLED));
        var shaderProgram = new ShaderVulkan(shaderModuleDataList, state);

        shaderMap.addShader(RenderStage.Type.ANIMATION, shaderProgram);
    }

    /**
     * Set up the default filter shader.
     *
     * @throws ShaderException If the default filter could not be found or loaded properly.
     */
    private void initializeFilterShader() {
        List<ShaderVulkan.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new ShaderVulkan.ShaderModuleData(
                        "shaders/filters/default.vert",
                        ShaderVulkan.Type.VERTEX,
                        ShaderVulkan.Location.BUNDLED));
        shaderModuleDataList.add(
                new ShaderVulkan.ShaderModuleData(
                        "shaders/filters/default.frag",
                        ShaderVulkan.Type.FRAGMENT,
                        ShaderVulkan.Location.BUNDLED));
        var shaderProgram = new ShaderVulkan(shaderModuleDataList, state);

        shaderMap.addShader(RenderStage.Type.FILTER, shaderProgram);
    }

    /**
     * Create an IkGui context and configure it.
     *
     * @param window The window to pull display info from.
     */
    private void initializeGui(@NonNull Window window) {
        IkGui.createContext();

        IkIO ikIO = IkGui.getIO();
        ikIO.iniFilename = null;
        // Tab, arrow keys and gamepads move between items, so menus work without a mouse
        ikIO.configFlags |= ConfigFlags.NAV_ENABLE_KEYBOARD | ConfigFlags.NAV_ENABLE_GAMEPAD;
        ikIO.displaySize.set(window.getWidth(), window.getHeight());
        window.setupIkGuiPlatformIO();
    }

    /** Set up the GUI shader and uniforms. */
    private void initializeGuiShader() {
        List<ShaderVulkan.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new ShaderVulkan.ShaderModuleData(
                        "shaders/ikgui.vert",
                        ShaderVulkan.Type.VERTEX,
                        ShaderVulkan.Location.BUNDLED));
        shaderModuleDataList.add(
                new ShaderVulkan.ShaderModuleData(
                        "shaders/ikgui.frag",
                        ShaderVulkan.Type.FRAGMENT,
                        ShaderVulkan.Location.BUNDLED));
        var shaderProgram = new ShaderVulkan(shaderModuleDataList, state);

        shaderMap.addShader(RenderStage.Type.GUI, shaderProgram);
    }

    /** Set up the light shader and uniforms. */
    private void initializeLightShader() {
        List<ShaderVulkan.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new ShaderVulkan.ShaderModuleData(
                        "shaders/lights.vert",
                        ShaderVulkan.Type.VERTEX,
                        ShaderVulkan.Location.BUNDLED));
        shaderModuleDataList.add(
                new ShaderVulkan.ShaderModuleData(
                        "shaders/lights.frag",
                        ShaderVulkan.Type.FRAGMENT,
                        ShaderVulkan.Location.BUNDLED));
        var shaderProgram = new ShaderVulkan(shaderModuleDataList, state);

        shaderMap.addShader(RenderStage.Type.LIGHT, shaderProgram);
    }

    /**
     * Set up a model before rendering for the first time. For example, creating buffers. This must
     * only be called once for a model, and only after it's fully loaded (e.g. animations set up).
     *
     * @param model The model to set up.
     */
    public void initializeModel(@NonNull Model model) {
        if (model.isAnimated()) {
            // Filled out later
            model.setEntityAnimationOffsetsBuffer(
                    SharedBuffer.allocate(0, state, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT));

            int totalSize = 0;
            for (Model.Animation animation : model.getAnimationList()) {
                totalSize += animation.frameData().length;
            }
            SharedBuffer animationBuffer =
                    SharedBuffer.allocate(totalSize, state, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            model.setAnimationBuffer(animationBuffer);

            // NOTE(ches) memByteBuffer rejects the null mapping of an empty buffer
            if (totalSize > 0) {
                ByteBuffer animations =
                        MemoryUtil.memByteBuffer(
                                animationBuffer.allocationInfo.pMappedData(), totalSize);
                for (Model.Animation animation : model.getAnimationList()) {
                    animations.put(animation.frameData());
                }
            }

            for (MeshData meshData : model.getMeshDataList()) {
                // Filled out later. Written by the animation compute shader, read as vertices.
                meshData.setAnimationTargetBuffer(
                        SharedBuffer.allocate(
                                0,
                                state,
                                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
                                        | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT));

                final byte[] boneWeights = meshData.getBoneWeightData();
                SharedBuffer boneWeightBuffer =
                        SharedBuffer.allocate(
                                boneWeights.length, state, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
                meshData.setBoneWeightBuffer(boneWeightBuffer);
                if (boneWeights.length > 0) {
                    MemoryUtil.memByteBuffer(
                                    boneWeightBuffer.allocationInfo.pMappedData(),
                                    boneWeights.length)
                            .put(boneWeights);
                }
            }
        }

        for (MeshData meshData : model.getMeshDataList()) {
            final int vertexSize = meshData.getVertexData().length * Float.BYTES;
            final int indexSize = meshData.getIndices().length * Integer.BYTES;

            ByteBuffer vertexData = MemoryUtil.memAlloc(vertexSize);
            ByteBuffer indexData = MemoryUtil.memAlloc(indexSize);

            MemoryUtil.memCopy(meshData.getVertexData(), vertexData);
            MemoryUtil.memCopy(meshData.getIndices(), indexData);

            MemoryUtil.memCopy(
                    MemoryUtil.memAddress(vertexData),
                    ((SharedBuffer) meshData.getVertexBuffer()).allocationInfo.pMappedData(),
                    vertexSize);
            MemoryUtil.memCopy(
                    MemoryUtil.memAddress(indexData),
                    ((SharedBuffer) meshData.getIndexBuffer()).allocationInfo.pMappedData(),
                    indexSize);

            MemoryUtil.memFree(indexData);
            MemoryUtil.memFree(vertexData);
        }
    }

    /** Set up the scene shader and uniforms. */
    private void initializeSceneShader() {
        List<ShaderVulkan.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new ShaderVulkan.ShaderModuleData(
                        "shaders/scene.vert",
                        ShaderVulkan.Type.VERTEX,
                        ShaderVulkan.Location.BUNDLED));
        shaderModuleDataList.add(
                new ShaderVulkan.ShaderModuleData(
                        "shaders/scene.frag",
                        ShaderVulkan.Type.FRAGMENT,
                        ShaderVulkan.Location.BUNDLED));
        var shaderProgram = new ShaderVulkan(shaderModuleDataList, state);

        shaderMap.addShader(RenderStage.Type.SCENE, shaderProgram);
    }

    /**
     * Set up the shaders for each stage.
     *
     * @throws ShaderException If there was a problem finding or loading shaders.
     */
    private void initializeShaders() {
        initializeAnimationShader();
        initializeShadowShader();
        initializeSceneShader();
        initializeLightShader();
        initializeSkyboxShader();
        initializeDebugShader();
        initializeFilterShader();
        initializeGuiShader();
    }

    /** Set up the shadow shader and uniforms. */
    private void initializeShadowShader() {
        List<ShaderVulkan.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new ShaderVulkan.ShaderModuleData(
                        "shaders/shadow.vert",
                        ShaderVulkan.Type.VERTEX,
                        ShaderVulkan.Location.BUNDLED));
        var shaderProgram = new ShaderVulkan(shaderModuleDataList, state);

        shaderMap.addShader(RenderStage.Type.SHADOW, shaderProgram);
    }

    /** Set up the skybox shader and uniforms. */
    private void initializeSkyboxShader() {
        List<ShaderVulkan.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new ShaderVulkan.ShaderModuleData(
                        "shaders/skybox.vert",
                        ShaderVulkan.Type.VERTEX,
                        ShaderVulkan.Location.BUNDLED));
        shaderModuleDataList.add(
                new ShaderVulkan.ShaderModuleData(
                        "shaders/skybox.frag",
                        ShaderVulkan.Type.FRAGMENT,
                        ShaderVulkan.Location.BUNDLED));
        var shaderProgram = new ShaderVulkan(shaderModuleDataList, state);

        shaderMap.addShader(RenderStage.Type.SKYBOX, shaderProgram);
    }

    /** Set up the debug shape and debug normal shaders. */
    private void initializeDebugShader() {
        List<ShaderVulkan.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new ShaderVulkan.ShaderModuleData(
                        "shaders/debug.vert",
                        ShaderVulkan.Type.VERTEX,
                        ShaderVulkan.Location.BUNDLED));
        shaderModuleDataList.add(
                new ShaderVulkan.ShaderModuleData(
                        "shaders/debug.frag",
                        ShaderVulkan.Type.FRAGMENT,
                        ShaderVulkan.Location.BUNDLED));
        var shaderProgram = new ShaderVulkan(shaderModuleDataList, state);

        shaderMap.addShader(RenderStage.Type.DEBUG, shaderProgram);

        List<ShaderVulkan.ShaderModuleData> normalsModules = new ArrayList<>();
        normalsModules.add(
                new ShaderVulkan.ShaderModuleData(
                        "shaders/debug_normals.vert",
                        ShaderVulkan.Type.VERTEX,
                        ShaderVulkan.Location.BUNDLED));
        normalsModules.add(
                new ShaderVulkan.ShaderModuleData(
                        "shaders/debug.frag",
                        ShaderVulkan.Type.FRAGMENT,
                        ShaderVulkan.Location.BUNDLED));
        shaderMap.addShader(
                RenderStage.Type.DEBUG_NORMALS, new ShaderVulkan(normalsModules, state));
    }

    /**
     * Do any periodic resource management that is required. Things like deleting resources in the
     * resource queue.
     */
    public void processResources() {
        DeletionQueue.Entry toDelete = GraphicsManager.getDeletionQueue().pop();
        while (toDelete != null) {
            deleteResource(toDelete);
            toDelete = GraphicsManager.getDeletionQueue().pop();
        }
    }

    /**
     * Fetch the Vulkan state.
     *
     * @return The state.
     */
    public VulkanState getState() {
        return state;
    }

    /**
     * Recreate the swapchain to match the window, and grow the render targets if needed.
     *
     * @param windowInfo The window whose swapchain is out of date.
     * @return False if the window has no area right now, so there is nothing to render to.
     */
    private boolean regenerateSwapchain(@NonNull VulkanState.WindowInfo windowInfo) {
        checkError(vkDeviceWaitIdle(state.device.logical));

        if (!createSwapchain(windowInfo)) {
            return false;
        }

        pipelineManager.resize(state, windowInfo.swapchainWidth, windowInfo.swapchainHeight);
        return true;
    }

    /*
     * TODO(ches) have this render to any relevant windows, rather than pass one in, once the instance owns windows
     */
    /**
     * Render a scene on the window.
     *
     * @param scene The scene to render.
     */
    public void render(@NonNull Scene scene, @NonNull Window window) {
        if (pipeline == PipelineManagerVulkan.ERROR_PIPELINE) {
            return;
        }
        VulkanState.WindowInfo windowInfo = state.windows.get(window);

        if (windowInfo.updateSwapchain
                && (!shouldRegenerateSwapchain(windowInfo) || !regenerateSwapchain(windowInfo))) {
            // Resizing or minimized, skip the frame until we have a swapchain that fits
            return;
        }

        longOutput.put(0, state.fences[state.frameIndex]);
        checkError(vkWaitForFences(state.device.logical, longOutput, true, Long.MAX_VALUE));
        // The GPU is done with this frame index, so nothing can be using these anymore
        state.runDeferredFrees(state.frameIndex);

        final boolean acquired =
                checkSwapchain(
                        vkAcquireNextImageKHR(
                                state.device.logical,
                                windowInfo.swapchainHandle,
                                Long.MAX_VALUE,
                                state.imageAcquiredSemaphores[state.frameIndex],
                                VK_NULL_HANDLE,
                                intOutput),
                        windowInfo);
        if (!acquired) {
            /*
             * The fence is still signaled since we haven't reset it, and the semaphore was never
             * signaled, so we can just try again with this frame index once the swapchain is
             * regenerated.
             */
            return;
        }
        windowInfo.currentSwapchainIndex = intOutput.get(0);

        // Only reset once we know we are going to submit something that will signal it
        longOutput.put(0, state.fences[state.frameIndex]);
        checkError(vkResetFences(state.device.logical, longOutput));

        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[state.frameIndex];
        checkError(vkResetCommandBuffer(commandBuffer, 0));

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferBeginInfo commandBufferBeginInfo =
                    VkCommandBufferBeginInfo.calloc(stack)
                            .sType$Default()
                            .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            checkError(vkBeginCommandBuffer(commandBuffer, commandBufferBeginInfo));
        }

        // Before anything that could sample the textures
        state.textureUploads.record(state, commandBuffer, textureLoader);

        // This will record the command buffer
        pipeline.render(scene, windowInfo.window, state);

        checkError(vkEndCommandBuffer(commandBuffer));

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreSubmitInfo.Buffer waitSemaphoreInfos =
                    VkSemaphoreSubmitInfo.calloc(1, stack);
            waitSemaphoreInfos
                    .get(0)
                    .sType$Default()
                    .semaphore(state.imageAcquiredSemaphores[state.frameIndex])
                    .stageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT);

            VkCommandBufferSubmitInfo.Buffer commandBufferSubmitInfos =
                    VkCommandBufferSubmitInfo.calloc(1, stack);
            commandBufferSubmitInfos.get(0).sType$Default().commandBuffer(commandBuffer);
            VkSemaphoreSubmitInfo.Buffer signalSemaphoreInfos =
                    VkSemaphoreSubmitInfo.calloc(1, stack);
            signalSemaphoreInfos
                    .get(0)
                    .sType$Default()
                    .semaphore(
                            windowInfo.renderCompleteSemaphores[windowInfo.currentSwapchainIndex])
                    .stageMask(VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT);

            VkSubmitInfo2.Buffer submitInfos = VkSubmitInfo2.calloc(1, stack);
            submitInfos
                    .get(0)
                    .sType$Default()
                    .pWaitSemaphoreInfos(waitSemaphoreInfos)
                    .pCommandBufferInfos(commandBufferSubmitInfos)
                    .pSignalSemaphoreInfos(signalSemaphoreInfos);
            checkError(
                    vkQueueSubmit2(
                            state.device.graphicsQueue,
                            submitInfos,
                            state.fences[state.frameIndex]));

            LongBuffer waitSemaphores =
                    stack.longs(
                            windowInfo.renderCompleteSemaphores[windowInfo.currentSwapchainIndex]);
            LongBuffer swapchains = stack.longs(windowInfo.swapchainHandle);
            IntBuffer imageIndices = stack.ints(windowInfo.currentSwapchainIndex);

            VkPresentInfoKHR presentInfo =
                    VkPresentInfoKHR.calloc(stack)
                            .sType$Default()
                            .pWaitSemaphores(waitSemaphores)
                            .pSwapchains(swapchains)
                            .swapchainCount(1)
                            .pImageIndices(imageIndices);
            // The graphics queue family is required to support presenting during device selection
            checkSwapchain(vkQueuePresentKHR(state.device.graphicsQueue, presentInfo), windowInfo);
        }

        state.frameIndex = (state.frameIndex + 1) % GraphicsManager.MAX_FRAMES_IN_FLIGHT;
        windowInfo.currentSwapchainIndex = VulkanState.WindowInfo.INVALID_SWAPCHAIN_INDEX;
    }

    /**
     * Update the buffers and GUI when we resize the screen.
     *
     * @param width The new screen width in pixels.
     * @param height The new screen height in pixels.
     */
    public void resize(@NonNull Window window, int width, int height) {
        VulkanState.WindowInfo windowInfo = state.windows.get(window);
        windowInfo.updateSwapchain = true;
        windowInfo.lastResize = System.currentTimeMillis();
        IkIO ikIO = IkGui.getIO();
        ikIO.displaySize.set(width, height);
    }

    /**
     * Give a device a score based on how suitable it is, for use in device selection.
     *
     * @param deviceInfo The device we want to score.
     * @param surfaceHandle The surface handle, for checking swapchain support.
     */
    private int scoreDevice(
            @NonNull VulkanState.PhysicalDeviceInfo deviceInfo, final long surfaceHandle) {
        updateQueueFamilies(deviceInfo, surfaceHandle);

        /* Quick checks to rule out the device entirely. */

        if (deviceInfo.queueFamilyIndices.graphics() == QueueFamilyIndices.MISSING
                || deviceInfo.queueFamilyIndices.present() == QueueFamilyIndices.MISSING) {
            /*
             * We either can't deal with graphics at all, or can't present them with this device. We don't really
             * need to check transfer support, because it can always use the transfer queue as a backup. Though,
             * we'll reward having a separate queue lower down.
             */
            return 0;
        }

        if (deviceInfo.queueFamilyIndices.graphics() != deviceInfo.queueFamilyIndices.present()) {
            /*
             * We present on the graphics queue. Every desktop GPU has a family that does both, so it's not worth
             * transferring swapchain image ownership between queues for the hypothetical ones that don't.
             */
            log.debug(
                    "Skipping {}, no queue family supports both graphics and present",
                    deviceInfo.deviceProperties.deviceNameString());
            return 0;
        }

        if (VK_API_VERSION_MAJOR(deviceInfo.deviceProperties.apiVersion()) == 1
                && VK_API_VERSION_MINOR(deviceInfo.deviceProperties.apiVersion()) < 3) {
            log.debug(
                    "Skipping {}, it only supports Vulkan {}.{}",
                    deviceInfo.deviceProperties.deviceNameString(),
                    VK_API_VERSION_MAJOR(deviceInfo.deviceProperties.apiVersion()),
                    VK_API_VERSION_MINOR(deviceInfo.deviceProperties.apiVersion()));
            return 0;
        }

        if (!supportsRequiredFeatures(deviceInfo)) {
            return 0;
        }

        if (!supportsRequiredExtensions(deviceInfo)) {
            return 0;
        }

        updateSwapChainSupport(deviceInfo, surfaceHandle);
        if (deviceInfo.formats == null
                || deviceInfo.presentModes == null
                || deviceInfo.presentModes.length == 0
                || !supportsSwapchainFormat(deviceInfo.formats)) {
            deviceInfo.capabilities = null;
            deviceInfo.formats = null;
            deviceInfo.presentModes = null;
            return 0;
        }
        int score = 0;

        if (deviceInfo.queueFamilyIndices.transfer() != deviceInfo.queueFamilyIndices.graphics()) {
            score += 1_000_000;
        } else if (deviceInfo.queueFamilyIndices.roomForSeparateTransferQueue()) {
            score += 500_000;
        }

        if (deviceInfo.deviceProperties.deviceType() == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) {
            score += 10_000_000;
        }

        /* I expect these values to be ~8-16K */
        score += deviceInfo.deviceProperties.limits().maxImageDimension2D();

        return score;
    }

    /**
     * Select a physical device to use.
     *
     * @param surfaceHandle The surface handle, for checking swapchain support.
     * @return The selected physical device.
     * @throws RenderException If no device could possibly work.
     */
    private VulkanState.PhysicalDeviceInfo selectPhysicalDevice(final long surfaceHandle) {
        VulkanState.PhysicalDeviceInfo bestChoice = null;
        int highestScore = Integer.MIN_VALUE;

        for (VulkanState.PhysicalDeviceInfo device : state.physicalDevices) {
            int score = scoreDevice(device, surfaceHandle);
            // 0 means the device can't run the engine at all
            if (score > 0 && score > highestScore) {
                highestScore = score;
                bestChoice = device;
            }
        }

        if (null == bestChoice) {
            final var message = "No valid physical device found";
            log.error(message);
            throw new RenderException(message);
        }

        return bestChoice;
    }

    /**
     * Update graphics quality. This is a very heavy operation, and should only be used internally.
     *
     * @param oldQuality The new quality to use, so we know what sort of resources to clean up.
     * @param newQuality The new quality to use, so we know what to set up for.
     */
    public void setQuality(
            @NonNull GraphicsSettings.Quality oldQuality,
            @NonNull GraphicsSettings.Quality newQuality) {
        checkError(vkDeviceWaitIdle(state.device.logical));
        // TODO(ches) set up or clean up as needed
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

    /**
     * Check if the specified device supports the required device extensions, and note optional ones
     * that we have to enable if present.
     *
     * @param deviceInfo The device to check. Updated with the optional extensions it has.
     * @return Whether we found the support that we need.
     */
    private boolean supportsRequiredExtensions(@NonNull VulkanState.PhysicalDeviceInfo deviceInfo) {
        List<String> missingExtensions = new ArrayList<>(REQUIRED_DEVICE_EXTENSION_NAMES);

        checkError(
                vkEnumerateDeviceExtensionProperties(
                        deviceInfo.physicalDevice, (String) null, intOutput, null));
        // Hundreds of these is too big for the stack, so it's garbage collected instead
        var properties = VkExtensionProperties.create(intOutput.get(0));
        checkError(
                vkEnumerateDeviceExtensionProperties(
                        deviceInfo.physicalDevice, (String) null, intOutput, properties));

        deviceInfo.portabilitySubset = false;
        for (int i = 0; i < properties.limit(); ++i) {
            var extension = properties.get(i).extensionNameString();
            missingExtensions.remove(extension);
            if (VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME.equals(extension)) {
                deviceInfo.portabilitySubset = true;
            }
        }

        if (!missingExtensions.isEmpty()) {
            log.debug(
                    "Skipping {}, missing extensions {}",
                    deviceInfo.deviceProperties.deviceNameString(),
                    String.join(", ", missingExtensions));
        }
        return missingExtensions.isEmpty();
    }

    /**
     * Check if the specified device supports every feature we enable when creating the logical
     * device. Keep this in sync with {@link #createSurface(Window)}.
     *
     * @param deviceInfo The device to check.
     * @return Whether all the features we need are supported.
     */
    private boolean supportsRequiredFeatures(@NonNull VulkanState.PhysicalDeviceInfo deviceInfo) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceVulkan11Features vk11 =
                    VkPhysicalDeviceVulkan11Features.calloc(stack).sType$Default();
            VkPhysicalDeviceVulkan12Features vk12 =
                    VkPhysicalDeviceVulkan12Features.calloc(stack)
                            .sType$Default()
                            .pNext(vk11.address());
            VkPhysicalDeviceVulkan13Features vk13 =
                    VkPhysicalDeviceVulkan13Features.calloc(stack)
                            .sType$Default()
                            .pNext(vk12.address());
            VkPhysicalDeviceFeatures2 features =
                    VkPhysicalDeviceFeatures2.calloc(stack).sType$Default().pNext(vk13.address());
            vkGetPhysicalDeviceFeatures2(deviceInfo.physicalDevice, features);
            VkPhysicalDeviceFeatures vk10 = features.features();

            Map<String, Boolean> required = new LinkedHashMap<>();
            required.put("samplerAnisotropy", vk10.samplerAnisotropy());
            required.put("fillModeNonSolid", vk10.fillModeNonSolid());
            required.put("multiDrawIndirect", vk10.multiDrawIndirect());
            required.put("drawIndirectFirstInstance", vk10.drawIndirectFirstInstance());
            required.put("shaderDrawParameters", vk11.shaderDrawParameters());
            required.put("bufferDeviceAddress", vk12.bufferDeviceAddress());
            required.put("descriptorBindingPartiallyBound", vk12.descriptorBindingPartiallyBound());
            required.put(
                    "descriptorBindingSampledImageUpdateAfterBind",
                    vk12.descriptorBindingSampledImageUpdateAfterBind());
            required.put(
                    "descriptorBindingStorageBufferUpdateAfterBind",
                    vk12.descriptorBindingStorageBufferUpdateAfterBind());
            required.put(
                    "descriptorBindingUpdateUnusedWhilePending",
                    vk12.descriptorBindingUpdateUnusedWhilePending());
            required.put(
                    "descriptorBindingVariableDescriptorCount",
                    vk12.descriptorBindingVariableDescriptorCount());
            required.put("descriptorIndexing", vk12.descriptorIndexing());
            required.put("runtimeDescriptorArray", vk12.runtimeDescriptorArray());
            required.put(
                    "shaderSampledImageArrayNonUniformIndexing",
                    vk12.shaderSampledImageArrayNonUniformIndexing());
            required.put("dynamicRendering", vk13.dynamicRendering());
            required.put("shaderDemoteToHelperInvocation", vk13.shaderDemoteToHelperInvocation());
            required.put("synchronization2", vk13.synchronization2());

            List<String> missing =
                    required.entrySet().stream()
                            .filter(entry -> !entry.getValue())
                            .map(Map.Entry::getKey)
                            .toList();
            if (!missing.isEmpty()) {
                log.debug(
                        "Skipping {}, missing features {}",
                        deviceInfo.deviceProperties.deviceNameString(),
                        String.join(", ", missing));
                return false;
            }
            return true;
        }
    }

    /**
     * Check if the surface supports the format and color space we use for the swapchain.
     *
     * @param formats The formats the surface supports.
     * @return Whether we can use {@link #SWAPCHAIN_FORMAT} with {@link #SWAPCHAIN_COLOR_SPACE}.
     */
    private static boolean supportsSwapchainFormat(@NonNull VkSurfaceFormatKHR.Buffer formats) {
        for (int i = 0; i < formats.limit(); ++i) {
            if (formats.get(i).format() == SWAPCHAIN_FORMAT
                    && formats.get(i).colorSpace() == SWAPCHAIN_COLOR_SPACE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Change over to another rendering pipeline.
     *
     * @param config The configuration specifying the pipeline to switch to.
     * @see RenderConfig
     */
    public void swapPipeline(final int config) {
        checkError(vkDeviceWaitIdle(state.device.logical));
        renderConfig = config;
        pipeline = pipelineManager.getPipeline(config);
    }

    /**
     * Look up the queue family indices for the specified device, update tracking info for the
     * device.
     *
     * @param deviceInfo The device we are interested in.
     * @param surfaceHandle The surface handle, for checking support.
     */
    private void updateQueueFamilies(
            @NonNull VulkanState.PhysicalDeviceInfo deviceInfo, final long surfaceHandle) {
        int graphicsFamily = QueueFamilyIndices.MISSING;
        int graphicsQueueCount = 0;
        int presentFamily = QueueFamilyIndices.MISSING;
        int transferFamily = QueueFamilyIndices.MISSING;
        int transferFamilyIdeal = QueueFamilyIndices.MISSING;

        try {
            /* There are probably less than a dozen of these, we'll just look through all of them per device */
            for (int i = 0; i < deviceInfo.queueFamilyProperties.limit(); ++i) {
                var family = deviceInfo.queueFamilyProperties.get(i);

                if ((family.queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0
                        && (graphicsFamily == QueueFamilyIndices.MISSING
                                || graphicsQueueCount <= 1 && family.queueCount() > 1)) {
                    graphicsFamily = i;
                    graphicsQueueCount = family.queueCount();
                }
                if (transferFamilyIdeal == QueueFamilyIndices.MISSING
                        && (family.queueFlags() & VK_QUEUE_TRANSFER_BIT) != 0) {
                    /*
                     * I don't care if we overwrite transfer family, in fact it's preferable to be a different queue
                     * from the graphics, but once we find a dedicated transfer queue we can stop looking.
                     */
                    transferFamily = i;
                    if ((family.queueFlags() & (VK_QUEUE_GRAPHICS_BIT | VK_QUEUE_COMPUTE_BIT))
                            == 0) {
                        transferFamilyIdeal = i;
                    }
                }

                if (presentFamily == QueueFamilyIndices.MISSING
                        || presentFamily != graphicsFamily) {
                    checkError(
                            vkGetPhysicalDeviceSurfaceSupportKHR(
                                    deviceInfo.physicalDevice, i, surfaceHandle, intOutput));
                    if (intOutput.get(0) == VK_TRUE) {
                        presentFamily = i;
                        if (graphicsFamily != i
                                && (family.queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                            /* We really prefer a graphics family that has both graphics and present,
                             * so if we find a present family that also supports graphics we'll use that instead.
                             */
                            graphicsFamily = i;
                            graphicsQueueCount = family.queueCount();
                        }
                    }
                }
            }
        } finally {
            if (transferFamilyIdeal != QueueFamilyIndices.MISSING
                    && transferFamilyIdeal != transferFamily) {
                /*
                 * We found a family with no graphics or compute that's different from any ole transferFamily
                 */
                transferFamily = transferFamilyIdeal;
            }
            if (transferFamily == QueueFamilyIndices.MISSING
                    && graphicsFamily != QueueFamilyIndices.MISSING) {
                /*
                 * Queue families that expose VK_QUEUE_GRAPHICS_BIT automatically support transfer commands
                 * even if the flag is listed. So if we couldn't find any with VK_QUEUE_TRANSFER_BIT, might as well
                 * just use a queue family that does support transfer commands.
                 */
                transferFamily = graphicsFamily;
            }
            boolean roomForSeparateTransferQueue =
                    transferFamily != graphicsFamily || graphicsQueueCount > 1;
            deviceInfo.queueFamilyIndices =
                    new QueueFamilyIndices(
                            graphicsFamily,
                            presentFamily,
                            transferFamily,
                            roomForSeparateTransferQueue);
            // NOTE(ches) it's important that we use a buffer that doesn't need manual freeing
            deviceInfo.queueFamilyProperties = null;
        }
    }

    /**
     * Check the swap chain support provided for the surface by the provided device.
     *
     * @param deviceInfo The device info to update.
     */
    private void updateSwapChainSupport(
            @NonNull VulkanState.PhysicalDeviceInfo deviceInfo, long surfaceHandle) {
        deviceInfo.capabilities = VkSurfaceCapabilitiesKHR.create();

        checkError(
                vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
                        deviceInfo.physicalDevice, surfaceHandle, deviceInfo.capabilities));

        checkError(
                vkGetPhysicalDeviceSurfaceFormatsKHR(
                        deviceInfo.physicalDevice, surfaceHandle, intOutput, null));

        if (intOutput.get(0) > 0) {
            deviceInfo.formats = VkSurfaceFormatKHR.create(intOutput.get(0));
            checkError(
                    vkGetPhysicalDeviceSurfaceFormatsKHR(
                            deviceInfo.physicalDevice,
                            surfaceHandle,
                            intOutput,
                            deviceInfo.formats));
        }

        checkError(
                vkGetPhysicalDeviceSurfacePresentModesKHR(
                        deviceInfo.physicalDevice, surfaceHandle, intOutput, null));
        if (intOutput.get(0) > 0) {
            final int presentModeCount = intOutput.get(0);
            deviceInfo.presentModes = new int[presentModeCount];
            int[] arrayForSignatureReasons = new int[] {presentModeCount};
            checkError(
                    vkGetPhysicalDeviceSurfacePresentModesKHR(
                            deviceInfo.physicalDevice,
                            surfaceHandle,
                            arrayForSignatureReasons,
                            deviceInfo.presentModes));
        }
    }
}
