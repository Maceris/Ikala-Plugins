package com.ikalagaming.graphics.backend.vulkan.stages;

import com.ikalagaming.graphics.backend.base.RenderStage;
import com.ikalagaming.graphics.backend.vulkan.ShaderVulkan;
import com.ikalagaming.graphics.frontend.Framebuffer;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.Setter;

/** Handles rendering of scene geometry to the g-buffer. */
public class SceneRenderWireframe implements RenderStage {

    /** The shader to use for rendering. */
    @NonNull @Setter private ShaderVulkan shader;

    /** The g-buffer for rendering geometry to. */
    @Setter @NonNull private Framebuffer gBuffer;

    /**
     * Set up the shadow render stage.
     *
     * @param shader The shader to use for rendering.
     * @param gBuffer The depth map buffers.
     */
    public SceneRenderWireframe(
            final @NonNull ShaderVulkan shader, final @NonNull Framebuffer gBuffer) {
        this.shader = shader;
        this.gBuffer = gBuffer;
    }

    /**
     * Compute animation transformations for all animated models in the scene.
     *
     * @param scene The scene we are rendering.
     */
    public void render(Scene scene) {
        // TODO(ches) pretty sure this is going to need to change quite a bit
        SceneRender.commonSceneRender(scene, shader, gBuffer);
    }
}
