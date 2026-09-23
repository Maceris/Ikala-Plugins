package com.ikalagaming.graphics.backend.vulkan.stages;

import static com.ikalagaming.graphics.backend.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.base.State;
import com.ikalagaming.graphics.backend.vulkan.ShaderBindings;
import com.ikalagaming.graphics.backend.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.backend.vulkan.SharedBuffer;
import com.ikalagaming.graphics.backend.vulkan.VulkanState;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

/** Handles computations for animated models. */
@Setter
@Slf4j
public class AnimationRender implements RenderStage {

    /** VkDescriptorSet's for a frame, will be VK_NULL_HANDLE if not set up. */
    private static class Descriptors {
        /** The total number of descriptors to create, not how many actually get bound per frame. */
        public static final int COUNT = 5;

        public long animationData = VK_NULL_HANDLE;
        public long animationOffsets = VK_NULL_HANDLE;
        public long modelData = VK_NULL_HANDLE;
        public long boneWeightData = VK_NULL_HANDLE;
        public long animationTarget = VK_NULL_HANDLE;

        /** Clear values so we don't refer to junk descriptor handles. */
        public void reset() {
            animationData = VK_NULL_HANDLE;
            animationOffsets = VK_NULL_HANDLE;
            modelData = VK_NULL_HANDLE;
            boneWeightData = VK_NULL_HANDLE;
            animationTarget = VK_NULL_HANDLE;
        }
    }

    private static void updateAnimationOffsets(VulkanState state, Model model, int entityCount) {
        ByteBuffer animationOffsets = MemoryUtil.memAlloc(entityCount * Integer.BYTES);
        for (int i = 0; i < entityCount; ++i) {
            Entity entity = model.getEntitiesList().get(i);

            Model.Animation animation = entity.getAnimationState().getCurrentAnimation();
            if (animation == null) {
                animationOffsets.putInt(-1);
                continue;
            }
            int baseOffset = animation.offset();
            int frameIndex = entity.getAnimationState().getCurrentFrameIndex();
            int frameSize = animation.boneCount() * 4 * 4 /* mat4 */ * 4 /* 4 bytes per float */;

            animationOffsets.putInt(baseOffset + frameIndex * frameSize);
        }
        animationOffsets.flip();

        var offsetsBuffer = (SharedBuffer) model.getEntityAnimationOffsetsBuffer();
        offsetsBuffer.ensureFits(animationOffsets.limit(), state);
        MemoryUtil.memCopy(
                MemoryUtil.memGetAddress(animationOffsets, 0),
                offsetsBuffer.allocationInfo.pMappedData(),
                animationOffsets.limit());
        MemoryUtil.memFree(animationOffsets);
    }

    private static void updateInstancedStorage(VulkanState state, Model model, int entityCount) {
        int entityCap = model.getMaxAnimatedBufferCapacity();

        if (entityCount > entityCap) {
            if (entityCap < 4) {
                entityCap = 4;
            }

            while (entityCount >= entityCap) {
                entityCap *= 2;
            }
            model.setMaxAnimatedBufferCapacity(entityCap);

            for (MeshData meshData : model.getMeshDataList()) {
                var targetBuffer = (SharedBuffer) meshData.getAnimationTargetBuffer();
                targetBuffer.ensureFits(
                        (long) entityCap * meshData.getVertexCount() * 14 * 4, state);
            }
        }
    }

    /** The shader to use for rendering. */
    @NonNull private ShaderVulkan shader;

    /** VkDescriptorSetLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long descriptorSetLayout;

    /** VkPipelineLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLayout;

    /** VkPipeline pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipeline;

    /** VkDescriptorPool pointer, will be VK_NULL_HANDLE if not set up. */
    private long descriptorPool;

    /** All the descriptors, one per frame in flight. */
    private Descriptors[] descriptors;

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

