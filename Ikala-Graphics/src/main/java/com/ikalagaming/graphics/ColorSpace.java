package com.ikalagaming.graphics;

import lombok.NonNull;

import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * How a texture's values are stored, which decides how shaders read them. Either way, a shader
 * sampling the texture gets values as they are meant: colors in linear light, data as stored.
 *
 * <ul>
 *   <li>{@link #SRGB}: colors encoded with the sRGB curve, like base color, emissive and most
 *       images saved from paint programs. They load as an {@code _SRGB} format, so the GPU decodes
 *       them to linear light when sampling, and filters and makes mips in linear light.
 *   <li>{@link #LINEAR}: values stored as they are meant, like normal maps, packed roughness and
 *       metal maps, masks, and colors already in linear light. They load as a {@code _UNORM}
 *       format.
 * </ul>
 *
 * <p>Only formats with both an {@code _SRGB} and a {@code _UNORM} version can be either; anything
 * else, like float or two channel formats, is always linear.
 *
 * @see Textures#setColorSpace(TextureHandle, ColorSpace)
 */
public enum ColorSpace {
    /** Colors encoded with the sRGB curve, decoded to linear light when sampled. */
    SRGB,
    /** Values stored as they are meant, sampled as they are. */
    LINEAR;

    /** The sRGB version of each format that has one, by its UNORM version. */
    private static final Map<Format, Format> SRGB_BY_UNORM = new EnumMap<>(Format.class);

    /** The UNORM version of each sRGB format. */
    private static final Map<Format, Format> UNORM_BY_SRGB = new EnumMap<>(Format.class);

    static {
        pair(Format.R8_UNORM, Format.R8_SRGB);
        pair(Format.R8G8_UNORM, Format.R8G8_SRGB);
        pair(Format.R8G8B8_UNORM, Format.R8G8B8_SRGB);
        pair(Format.R8G8B8A8_UNORM, Format.R8G8B8A8_SRGB);
        pair(Format.B8G8R8_UNORM, Format.B8G8R8_SRGB);
        pair(Format.B8G8R8A8_UNORM, Format.B8G8R8A8_SRGB);
        pair(Format.A8B8G8R8_UNORM_PACK32, Format.A8B8G8R8_SRGB_PACK32);
        pair(Format.BC1_RGB_UNORM_BLOCK, Format.BC1_RGB_SRGB_BLOCK);
        pair(Format.BC1_RGBA_UNORM_BLOCK, Format.BC1_RGBA_SRGB_BLOCK);
        pair(Format.BC2_UNORM_BLOCK, Format.BC2_SRGB_BLOCK);
        pair(Format.BC3_UNORM_BLOCK, Format.BC3_SRGB_BLOCK);
        pair(Format.BC7_UNORM_BLOCK, Format.BC7_SRGB_BLOCK);
    }

    /**
     * Note a format's two versions.
     *
     * @param unorm The UNORM version.
     * @param srgb The sRGB version.
     */
    private static void pair(Format unorm, Format srgb) {
        SRGB_BY_UNORM.put(unorm, srgb);
        UNORM_BY_SRGB.put(srgb, unorm);
    }

    /**
     * The color space a format's values are in.
     *
     * @param format The format.
     * @return {@link #SRGB} for sRGB formats, {@link #LINEAR} for everything else.
     */
    public static ColorSpace of(@NonNull Format format) {
        return UNORM_BY_SRGB.containsKey(format) ? SRGB : LINEAR;
    }

    /**
     * Whether a format has both an sRGB and a UNORM version, so it can be read either way.
     *
     * @param format The format, either version.
     * @return True if it has both versions.
     */
    public static boolean canSwitch(@NonNull Format format) {
        return SRGB_BY_UNORM.containsKey(format) || UNORM_BY_SRGB.containsKey(format);
    }

    /**
     * The version of a format in this color space.
     *
     * @param format The format, either version.
     * @return The format in this color space, or the format itself if it has no other version.
     */
    public Format apply(@NonNull Format format) {
        final Format other = this == SRGB ? SRGB_BY_UNORM.get(format) : UNORM_BY_SRGB.get(format);
        return other == null ? format : other;
    }

    /**
     * The other version of a format: sRGB for UNORM and UNORM for sRGB.
     *
     * @param format The format.
     * @return The other version, or null if it has none.
     */
    public static @Nullable Format twin(@NonNull Format format) {
        final Format srgb = SRGB_BY_UNORM.get(format);
        return srgb != null ? srgb : UNORM_BY_SRGB.get(format);
    }
}
