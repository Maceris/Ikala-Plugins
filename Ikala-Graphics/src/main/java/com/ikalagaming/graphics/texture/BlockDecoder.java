package com.ikalagaming.graphics.texture;

import com.ikalagaming.graphics.Format;

import lombok.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.annotation.Nullable;

/**
 * Decodes texture data to 8 bit RGBA on the CPU, for what needs the pixels themselves: a texture's
 * average color, and the value under the mouse in the asset workbench. The GPU samples compressed
 * data itself, so this isn't on any drawing path.
 *
 * <p>Handles BC1 to BC5 and the 8 bit uncompressed formats. Channels a format doesn't have read as
 * the GPU reads them: 0 for missing color channels, opaque for missing alpha. Signed BC4 and BC5
 * are mapped from -1 to 1 onto 0 to 255. BC6H and BC7 aren't handled.
 */
public final class BlockDecoder {
    /** Channels in a decoded pixel. */
    private static final int RGBA = 4;

    /** Pixels along a block's side. */
    private static final int BLOCK = 4;

    /** Opaque, or the largest channel value. */
    private static final int MAX = 255;

    /** Not to be made. */
    private BlockDecoder() {}

    /**
     * Whether a format can be decoded.
     *
     * @param format The format.
     * @return True if {@link #decode} handles it.
     */
    public static boolean canDecode(@NonNull Format format) {
        return switch (format) {
            case BC1_RGB_UNORM_BLOCK,
                            BC1_RGB_SRGB_BLOCK,
                            BC1_RGBA_UNORM_BLOCK,
                            BC1_RGBA_SRGB_BLOCK,
                            BC2_UNORM_BLOCK,
                            BC2_SRGB_BLOCK,
                            BC3_UNORM_BLOCK,
                            BC3_SRGB_BLOCK,
                            BC4_UNORM_BLOCK,
                            BC4_SNORM_BLOCK,
                            BC5_UNORM_BLOCK,
                            BC5_SNORM_BLOCK,
                            R8_UNORM,
                            R8G8_UNORM,
                            R8G8B8A8_UNORM,
                            R8G8B8A8_SRGB,
                            B8G8R8A8_UNORM,
                            B8G8R8A8_SRGB ->
                    true;
            default -> false;
        };
    }

    /**
     * Decode one level of a DDS file.
     *
     * @param dds The file.
     * @param level The mip level.
     * @return The pixels, RGBA, rows from the top, or null if the format can't be decoded.
     */
    public static @Nullable byte[] decode(@NonNull DdsFile dds, int level) {
        return decode(
                dds.getFormat(),
                dds.getData(),
                dds.levelOffset(level),
                dds.levelWidth(level),
                dds.levelHeight(level));
    }

    /**
     * Decode an image.
     *
     * @param format Its format.
     * @param data The data. Not changed.
     * @param offset Where the image starts in the data.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @return The pixels, RGBA, rows from the top, or null if the format can't be decoded.
     */
    public static @Nullable byte[] decode(
            @NonNull Format format, @NonNull ByteBuffer data, int offset, int width, int height) {
        if (!canDecode(format)) {
            return null;
        }
        final ByteBuffer in = data.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        final byte[] out = new byte[width * height * RGBA];
        final int blockBytes = DdsFile.blockBytes(format);
        if (blockBytes == 0) {
            decodePlain(format, in, offset, width, height, out);
            return out;
        }
        final int blocksWide = (width + BLOCK - 1) / BLOCK;
        final int blocksHigh = (height + BLOCK - 1) / BLOCK;
        final int[] block = new int[BLOCK * BLOCK * RGBA];
        for (int by = 0; by < blocksHigh; ++by) {
            for (int bx = 0; bx < blocksWide; ++bx) {
                final int at = offset + (by * blocksWide + bx) * blockBytes;
                decodeBlock(format, in, at, block);
                for (int y = 0; y < BLOCK; ++y) {
                    final int py = by * BLOCK + y;
                    if (py >= height) {
                        break;
                    }
                    for (int x = 0; x < BLOCK; ++x) {
                        final int px = bx * BLOCK + x;
                        if (px >= width) {
                            break;
                        }
                        final int from = (y * BLOCK + x) * RGBA;
                        final int to = (py * width + px) * RGBA;
                        for (int c = 0; c < RGBA; ++c) {
                            out[to + c] = (byte) block[from + c];
                        }
                    }
                }
            }
        }
        return out;
    }

