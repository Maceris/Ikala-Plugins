package com.ikalagaming.graphics.asset;

import lombok.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * A texture's maps, as a {@code TEXR} section: what the asset converter makes from a texture's PNG
 * maps, and what a game loads to draw with them. Every map has the texture's size and is
 * uncompressed RGBA, 8 bits a channel, since pixel art a few pixels across can't be
 * block-compressed without smearing its pixels. Each map holds all of its mip levels, made when the
 * file is written, each half the size of the one before (rounded down, at least 1), down to 1 by 1,
 * the way the GPU would make them.
 *
 * <p>Version 1, little-endian like every payload:
 *
 * <ol>
 *   <li>A fixed record: its own size in bytes (int), the width, the height, the number of mip
 *       levels and the number of maps (ints).
 *   <li>Each map: its kind (int, {@link Kind}), then its pixels' byte length (int) and the pixels,
 *       every level in turn from the largest, rows from the top, red, green, blue and alpha.
 * </ol>
 *
 * Readers skip fields past the ones they know, using the record's size, and maps of kinds they
 * don't know.
 */
public final class TextureSection {
    /** The section's tag. */
    public static final int TAG = SectionTag.of("TEXR");

    /** The version this code writes and reads. */
    public static final int VERSION = 1;

    /** The size of the fixed record this version writes. */
    static final int RECORD_SIZE = 5 * Integer.BYTES;

    /** The largest width or height. */
    static final int MAX_SIZE = 16_384;

    /** Bytes per pixel. */
    static final int CHANNELS = 4;

    /** What a map holds, and how its values are read. */
    public enum Kind {
        /** Base color, sRGB, alpha for coverage. */
        COLOR(0, "", true),
        /** A tangent space normal, x, y and z from 0..1, green up. */
        NORMAL(1, ".normal", false),
        /** Packed occlusion, roughness and metallic, linear. */
        ORM(2, ".orm", false),
        /** Emitted light, sRGB. */
        EMISSIVE(3, ".emissive", true),
        /** Which material each pixel is, by color. */
        MASK(4, ".mask", true);

        /** Its number in files. */
        public final int id;

        /** What a PNG of this map ends with before ".png", after the texture's name. */
        public final String suffix;

        /** Whether its colors are sRGB encoded, rather than linear values. */
        public final boolean srgb;

        Kind(int id, String suffix, boolean srgb) {
            this.id = id;
            this.suffix = suffix;
            this.srgb = srgb;
        }

        /**
         * The kind with a number.
         *
         * @param id The number.
         * @return The kind, or empty if this code doesn't know it.
         */
        public static Optional<Kind> of(int id) {
            return Arrays.stream(values()).filter(k -> k.id == id).findFirst();
        }
    }

    /**
     * One map, with every mip level.
     *
     * @param kind What it holds.
     * @param pixels Its levels, one after another from the largest.
     */
    public record MapData(@NonNull Kind kind, byte @NonNull [] pixels) {}

    /**
     * A stored texture.
     *
     * @param width The width of the largest level, in pixels.
     * @param height Its height.
     * @param levels How many mip levels each map has.
     * @param maps The maps, in the order written.
     */
    public record Stored(int width, int height, int levels, @NonNull List<MapData> maps) {
        /**
         * One of the maps.
         *
         * @param kind Which.
         * @return It, or empty if the texture has none.
         */
        public Optional<MapData> map(@NonNull Kind kind) {
            return maps.stream().filter(m -> m.kind() == kind).findFirst();
        }

        /**
         * Where each level starts in a map's pixels.
         *
         * @return The byte offsets, from the largest level.
         */
        public int[] levelOffsets() {
            final int[] offsets = new int[levels];
            int at = 0;
            for (int level = 0; level < levels; ++level) {
                offsets[level] = at;
                at += levelWidth(width, level) * levelWidth(height, level) * CHANNELS;
            }
            return offsets;
        }

        /**
         * The largest level of a map, alone.
         *
         * @param map The map.
         * @return Its pixels, rows from the top, RGBA.
         */
        public byte[] topLevel(@NonNull MapData map) {
            return Arrays.copyOf(map.pixels(), width * height * CHANNELS);
        }
    }

    /**
     * How many mip levels a texture has: down to 1 by 1.
     *
     * @param width The width.
     * @param height The height.
     * @return The count.
     */
    public static int levelCount(int width, int height) {
        return 32 - Integer.numberOfLeadingZeros(Math.max(width, height));
    }

    /**
     * A size at a mip level.
     *
     * @param size The size at the largest level.
     * @param level The level.
     * @return Half for each level, rounded down, at least 1.
     */
    static int levelWidth(int size, int level) {
        return Math.max(1, size >> level);
    }

