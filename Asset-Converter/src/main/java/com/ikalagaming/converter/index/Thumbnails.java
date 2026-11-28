package com.ikalagaming.converter.index;

import com.ikalagaming.graphics.asset.AssetFile;
import com.ikalagaming.graphics.asset.TextureSection;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;

/**
 * Thumbnails of indexed files, made in the background and kept as PNG files in a cache folder. A
 * thumbnail's name hashes the file's path, size and time, those of every file it uses, and {@link
 * #VERSION}, so a change to any of them makes a new one and the old one ages out: the folder is
 * kept under a size, the least recently used dropped first. Textures and images are made here,
 * tiled 3x3 pixel-sharp so seams show; other types have none until a renderer makes them.
 */
@Slf4j
public final class Thumbnails implements AutoCloseable {
    /** Raised when the way thumbnails look changes, so old ones are made again. */
    public static final int VERSION = 1;

    /** The most pixels across a thumbnail. */
    public static final int SIZE = 128;

    /** How many times a texture repeats each way. */
    private static final int TILES = 3;

    /** The cache's size by default, in bytes. */
    public static final long DEFAULT_CAP = 64L << 20;

    /** Image files thumbnails are made of. */
    private static final Set<String> IMAGES = Set.of(".png", ".jpg", ".jpeg", ".bmp", ".gif");

    /** The cache folder. */
    private final Path folder;

    /** The most bytes the folder holds. */
    private final long cap;

    /** Makes thumbnails, one at a time. */
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(
                    runnable -> {
                        final Thread thread = new Thread(runnable, "Thumbnails");
                        thread.setDaemon(true);
                        thread.setPriority(Thread.MIN_PRIORITY);
                        return thread;
                    });

    /** How many thumbnails this made, rather than found in the cache. */
    private final java.util.concurrent.atomic.AtomicInteger made =
            new java.util.concurrent.atomic.AtomicInteger();

    /**
     * How many thumbnails this made since it started, rather than found in the cache.
     *
     * @return The count.
     */
    public int getMade() {
        return made.get();
    }

    /** Thumbnails being made, by their file. */
    private final Map<Path, CompletableFuture<Path>> making = new ConcurrentHashMap<>();

    /**
     * Use a cache folder.
     *
     * @param folder The folder, made when the first thumbnail is.
     * @param cap The most bytes it holds.
     */
    public Thumbnails(@NonNull Path folder, long cap) {
        this.folder = folder.toAbsolutePath().normalize();
        this.cap = cap;
    }

