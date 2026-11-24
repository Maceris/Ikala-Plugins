package com.ikalagaming.factory.world.gen.data;

import com.ikalagaming.factory.FactoryStrings;
import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.factory.kvt.TreeStringSerialization;
import com.ikalagaming.factory.registry.ContentFolders;
import com.ikalagaming.factory.world.gen.WorldgenHash;

import lombok.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * The raw world generation files from some data folders, parsed but not yet checked or compiled.
 *
 * <p>Each content folder ({@link ContentFolders}) holds {@code worldgen/<kind>/<path>.kvt}, and the
 * file's ID comes from where it is: {@code worldgen/density/overworld/terrain.kvt} in the {@code
 * Lotomation} plugin's data folder is the density {@code lotomation:overworld/terrain}. Files under
 * {@code overrides/<namespace>/worldgen/} replace another plugin's. Folders are read in order, and
 * a file in a later folder replaces one with the same kind and ID from an earlier one. Everything
 * is kept sorted, so nothing depends on the order the file system lists files in.
 */
public final class WorldgenData {

    /** The folder under a content folder that holds world generation data. */
    public static final String WORLDGEN_FOLDER = "worldgen";

    /** The extension of data files. */
    public static final String EXTENSION = ".kvt";

    /**
     * One parsed file.
     *
     * @param id The data ID, like {@code lotomation:overworld/terrain}.
     * @param kind What kind of file it is.
     * @param path Where it was read from.
     * @param root Its contents.
     * @param textHash A hash of the file's text, for {@link #contentHash()}.
     */
    public record DataFile(
            @NonNull String id,
            @NonNull Kind kind,
            @NonNull Path path,
            @NonNull Node root,
            long textHash) {}

    /** Every file, by kind then ID. */
    private final Map<Kind, SortedMap<String, DataFile>> files = new EnumMap<>(Kind.class);

    /** Problems found while reading. */
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    private WorldgenData() {
        for (Kind kind : Kind.values()) {
            files.put(kind, new TreeMap<>());
        }
    }

    /**
     * Read every world generation file in some data folders.
     *
     * @param dataFolders The content folders, in load order: later ones replace files from earlier
     *     ones.
     * @return The files, and any problems reading them.
     */
    public static WorldgenData load(@NonNull List<Path> dataFolders) {
        WorldgenData data = new WorldgenData();
        for (Path folder : dataFolders) {
            data.checkFolder(folder);
        }
        for (Kind kind : Kind.values()) {
            data.loadKind(dataFolders, kind);
        }
        return data;
    }

    /**
     * Build a data set from text, for tests and tools that don't read files.
     *
     * @param sources The file contents by kind and ID.
     * @return The parsed files, and any problems parsing them.
     */
    public static WorldgenData fromText(@NonNull Map<Kind, Map<String, String>> sources) {
        WorldgenData data = new WorldgenData();
        sources.forEach(
                (kind, byId) ->
                        new TreeMap<>(byId)
                                .forEach(
                                        (id, text) ->
                                                data.parse(id, kind, pathOf(kind, id), text)));
        return data;
    }

    /**
     * Where a file with an ID would be, relative to its own plugin's content folder.
     *
     * @param kind The kind of file.
     * @param id The data ID, like {@code lotomation:overworld/terrain}.
     * @return The path, like {@code worldgen/density/overworld/terrain.kvt}.
     */
    public static Path pathOf(@NonNull Kind kind, @NonNull String id) {
        final int colon = id.indexOf(':');
        return Path.of(WORLDGEN_FOLDER, kind.getFolder(), id.substring(colon + 1) + EXTENSION);
    }

    /**
     * A file, if there is one.
     *
     * @param kind The kind of file.
     * @param id The data ID.
     * @return The file.
     */
    public Optional<DataFile> get(@NonNull Kind kind, @NonNull String id) {
        return Optional.ofNullable(files.get(kind).get(id));
    }

    /**
     * Every file of a kind.
     *
     * @param kind The kind of file.
     * @return The files, sorted by ID.
     */
    public Collection<DataFile> all(@NonNull Kind kind) {
        return Collections.unmodifiableCollection(files.get(kind).values());
    }

    /**
     * A hash of every file's kind, ID and text, in sorted order, so it changes with any edit and
     * doesn't depend on the order folders were read in. Saves and exported corpora record it, to
     * tell when the data they were made with has changed.
     *
     * @return The hash.
     */
    public long contentHash() {
        long hash = WorldgenHash.FNV_OFFSET;
        for (Kind kind : Kind.values()) {
            for (DataFile file : files.get(kind).values()) {
                hash = WorldgenHash.fnvAdd(hash, WorldgenHash.fnv1a64(kind.getFolder()));
                hash = WorldgenHash.fnvAdd(hash, WorldgenHash.fnv1a64(file.id()));
                hash = WorldgenHash.fnvAdd(hash, file.textHash());
            }
        }
        return hash;
    }

