package com.ikalagaming.converter.workbench;

import com.ikalagaming.converter.content.BlockContent;
import com.ikalagaming.converter.content.GameContent;
import com.ikalagaming.converter.index.AssetIndex;
import com.ikalagaming.converter.index.AssetType;
import com.ikalagaming.converter.index.EditorTypes;
import com.ikalagaming.converter.index.GameTypes;
import com.ikalagaming.graphics.ui.SavedState;
import com.ikalagaming.launcher.AppPaths;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * The workbench's asset index: the roots it covers, kept in the GUI's saved state, and the index
 * read from them in the background. A scan runs at start, from the cache file where it can, again
 * when a watched folder changes, and every few seconds in case a change went unseen. The snapshot
 * can be read from any thread; the roots are changed from the render thread.
 */
@Slf4j
public final class AssetIndexService implements AutoCloseable {
    /** The saved state entry holding the roots. */
    private static final String SAVED = "Asset roots";

    /** How often the roots are scanned even without a change seen, in seconds. */
    private static final long POLL_SECONDS = 10;

    /** How long changes are gathered before a scan, in milliseconds. */
    private static final long SETTLE_MILLIS = 300;

    /** The index. */
    private final AssetIndex index;

    /** The roots, in the order content folders are read. */
    private final List<Path> roots = new CopyOnWriteArrayList<>();

    /** Runs scans, one at a time, and the poll. */
    private final ScheduledExecutorService scanner =
            Executors.newSingleThreadScheduledExecutor(
                    runnable -> {
                        final Thread thread = new Thread(runnable, "AssetIndex");
                        thread.setDaemon(true);
                        thread.setPriority(Thread.MIN_PRIORITY);
                        return thread;
                    });

    /** Whether a scan is waiting to run. */
    private final AtomicBoolean queued = new AtomicBoolean();

    /** The latest snapshot. */
    private volatile AssetIndex.Snapshot snapshot;

    /** Whether a scan is running. */
    private volatile boolean scanning;

    /** Where the index is kept between runs. */
    private final Path cacheFile;

    /** Watches the roots' folders, or null if watching isn't possible. */
    private @Nullable WatchService watcher;

    /** The folders watched, with their keys. Scanner thread. */
    private final Map<Path, WatchKey> watched = new HashMap<>();

    /** Waits for changes. */
    private @Nullable Thread watchThread;

    /** Whether {@link #start} ran. */
    private final AtomicBoolean started = new AtomicBoolean();

    /** Make the service; {@link #start} reads the roots and starts scanning. */
    public AssetIndexService() {
        final List<AssetType> types = new ArrayList<>();
        if (GameContent.available()) {
            types.addAll(GameTypes.all());
        }
        types.addAll(EditorTypes.all());
        index = new AssetIndex(types);
        cacheFile = AppPaths.cache().resolve("asset-index.yml");
        snapshot = index.scan(List.of(), null);
    }

