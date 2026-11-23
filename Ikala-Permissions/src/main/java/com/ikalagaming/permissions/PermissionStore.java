package com.ikalagaming.permissions;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The groups and each player's own groups and permissions, kept in a YAML file a server's operators
 * can edit:
 *
 * <pre>
 * groups:
 *   builder:
 *     inherits: [default]
 *     permissions: { lotomation.command.gamemode.creative: true }
 * players:
 *   0f8fad5b-d9cb-469f-a165-70867728950e:
 *     name: Somebody
 *     groups: [builder]
 *     permissions: { lotomation.chat: false }
 * </pre>
 *
 * Groups the game relies on, like {@code default} and {@code operator}, are added when missing.
 */
@Slf4j
public final class PermissionStore {

    private static final String GROUPS = "groups";
    private static final String PLAYERS = "players";
    private static final String INHERITS = "inherits";
    private static final String PERMISSIONS = "permissions";
    private static final String NAME = "name";

    /**
     * A player's own entry.
     *
     * @param name Their name when last saved, only to make the file easier to read.
     * @param groups Groups they are in, besides their role's.
     * @param permissions Their own settings, which beat their groups'.
     */
    public record PlayerEntry(
            @NonNull String name,
            @NonNull List<String> groups,
            @NonNull Map<String, Boolean> permissions) {

        /**
         * Copy.
         *
         * @param name The name.
         * @param groups The groups.
         * @param permissions The settings.
         */
        public PlayerEntry {
            groups = List.copyOf(groups);
            final Map<String, Boolean> normalized = new LinkedHashMap<>();
            permissions.forEach((node, value) -> normalized.put(Permission.normalize(node), value));
            permissions = java.util.Collections.unmodifiableMap(normalized);
        }
    }

    /** Guards everything. */
    private final ReentrantLock lock = new ReentrantLock();

    /** The groups, by name. */
    private final Map<String, Group> groups = new LinkedHashMap<>();

    /** The players' entries, by id. */
    private final Map<String, PlayerEntry> players = new LinkedHashMap<>();

    /** The file, or null for memory only. */
    private final Path file;

    /**
     * A store.
     *
     * @param file The file, or null for memory only.
     */
    private PermissionStore(Path file) {
        this.file = file;
    }

    /**
     * A store in memory, for tests and servers without a save.
     *
     * @param defaults Groups to start with.
     * @return The store.
     */
    public static PermissionStore inMemory(@NonNull Group... defaults) {
        final PermissionStore store = new PermissionStore(null);
        for (Group group : defaults) {
            store.groups.put(group.name(), group);
        }
        return store;
    }

    /**
     * Read a store, adding any of the default groups it lacks, and writing it back if it changed.
     *
     * @param file The file; missing means empty.
     * @param defaults Groups to add when missing.
     * @return The store.
     */
    public static PermissionStore load(@NonNull Path file, @NonNull Group... defaults) {
        final PermissionStore store = new PermissionStore(file);
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                if (new Yaml().load(reader) instanceof Map<?, ?> root) {
                    store.read(root);
                }
            } catch (IOException | RuntimeException e) {
                log.warn("Could not read the permissions in {}", file, e);
            }
        }
        boolean added = false;
        for (Group group : defaults) {
            if (!store.groups.containsKey(group.name())) {
                store.groups.put(group.name(), group);
                added = true;
            }
        }
        if (added) {
            store.save();
        }
        return store;
    }

    /**
     * Read the YAML tree.
     *
     * @param root The root map.
     */
    private void read(@NonNull Map<?, ?> root) {
        if (root.get(GROUPS) instanceof Map<?, ?> groupMap) {
            for (Map.Entry<?, ?> entry : groupMap.entrySet()) {
                final String name = String.valueOf(entry.getKey());
                final Map<?, ?> body = entry.getValue() instanceof Map<?, ?> m ? m : Map.of();
                groups.put(
                        name,
                        new Group(name, strings(body.get(INHERITS)), flags(body.get(PERMISSIONS))));
            }
        }
        if (root.get(PLAYERS) instanceof Map<?, ?> playerMap) {
            for (Map.Entry<?, ?> entry : playerMap.entrySet()) {
                final Map<?, ?> body = entry.getValue() instanceof Map<?, ?> m ? m : Map.of();
                players.put(
                        String.valueOf(entry.getKey()),
                        new PlayerEntry(
                                body.get(NAME) == null ? "" : body.get(NAME).toString(),
                                strings(body.get(GROUPS)),
                                flags(body.get(PERMISSIONS))));
            }
        }
    }

    /**
     * A YAML list as text.
     *
     * @param value The value.
     * @return Its items, or none if it isn't a list.
     */
    private static List<String> strings(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    /**
     * A YAML map of node to true or false.
     *
     * @param value The value.
     * @return The flags; entries that aren't booleans are skipped.
     */
    private static Map<String, Boolean> flags(Object value) {
        final Map<String, Boolean> flags = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach(
                    (node, flag) -> {
                        if (flag instanceof Boolean bool) {
                            flags.put(String.valueOf(node), bool);
                        }
                    });
        }
        return flags;
    }

    /**
     * A group.
     *
     * @param name Its name.
     * @return It, if it exists.
     */
    public Optional<Group> group(@NonNull String name) {
        lock.lock();
        try {
            return Optional.ofNullable(groups.get(name));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Every group.
     *
     * @return A copy.
     */
    public List<Group> groups() {
        lock.lock();
        try {
            return new ArrayList<>(groups.values());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Add or replace a group, and save.
     *
     * @param group The group.
     */
    public void putGroup(@NonNull Group group) {
        lock.lock();
        try {
            groups.put(group.name(), group);
        } finally {
            lock.unlock();
        }
        save();
    }

    /**
     * A player's own entry.
     *
     * @param id Their id.
     * @return The entry, if they have one.
     */
    public Optional<PlayerEntry> player(@NonNull String id) {
        lock.lock();
        try {
            return Optional.ofNullable(players.get(id));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Add or replace a player's entry, and save.
     *
     * @param id Their id.
     * @param entry The entry.
     */
    public void putPlayer(@NonNull String id, @NonNull PlayerEntry entry) {
        lock.lock();
        try {
            players.put(id, entry);
        } finally {
            lock.unlock();
        }
        save();
    }

    /** Write the file, if there is one. */
    public void save() {
        if (file == null) {
            return;
        }
        final Map<String, Object> root = new LinkedHashMap<>();
        final Map<String, Object> groupMap = new LinkedHashMap<>();
        final Map<String, Object> playerMap = new LinkedHashMap<>();
        lock.lock();
        try {
            groups.forEach(
                    (name, group) -> {
                        final Map<String, Object> body = new LinkedHashMap<>();
                        body.put(INHERITS, group.inherits());
                        body.put(PERMISSIONS, new LinkedHashMap<>(group.permissions()));
                        groupMap.put(name, body);
                    });
            players.forEach(
                    (id, entry) -> {
                        final Map<String, Object> body = new LinkedHashMap<>();
                        body.put(NAME, entry.name());
                        body.put(GROUPS, entry.groups());
                        body.put(PERMISSIONS, new LinkedHashMap<>(entry.permissions()));
                        playerMap.put(id, body);
                    });
        } finally {
            lock.unlock();
        }
        root.put(GROUPS, groupMap);
        root.put(PLAYERS, playerMap);
        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            final Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write(
                        "# Permission groups, and players' own groups and permissions by account"
                                + " id.\n# Operators and the owner also get the operator and owner"
                                + " groups from their role.\n");
                new Yaml(options).dump(root, writer);
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("Could not save the permissions to {}", file, e);
        }
    }
}
