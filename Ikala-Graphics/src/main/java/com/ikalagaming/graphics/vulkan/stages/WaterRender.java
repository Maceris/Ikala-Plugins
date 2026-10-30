package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.RenderConfig;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.WaterRegistry;
import com.ikalagaming.graphics.scene.WaterTile;
import com.ikalagaming.graphics.vulkan.PerFrameData;
import com.ikalagaming.graphics.vulkan.PipelineManagerVulkan;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.SceneTarget;
import com.ikalagaming.graphics.vulkan.ShaderBindings;
import com.ikalagaming.graphics.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.vulkan.SharedBuffer;
import com.ikalagaming.graphics.vulkan.TextureInfoVulkan;
import com.ikalagaming.graphics.vulkan.VulkanState;
import com.ikalagaming.graphics.vulkan.WaterMath;
import com.ikalagaming.graphics.vulkan.WaterMesh;
import com.ikalagaming.graphics.vulkan.WaterVolume;
import com.ikalagaming.graphics.vulkan.WaveGrid;
import com.ikalagaming.graphics.vulkan.WaveMath;
import com.ikalagaming.graphics.vulkan.WaveletMath;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Draws the scene's water tiles over the lit scene and the sky, before the transparency passes, so
 * glass, particles and volume bricks in front of the water blend over it. The scene's color and
 * depth are copied first, for the shader to see what lies behind the surface through it; the water
 * writes its own depth, so what is under it stays under it. The surfaces are built on the CPU from
 * the tiles whenever they change, on a thread of their own, the last tiles drawn until the new ones
 * are built, and copied into each frame in flight's buffers; each frame the tiles' places relative
 * to the camera and their optics are written to a small list. See {@code water.frag}.
 *
 * <p>Water in wind has waves, see {@link WaveMath}: tiles whose bodies share a wind share a wave
 * climate, up to {@link #MAX_CLIMATES}. Each frame a compute pass fills each climate's profiles
 * from its waves' phases at this time, which the surface then sums over directions.
 */
@Slf4j
public class WaterRender implements RenderStage {

    /** A calm surface's roughness, until waves set it. */
    private static final float CALM_ROUGHNESS = 0.02f;

    /** The most wave climates drawn at once; tiles in further winds are drawn still. */
    public static final int MAX_CLIMATES = 4;

    /** Nanoseconds in a second. */
    private static final double NANOS_PER_SECOND = 1e9;

    /** What the ripples' phases and directions' offsets are picked from. */
    private static final long RIPPLE_SEED = 0x9195_1E5L;

    /** What the climates' waves' phases and directions' offsets are picked from. */
    private static final long WAVE_SEED = 0x5EA5_1DE5L;

    /**
     * A wind the tiles' waves come from.
     *
     * @param speed Its speed, in meters a second.
     * @param direction The way it blows, in radians.
     * @param fetch How far it has blown over the water, in meters.
     */
    private record Wind(float speed, float direction, float fetch) {}

    /**
     * A wave climate: a wind's waves, its directions' amplitudes and random offsets along their
     * profiles, so the directions don't line up, and the slope variance each layer leaves out.
     *
     * @param waves The waves each profile sums.
     * @param amplitudes Each direction's amplitude.
     * @param offsets Each direction's random offset, in meters.
     * @param missingVariance Each layer's left out slope variance.
     * @param atDepths The waves at each of {@link WaveMath#DEPTH_LEVELS}, deep water's first.
     * @param levels Which depth's profiles each depth reads, see {@link WaveMath#levelsUsed}.
     * @param foamJacobian Where its waves break into foam, see {@link WaveMath#foamJacobian}.
     */
    private record Climate(
            WaveMath.Waves waves,
            double[] amplitudes,
            double[] offsets,
            double[] missingVariance,
            WaveMath.Waves[] atDepths,
            int[] levels,
            double foamJacobian) {

        /**
         * Work a wind's climate out.
         *
         * @param wind The wind.
         * @param seed What picks its phases and offsets.
         * @return The climate.
         */
        static Climate of(@NonNull Wind wind, long seed) {
            return of(
                    WaveMath.sample(wind.speed(), wind.fetch(), seed),
                    WaveMath.directionAmplitudes(wind.direction()),
                    seed,
                    wind.speed());
        }

        /**
         * Work a climate out from its waves.
         *
         * @param waves The waves.
         * @param amplitudes Each direction's amplitude.
         * @param seed What picks its directions' offsets.
         * @param windSpeed The wind that raised them, in meters a second, for their whitecaps; 0
         *     for none.
         * @return The climate.
         */
        static Climate of(
                @NonNull WaveMath.Waves waves, double[] amplitudes, long seed, double windSpeed) {
            final double[] offsets = new double[WaveMath.DIRECTIONS];
            for (int b = 0; b < offsets.length; ++b) {
                offsets[b] = WaveMath.PROFILE_LENGTH * WaveMath.unitHash(seed + WaveMath.WAVES, b);
            }
            final double[] missing = new double[WaveMath.LAYERS];
            for (int layer = 0; layer < missing.length; ++layer) {
                missing[layer] = WaveMath.missingSlopeVariance(waves, layer);
            }
            final WaveMath.Waves[] atDepths = new WaveMath.Waves[WaveMath.DEPTHS];
            for (int level = 0; level < atDepths.length; ++level) {
                atDepths[level] = WaveMath.atDepth(waves, WaveMath.DEPTH_LEVELS[level]);
            }
            return new Climate(
                    waves,
                    amplitudes,
                    offsets,
                    missing,
                    atDepths,
                    WaveMath.levelsUsed(atDepths),
                    WaveMath.foamJacobian(waves, windSpeed));
        }
    }

    /** The shader. */
    @NonNull private final ShaderVulkan shader;

    /** The shader that fills the wave profiles. */
    @NonNull private final ShaderVulkan wavesShader;

    /** The waves pass's pipeline layout and pipeline, VK_NULL_HANDLE until set up. */
    private long wavesLayout = VK_NULL_HANDLE;

    private long wavesPipeline = VK_NULL_HANDLE;

    /** Each frame in flight's climates, waves and profiles, or null before its first waves. */
    private final SharedBuffer[] climateBuffers =
            new SharedBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    private final SharedBuffer[] sampleBuffers =
            new SharedBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    private final SharedBuffer[] profileBuffers =
            new SharedBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** The winds the tiles' climates come from, by climate. */
    private final List<Wind> winds = new ArrayList<>();

    /**
     * The climates, in step with {@link #winds}, then the ripples' last, whose amplitudes come from
     * the wave grid.
     */
    private final List<Climate> climates = new ArrayList<>();

    /** The ripples disturbances start, as a climate. */
    private final Climate ripples;

    /** The wave amplitude grid around the camera. */
    @NonNull private final WaveGrid grid;

    /** This frame's disturbances, a scratch list. */
    private final List<WaterRegistry.Disturbance> disturbances = new ArrayList<>();

    /** This frame's wave paints, reused. */
    private final List<WaterRegistry.WavePaint> paints = new ArrayList<>();

    /** Each tile's climate, in step with {@link #tiles}, -1 for still water. */
    private final List<Integer> tileClimates = new ArrayList<>();

    /** When the stage first drew water, for the waves' time, or 0 before. */
    private long startNanos;

    /** Whether running out of climates was logged, so it is logged once. */
    private boolean climatesFullLogged;

    /** The scene stage, for its matrices' set. */
    @NonNull private final SceneRender sceneRender;

    /** The light stage, for its inputs' set. */
    @NonNull private final LightRender lightRender;

    /** The pipeline layout, VK_NULL_HANDLE until set up. */
    private long pipelineLayout = VK_NULL_HANDLE;

    /** The pipeline, VK_NULL_HANDLE until set up. */
    private long pipeline = VK_NULL_HANDLE;

    /** The gate's pipeline: the surface's depth alone, VK_NULL_HANDLE until set up. */
    private long gatePipeline = VK_NULL_HANDLE;

    /** The frame number {@link #prepare} last got ready, and what it worked out for it. */
    private long preparedFrame = -1;

    private int preparedWidth;
    private int preparedHeight;
    private double preparedSeconds;
    private float preparedPixelAngle;
    private final Vector3d preparedCamera = new Vector3d();

    /**
     * How many of each frame in flight's indices are the surfaces', drawn first; the sides' come
     * after, drawn over a copy of the scene with the surfaces, so water shows through them.
     */
    private final int[] surfaceIndexCounts = new int[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** Each frame in flight's surface vertices, or null before its first water. */
    private final SharedBuffer[] vertexBuffers =
            new SharedBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** Which version of the tiles each frame's vertices hold, -1 for none. */
    private final long[] vertexVersions = new long[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** Each frame in flight's surface indices, or null before its first water. */
    private final SharedBuffer[] indexBuffers =
            new SharedBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** How many indices each frame's buffer holds. */
    private final int[] indexCounts = new int[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** Each frame in flight's tile list, or null before its first water. */
    private final SharedBuffer[] tileBuffers =
            new SharedBuffer[GraphicsManager.MAX_FRAMES_IN_FLIGHT];

    /** The tiles, as of {@link #version}. */
    private final List<WaterTile> tiles = new ArrayList<>();

    /** Each tile's optics, in step with {@link #tiles}. */
    private final List<WaterMath.Optics> optics = new ArrayList<>();

    /** The version of the tiles drawn, -1 for none. */
    private long version = -1;

    /**
     * The tiles' version last taken from the registry to build, -1 for none, which may be newer
     * than {@link #version} while it builds.
     */
    private long seenVersion = -1;

    /** A scratch list the registry's tiles are copied into. */
    private final List<WaterTile> seenTiles = new ArrayList<>();

    /** The drawn tiles' surfaces and sides, built once for every frame in flight. */
    private WaterMesh.Mesh mesh;

    /** What each one meter cell of the drawn tiles holds, for the wave grid. */
    private WaterMesh.CellData cellData = WaterMesh.CellData.EMPTY;

    /** The build of the newest tiles under way, or null. Render thread. */
    private CompletableFuture<Built> building;

    /**
     * Builds the tiles' meshes and cell data off the render thread, so tiles coming and going, as
     * in a world streamed around the camera, don't stall frames; made on first use.
     */
    private ExecutorService builder;

    /**
     * The tiles' climates: each tile's, -1 for still water, and the winds they come from.
     *
     * @param tileClimates Each tile's climate.
     * @param winds The winds, by climate.
     * @param overflowed Whether more winds were found than {@link #MAX_CLIMATES}.
     */
    private record Climates(
            @NonNull List<Integer> tileClimates, @NonNull List<Wind> winds, boolean overflowed) {}

    /**
     * A version of the tiles, built and ready to draw.
     *
     * @param version The tiles' version.
     * @param tiles The tiles.
     * @param climates Their climates.
     * @param mesh Their surfaces and sides.
     * @param cells What each one meter cell they cover holds.
     */
    private record Built(
            long version,
            @NonNull List<WaterTile> tiles,
            @NonNull Climates climates,
            WaterMesh.@NonNull Mesh mesh,
            WaterMesh.@NonNull CellData cells) {}

    /**
     * Set up the stage.
     *
     * @param shader The water shader.
     * @param wavesShader The compute shader that fills the wave profiles.
     * @param waveletsShader The compute shader that steps the wave amplitude grid.
     * @param sceneRender The scene stage, whose matrices' set the shader reads.
     * @param lightRender The light stage, whose inputs' set the shader reads.
     */
    public WaterRender(
            @NonNull ShaderVulkan shader,
            @NonNull ShaderVulkan wavesShader,
            @NonNull ShaderVulkan waveletsShader,
            @NonNull SceneRender sceneRender,
            @NonNull LightRender lightRender) {
        this.shader = shader;
        this.wavesShader = wavesShader;
        grid = new WaveGrid(waveletsShader);
        final double[] everyWay = new double[WaveMath.DIRECTIONS];
        Arrays.fill(everyWay, 1);
        ripples = Climate.of(WaveletMath.ripples(RIPPLE_SEED), everyWay, RIPPLE_SEED, 0);
        climates.add(ripples);
        this.sceneRender = sceneRender;
        this.lightRender = lightRender;
        Arrays.fill(vertexVersions, -1);
    }

    @Override
    public void initialize(@NonNull VulkanState state) {
        log.debug("Initializing water render");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            VkPushConstantRange.Buffer ranges = VkPushConstantRange.calloc(1, stack);
            ranges.get(0)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                    .offset(0)
                    .size(ShaderBindings.Water.PUSH_CONSTANTS_SIZE);
            final long[] setLayouts = {
                lightRender.getDescriptorSetLayout(),
                state.bindlessTextures.getDescriptorSetLayout(),
                sceneRender.getDescriptorSetLayout(),
                state.environment.getSetLayout(),
                state.fogVolume.getReadSetLayout()
            };
            checkError(
                    vkCreatePipelineLayout(
                            state.device.logical,
                            VkPipelineLayoutCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .pSetLayouts(stack.longs(setLayouts))
                                    .pPushConstantRanges(ranges),
                            null,
                            longOutput));
            pipelineLayout = longOutput.get(0);
            pipeline = createPipeline(state, stack, false);
            gatePipeline = createPipeline(state, stack, true);

            VkPushConstantRange.Buffer wavesRanges = VkPushConstantRange.calloc(1, stack);
            wavesRanges
                    .get(0)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                    .offset(0)
                    .size(ShaderBindings.Water.WAVES_CONSTANTS_SIZE);
            checkError(
                    vkCreatePipelineLayout(
                            state.device.logical,
                            VkPipelineLayoutCreateInfo.calloc(stack)
                                    .sType$Default()
                                    .pPushConstantRanges(wavesRanges),
                            null,
                            longOutput));
            wavesLayout = longOutput.get(0);
            VkComputePipelineCreateInfo.Buffer infos = VkComputePipelineCreateInfo.calloc(1, stack);
            infos.get(0).sType$Default().stage(wavesShader.shaderStages.get(0)).layout(wavesLayout);
            checkError(
                    vkCreateComputePipelines(
                            state.device.logical, VK_NULL_HANDLE, infos, null, longOutput));
            wavesPipeline = longOutput.get(0);
        }
        grid.initialize(state);
    }

    @Override
    public void cleanup(@NonNull VulkanState state) {
        for (SharedBuffer[] buffers :
                new SharedBuffer[][] {
                    vertexBuffers,
                    indexBuffers,
                    tileBuffers,
                    climateBuffers,
                    sampleBuffers,
                    profileBuffers
                }) {
            for (int i = 0; i < buffers.length; ++i) {
                if (buffers[i] != null) {
                    SharedBuffer.free(buffers[i], state);
                    buffers[i] = null;
                }
            }
        }
        Arrays.fill(vertexVersions, -1);
        if (builder != null) {
            builder.shutdownNow();
            builder = null;
        }
        building = null;
        seenVersion = version;
        vkDestroyPipeline(state.device.logical, pipeline, null);
        pipeline = VK_NULL_HANDLE;
        vkDestroyPipeline(state.device.logical, gatePipeline, null);
        gatePipeline = VK_NULL_HANDLE;
        vkDestroyPipelineLayout(state.device.logical, pipelineLayout, null);
        pipelineLayout = VK_NULL_HANDLE;
        grid.cleanup(state);
        vkDestroyPipeline(state.device.logical, wavesPipeline, null);
        wavesPipeline = VK_NULL_HANDLE;
        vkDestroyPipelineLayout(state.device.logical, wavesLayout, null);
        wavesLayout = VK_NULL_HANDLE;
    }

    @Override
    public void render(
            Scene scene,
            @NonNull SceneTarget sceneTarget,
            @NonNull VulkanState state,
            RenderConfig renderConfig) {
        final int frame = state.frameIndex;
        final PerFrameData frameData = state.perFrameData[frame];
        if (preparedFrame != state.frameNumber) {
            return;
        }
        final int width = preparedWidth;
        final int height = preparedHeight;
        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[frame];
        final TextureInfoVulkan color = frameData.sceneColor;
        final TextureInfoVulkan depth = frameData.gBuffer.depth();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // What lies behind the water is drawn: the gate opens for what is in front of it
            clearGate(commandBuffer, frameData, width, height, stack);
            copyBehind(commandBuffer, frameData, width, height, false, stack);

            VkRenderingAttachmentInfo.Buffer colorAttachments =
                    VkRenderingAttachmentInfo.calloc(1, stack);
            colorAttachments
                    .get(0)
                    .sType$Default()
                    .imageView(color.view)
                    .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            VkRenderingAttachmentInfo depthAttachment =
                    VkRenderingAttachmentInfo.calloc(stack)
                            .sType$Default()
                            .imageView(depth.view)
                            .imageLayout(VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL)
                            .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD)
                            .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            vkCmdBeginRendering(
                    commandBuffer,
                    VkRenderingInfo.calloc(stack)
                            .sType$Default()
                            .renderArea(area -> area.extent().set(width, height))
                            .layerCount(1)
                            .pColorAttachments(colorAttachments)
                            .pDepthAttachment(depthAttachment));
            TranslucentRender.setViewport(commandBuffer, width, height, stack);
            bindSurface(commandBuffer, state, frameData, pipeline, stack);
            // The surfaces: from above, water only ever has its own water under it
            vkCmdDrawIndexed(commandBuffer, surfaceIndexCounts[frame], 1, 0, 0, 0);
            vkCmdEndRendering(commandBuffer);
            final int sides = indexCounts[frame] - surfaceIndexCounts[frame];
            if (sides > 0) {
                // The sides: over a copy of the scene with the surfaces drawn, so a side shows the
                // water behind it, the sea, a pool below, as well as the ground and the sky
                copyBehind(commandBuffer, frameData, width, height, true, stack);
                vkCmdBeginRendering(
                        commandBuffer,
                        VkRenderingInfo.calloc(stack)
                                .sType$Default()
                                .renderArea(area -> area.extent().set(width, height))
                                .layerCount(1)
                                .pColorAttachments(colorAttachments)
                                .pDepthAttachment(depthAttachment));
                TranslucentRender.setViewport(commandBuffer, width, height, stack);
                bindSurface(commandBuffer, state, frameData, pipeline, stack);
                vkCmdDrawIndexed(commandBuffer, sides, 1, surfaceIndexCounts[frame], 0, 0);
                vkCmdEndRendering(commandBuffer);
            }

            // Read only again, as the transparency passes and filters expect it
            VkImageMemoryBarrier2.Buffer barriers = VkImageMemoryBarrier2.calloc(1, stack);
            SceneRender.imageBarrier(
                    barriers.get(0),
                    depth.texture,
                    VK_IMAGE_ASPECT_DEPTH_BIT,
                    VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT
                            | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                    VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT
                            | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT
                            | VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT
                            | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                            | VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                    VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL,
                    VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL);
            vkCmdPipelineBarrier2(
                    commandBuffer,
                    VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barriers));
        }
    }

    /**
     * Get the frame ready to draw the water: its tiles, vertices, climates and waves, and the wave
     * grid's step. Records the profiles' fill and the step.
     *
     * @param scene The scene.
     * @param sceneTarget What the scene is drawn into.
     * @param state The Vulkan state.
     * @param renderConfig What is drawn.
     * @return Whether there is water to draw.
     */
    private boolean prepare(
            Scene scene,
            @NonNull SceneTarget sceneTarget,
            @NonNull VulkanState state,
            RenderConfig renderConfig) {
        if (scene == null || renderConfig == null || !renderConfig.world()) {
            return false;
        }
        final long now = scene.getWaterRegistry().snapshotIfChanged(seenVersion, seenTiles);
        if (now != seenVersion && building == null) {
            seenVersion = now;
            final List<WaterTile> snapshot = List.copyOf(seenTiles);
            if (builder == null) {
                builder =
                        Executors.newSingleThreadExecutor(
                                runnable -> {
                                    final Thread thread = new Thread(runnable, "Water mesh");
                                    thread.setDaemon(true);
                                    return thread;
                                });
            }
            building = CompletableFuture.supplyAsync(() -> build(now, snapshot), builder);
        }
        // The first tiles are waited for, so water shows from the first frame; later ones are
        // swapped in once built, the last ones drawn meanwhile
        if (building != null && (building.isDone() || mesh == null)) {
            final CompletableFuture<Built> done = building;
            building = null;
            try {
                apply(done.join());
            } catch (CompletionException e) {
                log.error("Building the water's surfaces failed", e.getCause());
            }
        }
        if (tiles.isEmpty()) {
            return false;
        }
        final int frame = state.frameIndex;
        if (vertexVersions[frame] != version) {
            writeVertices(state, frame);
            vertexVersions[frame] = version;
        }
        final int width = Math.min(sceneTarget.width(), state.realSize.width());
        final int height = Math.min(sceneTarget.height(), state.realSize.height());
        if (indexCounts[frame] == 0 || width <= 0 || height <= 0) {
            return false;
        }
        final long nanos = System.nanoTime();
        if (startNanos == 0) {
            startNanos = nanos;
        }
        final double seconds = (nanos - startNanos) / NANOS_PER_SECOND;
        writeTiles(state, frame, scene.getCamera().getPosition());
        writeClimates(state, frame, scene.getCamera().getPosition(), seconds);
        final float pixelAngle =
                2 / (Math.abs(scene.getProjection().getProjectionMatrix().m11()) * height);

        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[frame];
        scene.getWaterRegistry().takeDisturbances(WaveletMath.MAX_DISTURBANCES, disturbances);
        scene.getWaterRegistry().takePaints(WaveletMath.MAX_PAINTS, paints);
        final Vector3dc camera = scene.getCamera().getPosition();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // The profiles first: the grid's step reads them for where crests break
            fillProfiles(commandBuffer, frame, stack);
            grid.step(
                    commandBuffer,
                    state,
                    frame,
                    camera,
                    version,
                    () -> cellData,
                    disturbances,
                    climateBuffers[frame].deviceAddress,
                    paints,
                    profileBuffers[frame].deviceAddress,
                    state.particlePool,
                    stack);
        }
        preparedFrame = state.frameNumber;
        preparedWidth = width;
        preparedHeight = height;
        preparedSeconds = seconds;
        preparedPixelAngle = pixelAngle;
        preparedCamera.set(camera);
        return true;
    }

    /**
     * The push constants the surface's shaders share, for this frame.
     *
     * @param state The Vulkan state.
     * @param frameData The frame's images.
     * @param stack The stack to allocate on.
     * @return The constants.
     */
    private ByteBuffer constants(
            @NonNull VulkanState state,
            @NonNull PerFrameData frameData,
            @NonNull MemoryStack stack) {
        final int frame = state.frameIndex;
        ByteBuffer constants = stack.calloc(ShaderBindings.Water.PUSH_CONSTANTS_SIZE);
        constants.putLong(
                ShaderBindings.Water.PUSH_CONSTANT_TILES_OFFSET, tileBuffers[frame].deviceAddress);
        constants.putLong(
                ShaderBindings.Water.PUSH_CONSTANT_CLIMATES_OFFSET,
                climateBuffers[frame].deviceAddress);
        constants.putLong(
                ShaderBindings.Water.PUSH_CONSTANT_PROFILES_OFFSET,
                profileBuffers[frame].deviceAddress);
        constants.putFloat(
                ShaderBindings.Water.PUSH_CONSTANT_PIXEL_ANGLE_OFFSET, preparedPixelAngle);
        constants.putLong(ShaderBindings.Water.PUSH_CONSTANT_GRID_OFFSET, grid.currentAddress());
        constants.putFloat(
                ShaderBindings.Water.PUSH_CONSTANT_GRID_CORNER_OFFSET,
                (float) (grid.getCornerX() * WaveletMath.CELL_SIZE - preparedCamera.x()));
        constants.putFloat(
                ShaderBindings.Water.PUSH_CONSTANT_GRID_CORNER_OFFSET + Float.BYTES,
                (float) (grid.getCornerZ() * WaveletMath.CELL_SIZE - preparedCamera.z()));
        constants.putInt(ShaderBindings.Water.PUSH_CONSTANT_GRID_CELL_OFFSET, grid.getCornerX());
        constants.putInt(
                ShaderBindings.Water.PUSH_CONSTANT_GRID_CELL_OFFSET + Integer.BYTES,
                grid.getCornerZ());
        constants.putInt(
                ShaderBindings.Water.PUSH_CONSTANT_RIPPLE_CLIMATE_OFFSET, climates.size() - 1);
        constants.putInt(ShaderBindings.Water.PUSH_CONSTANT_GRID_ON_OFFSET, 1);
        constants.putFloat(
                ShaderBindings.Water.PUSH_CONSTANT_WAVE_TIME_OFFSET,
                (float) (preparedSeconds % WaveMath.FLOW_CYCLE));
        constants.putInt(ShaderBindings.Water.PUSH_CONSTANT_FRAME_OFFSET, (int) state.frameNumber);
        constants.putFloat(
                ShaderBindings.Water.PUSH_CONSTANT_SHEET_TIME_OFFSET,
                (float) (preparedSeconds % WaveMath.SHEET_TIME_PERIOD));
        constants.putFloat(
                ShaderBindings.Water.PUSH_CONSTANT_CAMERA_WRAP_OFFSET,
                (float)
                        (preparedCamera.x()
                                - WaveMath.PATTERN_WRAP
                                        * Math.floor(preparedCamera.x() / WaveMath.PATTERN_WRAP)));
        constants.putFloat(
                ShaderBindings.Water.PUSH_CONSTANT_CAMERA_WRAP_OFFSET + Float.BYTES,
                (float)
                        (preparedCamera.z()
                                - WaveMath.PATTERN_WRAP
                                        * Math.floor(preparedCamera.z() / WaveMath.PATTERN_WRAP)));
        constants.putInt(
                ShaderBindings.Water.PUSH_CONSTANT_UNDER_WATER_OFFSET,
                WaterVolume.isUnderWater(tiles, preparedCamera) ? 1 : 0);
        constants.putFloat(ShaderBindings.Water.PUSH_CONSTANT_SCREEN_SIZE_OFFSET, preparedWidth);
        constants.putFloat(
                ShaderBindings.Water.PUSH_CONSTANT_SCREEN_SIZE_OFFSET + Float.BYTES,
                preparedHeight);
        constants.putInt(
                ShaderBindings.Water.PUSH_CONSTANT_BEHIND_COLOR_OFFSET,
                frameData.waterBehindColor.bindlessIndex);
        constants.putInt(
                ShaderBindings.Water.PUSH_CONSTANT_BEHIND_DEPTH_OFFSET,
                frameData.waterBehindDepth.bindlessIndex);
        constants.putFloat(ShaderBindings.Water.PUSH_CONSTANT_ROUGHNESS_OFFSET, CALM_ROUGHNESS);
        return constants;
    }

    /**
     * Bind what the surface's pipelines read: their sets and constants, vertices and indices.
     *
     * @param commandBuffer The command buffer.
     * @param state The Vulkan state.
     * @param frameData The frame's images.
     * @param layerPipeline The pipeline.
     * @param stack The stack to allocate on.
     */
    private void bindSurface(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull VulkanState state,
            @NonNull PerFrameData frameData,
            long layerPipeline,
            @NonNull MemoryStack stack) {
        final int frame = state.frameIndex;
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, layerPipeline);
        vkCmdBindDescriptorSets(
                commandBuffer,
                VK_PIPELINE_BIND_POINT_GRAPHICS,
                pipelineLayout,
                0,
                stack.longs(
                        lightRender.getDescriptorSet(frame),
                        state.bindlessTextures.getDescriptorSet(),
                        sceneRender.getDescriptorSet(frame),
                        state.environment.getSet(),
                        state.fogVolume.getReadSet()),
                null);
        vkCmdPushConstants(
                commandBuffer,
                pipelineLayout,
                VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT,
                0,
                constants(state, frameData, stack));
        vkCmdBindVertexBuffers(
                commandBuffer, 0, stack.longs(vertexBuffers[frame].buffer), stack.longs(0));
        vkCmdBindIndexBuffer(commandBuffer, indexBuffers[frame].buffer, 0, VK_INDEX_TYPE_UINT32);
    }

    /**
     * Draw the gate, see {@link PerFrameData#waterGate}: the water's depth when there is water,
     * else 0, then read only for the transparent stages.
     *
     * @param scene The scene.
     * @param sceneTarget What the scene is drawn into.
     * @param state The Vulkan state.
     * @param renderConfig What is drawn.
     */
    private void drawGate(
            Scene scene,
            @NonNull SceneTarget sceneTarget,
            @NonNull VulkanState state,
            RenderConfig renderConfig) {
        final PerFrameData frameData = state.perFrameData[state.frameIndex];
        final boolean water = prepare(scene, sceneTarget, state, renderConfig);
        frameData.waterGated = water;
        final int width =
                water ? preparedWidth : Math.min(sceneTarget.width(), state.realSize.width());
        final int height =
                water ? preparedHeight : Math.min(sceneTarget.height(), state.realSize.height());
        final VkCommandBuffer commandBuffer = state.commandBuffersGraphics[state.frameIndex];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            gateBarrier(commandBuffer, frameData, true, stack);
            VkRenderingAttachmentInfo depthAttachment =
                    VkRenderingAttachmentInfo.calloc(stack)
                            .sType$Default()
                            .imageView(frameData.waterGate.view)
                            .imageLayout(VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL)
                            .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                            .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            depthAttachment.clearValue().depthStencil().depth(water ? 1 : 0);
            vkCmdBeginRendering(
                    commandBuffer,
                    VkRenderingInfo.calloc(stack)
                            .sType$Default()
                            .renderArea(
                                    area ->
                                            area.extent()
                                                    .set(Math.max(width, 1), Math.max(height, 1)))
                            .layerCount(1)
                            .pDepthAttachment(depthAttachment));
            if (water) {
                TranslucentRender.setViewport(commandBuffer, width, height, stack);
                bindSurface(commandBuffer, state, frameData, gatePipeline, stack);
                vkCmdDrawIndexed(commandBuffer, indexCounts[state.frameIndex], 1, 0, 0, 0);
            }
            vkCmdEndRendering(commandBuffer);
            gateBarrier(commandBuffer, frameData, false, stack);
        }
    }

    /**
     * Clear the gate to 0, so the transparent stages draw everything in front of the water.
     *
     * @param commandBuffer The command buffer.
     * @param frameData The frame's images.
     * @param width The drawn width, in pixels.
     * @param height The drawn height.
     * @param stack The stack to allocate on.
     */
    private static void clearGate(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull PerFrameData frameData,
            int width,
            int height,
            @NonNull MemoryStack stack) {
        gateBarrier(commandBuffer, frameData, true, stack);
        VkRenderingAttachmentInfo depthAttachment =
                VkRenderingAttachmentInfo.calloc(stack)
                        .sType$Default()
                        .imageView(frameData.waterGate.view)
                        .imageLayout(VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL)
                        .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                        .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
        depthAttachment.clearValue().depthStencil().depth(0);
        vkCmdBeginRendering(
                commandBuffer,
                VkRenderingInfo.calloc(stack)
                        .sType$Default()
                        .renderArea(area -> area.extent().set(width, height))
                        .layerCount(1)
                        .pDepthAttachment(depthAttachment));
        vkCmdEndRendering(commandBuffer);
        gateBarrier(commandBuffer, frameData, false, stack);
    }

    /**
     * Move the gate between being drawn and being read by the transparent stages.
     *
     * @param commandBuffer The command buffer.
     * @param frameData The frame's images.
     * @param toDraw Whether it is about to be drawn, rather than read.
     * @param stack The stack to allocate on.
     */
    private static void gateBarrier(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull PerFrameData frameData,
            boolean toDraw,
            @NonNull MemoryStack stack) {
        final long tests =
                VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT
                        | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT;
        final long attachment =
                VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                        | VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
        VkImageMemoryBarrier2.Buffer barrier = VkImageMemoryBarrier2.calloc(1, stack);
        if (toDraw) {
            // Whatever it held before: last frame's, or what the transparent stages read
            SceneRender.imageBarrier(
                    barrier.get(0),
                    frameData.waterGate.texture,
                    VK_IMAGE_ASPECT_DEPTH_BIT,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                    VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                    tests,
                    attachment,
                    VK_IMAGE_LAYOUT_UNDEFINED,
                    VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL);
        } else {
            SceneRender.imageBarrier(
                    barrier.get(0),
                    frameData.waterGate.texture,
                    VK_IMAGE_ASPECT_DEPTH_BIT,
                    tests,
                    VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                    VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                    VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL,
                    VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL);
        }
        vkCmdPipelineBarrier2(
                commandBuffer,
                VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barrier));
    }

    /**
     * The stage that draws the water's depth into the gate, see {@link PerFrameData#waterGate},
     * before the transparent stages draw what lies behind the water: it gets the frame ready for
     * the water stage too.
     *
     * @return The stage.
     */
    public RenderStage getGate() {
        return gate;
    }

    /** See {@link #getGate}. */
    private final RenderStage gate =
            new RenderStage() {
                @Override
                public void render(
                        Scene scene,
                        @NonNull SceneTarget sceneTarget,
                        @NonNull VulkanState state,
                        RenderConfig renderConfig) {
                    drawGate(scene, sceneTarget, state, renderConfig);
                }

                @Override
                public String name() {
                    return "WaterGate";
                }
            };

    /**
     * Build a version of the tiles: their climates, surfaces and cell data. On the water mesh
     * thread.
     *
     * @param version The tiles' version.
     * @param list The tiles.
     * @return The build.
     */
    private static Built build(long version, @NonNull List<WaterTile> list) {
        final Climates found = findClimates(list);
        return new Built(
                version,
                list,
                found,
                WaterMesh.build(list),
                WaterMesh.waterCellData(list, found.tileClimates()));
    }

    /**
     * Draw a built version of the tiles from now on. Render thread.
     *
     * @param built The build.
     */
    private void apply(@NonNull Built built) {
        version = built.version();
        tiles.clear();
        tiles.addAll(built.tiles());
        optics.clear();
        for (WaterTile tile : tiles) {
            optics.add(WaterMath.optics(tile.body()));
        }
        mesh = built.mesh();
        cellData = built.cells();
        useClimates(built.climates());
    }

    /**
     * Find the winds the tiles' waves come from, one climate each, and which each tile has. Still
     * water has none, and past {@link #MAX_CLIMATES} winds tiles are drawn still.
     *
     * @param list The tiles.
     * @return The climates.
     */
    private static Climates findClimates(@NonNull List<WaterTile> list) {
        final List<Wind> found = new ArrayList<>();
        final List<Integer> each = new ArrayList<>(list.size());
        boolean overflowed = false;
        for (WaterTile tile : list) {
            if (tile.body().windSpeed() <= 0) {
                each.add(-1);
                continue;
            }
            final Wind wind =
                    new Wind(
                            tile.body().windSpeed(),
                            tile.body().windDirection(),
                            tile.body().fetch());
            int index = found.indexOf(wind);
            if (index < 0 && found.size() < MAX_CLIMATES) {
                found.add(wind);
                index = found.size() - 1;
            } else if (index < 0) {
                overflowed = true;
            }
            each.add(index);
        }
        return new Climates(each, found, overflowed);
    }

    /**
     * Use the drawn tiles' climates, making the winds' climates again if the winds changed.
     *
     * @param found The climates.
     */
    private void useClimates(@NonNull Climates found) {
        if (found.overflowed() && !climatesFullLogged) {
            climatesFullLogged = true;
            log.warn("More than {} winds over water; the rest is drawn still", MAX_CLIMATES);
        }
        tileClimates.clear();
        tileClimates.addAll(found.tileClimates());
        if (!found.winds().equals(winds)) {
            winds.clear();
            winds.addAll(found.winds());
            climates.clear();
            for (int i = 0; i < winds.size(); ++i) {
                climates.add(Climate.of(winds.get(i), WAVE_SEED + i * (long) WaveMath.WAVES));
            }
            climates.add(ripples);
        }
    }

    /**
     * Write this frame's climates and their waves' phases now: each direction's amplitude and where
     * the camera stands along its profile, worked out in doubles so it stays precise far out.
     *
     * @param state The Vulkan state.
     * @param frame The frame in flight.
     * @param camera The camera's position.
     * @param seconds The waves' time.
     */
    private void writeClimates(
            @NonNull VulkanState state, int frame, @NonNull Vector3dc camera, double seconds) {
        final int count = Math.max(1, climates.size());
        climateBuffers[frame] =
                ensure(
                        state,
                        climateBuffers[frame],
                        (long) count * ShaderBindings.Water.CLIMATE_SIZE,
                        false);
        sampleBuffers[frame] =
                ensure(
                        state,
                        sampleBuffers[frame],
                        (long) count
                                * WaveMath.DEPTHS
                                * WaveMath.WAVES
                                * ShaderBindings.Water.WAVE_SAMPLE_SIZE,
                        false);
        profileBuffers[frame] =
                ensure(
                        state,
                        profileBuffers[frame],
                        (long) count
                                * WaveMath.DEPTHS
                                * WaveMath.DIRECTIONS
                                * WaveMath.LAYERS
                                * WaveMath.PROFILE_SAMPLES
                                * ShaderBindings.Water.PROFILE_SAMPLE_SIZE,
                        true);
        final long climateAddress = climateBuffers[frame].allocationInfo.pMappedData();
        final long sampleAddress = sampleBuffers[frame].allocationInfo.pMappedData();
        MemoryUtil.memSet(
                sampleAddress,
                0,
                (long) count * WaveMath.WAVES * ShaderBindings.Water.WAVE_SAMPLE_SIZE);
        for (int c = 0; c < climates.size(); ++c) {
            final Climate climate = climates.get(c);
            final long at = climateAddress + (long) c * ShaderBindings.Water.CLIMATE_SIZE;
            for (int b = 0; b < WaveMath.DIRECTIONS; ++b) {
                final double angle = 2 * Math.PI * b / WaveMath.DIRECTIONS;
                final double along =
                        camera.x() * Math.cos(angle)
                                + camera.z() * Math.sin(angle)
                                + climate.offsets()[b];
                final double wrapped =
                        along
                                - WaveMath.PROFILE_LENGTH
                                        * Math.floor(along / WaveMath.PROFILE_LENGTH);
                MemoryUtil.memPutFloat(
                        at + ShaderBindings.Water.CLIMATE_AMPLITUDES + (long) b * Float.BYTES,
                        (float) climate.amplitudes()[b]);
                MemoryUtil.memPutFloat(
                        at + ShaderBindings.Water.CLIMATE_OFFSETS + (long) b * Float.BYTES,
                        (float) wrapped);
            }
            for (int layer = 0; layer < WaveMath.LAYERS; ++layer) {
                MemoryUtil.memPutFloat(
                        at
                                + ShaderBindings.Water.CLIMATE_MISSING_VARIANCE
                                + (long) layer * Float.BYTES,
                        (float) climate.missingVariance()[layer]);
            }
            double peak = 0;
            for (double amplitude : climate.amplitudes()) {
                peak = Math.max(peak, amplitude);
            }
            MemoryUtil.memPutFloat(at + ShaderBindings.Water.CLIMATE_PEAK, (float) peak);
            MemoryUtil.memPutFloat(
                    at + ShaderBindings.Water.CLIMATE_FOAM_JACOBIAN,
                    (float) climate.foamJacobian());
            MemoryUtil.memPutFloat(
                    at + ShaderBindings.Water.CLIMATE_JACOBIAN_VARIANCE,
                    (float) WaveMath.jacobianVariance(climate.waves(), 0));
            MemoryUtil.memPutFloat(
                    at + ShaderBindings.Water.CLIMATE_JACOBIAN_VARIANCE + Float.BYTES,
                    (float)
                            WaveMath.jacobianVariance(
                                    climate.waves(), WaveMath.LAYER_CUTOFFS[WaveMath.LAYERS - 1]));
            for (int level = 0; level < WaveMath.DEPTHS; ++level) {
                MemoryUtil.memPutInt(
                        at + ShaderBindings.Water.CLIMATE_LEVELS + (long) level * Integer.BYTES,
                        climate.levels()[level]);
                if (climate.levels()[level] != level) {
                    continue;
                }
                final WaveMath.Waves waves = climate.atDepths()[level];
                for (int i = 0; i < waves.wavenumbers().length; ++i) {
                    final long sample =
                            sampleAddress
                                    + (((long) c * WaveMath.DEPTHS + level) * WaveMath.WAVES + i)
                                            * ShaderBindings.Water.WAVE_SAMPLE_SIZE;
                    final double k = waves.wavenumbers()[i];
                    MemoryUtil.memPutFloat(sample, (float) k);
                    MemoryUtil.memPutFloat(sample + Float.BYTES, (float) waves.amplitudes()[i]);
                    MemoryUtil.memPutFloat(
                            sample + 2 * Float.BYTES, (float) WaveMath.phaseAt(waves, i, seconds));
                    MemoryUtil.memPutFloat(sample + 3 * Float.BYTES, (float) (2 * Math.PI / k));
                }
            }
        }
    }

    /**
     * Fill this frame's climates' profiles, for the surface to read.
     *
     * @param commandBuffer The command buffer.
     * @param frame The frame in flight.
     * @param stack The stack to allocate on.
     */
    private void fillProfiles(
            @NonNull VkCommandBuffer commandBuffer, int frame, @NonNull MemoryStack stack) {
        if (climates.isEmpty()) {
            return;
        }
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, wavesPipeline);
        ByteBuffer constants = stack.calloc(ShaderBindings.Water.WAVES_CONSTANTS_SIZE);
        constants.putLong(
                ShaderBindings.Water.WAVES_SAMPLES_OFFSET, sampleBuffers[frame].deviceAddress);
        constants.putLong(
                ShaderBindings.Water.WAVES_PROFILES_OFFSET, profileBuffers[frame].deviceAddress);
        constants.putLong(
                ShaderBindings.Water.WAVES_CLIMATES_OFFSET, climateBuffers[frame].deviceAddress);
        constants.putInt(ShaderBindings.Water.WAVES_COUNT_OFFSET, WaveMath.WAVES);
        vkCmdPushConstants(commandBuffer, wavesLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, constants);
        vkCmdDispatch(
                commandBuffer,
                WaveMath.PROFILE_SAMPLES / WaveMath.WORKGROUP_SIZE,
                climates.size() * WaveMath.DEPTHS * WaveMath.DIRECTIONS,
                1);
        VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack);
        barrier.get(0)
                .sType$Default()
                .srcStageMask(VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT)
                .srcAccessMask(VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT)
                .dstStageMask(
                        VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT
                                | VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT
                                | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT)
                .dstAccessMask(VK_ACCESS_2_SHADER_STORAGE_READ_BIT);
        vkCmdPipelineBarrier2(
                commandBuffer,
                VkDependencyInfo.calloc(stack).sType$Default().pMemoryBarriers(barrier));
    }

    /**
     * A buffer of at least a size, made or grown as needed.
     *
     * @param state The Vulkan state.
     * @param buffer The buffer so far, or null.
     * @param bytes The size needed.
     * @param deviceLocal Whether only the GPU uses it.
     * @return The buffer.
     */
    private static SharedBuffer ensure(
            @NonNull VulkanState state, SharedBuffer buffer, long bytes, boolean deviceLocal) {
        if (buffer == null) {
            return deviceLocal
                    ? SharedBuffer.allocateDeviceLocal(
                            bytes, state, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT)
                    : SharedBuffer.allocate(bytes, state, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        }
        buffer.ensureCapacity(bytes, state);
        return buffer;
    }

    /**
     * Copy the scene's color and depth, as the light and skybox stages left them, for the water to
     * see through, and leave the scene's color ready to draw on and its depth to write.
     *
     * @param commandBuffer The command buffer.
     * @param frameData This frame's data.
     * @param width The width drawn, in pixels.
     * @param height The height drawn, in pixels.
     * @param depthWritten Whether the water has just written the depth, as an attachment, rather
     *     than it being read only.
     * @param stack The stack to allocate on.
     */
    private static void copyBehind(
            @NonNull VkCommandBuffer commandBuffer,
            @NonNull PerFrameData frameData,
            int width,
            int height,
            boolean depthWritten,
            @NonNull MemoryStack stack) {
        final TextureInfoVulkan color = frameData.sceneColor;
        final TextureInfoVulkan depth = frameData.gBuffer.depth();
        final TextureInfoVulkan behindColor = frameData.waterBehindColor;
        final TextureInfoVulkan behindDepth = frameData.waterBehindDepth;
        VkImageMemoryBarrier2.Buffer before = VkImageMemoryBarrier2.calloc(4, stack);
        SceneRender.imageBarrier(
                before.get(0),
                color.texture,
                VK_IMAGE_ASPECT_COLOR_BIT,
                VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                VK_PIPELINE_STAGE_2_COPY_BIT,
                VK_ACCESS_2_TRANSFER_READ_BIT,
                VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
        SceneRender.imageBarrier(
                before.get(1),
                depth.texture,
                VK_IMAGE_ASPECT_DEPTH_BIT,
                VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT
                        | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT
                        | VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT
                        | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                depthWritten ? VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT : VK_ACCESS_2_NONE,
                VK_PIPELINE_STAGE_2_COPY_BIT,
                VK_ACCESS_2_TRANSFER_READ_BIT,
                depthWritten
                        ? VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL
                        : VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL,
                VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
        for (int i = 0; i < 2; ++i) {
            SceneRender.imageBarrier(
                    before.get(2 + i),
                    i == 0 ? behindColor.texture : behindDepth.texture,
                    i == 0 ? VK_IMAGE_ASPECT_COLOR_BIT : VK_IMAGE_ASPECT_DEPTH_BIT,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                    VK_ACCESS_2_NONE,
                    VK_PIPELINE_STAGE_2_COPY_BIT,
                    VK_ACCESS_2_TRANSFER_WRITE_BIT,
                    VK_IMAGE_LAYOUT_UNDEFINED,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
        }
        vkCmdPipelineBarrier2(
                commandBuffer,
                VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(before));

        for (int i = 0; i < 2; ++i) {
            final int aspect = i == 0 ? VK_IMAGE_ASPECT_COLOR_BIT : VK_IMAGE_ASPECT_DEPTH_BIT;
            VkImageCopy.Buffer region = VkImageCopy.calloc(1, stack);
            region.get(0).srcSubresource(layers -> layers.aspectMask(aspect).layerCount(1));
            region.get(0).dstSubresource(layers -> layers.aspectMask(aspect).layerCount(1));
            region.get(0).extent().set(width, height, 1);
            vkCmdCopyImage(
                    commandBuffer,
                    i == 0 ? color.texture : depth.texture,
                    VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    i == 0 ? behindColor.texture : behindDepth.texture,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    region);
        }

        VkImageMemoryBarrier2.Buffer after = VkImageMemoryBarrier2.calloc(4, stack);
        SceneRender.imageBarrier(
                after.get(0),
                color.texture,
                VK_IMAGE_ASPECT_COLOR_BIT,
                VK_PIPELINE_STAGE_2_COPY_BIT,
                VK_ACCESS_2_NONE,
                VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        SceneRender.imageBarrier(
                after.get(1),
                depth.texture,
                VK_IMAGE_ASPECT_DEPTH_BIT,
                VK_PIPELINE_STAGE_2_COPY_BIT,
                VK_ACCESS_2_NONE,
                VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT
                        | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                        | VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL);
        for (int i = 0; i < 2; ++i) {
            SceneRender.imageBarrier(
                    after.get(2 + i),
                    i == 0 ? behindColor.texture : behindDepth.texture,
                    i == 0 ? VK_IMAGE_ASPECT_COLOR_BIT : VK_IMAGE_ASPECT_DEPTH_BIT,
                    VK_PIPELINE_STAGE_2_COPY_BIT,
                    VK_ACCESS_2_TRANSFER_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                    VK_ACCESS_2_SHADER_SAMPLED_READ_BIT,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL);
        }
        vkCmdPipelineBarrier2(
                commandBuffer,
                VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(after));
    }

    /**
     * Copy the drawn tiles' surfaces into this frame's buffers: two triangles for each cell that
     * holds water, each corner at its height, relative to its tile's corner, built once for every
     * frame in flight, see {@link #build}.
     *
     * @param state The Vulkan state.
     * @param frame The frame in flight.
     */
    private void writeVertices(@NonNull VulkanState state, int frame) {
        indexCounts[frame] = mesh.indexCount();
        surfaceIndexCounts[frame] = mesh.surfaceIndexCount();
        if (mesh.indexCount() == 0) {
            return;
        }
        final long bytes = (long) mesh.count() * ShaderBindings.Water.VERTEX_SIZE;
        SharedBuffer buffer = vertexBuffers[frame];
        if (buffer == null) {
            buffer = SharedBuffer.allocate(bytes, state, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
            vertexBuffers[frame] = buffer;
        }
        buffer.ensureCapacity(bytes, state);
        MemoryUtil.memFloatBuffer(
                        buffer.allocationInfo.pMappedData(), mesh.count() * WaterMesh.VERTEX_FLOATS)
                .put(mesh.vertices(), 0, mesh.count() * WaterMesh.VERTEX_FLOATS);
        final long indexBytes = (long) mesh.indexCount() * Integer.BYTES;
        SharedBuffer indices = indexBuffers[frame];
        if (indices == null) {
            indices = SharedBuffer.allocate(indexBytes, state, VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
            indexBuffers[frame] = indices;
        }
        indices.ensureCapacity(indexBytes, state);
        MemoryUtil.memIntBuffer(indices.allocationInfo.pMappedData(), mesh.indexCount())
                .put(mesh.indices(), 0, mesh.indexCount());
    }

    /**
     * Write this frame's tile list: each tile's corner relative to the camera, and its optics.
     *
     * @param state The Vulkan state.
     * @param frame The frame in flight.
     * @param camera The camera's position.
     */
    private void writeTiles(@NonNull VulkanState state, int frame, @NonNull Vector3dc camera) {
        final long bytes = (long) tiles.size() * ShaderBindings.Water.TILE_SIZE;
        SharedBuffer buffer = tileBuffers[frame];
        if (buffer == null) {
            buffer = SharedBuffer.allocate(bytes, state, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            tileBuffers[frame] = buffer;
        }
        buffer.ensureCapacity(bytes, state);
        final long address = buffer.allocationInfo.pMappedData();
        for (int index = 0; index < tiles.size(); ++index) {
            final long at = address + (long) index * ShaderBindings.Water.TILE_SIZE;
            final Vector3dc origin = tiles.get(index).origin();
            MemoryUtil.memPutFloat(at, (float) (origin.x() - camera.x()));
            MemoryUtil.memPutFloat(at + Float.BYTES, (float) (origin.y() - camera.y()));
            MemoryUtil.memPutFloat(at + 2 * Float.BYTES, (float) (origin.z() - camera.z()));
            MemoryUtil.memPutFloat(at + 3 * Float.BYTES, tileClimates.get(index));
            final WaterTile tile = tiles.get(index);
            MemoryUtil.memPutFloat(
                    at + ShaderBindings.Water.TILE_WIDTH_X,
                    (float) (tile.cellsX() * tile.cellSize()));
            MemoryUtil.memPutFloat(
                    at + ShaderBindings.Water.TILE_WIDTH_Z,
                    (float) (tile.cellsZ() * tile.cellSize()));
            final WaterMath.Optics tileOptics = optics.get(index);
            for (int channel = 0; channel < WaterMath.CHANNELS; ++channel) {
                final long offset = (long) channel * Float.BYTES;
                MemoryUtil.memPutFloat(
                        at + ShaderBindings.Water.TILE_ABSORPTION + offset,
                        (float) tileOptics.absorption()[channel]);
                MemoryUtil.memPutFloat(
                        at + ShaderBindings.Water.TILE_SCATTERING + offset,
                        (float) tileOptics.scattering()[channel]);
                MemoryUtil.memPutFloat(
                        at + ShaderBindings.Water.TILE_BACKSCATTERING + offset,
                        (float) tileOptics.backscattering()[channel]);
            }
        }
    }

    /**
     * Create the pipeline: water cells as triangles, both sides, depth tested and written, drawn
     * over the scene's color.
     *
     * @param state The Vulkan state.
     * @param stack The stack to allocate on.
     * @param depthOnly Whether for the gate: the vertex shader alone, writing only depth.
     * @return The VkPipeline.
     */
    private long createPipeline(
            @NonNull VulkanState state, @NonNull MemoryStack stack, boolean depthOnly) {
        int count = 0;
        for (int i = 0; i < shader.shaderModules.length; ++i) {
            if (!depthOnly || shader.shaderStages.get(i).stage() == VK_SHADER_STAGE_VERTEX_BIT) {
                ++count;
            }
        }
        VkPipelineShaderStageCreateInfo.Buffer stages =
                VkPipelineShaderStageCreateInfo.calloc(count, stack);
        int added = 0;
        for (int i = 0; i < shader.shaderModules.length; ++i) {
            if (!depthOnly || shader.shaderStages.get(i).stage() == VK_SHADER_STAGE_VERTEX_BIT) {
                stages.get(added++).set(shader.shaderStages.get(i));
            }
        }
        VkVertexInputBindingDescription.Buffer bindings =
                VkVertexInputBindingDescription.calloc(1, stack);
        bindings.get(0)
                .binding(0)
                .stride(ShaderBindings.Water.VERTEX_SIZE)
                .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
        VkVertexInputAttributeDescription.Buffer attributes =
                VkVertexInputAttributeDescription.calloc(6, stack);
        attributes
                .get(4)
                .binding(0)
                .location(4)
                .format(VK_FORMAT_R32G32_SFLOAT)
                .offset(WaterMesh.FLOW_FLOAT * Float.BYTES);
        attributes
                .get(5)
                .binding(0)
                .location(5)
                .format(VK_FORMAT_R32_SFLOAT)
                .offset(WaterMesh.DEPTH_FLOAT * Float.BYTES);
        attributes
                .get(2)
                .binding(0)
                .location(2)
                .format(VK_FORMAT_R32_SFLOAT)
                .offset(WaterMesh.CALM_FLOAT * Float.BYTES);
        attributes
                .get(3)
                .binding(0)
                .location(3)
                .format(VK_FORMAT_R32G32_SFLOAT)
                .offset(WaterMesh.NORMAL_FLOAT * Float.BYTES);
        attributes.get(0).binding(0).location(0).format(VK_FORMAT_R32G32B32_SFLOAT).offset(0);
        attributes.get(1).binding(0).location(1).format(VK_FORMAT_R32_UINT).offset(3 * Float.BYTES);
        VkPipelineColorBlendAttachmentState.Buffer blend =
                VkPipelineColorBlendAttachmentState.calloc(1, stack);
        blend.get(0)
                .blendEnable(false)
                .colorWriteMask(
                        VK_COLOR_COMPONENT_R_BIT
                                | VK_COLOR_COMPONENT_G_BIT
                                | VK_COLOR_COMPONENT_B_BIT
                                | VK_COLOR_COMPONENT_A_BIT);
        VkGraphicsPipelineCreateInfo.Buffer infos = VkGraphicsPipelineCreateInfo.calloc(1, stack);
        infos.get(0)
                .sType$Default()
                .pNext(
                        VkPipelineRenderingCreateInfo.calloc(stack)
                                .sType$Default()
                                .pColorAttachmentFormats(
                                        depthOnly
                                                ? null
                                                : stack.ints(
                                                        PipelineManagerVulkan.SCENE_COLOR_FORMAT))
                                .depthAttachmentFormat(PipelineManagerVulkan.DEPTH_FORMAT))
                .stageCount(count)
                .pStages(stages)
                .pVertexInputState(
                        VkPipelineVertexInputStateCreateInfo.calloc(stack)
                                .sType$Default()
                                .pVertexBindingDescriptions(bindings)
                                .pVertexAttributeDescriptions(attributes))
                .pInputAssemblyState(
                        VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                                .sType$Default()
                                .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST))
                .pViewportState(
                        VkPipelineViewportStateCreateInfo.calloc(stack)
                                .sType$Default()
                                .viewportCount(1)
                                .scissorCount(1))
                .pRasterizationState(
                        VkPipelineRasterizationStateCreateInfo.calloc(stack)
                                .sType$Default()
                                .polygonMode(VK_POLYGON_MODE_FILL)
                                .cullMode(VK_CULL_MODE_NONE)
                                .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                                .lineWidth(1.0f))
                .pMultisampleState(
                        VkPipelineMultisampleStateCreateInfo.calloc(stack)
                                .sType$Default()
                                .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT))
                .pDepthStencilState(
                        VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                                .sType$Default()
                                .depthTestEnable(true)
                                .depthWriteEnable(true)
                                .depthCompareOp(VK_COMPARE_OP_LESS))
                .pColorBlendState(
                        VkPipelineColorBlendStateCreateInfo.calloc(stack)
                                .sType$Default()
                                .pAttachments(depthOnly ? null : blend))
                .pDynamicState(
                        VkPipelineDynamicStateCreateInfo.calloc(stack)
                                .sType$Default()
                                .pDynamicStates(
                                        stack.ints(
                                                VK_DYNAMIC_STATE_VIEWPORT,
                                                VK_DYNAMIC_STATE_SCISSOR)))
                .layout(pipelineLayout)
                .renderPass(VK_NULL_HANDLE);
        LongBuffer longOutput = stack.callocLong(1);
        checkError(
                vkCreateGraphicsPipelines(
                        state.device.logical, VK_NULL_HANDLE, infos, null, longOutput));
        return longOutput.get(0);
    }
}
