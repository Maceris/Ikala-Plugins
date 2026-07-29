package com.ikalagaming.graphics.backend.vulkan;

import static org.lwjgl.vulkan.VK13.VK_NULL_HANDLE;

/** Tracks handles for a texture, but does not handle the lifetimes. */
public class TextureInfo {

    /** Image sampler handle. 0 if unused. */
    public long sampler = VK_NULL_HANDLE;

    /** The texture handle. 0 if unused. */
    public long texture = VK_NULL_HANDLE;

    /** VMA handle for the texture allocation. 0 if unused. */
    public long textureAllocation = VK_NULL_HANDLE;

    /** The image view handle. 0 if unused. */
    public long view = VK_NULL_HANDLE;

    /**
     * Builder-style method to set the sampler.
     *
     * @param sampler Image sampler handle.
     * @return This object.
     */
    public TextureInfo sampler(long sampler) {
        this.sampler = sampler;
        return this;
    }

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
