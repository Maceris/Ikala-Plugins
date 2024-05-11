/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_MULTISAMPLE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT16;
import static org.lwjgl.opengl.GL14.GL_FUNC_ADD;
import static org.lwjgl.opengl.GL14.glBlendEquation;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_RENDERBUFFER;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glBindRenderbuffer;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30.glDeleteRenderbuffers;
import static org.lwjgl.opengl.GL30.glFramebufferRenderbuffer;
import static org.lwjgl.opengl.GL30.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30.glGenFramebuffers;
import static org.lwjgl.opengl.GL30.glGenRenderbuffers;
import static org.lwjgl.opengl.GL30.glRenderbufferStorage;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.GBufferHandler;
import com.ikalagaming.graphics.backend.base.QuadMeshHandler;
import com.ikalagaming.graphics.frontend.Pipeline;
import com.ikalagaming.graphics.frontend.TextureLoader;
import com.ikalagaming.graphics.graph.GBuffer;
import com.ikalagaming.graphics.graph.Model;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Renders things on the screen. */
public class PipelineOpenGL implements Pipeline {

    /** The size of a draw command. */
    private static final int COMMAND_SIZE = 5 * 4;

    /** The size of a draw element. */
    private static final int DRAW_ELEMENT_SIZE = 2 * 4;

    /** The size of a model matrix. */
    private static final int MODEL_MATRIX_SIZE = 4 * 4;

    /** The binding for the draw elements buffer SSBO. */
    static final int DRAW_ELEMENT_BINDING = 1;

    /** The binding for the model matrices buffer SSBO. */
    static final int MODEL_MATRICES_BINDING = 2;

    /** The animation render handler. */
    private AnimationRender animationRender;

    /** Geometry buffer. */
    private GBuffer gBuffer;

    /** The GUI render handler. */
    private GuiRender guiRender;

    /** The lights render handler. */
    private LightRender lightRender;

    /** The buffers for indirect drawing of models. */
    private RenderBuffers renderBuffers;

    /** The scene render handler. */
    private SceneRender sceneRender;

    /** The shadow render handler. */
    private ShadowRender shadowRender;

    /** The skybox render handler. */
    private SkyBoxRender skyBoxRender;

    /** Render a filter on top of the final result. */
    private FilterRender filterRender;

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

    private TextureLoader textureLoader;
    private QuadMeshHandler quadMeshHandler;
    private GBufferHandler gBufferHandler;

    /** Set up a new rendering pipeline. */
    public PipelineOpenGL() {
        buffersPopulated = new AtomicBoolean();
    }

    @Override
    public void initialize(@NonNull Window window) {
        GL.createCapabilities();
        glEnable(GL_MULTISAMPLE);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);

        // Support for transparencies
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        textureLoader = new TextureLoaderOpenGL();
        quadMeshHandler = new QuadMeshHandlerOpenGL();
        gBufferHandler = new GBufferHandlerOpenGL();

        sceneRender = new SceneRender(textureLoader);
        guiRender = new GuiRender(window, textureLoader);
        skyBoxRender = new SkyBoxRender();
        shadowRender = new ShadowRender();
        lightRender = new LightRender(quadMeshHandler);
        animationRender = new AnimationRender();
        filterRender = new FilterRender(quadMeshHandler);
        gBuffer = new GBuffer();
        gBufferHandler.initialize(gBuffer, window);
        renderBuffers = new RenderBuffers();
        commandBuffers = new CommandBuffer();