    /**
     * Decode an uncompressed image.
     *
     * @param format Its format.
     * @param in The data, little endian.
     * @param offset Where it starts.
     * @param width The width.
     * @param height The height.
     * @param out Where to put RGBA pixels.
     */
    private static void decodePlain(
            Format format, ByteBuffer in, int offset, int width, int height, byte[] out) {
        final int pixels = width * height;
        for (int p = 0; p < pixels; ++p) {
            final int to = p * RGBA;
            switch (format) {
                case R8_UNORM -> {
                    out[to] = in.get(offset + p);
                    out[to + 3] = (byte) MAX;
                }
                case R8G8_UNORM -> {
                    out[to] = in.get(offset + p * 2);
                    out[to + 1] = in.get(offset + p * 2 + 1);
                    out[to + 3] = (byte) MAX;
                }
                case B8G8R8A8_UNORM, B8G8R8A8_SRGB -> {
                    final int from = offset + p * RGBA;
                    out[to] = in.get(from + 2);
                    out[to + 1] = in.get(from + 1);
                    out[to + 2] = in.get(from);
                    out[to + 3] = in.get(from + 3);
                }
                default -> in.get(offset + p * RGBA, out, to, RGBA);
            }
        }
    }

    /**
     * Decode one 4 by 4 block.
     *
     * @param format The block compressed format.
     * @param in The data, little endian.
     * @param at Where the block starts.
     * @param out Where to put 16 RGBA pixels, row by row, as ints from 0 to 255.
     */
    private static void decodeBlock(Format format, ByteBuffer in, int at, int[] out) {
        switch (format) {
            case BC1_RGB_UNORM_BLOCK, BC1_RGB_SRGB_BLOCK -> color(in, at, out, true, false);
            case BC1_RGBA_UNORM_BLOCK, BC1_RGBA_SRGB_BLOCK -> color(in, at, out, true, true);
            case BC2_UNORM_BLOCK, BC2_SRGB_BLOCK -> {
                color(in, at + 8, out, false, false);
                final long bits = in.getLong(at);
                for (int i = 0; i < BLOCK * BLOCK; ++i) {
                    final int alpha = (int) ((bits >>> (i * 4)) & 0xF);
                    out[i * RGBA + 3] = alpha * 17;
                }
            }
            case BC3_UNORM_BLOCK, BC3_SRGB_BLOCK -> {
                color(in, at + 8, out, false, false);
                single(in, at, out, 3, false);
            }
            case BC4_UNORM_BLOCK, BC4_SNORM_BLOCK -> {
                fill(out, 0, MAX);
                single(in, at, out, 0, format == Format.BC4_SNORM_BLOCK);
            }
            default -> {
                // BC5
                final boolean signed = format == Format.BC5_SNORM_BLOCK;
                fill(out, 0, MAX);
                single(in, at, out, 0, signed);
                single(in, at + 8, out, 1, signed);
            }
        }
    }

    /**
     * Set every pixel's color to one value and its alpha to another.
     *
     * @param out The pixels.
     * @param color The red, green and blue.
     * @param alpha The alpha.
     */
    private static void fill(int[] out, int color, int alpha) {
        for (int i = 0; i < BLOCK * BLOCK; ++i) {
            out[i * RGBA] = color;
            out[i * RGBA + 1] = color;
            out[i * RGBA + 2] = color;
            out[i * RGBA + 3] = alpha;
        }
    }

