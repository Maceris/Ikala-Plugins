package com.ikalagaming.graphics.backend.vulkan;

import static org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface;
import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.BufferHolder;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.exceptions.RenderException;
import com.ikalagaming.graphics.frontend.GraphicsSettings;
import com.ikalagaming.graphics.frontend.Instance;
import com.ikalagaming.graphics.frontend.TextureLoader;
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
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class VulkanInstance implements Instance {

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

    private final IntBuffer intOutput = MemoryUtil.memAllocInt(1);
    private final LongBuffer longOutput = MemoryUtil.memAllocLong(1);
    private final PointerBuffer pointerOutput = MemoryUtil.memAllocPointer(1);

    private final VkDebugUtilsMessengerCallbackEXT debugLogger =
            VkDebugUtilsMessengerCallbackEXT.create(VulkanInstance::logDebugMessage);

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
     * Check for an error, and if there is one then log it and throw an exception.
     *
     * @param errorCode The result from a Vulkan function.
     * @throws RenderException If the error code is not 0.
     */
    private static void checkError(int errorCode) {
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

    /** Tracks the state and handles. */
    private final VulkanState state = new VulkanState();

    @Override
    public boolean initialize(@NonNull Window window) {
        createVulkanInstance(window);
        createSwapchain(window);

        initializeGui(window);
        return true;
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

    @Override
    public void initializeModel(@NonNull Model model) {
        // TODO(ches) initialize model
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

            checkError(
                    glfwCreateWindowSurface(
                            state.instance, window.getWindowHandle(), null, longOutput));
            state.surfaceHandle = longOutput.get(0);

            checkError(vkEnumeratePhysicalDevices(state.instance, intOutput, null));

            if (intOutput.get(0) <= 0) {
                log.error("Could not find number of physical devices");
                return;
            }
            PointerBuffer physicalDevices = PointerBuffer.allocateDirect(intOutput.get(0));
            checkError(vkEnumeratePhysicalDevices(state.instance, intOutput, physicalDevices));

            List<VkPhysicalDevice> devices = new ArrayList<>();
            for (int i = 0; i < physicalDevices.limit(); ++i) {
                devices.add(new VkPhysicalDevice(physicalDevices.get(i), state.instance));
            }

            state.device.physical = selectPhysicalDevice(devices);
            vkGetPhysicalDeviceProperties(state.device.physical, state.device.deviceProperties);
            vkGetPhysicalDeviceFeatures(state.device.physical, state.device.deviceFeatures);

            VkPhysicalDeviceVulkan13Features enabledVk13Features =
                    VkPhysicalDeviceVulkan13Features.create();
            enabledVk13Features.sType$Default().synchronization2(true).dynamicRendering(true);

            VkPhysicalDeviceVulkan12Features enabledVk12Features =
                    VkPhysicalDeviceVulkan12Features.create();
            enabledVk12Features
                    .sType$Default()
                    .descriptorIndexing(true)
                    .shaderSampledImageArrayNonUniformIndexing(true)
                    .descriptorBindingVariableDescriptorCount(true)
                    .runtimeDescriptorArray(true)
                    .bufferDeviceAddress(true)
                    .pNext(enabledVk13Features.address());

            VkPhysicalDeviceFeatures enabledVkFeatures = VkPhysicalDeviceFeatures.create();
            enabledVkFeatures.samplerAnisotropy(true);

            int queueCount = 1;
            if (state.device.queueFamilyIndices.graphics()
                    != state.device.queueFamilyIndices.present()) {
                queueCount = 2;
            }

            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkDeviceQueueCreateInfo.Buffer deviceQueueCreateInfos =
                        VkDeviceQueueCreateInfo.create(queueCount);

                for (int i = 0; i < queueCount; i++) {
                    final int index =
                            switch (i) {
                                case 0 -> state.device.queueFamilyIndices.graphics();
                                case 1 -> state.device.queueFamilyIndices.present();
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
                                state.device.physical, deviceCreateInfo, null, pointerOutput));

                state.device.logical =
                        new VkDevice(pointerOutput.get(0), state.device.physical, deviceCreateInfo);
            }

            vkGetDeviceQueue(
                    state.device.logical,
                    state.device.queueFamilyIndices.graphics(),
                    0,
                    pointerOutput);
            final long graphicsQueueIndex = pointerOutput.get(0);
            state.device.graphicsQueue = new VkQueue(graphicsQueueIndex, state.device.logical);
            if (queueCount == 1) {
                state.device.presentQueue = state.device.graphicsQueue;
            } else {
                vkGetDeviceQueue(
                        state.device.logical,
                        state.device.queueFamilyIndices.present(),
                        0,
                        pointerOutput);
                state.device.presentQueue = new VkQueue(pointerOutput.get(0), state.device.logical);
            }

            checkError(
                    glfwCreateWindowSurface(
                            state.instance, window.getWindowHandle(), null, longOutput));
            state.surfaceHandle = longOutput.get(0);
        }
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

    private void createSwapchain(@NonNull Window window) {
        // TODO(ches) actually create the swapchain

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSurfaceCapabilitiesKHR surfaceCapabilities = VkSurfaceCapabilitiesKHR.calloc(stack);
            checkError(
                    vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
                            state.device.physical, state.surfaceHandle, surfaceCapabilities));
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
                    .surface(state.surfaceHandle)
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
            state.swapchainHandle = longOutput.get(0);

            checkError(
                    vkGetSwapchainImagesKHR(
                            state.device.logical, state.swapchainHandle, intOutput, null));
            final int imageCount = intOutput.get(0);
            LongBuffer images = stack.callocLong(imageCount);
            checkError(
                    vkGetSwapchainImagesKHR(
                            state.device.logical, state.swapchainHandle, intOutput, images));

            state.swapchainImages = new long[imageCount];
            images.get(0, state.swapchainImages);

            // TODO(ches) set up depth attachment, image views
        }
    }

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

    /**
     * Check the swap chain support provided for the surface by the provided device.
     *
     * @param device The device.
     * @return Swap chain support information provided for the surface by the provided device.
     */
    private SwapChainSupport checkSwapChainSupport(@NonNull VkPhysicalDevice device) {
        var capabilities = VkSurfaceCapabilitiesKHR.malloc();
        VkSurfaceFormatKHR.Buffer formats = null;
        int[] presentModes = null;
        checkError(
                vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
                        device, state.surfaceHandle, capabilities));

        checkError(
                vkGetPhysicalDeviceSurfaceFormatsKHR(device, state.surfaceHandle, intOutput, null));
        if (intOutput.get(0) > 0) {
            formats = VkSurfaceFormatKHR.malloc(intOutput.get(0));
            checkError(
                    vkGetPhysicalDeviceSurfaceFormatsKHR(
                            device, state.surfaceHandle, intOutput, formats));
        }

        checkError(
                vkGetPhysicalDeviceSurfacePresentModesKHR(
                        device, state.surfaceHandle, intOutput, null));
        if (intOutput.get(0) > 0) {
            presentModes = new int[intOutput.get(0)];
            int[] presentModeCount = new int[] {intOutput.get(0)};
            checkError(
                    vkGetPhysicalDeviceSurfacePresentModesKHR(
                            device, state.surfaceHandle, presentModeCount, presentModes));
        }

        return new SwapChainSupport(capabilities, formats, presentModes);
    }

    @Override
    public void cleanup() {
        // TODO(ches) complete this
    }

    /**
     * Look up the queue family indices for the specified device.
     *
     * @param device The physical device.
     * @return The queue family indices, which will be non-null but may have missing values.
     */
    private QueueFamilyIndices findQueueFamilies(@NonNull VkPhysicalDevice device) {
        vkGetPhysicalDeviceQueueFamilyProperties(device, intOutput, null);
        VkQueueFamilyProperties.Buffer queueProperties =
                VkQueueFamilyProperties.malloc(intOutput.get(0));
        vkGetPhysicalDeviceQueueFamilyProperties(device, intOutput, queueProperties);

        int graphicsFamily = QueueFamilyIndices.MISSING;
        int presentFamily = QueueFamilyIndices.MISSING;

        for (int i = 0; i < queueProperties.limit(); ++i) {
            var family = queueProperties.get(i);

            if ((family.queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                graphicsFamily = i;
            }

            checkError(
                    vkGetPhysicalDeviceSurfaceSupportKHR(
                            device, i, state.surfaceHandle, intOutput));

            if (intOutput.get(0) == VK_TRUE) {
                presentFamily = i;
            }

            if (graphicsFamily != QueueFamilyIndices.MISSING
                    && presentFamily != QueueFamilyIndices.MISSING) {
                break;
            }
        }

        queueProperties.free();
        return new QueueFamilyIndices(graphicsFamily, presentFamily);
    }

    @Override
    public void processResources() {
        // TODO(ches) complete this
    }

    @Override
    public TextureLoader getTextureLoader() {
        return null;
        // TODO(ches) complete this
    }

    @Override
    public void render(@NonNull Scene scene) {
        // TODO(ches) complete this

        // TODO(ches) vkQueuePresentKHR()... somewhere
    }

    @Override
    public void resize(int width, int height) {
        // TODO(ches) complete this
    }

    /**
     * Give a device a score based on how suitable it is, for use in device selection.
     *
     * @param device The device we want to score.
     * @param score A 1-sized array of integers, used as an out parameter for the score.
     * @return Queue family indices for the device.
     */
    private QueueFamilyIndices scoreDevice(@NonNull VkPhysicalDevice device, int[] score) {
        assert score != null && score.length >= 1;

        vkGetPhysicalDeviceFeatures(device, state.device.deviceFeatures);

        if (!state.device.deviceFeatures.geometryShader()) {
            score[0] = 0;
            return new QueueFamilyIndices(QueueFamilyIndices.MISSING, QueueFamilyIndices.MISSING);
        }

        QueueFamilyIndices queueFamilies = findQueueFamilies(device);

        if (!queueFamilies.hasAllValues()) {
            score[0] = 0;
            return queueFamilies;
        }

        if (!supportsRequiredExtensions(device)) {
            score[0] = 0;
            return queueFamilies;
        }

        var swapChainSupport = checkSwapChainSupport(device);
        if (swapChainSupport.isMissingSupport()) {
            swapChainSupport.free();
            score[0] = 0;
            return queueFamilies;
        }

        swapChainSupport.free();

        vkGetPhysicalDeviceProperties(device, state.device.deviceProperties);
        if (state.device.deviceProperties.deviceType() == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) {
            score[0] += 1_000_000;
        }

        score[0] += state.device.deviceProperties.limits().maxImageDimension2D();

        return queueFamilies;
    }

    /**
     * Select a physical device to use.
     *
     * @param vkPhysicalDevices The list of devices to choose from.
     * @return The selected physical device.
     * @throws RenderException If no device could possibly work.
     */
    private VkPhysicalDevice selectPhysicalDevice(
            @NonNull List<VkPhysicalDevice> vkPhysicalDevices) {
        VkPhysicalDevice bestChoice = null;
        int highestScore = Integer.MIN_VALUE;

        int[] score = {0};
        QueueFamilyIndices indices = null;
        for (VkPhysicalDevice device : vkPhysicalDevices) {
            score[0] = 0;
            indices = scoreDevice(device, score);
            if (score[0] > highestScore) {
                highestScore = score[0];
                bestChoice = device;
            }
        }

        if (null == bestChoice) {
            final var message = "No valid physical device found";
            log.error(message);
            throw new RenderException(message);
        }

        state.device.queueFamilyIndices = indices;
        return bestChoice;
    }

    /**
     * Check if the specified device supports the required device extensions.
     *
     * @param device The device
     * @return Whether we found the support that we need.
     */
    private boolean supportsRequiredExtensions(@NonNull VkPhysicalDevice device) {
        vkEnumerateDeviceExtensionProperties(device, (String) null, intOutput, null);
        var properties = VkExtensionProperties.malloc(intOutput.get(0));
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
    public int getPipelineConfig() {
        // TODO(ches) return config
        return 0;
    }

    @Override
    public void setQuality(GraphicsSettings.@NonNull Quality quality) {
        // TODO(ches) set up or clean up as needed
    }

    @Override
    public void swapPipeline(final int config) {
        // TODO(ches) complete this
        // TODO(ches) Can we eliminate pipelines as a concept?
    }
}
