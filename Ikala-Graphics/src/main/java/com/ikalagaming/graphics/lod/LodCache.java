package com.ikalagaming.graphics.lod;

import com.ikalagaming.graphics.GraphicsManager;
import com.ikalagaming.graphics.GraphicsSettings;
import com.ikalagaming.graphics.asset.AssetFile;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Built region cells on disk, one {@code .ika} file each, named by a hash of everything the cell
 * was built from (see {@link LodStore}), so a cell whose inputs haven't changed since any earlier
 * session is read instead of built.
 *
 * <p>The folder is kept under a size cap, least recently used out: a hit marks its file as used,
 * and once a save takes the folder over the cap, the oldest files go until it is under 90% of it,
 * so a full cache doesn't delete on every save. Several caches may share a folder; a file another
 * deletes while this one reads it is just a miss.
 *
 * <p>Safe from any thread.
 */
@Slf4j
public final class LodCache {

    /** The extension of a cell's file. */
    static final String EXTENSION = ".ika";

    /** The extension of a file being written. */
    private static final String WRITING = ".tmp";

    /** The share of the cap an eviction brings the folder down to. */
    private static final double EVICT_TO = 0.9;

    /** Bytes in a mebibyte. */
    private static final long BYTES_PER_MIB = 1024L * 1024L;

    /**
     * What a cache has done.
     *
     * @param hits Cells read.
     * @param misses Cells looked for and not there, or unreadable.
     * @param bytesRead Bytes read.
     * @param bytesWritten Bytes written.
     * @param evicted Files deleted to stay under the cap.
     * @param folderBytes Bytes in the folder, as this cache last knew, or -1 until it first saves,
     *     which is when it counts them.
     */
    public record Stats(
            long hits,
            long misses,
            long bytesRead,
            long bytesWritten,
            long evicted,
            long folderBytes) {}

    /** The folder. */
    private final Path folder;

    /** The most bytes to keep. */
    private final long maxBytes;

