package com.ikalagaming.graphics.vulkan;

import static org.lwjgl.util.vma.Vma.vmaDestroyImage;
import static org.lwjgl.vulkan.VK13.*;

import com.ikalagaming.graphics.TextureInfo;

import lombok.NonNull;

/** Tracks handles for a texture, but does not handle the lifetimes. */
public class TextureInfoVulkan implements TextureInfo {

    /** The value of {@link #bindlessIndex} when the texture has no slot. */
    public static final int NO_BINDLESS_INDEX = -1;

    /**
     * The slot in the bindless texture array, or {@link #NO_BINDLESS_INDEX} if the texture is not
     * registered.
     *
     * @see BindlessTextures
     */
    public int bindlessIndex = NO_BINDLESS_INDEX;

    /**
     * Whether the sampler belongs to this texture and should be destroyed with it, as opposed to
     * being shared between textures.
     */
    public boolean ownsSampler = false;

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
    public TextureInfoVulkan sampler(long sampler) {
        this.sampler = sampler;
        return this;
    }

    /**
     * Builder-style method to set the texture.
     *
     * @param texture The texture handle.
     * @return This object.
     */
    public TextureInfoVulkan texture(long texture) {
        this.texture = texture;
        return this;
    }

    /**
     * Builder-style method to set the texture allocation.
     *
     * @param textureAllocation VMA handle for the texture allocation.
     * @return This object.
     */
    public TextureInfoVulkan textureAllocation(long textureAllocation) {
        this.textureAllocation = textureAllocation;
        return this;
    }

    /**
     * Builder-style method to set the view.
     *
     * @param view The image view handle.
     * @return This object.
     */
    public TextureInfoVulkan view(long view) {
        this.view = view;
        return this;
    }

    /**
     * Destroy the view, the image, and the sampler if this texture owns it, then clear the handles.
     * This does not release the bindless slot. The GPU must be done with the texture.
     *
     * @param state The Vulkan state.
     */
    public void destroy(@NonNull VulkanState state) {
        vkDestroyImageView(state.device.logical, view, null);
        view = VK_NULL_HANDLE;
        if (ownsSampler) {
            vkDestroySampler(state.device.logical, sampler, null);
        }
        sampler = VK_NULL_HANDLE;
        vmaDestroyImage(state.vmaAllocator, texture, textureAllocation);
        texture = VK_NULL_HANDLE;
        textureAllocation = VK_NULL_HANDLE;
    }
}
