package com.ikalagaming.factory.world.gen.data;

import com.ikalagaming.factory.FactoryStrings;
import com.ikalagaming.factory.kvt.Node;
import com.ikalagaming.factory.kvt.TreeStringSerialization;
import com.ikalagaming.factory.registry.RegistryConstants;
import com.ikalagaming.factory.world.gen.WorldgenHash;

import lombok.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The raw world generation files from some data folders, parsed but not yet checked or compiled.
 *
 * <p>Each data folder holds {@code mods/<mod>/worldgen/<kind>/<path>.kvt}, and the file's ID comes
 * from where it is: {@code mods/lotomation/worldgen/density/overworld/terrain.kvt} is the density
 * {@code lotomation:overworld/terrain}. Folders are read in order, and a file in a later folder
 * replaces one with the same kind and ID from an earlier one. Everything is kept sorted, so nothing
 * depends on the order the file system lists files in.
 */
public final class WorldgenData {

    /** The folder under a data folder that holds each mod's folder. */
    public static final String MODS_FOLDER = "mods";

    /** The folder under a mod's folder that holds world generation data. */
    public static final String WORLDGEN_FOLDER = "worldgen";

    /** The extension of data files. */
    public static final String EXTENSION = ".kvt";

    /** What each part of a data ID's path must look like. */
    private static final Pattern PATH_SEGMENT =
            Pattern.compile(RegistryConstants.RESOURCE_NAME_FORMAT);

    /** What a mod's folder name must look like. */
    private static final Pattern MOD_NAME = Pattern.compile(RegistryConstants.MOD_NAME_FORMAT);

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
     * @param dataFolders The folders, in load order: later ones replace files from earlier ones.
     * @return The files, and any problems reading them.
     */
    public static WorldgenData load(@NonNull List<Path> dataFolders) {
        WorldgenData data = new WorldgenData();
        for (Path folder : dataFolders) {
            data.loadFolder(folder);
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
     * Where a file with an ID would be, relative to a data folder.
     *
     * @param kind The kind of file.
     * @param id The data ID, like {@code lotomation:overworld/terrain}.
     * @return The path, like {@code mods/lotomation/worldgen/density/overworld/terrain.kvt}.
     */
    public static Path pathOf(@NonNull Kind kind, @NonNull String id) {
        final int colon = id.indexOf(':');
        final String mod = colon < 0 ? "" : id.substring(0, colon);
        return Path.of(
                MODS_FOLDER,
                mod,
                WORLDGEN_FOLDER,
                kind.getFolder(),
                id.substring(colon + 1) + EXTENSION);
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
     * Read one data folder.
     *
     * @param folder The data folder.
     */
    private void loadFolder(@NonNull Path folder) {
        final Path mods = folder.resolve(MODS_FOLDER);
        if (!Files.isDirectory(mods)) {
            report(folder.toString(), "WORLDGEN_NO_MODS_FOLDER", mods);
            return;
        }
        for (Path mod : sortedChildren(mods)) {
            if (!Files.isDirectory(mod)) {
                continue;
            }
            final String modName = mod.getFileName().toString();
            if (!MOD_NAME.matcher(modName).matches()) {
                report(mod.toString(), "WORLDGEN_BAD_MOD_FOLDER", modName);
                continue;
            }
            final Path worldgen = mod.resolve(WORLDGEN_FOLDER);
            if (!Files.isDirectory(worldgen)) {
                continue;
            }
            for (Path kindFolder : sortedChildren(worldgen)) {
                loadKindFolder(modName, kindFolder);
            }
        }
    }

    /**
     * Read the files of one kind from one mod.
     *
     * @param modName The mod's name, the first part of each ID.
     * @param kindFolder The kind's folder.
     */
    private void loadKindFolder(@NonNull String modName, @NonNull Path kindFolder) {
        final String folderName = kindFolder.getFileName().toString();
        final Optional<Kind> kind =
                Arrays.stream(Kind.values())
                        .filter(k -> k.getFolder().equals(folderName))
                        .findFirst();
        if (kind.isEmpty()) {
            if (Files.isDirectory(kindFolder)) {
                report(kindFolder.toString(), "WORLDGEN_UNKNOWN_KIND_FOLDER", folderName);
            }
            return;
        }
        try (Stream<Path> walk = Files.walk(kindFolder)) {
            final List<Path> found =
                    walk.filter(Files::isRegularFile)
                            .sorted(Comparator.comparing(Path::toString))
                            .toList();
            for (Path file : found) {
                final String relative = kindFolder.relativize(file).toString().replace('\\', '/');
                if (!relative.endsWith(EXTENSION)) {
                    report(file.toString(), "WORLDGEN_NOT_KVT", relative);
                    continue;
                }
                final String path = relative.substring(0, relative.length() - EXTENSION.length());
                final String id = modName + ":" + path;
                if (!isValidPath(path)) {
                    report(file.toString(), "WORLDGEN_BAD_ID", id);
                    continue;
                }
                parse(id, kind.get(), file, Files.readString(file, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            report(kindFolder.toString(), "WORLDGEN_READ_FAILED", e.getMessage());
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
        for (String segment : path.split("/", -1)) {
            if (!PATH_SEGMENT.matcher(segment).matches()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a data ID is well formed: a mod name, a colon and a path.
     *
     * @param id The ID.
     * @return True if it is valid.
     */
    public static boolean isValidId(@NonNull String id) {
        final int colon = id.indexOf(':');
        return colon > 0
                && MOD_NAME.matcher(id.substring(0, colon)).matches()
                && isValidPath(id.substring(colon + 1));
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
