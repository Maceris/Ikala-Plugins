package com.ikalagaming.graphics.frontend;

/** The layouts of an image or image subresource. */
public enum ImageLayout {
    /**
     * Unknown layout. Memory cannot transition to this layout, but it can be the initial layout. In
     * that case, the contents are undefined.
     */
    UNDEFINED,
    /** Any kind of device access. */
    GENERAL,
    /** Used only as a color or resolve attachment in a frame buffer. */
    COLOR_ATTACHMENT,
    /**
     * Specifies a layout for both depth and stencil formats, allowing for both read and write
     * access as a depth/stencil attachment. Equivalent to both {@link #DEPTH_ATTACHMENT} and {@link
     * #STENCIL_ATTACHMENT}.
     */
    DEPTH_STENCIL_ATTACHMENT,
    /**
     * Specifies a layout for both depth and stencil formats, allowing for read-only access as a
     * depth/stencil attachment. Equivalent to both {@link #DEPTH_READ_ONLY} and {@link
     * #STENCIL_READ_ONLY}.
     */
    DEPTH_STENCIL_READ_ONLY,
    /**
     * Specifies a layout allowing read-only access in a shader as a sampled image, combined
     * image/sampler, or input attachment. Only valid for images that allow sampling or usage as an
     * input attachment.
     */
    SHADER_READ_ONLY,
    /** Used only as a source image for a transfer command. */
    TRANSFER_SOURCE,
    /** Used only as a destination image for a transfer command. */
    TRANSFER_DESTINATION,
    /**
     * Memory that is in a defined layout, but not been initialized. Memory cannot transition to
     * this layout, but it can be the initial layout. Data can be written to this without
     * transitioning layouts.
     */
    PREINITIALIZED,
    /**
     * Specifies a layout for both depth and stencil formats, allowing for read and write access to
     * the stencil aspect, and read-only access to the depth aspect as a depth attachment, or in
     * shaders as a sampled image, combined image/sampler, or input attachment. Equivalent to both
     * {@link #DEPTH_READ_ONLY} and {@link #STENCIL_ATTACHMENT}.
     */
    DEPTH_READ_ONLY_STENCIL_ATTACHMENT,
    /**
     * Specifies a layout for both depth and stencil formats, allowing for read and write access to
     * the depth aspect as a depth attachment, and read-only access to the stencil aspect as a
     * stencil attachment, or in shaders as a sampled image, combined image/sampler, or input
     * attachment. Equivalent to both {@link #DEPTH_ATTACHMENT} and {@link #STENCIL_READ_ONLY}.
     */
    DEPTH_ATTACHMENT_STENCIL_READ_ONLY,
    /**
     * Specifies a layout for the depth aspect of a depth/stencil format image, allowing read and
     * write access as a depth attachment, or in shaders as a sample image, combined image/sampler,
     * or input attachment.
     */
    DEPTH_ATTACHMENT,
    /**
     * Specifies a layout for the depth aspect of a depth/stencil format image, allowing read-only
     * access as a depth attachment, or in shaders as a sample image, combined image/sampler, or
     * input attachment.
     */
    DEPTH_READ_ONLY,
    /**
     * Specifies a layout for the stencil aspect of a depth/stencil format image, allowing read and
     * write access as a depth attachment, or in shaders as a sample image, combined image/sampler,
     * or input attachment.
     */
    STENCIL_ATTACHMENT,
    /**
     * Specifies a layout for the stencil aspect of a depth/stencil format image, allowing read-only
     * access as a depth attachment, or in shaders as a sample image, combined image/sampler, or
     * input attachment.
     */
    STENCIL_READ_ONLY,
    /**
     * Specifies a layout allowing read-only access as an attachment, or in shaders as a sampled
     * image, combined image/sampler, or input attachment.
     */
    READ_ONLY,
    /** A layout that must only be used with attachment access in the pipeline. */
    ATTACHMENT,
    /** Used only for presenting an image for display. */
    PRESENT
}
