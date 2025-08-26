package com.ikalagaming.graphics.backend.opengl.stages;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.glBindVertexArray;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.opengl.SkyboxModel;
import com.ikalagaming.graphics.frontend.Shader;
import com.ikalagaming.graphics.frontend.Texture;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.Setter;
import org.joml.Matrix4f;

public class SkyboxRender implements RenderStage {

    /** The cameras view matrix. */
    private final Matrix4f viewMatrix;

    /** The shader to use for rendering. */
    @NonNull @Setter private Shader shader;

    /** The model to use for the skybox. */
    private final SkyboxModel skybox;

    /**
     * Set up the skybox render stage.
     *
     * @param shader The shader to use for rendering.
     * @param skybox The model to use for drawing the skybox.
     */
    public SkyboxRender(final @NonNull Shader shader, @NonNull SkyboxModel skybox) {
        viewMatrix = new Matrix4f();
        this.shader = shader;
        this.skybox = skybox;
    }

    public void render(Scene scene) {
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

        glBindVertexArray(skybox.getVao());

        glDrawElements(GL_TRIANGLES, SkyboxModel.VERTEX_COUNT, GL_UNSIGNED_INT, 0);

        glBindVertexArray(0);

        shader.unbind();
    }
}