    /**
     * Make a map's mip levels from its largest one, averaging each 2 by 2 block of the level above:
     * in linear light for sRGB maps, with normals renormalized, and plain values otherwise.
     *
     * @param kind What the map holds.
     * @param top The largest level, rows from the top, RGBA.
     * @param width Its width.
     * @param height Its height.
     * @return Every level, one after another.
     * @throws IllegalArgumentException If the pixels don't fit the size.
     */
    public static byte[] withMips(@NonNull Kind kind, byte @NonNull [] top, int width, int height) {
        if (top.length != width * height * CHANNELS) {
            throw new IllegalArgumentException(
                    "A "
                            + width
                            + " x "
                            + height
                            + " map has "
                            + width * height * CHANNELS
                            + " bytes, not "
                            + top.length);
        }
        final int levels = levelCount(width, height);
        final List<byte[]> out = new ArrayList<>();
        out.add(top);
        byte[] above = top;
        for (int level = 1; level < levels; ++level) {
            final int aw = levelWidth(width, level - 1);
            final int ah = levelWidth(height, level - 1);
            final int w = levelWidth(width, level);
            final int h = levelWidth(height, level);
            final byte[] next = new byte[w * h * CHANNELS];
            for (int y = 0; y < h; ++y) {
                for (int x = 0; x < w; ++x) {
                    final double[] sum = new double[CHANNELS];
                    int count = 0;
                    for (int dy = 0; dy < 2; ++dy) {
                        for (int dx = 0; dx < 2; ++dx) {
                            final int sx = Math.min(aw - 1, x * 2 + dx);
                            final int sy = Math.min(ah - 1, y * 2 + dy);
                            final int at = (sy * aw + sx) * CHANNELS;
                            for (int c = 0; c < CHANNELS; ++c) {
                                sum[c] += read(kind, c, above[at + c] & 0xFF);
                            }
                            ++count;
                        }
                    }
                    final int at = (y * w + x) * CHANNELS;
                    if (kind == Kind.NORMAL) {
                        double length = 0;
                        for (int c = 0; c < 3; ++c) {
                            length += sum[c] * sum[c];
                        }
                        length = Math.sqrt(length);
                        for (int c = 0; c < 3; ++c) {
                            sum[c] = length == 0 ? (c == 2 ? count : 0) : sum[c] / length * count;
                        }
                    }
                    for (int c = 0; c < CHANNELS; ++c) {
                        next[at + c] = (byte) write(kind, c, sum[c] / count);
                    }
                }
            }
            out.add(next);
            above = next;
        }
        int total = 0;
        for (byte[] level : out) {
            total += level.length;
        }
        final byte[] all = new byte[total];
        int at = 0;
        for (byte[] level : out) {
            System.arraycopy(level, 0, all, at, level.length);
            at += level.length;
        }
        return all;
    }

    /**
     * A stored byte as the value averaged.
     *
     * @param kind The map's kind.
     * @param channel The channel.
     * @param value The byte.
     * @return Linear light for sRGB colors, -1..1 for normals, else 0..1.
     */
    private static double read(Kind kind, int channel, int value) {
        final double unit = value / 255.0;
        if (channel == 3) {
            return unit;
        }
        if (kind == Kind.NORMAL) {
            return unit * 2 - 1;
        }
        return kind.srgb ? toLinear(unit) : unit;
    }

    /**
     * An averaged value as a stored byte.
     *
     * @param kind The map's kind.
     * @param channel The channel.
     * @param value The value, as {@link #read} gives it.
     * @return The byte.
     */
    private static int write(Kind kind, int channel, double value) {
        double unit = value;
        if (channel != 3) {
            if (kind == Kind.NORMAL) {
                unit = value * 0.5 + 0.5;
            } else if (kind.srgb) {
                unit = fromLinear(value);
            }
        }
        return (int) Math.round(Math.clamp(unit, 0, 1) * 255);
    }

    /**
     * An sRGB value as linear light.
     *
     * @param encoded The value, 0 to 1.
     * @return Linear.
     */
    private static double toLinear(double encoded) {
        return encoded <= 0.040_45 ? encoded / 12.92 : Math.pow((encoded + 0.055) / 1.055, 2.4);
    }

    /**
     * Linear light as an sRGB value.
     *
     * @param linear The value, 0 to 1.
     * @return Encoded.
     */
    private static double fromLinear(double linear) {
        return linear <= 0.003_130_8 ? linear * 12.92 : 1.055 * Math.pow(linear, 1 / 2.4) - 0.055;
    }

