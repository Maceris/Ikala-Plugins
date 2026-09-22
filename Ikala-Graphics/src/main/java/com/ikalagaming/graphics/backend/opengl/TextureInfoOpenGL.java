package com.ikalagaming.graphics.backend.opengl;

import com.ikalagaming.graphics.frontend.TextureInfo;

public class TextureInfoOpenGL implements TextureInfo {
    /** A unique ID for the texture. */
    public long id;

    /** A bindless texture handle. Will be 0 if this is not a bindless texture. */
    public long bindlessHandle;
}
