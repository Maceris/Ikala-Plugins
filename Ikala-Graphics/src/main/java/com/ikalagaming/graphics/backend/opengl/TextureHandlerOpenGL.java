package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.GL46.*;

import com.ikalagaming.graphics.GraphicsPlugin;
import com.ikalagaming.graphics.backend.base.TextureHandler;
import com.ikalagaming.graphics.exceptions.TextureException;
import com.ikalagaming.graphics.frontend.Format;
import com.ikalagaming.graphics.frontend.Texture;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

@Slf4j
public class TextureHandlerOpenGL implements TextureHandler {
    @Override
    public void bind(@NonNull Texture texture) {
        glBindTexture(GL_TEXTURE_2D, texture.id());
    }

    @Override
    public void cleanup(@NonNull Texture texture) {
        glDeleteTextures(texture.id());
    }

    @Override
    public Texture load(
            @NonNull ByteBuffer buffer, @NonNull Format format, final int width, final int height) {
        int textureID = glGenTextures();

        glBindTexture(GL_TEXTURE_2D, textureID);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexImage2D(
                GL_TEXTURE_2D,
                0,
                FormatMapperOpenGL.mapFormat(format),
                width,
                height,
                0,
                FormatMapperOpenGL.mapFormat(format),
                FormatMapperOpenGL.mapType(format),
                buffer);
        glGenerateMipmap(GL_TEXTURE_2D);

        return new Texture(textureID, width, height);
    }

    @Override
    public Texture load(@NonNull String texturePath) {
        Texture result;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            ByteBuffer buffer = STBImage.stbi_load(texturePath, width, height, channels, 4);
            if (buffer == null) {
                String error =
                        SafeResourceLoader.getStringFormatted(
                                "TEXTURE_ERROR_LOADING",
                                GraphicsPlugin.getResourceBundle(),
                                texturePath,
                                STBImage.stbi_failure_reason());
                log.info(error);
                throw new TextureException(error);
            }

            result = load(buffer, Format.R8G8B8A8_UINT, width.get(), height.get());

            STBImage.stbi_image_free(buffer);
        }
        return result;
    }
}
