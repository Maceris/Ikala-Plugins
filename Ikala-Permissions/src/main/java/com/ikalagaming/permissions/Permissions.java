package com.ikalagaming.permissions;

import lombok.Getter;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Works out what someone may do, the way Bukkit does: start from every declared node's default,
 * then apply their groups, each after the groups it inherits, then their own settings. Setting a
 * node sets its children too. Within one layer, wildcards apply before exact nodes, so {@code
 * lotomation.command.*: true} with {@code lotomation.command.op: false} leaves op off.
 */
public final class Permissions {

    /** The wildcard for every node. */
    public static final String ALL = "*";

    /** The suffix that makes a node a wildcard. */
    private static final String WILDCARD_SUFFIX = ".*";

    /** How deep children are followed, so a loop of children can't run forever. */
    private static final int MAX_CHILD_DEPTH = 32;

    /**
     * The nodes plugins declared.
     *
     * @return The registry.
     */
    @Getter private final PermissionRegistry registry;

    /**
     * The groups and players' settings.
     *
     * @return The store.
     */
    @Getter private final PermissionStore store;

    /**
     * Permissions from declared nodes and a store.
     *
     * @param registry The nodes.
     * @param store The groups and players' settings.
     */
    public Permissions(@NonNull PermissionRegistry registry, @NonNull PermissionStore store) {
        this.registry = registry;
        this.store = store;
    }

    /**
     * Whether someone may do something.
     *
     * @param subject Who.
     * @param node The node.
     * @return True if allowed.
     */
    public boolean has(@NonNull Subject subject, @NonNull String node) {
        return effective(subject).has(node);
    }

    /**
     * Everything someone may and may not do, worked out once, for checking many nodes.
     *
     * @param subject Who.
     * @return Their effective permissions.
     */
    public Effective effective(@NonNull Subject subject) {
        final Map<String, Permission> declared = registry.snapshot();
        final Effective result = new Effective(declared);
        // Every node's own default, then the nodes that are on by default pass it to their
        // children, as Bukkit does, so the order nodes were declared in doesn't matter
        for (Permission permission : declared.values()) {
            result.values.put(permission.name(), permission.defaultValue().value(subject.op()));
        }
        for (Permission permission : declared.values()) {
            if (permission.defaultValue().value(subject.op())) {
                result.set(permission.name(), true, 0);
            }
        }
        final List<String> groupNames = new ArrayList<>(subject.groups());
        final PermissionStore.PlayerEntry own = store.player(subject.id()).orElse(null);
        if (own != null) {
            groupNames.addAll(own.groups());
        }
        for (String groupName : linearize(groupNames)) {
            store.group(groupName).ifPresent(group -> result.apply(group.permissions()));
        }
        if (own != null) {
            result.apply(own.permissions());
        }
        return result;
    }

    /**
     * Order groups so each comes after the groups it inherits, each once.
     *
     * @param groupNames The groups, in order.
     * @return Every group they reach, bases first.
     */
    List<String> linearize(@NonNull List<String> groupNames) {
        final Set<String> ordered = new LinkedHashSet<>();
        for (String name : groupNames) {
            visit(name, ordered, new HashSet<>());
        }
        return new ArrayList<>(ordered);
    }

    /**
     * Add a group after the groups it inherits.
     *
     * @param name The group.
     * @param ordered The groups so far.
     * @param visiting The groups being visited, to stop on loops.
     */
    private void visit(
            @NonNull String name, @NonNull Set<String> ordered, @NonNull Set<String> visiting) {
        if (ordered.contains(name) || !visiting.add(name)) {
            return;
        }
        store.group(name)
                .ifPresent(
                        group -> group.inherits().forEach(base -> visit(base, ordered, visiting)));
        ordered.add(name);
    }

    /** Someone's permissions, worked out. */
    public static final class Effective {

        /** The declared nodes. */
        private final Map<String, Permission> declared;

        /** Each node's value. */
        private final Map<String, Boolean> values = new HashMap<>();

        /** Wildcards applied, by prefix, latest last, for nodes nobody declared. */
        private final Map<String, Boolean> wildcards = new LinkedHashMap<>();

        /**
         * Start from nothing.
         *
         * @param declared The declared nodes.
         */
        private Effective(@NonNull Map<String, Permission> declared) {
            this.declared = declared;
        }

        /**
         * Whether a node is allowed.
         *
         * @param node The node.
         * @return Its value; for an undeclared node with no value, the latest matching wildcard,
         *     else false.
         */
        public boolean has(@NonNull String node) {
            final String name = Permission.normalize(node);
            final Boolean value = values.get(name);
            if (value != null) {
                return value;
            }
            Boolean found = null;
            for (Map.Entry<String, Boolean> wildcard : wildcards.entrySet()) {
                if (wildcard.getKey().isEmpty() || name.startsWith(wildcard.getKey())) {
                    found = wildcard.getValue();
                }
            }
            return Boolean.TRUE.equals(found);
        }

        /**
         * Apply one layer of settings: wildcards first, then exact nodes.
         *
         * @param settings Nodes and wildcards to values.
         */
        private void apply(@NonNull Map<String, Boolean> settings) {
            settings.forEach(
                    (node, value) -> {
                        if (ALL.equals(node) || node.endsWith(WILDCARD_SUFFIX)) {
                            final String prefix =
                                    ALL.equals(node) ? "" : node.substring(0, node.length() - 1);
                            wildcards.remove(prefix);
                            wildcards.put(prefix, value);
                            for (String name : declared.keySet()) {
                                if (name.startsWith(prefix)) {
                                    set(name, value, 0);
                                }
                            }
                        }
                    });
            settings.forEach(
                    (node, value) -> {
                        if (!ALL.equals(node) && !node.endsWith(WILDCARD_SUFFIX)) {
                            set(node, value, 0);
                        }
                    });
        }

        /**
         * Set a node and, through them, its children.
         *
         * @param node The node.
         * @param value Its value.
         * @param depth How many parents led here.
         */
        private void set(@NonNull String node, boolean value, int depth) {
            values.put(node, value);
            final Permission permission = declared.get(node);
            if (permission == null || depth >= MAX_CHILD_DEPTH) {
                return;
            }
            permission
                    .children()
                    .forEach((child, follows) -> set(child, follows == value, depth + 1));
        }
    }
}
