package com.ikalagaming.graphics.backend.vulkan;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.base.ShaderMap;
import com.ikalagaming.graphics.backend.vulkan.stages.*;
import com.ikalagaming.graphics.frontend.*;
import com.ikalagaming.graphics.frontend.gui.IkGui;
import com.ikalagaming.graphics.frontend.gui.data.FontAtlas;
import com.ikalagaming.graphics.graph.CascadeShadow;

import imgui.ImFontAtlas;
import imgui.ImGui;
import imgui.type.ImInt;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;

@Slf4j
public class PipelineManagerVulkan {

    /** The size of a 4x4 model matrix ({@value}). */
    public static final int MODEL_MATRIX_SIZE = 4 * 4;

    /** Fallback pipeline that does nothing. */
    private static final Pipeline ERROR_PIPELINE = new PipelineVulkan(new RenderStage[0]);

    /** The width of the drawable area in pixels. */
    private int cachedHeight;

    /** The width of the drawable area in pixels. */
    private int cachedWidth;

    /** The cascade shadow map. */
    private final ArrayList<CascadeShadow> cascadeShadows;

    /** The texture we store font atlas on. */
    @Deprecated private Texture imguiFont;

    /** The texture we store the font atlas on. */
    private Texture fontAtlas;

    /** Geometry buffer. */
    private Framebuffer gBuffer;

    /** The mesh to render. */
    private ImGuiMesh imGuiMesh;

    /** The GUI mesh to render. */
    private GuiMesh guiMesh;

    /** The buffer to use for storing point light info. */
    private Buffer pointLights;

    /** A mesh for rendering onto. */
    private QuadMesh quadMesh;

    /** The map from config value to the associated renderer. */
    private final Map<Integer, Pipeline> renderers;

    /** The buffer to render the scene to before post-processing. */
    private Framebuffer screenTexture;

    /** The depth map for shadows. */
    private Framebuffer shadowBuffers;

    /** Model used for rendering the skybox. */
    private SkyboxModel skybox;

    /** The buffer to use for storing spotlight info. */
    private Buffer spotLights;

    private final AnimationRender stageAnimationRender;
    private final FramebufferTransition stageBackBufferBinding;
    private final FilterRender stageFilterRender;
    private final GuiRender stageGuiRender;
    private final LightRender stageLightRender;
    private final ModelMatrixUpdate stageModelMatrixUpdate;
    private final SceneRender stageSceneRender;
    private final SceneRenderWireframe stageSceneRenderWireframe;
    private final FramebufferTransition stageScreenTextureBinding;
    private final ShadowRender stageShadowRender;
    private final SkyboxRender stageSkyboxRender;

    public PipelineManagerVulkan(
            @NonNull Window window, @NonNull ShaderMap shaders, @NonNull VulkanState state) {
        cascadeShadows = new ArrayList<>();
        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
            cascadeShadows.add(new CascadeShadow());
        }

        cachedWidth = window.getWidth();
        cachedHeight = window.getHeight();

        renderers = new HashMap<>();
        gBuffer = generateGBuffer();
        generateRenderBuffers();
        createGuiFont();
        shadowBuffers = createShadowBuffers();
        skybox = new SkyboxModel();
        quadMesh = QuadMesh.getInstance();
        createLightBuffers();
        imGuiMesh = ImGuiMesh.create();
        guiMesh = GuiMesh.create();

