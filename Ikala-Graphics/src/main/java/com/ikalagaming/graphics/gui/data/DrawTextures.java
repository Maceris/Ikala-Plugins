package com.ikalagaming.graphics.gui.data;

import com.ikalagaming.graphics.TextureInfo;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The textures used by draw lists during a frame. Draw commands refer to textures by their index in
 * this list, and each rendering backend is responsible for mapping those indices to their own
 * texture handles. This is shared between the draw data of all viewports, so that draw lists don't
 * need to know which viewport they will end up in.
 */
public class DrawTextures {
    /** The textures used this frame, in order of their indices. */
    public final List<TextureInfo> textures;

    /** Look up the index of a texture in {@link #textures}, by identity. */
    private final Map<TextureInfo, Integer> textureIndices;

    public DrawTextures() {
        textures = new ArrayList<>();
        textureIndices = new IdentityHashMap<>();
    }

    /**
     * Fetch the index of a texture for this frame, adding it to the list of textures if it is not
     * already there.
     *
     * @param texture The texture.
     * @return The index of the texture in {@link #textures}.
     */
    public int register(@NonNull TextureInfo texture) {
        return textureIndices.computeIfAbsent(
                texture,
                newTexture -> {
                    textures.add(newTexture);
                    return textures.size() - 1;
                });
    }

    /** Clear out the textures used for the frame. */
    public void clear() {
        textures.clear();
        textureIndices.clear();
    }
}
