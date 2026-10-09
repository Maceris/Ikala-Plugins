package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.graph.CascadeShadowSplit;
import com.ikalagaming.graphics.scene.Fog;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.*;
import com.ikalagaming.graphics.vulkan.*;
import com.ikalagaming.graphics.vulkan.RenderStage;

import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * Handles rendering the lighting for a scene, given the g-buffer, lighting, and shadow information.
 * Renders into the pre-filter image if there is a filter stage, otherwise the final image, and
 * leaves it as a color attachment for the stages after.
 */
@Setter
@Slf4j
public class LightRender implements RenderStage {

    /** Floats in a point light struct: position, padding, color, and intensity. */
    private static final int POINT_LIGHT_FLOATS = 3 + 1 + 3 + 1;

    /** Floats in a spotlight struct: a point light, cone direction, and cutoff. */
    private static final int SPOT_LIGHT_FLOATS = POINT_LIGHT_FLOATS + 3 + 1;

    /** The storage buffer bindings in the descriptor set, in the order we update them. */
    private static final int[] STORAGE_BINDINGS = {
        ShaderBindings.Light.POINT_LIGHT_BINDING,
        ShaderBindings.Light.SPOT_LIGHT_BINDING,
        ShaderBindings.Light.MATERIALS_BINDING
    };

    /** The shader to use for rendering. */
    @NonNull private ShaderVulkan shader;

    /** A mesh for rendering onto. */
    @NonNull private QuadMesh quadMesh;

    /** VkDescriptorSetLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long descriptorSetLayout;

    /** VkPipelineLayout pointer, will be VK_NULL_HANDLE if not set up. */
    private long pipelineLayout;

    /**
     * VkPipeline that alpha blends onto the final image, like OpenGL does when rendering to the
     * back buffer. VK_NULL_HANDLE if not set up.
     */
    private long pipelineAlphaBlend;

    /**
     * VkPipeline that adds onto the pre-filter image, like OpenGL does when rendering to the screen
     * texture. VK_NULL_HANDLE if not set up.
     */
    private long pipelineAdditive;

    /** VkDescriptorPool pointer, will be VK_NULL_HANDLE if not set up. */
    private long descriptorPool;

    /** The VkDescriptorSet for each frame in flight, VK_NULL_HANDLE if not set up. */
    private final long[] descriptorSets;

