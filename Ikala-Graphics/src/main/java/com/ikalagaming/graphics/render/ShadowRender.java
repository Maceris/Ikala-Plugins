/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.render;

import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43.glMultiDrawElementsIndirect;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.graph.CascadeShadow;
import com.ikalagaming.graphics.graph.RenderBuffers;
import com.ikalagaming.graphics.graph.ShaderProgram;
import com.ikalagaming.graphics.graph.ShadowBuffer;
import com.ikalagaming.graphics.graph.UniformsMap;
import com.ikalagaming.graphics.scene.Scene;

import lombok.Getter;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/** Handles rendering for shadows. */
public class ShadowRender {
    /**
     * The cascade shadow map.
     *
     * @return The cascade shadows.
     */
    @Getter private ArrayList<CascadeShadow> cascadeShadows;

    /** The shader program for the shadow rendering. */
    private ShaderProgram shaderProgram;

    /**
     * The buffer we render shadows to.
     *
     * @return The shadow buffer.
     */
    @Getter private ShadowBuffer shadowBuffer;

    /** The uniform map for the shader program. */
    private UniformsMap uniformsMap;

    /** Set up a new shadow renderer. */
    public ShadowRender() {
        List<ShaderProgram.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new ShaderProgram.ShaderModuleData("shaders/shadow.vert", GL_VERTEX_SHADER));
        shaderProgram = new ShaderProgram(shaderModuleDataList);

        shadowBuffer = new ShadowBuffer();

        cascadeShadows = new ArrayList<>();
        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
            cascadeShadows.add(new CascadeShadow());
        }

        createUniforms();
    }

    /** Clean up buffers and shaders. */
    public void cleanup() {
        shaderProgram.cleanup();
        shadowBuffer.cleanup();
    }

    /** Create the uniforms for the shadow shader. */
    private void createUniforms() {
        uniformsMap = new UniformsMap(shaderProgram.getProgramID());
        uniformsMap.createUniform(ShaderUniforms.Shadow.PROJECTION_VIEW_MATRIX);
    }

    /**
     * Render the shadows for the scene.
     *
     * @param scene The scene we are rendering.
     * @param renderBuffers The buffers for indirect drawing of models.
     * @param commandBuffers The rendering command buffers.
     */
    public void render(
            @NonNull Scene scene,
            @NonNull RenderBuffers renderBuffers,
            @NonNull CommandBuffer commandBuffers) {
        CascadeShadow.updateCascadeShadows(cascadeShadows, scene);

        glBindFramebuffer(GL_FRAMEBUFFER, shadowBuffer.getDepthMapFBO());
        glViewport(0, 0, ShadowBuffer.SHADOW_MAP_WIDTH, ShadowBuffer.SHADOW_MAP_HEIGHT);

        shaderProgram.bind();

        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
            glFramebufferTexture2D(
                    GL_FRAMEBUFFER,
                    GL_DEPTH_ATTACHMENT,
                    GL_TEXTURE_2D,
                    shadowBuffer.getDepthMap().getIDs()[i],
                    0);
            glClear(GL_DEPTH_BUFFER_BIT);
        }

        // Static meshes
        glBindBufferBase(
                GL_SHADER_STORAGE_BUFFER,
                Render.DRAW_ELEMENT_BINDING,
                commandBuffers.getStaticDrawElementBuffer());
        glBindBufferBase(
                GL_SHADER_STORAGE_BUFFER,
                Render.MODEL_MATRICES_BINDING,
                commandBuffers.getStaticModelMatricesBuffer());
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, commandBuffers.getStaticCommandBuffer());
        glBindVertexArray(renderBuffers.getStaticVaoID());
        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
            glFramebufferTexture2D(
                    GL_FRAMEBUFFER,
                    GL_DEPTH_ATTACHMENT,
                    GL_TEXTURE_2D,
                    shadowBuffer.getDepthMap().getIDs()[i],
                    0);

            CascadeShadow shadowCascade = cascadeShadows.get(i);
            uniformsMap.setUniform(
                    ShaderUniforms.Shadow.PROJECTION_VIEW_MATRIX,
                    shadowCascade.getProjViewMatrix());

            glMultiDrawElementsIndirect(
                    GL_TRIANGLES, GL_UNSIGNED_INT, 0, commandBuffers.getStaticDrawCount(), 0);
        }

        // Animated meshes
        glBindBufferBase(
                GL_SHADER_STORAGE_BUFFER,
                Render.DRAW_ELEMENT_BINDING,
                commandBuffers.getAnimatedDrawElementBuffer());
        glBindBufferBase(
                GL_SHADER_STORAGE_BUFFER,
                Render.MODEL_MATRICES_BINDING,
                commandBuffers.getAnimatedModelMatricesBuffer());
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, commandBuffers.getAnimatedCommandBuffer());
        glBindVertexArray(renderBuffers.getAnimVaoID());
        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
            glFramebufferTexture2D(
                    GL_FRAMEBUFFER,
                    GL_DEPTH_ATTACHMENT,
                    GL_TEXTURE_2D,
                    shadowBuffer.getDepthMap().getIDs()[i],
                    0);

            CascadeShadow shadowCascade = cascadeShadows.get(i);
            uniformsMap.setUniform(
                    ShaderUniforms.Shadow.PROJECTION_VIEW_MATRIX,
                    shadowCascade.getProjViewMatrix());

            glMultiDrawElementsIndirect(
                    GL_TRIANGLES, GL_UNSIGNED_INT, 0, commandBuffers.getAnimatedDrawCount(), 0);
        }

        glBindVertexArray(0);
        shaderProgram.unbind();
    }
}
