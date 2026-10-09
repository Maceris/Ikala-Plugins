package com.ikalagaming.graphics.vulkan.stages;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.debug.DebugShape;
import com.ikalagaming.graphics.vulkan.DebugGeometry;
import com.ikalagaming.graphics.vulkan.DebugVisualizerShapes;
import com.ikalagaming.graphics.vulkan.PerFrameData;
import com.ikalagaming.graphics.vulkan.PipelineManagerVulkan;
import com.ikalagaming.graphics.vulkan.RenderStage;
import com.ikalagaming.graphics.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.vulkan.TextureInfoVulkan;
import com.ikalagaming.graphics.vulkan.VulkanState;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix4f;
import org.joml.Vector3dc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;

/**
 * Draws debug shapes as lines onto the final image, after the filters and before the GUI. Shapes
 * come from plugins (see {@link com.ikalagaming.graphics.DebugDraw}) and from the built-in
 * visualizers. Depth tested shapes test against the scene's depth without writing to it, and the
 * rest are drawn on top of everything.
 */
@Slf4j
public class DebugRender implements RenderStage {

    /** Bytes per vertex: a render space position, then a packed RGBA color. */
    public static final int VERTEX_SIZE = 3 * Float.BYTES + Integer.BYTES;

    /** The size of the push constants, the projection × view matrix. */
    private static final int PUSH_CONSTANT_SIZE = 16 * Float.BYTES;

    /** The shader to use for rendering. */
    private final ShaderVulkan shader;

    /** VkPipelineLayout, VK_NULL_HANDLE if not set up. */
    private long pipelineLayout;

    /** VkPipeline for depth tested lines, VK_NULL_HANDLE if not set up. */
    private long pipelineDepthTested;

    /** VkPipeline for lines drawn on top of everything, VK_NULL_HANDLE if not set up. */
    private long pipelineOnTop;

    /** Line vertices for depth tested shapes, built each frame. */
    private final VertexList depthTested = new VertexList();

    /** Line vertices for shapes drawn on top, built each frame. */
    private final VertexList onTop = new VertexList();

    /** Scratch matrix for the push constants. */
    private final Matrix4f projectionView = new Matrix4f();

    /**
     * Set up the debug render stage.
     *
     * @param shader The shader to use for rendering.
     */
    public DebugRender(@NonNull ShaderVulkan shader) {
        this.shader = shader;
        pipelineLayout = VK_NULL_HANDLE;
        pipelineDepthTested = VK_NULL_HANDLE;
        pipelineOnTop = VK_NULL_HANDLE;
    }

    @Override
    public void initialize(@NonNull VulkanState vulkanState) {
        log.debug("Initializing debug render");
        createPipelineLayout(vulkanState);
        pipelineDepthTested = createPipeline(vulkanState, true);
        pipelineOnTop = createPipeline(vulkanState, false);
    }

    @Override
    public void cleanup(@NonNull VulkanState vulkanState) {
        vkDestroyPipeline(vulkanState.device.logical, pipelineOnTop, null);
        pipelineOnTop = VK_NULL_HANDLE;
        vkDestroyPipeline(vulkanState.device.logical, pipelineDepthTested, null);
        pipelineDepthTested = VK_NULL_HANDLE;
        vkDestroyPipelineLayout(vulkanState.device.logical, pipelineLayout, null);
        pipelineLayout = VK_NULL_HANDLE;
    }

