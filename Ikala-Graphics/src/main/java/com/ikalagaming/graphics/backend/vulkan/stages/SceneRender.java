package com.ikalagaming.graphics.backend.vulkan.stages;

import static com.ikalagaming.graphics.ShaderUniforms.Scene.*;
import static com.ikalagaming.graphics.backend.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.vkCreatePipelineLayout;
import static org.lwjgl.vulkan.VK12.*;
import static org.lwjgl.vulkan.VK12.VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.base.State;
import com.ikalagaming.graphics.backend.vulkan.ShaderBindings;
import com.ikalagaming.graphics.backend.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.backend.vulkan.VulkanState;
import com.ikalagaming.graphics.frontend.*;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.graph.MeshData;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

/** Handles rendering of scene geometry to the g-buffer. */
@Slf4j
public class SceneRender implements RenderStage {

    /** The shader to use for rendering. */
    @NonNull @Setter private ShaderVulkan shader;

    /** The g-buffer for rendering geometry to. */
    @Setter @NonNull private Framebuffer gBuffer;

    /** VkDescriptorSetLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long descriptorSetLayout;

    /** VkPipelineLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLayout;

    /** VkPipeline pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipeline;

    /**
     * Set up the scene render stage.
     *
     * @param shader The shader to use for rendering.
     * @param gBuffer The gbuffer.
     */
    public SceneRender(final @NonNull ShaderVulkan shader, final @NonNull Framebuffer gBuffer) {
        this.shader = shader;
        this.gBuffer = gBuffer;
        this.descriptorSetLayout = VK_NULL_HANDLE;
        this.pipelineLayout = VK_NULL_HANDLE;
        this.pipeline = VK_NULL_HANDLE;
    }

