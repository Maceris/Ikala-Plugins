package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDepthMask;
import static org.lwjgl.opengl.GL11.glDrawElements;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.glBindVertexArray;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.frontend.Shader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/** Handle post-processing filters. */
@Slf4j
public class FilterRender {

    /**
     * Render a scene.
     *
     * @param screenTexture The screen texture for post-processing.
     */
    public void render(int screenTexture, @NonNull Shader shader, @NonNull QuadMesh quadMesh) {
        glDepthMask(false);

        shader.bind();
        var uniformsMap = shader.getUniformMap();

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, screenTexture);

        uniformsMap.setUniform(ShaderUniforms.Filter.SCREEN_TEXTURE, 0);

        glBindVertexArray(quadMesh.vao());
        glDrawElements(GL_TRIANGLES, QuadMesh.VERTEX_COUNT, GL_UNSIGNED_INT, 0);

        shader.unbind();

        glDepthMask(true);
    }
}
