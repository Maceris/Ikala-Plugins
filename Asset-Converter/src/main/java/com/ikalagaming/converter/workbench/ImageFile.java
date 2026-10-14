package com.ikalagaming.converter.workbench;

import com.ikalagaming.graphics.Format;

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

/**
 * An image file decoded with stb, as RGBA pixels ready to load as a texture, kept so the viewer can
 * read back the pixel under the mouse. 8 bit images stay 8 bit, 16 bit images 16 bit, and HDR
 * images become half floats, so nothing is lost to show them.
 *
 * @param path The file.
 * @param fileSize The file's size in bytes.
 * @param width The width in pixels.
 * @param height The height in pixels.
 * @param sourceChannels How many channels the file has, 1 to 4; missing ones are filled in.
 * @param depth How each channel is stored.
 * @param pixels The pixels, RGBA, rows from the top, tightly packed, in native order.
 */
public record ImageFile(
        @NonNull Path path,
        long fileSize,
        int width,
        int height,
        int sourceChannels,
        @NonNull Depth depth,
        @NonNull ByteBuffer pixels) {

    /** Channels in the decoded pixels. */
    public static final int CHANNELS = 4;

    /** How a channel is stored. */
    public enum Depth {
        /** 8 bit unsigned normalized. */
        UNORM8(Format.R8G8B8A8_UNORM, Byte.BYTES, "8 bit"),
        /** 16 bit unsigned normalized. */
        UNORM16(Format.R16G16B16A16_UNORM, Short.BYTES, "16 bit"),
        /** 16 bit float, for HDR images. */
        HALF(Format.R16G16B16A16_SFLOAT, Short.BYTES, "HDR, half float");

        /** The texture format the pixels load as. */
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
     * Read and decode an image file.
     *
     * @param path The file.
     * @return The decoded image.
     * @throws IOException If the file can't be read or decoded.
     */
    public static ImageFile read(@NonNull Path path) throws IOException {
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
                    channels.get(0),
                    depth,
                    pixels);
        } finally {
            MemoryUtil.memFree(encoded);
        }
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
     * Read one pixel, as floats: 0 to 1 for normalized images, as stored for HDR ones.
     *
     * @param x The column, from the left.
     * @param y The row, from the top.
     * @param out Where to put red, green, blue and alpha.
     * @return The output.
     * @throws IndexOutOfBoundsException If the pixel is outside the image.
     */
    public float[] pixel(int x, int y, float @NonNull [] out) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw new IndexOutOfBoundsException("Pixel " + x + ", " + y);
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
     * How many bytes the decoded pixels take.
     *
     * @return The size.
     */
    public long decodedBytes() {
        return (long) width * height * CHANNELS * depth.bytes;
    }
}