    @Override
    public void initialize(@NonNull State state) {
        log.debug("Initializing scene render");
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
    public void render(Scene scene) {
        commonSceneRender(scene, shader, gBuffer);
    }

    /**
     * Common rendering code for the scene, shared between stages.
     *
     * @param scene The scene we are rendering.
     * @param shader The shader to use for rendering.
     * @param gBuffer The depth map buffers.
     */
    static void commonSceneRender(Scene scene, Shader shader, Framebuffer gBuffer) {
        var uniformsMap = shader.getUniformMap();
        // TODO(ches) clear the framebuffer
        shader.bind();

        updateMaterialBuffers(scene);
        updateMaterialOverrides(scene);

        uniformsMap.setUniform(
                ShaderUniforms.Scene.PROJECTION_MATRIX,
                scene.getProjection().getProjectionMatrix());
        uniformsMap.setUniform(ShaderUniforms.Scene.VIEW_MATRIX, scene.getCamera().getViewMatrix());

        for (Model model : scene.getModelMap().values()) {
            final int entityCount = model.getEntitiesList().size();
            if (entityCount == 0) {
                continue;
            }

            final int commandCount = model.isAnimated() ? entityCount : 1;

            // TODO(ches) figure out right buffer usage or redesign BufferUtil API
            BufferUtil.INSTANCE.bindBuffer(
                    model.getModelMatricesBuffer(), VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            BufferUtil.INSTANCE.bindBuffer(
                    scene.getMaterialCache().getMaterialBuffer(),
                    ShaderBindings.Scene.MATERIALS_BINDING);
            BufferUtil.INSTANCE.bindBuffer(
                    model.getMaterialOverridesBuffer(),
                    ShaderBindings.Scene.MATERIAL_OVERRIDES_BINDING);

            int meshIndex = 0;
            for (MeshData mesh : model.getMeshDataList()) {
                int indexOrFallback = scene.getMaterialCache().getMaterialIndex(mesh.getMaterial());
                Material assignedOrDefaultMaterial =
                        scene.getMaterialCache().getMaterial(indexOrFallback);

                uniformsMap.setUniformUnsigned(MATERIAL_INDEX, indexOrFallback);
                uniformsMap.setUniformUnsigned(MESH_INDEX, meshIndex);

                if (assignedOrDefaultMaterial.getTexture() != null) {
                    // TODO(ches) make sure image is resident
                }
                if (assignedOrDefaultMaterial.getNormalMap() != null) {
                    // TODO(ches) make sure image is resident
                }
                uniformsMap.setUniform(BASE_COLOR_SAMPLER, assignedOrDefaultMaterial.getTexture());
                uniformsMap.setUniform(NORMAL_SAMPLER, assignedOrDefaultMaterial.getNormalMap());

                if (model.isAnimated()) {
                    // TODO(ches) bind mesh.getAnimationTargetBuffer().id()

                } else {
                    // TODO(ches) ... don't bind mesh.getAnimationTargetBuffer().id()
                }
                BufferUtil.INSTANCE.bindBuffer(mesh.getIndexBuffer());
                BufferUtil.INSTANCE.bindBuffer(mesh.getDrawIndirectBuffer());
                // TODO(ches) draw indirect
                meshIndex += 1;
            }

            // TODO(ches) unbind model matrices (?)
        }

        shader.unbind();
    }

    private static void updateMaterialOverrides(Scene scene) {
        MaterialCache cache = scene.getMaterialCache();

        for (Model model : scene.getModelMap().values()) {
            if (model.getEntitiesList().isEmpty() || !model.isMaterialOverridesDirty()) {
                continue;
            }

            final int MESH_COUNT = model.getMeshDataList().size();
            final int ENTITY_COUNT = model.getEntitiesList().size();

            IntBuffer buffer = MemoryUtil.memAllocInt(ENTITY_COUNT * MESH_COUNT);

            for (Entity entity : model.getEntitiesList()) {
                for (Material material : entity.getMaterialOverrides()) {
                    int override = cache.getMaterialIndex(material);
                    buffer.put(override);
                }
            }

            buffer.flip();

            BufferUtil.INSTANCE.bindBuffer(model.getMaterialOverridesBuffer());
            // TODO(ches) figure out right buffer usage or redesign BufferUtil API
            BufferUtil.INSTANCE.bufferData(
                    model.getMaterialOverridesBuffer(), buffer, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            BufferUtil.INSTANCE.unbindBuffer(model.getMaterialOverridesBuffer());

            MemoryUtil.memFree(buffer);
            model.setMaterialOverridesDirty(false);
        }
    }

    private static void updateMaterialBuffers(Scene scene) {
        if (!scene.getMaterialCache().isDirty()) {
            return;
        }

        final int MATERIAL_SIZE = 16 /* floats/ints */ * 4 /* bytes per float/int */;
        final int materialCount = scene.getMaterialCache().getMaterialCount();
        if (materialCount <= 0) {
            return;
        }

        ByteBuffer materialData = MemoryUtil.memAlloc(materialCount * MATERIAL_SIZE);

        for (int i = 0; i < materialCount; ++i) {
            Material material = scene.getMaterialCache().getMaterial(i);

            int normalMapID = 0;
            int textureID = 0;

            if (material.getNormalMap() != null) {
                normalMapID = (int) material.getNormalMap().id();
            }
            if (material.getTexture() != null) {
                textureID = (int) material.getTexture().id();
            }

            Vector4f baseColor = material.getBaseColor();
            materialData.putFloat(baseColor.x);
            materialData.putFloat(baseColor.y);
            materialData.putFloat(baseColor.z);
            materialData.putFloat(baseColor.w);

            materialData.putFloat(material.getAnisotropic());
            materialData.putFloat(material.getClearcoat());
            materialData.putFloat(material.getClearcoatGloss());
            materialData.putFloat(material.getMetallic());

            materialData.putFloat(material.getRoughness());
            materialData.putFloat(material.getSheen());
            materialData.putFloat(material.getSheenTint());
            materialData.putFloat(material.getSpecular());

            materialData.putFloat(material.getSpecularTint());
            materialData.putFloat(material.getSubsurface());
            materialData.putInt(normalMapID);
            materialData.putInt(textureID);
        }

        materialData.flip();

        Buffer materialBuffer = scene.getMaterialCache().getMaterialBuffer();
        BufferUtil.INSTANCE.bindBuffer(materialBuffer);
        // TODO(ches) upload data
        BufferUtil.INSTANCE.unbindBuffer(materialBuffer);

        MemoryUtil.memFree(materialData);
        scene.getMaterialCache().setDirty(false);
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
                            0,
                            0,
                            0,
                            0,
                            VK_DESCRIPTOR_BINDING_VARIABLE_DESCRIPTOR_COUNT_BIT
                                    /* Not every texture slot should need to be filled */
                                    | VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT
                                    /* We will probably update the buffer while figuring out what to render */
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
                    .get(ShaderBindings.Scene.UNIFORMS_BINDING)
                    .binding(ShaderBindings.Scene.UNIFORMS_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.Scene.MODEL_MATRICES_BINDING)
                    .binding(ShaderBindings.Scene.MODEL_MATRICES_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.Scene.MATERIALS_BINDING)
                    .binding(ShaderBindings.Scene.MATERIALS_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.Scene.MATERIAL_OVERRIDES_BINDING)
                    .binding(ShaderBindings.Scene.MATERIAL_OVERRIDES_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
            descriptorSetLayoutBindings
                    .get(ShaderBindings.Scene.TEXTURES_BINDING)
                    .binding(ShaderBindings.Scene.TEXTURES_BINDING)
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
        }
    }

    private void createPipeline(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            VkVertexInputAttributeDescription.Buffer vertexAttributes =
                    VkVertexInputAttributeDescription.calloc(5, stack);

            int offset = 0;
            // Positions
            vertexAttributes
                    .get(0)
                    .binding(0)
                    .location(0)
                    .format(VK_FORMAT_R32G32B32_SFLOAT)
                    .offset(offset);
            offset += 3 * Float.BYTES;

            // Normals
            vertexAttributes
                    .get(1)
                    .binding(0)
                    .location(1)
                    .format(VK_FORMAT_R32G32B32_SFLOAT)
                    .offset(offset);
            offset += 3 * Float.BYTES;

            // Tangents
            vertexAttributes
                    .get(2)
                    .binding(0)
                    .location(2)
                    .format(VK_FORMAT_R32G32B32_SFLOAT)
                    .offset(offset);
            offset += 3 * Float.BYTES;

            // Bitangents
            vertexAttributes
                    .get(3)
                    .binding(0)
                    .location(3)
                    .format(VK_FORMAT_R32G32B32_SFLOAT)
                    .offset(offset);
            offset += 3 * Float.BYTES;

            // Texture coordinates
            vertexAttributes
                    .get(4)
                    .binding(0)
                    .location(4)
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

            IntBuffer imageFormat =
                    stack.ints(
                            /* Base color */
                            VK_FORMAT_R8G8B8A8_SRGB,
                            /* Normal */
                            VK_FORMAT_R8G8B8A8_SRGB,
                            /* Tangent */
                            VK_FORMAT_R8G8B8A8_SRGB,
                            /* Material */
                            VK_FORMAT_R32_UINT);

            VkPipelineRenderingCreateInfo renderingCreateInfo =
                    VkPipelineRenderingCreateInfo.calloc(stack)
                            .sType$Default()
                            .colorAttachmentCount(4)
                            .pColorAttachmentFormats(imageFormat)
                            .depthAttachmentFormat(state.device.physical.depthFormat);

            VkPipelineColorBlendAttachmentState.Buffer blendAttachments =
                    VkPipelineColorBlendAttachmentState.calloc(4, stack);
            blendAttachments
                    .get(0)
                    .colorWriteMask(
                            VK_COLOR_COMPONENT_R_BIT
                                    | VK_COLOR_COMPONENT_G_BIT
                                    | VK_COLOR_COMPONENT_B_BIT
                                    | VK_COLOR_COMPONENT_A_BIT);
            blendAttachments
                    .get(1)
                    .colorWriteMask(
                            VK_COLOR_COMPONENT_R_BIT
                                    | VK_COLOR_COMPONENT_G_BIT
                                    | VK_COLOR_COMPONENT_B_BIT
                                    | VK_COLOR_COMPONENT_A_BIT);
            blendAttachments
                    .get(2)
                    .colorWriteMask(
                            VK_COLOR_COMPONENT_R_BIT
                                    | VK_COLOR_COMPONENT_G_BIT
                                    | VK_COLOR_COMPONENT_B_BIT
                                    | VK_COLOR_COMPONENT_A_BIT);
            blendAttachments
                    .get(3)
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