    /**
     * The problems found while reading.
     *
     * @return The diagnostics.
     */
    public List<Diagnostic> getDiagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }

    /**
     * Check a content folder: that it has a namespace, and that its world generation folders are
     * ones this knows.
     *
     * @param folder The content folder.
     */
    private void checkFolder(@NonNull Path folder) {
        if (ContentFolders.namespace(folder).isEmpty()) {
            report(folder.toString(), "WORLDGEN_BAD_MOD_FOLDER", folder);
            return;
        }
        checkKindFolders(folder.resolve(WORLDGEN_FOLDER));
        final Path overrides = folder.resolve(ContentFolders.OVERRIDES_FOLDER);
        if (Files.isDirectory(overrides)) {
            for (Path other : sortedChildren(overrides)) {
                checkKindFolders(other.resolve(WORLDGEN_FOLDER));
            }
        }
    }

    /**
     * Report folders in a world generation folder that aren't a kind.
     *
     * @param worldgen The world generation folder, which may not exist.
     */
    private void checkKindFolders(@NonNull Path worldgen) {
        if (!Files.isDirectory(worldgen)) {
            return;
        }
        for (Path kindFolder : sortedChildren(worldgen)) {
            final String folderName = kindFolder.getFileName().toString();
            if (Files.isDirectory(kindFolder)
                    && Arrays.stream(Kind.values())
                            .noneMatch(k -> k.getFolder().equals(folderName))) {
                report(kindFolder.toString(), "WORLDGEN_UNKNOWN_KIND_FOLDER", folderName);
            }
        }
    }

    /**
     * Read every file of one kind from the content folders.
     *
     * @param folders The content folders, in load order.
     * @param kind The kind of file.
     */
    private void loadKind(@NonNull List<Path> folders, @NonNull Kind kind) {
        final ContentFolders.Listing listing =
                ContentFolders.files(folders, WORLDGEN_FOLDER + "/" + kind.getFolder(), EXTENSION);
        for (ContentFolders.Skipped skipped : listing.skipped()) {
            final String file = skipped.file().toString();
            if (skipped.problem() == ContentFolders.Problem.WRONG_EXTENSION) {
                report(file, "WORLDGEN_NOT_KVT", skipped.file().getFileName());
            } else {
                report(file, "WORLDGEN_BAD_ID", skipped.file().getFileName());
            }
        }
        for (Map.Entry<String, Path> entry : listing.files().entrySet()) {
            try {
                parse(
                        entry.getKey(),
                        kind,
                        entry.getValue(),
                        Files.readString(entry.getValue(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                report(entry.getValue().toString(), "WORLDGEN_READ_FAILED", e.getMessage());
            }
        }
    }

    /**
     * Parse one file's text and keep it, replacing any earlier file with the same kind and ID.
     *
     * @param id The data ID.
     * @param kind The kind of file.
     * @param path Where it came from.
     * @param text Its contents.
     */
    private void parse(
            @NonNull String id, @NonNull Kind kind, @NonNull Path path, @NonNull String text) {
        Optional<Node> root;
        try {
            root = TreeStringSerialization.fromString(text);
        } catch (RuntimeException e) {
            // Some malformed text, like a bare number in a [N;...] array, throws instead
            root = Optional.empty();
        }
        if (root.isEmpty()) {
            report(id, "WORLDGEN_PARSE_FAILED", path);
            return;
        }
        files.get(kind)
                .put(id, new DataFile(id, kind, path, root.get(), WorldgenHash.fnv1a64(text)));
    }

    /**
     * Whether the path part of a data ID is well formed.
     *
     * @param path The path, like {@code overworld/terrain}.
     * @return True if every part is a valid resource name.
     */
    public static boolean isValidPath(@NonNull String path) {
        return ContentFolders.isValidPath(path);
    }

    /**
     * Whether a data ID is well formed: a mod name, a colon and a path.
     *
     * @param id The ID.
     * @return True if it is valid.
     */
    public static boolean isValidId(@NonNull String id) {
        return ContentFolders.isValidId(id);
    }

    /**
     * The entries of a folder, sorted by name.
     *
     * @param folder The folder.
     * @return Its entries.
     */
    private List<Path> sortedChildren(@NonNull Path folder) {
        try (Stream<Path> list = Files.list(folder)) {
            return list.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        } catch (IOException e) {
            report(folder.toString(), "WORLDGEN_READ_FAILED", e.getMessage());
            return List.of();
        }
    }

    /**
     * Record a problem with a file as a whole.
     *
     * @param file The file or folder.
     * @param code The message key.
     * @param args The message's values.
     */
    private void report(@NonNull String file, @NonNull String code, Object... args) {
        diagnostics.add(
                new Diagnostic(
                        file,
                        "",
                        Diagnostic.Severity.ERROR,
                        code,
                        FactoryStrings.format(code, args)));
    }
}
