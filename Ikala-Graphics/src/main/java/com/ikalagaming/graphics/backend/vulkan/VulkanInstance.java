package com.ikalagaming.graphics.backend.vulkan;

import static org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface;
import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memAllocPointer;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.VK10.*;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.exceptions.RenderException;
import com.ikalagaming.graphics.frontend.BufferUtil;
import com.ikalagaming.graphics.frontend.Instance;
import com.ikalagaming.graphics.frontend.TextureLoader;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.util.SafeResourceLoader;

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
import java.util.List;

@Slf4j
public class VulkanInstance implements Instance {

    private static final List<String> REQUIRED_INSTANCE_EXTENSION_NAMES = List.of("VK_KHR_surface");
    private static final ByteBuffer[] REQUIRED_INSTANCE_EXTENSIONS =
            REQUIRED_INSTANCE_EXTENSION_NAMES.stream()
                    .map(MemoryUtil::memASCII)
                    .toArray(ByteBuffer[]::new);

    private static final List<String> REQUIRED_DEVICE_EXTENSIONS = List.of("VK_KHR_swapchain");

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
            var message =
                    SafeResourceLoader.getStringFormatted(
                            "VULKAN_ERROR",
                            GraphicsPlugin.getResourceBundle(),
                            String.format("0x%X", errorCode));
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
        return true;
    }

    @Override
    public void initialize(@NonNull Model model) {
        // TODO(ches) initialize model
    }

    /**
     * Set up the vulkan instance.
     *
     * @param window The window we are setting up to render with.
     * @throws RenderException If an unrecoverable issue occurs setting up vulkan.
     */
    private void createVulkanInstance(@NonNull Window window) {
        try (MemoryStack stack = stackPush()) {

            PointerBuffer requiredExtensionNames = memAllocPointer(64);

            populateRequiredExtensions(requiredExtensionNames);

            PointerBuffer requiredLayerNames = null;

            if (ENABLE_VALIDATION) {
                requiredLayerNames = stack.mallocPointer(VALIDATION_LAYERS.length);
                for (String validationLayer : VALIDATION_LAYERS) {
                    requiredLayerNames.put(stack.ASCII(validationLayer));
                }

                try (MemoryStack extraFrame = stackPush()) {
                    checkError(vkEnumerateInstanceLayerProperties(intOutput, null));
                    VkLayerProperties.Buffer availableLayers =
                            VkLayerProperties.malloc(intOutput.get(0), extraFrame);
                    checkError(vkEnumerateInstanceLayerProperties(intOutput, availableLayers));

                    checkLayers(availableLayers, requiredLayerNames);
                }
                requiredLayerNames.flip();
            }

            ByteBuffer appName = stack.UTF8(window.getTitle());
            ByteBuffer engineName = stack.UTF8("Ikala Engine");

            var applicationInfo =
                    VkApplicationInfo.malloc(stack)
                            .sType$Default()
                            .pNext(NULL)
                            .pApplicationName(appName)
                            .applicationVersion(1)
                            .pEngineName(engineName)
                            .engineVersion(1)
                            .apiVersion(VK_MAKE_API_VERSION(0, 1, 2, 0));

            requiredExtensionNames.flip();
            var instanceInfo =
                    VkInstanceCreateInfo.malloc(stack)
                            .sType$Default()
                            .pNext(NULL)
                            .flags(0)
                            .pApplicationInfo(applicationInfo)
                            .ppEnabledLayerNames(requiredLayerNames)
                            .ppEnabledExtensionNames(requiredExtensionNames);

            VkDebugUtilsMessengerCreateInfoEXT debugCreateInfo;

            if (ENABLE_VALIDATION) {
                debugCreateInfo =
                        VkDebugUtilsMessengerCreateInfoEXT.malloc(stack)
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
            requiredExtensionNames.free();
            if (error == VK_ERROR_INCOMPATIBLE_DRIVER) {
                var message =
                        SafeResourceLoader.getString(
                                "VULKAN_INCOMPATIBLE_DRIVER", GraphicsPlugin.getResourceBundle());
                log.error(message);
                throw new RenderException(message);
            }
            if (error == VK_ERROR_EXTENSION_NOT_PRESENT) {
                var message =
                        SafeResourceLoader.getString(
                                "VULKAN_EXTENSION_NOT_PRESENT", GraphicsPlugin.getResourceBundle());
                log.error(message);
                throw new RenderException(message);
            }
            if (error != 0) {
                var message =
                        SafeResourceLoader.getStringFormatted(
                                "VULKAN_GENERIC_CREATION_FAILURE",
                                GraphicsPlugin.getResourceBundle(),
                                String.valueOf(error));
                log.error(message);
                throw new RenderException(message);
            }

            state.instance = new VkInstance(pointerOutput.get(0), instanceInfo);

            checkError(
                    glfwCreateWindowSurface(
                            state.instance, window.getWindowHandle(), null, longOutput));
            state.surfaceHandle = longOutput.get(0);

            checkError(vkEnumeratePhysicalDevices(state.instance, intOutput, null));

            if (intOutput.get(0) > 0) {
                PointerBuffer physicalDevices = stack.mallocPointer(intOutput.get(0));
                checkError(vkEnumeratePhysicalDevices(state.instance, intOutput, physicalDevices));

                List<VkPhysicalDevice> devices = new ArrayList<>();
                for (int i = 0; i < physicalDevices.limit(); ++i) {
                    devices.add(new VkPhysicalDevice(physicalDevices.get(i), state.instance));
                }

                state.device.physical = selectPhysicalDevice(devices);
                vkGetPhysicalDeviceProperties(state.device.physical, state.device.deviceProperties);
                vkGetPhysicalDeviceFeatures(state.device.physical, state.device.deviceFeatures);
            }
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
            var message =
                    SafeResourceLoader.getString(
                            "GLFW_EXTENSIONS_NULL", GraphicsPlugin.getResourceBundle());
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
        try (MemoryStack stack = stackPush()) {}
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

        requiredLayerNames.flip();
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
            var layerNames = String.join(", ", missingLayers);
            var message =
                    SafeResourceLoader.getStringFormatted(
                            "VULKAN_LAYERS_MISSING",
                            GraphicsPlugin.getResourceBundle(),
                            layerNames);
            log.error(message);

            List<String> layers = new ArrayList<>();
            for (int j = 0; j < availableLayerNames.capacity(); ++j) {
                var layer = availableLayerNames.get(j);
                layers.add(
                        String.format(
                                "%s (%s)", layer.layerNameString(), layer.descriptionString()));
            }
            log.info(
                    SafeResourceLoader.getStringFormatted(
                            "VULKAN_LAYERS_LIST",
                            GraphicsPlugin.getResourceBundle(),
                            String.join(", ", layers)));

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
    public BufferUtil getBufferUtil() {
        // TODO(ches) buffer utility
        return null;
    }

    @Override
    public void cleanup() {}

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
    public void processResources() {}

    @Override
    public TextureLoader getTextureLoader() {
        return null;
    }

    @Override
    public void render(@NonNull Scene scene) {}

    @Override
    public void resize(int width, int height) {}

    /**
     * Give a device a score based on how suitable it is, for use in device selection.
     *
     * @param device The device we want to score.
     * @return The score for the device.
     */
    private int scoreDevice(@NonNull VkPhysicalDevice device) {
        int score = 0;

        vkGetPhysicalDeviceFeatures(device, state.device.deviceFeatures);

        if (!state.device.deviceFeatures.geometryShader()) {
            return 0;
        }

        var queueFamilies = findQueueFamilies(device);

        if (!queueFamilies.hasAllValues()) {
            return 0;
        }

        if (!supportsRequiredExtensions(device)) {
            return 0;
        }

        var swapChainSupport = checkSwapChainSupport(device);
        if (swapChainSupport.isMissingSupport()) {
            swapChainSupport.free();
            return 0;
        }

        swapChainSupport.free();

        vkGetPhysicalDeviceProperties(device, state.device.deviceProperties);
        if (state.device.deviceProperties.deviceType() == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) {
            score += 1_000_000;
        }

        score += state.device.deviceProperties.limits().maxImageDimension2D();

        return score;
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

        for (VkPhysicalDevice device : vkPhysicalDevices) {
            int score = scoreDevice(device);
            if (score > highestScore) {
                highestScore = score;
                bestChoice = device;
            }
        }

        if (null == bestChoice) {
            var message =
                    SafeResourceLoader.getString(
                            "VULKAN_NO_PHYSICAL_DEVICE", GraphicsPlugin.getResourceBundle());
            log.error(message);
            throw new RenderException(message);
        }

        return bestChoice;
    }

    /**
     * Check if the specified device supports the required device extensions.
     *
     * @param device The device
     * @return
     */
    private boolean supportsRequiredExtensions(@NonNull VkPhysicalDevice device) {
        vkEnumerateDeviceExtensionProperties(device, (String) null, intOutput, null);
        var properties = VkExtensionProperties.malloc(intOutput.get(0));
        vkEnumerateDeviceExtensionProperties(device, (String) null, intOutput, properties);

        List<String> missingExtensions = new ArrayList<>(REQUIRED_DEVICE_EXTENSIONS);

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
    public void setupData(@NonNull Scene scene) {}

    @Override
    public void swapPipeline(final int config) {}
}