    /**
     * Whether this can make a file's thumbnail: a texture or image.
     *
     * @param entry The file's entry.
     * @return True if it can.
     */
    public static boolean canMake(AssetIndex.@NonNull Entry entry) {
        final String name = entry.file().getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".ika")) {
            return EditorTypes.TEXTURE.equals(entry.type());
        }
        final int dot = name.lastIndexOf('.');
        return dot >= 0 && IMAGES.contains(name.substring(dot));
    }

    /**
     * A file's thumbnail key: a hash of what its thumbnail shows.
     *
     * @param entry The file's entry.
     * @param snapshot The index, for what it uses.
     * @return The key, hexadecimal.
     */
    public static String key(
            AssetIndex.@NonNull Entry entry, AssetIndex.@NonNull Snapshot snapshot) {
        final StringBuilder text = new StringBuilder();
        text.append(VERSION)
                .append('|')
                .append(entry.file())
                .append('|')
                .append(entry.size())
                .append('|')
                .append(entry.modified());
        for (AssetIndex.Link link : snapshot.uses(entry.file())) {
            final AssetIndex.Entry used = link.file() == null ? null : snapshot.entry(link.file());
            text.append('|').append(link.name());
            if (used != null) {
                text.append('@').append(used.size()).append(':').append(used.modified());
            }
        }
        try {
            final byte[] hash =
                    MessageDigest.getInstance("SHA-1")
                            .digest(text.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is always there", e);
        }
    }

    /**
     * A file's thumbnail if it is made, else start making it, if this can.
     *
     * @param entry The file's entry.
     * @param snapshot The index.
     * @return The thumbnail file, or null until it is made or if this can't make it.
     */
    public @Nullable Path get(
            AssetIndex.@NonNull Entry entry, AssetIndex.@NonNull Snapshot snapshot) {
        if (!canMake(entry)) {
            return null;
        }
        final Path file = folder.resolve(key(entry, snapshot) + ".png");
        if (Files.isRegularFile(file)) {
            touch(file);
            return file;
        }
        request(entry, file);
        return null;
    }

    /**
     * Whether a file's thumbnail is in the cache, without making it.
     *
     * @param entry The file's entry.
     * @param snapshot The index.
     * @return True if it is.
     */
    public boolean has(AssetIndex.@NonNull Entry entry, AssetIndex.@NonNull Snapshot snapshot) {
        return canMake(entry) && Files.isRegularFile(folder.resolve(key(entry, snapshot) + ".png"));
    }

    /**
     * Make a file's thumbnail, if it isn't made or being made.
     *
     * @param entry The file's entry.
     * @param snapshot The index.
     * @return The thumbnail file once made, or null if this can't make it.
     */
    public @Nullable CompletableFuture<Path> make(
            AssetIndex.@NonNull Entry entry, AssetIndex.@NonNull Snapshot snapshot) {
        if (!canMake(entry)) {
            return null;
        }
        final Path file = folder.resolve(key(entry, snapshot) + ".png");
        if (Files.isRegularFile(file)) {
            return CompletableFuture.completedFuture(file);
        }
        return request(entry, file);
    }

    /**
     * Start making a thumbnail, once.
     *
     * @param entry The file's entry.
     * @param file The thumbnail file.
     * @return The thumbnail file once made.
     */
    private CompletableFuture<Path> request(AssetIndex.Entry entry, Path file) {
        return making.computeIfAbsent(
                file,
                f ->
                        CompletableFuture.supplyAsync(
                                        () -> {
                                            write(entry.file(), f);
                                            return f;
                                        },
                                        worker)
                                .whenComplete((done, error) -> making.remove(f)));
    }

    /**
     * Make and write a thumbnail, then keep the folder under its size.
     *
     * @param source The texture or image.
     * @param file The thumbnail file.
     */
    private void write(Path source, Path file) {
        try {
            final BufferedImage thumbnail = tile(read(source));
            Files.createDirectories(folder);
            final Path temporary = Files.createTempFile(folder, "making", ".png");
            ImageIO.write(thumbnail, "png", temporary.toFile());
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            made.incrementAndGet();
            trim();
        } catch (IOException | RuntimeException e) {
            throw new UncheckedIOException(
                    new IOException("Can't make a thumbnail of " + source.getFileName(), e));
        }
    }

    /**
     * Read a texture's base color or an image.
     *
     * @param source The file.
     * @return The image.
     * @throws IOException If it can't be read.
     */
    static BufferedImage read(Path source) throws IOException {
        if (source.toString().toLowerCase(Locale.ROOT).endsWith(".ika")) {
            final TextureSection.Stored stored = TextureSection.read(AssetFile.open(source));
            final byte[] pixels =
                    stored.map(TextureSection.Kind.COLOR)
                            .orElseThrow(() -> new IOException("The texture has no color"))
                            .pixels();
            final BufferedImage image =
                    new BufferedImage(stored.width(), stored.height(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < stored.height(); ++y) {
                for (int x = 0; x < stored.width(); ++x) {
                    final int at = (y * stored.width() + x) * 4;
                    image.setRGB(
                            x,
                            y,
                            (pixels[at + 3] & 0xFF) << 24
                                    | (pixels[at] & 0xFF) << 16
                                    | (pixels[at + 1] & 0xFF) << 8
                                    | pixels[at + 2] & 0xFF);
                }
            }
            return image;
        }
        final BufferedImage image = ImageIO.read(source.toFile());
        if (image == null) {
            throw new IOException("Not an image");
        }
        return image;
    }

    /**
     * A thumbnail of an image: tiled 3x3 at a whole-number scale when it fits, else one tile shrunk
     * to fit, every pixel sharp.
     *
     * @param source The image.
     * @return The thumbnail.
     */
    static BufferedImage tile(BufferedImage source) {
        final int width = source.getWidth();
        final int height = source.getHeight();
        final int largest = Math.max(width, height);
        final int outWidth;
        final int outHeight;
        final double step;
        final int tiles;
        if (TILES * largest <= SIZE) {
            tiles = TILES;
            final int scale = SIZE / (TILES * largest);
            outWidth = TILES * width * scale;
            outHeight = TILES * height * scale;
            step = 1.0 / scale;
        } else {
            tiles = 1;
            final double fit = Math.min(1.0, SIZE / (double) largest);
            outWidth = Math.max(1, (int) Math.round(width * fit));
            outHeight = Math.max(1, (int) Math.round(height * fit));
            step = 1.0 / fit;
        }
        final BufferedImage out =
                new BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < outHeight; ++y) {
            for (int x = 0; x < outWidth; ++x) {
                final int sx = Math.min((int) (x * step), tiles * width - 1) % width;
                final int sy = Math.min((int) (y * step), tiles * height - 1) % height;
                out.setRGB(x, y, source.getRGB(sx, sy));
            }
        }
        return out;
    }

    /**
     * Mark a thumbnail used now, so it is kept over older ones.
     *
     * @param file The thumbnail.
     */
    private static void touch(Path file) {
        try {
            Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
        } catch (IOException e) {
            log.debug("Can't mark the thumbnail {} used", file, e);
        }
    }

    /** Delete the least recently used thumbnails until the folder is under its size. */
    private void trim() {
        final List<Path> files = new ArrayList<>();
        try (Stream<Path> list = Files.list(folder)) {
            list.filter(f -> f.toString().endsWith(".png")).forEach(files::add);
        } catch (IOException | UncheckedIOException e) {
            return;
        }
        long total = 0;
        final Map<Path, long[]> stats = new ConcurrentHashMap<>();
        for (Path file : files) {
            try {
                final long size = Files.size(file);
                stats.put(file, new long[] {size, Files.getLastModifiedTime(file).toMillis()});
                total += size;
            } catch (IOException e) {
                // Gone already
            }
        }
        if (total <= cap) {
            return;
        }
        final List<Path> oldestFirst = new ArrayList<>(stats.keySet());
        oldestFirst.sort(Comparator.comparingLong(f -> stats.get(f)[1]));
        for (Path file : oldestFirst) {
            if (total <= cap) {
                break;
            }
            try {
                Files.deleteIfExists(file);
                total -= stats.get(file)[0];
            } catch (IOException e) {
                log.debug("Can't drop the thumbnail {}", file, e);
            }
        }
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }
}
