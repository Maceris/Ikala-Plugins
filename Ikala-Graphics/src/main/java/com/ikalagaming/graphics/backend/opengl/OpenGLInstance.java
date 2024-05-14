package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL11.glDeleteTextures;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.frontend.*;

import lombok.NonNull;
import org.lwjgl.opengl.GL;

public class OpenGLInstance implements Instance {

    private Pipeline pipeline;
    private TextureLoader textureLoader;

    @Override
    public void initialize(@NonNull Window window) {
        GL.createCapabilities();
        pipeline = new PipelineOpenGL();
        textureLoader = new TextureLoaderOpenGL();
        pipeline.initialize(window);
    }

    @Override
    public void cleanup() {
        pipeline.cleanup();
    }

    @Override
    public void processResources() {
        var toDelete = GraphicsManager.getDeletionQueue().pop();
        if (toDelete != null) {
            deleteResource(toDelete);
        }
    }

    @Override
    public TextureLoader getTextureLoader() {
        return textureLoader;
    }

    @Override
    public Pipeline getPipeline() {
        return pipeline;
    }

    /**
     * Process resource deletion.
     *
     * @param entry The deletion queue entry to handle.
     */
    private void deleteResource(@NonNull DeletionQueue.Entry entry) {
        switch (entry.type()) {
            case TEXTURE -> {
                var texture = (Texture) entry.resource();
                glDeleteTextures(texture.id());
            }
        }
    }
}
