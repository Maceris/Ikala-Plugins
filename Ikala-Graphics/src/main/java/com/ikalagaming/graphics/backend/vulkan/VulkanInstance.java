package com.ikalagaming.graphics.backend.vulkan;

import static org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface;
import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceProperties;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.BufferHolder;
import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.base.ShaderMap;
import com.ikalagaming.graphics.exceptions.RenderException;
import com.ikalagaming.graphics.exceptions.ShaderException;
import com.ikalagaming.graphics.frontend.*;
import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.data.IkIO;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.util.SafeResourceLoader;

import imgui.ImGui;
import imgui.ImGuiIO;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.util.vma.VmaVulkanFunctions;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class VulkanInstance implements Instance {

    /**
     * The maximum number of bindless textures we could support. The actual number that is supported
     * may be lower due to runtime physical GPU limits, but it will never be higher.
     */
    public static final int MAX_BINDLESS_TEXTURE_COUNT = 10_000;

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

    /** Whether to enable validation layers and logging. */
    private static final boolean ENABLE_VALIDATION = true;

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

    private final IntBuffer intOutput = MemoryUtil.memAllocInt(1);

    private final LongBuffer longOutput = MemoryUtil.memAllocLong(1);

    private final PointerBuffer pointerOutput = MemoryUtil.memAllocPointer(1);

    private final VkDebugUtilsMessengerCallbackEXT debugLogger =
            VkDebugUtilsMessengerCallbackEXT.create(VulkanInstance::logDebugMessage);

    /** Tracks the state and handles. */
    private final VulkanState state = new VulkanState();

    private int renderConfig;
    private Pipeline pipeline;
    private TextureLoader textureLoader;
    private ShaderMap shaderMap;
    private PipelineManagerVulkan pipelineManager;

    /**
     * Check that the specified layers are available, and throw an exception if any are not.
     *
     * @param availableLayerNames The layer names that are available.
     * @param requiredLayerNames The layers that we require.
     * @throws RenderException If required layers are missing.
     */
    private void checkLayers(
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
            final var message = SafeResourceLoader.format("Vulkan layers missing: {}", layerNames);
            log.error(message);

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

            throw new RenderException(message);
        }
    }

    @Override
    public void cleanup() {
        // TODO(ches) complete this
    }

    private void createShaderData() {
        for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
            state.shaderDataBuffers[i] = new PerFrameData();

            // TODO(ches) figure out the shader data size
            final long figureOutSize = 1;

            state.shaderDataBuffers[i].animation = createSharedBuffer(figureOutSize);
            state.shaderDataBuffers[i].filter = createSharedBuffer(figureOutSize);
            state.shaderDataBuffers[i].gui = createSharedBuffer(figureOutSize);
            state.shaderDataBuffers[i].guiLegacy = createSharedBuffer(figureOutSize);
            state.shaderDataBuffers[i].light = createSharedBuffer(figureOutSize);
            state.shaderDataBuffers[i].scene = createSharedBuffer(figureOutSize);
            state.shaderDataBuffers[i].shadow = createSharedBuffer(figureOutSize);
            state.shaderDataBuffers[i].skybox = createSharedBuffer(figureOutSize);
        }

        // TODO(ches) setup command pools, and coommand buffers * MAX_FRAMES_IN_FLIGHT
    }

    /**
     * Create a shared buffer with the given size.
     *
     * @param bufferSize The size of the buffer in bytes.
     * @return The new buffer object.
     */
    private SharedBuffer createSharedBuffer(long bufferSize) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferCreateInfo =
                    VkBufferCreateInfo.calloc(stack)
                            .sType$Default()
                            .size(bufferSize)
                            .usage(VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT);
            VmaAllocationCreateInfo bufferAllocationCreateInfo =
                    VmaAllocationCreateInfo.calloc(stack)
                            .flags(
                                    VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT
                                            | VMA_ALLOCATION_CREATE_HOST_ACCESS_ALLOW_TRANSFER_INSTEAD_BIT
                                            | VMA_ALLOCATION_CREATE_MAPPED_BIT)
                            .usage(VMA_MEMORY_USAGE_AUTO);
            VkBufferDeviceAddressInfo bufferDeviceAddressInfo =
                    VkBufferDeviceAddressInfo.calloc(stack).sType$Default();

            SharedBuffer result = new SharedBuffer();

            checkError(
                    vmaCreateBuffer(
                            state.vmaAllocator,
                            bufferCreateInfo,
                            bufferAllocationCreateInfo,
                            longOutput,
                            pointerOutput,
                            result.allocationInfo));
            result.buffer = longOutput.get(0);
            result.allocation = pointerOutput.get(0);

            bufferDeviceAddressInfo.buffer(result.buffer);
            result.deviceAddress =
                    vkGetBufferDeviceAddress(state.device.logical, bufferDeviceAddressInfo);
            return result;
        }
    }

    /**
     * Set up a surface for a window, and selects the physical device.
     *
     * @param window The window.
     */
    private void createSurface(@NonNull Window window) {
        VulkanState.WindowInfo windowInfo = new VulkanState.WindowInfo();
        state.windows.add(windowInfo);

        checkError(
                glfwCreateWindowSurface(
                        state.instance, window.getWindowHandle(), null, longOutput));
        windowInfo.surfaceHandle = longOutput.get(0);

        state.device.physical = selectPhysicalDevice(windowInfo.surfaceHandle);

        int queueCount = 1;
        if (state.device.physical.queueFamilyIndices.graphics()
                != state.device.physical.queueFamilyIndices.present()) {
            queueCount = 2;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceVulkan13Features enabledVk13Features =
                    VkPhysicalDeviceVulkan13Features.calloc(stack);
            enabledVk13Features.sType$Default().synchronization2(true).dynamicRendering(true);

            VkPhysicalDeviceVulkan12Features enabledVk12Features =
                    VkPhysicalDeviceVulkan12Features.calloc(stack);
            enabledVk12Features
                    .sType$Default()
                    .bufferDeviceAddress(true)
                    .descriptorBindingPartiallyBound(true)
                    .descriptorBindingSampledImageUpdateAfterBind(true)
                    .descriptorBindingVariableDescriptorCount(true)
                    .descriptorIndexing(true)
                    .runtimeDescriptorArray(true)
                    .shaderSampledImageArrayNonUniformIndexing(true)
                    .pNext(enabledVk13Features.address());

            VkPhysicalDeviceFeatures enabledVkFeatures = VkPhysicalDeviceFeatures.calloc(stack);
            enabledVkFeatures.samplerAnisotropy(true);

            VkDeviceQueueCreateInfo.Buffer deviceQueueCreateInfos =
                    VkDeviceQueueCreateInfo.calloc(queueCount, stack);

            for (int i = 0; i < queueCount; i++) {
                final int index =
                        switch (i) {
                            case 0 -> state.device.physical.queueFamilyIndices.graphics();
                            case 1 -> state.device.physical.queueFamilyIndices.present();
                            default -> 0;
                        };
                deviceQueueCreateInfos
                        .get(i)
                        .sType$Default()
                        .pNext(NULL)
                        .flags(0)
                        .queueFamilyIndex(index)
                        .pQueuePriorities(stack.floats(1.0f));
            }

            PointerBuffer deviceExtensionNames =
                    PointerBuffer.allocateDirect(REQUIRED_DEVICE_EXTENSIONS.length);
            Arrays.stream(REQUIRED_DEVICE_EXTENSIONS).forEach(deviceExtensionNames::put);
            deviceExtensionNames.flip();

            VkDeviceCreateInfo deviceCreateInfo = VkDeviceCreateInfo.create();
            deviceCreateInfo
                    .sType$Default()
                    .pEnabledFeatures(enabledVkFeatures)
                    .pNext(enabledVk12Features)
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
        final long graphicsQueueIndex = pointerOutput.get(0);
        state.device.graphicsQueue = new VkQueue(graphicsQueueIndex, state.device.logical);
        if (queueCount == 1) {
            state.device.presentQueue = state.device.graphicsQueue;
        } else {
            vkGetDeviceQueue(
                    state.device.logical,
                    state.device.physical.queueFamilyIndices.present(),
                    0,
                    pointerOutput);
            state.device.presentQueue = new VkQueue(pointerOutput.get(0), state.device.logical);
        }

        checkError(
                glfwCreateWindowSurface(
                        state.instance, window.getWindowHandle(), null, longOutput));
        windowInfo.surfaceHandle = longOutput.get(0);
    }

    private void createSwapchain(@NonNull Window window) {
        // TODO(ches) this should be associated with the window, rather than just having one
        VulkanState.WindowInfo windowInfo = state.windows.getFirst();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSurfaceCapabilitiesKHR surfaceCapabilities = VkSurfaceCapabilitiesKHR.calloc(stack);
            checkError(
                    vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
                            state.device.physical.physicalDevice,
                            windowInfo.surfaceHandle,
                            surfaceCapabilities));
            VkExtent2D swapchainExtent = VkExtent2D.calloc(stack);

            if (surfaceCapabilities.currentExtent().width() == 0xFFFF_FFFF) {
                swapchainExtent.set(window.getWidth(), window.getHeight());
            } else {
                swapchainExtent.set(surfaceCapabilities.currentExtent());
            }

            VkSwapchainCreateInfoKHR swapchainCreateInfo = VkSwapchainCreateInfoKHR.calloc(stack);
            /*
             * NOTE(ches) The swapchain is BGRA as that's guaranteed to be everywhere, though our app generally operates in RGBA. We'll just swizzle at the
             * last possible second. VK_PRESENT_MODE_FIFO_KHR is a v-synced mode and the only mode guaranteed to be available everywhere.
             */
            swapchainCreateInfo
                    .sType$Default()
                    .surface(windowInfo.surfaceHandle)
                    .minImageCount(surfaceCapabilities.minImageCount())
                    .imageFormat(VK_FORMAT_B8G8R8A8_SRGB)
                    .imageColorSpace(VK_COLOR_SPACE_SRGB_NONLINEAR_KHR)
                    .imageExtent(swapchainExtent)
                    .imageArrayLayers(1)
                    .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                    .preTransform(VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR)
                    .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                    .presentMode(VK_PRESENT_MODE_FIFO_KHR);

            checkError(
                    vkCreateSwapchainKHR(
                            state.device.logical, swapchainCreateInfo, null, longOutput));
            windowInfo.swapchainHandle = longOutput.get(0);

            checkError(
                    vkGetSwapchainImagesKHR(
                            state.device.logical, windowInfo.swapchainHandle, intOutput, null));
            final int imageCount = intOutput.get(0);
            LongBuffer images = stack.callocLong(imageCount);
            checkError(
                    vkGetSwapchainImagesKHR(
                            state.device.logical, windowInfo.swapchainHandle, intOutput, images));

            windowInfo.swapchainImages = new long[imageCount];
            images.get(0, windowInfo.swapchainImages);

            windowInfo.renderCompleteSemaphores = new long[imageCount];
            VkSemaphoreCreateInfo semaphoreCreateInfo =
                    VkSemaphoreCreateInfo.calloc(stack).sType$Default();
            for (int i = 0; i < imageCount; i++) {
                checkError(
                        vkCreateSemaphore(
                                state.device.logical, semaphoreCreateInfo, null, longOutput));
                windowInfo.renderCompleteSemaphores[i] = longOutput.get(0);
            }

            VkExtent3D depthExtent =
                    VkExtent3D.calloc(stack).set(window.getWidth(), window.getHeight(), 1);

            VkImageCreateInfo depthImageCreateInfo =
                    VkImageCreateInfo.calloc(stack)
                            .sType$Default()
                            .imageType(VK_IMAGE_TYPE_2D)
                            .format(state.device.physical.depthFormat)
                            .extent(depthExtent)
                            .mipLevels(1)
                            .arrayLayers(1)
                            .samples(VK_SAMPLE_COUNT_1_BIT)
                            .tiling(VK_IMAGE_TILING_OPTIMAL)
                            .usage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT)
                            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);

            VmaAllocationCreateInfo depthImageAlloc =
                    VmaAllocationCreateInfo.calloc(stack)
                            .flags(VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT)
                            .usage(VMA_MEMORY_USAGE_AUTO);

            checkError(
                    vmaCreateImage(
                            state.vmaAllocator,
                            depthImageCreateInfo,
                            depthImageAlloc,
                            longOutput,
                            pointerOutput,
                            null));
            final long depthImage = longOutput.get(0);
            final long depthImageAllocation = pointerOutput.get(0);

            VkImageSubresourceRange depthViewSubresourceRange =
                    VkImageSubresourceRange.calloc(stack)
                            .aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT)
                            .levelCount(1)
                            .layerCount(1);

            VkImageViewCreateInfo depthViewCreateInfo =
                    VkImageViewCreateInfo.calloc(stack)
                            .sType$Default()
                            .image(depthImage)
                            .viewType(VK_IMAGE_VIEW_TYPE_2D)
                            .format(state.device.physical.depthFormat)
                            .subresourceRange(depthViewSubresourceRange);

            checkError(
                    vkCreateImageView(state.device.logical, depthViewCreateInfo, null, longOutput));
            final long depthView = longOutput.get(0);

            windowInfo.depthImage =
                    new TextureInfo()
                            .texture(depthImage)
                            .textureAllocation(depthImageAllocation)
                            .view(depthView);
        }
    }

    private void createSynchronizationInfo() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreCreateInfo semaphoreCreateInfo =
                    VkSemaphoreCreateInfo.calloc(stack).sType$Default();
            VkFenceCreateInfo fenceCreateInfo =
                    VkFenceCreateInfo.calloc(stack)
                            .sType$Default()
                            .flags(VK_FENCE_CREATE_SIGNALED_BIT);

            assert state.fences != null
                    && state.fences.length == GraphicsManager.MAX_FRAMES_IN_FLIGHT;
            assert state.imageAcquiredSemaphores != null
                    && state.imageAcquiredSemaphores.length == GraphicsManager.MAX_FRAMES_IN_FLIGHT;

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
            requiredExtensionNames.flip();

            PointerBuffer requiredLayerNames = null;

            if (ENABLE_VALIDATION) {
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

                checkLayers(availableLayers, requiredLayerNames);
            }

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
                            .flags(0)
                            .pApplicationInfo(applicationInfo)
                            .ppEnabledLayerNames(requiredLayerNames)
                            .ppEnabledExtensionNames(requiredExtensionNames);

            VkDebugUtilsMessengerCreateInfoEXT debugCreateInfo;

            if (ENABLE_VALIDATION) {
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
                                    vk12Properties.maxDescriptorSetUpdateAfterBindSampledImages());
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
                        // Can't happen (tm) unless the spec changes, log error and barrel forwards
                        // until
                        // something breaks
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

    @Override
    public int getPipelineConfig() {
        return renderConfig;
    }

    @Override
    public TextureLoader getTextureLoader() {
        return textureLoader;
    }

    @Override
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

        createSwapchain(window);
        createShaderData();

        textureLoader = new TextureLoaderVulkan();
        shaderMap = new ShaderMap();
        initializeShaders();
        initializeGui(window);
        pipelineManager = new PipelineManagerVulkan(window, shaderMap);
        renderConfig = RenderConfig.builder().withGui().build();
        pipeline = pipelineManager.getPipeline(renderConfig);
        pipeline.initialize(window, shaderMap);

        createSynchronizationInfo();

        initializeGui(window);
        return true;
    }

    /** Set up the animation shader and uniforms. */
    private void initializeAnimationShader() {
        List<Shader.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new Shader.ShaderModuleData(
                        "shaders/vulkan/anim.comp", Shader.Type.COMPUTE, Shader.Location.BUNDLED));
        var shaderProgram = new ShaderVulkan(shaderModuleDataList, state);

        shaderMap.addShader(RenderStage.Type.ANIMATION, shaderProgram);
    }

    /**
     * Set up the default filter shader.
     *
     * @throws ShaderException If the default filter could not be found or loaded properly.
     */
    private void initializeFilterShader() {
        List<Shader.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new Shader.ShaderModuleData(
                        "shaders/vulkan/filters/default.vert",
                        Shader.Type.VERTEX,
                        Shader.Location.BUNDLED));
        shaderModuleDataList.add(
                new Shader.ShaderModuleData(
                        "shaders/vulkan/filters/default.frag",
                        Shader.Type.FRAGMENT,
                        Shader.Location.BUNDLED));
        var shaderProgram = new ShaderVulkan(shaderModuleDataList, state);

        shaderMap.addShader(RenderStage.Type.FILTER, shaderProgram);
    }

    /**
     * Create an IkGui context and configure it.
     *
     * @param window The window to pull display info from.
     */
    private void initializeGui(@NonNull Window window) {
        // TODO(ches) clear out ImGui
        ImGui.createContext();

        ImGuiIO imGuiIO = ImGui.getIO();
        imGuiIO.setIniFilename(null);
        imGuiIO.setDisplaySize(window.getWidth(), window.getHeight());

        IkGui.createContext();

        IkIO ikIO = IkGui.getIO();
        ikIO.iniFilename = null;
        ikIO.displaySize.set(window.getWidth(), window.getHeight());
    }

    /** Set up the GUI shader and uniforms. */
    private void initializeGuiShader() {
        // TODO(ches) remove the imgui part of this
        {
            List<Shader.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
            shaderModuleDataList.add(
                    new Shader.ShaderModuleData(
                            "shaders/vulkan/imgui.vert",
                            Shader.Type.VERTEX,
                            Shader.Location.BUNDLED));
            shaderModuleDataList.add(
                    new Shader.ShaderModuleData(
                            "shaders/vulkan/imgui.frag",
                            Shader.Type.FRAGMENT,
                            Shader.Location.BUNDLED));
            var shaderProgram = new ShaderVulkan(shaderModuleDataList, state);

            shaderMap.addShader(RenderStage.Type.GUI_LEGACY, shaderProgram);
        }
        {
            List<Shader.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
            shaderModuleDataList.add(
                    new Shader.ShaderModuleData(
                            "shaders/vulkan/ikgui.vert",
                            Shader.Type.VERTEX,
                            Shader.Location.BUNDLED));
            shaderModuleDataList.add(
                    new Shader.ShaderModuleData(
                            "shaders/vulkan/ikgui.frag",
                            Shader.Type.FRAGMENT,
                            Shader.Location.BUNDLED));
            var shaderProgram = new ShaderVulkan(shaderModuleDataList, state);

            shaderMap.addShader(RenderStage.Type.GUI, shaderProgram);
        }
    }

    /** Set up the light shader and uniforms. */
    private void initializeLightShader() {
        List<Shader.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new Shader.ShaderModuleData(
                        "shaders/vulkan/lights.vert", Shader.Type.VERTEX, Shader.Location.BUNDLED));
        shaderModuleDataList.add(
                new Shader.ShaderModuleData(
                        "shaders/vulkan/lights.frag",
                        Shader.Type.FRAGMENT,
                        Shader.Location.BUNDLED));
        var shaderProgram = new ShaderVulkan(shaderModuleDataList, state);

        shaderMap.addShader(RenderStage.Type.LIGHT, shaderProgram);
    }

    @Override
    public void initializeModel(@NonNull Model model) {
        // TODO(ches) initialize model
    }

    /** Set up the scene shader and uniforms. */
    private void initializeSceneShader() {
        List<Shader.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new Shader.ShaderModuleData(
                        "shaders/vulkan/scene.vert", Shader.Type.VERTEX, Shader.Location.BUNDLED));
        shaderModuleDataList.add(
                new Shader.ShaderModuleData(
                        "shaders/vulkan/scene.frag",
                        Shader.Type.FRAGMENT,
                        Shader.Location.BUNDLED));
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
        initializeFilterShader();
        initializeGuiShader();
    }

    /** Set up the shadow shader and uniforms. */
    private void initializeShadowShader() {
        List<Shader.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new Shader.ShaderModuleData(
                        "shaders/vulkan/shadow.vert", Shader.Type.VERTEX, Shader.Location.BUNDLED));
        var shaderProgram = new ShaderVulkan(shaderModuleDataList, state);

        shaderMap.addShader(RenderStage.Type.SHADOW, shaderProgram);
    }

    /** Set up the skybox shader and uniforms. */
    private void initializeSkyboxShader() {
        List<Shader.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new Shader.ShaderModuleData(
                        "shaders/vulkan/skybox.vert", Shader.Type.VERTEX, Shader.Location.BUNDLED));
        shaderModuleDataList.add(
                new Shader.ShaderModuleData(
                        "shaders/vulkan/skybox.frag",
                        Shader.Type.FRAGMENT,
                        Shader.Location.BUNDLED));
        var shaderProgram = new ShaderVulkan(shaderModuleDataList, state);

        shaderMap.addShader(RenderStage.Type.SKYBOX, shaderProgram);
    }

    @Override
    public void processResources() {
        // TODO(ches) complete this
    }

    @Override
    public void render(@NonNull Scene scene) {
        if (false) {
            // TODO(ches) actually run this once we have fences being signaled from shaders
            longOutput.put(0, state.fences[state.frameIndex]);
            checkError(vkWaitForFences(state.device.logical, longOutput, true, Integer.MAX_VALUE));
            longOutput.put(0, state.fences[state.frameIndex]);
            checkError(vkResetFences(state.device.logical, longOutput));
        }
        // TODO(ches) acquire next image
        // TODO(ches) recreate swapchain if necessary
        // TODO(ches) update shader data
        // TODO(ches) record command buffer
        // TODO(ches) submit command buffer
        // TODO(ches) present image

    }

    @Override
    public void resize(int width, int height) {
        // TODO(ches) complete this
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

        if (!deviceInfo.queueFamilyIndices.hasAllValues()) {
            return 0;
        }

        if (!deviceInfo.deviceFeatures.geometryShader()) {
            return 0;
        }

        if (!supportsRequiredExtensions(deviceInfo.physicalDevice)) {
            return 0;
        }

        updateSwapChainSupport(deviceInfo, surfaceHandle);
        if (deviceInfo.formats == null
                || deviceInfo.presentModes == null
                || deviceInfo.presentModes.length == 0) {
            deviceInfo.capabilities = null;
            deviceInfo.formats = null;
            deviceInfo.presentModes = null;
            return 0;
        }
        int score = 0;

        if (deviceInfo.deviceProperties.deviceType() == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) {
            score += 1_000_000;
        }

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
            if (score > highestScore) {
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

    @Override
    public void setQuality(
            @NonNull GraphicsSettings.Quality oldQuality,
            @NonNull GraphicsSettings.Quality newQuality) {
        // TODO(ches) set up or clean up as needed
    }

    /**
     * Check if the specified device supports the required device extensions.
     *
     * @param device The device
     * @return Whether we found the support that we need.
     */
    private boolean supportsRequiredExtensions(@NonNull VkPhysicalDevice device) {
        vkEnumerateDeviceExtensionProperties(device, (String) null, intOutput, null);
        var properties = VkExtensionProperties.calloc(intOutput.get(0));
        vkEnumerateDeviceExtensionProperties(device, (String) null, intOutput, properties);

        List<String> missingExtensions = new ArrayList<>(REQUIRED_DEVICE_EXTENSION_NAMES);

        for (int i = 0; i < properties.limit(); ++i) {
            if (missingExtensions.isEmpty()) {
                break;
            }
            var extension = properties.get(i).extensionNameString();
            missingExtensions.remove(extension);
        }

        properties.free();
        return missingExtensions.isEmpty();
    }

    @Override
    public void swapPipeline(final int config) {
        // TODO(ches) complete this
        // TODO(ches) Can we eliminate pipelines as a concept?
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
        int presentFamily = QueueFamilyIndices.MISSING;

        try {
            for (int i = 0; i < deviceInfo.queueFamilyProperties.limit(); ++i) {
                var family = deviceInfo.queueFamilyProperties.get(i);

                if ((family.queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                    graphicsFamily = i;
                }

                checkError(
                        vkGetPhysicalDeviceSurfaceSupportKHR(
                                deviceInfo.physicalDevice, i, surfaceHandle, intOutput));

                if (intOutput.get(0) == VK_TRUE) {
                    presentFamily = i;
                }

                if (graphicsFamily != QueueFamilyIndices.MISSING
                        && presentFamily != QueueFamilyIndices.MISSING) {
                    break;
                }
            }
        } finally {
            deviceInfo.queueFamilyIndices = new QueueFamilyIndices(graphicsFamily, presentFamily);
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