    /**
     * Decode a BC1 style color block: two 5:6:5 end points and a 2 bit index per pixel.
     *
     * @param in The data.
     * @param at Where the block starts.
     * @param out Where to put the pixels. Alpha is set too, opaque unless punched through.
     * @param bc1 Whether this is BC1, where end points in increasing order mean three colors and
     *     transparent black, instead of always four colors.
     * @param punchThrough Whether the fourth color of a three color block is transparent, as in BC1
     *     with alpha, instead of opaque black.
     */
    private static void color(ByteBuffer in, int at, int[] out, boolean bc1, boolean punchThrough) {
        final int c0 = Short.toUnsignedInt(in.getShort(at));
        final int c1 = Short.toUnsignedInt(in.getShort(at + 2));
        final int indices = in.getInt(at + 4);
        final int[][] palette = new int[4][];
        palette[0] = expand(c0);
        palette[1] = expand(c1);
        final boolean four = !bc1 || c0 > c1;
        palette[2] = new int[4];
        palette[3] = new int[4];
        for (int c = 0; c < 3; ++c) {
            if (four) {
                palette[2][c] = (2 * palette[0][c] + palette[1][c] + 1) / 3;
                palette[3][c] = (palette[0][c] + 2 * palette[1][c] + 1) / 3;
            } else {
                palette[2][c] = (palette[0][c] + palette[1][c]) / 2;
                palette[3][c] = 0;
            }
        }
        palette[2][3] = MAX;
        palette[3][3] = four || !punchThrough ? MAX : 0;
        for (int i = 0; i < BLOCK * BLOCK; ++i) {
            final int[] color = palette[(indices >>> (i * 2)) & 0x3];
            System.arraycopy(color, 0, out, i * RGBA, RGBA);
        }
    }

    /**
     * Widen a 5:6:5 color to 8 bits a channel.
     *
     * @param packed The color.
     * @return Red, green, blue and an opaque alpha.
     */
    private static int[] expand(int packed) {
        final int r = (packed >>> 11) & 0x1F;
        final int g = (packed >>> 5) & 0x3F;
        final int b = packed & 0x1F;
        return new int[] {(r << 3) | (r >>> 2), (g << 2) | (g >>> 4), (b << 3) | (b >>> 2), MAX};
    }

    /**
     * Decode a single channel block, as BC3's alpha and BC4 and BC5 use: two 8 bit end points and a
     * 3 bit index per pixel.
     *
     * @param in The data.
     * @param at Where the block starts.
     * @param out Where to put the pixels.
     * @param channel Which channel of each pixel to set.
     * @param signed Whether the end points are signed, from -127 to 127.
     */
    private static void single(ByteBuffer in, int at, int[] out, int channel, boolean signed) {
        final float e0 = signed ? Math.max(-127, in.get(at)) : Byte.toUnsignedInt(in.get(at));
        final float e1 =
                signed ? Math.max(-127, in.get(at + 1)) : Byte.toUnsignedInt(in.get(at + 1));
        final float[] palette = new float[8];
        palette[0] = e0;
        palette[1] = e1;
        if (e0 > e1) {
            for (int i = 1; i < 7; ++i) {
                palette[i + 1] = ((7 - i) * e0 + i * e1) / 7;
            }
        } else {
            for (int i = 1; i < 5; ++i) {
                palette[i + 1] = ((5 - i) * e0 + i * e1) / 5;
            }
            palette[6] = signed ? -127 : 0;
            palette[7] = signed ? 127 : MAX;
        }
        long bits = 0;
        for (int b = 0; b < 6; ++b) {
            bits |= (long) Byte.toUnsignedInt(in.get(at + 2 + b)) << (b * 8);
        }
        for (int i = 0; i < BLOCK * BLOCK; ++i) {
            final float value = palette[(int) ((bits >>> (i * 3)) & 0x7)];
            out[i * RGBA + channel] =
                    signed ? Math.round((value / 127 * 0.5f + 0.5f) * MAX) : Math.round(value);
        }
    }
}
