package com.ikalagaming.graphics.gui.data;

import lombok.NonNull;

/**
 * How {@link DrawList#addImageView} shows a texture, the way an image viewer does: one channel at a
 * time, a chosen mip level, an exposure, and so on. The shader applies these in order: sample,
 * decode the normal, scale by the exposure, pick the channel, encode to sRGB, then put it over the
 * checkerboard. Must line up with {@code sampleTextureView} in {@code ikgui.frag}.
 *
 * @param channel Which channels to show.
 * @param mipLevel The mip level to sample, clamped to the texture's levels, or below 0 to pick one
 *     from the size on screen as usual.
 * @param exposure How many stops brighter to show the color channels, 0 for as they are.
 * @param nearest Whether to show each texel as a hard edged square instead of filtering.
 * @param encodeSrgb Whether the texture holds linear values that need encoding to sRGB to show,
 *     like an HDR image or an sRGB format texture, which the GPU decodes when sampling.
 * @param decodeNormal Whether to show a tangent space normal map rebuilt from its x and y, so two
 *     channel maps show the same as three channel ones.
 * @param checker Whether to show the texture over a checkerboard by its alpha, opaque.
 */
public record TextureView(
        @NonNull Channel channel,
        float mipLevel,
        float exposure,
        boolean nearest,
        boolean encodeSrgb,
        boolean decodeNormal,
        boolean checker) {

    /** Which channels of a texture to show. Must line up with the shader. */
    public enum Channel {
        /** Every channel, with alpha blending. */
        RGBA(0, "RGBA"),
        /** The color channels, opaque. */
        RGB(1, "RGB"),
        /** Red, as gray. */
        RED(2, "R"),
        /** Green, as gray. */
        GREEN(3, "G"),
        /** Blue, as gray. */
        BLUE(4, "B"),
        /** Alpha, as gray. */
        ALPHA(5, "A");

        /** The ID the shader uses. */
        final int id;

        /** A short label, for buttons. */
        public final String label;

        Channel(int id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    /** Option bit: nearest texels. Must line up with the shader. */
    static final int OPTION_NEAREST = 1;

    /** Option bit: encode to sRGB. Must line up with the shader. */
    static final int OPTION_ENCODE_SRGB = 2;

    /** Option bit: decode a normal map. Must line up with the shader. */
    static final int OPTION_DECODE_NORMAL = 4;

    /** Option bit: checkerboard behind. Must line up with the shader. */
    static final int OPTION_CHECKER = 8;

    /** Show the texture as it is: every channel, filtered, at the usual mip level. */
    public static final TextureView PLAIN =
            new TextureView(Channel.RGBA, -1, 0, false, false, false, false);

    /**
     * The options as the shader's bit flags.
     *
     * @return The flags.
     */
    int options() {
        return (nearest ? OPTION_NEAREST : 0)
                | (encodeSrgb ? OPTION_ENCODE_SRGB : 0)
                | (decodeNormal ? OPTION_DECODE_NORMAL : 0)
                | (checker ? OPTION_CHECKER : 0);
    }
}
