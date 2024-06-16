package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_MULTISAMPLE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT16;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.glDrawBuffers;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.Window;
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

/** Renders things on the screen. */
public class RendererOpenGL implements Renderer {

    /** The size of a draw command. */
    private static final int COMMAND_SIZE = 5 * 4;

    /** The size of a draw element. */
    private static final int DRAW_ELEMENT_SIZE = 2 * 4;

    /** The size of a model matrix. */
    private static final int MODEL_MATRIX_SIZE = 4 * 4;

    /** The binding for the point light SSBO. */
    public static final int POINT_LIGHT_BINDING = 0;

    /** The binding for the spotlight SSBO. */
    public static final int SPOT_LIGHT_BINDING = 1;

    /** How many lights of each type (spot, point) that are currently supported. */
    public static final int MAX_LIGHTS_SUPPORTED = 1000;

    /** The animation render handler. */
    private RenderStage animationRender;

    /** Geometry buffer. */
    private Framebuffer gBuffer;

    /** The GUI render handler. */
    private RenderStage guiRenderStandalone;

    private RenderStage guiRenderRegular;

    /** The lights render handler. */
    private RenderStage lightRender;

    /** The buffers for indirect drawing of models. */
    private RenderBuffers renderBuffers;

    /** The scene render handler. */
    private RenderStage sceneRender;

    /** The shadow render handler. */
    private RenderStage shadowRender;

    /** The skybox render handler. */
    private SkyBoxRender skyBoxRender;

    /** Render a filter on top of the final result. */
    private FilterRender filterRender;

    private RenderStage screenTextureBinding;

    private RenderStage backBufferBinding;

    /**
     * Whether we have set up the buffers for the scene. If we have, but need to set data up for the
     * scene again, we will need to clear them out and start over.
     */
    private final AtomicBoolean buffersPopulated;

    /** The buffers for the batches. */
    CommandBuffer commandBuffers;

    /** The Frame Buffer Object for the pre-filter render target. */
    private int screenFBO;

    /** The depth RBO for the pre-filter render target. */
    private int screenRBODepth;

    /** The texture ID we render to before applying filters. */
    private int screenTexture;

    /** The width of the drawable area in pixels. */
    private int cachedWidth;

    /** The width of the drawable area in pixels. */
    private int cachedHeight;

    /** The cascade shadow map. */
    private final ArrayList<CascadeShadow> cascadeShadows;

    /** The depth map for shadows. */
    private Framebuffer shadowBuffers;

    /** The mesh to render. */
    private GuiMesh guiMesh;

    /** The texture we store font atlas on. */
    private Texture font;

    /** The texture to use by default if nothing is provided for a model. */
    private Texture defaultTexture;

    private SkyboxModel skybox;

    /** The buffer to use for storing point light info. */
    private Buffer pointLights;

    /** The buffer to use for storing spotlight info. */
    private Buffer spotLights;

    /** A mesh for rendering onto. */
    private QuadMesh quadMesh;

    /** The list of render stages that this renderer uses. */
    private RenderStage[] renderStages;

