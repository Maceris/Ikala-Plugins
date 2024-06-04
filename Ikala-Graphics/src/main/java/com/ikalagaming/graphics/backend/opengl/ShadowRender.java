package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43.glMultiDrawElementsIndirect;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.backend.base.UniformsMap;
import com.ikalagaming.graphics.frontend.Framebuffer;
import com.ikalagaming.graphics.frontend.Shader;
import com.ikalagaming.graphics.graph.CascadeShadow;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/** Handles rendering for shadows. */
public class ShadowRender {

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

        createUniforms();
    }

    /** Clean up buffers and shaders. */
    public void cleanup() {
        shaderProgram.cleanup();
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
     * @param cascadeShadows Cascade shadow information.
     * @param depthMap The depth map buffers.
     * @param renderBuffers The buffers for indirect drawing of models.
     * @param commandBuffers The rendering command buffers.
     */
    public void render(
            @NonNull Scene scene,
            @NonNull RenderBuffers renderBuffers,
            @NonNull List<CascadeShadow> cascadeShadows,
            @NonNull Framebuffer depthMap,
            @NonNull CommandBuffer commandBuffers) {
        CascadeShadow.updateCascadeShadows(cascadeShadows, scene);

        glBindFramebuffer(GL_FRAMEBUFFER, (int) depthMap.id());
        glViewport(0, 0, CascadeShadow.SHADOW_MAP_WIDTH, CascadeShadow.SHADOW_MAP_HEIGHT);

        shaderProgram.bind();

        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
            glFramebufferTexture2D(
                    GL_FRAMEBUFFER,
                    GL_DEPTH_ATTACHMENT,
                    GL_TEXTURE_2D,
                    (int) depthMap.textures()[i],
                    0);
            glClear(GL_DEPTH_BUFFER_BIT);
        }

        // Static meshes
        glBindBufferBase(
                GL_SHADER_STORAGE_BUFFER,
                RendererOpenGL.DRAW_ELEMENT_BINDING,
                commandBuffers.getStaticDrawElementBuffer());
        glBindBufferBase(
                GL_SHADER_STORAGE_BUFFER,
                RendererOpenGL.MODEL_MATRICES_BINDING,
                commandBuffers.getStaticModelMatricesBuffer());
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, commandBuffers.getStaticCommandBuffer());
        glBindVertexArray(renderBuffers.getStaticVaoID());
        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
            glFramebufferTexture2D(
                    GL_FRAMEBUFFER,
                    GL_DEPTH_ATTACHMENT,
                    GL_TEXTURE_2D,
                    (int) depthMap.textures()[i],
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
                RendererOpenGL.DRAW_ELEMENT_BINDING,
                commandBuffers.getAnimatedDrawElementBuffer());
        glBindBufferBase(
                GL_SHADER_STORAGE_BUFFER,
                RendererOpenGL.MODEL_MATRICES_BINDING,
                commandBuffers.getAnimatedModelMatricesBuffer());
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, commandBuffers.getAnimatedCommandBuffer());
        glBindVertexArray(renderBuffers.getAnimVaoID());
        for (int i = 0; i < CascadeShadow.SHADOW_MAP_CASCADE_COUNT; ++i) {
            glFramebufferTexture2D(
                    GL_FRAMEBUFFER,
                    GL_DEPTH_ATTACHMENT,
                    GL_TEXTURE_2D,
                    (int) depthMap.textures()[i],
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
