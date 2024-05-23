package com.ikalagaming.graphics.backend.vulkan;

import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.system.MemoryStack.stackPush;
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
import org.lwjgl.vulkan.VK;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkLayerProperties;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

@Slf4j
public class VulkanInstance implements Instance {

    private static final long NULL = 0L;
    private static final String[] REQUIRED_LAYERS = {};

    private static final String[] VALIDATION_LAYERS = {"VK_LAYER_KHRONOS_validation"};

    private final IntBuffer intOutput = MemoryUtil.memAllocInt(1);
    private final LongBuffer longOutput = MemoryUtil.memAllocLong(1);
    private final PointerBuffer pointerOutput = MemoryUtil.memAllocPointer(1);

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

    @Override
    public void initialize(@NonNull Window window) {
        createVulkanInstance(window);
    }

    private void createVulkanInstance(@NonNull Window window) {
        try (MemoryStack stack = stackPush()) {

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

            PointerBuffer extensionNames = stack.mallocPointer(64);
            PointerBuffer glfwExtensions = glfwGetRequiredInstanceExtensions();
            if (glfwExtensions == null) {
                var message =
                        SafeResourceLoader.getString(
                                "GLFW_EXTENSIONS_NULL", GraphicsPlugin.getResourceBundle());
                log.error(message);
                throw new RenderException(message);
            }

            for (int i = 0; i < glfwExtensions.capacity(); ++i) {
                extensionNames.put(glfwExtensions.get(i));
            }

            checkError(vkEnumerateInstanceExtensionProperties((String) null, intOutput, null));
        }
    }

    private void checkLayers(
            @NonNull MemoryStack stack,
            @NonNull VkLayerProperties.Buffer available,
            String... layers) {}

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
