package com.ikalagaming.graphics.backend.vulkan.stages;

import com.ikalagaming.graphics.ShaderUniforms;
import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.vulkan.QuadMesh;
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
        shader.bind();
        var uniformsMap = shader.getUniformMap();

        uniformsMap.setUniform(ShaderUniforms.Filter.SCREEN_TEXTURE, 0);
        // TODO(ches) render

        shader.unbind();
    }
}
