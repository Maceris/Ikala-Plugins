package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.glDrawBuffers;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.base.ShaderMap;
import com.ikalagaming.graphics.backend.opengl.stages.*;
import com.ikalagaming.graphics.exceptions.RenderException;
import com.ikalagaming.graphics.frontend.*;
import com.ikalagaming.graphics.graph.CascadeShadow;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.launcher.PluginFolder;
import com.ikalagaming.plugins.config.ConfigManager;
import com.ikalagaming.util.SafeResourceLoader;

import imgui.ImFontAtlas;
import imgui.ImGui;
import imgui.type.ImInt;
import lombok.NonNull;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class PipelineManager {

    /** The size of a draw command. */
    private static final int COMMAND_SIZE = 5 * 4;

    /** The size of a draw element. */
    private static final int DRAW_ELEMENT_SIZE = 2 * 4;

    /** The size of a 4x4 model matrix ({@value}). */
    public static final int MODEL_MATRIX_SIZE = 4 * 4;

    /**
     * Whether we have set up the buffers for the scene. If we have, but need to set data up for the
     * scene again, we will need to clear them out and start over.
     */
    private final AtomicBoolean buffersPopulated;

    /** The width of the drawable area in pixels. */
    private int cachedHeight;

    /** The width of the drawable area in pixels. */
    private int cachedWidth;

    /** The cascade shadow map. */
    private final ArrayList<CascadeShadow> cascadeShadows;

    /** The buffers for the batches. */
    private final CommandBuffer commandBuffers;

    /** The texture to use by default if nothing is provided for a model. */
    private Texture defaultTexture;

    /** The texture we store font atlas on. */
    private Texture font;

    /** Geometry buffer. */
    private Framebuffer gBuffer;

    /** The mesh to render. */
    private GuiMesh guiMesh;

    /** The buffer to use for storing point light info. */
    private Buffer pointLights;

    /** A mesh for rendering onto. */
    private QuadMesh quadMesh;

    /** The buffers for indirect drawing of models. */
    private final RenderBuffers renderBuffers;

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
    private final GuiRenderStandalone stageGuiRenderStandalone;
    private final LightRender stageLightRender;
    private final ModelMatrixUpdate stageModelMatrixUpdate;
    private final SceneRender stageSceneRender;
    private final SceneRenderWireframe stageSceneRenderWireframe;
    private final FramebufferTransition stageScreenTextureBinding;
    private final ShadowRender stageShadowRender;
    private final SkyboxRender stageSkyboxRender;

    public PipelineManager(@NonNull Window window, @NonNull ShaderMap shaders) {
        buffersPopulated = new AtomicBoolean();
        cascadeShadows = new ArrayList<>();
        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
            cascadeShadows.add(new CascadeShadow());
        }

        cachedWidth = window.getWidth();
        cachedHeight = window.getHeight();

        renderers = new HashMap<>();
        gBuffer = generateGBuffer();
        renderBuffers = new RenderBuffers();
        generateRenderBuffers();
        commandBuffers = new CommandBuffer();
        createImGuiFont();
        shadowBuffers = createShadowBuffers();
        skybox = SkyboxModel.create();
        quadMesh = QuadMesh.getInstance();
        createLightBuffers();
        guiMesh = GuiMesh.create();

        final String defaultTexturePath =
                PluginFolder.getResource(
                                GraphicsPlugin.PLUGIN_NAME,
                                PluginFolder.ResourceType.DATA,
                                ConfigManager.loadConfig(GraphicsPlugin.PLUGIN_NAME)
                                        .getString("default-texture"))
                        .getAbsolutePath();

        defaultTexture =
                GraphicsManager.getRenderInstance().getTextureLoader().load(defaultTexturePath);

        stageModelMatrixUpdate = new ModelMatrixUpdate(commandBuffers);
        stageSceneRender =
                new SceneRender(
                        shaders.getShader(RenderStage.Type.SCENE),
                        renderBuffers,
                        gBuffer,
                        commandBuffers,
                        defaultTexture);
        stageSceneRenderWireframe =
                new SceneRenderWireframe(
                        shaders.getShader(RenderStage.Type.SCENE),
                        renderBuffers,
                        gBuffer,
                        commandBuffers,
                        defaultTexture);
        stageGuiRenderStandalone =
                new GuiRenderStandalone(shaders.getShader(RenderStage.Type.GUI), guiMesh);
        stageGuiRender = new GuiRender(shaders.getShader(RenderStage.Type.GUI), guiMesh);
        stageSkyboxRender = new SkyboxRender(shaders.getShader(RenderStage.Type.SKYBOX), skybox);
        stageShadowRender =
                new ShadowRender(
                        shaders.getShader(RenderStage.Type.SHADOW),
                        renderBuffers,
                        cascadeShadows,
                        shadowBuffers,
                        commandBuffers);
        stageLightRender =
                new LightRender(
                        shaders.getShader(RenderStage.Type.LIGHT),
                        cascadeShadows,
                        pointLights,
                        spotLights,
                        shadowBuffers,
                        gBuffer,
                        quadMesh);
        stageAnimationRender =
                new AnimationRender(shaders.getShader(RenderStage.Type.GUI), renderBuffers);
        stageFilterRender =
                new FilterRender(
                        shaders.getShader(RenderStage.Type.FILTER), screenTexture, quadMesh);
        stageScreenTextureBinding = new FramebufferTransition(screenTexture, GL_ONE, GL_ONE);
        stageBackBufferBinding =
                new FramebufferTransition(
                        new Framebuffer(0, cachedWidth, cachedHeight, new long[] {}),
                        GL_SRC_ALPHA,
                        GL_ONE_MINUS_SRC_ALPHA);
    }

    private Pipeline buildPipeline(final int configuration) {
        boolean renderingScene = false;
        List<RenderStage> stages = new ArrayList<>();
        if (RenderConfig.hasError(configuration)) {
            // TODO(ches) handle this
        }

        if (RenderConfig.hasAnimationStage(configuration)) {
            stages.add(stageAnimationRender);
            renderingScene = true;
        }
        if (RenderConfig.hasShadowStage(configuration)) {
            stages.add(stageShadowRender);
            renderingScene = true;
        }
        if (RenderConfig.hasSceneStage(configuration)) {
            if (RenderConfig.sceneIsWireframe(configuration)) {
                stages.add(stageSceneRenderWireframe);
            } else {
                stages.add(stageSceneRender);
            }
            renderingScene = true;
        }
        if (RenderConfig.hasFilterStage(configuration)) {
            // NOTE(ches) for post-processing we will need to render to a texture instead of the
            // back buffer
            stages.add(stageScreenTextureBinding);
        } else {
            stages.add(stageBackBufferBinding);
        }
        if (RenderConfig.hasLightingStage(configuration)) {
            stages.add(stageLightRender);
            renderingScene = true;
        }
        if (RenderConfig.hasSkyboxStage(configuration)) {
            stages.add(stageSkyboxRender);
            renderingScene = true;
        }
        if (RenderConfig.hasFilterStage(configuration)) {
            stages.add(stageBackBufferBinding);
            stages.add(stageFilterRender);
            renderingScene = true;
        }
        if (renderingScene) {
            stages.add(0, stageModelMatrixUpdate);
        }
        if (RenderConfig.hasGuiStage(configuration)) {
            if (renderingScene) {
                stages.add(stageGuiRender);
            } else {
                stages.add(stageGuiRenderStandalone);
            }
        }

        return new PipelineOpenGL(stages.toArray(new RenderStage[0]));
    }

    /** Clean up all the rendering resources. */
    public void cleanup() {
        GraphicsManager.getDeletionQueue().add(font);
        font = null;
        GraphicsManager.getDeletionQueue().add(defaultTexture);
        defaultTexture = null;
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
        renderBuffers.cleanup();
        commandBuffers.cleanup();
        deleteRenderBuffers();
    }

    private void createImGuiFont() {
        ImFontAtlas fontAtlas = ImGui.getIO().getFonts();
        ImInt width = new ImInt();
        ImInt height = new ImInt();
        ByteBuffer buf = fontAtlas.getTexDataAsRGBA32(width, height);
        font =
                GraphicsManager.getRenderInstance()
                        .getTextureLoader()
                        .load(buf, Format.R8G8B8A8_UINT, width.get(), height.get());
        fontAtlas.setTexID((int) font.id());
    }

    /** Initialize the lighting SSBOs and fill them with zeroes. */
    private void createLightBuffers() {
        int pointLightBuffer = glGenBuffers();

        /*
         * Position (vec3 + ignored), color (vec3), intensity (1), Attenuation
         * (3 + ignored), in that order.
         */
        final int POINT_LIGHT_SIZE = 4 + 3 + 1 + 4;
        FloatBuffer pointLightFloatBuffer =
                MemoryUtil.memAllocFloat(PipelineOpenGL.MAX_LIGHTS_SUPPORTED * POINT_LIGHT_SIZE);

        pointLightFloatBuffer
                .put(new float[PipelineOpenGL.MAX_LIGHTS_SUPPORTED * POINT_LIGHT_SIZE])
                .flip();

        glBindBufferBase(
                GL_SHADER_STORAGE_BUFFER, PipelineOpenGL.POINT_LIGHT_BINDING, pointLightBuffer);
        glBufferData(GL_SHADER_STORAGE_BUFFER, pointLightFloatBuffer, GL_STATIC_DRAW);

        MemoryUtil.memFree(pointLightFloatBuffer);
        pointLights = new Buffer(pointLightBuffer, Buffer.Type.SHADER_STORAGE);

        int spotLightBuffer = glGenBuffers();
        /*
         * Position (vec3 + ignored), color (vec3), intensity (1), Attenuation
         * (3 + ignored), cone direction (vec3), cutoff (1) in that order.
         */
        final int SPOT_LIGHT_SIZE = 4 + 3 + 1 + 4 + 3 + 1;
        FloatBuffer spotLightFloatBuffer =
                MemoryUtil.memAllocFloat(PipelineOpenGL.MAX_LIGHTS_SUPPORTED * SPOT_LIGHT_SIZE);

        spotLightFloatBuffer
                .put(new float[PipelineOpenGL.MAX_LIGHTS_SUPPORTED * POINT_LIGHT_SIZE])
                .flip();

        glBindBufferBase(
                GL_SHADER_STORAGE_BUFFER, PipelineOpenGL.SPOT_LIGHT_BINDING, spotLightBuffer);
        glBufferData(GL_SHADER_STORAGE_BUFFER, spotLightFloatBuffer, GL_STATIC_DRAW);

        MemoryUtil.memFree(spotLightFloatBuffer);
        spotLights = new Buffer(spotLightBuffer, Buffer.Type.SHADER_STORAGE);
    }

    private Framebuffer createShadowBuffers() {
        int depthMapFBO = glGenFramebuffers();

        int[] shadowTextures = new int[CascadeShadow.SHADOW_MAP_CASCADE_COUNT];

        glGenTextures(shadowTextures);

        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
            glBindTexture(GL_TEXTURE_2D, shadowTextures[i]);
            glTexImage2D(
                    GL_TEXTURE_2D,
                    0,
                    GL_DEPTH_COMPONENT,
                    CascadeShadow.SHADOW_MAP_WIDTH,
                    CascadeShadow.SHADOW_MAP_HEIGHT,
                    0,
                    GL_DEPTH_COMPONENT,
                    GL_FLOAT,
                    (ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_NONE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        }

        glBindFramebuffer(GL_FRAMEBUFFER, depthMapFBO);
        glFramebufferTexture2D(
                GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, shadowTextures[0], 0);

        // Set only depth
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RenderException(
                    SafeResourceLoader.getString(
                            "FRAME_BUFFER_CREATION_FAIL", GraphicsPlugin.getResourceBundle()));
        }

        // Unbind
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

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
        int gBufferId = glGenFramebuffers();

        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, gBufferId);

        int[] textures = new int[4];
        glGenTextures(textures);

        for (int i = 0; i < textures.length; ++i) {
            glBindTexture(GL_TEXTURE_2D, textures[i]);
            int attachmentType;
            if (i == textures.length - 1) {
                glTexImage2D(
                        GL_TEXTURE_2D,
                        0,
                        GL_DEPTH_COMPONENT32F,
                        cachedWidth,
                        cachedHeight,
                        0,
                        GL_DEPTH_COMPONENT,
                        GL_FLOAT,
                        (ByteBuffer) null);
                attachmentType = GL_DEPTH_ATTACHMENT;
            } else {
                glTexImage2D(
                        GL_TEXTURE_2D,
                        0,
                        GL_RGBA32F,
                        cachedWidth,
                        cachedHeight,
                        0,
                        GL_RGBA,
                        GL_FLOAT,
                        (ByteBuffer) null);
                attachmentType = GL_COLOR_ATTACHMENT0 + i;
            }
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

            glFramebufferTexture2D(GL_FRAMEBUFFER, attachmentType, GL_TEXTURE_2D, textures[i], 0);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer intBuff = stack.mallocInt(textures.length - 1);
            for (int i = 0; i < textures.length - 1; ++i) {
                intBuff.put(i, GL_COLOR_ATTACHMENT0 + i);
            }
            glDrawBuffers(intBuff);
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        long[] textureIds = Arrays.stream(textures).mapToLong(i -> (long) i).toArray();
        return new Framebuffer(gBufferId, cachedWidth, cachedHeight, textureIds);
    }

    /**
     * Generate new render buffers. {@link #deleteRenderBuffers()} should be called before doing
     * this if they already exist.
     */
    private void generateRenderBuffers() {
        int[] textures = new int[2];
        glGenTextures(textures);

        glBindTexture(GL_TEXTURE_2D, textures[0]);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(
                GL_TEXTURE_2D,
                0,
                GL_RGBA,
                cachedWidth,
                cachedHeight,
                0,
                GL_RGBA,
                GL_UNSIGNED_BYTE,
                0);

        glBindTexture(GL_TEXTURE_2D, textures[1]);
        glTexImage2D(
                GL_TEXTURE_2D,
                0,
                GL_DEPTH_COMPONENT16,
                cachedWidth,
                cachedHeight,
                0,
                GL_DEPTH_COMPONENT,
                GL_FLOAT,
                (ByteBuffer) null);

        int screenFBO = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, screenFBO);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textures[0], 0);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, textures[1], 0);

        glBindFramebuffer(GL_FRAMEBUFFER, 0);

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

    /**
     * Set up the command buffer for animated models.
     *
     * @param scene The scene to render.
     */
    private void setupAnimCommandBuffer(@NonNull Scene scene) {
        List<Model> modelList =
                scene.getModelMap().values().stream().filter(Model::isAnimated).toList();

        int numMeshes = 0;
        int totalEntities = 0;
        int drawElementCount = 0;
        for (Model model : modelList) {
            numMeshes += model.getMeshDataList().size();
            drawElementCount += model.getEntitiesList().size() * model.getMeshDataList().size();
            totalEntities += model.getEntitiesList().size();
        }

        Map<String, Integer> entitiesIndexMap = new HashMap<>();

        // currently contains the size of the list of entities
        FloatBuffer modelMatrices = MemoryUtil.memAllocFloat(totalEntities * MODEL_MATRIX_SIZE);

        int entityIndex = 0;
        for (Model model : modelList) {
            List<Entity> entities = model.getEntitiesList();
            for (Entity entity : entities) {
                entity.getModelMatrix().get(entityIndex * MODEL_MATRIX_SIZE, modelMatrices);
                entitiesIndexMap.put(entity.getEntityID(), entityIndex);
                entityIndex++;
            }
        }
        int modelMatrixBuffer = glGenBuffers();
        commandBuffers.setAnimatedModelMatricesBuffer(modelMatrixBuffer);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, modelMatrixBuffer);
        glBufferData(GL_SHADER_STORAGE_BUFFER, modelMatrices, GL_STATIC_DRAW);
        MemoryUtil.memFree(modelMatrices);

        ByteBuffer commandBuffer = MemoryUtil.memAlloc(numMeshes * COMMAND_SIZE);
        ByteBuffer drawElements = MemoryUtil.memAlloc(drawElementCount * DRAW_ELEMENT_SIZE);
        // TODO(ches) fill out command buffers
        commandBuffer.flip();
        drawElements.flip();

        commandBuffers.setAnimatedDrawCount(commandBuffer.remaining() / COMMAND_SIZE);

        int animRenderBufferHandle = glGenBuffers();
        commandBuffers.setAnimatedCommandBuffer(animRenderBufferHandle);
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, animRenderBufferHandle);
        glBufferData(GL_DRAW_INDIRECT_BUFFER, commandBuffer, GL_DYNAMIC_DRAW);
        MemoryUtil.memFree(commandBuffer);

        int drawElementBuffer = glGenBuffers();
        commandBuffers.setAnimatedDrawElementBuffer(drawElementBuffer);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, drawElementBuffer);
        glBufferData(GL_SHADER_STORAGE_BUFFER, drawElements, GL_STATIC_DRAW);
        MemoryUtil.memFree(drawElements);
    }

    /**
     * Set up model data before rendering.
     *
     * @param scene The scene to read models from.
     */
    @Deprecated
    public void setupData(@NonNull Scene scene) {
        // TODO(ches) remove this
        if (buffersPopulated.getAndSet(false)) {
            renderBuffers.cleanup();
            commandBuffers.cleanup();
        }
        renderBuffers.loadStaticModels(scene);
        renderBuffers.loadAnimatedModels(scene);
        setupAnimCommandBuffer(scene);
        setupStaticCommandBuffer(scene);
        buffersPopulated.set(true);
    }

    /**
     * Set up the command buffer for static models.
     *
     * @param scene The scene to render.
     */
    private void setupStaticCommandBuffer(@NonNull Scene scene) {
        List<Model> modelList =
                scene.getModelMap().values().stream().filter(m -> !m.isAnimated()).toList();

        int numMeshes = 0;
        int totalEntities = 0;
        int drawElementCount = 0;
        for (Model model : modelList) {
            numMeshes += model.getMeshDataList().size();
            drawElementCount += model.getEntitiesList().size() * model.getMeshDataList().size();
            totalEntities += model.getEntitiesList().size();
        }

        Map<String, Integer> entitiesIndexMap = new HashMap<>();

        // currently contains the size of the list of entities
        FloatBuffer modelMatrices = MemoryUtil.memAllocFloat(totalEntities * MODEL_MATRIX_SIZE);

        int entityIndex = 0;
        for (Model model : modelList) {
            List<Entity> entities = model.getEntitiesList();
            for (Entity entity : entities) {
                entity.getModelMatrix().get(entityIndex * MODEL_MATRIX_SIZE, modelMatrices);
                entitiesIndexMap.put(entity.getEntityID(), entityIndex);
                entityIndex++;
            }
        }
        int modelMatrixBuffer = glGenBuffers();
        commandBuffers.setStaticModelMatricesBuffer(modelMatrixBuffer);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, modelMatrixBuffer);
        glBufferData(GL_SHADER_STORAGE_BUFFER, modelMatrices, GL_STATIC_DRAW);
        MemoryUtil.memFree(modelMatrices);

        ByteBuffer commandBuffer = MemoryUtil.memAlloc(numMeshes * COMMAND_SIZE);
        ByteBuffer drawElements = MemoryUtil.memAlloc(drawElementCount * DRAW_ELEMENT_SIZE);

        // TODO(ches) fill out command buffers

        commandBuffer.flip();
        drawElements.flip();

        commandBuffers.setStaticDrawCount(commandBuffer.remaining() / COMMAND_SIZE);

        int staticRenderBufferHandle = glGenBuffers();
        commandBuffers.setStaticCommandBuffer(staticRenderBufferHandle);
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, staticRenderBufferHandle);
        glBufferData(GL_DRAW_INDIRECT_BUFFER, commandBuffer, GL_DYNAMIC_DRAW);
        MemoryUtil.memFree(commandBuffer);

        int drawElementBuffer = glGenBuffers();
        commandBuffers.setStaticDrawElementBuffer(drawElementBuffer);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, drawElementBuffer);
        glBufferData(GL_SHADER_STORAGE_BUFFER, drawElements, GL_STATIC_DRAW);
        MemoryUtil.memFree(drawElements);
    }
}
