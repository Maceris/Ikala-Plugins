package com.ikalagaming.graphics.texture;

import com.ikalagaming.graphics.ColorSpace;
import com.ikalagaming.graphics.Format;

import lombok.Getter;
import lombok.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * A DirectDraw Surface file: a 2D texture with its mip levels already made, usually block
 * compressed (BC1 to BC7). The data is kept as stored, every level one after another, ready to copy
 * to the GPU as it is.
 *
 * <p>Reads the legacy header, with four character codes like {@code DXT1}, {@code DXT5} and {@code
 * ATI2}, and the DX10 header with DXGI formats, including sRGB ones. Legacy uncompressed 32 bit
 * RGBA and BGRA files load as they are; 24 bit and alpha-less ones are widened to RGBA. Cube maps,
 * arrays and volume textures are refused, since only 2D textures are supported.
 *
 * <p>Legacy files can't say whether their colors are sRGB, so they load as UNORM; the DX10 header
 * can, and {@link #colorSpace()} reports it. Callers choose the color space they load in, see
 * {@link ColorSpace#apply(Format)}.
 */
public final class DdsFile {
    /** "DDS " as a little endian int. */
    static final int MAGIC = 0x20534444;

    /** The size of the magic and the legacy header, in bytes. */
    static final int HEADER_SIZE = 4 + 124;

    /** The size of the DX10 header that may follow the legacy one, in bytes. */
    static final int DX10_HEADER_SIZE = 20;

    /** Header flag: the mip map count is valid. */
    static final int FLAG_MIPMAP_COUNT = 0x20000;

    /** Pixel format flag: a four character code says the format. */
    static final int PIXEL_FOURCC = 0x4;

    /** Pixel format flag: uncompressed RGB data, with masks. */
    static final int PIXEL_RGB = 0x40;

    /** Pixel format flag: the alpha mask is valid. */
    static final int PIXEL_ALPHA_PIXELS = 0x1;

    /** Pixel format flag: uncompressed single channel data. */
    static final int PIXEL_LUMINANCE = 0x20000;

    /** Caps2 flag: a cube map. */
    static final int CAPS2_CUBEMAP = 0x200;

    /** Caps2 flag: a volume texture. */
    static final int CAPS2_VOLUME = 0x200000;

    /** DX10 misc flag: a cube map. */
    static final int DX10_MISC_CUBE = 0x4;

    /** DX10 resource dimension for 2D textures. */
    static final int DX10_TEXTURE_2D = 3;

    /** Formats by four character code, for legacy headers. */
    private static final Map<Integer, Format> BY_FOURCC =
            Map.ofEntries(
                    Map.entry(fourCC("DXT1"), Format.BC1_RGBA_UNORM_BLOCK),
                    Map.entry(fourCC("DXT2"), Format.BC2_UNORM_BLOCK),
                    Map.entry(fourCC("DXT3"), Format.BC2_UNORM_BLOCK),
                    Map.entry(fourCC("DXT4"), Format.BC3_UNORM_BLOCK),
                    Map.entry(fourCC("DXT5"), Format.BC3_UNORM_BLOCK),
                    Map.entry(fourCC("ATI1"), Format.BC4_UNORM_BLOCK),
                    Map.entry(fourCC("BC4U"), Format.BC4_UNORM_BLOCK),
                    Map.entry(fourCC("BC4S"), Format.BC4_SNORM_BLOCK),
                    Map.entry(fourCC("ATI2"), Format.BC5_UNORM_BLOCK),
                    Map.entry(fourCC("BC5U"), Format.BC5_UNORM_BLOCK),
                    Map.entry(fourCC("BC5S"), Format.BC5_SNORM_BLOCK),
                    // D3DFORMAT numbers, which some tools write as the four character code
                    Map.entry(36, Format.R16G16B16A16_UNORM),
                    Map.entry(111, Format.R16_SFLOAT),
                    Map.entry(112, Format.R16G16_SFLOAT),
                    Map.entry(113, Format.R16G16B16A16_SFLOAT),
                    Map.entry(114, Format.R32_SFLOAT),
                    Map.entry(115, Format.R32G32_SFLOAT),
                    Map.entry(116, Format.R32G32B32A32_SFLOAT));

    /** Formats by DXGI format number, for DX10 headers. Typeless formats read as UNORM. */
    private static final Map<Integer, Format> BY_DXGI =
            Map.ofEntries(
                    Map.entry(2, Format.R32G32B32A32_SFLOAT),
                    Map.entry(10, Format.R16G16B16A16_SFLOAT),
                    Map.entry(11, Format.R16G16B16A16_UNORM),
                    Map.entry(16, Format.R32G32_SFLOAT),
                    Map.entry(27, Format.R8G8B8A8_UNORM),
                    Map.entry(28, Format.R8G8B8A8_UNORM),
                    Map.entry(29, Format.R8G8B8A8_SRGB),
                    Map.entry(34, Format.R16G16_SFLOAT),
                    Map.entry(41, Format.R32_SFLOAT),
                    Map.entry(49, Format.R8G8_UNORM),
                    Map.entry(54, Format.R16_SFLOAT),
                    Map.entry(61, Format.R8_UNORM),
                    Map.entry(70, Format.BC1_RGBA_UNORM_BLOCK),
                    Map.entry(71, Format.BC1_RGBA_UNORM_BLOCK),
                    Map.entry(72, Format.BC1_RGBA_SRGB_BLOCK),
                    Map.entry(73, Format.BC2_UNORM_BLOCK),
                    Map.entry(74, Format.BC2_UNORM_BLOCK),
                    Map.entry(75, Format.BC2_SRGB_BLOCK),
                    Map.entry(76, Format.BC3_UNORM_BLOCK),
                    Map.entry(77, Format.BC3_UNORM_BLOCK),
                    Map.entry(78, Format.BC3_SRGB_BLOCK),
                    Map.entry(79, Format.BC4_UNORM_BLOCK),
                    Map.entry(80, Format.BC4_UNORM_BLOCK),
                    Map.entry(81, Format.BC4_SNORM_BLOCK),
                    Map.entry(82, Format.BC5_UNORM_BLOCK),
                    Map.entry(83, Format.BC5_UNORM_BLOCK),
                    Map.entry(84, Format.BC5_SNORM_BLOCK),
                    Map.entry(87, Format.B8G8R8A8_UNORM),
                    Map.entry(90, Format.B8G8R8A8_UNORM),
                    Map.entry(91, Format.B8G8R8A8_SRGB),
                    Map.entry(94, Format.BC6H_UFLOAT_BLOCK),
                    Map.entry(95, Format.BC6H_UFLOAT_BLOCK),
                    Map.entry(96, Format.BC6H_SFLOAT_BLOCK),
                    Map.entry(97, Format.BC7_UNORM_BLOCK),
                    Map.entry(98, Format.BC7_UNORM_BLOCK),
                    Map.entry(99, Format.BC7_SRGB_BLOCK));

    /**
     * The format as stored: UNORM unless a DX10 header says sRGB.
     *
     * @return The format.
     */
    @Getter private final Format format;

    /**
     * The width of the top level, in pixels.
     *
     * @return The width.
     */
    @Getter private final int width;

    /**
     * The height of the top level, in pixels.
     *
     * @return The height.
     */
    @Getter private final int height;

    /**
     * How many mip levels the file holds, from the top level down.
     *
     * @return The count, at least 1.
     */
    @Getter private final int mipLevels;

    /**
     * Every level, one after another, from the top. Direct and in native order, so it can be staged
     * as it is; position 0, limit at the end of the last level.
     *
     * @return The data. Don't change it.
     */
    @Getter private final ByteBuffer data;

    /** Where each level starts in the data. */
    private final int[] offsets;

    /**
     * Read a DDS file.
     *
     * @param path The file.
     * @return The texture.
     * @throws IOException If the file can't be read, or isn't a DDS file this can load.
     */
    public static DdsFile read(@NonNull Path path) throws IOException {
        final byte[] bytes = Files.readAllBytes(path);
        try {
            return parse(ByteBuffer.wrap(bytes));
        } catch (IOException e) {
            throw new IOException(path.getFileName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Read a DDS file's contents.
     *
     * @param file The whole file, from its position to its limit. Not changed.
     * @return The texture.
     * @throws IOException If it isn't a DDS file this can load.
     */
    public static DdsFile parse(@NonNull ByteBuffer file) throws IOException {
        final ByteBuffer in = file.slice().order(ByteOrder.LITTLE_ENDIAN);
        if (in.remaining() < HEADER_SIZE || in.getInt(0) != MAGIC) {
            throw new IOException("Not a DDS file");
        }
        final int flags = in.getInt(8);
        final int height = in.getInt(12);
        final int width = in.getInt(16);
        final int mipCount = in.getInt(28);
        final int pixelFlags = in.getInt(80);
        final int fourCC = in.getInt(84);
        final int bitCount = in.getInt(88);
        final int[] masks = {in.getInt(92), in.getInt(96), in.getInt(100), in.getInt(104)};
        final int caps2 = in.getInt(112);
        if (width <= 0 || height <= 0) {
            throw new IOException("Bad size " + width + " x " + height);
        }
        if ((caps2 & (CAPS2_CUBEMAP | CAPS2_VOLUME)) != 0) {
            throw new IOException("Cube maps and volume textures aren't supported");
        }

        int dataStart = HEADER_SIZE;
        Format format;
        Widen widen = Widen.NONE;
        if ((pixelFlags & PIXEL_FOURCC) != 0 && fourCC == fourCC("DX10")) {
            if (in.remaining() < HEADER_SIZE + DX10_HEADER_SIZE) {
                throw new IOException("Truncated DX10 header");
            }
            final int dxgi = in.getInt(HEADER_SIZE);
            final int dimension = in.getInt(HEADER_SIZE + 4);
            final int misc = in.getInt(HEADER_SIZE + 8);
            final int arraySize = in.getInt(HEADER_SIZE + 12);
            if (dimension != DX10_TEXTURE_2D || (misc & DX10_MISC_CUBE) != 0 || arraySize > 1) {
                throw new IOException("Only single 2D textures are supported");
            }
            format = BY_DXGI.get(dxgi);
            if (format == null) {
                throw new IOException("Unsupported DXGI format " + dxgi);
            }
            dataStart += DX10_HEADER_SIZE;
        } else if ((pixelFlags & PIXEL_FOURCC) != 0) {
            format = BY_FOURCC.get(fourCC);
            if (format == null) {
                throw new IOException("Unsupported format " + fourCCText(fourCC));
            }
        } else if ((pixelFlags & PIXEL_RGB) != 0) {
            final boolean alpha = (pixelFlags & PIXEL_ALPHA_PIXELS) != 0 && masks[3] != 0;
            if (bitCount == 32 && masks[0] == 0xFF && masks[1] == 0xFF00 && masks[2] == 0xFF0000) {
                format = Format.R8G8B8A8_UNORM;
                widen = alpha ? Widen.NONE : Widen.OPAQUE_32;
            } else if (bitCount == 32
                    && masks[0] == 0xFF0000
                    && masks[1] == 0xFF00
                    && masks[2] == 0xFF) {
                format = Format.B8G8R8A8_UNORM;
                widen = alpha ? Widen.NONE : Widen.OPAQUE_32;
            } else if (bitCount == 24 && masks[0] == 0xFF0000 && masks[2] == 0xFF) {
                format = Format.B8G8R8A8_UNORM;
                widen = Widen.FROM_24;
            } else if (bitCount == 24 && masks[0] == 0xFF && masks[2] == 0xFF0000) {
                format = Format.R8G8B8A8_UNORM;
                widen = Widen.FROM_24;
            } else {
                throw new IOException(
                        "Unsupported uncompressed layout, " + bitCount + " bits per pixel");
            }
        } else if ((pixelFlags & PIXEL_LUMINANCE) != 0 && bitCount == 8) {
            format = Format.R8_UNORM;
        } else {
            throw new IOException("Unsupported pixel format");
        }

        final int fullChain = 32 - Integer.numberOfLeadingZeros(Math.max(width, height));
        final int levels =
                (flags & FLAG_MIPMAP_COUNT) != 0 && mipCount > 0
                        ? Math.min(mipCount, fullChain)
                        : 1;
        final int[] offsets = new int[levels + 1];
        final int[] storedOffsets = new int[levels + 1];
        for (int level = 0; level < levels; ++level) {
            final int w = levelSize(width, level);
            final int h = levelSize(height, level);
            offsets[level + 1] = offsets[level] + byteCount(format, w, h);
            storedOffsets[level + 1] = storedOffsets[level] + widen.storedBytes(format, w, h);
        }
        final int stored = storedOffsets[levels];
        if (in.remaining() - dataStart < stored) {
            throw new IOException(
                    "Truncated: "
                            + levels
                            + " levels need "
                            + stored
                            + " bytes, the file has "
                            + (in.remaining() - dataStart));
        }

        final ByteBuffer data =
                ByteBuffer.allocateDirect(offsets[levels]).order(ByteOrder.nativeOrder());
        if (widen == Widen.NONE) {
            data.put(0, in, dataStart, stored);
        } else {
            widen.copy(in, dataStart, data, stored);
        }
        return new DdsFile(format, width, height, levels, data, offsets);
    }

    /**
     * Set up a texture.
     *
     * @param format The format.
     * @param width The top level's width.
     * @param height The top level's height.
     * @param mipLevels How many levels.
     * @param data Every level.
     * @param offsets Where each level starts, then the end of the last.
     */
    private DdsFile(
            Format format, int width, int height, int mipLevels, ByteBuffer data, int[] offsets) {
        this.format = format;
        this.width = width;
        this.height = height;
        this.mipLevels = mipLevels;
        this.data = data;
        this.offsets = offsets;
    }

    /**
     * Whether the file says its colors are sRGB, which only DX10 headers can.
     *
     * @return The color space the file says it is in.
     */
    public ColorSpace colorSpace() {
        return ColorSpace.of(format);
    }

    /**
     * Where a level starts in the data.
     *
     * @param level The mip level.
     * @return The offset in bytes.
     */
    public int levelOffset(int level) {
        return offsets[level];
    }

    /**
     * How many bytes a level takes.
     *
     * @param level The mip level.
     * @return The size in bytes.
     */
    public int levelBytes(int level) {
        return offsets[level + 1] - offsets[level];
    }

    /**
     * A level's width, halved from the top level's for each level down, at least 1.
     *
     * @param level The mip level.
     * @return The width in pixels.
     */
    public int levelWidth(int level) {
        return levelSize(width, level);
    }

    /**
     * A level's height, halved from the top level's for each level down, at least 1.
     *
     * @param level The mip level.
     * @return The height in pixels.
     */
    public int levelHeight(int level) {
        return levelSize(height, level);
    }

    /**
     * A dimension at a mip level.
     *
     * @param size The top level's size.
     * @param level The level.
     * @return The size, at least 1.
     */
    static int levelSize(int size, int level) {
        return Math.max(1, size >> level);
    }

    /**
     * The bytes a 4 by 4 block takes in a block compressed format.
     *
     * @param format The format.
     * @return The block size, or 0 if the format isn't block compressed.
     */
    public static int blockBytes(@NonNull Format format) {
        return switch (format) {
            case BC1_RGB_UNORM_BLOCK,
                            BC1_RGB_SRGB_BLOCK,
                            BC1_RGBA_UNORM_BLOCK,
                            BC1_RGBA_SRGB_BLOCK,
                            BC4_UNORM_BLOCK,
                            BC4_SNORM_BLOCK ->
                    8;
            case BC2_UNORM_BLOCK,
                            BC2_SRGB_BLOCK,
                            BC3_UNORM_BLOCK,
                            BC3_SRGB_BLOCK,
                            BC5_UNORM_BLOCK,
                            BC5_SNORM_BLOCK,
                            BC6H_UFLOAT_BLOCK,
                            BC6H_SFLOAT_BLOCK,
                            BC7_UNORM_BLOCK,
                            BC7_SRGB_BLOCK ->
                    16;
            default -> 0;
        };
    }

    /**
     * The bytes a pixel takes in one of the uncompressed formats DDS files load as.
     *
     * @param format The format.
     * @return The size, or 0 if the format isn't one of them.
     */
    public static int pixelBytes(@NonNull Format format) {
        return switch (format) {
            case R8_UNORM -> 1;
            case R8G8_UNORM, R16_SFLOAT -> 2;
            case R8G8B8A8_UNORM,
                            R8G8B8A8_SRGB,
                            B8G8R8A8_UNORM,
                            B8G8R8A8_SRGB,
                            R16G16_SFLOAT,
                            R32_SFLOAT ->
                    4;
            case R16G16B16A16_UNORM, R16G16B16A16_SFLOAT, R32G32_SFLOAT -> 8;
            case R32G32B32A32_SFLOAT -> 16;
            default -> 0;
        };
    }

    /**
     * The bytes an image of a format takes, tightly packed.
     *
     * @param format The format.
     * @param width The width in pixels.
     * @param height The height in pixels.
     * @return The size in bytes.
     */
    public static int byteCount(@NonNull Format format, int width, int height) {
        final int block = blockBytes(format);
        if (block > 0) {
            return ((width + 3) / 4) * ((height + 3) / 4) * block;
        }
        return width * height * pixelBytes(format);
    }

    /**
     * A four character code as the file stores it.
     *
     * @param text The four characters.
     * @return The code, little endian.
     */
    static int fourCC(@NonNull String text) {
        return text.charAt(0)
                | (text.charAt(1) << 8)
                | (text.charAt(2) << 16)
                | (text.charAt(3) << 24);
    }

    /**
     * A four character code as text, or its number if it isn't printable.
     *
     * @param code The code.
     * @return The text.
     */
    private static String fourCCText(int code) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 4; ++i) {
            final char c = (char) ((code >>> (i * 8)) & 0xFF);
            if (c < ' ' || c > '~') {
                return Integer.toString(code);
            }
            text.append(c);
        }
        return text.toString();
    }

    /** How uncompressed data is widened to 32 bit pixels as it is read. */
    private enum Widen {
        /** Copied as it is. */
        NONE,
        /** 32 bit pixels whose fourth byte isn't alpha, made opaque. */
        OPAQUE_32,
        /** 24 bit pixels, given an opaque fourth byte. */
        FROM_24;

        /**
         * How many bytes a level takes in the file.
         *
         * @param format The format it loads as.
         * @param width The width.
         * @param height The height.
         * @return The size in the file.
         */
        int storedBytes(Format format, int width, int height) {
            return this == FROM_24 ? width * height * 3 : byteCount(format, width, height);
        }

        /**
         * Copy every level, widening each pixel.
         *
         * @param in The file.
         * @param start Where the data starts in the file.
         * @param out Where to put the 32 bit pixels.
         * @param stored How many bytes the file holds.
         */
        void copy(ByteBuffer in, int start, ByteBuffer out, int stored) {
            final int stride = this == FROM_24 ? 3 : 4;
            final int pixels = stored / stride;
            for (int p = 0; p < pixels; ++p) {
                final int from = start + p * stride;
                final int to = p * 4;
                out.put(to, in.get(from));
                out.put(to + 1, in.get(from + 1));
                out.put(to + 2, in.get(from + 2));
                out.put(to + 3, (byte) 0xFF);
            }
        }
    }
}