    /**
     * The VkBuffer each storage binding of each frame's descriptor set points at, so we know when
     * to rewrite them.
     */
    private final long[][] writtenBuffers;

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
        this.pipelineAlphaBlend = VK_NULL_HANDLE;
        this.pipelineAdditive = VK_NULL_HANDLE;
        this.descriptorPool = VK_NULL_HANDLE;
        this.descriptorSets = new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT];
        this.writtenBuffers =
                new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT][STORAGE_BINDINGS.length];
    }

    @Override
    public void initialize(@NonNull VulkanState vulkanState) {
        log.debug("Initializing light render");
        createPipelineLayout(vulkanState);
        pipelineAlphaBlend = createPipeline(vulkanState, false);
        pipelineAdditive = createPipeline(vulkanState, true);
    }

    @Override
    public void cleanup(@NonNull VulkanState vulkanState) {
        // Freed along with the pool
        Arrays.fill(descriptorSets, VK_NULL_HANDLE);
        vkDestroyDescriptorPool(vulkanState.device.logical, descriptorPool, null);
        descriptorPool = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, pipelineAdditive, null);
        pipelineAdditive = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, pipelineAlphaBlend, null);
        pipelineAlphaBlend = VK_NULL_HANDLE;
        vkDestroyPipelineLayout(vulkanState.device.logical, pipelineLayout, null);
        pipelineLayout = VK_NULL_HANDLE;
        vkDestroyDescriptorSetLayout(vulkanState.device.logical, descriptorSetLayout, null);
        descriptorSetLayout = VK_NULL_HANDLE;
    }

    @Override
    public void render(
            Scene scene,
            @NonNull Window window,
            @NonNull VulkanState vulkanState,
            int renderConfig) {
        final VkCommandBuffer commandBuffer =
                vulkanState.commandBuffersGraphics[vulkanState.frameIndex];
        final PerFrameData frameData = vulkanState.perFrameData[vulkanState.frameIndex];
        final boolean hasFilter = RenderConfig.hasFilterStage(renderConfig);
        final TextureInfoVulkan target =
                hasFilter ? frameData.preFilterTexture : frameData.finalTexture;

        final int pointLightCount = updatePointLights(scene, vulkanState, frameData);
        final int spotLightCount = updateSpotLights(scene, vulkanState, frameData);
        updateUniforms(scene, frameData, pointLightCount, spotLightCount);
        SceneRender.writeStorageBindings(
                vulkanState,
                descriptorSets[vulkanState.frameIndex],
                new SharedBuffer[] {
                    frameData.lightPointLights, frameData.lightSpotLights, frameData.materials
                },
                STORAGE_BINDINGS,
                writtenBuffers[vulkanState.frameIndex]);

        final int width = Math.min(window.getWidth(), vulkanState.realSize.width());
        final int height = Math.min(window.getHeight(), vulkanState.realSize.height());

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Last used by the blit to the swapchain or by the filter, a frame or more ago
            VkImageMemoryBarrier2.Buffer barrier = VkImageMemoryBarrier2.calloc(1, stack);
            SceneRender.imageBarrier(
                    barrier.get(0),
                    target.texture,
                    VK_IMAGE_ASPECT_COLOR_BIT,
                    VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT | VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                    VK_ACCESS_2_NONE,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_LAYOUT_UNDEFINED,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            vkCmdPipelineBarrier2(
                    commandBuffer,
                    VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barrier));

            // Cleared to transparent black, like OpenGL
            VkRenderingAttachmentInfo.Buffer colorAttachments =
                    VkRenderingAttachmentInfo.calloc(1, stack);
            colorAttachments
                    .get(0)
                    .sType$Default()
                    .imageView(target.view)
                    .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            VkRenderingInfo renderingInfo =
                    VkRenderingInfo.calloc(stack)
                            .sType$Default()
                            .renderArea(area -> area.extent().set(width, height))
                            .layerCount(1)
                            .pColorAttachments(colorAttachments);
            vkCmdBeginRendering(commandBuffer, renderingInfo);

            if (width > 0 && height > 0) {
                vkCmdBindPipeline(
                        commandBuffer,
                        VK_PIPELINE_BIND_POINT_GRAPHICS,
                        hasFilter ? pipelineAdditive : pipelineAlphaBlend);

                VkViewport.Buffer viewports = VkViewport.calloc(1, stack);
                viewports.get(0).width(width).height(height).minDepth(0).maxDepth(1);
                vkCmdSetViewport(commandBuffer, 0, viewports);
                VkRect2D.Buffer scissors = VkRect2D.calloc(1, stack);
                scissors.get(0).extent().set(width, height);
                vkCmdSetScissor(commandBuffer, 0, scissors);

                vkCmdBindDescriptorSets(
                        commandBuffer,
                        VK_PIPELINE_BIND_POINT_GRAPHICS,
                        pipelineLayout,
                        0,
                        stack.longs(
                                descriptorSets[vulkanState.frameIndex],
                                vulkanState.bindlessTextures.getDescriptorSet()),
                        null);
                vkCmdBindVertexBuffers(
                        commandBuffer,
                        0,
                        stack.longs(quadMesh.vertexBuffer().buffer),
                        stack.longs(0));
                vkCmdBindIndexBuffer(
                        commandBuffer, quadMesh.indexBuffer().buffer, 0, VK_INDEX_TYPE_UINT32);
                vkCmdDrawIndexed(commandBuffer, QuadMesh.INDEX_COUNT, 1, 0, 0, 0);
            }

            vkCmdEndRendering(commandBuffer);
        }
    }

    /**
     * Write the uniforms for the current frame.
     *
     * @param scene The scene.
     * @param frameData The data for the current frame.
     * @param pointLightCount How many point lights are in the buffer.
     * @param spotLightCount How many spotlights are in the buffer.
     */
    private static void updateUniforms(
            @NonNull Scene scene,
            @NonNull PerFrameData frameData,
            int pointLightCount,
            int spotLightCount) {
        ByteBuffer uniformData =
                MemoryUtil.memByteBuffer(
                        frameData.lightUniforms.allocationInfo.pMappedData(),
                        ShaderBindings.Light.UNIFORMS_BUFFER_SIZE);

        scene.getProjection()
                .getInverseProjectionMatrix()
                .get(ShaderBindings.Light.INVERSE_PROJECTION_MATRIX_OFFSET, uniformData);
        scene.getCamera()
                .getInvViewMatrix()
                .get(ShaderBindings.Light.INVERSE_VIEW_MATRIX_OFFSET, uniformData);

        AmbientLight ambientLight = scene.getSceneLights().getAmbientLight();
        int offset = ShaderBindings.Light.AMBIENT_LIGHT_OFFSET;
        ambientLight.getColor().get(offset + ShaderBindings.Light.AmbientLight.COLOR, uniformData);
        uniformData.putFloat(
                offset + ShaderBindings.Light.AmbientLight.INTENSITY, ambientLight.getIntensity());

        // The light direction is in view space, like the other lights and the normals
        DirectionalLight dirLight = scene.getSceneLights().getDirLight();
        Vector4f auxDir = new Vector4f(dirLight.getDirection(), 0);
        auxDir.mul(scene.getCamera().getViewMatrix());
        offset = ShaderBindings.Light.DIRECTIONAL_LIGHT_OFFSET;
        dirLight.getColor().get(offset + ShaderBindings.Light.DirectionalLight.COLOR, uniformData);
        new Vector3f(auxDir.x, auxDir.y, auxDir.z)
                .get(offset + ShaderBindings.Light.DirectionalLight.DIRECTION, uniformData);
        uniformData.putFloat(
                offset + ShaderBindings.Light.DirectionalLight.INTENSITY, dirLight.getIntensity());

        uniformData.putInt(ShaderBindings.Light.POINT_LIGHT_COUNT_OFFSET, pointLightCount);
        uniformData.putInt(ShaderBindings.Light.SPOT_LIGHT_COUNT_OFFSET, spotLightCount);

        Fog fog = scene.getFog();
        offset = ShaderBindings.Light.FOG_OFFSET;
        fog.getColor().get(offset + ShaderBindings.Light.Fog.COLOR, uniformData);
        uniformData.putFloat(offset + ShaderBindings.Light.Fog.DENSITY, fog.getDensity());
        uniformData.putInt(offset + ShaderBindings.Light.Fog.ENABLED, fog.isActive() ? 1 : 0);

        CascadeShadowSplit[] cascadeShadowSplits = frameData.cascadeShadowSplits;
        for (int i = 0; i < CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT; ++i) {
            offset =
                    ShaderBindings.Light.CASCADE_SHADOWS_OFFSET
                            + i * ShaderBindings.Light.CascadeShadow.ARRAY_STRIDE;
            cascadeShadowSplits[i]
                    .getProjViewMatrix()
                    .get(
                            offset + ShaderBindings.Light.CascadeShadow.PROJECTION_VIEW_MATRIX,
                            uniformData);
            uniformData.putFloat(
                    offset + ShaderBindings.Light.CascadeShadow.SPLIT_DISTANCE,
                    cascadeShadowSplits[i].getSplitDistance());
        }

        TextureInfoVulkan[] gBuffer = frameData.gBuffer.textures();
        uniformData.putInt(
                ShaderBindings.Light.BASE_COLOR_SAMPLER_INDEX_OFFSET,
                gBuffer[GBuffer.BASE_COLOR].bindlessIndex);
        uniformData.putInt(
                ShaderBindings.Light.NORMAL_SAMPLER_INDEX_OFFSET,
                gBuffer[GBuffer.NORMAL].bindlessIndex);
        uniformData.putInt(
                ShaderBindings.Light.TANGENT_SAMPLER_INDEX_OFFSET,
                gBuffer[GBuffer.TANGENT].bindlessIndex);
        uniformData.putInt(
                ShaderBindings.Light.MATERIAL_SAMPLER_INDEX_OFFSET,
                gBuffer[GBuffer.MATERIAL].bindlessIndex);
        uniformData.putInt(
                ShaderBindings.Light.DEPTH_SAMPLER_INDEX_OFFSET,
                frameData.gBuffer.depth().bindlessIndex);
        for (int i = 0; i < CascadeShadowSplit.SHADOW_MAP_CASCADE_COUNT; ++i) {
            uniformData.putInt(
                    ShaderBindings.Light.SHADOW_MAP_0_INDEX_OFFSET + i * Integer.BYTES,
                    frameData.cascadeShadows[i].bindlessIndex);
        }
    }

    /**
     * Write the point lights, in view space, into this frame's buffer.
     *
     * @param scene The scene to fetch lights from.
     * @param state The Vulkan state.
     * @param frameData The data for the current frame.
     * @return How many point lights were written.
     */
    private static int updatePointLights(
            @NonNull Scene scene, @NonNull VulkanState state, @NonNull PerFrameData frameData) {
        List<PointLight> pointLights = scene.getSceneLights().getPointLights();
        if (pointLights.size() > PipelineVulkan.MAX_LIGHTS_SUPPORTED) {
            log.warn(
                    "Only {} point lights are supported but there are {} in the scene",
                    PipelineVulkan.MAX_LIGHTS_SUPPORTED,
                    pointLights.size());
        }
        final int lightsToRender =
                Math.min(PipelineVulkan.MAX_LIGHTS_SUPPORTED, pointLights.size());
        frameData.lightPointLights.ensureCapacity(
                (long) lightsToRender * POINT_LIGHT_FLOATS * Float.BYTES, state);
        if (lightsToRender == 0) {
            return 0;
        }

        FloatBuffer lightBuffer =
                MemoryUtil.memFloatBuffer(
                        frameData.lightPointLights.allocationInfo.pMappedData(),
                        lightsToRender * POINT_LIGHT_FLOATS);
        final Matrix4f viewMatrix = scene.getCamera().getViewMatrix();
        final Vector3dc origin = scene.getCamera().getPosition();
        Vector4f lightPosition = new Vector4f();
        for (int i = 0; i < lightsToRender; ++i) {
            putPointLight(lightBuffer, pointLights.get(i), viewMatrix, origin, lightPosition);
        }
        return lightsToRender;
    }

    /**
     * Write the spotlights, in view space, into this frame's buffer.
     *
     * @param scene The scene to fetch lights from.
     * @param state The Vulkan state.
     * @param frameData The data for the current frame.
     * @return How many spotlights were written.
     */
    private static int updateSpotLights(
            @NonNull Scene scene, @NonNull VulkanState state, @NonNull PerFrameData frameData) {
        List<SpotLight> spotLights = scene.getSceneLights().getSpotLights();
        if (spotLights.size() > PipelineVulkan.MAX_LIGHTS_SUPPORTED) {
            log.warn(
                    "Only {} spotlights are supported but there are {} in the scene",
                    PipelineVulkan.MAX_LIGHTS_SUPPORTED,
                    spotLights.size());
        }
        final int lightsToRender = Math.min(PipelineVulkan.MAX_LIGHTS_SUPPORTED, spotLights.size());
        frameData.lightSpotLights.ensureCapacity(
                (long) lightsToRender * SPOT_LIGHT_FLOATS * Float.BYTES, state);
        if (lightsToRender == 0) {
            return 0;
        }

        FloatBuffer lightBuffer =
                MemoryUtil.memFloatBuffer(
                        frameData.lightSpotLights.allocationInfo.pMappedData(),
                        lightsToRender * SPOT_LIGHT_FLOATS);
        final Matrix4f viewMatrix = scene.getCamera().getViewMatrix();
        final Vector3dc origin = scene.getCamera().getPosition();
        Vector4f lightPosition = new Vector4f();
        Vector4f lightDirection = new Vector4f();
        for (int i = 0; i < lightsToRender; ++i) {
            SpotLight light = spotLights.get(i);
            putPointLight(lightBuffer, light.getPointLight(), viewMatrix, origin, lightPosition);
            // A direction, so w is 0 and the camera translation (none in render space) never
            // applies
            lightDirection.set(light.getConeDirection(), 0);
            lightDirection.mul(viewMatrix);
            lightBuffer.put(lightDirection.x);
            lightBuffer.put(lightDirection.y);
            lightBuffer.put(lightDirection.z);
            lightBuffer.put(light.getCutOff());
        }
        return lightsToRender;
    }

    /**
     * Put a point light struct into a buffer, with the position converted to view space.
     *
     * @param buffer The buffer to write into.
     * @param light The light.
     * @param viewMatrix The camera view matrix, from render space to view space.
     * @param origin The world position of the render space origin, the camera position.
     * @param scratch A vector to do math in, so we don't allocate one per light.
     */
    private static void putPointLight(
            @NonNull FloatBuffer buffer,
            @NonNull PointLight light,
            @NonNull Matrix4f viewMatrix,
            @NonNull Vector3dc origin,
            @NonNull Vector4f scratch) {
        final float padding = 0.0f;
        // Relative to the camera in double precision first, so it stays exact far from the origin
        final Vector3dc position = light.getPosition();
        scratch.set(
                (float) (position.x() - origin.x()),
                (float) (position.y() - origin.y()),
                (float) (position.z() - origin.z()),
                1);
        scratch.mul(viewMatrix);
        buffer.put(scratch.x);
        buffer.put(scratch.y);
        buffer.put(scratch.z);
        buffer.put(padding);
        buffer.put(light.getColor().x);
        buffer.put(light.getColor().y);
        buffer.put(light.getColor().z);
        buffer.put(light.getIntensity());
    }

    private void createPipelineLayout(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            VkDescriptorSetLayoutBinding.Buffer bindings =
                    VkDescriptorSetLayoutBinding.calloc(1 + STORAGE_BINDINGS.length, stack);
            bindings.get(0)
                    .binding(ShaderBindings.Light.UNIFORMS_BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            for (int i = 0; i < STORAGE_BINDINGS.length; i++) {
                bindings.get(i + 1)
                        .binding(STORAGE_BINDINGS[i])
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .descriptorCount(1)
                        .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            }

            // Each frame has its own set, only updated once the GPU is done with that frame
            VkDescriptorSetLayoutCreateInfo descriptorSetLayoutCreateInfo =
                    VkDescriptorSetLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pBindings(bindings);
            checkError(
                    vkCreateDescriptorSetLayout(
                            state.device.logical, descriptorSetLayoutCreateInfo, null, longOutput));
            descriptorSetLayout = longOutput.get(0);

            VkPipelineLayoutCreateInfo pipelineLayoutCreateInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pSetLayouts(
                                    stack.longs(
                                            descriptorSetLayout,
                                            state.bindlessTextures.getDescriptorSetLayout()));
            checkError(
                    vkCreatePipelineLayout(
                            state.device.logical, pipelineLayoutCreateInfo, null, longOutput));
            pipelineLayout = longOutput.get(0);

            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(2, stack);
            poolSizes
                    .get(0)
                    .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(GraphicsManager.MAX_FRAMES_IN_FLIGHT);
            poolSizes
                    .get(1)
                    .type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(
                            STORAGE_BINDINGS.length * GraphicsManager.MAX_FRAMES_IN_FLIGHT);
            VkDescriptorPoolCreateInfo descriptorPoolCreateInfo =
                    VkDescriptorPoolCreateInfo.calloc(stack)
                            .sType$Default()
                            .maxSets(GraphicsManager.MAX_FRAMES_IN_FLIGHT)
                            .pPoolSizes(poolSizes);
            checkError(
                    vkCreateDescriptorPool(
                            state.device.logical, descriptorPoolCreateInfo, null, longOutput));
            descriptorPool = longOutput.get(0);

            LongBuffer setLayouts = stack.callocLong(GraphicsManager.MAX_FRAMES_IN_FLIGHT);
            for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
                setLayouts.put(i, descriptorSetLayout);
            }
            VkDescriptorSetAllocateInfo descriptorSetAlloc =
                    VkDescriptorSetAllocateInfo.calloc(stack)
                            .sType$Default()
                            .descriptorPool(descriptorPool)
                            .pSetLayouts(setLayouts);
            checkError(
                    vkAllocateDescriptorSets(
                            state.device.logical, descriptorSetAlloc, descriptorSets));

            // The uniform buffers never change, so write them once
            VkWriteDescriptorSet.Buffer writes =
                    VkWriteDescriptorSet.calloc(GraphicsManager.MAX_FRAMES_IN_FLIGHT, stack);
            for (int i = 0; i < GraphicsManager.MAX_FRAMES_IN_FLIGHT; i++) {
                VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);
                bufferInfo
                        .get(0)
                        .buffer(state.perFrameData[i].lightUniforms.buffer)
                        .offset(0)
                        .range(VK_WHOLE_SIZE);
                writes.get(i)
                        .sType$Default()
                        .dstSet(descriptorSets[i])
                        .dstBinding(ShaderBindings.Light.UNIFORMS_BINDING)
                        .descriptorCount(1)
                        .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                        .pBufferInfo(bufferInfo);
            }
            vkUpdateDescriptorSets(state.device.logical, writes, null);
        }
    }

    /**
     * Create a pipeline for drawing the lit scene onto a full screen quad.
     *
     * @param state The Vulkan state.
     * @param additive True to add onto the target, false to alpha blend onto it.
     * @return The VkPipeline.
     */
    private long createPipeline(@NonNull VulkanState state, boolean additive) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            // Matches the QuadMesh layout
            VkVertexInputAttributeDescription.Buffer vertexAttributes =
                    VkVertexInputAttributeDescription.calloc(2, stack);
            // Positions
            vertexAttributes
                    .get(0)
                    .binding(0)
                    .location(0)
                    .format(VK_FORMAT_R32G32B32_SFLOAT)
                    .offset(0);
            // Texture Coordinates
            vertexAttributes
                    .get(1)
                    .binding(0)
                    .location(1)
                    .format(VK_FORMAT_R32G32_SFLOAT)
                    .offset(3 * Float.BYTES);

            VkVertexInputBindingDescription.Buffer vertexBindings =
                    VkVertexInputBindingDescription.calloc(1, stack);
            vertexBindings
                    .get(0)
                    .binding(0)
                    .stride((3 + 2) * Float.BYTES)
                    .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

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

            VkPipelineDynamicStateCreateInfo dynamicState =
                    VkPipelineDynamicStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .pDynamicStates(
                                    stack.ints(
                                            VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));

            VkPipelineRenderingCreateInfo renderingCreateInfo =
                    VkPipelineRenderingCreateInfo.calloc(stack)
                            .sType$Default()
                            .pColorAttachmentFormats(
                                    stack.ints(PipelineManagerVulkan.SCREEN_FORMAT));

            // OpenGL applies the same factors to the alpha channel too
            final int srcFactor = additive ? VK_BLEND_FACTOR_ONE : VK_BLEND_FACTOR_SRC_ALPHA;
            final int dstFactor =
                    additive ? VK_BLEND_FACTOR_ONE : VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
            VkPipelineColorBlendAttachmentState.Buffer blendAttachments =
                    VkPipelineColorBlendAttachmentState.calloc(1, stack);
            blendAttachments
                    .get(0)
                    .blendEnable(true)
                    .srcColorBlendFactor(srcFactor)
                    .dstColorBlendFactor(dstFactor)
                    .colorBlendOp(VK_BLEND_OP_ADD)
                    .srcAlphaBlendFactor(srcFactor)
                    .dstAlphaBlendFactor(dstFactor)
                    .alphaBlendOp(VK_BLEND_OP_ADD)
                    .colorWriteMask(
                            VK_COLOR_COMPONENT_R_BIT
                                    | VK_COLOR_COMPONENT_G_BIT
                                    | VK_COLOR_COMPONENT_B_BIT
                                    | VK_COLOR_COMPONENT_A_BIT);
            VkPipelineColorBlendStateCreateInfo colorBlendState =
                    VkPipelineColorBlendStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .pAttachments(blendAttachments);
            VkPipelineRasterizationStateCreateInfo rasterizationState =
                    VkPipelineRasterizationStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .polygonMode(VK_POLYGON_MODE_FILL)
                            .cullMode(VK_CULL_MODE_NONE)
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

            return longOutput.get(0);
        }
    }
}
