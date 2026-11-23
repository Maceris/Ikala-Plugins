package com.ikalagaming.permissions;

import lombok.NonNull;

import java.util.List;

/**
 * Someone whose permissions are checked: a player, or the server console.
 *
 * @param id Who they are, as the permission store keys players; for a player, their account id.
 * @param op Whether they are an operator, which decides {@code op} and {@code not op} defaults.
 * @param groups The groups they are in because of who they are, like their role's group; the
 *     store's groups for them come after these.
 */
public record Subject(@NonNull String id, boolean op, @NonNull List<String> groups) {

    /** The server console, which may do anything. */
    public static final Subject CONSOLE = new Subject("console", true, List.of());

    /**
     * Copy.
     *
     * @param id The id.
     * @param op Whether an operator.
     * @param groups Their groups.
     */
    public Subject {
        groups = List.copyOf(groups);
    }
}
