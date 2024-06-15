package com.ikalagaming.graphics.backend.opengl.stages;

import static org.lwjgl.opengl.GL43.*;

import com.ikalagaming.graphics.backend.opengl.CommandBuffer;
import com.ikalagaming.graphics.backend.opengl.RenderBuffers;
import com.ikalagaming.graphics.frontend.*;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import lombok.Setter;

/** Handles rendering of scene geometry to the g-buffer. */
public class SceneRenderWireframe implements RenderStage {

    /** The shader to use for rendering. */
    @NonNull @Setter private Shader shader;

    /** The buffers for indirect drawing of models. */
    private final RenderBuffers renderBuffers;

    /** The g-buffer for rendering geometry to. */
    @Setter @NonNull private Framebuffer gBuffer;

    /** The scene object rendering command buffers. */
    @Setter @NonNull private CommandBuffer commandBuffers;

    @Setter @NonNull private Texture defaultTexture;

    /**
     * Set up the shadow render stage.
     *
     * @param shader The shader to use for rendering.
     * @param renderBuffers The buffers for indirect drawing of models.
     * @param gBuffer The depth map buffers.
     * @param commandBuffers The rendering command buffers.
     */
    public SceneRenderWireframe(
            final @NonNull Shader shader,
            final @NonNull RenderBuffers renderBuffers,
            final @NonNull Framebuffer gBuffer,
            final @NonNull CommandBuffer commandBuffers,
            final @NonNull Texture defaultTexture) {
        this.shader = shader;
        this.renderBuffers = renderBuffers;
        this.gBuffer = gBuffer;
        this.commandBuffers = commandBuffers;
        this.defaultTexture = defaultTexture;
    }

    /**
     * Compute animation transformations for all animated models in the scene.
     *
     * @param scene The scene we are rendering.
     */
    public void render(Scene scene) {
        glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
        glDisable(GL_TEXTURE_2D);

        SceneRender.commonSceneRender(
                scene, shader, renderBuffers, gBuffer, commandBuffers, defaultTexture);

        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        glEnable(GL_TEXTURE_2D);
    }
}
