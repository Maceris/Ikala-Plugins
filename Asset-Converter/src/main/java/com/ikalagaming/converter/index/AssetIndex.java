package com.ikalagaming.converter.index;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * An index of every file under a list of roots: each file's type, its ID where it has one, what it
 * references and what references it, which of several content folders' copies of an ID wins, and
 * the references that resolve to nothing.
 *
 * <p>A root is a content folder, a plugin's {@code data} folder whose files have IDs ({@code
 * textures/block/stone.ika} in the Lotomation plugin is the texture {@code lotomation:block/stone},
 * and {@code overrides/<ns>/} replaces another namespace's), or any other folder, like an unshipped
 * art folder. Content folders are read in the roots' order, a later one's file winning over an
 * earlier one's with the same ID, as the game reads them.
 *
 * <p>{@link #scan} reads the roots into a {@link Snapshot}, reusing what an earlier snapshot read
 * of files whose size and time are unchanged; {@link #save} and {@link #load} keep that between
 * runs. Snapshots never change, so any thread can read one.
 */
@Slf4j
public final class AssetIndex {
    /** The type of files no registered type matches. */
    public static final String FILE = "file";

    /** The cache file's format. */
    private static final int FORMAT = 1;

    /** Folders never indexed. */
    private static final Set<String> SKIPPED = Set.of(".git", ".gradle", "build", "out");

    /** What a namespace looks like, as the game's. */
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");

    /** The types, in the order they are tried. */
    private final List<AssetType> types;

    /**
     * Make an index of some types.
     *
     * @param types The types, tried in order; files none matches are of type {@value #FILE}.
     */
    public AssetIndex(@NonNull List<AssetType> types) {
        this.types = List.copyOf(types);
    }

    /**
     * A folder indexed.
     *
     * @param folder The folder, absolute.
     * @param namespace Its namespace, for a content folder, or null.
     */
    public record Root(@NonNull Path folder, @Nullable String namespace) {
        /**
         * A root, a content folder if it is a plugin's {@code data} folder.
         *
         * @param folder The folder.
         * @return The root.
         */
        public static Root of(@NonNull Path folder) {
            final Path absolute = folder.toAbsolutePath().normalize();
            final Path name = absolute.getFileName();
            final Path plugin = absolute.getParent();
            if (name != null
                    && "data".equals(name.toString())
                    && plugin != null
                    && plugin.getFileName() != null) {
                final String namespace =
                        plugin.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                if (NAMESPACE.matcher(namespace).matches()) {
                    return new Root(absolute, namespace);
                }
            }
            return new Root(absolute, null);
        }

        /**
         * Whether its files have IDs.
         *
         * @return True for a content folder.
         */
        public boolean content() {
            return namespace != null;
        }
    }

    /**
     * Something a file references: by ID, resolved through the content folders; by path; or a file
     * it makes, looked for in every content folder.
     *
     * @param label What the reference is, like {@code texture} or {@code parent}.
     * @param type The type an ID or made file is of, or null for a path.
     * @param name The ID ({@code ns:path}), the made file's path under its type's folder, or for a
     *     path, the file's name as written.
     * @param path The file, absolute, for a path, else null.
     * @param optional Whether nothing being there is fine, so not a problem.
     */
    public record Ref(
            @NonNull String label,
            @Nullable String type,
            @NonNull String name,
            @Nullable Path path,
            boolean optional) {
        /**
         * A reference by ID.
         *
         * @param label What it is.
         * @param type The type the ID is of.
         * @param id The ID.
         * @return The reference.
         */
        public static Ref id(@NonNull String label, @NonNull String type, @NonNull String id) {
            return new Ref(label, type, id, null, false);
        }

        /**
         * A reference by path.
         *
         * @param label What it is.
         * @param file The file.
         * @param written Its name as written, for messages.
         * @return The reference.
         */
        public static Ref path(@NonNull String label, @NonNull Path file, @NonNull String written) {
            return new Ref(label, null, written, file.toAbsolutePath().normalize(), false);
        }

        /**
         * A file this one makes, under its type's folder in any content folder, which may not be
         * made yet.
         *
         * @param type The made file's type.
         * @param path Its path under the type's folder, without an extension.
         * @return The reference.
         */
        public static Ref makes(@NonNull String type, @NonNull String path) {
            return new Ref("makes", type, path, null, true);
        }

        /**
         * This reference, or not, as fine to be missing.
         *
         * @param isOptional Whether it may be missing.
         * @return The reference.
         */
        public Ref optional(boolean isOptional) {
            return new Ref(label, type, name, path, isOptional);
        }
    }

    /**
     * A file indexed.
     *
     * @param file The file, absolute.
     * @param root Which root it is under, by index.
     * @param type Its type's name.
     * @param id Its ID, for a file of a type with IDs in a content folder, or null.
     * @param size Its size when read.
     * @param modified Its time when read, in milliseconds.
     * @param refs What it references.
     * @param error Why its references couldn't be read, or null.
     */
    public record Entry(
            @NonNull Path file,
            int root,
            @NonNull String type,
            @Nullable String id,
            long size,
            long modified,
            @NonNull List<Ref> refs,
            @Nullable String error) {}

    /**
     * One end of a reference, as a file shows it.
     *
     * @param label What the reference is.
     * @param file The file at the other end, or null if it resolves to nothing.
     * @param name What the reference names.
     */
    public record Link(@NonNull String label, @Nullable Path file, @NonNull String name) {}

    /**
     * A reference resolving to nothing, or a file that couldn't be read.
     *
     * @param file The file with the reference.
     * @param label What the reference is.
     * @param name What it names, or why the file couldn't be read.
     */
    public record Problem(@NonNull Path file, @NonNull String label, @NonNull String name) {}

    /** An index as read at one time. Never changes. */
    public static final class Snapshot {
        /** The roots. */
        private final List<Root> roots;

        /** The entries, by file. */
        private final Map<Path, Entry> entries;

        /** What each file references, resolved. */
        private final Map<Path, List<Link>> uses = new HashMap<>();

        /** What references each file. */
        private final Map<Path, List<Link>> usedBy = new HashMap<>();

        /** Every file providing each type and ID, in root order, the winner last. */
        private final Map<String, List<Path>> providers = new HashMap<>();

        /** References resolving to nothing, and files that couldn't be read. */
        private final List<Problem> problems = new ArrayList<>();

        /** The types, by name. */
        private final Map<String, AssetType> typesByName;

        /**
         * Resolve an index.
         *
         * @param roots The roots.
         * @param entries The entries, by file.
         * @param types The types.
         */
        Snapshot(List<Root> roots, Map<Path, Entry> entries, List<AssetType> types) {
            this.roots = List.copyOf(roots);
            this.entries = Collections.unmodifiableMap(new TreeMap<>(entries));
            final Map<String, AssetType> byName = new LinkedHashMap<>();
            types.forEach(type -> byName.putIfAbsent(type.name(), type));
            typesByName = byName;
            for (Entry entry : this.entries.values()) {
                if (entry.id() != null) {
                    providers
                            .computeIfAbsent(
                                    entry.type() + "|" + entry.id(), k -> new ArrayList<>())
                            .add(entry.file());
                }
            }
            providers.values().forEach(list -> list.sort(this::byRootOrder));
            for (Entry entry : this.entries.values()) {
                if (entry.error() != null) {
                    problems.add(new Problem(entry.file(), "unreadable", entry.error()));
                }
                final List<Link> links = new ArrayList<>();
                for (Ref ref : entry.refs()) {
                    final Path target = resolve(ref);
                    links.add(new Link(ref.label(), target, ref.name()));
                    if (target == null) {
                        if (!ref.optional()) {
                            problems.add(new Problem(entry.file(), ref.label(), ref.name()));
                        }
                    } else {
                        usedBy.computeIfAbsent(target, t -> new ArrayList<>())
                                .add(new Link(ref.label(), entry.file(), ref.name()));
                    }
                }
                uses.put(entry.file(), List.copyOf(links));
            }
            usedBy.replaceAll((file, list) -> List.copyOf(list));
        }

        /**
         * Compare two files by their roots' order, then path.
         *
         * @param a One.
         * @param b The other.
         * @return Their order.
         */
        private int byRootOrder(Path a, Path b) {
            final int byRoot = Integer.compare(entries.get(a).root(), entries.get(b).root());
            return byRoot != 0 ? byRoot : a.compareTo(b);
        }

        /**
         * Where a reference leads.
         *
         * @param ref The reference.
         * @return The file, or null if it leads nowhere.
         */
        private @Nullable Path resolve(Ref ref) {
            if (ref.path() != null) {
                return entries.containsKey(ref.path()) || Files.isRegularFile(ref.path())
                        ? ref.path()
                        : null;
            }
            final AssetType type = typesByName.get(ref.type());
            if (type == null || type.kind() == null) {
                return null;
            }
            if ("makes".equals(ref.label())) {
                for (Root root : roots) {
                    if (root.content()) {
                        final Path found =
                                firstOf(root.folder().resolve(type.kind()), ref.name(), type);
                        if (found != null) {
                            return found;
                        }
                    }
                }
                return null;
            }
            final int colon = ref.name().indexOf(':');
            if (colon <= 0) {
                return null;
            }
            final String namespace = ref.name().substring(0, colon);
            final String path = ref.name().substring(colon + 1);
            for (int i = roots.size() - 1; i >= 0; --i) {
                final Root root = roots.get(i);
                if (!root.content()) {
                    continue;
                }
                final Path base =
                        namespace.equals(root.namespace())
                                ? root.folder()
                                : root.folder().resolve("overrides").resolve(namespace);
                final Path found = firstOf(base.resolve(type.kind()), path, type);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }

        /**
         * The first file of a path with one of a type's extensions that is indexed.
         *
         * @param folder The type's folder.
         * @param path The path, without an extension.
         * @param type The type.
         * @return The file, or null.
         */
        private @Nullable Path firstOf(Path folder, String path, AssetType type) {
            for (String extension : type.extensions()) {
                final Path file = folder.resolve(path + extension).normalize();
                if (entries.containsKey(file)) {
                    return file;
                }
            }
            return null;
        }

        /**
         * The roots.
         *
         * @return The roots, in order.
         */
        public List<Root> roots() {
            return roots;
        }

        /**
         * Every entry.
         *
         * @return The entries, sorted by file.
         */
        public Collection<Entry> entries() {
            return entries.values();
        }

        /**
         * A file's entry.
         *
         * @param file The file.
         * @return Its entry, or null if it isn't indexed.
         */
        public @Nullable Entry entry(@NonNull Path file) {
            return entries.get(file.toAbsolutePath().normalize());
        }

        /**
         * What a file references.
         *
         * @param file The file.
         * @return The references, each with the file it leads to, or none if it leads nowhere.
         */
        public List<Link> uses(@NonNull Path file) {
            return uses.getOrDefault(file.toAbsolutePath().normalize(), List.of());
        }

        /**
         * What references a file.
         *
         * @param file The file.
         * @return The references, each with the file it is in.
         */
        public List<Link> usedBy(@NonNull Path file) {
            return usedBy.getOrDefault(file.toAbsolutePath().normalize(), List.of());
        }

        /**
         * Every file providing the same type and ID as a file, in the order read, so the last wins.
         *
         * @param file The file.
         * @return The providers, just the file when only it does, or none if it has no ID.
         */
        public List<Path> providers(@NonNull Path file) {
            final Entry entry = entry(file);
            if (entry == null || entry.id() == null) {
                return List.of();
            }
            return List.copyOf(providers.get(entry.type() + "|" + entry.id()));
        }

        /**
         * References that resolve to nothing, and files that couldn't be read.
         *
         * @return The problems, by file.
         */
        public List<Problem> problems() {
            return Collections.unmodifiableList(problems);
        }
    }

    /**
     * Read the roots.
     *
     * @param roots The roots, content folders in the order they are read.
     * @param previous An earlier snapshot whose entries are reused for unchanged files, or null.
     * @return The snapshot.
     */
    public Snapshot scan(@NonNull List<Root> roots, @Nullable Snapshot previous) {
        final Map<Path, Entry> known = new HashMap<>();
        if (previous != null) {
            previous.entries().forEach(entry -> known.put(entry.file(), entry));
        }
        return scan(roots, known);
    }

    /**
     * Read the roots, reusing known entries of unchanged files.
     *
     * @param roots The roots.
     * @param known Entries read before, by file.
     * @return The snapshot.
     */
    private Snapshot scan(List<Root> roots, Map<Path, Entry> known) {
        final Map<Path, Entry> entries = new HashMap<>();
        for (int i = 0; i < roots.size(); ++i) {
            final Root root = roots.get(i);
            final int index = i;
            if (!Files.isDirectory(root.folder())) {
                continue;
            }
            try {
                Files.walkFileTree(
                        root.folder(),
                        new SimpleFileVisitor<>() {
                            @Override
                            public FileVisitResult preVisitDirectory(
                                    Path dir, BasicFileAttributes attributes) {
                                final Path name = dir.getFileName();
                                return !dir.equals(root.folder())
                                                && name != null
                                                && SKIPPED.contains(name.toString())
                                        ? FileVisitResult.SKIP_SUBTREE
                                        : FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFile(
                                    Path file, BasicFileAttributes attributes) {
                                final Path absolute = file.toAbsolutePath().normalize();
                                if (entries.containsKey(absolute)) {
                                    return FileVisitResult.CONTINUE;
                                }
                                final Entry old = known.get(absolute);
                                final long size = attributes.size();
                                final long modified = attributes.lastModifiedTime().toMillis();
                                entries.put(
                                        absolute,
                                        old != null
                                                        && old.root() == index
                                                        && old.size() == size
                                                        && old.modified() == modified
                                                ? old
                                                : read(absolute, index, root, size, modified));
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFileFailed(Path file, IOException e) {
                                return FileVisitResult.CONTINUE;
                            }
                        });
            } catch (IOException | UncheckedIOException e) {
                log.warn("Could not index {}: {}", root.folder(), e.getMessage());
            }
        }
        return new Snapshot(roots, entries, types);
    }

    /**
     * Read one file's entry.
     *
     * @param file The file.
     * @param index Its root's index.
     * @param root Its root.
     * @param size Its size.
     * @param modified Its time.
     * @return The entry.
     */
    private Entry read(Path file, int index, Root root, long size, long modified) {
        final String relative = root.folder().relativize(file).toString().replace('\\', '/');
        for (AssetType type : types) {
            if (!type.matches(root, relative)) {
                continue;
            }
            List<Ref> refs = List.of();
            String error = null;
            try {
                refs = List.copyOf(type.references(file, root, relative));
            } catch (IOException | RuntimeException e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            return new Entry(
                    file,
                    index,
                    type.name(),
                    idOf(root, relative, type),
                    size,
                    modified,
                    refs,
                    error);
        }
        return new Entry(file, index, FILE, null, size, modified, List.of(), null);
    }

    /**
     * A file's ID: in a content folder, its path under its type's folder without the extension, in
     * its folder's namespace or the one it overrides.
     *
     * @param root The root.
     * @param relative The file's path under it.
     * @param type Its type.
     * @return The ID, or null.
     */
    static @Nullable String idOf(Root root, String relative, AssetType type) {
        if (!root.content() || type.kind() == null) {
            return null;
        }
        String namespace = root.namespace();
        String path = relative;
        if (path.startsWith("overrides/")) {
            final int slash = path.indexOf('/', "overrides/".length());
            if (slash < 0) {
                return null;
            }
            namespace = path.substring("overrides/".length(), slash);
            path = path.substring(slash + 1);
        }
        final String prefix = type.kind() + "/";
        if (!path.startsWith(prefix)) {
            return null;
        }
        path = path.substring(prefix.length());
        for (String extension : type.extensions()) {
            if (path.endsWith(extension)) {
                return namespace + ":" + path.substring(0, path.length() - extension.length());
            }
        }
        return null;
    }

    /**
     * Save a snapshot's entries, so the next run reads only what changed.
     *
     * @param snapshot The snapshot.
     * @param file Where.
     * @throws IOException If it can't be written.
     */
    public static void save(@NonNull Snapshot snapshot, @NonNull Path file) throws IOException {
        final List<Map<String, Object>> list = new ArrayList<>();
        for (Entry entry : snapshot.entries()) {
            final Map<String, Object> map = new LinkedHashMap<>();
            map.put("file", entry.file().toString());
            map.put("root", entry.root());
            map.put("type", entry.type());
            if (entry.id() != null) {
                map.put("id", entry.id());
            }
            map.put("size", entry.size());
            map.put("modified", entry.modified());
            if (entry.error() != null) {
                map.put("error", entry.error());
            }
            final List<List<Object>> refs = new ArrayList<>();
            for (Ref ref : entry.refs()) {
                refs.add(
                        java.util.Arrays.asList(
                                ref.label(),
                                ref.type(),
                                ref.name(),
                                ref.path() == null ? null : ref.path().toString(),
                                ref.optional()));
            }
            if (!refs.isEmpty()) {
                map.put("refs", refs);
            }
            list.add(map);
        }
        final Map<String, Object> root = new LinkedHashMap<>();
        root.put("asset index", FORMAT);
        root.put("entries", list);
        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, new Yaml(options).dump(root));
    }

    /**
     * Read the entries a {@link #save} wrote, for {@link #scan} to reuse.
     *
     * @param file The file.
     * @param roots The roots they are for, which give the snapshot.
     * @return A snapshot of them, or null if there is none or it can't be read.
     */
    public @Nullable Snapshot load(@NonNull Path file, @NonNull List<Root> roots) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            final Object document =
                    new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(file));
            if (!(document instanceof Map<?, ?> root)
                    || !(root.get("asset index") instanceof Number version)
                    || version.intValue() != FORMAT
                    || !(root.get("entries") instanceof List<?> list)) {
                return null;
            }
            final Map<Path, Entry> entries = new HashMap<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    final Entry entry = entryOf(map);
                    if (entry.root() < roots.size()
                            && entry.file().startsWith(roots.get(entry.root()).folder())) {
                        entries.put(entry.file(), entry);
                    }
                }
            }
            return new Snapshot(roots, entries, types);
        } catch (IOException | RuntimeException e) {
            log.debug("Ignoring the asset index cache {}: {}", file, e.getMessage());
            return null;
        }
    }

    /**
     * An entry from its saved map.
     *
     * @param map The map.
     * @return The entry.
     */
    private static Entry entryOf(Map<?, ?> map) {
        final List<Ref> refs = new ArrayList<>();
        if (map.get("refs") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof List<?> ref && ref.size() == 5) {
                    refs.add(
                            new Ref(
                                    String.valueOf(ref.get(0)),
                                    ref.get(1) == null ? null : String.valueOf(ref.get(1)),
                                    String.valueOf(ref.get(2)),
                                    ref.get(3) == null ? null : Path.of(String.valueOf(ref.get(3))),
                                    Boolean.TRUE.equals(ref.get(4))));
                }
            }
        }
        return new Entry(
                Path.of(String.valueOf(map.get("file"))),
                ((Number) map.get("root")).intValue(),
                String.valueOf(map.get("type")),
                map.get("id") == null ? null : String.valueOf(map.get("id")),
                ((Number) map.get("size")).longValue(),
                ((Number) map.get("modified")).longValue(),
                refs,
                map.get("error") == null ? null : String.valueOf(map.get("error")));
    }
}