        generateRenderBuffers(window.getWidth(), window.getHeight());
    }

    @Override
    public void cleanup() {
        sceneRender.cleanup();
        guiRender.cleanup();
        skyBoxRender.cleanup();
        shadowRender.cleanup();
        lightRender.cleanup(quadMeshHandler);
        animationRender.cleanup();
        filterRender.cleanup(quadMeshHandler);
        gBufferHandler.cleanup(gBuffer);
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
     * Generate new render buffers. {@link #deleteRenderBuffers()} should be called before doing
     * this if they already exist.
     *
     * @param width The width of the window/buffers in pixels.
     * @param height The height of the window/buffers in pixels.
     */
    private void generateRenderBuffers(int width, int height) {
        glActiveTexture(GL_TEXTURE0);
        screenTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, screenTexture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);
        glBindTexture(GL_TEXTURE_2D, 0);

        screenRBODepth = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, screenRBODepth);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT16, width, height);
        glBindRenderbuffer(GL_RENDERBUFFER, 0);

        screenFBO = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, screenFBO);
        glFramebufferTexture2D(
                GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, screenTexture, 0);
        glFramebufferRenderbuffer(
                GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, screenRBODepth);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /** Reset the blending after we are done with the light rendering. */
    private void lightRenderFinish() {
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glBindRenderbuffer(GL_RENDERBUFFER, 0);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    /**
     * Set up for rendering lights.
     *
     * @param window The window we are rendering in.
     */
    private void lightRenderStart(@NonNull Window window) {
        glBindFramebuffer(GL_FRAMEBUFFER, screenFBO);
        glBindRenderbuffer(GL_RENDERBUFFER, screenRBODepth);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, screenTexture);

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glViewport(0, 0, window.getWidth(), window.getHeight());

        glEnable(GL_BLEND);
        glBlendEquation(GL_FUNC_ADD);
        glBlendFunc(GL_ONE, GL_ONE);
    }

    @Override
    public void render(@NonNull Window window, @NonNull Scene scene) {
        if (Pipeline.configuration.isRenderingScene()) {
            updateModelMatrices(scene);

            animationRender.render(scene, renderBuffers);
            shadowRender.render(scene, renderBuffers, commandBuffers);

            if (Pipeline.configuration.isWireframe()) {
                glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
                glDisable(GL_TEXTURE_2D);
            }

            sceneRender.render(scene, renderBuffers, gBuffer, commandBuffers);

            if (Pipeline.configuration.isWireframe()) {
                glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
                glEnable(GL_TEXTURE_2D);
            }
            lightRenderStart(window);
            lightRender.render(scene, shadowRender, gBuffer);
            skyBoxRender.render(scene);
            lightRenderFinish();

            filterRender.render(screenTexture);
        } else {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, screenTexture);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            glViewport(0, 0, window.getWidth(), window.getHeight());
        }
        guiRender.render(window);
    }

    @Override
    public void resize(int width, int height) {
        deleteRenderBuffers();
        generateRenderBuffers(width, height);
        guiRender.resize(width, height);
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
                MemoryUtil.memAllocFloat(totalEntities * PipelineOpenGL.MODEL_MATRIX_SIZE);

        int entityIndex = 0;
        for (Model model : modelList) {
            List<Entity> entities = model.getEntitiesList();
            for (Entity entity : entities) {
                entity.getModelMatrix()
                        .get(entityIndex * PipelineOpenGL.MODEL_MATRIX_SIZE, modelMatrices);
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
        ByteBuffer commandBuffer = MemoryUtil.memAlloc(numMeshes * PipelineOpenGL.COMMAND_SIZE);
        ByteBuffer drawElements =
                MemoryUtil.memAlloc(drawElementCount * PipelineOpenGL.DRAW_ELEMENT_SIZE);
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
                commandBuffer.remaining() / PipelineOpenGL.COMMAND_SIZE);

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
    public void setupData(@NonNull Scene scene) {
        if (buffersPopulated.getAndSet(false)) {
            renderBuffers.cleanup();
            commandBuffers.cleanup();
        }
        renderBuffers.loadStaticModels(scene);
        renderBuffers.loadAnimatedModels(scene);
        sceneRender.recalculateMaterials(scene);
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
                MemoryUtil.memAllocFloat(totalEntities * PipelineOpenGL.MODEL_MATRIX_SIZE);

        int entityIndex = 0;
        for (Model model : modelList) {
            List<Entity> entities = model.getEntitiesList();
            for (Entity entity : entities) {
                entity.getModelMatrix()
                        .get(entityIndex * PipelineOpenGL.MODEL_MATRIX_SIZE, modelMatrices);
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
        ByteBuffer commandBuffer = MemoryUtil.memAlloc(numMeshes * PipelineOpenGL.COMMAND_SIZE);
        ByteBuffer drawElements =
                MemoryUtil.memAlloc(drawElementCount * PipelineOpenGL.DRAW_ELEMENT_SIZE);

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

        commandBuffers.setStaticDrawCount(commandBuffer.remaining() / PipelineOpenGL.COMMAND_SIZE);

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
                MemoryUtil.memAllocFloat(totalEntities * PipelineOpenGL.MODEL_MATRIX_SIZE);

        int entityIndex = 0;
        for (Model model : models) {
            List<Entity> entities = model.getEntitiesList();
            for (Entity entity : entities) {
                entity.getModelMatrix()
                        .get(entityIndex * PipelineOpenGL.MODEL_MATRIX_SIZE, modelMatrices);
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
