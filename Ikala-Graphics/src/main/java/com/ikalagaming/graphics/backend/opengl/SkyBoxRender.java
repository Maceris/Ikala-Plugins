package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.frontend.Shader;
import com.ikalagaming.graphics.frontend.Texture;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import org.joml.Matrix4f;

/** Handles rendering for the skybox. */
public class SkyBoxRender {

    /** The cameras view matrix. */
    private final Matrix4f viewMatrix;

    /** Set up the renderer. */
    public SkyBoxRender() {
        viewMatrix = new Matrix4f();
    }

    /**
     * Render the skybox.
     *
     * @param scene The scene to render.
     * @param shader The shader to use for rendering.
     * @param skybox The actual skybox to render.
     */
    public void render(@NonNull Scene scene, @NonNull Shader shader, @NonNull SkyboxModel skybox) {
        shader.bind();
        var uniformsMap = shader.getUniformMap();

        uniformsMap.setUniform(
                ShaderUniforms.Skybox.PROJECTION_MATRIX,
                scene.getProjection().getProjectionMatrix());
        viewMatrix.set(scene.getCamera().getViewMatrix());
        viewMatrix.m30(0);
        viewMatrix.m31(0);
        viewMatrix.m32(0);
        uniformsMap.setUniform(ShaderUniforms.Skybox.VIEW_MATRIX, viewMatrix);
        uniformsMap.setUniform(ShaderUniforms.Skybox.TEXTURE_SAMPLER, 0);

        uniformsMap.setUniform(ShaderUniforms.Skybox.DIFFUSE, scene.getSkyboxDiffuse());

        Texture texture = scene.getSkyboxTexture();
        boolean hasTexture = false;
        if (texture != null) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, (int) texture.id());
            hasTexture = true;
        }

        uniformsMap.setUniform(ShaderUniforms.Skybox.HAS_TEXTURE, hasTexture ? 1 : 0);

        glBindVertexArray(skybox.vao());

        glDrawElements(GL_TRIANGLES, SkyboxModel.VERTEX_COUNT, GL_UNSIGNED_INT, 0);

        glBindVertexArray(0);

        shader.unbind();
    }
}