    @Override
    public void render(
            Scene scene,
            @NonNull Window window,
            @NonNull VulkanState vulkanState,
            int renderConfig) {
        final PerFrameData frameData = vulkanState.perFrameData[vulkanState.frameIndex];

        depthTested.clear();
        onTop.clear();
        final Vector3dc origin = scene.getCamera().getPosition();
        GraphicsManager.collectDebugShapes(shape -> add(shape, origin));
        DebugVisualizerShapes.collect(scene, frameData, shape -> add(shape, origin));

        final int vertexCount = depthTested.count() + onTop.count();
        if (vertexCount == 0) {
            return;
        }

        frameData.debugVertices.ensureCapacity((long) vertexCount * VERTEX_SIZE, vulkanState);
        ByteBuffer vertices =
                MemoryUtil.memByteBuffer(
                        frameData.debugVertices.allocationInfo.pMappedData(),
                        vertexCount * VERTEX_SIZE);
        depthTested.copyTo(vertices, 0);
        onTop.copyTo(vertices, depthTested.count() * VERTEX_SIZE);

        final int width = Math.min(window.getWidth(), vulkanState.realSize.width());
        final int height = Math.min(window.getHeight(), vulkanState.realSize.height());
        if (width <= 0 || height <= 0) {
            return;
        }

        scene.getProjection()
                .getProjectionMatrix()
                .mul(scene.getCamera().getViewMatrix(), projectionView);

        final VkCommandBuffer commandBuffer =
                vulkanState.commandBuffersGraphics[vulkanState.frameIndex];
        final TextureInfoVulkan target = frameData.finalTexture;
        final TextureInfoVulkan depth = frameData.gBuffer.depth();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Earlier stages drew to the final image, and left the depth read only
            VkImageMemoryBarrier2.Buffer barriers = VkImageMemoryBarrier2.calloc(1, stack);
            SceneRender.imageBarrier(
                    barriers.get(0),
                    target.texture,
                    VK_IMAGE_ASPECT_COLOR_BIT,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            vkCmdPipelineBarrier2(
                    commandBuffer,
                    VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barriers));

            VkRenderingAttachmentInfo.Buffer colorAttachments =
                    VkRenderingAttachmentInfo.calloc(1, stack);
            colorAttachments
                    .get(0)
                    .sType$Default()
                    .imageView(target.view)
                    .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            VkRenderingAttachmentInfo depthAttachment =
                    VkRenderingAttachmentInfo.calloc(stack)
                            .sType$Default()
                            .imageView(depth.view)
                            .imageLayout(VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL)
                            .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD)
                            .storeOp(VK_ATTACHMENT_STORE_OP_NONE);
            VkRenderingInfo renderingInfo =
                    VkRenderingInfo.calloc(stack)
                            .sType$Default()
                            .renderArea(area -> area.extent().set(width, height))
                            .layerCount(1)
                            .pColorAttachments(colorAttachments)
                            .pDepthAttachment(depthAttachment);
            vkCmdBeginRendering(commandBuffer, renderingInfo);

            // A negative height flips y so the projection matrices work the same as for the scene
            VkViewport.Buffer viewports = VkViewport.calloc(1, stack);
            viewports.get(0).x(0).y(height).width(width).height(-height).minDepth(0).maxDepth(1);
            vkCmdSetViewport(commandBuffer, 0, viewports);
            VkRect2D.Buffer scissors = VkRect2D.calloc(1, stack);
            scissors.get(0).extent().set(width, height);
            vkCmdSetScissor(commandBuffer, 0, scissors);

            vkCmdBindVertexBuffers(
                    commandBuffer, 0, stack.longs(frameData.debugVertices.buffer), stack.longs(0));
            ByteBuffer pushConstants = stack.malloc(PUSH_CONSTANT_SIZE);
            projectionView.get(pushConstants);
            vkCmdPushConstants(
                    commandBuffer, pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstants);

