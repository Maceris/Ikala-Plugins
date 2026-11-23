package com.ikalagaming.permissions;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Every permission node plugins declared, and which plugin declared it, so a plugin's nodes go when
 * it unloads. Nodes come from YAML in Bukkit's {@code permissions:} format, or from code.
 */
@Slf4j
public final class PermissionRegistry {

    /** The YAML key for a node's description. */
    public static final String DESCRIPTION = "description";

    /** The YAML key for a node's default. */
    public static final String DEFAULT = "default";

    /** The YAML key for a node's children. */
    public static final String CHILDREN = "children";

    /** A node and who declared it. */
    private record Declared(@NonNull Permission permission, @NonNull String owner) {}

    /** Guards the nodes. */
    private final ReentrantLock lock = new ReentrantLock();

    /** The nodes, by name. */
    private final Map<String, Declared> nodes = new LinkedHashMap<>();

    /**
     * Declare a node. Declaring it again from the same owner replaces it.
     *
     * @param owner Who declares it.
     * @param permission The node.
     * @throws IllegalArgumentException If another owner declared it.
     */
    public void register(@NonNull String owner, @NonNull Permission permission) {
        lock.lock();
        try {
            final Declared existing = nodes.get(permission.name());
            if (existing != null && !existing.owner().equals(owner)) {
                throw new IllegalArgumentException(
                        permission.name() + " is already declared by " + existing.owner());
            }
            nodes.put(permission.name(), new Declared(permission, owner));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Declare nodes from a {@code permissions:} map in Bukkit's format: each key a node, each value
     * a map with {@code description}, {@code default} and {@code children}. A child whose value is
     * a map is declared too, as Bukkit allows. Entries that can't be read are skipped with a
     * warning.
     *
     * @param owner Who declares them.
     * @param permissions The map.
     * @param fallback The default for nodes that don't give one.
     * @return How many nodes were declared.
     */
    public int registerAll(
            @NonNull String owner,
            @NonNull Map<?, ?> permissions,
            @NonNull PermissionDefault fallback) {
        final List<Permission> found = new ArrayList<>();
        for (Map.Entry<?, ?> entry : permissions.entrySet()) {
            read(String.valueOf(entry.getKey()), entry.getValue(), fallback, found);
        }
        for (Permission permission : found) {
            try {
                register(owner, permission);
            } catch (IllegalArgumentException e) {
                log.warn(
                        "Skipping the permission {} from {}: {}",
                        permission.name(),
                        owner,
                        e.getMessage());
            }
        }
        return found.size();
    }

    /**
     * Read one node from YAML, and any children declared inside it.
     *
     * @param name The node.
     * @param value Its map, or null.
     * @param fallback The default if it gives none.
     * @param out Receives the nodes.
     */
    private static void read(
            @NonNull String name,
            Object value,
            @NonNull PermissionDefault fallback,
            @NonNull List<Permission> out) {
        final Map<?, ?> map = value instanceof Map<?, ?> m ? m : Map.of();
        final PermissionDefault def = PermissionDefault.named(map.get(DEFAULT)).orElse(fallback);
        final Map<String, Boolean> children = new LinkedHashMap<>();
        if (map.get(CHILDREN) instanceof Map<?, ?> childMap) {
            for (Map.Entry<?, ?> child : childMap.entrySet()) {
                final String childName = String.valueOf(child.getKey());
                if (child.getValue() instanceof Boolean flag) {
                    children.put(Permission.normalize(childName), flag);
                } else if (child.getValue() instanceof Map<?, ?>) {
                    // Declared inline, as Bukkit allows; it follows its parent
                    read(childName, child.getValue(), def, out);
                    children.put(Permission.normalize(childName), true);
                }
            }
        }
        final Object description = map.get(DESCRIPTION);
        try {
            out.add(
                    new Permission(
                            name,
                            description == null ? "" : description.toString(),
                            def,
                            children));
        } catch (IllegalArgumentException e) {
            log.warn("Skipping the permission {}: {}", name, e.getMessage());
        }
    }

    /**
     * Remove every node an owner declared.
     *
     * @param owner The owner.
     * @return How many were removed.
     */
    public int removeAllOwnedBy(@NonNull String owner) {
        lock.lock();
        try {
            final int before = nodes.size();
            nodes.values().removeIf(declared -> declared.owner().equals(owner));
            return before - nodes.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * A declared node.
     *
     * @param name The node.
     * @return It, if declared.
     */
    public Optional<Permission> get(@NonNull String name) {
        lock.lock();
        try {
            return Optional.ofNullable(nodes.get(Permission.normalize(name)))
                    .map(Declared::permission);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Every declared node, in the order declared.
     *
     * @return A copy.
     */
    public List<Permission> all() {
        lock.lock();
        try {
            return nodes.values().stream().map(Declared::permission).toList();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Every declared node, by name, for resolving.
     *
     * @return A copy.
     */
    Map<String, Permission> snapshot() {
        lock.lock();
        try {
            final Map<String, Permission> copy = new HashMap<>();
            nodes.forEach((name, declared) -> copy.put(name, declared.permission()));
            return copy;
        } finally {
            lock.unlock();
        }
    }
}