        stageModelMatrixUpdate = new ModelMatrixUpdate();
        stageModelMatrixUpdate.initialize(state);
        stageSceneRender =
                new SceneRender((ShaderVulkan) shaders.getShader(RenderStage.Type.SCENE), gBuffer);
        stageSceneRender.initialize(state);
        stageSceneRenderWireframe =
                new SceneRenderWireframe(shaders.getShader(RenderStage.Type.SCENE), gBuffer);
        stageSceneRenderWireframe.initialize(state);
        stageGuiRender =
                new GuiRender(
                        shaders.getShader(RenderStage.Type.GUI_LEGACY),
                        shaders.getShader(RenderStage.Type.GUI),
                        imGuiMesh,
                        guiMesh,
                        fontAtlas);
        stageGuiRender.initialize(state);
        stageSkyboxRender = new SkyboxRender(shaders.getShader(RenderStage.Type.SKYBOX), skybox);
        stageSkyboxRender.initialize(state);
        stageShadowRender =
                new ShadowRender(
                        shaders.getShader(RenderStage.Type.SHADOW), cascadeShadows, shadowBuffers);
        stageShadowRender.initialize(state);
        stageLightRender =
                new LightRender(
                        shaders.getShader(RenderStage.Type.LIGHT),
                        cascadeShadows,
                        pointLights,
                        spotLights,
                        shadowBuffers,
                        gBuffer,
                        quadMesh);
        stageLightRender.initialize(state);
        stageAnimationRender =
                new AnimationRender((ShaderVulkan) shaders.getShader(RenderStage.Type.ANIMATION));
        stageAnimationRender.initialize(state);
        stageFilterRender =
                new FilterRender(
                        shaders.getShader(RenderStage.Type.FILTER), screenTexture, quadMesh);
        stageFilterRender.initialize(state);
        stageScreenTextureBinding = new FramebufferTransition(screenTexture, 1, 1);
        stageScreenTextureBinding.initialize(state);
        stageBackBufferBinding =
                new FramebufferTransition(
                        new Framebuffer(0, cachedWidth, cachedHeight, new long[] {}), 1, 1);
        stageBackBufferBinding.initialize(state);
    }

    private Pipeline buildPipeline(final int configuration) {
        List<RenderStage> stages = new ArrayList<>();
        if (RenderConfig.hasError(configuration)) {
            log.error("Error in pipeline config");
            return ERROR_PIPELINE;
        }

        if (RenderConfig.hasSceneStage(configuration)) {
            stages.add(stageModelMatrixUpdate);
        }
        if (RenderConfig.hasAnimationStage(configuration)) {
            stages.add(stageAnimationRender);
        }
        if (RenderConfig.hasShadowStage(configuration)) {
            stages.add(stageShadowRender);
        }
        if (RenderConfig.hasSceneStage(configuration)) {
            if (RenderConfig.sceneIsWireframe(configuration)) {
                stages.add(stageSceneRenderWireframe);
            } else {
                stages.add(stageSceneRender);
            }
        }
        if (RenderConfig.hasFilterStage(configuration)) {
            // NOTE(ches) for post-processing we will need to render to a texture instead of the
            // back buffer
            stages.add(stageScreenTextureBinding);
        } else {
            stages.add(stageBackBufferBinding);
        }
        if (RenderConfig.hasSceneStage(configuration)) {
            stages.add(stageLightRender);
        }
        if (RenderConfig.hasSkyboxStage(configuration)) {
            stages.add(stageSkyboxRender);
        }
        if (RenderConfig.hasFilterStage(configuration)) {
            stages.add(stageBackBufferBinding);
            stages.add(stageFilterRender);
        }
        if (RenderConfig.hasGuiStage(configuration)) {
            stages.add(stageGuiRender);
        }

        return new PipelineVulkan(stages.toArray(new RenderStage[0]));
    }

    /** Clean up all the rendering resources. */
    public void cleanup(@NonNull VulkanState state) {
        stageAnimationRender.cleanup(state);
        stageBackBufferBinding.cleanup(state);
        stageFilterRender.cleanup(state);
        stageGuiRender.cleanup(state);
        stageLightRender.cleanup(state);
        stageModelMatrixUpdate.cleanup(state);
        stageSceneRender.cleanup(state);
        stageSceneRenderWireframe.cleanup(state);
        stageScreenTextureBinding.cleanup(state);
        stageShadowRender.cleanup(state);
        stageSkyboxRender.cleanup(state);
        GraphicsManager.getDeletionQueue().add(imguiFont);
        imguiFont = null;
        GraphicsManager.getDeletionQueue().add(fontAtlas);
        fontAtlas = null;
        imGuiMesh.cleanup();
        guiMesh.cleanup();
        skybox.cleanup();
        skybox = null;
        quadMesh.cleanup();
        quadMesh = null;
        GraphicsManager.getDeletionQueue().add(pointLights);
        pointLights = null;
        GraphicsManager.getDeletionQueue().add(spotLights);
        spotLights = null;
        GraphicsManager.getDeletionQueue().add(gBuffer);
        gBuffer = null;
        GraphicsManager.getDeletionQueue().add(shadowBuffers);
        shadowBuffers = null;
        deleteRenderBuffers();
    }

    private void createGuiFont() {
        ImFontAtlas fontAtlas = ImGui.getIO().getFonts();
        ImInt width = new ImInt();
        ImInt height = new ImInt();
        ByteBuffer buf = fontAtlas.getTexDataAsRGBA32(width, height);
        imguiFont =
                GraphicsManager.getRenderInstance()
                        .getTextureLoader()
                        .load(buf, Format.R8G8B8A8_UINT, width.get(), height.get());
        fontAtlas.setTexID((int) imguiFont.id());

        FontAtlas fontAtlas1 = IkGui.getIO().fonts;
        final String notoSans = "fonts/NotoSans.ttf";
        if (!fontAtlas1.loadFont(notoSans)) {
            log.error("Issue setting up GUI font");
        }
        IkGui.setFont(notoSans, 12);
        IkGui.setFontFallbacks(notoSans);
        fontAtlas1.addDefaultCharacters(notoSans, IkGui.getFontSize());

        this.fontAtlas =
                GraphicsManager.getRenderInstance()
                        .getTextureLoader()
                        .load(
                                null,
                                Format.R8G8B8A8_UINT,
                                FontAtlas.FONT_ATLAS_IMAGE_WIDTH,
                                FontAtlas.FONT_ATLAS_IMAGE_HEIGHT);
    }

    /** Initialize the lighting SSBOs and fill them with zeroes. */
    private void createLightBuffers() {
        int pointLightBuffer = 0;
        // TODO(ches) create buffer

        /*
         * Position (vec3 + ignored), color (vec3), intensity (1), Attenuation
         * (3 + ignored), in that order.
         */
        final int POINT_LIGHT_SIZE = 4 + 3 + 1 + 4;
        FloatBuffer pointLightFloatBuffer =
                MemoryUtil.memAllocFloat(PipelineVulkan.MAX_LIGHTS_SUPPORTED * POINT_LIGHT_SIZE);

        pointLightFloatBuffer
                .put(new float[PipelineVulkan.MAX_LIGHTS_SUPPORTED * POINT_LIGHT_SIZE])
                .flip();

        // TODO(ches) buffer data

        MemoryUtil.memFree(pointLightFloatBuffer);
        pointLights = new Buffer(pointLightBuffer, Buffer.Type.SHADER_STORAGE);

        int spotLightBuffer = 0;
        // TODO(ches) create buffer

        /*
         * Position (vec3 + ignored), color (vec3), intensity (1), Attenuation
         * (3 + ignored), cone direction (vec3), cutoff (1) in that order.
         */
        final int SPOT_LIGHT_SIZE = 4 + 3 + 1 + 4 + 3 + 1;
        FloatBuffer spotLightFloatBuffer =
                MemoryUtil.memAllocFloat(PipelineVulkan.MAX_LIGHTS_SUPPORTED * SPOT_LIGHT_SIZE);

        spotLightFloatBuffer
                .put(new float[PipelineVulkan.MAX_LIGHTS_SUPPORTED * POINT_LIGHT_SIZE])
                .flip();

        // TODO(ches) buffer data

        MemoryUtil.memFree(spotLightFloatBuffer);
        spotLights = new Buffer(spotLightBuffer, Buffer.Type.SHADER_STORAGE);
    }

    private Framebuffer createShadowBuffers() {
        int depthMapFBO = 0;
        // TODO(ches) create buffer

        int[] shadowTextures = new int[CascadeShadow.SHADOW_MAP_CASCADE_COUNT];

        // TODO(ches) create textures

        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
            // TODO(ches) create all the textures
        }

        long[] textureIds = Arrays.stream(shadowTextures).mapToLong(i -> (long) i).toArray();
        return new Framebuffer(
                depthMapFBO,
                CascadeShadow.SHADOW_MAP_WIDTH,
                CascadeShadow.SHADOW_MAP_HEIGHT,
                textureIds);
    }

    /** Free up the current render buffers. */
    void deleteRenderBuffers() {
        GraphicsManager.getDeletionQueue().add(screenTexture);
    }

    /**
     * Generate the geometry buffer.
     *
     * @return The newly generated buffer.
     */
    private Framebuffer generateGBuffer() {
        int gBufferId = 0;
        // TODO(ches) create buffer

        // TODO(ches) create textures
        int[] textures = new int[5];

        // Base Color
        // Normal
        // Tangent
        for (int i = 0; i <= 2; ++i) {
            // TODO(ches) set up textures
        }

        // Material
        // TODO(ches) set up texture

        // Depth
        // TODO(ches) set up texture

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer intBuff = stack.mallocInt(textures.length - 1);
            for (int i = 0; i < textures.length - 1; ++i) {
                intBuff.put(i, i);
            }
            // TODO(ches) set these textures up as the gbuffer
        }

        long[] textureIds = Arrays.stream(textures).mapToLong(i -> (long) i).toArray();
        return new Framebuffer(gBufferId, cachedWidth, cachedHeight, textureIds);
    }

    /**
     * Generate new render buffers. {@link #deleteRenderBuffers()} should be called before doing
     * this if they already exist.
     */
    private void generateRenderBuffers() {
        int[] textures = new int[2];
        // TODO(ches) create textures

        // TODO(ches) set up textures

        int screenFBO = 0;
        // TODO(ches) create buffer

        // TODO(ches) set up render buffers

        long[] textureIds = Arrays.stream(textures).mapToLong(i -> (long) i).toArray();
        screenTexture = new Framebuffer(screenFBO, cachedWidth, cachedHeight, textureIds);
        if (stageScreenTextureBinding != null) {
            stageScreenTextureBinding.setFramebuffer(screenTexture);
        }
        if (stageFilterRender != null) {
            stageFilterRender.setSceneTexture(screenTexture);
        }
    }

    public Pipeline getPipeline(final int configuration) {
        return renderers.computeIfAbsent(configuration, this::buildPipeline);
    }

    /**
     * Update the buffers and GUI when we resize the screen.
     *
     * @param width The new screen width in pixels.
     * @param height The new screen height in pixels.
     */
    public void resize(final int width, final int height) {
        cachedWidth = width;
        cachedHeight = height;
        deleteRenderBuffers();
        generateRenderBuffers();
    }
}
