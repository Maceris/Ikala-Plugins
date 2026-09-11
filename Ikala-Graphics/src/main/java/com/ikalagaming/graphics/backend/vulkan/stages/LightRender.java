package com.ikalagaming.graphics.backend.vulkan.stages;

import static com.ikalagaming.graphics.backend.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK12.*;
import static org.lwjgl.vulkan.VK12.VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.base.State;
import com.ikalagaming.graphics.backend.vulkan.*;
import com.ikalagaming.graphics.graph.CascadeShadowSplit;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.*;

import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.List;

/**
 * Handles rendering the lighting for a scene, given the g-buffer, lighting, and shadow information.
 */
@Setter
@Slf4j
public class LightRender implements RenderStage {

    /** VkDescriptorSet's for a frame, will be VK_NULL_HANDLE if not set up. */
    private static class Descriptors {
        /** The total number of descriptors to create, not how many actually get bound per frame. */
        public static final int COUNT = 5;

        public long uniforms = VK_NULL_HANDLE;
        public long pointLights = VK_NULL_HANDLE;
        public long spotLights = VK_NULL_HANDLE;
        public long materials = VK_NULL_HANDLE;
        public long textures = VK_NULL_HANDLE;

        /** Clear values so we don't refer to junk descriptor handles. */
        public void reset() {
            uniforms = VK_NULL_HANDLE;
            pointLights = VK_NULL_HANDLE;
            spotLights = VK_NULL_HANDLE;
            materials = VK_NULL_HANDLE;
            textures = VK_NULL_HANDLE;
        }
    }

    /** The shader to use for rendering. */
    @NonNull private ShaderVulkan shader;

