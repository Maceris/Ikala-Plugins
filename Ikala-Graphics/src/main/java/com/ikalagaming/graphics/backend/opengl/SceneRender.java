/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43.glMultiDrawElementsIndirect;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.backend.base.UniformsMap;
import com.ikalagaming.graphics.frontend.Material;
import com.ikalagaming.graphics.frontend.Shader;
import com.ikalagaming.graphics.frontend.Texture;
import com.ikalagaming.graphics.graph.GBuffer;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.launcher.PluginFolder;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/** Handles rendering for the scene geometry. */
public class SceneRender {
    private static final String DEFAULT_TEXTURE_PATH =
            PluginFolder.getResource(
                            GraphicsPlugin.PLUGIN_NAME,
                            PluginFolder.ResourceType.DATA,
                            "models/default/default_texture.png")
                    .getAbsolutePath();

    /** The maximum number of draw elements that we can process. */
    public static final int MAX_DRAW_ELEMENTS = 300;

    /** The maximum number of entities that we can process. */
    public static final int MAX_ENTITIES = 100;

    /** The maximum number of materials we can have. */
    private static final int MAX_MATERIALS = 30;

    /** The maximum number of textures we can have. */
    private static final int MAX_TEXTURES = 16;

    /** The shader program for the scene. */
    private final Shader shaderProgram;

    /** The uniform map for the shader program. */
    private UniformsMap uniformsMap;

    private final Texture defaultTexture;

    /** Set up a new scene renderer. */
    public SceneRender() {
        List<Shader.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new Shader.ShaderModuleData("shaders/scene.vert", Shader.Type.VERTEX));
        shaderModuleDataList.add(
                new Shader.ShaderModuleData("shaders/scene.frag", Shader.Type.FRAGMENT));
        shaderProgram = new ShaderOpenGL(shaderModuleDataList);
        createUniforms();
        defaultTexture = GraphicsManager.getTextureLoader().load(DEFAULT_TEXTURE_PATH);
    }

    /** Clean up buffers and shaders. */
    public void cleanup() {
        shaderProgram.cleanup();
    }

    /** Set up the uniforms for the shader. */
    private void createUniforms() {
        uniformsMap = new UniformsMapOpenGL(shaderProgram.getProgramID());
        uniformsMap.createUniform(ShaderUniforms.Scene.PROJECTION_MATRIX);
        uniformsMap.createUniform(ShaderUniforms.Scene.VIEW_MATRIX);

        for (int i = 0; i < SceneRender.MAX_TEXTURES; ++i) {
            uniformsMap.createUniform(ShaderUniforms.Scene.TEXTURE_SAMPLER + "[" + i + "]");
        }

        for (int i = 0; i < SceneRender.MAX_MATERIALS; ++i) {
            String name = ShaderUniforms.Scene.MATERIALS + "[" + i + "].";
            uniformsMap.createUniform(name + ShaderUniforms.Scene.Material.DIFFUSE);
            uniformsMap.createUniform(name + ShaderUniforms.Scene.Material.SPECULAR);
            uniformsMap.createUniform(name + ShaderUniforms.Scene.Material.REFLECTANCE);
            uniformsMap.createUniform(name + ShaderUniforms.Scene.Material.NORMAL_MAP_INDEX);
            uniformsMap.createUniform(name + ShaderUniforms.Scene.Material.TEXTURE_INDEX);
        }
    }

    /**
     * Set up uniforms for textures and materials.
     *
     * @param scene The scene to render.
     */
    public void recalculateMaterials(@NonNull Scene scene) {
        setupMaterialsUniform(scene.getMaterialCache());
    }

    /**
     * Render the scene.
     *
     * @param scene The scene to render.
     * @param renderBuffers The buffers for indirect drawing of models.
     * @param gBuffer The buffer for geometry data.
     * @param commandBuffers The render command buffers.
     */
    public void render(
            @NonNull Scene scene,
            @NonNull RenderBuffers renderBuffers,
            @NonNull GBuffer gBuffer,
            @NonNull CommandBuffer commandBuffers) {

        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, gBuffer.getGBufferId());
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glViewport(0, 0, gBuffer.getWidth(), gBuffer.getHeight());
        glDisable(GL_BLEND);
        shaderProgram.bind();

        uniformsMap.setUniform(
                ShaderUniforms.Scene.PROJECTION_MATRIX,
                scene.getProjection().getProjectionMatrix());
        uniformsMap.setUniform(ShaderUniforms.Scene.VIEW_MATRIX, scene.getCamera().getViewMatrix());

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, defaultTexture.id());

        int nextTexture = 1;
        for (Material material : scene.getMaterialCache().getMaterialsList()) {
            if (material.getNormalMap() != null) {
                glActiveTexture(GL_TEXTURE0 + nextTexture);
                glBindTexture(GL_TEXTURE_2D, material.getNormalMap().id());
                uniformsMap.setUniform(
                        ShaderUniforms.Scene.TEXTURE_SAMPLER + "[" + nextTexture + "]",
                        nextTexture);
                ++nextTexture;
            }
            if (material.getTexture() != null) {
                glActiveTexture(GL_TEXTURE0 + nextTexture);
                glBindTexture(GL_TEXTURE_2D, material.getTexture().id());
                uniformsMap.setUniform(
                        ShaderUniforms.Scene.TEXTURE_SAMPLER + "[" + nextTexture + "]",
                        nextTexture);
                ++nextTexture;
            }
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
        glMultiDrawElementsIndirect(
                GL_TRIANGLES, GL_UNSIGNED_INT, 0, commandBuffers.getStaticDrawCount(), 0);

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
        glMultiDrawElementsIndirect(
                GL_TRIANGLES, GL_UNSIGNED_INT, 0, commandBuffers.getAnimatedDrawCount(), 0);
        glBindVertexArray(0);
        glEnable(GL_BLEND);
        shaderProgram.unbind();
    }

    /**
     * Set the uniforms from cached textures and materials.
     *
     * @param materialCache The material cache.
     */
    private void setupMaterialsUniform(@NonNull MaterialCache materialCache) {
        shaderProgram.bind();
        List<Material> materialList = materialCache.getMaterialsList();
        int numMaterials = materialList.size();

        int nextTexture = 1;
        for (int i = 0; i < numMaterials; ++i) {
            Material material = materialCache.getMaterial(i);
            String name = ShaderUniforms.Scene.MATERIALS + "[" + i + "].";
            uniformsMap.setUniform(
                    name + ShaderUniforms.Scene.Material.DIFFUSE, material.getDiffuseColor());
            uniformsMap.setUniform(
                    name + ShaderUniforms.Scene.Material.SPECULAR, material.getSpecularColor());
            uniformsMap.setUniform(
                    name + ShaderUniforms.Scene.Material.REFLECTANCE, material.getReflectance());

            // We bind the default texture to 0
            int index = 0;
            if (material.getNormalMap() != null) {
                index = nextTexture;
                ++nextTexture;
            }
            uniformsMap.setUniform(name + ShaderUniforms.Scene.Material.NORMAL_MAP_INDEX, index);
            index = 0;
            if (material.getTexture() != null) {
                index = nextTexture;
                ++nextTexture;
            }
            uniformsMap.setUniform(name + ShaderUniforms.Scene.Material.TEXTURE_INDEX, index);
        }
        shaderProgram.unbind();
    }
}
