package com.ikalagaming.converter.index;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Named collections of assets, kept in a file in the editor's state folder: saved searches, which
 * find what matches them each time, and hand-picked sets of files. Not thread safe.
 */
@Slf4j
public final class AssetCollections {
    /** The file's format. */
    private static final int FORMAT = 1;

    /**
     * A collection.
     *
     * @param name Its name.
     * @param search The search it saves, or null for a hand-picked set.
     * @param files The files picked, for a set.
     */
    public record Collection(
            @NonNull String name, @Nullable AssetSearch.Query search, @NonNull Set<Path> files) {
        /** Copy the files. */
        public Collection {
            files = Set.copyOf(files);
        }

        /**
         * Whether it is a saved search.
         *
         * @return True for a search, false for a set.
         */
        public boolean isSearch() {
            return search != null;
        }
    }

    /** Where they are kept. */
    private final Path file;

    /** The collections, by name, in the order made. */
    private final Map<String, Collection> collections = new LinkedHashMap<>();

    /**
     * Read the collections from a file, or start with none.
     *
     * @param file The file.
     */
    public AssetCollections(@NonNull Path file) {
        this.file = file;
        read();
    }

    /**
     * Every collection.
     *
     * @return Them, in the order made.
     */
    public List<Collection> all() {
        return List.copyOf(collections.values());
    }

    /**
     * A collection by name.
     *
     * @param name The name.
     * @return It, or null.
     */
    public @Nullable Collection get(@NonNull String name) {
        return collections.get(name);
    }

    /**
     * Save a search under a name, replacing any collection with that name.
     *
     * @param name The name.
     * @param search The search.
     */
    public void saveSearch(@NonNull String name, AssetSearch.@NonNull Query search) {
        collections.put(name, new Collection(name, search, Set.of()));
        write();
    }

    /**
     * Add a file to a hand-picked set, making the set if there is none by that name.
     *
     * @param name The set's name.
     * @param picked The file.
     * @return False if a saved search has that name.
     */
    public boolean add(@NonNull String name, @NonNull Path picked) {
        final Collection old = collections.get(name);
        if (old != null && old.isSearch()) {
            return false;
        }
        final Set<Path> files = new LinkedHashSet<>(old == null ? Set.of() : old.files());
        files.add(picked.toAbsolutePath().normalize());
        collections.put(name, new Collection(name, null, files));
        write();
        return true;
    }

    /**
     * Take a file out of a hand-picked set.
     *
     * @param name The set's name.
     * @param picked The file.
     */
    public void remove(@NonNull String name, @NonNull Path picked) {
        final Collection old = collections.get(name);
        if (old == null || old.isSearch()) {
            return;
        }
        final Set<Path> files = new LinkedHashSet<>(old.files());
        files.remove(picked.toAbsolutePath().normalize());
        collections.put(name, new Collection(name, null, files));
        write();
    }

    /**
     * Delete a collection.
     *
     * @param name Its name.
     */
    public void delete(@NonNull String name) {
        if (collections.remove(name) != null) {
            write();
        }
    }

    /** Read the file, if there is one. */
    private void read() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            final Object document =
                    new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(file));
            if (!(document instanceof Map<?, ?> root)
                    || !(root.get("collections") instanceof List<?> list)) {
                return;
            }
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map) || map.get("name") == null) {
                    continue;
                }
                final String name = String.valueOf(map.get("name"));
                AssetSearch.Query search = null;
                if (map.get("search") instanceof Map<?, ?> query) {
                    search =
                            new AssetSearch.Query(
                                    query.get("text") == null
                                            ? ""
                                            : String.valueOf(query.get("text")),
                                    text(query.get("type")),
                                    query.get("namespace") == null
                                            ? null
                                            : String.valueOf(query.get("namespace")),
                                    query.get("folder") == null
                                            ? null
                                            : Path.of(String.valueOf(query.get("folder"))));
                }
                final Set<Path> files = new LinkedHashSet<>();
                if (map.get("files") instanceof List<?> paths) {
                    paths.forEach(path -> files.add(Path.of(String.valueOf(path))));
                }
                collections.put(name, new Collection(name, search, files));
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Could not read the asset collections {}: {}", file, e.getMessage());
        }
    }

    /**
     * A YAML value as text.
     *
     * @param value The value.
     * @return Its text, or null if absent or blank.
     */
    private static @Nullable String text(@Nullable Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    /** Write the file. */
    private void write() {
        final List<Map<String, Object>> list = new ArrayList<>();
        for (Collection collection : collections.values()) {
            final Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", collection.name());
            if (collection.search() != null) {
                final Map<String, Object> query = new LinkedHashMap<>();
                query.put("text", collection.search().text());
                if (collection.search().type() != null) {
                    query.put("type", collection.search().type());
                }
                if (collection.search().namespace() != null) {
                    query.put("namespace", collection.search().namespace());
                }
                if (collection.search().folder() != null) {
                    query.put("folder", collection.search().folder().toString());
                }
                map.put("search", query);
            } else {
                map.put("files", collection.files().stream().map(Path::toString).sorted().toList());
            }
            list.add(map);
        }
        final Map<String, Object> root = new LinkedHashMap<>();
        root.put("asset collections", FORMAT);
        root.put("collections", list);
        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, new Yaml(options).dump(root));
        } catch (IOException e) {
            log.warn("Could not save the asset collections {}: {}", file, e.getMessage());
        }
    }
}
