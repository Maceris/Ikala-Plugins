package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;

import com.ikalagaming.graphics.Window;
import com.ikalagaming.graphics.backend.base.GBufferHandler;
import com.ikalagaming.graphics.graph.GBuffer;

import lombok.NonNull;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

public class GBufferHandlerOpenGL implements GBufferHandler {

    @Override
    public void initialize(@NonNull GBuffer buffer, @NonNull Window window) {
        int gBufferId = glGenFramebuffers();
        buffer.setGBufferId(gBufferId);

        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, gBufferId);

        glGenTextures(buffer.getTextureIDs());

        final int width = window.getWidth();
        final int height = window.getHeight();

        buffer.setWidth(width);
        buffer.setHeight(height);

        for (int i = 0; i < buffer.getTextureIDs().length; ++i) {
            glBindTexture(GL_TEXTURE_2D, buffer.getTextureIDs()[i]);
            int attachmentType;
            if (i == buffer.getTextureIDs().length - 1) {
                glTexImage2D(
                        GL_TEXTURE_2D,
                        0,
                        GL_DEPTH_COMPONENT32F,
                        width,
                        height,
                        0,
                        GL_DEPTH_COMPONENT,
                        GL_FLOAT,
                        (ByteBuffer) null);
                attachmentType = GL_DEPTH_ATTACHMENT;
            } else {
                glTexImage2D(
                        GL_TEXTURE_2D,
                        0,
                        GL_RGBA32F,
                        width,
                        height,
                        0,
                        GL_RGBA,
                        GL_FLOAT,
                        (ByteBuffer) null);
                attachmentType = GL_COLOR_ATTACHMENT0 + i;
            }
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

            glFramebufferTexture2D(
                    GL_FRAMEBUFFER, attachmentType, GL_TEXTURE_2D, buffer.getTextureIDs()[i], 0);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer intBuff = stack.mallocInt(buffer.getTextureIDs().length - 1);
            for (int i = 0; i < buffer.getTextureIDs().length - 1; ++i) {
                intBuff.put(i, GL_COLOR_ATTACHMENT0 + i);
            }
            glDrawBuffers(intBuff);
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    @Override
    public void cleanup(@NonNull GBuffer buffer) {
        glDeleteFramebuffers(buffer.getGBufferId());
        Arrays.stream(buffer.getTextureIDs()).forEach(GL11::glDeleteTextures);
    }
}
