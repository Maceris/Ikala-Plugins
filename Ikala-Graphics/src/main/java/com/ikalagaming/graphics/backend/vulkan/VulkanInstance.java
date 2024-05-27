package com.ikalagaming.graphics.backend.vulkan;

import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.VK10.*;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.exceptions.RenderException;
import com.ikalagaming.graphics.frontend.Instance;
import com.ikalagaming.graphics.frontend.TextureLoader;
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

    private static final long NULL = 0L;

    private static final String[] REQUIRED_EXTENSIONS = {};

    /**
     * The list of validation layers we want if validation is enabled.
     */
    private static final String[] VALIDATION_LAYERS = {"VK_LAYER_KHRONOS_validation"};

    /**
     * Whether to enable validation layers and logging.
     */
    private static final boolean ENABLE_VALIDATION = true;

    private final IntBuffer intOutput = MemoryUtil.memAllocInt(1);
    private final LongBuffer longOutput = MemoryUtil.memAllocLong(1);
    private final PointerBuffer pointerOutput = MemoryUtil.memAllocPointer(1);

    private final VkDebugUtilsMessengerCallbackEXT debugLogger =
            VkDebugUtilsMessengerCallbackEXT.create(VulkanInstance::logDebugMessage);

    /**
     * Log a debug message from Vulkan. Intended to be used by the {@link #debugLogger}, not called by us.
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

    private VkInstance instance;

    @Override
    public void initialize(@NonNull Window window) {
        createVulkanInstance(window);
    }

    /**
     * Set up the vulkan instance.
     *
     * @param window The window we are setting up to render with.
     * @throws RenderException If an unrecoverable issue occurs setting up vulkan.
     */
    private void createVulkanInstance(@NonNull Window window) {
        try (MemoryStack stack = stackPush()) {

            PointerBuffer requiredExtensionNames = glfwGetRequiredInstanceExtensions();
            if (requiredExtensionNames == null) {
                var message =
                        SafeResourceLoader.getString(
                                "GLFW_EXTENSIONS_NULL", GraphicsPlugin.getResourceBundle());
                log.error(message);
                throw new RenderException(message);
            }

            for (String requiredLayer : REQUIRED_EXTENSIONS) {
                requiredExtensionNames.put(stack.ASCII(requiredLayer));
            }

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

            VkApplicationInfo applicationInfo =
                    VkApplicationInfo.malloc(stack)
                            .sType$Default()
                            .pNext(NULL)
                            .pApplicationName(appName)
                            .applicationVersion(1)
                            .pEngineName(engineName)
                            .engineVersion(1)
                            .apiVersion(VK.getInstanceVersionSupported());

            requiredExtensionNames.flip();
            VkInstanceCreateInfo instanceInfo =
                    VkInstanceCreateInfo.malloc(stack)
                            .sType$Default()
                            .pNext(NULL)
                            .flags(0)
                            .pApplicationInfo(applicationInfo)
                            .ppEnabledLayerNames(requiredLayerNames)
                            .ppEnabledExtensionNames(requiredExtensionNames);
            requiredExtensionNames.clear();

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

            instance = new VkInstance(pointerOutput.get(0), instanceInfo);
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

    @Override
    public void cleanup() {}

    @Override
    public void processResources() {}

    @Override
    public TextureLoader getTextureLoader() {
        return null;
    }

    @Override
    public void render(@NonNull Window window, @NonNull Scene scene) {}

    @Override
    public void resize(int width, int height) {}

    @Override
    public void setupData(@NonNull Scene scene) {}
}