            if (depthTested.count() > 0) {
                vkCmdBindPipeline(
                        commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineDepthTested);
                vkCmdDraw(commandBuffer, depthTested.count(), 1, 0, 0);
            }
            if (onTop.count() > 0) {
                vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineOnTop);
                vkCmdDraw(commandBuffer, onTop.count(), 1, depthTested.count(), 0);
            }

            vkCmdEndRendering(commandBuffer);
        }
    }

    /**
     * Turn a shape into line vertices in the right list.
     *
     * @param shape The shape.
     * @param origin The camera position, the render space origin.
     */
    private void add(@NonNull DebugShape shape, @NonNull Vector3dc origin) {
        VertexList list = shape.onTop() ? onTop : depthTested;
        DebugGeometry.append(shape, origin, list::add);
    }

    private void createPipelineLayout(@NonNull VulkanState state) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);
            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack);
            pushConstantRanges
                    .get(0)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)
                    .offset(0)
                    .size(PUSH_CONSTANT_SIZE);
            VkPipelineLayoutCreateInfo pipelineLayoutCreateInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pPushConstantRanges(pushConstantRanges);
            checkError(
                    vkCreatePipelineLayout(
                            state.device.logical, pipelineLayoutCreateInfo, null, longOutput));
            pipelineLayout = longOutput.get(0);
        }
    }

    /**
     * Create a pipeline for drawing lines.
     *
     * @param state The Vulkan state.
     * @param depthTest Whether to test against the scene depth, or draw on top.
     * @return The VkPipeline.
     */
    private long createPipeline(@NonNull VulkanState state, boolean depthTest) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            VkVertexInputAttributeDescription.Buffer vertexAttributes =
                    VkVertexInputAttributeDescription.calloc(2, stack);
            vertexAttributes
                    .get(0)
                    .binding(0)
                    .location(0)
                    .format(VK_FORMAT_R32G32B32_SFLOAT)
                    .offset(0);
            vertexAttributes
                    .get(1)
                    .binding(0)
                    .location(1)
                    .format(VK_FORMAT_R32_UINT)
                    .offset(3 * Float.BYTES);

            VkVertexInputBindingDescription.Buffer vertexBindings =
                    VkVertexInputBindingDescription.calloc(1, stack);
            vertexBindings
                    .get(0)
                    .binding(0)
                    .stride(VERTEX_SIZE)
                    .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

            VkPipelineVertexInputStateCreateInfo vertexInputState =
                    VkPipelineVertexInputStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .pVertexBindingDescriptions(vertexBindings)
                            .pVertexAttributeDescriptions(vertexAttributes);

            VkPipelineInputAssemblyStateCreateInfo inputAssemblyState =
                    VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .topology(VK_PRIMITIVE_TOPOLOGY_LINE_LIST);

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

            // Never writes depth, the depth stays as the scene left it
            VkPipelineDepthStencilStateCreateInfo depthStencilState =
                    VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .depthTestEnable(depthTest)
                            .depthWriteEnable(false)
                            .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL);

            VkPipelineRenderingCreateInfo renderingCreateInfo =
                    VkPipelineRenderingCreateInfo.calloc(stack)
                            .sType$Default()
                            .pColorAttachmentFormats(
                                    stack.ints(PipelineManagerVulkan.SCREEN_FORMAT))
                            .depthAttachmentFormat(PipelineManagerVulkan.DEPTH_FORMAT);

            VkPipelineColorBlendAttachmentState.Buffer blendAttachments =
                    VkPipelineColorBlendAttachmentState.calloc(1, stack);
            blendAttachments
                    .get(0)
                    .blendEnable(true)
                    .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                    .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                    .colorBlendOp(VK_BLEND_OP_ADD)
                    .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                    .dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
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

            // Lines are 1 pixel wide, wide lines aren't available on MoltenVK
            VkPipelineRasterizationStateCreateInfo rasterizationState =
                    VkPipelineRasterizationStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .polygonMode(VK_POLYGON_MODE_FILL)
                            .cullMode(VK_CULL_MODE_NONE)
                            .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
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
                    .pVertexInputState(vertexInputState)
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
            return longOutput.get(0);
        }
    }

    /** A growable list of line vertices, kept between frames to avoid reallocating. */
    private static final class VertexList {
        /** Four ints per vertex: the position as float bits, then the color. */
        private int[] data = new int[1024];

        /** The number of vertices. */
        private int count = 0;

        private void clear() {
            count = 0;
        }

        private int count() {
            return count;
        }

        private void add(float x, float y, float z, int color) {
            if ((count + 1) * 4 > data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            final int offset = count * 4;
            data[offset] = Float.floatToRawIntBits(x);
            data[offset + 1] = Float.floatToRawIntBits(y);
            data[offset + 2] = Float.floatToRawIntBits(z);
            data[offset + 3] = color;
            count += 1;
        }

        private void copyTo(@NonNull ByteBuffer destination, int byteOffset) {
            destination.asIntBuffer().put(byteOffset / Integer.BYTES, data, 0, count * 4);
        }
    }
}
