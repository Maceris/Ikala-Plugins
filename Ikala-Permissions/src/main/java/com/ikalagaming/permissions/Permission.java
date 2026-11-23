package com.ikalagaming.permissions;

import lombok.NonNull;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A permission node, like {@code lotomation.command.gamemode}.
 *
 * @param name The node, lower case, dot separated.
 * @param description What it allows, for players and operators to read.
 * @param defaultValue Who has it when nothing sets it.
 * @param children Nodes that follow this one: when this is set, each child is set to the same value
 *     if its flag is true, or the opposite if false.
 */
public record Permission(
        @NonNull String name,
        @NonNull String description,
        @NonNull PermissionDefault defaultValue,
        @NonNull Map<String, Boolean> children) {

    /** What a node looks like: dot-separated parts of letters, digits, _ and -. */
    private static final Pattern NODE = Pattern.compile("[a-z0-9_-]+(\\.[a-z0-9_-]+)*");

    /**
     * Check and copy.
     *
     * @param name The node.
     * @param description What it allows.
     * @param defaultValue Who has it by default.
     * @param children Its children.
     * @throws IllegalArgumentException If the node or a child isn't a valid node.
     */
    public Permission {
        name = normalize(name);
        if (!isValidNode(name)) {
            throw new IllegalArgumentException("Not a permission node: " + name);
        }
        for (String child : children.keySet()) {
            if (!isValidNode(normalize(child))) {
                throw new IllegalArgumentException("Not a permission node: " + child);
            }
        }
        children = Map.copyOf(children);
    }

    /**
     * A permission with no children.
     *
     * @param name The node.
     * @param description What it allows.
     * @param defaultValue Who has it by default.
     */
    public Permission(
            @NonNull String name,
            @NonNull String description,
            @NonNull PermissionDefault defaultValue) {
        this(name, description, defaultValue, Map.of());
    }

    /**
     * Whether text is a node, not a wildcard.
     *
     * @param node The text.
     * @return True for a node like {@code a.b.c}.
     */
    public static boolean isValidNode(String node) {
        return node != null && NODE.matcher(node).matches();
    }

    /**
     * Put a node or wildcard in the form it is compared in.
     *
     * @param node The node.
     * @return It trimmed and in lower case.
     */
    public static String normalize(@NonNull String node) {
        return node.strip().toLowerCase(Locale.ROOT);
    }
}