        this.descriptors = new Descriptors[GraphicsManager.MAX_FRAMES_IN_FLIGHT];
        for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
            this.descriptors[i] = new Descriptors();
        }
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
        for (Descriptors descriptorSet : this.descriptors) {
            descriptorSet.reset();
        }
        vkDestroyDescriptorPool(vulkanState.device.logical, descriptorPool, null);
        descriptorPool = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, pipeline, null);
        pipeline = VK_NULL_HANDLE;
        vkDestroyPipelineLayout(vulkanState.device.logical, pipelineLayout, null);
        pipelineLayout = VK_NULL_HANDLE;
        vkDestroyDescriptorSetLayout(vulkanState.device.logical, descriptorSetLayout, null);
        descriptorSetLayout = VK_NULL_HANDLE;
    }

    @Override
    public void render(Scene scene, @NonNull Window window, State state, int renderConfig) {

        for (Model model : scene.getModelMap().values()) {
            int entityCount = model.getEntitiesList().size();
            if (!model.isAnimated() || entityCount == 0) {
                continue;
            }

            updateInstancedStorage((VulkanState) state, model, entityCount);
            updateAnimationOffsets((VulkanState) state, model, entityCount);

            for (MeshData meshData : model.getMeshDataList()) {
                final int vertexCount = meshData.getVertexCount();
                // TODO(ches) render
                // TODO(ches) we are going to have to update the buffer bindings a LOT here
                // glDispatchCompute(vertexCount, entityCount, 1);
            }
        }
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

            VkDescriptorPoolSize.Buffer poolSizes =
                    VkDescriptorPoolSize.calloc(GraphicsManager.MAX_FRAMES_IN_FLIGHT, stack);
            for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
                poolSizes.get(i).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(5);
            }
            VkDescriptorPoolCreateInfo descriptorPoolCreateInfo =
                    VkDescriptorPoolCreateInfo.calloc(stack)
                            .sType$Default()
                            .maxSets(GraphicsManager.MAX_FRAMES_IN_FLIGHT * Descriptors.COUNT)
                            .flags(VK_DESCRIPTOR_POOL_CREATE_UPDATE_AFTER_BIND_BIT)
                            .pPoolSizes(poolSizes);

            checkError(
                    vkCreateDescriptorPool(
                            state.device.logical, descriptorPoolCreateInfo, null, longOutput));
            descriptorPool = longOutput.get(0);

            LongBuffer descriptorSetLayoutAddresses =
                    stack.callocLong(GraphicsManager.MAX_FRAMES_IN_FLIGHT);
            for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
                descriptorSetLayoutAddresses.put(i, descriptorSetLayout);
            }

            VkDescriptorSetAllocateInfo descriptorSetAlloc =
                    VkDescriptorSetAllocateInfo.calloc(stack)
                            .sType$Default()
                            .pNext(VK_NULL_HANDLE)
                            .descriptorPool(descriptorPool)
                            .pSetLayouts(descriptorSetLayoutAddresses);
            LongBuffer setAddresses =
                    stack.callocLong(GraphicsManager.MAX_FRAMES_IN_FLIGHT * Descriptors.COUNT);
            checkError(
                    vkAllocateDescriptorSets(
                            state.device.logical, descriptorSetAlloc, setAddresses));
            for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
                descriptors[i].animationData =
                        setAddresses.get(
                                i * Descriptors.COUNT
                                        + ShaderBindings.Animation.ANIMATION_DATA_BINDING);
                descriptors[i].animationOffsets =
                        setAddresses.get(
                                i * Descriptors.COUNT
                                        + ShaderBindings.Animation.ANIMATION_OFFSETS_BINDING);
                descriptors[i].modelData =
                        setAddresses.get(
                                i * Descriptors.COUNT
                                        + ShaderBindings.Animation.MODEL_DATA_BINDING);
                descriptors[i].boneWeightData =
                        setAddresses.get(
                                i * Descriptors.COUNT
                                        + ShaderBindings.Animation.BONE_WEIGHT_BINDING);
                descriptors[i].animationTarget =
                        setAddresses.get(
                                i * Descriptors.COUNT
                                        + ShaderBindings.Animation.ANIMATION_TARGET_BINDING);
            }
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
