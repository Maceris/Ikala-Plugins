package com.ikalagaming.graphics.backend.opengl;

import static org.lwjgl.opengl.ARBBindlessTexture.glGetTextureHandleARB;
import static org.lwjgl.opengl.ARBBindlessTexture.glIsTextureHandleResidentARB;
import static org.lwjgl.opengl.ARBBindlessTexture.glMakeTextureHandleResidentARB;

import com.ikalagaming.graphics.frontend.TextureInfo;

public class TextureInfoOpenGL implements TextureInfo {
    /** A unique ID for the texture. */
    public long id;

    /** A bindless texture handle. Will be 0 if this is not a bindless texture. */
    public long bindlessHandle;

    /**
     * Make sure this texture has a bindless handle that is resident, so that it can be sampled in
     * shaders. Creates the handle if the texture was not loaded as bindless, which makes the
     * texture parameters immutable from then on. Safe to call every frame.
     *
     * @return The bindless texture handle.
     */
    public long makeResident() {
        if (bindlessHandle == 0) {
            bindlessHandle = glGetTextureHandleARB((int) id);
        }
        // Making an already resident handle resident again is an error
        if (!glIsTextureHandleResidentARB(bindlessHandle)) {
            glMakeTextureHandleResidentARB(bindlessHandle);
        }
        return bindlessHandle;
    }
}
