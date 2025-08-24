package com.ikalagaming.graphics.backend.opengl.stages;

import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL43.*;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.opengl.CommandBuffer;
import com.ikalagaming.graphics.backend.opengl.RenderBuffers;
import com.ikalagaming.graphics.frontend.*;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.Setter;

/** Handles rendering of scene geometry to the g-buffer. */
public class SceneRender implements RenderStage {

    /** The shader to use for rendering. */
    @NonNull @Setter private Shader shader;

    /** The buffers for indirect drawing of models. */
    private final RenderBuffers renderBuffers;

    /** The g-buffer for rendering geometry to. */
    @Setter @NonNull private Framebuffer gBuffer;

    /** The scene object rendering command buffers. */
    @Setter @NonNull private CommandBuffer commandBuffers;

    @Setter @NonNull private Texture defaultTexture;

    /**
     * Set up the shadow render stage.
     *
     * @param shader The shader to use for rendering.
     * @param renderBuffers The buffers for indirect drawing of models.
     * @param gBuffer The depth map buffers.
     * @param commandBuffers The rendering command buffers.
     */
    public SceneRender(
            final @NonNull Shader shader,
            final @NonNull RenderBuffers renderBuffers,
            final @NonNull Framebuffer gBuffer,
            final @NonNull CommandBuffer commandBuffers,
            final @NonNull Texture defaultTexture) {
        this.shader = shader;
        this.renderBuffers = renderBuffers;
        this.gBuffer = gBuffer;
        this.commandBuffers = commandBuffers;
        this.defaultTexture = defaultTexture;
    }

    /**
     * Compute animation transformations for all animated models in the scene.
     *
     * @param scene The scene we are rendering.
     */
    public void render(Scene scene) {
        commonSceneRender(scene, shader, renderBuffers, gBuffer, commandBuffers, defaultTexture);
    }

    /**
     * Common rendering code for the scene, shared between stages.
     *
     * @param scene The scene we are rendering.
     * @param shader The shader to use for rendering.
     * @param renderBuffers The buffers for indirect drawing of models.
     * @param gBuffer The depth map buffers.
     * @param commandBuffers The rendering command buffers.
     */
    static void commonSceneRender(
            Scene scene,
            Shader shader,
            RenderBuffers renderBuffers,
            Framebuffer gBuffer,
            CommandBuffer commandBuffers,
            Texture defaultTexture) {
        // TODO(ches) fill out materials uniform buffer if dirty

        var uniformsMap = shader.getUniformMap();
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, (int) gBuffer.id());
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glViewport(0, 0, gBuffer.width(), gBuffer.height());
        glDisable(GL_BLEND);
        shader.bind();

        uniformsMap.setUniform(
                ShaderUniforms.Scene.PROJECTION_MATRIX,
                scene.getProjection().getProjectionMatrix());
        uniformsMap.setUniform(ShaderUniforms.Scene.VIEW_MATRIX, scene.getCamera().getViewMatrix());

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, (int) defaultTexture.id());

        int nextTexture = 1;
        for (Material material : scene.getMaterialCache().getMaterialsList()) {
            if (material.getNormalMap() != null) {
                glActiveTexture(GL_TEXTURE0 + nextTexture);
                glBindTexture(GL_TEXTURE_2D, (int) material.getNormalMap().id());
                uniformsMap.setUniform(
                        ShaderUniforms.Scene.TEXTURE_SAMPLER + "[" + nextTexture + "]",
                        nextTexture);
                ++nextTexture;
            }
            if (material.getTexture() != null) {
                glActiveTexture(GL_TEXTURE0 + nextTexture);
                glBindTexture(GL_TEXTURE_2D, (int) material.getTexture().id());
                uniformsMap.setUniform(
                        ShaderUniforms.Scene.TEXTURE_SAMPLER + "[" + nextTexture + "]",
                        nextTexture);
                ++nextTexture;
            }
        }

        // Static meshes
        if (commandBuffers.getStaticDrawCount() > 0) {
            glBindBufferBase(
                    GL_SHADER_STORAGE_BUFFER,
                    ShadowRender.DRAW_ELEMENT_BINDING,
                    commandBuffers.getStaticDrawElementBuffer());
            glBindBufferBase(
                    GL_SHADER_STORAGE_BUFFER,
                    ShadowRender.MODEL_MATRICES_BINDING,
                    commandBuffers.getStaticModelMatricesBuffer());
            glBindBuffer(GL_DRAW_INDIRECT_BUFFER, commandBuffers.getStaticCommandBuffer());
            glBindVertexArray(renderBuffers.getStaticVaoID());
            glMultiDrawElementsIndirect(
                    GL_TRIANGLES, GL_UNSIGNED_INT, 0, commandBuffers.getStaticDrawCount(), 0);
        }

        // Animated meshes
        if (commandBuffers.getAnimatedDrawCount() > 0) {
            glBindBufferBase(
                    GL_SHADER_STORAGE_BUFFER,
                    ShadowRender.DRAW_ELEMENT_BINDING,
                    commandBuffers.getAnimatedDrawElementBuffer());
            glBindBufferBase(
                    GL_SHADER_STORAGE_BUFFER,
                    ShadowRender.MODEL_MATRICES_BINDING,
                    commandBuffers.getAnimatedModelMatricesBuffer());
            glBindBuffer(GL_DRAW_INDIRECT_BUFFER, commandBuffers.getAnimatedCommandBuffer());
            glBindVertexArray(renderBuffers.getAnimVaoID());
            glMultiDrawElementsIndirect(
                    GL_TRIANGLES, GL_UNSIGNED_INT, 0, commandBuffers.getAnimatedDrawCount(), 0);
        }

        glBindVertexArray(0);
        glEnable(GL_BLEND);
        shader.unbind();
    }
}
