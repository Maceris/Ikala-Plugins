package com.ikalagaming.graphics.backend.vulkan.stages;

import static com.ikalagaming.graphics.backend.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK12.*;
import static org.lwjgl.vulkan.VK12.VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.base.State;
import com.ikalagaming.graphics.backend.base.UniformsMap;
import com.ikalagaming.graphics.backend.vulkan.*;
import com.ikalagaming.graphics.frontend.Buffer;
import com.ikalagaming.graphics.graph.CascadeShadow;
import com.ikalagaming.graphics.scene.Fog;
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

    /** The binding for the point light SSBO. */
    public static final int POINT_LIGHT_BINDING = 0;

    /** The binding for the spotlight SSBO. */
    public static final int SPOT_LIGHT_BINDING = 1;

    /** The binding for the materials SSBO. */
    public static final int MATERIALS_BINDING = 2;

    /** The shader to use for rendering. */
    @NonNull private ShaderVulkan shader;

    /** The cascade shadows information. */
    @NonNull private List<CascadeShadow> cascadeShadows;

    /** The buffer to use for storing point light info. */
    @NonNull private Buffer pointLightsBuffer;

    /** The buffer to use for storing spotlight info. */
    @NonNull private Buffer spotLightsBuffer;

    /** A mesh for rendering onto. */
    @NonNull private QuadMesh quadMesh;

    /** VkDescriptorSetLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long descriptorSetLayout;

    /** VkPipelineLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLayout;

    /** VkPipeline pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipeline;

    /**
     * Set up the light render.
     *
     * @param shader Shader to use for rendering.
     * @param cascadeShadows Cascade shadows information.
     * @param pointLightsBuffer Buffer to use for storing point light info.
     * @param spotLightsBuffer Buffer to use for storing spotlight info.
     * @param quadMesh Mesh for rendering onto.
     */
    public LightRender(
            final @NonNull ShaderVulkan shader,
            final @NonNull List<CascadeShadow> cascadeShadows,
            final @NonNull Buffer pointLightsBuffer,
            final @NonNull Buffer spotLightsBuffer,
            final @NonNull QuadMesh quadMesh) {
        this.shader = shader;
        this.cascadeShadows = cascadeShadows;
        this.pointLightsBuffer = pointLightsBuffer;
        this.spotLightsBuffer = spotLightsBuffer;
        this.quadMesh = quadMesh;

        this.descriptorSetLayout = VK_NULL_HANDLE;
        this.pipelineLayout = VK_NULL_HANDLE;
        this.pipeline = VK_NULL_HANDLE;
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
        vkDestroyPipeline(vulkanState.device.logical, pipeline, null);
        pipeline = VK_NULL_HANDLE;
        vkDestroyPipelineLayout(vulkanState.device.logical, pipelineLayout, null);
        pipelineLayout = VK_NULL_HANDLE;
        vkDestroyDescriptorSetLayout(vulkanState.device.logical, descriptorSetLayout, null);
        descriptorSetLayout = VK_NULL_HANDLE;
    }

    @Override
    public void render(Scene scene, @NonNull Window window, State state, int renderConfig) {
        shader.bind();
        var uniformsMap = shader.getUniformMap();

        updateLights(scene, pointLightsBuffer, spotLightsBuffer, uniformsMap);

        int nextTexture = 0;
        long[] textureIds = new long[] {};
        if (textureIds != null) {
            for (long textureId : textureIds) {
                // TODO(ches) opengl bound things here
                nextTexture += 1;
            }
        }

        uniformsMap.setUniform(ShaderUniforms.Light.BASE_COLOR_SAMPLER, 0);
        uniformsMap.setUniform(ShaderUniforms.Light.NORMAL_SAMPLER, 1);
        uniformsMap.setUniform(ShaderUniforms.Light.TANGENT_SAMPLER, 2);
        uniformsMap.setUniform(ShaderUniforms.Light.MATERIAL_SAMPLER, 3);
        uniformsMap.setUniform(ShaderUniforms.Light.DEPTH_SAMPLER, 4);

        Fog fog = scene.getFog();
        uniformsMap.setUniform(
                ShaderUniforms.Light.FOG + "." + ShaderUniforms.Light.Fog.ENABLED,
                fog.isActive() ? 1 : 0);
        uniformsMap.setUniform(
                ShaderUniforms.Light.FOG + "." + ShaderUniforms.Light.Fog.COLOR, fog.getColor());
        uniformsMap.setUniform(
                ShaderUniforms.Light.FOG + "." + ShaderUniforms.Light.Fog.DENSITY,
                fog.getDensity());

        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
            uniformsMap.setUniform(ShaderUniforms.Light.SHADOW_MAP_PREFIX + i, nextTexture + i);
            CascadeShadow cascadeShadow = cascadeShadows.get(i);
            uniformsMap.setUniform(
                    ShaderUniforms.Light.CASCADE_SHADOWS
                            + "["
                            + i
                            + "]."
                            + ShaderUniforms.Light.CascadeShadow.PROJECTION_VIEW_MATRIX,
                    cascadeShadow.getProjViewMatrix());
            uniformsMap.setUniform(
                    ShaderUniforms.Light.CASCADE_SHADOWS
                            + "["
                            + i
                            + "]."
                            + ShaderUniforms.Light.CascadeShadow.SPLIT_DISTANCE,
                    cascadeShadow.getSplitDistance());
        }

        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
            // TODO(ches) opengl bound things here
        }

        uniformsMap.setUniform(
                ShaderUniforms.Light.INVERSE_PROJECTION_MATRIX,
                scene.getProjection().getInverseProjectionMatrix());
        uniformsMap.setUniform(
                ShaderUniforms.Light.INVERSE_VIEW_MATRIX, scene.getCamera().getInvViewMatrix());

        // TODO(ches) bind materials buffer
        // TODO(ches) render things here

        shader.unbind();
    }

    /**
     * Load all the point lights into the SSBO for rendering.
     *
     * @param scene The scene to fetch lights from.
     */
    private void setupPointLightBuffer(
            @NonNull Scene scene, int pointLightBuffer, @NonNull UniformsMap uniformsMap) {
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

        // TODO(ches) upload to the buffer

        MemoryUtil.memFree(lightBuffer);

        uniformsMap.setUniform(ShaderUniforms.Light.POINT_LIGHT_COUNT, lightsToRender);
    }

    /**
     * Load all the spotlights into the SSBO for rendering.
     *
     * @param scene The scene to fetch lights from.
     */
    private void setupSpotLightBuffer(
            @NonNull Scene scene, int spotLightBuffer, @NonNull UniformsMap uniformsMap) {
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

        // TODO(ches) upload to the buffer

        MemoryUtil.memFree(lightBuffer);

        uniformsMap.setUniform(ShaderUniforms.Light.SPOT_LIGHT_COUNT, lightsToRender);
    }

    /**
     * Update the uniforms for lights in the scene.
     *
     * @param scene The scene we are updating.
     */
    private void updateLights(
            Scene scene, Buffer pointLights, Buffer spotLights, UniformsMap uniformsMap) {
        Matrix4f viewMatrix = scene.getCamera().getViewMatrix();

        SceneLights sceneLights = scene.getSceneLights();
        AmbientLight ambientLight = sceneLights.getAmbientLight();
        uniformsMap.setUniform(
                ShaderUniforms.Light.AMBIENT_LIGHT
                        + "."
                        + ShaderUniforms.Light.AmbientLight.INTENSITY,
                ambientLight.getIntensity());
        uniformsMap.setUniform(
                ShaderUniforms.Light.AMBIENT_LIGHT + "." + ShaderUniforms.Light.AmbientLight.COLOR,
                ambientLight.getColor());

        DirectionalLight dirLight = sceneLights.getDirLight();
        Vector4f auxDir = new Vector4f(dirLight.getDirection(), 0);
        auxDir.mul(viewMatrix);
        Vector3f dir = new Vector3f(auxDir.x, auxDir.y, auxDir.z);
        uniformsMap.setUniform(
                ShaderUniforms.Light.DIRECTIONAL_LIGHT
                        + "."
                        + ShaderUniforms.Light.DirectionalLight.COLOR,
                dirLight.getColor());
        uniformsMap.setUniform(
                ShaderUniforms.Light.DIRECTIONAL_LIGHT
                        + "."
                        + ShaderUniforms.Light.DirectionalLight.DIRECTION,
                dir);
        uniformsMap.setUniform(
                ShaderUniforms.Light.DIRECTIONAL_LIGHT
                        + "."
                        + ShaderUniforms.Light.DirectionalLight.INTENSITY,
                dirLight.getIntensity());

        setupPointLightBuffer(scene, (int) pointLights.id(), uniformsMap);
        setupSpotLightBuffer(scene, (int) spotLights.id(), uniformsMap);
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
                            /* Base color sampler */
                            0,
                            /* Normal sampler */
                            0,
                            /* Tangent sampler */
                            0,
                            /* Material sampler */
                            0,
                            /* Depth sampler */
                            0,
                            /* Shadow map 0 */
                            0,
                            /* Shadow map 1 */
                            0,
                            /* Shadow map 2 */
                            0,
                            /* Point lights */
                            VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT,
                            /* Spotlights */
                            VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT,
                            /* Materials */
                            VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT);

            VkDescriptorSetLayoutBindingFlagsCreateInfo descriptorSetBindingFlags =
                    VkDescriptorSetLayoutBindingFlagsCreateInfo.calloc(stack);
            descriptorSetBindingFlags
                    .sType$Default()
                    .bindingCount(12)
                    .pBindingFlags(descriptorVariableFlags);

            VkDescriptorSetLayoutBinding.Buffer descriptorSetLayoutBindings =
                    VkDescriptorSetLayoutBinding.calloc(12, stack);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.Light.UNIFORMS_BINDING)
                    .binding(ShaderBindings.Light.UNIFORMS_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.Light.BASE_COLOR_SAMPLER_BINDING)
                    .binding(ShaderBindings.Light.BASE_COLOR_SAMPLER_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.Light.NORMAL_SAMPLER_BINDING)
                    .binding(ShaderBindings.Light.NORMAL_SAMPLER_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.Light.TANGENT_SAMPLER_BINDING)
                    .binding(ShaderBindings.Light.TANGENT_SAMPLER_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.Light.MATERIAL_SAMPLER_BINDING)
                    .binding(ShaderBindings.Light.MATERIAL_SAMPLER_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.Light.DEPTH_SAMPLER_BINDING)
                    .binding(ShaderBindings.Light.DEPTH_SAMPLER_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.Light.SHADOW_MAP_0_BINDING)
                    .binding(ShaderBindings.Light.SHADOW_MAP_0_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.Light.SHADOW_MAP_1_BINDING)
                    .binding(ShaderBindings.Light.SHADOW_MAP_1_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.Light.SHADOW_MAP_2_BINDING)
                    .binding(ShaderBindings.Light.SHADOW_MAP_2_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
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
