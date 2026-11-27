package com.ikalagaming.converter.texture;

import com.ikalagaming.graphics.asset.AssetFile;
import com.ikalagaming.graphics.asset.AssetMetadata;
import com.ikalagaming.graphics.asset.TextureSection;

import lombok.NonNull;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 * Turns a texture's PNG maps into the one {@code .ika} the game ships: {@code <name>.png} for the
 * base color, and beside it {@code <name>.normal.png}, {@code .orm.png}, {@code .emissive.png} and
 * {@code .mask.png} where they exist, all one size. Each map gets its mip levels (see {@link
 * TextureSection#withMips}). The PNGs come from texture graphs or are painted by hand; either way
 * they are sources, kept apart from what ships. A file whose maps wouldn't change is left alone,
 * even if the converter's version did, so converting a folder doesn't churn files.
 */
public final class TextureConvert {
    /** What a PNG's name ends with. */
    public static final String PNG = ".png";

    /** The ending of a height map, which is a source for normals and isn't shipped. */
    public static final String HEIGHT_SUFFIX = ".height";

    /** What wrote the files, in their metadata. */
    public static final String EXPORTER = "Ikala Asset Editor textures";

    /** Not made. */
    private TextureConvert() {}

    /**
     * What converting did to one texture.
     *
     * @param source The base color PNG.
     * @param target The {@code .ika} file.
     * @param written Whether the file was written, rather than left as it was.
     */
    public record Converted(Path source, Path target, boolean written) {}

    /**
     * Convert one texture.
     *
     * @param base The base color PNG, {@code <name>.png}.
     * @param target The {@code .ika} file to write.
     * @param version The converter's version, for the file's metadata.
     * @return What happened.
     * @throws IOException If a map can't be read or the file can't be written.
     * @throws IllegalArgumentException If the maps aren't all one size.
     */
    public static Converted convert(
            @NonNull Path base, @NonNull Path target, @NonNull String version) throws IOException {
        final TextureSection.Stored texture = read(base);
        if (Files.isRegularFile(target)) {
            try {
                if (same(texture, TextureSection.read(AssetFile.open(target)))) {
                    return new Converted(base, target, false);
                }
            } catch (RuntimeException e) {
                // Unreadable or another kind of file: written over below
            }
        }
        final ByteBuffer bytes =
                TextureSection.file(
                        texture,
                        AssetMetadata.of(EXPORTER, version)
                                .with(AssetMetadata.SOURCE, base.getFileName().toString()));
        Files.createDirectories(target.toAbsolutePath().getParent());
        try (FileChannel channel =
                FileChannel.open(
                        target,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE)) {
            while (bytes.hasRemaining()) {
                channel.write(bytes);
            }
        }
        return new Converted(base, target, true);
    }

    /**
     * Read a texture's PNG maps into a texture with mip levels.
     *
     * @param base The base color PNG.
     * @return The texture.
     * @throws IOException If a map can't be read.
     * @throws IllegalArgumentException If the maps aren't all one size.
     */
    public static TextureSection.Stored read(@NonNull Path base) throws IOException {
        final String name = stem(base);
        int width = -1;
        int height = -1;
        final List<TextureSection.MapData> maps = new ArrayList<>();
        for (TextureSection.Kind kind : TextureSection.Kind.values()) {
            final Path file = base.resolveSibling(name + kind.suffix + PNG);
            if (!Files.isRegularFile(file)) {
                if (kind == TextureSection.Kind.COLOR) {
                    throw new IOException("There is no " + file);
                }
                continue;
            }
            final BufferedImage image = ImageIO.read(file.toFile());
            if (image == null) {
                throw new IOException(file + " isn't an image");
            }
            if (width < 0) {
                width = image.getWidth();
                height = image.getHeight();
            } else if (image.getWidth() != width || image.getHeight() != height) {
                throw new IllegalArgumentException(
                        file.getFileName()
                                + " is "
                                + image.getWidth()
                                + " x "
                                + image.getHeight()
                                + ", not "
                                + width
                                + " x "
                                + height
                                + " like the base color");
            }
            maps.add(
                    new TextureSection.MapData(
                            kind, TextureSection.withMips(kind, rgba(image), width, height)));
        }
        return new TextureSection.Stored(
                width, height, TextureSection.levelCount(width, height), List.copyOf(maps));
    }

    /**
     * Convert every texture under a folder of PNGs into a folder of {@code .ika} files with the
     * same layout: {@code art/textures/block/stone.png} and its maps become {@code
     * data/textures/block/stone.ika}.
     *
     * @param sources The folder of PNGs.
     * @param targets The folder the files go in.
     * @param version The converter's version.
     * @return What happened to each texture, in name order.
     * @throws IOException If the folder can't be listed, or a texture can't be converted.
     */
    public static List<Converted> convertAll(
            @NonNull Path sources, @NonNull Path targets, @NonNull String version)
            throws IOException {
        final List<Converted> done = new ArrayList<>();
        for (Path base : bases(sources)) {
            final Path relative = sources.relativize(base);
            final String name = stem(base);
            final Path target = targets.resolve(relative).resolveSibling(name + ".ika");
            try {
                done.add(convert(base, target, version));
            } catch (IllegalArgumentException e) {
                throw new IOException(base + ": " + e.getMessage(), e);
            }
        }
        return done;
    }

    /**
     * Every base color PNG under a folder: PNGs whose names don't end in a map's suffix.
     *
     * @param folder The folder.
     * @return The files, in name order.
     * @throws IOException If it can't be listed.
     */
    public static List<Path> bases(@NonNull Path folder) throws IOException {
        try (Stream<Path> files = Files.walk(folder)) {
            return files.filter(f -> f.getFileName().toString().endsWith(PNG))
                    .filter(f -> isBase(stem(f)))
                    .sorted()
                    .toList();
        }
    }

    /**
     * Whether a PNG's name, without ".png", is a base color rather than one of its maps.
     *
     * @param name The name.
     * @return True for a base color.
     */
    static boolean isBase(String name) {
        if (name.endsWith(HEIGHT_SUFFIX)) {
            return false;
        }
        for (TextureSection.Kind kind : TextureSection.Kind.values()) {
            if (!kind.suffix.isEmpty() && name.endsWith(kind.suffix)) {
                return false;
            }
        }
        return true;
    }

    /**
     * A PNG's name without ".png".
     *
     * @param file The file.
     * @return The name.
     */
    static String stem(Path file) {
        final String name = file.getFileName().toString();
        return name.endsWith(PNG) ? name.substring(0, name.length() - PNG.length()) : name;
    }

    /**
     * Whether two textures hold the same maps.
     *
     * @param a One.
     * @param b The other.
     * @return True if the same size, levels and maps.
     */
    public static boolean same(TextureSection.Stored a, TextureSection.Stored b) {
        if (a.width() != b.width()
                || a.height() != b.height()
                || a.levels() != b.levels()
                || a.maps().size() != b.maps().size()) {
            return false;
        }
        for (TextureSection.MapData map : a.maps()) {
            final var other = b.map(map.kind());
            if (other.isEmpty() || !Arrays.equals(map.pixels(), other.get().pixels())) {
                return false;
            }
        }
        return true;
    }

    /**
     * An image's pixels, rows from the top, RGBA bytes.
     *
     * @param image The image.
     * @return The pixels.
     */
    public static byte[] rgba(@NonNull BufferedImage image) {
        final int width = image.getWidth();
        final int height = image.getHeight();
        final byte[] out = new byte[width * height * 4];
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                final int argb = image.getRGB(x, y);
                final int at = (y * width + x) * 4;
                out[at] = (byte) (argb >> 16);
                out[at + 1] = (byte) (argb >> 8);
                out[at + 2] = (byte) argb;
                out[at + 3] = (byte) (argb >>> 24);
            }
        }
        return out;
    }
}