    /** Guards the folder size and eviction. */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Files a store reads clusters from on demand (see {@link LodStore#page}), which evicting
     * leaves alone.
     */
    private final Set<Path> pinned = ConcurrentHashMap.newKeySet();

    /** Bytes in the folder, once scanned, or -1. Guarded by {@link #lock}. */
    private long folderBytes = -1;

    /** Cells read. */
    private final AtomicLong hits = new AtomicLong();

    /** Cells not found or unreadable. */
    private final AtomicLong misses = new AtomicLong();

    /** Bytes read. */
    private final AtomicLong bytesRead = new AtomicLong();

    /** Bytes written. */
    private final AtomicLong bytesWritten = new AtomicLong();

    /** Files evicted. */
    private final AtomicLong evicted = new AtomicLong();

    /**
     * A cache in a folder, made if needed.
     *
     * @param folder The folder.
     * @param maxBytes The most bytes to keep there.
     */
    public LodCache(@NonNull Path folder, long maxBytes) {
        this.folder = folder;
        this.maxBytes = maxBytes;
    }

    /**
     * The cache the graphics settings ask for.
     *
     * @return The cache, or null if caching is off.
     */
    public static LodCache fromSettings() {
        final GraphicsSettings settings = GraphicsManager.getSettings();
        final String folder = settings.lodCacheDirectory;
        if (folder == null || folder.isBlank()) {
            return null;
        }
        return new LodCache(Path.of(folder), settings.lodCacheMaxBytes);
    }

    /**
     * Read a cell, if it is there and sound. A file that can't be read is a miss, and is deleted so
     * the cell's next build replaces it.
     *
     * @param key The cell's cache key.
     * @param builderVersion The builder version it must have.
     * @return The cell, or null.
     */
    LodCellFile.Stored load(@NonNull String key, int builderVersion) {
        final Path file = fileOf(key);
        if (!Files.exists(file)) {
            misses.incrementAndGet();
            return null;
        }
        try {
            final AssetFile asset = AssetFile.open(file, LodCellFile.known());
            final LodCellFile.Stored stored = LodCellFile.decode(asset, key, builderVersion);
            bytesRead.addAndGet(Files.size(file));
            // Used now, so the last to go
            Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
            hits.incrementAndGet();
            return stored;
        } catch (NoSuchFileException e) {
            misses.incrementAndGet();
            return null;
        } catch (IOException | RuntimeException e) {
            log.warn("The cached LOD cell {} can't be read, rebuilding it: {}", file, e.toString());
            misses.incrementAndGet();
            deleteQuietly(file);
            return null;
        }
    }

    /**
     * Write a cell, replacing the file whole, then keep the folder under the cap. A cell that can't
     * be written is only logged: it will be built again next time.
     *
     * @param key The cell's cache key.
     * @param builderVersion The builder version that built it.
     * @param cellKey The cell's key in the store.
     * @param cell The cell.
     */
    void save(
            @NonNull String key,
            int builderVersion,
            LodStore.@NonNull CellKey cellKey,
            LodStore.@NonNull Cell cell) {
        final Path file = fileOf(key);
        final Path writing = folder.resolve(key + EXTENSION + WRITING);
        try {
            Files.createDirectories(folder);
            final long replaced = Files.exists(file) ? Files.size(file) : 0;
            LodCellFile.encode(key, builderVersion, cellKey, cell).write(writing);
            final long size = Files.size(writing);
            try {
                Files.move(
                        writing,
                        file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(writing, file, StandardCopyOption.REPLACE_EXISTING);
            }
            bytesWritten.addAndGet(size);
            grew(size - replaced);
        } catch (IOException | UncheckedIOException e) {
            log.warn("The LOD cell {} couldn't be cached: {}", file, e.toString());
            deleteQuietly(writing);
        }
    }

    /**
     * Note the folder grew, scanning it the first time, and evict if it is over the cap.
     *
     * @param bytes How much it grew.
     */
    private void grew(long bytes) {
        lock.lock();
        try {
            if (folderBytes < 0) {
                folderBytes = scan();
            } else {
                folderBytes += bytes;
            }
            if (folderBytes > maxBytes) {
                evict();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * How many bytes the cell files in the folder take.
     *
     * @return The bytes.
     */
    private long scan() {
        long total = 0;
        for (Path file : cellFiles()) {
            try {
                total += Files.size(file);
            } catch (IOException e) {
                // Gone meanwhile
            }
        }
        return total;
    }

    /**
     * Delete the least recently used files until the folder is under 90% of the cap. Hold the lock.
     */
    private void evict() {
        final List<Path> files = cellFiles();
        final List<Dated> dated = new ArrayList<>(files.size());
        long total = 0;
        for (Path file : files) {
            try {
                final long size = Files.size(file);
                dated.add(new Dated(file, Files.getLastModifiedTime(file).toMillis(), size));
                total += size;
            } catch (IOException e) {
                // Gone meanwhile
            }
        }
        dated.sort(Comparator.comparingLong(Dated::modified));
        final long target = (long) (maxBytes * EVICT_TO);
        long removed = 0;
        int count = 0;
        for (Dated entry : dated) {
            if (total <= target) {
                break;
            }
            if (pinned.contains(entry.file())) {
                continue;
            }
            if (deleteQuietly(entry.file())) {
                total -= entry.size();
                removed += entry.size();
                ++count;
            }
        }
        folderBytes = total;
        evicted.addAndGet(count);
        if (count > 0) {
            log.info(
                    "Evicted {} cached LOD cells ({} MiB) to stay under {} MiB",
                    count,
                    removed / BYTES_PER_MIB,
                    maxBytes / BYTES_PER_MIB);
        }
    }

    /**
     * A cell file with when it was last used and its size.
     *
     * @param file The file.
     * @param modified When it was last written or read, in milliseconds.
     * @param size Its size in bytes.
     */
    private record Dated(@NonNull Path file, long modified, long size) {}

    /**
     * The cell files in the folder.
     *
     * @return Their paths.
     */
    private List<Path> cellFiles() {
        final List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            return out;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*" + EXTENSION)) {
            stream.forEach(out::add);
        } catch (IOException e) {
            log.warn("The LOD cache folder {} couldn't be listed: {}", folder, e.toString());
        }
        return out;
    }

    /**
     * Delete a file, ignoring failure.
     *
     * @param file The file.
     * @return True if it was deleted.
     */
    private static boolean deleteQuietly(@NonNull Path file) {
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Keep a cell's file from being evicted, since a store reads its clusters on demand.
     *
     * @param file The file.
     */
    void pin(@NonNull Path file) {
        pinned.add(file);
    }

    /**
     * Where a cell's file is.
     *
     * @param key The cell's cache key.
     * @return The path.
     */
    Path fileOf(@NonNull String key) {
        return folder.resolve(key + EXTENSION);
    }

    /**
     * What the cache has done.
     *
     * @return The numbers.
     */
    public Stats stats() {
        lock.lock();
        try {
            return new Stats(
                    hits.get(),
                    misses.get(),
                    bytesRead.get(),
                    bytesWritten.get(),
                    evicted.get(),
                    folderBytes);
        } finally {
            lock.unlock();
        }
    }
}
