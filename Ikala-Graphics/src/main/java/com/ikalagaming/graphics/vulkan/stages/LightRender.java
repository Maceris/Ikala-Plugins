package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.graph.CascadeShadowSplit;
import com.ikalagaming.graphics.scene.Fog;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.AmbientLight;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;
import com.ikalagaming.graphics.vulkan.*;
import com.ikalagaming.graphics.vulkan.RenderStage;

import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;

/**
 * Handles rendering the lighting for a scene, given the g-buffer, lighting, and shadow information.
 * Renders linear light into the HDR scene color, clearing the sky's pixels for the skybox, and
 * leaves it as a color attachment for the stages after; {@link ToneMap} turns it into the image
 * shown.
 */
@Setter
@Slf4j
public class LightRender implements RenderStage {

    /** The storage buffer bindings in the descriptor set, in the order we update them. */
    private static final int[] STORAGE_BINDINGS = {
        ShaderBindings.Light.LIGHTS_BINDING,
        ShaderBindings.Light.MATERIALS_BINDING,
        ShaderBindings.Light.CLUSTERS_BINDING
    };

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
        this.pipeline = VK_NULL_HANDLE;
        this.descriptorPool = VK_NULL_HANDLE;
        this.descriptorSets = new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT];
        this.writtenBuffers =
                new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT][STORAGE_BINDINGS.length];
    }

    @Override
    public void initialize(@NonNull VulkanState vulkanState) {
        log.debug("Initializing light render");
        createPipelineLayout(vulkanState);
        pipeline =
                FullScreenPass.createPipeline(
                        vulkanState,
                        shader,
                        pipelineLayout,
                        PipelineManagerVulkan.SCENE_COLOR_FORMAT,
                        null);
    }

    @Override
    public void cleanup(@NonNull VulkanState vulkanState) {
        // Freed along with the pool
        Arrays.fill(descriptorSets, VK_NULL_HANDLE);
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
    public void render(
            Scene scene,
            @NonNull Window window,
            @NonNull VulkanState vulkanState,
            int renderConfig) {
        final VkCommandBuffer commandBuffer =
                vulkanState.commandBuffersGraphics[vulkanState.frameIndex];
        final PerFrameData frameData = vulkanState.perFrameData[vulkanState.frameIndex];
        final TextureInfoVulkan target = frameData.sceneColor;

        // The light cull stage wrote the lights and sorted them into clusters
        updateUniforms(scene, frameData, frameData.lightCount);
        SceneRender.writeStorageBindings(
                vulkanState,
                descriptorSets[vulkanState.frameIndex],
                new SharedBuffer[] {frameData.lights, frameData.materials, frameData.lightClusters},
                STORAGE_BINDINGS,
                writtenBuffers[vulkanState.frameIndex]);

        final int width = Math.min(window.getWidth(), vulkanState.realSize.width());
        final int height = Math.min(window.getHeight(), vulkanState.realSize.height());

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Last read by the tone map stage, a frame or more ago
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

            // Cleared, so the sky's pixels are empty until the skybox draws them
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
                vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);

                vkCmdBindDescriptorSets(
                        commandBuffer,
                        VK_PIPELINE_BIND_POINT_GRAPHICS,
                        pipelineLayout,
                        0,
                        stack.longs(
                                descriptorSets[vulkanState.frameIndex],
                                vulkanState.bindlessTextures.getDescriptorSet()),
                        null);
                FullScreenPass.draw(commandBuffer, quadMesh, width, height, stack);
            }

            vkCmdEndRendering(commandBuffer);
        }
    }

    /**
     * Write the uniforms for the current frame.
     *
     * @param scene The scene.
     * @param frameData The data for the current frame.
     * @param lightCount How many lights are in the lights buffer.
     */
    private static void updateUniforms(
            @NonNull Scene scene, @NonNull PerFrameData frameData, int lightCount) {
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

        uniformData.putInt(ShaderBindings.Light.LIGHT_COUNT_OFFSET, lightCount);
        uniformData.putInt(
                ShaderBindings.Light.CLUSTER_HEAT_MAP_OFFSET,
                scene.getDebugVisualizers().isClusterHeatMap() ? 1 : 0);
        uniformData.putFloat(
                ShaderBindings.Light.CLUSTER_LOG_SCALE_OFFSET,
                ClusterMath.logScale(scene.getViewDistance()));

        Fog fog = scene.getFog();
        offset = ShaderBindings.Light.FOG_OFFSET;
        fog.getColor().get(offset + ShaderBindings.Light.Fog.COLOR, uniformData);
        uniformData.putFloat(
                offset + ShaderBindings.Light.Fog.DENSITY, fog.getDensity() * Fog.DENSITY_SCALE);
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
            uniformData.putFloat(
                    offset + ShaderBindings.Light.CascadeShadow.TEXEL_SIZE,
                    cascadeShadowSplits[i].getTexelSize());
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
     * The layout of this stage's per-frame descriptor set, for stages that read the same inputs.
     *
     * @return The VkDescriptorSetLayout.
     */
    public long getDescriptorSetLayout() {
        return descriptorSetLayout;
    }

    /**
     * This stage's descriptor set for a frame, written when the stage renders that frame.
     *
     * @param frameIndex The frame in flight.
     * @return The VkDescriptorSet.
     */
    public long getDescriptorSet(int frameIndex) {
        return descriptorSets[frameIndex];
    }
}
