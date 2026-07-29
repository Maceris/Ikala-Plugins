package com.ikalagaming.graphics.backend.vulkan;

/** Tracks handles for a texture, but does not handle the lifetimes. */
public class TextureInfo {
    /** The texture handle. 0 if unused. */
    public long texture;

    /** VMA handle for the texture allocation. 0 if unused. */
    public long textureAllocation;

    /** The image view handle. 0 if unused. */
    public long view;

    /**
     * Builder-style method to set the texture.
     *
     * @param texture The texture handle.
     * @return This object.
     */
    public TextureInfo texture(long texture) {
        this.texture = texture;
        return this;
    }

    /**
     * Builder-style method to set the texture allocation.
     *
     * @param textureAllocation VMA handle for the texture allocation.
     * @return This object.
     */
    public TextureInfo textureAllocation(long textureAllocation) {
        this.textureAllocation = textureAllocation;
        return this;
    }

    /**
     * Builder-style method to set the view.
     *
     * @param view The image view handle.
     * @return This object.
     */
    public TextureInfo view(long view) {
        this.view = view;
        return this;
    }
}
