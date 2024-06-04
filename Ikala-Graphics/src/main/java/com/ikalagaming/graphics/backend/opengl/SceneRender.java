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
import com.ikalagaming.graphics.frontend.Framebuffer;
import com.ikalagaming.graphics.frontend.Material;
import com.ikalagaming.graphics.frontend.Shader;
import com.ikalagaming.graphics.frontend.Texture;
import com.ikalagaming.graphics.graph.MaterialCache;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.launcher.PluginFolder;

import lombok.NonNull;

import java.util.List;

/** Handles rendering for the scene geometry. */
public class SceneRender {
    private static final String DEFAULT_TEXTURE_PATH =
            PluginFolder.getResource(
                            GraphicsPlugin.PLUGIN_NAME,
                            PluginFolder.ResourceType.DATA,
                            "models/default/default_texture.png")
                    .getAbsolutePath();

    /** The maximum number of materials we can have. */
    @Deprecated static final int MAX_MATERIALS = 30;

    /** The maximum number of textures we can have. */
    @Deprecated static final int MAX_TEXTURES = 16;

    private final Texture defaultTexture;

    /** Set up a new scene renderer. */
    public SceneRender() {
        defaultTexture =
                GraphicsManager.getRenderInstance().getTextureLoader().load(DEFAULT_TEXTURE_PATH);
    }

    /**
     * Set up uniforms for textures and materials.
     *
     * @param scene The scene to render.
     * @param shader The shader that we want to set materials for.
     */
    @Deprecated
    public void recalculateMaterials(@NonNull Scene scene, @NonNull Shader shader) {
        setupMaterialsUniform(scene.getMaterialCache(), shader);
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
            @NonNull Shader shader,
            @NonNull RenderBuffers renderBuffers,
            @NonNull Framebuffer gBuffer,
            @NonNull CommandBuffer commandBuffers) {

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
        glMultiDrawElementsIndirect(
                GL_TRIANGLES, GL_UNSIGNED_INT, 0, commandBuffers.getStaticDrawCount(), 0);

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
        glMultiDrawElementsIndirect(
                GL_TRIANGLES, GL_UNSIGNED_INT, 0, commandBuffers.getAnimatedDrawCount(), 0);
        glBindVertexArray(0);
        glEnable(GL_BLEND);
        shader.unbind();
    }

    /**
     * Set the uniforms from cached textures and materials.
     *
     * @param materialCache The material cache.
     */
    @Deprecated
    private void setupMaterialsUniform(
            @NonNull MaterialCache materialCache, @NonNull Shader shader) {
        var uniformsMap = shader.getUniformMap();
        shader.bind();
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
        shader.unbind();
    }
}