    /** A mesh for rendering onto. */
    @NonNull private QuadMesh quadMesh;

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
     * Set up the light render.
     *
     * @param shader Shader to use for rendering.
     * @param quadMesh Mesh for rendering onto.
     */
    public LightRender(final @NonNull ShaderVulkan shader, final @NonNull QuadMesh quadMesh) {
        this.shader = shader;
        this.quadMesh = quadMesh;

        this.descriptorSetLayout = VK_NULL_HANDLE;
        this.pipelineLayout = VK_NULL_HANDLE;
        this.pipeline = VK_NULL_HANDLE;
        this.descriptorPool = VK_NULL_HANDLE;

        this.descriptors = new Descriptors[GraphicsManager.MAX_FRAMES_IN_FLIGHT];
        for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
            this.descriptors[i] = new Descriptors();
        }
    }

    @Override
    public void initialize(@NonNull State state) {
        log.debug("Initializing light render");
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
        vkDestroyDescriptorSetLayout(vulkanState.device.logical, descriptorSetLayout, null);
        vkDestroyPipeline(vulkanState.device.logical, pipeline, null);
        pipeline = VK_NULL_HANDLE;
        vkDestroyPipelineLayout(vulkanState.device.logical, pipelineLayout, null);
        pipelineLayout = VK_NULL_HANDLE;
        vkDestroyDescriptorSetLayout(vulkanState.device.logical, descriptorSetLayout, null);
        descriptorSetLayout = VK_NULL_HANDLE;
    }

    @Override
    public void render(Scene scene, @NonNull Window window, State state, int renderConfig) {
        VulkanState vulkanState = (VulkanState) state;
        final VkCommandBuffer commandBuffer =
                vulkanState.commandBuffersGraphics[vulkanState.frameIndex];

        updateLights(
                scene,
                vulkanState.perFrameData[vulkanState.frameIndex].lightPointLights,
                vulkanState.perFrameData[vulkanState.frameIndex].lightSpotLights);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer uniformData = stack.calloc(ShaderBindings.Light.UNIFORMS_BUFFER_SIZE);

            scene.getProjection()
                    .getInverseProjectionMatrix()
                    .get(ShaderBindings.Light.INVERSE_PROJECTION_MATRIX_OFFSET, uniformData);
            scene.getCamera()
                    .getInvViewMatrix()
                    .get(ShaderBindings.Light.INVERSE_VIEW_MATRIX_OFFSET, uniformData);

            int offset = ShaderBindings.Light.AMBIENT_LIGHT_OFFSET;
            scene.getSceneLights().getAmbientLight().getColor().get(offset, uniformData);
            offset += 3 * Float.BYTES;
            uniformData.putFloat(offset, scene.getSceneLights().getAmbientLight().getIntensity());

            offset = ShaderBindings.Light.DIRECTIONAL_LIGHT_OFFSET;
            scene.getSceneLights().getDirLight().getColor().get(offset, uniformData);
            offset += 3 * Float.BYTES;
            // ignoring padding
            offset += Float.BYTES;
            Vector4f auxDir = new Vector4f(scene.getSceneLights().getDirLight().getDirection(), 0);
            auxDir.mul(scene.getCamera().getViewMatrix());
            Vector3f dir = new Vector3f(auxDir.x, auxDir.y, auxDir.z);
            dir.get(offset, uniformData);

            uniformData.putInt(
                    ShaderBindings.Light.POINT_LIGHT_COUNT_OFFSET,
                    scene.getSceneLights().getPointLights().size());
            uniformData.putInt(
                    ShaderBindings.Light.SPOT_LIGHT_COUNT_OFFSET,
                    scene.getSceneLights().getSpotLights().size());

            offset = ShaderBindings.Light.FOG_OFFSET;
            scene.getFog().getColor().get(offset, uniformData);
            offset += 3 * Float.BYTES;
            uniformData.putFloat(offset, scene.getFog().getDensity());
            offset += Float.BYTES;
            uniformData.putInt(offset, scene.getFog().isActive() ? 1 : 0);
            // ignoring padding

            offset = ShaderBindings.Light.CASCADE_SHADOWS_OFFSET;

            CascadeShadowSplit[] cascadeShadowSplits =
                    vulkanState.perFrameData[vulkanState.frameIndex].cascadeShadowSplits;
            for (int i = 0; i < CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT; ++i) {
                CascadeShadowSplit cascadeShadowSplit = cascadeShadowSplits[i];

                cascadeShadowSplit.getProjViewMatrix().get(offset, uniformData);
                offset += 4 * 4 * Float.BYTES;
                uniformData.putFloat(offset, cascadeShadowSplit.getSplitDistance());
                offset += Float.BYTES;
            }

            offset = ShaderBindings.Light.BASE_COLOR_SAMPLER_INDEX_OFFSET;

            GBuffer gBuffer = vulkanState.perFrameData[vulkanState.frameIndex].gBuffer;
            // TODO(ches) figure out the texture indices

            vkCmdUpdateBuffer(
                    commandBuffer, descriptors[vulkanState.frameIndex].uniforms, 0, uniformData);
        }

        // TODO(ches) render things here

    }

    /**
     * Load all the point lights into the SSBO for rendering.
     *
     * @param scene The scene to fetch lights from.
     */
    private void setupPointLightBuffer(@NonNull Scene scene, SharedBuffer pointLightBuffer) {
        List<PointLight> pointLights = scene.getSceneLights().getPointLights();
        final Matrix4f viewMatrix = scene.getCamera().getViewMatrix();

        if (pointLights.size() > PipelineVulkan.MAX_LIGHTS_SUPPORTED) {
            log.warn(
                    "Only {} point lights are supported but there are {} in the scene",
                    PipelineVulkan.MAX_LIGHTS_SUPPORTED,
                    pointLights.size());
        }
        /*
         * Position (vec3 + ignored), color (vec3), intensity (1),
         */
        final int STRUCT_SIZE = 3 + 1 + 3 + 1;

        final int lightsToRender =
                Math.min(PipelineVulkan.MAX_LIGHTS_SUPPORTED, pointLights.size());

        FloatBuffer lightBuffer = MemoryUtil.memAllocFloat(lightsToRender * STRUCT_SIZE);

        Vector4f lightPosition = new Vector4f();
        final float padding = 0.0f;
        for (int i = 0; i < lightsToRender; ++i) {
            PointLight light = pointLights.get(i);
            lightPosition.set(light.getPosition(), 1);
            lightPosition.mul(viewMatrix);
            lightBuffer.put(lightPosition.x);
            lightBuffer.put(lightPosition.y);
            lightBuffer.put(lightPosition.z);
            lightBuffer.put(padding);
            lightBuffer.put(light.getColor().x);
            lightBuffer.put(light.getColor().y);
            lightBuffer.put(light.getColor().z);
            lightBuffer.put(light.getIntensity());
        }

        lightBuffer.flip();

        MemoryUtil.memCopy(
                MemoryUtil.memAddress(lightBuffer),
                pointLightBuffer.allocationInfo.pMappedData(),
                (long) lightsToRender * STRUCT_SIZE);

        MemoryUtil.memFree(lightBuffer);
    }

    /**
     * Load all the spotlights into the SSBO for rendering.
     *
     * @param scene The scene to fetch lights from.
     */
    private void setupSpotLightBuffer(@NonNull Scene scene, SharedBuffer spotLightBuffer) {
        List<SpotLight> spotLights = scene.getSceneLights().getSpotLights();
        final Matrix4f viewMatrix = scene.getCamera().getViewMatrix();

        if (spotLights.size() > PipelineVulkan.MAX_LIGHTS_SUPPORTED) {
            log.warn(
                    "Only {} spotlights are supported but there are {} in the scene",
                    PipelineVulkan.MAX_LIGHTS_SUPPORTED,
                    spotLights.size());
        }

        /*
         * Position (vec3), padding (1), color (vec3), intensity (1), cone direction (vec3), cutoff (1) in that order.
         */
        final int STRUCT_SIZE = 3 + 1 + 3 + 1 + 3 + 1;

        final int lightsToRender = Math.min(PipelineVulkan.MAX_LIGHTS_SUPPORTED, spotLights.size());

        FloatBuffer lightBuffer = MemoryUtil.memAllocFloat(lightsToRender * STRUCT_SIZE);

        Vector4f lightPosition = new Vector4f();
        Vector4f lightDirection = new Vector4f();
        final float padding = 0.0f;
        for (int i = 0; i < lightsToRender; ++i) {
            SpotLight light = spotLights.get(i);
            lightPosition.set(light.getPointLight().getPosition(), 1);
            lightPosition.mul(viewMatrix);
            lightBuffer.put(lightPosition.x);
            lightBuffer.put(lightPosition.y);
            lightBuffer.put(lightPosition.z);
            lightBuffer.put(padding);
            lightBuffer.put(light.getPointLight().getColor().x);
            lightBuffer.put(light.getPointLight().getColor().y);
            lightBuffer.put(light.getPointLight().getColor().z);
            lightBuffer.put(light.getPointLight().getIntensity());
            lightDirection.set(light.getConeDirection(), 1);
            lightDirection.mul(viewMatrix);
            lightBuffer.put(lightDirection.x);
            lightBuffer.put(lightDirection.y);
            lightBuffer.put(lightDirection.z);
            lightBuffer.put(light.getCutOff());
        }
        lightBuffer.flip();

        MemoryUtil.memCopy(
                MemoryUtil.memAddress(lightBuffer),
                spotLightBuffer.allocationInfo.pMappedData(),
                (long) lightsToRender * STRUCT_SIZE);

        MemoryUtil.memFree(lightBuffer);
    }

    /**
     * Update the uniforms for lights in the scene.
     *
     * @param scene The scene we are updating.
     */
    private void updateLights(Scene scene, SharedBuffer pointLights, SharedBuffer spotLights) {

        setupPointLightBuffer(scene, pointLights);
        setupSpotLightBuffer(scene, spotLights);
    }

    private void createPipelineLayout(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack);
            pushConstantRanges
                    .get(0)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                    .size(Long.BYTES);

            IntBuffer descriptorVariableFlags =
                    stack.ints(
                            /* Uniforms */
                            0,
                            /* Point lights */
                            VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT,
                            /* Spotlights */
                            VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT,
                            /* Materials */
                            VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT,
                            /* Textures */
                            VK_DESCRIPTOR_BINDING_VARIABLE_DESCRIPTOR_COUNT_BIT
                                    | VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT
                                    | VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT);

            VkDescriptorSetLayoutBindingFlagsCreateInfo descriptorSetBindingFlags =
                    VkDescriptorSetLayoutBindingFlagsCreateInfo.calloc(stack);
            descriptorSetBindingFlags
                    .sType$Default()
                    .bindingCount(5)
                    .pBindingFlags(descriptorVariableFlags);

            VkDescriptorSetLayoutBinding.Buffer descriptorSetLayoutBindings =
                    VkDescriptorSetLayoutBinding.calloc(5, stack);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.Light.UNIFORMS_BINDING)
                    .binding(ShaderBindings.Light.UNIFORMS_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.Light.POINT_LIGHT_BINDING)
                    .binding(ShaderBindings.Light.POINT_LIGHT_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.Light.SPOT_LIGHT_BINDING)
                    .binding(ShaderBindings.Light.SPOT_LIGHT_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.Light.MATERIALS_BINDING)
                    .binding(ShaderBindings.Light.MATERIALS_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.Light.TEXTURES_BINDING)
                    .binding(ShaderBindings.Light.TEXTURES_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(state.device.physical.maxBindlessImages)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);

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

            final int TYPES_OF_DESCRIPTORS = 3;
            VkDescriptorPoolSize.Buffer poolSizes =
                    VkDescriptorPoolSize.calloc(
                            GraphicsManager.MAX_FRAMES_IN_FLIGHT * TYPES_OF_DESCRIPTORS, stack);
            for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
                poolSizes
                        .get(i * TYPES_OF_DESCRIPTORS)
                        .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                        .descriptorCount(1);
                poolSizes
                        .get(i * TYPES_OF_DESCRIPTORS + 1)
                        .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                        .descriptorCount(8);
                poolSizes
                        .get(i * TYPES_OF_DESCRIPTORS + 2)
                        .type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .descriptorCount(3);
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
                descriptors[i].uniforms =
                        setAddresses.get(
                                i * Descriptors.COUNT + ShaderBindings.Light.UNIFORMS_BINDING);
                descriptors[i].pointLights =
                        setAddresses.get(
                                i * Descriptors.COUNT + ShaderBindings.Light.POINT_LIGHT_BINDING);
                descriptors[i].spotLights =
                        setAddresses.get(
                                i * Descriptors.COUNT + ShaderBindings.Light.SPOT_LIGHT_BINDING);
                descriptors[i].materials =
                        setAddresses.get(
                                i * Descriptors.COUNT + ShaderBindings.Light.MATERIALS_BINDING);
                descriptors[i].textures =
                        setAddresses.get(
                                i * Descriptors.COUNT + ShaderBindings.Light.TEXTURES_BINDING);
            }
        }
    }

    private void createPipeline(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            VkVertexInputAttributeDescription.Buffer vertexAttributes =
                    VkVertexInputAttributeDescription.calloc(2, stack);

            int offset = 0;
            // Positions
            vertexAttributes
                    .get(0)
                    .binding(0)
                    .location(0)
                    .format(VK_FORMAT_R32G32_SFLOAT)
                    .offset(offset);
            offset += 2 * Float.BYTES;
            // Texture Coordinates
            vertexAttributes
                    .get(1)
                    .binding(0)
                    .location(1)
                    .format(VK_FORMAT_R32G32_SFLOAT)
                    .offset(offset);
            offset += 2 * Float.BYTES;

            VkVertexInputBindingDescription.Buffer vertexBindings =
                    VkVertexInputBindingDescription.calloc(1, stack);
            vertexBindings.get(0).binding(0).stride(offset).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

            VkPipelineVertexInputStateCreateInfo vertexInputStateCreateInfo =
                    VkPipelineVertexInputStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .pVertexBindingDescriptions(vertexBindings)
                            .pVertexAttributeDescriptions(vertexAttributes);

            VkPipelineInputAssemblyStateCreateInfo inputAssemblyState =
                    VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);

            VkPipelineViewportStateCreateInfo viewportState =
                    VkPipelineViewportStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .viewportCount(1)
                            .scissorCount(1);

            IntBuffer dynamicStates =
                    stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR);
            VkPipelineDynamicStateCreateInfo dynamicState =
                    VkPipelineDynamicStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .pDynamicStates(dynamicStates);

            VkPipelineDepthStencilStateCreateInfo depthStencilState =
                    VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .depthTestEnable(true)
                            .depthWriteEnable(true)
                            .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL);

            IntBuffer imageFormat = stack.ints(VK_FORMAT_R8G8B8A8_SRGB);

            VkPipelineRenderingCreateInfo renderingCreateInfo =
                    VkPipelineRenderingCreateInfo.calloc(stack)
                            .sType$Default()
                            .colorAttachmentCount(1)
                            .pColorAttachmentFormats(imageFormat)
                            .depthAttachmentFormat(state.device.physical.depthFormat);

            VkPipelineColorBlendAttachmentState.Buffer blendAttachments =
                    VkPipelineColorBlendAttachmentState.calloc(1, stack);
            blendAttachments
                    .get(0)
                    .colorWriteMask(
                            VK_COLOR_COMPONENT_R_BIT
                                    | VK_COLOR_COMPONENT_G_BIT
                                    | VK_COLOR_COMPONENT_B_BIT
                                    | VK_COLOR_COMPONENT_A_BIT);

            VkPipelineColorBlendStateCreateInfo colorBlendState =
                    VkPipelineColorBlendStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .attachmentCount(4)
                            .pAttachments(blendAttachments);
            VkPipelineRasterizationStateCreateInfo rasterizationState =
                    VkPipelineRasterizationStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .lineWidth(1.0f);
            VkPipelineMultisampleStateCreateInfo multisampleState =
                    VkPipelineMultisampleStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

            VkGraphicsPipelineCreateInfo.Buffer pipelineCreateInfos =
                    VkGraphicsPipelineCreateInfo.calloc(1, stack);
            pipelineCreateInfos
                    .get(0)
                    .sType$Default()
                    .pNext(renderingCreateInfo)
                    .stageCount(shader.shaderModules.length)
                    .pStages(shader.shaderStages)
                    .pVertexInputState(vertexInputStateCreateInfo)
                    .pInputAssemblyState(inputAssemblyState)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizationState)
                    .pMultisampleState(multisampleState)
                    .pDepthStencilState(depthStencilState)
                    .pColorBlendState(colorBlendState)
                    .pDynamicState(dynamicState)
                    .layout(pipelineLayout)
                    .renderPass(VK_NULL_HANDLE);

            checkError(
                    vkCreateGraphicsPipelines(
                            state.device.logical,
                            VK_NULL_HANDLE,
                            pipelineCreateInfos,
                            null,
                            longOutput));

            pipeline = longOutput.get(0);
        }
    }
}
