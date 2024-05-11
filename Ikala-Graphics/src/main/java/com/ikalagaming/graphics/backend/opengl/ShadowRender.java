/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_NONE;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glDrawBuffer;
import static org.lwjgl.opengl.GL11.glReadBuffer;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL13.GL_CLAMP_TO_BORDER;
import static org.lwjgl.opengl.GL14.GL_TEXTURE_COMPARE_MODE;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43.glMultiDrawElementsIndirect;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.backend.base.UniformsMap;
import com.ikalagaming.graphics.exceptions.RenderException;
import com.ikalagaming.graphics.frontend.Shader;
import com.ikalagaming.graphics.graph.CascadeShadow;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.Getter;
import lombok.NonNull;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Handles rendering for shadows. */
public class ShadowRender {

    /** The width of the shadow map in pixels. */
    private static final int SHADOW_MAP_WIDTH = 4096;

    /** The height of the shadow map in pixels. */
    private static final int SHADOW_MAP_HEIGHT = 4096;

    /** The list of all texture IDs. */
    @Getter private final int[] shadowTextures = new int[CascadeShadow.SHADOW_MAP_CASCADE_COUNT];

    /** The Frame Buffer Object ID. */
    private int depthMapFBO;

    /**
     * The cascade shadow map.
     *
     * @return The cascade shadows.
     */
    @Getter private final ArrayList<CascadeShadow> cascadeShadows;

    /** The shader program for the shadow rendering. */
    private final Shader shaderProgram;

    /** The uniform map for the shader program. */
    private UniformsMap uniformsMap;

    /** Set up a new shadow renderer. */
    public ShadowRender() {
        List<Shader.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new Shader.ShaderModuleData("shaders/shadow.vert", Shader.Type.VERTEX));
        shaderProgram = new ShaderOpenGL(shaderModuleDataList);

        createShadowBuffers();

        cascadeShadows = new ArrayList<>();
        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
            cascadeShadows.add(new CascadeShadow());
        }

        createUniforms();
    }

    private void createShadowBuffers() {
        depthMapFBO = glGenFramebuffers();

        glGenTextures(shadowTextures);

        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
            glBindTexture(GL_TEXTURE_2D, shadowTextures[i]);
            glTexImage2D(
                    GL_TEXTURE_2D,
                    0,
                    GL_DEPTH_COMPONENT,
                    SHADOW_MAP_WIDTH,
                    SHADOW_MAP_HEIGHT,
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
    }

    /** Clean up buffers and shaders. */
    public void cleanup() {
        shaderProgram.cleanup();
        glDeleteFramebuffers(depthMapFBO);
        glDeleteTextures(shadowTextures);
    }

    /** Create the uniforms for the shadow shader. */
    private void createUniforms() {
        uniformsMap = new UniformsMapOpenGL(shaderProgram.getProgramID());
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

        glBindFramebuffer(GL_FRAMEBUFFER, depthMapFBO);
        glViewport(0, 0, SHADOW_MAP_WIDTH, SHADOW_MAP_HEIGHT);

        shaderProgram.bind();

        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
            glFramebufferTexture2D(
                    GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, shadowTextures[i], 0);
            glClear(GL_DEPTH_BUFFER_BIT);
        }

        // Static meshes
        glBindBufferBase(
                GL_SHADER_STORAGE_BUFFER,
                PipelineOpenGL.DRAW_ELEMENT_BINDING,
                commandBuffers.getStaticDrawElementBuffer());
        glBindBufferBase(
                GL_SHADER_STORAGE_BUFFER,
                PipelineOpenGL.MODEL_MATRICES_BINDING,
                commandBuffers.getStaticModelMatricesBuffer());
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, commandBuffers.getStaticCommandBuffer());
        glBindVertexArray(renderBuffers.getStaticVaoID());
        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
            glFramebufferTexture2D(
                    GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, shadowTextures[i], 0);

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
                PipelineOpenGL.DRAW_ELEMENT_BINDING,
                commandBuffers.getAnimatedDrawElementBuffer());
        glBindBufferBase(
                GL_SHADER_STORAGE_BUFFER,
                PipelineOpenGL.MODEL_MATRICES_BINDING,
                commandBuffers.getAnimatedModelMatricesBuffer());
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, commandBuffers.getAnimatedCommandBuffer());
        glBindVertexArray(renderBuffers.getAnimVaoID());
        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
            glFramebufferTexture2D(
                    GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, shadowTextures[i], 0);

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
