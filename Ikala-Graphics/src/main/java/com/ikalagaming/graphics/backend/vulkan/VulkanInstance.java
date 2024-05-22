package com.ikalagaming.graphics.backend.vulkan;

import static org.lwjgl.system.MemoryStack.stackPush;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.frontend.Instance;
import com.ikalagaming.graphics.frontend.TextureLoader;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkLayerProperties;

public class VulkanInstance implements Instance {

    private static final String[] REQUIRED_LAYERS = {};

    private static final String[] VALIDATION_LAYERS = {"VK_LAYER_KHRONOS_validation"};

    @Override
    public void initialize(@NonNull Window window) {
        createVulkanInstance();
    }

    private void createVulkanInstance() {
        try (MemoryStack stack = stackPush()) {
            VkApplicationInfo applicationInfo = VkApplicationInfo.malloc(stack);
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