    /**
     * Start, once: read the saved roots, or start from the installed content folders, read the
     * cache file, and start scanning and watching. Render thread, once the GUI's saved state has
     * been read, so not while the workbench is being made.
     */
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        final Map<String, String> saved = SavedState.get(SAVED);
        if (saved != null) {
            saved.entrySet().stream()
                    .sorted(
                            Map.Entry.comparingByKey(
                                    java.util.Comparator.comparingInt(Integer::parseInt)))
                    .forEach(entry -> roots.add(Path.of(entry.getValue())));
        } else if (GameContent.available()) {
            for (Path folder : BlockContent.folders()) {
                roots.add(folder.toAbsolutePath().normalize());
            }
        }
        final AssetIndex.Snapshot cached = index.load(cacheFile, roots());
        if (cached != null) {
            snapshot = cached;
        }
        try {
            watcher = FileSystems.getDefault().newWatchService();
            watchThread = new Thread(this::watch, "AssetIndexWatch");
            watchThread.setDaemon(true);
            watchThread.start();
        } catch (IOException | UnsupportedOperationException e) {
            log.info("Not watching asset folders, polling them: {}", e.getMessage());
            watcher = null;
        }
        scanner.scheduleWithFixedDelay(this::scanNow, 0, POLL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * The roots as the index reads them.
     *
     * @return The roots.
     */
    private List<AssetIndex.Root> roots() {
        return roots.stream().map(AssetIndex.Root::of).toList();
    }

    /**
     * The folders indexed.
     *
     * @return The folders, in the order content folders are read.
     */
    public List<Path> getRoots() {
        return List.copyOf(roots);
    }

    /**
     * The index as last read.
     *
     * @return The snapshot.
     */
    public AssetIndex.Snapshot getSnapshot() {
        return snapshot;
    }

    /**
     * Whether a scan is running or waiting.
     *
     * @return True while the snapshot may be about to change.
     */
    public boolean isScanning() {
        return scanning || queued.get();
    }

    /**
     * Add a folder to the roots, last, and scan. Render thread.
     *
     * @param folder The folder.
     * @return True if it wasn't a root.
     */
    public boolean addRoot(@NonNull Path folder) {
        final Path absolute = folder.toAbsolutePath().normalize();
        if (roots.contains(absolute)) {
            return false;
        }
        roots.add(absolute);
        saveRoots();
        requestScan();
        return true;
    }

    /**
     * Take a folder out of the roots, and scan. Render thread.
     *
     * @param folder The folder.
     * @return True if it was a root.
     */
    public boolean removeRoot(@NonNull Path folder) {
        final boolean removed = roots.remove(folder.toAbsolutePath().normalize());
        if (removed) {
            saveRoots();
            requestScan();
        }
        return removed;
    }

    /** Keep the roots in the saved state. */
    private void saveRoots() {
        final Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < roots.size(); ++i) {
            values.put(Integer.toString(i), roots.get(i).toString());
        }
        SavedState.put(SAVED, values);
    }

    /** Scan soon, once, however often asked. */
    public void requestScan() {
        if (queued.compareAndSet(false, true)) {
            scanner.schedule(this::scanNow, SETTLE_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Scan now, after any scan waiting, and give the snapshot made.
     *
     * @return The snapshot, once made.
     */
    public CompletableFuture<AssetIndex.Snapshot> scan() {
        return CompletableFuture.supplyAsync(
                () -> {
                    scanNow();
                    return snapshot;
                },
                scanner);
    }

    /** Read the roots. Scanner thread. */
    private void scanNow() {
        queued.set(false);
        scanning = true;
        try {
            final List<AssetIndex.Root> current = roots();
            snapshot = index.scan(current, snapshot);
            watchFolders(current);
            AssetIndex.save(snapshot, cacheFile);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not index the asset roots: {}", e.getMessage());
        } finally {
            scanning = false;
        }
    }

    /**
     * Watch every folder under the roots, and stop watching those gone. Scanner thread.
     *
     * @param current The roots.
     */
    private void watchFolders(List<AssetIndex.Root> current) {
        if (watcher == null) {
            return;
        }
        final List<Path> folders = new ArrayList<>();
        for (AssetIndex.Root root : current) {
            if (!Files.isDirectory(root.folder())) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root.folder())) {
                walk.filter(Files::isDirectory)
                        .filter(dir -> !dir.toString().contains(".git"))
                        .forEach(folders::add);
            } catch (IOException | UncheckedIOException e) {
                log.debug("Can't list {} to watch it", root.folder(), e);
            }
        }
        watched.entrySet()
                .removeIf(
                        entry -> {
                            if (folders.contains(entry.getKey())) {
                                return false;
                            }
                            entry.getValue().cancel();
                            return true;
                        });
        for (Path folder : folders) {
            if (watched.containsKey(folder)) {
                continue;
            }
            try {
                watched.put(
                        folder,
                        folder.register(
                                watcher,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_DELETE,
                                StandardWatchEventKinds.ENTRY_MODIFY));
            } catch (IOException | ClosedWatchServiceException e) {
                log.debug("Can't watch {}", folder, e);
            }
        }
    }

    /** Wait for changes in watched folders, asking for a scan on each. Watch thread. */
    private void watch() {
        while (watcher != null) {
            final WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ClosedWatchServiceException e) {
                return;
            }
            key.pollEvents();
            key.reset();
            requestScan();
        }
    }

    @Override
    public void close() {
        scanner.shutdownNow();
        final WatchService closing = watcher;
        watcher = null;
        if (closing != null) {
            try {
                closing.close();
            } catch (IOException e) {
                log.debug("Closing the asset watcher", e);
            }
        }
        if (watchThread != null) {
            watchThread.interrupt();
        }
    }
}
