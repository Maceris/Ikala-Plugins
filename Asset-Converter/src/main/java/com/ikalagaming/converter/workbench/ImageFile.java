package com.ikalagaming.converter.workbench;

import com.ikalagaming.graphics.ColorSpace;
import com.ikalagaming.graphics.Format;
import com.ikalagaming.graphics.texture.BlockDecoder;
import com.ikalagaming.graphics.texture.DdsFile;

import lombok.NonNull;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * An image file opened for the workbench: either a DDS file, kept as stored with its mip levels, or
 * an image decoded with stb as RGBA pixels, with 8 bit images kept 8 bit, 16 bit images 16 bit, and
 * HDR images as half floats, so nothing is lost to show them. The top level's pixels are kept so
 * the viewer can read back the pixel under the mouse.
 *
 * @param path The file.
 * @param fileSize The file's size in bytes.
 * @param width The width in pixels.
 * @param height The height in pixels.
 * @param channels What channels the file has, for the info panel, like "3 (RGB)".
 * @param format The format as stored, UNORM unless the file says sRGB; the color space it loads in
 *     may change that.
 * @param mipLevels How many mip levels the texture has: stored for DDS files, made for the rest.
 * @param depth How the pixels for read back store each channel.
 * @param pixels The top level's pixels, RGBA, rows from the top, tightly packed, in native order;
 *     for stb images also the data to load. Null for DDS formats that can't be decoded on the CPU.
 * @param dds The DDS file, or null for images stb decoded.
 */