    /**
     * Encode a texture.
     *
     * @param texture The texture, its maps with every level.
     * @return The payload, little-endian, from position 0 to its limit.
     * @throws IllegalArgumentException If the size is out of range, a map's length doesn't fit the
     *     size and levels, or a kind comes twice.
     */
    public static ByteBuffer encode(@NonNull Stored texture) {
        check(texture.width(), texture.height(), texture.levels());
        final int expected = expectedLength(texture.width(), texture.height(), texture.levels());
        int size = RECORD_SIZE;
        final java.util.Set<Kind> seen = java.util.EnumSet.noneOf(Kind.class);
        for (MapData map : texture.maps()) {
            if (!seen.add(map.kind())) {
                throw new IllegalArgumentException("Two " + map.kind() + " maps");
            }
            if (map.pixels().length != expected) {
                throw new IllegalArgumentException(
                        "The "
                                + map.kind()
                                + " map has "
                                + map.pixels().length
                                + " bytes, not "
                                + expected);
            }
            size += 2 * Integer.BYTES + map.pixels().length;
        }
        final ByteBuffer out = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(RECORD_SIZE)
                .putInt(texture.width())
                .putInt(texture.height())
                .putInt(texture.levels())
                .putInt(texture.maps().size());
        for (MapData map : texture.maps()) {
            out.putInt(map.kind().id).putInt(map.pixels().length).put(map.pixels());
        }
        return out.flip();
    }

    /**
     * Check a texture's size and levels.
     *
     * @param width The width.
     * @param height The height.
     * @param levels The levels.
     */
    private static void check(int width, int height, int levels) {
        if (width < 1 || height < 1 || width > MAX_SIZE || height > MAX_SIZE) {
            throw new IllegalArgumentException("Bad texture size " + width + " x " + height);
        }
        if (levels < 1 || levels > levelCount(width, height)) {
            throw new IllegalArgumentException("Bad mip level count " + levels);
        }
    }

    /**
     * How many bytes a map with some levels has.
     *
     * @param width The width.
     * @param height The height.
     * @param levels The levels.
     * @return The byte count.
     */
    private static int expectedLength(int width, int height, int levels) {
        int total = 0;
        for (int level = 0; level < levels; ++level) {
            total += levelWidth(width, level) * levelWidth(height, level) * CHANNELS;
        }
        return total;
    }

    /**
     * Decode a texture.
     *
     * @param section The section.
     * @return The texture, with the maps of kinds this code knows.
     * @throws AssetFormatException If the section isn't a texture this code can read, or is cut
     *     short.
     */
    public static Stored decode(@NonNull Section section) {
        if (section.tag() != TAG) {
            throw new AssetFormatException(
                    "Expected a TEXR section, not " + SectionTag.toString(section.tag()));
        }
        if (section.version() != VERSION) {
            throw new AssetFormatException("Unsupported TEXR version " + section.version());
        }
        final ByteBuffer in = section.data();
        if (in.remaining() < RECORD_SIZE) {
            throw new AssetFormatException("The TEXR record is cut short");
        }
        final int start = in.position();
        final int recordSize = in.getInt();
        if (recordSize < RECORD_SIZE || recordSize > in.limit() - start) {
            throw new AssetFormatException("Bad TEXR record size " + recordSize);
        }
        final int width = in.getInt();
        final int height = in.getInt();
        final int levels = in.getInt();
        final int count = in.getInt();
        try {
            check(width, height, levels);
        } catch (IllegalArgumentException e) {
            throw new AssetFormatException(e.getMessage());
        }
        in.position(start + recordSize);
        final int expected = expectedLength(width, height, levels);
        final List<MapData> maps = new ArrayList<>();
        for (int i = 0; i < count; ++i) {
            if (in.remaining() < 2 * Integer.BYTES) {
                throw new AssetFormatException("The TEXR maps are cut short");
            }
            final int kind = in.getInt();
            final int length = in.getInt();
            if (length < 0 || length > in.remaining()) {
                throw new AssetFormatException("Bad TEXR map length " + length);
            }
            final byte[] pixels = new byte[length];
            in.get(pixels);
            final Optional<Kind> known = Kind.of(kind);
            if (known.isEmpty()) {
                continue;
            }
            if (length != expected) {
                throw new AssetFormatException(
                        "The " + known.get() + " map has " + length + " bytes, not " + expected);
            }
            maps.add(new MapData(known.get(), pixels));
        }
        return new Stored(width, height, levels, List.copyOf(maps));
    }

    /**
     * Write a texture file: one {@code TEXR} section.
     *
     * @param texture The texture.
     * @param metadata The file's metadata, naming what wrote it.
     * @return The file's bytes.
     */
    public static ByteBuffer file(@NonNull Stored texture, @NonNull AssetMetadata metadata) {
        return new AssetWriter()
                .metadata(metadata)
                .section(TAG, VERSION, true, encode(texture))
                .toBuffer();
    }

    /**
     * Read the texture in a file.
     *
     * @param file The file, as {@link AssetFile} read it.
     * @return The texture.
     * @throws AssetFormatException If the file has no texture, or it can't be read.
     */
    public static Stored read(@NonNull AssetFile file) {
        return decode(
                file.section(TAG)
                        .orElseThrow(() -> new AssetFormatException("The file has no texture")));
    }

    /** Not to be made. */
    private TextureSection() {}
}
