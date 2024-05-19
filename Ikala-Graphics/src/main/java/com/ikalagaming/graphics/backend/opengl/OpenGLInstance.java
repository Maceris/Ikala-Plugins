package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.frontend.*;
import com.ikalagaming.graphics.scene.Scene;

import lombok.NonNull;
import org.lwjgl.opengl.GL;

import java.util.Arrays;

public class OpenGLInstance implements Instance {

    private Renderer renderer;
    private TextureLoader textureLoader;

    @Override
    public void initialize(@NonNull Window window) {
        GL.createCapabilities();
        renderer = new RendererOpenGL();
        textureLoader = new TextureLoaderOpenGL();
        renderer.initialize(window);
    }

    @Override
    public void cleanup() {
        renderer.cleanup();
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
    public void render(@NonNull Window window, @NonNull Scene scene) {
        renderer.render(window, scene);
    }

    @Override
    public void resize(int width, int height) {
        // TODO(ches) move this out of the render pass itself
        renderer.resize(width, height);
    }

    @Override
    public void setupData(@NonNull Scene scene) {
        renderer.setupData(scene);
    }

    /**
     * Process resource deletion.
     *
     * @param entry The deletion queue entry to handle.
     */
    private void deleteResource(@NonNull DeletionQueue.Entry entry) {
        switch (entry.type()) {
            case FRAME_BUFFER -> {
                var framebuffer = (Framebuffer) entry.resource();
                glDeleteFramebuffers((int) framebuffer.id());
                Arrays.stream(framebuffer.textures()).forEach(id -> glDeleteTextures((int) id));
            }
            case TEXTURE -> {
                var texture = (Texture) entry.resource();
                glDeleteTextures((int) texture.id());
            }
        }
    }
}
