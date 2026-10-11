package com.ikalagaming.factory.lab;

import com.ikalagaming.factory.registry.DefinitionLoader;
import com.ikalagaming.factory.world.gen.data.Diagnostic;
import com.ikalagaming.factory.world.gen.data.WorldgenData;
import com.ikalagaming.factory.world.gen.debug.WorldgenDebug;

import lombok.NonNull;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * The world generation data the Lab is looking at: which data folders, world type and seed, the
 * last good compile of them, and the problems the latest one found. When files change it reloads,
 * and if the new data has errors it keeps showing the last good world, marked stale, so a typo
 * never blanks the view.
 *
 * <p>Loading is slow enough to keep off the render thread, so it runs wherever {@link #load} is
 * called from, and reports through a callback.
 */
@Slf4j
public final class LabSession {

    /**
     * What to load.
     *
     * @param dataFolders The data folders, in load order.
     * @param worldType The world type's ID.
     * @param seed The world seed.
     */
    public record Settings(@NonNull List<Path> dataFolders, @NonNull String worldType, long seed) {
        /**
         * The same settings with another seed.
         *
         * @param newSeed The seed.
         * @return The new settings.
         */
        public Settings withSeed(long newSeed) {
            return new Settings(dataFolders, worldType, newSeed);
        }
    }

    /**
     * The result of a load.
     *
     * @param settings What was loaded.
     * @param debug The world to show: this load's, or the last good one if this load failed.
     * @param diagnostics The problems this load found.
     * @param stale Whether {@code debug} is from an earlier load, because this one failed.
     * @param dataHash The data's content hash.
     * @param version Counts loads, so views know when to redraw.
     */
    public record State(
            @NonNull Settings settings,
            WorldgenDebug debug,
            @NonNull List<Diagnostic> diagnostics,
            boolean stale,
            long dataHash,
            long version) {}

    /** Told about every load, on the thread that loaded. */
    private final Consumer<State> listener;

    /** The latest state. */
    private volatile State state;

    /** When each data file was last changed, for noticing edits. */
    private Map<Path, Long> stamps = Map.of();

    /**
     * Start a session; nothing is loaded until {@link #load} is called.
     *
     * @param listener Told about every load, on the thread that loaded.
     */
    public LabSession(@NonNull Consumer<State> listener) {
        this.listener = listener;
    }

    /**
     * The latest state.
     *
     * @return The state, or null before the first load.
     */
    public State getState() {
        return state;
    }

    /**
     * Load data, blocking. Keeps the last good world if this one fails.
     *
     * @param settings What to load.
     * @return The new state.
     */
    @Synchronized
    public State load(@NonNull Settings settings) {
        stamps = stamp(settings.dataFolders());
        final WorldgenData data = WorldgenData.load(settings.dataFolders());
        final WorldgenDebug.LoadResult result =
                WorldgenDebug.load(settings.worldType(), data, settings.seed(), blocks(settings));
        final State previous = state;
        final boolean failed = result.debug().isEmpty();
        // A failed load keeps the last good world only if it is the same world type and seed
        final boolean keep =
                failed
                        && previous != null
                        && previous.debug() != null
                        && previous.settings().worldType().equals(settings.worldType())
                        && previous.settings().seed() == settings.seed();
        final State next =
                new State(
                        settings,
                        failed ? (keep ? previous.debug() : null) : result.debug().get(),
                        result.diagnostics(),
                        failed && keep,
                        data.contentHash(),
                        previous == null ? 1 : previous.version() + 1);
        state = next;
        listener.accept(next);
        return next;
    }

    /**
     * Reload if any data file changed since the last load, blocking.
     *
     * @return True if it reloaded.
     */
    @Synchronized
    public boolean reloadIfChanged() {
        final State current = state;
        if (current == null) {
            return false;
        }
        if (stamp(current.settings().dataFolders()).equals(stamps)) {
            return false;
        }
        load(current.settings());
        return true;
    }

    /**
     * The blocks the data folders define, for checking block IDs.
     *
     * @param settings What is being loaded.
     * @return The block IDs, or null if no folder defines blocks.
     */
    private static Set<String> blocks(@NonNull Settings settings) {
        Set<String> ids = new TreeSet<>();
        boolean any = false;
        for (Path folder : settings.dataFolders()) {
            if (Files.isRegularFile(folder.resolve(DefinitionLoader.BLOCKS_FILE))) {
                any = true;
                ids.addAll(DefinitionLoader.blockIds(folder));
            }
        }
        return any ? ids : null;
    }

    /**
     * When each data file was last changed.
     *
     * @param folders The data folders.
     * @return Each regular file's modification time, by path.
     */
    static Map<Path, Long> stamp(@NonNull List<Path> folders) {
        Map<Path, Long> found = new HashMap<>();
        for (Path folder : folders) {
            if (!Files.isDirectory(folder)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(folder)) {
                walk.filter(Files::isRegularFile)
                        .forEach(
                                file -> {
                                    try {
                                        found.put(file, Files.getLastModifiedTime(file).toMillis());
                                    } catch (IOException e) {
                                        throw new UncheckedIOException(e);
                                    }
                                });
            } catch (IOException | UncheckedIOException e) {
                log.debug("Could not read {} for changes: {}", folder, e.getMessage());
            }
        }
        return found;
    }
}
