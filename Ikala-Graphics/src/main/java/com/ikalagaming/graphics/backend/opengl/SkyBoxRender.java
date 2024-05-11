/*
 * NOTICE: This file is a modified version of contents from
 * https://github.com/lwjglgamedev/lwjglbook, which was licensed under Apache
 * v2.0. Changes have been made related to formatting, functionality, and
 * naming.
 */
package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.glDrawElements;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL30.glBindVertexArray;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.backend.base.TextureHandler;
import com.ikalagaming.graphics.frontend.Material;
import com.ikalagaming.graphics.frontend.Texture;
import com.ikalagaming.graphics.graph.ShaderProgram;
import com.ikalagaming.graphics.graph.UniformsMap;
import com.ikalagaming.graphics.scene.Entity;
import com.ikalagaming.graphics.scene.Scene;
import com.ikalagaming.graphics.scene.SkyBox;

import lombok.NonNull;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Handles rendering for the skybox. */
public class SkyBoxRender {
    /** The shader program for the skybox. */
    private ShaderProgram shaderProgram;

    /** The uniform map for the shader program. */
    private UniformsMap uniformsMap;

    /** The cameras view matrix. */
    private Matrix4f viewMatrix;

    /** Set up the renderer. */
    public SkyBoxRender() {
        List<ShaderProgram.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(
                new ShaderProgram.ShaderModuleData("shaders/skybox.vert", GL_VERTEX_SHADER));
        shaderModuleDataList.add(
                new ShaderProgram.ShaderModuleData("shaders/skybox.frag", GL_FRAGMENT_SHADER));
        shaderProgram = new ShaderProgram(shaderModuleDataList);
        viewMatrix = new Matrix4f();
        createUniforms();
    }

    /** Clean up the shader. */
    public void cleanup() {
        shaderProgram.cleanup();
    }

    /** Set up uniforms for the shader. */
    private void createUniforms() {
        uniformsMap = new UniformsMap(shaderProgram.getProgramID());
        uniformsMap.createUniform(ShaderUniforms.Skybox.PROJECTION_MATRIX);
        uniformsMap.createUniform(ShaderUniforms.Skybox.VIEW_MATRIX);
        uniformsMap.createUniform(ShaderUniforms.Skybox.MODEL_MATRIX);
        uniformsMap.createUniform(ShaderUniforms.Skybox.DIFFUSE);
        uniformsMap.createUniform(ShaderUniforms.Skybox.TEXTURE_SAMPLER);
        uniformsMap.createUniform(ShaderUniforms.Skybox.HAS_TEXTURE);
    }

    /**
     * Render the skybox.
     *
     * @param scene The scene to render.
     * @param textureHandler The texture handler implementation to use.
     */
    public void render(@NonNull Scene scene, @NonNull TextureHandler textureHandler) {
        SkyBox skyBox = scene.getSkyBox();

        if (skyBox == null) {
            return;
        }

        shaderProgram.bind();

        uniformsMap.setUniform(
                ShaderUniforms.Skybox.PROJECTION_MATRIX,
                scene.getProjection().getProjectionMatrix());
        viewMatrix.set(scene.getCamera().getViewMatrix());
        viewMatrix.m30(0);
        viewMatrix.m31(0);
        viewMatrix.m32(0);
        uniformsMap.setUniform(ShaderUniforms.Skybox.VIEW_MATRIX, viewMatrix);
        uniformsMap.setUniform(ShaderUniforms.Skybox.TEXTURE_SAMPLER, 0);

        final Entity skyBoxEntity = skyBox.getSkyBoxEntity();
        Material material = scene.getMaterialCache().getMaterial(skyBox.getMaterialIndex());

        uniformsMap.setUniform(ShaderUniforms.Skybox.DIFFUSE, material.getDiffuseColor());

        Texture texture = material.getTexture();
        boolean hasTexture = false;
        if (texture != null) {
            glActiveTexture(GL_TEXTURE0);
            textureHandler.bind(texture);
            hasTexture = true;
        }

        uniformsMap.setUniform(ShaderUniforms.Skybox.HAS_TEXTURE, hasTexture ? 1 : 0);

        glBindVertexArray(skyBox.getVaoID());

        uniformsMap.setUniform(ShaderUniforms.Skybox.MODEL_MATRIX, skyBoxEntity.getModelMatrix());
        glDrawElements(GL_TRIANGLES, skyBox.getVertexCount(), GL_UNSIGNED_INT, 0);

        glBindVertexArray(0);

        shaderProgram.unbind();
    }
}
