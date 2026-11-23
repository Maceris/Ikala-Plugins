package com.ikalagaming.permissions;

import lombok.NonNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A named set of permissions that players can be in, inheriting other groups' permissions. A
 * group's own settings beat what it inherits.
 *
 * @param name The group's name.
 * @param inherits The groups whose permissions it starts from, in order; later ones win.
 * @param permissions Nodes or wildcards like {@code lotomation.command.*} or {@code *}, set to true
 *     or false.
 */
public record Group(
        @NonNull String name,
        @NonNull List<String> inherits,
        @NonNull Map<String, Boolean> permissions) {

    /**
     * Copy.
     *
     * @param name The name.
     * @param inherits The groups it inherits.
     * @param permissions Its permissions.
     */
    public Group {
        inherits = List.copyOf(inherits);
        final Map<String, Boolean> normalized = new LinkedHashMap<>();
        permissions.forEach((node, value) -> normalized.put(Permission.normalize(node), value));
        permissions = java.util.Collections.unmodifiableMap(normalized);
    }

    /**
     * A group with no permissions of its own.
     *
     * @param name The name.
     * @param inherits The groups it inherits.
     * @return The group.
     */
    public static Group inheriting(@NonNull String name, @NonNull String... inherits) {
        return new Group(name, List.of(inherits), Map.of());
    }
}
