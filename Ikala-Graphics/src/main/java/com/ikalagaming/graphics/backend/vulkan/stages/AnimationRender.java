package com.ikalagaming.graphics.backend.vulkan.stages;

import static com.ikalagaming.graphics.backend.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.base.State;
import com.ikalagaming.graphics.backend.vulkan.ShaderBindings;
import com.ikalagaming.graphics.backend.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.backend.vulkan.VulkanState;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

/** Handles computations for animated models. */
@Setter
@Slf4j
public class AnimationRender implements RenderStage {

    private static void updateAnimationOffsets(Model model, int entityCount) {
        // TODO(ches) complete this
    }

    private static void updateInstancedStorage(Model model, int entityCount) {
        // TODO(ches) complete this
    }

    /** The shader to use for rendering. */
    @NonNull private ShaderVulkan shader;

    /** VkDescriptorSetLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long descriptorSetLayout;

    /** VkPipelineLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLayout;

    /** VkPipeline pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipeline;

    /**
     * Set up the animation render stage.
     *
     * @param shader The shader to use for rendering.
     */
    public AnimationRender(final @NonNull ShaderVulkan shader) {
        this.shader = shader;
        this.descriptorSetLayout = VK_NULL_HANDLE;
        this.pipelineLayout = VK_NULL_HANDLE;
        this.pipeline = VK_NULL_HANDLE;
    }

    @Override
    public void initialize(@NonNull State state) {
        log.debug("Initializing animation render");
        VulkanState vulkanState = (VulkanState) state;
        createPipelineLayout(vulkanState);
        createPipeline(vulkanState);
    }

    @Override
    public void cleanup(@NonNull State state) {
        VulkanState vulkanState = (VulkanState) state;
        vkDestroyPipeline(vulkanState.device.logical, pipeline, null);
        pipeline = VK_NULL_HANDLE;
        vkDestroyPipelineLayout(vulkanState.device.logical, pipelineLayout, null);
        pipelineLayout = VK_NULL_HANDLE;
        vkDestroyDescriptorSetLayout(vulkanState.device.logical, descriptorSetLayout, null);
        descriptorSetLayout = VK_NULL_HANDLE;
    }

    /**
     * Compute animation transformations for all animated models in the scene.
     *
     * @param scene The scene we are rendering.
     */
    @Override
    public void render(Scene scene, @NonNull Window window, State state) {
        shader.bind();

        for (Model model : scene.getModelMap().values()) {
            int entityCount = model.getEntitiesList().size();
            if (!model.isAnimated() || entityCount == 0) {
                continue;
            }

            updateInstancedStorage(model, entityCount);

            updateAnimationOffsets(model, entityCount);

            for (MeshData meshData : model.getMeshDataList()) {
                final int vertexCount = meshData.getVertexCount();
                // TODO(ches) render
            }
        }

        shader.unbind();
    }

    private void createPipelineLayout(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack);
            pushConstantRanges.get(0).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT).size(Long.BYTES);

            IntBuffer descriptorVariableFlags = stack.ints(0, 0, 0, 0, 0);

            VkDescriptorSetLayoutBindingFlagsCreateInfo descriptorSetBindingFlags =
                    VkDescriptorSetLayoutBindingFlagsCreateInfo.calloc(stack);
            descriptorSetBindingFlags
                    .sType$Default()
                    .bindingCount(5)
                    .pBindingFlags(descriptorVariableFlags);

            VkDescriptorSetLayoutBinding.Buffer descriptorSetLayoutBindings =
                    VkDescriptorSetLayoutBinding.calloc(5, stack);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.Animation.ANIMATION_DATA_BINDING)
                    .binding(ShaderBindings.Animation.ANIMATION_DATA_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.Animation.ANIMATION_OFFSETS_BINDING)
                    .binding(ShaderBindings.Animation.ANIMATION_OFFSETS_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.Animation.MODEL_DATA_BINDING)
                    .binding(ShaderBindings.Animation.MODEL_DATA_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.Animation.BONE_WEIGHT_BINDING)
                    .binding(ShaderBindings.Animation.BONE_WEIGHT_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.Animation.ANIMATION_TARGET_BINDING)
                    .binding(ShaderBindings.Animation.ANIMATION_TARGET_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);

            VkDescriptorSetLayoutCreateInfo descriptorSetLayoutCreateInfo =
                    VkDescriptorSetLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pNext(descriptorSetBindingFlags)
                            .pBindings(descriptorSetLayoutBindings)
                            .flags(VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT);

            checkError(
                    vkCreateDescriptorSetLayout(
                            state.device.logical, descriptorSetLayoutCreateInfo, null, longOutput));
            descriptorSetLayout = longOutput.get(0);

            LongBuffer descriptorSetLayoutAddress = stack.longs(descriptorSetLayout);

            VkPipelineLayoutCreateInfo pipelineLayoutCreateInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack);
            pipelineLayoutCreateInfo
                    .sType$Default()
                    .setLayoutCount(1)
                    .pSetLayouts(descriptorSetLayoutAddress)
                    .pPushConstantRanges(pushConstantRanges);
            checkError(
                    vkCreatePipelineLayout(
                            state.device.logical, pipelineLayoutCreateInfo, null, longOutput));
            pipelineLayout = longOutput.get(0);
        }
    }

    private void createPipeline(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            VkComputePipelineCreateInfo.Buffer pipelineCreateInfos =
                    VkComputePipelineCreateInfo.calloc(1, stack);
            pipelineCreateInfos
                    .get(0)
                    .sType$Default()
                    .pNext(VK_NULL_HANDLE)
                    .flags(0)
                    .stage(shader.shaderStages.get(0))
                    .layout(pipelineLayout)
                    .basePipelineHandle(VK_NULL_HANDLE)
                    .basePipelineIndex(0);

            checkError(
                    vkCreateComputePipelines(
                            state.device.logical,
                            VK_NULL_HANDLE,
                            pipelineCreateInfos,
                            null,
                            longOutput));

            pipeline = longOutput.get(0);
        }
    }
}
