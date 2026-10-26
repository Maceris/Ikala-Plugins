package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.graph.CascadeShadowSplit;
import com.ikalagaming.graphics.graph.OccupancyClipmap;
import com.ikalagaming.graphics.scene.Fog;
import com.ikalagaming.graphics.scene.PhysicalSky;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.lights.AmbientLight;
import com.ikalagaming.graphics.scene.lights.DirectionalLight;
import com.ikalagaming.graphics.vulkan.*;
import com.ikalagaming.graphics.vulkan.RenderStage;

import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3dc;
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

    /** The name of the fog's part of the frame timings. */
    public static final String FOG_TIMING = "Fog";

    /** What the particles' lighting is called in frame timings. */
    public static final String PARTICLE_LIGHT_TIMING = "ParticleLight";

    /** The most the fog's phase anisotropy goes either way, short of all one way. */
    private static final float MAX_ANISOTROPY = 0.95f;

    /** The storage buffer bindings in the descriptor set, in the order we update them. */
    private static final int[] STORAGE_BINDINGS = {
        ShaderBindings.Light.LIGHTS_BINDING,
        ShaderBindings.Light.MATERIALS_BINDING,
        ShaderBindings.Light.CLUSTERS_BINDING,
        ShaderBindings.Light.POINT_SHADOWS_BINDING
    };

    /** The shader to use for rendering. */
    @NonNull private ShaderVulkan shader;

    /** A mesh for rendering onto. */
    @NonNull private QuadMesh quadMesh;

    /** Fills the fog volume, which the scene is lit through. */
    @NonNull private final FogRender fogRender;

    /** Lights the particles, with this stage's sets, before they are drawn. */
    @NonNull private final ParticleLight particleLight;

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
     * @param fogRender Fills the fog volume, before the scene is lit through it.
     * @param particleLight Lights the particles, with the same sets.
     */
    public LightRender(
            final @NonNull ShaderVulkan shader,
            final @NonNull QuadMesh quadMesh,
            final @NonNull FogRender fogRender,
            final @NonNull ParticleLight particleLight) {
        this.shader = shader;
        this.quadMesh = quadMesh;
        this.fogRender = fogRender;
        this.particleLight = particleLight;

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
        fogRender.initialize(vulkanState, descriptorSetLayout);
        particleLight.initialize(vulkanState, descriptorSetLayout);
    }

    @Override
    public void cleanup(@NonNull VulkanState vulkanState) {
        fogRender.cleanup(vulkanState);
        particleLight.cleanup(vulkanState);
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
            @NonNull SceneTarget sceneTarget,
            @NonNull VulkanState vulkanState,
            RenderConfig renderConfig) {
        final VkCommandBuffer commandBuffer =
                vulkanState.commandBuffersGraphics[vulkanState.frameIndex];
        final PerFrameData frameData = vulkanState.perFrameData[vulkanState.frameIndex];
        final TextureInfoVulkan target = frameData.sceneColor;

        // The fog volume is filled for the scene's fog or any medium volumes
        frameData.fogFilled = scene.getFog().isActive() || scene.getMediumRegistry().getCount() > 0;
        // The light cull stage wrote the lights and sorted them into clusters
        updateUniforms(scene, vulkanState, frameData, frameData.lightCount);
        SceneRender.writeStorageBindings(
                vulkanState,
                descriptorSets[vulkanState.frameIndex],
                new SharedBuffer[] {
                    frameData.lights,
                    frameData.materials,
                    frameData.lightClusters,
                    frameData.pointShadowMaps
                },
                STORAGE_BINDINGS,
                writtenBuffers[vulkanState.frameIndex]);

        final int width = Math.min(sceneTarget.width(), vulkanState.realSize.width());
        final int height = Math.min(sceneTarget.height(), vulkanState.realSize.height());
        final long[] sets = {
            descriptorSets[vulkanState.frameIndex],
            vulkanState.bindlessTextures.getDescriptorSet(),
            vulkanState.occupancy.getSet(),
            vulkanState.environment.getSet()
        };
        // The fog, lit by what lights the scene, before the scene is lit through it
        if (frameData.fogFilled && width > 0 && height > 0) {
            fogRender.record(commandBuffer, vulkanState, sets, width, height, scene);
            vulkanState.frameTimings.mark(commandBuffer, vulkanState.frameIndex, FOG_TIMING);
        } else {
            fogRender.skip(vulkanState);
        }
        // The particles, lit once each by the same lights, before the billboards draw them
        particleLight.record(commandBuffer, vulkanState, sets, scene);
        vulkanState.frameTimings.mark(commandBuffer, vulkanState.frameIndex, PARTICLE_LIGHT_TIMING);

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
                                sets[0],
                                sets[1],
                                sets[2],
                                sets[3],
                                vulkanState.fogVolume.getReadSet()),
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
     * @param vulkanState The Vulkan state.
     * @param frameData The data for the current frame.
     * @param lightCount How many lights are in the lights buffer.
     */
    private static void updateUniforms(
            @NonNull Scene scene,
            @NonNull VulkanState vulkanState,
            @NonNull PerFrameData frameData,
            int lightCount) {
        ByteBuffer uniformData =
                MemoryUtil.memByteBuffer(
                        frameData.lightUniforms.allocationInfo.pMappedData(),
                        ShaderBindings.Light.UNIFORMS_BUFFER_SIZE);

        scene.getProjection()
                .getInverseJitteredProjectionMatrix()
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
        // Under a physical sky the sun's light crosses the atmosphere first, going red near the
        // horizon and out below it
        final Vector3f sunColor = new Vector3f(dirLight.getColor());
        final PhysicalSky atmosphere = scene.getPhysicalSky();
        if (atmosphere != null) {
            final Vector3f toward = AtmosphereMath.towardSun(dirLight.getDirection());
            sunColor.mul(
                    AtmosphereMath.transmittance(
                            AtmosphereMath.cameraRadius(
                                    atmosphere, scene.getCamera().getPosition().y()),
                            toward.y));
        }
        sunColor.get(offset + ShaderBindings.Light.DirectionalLight.COLOR, uniformData);
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

        final CascadeShadowSplit sky = frameData.skyView;
        sky.getProjViewMatrix()
                .get(ShaderBindings.Light.SKY_PROJECTION_VIEW_MATRIX_OFFSET, uniformData);
        uniformData.putInt(
                ShaderBindings.Light.SKY_MAP_INDEX_OFFSET,
                GraphicsManager.getSettings().skyOcclusion && frameData.skyMap != null
                        ? frameData.skyMap.bindlessIndex
                        : -1);
        uniformData.putFloat(ShaderBindings.Light.SKY_DEPTH_RANGE_OFFSET, sky.getDepthRange());
        uniformData.putFloat(
                ShaderBindings.Light.SKY_LIGHT_FLOOR_OFFSET,
                (float) GraphicsManager.getSettings().skyLightFloor);
        uniformData.putInt(
                ShaderBindings.Light.SKY_OCCLUSION_VIEW_OFFSET,
                scene.getDebugVisualizers().isSkyOcclusionView() ? 1 : 0);
        // The occupancy levels, from render space
        final OccupancyClipmap clipmap = vulkanState.occupancy.getClipmap();
        final Vector3dc camera = scene.getCamera().getPosition();
        boolean occupancyReady = GraphicsManager.getSettings().occupancy;
        for (int level = 0; level < OccupancyClipmap.LEVELS; ++level) {
            final long[] origin = clipmap.origin(level);
            final int size = OccupancyClipmap.VOXEL_SIZES[level];
            final int at = ShaderBindings.Light.OCCUPANCY_ORIGINS_OFFSET + level * 4 * Float.BYTES;
            for (int axis = 0; axis < 3; ++axis) {
                uniformData.putFloat(
                        at + axis * Float.BYTES,
                        (float) ((double) origin[axis] * size - camera.get(axis)));
                uniformData.putInt(
                        ShaderBindings.Light.OCCUPANCY_TEXELS_OFFSET
                                + (level * 4 + axis) * Integer.BYTES,
                        OccupancyClipmap.texel(origin[axis]));
            }
            uniformData.putFloat(at + 3 * Float.BYTES, size);
            occupancyReady &= clipmap.isValid(level);
        }
        uniformData.putInt(ShaderBindings.Light.OCCUPANCY_ENABLED_OFFSET, occupancyReady ? 1 : 0);
        uniformData.putInt(
                ShaderBindings.Light.OCCUPANCY_VIEW_OFFSET,
                scene.getDebugVisualizers().isOccupancyView() ? 1 : 0);
        uniformData.putFloat(
                ShaderBindings.Light.OCCUPANCY_AO_REACH_OFFSET,
                GraphicsManager.getSettings().occupancyAo
                        ? (float) GraphicsManager.getSettings().occupancyAoReach
                        : 0);
        uniformData.putInt(
                ShaderBindings.Light.OCCUPANCY_AO_VIEW_OFFSET,
                scene.getDebugVisualizers().isOccupancyAoView() ? 1 : 0);
        uniformData.putInt(
                ShaderBindings.Light.OCCUPANCY_RAY_VIEW_OFFSET,
                scene.getDebugVisualizers().isOccupancyRayView() ? 1 : 0);
        uniformData.putInt(
                ShaderBindings.Light.OCCUPANCY_RAYS_OFFSET,
                GraphicsManager.getSettings().occupancyLightRays
                        ? Math.clamp(
                                GraphicsManager.getSettings().occupancyRaysPerPixel,
                                0,
                                ShaderBindings.Light.MAX_OCCUPANCY_RAYS)
                        : 0);
        uniformData.putInt(
                ShaderBindings.Light.POINT_ATLAS_INDEX_OFFSET,
                frameData.pointAtlas == null ? -1 : frameData.pointAtlas.bindlessIndex);
        uniformData.putInt(
                ShaderBindings.Light.POINT_DYNAMIC_ATLAS_INDEX_OFFSET,
                frameData.pointDynamicAtlas == null
                        ? -1
                        : frameData.pointDynamicAtlas.bindlessIndex);

        Fog fog = scene.getFog();
        offset = ShaderBindings.Light.FOG_OFFSET;
        fog.getColor().get(offset + ShaderBindings.Light.Fog.COLOR, uniformData);
        uniformData.putFloat(offset + ShaderBindings.Light.Fog.DENSITY, fog.getDensity());
        int fogEnabled = 0;
        if (frameData.fogFilled) {
            fogEnabled |= ShaderBindings.Light.Fog.ENABLED_FOG;
        }
        if (fog.isActive()) {
            fogEnabled |= ShaderBindings.Light.Fog.ENABLED_GLOBAL;
            // The air and haze are the physical sky's
            if (fog.isHaze() && scene.getPhysicalSky() != null) {
                fogEnabled |= ShaderBindings.Light.Fog.ENABLED_HAZE;
            }
        }
        uniformData.putInt(offset + ShaderBindings.Light.Fog.ENABLED, fogEnabled);
        uniformData.putFloat(
                offset + ShaderBindings.Light.Fog.LOG_SCALE,
                VoxelOitMath.logScale(scene.getViewDistance()));
        // Its base height in render space, which is world space with the camera at the origin
        new Vector3f(
                        fog.getHeightFalloff(),
                        (float) (fog.getBaseHeight() - scene.getCamera().getPosition().y()),
                        Math.clamp(fog.getAnisotropy(), -MAX_ANISOTROPY, MAX_ANISOTROPY))
                .get(offset + ShaderBindings.Light.Fog.SHAPE, uniformData);

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
            // What moves is only drawn separately for a cached cascade, and only if there is any
            final TextureInfoVulkan dynamicMap = frameData.cascadeDynamicShadows[i];
            uniformData.putInt(
                    offset + ShaderBindings.Light.CascadeShadow.DYNAMIC_MAP_INDEX,
                    cascadeShadowSplits[i].isCached()
                                    && frameData.dynamicShadowCasters
                                    && dynamicMap != null
                            ? dynamicMap.bindlessIndex
                            : -1);
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
        uniformData.putInt(
                ShaderBindings.Light.EMISSION_SAMPLER_INDEX_OFFSET,
                gBuffer[GBuffer.EMISSION].bindlessIndex);
        uniformData.putInt(
                ShaderBindings.Light.ENVIRONMENT_ENABLED_OFFSET,
                GraphicsManager.getSettings().environmentLighting ? 1 : 0);
        // A physical sky's light is per unit of the sun's, so it follows the sun; w stays 0 for a
        // skybox, scaled to the ambient light in the shader (JOML's new Vector4f() has w 1)
        final PhysicalSky physicalSky = scene.getPhysicalSky();
        final Vector4f environmentScale = new Vector4f(0, 0, 0, 0);
        if (physicalSky != null) {
            final DirectionalLight sun = scene.getSceneLights().getDirLight();
            environmentScale.set(
                    new Vector3f(sun.getColor())
                            .mul(sun.getIntensity() * physicalSky.getStrength()),
                    1);
        }
        environmentScale.get(ShaderBindings.Light.ENVIRONMENT_SCALE_OFFSET, uniformData);
        uniformData.putInt(
                ShaderBindings.Light.ENVIRONMENT_OCCLUSION_OFFSET,
                GraphicsManager.getSettings().environmentOcclusion ? 1 : 0);
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
                    // The fog's injection lights the fog with the same inputs
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT | VK_SHADER_STAGE_COMPUTE_BIT);
            for (int i = 0; i < STORAGE_BINDINGS.length; i++) {
                bindings.get(i + 1)
                        .binding(STORAGE_BINDINGS[i])
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .descriptorCount(1)
                        .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT | VK_SHADER_STAGE_COMPUTE_BIT);
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
                                            state.bindlessTextures.getDescriptorSetLayout(),
                                            state.occupancy.getSetLayout(),
                                            state.environment.getSetLayout(),
                                            state.fogVolume.getReadSetLayout()));
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
