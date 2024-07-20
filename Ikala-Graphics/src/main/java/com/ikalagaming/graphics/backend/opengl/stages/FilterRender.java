package com.ikalagaming.graphics.backend.opengl.stages;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.glBindVertexArray;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.opengl.QuadMesh;
import com.ikalagaming.graphics.frontend.Framebuffer;
import com.ikalagaming.graphics.frontend.Shader;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.Setter;

/** Handles post-processing filters. */
public class FilterRender implements RenderStage {

    /** The shader to use for rendering. */
    @NonNull @Setter private Shader shader;

    /** The source texture for the filter. */
    @Setter @NonNull private Framebuffer sceneTexture;

    /** A mesh for rendering onto. */
    @NonNull private final QuadMesh quadMesh;

    /**
     * Set up the skybox render stage.
     *
     * @param shader The shader to use for rendering.
     * @param sceneTexture The destination framebuffer to render to.
     */
    public FilterRender(
            final @NonNull Shader shader,
            @NonNull final Framebuffer sceneTexture,
            @NonNull final QuadMesh quadMesh) {
        this.shader = shader;
        this.sceneTexture = sceneTexture;
        this.quadMesh = quadMesh;
    }

    public void render(Scene scene) {
        glDepthMask(false);

        shader.bind();
        var uniformsMap = shader.getUniformMap();

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, (int) sceneTexture.textures()[0]);

        uniformsMap.setUniform(ShaderUniforms.Filter.SCREEN_TEXTURE, 0);

        glBindVertexArray(quadMesh.vao());
        glDrawElements(GL_TRIANGLES, QuadMesh.VERTEX_COUNT, GL_UNSIGNED_INT, 0);

        shader.unbind();

        glDepthMask(true);
    }
}