    /** Set up a new rendering pipeline. */
    public RendererOpenGL() {
        buffersPopulated = new AtomicBoolean();
        cascadeShadows = new ArrayList<>();
        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
            cascadeShadows.add(new CascadeShadow());
        }
    }

    @Override
    public void initialize(@NonNull Window window, @NonNull ShaderMap shaders) {
        glEnable(GL_MULTISAMPLE);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);

        // Support for transparencies
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        cachedWidth = window.getWidth();
        cachedHeight = window.getHeight();

        gBuffer = generateGBuffer();
        renderBuffers = new RenderBuffers();
        generateRenderBuffers();
        commandBuffers = new CommandBuffer();
        createImGuiFont();
        shadowBuffers = createShadowBuffers();
        skybox = SkyboxModel.create();
        quadMesh = QuadMesh.getInstance();
        createLightBuffers();

        final String defaultTexturePath =
                PluginFolder.getResource(
                                GraphicsPlugin.PLUGIN_NAME,
                                PluginFolder.ResourceType.DATA,
                                ConfigManager.loadConfig(GraphicsPlugin.PLUGIN_NAME)
                                        .getString("default-texture"))
                        .getAbsolutePath();

        defaultTexture =
                GraphicsManager.getRenderInstance().getTextureLoader().load(defaultTexturePath);

        sceneRender =
                new SceneRender(
                        shaders.getShader(RenderStage.Type.SCENE),
                        renderBuffers,
                        gBuffer,
                        commandBuffers,
                        defaultTexture);
        guiMesh = GuiMesh.create();
        guiRenderStandalone =
                new GuiRenderStandalone(shaders.getShader(RenderStage.Type.GUI), guiMesh);
        guiRenderRegular = new GuiRender(shaders.getShader(RenderStage.Type.GUI), guiMesh);
        skyBoxRender = new SkyBoxRender();
        shadowRender =
                new ShadowRender(
                        shaders.getShader(RenderStage.Type.SHADOW),
                        renderBuffers,
                        cascadeShadows,
                        shadowBuffers,
                        commandBuffers);
        lightRender =
                new LightRender(
                        shaders.getShader(RenderStage.Type.LIGHT),
                        cascadeShadows,
                        pointLights,
                        spotLights,
                        shadowBuffers,
                        gBuffer,
                        quadMesh);
        animationRender =
                new AnimationRender(shaders.getShader(RenderStage.Type.GUI), renderBuffers);
        filterRender = new FilterRender();

        screenTextureBinding = new FramebufferTransition(screenFBO, screenRBODepth, GL_ONE, GL_ONE);
        backBufferBinding = new FramebufferTransition(0, 0, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
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
                MemoryUtil.memAllocFloat(MAX_LIGHTS_SUPPORTED * POINT_LIGHT_SIZE);

        pointLightFloatBuffer.put(new float[MAX_LIGHTS_SUPPORTED * POINT_LIGHT_SIZE]).flip();

        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, POINT_LIGHT_BINDING, pointLightBuffer);
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
                MemoryUtil.memAllocFloat(MAX_LIGHTS_SUPPORTED * SPOT_LIGHT_SIZE);

        spotLightFloatBuffer.put(new float[MAX_LIGHTS_SUPPORTED * POINT_LIGHT_SIZE]).flip();

        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, SPOT_LIGHT_BINDING, spotLightBuffer);
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

    @Override
    public void cleanup() {
        GraphicsManager.getDeletionQueue().add(font);
        font = null;
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

    /** Free up the current render buffers. */
    void deleteRenderBuffers() {
        glDeleteRenderbuffers(screenRBODepth);
        glDeleteFramebuffers(screenFBO);
        glDeleteTextures(screenTexture);
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
        glActiveTexture(GL_TEXTURE0);
        screenTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, screenTexture);
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
        glBindTexture(GL_TEXTURE_2D, 0);

        screenRBODepth = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, screenRBODepth);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT16, cachedWidth, cachedHeight);
        glBindRenderbuffer(GL_RENDERBUFFER, 0);

        screenFBO = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, screenFBO);
        glFramebufferTexture2D(
                GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, screenTexture, 0);
        glFramebufferRenderbuffer(
                GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, screenRBODepth);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    @Override
    public void render(@NonNull Scene scene, @NonNull ShaderMap shaders) {
        if (Renderer.configuration.isRenderingScene()) {
            updateModelMatrices(scene);

            animationRender.render(scene);
            shadowRender.render(scene);
            sceneRender.render(scene);

            screenTextureBinding.render(scene);

            lightRender.render(scene);
            skyBoxRender.render(scene, shaders.getShader(RenderStage.Type.SKYBOX), skybox);

            backBufferBinding.render(scene);

            filterRender.render(
                    screenTexture, shaders.getShader(RenderStage.Type.FILTER), quadMesh);
            guiRenderRegular.render(scene);
        } else {
            guiRenderStandalone.render(scene);
        }
    }

    @Override
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
            numMeshes += model.getMeshDrawDataList().size();
            drawElementCount += model.getEntitiesList().size() * model.getMeshDrawDataList().size();
            totalEntities += model.getEntitiesList().size();
        }

        Map<String, Integer> entitiesIndexMap = new HashMap<>();

        // currently contains the size of the list of entities
        FloatBuffer modelMatrices =
                MemoryUtil.memAllocFloat(totalEntities * RendererOpenGL.MODEL_MATRIX_SIZE);

        int entityIndex = 0;
        for (Model model : modelList) {
            List<Entity> entities = model.getEntitiesList();
            for (Entity entity : entities) {
                entity.getModelMatrix()
                        .get(entityIndex * RendererOpenGL.MODEL_MATRIX_SIZE, modelMatrices);
                entitiesIndexMap.put(entity.getEntityID(), entityIndex);
                entityIndex++;
            }
        }
        int modelMatrixBuffer = glGenBuffers();
        commandBuffers.setAnimatedModelMatricesBuffer(modelMatrixBuffer);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, modelMatrixBuffer);
        glBufferData(GL_SHADER_STORAGE_BUFFER, modelMatrices, GL_STATIC_DRAW);
        MemoryUtil.memFree(modelMatrices);

        int firstIndex = 0;
        int baseInstance = 0;
        ByteBuffer commandBuffer = MemoryUtil.memAlloc(numMeshes * RendererOpenGL.COMMAND_SIZE);
        ByteBuffer drawElements =
                MemoryUtil.memAlloc(drawElementCount * RendererOpenGL.DRAW_ELEMENT_SIZE);
        for (Model model : modelList) {
            for (RenderBuffers.MeshDrawData meshDrawData : model.getMeshDrawDataList()) {
                // count
                commandBuffer.putInt(meshDrawData.vertices());
                // instanceCount
                commandBuffer.putInt(1);
                commandBuffer.putInt(firstIndex);
                // baseVertex
                commandBuffer.putInt(meshDrawData.offset());
                commandBuffer.putInt(baseInstance);

                firstIndex += meshDrawData.vertices();
                baseInstance++;

                Entity entity = meshDrawData.animMeshDrawData().entity();
                // model matrix index
                drawElements.putInt(entitiesIndexMap.get(entity.getEntityID()));
                drawElements.putInt(meshDrawData.materialIndex());
            }
        }
        commandBuffer.flip();
        drawElements.flip();

        commandBuffers.setAnimatedDrawCount(
                commandBuffer.remaining() / RendererOpenGL.COMMAND_SIZE);

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

    @Override
    public void setupData(@NonNull Scene scene, @NonNull ShaderMap shaders) {
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
            numMeshes += model.getMeshDrawDataList().size();
            drawElementCount += model.getEntitiesList().size() * model.getMeshDrawDataList().size();
            totalEntities += model.getEntitiesList().size();
        }

        Map<String, Integer> entitiesIndexMap = new HashMap<>();

        // currently contains the size of the list of entities
        FloatBuffer modelMatrices =
                MemoryUtil.memAllocFloat(totalEntities * RendererOpenGL.MODEL_MATRIX_SIZE);

        int entityIndex = 0;
        for (Model model : modelList) {
            List<Entity> entities = model.getEntitiesList();
            for (Entity entity : entities) {
                entity.getModelMatrix()
                        .get(entityIndex * RendererOpenGL.MODEL_MATRIX_SIZE, modelMatrices);
                entitiesIndexMap.put(entity.getEntityID(), entityIndex);
                entityIndex++;
            }
        }
        int modelMatrixBuffer = glGenBuffers();
        commandBuffers.setStaticModelMatricesBuffer(modelMatrixBuffer);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, modelMatrixBuffer);
        glBufferData(GL_SHADER_STORAGE_BUFFER, modelMatrices, GL_STATIC_DRAW);
        MemoryUtil.memFree(modelMatrices);

        int firstIndex = 0;
        int baseInstance = 0;
        ByteBuffer commandBuffer = MemoryUtil.memAlloc(numMeshes * RendererOpenGL.COMMAND_SIZE);
        ByteBuffer drawElements =
                MemoryUtil.memAlloc(drawElementCount * RendererOpenGL.DRAW_ELEMENT_SIZE);

        for (Model model : modelList) {
            List<Entity> entities = model.getEntitiesList();
            int numEntities = entities.size();
            for (RenderBuffers.MeshDrawData meshDrawData : model.getMeshDrawDataList()) {
                // count
                commandBuffer.putInt(meshDrawData.vertices());
                // instanceCount
                commandBuffer.putInt(numEntities);
                commandBuffer.putInt(firstIndex);
                // baseVertex
                commandBuffer.putInt(meshDrawData.offset());
                commandBuffer.putInt(baseInstance);

                firstIndex += meshDrawData.vertices();
                baseInstance += numEntities;

                int materialIndex = meshDrawData.materialIndex();
                for (Entity entity : entities) {
                    // model matrix index
                    drawElements.putInt(entitiesIndexMap.get(entity.getEntityID()));
                    drawElements.putInt(materialIndex);
                }
            }
        }

        commandBuffer.flip();
        drawElements.flip();

        commandBuffers.setStaticDrawCount(commandBuffer.remaining() / RendererOpenGL.COMMAND_SIZE);

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

    /**
     * Take a list of models and upload the model matrices to the appropriate buffer.
     *
     * @param models The list of models.
     * @param bufferID The opengl ID of the buffer we want to buffer data to.
     */
    private void updateModelBuffer(List<Model> models, int bufferID) {
        int totalEntities = 0;
        for (Model model : models) {
            totalEntities += model.getEntitiesList().size();
        }

        // currently contains the size of the list of entities
        FloatBuffer modelMatrices =
                MemoryUtil.memAllocFloat(totalEntities * RendererOpenGL.MODEL_MATRIX_SIZE);

        int entityIndex = 0;
        for (Model model : models) {
            List<Entity> entities = model.getEntitiesList();
            for (Entity entity : entities) {
                entity.getModelMatrix()
                        .get(entityIndex * RendererOpenGL.MODEL_MATRIX_SIZE, modelMatrices);
                entityIndex++;
            }
        }
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, bufferID);
        glBufferData(GL_SHADER_STORAGE_BUFFER, modelMatrices, GL_STATIC_DRAW);
        MemoryUtil.memFree(modelMatrices);
    }

    /**
     * Update the model matrices buffers for all the scene objects.
     *
     * @param scene The scene we are rendering.
     */
    private void updateModelMatrices(@NonNull Scene scene) {
        List<Model> animatedList =
                scene.getModelMap().values().stream().filter(Model::isAnimated).toList();
        int animatedBuffer = commandBuffers.getAnimatedModelMatricesBuffer();
        updateModelBuffer(animatedList, animatedBuffer);

        List<Model> staticList =
                scene.getModelMap().values().stream().filter(m -> !m.isAnimated()).toList();
        int staticBuffer = commandBuffers.getStaticModelMatricesBuffer();
        updateModelBuffer(staticList, staticBuffer);
    }
}