public record ImageFile(
        @NonNull Path path,
        long fileSize,
        int width,
        int height,
        @NonNull String channels,
        @NonNull Format format,
        int mipLevels,
        @NonNull Depth depth,
        @Nullable ByteBuffer pixels,
        @Nullable DdsFile dds) {

    /** Channels in the decoded pixels. */
    public static final int CHANNELS = 4;

    /**
     * Words in a file name that mark a texture as data rather than color, so it loads linear:
     * normal maps, packed roughness and metal maps (Bistro calls those "Specular"), masks. Whole
     * words only, so a material named Metal doesn't count; "metal" alone isn't one of them.
     */
    private static final Set<String> DATA_WORDS =
            Set.of(
                    "normal",
                    "normals",
                    "nrm",
                    "nor",
                    "specular",
                    "spec",
                    "roughness",
                    "rough",
                    "metallic",
                    "metalness",
                    "orm",
                    "arm",
                    "occlusion",
                    "ao",
                    "height",
                    "displacement",
                    "disp",
                    "bump",
                    "mask");

    /** Words in a file name that mark a normal map. */
    private static final Set<String> NORMAL_WORDS = Set.of("normal", "normals", "nrm", "nor");

    /** How a channel is stored. */
    public enum Depth {
        /** 8 bit unsigned normalized. */
        UNORM8(Format.R8G8B8A8_UNORM, Byte.BYTES, "8 bit"),
        /** 16 bit unsigned normalized. */
        UNORM16(Format.R16G16B16A16_UNORM, Short.BYTES, "16 bit"),
        /** 16 bit float, for HDR images. */
        HALF(Format.R16G16B16A16_SFLOAT, Short.BYTES, "HDR, half float");

        /** The texture format the pixels load as, in the linear color space. */
        public final Format format;

        /** Bytes per channel. */
        public final int bytes;

        /** A description, for the info panel. */
        public final String label;

        Depth(Format format, int bytes, String label) {
            this.format = format;
            this.bytes = bytes;
            this.label = label;
        }
    }

    /**
     * Read an image file: DDS files as they are stored, anything else decoded with stb.
     *
     * @param path The file.
     * @return The image.
     * @throws IOException If the file can't be read or decoded.
     */
    public static ImageFile read(@NonNull Path path) throws IOException {
        final String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".dds") ? readDds(path) : readStb(path);
    }

    /**
     * Read a DDS file, decoding its top level on the CPU for read back where the format allows.
     *
     * @param path The file.
     * @return The image.
     * @throws IOException If the file can't be read, or isn't a DDS file graphics can load.
     */
    private static ImageFile readDds(@NonNull Path path) throws IOException {
        final DdsFile dds = DdsFile.read(path);
        final byte[] decoded = BlockDecoder.decode(dds, 0);
        ByteBuffer pixels = null;
        if (decoded != null) {
            pixels = allocate(decoded.length);
            pixels.put(0, decoded);
        }
        return new ImageFile(
                path,
                Files.size(path),
                dds.getWidth(),
                dds.getHeight(),
                ddsChannels(dds.getFormat()),
                dds.getFormat(),
                dds.getMipLevels(),
                Depth.UNORM8,
                pixels,
                dds);
    }

    /**
     * Describe the channels a DDS format holds.
     *
     * @param format The format.
     * @return For example "3 (RGB)".
     */
    static String ddsChannels(@NonNull Format format) {
        return switch (format) {
            case BC1_RGB_UNORM_BLOCK, BC1_RGB_SRGB_BLOCK, BC6H_UFLOAT_BLOCK, BC6H_SFLOAT_BLOCK ->
                    "3 (RGB)";
            case BC1_RGBA_UNORM_BLOCK, BC1_RGBA_SRGB_BLOCK -> "4 (RGB, 1 bit alpha)";
            case BC4_UNORM_BLOCK, BC4_SNORM_BLOCK, R8_UNORM, R16_SFLOAT, R32_SFLOAT -> "1 (R)";
            case BC5_UNORM_BLOCK, BC5_SNORM_BLOCK, R8G8_UNORM, R16G16_SFLOAT, R32G32_SFLOAT ->
                    "2 (RG)";
            default -> "4 (RGBA)";
        };
    }

    /**
     * Read and decode an image file with stb.
     *
     * @param path The file.
     * @return The decoded image.
     * @throws IOException If the file can't be read or decoded.
     */
    private static ImageFile readStb(@NonNull Path path) throws IOException {
        final byte[] bytes = Files.readAllBytes(path);
        final ByteBuffer encoded = MemoryUtil.memAlloc(Math.max(1, bytes.length));
        try (MemoryStack stack = MemoryStack.stackPush()) {
            encoded.put(bytes).flip();
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            if (!STBImage.stbi_info_from_memory(encoded, width, height, channels)) {
                throw new IOException(
                        "Can't decode " + path.getFileName() + ": " + failureReason());
            }
            final Depth depth;
            if (STBImage.stbi_is_hdr_from_memory(encoded)) {
                depth = Depth.HALF;
            } else if (STBImage.stbi_is_16_bit_from_memory(encoded)) {
                depth = Depth.UNORM16;
            } else {
                depth = Depth.UNORM8;
            }
            final ByteBuffer pixels = decode(encoded, depth, width, height, channels);
            if (pixels == null) {
                throw new IOException(
                        "Can't decode " + path.getFileName() + ": " + failureReason());
            }
            return new ImageFile(
                    path,
                    bytes.length,
                    width.get(0),
                    height.get(0),
                    stbChannels(channels.get(0)),
                    depth.format,
                    fullChain(width.get(0), height.get(0)),
                    depth,
                    pixels,
                    null);
        } finally {
            MemoryUtil.memFree(encoded);
        }
    }

    /**
     * Describe a channel count stb reports.
     *
     * @param channels The count.
     * @return For example "3 (RGB)".
     */
    static String stbChannels(int channels) {
        final String names =
                switch (channels) {
                    case 1 -> "gray";
                    case 2 -> "gray, alpha";
                    case 3 -> "RGB";
                    default -> "RGBA";
                };
        return channels + " (" + names + ")";
    }

    /**
     * How many mip levels a texture of this size has: down to 1 by 1, halving each time.
     *
     * @param width The width.
     * @param height The height.
     * @return The level count.
     */
    static int fullChain(int width, int height) {
        return 32 - Integer.numberOfLeadingZeros(Math.max(1, Math.max(width, height)));
    }

    /**
     * Decode the pixels into a buffer we own, freeing stb's copy.
     *
     * @param encoded The file's contents.
     * @param depth How to store each channel.
     * @param width Set to the width.
     * @param height Set to the height.
     * @param channels Set to the file's channel count.
     * @return The pixels, or null if decoding failed.
     */
    private static ByteBuffer decode(
            ByteBuffer encoded,
            Depth depth,
            IntBuffer width,
            IntBuffer height,
            IntBuffer channels) {
        switch (depth) {
            case UNORM8 -> {
                ByteBuffer decoded =
                        STBImage.stbi_load_from_memory(encoded, width, height, channels, CHANNELS);
                if (decoded == null) {
                    return null;
                }
                ByteBuffer pixels = allocate(decoded.remaining());
                // Absolute, since stbi_image_free frees from the buffer's position
                pixels.put(0, decoded, 0, decoded.remaining());
                STBImage.stbi_image_free(decoded);
                return pixels;
            }
            case UNORM16 -> {
                ShortBuffer decoded =
                        STBImage.stbi_load_16_from_memory(
                                encoded, width, height, channels, CHANNELS);
                if (decoded == null) {
                    return null;
                }
                ByteBuffer pixels = allocate(decoded.remaining() * Short.BYTES);
                pixels.asShortBuffer().put(0, decoded, 0, decoded.remaining());
                STBImage.stbi_image_free(decoded);
                return pixels;
            }
            default -> {
                FloatBuffer decoded =
                        STBImage.stbi_loadf_from_memory(encoded, width, height, channels, CHANNELS);
                if (decoded == null) {
                    return null;
                }
                final int count = decoded.remaining();
                ByteBuffer pixels = allocate(count * Short.BYTES);
                ShortBuffer halves = pixels.asShortBuffer();
                for (int i = 0; i < count; ++i) {
                    halves.put(i, Float.floatToFloat16(decoded.get(i)));
                }
                STBImage.stbi_image_free(decoded);
                return pixels;
            }
        }
    }

    /**
     * Allocate a buffer the garbage collector frees, in native order for the GPU.
     *
     * @param bytes The size.
     * @return The buffer.
     */
    private static ByteBuffer allocate(int bytes) {
        return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
    }

    /**
     * Why stb last failed, on this thread.
     *
     * @return The reason.
     */
    private static String failureReason() {
        final String reason = STBImage.stbi_failure_reason();
        return reason == null ? "unknown error" : reason;
    }

    /**
     * The color space to load the image in, until someone says otherwise: linear for formats that
     * can't be sRGB and for names that sound like data, such as normal maps; what the file says, if
     * it says; sRGB otherwise, since most images are colors.
     *
     * @return The color space.
     */
    public ColorSpace defaultColorSpace() {
        if (!ColorSpace.canSwitch(format)) {
            return ColorSpace.LINEAR;
        }
        if (looksLikeData(path.getFileName().toString())) {
            return ColorSpace.LINEAR;
        }
        return ColorSpace.SRGB;
    }

    /**
     * Guess from a file name whether a texture holds data rather than colors, like {@code
     * brick_normal.png}, {@code rock_nrm.tga} or Bistro's packed {@code Wood_Specular.dds}.
     *
     * @param name The file name.
     * @return Whether it sounds like data.
     */
    static boolean looksLikeData(@NonNull String name) {
        return words(name).stream().anyMatch(DATA_WORDS::contains);
    }

    /**
     * Guess from a file name whether it is a normal map, like {@code brick_normal.png} or {@code
     * rock_nrm.tga}.
     *
     * @param name The file name.
     * @return Whether it looks like a normal map.
     */
    static boolean looksLikeNormalMap(@NonNull String name) {
        return words(name).stream().anyMatch(NORMAL_WORDS::contains);
    }

    /**
     * Split a file name into lower case words, without its extension: at underscores, dashes, dots
     * and spaces, and where lower case turns to upper, so {@code Wood_BaseColor.dds} gives wood,
     * base and color.
     *
     * @param name The file name.
     * @return The words.
     */
    static List<String> words(@NonNull String name) {
        final int dot = name.lastIndexOf('.');
        final String stem = dot > 0 ? name.substring(0, dot) : name;
        return Arrays.stream(stem.split("[-_. ]+|(?<=[a-z])(?=[A-Z])"))
                .filter(word -> !word.isEmpty())
                .map(word -> word.toLowerCase(Locale.ROOT))
                .toList();
    }

    /**
     * Read one pixel of the top level, as floats: 0 to 1 for normalized images, as stored for HDR
     * ones.
     *
     * @param x The column, from the left.
     * @param y The row, from the top.
     * @param out Where to put red, green, blue and alpha.
     * @return The output, or null if the pixels can't be read back.
     * @throws IndexOutOfBoundsException If the pixel is outside the image.
     */
    public @Nullable float[] pixel(int x, int y, float @NonNull [] out) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw new IndexOutOfBoundsException("Pixel " + x + ", " + y);
        }
        if (pixels == null) {
            return null;
        }
        final int first = ((y * width) + x) * CHANNELS;
        for (int c = 0; c < CHANNELS; ++c) {
            final int index = (first + c) * depth.bytes;
            out[c] =
                    switch (depth) {
                        case UNORM8 -> Byte.toUnsignedInt(pixels.get(index)) / 255.0f;
                        case UNORM16 -> Short.toUnsignedInt(pixels.getShort(index)) / 65_535.0f;
                        case HALF -> Float.float16ToFloat(pixels.getShort(index));
                    };
        }
        return out;
    }

    /**
     * How many bytes go to the GPU: the stored levels for a DDS file, the top level for the rest,
     * whose other levels the GPU makes.
     *
     * @return The size.
     */
    public long dataBytes() {
        if (dds != null) {
            return dds.getData().remaining();
        }
        return (long) width * height * CHANNELS * depth.bytes;
    }
}
